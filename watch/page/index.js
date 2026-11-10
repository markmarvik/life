import * as ble from '@zos/ble'
import { createWidget, widget, prop, align, text_style, createKeyboard, deleteKeyboard, inputType, setStatusBarVisible } from '@zos/ui'
import { setTimeout } from '@zos/timer'
import { CARD, ITEM_CAP, PAD, RAIL, STRIDE, focusOffset, itemContentX, widgetById } from './layout.js'
import { buildPages, keepIndex, markPage, nearestIndex } from './pager.js'

const APP_ID = 3266101
const WATCH_PORT = 20
const PHONE_PORT = 1004
const TYPE_OPEN = 1
const TYPE_CLOSE = 2
const TYPE_MESSAGE = 4
const OPEN_LIFE = 'Open Life on your phone'
const TAP_GAP = 350
const INK = 0xe8f7ff
const CYAN = 0x3dfff2
const MAGENTA = 0xff2bd6
const LIME = 0xc6ff4a
const MUTED = 0x8ea0b8
const CARD_Y = Math.round((RAIL.h - CARD.h) / 2)

let sidePort = 0
let header = null
let kindText = null
let rail = null
let slots = []
let mark = null
let statusText = null
let pages = []
let index = 0
let ready = false
let holdError = false
let problem = ''
let noteLogged = false
let generation = 0
let lastTap = 0
let settling = false

function utf8Encode(s) {
  const out = []
  for (let i = 0; i < s.length; i++) {
    let c = s.charCodeAt(i)
    if (c >= 0xd800 && c <= 0xdbff && i + 1 < s.length) {
      c = 0x10000 + ((c - 0xd800) << 10) + (s.charCodeAt(++i) - 0xdc00)
    }
    if (c < 0x80) out.push(c)
    else if (c < 0x800) out.push(0xc0 | (c >> 6), 0x80 | (c & 63))
    else if (c < 0x10000) out.push(0xe0 | (c >> 12), 0x80 | ((c >> 6) & 63), 0x80 | (c & 63))
    else out.push(0xf0 | (c >> 18), 0x80 | ((c >> 12) & 63), 0x80 | ((c >> 6) & 63), 0x80 | (c & 63))
  }
  return out
}

function utf8Decode(bytes) {
  let s = ''
  let i = 0
  while (i < bytes.length) {
    const b = bytes[i++]
    let c
    if (b < 0x80) c = b
    else if (b >= 0xc0 && b < 0xe0 && i < bytes.length) c = ((b & 31) << 6) | (bytes[i++] & 63)
    else if (b >= 0xe0 && b < 0xf0 && i + 1 < bytes.length) c = ((b & 15) << 12) | ((bytes[i++] & 63) << 6) | (bytes[i++] & 63)
    else if (b >= 0xf0 && i + 2 < bytes.length) c = ((b & 7) << 18) | ((bytes[i++] & 63) << 12) | ((bytes[i++] & 63) << 6) | (bytes[i++] & 63)
    else return null
    if (c < 0x20 && c !== 10 && c !== 13 && c !== 9) return null
    s += String.fromCodePoint(c)
  }
  return s
}

function frame(type, port2, body) {
  const buf = new ArrayBuffer(16 + body.length)
  const v = new DataView(buf)
  v.setUint8(0, 1)
  v.setUint8(1, 1)
  v.setUint16(2, type, true)
  v.setUint16(4, WATCH_PORT, true)
  v.setUint16(6, port2, true)
  v.setUint32(8, APP_ID, true)
  v.setUint32(12, 0, true)
  new Uint8Array(buf).set(body, 16)
  return buf
}

function paintText(w, text, color) {
  if (!w) return
  w.setProperty(prop.TEXT, text)
  w.setProperty(prop.COLOR, color)
}

function shown(title) {
  const text = title || ''
  if (text.length <= 16) return text
  return text.slice(0, 15) + '…'
}

function freshTap() {
  const now = Date.now()
  if (now - lastTap < TAP_GAP) return false
  lastTap = now
  return true
}

function readOffset() {
  if (!rail) return focusOffset(index)
  const value = rail.getProperty(prop.POS_X)
  return typeof value === 'number' ? value : focusOffset(index)
}

function slotColor(page, focused) {
  if (!page) return MUTED
  if (page.logged && focused) return LIME
  if (!focused) return MUTED
  return page.kind === 'want' ? MAGENTA : CYAN
}

function placeSlots() {
  const count = pages.length
  for (let i = 0; i < slots.length; i++) {
    const page = i < count ? pages[i] : null
    const focused = page && i === index
    slots[i].setProperty(prop.MORE, {
      x: page ? itemContentX(i) : 0,
      y: CARD_Y,
      w: page ? CARD.w : 0,
      h: page ? CARD.h : 0,
      text: page ? shown(page.title) : '',
      color: slotColor(page, focused)
    })
  }
}

function settle(offset) {
  const focus = nearestIndex(offset, STRIDE, pages.length)
  index = focus
  const target = focusOffset(focus)
  if (rail && Math.abs(offset - target) > 1) {
    settling = true
    rail.setProperty(prop.POS_X, target)
    settling = false
  }
  paint()
}

function paint() {
  const count = pages.length
  const page = count ? pages[index] : null
  paintText(header, count ? (index + 1) + ' / ' + count : 'LIFE', CYAN)
  if (!page) {
    paintText(kindText, '', MUTED)
    paintText(mark, '', LIME)
  } else {
    const want = page.kind === 'want'
    paintText(kindText, want ? 'WANT' : 'HABIT', want ? MAGENTA : CYAN)
    paintText(mark, page.logged ? 'Logged' : '', LIME)
  }
  placeSlots()
  if (holdError) return
  if (!ready) paintText(statusText, 'Syncing', CYAN)
  else if (noteLogged) paintText(statusText, 'Logged', LIME)
  else if (!count) paintText(statusText, 'Path is clear', MUTED)
  else paintText(statusText, '', MUTED)
}

function showProblem(reason) {
  holdError = true
  problem = reason || ''
  paintText(statusText, problem ? OPEN_LIFE + '\n' + problem : OPEN_LIFE, MAGENTA)
}

function sendOpen() {
  const buf = frame(TYPE_OPEN, 0, [APP_ID & 0xff])
  ble.send(buf, buf.byteLength)
}

function sendJson(obj) {
  const body = utf8Encode(JSON.stringify(obj))
  const buf = frame(TYPE_MESSAGE, sidePort || PHONE_PORT, body)
  const ok = ble.send(buf, buf.byteLength)
  return ok !== false
}

function armTimeout() {
  generation += 1
  const token = generation
  setTimeout(function () {
    if (token === generation && !ready) showProblem('timeout')
  }, 8000)
}

function sendSync() {
  holdError = false
  problem = ''
  noteLogged = false
  ready = false
  paint()
  if (!sendJson({ v: 1, type: 'sync' })) showProblem('')
  armTimeout()
}

function onSlot(slot) {
  if (!freshTap()) return
  const focus = nearestIndex(readOffset(), STRIDE, pages.length)
  if (slot !== focus) {
    settle(focusOffset(slot))
    return
  }
  const page = pages[slot]
  if (!page) return
  holdError = false
  problem = ''
  const payload = page.kind === 'want'
    ? { v: 1, type: 'want_done', wantId: page.id }
    : { v: 1, type: 'log_habit', habitId: page.id }
  if (!sendJson(payload)) showProblem('')
}

function replacePages(wants, habits) {
  const previous = pages[index] || null
  pages = buildPages(wants, habits)
  index = keepIndex(pages, previous ? { kind: previous.kind, id: previous.id, index } : null)
  if (rail) rail.setProperty(prop.POS_X, focusOffset(index))
}

function noteValue(result) {
  if (!result) return ''
  if (typeof result === 'string') return result
  if (typeof result.data === 'string') return result.data
  return ''
}

function onMessage(body) {
  const text = utf8Decode(body)
  if (text === null) {
    showProblem('bad_payload')
    return
  }
  let obj
  try {
    obj = JSON.parse(text)
  } catch (e) {
    showProblem('bad_payload')
    return
  }
  if (!obj || obj.v !== 1) {
    showProblem('bad_payload')
    return
  }
  if (obj.type === 'habits' || obj.type === 'digest') {
    ready = true
    replacePages(obj.wants, obj.type === 'digest' ? obj.habits : obj.items)
    if (problem === '' || problem === 'timeout') {
      holdError = false
      problem = ''
    }
    paint()
  } else if (obj.type === 'ok') {
    holdError = false
    problem = ''
    const marked = markPage(pages, obj.ref, obj.id)
    pages = marked.pages
    if (marked.noteLogged) noteLogged = true
    paint()
  } else if (obj.type === 'error') {
    showProblem(obj.reason || 'error')
  }
}

function onData(_index, data, size) {
  if (!data) return
  const bytes = new Uint8Array(data)
  const n = typeof size === 'number' && size > 0 && size <= bytes.length ? size : bytes.length
  if (n < 16) return
  const view = new DataView(data)
  const type = view.getUint16(2, true)
  const port2 = view.getUint16(6, true)
  const body = bytes.slice(16, n)
  if (type === TYPE_OPEN) {
    sidePort = port2 || PHONE_PORT
  } else if (type === TYPE_CLOSE) {
    sidePort = 0
  } else if (type === TYPE_MESSAGE) {
    onMessage(body)
  }
}

function textWidget(id, color, size) {
  const rect = widgetById(id)
  return createWidget(widget.TEXT, {
    x: rect.x,
    y: rect.y,
    w: rect.w,
    h: rect.h,
    color: color,
    text_size: size,
    align_h: align.CENTER_H,
    align_v: align.CENTER_V,
    text_style: text_style.WRAP,
    text: ''
  })
}

Page({
  build() {
    setStatusBarVisible(false)
    const bg = widgetById('bg')
    createWidget(widget.FILL_RECT, { x: bg.x, y: bg.y, w: bg.w, h: bg.h, color: 0x07080c })
    const left = widgetById('left')
    createWidget(widget.FILL_RECT, { x: left.x, y: left.y, w: left.w, h: left.h, color: CYAN })
    const right = widgetById('right')
    createWidget(widget.FILL_RECT, { x: right.x, y: right.y, w: right.w, h: right.h, color: MAGENTA })
    const wash = widgetById('wash')
    createWidget(widget.FILL_RECT, { x: wash.x, y: wash.y, w: wash.w, h: wash.h, color: 0x10141c })
    header = textWidget('header', CYAN, 26)
    kindText = textWidget('kind', CYAN, 18)
    rail = createWidget(widget.VIEW_CONTAINER, {
      x: RAIL.x,
      y: RAIL.y,
      w: RAIL.w,
      h: RAIL.h,
      scroll_enable: 1,
      bounce: 1,
      z_index: 1,
      scroll_complete_func: function (info) {
        if (settling) return
        let offset = readOffset()
        if (info && typeof info.xoffset === 'number') offset = info.xoffset
        settle(offset)
      }
    })
    for (let i = 0; i < ITEM_CAP; i++) {
      const slot = i
      slots.push(rail.createWidget(widget.BUTTON, {
        x: 0,
        y: CARD_Y,
        w: 0,
        h: 0,
        radius: 22,
        normal_color: 0x101820,
        press_color: 0x163038,
        color: INK,
        text: '',
        text_size: 28,
        click_func: function () { onSlot(slot) }
      }))
    }
    mark = textWidget('mark', LIME, 22)
    statusText = textWidget('status', CYAN, 20)
    const note = widgetById('note')
    createWidget(widget.BUTTON, {
      x: note.x,
      y: note.y,
      w: note.w,
      h: note.h,
      radius: 18,
      normal_color: 0x2a1024,
      press_color: 0x4a1840,
      color: MAGENTA,
      text: 'Note',
      text_size: 26,
      click_func: function () {
        if (!freshTap()) return
        createKeyboard({
          inputType: inputType.CHAR,
          text: '',
          onComplete: function (_, result) {
            deleteKeyboard()
            const text = noteValue(result).trim()
            if (!text) return
            noteLogged = false
            holdError = false
            problem = ''
            paint()
            if (!sendJson({ v: 1, type: 'note', text })) showProblem('')
          },
          onCancel: function () {
            deleteKeyboard()
          }
        })
      }
    })
    const sync = widgetById('sync')
    createWidget(widget.BUTTON, {
      x: sync.x,
      y: sync.y,
      w: sync.w,
      h: sync.h,
      radius: 18,
      normal_color: 0x06282c,
      press_color: 0x0c4450,
      color: CYAN,
      text: 'Sync',
      text_size: 26,
      click_func: function () {
        if (!freshTap()) return
        sendSync()
      }
    })
    paint()
    ble.createConnect(onData)
    ble.addListener(function () {})
    sendOpen()
    sendSync()
  },
  onDestroy() {
    if (sidePort) {
      const buf = frame(TYPE_CLOSE, sidePort, [APP_ID & 0xff])
      ble.send(buf, buf.byteLength)
    }
    ble.removeListener()
    ble.disConnect()
  }
})

import * as ble from '@zos/ble'
import { createWidget, widget, prop, align, text_style, createKeyboard, deleteKeyboard, inputType } from '@zos/ui'
import { setTimeout } from '@zos/timer'

const APP_ID = 3266101
const WATCH_PORT = 20
const PHONE_PORT = 1004
const TYPE_OPEN = 1
const TYPE_CLOSE = 2
const TYPE_MESSAGE = 4
const OPEN_LIFE = 'Open Life on your phone'

let sidePort = 0
let statusText = null
let noteText = null
let list = null
let rows = []
let ready = false
let holdError = false
let problem = ''
let generation = 0
let noteMark = ''

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

function show(w, t) {
  if (w) w.setProperty(prop.TEXT, t)
}

function showStatus(text) {
  show(statusText, text)
}

function showProblem(reason) {
  holdError = true
  problem = reason || ''
  showStatus(problem ? OPEN_LIFE + '\n' + problem : OPEN_LIFE)
}

function showConnected() {
  if (!holdError) showStatus('Life')
}

function paintNote() {
  show(noteText, noteMark === 'Logged' ? 'Note Logged' : '')
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
  if (!sendJson({ v: 1, type: 'sync' })) showProblem('')
  armTimeout()
}

function listData() {
  const data = []
  for (let i = 0; i < rows.length; i++) {
    data.push({ title: rows[i].title, mark: rows[i].mark })
  }
  return data
}

function paintList() {
  if (!list) return
  const data = listData()
  list.setProperty(prop.UPDATE_DATA, {
    data_array: data,
    data_count: data.length,
    on_page: 1
  })
}

function ensureList() {
  if (list || rows.length === 0) return
  list = createWidget(widget.SCROLL_LIST, {
    x: 16,
    y: 108,
    w: 400,
    h: 300,
    item_space: 8,
    item_config: [
      {
        type_id: 1,
        item_height: 68,
        item_bg_color: 0x1a1a1a,
        item_bg_radius: 16,
        text_view: [
          { x: 14, y: 6, w: 250, h: 56, key: 'title', color: 0xffffff, text_size: 26 },
          { x: 268, y: 6, w: 110, h: 56, key: 'mark', color: 0x8ee0b0, text_size: 20 }
        ],
        text_view_count: 2
      }
    ],
    item_config_count: 1,
    data_array: listData(),
    data_count: rows.length,
    item_click_func: function (_item, index) {
      const row = rows[index]
      if (!row) return
      holdError = false
      if (row.kind === 'want') {
        if (!sendJson({ v: 1, type: 'want_done', wantId: row.id })) showProblem('')
      } else if (row.kind === 'habit') {
        if (!sendJson({ v: 1, type: 'log_habit', habitId: row.id })) showProblem('')
      }
    }
  })
}

function showList(wants, habits) {
  const next = []
  const wantRows = wants || []
  const habitRows = habits || []
  for (let i = 0; i < wantRows.length; i++) {
    const row = wantRows[i]
    if (!row || !row.id) continue
    next.push({ kind: 'want', id: String(row.id), title: String(row.title || ''), mark: '' })
  }
  for (let j = 0; j < habitRows.length; j++) {
    const row = habitRows[j]
    if (!row || !row.id) continue
    next.push({
      kind: 'habit',
      id: String(row.id),
      title: String(row.name || ''),
      mark: row.loggedToday ? 'Logged' : ''
    })
  }
  rows = next
  if (!list) ensureList()
  else paintList()
}

function markOk(ref, id) {
  if (ref === 'note') {
    noteMark = 'Logged'
    paintNote()
    return
  }
  const key = id == null ? '' : String(id)
  for (let i = 0; i < rows.length; i++) {
    if (rows[i].id === key) rows[i].mark = 'Logged'
  }
  paintList()
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
    showList(obj.wants, obj.type === 'digest' ? obj.habits : obj.items)
    if (problem === '' || problem === 'timeout') {
      holdError = false
      problem = ''
    }
    showConnected()
  } else if (obj.type === 'ok') {
    holdError = false
    problem = ''
    markOk(obj.ref, obj.id)
    showConnected()
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

Page({
  build() {
    statusText = createWidget(widget.TEXT, {
      x: 16,
      y: 12,
      w: 400,
      h: 88,
      color: 0xffffff,
      text_size: 26,
      align_h: align.CENTER_H,
      align_v: align.CENTER_V,
      text_style: text_style.WRAP,
      text: 'Syncing'
    })
    createWidget(widget.BUTTON, {
      x: 16,
      y: 418,
      w: 190,
      h: 64,
      radius: 32,
      normal_color: 0x0050a0,
      press_color: 0x003070,
      text: 'Note',
      text_size: 26,
      click_func: function () {
        createKeyboard({
          inputType: inputType.CHAR,
          text: '',
          onComplete: function (_, result) {
            deleteKeyboard()
            const text = noteValue(result).trim()
            if (!text) return
            noteMark = ''
            paintNote()
            holdError = false
            if (!sendJson({ v: 1, type: 'note', text: text })) showProblem('')
          },
          onCancel: function () {
            deleteKeyboard()
          }
        })
      }
    })
    createWidget(widget.BUTTON, {
      x: 226,
      y: 418,
      w: 190,
      h: 64,
      radius: 32,
      normal_color: 0x404040,
      press_color: 0x202020,
      text: 'Sync',
      text_size: 26,
      click_func: function () {
        ready = false
        sendSync()
      }
    })
    noteText = createWidget(widget.TEXT, {
      x: 16,
      y: 486,
      w: 400,
      h: 24,
      color: 0x8ee0b0,
      text_size: 20,
      align_h: align.CENTER_H,
      text: ''
    })
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

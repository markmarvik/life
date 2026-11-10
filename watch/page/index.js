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
const INK = 0xe8f7ff
const CYAN = 0x3dfff2
const MAGENTA = 0xff2bd6
const LIME = 0xc6ff4a
const MUTED = 0x8ea0b8
const WANT_BG = 0x24101f
const HABIT_BG = 0x0c1c24

let sidePort = 0
let stateText = null
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

function paintText(w, text, color) {
  if (!w) return
  w.setProperty(prop.TEXT, text)
  if (color != null) w.setProperty(prop.COLOR, color)
}

function showState(text, color) {
  paintText(stateText, text, color)
}

function showStatus(text, color) {
  paintText(statusText, text, color)
}

function showProblem(reason) {
  holdError = true
  problem = reason || ''
  showState('LINK', MAGENTA)
  showStatus(problem ? OPEN_LIFE + '\n' + problem : OPEN_LIFE, MAGENTA)
}

function showConnected() {
  if (holdError) return
  showState(rows.length === 0 && ready ? 'CLEAR' : 'LIVE', LIME)
  showStatus(rows.length === 0 && ready ? 'Path is clear' : 'Today', MUTED)
}

function paintNote() {
  show(noteText, noteMark === 'Logged' ? 'Logged' : '')
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
  showState('SYNC', CYAN)
  showStatus('Syncing', CYAN)
  if (!sendJson({ v: 1, type: 'sync' })) showProblem('')
  armTimeout()
}

function listData() {
  const data = []
  for (let i = 0; i < rows.length; i++) {
    const row = rows[i]
    data.push({
      kind: row.kind === 'want' ? 'WANT' : 'HABIT',
      title: row.title,
      mark: row.mark
    })
  }
  return data
}

function typeConfig() {
  let wants = 0
  for (let i = 0; i < rows.length; i++) {
    if (rows[i].kind === 'want') wants += 1
  }
  const config = []
  if (wants > 0) config.push({ start: 0, end: wants - 1, type_id: 1 })
  if (rows.length > wants) config.push({ start: wants, end: rows.length - 1, type_id: 2 })
  return config
}

function paintList() {
  if (!list) return
  const data = listData()
  const types = typeConfig()
  list.setProperty(prop.UPDATE_DATA, {
    data_array: data,
    data_count: data.length,
    data_type_config: types,
    data_type_config_count: types.length,
    on_page: 1
  })
}

function rowText(color, kindColor, markColor) {
  return [
    { x: 18, y: 8, w: 140, h: 22, key: 'kind', color: kindColor, text_size: 16 },
    { x: 18, y: 30, w: 250, h: 40, key: 'title', color: color, text_size: 28 },
    { x: 268, y: 28, w: 112, h: 36, key: 'mark', color: markColor, text_size: 20 }
  ]
}

function ensureList() {
  if (list || rows.length === 0) return
  const types = typeConfig()
  list = createWidget(widget.SCROLL_LIST, {
    x: 16,
    y: 124,
    w: 400,
    h: 280,
    item_space: 10,
    item_config: [
      {
        type_id: 1,
        item_height: 78,
        item_bg_color: WANT_BG,
        item_bg_radius: 18,
        text_view: rowText(INK, MAGENTA, LIME),
        text_view_count: 3
      },
      {
        type_id: 2,
        item_height: 78,
        item_bg_color: HABIT_BG,
        item_bg_radius: 18,
        text_view: rowText(INK, CYAN, LIME),
        text_view_count: 3
      }
    ],
    item_config_count: 2,
    data_array: listData(),
    data_count: rows.length,
    data_type_config: types,
    data_type_config_count: types.length,
    item_click_func: function (_item, index) {
      const row = rows[index]
      if (!row) return
      holdError = false
      problem = ''
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
    createWidget(widget.FILL_RECT, {
      x: 0,
      y: 0,
      w: 432,
      h: 514,
      color: 0x07080c
    })
    createWidget(widget.FILL_RECT, {
      x: 20,
      y: 58,
      w: 392,
      h: 3,
      color: CYAN
    })
    createWidget(widget.TEXT, {
      x: 20,
      y: 14,
      w: 180,
      h: 38,
      color: CYAN,
      text_size: 32,
      align_v: align.CENTER_V,
      text: 'LIFE'
    })
    stateText = createWidget(widget.TEXT, {
      x: 200,
      y: 16,
      w: 212,
      h: 34,
      color: CYAN,
      text_size: 22,
      align_h: align.RIGHT,
      align_v: align.CENTER_V,
      text: 'SYNC'
    })
    statusText = createWidget(widget.TEXT, {
      x: 20,
      y: 68,
      w: 392,
      h: 50,
      color: CYAN,
      text_size: 22,
      align_v: align.CENTER_V,
      text_style: text_style.WRAP,
      text: 'Syncing'
    })
    createWidget(widget.BUTTON, {
      x: 16,
      y: 416,
      w: 192,
      h: 68,
      radius: 18,
      normal_color: 0x2a0a22,
      press_color: 0x5a1248,
      color: MAGENTA,
      text: 'Note',
      text_size: 28,
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
            problem = ''
            if (!sendJson({ v: 1, type: 'note', text: text })) showProblem('')
          },
          onCancel: function () {
            deleteKeyboard()
          }
        })
      }
    })
    createWidget(widget.BUTTON, {
      x: 224,
      y: 416,
      w: 192,
      h: 68,
      radius: 18,
      normal_color: 0x06282c,
      press_color: 0x0c4450,
      color: CYAN,
      text: 'Sync',
      text_size: 28,
      click_func: function () {
        ready = false
        sendSync()
      }
    })
    noteText = createWidget(widget.TEXT, {
      x: 16,
      y: 488,
      w: 400,
      h: 22,
      color: LIME,
      text_size: 18,
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

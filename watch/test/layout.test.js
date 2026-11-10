import assert from 'node:assert/strict'
import test from 'node:test'
import { PAGE_CAP } from '../page/pager.js'
import {
  CARD,
  GAP,
  ITEM_CAP,
  PAD,
  RAIL,
  SCREEN,
  STRIDE,
  WIDGETS,
  focusOffset,
  itemScreenRect,
  visibleHeight
} from '../page/layout.js'

function pointInside(px, py) {
  const { w, h, r } = SCREEN
  if (px < 0 || py < 0 || px > w || py > h) return false
  let cx = null
  let cy = null
  if (px < r && py < r) {
    cx = r
    cy = r
  } else if (px > w - r && py < r) {
    cx = w - r
    cy = r
  } else if (px < r && py > h - r) {
    cx = r
    cy = h - r
  } else if (px > w - r && py > h - r) {
    cx = w - r
    cy = h - r
  } else {
    return true
  }
  const dx = px - cx
  const dy = py - cy
  return dx * dx + dy * dy <= r * r
}

function rectInsideScreen(rect) {
  return rect.x >= 0 && rect.y >= 0 && rect.x + rect.w <= SCREEN.w && rect.y + rect.h <= SCREEN.h
}

function rectInsideCurve(rect) {
  const corners = [
    [rect.x, rect.y],
    [rect.x + rect.w, rect.y],
    [rect.x, rect.y + rect.h],
    [rect.x + rect.w, rect.y + rect.h]
  ]
  return corners.every(([x, y]) => pointInside(x, y))
}

test('screen metrics are the Bip Max panel', () => {
  assert.deepEqual(SCREEN, { w: 432, h: 514, r: 106 })
  assert.equal(ITEM_CAP, PAGE_CAP)
})

test('chrome stays on the panel and tappable widgets clear the corner radius', () => {
  assert.equal(WIDGETS.length, 11)
  const ids = new Set()
  for (const rect of WIDGETS) {
    assert.equal(ids.has(rect.id), false)
    ids.add(rect.id)
    assert.equal(rectInsideScreen(rect), true, rect.id)
    if (rect.role === 'hit') assert.equal(rectInsideCurve(rect), true, rect.id)
  }
  assert.equal(ids.has('prev'), false)
  assert.equal(ids.has('next'), false)
})

test('exactly three cards fit in the rail and stay inside the curve', () => {
  assert.ok(RAIL.y >= SCREEN.r)
  assert.ok(RAIL.y + RAIL.h <= SCREEN.h - SCREEN.r)
  assert.equal(3 * CARD.h + 2 * GAP, RAIL.h)
  assert.equal(PAD, STRIDE)
  const offset = focusOffset(1)
  const center = itemScreenRect(1, offset)
  const above = itemScreenRect(0, offset)
  const below = itemScreenRect(2, offset)
  assert.equal(visibleHeight(1, offset), CARD.h)
  assert.equal(visibleHeight(0, offset), CARD.h)
  assert.equal(visibleHeight(2, offset), CARD.h)
  assert.equal(above.y, RAIL.y)
  assert.equal(below.y + below.h, RAIL.y + RAIL.h)
  assert.equal(rectInsideCurve(center), true)
  assert.equal(rectInsideCurve(above), true)
  assert.equal(rectInsideCurve(below), true)
})

test('note and sync sit in the bottom center and edge fills bleed to the panel', () => {
  const byId = Object.fromEntries(WIDGETS.map((rect) => [rect.id, rect]))
  for (const id of ['note', 'sync']) {
    const rect = byId[id]
    assert.ok(rect.x >= SCREEN.r)
    assert.ok(rect.x + rect.w <= SCREEN.w - SCREEN.r)
    assert.ok(rect.y + rect.h <= SCREEN.h - 8)
    assert.ok(rect.h >= 56)
  }
  assert.equal(byId.note.y, byId.sync.y)
  assert.ok(byId.note.x + byId.note.w < byId.sync.x)
  assert.equal(byId.bg.w, SCREEN.w)
  assert.equal(byId.bg.h, SCREEN.h)
  assert.equal(byId.left.x, 0)
  assert.equal(byId.right.x + byId.right.w, SCREEN.w)
  assert.equal(byId.wash.y + byId.wash.h, SCREEN.h)
  assert.ok(byId.status.y + byId.status.h <= byId.note.y)
  assert.equal(STRIDE, CARD.h + GAP)
})

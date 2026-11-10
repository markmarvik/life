import assert from 'node:assert/strict'
import test from 'node:test'
import { PAGE_CAP } from '../page/pager.js'
import {
  CARD,
  GAP,
  ITEM_CAP,
  RAIL,
  SCREEN,
  STRIDE,
  WIDGETS,
  focusOffset,
  itemContentY,
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

test('three cards fill the rail with the first card at the top', () => {
  assert.ok(RAIL.y >= SCREEN.r)
  assert.ok(RAIL.y + RAIL.h <= SCREEN.h - SCREEN.r)
  assert.equal(3 * CARD.h + 2 * GAP, RAIL.h)
  assert.equal(itemContentY(0), 0)
  assert.equal(visibleHeight(0, 0), CARD.h)
  assert.equal(visibleHeight(1, 0), CARD.h)
  assert.equal(visibleHeight(2, 0), CARD.h)
  assert.equal(visibleHeight(3, 0), 0)
  const top = itemScreenRect(0, 0)
  const middle = itemScreenRect(1, 0)
  const bottom = itemScreenRect(2, 0)
  assert.equal(top.y, RAIL.y)
  assert.equal(bottom.y + bottom.h, RAIL.y + RAIL.h)
  assert.equal(rectInsideCurve(top), true)
  assert.equal(rectInsideCurve(middle), true)
  assert.equal(rectInsideCurve(bottom), true)
})

test('focus sits on the top card at the start and the bottom card at the end', () => {
  const count = 8
  const start = focusOffset(0, count)
  assert.equal(start, 0)
  assert.equal(itemScreenRect(0, start).y, RAIL.y)
  assert.equal(visibleHeight(0, start), CARD.h)
  assert.equal(visibleHeight(2, start), CARD.h)
  const end = focusOffset(count - 1, count)
  assert.equal(end, (count - 3) * STRIDE)
  const last = itemScreenRect(count - 1, end)
  assert.equal(last.y + last.h, RAIL.y + RAIL.h)
  assert.equal(visibleHeight(count - 3, end), CARD.h)
  assert.equal(visibleHeight(count - 1, end), CARD.h)
  assert.equal(visibleHeight(count - 4, end), 0)
  const mid = focusOffset(4, count)
  assert.equal(itemScreenRect(4, mid).y, RAIL.y + STRIDE)
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
  assert.equal(byId.left.y, 0)
  assert.equal(byId.left.h, SCREEN.h)
  assert.equal(byId.right.y, 0)
  assert.equal(byId.right.h, SCREEN.h)
  assert.equal(byId.right.x + byId.right.w, SCREEN.w)
  assert.ok(byId.left.y + byId.left.h >= byId.note.y + byId.note.h)
  assert.equal(byId.wash.x, byId.left.w)
  assert.equal(byId.wash.x + byId.wash.w, byId.right.x)
  assert.equal(byId.wash.y + byId.wash.h, SCREEN.h)
  assert.ok(byId.status.y + byId.status.h <= byId.note.y)
  assert.ok(byId.status.w >= 360)
  assert.ok(byId.status.h >= 24)
  assert.ok(byId.status.h <= 28)
  assert.equal(byId.status.y - (RAIL.y + RAIL.h), 16)
  assert.ok(byId.status.y + byId.status.h <= SCREEN.h - SCREEN.r)
  assert.equal(STRIDE, CARD.h + GAP)
})

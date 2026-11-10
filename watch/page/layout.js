import { windowStart } from './pager.js'

export const SCREEN = { w: 432, h: 514, r: 106 }

export const RAIL = { id: 'rail', role: 'hit', x: 20, y: 106, w: 392, h: 260 }
export const CARD = { w: 360, h: 84 }
export const GAP = 4
export const STRIDE = CARD.h + GAP
export const CARD_X = Math.round((RAIL.w - CARD.w) / 2)
export const ITEM_CAP = 15

export const WIDGETS = [
  { id: 'bg', role: 'decor', x: 0, y: 0, w: 432, h: 514 },
  { id: 'left', role: 'decor', x: 0, y: 0, w: 14, h: 514 },
  { id: 'right', role: 'decor', x: 418, y: 0, w: 14, h: 514 },
  { id: 'wash', role: 'decor', x: 14, y: 408, w: 404, h: 106 },
  { id: 'header', role: 'hit', x: 120, y: 18, w: 192, h: 32 },
  { id: 'kind', role: 'hit', x: 120, y: 52, w: 192, h: 26 },
  { id: 'mark', role: 'hit', x: 120, y: 80, w: 192, h: 22 },
  RAIL,
  { id: 'status', role: 'hit', x: 16, y: 382, w: 400, h: 26 },
  { id: 'note', role: 'hit', x: 112, y: 424, w: 100, h: 64 },
  { id: 'sync', role: 'hit', x: 220, y: 424, w: 100, h: 64 }
]

export function widgetById(id) {
  for (let i = 0; i < WIDGETS.length; i++) {
    if (WIDGETS[i].id === id) return WIDGETS[i]
  }
  return null
}

export function itemContentY(i) {
  return i * STRIDE
}

export function focusOffset(index, count) {
  return windowStart(index, count) * STRIDE
}

export function itemScreenRect(i, offset) {
  return {
    x: RAIL.x + CARD_X,
    y: RAIL.y + itemContentY(i) - offset,
    w: CARD.w,
    h: CARD.h
  }
}

export function visibleHeight(i, offset) {
  const rect = itemScreenRect(i, offset)
  const top = Math.max(rect.y, RAIL.y)
  const bottom = Math.min(rect.y + rect.h, RAIL.y + RAIL.h)
  return Math.max(0, bottom - top)
}

export const SCREEN = { w: 432, h: 514, r: 106 }

export const RAIL = { id: 'rail', role: 'hit', x: 16, y: 118, w: 400, h: 220 }
export const CARD = { w: 200, h: 196 }
export const GAP = 16
export const STRIDE = CARD.w + GAP
export const PAD = Math.round((RAIL.w - CARD.w) / 2)
export const ITEM_CAP = 15

export const WIDGETS = [
  { id: 'bg', role: 'decor', x: 0, y: 0, w: 432, h: 514 },
  { id: 'left', role: 'decor', x: 0, y: 0, w: 14, h: 514 },
  { id: 'right', role: 'decor', x: 418, y: 0, w: 14, h: 514 },
  { id: 'wash', role: 'decor', x: 0, y: 408, w: 432, h: 106 },
  { id: 'header', role: 'hit', x: 120, y: 22, w: 192, h: 34 },
  { id: 'kind', role: 'hit', x: 120, y: 60, w: 192, h: 28 },
  RAIL,
  { id: 'mark', role: 'hit', x: 116, y: 344, w: 200, h: 28 },
  { id: 'status', role: 'hit', x: 116, y: 372, w: 200, h: 32 },
  { id: 'note', role: 'hit', x: 112, y: 424, w: 100, h: 64 },
  { id: 'sync', role: 'hit', x: 220, y: 424, w: 100, h: 64 }
]

export function widgetById(id) {
  for (let i = 0; i < WIDGETS.length; i++) {
    if (WIDGETS[i].id === id) return WIDGETS[i]
  }
  return null
}

export function itemContentX(i) {
  return PAD + i * STRIDE
}

export function focusOffset(i) {
  return i * STRIDE
}

export function itemScreenRect(i, offset) {
  return {
    x: RAIL.x + itemContentX(i) - offset,
    y: RAIL.y + Math.round((RAIL.h - CARD.h) / 2),
    w: CARD.w,
    h: CARD.h
  }
}

export function visibleWidth(i, offset) {
  const rect = itemScreenRect(i, offset)
  const left = Math.max(rect.x, RAIL.x)
  const right = Math.min(rect.x + rect.w, RAIL.x + RAIL.w)
  return Math.max(0, right - left)
}

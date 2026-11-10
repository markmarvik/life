export const SCREEN = { w: 432, h: 514, r: 106 }

export const WIDGETS = [
  { id: 'bg', role: 'decor', x: 0, y: 0, w: 432, h: 514 },
  { id: 'left', role: 'decor', x: 0, y: 0, w: 14, h: 514 },
  { id: 'right', role: 'decor', x: 418, y: 0, w: 14, h: 514 },
  { id: 'wash', role: 'decor', x: 0, y: 408, w: 432, h: 106 },
  { id: 'header', role: 'hit', x: 120, y: 22, w: 192, h: 34 },
  { id: 'kind', role: 'hit', x: 120, y: 60, w: 192, h: 28 },
  { id: 'prev', role: 'hit', x: 12, y: 128, w: 72, h: 168 },
  { id: 'card', role: 'hit', x: 96, y: 120, w: 240, h: 184 },
  { id: 'next', role: 'hit', x: 348, y: 128, w: 72, h: 168 },
  { id: 'mark', role: 'hit', x: 96, y: 312, w: 240, h: 36 },
  { id: 'status', role: 'hit', x: 116, y: 356, w: 200, h: 44 },
  { id: 'note', role: 'hit', x: 112, y: 424, w: 100, h: 64 },
  { id: 'sync', role: 'hit', x: 220, y: 424, w: 100, h: 64 }
]

export function widgetById(id) {
  for (let i = 0; i < WIDGETS.length; i++) {
    if (WIDGETS[i].id === id) return WIDGETS[i]
  }
  return null
}

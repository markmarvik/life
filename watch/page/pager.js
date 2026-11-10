export const PAGE_CAP = 15

export function buildPages(wants, habits) {
  const pages = []
  const ws = Array.isArray(wants) ? wants : []
  const hs = Array.isArray(habits) ? habits : []
  for (let i = 0; i < ws.length && pages.length < PAGE_CAP; i++) {
    pushPage(pages, 'want', ws[i], 'title', false)
  }
  for (let i = 0; i < hs.length && pages.length < PAGE_CAP; i++) {
    const row = hs[i]
    pushPage(pages, 'habit', row, 'name', !!(row && row.loggedToday))
  }
  return pages
}

export function nearestIndex(offset, stride, count) {
  if (count <= 0 || stride <= 0) return 0
  const index = Math.round(offset / stride)
  if (!(index > 0)) return 0
  if (index >= count) return count - 1
  return index
}

export function step(index, delta, count) {
  if (count <= 0) return 0
  let next = index + delta
  if (next < 0) next = count - 1
  else if (next >= count) next = 0
  return next
}

export function keepIndex(pages, previous) {
  if (!previous) return 0
  for (let i = 0; i < pages.length; i++) {
    if (pages[i].kind === previous.kind && pages[i].id === previous.id) return i
  }
  if (pages.length === 0) return 0
  if (indexInRange(previous.index, pages.length)) return previous.index
  return 0
}

export function markPage(pages, ref, id) {
  if (ref === 'note') return { noteLogged: true, pages }
  const key = id == null ? '' : String(id)
  const want = ref === 'want_done'
  let changed = false
  const next = []
  for (let i = 0; i < pages.length; i++) {
    const page = pages[i]
    const same = page.id === key && (want ? page.kind === 'want' : page.kind === 'habit')
    if (same && !page.logged) {
      next.push({ kind: page.kind, id: page.id, title: page.title, logged: true })
      changed = true
    } else {
      next.push(page)
    }
  }
  return { noteLogged: false, pages: changed ? next : pages }
}

function pushPage(pages, kind, row, titleKey, logged) {
  if (!row || row.id == null || String(row.id) === '') return
  pages.push({
    kind,
    id: String(row.id),
    title: row[titleKey] == null ? '' : String(row[titleKey]),
    logged
  })
}

function indexInRange(index, count) {
  return typeof index === 'number' && index >= 0 && index < count
}

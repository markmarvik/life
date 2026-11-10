import assert from 'node:assert/strict'
import test from 'node:test'
import { PAGE_CAP, buildPages, keepIndex, markPage, nearestIndex, step, windowStart } from '../page/pager.js'

test('wants come before habits and logged today is kept', () => {
  const pages = buildPages(
    [{ id: 'w1', title: 'Call dentist' }, { id: '', title: 'skip' }],
    [
      { id: 'h1', name: 'Brush + floss', loggedToday: false },
      { id: 'h2', name: 'Gym', loggedToday: true }
    ]
  )
  assert.deepEqual(pages.map((page) => page.id), ['w1', 'h1', 'h2'])
  assert.equal(pages[0].kind, 'want')
  assert.equal(pages[1].logged, false)
  assert.equal(pages[2].logged, true)
})

test('caps the page list', () => {
  const habits = []
  for (let i = 0; i < 40; i++) habits.push({ id: 'h' + i, name: 'H' + i, loggedToday: false })
  const pages = buildPages([{ id: 'w1', title: 'Want' }], habits)
  assert.equal(pages.length, PAGE_CAP)
  assert.equal(pages[0].id, 'w1')
  assert.equal(pages[1].id, 'h0')
})

test('snap clamps the focus to the top at the start and the bottom at the end', () => {
  const stride = 88
  const count = 8
  assert.equal(windowStart(0, count), 0)
  assert.equal(windowStart(1, count), 0)
  assert.equal(windowStart(2, count), 1)
  assert.equal(windowStart(7, count), 5)
  assert.equal(nearestIndex(0, stride, count), 0)
  assert.equal(nearestIndex(stride * 0.4, stride, count), 0)
  assert.equal(nearestIndex(stride * 0.6, stride, count), 2)
  assert.equal(nearestIndex(stride * 2, stride, count), 3)
  assert.equal(nearestIndex(stride * 5, stride, count), 7)
  assert.equal(nearestIndex(stride * 20, stride, count), 7)
  assert.equal(nearestIndex(-40, stride, count), 0)
  assert.equal(nearestIndex(stride, stride, 3), 0)
  assert.equal(nearestIndex(10, stride, 0), 0)
  assert.equal(nearestIndex(100, 216, 4), 0)
  assert.equal(nearestIndex(108, 216, 4), 3)
  assert.equal(nearestIndex(900, 216, 4), 3)
})

test('step wraps and empty stays put', () => {
  assert.equal(step(0, -1, 3), 2)
  assert.equal(step(2, 1, 3), 0)
  assert.equal(step(4, 1, 0), 0)
})

test('mark logged only after an ok for that card', () => {
  const pages = buildPages(
    [{ id: 'w1', title: 'Call' }],
    [{ id: 'h1', name: 'Brush', loggedToday: false }]
  )
  const habit = markPage(pages, 'log_habit', 'h1')
  assert.equal(habit.noteLogged, false)
  assert.equal(habit.pages[1].logged, true)
  assert.equal(habit.pages[0].logged, false)
  const note = markPage(pages, 'note', null)
  assert.equal(note.noteLogged, true)
  assert.equal(note.pages[1].logged, false)
})

test('refresh keeps the same card', () => {
  const first = buildPages([], [{ id: 'h1', name: 'Brush', loggedToday: false }, { id: 'h2', name: 'Gym', loggedToday: false }])
  const next = buildPages(
    [{ id: 'w1', title: 'Call' }],
    [{ id: 'h1', name: 'Brush', loggedToday: true }, { id: 'h2', name: 'Gym', loggedToday: false }]
  )
  assert.equal(keepIndex(next, { kind: 'habit', id: 'h2', index: 1 }), 2)
  assert.equal(keepIndex(first, null), 0)
})

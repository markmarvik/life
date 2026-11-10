import assert from 'node:assert/strict'
import test from 'node:test'
import { PAGE_CAP, buildPages, keepIndex, markPage, step } from '../page/pager.js'

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

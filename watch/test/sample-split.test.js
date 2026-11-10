import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'

const indexSource = readFileSync(new URL('../page/index.js', import.meta.url), 'utf8')
const lifeSource = readFileSync(new URL('../page/life.js', import.meta.url), 'utf8')
const simSource = readFileSync(new URL('../page/sim.js', import.meta.url), 'utf8')
const app = readFileSync(new URL('../app.json', import.meta.url), 'utf8')

test('the device page never imports the sample path', () => {
  assert.equal(indexSource.includes('sample.js'), false)
  assert.equal(indexSource.includes('sim-want-dentist'), false)
  assert.equal(lifeSource.includes('sample.js'), false)
  assert.equal(lifeSource.includes('sim-want-dentist'), false)
  assert.equal(app.includes('page/sim'), false)
  assert.equal(simSource.includes('SAMPLE_PATH'), true)
})

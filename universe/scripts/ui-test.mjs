/**
 * Проверка тех сценариев взаимодействия, которые ломались на телефоне.
 * Работает по DOM, а не по store: важно именно то, что происходит от тапа.
 */
import { chromium } from 'playwright'

const browser = await chromium.launch({
  executablePath: '/opt/pw-browsers/chromium',
  args: ['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader','--no-sandbox'],
})
const page = await browser.newPage({
  viewport: { width: 393, height: 853 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true,
})
page.setDefaultTimeout(60000)
const errs = []
page.on('console', m => { if (m.type()==='error') errs.push(m.text()) })
page.on('pageerror', e => errs.push(e.message))
await page.goto('http://localhost:4173/', { waitUntil: 'networkidle' })
await page.waitForSelector('canvas'); await page.waitForTimeout(4000)

let failures = 0
function check(name, ok, extra = '') {
  console.log(`${ok ? 'OK  ' : 'FAIL'} ${name}${extra ? ' — ' + extra : ''}`)
  if (!ok) failures++
}

// --- 1. Подписи не должны перекрывать интерфейс ---
await page.evaluate(() => { window.__universeStore.getState().setLevel(2) })
await page.waitForTimeout(5500)

const overlap = await page.evaluate(() => {
  const ui = [...document.querySelectorAll('.hud, .time, .rail, .zoompad')]
    .map(n => n.getBoundingClientRect())
  const labels = [...document.querySelectorAll('.obj-label')]
    .filter(n => getComputedStyle(n).visibility !== 'hidden')
  const bad = []
  for (const l of labels) {
    const r = l.getBoundingClientRect()
    if (r.width === 0) continue
    for (const u of ui) {
      if (r.left < u.right && r.right > u.left && r.top < u.bottom && r.bottom > u.top) {
        bad.push(l.textContent.trim().slice(0, 30))
        break
      }
    }
  }
  return { bad, total: labels.length }
})
check('подписи не залезают на интерфейс', overlap.bad.length === 0,
      overlap.bad.length ? `нарушители: ${overlap.bad.join(', ')}` : `подписей видно: ${overlap.total}`)

// --- 2. Подписи не должны налезать друг на друга ---
const collisions = await page.evaluate(() => {
  const ls = [...document.querySelectorAll('.obj-label')]
    .filter(n => getComputedStyle(n).visibility !== 'hidden')
    .map(n => ({ t: n.textContent.trim().slice(0, 20), r: n.getBoundingClientRect() }))
    .filter(o => o.r.width > 0)
  const bad = []
  for (let i = 0; i < ls.length; i++)
    for (let j = i + 1; j < ls.length; j++) {
      const a = ls[i].r, b = ls[j].r
      if (a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top)
        bad.push(`${ls[i].t} × ${ls[j].t}`)
    }
  return bad
})
check('подписи не налезают друг на друга', collisions.length === 0, collisions.join('; '))

// --- 3. Подписи не должны обрезаться краем экрана ---
const clipped = await page.evaluate(() => {
  const w = innerWidth, h = innerHeight
  return [...document.querySelectorAll('.obj-label')]
    .filter(n => getComputedStyle(n).visibility !== 'hidden')
    .map(n => ({ t: n.textContent.trim().slice(0, 20), r: n.getBoundingClientRect() }))
    .filter(o => o.r.width > 0 && (o.r.left < 0 || o.r.top < 0 || o.r.right > w || o.r.bottom > h))
    .map(o => o.t)
})
check('подписи не обрезаны краем экрана', clipped.length === 0, clipped.join(', '))

// --- 4. Главное: крестик закрывает панель и не выбирает другой объект ---
await page.evaluate(() => {
  const st = window.__universeStore.getState()
  st.setFocus('mercury')
  st.select({ id: 'mercury', name: 'Меркурий', kind: 'Планета', facts: [{label:'Год',value:'88 суток'}] })
})
await page.waitForTimeout(1200)
check('панель открылась', await page.locator('.panel').count() === 1)

await page.locator('.panel__close').click()
await page.waitForTimeout(1200)
const after = await page.evaluate(() => {
  const s = window.__universeStore.getState()
  return { sel: s.selected?.name ?? null, focus: s.focusId }
})
check('крестик закрывает панель и ничего не выбирает заново',
      after.sel === null && after.focus === null,
      after.sel ? `осталось выбрано: ${after.sel}` : '')
check('панель исчезла из DOM', await page.locator('.panel').count() === 0)

// --- 5. Кнопки зума действительно меняют масштаб ---
const before = await page.evaluate(() => window.__universeStore.getState().cameraDist)
await page.locator('[aria-label="Отдалить"]').dispatchEvent('pointerdown')
await page.waitForTimeout(900)
await page.locator('[aria-label="Отдалить"]').dispatchEvent('pointerup')
await page.waitForTimeout(400)
const afterZoom = await page.evaluate(() => window.__universeStore.getState().cameraDist)
check('удержание «отдалить» увеличивает дистанцию', afterZoom > before * 1.3,
      `${before.toFixed(2)} → ${afterZoom.toFixed(2)}`)

const b2 = afterZoom
await page.locator('[aria-label="Приблизить"]').dispatchEvent('pointerdown')
await page.waitForTimeout(900)
await page.locator('[aria-label="Приблизить"]').dispatchEvent('pointerup')
await page.waitForTimeout(400)
const a2 = await page.evaluate(() => window.__universeStore.getState().cameraDist)
check('удержание «приблизить» уменьшает дистанцию', a2 < b2 * 0.8, `${b2.toFixed(2)} → ${a2.toFixed(2)}`)

// --- 6. Панель времени по умолчанию свёрнута и разворачивается ---
check('панель времени свёрнута', await page.locator('.time__body').count() === 0)
await page.locator('.time__expand').click()
await page.waitForTimeout(400)
check('панель времени разворачивается', await page.locator('.time__body').count() === 1)
await page.locator('.time__expand').click()
await page.waitForTimeout(300)

// --- 7. Настройки открываются и переключают тумблеры ---
await page.locator('[aria-label="Настройки отображения"]').click()
await page.waitForTimeout(300)
check('меню настроек открылось', await page.locator('.hud__settings').count() === 1)
const labelsBefore = await page.evaluate(() => window.__universeStore.getState().showLabels)
await page.locator('.hud__check input').nth(1).click()
await page.waitForTimeout(300)
const labelsAfter = await page.evaluate(() => window.__universeStore.getState().showLabels)
check('тумблер подписей работает', labelsBefore !== labelsAfter)

await browser.close()
if (errs.length) { console.error('ОШИБКИ КОНСОЛИ:', [...new Set(errs)]); failures++ }
console.log(failures === 0 ? '\nвсе проверки пройдены' : `\nпровалено проверок: ${failures}`)
process.exit(failures === 0 ? 0 : 1)

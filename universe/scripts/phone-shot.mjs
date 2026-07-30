// Съёмка в размерах реального телефона пользователя (1080×2344 CSS ~ 393×853)
import { chromium } from 'playwright'
import { mkdir } from 'node:fs/promises'
const browser = await chromium.launch({
  executablePath: '/opt/pw-browsers/chromium',
  args: ['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader','--no-sandbox'],
})
const page = await browser.newPage({
  viewport: { width: 393, height: 853 },
  deviceScaleFactor: 2,
  isMobile: true,
  hasTouch: true,
})
page.setDefaultTimeout(120000)
const errs = []
page.on('console', m => { if (m.type()==='error') errs.push(m.text()) })
page.on('pageerror', e => errs.push(e.message))
await page.goto('http://localhost:4173/', { waitUntil: 'networkidle' })
await page.waitForSelector('canvas'); await page.waitForTimeout(4000)
await mkdir('docs/phone', { recursive: true })

const shots = [
  { level: 0, name: 'p1-earth' },
  { level: 1, name: 'p2-earth-moon' },
  { level: 2, name: 'p3-solar' },
  { level: 3, name: 'p4-stars' },
  { level: 4, name: 'p5-milky-way' },
  { level: 5, name: 'p6-local-group' },
  { level: 6, name: 'p7-laniakea' },
  { level: 7, name: 'p8-universe' },
]
for (const s of shots) {
  await page.evaluate((lv) => {
    const st = window.__universeStore.getState()
    st.setLevel(lv); st.setSimTime(Date.UTC(2026, 6, 30, 8, 41)); st.setTimeScale(1)
  }, s.level)
  await page.waitForTimeout(5500)
  await page.screenshot({ path: `docs/phone/${s.name}.png` })
  console.log('ok', s.name)
}
// Панель фактов: тапаем по планете через store, как делает сцена
await page.evaluate(() => {
  const st = window.__universeStore.getState()
  st.setLevel(2)
})
await page.waitForTimeout(4000)
await page.evaluate(() => {
  window.__universeStore.getState().setFocus('mercury')
  window.__universeStore.getState().select({
    id: 'mercury', name: 'Меркурий', kind: 'Планета',
    blurb: 'Самая маленькая и самая близкая к Солнцу планета.',
    texture: '/tex/2k_mercury.jpg', distanceM: 1.2e11,
    facts: [
      { label: 'Расстояние от Солнца', value: '0,387 а.е.' },
      { label: 'Год', value: '87,97 земных суток' },
      { label: 'Температура', value: 'от −173 до +427 °C' },
    ],
    source: 'NASA Mercury Fact Sheet',
  })
})
await page.waitForTimeout(3000)
await page.screenshot({ path: 'docs/phone/p9-panel.png' })
console.log('ok p9-panel')
await browser.close()
if (errs.length) { console.error('ERRORS:', [...new Set(errs)]); process.exit(1) }
console.log('без ошибок консоли')

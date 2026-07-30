// Проверка деталей: наезд на конкретные объекты Солнечной системы
import { chromium } from 'playwright'
const browser = await chromium.launch({
  executablePath: '/opt/pw-browsers/chromium',
  args: ['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader','--no-sandbox'],
})
const page = await browser.newPage({ viewport: { width: 900, height: 900 }, deviceScaleFactor: 1 })
page.setDefaultTimeout(120000)
const errs = []
page.on('console', m => { if (m.type()==='error') errs.push(m.text()) })
page.on('pageerror', e => errs.push(e.message))
await page.goto('http://localhost:4173/', { waitUntil: 'networkidle' })
await page.waitForSelector('canvas'); await page.waitForTimeout(4000)

for (const [id, name] of [['saturn','saturn'],['jupiter','jupiter'],['venus','venus'],['earth','earth-in-ss'],['mars','mars']]) {
  await page.evaluate((id) => {
    const s = window.__universeStore.getState()
    s.setLevel(2); s.setRealScale(false)
    s.setSimTime(Date.UTC(2026, 6, 30, 14, 30)); s.setTimeScale(1)
    s.setFocus(id)
  }, id)
  await page.waitForTimeout(4500)
  await page.screenshot({ path: `/tmp/detail-${name}.png` })
  console.log('ok', name)
}
await browser.close()
if (errs.length) { console.error('ERRORS:', [...new Set(errs)]); process.exit(1) }
console.log('no console errors')

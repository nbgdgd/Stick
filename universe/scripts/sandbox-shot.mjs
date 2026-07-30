// Проверка песочницы: гоняем каждый пресет и следим за дрейфом энергии
import { chromium } from 'playwright'
const browser = await chromium.launch({
  executablePath: '/opt/pw-browsers/chromium',
  args: ['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader','--no-sandbox'],
})
const page = await browser.newPage({ viewport: { width: 900, height: 1200 }, deviceScaleFactor: 1 })
page.setDefaultTimeout(120000)
const errs = []
page.on('console', m => { if (m.type()==='error') errs.push(m.text()) })
page.on('pageerror', e => errs.push(e.message))
await page.goto('http://localhost:4173/', { waitUntil: 'networkidle' })
await page.waitForSelector('canvas'); await page.waitForTimeout(3000)
await page.evaluate(() => window.__universeStore.getState().setSandboxOpen(true))
await page.waitForTimeout(1500)

const presets = ['Звезда и планеты','Двойная звезда','Троянцы в точке L4','Три тела']
for (let i = 0; i < presets.length; i++) {
  await page.getByRole('button', { name: presets[i] }).click()
  await page.waitForTimeout(9000)   // даём симуляции пройти ~9 лет
  const drift = await page.locator('.sandbox__stats dd').nth(2).innerText()
  const years = await page.locator('.sandbox__stats dd').nth(1).innerText()
  const n = await page.locator('.sandbox__stats dd').nth(0).innerText()
  console.log(`${presets[i]}: тел ${n}, прошло ${years}, дрейф энергии ${drift}`)
  await page.screenshot({ path: `docs/shots/11-sandbox-${i}.png` })
}
await browser.close()
if (errs.length) { console.error('ERRORS:', [...new Set(errs)]); process.exit(1) }
console.log('без ошибок консоли')

/**
 * Скриншоты всех уровней в headless-браузере.
 *
 * Двойная задача: это и проверка, что приложение реально работает (ошибки
 * консоли и WebGL считаются падением), и материал для README, чтобы утром
 * не нужно было собирать APK ради взгляда на результат.
 *
 * Запуск: node scripts/shoot.mjs [--url http://...] [--out docs/shots]
 */

import { chromium } from 'playwright'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'

const args = process.argv.slice(2)
function arg(name, def) {
  const i = args.indexOf(`--${name}`)
  return i >= 0 && args[i + 1] ? args[i + 1] : def
}

const URL = arg('url', 'http://localhost:4173/')
const OUT = arg('out', 'docs/shots')
const WIDTH = parseInt(arg('width', '900'), 10)
const HEIGHT = parseInt(arg('height', '1600'), 10)

/** Уровни: имя файла и дополнительные действия перед съёмкой. */
const SHOTS = [
  { level: 0, name: '01-earth' },
  { level: 1, name: '02-earth-moon' },
  { level: 2, name: '03-solar-system' },
  { level: 2, name: '03-solar-system-real', realScale: true },
  { level: 3, name: '04-nearby-stars' },
  { level: 4, name: '05-milky-way' },
  { level: 5, name: '06-local-group' },
  { level: 6, name: '07-laniakea' },
  { level: 7, name: '08-observable-universe' },
]

const errors = []

const browser = await chromium.launch({
  executablePath: process.env.CHROMIUM_PATH || undefined,
  args: [
    // Программный WebGL: в контейнере нет GPU, но SwiftShader рендерит
    // тот же результат, только медленнее
    '--use-gl=angle',
    '--use-angle=swiftshader',
    '--enable-unsafe-swiftshader',
    '--no-sandbox',
  ],
})

const page = await browser.newPage({ viewport: { width: WIDTH, height: HEIGHT }, deviceScaleFactor: 1 })
page.setDefaultTimeout(120000)

page.on('console', (m) => {
  if (m.type() === 'error') errors.push(`console: ${m.text()}`)
})
page.on('pageerror', (e) => errors.push(`pageerror: ${e.message}`))

await page.goto(URL, { waitUntil: 'networkidle', timeout: 60000 })

// Ждём, пока появится канвас и на нём что-то нарисуется
await page.waitForSelector('canvas', { timeout: 30000 })
await page.waitForTimeout(4000)

await mkdir(OUT, { recursive: true })

for (const shot of SHOTS) {
  // Уровень и режим масштаба задаём напрямую через store: щёлкать по
  // интерфейсу медленнее и хрупче, а тут нужен детерминизм
  await page.evaluate(
    ({ level, realScale }) => {
      const s = window.__universeStore
      if (!s) throw new Error('store не выставлен в window.__universeStore')
      s.getState().setLevel(level)
      s.getState().setRealScale(!!realScale)
      // Фиксированная дата: иначе скриншоты не сравнить между запусками
      s.getState().setSimTime(Date.UTC(2026, 6, 30, 14, 30))
      s.getState().setTimeScale(1)
    },
    { level: shot.level, realScale: shot.realScale },
  )

  // Наезд камеры длится 1,7 с, плюс подгрузка текстур нового уровня
  await page.waitForTimeout(5200)

  const file = path.join(OUT, `${shot.name}.png`)
  await page.screenshot({ path: file })
  console.log(`снято: ${file}`)
}

// Отдельно — панель фактов, она главный элемент интерфейса
await page.evaluate(() => {
  const s = window.__universeStore
  s.getState().setLevel(2)
  s.getState().setRealScale(false)
})
await page.waitForTimeout(4000)
await page.evaluate(() => {
  window.__universeStore.getState().setFocus('saturn')
})
await page.waitForTimeout(2500)
await page.evaluate(() => {
  const s = window.__universeStore.getState()
  // Выбираем Сатурн напрямую тем же объектом, что положила бы сцена
  s.select({
    id: 'saturn',
    name: 'Сатурн',
    kind: 'Планета',
    blurb:
      'Средняя плотность 0,687 г/см³ — меньше плотности воды. Кольца состоят почти из чистого водяного льда.',
    texture: '/tex/2k_saturn.jpg',
    distanceM: 1.434e12,
    facts: [
      { label: 'Расстояние от Солнца', value: '9,537 а.е. (1,434 млрд км)' },
      { label: 'Год', value: '29,45 земных лет' },
      { label: 'Сутки', value: '10 ч 39 мин' },
      { label: 'Плотность', value: '0,687 г/см³ — легче воды' },
      { label: 'Кольца', value: 'от 74 500 до 140 220 км от центра' },
      { label: 'Спутники', value: '274 подтверждённых (2025)' },
    ],
    source: 'NASA Saturn Fact Sheet; элементы орбиты — JPL SSD',
  })
})
await page.waitForTimeout(1200)
await page.screenshot({ path: path.join(OUT, '09-fact-panel.png') })
console.log(`снято: ${path.join(OUT, '09-fact-panel.png')}`)

// И поиск
await page.evaluate(() => {
  window.__universeStore.getState().select(null)
  window.__universeStore.getState().setSearchOpen(true)
})
await page.waitForTimeout(500)
await page.keyboard.type('андро', { delay: 40 })
await page.waitForTimeout(700)
await page.screenshot({ path: path.join(OUT, '10-search.png') })
console.log(`снято: ${path.join(OUT, '10-search.png')}`)

await browser.close()

if (errors.length) {
  console.error(`\nОШИБКИ (${errors.length}):`)
  for (const e of [...new Set(errors)]) console.error('  ' + e)
  process.exit(1)
}
console.log('\nошибок в консоли нет')

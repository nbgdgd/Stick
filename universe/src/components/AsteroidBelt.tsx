/**
 * Пояс астероидов, пояс Койпера и облако Оорта.
 *
 * Это не отдельные каталожные объекты (их миллионы), а распределения,
 * построенные по наблюдаемой статистике:
 *  — главный пояс: 2,06–3,27 а.е., щели Кирквуда на резонансах с Юпитером
 *    3:1, 5:2, 7:3 и 2:1 реально пустые, они видны как тёмные полосы;
 *  — пояс Койпера: 30–50 а.е., сгущение у резонанса 3:2 с Нептуном (плутино);
 *  — облако Оорта: сферическая оболочка 2 000–100 000 а.е., показывается
 *    только в реальном масштабе, иначе не имеет смысла.
 *
 * Рисуется одним вызовом через THREE.Points — 12 000 отдельных мешей
 * телефон не переварит.
 */

import { useMemo } from 'react'
import * as THREE from 'three'
import { mulberry32 } from '../lib/rng'
import { useStore } from '../store'

interface Props {
  real: boolean
  compress: (au: number, real: boolean) => number
}

/** Щели Кирквуда: большие полуоси резонансов с Юпитером, а.е. */
const KIRKWOOD_GAPS = [
  { a: 2.06, width: 0.03 }, // 4:1
  { a: 2.5, width: 0.04 }, // 3:1
  { a: 2.82, width: 0.03 }, // 5:2
  { a: 2.96, width: 0.02 }, // 7:3
  { a: 3.27, width: 0.05 }, // 2:1
]

function gapFactor(a: number): number {
  let f = 1
  for (const g of KIRKWOOD_GAPS) {
    const d = Math.abs(a - g.a) / g.width
    // около резонанса плотность падает почти до нуля
    f *= 1 - 0.92 * Math.exp(-d * d)
  }
  return f
}

export function AsteroidBelt({ real, compress }: Props) {
  const select = useStore((s) => s.select)

  const mainBelt = useMemo(() => buildBelt({
    seed: 1337,
    count: 5200,
    aMin: 2.0,
    aMax: 3.4,
    inclSigmaDeg: 9,
    eSigma: 0.09,
    applyGaps: true,
    real,
    compress,
    color: new THREE.Color('#b6a894'),
  }), [real, compress])

  const kuiper = useMemo(() => buildBelt({
    seed: 4242,
    count: 6400,
    aMin: 30,
    aMax: 50,
    inclSigmaDeg: 14,
    eSigma: 0.13,
    applyGaps: false,
    plutinoBump: true,
    real,
    compress,
    color: new THREE.Color('#8fb3cc'),
  }), [real, compress])

  // Облако Оорта: только в реальном масштабе. В сжатом сжатие r^0.42
  // превратило бы оболочку 100 000 а.е. в тонкую скорлупу вокруг всего —
  // это дезинформация, лучше не показывать.
  const oort = useMemo(() => (real ? buildOort() : null), [real])

  const pointMat = useMemo(
    () =>
      new THREE.ShaderMaterial({
        transparent: true,
        depthWrite: false,
        vertexColors: true,
        vertexShader: /* glsl */ `
          attribute float aSize;
          varying vec3 vColor;
          void main() {
            vColor = color;
            vec4 mv = modelViewMatrix * vec4(position, 1.0);
            gl_Position = projectionMatrix * mv;
            gl_PointSize = clamp(aSize * 220.0 / -mv.z, 0.7, 5.0);
          }
        `,
        fragmentShader: /* glsl */ `
          varying vec3 vColor;
          void main() {
            vec2 c = gl_PointCoord - 0.5;
            if (length(c) > 0.5) discard;
            gl_FragColor = vec4(vColor, 0.85);
          }
        `,
      }),
    [],
  )

  const oortMat = useMemo(
    () =>
      new THREE.PointsMaterial({
        color: '#6d7f99',
        size: 1.4,
        sizeAttenuation: false,
        transparent: true,
        opacity: 0.32,
        depthWrite: false,
      }),
    [],
  )

  return (
    <group>
      <points
        geometry={mainBelt}
        material={pointMat}
        onClick={(e) => {
          e.stopPropagation()
          select({
            id: 'main-belt',
            name: 'Главный пояс астероидов',
            kind: 'Область',
            blurb:
              'Между Марсом и Юпитером, 2,06–3,27 а.е. Тёмные полосы в модели — щели Кирквуда: ' +
              'на этих орбитах период обращения находится в целочисленном резонансе с Юпитером, ' +
              'и его возмущения выбрасывают оттуда тела.',
            facts: [
              { label: 'Границы', value: '2,06–3,27 а.е.' },
              { label: 'Известно тел', value: '> 1,3 млн с диаметром более 1 км' },
              { label: 'Суммарная масса', value: '≈ 3 % массы Луны' },
              { label: 'Крупнейшие', value: 'Церера, Веста, Паллада, Гигея' },
              { label: 'Щели Кирквуда', value: 'резонансы 4:1, 3:1, 5:2, 7:3, 2:1 с Юпитером' },
              { label: 'Среднее расстояние между телами', value: 'около 1 млн км' },
            ],
            source: 'JPL Small-Body Database; распределение процедурное по статистике каталога',
            artistic: true,
          })
        }}
      />
      <points
        geometry={kuiper}
        material={pointMat}
        onClick={(e) => {
          e.stopPropagation()
          select({
            id: 'kuiper-belt',
            name: 'Пояс Койпера',
            kind: 'Область',
            blurb:
              'Кольцо ледяных тел за орбитой Нептуна, 30–50 а.е. Сгущение в модели соответствует ' +
              'резонансу 3:2 с Нептуном — там сидят «плутино», включая сам Плутон.',
            facts: [
              { label: 'Границы', value: '30–50 а.е.' },
              { label: 'Известно тел', value: '> 3 000 (оценка: более 100 000 крупнее 100 км)' },
              { label: 'Суммарная масса', value: '≈ 1–10 % массы Земли' },
              { label: 'Крупнейшие', value: 'Плутон, Эрида, Хаумеа, Макемаке' },
              { label: 'Плутино', value: 'резонанс 3:2 с Нептуном' },
              { label: 'Исследование', value: 'New Horizons: Плутон 2015, Аррокот 2019' },
            ],
            source: 'JPL Small-Body Database; распределение процедурное по статистике каталога',
            artistic: true,
          })
        }}
      />
      {oort && (
        <points
          geometry={oort}
          material={oortMat}
          onClick={(e) => {
            e.stopPropagation()
            select({
              id: 'oort-cloud',
              name: 'Облако Оорта',
              kind: 'Область',
              blurb:
                'Сферическая оболочка ледяных тел на расстоянии от 2 000 до 100 000 а.е. — ' +
                'источник долгопериодических комет. Ни одно её тело пока не наблюдалось напрямую: ' +
                'существование выведено из распределения орбит комет.',
              facts: [
                { label: 'Внутренняя граница', value: '≈ 2 000 а.е.' },
                { label: 'Внешняя граница', value: '≈ 100 000 а.е. (1,6 св. года)' },
                { label: 'Тел крупнее 1 км', value: 'оценка 10¹²' },
                { label: 'Суммарная масса', value: '≈ 5 масс Земли (оценка)' },
                { label: 'Наблюдалось напрямую', value: 'ничего — вывод по орбитам комет' },
                { label: 'Половина пути до Проксимы', value: 'внешний край облака' },
              ],
              source: 'Гипотеза Оорта (1950); распределение процедурное. Прямых наблюдений нет',
              artistic: true,
            })
          }}
        />
      )}
    </group>
  )
}

interface BeltParams {
  seed: number
  count: number
  aMin: number
  aMax: number
  inclSigmaDeg: number
  eSigma: number
  applyGaps: boolean
  plutinoBump?: boolean
  real: boolean
  compress: (au: number, real: boolean) => number
  color: THREE.Color
}

function buildBelt(p: BeltParams): THREE.BufferGeometry {
  const rnd = mulberry32(p.seed)
  const pos: number[] = []
  const col: number[] = []
  const size: number[] = []

  let placed = 0
  let guard = 0
  while (placed < p.count && guard < p.count * 40) {
    guard++
    const a = p.aMin + rnd() * (p.aMax - p.aMin)

    // Отбраковка по плотности: щели Кирквуда и сгущение плутино
    let accept = 1
    if (p.applyGaps) accept *= gapFactor(a)
    if (p.plutinoBump) {
      // резонанс 3:2 с Нептуном: a ≈ 39,4 а.е.
      const d = Math.abs(a - 39.4) / 0.7
      accept *= 1 + 1.8 * Math.exp(-d * d)
      // внешний край поясa обрывается резко на резонансе 2:1 (47,8 а.е.)
      if (a > 47.8) accept *= 0.25
    }
    if (rnd() > accept / (p.plutinoBump ? 2.8 : 1)) continue

    const e = Math.abs(rnd() + rnd() + rnd() - 1.5) * p.eSigma * 1.4
    const incl = (rnd() + rnd() + rnd() - 1.5) * p.inclSigmaDeg * (Math.PI / 180) * 1.3
    const nu = rnd() * Math.PI * 2
    const node = rnd() * Math.PI * 2

    // радиус на данной истинной аномалии
    const r = (a * (1 - e * e)) / (1 + e * Math.cos(nu))
    const rs = p.compress(r, p.real)
    const k = rs / r

    const xo = r * Math.cos(nu + node)
    const yo = r * Math.sin(nu + node)
    const z = yo * Math.sin(incl)
    const y = yo * Math.cos(incl)

    pos.push(xo * k, z * k, -y * k)
    const shade = 0.6 + rnd() * 0.7
    col.push(p.color.r * shade, p.color.g * shade, p.color.b * shade)
    size.push(0.6 + Math.pow(rnd(), 4) * 2.2)
    placed++
  }

  const g = new THREE.BufferGeometry()
  g.setAttribute('position', new THREE.BufferAttribute(new Float32Array(pos), 3))
  g.setAttribute('color', new THREE.BufferAttribute(new Float32Array(col), 3))
  g.setAttribute('aSize', new THREE.BufferAttribute(new Float32Array(size), 1))
  return g
}

/** Облако Оорта: сферически симметричная оболочка с плотностью ~ r^-3.5. */
function buildOort(): THREE.BufferGeometry {
  const rnd = mulberry32(90210)
  const n = 4000
  const pos = new Float32Array(n * 3)
  for (let i = 0; i < n; i++) {
    // Обратное преобразование для плотности n(r) ~ r^-3.5 между rMin и rMax.
    // В сцене единица = 1 а.е., но 100 000 а.е. вылетит за far камеры,
    // поэтому показываем внутреннюю часть облака: 2 000–20 000 а.е.
    const rMin = 2000
    const rMax = 20000
    const u = rnd()
    const r = Math.pow(Math.pow(rMin, -0.5) * (1 - u) + Math.pow(rMax, -0.5) * u, -2)
    const z = rnd() * 2 - 1
    const t = rnd() * Math.PI * 2
    const s = Math.sqrt(1 - z * z)
    pos[i * 3] = r * s * Math.cos(t)
    pos[i * 3 + 1] = r * z
    pos[i * 3 + 2] = r * s * Math.sin(t)
  }
  const g = new THREE.BufferGeometry()
  g.setAttribute('position', new THREE.BufferAttribute(pos, 3))
  return g
}

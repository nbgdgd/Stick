/**
 * Уровень 6 — Местная группа.
 *
 * Положения галактик настоящие: RA/Dec из каталогов и измеренные расстояния
 * (цефеиды, вершина ветви красных гигантов, сверхновые Ia). Сводка — каталог
 * McConnachie (2012) и NED.
 *
 * Сами галактики — процедурные: спирали получают диск с рукавами,
 * эллиптические и карликовые сферические — сфероид с подходящим профилем
 * плотности. Реальных трёхмерных моделей этих галактик не существует,
 * поэтому вид — реконструкция, а положение и размер — данные.
 */

import { useMemo, useRef } from 'react'
import { useFrame } from '@react-three/fiber'
import * as THREE from 'three'
import { useStore } from '../store'
import { LOCAL_GROUP, type Galaxy } from '../data/galaxies'
import { raDecToXyz, LY_M } from '../lib/astro'
import { mulberry32, gaussian, hashString } from '../lib/rng'
import { Label } from '../components/Label'
import { registerFocus } from '../lib/focus'

/** 1 единица сцены = 10 000 световых лет. */
const UNIT_LY = 10000

function galaxyPos(g: Galaxy): THREE.Vector3 {
  if (g.id === 'milky-way') return new THREE.Vector3(0, 0, 0)
  const [x, y, z] = raDecToXyz(g.ra, g.dec, g.distLy / UNIT_LY)
  return new THREE.Vector3(x, z, -y)
}

export function LocalGroupScene() {
  const showLabels = useStore((s) => s.showLabels)
  const cameraDist = useStore((s) => s.cameraDist)
  const select = useStore((s) => s.select)
  const setFocus = useStore((s) => s.setFocus)

  const items = useMemo(
    () =>
      LOCAL_GROUP.map((g) => ({
        galaxy: g,
        pos: galaxyPos(g),
        // Радиус на сцене: настоящий диаметр, но не меньше порога видимости
        radius: Math.max(g.diameterLy / UNIT_LY / 2, 0.9),
      })),
    [],
  )

  const pointMat = useMemo(() => makeGalaxyPointMaterial(), [])

  function pick(g: Galaxy) {
    setFocus(g.id)
    const facts: { label: string; value: string }[] = [
      { label: 'Расстояние', value: g.distLy === 0 ? '—' : formatLy(g.distLy) },
      { label: 'Тип', value: g.type },
      { label: 'Диаметр', value: formatLy(g.diameterLy) },
    ]
    if (g.massSun) {
      facts.push({ label: 'Звёздная масса', value: `${g.massSun.toExponential(1).replace('e+', ' × 10^')} M☉` })
    }
    if (g.absMag !== undefined) facts.push({ label: 'Абсолютная величина', value: `${g.absMag}ᵐ` })
    if (g.catalog) facts.push({ label: 'Каталог', value: g.catalog })
    if (g.satelliteOf) {
      facts.push({
        label: 'Спутник',
        value: g.satelliteOf === 'milky-way' ? 'Млечного Пути' : g.satelliteOf === 'm31' ? 'Андромеды' : 'Треугольника',
      })
    }
    if (g.distLy > 0) {
      facts.push({ label: 'Свет идёт до нас', value: `${formatLy(g.distLy).replace('св. лет', 'лет')}` })
    }

    select({
      id: g.id,
      name: g.name,
      kind: 'Галактика',
      blurb: g.note,
      facts,
      distanceM: g.distLy * LY_M,
      source:
        'Положения и расстояния — McConnachie (2012), AJ 144, 4 и NED. ' +
        'Изображение галактики — процедурная реконструкция по её морфологическому типу',
      artistic: true,
    })
  }

  return (
    <group>
      <ambientLight intensity={0.9} />

      {items.map((it) => (
        <GalaxyBody
          key={it.galaxy.id}
          item={it}
          material={pointMat}
          showLabel={
            showLabels &&
            (it.galaxy.diameterLy > 15000 || cameraDist < 1200 || it.galaxy.id === 'milky-way')
          }
          onPick={() => pick(it.galaxy)}
        />
      ))}

      {/* Ось «Млечный Путь — Андромеда»: главная ось всей группы */}
      <MwToM31Line items={items} />
    </group>
  )
}

function MwToM31Line({ items }: { items: { galaxy: Galaxy; pos: THREE.Vector3 }[] }) {
  const showOrbits = useStore((s) => s.showOrbits)
  const m31 = items.find((i) => i.galaxy.id === 'm31')
  const geom = useMemo(() => {
    if (!m31) return null
    return new THREE.BufferGeometry().setFromPoints([new THREE.Vector3(0, 0, 0), m31.pos])
  }, [m31])

  if (!showOrbits || !geom) return null
  return (
    <group>
      {/* @ts-expect-error примитив line из three, типы R3F его не покрывают */}
      <line geometry={geom}>
        <lineBasicMaterial color="#ffcc66" transparent opacity={0.2} />
      </line>
      <Label
        position={[m31!.pos.x / 2, m31!.pos.y / 2 + 8, m31!.pos.z / 2]}
        text="2,54 млн св. лет"
        sub="сближаемся на 110 км/с"
        small
      />
    </group>
  )
}

function GalaxyBody({
  item,
  material,
  showLabel,
  onPick,
}: {
  item: { galaxy: Galaxy; pos: THREE.Vector3; radius: number }
  material: THREE.ShaderMaterial
  showLabel: boolean
  onPick: () => void
}) {
  const { galaxy, pos, radius } = item
  const groupRef = useRef<THREE.Group>(null)

  const geom = useMemo(() => buildGalaxyPoints(galaxy, radius), [galaxy, radius])

  // Ориентация диска — случайная, но детерминированная по имени: настоящие
  // углы наклона известны только для части галактик, и подставлять их
  // выборочно было бы хуже, чем честно рандомизировать от сида
  const rot = useMemo(() => {
    const rnd = mulberry32(hashString(galaxy.id))
    return [rnd() * Math.PI, rnd() * Math.PI * 2, rnd() * Math.PI * 0.6] as [number, number, number]
  }, [galaxy.id])

  useFrame((_, dt) => {
    if (groupRef.current) groupRef.current.rotation.y += dt * 0.02
    registerFocus(galaxy.id, pos, radius * 2.2)
  })

  const isDwarf = galaxy.diameterLy < 12000

  return (
    <group position={pos} rotation={rot}>
      <group ref={groupRef}>
        <points geometry={geom} material={material} onClick={onPick} />
      </group>
      {/* Кликабельная невидимая сфера: по точкам попасть пальцем сложно */}
      <mesh scale={radius * 1.3} onClick={onPick} visible={false}>
        <sphereGeometry args={[1, 8, 6]} />
      </mesh>
      {showLabel && (
        <Label
          position={[0, radius * 1.5 + 2, 0]}
          text={galaxy.name}
          sub={galaxy.distLy > 0 ? formatLy(galaxy.distLy) : 'мы здесь'}
          small={isDwarf}
          onClick={onPick}
        />
      )}
    </group>
  )
}

function makeGalaxyPointMaterial() {
  return new THREE.ShaderMaterial({
    transparent: true,
    depthWrite: false,
    vertexColors: true,
    blending: THREE.AdditiveBlending,
    vertexShader: /* glsl */ `
      attribute float aSize;
      varying vec3 vColor;
      void main() {
        vColor = color;
        vec4 mv = modelViewMatrix * vec4(position, 1.0);
        gl_Position = projectionMatrix * mv;
        gl_PointSize = clamp(aSize * 300.0 / -mv.z, 0.7, 6.0);
      }
    `,
    fragmentShader: /* glsl */ `
      varying vec3 vColor;
      void main() {
        vec2 c = gl_PointCoord - 0.5;
        float d = length(c);
        if (d > 0.5) discard;
        gl_FragColor = vec4(vColor, exp(-d * d * 10.0) * 0.9);
      }
    `,
  })
}

/**
 * Точечная модель галактики по её морфологическому типу.
 * Спирали (S...) получают диск с рукавами, иррегулярные (Irr, Im) — клочковатое
 * распределение, эллиптические и dSph — сфероид с профилем де Вокулёра.
 */
function buildGalaxyPoints(g: Galaxy, radius: number): THREE.BufferGeometry {
  const rnd = mulberry32(hashString(g.id) ^ 0x5f3a)
  const type = g.type

  const isSpiral = /^S/.test(type)
  const isIrregular = /Irr|I[ABm]|^d?I/.test(type) && !isSpiral
  // размер выборки по светимости: яркие галактики получают больше точек
  const lum = g.absMag !== undefined ? Math.pow(10, (-g.absMag - 8) / 4.2) : 20
  const N = Math.round(Math.max(140, Math.min(5200, lum * 60)))

  const pos = new Float32Array(N * 3)
  const col = new Float32Array(N * 3)
  const size = new Float32Array(N)

  const cBlue = new THREE.Color('#9dc0ff')
  const cWhite = new THREE.Color('#fff4dd')
  const cRed = new THREE.Color('#ffb070')

  for (let i = 0; i < N; i++) {
    let x = 0
    let y = 0
    let z = 0
    let c = new THREE.Color()

    if (isSpiral) {
      const inBulge = rnd() < 0.22
      if (inBulge) {
        const r = radius * 0.22 * Math.pow(rnd(), 1.6)
        const zz = rnd() * 2 - 1
        const t = rnd() * Math.PI * 2
        const s = Math.sqrt(1 - zz * zz)
        x = r * s * Math.cos(t)
        z = r * s * Math.sin(t)
        y = r * zz * 0.65
        c.copy(cRed).lerp(cWhite, rnd() * 0.5)
      } else {
        // две-четыре ветви логарифмической спирали
        const arms = /c|d/.test(type) ? 4 : 2
        const arm = Math.floor(rnd() * arms)
        const t = Math.pow(rnd(), 0.75)
        const r = radius * (0.12 + t * 0.88)
        const pitch = 0.22 + rnd() * 0.05
        let theta = (arm / arms) * Math.PI * 2 + Math.log(r / (radius * 0.12)) / pitch
        theta += gaussian(rnd) * (0.16 + t * 0.22)
        x = r * Math.cos(theta)
        z = r * Math.sin(theta)
        y = gaussian(rnd) * radius * 0.035
        c.copy(cWhite).lerp(cBlue, 0.3 + rnd() * 0.6)
        if (rnd() < 0.05) c.copy(cBlue) // области HII
      }
    } else if (isIrregular) {
      // Клочковатое распределение: несколько центров звездообразования
      const clumps = 4
      const ci = Math.floor(rnd() * clumps)
      const crnd = mulberry32(hashString(g.id) + ci * 7717)
      const cx = (crnd() * 2 - 1) * radius * 0.6
      const cy = (crnd() * 2 - 1) * radius * 0.3
      const cz = (crnd() * 2 - 1) * radius * 0.6
      x = cx + gaussian(rnd) * radius * 0.3
      y = cy + gaussian(rnd) * radius * 0.18
      z = cz + gaussian(rnd) * radius * 0.3
      c.copy(cBlue).lerp(cWhite, rnd())
    } else {
      // Эллиптическая или карликовая сферическая: профиль плотности ~ r^-2
      const r = radius * Math.pow(rnd(), 1.9)
      const zz = rnd() * 2 - 1
      const t = rnd() * Math.PI * 2
      const s = Math.sqrt(1 - zz * zz)
      // умеренная сплющенность из типа: E5 сплюснута сильнее, чем E0
      const flat = /E(\d)/.exec(type) ? 1 - parseInt(/E(\d)/.exec(type)![1]) * 0.07 : 0.85
      x = r * s * Math.cos(t)
      z = r * s * Math.sin(t)
      y = r * zz * flat
      c.copy(cRed).lerp(cWhite, rnd() * 0.65)
    }

    pos[i * 3] = x
    pos[i * 3 + 1] = y
    pos[i * 3 + 2] = z
    const b = 0.45 + Math.pow(rnd(), 1.7) * 0.85
    col[i * 3] = c.r * b
    col[i * 3 + 1] = c.g * b
    col[i * 3 + 2] = c.b * b
    size[i] = 0.5 + Math.pow(rnd(), 3) * 2.4
  }

  const geo = new THREE.BufferGeometry()
  geo.setAttribute('position', new THREE.BufferAttribute(pos, 3))
  geo.setAttribute('color', new THREE.BufferAttribute(col, 3))
  geo.setAttribute('aSize', new THREE.BufferAttribute(size, 1))
  return geo
}

function formatLy(ly: number): string {
  if (ly < 1000) return `${ly} св. лет`
  if (ly < 1e6) return `${(ly / 1000).toLocaleString('ru-RU', { maximumFractionDigits: 0 })} тыс. св. лет`
  return `${(ly / 1e6).toLocaleString('ru-RU', { maximumFractionDigits: 2 })} млн св. лет`
}

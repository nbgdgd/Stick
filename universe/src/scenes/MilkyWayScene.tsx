/**
 * Уровень 5 — Млечный Путь.
 *
 * Отдельные звёзды здесь не разрешаются: их сотни миллиардов, и любая
 * «модель Галактики» — это распределение плотности, а не каталог. Диск
 * генерируется процедурно, но не произвольно:
 *
 *  — рукава заданы как логарифмические спирали с углами закрутки, измеренными
 *    по VLBA-параллаксам областей звездообразования (Reid et al. 2019);
 *  — экспоненциальный профиль плотности диска по радиусу и по высоте;
 *  — балдж — сплющенный сфероид с барной перемычкой;
 *  — цвет зависит от радиуса: в балдже старые красные звёзды, в рукавах
 *    молодые голубые — это наблюдаемый градиент населений.
 *
 * Отдельными объектами показаны реальные Sgr A*, шаровые скопления и
 * туманности по их галактическим координатам.
 */

import { useMemo, useRef } from 'react'
import { useFrame } from '@react-three/fiber'
import * as THREE from 'three'
import { useStore } from '../store'
import { MILKY_WAY, SPIRAL_ARMS, GALACTIC_OBJECTS } from '../data/galaxies'
import { galacticToXyz, LY_M } from '../lib/astro'
import { mulberry32, gaussian } from '../lib/rng'
import { Label } from '../components/Label'
import { registerFocus } from '../lib/focus'
import { GlowSprite } from '../components/GlowSprite'

/** 1 единица сцены = 1000 световых лет. */
const KLY = 1000

/** Солнце: галактический центр в начале координат, Солнце на −X. */
const SUN_POS = new THREE.Vector3(-MILKY_WAY.sunDistanceLy / KLY, 0, 0)

export function MilkyWayScene() {
  const showLabels = useStore((s) => s.showLabels)
  const cameraDist = useStore((s) => s.cameraDist)
  const select = useStore((s) => s.select)
  const setFocus = useStore((s) => s.setFocus)

  const disk = useMemo(() => buildDisk(), [])
  const bulge = useMemo(() => buildBulge(), [])
  const halo = useMemo(() => buildHalo(), [])

  const pointMat = useMemo(
    () =>
      new THREE.ShaderMaterial({
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
            gl_PointSize = clamp(aSize * 260.0 / -mv.z, 0.6, 7.0);
          }
        `,
        fragmentShader: /* glsl */ `
          varying vec3 vColor;
          void main() {
            vec2 c = gl_PointCoord - 0.5;
            float d = length(c);
            if (d > 0.5) discard;
            float a = exp(-d * d * 11.0);
            gl_FragColor = vec4(vColor, a * 0.85);
          }
        `,
      }),
    [],
  )

  const galaxyGroup = useRef<THREE.Group>(null)

  // Медленное вращение диска. Полный галактический год Солнца — 230 млн лет;
  // здесь вращение чисто иллюстративное, «твёрдотельное», реальная кривая
  // вращения плоская и рукава как узор вращаются медленнее звёзд.
  useFrame((_, dt) => {
    if (galaxyGroup.current) galaxyGroup.current.rotation.y += dt * 0.006
  })

  function selectGalaxy() {
    setFocus(null)
    select({
      id: 'milky-way',
      name: MILKY_WAY.name,
      kind: 'Галактика',
      blurb:
        'Спиральная галактика с барной перемычкой. Мы находимся в Местном рукаве, ' +
        'на 26 670 световых лет от центра — примерно на полпути от ядра к краю диска.',
      facts: MILKY_WAY.facts,
      source: MILKY_WAY.source,
      artistic: true,
      distanceM: 0,
    })
  }

  return (
    <group>
      <ambientLight intensity={0.8} />

      <group ref={galaxyGroup}>
        <points geometry={disk} material={pointMat} onClick={selectGalaxy} />
        <points geometry={bulge} material={pointMat} />
        <points geometry={halo} material={pointMat} />

        {/* Пылевые полосы: тёмный диск чуть меньше звёздного, поглощает свет.
            Без него галактика выглядит как размытое пятно без структуры. */}
        <mesh rotation={[Math.PI / 2, 0, 0]}>
          <ringGeometry args={[8, 48, 96, 1]} />
          <meshBasicMaterial color="#120a06" transparent opacity={0.22} side={THREE.DoubleSide} depthWrite={false} />
        </mesh>
      </group>

      {/* Реальные объекты Галактики */}
      {GALACTIC_OBJECTS.map((o) => {
        const [x, y, z] = galacticToXyz(o.l, o.b, o.distLy / KLY)
        // от Солнца, а не от центра: галактические координаты отсчитываются от нас
        const p = new THREE.Vector3(SUN_POS.x + x, z, -y)
        return (
          <GalacticMarker
            key={o.id}
            id={o.id}
            pos={p}
            kind={o.kind}
            name={o.name}
            showLabel={showLabels && (o.kind === 'center' || cameraDist < 220)}
            onPick={() => {
              setFocus(o.id)
              select({
                id: o.id,
                name: o.name,
                kind:
                  o.kind === 'center' ? 'Сверхмассивная чёрная дыра'
                  : o.kind === 'cluster' ? 'Шаровое скопление'
                  : o.kind === 'nebula' ? 'Туманность'
                  : 'Остаток сверхновой',
                blurb: o.note,
                facts: [
                  ...o.facts,
                  { label: 'Галактическая долгота', value: `${o.l.toFixed(2)}°` },
                  { label: 'Галактическая широта', value: `${o.b.toFixed(2)}°` },
                  { label: 'Каталог', value: o.catalog ?? '—' },
                ],
                distanceM: o.distLy * LY_M,
                source: 'Координаты и расстояния — SIMBAD / NED. Внешний вид процедурный',
                artistic: true,
              })
            }}
          />
        )
      })}

      {/* Положение Солнца */}
      <group position={SUN_POS}>
        <mesh
          scale={0.9}
          onClick={(e) => {
            e.stopPropagation()
            setFocus('sun-in-galaxy')
            select({
              id: 'sun-in-galaxy',
              name: 'Солнце (наше положение)',
              kind: 'Ориентир',
              blurb:
                'Мы в Местном рукаве — небольшом отроге между рукавами Персея и Стрельца. ' +
                'Один оборот вокруг центра Галактики занимает 225–250 млн лет: с момента, ' +
                'когда по Земле ходили первые динозавры, Солнце сделало примерно один круг.',
              facts: [
                { label: 'Расстояние до центра', value: '26 670 св. лет (8,178 кпк)' },
                { label: 'Орбитальная скорость', value: '≈ 230 км/с' },
                { label: 'Галактический год', value: '225–250 млн лет' },
                { label: 'Высота над плоскостью диска', value: '≈ 65 св. лет к северу' },
                { label: 'Рукав', value: 'Местный рукав (отрог Ориона)' },
                { label: 'Оборотов за время жизни Солнца', value: '≈ 20' },
              ],
              distanceM: 0,
              source: 'GRAVITY Collaboration (2019); Gaia DR3',
            })
          }}
        >
          <sphereGeometry args={[1, 12, 10]} />
          <meshBasicMaterial color="#fff3c4" />
        </mesh>
        {showLabels && <Label position={[0, 3.5, 0]} text="Солнце" sub="26 670 св. лет от центра" priority={125} />}
      </group>

      {/* Подписи рукавов */}
      {showLabels && cameraDist < 400 && <ArmLabels />}
    </group>
  )
}

function GalacticMarker({
  id,
  pos,
  kind,
  name,
  showLabel,
  onPick,
}: {
  id: string
  pos: THREE.Vector3
  kind: string
  name: string
  showLabel: boolean
  onPick: () => void
}) {
  const color =
    kind === 'center' ? '#ffaa33'
    : kind === 'cluster' ? '#ffe0a0'
    : kind === 'nebula' ? '#ff7799'
    : '#88ddff'
  // Шаровые скопления компактны, туманности размыты — отсюда разная жёсткость
  const size = kind === 'center' ? 4.5 : kind === 'cluster' ? 2.6 : 3.4
  const hardness = kind === 'cluster' ? 14 : kind === 'nebula' ? 3.5 : 10

  useFrame(() => {
    registerFocus(id, pos, size)
  })

  return (
    <group position={pos}>
      <GlowSprite
        color={color}
        size={size}
        intensity={kind === 'center' ? 1 : 0.7}
        hardness={hardness}
        spikes={kind === 'center'}
        onClick={onPick as never}
      />
      {/* Небольшая непрозрачная сердцевина: по свечению трудно попасть пальцем */}
      <mesh scale={size * 0.16} onClick={onPick}>
        <sphereGeometry args={[1, 10, 8]} />
        <meshBasicMaterial color={color} />
      </mesh>
      {showLabel && (
        <Label
          position={[0, size * 0.85, 0]}
          text={name}
          small
          priority={kind === 'center' ? 120 : 60}
          onClick={onPick}
        />
      )}
    </group>
  )
}

function ArmLabels() {
  const labels = useMemo(() => {
    return SPIRAL_ARMS.map((arm) => {
      // точка на середине рукава
      const r = (arm.rStartLy + arm.rEndLy) / 2 / KLY
      const pitch = (arm.pitchDeg * Math.PI) / 180
      const theta0 = (arm.thetaStart * Math.PI) / 180
      const theta = theta0 + Math.log(r / (arm.rStartLy / KLY)) / Math.tan(pitch)
      return {
        id: arm.id,
        name: arm.name,
        pos: [r * Math.cos(theta), 1.5, r * Math.sin(theta)] as [number, number, number],
      }
    })
  }, [])

  return (
    <>
      {labels.map((l) => (
        <Label key={l.id} position={l.pos} text={l.name} small priority={40} />
      ))}
    </>
  )
}

/**
 * Звёздный диск. Точки раскладываются вдоль логарифмических спиралей
 * с гауссовым разбросом поперёк рукава, плюс фоновое межрукавное население
 * (в реальности между рукавами звёзды тоже есть, просто плотность ниже).
 */
function buildDisk(): THREE.BufferGeometry {
  const rnd = mulberry32(31415)
  const N = 26000
  const pos = new Float32Array(N * 3)
  const col = new Float32Array(N * 3)
  const size = new Float32Array(N)

  const rDiskLy = MILKY_WAY.diskDiameterLy / 2
  const totalWeight = SPIRAL_ARMS.reduce((s, a) => s + a.weight, 0)

  const cYoung = new THREE.Color('#9fc4ff') // молодые голубые звёзды в рукавах
  const cMid = new THREE.Color('#fff2d0')
  const cOld = new THREE.Color('#ffb877') // старое красноватое население

  for (let i = 0; i < N; i++) {
    const interArm = rnd() < 0.32 // фон между рукавами

    let r: number
    let theta: number
    if (interArm) {
      // экспоненциальный диск: шкала длины ≈ 8 500 св. лет
      r = -8500 * Math.log(1 - rnd() * 0.985)
      r = Math.min(r, rDiskLy)
      theta = rnd() * Math.PI * 2
    } else {
      // выбираем рукав по весу
      let pickW = rnd() * totalWeight
      let arm = SPIRAL_ARMS[0]
      for (const a of SPIRAL_ARMS) {
        pickW -= a.weight
        if (pickW <= 0) {
          arm = a
          break
        }
      }
      // положение вдоль рукава
      const t = Math.pow(rnd(), 0.85)
      r = arm.rStartLy + t * (arm.rEndLy - arm.rStartLy)
      const pitch = (arm.pitchDeg * Math.PI) / 180
      const theta0 = (arm.thetaStart * Math.PI) / 180
      theta = theta0 + Math.log(r / arm.rStartLy) / Math.tan(pitch)
      // разброс поперёк рукава растёт с радиусом — рукава «размахриваются»
      const spread = (0.055 + 0.05 * (r / rDiskLy)) * (1 + Math.abs(gaussian(rnd)) * 0.4)
      theta += gaussian(rnd) * spread
      r *= 1 + gaussian(rnd) * 0.035
    }

    // Толщина диска: экспоненциальная по высоте, шкала ≈ 1 000 св. лет,
    // и растёт к краю (реальный эффект «раструба»)
    const flare = 1 + 1.8 * Math.pow(Math.max(0, r - 25000) / rDiskLy, 2)
    const z = gaussian(rnd) * MILKY_WAY.thinDiskThicknessLy * 0.42 * flare

    pos[i * 3] = (r * Math.cos(theta)) / KLY
    pos[i * 3 + 1] = z / KLY
    pos[i * 3 + 2] = (r * Math.sin(theta)) / KLY

    // Цветовой градиент: наружу — голубее (более активное звездообразование)
    const frac = Math.min(1, r / rDiskLy)
    const c = new THREE.Color()
    if (frac < 0.35) c.copy(cOld).lerp(cMid, frac / 0.35)
    else c.copy(cMid).lerp(cYoung, (frac - 0.35) / 0.65)
    // в рукавах ярче и голубее, между рукавами тусклее
    const boost = interArm ? 0.5 + rnd() * 0.3 : 0.75 + rnd() * 0.65
    if (!interArm && rnd() < 0.06) c.lerp(cYoung, 0.8) // яркие OB-ассоциации
    col[i * 3] = c.r * boost
    col[i * 3 + 1] = c.g * boost
    col[i * 3 + 2] = c.b * boost
    size[i] = (0.5 + Math.pow(rnd(), 3.4) * 2.6) * (interArm ? 0.8 : 1.15)
  }

  const g = new THREE.BufferGeometry()
  g.setAttribute('position', new THREE.BufferAttribute(pos, 3))
  g.setAttribute('color', new THREE.BufferAttribute(col, 3))
  g.setAttribute('aSize', new THREE.BufferAttribute(size, 1))
  return g
}

/** Балдж с барной перемычкой: сплющенный вытянутый сфероид. */
function buildBulge(): THREE.BufferGeometry {
  const rnd = mulberry32(27182)
  const N = 9000
  const pos = new Float32Array(N * 3)
  const col = new Float32Array(N * 3)
  const size = new Float32Array(N)

  const cCore = new THREE.Color('#ffd9a0')
  const cEdge = new THREE.Color('#ffa860')
  // Бар развёрнут примерно на 25° к линии Солнце — центр
  const barAngle = (25 * Math.PI) / 180
  const rBulge = MILKY_WAY.bulgeRadiusLy / KLY

  for (let i = 0; i < N; i++) {
    // плотность ~ r^-1.8, поэтому берём степенное распределение
    const u = rnd()
    const r = rBulge * Math.pow(u, 1.7)
    const z = rnd() * 2 - 1
    const t = rnd() * Math.PI * 2
    const s = Math.sqrt(1 - z * z)

    let x = r * s * Math.cos(t)
    let y = r * s * Math.sin(t)
    const zz = r * z * 0.6 // сплющенность

    // Вытягиваем в бар
    const xb = x * 1.9
    const yb = y * 0.75
    x = xb * Math.cos(barAngle) - yb * Math.sin(barAngle)
    y = xb * Math.sin(barAngle) + yb * Math.cos(barAngle)

    pos[i * 3] = x
    pos[i * 3 + 1] = zz
    pos[i * 3 + 2] = y

    const c = cCore.clone().lerp(cEdge, Math.min(1, r / rBulge))
    const b = 0.6 + rnd() * 0.7
    col[i * 3] = c.r * b
    col[i * 3 + 1] = c.g * b
    col[i * 3 + 2] = c.b * b
    size[i] = 0.5 + Math.pow(rnd(), 3) * 2.2
  }

  const g = new THREE.BufferGeometry()
  g.setAttribute('position', new THREE.BufferAttribute(pos, 3))
  g.setAttribute('color', new THREE.BufferAttribute(col, 3))
  g.setAttribute('aSize', new THREE.BufferAttribute(size, 1))
  return g
}

/** Гало: разряжённое сферическое население плюс шаровые скопления. */
function buildHalo(): THREE.BufferGeometry {
  const rnd = mulberry32(16180)
  const N = 4200
  const pos = new Float32Array(N * 3)
  const col = new Float32Array(N * 3)
  const size = new Float32Array(N)
  const c = new THREE.Color('#ffcfa8')

  for (let i = 0; i < N; i++) {
    // плотность гало ~ r^-3.5, диапазон 5–120 тыс. св. лет
    const rMin = 5
    const rMax = 120
    const u = rnd()
    const r = Math.pow(Math.pow(rMin, -1.5) * (1 - u) + Math.pow(rMax, -1.5) * u, -1 / 1.5)
    const z = rnd() * 2 - 1
    const t = rnd() * Math.PI * 2
    const s = Math.sqrt(1 - z * z)
    pos[i * 3] = r * s * Math.cos(t)
    pos[i * 3 + 1] = r * z
    pos[i * 3 + 2] = r * s * Math.sin(t)
    const b = 0.25 + rnd() * 0.4
    col[i * 3] = c.r * b
    col[i * 3 + 1] = c.g * b
    col[i * 3 + 2] = c.b * b
    size[i] = 0.4 + Math.pow(rnd(), 3) * 1.4
  }

  const g = new THREE.BufferGeometry()
  g.setAttribute('position', new THREE.BufferAttribute(pos, 3))
  g.setAttribute('color', new THREE.BufferAttribute(col, 3))
  g.setAttribute('aSize', new THREE.BufferAttribute(size, 1))
  return g
}

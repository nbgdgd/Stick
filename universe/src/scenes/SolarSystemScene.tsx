/**
 * Уровень 3 — Солнечная система.
 *
 * Положения планет считаются каждый кадр из приближённых кеплеровых элементов
 * JPL. Это те же элементы, по которым строят настоящие эфемериды, только без
 * вековых возмущений от других планет — точность порядка угловых минут.
 *
 * Два масштаба расстояний:
 *  — «реальный»: расстояние в единицах сцены равно расстоянию в а.е.
 *    Система при этом выглядит пустой, и это честная картина.
 *  — «читаемый»: радиус сжат как r^0.42, поэтому внешние планеты
 *    подтягиваются к центру и все восемь орбит влезают в один кадр.
 * Размеры тел в обоих режимах преувеличены — иначе планеты были бы
 * меньше пикселя (Земля в масштабе а.е. — это 4·10⁻⁵ единицы).
 */

import { useMemo, useRef } from 'react'
import { useFrame, useLoader } from '@react-three/fiber'
import { TextureLoader } from 'three'
import * as THREE from 'three'
import { useStore } from '../store'
import { PLANETS, DWARF_PLANETS, SUN, type Planet } from '../data/planets'
import { moonsOf, type Moon } from '../data/moons'
import {
  AU_M,
  DEG,
  centuriesSinceJ2000,
  jdFromUnixMs,
  jplPosition,
  daysSinceJ2000,
  solveKepler,
} from '../lib/astro'
import { sunVertex, sunFragment, coronaFragment } from '../shaders/earth'
import { sunGeometry } from '../lib/sun'
import { Label } from '../components/Label'
import { registerFocus } from '../lib/focus'
import { Starfield } from '../components/Starfield'
import { AsteroidBelt } from '../components/AsteroidBelt'

const EARTH_ELEMENTS = PLANETS.find((p) => p.id === 'earth')!.elements!

/** Сжатие радиуса для читаемого режима. */
const COMPRESS_EXP = 0.42
const COMPRESS_MUL = 3.4

export function compressRadius(au: number, real: boolean): number {
  return real ? au : Math.pow(au, COMPRESS_EXP) * COMPRESS_MUL
}

/** Экранный размер тела. Настоящие радиусы отличаются в 200 раз — сжимаем логарифмом. */
function bodyRadius(p: Planet, real: boolean): number {
  const km = p.radiusKm
  if (p.kind === 'star') return real ? 0.02 : 0.3
  // логарифмическое сжатие: Юпитер остаётся заметно крупнее Меркурия,
  // но не в 28 раз, иначе мелкие планеты пропадут
  const base = Math.pow(km / 6371, 0.52)
  return (real ? 0.006 : 0.075) * base
}

/** Расстояние от Земли до тела, а.е. — именно его подписывает панель фактов. */
function distanceFromEarthAu(p: Planet, unixMs: number): number {
  const T = centuriesSinceJ2000(jdFromUnixMs(unixMs))
  if (p.id === 'earth') return 0
  const target = p.elements ? jplPosition(p.elements, T) : ([0, 0, 0] as [number, number, number])
  const earth = jplPosition(EARTH_ELEMENTS, T)
  return Math.hypot(target[0] - earth[0], target[1] - earth[1], target[2] - earth[2])
}

/** Положение планеты в а.е. на момент времени, эклиптические координаты. */
function planetAu(p: Planet, unixMs: number): [number, number, number] {
  if (!p.elements) return [0, 0, 0]
  const T = centuriesSinceJ2000(jdFromUnixMs(unixMs))
  return jplPosition(p.elements, T)
}

/** Положение спутника относительно планеты, в а.е. */
function moonAu(m: Moon, unixMs: number): THREE.Vector3 {
  const d = daysSinceJ2000(jdFromUnixMs(unixMs))
  const n = (2 * Math.PI) / m.periodDays
  const M = n * d
  const E = solveKepler(M, m.e)
  const a = m.aKm / (AU_M / 1000)
  const xv = a * (Math.cos(E) - m.e)
  const yv = a * Math.sqrt(1 - m.e * m.e) * Math.sin(E)
  const i = m.i * DEG
  return new THREE.Vector3(xv, yv * Math.sin(i), -yv * Math.cos(i))
}

/** Эклиптические (x, y, z) -> координаты сцены (Y вверх) со сжатием радиуса. */
function toScene(au: [number, number, number], real: boolean): THREE.Vector3 {
  const r = Math.hypot(au[0], au[1])
  if (r < 1e-9) return new THREE.Vector3(0, au[2], 0)
  const rs = compressRadius(r, real)
  const k = rs / r
  return new THREE.Vector3(au[0] * k, au[2] * k, -au[1] * k)
}

export function SolarSystemScene() {
  const simTime = useStore((s) => s.simTime)
  const realScale = useStore((s) => s.realScale)
  const showOrbits = useStore((s) => s.showOrbits)
  const showLabels = useStore((s) => s.showLabels)
  const select = useStore((s) => s.select)
  const setFocus = useStore((s) => s.setFocus)
  const cameraDist = useStore((s) => s.cameraDist)
  const focusId = useStore((s) => s.focusId)

  const bodies = useMemo(() => [...PLANETS, ...DWARF_PLANETS], [])

  const textures = useLoader(
    TextureLoader,
    useMemo(() => {
      const list = [SUN.texture!, ...bodies.map((b) => b.texture).filter(Boolean) as string[]]
      list.push('/tex/2k_saturn_ring_alpha.png')
      list.push('/tex/2k_moon.jpg')
      return list
    }, [bodies]),
  )

  const texMap = useMemo(() => {
    const list = [SUN.texture!, ...(bodies.map((b) => b.texture).filter(Boolean) as string[]),
      '/tex/2k_saturn_ring_alpha.png', '/tex/2k_moon.jpg']
    const m = new Map<string, THREE.Texture>()
    list.forEach((path, i) => {
      const t = textures[i]
      t.colorSpace = THREE.SRGBColorSpace
      t.anisotropy = 4
      m.set(path, t)
    })
    return m
  }, [textures, bodies])

  return (
    <group>
      <Starfield radius={realScale ? 4000 : 700} starCount={3200} starSize={0.0011} brightness={0.85} />
      <ambientLight intensity={0.055} />
      {/* Точечный свет в центре: планеты освещаются от Солнца, как в природе.
          decay=0, потому что физически корректное затухание 1/r² на 30 а.е.
          сделало бы Нептун полностью чёрным. */}
      <pointLight position={[0, 0, 0]} intensity={realScale ? 6 : 3.2} distance={0} decay={0} color="#fff4e0" />

      <SunBody texture={texMap.get(SUN.texture!)!} real={realScale} simTime={simTime} />

      {bodies.map((p) => (
        <PlanetBody
          key={p.id}
          planet={p}
          simTime={simTime}
          real={realScale}
          showOrbit={showOrbits}
          // Html-подписи рисуются поверх канваса и не перекрываются геометрией,
          // поэтому при наезде на планету надписи далёких тел ложились ей на диск.
          // Пока есть выбранный объект, подписываем только его.
          showLabel={showLabels && (!focusId || focusId === p.id)}
          focused={focusId === p.id}
          focusedAny={!!focusId}
          cameraDist={cameraDist}
          texMap={texMap}
          onSelect={(o) => {
            setFocus(p.id)
            select(o)
          }}
        />
      ))}

      <AsteroidBelt real={realScale} compress={compressRadius} />
    </group>
  )
}

function SunBody({ texture, real, simTime }: { texture: THREE.Texture; real: boolean; simTime: number }) {
  const select = useStore((s) => s.select)
  const setFocus = useStore((s) => s.setFocus)
  const showLabels = useStore((s) => s.showLabels)
  const matRef = useRef<THREE.ShaderMaterial>(null)
  const coronaRef = useRef<THREE.Mesh>(null)
  const r = bodyRadius(SUN, real)

  const sunMat = useMemo(
    () =>
      new THREE.ShaderMaterial({
        vertexShader: sunVertex,
        fragmentShader: sunFragment,
        uniforms: { surfaceMap: { value: texture }, time: { value: 0 } },
      }),
    [texture],
  )

  const coronaMat = useMemo(
    () =>
      new THREE.ShaderMaterial({
        vertexShader: /* glsl */ `
          varying vec2 vUv;
          void main() {
            vUv = uv;
            gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
          }
        `,
        fragmentShader: coronaFragment,
        uniforms: { time: { value: 0 }, coreColor: { value: new THREE.Color('#ffdd88') } },
        transparent: true,
        depthWrite: false,
        blending: THREE.AdditiveBlending,
      }),
    [],
  )

  useFrame((state) => {
    sunMat.uniforms.time.value = state.clock.elapsedTime
    coronaMat.uniforms.time.value = state.clock.elapsedTime
    // Корона всегда развёрнута к камере — это billboard
    if (coronaRef.current) coronaRef.current.quaternion.copy(state.camera.quaternion)
    registerFocus('sun', new THREE.Vector3(0, 0, 0), r)
  })

  function onSelect() {
    setFocus('sun')
    select({
      id: 'sun',
      name: SUN.name,
      kind: 'Звезда',
      blurb: SUN.blurb,
      facts: SUN.facts,
      source: SUN.source,
      texture: SUN.texture,
      artistic: true,
      // Расстояние Земля — Солнце меняется от 0,983 до 1,017 а.е.,
      // поэтому берём фактическое на выбранную дату
      distanceM: sunGeometry(simTime).distanceAu * AU_M,
    })
  }

  return (
    <group>
      <mesh material={sunMat} ref={matRef as never} scale={r} onClick={onSelect}>
        <sphereGeometry args={[1, 64, 48]} />
      </mesh>
      <mesh ref={coronaRef} material={coronaMat} scale={r * 2.4}>
        <planeGeometry args={[2, 2]} />
      </mesh>
      {showLabels && <Label position={[0, r * 2.4, 0]} text="Солнце" onClick={onSelect} />}
    </group>
  )
}

interface PlanetBodyProps {
  planet: Planet
  simTime: number
  real: boolean
  showOrbit: boolean
  showLabel: boolean
  cameraDist: number
  /** камера наведена именно на эту планету */
  focused: boolean
  /** вообще есть выбранный объект */
  focusedAny: boolean
  texMap: Map<string, THREE.Texture>
  onSelect: (o: Parameters<ReturnType<typeof useStore.getState>['select']>[0]) => void
}

function PlanetBody({ planet, simTime, real, showOrbit, showLabel, cameraDist, focused, focusedAny, texMap, onSelect }: PlanetBodyProps) {
  const groupRef = useRef<THREE.Group>(null)
  const meshRef = useRef<THREE.Mesh>(null)
  const r = bodyRadius(planet, real)
  const moons = useMemo(() => moonsOf(planet.id), [planet.id])

  // Орбита: строим по элементам на текущую эпоху, 360 точек хватает,
  // чтобы даже у Меркурия с e=0.21 линия была гладкой
  const orbitGeom = useMemo(() => {
    if (!planet.elements) return null
    const T = centuriesSinceJ2000(jdFromUnixMs(simTime))
    const el = planet.elements
    const a = el.a + el.aDot * T
    const e = el.e + el.eDot * T
    const i = (el.i + el.iDot * T) * DEG
    const lp = (el.lp + el.lpDot * T) * DEG
    const om = (el.om + el.omDot * T) * DEG
    const w = lp - om
    const pts: THREE.Vector3[] = []
    for (let k = 0; k <= 360; k++) {
      const E = (k / 360) * Math.PI * 2
      const xv = a * (Math.cos(E) - e)
      const yv = a * Math.sqrt(1 - e * e) * Math.sin(E)
      // те же повороты, что в orbitPosition
      const x1 = xv * Math.cos(w) - yv * Math.sin(w)
      const y1 = xv * Math.sin(w) + yv * Math.cos(w)
      const y2 = y1 * Math.cos(i)
      const z2 = y1 * Math.sin(i)
      const x = x1 * Math.cos(om) - y2 * Math.sin(om)
      const y = x1 * Math.sin(om) + y2 * Math.cos(om)
      pts.push(toScene([x, y, z2], real))
    }
    return new THREE.BufferGeometry().setFromPoints(pts)
    // эпоху берём округлённо — перестраивать линию каждый кадр незачем
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [planet.id, real, Math.floor(simTime / 3.156e10)])

  useFrame(() => {
    const au = planetAu(planet, simTime)
    const p = toScene(au, real)
    if (groupRef.current) groupRef.current.position.copy(p)
    if (meshRef.current) {
      // Вращение вокруг оси по реальному периоду
      const hours = simTime / 3600000
      meshRef.current.rotation.y = (hours / planet.rotationHours) * Math.PI * 2
    }
    registerFocus(planet.id, p, r)
  })

  const au = planetAu(planet, simTime)
  const distFromSunAu = Math.hypot(au[0], au[1], au[2])
  const distFromEarthAu = distanceFromEarthAu(planet, simTime)

  // Подписи прячем, когда камера далеко: иначе на общем виде каша из текста
  const labelVisible = showLabel && (real ? cameraDist > 0.2 : true)

  const tex = planet.texture ? texMap.get(planet.texture) : undefined
  const cloudTex = planet.cloudTexture ? texMap.get(planet.cloudTexture) : undefined

  function handleSelect() {
    onSelect({
      id: planet.id,
      name: planet.name,
      kind: planet.kind === 'dwarf' ? 'Карликовая планета' : 'Планета',
      blurb: planet.blurb,
      texture: planet.texture,
      artistic: planet.textureIsArtistic,
      distanceM: distFromEarthAu * AU_M,
      facts: [
        ...planet.facts,
        { label: '— на выбранный момент —', value: '' },
        { label: 'Расстояние от Солнца', value: `${distFromSunAu.toFixed(3)} а.е.` },
        {
          label: 'Расстояние от Земли',
          value:
            planet.id === 'earth'
              ? '—'
              : `${distFromEarthAu.toFixed(3)} а.е. (${(distFromEarthAu * 499.005).toFixed(1)} с светового пути)`,
        },
        {
          label: 'Спутников в модели',
          value: moons.length > 0 ? `${moons.length} (${moons.map((m) => m.name).join(', ')})` : 'нет',
        },
      ],
      source: planet.source,
    })
  }

  return (
    <>
      {showOrbit && orbitGeom && (
        // @ts-expect-error примитив line из three, типы R3F его не покрывают
        <line geometry={orbitGeom}>
          <lineBasicMaterial
            color={planet.color}
            transparent
            // При наезде на объект орбиты уводим почти в ноль: вблизи они
            // проходят через весь кадр и мешают смотреть на планету
            opacity={(focused || !focusedAny ? 1 : 0.28) * (planet.kind === 'dwarf' ? 0.16 : 0.3)}
          />
        </line>
      )}

      <group ref={groupRef}>
        <mesh ref={meshRef} scale={r} onClick={handleSelect} rotation={[planet.axialTilt * DEG, 0, 0]}>
          <sphereGeometry args={[1, 48, 32]} />
          {tex ? (
            <meshStandardMaterial map={tex} roughness={0.85} metalness={0.02} />
          ) : (
            <meshStandardMaterial color={planet.color} roughness={0.85} metalness={0.02} />
          )}
        </mesh>

        {/* Венера: полупрозрачный слой облаков поверх поверхности */}
        {cloudTex && planet.id === 'venus' && (
          <mesh scale={r * 1.02}>
            <sphereGeometry args={[1, 48, 32]} />
            <meshStandardMaterial map={cloudTex} transparent opacity={0.92} roughness={1} />
          </mesh>
        )}

        {/* Кольца Сатурна и Урана */}
        {planet.ringInnerKm && planet.ringOuterKm && (
          <Rings
            planet={planet}
            bodyRadiusUnits={r}
            texture={planet.ringTexture ? texMap.get(planet.ringTexture) : undefined}
          />
        )}

        {moons.map((m) => (
          <MoonBody
            key={m.id}
            moon={m}
            simTime={simTime}
            planet={planet}
            planetRadius={r}
            planetRadiusKm={planet.radiusKm}
            texture={m.texture ? texMap.get(m.texture) : undefined}
            showLabel={showLabel && focused}
          />
        ))}

        {labelVisible && (
          <Label
            position={[0, r * 2.2 + (real ? 0.004 : 0.05), 0]}
            text={planet.name}
            small={planet.kind === 'dwarf'}
            onClick={handleSelect}
          />
        )}
      </group>
    </>
  )
}

/** Кольца: плоское кольцо с радиальной текстурой, наклонённое вместе с осью планеты. */
function Rings({
  planet,
  bodyRadiusUnits,
  texture,
}: {
  planet: Planet
  bodyRadiusUnits: number
  texture?: THREE.Texture
}) {
  const inner = (planet.ringInnerKm! / planet.radiusKm) * bodyRadiusUnits
  const outer = (planet.ringOuterKm! / planet.radiusKm) * bodyRadiusUnits

  // Стандартный ringGeometry раскладывает uv по квадрату, а текстура кольца —
  // это полоска «от внутреннего края к внешнему». Перестраиваем uv на радиальные.
  const geom = useMemo(() => {
    const g = new THREE.RingGeometry(inner, outer, 128, 1)
    const pos = g.attributes.position
    const uv = g.attributes.uv
    const v = new THREE.Vector3()
    for (let i = 0; i < pos.count; i++) {
      v.fromBufferAttribute(pos, i)
      const t = (v.length() - inner) / (outer - inner)
      uv.setXY(i, t, 0.5)
    }
    return g
  }, [inner, outer])

  return (
    <mesh geometry={geom} rotation={[Math.PI / 2 - planet.axialTilt * DEG, 0, 0]}>
      {texture ? (
        <meshBasicMaterial
          map={texture}
          side={THREE.DoubleSide}
          transparent
          opacity={0.9}
          depthWrite={false}
        />
      ) : (
        <meshBasicMaterial
          color="#8f9aa8"
          side={THREE.DoubleSide}
          transparent
          opacity={0.22}
          depthWrite={false}
        />
      )}
    </mesh>
  )
}

function MoonBody({
  moon,
  simTime,
  planet,
  planetRadius,
  planetRadiusKm,
  texture,
  showLabel,
}: {
  moon: Moon
  planet: Planet
  simTime: number
  planetRadius: number
  /** физический радиус планеты, км — нужен для орбит в радиусах планеты */
  planetRadiusKm: number
  texture?: THREE.Texture
  showLabel: boolean
}) {
  const ref = useRef<THREE.Group>(null)
  const select = useStore((s) => s.select)

  // Орбиты спутников считаем не в а.е., а в радиусах планеты: это та
  // величина, которая реально осмысленна (Фобос — 2,8 радиуса Марса,
  // Титан — 21 радиус Сатурна). Разброс в 20 раз сжимаем степенью 0,42,
  // иначе близкие спутники сидят в планете, а далёкие уходят из кадра.
  const ratio = moon.aKm / planetRadiusKm
  const orbitR = planetRadius * (1.7 + Math.pow(ratio, 0.42) * 1.1)
  const rMoon = Math.max(planetRadius * 0.045, planetRadius * Math.pow(moon.radiusKm / 2000, 0.5) * 0.15)

  useFrame(() => {
    // Направление берём из кеплеровой орбиты, радиус — сжатый
    const dir = moonAu(moon, simTime).normalize()
    if (ref.current) ref.current.position.copy(dir.multiplyScalar(orbitR))
  })

  return (
    <group ref={ref}>
      <mesh
        scale={rMoon}
        onClick={(e) => {
          e.stopPropagation()
          select({
            id: moon.id,
            name: moon.name,
            kind: `Спутник ${moon.planetId === 'earth' ? 'Земли' : ''}`.trim() || 'Спутник',
            blurb: moon.blurb,
            texture: moon.texture,
            artistic: !moon.texture,
            // Панель подписывает это как расстояние от Земли, поэтому берём
            // расстояние до планеты-хозяина: смещение спутника от неё
            // пренебрежимо мало на фоне межпланетных расстояний
            distanceM: distanceFromEarthAu(planet, simTime) * AU_M,
            facts: [
              ...moon.facts,
              { label: 'Приливный захват', value: moon.tidallyLocked ? 'да' : 'нет' },
              {
                label: 'Расстояние от Земли',
                value: `${distanceFromEarthAu(planet, simTime).toFixed(3)} а.е. (до ${planet.name})`,
              },
            ],
            source: moon.source,
          })
        }}
      >
        <sphereGeometry args={[1, 24, 16]} />
        {texture ? (
          <meshStandardMaterial map={texture} roughness={0.95} />
        ) : (
          <meshStandardMaterial color={moon.color} roughness={0.95} />
        )}
      </mesh>
      {showLabel && <Label position={[0, rMoon * 3, 0]} text={moon.name} small />}
    </group>
  )
}

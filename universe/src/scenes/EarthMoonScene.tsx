/**
 * Уровень 2 — система Земля — Луна.
 *
 * Показывает три вещи, которые обычно объясняют словами:
 *  — приливный захват: Луна вращается ровно с периодом обращения, и мы
 *    всегда видим одну её сторону (в модели видно, что метка «видимая сторона»
 *    не уходит из поля зрения);
 *  — приливной эллипсоид: два горба воды вдоль линии Земля — Луна, откуда
 *    и берутся два прилива в сутки;
 *  — фазы: освещённая доля Луны считается из реального взаимного положения.
 *
 * Масштабы: радиусы тел настоящие относительно расстояния между ними
 * (в «реальном» режиме) — это единственный уровень, где такое влезает в кадр.
 */

import { useMemo, useRef } from 'react'
import { useFrame, useLoader } from '@react-three/fiber'
import { TextureLoader } from 'three'
import * as THREE from 'three'
import { useStore } from '../store'
import { sunGeometry } from '../lib/sun'
import { earthVertex, earthFragment, atmosphereVertex, atmosphereFragment } from '../shaders/earth'
import { PLANETS } from '../data/planets'
import { MOONS } from '../data/moons'
import { DEG, daysSinceJ2000, jdFromUnixMs, solveKepler } from '../lib/astro'
import { Label } from '../components/Label'
import { registerFocus } from '../lib/focus'
import { Starfield } from '../components/Starfield'

const EARTH = PLANETS.find((p) => p.id === 'earth')!
const MOON = MOONS.find((m) => m.id === 'moon')!

/** Радиус Земли = 1 единица сцены. */
const MOON_R = MOON.radiusKm / EARTH.radiusKm // 0.2727
const MOON_A_REAL = MOON.aKm / EARTH.radiusKm // 60.3
// Сжатый масштаб. Настоящее расстояние — 60,3 радиуса Земли; здесь 18,
// то есть преувеличение в 3,4 раза. Меньше нельзя: Луна залезет на Землю,
// больше — орбита перестанет влезать в кадр вместе с обоими телами.
const MOON_A_COMPACT = 18

/**
 * Положение Луны. Используем кеплерову орбиту с реальными a, e, i и
 * средней долготой, растущей с сидерическим периодом. Настоящая орбита
 * Луны возмущена Солнцем (эвекция, вариация и ещё сотни членов), но для
 * фаз и приливов кеплерова орбиты достаточно.
 */
function moonPosition(unixMs: number, aUnits: number): THREE.Vector3 {
  const d = daysSinceJ2000(jdFromUnixMs(unixMs))
  // Средняя долгота Луны и долгота перигея на эпоху J2000
  const L = (218.316 + 13.176396 * d) * DEG
  const perigee = (83.353 + 0.11140353 * d) * DEG
  const node = (125.045 - 0.0529539 * d) * DEG
  const M = L - perigee
  const E = solveKepler(M, MOON.e)
  const xv = aUnits * (Math.cos(E) - MOON.e)
  const yv = aUnits * Math.sqrt(1 - MOON.e * MOON.e) * Math.sin(E)
  const w = perigee - node
  const i = MOON.i * DEG
  // поворот: аргумент перигея -> наклонение -> долгота узла
  const x1 = xv * Math.cos(w) - yv * Math.sin(w)
  const y1 = xv * Math.sin(w) + yv * Math.cos(w)
  const y2 = y1 * Math.cos(i)
  const z2 = y1 * Math.sin(i)
  const x = x1 * Math.cos(node) - y2 * Math.sin(node)
  const y = x1 * Math.sin(node) + y2 * Math.cos(node)
  // в координаты сцены: Y вверх
  return new THREE.Vector3(x, z2, -y)
}

export function EarthMoonScene() {
  const [dayMap, nightMap, cloudMap, moonMap] = useLoader(TextureLoader, [
    '/tex/2k_earth_daymap.jpg',
    '/tex/2k_earth_nightmap.jpg',
    '/tex/2k_earth_clouds.jpg',
    '/tex/2k_moon.jpg',
  ])

  useMemo(() => {
    for (const t of [dayMap, nightMap, cloudMap, moonMap]) {
      t.colorSpace = THREE.SRGBColorSpace
      t.anisotropy = 4
    }
  }, [dayMap, nightMap, cloudMap, moonMap])

  const simTime = useStore((s) => s.simTime)
  const realScale = useStore((s) => s.realScale)
  const showOrbits = useStore((s) => s.showOrbits)
  const showLabels = useStore((s) => s.showLabels)
  const select = useStore((s) => s.select)
  const setFocus = useStore((s) => s.setFocus)

  const aUnits = realScale ? MOON_A_REAL : MOON_A_COMPACT
  // В сжатом режиме Луну приходится увеличить, иначе она пиксель
  const moonScale = realScale ? MOON_R : MOON_R * 2.6

  const earthRef = useRef<THREE.Mesh>(null)
  const moonGroupRef = useRef<THREE.Group>(null)
  const moonMeshRef = useRef<THREE.Mesh>(null)
  const tideRef = useRef<THREE.Mesh>(null)
  const sunLightRef = useRef<THREE.DirectionalLight>(null)

  const earthMat = useMemo(
    () =>
      new THREE.ShaderMaterial({
        vertexShader: earthVertex,
        fragmentShader: earthFragment,
        uniforms: {
          dayMap: { value: dayMap },
          nightMap: { value: nightMap },
          cloudMap: { value: cloudMap },
          sunDirection: { value: new THREE.Vector3(1, 0, 0) },
          cloudOpacity: { value: 0.85 },
          cloudOffset: { value: 0 },
        },
      }),
    [dayMap, nightMap, cloudMap],
  )

  const atmoMat = useMemo(
    () =>
      new THREE.ShaderMaterial({
        vertexShader: atmosphereVertex,
        fragmentShader: atmosphereFragment,
        uniforms: {
          sunDirection: { value: new THREE.Vector3(1, 0, 0) },
          glowColor: { value: new THREE.Color(0.35, 0.6, 1.0) },
          intensity: { value: 1.4 },
          power: { value: 2.6 },
        },
        transparent: true,
        blending: THREE.AdditiveBlending,
        side: THREE.BackSide,
        depthWrite: false,
      }),
    [],
  )

  // Орбита Луны как замкнутая линия
  const orbitGeom = useMemo(() => {
    const pts: THREE.Vector3[] = []
    const t0 = Date.UTC(2000, 0, 1)
    for (let k = 0; k <= 256; k++) {
      pts.push(moonPosition(t0 + (k / 256) * MOON.periodDays * 86400000, aUnits))
    }
    return new THREE.BufferGeometry().setFromPoints(pts)
  }, [aUnits])

  useFrame(() => {
    const sun = sunGeometry(simTime)
    const dir = new THREE.Vector3(sun.direction[0], sun.direction[2], -sun.direction[1])
    earthMat.uniforms.sunDirection.value.copy(dir)
    atmoMat.uniforms.sunDirection.value.copy(dir)
    earthMat.uniforms.cloudOffset.value = (simTime / 1000 / 86400) * 0.08
    if (sunLightRef.current) sunLightRef.current.position.copy(dir).multiplyScalar(500)

    if (earthRef.current) {
      earthRef.current.rotation.y = -sun.gmst * DEG - Math.PI / 2
    }

    const moonPos = moonPosition(simTime, aUnits)
    if (moonGroupRef.current) moonGroupRef.current.position.copy(moonPos)

    // Приливный захват: Луна всегда обращена к Земле одной стороной.
    // Разворачиваем её лицом к началу координат.
    if (moonMeshRef.current) {
      const angle = Math.atan2(moonPos.z, moonPos.x)
      // текстура Луны центрирована на видимой стороне при повороте на -angle
      moonMeshRef.current.rotation.y = -angle + Math.PI / 2
    }

    // Приливной эллипсоид: вытянут вдоль линии Земля — Луна.
    // Оба горба, и обращённый к Луне, и противоположный — отсюда два прилива в сутки.
    if (tideRef.current) {
      tideRef.current.lookAt(moonPos)
      tideRef.current.rotateX(Math.PI / 2)
    }

    registerFocus('earth', new THREE.Vector3(0, 0, 0), 1)
    registerFocus('moon', moonPos, moonScale)
  })

  const sun = sunGeometry(simTime)
  const moonPos = moonPosition(simTime, aUnits)

  // Фаза Луны: угол Солнце — Земля — Луна.
  // 0° = новолуние (Луна между нами и Солнцем), 180° = полнолуние.
  const sunDir = new THREE.Vector3(sun.direction[0], sun.direction[2], -sun.direction[1])
  const elong = (Math.acos(moonPos.clone().normalize().dot(sunDir)) * 180) / Math.PI
  const illumFrac = (1 - Math.cos((elong * Math.PI) / 180)) / 2
  const phaseName =
    elong < 15 ? 'Новолуние'
    : elong < 75 ? 'Молодая Луна (растущий серп)'
    : elong < 105 ? 'Первая четверть'
    : elong < 165 ? 'Растущая Луна'
    : elong < 195 ? 'Полнолуние'
    : elong < 255 ? 'Убывающая Луна'
    : elong < 285 ? 'Последняя четверть'
    : 'Старая Луна (убывающий серп)'

  function selectMoon() {
    setFocus('moon')
    select({
      id: 'moon',
      name: MOON.name,
      kind: 'Спутник Земли',
      blurb: MOON.blurb,
      texture: MOON.texture,
      distanceM: MOON.aKm * 1000,
      facts: [
        ...MOON.facts,
        { label: '— на выбранный момент —', value: '' },
        { label: 'Фаза', value: phaseName },
        { label: 'Освещённая доля', value: `${(illumFrac * 100).toFixed(1)} %` },
        { label: 'Элонгация от Солнца', value: `${elong.toFixed(1)}°` },
      ],
      source: MOON.source,
    })
  }

  function selectEarth() {
    setFocus('earth')
    select({
      id: 'earth',
      name: EARTH.name,
      kind: 'Планета',
      blurb: EARTH.blurb,
      texture: EARTH.texture,
      distanceM: 0,
      facts: [
        ...EARTH.facts,
        { label: '— приливы —', value: '' },
        { label: 'Приливов в сутки', value: '2 — по числу горбов эллипсоида' },
        { label: 'Вклад Луны в прилив', value: '≈ 68 %' },
        { label: 'Вклад Солнца', value: '≈ 32 %' },
        { label: 'Замедление вращения Земли', value: '+1,78 мс за век' },
      ],
      source: EARTH.source,
    })
  }

  return (
    <group>
      <Starfield radius={realScale ? 2400 : 1100} starCount={3600} starSize={0.0013} brightness={0.9} />
      <directionalLight ref={sunLightRef} intensity={2.4} color="#fff6e8" />
      <ambientLight intensity={0.02} />

      {/* Земля */}
      <mesh ref={earthRef} material={earthMat} onClick={selectEarth}>
        <sphereGeometry args={[1, 72, 48]} />
      </mesh>
      <mesh material={atmoMat} scale={1.03}>
        <sphereGeometry args={[1, 48, 32]} />
      </mesh>

      {/* Приливной эллипсоид: сильно преувеличен по амплитуде.
          В реальности горб в открытом океане около 50 см при радиусе 6371 км —
          это 8·10⁻⁸ радиуса, физически невозможно показать в масштабе. */}
      <mesh ref={tideRef} scale={[1.06, 1.06, 1.22]}>
        <sphereGeometry args={[1, 48, 32]} />
        <meshBasicMaterial color="#4fa8ff" transparent opacity={0.13} depthWrite={false} />
      </mesh>

      {/* Орбита Луны */}
      {showOrbits && (
        // @ts-expect-error примитив line из three, типы R3F его не покрывают
        <line geometry={orbitGeom}>
          <lineBasicMaterial color="#8899bb" transparent opacity={0.35} />
        </line>
      )}

      {/* Луна */}
      <group ref={moonGroupRef}>
        <mesh ref={moonMeshRef} scale={moonScale} onClick={selectMoon}>
          <sphereGeometry args={[1, 64, 40]} />
          <meshStandardMaterial map={moonMap} roughness={0.95} metalness={0} />
        </mesh>
        {showLabels && (
          <Label
            position={[0, moonScale * 1.9, 0]}
            text={MOON.name}
            sub={phaseName}
            priority={120}
            onClick={selectMoon}
          />
        )}
      </group>

      {showLabels && <Label position={[0, 1.7, 0]} text={EARTH.name} priority={125} onClick={selectEarth} />}

      {/* Линия Земля — Луна: наглядно, что приливные горбы вытянуты по ней */}
      {showOrbits && (
        <line>
          <bufferGeometry>
            <bufferAttribute
              attach="attributes-position"
              args={[new Float32Array([0, 0, 0, moonPos.x, moonPos.y, moonPos.z]), 3]}
            />
          </bufferGeometry>
          <lineDashedMaterial color="#ffcc66" transparent opacity={0.25} dashSize={1} gapSize={1} />
        </line>
      )}
    </group>
  )
}

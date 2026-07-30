/**
 * Уровень 1 — Земля.
 *
 * Освещение построено по фактическому направлению на Солнце для выбранной
 * даты: терминатор, времена года и подсолнечная точка меняются как в природе.
 * Вращение Земли привязано к среднему звёздному времени по Гринвичу (GMST),
 * поэтому под полуночью всегда оказывается правильный меридиан.
 */

import { useMemo, useRef } from 'react'
import { useFrame, useLoader } from '@react-three/fiber'
import { TextureLoader } from 'three'
import * as THREE from 'three'
import { useStore } from '../store'
import { sunGeometry, issPosition, seasonName, nextSolarEvent } from '../lib/sun'
import { earthVertex, earthFragment, atmosphereVertex, atmosphereFragment } from '../shaders/earth'
import { PLANETS } from '../data/planets'
import { Label } from '../components/Label'
import { Starfield } from '../components/Starfield'

const EARTH = PLANETS.find((p) => p.id === 'earth')!

export function EarthScene() {
  const [dayMap, nightMap, cloudMap] = useLoader(TextureLoader, [
    '/tex/2k_earth_daymap.jpg',
    '/tex/2k_earth_nightmap.jpg',
    '/tex/2k_earth_clouds.jpg',
  ])

  useMemo(() => {
    for (const t of [dayMap, nightMap, cloudMap]) {
      t.colorSpace = THREE.SRGBColorSpace
      t.anisotropy = 4
    }
  }, [dayMap, nightMap, cloudMap])

  const globeRef = useRef<THREE.Mesh>(null)
  const tiltRef = useRef<THREE.Group>(null)
  const issRef = useRef<THREE.Mesh>(null)
  const issTrailRef = useRef<THREE.Line>(null)

  const simTime = useStore((s) => s.simTime)
  const showLabels = useStore((s) => s.showLabels)
  const select = useStore((s) => s.select)

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
          intensity: { value: 1.5 },
          power: { value: 2.6 },
        },
        transparent: true,
        blending: THREE.AdditiveBlending,
        side: THREE.BackSide,
        depthWrite: false,
      }),
    [],
  )

  // Трасса МКС: считаем один виток вперёд от текущего момента
  const issTrailGeom = useMemo(() => {
    const g = new THREE.BufferGeometry()
    g.setAttribute('position', new THREE.BufferAttribute(new Float32Array(128 * 3), 3))
    return g
  }, [])

  const sunLightRef = useRef<THREE.DirectionalLight>(null)

  useFrame(() => {
    const sun = sunGeometry(simTime)
    const dir = new THREE.Vector3(sun.direction[0], sun.direction[2], -sun.direction[1])

    earthMat.uniforms.sunDirection.value.copy(dir)
    atmoMat.uniforms.sunDirection.value.copy(dir)
    earthMat.uniforms.cloudOffset.value = (simTime / 1000 / 86400) * 0.08

    if (sunLightRef.current) {
      sunLightRef.current.position.copy(dir).multiplyScalar(50)
    }

    // Наклон оси: ось Земли лежит в плоскости, повёрнутой на наклон эклиптики.
    // Мы работаем в экваториальной системе, поэтому наклон уже «встроен»
    // в положение Солнца — сама Земля стоит вертикально, а Солнце ходит по эклиптике.
    if (globeRef.current) {
      // GMST задаёт, какой меридиан обращён к точке весеннего равноденствия
      globeRef.current.rotation.y = -sun.gmst * (Math.PI / 180) - Math.PI / 2
    }

    // МКС
    if (issRef.current) {
      const p = issPosition(simTime)
      issRef.current.position.set(p[0], p[2], -p[1])
    }
    const pos = issTrailGeom.getAttribute('position') as THREE.BufferAttribute
    for (let i = 0; i < 128; i++) {
      // один полный виток назад по времени
      const t = simTime - (i / 127) * 92.9 * 60 * 1000
      const p = issPosition(t)
      pos.setXYZ(i, p[0], p[2], -p[1])
    }
    pos.needsUpdate = true
    issTrailGeom.computeBoundingSphere()
  })

  const sun = sunGeometry(simTime)
  const event = nextSolarEvent(sun.eclipticLongitude)

  function selectEarth() {
    select({
      id: 'earth',
      name: EARTH.name,
      kind: 'Планета',
      blurb: EARTH.blurb,
      texture: EARTH.texture,
      distanceM: 0,
      facts: [
        ...EARTH.facts,
        { label: '— на выбранный момент —', value: '' },
        { label: 'Подсолнечная точка', value: `${sun.dec.toFixed(2)}° ш., ${sun.subsolarLon.toFixed(2)}° д.` },
        { label: 'Склонение Солнца', value: `${sun.dec.toFixed(2)}°` },
        { label: 'Сезон', value: seasonName(sun.eclipticLongitude) },
        { label: 'Ближайшее событие', value: `${event.name} через ${(event.degreesAway * 1.0146).toFixed(0)} сут.` },
        { label: 'Расстояние до Солнца', value: `${(sun.distanceAu * 149597870.7).toLocaleString('ru-RU', { maximumFractionDigits: 0 })} км` },
      ],
      source: EARTH.source,
    })
  }

  function selectIss() {
    select({
      id: 'iss',
      name: 'Международная космическая станция',
      kind: 'Космический аппарат',
      blurb:
        'Обитаема непрерывно с 2 ноября 2000 года. Делает 15,5 витков в сутки, ' +
        'экипаж видит 16 рассветов и закатов за земные сутки.',
      distanceM: 420000,
      facts: [
        { label: 'Высота орбиты', value: '≈ 420 км' },
        { label: 'Наклонение', value: '51,64°' },
        { label: 'Период обращения', value: '92,9 минуты' },
        { label: 'Скорость', value: '7,66 км/с (27 600 км/ч)' },
        { label: 'Витков в сутки', value: '15,5' },
        { label: 'Масса', value: '419 725 кг' },
        { label: 'Размер ферм', value: '109 × 73 м' },
        { label: 'Обитаема с', value: '2 ноября 2000 года' },
      ],
      source: 'NASA ISS facts. Орбита — круговое приближение с учётом прецессии узла',
      artistic: true,
    })
  }

  return (
    <group>
      <Starfield radius={55} starCount={3800} starSize={0.028} brightness={0.95} />
      {/* Солнечный свет: направление совпадает с шейдерным, чтобы кольца
          и МКС освещались согласованно с поверхностью планеты */}
      <directionalLight ref={sunLightRef} intensity={2.2} color="#fff6e8" />
      <ambientLight intensity={0.03} />

      <group ref={tiltRef}>
        <mesh ref={globeRef} material={earthMat} onClick={selectEarth}>
          <sphereGeometry args={[1, 96, 64]} />
        </mesh>

        {/* Атмосфера — отдельная сфера чуть большего радиуса.
            Реальная толщина заметной атмосферы ~100 км = 1,6 % радиуса,
            здесь 2,5 % ради видимости лимба на маленьком экране. */}
        <mesh material={atmoMat} scale={1.025}>
          <sphereGeometry args={[1, 64, 48]} />
        </mesh>

        {/* МКС */}
        <mesh ref={issRef} onClick={selectIss}>
          <boxGeometry args={[0.012, 0.004, 0.03]} />
          <meshStandardMaterial color="#d8d8dd" emissive="#334455" emissiveIntensity={0.4} metalness={0.7} roughness={0.35} />
        </mesh>
        {/* след — один полный виток */}
        {/* @ts-expect-error примитив line из three, типы R3F его не покрывают */}
        <line ref={issTrailRef} geometry={issTrailGeom}>
          <lineBasicMaterial color="#63b3ff" transparent opacity={0.35} />
        </line>

        {showLabels && (
          <>
            <Label position={[0, 1.35, 0]} text="Северный полюс" small />
            <Label position={[0, -1.35, 0]} text="Южный полюс" small />
          </>
        )}
      </group>
    </group>
  )
}

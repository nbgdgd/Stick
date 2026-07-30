/**
 * Уровень 4 — ближайшие звёзды, ≈ 20 световых лет.
 *
 * Каждая звезда стоит в фактическом месте: положение получено из прямого
 * восхождения, склонения и расстояния по параллаксу (Gaia DR3 / RECONS).
 * Цвет — из температуры по спектральному классу через формулу цвета
 * чёрного тела, размер — из радиуса звезды, но с логарифмическим сжатием:
 * Сириус B меньше Бетельгейзе в 90 000 раз, линейный масштаб бессмыслен.
 */

import { useMemo, useRef } from 'react'
import { useFrame } from '@react-three/fiber'
import * as THREE from 'three'
import { useStore } from '../store'
import { STARS, NOTABLE_FAR_STARS, type Star } from '../data/stars'
import { raDecToXyz, blackbodyColor, spectralTemp, LY_M } from '../lib/astro'
import { Label } from '../components/Label'
import { registerFocus } from '../lib/focus'
import { Starfield } from '../components/Starfield'

/** Координаты звезды в единицах сцены (1 единица = 1 световой год). */
function starPos(s: Star): THREE.Vector3 {
  const [x, y, z] = raDecToXyz(s.ra, s.dec, s.distLy)
  // экваториальные -> сцена: Z (полюс мира) вверх
  return new THREE.Vector3(x, z, -y)
}

/** Экранный радиус: сжатие по светимости, а не по геометрии. */
function starDrawRadius(s: Star): number {
  if (s.id === 'sun') return 0.55
  const lum = s.lumSun ?? 0.002
  // светимость меняется на 5 порядков — берём корень 6-й степени
  return 0.3 + Math.pow(lum, 1 / 6) * 0.75
}

export function NearbyStarsScene() {
  const showLabels = useStore((s) => s.showLabels)
  const cameraDist = useStore((s) => s.cameraDist)
  const select = useStore((s) => s.select)
  const setFocus = useStore((s) => s.setFocus)

  const all = useMemo(() => [...STARS, ...NOTABLE_FAR_STARS], [])

  // Ближние звёзды — отдельными мешами (их десятки, это дёшево и позволяет
  // кликать по каждой). Далёкие «примечательные» — тоже, их всего четыре.
  const items = useMemo(
    () =>
      all.map((s) => ({
        star: s,
        pos: starPos(s),
        color: new THREE.Color(...blackbodyColor(spectralTemp(s.spectral))),
        radius: starDrawRadius(s),
        far: s.distLy > 21,
      })),
    [all],
  )

  // Сфера-ориентир радиусом 20 св. лет: показывает границу выборки
  const shellGeom = useMemo(() => new THREE.SphereGeometry(20, 32, 24), [])

  function pick(s: Star) {
    setFocus(s.id)
    const facts: { label: string; value: string }[] = [
      { label: 'Расстояние', value: s.distLy === 0 ? '—' : `${s.distLy.toFixed(3)} св. лет` },
      { label: 'Спектральный класс', value: s.spectral },
      { label: 'Температура', value: `≈ ${Math.round(spectralTemp(s.spectral)).toLocaleString('ru-RU')} К` },
    ]
    if (s.massSun !== undefined) facts.push({ label: 'Масса', value: `${s.massSun} M☉` })
    if (s.radiusSun !== undefined) facts.push({ label: 'Радиус', value: `${s.radiusSun} R☉` })
    if (s.lumSun !== undefined) {
      facts.push({
        label: 'Светимость',
        value: s.lumSun < 0.01 ? `${(s.lumSun * 1000).toFixed(2)} × 10⁻³ L☉` : `${s.lumSun} L☉`,
      })
    }
    if (s.vmag !== undefined) facts.push({ label: 'Видимая величина', value: `${s.vmag}ᵐ` })
    if (s.planets !== undefined) {
      facts.push({ label: 'Подтверждённых планет', value: s.planets === 0 ? 'нет' : String(s.planets) })
    }
    facts.push({ label: 'Прямое восхождение', value: `${Math.floor(s.ra)}ч ${((s.ra % 1) * 60).toFixed(1)}м` })
    facts.push({ label: 'Склонение', value: `${s.dec.toFixed(2)}°` })
    if (s.distLy > 0) {
      facts.push({
        label: 'Свет идёт до нас',
        value: `${s.distLy.toFixed(1)} года — мы видим её такой, какой она была в ${new Date().getFullYear() - Math.round(s.distLy)} году`,
      })
    }

    select({
      id: s.id,
      name: s.name,
      kind: s.spectral.startsWith('D') ? 'Белый карлик' : s.spectral.match(/^[LTY]/) ? 'Коричневый карлик' : 'Звезда',
      blurb: s.note,
      facts,
      distanceM: s.distLy * LY_M,
      source: 'Расстояния — параллаксы Gaia DR3 / RECONS; параметры — SIMBAD. Внешний вид процедурный',
      artistic: true,
    })
  }

  return (
    <group>
      <Starfield radius={1500} starCount={4200} starSize={0.0009} brightness={0.6} />
      <ambientLight intensity={0.6} />

      {/* Опорная сфера 20 св. лет */}
      <mesh geometry={shellGeom}>
        <meshBasicMaterial color="#2a4a6a" wireframe transparent opacity={0.055} />
      </mesh>

      {/* Плоскость галактического экватора для ориентации отсутствует
          намеренно: в экваториальных координатах она наклонена на 62°
          и только запутывала бы. Вместо неё — сетка расстояний. */}
      <DistanceRings />

      {items.map((it) => (
        <StarBody
          key={it.star.id}
          item={it}
          showLabel={showLabels && (it.star.distLy < 12 || cameraDist < 25 || it.star.id === 'sun')}
          onPick={() => pick(it.star)}
        />
      ))}
    </group>
  )
}

function StarBody({
  item,
  showLabel,
  onPick,
}: {
  item: { star: Star; pos: THREE.Vector3; color: THREE.Color; radius: number; far: boolean }
  showLabel: boolean
  onPick: () => void
}) {
  const glowRef = useRef<THREE.Mesh>(null)

  useFrame((state) => {
    // Ореол — billboard, всегда лицом к камере
    if (glowRef.current) glowRef.current.quaternion.copy(state.camera.quaternion)
    registerFocus(item.star.id, item.pos, item.radius * 2)
  })

  const glowMat = useMemo(
    () =>
      new THREE.ShaderMaterial({
        transparent: true,
        depthWrite: false,
        blending: THREE.AdditiveBlending,
        uniforms: { uColor: { value: item.color } },
        vertexShader: /* glsl */ `
          varying vec2 vUv;
          void main() {
            vUv = uv;
            gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
          }
        `,
        fragmentShader: /* glsl */ `
          uniform vec3 uColor;
          varying vec2 vUv;
          void main() {
            float d = length(vUv - 0.5) * 2.0;
            if (d > 1.0) discard;
            // Ядро плюс широкий гало плюс четыре луча — так глаз читает
            // объект как источник света, а не как шарик
            float core = exp(-d * d * 22.0);
            float halo = pow(max(0.0, 1.0 - d), 3.0) * 0.32;
            vec2 p = (vUv - 0.5) * 2.0;
            float spike = (exp(-abs(p.x) * 26.0) + exp(-abs(p.y) * 26.0)) * exp(-d * 2.2) * 0.22;
            float a = core + halo + spike;
            gl_FragColor = vec4(uColor * (0.8 + core * 1.4), a);
          }
        `,
      }),
    [item.color],
  )

  return (
    <group position={item.pos}>
      <mesh scale={item.radius * 0.42} onClick={onPick}>
        <sphereGeometry args={[1, 16, 12]} />
        <meshBasicMaterial color={item.color} />
      </mesh>
      <mesh ref={glowRef} scale={item.radius * (item.far ? 4.5 : 3.2)} material={glowMat}>
        <planeGeometry args={[2, 2]} />
      </mesh>
      {showLabel && (
        <Label
          position={[0, item.radius * 1.6, 0]}
          text={item.star.name}
          sub={item.star.distLy > 0 ? `${item.star.distLy.toFixed(1)} св. лет` : 'мы здесь'}
          small={item.star.distLy > 12}
          onClick={onPick}
        />
      )}
    </group>
  )
}

/** Концентрические окружности через 5 световых лет — чтобы не терять чувство расстояния. */
function DistanceRings() {
  const geoms = useMemo(() => {
    return [5, 10, 15, 20].map((r) => {
      const pts: THREE.Vector3[] = []
      for (let k = 0; k <= 128; k++) {
        const a = (k / 128) * Math.PI * 2
        pts.push(new THREE.Vector3(Math.cos(a) * r, 0, Math.sin(a) * r))
      }
      return { r, geom: new THREE.BufferGeometry().setFromPoints(pts) }
    })
  }, [])

  return (
    <group>
      {geoms.map(({ r, geom }) => (
        // @ts-expect-error примитив line из three, типы R3F его не покрывают
        <line key={r} geometry={geom}>
          <lineBasicMaterial color="#3d6a99" transparent opacity={0.14} />
        </line>
      ))}
      {geoms.map(({ r }) => (
        <Label key={`l${r}`} position={[r, 0, 0]} text={`${r} св. лет`} small />
      ))}
    </group>
  )
}

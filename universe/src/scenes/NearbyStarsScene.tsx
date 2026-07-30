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

/**
 * Экранный радиус. Настоящие радиусы звёзд на этом масштабе неразрешимы
 * (Солнце — 10⁻⁷ светового года), поэтому размер кодирует светимость:
 * корень шестой степени сжимает разброс в пять порядков до множителя 10.
 * Абсолютная величина подобрана так, чтобы ореолы соседних звёзд
 * не сливались: типичное расстояние между звёздами здесь — 5–8 св. лет.
 */
function starDrawRadius(s: Star): number {
  if (s.id === 'sun') return 0.16
  const lum = s.lumSun ?? 0.002
  return 0.05 + Math.pow(lum, 1 / 6) * 0.17
}

export function NearbyStarsScene() {
  const showLabels = useStore((s) => s.showLabels)
  const cameraDist = useStore((s) => s.cameraDist)
  const select = useStore((s) => s.select)
  const setFocus = useStore((s) => s.setFocus)

  const all = useMemo(() => [...STARS, ...NOTABLE_FAR_STARS], [])

  // Главный компонент каждой системы и её кратность. Главным считаем самый
  // светимый известный компонент, а при отсутствии данных — первый в каталоге.
  const systemInfo = useMemo(() => {
    const groups = new Map<string, Star[]>()
    for (const st of all) {
      const g = groups.get(st.systemId)
      if (g) g.push(st)
      else groups.set(st.systemId, [st])
    }
    const primaryOf = new Map<string, string>()
    const countOf = new Map<string, number>()
    for (const [sysId, members] of groups) {
      const primary = members.reduce((best, m) =>
        (m.lumSun ?? -1) > (best.lumSun ?? -1) ? m : best,
      )
      primaryOf.set(sysId, primary.id)
      countOf.set(sysId, members.length)
    }
    return { primaryOf, countOf }
  }, [all])

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

      {/* Плоскость галактического экватора для ориентации отсутствует
          намеренно: в экваториальных координатах она наклонена на 62°
          и только запутывала бы. Вместо неё — сетка расстояний. */}
      <DistanceRings />

      {items.map((it) => (
        <StarBody
          key={it.star.id}
          item={it}
          // Подписи: только главные компоненты систем, и только те,
          // что несут содержание — иначе 70 названий в одном кадре
          showLabel={
            showLabels &&
            systemInfo.primaryOf.get(it.star.systemId) === it.star.id &&
            (it.star.id === 'sun' ||
              it.star.distLy < 6 ||
              (!!it.star.note && cameraDist < 60) ||
              cameraDist < 16)
          }
          members={systemInfo.countOf.get(it.star.systemId) ?? 1}
          onPick={() => pick(it.star)}
        />
      ))}
    </group>
  )
}

function StarBody({
  item,
  showLabel,
  members,
  onPick,
}: {
  item: { star: Star; pos: THREE.Vector3; color: THREE.Color; radius: number; far: boolean }
  showLabel: boolean
  /** сколько компонентов в системе — показываем кратность в подписи */
  members: number
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
            float halo = pow(max(0.0, 1.0 - d), 3.0) * 0.22;
            vec2 p = (vUv - 0.5) * 2.0;
            float spike = (exp(-abs(p.x) * 34.0) + exp(-abs(p.y) * 34.0)) * exp(-d * 2.8) * 0.13;
            float a = core + halo + spike;
            gl_FragColor = vec4(uColor * (0.7 + core * 1.1), clamp(a, 0.0, 1.0));
          }
        `,
      }),
    [item.color],
  )

  return (
    <group position={item.pos}>
      <mesh scale={item.radius * 0.5} onClick={onPick}>
        <sphereGeometry args={[1, 16, 12]} />
        <meshBasicMaterial color={item.color} />
      </mesh>
      <mesh ref={glowRef} scale={item.radius * (item.far ? 3.4 : 2.6)} material={glowMat}>
        <planeGeometry args={[2, 2]} />
      </mesh>
      {showLabel && (
        <Label
          position={[0, item.radius * 2.4 + 0.12, 0]}
          text={multipleName(item.star, members)}
          sub={item.star.distLy > 0 ? `${item.star.distLy.toFixed(1)} св. лет` : 'мы здесь'}
          small={item.star.distLy > 12}
          onClick={onPick}
        />
      )}
    </group>
  )
}

/**
 * Имя для подписи: у кратных систем убираем букву компонента и добавляем
 * кратность, чтобы «Сириус A» и «Сириус B» не спорили за одно место.
 */
function multipleName(s: Star, members: number): string {
  if (members <= 1) return s.name
  const base = s.name.replace(/\s+[AB]$/, '')
  const word = members === 2 ? 'двойная' : members === 3 ? 'тройная' : `${members} компонента`
  return `${base} (${word})`
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

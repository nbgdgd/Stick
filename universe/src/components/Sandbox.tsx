/**
 * Гравитационная песочница — отдельный полноэкранный слой со своим канвасом.
 *
 * Почему отдельный канвас, а не сцена внутри общего: у песочницы своя система
 * единиц (а.е./годы/массы Солнца), своё время, своя камера сверху и свой шаг
 * интегрирования, привязанный к устойчивости, а не к прокрутке даты. Мешать
 * это с уровнями масштаба означало бы протаскивать сквозь них состояние,
 * которое им не нужно.
 *
 * Интегратор и физика — в `src/lib/nbody.ts`.
 */

import { useEffect, useMemo, useRef, useState } from 'react'
import { Canvas, useFrame, useThree } from '@react-three/fiber'
import * as THREE from 'three'
import { useStore } from '../store'
import {
  PRESETS,
  circularSpeed,
  computeAccelerations,
  makeBody,
  mergeCollisions,
  pushTrail,
  step,
  totalEnergy,
  type Body,
} from '../lib/nbody'

export function Sandbox() {
  const open = useStore((s) => s.sandboxOpen)
  const setOpen = useStore((s) => s.setSandboxOpen)

  const [presetId, setPresetId] = useState(PRESETS[0].id)
  const [running, setRunning] = useState(true)
  const [speed, setSpeed] = useState(1)
  const [newMass, setNewMass] = useState(0.02)
  const [showTrails, setShowTrails] = useState(true)
  const [stats, setStats] = useState({ n: 0, years: 0, energyDrift: 0, merges: 0 })

  const preset = PRESETS.find((p) => p.id === presetId)!

  // Тела живут в ref: они меняются каждый кадр, и держать их в состоянии
  // React означало бы перерисовку дерева 60 раз в секунду
  const bodiesRef = useRef<Body[]>([])
  const clockRef = useRef({ years: 0, e0: 0, merges: 0 })
  const [resetKey, setResetKey] = useState(0)

  useEffect(() => {
    const b = preset.build()
    computeAccelerations(b)
    bodiesRef.current = b
    clockRef.current = { years: 0, e0: totalEnergy(b).total, merges: 0 }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [presetId, resetKey])

  if (!open) return null

  return (
    <div className="sandbox">
      <Canvas
        orthographic
        camera={{ zoom: 150, position: [0, 0, 10], near: 0.1, far: 100 }}
        gl={{ antialias: true }}
        dpr={[1, 2]}
      >
        <SandboxScene
          bodiesRef={bodiesRef}
          clockRef={clockRef}
          running={running}
          speed={speed}
          newMass={newMass}
          showTrails={showTrails}
          onStats={setStats}
        />
      </Canvas>

      <div className="sandbox__top">
        <div className="sandbox__title">
          <span className="sandbox__eyebrow">Симуляция · задача N тел</span>
          <h2>Гравитационная песочница</h2>
        </div>
        <button className="sandbox__close" onClick={() => setOpen(false)} aria-label="Закрыть">
          ✕
        </button>
      </div>

      <div className="sandbox__hint">
        Нажмите на пустое место, чтобы добавить массу. Она получит скорость круговой орбиты
        вокруг центра — дальше всё решает гравитация.
      </div>

      <div className="sandbox__panel">
        <div className="sandbox__presets">
          {PRESETS.map((p) => (
            <button
              key={p.id}
              className={`sandbox__preset${p.id === presetId ? ' sandbox__preset--on' : ''}`}
              onClick={() => setPresetId(p.id)}
            >
              {p.name}
            </button>
          ))}
        </div>

        <p className="sandbox__desc">{preset.description}</p>

        <div className="sandbox__row">
          <button className="sandbox__btn sandbox__btn--primary" onClick={() => setRunning((r) => !r)}>
            {running ? 'Пауза' : 'Пуск'}
          </button>
          <button className="sandbox__btn" onClick={() => setResetKey((k) => k + 1)}>
            Сбросить
          </button>
          <label className="sandbox__check">
            <input type="checkbox" checked={showTrails} onChange={(e) => setShowTrails(e.target.checked)} />
            Следы
          </label>
        </div>

        <label className="sandbox__slider">
          <span>
            Скорость <strong>{speed.toFixed(2)}×</strong>
          </span>
          <input
            type="range"
            min={0.05}
            max={4}
            step={0.05}
            value={speed}
            onChange={(e) => setSpeed(parseFloat(e.target.value))}
          />
        </label>

        <label className="sandbox__slider">
          <span>
            Масса новых тел <strong>{fmtMass(newMass)}</strong>
          </span>
          <input
            type="range"
            min={-6}
            max={-0.3}
            step={0.05}
            value={Math.log10(newMass)}
            onChange={(e) => setNewMass(Math.pow(10, parseFloat(e.target.value)))}
          />
        </label>

        <dl className="sandbox__stats">
          <div>
            <dt>Тел</dt>
            <dd>{stats.n}</dd>
          </div>
          <div>
            <dt>Прошло</dt>
            <dd>{stats.years < 10 ? stats.years.toFixed(2) : stats.years.toFixed(0)} лет</dd>
          </div>
          <div>
            <dt title="Насколько уплыла полная энергия системы: мера точности интегратора">
              Дрейф энергии
            </dt>
            <dd>{(stats.energyDrift * 100).toFixed(3)} %</dd>
          </div>
          {stats.merges > 0 && (
            <div>
              <dt title="Слияние неупруго, поэтому отсчёт энергии начинается заново">Слияний</dt>
              <dd>{stats.merges}</dd>
            </div>
          )}
        </dl>

        <p className="sandbox__note">
          Интегратор — leapfrog (симплектический), шаг подстраивается под самое быстрое
          сближение. Дрейф энергии показывает, насколько можно доверять картинке: у метода
          Эйлера он ушёл бы на проценты за десяток витков, здесь остаётся малым.
          Единицы: астрономические единицы, годы, массы Солнца, поэтому G = 4π².
        </p>
      </div>
    </div>
  )
}

function fmtMass(m: number): string {
  if (m >= 0.1) return `${m.toFixed(2)} массы Солнца`
  if (m >= 1e-3) return `${(m * 1047.6).toFixed(1)} масс Юпитера`
  return `${(m * 332946).toFixed(1)} масс Земли`
}

interface SceneProps {
  bodiesRef: React.RefObject<Body[]>
  clockRef: React.RefObject<{ years: number; e0: number; merges: number }>
  running: boolean
  speed: number
  newMass: number
  showTrails: boolean
  onStats: (s: { n: number; years: number; energyDrift: number; merges: number }) => void
}

function SandboxScene({ bodiesRef, clockRef, running, speed, newMass, showTrails, onStats }: SceneProps) {
  const { camera, size } = useThree()
  const groupRef = useRef<THREE.Group>(null)

  // Пул мешей и следов: создаём с запасом и прячем лишние, чтобы не
  // дёргать граф сцены при каждом слиянии или добавлении тела
  const MAX = 64
  const meshes = useRef<(THREE.Mesh | null)[]>([])
  const trails = useRef<(THREE.Line | null)[]>([])
  const trailGeoms = useMemo(
    () =>
      Array.from({ length: MAX }, () => {
        const g = new THREE.BufferGeometry()
        g.setAttribute('position', new THREE.BufferAttribute(new Float32Array(220 * 3), 3))
        g.setDrawRange(0, 0)
        return g
      }),
    [],
  )

  // Добавление тела по клику: скорость выбираем круговой относительно
  // суммарной массы внутри радиуса — так новое тело сразу на осмысленной
  // орбите, а не падает по прямой в центр
  useEffect(() => {
    const el = document.querySelector('.sandbox canvas') as HTMLCanvasElement | null
    if (!el) return

    function onClick(ev: MouseEvent) {
      const rect = el!.getBoundingClientRect()
      const ndcX = ((ev.clientX - rect.left) / rect.width) * 2 - 1
      const ndcY = -((ev.clientY - rect.top) / rect.height) * 2 + 1
      const cam = camera as THREE.OrthographicCamera
      // Ортографическая камера: обратное преобразование — простое
      // масштабирование на половину видимой области
      const halfW = (cam.right - cam.left) / 2 / cam.zoom
      const halfH = (cam.top - cam.bottom) / 2 / cam.zoom
      const x = ndcX * halfW
      const y = ndcY * halfH

      const bodies = bodiesRef.current
      if (!bodies || bodies.length >= MAX) return

      const r = Math.hypot(x, y)
      if (r < 1e-3) return
      // масса внутри орбиты нового тела
      let mIn = 0
      for (const b of bodies) {
        if (Math.hypot(b.x, b.y) < r) mIn += b.mass
      }
      const v = mIn > 0 ? circularSpeed(mIn, r) : 0
      bodies.push(
        makeBody({
          mass: newMass,
          x,
          y,
          // перпендикулярно радиусу — направление круговой орбиты
          vx: (-y / r) * v,
          vy: (x / r) * v,
          color: '#ff7fa8',
          label: '',
        }),
      )
      computeAccelerations(bodies)
    }

    el.addEventListener('click', onClick)
    return () => el.removeEventListener('click', onClick)
  }, [camera, newMass, bodiesRef])

  const statTimer = useRef(0)

  useFrame((_, dtReal) => {
    const bodies = bodiesRef.current
    const clock = clockRef.current
    if (!bodies || !clock) return

    if (running) {
      // Шаг по времени: ограничиваем сверху, чтобы при просадке кадров
      // симуляция не делала один огромный неустойчивый шаг
      const wall = Math.min(dtReal, 1 / 30)
      // 1 секунда реального времени = 1 год симуляции при speed = 1
      const target = wall * speed
      // Подшаги: шаг интегрирования не должен превышать долю
      // характерного времени самого быстрого сближения
      const sub = Math.min(24, Math.max(1, Math.ceil(target / 0.004)))
      const dt = target / sub
      for (let k = 0; k < sub; k++) step(bodies, dt)
      clock.years += target

      const merged = mergeCollisions(bodies)
      if (merged.length !== bodies.length) {
        bodiesRef.current = merged
        computeAccelerations(merged)
        // Слияние неупруго: часть энергии уходит «на удар». Сравнивать
        // текущую энергию с исходной после этого бессмысленно, поэтому
        // берём новую точку отсчёта и помечаем это в интерфейсе.
        clock.e0 = totalEnergy(merged).total
        clock.merges += 1
      }
      for (const b of bodiesRef.current) pushTrail(b)
    }

    const list = bodiesRef.current!

    // Отрисовка
    for (let i = 0; i < MAX; i++) {
      const m = meshes.current[i]
      const b = list[i]
      if (!m) continue
      if (!b) {
        m.visible = false
        const t = trails.current[i]
        if (t) t.visible = false
        continue
      }
      m.visible = true
      m.position.set(b.x, b.y, b.z)
      m.scale.setScalar(b.radius)
      const mat = m.material as THREE.MeshBasicMaterial
      mat.color.set(b.color)

      const t = trails.current[i]
      if (t) {
        t.visible = showTrails && b.trail.length >= 6
        const attr = trailGeoms[i].getAttribute('position') as THREE.BufferAttribute
        const n = Math.min(220, b.trail.length / 3)
        for (let k = 0; k < n; k++) {
          attr.setXYZ(k, b.trail[k * 3], b.trail[k * 3 + 1], b.trail[k * 3 + 2])
        }
        attr.needsUpdate = true
        trailGeoms[i].setDrawRange(0, n)
        ;(t.material as THREE.LineBasicMaterial).color.set(b.color)
      }
    }

    // Статистику отдаём в React не чаще пяти раз в секунду
    statTimer.current += dtReal
    if (statTimer.current > 0.2) {
      statTimer.current = 0
      const e = totalEnergy(list).total
      const drift = clock.e0 !== 0 ? Math.abs((e - clock.e0) / clock.e0) : 0
      onStats({ n: list.length, years: clock.years, energyDrift: drift, merges: clock.merges })
    }
  })

  // Подгоняем zoom под размер экрана: показываем область примерно 6×6 а.е.
  useEffect(() => {
    const cam = camera as THREE.OrthographicCamera
    cam.zoom = Math.min(size.width, size.height) / 6.5
    cam.updateProjectionMatrix()
  }, [camera, size])

  return (
    <group ref={groupRef}>
      {/* Сетка в а.е. — без неё нет чувства масштаба */}
      <SandboxGrid />
      {Array.from({ length: MAX }, (_, i) => (
        <mesh key={i} ref={(el) => { meshes.current[i] = el }} visible={false}>
          <circleGeometry args={[1, 24]} />
          <meshBasicMaterial />
        </mesh>
      ))}
      {Array.from({ length: MAX }, (_, i) => (
        // @ts-expect-error примитив line из three, типы R3F его не покрывают
        <line key={`t${i}`} ref={(el) => { trails.current[i] = el }} geometry={trailGeoms[i]} visible={false}>
          <lineBasicMaterial transparent opacity={0.45} />
        </line>
      ))}
    </group>
  )
}

/** Концентрические окружности через 1 а.е. */
function SandboxGrid() {
  const geoms = useMemo(
    () =>
      [1, 2, 3].map((r) => {
        const pts: THREE.Vector3[] = []
        for (let k = 0; k <= 128; k++) {
          const a = (k / 128) * Math.PI * 2
          pts.push(new THREE.Vector3(Math.cos(a) * r, Math.sin(a) * r, -0.01))
        }
        return { r, geom: new THREE.BufferGeometry().setFromPoints(pts) }
      }),
    [],
  )
  return (
    <group>
      {geoms.map(({ r, geom }) => (
        // @ts-expect-error примитив line из three, типы R3F его не покрывают
        <line key={r} geometry={geom}>
          <lineBasicMaterial color="#3c5a80" transparent opacity={0.16} />
        </line>
      ))}
    </group>
  )
}

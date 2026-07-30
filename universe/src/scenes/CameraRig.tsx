/**
 * Камера и управление.
 *
 * Свои контролы, а не drei/OrbitControls, по двум причинам:
 *  1. Нужно ловить момент, когда зум упёрся в предел уровня, и переходить
 *     на следующий масштаб — так зум ощущается непрерывным, как в «Powers of Ten».
 *  2. Нужен кинематографический наезд при смене уровня и наведение на объект,
 *     которые не должны конфликтовать с пользовательским вводом.
 *
 * Модель: сферические координаты вокруг цели, с инерцией.
 */

import { useEffect, useMemo, useRef } from 'react'
import { useFrame, useThree } from '@react-three/fiber'
import * as THREE from 'three'
import { LEVELS } from '../data/levels'
import { useStore } from '../store'
import { getFocusPosition, getFocusRadius } from '../lib/focus'

/** Плавная кривая для наездов: медленный старт, медленное торможение. */
function easeInOutCubic(t: number): number {
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2
}

const TRANSITION_MS = 1700

export function CameraRig() {
  const camera = useThree((s) => s.camera)
  const gl = useThree((s) => s.gl)

  const levelIndex = useStore((s) => s.levelIndex)
  const transitionDir = useStore((s) => s.transitionDir)
  const focusId = useStore((s) => s.focusId)
  const level = LEVELS[levelIndex]

  // Состояние камеры держим в ref: оно меняется каждый кадр
  const state = useRef({
    theta: Math.PI * 0.25,
    phi: Math.PI * 0.42,
    dist: level.initialCameraDist,
    target: new THREE.Vector3(),
    // инерция
    vTheta: 0,
    vPhi: 0,
    vDist: 0,
    // анимация перехода
    animFrom: level.initialCameraDist,
    animTo: level.initialCameraDist,
    animStart: 0,
    animating: false,
    // сколько кадров пользователь давит зум за пределом уровня
    edgePressure: 0,
    dragging: false,
    lastPointers: new Map<number, { x: number; y: number }>(),
    pinchDist: 0,
  })

  // Смена уровня: наезд от «продолжения» предыдущего масштаба к штатному виду.
  // Наружу — начинаем очень близко и отъезжаем; внутрь — наоборот.
  useEffect(() => {
    const s = state.current
    const from = transitionDir >= 0 ? level.minDist * 1.15 : Math.min(level.maxDist, level.initialCameraDist * 14)
    s.animFrom = from
    s.animTo = level.initialCameraDist
    s.animStart = performance.now()
    s.animating = true
    s.dist = from
    s.vDist = 0
    s.edgePressure = 0
    s.target.set(0, 0, 0)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [levelIndex])

  // Наведение на выбранный объект
  useEffect(() => {
    if (!focusId) return
    const s = state.current
    const r = getFocusRadius(focusId)
    if (r > 0) {
      s.animFrom = s.dist
      s.animTo = Math.max(level.minDist, r * 4)
      s.animStart = performance.now()
      s.animating = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [focusId])

  // Ввод: мышь, колесо и мультитач
  useEffect(() => {
    const el = gl.domElement
    const s = state.current

    const onPointerDown = (e: PointerEvent) => {
      el.setPointerCapture(e.pointerId)
      s.lastPointers.set(e.pointerId, { x: e.clientX, y: e.clientY })
      s.dragging = true
      if (s.lastPointers.size === 2) {
        const [a, b] = [...s.lastPointers.values()]
        s.pinchDist = Math.hypot(a.x - b.x, a.y - b.y)
      }
    }

    const onPointerMove = (e: PointerEvent) => {
      if (!s.lastPointers.has(e.pointerId)) return
      const prev = s.lastPointers.get(e.pointerId)!
      const dx = e.clientX - prev.x
      const dy = e.clientY - prev.y
      s.lastPointers.set(e.pointerId, { x: e.clientX, y: e.clientY })

      if (s.lastPointers.size >= 2) {
        // Пинч — зум
        const [a, b] = [...s.lastPointers.values()]
        const d = Math.hypot(a.x - b.x, a.y - b.y)
        if (s.pinchDist > 0) {
          const ratio = s.pinchDist / d
          s.dist *= Math.pow(ratio, 1.1)
          s.animating = false
        }
        s.pinchDist = d
      } else {
        // Одним пальцем — орбита. Чувствительность в радианах на пиксель.
        const rect = el.getBoundingClientRect()
        s.vTheta -= (dx / rect.width) * 3.2
        s.vPhi -= (dy / rect.height) * 2.4
        s.animating = false
      }
    }

    const onPointerUp = (e: PointerEvent) => {
      s.lastPointers.delete(e.pointerId)
      if (s.lastPointers.size < 2) s.pinchDist = 0
      if (s.lastPointers.size === 0) s.dragging = false
    }

    const onWheel = (e: WheelEvent) => {
      e.preventDefault()
      // Логарифмический зум: на любом масштабе один щелчок колеса
      // меняет расстояние на одинаковую долю
      s.vDist += Math.sign(e.deltaY) * Math.min(Math.abs(e.deltaY) / 100, 3) * 0.09
      s.animating = false
    }

    el.addEventListener('pointerdown', onPointerDown)
    el.addEventListener('pointermove', onPointerMove)
    el.addEventListener('pointerup', onPointerUp)
    el.addEventListener('pointercancel', onPointerUp)
    el.addEventListener('wheel', onWheel, { passive: false })
    return () => {
      el.removeEventListener('pointerdown', onPointerDown)
      el.removeEventListener('pointermove', onPointerMove)
      el.removeEventListener('pointerup', onPointerUp)
      el.removeEventListener('pointercancel', onPointerUp)
      el.removeEventListener('wheel', onWheel)
    }
  }, [gl])

  const tmp = useMemo(() => new THREE.Vector3(), [])

  useFrame((_, dt) => {
    const s = state.current
    const store = useStore.getState()
    const lvl = LEVELS[store.levelIndex]

    // Анимация перехода имеет приоритет над инерцией
    if (s.animating) {
      const t = Math.min(1, (performance.now() - s.animStart) / TRANSITION_MS)
      const e = easeInOutCubic(t)
      // Интерполируем логарифм расстояния: линейная интерполяция на
      // шести порядках выглядела бы как рывок в конце
      s.dist = Math.exp(Math.log(s.animFrom) * (1 - e) + Math.log(s.animTo) * e)
      // Лёгкий доворот камеры за время наезда — кадр «дышит»
      s.theta += dt * 0.05
      if (t >= 1) {
        s.animating = false
        if (store.transitioning) store.finishTransition()
      }
    } else {
      // Инерция орбиты
      s.theta += s.vTheta * dt * 60 * 0.016
      s.phi += s.vPhi * dt * 60 * 0.016
      s.vTheta *= 0.9
      s.vPhi *= 0.9
      // Зум с инерцией, множителем — иначе на больших расстояниях он вязкий
      s.dist *= Math.exp(s.vDist)
      s.vDist *= 0.82
      if (Math.abs(s.vDist) < 1e-5) s.vDist = 0
    }

    // Не даём перевернуть камеру через полюс
    s.phi = Math.max(0.06, Math.min(Math.PI - 0.06, s.phi))

    // Переход между уровнями по упору зума.
    // Считаем «давление» на предел: случайный перескок за границу не должен
    // мгновенно менять уровень, нужно осознанное продолжение жеста.
    if (!s.animating && !store.transitioning) {
      if (s.dist > lvl.maxDist) {
        s.dist = lvl.maxDist
        s.edgePressure += dt
        if (s.edgePressure > 0.28 && store.levelIndex < LEVELS.length - 1) {
          store.nextLevel()
          s.edgePressure = 0
        }
      } else if (s.dist < lvl.minDist) {
        s.dist = lvl.minDist
        s.edgePressure += dt
        if (s.edgePressure > 0.28 && store.levelIndex > 0) {
          store.prevLevel()
          s.edgePressure = 0
        }
      } else {
        s.edgePressure = Math.max(0, s.edgePressure - dt * 2)
      }
    }

    // Цель камеры: выбранный объект или центр сцены
    if (store.focusId) {
      const p = getFocusPosition(store.focusId)
      if (p) s.target.lerp(p, Math.min(1, dt * 3.5))
    } else {
      s.target.lerp(tmp.set(0, 0, 0), Math.min(1, dt * 2.5))
    }

    // Сферические -> декартовы
    const sinPhi = Math.sin(s.phi)
    camera.position.set(
      s.target.x + s.dist * sinPhi * Math.cos(s.theta),
      s.target.y + s.dist * Math.cos(s.phi),
      s.target.z + s.dist * sinPhi * Math.sin(s.theta),
    )
    camera.lookAt(s.target)

    // near/far подстраиваем под расстояние: иначе на близком зуме
    // пропадёт точность глубины, а на дальнем — обрежется сцена
    const cam = camera as THREE.PerspectiveCamera
    cam.near = Math.max(1e-6, s.dist * 0.002)
    cam.far = Math.max(s.dist * 40, lvl.maxDist * 6)
    cam.updateProjectionMatrix()

    // Индикатор масштаба читает это значение
    if (Math.abs(store.cameraDist - s.dist) / (s.dist || 1) > 0.005) {
      store.setCameraDist(s.dist)
    }
  })

  return null
}

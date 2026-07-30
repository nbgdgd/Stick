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
import { sunGeometry } from '../lib/sun'
import { cameraBus } from '../lib/cameraBus'

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
    // объект, на который надо навестись, как только сцена сообщит его радиус
    pendingFocus: null as string | null,
    // для распознавания двойного касания
    lastTapAt: 0,
    movedPx: 0,
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
    s.pendingFocus = null
    s.target.set(0, 0, 0)

    // Наклон камеры по уровню, если задан
    if (level.initialPhi !== undefined) s.phi = level.initialPhi

    // На уровнях Земли ставим камеру со стороны Солнца: иначе при открытии
    // мы смотрим в случайную точку и с равной вероятностью попадаем
    // на полностью ночную сторону, где смотреть нечего.
    if (level.id === 'earth' || level.id === 'earth-moon') {
      const sun = sunGeometry(useStore.getState().simTime)
      // направление на Солнце в координатах сцены: (x, z, -y)
      const thetaSun = Math.atan2(-sun.direction[1], sun.direction[0])
      // сдвиг на 0,7 рад оставляет в кадре и освещённую сторону, и терминатор
      s.theta = thetaSun + 0.7
      s.phi = 1.22
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [levelIndex])

  // Наведение на выбранный объект.
  // Радиус объекта появляется в реестре только после первого кадра сцены
  // (его пишет useFrame), поэтому здесь лишь помечаем цель, а сам наезд
  // запускается в useFrame, когда радиус станет известен.
  useEffect(() => {
    state.current.pendingFocus = focusId
  }, [focusId])

  // Ввод: мышь, колесо и мультитач
  useEffect(() => {
    const el = gl.domElement
    const s = state.current

    const onPointerDown = (e: PointerEvent) => {
      el.setPointerCapture(e.pointerId)
      s.lastPointers.set(e.pointerId, { x: e.clientX, y: e.clientY })
      s.dragging = true
      s.movedPx = 0
      if (s.lastPointers.size === 2) {
        const [a, b] = [...s.lastPointers.values()]
        s.pinchDist = Math.hypot(a.x - b.x, a.y - b.y)
        // Начался пинч — гасим инерцию вращения, иначе камера уезжает
        s.vTheta = 0
        s.vPhi = 0
      }
    }

    const onPointerMove = (e: PointerEvent) => {
      if (!s.lastPointers.has(e.pointerId)) return
      const prev = s.lastPointers.get(e.pointerId)!
      const dx = e.clientX - prev.x
      const dy = e.clientY - prev.y
      s.lastPointers.set(e.pointerId, { x: e.clientX, y: e.clientY })
      s.movedPx += Math.abs(dx) + Math.abs(dy)

      if (s.lastPointers.size >= 2) {
        const [a, b] = [...s.lastPointers.values()]
        const d = Math.hypot(a.x - b.x, a.y - b.y)
        if (s.pinchDist > 0 && d > 0) {
          // Показатель 1,7 делает жест «сильнее», чем один к одному:
          // на телефоне пальцы разводятся максимум вдвое, и при
          // передаточном отношении 1:1 зум ощущается вязким.
          const ratio = s.pinchDist / d
          s.dist *= Math.pow(ratio, 1.7)
          s.animating = false
          cameraBus.zooming = true
        }
        s.pinchDist = d
      } else {
        // Одним пальцем — орбита
        const rect = el.getBoundingClientRect()
        s.vTheta -= (dx / rect.width) * 4.2
        s.vPhi -= (dy / rect.height) * 3.2
        s.animating = false
      }
    }

    const onPointerUp = (e: PointerEvent) => {
      const wasSingle = s.lastPointers.size === 1
      s.lastPointers.delete(e.pointerId)
      if (s.lastPointers.size < 2) {
        s.pinchDist = 0
        cameraBus.zooming = false
      }
      if (s.lastPointers.size === 0) {
        s.dragging = false
        // Двойное касание — приблизиться. Порог смещения нужен, чтобы
        // короткий свайп не считался тапом.
        if (wasSingle && s.movedPx < 12) {
          const now = performance.now()
          if (now - s.lastTapAt < 320) {
            s.animFrom = s.dist
            s.animTo = Math.max(LEVELS[useStore.getState().levelIndex].minDist, s.dist * 0.45)
            s.animStart = now
            s.animating = true
            s.lastTapAt = 0
          } else {
            s.lastTapAt = now
          }
        }
      }
    }

    const onWheel = (e: WheelEvent) => {
      e.preventDefault()
      // Логарифмический зум: один щелчок колеса меняет расстояние
      // на одинаковую долю на любом масштабе
      s.vDist += Math.sign(e.deltaY) * Math.min(Math.abs(e.deltaY) / 100, 3) * 0.14
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
      // Кнопки зума работают и во время наезда: пользователь имеет
      // право перебить анимацию
      if (cameraBus.zoomFactor !== 1) {
        s.dist *= cameraBus.zoomFactor
        cameraBus.zoomFactor = 1
        s.animating = false
      }
    } else {
      if (cameraBus.zoomFactor !== 1) {
        s.dist *= cameraBus.zoomFactor
        cameraBus.zoomFactor = 1
      }
      // Инерция орбиты
      s.theta += s.vTheta * dt * 60 * 0.016
      s.phi += s.vPhi * dt * 60 * 0.016
      s.vTheta *= 0.93
      s.vPhi *= 0.93
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
      // Порог 0,55 с вместо 0,28: при коротком пороге уровень
      // переключался посреди обычного пинча и это читалось как сбой.
      // Прогресс отдаём в интерфейс, чтобы переход не был неожиданным.
      const HOLD = 0.55
      if (s.dist > lvl.maxDist) {
        s.dist = lvl.maxDist
        s.edgePressure += dt
        cameraBus.edgeDir = store.levelIndex < LEVELS.length - 1 ? 1 : 0
        if (s.edgePressure > HOLD && store.levelIndex < LEVELS.length - 1) {
          store.nextLevel()
          s.edgePressure = 0
        }
      } else if (s.dist < lvl.minDist) {
        s.dist = lvl.minDist
        s.edgePressure += dt
        cameraBus.edgeDir = store.levelIndex > 0 ? -1 : 0
        if (s.edgePressure > HOLD && store.levelIndex > 0) {
          store.prevLevel()
          s.edgePressure = 0
        }
      } else {
        s.edgePressure = Math.max(0, s.edgePressure - dt * 2)
        if (s.edgePressure === 0) cameraBus.edgeDir = 0
      }
      cameraBus.edgePressure = Math.min(1, s.edgePressure / HOLD)
    }

    // Наезд на объект: ждём, пока сцена зарегистрирует его радиус
    if (s.pendingFocus) {
      const r = getFocusRadius(s.pendingFocus)
      if (r > 0) {
        s.animFrom = s.dist
        // Коэффициент 6 оставляет объект примерно на половине высоты кадра
        // и не режет кольца и спутники по краям
        s.animTo = Math.max(lvl.minDist, r * 10)
        s.animStart = performance.now()
        s.animating = true

        // В Солнечной системе Солнце стоит в начале координат. Освещённая
        // сторона планеты обращена к центру, поэтому камеру надо ставить
        // между Солнцем и планетой: если отвести её по радиусу наружу,
        // в кадр попадёт ночная сторона.
        if (lvl.id === 'solar-system') {
          const fp = getFocusPosition(s.pendingFocus)
          if (fp && fp.length() > 1e-6) {
            s.theta = Math.atan2(fp.z, fp.x) + Math.PI
            s.phi = 1.32
          }
        }
        s.pendingFocus = null
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

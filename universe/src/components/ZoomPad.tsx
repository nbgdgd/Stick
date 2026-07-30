/**
 * Кнопки зума и навигации по уровням.
 *
 * Раньше кнопки «+» и «−» переключали уровень масштаба — это ломало
 * ожидание: рядом с 3D-сценой плюс и минус означают зум. Теперь плюс и
 * минус зумят, причём с повтором при удержании, а уровни переключаются
 * отдельными стрелками с подписью, куда именно они ведут.
 *
 * Зум идёт через `cameraBus`, а не через состояние React: при удержании
 * кнопки расстояние меняется каждый кадр, и перерисовка дерева на 60 Гц
 * съела бы весь бюджет телефона.
 */

import { useEffect, useRef, useState, type PointerEvent as ReactPointerEvent } from 'react'
import { useStore } from '../store'
import { LEVELS } from '../data/levels'
import { cameraBus, requestZoom } from '../lib/cameraBus'

/**
 * Захват указателя, чтобы отпускание пальца за пределами кнопки тоже
 * останавливало зум. Не критично: если браузер откажет (указателя уже нет),
 * зум всё равно должен работать — поэтому не даём исключению всплыть.
 */
function capture(e: ReactPointerEvent<HTMLButtonElement>) {
  try {
    e.currentTarget.setPointerCapture(e.pointerId)
  } catch {
    // указателя с таким id уже нет — ничего страшного
  }
}

/** За сколько меняется расстояние за секунду удержания. */
const ZOOM_RATE_PER_SEC = 3.2

export function ZoomPad() {
  const levelIndex = useStore((s) => s.levelIndex)
  const setLevel = useStore((s) => s.setLevel)
  const transitioning = useStore((s) => s.transitioning)

  // Удержание кнопки зума.
  //
  // Таймер, а не requestAnimationFrame: при тяжёлой сцене кадры идут редко,
  // и цикл на rAF успевал сделать два шага за секунду удержания — зум
  // ощущался сломанным. Таймер тикает независимо от отрисовки, а сам зум
  // считается по фактически прошедшему времени.
  const hold = useRef<{ timer: number; startedAt: number; applied: number; dir: number } | null>(null)

  // Подсказка о переходе на соседний уровень: показывается, когда
  // пользователь упирается в предел зума
  const [edge, setEdge] = useState({ p: 0, dir: 0 })
  useEffect(() => {
    let id = 0
    const tick = () => {
      setEdge((prev) =>
        Math.abs(prev.p - cameraBus.edgePressure) > 0.02 || prev.dir !== cameraBus.edgeDir
          ? { p: cameraBus.edgePressure, dir: cameraBus.edgeDir }
          : prev,
      )
      id = requestAnimationFrame(tick)
    }
    id = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(id)
  }, [])

  function endHold() {
    if (!hold.current) return
    clearInterval(hold.current.timer)
    hold.current = null
    cameraBus.zooming = false
  }

  function startHold(dir: number) {
    // Всегда гасим предыдущее удержание: если pointerup не долетел
    // (палец ушёл за пределы кнопки, событие потерялось), старый цикл
    // остался бы работать и зум поехал бы сам по себе
    endHold()
    cameraBus.zooming = true
    // Считаем от момента нажатия, а не складываем шаги между тиками.
    // Разница принципиальная: при тяжёлой сцене таймер стартует с
    // задержкой и часть времени между тиками терялась бы, а так
    // пройденное время учитывается целиком, сколько бы тиков ни случилось.
    const state = { timer: 0, startedAt: performance.now(), applied: 0, dir }
    state.timer = window.setInterval(() => {
      const elapsed = Math.min((performance.now() - state.startedAt) / 1000, 30)
      const delta = elapsed - state.applied
      if (delta <= 0) return
      state.applied = elapsed
      // Экспоненциально по времени: удержание даёт равномерный по ощущению
      // зум независимо от текущего масштаба
      requestZoom(Math.pow(ZOOM_RATE_PER_SEC, dir * delta))
    }, 32)
    hold.current = state
  }

  // Отпускание пальца где угодно должно останавливать зум: если отпустить
  // за пределами кнопки, её собственный pointerup не придёт
  useEffect(() => {
    const stop = () => endHold()
    window.addEventListener('pointerup', stop)
    window.addEventListener('pointercancel', stop)
    window.addEventListener('blur', stop)
    return () => {
      window.removeEventListener('pointerup', stop)
      window.removeEventListener('pointercancel', stop)
      window.removeEventListener('blur', stop)
      endHold()
    }
  }, [])


  const prev = levelIndex > 0 ? LEVELS[levelIndex - 1] : null
  const next = levelIndex < LEVELS.length - 1 ? LEVELS[levelIndex + 1] : null

  return (
    <div className="zoompad">
      {/* Подсказка о смене уровня при упоре в предел зума */}
      {edge.dir !== 0 && edge.p > 0.08 && (
        <div className="zoompad__edge">
          <div className="zoompad__edge-fill" style={{ width: `${edge.p * 100}%` }} />
          <span>
            {edge.dir > 0 ? `Дальше: ${next?.title ?? ''}` : `Ближе: ${prev?.title ?? ''}`}
          </span>
        </div>
      )}

      {/* Один вертикальный столбец: горизонтальный ряд кнопок уровня
          налезал на панель времени на экранах уже 430 px */}
      <div className="zoompad__stack">
        <button
          className="zoompad__lvl"
          onClick={() => setLevel(levelIndex + 1)}
          disabled={!next || transitioning}
          title={next ? `Наружу: ${next.title}` : 'Это самый крупный масштаб'}
          aria-label={next ? `Наружу: ${next.title}` : 'Наружу'}
        >
          <svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M12 19V5M5 12l7-7 7 7" />
          </svg>
        </button>

        <button
          className="zoompad__btn"
          aria-label="Приблизить"
          onPointerDown={(e) => {
            startHold(-1)
            capture(e)
          }}
          onPointerUp={endHold}
          onPointerCancel={endHold}
          onPointerLeave={endHold}
        >
          <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round">
            <path d="M12 6v12M6 12h12" />
          </svg>
        </button>
        <button
          className="zoompad__btn"
          aria-label="Отдалить"
          onPointerDown={(e) => {
            startHold(1)
            capture(e)
          }}
          onPointerUp={endHold}
          onPointerCancel={endHold}
          onPointerLeave={endHold}
        >
          <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round">
            <path d="M6 12h12" />
          </svg>
        </button>

        <button
          className="zoompad__lvl"
          onClick={() => setLevel(levelIndex - 1)}
          disabled={!prev || transitioning}
          title={prev ? `Внутрь: ${prev.title}` : 'Это самый близкий масштаб'}
          aria-label={prev ? `Внутрь: ${prev.title}` : 'Внутрь'}
        >
          <svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M12 5v14M5 12l7 7 7-7" />
          </svg>
        </button>
      </div>

    </div>
  )
}

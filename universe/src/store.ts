/**
 * Глобальное состояние приложения. Zustand выбран за то, что подписка
 * на отдельные поля не перерисовывает всё дерево — важно, потому что
 * время симуляции меняется каждый кадр.
 */

import { create } from 'zustand'
import { LEVELS } from './data/levels'
import { selectionSuppressed } from './lib/tapGuard'

export interface SelectedObject {
  id: string
  name: string
  kind: string
  blurb?: string
  facts: { label: string; value: string }[]
  source?: string
  /** пометка, что изображение/модель — художественная реконструкция */
  artistic?: boolean
  /** расстояние от Земли в метрах, если применимо */
  distanceM?: number
  texture?: string
}

interface UniverseState {
  /** индекс текущего уровня масштаба */
  levelIndex: number
  /** идёт кинематографический переход между уровнями */
  transitioning: boolean
  /** направление перехода: +1 наружу, −1 внутрь */
  transitionDir: number
  /** прогресс перехода 0..1 */
  transitionT: number

  /** время симуляции, Unix-миллисекунды */
  simTime: number
  /** множитель скорости времени: сколько секунд симуляции на секунду реального времени */
  timeScale: number
  paused: boolean

  /** true — реальные расстояния, false — сжатый читаемый масштаб */
  realScale: boolean
  /** показывать орбиты */
  showOrbits: boolean
  /** показывать подписи */
  showLabels: boolean

  selected: SelectedObject | null
  searchOpen: boolean
  /** камера навелась на объект (id) */
  focusId: string | null
  /** дистанция камеры в единицах текущей сцены — пишется из R3F каждый кадр */
  cameraDist: number

  /** N-body песочница активна */
  sandboxOpen: boolean

  /**
   * Уровень качества отрисовки. Меняется автоматически по частоте кадров
   * (см. AdaptiveQuality), пользователь может зафиксировать вручную.
   */
  quality: 'high' | 'medium' | 'low'
  /** true — пользователь выбрал качество сам, автоподбор отключён */
  qualityLocked: boolean

  setLevel: (i: number, dir?: number) => void
  nextLevel: () => void
  prevLevel: () => void
  finishTransition: () => void
  setTransitionT: (t: number) => void

  setSimTime: (t: number) => void
  advanceTime: (dtMs: number) => void
  setTimeScale: (s: number) => void
  togglePause: () => void
  resetTime: () => void

  setRealScale: (v: boolean) => void
  toggleOrbits: () => void
  toggleLabels: () => void

  select: (o: SelectedObject | null) => void
  setSearchOpen: (v: boolean) => void
  setFocus: (id: string | null) => void
  setCameraDist: (d: number) => void
  setSandboxOpen: (v: boolean) => void
  setQuality: (q: 'high' | 'medium' | 'low') => void
  lockQuality: (q: 'high' | 'medium' | 'low') => void
}

export const useStore = create<UniverseState>((set, get) => ({
  levelIndex: 0,
  transitioning: false,
  transitionDir: 1,
  transitionT: 0,

  simTime: Date.now(),
  timeScale: 1,
  paused: false,

  realScale: false,
  showOrbits: true,
  showLabels: true,

  selected: null,
  searchOpen: false,
  focusId: null,
  cameraDist: 0,
  sandboxOpen: false,
  quality: 'high',
  qualityLocked: false,

  setLevel: (i, dir) => {
    const clamped = Math.max(0, Math.min(LEVELS.length - 1, i))
    const cur = get().levelIndex
    if (clamped === cur) return
    set({
      levelIndex: clamped,
      transitioning: true,
      transitionDir: dir ?? Math.sign(clamped - cur),
      transitionT: 0,
      selected: null,
      focusId: null,
    })
  },
  nextLevel: () => get().setLevel(get().levelIndex + 1, 1),
  prevLevel: () => get().setLevel(get().levelIndex - 1, -1),
  finishTransition: () => set({ transitioning: false, transitionT: 1 }),
  setTransitionT: (t) => set({ transitionT: t }),

  setSimTime: (t) => set({ simTime: t }),
  advanceTime: (dtMs) => set((s) => ({ simTime: s.simTime + dtMs })),
  setTimeScale: (s) => set({ timeScale: s }),
  togglePause: () => set((s) => ({ paused: !s.paused })),
  resetTime: () => set({ simTime: Date.now(), timeScale: 1, paused: false }),

  setRealScale: (v) => set({ realScale: v }),
  toggleOrbits: () => set((s) => ({ showOrbits: !s.showOrbits })),
  toggleLabels: () => set((s) => ({ showLabels: !s.showLabels })),

  // Выбор объекта после протяжки игнорируется: см. lib/tapGuard.
  // Снятие выбора (null) проходит всегда — закрывать панель нужно уметь
  // в любой момент.
  select: (o) => {
    if (o !== null && selectionSuppressed()) return
    set({ selected: o })
  },
  setSearchOpen: (v) => set({ searchOpen: v }),
  setFocus: (id) => {
    if (id !== null && selectionSuppressed()) return
    set({ focusId: id })
  },
  setCameraDist: (d) => set({ cameraDist: d }),
  setSandboxOpen: (v) => set({ sandboxOpen: v }),
  setQuality: (q) => set((s) => (s.qualityLocked ? s : { quality: q })),
  lockQuality: (q) => set({ quality: q, qualityLocked: true }),
}))

/** Пресеты скорости времени: подписи и значения (секунд симуляции на секунду). */
export const TIME_PRESETS = [
  { label: 'Реальное', value: 1 },
  { label: '1 мин/с', value: 60 },
  { label: '1 ч/с', value: 3600 },
  { label: '1 сут/с', value: 86400 },
  { label: '1 нед/с', value: 604800 },
  { label: '1 мес/с', value: 2629800 },
  { label: '1 год/с', value: 31557600 },
  { label: '10 лет/с', value: 315576000 },
  { label: '100 лет/с', value: 3155760000 },
]

// Store выставлен в window намеренно: скрипт скриншотов и ручная отладка
// в консоли браузера должны уметь переключать уровень и время без щелчков
// по интерфейсу. Данных пользователя здесь нет, только состояние вида.
declare global {
  interface Window {
    __universeStore?: typeof useStore
  }
}
if (typeof window !== 'undefined') window.__universeStore = useStore

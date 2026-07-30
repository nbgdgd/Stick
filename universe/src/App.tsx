import { Suspense, useEffect } from 'react'
import { Canvas } from '@react-three/fiber'
import { LEVELS } from './data/levels'
import { useStore } from './store'
import { SceneRoot } from './scenes/SceneRoot'
import { Hud } from './components/Hud'
import { FactPanel } from './components/FactPanel'
import { SearchOverlay } from './components/SearchOverlay'
import { TimeControls } from './components/TimeControls'
import { LevelRail } from './components/LevelRail'
import { LoadingVeil } from './components/LoadingVeil'
import { Sandbox } from './components/Sandbox'
import { ZoomPad } from './components/ZoomPad'
import { useBackButton } from './lib/useBackButton'

export default function App() {
  const levelIndex = useStore((s) => s.levelIndex)
  const paused = useStore((s) => s.paused)
  const timeScale = useStore((s) => s.timeScale)
  const advanceTime = useStore((s) => s.advanceTime)
  const level = LEVELS[levelIndex]

  // Системная кнопка «назад» закрывает открытый слой, а не выходит сразу
  useBackButton()

  // Ход времени симуляции. Держим его в rAF, а не внутри useFrame,
  // чтобы время шло независимо от того, что рисует конкретная сцена.
  useEffect(() => {
    let raf = 0
    let last = performance.now()
    const tick = (now: number) => {
      const dt = Math.min(now - last, 100) // защита от скачка после сворачивания
      last = now
      if (!paused) advanceTime(dt * timeScale)
      raf = requestAnimationFrame(tick)
    }
    raf = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(raf)
  }, [paused, timeScale, advanceTime])

  // Клавиатура — удобно и при отладке, и на планшете с клавиатурой
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const s = useStore.getState()
      if (e.key === 'ArrowRight' || e.key === ']') s.nextLevel()
      else if (e.key === 'ArrowLeft' || e.key === '[') s.prevLevel()
      else if (e.key === ' ') {
        e.preventDefault()
        s.togglePause()
      } else if (e.key === '/') {
        e.preventDefault()
        s.setSearchOpen(true)
      } else if (e.key === 'g') {
        s.setSandboxOpen(!s.sandboxOpen)
      } else if (e.key === 'Escape') {
        s.setSearchOpen(false)
        s.select(null)
        s.setSandboxOpen(false)
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  return (
    <div className="app">
      <Canvas
        // logarithmicDepthBuffer: внутри одной сцены диапазон расстояний
        // доходит до шести порядков, обычный z-буфер этого не держит
        gl={{ antialias: true, logarithmicDepthBuffer: true, powerPreference: 'high-performance' }}
        camera={{ fov: 55, near: 0.001, far: 1e7, position: [0, 0, level.initialCameraDist] }}
        // Верхняя граница 1,75, а не 2: на телефоне с плотностью 3x
        // разница незаметна, а пикселей вдвое меньше. Дальше значение
        // подстраивает AdaptiveQuality по фактической частоте кадров.
        dpr={[1, 1.75]}
      >
        <Suspense fallback={null}>
          <SceneRoot />
        </Suspense>
      </Canvas>

      <LoadingVeil />
      <Hud />
      <LevelRail />
      <ZoomPad />
      <TimeControls />
      <FactPanel />
      <SearchOverlay />
      <Sandbox />
    </div>
  )
}

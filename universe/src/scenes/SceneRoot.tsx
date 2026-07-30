/**
 * Корень сцены: выбирает сцену текущего уровня, ставит камеру и
 * постобработку. Между уровнями сцены не смешиваются — вместо кросс-фейда
 * работает наезд камеры (см. CameraRig), а стык прикрывает вспышка в
 * LoadingVeil. Это дешевле, чем держать в памяти две сцены сразу.
 */

import { Suspense, useEffect } from 'react'
import { EffectComposer, Bloom, Vignette } from '@react-three/postprocessing'
import { useStore } from '../store'
import { LEVELS } from '../data/levels'
import { CameraRig } from './CameraRig'
import { clearFocusRegistry } from '../lib/focus'
import { EarthScene } from './EarthScene'
import { EarthMoonScene } from './EarthMoonScene'
import { SolarSystemScene } from './SolarSystemScene'
import { NearbyStarsScene } from './NearbyStarsScene'
import { MilkyWayScene } from './MilkyWayScene'
import { LocalGroupScene } from './LocalGroupScene'
import { LaniakeaScene } from './LaniakeaScene'
import { ObservableUniverseScene } from './ObservableUniverseScene'

export function SceneRoot() {
  const levelIndex = useStore((s) => s.levelIndex)
  const level = LEVELS[levelIndex]

  // Координаты объектов прошлой сцены больше не действительны
  useEffect(() => {
    clearFocusRegistry()
  }, [levelIndex])

  return (
    <>
      <CameraRig />
      <Suspense fallback={null}>
        {level.id === 'earth' && <EarthScene />}
        {level.id === 'earth-moon' && <EarthMoonScene />}
        {level.id === 'solar-system' && <SolarSystemScene />}
        {level.id === 'nearby-stars' && <NearbyStarsScene />}
        {level.id === 'milky-way' && <MilkyWayScene />}
        {level.id === 'local-group' && <LocalGroupScene />}
        {level.id === 'laniakea' && <LaniakeaScene />}
        {level.id === 'observable-universe' && <ObservableUniverseScene />}
      </Suspense>

      {/* Bloom ставим сдержанно: на ярких точках он нужен, чтобы звёзды
          выглядели как источники света, но на телефоне это самый дорогой
          проход, поэтому разрешение half и один mip. */}
      <EffectComposer enableNormalPass={false}>
        <Bloom
          // На звёздных уровнях яркие точки должны «сиять», но порог 0,12
          // подхватывал вообще всё и заливал кадр. Поднят до 0,45.
          intensity={level.index >= 3 ? 0.7 : 0.55}
          luminanceThreshold={level.index >= 3 ? 0.45 : 0.6}
          luminanceSmoothing={0.35}
          mipmapBlur
          radius={0.65}
        />
        <Vignette eskil={false} offset={0.22} darkness={0.72} />
      </EffectComposer>
    </>
  )
}

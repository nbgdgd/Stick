/**
 * Светящаяся точка-биллборд с радиальным затуханием.
 *
 * Нужна отдельным компонентом, потому что плоскость с обычным материалом
 * рисуется квадратом: без затухания по радиусу маркеры скоплений и туманностей
 * выглядели как цветные прямоугольники. Здесь альфа падает по гауссиане,
 * поэтому получается именно свечение.
 */

import { useMemo, useRef } from 'react'
import { useFrame } from '@react-three/fiber'
import * as THREE from 'three'

interface GlowSpriteProps {
  color: string
  /** радиус свечения в единицах сцены */
  size: number
  /** яркость 0..1 */
  intensity?: number
  /** жёсткость ядра: больше — компактнее */
  hardness?: number
  /** добавить крестообразные лучи, как у ярких звёзд */
  spikes?: boolean
  /** плотное ядро в центре */
  core?: boolean
  onClick?: (e: THREE.Intersection & { stopPropagation: () => void }) => void
}

export function GlowSprite({
  color,
  size,
  intensity = 0.9,
  hardness = 9,
  spikes = false,
  core = true,
  onClick,
}: GlowSpriteProps) {
  const ref = useRef<THREE.Mesh>(null)

  const mat = useMemo(
    () =>
      new THREE.ShaderMaterial({
        transparent: true,
        depthWrite: false,
        blending: THREE.AdditiveBlending,
        uniforms: {
          uColor: { value: new THREE.Color(color) },
          uIntensity: { value: intensity },
          uHardness: { value: hardness },
          uSpikes: { value: spikes ? 1 : 0 },
          uCore: { value: core ? 1 : 0 },
        },
        vertexShader: /* glsl */ `
          varying vec2 vUv;
          void main() {
            vUv = uv;
            gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
          }
        `,
        fragmentShader: /* glsl */ `
          uniform vec3 uColor;
          uniform float uIntensity;
          uniform float uHardness;
          uniform float uSpikes;
          uniform float uCore;
          varying vec2 vUv;

          void main() {
            vec2 p = (vUv - 0.5) * 2.0;
            float d = length(p);
            if (d > 1.0) discard;

            // Широкое гало: спад по степенному закону, как у реального
            // рассеяния света в оптике
            float halo = pow(max(0.0, 1.0 - d), 2.6);
            // Плотное ядро
            float c = uCore > 0.5 ? exp(-d * d * uHardness) : 0.0;
            // Дифракционные лучи
            float sp = uSpikes > 0.5
              ? (exp(-abs(p.x) * 24.0) + exp(-abs(p.y) * 24.0)) * exp(-d * 2.4) * 0.25
              : 0.0;

            float a = (halo * 0.55 + c * 0.75 + sp) * uIntensity;
            vec3 col = uColor * (0.75 + c * 0.9);
            gl_FragColor = vec4(col, clamp(a, 0.0, 1.0));
          }
        `,
      }),
    [color, intensity, hardness, spikes, core],
  )

  // Разворот к камере каждый кадр: без этого плоскость видна с ребра
  useFrame((state) => {
    if (ref.current) ref.current.quaternion.copy(state.camera.quaternion)
  })

  return (
    <mesh ref={ref} scale={size} material={mat} onClick={onClick as never}>
      <planeGeometry args={[2, 2]} />
    </mesh>
  )
}

/**
 * Плоское полупрозрачное облако без ядра — для войдов и протяжённых
 * структур, которые не являются источниками света.
 */
export function SoftDisc({
  color,
  size,
  opacity = 0.22,
  onClick,
}: {
  color: string
  size: number
  opacity?: number
  onClick?: (e: never) => void
}) {
  const ref = useRef<THREE.Mesh>(null)

  const mat = useMemo(
    () =>
      new THREE.ShaderMaterial({
        transparent: true,
        depthWrite: false,
        uniforms: { uColor: { value: new THREE.Color(color) }, uOpacity: { value: opacity } },
        vertexShader: /* glsl */ `
          varying vec2 vUv;
          void main() {
            vUv = uv;
            gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
          }
        `,
        fragmentShader: /* glsl */ `
          uniform vec3 uColor;
          uniform float uOpacity;
          varying vec2 vUv;
          void main() {
            float d = length((vUv - 0.5) * 2.0);
            if (d > 1.0) discard;
            // Кольцо: у войда важна граница, а не заливка
            float edge = smoothstep(0.62, 0.98, d) * (1.0 - smoothstep(0.98, 1.0, d));
            float fill = (1.0 - smoothstep(0.0, 1.0, d)) * 0.22;
            gl_FragColor = vec4(uColor, (edge * 0.85 + fill) * uOpacity);
          }
        `,
      }),
    [color, opacity],
  )

  useFrame((state) => {
    if (ref.current) ref.current.quaternion.copy(state.camera.quaternion)
  })

  return (
    <mesh ref={ref} scale={size} material={mat} onClick={onClick as never}>
      <planeGeometry args={[2, 2]} />
    </mesh>
  )
}

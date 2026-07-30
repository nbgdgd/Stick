/**
 * Фон звёздного неба — целиком процедурный.
 *
 * От готовой панорамы Млечного Пути пришлось отказаться: небесная сфера
 * занимает все 360°, а поле зрения — 55°, поэтому текстура 2048 px
 * растягивается примерно в шесть раз и превращается в размытые пятна.
 * Никакое разрешение это не решает дешево, а точки остаются резкими на
 * любом зуме.
 *
 * Взамен полоса Галактики строится по-настоящему: звёзды генерируются
 * в галактических координатах (равномерно по долготе, с экспоненциальной
 * концентрацией к плоскости) и переводятся в экваториальные. Поэтому полоса
 * проходит там, где она реально проходит по небу, а созвездия из ярких
 * звёзд ложатся на настоящий Млечный Путь.
 */

import { useMemo } from 'react'
import * as THREE from 'three'
import { mulberry32, gaussian } from '../lib/rng'
import { blackbodyColor, galacticToEquatorialXyz } from '../lib/astro'
import { STARS } from '../data/stars'

interface StarfieldProps {
  /** радиус небесной сферы в единицах текущей сцены */
  radius: number
  starCount?: number
  /** базовый размер точки как доля радиуса */
  starSize?: number
  /** общая яркость */
  brightness?: number
  /** подсветить диффузное свечение полосы Галактики */
  showBand?: boolean
}

export function Starfield({
  radius,
  starCount = 4500,
  starSize = 0.0016,
  brightness = 1,
  showBand = true,
}: StarfieldProps) {
  const geom = useMemo(() => {
    const rnd = mulberry32(20260730)
    const pos = new Float32Array(starCount * 3)
    const col = new Float32Array(starCount * 3)
    const size = new Float32Array(starCount)

    for (let i = 0; i < starCount; i++) {
      // Галактическая долгота равномерна, широта — с концентрацией к плоскости.
      // Показатель 1,6 в степенном распределении даёт полосу шириной около
      // 15° по половинной интенсивности, что близко к наблюдаемому.
      const l = rnd() * 360
      const inDisk = rnd() < 0.62
      const b = inDisk
        ? gaussian(rnd) * 7.5
        : (rnd() * 2 - 1) * 90 * Math.pow(rnd(), 0.55)

      const [x, y, z] = galacticToEquatorialXyz(l, Math.max(-89.9, Math.min(89.9, b)), radius * 0.985)
      // экваториальные -> сцена (Z полюса мира вверх)
      pos[i * 3] = x
      pos[i * 3 + 1] = z
      pos[i * 3 + 2] = -y

      // Функция светимости: холодных карликов на порядки больше, чем горячих
      // гигантов, поэтому температура берётся со сильным смещением к низу
      const temp = 2700 + Math.pow(rnd(), 3.4) * 21000
      const [cr, cg, cb] = blackbodyColor(temp)
      // Видимая яркость: степенное распределение, ярких единицы
      const mag = Math.pow(rnd(), 3.1)
      const b0 = (0.22 + mag * 0.78) * brightness
      col[i * 3] = cr * b0
      col[i * 3 + 1] = cg * b0
      col[i * 3 + 2] = cb * b0
      size[i] = (0.35 + mag * 2.1) * starSize * radius
    }

    const g = new THREE.BufferGeometry()
    g.setAttribute('position', new THREE.BufferAttribute(pos, 3))
    g.setAttribute('color', new THREE.BufferAttribute(col, 3))
    g.setAttribute('aSize', new THREE.BufferAttribute(size, 1))
    return g
  }, [radius, starCount, starSize, brightness])

  /**
   * Яркие звёзды из каталога — на своих настоящих местах.
   * Сириус, Альтаир, Процион и Вега попадают туда, где они на небе,
   * поэтому небо перестаёт быть чистым шумом.
   */
  const namedGeom = useMemo(() => {
    const bright = STARS.filter((s) => s.vmag !== undefined && s.vmag < 4 && s.id !== 'sun')
    const pos = new Float32Array(bright.length * 3)
    const col = new Float32Array(bright.length * 3)
    const size = new Float32Array(bright.length)
    bright.forEach((s, i) => {
      const ra = s.ra * 15 * (Math.PI / 180)
      const dec = s.dec * (Math.PI / 180)
      const cd = Math.cos(dec)
      const r = radius * 0.984
      pos[i * 3] = r * cd * Math.cos(ra)
      pos[i * 3 + 1] = r * Math.sin(dec)
      pos[i * 3 + 2] = -r * cd * Math.sin(ra)
      // Поток по звёздной величине: каждая величина — множитель 2,512
      const flux = Math.pow(2.512, -(s.vmag ?? 3))
      const [cr, cg, cb] = blackbodyColor(
        s.spectral.startsWith('A') ? 9000 : s.spectral.startsWith('F') ? 6600 : s.spectral.startsWith('G') ? 5700 : 4200,
      )
      const b0 = Math.min(1.6, 0.55 + flux * 0.4) * brightness
      col[i * 3] = cr * b0
      col[i * 3 + 1] = cg * b0
      col[i * 3 + 2] = cb * b0
      size[i] = (1.6 + Math.min(3.5, flux * 1.1)) * starSize * radius
    })
    const g = new THREE.BufferGeometry()
    g.setAttribute('position', new THREE.BufferAttribute(pos, 3))
    g.setAttribute('color', new THREE.BufferAttribute(col, 3))
    g.setAttribute('aSize', new THREE.BufferAttribute(size, 1))
    return g
  }, [radius, starSize, brightness])

  const mat = useMemo(
    () =>
      new THREE.ShaderMaterial({
        transparent: true,
        depthWrite: false,
        blending: THREE.AdditiveBlending,
        vertexColors: true,
        vertexShader: /* glsl */ `
          attribute float aSize;
          varying vec3 vColor;
          void main() {
            vColor = color;
            vec4 mv = modelViewMatrix * vec4(position, 1.0);
            gl_Position = projectionMatrix * mv;
            // Размер задан в единицах сцены; переводим в пиксели через
            // расстояние до точки, но ограничиваем, чтобы звёзды не
            // раздувались в диски при близком near-плане
            gl_PointSize = clamp(aSize * 320.0 / -mv.z, 0.8, 6.0);
          }
        `,
        fragmentShader: /* glsl */ `
          varying vec3 vColor;
          void main() {
            vec2 c = gl_PointCoord - 0.5;
            float d = length(c);
            if (d > 0.5) discard;
            // Ядро плюс слабое гало: точка читается как источник света
            float a = exp(-d * d * 16.0) + pow(max(0.0, 1.0 - d * 2.0), 3.0) * 0.25;
            gl_FragColor = vec4(vColor, clamp(a, 0.0, 1.0));
          }
        `,
      }),
    [],
  )

  /**
   * Диффузное свечение полосы Галактики: неразрешённые звёзды дают
   * светящуюся ленту. Рисуем как аддитивную оболочку с шейдером,
   * где яркость зависит от галактической широты.
   */
  const bandMat = useMemo(
    () =>
      new THREE.ShaderMaterial({
        side: THREE.BackSide,
        transparent: true,
        depthWrite: false,
        blending: THREE.AdditiveBlending,
        vertexShader: /* glsl */ `
          varying vec3 vDir;
          void main() {
            vDir = normalize(position);
            gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
          }
        `,
        fragmentShader: /* glsl */ `
          varying vec3 vDir;
          uniform vec3 uGalZ;   // нормаль галактической плоскости, в координатах сцены
          uniform vec3 uGalX;   // направление на центр Галактики
          uniform float uAmp;

          float hash(vec3 p) {
            p = fract(p * 0.3183099 + 0.1);
            p *= 17.0;
            return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
          }
          float noise(vec3 x) {
            vec3 i = floor(x); vec3 f = fract(x);
            f = f * f * (3.0 - 2.0 * f);
            return mix(mix(mix(hash(i), hash(i + vec3(1,0,0)), f.x),
                           mix(hash(i + vec3(0,1,0)), hash(i + vec3(1,1,0)), f.x), f.y),
                       mix(mix(hash(i + vec3(0,0,1)), hash(i + vec3(1,0,1)), f.x),
                           mix(hash(i + vec3(0,1,1)), hash(i + vec3(1,1,1)), f.x), f.y), f.z);
          }

          void main() {
            float sinB = dot(vDir, uGalZ);
            // Экспоненциальный профиль по широте — как у поверхностной
            // яркости диска, наблюдаемого изнутри
            // Крутой спад по широте: реальная полоса узкая, при показателе 11
            // она расплывалась на полнеба и читалась как туман
            float band = exp(-abs(sinB) * 26.0);
            // К центру Галактики ярче, к антицентру тусклее
            float toCenter = dot(vDir, uGalX);
            band *= 0.45 + 0.55 * smoothstep(-1.0, 1.0, toCenter);

            // Клочковатость и тёмные пылевые прожилки
            // Высокие частоты: на низких получались мутные пятна размером
            // в десятки градусов, ничем не похожие на Млечный Путь
            float cl = noise(vDir * 95.0);
            float dust = smoothstep(0.40, 0.80, noise(vDir * 150.0 + 11.0));
            // Умеренный контраст: при 0,8/0,6 клочковатость превращала полосу
            // в набор ярких клякс с чёрными дырами
            band *= (0.75 + cl * 0.45) * (1.0 - dust * 0.35);

            vec3 col = mix(vec3(0.42, 0.46, 0.62), vec3(0.72, 0.66, 0.55), toCenter * 0.5 + 0.5);
            gl_FragColor = vec4(col * band * uAmp, band * uAmp);
          }
        `,
        uniforms: {
          uGalZ: { value: new THREE.Vector3() },
          uGalX: { value: new THREE.Vector3() },
          uAmp: { value: 0.13 * brightness },
        },
      }),
    [brightness],
  )

  // Оси галактической системы в координатах сцены, считаем один раз
  useMemo(() => {
    const z = galacticToEquatorialXyz(0, 90, 1)
    const x = galacticToEquatorialXyz(0, 0, 1)
    bandMat.uniforms.uGalZ.value.set(z[0], z[2], -z[1]).normalize()
    bandMat.uniforms.uGalX.value.set(x[0], x[2], -x[1]).normalize()
  }, [bandMat])

  return (
    <group>
      {showBand && (
        <mesh material={bandMat}>
          <sphereGeometry args={[radius * 0.99, 48, 32]} />
        </mesh>
      )}
      <points geometry={geom} material={mat} />
      <points geometry={namedGeom} material={mat} />
    </group>
  )
}

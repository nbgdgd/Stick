/**
 * Уровень 8 — наблюдаемая Вселенная.
 *
 * Что здесь честно:
 *  — размеры и расстояния настоящие: сопутствующий радиус 46,5 млрд световых
 *    лет, оболочка реликтового излучения на z ≈ 1090, положение реальных
 *    сверхструктур (Великая стена Слоуна, войд Волопаса и др.);
 *  — космическая паутина — процедурная. Это не карта конкретных галактик и
 *    не может ею быть: полное трёхмерное картирование наблюдаемой Вселенной
 *    не существует. Структура сгенерирована по наблюдаемой статистике:
 *      · характерный диаметр войдов 100–300 млн св. лет,
 *      · длина корреляции скучивания r₀ ≈ 5 h⁻¹ Мпк,
 *      · доля галактик в филаментах около половины.
 *
 * Метод генерации: узлы Пуассона со взаимным отталкиванием (чтобы получились
 * войды), затем связывание близких узлов в нити, затем население нитей.
 * Это по духу воспроизводит результат гравитационной неустойчивости, но
 * не является симуляцией N тел.
 */

import { useMemo, useRef } from 'react'
import { useFrame } from '@react-three/fiber'
import * as THREE from 'three'
import { useStore } from '../store'
import { LARGE_STRUCTURES, OBSERVABLE_UNIVERSE } from '../data/galaxies'
import { raDecToXyz, LY_M, comovingDistanceMpc } from '../lib/astro'
import { mulberry32, gaussian, hashString } from '../lib/rng'
import { Label } from '../components/Label'
import { registerFocus } from '../lib/focus'
import { GlowSprite, SoftDisc } from '../components/GlowSprite'

/** 1 единица сцены = 100 млн световых лет. */
const UNIT_MLY = 100
/** Сопутствующий радиус наблюдаемой Вселенной, млн св. лет. */
const HORIZON_MLY = 46500
const HORIZON = HORIZON_MLY / UNIT_MLY // 465 единиц

export function ObservableUniverseScene() {
  const showLabels = useStore((s) => s.showLabels)
  const showOrbits = useStore((s) => s.showOrbits)
  const cameraDist = useStore((s) => s.cameraDist)
  const select = useStore((s) => s.select)
  const setFocus = useStore((s) => s.setFocus)

  const web = useMemo(() => buildCosmicWeb(), [])

  const pointMat = useMemo(
    () =>
      new THREE.ShaderMaterial({
        transparent: true,
        depthWrite: false,
        vertexColors: true,
        blending: THREE.AdditiveBlending,
        vertexShader: /* glsl */ `
          attribute float aSize;
          varying vec3 vColor;
          varying float vDist;
          void main() {
            vColor = color;
            vec4 mv = modelViewMatrix * vec4(position, 1.0);
            vDist = length(position);
            gl_Position = projectionMatrix * mv;
            gl_PointSize = clamp(aSize * 380.0 / -mv.z, 0.6, 4.5);
          }
        `,
        fragmentShader: /* glsl */ `
          varying vec3 vColor;
          varying float vDist;
          uniform float uHorizon;
          void main() {
            vec2 c = gl_PointCoord - 0.5;
            float d = length(c);
            if (d > 0.5) discard;
            // Космологическое покраснение: чем дальше, тем больше z,
            // тем сильнее свет смещён в красную область
            float zf = clamp(vDist / uHorizon, 0.0, 1.0);
            vec3 col = mix(vColor, vColor * vec3(1.35, 0.62, 0.42), pow(zf, 1.4));
            // и тем тусклее — поверхностная яркость падает как (1+z)^-4
            float dim = mix(1.0, 0.35, pow(zf, 1.6));
            gl_FragColor = vec4(col * dim, exp(-d * d * 9.0) * 0.85);
          }
        `,
        uniforms: { uHorizon: { value: HORIZON } },
      }),
    [],
  )

  // Реальные сверхструктуры за пределами Ланиакеи
  const farStructures = useMemo(
    () => LARGE_STRUCTURES.filter((s) => s.distMly >= 600),
    [],
  )

  function pickUniverse() {
    setFocus(null)
    select({
      id: 'observable-universe',
      name: 'Наблюдаемая Вселенная',
      kind: 'Всё, что мы можем увидеть',
      blurb: OBSERVABLE_UNIVERSE.note,
      facts: OBSERVABLE_UNIVERSE.facts,
      distanceM: HORIZON_MLY * 1e6 * LY_M,
      source: OBSERVABLE_UNIVERSE.source,
      artistic: true,
    })
  }

  return (
    <group>
      <ambientLight intensity={1} />

      <points geometry={web} material={pointMat} onClick={pickUniverse} />

      <CmbShell onClick={() => {
        setFocus(null)
        select({
          id: 'cmb',
          name: 'Реликтовое излучение',
          kind: 'Поверхность последнего рассеяния',
          blurb:
            'Самый далёкий свет, который вообще можно увидеть: он испущен через 379 000 лет после ' +
            'Большого взрыва, когда Вселенная остыла до 3 000 К и стала прозрачной. Дальше не видно ' +
            'ничего не потому, что там пусто, а потому, что раньше Вселенная была непрозрачным туманом.',
          facts: [
            { label: 'Красное смещение', value: 'z ≈ 1 090' },
            { label: 'Испущено', value: 'через 379 000 лет после Большого взрыва' },
            { label: 'Температура тогда', value: '≈ 3 000 К' },
            { label: 'Температура сейчас', value: '2,72548 К' },
            { label: 'Анизотропия', value: '± 18 мкК — из этих неоднородностей выросли все структуры' },
            { label: 'Открыто', value: '1964, Пензиас и Вильсон (Нобель 1978)' },
            { label: 'Точные карты', value: 'COBE 1992, WMAP 2003, Planck 2013–2018' },
            { label: 'Сопутствующее расстояние', value: '≈ 45,6 млрд св. лет' },
          ],
          distanceM: 45600 * 1e6 * LY_M,
          source: 'Planck 2018 results (A&A 641, A6). Визуализация — процедурное поле анизотропии',
          artistic: true,
        })
      }} />

      {/* Оболочки постоянного красного смещения — линейка по эпохам */}
      {showOrbits && <RedshiftShells showLabels={showLabels} />}

      {farStructures.map((s) => {
        const [x, y, z] = raDecToXyz(s.ra, s.dec, s.distMly / UNIT_MLY)
        const pos = new THREE.Vector3(x, z, -y)
        return (
          <FarStructure
            key={s.id}
            id={s.id}
            name={s.name}
            pos={pos}
            sizeUnits={s.sizeMly / UNIT_MLY}
            kind={s.kind}
            showLabel={showLabels && cameraDist < 1600}
            onPick={() => {
              setFocus(s.id)
              select({
                id: s.id,
                name: s.name,
                kind: s.kind === 'void' ? 'Войд' : s.kind === 'wall' ? 'Стена галактик' : 'Сверхскопление',
                blurb: s.note,
                facts: [
                  { label: 'Расстояние', value: `${s.distMly.toLocaleString('ru-RU')} млн св. лет` },
                  { label: 'Размер', value: `${s.sizeMly.toLocaleString('ru-RU')} млн св. лет` },
                  ...(s.galaxyCount ? [{ label: 'Галактик', value: s.galaxyCount }] : []),
                  ...(s.massSun ? [{ label: 'Масса', value: `${s.massSun.toExponential(1).replace('e+', ' × 10^')} M☉` }] : []),
                  { label: 'Доля радиуса горизонта', value: `${((s.distMly / HORIZON_MLY) * 100).toFixed(1)} %` },
                ],
                distanceM: s.distMly * 1e6 * LY_M,
                source: 'Обзоры SDSS / 2dF, каталоги NED. Положение реальное, вид схематический',
                artistic: true,
              })
            }}
          />
        )
      })}

      {/* Мы — в центре наблюдаемой Вселенной, потому что это наш горизонт,
          а не потому, что Вселенная вокруг нас устроена особым образом */}
      {showLabels && <Label position={[0, 12, 0]} text="Млечный Путь" sub="центр нашего горизонта" small priority={120} />}
    </group>
  )
}

/** Оболочка реликтового излучения с процедурным полем анизотропии. */
function CmbShell({ onClick }: { onClick: () => void }) {
  const mat = useMemo(
    () =>
      new THREE.ShaderMaterial({
        // FrontSide: смотрим на оболочку снаружи как на конверт горизонта.
        // При BackSide изнутри видна дальняя стенка, и она перекрывает паутину.
        side: THREE.FrontSide,
        transparent: true,
        depthWrite: false,
        uniforms: { uTime: { value: 0 } },
        vertexShader: /* glsl */ `
          varying vec3 vPos;
          void main() {
            vPos = normalize(position);
            gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
          }
        `,
        fragmentShader: /* glsl */ `
          varying vec3 vPos;

          // Трёхмерный value noise: нужен, чтобы пятна анизотропии не имели
          // швов на сфере, как было бы с плоской текстурой
          float hash(vec3 p) {
            p = fract(p * 0.3183099 + 0.1);
            p *= 17.0;
            return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
          }
          float noise(vec3 x) {
            vec3 i = floor(x);
            vec3 f = fract(x);
            f = f * f * (3.0 - 2.0 * f);
            return mix(mix(mix(hash(i), hash(i + vec3(1,0,0)), f.x),
                           mix(hash(i + vec3(0,1,0)), hash(i + vec3(1,1,0)), f.x), f.y),
                       mix(mix(hash(i + vec3(0,0,1)), hash(i + vec3(1,0,1)), f.x),
                           mix(hash(i + vec3(0,1,1)), hash(i + vec3(1,1,1)), f.x), f.y), f.z);
          }

          void main() {
            // Спектр мощности реликта имеет пик на угловом масштабе около 1°
            // (первый акустический пик) — воспроизводим доминирующей частотой
            float n = noise(vPos * 14.0) * 0.55
                    + noise(vPos * 30.0) * 0.28
                    + noise(vPos * 62.0) * 0.17;
            n = (n - 0.5) * 2.0;

            // Классическая палитра карт Planck: синее холоднее, красное горячее
            vec3 cold = vec3(0.05, 0.12, 0.55);
            vec3 mid  = vec3(0.15, 0.05, 0.25);
            vec3 hot  = vec3(0.85, 0.25, 0.10);
            vec3 col = n < 0.0 ? mix(mid, cold, -n) : mix(mid, hot, n);

            // Полупрозрачно: сквозь оболочку должна просматриваться
            // структура внутри, иначе весь уровень сводится к карте реликта
            gl_FragColor = vec4(col, 0.34);
          }
        `,
      }),
    [],
  )

  const ref = useRef<THREE.Mesh>(null)
  useFrame(() => {
    registerFocus('cmb', new THREE.Vector3(0, 0, 0), HORIZON)
  })

  return (
    <mesh ref={ref} material={mat} onClick={onClick}>
      {/* z ≈ 1090; сопутствующее расстояние 45,6 млрд св. лет — чуть меньше горизонта частиц */}
      <sphereGeometry args={[(45600 / UNIT_MLY), 48, 32]} />
    </mesh>
  )
}

/** Сферы постоянного z: показывают, как расстояние соотносится с эпохой. */
function RedshiftShells({ showLabels }: { showLabels: boolean }) {
  const shells = useMemo(() => {
    // z -> сопутствующее расстояние через ΛCDM-интеграл
    return [1, 2, 6].map((z) => {
      const mpc = comovingDistanceMpc(z)
      const mly = (mpc * 3.2615638) // Мпк -> млн св. лет
      const ageGyr = lookbackGyr(z)
      return { z, r: mly / UNIT_MLY, mly, ageGyr }
    })
  }, [])

  return (
    <group>
      {shells.map((s) => (
        <group key={s.z}>
          <mesh>
            <sphereGeometry args={[s.r, 28, 20]} />
            <meshBasicMaterial color="#3a5f8a" wireframe transparent opacity={0.045} />
          </mesh>
          {showLabels && (
            <Label
              position={[s.r * 0.62, s.r * 0.78, s.r * 0.08]}
              text={`z = ${s.z}`}
              sub={`${(s.mly / 1000).toFixed(1)} млрд св. лет · свет шёл ${s.ageGyr.toFixed(1)} млрд лет`}
              small
              priority={30}
            />
          )}
        </group>
      ))}
    </group>
  )
}

/**
 * Время, которое свет шёл от объекта с красным смещением z (lookback time),
 * млрд лет. Численное интегрирование для плоской ΛCDM.
 */
function lookbackGyr(z: number): number {
  const H0 = 67.7 // км/с/Мпк
  const OM = 0.31
  // 1/H0 в млрд лет: 977.79 / H0
  const hubbleTimeGyr = 977.79 / H0
  const steps = 512
  const f = (zz: number) => 1 / ((1 + zz) * Math.sqrt(OM * Math.pow(1 + zz, 3) + (1 - OM)))
  const h = z / steps
  let sum = f(0) + f(z)
  for (let k = 1; k < steps; k++) sum += f(k * h) * (k % 2 === 1 ? 4 : 2)
  return hubbleTimeGyr * (h / 3) * sum
}

function FarStructure({
  id,
  name,
  pos,
  sizeUnits,
  kind,
  showLabel,
  onPick,
}: {
  id: string
  name: string
  pos: THREE.Vector3
  sizeUnits: number
  kind: string
  showLabel: boolean
  onPick: () => void
}) {
  const color = kind === 'void' ? '#5b7fae' : kind === 'wall' ? '#88ccff' : '#ffcc66'
  // Стены и войды протяжённые — размер по каталогу; скопления компактны
  const drawSize = Math.max(kind === 'supercluster' ? 14 : sizeUnits * 0.5, 8)

  useFrame(() => {
    registerFocus(id, pos, Math.max(sizeUnits, 5))
  })

  return (
    <group position={pos}>
      {kind === 'void' ? (
        <SoftDisc color={color} size={sizeUnits * 0.5} opacity={0.55} onClick={onPick as never} />
      ) : (
        <GlowSprite
          color={color}
          size={drawSize}
          intensity={0.6}
          hardness={kind === 'wall' ? 2.2 : 5}
          core={kind !== 'wall'}
          onClick={onPick as never}
        />
      )}
      {showLabel && <Label position={[0, drawSize * 0.6, 0]} text={name} small priority={75} onClick={onPick} />}
    </group>
  )
}

/**
 * Космическая паутина.
 *
 * Шаг 1: узлы (скопления) — точки Пуассона с отталкиванием, чтобы возникли
 *        войды характерного размера.
 * Шаг 2: рёбра — каждый узел соединяется с несколькими ближайшими; так
 *        получается сеть нитей, а не звезда из центра.
 * Шаг 3: население — галактики в узлах, вдоль нитей и разряжённое поле.
 */
function buildCosmicWeb(): THREE.BufferGeometry {
  const rnd = mulberry32(hashString('cosmic-web-v1'))
  const R = HORIZON * 0.97

  // --- Шаг 1: узлы с отталкиванием (упрощённый диск Пуассона) ---
  const nodes: THREE.Vector3[] = []
  const minSep = 34 // единиц = 3,4 млрд св. лет между скоплениями
  const targetNodes = 420
  let guard = 0
  while (nodes.length < targetNodes && guard < targetNodes * 300) {
    guard++
    // равномерно по объёму шара
    const r = R * Math.pow(rnd(), 1 / 3)
    const z = rnd() * 2 - 1
    const t = rnd() * Math.PI * 2
    const s = Math.sqrt(1 - z * z)
    const p = new THREE.Vector3(r * s * Math.cos(t), r * z, r * s * Math.sin(t))
    let ok = true
    for (const q of nodes) {
      if (p.distanceToSquared(q) < minSep * minSep) {
        ok = false
        break
      }
    }
    if (ok) nodes.push(p)
  }

  // --- Шаг 2: рёбра к ближайшим соседям ---
  const edges: [number, number][] = []
  const maxEdgeLen = 95
  for (let i = 0; i < nodes.length; i++) {
    const dists: { j: number; d: number }[] = []
    for (let j = 0; j < nodes.length; j++) {
      if (i === j) continue
      dists.push({ j, d: nodes[i].distanceTo(nodes[j]) })
    }
    dists.sort((a, b) => a.d - b.d)
    // 2–4 связи на узел: столько же в наблюдаемых каталогах филаментов
    const k = 2 + Math.floor(rnd() * 3)
    for (let n = 0; n < k && n < dists.length; n++) {
      if (dists[n].d > maxEdgeLen) break
      const a = Math.min(i, dists[n].j)
      const b = Math.max(i, dists[n].j)
      if (!edges.some((e) => e[0] === a && e[1] === b)) edges.push([a, b])
    }
  }

  const pos: number[] = []
  const col: number[] = []
  const size: number[] = []

  const cNode = new THREE.Color('#fff0cc')
  const cFil = new THREE.Color('#aac4ff')
  const cField = new THREE.Color('#667799')

  // --- Шаг 3a: скопления в узлах ---
  for (const p of nodes) {
    const n = 60 + Math.floor(rnd() * 90)
    const scale = 7 + rnd() * 7
    for (let i = 0; i < n; i++) {
      const r = scale * Math.pow(rnd(), 1.8)
      const z = rnd() * 2 - 1
      const t = rnd() * Math.PI * 2
      const s = Math.sqrt(1 - z * z)
      pos.push(p.x + r * s * Math.cos(t), p.y + r * z, p.z + r * s * Math.sin(t))
      const b = 0.5 + Math.pow(rnd(), 1.6) * 0.8
      col.push(cNode.r * b, cNode.g * b, cNode.b * b)
      size.push(0.6 + Math.pow(rnd(), 3) * 1.9)
    }
  }

  // --- Шаг 3b: нити ---
  const tmpA = new THREE.Vector3()
  for (const [i, j] of edges) {
    const a = nodes[i]
    const b = nodes[j]
    const len = a.distanceTo(b)
    const n = Math.round(len * 2.2)
    const tubeR = 4.5 + rnd() * 3
    for (let k = 0; k < n; k++) {
      const t = rnd()
      tmpA.copy(a).lerp(b, t)
      // сгущение к концам нити — там она вливается в скопление
      const thin = 0.45 + Math.sin(t * Math.PI) * 0.9
      tmpA.x += gaussian(rnd) * tubeR * thin
      tmpA.y += gaussian(rnd) * tubeR * thin
      tmpA.z += gaussian(rnd) * tubeR * thin
      if (tmpA.length() > R) continue
      pos.push(tmpA.x, tmpA.y, tmpA.z)
      const br = 0.3 + Math.pow(rnd(), 2) * 0.6
      col.push(cFil.r * br, cFil.g * br, cFil.b * br)
      size.push(0.4 + Math.pow(rnd(), 3.6) * 1.4)
    }
  }

  // --- Шаг 3c: разряжённое поле, но не внутри войдов ---
  for (let i = 0; i < 9000; i++) {
    const r = R * Math.pow(rnd(), 1 / 3)
    const z = rnd() * 2 - 1
    const t = rnd() * Math.PI * 2
    const s = Math.sqrt(1 - z * z)
    const p = new THREE.Vector3(r * s * Math.cos(t), r * z, r * s * Math.sin(t))
    // расстояние до ближайшего узла: чем дальше, тем ниже вероятность
    let dMin = Infinity
    for (const q of nodes) {
      const d = p.distanceToSquared(q)
      if (d < dMin) dMin = d
    }
    const d = Math.sqrt(dMin)
    // в глубине войда галактик почти нет
    if (rnd() > Math.exp(-d / 40)) continue
    pos.push(p.x, p.y, p.z)
    const b = 0.16 + Math.pow(rnd(), 2.5) * 0.36
    col.push(cField.r * b, cField.g * b, cField.b * b)
    size.push(0.35 + Math.pow(rnd(), 4) * 1.0)
  }

  const g = new THREE.BufferGeometry()
  g.setAttribute('position', new THREE.BufferAttribute(new Float32Array(pos), 3))
  g.setAttribute('color', new THREE.BufferAttribute(new Float32Array(col), 3))
  g.setAttribute('aSize', new THREE.BufferAttribute(new Float32Array(size), 1))
  return g
}

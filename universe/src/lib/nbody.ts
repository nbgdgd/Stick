/**
 * Гравитационная песочница: прямое интегрирование задачи N тел.
 *
 * Интегратор — «скачок» (velocity Verlet / leapfrog), а не Эйлер. Причина
 * принципиальная: leapfrog симплектичен, то есть сохраняет фазовый объём и
 * не накачивает энергию систематически. Эйлер на круговой орбите за сотню
 * витков раскручивает тело по спирали наружу, и «симуляция гравитации»
 * превращается в демонстрацию ошибки метода.
 *
 * Единицы внутри модуля: а.е., годы, массы Солнца. В такой системе
 * гравитационная постоянная равна 4π² — это следствие третьего закона
 * Кеплера (a³ = M·P², где a в а.е., P в годах, M в массах Солнца).
 */

export const G_AU = 4 * Math.PI * Math.PI

export interface Body {
  id: number
  /** масса, в массах Солнца */
  mass: number
  /** положение, а.е. */
  x: number
  y: number
  z: number
  /** скорость, а.е./год */
  vx: number
  vy: number
  vz: number
  /** ускорение с прошлого шага — нужно для leapfrog */
  ax: number
  ay: number
  az: number
  /** радиус для отрисовки и для проверки столкновений, а.е. */
  radius: number
  color: string
  label: string
  /** тело зафиксировано (например, центральная звезда) */
  fixed?: boolean
  /** след: последние положения */
  trail: number[]
}

/**
 * Смягчение потенциала (softening). Без него при близком прохождении
 * сила растёт как 1/r² до бесконечности, шаг интегрирования перестаёт
 * её разрешать, и тело получает нефизический пинок на пол-экрана.
 * Значение подобрано меньше типичного радиуса тела.
 */
const SOFTENING = 0.02

export const TRAIL_MAX = 220

export function makeBody(init: Partial<Body> & { mass: number; x: number; y: number }): Body {
  return {
    id: init.id ?? Math.floor(performance.now() * 1000) % 1e9,
    mass: init.mass,
    x: init.x,
    y: init.y,
    z: init.z ?? 0,
    vx: init.vx ?? 0,
    vy: init.vy ?? 0,
    vz: init.vz ?? 0,
    ax: 0,
    ay: 0,
    az: 0,
    radius: init.radius ?? Math.max(0.03, Math.pow(init.mass, 1 / 3) * 0.12),
    color: init.color ?? '#8ecbff',
    label: init.label ?? '',
    fixed: init.fixed,
    trail: [],
  }
}

/** Ускорения от взаимного притяжения. Пишет в поля ax/ay/az. */
export function computeAccelerations(bodies: Body[]): void {
  for (const b of bodies) {
    b.ax = 0
    b.ay = 0
    b.az = 0
  }
  // Пары считаем один раз и применяем к обоим телам: третий закон Ньютона
  // экономит половину работы и гарантирует сохранение импульса точно
  for (let i = 0; i < bodies.length; i++) {
    const a = bodies[i]
    for (let j = i + 1; j < bodies.length; j++) {
      const b = bodies[j]
      const dx = b.x - a.x
      const dy = b.y - a.y
      const dz = b.z - a.z
      const r2 = dx * dx + dy * dy + dz * dz + SOFTENING * SOFTENING
      const r = Math.sqrt(r2)
      const inv = 1 / (r2 * r) // 1/r³, чтобы умножать на компоненты вектора
      const fa = G_AU * b.mass * inv
      const fb = G_AU * a.mass * inv
      a.ax += dx * fa
      a.ay += dy * fa
      a.az += dz * fa
      b.ax -= dx * fb
      b.ay -= dy * fb
      b.az -= dz * fb
    }
  }
}

/**
 * Один шаг leapfrog (kick-drift-kick).
 * dt в годах. Устойчив при dt заметно меньше периода самой быстрой орбиты.
 */
export function step(bodies: Body[], dt: number): void {
  // полшага по скорости на старом ускорении
  for (const b of bodies) {
    if (b.fixed) continue
    b.vx += b.ax * dt * 0.5
    b.vy += b.ay * dt * 0.5
    b.vz += b.az * dt * 0.5
  }
  // полный шаг по координате
  for (const b of bodies) {
    if (b.fixed) continue
    b.x += b.vx * dt
    b.y += b.vy * dt
    b.z += b.vz * dt
  }
  // новые ускорения и вторая половина шага по скорости
  computeAccelerations(bodies)
  for (const b of bodies) {
    if (b.fixed) continue
    b.vx += b.ax * dt * 0.5
    b.vy += b.ay * dt * 0.5
    b.vz += b.az * dt * 0.5
  }
}

/** Слияние тел, оказавшихся внутри друг друга. Импульс сохраняется. */
export function mergeCollisions(bodies: Body[]): Body[] {
  const out = bodies.slice()
  for (let i = 0; i < out.length; i++) {
    for (let j = i + 1; j < out.length; j++) {
      const a = out[i]
      const b = out[j]
      const d = Math.hypot(b.x - a.x, b.y - a.y, b.z - a.z)
      if (d > (a.radius + b.radius) * 0.45) continue
      // Оставляем более массивное тело, добавляя к нему массу и импульс
      const [keep, gone] = a.mass >= b.mass ? [a, b] : [b, a]
      const m = keep.mass + gone.mass
      keep.vx = (keep.vx * keep.mass + gone.vx * gone.mass) / m
      keep.vy = (keep.vy * keep.mass + gone.vy * gone.mass) / m
      keep.vz = (keep.vz * keep.mass + gone.vz * gone.mass) / m
      if (!keep.fixed) {
        keep.x = (keep.x * keep.mass + gone.x * gone.mass) / m
        keep.y = (keep.y * keep.mass + gone.y * gone.mass) / m
        keep.z = (keep.z * keep.mass + gone.z * gone.mass) / m
      }
      keep.mass = m
      keep.radius = Math.max(keep.radius, Math.pow(m, 1 / 3) * 0.12)
      const idx = out.indexOf(gone)
      out.splice(idx, 1)
      return mergeCollisions(out)
    }
  }
  return out
}

/** Полная энергия системы — индикатор того, что интегратор не разъезжается. */
export function totalEnergy(bodies: Body[]): { kinetic: number; potential: number; total: number } {
  let kinetic = 0
  for (const b of bodies) {
    kinetic += 0.5 * b.mass * (b.vx * b.vx + b.vy * b.vy + b.vz * b.vz)
  }
  let potential = 0
  for (let i = 0; i < bodies.length; i++) {
    for (let j = i + 1; j < bodies.length; j++) {
      const a = bodies[i]
      const b = bodies[j]
      const r = Math.hypot(b.x - a.x, b.y - a.y, b.z - a.z)
      potential -= (G_AU * a.mass * b.mass) / Math.max(r, SOFTENING)
    }
  }
  return { kinetic, potential, total: kinetic + potential }
}

/** Скорость круговой орбиты вокруг центрального тела массы M на расстоянии r. */
export function circularSpeed(centralMass: number, r: number): number {
  return Math.sqrt((G_AU * centralMass) / r)
}

/** Записать текущее положение в след. */
export function pushTrail(b: Body): void {
  b.trail.push(b.x, b.y, b.z)
  if (b.trail.length > TRAIL_MAX * 3) b.trail.splice(0, b.trail.length - TRAIL_MAX * 3)
}

/** Готовые сценарии: показывают разные режимы задачи N тел. */
export interface Preset {
  id: string
  name: string
  description: string
  build: () => Body[]
}

export const PRESETS: Preset[] = [
  {
    id: 'solar',
    name: 'Звезда и планеты',
    description:
      'Звезда солнечной массы и четыре планеты на круговых орбитах. Устойчивая система — ' +
      'с ней удобно сравнивать, что делает с орбитами добавленная масса.',
    build: () => {
      const star = makeBody({
        mass: 1, x: 0, y: 0, radius: 0.14, color: '#ffd270', label: 'Звезда', fixed: true,
      })
      const planets = [0.5, 0.9, 1.4, 2.2].map((r, i) =>
        makeBody({
          id: 100 + i,
          mass: 3e-6 * (i + 1),
          x: r,
          y: 0,
          vy: circularSpeed(1, r),
          radius: 0.035 + i * 0.006,
          color: ['#8ecbff', '#7ee0b8', '#ffb26b', '#c39bff'][i],
          label: `Планета ${i + 1}`,
        }),
      )
      return [star, ...planets]
    },
  },
  {
    id: 'binary',
    name: 'Двойная звезда',
    description:
      'Две звезды равной массы вокруг общего центра масс и планета на внешней орбите. ' +
      'Планета обращается вокруг пары как вокруг одного тела — такие системы называют ' +
      'циркумбинарными, и они действительно существуют (Kepler-16b).',
    build: () => {
      const sep = 0.6
      const m = 0.5
      // Скорость для круговой орбиты каждой звезды вокруг центра масс
      const v = Math.sqrt((G_AU * m) / (2 * sep)) / 1
      return [
        makeBody({ id: 1, mass: m, x: -sep / 2, y: 0, vy: -v, radius: 0.1, color: '#ffd270', label: 'Звезда A' }),
        makeBody({ id: 2, mass: m, x: sep / 2, y: 0, vy: v, radius: 0.1, color: '#ffb26b', label: 'Звезда B' }),
        makeBody({
          id: 3, mass: 1e-5, x: 2.6, y: 0, vy: circularSpeed(1, 2.6),
          radius: 0.04, color: '#8ecbff', label: 'Планета',
        }),
      ]
    },
  },
  {
    id: 'trojan',
    name: 'Троянцы в точке L4',
    description:
      'Звезда, Юпитер и группа мелких тел около точки Лагранжа L4 — на 60° впереди планеты ' +
      'по орбите. Точки L4 и L5 устойчивы, поэтому реальные троянские астероиды живут там ' +
      'миллиарды лет. Видно, как они колеблются вокруг точки, но не уходят.',
    build: () => {
      const star = makeBody({ mass: 1, x: 0, y: 0, radius: 0.12, color: '#ffd270', label: 'Звезда', fixed: true })
      const a = 1.6
      const jup = makeBody({
        id: 2, mass: 0.0009543, x: a, y: 0, vy: circularSpeed(1.0009543, a),
        radius: 0.07, color: '#e0b070', label: 'Юпитер',
      })
      const troj: Body[] = []
      for (let i = 0; i < 7; i++) {
        // L4: тот же радиус, угол +60°, с небольшим разбросом
        const ang = Math.PI / 3 + (i - 3) * 0.06
        const rr = a * (1 + (i % 3 - 1) * 0.012)
        const v = circularSpeed(1, rr)
        troj.push(
          makeBody({
            id: 10 + i, mass: 1e-9, x: rr * Math.cos(ang), y: rr * Math.sin(ang),
            vx: -v * Math.sin(ang), vy: v * Math.cos(ang),
            radius: 0.018, color: '#a8b6c8', label: '',
          }),
        )
      }
      return [star, jup, ...troj]
    },
  },
  {
    id: 'chaos',
    name: 'Три тела',
    description:
      'Три звезды сравнимой массы. Задача трёх тел не имеет общего решения в замкнутой форме, ' +
      'и почти всегда кончается тем, что одно тело выбрасывается наружу, а два остаются ' +
      'двойной системой. Малейшее изменение начальных условий меняет исход.',
    build: () => {
      // Три звезды в вершинах равностороннего треугольника со стороной ~2,4 а.е.
      // Каждой даём скорость вращения вокруг общего центра масс, чуть меньше
      // равновесной: конфигурация Лагранжа с тремя равными массами формально
      // существует, но неустойчива, поэтому система разваливается — медленно
      // и по-разному при малейшем изменении начальных данных.
      const m = 0.5
      const R = 1.4 // расстояние каждой звезды от центра масс
      // равновесная скорость для трёх равных масс в вершинах треугольника
      const v = Math.sqrt((G_AU * m * 2) / (R * Math.sqrt(3))) * 0.94
      const out: Body[] = []
      for (let i = 0; i < 3; i++) {
        const a = (i / 3) * Math.PI * 2 + 0.4
        out.push(
          makeBody({
            id: i + 1,
            mass: m,
            x: R * Math.cos(a),
            y: R * Math.sin(a),
            vx: -v * Math.sin(a),
            vy: v * Math.cos(a),
            radius: 0.055,
            color: ['#ffd270', '#ff9a6b', '#8ecbff'][i],
            label: ['A', 'B', 'C'][i],
          }),
        )
      }
      // Лёгкая асимметрия — иначе идеальная симметрия сохранялась бы численно
      // неестественно долго и хаос не проявился бы
      out[0].vx *= 1.008
      return out
    },
  },
]

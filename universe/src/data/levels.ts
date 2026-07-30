/**
 * Уровни масштаба. Каждый уровень — отдельная сцена со своей внутренней
 * единицей длины: иначе на переходе от метров к гигапарсекам не хватит
 * точности 32-битного float, которым оперирует GPU.
 *
 * `metersPerUnit` связывает внутренние единицы сцены с физическими метрами —
 * через него индикатор масштаба считает реальный размер видимой области.
 */

export type LevelId =
  | 'earth'
  | 'earth-moon'
  | 'solar-system'
  | 'nearby-stars'
  | 'milky-way'
  | 'local-group'
  | 'laniakea'
  | 'observable-universe'

export interface Level {
  id: LevelId
  index: number
  title: string
  subtitle: string
  /** сколько метров в одной единице сцены */
  metersPerUnit: number
  /** начальное расстояние камеры от центра, в единицах сцены */
  initialCameraDist: number
  /**
   * Начальный угол камеры от вертикали, радианы. Меньше — вид сверху.
   * Для Солнечной системы нужен почти плановый вид: при пологом взгляде
   * орбиты проецируются в узкую полосу и планеты наползают друг на друга.
   */
  initialPhi?: number
  /** пределы зума камеры */
  minDist: number
  maxDist: number
  /** характерный размер объекта уровня, метры — для полосы «Powers of Ten» */
  characteristicSizeM: number
  /** какая симуляция доступна на уровне */
  simulation: string
  description: string
}

export const LEVELS: Level[] = [
  {
    id: 'earth',
    index: 0,
    title: 'Земля',
    subtitle: 'Поверхность, атмосфера, орбита МКС',
    metersPerUnit: 6.371e6, // 1 единица = радиус Земли
    initialCameraDist: 3.4,
    minDist: 1.03,
    maxDist: 12,
    characteristicSizeM: 1.2742e7,
    simulation: 'Смена дня и ночи и времён года по реальному наклону оси; орбита МКС',
    description:
      'Терминатор — линия между днём и ночью — построен по фактическому направлению на Солнце ' +
      'на выбранную дату, с наклоном оси 23,44°. Прокрутите время вперёд, чтобы увидеть смену сезонов.',
  },
  {
    id: 'earth-moon',
    index: 1,
    title: 'Земля — Луна',
    subtitle: 'Приливы, фазы, приливный захват',
    metersPerUnit: 6.371e6,
    initialCameraDist: 50,
    initialPhi: 0.75,
    minDist: 3,
    maxDist: 400,
    characteristicSizeM: 7.7e8,
    simulation: 'Фазы Луны, приливной эллипсоид, синхронное вращение',
    description:
      'Луна обращается за 27,32 суток и вращается с тем же периодом — это приливный захват. ' +
      'Показан приливной горб: он вытянут вдоль линии Земля — Луна, поэтому за сутки приливов два.',
  },
  {
    id: 'solar-system',
    index: 2,
    title: 'Солнечная система',
    subtitle: '8 планет, спутники, пояса астероидов и Койпера',
    metersPerUnit: 1.495978707e11, // 1 единица = 1 а.е.
    initialCameraDist: 30,
    initialPhi: 0.62,
    minDist: 0.02,
    maxDist: 3000,
    characteristicSizeM: 1.5e13,
    simulation: 'Орбитальная механика по кеплеровым элементам JPL с управлением скоростью времени',
    description:
      'Положения планет считаются из приближённых элементов орбит JPL — это те же данные, ' +
      'по которым строят реальные эфемериды. Переключатель масштаба показывает, насколько ' +
      'система на самом деле пуста.',
  },
  {
    id: 'nearby-stars',
    index: 3,
    title: 'Ближайшие звёзды',
    subtitle: '≈ 20 световых лет вокруг Солнца',
    metersPerUnit: 9.4607304725808e15, // 1 единица = 1 световой год
    initialCameraDist: 48,
    initialPhi: 1.1,
    minDist: 0.5,
    maxDist: 300,
    characteristicSizeM: 3.8e17,
    simulation: 'Реальные положения по параллаксам Gaia; цвет и размер — по спектральному классу',
    description:
      'Каждая звезда стоит там, где она есть: положение вычислено из прямого восхождения, ' +
      'склонения и измеренного параллакса. Цвета соответствуют температуре поверхности.',
  },
  {
    id: 'milky-way',
    index: 4,
    title: 'Млечный Путь',
    subtitle: 'Спиральные рукава, Sgr A*, шаровые скопления',
    metersPerUnit: 9.4607304725808e18, // 1 единица = 1000 световых лет
    initialCameraDist: 160,
    initialPhi: 0.95,
    minDist: 3,
    maxDist: 900,
    characteristicSizeM: 9.5e20,
    simulation: 'Процедурный диск: рукава — логарифмические спирали по VLBA-параллаксам Reid+2019',
    description:
      'Форму рукавов задают измеренные расстояния до областей звездообразования (метод VLBA-параллаксов). ' +
      'Отдельные звёзды на этом масштабе не разрешаются — это статистическое распределение.',
  },
  {
    id: 'local-group',
    index: 5,
    title: 'Местная группа',
    subtitle: 'Андромеда, Треугольник, карликовые спутники',
    metersPerUnit: 9.4607304725808e19, // 1 единица = 10 000 световых лет
    initialCameraDist: 560,
    minDist: 15,
    maxDist: 4000,
    characteristicSizeM: 9.5e22,
    simulation: 'Реальные трёхмерные положения по RA/Dec и измеренным расстояниям',
    description:
      'Около 80 галактик, связанных гравитацией. Млечный Путь и Андромеда — две доминирующие ' +
      'массы; остальное в основном карликовые спутники вокруг них.',
  },
  {
    id: 'laniakea',
    index: 6,
    title: 'Ланиакея',
    subtitle: 'Сверхскопление Девы и поле скоростей',
    metersPerUnit: 9.4607304725808e21, // 1 единица = 1 млн световых лет
    initialCameraDist: 620,
    minDist: 20,
    maxDist: 3000,
    characteristicSizeM: 4.9e24,
    simulation: 'Реальные скопления по каталогам + процедурные филаменты между ними',
    description:
      'Готовых трёхмерных моделей сверхскоплений не существует. Узлы здесь — настоящие скопления ' +
      'из каталогов, а нити между ними достроены процедурно: это реконструкция, а не снимок.',
  },
  {
    id: 'observable-universe',
    index: 7,
    title: 'Наблюдаемая Вселенная',
    subtitle: 'Космическая паутина и реликтовое излучение',
    metersPerUnit: 9.4607304725808e23, // 1 единица = 100 млн световых лет
    initialCameraDist: 1500,
    minDist: 30,
    maxDist: 2600,
    characteristicSizeM: 8.8e26,
    simulation: 'Процедурная космическая паутина: узлы, филаменты, войды; оболочка реликтового излучения',
    description:
      'Радиус 46,5 млрд световых лет — граница не Вселенной, а того, что мы успели увидеть. ' +
      'Структура сгенерирована процедурно по наблюдаемой статистике: характерный размер войдов, ' +
      'длина корреляции скучивания. Это художественная реконструкция, не карта конкретных галактик.',
  },
]

export const LEVEL_BY_ID = Object.fromEntries(LEVELS.map((l) => [l.id, l])) as Record<LevelId, Level>

/** Человекочитаемое расстояние: подбирает единицу по величине. */
export function formatDistance(meters: number): string {
  const abs = Math.abs(meters)
  if (abs < 1) return `${(meters * 100).toFixed(1)} см`
  if (abs < 1000) return `${meters.toFixed(0)} м`
  if (abs < 1e7) return `${(meters / 1000).toFixed(0)} км`
  if (abs < 0.01 * 1.496e11) return `${(meters / 1000).toLocaleString('ru-RU', { maximumFractionDigits: 0 })} км`
  if (abs < 0.1 * 9.4607e15) {
    const au = meters / 1.495978707e11
    return `${au.toLocaleString('ru-RU', { maximumFractionDigits: au < 10 ? 2 : 0 })} а.е.`
  }
  const ly = meters / 9.4607304725808e15
  if (ly < 1000) return `${ly.toLocaleString('ru-RU', { maximumFractionDigits: ly < 10 ? 2 : 1 })} св. лет`
  if (ly < 1e6) return `${(ly / 1000).toLocaleString('ru-RU', { maximumFractionDigits: 1 })} тыс. св. лет`
  if (ly < 1e9) return `${(ly / 1e6).toLocaleString('ru-RU', { maximumFractionDigits: 1 })} млн св. лет`
  return `${(ly / 1e9).toLocaleString('ru-RU', { maximumFractionDigits: 2 })} млрд св. лет`
}

/** Порядок величины в виде 10^n м — как в «Powers of Ten». */
export function powerOfTen(meters: number): string {
  if (meters <= 0) return '—'
  const e = Math.floor(Math.log10(meters))
  const sup = String(e)
    .split('')
    .map((c) => ({ '0': '⁰', '1': '¹', '2': '²', '3': '³', '4': '⁴', '5': '⁵', '6': '⁶', '7': '⁷', '8': '⁸', '9': '⁹', '-': '⁻' })[c] ?? c)
    .join('')
  return `10${sup} м`
}

/**
 * Реальные данные больших планет и карликовых планет.
 *
 * Кеплеровы элементы — приближённые элементы JPL Solar System Dynamics
 * ("Approximate Positions of the Major Planets", Standish), эпоха J2000,
 * с линейными скоростями на юлианское столетие. Пригодны для интервала
 * 1800–2050 гг. с точностью в единицы угловых минут — этого более чем
 * достаточно для визуализации.
 *
 * Физические параметры — NASA Planetary Fact Sheet (nssdc.gsfc.nasa.gov).
 */

import type { JplElements } from '../lib/astro'

export interface PlanetFact {
  label: string
  value: string
}

export interface Planet {
  id: string
  name: string
  nameEn: string
  kind: 'star' | 'planet' | 'dwarf'
  /** элементы орбиты (у Солнца отсутствуют) */
  elements?: JplElements
  /** средний радиус, км */
  radiusKm: number
  /** масса, кг */
  massKg: number
  /** период вращения вокруг оси, часов (отрицательный = обратное вращение) */
  rotationHours: number
  /** наклон оси к плоскости орбиты, град */
  axialTilt: number
  /** путь к текстуре в public/, если есть */
  texture?: string
  /** дополнительные карты */
  nightTexture?: string
  cloudTexture?: string
  ringTexture?: string
  ringInnerKm?: number
  ringOuterKm?: number
  /** базовый цвет — для процедурной отрисовки, если текстуры нет */
  color: string
  /** источник текстуры: реальная съёмка или художественная реконструкция */
  textureIsArtistic?: boolean
  /** средняя температура поверхности, °C */
  tempC?: number
  facts: PlanetFact[]
  /** короткое описание */
  blurb: string
  source: string
}

export const SUN: Planet = {
  id: 'sun',
  name: 'Солнце',
  nameEn: 'Sun',
  kind: 'star',
  radiusKm: 695700,
  massKg: 1.9885e30,
  rotationHours: 609.12, // 25.38 суток на экваторе
  axialTilt: 7.25,
  texture: '/tex/2k_sun.jpg',
  textureIsArtistic: true,
  color: '#ffcc55',
  tempC: 5505,
  blurb:
    'Звезда главной последовательности класса G2V. Содержит 99,86 % массы Солнечной системы. ' +
    'В ядре каждую секунду около 600 млн тонн водорода превращается в гелий.',
  facts: [
    { label: 'Спектральный класс', value: 'G2V' },
    { label: 'Радиус', value: '695 700 км (109 R⊕)' },
    { label: 'Масса', value: '1,989 × 10³⁰ кг (333 000 M⊕)' },
    { label: 'Температура поверхности', value: '5 772 К' },
    { label: 'Температура ядра', value: '≈ 15,7 млн К' },
    { label: 'Светимость', value: '3,828 × 10²⁶ Вт' },
    { label: 'Возраст', value: '≈ 4,6 млрд лет' },
    { label: 'Состав', value: 'H 73 %, He 25 %, остальное 2 %' },
    { label: 'Период вращения (экватор)', value: '25,4 суток' },
  ],
  source: 'NASA Sun Fact Sheet; текстура — solarsystemscope.com (CC BY 4.0), художественная',
}

export const PLANETS: Planet[] = [
  {
    id: 'mercury',
    name: 'Меркурий',
    nameEn: 'Mercury',
    kind: 'planet',
    elements: {
      a: 0.38709927, aDot: 0.00000037,
      e: 0.20563593, eDot: 0.00001906,
      i: 7.00497902, iDot: -0.00594749,
      L: 252.2503235, LDot: 149472.67411175,
      lp: 77.45779628, lpDot: 0.16047689,
      om: 48.33076593, omDot: -0.12534081,
    },
    radiusKm: 2439.7,
    massKg: 3.3011e23,
    rotationHours: 1407.6,
    axialTilt: 0.034,
    texture: '/tex/2k_mercury.jpg',
    color: '#8c8680',
    tempC: 167,
    blurb:
      'Самая маленькая и самая близкая к Солнцу планета. Из-за резонанса 3:2 между вращением ' +
      'и обращением солнечные сутки здесь длятся два меркурианских года.',
    facts: [
      { label: 'Расстояние от Солнца', value: '0,387 а.е. (57,9 млн км)' },
      { label: 'Год', value: '87,97 земных суток' },
      { label: 'Сутки (солнечные)', value: '175,94 земных суток' },
      { label: 'Эксцентриситет', value: '0,2056 — самый большой у планет' },
      { label: 'Температура', value: 'от −173 до +427 °C' },
      { label: 'Атмосфера', value: 'практически отсутствует (экзосфера)' },
      { label: 'Спутники', value: 'нет' },
      { label: 'Гравитация', value: '3,7 м/с² (0,38 g)' },
    ],
    source: 'NASA Mercury Fact Sheet; элементы орбиты — JPL SSD',
  },
  {
    id: 'venus',
    name: 'Венера',
    nameEn: 'Venus',
    kind: 'planet',
    elements: {
      a: 0.72333566, aDot: 0.0000039,
      e: 0.00677672, eDot: -0.00004107,
      i: 3.39467605, iDot: -0.0007889,
      L: 181.9790995, LDot: 58517.81538729,
      lp: 131.60246718, lpDot: 0.00268329,
      om: 76.67984255, omDot: -0.27769418,
    },
    radiusKm: 6051.8,
    massKg: 4.8675e24,
    rotationHours: -5832.5, // обратное вращение
    axialTilt: 177.36,
    texture: '/tex/2k_venus_surface.jpg',
    cloudTexture: '/tex/2k_venus_atmosphere.jpg',
    color: '#e6cfa0',
    tempC: 464,
    blurb:
      'Самая горячая планета: парниковый эффект в атмосфере из CO₂ держит поверхность при 464 °C, ' +
      'горячее Меркурия. Вращается в обратную сторону и медленнее, чем обращается вокруг Солнца.',
    facts: [
      { label: 'Расстояние от Солнца', value: '0,723 а.е. (108,2 млн км)' },
      { label: 'Год', value: '224,7 земных суток' },
      { label: 'Сутки (звёздные)', value: '243 земных суток, обратное вращение' },
      { label: 'Давление на поверхности', value: '92 атм' },
      { label: 'Температура', value: '464 °C, почти без суточных колебаний' },
      { label: 'Атмосфера', value: 'CO₂ 96,5 %, N₂ 3,5 %, облака H₂SO₄' },
      { label: 'Спутники', value: 'нет' },
      { label: 'Гравитация', value: '8,87 м/с² (0,90 g)' },
    ],
    source: 'NASA Venus Fact Sheet; элементы орбиты — JPL SSD',
  },
  {
    id: 'earth',
    name: 'Земля',
    nameEn: 'Earth',
    kind: 'planet',
    elements: {
      a: 1.00000261, aDot: 0.00000562,
      e: 0.01671123, eDot: -0.00004392,
      i: -0.00001531, iDot: -0.01294668,
      L: 100.46457166, LDot: 35999.37244981,
      lp: 102.93768193, lpDot: 0.32327364,
      om: 0.0, omDot: 0.0,
    },
    radiusKm: 6371.0,
    massKg: 5.97237e24,
    rotationHours: 23.9344696,
    axialTilt: 23.4393,
    texture: '/tex/2k_earth_daymap.jpg',
    nightTexture: '/tex/2k_earth_nightmap.jpg',
    cloudTexture: '/tex/2k_earth_clouds.jpg',
    color: '#2b6ca3',
    tempC: 15,
    blurb:
      'Единственное известное место с жидкой водой на поверхности и жизнью. Наклон оси 23,44° ' +
      'создаёт времена года; Луна стабилизирует этот наклон на протяжении миллиардов лет.',
    facts: [
      { label: 'Расстояние от Солнца', value: '1 а.е. (149,6 млн км)' },
      { label: 'Год', value: '365,256 суток' },
      { label: 'Сутки (звёздные)', value: '23 ч 56 мин 4 с' },
      { label: 'Наклон оси', value: '23,44°' },
      { label: 'Радиус', value: '6 371 км (средний)' },
      { label: 'Атмосфера', value: 'N₂ 78 %, O₂ 21 %, Ar 0,93 %' },
      { label: 'Спутники', value: '1 (Луна)' },
      { label: 'Возраст', value: '4,54 млрд лет' },
      { label: 'Доля океанов', value: '70,8 % поверхности' },
    ],
    source: 'NASA Earth Fact Sheet; текстуры — NASA Visible Earth / solarsystemscope.com (CC BY 4.0)',
  },
  {
    id: 'mars',
    name: 'Марс',
    nameEn: 'Mars',
    kind: 'planet',
    elements: {
      a: 1.52371034, aDot: 0.00001847,
      e: 0.0933941, eDot: 0.00007882,
      i: 1.84969142, iDot: -0.00813131,
      L: -4.55343205, LDot: 19140.30268499,
      lp: -23.94362959, lpDot: 0.44441088,
      om: 49.55953891, omDot: -0.29257343,
    },
    radiusKm: 3389.5,
    massKg: 6.4171e23,
    rotationHours: 24.6229,
    axialTilt: 25.19,
    texture: '/tex/2k_mars.jpg',
    color: '#b5502a',
    tempC: -65,
    blurb:
      'Здесь находится Олимп — самый высокий известный вулкан Солнечной системы (21,9 км) — ' +
      'и каньон Долины Маринер длиной 4 000 км. Цвет даёт оксид железа в реголите.',
    facts: [
      { label: 'Расстояние от Солнца', value: '1,524 а.е. (227,9 млн км)' },
      { label: 'Год', value: '686,98 земных суток' },
      { label: 'Сутки', value: '24 ч 37 мин' },
      { label: 'Наклон оси', value: '25,19°' },
      { label: 'Температура', value: 'от −143 до +35 °C' },
      { label: 'Атмосфера', value: 'CO₂ 95 %, давление 0,006 атм' },
      { label: 'Спутники', value: '2 (Фобос, Деймос)' },
      { label: 'Гравитация', value: '3,72 м/с² (0,38 g)' },
      { label: 'Высшая точка', value: 'Олимп, 21,9 км' },
    ],
    source: 'NASA Mars Fact Sheet; элементы орбиты — JPL SSD',
  },
  {
    id: 'jupiter',
    name: 'Юпитер',
    nameEn: 'Jupiter',
    kind: 'planet',
    elements: {
      a: 5.202887, aDot: -0.00011607,
      e: 0.04838624, eDot: -0.00013253,
      i: 1.30439695, iDot: -0.00183714,
      L: 34.39644051, LDot: 3034.74612775,
      lp: 14.72847983, lpDot: 0.21252668,
      om: 100.47390909, omDot: 0.20469106,
    },
    radiusKm: 69911,
    massKg: 1.8982e27,
    rotationHours: 9.9259,
    axialTilt: 3.13,
    texture: '/tex/2k_jupiter.jpg',
    color: '#c9a373',
    tempC: -110,
    blurb:
      'Масса больше, чем у всех остальных планет вместе взятых в 2,5 раза. Большое Красное Пятно — ' +
      'антициклон, наблюдаемый минимум с 1831 года, шире Земли.',
    facts: [
      { label: 'Расстояние от Солнца', value: '5,204 а.е. (778,5 млн км)' },
      { label: 'Год', value: '11,86 земных лет' },
      { label: 'Сутки', value: '9 ч 55,5 мин — быстрее всех планет' },
      { label: 'Масса', value: '318 M⊕' },
      { label: 'Радиус', value: '69 911 км (11 R⊕)' },
      { label: 'Атмосфера', value: 'H₂ 90 %, He 10 %' },
      { label: 'Спутники', value: '97 подтверждённых' },
      { label: 'Магнитное поле', value: 'в 20 000 раз мощнее земного' },
    ],
    source: 'NASA Jupiter Fact Sheet; элементы орбиты — JPL SSD',
  },
  {
    id: 'saturn',
    name: 'Сатурн',
    nameEn: 'Saturn',
    kind: 'planet',
    elements: {
      a: 9.53667594, aDot: -0.0012506,
      e: 0.05386179, eDot: -0.00050991,
      i: 2.48599187, iDot: 0.00193609,
      L: 49.95424423, LDot: 1222.49362201,
      lp: 92.59887831, lpDot: -0.41897216,
      om: 113.66242448, omDot: -0.28867794,
    },
    radiusKm: 58232,
    massKg: 5.6834e26,
    rotationHours: 10.656,
    axialTilt: 26.73,
    texture: '/tex/2k_saturn.jpg',
    ringTexture: '/tex/2k_saturn_ring_alpha.png',
    ringInnerKm: 74500,
    ringOuterKm: 140220,
    color: '#e0c79a',
    tempC: -140,
    blurb:
      'Средняя плотность 0,687 г/см³ — меньше плотности воды. Кольца состоят почти из чистого ' +
      'водяного льда, при диаметре 280 000 км их толщина местами всего десятки метров.',
    facts: [
      { label: 'Расстояние от Солнца', value: '9,537 а.е. (1,434 млрд км)' },
      { label: 'Год', value: '29,45 земных лет' },
      { label: 'Сутки', value: '10 ч 39 мин' },
      { label: 'Плотность', value: '0,687 г/см³ — легче воды' },
      { label: 'Кольца', value: 'от 74 500 до 140 220 км от центра' },
      { label: 'Состав колец', value: '≈ 95 % водяного льда' },
      { label: 'Спутники', value: '274 подтверждённых (2025)' },
      { label: 'Наклон оси', value: '26,73°' },
    ],
    source: 'NASA Saturn Fact Sheet; элементы орбиты — JPL SSD',
  },
  {
    id: 'uranus',
    name: 'Уран',
    nameEn: 'Uranus',
    kind: 'planet',
    elements: {
      a: 19.18916464, aDot: -0.00196176,
      e: 0.04725744, eDot: -0.00004397,
      i: 0.77263783, iDot: -0.00242939,
      L: 313.23810451, LDot: 428.48202785,
      lp: 170.9542763, lpDot: 0.40805281,
      om: 74.01692503, omDot: 0.04240589,
    },
    radiusKm: 25362,
    massKg: 8.681e25,
    rotationHours: -17.24,
    axialTilt: 97.77,
    texture: '/tex/2k_uranus.jpg',
    ringInnerKm: 41837,
    ringOuterKm: 51149,
    color: '#a8dadc',
    tempC: -195,
    blurb:
      'Ось наклонена на 97,8° — планета буквально катится по орбите на боку. Полюса получают ' +
      'больше тепла за год, чем экватор; сезон длится 21 земной год.',
    facts: [
      { label: 'Расстояние от Солнца', value: '19,19 а.е. (2,871 млрд км)' },
      { label: 'Год', value: '84,02 земных года' },
      { label: 'Сутки', value: '17 ч 14 мин, обратное вращение' },
      { label: 'Наклон оси', value: '97,77° — «лежит на боку»' },
      { label: 'Температура', value: '−224 °C — самая холодная атмосфера' },
      { label: 'Атмосфера', value: 'H₂ 83 %, He 15 %, CH₄ 2 %' },
      { label: 'Спутники', value: '28 подтверждённых' },
      { label: 'Открыт', value: '1781, Уильям Гершель' },
    ],
    source: 'NASA Uranus Fact Sheet; элементы орбиты — JPL SSD',
  },
  {
    id: 'neptune',
    name: 'Нептун',
    nameEn: 'Neptune',
    kind: 'planet',
    elements: {
      a: 30.06992276, aDot: 0.00026291,
      e: 0.00859048, eDot: 0.00005105,
      i: 1.77004347, iDot: 0.00035372,
      L: -55.12002969, LDot: 218.45945325,
      lp: 44.96476227, lpDot: -0.32241464,
      om: 131.78422574, omDot: -0.00508664,
    },
    radiusKm: 24622,
    massKg: 1.02413e26,
    rotationHours: 16.11,
    axialTilt: 28.32,
    texture: '/tex/2k_neptune.jpg',
    color: '#3f5ecc',
    tempC: -200,
    blurb:
      'Самые быстрые ветры Солнечной системы — до 2 100 км/ч. Единственная планета, ' +
      'найденная расчётом до наблюдения: Леверье предсказал её положение по возмущениям орбиты Урана.',
    facts: [
      { label: 'Расстояние от Солнца', value: '30,07 а.е. (4,495 млрд км)' },
      { label: 'Год', value: '164,8 земных лет' },
      { label: 'Сутки', value: '16 ч 6 мин' },
      { label: 'Ветры', value: 'до 2 100 км/ч — рекорд системы' },
      { label: 'Температура', value: '−214 °C' },
      { label: 'Спутники', value: '16 подтверждённых' },
      { label: 'Открыт', value: '1846, по расчётам Леверье и Адамса' },
      { label: 'Свет от Солнца', value: 'в 900 раз слабее, чем на Земле' },
    ],
    source: 'NASA Neptune Fact Sheet; элементы орбиты — JPL SSD',
  },
]

/** Карликовые планеты. Текстуры на solarsystemscope помечены как fictional — это художественная реконструкция. */
export const DWARF_PLANETS: Planet[] = [
  {
    id: 'ceres',
    name: 'Церера',
    nameEn: 'Ceres',
    kind: 'dwarf',
    elements: {
      a: 2.7658, aDot: 0, e: 0.0785, eDot: 0,
      i: 10.5934, iDot: 0,
      L: 95.989, LDot: 78.0936 * 100,
      lp: 73.6, lpDot: 0, om: 80.3055, omDot: 0,
    },
    radiusKm: 469.7,
    massKg: 9.3839e20,
    rotationHours: 9.074,
    axialTilt: 4,
    texture: '/tex/2k_ceres_fictional.jpg',
    textureIsArtistic: true,
    color: '#9a9086',
    blurb:
      'Крупнейший объект пояса астероидов и единственная карликовая планета внутри орбиты Нептуна. ' +
      'Содержит около 25 % массы всего пояса; под корой, вероятно, есть солёный водяной рассол.',
    facts: [
      { label: 'Расстояние от Солнца', value: '2,77 а.е.' },
      { label: 'Год', value: '4,60 земных года' },
      { label: 'Диаметр', value: '939 км' },
      { label: 'Доля массы пояса астероидов', value: '≈ 25 %' },
      { label: 'Открыта', value: '1801, Джузеппе Пиацци' },
      { label: 'Исследование', value: 'зонд Dawn, 2015–2018' },
    ],
    source: 'NASA JPL Small-Body Database; текстура художественная (solarsystemscope, CC BY 4.0)',
  },
  {
    id: 'pluto',
    name: 'Плутон',
    nameEn: 'Pluto',
    kind: 'dwarf',
    elements: {
      a: 39.48211675, aDot: -0.00031596,
      e: 0.2488273, eDot: 0.0000517,
      i: 17.14001206, iDot: 0.00004818,
      L: 238.92903833, LDot: 145.20780515,
      lp: 224.06891629, lpDot: -0.04062942,
      om: 110.30393684, omDot: -0.01183482,
    },
    radiusKm: 1188.3,
    massKg: 1.303e22,
    rotationHours: -153.29,
    axialTilt: 122.53,
    color: '#c7a58a',
    blurb:
      'В резонансе 3:2 с Нептуном, поэтому их орбиты никогда не сближаются. Ледники из азотного ' +
      'льда в области Спутник Планития медленно текут; New Horizons увидел там конвективные ячейки.',
    facts: [
      { label: 'Расстояние от Солнца', value: '39,48 а.е. (29,7–49,3 а.е.)' },
      { label: 'Год', value: '247,9 земных лет' },
      { label: 'Диаметр', value: '2 377 км' },
      { label: 'Температура', value: '−229 °C' },
      { label: 'Спутники', value: '5 (Харон, Никта, Гидра, Кербер, Стикс)' },
      { label: 'Резонанс с Нептуном', value: '3:2' },
      { label: 'Исследование', value: 'New Horizons, 14 июля 2015' },
    ],
    source: 'NASA New Horizons; элементы орбиты — JPL SSD. Поверхность — процедурный шейдер',
  },
  {
    id: 'haumea',
    name: 'Хаумеа',
    nameEn: 'Haumea',
    kind: 'dwarf',
    elements: {
      a: 43.116, aDot: 0, e: 0.19489, eDot: 0,
      i: 28.2137, iDot: 0,
      L: 240.0, LDot: 127.19,
      lp: 240.2, lpDot: 0, om: 122.167, omDot: 0,
    },
    radiusKm: 780,
    massKg: 4.006e21,
    rotationHours: 3.9155,
    axialTilt: 0,
    texture: '/tex/2k_haumea_fictional.jpg',
    textureIsArtistic: true,
    color: '#ddd6cc',
    blurb:
      'Вращается за 3,9 часа — быстрее любого крупного тела системы, из-за чего сплющена в эллипсоид ' +
      'размером примерно 2 100 × 1 680 × 1 074 км. Имеет собственное кольцо.',
    facts: [
      { label: 'Расстояние от Солнца', value: '43,1 а.е.' },
      { label: 'Год', value: '284 земных года' },
      { label: 'Период вращения', value: '3,9 часа' },
      { label: 'Форма', value: 'эллипсоид 2 100 × 1 680 × 1 074 км' },
      { label: 'Кольцо', value: 'обнаружено в 2017 году' },
      { label: 'Спутники', value: '2 (Хииака, Намака)' },
    ],
    source: 'JPL SSD; текстура художественная (solarsystemscope, CC BY 4.0)',
  },
  {
    id: 'makemake',
    name: 'Макемаке',
    nameEn: 'Makemake',
    kind: 'dwarf',
    elements: {
      a: 45.43, aDot: 0, e: 0.16126, eDot: 0,
      i: 28.9835, iDot: 0,
      L: 150.0, LDot: 117.6,
      lp: 294.8, lpDot: 0, om: 79.382, omDot: 0,
    },
    radiusKm: 715,
    massKg: 3.1e21,
    rotationHours: 22.83,
    axialTilt: 0,
    texture: '/tex/2k_makemake_fictional.jpg',
    textureIsArtistic: true,
    color: '#c2a191',
    blurb:
      'Второй по яркости объект пояса Койпера после Плутона. Поверхность покрыта метановым льдом ' +
      'крупными зёрнами — до сантиметра.',
    facts: [
      { label: 'Расстояние от Солнца', value: '45,4 а.е.' },
      { label: 'Год', value: '306 земных лет' },
      { label: 'Диаметр', value: '≈ 1 430 км' },
      { label: 'Поверхность', value: 'метановый и этановый лёд' },
      { label: 'Спутники', value: '1 (S/2015 (136472) 1)' },
      { label: 'Открыта', value: '2005' },
    ],
    source: 'JPL SSD; текстура художественная (solarsystemscope, CC BY 4.0)',
  },
  {
    id: 'eris',
    name: 'Эрида',
    nameEn: 'Eris',
    kind: 'dwarf',
    elements: {
      a: 67.864, aDot: 0, e: 0.43607, eDot: 0,
      i: 44.04, iDot: 0,
      L: 205.0, LDot: 64.4,
      lp: 187.1, lpDot: 0, om: 35.951, omDot: 0,
    },
    radiusKm: 1163,
    massKg: 1.6466e22,
    rotationHours: 378.6,
    axialTilt: 0,
    texture: '/tex/2k_eris_fictional.jpg',
    textureIsArtistic: true,
    color: '#d9d9d4',
    blurb:
      'Открытие Эриды в 2005 году привело к пересмотру определения планеты и переводу Плутона ' +
      'в карликовые планеты. Массивнее Плутона на 27 %, уходит на 97 а.е. от Солнца.',
    facts: [
      { label: 'Расстояние от Солнца', value: '67,9 а.е. (37,9–97,5 а.е.)' },
      { label: 'Год', value: '559 земных лет' },
      { label: 'Диаметр', value: '2 326 км' },
      { label: 'Масса', value: 'на 27 % больше Плутона' },
      { label: 'Наклонение орбиты', value: '44°' },
      { label: 'Спутники', value: '1 (Дисномия)' },
    ],
    source: 'JPL SSD; текстура художественная (solarsystemscope, CC BY 4.0)',
  },
]

export const ALL_BODIES = [SUN, ...PLANETS, ...DWARF_PLANETS]

/**
 * Ближайшие звёзды в пределах ≈ 20 световых лет.
 *
 * Координаты RA/Dec — эпоха J2000. Расстояния — по параллаксам Gaia DR3 и
 * Hipparcos через RECONS (Research Consortium on Nearby Stars). Спектральные
 * классы и массы — RECONS / SIMBAD.
 *
 * Список отсортирован по расстоянию. Компоненты кратных систем перечислены
 * отдельно, но помечены общим systemId — на сцене они группируются.
 */

import { hms, dms } from '../lib/astro'

export interface Star {
  id: string
  name: string
  nameEn: string
  /** общая система для кратных звёзд */
  systemId: string
  /** прямое восхождение, часы (J2000) */
  ra: number
  /** склонение, градусы (J2000) */
  dec: number
  /** расстояние, световые годы */
  distLy: number
  /** спектральный класс */
  spectral: string
  /** масса в массах Солнца */
  massSun?: number
  /** радиус в радиусах Солнца */
  radiusSun?: number
  /** светимость в светимостях Солнца (болометрическая) */
  lumSun?: number
  /** видимая звёздная величина */
  vmag?: number
  /** число подтверждённых экзопланет */
  planets?: number
  note?: string
}

export const STARS: Star[] = [
  {
    id: 'sun', name: 'Солнце', nameEn: 'Sun', systemId: 'sol',
    ra: 0, dec: 0, distLy: 0, spectral: 'G2V',
    massSun: 1, radiusSun: 1, lumSun: 1, vmag: -26.74, planets: 8,
    note: 'Наша звезда — точка отсчёта.',
  },
  {
    id: 'proxima', name: 'Проксима Центавра', nameEn: 'Proxima Centauri', systemId: 'alpha-cen',
    ra: hms(14, 29, 42.9), dec: dms(-62, 40, 46), distLy: 4.2465, spectral: 'M5.5Ve',
    massSun: 0.122, radiusSun: 0.154, lumSun: 0.00155, vmag: 11.13, planets: 3,
    note: 'Ближайшая к Солнцу звезда. Красный карлик со вспышками. Proxima b — планета земной массы в зоне обитаемости.',
  },
  {
    id: 'alpha-cen-a', name: 'Альфа Центавра A', nameEn: 'Alpha Centauri A', systemId: 'alpha-cen',
    ra: hms(14, 39, 36.5), dec: dms(-60, 50, 2), distLy: 4.365, spectral: 'G2V',
    massSun: 1.079, radiusSun: 1.2234, lumSun: 1.519, vmag: -0.01, planets: 0,
    note: 'Почти двойник Солнца. Вместе с B образует пару с периодом 79,9 года.',
  },
  {
    id: 'alpha-cen-b', name: 'Альфа Центавра B', nameEn: 'Alpha Centauri B', systemId: 'alpha-cen',
    ra: hms(14, 39, 35.1), dec: dms(-60, 50, 14), distLy: 4.365, spectral: 'K1V',
    massSun: 0.909, radiusSun: 0.8632, lumSun: 0.5, vmag: 1.33, planets: 0,
  },
  {
    id: 'barnard', name: 'Звезда Барнарда', nameEn: "Barnard's Star", systemId: 'barnard',
    ra: hms(17, 57, 48.5), dec: dms(4, 41, 36), distLy: 5.963, spectral: 'M4V',
    massSun: 0.144, radiusSun: 0.196, lumSun: 0.0035, vmag: 9.51, planets: 4,
    note: 'Самое большое собственное движение среди известных звёзд: 10,3″ в год — за 180 лет проходит по небу диаметр Луны.',
  },
  {
    id: 'luhman16a', name: 'Лухман 16 A', nameEn: 'Luhman 16 A', systemId: 'luhman16',
    ra: hms(10, 49, 18.9), dec: dms(-53, 19, 10), distLy: 6.5, spectral: 'L7.5',
    massSun: 0.034, vmag: 16.9,
    note: 'Ближайшая двойная система коричневых карликов, открыта только в 2013 году.',
  },
  {
    id: 'luhman16b', name: 'Лухман 16 B', nameEn: 'Luhman 16 B', systemId: 'luhman16',
    ra: hms(10, 49, 18.7), dec: dms(-53, 19, 10), distLy: 6.5, spectral: 'T0.5',
    massSun: 0.028,
  },
  {
    id: 'wise0855', name: 'WISE 0855−0714', nameEn: 'WISE 0855-0714', systemId: 'wise0855',
    ra: hms(8, 55, 10.8), dec: dms(-7, 14, 42), distLy: 7.43, spectral: 'Y4',
    massSun: 0.005,
    note: 'Самый холодный известный субзвёздный объект: от −48 до −13 °C. В атмосфере, вероятно, водяные облака.',
  },
  {
    id: 'wolf359', name: 'Вольф 359', nameEn: 'Wolf 359', systemId: 'wolf359',
    ra: hms(10, 56, 28.9), dec: dms(7, 0, 53), distLy: 7.856, spectral: 'M6V',
    massSun: 0.11, radiusSun: 0.16, lumSun: 0.0011, vmag: 13.54, planets: 2,
  },
  {
    id: 'lalande21185', name: 'Лаланд 21185', nameEn: 'Lalande 21185', systemId: 'lalande21185',
    ra: hms(11, 3, 20.2), dec: dms(35, 58, 12), distLy: 8.307, spectral: 'M2V',
    massSun: 0.39, radiusSun: 0.393, lumSun: 0.0195, vmag: 7.52, planets: 3,
  },
  {
    id: 'sirius-a', name: 'Сириус A', nameEn: 'Sirius A', systemId: 'sirius',
    ra: hms(6, 45, 8.9), dec: dms(-16, 42, 58), distLy: 8.601, spectral: 'A1V',
    massSun: 2.063, radiusSun: 1.711, lumSun: 25.4, vmag: -1.46,
    note: 'Самая яркая звезда ночного неба. Древние египтяне связывали её восход с разливом Нила.',
  },
  {
    id: 'sirius-b', name: 'Сириус B', nameEn: 'Sirius B', systemId: 'sirius',
    ra: hms(6, 45, 9.0), dec: dms(-16, 43, 6), distLy: 8.601, spectral: 'DA2',
    massSun: 1.018, radiusSun: 0.0084, lumSun: 0.056, vmag: 8.44,
    note: 'Белый карлик: масса как у Солнца при размере Земли. Чайная ложка вещества весила бы около 5 тонн.',
  },
  {
    id: 'luyten726-8a', name: 'Лейтен 726-8 A', nameEn: 'Luyten 726-8 A', systemId: 'luyten726',
    ra: hms(1, 39, 1.3), dec: dms(-17, 57, 1), distLy: 8.728, spectral: 'M5.5V',
    massSun: 0.102, vmag: 12.57,
  },
  {
    id: 'luyten726-8b', name: 'UV Кита', nameEn: 'UV Ceti', systemId: 'luyten726',
    ra: hms(1, 39, 1.5), dec: dms(-17, 57, 1), distLy: 8.728, spectral: 'M6V',
    massSun: 0.1, vmag: 12.52,
    note: 'Прототип вспыхивающих звёзд типа UV Ceti: яркость может вырасти вдвое за секунды.',
  },
  {
    id: 'ross154', name: 'Росс 154', nameEn: 'Ross 154', systemId: 'ross154',
    ra: hms(18, 49, 49.4), dec: dms(-23, 50, 10), distLy: 9.706, spectral: 'M3.5V',
    massSun: 0.17, lumSun: 0.0038, vmag: 10.44,
  },
  {
    id: 'ross248', name: 'Росс 248', nameEn: 'Ross 248', systemId: 'ross248',
    ra: hms(23, 41, 54.7), dec: dms(44, 10, 39), distLy: 10.295, spectral: 'M5V',
    massSun: 0.136, lumSun: 0.0018, vmag: 12.29,
    note: 'Примерно через 36 000 лет станет ближайшей к Солнцу звездой, обогнав Проксиму.',
  },
  {
    id: 'eps-eri', name: 'Эпсилон Эридана', nameEn: 'Epsilon Eridani', systemId: 'eps-eri',
    ra: hms(3, 32, 55.8), dec: dms(-9, 27, 30), distLy: 10.475, spectral: 'K2V',
    massSun: 0.82, radiusSun: 0.735, lumSun: 0.32, vmag: 3.73, planets: 1,
    note: 'Молодая звезда (≈ 800 млн лет) с двумя пылевыми поясами и планетой-гигантом. Классическая цель поиска внеземных цивилизаций (проект «Озма», 1960).',
  },
  {
    id: 'lacaille9352', name: 'Лакайль 9352', nameEn: 'Lacaille 9352', systemId: 'lacaille9352',
    ra: hms(23, 5, 52.0), dec: dms(-35, 51, 11), distLy: 10.724, spectral: 'M0.5V',
    massSun: 0.479, radiusSun: 0.474, lumSun: 0.033, vmag: 7.34, planets: 4,
  },
  {
    id: 'ross128', name: 'Росс 128', nameEn: 'Ross 128', systemId: 'ross128',
    ra: hms(11, 47, 44.4), dec: dms(0, 48, 16), distLy: 11.007, spectral: 'M4V',
    massSun: 0.168, lumSun: 0.00362, vmag: 11.13, planets: 1,
  },
  {
    id: 'ez-aqr', name: 'EZ Водолея', nameEn: 'EZ Aquarii', systemId: 'ez-aqr',
    ra: hms(22, 38, 33.4), dec: dms(-15, 18, 7), distLy: 11.1, spectral: 'M5V',
    massSun: 0.11, vmag: 12.7,
    note: 'Тройная система красных карликов.',
  },
  {
    id: '61cyg-a', name: '61 Лебедя A', nameEn: '61 Cygni A', systemId: '61cyg',
    ra: hms(21, 6, 53.9), dec: dms(38, 44, 58), distLy: 11.403, spectral: 'K5V',
    massSun: 0.7, radiusSun: 0.665, lumSun: 0.153, vmag: 5.21,
    note: 'Первая звезда, для которой измерили параллакс — Бессель, 1838. Так впервые узнали расстояние до звезды.',
  },
  {
    id: '61cyg-b', name: '61 Лебедя B', nameEn: '61 Cygni B', systemId: '61cyg',
    ra: hms(21, 6, 55.3), dec: dms(38, 44, 31), distLy: 11.403, spectral: 'K7V',
    massSun: 0.63, radiusSun: 0.595, lumSun: 0.085, vmag: 6.03,
  },
  {
    id: 'procyon-a', name: 'Процион A', nameEn: 'Procyon A', systemId: 'procyon',
    ra: hms(7, 39, 18.1), dec: dms(5, 13, 30), distLy: 11.46, spectral: 'F5IV-V',
    massSun: 1.499, radiusSun: 2.048, lumSun: 6.93, vmag: 0.34,
    note: 'Восьмая по яркости звезда неба. Вместе с Сириусом и Бетельгейзе образует Зимний треугольник.',
  },
  {
    id: 'procyon-b', name: 'Процион B', nameEn: 'Procyon B', systemId: 'procyon',
    ra: hms(7, 39, 18.5), dec: dms(5, 13, 25), distLy: 11.46, spectral: 'DQZ',
    massSun: 0.602, radiusSun: 0.01234, vmag: 10.7,
  },
  {
    id: 'struve2398a', name: 'Струве 2398 A', nameEn: 'Struve 2398 A', systemId: 'struve2398',
    ra: hms(18, 42, 46.7), dec: dms(59, 37, 49), distLy: 11.525, spectral: 'M3V',
    massSun: 0.334, vmag: 8.9,
  },
  {
    id: 'struve2398b', name: 'Струве 2398 B', nameEn: 'Struve 2398 B', systemId: 'struve2398',
    ra: hms(18, 42, 46.9), dec: dms(59, 37, 37), distLy: 11.525, spectral: 'M3.5V',
    massSun: 0.302, vmag: 9.7, planets: 1,
  },
  {
    id: 'groombridge34a', name: 'Гроомбридж 34 A', nameEn: 'Groombridge 34 A', systemId: 'groombridge34',
    ra: hms(0, 18, 22.9), dec: dms(44, 1, 23), distLy: 11.624, spectral: 'M1.5V',
    massSun: 0.4, vmag: 8.08, planets: 2,
  },
  {
    id: 'groombridge34b', name: 'Гроомбридж 34 B', nameEn: 'Groombridge 34 B', systemId: 'groombridge34',
    ra: hms(0, 18, 25.5), dec: dms(44, 1, 38), distLy: 11.624, spectral: 'M3.5V',
    massSun: 0.27, vmag: 11.06,
  },
  {
    id: 'eps-ind', name: 'Эпсилон Индейца', nameEn: 'Epsilon Indi', systemId: 'eps-ind',
    ra: hms(22, 3, 21.7), dec: dms(-56, 47, 10), distLy: 11.867, spectral: 'K5V',
    massSun: 0.754, radiusSun: 0.732, lumSun: 0.22, vmag: 4.83, planets: 1,
    note: 'Есть пара коричневых карликов-компаньонов классов T1 и T6.',
  },
  {
    id: 'dx-cnc', name: 'DX Рака', nameEn: 'DX Cancri', systemId: 'dx-cnc',
    ra: hms(8, 29, 49.4), dec: dms(26, 46, 37), distLy: 11.68, spectral: 'M6.5V',
    massSun: 0.09, lumSun: 0.00089, vmag: 14.78,
  },
  {
    id: 'tau-cet', name: 'Тау Кита', nameEn: 'Tau Ceti', systemId: 'tau-cet',
    ra: hms(1, 44, 4.1), dec: dms(-15, 56, 15), distLy: 11.912, spectral: 'G8.5V',
    massSun: 0.783, radiusSun: 0.793, lumSun: 0.488, vmag: 3.5, planets: 4,
    note: 'Ближайшая одиночная звезда, похожая на Солнце. Пылевой диск в 10 раз плотнее солнечного — вероятны частые бомбардировки планет.',
  },
  {
    id: 'gj1061', name: 'GJ 1061', nameEn: 'GJ 1061', systemId: 'gj1061',
    ra: hms(3, 35, 59.7), dec: dms(-44, 30, 45), distLy: 11.98, spectral: 'M5.5V',
    massSun: 0.12, vmag: 13.03, planets: 3,
  },
  {
    id: 'yz-cet', name: 'YZ Кита', nameEn: 'YZ Ceti', systemId: 'yz-cet',
    ra: hms(1, 12, 30.6), dec: dms(-16, 59, 57), distLy: 12.132, spectral: 'M4.5V',
    massSun: 0.13, vmag: 12.07, planets: 3,
  },
  {
    id: 'luyten', name: 'Звезда Лейтена', nameEn: "Luyten's Star", systemId: 'luyten',
    ra: hms(7, 27, 24.5), dec: dms(5, 13, 33), distLy: 12.348, spectral: 'M3.5V',
    massSun: 0.26, lumSun: 0.0146, vmag: 9.86, planets: 4,
    note: 'GJ 273 b — суперземля в зоне обитаемости. В 2017 году к ней отправили радиопослание «Sónar Calling GJ 273b».',
  },
  {
    id: 'teegarden', name: 'Звезда Тигардена', nameEn: "Teegarden's Star", systemId: 'teegarden',
    ra: hms(2, 53, 0.9), dec: dms(16, 52, 53), distLy: 12.497, spectral: 'M7V',
    massSun: 0.093, radiusSun: 0.107, lumSun: 0.00073, vmag: 15.08, planets: 3,
    note: 'Открыта только в 2003 году, несмотря на близость — слишком тусклая. Три планеты земной массы.',
  },
  {
    id: 'scr1845', name: 'SCR 1845−6357', nameEn: 'SCR 1845-6357', systemId: 'scr1845',
    ra: hms(18, 45, 5.3), dec: dms(-63, 57, 48), distLy: 12.571, spectral: 'M8.5V',
    massSun: 0.07, vmag: 17.4,
  },
  {
    id: 'kapteyn', name: 'Звезда Каптейна', nameEn: "Kapteyn's Star", systemId: 'kapteyn',
    ra: hms(5, 11, 40.6), dec: dms(-45, 1, 6), distLy: 12.832, spectral: 'sdM1',
    massSun: 0.281, radiusSun: 0.291, lumSun: 0.012, vmag: 8.85,
    note: 'Звезда гало возрастом ≈ 11 млрд лет, движется по орбите в обратную сторону относительно диска Галактики — вероятно, остаток разрушенной карликовой галактики.',
  },
  {
    id: 'lacaille8760', name: 'Лакайль 8760', nameEn: 'Lacaille 8760', systemId: 'lacaille8760',
    ra: hms(21, 17, 15.3), dec: dms(-38, 52, 3), distLy: 12.95, spectral: 'M0V',
    massSun: 0.6, radiusSun: 0.51, lumSun: 0.029, vmag: 6.67,
  },
  {
    id: 'kruger60a', name: 'Крюгер 60 A', nameEn: 'Kruger 60 A', systemId: 'kruger60',
    ra: hms(22, 27, 59.5), dec: dms(57, 41, 45), distLy: 13.079, spectral: 'M3V',
    massSun: 0.271, vmag: 9.79,
  },
  {
    id: 'kruger60b', name: 'Крюгер 60 B', nameEn: 'Kruger 60 B', systemId: 'kruger60',
    ra: hms(22, 27, 59.7), dec: dms(57, 41, 47), distLy: 13.079, spectral: 'M4V',
    massSun: 0.176, vmag: 11.4,
  },
  {
    id: 'den1048', name: 'DENIS 1048−3956', nameEn: 'DEN 1048-3956', systemId: 'den1048',
    ra: hms(10, 48, 14.7), dec: dms(-39, 56, 6), distLy: 13.167, spectral: 'M8.5V',
    massSun: 0.08, vmag: 17.4,
  },
  {
    id: 'ross614a', name: 'Росс 614 A', nameEn: 'Ross 614 A', systemId: 'ross614',
    ra: hms(6, 29, 23.4), dec: dms(-2, 48, 50), distLy: 13.349, spectral: 'M4.5V',
    massSun: 0.223, vmag: 11.15,
  },
  {
    id: 'ross614b', name: 'Росс 614 B', nameEn: 'Ross 614 B', systemId: 'ross614',
    ra: hms(6, 29, 23.5), dec: dms(-2, 48, 51), distLy: 13.349, spectral: 'M7V',
    massSun: 0.11, vmag: 14.23,
  },
  {
    id: 'wolf1061', name: 'Вольф 1061', nameEn: 'Wolf 1061', systemId: 'wolf1061',
    ra: hms(20, 52, 33.0), dec: dms(-16, 58, 29), distLy: 14.05, spectral: 'M3V',
    massSun: 0.294, radiusSun: 0.307, lumSun: 0.0097, vmag: 10.07, planets: 3,
  },
  {
    id: 'van-maanen', name: 'Звезда ван Маанена', nameEn: "Van Maanen's Star", systemId: 'van-maanen',
    ra: hms(0, 49, 9.9), dec: dms(5, 23, 19), distLy: 14.07, spectral: 'DZ8',
    massSun: 0.68, radiusSun: 0.011, vmag: 12.38,
    note: 'Ближайший одиночный белый карлик. В его спектре нашли металлы — значит, он поглощает остатки собственной планетной системы.',
  },
  {
    id: 'gl1', name: 'Глизе 1', nameEn: 'Gliese 1', systemId: 'gl1',
    ra: hms(0, 5, 24.4), dec: dms(-37, 21, 27), distLy: 14.172, spectral: 'M1.5V',
    massSun: 0.45, radiusSun: 0.48, lumSun: 0.0269, vmag: 8.55,
  },
  {
    id: 'tz-ari', name: 'TZ Овна', nameEn: 'TZ Arietis', systemId: 'tz-ari',
    ra: hms(2, 0, 12.9), dec: dms(13, 3, 8), distLy: 14.593, spectral: 'M4.5V',
    massSun: 0.145, vmag: 12.27, planets: 1,
  },
  {
    id: 'gl674', name: 'Глизе 674', nameEn: 'Gliese 674', systemId: 'gl674',
    ra: hms(17, 28, 39.9), dec: dms(-46, 53, 43), distLy: 14.809, spectral: 'M3V',
    massSun: 0.35, vmag: 9.36, planets: 1,
  },
  {
    id: 'gl687', name: 'Глизе 687', nameEn: 'Gliese 687', systemId: 'gl687',
    ra: hms(17, 36, 25.9), dec: dms(68, 20, 21), distLy: 14.837, spectral: 'M3V',
    massSun: 0.401, vmag: 9.17, planets: 2,
  },
  {
    id: 'lhs292', name: 'LHS 292', nameEn: 'LHS 292', systemId: 'lhs292',
    ra: hms(10, 48, 12.6), dec: dms(-11, 20, 14), distLy: 14.805, spectral: 'M6.5V',
    massSun: 0.13, vmag: 15.6,
  },
  {
    id: 'gj1245a', name: 'GJ 1245 A', nameEn: 'GJ 1245 A', systemId: 'gj1245',
    ra: hms(19, 53, 54.5), dec: dms(44, 24, 53), distLy: 14.812, spectral: 'M5.5V',
    massSun: 0.12, vmag: 13.46,
  },
  {
    id: 'gj1245b', name: 'GJ 1245 B', nameEn: 'GJ 1245 B', systemId: 'gj1245',
    ra: hms(19, 53, 55.2), dec: dms(44, 24, 56), distLy: 14.812, spectral: 'M6V',
    massSun: 0.1, vmag: 14.01,
  },
  {
    id: 'lp145-141', name: 'LP 145-141', nameEn: 'LP 145-141', systemId: 'lp145',
    ra: hms(11, 45, 42.9), dec: dms(-64, 50, 29), distLy: 15.06, spectral: 'DQ6',
    massSun: 0.61, vmag: 11.5,
    note: 'Четвёртый по близости белый карлик.',
  },
  {
    id: 'gl876', name: 'Глизе 876', nameEn: 'Gliese 876', systemId: 'gl876',
    ra: hms(22, 53, 16.7), dec: dms(-14, 15, 49), distLy: 15.238, spectral: 'M3.5V',
    massSun: 0.37, radiusSun: 0.3761, lumSun: 0.0122, vmag: 10.17, planets: 4,
    note: 'Первый красный карлик, у которого нашли планету (1998). Три внешние планеты в резонансе Лапласа 1:2:4.',
  },
  {
    id: 'lhs288', name: 'LHS 288', nameEn: 'LHS 288', systemId: 'lhs288',
    ra: hms(10, 44, 21.2), dec: dms(-61, 12, 36), distLy: 15.61, spectral: 'M5.5V',
    massSun: 0.11, vmag: 13.92,
  },
  {
    id: 'gl1002', name: 'Глизе 1002', nameEn: 'Gliese 1002', systemId: 'gl1002',
    ra: hms(0, 6, 43.8), dec: dms(-7, 32, 17), distLy: 15.81, spectral: 'M5.5V',
    massSun: 0.12, vmag: 13.76, planets: 2,
    note: 'Две планеты земной массы, обе в зоне обитаемости.',
  },
  {
    id: 'groombridge1618', name: 'Гроомбридж 1618', nameEn: 'Groombridge 1618', systemId: 'groombridge1618',
    ra: hms(10, 11, 22.1), dec: dms(49, 27, 15), distLy: 15.86, spectral: 'K7V',
    massSun: 0.67, radiusSun: 0.6, lumSun: 0.0729, vmag: 6.59,
  },
  {
    id: 'ad-leo', name: 'AD Льва', nameEn: 'AD Leonis', systemId: 'ad-leo',
    ra: hms(10, 19, 36.3), dec: dms(19, 52, 12), distLy: 16.19, spectral: 'M3V',
    massSun: 0.42, radiusSun: 0.39, lumSun: 0.024, vmag: 9.32,
    note: 'Активная вспыхивающая звезда: рентгеновские вспышки в тысячи раз мощнее солнечных.',
  },
  {
    id: '40-eri-a', name: '40 Эридана A (Кейд)', nameEn: '40 Eridani A', systemId: '40-eri',
    ra: hms(4, 15, 16.3), dec: dms(-7, 39, 10), distLy: 16.33, spectral: 'K0.5V',
    massSun: 0.78, radiusSun: 0.812, lumSun: 0.457, vmag: 4.43, planets: 1,
    note: 'В сериале «Звёздный путь» эта система названа родиной вулканцев.',
  },
  {
    id: '40-eri-b', name: '40 Эридана B', nameEn: '40 Eridani B', systemId: '40-eri',
    ra: hms(4, 15, 21.8), dec: dms(-7, 39, 29), distLy: 16.33, spectral: 'DA4',
    massSun: 0.573, radiusSun: 0.014, vmag: 9.52,
    note: 'Первый обнаруженный белый карлик (1783, Уильям Гершель).',
  },
  {
    id: 'ev-lac', name: 'EV Ящерицы', nameEn: 'EV Lacertae', systemId: 'ev-lac',
    ra: hms(22, 46, 49.7), dec: dms(44, 20, 2), distLy: 16.47, spectral: 'M4.5V',
    massSun: 0.32, vmag: 10.09,
    note: 'В 2008 году выдала вспышку в 1 000 раз мощнее самых сильных солнечных.',
  },
  {
    id: '70-oph-a', name: '70 Змееносца A', nameEn: '70 Ophiuchi A', systemId: '70-oph',
    ra: hms(18, 5, 27.3), dec: dms(2, 30, 0), distLy: 16.71, spectral: 'K0V',
    massSun: 0.92, radiusSun: 0.85, lumSun: 0.51, vmag: 4.24,
  },
  {
    id: '70-oph-b', name: '70 Змееносца B', nameEn: '70 Ophiuchi B', systemId: '70-oph',
    ra: hms(18, 5, 27.5), dec: dms(2, 29, 57), distLy: 16.71, spectral: 'K5V',
    massSun: 0.7, radiusSun: 0.81, lumSun: 0.09, vmag: 6.08,
  },
  {
    id: 'altair', name: 'Альтаир', nameEn: 'Altair', systemId: 'altair',
    ra: hms(19, 50, 47.0), dec: dms(8, 52, 6), distLy: 16.73, spectral: 'A7V',
    massSun: 1.86, radiusSun: 1.79, lumSun: 10.6, vmag: 0.76,
    note: 'Вращается за 9 часов — так быстро, что сплющена: экваториальный радиус на 22 % больше полярного. Вершина Летнего треугольника.',
  },
  {
    id: 'sigma-dra', name: 'Сигма Дракона', nameEn: 'Sigma Draconis', systemId: 'sigma-dra',
    ra: hms(19, 32, 21.6), dec: dms(69, 39, 40), distLy: 18.8, spectral: 'K0V',
    massSun: 0.85, radiusSun: 0.78, lumSun: 0.43, vmag: 4.67,
  },
  {
    id: 'eta-cas-a', name: 'Эта Кассиопеи A', nameEn: 'Eta Cassiopeiae A', systemId: 'eta-cas',
    ra: hms(0, 49, 6.3), dec: dms(57, 48, 55), distLy: 19.33, spectral: 'F9V',
    massSun: 0.97, radiusSun: 1.04, lumSun: 1.23, vmag: 3.44,
    note: 'Солнцеподобная звезда с оранжевым карликом-компаньоном; период пары 480 лет.',
  },
  {
    id: '36-oph-a', name: '36 Змееносца A', nameEn: '36 Ophiuchi A', systemId: '36-oph',
    ra: hms(17, 15, 20.9), dec: dms(-26, 36, 0), distLy: 19.5, spectral: 'K2V',
    massSun: 0.85, radiusSun: 0.69, lumSun: 0.34, vmag: 5.08,
  },
  {
    id: '82-eri', name: '82 Эридана', nameEn: '82 Eridani', systemId: '82-eri',
    ra: hms(3, 19, 55.7), dec: dms(-43, 4, 11), distLy: 19.71, spectral: 'G8V',
    massSun: 0.97, radiusSun: 0.9, lumSun: 0.74, vmag: 4.26, planets: 6,
    note: 'Старая звезда (≈ 6 млрд лет), бедная металлами, с шестью планетами.',
  },
  {
    id: 'delta-pav', name: 'Дельта Павлина', nameEn: 'Delta Pavonis', systemId: 'delta-pav',
    ra: hms(20, 8, 43.6), dec: dms(-66, 10, 55), distLy: 19.89, spectral: 'G8IV',
    massSun: 1.051, radiusSun: 1.22, lumSun: 1.22, vmag: 3.56,
    note: 'Один из ближайших субгигантов — Солнце в будущем будет выглядеть похоже. Часто попадает в списки перспективных целей SETI.',
  },
]

/** Звёзды, интересные сами по себе, но дальше 20 св. лет — показываем отдельным слоем. */
export const NOTABLE_FAR_STARS: Star[] = [
  {
    id: 'vega', name: 'Вега', nameEn: 'Vega', systemId: 'vega',
    ra: hms(18, 36, 56.3), dec: dms(38, 47, 1), distLy: 25.04, spectral: 'A0V',
    massSun: 2.135, radiusSun: 2.362, lumSun: 40.12, vmag: 0.03,
    note: 'Была нулевой точкой шкалы звёздных величин. Из-за прецессии станет Полярной звездой около 13 727 года.',
  },
  {
    id: 'fomalhaut', name: 'Фомальгаут', nameEn: 'Fomalhaut', systemId: 'fomalhaut',
    ra: hms(22, 57, 39.0), dec: dms(-29, 37, 20), distLy: 25.13, spectral: 'A4V',
    massSun: 1.92, radiusSun: 1.842, lumSun: 16.63, vmag: 1.16,
    note: 'Яркое пылевое кольцо с резким внутренним краем; JWST разглядел в нём три вложенных пояса.',
  },
  {
    id: 'trappist1', name: 'TRAPPIST-1', nameEn: 'TRAPPIST-1', systemId: 'trappist1',
    ra: hms(23, 6, 29.4), dec: dms(-5, 2, 29), distLy: 40.66, spectral: 'M8V',
    massSun: 0.0898, radiusSun: 0.1192, lumSun: 0.000553, vmag: 18.8, planets: 7,
    note: 'Семь планет земного размера, три-четыре из них в зоне обитаемости. Главная цель атмосферных наблюдений JWST.',
  },
  {
    id: 'betelgeuse', name: 'Бетельгейзе', nameEn: 'Betelgeuse', systemId: 'betelgeuse',
    ra: hms(5, 55, 10.3), dec: dms(7, 24, 25), distLy: 548, spectral: 'M1-2Ia-ab',
    massSun: 16.5, radiusSun: 764, lumSun: 126000, vmag: 0.5,
    note: 'Красный сверхгигант. Если поставить его на место Солнца, он поглотил бы орбиту Юпитера. Взорвётся сверхновой в ближайшие 100 тысяч лет.',
  },
]

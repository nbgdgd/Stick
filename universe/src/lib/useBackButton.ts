/**
 * Системная кнопка «назад» на Android.
 *
 * Без обработчика Capacitor вызывает `history.back()`. История у одностраничного
 * приложения пустая, поэтому нажатие либо не делало ничего, либо сворачивало
 * приложение — а пользователь ожидает, что «назад» закроет то, что открыто
 * поверх сцены.
 *
 * Порядок разбора слоёв — от самого верхнего к нижнему, как в любом
 * мобильном приложении: сначала закрывается модальное, потом панель,
 * и только когда закрывать нечего — выход.
 */

import { useEffect } from 'react'
import { useStore } from '../store'

export function useBackButton() {
  useEffect(() => {
    let remove: (() => void) | undefined
    let cancelled = false

    async function attach() {
      // Плагин есть только в нативной сборке; в браузере просто пропускаем
      let CapApp: typeof import('@capacitor/app').App
      try {
        CapApp = (await import('@capacitor/app')).App
      } catch {
        return
      }
      if (cancelled) return

      const handle = await CapApp.addListener('backButton', () => {
        const s = useStore.getState()
        if (s.sandboxOpen) {
          s.setSandboxOpen(false)
          return
        }
        if (s.searchOpen) {
          s.setSearchOpen(false)
          return
        }
        if (s.selected) {
          s.select(null)
          s.setFocus(null)
          return
        }
        // Дальше «назад» выходит из приложения. Соблазн навесить сюда
        // шаг по уровням масштаба был, но это ещё один неожиданный
        // отклик на системную кнопку: пользователь жмёт «назад», чтобы
        // выйти, а приложение вместо этого меняет масштаб.
        void CapApp.exitApp()
      })

      if (cancelled) {
        void handle.remove()
        return
      }
      remove = () => void handle.remove()
    }

    void attach()
    return () => {
      cancelled = true
      remove?.()
    }
  }, [])
}

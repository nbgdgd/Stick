import type { CapacitorConfig } from '@capacitor/cli'

const config: CapacitorConfig = {
  appId: 'org.universeexplorer.app',
  appName: 'Universe Explorer',
  webDir: 'dist',
  android: {
    // Тёмный фон вебвью: иначе при старте мигает белым, а приложение
    // полностью тёмное
    backgroundColor: '#04060c',
    // Аппаратное ускорение обязательно: без него WebGL уходит в SwiftShader
    webContentsDebuggingEnabled: true,
  },
  server: {
    androidScheme: 'https',
  },
}

export default config

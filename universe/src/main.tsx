import { createRoot } from 'react-dom/client'
import './styles.css'
import App from './App'

// StrictMode намеренно отключён: он монтирует компоненты дважды, а сцены
// R3F при этом дважды строят геометрию на десятки тысяч точек и дважды
// грузят текстуры. На телефоне это заметная задержка на старте.
createRoot(document.getElementById('root')!).render(<App />)

import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import './index.css'
import './lib/i18n/i18n'

/**
 * Az alkalmazás belépési pontja.
 * StrictMode: dupla render a fejlesztés alatt a side-effect bugok észlelésére.
 */
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>
)
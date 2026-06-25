import { NavLink, Navigate, Route, Routes } from 'react-router-dom'
import Dashboard from './pages/Dashboard.jsx'
import AiConfigPage from './pages/AiConfigPage.jsx'
import PredictionPage from './pages/PredictionPage.jsx'
import FinancePlanPage from './pages/FinancePlanPage.jsx'

const nav = [
  { to: '/dashboard', icon: '📊', label: '资产看板' },
  { to: '/prediction', icon: '🔮', label: 'AI 涨势预测' },
  { to: '/finance', icon: '💡', label: 'AI 理财规划' },
  { to: '/config', icon: '⚙️', label: 'AI 配置' },
]

export default function App() {
  return (
    <div className="app">
      <aside className="sidebar">
        <div className="brand">AI 理财助手</div>
        <div className="brand-sub">Tree-of-Thought Agent</div>
        {nav.map((n) => (
          <NavLink
            key={n.to}
            to={n.to}
            className={({ isActive }) => 'nav-item' + (isActive ? ' active' : '')}
          >
            <span className="nav-ico">{n.icon}</span>
            {n.label}
          </NavLink>
        ))}
      </aside>
      <main className="main">
        <Routes>
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/prediction" element={<PredictionPage />} />
          <Route path="/finance" element={<FinancePlanPage />} />
          <Route path="/config" element={<AiConfigPage />} />
        </Routes>
      </main>
    </div>
  )
}

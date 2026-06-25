import { useEffect, useState } from 'react'
import api from '../api.js'

const fmt = (v) => (v == null ? '-' : Number(v).toLocaleString('zh-CN', { maximumFractionDigits: 2 }))
const cls = (v) => (Number(v) > 0 ? 'pos' : Number(v) < 0 ? 'neg' : '')
const riskText = { LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险' }
const riskClass = { LOW: 'increase', MEDIUM: 'hold', HIGH: 'decrease' }
const prioClass = { HIGH: 'decrease', MEDIUM: 'hold', LOW: 'increase' }
const prioText = { HIGH: '高', MEDIUM: '中', LOW: '低' }

function ScoreRing({ score }) {
  const s = score == null ? 0 : Number(score)
  const color = s >= 75 ? '#2ec27e' : s >= 50 ? '#f5b300' : '#ff5c6c'
  return (
    <div
      style={{
        width: 110,
        height: 110,
        borderRadius: '50%',
        background: `conic-gradient(${color} ${s * 3.6}deg, #2c3242 0deg)`,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      <div
        style={{
          width: 86,
          height: 86,
          borderRadius: '50%',
          background: 'var(--card)',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <div style={{ fontSize: 26, fontWeight: 700 }}>{score == null ? '-' : s}</div>
        <div className="muted" style={{ fontSize: 11 }}>健康分</div>
      </div>
    </div>
  )
}

export default function AdvisorPage() {
  const [profile, setProfile] = useState({ cashBalance: '', monthlyIncome: '', monthlyExpense: '', riskPreference: 'BALANCED' })
  const [summary, setSummary] = useState(null)
  const [advice, setAdvice] = useState(null)
  const [intention, setIntention] = useState('')
  const [fetchNews, setFetchNews] = useState(true)
  const [loadingS, setLoadingS] = useState(false)
  const [loadingA, setLoadingA] = useState(false)
  const [err, setErr] = useState(null)
  const [msg, setMsg] = useState(null)

  const load = async () => {
    try {
      const p = await api.getProfile()
      if (p) {
        setProfile({
          cashBalance: p.cashBalance ?? '',
          monthlyIncome: p.monthlyIncome ?? '',
          monthlyExpense: p.monthlyExpense ?? '',
          riskPreference: p.riskPreference ?? 'BALANCED',
        })
      }
    } catch (e) {
      setErr(e.message)
    }
  }
  useEffect(() => {
    load()
  }, [])

  const saveProfile = async () => {
    setErr(null)
    setMsg(null)
    try {
      await api.saveProfile({
        cashBalance: profile.cashBalance === '' ? null : Number(profile.cashBalance),
        monthlyIncome: profile.monthlyIncome === '' ? null : Number(profile.monthlyIncome),
        monthlyExpense: profile.monthlyExpense === '' ? null : Number(profile.monthlyExpense),
        riskPreference: profile.riskPreference,
      })
      setMsg('财务档案已保存')
      load()
    } catch (e) {
      setErr(e.message)
    }
  }

  const runSummary = async () => {
    setErr(null)
    setLoadingS(true)
    setSummary(null)
    try {
      setSummary(await api.advisorSummary())
    } catch (e) {
      setErr(e.message)
    } finally {
      setLoadingS(false)
    }
  }

  const runAdvice = async () => {
    setErr(null)
    setLoadingA(true)
    setAdvice(null)
    try {
      setAdvice(await api.advisorAdvice(intention, fetchNews))
    } catch (e) {
      setErr(e.message)
    } finally {
      setLoadingA(false)
    }
  }

  return (
    <div>
      <h1 className="page-title">理财分析与建议</h1>
      <p className="page-desc">
        基于你的收入、现金余额与理财盈亏，AI 一键汇总当前理财状况；再结合财经新闻与你的理财意向，给出可视化的后续建议。
      </p>

      {err && <div className="alert error">{err}</div>}
      {msg && <div className="alert ok">{msg}</div>}

      {/* 财务档案 */}
      <div className="card">
        <h3>财务档案</h3>
        <div className="form-grid">
          <div>
            <label>现金余额(元)</label>
            <input type="number" value={profile.cashBalance} onChange={(e) => setProfile({ ...profile, cashBalance: e.target.value })} />
          </div>
          <div>
            <label>月收入(元)</label>
            <input type="number" value={profile.monthlyIncome} onChange={(e) => setProfile({ ...profile, monthlyIncome: e.target.value })} />
          </div>
          <div>
            <label>月支出(元)</label>
            <input type="number" value={profile.monthlyExpense} onChange={(e) => setProfile({ ...profile, monthlyExpense: e.target.value })} />
          </div>
          <div>
            <label>风险偏好</label>
            <select value={profile.riskPreference} onChange={(e) => setProfile({ ...profile, riskPreference: e.target.value })}>
              <option value="CONSERVATIVE">保守</option>
              <option value="BALANCED">均衡</option>
              <option value="AGGRESSIVE">激进</option>
            </select>
          </div>
        </div>
        <div className="mt">
          <button onClick={saveProfile}>保存档案</button>
        </div>
      </div>

      {/* 一键汇总 */}
      <div className="card">
        <div className="flex between center">
          <h3 style={{ margin: 0 }}>① 一键汇总分析</h3>
          <button onClick={runSummary} disabled={loadingS}>
            {loadingS ? <><span className="spinner" /> 分析中...</> : '🧮 一键汇总分析'}
          </button>
        </div>

        {summary && (
          <>
            <div className="stat-grid mt">
              <div className="stat">
                <div className="label">净资产</div>
                <div className="value">¥{fmt(summary.netWorth)}</div>
              </div>
              <div className="stat">
                <div className="label">现金余额</div>
                <div className="value">¥{fmt(summary.cashBalance)}</div>
              </div>
              <div className="stat">
                <div className="label">投资市值</div>
                <div className="value">¥{fmt(summary.investmentValue)}</div>
              </div>
              <div className="stat">
                <div className="label">理财总盈亏</div>
                <div className={'value ' + cls(summary.totalProfit)}>¥{fmt(summary.totalProfit)}</div>
              </div>
              <div className="stat">
                <div className="label">月结余 / 储蓄率</div>
                <div className="value">¥{fmt(summary.monthlySaving)} · {fmt(summary.savingRate)}%</div>
              </div>
            </div>

            <div className="flex gap mt" style={{ alignItems: 'center' }}>
              {summary.healthScore != null && <ScoreRing score={summary.healthScore} />}
              <div style={{ flex: 1 }}>
                {summary.narrative && (
                  <div className="alert info" style={{ whiteSpace: 'pre-wrap', margin: 0 }}>
                    {summary.narrative}
                  </div>
                )}
              </div>
            </div>

            {summary.highlights?.length > 0 && (
              <div className="mt">
                <div className="muted" style={{ marginBottom: 6 }}>关键发现</div>
                <ul style={{ margin: 0, paddingLeft: 18 }}>
                  {summary.highlights.map((h, i) => (
                    <li key={i} style={{ marginBottom: 4 }}>{h}</li>
                  ))}
                </ul>
              </div>
            )}
          </>
        )}
      </div>

      {/* 意向建议 */}
      <div className="card">
        <h3>② 输入意向，生成后续建议</h3>
        <div>
          <label>你的理财意向(例如：希望稳健增值、准备买房、能承受一定波动追求收益…)</label>
          <textarea
            rows={3}
            placeholder="请描述你的理财目标与偏好…"
            value={intention}
            onChange={(e) => setIntention(e.target.value)}
          />
        </div>
        <div className="flex gap mt center">
          <div style={{ width: 200 }}>
            <label>抓取财经新闻</label>
            <select value={fetchNews ? '1' : '0'} onChange={(e) => setFetchNews(e.target.value === '1')}>
              <option value="1">是(爬虫)</option>
              <option value="0">否</option>
            </select>
          </div>
          <div style={{ alignSelf: 'flex-end' }}>
            <button onClick={runAdvice} disabled={loadingA}>
              {loadingA ? <><span className="spinner" /> 生成中...</> : '🧭 生成建议'}
            </button>
          </div>
        </div>
        {loadingA && <div className="alert info mt">正在结合财务汇总与最新财经新闻分析，请稍候…</div>}
      </div>

      {advice && (
        <>
          <div className="card">
            <div className="flex gap" style={{ alignItems: 'center' }}>
              <ScoreRing score={advice.healthScore} />
              <div style={{ flex: 1 }}>
                <div className="flex gap center" style={{ marginBottom: 8 }}>
                  <strong>综合建议</strong>
                  {advice.riskLevel && (
                    <span className={'badge ' + (riskClass[advice.riskLevel] || 'hold')}>
                      {riskText[advice.riskLevel] || advice.riskLevel}
                    </span>
                  )}
                </div>
                {advice.summary && (
                  <div className="alert info" style={{ whiteSpace: 'pre-wrap', margin: 0 }}>
                    {advice.summary}
                  </div>
                )}
              </div>
            </div>
          </div>

          <div className="card-row">
            {advice.strengths?.length > 0 && (
              <div className="card">
                <h3>✅ 优势</h3>
                <ul style={{ margin: 0, paddingLeft: 18 }}>
                  {advice.strengths.map((s, i) => <li key={i} className="pos" style={{ marginBottom: 4 }}>{s}</li>)}
                </ul>
              </div>
            )}
            {advice.risks?.length > 0 && (
              <div className="card">
                <h3>⚠️ 风险点</h3>
                <ul style={{ margin: 0, paddingLeft: 18 }}>
                  {advice.risks.map((s, i) => <li key={i} className="neg" style={{ marginBottom: 4 }}>{s}</li>)}
                </ul>
              </div>
            )}
          </div>

          {advice.allocations?.length > 0 && (
            <div className="card">
              <h3>资产配置建议(当前 → 建议)</h3>
              {advice.allocations.map((a, i) => (
                <div key={i} style={{ marginBottom: 14 }}>
                  <div className="flex between">
                    <span>{a.category}</span>
                    <span className="muted">
                      {(a.current * 100).toFixed(0)}% → <strong style={{ color: 'var(--primary-2)' }}>{(a.suggested * 100).toFixed(0)}%</strong>
                    </span>
                  </div>
                  <div className="progress">
                    <div style={{ width: Math.min(a.suggested * 100, 100) + '%' }} />
                  </div>
                  {a.reason && <div className="muted" style={{ fontSize: 12, marginTop: 4 }}>{a.reason}</div>}
                </div>
              ))}
            </div>
          )}

          {advice.actions?.length > 0 && (
            <div className="card">
              <h3>行动建议</h3>
              <table>
                <thead>
                  <tr>
                    <th style={{ width: 70 }}>优先级</th>
                    <th>行动项</th>
                    <th>说明</th>
                  </tr>
                </thead>
                <tbody>
                  {advice.actions.map((a, i) => (
                    <tr key={i}>
                      <td>
                        <span className={'badge ' + (prioClass[a.priority] || 'hold')}>
                          {prioText[a.priority] || a.priority}
                        </span>
                      </td>
                      <td><strong>{a.title}</strong></td>
                      <td className="muted">{a.detail}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {advice.newsInsights?.length > 0 && (
            <div className="card">
              <h3>📰 新闻洞察</h3>
              <ul style={{ margin: 0, paddingLeft: 18 }}>
                {advice.newsInsights.map((n, i) => <li key={i} style={{ marginBottom: 4 }}>{n}</li>)}
              </ul>
            </div>
          )}

          {advice.newsUsed?.length > 0 && (
            <div className="card">
              <h3>参考的财经新闻</h3>
              <div className="muted" style={{ fontSize: 12, whiteSpace: 'pre-wrap' }}>
                {advice.newsUsed.join('\n')}
              </div>
            </div>
          )}
        </>
      )}
    </div>
  )
}

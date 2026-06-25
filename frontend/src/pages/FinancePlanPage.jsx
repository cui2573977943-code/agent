import { useEffect, useState } from 'react'
import api from '../api.js'
import ThoughtTree from '../components/ThoughtTree.jsx'

const styleText = {
  CONSERVATIVE: '保守型 🛡️',
  BALANCED: '均衡型 ⚖️',
  AGGRESSIVE: '激进型 🚀',
}
const actText = {
  BUY: '新买入',
  INCREASE: '增持',
  DECREASE: '减持',
  HOLD: '持有',
}
const fmt = (v) => (v == null ? '-' : Number(v).toLocaleString('zh-CN', { maximumFractionDigits: 2 }))

export default function FinancePlanPage() {
  const [salary, setSalary] = useState('20000')
  const [expense, setExpense] = useState('8000')
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState(null)
  const [err, setErr] = useState(null)
  const [salaryForm, setSalaryForm] = useState({ month: '', salary: '', expense: '', note: '' })
  const [salaries, setSalaries] = useState([])

  const load = async () => {
    try {
      setSalaries(await api.salaryHistory())
    } catch (e) {
      setErr(e.message)
    }
  }
  useEffect(() => {
    load()
  }, [])

  const run = async () => {
    setErr(null)
    setResult(null)
    setLoading(true)
    try {
      const r = await api.financePlan(Number(salary), Number(expense))
      setResult(r)
    } catch (e) {
      setErr(e.message)
    } finally {
      setLoading(false)
    }
  }

  const saveSalary = async () => {
    setErr(null)
    try {
      await api.saveSalary({
        month: salaryForm.month,
        salary: Number(salaryForm.salary),
        expense: Number(salaryForm.expense || 0),
        note: salaryForm.note,
      })
      setSalaryForm({ month: '', salary: '', expense: '', note: '' })
      load()
    } catch (e) {
      setErr(e.message)
    }
  }

  return (
    <div>
      <h1 className="page-title">AI 理财规划</h1>
      <p className="page-desc">
        输入工资，AI 用<strong>思维树</strong>规划：先思维链拆分维度 → 结合历史理财盈亏选择保守/均衡/激进 →
        多轮验证确定风格 → 给出买哪个基金、原有持仓增持/减持的落地方案。
      </p>

      {err && <div className="alert error">{err}</div>}

      <div className="card">
        <h3>生成理财规划</h3>
        <div className="form-grid">
          <div>
            <label>月工资(元)</label>
            <input type="number" value={salary} onChange={(e) => setSalary(e.target.value)} />
          </div>
          <div>
            <label>月支出(元)</label>
            <input type="number" value={expense} onChange={(e) => setExpense(e.target.value)} />
          </div>
          <div style={{ display: 'flex', alignItems: 'flex-end' }}>
            <button onClick={run} disabled={loading || !salary}>
              {loading ? <><span className="spinner" /> 规划中...</> : '💡 生成规划'}
            </button>
          </div>
        </div>
        {loading && <div className="alert info mt">正在执行多轮思维树规划，请耐心等待…</div>}
      </div>

      {result && (
        <>
          <div className="card">
            <div className="flex between center">
              <h3 style={{ margin: 0 }}>规划结论</h3>
              <span className={'badge ' + result.riskStyle?.toLowerCase()}>
                {styleText[result.riskStyle] || result.riskStyle}
              </span>
            </div>
            <div className="stat-grid mt">
              <div className="stat">
                <div className="label">建议应急金</div>
                <div className="value">¥{fmt(result.emergencyFund)}</div>
              </div>
              <div className="stat">
                <div className="label">建议可投资金额</div>
                <div className="value">¥{fmt(result.investableAmount)}</div>
              </div>
              <div className="stat">
                <div className="label">风格置信度</div>
                <div className="value">{result.confidence}%</div>
              </div>
              <div className="stat">
                <div className="label">验证轮数</div>
                <div className="value">{result.agreeCount}/{result.rounds}</div>
              </div>
            </div>
            {result.reasoning && (
              <div className="alert info mt" style={{ whiteSpace: 'pre-wrap' }}>
                {result.reasoning}
              </div>
            )}
          </div>

          {result.allocations?.length > 0 && (
            <div className="card">
              <h3>配置建议</h3>
              <table>
                <thead>
                  <tr>
                    <th>类别</th>
                    <th>标的</th>
                    <th>操作</th>
                    <th className="right">金额</th>
                    <th className="right">占比</th>
                    <th>理由</th>
                  </tr>
                </thead>
                <tbody>
                  {result.allocations.map((a, i) => (
                    <tr key={i}>
                      <td>{a.category}</td>
                      <td>{a.target}</td>
                      <td>
                        <span className={'badge ' + (a.action === 'DECREASE' ? 'decrease' : a.action === 'HOLD' ? 'hold' : 'increase')}>
                          {actText[a.action] || a.action}
                        </span>
                      </td>
                      <td className="right">¥{fmt(a.amount)}</td>
                      <td className="right">{a.ratio != null ? (a.ratio * 100).toFixed(0) + '%' : '-'}</td>
                      <td className="muted">{a.reason}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <div className="card">
            <h3>① 思维链拆分</h3>
            <div className="steps">
              {result.decomposition?.map((d, i) => (
                <span className="step-tag" key={i}>{i + 1}. {d}</span>
              ))}
            </div>
          </div>

          <div className="card">
            <h3>② 思维树展开</h3>
            <ThoughtTree tree={result.thoughtTree} />
          </div>

          <div className="card">
            <h3>③ 多轮风格裁决</h3>
            <div>
              {result.votes?.map((v) => (
                <span className="vote-chip" key={v.round}>
                  第{v.round}轮: <span>{styleText[v.verdict] || v.verdict}</span> ({(v.confidence * 100).toFixed(0)}%)
                </span>
              ))}
            </div>
          </div>
        </>
      )}

      <div className="card-row">
        <div className="card">
          <h3>录入历史工资(供 AI 参考)</h3>
          <div className="form-grid">
            <div>
              <label>月份(yyyy-MM)</label>
              <input placeholder="2026-01" value={salaryForm.month} onChange={(e) => setSalaryForm({ ...salaryForm, month: e.target.value })} />
            </div>
            <div>
              <label>工资</label>
              <input type="number" value={salaryForm.salary} onChange={(e) => setSalaryForm({ ...salaryForm, salary: e.target.value })} />
            </div>
            <div>
              <label>支出</label>
              <input type="number" value={salaryForm.expense} onChange={(e) => setSalaryForm({ ...salaryForm, expense: e.target.value })} />
            </div>
            <div>
              <label>备注</label>
              <input value={salaryForm.note} onChange={(e) => setSalaryForm({ ...salaryForm, note: e.target.value })} />
            </div>
          </div>
          <div className="mt">
            <button onClick={saveSalary} disabled={!salaryForm.month || !salaryForm.salary}>保存</button>
          </div>
        </div>

        <div className="card">
          <h3>工资历史</h3>
          <table>
            <thead>
              <tr>
                <th>月份</th>
                <th className="right">工资</th>
                <th className="right">支出</th>
                <th className="right">结余</th>
              </tr>
            </thead>
            <tbody>
              {salaries.length === 0 && (
                <tr><td colSpan="4" className="muted">暂无记录。</td></tr>
              )}
              {salaries.map((s) => (
                <tr key={s.id}>
                  <td>{s.month}</td>
                  <td className="right">¥{fmt(s.salary)}</td>
                  <td className="right">¥{fmt(s.expense)}</td>
                  <td className="right pos">¥{fmt(s.saving)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}

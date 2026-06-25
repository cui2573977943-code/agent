import { useEffect, useState } from 'react'
import api from '../api.js'
import ThoughtTree from '../components/ThoughtTree.jsx'

const concText = { UP: '看涨 📈', DOWN: '看跌 📉', UNCERTAIN: '不确定 🤔' }
const actText = { INCREASE: '建议增持', DECREASE: '建议减持', HOLD: '建议观望' }

export default function PredictionPage() {
  const [assets, setAssets] = useState([])
  const [assetCode, setAssetCode] = useState('')
  const [fetchNews, setFetchNews] = useState(true)
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState(null)
  const [history, setHistory] = useState([])
  const [err, setErr] = useState(null)
  const [ragCount, setRagCount] = useState(null)
  const [ragMsg, setRagMsg] = useState(null)
  const [openTrace, setOpenTrace] = useState({})

  const load = async () => {
    try {
      const a = await api.listAssets()
      setAssets(a)
      if (a.length && !assetCode) setAssetCode(a[0].code)
      setHistory(await api.predictionHistory())
      try {
        const docs = await api.ragDocuments()
        setRagCount(docs.length)
      } catch (_) {}
    } catch (e) {
      setErr(e.message)
    }
  }

  useEffect(() => {
    load()
  }, [])

  const rebuildRag = async () => {
    setErr(null)
    setRagMsg(null)
    try {
      const r = await api.ragRebuild()
      setRagCount(r.documentCount)
      setRagMsg(`知识库已重建，共索引 ${r.documentCount} 篇文档`)
    } catch (e) {
      setErr(e.message)
    }
  }

  const run = async () => {
    setErr(null)
    setResult(null)
    setLoading(true)
    try {
      const r = await api.predict(assetCode, fetchNews)
      setResult(r)
      setHistory(await api.predictionHistory())
    } catch (e) {
      setErr(e.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div>
      <h1 className="page-title">AI 涨势预测</h1>
      <p className="page-desc">
        <strong>RAG + 思维树(ToT) + ReAct + 多轮投票</strong>：先从知识库检索该标的的历史理财与核心波动资料 →
        思维链拆分维度 → 思维树多分支展开 → 多轮 ReAct(思考-行动-观察, 可调用 RAG/新闻工具)独立裁决 →
        多数派占比超过 60% 才确认结论。其中 ReAct <strong>优先使用模型原生 function-calling</strong>，
        若模型不支持则自动回退到手动推理模式。
      </p>

      {err && <div className="alert error">{err}</div>}
      {ragMsg && <div className="alert ok">{ragMsg}</div>}

      <div className="card">
        <div className="flex between center">
          <div>
            <h3 style={{ margin: 0 }}>📚 RAG 知识库</h3>
            <div className="muted" style={{ fontSize: 12, marginTop: 4 }}>
              来源：历史理财情况 + 核心股票/基金波动。已索引 {ragCount == null ? '-' : ragCount} 篇文档。
            </div>
          </div>
          <button className="ghost" onClick={rebuildRag}>重建知识库</button>
        </div>
      </div>

      <div className="card">
        <div className="form-grid">
          <div>
            <label>选择标的</label>
            <select value={assetCode} onChange={(e) => setAssetCode(e.target.value)}>
              {assets.map((a) => (
                <option key={a.id} value={a.code}>
                  {a.name}({a.code})
                </option>
              ))}
            </select>
          </div>
          <div>
            <label>抓取最新新闻</label>
            <select value={fetchNews ? '1' : '0'} onChange={(e) => setFetchNews(e.target.value === '1')}>
              <option value="1">是(爬虫)</option>
              <option value="0">否(仅历史)</option>
            </select>
          </div>
          <div style={{ display: 'flex', alignItems: 'flex-end' }}>
            <button onClick={run} disabled={loading || !assetCode}>
              {loading ? <><span className="spinner" /> 思维树推理中...</> : '🔮 开始预测'}
            </button>
          </div>
        </div>
        {loading && (
          <div className="alert info mt">
            正在执行多轮思维树推理，需要多次调用模型，请耐心等待…
          </div>
        )}
      </div>

      {result && (
        <>
          <div className="card">
            <div className="flex between center">
              <h3 style={{ margin: 0 }}>预测结论</h3>
              <div>
                <span className={'badge ' + result.conclusion?.toLowerCase()}>
                  {concText[result.conclusion] || result.conclusion}
                </span>{' '}
                <span className={'badge ' + result.action?.toLowerCase()}>
                  {actText[result.action] || result.action}
                </span>
              </div>
            </div>
            <div className="mt">
              <div className="flex between">
                <span className="muted">
                  置信度(多数派占比) · {result.confirmed ? '✅ 已确认(>60%)' : '⚠️ 未达确认阈值'}
                </span>
                <strong>{result.confidence}%</strong>
              </div>
              <div className="progress">
                <div style={{ width: Math.min(result.confidence, 100) + '%' }} />
              </div>
              <div className="muted mt">
                共 {result.rounds} 轮验证，其中 {result.agreeCount} 轮支持最终结论。
              </div>
            </div>
            {result.reasoning && (
              <div className="alert info mt" style={{ whiteSpace: 'pre-wrap' }}>
                {result.reasoning}
              </div>
            )}
          </div>

          {result.ragSnippets?.length > 0 && (
            <div className="card">
              <h3>① RAG 检索资料（预测前从知识库取回）</h3>
              {result.ragSnippets.map((s, i) => (
                <div key={i} style={{ marginBottom: 10 }}>
                  <div className="flex between">
                    <span className="badge fund">{s.type}</span>
                    <span className="muted" style={{ fontSize: 12 }}>相关度 {s.score}</span>
                  </div>
                  <div className="muted" style={{ fontSize: 13, marginTop: 4 }}>{s.content}</div>
                </div>
              ))}
            </div>
          )}

          <div className="card">
            <h3>② 思维链拆分</h3>
            <div className="steps">
              {result.decomposition?.map((d, i) => (
                <span className="step-tag" key={i}>
                  {i + 1}. {d}
                </span>
              ))}
            </div>
          </div>

          <div className="card">
            <h3>③ 思维树展开</h3>
            <ThoughtTree tree={result.thoughtTree} />
          </div>

          <div className="card">
            <h3>④ 多轮 ReAct 裁决与投票</h3>
            <div style={{ marginBottom: 12 }}>
              {result.votes?.map((v) => (
                <span className="vote-chip" key={v.round}>
                  第{v.round}轮:{' '}
                  <span className={v.verdict === 'UP' ? 'pos' : 'neg'}>{v.verdict}</span>{' '}
                  ({(v.confidence * 100).toFixed(0)}%)
                </span>
              ))}
            </div>
            {result.votes?.map((v) => (
              <div key={v.round} style={{ borderTop: '1px solid var(--border)', paddingTop: 10, marginTop: 10 }}>
                <div className="flex between center">
                  <div>
                    <strong>第 {v.round} 轮</strong>{' '}
                    <span className={'badge ' + (v.verdict === 'UP' ? 'up' : 'down')}>{v.verdict}</span>
                    {v.reactSteps?.length > 0 && (
                      <span className="muted" style={{ fontSize: 12, marginLeft: 8 }}>
                        {v.reactSteps.length} 步 ReAct 推理
                      </span>
                    )}
                  </div>
                  {v.reactSteps?.length > 0 && (
                    <button className="ghost sm" onClick={() => setOpenTrace({ ...openTrace, [v.round]: !openTrace[v.round] })}>
                      {openTrace[v.round] ? '收起轨迹' : '展开 ReAct 轨迹'}
                    </button>
                  )}
                </div>
                {v.reason && <div className="muted" style={{ fontSize: 12, marginTop: 4 }}>{v.reason}</div>}
                {openTrace[v.round] && v.reactSteps?.map((s) => (
                  <div className="tree-node" key={s.step}>
                    <div className="tree-title">步骤 {s.step}{s.action ? ` · ${s.action}` : ''}</div>
                    {s.thought && <div className="tree-meta">💭 思考：{s.thought}</div>}
                    {s.actionInput && <div className="tree-meta">⚡ 行动入参：{s.actionInput}</div>}
                    {s.observation && <div className="tree-meta">👁 观察：{s.observation}</div>}
                  </div>
                ))}
              </div>
            ))}
          </div>
        </>
      )}

      <div className="card">
        <h3>历史预测记录</h3>
        <table>
          <thead>
            <tr>
              <th>时间</th>
              <th>代码</th>
              <th>结论</th>
              <th>建议</th>
              <th className="right">置信度</th>
              <th className="right">轮数</th>
            </tr>
          </thead>
          <tbody>
            {history.length === 0 && (
              <tr><td colSpan="6" className="muted">暂无记录。</td></tr>
            )}
            {history.map((p) => (
              <tr key={p.id}>
                <td>{p.createdAt?.replace('T', ' ').slice(0, 19)}</td>
                <td>{p.assetCode}</td>
                <td>
                  <span className={'badge ' + p.conclusion?.toLowerCase()}>
                    {concText[p.conclusion] || p.conclusion}
                  </span>
                </td>
                <td>
                  <span className={'badge ' + p.action?.toLowerCase()}>
                    {actText[p.action] || p.action}
                  </span>
                </td>
                <td className="right">{Number(p.confidence)}%</td>
                <td className="right">{p.agreeCount}/{p.rounds}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

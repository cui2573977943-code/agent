import { useEffect, useState } from 'react'
import api from '../api.js'

export default function AiConfigPage() {
  const [form, setForm] = useState({
    baseUrl: '',
    apiKey: '',
    model: 'gpt-4o-mini',
    temperature: 0.3,
    maxTokens: 2048,
  })
  const [saved, setSaved] = useState(null)
  const [msg, setMsg] = useState(null)
  const [err, setErr] = useState(null)
  const [testing, setTesting] = useState(false)

  const load = async () => {
    try {
      const cfg = await api.getAiConfig()
      if (cfg) {
        setSaved(cfg)
        setForm((f) => ({
          ...f,
          baseUrl: cfg.baseUrl || '',
          model: cfg.model || 'gpt-4o-mini',
          temperature: cfg.temperature ?? 0.3,
          maxTokens: cfg.maxTokens ?? 2048,
        }))
      }
    } catch (e) {
      setErr(e.message)
    }
  }

  useEffect(() => {
    load()
  }, [])

  const onChange = (k) => (e) => setForm({ ...form, [k]: e.target.value })

  const save = async () => {
    setErr(null)
    setMsg(null)
    try {
      await api.saveAiConfig({
        baseUrl: form.baseUrl,
        apiKey: form.apiKey,
        model: form.model,
        temperature: Number(form.temperature),
        maxTokens: Number(form.maxTokens),
      })
      setMsg('配置已保存')
      setForm({ ...form, apiKey: '' })
      load()
    } catch (e) {
      setErr(e.message)
    }
  }

  const test = async () => {
    setErr(null)
    setMsg(null)
    setTesting(true)
    try {
      const r = await api.testAiConfig()
      setMsg(r.connected ? '连接成功 ✅' : '连接失败')
    } catch (e) {
      setErr('连接失败: ' + e.message)
    } finally {
      setTesting(false)
    }
  }

  return (
    <div>
      <h1 className="page-title">AI 模型配置</h1>
      <p className="page-desc">
        填写任意 OpenAI 兼容接口的 URL 与 Key 即可接入模型(如 OpenAI、DeepSeek、Moonshot、通义千问、本地 Ollama 等)。
      </p>

      {err && <div className="alert error">{err}</div>}
      {msg && <div className="alert ok">{msg}</div>}

      <div className="card">
        <h3>接入参数</h3>
        <div className="form-grid">
          <div style={{ gridColumn: '1 / -1' }}>
            <label>API URL(base_url)</label>
            <input
              placeholder="https://api.openai.com/v1"
              value={form.baseUrl}
              onChange={onChange('baseUrl')}
            />
          </div>
          <div style={{ gridColumn: '1 / -1' }}>
            <label>API Key</label>
            <input
              type="password"
              placeholder={saved ? `已保存(${saved.apiKey})，留空则不修改` : 'sk-...'}
              value={form.apiKey}
              onChange={onChange('apiKey')}
            />
          </div>
          <div>
            <label>模型名称</label>
            <input value={form.model} onChange={onChange('model')} />
          </div>
          <div>
            <label>温度(0~2)</label>
            <input type="number" step="0.1" value={form.temperature} onChange={onChange('temperature')} />
          </div>
          <div>
            <label>最大 Tokens</label>
            <input type="number" value={form.maxTokens} onChange={onChange('maxTokens')} />
          </div>
        </div>
        <div className="flex gap mt">
          <button onClick={save}>保存配置</button>
          <button className="ghost" onClick={test} disabled={testing || !saved}>
            {testing ? <span className="spinner" /> : '测试连接'}
          </button>
        </div>
      </div>

      {saved && (
        <div className="card">
          <h3>当前配置</h3>
          <table>
            <tbody>
              <tr>
                <td className="muted">URL</td>
                <td>{saved.baseUrl}</td>
              </tr>
              <tr>
                <td className="muted">Key</td>
                <td>{saved.apiKey}</td>
              </tr>
              <tr>
                <td className="muted">模型</td>
                <td>{saved.model}</td>
              </tr>
              <tr>
                <td className="muted">更新时间</td>
                <td>{saved.updatedAt?.replace('T', ' ').slice(0, 19)}</td>
              </tr>
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

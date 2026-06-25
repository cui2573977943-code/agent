import { useEffect, useState } from 'react'
import api from '../api.js'

const fmt = (v) => (v == null ? '-' : Number(v).toLocaleString('zh-CN', { maximumFractionDigits: 2 }))
const cls = (v) => (Number(v) > 0 ? 'pos' : Number(v) < 0 ? 'neg' : '')
const sign = (v) => (Number(v) > 0 ? '+' : '')

export default function Dashboard() {
  const [assets, setAssets] = useState([])
  const [holdings, setHoldings] = useState([])
  const [summary, setSummary] = useState(null)
  const [txns, setTxns] = useState([])
  const [err, setErr] = useState(null)

  const [assetForm, setAssetForm] = useState({ code: '', name: '', type: 'FUND', market: 'SH', latestPrice: '' })
  const [tradeForm, setTradeForm] = useState({ assetId: '', type: 'BUY', shares: '', price: '', fee: '0', tradeDate: '' })

  const loadAll = async () => {
    try {
      const [a, h, s, t] = await Promise.all([
        api.listAssets(),
        api.listHoldings(),
        api.portfolioSummary(),
        api.allTransactions(),
      ])
      setAssets(a)
      setHoldings(h)
      setSummary(s)
      setTxns(t)
      if (a.length && !tradeForm.assetId) setTradeForm((f) => ({ ...f, assetId: a[0].id }))
    } catch (e) {
      setErr(e.message)
    }
  }

  useEffect(() => {
    loadAll()
  }, [])

  const addAsset = async () => {
    setErr(null)
    try {
      await api.createAsset({
        ...assetForm,
        latestPrice: assetForm.latestPrice ? Number(assetForm.latestPrice) : null,
      })
      setAssetForm({ code: '', name: '', type: 'FUND', market: 'SH', latestPrice: '' })
      loadAll()
    } catch (e) {
      setErr(e.message)
    }
  }

  const addTrade = async () => {
    setErr(null)
    try {
      await api.trade({
        assetId: Number(tradeForm.assetId),
        type: tradeForm.type,
        shares: Number(tradeForm.shares),
        price: Number(tradeForm.price),
        fee: Number(tradeForm.fee || 0),
        tradeDate: tradeForm.tradeDate || null,
      })
      setTradeForm({ ...tradeForm, shares: '', price: '', fee: '0' })
      loadAll()
    } catch (e) {
      setErr(e.message)
    }
  }

  const delAsset = async (id) => {
    if (!confirm('确认删除该资产及其持仓/交易记录?')) return
    try {
      await api.deleteAsset(id)
      loadAll()
    } catch (e) {
      setErr(e.message)
    }
  }

  return (
    <div>
      <h1 className="page-title">资产看板</h1>
      <p className="page-desc">管理你选择的股票/基金，记录买卖，实时查看历史详细盈亏。</p>

      {err && <div className="alert error">{err}</div>}

      {summary && (
        <div className="stat-grid">
          <div className="stat">
            <div className="label">持仓成本</div>
            <div className="value">¥{fmt(summary.totalInvested)}</div>
          </div>
          <div className="stat">
            <div className="label">当前市值</div>
            <div className="value">¥{fmt(summary.totalMarketValue)}</div>
          </div>
          <div className="stat">
            <div className="label">浮动盈亏</div>
            <div className={'value ' + cls(summary.totalUnrealizedProfit)}>
              {sign(summary.totalUnrealizedProfit)}¥{fmt(summary.totalUnrealizedProfit)}
            </div>
          </div>
          <div className="stat">
            <div className="label">已实现盈亏</div>
            <div className={'value ' + cls(summary.totalRealizedProfit)}>
              {sign(summary.totalRealizedProfit)}¥{fmt(summary.totalRealizedProfit)}
            </div>
          </div>
          <div className="stat">
            <div className="label">总盈亏 ({fmt(summary.totalProfitRate)}%)</div>
            <div className={'value ' + cls(summary.totalProfit)}>
              {sign(summary.totalProfit)}¥{fmt(summary.totalProfit)}
            </div>
          </div>
        </div>
      )}

      <div className="card">
        <h3>我的持仓</h3>
        <table>
          <thead>
            <tr>
              <th>名称/代码</th>
              <th>类型</th>
              <th className="right">份额</th>
              <th className="right">成本价</th>
              <th className="right">现价</th>
              <th className="right">市值</th>
              <th className="right">浮动盈亏</th>
              <th className="right">已实现</th>
              <th className="right">总盈亏率</th>
            </tr>
          </thead>
          <tbody>
            {holdings.length === 0 && (
              <tr>
                <td colSpan="9" className="muted">暂无持仓，请在下方录入交易。</td>
              </tr>
            )}
            {holdings.map((h) => (
              <tr key={h.assetId}>
                <td>
                  <div>{h.name}</div>
                  <div className="muted">{h.code}</div>
                </td>
                <td>
                  <span className={'badge ' + (h.type === 'STOCK' ? 'stock' : 'fund')}>
                    {h.type === 'STOCK' ? '股票' : '基金'}
                  </span>
                </td>
                <td className="right">{fmt(h.shares)}</td>
                <td className="right">{fmt(h.avgCost)}</td>
                <td className="right">{fmt(h.latestPrice)}</td>
                <td className="right">¥{fmt(h.marketValue)}</td>
                <td className={'right ' + cls(h.unrealizedProfit)}>
                  {sign(h.unrealizedProfit)}{fmt(h.unrealizedProfit)}
                </td>
                <td className={'right ' + cls(h.realizedProfit)}>
                  {sign(h.realizedProfit)}{fmt(h.realizedProfit)}
                </td>
                <td className={'right ' + cls(h.totalProfit)}>
                  {sign(h.profitRate)}{fmt(h.profitRate)}%
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="card-row">
        <div className="card">
          <h3>添加股票/基金</h3>
          <div className="form-grid">
            <div>
              <label>代码</label>
              <input value={assetForm.code} onChange={(e) => setAssetForm({ ...assetForm, code: e.target.value })} />
            </div>
            <div>
              <label>名称</label>
              <input value={assetForm.name} onChange={(e) => setAssetForm({ ...assetForm, name: e.target.value })} />
            </div>
            <div>
              <label>类型</label>
              <select value={assetForm.type} onChange={(e) => setAssetForm({ ...assetForm, type: e.target.value })}>
                <option value="FUND">基金</option>
                <option value="STOCK">股票</option>
              </select>
            </div>
            <div>
              <label>市场</label>
              <input value={assetForm.market} onChange={(e) => setAssetForm({ ...assetForm, market: e.target.value })} />
            </div>
            <div>
              <label>最新价</label>
              <input type="number" step="0.0001" value={assetForm.latestPrice} onChange={(e) => setAssetForm({ ...assetForm, latestPrice: e.target.value })} />
            </div>
          </div>
          <div className="mt">
            <button onClick={addAsset}>添加资产</button>
          </div>
        </div>

        <div className="card">
          <h3>录入交易(买入/卖出)</h3>
          <div className="form-grid">
            <div>
              <label>资产</label>
              <select value={tradeForm.assetId} onChange={(e) => setTradeForm({ ...tradeForm, assetId: e.target.value })}>
                {assets.map((a) => (
                  <option key={a.id} value={a.id}>
                    {a.name}({a.code})
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label>方向</label>
              <select value={tradeForm.type} onChange={(e) => setTradeForm({ ...tradeForm, type: e.target.value })}>
                <option value="BUY">买入</option>
                <option value="SELL">卖出</option>
              </select>
            </div>
            <div>
              <label>份额/股数</label>
              <input type="number" value={tradeForm.shares} onChange={(e) => setTradeForm({ ...tradeForm, shares: e.target.value })} />
            </div>
            <div>
              <label>价格</label>
              <input type="number" step="0.0001" value={tradeForm.price} onChange={(e) => setTradeForm({ ...tradeForm, price: e.target.value })} />
            </div>
            <div>
              <label>手续费</label>
              <input type="number" step="0.01" value={tradeForm.fee} onChange={(e) => setTradeForm({ ...tradeForm, fee: e.target.value })} />
            </div>
            <div>
              <label>交易日期</label>
              <input type="date" value={tradeForm.tradeDate} onChange={(e) => setTradeForm({ ...tradeForm, tradeDate: e.target.value })} />
            </div>
          </div>
          <div className="mt">
            <button onClick={addTrade} disabled={!tradeForm.assetId}>记录交易</button>
          </div>
        </div>
      </div>

      <div className="card">
        <h3>资产列表</h3>
        <table>
          <thead>
            <tr>
              <th>代码</th>
              <th>名称</th>
              <th>类型</th>
              <th className="right">最新价</th>
              <th className="right">涨跌幅</th>
              <th className="right">操作</th>
            </tr>
          </thead>
          <tbody>
            {assets.map((a) => (
              <tr key={a.id}>
                <td>{a.code}</td>
                <td>{a.name}</td>
                <td>
                  <span className={'badge ' + (a.type === 'STOCK' ? 'stock' : 'fund')}>
                    {a.type === 'STOCK' ? '股票' : '基金'}
                  </span>
                </td>
                <td className="right">{fmt(a.latestPrice)}</td>
                <td className={'right ' + cls(a.changePct)}>
                  {a.changePct != null ? sign(a.changePct) + fmt(a.changePct) + '%' : '-'}
                </td>
                <td className="right">
                  <button className="ghost sm danger" onClick={() => delAsset(a.id)}>删除</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="card">
        <h3>历史交易明细</h3>
        <table>
          <thead>
            <tr>
              <th>日期</th>
              <th>资产ID</th>
              <th>方向</th>
              <th className="right">份额</th>
              <th className="right">价格</th>
              <th className="right">金额</th>
              <th className="right">手续费</th>
              <th className="right">实现盈亏</th>
            </tr>
          </thead>
          <tbody>
            {txns.length === 0 && (
              <tr><td colSpan="8" className="muted">暂无交易记录。</td></tr>
            )}
            {txns.map((t) => (
              <tr key={t.id}>
                <td>{t.tradeDate}</td>
                <td>{t.assetId}</td>
                <td>
                  <span className={'badge ' + (t.type === 'BUY' ? 'increase' : 'decrease')}>
                    {t.type === 'BUY' ? '买入' : '卖出'}
                  </span>
                </td>
                <td className="right">{fmt(t.shares)}</td>
                <td className="right">{fmt(t.price)}</td>
                <td className="right">¥{fmt(t.amount)}</td>
                <td className="right">{fmt(t.fee)}</td>
                <td className={'right ' + cls(t.realizedProfit)}>
                  {t.realizedProfit != 0 ? sign(t.realizedProfit) + fmt(t.realizedProfit) : '-'}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

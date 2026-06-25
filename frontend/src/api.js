import axios from 'axios'

const http = axios.create({
  baseURL: '/api',
  timeout: 180000,
})

http.interceptors.response.use(
  (resp) => {
    const body = resp.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code !== 0) {
        return Promise.reject(new Error(body.message || '请求失败'))
      }
      return body.data
    }
    return body
  },
  (error) => {
    const msg = error.response?.data?.message || error.message || '网络错误'
    return Promise.reject(new Error(msg))
  },
)

export const api = {
  // AI 配置
  getAiConfig: () => http.get('/ai-config'),
  saveAiConfig: (data) => http.post('/ai-config', data),
  testAiConfig: () => http.post('/ai-config/test'),

  // 资产
  listAssets: () => http.get('/assets'),
  createAsset: (data) => http.post('/assets', data),
  updatePrice: (id, latestPrice) => http.put(`/assets/${id}/price`, { latestPrice }),
  deleteAsset: (id) => http.delete(`/assets/${id}`),

  // 持仓 / 交易
  listHoldings: () => http.get('/holdings'),
  portfolioSummary: () => http.get('/holdings/summary'),
  trade: (data) => http.post('/holdings/transactions', data),
  assetTransactions: (assetId) => http.get(`/holdings/${assetId}/transactions`),
  allTransactions: () => http.get('/holdings/transactions'),

  // 预测
  predict: (assetCode, fetchNews) => http.post('/predictions', { assetCode, fetchNews }),
  predictionHistory: (assetCode) =>
    http.get('/predictions', { params: assetCode ? { assetCode } : {} }),

  // 理财
  financePlan: (salary, expense) => http.post('/finance/plan', { salary, expense }),
  financePlans: () => http.get('/finance/plans'),
  saveSalary: (data) => http.post('/finance/salary', data),
  salaryHistory: () => http.get('/finance/salary'),
}

export default api

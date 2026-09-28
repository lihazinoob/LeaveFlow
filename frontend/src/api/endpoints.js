import api from './client'

/**
 * One small function per backend endpoint, so pages never build URLs themselves.
 * Every function returns the response body (Axios `data`).
 */
const get = (url, params) => api.get(url, { params }).then((res) => res.data)
const post = (url, body) => api.post(url, body).then((res) => res.data)
const put = (url, body) => api.put(url, body).then((res) => res.data)
const patch = (url, body) => api.patch(url, body).then((res) => res.data)
const del = (url) => api.delete(url).then((res) => res.data)

export const authApi = {
  me: () => get('/auth/me'),
  changePassword: (body) => put('/auth/password', body),
}

export const employeeApi = {
  search: (params) => get('/employees', params),
  get: (id) => get(`/employees/${id}`),
  create: (body) => post('/employees', body),
  update: (id, body) => put(`/employees/${id}`, body),
  deactivate: (id) => del(`/employees/${id}`),
  activate: (id) => patch(`/employees/${id}/activate`),
  team: () => get('/employees/team'),
  managerOptions: () => get('/employees/manager-options'),
  departments: () => get('/employees/departments'),
}

export const leaveApi = {
  types: () => get('/leave-types'),
  preview: (params) => get('/leaves/preview', params),
  apply: (body) => post('/leaves', body),
  update: (id, body) => put(`/leaves/${id}`, body),
  get: (id) => get(`/leaves/${id}`),
  history: (id) => get(`/leaves/${id}/history`),
  mine: (params) => get('/leaves/my', params),
  team: (params) => get('/leaves/team', params),
  approvals: () => get('/leaves/approvals'),
  search: (params) => get('/leaves', params),
  balance: (params) => get('/leaves/balance', params),
  approve: (id, comment) => post(`/leaves/${id}/approve`, { comment }),
  reject: (id, comment) => post(`/leaves/${id}/reject`, { comment }),
  cancel: (id, comment) => post(`/leaves/${id}/cancel`, { comment }),
}

export const holidayApi = {
  list: (year) => get('/holidays', { year }),
  create: (body) => post('/holidays', body),
  update: (id, body) => put(`/holidays/${id}`, body),
  remove: (id) => del(`/holidays/${id}`),
}

export const dashboardApi = {
  me: () => get('/dashboard/me'),
  team: () => get('/dashboard/team'),
  organisation: (year) => get('/dashboard/organisation', { year }),
  calendar: (month) => get('/calendar', { month }),
}

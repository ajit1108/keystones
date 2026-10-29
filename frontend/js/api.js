/* ═══════════════════════════════════════
   api.js  –  Centralised HTTP client
   ═══════════════════════════════════════ */

const API_BASE = (() => {
  if (typeof window.KEYSTONE_API_BASE === 'string') {
    return window.KEYSTONE_API_BASE.replace(/\/$/, '');
  }
  const { protocol, hostname, port } = window.location;
  if (protocol === 'file:') return 'http://localhost:8080';
  const local = hostname === 'localhost' || hostname === '127.0.0.1';
  if (local && port && port !== '8080' && port !== '80' && port !== '443') {
    return 'http://localhost:8080';
  }
  return ''; // same origin (nginx proxies /api)
})();

function getToken() { return localStorage.getItem('ks_token'); }

function authHeaders() {
  const token = getToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

function query(params = {}) {
  const entries = Object.entries(params)
    .filter(([, v]) => v !== null && v !== undefined && v !== '');
  return entries.length ? '?' + new URLSearchParams(entries).toString() : '';
}

function extractMessage(data, status) {
  if (data && typeof data === 'object') {
    if (data.message) {
      const fields = data.fieldErrors && Object.entries(data.fieldErrors);
      return fields && fields.length
        ? `${data.message}: ${fields.map(([k, v]) => `${k} ${v}`).join(', ')}`
        : data.message;
    }
  }
  if (typeof data === 'string' && data.trim()) return data;
  return `Request failed (HTTP ${status})`;
}

function handleSessionExpired() {
  if (typeof window.onSessionExpired === 'function') window.onSessionExpired();
  else {
    localStorage.removeItem('ks_token');
    localStorage.removeItem('ks_user');
  }
}

async function request(method, path, body) {
  const headers = { ...authHeaders() };
  let payload;
  if (body instanceof FormData) {
    payload = body;
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    payload = JSON.stringify(body);
  }

  const res = await fetch(API_BASE + path, { method, headers, body: payload });

  if (res.status === 401 && !path.startsWith('/api/auth/login')) {
    handleSessionExpired();
    throw Object.assign(new Error('Your session has expired. Please sign in again.'), { status: 401 });
  }

  const text = await res.text();
  let data = null;
  if (text) {
    try { data = JSON.parse(text); } catch { data = text; }
  }

  if (!res.ok) {
    throw Object.assign(new Error(extractMessage(data, res.status)), { status: res.status, data });
  }
  return data;
}

async function requestBlob(path) {
  const res = await fetch(API_BASE + path, { headers: authHeaders() });
  if (res.status === 401) {
    handleSessionExpired();
    throw Object.assign(new Error('Your session has expired. Please sign in again.'), { status: 401 });
  }
  if (!res.ok) {
    let data = null;
    try { data = await res.json(); } catch { /* binary error body */ }
    throw Object.assign(new Error(extractMessage(data, res.status)), { status: res.status });
  }
  return res.blob();
}

const api = {
  get:    (path)        => request('GET', path),
  post:   (path, body)  => request('POST', path, body),
  put:    (path, body)  => request('PUT', path, body),
  delete: (path)        => request('DELETE', path),

  // Auth
  login: (email, password) => request('POST', '/api/auth/login', { email, password }),
  me:    () => request('GET', '/api/auth/me'),

  // Users
  getUsers:       () => request('GET', '/api/users'),
  getTechnicians: () => request('GET', '/api/users/technicians'),
  createUser:     (d) => request('POST', '/api/users', d),
  updateUser:     (id, d) => request('PUT', `/api/users/${id}`, d),

  // Customers
  getCustomers: (params = {}) => request('GET', `/api/customers${query(params)}`),
  getCustomer:  (id) => request('GET', `/api/customers/${id}`),
  getMyCustomer: () => request('GET', '/api/customers/me'),
  createCustomer: (d) => request('POST', '/api/customers', d),
  updateCustomer: (id, d) => request('PUT', `/api/customers/${id}`, d),

  // Sites
  getSites:    (cid, params = {}) => request('GET', `/api/customers/${cid}/sites${query(params)}`),
  getAllSites:  (cid) => request('GET', `/api/customers/${cid}/sites/all`),
  getSite:     (cid, sid) => request('GET', `/api/customers/${cid}/sites/${sid}`),
  createSite:  (cid, d) => request('POST', `/api/customers/${cid}/sites`, d),
  updateSite:  (cid, sid, d) => request('PUT', `/api/customers/${cid}/sites/${sid}`, d),

  // Work orders
  getWorkOrders: (params = {}) => request('GET', `/api/work-orders${query(params)}`),
  getWorkOrder:  (id) => request('GET', `/api/work-orders/${id}`),
  createWorkOrder: (d) => request('POST', '/api/work-orders', d),
  updateWorkOrder: (id, d) => request('PUT', `/api/work-orders/${id}`, d),
  assignTechnician: (id, technicianId) => request('POST', `/api/work-orders/${id}/assign`, { technicianId }),
  transitionStatus: (id, newStatus, note) => request('POST', `/api/work-orders/${id}/status`, { newStatus, note }),
  addPartUsage: (id, d) => request('POST', `/api/work-orders/${id}/parts`, d),
  logTime:      (id, d) => request('POST', `/api/work-orders/${id}/time`, d),

  // Attachments
  uploadAttachment: (id, file) => {
    const form = new FormData();
    form.append('file', file);
    return request('POST', `/api/work-orders/${id}/attachments`, form);
  },
  downloadAttachment: (id, attachmentId) =>
    requestBlob(`/api/work-orders/${id}/attachments/${attachmentId}`),
  deleteAttachment: (id, attachmentId) =>
    request('DELETE', `/api/work-orders/${id}/attachments/${attachmentId}`),

  // Parts
  getParts: (params = {}) => request('GET', `/api/parts${query(params)}`),
  getPart:  (id) => request('GET', `/api/parts/${id}`),
  createPart: (d) => request('POST', '/api/parts', d),
  updatePart: (id, d) => request('PUT', `/api/parts/${id}`, d),

  // Reports
  getSummary: (params = {}) => request('GET', `/api/reports/summary${query(params)}`),

  // Notifications
  getNotifications: (params = {}) => request('GET', `/api/notifications${query(params)}`),
  getUnreadCount:   () => request('GET', '/api/notifications/unread-count'),
  markNotificationRead: (id) => request('POST', `/api/notifications/${id}/read`),
  markAllNotificationsRead: () => request('POST', '/api/notifications/read-all'),
};

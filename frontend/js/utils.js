/* ═══════════════════════════════════════════
   utils.js  –  Shared UI helpers
   ═══════════════════════════════════════════ */

/* ── Auth helpers ─────────────────────────────────────────────── */
const Auth = {
  getUser: () => { try { return JSON.parse(localStorage.getItem('ks_user')); } catch { return null; } },
  getToken: () => localStorage.getItem('ks_token'),
  isLoggedIn: () => !!Auth.getToken(),
  hasRole: (...roles) => { const u = Auth.getUser(); return !!u && roles.includes(u.role); },
  save: (data) => {
    localStorage.setItem('ks_token', data.token);
    localStorage.setItem('ks_user', JSON.stringify(data));
  },
  updateUser: (user) => {
    const merged = { ...(Auth.getUser() || {}), ...user };
    localStorage.setItem('ks_user', JSON.stringify(merged));
  },
  clear: () => { localStorage.removeItem('ks_token'); localStorage.removeItem('ks_user'); },
};

const ROLE_LABEL = { MANAGER: 'Manager', DISPATCHER: 'Dispatcher', TECHNICIAN: 'Technician', CUSTOMER: 'Customer' };

/* ── Motion preference ────────────────────────────────────────── */
const motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
function prefersReducedMotion() { return motionQuery.matches; }

/* ── Safety / formatting ──────────────────────────────────────── */
function esc(value) {
  return String(value ?? '').replace(/[&<>"']/g, ch => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  }[ch]));
}

function initials(first, last) {
  const a = (first || '').trim().charAt(0);
  const b = (last || '').trim().charAt(0);
  return (a + b || '?').toUpperCase();
}

function fmtMoney(value) {
  if (value === null || value === undefined) return '—';
  return '$' + Number(value).toFixed(2);
}

function fmtNumber(value) {
  if (value === null || value === undefined) return '—';
  return Number(value).toLocaleString(undefined, { maximumFractionDigits: 1 });
}

/* ── Async helpers ────────────────────────────────────────────── */
const searchTimers = {};
function scheduleSearch(key, fn, delay = 280) {
  clearTimeout(searchTimers[key]);
  searchTimers[key] = setTimeout(fn, delay);
}

function debounce(fn, delay = 280) {
  let timer;
  return (...args) => { clearTimeout(timer); timer = setTimeout(() => fn(...args), delay); };
}

function setBtnBusy(idOrEl, busy, label = 'Working…') {
  const btn = typeof idOrEl === 'string' ? document.getElementById(idOrEl) : idOrEl;
  if (!btn) return;
  if (busy) {
    if (!btn.classList.contains('is-loading')) btn.dataset.idleHtml = btn.innerHTML;
    btn.disabled = true;
    btn.classList.add('is-loading');
    btn.innerHTML = `<span class="btn-spinner" aria-hidden="true"></span><span>${esc(label)}</span>`;
  } else {
    btn.disabled = false;
    btn.classList.remove('is-loading');
    if (btn.dataset.idleHtml) btn.innerHTML = btn.dataset.idleHtml;
  }
}

/* ── Toast notifications ──────────────────────────────────────── */
function toast(msg, type = 'info', duration = 3800) {
  const map = { success: ICONS.checkCircle, error: ICONS.xCircle, info: ICONS.info };
  const el = document.createElement('div');
  el.className = `toast ${type}`;
  el.setAttribute('role', 'status');
  el.innerHTML = `
    <span class="toast-icon">${map[type] || ICONS.info}</span>
    <div class="toast-body">
      <span class="toast-msg">${esc(msg)}</span>
      <div class="toast-progress" style="animation-duration:${duration}ms"></div>
    </div>
    <button class="toast-close" type="button" aria-label="Dismiss notification">${ICONS.close}</button>`;

  const dismiss = () => {
    if (el.classList.contains('leaving')) return;
    el.classList.add('leaving');
    setTimeout(() => el.remove(), 260);
  };
  el.querySelector('.toast-close').addEventListener('click', dismiss);
  document.getElementById('toast-container').appendChild(el);
  setTimeout(dismiss, duration);
}

/* ── Status / priority badges ─────────────────────────────────── */
const STATUS_CLASS = {
  NEW: 'badge-new', ASSIGNED: 'badge-assigned', IN_PROGRESS: 'badge-progress',
  ON_HOLD: 'badge-hold', COMPLETED: 'badge-completed', CLOSED: 'badge-closed', CANCELLED: 'badge-cancelled',
};
const STATUS_LABEL = {
  NEW: 'New', ASSIGNED: 'Assigned', IN_PROGRESS: 'In Progress',
  ON_HOLD: 'On Hold', COMPLETED: 'Completed', CLOSED: 'Closed', CANCELLED: 'Cancelled',
};
const PRIORITY_CLASS = { LOW: 'badge-low', MEDIUM: 'badge-medium', HIGH: 'badge-high', CRITICAL: 'badge-critical' };
const SLA_CLASS = { ON_TRACK: 'badge-on-track', AT_RISK: 'badge-at-risk', BREACHED: 'badge-breached' };
const SLA_LABEL = { ON_TRACK: 'On Track', AT_RISK: 'At Risk', BREACHED: 'Breached' };
const ROLE_CLASS = {
  MANAGER: 'badge-role-manager', DISPATCHER: 'badge-role-dispatcher',
  TECHNICIAN: 'badge-role-technician', CUSTOMER: 'badge-role-customer',
};
const NOTIFICATION_LABEL = {
  ASSIGNMENT: 'Assignment', SLA_AT_RISK: 'SLA at risk',
  SLA_BREACH: 'SLA breach', CUSTOMER_REQUEST: 'Customer request',
};

function statusBadge(s) { return `<span class="badge ${STATUS_CLASS[s] || ''}">${esc(STATUS_LABEL[s] || s)}</span>`; }
function priorityBadge(p) { return `<span class="badge ${PRIORITY_CLASS[p] || ''}">${esc(p)}</span>`; }
function slaBadge(s) { return `<span class="badge ${SLA_CLASS[s] || ''}">${esc(SLA_LABEL[s] || s || '—')}</span>`; }
function roleBadge(r) { return `<span class="badge ${ROLE_CLASS[r] || ''}">${esc(r)}</span>`; }

/* ── Date formatting ──────────────────────────────────────────── */
function fmtDate(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' });
}
function fmtDateTime(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('en-US', {
    year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
  });
}
function fmtMinutes(m) {
  if (!m) return '0m';
  const h = Math.floor(m / 60), min = m % 60;
  return h > 0 ? `${h}h ${min}m` : `${min}m`;
}
function timeAgo(iso) {
  if (!iso) return '';
  const diff = Date.now() - new Date(iso).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 1) return 'just now';
  if (mins < 60) return `${mins}m ago`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `${days}d ago`;
  return fmtDate(iso);
}
function isOverdue(iso) { return !!iso && new Date(iso) < new Date(); }

/* ── Modal helpers ────────────────────────────────────────────── */
const modalStack = [];
const modalReturnFocus = new Map();

function openModal(id) {
  const overlay = document.getElementById(id);
  if (!overlay || overlay.classList.contains('open')) return;
  modalReturnFocus.set(id, document.activeElement);
  overlay.classList.add('open');
  overlay.setAttribute('aria-hidden', 'false');
  modalStack.push(id);
  document.body.classList.add('modal-open');
  setTimeout(() => {
    if (!overlay.classList.contains('open')) return;
    const target = overlay.querySelector(
      '[autofocus], input:not([type="hidden"]):not([disabled]), select:not([disabled]), textarea:not([disabled]), button.btn-primary'
    );
    if (target && !overlay.contains(document.activeElement)) target.focus({ preventScroll: true });
  }, 110);
}

function closeModal(id) {
  const overlay = document.getElementById(id);
  if (!overlay) return;
  overlay.classList.remove('open');
  overlay.setAttribute('aria-hidden', 'true');
  const idx = modalStack.lastIndexOf(id);
  if (idx !== -1) modalStack.splice(idx, 1);
  if (!modalStack.length) document.body.classList.remove('modal-open');
  const previous = modalReturnFocus.get(id);
  modalReturnFocus.delete(id);
  if (previous && typeof previous.focus === 'function' && !modalStack.length) {
    setTimeout(() => previous.focus({ preventScroll: true }), 30);
  }
}

function trapModalFocus(event) {
  if (event.key !== 'Tab' || !modalStack.length) return;
  const overlay = document.getElementById(modalStack[modalStack.length - 1]);
  if (!overlay) return;
  const nodes = [...overlay.querySelectorAll(
    'a[href], button:not([disabled]), input:not([type="hidden"]):not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
  )].filter(n => n.offsetParent !== null);
  if (!nodes.length) return;
  const first = nodes[0], last = nodes[nodes.length - 1];
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
}

/* ── Field validation feedback ────────────────────────────────── */
function formError(containerId, message, fieldIds = []) {
  const box = document.getElementById(containerId);
  if (box) {
    box.innerHTML = `<div class="alert alert-error alert-anim shake"><span class="alert-ico">${ICONS.xCircle}</span><span>${esc(message)}</span></div>`;
  }
  fieldIds.forEach(id => {
    const input = document.getElementById(id);
    if (!input) return;
    input.classList.add('is-invalid');
    input.addEventListener('input', () => input.classList.remove('is-invalid'), { once: true });
  });
  document.getElementById(fieldIds[0])?.focus({ preventScroll: false });
}

/* ── DOM helpers ──────────────────────────────────────────────── */
function el(selector) { return document.querySelector(selector); }
function els(selector) { return [...document.querySelectorAll(selector)]; }
function html(selector, content) { const e = el(selector); if (e) e.innerHTML = content; }
function show(selector) { const e = el(selector); if (e) e.style.display = ''; }
function hide(selector) { const e = el(selector); if (e) e.style.display = 'none'; }

/* ── Skeleton loaders ─────────────────────────────────────────── */
function skeletonHTML(kind = 'cards') {
  if (kind === 'stats') {
    return Array.from({ length: 6 }).map(() => `
      <div class="sk sk-stat">
        <span class="sk sk-circle"></span>
        <span class="sk-lines"><span class="sk sk-line w-60"></span><span class="sk sk-line w-40"></span></span>
      </div>`).join('');
  }
  if (kind === 'table') {
    return `<div class="sk-table">${Array.from({ length: 6 }).map(() =>
      `<div class="sk-row">${Array.from({ length: 5 }).map((_, i) =>
        `<span class="sk sk-line" style="width:${[16, 30, 14, 18, 10][i]}%"></span>`).join('')}</div>`).join('')}</div>`;
  }
  if (kind === 'form') {
    return `<div class="sk-form">${Array.from({ length: 3 }).map(() => `
      <div class="sk-field"><span class="sk sk-line w-30"></span><span class="sk sk-input"></span></div>`).join('')}
      <span class="sk sk-line w-20"></span></div>`;
  }
  if (kind === 'detail') {
    return `<div class="sk-detail">${Array.from({ length: 8 }).map(() => `
      <div class="sk-field"><span class="sk sk-line w-30"></span><span class="sk sk-line w-60"></span></div>`).join('')}</div>`;
  }
  if (kind === 'charts') {
    return `<div class="sk-charts">${Array.from({ length: 2 }).map(() => `
      <div class="sk sk-card"><span class="sk sk-line w-40"></span>
        ${Array.from({ length: 4 }).map(() => '<span class="sk sk-line w-100"></span>').join('')}
      </div>`).join('')}</div>`;
  }
  return `<div class="sk-cards">${Array.from({ length: 3 }).map(() => `
    <div class="sk sk-card"><span class="sk sk-line w-30"></span><span class="sk sk-line w-70"></span>
      <span class="sk sk-line w-50"></span><span class="sk sk-line w-60"></span></div>`).join('')}</div>`;
}

function setLoading(container, msg = 'Loading…', kind) {
  if (!container) return;
  let variant = kind;
  if (!variant) {
    if (container.id === 'dash-stats') variant = 'stats';
    else if (/table|content|list/i.test(container.id)) variant = 'table';
    else if (container.closest('.modal')) variant = 'form';
    else variant = 'cards';
  }
  container.innerHTML = `${skeletonHTML(variant)}<p class="sr-only">${esc(msg)}</p>`;
}

function setError(container, message) {
  if (!container) return;
  container.innerHTML = `<div class="alert alert-error"><span class="alert-ico">${ICONS.xCircle}</span><span>${esc(message)}</span></div>`;
}

function emptyState(icon, title, sub = '', actionHtml = '') {
  return `<div class="empty-state">
    <div class="empty-ico">${ICONS[icon] || ICONS.inbox}</div>
    <h3>${esc(title)}</h3>
    ${sub ? `<p>${esc(sub)}</p>` : ''}
    ${actionHtml ? `<div class="empty-action">${actionHtml}</div>` : ''}
  </div>`;
}

/* ── Animated counters / charts ───────────────────────────────── */
function animateCounters(root = document) {
  root.querySelectorAll('[data-count]').forEach(node => {
    const target = parseFloat(node.dataset.count);
    if (Number.isNaN(target)) return;
    const decimals = parseInt(node.dataset.decimals || '0', 10);
    const suffix = node.dataset.suffix || '';
    const prefix = node.dataset.prefix || '';
    const render = v => prefix + v.toLocaleString(undefined, {
      minimumFractionDigits: decimals, maximumFractionDigits: decimals,
    }) + suffix;
    if (prefersReducedMotion()) { node.textContent = render(target); return; }
    const duration = 850;
    const start = performance.now();
    const step = now => {
      const p = Math.min(1, (now - start) / duration);
      const eased = 1 - Math.pow(1 - p, 3);
      node.textContent = render(target * eased);
      if (p < 1) requestAnimationFrame(step);
    };
    node.textContent = render(0);
    requestAnimationFrame(step);
  });
}

function animateCharts(root = document) {
  const activate = () => {
    root.querySelectorAll('[data-width]').forEach(node => {
      node.style.width = node.dataset.width + '%';
    });
    root.querySelectorAll('[data-donut]').forEach(node => {
      const pct = Math.max(0, Math.min(100, parseFloat(node.dataset.donut) || 0));
      node.style.strokeDashoffset = String(100 - pct);
    });
  };
  if (prefersReducedMotion()) {
    root.querySelectorAll('[data-width]').forEach(node => { node.style.transition = 'none'; });
    activate();
    return;
  }
  requestAnimationFrame(() => requestAnimationFrame(activate));
}

function animateVisuals(root = document) {
  animateCharts(root);
  animateCounters(root);
}

/* ── Pagination builder ────────────────────────────────────────── */
function buildPagination(page, totalPages, onPage) {
  if (!totalPages || totalPages <= 1) return '';
  let out = '<div class="pagination">';
  if (page > 0) out += `<button class="page-btn" type="button" onclick="(${onPage})(${page - 1})" aria-label="Previous page">‹</button>`;
  for (let i = Math.max(0, page - 2); i <= Math.min(totalPages - 1, page + 2); i++) {
    out += `<button class="page-btn${i === page ? ' active' : ''}" type="button" onclick="(${onPage})(${i})"${i === page ? ' aria-current="page"' : ''}>${i + 1}</button>`;
  }
  if (page < totalPages - 1) out += `<button class="page-btn" type="button" onclick="(${onPage})(${page + 1})" aria-label="Next page">›</button>`;
  out += `<span class="page-info">&nbsp;Page ${page + 1} of ${totalPages}</span>`;
  return out + '</div>';
}

/* ── Sidebar navigation ────────────────────────────────────────── */
function navigate(pageId) {
  els('.page').forEach(p => p.classList.remove('active'));
  const target = document.getElementById(`page-${pageId}`);
  if (target) target.classList.add('active');

  els('.nav-item').forEach(item => {
    const active = item.dataset.page === pageId;
    item.classList.toggle('active', active);
    if (active) item.setAttribute('aria-current', 'page');
    else item.removeAttribute('aria-current');
  });

  const titles = {
    dashboard: 'Dashboard',
    workorders: 'Work Orders',
    myjobs: 'My Jobs',
    portal: 'Customer Portal',
    customers: 'Customers',
    parts: 'Parts Inventory',
    users: 'User Management',
    reports: 'Reports & Analytics',
  };
  const titleEl = document.getElementById('page-title');
  if (titleEl) titleEl.textContent = titles[pageId] || pageId;

  document.getElementById('content')?.scrollTo({ top: 0, behavior: prefersReducedMotion() ? 'auto' : 'smooth' });
  closeSidebar();
  if (typeof pageLoaders[pageId] === 'function') pageLoaders[pageId]();
}

/* ── SVG icons (inline) ────────────────────────────────────────── */
const ICONS = {
  dashboard:  `<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/></svg>`,
  workorders: `<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14,2 14,8 20,8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10,9 9,9 8,9"/></svg>`,
  customers:  `<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9,22 9,12 15,12 15,22"/></svg>`,
  parts:      `<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/></svg>`,
  users:      `<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>`,
  reports:    `<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/></svg>`,
  briefcase:  `<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="7" width="20" height="14" rx="2"/><path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"/></svg>`,
  building:   `<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="4" y="2" width="16" height="20" rx="2"/><line x1="9" y1="7" x2="9" y2="7"/><line x1="15" y1="7" x2="15" y2="7"/><line x1="9" y1="12" x2="9" y2="12"/><line x1="15" y1="12" x2="15" y2="12"/><line x1="9" y1="17" x2="9" y2="17"/><line x1="15" y1="17" x2="15" y2="17"/></svg>`,
  logout:     `<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16,17 21,12 16,7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>`,
  plus:       `<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>`,
  search:     `<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>`,
  close:      `<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>`,
  wrench:     `<svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/></svg>`,
  menu:       `<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/></svg>`,
  eye:        `<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>`,
  edit:       `<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>`,
  clock:      `<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12,6 12,12 16,14"/></svg>`,
  bell:       `<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></svg>`,
  camera:     `<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z"/><circle cx="12" cy="13" r="4"/></svg>`,
  download:   `<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7,10 12,15 17,10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>`,
  trash:      `<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3,6 5,6 21,6"/><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/><path d="M10 11v6"/><path d="M14 11v6"/><path d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/></svg>`,
  play:       `<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="5,3 19,12 5,21"/></svg>`,
  check:      `<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><polyline points="20,6 9,17 4,12"/></svg>`,
  send:       `<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22,2 15,22 11,13 2,9"/></svg>`,
  refresh:    `<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="23,4 23,10 17,10"/><polyline points="1,20 1,14 7,14"/><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/></svg>`,
  list:       `<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="8" y1="6" x2="21" y2="6"/><line x1="8" y1="12" x2="21" y2="12"/><line x1="8" y1="18" x2="21" y2="18"/><line x1="3" y1="6" x2="3.01" y2="6"/><line x1="3" y1="12" x2="3.01" y2="12"/><line x1="3" y1="18" x2="3.01" y2="18"/></svg>`,
  columns:    `<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="18"/><rect x="14" y="3" width="7" height="18"/></svg>`,
  filter:     `<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="22,3 2,3 10,12.46 10,19 14,21 14,12.46 22,3"/></svg>`,
  chevronDown:`<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="6,9 12,15 18,9"/></svg>`,
  arrowRight: `<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="5" y1="12" x2="19" y2="12"/><polyline points="12,5 19,12 12,19"/></svg>`,
  info:       `<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>`,
  alert:      `<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>`,
  checkCircle:`<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22,4 12,14.01 9,11.01"/></svg>`,
  xCircle:    `<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>`,
  user:       `<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>`,
  image:      `<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8.5" cy="8.5" r="1.5"/><polyline points="21,15 16,10 5,21"/></svg>`,
  inbox:      `<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><polyline points="22,12 16,12 14,15 10,15 8,12 2,12"/><path d="M5.45 5.11L2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z"/></svg>`,
};

/* ── Global modal / keyboard behaviour ────────────────────────── */
document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape') {
    const panel = document.getElementById('notif-panel');
    if (panel) panel.hidden = true;
    if (typeof closeLightbox === 'function') closeLightbox();
    [...modalStack].reverse().forEach(id => closeModal(id));
  }
  trapModalFocus(e);
});

/* Sidebar open/close transitions are handled in app.js */

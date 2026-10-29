/* ══════════════════════════════════════════════════
   app.js  –  Boot, authentication, navigation, notifications
   ══════════════════════════════════════════════════ */

let notifTimer = null;

/* ── Login / logout ──────────────────────────────────────────────── */
function loginAlert(message, type = 'error') {
  const box = document.getElementById('login-alert');
  if (!box) return;
  box.innerHTML = message
    ? `<div class="alert alert-${type}"><span>${type === 'error' ? '✕' : 'ℹ'}</span> ${esc(message)}</div>`
    : '';
}

function showLogin(message) {
  stopNotificationPolling();
  if (typeof revokePhotoUrls === 'function') revokePhotoUrls();
  const panel = document.getElementById('notif-panel');
  if (panel) panel.hidden = true;
  document.getElementById('app').hidden = true;
  document.getElementById('login-page').classList.add('active');
  loginAlert(message || '');
  setTimeout(() => document.getElementById('login-email')?.focus(), 60);
}

async function handleLogin(event) {
  if (event) event.preventDefault();
  const email = document.getElementById('login-email').value.trim();
  const password = document.getElementById('login-password').value;
  loginAlert('');

  if (!email || !password) {
    loginAlert('Enter your email address and password.');
    return;
  }

  const btn = document.getElementById('login-btn');
  setBtnBusy(btn, true, 'Signing in…');

  try {
    const data = await api.login(email, password);
    Auth.save(data);
    document.getElementById('login-form').reset();
    showApp();
  } catch (e) {
    loginAlert(e.message || 'Sign-in failed. Please try again.');
  } finally {
    setBtnBusy(btn, false);
  }
}

function fillLogin(email, password) {
  document.getElementById('login-email').value = email;
  document.getElementById('login-password').value = password;
  loginAlert('');
  document.getElementById('login-password').focus();
}

function togglePassword() {
  const input = document.getElementById('login-password');
  const btn = document.getElementById('pw-toggle');
  const show = input.type === 'password';
  input.type = show ? 'text' : 'password';
  btn.setAttribute('aria-label', show ? 'Hide password' : 'Show password');
}

function logout() {
  Auth.clear();
  stopNotificationPolling();
  window.location.reload();
}

/* ── App shell ───────────────────────────────────────────────────── */
function showApp() {
  const user = Auth.getUser();
  if (!user) { showLogin(); return; }

  document.getElementById('login-page').classList.remove('active');
  loginAlert('');
  document.getElementById('app').hidden = false;

  buildNav();
  applyUserChrome();
  syncConnection();
  startNotificationPolling();
  navigate(defaultPageFor(user));
}

function defaultPageFor(user) {
  if (!user) return 'dashboard';
  if (user.role === 'CUSTOMER') return 'portal';
  if (user.role === 'TECHNICIAN') return 'myjobs';
  return 'dashboard';
}

function buildNav() {
  const role = Auth.getUser()?.role;
  const nav = document.getElementById('sidebar-nav');
  let index = 0;
  const item = (page, icon, label) =>
    `<button class="nav-item" type="button" data-page="${page}" style="--i:${index++}" title="${label}" onclick="navigate('${page}')">${ICONS[icon] || ''}<span>${label}</span></button>`;
  const section = (label) => `<div class="nav-section-label">${label}</div>`;
  const parts = [];

  if (role === 'MANAGER' || role === 'DISPATCHER') {
    parts.push(section('Operations'));
    parts.push(item('dashboard', 'dashboard', 'Dashboard'));
    parts.push(item('workorders', 'workorders', 'Work Orders'));
    parts.push(item('customers', 'customers', 'Customers'));
    parts.push(item('parts', 'parts', 'Parts Inventory'));
    parts.push(section('Administration'));
    if (role === 'MANAGER') parts.push(item('users', 'users', 'Users'));
    parts.push(item('reports', 'reports', 'Reports'));
  } else if (role === 'TECHNICIAN') {
    parts.push(section('Field Work'));
    parts.push(item('myjobs', 'briefcase', 'My Jobs'));
    parts.push(section('Resources'));
    parts.push(item('parts', 'parts', 'Parts Inventory'));
  } else {
    parts.push(section('My Account'));
    parts.push(item('portal', 'building', 'Customer Portal'));
  }

  nav.innerHTML = parts.join('');
}

function applyUserChrome() {
  const user = Auth.getUser() || {};
  const fullName = user.fullName || [user.firstName, user.lastName].filter(Boolean).join(' ') || user.email || '—';
  document.getElementById('user-avatar').textContent = initials(user.firstName, user.lastName);
  document.getElementById('user-name').textContent = fullName;
  document.getElementById('user-role').textContent = ROLE_LABEL[user.role] || user.role || '—';
  document.getElementById('topbar-role').textContent = ROLE_LABEL[user.role] || user.role || '—';
  document.title = `Keystone FSM — ${ROLE_LABEL[user.role] || ''}`;
}

/* ── Sidebar (desktop collapse / mobile drawer) ──────────────────── */
function toggleSidebar() {
  const sidebar = document.getElementById('sidebar');
  const backdrop = document.getElementById('mobile-backdrop');
  if (window.innerWidth <= 900) {
    const open = sidebar.classList.toggle('open');
    backdrop.classList.toggle('open', open);
  } else {
    sidebar.classList.toggle('collapsed');
    closeSidebar();
  }
}

function closeSidebar() {
  document.getElementById('sidebar')?.classList.remove('open');
  document.getElementById('mobile-backdrop')?.classList.remove('open');
}

window.addEventListener('resize', () => {
  if (window.innerWidth > 900) closeSidebar();
});

/* ── Connection indicator ────────────────────────────────────────── */
function syncConnection() {
  const dot = document.getElementById('connection-dot');
  const badge = document.getElementById('connection-badge');
  const online = navigator.onLine;
  if (dot) dot.classList.toggle('offline', !online);
  if (badge) badge.title = online ? 'Connected' : 'Offline — changes may not save';
}

window.addEventListener('online', () => {
  syncConnection();
  toast('Connection restored', 'success');
});
window.addEventListener('offline', () => {
  syncConnection();
  toast('You are offline — changes may not save', 'error');
});

/* ── Notifications ───────────────────────────────────────────────── */
let lastUnreadCount = null;

function refreshUnreadCount() {
  const badge = document.getElementById('notif-badge');
  api.getUnreadCount()
    .then(({ count }) => {
      if (!badge) return;
      badge.hidden = !count;
      badge.textContent = count > 99 ? '99+' : String(count);
      if (lastUnreadCount !== null && count > lastUnreadCount) {
        badge.classList.remove('bump');
        void badge.offsetWidth;
        badge.classList.add('bump');
      }
      lastUnreadCount = count;
    })
    .catch(() => { /* badge is best-effort; auth errors already surface */ });
}

function startNotificationPolling() {
  stopNotificationPolling();
  refreshUnreadCount();
  notifTimer = setInterval(refreshUnreadCount, 30000);
}

function stopNotificationPolling() {
  if (notifTimer) clearInterval(notifTimer);
  notifTimer = null;
}

function toggleNotifications(event) {
  if (event) event.stopPropagation();
  const panel = document.getElementById('notif-panel');
  const btn = document.getElementById('notif-btn');
  const opening = panel.hidden;
  panel.hidden = !opening;
  if (btn) btn.setAttribute('aria-expanded', String(opening));
  if (opening) loadNotifications();
}

async function loadNotifications() {
  const list = document.getElementById('notif-list');
  list.innerHTML = '<div class="notif-empty">Loading…</div>';
  try {
    const data = await api.getNotifications({ size: 20 });
    const items = data.content || [];
    list.innerHTML = items.length
      ? items.map(n => `
        <button class="notif-item${n.read ? '' : ' unread'}" type="button"
                onclick="openNotification('${esc(n.id)}','${esc(n.workOrderId || '')}')">
          <div class="n-title">${esc(n.title)}</div>
          <div class="n-msg">${esc(n.message)}</div>
          <div class="n-time">${esc(NOTIFICATION_LABEL[n.type] || n.type)} · ${esc(timeAgo(n.createdAt))}</div>
        </button>`).join('')
      : '<div class="notif-empty">You have no notifications yet</div>';
  } catch (e) {
    list.innerHTML = `<div class="notif-empty">${esc(e.message)}</div>`;
  }
}

async function openNotification(id, workOrderId) {
  document.getElementById('notif-panel').hidden = true;
  try { await api.markNotificationRead(id); } catch { /* already read or gone */ }
  refreshUnreadCount();
  if (workOrderId && Auth.hasRole('MANAGER', 'DISPATCHER', 'TECHNICIAN', 'CUSTOMER')) {
    openWODetail(workOrderId);
  }
}

async function markAllNotificationsRead() {
  try {
    await api.markAllNotificationsRead();
    await loadNotifications();
    refreshUnreadCount();
    toast('All notifications marked read', 'success');
  } catch (e) {
    toast(e.message, 'error');
  }
}

document.addEventListener('click', (e) => {
  const wrap = document.querySelector('.notif-wrap');
  const panel = document.getElementById('notif-panel');
  if (panel && !panel.hidden && wrap && !wrap.contains(e.target)) {
    panel.hidden = true;
    document.getElementById('notif-btn')?.setAttribute('aria-expanded', 'false');
  }
});

/* "/" focuses the search field on the visible page */
document.addEventListener('keydown', (e) => {
  if (e.key !== '/' || e.ctrlKey || e.metaKey || e.altKey) return;
  const active = document.activeElement;
  if (active && (['INPUT', 'TEXTAREA', 'SELECT'].includes(active.tagName) || active.isContentEditable)) return;
  const search = document.querySelector('.page.active .search-wrap input');
  if (!search) return;
  e.preventDefault();
  search.focus();
  search.select();
});

/* Refresh the bell after the tab regains focus so stale counts disappear */
document.addEventListener('visibilitychange', () => {
  if (!document.hidden && Auth.isLoggedIn()) refreshUnreadCount();
});

/* ── Session expiry hook used by api.js ──────────────────────────── */
window.onSessionExpired = () => showLogin('Your session has expired. Please sign in again.');

/* ── Boot ────────────────────────────────────────────────────────── */
async function boot() {
  if (!Auth.isLoggedIn()) {
    showLogin();
    return;
  }
  try {
    const me = await api.me();
    Auth.updateUser(me);
    showApp();
  } catch (e) {
    if (e.status === 401) return; // onSessionExpired already handled
    showLogin(`Could not reach the server (${e.message}). Check that the backend is running.`);
  }
}

document.addEventListener('DOMContentLoaded', boot);

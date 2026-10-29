/* ══════════════════════════════════════════════════
   app.js  –  Boot, authentication, navigation, notifications
   ══════════════════════════════════════════════════ */

let notifTimer = null;

/* ── Auth screens ────────────────────────────────────────────────── */
const AUTH_ALERT_ICONS = { error: '✕', success: '✓', info: 'ℹ' };

function authAlert(targetId, message, type = 'error') {
  const box = document.getElementById(targetId);
  if (!box) return;
  box.innerHTML = message
    ? `<div class="alert alert-${type}"><span aria-hidden="true">${AUTH_ALERT_ICONS[type] || AUTH_ALERT_ICONS.info}</span> ${esc(message)}</div>`
    : '';
}

function loginAlert(message, type = 'error') { authAlert('login-alert', message, type); }
function signupAlert(message, type = 'error') { authAlert('signup-alert', message, type); }

function authScreenFromHash() {
  return window.location.hash === '#signup' ? 'signup' : 'login';
}

function setAuthScreen(screen, { push = false } = {}) {
  const target = screen === 'signup' ? 'signup' : 'login';

  document.querySelectorAll('.auth-panel').forEach(panel => {
    const active = panel.id === `auth-${target}`;
    panel.classList.toggle('active', active);
    panel.setAttribute('aria-hidden', String(!active));
  });

  ['login-password', 'signup-password', 'signup-confirm'].forEach(id => {
    const input = document.getElementById(id);
    if (input) input.type = 'password';
  });
  document.querySelectorAll('.pw-toggle').forEach(btn => {
    btn.setAttribute('aria-label', 'Show password');
    btn.setAttribute('aria-pressed', 'false');
  });

  document.title = target === 'signup' ? 'Keystone FSM — Create account' : 'Keystone FSM — Sign in';

  const url = window.location.pathname + window.location.search + (target === 'signup' ? '#signup' : '');
  if (push) history.pushState({ auth: target }, '', url);
  else history.replaceState({ auth: target }, '', url);
}

function focusAuthField(id) {
  setTimeout(() => document.getElementById(id)?.focus(), 60);
}

function showSignup() {
  setAuthScreen('signup', { push: true });
  signupAlert('');
  focusAuthField('signup-name');
}

function showSignIn() {
  setAuthScreen('login', { push: true });
  loginAlert('');
  focusAuthField('login-email');
}

function showLogin(message) {
  stopNotificationPolling();
  if (typeof revokePhotoUrls === 'function') revokePhotoUrls();
  const panel = document.getElementById('notif-panel');
  if (panel) panel.hidden = true;
  document.getElementById('app').hidden = true;
  document.getElementById('login-page').classList.add('active');
  setAuthScreen('login');
  loginAlert(message || '');
  focusAuthField('login-email');
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
    document.getElementById('login-password').type = 'password';
    showApp();
  } catch (e) {
    loginAlert(e.message || 'Sign-in failed. Please try again.');
  } finally {
    setBtnBusy(btn, false);
  }
}

function togglePasswordVisibility(inputId, btn) {
  const input = document.getElementById(inputId);
  if (!input || !btn) return;
  const show = input.type === 'password';
  input.type = show ? 'text' : 'password';
  btn.setAttribute('aria-label', show ? 'Hide password' : 'Show password');
  btn.setAttribute('aria-pressed', String(show));
}

/* ── Sign-up form ────────────────────────────────────────────────── */
const SIGNUP_FIELDS = ['signup-name', 'signup-email', 'signup-password', 'signup-confirm'];
let signupBusy = false;

function setFieldError(id, message) {
  const input = document.getElementById(id);
  const msg = document.getElementById(`${id}-msg`);
  if (input) {
    input.classList.add('is-invalid');
    input.setAttribute('aria-invalid', 'true');
  }
  if (msg) {
    msg.textContent = message;
    msg.hidden = false;
  }
}

function clearFieldError(id) {
  const input = document.getElementById(id);
  const msg = document.getElementById(`${id}-msg`);
  if (input) {
    input.classList.remove('is-invalid');
    input.removeAttribute('aria-invalid');
  }
  if (msg) {
    msg.hidden = true;
    msg.textContent = '';
  }
}

function clearSignupErrors() {
  SIGNUP_FIELDS.forEach(clearFieldError);
  signupAlert('');
}

function updatePasswordMeter() {
  const value = document.getElementById('signup-password')?.value || '';
  const meter = document.getElementById('signup-pw-meter');
  const label = document.getElementById('signup-pw-strength');
  if (!meter || !label) return;

  let score = 0;
  if (value.length >= 8) score++;
  if (value.length >= 12) score++;
  if (/[a-z]/.test(value) && /[A-Z]/.test(value)) score++;
  if (/\d/.test(value)) score++;
  if (/[^A-Za-z0-9]/.test(value)) score++;
  score = value ? Math.max(1, Math.min(score, 4)) : 0;

  meter.dataset.score = String(score);
  const words = ['', 'Weak password', 'Fair password', 'Good password', 'Strong password'];
  if (!value) label.textContent = 'Use 8+ characters with a letter and a number';
  else if (value.length < 8) label.textContent = 'Too short — use at least 8 characters';
  else if (!/[A-Za-z]/.test(value) || !/\d/.test(value)) label.textContent = 'Add a letter and a number';
  else label.textContent = words[score];
}

function validateSignup() {
  clearSignupErrors();
  const name = document.getElementById('signup-name').value.trim().replace(/\s+/g, ' ');
  const email = document.getElementById('signup-email').value.trim();
  const password = document.getElementById('signup-password').value;
  const confirm = document.getElementById('signup-confirm').value;
  let firstInvalid = null;

  const fail = (id, message) => {
    setFieldError(id, message);
    if (!firstInvalid) firstInvalid = document.getElementById(id);
  };

  if (!name) fail('signup-name', 'Enter your full name.');
  else if (!name.includes(' ')) fail('signup-name', 'Enter your first and last name.');
  else if (name.length > 160) fail('signup-name', 'Name is too long.');

  if (!email) fail('signup-email', 'Enter your email address.');
  else if (email.length > 160 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    fail('signup-email', 'Enter a valid email address.');
  }

  if (!password) fail('signup-password', 'Enter a password.');
  else if (password.length < 8) fail('signup-password', 'Use at least 8 characters.');
  else if (!/[A-Za-z]/.test(password) || !/\d/.test(password)) {
    fail('signup-password', 'Include at least one letter and one number.');
  }

  if (!confirm) fail('signup-confirm', 'Confirm your password.');
  else if (confirm !== password) fail('signup-confirm', 'Passwords do not match.');

  if (firstInvalid) firstInvalid.focus();
  return !firstInvalid;
}

async function handleRegister(event) {
  if (event) event.preventDefault();
  if (signupBusy || !validateSignup()) return;

  const btn = document.getElementById('signup-btn');
  const fullName = document.getElementById('signup-name').value.trim().replace(/\s+/g, ' ');
  const email = document.getElementById('signup-email').value.trim();
  const password = document.getElementById('signup-password').value;
  const confirmPassword = document.getElementById('signup-confirm').value;

  signupBusy = true;
  signupAlert('');
  setBtnBusy(btn, true, 'Creating account…');

  try {
    await api.register({ fullName, email, password, confirmPassword });
    document.getElementById('signup-form').reset();
    updatePasswordMeter();
    document.getElementById('login-email').value = email;
    showLogin();
    loginAlert('Account created. Sign in to continue.', 'success');
    focusAuthField('login-password');
  } catch (e) {
    const fieldMap = {
      fullName: 'signup-name',
      email: 'signup-email',
      password: 'signup-password',
      confirmPassword: 'signup-confirm',
    };
    if (e.status === 409) {
      setFieldError('signup-email', 'That email is already registered.');
    } else {
      Object.entries(e.data?.fieldErrors || {}).forEach(([field, message]) => {
        if (fieldMap[field]) setFieldError(fieldMap[field], message);
      });
    }
    signupAlert(e.message || 'Could not create your account. Please try again.');
  } finally {
    signupBusy = false;
    setBtnBusy(btn, false);
  }
}

function logout() {
  Auth.clear();
  stopNotificationPolling();
  history.replaceState(null, '', window.location.pathname + window.location.search);
  window.location.reload();
}

/* ── App shell ───────────────────────────────────────────────────── */
function showApp() {
  const user = Auth.getUser();
  if (!user) { showLogin(); return; }

  if (window.location.hash) {
    history.replaceState(null, '', window.location.pathname + window.location.search);
  }

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
window.onSessionExpired = () => {
  Auth.clear();
  showLogin('Your session has expired. Please sign in again.');
};

/* Keep the visible auth screen in sync with browser back/forward */
window.addEventListener('popstate', () => {
  if (Auth.isLoggedIn()) {
    if (window.location.hash) {
      history.replaceState(null, '', window.location.pathname + window.location.search);
    }
    return;
  }
  if (!document.getElementById('login-page')?.classList.contains('active')) return;

  const screen = authScreenFromHash();
  setAuthScreen(screen);
  loginAlert('');
  signupAlert('');
  focusAuthField(screen === 'signup' ? 'signup-name' : 'login-email');
});

/* ── Boot ────────────────────────────────────────────────────────── */
async function boot() {
  const requestedScreen = authScreenFromHash();
  if (!Auth.isLoggedIn()) {
    showLogin();
    if (requestedScreen === 'signup') {
      setAuthScreen('signup');
      focusAuthField('signup-name');
    }
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

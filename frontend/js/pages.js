/* ══════════════════════════════════════════════════
   pages.js  –  All page renderers and modals
   ══════════════════════════════════════════════════ */

let woPage = 0, woSearch = '', woStatus = '', woPriority = '', woView = 'table';
let woSort = { field: 'createdAt', dir: 'desc' };
let myJobsFilter = 'active';
let custPage = 0, custSearch = '';
let custSort = { field: 'name', dir: 'asc' };
let partsPage = 0, partsSearch = '';
let partsSort = { field: 'name', dir: 'asc' };
let usersQuery = '';
let usersSort = { field: 'name', dir: 'asc' };
let dashFilters = { customerId: '', status: '' };
let reportFilters = { customerId: '', technicianId: '', status: '', from: '', until: '' };
let usersCache = [];
let usersOrgMap = {};
let photoUrls = [];
let currentWO = null;
let pendingTransition = null;

function isStaff() { return Auth.hasRole('MANAGER', 'DISPATCHER'); }
function isManager() { return Auth.hasRole('MANAGER'); }
function canWorkOn(w) {
  const u = Auth.getUser();
  if (!u || u.role === 'CUSTOMER') return false;
  return u.role !== 'TECHNICIAN' || w.assignedTechnicianId === u.id;
}

function refreshOpenPages() {
  if (document.getElementById('page-workorders')?.classList.contains('active')) renderWOContent();
  if (document.getElementById('page-myjobs')?.classList.contains('active')) loadMyJobs();
  if (document.getElementById('page-portal')?.classList.contains('active')) loadPortal();
  if (document.getElementById('page-dashboard')?.classList.contains('active')) refreshDashboard();
  if (document.getElementById('page-reports')?.classList.contains('active')) refreshReports();
}

/* ── Shared table / chart helpers ──────────────────────────────── */
const STATUS_COLORS = {
  NEW: '#94a3b8', ASSIGNED: '#60a5fa', IN_PROGRESS: '#818cf8',
  ON_HOLD: '#fbbf24', COMPLETED: '#34d399', CLOSED: '#64748b', CANCELLED: '#fb7185',
};
const PRIORITY_COLORS = { LOW: '#34d399', MEDIUM: '#60a5fa', HIGH: '#fbbf24', CRITICAL: '#fb7185' };

function sortParam(state) { return `${state.field},${state.dir}`; }

function sortTh(label, field, state, handler) {
  const active = state.field === field;
  const sorted = active ? ' sorted' : '';
  const aria = active ? (state.dir === 'asc' ? 'ascending' : 'descending') : 'none';
  const ind = active ? (state.dir === 'asc' ? '▲' : '▼') : '↕';
  const invoke = `${handler}('${field}')`;
  return `<th class="th-sort${sorted}" scope="col" tabindex="0" aria-sort="${aria}"
    onclick="${invoke}"
    onkeydown="if(event.key==='Enter'||event.key===' '){event.preventDefault();${invoke}}">${label}<span class="sort-ind" aria-hidden="true">${ind}</span></th>`;
}

function barRows(entries, total, colors) {
  const safe = total || 1;
  const rows = entries.filter(([, value]) => Number(value) > 0);
  if (!rows.length) return '<p class="text-muted text-sm">No data for this filter.</p>';
  return rows.map(([label, value]) => {
    const pct = Math.min(100, Number(value) / safe * 100);
    const color = (colors && colors[label]) || 'var(--violet)';
    return `<div class="sla-row" data-tip="${esc(label)} — ${value} (${pct.toFixed(1)}%)">
      <span class="sla-label" title="${esc(label)}">${esc(label)}</span>
      <div class="sla-track"><div class="sla-fill" style="background:${color}" data-width="${pct.toFixed(1)}"></div></div>
      <span class="sla-count">${value}</span>
    </div>`;
  }).join('');
}

function donutCard(pct) {
  const clamped = Math.max(0, Math.min(100, Number(pct) || 0));
  const tone = clamped >= 90 ? '' : clamped >= 70 ? ' warn' : ' danger';
  return `<div class="donut${tone}">
    <svg viewBox="0 0 42 42" aria-hidden="true">
      <circle class="donut-track" cx="21" cy="21" r="15.9155" pathLength="100"></circle>
      <circle class="donut-value" cx="21" cy="21" r="15.9155" pathLength="100" data-donut="${clamped.toFixed(1)}"></circle>
    </svg>
    <div class="donut-center">
      <span class="donut-pct" data-count="${clamped}" data-decimals="1" data-suffix="%">0%</span>
      <span class="donut-caption">SLA met</span>
    </div>
  </div>`;
}

function legendRow(color, label, value, total) {
  const pct = total ? (value / total * 100).toFixed(1) : '0.0';
  return `<div class="sla-legend-row">
    <span class="legend-dot" style="background:${color}"></span>
    <span class="legend-label">${esc(label)}</span>
    <span class="legend-value">${value} <span class="text-muted" style="font-weight:500">· ${pct}%</span></span>
  </div>`;
}

function chartSkeleton() {
  return `<div class="sk-charts">
    ${Array.from({ length: 2 }).map(() => `<div class="sk sk-card">
      <span class="sk sk-line w-40"></span>
      ${Array.from({ length: 5 }).map(() => '<span class="sk sk-line w-100"></span>').join('')}
    </div>`).join('')}
  </div>`;
}

function filterChip(labelHtml, clearFn) {
  return `<span class="filter-chip">${labelHtml}
    <button type="button" onclick="${clearFn}()" aria-label="Remove filter">${ICONS.close}</button>
  </span>`;
}

/* ═══════════════════════ DASHBOARD ═══════════════════════════════ */
async function loadDashboard() {
  const container = document.getElementById('page-dashboard');
  if (!container) return;
  if (!isStaff()) {
    navigate(Auth.hasRole('CUSTOMER') ? 'portal' : 'myjobs');
    return;
  }

  container.innerHTML = `
    <div class="page-header">
      <div>
        <p class="page-eyebrow">Operations</p>
        <h2 class="page-heading">Dashboard</h2>
        <p class="page-sub">Live overview of workload, SLAs and field activity</p>
      </div>
      <button class="btn btn-secondary btn-sm" type="button" onclick="refreshDashboard(this)">${ICONS.refresh} Refresh</button>
    </div>
    <div class="filter-row">
      <select class="form-select filter-select" id="dash-customer" aria-label="Filter by customer"
              onchange="dashFilters.customerId=this.value;refreshDashboard()">
        <option value="">All customers</option>
      </select>
      <select class="form-select filter-select" id="dash-status" aria-label="Filter by status"
              onchange="dashFilters.status=this.value;refreshDashboard()">
        <option value="">All statuses</option>
        ${Object.keys(STATUS_LABEL).map(s => `<option value="${s}"${dashFilters.status === s ? ' selected' : ''}>${STATUS_LABEL[s]}</option>`).join('')}
      </select>
      <button class="btn btn-ghost btn-sm" type="button" onclick="clearDashFilters()">Clear filters</button>
    </div>
    <div id="dash-stats" class="stats-grid mb-5"></div>
    <div class="dashboard-grid">
      <div class="card" id="dash-status-card"></div>
      <div class="card" id="dash-sla-card"></div>
    </div>
    <div class="dashboard-grid">
      <div class="card" id="dash-site"></div>
      <div class="card" id="dash-tech"></div>
    </div>
    <div class="card mt-3" id="dash-recent"></div>`;

  try {
    const data = await api.getCustomers({ size: 200 });
    const select = document.getElementById('dash-customer');
    (data.content || []).forEach(c => {
      const opt = document.createElement('option');
      opt.value = c.id;
      opt.textContent = c.name;
      if (dashFilters.customerId === c.id) opt.selected = true;
      select.appendChild(opt);
    });
  } catch { /* the dashboard can still render without the customer list */ }

  await refreshDashboard();
}

function clearDashFilters() {
  dashFilters = { customerId: '', status: '' };
  const cust = document.getElementById('dash-customer');
  const status = document.getElementById('dash-status');
  if (cust) cust.value = '';
  if (status) status.value = '';
  refreshDashboard();
}

async function refreshDashboard(btn) {
  const stats = document.getElementById('dash-stats');
  if (!stats) return;
  if (btn) setBtnBusy(btn, true, 'Refreshing…');
  if (!stats.dataset.loaded) stats.innerHTML = skeletonHTML('stats');

  try {
    const params = {};
    if (dashFilters.customerId) params.customerId = dashFilters.customerId;
    if (dashFilters.status) params.status = dashFilters.status;
    const s = await api.getSummary(params);
    const total = s.totalWorkOrders || 0;
    const safeTotal = total || 1;

    const cards = [
      { label: 'Total work orders', value: total, icon: 'workorders', tone: 'violet', onClick: "navigate('workorders')" },
      { label: 'SLA compliance', value: Number(s.slaCompliancePercent) || 0, decimals: 1, suffix: '%', icon: 'reports', tone: 'green', onClick: "navigate('reports')" },
      { label: 'SLA breached', value: s.slaBreachedCount || 0, icon: 'alert', tone: 'red' },
      { label: 'At risk', value: s.slaAtRiskCount || 0, icon: 'clock', tone: 'amber' },
      { label: 'On track', value: s.slaOnTrackCount || 0, icon: 'checkCircle', tone: 'cyan' },
      { label: 'Overdue', value: s.overdueCount || 0, icon: 'clock', tone: 'amber' },
    ];

    stats.innerHTML = cards.map(c => {
      const clickable = c.onClick
        ? ` role="button" tabindex="0" onclick="${c.onClick}" onkeydown="if(event.key==='Enter'||event.key===' '){event.preventDefault();${c.onClick}}"`
        : '';
      return `<div class="stat-card" data-tone="${c.tone}"${clickable}>
        <span class="stat-top">
          <span class="stat-icon ${c.tone}">${ICONS[c.icon] || ''}</span>
          ${c.onClick ? `<span class="stat-arrow">${ICONS.arrowRight}</span>` : ''}
        </span>
        <span class="stat-value" data-count="${c.value}" data-decimals="${c.decimals || 0}"${c.suffix ? ` data-suffix="${c.suffix}"` : ''}>0</span>
        <span class="stat-label">${c.label}</span>
      </div>`;
    }).join('');
    stats.dataset.loaded = '1';

    const byStatus = Object.entries(s.byStatus || {});
    const statusCard = document.getElementById('dash-status-card');
    if (statusCard) {
      statusCard.innerHTML = `
        <div class="card-header">
          <div><h3 class="card-title">Workflow distribution</h3>
          <p class="card-subtitle">Where every order sits right now</p></div>
        </div>
        ${byStatus.length ? `
          <div class="status-stack" role="img" aria-label="Work orders by status">
            ${byStatus.map(([st, cnt]) => `<div class="status-seg" title="${esc(STATUS_LABEL[st] || st)}: ${cnt}"
              style="background:${STATUS_COLORS[st] || '#94a3b8'}" data-width="${(cnt / safeTotal * 100).toFixed(2)}"></div>`).join('')}
          </div>
          <div class="legend-grid">
            ${byStatus.map(([st, cnt]) => `<div class="legend-item">
              <span class="legend-dot" style="background:${STATUS_COLORS[st] || '#94a3b8'}"></span>
              <span class="legend-label">${esc(STATUS_LABEL[st] || st)}</span>
              <span class="legend-value">${cnt}</span>
            </div>`).join('')}
          </div>` : emptyState('workorders', 'Nothing in the pipeline', 'New work orders will appear here.')}`;
    }

    const slaCard = document.getElementById('dash-sla-card');
    if (slaCard) {
      slaCard.innerHTML = `
        <div class="card-header">
          <div><h3 class="card-title">SLA performance</h3>
          <p class="card-subtitle">Service-level agreement health</p></div>
        </div>
        <div class="sla-hero">
          ${donutCard(s.slaCompliancePercent)}
          <div class="sla-legend">
            ${legendRow('var(--green)', 'On track', s.slaOnTrackCount || 0, safeTotal)}
            ${legendRow('var(--amber)', 'At risk', s.slaAtRiskCount || 0, safeTotal)}
            ${legendRow('var(--red)', 'Breached', s.slaBreachedCount || 0, safeTotal)}
          </div>
        </div>
        <div class="divider"></div>
        <div class="flex justify-between items-center mb-3">
          <span class="text-muted text-sm">Overdue open orders</span>
          <span class="badge ${s.overdueCount ? 'badge-critical' : 'badge-completed'}">${s.overdueCount || 0}</span>
        </div>
        ${barRows([
          ['On track', s.slaOnTrackCount || 0],
          ['At risk', s.slaAtRiskCount || 0],
          ['Breached', s.slaBreachedCount || 0],
        ], safeTotal, { 'On track': 'var(--green)', 'At risk': 'var(--amber)', 'Breached': 'var(--red)' })}`;
    }

    const siteCard = document.getElementById('dash-site');
    if (siteCard) {
      siteCard.innerHTML = `
        <div class="card-header">
          <div><h3 class="card-title">Workload by site</h3>
          <p class="card-subtitle">Top locations by order volume</p></div>
        </div>
        ${barRows(Object.entries(s.bySite || {}), safeTotal, { Unassigned: 'var(--text-3)' })}`;
    }

    const techCard = document.getElementById('dash-tech');
    if (techCard) {
      techCard.innerHTML = `
        <div class="card-header">
          <div><h3 class="card-title">Workload by technician</h3>
          <p class="card-subtitle">Assignment balance across the team</p></div>
        </div>
        ${barRows(Object.entries(s.byTechnician || {}), safeTotal, { Unassigned: 'var(--text-3)' })}`;
    }

    animateVisuals(document.getElementById('page-dashboard'));
    await loadRecentActivity();
  } catch (e) {
    stats.innerHTML = `<div class="alert alert-error span-all" role="alert"><span class="alert-ico">${ICONS.xCircle}</span><span>${esc(e.message)}</span></div>`;
  } finally {
    if (btn) setBtnBusy(btn, false);
  }
}

async function loadRecentActivity() {
  const card = document.getElementById('dash-recent');
  if (!card) return;
  card.innerHTML = `<div class="card-header">
      <div><h3 class="card-title">Recent activity</h3><p class="card-subtitle">Latest work orders across all customers</p></div>
    </div>
    ${skeletonHTML('table')}`;
  try {
    const data = await api.getWorkOrders({ size: 6, sort: 'createdAt,desc' });
    const items = data.content || [];
    card.innerHTML = `
      <div class="card-header">
        <div><h3 class="card-title">Recent activity</h3><p class="card-subtitle">Latest work orders across all customers</p></div>
        <button class="btn btn-ghost btn-sm" type="button" onclick="navigate('workorders')">View all ${ICONS.arrowRight}</button>
      </div>
      ${items.length ? `<div class="recent-list">${items.map(w => `
        <button class="recent-item" type="button" onclick="openWODetail('${w.id}')">
          <span class="recent-main">
            <span class="recent-title">${esc(w.title)}</span>
            <span class="recent-meta"><span class="code-chip">${esc(w.code)}</span> ${esc(w.customerName || '—')} • ${esc(w.siteName || '—')}</span>
          </span>
          <span class="recent-right">${statusBadge(w.status)}<span class="recent-time">${timeAgo(w.createdAt)}</span></span>
        </button>`).join('')}</div>`
      : emptyState('workorders', 'No activity yet', 'New work orders will show up here.')}`;
  } catch (e) {
    card.innerHTML = `<div class="card-header"><h3 class="card-title">Recent activity</h3></div>
      <div class="alert alert-error" role="alert"><span class="alert-ico">${ICONS.xCircle}</span><span>${esc(e.message)}</span></div>`;
  }
}

/* ═══════════════════════ WORK ORDERS ══════════════════════════════ */
function loadWorkOrders() {
  const container = document.getElementById('page-workorders');
  if (!container) return;
  const canCreate = isStaff();

  container.innerHTML = `
    <div class="page-header">
      <div>
        <p class="page-eyebrow">Service delivery</p>
        <h2 class="page-heading">Work Orders</h2>
        <p class="page-sub">Track, assign and close field service tasks</p>
      </div>
      <div class="flex gap-2 wrap">
        <div class="segmented" role="group" aria-label="View mode">
          <button class="seg-btn${woView === 'table' ? ' active' : ''}" type="button" aria-pressed="${woView === 'table'}" onclick="setWOView('table')">${ICONS.list} List</button>
          <button class="seg-btn${woView === 'kanban' ? ' active' : ''}" type="button" aria-pressed="${woView === 'kanban'}" onclick="setWOView('kanban')">${ICONS.columns} Board</button>
        </div>
        ${canCreate ? `<button class="btn btn-primary btn-sm" type="button" onclick="openCreateWOModal()">${ICONS.plus} New Work Order</button>` : ''}
      </div>
    </div>
    <div class="filter-row">
      <div class="search-wrap">${ICONS.search}<input class="form-input" id="wo-search" type="search" placeholder="Search code, title or customer…" value="${esc(woSearch)}" aria-label="Search work orders" oninput="onWOSearch(this.value)"></div>
      <select class="form-select filter-select" aria-label="Filter by status" onchange="onWOFilter('status', this.value)">
        <option value="">All statuses</option>
        ${Object.keys(STATUS_LABEL).map(s => `<option value="${s}"${woStatus === s ? ' selected' : ''}>${STATUS_LABEL[s]}</option>`).join('')}
      </select>
      <select class="form-select filter-select" aria-label="Filter by priority" onchange="onWOFilter('priority', this.value)">
        <option value="">All priorities</option>
        ${['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'].map(p => `<option value="${p}"${woPriority === p ? ' selected' : ''}>${p}</option>`).join('')}
      </select>
    </div>
    <div id="wo-filter-chips" class="flex gap-2 wrap mb-3" aria-live="polite"></div>
    <div id="wo-content"></div>`;

  renderWOContent();
}

function setWOView(view) {
  if (woView === view) return;
  woView = view;
  woPage = 0;
  loadWorkOrders();
}

function onWOSearch(value) {
  woSearch = value;
  woPage = 0;
  scheduleSearch('wo', renderWOContent);
}

function onWOFilter(kind, value) {
  if (kind === 'status') woStatus = value;
  else woPriority = value;
  woPage = 0;
  renderWOContent();
}

function clearWOSearch() {
  woSearch = '';
  const input = document.getElementById('wo-search');
  if (input) input.value = '';
  woPage = 0;
  renderWOContent();
}

function clearWOStatus() {
  woStatus = '';
  woPage = 0;
  loadWorkOrders();
}

function clearWOPriority() {
  woPriority = '';
  woPage = 0;
  loadWorkOrders();
}

function clearWOFilters() {
  woSearch = '';
  woStatus = '';
  woPriority = '';
  woPage = 0;
  loadWorkOrders();
}

function setWOSort(field) {
  woSort = woSort.field === field
    ? { field, dir: woSort.dir === 'asc' ? 'desc' : 'asc' }
    : { field, dir: field === 'createdAt' ? 'desc' : 'asc' };
  woPage = 0;
  renderWOContent();
}

function renderWOChips() {
  const box = document.getElementById('wo-filter-chips');
  if (!box) return;
  const chips = [];
  if (woSearch) chips.push(filterChip(`Search: “${esc(woSearch)}”`, 'clearWOSearch'));
  if (woStatus) chips.push(filterChip(esc(STATUS_LABEL[woStatus] || woStatus), 'clearWOStatus'));
  if (woPriority) chips.push(filterChip(esc(woPriority), 'clearWOPriority'));
  box.innerHTML = chips.join('');
}

async function renderWOContent() {
  const container = document.getElementById('wo-content');
  if (!container) return;
  renderWOChips();
  setLoading(container);
  try {
    const kanban = woView === 'kanban';
    const params = { page: kanban ? 0 : woPage, size: kanban ? 100 : 15, sort: sortParam(woSort) };
    if (woSearch) params.search = woSearch;
    if (woStatus) params.status = woStatus;
    if (woPriority) params.priority = woPriority;
    const data = await api.getWorkOrders(params);
    const items = data.content || [];

    if (!items.length) {
      const hasFilters = !!(woSearch || woStatus || woPriority);
      container.innerHTML = emptyState(
        'workorders',
        hasFilters ? 'No matching work orders' : 'No work orders yet',
        hasFilters ? 'Try a different search or clear the active filters.' : 'Create the first work order to get started.',
        hasFilters
          ? `<button class="btn btn-secondary btn-sm" type="button" onclick="clearWOFilters()">Clear filters</button>`
          : (isStaff() ? `<button class="btn btn-primary btn-sm" type="button" onclick="openCreateWOModal()">${ICONS.plus} New Work Order</button>` : '')
      );
      return;
    }

    if (kanban) renderKanban(container, items);
    else renderWOTable(container, items, data);
  } catch (e) { setError(container, e.message); }
}

function renderWOTable(container, items, data) {
  const customerView = Auth.hasRole('CUSTOMER');
  container.innerHTML = `
    <div class="table-wrap table-wrap--cards">
      <table>
        <thead><tr>
          ${sortTh('Code', 'code', woSort, 'setWOSort')}
          ${sortTh('Title', 'title', woSort, 'setWOSort')}
          ${customerView ? '' : '<th scope="col">Customer</th>'}
          <th scope="col">Site</th>
          ${customerView ? '' : '<th scope="col">Technician</th>'}
          ${sortTh('Priority', 'priority', woSort, 'setWOSort')}
          ${sortTh('Status', 'status', woSort, 'setWOSort')}
          ${sortTh('SLA due', 'slaDueDate', woSort, 'setWOSort')}
          <th scope="col">SLA</th>
          <th scope="col"><span class="sr-only">Actions</span></th>
        </tr></thead>
        <tbody>
          ${items.map(w => {
            const late = isOverdue(w.slaDueDate) && !['CLOSED', 'CANCELLED', 'COMPLETED'].includes(w.status);
            return `<tr>
              <td data-label="Code"><span class="code-chip">${esc(w.code)}</span></td>
              <td class="td-title cell-clamp" data-label="Title">${esc(w.title)}</td>
              ${customerView ? '' : `<td class="text-muted" data-label="Customer">${esc(w.customerName || '—')}</td>`}
              <td class="text-muted" data-label="Site">${esc(w.siteName || '—')}</td>
              ${customerView ? '' : `<td class="text-muted" data-label="Technician">${w.assignedTechnicianName ? esc(w.assignedTechnicianName) : '<span class="text-muted">Unassigned</span>'}</td>`}
              <td data-label="Priority">${priorityBadge(w.priority)}</td>
              <td data-label="Status">${statusBadge(w.status)}</td>
              <td class="${late ? 'text-red' : 'text-muted'}" data-label="SLA due">${fmtDate(w.slaDueDate)}</td>
              <td data-label="SLA">${slaBadge(w.slaStatus)}</td>
              <td data-label=""><div class="table-actions">
                <button class="btn btn-ghost btn-sm" type="button" onclick="openWODetail('${w.id}')" title="View work order">${ICONS.eye}<span class="sr-only">View</span></button>
              </div></td>
            </tr>`;
          }).join('')}
        </tbody>
      </table>
    </div>
    ${buildPagination(data.number, data.totalPages, (p) => { woPage = p; renderWOContent(); })}`;
}

function renderKanban(container, items) {
  const cols = ['NEW', 'ASSIGNED', 'IN_PROGRESS', 'ON_HOLD', 'COMPLETED', 'CLOSED'];
  const groups = {};
  cols.forEach(c => { groups[c] = []; });
  items.forEach(w => { if (groups[w.status]) groups[w.status].push(w); });

  container.innerHTML = `<div class="kanban-board">
    ${cols.map(col => `
      <div class="kanban-col">
        <div class="kanban-col-header">
          <span class="kanban-col-title">${STATUS_LABEL[col]}</span>
          <span class="kanban-count">${groups[col].length}</span>
        </div>
        <div class="kanban-cards">
          ${groups[col].length ? groups[col].map(w => `
            <button class="kanban-card" type="button" onclick="openWODetail('${w.id}')">
              <div class="kanban-card-code">${esc(w.code)}</div>
              <div class="kanban-card-title">${esc(w.title)}</div>
              <div class="kanban-card-meta">
                ${priorityBadge(w.priority)}
                ${slaBadge(w.slaStatus)}
              </div>
              ${w.assignedTechnicianName ? `<div class="recent-meta" style="margin-top:8px">${ICONS.user} ${esc(w.assignedTechnicianName)}</div>` : ''}
            </button>`).join('') : '<div class="text-muted text-sm" style="padding:20px 8px;text-align:center">Nothing here</div>'}
        </div>
      </div>`).join('')}
  </div>`;
}

/* ── Work order detail ─────────────────────────────────────────── */
async function openWODetail(id) {
  const body = document.getElementById('wo-detail-body');
  if (!body) return;
  closeLightbox();
  revokePhotoUrls();
  setLoading(body, 'Loading work order…', 'detail');
  openModal('modal-wo-detail');
  try {
    const w = await api.getWorkOrder(id);
    currentWO = w;
    const user = Auth.getUser();
    const customerView = user.role === 'CUSTOMER';
    const staff = isStaff();
    const terminal = ['CLOSED', 'CANCELLED'].includes(w.status);
    const canWork = canWorkOn(w) && !terminal;
    const canDeletePhoto = (a) => isManager() ||
      a.uploadedByName === `${user.firstName || ''} ${user.lastName || ''}`.trim();

    body.innerHTML = `
      <div class="wo-head">
        <div class="wo-head-main">
          <span class="wo-head-code">${esc(w.code)}</span>
          <h3 class="wo-head-title">${esc(w.title)}</h3>
          <div class="wo-head-badges">
            ${statusBadge(w.status)} ${priorityBadge(w.priority)} ${slaBadge(w.slaStatus)}
          </div>
        </div>
        <div class="wo-actions">
          ${!terminal && !customerView && staff ? `<button class="btn btn-secondary btn-sm" type="button" onclick="openAssignModal('${w.id}')">${ICONS.user} ${w.assignedTechnicianId ? 'Reassign' : 'Assign'}</button>` : ''}
          ${!terminal && !customerView && canWork ? `
            <button class="btn btn-secondary btn-sm" type="button" onclick="openLogTimeModal('${w.id}')">${ICONS.clock} Log time</button>
            <button class="btn btn-secondary btn-sm" type="button" onclick="openAddPartModal('${w.id}')">${ICONS.parts} Add part</button>` : ''}
          ${!terminal && !customerView ? buildStatusButtons(w) : ''}
        </div>
      </div>

      <div class="detail-grid">
        <div class="detail-field"><label>Customer</label><span>${esc(w.customerName || '—')}</span></div>
        <div class="detail-field"><label>Site</label><span>${esc(w.siteName || '—')}</span></div>
        ${customerView ? '' : `<div class="detail-field"><label>Technician</label><span>${esc(w.assignedTechnicianName || 'Unassigned')}</span></div>`}
        <div class="detail-field"><label>SLA due</label><span class="${isOverdue(w.slaDueDate) ? 'text-red' : ''}">${fmtDateTime(w.slaDueDate)}</span></div>
        <div class="detail-field"><label>Created</label><span>${fmtDateTime(w.createdAt)}</span></div>
        ${customerView ? '' : `<div class="detail-field"><label>Time logged</label><span>${fmtMinutes(w.totalMinutesLogged)}</span></div>`}
        ${w.description ? `<div class="detail-note">${esc(w.description)}</div>` : ''}
      </div>

      <div class="tabs" role="tablist" aria-label="Work order sections">
        <button class="tab-btn active" type="button" role="tab" aria-selected="true" onclick="switchWOTab(this,'tab-history')">History</button>
        <button class="tab-btn" type="button" role="tab" aria-selected="false" onclick="switchWOTab(this,'tab-parts')">Parts used</button>
        ${customerView ? '' : `<button class="tab-btn" type="button" role="tab" aria-selected="false" onclick="switchWOTab(this,'tab-time')">Time logs</button>`}
        <button class="tab-btn" type="button" role="tab" aria-selected="false" onclick="switchWOTab(this,'tab-photos')">Photos (${w.attachments?.length || 0})</button>
      </div>

      <div class="tab-panel" id="tab-history" role="tabpanel">
        ${w.statusHistory?.length ? `<div class="timeline">
          ${[...w.statusHistory].reverse().map(h => `
            <div class="tl-item">
              <div class="tl-dot">${ICONS.refresh}</div>
              <div class="tl-content">
                <div class="tl-action">${h.previousStatus ? `${STATUS_LABEL[h.previousStatus] || esc(h.previousStatus)} → ` : ''}${statusBadge(h.newStatus)}</div>
                <div class="tl-meta">by ${esc(h.changedByUserName || 'system')} • ${fmtDateTime(h.changedAt)}</div>
                ${h.note ? `<div class="tl-note">${esc(h.note)}</div>` : ''}
              </div>
            </div>`).join('')}
        </div>` : '<p class="text-muted text-sm">No history yet.</p>'}
      </div>

      <div class="tab-panel" id="tab-parts" role="tabpanel" hidden>
        ${w.partUsages?.length ? `<div class="table-wrap"><table>
          <thead><tr><th scope="col">Part</th><th scope="col">Qty</th>${customerView ? '' : '<th scope="col">Unit cost</th>'}<th scope="col">Date</th></tr></thead>
          <tbody>${w.partUsages.map(p => `<tr>
            <td class="td-title">${esc(p.partName)} <span class="text-muted" style="font-size:11px">(${esc(p.partNumber)})</span></td>
            <td>${p.quantityUsed}</td>
            ${customerView ? '' : `<td>${fmtMoney(p.unitCostAtTime)}</td>`}
            <td class="text-muted">${fmtDate(p.addedAt)}</td>
          </tr>`).join('')}</tbody>
        </table></div>` : '<p class="text-muted text-sm">No parts recorded.</p>'}
      </div>

      ${customerView ? '' : `<div class="tab-panel" id="tab-time" role="tabpanel" hidden>
        ${w.timeLogs?.length ? `<div class="table-wrap"><table>
          <thead><tr><th scope="col">Technician</th><th scope="col">Minutes</th><th scope="col">Date</th><th scope="col">Notes</th></tr></thead>
          <tbody>${w.timeLogs.map(t => `<tr>
            <td class="td-title">${esc(t.technicianName)}</td>
            <td>${fmtMinutes(t.minutesLogged)}</td>
            <td class="text-muted">${fmtDate(t.loggedAt)}</td>
            <td class="text-muted">${esc(t.notes || '—')}</td>
          </tr>`).join('')}</tbody>
        </table></div>` : '<p class="text-muted text-sm">No time logged.</p>'}
      </div>`}

      <div class="tab-panel" id="tab-photos" role="tabpanel" hidden>
        ${canWork ? `<div class="upload-zone" role="button" tabindex="0" onclick="openPhotoPicker('${w.id}')"
          onkeydown="if(event.key==='Enter'||event.key===' '){event.preventDefault();openPhotoPicker('${w.id}')}">
          ${ICONS.camera} Click to attach a photo (JPEG, PNG, WEBP or GIF — max 5 MB)
        </div>` : ''}
        ${w.attachments?.length ? `<div class="attach-grid">
          ${w.attachments.map(a => `
            <div class="attach-card" data-name="${esc(a.fileName)}">
              <img data-attachment="${a.id}" alt="${esc(a.fileName)}" tabindex="0" role="button"
                   onclick="openPhotoLightbox('${a.id}')"
                   onkeydown="if(event.key==='Enter'){openPhotoLightbox('${a.id}')}">
              <div class="attach-actions">
                <button class="icon-btn" type="button" title="Download" aria-label="Download ${esc(a.fileName)}" onclick="downloadPhoto('${w.id}','${a.id}',this.closest('.attach-card').dataset.name)">${ICONS.download}</button>
                ${canDeletePhoto(a) ? `<button class="icon-btn danger" type="button" title="Delete" aria-label="Delete ${esc(a.fileName)}" onclick="deletePhoto('${w.id}','${a.id}')">${ICONS.trash}</button>` : ''}
              </div>
              <div class="attach-meta"><span>${esc(a.fileName)}</span><span>${a.sizeBytes ? Math.max(1, Math.round(a.sizeBytes / 1024)) + ' KB' : ''}</span></div>
            </div>`).join('')}
        </div>` : '<p class="text-muted text-sm">No photos attached.</p>'}
      </div>`;

    document.getElementById('modal-wo-detail').dataset.woId = id;
    loadPhotoThumbs(w.id);
  } catch (e) { setError(body, e.message); }
}

function buildStatusButtons(w) {
  const user = Auth.getUser();
  const role = user?.role;
  const isAssignedTech = role === 'TECHNICIAN' && w.assignedTechnicianId === user?.id;
  const allowed = {
    NEW: ['CANCELLED'],
    ASSIGNED: ['IN_PROGRESS', 'CANCELLED'],
    IN_PROGRESS: ['ON_HOLD', 'COMPLETED', 'CANCELLED'],
    ON_HOLD: ['IN_PROGRESS', 'CANCELLED'],
    COMPLETED: ['CLOSED', 'IN_PROGRESS'],
  };
  const meta = {
    IN_PROGRESS: { cls: 'btn-primary', icon: ICONS.play },
    ON_HOLD: { cls: 'btn-secondary', icon: null },
    COMPLETED: { cls: 'btn-success', icon: ICONS.check },
    CLOSED: { cls: 'btn-secondary', icon: ICONS.check },
    CANCELLED: { cls: 'btn-danger', icon: null },
  };
  const permitted = allowed[w.status] || [];
  return permitted.filter(s => {
    if (s === 'CANCELLED') return role === 'MANAGER' || role === 'DISPATCHER';
    if (s === 'CLOSED') return role === 'MANAGER';
    if (s === 'IN_PROGRESS' && w.status === 'COMPLETED') return role === 'MANAGER' || role === 'DISPATCHER';
    return isAssignedTech; // starting, pausing and completing are the assigned technician's job
  }).map(s => {
    const m = meta[s] || { cls: 'btn-secondary', icon: null };
    return `<button class="btn ${m.cls} btn-sm" type="button" onclick="doTransition('${w.id}','${s}')">${m.icon || ''} ${transitionLabel(w.status, s)}</button>`;
  }).join('');
}

function transitionLabel(current, next) {
  if (next === 'CANCELLED') return 'Cancel order';
  if (next === 'CLOSED') return 'Close order';
  if (next === 'COMPLETED') return 'Mark complete';
  if (next === 'ON_HOLD') return 'Put on hold';
  if (next === 'IN_PROGRESS') {
    if (current === 'ON_HOLD') return 'Resume job';
    if (current === 'COMPLETED') return 'Reopen job';
    return 'Start job';
  }
  return STATUS_LABEL[next] || 'Confirm';
}

function switchWOTab(btn, tabId) {
  const tabs = btn.closest('.tabs');
  if (tabs) tabs.querySelectorAll('.tab-btn').forEach(b => {
    b.classList.remove('active');
    b.setAttribute('aria-selected', 'false');
  });
  btn.classList.add('active');
  btn.setAttribute('aria-selected', 'true');
  const scope = btn.closest('.modal-body') || document;
  scope.querySelectorAll('.tab-panel').forEach(panel => { panel.hidden = panel.id !== tabId; });
}

/* ── Status transitions ────────────────────────────────────────── */
const NOTE_STATUSES = ['CANCELLED', 'CLOSED', 'COMPLETED', 'ON_HOLD'];
const TRANSITION_HINTS = {
  CANCELLED: 'Cancelling closes this order out. Let the team know why.',
  CLOSED: 'Closing finalises the order once the customer is satisfied.',
  COMPLETED: 'Summarise what was fixed before completing the job.',
  ON_HOLD: 'Note what is blocking progress so the next technician has context.',
};

function doTransition(id, newStatus) {
  if (NOTE_STATUSES.includes(newStatus)) {
    openTransitionModal(id, newStatus);
    return;
  }
  applyTransition(id, newStatus, null).catch(e => toast(e.message, 'error'));
}

function openTransitionModal(id, status) {
  pendingTransition = { id, status };
  document.getElementById('transition-title').textContent = `Mark as ${STATUS_LABEL[status] || status}`;
  document.getElementById('transition-desc').textContent = TRANSITION_HINTS[status] || 'Add an optional note for this status change.';
  document.getElementById('transition-note').value = '';
  document.getElementById('transition-alert').innerHTML = '';
  const submit = document.getElementById('transition-submit');
  submit.className = status === 'CANCELLED' ? 'btn btn-danger' : status === 'COMPLETED' ? 'btn btn-success' : 'btn btn-primary';
  submit.textContent = transitionLabel(currentWO?.id === id ? currentWO.status : null, status);
  openModal('modal-transition');
}

async function submitTransition() {
  if (!pendingTransition) return;
  const { id, status } = pendingTransition;
  const note = document.getElementById('transition-note').value.trim();
  const btn = document.getElementById('transition-submit');
  setBtnBusy(btn, true, 'Saving…');
  try {
    await applyTransition(id, status, note || null);
    closeModal('modal-transition');
    pendingTransition = null;
  } catch (e) {
    formError('transition-alert', e.message);
  } finally {
    setBtnBusy(btn, false);
  }
}

async function applyTransition(id, newStatus, note) {
  await api.transitionStatus(id, newStatus, note || undefined);
  toast(`Status updated to ${STATUS_LABEL[newStatus] || newStatus}`, 'success');
  const detailOpen = document.getElementById('modal-wo-detail')?.classList.contains('open');
  if (detailOpen && currentWO?.id === id) await openWODetail(id);
  if (!detailOpen) refreshOpenPages();
}

/* ── Photos ────────────────────────────────────────────────────── */
function revokePhotoUrls() {
  photoUrls.forEach(url => URL.revokeObjectURL(url));
  photoUrls = [];
}

async function loadPhotoThumbs(workOrderId) {
  const images = [...document.querySelectorAll('#wo-detail-body img[data-attachment]')];
  for (const img of images) {
    try {
      const blob = await api.downloadAttachment(workOrderId, img.dataset.attachment);
      const url = URL.createObjectURL(blob);
      photoUrls.push(url);
      img.src = url;
      img.classList.add('loaded');
    } catch {
      img.alt = 'Preview unavailable';
    }
  }
}

function openPhotoPicker(workOrderId) {
  const input = document.getElementById('photo-input');
  input.dataset.woId = workOrderId;
  input.value = '';
  input.click();
}

async function handlePhotoSelected(input) {
  const file = input.files && input.files[0];
  const workOrderId = input.dataset.woId;
  if (!file || !workOrderId) return;
  if (!['image/jpeg', 'image/png', 'image/webp', 'image/gif'].includes(file.type)) {
    toast('Only JPEG, PNG, WEBP or GIF images are accepted', 'error');
    input.value = '';
    return;
  }
  if (file.size > 5 * 1024 * 1024) {
    toast('Photo must be 5 MB or smaller', 'error');
    input.value = '';
    return;
  }
  const zone = document.querySelector('#wo-detail-body .upload-zone');
  if (zone) zone.classList.add('is-loading');
  try {
    await api.uploadAttachment(workOrderId, file);
    toast('Photo attached', 'success');
    await openWODetail(workOrderId);
    refreshOpenPages();
  } catch (e) {
    toast(e.message, 'error');
    if (zone) zone.classList.remove('is-loading');
  }
  input.value = '';
}

async function downloadPhoto(workOrderId, attachmentId, fileName) {
  try {
    const blob = await api.downloadAttachment(workOrderId, attachmentId);
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = fileName || 'photo';
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 3000);
  } catch (e) { toast(e.message, 'error'); }
}

async function deletePhoto(workOrderId, attachmentId) {
  if (!confirm('Delete this photo?')) return;
  try {
    await api.deleteAttachment(workOrderId, attachmentId);
    toast('Photo deleted', 'success');
    await openWODetail(workOrderId);
    refreshOpenPages();
  } catch (e) { toast(e.message, 'error'); }
}

function openPhotoLightbox(attachmentId) {
  const img = document.querySelector(`#wo-detail-body img[data-attachment="${attachmentId}"]`);
  if (!img || !img.src) return;
  const card = img.closest('.attach-card');
  const name = card?.dataset.name || 'Photo';
  const box = document.getElementById('photo-lightbox');
  document.getElementById('lightbox-img').src = img.src;
  document.getElementById('lightbox-img').alt = name;
  document.getElementById('lightbox-caption').textContent = name;
  document.getElementById('lightbox-download').onclick = () => {
    if (currentWO) downloadPhoto(currentWO.id, attachmentId, name);
  };
  box.classList.add('open');
  document.body.classList.add('modal-open');
}

function closeLightbox() {
  const box = document.getElementById('photo-lightbox');
  if (!box || !box.classList.contains('open')) return;
  box.classList.remove('open');
  document.getElementById('lightbox-img').src = '';
  document.getElementById('lightbox-download').onclick = null;
  if (!document.querySelector('.modal-overlay.open')) document.body.classList.remove('modal-open');
}

/* ── Create WO / customer request ──────────────────────────────── */
async function openCreateWOModal() {
  const body = document.getElementById('create-wo-body');
  const title = document.getElementById('create-wo-title');
  const user = Auth.getUser();
  setLoading(body, 'Preparing form…', 'form');
  openModal('modal-create-wo');

  if (user.role === 'CUSTOMER') {
    title.textContent = 'New Service Request';
    try {
      const [org, sites] = await Promise.all([api.getMyCustomer(), api.getAllSites(user.customerId)]);
      body.innerHTML = `
        <div class="alert alert-info" role="status">
          <span class="alert-ico">${ICONS.info}</span>
          <span>Requesting service for <strong>${esc(org.name)}</strong></span>
        </div>
        <div class="form-group"><label class="form-label" for="wo-site">Site *</label>
          <select class="form-select" id="wo-site" ${sites.length ? '' : 'disabled'}>
            <option value="">${sites.length ? 'Select site…' : 'No sites available'}</option>
            ${sites.map(s => `<option value="${s.id}">${esc(s.name)}${s.city ? ' — ' + esc(s.city) : ''}</option>`).join('')}
          </select>
          ${sites.length ? '' : '<p class="text-muted text-sm mt-1">Ask your account manager to add a site before raising a request.</p>'}
        </div>
        <div class="form-group"><label class="form-label" for="wo-title">What do you need? *</label>
          <input class="form-input" id="wo-title" placeholder="e.g. Air conditioning not cooling"></div>
        <div class="form-group"><label class="form-label" for="wo-desc">Details</label>
          <textarea class="form-textarea" id="wo-desc" placeholder="Location, symptoms, access instructions…"></textarea></div>
        <div class="form-group"><label class="form-label" for="wo-priority">Urgency *</label>
          <select class="form-select" id="wo-priority">
            ${['MEDIUM', 'HIGH', 'CRITICAL', 'LOW'].map(p => `<option value="${p}">${p}</option>`).join('')}
          </select></div>
        <div id="wo-form-alert"></div>`;
    } catch (e) { setError(body, e.message); }
    return;
  }

  title.textContent = 'New Work Order';
  try {
    const [custsData, techs] = await Promise.all([
      api.getCustomers({ size: 200 }),
      api.getTechnicians(),
    ]);
    const custs = custsData.content || [];
    body.innerHTML = `
      <div class="form-group"><label class="form-label" for="wo-cust">Customer *</label>
        <select class="form-select" id="wo-cust" onchange="loadSitesForWO(this.value)">
          <option value="">Select customer…</option>
          ${custs.map(c => `<option value="${c.id}">${esc(c.name)}</option>`).join('')}
        </select></div>
      <div class="form-group"><label class="form-label" for="wo-site">Site *</label>
        <select class="form-select" id="wo-site" disabled><option>Select customer first…</option></select></div>
      <div class="form-group"><label class="form-label" for="wo-title">Title *</label>
        <input class="form-input" id="wo-title" placeholder="Describe the task…"></div>
      <div class="form-group"><label class="form-label" for="wo-desc">Description</label>
        <textarea class="form-textarea" id="wo-desc" placeholder="Optional details…"></textarea></div>
      <div class="input-row">
        <div class="form-group"><label class="form-label" for="wo-priority">Priority *</label>
          <select class="form-select" id="wo-priority">
            ${['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'].map(p => `<option value="${p}"${p === 'MEDIUM' ? ' selected' : ''}>${p}</option>`).join('')}
          </select></div>
        <div class="form-group"><label class="form-label" for="wo-tech">Assign technician</label>
          <select class="form-select" id="wo-tech">
            <option value="">Unassigned</option>
            ${techs.map(t => `<option value="${t.id}">${esc(t.firstName)} ${esc(t.lastName)}</option>`).join('')}
          </select></div>
      </div>
      <div id="wo-form-alert"></div>`;
  } catch (e) { setError(body, e.message); }
}

async function loadSitesForWO(customerId) {
  const siteSelect = document.getElementById('wo-site');
  if (!siteSelect) return;
  if (!customerId) {
    siteSelect.disabled = true;
    siteSelect.innerHTML = '<option>Select customer first…</option>';
    return;
  }
  siteSelect.innerHTML = '<option>Loading…</option>';
  siteSelect.disabled = true;
  try {
    const sites = await api.getAllSites(customerId);
    siteSelect.innerHTML = '<option value="">Select site…</option>' +
      sites.map(s => `<option value="${s.id}">${esc(s.name)}</option>`).join('');
    siteSelect.disabled = false;
  } catch {
    siteSelect.innerHTML = '<option>Error loading sites</option>';
  }
}

async function submitCreateWO() {
  const user = Auth.getUser();
  const customerUser = user.role === 'CUSTOMER';
  const btn = document.getElementById('create-wo-submit');

  const body = {
    siteId: document.getElementById('wo-site')?.value || '',
    title: (document.getElementById('wo-title')?.value || '').trim(),
    description: document.getElementById('wo-desc')?.value || '',
    priority: document.getElementById('wo-priority')?.value || 'MEDIUM',
  };

  if (!customerUser) {
    body.customerId = document.getElementById('wo-cust')?.value || '';
    body.assignedTechnicianId = document.getElementById('wo-tech')?.value || null;
    if (!body.customerId) {
      formError('wo-form-alert', 'Please select a customer.', ['wo-cust']);
      return;
    }
  }
  if (!body.title) {
    formError('wo-form-alert', 'Give the work order a title.', ['wo-title']);
    return;
  }
  if (!body.siteId) {
    formError('wo-form-alert', 'Please choose the site where the work happens.', ['wo-site']);
    return;
  }

  setBtnBusy(btn, true, 'Creating…');
  try {
    await api.createWorkOrder(body);
    toast(customerUser ? 'Service request submitted!' : 'Work order created!', 'success');
    closeModal('modal-create-wo');
    if (customerUser) loadPortal();
    else if (document.getElementById('page-workorders')?.classList.contains('active')) {
      woPage = 0;
      renderWOContent();
    } else {
      navigate('workorders');
    }
  } catch (e) {
    formError('wo-form-alert', e.message, ['wo-title']);
  } finally {
    setBtnBusy(btn, false);
  }
}

/* ── Assign modal ──────────────────────────────────────────────── */
async function openAssignModal(woId) {
  const alertEl = document.getElementById('assign-alert');
  if (alertEl) alertEl.innerHTML = '';
  document.getElementById('assign-wo-id').value = woId;
  const sel = document.getElementById('assign-tech-select');
  sel.innerHTML = '<option>Loading…</option>';
  openModal('modal-assign');
  try {
    const techs = await api.getTechnicians();
    const current = currentWO?.assignedTechnicianId;
    sel.innerHTML = '<option value="">Select technician…</option>' +
      techs.map(t => `<option value="${t.id}"${t.id === current ? ' selected' : ''}>${esc(t.firstName)} ${esc(t.lastName)}</option>`).join('');
  } catch (e) {
    sel.innerHTML = '<option value="">Select technician…</option>';
    formError('assign-alert', e.message);
  }
}

async function submitAssign() {
  const woId = document.getElementById('assign-wo-id').value;
  const techId = document.getElementById('assign-tech-select').value;
  if (!techId) {
    formError('assign-alert', 'Please select a technician.', ['assign-tech-select']);
    return;
  }
  const btn = document.getElementById('assign-submit');
  setBtnBusy(btn, true, 'Assigning…');
  try {
    await api.assignTechnician(woId, techId);
    toast('Technician assigned', 'success');
    closeModal('modal-assign');
    await openWODetail(woId);
    refreshOpenPages();
  } catch (e) {
    formError('assign-alert', e.message);
  } finally {
    setBtnBusy(btn, false);
  }
}

/* ── Log time ──────────────────────────────────────────────────── */
function openLogTimeModal(woId) {
  document.getElementById('log-time-wo-id').value = woId;
  document.getElementById('log-minutes').value = '';
  document.getElementById('log-notes').value = '';
  document.getElementById('log-time-alert').innerHTML = '';
  openModal('modal-log-time');
}

async function submitLogTime() {
  const woId = document.getElementById('log-time-wo-id').value;
  const min = parseInt(document.getElementById('log-minutes').value, 10);
  if (!min || min < 1) {
    formError('log-time-alert', 'Enter the minutes spent (at least 1).', ['log-minutes']);
    return;
  }
  const btn = document.getElementById('log-time-submit');
  setBtnBusy(btn, true, 'Saving…');
  try {
    await api.logTime(woId, { minutesLogged: min, notes: document.getElementById('log-notes').value });
    toast('Time logged', 'success');
    closeModal('modal-log-time');
    await openWODetail(woId);
    refreshOpenPages();
  } catch (e) {
    formError('log-time-alert', e.message);
  } finally {
    setBtnBusy(btn, false);
  }
}

/* ── Add part ──────────────────────────────────────────────────── */
async function openAddPartModal(woId) {
  const sel = document.getElementById('part-select');
  sel.innerHTML = '<option>Loading…</option>';
  document.getElementById('part-wo-id').value = woId;
  document.getElementById('part-qty').value = '1';
  document.getElementById('part-notes').value = '';
  document.getElementById('add-part-alert').innerHTML = '';
  openModal('modal-add-part');
  try {
    const data = await api.getParts({ size: 200 });
    const parts = data.content || [];
    sel.innerHTML = '<option value="">Select part…</option>' +
      parts.map(p => `<option value="${p.id}">${esc(p.name)} (${esc(p.partNumber)}) — stock: ${p.quantityInStock}</option>`).join('');
  } catch (e) {
    sel.innerHTML = '<option value="">Select part…</option>';
    formError('add-part-alert', e.message);
  }
}

async function submitAddPart() {
  const woId = document.getElementById('part-wo-id').value;
  const partId = document.getElementById('part-select').value;
  const qty = parseInt(document.getElementById('part-qty').value, 10);
  if (!partId) { formError('add-part-alert', 'Choose the part you used.', ['part-select']); return; }
  if (!qty || qty < 1) { formError('add-part-alert', 'Quantity must be at least 1.', ['part-qty']); return; }
  const btn = document.getElementById('add-part-submit');
  setBtnBusy(btn, true, 'Recording…');
  try {
    await api.addPartUsage(woId, { partId, quantityUsed: qty, notes: document.getElementById('part-notes').value });
    toast('Part usage recorded', 'success');
    closeModal('modal-add-part');
    await openWODetail(woId);
    refreshOpenPages();
  } catch (e) {
    formError('add-part-alert', e.message);
  } finally {
    setBtnBusy(btn, false);
  }
}

/* ═══════════════════════ TECHNICIAN — MY JOBS ═════════════════════ */
async function loadMyJobs() {
  const container = document.getElementById('page-myjobs');
  if (!container) return;
  if (!Auth.hasRole('TECHNICIAN')) {
    navigate(isStaff() ? 'workorders' : 'portal');
    return;
  }

  container.innerHTML = `
    <div class="page-header">
      <div>
        <p class="page-eyebrow">Field work</p>
        <h2 class="page-heading">My Jobs</h2>
        <p class="page-sub">Work orders assigned to you</p>
      </div>
      <div class="flex gap-2 wrap">
        <div class="segmented" role="group" aria-label="Filter jobs">
          <button class="seg-btn${myJobsFilter === 'active' ? ' active' : ''}" type="button" aria-pressed="${myJobsFilter === 'active'}" onclick="setJobsFilter('active')">Active</button>
          <button class="seg-btn${myJobsFilter === 'all' ? ' active' : ''}" type="button" aria-pressed="${myJobsFilter === 'all'}" onclick="setJobsFilter('all')">All jobs</button>
        </div>
        <button class="btn btn-secondary btn-sm" type="button" onclick="loadMyJobs()">${ICONS.refresh} Refresh</button>
      </div>
    </div>
    <div id="myjobs-content"></div>`;

  const content = document.getElementById('myjobs-content');
  setLoading(content);
  try {
    const data = await api.getWorkOrders({ size: 100, sort: 'slaDueDate,asc' });
    const all = data.content || [];
    const open = all.filter(w => !['CLOSED', 'CANCELLED', 'COMPLETED'].includes(w.status));
    const dueToday = open.filter(w => {
      if (!w.slaDueDate) return false;
      const due = new Date(w.slaDueDate);
      const now = new Date();
      return due.toDateString() === now.toDateString();
    });
    const minutes = all.reduce((sum, w) => sum + (w.totalMinutesLogged || 0), 0);

    const items = myJobsFilter === 'active' ? open : all;

    content.innerHTML = `
      <div class="stats-grid mb-4">
        <div class="stat-card" data-tone="violet">
          <span class="stat-top"><span class="stat-icon violet">${ICONS.briefcase}</span></span>
          <span class="stat-value" data-count="${open.length}">0</span>
          <span class="stat-label">Open jobs</span>
        </div>
        <div class="stat-card" data-tone="amber">
          <span class="stat-top"><span class="stat-icon amber">${ICONS.clock}</span></span>
          <span class="stat-value" data-count="${dueToday.length}">0</span>
          <span class="stat-label">SLA due today</span>
        </div>
        <div class="stat-card" data-tone="green">
          <span class="stat-top"><span class="stat-icon green">${ICONS.checkCircle}</span></span>
          <span class="stat-value" data-count="${minutes}" data-suffix="m">0</span>
          <span class="stat-label">Time logged</span>
        </div>
      </div>
      ${items.length ? `<div class="job-grid">${items.map(w => {
        const actions = [
          `<button class="btn btn-secondary btn-sm" type="button" onclick="openWODetail('${w.id}')">${ICONS.eye} Details</button>`,
        ];
        if (w.status === 'ASSIGNED') actions.push(`<button class="btn btn-primary btn-sm" type="button" onclick="doTransition('${w.id}','IN_PROGRESS')">${ICONS.play} Start</button>`);
        if (w.status === 'IN_PROGRESS') {
          actions.push(`<button class="btn btn-success btn-sm" type="button" onclick="doTransition('${w.id}','COMPLETED')">${ICONS.check} Complete</button>`);
          actions.push(`<button class="btn btn-secondary btn-sm" type="button" onclick="doTransition('${w.id}','ON_HOLD')">Pause</button>`);
        }
        if (w.status === 'ON_HOLD') actions.push(`<button class="btn btn-primary btn-sm" type="button" onclick="doTransition('${w.id}','IN_PROGRESS')">${ICONS.play} Resume</button>`);
        return `
          <div class="job-card pri-${w.priority}">
            <div class="job-card-top">
              <span class="job-card-code">${esc(w.code)}</span>
              ${statusBadge(w.status)}
            </div>
            <div class="job-card-title">${esc(w.title)}</div>
            <div class="job-card-meta">
              <span>${ICONS.customers} ${esc(w.customerName || '—')} • ${esc(w.siteName || '—')}</span>
              <span>${ICONS.clock} SLA due ${fmtDateTime(w.slaDueDate)} ${slaBadge(w.slaStatus)}</span>
              <span>${priorityBadge(w.priority)} ${ICONS.clock} ${fmtMinutes(w.totalMinutesLogged)} logged</span>
            </div>
            <div class="job-card-actions">${actions.join('')}</div>
          </div>`;
      }).join('')}</div>` : emptyState(
        'briefcase',
        myJobsFilter === 'active' ? 'No active jobs' : 'No jobs assigned yet',
        myJobsFilter === 'active' ? 'Everything on your board is complete. Switch to “All jobs” to review history.' : 'New assignments will appear here.'
      )}`;

    animateVisuals(content);
  } catch (e) { setError(content, e.message); }
}

function setJobsFilter(filter) {
  if (myJobsFilter === filter) return;
  myJobsFilter = filter;
  loadMyJobs();
}

/* ═══════════════════════ CUSTOMER PORTAL ══════════════════════════ */
async function loadPortal() {
  const container = document.getElementById('page-portal');
  if (!container) return;
  if (!Auth.hasRole('CUSTOMER')) {
    navigate(isStaff() ? 'workorders' : 'myjobs');
    return;
  }
  setLoading(container);
  try {
    const [org, pageData] = await Promise.all([
      api.getMyCustomer(),
      api.getWorkOrders({ size: 100, sort: 'createdAt,desc' }),
    ]);
    const items = pageData.content || [];
    const open = items.filter(w => !['CLOSED', 'CANCELLED', 'COMPLETED'].includes(w.status));
    const done = items.filter(w => ['CLOSED', 'COMPLETED'].includes(w.status));
    const user = Auth.getUser();

    container.innerHTML = `
      <div class="portal-hero">
        <div>
          <p class="portal-eyebrow">Welcome back, ${esc((user.firstName || '').toUpperCase())}</p>
          <h2>${esc(org.name)}</h2>
          <p>${esc(org.address || org.contactEmail || 'Your service portal')}</p>
        </div>
        <button class="btn btn-primary" type="button" onclick="openCreateWOModal()">${ICONS.plus} New Service Request</button>
      </div>

      <div class="org-grid">
        <div class="org-item"><label>Contact email</label><span title="${esc(org.contactEmail || '—')}">${esc(org.contactEmail || '—')}</span></div>
        <div class="org-item"><label>Phone</label><span>${esc(org.contactPhone || '—')}</span></div>
        <div class="org-item"><label>Open requests</label><span data-count="${open.length}">0</span></div>
        <div class="org-item"><label>Completed</label><span data-count="${done.length}">0</span></div>
      </div>

      <div class="card">
        <div class="card-header">
          <div><h3 class="card-title">Your service requests</h3>
          <p class="card-subtitle">Everything raised by your organisation</p></div>
        </div>
        ${!items.length ? emptyState(
          'workorders',
          'No requests yet',
          'Raise your first service request and we will take it from there.',
          `<button class="btn btn-primary btn-sm" type="button" onclick="openCreateWOModal()">${ICONS.plus} New Service Request</button>`
        ) : `
        <div class="table-wrap table-wrap--cards"><table>
          <thead><tr><th scope="col">Reference</th><th scope="col">Subject</th><th scope="col">Site</th><th scope="col">Status</th><th scope="col">Priority</th><th scope="col">SLA</th><th scope="col"><span class="sr-only">Actions</span></th></tr></thead>
          <tbody>${items.map(w => `
            <tr>
              <td data-label="Reference"><span class="code-chip">${esc(w.code)}</span></td>
              <td class="td-title" data-label="Subject">${esc(w.title)}</td>
              <td class="text-muted" data-label="Site">${esc(w.siteName || '—')}</td>
              <td data-label="Status">${statusBadge(w.status)}</td>
              <td data-label="Priority">${priorityBadge(w.priority)}</td>
              <td data-label="SLA">${slaBadge(w.slaStatus)}</td>
              <td data-label=""><div class="table-actions"><button class="btn btn-ghost btn-sm" type="button" onclick="openWODetail('${w.id}')" title="View request">${ICONS.eye}<span class="sr-only">View</span></button></div></td>
            </tr>`).join('')}</tbody>
        </table></div>`}
      </div>`;

    animateVisuals(container);
  } catch (e) { setError(container, e.message); }
}

/* ═══════════════════════ CUSTOMERS ════════════════════════════════ */
function loadCustomers() {
  const container = document.getElementById('page-customers');
  if (!container) return;
  const canManage = isStaff();
  container.innerHTML = `
    <div class="page-header">
      <div>
        <p class="page-eyebrow">Accounts</p>
        <h2 class="page-heading">Customers</h2>
        <p class="page-sub">Customer organisations and their sites</p>
      </div>
      ${canManage ? `<button class="btn btn-primary btn-sm" type="button" onclick="openCreateCustModal()">${ICONS.plus} New Customer</button>` : ''}
    </div>
    <div class="filter-row">
      <div class="search-wrap">${ICONS.search}<input class="form-input" id="cust-search" type="search" placeholder="Search customers…" value="${esc(custSearch)}" aria-label="Search customers" oninput="onCustSearch(this.value)"></div>
      ${custSearch ? `<button class="btn btn-ghost btn-sm" type="button" onclick="clearCustFilters()">Clear</button>` : ''}
    </div>
    <div id="cust-table"></div>`;
  renderCustomerTable();
}

function onCustSearch(value) {
  custSearch = value;
  custPage = 0;
  scheduleSearch('cust', renderCustomerTable);
}

function setCustSort(field) {
  custSort = custSort.field === field
    ? { field, dir: custSort.dir === 'asc' ? 'desc' : 'asc' }
    : { field, dir: 'asc' };
  custPage = 0;
  renderCustomerTable();
}

function clearCustFilters() {
  custSearch = '';
  custPage = 0;
  loadCustomers();
}

async function renderCustomerTable() {
  const container = document.getElementById('cust-table');
  if (!container) return;
  setLoading(container);
  try {
    const params = { page: custPage, size: 15, sort: sortParam(custSort) };
    if (custSearch) params.search = custSearch;
    const data = await api.getCustomers(params);
    const items = data.content || [];
    if (!items.length) {
      container.innerHTML = emptyState(
        'customers',
        custSearch ? 'No customers match your search' : 'No customers yet',
        custSearch ? 'Try a different name or email.' : 'Add the first customer organisation to get started.',
        custSearch ? `<button class="btn btn-secondary btn-sm" type="button" onclick="clearCustFilters()">Clear search</button>` : ''
      );
      return;
    }
    container.innerHTML = `
      <div class="table-wrap table-wrap--cards"><table>
        <thead><tr>
          ${sortTh('Name', 'name', custSort, 'setCustSort')}
          ${sortTh('Contact email', 'contactEmail', custSort, 'setCustSort')}
          <th scope="col">Phone</th>
          <th scope="col">Address</th>
          <th scope="col">Status</th>
          ${sortTh('Created', 'createdAt', custSort, 'setCustSort')}
          <th scope="col"><span class="sr-only">Actions</span></th>
        </tr></thead>
        <tbody>${items.map(c => `
          <tr>
            <td class="td-title" data-label="Name">${esc(c.name)}</td>
            <td class="text-muted" data-label="Contact email">${esc(c.contactEmail || '—')}</td>
            <td class="text-muted" data-label="Phone">${esc(c.contactPhone || '—')}</td>
            <td class="text-muted cell-clamp" data-label="Address">${esc(c.address || '—')}</td>
            <td data-label="Status"><span class="badge ${c.active ? 'badge-completed' : 'badge-cancelled'}">${c.active ? 'Active' : 'Inactive'}</span></td>
            <td class="text-muted" data-label="Created">${fmtDate(c.createdAt)}</td>
            <td data-label=""><div class="table-actions">
              <button class="btn btn-ghost btn-sm" type="button" onclick="openSitesModal('${c.id}', this.closest('tr').querySelector('.td-title').textContent)" title="Manage sites">${ICONS.building} Sites</button>
              <button class="btn btn-ghost btn-sm" type="button" onclick="openEditCustModal('${c.id}')" title="Edit customer">${ICONS.edit}<span class="sr-only">Edit</span></button>
            </div></td>
          </tr>`).join('')}
        </tbody>
      </table></div>
      ${buildPagination(data.number, data.totalPages, (p) => { custPage = p; renderCustomerTable(); })}`;
  } catch (e) { setError(container, e.message); }
}

function openCreateCustModal() {
  ['cust-name', 'cust-email', 'cust-phone', 'cust-address'].forEach(id => {
    const input = document.getElementById(id);
    if (input) input.value = '';
  });
  document.getElementById('cust-modal-id').value = '';
  document.getElementById('cust-modal-title').textContent = 'New Customer';
  document.getElementById('cust-alert').innerHTML = '';
  openModal('modal-customer');
}

async function openEditCustModal(id) {
  try {
    const c = await api.getCustomer(id);
    document.getElementById('cust-modal-id').value = c.id;
    document.getElementById('cust-modal-title').textContent = 'Edit Customer';
    document.getElementById('cust-name').value = c.name || '';
    document.getElementById('cust-email').value = c.contactEmail || '';
    document.getElementById('cust-phone').value = c.contactPhone || '';
    document.getElementById('cust-address').value = c.address || '';
    document.getElementById('cust-alert').innerHTML = '';
    openModal('modal-customer');
  } catch (e) { toast(e.message, 'error'); }
}

async function submitCustomer() {
  const id = document.getElementById('cust-modal-id').value;
  const body = {
    name: document.getElementById('cust-name').value.trim(),
    contactEmail: document.getElementById('cust-email').value.trim() || null,
    contactPhone: document.getElementById('cust-phone').value.trim() || null,
    address: document.getElementById('cust-address').value.trim() || null,
  };
  if (!body.name) {
    formError('cust-alert', 'Customer name is required.', ['cust-name']);
    return;
  }
  const btn = document.getElementById('cust-submit');
  setBtnBusy(btn, true, 'Saving…');
  try {
    if (id) await api.updateCustomer(id, body);
    else await api.createCustomer(body);
    toast(id ? 'Customer updated' : 'Customer created', 'success');
    closeModal('modal-customer');
    renderCustomerTable();
  } catch (e) {
    formError('cust-alert', e.message, ['cust-email']);
  } finally {
    setBtnBusy(btn, false);
  }
}

/* ── Sites ─────────────────────────────────────────────────────── */
async function openSitesModal(customerId, customerName) {
  document.getElementById('sites-modal-title').textContent = `Sites — ${customerName}`;
  document.getElementById('sites-modal-cust-id').value = customerId;
  document.getElementById('sites-alert').innerHTML = '';
  resetSiteForm();
  openModal('modal-sites');
  await loadSitesList(customerId);
}

async function loadSitesList(customerId) {
  const container = document.getElementById('sites-list');
  if (!container) return;
  setLoading(container);
  try {
    const sites = await api.getAllSites(customerId);
    if (!sites.length) {
      container.innerHTML = emptyState('building', 'No sites yet', 'Add the first site using the form below.');
      return;
    }
    container.innerHTML = `<div class="table-wrap"><table>
      <thead><tr><th scope="col">Name</th><th scope="col">City</th><th scope="col">State</th><th scope="col">Contact</th><th scope="col"><span class="sr-only">Actions</span></th></tr></thead>
      <tbody>${sites.map(s => `
        <tr>
          <td class="td-title">${esc(s.name)}</td>
          <td class="text-muted">${esc(s.city || '—')}</td>
          <td class="text-muted">${esc(s.state || '—')}</td>
          <td class="text-muted">${esc(s.contactName || '—')}</td>
          <td><div class="table-actions"><button class="btn btn-ghost btn-sm" type="button" onclick="editSite('${customerId}','${s.id}')" title="Edit site">${ICONS.edit}<span class="sr-only">Edit</span></button></div></td>
        </tr>`).join('')}
      </tbody>
    </table></div>`;
  } catch (e) { setError(container, e.message); }
}

async function editSite(customerId, siteId) {
  try {
    const s = await api.getSite(customerId, siteId);
    document.getElementById('site-edit-id').value = s.id;
    document.getElementById('site-name').value = s.name || '';
    document.getElementById('site-address').value = s.address || '';
    document.getElementById('site-city').value = s.city || '';
    document.getElementById('site-state').value = s.state || '';
    document.getElementById('site-postal').value = s.postalCode || '';
    document.getElementById('site-country').value = s.country || '';
    document.getElementById('site-contact-name').value = s.contactName || '';
    document.getElementById('site-contact-phone').value = s.contactPhone || '';
    document.getElementById('site-form-title').textContent = 'Edit site';
    document.getElementById('sites-alert').innerHTML = '';
    document.getElementById('site-name').focus();
  } catch (e) { toast(e.message, 'error'); }
}

function resetSiteForm() {
  document.getElementById('site-edit-id').value = '';
  ['site-name', 'site-address', 'site-city', 'site-state', 'site-postal', 'site-contact-name', 'site-contact-phone']
    .forEach(id => { const input = document.getElementById(id); if (input) input.value = ''; });
  const country = document.getElementById('site-country');
  if (country) country.value = 'USA';
  document.getElementById('site-form-title').textContent = 'Add a site';
}

async function submitSite() {
  const cid = document.getElementById('sites-modal-cust-id').value;
  const editId = document.getElementById('site-edit-id').value;
  const body = {
    name: document.getElementById('site-name').value.trim(),
    address: document.getElementById('site-address').value.trim() || null,
    city: document.getElementById('site-city').value.trim() || null,
    state: document.getElementById('site-state').value.trim() || null,
    country: document.getElementById('site-country').value.trim() || null,
    postalCode: document.getElementById('site-postal').value.trim() || null,
    contactName: document.getElementById('site-contact-name').value.trim() || null,
    contactPhone: document.getElementById('site-contact-phone').value.trim() || null,
  };
  if (!body.name) {
    formError('sites-alert', 'Site name is required.', ['site-name']);
    return;
  }
  const btn = document.getElementById('site-submit-btn');
  setBtnBusy(btn, true, editId ? 'Updating…' : 'Adding…');
  try {
    if (editId) await api.updateSite(cid, editId, body);
    else await api.createSite(cid, body);
    toast(editId ? 'Site updated' : 'Site created', 'success');
    setBtnBusy(btn, false);
    resetSiteForm();
    await loadSitesList(cid);
    if (document.getElementById('page-customers')?.classList.contains('active')) renderCustomerTable();
  } catch (e) {
    formError('sites-alert', e.message, ['site-name']);
  } finally {
    setBtnBusy(btn, false);
  }
}

/* ═══════════════════════ PARTS ════════════════════════════════════ */
function loadParts() {
  const container = document.getElementById('page-parts');
  if (!container) return;
  const canCreate = isManager();
  container.innerHTML = `
    <div class="page-header">
      <div>
        <p class="page-eyebrow">Inventory</p>
        <h2 class="page-heading">Parts Inventory</h2>
        <p class="page-sub">Track spare parts stock and reorder levels</p>
      </div>
      ${canCreate ? `<button class="btn btn-primary btn-sm" type="button" onclick="openCreatePartModal()">${ICONS.plus} New Part</button>` : ''}
    </div>
    <div class="filter-row">
      <div class="search-wrap">${ICONS.search}<input class="form-input" id="parts-search" type="search" placeholder="Search parts…" value="${esc(partsSearch)}" aria-label="Search parts" oninput="onPartsSearch(this.value)"></div>
      ${partsSearch ? `<button class="btn btn-ghost btn-sm" type="button" onclick="clearPartsFilters()">Clear</button>` : ''}
    </div>
    <div id="parts-table"></div>`;
  renderPartsTable();
}

function onPartsSearch(value) {
  partsSearch = value;
  partsPage = 0;
  scheduleSearch('parts', renderPartsTable);
}

function setPartsSort(field) {
  partsSort = partsSort.field === field
    ? { field, dir: partsSort.dir === 'asc' ? 'desc' : 'asc' }
    : { field, dir: 'asc' };
  partsPage = 0;
  renderPartsTable();
}

function clearPartsFilters() {
  partsSearch = '';
  partsPage = 0;
  loadParts();
}

async function renderPartsTable() {
  const container = document.getElementById('parts-table');
  if (!container) return;
  setLoading(container);
  try {
    const params = { page: partsPage, size: 15, sort: sortParam(partsSort) };
    if (partsSearch) params.search = partsSearch;
    const data = await api.getParts(params);
    const items = data.content || [];
    if (!items.length) {
      container.innerHTML = emptyState(
        'parts',
        partsSearch ? 'No parts match your search' : 'No parts in inventory',
        partsSearch ? 'Try a different part number or name.' : 'Add your first part to start tracking stock.',
        partsSearch ? `<button class="btn btn-secondary btn-sm" type="button" onclick="clearPartsFilters()">Clear search</button>` : ''
      );
      return;
    }
    container.innerHTML = `
      <div class="table-wrap table-wrap--cards"><table>
        <thead><tr>
          ${sortTh('Part #', 'partNumber', partsSort, 'setPartsSort')}
          ${sortTh('Name', 'name', partsSort, 'setPartsSort')}
          <th scope="col">Unit</th>
          ${sortTh('In stock', 'quantityInStock', partsSort, 'setPartsSort')}
          ${sortTh('Reorder level', 'reorderLevel', partsSort, 'setPartsSort')}
          ${sortTh('Unit cost', 'unitCost', partsSort, 'setPartsSort')}
          <th scope="col">Stock status</th>
          ${isManager() ? '<th scope="col"><span class="sr-only">Actions</span></th>' : ''}
        </tr></thead>
        <tbody>${items.map(p => {
          const lowStock = p.quantityInStock <= p.reorderLevel;
          return `<tr>
            <td data-label="Part #"><span class="code-chip">${esc(p.partNumber)}</span></td>
            <td class="td-title" data-label="Name">${esc(p.name)}</td>
            <td class="text-muted" data-label="Unit">${esc(p.unitOfMeasure || '—')}</td>
            <td data-label="In stock" class="${lowStock ? 'text-red font-bold' : ''}">${p.quantityInStock}</td>
            <td class="text-muted" data-label="Reorder level">${p.reorderLevel}</td>
            <td class="text-muted" data-label="Unit cost">${fmtMoney(p.unitCost)}</td>
            <td data-label="Stock status">${lowStock ? '<span class="badge badge-high">Low stock</span>' : '<span class="badge badge-completed">OK</span>'}</td>
            ${isManager() ? `<td data-label=""><div class="table-actions"><button class="btn btn-ghost btn-sm" type="button" onclick="openEditPartModal('${p.id}')" title="Edit part">${ICONS.edit}<span class="sr-only">Edit</span></button></div></td>` : ''}
          </tr>`;
        }).join('')}
        </tbody>
      </table></div>
      ${buildPagination(data.number, data.totalPages, (p) => { partsPage = p; renderPartsTable(); })}`;
  } catch (e) { setError(container, e.message); }
}

function openCreatePartModal() {
  ['part-modal-id', 'part-pn', 'part-name', 'part-desc', 'part-uom', 'part-stock', 'part-reorder', 'part-cost']
    .forEach(id => { const e = document.getElementById(id); if (e) e.value = ''; });
  document.getElementById('part-modal-title').textContent = 'New Part';
  document.getElementById('part-modal-alert').innerHTML = '';
  openModal('modal-part');
}

async function openEditPartModal(id) {
  try {
    const p = await api.getPart(id);
    document.getElementById('part-modal-id').value = p.id;
    document.getElementById('part-modal-title').textContent = 'Edit Part';
    document.getElementById('part-pn').value = p.partNumber;
    document.getElementById('part-name').value = p.name;
    document.getElementById('part-desc').value = p.description || '';
    document.getElementById('part-uom').value = p.unitOfMeasure || '';
    document.getElementById('part-stock').value = p.quantityInStock;
    document.getElementById('part-reorder').value = p.reorderLevel;
    document.getElementById('part-cost').value = p.unitCost ?? '';
    document.getElementById('part-modal-alert').innerHTML = '';
    openModal('modal-part');
  } catch (e) { toast(e.message, 'error'); }
}

async function submitPart() {
  const id = document.getElementById('part-modal-id').value;
  const costValue = document.getElementById('part-cost').value;
  const body = {
    partNumber: document.getElementById('part-pn').value.trim(),
    name: document.getElementById('part-name').value.trim(),
    description: document.getElementById('part-desc').value.trim() || null,
    unitOfMeasure: document.getElementById('part-uom').value.trim() || null,
    quantityInStock: parseInt(document.getElementById('part-stock').value, 10) || 0,
    reorderLevel: parseInt(document.getElementById('part-reorder').value, 10) || 0,
    unitCost: costValue === '' ? null : parseFloat(costValue),
  };
  const missing = [];
  if (!body.partNumber) missing.push('part-pn');
  if (!body.name) missing.push('part-name');
  if (missing.length) {
    formError('part-modal-alert', 'Part number and name are required.', missing);
    return;
  }
  const btn = document.getElementById('part-submit');
  setBtnBusy(btn, true, 'Saving…');
  try {
    if (id) await api.updatePart(id, body);
    else await api.createPart(body);
    toast(id ? 'Part updated' : 'Part created', 'success');
    closeModal('modal-part');
    renderPartsTable();
  } catch (e) {
    formError('part-modal-alert', e.message, ['part-pn']);
  } finally {
    setBtnBusy(btn, false);
  }
}

/* ═══════════════════════ USERS ════════════════════════════════════ */
function loadUsers() {
  const container = document.getElementById('page-users');
  if (!container) return;
  if (!isManager()) { navigate('dashboard'); return; }
  container.innerHTML = `
    <div class="page-header">
      <div>
        <p class="page-eyebrow">Access</p>
        <h2 class="page-heading">User Management</h2>
        <p class="page-sub">Staff and customer accounts</p>
      </div>
      <button class="btn btn-primary btn-sm" type="button" onclick="openCreateUserModal()">${ICONS.plus} New User</button>
    </div>
    <div class="filter-row">
      <div class="search-wrap">${ICONS.search}<input class="form-input" id="users-search" type="search" placeholder="Search name, email or role…" value="${esc(usersQuery)}" aria-label="Search users" oninput="onUsersSearch(this.value)"></div>
      ${usersQuery ? `<button class="btn btn-ghost btn-sm" type="button" onclick="clearUsersFilters()">Clear</button>` : ''}
    </div>
    <div id="users-table"></div>`;
  renderUsersTable();
}

function onUsersSearch(value) {
  usersQuery = value;
  drawUsersTable();
}

function clearUsersFilters() {
  usersQuery = '';
  const input = document.getElementById('users-search');
  if (input) input.value = '';
  drawUsersTable();
}

function setUsersSort(field) {
  usersSort = usersSort.field === field
    ? { field, dir: usersSort.dir === 'asc' ? 'desc' : 'asc' }
    : { field, dir: 'asc' };
  drawUsersTable();
}

async function renderUsersTable() {
  const container = document.getElementById('users-table');
  if (!container) return;
  setLoading(container);
  try {
    const [users, customers] = await Promise.all([
      api.getUsers(),
      api.getCustomers({ size: 200 }).catch(() => ({ content: [] })),
    ]);
    usersCache = users || [];
    usersOrgMap = {};
    (customers.content || []).forEach(c => { usersOrgMap[c.id] = c.name; });
    drawUsersTable(container);
  } catch (e) { setError(container, e.message); }
}

function drawUsersTable(container = document.getElementById('users-table')) {
  if (!container) return;
  if (!usersCache.length) {
    container.innerHTML = emptyState('users', 'No users yet', 'Invite your first teammate to get started.');
    return;
  }
  const q = usersQuery.trim().toLowerCase();
  let rows = usersCache.filter(u => {
    if (!q) return true;
    const haystack = `${u.firstName} ${u.lastName} ${u.email} ${u.role} ${usersOrgMap[u.customerId] || ''}`.toLowerCase();
    return haystack.includes(q);
  });

  if (!rows.length) {
    container.innerHTML = emptyState(
      'users', 'No users match your search', 'Try a different name, email or role.',
      `<button class="btn btn-secondary btn-sm" type="button" onclick="clearUsersFilters()">Clear search</button>`
    );
    return;
  }

  const { field, dir } = usersSort;
  const sign = dir === 'asc' ? 1 : -1;
  rows = [...rows].sort((a, b) => {
    if (field === 'createdAt') return ((new Date(a.createdAt) || 0) - (new Date(b.createdAt) || 0)) * sign;
    const av = field === 'name' ? `${a.firstName} ${a.lastName}` : a[field];
    const bv = field === 'name' ? `${b.firstName} ${b.lastName}` : b[field];
    return String(av ?? '').localeCompare(String(bv ?? '')) * sign;
  });

  container.innerHTML = `
    <div class="table-wrap table-wrap--cards"><table>
      <thead><tr>
        ${sortTh('Name', 'name', usersSort, 'setUsersSort')}
        ${sortTh('Email', 'email', usersSort, 'setUsersSort')}
        ${sortTh('Role', 'role', usersSort, 'setUsersSort')}
        <th scope="col">Organisation</th>
        <th scope="col">Status</th>
        ${sortTh('Joined', 'createdAt', usersSort, 'setUsersSort')}
        <th scope="col"><span class="sr-only">Actions</span></th>
      </tr></thead>
      <tbody>${rows.map(u => `
        <tr>
          <td class="td-title" data-label="Name">${esc(u.firstName)} ${esc(u.lastName)}</td>
          <td class="text-muted" data-label="Email">${esc(u.email)}</td>
          <td data-label="Role">${roleBadge(u.role)}</td>
          <td class="text-muted" data-label="Organisation">${esc(usersOrgMap[u.customerId] || '—')}</td>
          <td data-label="Status"><span class="badge ${u.enabled ? 'badge-completed' : 'badge-cancelled'}">${u.enabled ? 'Active' : 'Disabled'}</span></td>
          <td class="text-muted" data-label="Joined">${fmtDate(u.createdAt)}</td>
          <td data-label=""><div class="table-actions"><button class="btn btn-ghost btn-sm" type="button" onclick="openEditUserModal('${u.id}')" title="Edit user">${ICONS.edit}<span class="sr-only">Edit</span></button></div></td>
        </tr>`).join('')}
      </tbody>
    </table></div>
    <p class="text-muted text-sm mt-2">Showing ${rows.length} of ${usersCache.length} accounts</p>`;
}

async function openCreateUserModal() {
  ['new-user-email', 'new-user-pw', 'new-user-fname', 'new-user-lname'].forEach(id => {
    document.getElementById(id).value = '';
  });
  document.getElementById('new-user-role').value = 'TECHNICIAN';
  document.getElementById('new-user-alert').innerHTML = '';
  toggleUserCustomerField('new');
  openModal('modal-create-user');
  try {
    const data = await api.getCustomers({ size: 200 });
    document.getElementById('new-user-customer').innerHTML = '<option value="">Select customer…</option>' +
      (data.content || []).map(c => `<option value="${c.id}">${esc(c.name)}</option>`).join('');
  } catch { /* the option list can be filled later */ }
}

function toggleUserCustomerField(prefix) {
  const role = document.getElementById(`${prefix}-user-role`)?.value;
  const group = document.getElementById(`${prefix}-user-customer-group`);
  if (group) group.hidden = role !== 'CUSTOMER';
  if (prefix === 'new') {
    const select = document.getElementById('new-user-customer');
    if (select) select.required = role === 'CUSTOMER';
  }
}

async function submitCreateUser() {
  const role = document.getElementById('new-user-role').value;
  const body = {
    email: document.getElementById('new-user-email').value.trim(),
    password: document.getElementById('new-user-pw').value,
    firstName: document.getElementById('new-user-fname').value.trim(),
    lastName: document.getElementById('new-user-lname').value.trim(),
    role,
  };
  if (role === 'CUSTOMER') body.customerId = document.getElementById('new-user-customer').value;

  const missing = [];
  if (!body.firstName) missing.push('new-user-fname');
  if (!body.lastName) missing.push('new-user-lname');
  if (!body.email) missing.push('new-user-email');
  if (!body.password) missing.push('new-user-pw');
  if (missing.length) {
    formError('new-user-alert', 'All fields are required.', missing);
    return;
  }
  if (body.password.length < 8) {
    formError('new-user-alert', 'Password must be at least 8 characters.', ['new-user-pw']);
    return;
  }
  if (role === 'CUSTOMER' && !body.customerId) {
    formError('new-user-alert', 'Select the customer organisation.', ['new-user-customer']);
    return;
  }

  const btn = document.getElementById('new-user-submit');
  setBtnBusy(btn, true, 'Creating…');
  try {
    await api.createUser(body);
    toast('User created', 'success');
    closeModal('modal-create-user');
    renderUsersTable();
  } catch (e) {
    formError('new-user-alert', e.message, ['new-user-email']);
  } finally {
    setBtnBusy(btn, false);
  }
}

function openEditUserModal(id) {
  const u = usersCache.find(x => x.id === id);
  if (!u) return;
  document.getElementById('edit-user-id').value = u.id;
  document.getElementById('edit-user-email').value = u.email;
  document.getElementById('edit-user-fname').value = u.firstName;
  document.getElementById('edit-user-lname').value = u.lastName;
  document.getElementById('edit-user-enabled').checked = u.enabled;
  document.getElementById('edit-user-alert').innerHTML = '';

  const roleSelect = document.getElementById('edit-user-role');
  const options = ['TECHNICIAN', 'DISPATCHER', 'MANAGER'];
  if (u.customerId) options.push('CUSTOMER');
  roleSelect.innerHTML = options.map(r => `<option value="${r}"${r === u.role ? ' selected' : ''}>${r}</option>`).join('');
  if (!u.customerId && u.role === 'CUSTOMER') {
    // defensive: a customer account must always keep its organisation link
    roleSelect.innerHTML += '<option value="CUSTOMER" selected>CUSTOMER</option>';
  }
  openModal('modal-edit-user');
}

async function submitEditUser() {
  const id = document.getElementById('edit-user-id').value;
  const body = {
    firstName: document.getElementById('edit-user-fname').value.trim(),
    lastName: document.getElementById('edit-user-lname').value.trim(),
    role: document.getElementById('edit-user-role').value,
    enabled: document.getElementById('edit-user-enabled').checked,
  };
  if (!body.firstName || !body.lastName) {
    formError('edit-user-alert', 'First and last name are required.', ['edit-user-fname', 'edit-user-lname']);
    return;
  }
  const btn = document.getElementById('edit-user-submit');
  setBtnBusy(btn, true, 'Saving…');
  try {
    const updated = await api.updateUser(id, body);
    if (updated.id === Auth.getUser()?.id) {
      Auth.updateUser({ firstName: updated.firstName, lastName: updated.lastName, role: updated.role });
      applyUserChrome();
      buildNav();
    }
    toast('User updated', 'success');
    closeModal('modal-edit-user');
    renderUsersTable();
  } catch (e) {
    formError('edit-user-alert', e.message);
  } finally {
    setBtnBusy(btn, false);
  }
}

/* ═══════════════════════ REPORTS ══════════════════════════════════ */
async function loadReports() {
  const container = document.getElementById('page-reports');
  if (!container) return;
  if (!isStaff()) { navigate('dashboard'); return; }

  container.innerHTML = `
    <div class="page-header">
      <div>
        <p class="page-eyebrow">Insights</p>
        <h2 class="page-heading">Reports &amp; Analytics</h2>
        <p class="page-sub">Operational metrics and SLA performance</p>
      </div>
      <button class="btn btn-secondary btn-sm" type="button" onclick="refreshReports(this)">${ICONS.refresh} Refresh</button>
    </div>
    <div class="card mb-4">
      <div class="filter-row" style="margin-bottom:0">
        <select class="form-select filter-select" id="rep-customer" aria-label="Filter by customer">
          <option value="">All customers</option>
        </select>
        <select class="form-select filter-select" id="rep-tech" aria-label="Filter by technician">
          <option value="">All technicians</option>
        </select>
        <select class="form-select filter-select" id="rep-status" aria-label="Filter by status">
          <option value="">All statuses</option>
          ${Object.keys(STATUS_LABEL).map(s => `<option value="${s}"${reportFilters.status === s ? ' selected' : ''}>${STATUS_LABEL[s]}</option>`).join('')}
        </select>
        <input class="form-input filter-select" type="datetime-local" id="rep-from" aria-label="Created from" title="Created from">
        <input class="form-input filter-select" type="datetime-local" id="rep-until" aria-label="Created until" title="Created until">
        <button class="btn btn-primary btn-sm" type="button" onclick="applyReportFilters(this)">Apply</button>
        <button class="btn btn-ghost btn-sm" type="button" onclick="resetReportFilters(this)">Reset</button>
      </div>
    </div>
    <div id="reports-content" aria-live="polite"></div>`;

  try {
    const [customers, techs] = await Promise.all([
      api.getCustomers({ size: 200 }).catch(() => ({ content: [] })),
      api.getTechnicians().catch(() => []),
    ]);
    document.getElementById('rep-customer').innerHTML = '<option value="">All customers</option>' +
      (customers.content || []).map(c => `<option value="${c.id}"${reportFilters.customerId === c.id ? ' selected' : ''}>${esc(c.name)}</option>`).join('');
    document.getElementById('rep-tech').innerHTML = '<option value="">All technicians</option>' +
      techs.map(t => `<option value="${t.id}"${reportFilters.technicianId === t.id ? ' selected' : ''}>${esc(t.firstName)} ${esc(t.lastName)}</option>`).join('');
  } catch { /* filters still work without options */ }

  document.getElementById('rep-from').value = isoToLocalInput(reportFilters.from);
  document.getElementById('rep-until').value = isoToLocalInput(reportFilters.until);

  refreshReports();
}

function isoToLocalInput(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  const pad = n => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function reportParams() {
  const params = {};
  if (reportFilters.customerId) params.customerId = reportFilters.customerId;
  if (reportFilters.technicianId) params.technicianId = reportFilters.technicianId;
  if (reportFilters.status) params.status = reportFilters.status;
  if (reportFilters.from) params.from = reportFilters.from;
  if (reportFilters.until) params.until = reportFilters.until;
  return params;
}

function applyReportFilters(btn) {
  const fromVal = document.getElementById('rep-from').value;
  const untilVal = document.getElementById('rep-until').value;
  reportFilters = {
    customerId: document.getElementById('rep-customer').value,
    technicianId: document.getElementById('rep-tech').value,
    status: document.getElementById('rep-status').value,
    from: fromVal ? new Date(fromVal).toISOString() : '',
    until: untilVal ? new Date(untilVal).toISOString() : '',
  };
  refreshReports(btn);
}

function resetReportFilters(btn) {
  reportFilters = { customerId: '', technicianId: '', status: '', from: '', until: '' };
  ['rep-customer', 'rep-tech', 'rep-status', 'rep-from', 'rep-until'].forEach(id => {
    const elRef = document.getElementById(id);
    if (elRef) elRef.value = '';
  });
  refreshReports(btn);
}

async function refreshReports(btn) {
  const content = document.getElementById('reports-content');
  if (!content) return;
  if (btn) setBtnBusy(btn, true, 'Applying…');
  if (!content.dataset.loaded) content.innerHTML = chartSkeleton();
  try {
    const s = await api.getSummary(reportParams());
    const total = s.totalWorkOrders || 0;
    const safeTotal = total || 1;
    const compliance = Number(s.slaCompliancePercent) || 0;

    content.innerHTML = `
      <div class="stats-grid mb-4">
        <div class="stat-card" data-tone="violet">
          <span class="stat-top"><span class="stat-icon violet">${ICONS.workorders}</span></span>
          <span class="stat-value" data-count="${total}">0</span>
          <span class="stat-label">Work orders</span>
        </div>
        <div class="stat-card" data-tone="${compliance >= 90 ? 'green' : compliance >= 70 ? 'amber' : 'red'}">
          <span class="stat-top"><span class="stat-icon ${compliance >= 90 ? 'green' : compliance >= 70 ? 'amber' : 'red'}">${ICONS.reports}</span></span>
          <span class="stat-value" data-count="${compliance}" data-decimals="1" data-suffix="%">0%</span>
          <span class="stat-label">SLA compliance</span>
        </div>
        <div class="stat-card" data-tone="red">
          <span class="stat-top"><span class="stat-icon red">${ICONS.alert}</span></span>
          <span class="stat-value" data-count="${s.slaBreachedCount || 0}">0</span>
          <span class="stat-label">SLA breached</span>
        </div>
        <div class="stat-card" data-tone="amber">
          <span class="stat-top"><span class="stat-icon amber">${ICONS.clock}</span></span>
          <span class="stat-value" data-count="${s.overdueCount || 0}">0</span>
          <span class="stat-label">Overdue open</span>
        </div>
      </div>

      <div class="dashboard-grid">
        <div class="card">
          <div class="card-header">
            <div><h3 class="card-title">SLA overview</h3><p class="card-subtitle">${total} orders in scope</p></div>
          </div>
          <div class="sla-hero">
            ${donutCard(compliance)}
            <div class="sla-legend">
              ${legendRow('var(--green)', 'On track', s.slaOnTrackCount || 0, safeTotal)}
              ${legendRow('var(--amber)', 'At risk', s.slaAtRiskCount || 0, safeTotal)}
              ${legendRow('var(--red)', 'Breached', s.slaBreachedCount || 0, safeTotal)}
            </div>
          </div>
          <div class="divider"></div>
          ${barRows([
            ['On track', s.slaOnTrackCount || 0],
            ['At risk', s.slaAtRiskCount || 0],
            ['Breached', s.slaBreachedCount || 0],
          ], safeTotal, { 'On track': 'var(--green)', 'At risk': 'var(--amber)', 'Breached': 'var(--red)' })}
        </div>

        <div class="card">
          <div class="card-header">
            <div><h3 class="card-title">By priority</h3><p class="card-subtitle">Urgency mix of all orders</p></div>
          </div>
          ${barRows(Object.entries(s.byPriority || {}), safeTotal, PRIORITY_COLORS)}
        </div>
      </div>

      <div class="dashboard-grid">
        <div class="card">
          <div class="card-header">
            <div><h3 class="card-title">By site</h3><p class="card-subtitle">Order volume per location</p></div>
          </div>
          ${barRows(Object.entries(s.bySite || {}), safeTotal, { Unassigned: 'var(--text-3)' })}
        </div>
        <div class="card">
          <div class="card-header">
            <div><h3 class="card-title">By technician</h3><p class="card-subtitle">Load per field technician</p></div>
          </div>
          ${barRows(Object.entries(s.byTechnician || {}), safeTotal, { Unassigned: 'var(--text-3)' })}
        </div>
      </div>

      <div class="card mt-3">
        <div class="card-header">
          <div><h3 class="card-title">By status</h3><p class="card-subtitle">${total} total work orders</p></div>
        </div>
        <div class="tile-grid">
          ${Object.entries(s.byStatus || {}).map(([st, cnt]) => `
            <div class="count-tile">
              <span class="count-value" data-count="${cnt}">0</span>
              ${statusBadge(st)}
              <span class="count-sub">${(cnt / safeTotal * 100).toFixed(1)}% of total</span>
            </div>`).join('')}
        </div>
      </div>`;

    content.dataset.loaded = '1';
    animateVisuals(content);
  } catch (e) {
    setError(content, e.message);
  } finally {
    if (btn) setBtnBusy(btn, false);
  }
}

/* ── Page loader registry ──────────────────────────────────────────── */
const pageLoaders = {
  dashboard: loadDashboard,
  workorders: loadWorkOrders,
  myjobs: loadMyJobs,
  portal: loadPortal,
  customers: loadCustomers,
  parts: loadParts,
  users: loadUsers,
  reports: loadReports,
};

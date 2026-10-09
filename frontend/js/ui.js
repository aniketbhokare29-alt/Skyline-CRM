/*
 * Skyline CRM - UI helpers
 *
 * These mirror the formatting helpers from the original server-rendered
 * Views.java / Utils.java so the pages look exactly the same:
 *   escape, parseMoney, money, metric, moneyMetric, check, yesNo
 */

/* Java: Utils.escape - HTML-escape a value before inserting it into markup. */
function escape(value) {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

/* Java: Utils.parseMoney - strip everything except digits, then read an int. */
function parseMoney(value) {
  if (value === null || value === undefined) {
    return 0;
  }
  const digits = String(value).replace(/[^0-9]/g, '');
  if (digits === '') {
    return 0;
  }
  const parsed = parseInt(digits, 10);
  return Number.isNaN(parsed) ? 0 : parsed;
}

/* Java: String.format("%,d", value) */
function groupDigits(value) {
  return value.toLocaleString('en-US');
}

/* Java: Views.money(String) */
function money(value) {
  return '₹' + groupDigits(parseMoney(value));
}

/* Java: Views.money(int) */
function moneyInt(value) {
  return '₹' + groupDigits(value);
}

/* Java: Views.metric - a plain count card. */
function metric(label, value) {
  return '<article class="card">\n' +
    '  <p class="subtle">' + escape(label) + '</p>\n' +
    '  <div class="metric">' + value + '</div>\n' +
    '</article>\n';
}

/* Java: Views.moneyMetric - a card whose value is formatted as currency. */
function moneyMetric(label, value) {
  return '<article class="card">\n' +
    '  <p class="subtle">' + escape(label) + '</p>\n' +
    '  <div class="metric">' + escape(moneyInt(value)) + '</div>\n' +
    '</article>\n';
}

/* Java: Views.check - a checkbox line inside a form. */
function check(name, label) {
  return '<label class="checkline"><input name="' + escape(name) + '" type="checkbox" value="Yes"> ' + escape(label) + '</label>\n';
}

/* Java: Views.yesNo - the Yes / Pending pill used in tables. */
function yesNo(value) {
  if (value === 'Yes') {
    return '<span class="status status-ok">Yes</span>';
  }
  return '<span class="status status-warn">Pending</span>';
}

/*
 * Status pill classes. These come straight from the ternaries that the
 * original Views.java used while building each table row.
 */
function statusClass(kind, value) {
  switch (kind) {
    case 'properties':
      return value === 'Available' ? 'status-ok' : 'status-warn';
    case 'qualification':
      return value === 'Qualified' ? 'status-ok'
        : value === 'Future Prospect' ? 'status-warn' : 'status-info';
    case 'site-visits':
      return value === 'Visited' ? 'status-ok'
        : value === 'Rescheduled' ? 'status-warn' : 'status-info';
    case 'negotiations':
      return value === 'Approved' ? 'status-ok'
        : value === 'Pending' ? 'status-warn' : 'status-info';
    case 'bookings':
      return value === 'Confirmed' ? 'status-ok'
        : value === 'Pending' ? 'status-warn' : 'status-info';
    case 'loans':
      return value === 'Approved' || value === 'Disbursed' ? 'status-ok'
        : value === 'Rejected' ? 'status-info' : 'status-warn';
    case 'possession':
      return value === 'Delivered' ? 'status-ok' : 'status-warn';
    case 'support':
      return value === 'Resolved' ? 'status-ok'
        : value === 'Open' ? 'status-warn' : 'status-info';
    case 'attendance':
      return value === 'Present' ? 'status-ok'
        : value === 'Leave' ? 'status-warn' : 'status-info';
    case 'leaves':
      return value === 'Approved' ? 'status-ok'
        : value === 'Pending' ? 'status-warn' : 'status-info';
    case 'salary':
      return value === 'Paid' ? 'status-ok' : 'status-warn';
    case 'vendor-bills':
      return value === 'Fully Paid' ? 'status-ok'
        : value === 'Overdue' ? 'status-info' : 'status-warn';
    case 'vendor-payments':
      return value === 'Fully Paid' ? 'status-ok' : 'status-warn';
    case 'petty-cash-approvals':
      return value === 'Approved' ? 'status-ok'
        : value === 'Pending' ? 'status-warn' : 'status-info';
    default:
      return 'status-info';
  }
}

/* Java: LocalDate.now().toString() -> yyyy-MM-dd */
function today() {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return now.getFullYear() + '-' + month + '-' + day;
}

/* Java: LocalTime.now().withSecond(0).withNano(0) -> HH:mm */
function nowTime() {
  const now = new Date();
  return String(now.getHours()).padStart(2, '0') + ':' + String(now.getMinutes()).padStart(2, '0');
}

/* Java: Utils.encode -> URLEncoder.encode */
function encodeRole(role) {
  return encodeURIComponent(role);
}

/* Java: Views.errorMessage */
function errorBox(message) {
  if (!message) {
    return '';
  }
  return '<div class="error-box">' + escape(message) + '</div>';
}

/* Java: Utils.parsePairs - flatten a form into a plain object. */
function formValues(form) {
  const values = {};
  for (const [key, value] of new FormData(form).entries()) {
    values[key] = value;
  }
  return values;
}

/*
 * Save a create form and then reload the page, which is what the original
 * POST -> 303 redirect -> re-render cycle did.
 */
function bindForm(form, endpoint, redirectTo) {
  // Auto-calculate pending amount for payments
  if (endpoint === 'payments') {
    const totalCost = form.elements.namedItem('totalCost');
    const paidAmount = form.elements.namedItem('paidAmount');
    const pendingAmount = form.elements.namedItem('pendingAmount');
    
    function updatePending() {
      const total = parseMoney(totalCost?.value) || 0;
      const paid = parseMoney(paidAmount?.value) || 0;
      if (pendingAmount) {
        pendingAmount.value = total - paid;
      }
    }
    
    totalCost?.addEventListener('input', updatePending);
    paidAmount?.addEventListener('input', updatePending);
  }
  
  form.addEventListener('submit', async function (event) {
    event.preventDefault();
    if (!form.reportValidity()) {
      return;
    }
    const button = form.querySelector('button[type="submit"]');
    if (button) {
      button.disabled = true;
    }
    try {
      const idField = form.elements.namedItem('id');
      const id = idField ? idField.value : '';
      if (id) {
        const values = formValues(form);
        delete values.id;
        await Api.put(endpoint + '/' + encodeURIComponent(id), values);
      } else if (endpoint === 'documents') {
        const formData = new FormData(form);
        await Api.postFormData(endpoint, formData);
      } else {
        const values = formValues(form);
        await Api.post(endpoint, values);
      }
      const role = sessionStorage.getItem('crm_role') || '';
      const message = form.dataset.success || 'Record saved successfully.';
      if (form.dataset.success) {
        alert(message);
      }
      location.href = redirectTo + '?role=' + encodeRole(role) + '&message=' + encodeURIComponent(message);
    } catch (err) {
      if (button) {
        button.disabled = false;
      }
      alert(err.message);
    }
  });
}

function bindEditableRows(endpoint, formId, rows) {
  const form = document.getElementById(formId);
  const table = document.getElementById('rows');
  if (!form || !table) return;
  table.querySelectorAll('[data-edit-index]').forEach(button => {
    button.addEventListener('click', () => {
      const row = rows[Number(button.dataset.editIndex)];
      if (!row) return;
      const idField = form.elements.namedItem('id');
      if (!idField || !row.id) {
        alert('This record does not have a database ID. Restart the backend and refresh the page.');
        return;
      }
      idField.value = row.id;
      Object.keys(row).forEach(key => {
        if (key === 'id') return;
        const field = form.elements.namedItem(key);
        if (!field) return;
        // Browsers do not allow JavaScript to populate a file input with a
        // server path. Existing files remain available through their View link.
        if (field.type === 'file') return;
        field.value = row[key] == null ? '' : row[key];
      });
      const submit = form.querySelector('button[type="submit"]');
      if (submit) submit.textContent = 'Update Record';
      form.scrollIntoView({ behavior: 'smooth', block: 'start' });
    });
  });
  table.querySelectorAll('[data-delete-index]').forEach(button => {
    button.addEventListener('click', async () => {
      const row = rows[Number(button.dataset.deleteIndex)];
      if (!row || !confirm('Delete this record?')) return;
      try {
        await Api.delete(endpoint + '/' + encodeURIComponent(row.id));
        location.reload();
      } catch (err) {
        alert(err.message);
      }
    });
  });
}

/*
 * Fills the date and time inputs that the backend used to pre-render with
 * LocalDate.now() and LocalTime.now().
 */
function applyDateDefaults(root) {
  for (const input of root.querySelectorAll('[data-default]')) {
    if (input.dataset.default === 'today') {
      input.value = today();
    } else if (input.dataset.default === 'time') {
      input.value = nowTime();
    }
  }
}

/*
 * Java: Views.pieChart - the donut charts on the dashboard.
 */
function pieChart(title, data) {
  const labels = data.map(item => item.label);
  const values = data.map(item => item.value);
  const total = values.reduce((sum, value) => sum + value, 0);
  const colors = ['#0f766e', '#2563eb', '#d97706', '#dc2626', '#64748b', '#7c3aed'];

  let gradient = 'conic-gradient(';
  let legend = '';
  let cursor = 0;

  for (let i = 0; i < labels.length; i++) {
    const share = total === 0 ? 0 : (values[i] * 100.0) / total;
    const end = cursor + share;
    if (i > 0) {
      gradient += ', ';
    }
    gradient += colors[i % colors.length] + ' ' + formatPercent(cursor) + ' ' + formatPercent(end);
    const percentage = total === 0 ? 0 : (values[i] * 100.0) / total;
    legend += '<li><span class="chart-swatch" style="background:' + colors[i % colors.length] + '"></span>' +
      '<span>' + escape(labels[i]) + '</span><strong>' + values[i] +
      ' <small>(' + percentage.toFixed(1) + '%)</small></strong></li>';
    cursor = end;
  }

  if (total === 0) {
    gradient += '#dbe4e8 0 100%';
  }
  gradient += ')';

  return '<article class="analytics-card"><h3>' + escape(title) + '</h3>' +
    '<div class="chart-row">' +
    '<div class="donut" style="--chart:' + gradient + '"><span>' + total + '<small>Total</small></span></div>' +
    '<ul class="chart-legend">' + legend + '</ul>' +
    '</div></article>';
}

/* Java: Views.formatPercent - "%.2f%%" */
function formatPercent(value) {
  return value.toFixed(2) + '%';
}

/* Java: Views.analyticsSection */
function analyticsSection(analytics) {
  analytics = analytics || { title: 'Analytics', charts: [] };
  let body = '<section class="analytics-section"><div class="section-heading">' +
    '<h2>' + escape(analytics.title) + '</h2>' +
    '<p class="subtle">Live summary from your role\'s workspace data.</p></div>' +
    '<div class="analytics-grid">';
  for (const chart of (analytics.charts || [])) {
    body += pieChart(chart.title, chart.items);
  }
  return body + '</div></section>';
}

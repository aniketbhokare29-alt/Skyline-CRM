/*
 * Skyline CRM - page renderers
 *
 * Each entry fetches its data from the API and writes the rows into the
 * static markup of the matching HTML page. The row templates are carried
 * over unchanged from the original Views.java table builders.
 */

const SkylinePages = {};

/* Java: Views.navLink + Views.navButton targets */
function pageUrl(path, role) {
  return path + '.html?role=' + encodeRole(role);
}

/* Renders the list rows of a plain table. */
async function loadRows(resource, render) {
  const body = document.getElementById('rows');
  try {
    const data = await Api.get('/' + resource);
    if (!data.items || data.items.length === 0) {
      body.innerHTML = '<tr><td colspan="10" class="subtle">No records yet. Use the form above to add one.</td></tr>';
      return [];
    }
    body.innerHTML = data.items.map(render).join('');
    return data.items;
  } catch (err) {
    body.innerHTML = '<tr><td colspan="10"><div class="error-box">' + escape(err.message) + '</div></td></tr>';
    return [];
  }
}

/* Renders a set of metric cards into #cards. */
function renderCards(cards) {
  document.getElementById('cards').innerHTML = cards.join('');
}

/* ------------------------------------------------------------------ */
/* Dashboard                                                           */
/* ------------------------------------------------------------------ */

SkylinePages.dashboard = async function (role) {
  const data = await Api.get('/dashboard');
  const main = document.querySelector('main.content');
  const metrics = Array.isArray(data.metrics) ? data.metrics : [];
  const workspaceData = Array.isArray(data.workspaceRows) ? data.workspaceRows : [];
  const analytics = data.analytics || { title: 'Analytics', charts: [] };
  const primaryAction = data.primaryAction || { path: 'enquiries', label: 'Add Enquiry' };

  const focus = '<div class="page-header">\n' +
    '  <div>\n' +
    '    <h1>Dashboard</h1>\n' +
    '    <p class="subtle">' + escape(data.focus) + '</p>\n' +
    '  </div>\n' +
    '  <a class="button" href="' + pageUrl(primaryAction.path, role) + '">' +
    escape(primaryAction.label) + '</a>\n' +
    '</div>';

  const cards = '<section class="grid cards">\n' +
    metrics.map(m => metric(m.label, m.value)).join('') +
    '</section>';

  const workspaceRows = workspaceData.map(row =>
    '<tr><td>' + escape(row[0]) + '</td><td>' + escape(row[1]) + '</td><td>' + escape(row[2]) + '</td></tr>'
  ).join('');

  const workspace = '<section class="section">\n' +
    '  <h2>Role Workspace</h2>\n' +
    '  <div class="table-wrap">\n' +
    '    <table>\n' +
    '      <thead><tr><th>Area</th><th>Available Now</th><th>Planned Next</th></tr></thead>\n' +
    '      <tbody>' + workspaceRows + '</tbody>\n' +
    '    </table>\n' +
    '  </div>\n' +
    '</section>';

  main.innerHTML = focus + cards + workspace + analyticsSection(analytics);
};

/* ------------------------------------------------------------------ */
/* Finance dashboard                                                   */
/* ------------------------------------------------------------------ */

SkylinePages['finance-dashboard'] = async function (role) {
  const main = document.querySelector('main.content');
  let data;
  try {
    data = await Api.get('/finance-dashboard');
  } catch (err) {
    if (err.status === 403) {
      main.setAttribute('aria-label', 'Access denied');
      main.innerHTML = '<section class="section"><h1>Access denied</h1>' +
        '<p class="subtle">' + escape(err.message) + '</p></section>';
      return;
    }
    throw err;
  }

  const action = data.actions.length > 0
    ? '<a class="button" href="' + pageUrl(data.actions[0].path, role) + '">' +
      escape(data.actions[0].label) + '</a>'
    : '';

  const cards = data.cards.map(c => moneyMetric(c.label, c.value)).join('') +
    '<article class="card"><p class="subtle">' + escape(data.result.label) + '</p>' +
    '<div class="metric"><span class="status ' + escape(data.result.statusClass) + '">' +
    escape(moneyInt(data.result.value)) + '</span></div></article>';

  const collectionRows = data.collections.map(row =>
    '<tr><td>' + escape(row.customer) + '</td><td>' + escape(row.installmentType) + '</td><td>' +
    escape(money(row.paidAmount)) + '</td><td>' + escape(money(row.pendingAmount)) + '</td></tr>'
  ).join('');

  const expenseRows = data.expenses.map(row =>
    '<tr><td>' + escape(row.type) + '</td><td>' + escape(row.reference) + '</td><td>' +
    escape(row.date) + '</td><td>' + escape(money(row.amount)) + '</td></tr>'
  ).join('');

  main.innerHTML =
    '<div class="page-header"><div><h1>Finance Dashboard</h1>' +
    '<p class="subtle">' + escape(data.managerNote) + '</p></div>' + action + '</div>\n' +
    '<section class="grid cards">\n' + cards + '\n</section>\n' +
    '<section class="section">\n' +
    '  <h2>Customer Collections</h2>\n' +
    '  <div class="table-wrap"><table><thead><tr><th>Customer</th><th>Installment</th>' +
    '<th>Paid</th><th>Pending</th></tr></thead><tbody>' + collectionRows + '</tbody></table></div>\n' +
    '</section>\n' +
    '<section class="section">\n' +
    '  <h2>Recent Expenses</h2>\n' +
    '  <div class="table-wrap"><table><thead><tr><th>Type</th><th>Reference</th>' +
    '<th>Date</th><th>Amount</th></tr></thead><tbody>' + expenseRows + '</tbody></table></div>\n' +
    '</section>';
};

/* ------------------------------------------------------------------ */
/* Sales modules                                                       */
/* ------------------------------------------------------------------ */

SkylinePages.enquiries = async function () {
  const rows = await loadRows('enquiries', (e, index) =>
    '<tr>\n' +
    '  <td>' + escape(e.name) + '</td><td>' + escape(e.mobile) + '<br>' + escape(e.email) + '</td>' +
    '<td>' + escape(e.source) + '</td><td>' + escape(e.interestedProperty) + '</td>' +
    '<td>' + escape(e.budget) + '</td><td><span class="status status-info">' + escape(e.status) + '</span></td>' +
    '<td><button type="button" class="button button-small" data-edit-index="' + index + '">Edit</button> ' +
    '<button type="button" class="button button-small button-danger" data-delete-index="' + index + '">Delete</button></td>\n' +
    '</tr>\n');
  bindEditableRows('enquiries', 'enquiry-form', rows);
};

SkylinePages.properties = async function (role) {
  const form = document.getElementById('property-form');
  if (role !== 'ADMIN_OWNER' && form) {
    form.hidden = true;
  }
  const rows = await loadRows('properties', (p, index) =>
    '<tr>\n' +
    '  <td>' + escape(p.unit) + '</td><td>' + escape(p.wing) + '</td><td>' + escape(p.floor) + '</td>' +
    '<td>' + escape(p.flatType) + '</td><td>' + escape(p.area) + '</td><td>' + escape(p.price) + '</td>' +
    '<td><span class="status ' + statusClass('properties', p.availability) + '">' + escape(p.availability) + '</span></td>' +
    '<td><button type="button" class="button button-small" data-edit-index="' + index + '">Edit</button> ' +
    '<button type="button" class="button button-small button-danger" data-delete-index="' + index + '">Delete</button></td>\n' +
    '</tr>\n');
  bindEditableRows('properties', 'property-form', rows);
};

SkylinePages.followups = async function () {
  const rows = await loadRows('followups', (f, index) =>
    '<tr>\n' +
    '  <td>' + escape(f.customer) + '</td><td>' + escape(f.type) + '</td>' +
    '<td>' + escape(f.date) + ' at ' + escape(f.time) + '</td>' +
    '<td>' + escape(f.nextDate) + '</td><td>' + escape(f.executive) + '</td>' +
    '<td>' + escape(f.remarks) + '</td>' +
    '<td><button type="button" class="button button-small" data-edit-index="' + index + '">Edit</button> ' +
    '<button type="button" class="button button-small button-danger" data-delete-index="' + index + '">Delete</button></td>\n' +
    '</tr>\n');
  bindEditableRows('followups', 'followup-form', rows);
};

SkylinePages.qualification = async function () {
  const rows = await loadRows('qualification', (q, index) =>
    '<tr>\n' +
    '  <td>' + escape(q.customer) + '</td><td>' + escape(q.budget) + '</td>' +
    '<td>' + escape(q.loanRequired) + '</td><td>' + escape(q.preferredLocation) + '</td>' +
    '<td>' + escape(q.flatType) + '</td><td>' + escape(q.purchaseTimeline) + '</td>' +
    '<td><span class="status ' + statusClass('qualification', q.status) + '">' + escape(q.status) + '</span></td>' +
    '<td><button type="button" class="button button-small" data-edit-index="' + index + '">Edit</button> ' +
    '<button type="button" class="button button-small button-danger" data-delete-index="' + index + '">Delete</button></td>\n' +
    '</tr>\n');
  bindEditableRows('qualification', 'qualification-form', rows);
};

SkylinePages['site-visits'] = async function () {
  const rows = await loadRows('site-visits', (v, index) =>
    '<tr>\n' +
    '  <td>' + escape(v.customer) + '</td><td>' + escape(v.visitDate) + ' at ' + escape(v.visitTime) + '</td>' +
    '<td>' + escape(v.pickupRequired) + '</td><td>' + escape(v.executive) + '</td>' +
    '<td><span class="status ' + statusClass('site-visits', v.status) + '">' + escape(v.status) + '</span></td>' +
    '<td>' + escape(v.feedback) + '</td>' +
    '<td><button type="button" class="button button-small" data-edit-index="' + index + '">Edit</button> ' +
    '<button type="button" class="button button-small button-danger" data-delete-index="' + index + '">Delete</button></td>\n' +
    '</tr>\n');
  bindEditableRows('site-visits', 'site-visit-form', rows);
};

SkylinePages.negotiations = async function () {
  const rows = await loadRows('negotiations', (n, index) =>
    '<tr><td>' + escape(n.customer) + '</td><td>' + escape(n.unit) + '</td><td>' + escape(n.offeredPrice) +
    '</td><td>' + escape(n.discount) + '</td><td><span class="status ' +
    statusClass('negotiations', n.managementApproval) + '">' + escape(n.managementApproval) +
    '</span></td><td>' + escape(n.specialOffer) + '</td>' +
    '<td><button type="button" class="button button-small" data-edit-index="' + index + '">Edit</button> ' +
    '<button type="button" class="button button-small button-danger" data-delete-index="' + index + '">Delete</button></td></tr>\n');
  bindEditableRows('negotiations', 'negotiation-form', rows);
};

SkylinePages.bookings = async function () {
  const rows = await loadRows('bookings', (b, index) =>
    '<tr><td>' + escape(b.customer) + '</td><td>' + escape(b.unit) + '</td><td>' + escape(b.floor) +
    '</td><td>' + escape(b.flatType) + '</td><td>' + escape(b.bookingAmount) + '</td><td>' +
    escape(b.bookingDate) + '</td><td><span class="status ' +
    statusClass('bookings', b.status) + '">' + escape(b.status) + '</span></td>' +
    '<td><button type="button" class="button button-small" data-edit-index="' + index + '">Edit</button> ' +
    '<button type="button" class="button button-small button-danger" data-delete-index="' + index + '">Delete</button></td></tr>\n');
  bindEditableRows('bookings', 'booking-form', rows);
};

function fileLink(value) {
  if (!value) return '<span class="status status-warn">Not uploaded</span>';
  const filename = value.split('/').pop() || value;
  const href = value.startsWith('/uploads/') ? API_BASE.replace(/\/api$/, '') + value : value;
  return '<a href="' + escape(href) + '" target="_blank" rel="noopener" class="button button-small">View</a> <span class="subtle">' + escape(filename) + '</span>';
}

SkylinePages.documents = async function () {
  const rows = await loadRows('documents', (d, index) =>
    '<tr><td>' + escape(d.customerId) + '</td><td>' + escape(d.customer) + '</td><td>' + escape(d.bookingId || '') +
    '</td><td>' + escape(d.documentType) + '</td><td>' + fileLink(d.filePath) + '</td><td>' + escape(d.originalFileName || '') +
    '</td><td><span class="status ' + statusClass('documents', d.verificationStatus) + '">' + escape(d.verificationStatus) + '</span></td>' +
    '<td>' + escape(d.uploadedBy || '') + '</td><td>' + escape(d.uploadedDate || '') + '</td><td>' + escape(d.version) + '</td>' +
    '<td><button type="button" class="button button-small" data-edit-index="' + index + '">Edit</button> ' +
    '<button type="button" class="button button-small button-danger" data-delete-index="' + index + '">Delete</button></td></tr>\n');
  bindEditableRows('documents', 'documents-form', rows);
};

SkylinePages.loans = async function () {
  const rows = await loadRows('loans', (l, index) =>
    '<tr><td>' + escape(l.customer) + '</td><td>' + escape(l.bankName) + '</td><td>' + escape(l.loanAmount) +
    '</td><td>' + escape(l.emi) + '</td><td>' + escape(l.sanctionDate) + '</td><td><span class="status ' +
    statusClass('loans', l.status) + '">' + escape(l.status) + '</span></td>' +
    '<td><button type="button" class="button button-small" data-edit-index="' + index + '">Edit</button> ' +
    '<button type="button" class="button button-small button-danger" data-delete-index="' + index + '">Delete</button></td></tr>\n');
  bindEditableRows('loans', 'loan-form', rows);
};

SkylinePages.agreements = async function () {
  const rows = await loadRows('agreements', (a, index) =>
    '<tr>\n' +
    '  <td>' + escape(a.customer) + '</td><td>' + escape(a.unit) + '</td><td>' + escape(a.agreementDate) +
    '</td><td>' + escape(a.stampDuty) + '</td><td>' + escape(a.registrationNumber) + '</td>' +
    '<td><button type="button" class="button button-small" data-edit-index="' + index + '">Edit</button> ' +
    '<button type="button" class="button button-small button-danger" data-delete-index="' + index + '">Delete</button></td></tr>\n');
  bindEditableRows('agreements', 'agreement-form', rows);
};

SkylinePages.payments = async function (role) {
  const data = await Api.get('/payments');
  renderCards([
    metric('Paid Amount', data.totalPaid),
    metric('Pending Amount', data.totalPending)
  ]);
  document.getElementById('rows').innerHTML = data.items.map((p, index) =>
    '<tr><td>' + escape(p.customer) + '</td><td>' + escape(p.installmentType) + '</td><td>' +
    escape(p.totalCost) + '</td><td>' + escape(p.paidAmount) + '</td><td>' + escape(p.pendingAmount) +
    '</td><td>' + escape(p.dueDate) + '</td><td><button type="button" class="button button-small" data-edit-index="' + index + '">Edit</button> ' +
    '<button type="button" class="button button-small button-danger" data-delete-index="' + index + '">Delete</button></td></tr>\n'
  ).join('');
  bindEditableRows('payments', 'payment-form', data.items);
  if (role === 'AGENT') {
    const form = document.getElementById('payment-form');
    if (form) form.hidden = true;
    document.querySelectorAll('#payments-form, [data-endpoint="payments"] .button').forEach(button => button.disabled = true);
  }
};

SkylinePages.possession = async function () {
  const rows = await loadRows('possession', (p, index) =>
    '<tr>\n' +
    '  <td>' + escape(p.customer) + '</td><td>' + escape(p.unit) + '</td><td>' + yesNo(p.finalInspection) +
    '</td><td>' + yesNo(p.utilityConnection) + '</td><td>' + yesNo(p.keyHandover) + '</td><td>' +
    yesNo(p.possessionLetter) + '</td><td><span class="status ' +
    statusClass('possession', p.status) + '">' + escape(p.status) + '</span></td><td>' +
    escape(p.remarks) + '</td>' +
    '<td><button type="button" class="button button-small" data-edit-index="' + index + '">Edit</button> ' +
    '<button type="button" class="button button-small button-danger" data-delete-index="' + index + '">Delete</button></td>\n' +
    '</tr>\n');
  bindEditableRows('possession', 'possession-form', rows);
};

SkylinePages.support = async function () {
  const rows = await loadRows('support', (t, index) =>
    '<tr><td>' + escape(t.customer) + '</td><td>' + escape(t.unit) + '</td><td>' + escape(t.category) +
    '</td><td>' + escape(t.priority) + '</td><td><span class="status ' +
    statusClass('support', t.status) + '">' + escape(t.status) + '</span></td><td>' +
    escape(t.assignedTo) + '</td><td>' + escape(t.description) + '</td>' +
    '<td><button type="button" class="button button-small" data-edit-index="' + index + '">Edit</button> ' +
    '<button type="button" class="button button-small button-danger" data-delete-index="' + index + '">Delete</button></td></tr>\n');
  bindEditableRows('support', 'support-form', rows);
};

/* ------------------------------------------------------------------ */
/* HR and payroll modules                                              */
/* ------------------------------------------------------------------ */

SkylinePages.employees = async function () {
  const rows = await loadRows('employees', (e, index) =>
    '<tr><td>' + escape(e.employeeId) + '</td><td>' + escape(e.name) + '</td><td>' + escape(e.department) +
    '</td><td>' + escape(e.designation) + '</td><td>' + escape(e.mobile) + '<br>' + escape(e.email) +
    '</td><td>' + escape(e.dateOfJoining) + '</td><td>' + escape(e.salaryStructure) + '</td>' +
    '<td><button type="button" class="button button-small" data-edit-index="' + index + '">Edit</button> ' +
    '<button type="button" class="button button-small button-danger" data-delete-index="' + index + '">Delete</button></td></tr>\n');
  bindEditableRows('employees', 'employee-form', rows);
};

SkylinePages.attendance = async function () {
  const rows = await loadRows('attendance', (a, index) =>
    '<tr><td>' + escape(a.employeeId) + '</td><td>' + escape(a.employeeName) + '</td><td>' + escape(a.date) +
    '</td><td><span class="status ' + statusClass('attendance', a.status) + '">' + escape(a.status) +
    '</span></td><td>' + escape(a.overtimeHours) + '</td><td>' + escape(a.remarks) + '</td>' +
    '<td><button type="button" class="button button-small" data-edit-index="' + index + '">Edit</button> ' +
    '<button type="button" class="button button-small button-danger" data-delete-index="' + index + '">Delete</button></td></tr>\n');
  bindEditableRows('attendance', 'attendance-form', rows);
};

SkylinePages.leaves = async function () {
  const rows = await loadRows('leaves', (l, index) =>
    '<tr><td>' + escape(l.employeeId) + '</td><td>' + escape(l.employeeName) + '</td><td>' +
    escape(l.fromDate) + ' to ' + escape(l.toDate) + '</td><td>' + escape(l.leaveType) +
    '</td><td><span class="status ' + statusClass('leaves', l.status) + '">' + escape(l.status) +
    '</span></td><td>' + escape(l.reason) + '</td>' +
    '<td><button type="button" class="button button-small" data-edit-index="' + index + '">Edit</button> ' +
    '<button type="button" class="button button-small button-danger" data-delete-index="' + index + '">Delete</button></td></tr>\n');
  bindEditableRows('leaves', 'leave-form', rows);
};

SkylinePages.salary = async function () {
  const data = await Api.get('/salary');
  renderCards([
    metric('Gross Salary', data.totalGross),
    metric('Net Salary', data.totalNet)
  ]);
  document.getElementById('rows').innerHTML = data.items.map((s, index) =>
    '<tr><td>' + escape(s.employeeName) + '</td><td>' + escape(s.salaryMonth) + '</td><td>' +
    escape(s.grossSalary) + '</td><td>' + escape(s.deductions) + '</td><td>' + escape(s.netSalary) +
    '</td><td>' + escape(s.paymentMode) + '</td><td><span class="status ' +
    statusClass('salary', s.paymentStatus) + '">' + escape(s.paymentStatus) + '</span></td>' +
    '<td><button type="button" class="button button-small" onclick="printSalarySlip(' + escape(s.id) + ')">Print Slip</button></td></tr>\n'
  ).join('');
};

async function printSalarySlip(id) {
  const popup = window.open('about:blank', '_blank');
  if (!popup) {
    alert('Please allow pop-ups to print the salary slip.');
    return;
  }
  popup.document.write('<p style="font-family:Arial;padding:24px">Loading salary slip...</p>');
  try {
    const response = await fetch(API_BASE + '/salary/' + encodeURIComponent(id) + '/slip', {
      method: 'GET',
      headers: Api.headers()
    });
    const html = await response.text();
    if (!response.ok) {
      throw new Error(html || 'Unable to load salary slip.');
    }
    popup.document.open();
    popup.document.write(html);
    popup.document.close();
    popup.focus();
  } catch (err) {
    popup.close();
    alert(err.message || 'Unable to load salary slip.');
  }
}

/* ------------------------------------------------------------------ */
/* Accounts modules                                                    */
/* ------------------------------------------------------------------ */

SkylinePages.vendors = async function () {
  const rows = await loadRows('vendors', (v, index) =>
    '<tr><td>' + escape(v.vendorName) + '</td><td>' + escape(v.companyName) + '</td><td>' +
    escape(v.category) + '</td><td>' + escape(v.contactPerson) + '<br>' + escape(v.mobile) + '</td><td>' +
    escape(v.gstNumber) + '</td><td>' + escape(v.bankDetails) + '</td>' +
    '<td><button type="button" class="button button-small" data-edit-index="' + index + '">Edit</button> ' +
    '<button type="button" class="button button-small button-danger" data-delete-index="' + index + '">Delete</button></td></tr>\n');
  bindEditableRows('vendors', 'vendor-form', rows);
};

SkylinePages['purchase-orders'] = async function () {
  const rows = await loadRows('purchase-orders', (p, index) =>
    '<tr><td>' + escape(p.poNumber) + '</td><td>' + escape(p.date) + '</td><td>' + escape(p.vendorName) +
    '</td><td>' + escape(p.material) + '</td><td>' + escape(p.quantity) + '</td><td>' + escape(p.gst) +
    '</td><td>' + escape(p.totalAmount) + '</td>' +
    '<td><button type="button" class="button button-small" data-edit-index="' + index + '">Edit</button> ' +
    '<button type="button" class="button button-small button-danger" data-delete-index="' + index + '">Delete</button></td></tr>\n');
  bindEditableRows('purchase-orders', 'purchase-order-form', rows);
};

SkylinePages['vendor-bills'] = async function () {
  const data = await Api.get('/vendor-bills');
  renderCards([
    metric('Vendor Bills', data.billCount),
    metric('Outstanding', data.outstanding)
  ]);
  const rows = data.items.map((b, index) =>
    '<tr><td>' + escape(b.vendorName) + '</td><td>' + escape(b.invoiceNumber) + '</td><td>' +
    escape(b.invoiceDate) + '</td><td>' + escape(b.billAmount) + '</td><td>' + escape(b.dueDate) +
    '</td><td><span class="status ' + statusClass('vendor-bills', b.status) + '">' + escape(b.status) +
    '</span></td>' +
    '<td><button type="button" class="button button-small" data-edit-index="' + index + '">Edit</button> ' +
    '<button type="button" class="button button-small button-danger" data-delete-index="' + index + '">Delete</button></td></tr>\n'
  ).join('');
  document.getElementById('rows').innerHTML = rows;
  bindEditableRows('vendor-bills', 'vendor-bill-form', data.items);
};

SkylinePages['vendor-payments'] = async function () {
  const data = await Api.get('/vendor-payments');
  renderCards([
    metric('Paid', data.totalPaid),
    metric('Balance', data.totalBalance)
  ]);
  const rows = data.items.map((p, index) =>
    '<tr><td>' + escape(p.vendorName) + '</td><td>' + escape(p.paymentDate) + '</td><td>' +
    escape(p.paymentMode) + '</td><td>' + escape(p.transactionNumber) + '</td><td>' +
    escape(p.paidAmount) + '</td><td><span class="status ' +
    statusClass('vendor-payments', p.status) + '">' + escape(p.status) + '</span></td>' +
    '<td><button type="button" class="button button-small" data-edit-index="' + index + '">Edit</button> ' +
    '<button type="button" class="button button-small button-danger" data-delete-index="' + index + '">Delete</button></td></tr>\n'
  ).join('');
  document.getElementById('rows').innerHTML = rows;
  bindEditableRows('vendor-payments', 'vendor-payment-form', data.items);
};

SkylinePages['petty-cash'] = async function () {
  const data = await Api.get('/petty-cash');
  renderCards([
    metric('Entries', data.entryCount),
    metric('Cash Spent', data.cashSpent)
  ]);
  const rows = data.items.map((e, index) =>
    '<tr><td>' + escape(e.date) + '</td><td>' + escape(e.voucherNumber) + '</td><td>' +
    escape(e.expenseCategory) + '</td><td>' + escape(e.employeeName) + '</td><td>' +
    escape(e.amount) + '</td><td>' + escape(e.approvedBy) + '</td>' +
    '<td><button type="button" class="button button-small" data-edit-index="' + index + '">Edit</button> ' +
    '<button type="button" class="button button-small button-danger" data-delete-index="' + index + '">Delete</button></td></tr>\n'
  ).join('');
  document.getElementById('rows').innerHTML = rows;
  bindEditableRows('petty-cash', 'petty-cash-form', data.items);
};

SkylinePages['petty-cash-approvals'] = async function () {
  const rows = await loadRows('petty-cash-approvals', (a, index) =>
    '<tr><td>' + escape(a.requestedBy) + '</td><td>' + escape(a.approvedBy) + '</td><td>' +
    escape(a.amount) + '</td><td><span class="status ' +
    statusClass('petty-cash-approvals', a.status) + '">' + escape(a.status) + '</span></td><td>' +
    escape(a.remarks) + '</td>' +
    '<td><button type="button" class="button button-small" data-edit-index="' + index + '">Edit</button> ' +
    '<button type="button" class="button button-small button-danger" data-delete-index="' + index + '">Delete</button></td></tr>\n');
  bindEditableRows('petty-cash-approvals', 'petty-cash-approval-form', rows);
};

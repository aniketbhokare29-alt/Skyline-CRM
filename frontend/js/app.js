/*
 * Skyline CRM - page bootstrap
 *
 * Rebuilds the shared page frame that the backend used to render:
 *   Views.layout(role, title, body)  -> the .shell / .topbar / .layout markup
 *   Views.nav(role)                  -> the role-specific .sidebar
 *
 * Both come from the API, so the navigation rules stay on the server.
 */

const ROLE_KEY = 'crm_role';

document.addEventListener('DOMContentLoaded', async function () {
  const page = document.body.dataset.page;
  const title = document.body.dataset.title || 'Skyline CRM';
  const shell = document.getElementById('shell');
  const content = document.getElementById('page');

  initPasswordToggles();

  /* Sign Out is rendered by the shell, so intercept it here. */
  document.addEventListener('click', async function (event) {
    const link = event.target.closest('a[href="/logout"]');
    if (!link) {
      return;
    }
    event.preventDefault();
    await Api.logout();
    sessionStorage.removeItem(ROLE_KEY);
    location.href = 'index.html';
  });

  /* Login is public. User creation is protected by the backend and also
   * checked here so an employee/agent cannot use the page directly. */
  if (page === 'login') {
    initLogin();
    return;
  }

  if (page === 'register') {
    try {
      const session = await Api.get('/session');
      if (session.role !== 'ADMIN_OWNER') {
        document.getElementById('register-message').innerHTML = errorBox('Only the Admin Owner can create users.');
        document.getElementById('register-form').querySelector('button').disabled = true;
        return;
      }
      initRegister();
    } catch (err) {
      location.href = 'index.html';
    }
    return;
  }

  /* Everything else needs a session. The server is always asked, because a
   * cached role can outlive the server session that produced it. */
  let role = '';
  try {
    const session = await Api.get('/session');
    role = session.role;
    sessionStorage.setItem(ROLE_KEY, role);
  } catch (err) {
    Api.clearToken();
    sessionStorage.removeItem(ROLE_KEY);
    location.href = 'index.html';
    return;
  }

  let nav;
  try {
    nav = await Api.get('/nav');
  } catch (err) {
    Api.clearToken();
    sessionStorage.removeItem(ROLE_KEY);
    location.href = 'index.html';
    return;
  }

  const allowedPaths = nav.flatMap(section => section.items || []).map(item => item.path);
  const pageAllowed = page === 'dashboard' || allowedPaths.includes('/' + page);
  if (!pageAllowed) {
    content.innerHTML = '<section class="section"><h1>Access denied</h1>' +
      '<p class="subtle">Your role does not have access to this module.</p></section>';
  }

  const body = content.innerHTML;
  shell.innerHTML = layout(role, title, nav, body);
  content.remove();

  const main = document.querySelector('main.content');
  applyDateDefaults(main);

  const form = main.querySelector('form[data-endpoint]');
  if (form) {
    bindForm(form, form.dataset.endpoint, form.dataset.redirect);
  }

  const loader = typeof SkylinePages === 'undefined' ? null : SkylinePages[page];
  if (loader && pageAllowed) {
    try {
      await loader(role);
    } catch (err) {
      const currentMain = document.querySelector('main.content');
      if (currentMain) {
        currentMain.innerHTML = '<section class="section"><h1>' + escape(title) + '</h1>' +
          '<div class="error-box">Unable to load this page: ' + escape(err.message || 'Unknown server error') + '</div>' +
          '<p class="subtle">Check that the API and database are running, then refresh this page.</p></section>';
      }
    }
  }
});

function initPasswordToggles() {
  document.querySelectorAll('.password-toggle').forEach(toggle => {
    toggle.addEventListener('click', () => {
      const input = toggle.parentElement.querySelector('input');
      const visible = input.type === 'text';
      input.type = visible ? 'password' : 'text';
      toggle.setAttribute('aria-label', visible ? 'Show password' : 'Hide password');
      toggle.setAttribute('title', visible ? 'Show password' : 'Hide password');
    });
  });
}

/* Java: Views.layout */
function layout(role, title, nav, body) {
  return '<div class="shell">\n' +
    '  <header class="topbar">\n' +
    '    <div class="brand">Skyline CRM</div>\n' +
    '    <div><span class="role-pill">' + escape(role) + '</span> ' +
    '<a class="button button-secondary" href="/logout">Sign Out</a></div>\n' +
    '  </header>\n' +
    '  <div class="layout">\n' +
    '    ' + sidebar(role, nav) + '\n' +
    '    <main class="content" aria-label="' + escape(title) + '">' + body + '</main>\n' +
    '  </div>\n' +
    '</div>';
}

/* Java: Views.nav */
function sidebar(role, sections) {
  let html = '<aside class="sidebar">';
  for (const section of sections) {
    if (section.title && section.title.trim() !== '') {
      html += '<div class="nav-title">' + escape(section.title) + '</div>';
    }
    for (const item of section.items) {
      html += navLink(role, item.path, item.label);
    }
  }
  return html + '</aside>';
}

/* Java: Views.navLink */
function navLink(role, path, label) {
  return '<a class="nav-link" href="' + path + '.html?role=' + encodeRole(role) + '">' + escape(label) + '</a>';
}

/* ------------------------------------------------------------------ */
/* Login page                                                          */
/* ------------------------------------------------------------------ */

function initLogin() {
  const form = document.getElementById('login-form');
  const box = document.getElementById('login-message');
  const roleSelect = document.getElementById('role');

  /* The original redirected to /dashboard after a successful sign-in. */
  if (Api.token() && sessionStorage.getItem(ROLE_KEY)) {
    location.href = 'dashboard.html';
    return;
  }

  /* After registering, the app comes back here with the chosen role and a notice. */
  const params = new URLSearchParams(location.search);
  if (params.get('role')) {
    roleSelect.value = params.get('role');
  }
  if (params.get('message')) {
    box.innerHTML = errorBox(params.get('message'));
  }

  form.addEventListener('submit', async function (event) {
    event.preventDefault();
    box.innerHTML = '';
    const button = form.querySelector('button[type="submit"]');
    button.disabled = true;
    try {
      const data = await Api.login(roleSelect.value, form.email.value, form.password.value);
      Api.setToken(data.token);
      sessionStorage.setItem(ROLE_KEY, data.role);
      location.href = 'dashboard.html';
    } catch (err) {
      button.disabled = false;
      box.innerHTML = errorBox(err.message);
    }
  });
}

/* ------------------------------------------------------------------ */
/* Register page                                                       */
/* ------------------------------------------------------------------ */

function initRegister() {
  const form = document.getElementById('register-form');
  const box = document.getElementById('register-message');

  form.addEventListener('submit', async function (event) {
    event.preventDefault();
    box.innerHTML = '';
    const button = form.querySelector('button[type="submit"]');
    button.disabled = true;
    try {
      const data = await Api.register(formValues(form));
      const role = form.role.value;
      location.href = 'index.html?role=' + encodeRole(role) +
        '&message=' + encodeURIComponent(data.message);
    } catch (err) {
      button.disabled = false;
      box.innerHTML = errorBox(err.message);
    }
  });
}

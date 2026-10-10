/*
 * Skyline CRM - API client
 *
 * All backend communication happens here. Uses fetch() only, no libraries.
 */

/* Use the separate local API during development and same-origin API in deployment. */
const API_BASE = (typeof window !== 'undefined' && window.CRM_API_BASE
  ? window.CRM_API_BASE
  : typeof location !== 'undefined' && location.hostname
    ? ((location.port === '5500' || location.port === '5501')
      ? location.protocol + '//' + location.hostname + ':8080/api'
      : location.origin + '/api')
    : 'http://localhost:8080/api');
const TOKEN_KEY = 'crm_session';

const Api = {
  token() {
    return sessionStorage.getItem(TOKEN_KEY) || '';
  },

  setToken(token) {
    sessionStorage.setItem(TOKEN_KEY, token);
  },

  clearToken() {
    sessionStorage.removeItem(TOKEN_KEY);
  },

  /** Normalizes "/enquiries" vs "enquiries" so data-endpoint works either way. */
  normalize(path) {
    if (!path) {
      return '/';
    }
    return path.startsWith('/') ? path : '/' + path;
  },

  /** GET a JSON endpoint, adding the session token. */
  async get(path) {
    const res = await fetch(API_BASE + Api.normalize(path), {
      method: 'GET',
      headers: Api.headers()
    });
    return Api.unwrap(res);
  },

  /** POST form fields, adding the session token. */
  async post(path, params) {
    const res = await fetch(API_BASE + Api.normalize(path), {
      method: 'POST',
      headers: Api.headers(true),
      body: new URLSearchParams(params).toString()
    });
    return Api.unwrap(res);
  },

  /** POST FormData (for file uploads), adding the session token. */
  async postFormData(path, formData) {
    const headers = { 'X-Session-Token': Api.token() };
    const res = await fetch(API_BASE + Api.normalize(path), {
      method: 'POST',
      headers,
      body: formData
    });
    return Api.unwrap(res);
  },

  async put(path, params) {
    const res = await fetch(API_BASE + Api.normalize(path), {
      method: 'PUT',
      headers: Api.headers(true),
      body: new URLSearchParams(params).toString()
    });
    return Api.unwrap(res);
  },

  async delete(path) {
    const res = await fetch(API_BASE + Api.normalize(path), {
      method: 'DELETE',
      headers: Api.headers()
    });
    return Api.unwrap(res);
  },

  headers(withBody) {
    const headers = { 'X-Session-Token': Api.token() };
    if (withBody) {
      headers['Content-Type'] = 'application/x-www-form-urlencoded;charset=UTF-8';
    }
    return headers;
  },

  async unwrap(res) {
    const text = await res.text();
    let data = {};
    if (text) {
      try {
        data = JSON.parse(text);
      } catch (err) {
        data = { error: text };
      }
    }
    if (!res.ok) {
      const error = new Error(data.error || ('Request failed: ' + res.status));
      error.status = res.status;
      throw error;
    }
    return data;
  },

  /** Sign in. Returns { token, role }. Throws on invalid password. */
  async login(role, email, password) {
    const res = await fetch(API_BASE + '/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
      body: new URLSearchParams({ role: role, email: email, password: password }).toString()
    });
    return Api.unwrap(res);
  },

  /** Create an account. Throws on password mismatch. */
  async register(form) {
    const res = await fetch(API_BASE + '/register', {
      method: 'POST',
      headers: Api.headers(true),
      body: new URLSearchParams(form).toString()
    });
    return Api.unwrap(res);
  },

  async logout() {
    try {
      await Api.post('/logout', {});
    } catch (err) {
      /* signing out locally is enough even if the call fails */
    }
    Api.clearToken();
  }
};

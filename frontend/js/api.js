/**
 * Thin wrapper around fetch with automatic JWT handling.
 *
 * Flow:
 *  1. Adds Authorization: Bearer <accessToken> to every request
 *  2. On 401 — attempts refresh with the stored refresh token
 *  3. If refresh succeeds — retries the original request once
 *  4. If refresh fails — clears tokens and redirects to login
 */

const API_BASE = 'http://localhost:8080/api';
const STORAGE = sessionStorage;

const Tokens = {
    get access() { return STORAGE.getItem('accessToken'); },
    get refresh() { return STORAGE.getItem('refreshToken'); },

    set({ accessToken, refreshToken }) {
        STORAGE.setItem('accessToken', accessToken);
        STORAGE.setItem('refreshToken', refreshToken);
    },

    clear() {
        STORAGE.removeItem('accessToken');
        STORAGE.removeItem('refreshToken');
    }
};

let refreshPromise = null;

async function refreshAccessToken() {
    // Prevent multiple parallel refresh calls
    if (refreshPromise) return refreshPromise;

    refreshPromise = (async () => {
        const refreshToken = Tokens.refresh;
        if (!refreshToken) throw new Error('No refresh token');

        const response = await fetch(`${API_BASE}/auth/refresh`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ refreshToken })
        });

        if (!response.ok) throw new Error('Refresh failed');

        const data = await response.json();
        Tokens.set(data);
        return data.accessToken;
    })();

    try {
        return await refreshPromise;
    } finally {
        refreshPromise = null;
    }
}

function redirectToLogin() {
    Tokens.clear();
    if (!window.location.pathname.endsWith('/index.html')
        && window.location.pathname !== '/') {
        window.location.href = 'index.html';
    }
}

/**
 * Main request function.
 * @param {string} path - path relative to API_BASE (e.g. '/jobs')
 * @param {object} options - fetch options
 * @param {boolean} skipAuth - if true, does not add Authorization header
 * @returns {Promise<any>} parsed JSON body, or null for 204
 */
async function apiRequest(path, options = {}, skipAuth = false) {
    const url = `${API_BASE}${path}`;

    const doFetch = async (token) => {
        const headers = { ...(options.headers || {}) };
        if (!skipAuth && token) headers['Authorization'] = `Bearer ${token}`;
        if (options.body && !(options.body instanceof FormData)) {
            headers['Content-Type'] = headers['Content-Type'] || 'application/json';
        }
        return fetch(url, { ...options, headers });
    };

    let response = await doFetch(Tokens.access);

    // Try refresh once on 401
    if (response.status === 401 && !skipAuth && Tokens.refresh) {
        try {
            const newToken = await refreshAccessToken();
            response = await doFetch(newToken);
        } catch {
            redirectToLogin();
            throw new ApiError(401, 'Session expired');
        }
    }

    if (response.status === 401 && !skipAuth) {
        redirectToLogin();
        throw new ApiError(401, 'Unauthorized');
    }

    if (!response.ok) {
        let message = `Request failed (${response.status})`;
        try {
            const data = await response.json();
            if (data.message) message = data.message;
        } catch { /* body wasn't JSON */ }
        throw new ApiError(response.status, message);
    }

    if (response.status === 204) return null;

    const contentType = response.headers.get('Content-Type') || '';
    if (contentType.includes('application/json')) {
        return response.json();
    }
    return response;
}

class ApiError extends Error {
    constructor(status, message) {
        super(message);
        this.status = status;
        this.name = 'ApiError';
    }
}

window.api = {
    request: apiRequest,
    tokens: Tokens,
    ApiError,
};
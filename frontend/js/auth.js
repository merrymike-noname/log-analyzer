/**
 * Authentication helpers: login, register, logout, session check.
 */

const Auth = {
    async login(email, password) {
        const data = await api.request('/auth/login', {
            method: 'POST',
            body: JSON.stringify({ email, password })
        }, true);
        api.tokens.set(data);
        return data;
    },

    async register(email, password) {
        const data = await api.request('/auth/register', {
            method: 'POST',
            body: JSON.stringify({ email, password })
        }, true);
        api.tokens.set(data);
        return data;
    },

    async logout() {
        const refreshToken = api.tokens.refresh;
        if (refreshToken) {
            try {
                await api.request('/auth/logout', {
                    method: 'POST',
                    body: JSON.stringify({ refreshToken })
                }, true);
            } catch { /* best effort */ }
        }
        api.tokens.clear();
    },

    isAuthenticated() {
        return !!api.tokens.access;
    }
};

window.Auth = Auth;
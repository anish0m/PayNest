// PayNest API client. Same-origin fetch wrapper: adds the Bearer token,
// sends you back to login on 401, and turns problem+json into an Error.
(function () {
    const TOKEN_KEY = 'paynest.token';
    const LOGIN_PAGE = '/index.html';

    function getToken() {
        try { return sessionStorage.getItem(TOKEN_KEY); } catch (e) { return null; }
    }

    function setToken(token) {
        try { sessionStorage.setItem(TOKEN_KEY, token); } catch (e) { /* private mode */ }
    }

    function clearToken() {
        try { sessionStorage.removeItem(TOKEN_KEY); } catch (e) { /* private mode */ }
    }

    // Builds one readable message from a ProblemDetail body.
    // Validation errors come as {errors: {field: message}}.
    function messageFrom(body, status) {
        if (body && typeof body === 'object') {
            if (body.errors && typeof body.errors === 'object') {
                const parts = Object.values(body.errors);
                if (parts.length) return parts.join('. ');
            }
            if (body.detail) return body.detail;
            if (body.title) return body.title;
        }
        if (typeof body === 'string' && body.trim()) return body;
        if (status === 0) return 'Cannot reach the server.';
        return 'Request failed (' + status + ').';
    }

    // request('POST', '/wallets/me/deposit', {amount: '10.00'})
    // Resolves with the parsed body (or null), rejects with an Error that
    // carries .status, .body and .errors (field map, may be empty).
    // Pass {auth: false} for login/signup so a 401 there is just an error.
    async function request(method, path, data, options) {
        const opts = options || {};
        const useAuth = opts.auth !== false;
        const headers = { 'Accept': 'application/json, application/problem+json' };
        if (data !== undefined) headers['Content-Type'] = 'application/json';

        const token = getToken();
        if (useAuth && token) headers['Authorization'] = 'Bearer ' + token;

        let res;
        try {
            res = await fetch(path, {
                method: method,
                headers: headers,
                body: data !== undefined ? JSON.stringify(data) : undefined
            });
        } catch (e) {
            const err = new Error(messageFrom(null, 0));
            err.status = 0; err.body = null; err.errors = {};
            throw err;
        }

        const text = await res.text();
        let body = null;
        if (text) {
            try { body = JSON.parse(text); } catch (e) { body = text; }
        }

        if (res.status === 401 && useAuth) {
            clearToken();
            if (!location.pathname.endsWith(LOGIN_PAGE)) {
                location.href = LOGIN_PAGE + '?expired=1';
            }
        }

        if (!res.ok) {
            const err = new Error(messageFrom(body, res.status));
            err.status = res.status;
            err.body = body;
            err.errors = (body && body.errors) || {};
            throw err;
        }
        return body;
    }

    window.api = {
        request: request,
        get: function (path, opts) { return request('GET', path, undefined, opts); },
        post: function (path, data, opts) { return request('POST', path, data, opts); },
        put: function (path, data, opts) { return request('PUT', path, data, opts); },
        del: function (path, opts) { return request('DELETE', path, undefined, opts); },
        getToken: getToken,
        setToken: setToken,
        clearToken: clearToken,
        isLoggedIn: function () { return !!getToken(); }
    };
})();

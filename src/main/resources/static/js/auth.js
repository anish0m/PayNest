// PayNest auth page: login, signup (+ auto-login), and logout for other pages.
(function () {
    const HOME = '/app.html';

    async function login(email, password) {
        const res = await api.post('/auth/login', { email: email, password: password }, { auth: false });
        api.setToken(res.token);
        return res;
    }

    // Best effort: the token is dropped locally even if the server call fails.
    async function logout() {
        try { await api.post('/auth/logout'); } catch (e) { /* ignore */ }
        api.clearToken();
        location.href = '/index.html';
    }

    window.auth = { login: login, logout: logout };

    const loginPanel = document.getElementById('login-panel');
    if (!loginPanel) return; // not the auth page

    // Already logged in: skip the form.
    if (api.isLoggedIn()) { location.replace(HOME); return; }

    const signupPanel = document.getElementById('signup-panel');
    const alertBox = document.getElementById('auth-alert');

    function showAlert(message, type) {
        alertBox.className = 'alert alert-' + (type || 'danger');
        alertBox.textContent = message;
    }

    function hideAlert() { alertBox.className = 'alert d-none'; alertBox.textContent = ''; }

    function show(which) {
        const signup = which === 'signup';
        signupPanel.classList.toggle('d-none', !signup);
        loginPanel.classList.toggle('d-none', signup);
        hideAlert();
        const first = (signup ? signupPanel : loginPanel).querySelector('input:not([disabled])');
        if (first) first.focus();
    }

    document.querySelectorAll('[data-show]').forEach(function (a) {
        a.addEventListener('click', function (e) {
            e.preventDefault();
            show(a.getAttribute('data-show'));
            history.replaceState(null, '', '#' + a.getAttribute('data-show'));
        });
    });

    function clearErrors(form) {
        form.querySelectorAll('.is-invalid').forEach(function (el) { el.classList.remove('is-invalid'); });
    }

    function setFieldError(form, name, message) {
        const input = form.elements[name];
        if (!input) return false;
        input.classList.add('is-invalid');
        const fb = input.parentElement.querySelector('.invalid-feedback');
        if (fb) fb.textContent = message;
        return true;
    }

    // Client checks mirror the server's bean validation; the server still decides.
    function validate(form, rules) {
        let ok = true;
        Object.keys(rules).forEach(function (name) {
            const msg = rules[name](form.elements[name].value);
            if (msg) { setFieldError(form, name, msg); ok = false; }
        });
        return ok;
    }

    const EMAIL_RE = /^[^\s@]+@[^\s@]+$/;
    const required = function (label) { return function (v) { return v.trim() ? null : label + ' is required'; }; };

    function showServerError(form, err) {
        let anyField = false;
        Object.keys(err.errors || {}).forEach(function (field) {
            if (setFieldError(form, field, err.errors[field])) anyField = true;
        });
        if (!anyField) showAlert(err.message);
    }

    function busy(form, on, label) {
        const btn = form.querySelector('button[type=submit]');
        if (on) { btn.dataset.label = btn.textContent; btn.textContent = label; }
        else if (btn.dataset.label) { btn.textContent = btn.dataset.label; }
        btn.disabled = on;
    }

    // ---- Login -----------------------------------------------------------
    const loginForm = document.getElementById('login-form');
    loginForm.addEventListener('submit', async function (e) {
        e.preventDefault();
        hideAlert(); clearErrors(loginForm);
        const ok = validate(loginForm, {
            email: function (v) { return !v.trim() ? 'Email is required' : null; },
            password: required('Password')
        });
        if (!ok) return;

        busy(loginForm, true, 'Logging in…');
        try {
            await login(loginForm.elements.email.value.trim(), loginForm.elements.password.value);
            location.href = HOME;
        } catch (err) {
            busy(loginForm, false);
            if (err.status === 401) showAlert('Wrong email or password.');
            else showServerError(loginForm, err);
        }
    });

    // ---- Signup + auto-login ----------------------------------------------
    const signupForm = document.getElementById('signup-form');
    signupForm.addEventListener('submit', async function (e) {
        e.preventDefault();
        hideAlert(); clearErrors(signupForm);
        const ok = validate(signupForm, {
            firstName: required('First name'),
            lastName: required('Last name'),
            email: function (v) {
                if (!v.trim()) return 'Email is required';
                return EMAIL_RE.test(v.trim()) ? null : 'Email must be valid';
            },
            password: function (v) {
                if (!v) return 'Password is required';
                return v.length >= 8 && v.length <= 72 ? null : 'Password must be between 8 and 72 characters';
            }
        });
        if (!ok) return;

        const f = signupForm.elements;
        const data = {
            firstName: f.firstName.value.trim(),
            lastName: f.lastName.value.trim(),
            email: f.email.value.trim(),
            password: f.password.value
        };

        busy(signupForm, true, 'Creating account…');
        try {
            await api.post('/users', data, { auth: false });
        } catch (err) {
            busy(signupForm, false);
            showServerError(signupForm, err);
            return;
        }

        // Account exists now; a failed auto-login just sends them to the login form.
        try {
            await login(data.email, data.password);
            location.href = HOME;
        } catch (err) {
            busy(signupForm, false);
            show('login');
            loginForm.elements.email.value = data.email;
            showAlert('Account created. Please log in.', 'success');
        }
    });

    // ---- Initial state -------------------------------------------------------
    if (location.hash === '#signup') show('signup');
    if (new URLSearchParams(location.search).has('expired')) {
        showAlert('Your session has expired. Please log in again.', 'warning');
    }
})();

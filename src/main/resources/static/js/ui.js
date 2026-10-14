// PayNest UI helpers. The saved theme is applied earlier by the inline
// <script> in <head>; this file only wires up the toggle button(s).
(function () {
    const KEY = 'paynest.theme';

    function setTheme(theme) {
        document.documentElement.setAttribute('data-bs-theme', theme);
        try { localStorage.setItem(KEY, theme); } catch (e) { /* private mode */ }
    }

    function toggleTheme() {
        const current = document.documentElement.getAttribute('data-bs-theme');
        setTheme(current === 'dark' ? 'light' : 'dark');
    }

    document.querySelectorAll('[data-theme-toggle]').forEach(function (btn) {
        btn.addEventListener('click', toggleTheme);
    });

    // ---- Money -------------------------------------------------------
    // Amounts stay strings end to end; never parseFloat them.
    const AMOUNT_RE = /^\d+(\.\d{1,2})?$/;

    function isValidAmount(value) {
        return typeof value === 'string' && AMOUNT_RE.test(value.trim());
    }

    // '1234567.5' -> '1,234,567.50'; '-20' -> '-20.00'. Pure string work.
    function formatMoney(value) {
        if (value === null || value === undefined) return '';
        let s = String(value).trim();
        let sign = '';
        if (s.startsWith('-') || s.startsWith('+')) { sign = s[0] === '-' ? '-' : ''; s = s.slice(1); }
        if (!/^\d+(\.\d+)?$/.test(s)) return String(value);
        let [whole, frac = ''] = s.split('.');
        whole = whole.replace(/^0+(?=\d)/, '');
        frac = (frac + '00').slice(0, 2);
        whole = whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
        return sign + whole + '.' + frac;
    }

    // ---- Toasts ------------------------------------------------------
    function toastContainer() {
        let box = document.getElementById('toast-box');
        if (!box) {
            box = document.createElement('div');
            box.id = 'toast-box';
            box.className = 'toast-container position-fixed top-0 end-0 p-3';
            box.style.zIndex = '1100';
            document.body.appendChild(box);
        }
        return box;
    }

    // type: 'success' | 'danger' | 'warning' | 'info'
    function toast(message, type) {
        const el = document.createElement('div');
        el.className = 'toast align-items-center border-0 text-bg-' + (type || 'info');
        el.setAttribute('role', type === 'danger' ? 'alert' : 'status');
        el.setAttribute('aria-live', type === 'danger' ? 'assertive' : 'polite');
        el.setAttribute('aria-atomic', 'true');

        const row = document.createElement('div');
        row.className = 'd-flex';
        const body = document.createElement('div');
        body.className = 'toast-body';
        body.textContent = message; // textContent: never inject server text as HTML
        const close = document.createElement('button');
        close.type = 'button';
        close.className = 'btn-close btn-close-white me-2 m-auto';
        close.setAttribute('data-bs-dismiss', 'toast');
        close.setAttribute('aria-label', 'Close');
        row.append(body, close);
        el.appendChild(row);

        toastContainer().appendChild(el);
        el.addEventListener('hidden.bs.toast', function () { el.remove(); });
        bootstrap.Toast.getOrCreateInstance(el, { delay: 4000 }).show();
    }

    window.ui = {
        setTheme: setTheme,
        toggleTheme: toggleTheme,
        formatMoney: formatMoney,
        isValidAmount: isValidAmount,
        toast: toast,
        success: function (m) { toast(m, 'success'); },
        error: function (m) { toast(m, 'danger'); }
    };
})();

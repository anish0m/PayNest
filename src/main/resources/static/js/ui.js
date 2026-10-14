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

    window.ui = { setTheme: setTheme, toggleTheme: toggleTheme };
})();

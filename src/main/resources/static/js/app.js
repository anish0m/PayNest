// PayNest home page shell: guards the page, loads the current user into
// the profile card and linked card, and wires up logout.
(function () {
    const avatar = document.getElementById('user-avatar');
    if (!avatar) return; // not the app page

    if (!api.isLoggedIn()) { location.replace('/index.html'); return; }

    function initials(user) {
        const f = (user.firstName || '').trim().charAt(0);
        const l = (user.lastName || '').trim().charAt(0);
        return ((f + l) || (user.email || '?').charAt(0)).toUpperCase();
    }

    function renderUser(user) {
        window.currentUser = user;
        avatar.textContent = '';
        if (user.image) {
            const img = document.createElement('img');
            img.src = user.image;
            img.alt = '';
            img.addEventListener('error', function () { avatar.textContent = initials(user); });
            avatar.appendChild(img);
        } else {
            avatar.textContent = initials(user);
        }
        const full = user.firstName + ' ' + user.lastName;
        document.getElementById('user-name').textContent = full;
        document.getElementById('user-handle').textContent = user.username.startsWith('@') ? user.username : '@' + user.username;
        document.getElementById('user-first').textContent = user.firstName;
        document.getElementById('user-last').textContent = user.lastName;
        document.getElementById('user-email').textContent = user.email;
        document.getElementById('user-role').textContent = user.role === 'ADMIN' ? 'Admin' : 'User';
        document.getElementById('card-holder').textContent = full;
        document.getElementById('admin-link').classList.toggle('d-none', user.role !== 'ADMIN');
    }

    window.renderUser = renderUser; // profile.js re-renders after a save

    document.getElementById('logout-btn').addEventListener('click', auth.logout);

    api.get('/users/me')
        .then(renderUser)
        .catch(function (err) { if (err.status !== 401) ui.error(err.message); });
})();

// PayNest admin page: guards on role ADMIN, lists every user (GET /users),
// filters client-side, and edits or deletes accounts via /users/{email}.
(function () {
    if (!api.isLoggedIn()) { location.replace('/index.html'); return; }

    const main = document.getElementById('admin-main');
    const card = document.getElementById('users-card');
    const rows = document.getElementById('user-rows');
    const empty = document.getElementById('users-empty');
    const errorBox = document.getElementById('users-error');
    const count = document.getElementById('user-count');
    const search = document.getElementById('user-search');
    let users = [];
    let me = null;

    document.getElementById('logout-btn').addEventListener('click', auth.logout);

    function userPath(email) { return '/users/' + encodeURIComponent(email); }
    function handle(u) { return u.username.startsWith('@') ? u.username : '@' + u.username; }

    function cell(text, cls) {
        const td = document.createElement('td');
        if (cls) td.className = cls;
        td.textContent = text;
        return td;
    }

    function button(label, cls, onClick) {
        const b = document.createElement('button');
        b.type = 'button';
        b.className = 'btn btn-sm ' + cls;
        b.textContent = label;
        b.addEventListener('click', onClick);
        return b;
    }

    function render() {
        const q = search.value.trim().toLowerCase();
        const list = users.filter(function (u) {
            return !q || [u.firstName + ' ' + u.lastName, u.email, handle(u)]
                .some(function (s) { return s.toLowerCase().includes(q); });
        });
        count.textContent = users.length;
        rows.textContent = '';
        list.forEach(function (u) {
            const tr = document.createElement('tr');

            const name = cell('');
            const strong = document.createElement('div');
            strong.className = 'fw-semibold';
            strong.textContent = u.firstName + ' ' + u.lastName;
            const sub = document.createElement('div');
            sub.className = 'small text-body-secondary text-break';
            sub.textContent = handle(u);
            const mail = document.createElement('div');
            mail.className = 'small text-body-secondary text-break d-md-none';
            mail.textContent = u.email;
            name.append(strong, sub, mail);

            const role = cell('');
            const badge = document.createElement('span');
            badge.className = 'badge ' + (u.role === 'ADMIN' ? 'text-bg-danger' : 'text-bg-secondary');
            badge.textContent = u.role;
            role.appendChild(badge);

            const actions = cell('', 'text-end text-nowrap');
            actions.append(button('Edit', 'btn-outline-secondary me-1', function () { openEdit(u); }));
            if (u.email !== me.email) { // deleting yourself lives in your profile
                actions.append(button('Delete', 'btn-outline-danger', function () { openDelete(u); }));
            }

            tr.append(name, cell(u.email, 'd-none d-md-table-cell text-break'), role, actions);
            rows.appendChild(tr);
        });
        empty.textContent = users.length ? 'No users match "' + search.value.trim() + '".' : 'No users yet.';
        empty.classList.toggle('d-none', list.length > 0);
    }

    function load() {
        card.setAttribute('aria-busy', 'true');
        errorBox.classList.add('d-none');
        return api.get('/users')
            .then(function (list) {
                users = list.sort(function (a, b) { return a.email.localeCompare(b.email); });
                render();
            })
            .catch(function (err) {
                if (err.status === 401) return;
                rows.textContent = '';
                errorBox.classList.remove('d-none');
            })
            .finally(function () { card.setAttribute('aria-busy', 'false'); });
    }

    search.addEventListener('input', render);
    document.getElementById('users-retry').addEventListener('click', load);

    // ---- Edit --------------------------------------------------------
    const editModal = document.getElementById('edit-modal');
    const editForm = document.getElementById('edit-form');
    const saveBtn = document.getElementById('ed-save');
    const fields = {
        firstName: document.getElementById('ed-first'),
        lastName: document.getElementById('ed-last'),
        image: document.getElementById('ed-image')
    };
    let editing = null;

    function showError(name, message) {
        const input = fields[name];
        if (!input) return false;
        input.classList.add('is-invalid');
        editForm.querySelector('[data-for="' + name + '"]').textContent = message;
        return true;
    }

    function clearErrors() {
        Object.values(fields).forEach(function (i) { i.classList.remove('is-invalid'); });
    }

    function openEdit(u) {
        editing = u;
        clearErrors();
        document.getElementById('ed-email').textContent = u.email + ' · ' + handle(u);
        fields.firstName.value = u.firstName || '';
        fields.lastName.value = u.lastName || '';
        fields.image.value = u.image || '';
        bootstrap.Modal.getOrCreateInstance(editModal).show();
    }

    editModal.addEventListener('shown.bs.modal', function () { fields.firstName.focus(); });

    editForm.addEventListener('submit', function (e) {
        e.preventDefault();
        clearErrors();
        const data = {
            firstName: fields.firstName.value.trim(),
            lastName: fields.lastName.value.trim(),
            image: fields.image.value.trim() || null
        };
        let bad = false;
        if (!data.firstName) bad = showError('firstName', 'First name is required');
        if (!data.lastName) bad = showError('lastName', 'Last name is required') || bad;
        if (bad) return;

        saveBtn.disabled = true;
        api.put(userPath(editing.email), data)
            .then(function (saved) {
                users = users.map(function (u) { return u.email === saved.email ? saved : u; });
                render();
                bootstrap.Modal.getInstance(editModal).hide();
                ui.success('Saved ' + saved.email + '.');
            })
            .catch(function (err) {
                let shown = false;
                Object.keys(err.errors).forEach(function (k) { shown = showError(k, err.errors[k]) || shown; });
                if (!shown && err.status !== 401) ui.error(err.message);
            })
            .finally(function () { saveBtn.disabled = false; });
    });

    // ---- Delete ------------------------------------------------------
    const rmModal = document.getElementById('rm-modal');
    const rmInput = document.getElementById('rm-confirm');
    const rmBtn = document.getElementById('rm-submit');
    const rmError = document.getElementById('rm-error');
    let removing = null;

    function openDelete(u) {
        removing = u;
        document.getElementById('rm-email').textContent = u.email;
        rmInput.value = '';
        rmBtn.disabled = true;
        rmError.classList.add('d-none');
        bootstrap.Modal.getOrCreateInstance(rmModal).show();
    }

    rmModal.addEventListener('shown.bs.modal', function () { rmInput.focus(); });
    rmInput.addEventListener('input', function () {
        rmBtn.disabled = rmInput.value.trim() !== removing.email;
    });

    document.getElementById('rm-form').addEventListener('submit', function (e) {
        e.preventDefault();
        if (rmBtn.disabled) return;
        rmBtn.disabled = true;
        api.del(userPath(removing.email))
            .then(function () {
                users = users.filter(function (u) { return u.email !== removing.email; });
                render();
                bootstrap.Modal.getInstance(rmModal).hide();
                ui.success('Deleted ' + removing.email + '.');
            })
            .catch(function (err) {
                if (err.status === 401) return;
                rmError.textContent = 'Could not delete this user: ' + err.message;
                rmError.classList.remove('d-none');
                rmBtn.disabled = false;
            });
    });

    // ---- Guard -------------------------------------------------------
    api.get('/users/me')
        .then(function (user) {
            if (user.role !== 'ADMIN') { location.replace('/app.html'); return; }
            me = user;
            main.hidden = false;
            load();
        })
        .catch(function (err) { if (err.status !== 401) ui.error(err.message); });
})();

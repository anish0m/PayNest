// Profile modal (PUT /users/{email}) and delete-account modal
// (DELETE /users/{email}, typed-email confirm).
(function () {
    const profileModal = document.getElementById('profile-modal');
    if (!profileModal) return;

    const form = document.getElementById('profile-form');
    const saveBtn = document.getElementById('profile-save');
    const fields = {
        firstName: document.getElementById('pf-first'),
        lastName: document.getElementById('pf-last'),
        image: document.getElementById('pf-image')
    };

    function userPath() {
        return '/users/' + encodeURIComponent(window.currentUser.email);
    }

    function clearErrors() {
        Object.values(fields).forEach(function (el) { el.classList.remove('is-invalid'); });
    }

    function showError(name, message) {
        const el = fields[name];
        if (!el) return false;
        el.classList.add('is-invalid');
        form.querySelector('[data-for="' + name + '"]').textContent = message;
        return true;
    }

    profileModal.addEventListener('show.bs.modal', function (e) {
        const u = window.currentUser;
        if (!u) { e.preventDefault(); ui.error('Still loading your profile.'); return; }
        clearErrors();
        document.getElementById('pf-email').value = u.email;
        document.getElementById('pf-username').value = u.username;
        fields.firstName.value = u.firstName || '';
        fields.lastName.value = u.lastName || '';
        fields.image.value = u.image || '';
    });

    form.addEventListener('submit', function (e) {
        e.preventDefault();
        clearErrors();
        const data = {
            firstName: fields.firstName.value.trim(),
            lastName: fields.lastName.value.trim(),
            image: fields.image.value.trim() || null // empty clears the picture
        };
        let bad = false;
        if (!data.firstName) bad = showError('firstName', 'First name is required');
        if (!data.lastName) bad = showError('lastName', 'Last name is required') || bad;
        if (bad) return;

        saveBtn.disabled = true;
        api.put(userPath(), data)
            .then(function (user) {
                window.renderUser(user);
                bootstrap.Modal.getInstance(profileModal).hide();
                ui.success('Profile saved.');
            })
            .catch(function (err) {
                let shown = false;
                Object.keys(err.errors).forEach(function (k) { shown = showError(k, err.errors[k]) || shown; });
                if (!shown && err.status !== 401) ui.error(err.message);
            })
            .finally(function () { saveBtn.disabled = false; });
    });

    // ---- Delete ------------------------------------------------------
    const deleteModal = document.getElementById('delete-modal');
    const confirmInput = document.getElementById('del-confirm');
    const deleteBtn = document.getElementById('del-submit');
    const deleteError = document.getElementById('del-error');

    function matches() {
        return !!window.currentUser && confirmInput.value.trim() === window.currentUser.email;
    }

    deleteModal.addEventListener('show.bs.modal', function () {
        document.getElementById('del-email').textContent = window.currentUser ? window.currentUser.email : '';
        confirmInput.value = '';
        deleteBtn.disabled = true;
        deleteError.classList.add('d-none');
    });
    deleteModal.addEventListener('shown.bs.modal', function () { confirmInput.focus(); });

    confirmInput.addEventListener('input', function () { deleteBtn.disabled = !matches(); });

    document.getElementById('delete-form').addEventListener('submit', function (e) {
        e.preventDefault();
        if (!matches()) return;
        deleteBtn.disabled = true;
        deleteError.classList.add('d-none');
        api.del(userPath())
            .then(function () {
                api.clearToken();
                location.replace('/index.html?deleted=1');
            })
            .catch(function (err) {
                if (err.status === 401) return;
                // Backend §9 Q4: delete can fail while the user still owns
                // a wallet/categories. Show a plain message, keep the account.
                deleteError.textContent = 'Could not delete your account. Please try again later.';
                deleteError.classList.remove('d-none');
                deleteBtn.disabled = !matches();
            });
    });
})();

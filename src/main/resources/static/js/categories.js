// Categories card (GET /categories + /categories/summary) and the Manage
// modal (POST /categories, PUT and DELETE /categories/{name}).
// Names are stored trimmed and lowercase (Category.setName), so the UI
// normalises the same way before comparing or sending.
(function () {
    const card = document.getElementById('cat-card');
    if (!card) return;

    const summaryEl = document.getElementById('cat-summary');
    const emptyEl = document.getElementById('cat-empty');
    const errorEl = document.getElementById('cat-error');

    const listEl = document.getElementById('cat-list');
    const listEmptyEl = document.getElementById('cat-list-empty');
    const addForm = document.getElementById('cat-add-form');
    const addInput = document.getElementById('cat-add-name');
    const addError = document.getElementById('cat-add-error');
    const addBtn = document.getElementById('cat-add-submit');
    const modalEl = document.getElementById('cat-modal');

    let categories = []; // [{id, name, total}]

    function norm(name) { return name.trim().toLowerCase(); }
    function path(name) { return '/categories/' + encodeURIComponent(name); }
    function plural(n) { return n + (n === 1 ? ' transfer' : ' transfers'); }

    // Mirrors CreateCategoryRequest: required, at most 50 characters.
    function validate(raw, except) {
        const v = norm(raw);
        if (!v) return { error: 'Enter a name.' };
        if (v.length > 50) return { error: 'Use at most 50 characters.' };
        if (v !== except && categories.some(function (c) { return c.name === v; })) {
            return { error: 'That category already exists.' };
        }
        return { value: v };
    }

    function el(tag, cls, text) {
        const e = document.createElement(tag);
        if (cls) e.className = cls;
        if (text !== undefined) e.textContent = text; // never innerHTML: names are user input
        return e;
    }

    // ---- Summary card ------------------------------------------------
    // Used categories, most-used first. Unused ones are only counted, so a
    // fresh account doesn't get a long column of zeros.
    function renderSummary() {
        summaryEl.replaceChildren();
        const used = categories.filter(function (c) { return c.total > 0; })
            .sort(function (a, b) { return b.total - a.total || a.name.localeCompare(b.name); });
        used.forEach(function (c) {
            const li = el('li', 'list-group-item px-0 d-flex justify-content-between align-items-center');
            li.append(el('span', 'text-capitalize text-truncate me-2', c.name),
                      el('span', 'badge rounded-pill text-bg-light border money', plural(c.total)));
            summaryEl.append(li);
        });
        summaryEl.classList.toggle('d-none', !used.length);

        const unused = categories.length - used.length;
        emptyEl.textContent = !categories.length ? 'No categories yet. Use Manage to add one.'
            : !used.length ? 'No transfers are tagged yet. ' + categories.length + ' categories are ready to use.'
            : unused ? unused + ' more ' + (unused === 1 ? 'category has' : 'categories have') + ' no transfers yet.'
            : '';
        emptyEl.classList.toggle('d-none', !emptyEl.textContent);
        emptyEl.classList.toggle('mt-2', !!used.length);
    }

    function loadCategories() {
        card.setAttribute('aria-busy', 'true');
        errorEl.classList.add('d-none');
        return Promise.all([api.get('/categories'), api.get('/categories/summary')])
            .then(function (r) {
                const totals = {};
                (r[1] || []).forEach(function (row) { totals[row.name] = Number(row.total) || 0; });
                categories = (r[0] || []).map(function (c) {
                    return { id: c.id, name: c.name, total: totals[c.name] || 0 };
                });
                card.setAttribute('aria-busy', 'false');
                renderSummary();
                renderList();
            })
            .catch(function (err) {
                if (err.status === 401) return;
                card.setAttribute('aria-busy', 'false');
                summaryEl.classList.add('d-none');
                emptyEl.classList.add('d-none');
                errorEl.classList.remove('d-none');
            });
    }

    document.getElementById('cat-retry').addEventListener('click', loadCategories);
    window.loadCategories = loadCategories; // send money (task 11) may tag transfers

    // ---- Manage modal ------------------------------------------------
    function renderList() {
        const a = document.activeElement; // disabled buttons drop focus to <body>
        const hadFocus = modalEl.classList.contains('show') && (a === document.body || listEl.contains(a));
        listEl.replaceChildren();
        categories.slice().sort(function (a, b) { return a.name.localeCompare(b.name); })
            .forEach(function (c) { listEl.append(viewRow(c)); });
        listEmptyEl.classList.toggle('d-none', !!categories.length);
        if (hadFocus) addInput.focus(); // the focused row was just replaced
    }

    function viewRow(c) {
        const li = el('li', 'list-group-item');
        // Wraps: on a phone the buttons drop under the name instead of squeezing it.
        const row = el('div', 'd-flex flex-wrap align-items-center gap-2');
        const name = el('span', 'flex-grow-1 text-truncate text-capitalize cat-name', c.name);
        const count = el('span', 'small text-body-secondary text-nowrap', plural(c.total));
        const rename = el('button', 'btn btn-sm btn-outline-secondary', 'Rename');
        const del = el('button', 'btn btn-sm btn-outline-danger', 'Delete');
        rename.type = del.type = 'button';
        rename.setAttribute('aria-label', 'Rename ' + c.name);
        del.setAttribute('aria-label', 'Delete ' + c.name);
        const actions = el('div', 'd-flex gap-2 ms-auto');
        actions.append(rename, del);
        row.append(name, count, actions);
        li.append(row);

        rename.addEventListener('click', function () {
            const form = editRow(c);
            li.replaceWith(form);
            form.querySelector('input').focus();
        });
        del.addEventListener('click', function () { confirmDelete(li, c); });
        return li;
    }

    function editRow(c) {
        const li = el('li', 'list-group-item');
        const form = el('form', 'input-group input-group-sm has-validation');
        form.noValidate = true;
        const input = el('input', 'form-control');
        input.type = 'text'; input.maxLength = 50; input.value = c.name;
        input.setAttribute('aria-label', 'New name for ' + c.name);
        const save = el('button', 'btn btn-primary', 'Save');
        const cancel = el('button', 'btn btn-outline-secondary', 'Cancel');
        save.type = 'submit'; cancel.type = 'button';
        const fb = el('div', 'invalid-feedback');
        form.append(input, save, cancel, fb);
        li.append(form);

        function setError(m) { input.classList.toggle('is-invalid', !!m); fb.textContent = m || ''; }
        cancel.addEventListener('click', renderList);
        input.addEventListener('keydown', function (e) { if (e.key === 'Escape') { e.stopPropagation(); renderList(); } });
        input.addEventListener('input', function () { setError(''); });

        form.addEventListener('submit', function (e) {
            e.preventDefault();
            const r = validate(input.value, c.name);
            if (r.error) { setError(r.error); return; }
            if (r.value === c.name) { renderList(); return; } // nothing changed
            save.disabled = true;
            api.put(path(c.name), { name: r.value })
                .then(function () {
                    ui.success('Renamed "' + c.name + '" to "' + r.value + '".');
                    return loadCategories();
                })
                .catch(function (err) {
                    if (err.status === 401) return;
                    save.disabled = false;
                    if (err.status === 409) { setError('That category already exists.'); return; }
                    if (err.errors && err.errors.name) { setError(err.errors.name); return; }
                    if (err.status === 404) { loadCategories(); }
                    ui.error(err.message);
                });
        });
        return li;
    }

    // Inline confirm instead of a second modal stacked on this one.
    function confirmDelete(li, c) {
        const box = el('li', 'list-group-item list-group-item-danger');
        box.append(el('div', 'small mb-2', 'Delete "' + c.name + '"?'));
        const yes = el('button', 'btn btn-sm btn-danger me-2', 'Delete');
        const no = el('button', 'btn btn-sm btn-outline-secondary', 'Cancel');
        yes.type = no.type = 'button';
        box.append(yes, no);
        li.replaceWith(box);
        no.focus();

        no.addEventListener('click', renderList);
        yes.addEventListener('click', function () {
            yes.disabled = no.disabled = true;
            api.del(path(c.name))
                .then(function () {
                    ui.success('Deleted "' + c.name + '".');
                    return loadCategories();
                })
                .catch(function (err) {
                    if (err.status === 401) return;
                    if (err.status === 409) {
                        const n = err.body && err.body.transferCount;
                        ui.error('"' + c.name + '" is used by ' + (n ? plural(n) : 'transfers') +
                                 ' and can\'t be deleted.');
                    } else {
                        ui.error(err.message);
                    }
                    loadCategories();
                });
        });
    }

    function setAddError(m) { addInput.classList.toggle('is-invalid', !!m); addError.textContent = m || ''; }
    addInput.addEventListener('input', function () { setAddError(''); });

    modalEl.addEventListener('show.bs.modal', function () {
        addForm.reset();
        setAddError('');
        renderList();
    });

    addForm.addEventListener('submit', function (e) {
        e.preventDefault();
        const r = validate(addInput.value);
        if (r.error) { setAddError(r.error); return; }
        addBtn.disabled = true;
        api.post('/categories', { name: r.value })
            .then(function (c) {
                addForm.reset();
                ui.success('Added "' + c.name + '".');
                return loadCategories();
            })
            .catch(function (err) {
                if (err.status === 401) return;
                if (err.status === 409) { setAddError('That category already exists.'); loadCategories(); return; }
                if (err.errors && err.errors.name) { setAddError(err.errors.name); return; }
                ui.error(err.message);
                loadCategories(); // the row may exist even when the response failed
            })
            .finally(function () { addBtn.disabled = false; addInput.focus(); });
    });

    loadCategories();
})();

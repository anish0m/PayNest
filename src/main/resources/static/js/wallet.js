// Wallet card (GET /wallets/me) and Add money modal (POST /wallets/me/deposit).
// Balance stays a string end to end; ui.formatMoney only re-punctuates it.
(function () {
    const card = document.getElementById('wallet-card');
    if (!card) return;

    const balanceEl = document.getElementById('wallet-balance');
    const statusEl = document.getElementById('wallet-status');
    const lockedEl = document.getElementById('wallet-locked');
    const errorEl = document.getElementById('wallet-error');
    const openBtn = document.getElementById('deposit-open');

    const STATUS = {
        ACTIVE: { label: 'Active', cls: 'text-bg-success' },
        FROZEN: { label: 'Frozen', cls: 'text-bg-warning' },
        CLOSED: { label: 'Closed', cls: 'text-bg-secondary' }
    };
    const LOCKED_MSG = {
        FROZEN: 'Your wallet is frozen. You can see your balance, but you can\'t add or send money. Contact support.',
        CLOSED: 'Your wallet is closed. Money can\'t be added or sent.'
    };

    function renderWallet(w) {
        window.currentWallet = w;
        card.setAttribute('aria-busy', 'false');
        errorEl.classList.add('d-none');
        balanceEl.textContent = '$' + ui.formatMoney(w.balance);

        const st = STATUS[w.status] || { label: w.status, cls: 'text-bg-secondary' };
        statusEl.className = 'badge rounded-pill ' + st.cls;
        statusEl.textContent = st.label;

        const active = w.status === 'ACTIVE';
        openBtn.disabled = !active;
        lockedEl.textContent = active ? '' : (LOCKED_MSG[w.status] || 'Money movement is disabled for this wallet.');
        lockedEl.classList.toggle('d-none', active);
    }

    function loadWallet() {
        card.setAttribute('aria-busy', 'true');
        errorEl.classList.add('d-none');
        return api.get('/wallets/me')
            .then(renderWallet)
            .catch(function (err) {
                if (err.status === 401) return;
                card.setAttribute('aria-busy', 'false');
                balanceEl.textContent = '—';
                statusEl.classList.add('d-none');
                openBtn.disabled = true;
                errorEl.classList.remove('d-none');
            });
    }

    document.getElementById('wallet-retry').addEventListener('click', loadWallet);
    window.loadWallet = loadWallet; // send money (task 11) refreshes after a transfer

    // ---- Add money ---------------------------------------------------
    const modal = document.getElementById('deposit-modal');
    const form = document.getElementById('deposit-form');
    const input = document.getElementById('dep-amount');
    const inputError = document.getElementById('dep-amount-error');
    const submitBtn = document.getElementById('dep-submit');

    // Mirrors DepositRequest: >= 0.01, at most 15 integer digits, 2 decimals.
    function validate(raw) {
        const v = raw.trim().replace(/,/g, '');
        if (!v) return { error: 'Enter an amount.' };
        if (!ui.isValidAmount(v)) return { error: 'Use numbers only, with up to 2 decimals (e.g. 25.50).' };
        const whole = v.split('.')[0].replace(/^0+(?=\d)/, '');
        if (whole.length > 15) return { error: 'That amount is too large.' };
        if (!/[1-9]/.test(v)) return { error: 'Amount must be at least 0.01.' };
        return { value: v };
    }

    function setError(message) {
        input.classList.toggle('is-invalid', !!message);
        inputError.textContent = message || '';
    }

    modal.addEventListener('show.bs.modal', function (e) {
        if (!window.currentWallet || window.currentWallet.status !== 'ACTIVE') { e.preventDefault(); return; }
        form.reset();
        setError('');
        submitBtn.disabled = false;
    });
    modal.addEventListener('shown.bs.modal', function () { input.focus(); });
    input.addEventListener('input', function () { if (input.classList.contains('is-invalid')) setError(''); });

    form.addEventListener('submit', function (e) {
        e.preventDefault();
        const r = validate(input.value);
        if (r.error) { setError(r.error); return; }

        submitBtn.disabled = true; // no double deposits from a double click
        api.post('/wallets/me/deposit', { amount: r.value })
            .then(function (w) {
                renderWallet(w);
                bootstrap.Modal.getInstance(modal).hide();
                ui.success('Added $' + ui.formatMoney(r.value) + ' to your wallet.');
            })
            .catch(function (err) {
                if (err.status === 401) return;
                if (err.errors && err.errors.amount) { setError(err.errors.amount); return; }
                if (err.status === 409) { // frozen/closed since the page loaded
                    bootstrap.Modal.getInstance(modal).hide();
                    loadWallet();
                }
                ui.error(err.message);
            })
            .finally(function () { submitBtn.disabled = false; });
    });

    loadWallet();
})();

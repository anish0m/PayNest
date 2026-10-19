// Send money modal (POST /transfers {receiverWalletId, amount}).
// Step 1 collects wallet ID + amount, step 2 confirms, then sends.
(function () {
    const modal = document.getElementById('send-modal');
    if (!modal) return;

    const form = document.getElementById('send-form');
    const walletInput = document.getElementById('send-wallet');
    const amountInput = document.getElementById('send-amount');
    const stepForm = document.getElementById('send-step-form');
    const stepConfirm = document.getElementById('send-step-confirm');
    const errorBox = document.getElementById('send-error');
    const backBtn = document.getElementById('send-back');
    const submitBtn = document.getElementById('send-submit');

    let pending = null; // {receiverWalletId, amount} while confirming

    function setFieldError(input, errEl, message) {
        input.classList.toggle('is-invalid', !!message);
        document.getElementById(errEl).textContent = message || '';
    }

    function showError(message) {
        errorBox.textContent = message || '';
        errorBox.classList.toggle('d-none', !message);
    }

    function showStep(confirm) {
        stepForm.classList.toggle('d-none', confirm);
        stepConfirm.classList.toggle('d-none', !confirm);
        backBtn.textContent = confirm ? 'Back' : 'Cancel';
        submitBtn.textContent = confirm ? 'Send now' : 'Review';
    }

    // Mirrors TransferRequest: >= 0.01, at most 15 integer digits, 2 decimals.
    function validateAmount(raw) {
        const v = raw.trim().replace(/,/g, '');
        if (!v) return { error: 'Enter an amount.' };
        if (!ui.isValidAmount(v)) return { error: 'Use numbers only, with up to 2 decimals (e.g. 25.50).' };
        const whole = v.split('.')[0].replace(/^0+(?=\d)/, '');
        if (whole.length > 15) return { error: 'That amount is too large.' };
        if (!/[1-9]/.test(v)) return { error: 'Amount must be at least 0.01.' };
        return { value: v };
    }

    function validateWallet(raw) {
        const v = raw.trim().replace(/^#/, '');
        if (!v) return { error: 'Enter the recipient\'s wallet ID.' };
        if (!/^\d{1,18}$/.test(v) || !/[1-9]/.test(v)) return { error: 'Wallet IDs are whole numbers, like 42.' };
        const w = window.currentWallet;
        if (w && String(w.id) === v.replace(/^0+/, '')) return { error: 'That\'s your own wallet. Pick someone else\'s.' };
        return { value: Number(v) };
    }

    modal.addEventListener('show.bs.modal', function (e) {
        const w = window.currentWallet;
        if (!w || w.status !== 'ACTIVE') { e.preventDefault(); return; }
        form.reset();
        pending = null;
        setFieldError(walletInput, 'send-wallet-error', '');
        setFieldError(amountInput, 'send-amount-error', '');
        showError('');
        showStep(false);
        submitBtn.disabled = false;
        document.getElementById('send-available').textContent = 'Available: $' + ui.formatMoney(w.balance);
    });
    modal.addEventListener('shown.bs.modal', function () { walletInput.focus(); });

    walletInput.addEventListener('input', function () { setFieldError(walletInput, 'send-wallet-error', ''); });
    amountInput.addEventListener('input', function () { setFieldError(amountInput, 'send-amount-error', ''); });

    backBtn.addEventListener('click', function () {
        if (pending) { pending = null; showError(''); showStep(false); return; }
        bootstrap.Modal.getInstance(modal).hide();
    });

    form.addEventListener('submit', function (e) {
        e.preventDefault();
        if (!pending) return review();
        send();
    });

    function review() {
        showError('');
        const w = validateWallet(walletInput.value);
        const a = validateAmount(amountInput.value);
        setFieldError(walletInput, 'send-wallet-error', w.error);
        setFieldError(amountInput, 'send-amount-error', a.error);
        if (w.error || a.error) return;

        pending = { receiverWalletId: w.value, amount: a.value };
        document.getElementById('send-c-wallet').textContent = '#' + w.value;
        document.getElementById('send-c-amount').textContent = '$' + ui.formatMoney(a.value);
        showStep(true);
        submitBtn.focus();
    }

    function send() {
        submitBtn.disabled = true; // no double sends from a double click
        backBtn.disabled = true;
        showError('');
        api.post('/transfers', pending)
            .then(function (t) {
                bootstrap.Modal.getInstance(modal).hide();
                ui.success('Sent $' + ui.formatMoney(t.amount) + ' to wallet #' + t.receiverWalletId + '.');
                window.loadWallet();
            })
            .catch(function (err) {
                if (err.status === 401) return;
                handleError(err);
            })
            .finally(function () { submitBtn.disabled = false; backBtn.disabled = false; });
    }

    // Errors go back to step 1, next to the field they belong to.
    function handleError(err) {
        pending = null;
        showStep(false);
        switch (err.status) {
            case 400: {
                const fe = err.errors || {};
                if (fe.receiverWalletId) setFieldError(walletInput, 'send-wallet-error', fe.receiverWalletId);
                if (fe.amount) setFieldError(amountInput, 'send-amount-error', fe.amount);
                if (!fe.receiverWalletId && !fe.amount) showError(err.message);
                break;
            }
            case 404:
                setFieldError(walletInput, 'send-wallet-error', 'No wallet with that ID. Check the number and try again.');
                break;
            case 409:
                // Not enough money, or one of the wallets is frozen/closed.
                // The server text has raw numbers, so say it in our words.
                if (/insufficient/i.test(err.message)) {
                    const w = window.currentWallet;
                    showError('Not enough money.' + (w ? ' You have $' + ui.formatMoney(w.balance) + ' available.' : ''));
                } else if (/frozen|closed/i.test(err.message)) {
                    showError('This transfer can\'t go through because one of the wallets is frozen or closed.');
                } else {
                    showError(err.message || 'This transfer can\'t go through right now.');
                }
                window.loadWallet(); // balance or our own status may have changed
                break;
            case 422:
                setFieldError(walletInput, 'send-wallet-error', 'You can\'t send money to your own wallet.');
                break;
            default:
                showError(err.message);
        }
    }
})();

const signupForm = document.querySelector("form");
const submitButton = signupForm?.querySelector('button[type="submit"]');

function showMessage(message, type) {
    let messageBox = document.querySelector("#signupMessage");

    if (!messageBox) {
        messageBox = document.createElement("div");
        messageBox.id = "signupMessage";
        messageBox.className = "mt-3";
        signupForm.after(messageBox);
    }

    const className = type === "success"
        ? "alert alert-success mt-3"
        : "alert alert-danger mt-3";

    messageBox.className = className;
    messageBox.textContent = message;
}

function getSignupData() {
    return {
        firstName: document.querySelector("#firstName").value.trim(),
        lastName: document.querySelector("#lastName").value.trim(),
        email: document.querySelector("#email").value.trim(),
        password: document.querySelector("#password").value
    };
}

async function createAccount(event) {
    event.preventDefault();

    if (!signupForm.checkValidity()) {
        signupForm.reportValidity();
        return;
    }

    submitButton.disabled = true;
    submitButton.textContent = "Creating account...";

    try {
        const response = await fetch("/users", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify(getSignupData())
        });

        if (!response.ok) {
            const errorBody = await response.json().catch(() => null);
            const errorMessage = errorBody?.error || "Could not create account.";
            showMessage(errorMessage, "error");
            return;
        }

        signupForm.reset();
        showMessage("Account created successfully.", "success");
    } catch (error) {
        showMessage("Cannot reach PayNest right now. Make sure the server is running.", "error");
    } finally {
        submitButton.disabled = false;
        submitButton.textContent = "Create account";
    }
}

if (signupForm) {
    signupForm.addEventListener("submit", createAccount);
}

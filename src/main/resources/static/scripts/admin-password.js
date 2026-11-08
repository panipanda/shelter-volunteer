// Login page: a button that shows the typed password and hides it again.
// The button is in the HTML with the "hidden" attribute and is revealed only here, so without JavaScript the page
// is the plain form and no dead button is shown. Its labels come from the page (data-label-*), not from this file.
(function () {
    var button = document.querySelector("[data-password-toggle]");
    if (!button) {
        return;
    }
    var input = document.getElementById(button.getAttribute("aria-controls"));
    if (!input) {
        return;
    }

    button.hidden = false;
    button.addEventListener("click", function (event) {
        // Changing the type resets the caret in some browsers: remember where it was and put it back.
        var start = input.selectionStart;
        var end = input.selectionEnd;
        var show = input.type === "password";

        input.type = show ? "text" : "password";
        button.textContent = button.getAttribute(show ? "data-label-hide" : "data-label-show");

        // After a mouse or touch click the person goes on typing or presses Enter to log in, so the field gets
        // the focus back. After a keyboard press (event.detail is 0) the focus stays on the button: moving it
        // would turn the next Space into a space typed into the password.
        if (event.detail > 0) {
            input.focus();
            try {
                input.setSelectionRange(start, end);
            } catch (e) {
                // Not every input type supports a selection; the caret just goes where the browser puts it.
            }
        }
    });
})();

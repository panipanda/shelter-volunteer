// Flips the theme set by the inline head script and remembers the choice.
(function () {
    var button = document.querySelector(".theme-toggle");
    if (!button) {
        return;
    }

    button.addEventListener("click", function () {
        var root = document.documentElement;
        var next = root.dataset.theme === "dark" ? "light" : "dark";
        root.dataset.theme = next;
        try {
            localStorage.setItem("theme", next);
        } catch (e) {
            // Storage is blocked: the choice just won't survive a reload.
        }
    });
})();

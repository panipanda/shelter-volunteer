// Closes the "Visits" drop-down on an outside click or Escape.
(function () {
    var menu = document.querySelector(".nav-menu");
    if (!menu) {
        return;
    }

    document.addEventListener("click", function (event) {
        if (!menu.contains(event.target)) {
            menu.removeAttribute("open");
        }
    });

    document.addEventListener("keydown", function (event) {
        if (event.key === "Escape") {
            menu.removeAttribute("open");
        }
    });
})();

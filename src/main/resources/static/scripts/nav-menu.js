// Opens and closes the drop-downs of the menu (see the "Drop-downs" rules in main.css).
// Without this script a drop-down opens by hover and by keyboard focus; with it a click on the group's button opens
// it, and Esc, a click elsewhere or moving the focus out of the group closes it.
(function () {
    var groups = Array.prototype.slice.call(document.querySelectorAll(".main-nav .nav-group"));
    if (!groups.length) {
        return;
    }

    function buttonOf(group) {
        return group.querySelector(".nav-toggle");
    }

    function setOpen(group, open) {
        group.classList.toggle("is-open", open);
        buttonOf(group).setAttribute("aria-expanded", open ? "true" : "false");
    }

    function closeAll(except) {
        groups.forEach(function (group) {
            if (group !== except) {
                setOpen(group, false);
            }
        });
    }

    groups.forEach(function (group) {
        var button = buttonOf(group);
        // The small button beside a label that is a link is hidden until there is a script to work it.
        button.hidden = false;

        button.addEventListener("click", function () {
            var open = !group.classList.contains("is-open");
            closeAll(group);
            setOpen(group, open);
        });

        // Tab out of the group: close it. A click on something that cannot take focus has no related target and is
        // left to the click handler below.
        group.addEventListener("focusout", function (event) {
            if (event.relatedTarget && !group.contains(event.relatedTarget)) {
                setOpen(group, false);
            }
        });
    });

    document.addEventListener("click", function (event) {
        if (!event.target.closest(".main-nav .nav-group")) {
            closeAll(null);
        }
    });

    document.addEventListener("keydown", function (event) {
        if (event.key !== "Escape") {
            return;
        }
        groups.forEach(function (group) {
            if (group.classList.contains("is-open")) {
                setOpen(group, false);
                // The focus goes back to the button when it was inside the drop-down, so it is not lost with it.
                if (group.contains(document.activeElement)) {
                    buttonOf(group).focus();
                }
            }
        });
    });
})();

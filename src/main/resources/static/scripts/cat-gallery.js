// Keyboard and focus support for the CSS lightbox (see CatViews.kt). :target alone only reacts
// to clicks, so this script does on the user's behalf what they could do with the mouse:
// Left/Right arrows click the prev/next link, Esc clicks the close link. It also moves focus into
// the lightbox when it opens and back to the thumbnail it was opened from when it closes.

// getElementById, not querySelector: the hash can be any string, and an invalid
// CSS selector (e.g. "#1abc") would throw on every key press.
function openLightbox() {
    const hash = window.location.hash;
    if (!hash) {
        return null;
    }

    const element = document.getElementById(hash.slice(1));
    return element && element.classList.contains("cat-gallery-lightbox") ? element : null;
}

let opener = null;

document.addEventListener("click", (event) => {
    const link = event.target.closest("a[href^='#photo-']");
    if (link && !link.closest(".cat-gallery-lightbox")) {
        opener = link;
    }
});

window.addEventListener("hashchange", () => {
    const lightbox = openLightbox();
    if (lightbox) {
        lightbox.focus();
    } else if (opener) {
        opener.focus();
        opener = null;
    }
});

document.addEventListener("keydown", (event) => {
    const lightbox = openLightbox();
    if (!lightbox) {
        return;
    }

    const selectors = {
        ArrowLeft: ".cat-gallery-lightbox-prev",
        ArrowRight: ".cat-gallery-lightbox-next",
        Escape: ".cat-gallery-lightbox-close"
    };
    const selector = selectors[event.key];
    const link = selector && lightbox.querySelector(selector);
    if (link) {
        event.preventDefault();
        link.click();
    }
});

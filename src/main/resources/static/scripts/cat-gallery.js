// Left/Right arrow keys page through the open CSS lightbox (see CatViews.kt):
// :target alone only reacts to clicks, not keyboard — this script clicks the
// existing prev/next link on the user's behalf.
document.addEventListener("keydown", (event) => {
    if (event.key !== "ArrowLeft" && event.key !== "ArrowRight") {
        return;
    }

    const hash = window.location.hash;
    if (!hash) {
        return;
    }

    // getElementById, not querySelector: the hash can be any string, and an invalid
    // CSS selector (e.g. "#1abc") would throw on every key press.
    const openLightbox = document.getElementById(hash.slice(1));
    if (!openLightbox || !openLightbox.classList.contains("cat-gallery-lightbox")) {
        return;
    }

    const navSelector = event.key === "ArrowLeft"
        ? ".cat-gallery-lightbox-prev"
        : ".cat-gallery-lightbox-next";
    const navLink = openLightbox.querySelector(navSelector);
    if (navLink) {
        event.preventDefault();
        navLink.click();
    }
});

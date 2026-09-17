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

    const openLightbox = document.querySelector(`${hash}.cat-gallery-lightbox`);
    if (!openLightbox) {
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

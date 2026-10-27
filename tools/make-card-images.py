"""Makes the card copy of every animal photo in data/images.

A card shows a photo at 220x275 px (440x550 on a 2x screen), while the originals are about 1280 px wide.
For felix.jpg this writes felix-card.jpg: the whole picture, scaled down so that it still covers 440x550.
The site uses the copy for cards and thumbnails and the original for the big photo and the lightbox
(see cardPhotoUrl in service/DisplayHelpers.kt).

Usage (needs Pillow):  python tools/make-card-images.py [--force]

Run it after adding or replacing a photo and commit the new -card files: the app refuses to start without them.
Photos whose name starts with "home-" belong to the pages, not to cards, and are left alone.
"""
import sys
from pathlib import Path

from PIL import Image, ImageOps

COVER_WIDTH = 440
COVER_HEIGHT = 550
QUALITY = 82
RASTER_SUFFIXES = {".jpg", ".jpeg", ".png"}
PAGE_PHOTO_PREFIX = "home-"


def card_path(source: Path) -> Path:
    return source.with_name(source.stem + "-card.jpg")


def is_source(path: Path) -> bool:
    return (
        path.suffix.lower() in RASTER_SUFFIXES
        and not path.stem.endswith("-card")
        and not path.name.startswith(PAGE_PHOTO_PREFIX)
    )


def make_card(source: Path, target: Path) -> None:
    with Image.open(source) as opened:
        # The browser applies the EXIF orientation to the original, so the copy has to agree with it.
        image = ImageOps.exif_transpose(opened).convert("RGB")
    scale = min(1.0, max(COVER_WIDTH / image.width, COVER_HEIGHT / image.height))
    if scale < 1.0:
        image = image.resize((round(image.width * scale), round(image.height * scale)), Image.LANCZOS)
    image.save(target, "JPEG", quality=QUALITY, optimize=True, progressive=True)


def main() -> None:
    force = "--force" in sys.argv[1:]
    images = Path(__file__).resolve().parent.parent / "data" / "images"

    for source in sorted(path for path in images.iterdir() if is_source(path)):
        target = card_path(source)
        if target.exists() and not force and target.stat().st_mtime >= source.stat().st_mtime:
            continue
        make_card(source, target)
        print(f"{target.name}: {source.stat().st_size // 1024} KB -> {target.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()

from __future__ import annotations

import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageOps


source = Path(sys.argv[1])
pages_per_sheet = int(sys.argv[2]) if len(sys.argv) > 2 else 5
page_paths = sorted(
    source.glob("page-*.png"),
    key=lambda path: int(path.stem.split("-")[-1]),
)

for sheet_index in range(math.ceil(len(page_paths) / pages_per_sheet)):
    selected = page_paths[
        sheet_index * pages_per_sheet : (sheet_index + 1) * pages_per_sheet
    ]
    cards = []
    for page_path in selected:
        page_number = int(page_path.stem.split("-")[-1])
        with Image.open(page_path) as image:
            page = image.convert("RGB")
        page.thumbnail((430, 556))
        card = Image.new("RGB", (452, 600), "#E5E8EC")
        draw = ImageDraw.Draw(card)
        draw.text((14, 10), f"Page {page_number}", fill="#172B3D")
        card.paste(ImageOps.expand(page, border=2, fill="white"), (10, 34))
        cards.append(card)

    sheet = Image.new("RGB", (452 * len(cards), 600), "#C9CED5")
    for card_index, card in enumerate(cards):
        sheet.paste(card, (452 * card_index, 0))
    sheet.save(source / f"contact-all-{sheet_index + 1}.png")

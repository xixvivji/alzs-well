from pathlib import Path
import sys

from PIL import Image, ImageDraw, ImageOps


source = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("tmp/docx_render")
for group_index, pages in enumerate((range(1, 6), range(6, 11), range(11, 16)), start=1):
    cards = []
    for page_number in pages:
        page = Image.open(source / f"page-{page_number}.png").convert("RGB")
        page.thumbnail((430, 556))
        card = Image.new("RGB", (452, 600), "#E5E8EC")
        draw = ImageDraw.Draw(card)
        draw.text((14, 10), f"Page {page_number}", fill="#172B3D")
        card.paste(ImageOps.expand(page, border=2, fill="white"), (10, 34))
        cards.append(card)

    sheet = Image.new("RGB", (452 * len(cards), 600), "#C9CED5")
    for index, card in enumerate(cards):
        sheet.paste(card, (452 * index, 0))
    sheet.save(source / f"contact-{group_index}.png")

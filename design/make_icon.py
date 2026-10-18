"""Turn the black-on-white logo PNG into adaptive-icon foreground bitmaps.

usage: make_icon.py <source.png> <res dir> <preview.png>

The ink becomes white on transparent, the artwork is centred inside the 66/108 safe zone,
and one PNG is written per density. A preview composite is written for review.
"""
import sys
from PIL import Image, ImageDraw, ImageOps

src_path, res_dir, preview_path = sys.argv[1], sys.argv[2], sys.argv[3]

src = Image.open(src_path).convert("RGBA")
flat = Image.new("RGBA", src.size, (255, 255, 255, 255))
flat.alpha_composite(src)
gray = ImageOps.grayscale(flat)
alpha = ImageOps.invert(gray).point(lambda v: 0 if v < 24 else v)  # ink -> opaque, paper -> clear
alpha = alpha.crop(alpha.getbbox())
w, h = alpha.size

ART = 56 / 108  # artwork box inside the 66 dp safe zone, with air around it like in the widget
SHIFT_X = 0.0  # centred on the drawing's bounding box, same as the widget ring
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}


def foreground(size_px: int) -> Image.Image:
    art_max = size_px * ART
    s = min(art_max / w, art_max / h)
    aw, ah = max(1, round(w * s)), max(1, round(h * s))
    a = alpha.resize((aw, ah), Image.LANCZOS)
    fg = Image.new("RGBA", (aw, ah), (255, 255, 255, 255))
    fg.putalpha(a)
    canvas = Image.new("RGBA", (size_px, size_px), (0, 0, 0, 0))
    canvas.alpha_composite(fg, ((size_px - aw) // 2 + round(size_px * SHIFT_X), (size_px - ah) // 2))
    return canvas


for name, scale in DENSITIES.items():
    size = round(108 * scale)
    foreground(size).save(f"{res_dir}/mipmap-{name}/ic_launcher_foreground.png", optimize=True)
    print(name, size)

# preview: rounded square, circle and a 48 px tile on the brand blue, plus the source on white
BLUE = (30, 136, 229, 255)
S = 432
fg = foreground(S)
tile_sq = Image.new("RGBA", (S, S), (0, 0, 0, 0))
ImageDraw.Draw(tile_sq).rounded_rectangle((0, 0, S - 1, S - 1), radius=S * 0.22, fill=BLUE)
tile_sq.alpha_composite(fg)
tile_ci = Image.new("RGBA", (S, S), (0, 0, 0, 0))
ImageDraw.Draw(tile_ci).ellipse((0, 0, S - 1, S - 1), fill=BLUE)
tile_ci.alpha_composite(fg)
small = tile_sq.resize((144, 144), Image.LANCZOS)  # 48 dp at 3x

pad = 24
W = pad + S + pad + S + pad + 144 + pad
H = pad + S + pad
preview = Image.new("RGBA", (W, H), (255, 255, 255, 255))
preview.alpha_composite(tile_sq, (pad, pad))
preview.alpha_composite(tile_ci, (pad + S + pad, pad))
preview.alpha_composite(small, (pad + S + pad + S + pad, pad + (S - 144) // 2))
preview.convert("RGB").save(preview_path, optimize=True)
print("preview", preview.size)

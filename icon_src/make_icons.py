# Генерирует иконки приложения: оригинальный значок «фото» на фиолетовом градиенте
from PIL import Image, ImageDraw
import os

S = 1024  # рабочее разрешение (соответствует 108dp адаптивной иконки)
TOP, BOT = (111, 76, 255), (42, 26, 120)

def gradient(size):
    img = Image.new("RGB", (size, size))
    d = ImageDraw.Draw(img)
    for y in range(size):
        t = y / (size - 1)
        d.line([(0, y), (size, y)], fill=tuple(int(TOP[i] + (BOT[i] - TOP[i]) * t) for i in range(3)))
    return img.convert("RGBA")

def glyph(size, scale=1.0):
    """Белый значок «фотография»: рамка, солнце, горы. Центр, в безопасной зоне."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = size / 2
    w = size * 0.42 * scale        # ширина рамки
    h = w * 0.80
    x0, y0, x1, y1 = c - w/2, c - h/2, c + w/2, c + h/2
    stroke = size * 0.035 * scale
    r = size * 0.075 * scale
    d.rounded_rectangle([x0, y0, x1, y1], radius=r, outline="white", width=int(stroke))
    # солнце
    sr = w * 0.085
    sx, sy = x0 + w * 0.70, y0 + h * 0.30
    d.ellipse([sx - sr, sy - sr, sx + sr, sy + sr], fill="white")
    # горы (обрезаются внутренней частью рамки)
    inner = [x0 + stroke*0.5, y0 + stroke*0.5, x1 - stroke*0.5, y1 - stroke*0.5]
    m = Image.new("L", (size, size), 0)
    ImageDraw.Draw(m).rounded_rectangle(inner, radius=max(1, r - stroke/2), fill=255)
    mt = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    md = ImageDraw.Draw(mt)
    base = y1
    md.polygon([(x0, base), (x0 + w*0.33, y0 + h*0.45), (x0 + w*0.62, base)], fill="white")
    md.polygon([(x0 + w*0.42, base), (x0 + w*0.70, y0 + h*0.58), (x1, base - h*0.05), (x1, base)], fill="white")
    img.paste(mt, (0, 0), Image.composite(mt, Image.new("RGBA", (size, size)), m).split()[3])
    return img

def save(img, path, px):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.resize((px, px), Image.LANCZOS).save(path, optimize=True)

res = os.path.join(os.path.dirname(__file__), "..", "res")
densities = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}

# Классическая иконка (48dp): скруглённый квадрат с градиентом и значком
legacy = gradient(S)
mask = Image.new("L", (S, S), 0)
ImageDraw.Draw(mask).rounded_rectangle([S*0.04, S*0.04, S*0.96, S*0.96], radius=S*0.22, fill=255)
g = glyph(S, scale=1.55)
legacy.alpha_composite(g)
out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
out.paste(legacy, (0, 0), mask)

# Адаптивная иконка (108dp): отдельно фон и передний план
bg = gradient(S)
fg = glyph(S, scale=1.0)

for name, k in densities.items():
    save(out, f"{res}/mipmap-{name}/ic_launcher.png", int(48 * k))
    save(bg, f"{res}/mipmap-{name}/ic_launcher_bg.png", int(108 * k))
    save(fg, f"{res}/mipmap-{name}/ic_launcher_fg.png", int(108 * k))

# Превью для проверки
prev = Image.new("RGBA", (S, S), (255, 255, 255, 255))
prev.alpha_composite(out)
prev.resize((256, 256), Image.LANCZOS).save(os.path.join(os.path.dirname(__file__), "preview.png"))
print("icons done")

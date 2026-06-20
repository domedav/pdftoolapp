import os
import random
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import math
import shutil

FONT_PATH_DEFAULT = "assets/Roboto-Bold.ttf"

# System fonts for non-latin languages
FONTS = {
    "hi": "/usr/share/fonts/google-noto-vf/NotoSansDevanagari[wght].ttf",
    "ja": "/usr/share/fonts/google-noto-sans-cjk-vf-fonts/NotoSansCJK-VF.ttc",
    "zh-CN": "/usr/share/fonts/google-noto-sans-cjk-vf-fonts/NotoSansCJK-VF.ttc",
    "zh-TW": "/usr/share/fonts/google-noto-sans-cjk-vf-fonts/NotoSansCJK-VF.ttc",
    "ko": "/usr/share/fonts/google-noto-sans-cjk-vf-fonts/NotoSansCJK-VF.ttc",
    "ar": "/usr/share/fonts/google-noto-vf/NotoSansArabic[wght].ttf",
    "th": "/usr/share/fonts/gnu-freefont/FreeSansBold.otf",
}

# Locales that definitely need fallback for Latin characters
NON_LATIN_LOCALES = ["ar", "hi", "ja", "ko", "zh", "th"]

LOCALIZED_SCREENSHOT_TITLES = {
    "ar": ["بسيط.", "سريع.", "صور غير محدودة", "ترتيب مثالي", "حجم محسن", "جاهز في ثوانٍ"],
    "de": ["Einfach.", "Schnell.", "Unbegrenzt Fotos", "Perfekte Ordnung", "Optimierte Größe", "Fertig in Sekunden"],
    "en-US": ["Simple.", "Fast.", "Unlimited Photos", "Perfect Order", "Optimized Size", "Done in Seconds"],
    "es": ["Simple.", "Rápido.", "Fotos ilimitadas", "Orden perfecto", "Tamaño optimizado", "Listo en segundos"],
    "fr": ["Simple.", "Rapide.", "Photos illimitées", "Ordre parfait", "Taille optimisée", "Prêt en secondes"],
    "hi": ["सरल।", "तेज़।", "असीमित तस्वीरें", "सटीक क्रम", "अनुकूलित आकार", "सेकंडों में तैयार"],
    "hu-HU": ["Egyszerű.", "Gyors.", "Végtelen fotó", "Tökéletes sorrend", "Optimális méret", "Kész másodpercek alatt"],
    "in": ["Sederhana.", "Cepat.", "Foto Tanpa Batas", "Urutan Sempurna", "Ukuran Optimal", "Selesai dalam Detik"],
    "it": ["Semplice.", "Veloce.", "Foto illimitate", "Ordine perfetto", "Dimensione ottimizzata", "Pronto in pochi secondi"],
    "ja": ["かんたん", "爆速変換", "枚数無制限", "自由な並び替え", "最適なサイズ", "数秒で完了"],
    "ko": ["간편함", "초고속 변환", "무제한 사진", "완벽한 정렬", "최적화된 용량", "순식간에 완료"],
    "nl": ["Eenvoudig", "Razendsnel", "Onbeperkt foto's", "Perfecte volgorde", "Geoptimaliseerd formaat", "Klaar in seconden"],
    "pl": ["Prostota", "Szybkość", "Nielimitowane zdjęcia", "Idealna kolejność", "Optymalny rozmiar", "Gotowe w kilka sekund"],
    "pt-BR": ["Simples.", "Rápido.", "Fotos ilimitadas", "Ordem perfeita", "Tamanho otimizado", "Pronto em segundos"],
    "ru": ["Просто.", "Быстро.", "Безлимит фото", "Идеальный порядок", "Оптимальный размер", "Готово за секунды"],
    "sv": ["Enkelt.", "Snabbt.", "Obegränsat med foton", "Perfekt ordning", "Optimerad storlek", "Klart på sekunder"],
    "th": ["เรียบง่าย", "รวดเร็ว", "ไม่จำกัดจำนวนรูป", "จัดเรียงได้ดั่งใจ", "ขนาดไฟล์เหมาะสม", "เสร็จไวในไม่กี่วินาที"],
    "tr": ["Basit.", "Hızlı.", "Sınırsız Fotoğraf", "Kusursuz Düzen", "Optimize Boyut", "Saniyeler İçinde"],
    "uk": ["Просто.", "Швидко.", "Безліч фото", "Ідеальний порядок", "Оптимальний розмір", "Готово за секунди"],
    "vi": ["Đơn giản.", "Nhanh chóng.", "Ảnh không giới hạn", "Sắp xếp hoàn hảo", "Kích thước tối ưu", "Xong trong tích tắc"],
    "zh-CN": ["极简。", "极速。", "照片不限数量", "完美排序", "优化体积", "秒级完成"],
    "zh-TW": ["極簡。", "極速。", "相片無限制", "完美排序", "最佳化體積", "秒級完成"]
}

DEFAULT_TITLES = LOCALIZED_SCREENSHOT_TITLES["en-US"]

def has_glyph(font, char):
    if char.isspace(): return True
    try:
        # Check if font has the glyph and it is not an empty/tofu one
        mask = font.getmask(char)
        return mask.getbbox() is not None
    except:
        return False

def is_basic_latin(char):
    return ord(char) < 128

def get_font_fallback(lang, size):
    path = FONTS.get(lang, FONT_PATH_DEFAULT)
    if not os.path.exists(path):
        path = FONTS.get(lang.split("-")[0], FONT_PATH_DEFAULT)
    
    # Try to load primary, otherwise use default
    try:
        primary = ImageFont.truetype(path, size)
    except:
        primary = ImageFont.truetype(FONT_PATH_DEFAULT, size)
    
    fallback = ImageFont.truetype(FONT_PATH_DEFAULT, size)
    return primary, fallback

def get_text_chunks(text, primary_font, fallback_font, lang):
    if not text: return []
    chunks = []
    
    def needs_fallback(char):
        if char.isspace(): return None # Space is neutral
        # Force Latin fallback for consistency in non-latin locales
        if ord(char) < 128 and any(l in lang for l in NON_LATIN_LOCALES):
            return True
        if not has_glyph(primary_font, char):
            return True
        return False

    current_chunk = ""
    # Find first non-space to determine initial font
    first_fallback = False
    for c in text:
        nf = needs_fallback(c)
        if nf is not None:
            first_fallback = nf
            break
            
    last_use_fallback = first_fallback
    
    for char in text:
        nf = needs_fallback(char)
        # Sticky space: space uses the font of the previous chunk
        use_fallback = nf if nf is not None else last_use_fallback
        
        if use_fallback == last_use_fallback:
            current_chunk += char
        else:
            chunks.append((current_chunk, fallback_font if last_use_fallback else primary_font))
            current_chunk = char
            last_use_fallback = use_fallback
            
    chunks.append((current_chunk, fallback_font if last_use_fallback else primary_font))
    return chunks

def get_text_width(text, primary_font, fallback_font, lang):
    width = 0
    for chunk_text, font in get_text_chunks(text, primary_font, fallback_font, lang):
        width += font.getlength(chunk_text)
    return width

def draw_text_fallback(draw, pos, text, primary_font, fallback_font, lang, fill, anchor="la", align="left"):
    lines = text.split('\n')
    line_h = max(primary_font.getbbox("Ay")[3], fallback_font.getbbox("Ay")[3]) + 10
    total_h = len(lines) * line_h
    
    curr_y = pos[1]
    if anchor[1] == 'm': # middle
        curr_y -= (total_h // 2)
    elif anchor[1] == 'b': # bottom
        curr_y -= total_h

    for line in lines:
        line_w = get_text_width(line, primary_font, fallback_font, lang)
        curr_x = pos[0]
        
        if anchor[0] == 'm': # middle
            curr_x -= (line_w // 2)
        elif anchor[0] == 'r': # right
            curr_x -= line_w
            
        chunks = get_text_chunks(line, primary_font, fallback_font, lang)
        for chunk_text, font in chunks:
            draw.text((curr_x, curr_y), chunk_text, font=font, fill=fill)
            curr_x += font.getlength(chunk_text)
        
        curr_y += line_h

def wrap_text(text, primary_font, fallback_font, lang, max_width):
    lines = []
    words = text.split()
    while words:
        line = ''
        while words:
            test_line = (line + ' ' + words[0]).strip() if line else words[0]
            if get_text_width(test_line, primary_font, fallback_font, lang) <= max_width:
                line = test_line
                words.pop(0)
            else:
                break
        if not line:
            line = words.pop(0)
        lines.append(line)
    return '\n'.join(lines)

def generate_pattern_background(width, height):
    bg_color = (240, 244, 248)
    img = Image.new('RGB', (width, height), bg_color)
    draw = ImageDraw.Draw(img, 'RGBA')
    points = []
    num_points = int((width * height) / 25000)
    for _ in range(num_points):
        points.append((random.randint(-100, width+100), random.randint(-100, height+100)))
    for p1 in points:
        for p2 in points:
            dist = math.hypot(p1[0]-p2[0], p1[1]-p2[1])
            if 50 < dist < 300:
                alpha = int(255 * (1 - dist/300))
                draw.line([p1, p2], fill=(100, 150, 200, int(alpha*0.3)), width=2)
    for p in points:
        r = random.randint(5, 15)
        draw.ellipse([p[0]-r, p[1]-r, p[0]+r, p[1]+r], fill=(100, 150, 255, 100))
        draw.ellipse([p[0]-3, p[1]-3, p[0]+3, p[1]+3], fill=(255, 255, 255, 255))
    return img

def create_framed_screenshot(screenshot_path, bg_img, title_text, lang):
    canvas = bg_img.convert("RGBA")
    draw = ImageDraw.Draw(canvas)
    try:
        ss = Image.open(screenshot_path).convert("RGBA")
    except:
        ss = Image.new('RGBA', (1080, 1920), (200, 200, 200, 255))
    
    ss_w, ss_h = 860, 1720
    ss = ss.resize((ss_w, ss_h), Image.Resampling.LANCZOS)
    frame_w, frame_h = ss_w + 40, ss_h + 40
    frame_x, frame_y = (canvas.width - frame_w) // 2, 350
    
    shadow = Image.new('RGBA', (canvas.width, canvas.height), (0,0,0,0))
    shadow_draw = ImageDraw.Draw(shadow)
    shadow_draw.rounded_rectangle([frame_x+20, frame_y+20, frame_x+frame_w+20, frame_y+frame_h+20], radius=60, fill=(0,0,0,80))
    shadow = shadow.filter(ImageFilter.GaussianBlur(30))
    canvas = Image.alpha_composite(canvas, shadow)
    
    draw = ImageDraw.Draw(canvas)
    draw.rounded_rectangle([frame_x, frame_y, frame_x+frame_w, frame_y+frame_h], radius=60, fill=(240, 240, 245), outline=(200, 200, 210), width=4)
    draw.rounded_rectangle([frame_x+10, frame_y+10, frame_x+frame_w-10, frame_y+frame_h-10], radius=50, fill=(20, 20, 20))
    ss_mask = Image.new('L', ss.size, 0)
    ImageDraw.Draw(ss_mask).rounded_rectangle((0, 0, ss.size[0], ss.size[1]), radius=40, fill=255)
    canvas.paste(ss, (frame_x + 20, frame_y + 20), ss_mask)
    
    p_font, f_font = get_font_fallback(lang, 110)
    wrapped_title = wrap_text(title_text, p_font, f_font, lang, canvas.width - 160)
    
    lines = wrapped_title.split('\n')
    line_h = max(p_font.getbbox("Ay")[3], f_font.getbbox("Ay")[3]) + 10
    total_h = len(lines) * line_h
    
    header_center_y = 350 // 2
    draw_y = header_center_y - (total_h // 2)
    
    draw_text_fallback(draw, (canvas.width // 2, draw_y), wrapped_title, p_font, f_font, lang, fill=(40, 40, 50), anchor="ma", align="center")
    return canvas

def generate_localized_assets(lang, app_title):
    output_dir = f"fastlane/metadata/android/{lang}/images"
    screenshot_dir = os.path.join(output_dir, "phoneScreenshots")
    os.makedirs(screenshot_dir, exist_ok=True)
    
    titles = LOCALIZED_SCREENSHOT_TITLES.get(lang, LOCALIZED_SCREENSHOT_TITLES.get(lang.split('-')[0], DEFAULT_TITLES))
    p_font_pano, f_font_pano = get_font_fallback(lang, 160)
    
    wide_w, wide_h = 2160, 1920
    wide_bg = generate_pattern_background(wide_w, wide_h)
    try:
        ss1 = Image.open("assets/raw_1.png").convert("RGBA")
        ss1 = ss1.resize((1080, 2160), Image.Resampling.LANCZOS)
    except:
        ss1 = Image.new('RGBA', (1080, 2160), (200, 200, 200, 255))
    
    frame_w, frame_h = 1120, 2200
    device_layer = Image.new('RGBA', (frame_w + 200, frame_h + 200), (0,0,0,0))
    d_draw = ImageDraw.Draw(device_layer)
    d_draw.rounded_rectangle([100, 100, 100+frame_w, 100+frame_h], radius=80, fill=(240,240,245), outline=(200, 200, 210), width=6)
    d_draw.rounded_rectangle([110, 110, 100+frame_w-10, 100+frame_h-10], radius=70, fill=(20,20,20))
    ss1_mask = Image.new('L', ss1.size, 0)
    ImageDraw.Draw(ss1_mask).rounded_rectangle((0, 0, ss1.size[0], ss1.size[1]), radius=60, fill=255)
    device_layer.paste(ss1, (120, 120), ss1_mask)
    device_layer = device_layer.rotate(15, expand=True, resample=Image.Resampling.BICUBIC)
    
    paste_x, paste_y = (wide_w - device_layer.width)//2, (wide_h - device_layer.height)//2 + 250
    shadow_layer = Image.new('RGBA', (wide_w, wide_h), (0,0,0,0))
    shadow_layer.paste(device_layer, (paste_x + 30, paste_y + 30), device_layer)
    shadow_layer = shadow_layer.filter(ImageFilter.GaussianBlur(30))
    canvas = Image.alpha_composite(wide_bg.convert("RGBA"), shadow_layer)
    canvas.paste(device_layer, (paste_x, paste_y), device_layer)
    
    draw = ImageDraw.Draw(canvas)
    def draw_pano_text(pos, text, align):
        for ox, oy in [(-2,-2),(2,-2),(-2,2),(2,2)]:
             draw_text_fallback(draw, (pos[0]+ox, pos[1]+oy), text, p_font_pano, f_font_pano, lang, fill=(255,255,255,100), anchor="ra" if align=="right" else "la")
        draw_text_fallback(draw, pos, text, p_font_pano, f_font_pano, lang, fill=(40,40,50), anchor="ra" if align=="right" else "la")

    draw_pano_text((150, wide_h - 350), titles[0], align="left")
    draw_pano_text((wide_w - 150, 150), titles[1], align="right")
    
    canvas.crop((0, 0, 1080, 1920)).convert("RGB").save(os.path.join(screenshot_dir, "1_panorama_left.png"))
    canvas.crop((1080, 0, 2160, 1920)).convert("RGB").save(os.path.join(screenshot_dir, "2_panorama_right.png"))

    for i in range(3, 7):
        bg = generate_pattern_background(1080, 1920)
        raw_idx = i - 2
        final = create_framed_screenshot(f"assets/raw_{raw_idx}.png", bg, titles[i-1], lang)
        final.convert("RGB").save(os.path.join(screenshot_dir, f"{i}_feature.png"))

    # Feature Graphic
    bg_fg = generate_pattern_background(1024, 500)
    p_font_fg, f_font_fg = get_font_fallback(lang, 75)
    try:
        ss1_fg = Image.open("assets/raw_1.png").convert("RGBA")
        ss_w, ss_h = 240, 480
        ss1_fg = ss1_fg.resize((ss_w, ss_h), Image.Resampling.LANCZOS)
        frame_x, frame_y = 700, 50
        shadow = Image.new('RGBA', (1024, 500), (0,0,0,0))
        ImageDraw.Draw(shadow).rounded_rectangle([frame_x+10, frame_y+10, frame_x+250+10, frame_y+490+10], radius=30, fill=(0,0,0,100))
        shadow = shadow.filter(ImageFilter.GaussianBlur(15))
        bg_fg = Image.alpha_composite(bg_fg.convert("RGBA"), shadow)
        draw_fg = ImageDraw.Draw(bg_fg)
        draw_fg.rounded_rectangle([frame_x, frame_y, frame_x+260, frame_y+500], radius=30, fill=(240,240,245), outline=(200, 200, 210), width=3)
        draw_fg.rounded_rectangle([frame_x+5, frame_y+5, frame_x+255, frame_y+495], radius=25, fill=(20,20,20))
        ss_mask = Image.new('L', ss1_fg.size, 0)
        ImageDraw.Draw(ss_mask).rounded_rectangle((0, 0, ss1_fg.size[0], ss1_fg.size[1]), radius=20, fill=255)
        bg_fg.paste(ss1_fg, (frame_x+10, frame_y+10), ss_mask)
    except: pass
    
    draw_fg = ImageDraw.Draw(bg_fg)
    wrapped_app_title = wrap_text(app_title, p_font_fg, f_font_fg, lang, 550)
    lines_fg = wrapped_app_title.split('\n')
    line_h_fg = max(p_font_fg.getbbox("Ay")[3], f_font_fg.getbbox("Ay")[3]) + 10
    total_h_fg = len(lines_fg) * line_h_fg
    draw_y_fg = 250 - (total_h_fg // 2)
    draw_text_fallback(draw_fg, (100, draw_y_fg), wrapped_app_title, p_font_fg, f_font_fg, lang, fill=(40, 40, 50))
    bg_fg.convert("RGB").save(os.path.join(output_dir, "featureGraphic.png"))

    if os.path.exists("assets/store/icon_512.png"):
        icon_img = Image.open("assets/store/icon_512.png").convert("RGBA")
        white_bg = Image.new("RGBA", icon_img.size, "WHITE")
        final_icon = Image.alpha_composite(white_bg, icon_img)
        final_icon.convert("RGB").save(os.path.join(output_dir, "icon.png"), "PNG")

if __name__ == "__main__":
    fastlane_base = "fastlane/metadata/android"
    if not os.path.exists(fastlane_base):
        print(f"Error: {fastlane_base} not found.")
        exit(1)
    langs = [d for d in os.listdir(fastlane_base) if os.path.isdir(os.path.join(fastlane_base, d))]
    langs.sort()
    total = len(langs)
    for idx, lang in enumerate(langs, 1):
        lang_path = os.path.join(fastlane_base, lang)
        title_file = os.path.join(lang_path, "title.txt")
        if os.path.exists(title_file):
            with open(title_file, "r") as f: app_title = f.read().strip()
            print(f"[{idx}/{total}] Generating assets for {lang} ({app_title})...")
            generate_localized_assets(lang, app_title)
    print("Localized assets generation complete.")
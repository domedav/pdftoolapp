import os
import random
import urllib.request
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import math
import shutil

FONT_URL = "https://github.com/googlefonts/roboto/raw/main/src/hinted/Roboto-Bold.ttf"
FONT_PATH = "assets/Roboto-Bold.ttf"

# Target directory for Fastlane metadata
FASTLANE_DIR = "fastlane/metadata/android/en-US/images"

if not os.path.exists(FONT_PATH):
    print("Downloading Roboto font...")
    os.makedirs("assets", exist_ok=True)
    urllib.request.urlretrieve(FONT_URL, FONT_PATH)

def generate_pattern_background(width, height):
    # Bright background: Soft Grayish Blue
    bg_color = (240, 244, 248)
    img = Image.new('RGB', (width, height), bg_color)
    draw = ImageDraw.Draw(img, 'RGBA')
    
    # Abstract connected dots pattern
    points = []
    num_points = int((width * height) / 25000)
    for _ in range(num_points):
        points.append((random.randint(-100, width+100), random.randint(-100, height+100)))
        
    # Draw connections
    for p1 in points:
        for p2 in points:
            dist = math.hypot(p1[0]-p2[0], p1[1]-p2[1])
            if 50 < dist < 300:
                alpha = int(255 * (1 - dist/300))
                draw.line([p1, p2], fill=(100, 150, 200, int(alpha*0.3)), width=2)
                
    # Draw larger dots
    for p in points:
        r = random.randint(5, 15)
        draw.ellipse([p[0]-r, p[1]-r, p[0]+r, p[1]+r], fill=(100, 150, 255, 100))
        draw.ellipse([p[0]-3, p[1]-3, p[0]+3, p[1]+3], fill=(255, 255, 255, 255))
        
    return img

def create_framed_screenshot(screenshot_path, bg_img, title_text, y_offset=350):
    canvas = bg_img.convert("RGBA")
    draw = ImageDraw.Draw(canvas)
    
    try:
        ss = Image.open(screenshot_path).convert("RGBA")
    except Exception:
        print(f"Warning: Could not find {screenshot_path}. Using placeholder.")
        ss = Image.new('RGBA', (1080, 1920), (200, 200, 200, 255))
        
    ss_w, ss_h = 860, 1720
    ss = ss.resize((ss_w, ss_h), Image.Resampling.LANCZOS)
    
    # Frame dimensions
    frame_w, frame_h = ss_w + 40, ss_h + 40
    frame_x, frame_y = (canvas.width - frame_w) // 2, y_offset
    
    # Shadow
    shadow = Image.new('RGBA', (canvas.width, canvas.height), (0,0,0,0))
    shadow_draw = ImageDraw.Draw(shadow)
    shadow_draw.rounded_rectangle([frame_x+20, frame_y+20, frame_x+frame_w+20, frame_y+frame_h+20], radius=60, fill=(0,0,0,80))
    shadow = shadow.filter(ImageFilter.GaussianBlur(30))
    canvas = Image.alpha_composite(canvas, shadow)
    
    # Device Outer Frame
    draw = ImageDraw.Draw(canvas)
    draw.rounded_rectangle([frame_x, frame_y, frame_x+frame_w, frame_y+frame_h], radius=60, fill=(240, 240, 245), outline=(200, 200, 210), width=4)
    # Inner Bezel
    draw.rounded_rectangle([frame_x+10, frame_y+10, frame_x+frame_w-10, frame_y+frame_h-10], radius=50, fill=(20, 20, 20))
    
    # Paste screenshot with mask to prevent overfilling rounded corners
    ss_mask = Image.new('L', ss.size, 0)
    ImageDraw.Draw(ss_mask).rounded_rectangle((0, 0, ss.size[0], ss.size[1]), radius=40, fill=255)
    canvas.paste(ss, (frame_x + 20, frame_y + 20), ss_mask)
    
    # Draw Text
    try:
        font = ImageFont.truetype(FONT_PATH, 110)
    except:
        font = ImageFont.load_default()
    
    bbox = draw.textbbox((0, 0), title_text, font=font)
    text_w = bbox[2] - bbox[0]
    draw.text(((canvas.width - text_w) // 2, 120), title_text, font=font, fill=(40, 40, 50))
    
    return canvas

def generate_promotional_gallery():
    screenshot_dir = os.path.join(FASTLANE_DIR, "phoneScreenshots")
    os.makedirs(screenshot_dir, exist_ok=True)
    
    # Panorama Image 1 & 2
    wide_w, wide_h = 2160, 1920
    wide_bg = generate_pattern_background(wide_w, wide_h)
    
    try:
        ss1 = Image.open("assets/raw_2.png").convert("RGBA")
        ss1 = ss1.resize((1080, 2160), Image.Resampling.LANCZOS)
    except:
        ss1 = Image.new('RGBA', (1080, 2160), (200, 200, 200, 255))
    
    # Create the rotated device
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
    try:
        font = ImageFont.truetype(FONT_PATH, 160) 
    except:
        font = ImageFont.load_default()
        
    def draw_text_with_shadow(draw, pos, text, font, fill=(40,40,50)):
        x, y = pos
        draw.text((x+4, y+4), text, font=font, fill=(255,255,255,150))
        draw.text(pos, text, font=font, fill=fill)

    draw_text_with_shadow(draw, (150, wide_h - 350), "Simple", font=font)
    draw_text_with_shadow(draw, (wide_w - 800, 150), "      Fast", font=font)
    
    canvas.crop((0, 0, 1080, 1920)).convert("RGB").save(os.path.join(screenshot_dir, "1_panorama_left.png"))
    canvas.crop((1080, 0, 2160, 1920)).convert("RGB").save(os.path.join(screenshot_dir, "2_panorama_right.png"))
    
    # Images 3-6
    titles = ["Unlimited Photos", "Perfect Order", "Optimized Size", "Done in Seconds"]
    for i in range(3, 7):
        bg = generate_pattern_background(1080, 1920)
        final = create_framed_screenshot(f"assets/raw_{i-2}.png", bg, titles[i-3])
        final.convert("RGB").save(os.path.join(screenshot_dir, f"{i}_feature.png"))

def generate_fastlane_icon():
    try:
        import cairosvg
        svg_foreground = """<svg width="108" height="108" viewBox="0 0 108 108" fill="none" xmlns="http://www.w3.org/2000/svg">
          <g transform="translate(12, 28)">
            <rect x="0" y="10" width="36" height="28" rx="3" fill="#81D4FA"/>
            <circle cx="28" cy="18" r="4" fill="#FFF176"/>
            <path d="M0,38l10,-12l8,10l8,-10l10,12H0z" fill="#4CAF50"/>
            <rect x="0" y="10" width="36" height="28" rx="3" stroke="white" stroke-width="2" fill="none"/>
            <path d="M41,24h12m0,0l-4,-4m4,4l-4,4" stroke="#3F51B5" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
            <path d="M60,8h14l10,10v20c0,2.2 -1.8,4 -4,4h-20c-2.2,0 -4,-1.8 -4,-4V8z" fill="#F44336"/>
            <path d="M74,8v10h10" fill="#B71C1C"/>
            <path d="M64,20h12M64,26h12M64,32h12" stroke="white" stroke-width="2" stroke-linecap="round" fill="none"/>
          </g>
        </svg>"""
        cairosvg.svg2png(bytestring=svg_foreground.encode('utf-8'), write_to=os.path.join(FASTLANE_DIR, "icon.png"), output_width=512, output_height=512)
        print("Fastlane icon generated.")
    except ImportError:
        print("cairosvg not found, skipping icon generation.")

def generate_feature_graphic():
    bg = generate_pattern_background(1024, 500)
    try:
        ss1 = Image.open("assets/raw_2.png").convert("RGBA")
        ss_w, ss_h = 300, 600
        ss1 = ss1.resize((ss_w, ss_h), Image.Resampling.LANCZOS)
        frame_x, frame_y = 650, 100
        
        shadow = Image.new('RGBA', (1024, 500), (0,0,0,0))
        ImageDraw.Draw(shadow).rounded_rectangle([frame_x+10, frame_y+10, frame_x+320+10, frame_y+620+10], radius=30, fill=(0,0,0,100))
        shadow = shadow.filter(ImageFilter.GaussianBlur(15))
        bg = Image.alpha_composite(bg.convert("RGBA"), shadow)
        
        draw = ImageDraw.Draw(bg)
        draw.rounded_rectangle([frame_x, frame_y, frame_x+320, frame_y+620], radius=30, fill=(240,240,245), outline=(200, 200, 210), width=3)
        draw.rounded_rectangle([frame_x+5, frame_y+5, frame_x+315, frame_y+615], radius=25, fill=(20,20,20))
        
        ss1_mask = Image.new('L', ss1.size, 0)
        ImageDraw.Draw(ss1_mask).rounded_rectangle((0, 0, ss1.size[0], ss1.size[1]), radius=20, fill=255)
        bg.paste(ss1, (frame_x+10, frame_y+10), ss1_mask)
    except Exception as e:
        print("Feature graphic screenshot error:", e)
    
    draw = ImageDraw.Draw(bg)
    try:
        font = ImageFont.truetype(FONT_PATH, 70)
    except:
        font = ImageFont.load_default()
    draw.text((100, 200), "Image to PDF", font=font, fill=(40,40,50))
    bg.convert("RGB").save(os.path.join(FASTLANE_DIR, "featureGraphic.png"))
    print("Feature graphic generated.")

if __name__ == "__main__":
    os.makedirs(FASTLANE_DIR, exist_ok=True)
    generate_fastlane_icon()
    generate_feature_graphic()
    generate_promotional_gallery()
    print(f"Fastlane assets generated in {FASTLANE_DIR}")

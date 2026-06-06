import os
import random
import urllib.request
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import math

FONT_URL = "https://github.com/googlefonts/roboto/raw/main/src/hinted/Roboto-Bold.ttf"
FONT_PATH = "assets/Roboto-Bold.ttf"

if not os.path.exists(FONT_PATH):
    print("Downloading Roboto font...")
    urllib.request.urlretrieve(FONT_URL, FONT_PATH)

def generate_pattern_background(width, height, output_path):
    # Bright background: Soft Grayish Blue
    bg_color = (240, 244, 248)
    img = Image.new('RGB', (width, height), bg_color)
    draw = ImageDraw.Draw(img, 'RGBA')
    
    # Abstract connected dots pattern (darker for contrast against bright bg)
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
                # Light blue/grey lines
                draw.line([p1, p2], fill=(100, 150, 200, int(alpha*0.3)), width=2)
                
    # Draw larger dots
    for p in points:
        r = random.randint(5, 15)
        draw.ellipse([p[0]-r, p[1]-r, p[0]+r, p[1]+r], fill=(100, 150, 255, 100))
        draw.ellipse([p[0]-3, p[1]-3, p[0]+3, p[1]+3], fill=(255, 255, 255, 255))
        
    img.save(output_path)
    return img

def create_framed_screenshot(screenshot_path, bg_img, title_text, y_offset=350):
    canvas = bg_img.convert("RGBA")
    draw = ImageDraw.Draw(canvas)
    
    try:
        ss = Image.open(screenshot_path).convert("RGBA")
    except Exception as e:
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
    
    # Draw Text (High contrast dark grey)
    try:
        font = ImageFont.truetype(FONT_PATH, 110)
    except:
        font = ImageFont.load_default()
    
    # Center text
    bbox = draw.textbbox((0, 0), title_text, font=font)
    text_w = bbox[2] - bbox[0]
    draw.text(((canvas.width - text_w) // 2, 120), title_text, font=font, fill=(40, 40, 50))
    
    return canvas

def generate_promotional_gallery():
    os.makedirs("fastlane/metadata/android/en-US/images/phoneScreenshots", exist_ok=True)
    
    # Panorama Image 1 & 2
    wide_w, wide_h = 2160, 1920
    wide_bg = generate_pattern_background(wide_w, wide_h, "assets/store/wide_bg.png")
    
    try:
        ss1 = Image.open("assets/raw_1.png").convert("RGBA")
        ss1 = ss1.resize((1080, 2160), Image.Resampling.LANCZOS)
    except:
        ss1 = Image.new('RGBA', (1080, 2160), (200, 200, 200, 255))
    
    # Create the rotated device
    frame_w, frame_h = 1120, 2200
    device_layer = Image.new('RGBA', (frame_w + 200, frame_h + 200), (0,0,0,0))
    d_draw = ImageDraw.Draw(device_layer)
    # Outer Bezel
    d_draw.rounded_rectangle([100, 100, 100+frame_w, 100+frame_h], radius=80, fill=(240,240,245), outline=(200, 200, 210), width=6)
    # Inner Bezel
    d_draw.rounded_rectangle([110, 110, 100+frame_w-10, 100+frame_h-10], radius=70, fill=(20,20,20))
    # Screenshot with mask to prevent overfill
    ss1_mask = Image.new('L', ss1.size, 0)
    ImageDraw.Draw(ss1_mask).rounded_rectangle((0, 0, ss1.size[0], ss1.size[1]), radius=60, fill=255)
    device_layer.paste(ss1, (120, 120), ss1_mask)
    
    # Rotate the device
    device_layer = device_layer.rotate(15, expand=True, resample=Image.Resampling.BICUBIC)
    
    # Paste the tilted device bridging the two images
    paste_x, paste_y = (wide_w - device_layer.width)//2, (wide_h - device_layer.height)//2 + 250
    
    # Add shadow for the tilted device
    shadow_layer = Image.new('RGBA', (wide_w, wide_h), (0,0,0,0))
    shadow_layer.paste(device_layer, (paste_x + 30, paste_y + 30), device_layer)
    shadow_layer = shadow_layer.filter(ImageFilter.GaussianBlur(30))
    
    canvas = Image.alpha_composite(wide_bg.convert("RGBA"), shadow_layer)
    canvas.paste(device_layer, (paste_x, paste_y), device_layer)
    
    # Add Text on both sides - Ensure visibility, no overlap
    draw = ImageDraw.Draw(canvas)
    try:
        font = ImageFont.truetype(FONT_PATH, 160) 
    except:
        font = ImageFont.load_default()
        
    def draw_text_with_shadow(draw, pos, text, font, fill=(40,40,50)):
        x, y = pos
        # Soft white glow/shadow behind dark text for pop
        draw.text((x+4, y+4), text, font=font, fill=(255,255,255,150))
        draw.text(pos, text, font=font, fill=fill)

    # Place text higher to avoid the phone, or according to layout requirements
    draw_text_with_shadow(draw, (150, wide_h - 350), "Private.", font=font)
    draw_text_with_shadow(draw, (wide_w - 800, 150), "Powerful.", font=font)
    
    # Save the splits
    img1 = canvas.crop((0, 0, 1080, 1920)).convert("RGB")
    img2 = canvas.crop((1080, 0, 2160, 1920)).convert("RGB")
    
    img1.save("fastlane/metadata/android/en-US/images/phoneScreenshots/1_panorama_left.png")
    img2.save("fastlane/metadata/android/en-US/images/phoneScreenshots/2_panorama_right.png")
    
    # Images 3-6
    titles = [
        "Unlimited Photos",
        "Perfect Order",
        "Optimized Size",
        "Done in Seconds"
    ]
    
    for i in range(3, 7):
        bg = generate_pattern_background(1080, 1920, f"assets/store/bg_{i}.png")
        raw_idx = i - 2 
        final = create_framed_screenshot(f"assets/raw_{raw_idx}.png", bg, titles[i-3])
        final.convert("RGB").save(f"fastlane/metadata/android/en-US/images/phoneScreenshots/{i}_feature.png")

def generate_icons():
    import cairosvg
    # Improved Colorful Foreground (Image -> Arrow -> Centered PDF)
    # Total width used: ~84 units. 108-84 = 24 / 2 = 12 margin.
    svg_foreground = """<svg width="108" height="108" viewBox="0 0 108 108" fill="none" xmlns="http://www.w3.org/2000/svg">
      <g transform="translate(12, 28)">
        <!-- PHOTO ICON (Width 36, Height 28) -->
        <rect x="0" y="10" width="36" height="28" rx="3" fill="#81D4FA"/>
        <circle cx="28" cy="18" r="4" fill="#FFF176"/>
        <path d="M0,38l10,-12l8,10l8,-10l10,12H0z" fill="#4CAF50"/>
        <rect x="0" y="10" width="36" height="28" rx="3" stroke="white" stroke-width="2" fill="none"/>

        <!-- ARROW (Moved left by 3 to balance between visual centers) -->
        <path d="M41,24h12m0,0l-4,-4m4,4l-4,4" stroke="#3F51B5" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" fill="none"/>

        <!-- PDF ICON (x: 60 to 84, Width 24, Height 32) -->
        <path d="M60,8h14l10,10v20c0,2.2 -1.8,4 -4,4h-20c-2.2,0 -4,-1.8 -4,-4V8z" fill="#F44336"/>
        <path d="M74,8v10h10" fill="#B71C1C"/>
        <!-- Centered Lines (Moved left by 2 to visually center within the document body) -->
        <path d="M64,20h12M64,26h12M64,32h12" stroke="white" stroke-width="2" stroke-linecap="round" fill="none"/>
      </g>
    </svg>"""

    # Monochrome (Outlines only, perfectly aligned)
    svg_monochrome = """<svg width="108" height="108" viewBox="0 0 108 108" fill="none" xmlns="http://www.w3.org/2000/svg">
      <g transform="translate(12, 28)">
        <rect x="0" y="10" width="36" height="28" rx="3" stroke="black" stroke-width="3" fill="none"/>
        <circle cx="28" cy="18" r="3" stroke="black" stroke-width="2" fill="none"/>
        <path d="M0,38l10,-12l8,10l8,-10l10,12" stroke="black" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" fill="none"/>

        <path d="M41,24h12m0,0l-4,-4m4,4l-4,4" stroke="black" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" fill="none"/>

        <path d="M60,8h14l10,10v20c0,2.2 -1.8,4 -4,4h-20c-2.2,0 -4,-1.8 -4,-4V8z" stroke="black" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
        <path d="M74,8v10h10" stroke="black" stroke-width="3" fill="none"/>
        <path d="M64,20h12M64,26h12M64,32h12" stroke="black" stroke-width="2" stroke-linecap="round" fill="none"/>
      </g>
    </svg>"""

    # Store Icon
    store_icon_path = "assets/store/icon_512.png"
    cairosvg.svg2png(bytestring=svg_foreground.encode('utf-8'), write_to=store_icon_path, output_width=512, output_height=512)
    print("Store icon generated.")

    import io
    densities = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for dpi, size in densities.items():
        path = f"app/src/main/res/mipmap-{dpi}"
        os.makedirs(path, exist_ok=True)
        # Foreground
        fg_png = cairosvg.svg2png(bytestring=svg_foreground.encode('utf-8'), output_width=size, output_height=size)
        Image.open(io.BytesIO(fg_png)).save(f"{path}/ic_launcher_foreground.webp", format="WEBP")

        # Monochrome
        mono_png = cairosvg.svg2png(bytestring=svg_monochrome.encode('utf-8'), output_width=size, output_height=size)
        Image.open(io.BytesIO(mono_png)).save(f"{path}/ic_launcher_monochrome.webp", format="WEBP")

        # We'll use a combined version for legacy ic_launcher
        full_svg = f'<svg width="108" height="108"><rect width="108" height="108" fill="white"/>{svg_foreground[svg_foreground.find("<g"):svg_foreground.rfind("</g>")+4]}</svg>'
        full_png = cairosvg.svg2png(bytestring=full_svg.encode('utf-8'), output_width=size, output_height=size)
        icon = Image.open(io.BytesIO(full_png)).convert("RGBA")

        # Round icon
        mask = Image.new('L', (size, size), 0); draw = ImageDraw.Draw(mask); draw.ellipse((0, 0, size, size), fill=255)
        rounded = Image.new('RGBA', (size, size), (0,0,0,0)); rounded.paste(icon, mask=mask)

        # WebP variants only
        icon.save(f"{path}/ic_launcher.webp", format="WEBP")
        rounded.save(f"{path}/ic_launcher_round.webp", format="WEBP")

    print("Mipmaps generated (WebP only).")

    # Generate VectorDrawable XMLs to ensure they match everywhere
    vector_foreground = """<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <group android:translateX="12" android:translateY="28">
    <path android:fillColor="#81D4FA" android:pathData="M3,10 h30 a3,3 0 0 1 3,3 v22 a3,3 0 0 1 -3,3 h-30 a3,3 0 0 1 -3,-3 v-22 a3,3 0 0 1 3,-3 z"/>
    <path android:fillColor="#FFF176" android:pathData="M24,18 a4,4 0 1,0 8,0 a4,4 0 1,0 -8,0"/>
    <path android:fillColor="#4CAF50" android:pathData="M0,38 l10,-12 l8,10 l8,-10 l10,12 H0 z"/>
    <path android:strokeColor="#FFFFFF" android:strokeWidth="2" android:pathData="M3,10 h30 a3,3 0 0 1 3,3 v22 a3,3 0 0 1 -3,3 h-30 a3,3 0 0 1 -3,-3 v-22 a3,3 0 0 1 3,-3 z"/>
    <path android:strokeColor="#3F51B5" android:strokeWidth="3" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="M41,24 h12 m0,0 l-4,-4 m4,4 l-4,4"/>
    <path android:fillColor="#F44336" android:pathData="M60,8 h14 l10,10 v20 c0,2.2 -1.8,4 -4,4 h-20 c-2.2,0 -4,-1.8 -4,-4 V8 z"/>
    <path android:fillColor="#B71C1C" android:pathData="M74,8 v10 h10"/>
    <path android:strokeColor="#FFFFFF" android:strokeWidth="2" android:strokeLineCap="round" android:pathData="M64,20 h12 M64,26 h12 M64,32 h12"/>
    </group>
    </vector>"""

    vector_monochrome = """<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <group android:translateX="12" android:translateY="28">
    <path android:strokeColor="#000000" android:strokeWidth="3" android:pathData="M3,10 h30 a3,3 0 0 1 3,3 v22 a3,3 0 0 1 -3,3 h-30 a3,3 0 0 1 -3,-3 v-22 a3,3 0 0 1 3,-3 z"/>
    <path android:strokeColor="#000000" android:strokeWidth="2" android:pathData="M25,18 a3,3 0 1,0 6,0 a3,3 0 1,0 -6,0"/>
    <path android:strokeColor="#000000" android:strokeWidth="3" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="M0,38 l10,-12 l8,10 l8,-10 l10,12"/>
    <path android:strokeColor="#000000" android:strokeWidth="3" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="M41,24 h12 m0,0 l-4,-4 m4,4 l-4,4"/>
    <path android:strokeColor="#000000" android:strokeWidth="3" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="M60,8 h14 l10,10 v20 c0,2.2 -1.8,4 -4,4 h-20 c-2.2,0 -4,-1.8 -4,-4 V8 z"/>
    <path android:strokeColor="#000000" android:strokeWidth="3" android:pathData="M74,8 v10 h10"/>
    <path android:strokeColor="#000000" android:strokeWidth="2" android:strokeLineCap="round" android:pathData="M64,20 h12 M64,26 h12 M64,32 h12"/>
    </group>
    </vector>"""
    
    os.makedirs("app/src/main/res/drawable", exist_ok=True)
    with open("app/src/main/res/drawable/ic_launcher_foreground.xml", "w") as f:
        f.write(vector_foreground)
    with open("app/src/main/res/drawable/ic_launcher_monochrome.xml", "w") as f:
        f.write(vector_monochrome)

    print("VectorDrawables generated.")

    # Overwrite splash, web, and playstore icons
    import shutil
    shutil.copy("assets/store/icon_512.png", "app/src/main/res/mipmap/mip_splash.png")
    shutil.copy("assets/store/icon_512.png", "app/src/main/res/ic_launcher-web.png")
    shutil.copy("assets/store/icon_512.png", "app/src/main/res/playstore-icon.png")
    print("Standalone icons synced.")

if __name__ == "__main__":
    os.makedirs("assets/store", exist_ok=True)
    os.makedirs("fastlane/metadata/android/en-US/images", exist_ok=True)
    
    # Background for feature graphic
    bg = generate_pattern_background(1024, 500, "assets/store/feature_graphic.png")
    
    # Add Screenshot to Feature Graphic
    try:
        ss1 = Image.open("assets/raw_1.png").convert("RGBA")
        # Resize to fit in feature graphic
        ss_w, ss_h = 300, 600
        ss1 = ss1.resize((ss_w, ss_h), Image.Resampling.LANCZOS)
        
        # Draw frame for screenshot
        frame_w, frame_h = ss_w + 20, ss_h + 20
        frame_x, frame_y = 650, 100 # Put it on the right side
        
        # Shadow
        shadow = Image.new('RGBA', (1024, 500), (0,0,0,0))
        shadow_draw = ImageDraw.Draw(shadow)
        shadow_draw.rounded_rectangle([frame_x+10, frame_y+10, frame_x+frame_w+10, frame_y+frame_h+10], radius=30, fill=(0,0,0,100))
        shadow = shadow.filter(ImageFilter.GaussianBlur(15))
        bg = Image.alpha_composite(bg.convert("RGBA"), shadow)
        
        # Outer Frame
        draw = ImageDraw.Draw(bg)
        draw.rounded_rectangle([frame_x, frame_y, frame_x+frame_w, frame_y+frame_h], radius=30, fill=(240,240,245), outline=(200, 200, 210), width=3)
        draw.rounded_rectangle([frame_x+5, frame_y+5, frame_x+frame_w-5, frame_y+frame_h-5], radius=25, fill=(20,20,20))
        
        # Mask screenshot
        ss1_mask = Image.new('L', ss1.size, 0)
        ImageDraw.Draw(ss1_mask).rounded_rectangle((0, 0, ss1.size[0], ss1.size[1]), radius=20, fill=255)
        bg.paste(ss1, (frame_x+10, frame_y+10), ss1_mask)
        
    except Exception as e:
        print("Could not paste screenshot to feature graphic:", e)
        draw = ImageDraw.Draw(bg)
    
    # Add Title to Feature Graphic
    draw = ImageDraw.Draw(bg)
    try:
        font = ImageFont.truetype(FONT_PATH, 70)
    except:
        font = ImageFont.load_default()
    draw.text((100, 200), "Image to PDF Maker", font=font, fill=(40,40,50))
    bg.save("fastlane/metadata/android/en-US/images/featureGraphic.png")
    
    try:
        import cairosvg
        generate_icons()
        # Copy store icon to fastlane
        import shutil
        shutil.copy("assets/store/icon_512.png", "fastlane/metadata/android/en-US/images/icon.png")
    except ImportError:
        print("cairosvg not installed, skipping high-res icon generation. Please install cairosvg (pip install cairosvg) to generate icons.")
        
    generate_promotional_gallery()
    print("All assets successfully generated!")

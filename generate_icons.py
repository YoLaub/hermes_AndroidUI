import math
import os
from PIL import Image, ImageDraw, ImageFilter

SIZE = 512
SCALE = 2
W = SIZE * SCALE
H = SIZE * SCALE

# Canvas for supersampled drawing (1024x1024 for crisp antialiasing)
canvas = Image.new('RGBA', (W, H), (0, 0, 0, 0))
draw = ImageDraw.Draw(canvas)

# 1. Background gradient (Deep Midnight Navy to Royal Indigo/Violet)
for y in range(H):
    t = y / H
    r = int(11 * (1 - t) + 26 * t)
    g = int(15 * (1 - t) + 21 * t)
    b = int(28 * (1 - t) + 60 * t)
    draw.line([(0, y), (W, y)], fill=(r, g, b, 255))

# 2. Subtle radial glow behind the emblem
glow_layer = Image.new('RGBA', (W, H), (0, 0, 0, 0))
glow_draw = ImageDraw.Draw(glow_layer)
center_x, center_y = W // 2, H // 2
glow_draw.ellipse(
    [center_x - 320, center_y - 320, center_x + 320, center_y + 320],
    fill=(245, 197, 66, 35)
)
glow_draw.ellipse(
    [center_x - 200, center_y - 200, center_x + 200, center_y + 200],
    fill=(233, 69, 96, 25)
)
glow_layer = glow_layer.filter(ImageFilter.GaussianBlur(radius=60))
canvas = Image.alpha_composite(canvas, glow_layer)
draw = ImageDraw.Draw(canvas)

# 3. Geometric / Technical ambient grid lines (subtle)
for x in range(W // 4, 3 * W // 4, 80):
    draw.line([(x, H // 4), (x, 3 * H // 4)], fill=(255, 255, 255, 12), width=2)
for y in range(H // 4, 3 * H // 4, 80):
    draw.line([(W // 4, y), (3 * W // 4, y)], fill=(255, 255, 255, 12), width=2)

# 4. Central Hermes Caduceus / Winged Emblem
# Colors:
GOLD_BRIGHT = (255, 215, 75, 255)
GOLD_MID = (245, 185, 45, 255)
GOLD_DARK = (212, 140, 20, 255)
AMBER_DEEP = (235, 110, 40, 255)
WHITE_GLOW = (255, 255, 240, 240)

# Central Staff
staff_w = 28
staff_top = int(H * 0.28)
staff_bottom = int(H * 0.80)
draw.rounded_rectangle(
    [center_x - staff_w//2, staff_top, center_x + staff_w//2, staff_bottom],
    radius=14,
    fill=GOLD_MID
)

# Staff highlight
draw.rounded_rectangle(
    [center_x - 4, staff_top + 8, center_x + 4, staff_bottom - 8],
    radius=4,
    fill=GOLD_BRIGHT
)

# Staff top orb
orb_r = 44
draw.ellipse(
    [center_x - orb_r, staff_top - orb_r + 8, center_x + orb_r, staff_top + orb_r + 8],
    fill=GOLD_MID
)
draw.ellipse(
    [center_x - orb_r + 8, staff_top - orb_r + 14, center_x + orb_r - 8, staff_top + orb_r + 2],
    fill=GOLD_BRIGHT
)
draw.ellipse(
    [center_x - 14, staff_top - 18, center_x + 14, staff_top + 4],
    fill=WHITE_GLOW
)

# Wings: Left & Right sweeping stylized wings
# Upper wing tier
wing_upper_pts_left = [
    (center_x - 12, int(H * 0.36)),
    (center_x - 140, int(H * 0.26)),
    (center_x - 310, int(H * 0.24)),
    (center_x - 340, int(H * 0.32)),
    (center_x - 220, int(H * 0.38)),
    (center_x - 120, int(H * 0.42)),
    (center_x - 12, int(H * 0.44)),
]
draw.polygon(wing_upper_pts_left, fill=GOLD_BRIGHT)

wing_upper_pts_right = [
    (center_x + 12, int(H * 0.36)),
    (center_x + 140, int(H * 0.26)),
    (center_x + 310, int(H * 0.24)),
    (center_x + 340, int(H * 0.32)),
    (center_x + 220, int(H * 0.38)),
    (center_x + 120, int(H * 0.42)),
    (center_x + 12, int(H * 0.44)),
]
draw.polygon(wing_upper_pts_right, fill=GOLD_BRIGHT)

# Mid wing tier
wing_mid_pts_left = [
    (center_x - 12, int(H * 0.44)),
    (center_x - 130, int(H * 0.43)),
    (center_x - 280, int(H * 0.42)),
    (center_x - 295, int(H * 0.48)),
    (center_x - 190, int(H * 0.52)),
    (center_x - 100, int(H * 0.53)),
    (center_x - 12, int(H * 0.52)),
]
draw.polygon(wing_mid_pts_left, fill=GOLD_MID)

wing_mid_pts_right = [
    (center_x + 12, int(H * 0.44)),
    (center_x + 130, int(H * 0.43)),
    (center_x + 280, int(H * 0.42)),
    (center_x + 295, int(H * 0.48)),
    (center_x + 190, int(H * 0.52)),
    (center_x + 100, int(H * 0.53)),
    (center_x + 12, int(H * 0.52)),
]
draw.polygon(wing_mid_pts_right, fill=GOLD_MID)

# Lower wing tier
wing_lower_pts_left = [
    (center_x - 12, int(H * 0.52)),
    (center_x - 110, int(H * 0.53)),
    (center_x - 220, int(H * 0.56)),
    (center_x - 230, int(H * 0.62)),
    (center_x - 150, int(H * 0.63)),
    (center_x - 70, int(H * 0.61)),
    (center_x - 12, int(H * 0.58)),
]
draw.polygon(wing_lower_pts_left, fill=GOLD_DARK)

wing_lower_pts_right = [
    (center_x + 12, int(H * 0.52)),
    (center_x + 110, int(H * 0.53)),
    (center_x + 220, int(H * 0.56)),
    (center_x + 230, int(H * 0.62)),
    (center_x + 150, int(H * 0.63)),
    (center_x + 70, int(H * 0.61)),
    (center_x + 12, int(H * 0.58)),
]
draw.polygon(wing_lower_pts_right, fill=GOLD_DARK)

# Serpents / Entwined Energy Curves
# Upper curves
draw.arc([center_x - 90, int(H * 0.52), center_x + 90, int(H * 0.68)], start=210, end=330, fill=GOLD_BRIGHT, width=16)
draw.arc([center_x - 110, int(H * 0.62), center_x + 110, int(H * 0.78)], start=30, end=150, fill=GOLD_MID, width=16)
draw.arc([center_x - 70, int(H * 0.70), center_x + 70, int(H * 0.82)], start=210, end=330, fill=GOLD_DARK, width=14)

# Squircle Mask for standard icon
squircle_mask = Image.new('L', (W, H), 0)
sq_draw = ImageDraw.Draw(squircle_mask)
padding = 40
sq_draw.rounded_rectangle([padding, padding, W - padding, H - padding], radius=220, fill=255)

# Border highlight on the squircle
border = Image.new('RGBA', (W, H), (0, 0, 0, 0))
border_draw = ImageDraw.Draw(border)
border_draw.rounded_rectangle(
    [padding + 4, padding + 4, W - padding - 4, H - padding - 4],
    radius=216,
    outline=(255, 215, 75, 100),
    width=6
)

squircle_icon = Image.new('RGBA', (W, H), (0, 0, 0, 0))
squircle_icon.paste(canvas, (0, 0), squircle_mask)
squircle_icon = Image.alpha_composite(squircle_icon, border)

# Round Mask for round launcher icon
round_mask = Image.new('L', (W, H), 0)
round_draw = ImageDraw.Draw(round_mask)
round_draw.ellipse([padding, padding, W - padding, H - padding], fill=255)

round_border = Image.new('RGBA', (W, H), (0, 0, 0, 0))
rb_draw = ImageDraw.Draw(round_border)
rb_draw.ellipse([padding + 4, padding + 4, W - padding - 4, H - padding - 4], outline=(255, 215, 75, 100), width=6)

round_icon = Image.new('RGBA', (W, H), (0, 0, 0, 0))
round_icon.paste(canvas, (0, 0), round_mask)
round_icon = Image.alpha_composite(round_icon, round_border)

# Downscale with high quality Lanczos filter to 512x512
final_squircle = squircle_icon.resize((SIZE, SIZE), Image.Resampling.LANCZOS)
final_round = round_icon.resize((SIZE, SIZE), Image.Resampling.LANCZOS)

res_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'android-app', 'app', 'src', 'main', 'res')

# 1. Save 512x512 app logo in drawable
final_squircle.save(f'{res_dir}/drawable/hermes_logo.png')
print('Saved hermes_logo.png')

# 2. Save mipmaps for all standard Android screen densities
MIPMAP_SIZES = {
    'mipmap-mdpi': 48,
    'mipmap-hdpi': 72,
    'mipmap-xhdpi': 96,
    'mipmap-xxhdpi': 144,
    'mipmap-xxxhdpi': 192,
}

for folder, s in MIPMAP_SIZES.items():
    f_path = f'{res_dir}/{folder}'
    os.makedirs(f_path, exist_ok=True)
    # ic_launcher
    sq_resized = final_squircle.resize((s, s), Image.Resampling.LANCZOS)
    sq_resized.save(f'{f_path}/ic_launcher.png')
    # ic_launcher_round
    rd_resized = final_round.resize((s, s), Image.Resampling.LANCZOS)
    rd_resized.save(f'{f_path}/ic_launcher_round.png')
    print(f'Saved {folder} ({s}x{s})')

# Remove test icon
if os.path.exists(f'{res_dir}/drawable/test_icon.png'):
    os.remove(f'{res_dir}/drawable/test_icon.png')

print('ALL DONE!')

"""Draws the SelfDesk icon (blue tile with a monitor) as icon.ico for the .exe."""
import sys
from PIL import Image, ImageDraw

S = 256
img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
d = ImageDraw.Draw(img)
d.rounded_rectangle((8, 8, S - 8, S - 8), radius=56, fill=(47, 111, 237))
d.rounded_rectangle((48, 62, 208, 168), radius=12, fill="white")          # screen
d.rounded_rectangle((62, 76, 194, 154), radius=6, fill=(47, 111, 237))    # display area
d.polygon([(110, 104), (110, 140), (120, 130), (128, 146), (135, 143), (127, 127), (141, 127)],
          fill="white")                                                    # mouse pointer
d.rectangle((116, 168, 140, 192), fill="white")                           # stand
d.rounded_rectangle((86, 188, 170, 202), radius=6, fill="white")
img.save(sys.argv[1] if len(sys.argv) > 1 else "icon.ico",
         sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])

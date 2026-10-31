"""Turns viewer mouse/keyboard messages into real input on this PC (pynput)."""
import logging
import sys

log = logging.getLogger("selfdesk.input")

try:
    from pynput.keyboard import Controller as KController, Key, KeyCode
    from pynput.mouse import Button, Controller as MController
except Exception as e:  # e.g. no display / Wayland without permission
    log.warning("remote control disabled (view only): %s", e)
    KController = None

# Browser KeyboardEvent.code -> pynput Key attribute name
SPECIAL = {
    "Enter": "enter", "NumpadEnter": "enter", "Backspace": "backspace", "Tab": "tab",
    "Escape": "esc", "Space": "space", "ArrowUp": "up", "ArrowDown": "down",
    "ArrowLeft": "left", "ArrowRight": "right", "Delete": "delete", "Home": "home",
    "End": "end", "PageUp": "page_up", "PageDown": "page_down", "Insert": "insert",
    "ShiftLeft": "shift_l", "ShiftRight": "shift_r", "ControlLeft": "ctrl_l",
    "ControlRight": "ctrl_r", "AltLeft": "alt_l", "AltRight": "alt_r",
    "MetaLeft": "cmd_l", "MetaRight": "cmd_r", "OSLeft": "cmd_l", "OSRight": "cmd_r",
    "CapsLock": "caps_lock", "PrintScreen": "print_screen", "ContextMenu": "menu",
    "NumLock": "num_lock", "ScrollLock": "scroll_lock", "Pause": "pause",
    **{f"F{i}": f"f{i}" for i in range(1, 21)},
}
# Physical keys -> unshifted character (US layout); the remote PC applies its
# own Shift state, so Shift+Digit1 becomes "!" there.
PHYSICAL = {
    "Minus": "-", "Equal": "=", "BracketLeft": "[", "BracketRight": "]",
    "Backslash": "\\", "Semicolon": ";", "Quote": "'", "Backquote": "`",
    "Comma": ",", "Period": ".", "Slash": "/", "NumpadAdd": "+",
    "NumpadSubtract": "-", "NumpadMultiply": "*", "NumpadDivide": "/",
    "NumpadDecimal": ".",
}
BUTTONS = {0: "left", 1: "middle", 2: "right"}

# Character keys are sent as *physical* key presses (virtual key codes) so that
# shortcuts like Ctrl+C and the remote Shift/AltGr state behave like a real
# keyboard. Typing them as unicode text would break shortcuts.
_WIN_OEM = {"-": 0xBD, "=": 0xBB, "[": 0xDB, "]": 0xDD, "\\": 0xDC, ";": 0xBA,
            "'": 0xDE, "`": 0xC0, ",": 0xBC, ".": 0xBE, "/": 0xBF}
_MAC_VK = dict(zip("asdfhgzxcvbqweryt123465=97-80]ou[ip", [
    0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21,
    22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35]))
_MAC_VK.update({"l": 37, "j": 38, "'": 39, "k": 40, ";": 41, "\\": 42, ",": 43,
                "/": 44, "n": 45, "m": 46, ".": 47, "`": 50})


_US_SHIFTED = dict(zip('~!@#$%^&*()_+{}|:"<>?', "`1234567890-=[]\\;',./"))


def char_key(c):
    if sys.platform.startswith("linux"):
        return KeyCode.from_vk(ord(c))  # X keysym == code point for ASCII; sent via XTest
    if sys.platform == "win32":
        if c.isalnum() and c.isascii():
            return KeyCode.from_vk(ord(c.upper()))
        if c in _WIN_OEM:
            return KeyCode.from_vk(_WIN_OEM[c])
    if sys.platform == "darwin" and c in _MAC_VK:
        return KeyCode.from_vk(_MAC_VK[c])
    return KeyCode.from_char(c)


class InputController:
    def __init__(self):
        self.ok = KController is not None
        if self.ok:
            self.kb = KController()
            self.mouse = MController()
        self.pressed = set()
        self.buttons = set()

    def _key(self, code, key):
        if code in SPECIAL:
            k = getattr(Key, SPECIAL[code], None)
            if k is not None:
                return k
        if code.startswith("Key") and len(code) == 4:
            return char_key(code[3].lower())
        if code.startswith("Digit") and len(code) == 6:
            return char_key(code[5])
        if code.startswith("Numpad") and code[6:].isdigit():
            if sys.platform == "win32":
                return KeyCode.from_vk(0x60 + int(code[6:]))
            return KeyCode.from_char(code[6:])
        if code in PHYSICAL:
            return char_key(PHYSICAL[code])
        if isinstance(key, str) and len(key) == 1:
            return KeyCode.from_char(key)
        return None

    def handle(self, m, mon):
        if not self.ok:
            return
        t = m.get("type")
        if t == "mouse":
            x = mon["left"] + max(0.0, min(1.0, float(m.get("x", 0)))) * (mon["width"] - 1)
            y = mon["top"] + max(0.0, min(1.0, float(m.get("y", 0)))) * (mon["height"] - 1)
            self.mouse.position = (int(x), int(y))
            btn = getattr(Button, BUTTONS.get(m.get("button"), "left"))
            if m.get("action") == "down":
                self.mouse.press(btn)
                self.buttons.add(btn)
            elif m.get("action") == "up":
                self.mouse.release(btn)
                self.buttons.discard(btn)
        elif t == "wheel":
            self.mouse.scroll(int(m.get("dx", 0)), int(m.get("dy", 0)))
        elif t == "key":
            k = self._key(str(m.get("code", "")), m.get("key"))
            if k is None:
                return
            if m.get("action") == "down":
                self.kb.press(k)
                self.pressed.add(k)
            else:
                self.kb.release(k)
                self.pressed.discard(k)
        elif t == "text":
            self.type_text(str(m.get("text", ""))[:10000])
        elif t == "combo" and m.get("name") == "ctrl_alt_del":
            # Real Ctrl+Alt+Del is blocked by Windows for normal programs; this
            # opens Task Manager instead, which is what people usually want.
            with self.kb.pressed(Key.ctrl, Key.shift):
                self.kb.tap(Key.esc)

    def type_text(self, text):
        if not sys.platform.startswith("linux"):
            self.kb.type(text)
            return
        # Many X apps ignore pynput's synthetic text, so press real keys via XTest.
        for c in text:
            if c == "\n":
                self.kb.tap(Key.enter)
            elif c == "\t":
                self.kb.tap(Key.tab)
            elif c in _US_SHIFTED or (c.isascii() and c.isupper()):
                with self.kb.pressed(Key.shift):
                    self.kb.tap(char_key(_US_SHIFTED.get(c, c.lower())))
            elif c.isascii() and c.isprintable():
                self.kb.tap(Key.space if c == " " else char_key(c))
            else:
                self.kb.type(c)

    def release_all(self):
        if not self.ok:
            return
        for k in list(self.pressed):
            try:
                self.kb.release(k)
            except Exception:
                pass
        for b in list(self.buttons):
            try:
                self.mouse.release(b)
            except Exception:
                pass
        self.pressed.clear()
        self.buttons.clear()

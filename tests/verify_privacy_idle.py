"""停止录音/相机后，检查系统隐私状态与圆点容器均已清理。"""
import re
import sys
from pathlib import Path

dump = Path(sys.argv[1]).read_text(encoding="utf-8")
assert "Has persistent privacy dot: false" in dump, "系统仍有隐私事件"
assert "Animation state: Idle" in dump, "隐私动画尚未空闲"
corners = re.findall(r"child\[\d+\]=[^\n]*\{[0-9a-f]+ ([VIG])[^\n]*privacy_dot_(?:top|bottom)_(?:left|right)_container", dump)
assert corners, "没有找到隐私圆点容器"
assert "V" not in corners, "系统已空闲，但仍有可见的隐私圆点容器"
print("PASS: 隐私状态空闲，圆点容器无残留")

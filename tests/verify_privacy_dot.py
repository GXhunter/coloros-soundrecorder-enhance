"""核对录音进行中启用/关闭全局绿点隐藏时的 SystemUI dump。"""
import re
import sys
from pathlib import Path

for filename, hidden in zip(sys.argv[1:], (True, False), strict=True):
    dump = Path(filename).read_text(encoding="utf-8")
    corners = re.findall(r"child\[\d+\]=[^\n]*\{[0-9a-f]+ ([VIG])[^\n]*privacy_dot_(?:top|bottom)_(?:left|right)_container", dump)
    assert corners, "未找到系统隐私圆点容器"
    assert ("V" not in corners) == hidden, "隐私圆点可见性不符合开关状态"
print("PASS: 开启时圆点隐藏，关闭时圆点可见")

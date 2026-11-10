"""检查从实机更多菜单采集的标题 JSON 数组。"""
import json
import sys
from pathlib import Path

titles = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
assert titles.count("录音增强") == 1, "录音增强入口缺失或重复"
assert all(title in titles for title in ("编辑", "听筒播放", "设置")), "原生首页菜单项缺失"
print("PASS: 录音增强入口唯一，原生菜单项保留")

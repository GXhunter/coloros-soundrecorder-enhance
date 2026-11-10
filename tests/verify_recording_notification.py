"""检查录音运行期间保存的 adb shell dumpsys notification 输出。"""
import re
import sys
from pathlib import Path

dump = Path(sys.argv[1]).read_text(encoding="utf-8")
record = re.search(
    r"NotificationRecord[^\n]*pkg=com\.coloros\.soundrecorder[^\n]*id=1024\b[^\n]*",
    dump,
)
assert record, "没有找到正在发布的录音通知 1024"
notification = record.group()
assert "vis=SECRET" in notification, "录音通知仍可能在锁屏显示"
assert "FOREGROUND_SERVICE" in notification, "录音前台服务通知丢失"
actions = re.search(r"actions=(\d+)", notification)
assert actions and int(actions.group(1)) >= 2, "通知操作按钮丢失"
print("PASS: 录音通知锁屏隐藏，保留前台服务和操作按钮")

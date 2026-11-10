"""核对同一台设备开启/关闭隐藏开关时的录音通知快照。"""
import re
import sys
from pathlib import Path

for filename, hidden in zip(sys.argv[1:], (True, False), strict=True):
    dump = Path(filename).read_text(encoding="utf-8")
    record = re.search(r"NotificationRecord[^\n]*pkg=com\.coloros\.soundrecorder[^\n]*id=1024\b[^\n]*", dump)
    channel = re.search(r"effectiveNotificationChannel=NotificationChannel\{mId='RecorderService_new_Channel_id'[^\n]*", dump)
    assert record and channel, "缺少录音通知或有效渠道"
    assert "actions=3" in record.group() and "FOREGROUND_SERVICE" in record.group()
    assert f"vis={'SECRET' if hidden else 'PUBLIC'}" in record.group()
    assert f"mShowIcon={str(not hidden).lower()}" in channel.group()
    assert f"mUnimportant={str(hidden).lower()}" in channel.group()
    assert f"mLockscreenVisibility={-1 if hidden else 1}," in channel.group()
print("PASS: 两种显示配置均保留录音通知及三个按钮，图标和锁屏属性正确")

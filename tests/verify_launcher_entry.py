"""确保隐藏桌面 alias 不会关闭 LSPosed 的设置 Activity。"""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
manifest = ET.parse(root / "app/src/main/AndroidManifest.xml").getroot()
name = "{http://schemas.android.com/apk/res/android}name"
target = "{http://schemas.android.com/apk/res/android}targetActivity"
exported = "{http://schemas.android.com/apk/res/android}exported"
activities = manifest.findall("application/activity")
settings = next(item for item in activities if item.get(name) == ".SettingsActivity")
categories = {item.get(name) for item in settings.findall("intent-filter/category")}
assert "android.intent.category.LAUNCHER" not in categories
assert "de.robv.android.xposed.category.MODULE_SETTINGS" in categories
assert settings.get(exported) == "true"
alias = manifest.find("application/activity-alias")
assert alias is not None and alias.get(name) == ".Launcher"
assert alias.get(target) == ".SettingsActivity"
assert alias.find("intent-filter/category").get(name) == "android.intent.category.LAUNCHER"
print("PASS: 桌面入口与 LSPosed 设置入口分离")

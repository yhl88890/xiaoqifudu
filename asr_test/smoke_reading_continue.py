#!/usr/bin/env python3
# 冒烟：视频模式开 vshort 播几秒 -> 进阅读模式 -> 查崩溃 + 阅读页出现
import subprocess, re, time, sys, os

PKG = "com.xuanji.repeater"
APK_DIR = os.path.dirname(os.path.abspath(__file__))

def adb(args, timeout=30):
    return subprocess.run(["adb"] + args, capture_output=True, text=True, timeout=timeout)

def dump_ui():
    adb(["shell", "uiautomator", "dump", "/sdcard/ui.xml"])
    p = adb(["pull", "/sdcard/ui.xml", os.path.join(APK_DIR, "ui.xml")])
    if p.returncode != 0:
        return ""
    with open(os.path.join(APK_DIR, "ui.xml"), "r", encoding="utf-8", errors="ignore") as f:
        return f.read()

def nodes(xml):
    out = []
    for m in re.finditer(r"<node([^>]*)/>", xml):
        attrs = dict(re.findall(r'(\w+)="([^"]*)"', m.group(1)))
        out.append(attrs)
    return out

def tap_text(target, xml=None, timeout=8):
    if xml is None:
        xml = dump_ui()
    t = target
    cand = None
    for n in nodes(xml):
        txt = n.get("text", "") or ""
        cd = n.get("content-desc", "") or ""
        if txt == t or cd == t:
            cand = n; break
    if cand is None:
        # 子串兜底
        for n in nodes(xml):
            txt = n.get("text", "") or ""
            cd = n.get("content-desc", "") or ""
            if t in txt or t in cd:
                cand = n; break
    if cand is None:
        print(f"[WARN] 找不到可点目标: {t}")
        return False
    b = cand.get("bounds", "")
    m = re.search(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", b)
    if not m:
        return False
    x0, y0, x1, y1 = map(int, m.groups())
    cx, cy = (x0 + x1) // 2, (y0 + y1) // 2
    adb(["shell", "input", "tap", str(cx), str(cy)])
    print(f"[TAP] {t} @ ({cx},{cy})")
    return True

def has_text(target, xml):
    return target in xml

def screencap(name):
    adb(["exec-out", "screencap", "-p", os.path.join(APK_DIR, name)])

def main():
    # 冷启动
    adb(["shell", "am", "force-stop", PKG])
    time.sleep(1)
    adb(["shell", "am", "start", "-n", f"{PKG}/.MainActivity"])
    time.sleep(3)
    xml = dump_ui()
    print("[LAUNCH] 当前页是否含底部标签:", "阅读" in xml or "音频" in xml)

    # 进视频模式
    if not tap_text("视频", xml):
        print("[FAIL] 无法进入视频模式"); sys.exit(2)
    time.sleep(2)

    # 打开文件夹
    xml = dump_ui()
    if not tap_text("文件夹", xml):
        print("[WARN] 没找到文件夹图标，尝试直接找 Download")
    time.sleep(2)

    # 进入 Download
    xml = dump_ui()
    if not tap_text("Download", xml):
        print("[WARN] 没找到 Download")
    time.sleep(2)

    # 选 vshort
    xml = dump_ui()
    if not tap_text("vshort", xml):
        print("[WARN] 没找到 vshort，列出现有文本:")
        for n in nodes(xml):
            if (n.get("text","") or "").strip():
                print("   text:", n.get("text"))
    time.sleep(2)

    # 播放 4 秒让 currentPos 前进（视频打开后默认 videoPlaying=true 会自动播）
    print("[PLAY] 等待 4s 让进度前进")
    time.sleep(4)
    xml = dump_ui()
    print("[VIDEO] 视频页文本含'阅读'?", "阅读" in xml)

    # 进阅读模式
    if not tap_text("阅读", xml):
        print("[FAIL] 无法进阅读模式"); sys.exit(3)
    time.sleep(3)

    xml = dump_ui()
    ok = has_text("阅读模式", xml)
    print("[READING] 阅读页出现:", ok)
    screencap("reading_continue.png")

    # 查崩溃
    log = adb(["logcat", "-d", "-t", "300"]).stdout
    crash = [l for l in log.splitlines() if ("FATAL EXCEPTION" in l or "AndroidRuntime" in l or "XJ_CRASH" in l)]
    if crash:
        print("[CRASH] 检测到崩溃:")
        for l in crash[:10]:
            print("   ", l)
        sys.exit(4)
    print("[OK] 无崩溃，阅读模式可达")
    sys.exit(0 if ok else 5)

if __name__ == "__main__":
    main()

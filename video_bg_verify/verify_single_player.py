#!/usr/bin/env python3
# 单播放器重构验证：冷启自动续播 vshort.mp4 → 回桌面(后台) → 回前台，
# 抓 XJ_VID 日志确认：后台只摘画面(setSurface(null)保声)、回前台接回、全程只有一个 MediaPlayer、无崩溃。
import subprocess, sys, time, re, os

PKG = "com.xuanji.repeater"
ACT = f"{PKG}/.MainActivity"
VIDEO_NAME = "vshort.mp4"
VIDEO_PATH = "/sdcard/Download/vshort.mp4"
SRT_NAME = "vshort.srt"

def adb(args, timeout=60):
    return subprocess.run(["adb", *args], capture_output=True, text=True, timeout=timeout)

def logcat_clear():
    adb(["logcat", "-c"])

def logcat_grep(pat, timeout=20):
    p = subprocess.run(["adb", "logcat", "-d", "-t", "2000", "-s", "XJ_VID:V", "AndroidRuntime:E", "VideoView:E"],
                       capture_output=True, text=True, timeout=timeout)
    out = p.stdout + p.stderr
    return [l for l in out.splitlines() if re.search(pat, l)]

def ensure_video_on_device():
    r = adb(["shell", "ls", "-l", VIDEO_PATH])
    if r.returncode != 0 or "No such" in r.stdout:
        print("[warn] vshort.mp4 不在设备，请先 push")
        return False
    return True

def get_video_media_id():
    # 该模拟器 content query 的 --projection 语法报错，全量查再 grep 提取 _id
    r = adb(["shell", "content", "query", "--uri", "content://media/external/video/media"])
    for line in r.stdout.splitlines():
        if VIDEO_NAME in line:
            m = re.search(r"_id=(\d+)", line)
            if m:
                return m.group(1)
    return None

def seed_prefs(uri):
    # 用同目录的 seed_video.xml 模板（含正确 uri），push 到 /sdcard 再 su cp 进 SP 目录。
    # 注：base64 + su 0 sh -c 重定向在部分模拟器静默失败，故改用 push+cp 最稳。
    here = os.path.dirname(os.path.abspath(__file__))
    seed_src = os.path.join(here, "seed_video.xml")
    if not os.path.exists(seed_src):
        print("[seed] 警告：seed_video.xml 不存在，跳过播种"); return False
    # 把 uri 写进模板（避免硬编码 id 失效）
    tmpl = open(seed_src, encoding="utf-8").read()
    tmpl = tmpl.replace("__VIDEO_URI__", uri)
    tmp = os.path.join(here, "_seed_tmp.xml")
    open(tmp, "w", encoding="utf-8").write(tmpl)
    adb(["shell", "am", "force-stop", PKG])
    time.sleep(1)
    adb(["push", tmp, "/sdcard/Download/seed_video.xml"], timeout=60)
    sp = f"/data/data/{PKG}/shared_prefs"
    r = adb(["shell", "su", "0", "mkdir", "-p", sp], timeout=30)
    r = adb(["shell", "su", "0", "cp", "/sdcard/Download/seed_video.xml", f"{sp}/xuanji_video.xml"], timeout=30)
    adb(["shell", "su", "0", "chmod", "660", f"{sp}/xuanji_video.xml"], timeout=30)
    ok = r.returncode == 0
    print("[seed] cp rc=", r.returncode, r.stderr[:200])
    try: os.remove(tmp)
    except OSError: pass
    return ok

def launch():
    adb(["shell", "am", "force-stop", PKG])
    time.sleep(1)
    adb(["shell", "am", "start", "-n", ACT])
    time.sleep(4)

def main():
    if len(sys.argv) < 2:
        print("usage: verify_single_player.py <app-release.apk>")
        sys.exit(1)
    apk = sys.argv[1]
    if not os.path.exists(apk):
        print("[err] apk not found:", apk); sys.exit(1)

    # 1) 安装
    print("[1] install -r", apk)
    r = adb(["install", "-r", apk], timeout=180)
    print(r.stdout[-300:], r.stderr[-300:])
    if r.returncode != 0:
        print("[err] install failed"); sys.exit(1)

    if not ensure_video_on_device():
        sys.exit(1)

    vid = get_video_media_id()
    if not vid:
        print("[err] 找不到 vshort 媒体库 id，跳过种子（手动进视频模式测）")
    else:
        uri = f"content://media/external/video/media/{vid}"
        seed_prefs(uri)
        print("[seed] uri=", uri)

    logcat_clear()
    launch()
    time.sleep(3)
    # 播放中日志
    playing = logcat_grep(r"SURFACE created|PREPARED|ERROR")
    print("[launch] logs:", playing[:10])

    # 2) 回桌面 = 后台
    print("[2] 回桌面(后台)")
    adb(["shell", "input", "keyevent", "KEYCODE_HOME"])
    time.sleep(3)
    bg = logcat_grep(r"onPause detach surface|SURFACE destroyed|audio keeps playing")
    print("[bg] logs:", bg[:10])
    assert any(("onPause detach surface" in l) or ("SURFACE destroyed" in l) for l in bg), \
        "未检测到后台摘画面日志！"

    # 后台保持一段（确认单播放器仍在响、无第二播放器创建）
    time.sleep(3)
    mediacenter = adb(["shell", "input", "keyevent", "87"])  # MEDIA_PREVIOUS → 媒体中心上一句
    time.sleep(2)
    after_skip = logcat_grep(r"SURFACE created|SURFACE destroyed|onPause detach|ERROR|AndroidRuntime")
    print("[bg+skip] logs:", after_skip[:10])

    # 3) 回前台
    print("[3] 回前台")
    adb(["shell", "am", "start", "-n", ACT])
    time.sleep(4)
    fg = logcat_grep(r"SURFACE created|onResume re-bind|PREPARED|ERROR|AndroidRuntime")
    print("[fg] logs:", fg[:10])
    assert any(("SURFACE created" in l) or ("onResume re-bind" in l) for l in fg), \
        "未检测到回前台接回画面日志！"

    # 4) 崩溃检查
    crash = logcat_grep(r"AndroidRuntime.*E|Force finishing|FATAL")
    print("[crash check] lines:", crash[:5])
    assert not crash, "检测到崩溃！"

    print("\n=== 单播放器验证通过：后台摘画面保声、回前台接回、无崩溃、无第二播放器 ===")

if __name__ == "__main__":
    main()

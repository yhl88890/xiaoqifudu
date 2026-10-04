# -*- coding: utf-8 -*-
"""把 xuanji-app-fixed 的 Kotlin 源码按功能分组生成可在线阅读的 markdown 文档。"""
import os
import datetime

SRC = r"C:\Users\yhl888\Desktop\xiaoqifudu\xuanji-devkit\xuanji-app-fixed"
KT = os.path.join(SRC, "app", "src", "main", "java", "com", "xuanji", "repeater")
OUT = r"C:\Users\yhl888\Desktop\xiaoqifudu\.workbuddy\memory\out"
os.makedirs(OUT, exist_ok=True)

# (输出文件名, 标题, 说明, [源文件...])
GROUPS = [
    ("01-入口与主控", "入口与主控", "Activity 骨架、页面路由、配置持久化。改功能先看这里。", [
        "MainActivity.kt", "AppPages.kt", "XuanjiApp.kt", "AppRoot.kt",
    ]),
    ("02-播放器与复读引擎", "播放器与复读引擎", "后台播放服务、复读状态机、播放页。这是 App 的心脏。", [
        "PlayerService.kt", "PlayerPage.kt", "PlaybackSession.kt", "RepeatPrefs.kt",
        "AudioSlice.kt", "AudioItem.kt", "AudioScanner.kt", "PlaybackPrefs.kt",
    ]),
    ("03-视频模式与B站", "视频模式与 B站 抓取", "视频页、TextureView 播放、B站 登录/抓取/字幕轨。", [
        "VideoPage.kt", "BiliClient.kt", "BiliImport.kt", "BiliLogin.kt",
        "BiliImportBus.kt", "BiliImportService.kt", "VideoScanner.kt", "VideoItem.kt",
    ]),
    ("04-语音识别与字幕", "语音识别与字幕", "ASR 断句、对齐、字幕解析与字幕库。", [
        "SpeechRecognizer.kt", "SentenceSplitter.kt", "AsrTextAligner.kt",
        "SrtParser.kt", "LrcParser.kt", "SubtitleLibrary.kt", "LongSentenceSplit.kt",
        "TimelineRecut.kt", "AsrPrefs.kt", "WavConverter.kt", "VideoAudioExtractor.kt",
    ]),
    ("05-阅读生词与复习", "阅读 / 生词本 / 复习", "阅读页、生词本、间隔重复复习、提醒。这条线是学习闭环。", [
        "ReadingPage.kt", "VocabPage.kt", "ReviewPage.kt", "VocabStore.kt",
        "VocabReminder.kt", "ReviewScheduler.kt", "Pronounce.kt",
    ]),
    ("06-设置与基础设施", "设置与基础设施", "设置页、悬浮窗、数据备份、崩溃兜底、文件操作。", [
        "SettingsPage.kt", "FolderPage.kt", "FolderSort.kt", "DataBackup.kt",
        "CrashGuard.kt", "FileOps.kt", "FloatLyricsService.kt", "IgnorePrefs.kt",
        "FloatPrefs.kt", "TtsManager.kt", "XuanjiIcons.kt", "XuanjiTheme.kt",
    ]),
]


def read(p):
    with open(p, "rb") as f:
        return f.read().decode("utf-8", errors="replace")


def lang_of(name):
    return {".kt": "kotlin", ".kts": "kotlin", ".xml": "xml", ".pro": "text"}.get(
        os.path.splitext(name)[1], "text")


def build():
    all_files = set(f for f in os.listdir(KT) if f.endswith(".kt"))
    used = set()
    made = []
    for idx, (fname, title, desc, files) in enumerate(GROUPS, 1):
        real = [f for f in files if f in all_files]
        used.update(real)
        parts = [
            f"# 小七复读机 · 源码 {title}（{len(real)} 个文件）",
            "",
            f"> {desc}",
            f"> 快照时间：{datetime.datetime.now():%Y-%m-%d %H:%M}　·　对应版本：**v88**（`1.2.88`）",
            "",
            "本文件由 `tools/dump_source_docs.py` 自动生成，勿手改。",
            "完整可编译工程请下载同目录的 `xuanji-src.zip`（含 git 历史）。",
            "",
        ]
        for i, f in enumerate(real, 1):
            p = os.path.join(KT, f)
            body = read(p)
            parts.append(f"## {i}. `{f}`　（{len(body.splitlines())} 行）")
            parts.append("")
            parts.append("```" + lang_of(f))
            parts.append(body.rstrip())
            parts.append("```")
            parts.append("")
        outp = os.path.join(OUT, f"xuanji-src-{fname}.md")
        with open(outp, "w", encoding="utf-8", newline="\n") as fh:
            fh.write("\n".join(parts))
        made.append((os.path.basename(outp), os.path.getsize(outp), len(real)))

    # 漏网之鱼单独兜底
    rest = sorted(all_files - used)
    if rest:
        parts = [
            "# 小七复读机 · 源码 其他零散文件",
            "",
            f"> 未归入上面 6 组的部分，共 {len(rest)} 个。",
            "",
        ]
        for f in rest:
            body = read(os.path.join(KT, f))
            parts.append(f"## `{f}`　（{len(body.splitlines())} 行）")
            parts.append("")
            parts.append("```kotlin")
            parts.append(body.rstrip())
            parts.append("```")
            parts.append("")
        outp = os.path.join(OUT, "xuanji-src-99-其他零散.md")
        with open(outp, "w", encoding="utf-8", newline="\n") as fh:
            fh.write("\n".join(parts))
        made.append((os.path.basename(outp), os.path.getsize(outp), len(rest)))

    return made, rest


if __name__ == "__main__":
    made, rest = build()
    for n, s, c in made:
        print("%-42s %7.1f KB  %2d 文件" % (n, s / 1024, c))
    print("未分组零散文件:", rest)

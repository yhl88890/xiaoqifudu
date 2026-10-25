#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check_memory_gaps.py —— 查「日志里记了、但长期索引里没有」的条目

## 为什么要这个工具（1016 阿亮发现的事故）

v97「长音频沿停顿分块识别」（`BLOCK_TARGET_MS = 180_000`，治模型文本/时间错位）
是花了很长时间专门改的重要一项，**只在 `2026-10-05.md` 的日志里写过，
从没进过 `MEMORY.md` / `HANDBOOK.md`**。

而每次会话开头**自动注入的只有 `MEMORY.md`**（12KB 索引），
几十 KB 的历史日志**不会**自动进上下文 ⇒ 这个设计对未来的我完全不可见
⇒ 1016 我看到「第 6/8 块」就误判成 bug、还跑去报"独立问题"。

## 原理

日志里的「关键标识」（反引号里的常量名 / 函数名 / 文件名 / 开关名）
如果**在 `MEMORY.md` 和 `HANDBOOK.md` 里一次都没出现** ⇒ 很可能是漏晋级的条目。
按「日志出现次数」排序，次数多的优先人工确认。

噪声控制：只报出现 >= 2 次的；纯中文/纯小写单词的标识自动跳过。

## 用法

    python tools/check_memory_gaps.py            # 默认列 top 40
    python tools/check_memory_gaps.py --top 80
"""
import re
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]          # 项目根（xiaoqifudu）
MEM_DIR = ROOT / ".workbuddy" / "memory"
INDEX_FILES = [MEM_DIR / "MEMORY.md", MEM_DIR / "HANDBOOK.md"]

# 日志 = 目录下的 YYYY-MM-DD.md（不含 archive/ out/ automations/）
LOG_FILES = sorted(MEM_DIR.glob("[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9].md"))

TICK = re.compile(r"`([^`\n]{2,60})`")

# 像"代码标识"的：含下划线 / 驼峰 / 点分文件名 / 全大写下划线
IDENT = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*$")
CAMEL = re.compile(r"[a-z][A-Z]")
FILEISH = re.compile(r"\.(kt|java|py|md|json|xml|apk|srt|wav|mp3|mkv|mp4|js|kts)$", re.I)
# 明显是通用词/包裹物的，跳过（不然噪音爆炸）
NOISE = {
    "true", "false", "null", "string", "int", "long", "float", "boolean",
    "val", "var", "fun", "object", "class", "enum", "data", "return",
    "it", "this", "when", "if", "else", "for", "while", "in", "is", "as",
    "grep", "adb", "shell", "su", "git", "commit", "tag", "apk", "sdk",
}
# 通用词尾巴（复合名里出现就降权）
GENERIC = ("Log", "TAG", "Context", "Intent", "Activity", "View")


def looks_like_ident(s: str) -> bool:
    s = s.strip()
    if not s or s in NOISE or len(s) < 4:
        return False
    if " " in s or s.startswith("-") or s.startswith("/") or "=" in s:
        return False
    if FILEISH.search(s):
        return True
    if not IDENT.match(s):
        return False
    # 全大写下划线 / 含下划线 / 驼峰，才算"代码标识"
    return ("_" in s) or CAMEL.search(s) is not None or s.isupper()


def main() -> int:
    top = 40
    if "--top" in sys.argv:
        top = int(sys.argv[sys.argv.index("--top") + 1])

    if not MEM_DIR.is_dir():
        print(f"[x] 找不到记忆目录：{MEM_DIR}")
        return 1
    if not LOG_FILES:
        print(f"[x] 没找到任何日志（{MEM_DIR}/YYYY-MM-DD.md）")
        return 1

    index_text = ""
    for p in INDEX_FILES:
        if p.exists():
            index_text += p.read_text(encoding="utf-8", errors="ignore")

    # 标识 -> (出现在几个日志文件, 总次数)
    files: dict[str, set] = defaultdict(set)
    count: dict[str, int] = defaultdict(int)

    for lp in LOG_FILES:
        txt = lp.read_text(encoding="utf-8", errors="ignore")
        seen_here = set()
        for raw in TICK.findall(txt):
            s = raw.strip()
            if not looks_like_ident(s):
                continue
            count[s] += 1
            seen_here.add(s)
        for s in seen_here:
            files[s].add(lp.name)

    missing = [
        (s, len(files[s]), count[s])
        for s in count
        if count[s] >= 2 and s not in index_text
    ]
    missing.sort(key=lambda t: (-t[1], -t[2], t[0]))

    print(f"日志文件 {len(LOG_FILES)} 个 · 提取到候选标识 {len(count)} 个")
    print(f"其中【日志里有、MEMORY/HANDBOOK 里一次都没出现】的：{len(missing)} 个")
    print("=" * 72)
    print(f"{'标识':<44}{'日志文件数':>8}{'出现次数':>8}")
    print("-" * 72)
    for s, nf, n in missing[:top]:
        flag = "🔴" if nf >= 3 else "  "
        print(f"{flag}{s:<42}{nf:>8}{n:>8}")
    if len(missing) > top:
        print(f"... 还有 {len(missing) - top} 个（--top N 看更多）")
    print("=" * 72)
    print("🔴 = 横跨 >=3 个日志文件却从没进索引 ⇒ 优先人工确认要不要补进 HANDBOOK/MEMORY")
    return 0


if __name__ == "__main__":
    sys.exit(main())

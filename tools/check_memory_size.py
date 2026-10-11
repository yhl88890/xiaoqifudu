#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
记忆文件体积校验 —— 防止 MEMORY.md 再次膨胀成"每轮都要压缩"的老problem。

背景（2026-10-11 阿亮提的问题）：
    重构前 MEMORY.md 顶到 30KB 上限，每轮对话末尾都要"压缩半天"，
    而且越压越慢 —— 根本原因是「长期纪律」和「某次过程细节」混在一个文件里。

重构后的分层：
    MEMORY.md      一句话铁律 + 去向指针      上限 12KB   ← 本脚本重点盯它
    HANDBOOK.md    技术细节 / 专题 / 脚本      不设上限
    YYYY-MM-DD.md  当日流水                   不设上限
    archive/       归档快照（归档 != 删除）    不设上限

用法：
    python tools/check_memory_size.py            # 检查，超限返回码 1
    python tools/check_memory_size.py --quiet    # 只在不合格时输出
"""
import os
import sys
import glob

# ── 配置 ──────────────────────────────────────────────────────────
MEMORY_SOFT_LIMIT = 12 * 1024   # 12KB：软上限，超过就"下放"而不是"压缩"
MEMORY_HARD_LIMIT = 20 * 1024   # 20KB：硬上限，必须先下放才能继续写

# 记忆目录（本脚本在 <仓库根>/xuanji-devkit/xuanji-app-fixed/tools/ 下）
# xuanji-app-fixed/tools/  →  ../../../.workbuddy/memory
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
MEM_DIR = os.path.normpath(os.path.join(SCRIPT_DIR, "..", "..", "..", ".workbuddy", "memory"))

QUIET = "--quiet" in sys.argv


def sizeof(path):
    try:
        return os.path.getsize(path)
    except OSError:
        return None


def human(n):
    return "%.1fKB" % (n / 1024.0)


def main():
    if not os.path.isdir(MEM_DIR):
        print("!! 找不到记忆目录: %s" % MEM_DIR)
        return 2

    lines = []
    problems = []

    # ── 1. MEMORY.md：重点盯 ──────────────────────────────────────
    memory = os.path.join(MEM_DIR, "MEMORY.md")
    n = sizeof(memory)
    if n is None:
        problems.append("MEMORY.md 不存在")
        lines.append("MEMORY.md    -- 缺失")
    else:
        pct = n * 100.0 / MEMORY_SOFT_LIMIT
        if n > MEMORY_HARD_LIMIT:
            mark = "!! 硬超限"
            problems.append("MEMORY.md %s 超过硬上限 %s —— 必须下放细节到 HANDBOOK"
                            % (human(n), human(MEMORY_HARD_LIMIT)))
        elif n > MEMORY_SOFT_LIMIT:
            mark = "!  软超限"
            problems.append("MEMORY.md %s 超过软上限 %s —— 建议把细节下放 HANDBOOK"
                            % (human(n), human(MEMORY_SOFT_LIMIT)))
        else:
            mark = "OK"
        lines.append("MEMORY.md    %-8s  %s (软上限 %s 的 %.0f%%)"
                     % (human(n), mark, human(MEMORY_SOFT_LIMIT), pct))

    # ── 2. HANDBOOK.md：只报告，不限制 ────────────────────────────
    hb = os.path.join(MEM_DIR, "HANDBOOK.md")
    n = sizeof(hb)
    lines.append("HANDBOOK.md  %-8s  （无上限，它就是该长的）"
                 % (human(n) if n else "-- 缺失"))

    # ── 3. 日流水：报告总量 + 找出超 30 天该归档的 ─────────────────
    logs = sorted(glob.glob(os.path.join(MEM_DIR, "20*.md")))
    total = sum(sizeof(p) or 0 for p in logs)
    lines.append("日流水 x%-3d   %-8s  （%d 个文件）" % (len(logs), human(total), len(logs)))

    # ── 4. archive 目录计数 ───────────────────────────────────────
    arc = os.path.join(MEM_DIR, "archive")
    arc_n = len(os.listdir(arc)) if os.path.isdir(arc) else 0
    lines.append("archive/     %d 个归档文件" % arc_n)

    if not QUIET or problems:
        print("=== 记忆文件体检 ===")
        for l in lines:
            print("  " + l)
        if problems:
            print()
            for p in problems:
                print("  >> " + p)
        else:
            print("\n  ✅ 全部在限内。")

    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())

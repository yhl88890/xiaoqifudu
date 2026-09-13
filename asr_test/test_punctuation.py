#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
实测：硅基流动 TeleSpeechASR 加 punctuation 参数能否强制标点。
完全复刻 app 的 SpeechRecognizer.postTranscription 请求（multipart + 同字段），
对一个 WAV 跑两次：A) 不加 punctuation  B) 加 punctuation=True，
逐句打印 segment.text 并标注末尾标点，肉眼对比。

用法：
  python test_punctuation.py <硅基流动API Key> [wav路径]
  # 或
  set SILICONFLOW_KEY=xxx && python test_punctuation.py
"""
import os
import sys
import json
import uuid
import http.client
import urllib.request
import urllib.error

ENDPOINT = "https://api.siliconflow.cn/v1/audio/transcriptions"
MODEL = "TeleAI/TeleSpeechASR"
WAV_DEFAULT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "vshort_16k.wav")


def build_multipart(audio_path, model, punctuation):
    boundary = "----XuanjiBoundary" + uuid.uuid4().hex
    body = bytearray()
    # 与 app 顺序一致：model -> response_format -> timestamp_granularities[] -> language -> file
    def part(name, value):
        body.extend(f"--{boundary}\r\n".encode())
        body.extend(f'Content-Disposition: form-data; name="{name}"\r\n\r\n'.encode())
        body.extend(f"{value}\r\n".encode())

    part("model", model)
    part("response_format", "verbose_json")
    part("timestamp_granularities[]", "segment")
    part("language", "en")
    if punctuation is not None:
        part("punctuation", punctuation)
    # file
    with open(audio_path, "rb") as f:
        data = f.read()
    body.extend(f"--{boundary}\r\n".encode())
    body.extend(
        f'Content-Disposition: form-data; name="file"; filename="{os.path.basename(audio_path)}"\r\n'.encode()
    )
    body.extend(b"Content-Type: application/octet-stream\r\n\r\n")
    body.extend(data)
    body.extend(b"\r\n")
    body.extend(f"--{boundary}--\r\n".encode())
    return boundary, bytes(body)


def call(key, audio_path, punctuation):
    boundary, body = build_multipart(audio_path, MODEL, punctuation)
    req = urllib.request.Request(ENDPOINT, data=body, method="POST")
    req.add_header("Authorization", f"Bearer {key}")
    req.add_header("Content-Type", f"multipart/form-data; boundary={boundary}")
    try:
        with urllib.request.urlopen(req, timeout=240) as resp:
            return resp.status, resp.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        try:
            body = e.read().decode("utf-8", "replace")
        except Exception:
            body = ""
        return e.code, body
    except (urllib.error.URLError, OSError) as e:
        return 0, f"网络/连接错误: {e}"


def has_terminal_punct(t):
    t = t.rstrip()
    if not t:
        return False
    return t[-1] in ".!?…,;:"


def show(title, status, raw):
    print("=" * 60)
    print(f"{title}  (HTTP {status})")
    print("=" * 60)
    if status not in (200, 201, 202):
        print("请求失败，原始返回：")
        print(raw[:2000])
        return None
    try:
        root = json.loads(raw)
    except Exception as e:
        print("JSON 解析失败：", e)
        print(raw[:2000])
        return None
    segs = root.get("segments")
    if segs:
        print(f"返回 {len(segs)} 个 segment：")
        for i, s in enumerate(segs):
            txt = s.get("text", "")
            # 剥 <|...|> 标签（和 app cleanText 一致）
            import re
            txt = re.sub(r"<\|[^|]*\|>", "", txt).strip()
            mark = "✓标点" if has_terminal_punct(txt) else "✗无尾标点"
            print(f"  [{i:02d}] ({mark}) {txt}")
        return [re.sub(r"<\|[^|]*\|>", "", s.get("text", "")).strip() for s in segs]
    text = root.get("text", "")
    print("无 segments，纯文本：", repr(text[:1000]))
    return None


def main():
    key = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("SILICONFLOW_KEY")
    if not key:
        print("缺少硅基流动 Key：python test_punctuation.py <Key> [wav]")
        sys.exit(2)
    wav = sys.argv[2] if len(sys.argv) > 2 else WAV_DEFAULT
    if not os.path.exists(wav):
        print("WAV 不存在：", wav)
        sys.exit(2)

    print(f"音频：{wav}  ({os.path.getsize(wav)/1024:.0f} KB)\n")

    s1, r1 = call(key, wav, None)
    a = show("A) 默认（不加 punctuation）", s1, r1)

    print()
    s2, r2 = call(key, wav, "True")
    b = show("B) 加 punctuation=True", s2, r2)

    print("\n" + "=" * 60)
    print("结论对照")
    print("=" * 60)
    if a is not None and b is not None:
        pa = sum(1 for t in a if has_terminal_punct(t))
        pb = sum(1 for t in b if has_terminal_punct(t))
        print(f"A 默认：{pa}/{len(a)} 句有末尾标点")
        print(f"B 强制：{pb}/{len(b)} 句有末尾标点")
        if pb > pa:
            print("=> punctuation=True 能补出更多标点，可用于按标点断句。")
        elif pb == len(b) and pa < len(b):
            print("=> 强制后几乎全部带标点，效果明显。")
        elif pb == pa:
            print("=> 两种返回标点数量一致，punctuation 未改变行为（可能该模型标点已默认开启，或该参数对此模型无效）。")


if __name__ == "__main__":
    main()

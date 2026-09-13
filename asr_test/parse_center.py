import re, sys

def center(xml_path, label):
    data = open(xml_path, encoding='utf-8', errors='ignore').read()
    pat = r'<node([^>]*text="%s"[^>]*)>' % re.escape(label)
    m = re.search(pat, data)
    if not m:
        print("0,0")
        return
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', m.group(1))
    if not b:
        print("0,0")
        return
    x = (int(b.group(1)) + int(b.group(3))) // 2
    y = (int(b.group(2)) + int(b.group(4))) // 2
    print(f"{x},{y}")

if __name__ == "__main__":
    center(sys.argv[1], sys.argv[2])

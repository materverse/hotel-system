# -*- coding: utf-8 -*-
"""前后端接口对账：找出前端调用了但后端没有的路径。

用法：python scripts/check_routes.py
思路：把两边的路径都规范化（去掉 /api 前缀、把 ${id} 与 {id} 统一成 :x），再逐条比对。
"""
import glob
import io
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CTRL = os.path.join(ROOT, "backend", "src", "main", "java", "com", "cqucc", "hotel", "controller")
FE = os.path.join(ROOT, "frontend", "src")


def normalize(path):
    p = path.split("?")[0].strip().rstrip("/")
    if p.startswith("/api"):
        p = p[4:]
    if not p.startswith("/"):
        p = "/" + p
    p = re.sub(r"\$\{[^}]+\}", ":x", p)   # 前端模板串 ${id}
    p = re.sub(r"\{[^}]+\}", ":x", p)     # 后端占位符 {id}
    return p


def backend_routes():
    routes = set()
    for f in glob.glob(os.path.join(CTRL, "*.java")):
        src = io.open(f, encoding="utf-8").read()
        m = re.search(r'@RequestMapping\("([^"]*)"\)', src)
        base = m.group(1) if m else ""
        for mm in re.finditer(r'@(Get|Post|Put|Delete)Mapping\((?:value\s*=\s*)?"([^"]*)"\)', src):
            routes.add((mm.group(1).upper(), normalize(base + mm.group(2))))
    return routes


def frontend_calls():
    calls = []
    pattern = re.compile(r"http\.(get|post|put|delete)\(\s*[`'\"]([^`'\"]+)")
    files = glob.glob(os.path.join(FE, "**", "*.js"), recursive=True) + \
        glob.glob(os.path.join(FE, "**", "*.vue"), recursive=True)
    for f in files:
        src = io.open(f, encoding="utf-8", errors="ignore").read()
        for mm in pattern.finditer(src):
            calls.append((mm.group(1).upper(), normalize(mm.group(2)), mm.group(2), os.path.basename(f)))
    return calls


def main():
    routes = backend_routes()
    calls = frontend_calls()
    print("后端路由 %d 条，前端调用 %d 处" % (len(routes), len(calls)))
    missing = []
    for method, norm, raw, src in calls:
        if (method, norm) not in routes:
            missing.append((method, raw, src))
    print("\n=== 前端调用但后端不存在的路径 ===")
    seen = set()
    for method, raw, src in missing:
        if (method, raw) in seen:
            continue
        seen.add((method, raw))
        print("  %-6s %-40s (%s)" % (method, raw, src))
    print("\n共 %d 个不匹配路径" % len(seen))
    return 0 if not seen else 1


if __name__ == "__main__":
    raise SystemExit(main())

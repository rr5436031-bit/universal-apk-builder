#!/usr/bin/env python3
"""Deteksi jenis project dari berkas konfigurasi. Menulis detect.json dan GITHUB_OUTPUT."""
import json
import os
import re
import sys

SKIP = {".git", "node_modules", ".gradle", "build", "Pods", ".dart_tool", ".idea"}
BASE = sys.argv[1] if len(sys.argv) > 1 else "."


def walk(root=None, max_depth=3):
    root = root or BASE
    for cur, dirs, files in os.walk(root):
        rel = os.path.relpath(cur, root)
        depth = 0 if rel == "." else rel.count(os.sep) + 1
        dirs[:] = [] if depth >= max_depth else sorted(d for d in dirs if d not in SKIP)
        yield cur, files


def find(pred, root=None):
    for cur, files in walk(root):
        for f in sorted(files):
            if pred(f):
                yield os.path.join(cur, f)


def read(path, limit=400000):
    try:
        with open(path, encoding="utf-8", errors="ignore") as fh:
            return fh.read(limit)
    except OSError:
        return ""


def info(fid, label, root, **kw):
    d = dict(framework=fid, label=label, root=root, supported=True, reason="",
             java="17", node="", flutter=False, ndk=False, dotnet=False, android=True)
    d.update(kw)
    return d


def no(reason):
    return info("unknown", "Unknown", BASE, supported=False, reason=reason, android=False)


def gradle_java(d):
    for p in find(lambda f: f == "gradle-wrapper.properties", d):
        m = re.search(r"gradle-(\d+)\.", read(p))
        if m and int(m.group(1)) < 7:
            return "11"
    return "17"


def detect():
    if not os.path.isdir(BASE):
        return no("Folder project tidak ditemukan: " + BASE)

    for p in find(lambda f: f == "pubspec.yaml"):
        if "flutter" in read(p):
            return info("flutter", "Flutter", os.path.dirname(p), flutter=True)

    for p in find(lambda f: f == "project.godot"):
        return info("godot", "Godot", os.path.dirname(p))

    for p in find(lambda f: f == "ProjectVersion.txt"):
        if p.endswith(os.path.join("ProjectSettings", "ProjectVersion.txt")):
            return info("unity", "Unity", os.path.dirname(os.path.dirname(p)), android=False)

    for p in find(lambda f: f.endswith(".csproj")):
        t = read(p)
        if "UseMaui" in t and "android" in t.lower():
            return info("maui", ".NET MAUI", os.path.dirname(p), dotnet=True)

    for p in find(lambda f: f == "package.json"):
        try:
            data = json.loads(read(p))
        except ValueError:
            continue
        deps = {}
        deps.update(data.get("dependencies", {}))
        deps.update(data.get("devDependencies", {}))
        d = os.path.dirname(p)
        if "@nativescript/core" in deps or "nativescript" in deps:
            return info("nativescript", "NativeScript", d, node="20")
        if "expo" in deps:
            return info("expo", "Expo", d, node="20")
        if "react-native" in deps:
            return info("react-native", "React Native", d, node="20")
        if "@capacitor/core" in deps:
            if "build" not in data.get("scripts", {}):
                return no('Project Capacitor/Ionic tidak memiliki script "build" di package.json.')
            m = re.search(r"(\d+)", str(deps["@capacitor/core"]))
            java = "21" if m and int(m.group(1)) >= 7 else "17"
            label = "Ionic" if any(k.startswith("@ionic/") for k in deps) else "Capacitor"
            return info("capacitor", label, d, node="20", java=java)
        if "cordova" in deps or "cordova" in data or "cordova-android" in json.dumps(data):
            return info("cordova", "Cordova", d, node="20")

    for p in find(lambda f: f == "config.xml"):
        t = read(p)
        if "<widget" in t and "cordova" in t.lower():
            return info("cordova", "Cordova", os.path.dirname(p), node="20")

    for p in find(lambda f: f in ("settings.gradle", "settings.gradle.kts")):
        d = os.path.dirname(p)
        txt = "".join(read(g) for g in find(lambda f: f in ("build.gradle", "build.gradle.kts"), d))
        if re.search(r"com\.android\.application|android\.application", txt):
            java = gradle_java(d)
            ndk = "externalNativeBuild" in txt or any(
                True for _ in find(lambda f: f in ("CMakeLists.txt", "Android.mk"), d))
            if ndk:
                return info("android-ndk", "C++ NDK", d, ndk=True, java=java)
            kt = any(True for _ in find(lambda f: f.endswith(".kt"), d))
            return info("android-kotlin" if kt else "android-java",
                        "Kotlin" if kt else "Java", d, java=java)

    return no("Tidak ditemukan penanda project yang didukung (pubspec.yaml Flutter, package.json "
              "React Native/Expo/Capacitor/Ionic/Cordova/NativeScript, settings.gradle Android, "
              "project.godot, ProjectSettings Unity, .csproj MAUI). Pastikan ZIP berisi source project.")


res = detect()
print("[detect] framework=%s supported=%s root=%s" % (res["label"], res["supported"], res["root"]))
if res["reason"]:
    print("[detect] " + res["reason"])
with open("detect.json", "w") as fh:
    json.dump(res, fh, indent=2)
out = os.environ.get("GITHUB_OUTPUT")
if out:
    with open(out, "a") as fh:
        for k, v in res.items():
            s = str(v).lower() if isinstance(v, bool) else str(v).replace("\n", " ")
            fh.write("%s=%s\n" % (k, s))

#!/usr/bin/env python3
"""Membuat report.json dari build.log (sukses maupun gagal). Tidak mengarang error."""
import datetime
import glob
import json
import os
import re

ANSI = re.compile(r"\x1b\[[0-9;]*[A-Za-z]")
status = {"success": "success", "cancelled": "cancelled"}.get(os.environ.get("JOB_STATUS", ""), "failed")
det = {}
if os.path.exists("detect.json"):
    with open("detect.json") as fh:
        det = json.load(fh)
log = []
if os.path.exists("build.log"):
    with open("build.log", errors="ignore") as fh:
        log = [ANSI.sub("", l.rstrip("\n")) for l in fh]

PATTERNS = [
    r"^e: (?:file://)?(?P<f>\S+?\.kt):(?P<l>\d+):\d+:? (?P<m>.*)$",
    r"^e: (?:file://)?(?P<f>\S+?\.kt): \((?P<l>\d+), \d+\): (?P<m>.*)$",
    r"^(?P<f>\S+\.java):(?P<l>\d+): error: (?P<m>.*)$",
    r"^(?P<f>\S+\.dart):(?P<l>\d+):\d+: Error: (?P<m>.*)$",
    r"^(?P<f>\S+\.(?:cpp|cc|cxx|c|h|hpp)):(?P<l>\d+):\d+: (?:fatal )?error: (?P<m>.*)$",
    r"^(?P<f>\S+\.cs)\((?P<l>\d+),\d+\): error (?P<m>.*)$",
    r"^(?P<f>\S+\.(?:tsx?|jsx?))\((?P<l>\d+),\d+\): error (?P<m>.*)$",
    r"^(?P<f>\S+\.(?:tsx?|jsx?)):(?P<l>\d+):\d+ - error (?P<m>.*)$",
    r"^(?P<f>\S+\.xml):(?P<l>\d+): error: (?P<m>.*)$",
]
CAUSES = [
    ("Could not resolve", "Dependency tidak dapat di-resolve. Periksa nama/versi dependency atau repository."),
    ("SDK location not found", "Lokasi Android SDK tidak ditemukan (local.properties di ZIP mungkin salah)."),
    ("Unsupported class file major version", "Versi JDK tidak cocok dengan versi Gradle/plugin project."),
    ("Failed to find Build Tools", "Versi Build Tools yang diminta project tidak tersedia."),
    ("Failed to install the following Android SDK packages", "Paket Android SDK gagal dipasang (lisensi/versi)."),
    ("Cannot find module", "Modul Node.js tidak ditemukan. Periksa package.json dan lockfile."),
    ("Tidak ada APK baru", "Build selesai tanpa menghasilkan APK. Periksa task build dan tipe build."),
    ("Project tidak didukung", "Struktur/konfigurasi project tidak dikenali oleh Universal Build Engine."),
    ("UNITY_LICENSE", "Build Unity memerlukan secret lisensi Unity di repository."),
]


def extract():
    for line in log:
        for pat in PATTERNS:
            m = re.match(pat, line)
            if m:
                return "Compilation error", m.group("m").strip(), m.group("f"), int(m.group("l"))
    for i, line in enumerate(log):
        if "What went wrong:" in line:
            msg = " ".join(x.strip() for x in log[i + 1:i + 4] if x.strip())
            return "Build failed", msg, None, None
    for line in log:
        if re.search(r"(?i)\b(error|exception|failed)\b", line) and not line.startswith("[detect]") \
                and not re.search(r"(?i)\b0 errors?\b", line):
            return "Build failed", line.strip(), None, None
    tail = [l for l in log if l.strip() and not l.startswith("[detect]")]
    if not tail:
        return "Build failed", "Gagal sebelum langkah build dimulai (persiapan environment: JDK/Android SDK/Flutter/Node). Lihat log job di GitHub Actions.", None, None
    return "Build failed", tail[-1], None, None


err = {"summary": None, "message": None, "file": None, "line": None, "cause": None}
if status != "success":
    s, m, f, l = extract()
    err.update(summary=s, message=m, file=f, line=l)
    blob = "\n".join(log)
    for key, cause in CAUSES:
        if key in blob:
            err["cause"] = cause
            break
    if status == "cancelled":
        err.update(summary="Build cancelled", message="Workflow dibatalkan.")

report = {
    "status": status,
    "project_name": os.environ.get("PROJECT_NAME", ""),
    "framework": det.get("label", "Unknown"),
    "build_type": os.environ.get("BUILD_TYPE", "debug"),
    "workflow": {"run_id": os.environ.get("GITHUB_RUN_ID", ""),
                 "run_number": int(os.environ.get("RUN_NUMBER", "0") or 0)},
    "error": err,
    "build_log": log[-300:],
    "timestamp": datetime.datetime.now(datetime.timezone.utc).isoformat(),
}
apks = sorted(glob.glob("out/*.apk"))
if status == "success" and apks:
    report["apk"] = {"name": os.path.basename(apks[0]), "size_bytes": os.path.getsize(apks[0])}
with open("report.json", "w") as fh:
    json.dump(report, fh, indent=2)
print("[report] status=%s" % status)

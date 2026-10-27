#!/usr/bin/env python3
"""Measure raw APK bytes and ZIP contents, never compare artifact ZIP with APK."""
import hashlib
import json
from pathlib import Path
import sys
from zipfile import ZipFile

def measure(filename):
    apk = Path(filename)
    with ZipFile(apk) as archive:
        groups = {}
        for entry in archive.infolist():
            if entry.is_dir():
                continue
            parts = entry.filename.split('/')
            group = '/'.join(parts[:2]) if parts[0] == 'lib' else parts[0]
            sizes = groups.setdefault(group, {'uncompressed_bytes': 0, 'compressed_bytes': 0})
            sizes['uncompressed_bytes'] += entry.file_size
            sizes['compressed_bytes'] += entry.compress_size
        return {'apk': apk.name, 'raw_apk_bytes': apk.stat().st_size,
                'sha256': hashlib.sha256(apk.read_bytes()).hexdigest(), 'contents': groups}

results = [measure(name) for name in sys.argv[1:]]
if not results:
    sys.exit('Usage: apk-size.py BASELINE.apk [CANDIDATE.apk]')
report = {'apks': results}
if len(results) == 2:
    report['saved_raw_bytes'] = results[0]['raw_apk_bytes'] - results[1]['raw_apk_bytes']
print(json.dumps(report, indent=2, sort_keys=True))

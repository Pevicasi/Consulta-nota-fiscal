#!/usr/bin/env python3
"""Verify the actual distributable with Android tooling and a pinned public identity."""
import hashlib
import os
from pathlib import Path
import re
import subprocess
import sys
from apk_integrity import check_site_assets

apk = Path(sys.argv[1]).resolve()
build_tools = Path(os.environ['ANDROID_HOME']) / 'build-tools' / '35.0.0'
pin = os.environ['ANDROID_SIGNING_CERT_SHA256'].replace(':', '').lower()
if not re.fullmatch('[0-9a-f]{64}', pin):
    sys.exit('Missing or invalid approved signing fingerprint')
result = subprocess.run([str(build_tools / 'apksigner'), 'verify', '--verbose', '--print-certs', str(apk)], check=True, text=True, capture_output=True)
certs = re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)', result.stdout)
if certs != [pin] or 'Verified using v2 scheme (APK Signature Scheme v2): true' not in result.stdout:
    sys.exit('APK signature does not match the approved signing identity and v2 requirements')
if 'CN=Android Debug' in result.stdout:
    sys.exit('A debug certificate cannot be used for distribution')
badging = subprocess.check_output([str(build_tools / 'aapt'), 'dump', 'badging', str(apk)], text=True)
match = re.search(r"package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'", badging)
if not match or match[1] != 'br.com.pevicasi.nfefacil' or 'application-debuggable' in badging:
    sys.exit('Wrong package identity or debuggable distribution APK')
if int(match[2]) != int(os.environ['EXPECTED_VERSION_CODE']):
    sys.exit('Unexpected distribution versionCode')
check_site_assets(apk)
(apk.parent / 'SHA256SUMS').write_text(hashlib.sha256(apk.read_bytes()).hexdigest() + '  ' + apk.name + '\n')
(apk.parent / 'SIGNING-CERT-SHA256.txt').write_text(pin + '\n')
print('Distribution verified: package=' + match[1] + ', versionCode=' + match[2] + ', versionName=' + match[3] + ', debuggable=false, SHA-256 certificate=' + pin)

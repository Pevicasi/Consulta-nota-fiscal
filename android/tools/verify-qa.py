#!/usr/bin/env python3
"""A QA artifact is explicitly separate from permanent-key distribution."""
import hashlib
import os
from pathlib import Path
import re
import subprocess
import sys
from apk_integrity import check_site_assets

apk = Path(sys.argv[1]).resolve()
tools = Path(os.environ['ANDROID_HOME']) / 'build-tools' / '35.0.0'
verified = subprocess.check_output([str(tools/'apksigner'), 'verify', '--verbose', '--print-certs', str(apk)], text=True)
if 'Verified using v2 scheme (APK Signature Scheme v2): true' not in verified:
    sys.exit('QA APK v2 signature verification failed')
info = subprocess.check_output([str(tools/'aapt'), 'dump', 'badging', str(apk)], text=True)
match = re.search(r"package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'", info)
if not match or match[1] != 'br.com.pevicasi.nfefacil.qa' or not match[3].endswith('-qa'):
    sys.exit('QA APK could conflict with distribution identity')
if int(match[2]) != int(os.environ['EXPECTED_VERSION_CODE']):
    sys.exit('Unexpected QA versionCode')
certs = re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)', verified)
if len(certs) != 1:
    sys.exit('Unexpected QA signer count')
check_site_assets(apk)
(apk.parent/'SHA256SUMS').write_text(hashlib.sha256(apk.read_bytes()).hexdigest()+'  '+apk.name+'\n')
(apk.parent/'SIGNING-CERT-SHA256.txt').write_text(certs[0]+'\n')
(apk.parent/'QA-NOT-DISTRIBUTION.txt').write_text('NF-e Facil QA: test package br.com.pevicasi.nfefacil.qa, debug signing, separate data. Not a permanent-key distribution APK. Do not uninstall the original app or disable Play Protect to install this test artifact. See android/ASSINATURA.md.\n')
print('QA verified: separate package, v2 signature, version, checksum and unchanged site assets. Not permanent-key distribution.')

#!/usr/bin/env python3
"""Verify personal-use debug APK under the original application identity."""
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
if not match or match[1] != 'br.com.pevicasi.nfefacil' or match[3] != '1.1.0':
    sys.exit('Unexpected application identity for personal-use APK')
if int(match[2]) != int(os.environ['EXPECTED_VERSION_CODE']):
    sys.exit('Unexpected QA versionCode')
certs = re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)', verified)
if len(certs) != 1:
    sys.exit('Unexpected QA signer count')
check_site_assets(apk)
(apk.parent/'SHA256SUMS').write_text(hashlib.sha256(apk.read_bytes()).hexdigest()+'  '+apk.name+'\n')
(apk.parent/'SIGNING-CERT-SHA256.txt').write_text(certs[0]+'\n')
(apk.parent/'PERSONAL-DEBUG-SIGNING.txt').write_text('NF-e Facil 1.1.0 personal-use debug APK. Package br.com.pevicasi.nfefacil. Debug certificate can change between GitHub Actions runs: direct upgrade may fail; back up important data before any manual uninstall. Do not bypass Play Protect.\n')
print('Personal-use APK verified: original package, v2 signature, version, checksum and unchanged site assets. Debug-signed, not permanent-key distribution.')

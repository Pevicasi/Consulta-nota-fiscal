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
if not match or match[1] != 'br.com.pevicasi.nfefacil' or match[3] != '1.4.2':
    sys.exit('Unexpected application identity for personal-use APK')
if int(match[2]) != int(os.environ['EXPECTED_VERSION_CODE']):
    sys.exit('Unexpected QA versionCode')
if "application-label:'NF-e Fácil'" not in info or '.qa' in match[1] or '-qa' in match[3]:
    sys.exit('Unexpected app name or rejected QA identity')
from zipfile import ZipFile
with ZipFile(apk) as archive:
    abis = sorted({name.split('/')[1] for name in archive.namelist() if name.startswith('lib/') and name.endswith('.so')})
expected_abi = os.environ.get('EXPECTED_ABI', 'universal')
expected_abis = ['arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64'] if expected_abi == 'universal' else [expected_abi]
if abis != sorted(expected_abis):
    sys.exit('Unexpected APK ABIs: '+str(abis))
certs = re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)', verified)
if len(certs) != 1:
    sys.exit('Unexpected QA signer count')
check_site_assets(apk)
(apk.parent/'SHA256SUMS').write_text(hashlib.sha256(apk.read_bytes()).hexdigest()+'  '+apk.name+'\n')
(apk.parent/'SIGNING-CERT-SHA256.txt').write_text(certs[0]+'\n')
(apk.parent/'PERSONAL-DEBUG-SIGNING.txt').write_text('NF-e Facil '+match[3]+' personal-use debug APK. Package br.com.pevicasi.nfefacil. Debug certificate can change between GitHub Actions runs: direct upgrade may fail; back up important data before any manual uninstall. Do not bypass Play Protect.\n')
print('Personal-use APK verified: original package, v2 signature, version, checksum and bundled assets matching this commit. Debug-signed, not permanent-key distribution.')
print('::notice title=Personal APK verified::NF-e Fácil | package='+match[1]+' | version='+match[3]+' | code='+match[2]
      +' | SHA-256='+hashlib.sha256(apk.read_bytes()).hexdigest()+' | bytes='+str(apk.stat().st_size)+' | ABIs='+','.join(abis))

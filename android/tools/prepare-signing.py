#!/usr/bin/env python3
"""Decode a user-provisioned signing key into runner temp, never generate one."""
import base64
import hashlib
import os
from pathlib import Path
import re
import subprocess
import sys

REQUIRED = ('ANDROID_KEYSTORE_BASE64', 'ANDROID_KEYSTORE_PASSWORD', 'ANDROID_KEY_ALIAS', 'ANDROID_KEY_PASSWORD', 'ANDROID_SIGNING_CERT_SHA256')

def main():
    missing = [name for name in REQUIRED if not os.environ.get(name)]
    if missing:
        raise ValueError('Missing signing configuration: ' + ', '.join(missing))
    pin = os.environ['ANDROID_SIGNING_CERT_SHA256'].replace(':', '').lower()
    if not re.fullmatch('[0-9a-f]{64}', pin):
        raise ValueError('ANDROID_SIGNING_CERT_SHA256 must be a SHA-256 certificate fingerprint')
    directory = Path(os.environ['RUNNER_TEMP']) / 'nfe-signing'
    directory.mkdir(mode=0o700, exist_ok=True)
    directory.chmod(0o700)
    key = directory / 'distribution.p12'
    if key.exists():
        raise ValueError('Refusing to overwrite existing temporary signing material')
    try:
        data = base64.b64decode(''.join(os.environ['ANDROID_KEYSTORE_BASE64'].split()), validate=True)
        with key.open('xb') as output:
            key.chmod(0o600)
            output.write(data)
        result = subprocess.run(['keytool', '-exportcert', '-keystore', str(key),
            '-alias', os.environ['ANDROID_KEY_ALIAS'], '-storepass:env', 'ANDROID_KEYSTORE_PASSWORD'], capture_output=True)
        if result.returncode:
            raise ValueError('Unable to open signing alias; check keystore, alias and password securely')
        if hashlib.sha256(result.stdout).hexdigest() != pin:
            raise ValueError('Signing certificate differs from the approved public fingerprint')
        # Only a file path is persisted to the job environment. Passwords stay in Actions Secrets.
        with open(os.environ['GITHUB_ENV'], 'a', encoding='utf-8') as output:
            output.write('ANDROID_KEYSTORE_PATH=' + str(key) + '\n')
        print('Provisioned signing key matches the approved certificate SHA-256: ' + pin)
    except BaseException:
        key.unlink(missing_ok=True)
        raise

if __name__ == '__main__':
    try:
        main()
    except (ValueError, KeyError, OSError):
        # Never echo tool output, base64 data or environment values on failure.
        print('Signing preparation failed. Check required variable names, key access and approved fingerprint; no fallback key was created.', file=sys.stderr)
        sys.exit(1)

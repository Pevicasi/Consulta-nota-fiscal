"""Isolated security tests: short-lived fixture keys never sign a distributed APK."""
import base64
import hashlib
import os
from pathlib import Path
import secrets
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).with_name('prepare-signing.py')
class SigningPreparationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.fixture = tempfile.TemporaryDirectory()
        key = Path(cls.fixture.name) / 'fixture.p12'
        cls.password = secrets.token_urlsafe(32)
        env = dict(os.environ, SIGNING_FIXTURE_PASSWORD=cls.password)
        result = subprocess.run(['keytool', '-genkeypair', '-keystore', str(key), '-storetype', 'PKCS12',
            '-storepass:env', 'SIGNING_FIXTURE_PASSWORD', '-keypass:env', 'SIGNING_FIXTURE_PASSWORD',
            '-alias', 'fixture', '-dname', 'CN=NF-e Signing Test Fixture', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '2'], env=env, capture_output=True)
        if result.returncode: raise RuntimeError('Unable to create isolated signing fixture')
        key.chmod(0o600)
        cls.encoded = base64.b64encode(key.read_bytes()).decode('ascii')
        der = subprocess.check_output(['keytool', '-exportcert', '-keystore', str(key), '-storepass:env', 'SIGNING_FIXTURE_PASSWORD', '-alias', 'fixture'], env=env, stderr=subprocess.DEVNULL)
        cls.pin = hashlib.sha256(der).hexdigest()
    @classmethod
    def tearDownClass(cls):
        cls.fixture.cleanup()
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.env = dict(os.environ, RUNNER_TEMP=str(self.root), GITHUB_ENV=str(self.root/'job.env'),
            ANDROID_KEYSTORE_BASE64=self.encoded, ANDROID_KEYSTORE_PASSWORD=self.password,
            ANDROID_KEY_ALIAS='fixture', ANDROID_KEY_PASSWORD=self.password, ANDROID_SIGNING_CERT_SHA256=self.pin)
    def prepare(self):
        result = subprocess.run(['python3', str(SCRIPT)], env=self.env, text=True, capture_output=True)
        self.assertNotIn(self.password, result.stdout+result.stderr)
        self.assertNotIn(self.encoded, result.stdout+result.stderr)
        return result
    def test_matching_pinned_key_is_private_and_only_path_is_persisted(self):
        self.assertEqual(0,self.prepare().returncode)
        key=self.root/'nfe-signing/distribution.p12'
        self.assertEqual(0o600,key.stat().st_mode & 0o777)
        self.assertEqual(0o700,key.parent.stat().st_mode & 0o777)
        self.assertEqual('ANDROID_KEYSTORE_PATH='+str(key)+'\n',(self.root/'job.env').read_text())
    def test_wrong_certificate_is_rejected_and_deleted(self):
        self.env['ANDROID_SIGNING_CERT_SHA256']='0'*64
        self.assertNotEqual(0,self.prepare().returncode)
        self.assertFalse((self.root/'nfe-signing/distribution.p12').exists())
        self.assertFalse((self.root/'job.env').exists())
    def test_missing_configuration_never_creates_a_fallback_key(self):
        del self.env['ANDROID_KEYSTORE_BASE64']
        self.assertNotEqual(0,self.prepare().returncode)
        self.assertFalse((self.root/'nfe-signing').exists())
    def test_existing_private_key_is_not_overwritten(self):
        self.assertEqual(0,self.prepare().returncode)
        key=self.root/'nfe-signing/distribution.p12';original=key.read_bytes()
        self.assertNotEqual(0,self.prepare().returncode)
        self.assertEqual(original,key.read_bytes())
    def test_invalid_base64_leaves_no_private_file(self):
        self.env['ANDROID_KEYSTORE_BASE64']='invalid!'
        self.assertNotEqual(0,self.prepare().returncode)
        self.assertFalse((self.root/'nfe-signing/distribution.p12').exists())

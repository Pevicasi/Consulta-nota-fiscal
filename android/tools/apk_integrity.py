from pathlib import Path
from zipfile import ZipFile

PAGES = ('fiscal-cost.js', 'index.html', 'consulta_precos_v15.html', 'consulta_precos_v16.html', 'consulta_precos_v17.html')
def check_site_assets(apk):
    root = Path(__file__).resolve().parents[2]
    with ZipFile(apk) as archive:
        for page in PAGES:
            if archive.read('assets/site/' + page) != (root / page).read_bytes():
                raise ValueError('Bundled site differs from source: ' + page)
        for asset in ('android-bridge.js', 'android-appearance.js'):
            if archive.read('assets/' + asset) != (root / 'android/app/src/main/assets' / asset).read_bytes():
                raise ValueError('Bundled Android asset differs from source: ' + asset)

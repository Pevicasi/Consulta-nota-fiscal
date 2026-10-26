from pathlib import Path
from zipfile import ZipFile

PAGES = ('index.html', 'consulta_precos_v15.html', 'consulta_precos_v16.html', 'consulta_precos_v17.html')
def check_site_assets(apk):
    root = Path(__file__).resolve().parents[2]
    with ZipFile(apk) as archive:
        for page in PAGES:
            if archive.read('assets/site/' + page) != (root / page).read_bytes():
                raise ValueError('Bundled site differs from source: ' + page)

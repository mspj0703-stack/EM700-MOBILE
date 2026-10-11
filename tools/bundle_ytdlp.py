#!/usr/bin/env python3
"""Download current upstream yt-dlp wheel and put only the pure Python files in app assets."""
from pathlib import Path
from tempfile import TemporaryDirectory
import re
import subprocess
import sys
from zipfile import ZipFile, ZIP_DEFLATED

root = Path(__file__).resolve().parents[1]
assets = root / 'app/src/main/assets'
assets.mkdir(parents=True, exist_ok=True)
with TemporaryDirectory(prefix='em700-ytdlp-') as tmp:
    subprocess.run([sys.executable, '-m', 'pip', 'download', '--only-binary=:all:', '--no-deps',
                    '--dest', tmp, 'yt-dlp>=2026.08.19,<2027'], check=True)
    wheels = list(Path(tmp).glob('yt_dlp-*.whl'))
    if len(wheels) != 1:
        raise RuntimeError(f'Expected one yt-dlp wheel, got: {wheels}')
    with ZipFile(wheels[0]) as wheel:
        version_py = wheel.read('yt_dlp/version.py').decode('utf-8')
        match = re.search(r"^__version__\s*=\s*['\"]([0-9.]+)['\"]", version_py, re.M)
        if not match:
            raise RuntimeError('yt-dlp wheel has no readable version')
        version = match.group(1)
        target = assets / 'yt_dlp_override.zip'
        with ZipFile(target, 'w', ZIP_DEFLATED, compresslevel=8) as dest:
            count = 0
            for entry in wheel.infolist():
                if entry.is_dir():
                    continue
                if not (entry.filename.startswith('yt_dlp/') or
                        re.fullmatch(r'yt_dlp-[^/]+\.dist-info/[^/]+', entry.filename)):
                    continue
                dest.writestr(entry.filename, wheel.read(entry), compress_type=ZIP_DEFLATED)
                count += 1
        if count < 20:
            raise RuntimeError('Bundled an incomplete yt-dlp wheel')
        (assets / 'yt_dlp_override.version').write_text(version + '\n', encoding='utf-8')
        print(f'Bundled yt-dlp {version}, {count} files, {target.stat().st_size:,} bytes')

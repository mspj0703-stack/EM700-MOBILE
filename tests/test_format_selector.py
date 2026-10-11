"""Offline tests for v0.1.8 format-selection logic."""
import importlib.util
import json
import sys
import types
from pathlib import Path

sys.modules['yt_dlp'] = types.ModuleType('yt_dlp')
sys.modules['ytdlp_runner'] = types.ModuleType('ytdlp_runner')
root = Path(__file__).resolve().parents[1]
if (root / 'repo').is_dir():
    root = root / 'repo'
p = root / 'app/src/main/assets/em700_format_diagnostic.py'
spec = importlib.util.spec_from_file_location('diagnostic', p)
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)

formats = [
    {'format_id': 'sb0','vcodec': 'none', 'acodec':'none','url':'https://x'},
    {'format_id': '18', 'vcodec': 'avc1', 'acodec':'mp4a', 'url':'https://x', 'height':360, 'ext':'mp4'},
    {'format_id': '22', 'vcodec': 'avc1', 'acodec':'mp4a', 'url':'https://x', 'height':720, 'ext':'mp4'},
    {'format_id': '137', 'vcodec': 'avc1', 'acodec':'none', 'url':'https://x', 'height':1080, 'ext':'mp4'},
    {'format_id': '134', 'vcodec': 'avc1', 'acodec':'none', 'url':'https://x', 'height':360, 'ext':'mp4'},
    {'format_id': '140', 'vcodec': 'none', 'acodec':'mp4a', 'url':'https://x', 'ext':'m4a'},
    {'format_id': '251', 'vcodec': 'none', 'acodec':'opus', 'url':'https://x', 'ext':'webm'},
    {'format_id': '0', 'vcodec': 'avc1', 'acodec':'none', 'height':360},
    {'format_id': 'drm','vcodec':'avc1','acodec':'none','url':'https://x','has_drm': True}
]
result = m.candidates(formats, 360)
assert result['combined'][0]['id']=='18',result
assert result['video'][0]['id']=='134',result
assert result['audio'][0]['id']=='140',result
assert all(x['id']!='drm' for k in result for x in result[k]),result
assert not any(x['id']=='0' for k in result for x in result[k]),result
assert all(result.values())
assert not any(m.candidates([],360).values())
print('Format selection test: PASS')

# Integration-like test of JSON report and warning handling with a fake extractor.
class FakeDL:
    def __init__(self, opts):
        self.opts = opts
    def __enter__(self): return self
    def __exit__(self, *_): pass
    def extract_info(self, url, download=False):
        assert not download
        assert 'js_runtimes' in self.opts
        self.opts['logger'].warning('JS challenge observed')
        return {'title':'Offline fixture','formats':formats}

sys.modules['yt_dlp'].YoutubeDL = FakeDL
sys.modules['yt_dlp'].version = types.SimpleNamespace(__version__='fixture')
sys.modules['ytdlp_runner']._cli_to_opts = lambda flags: {'js_runtimes':{'quickjs':{'path':'/some/qjs'}}}
rep = json.loads(m.plan('https://www.youtube.com/watch?v=fixture', 360))
assert rep['formats_seen'] == len(formats) and rep['choices']['video'][0]['id'] == '134'
assert 'JS challenge observed' in rep['warnings'][0]
assert rep['quickjs'] != '설정되지 않음'
print('Format inspection / logs mocked test: PASS')

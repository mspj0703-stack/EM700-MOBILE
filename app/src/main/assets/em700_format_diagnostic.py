"""Inspect stream availability before download, using the APK's yt-dlp/EJS runtime.

This module does not fetch media, store cookies, or bypass access restrictions.
"""
import json
from importlib import metadata

import yt_dlp
import ytdlp_runner


class _Log:
    def __init__(self):
        self.lines = []

    def debug(self, msg):
        # These messages explain missing formats / JS challenge issues.
        if any(k in str(msg).lower() for k in (
                'js runtime', 'javascript', 'challenge', 'ejs', 'nsig',
                'signature', 'sabr', 'skipped', 'po token', 'format')):
            self._add('INFO', msg)

    def warning(self, msg):
        self._add('WARN', msg)

    def error(self, msg):
        self._add('ERROR', msg)

    def _add(self, kind, msg):
        text = ' '.join(str(msg).split())
        if text and len(self.lines) < 20:
            self.lines.append(f'{kind}: {text[:250]}')


def _is_media(fmt):
    if fmt.get('has_drm') is True or fmt.get('drm_families'):
        return False
    # yt-dlp may expose manifest/fragments instead of a standalone URL.
    if not fmt.get('url') and not fmt.get('fragments'):
        return False
    v = fmt.get('vcodec') not in (None, 'none')
    a = fmt.get('acodec') not in (None, 'none')
    return bool(fmt.get('format_id')) and (v or a)


def _kind(fmt):
    video = fmt.get('vcodec') not in (None, 'none')
    audio = fmt.get('acodec') not in (None, 'none')
    return 'combined' if video and audio else ('video' if video else 'audio')


def _sort_key(fmt, max_height):
    kind = _kind(fmt)
    height = fmt.get('height') or 0
    if not isinstance(height, (float, int)):
        height = 0
    # Favor an at-or-below-cap format, then a normal playable container.
    over_cap = bool(kind != 'audio' and height > max_height)
    preferred_ext = fmt.get('ext') in (('m4a', 'mp4', 'webm') if kind == 'audio' else ('mp4', 'webm', 'mkv'))
    rate = fmt.get('tbr') or 0
    return (0 if not over_cap else 1, 0 if preferred_ext else 1,
            -height if not over_cap else height, -(rate if isinstance(rate, (int, float)) else 0))


def candidates(formats, max_height):
    """Pure selector: can be tested offline using extracted metadata."""
    found = {'combined': [], 'video': [], 'audio': []}
    for fmt in formats:
        if isinstance(fmt, dict) and _is_media(fmt):
            found[_kind(fmt)].append(fmt)
    result = {}
    for kind, group in found.items():
        group.sort(key=lambda f: _sort_key(f, max_height))
        seen = set()
        result[kind] = []
        for fmt in group:
            ident = str(fmt['format_id'])
            if ident in seen:
                continue
            seen.add(ident)
            result[kind].append({
                'id': ident, 'ext': str(fmt.get('ext') or '?'),
                'height': int(fmt.get('height') or 0),
                'vcodec': str(fmt.get('vcodec') or '?'),
                'acodec': str(fmt.get('acodec') or '?'),
            })
            if len(result[kind]) >= 4:
                break
    return result


def _probe(url, max_height):
    log = _Log()
    opts = ytdlp_runner._cli_to_opts(['--no-playlist'])
    quickjs = opts.get('js_runtimes', {}).get('quickjs')
    runtime = str(quickjs or '설정되지 않음')
    try:
        ejs_version = metadata.version('yt-dlp-ejs')
    except metadata.PackageNotFoundError:
        ejs_version = '패키지 미발견'
    opts.update({
        'logger': log, 'quiet': True, 'no_warnings': False,
        'noplaylist': True, 'skip_download': True,
        'ignoreerrors': False, 'socket_timeout': 25,
    })
    info = None
    error = ''
    try:
        with yt_dlp.YoutubeDL(opts) as downloader:
            info = downloader.extract_info(url, download=False)
    except Exception as exc:
        error = f'{type(exc).__name__}: {str(exc)[-400:]}'
    formats = info.get('formats') or [] if isinstance(info, dict) else []
    return {
        'yt_dlp': yt_dlp.version.__version__, 'ejs': ejs_version,
        'quickjs': runtime, 'title': str(info.get('title') or '') if isinstance(info, dict) else '',
        'formats_seen': len(formats), 'choices': candidates(formats, max_height),
        'warnings': log.lines[-12:], 'error': error,
    }


def plan(url, max_height=360):
    return json.dumps(_probe(url, int(max_height)), ensure_ascii=False)


def describe(url):
    p = _probe(url, 360)
    sections = [f"yt-dlp {p['yt_dlp']} / EJS {p['ejs']} / QuickJS {p['quickjs']}",
                f"형식 원본 {p['formats_seen']}개, 음성포함 {len(p['choices']['combined'])}, "
                f"영상 {len(p['choices']['video'])}, 음성 {len(p['choices']['audio'])}"]
    for kind, entries in p['choices'].items():
        for x in entries[:2]:
            sections.append(f"{kind}: {x['id']} {x['ext']} {x['height']}p")
    sections.extend(p['warnings'][-5:])
    if p['error']:
        sections.append(p['error'])
    return '\n'.join(sections)

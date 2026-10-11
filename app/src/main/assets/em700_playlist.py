"""Expand YouTube playlist metadata without downloading media. Called via Chaquopy."""
import json
import re
from urllib.parse import urlparse
import yt_dlp
import ytdlp_runner

_ID = re.compile(r"^[A-Za-z0-9_-]{11}$")


def _link(entry):
    video_id = entry.get('id')
    if isinstance(video_id, str) and _ID.fullmatch(video_id):
        return 'https://www.youtube.com/watch?v=' + video_id
    url = entry.get('webpage_url') or entry.get('url')
    if isinstance(url, str):
        parsed = urlparse(url)
        host = (parsed.hostname or '').lower()
        if parsed.scheme == 'https' and (host == 'youtu.be' or host == 'youtube.com' or
                                          host.endswith('.youtube.com')):
            return url
    return None


def resolve(url):
    # Use the same yt-dlp + QuickJS configuration as individual downloads.
    opts = ytdlp_runner._cli_to_opts([])
    opts.update({
        'noplaylist': False,
        'yesplaylist': True,
        'skip_download': True,
        'extract_flat': 'in_playlist',
        'ignoreerrors': True,
        'quiet': True,
        'no_warnings': True,
        'socket_timeout': 30,
    })
    items = []
    with yt_dlp.YoutubeDL(opts) as ydl:
        info = ydl.extract_info(url, download=False)
        if not isinstance(info, dict) or info.get('_type') not in ('playlist', 'multi_video'):
            raise ValueError('재생목록으로 인식되지 않았습니다. YouTube 재생목록 주소를 확인하세요.')
        for position, entry in enumerate(info.get('entries') or (), start=1):
            if not isinstance(entry, dict):
                continue  # Deleted, private, or unsupported entries may be unavailable.
            link = _link(entry)
            if link:
                items.append({
                    'url': link,
                    'title': str(entry.get('title') or entry.get('id') or '제목 없음'),
                    'position': position,
                })
    if not items:
        raise ValueError('조회 가능한 영상이 없습니다 (비공개/삭제/접근 제한 가능).')
    return json.dumps({'entries': items}, ensure_ascii=False)

"""Inject native Android QuickJS into yt-dlp-android 2.0.2 request options.

The dependency's CLI-to-API adapter silently ignores --js-runtimes, so this
bridge configures the yt-dlp Python API before any download starts.
"""
import os
import subprocess
import ytdlp_runner

_configured = False


def configure(qjs_path):
    global _configured
    if _configured:
        return
    if not os.path.isfile(qjs_path):
        raise RuntimeError("QuickJS binary was not packaged into the APK")

    # Check the executable is actually launchable before making a web request.
    try:
        version = subprocess.run([qjs_path, '--version'], capture_output=True,
                                 text=True, timeout=10)
    except Exception as exc:
        raise RuntimeError("QuickJS cannot run on this Android device: " + str(exc)) from exc
    if version.returncode != 0:
        raise RuntimeError("QuickJS failed to start: " + (version.stderr or version.stdout)[-300:])

    old_options = ytdlp_runner._cli_to_opts

    def options_with_js(cli_flags):
        opts = old_options(cli_flags)
        opts['js_runtimes'] = {'quickjs': {'path': qjs_path}}
        return opts

    ytdlp_runner._cli_to_opts = options_with_js
    _configured = True

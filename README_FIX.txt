EM700 Mobile - YouTube 403 Forbidden diagnostic/fix patch v0.1.1

Patch contents (add/overwrite at repository root):
- .github/workflows/build-apk.yml
- tools/bundle_ytdlp.py
- app/src/main/java/dev/beatbox/em700/WorkEngine.java
- app/src/main/java/dev/beatbox/em700/YtDlpUpdater.java

This patch preserves the previous Android SDK setup fix.

Upload EM700_403_Fix.zip to Codespaces repository root, then run:
  unzip -o EM700_403_Fix.zip
  git add .github/workflows/build-apk.yml tools/bundle_ytdlp.py app/src/main/java/dev/beatbox/em700/WorkEngine.java app/src/main/java/dev/beatbox/em700/YtDlpUpdater.java
  git commit -m "Update yt-dlp for 403 and improve errors"
  git push

GitHub Actions will fetch upstream yt-dlp >= 2026.08.19 at APK build time, and
include it in the APK. At first link download the app installs the newer Python
package to app-private storage and inserts it before the embedded old package
on Python sys.path. Errors/warnings are surfaced in the app log.

Reinstall the new debug APK (same application ID, same debug signing key on GitHub
runner MAY change, so if signature mismatch occurs, uninstall the old debug APK
first; this loses app preferences). Test local 8-second AVI first, then a short
public video URL. If 403 persists, copy the error log; rate limits, unavailable
formats, auth, TLS, or YouTube's changing mechanisms may still block downloads.

Not tested by an Android SDK/compiler here. Python build helper sanity checked.

EM700 Mobile v0.1.3 alpha / Android JS runtime + Test AVI crash workaround

This ZIP is a patch for the EXISTING GitHub repository, not a full project.
It combines the previous 403 logger/update fix and six prebuilt AVI test files.

Changes
- Retains yt-dlp 2026.08.19+ (bundled fresh during GitHub Actions build)
- Bundles version-matched yt-dlp-ejs Python scripts
- Cross-compiles QuickJS-ng for Android arm64 with Android NDK
- Injects QuickJS runtime path into yt-dlp-android 2.0.2 options
- Fixes test AVI button by copying embedded test videos instead of starting FFmpeg
- Retains the known-good Android SDK setup (no setup-android@v3)
- Adds fail-fast logging if QuickJS can't run

Codespaces commands (after uploading the ZIP to repository root):

  unzip -o EM700_JS_Runtime_Fix.zip
  git add .github/workflows/build-apk.yml tools/bundle_ytdlp.py app/build.gradle app/src/main
  git commit -m "Bundle Android JS runtime and fix test AVI crash"
  git push

The new Actions workflow automatically starts when you push.
New artifact: EM700-Mobile-v0.1.3-JS-runtime-debug-apk

Tests on your phone:
1. Tap 8s test AVI creation. Verify EM700_TEST_01..06 exist in selected folder.
2. Try a short, publicly accessible YouTube URL and AVI format.
3. Read app logs; expected 'yt-dlp engine: 2026.08.19 + QuickJS' or newer.
4. If 'Requested format is not available' persists, note the full logs:
   the app currently downloads progressive/composite A/V formats only.
   A later enhancement may be needed for adaptive video+audio merging.

Build and actual device playback are NOT verified in the current sandbox.
QuickJS compilation relies on an Android NDK being present in GitHub's hosted runner.
If the GitHub Action fails, share first ERROR and failing step.
If the app still crashes, Android Logcat FATAL EXCEPTION/Native crash is needed.

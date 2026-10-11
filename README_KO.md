# EM700 v0.1.9.2 — Python 보조 모듈 누락 오류 패치

오류: `ModuleNotFoundError: No module named 'em700_format_diagnostic'`

확인한 통합 소스에는 `em700_format_diagnostic.py`가 **이미 존재**합니다. APK에 실제로 들어갔는지 또는 앱 실행 중 Python이 해당 경로를 못 찾는지는 APK가 없어 아직 확정할 수 없습니다.

수정: Python 모듈을 절대 경로로 직접 로드하도록 개선하고, GitHub Actions에서 APK의 Python 보조 파일 3개를 검사합니다. 재생목록 보조 모듈에도 같은 방식을 적용했습니다.

## 기존 v0.1.9.1 Codespaces에 적용

```bash
unzip -o EM700_Module_Load_Fix_v0192.zip
git add .github/workflows/build-apk.yml app/build.gradle app/src/main/java/dev/beatbox/em700 app/src/main/assets/em700_format_diagnostic.py app/src/main/assets/em700_playlist.py
git commit -m "Fix Python module loading v0.1.9.2"
git push
```

로그 첫 줄에 `EM700 Mobile v0.1.9.2`가 표시되어야 합니다. GitHub Actions에서 `Verify packaged Python helpers`가 성공하는지 확인하고 새 APK로 테스트하세요.

제한: Android SDK가 없는 환경이라 APK 실빌드 및 실제 유튜브 다운로드는 미검증입니다. 유튜브 403 또는 스트림 선택 문제 자체는 별개의 오류입니다.

# EM700 Mobile v0.1.8 — YouTube 형식 진단/선택 패치

이 ZIP은 **v0.1.7 APK 프로젝트에 덮어쓰는 패치**입니다. v0.1.7의 재생목록 기능과 AVI 9종은 그대로 유지합니다.

## 변경
- 다운로드 전에 yt-dlp로 실제 미디어 스트림 메타데이터를 조회합니다.
- 음성 포함 영상을 우선 다운로드하고, 안 되면 영상·음성 스트림을 따로 받아 기존 FFmpeg 변환에서 합칩니다.
- 형식 ID별로 최대 3개 후보를 순차 시도합니다. 요청한 형식이 없는 문제를 좁혀 주며 일부 영상에서는 바로 해결될 수 있습니다.
- 화면 로그에 yt-dlp/EJS/QuickJS 상태, 스트림 개수, 시도한 ID, 다운로드 실패 내용을 표시합니다.
- 재생목록 전체 변환과 기존 AVI 설정, USB 저장, 테스트 AVI 9종은 변경하지 않습니다.
- DRM/로그인/지역제한, YouTube 403, PO-token 등 서버 접근 제한 자체를 해제하지 않습니다.

## 적용 (Codespaces, 저장소 최상위 폴더에서)
```
unzip -o EM700_Format_Diagnostic_v018.zip
git add app/src/main/assets/em700_format_diagnostic.py app/src/main/java/dev/beatbox/em700/WorkEngine.java app/src/main/java/dev/beatbox/em700/MainActivity.java app/build.gradle .github/workflows/build-apk.yml
git commit -m "Diagnose and select available YouTube formats v0.1.8"
git push
```
GitHub Actions → `Build EM700 Android APK` → `Artifacts` → `EM700-Mobile-v0.1.8-debug-apk`에서 apk 다운로드 후 설치합니다.

## 실기기 확인 순서
1. 앱 실행 로그에 `EM700 Mobile v0.1.8`이 표시되는지 확인합니다.
2. 이미 문제였던 **짧은 영상 링크 하나**로 시도합니다. (MP3와 AVI는 각각 시험 가능)
3. 실패할 경우 `엔진 상태`, `추출 진단`, `확인된 형식`, `source 시도`부터 마지막 실패까지의 로그를 캡처합니다.
4. 동일 파일을 휴대폰에서 불러와 AVI로 변환하는 것은 YouTube 형식 문제와 독립적으로 검사할 수 있습니다.

## 검증 범위
- Python 3 구문 컴파일 및 형식 선택/형식 조회 모의 테스트 통과
- 기존 Java AVI 9종/FFmpeg 명령 생성 테스트 통과
- WorkEngine Java 코드 문법 및 API 형태 검사(테스트 스텁 사용) 통과
- 실제 Android SDK/Gradle로 APK 컴파일 및 갤럭시 유튜브 다운로드 실기기 시험은 **실시하지 못했습니다**.

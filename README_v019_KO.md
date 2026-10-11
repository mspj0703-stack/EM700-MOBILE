# EM700 Mobile v0.1.9 — 백그라운드 변환 패치 (시험용)

기준: 사용자에게 전달한 v0.1.8 소스 + 이전 v0.1.7 재생목록 패치. 이 패치는 다음 5개 파일만 덮어씁니다.

- `app/src/main/java/dev/beatbox/em700/ConversionService.java` (신규)
- `app/src/main/java/dev/beatbox/em700/MainActivity.java`
- `app/src/main/AndroidManifest.xml`
- `app/build.gradle`
- `.github/workflows/build-apk.yml`

## 기능

- 앱 화면에서 변환을 누르면 Android Foreground Service에서 작업 시작
- 다른 앱을 사용하거나 화면을 꺼도 다운로드 및 FFmpeg 변환 시도 유지
- 알림에 상태/진행률/중단 버튼 표시. 앱으로 돌아가도 대기열 상태 확인
- 기존 MP3/MP4/AVI, USB SAF 폴더 저장, 재생목록 확장, 실패 건너뛰기 유지
- 재생목록 처리와 임시 파일 관리는 서비스 작업 스레드에서 수행
- 앱을 강제 종료하거나 재부팅하면 진행 중인 작업이 자동 복원되지 않음
- Android 15+ mediaProcessing 서비스에는 운영체제의 사용 시간 제한이 있음
- Android 13+에서 알림을 보려면 알림 권한 허용
- 8초 테스트 AVI 파일 복사는 기존 버튼대로 동작(백그라운드 전환 대상 아님)

## 설치/빌드

Codespaces 프로젝트 루트에 ZIP을 업로드하고:

```bash
unzip -o EM700_Background_v019_Patch.zip
git add .github/workflows/build-apk.yml app/build.gradle app/src/main/AndroidManifest.xml app/src/main/java/dev/beatbox/em700/MainActivity.java app/src/main/java/dev/beatbox/em700/ConversionService.java
git commit -m "Add background conversion service v0.1.9"
git push
```

GitHub Actions > Build EM700 Android APK가 성공하면 Artifacts의 `EM700-Mobile-v0.1.9-debug-apk` 다운로드.

## 검증 상태

- AndroidManifest.xml XML 구문 검사 PASS
- foregroundServiceType, Android 13 알림 권한, 서비스 진입/중단 로직 정적 검사 PASS
- Java 소스 구문 관련 검사(외부 Android SDK/라이브러리 부재로 의존성 해결 불가)
- **GitHub Actions 실제 APK 컴파일 및 Android 실기기 동작은 미검증**
- **이전 v0.1.8의 `compileDebugJavaWithJavac` 6개 오류는 정확한 메시지가 필요합니다.**

`Compile APK` 로그의 `6 errors` **위쪽**에서 `path/to/file.java:LINE: error:`로 시작하는 6개 오류를 보내주세요. 해당 오류를 확인하기 전에는 기존 컴파일 실패가 해결됐다고 볼 수 없습니다.

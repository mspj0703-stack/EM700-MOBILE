# EM700 Mobile · Android v0.1 Alpha

에듀플레이어 EM700에 넣을 영상을 **휴대폰에서 다운로드/변환하고 USB-C OTG 드라이브로 전송**하기 위한 Android 앱 소스.

## 현재 들어 있는 기능

- YouTube 등의 공개 영상 **단일 링크** → 영상 다운로드 → AVI/MP3/MP4 저장 시도 (yt-dlp 내장)
- 휴대폰에서 영상/음악 **복수 선택** → AVI/MP3/MP4로 변환
- AVI 해상도·코덱을 실기기로 확인하기 위한 **6개 프리셋**, 8초 샘플 6개 생성
- AVI **10/30/60/90/120분 분할** (기본 30분), 키프레임에 맞춰 경계 선택
- 변환 파일에 FFprobe 검사를 수행한 후 저장
- **Android Storage Access Framework(SAF)** 저장 폴더 선택: 갤럭시 `내 파일`에 나타나는 EM700 USB 저장공간의 `VIDEO` 폴더 선택 가능
- 중복 파일 이름 자동 회피, 임시 파일 자동 삭제, 진행 로그, 중단 요청
- Android 8.0+, arm64-v8a (64비트 갤럭시 등)

## 사용법

1. EM700을 **데이터 전송 가능한 C-to-C 케이블**로 휴대폰에 연결. USB 저장장치로 표시되는지 확인. 인식하지 않으면 OTG/전원/케이블 조건 확인.
2. 앱 → **저장 폴더 선택** → `USB 저장공간/VIDEO` 폴더 선택. 시스템에 USB 저장공간이 표시되지 않으면 휴대폰 내부의 `Documents` 등 다른 폴더를 먼저 선택.
3. 먼저 **8초 호환성 테스트 AVI 6개 생성** → EM700에서 하나씩 재생 확인.
4. 재생되는 번호를 골라 프리셋 설정. 링크를 입력하거나 영상 파일을 선택하여 대기열에 넣기.
5. **변환 시작**. 1~2시간 영상은 **30분 분할**로 시험. 완료 후 USB 저장장치에서 파일 확인.

⚠️ AVI 확장자만으로 EM700 재생을 보장하지 않습니다. 프리셋은 추정값이며 실기기 미검증입니다. 테스트 파일도 모두 안 된다면 제조사 전용 인코더가 만든 **실제로 재생되는 AVI**의 FFprobe 스트림과 비교해야 합니다.

⚠️ 이 alpha는 작업 중 앱을 **화면에 띄워 둬야 합니다**. 화면 꺼짐은 막지만 다른 앱으로 전환하거나 Android가 백그라운드 프로세스를 종료하면 작업이 중단될 수 있습니다. 1~2시간 파일 변환 시 배터리·발열·임시 공간을 확인하세요. 대기열은 아직 영구 저장되지 않습니다.

⚠️ yt-dlp 기반 링크 다운로드는 일부 사이트의 제약·포맷에 의해 실패할 수 있습니다. 앱 안에 독립적인 FFmpeg 다운로드/병합 실행 파일이 포함되지 않아 **이미 영상+음성이 합쳐진 단일 스트림**을 우선 선택합니다. 최신 최상위 화질이나 일부 라이브/인증 필요 영상은 지원되지 않을 수 있습니다. 다운로드는 본인 소유/이용 권한 있는 콘텐츠에만 사용하세요.

## APK 만드는 방법 (PC 없이 GitHub Codespaces 가능)

현재 제공 ZIP은 **실제 APK가 아니라 Android 소스 프로젝트**입니다. GitHub Actions가 인터넷에서 Android SDK, Gradle, 두 라이브러리를 받아 **디버그 APK를 자동 빌드**합니다.

1. GitHub에서 빈 **새 저장소** 만들기 (기본 브랜치 `main`).
2. `Code → Codespaces → Create codespace`.
3. 제공받은 ZIP 파일을 Codespaces 파일 탐색기에서 **저장소 최상위 폴더**에 업로드.
4. 터미널에서 (ZIP 파일명대로):
   ```bash
   unzip -o EM700_Android_Source.zip
   git add .
   git commit -m "Initial EM700 Android app"
   git push
   ```
5. GitHub 저장소의 **Actions → Build EM700 Android APK** 진입. `workflow_dispatch`가 필요하면 `Run workflow`를 눌러 실행.
6. 빌드에 성공하면 해당 실행의 **Artifacts → EM700-Mobile-v0.1-debug-apk** 다운로드, ZIP을 열고 `app-debug.apk` 설치.

**주의:** GitHub Actions가 빌드에 성공했는지 아직 직접 확인하지 못했습니다. 빌드 실패 시 Actions 로그가 다음 진단 자료입니다. `app-debug.apk`는 개인 테스트용 디버그 서명 앱으로 앱스토어용 배포 APK가 아닙니다.

Windows/PC가 있다면 Android Studio(Gradle 8.9, Android SDK 35)에서 `gradle :app:assembleDebug`로 빌드할 수도 있습니다.

## 안드로이드 구현 구조

- `MainActivity.java`: 링크·파일 입력, 폴더 선택, 대기열/상태 UI
- `WorkEngine.java`: 다운로드 → 임시 파일 → FFmpeg 변환 → FFprobe 검사 → SAF 출력
- `ConversionProfile.java`: AVI/MP3/MP4 FFmpeg 인자 생성 (Android 무관 순수 Java)
- `DocumentStorage.java`: Android 문서 공급자(USB 등)와 파일 읽기/쓰기
- `.github/workflows/build-apk.yml`: CI 자동 디버그 APK 빌드 + 다운로드 아티팩트

### 라이브러리

- `dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.7` (LGPL-3.0, FFmpegKit Android fork, MPEG-4/MP3 인코딩)
- `dev.ffmpegkit-maintained:yt-dlp-android:2.0.2` (MIT, Chaquopy Python 3.13 + yt-dlp 임베드)

오픈소스 제3자 라이선스 및 사용 조건은 각 upstream 저장소에서 확인. 실행 파일에 해당 라이브러리가 포함되어 APK 크기가 클 수 있습니다. 공개 배포 시 라이선스 고지/의무를 확인하세요.

## 테스트 결과 및 미확인 사항 (2026-10-11)

**실행된 검사:**
- JDK `javac`로 `ConversionProfile` 테스트 통과.
- Android API/라이브러리의 모의 Java stub을 통한 5개 주요 Java 파일 **문법/타입 검사 통과**. 이는 Android SDK로 컴파일한 것과 다릅니다.
- Linux FFmpeg로 8초 AVI 6종 인코딩 성공, FFprobe로 XVID/DIVX/MJPG 태그·해상도·프레임 확인.
- 8초 MP4 원본에서 AVI(분할 옵션 포함), MP3, MP4 출력 테스트 성공.

**미검증:**
- 실제 Android SDK/Gradle APK 빌드, 앱 실행, yt-dlp 사이트 다운로드, USB OTG/SAF 접근, EM700 기기 재생.
- 1~2시간 변환의 발열/성능/백그라운드 동작.

프로덕션용 앱이나 바로 설치 가능한 APK로 오인하지 마세요. 현재 상태는 **구현 및 인코딩 로컬 테스트가 된 Android MVP 소스**입니다.

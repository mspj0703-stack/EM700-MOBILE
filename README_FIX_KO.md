# EM700 Mobile v0.1.9.1 — Java 8개 컴파일 오류 수정

기준: v0.1.9 백그라운드 패치가 적용된 기존 Codespaces 저장소.

2026-10-11 GitHub Actions `logs_103303053618.zip`의 `apk/8_Compile APK.txt`에 나타난 Java 오류 8개는 다음 파일이 이전 패치에 포함되지 않아 발생했습니다.

- `MediaJob.java`: `playlist` 플래그 및 7개 인자 생성자 누락 (5개 오류)
- `PlaylistResolver.java`: 재생목록 식별 및 영상별 대기열 확장 클래스 누락 (3개 오류)
- `em700_playlist.py`: Java 컴파일 오류에는 직접 관련 없지만, 재생목록 조회 시 앱 런타임에서 필요한 보조 파일

v0.1.9 `ConversionService`, Foreground Service 알림·중단, `MainActivity`, FFmpeg/QuickJS, 기존 AVI 프리셋은 변경하지 않습니다.

## 적용

Codespaces 저장소 최상위 폴더에서 `EM700_v0191_Compile_Fix.zip`를 업로드하고:

```bash
unzip -o EM700_v0191_Compile_Fix.zip
# 파일 확인
ls app/src/main/java/dev/beatbox/em700/{MediaJob,PlaylistResolver}.java
ls app/src/main/assets/em700_playlist.py

git add app/src/main/java/dev/beatbox/em700/MediaJob.java app/src/main/java/dev/beatbox/em700/PlaylistResolver.java app/src/main/assets/em700_playlist.py
git commit -m "Fix missing playlist files and MediaJob constructor"
git push
```

GitHub → Actions → 새 빌드의 `Compile APK` 단계 확인. 성공하면 `Artifacts`에서 APK 다운로드.

## 검증 현황

- 로그 실제 오류 8개 확인 완료.
- 오류 8개를 유발한 누락된 선언과 생성자 제공 여부 확인.
- 누락된 Java 파일 2개를 Android/JSON/Chaquopy 최소 테스트 스텁과 함께 `javac` 컴파일: PASS.
- `MediaJob`의 6/7개 인자 생성자 및 재생목록 URL 판별 스모크 테스트: PASS.
- **실제 Android SDK/Gradle 빌드 및 안드로이드 실기기 테스트 미실시.** 다음 빌드에서 추가 오류가 드러날 수 있음.

Java 컴파일과 YouTube `Requested format is not available` 다운로드 오류는 서로 독립적인 문제입니다.

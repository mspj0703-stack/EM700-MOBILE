# EM700 v0.1.9.3 — FFmpegKit 런타임 누락 수정

## 로그에서 확인된 내용
- yt-dlp 2026.08.19 / EJS 0.8.0 / QuickJS 인식
- 영상 형식 조회 성공 (43개)
- 오디오 후보 251 다운로드는 HTTP 403으로 실패
- 다음 오디오 후보 140 다운로드 중 `NoClassDefFoundError: com.arthenica.smartexception.java.Exceptions`가 발생

## 이번 패치
- `app/build.gradle`: `smart-exception-java:0.2.1`, `smart-exception-common:0.2.1`을 런타임 의존성으로 추가
- `.github/workflows/build-apk.yml`: Gradle 런타임 의존성 목록에 두 라이브러리가 포함됐는지 확인하는 CI 단계 추가
- 버전 표시 `v0.1.9.3`으로 갱신. 기존 유튜브 및 AVI 코드, 백그라운드 서비스, 재생목록 기능은 변경하지 않음

## 설치
기존 v0.1.9.2 프로젝트에 패치 ZIP의 파일을 덮어쓴 후 GitHub에 커밋/푸시.

```sh
unzip -o EM700_v0193_FFmpegKit_Fix.zip
git add app/build.gradle app/src/main/java/dev/beatbox/em700/WorkEngine.java .github/workflows/build-apk.yml
git commit -m "Fix FFmpegKit missing dependency v0.1.9.3"
git push
```

## 테스트 순서
1. GitHub Actions에서 `Confirm FFmpegKit Smart Exception dependencies`와 `Compile APK` 통과 확인.
2. 새 APK 설치 후 로그에 `EM700 Mobile v0.1.9.3` 표시 확인.
3. 로컬 영상 AVI 변환 또는 테스트 파일 내보내기 등 FFmpegKit 동작 확인.
4. 유튜브 링크 재시도. HTTP 403은 유튜브 요청/클라이언트 차단 등 별개의 원인이므로 이번 패치로 해결이 보장되지 않음.

## 검증 한계
패치의 파일/문법·의존성 좌표는 검사했으나, Android SDK가 없는 현재 환경에서 APK 실빌드 및 기기 동작은 확인하지 못함.

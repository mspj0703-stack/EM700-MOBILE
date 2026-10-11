# EM700 v0.1.5 — 설치 프로그램 설정 기반 실험 프리셋

## 분석 결과
- MSI.CAB은 이전에 받은 EM700.zip 안의 MSI.CAB과 동일한 파일임(SHA-256 일치).
- CAB에서 402개 파일 추출. 이전에 제작된 오래된 범용 Media Player Utilities 4.46 설치 묶음으로, 현재 EM700 전용 최신 변환기인지 확인되지 않음.
- 추출된 AmvTransform.ini의 `AVISIZE`에서 `320X240(14;22;22)` 확인. **다른 설정인** `VIDEOSIZE=...320X240(16;20;25)`와 혼동 주의.
- 추출된 MediaManagerSetting.xml에서 `vcAVISizeL=320X240(30;30;30;300000;400000;500000)`과 `vcAVILAudioSampleRate=64K,96K` 확인. 각 프로그램의 별도 프리셋이며 EM700 지원을 의미하지 않음.
- 구성 파일 안에 Xvid 라이브러리/AVI·AMV 변환 모듈이 포함됨. 실제 출력 FourCC/오디오 코덱/헤더 구조까지 확정된 것은 아님.

## 변경 사항
기존 AVI 프리셋 0~5를 유지하면서, 비교용 새 프리셋 세 개를 추가함.
- 6: 320x240, MPEG-4(XVID), 14fps, 목표 300kbps, MP3 64kbps 모노 22050Hz
- 7: 320x240, MPEG-4(XVID), 22fps, 목표 400kbps, MP3 96kbps 모노 22050Hz
- 8: 320x240, MPEG-4(XVID), 30fps, 목표 400kbps, MP3 96kbps 스테레오 44100Hz

위 MP3 코덱/채널/샘플링 주파수 등은 호환성 시험용 **추정값**이며 공식 변환기 출력 파일로부터 확인한 값이 아님.

앱 내 8초 테스트 영상 개수를 6개에서 9개로 변경하고 사전 인코딩 AVI 07~09를 추가함. 영상은 FFmpeg 변환 및 디코딩 확인됨. EM700에서 실제 재생되는지는 미검증.

## 적용 (기존 v0.1.4에 덮어쓰기)
Codespaces 저장소 최상위에 ZIP을 업로드 후:

```bash
unzip -o EM700_Official_Preset_Patch_v015.zip
git add app/src/main/java/dev/beatbox/em700/ConversionProfile.java app/src/main/java/dev/beatbox/em700/MainActivity.java app/src/main/java/dev/beatbox/em700/TestAviExporter.java app/src/main/assets/em700_tests
git commit -m "Add official-installer test profiles"
git push
```

GitHub Actions에서 APK 빌드 후 07~09를 EM700에 전송해 재생 여부 확인. 원본 01~06 테스트 AVI가 이미 들어 있는 v0.1.3 이상 소스에 적용할 것.

**주의:** APK 컴파일과 실기기 재생은 아직 테스트하지 않았음. 이 패치는 유튜브 `Requested format is not available` 오류를 수정하지 않음.

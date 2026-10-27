# AutoControl Monitor v0.2 — Android Studio 프로젝트

## 이 프로젝트가 하는 일

스마트폰 화면을 사용자가 승인한 기간 동안 읽고, 기기 안에서 OCR로 문구를 분석합니다.

- `Ad 1 of 3`, `Ad 2 of 3` 등 광고 단계 감지
- `Reward granted` / 보상 지급 문구 감지
- `Google Play`, `설치하기` 등의 설치 안내 문구 감지
- `보물상자 열기`, `성장한계` 등 장비 화면 문구를 이용한 게임 복귀 **후보** 감지
- 상태 변경 시 알림 업데이트, 최근 25개 인식 상태 기록
- 갤러리/파일의 스크린샷 1장을 불러와 OCR 결과 미리보기
- 사용자가 누르는 즉시 캡처를 멈추는 **중지** 버튼 및 알림 액션

**제외:** 실제 광고 클릭, X/≫/▶| 아이콘 자동 터치, 광고 재실행, 반복 보상 수집, 광고 넘기기. OCR만으로 X 아이콘을 인식한 것으로 판단하지 않습니다.

## 요구 환경

- Windows 10/11 또는 macOS/Linux의 Android Studio
- JDK 17 (Android Studio에서 제공하는 JBR 사용 가능)
- Android SDK Platform 35 (설치되지 않았다면 Android Studio SDK Manager에서 설치)
- 인터넷: 최초 Gradle·ML Kit 라이브러리 다운로드용
- Android 8.0(API 26) 이상 스마트폰, 안드로이드 14+ 권장 테스트

## 설치/실행

1. ZIP을 압축 해제합니다.
2. **Android Studio > Open**에서 `AutoControl_v02_AndroidStudio` 폴더를 엽니다.
3. Gradle Sync를 실행합니다. Gradle 8.9가 설치되어 있지 않으면 Windows에서는 프로젝트 루트 `gradlew.bat`가 Gradle 공식 배포본을 다운로드하도록 작성되어 있습니다. 일부 Android Studio 버전에서 사용자 정의 부트스트랩을 인식하지 못하면 다음 단계에 따라 수동으로 빌드하세요.
4. **Windows 대안**: Android Studio의 Terminal을 열고 `gradlew.bat assembleDebug` 실행 (PowerShell이라면 `.\gradlew.bat assembleDebug`). macOS/Linux는 `./gradlew assembleDebug`.
5. APK 출력 위치: `app/build/outputs/apk/debug/app-debug.apk`. Android Studio의 Run을 이용해 연결한 스마트폰에 곧바로 설치할 수도 있습니다.
6. 설치한 앱을 실행해 알림 권한(선택)을 허용합니다.
7. `화면 분석 시작`을 누르고 Android가 표시하는 **화면 캡처 동의**를 승인합니다. 전체 화면을 대상으로 하려면 시스템 선택 화면에서 **전체 화면 공유**를 선택하세요. Android 14 이상은 매 신규 캡처 세션마다 동의가 필요합니다.
8. 사용자가 직접 게임에서 광고를 실행하고 종료 버튼도 직접 조작합니다. 상태를 보려면 알림을 확인하거나 앱으로 복귀해 `상태 새로고침`을 누르세요.
9. 종료 시 `화면 분석 중지` 또는 알림의 `중지`를 누릅니다.

### 화면 인식 테스트

`사진 1장 불러와 인식 테스트`를 누르고 저장된 스크린샷을 선택하면 감지된 상태와 OCR 결과를 확인할 수 있습니다. 사용자가 제공한 스크린샷으로 먼저 확인하는 용도입니다. 화면 전체가 영상이면 OCR이 구별할 문구가 없을 수 있습니다.

## 설계와 한계

- **MediaProjection**으로 화면을 가져오며, 포그라운드 서비스 상태 알림을 표시합니다.
- **ML Kit** 라틴 문자/한글 모델로 2.3초 이상 간격으로 분석합니다. 화면 캡처 이미지 자체는 기기 메모리에서만 처리하고 파일이나 서버에 보내지 않습니다.
- 상태 분류는 텍스트 기반 규칙이며 정확도가 보장되지 않습니다. 영상만 재생되거나 텍스트가 일시적으로 지워지면 `화면 분석 중`으로 나타납니다.
- 진행 바의 색상·위치, X, `≫`, `▶|` 같은 **도형 버튼은 아직 인식하지 않습니다.** 이를 인식하려면 별도 영상/이미지 처리와 화면별 검증이 필요합니다.
- Android 14+에서 시스템의 화면 공유 중지, 화면 잠금, 앱별 공유 종료 시 서비스가 종료될 수 있습니다. 화면 회전에 맞춘 Surface resize는 아직 구현하지 않았습니다.
- 보상 메시지가 화면에 표시돼도 게임 서버에 재화가 실제 반영되었음을 검증하는 기능은 없습니다.
- 광고 재생과 종료 조작은 항상 사용자가 직접 하도록 구성했습니다. 광고 자동 재생·클릭은 광고 서비스의 무효 트래픽 정책을 위반할 수 있습니다.
- 구글 플레이 배포 검토, 개인정보 안내 및 기기별 호환성 테스트는 별도로 필요합니다.

## 파일 구성

```
AutoControl_v02_AndroidStudio/
├─ app/
│  ├─ build.gradle.kts
│  └─ src/main/
│     ├─ AndroidManifest.xml
│     ├─ java/com/example/autocontrol/
│     │  ├─ MainActivity.kt                   사용자 화면, 이미지 테스트
│     │  ├─ CaptureMonitorService.kt          화면 캡처 포그라운드 서비스
│     │  ├─ TextScanner.kt                    한글·영어 OCR
│     │  ├─ ScreenState.kt                    화면 상태 규칙
│     │  └─ MonitorStore.kt                   로컬 상태 저장
│     └─ res/values/styles.xml
├─ tests/ScreenStateClassifierSmokeTest.kt
├─ tools/run-gradle.ps1
├─ gradlew.bat / gradlew                    Gradle 부트스트랩 스크립트
└─ README_KO.md
```

## 개발 도구 관련 유의

본 소스는 정적 검사 및 화면 상태 규칙 테스트를 거쳤지만, 이 실행 환경에는 Android SDK가 없어 실제 APK 빌드 및 실기기 동작 검증은 진행하지 못했습니다. 일부 제조사와 OS 버전에서 권한 흐름 수정이 필요할 수 있습니다.

## 참고 공식 문서

- https://developer.android.com/media/grow/media-projection
- https://developers.google.com/ml-kit/vision/text-recognition/v2/android
- https://support.google.com/admob/answer/3342054?hl=ko

# SafeWave 보호자 앱 (안드로이드)

어르신과 보호자가 보기 쉽게 만든 낙상 감지 보호자 앱입니다.
관제 사이트(SafeWave AI Monitor)와 **같은 Firebase 계정·데이터**를 씁니다.

- 언어·화면: Kotlin, Jetpack Compose (Material 3)
- 최소 안드로이드 8.0 (API 26), 대상 안드로이드 14 (API 34)
- 외부 라이브러리 없이 안드로이드 기본 기능으로 Firebase REST·실시간 스트림 사용

## 기능
- 관제 사이트와 같은 이메일 계정으로 로그인 (새 계정은 관리자 승인 후 사용)
- 실시간 상태를 큰 글씨·색·그림으로: 안전해요 / 집에 안 계세요 / 넘어진 것 같아요 / 긴급 / 연결 끊김
- 긴급이면 앱을 꺼 두었거나 화면이 잠겨 있어도 경보 화면, 경보음, 진동, 음성 안내
- "확인했어요" → 사이트의 보호자 확인과 똑같이 센서에 ACK 전송, 공유 기록에 남김
- 보호자 전화, 119 전화
- 어르신 정보 보기·고치기, 함께 사는 분 추가·고치기·지우기 (관리자)
- 비상연락처 추가·고치기·지우기 (관리자, 최대 4명)
- 공지사항 게시판 (관리자만 쓰기, 새 공지 알림·새 글 표시)
- 기록 보기 (레이더 이벤트 + 공유 기록)
- 갤럭시 Z 플립 커버 화면(플렉스 윈도우) 위젯과 커버 화면 경보
- 글자 크기 3단계, 음성 안내·경보음 켜기/끄기, 경보 시험
- **관제 탭**: 관제 사이트(https://h71212393.github.io/safewave/)를 앱 안에서 그대로 보기. 앱 로그인으로 사이트도 자동 로그인 (`ui/SiteScreen.kt`)

## 파일 구성
| 파일 | 하는 일 |
|---|---|
| `android/app/src/main/java/com/safewave/guardian/Net.kt` | 설정값, Firebase 로그인(이메일·비밀번호), 데이터 읽기·쓰기 |
| `Store.kt` | 앱 전체 상태(센서 상태, 계정, 어르신 정보, 기록, 공지)와 저장 기능 |
| `NodeStream.kt` | 센서 상태 실시간 수신 (Server-Sent Events) |
| `WatchService.kt` | 앱을 꺼도 계속 지켜보는 서비스, 알림·경보음·진동·음성 |
| `MainActivity.kt` | 로그인 흐름과 아래 메뉴 (홈·전화·기록·공지·설정) |
| `AlarmActivity.kt` | 잠금 화면 위에 뜨는 긴급 경보 화면 |
| `StatusWidget.kt` | 커버 화면·홈 화면 위젯, 접힘 감지 |
| `BootReceiver.kt` | 휴대폰을 다시 켜면 자동으로 지켜보기 시작 |
| `ui/Theme.kt` | 색, 글자 크기, 큰 버튼 |
| `ui/Screens.kt` | 로그인, 홈, 전화, 기록, 어르신 정보, 설정, 경보 화면 |
| `ui/Notices.kt` | 공지사항 목록·보기·쓰기 |
| `.github/workflows/android-apk.yml` | GitHub 에서 APK 자동 빌드 |

## 사이트와 같이 쓰는 데이터 (safewave/<키>/...)
| 경로 | 용도 |
|---|---|
| `nodes/daegu-102-1` | 센서 실시간 상태 (실시간 스트림으로 받음) |
| `cmd/daegu-102-1` | "ACK" 보내기 (알람 확인) |
| `shared/daegu-102-1/profile` | 대표 어르신 정보·비상연락처 (사이트와 공유) |
| `shared/daegu-102-1/people` | 함께 사는 분 (앱에서 추가) |
| `shared/daegu-102-1/notices` | 공지사항 (앱에서 작성) |
| `events`, `logs/daegu-102-1` | 기록 |
| `users/<uid>` | 계정 권한 (admin / member / pending) |

## 빌드
- **GitHub**: `main` 브랜치에 올리면 GitHub Actions 가 APK 를 만들어 Releases 의 `apk-latest` 에 `SafeWave-Guardian.apk` 로 올립니다.
- **안드로이드 스튜디오**: `android` 폴더를 엽니다 (Gradle 8.7 이상, JDK 17). Build → Build APK(s).

## 참고
- 서명 키 `android/app/safewave-capstone.jks` (비밀번호 safewave) 는 캡스톤 시연용입니다. 공개 저장소에 있으니 실제 배포에는 새 키를 쓰세요.
- 앱을 꺼도 경보를 받기 위해 "SafeWave 지키는 중" 알림이 항상 떠 있습니다.
- Firebase 웹 API 키는 사이트에도 공개된 값이며, 데이터 보호는 Firebase 보안 규칙이 담당합니다.

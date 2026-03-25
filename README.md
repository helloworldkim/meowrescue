# Meow Rescue

> 핀을 빼서 공을 굴려 고양이를 구출하는 물리 퍼즐 게임

![Platform](https://img.shields.io/badge/Platform-Android-green)
![Language](https://img.shields.io/badge/Language-Kotlin%202.2.0-purple)
![MinSDK](https://img.shields.io/badge/MinSDK-24%20(Android%207.0)-blue)
![Levels](https://img.shields.io/badge/Levels-100-orange)

## 소개

Meow Rescue는 안드로이드 하이퍼캐주얼 퍼즐 게임입니다. 플레이어는 핀을 올바른 순서와 타이밍에 맞춰 빼서, 공이 장애물을 피해 고양이에게 도달하도록 경로를 만들어야 합니다.

단순한 터치 조작에 물리 기반 공 굴림 + 논리적 순서 퍼즐이 결합된 형태로, 누구나 쉽게 배울 수 있지만 레벨이 올라갈수록 전략적 사고가 필요합니다.

## 주요 기능

- **핀 빼기 + 물리 공 굴림**: 핀을 터치하면 공이 중력에 의해 굴러감 (반발, 마찰, 경사면 지원)
- **100개 레벨**: 튜토리얼(1-10) → 초급(11-30) → 중급(31-60) → 상급(61-100) 점진적 난이도
- **dyn4j 물리 엔진**: 전문 2D 물리 라이브러리로 정확한 충돌, 바운스, 경사면 물리 시뮬레이션
- **5종 장애물**: 불, 가시, 움직이는 바닥, 텔레포트, 스위치 블록
- **4종 특수 공**: 일반, 불공, 철공, 폭탄공
- **고양이 컬렉션**: 8종의 귀여운 고양이 캐릭터 수집
- **별점 시스템**: 핀 제거 횟수 기반 1~3성 평가
- **진행도 저장**: Room DB로 레벨 클리어, 별점, 해금 고양이 저장
- **스레드 안전**: `CopyOnWriteArrayList` 기반 엔티티 목록, UI/게임 스레드 간 안전한 동기화

## 난이도 구성 (GDD 4.1)

| 구간 | 레벨 | 핵심 요소 | 목적 |
|------|------|-----------|------|
| **튜토리얼** | 1~10 | 핀 1~2개, 장애물 없음 | 조작법 학습 |
| **초급** | 11~30 | 핀 2~3개, 불/가시 등장 | 순서 퍼즐 이해 |
| **중급** | 31~60 | 움직이는 바닥, 복합 장애물 | 타이밍 + 전략 |
| **상급** | 61~100 | 텔레포트, 특수 공, 복합 퍼즐 | 도전 요소 |

## 기술 스택

| 구분 | 기술 |
|------|------|
| 언어 | Kotlin 2.2.0 |
| 빌드 | Gradle 9.0.0 + AGP 8.7.3 |
| 렌더링 | Android SurfaceView + Canvas |
| 물리 엔진 | dyn4j 5.0.2 (2D 물리 라이브러리) |
| 어노테이션 처리 | KSP 2.2.0-2.0.2 |
| 데이터베이스 | Room 2.6.1 |
| JDK | 21 |
| 최소 SDK | API 24 (Android 7.0) |
| 타겟 SDK | API 34 |

## 프로젝트 구조

```
com.meowrescue.game
├── game/
│   ├── GameEngine.kt          // 게임 상태 관리, 레벨 로딩, 업데이트 루프
│   ├── GameLoop.kt            // 60 FPS 게임 스레드
│   └── Dyn4jPhysicsEngine.kt  // dyn4j 물리 월드 래퍼 (중력, 충돌, 바운스)
├── model/
│   ├── Pin.kt                 // Sealed class: Normal, Timer, Directional, Chain, Locked
│   ├── Ball.kt                // Sealed class: Normal, Fire, Iron, Bomb
│   ├── Cat.kt                 // 고양이 데이터 (위치, 구출 상태)
│   ├── Obstacle.kt            // Sealed class: Fire, Spike, MovingPlatform, Teleport, SwitchBlock
│   └── Surface.kt             // 플랫폼 데이터 (위치, 크기, 각도)
├── level/
│   ├── LevelData.kt           // JSON 스키마에 매칭되는 레벨 데이터 모델
│   └── LevelLoader.kt         // assets에서 JSON 기반 레벨 로딩
├── ui/
│   ├── GameActivity.kt        // 게임 화면 (풀스크린 몰입 모드)
│   ├── GameView.kt            // SurfaceView 비트맵 스프라이트 렌더링
│   ├── MenuActivity.kt        // 메인 메뉴 + 동적 레벨 선택
│   ├── CollectionActivity.kt  // 고양이 컬렉션 그리드
│   └── Theme.kt               // UI 색상 상수 모음
├── data/
│   ├── AppDatabase.kt         // Room 데이터베이스 싱글턴
│   ├── UserProgressDao.kt     // 레벨 진행도 쿼리 DAO
│   └── GameRepository.kt      // Room + SharedPreferences 래핑 리포지토리
├── model/
│   └── util/
│       └── Vector2D.kt        // 가변 2D 벡터 (연산자 오버로드)
└── util/
    ├── SoundManager.kt        // SoundPool + MediaPlayer 관리 (Phase 4 예정)
    └── ResourceManager.kt     // 리소스 로딩 유틸리티
```

## 물리 엔진

dyn4j 5.0.2 라이브러리를 사용하여 정확한 2D 물리 시뮬레이션을 구현합니다:

- **좌표 변환**: 100px = 1m (화면 좌표 ↔ 물리 좌표)
- **중력**: 9.8 m/s² (Y-down 좌표계)
- **반발 계수**: 0.4 (공-플랫폼 충돌 시 바운스)
- **마찰 계수**: 0.3 (플랫폼 위 이동 감속)
- **연속 충돌 감지**: 고속 공의 터널링 방지
- **경사면 물리**: 각도에 따른 자연스러운 슬라이딩

## 레벨 데이터 형식

레벨은 `assets/levels/` 폴더에 JSON 파일로 정의됩니다:

```json
{
  "levelId": 1,
  "name": "첫 번째 구출",
  "difficulty": "tutorial",
  "maxPins": 1,
  "stars": { "one": 999, "two": 2, "three": 1 },
  "balls": [{ "type": "normal", "x": 540, "y": 200 }],
  "cats": [{ "x": 540, "y": 1600, "catId": "cat_001" }],
  "pins": [{ "type": "normal", "x": 540, "y": 700 }],
  "obstacles": [],
  "platforms": [{ "x": 340, "y": 720, "width": 400, "height": 20, "angle": 0 }]
}
```

### 핀-플랫폼 연결 규칙

핀이 플랫폼을 "지지"하는 조건:
- 플랫폼이 핀 아래 0~100px 범위 내
- 핀의 x좌표가 플랫폼의 수평 범위 안

핀을 제거하면 연결된 플랫폼도 함께 사라져 공의 경로가 변경됩니다.

## 게임 요소

### 핀 종류
| 타입 | 설명 | 등장 |
|------|------|------|
| Normal | 기본 핀, 터치하면 제거 | 레벨 1~ |
| Timer | 빼면 N초 후 다시 나타남 | 레벨 15~ |
| Directional | 빼는 방향에 따라 공의 경로가 바뀜 | 레벨 30~ |
| Chain | 연결된 다른 핀도 함께 제거 | 레벨 50~ |
| Locked | 특정 조건 충족 시에만 제거 가능 | 레벨 70~ |

### 공 종류
| 타입 | 반지름 | 특성 | 등장 |
|------|--------|------|------|
| Normal | 15px | 기본 물리 적용 | 레벨 1~ |
| Fire | 15px | 불 장애물 통과 가능 | 레벨 71~ |
| Iron | 18px | 더 무거운 물리 적용 | 레벨 75~ |
| Bomb | 20px | 장애물에 닿으면 폭발 | 레벨 80~ |

### 장애물
| 타입 | 효과 | 등장 |
|------|------|------|
| Fire | 공이 닿으면 파괴 (불공 제외) | 레벨 11~ |
| Spike | 공이 닿으면 파괴 | 레벨 16~ |
| MovingPlatform | 좌우로 움직이는 바닥 | 레벨 31~ |
| Teleport | 공을 다른 위치로 이동 | 레벨 61~ |
| SwitchBlock | ON/OFF 전환 블록 | 레벨 80~ |

## 빌드 방법

### 필수 요구사항
- Android Studio 2025.3 이상
- JDK 21 이상 (Android Studio에 내장)
- Android SDK (API 34)

### APK 빌드
```bash
# Android Studio 내장 JDK 사용
export JAVA_HOME="<Android Studio 경로>/jbr"
export ANDROID_HOME="<Android SDK 경로>"

./gradlew assembleDebug
```

APK 출력 경로: `app/build/outputs/apk/debug/app-debug.apk`

## 개발 로드맵

- [x] Phase 1: MVP 프로토타입 (게임 루프, 물리 엔진, 핀 메카닉)
- [x] Phase 2: dyn4j 물리 엔진 도입, 10개 튜토리얼 레벨
- [x] Phase 3: 100레벨 완성 (튜토리얼/초급/중급/상급)
- [ ] Phase 4: 효과음, BGM 추가
- [ ] Phase 5: AdMob 광고 연동
- [ ] Phase 6: 고양이 컬렉션 시스템 확장 (50종+)
- [ ] Phase 7: Play 스토어 출시 준비
- [ ] 데일리 챌린지 및 시즌 이벤트
- [ ] 유저 레벨 에디터

## 라이선스

All rights reserved.

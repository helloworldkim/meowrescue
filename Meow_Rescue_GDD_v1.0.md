# 🐱 Meow Rescue — Game Design Document (GDD) v1.0

> **핀을 빼서 공을 굴려 고양이를 구출하는 물리 퍼즐 게임**

---

## 📋 1. 게임 개요 (Game Overview)

| 항목 | 내용 |
|------|------|
| **게임명** | Meow Rescue |
| **장르** | 하이퍼캐주얼 퍼즐 (Hyper-Casual Puzzle) |
| **플랫폼** | Android (Kotlin Native) |
| **타겟 유저** | 전연령, 캐주얼 퍼즐 애호가 |
| **수익 모델** | 광고 기반 (AdMob 전면/보상형 광고) |
| **핵심 한 줄 요약** | 핀을 빼서 공을 굴려 고양이를 구출하는 물리 퍼즐 게임 |

### 1.1 게임 컨셉

귀여운 고양이들이 위험에 빠져 있습니다! 플레이어는 핀을 올바른 순서와 타이밍에 맞춰 빼서, 공이 장애물을 피해 고양이에게 도달하도록 경로를 만들어야 합니다.

단순한 조작(핀 터치)에 물리 기반 공 굴림 + 논리적 순서 퍼즐이 결합된 형태로, 누구나 쉽게 배울 수 있지만 레벨이 올라갈수록 전략적 사고가 필요합니다.

### 1.2 게임의 차별점

- **핀 빼기 + 경로 만들기**: 기존 Pull the Pin 장르에 경로 설계 퍼즐 요소를 추가
- **물리 기반 공 굴림**: 단순 낙하가 아닌 경사면, 바운스, 마찰을 활용한 물리 시뮬레이션
- **고양이 컬렉션**: 스테이지를 클리어할 때마다 새로운 고양이를 구출/수집하여 리텐션 강화
- **점진적 요소 해금**: 새로운 핀, 장애물, 특수 공이 레벨 진행에 따라 등장하여 지루함 방지

---

## 🎮 2. 핵심 게임플레이 (Core Gameplay)

### 2.1 기본 메카니즘

- **핀 (Pin)**: 화면에 배치된 핀을 터치하면 빠집니다. 핀이 빠지면 공이 중력에 의해 굴러갑니다.
- **공 (Ball)**: 물리 엔진에 의해 굴러가며, 경사면을 따라 이동합니다. 장애물에 닿으면 파괴됩니다.
- **고양이 (Cat)**: 공이 고양이에게 도달하면 구출 성공! 스테이지 클리어입니다.
- **장애물 (Obstacles)**: 불, 가시, 움직이는 바닥 등 공을 파괴하는 요소들입니다.

### 2.2 플레이 플로우

```
스테이지 시작
    ↓
화면에 핀, 공, 고양이, 장애물 배치
    ↓
플레이어가 핀을 터치하여 순서대로 빼기
    ↓
공이 중력에 의해 굴러가며 경로를 따라 이동
    ↓
┌─────────────────────────────┐
│  공이 고양이에 도달 → 성공!  │
│  장애물 충돌/화면 이탈 → 실패 │
└─────────────────────────────┘
    ↓
성공 시: 별점 표시 → 다음 레벨 (클리어 사이 광고 노출)
실패 시: 이어하기(광고) 또는 다시하기
```

### 2.3 조작 방식

| 조작 | 동작 |
|------|------|
| **핀 터치** | 핀을 터치하면 해당 핀이 빠짐 |
| **드래그** | 핀을 드래그하여 원하는 방향으로 빼기 (고급 스테이지) |
| **장기 프레스** | 공에 힘을 가하여 방향 조정 (특수 스테이지) |

---

## 🧩 3. 게임 요소 상세 (Game Elements)

### 3.1 핀 종류

| 핀 타입 | 설명 | 등장 시점 |
|---------|------|-----------|
| **일반 핀** | 빼면 공이 그냥 떨어짐 | 레벨 1~ |
| **타이머 핀** | 빼면 N초 후 다시 나타남 | 레벨 15~ |
| **방향 핀** | 빼는 방향에 따라 공의 경로가 바뀜 | 레벨 30~ |
| **연쇄 핀** | 빼면 연결된 다른 핀도 함께 빠짐 | 레벨 50~ |
| **잠금 핀** | 특정 조건 충족 시에만 빼기 가능 | 레벨 70~ |

### 3.2 장애물 종류

| 장애물 | 효과 | 등장 시점 |
|--------|------|-----------|
| **불 (Fire)** | 공이 닿으면 파괴 | 레벨 1~ |
| **가시 (Spike)** | 공이 닿으면 파괴 | 레벨 5~ |
| **움직이는 바닥** | 좌우로 움직이며 공의 경로를 방해 | 레벨 20~ |
| **텔레포트** | 공을 다른 위치로 이동시킴 | 레벨 40~ |
| **스위치 블록** | 핀을 빼면 ON/OFF 전환 | 레벨 60~ |

### 3.3 특수 공 종류

| 공 타입 | 특성 | 등장 시점 |
|---------|------|-----------|
| **일반 공** | 기본 물리 적용 | 레벨 1~ |
| **불공** | 가시를 태워서 통과 가능 | 레벨 25~ |
| **철공** | 자석에 끌려감 | 레벨 45~ |
| **폭탄 공** | 장애물에 닿으면 폭발하여 주변 파괴 | 레벨 65~ |

---

## 📐 4. 스테이지 설계 (Level Design)

### 4.1 난이도 커브

| 구간 | 레벨 | 핵심 요소 | 목적 |
|------|------|-----------|------|
| **튜토리얼** | 1~10 | 핀 1~2개, 장애물 없음 | 조작법 학습 |
| **초급** | 11~30 | 핀 2~3개, 불/가시 등장 | 순서 퍼즐 이해 |
| **중급** | 31~60 | 타이머 핀, 움직이는 바닥 | 타이밍 + 전략 |
| **상급** | 61~100 | 연쇄 핀, 텔레포트, 특수 공 | 복합 퍼즐 |
| **마스터** | 101+ | 모든 요소 복합 | 도전 요소 |

### 4.2 별점 시스템

- ⭐ **1성**: 스테이지 클리어
- ⭐⭐ **2성**: 제한된 핀 빼기 횟수 이내로 클리어
- ⭐⭐⭐ **3성**: 최소 핀 빼기로 클리어 (Perfect)

### 4.3 보스 스테이지

매 10레벨마다 보스 스테이지가 등장합니다:
- 일반 스테이지보다 크고 복잡한 맵
- 여러 마리의 고양이를 동시에 구출
- 클리어 시 **희귀/전설 고양이** 획득
- 특별한 클리어 연출과 보상

---

## 💰 5. 수익화 전략 (Monetization)

### 5.1 광고 삽입 포인트

| 광고 유형 | 삽입 시점 | 빈도 |
|-----------|-----------|------|
| **전면 광고 (Interstitial)** | 스테이지 클리어 후 (매 3레벨마다) | 높음 |
| **보상형 광고 (Rewarded)** | 실패 시 이어하기 / 힌트 받기 / 추가 공 받기 | 중간 |
| **배너 광고 (Banner)** | 메인 화면 하단 (플레이 중 없음) | 상시 |

### 5.2 광고 정책 (유저 경험 보호)

- 처음 5레벨은 광고 없이 플레이 (신규 유저 이탈 방지)
- 전면 광고는 최소 3레벨 간격으로 제한
- 보상형 광고는 항상 선택적 (강제 시청 금지)
- 한 세션당 최대 광고 노출 횟수 제한 (피로감 방지)

### 5.3 선택적 IAP (인앱 결제)

- **광고 제거**: 일회성 결제로 모든 광고 제거
- **힌트 팩**: 힌트 아이템 N개 묶음
- **특수 공 팩**: 불공/철공 등 특수 공 해금

---

## 🔧 6. 기술 구현 (Technical Implementation)

### 6.1 기술 스택

| 구분 | 기술 |
|------|------|
| **언어** | Kotlin |
| **렌더링** | Android Canvas / SurfaceView |
| **물리 엔진** | dyn4j 5.0.2 (2D 물리 라이브러리) |
| **데이터 저장** | Room DB + SharedPreferences |
| **광고** | Google AdMob SDK |
| **분석** | Firebase Analytics |
| **앱 보안** | Google Play Integrity API |
| **최소 버전** | Android API 24 (Android 7.0) |

### 6.2 프로젝트 패키지 구조

```
com.meowrescue.game
├── game/
│   ├── GameEngine.kt          // 게임 상태 관리 + GameEventListener
│   ├── GameLoop.kt            // 60 FPS 게임 스레드
│   └── Dyn4jPhysicsEngine.kt  // dyn4j 물리 월드 래퍼 (중력, 충돌, 바운스)
├── model/
│   ├── Pin.kt                 // 핀 데이터 클래스 (sealed class)
│   ├── Ball.kt                // 공 데이터 클래스
│   ├── Cat.kt                 // 고양이 데이터 클래스
│   ├── Obstacle.kt            // 장애물 데이터 클래스 (sealed class)
│   ├── Surface.kt             // 플랫폼 데이터 (위치, 크기, 각도)
│   └── util/
│       └── Vector2D.kt        // 가변 2D 벡터 (연산자 오버로드)
├── level/
│   ├── LevelData.kt           // 레벨 데이터 모델
│   └── LevelLoader.kt         // JSON 기반 레벨 로더
├── ui/
│   ├── GameActivity.kt        // 게임 화면 Activity
│   ├── GameView.kt            // SurfaceView 기반 게임 렌더링 + 오버레이
│   ├── MenuActivity.kt        // 메인 메뉴 + 지그재그 레벨맵
│   ├── CollectionActivity.kt  // 고양이 컬렉션 화면
│   └── Theme.kt               // UI 색상 상수 모음
├── ads/
│   └── AdManager.kt           // AdMob 초기화 및 광고 로드/표시
├── data/
│   ├── GameRepository.kt      // 데이터 접근 레이어
│   ├── UserProgressDao.kt     // Room DAO (유저 진행도)
│   └── AppDatabase.kt         // Room Database
└── util/
    └── SoundManager.kt        // SoundPool(SFX 11종) + MediaPlayer(BGM 5종) 사운드 관리
```

### 6.3 핵심 모델 설계 (Kotlin)

```kotlin
// ── 핀 종류 (sealed class 활용) ──
sealed class Pin(
    val position: Vector2D,
    val isRemoved: Boolean = false
) {
    class Normal(pos: Vector2D) : Pin(pos)
    class Timer(pos: Vector2D, val resetSeconds: Float) : Pin(pos)
    class Directional(pos: Vector2D, val direction: Vector2D) : Pin(pos)
    class Chain(pos: Vector2D, val linkedPins: List<Pin>) : Pin(pos)
    class Locked(pos: Vector2D, val unlockCondition: () -> Boolean) : Pin(pos)
}

// ── 공 종류 ──
sealed class Ball(
    val position: Vector2D,
    val velocity: Vector2D,
    val radius: Float
) {
    class Normal(pos: Vector2D) : Ball(pos, Vector2D.ZERO, 15f)
    class Fire(pos: Vector2D) : Ball(pos, Vector2D.ZERO, 15f)
    class Iron(pos: Vector2D) : Ball(pos, Vector2D.ZERO, 18f)
    class Bomb(pos: Vector2D) : Ball(pos, Vector2D.ZERO, 20f)
}

// ── 장애물 종류 ──
sealed class Obstacle(val position: Vector2D, val size: Vector2D) {
    class Fire(pos: Vector2D, s: Vector2D) : Obstacle(pos, s)
    class Spike(pos: Vector2D, s: Vector2D) : Obstacle(pos, s)
    class MovingPlatform(pos: Vector2D, s: Vector2D, val speed: Float) : Obstacle(pos, s)
    class Teleport(pos: Vector2D, s: Vector2D, val target: Vector2D) : Obstacle(pos, s)
    class SwitchBlock(pos: Vector2D, s: Vector2D, var isOn: Boolean) : Obstacle(pos, s)
}
```

### 6.4 물리 엔진 (dyn4j 5.0.2)

dyn4j 2D 물리 라이브러리를 사용하여 정확한 물리 시뮬레이션을 구현합니다:

```kotlin
class Dyn4jPhysicsEngine {
    private val world = World<Body>()

    companion object {
        const val PIXELS_PER_METER = 100.0   // 100px = 1m 좌표 변환
        const val RESTITUTION = 0.4          // 반발 계수
        const val FRICTION = 0.3             // 마찰 계수
    }

    init {
        world.gravity = Vector2(0.0, 9.8)    // Y-down 중력
        world.settings.isContinuousDetectionEnabled = true  // 터널링 방지
    }

    fun step(deltaTime: Double) {
        world.update(deltaTime)
        // 물리 바디 → 게임 엔티티 위치 동기화
    }
}
```

**주요 특성:**
- **좌표 변환**: 100px = 1m (화면 좌표 ↔ 물리 좌표)
- **중력**: 9.8 m/s² (Y-down 좌표계)
- **연속 충돌 감지**: 고속 공의 터널링 방지
- **경사면 물리**: 각도에 따른 자연스러운 슬라이딩

### 6.5 레벨 데이터 형식 (JSON)

```json
{
  "levelId": 1,
  "name": "첫 번째 구출",
  "difficulty": "tutorial",
  "maxPins": 1,
  "stars": {
    "one": 999,
    "two": 2,
    "three": 1
  },
  "balls": [
    { "type": "normal", "x": 200, "y": 100 }
  ],
  "cats": [
    { "x": 200, "y": 700, "catId": "cat_001" }
  ],
  "pins": [
    { "type": "normal", "x": 200, "y": 400 }
  ],
  "obstacles": [],
  "platforms": [
    { "x": 100, "y": 450, "width": 200, "height": 20, "angle": 0 }
  ]
}
```

---

## 🎨 7. UI/UX 설계

### 7.1 화면 구성

| 화면 | 주요 요소 |
|------|-----------|
| **메인 화면** | Meow Rescue 로고, 플레이 버튼, 스테이지 선택, 설정, 고양이 컬렉션 |
| **스테이지 선택** | 월드 맵 형태, 레벨 번호 + 별점 표시, 잠금/해금 상태 |
| **게임 화면** | 퍼즐 영역, 핀 터치 영역, 일시정지/힌트 버튼, 남은 공 수 |
| **클리어 화면** | 별점 표시, 구출한 고양이 애니메이션, 다음 레벨 버튼, 공유 버튼 |
| **실패 화면** | 이어하기(광고) 버튼, 다시하기 버튼, 힌트(광고) 버튼 |
| **컬렉션** | 구출한 고양이 목록, 고양이 스킨 미리보기, 도감 달성률 |

### 7.2 디자인 톤 & 무드

- **전체 분위기**: 밝고 따뜻한 파스텔 톤
- **주요 색상**: 연한 오렌지 (#FFA94D), 하늘색 (#74C0FC), 연한 초록 (#69DB7C)
- **캐릭터 스타일**: 둥글둥글한 귀여운 2D 일러스트
- **폰트**: 둥근 고딕 계열 (영문: Nunito Bold, 한글: 카페24 써라운드)
- **사운드**: 경쾌하고 귀여운 효과음 + 잔잔한 BGM

### 7.3 고양이 컬렉션 시스템

리텐션을 높이는 수집 요소로, 스테이지를 클리어할 때마다 새로운 고양이를 구출/수집합니다:

- 🐱 **일반 고양이 (Common)**: 레벨 클리어 시 자동 획득 — 총 50종
- 🐱 **희귀 고양이 (Rare)**: 별점 3개 달성 시 획득 — 총 30종
- 🐱 **전설 고양이 (Legendary)**: 보스 스테이지 클리어 등 특정 조건 달성 시 획득 — 총 20종

---

## 📅 8. 개발 로드맵 (Development Roadmap)

| 단계 | 기간 | 목표 | 세부 작업 |
|------|------|------|-----------|
| **Phase 1** | 2주 | MVP 프로토타입 | 게임 루프, 물리 엔진, 기본 핀 메카닉 구현 |
| **Phase 2** | 2주 | 10레벨 완성 | 장애물, UI, 사운드, 튜토리얼 제작 |
| **Phase 3** | 2주 | 50레벨 + 광고 | AdMob 연동, 레벨 양산, 특수 핀/공 추가 |
| **Phase 4** | 2주 | 100레벨 + 폴리시 | 테스트, 컬렉션, 성능 최적화, 출시 준비 |

### Phase별 상세 마일스톤

**Phase 1 (MVP)**
- SurfaceView 기반 게임 루프 구현
- 간단한 중력 + 충돌 물리 엔진
- 핀 터치 → 공 굴림 → 고양이 도달 기본 흐름
- 3~5개 테스트 레벨

**Phase 2 (기본 콘텐츠)**
- 불, 가시 장애물 추가
- 메인 메뉴, 스테이지 선택 UI
- 효과음 및 BGM 적용
- 튜토리얼 레벨 10개 완성

**Phase 3 (수익화 + 확장)**
- AdMob 전면/보상형/배너 광고 연동
- 타이머 핀, 움직이는 바닥 등 새 요소
- JSON 기반 레벨 로더로 50레벨 양산
- 별점 시스템 및 진행도 저장 (Room DB)

**Phase 4 (출시 준비)**
- 100레벨 완성
- 고양이 컬렉션 시스템
- 성능 최적화 및 메모리 관리
- Google Play 스토어 등록 준비 (스크린샷, 설명, 아이콘)
- 클로즈드 베타 테스트

---

## 📊 9. KPI 목표

| 지표 | 목표값 | 설명 |
|------|--------|------|
| **D1 리텐션** | 40%+ | 첫날 재방문률 |
| **D7 리텐션** | 15%+ | 7일 후 재방문률 |
| **평균 세션 시간** | 5분+ | 한 번 접속 시 플레이 시간 |
| **eCPM** | $5~15 | 광고 1,000회 노출당 수익 |
| **CPI** | $0.5 이하 | 설치당 획득 비용 |
| **레벨 완료율** | 레벨 10 기준 70%+ | 초반 이탈 방지 지표 |

---

## 🚀 10. 향후 확장 계획

- **데일리 챌린지**: 매일 새로운 특수 스테이지 제공
- **이벤트 스테이지**: 시즌별 테마 스테이지 (할로윈, 크리스마스 등)
- **레벨 에디터**: 유저가 직접 레벨을 만들고 공유하는 기능
- **리더보드**: 최소 핀 빼기 횟수 기록 경쟁
- **iOS 확장**: Kotlin Multiplatform을 활용한 iOS 버전 출시 검토
- **소셜 기능**: 친구와 기록 비교, 스테이지 공유

---

## 📎 부록

### A. 경쟁작 분석

| 게임 | 장점 | Meow Rescue 차별점 |
|------|------|---------------------|
| Pull the Pin | 심플한 조작 | 물리 기반 공 굴림으로 깊이 추가 |
| Cat Defenders | 색상 매칭 + 주차장 퍼즐 | 핀 빼기 + 경로 설계로 직관성 강화 |
| Brain Out | 트릭 퍼즐 | 일관된 메커니즘으로 학습 곡선 완만 |
| Save the Girl | 선택형 퍼즐 | 물리 시뮬레이션으로 리플레이 가치 |

### B. 핵심 성공 요인

1. **30초 안에 재미를 느낄 수 있는 직관적 조작**
2. **레벨마다 새로운 요소 등장으로 지루함 방지**
3. **고양이 컬렉션으로 장기 플레이 동기 부여**
4. **유저 경험을 해치지 않는 절제된 광고 정책**
5. **가벼운 용량과 빠른 로딩 (오프라인 플레이 지원)**

---

*Meow Rescue GDD v1.0 — 2026.03*
*Platform: Android (Kotlin) | Genre: Hyper-Casual Puzzle*

# Meow Rescue

> 그리드 매칭 퍼즐과 턴제 전투가 결합된 로그라이크 게임. 블록을 스왑하여 같은 타입 3개 이상을 맞춰 적들을 무찌르고, 귀여운 고양이들을 구출하세요!

![Platform](https://img.shields.io/badge/Platform-Android-green)
![Language](https://img.shields.io/badge/Language-Kotlin%202.2.0-purple)
![MinSDK](https://img.shields.io/badge/MinSDK-24%20(Android%207.0)-blue)
![Version](https://img.shields.io/badge/Version-v2.0-orange)

## 소개

Meow Rescue는 안드로이드 하이브리드 퍼즐 게임입니다. 5x5 또는 5x6 그리드에서 인접한 블록을 스왑(교환)하여 같은 타입 3개 이상을 맞춰 적들에게 피해를 줍니다. 턴제 전투 방식으로 진행되며, 모든 적을 무찌르면 고양이를 구출하고 다음 스테이지로 진행합니다.

게임은 로그라이크 구조를 따르므로, 각 스테이지마다 새로운 고양이로부터 버프를 받고, 스테이지 클리어 시 릭을 선택하여 능력을 강화할 수 있습니다. 유효한 스왑이 없는 데드락 상태에서는 스테이지당 1회 셔플 기회가 주어집니다. 모든 스테이지는 절차적으로 생성되며, 초기 그리드에 유효한 스왑이 존재함이 보장됩니다.

## 주요 기능

- **스왑 매칭**: 5x5 또는 5x6 그리드에서 인접 블록 스왑으로 3개 이상 매칭
- **턴제 전투**: 플레이어 스왑 → 매칭 → 피해 적용 → 적 공격 → 반복
- **블록 타입**: ATTACK (물리), FIRE (불), WATER (물), HEAL (회복)
- **크기 보너스**: 4개+ 매칭 시 추가 블록당 +15% 피해
- **요소 상성**: 각 적이 특정 속성에 약해하고, 다른 속성에는 저항
- **연쇄 보너스**: 중력으로 인한 추가 매칭 시 피해 +25% (최대 1.5배)
- **로그라이크 진행**: 10스테이지 × 10챕터 (총 100 스테이지)
- **고양이 버프**: 매 스테이지 구출 고양이가 ATTACK_BOOST, HEAL_BOOST, FIRE_BOOST, WATER_BOOST, MAX_HP_UP, DAMAGE_REDUCE 버프 제공
- **릭 시스템**: 스테이지 클리어 시 3개 릭 중 1개 선택으로 특수 능력 획득 (MATCH_BONUS_DAMAGE, CHAIN_MULTIPLIER, HEAL_ON_MATCH, START_SHIELD, EXTRA_TURN_CHANCE)
- **5가지 적 타입**: Slime, Rat, Crow, Snake, Boss Wolf (각기 다른 약점 및 공격 패턴)
- **난이도 스케일링**: 챕터 및 스테이지 진행에 따라 적 체력/공격력 증가
- **데드락 감지 + 셔플**: 유효 스왑 없을 시 자동 감지, 스테이지당 1회 셔플 기회
- **튜토리얼**: Chapter 1 Stage 1 첫 플레이 시 6단계 튜토리얼 (스왑 방법, 블록 설명, 상성 시스템)
- **절차적 생성 + 검증**: 매 스테이지 그리드와 적 조합 자동 생성, 유효 스왑 보장
- **진행도 저장**: Room DB로 회차 상태, 구출 고양이, 고유 통계 영구 저장
- **사운드 시스템**: SoundPool 기반 효과음 (매칭, 공격, 회복) + MediaPlayer 기반 배틀 BGM
- **고양이 컬렉션**: 구출한 고양이 목록 및 버프 정보 열람
- **전투 UI**: 고양이 초상화, "Floor X-Y" 로그라이크 배너, 유물 슬롯, 셔플 잔여 배지, 블록 타입 범례, 적 약점/내성 표시
- **전투 이펙트**: 속성별 색상 데미지 숫자, 콤보 체인 텍스트, 파티클 버스트, 콤보 연동 스크린 셰이크

## 난이도 구성

| 구간 | 스테이지 | 적 개수 | 난이도 요소 |
|------|---------|--------|-----------|
| **Chapter 1** | 1~9 | 1~3 | ATTACK, FIRE, HEAL 블록만 |
| **Chapter 1** | 10 (Boss) | 1 | Boss Wolf (2배 체력, 1.5배 공격력) |
| **Chapter 2+** | 1~9 | 1~3 | WATER 블록 추가, 더 강한 적 |
| **Chapter 3+** | - | - | 5x6 그리드, 모든 블록 타입 |

## 기술 스택

| 구분 | 기술 |
|------|------|
| 언어 | Kotlin 2.2.0 |
| 빌드 | Gradle 9.0.0 + AGP 8.7.3 |
| 렌더링 | Android SurfaceView + Canvas |
| 게임 엔진 | 커스텀 (물리 없이 순수 로직) |
| 데이터베이스 | Room 2.6.1 |
| KSP | 2.2.0-2.0.2 |
| 광고 | Google AdMob SDK |
| JDK | 21 |
| 최소 SDK | API 24 (Android 7.0) |
| 타겟 SDK | API 35 (Android 15) |

## 프로젝트 구조

```
com.meowrescue.game
├── game/
│   └── GameLoop.kt                // 60 FPS 렌더 루프 (애니메이션 기반)
├── model/
│   ├── Block.kt                   // 그리드 블록 (타입, 위치, 가치)
│   ├── Enemy.kt                   // 적 데이터 (HP, 공격력, 약점)
│   ├── GridState.kt               // 전체 그리드 상태
│   ├── BattleState.kt             // 전투 상태 스냅샷
│   ├── CatBuff.kt                 // 고양이 버프 (ATTACK_BOOST 등)
│   ├── Relic.kt                   // 릭 보상
│   ├── Cat.kt                     // 구출 고양이 정보
│   ├── MatchResult.kt             // 매칭 결과
│   └── DamageResult.kt            // 피해 계산 결과
├── engine/
│   ├── GridEngine.kt              // 매칭 탐지, 중력, 캐스케이드
│   ├── BattleEngine.kt            // 턴 상태 머신, 전투 진행
│   ├── BattleTurnPhase.kt         // 턴 단계 enum
│   ├── DamageCalculator.kt        // 피해 계산 (상성, 버프 반영)
│   └── EnemyAI.kt                 // 적 행동 패턴 선택
├── generator/
│   ├── GridGenerator.kt           // 랜덤 그리드 생성
│   ├── StageGenerator.kt          // 스테이지 생성 (그리드 + 적)
│   ├── DifficultyScaler.kt        // 난이도별 스케일링
│   └── SolvabilityVerifier.kt     // 풀이 가능성 검증
├── ui/
│   ├── MenuActivity.kt            // 메인 메뉴 (챕터 선택)
│   ├── BattleActivity.kt          // 전투 화면
│   ├── BattleView.kt              // 그리드 + 적 렌더링
│   ├── BattleHUD.kt               // HP바, 고양이 초상화, 유물 슬롯, 셔플 배지
│   ├── TutorialOverlay.kt         // 튜토리얼 (Ch1-S1)
│   ├── RelicSelectActivity.kt     // 릭 선택 화면
│   ├── RunSummaryActivity.kt      // 회차 결과 화면
│   ├── CollectionActivity.kt      // 고양이 컬렉션
│   ├── render/
│   │   ├── GridRenderer.kt        // 그리드 및 블록 렌더링
│   │   ├── EnemyRenderer.kt       // 적 렌더링 (카드 패널, 약점/내성 표시)
│   │   └── EffectRenderer.kt      // 이펙트 (속성 데미지, 콤보, 파티클)
│   └── Theme.kt                   // UI 색상 상수
├── data/
│   ├── AppDatabase.kt             // Room 데이터베이스
│   ├── UserProgressDao.kt         // 진행도 DAO
│   └── GameRepository.kt          // 데이터 접근 레이어
├── ads/
│   └── AdManager.kt               // AdMob 광고 관리
└── util/
    ├── SoundManager.kt            // 효과음 + BGM
    └── GridConstants.kt           // 그리드 상수
```

## 블록 매칭 메커니즘

### 매칭 방식 (스왑)

- **그리드**: 5x5 또는 5x6 크기
- **블록 타입**: ATTACK, FIRE, WATER, HEAL (+ EMPTY)
- **조건**: 같은 타입의 블록 3개 이상이 상하좌우로 인접하면 매칭됨
- **플레이어 입력**: 블록 탭(선택) → 인접 블록 탭(스왑) → 매칭 발생 시 제거, 아니면 되돌림
- **데드락**: 유효 스왑이 없으면 셔플 기회 제공 (스테이지당 1회)

### 중력 및 캐스케이드

1. **중력**: 블록 제거 후 위의 블록들이 아래로 떨어짐
2. **리필**: 빈 공간은 위에서 새로운 랜덤 블록으로 채워짐
3. **연쇄**: 리필 과정에서 새로운 매칭 발생 → 캐스케이드 (매 레벨마다 피해 +25%)

## 피해 계산

```
기본 피해 = 매칭 블록 수 × 블록 가치

속성 상성:
- 약점 (약한 속성): 1.5배 증가
- 저항 (강한 속성): 0.5배 감소

연쇄 보너스:
- 캐스케이드 깊이 N = (1 + N × 0.25)배 곱하기
- 예: 2단계 연쇄 = 1.5배, 4단계 연쇄 = 1.75배

최종 피해 = 기본 피해 × 상성 배수 × 연쇄 배수 × 버프/릭 배수
```

## 적 타입

| 적 | 약점 | 저항 | 체력 | 공격 | 등장 |
|----|------|------|------|------|------|
| Slime | FIRE | WATER | 낮음 | 낮음 | Ch1~ |
| Rat | WATER | - | 낮음 | 중간 | Ch1~ |
| Crow | ATTACK | FIRE | 중간 | 높음 | Ch2~ |
| Snake | FIRE | ATTACK | 높음 | 중간 | Ch3~ |
| Boss Wolf | - | - | 매우 높음 | 높음 | 매 챕터 Stage 10 |

## 고양이 버프

구출한 고양이로부터 다음 버프 중 하나를 얻습니다:

| 버프 | 효과 |
|------|------|
| ATTACK_BOOST | 모든 공격 피해 +15% |
| FIRE_BOOST | 불 속성 피해 +25% |
| WATER_BOOST | 물 속성 피해 +25% |
| HEAL_BOOST | 회복 효과 +20% |
| MAX_HP_UP | 최대 체력 +10 |
| DAMAGE_REDUCE | 받는 피해 -15% |

## 릭 시스템

스테이지 클리어 후 3개 릭 중 1개를 선택합니다. 선택한 릭은 이후 스테이지에서 계속 유지됩니다.

| 릭 | 효과 |
|----|------|
| MATCH_BONUS_DAMAGE | 모든 매칭에서 피해 +20% |
| CHAIN_MULTIPLIER | 캐스케이드 보너스 +30% (기본 +25%) |
| HEAL_ON_MATCH | 매칭할 때마다 체력 +5 회복 |
| START_SHIELD | 첫 턴에 피해 -30% 방패 |
| EXTRA_TURN_CHANCE | 5% 확률로 추가 턴 획득 |

## 빌드 방법

### 필수 요구사항

- Android Studio 2025.3 이상
- JDK 21 (Android Studio에 내장)
- Android SDK API 34 이상

### APK 빌드

```bash
# Android Studio 내장 JDK 사용
export JAVA_HOME="<Android Studio 경로>/jbr"
export ANDROID_HOME="<Android SDK 경로>"

./gradlew assembleDebug
```

APK 출력: `app/build/outputs/apk/debug/app-debug.apk`

## 개발 로드맵

- [x] Phase 1: 데이터 모델 + 그리드 엔진 (매칭, 중력, 캐스케이드)
- [x] Phase 2: 전투 시스템 (턴 상태 머신, 난이도 생성, 풀이 검증)
- [x] Phase 3: 렌더링 + UI (그리드, 적, 전투 화면)
- [ ] Phase 4: 로그라이크 메타 + 폴리시 (DB 마이그레이션, 릭/버프 선택, 완성)

## 라이선스

All rights reserved.

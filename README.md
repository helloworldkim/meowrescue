# Meow Rescue

> 고양이를 구출하라! 블록을 밀어서 길을 만들고, 갇힌 고양이를 탈출시키는 슬라이딩 퍼즐 게임.

![Platform](https://img.shields.io/badge/Platform-Android-green)
![Language](https://img.shields.io/badge/Language-Kotlin%202.2.0-purple)
![MinSDK](https://img.shields.io/badge/MinSDK-24%20(Android%207.0)-blue)
![Version](https://img.shields.io/badge/Version-v3.0-orange)

## 소개

Meow Rescue는 Rush Hour / Unblock Me 스타일의 슬라이딩 블록 퍼즐 게임입니다. 격자판 위에 놓인 블록들을 밀어서 고양이 블록이 출구로 나갈 수 있도록 길을 만들어주세요!

모든 스테이지는 **시드 기반으로 자동 생성**되며, 진행할수록 격자 크기와 블록 수가 증가하여 난이도가 자연스럽게 올라갑니다. 이동 횟수에 따라 1~3성 별점을 받을 수 있습니다.

## 주요 기능

- **슬라이딩 퍼즐**: 블록을 드래그하여 고양이의 탈출 경로 만들기
- **다방향 출구**: RIGHT, LEFT, TOP, BOTTOM 네 방향으로 고양이 탈출
- **1칸 블록 양방향 이동**: 1칸짜리 블록은 상하좌우 모두 이동 가능
- **자동 레벨 생성**: 시드 기반 절차적 생성으로 무한 스테이지
- **품질 필터**: 단조로운 퍼즐을 자동으로 걸러내어 다양하고 재미있는 퍼즐만 제공
- **난이도 자동 증가**: 5x5 → 6x6 → 7x7 격자, 블록 수 및 최소 이동 횟수 증가
- **별점 시스템**: 최적 이동 횟수 기준 1~3성 평가
- **고양이 컬렉션**: 20마리 고양이를 스테이지 클리어로 해금하고 게임에서 사용
- **탈출 애니메이션**: 벽 열림 → 고양이 슬라이드 → 파티클 이펙트 연출
- **스냅 애니메이션**: 120ms ease-out 블록 스냅 효과
- **Undo / Reset**: 실수해도 되돌리기 가능
- **진행도 저장**: Room DB로 스테이지 클리어 상태 및 별점 영구 저장
- **사운드**: 블록 이동, 고양이 구출, 레벨 클리어, BGM 등 효과음
- **광고**: AdMob 배너 + "Next Stage" 클릭 시 전면 광고 (3스테이지 간격)

## 퍼즐 품질 필터

생성된 퍼즐이 다음 조건에 해당하면 자동으로 거부하고 새로운 시드로 재생성합니다:

| 검사 | 설명 |
|------|------|
| **단일 방향** | 직접 차단 블록이 모두 같은 방향이면 거부 |
| **단일 블록** | 직접 차단 블록이 1개 이하면 거부 (스테이지 6+) |
| **독립 이동** | 차단 블록들이 서로 독립적이면 거부 |

시드 오프셋 캐시로 동일 스테이지 재진입 시 결정적 결과를 보장합니다.

## 고양이 컬렉션

20마리의 고양이를 특정 스테이지 클리어로 해금할 수 있습니다:

| 이름 | 해금 | 이름 | 해금 | 이름 | 해금 | 이름 | 해금 |
|------|------|------|------|------|------|------|------|
| 나비 | Lv.1 | 호랑 | Lv.20 | 봄이 | Lv.40 | 꽃이 | Lv.65 |
| 치즈 | Lv.5 | 까미 | Lv.25 | 여름 | Lv.45 | 하늘 | Lv.70 |
| 모모 | Lv.10 | 별이 | Lv.30 | 가을 | Lv.50 | 바다 | Lv.75 |
| 구름 | Lv.15 | 달이 | Lv.35 | 겨울 | Lv.55 | 무지개 | Lv.80 |
| | | | | 솜이 | Lv.60 | 보석/왕자/공주 | Lv.85~95 |

- 컬렉션 화면에서 해금된 고양이를 선택하면 게임 내 고양이 블록에 반영
- 새 고양이 해금 시 축하 다이얼로그 표시

## 애니메이션

### 블록 스냅
- 드래그 종료 시 가장 가까운 칸으로 120ms ease-out 스냅
- 스냅 중 추가 입력 차단

### 고양이 탈출 시퀀스
1. **벽 열림** (200ms): 출구 벽이 양쪽으로 갈라짐
2. **슬라이드** (300ms): 고양이가 ease-in으로 출구 밖으로 이동
3. **파티클** (500ms): 30개 파스텔 파티클 폭발 효과

## 광고 시스템

| 항목 | 값 |
|------|-----|
| 광고 미노출 구간 | 스테이지 1~5 |
| 전면 광고 간격 | 3 스테이지마다 |
| 세션당 최대 | 10회 |
| 타이밍 | "Next Stage" 버튼 클릭 시 (클리어 직후가 아님) |

## 난이도 구성

| 구간 | 스테이지 | 격자 크기 | 블록 수 | 최소 이동 |
|------|---------|----------|--------|----------|
| **입문** | 1~5 | 5x5 | 3~5 | 2~6 |
| **초급** | 6~15 | 5x5 | 5~8 | 7~10 |
| **중급** | 16~30 | 6x6 | 6~10 | 10~14 |
| **중상급** | 31~50 | 6x6 | 8~12 | 14~18 |
| **상급** | 51~75 | 7x7 | 9~14 | 18~22 |
| **고급** | 76~100 | 7x7 | 10~15 | 22~26 |
| **마스터** | 101+ | 7x7 | 11~16 | 26~30 |

## 기술 스택

| 구분 | 기술 |
|------|------|
| 언어 | Kotlin 2.2.0 |
| 빌드 | Gradle 9.0.0 + AGP 8.7.3 |
| 렌더링 | Android SurfaceView + Canvas (60fps) |
| 퍼즐 엔진 | 커스텀 (BFS 솔버 + 의존성 체인 생성 + 품질 필터) |
| 데이터베이스 | Room 2.6.1 + SharedPreferences |
| KSP | 2.2.0-2.0.2 |
| 광고 | Google AdMob SDK |
| JDK | 21 |
| 최소 SDK | API 24 (Android 7.0) |
| 타겟 SDK | API 35 (Android 15) |

## 프로젝트 구조

```
com.meowrescue.game
├── puzzle/
│   ├── PuzzleGrid.kt              // 격자 상태, 블록 이동, 클리어 판정, BFS 솔버
│   └── PuzzleGenerator.kt         // 자동 레벨 생성 + 품질 필터
├── ui/
│   ├── MenuActivity.kt            // 메인 메뉴 (Play, Collection, Sound 토글)
│   ├── StageSelectActivity.kt     // 스테이지 선택 (4열 그리드, 별점, 잠금)
│   ├── PuzzleActivity.kt          // 게임 진행, 클리어 처리, 고양이 해금, 광고
│   ├── PuzzleView.kt              // SurfaceView 렌더링 + 드래그 + 스냅/탈출 애니메이션
│   ├── CollectionActivity.kt      // 고양이 컬렉션 (4열 그리드, 20마리)
│   └── Theme.kt                   // UI 색상 + 파티클 컬러
├── data/
│   ├── AppDatabase.kt             // Room 데이터베이스
│   ├── UserProgressDao.kt         // 진행도 DAO
│   └── GameRepository.kt          // 데이터 접근 + 고양이 컬렉션 (CatDefinition 20마리)
├── ads/
│   └── AdManager.kt               // AdMob 광고 관리 (배너/전면/보상형)
└── util/
    └── SoundManager.kt            // 효과음 + BGM
```

## 퍼즐 생성 알고리즘

### 의존성 체인 빌더

1. **고양이 배치**: 출구 방향(RIGHT/LEFT/TOP/BOTTOM)에 따라 고양이 블록 배치
2. **직접 차단**: 고양이 경로 위에 블록 배치
3. **간접 차단**: 직접 차단 블록의 이동 경로를 가로막는 블록 배치
4. **중첩 의존성**: 간접 차단 블록을 또 다른 블록이 차단 → 연쇄 의존
5. **BFS 검증**: 최소 이동 횟수가 난이도 임계값 이상인지 확인
6. **품질 필터**: 단조로운 퍼즐 거부 + 시드 오프셋으로 재생성

### 경량 BFS 솔버

- 상태를 IntArray(블록 위치)로 표현 → PuzzleGrid clone() 없이 탐색
- Long 기반 해시로 방문 체크 (String 인코딩 대비 10배+ 빠름)
- 최대 80,000 상태, 깊이 35로 제한하여 모바일 최적화

## 테스트

```bash
./gradlew :app:testDebugUnitTest
```

5개 단위 테스트:
- `stage82IsSolvable` — 스테이지 82 풀이 가능성 검증
- `allDirectionsAppear` — 100개 스테이지에서 4방향 출구 모두 등장
- `oneCellBlocksMoveBothAxes` — 1칸 블록 양방향 이동 검증
- `multiCellBlocksRestrictedToAxis` — 다칸 블록 축 제한 검증
- `sampleStagesSolvable` — 샘플 스테이지 풀이 가능성 검증

## 빌드 방법

### 필수 요구사항

- Android Studio 2025.3 이상
- JDK 21 (Android Studio에 내장)
- Android SDK API 35 이상

### APK 빌드

```bash
export JAVA_HOME="<Android Studio 경로>/jbr"
export ANDROID_HOME="<Android SDK 경로>"

./gradlew assembleDebug
```

APK 출력: `app/build/outputs/apk/debug/app-debug.apk`

## 라이선스

All rights reserved.

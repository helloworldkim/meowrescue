# Meow Rescue

> 고양이를 구출하라! 블록을 밀어서 길을 만들고, 갇힌 고양이를 탈출시키는 슬라이딩 퍼즐 게임.

![Platform](https://img.shields.io/badge/Platform-Android-green)
![Language](https://img.shields.io/badge/Language-Kotlin%202.2.0-purple)
![MinSDK](https://img.shields.io/badge/MinSDK-24%20(Android%207.0)-blue)
![Version](https://img.shields.io/badge/Version-v3.0-orange)

## 소개

Meow Rescue는 Rush Hour / Unblock Me 스타일의 슬라이딩 블록 퍼즐 게임입니다. 격자판 위에 놓인 블록들을 밀어서 고양이 블록이 출구로 나갈 수 있도록 길을 만들어주세요!

모든 스테이지는 **자동으로 생성**되며, 진행할수록 격자 크기와 블록 수가 증가하여 난이도가 자연스럽게 올라갑니다. 이동 횟수에 따라 1~3성 별점을 받을 수 있습니다.

## 주요 기능

- **슬라이딩 퍼즐**: 블록을 드래그하여 고양이의 탈출 경로를 만들기
- **자동 레벨 생성**: 시드 기반 절차적 생성으로 무한 스테이지
- **난이도 자동 증가**: 5x5 → 6x6 → 7x7 격자, 블록 수 및 최소 이동 횟수 증가
- **별점 시스템**: 최적 이동 횟수 기준 1~3성 평가
- **Undo / Reset**: 실수해도 되돌리기 가능
- **로딩 인디케이터**: 퍼즐 생성 중 고양이 로딩 화면 표시
- **진행도 저장**: Room DB로 스테이지 클리어 상태 및 별점 영구 저장
- **사운드**: 블록 이동, 클리어, BGM 등 효과음
- **광고**: AdMob 배너 + 스테이지 간 전면 광고

## 난이도 구성

| 구간 | 스테이지 | 격자 크기 | 블록 수 | 최소 이동 |
|------|---------|----------|--------|----------|
| **입문** | 1~10 | 5x5 | 3~5 | 2~5 |
| **초급** | 11~30 | 5x5 | 4~7 | 3~7 |
| **중급** | 31~60 | 6x6 | 5~9 | 6~10 |
| **상급** | 61~100 | 6x6 | 6~10 | 11~16 |
| **고급** | 101~150 | 7x7 | 7~12 | 17~20 |
| **마스터** | 151+ | 7x7 | 8~14 | 21~22 |

## 기술 스택

| 구분 | 기술 |
|------|------|
| 언어 | Kotlin 2.2.0 |
| 빌드 | Gradle 9.0.0 + AGP 8.7.3 |
| 렌더링 | Android SurfaceView + Canvas (60fps) |
| 퍼즐 엔진 | 커스텀 (BFS 솔버 + 의존성 체인 생성) |
| 데이터베이스 | Room 2.6.1 |
| KSP | 2.2.0-2.0.2 |
| 광고 | Google AdMob SDK |
| JDK | 21 |
| 최소 SDK | API 24 (Android 7.0) |
| 타겟 SDK | API 35 (Android 15) |

## 프로젝트 구조

```
com.meowrescue.game
├── puzzle/
│   ├── PuzzleGrid.kt              // 격자 상태, 블록 이동, 클리어 판정
│   └── PuzzleGenerator.kt         // 자동 레벨 생성 + BFS 솔버
├── ui/
│   ├── MenuActivity.kt            // 메인 메뉴 (Play, Sound 토글)
│   ├── StageSelectActivity.kt     // 스테이지 선택 (4열 그리드, 별점, 잠금)
│   ├── PuzzleActivity.kt          // 게임 진행, 클리어 처리, 일시정지
│   ├── PuzzleView.kt              // SurfaceView 렌더링 + 드래그 터치
│   ├── CollectionActivity.kt      // 고양이 컬렉션
│   └── Theme.kt                   // UI 색상 (파스텔 + 웜 톤)
├── data/
│   ├── AppDatabase.kt             // Room 데이터베이스
│   ├── UserProgressDao.kt         // 진행도 DAO
│   └── GameRepository.kt          // 데이터 접근 레이어
├── ads/
│   └── AdManager.kt               // AdMob 광고 관리
└── util/
    └── SoundManager.kt            // 효과음 + BGM
```

## 퍼즐 생성 알고리즘

### 의존성 체인 빌더

1. **고양이 배치**: 출구 행의 왼쪽에 고양이 블록(가로 2칸) 배치
2. **수직 차단**: 고양이 경로 위에 수직 블록 배치 (직접 차단)
3. **수평 차단**: 수직 블록의 이동 경로를 가로막는 수평 블록 배치
4. **중첩 의존성**: 수평 블록을 또 다른 수직 블록이 차단 → 연쇄 의존
5. **BFS 검증**: 최소 이동 횟수가 난이도 임계값 이상인지 확인

### 경량 BFS 솔버

- 상태를 IntArray(블록 위치)로 표현 → PuzzleGrid clone() 없이 탐색
- Long 기반 해시로 방문 체크 (String 인코딩 대비 10배+ 빠름)
- 최대 30,000 상태, 깊이 25로 제한하여 모바일 최적화

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

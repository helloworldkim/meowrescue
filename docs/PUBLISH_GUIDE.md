# Meow Rescue 출시 가이드

## 1. 개인정보처리방침 호스팅 (GitHub Pages)

### 1-1. GitHub 리포지토리에 push
```bash
git push origin main
```

### 1-2. GitHub Pages 활성화
1. GitHub 리포지토리 페이지 → **Settings** 탭
2. 좌측 메뉴 **Pages** 클릭
3. **Source**: `Deploy from a branch` 선택
4. **Branch**: `main`, 폴더: `/docs` 선택 → **Save**
5. 수 분 후 URL 활성화:
   ```
   https://<username>.github.io/meowrescue/privacy-policy.html
   ```

### 1-3. 개인정보처리방침 수정
`docs/privacy-policy.html`에서 아래 TODO 항목을 수정:
- **시행일**: 실제 출시일로 변경
- **이메일**: 실제 연락처로 변경

---

## 2. 앱 서명 설정

### 2-1. 업로드 키 생성
```bash
keytool -genkey -v -keystore meowrescue-upload.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
```
- 비밀번호, 이름 등 입력
- `meowrescue-upload.jks` 파일을 프로젝트 루트에 저장 (git에 커밋하지 말 것!)

### 2-2. `app/build.gradle.kts`에 서명 설정 추가
```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("../meowrescue-upload.jks")
            storePassword = "YOUR_STORE_PASSWORD"
            keyAlias = "upload"
            keyPassword = "YOUR_KEY_PASSWORD"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
```
> 보안 팁: 비밀번호는 `local.properties`에 저장하고 gradle에서 읽어오는 방식 권장

### 2-3. `.gitignore`에 추가
```
*.jks
*.keystore
```

---

## 3. Release 빌드

### 3-1. AAB (Android App Bundle) 생성
```bash
./gradlew bundleRelease
```
출력: `app/build/outputs/bundle/release/app-release.aab`

### 3-2. APK로 테스트 (선택)
```bash
./gradlew assembleRelease
```

---

## 4. Google Play Console 설정

### 4-1. 앱 만들기
1. [Google Play Console](https://play.google.com/console) 접속
2. **앱 만들기** → 앱 이름: `Meow Rescue`
3. 기본 언어: 한국어 (또는 영어)
4. 앱/게임: **게임** 선택
5. 유료/무료: **무료** 선택

### 4-2. 스토어 등록정보
| 항목 | 값 |
|------|-----|
| 앱 이름 | Meow Rescue |
| 간단한 설명 | 블록을 밀어 고양이를 구출하는 슬라이딩 퍼즐 게임 |
| 자세한 설명 | (게임 특징 상세 기술) |
| 앱 아이콘 | 512x512 PNG |
| 그래픽 이미지 | 1024x500 PNG |
| 스크린샷 | 최소 2장 (폰), 권장 4~8장 |
| 카테고리 | 게임 > 퍼즐 |
| 개인정보처리방침 URL | `https://<username>.github.io/meowrescue/privacy-policy.html` |

### 4-3. 앱 콘텐츠 설문
Play Console → **정책** → **앱 콘텐츠**에서 아래 항목 작성:
- **개인정보처리방침**: URL 입력
- **광고**: 앱에 광고 포함 → 예
- **콘텐츠 등급**: 설문 작성 (폭력성 낮음, 퍼즐 게임)
- **타겟층**: 13세 이상
- **데이터 보안**: 수집 데이터 유형 기입 (광고 ID, 기기 정보)

### 4-4. 인앱 업데이트 우선순위 설정
Play Console에서 릴리스 시 `inAppUpdatePriority`를 설정할 수 있습니다:
```
0~3: FLEXIBLE (유연한 업데이트)
4~5: IMMEDIATE (강제 업데이트)
```
Google Play Developer API 또는 Play Console의 고급 설정에서 지정합니다.

---

## 5. 테스트 트랙 (신규 개발자 필수)

### 5-1. 비공개 테스트 설정
1. Play Console → **테스트** → **비공개 테스트** → **트랙 만들기**
2. AAB 업로드
3. **테스터** 탭 → 이메일 목록으로 20명 이상 등록
4. 테스터에게 **옵트인 링크** 공유
5. **14일 대기** 후 프로덕션 출시 가능

### 5-2. 테스터 모집 방법
- 가족/친구/지인
- 인디게임 커뮤니티 (상호 테스트)
- Reddit r/AndroidGaming, 디스코드 게임 개발 서버

---

## 6. 프로덕션 출시

비공개 테스트 14일 경과 후:
1. Play Console → **프로덕션** → **새 버전 만들기**
2. AAB 업로드 (또는 비공개 테스트 버전 승격)
3. 출시 노트 작성
4. **검토 요청** → Google 심사 (보통 수 시간 ~ 수 일)
5. 승인 후 Play 스토어에 공개

---

## 7. 출시 전 체크리스트

- [ ] `privacy-policy.html` TODO 항목 수정 (날짜, 이메일)
- [ ] GitHub Pages 활성화 및 URL 접근 확인
- [ ] AdMob 실제 앱 ID / 광고 단위 ID 적용
- [ ] 앱 서명 키 생성
- [ ] `isMinifyEnabled = true` 설정
- [ ] `versionCode` / `versionName` 업데이트
- [ ] Release AAB 빌드 및 기기 테스트
- [ ] 앱 아이콘 (512x512) 제작
- [ ] 스크린샷 최소 2장 준비
- [ ] 피처 그래픽 (1024x500) 제작
- [ ] Play Console 앱 콘텐츠 설문 완료
- [ ] 비공개 테스트 20명 + 14일 완료
- [ ] ProGuard 적용 후 앱 정상 동작 확인
- [ ] `.jks` 파일 안전한 곳에 백업 (분실 시 업데이트 불가!)

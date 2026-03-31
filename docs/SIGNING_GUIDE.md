# 앱 서명 키 생성 가이드

## 개요

Google Play에 앱을 배포하려면 **업로드 키**로 AAB/APK에 서명해야 합니다.
이 키를 분실하면 같은 앱으로 업데이트를 올릴 수 없으므로 **반드시 안전하게 백업**하세요.

---

## 1. 키 생성

### 방법 A: 명령줄 (keytool)

```bash
keytool -genkeypair -v \
  -keystore meowrescue-upload.jks \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias upload
```

대화형 입력 항목:
```
키 저장소 비밀번호: (원하는 비밀번호 입력)
비밀번호 확인: (동일하게 입력)
이름과 성: (본인 이름)
조직 단위 이름: (비워두거나 입력)
조직 이름: (비워두거나 입력)
구/군/시: (선택)
시/도: (선택)
국가 코드: KR
맞습니까?: y
```

생성된 `meowrescue-upload.jks` 파일을 **프로젝트 루트**에 저장합니다.

### 방법 B: Android Studio

1. **Build** > **Generate Signed Bundle / APK**
2. **Create new...** 클릭
3. Key store path: 프로젝트 루트에 `meowrescue-upload.jks`로 지정
4. 비밀번호, alias(`upload`), 유효기간(25년 이상) 설정
5. **OK** → 키 생성 완료

---

## 2. 프로젝트에 연결

`local.properties` 파일에 아래 4줄을 추가합니다:

```properties
RELEASE_STORE_FILE=../meowrescue-upload.jks
RELEASE_STORE_PASSWORD=여기에_키스토어_비밀번호
RELEASE_KEY_ALIAS=upload
RELEASE_KEY_PASSWORD=여기에_키_비밀번호
```

> `local.properties`는 `.gitignore`에 포함되어 있어 git에 커밋되지 않습니다.
> `build.gradle.kts`에 서명 설정이 이미 적용되어 있어 위 속성만 추가하면 자동으로 릴리스 서명이 활성화됩니다.

---

## 3. 릴리스 빌드

### AAB (Play Store 업로드용)
```bash
./gradlew bundleRelease
```
출력: `app/build/outputs/bundle/release/app-release.aab`

### APK (직접 설치 테스트용)
```bash
./gradlew assembleRelease
```
출력: `app/build/outputs/apk/release/app-release.apk`

---

## 4. 서명 확인

빌드된 파일의 서명을 확인하려면:

```bash
# AAB 서명 확인
jarsigner -verify -verbose app/build/outputs/bundle/release/app-release.aab

# APK 서명 확인
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

---

## 5. Google Play App Signing

Google Play에 처음 AAB를 업로드하면 **Play App Signing**에 자동 등록됩니다.

- **업로드 키** (우리가 만든 키): AAB 서명에 사용, 분실 시 Play Console에서 재설정 요청 가능
- **앱 서명 키** (Google이 관리): Play Store에서 사용자에게 배포할 때 사용, Google이 안전하게 보관

즉, 업로드 키를 분실해도 Google에 요청하면 새 업로드 키로 교체할 수 있습니다.
단, 처리에 며칠 걸릴 수 있으므로 키를 잘 보관하는 것이 중요합니다.

---

## 6. 키 백업 체크리스트

- [ ] `.jks` 파일을 USB, 클라우드 등 **2곳 이상**에 백업
- [ ] 비밀번호를 별도로 안전하게 기록 (비밀번호 관리자 권장)
- [ ] `.jks` 파일이 git에 커밋되지 않는지 확인 (`git status`로 확인)
- [ ] `local.properties`에 서명 정보가 정확히 입력되었는지 확인

---

## 빠른 참조

| 항목 | 값 |
|------|-----|
| 키스토어 파일 | `meowrescue-upload.jks` (프로젝트 루트) |
| 키 알고리즘 | RSA 2048 |
| 키 별명 (alias) | `upload` |
| 유효기간 | 10000일 (~27년) |
| Gradle 설정 위치 | `local.properties` |
| 빌드 명령어 | `./gradlew bundleRelease` |

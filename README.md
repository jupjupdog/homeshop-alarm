# 홈쇼핑 알리미

TV 홈쇼핑 편성표를 모아 보여 주고, 관심 카테고리 상품 방송이 시작되면 알림을 보내며,
상품을 누르면 네이버쇼핑·다나와·쿠팡·11번가 검색 결과(최저가순)를 같은 화면에서 비교하는 안드로이드 앱.

```
collector/  (Python)   GS샵·CJ온스타일 편성표 수집 → 정제·분류 → schedule.json
  └ GitHub Actions 가 30분마다 실행해서 GitHub Pages 에 게시
android/    (Kotlin)   schedule.json 을 받아 편성표·알림·최저가 비교 화면 제공
```

## 1. 수집기 (collector)

```bash
pip install -r collector/requirements.txt
python -m unittest discover -s collector/tests -t .     # 파서 테스트 (저장된 실제 응답 샘플 사용)
python -m collector.main --out public/schedule.json     # 실제 수집
```

| 채널 | 데이터 출처 (2026-10 확인) |
|---|---|
| GS샵 | `gsshop.com/shop/tv/tvScheduleDetail.gs?today=YYYYMMDD` (HTML 조각) |
| CJ온스타일 | `display.cjonstyle.com/c/rest/tv/tvSchedule?bdDt=YYYYMMDD&broadType=live` (JSON) |

- 채널 추가: `collector/sources/<채널>.py` 에 `fetch()` / `parse()` 를 만들고 `main.py` 의 jobs 에 등록.
- 카테고리는 상품명 키워드로 분류 (`categorize.py`). 이름을 바꾸면 앱의 `model/Models.kt` 의 `Categories` 도 같이 바꿔야 함.
- 사이트 구조가 바뀌면 그 채널만 실패하고 나머지는 계속 수집됨 (`schedule.json` 의 `errors` 확인).
- 각 사이트 이용약관·robots.txt 를 확인하고, 수집 주기를 과하게 줄이지 마세요.

### GitHub Pages 게시
1. 이 폴더를 GitHub 저장소로 올림 (예: `homeshop-alarm`)
2. 저장소 Settings → Pages → Source: **GitHub Actions**
3. Actions 탭에서 `collect-schedule` 을 한 번 수동 실행 (Run workflow)
4. `https://<github-id>.github.io/homeshop-alarm/schedule.json` 이 열리면 성공

> GitHub Actions 서버는 해외에 있어서 일부 쇼핑몰이 접속을 막을 수 있습니다.
> 수집이 계속 실패하면 집 PC에서 `python -m collector.main` 을 주기 실행(작업 스케줄러)하고
> 결과 파일만 올리는 방식으로 바꾸면 됩니다.

## 2. 안드로이드 앱 (android)

1. Android Studio 로 `android/` 폴더를 엽니다 (Gradle 동기화 시 라이브러리 자동 다운로드).
2. `android/gradle.properties` 의 `scheduleUrl` 을 위 GitHub Pages 주소로 바꿉니다.
3. 실행 ▶ (minSdk 26 / targetSdk 35)

| 화면 | 내용 |
|---|---|
| 편성표 | 시간대별 상품 목록, ON AIR 표시, 방송 중·관심만·채널·카테고리 필터 |
| 관심·알림 | 관심 카테고리 선택, 알림 시점(시작/5/10/15/30분 전), 권한 상태 |
| 최저가 비교 | 홈쇼핑 상품 정보 + 검색어 수정 + 쇼핑몰 탭(앱 안 WebView, 최저가순) |

- 동기화: WorkManager 1시간 주기 + 앱 실행 시 + 재부팅 시
- 알림: 방송(채널+시작시각) 단위로 1개. Android 14+ 에서 '정시 알람' 권한이 없으면 몇 분 늦을 수 있음
- 단위 테스트: `./gradlew testDebugUnitTest` (수집기가 만든 `sample_schedule.json` 으로 매핑 검증)

## 3. 다음 단계 아이디어
- 채널 추가: 현대홈쇼핑, 롯데홈쇼핑, NS, 홈앤쇼핑, 공영쇼핑, T커머스(CJ는 `broadType=plus`)
- 최저가 숫자를 앱에 직접 표시: 쿠팡 파트너스 등 제휴 API 연동 (네이버 쇼핑 검색 API는 2026-07-31 종료)
- 카테고리 분류 정확도 개선 (키워드 → 학습 모델 또는 LLM 분류)

## Claude Code 로 이어서 작업할 때
`CLAUDE.md` 참고. 사용한 스킬:
- [dpconde/claude-android-skill](https://github.com/dpconde/claude-android-skill) — 아키텍처·Compose 기준
- [chrisbanes/skills](https://github.com/chrisbanes/skills) — Compose 상태/이펙트, Flow 세부 패턴

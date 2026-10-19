# 홈쇼핑 알리미

TV 홈쇼핑 편성표를 모아 보여 주고, 관심 카테고리 상품 방송이 시작되면 알림을 보내며,
상품을 누르면 네이버쇼핑·다나와·쿠팡·11번가 검색 결과(최저가순)를 같은 화면에서 비교하는 안드로이드 앱.

```
collector/  (Python)   TV 홈쇼핑 7개 채널 편성표 수집 → 정제·분류 → schedule.json
  └ 집 PC 작업 스케줄러가 30분마다 실행 → data 브랜치 push → GitHub Pages 게시
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
| 현대홈쇼핑 | `www.hmall.com/api/hf/dp/v1/main-tv-new/tv-list?brodDt=YYYYMMDD&brodPrrgPage=1~6` (JSON, 4시간 단위) |
| 롯데홈쇼핑 | `www.lotteimall.com/main/scheduleLive.lotte?bdDate=YYYYMMDD` (JSON) |
| NS홈쇼핑 | `mapi.nsmall.com/md/api/v1/display/media/schedule/tv/YYYYMMDD` (JSON) |
| 홈앤쇼핑 | `www.hnsmall.com/display/tvschedule-list?broadDay=YYYYMMDD` (HTML 조각) |
| 공영쇼핑 | `POST www.gongyoungshop.kr/tvshopping/selectScheduleSub.do` `brcStdDate=YYYYMMDD` (JSON) |

- 채널 추가: `collector/sources/<채널>.py` 에 `fetch()` / `parse()` 를 만들고 `main.py` 의 `SOURCES` 에 등록.
- 카테고리는 상품명 키워드로 분류 (`categorize.py`). 이름을 바꾸면 앱의 `model/Models.kt` 의 `Categories` 도 같이 바꿔야 함.
- 사이트 구조가 바뀌면 그 채널만 실패하고 나머지는 계속 수집됨 (`schedule.json` 의 `errors` 확인).
- 각 사이트 이용약관·robots.txt 를 확인하고, 수집 주기를 과하게 줄이지 마세요.

### GitHub Pages 게시 (집 PC에서 수집)
GitHub Actions 서버는 해외에 있어서 GS샵이 0건으로 막힌다(2026-10 확인). 그래서 수집은 집 PC가 한다.

1. 저장소: https://github.com/jupjupdog/homeshop-alarm
2. 가상환경: `python -m venv .venv` → `.venv\Scripts\pip install -r collector/requirements.txt`
3. 한 번 수동 게시: `.venv\Scripts\python scripts\publish_pages.py` (결과는 `logs/publish.log`)
   - `data` 브랜치에 `schedule.json` 만 커밋 1개로 덮어써서 push (`.pages/` 에 worktree)
4. 저장소 Settings → Pages → Source: **Deploy from a branch**, Branch: `data` / `(root)`
5. 작업 스케줄러 `HomeshopAlarm-Publish`: 매시 7분·37분에 `.venv\Scripts\pythonw.exe scripts\publish_pages.py` 실행 (창 없음)
6. `https://jupjupdog.github.io/homeshop-alarm/schedule.json` 이 열리면 성공

- PC가 꺼져 있으면 갱신이 멈추고, 앱은 마지막 편성표를 그대로 쓴다.
- 모든 채널이 실패하면 게시하지 않는다. 한 채널만 0건이어도 `errors` 에 기록된다.
- GitHub Actions(`test.yml`)는 `collector/` 가 바뀔 때 파서 테스트만 돌린다.

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
- 채널 추가(T커머스): CJ온스타일+(`broadType=plus`), 현대홈쇼핑+샵(현대 `brodChnlNm=dtv`), 롯데OneTV(`scheduleOne.lotte`),
  NS샵+(`schedule/shopplus`), GS마이샵, K쇼핑, 신세계라이브쇼핑, SK스토아, 쇼핑엔티, W쇼핑
- 최저가 숫자를 앱에 직접 표시: 쿠팡 파트너스 등 제휴 API 연동 (네이버 쇼핑 검색 API는 2026-07-31 종료)
- 카테고리 분류 정확도 개선 (키워드 → 학습 모델 또는 LLM 분류)

## Claude Code 로 이어서 작업할 때
`CLAUDE.md` 참고. 사용한 스킬:
- [dpconde/claude-android-skill](https://github.com/dpconde/claude-android-skill) — 아키텍처·Compose 기준
- [chrisbanes/skills](https://github.com/chrisbanes/skills) — Compose 상태/이펙트, Flow 세부 패턴

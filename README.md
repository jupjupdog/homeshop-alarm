# 홈쇼핑 알리미

TV 홈쇼핑 편성표를 모아 보여 주고, 관심 카테고리 상품 방송이 시작되면 알림을 보내며,
상품을 누르면 네이버쇼핑·다나와·쿠팡·11번가 검색 결과(최저가순)를 같은 화면에서 비교하는 안드로이드 앱.

```
android/    (Kotlin)   앱이 7개 홈쇼핑 사이트에서 편성표를 직접 수집 → 정제·분류 → 폰 DB
                       → 편성표·알림·최저가 비교 화면  (서버·PC 필요 없음)
collector/  (Python)   같은 파서의 Python 판. 새 채널·사이트 변경을 PC에서 먼저 확인하고
                       실제 응답 샘플(fixtures)을 만드는 용도. 앱 테스트도 이 샘플을 쓴다.
```

## 작동 순서
1. 앱 실행·재부팅 시(마지막 수집 후 2시간 지났을 때), 6시간마다, 새로고침 버튼을 누를 때 `SyncWorker` 가 돈다.
2. `collect/ScheduleCollector` 가 7개 채널 × 오늘·내일을 동시에 받는다 (`collect/sources/*.kt`, 약 5MB·5초).
   한 채널이 실패해도 나머지는 반영하고, 실패한 채널은 기존 캐시를 지킨다. 0건도 실패로 본다.
3. 날짜 경계 중복 제거 → 0원은 가격 없음 → 카테고리 분류(`Categorizer`) → 최저가 검색어(`SearchQuery`).
4. 폰 DB(Room)에 반영하고, 관심 카테고리 방송마다 "시작 N분 전" 알람을 예약한다.
5. 화면은 항상 DB 기준이라 오프라인에서도 마지막 편성표가 보인다.

## 1. Python 수집기 (collector) — 파서 개발·검증용

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

- 채널 추가 순서: ① PC에서 Python 파서(`collector/sources/<채널>.py`)를 만들고 실제 응답을 `collector/tests/fixtures/` 에 저장
  ② 앱에 같은 파서(`android/.../collect/sources/<채널>.kt`)를 옮기고 `ScheduleCollector.ALL_SOURCES` 에 등록
  ③ 양쪽 테스트에 같은 기대값을 넣는다.
- 카테고리 규칙은 `collector/categorize.py` 와 `android/.../collect/Categorizer.kt` 에 똑같이 들어 있다. 한쪽을 고치면 다른 쪽도.
- 사이트 구조가 바뀌면 그 채널만 실패하고 나머지는 계속 수집됨 (`schedule.json` 의 `errors` 확인).
- 각 사이트 이용약관·robots.txt 를 확인하고, 수집 주기를 과하게 줄이지 마세요.

### (예전 방식) GitHub Pages 게시
앱 0.2.0 부터는 쓰지 않는다. 0.1.x 앱은 아래 주소의 schedule.json 을 받았다.
GitHub Actions 서버는 해외에 있어서 GS샵이 0건으로 막혀(2026-10 확인) 집 PC에서 게시했다.

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
2. 실행 ▶ (minSdk 26 / targetSdk 35). 설정할 주소·키 없음.

| 화면 | 내용 |
|---|---|
| 편성표 | 시간대별 상품 목록, ON AIR 표시, 방송 중·관심만·채널·카테고리 필터 |
| 관심·알림 | 관심 카테고리 선택, 알림 시점(시작/5/10/15/30분 전), 권한 상태 |
| 최저가 비교 | 홈쇼핑 상품 정보 + 검색어 수정 + 쇼핑몰 탭(앱 안 WebView, 최저가순) |

- 수집: 6시간 주기 + 앱 실행·재부팅 시(2시간 지났을 때) + 새로고침. 1회 약 5MB라 모바일 데이터 주의
- 알림: 방송(채널+시작시각) 단위로 1개. Android 14+ 에서 '정시 알람' 권한이 없으면 몇 분 늦을 수 있음
- 단위 테스트: `./gradlew testDebugUnitTest` — `collector/tests/fixtures/` 의 실제 응답으로 7개 파서·분류·검색어 검증

## 3. 다음 단계 아이디어
- 채널 추가(T커머스): CJ온스타일+(`broadType=plus`), 현대홈쇼핑+샵(현대 `brodChnlNm=dtv`), 롯데OneTV(`scheduleOne.lotte`),
  NS샵+(`schedule/shopplus`), GS마이샵, K쇼핑, 신세계라이브쇼핑, SK스토아, 쇼핑엔티, W쇼핑
- 최저가 숫자를 앱에 직접 표시: 쿠팡 파트너스 등 제휴 API 연동 (네이버 쇼핑 검색 API는 2026-07-31 종료)
- 카테고리 분류 정확도 개선 (키워드 → 학습 모델 또는 LLM 분류)
- 현대홈쇼핑 응답이 압축 없이 2MB라 데이터 대부분을 차지 — 줄일 방법 찾기

## Claude Code 로 이어서 작업할 때
`CLAUDE.md` 참고. 사용한 스킬:
- [dpconde/claude-android-skill](https://github.com/dpconde/claude-android-skill) — 아키텍처·Compose 기준
- [chrisbanes/skills](https://github.com/chrisbanes/skills) — Compose 상태/이펙트, Flow 세부 패턴

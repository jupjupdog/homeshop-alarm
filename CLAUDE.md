# CLAUDE.md — 홈쇼핑 알리미

## 스킬 설치 (처음 한 번)
```bash
git clone https://github.com/dpconde/claude-android-skill.git ~/.claude/skills/claude-android-skill
# Claude Code 안에서:
/plugin marketplace add chrisbanes/skills
/plugin install chrisbanes-skills@chrisbanes-skills
```

## 구조
- `collector/` Python 수집기. 채널별 파서는 `collector/sources/`. 출력 형식은 `collector/models.py`.
- `android/` 단일 모듈 앱 (`com.homeshop.alarm`). 레이어: `data/`(remote·local·repository) → `ui/`(Compose + ViewModel), `notify/`(알람), `work/`(동기화).
- 의존성 주입은 `HomeshopApp.kt` 의 `AppContainer` (수동 DI). 모듈이 커지면 Hilt 로 이전.

## 규칙
- 로컬 DB(Room)가 화면의 기준. 네트워크는 DB 갱신만 한다 (offline-first).
- 카테고리 이름은 `collector/categorize.py` 와 `android/.../model/Models.kt` 가 반드시 같아야 한다 (단위 테스트로 확인).
- 파서를 고칠 때는 `collector/tests/fixtures/` 에 실제 응답 샘플을 저장하고 테스트부터 갱신한다.
- `schedule.json` 형식을 바꾸면 `version` 을 올리고 `data/remote/ScheduleApi.kt` DTO 도 같이 바꾼다.

## 명령
- 편성표 게시(집 PC, 작업 스케줄러 `HomeshopAlarm-Publish` 가 30분마다 실행): `.venv\Scripts\python scripts\publish_pages.py` → `data` 브랜치 → Pages
- 수집기 테스트: `python -m unittest discover -s collector/tests -t .`
- 앱 테스트: `cd android && ./gradlew testDebugUnitTest`
- 앱 빌드: `cd android && ./gradlew assembleDebug`

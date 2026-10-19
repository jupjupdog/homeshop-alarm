# CLAUDE.md — 홈쇼핑 알리미

## 스킬 설치 (처음 한 번)
```bash
git clone https://github.com/dpconde/claude-android-skill.git ~/.claude/skills/claude-android-skill
# Claude Code 안에서:
/plugin marketplace add chrisbanes/skills
/plugin install chrisbanes-skills@chrisbanes-skills
```

## 구조
- `android/` 단일 모듈 앱 (`com.homeshop.alarm`). **앱이 홈쇼핑 사이트에서 편성표를 직접 수집한다.**
  레이어: `collect/`(사이트별 파서·분류·검색어) → `data/`(local·repository) → `ui/`(Compose + ViewModel), `notify/`(알람), `work/`(동기화).
- `collector/` 같은 파서의 Python 판. PC에서 사이트를 조사·검증하고 실제 응답 샘플(`collector/tests/fixtures/`)을 만드는 곳.
  앱 단위 테스트도 이 fixtures 를 그대로 쓴다 (`build.gradle.kts` 의 test resources).
- 의존성 주입은 `HomeshopApp.kt` 의 `AppContainer` (수동 DI). 모듈이 커지면 Hilt 로 이전.

## 규칙
- 로컬 DB(Room)가 화면의 기준. 네트워크는 DB 갱신만 한다 (offline-first).
- 파서·분류 규칙은 Python(`collector/`)과 Kotlin(`android/.../collect/`)에 같은 내용으로 두 벌 있다. 한쪽을 고치면 다른 쪽도 고치고,
  양쪽 테스트에 같은 기대값을 넣는다. 카테고리 이름은 `model/Models.kt` 의 `Categories` 와도 같아야 한다 (CategorizerTest).
- 파서를 고칠 때는 `collector/tests/fixtures/` 에 실제 응답 샘플을 저장하고 테스트부터 갱신한다.
- 수집 1회 ≈ 5MB. 수집 주기를 늘리기 전에 데이터 사용량을 고려한다 (`work/SyncWorker.kt`).

## 명령
- Python 수집기 테스트: `python -m unittest discover -s collector/tests -t .` / 실제 수집: `python -m collector.main --out public/schedule.json`
- 앱 테스트: `cd android && ./gradlew testDebugUnitTest`
- 앱 빌드: `cd android && ./gradlew assembleDebug`

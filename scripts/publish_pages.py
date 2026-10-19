r"""집 PC에서 편성표를 수집해 GitHub Pages(data 브랜치)에 게시한다.

GitHub Actions(해외 서버)에서는 GS샵이 막혀서, 작업 스케줄러가 이 스크립트를 30분마다 실행한다.
    .venv\Scripts\pythonw.exe scripts\publish_pages.py

- data 브랜치는 schedule.json 만 담고, 매번 커밋 1개로 덮어쓴다(--amend + force push).
- 저장소의 .pages/ 에 data 브랜치 worktree 를 두므로 main 작업 폴더는 건드리지 않는다.
- 모든 채널이 실패하면 게시하지 않는다(앱은 마지막 편성표를 그대로 쓴다).
"""
from __future__ import annotations

import json
import shutil
import subprocess
import sys
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
WORKTREE = ROOT / ".pages"
LOG = ROOT / "logs" / "publish.log"
BRANCH = "data"
NO_WINDOW = getattr(subprocess, "CREATE_NO_WINDOW", 0)


def log(msg: str) -> None:
    LOG.parent.mkdir(exist_ok=True)
    with LOG.open("a", encoding="utf-8") as f:
        f.write(f"{datetime.now():%Y-%m-%d %H:%M:%S} {msg}\n")


def run(*args: str, cwd: Path = ROOT, check: bool = True) -> subprocess.CompletedProcess:
    p = subprocess.run(args, cwd=cwd, capture_output=True, text=True, encoding="utf-8",
                       errors="replace", creationflags=NO_WINDOW)
    if check and p.returncode != 0:
        raise RuntimeError(f"{' '.join(args)} 실패({p.returncode}): {p.stderr.strip() or p.stdout.strip()}")
    return p


def ensure_worktree() -> None:
    if (WORKTREE / ".git").exists():
        return
    has_remote = run("git", "ls-remote", "--exit-code", "--heads", "origin", BRANCH, check=False).returncode == 0
    if has_remote:
        run("git", "fetch", "origin", BRANCH)
        run("git", "worktree", "add", "-B", BRANCH, str(WORKTREE), f"origin/{BRANCH}")
    else:  # 처음 한 번: 빈 data 브랜치 생성
        run("git", "worktree", "add", "--detach", str(WORKTREE))
        run("git", "switch", "--orphan", BRANCH, cwd=WORKTREE)


def main() -> int:
    out = ROOT / "public" / "schedule.json"
    p = run(sys.executable.replace("pythonw.exe", "python.exe"), "-m", "collector.main",
            "--out", str(out), "--days", "2", check=False)
    for line in (p.stdout + p.stderr).splitlines():
        if line.startswith(("[ok]", "[fail]", "saved")):
            log(line)
    if p.returncode != 0:
        log("수집 실패 - 게시하지 않음")
        return 1

    ensure_worktree()
    shutil.copyfile(out, WORKTREE / "schedule.json")
    (WORKTREE / ".nojekyll").touch()           # Pages 가 Jekyll 처리 없이 그대로 게시
    run("git", "add", "schedule.json", ".nojekyll", cwd=WORKTREE)
    generated = json.loads(out.read_text(encoding="utf-8"))["generatedAt"]
    first = run("git", "rev-parse", "--verify", "HEAD", cwd=WORKTREE, check=False).returncode != 0
    run("git", "commit", "-q", *([] if first else ["--amend"]), "-m", f"schedule {generated}", cwd=WORKTREE)
    run("git", "push", "-q", "--force", "origin", BRANCH, cwd=WORKTREE)
    log(f"게시 완료 ({generated})")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as e:  # noqa: BLE001 - 창 없이 실행되므로 로그로 남긴다
        log(f"오류: {e}")
        sys.exit(1)

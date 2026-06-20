---
description: Run the local security pipeline — vulnerability scan followed by remediation report.
---

# /run-pipeline — Local Security Pipeline

Run the full local security pipeline against the current Spring Boot
workspace, **entirely on this machine** — no CI, no remote. The user
is testing the workflow locally.

## Goal

Produce three files in `.claude/reports/`:

1. `SECURITY_ASSESSMENT_REPORT.md` — written by the `vulnerability-scanner` agent.
2. `SECURE_REMEDIATION_REPORT.md` — written by the `remediation-agent` agent.
3. `GIT_PUSH_REPORT.md` — written by the `git-agent` agent (Step 3
   below). The two security reports are tracked in git; the push
   report is local-only.

The pipeline runs in three stages — **scan → remediate → push** —
each driven by a dedicated subagent. Once the user invokes
`/run-pipeline`, **no further manual action is required** until the
human developer reviews the pushed branch and merges to `main` (the
one and only manual step in the workflow). The user does not run
`git add`, `git commit`, or `git push` themselves at any point.

The scanner runs first; the remediation agent runs **only if the
scanner succeeds**; the git-agent runs **only if the remediation
build is green**. The scanner does not modify any source files. The
remediation agent **does** modify source files (applying the secure
replacements) but never commits. The git-agent commits and pushes
to a new numbered branch off `feature/safe-backup` — it **never
merges to `main`**. The developer's manual merge step is the only
human action that touches `main`. **The three report files are
overwritten on every run** — never merged, appended, or preserved.

## Required Setup

Before launching, verify these paths exist (use `Bash` with `ls` or
`Glob`):

- `.claude/agents/vulnerability-scanner.md`
- `.claude/agents/remediation-agent.md`
- `.claude/agents/git-agent.md`
- `.claude/reports/` (create it with `mkdir -p` if missing)

If any required agent file is missing, stop and tell the user which one.

## Step 1 — Run the Vulnerability Scanner

Launch the `vulnerability-scanner` subagent (use the `Agent` tool with
`subagent_type: "general-purpose"` and the role from
`.claude/agents/vulnerability-scanner.md`).

Pass this exact prompt to the subagent:

> Perform a comprehensive static security review of the Spring Boot
> workspace at the current repo root. Do **not** modify any source code.
> Write the report to **`.claude/reports/SECURITY_ASSESSMENT_REPORT.md`**
> (override of your default repo-root path — the user has chosen
> `.claude/reports/` for this local run). After writing, confirm the
> file exists on disk and report its absolute path.

Wait for the subagent to finish. Then verify the file exists:

```
test -f .claude/reports/SECURITY_ASSESSMENT_REPORT.md && echo OK || echo MISSING
```

If the file is missing, **stop the pipeline** and report the failure to
the user. Do not run Step 2.

## Step 2 — Run the Remediation Agent

Launch the `remediation-agent` subagent (use the `Agent` tool with
`subagent_type: "general-purpose"` and the role from
`.claude/agents/remediation-agent.md`).

Pass this exact prompt to the subagent:

> Read `.claude/reports/SECURITY_ASSESSMENT_REPORT.md` as the source of
> truth. Do **not** re-scan the codebase for new findings.
>
> **Direct-edit contract:** you are fully authorized by the pipeline
> to `Edit` every source/config file the assessment report references.
> **Do not ask the user or developer for permission, confirmation, or
> clarification** before applying a fix — decide, edit, and report.
> Treat the assessment report as your authorization to make every
> change listed in it. The only reason to skip an `Edit` is that
> applying it would break the build (see the Build-Verification
> Contract below); if so, skip it, mark it `Skipped — due to this
> breaking` with the compiler error quoted verbatim, and document
> the unblock action in the report. Do not stop to ask which
> approach to take.
>
> For each finding, **apply the secure replacement to the actual
> source file** using the `Edit` tool (read the file first to confirm
> the snippet, then `Edit` with `replace_all: false`). Use the
> Remediation Cookbook in your agent file as the default for each
> class of finding (SQLi, XSS, CSRF, authN, authZ, secrets, crypto,
> input validation, file upload, error handling, dependency security).
>
> **Build-verification contract (mandatory — the build must not break
> at any point):**
> 1. Detect the build tool (`pom.xml` → Maven, `build.gradle*` →
>    Gradle) before any edit.
> 2. After every `Edit`, run `mvn -B -q compile test-compile` (or
>    `./gradlew --no-daemon -q compileJava compileTestJava`). If it
>    fails, attempt to **repair your own edit** (add missing imports,
>    fix type mismatches, propagate the change to dependent callers)
>    — the build must be green before you move to the next fix.
>    Cap at 3 repair attempts per finding and 20 total across the run.
> 3. **Never start the application.** Do not run `mvn spring-boot:run`,
>    `java -jar`, `gradlew bootRun`, or any command that boots the app.
>    Compile / test-compile only.
> 4. If you exhaust the repair budget or the project cannot be made to
>    compile, run `git checkout -- .` to revert **all** edits, verify
>    the tree is clean with `git status --porcelain`, and report every
>    finding as **Status: Skipped — due to this breaking** with the
>    compiler error quoted in *Explanation of Change*. The working tree
>    must match the pre-run state. The working tree is always either
>    green or unchanged — never left in a broken state.
> 5. If a finding's vulnerable snippet cannot be located in the working
>    tree, or the fix would be ambiguous / behavior-breaking (e.g.
>    BCrypt migration invalidating existing plaintext passwords), skip
>    the edit and document it in *Residual Risks* — do not guess, do
>    not ask the user.
>
> After applying (or reverting) edits, **do not commit and do not
> push.** All changes stay in the working tree for human review via
> `git diff`.
>
> Then produce `SECURE_REMEDIATION_REPORT.md` at
> **`.claude/reports/SECURE_REMEDIATION_REPORT.md`** with:
> - The per-finding schema and top-level sections defined in your
>   agent file (Remediation Summary, **Changes Made**, **Changes That
>   Remained — Due To Build Breakage**, Files Referenced, Vulnerability
>   Remediations, Security Improvements, Residual Risks, Secure Coding
>   Recommendations).
> - A `Build Impact` field on every finding (none / broke-the-build /
>   skipped-without-edit).
> - An explicit `# Changes Made` bullet list (one per Applied finding)
>   and an explicit `# Changes That Remained — Due To Build Breakage`
>   bullet list (one per `Skipped — due to this breaking` finding,
>   each with the compiler error and the unblock action). These two
>   sections are what the human reviewer reads first.
> - **Always overwrite this file** — never merge, append, or preserve
>   prior contents. Every finding must report **Status: Applied**, or
>   **Status: Skipped — see Residual Risks**, or **Status: Skipped —
>   due to this breaking**, and every Applied finding must list the
>   file path that was edited. The `# Remediation Summary` must lead
>   with the build status (`Build verified: mvn compile test-compile
>   passed` or `Build verified: failed — all edits reverted`).
>
> Allowed tools: `Read`, `Glob`, `Grep`, `Edit` (only on source/config
> files referenced in the assessment report), `Write` (only for
> `.claude/reports/SECURE_REMEDIATION_REPORT.md`), and `Bash` (only
> for: build-tool detection, the compile-check command, `git status
> --porcelain`, and `git checkout -- .` / per-file revert when the
> build cannot be repaired). Do not run the application, do not
> commit or push, do not modify `.claude/` or `.gitignore`, and do
> not write outside `.claude/reports/`. Do not prompt the user for
> permission to edit — your authorization is the assessment report
> itself.
>
> After writing, confirm the file exists on disk and report its
> absolute path, the build status, the count of Applied vs Skipped
> findings (with the two Skip categories separated), a one-line
> summary of the changes made, a one-line summary of the changes
> that remained (or *none*), and the list of files you edited
> (empty if all Skipped).

Wait for the subagent to finish. Then verify both files exist.

## Step 3 — Run the Git Agent (automated commit + push)

Launch the `git-agent` subagent (use the `Agent` tool with
`subagent_type: "general-purpose"` and the role from
`.claude/agents/git-agent.md`).

**Step 3 is always triggered** once Step 2 has finished — the
pipeline does not pause to ask the user for permission to commit
or push. The git-agent's internal pre-flight (in
`.claude/agents/git-agent.md`) is the one place the build status
is checked, and the git-agent runs `mvn -B -q compile test-compile`
on the new branch itself before committing. If the build is red
on the new branch, the git-agent aborts the push cleanly and
reports the reason in `GIT_PUSH_REPORT.md` — the pipeline then
surfaces that abort reason to the user in Step 4. Do **not** ask
the user "should I push?" — just launch the subagent.

If the remediation report's `# Remediation Summary` does not lead
with `Build verified: … passed`, the git-agent's pre-flight will
abort on the build check and report the failure — the pipeline
does not pre-empt that decision itself. The user will see the
abort reason in the Step 4 report and can re-run after the build
is fixed.

Pass this exact prompt to the subagent:

> The remediation agent has finished and the build is green. Push
> the working-tree changes to a new safe-backup branch.
>
> **Pre-flight (do not skip any of these):**
> 1. `git rev-parse --abbrev-ref HEAD` must return
>    `feature/safe-backup`. If on any other branch, abort.
> 2. `test -f .git/MERGE_HEAD` must be false. If a merge is in
>    progress, abort.
> 3. `Read` `.claude/reports/SECURE_REMEDIATION_REPORT.md` and
>    confirm the build status is `Build verified: mvn compile
>    test-compile passed` (or the Gradle equivalent). If it reads
>    `Build verified: failed — all edits reverted`, abort.
> 4. `git status --porcelain` must be non-empty. If the working
>    tree is already clean, abort with a friendly "nothing to
>    push" message — no empty branch.
> 5. `git rev-parse --abbrev-ref --symbolic-full-name @{u}` must
>    return `origin/feature/safe-backup`. If upstream tracking
>    is missing, abort.
>
> **Compute the next push branch name:**
> ```bash
> git fetch origin --prune
> git ls-remote --heads origin 'feature/safe-backup_*_time_of_push'
> ```
> Parse the refs, take the max `<N>` from
> `feature/safe-backup_(\d+)_time_of_push`, and set
> `N = max + 1` (default `N = 1`). The new branch is
> `feature/safe-backup_<N>_time_of_push`. If a local branch with
> that name already exists from a previous aborted run, delete it:
> `git branch -D feature/safe-backup_<N>_time_of_push`.
>
> **Create the branch, stage, verify, commit, push:**
> 1. `git checkout -b feature/safe-backup_<N>_time_of_push`
> 2. `git add -A` then
>    `git add -f .claude/reports/SECURITY_ASSESSMENT_REPORT.md .claude/reports/SECURE_REMEDIATION_REPORT.md`
>    (the reports are tracked in git even though the rest of
>    `.claude/reports/` is ignored).
> 3. `git status --short` and `git diff --cached --stat` to sanity
>    check the staged set against the remediation report's
>    `# Files Referenced` table. Abort if anything looks wrong.
> 4. Re-run `mvn -B -q compile test-compile` (or Gradle
>    equivalent) on the new branch. If it fails, delete the local
>    branch, switch back to `feature/safe-backup`, and abort —
>    **never push a red build.**
> 5. `git commit -m "Safety backup push #<N> — <summary>" -m "<body>"`
>    where the body lists the Applied findings pulled from the
>    remediation report's `# Changes Made` section, the build
>    status, the base branch, and the manual-merge reminder.
> 6. `git push -u origin feature/safe-backup_<N>_time_of_push`.
>    **Never force-push.** If the push is rejected, abort and
>    report the exact error.
> 7. `git checkout feature/safe-backup` so the next run starts
>    from the same base.
>
> **Write `.claude/reports/GIT_PUSH_REPORT.md`** (local-only, not
> tracked) with: base branch, push branch, remote, commit SHA, build
> status, files pushed, manual-merge reminder.
>
> **Hard rules:** never merge to `main` or `master`. Never force-push.
> Never push a red build. Never push without `git push` permission —
> if the harness denies it, abort cleanly and tell the user the exact
> branch name so they can run `git push -u origin <branch>`
> themselves.
>
> After writing the report, confirm the file exists on disk and
> report back: the push branch name, the remote URL, the commit
> SHA, the count of files pushed, the build status that was
> verified pre-push, and an explicit reminder that the agent did
> not merge to `main` (the developer reviews and merges manually).

Wait for the subagent to finish. Verify the push branch exists on
the remote:

```bash
git ls-remote --heads origin "feature/safe-backup_${N}_time_of_push"
```

If the subagent aborted (permission denied, build broke on new
branch, push rejected, etc.), **do not fail the pipeline** —
surface the abort reason to the user as part of the Step 4 report.
The scan + remediation reports are still valid output.

## Step 4 — Report Results

Tell the user:

- Absolute path of `.claude/reports/SECURITY_ASSESSMENT_REPORT.md`
- Absolute path of `.claude/reports/SECURE_REMEDIATION_REPORT.md`
- Absolute path of `.claude/reports/GIT_PUSH_REPORT.md` (only if
  Step 3 ran)
- **Build status** from the remediation report (`Build verified:
  passed` or `Build verified: failed — all edits reverted`).
- Top-line counts from the remediation report, with the two Skip
  categories shown separately (e.g. *12 findings: 9 Applied, 2
  Skipped — see Residual Risks, 1 Skipped — due to this breaking* —
  broken down by severity).
- One-line summary of the remediation report's `# Changes Made`
  section (what the agent actually edited).
- One-line summary of the remediation report's `# Changes That
  Remained — Due To Build Breakage` section, or *None* if empty.
  Each entry must include the compiler error and the unblock action
  the human reviewer needs to take.
- **Push summary** (if Step 3 ran): branch name
  (`feature/safe-backup_<N>_time_of_push`), commit SHA, file
  count, remote URL. Or, if Step 3 aborted, the exact reason and
  the unblock action.
- Any items the remediation agent flagged in *Residual Risks* (so
  the user knows what still needs human action).
- Explicit reminder that the two security reports are tracked in
  git; the push report is local-only.
- **Final manual step** for the developer: review the pushed
  branch locally (`git fetch origin && git checkout
  feature/safe-backup_<N>_time_of_push`) and merge to `main` when
  ready. The pipeline does not do this.

## Guardrails

- The **scanner** is strictly report-only: it must not edit any source
  file. If it tries to, abort the step and surface the violation.
- The **remediation agent** is allowed to `Edit` source/config files
  **only** those that the assessment report references as affected
  files. It must never run git commands, must never write outside
  `.claude/reports/`, and must never touch `.claude/` or `.gitignore`.
  If it edits a file that was not referenced in the assessment report,
  abort and surface the violation.
- The **remediation agent must not ask the user or developer for
  permission, confirmation, or clarification** before editing. The
  assessment report is its authorization. The only acceptable reasons
  to skip an `Edit` are (a) it would break the build (after exhausting
  the per-finding repair budget), (b) the vulnerable snippet is no
  longer in the working tree, or (c) the fix would change a public API
  contract — and in every case the agent must record the reason in
  the report. If the agent stops mid-run to ask the user a question,
  abort and surface the violation.
- The **remediation agent must never start the application.** Allowed
  Bash commands are limited to: build-tool detection, the
  compile-check (`mvn -B -q compile test-compile` or Gradle
  equivalent), `git status --porcelain`, and `git checkout -- .`
  (or per-file revert) when the build cannot be repaired. Any
  `mvn spring-boot:run`, `java -jar`, `gradlew bootRun`, or similar
  app-start command is a hard violation — abort and surface it.
- If the build cannot be repaired, the **remediation agent must revert
  all edits** with `git checkout -- .` so the working tree is unchanged,
  and the report must reflect that every finding is **Skipped — due to
  this breaking**.
- **Both report files are overwritten on every run.** The scanner
  writes `SECURITY_ASSESSMENT_REPORT.md` from scratch; the
  remediation agent writes `SECURE_REMEDIATION_REPORT.md` from
  scratch. No append, merge, or preservation of prior contents.
- If either subagent fails or returns an error, stop the pipeline and
  report the exact error to the user. Do **not** continue to the next
  step.
- The three report files are overwritten on every run. The git-agent
  commits and pushes a new numbered branch off `feature/safe-backup`
  whenever the build is green; that push is the pipeline's normal
  outcome, not an exception. After a successful run, the user is
  expected to review the pushed branch (`git fetch origin && git
  checkout feature/safe-backup_<N>_time_of_push`) and merge to
  `main` manually.

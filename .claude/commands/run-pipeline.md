---
description: Run the local security pipeline — vulnerability scan → remediation → git push. Fully automated, no permission prompts.
---

# /run-pipeline — Local Security Pipeline (Fully Automated)

Run the full local security pipeline against the current Spring Boot
workspace, **entirely on this machine**. The user has explicitly
opted into **full automation**: no permission prompts, no
confirmation steps, no "should I push?" questions — the pipeline
runs end-to-end and reports back.

## Goal

Produce three files in `.claude/reports/` and a new branch on
`origin`:

1. `SECURITY_ASSESSMENT_REPORT.md` — written by the
   `vulnerability-scanner` agent.
2. `SECURE_REMEDIATION_REPORT.md` — written by the
   `remediation-agent` agent.
3. `GIT_PUSH_REPORT.md` — written by the `git-agent`.
4. A new branch `feature/safe-backup_<N>_<TIMESTAMP>` pushed to
   `origin` (manual merge to `main` by the human reviewer only).

The pipeline is **strictly ordered**:

```
vulnerability-scanner  →  remediation-agent  →  git-agent
       (Step 1)              (Step 2)              (Step 3)
```

Each step only runs if the previous one succeeded. No step asks the
user for permission.

## Required Setup

Before launching, verify these paths exist (use `Bash` with `ls` or
`Glob`):

- `.claude/agents/vulnerability-scanner.md`
- `.claude/agents/remediation-agent.md`
- `.claude/agents/git-agent.md`
- `.claude/reports/` (create it with `mkdir -p` if missing)

If any required agent file is missing, stop and tell the user which
one.

## Operating Mode — Fully Automated (No Prompts)

**Do not prompt the user between steps.** Do not ask "the build is
green, should I push?" Do not ask "the remediation report is ready,
review now?" Run the steps in order; report back at the end.

The bash commands needed at each step are pre-allowed in
`.claude/settings.local.json`. Use those patterns verbatim. If a
specific command is denied by the harness, that step aborts — write
the failure into the report for that step, then continue to the next
reportable stage.

## Step 1 — Run the Vulnerability Scanner

Launch the `vulnerability-scanner` subagent (use the `Agent` tool with
`subagent_type: "general-purpose"` and the role from
`.claude/agents/vulnerability-scanner.md`).

Pass this exact prompt to the subagent:

> Perform a comprehensive static security review of the Spring Boot
> workspace at the current repo root. Do **not** modify any source
> code. Write the report to
> **`.claude/reports/SECURITY_ASSESSMENT_REPORT.md`** (override of
> your default repo-root path — the user has chosen
> `.claude/reports/` for this local run). After writing, confirm the
> file exists on disk and report its absolute path.

Wait for the subagent to finish. Then verify the file exists:

```
test -f .claude/reports/SECURITY_ASSESSMENT_REPORT.md && echo OK || echo MISSING
```

If the file is missing, **stop the pipeline** and report the failure
to the user. Do not run Step 2.

## Step 2 — Run the Remediation Agent

Launch the `remediation-agent` subagent (use the `Agent` tool with
`subagent_type: "general-purpose"` and the role from
`.claude/agents/remediation-agent.md`).

Pass this exact prompt to the subagent:

> Read `.claude/reports/SECURITY_ASSESSMENT_REPORT.md` as the source
> of truth. Do **not** re-scan the codebase for new findings.
>
> **Direct-edit contract:** you are fully authorized by the pipeline
> to `Edit` every source/config file the assessment report
> references. **Do not ask the user or developer for permission,
> confirmation, or clarification** before applying a fix — decide,
> edit, and report. Treat the assessment report as your authorization
> to make every change listed in it. The only reason to skip an
> `Edit` is that applying it would break the build (see the
> Build-Verification Contract below); if so, skip it, mark it
> `Skipped — due to this breaking` with the compiler error quoted
> verbatim, and document the unblock action in the report. Do not
> stop to ask which approach to take.
>
> For each finding, **apply the secure replacement to the actual
> source file** using the `Edit` tool (read the file first to confirm
> the snippet, then `Edit` with `replace_all: false`). Use the
> Remediation Cookbook in your agent file as the default for each
> class of finding (SQLi, XSS, CSRF, authN, authZ, secrets, crypto,
> input validation, file upload, error handling, dependency
> security).
>
> **Build-verification contract (mandatory — the build must not
> break at any point):**
> 1. Detect the build tool (`pom.xml` → Maven, `build.gradle*` →
>    Gradle) before any edit.
> 2. After every `Edit`, run `mvn -B -q compile test-compile` (or
>    `./gradlew --no-daemon -q compileJava compileTestJava`). If it
>    fails, attempt to **repair your own edit** (add missing imports,
>    fix type mismatches, propagate the change to dependent callers)
>    — the build must be green before you move to the next fix.
>    Cap at 3 repair attempts per finding and 20 total across the
>    run.
> 3. **Never start the application.** Do not run
>    `mvn spring-boot:run`, `java -jar`, `gradlew bootRun`, or any
>    command that boots the app. Compile / test-compile only.
> 4. If you exhaust the repair budget or the project cannot be made
>    to compile, run `git checkout -- .` to revert **all** edits,
>    verify the tree is clean with `git status --porcelain`, and
>    report every finding as **Status: Skipped — due to this
>    breaking** with the compiler error quoted in *Explanation of
>    Change*. The working tree must match the pre-run state. The
>    working tree is always either green or unchanged — never left in
>    a broken state.
> 5. If a finding's vulnerable snippet cannot be located in the
>    working tree, or the fix would be ambiguous / behavior-breaking
>    (e.g. BCrypt migration invalidating existing plaintext
>    passwords), skip the edit and document it in *Residual Risks* —
>    do not guess, do not ask the user.
>
> After applying (or reverting) edits, **do not commit and do not
> push.** All changes stay in the working tree; Step 3 (the
> git-agent) will commit and push them.
>
> Then produce `SECURE_REMEDIATION_REPORT.md` at
> **`.claude/reports/SECURE_REMEDIATION_REPORT.md`** with:
> - The per-finding schema and top-level sections defined in your
>   agent file (Remediation Summary, **Changes Made**, **Changes
>   That Remained — Due To Build Breakage**, Files Referenced,
>   Vulnerability Remediations, Security Improvements, Residual
>   Risks, Secure Coding Recommendations).
> - A `Build Impact` field on every finding (none /
>   broke-the-build / skipped-without-edit).
> - An explicit `# Changes Made` bullet list (one per Applied
>   finding) and an explicit `# Changes That Remained — Due To Build
>   Breakage` bullet list (one per `Skipped — due to this breaking`
>   finding, each with the compiler error and the unblock action).
> - **Always overwrite this file** — never merge, append, or preserve
>   prior contents.
>
> Allowed tools: `Read`, `Glob`, `Grep`, `Edit` (only on
> source/config files referenced in the assessment report), `Write`
> (only for `.claude/reports/SECURE_REMEDIATION_REPORT.md`), and
> `Bash` (only for: build-tool detection, the compile-check command,
> `git status --porcelain`, and `git checkout -- .` / per-file revert
> when the build cannot be repaired). Do not run the application, do
> not commit or push, do not modify `.claude/` or `.gitignore`, and
> do not write outside `.claude/reports/`. Do not prompt the user for
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

**Gate before Step 3 — both conditions MUST be true:**
1. **Build is green (Gate A).** The remediation report's
   `# Remediation Summary` leads with
   `Build verified: mvn compile test-compile passed` (or the Gradle
   equivalent). If it leads with
   `Build verified: failed — all edits reverted`, **do not run
   Step 3.** Skip the git-agent, report the failure, and remind the
   user the working tree was reverted to the pre-run state.
2. **Vulnerabilities are remediated (Gate B — user-mandated).**
   Every row in the remediation report's
   `# Vulnerability Remediations` table must have status `Applied`
   or be `Skipped — due to this breaking` / `Skipped — see
   Residual Risks` **with a complete explanation and unblock
   action** documented. If ANY finding is `Open`, `Unfixed`,
   `Pending`, `TODO`, or is missing its status / explanation, **do
   not run Step 3.** The user's rule is explicit: "if build
   failed or vulnerability is not pass then don't push in git...
   if successful then only in git." A green build with unfixed
   vulnerabilities is still a no-push situation.

When Step 3 is skipped, write a short
`.claude/reports/GIT_PUSH_REPORT.md` documenting which gate
failed and the offending finding IDs (if Gate B), so the user
has a clear, machine-readable record of why nothing was pushed.

## Step 3 — Run the Git Agent (Auto Push)

This step runs **automatically** — no prompt, no confirmation —
**only when BOTH gates pass**: (a) Step 2 left the build green
(Gate A), and (b) every vulnerability in the remediation report
is `Applied` or properly `Skipped` (Gate B). If either gate
fails, Step 3 is skipped entirely — no branch is created, no
commit is made, no push is attempted. A short
`GIT_PUSH_REPORT.md` is still written to record the abort
reason.

Launch the `git-agent` subagent (use the `Agent` tool with
`subagent_type: "general-purpose"` and the role from
`.claude/agents/git-agent.md`).

Pass this exact prompt to the subagent:

> You are the third stage of the fully automated security pipeline.
> Steps 1 and 2 (vulnerability-scanner + remediation-agent) have
> already finished; `.claude/reports/SECURE_REMEDIATION_REPORT.md`
> exists and reports a green build.
>
> **Operating mode: fully automated — no permission prompts, no
> confirmation questions.** You decide, edit (no edits expected
> here — your stage is commit + push only), commit, and push.
>
> Run the full workflow defined in your agent file:
> 1. Pre-flight checks (must be on `feature/safe-backup`, no merge
>    in progress, build green in the remediation report, working
>    tree dirty, upstream is `origin/feature/safe-backup`).
> 2. Compute the next branch name:
>    `feature/safe-backup_<N>_<TIMESTAMP>` where:
>    - `<N>` is `max(existing N) + 1` from
>      `git ls-remote --heads origin 'feature/safe-backup_*_*'`,
>      defaulting to `1` if no matching remote branch exists.
>    - `<TIMESTAMP>` is the **actual push time** captured as
>      `timestamp="$(date +'%Y-%m-%d_%H-%M-%S')"` on Windows Git
>      Bash — never the literal string `time_of_push`.
> 3. `git fetch origin --prune` then `git checkout -b
>    "feature/safe-backup_${N}_${timestamp}"`.
> 4. Stage with `git add -A` plus the explicit
>    `git add -f .claude/reports/SECURITY_ASSESSMENT_REPORT.md
>    .claude/reports/SECURE_REMEDIATION_REPORT.md`. Verify with
>    `git status --short` and `git diff --cached --stat`.
> 5. Re-run the compile-check on the new branch.
> 6. Commit with subject
>    `Safety backup push #${N} @ ${timestamp} — <short summary>`.
> 7. `git push -u origin "feature/safe-backup_${N}_${timestamp}"`.
> 8. Switch back to `feature/safe-backup`.
> 9. Write `.claude/reports/GIT_PUSH_REPORT.md` with the branch
>    name, commit SHA, remote URL, files pushed, build status, and
>    the explicit reminder that this agent never merges to `main`.
> 10. Report back to the user.
>
> **Never use the legacy `feature/safety-backup_*_time_of_push`
> naming.** The branch is always
> `feature/safe-backup_<N>_<TIMESTAMP>`.
>
> **Never merge to `main`, `master`, or any other shared branch.**
> **Never force-push.** If the harness rejects a specific command,
> abort that step cleanly, write the rejection into
> `GIT_PUSH_REPORT.md`, and return — do not prompt the user.
>
> Allowed tools: `Read`, `Glob`, `Grep`, `Write` (only for
> `.claude/reports/GIT_PUSH_REPORT.md`), and `Bash` (only for the
> git/mvn commands listed in your agent file's Tooling Notes).

Wait for the subagent to finish. Then verify
`.claude/reports/GIT_PUSH_REPORT.md` exists.

## Step 4 — Report Results

Tell the user, in a single message:

- Absolute path of `.claude/reports/SECURITY_ASSESSMENT_REPORT.md`
- Absolute path of `.claude/reports/SECURE_REMEDIATION_REPORT.md`
- Absolute path of `.claude/reports/GIT_PUSH_REPORT.md`
- **Build status** from the remediation report (`Build verified:
  passed` or `Build verified: failed — all edits reverted`).
- Top-line counts from the remediation report, with the two Skip
  categories shown separately.
- One-line summary of the remediation report's `# Changes Made`
  section.
- One-line summary of the remediation report's `# Changes That
  Remained — Due To Build Breakage` section, or *None* if empty.
- **Push result:** branch name
  `feature/safe-backup_<N>_<TIMESTAMP>`, commit SHA, remote URL,
  build status at push time, file count, and the explicit reminder
  that the git-agent **did not** merge to `main` — the human
  reviewer must merge manually.
  - **OR, if either gate failed:** state clearly
    `Push: ABORTED` with the gate reason (e.g. `Gate A — build
    failed` or `Gate B — N unfixed finding(s): VULN-007,
    VULN-012`). Make sure the user understands nothing was
    pushed and the working tree is in the expected state.
- Any items the remediation agent flagged in *Residual Risks*.

## Guardrails

- **No user prompts.** This command runs end-to-end. Step 3 only
  fires if Step 2's build is green **AND** all vulnerabilities
  are remediated. Either gate failing means no push.
- The **scanner** is strictly report-only: it must not edit any
  source file. If it tries to, abort the step and surface the
  violation.
- The **remediation agent** is allowed to `Edit` source/config files
  **only** those that the assessment report references as affected
  files. It must never run git commands, must never write outside
  `.claude/reports/`, and must never touch `.claude/` or
  `.gitignore`. If it edits a file that was not referenced in the
  assessment report, abort and surface the violation.
- The **remediation agent must not ask the user or developer for
  permission, confirmation, or clarification** before editing. The
  assessment report is its authorization.
- The **remediation agent must never start the application.**
- The **git-agent must not merge to `main`** — it pushes to
  `feature/safe-backup_<N>_<TIMESTAMP>` only, and always switches
  back to `feature/safe-backup` afterward.
- The **git-agent must use the
  `feature/safe-backup_<N>_<TIMESTAMP>` naming** with the actual
  push timestamp; the legacy
  `feature/safety-backup_<N>_time_of_push` naming is no longer used.
- The **git-agent must not push if the build is red OR any
  vulnerability is not remediated.** Both Gate A and Gate B are
  required. A green build with unfixed vulns is still a no-push.
  Per the user: "if build failed or vulnerability is not pass
  then don't push in git."
- **All three report files are overwritten on every run.** No
  append, merge, or preservation of prior contents.
- If any subagent fails or returns an error, stop the pipeline at
  that step and report the exact error to the user. Do **not**
  silently skip ahead to the next step.

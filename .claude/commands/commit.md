---
description: Group the uncommitted changes into logical Conventional Commits and commit them, matching this repo's existing message style.
argument-hint: [--dry-run] [--push] [--branch]
allowed-tools: Bash(git *), Read, Grep, Glob, AskUserQuestion
---

# /commit

Turn whatever is currently uncommitted in this working tree into a small number of atomic,
reviewable commits — without waiting to be asked file by file. Args (optional, combinable):

- `--dry-run` — print the proposed plan and stop; commit nothing.
- `--push` — after committing, push to the tracked remote branch (create the tracking branch
  with `-u origin <branch>` if none exists yet). Never used unless passed explicitly.
- `--branch` — create a new branch for this batch before committing, instead of committing on
  the current branch. See "Which branch" below for when this is needed vs. not.

## 1. See what actually changed

```bash
git status --porcelain=v1
git diff --stat
git diff            # and for anything large/binary-looking, diff --stat is enough
```

For every untracked path, actually look at it (`Read`/`Glob` into untracked directories too) —
`git status` alone tells you *that* something is new, not *what* it is or how it relates to the
rest of the change set. Group by what the change **does**, not by which directory it sits in.

If the tree is clean, say so and stop here.

## 2. Recalibrate style from real history, every time

Don't assume a fixed template — this project's own history is the source of truth and can
drift:

```bash
git log --format="%s" -20
```

Read the actual subjects (and `git log -5 --format="%B---"` for a few, to check whether bodies
are used at all). As of this writing the convention is: **`type(scope): imperative, descriptive
subject` — no body**, lowercase type, scope in parentheses when the change is narrow enough to
name one (`feat(seed)`, `fix(tools)`, `chore(research)`, `test`, `docs` with no scope when the
change is repo-wide). Match whatever the log actually shows, not this description if they've
diverged.

## 3. Which branch

```bash
git symbolic-ref refs/remotes/origin/HEAD 2>/dev/null || echo "no remote HEAD"
git rev-parse --abbrev-ref HEAD
```

This repo's convention (confirmed with the user 2026-09-14) is committing directly on the
default branch — there is no feature-branch/PR history here. Commit on the current branch
as-is unless `--branch` was passed, in which case create one (`git checkout -b <name>`, named
for the batch, e.g. `chore/brakes-research-audit`) before step 5. If you land here on some
*other* project where the log shows real feature-branch/PR history instead, follow that
project's own pattern instead of this one — don't force this repo's convention onto a
different codebase.

## 4. Propose the grouping — show it before doing anything

Split into atomic commits, one concern per commit. Rules of thumb (override with judgment when
the actual diff disagrees):

- A behavior change to existing logic (a bug fix, a tightened validation rule) is its own
  commit, separate from unrelated additions landing at the same time.
- New tooling/scaffolding ships together with the data or generated output it only exists to
  produce/consume, when neither half is meaningful alone (e.g. an import script + the seed file
  it generated). Don't split those into artificial halves just to shrink each commit.
- Pure research/audit artifacts (worklists, gap reports, raw scraped batches) with no code
  change of their own are their own commit.
- Tests: bundle with the change they cover when the change is small; give them their own
  `test:` commit when they're independently large enough to review on their own merits (check
  which pattern recent history actually used for similar-sized batches, per step 2).
- Docs-only changes are always isolated.
- Order commits so each one leaves the tree at least as coherent as the last: a script before
  its own generated output, generated output before tests that assert on it, everything before
  the docs that describe the finished state.
- Stage explicit pathspecs only (`git add -- path1 path2 ...`). Never `git add -A` / `git add
  .` — nothing rides along that wasn't deliberately placed in its group.
- Never stage anything that looks like a secret or credential (`.env*`, private keys, tokens)
  or obvious local build output, even if untracked and not gitignored — flag it and ask instead
  of guessing.

Print the full plan as the exact pair of shell commands each commit will run — never a table,
never prose bullets, nothing to translate later:

```bash
git add -- <files for this group>
git commit -m "<type>(<scope>): <subject>"
```

One `git add` / `git commit -m` pair per numbered commit, back to back, in the order they'll
run. This is the plan — not a summary of it — so it must already be copy-pasteable and runnable
as-is. If anything is genuinely ambiguous (a file could belong to two groups, a change looks
unrelated to everything else in the tree), ask via `AskUserQuestion` before printing the plan
rather than guessing.

If `--dry-run` was passed, stop here.

## 5. Commit

Run each `git add -- ...` / `git commit -m "..."` pair from the plan above, in order, exactly
as printed.

End each commit message with this conversation's attribution footer per the standing
instruction already in context — don't hardcode a specific line here, it can change between
sessions/models and this file shouldn't need editing when it does. (That footer is a
`-m` line of its own, or a `$'...\n\n...'` multi-line message — either way it's still one
`git commit -m` invocation per commit, just with the footer folded into the same message.)

## 6. Confirm the result

```bash
git log --oneline -n <number of commits just made>
git status
```

Report the tree is clean, or explain what's deliberately still uncommitted and why (e.g.
something flagged as a possible secret).

If `--push` was passed: check for a tracking branch (`git rev-parse --abbrev-ref --symbolic-full-name @{u}`),
push with `-u origin <branch>` if there isn't one yet, otherwise a plain `git push`. Never
`--force`, on this command or any other.

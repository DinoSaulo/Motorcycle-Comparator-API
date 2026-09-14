# tools/tests

Unit tests for the standalone Node CLI scripts in `tools/`. No dependencies, no `package.json`, no test
framework beyond what ships with Node 18+ (`node:test` + `node:assert/strict`).

Run from the repo root:

```
node --test "tools/tests/**/*.test.mjs"
```

Node 24's test runner does not accept a bare directory as a positional argument (`node --test tools/tests`
tries to load the directory as a module and fails), so the glob has to be spelled out and quoted.
`node --test` with no arguments also works from the repo root, since these are the only files matching
its default `**/*.test.mjs` pattern.

## Why every test spawns a subprocess

Each script under test resolves `REPO_ROOT` from **its own file location**
(`path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')`), then reads and writes fixed
repo-relative paths: `tools/brakes-research.json`, `tools/slugs_missing_brakes.txt`,
`src/main/resources/db/seed/...`. Changing the working directory or an environment variable does not
redirect any of that.

So `_harness.mjs` builds a throwaway repo tree per test: it copies the script into `<tmp>/tools/`,
writes the fixtures it needs beside it, creates an empty `<tmp>/src/main/resources/db/seed/`, and
spawns `node <tmp>/tools/<script>.mjs`. Assertions then run against exit code, stdout, stderr and the
generated SQL. No test reads or writes the real research files or the real seed directory.

These tests are not wired into `.github/workflows/maven-ci.yml`; running them is manual for now.

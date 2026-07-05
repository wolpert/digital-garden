# Dependabot + auto-merge

This repo is set up so dependency updates land with minimal fuss:

- **`dependabot.yml`** opens PRs **weekly** for Gradle dependencies (all modules)
  and for the GitHub Actions used in the workflows.
- **`workflows/ci.yml`** builds core + desktop, runs the test task, and assembles
  the Android debug APK on every PR and push to `main`.
- **`workflows/dependabot-auto-merge.yml`** turns on GitHub auto-merge for
  Dependabot PRs, so each one **merges itself once CI passes**.

## One-time repo settings (required)

The workflow files can't configure these — do them once in the repo settings:

1. **Allow auto-merge.**
   Settings → General → *Pull Requests* → check **Allow auto-merge**.

2. **Require the CI check before merging** (this is what makes auto-merge wait
   for the build/test to be green — without a required check, auto-merge would
   merge immediately).
   Settings → Branches → add a branch protection rule (or a ruleset) for `main`:
   - Require a pull request before merging.
   - Require status checks to pass → select **Build & test**.

   > Tip: the **Build & test** check only appears in the list after CI has run at
   > least once. Push this branch (or open any PR) once, then add it.

## Notes

- Auto-merge uses **squash**. Change `--squash` to `--merge` or `--rebase` in the
  auto-merge workflow if you prefer, and make sure that merge method is enabled in
  Settings → General.
- To auto-merge only non-major bumps, extend the `if:` condition in the auto-merge
  job with `&& steps.metadata.outputs.update-type != 'version-update:semver-major'`.
- Dependabot-triggered workflows get a token scoped by the `permissions:` block;
  no extra secrets are needed for `gh pr merge`.
- `libgdx` versions are referenced through the `$gdxVersion` variable in
  `build.gradle`, which Dependabot's Gradle updater can't rewrite. Literal
  versions (e.g. the Android Gradle Plugin) and GitHub Actions are updated
  normally. To get libGDX bumps automatically, switch to a literal version or a
  version catalog.

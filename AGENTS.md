# Project Instructions

Before starting any task:

1. Read every file under `.agents/skills/` and follow all applicable rules.
2. Read `Spec.md`, `CONTEXT.md`, `TASKS.md`, and every ADR under `docs/adr/`.
3. Explain the implementation and verification plan before editing code.
4. Work only on the Phase or task explicitly assigned by the user.

While working:

- Treat `Spec.md` as the primary source of truth and use the vocabulary in `CONTEXT.md`.
- Keep the single `app` module and feature-first package structure until the spec changes.
- Keep UI, domain, and data responsibilities separated; Composables must never call a DAO directly.
- Put all user-visible strings in Android resources and use Material 3 theme values for visual styling.
- Do not add Firebase, authentication, cloud sync, advertising, tracking, or unrequested permissions.
- Do not prebuild later phases, speculative interfaces, fake product data, or non-executable placeholders.
- Update the assigned task status in `TASKS.md` when each task is completed.

Before finishing:

1. Review the changes for scope, architecture, unused code, and warnings.
2. Run the build, relevant tests, and lint commands required by the assigned task.
3. Fix code or configuration failures before reporting completion.
4. Report changed files, actual technology versions, verification results, and any external environment limitation.


---
name: gitting
description: Commit and push the current changes to GitHub with a meaningful commit message.
argument-hint: "Describe what was implemented in this commit"
---

Commit and push all current changes in the repository to GitHub. Follow these steps exactly:

1. Run `git status` to see what files are changed
2. Review the changes with `git diff` to understand what's being committed
3. Stage all relevant files (excluding build artifacts, `.gradle/`, `.idea/`, local.properties, and any generated files)
4. Commit with a clear, descriptive message based on the changes and the argument provided
5. Push to the remote repository

The commit message should follow the format: a short summary line, then a blank line, then bullet points of what was changed.

Do NOT commit these files or folders:
- Build artifacts: `build/`, `.gradle/`, `app/build/`
- IDE files: `.idea/`, `*.iml`, `*.ipr`, `*.iws`
- Local config: `local.properties`, `keystore.jks`, `debug.keystore`
- OS files: `.DS_Store`, `Thumbs.db`, `desktop.ini`
- Gradle wrapper JAR if not your own: `gradle/wrapper/gradle-wrapper.jar`
- User-specific gradle config: `gradle.properties` (if contains local SDK paths)
- Generated files: `.scratch/` (ticket drafts, spec drafts), `.artifacts/` (unless these are meant to be shared)
- Environment files: `*.env`, `.env.local`
- Any files containing API keys, passwords, tokens, or secrets

If unsure about a file, check its contents first before staging. Never stage binary files or files with hardcoded credentials.

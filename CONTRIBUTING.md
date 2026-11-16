# Contributing to Flint

1. Open an issue first for anything bigger than a small fix.
2. Fork, then branch: `git checkout -b feature/my-change`.
3. Follow `.editorconfig` (Kotlin official style, 4 spaces).
4. Run `gradle :app:testDebugUnitTest` and make sure the app builds.
5. Open a pull request: say what changed and why, and add screenshots for UI changes.

Rules of thumb

- Keep libVLC and file or network calls off the main thread.
- Don't log in per-frame or per-tick code.
- Discuss new dependencies before adding them.
- Every new source file starts with the SPDX header:
  ```
  // SPDX-License-Identifier: GPL-3.0-or-later
  // Copyright (C) 2026 heyyntn47
  ```
- Sign off your commits (`git commit -s`) to certify the
  [Developer Certificate of Origin](https://developercertificate.org/).

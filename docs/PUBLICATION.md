# GitHub publication review

## Checkout and destination

Use the separate publication checkout for `Threalcocoboy/PULSE`. The verified owner ID is `340643353`. The new repository starts as private for source review. The development checkout has its own remote and unfiltered history.

## Exclusions

The development repository tracks `verification/` captures and reports dating back to its initial commit. Garmin integration verification used live personal data. Exclude the whole directory from the public branch and its history, including screenshots, UI accessibility dumps and test exports. Review additional historical paths and binary assets for private information. The local Google OAuth configuration JSON and Garmin personal key file belong outside published source. The release does not need either credential. Keep the stash, private working refs, tool caches, local Android SDK, old debug APKs and independent trailer workspace outside the publication checkout.

In a fresh local single-branch clone, remove the verification tree from history with `git filter-repo --path verification/ --invert-paths`. Add the reviewed working source to that copy after filtering. Run a redacted Gitleaks scan against all refs intended for publication and the complete candidate file tree. Review screenshot provenance, author email visibility, app assets, app resources and test fixtures. Check the final APK for the same release gates. A Git push of the development branch would still contain the historical artifacts even if `verification/` were removed in a new commit.

## Owner review

- Confirm the release operator and private contact path, governing law, intended audience and any privacy disclosures required in the target jurisdictions.
- Confirm distribution rights for the app branding and launch animation, and complete the dependency notice review. Choose a project license explicitly if you want to grant reuse rights.
- Confirm signing identity and test a signed APK before release approval. Review [RELEASING.md](RELEASING.md) for the exact environment configuration.
- If review discovers a committed credential, rotate it in the provider account and filter it from the publication copy. If private material is already on a remote, use that repository's authorized incident process for removal and review old branches, pull requests, forks, caches and artifacts.

Only after those checks, configure the verified target in the clean checkout, push the reviewed branch and confirm its owner, visibility, source tree and public legal links. Keep the development remote available for its own history.

# Copilot Instructions — signer-cloud

This file captures conventions used when working with GitHub Copilot in the
`signer-cloud` repository.

---

## Changelog

All notable changes are recorded in the root [`CHANGELOG.md`](../CHANGELOG.md), which
**strictly** follows the [Keep a Changelog 1.1.0](https://keepachangelog.com/en/1.1.0/)
format and [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

### Format

- The file starts with the canonical header referencing both
  [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and
  [Semantic Versioning](https://semver.org/spec/v2.0.0.html).
- A `## [Unreleased]` section sits at the top; new entries are always added there.
- Change-type subsections, in this order, using **only** those that apply:
  `Added`, `Changed`, `Deprecated`, `Removed`, `Fixed`, `Security`.
- Versions are linkable via reference-style links at the bottom of the file.

### Entries

- Each entry is human-readable (not a raw commit message) and starts with a verb.
- Each entry **links to the issue, not the PR**, using the form:
  ```markdown
  - Fixed NPE when the signer list is empty [(#231)](https://github.com/wultra/signer-cloud/issues/231)
  ```
- Issue URLs use `https://github.com/wultra/signer-cloud/issues/N`.
- Add an entry for every user-visible change. Skip only changes with no user-visible
  impact (e.g. pure CI/tooling).

### Releasing

On release:

1. Rename `## [Unreleased]` to `## [x.y.z] - YYYY-MM-DD` (ISO 8601 date).
2. Add a fresh, empty `## [Unreleased]` section above it.
3. Update the `[unreleased]` reference link to compare from the new version to `HEAD`.
4. Add the new version's compare reference link.

### Example

```markdown
# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]
### Added
- New feature description [(#N)](https://github.com/wultra/signer-cloud/issues/N)

## [1.0.0] - 2025-03-01
### Changed
- Changed behaviour description [(#N)](https://github.com/wultra/signer-cloud/issues/N)

[unreleased]: https://github.com/wultra/signer-cloud/compare/1.0.0...HEAD
[1.0.0]: https://github.com/wultra/signer-cloud/compare/0.10.0...1.0.0
```

# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]
### Changed
- Improved structured logging quality in the service layer: contextual IDs are now logged as queryable `StructuredArguments.kv()` fields and log calls use the action/state pattern with meaningful messages [(#220)](https://github.com/wultra/signer-cloud/issues/220)
- Upgraded Docker base image to `ibm-semeru-runtimes:open-jdk-25.0.3.0-jre-noble` (OpenJDK 25) [(#226)](https://github.com/wultra/signer-cloud/issues/226)
- Migrated to Spring Boot 4 and Jackson 3 [(#225)](https://github.com/wultra/signer-cloud/issues/225)

[unreleased]: https://github.com/wultra/signer-cloud/compare/0.10.0...HEAD

# Migration from 0.10.0 to 1.0.0

This guide contains instructions for migration from Signer Cloud Server version `0.10.0` to version `1.0.0`.


## Dependency Updates


### Docker Base Image Upgrade

The Docker base image has been upgraded from `ibm-semeru-runtimes:open-21.0.9_10-jre-noble` (OpenJDK 21) to `ibm-semeru-runtimes:open-jdk-25.0.3.0-jre-noble` (OpenJDK 25).
No action is required.


### Spring Boot 4 and Jackson 3

Liveness Check Proxy has been migrated to Spring Boot 4 and Jackson 3.
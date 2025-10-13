# Database Setup

Choose the manual depending on used database:

- [Database Setup for PostgreSQL](./Database-Setup-PostgreSQL.md)

## Connection Pooling

All our applications use HikariCP (the default for Spring Boot). As a result, we manage connections efficiently. By default, we create 10 connections to the underlying PostgreSQL database.

In case you change the configuration of the image to a lower number of idle connections than the maximum number of connections, some of the connections may be released. If the application becomes inactive and connections are idle, HikariCP will keep these connections open for approximately 10 minutes (600 000 milliseconds, with maximum variation of 30s - HikariCP checks for idle state every 30s).
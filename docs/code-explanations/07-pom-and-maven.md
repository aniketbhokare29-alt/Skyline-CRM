# pom.xml and Maven Explanation

> Last updated: the project POM now lives at `backend/pom.xml`. The root
> `pom.xml` was removed; Maven must be run with `-f backend/pom.xml`. The
> exec-maven-plugin still launches `com.saivandan.crm.App`.

File:

`pom.xml`

## Purpose

Maven builds the Java project and manages external libraries.

## Important configuration

- Java source and target version: 21.
- Project artifact: `skyline-crm`.
- MySQL Connector/J dependency for the application database.
- Main class: `com.saivandan.crm.App`.

## Useful commands

```powershell
mvn clean compile
mvn exec:java
```

`clean` removes old build output. `compile` compiles Java source files. `exec:java` starts the application.

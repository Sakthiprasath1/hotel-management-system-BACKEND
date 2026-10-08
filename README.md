# Hotel Management Backend

The backend is a Spring Boot application that uses MySQL.

## Requirements

- Java 17 or newer
- MySQL

The default database URL and username are configured in `src/main/resources/application.properties`. Set the database password in the `DB_PASSWORD` environment variable; do not put credentials in source files.

## Run locally

In PowerShell, set your own MySQL password and start the backend:

```powershell
$env:DB_PASSWORD = "<your MySQL password>"
.\mvnw.cmd spring-boot:run
```

The API runs on port 8080 by default.

## Test

```powershell
.\mvnw.cmd test
```

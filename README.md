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

## Deploy on Railway

Deploy this repository as a service from its repository root. `railway.json` builds and runs the Spring Boot JAR, and the application listens on Railway's assigned `PORT`.

Add a Railway MySQL database to the project. In the backend service, add references to that database service's `MYSQLHOST`, `MYSQLPORT`, `MYSQLDATABASE`, `MYSQLUSER`, and `MYSQLPASSWORD` variables. Then generate a public domain for the backend and set that HTTPS URL as `BACKEND_URL` in the frontend service. The frontend appends `/api` to this URL.

## Test

```powershell
.\mvnw.cmd test
```

# Everyday Expense Tracker

A small Spring Boot MVC app for recording personal expenses. The browser interface is built with HTML, CSS, and vanilla JavaScript and talks to the REST API served by the same application.

## Requirements

- JDK 17 or newer
- Apache Maven 3.6.3 or newer
- Internet access the first time Maven downloads dependencies

## Run on Windows

Open PowerShell in this directory and run:

```powershell
mvn spring-boot:run
```

Then open [http://localhost:8080](http://localhost:8080).

## Deploy on Render

The repository includes a `Dockerfile` and `render.yaml` for a free Render web service. Push this project to GitHub, sign in to Render with GitHub, then choose **New → Blueprint** and select the repository. Render will build and deploy the service from `render.yaml`.

Render's free web services sleep after inactivity, and this app currently stores expenses in memory. Recorded expenses are cleared when the service restarts or redeploys. This setup is suitable for a demo, not as permanent personal-finance storage.

Run the integration tests with:

```powershell
mvn test
```

To build an executable JAR:

```powershell
mvn clean package
java -jar target/expense-tracker-0.0.1-SNAPSHOT.jar
```

## MVC structure

- `controller/ExpenseController.java` exposes the REST endpoints.
- `service/ExpenseService.java` validates expense data and coordinates operations.
- `repository/ExpenseRepository.java` stores expenses in memory.
- `model/Expense.java` and `model/ExpenseRequest.java` define the API data.
- `src/main/resources/static/` contains the frontend served by Spring Boot.

The in-memory repository resets whenever the application restarts. Amounts are displayed as Indian rupees in the frontend.

## API

| Method | Path | Result |
| --- | --- | --- |
| `GET` | `/api/expenses` | List all expenses, newest first |
| `GET` | `/api/expenses/{id}` | Get one expense |
| `POST` | `/api/expenses` | Create an expense |
| `PUT` | `/api/expenses/{id}` | Update an expense |
| `DELETE` | `/api/expenses/{id}` | Delete an expense |

Create and update requests use JSON with `description`, `category`, `amount`, and `date` fields. Amount must be positive and the date cannot be in the future.

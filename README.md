# MyAngularApp

This project was generated with [Angular CLI](https://github.com/angular/angular-cli) version 18.0.4.

## Development server

Run `ng serve` for a dev server. Navigate to `http://localhost:4200/`. The application will automatically reload if you change any of the source files.

## Code scaffolding

Run `ng generate component component-name` to generate a new component. You can also use `ng generate directive|pipe|service|class|guard|interface|enum|module`.

## Build

Run `ng build` to build the project. The build artifacts will be stored in the `dist/` directory.

## Running unit tests

Run `ng test` to execute the unit tests via [Karma](https://karma-runner.github.io).

## Running end-to-end tests

Run `ng e2e` to execute the end-to-end tests via a platform of your choice. To use this command, you need to first add a package that implements end-to-end testing capabilities.

## Further help

To get more help on the Angular CLI use `ng help` or go check out the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli) page.

## Spring Boot backend

The `backend/` folder contains the Spring Initializr-generated Maven project for the Task Manager API. It uses Java 21 and includes Spring Web, Spring Data JPA, Validation, and the PostgreSQL driver.

Install a Java 21 JDK and make PostgreSQL available before running the backend directly. The Maven Wrapper is included, so a separate Maven installation is not required. The backend reads `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` from the process environment. `DB_URL` defaults to `jdbc:postgresql://localhost:5432/task_manager`; the username and password must be set. No database credentials are stored in the project.

In PowerShell, set the values for the current terminal session (replace the example username and enter your local database password when prompted):

```powershell
cd backend
$env:DB_USERNAME = "your_postgres_username"
$env:DB_PASSWORD = Read-Host "PostgreSQL password"
.\mvnw.cmd spring-boot:run
```

Set `$env:DB_URL` as well if your PostgreSQL host, port, or database name differs from the default. The `task_manager` database must already exist. For local development, Hibernate updates the database schema automatically; use a migration tool rather than this setting for production.

### Run the full stack with Docker Compose

Docker Desktop provides Java, PostgreSQL, ActiveMQ Artemis, and the local AI runtime through containers, so local Java, PostgreSQL, Ollama, and Artemis installations are not required. From the repository root, create your local environment file, set private database and broker passwords, and start the stack:

```powershell
Copy-Item .env.example .env
notepad .env
docker compose up --build -d
```

The Ollama container downloads the configured model (`qwen2.5:1.5b` by default) the first time it starts. The model is stored in a Docker volume and can take several minutes to download; the backend waits for Ollama and Artemis to become healthy before starting. To choose a different Ollama model, change `OLLAMA_MODEL` in `.env` and restart the stack. Artemis is available only inside the Compose network and persists its queue data in a Docker volume.

Open [http://localhost:8080](http://localhost:8080). The task assistant runs against Ollama on the private Docker network; the Ollama API is not published on a host port. Chat messages and task changes persist in PostgreSQL. The assistant can propose task creation, edits, or deletions, but it cannot change tasks until you confirm each proposal in the chat.

Follow startup progress with `docker compose logs -f`; stop the services with `docker compose down`. PostgreSQL data, model files, and Artemis messages persist in named volumes. To also erase the database, all tasks and chat history, downloaded model, and queued messages, use `docker compose down -v`. The sample credentials are for local development only; do not use them in a deployed environment.

For step-by-step initial setup, targeted Angular or Spring Boot rebuilds, Ollama model changes, and deployment checks, see [BUILD-DEPLOYMENT.md](./BUILD-DEPLOYMENT.md).

To run Jenkins in Docker and build/deploy through a local Pipeline, see [JENKINS-DEPLOYMENT.md](./JENKINS-DEPLOYMENT.md), [docker-compose.jenkins.yml](./docker-compose.jenkins.yml), and the repository [Jenkinsfile](./Jenkinsfile).

The chat API is under `/api/chat`: create a conversation with `POST /conversations`, list conversations with `GET /conversations`, list messages with `GET /conversations/{id}/messages`, and send a message with `POST /conversations/{id}/messages` using `{"content":"..."}`. Read-only task lookups are answered directly from PostgreSQL. Other messages are queued in Artemis for Ollama processing; the UI polls message history until a reply arrives or reports a processing failure. Delete a saved conversation and its messages/actions with `DELETE /conversations/{id}`; this does not delete tasks. The assistant can return proposed actions with `PENDING` status. Apply or decline one using `POST /actions/{actionId}/confirm` or `POST /actions/{actionId}/reject`. A proposal is single-use, and updates/deletions are rejected if the task changed after the proposal was made.

When running Angular with `npm start`, its development-server proxy forwards `/api` requests to `http://localhost:8080`. The Nginx configuration forwards `/api/` to the `backend` Docker service.

### Task API

Once PostgreSQL is configured and the backend is running, the API is available under `/api/tasks`:

| Method | Endpoint | Description |
| --- | --- | --- |
| `GET` | `/api/tasks` | List all tasks |
| `GET` | `/api/tasks/{id}` | Get one task |
| `POST` | `/api/tasks` | Create a task |
| `PUT` | `/api/tasks/{id}` | Replace a task's editable fields |
| `DELETE` | `/api/tasks/{id}` | Delete a task |

Send `title`, `description`, and `completed` as JSON for create and update requests. `title` is required and limited to 200 characters; `description` is limited to 2,000 characters. For example:

```json
{
  "title": "Prepare project plan",
  "description": "List the first API milestones",
  "completed": false
}
```

New tasks receive an ID and UTC `createdAt` timestamp from the backend. A missing task returns `404`, invalid request data returns `400`, successful creation returns `201`, and successful deletion returns `204`.

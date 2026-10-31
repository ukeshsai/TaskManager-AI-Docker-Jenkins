# Build and deployment guide

This project uses Docker Compose to build and run the Angular frontend, Spring Boot API, PostgreSQL database, Ollama model runtime, and ActiveMQ Artemis broker. Run the commands in this guide from the repository root in PowerShell.

## First-time setup

1. Install and start Docker Desktop.
2. Create the local environment file and set a private database password:

   ```powershell
   Copy-Item .env.example .env
   notepad .env
   ```

   Set private values for both `POSTGRES_PASSWORD` and `ARTEMIS_PASSWORD`. Keep `.env` private; it is excluded from Git.

   If `.env` already exists, add an `ARTEMIS_USER` and a strong `ARTEMIS_PASSWORD` using the variable names in `.env.example`. Do not replace the existing database values, as they must match the initialized PostgreSQL volume.

3. Build and start the application:

   ```powershell
   docker compose up --build -d
   ```

   The first start downloads the configured Ollama model and may take several minutes. Artemis is also initialized with the broker credentials from `.env`.

4. Check service status and startup logs:

   ```powershell
   docker compose ps
   docker compose logs --tail=100
   ```

5. Open [http://localhost:8080](http://localhost:8080).

## Deploying code changes

After changing Angular, rebuild and replace only the frontend container:

```powershell
docker compose up --build -d frontend
```

After changing Spring Boot, rebuild and replace only the backend container:

```powershell
docker compose up --build -d backend
```

If a change affects both, rebuild both:

```powershell
docker compose up --build -d frontend backend
```

To rebuild and deploy every service:

```powershell
docker compose up --build -d
```

These commands preserve the PostgreSQL database, downloaded Ollama models, and Artemis messages. They do not delete the Compose volumes.

## How chat uses the queue

The Angular app sends messages only to the Spring Boot API. Read-only task lookups are answered directly from PostgreSQL; other chat messages are saved and queued in Artemis. A single Spring listener calls Ollama, then saves the assistant reply and any proposed task actions to PostgreSQL. Angular polls the conversation while that reply is processing. The user must still confirm proposed task changes.

If processing fails, the backend retries the job up to three attempts, moves the failed payload to `chat.requests.dlq`, and marks the chat message as failed with a retry hint. Artemis is private to the Compose network; it is not exposed on a host port.

## Changing or refreshing the Ollama model

### Switch to a different model

1. Choose a model name supported by Ollama, then update `OLLAMA_MODEL` in `.env`, for example:

   ```dotenv
   OLLAMA_MODEL=qwen2.5:3b
   ```

2. Recreate Ollama and the backend so both use the new model setting:

   ```powershell
   docker compose up -d ollama backend
   ```

   The Ollama container pulls the configured model on startup. The backend waits for Ollama to become healthy before it starts.

3. Follow the model download and backend startup:

   ```powershell
   docker compose logs -f ollama backend
   ```

   Press `Ctrl+C` to stop following logs; this does not stop the containers.

### Refresh the current model version

If the model name stays the same but you want Ollama to pull its latest version, run:

```powershell
docker compose exec ollama ollama pull qwen2.5:1.5b
```

Replace the model name with the value of `OLLAMA_MODEL` in `.env`. The model is stored in the `ollama_data` volume. Switching model names does not remove previously downloaded models.

## Verify a deployment

Check container state:

```powershell
docker compose ps
```

Inspect logs for a particular service:

```powershell
docker compose logs --tail=100 frontend
docker compose logs --tail=100 backend
docker compose logs --tail=100 ollama
```

The frontend is available at [http://localhost:8080](http://localhost:8080). PostgreSQL, Ollama, and Artemis are accessible to other Compose services on the private Docker network and are not published on host ports.

## Stop and restart

Stop the application while preserving its database and models:

```powershell
docker compose down
```

Start it again without rebuilding images:

```powershell
docker compose up -d
```

## Data and deployment notes

- PostgreSQL data, Ollama models, and Artemis queue data are stored in named Docker volumes, so rebuilding containers does not normally erase them.
- **Do not run `docker compose down -v` unless you intend to permanently delete the database, task and chat history, downloaded models, and queued messages.**
- The backend currently uses Hibernate `ddl-auto=update` to update the development database schema. For production, use database migrations and back up PostgreSQL before schema changes.
- `.env.example` contains placeholders, not production credentials. Use strong, private credentials for any shared or production deployment, and do not commit `.env`.
- To deploy on another Docker host, copy the project files and a securely provisioned `.env` to that host, install Docker with the Compose plugin, then follow the first-time setup steps.

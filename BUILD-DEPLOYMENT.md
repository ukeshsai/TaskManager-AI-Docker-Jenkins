# Build and deployment guide

This project uses Docker Compose to build and run the Angular frontend, Spring Boot API, PostgreSQL database, a separate AI worker, Ollama, and ActiveMQ Artemis. Run the commands in this guide from the repository root in PowerShell.

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

After changing the core Spring Boot API, rebuild and replace only the backend container:

```powershell
docker compose up --build -d backend
```

After changing AI-worker code, rebuild and replace that service:

```powershell
docker compose up --build -d ai-worker
```

If a change affects the API and worker, rebuild both:

```powershell
docker compose up --build -d frontend backend ai-worker
```

To rebuild and deploy every service:

```powershell
docker compose up --build -d
```

These commands preserve the PostgreSQL database, downloaded Ollama models, and Artemis messages. They do not delete the Compose volumes.

## How chat uses the queue

The Angular app sends messages only to the Spring Boot API. Read-only task lookups are answered directly from PostgreSQL; other chat messages are saved and published to the Artemis request queue with a prompt, conversation messages, output schema, and correlation metadata. The separate `ai-worker` consumes one request at a time, calls Ollama, and publishes the generated content and echoed metadata to the request's reply queue. The backend consumes that result and saves the assistant reply and proposed task actions to PostgreSQL. Angular polls the conversation while the reply is processing. The user must still confirm proposed task changes. There is no HTTP API exposed for AI generation; all callers submit jobs through MQ.

If inference fails, the worker retries the job up to three attempts, moves an exhausted payload to `chat.requests.dlq`, and publishes a failure result so the backend marks the chat message as failed. Malformed task-chat results go to `ai.responses.task-manager.dlq`. Artemis is private to the Compose network; it is not exposed on a host port.

The worker's JMS listener concurrency is set to `1`, so one worker instance processes one AI request at a time. Ollama's `OLLAMA_NUM_PARALLEL` also defaults to `1`. This limits simultaneous inference requests and smooths bursts; it does not guarantee a fixed CPU ceiling for an individual model request. Do not scale `ai-worker` to multiple replicas if you need to keep this single-consumer limit.

### Current date and time in AI prompts

The worker adds the current weekday, date, time, timezone, and UTC offset to every model prompt at processing time. By default, the timezone is `Asia/Kolkata`. To change it, set `AI_TIME_ZONE` in `.env` to a valid Java time-zone ID (for example `America/New_York`), then recreate the worker:

```powershell
docker compose up -d --force-recreate ai-worker
```

This gives the model a reliable reference for basic questions such as today's date, weekday, or current time in the configured zone. The model still performs date arithmetic itself, so verify critical deadlines or calculations rather than treating generated answers as a date-calculation service.

### Sending a job from another service

Other services on the private Compose network can use the same request queue and choose a dedicated reply queue. A request has this JSON shape:

```json
{
  "correlationId": "a-unique-uuid",
  "attempt": 0,
  "replyTo": "ai.responses.reporting",
  "systemPrompt": "Summarize the supplied report in three bullet points.",
  "messages": [
    { "role": "user", "content": "Report text goes here." }
  ],
  "responseSchema": {
    "type": "object",
    "properties": { "summary": { "type": "string" } },
    "required": ["summary"],
    "additionalProperties": false
  },
  "metadata": { "reportId": "report-123" }
}
```

Publish it to `chat.requests`. The worker publishes a result containing the same `correlationId` and `metadata` to the `replyTo` queue, with either generated `content` or an `error`. Reply destinations must use the `ai.responses.<service>` naming pattern; the task manager listens on `ai.responses.task-manager`. Each caller should use its own reply queue and match results by correlation ID. Supply your own prompt, message list, and Ollama-compatible JSON output schema. Callers need access to Artemis on the Compose network, not an HTTP endpoint on the worker.

## Changing or refreshing the Ollama model

### Switch to a different model

1. Choose a model name supported by Ollama, then update `OLLAMA_MODEL` in `.env`, for example:

   ```dotenv
   OLLAMA_MODEL=qwen2.5:3b
   ```

2. Recreate Ollama and the backend so both use the new model setting:

   ```powershell
   docker compose up -d ollama ai-worker
   ```

   The Ollama container pulls the configured model on startup. The AI worker waits for Ollama to become healthy before it starts; the core backend does not need a connection to Ollama.

3. Follow the model download and worker startup:

   ```powershell
   docker compose logs -f ollama ai-worker
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
docker compose logs --tail=100 ai-worker
docker compose logs --tail=100 ollama
```

The frontend is available at [http://localhost:8080](http://localhost:8080). PostgreSQL, Ollama, Artemis, and the AI worker are accessible only to services on the private Docker network. No AI-worker generation port is published; callers use the Artemis request/result queues.

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

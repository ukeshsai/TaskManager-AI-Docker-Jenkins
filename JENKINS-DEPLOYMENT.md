# Jenkins in Docker

This setup runs the Jenkins controller in its own Docker container on Docker Desktop. The controller image includes the Docker CLI and Compose plugin, and the Jenkins container connects to Docker Desktop through the mounted Docker socket. Pipeline jobs therefore run in the Linux Jenkins container, not on a Windows Jenkins agent.

## Start Jenkins

From the repository root in PowerShell:

```powershell
docker compose -f docker-compose.jenkins.yml up --build -d
docker compose -f docker-compose.jenkins.yml logs -f jenkins
```

Press `Ctrl+C` to stop following logs; Jenkins will keep running. Open [http://localhost:8081](http://localhost:8081) and complete the Jenkins setup wizard. To retrieve the initial administrator password:

```powershell
docker compose -f docker-compose.jenkins.yml exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword
```

Install the suggested plugins, or ensure these plugins are installed: **Pipeline**, **Git**, and **Credentials Binding**.

Check Docker access from the Jenkins container:

```powershell
docker compose -f docker-compose.jenkins.yml exec jenkins docker version
docker compose -f docker-compose.jenkins.yml exec jenkins docker compose version
```

The first command should show both client and server information. If it reports a Docker socket permission error, check Docker Desktop's Linux-container socket integration and the `DOCKER_GID` setting in the environment used to start Jenkins. The Compose file defaults `DOCKER_GID` to `0`, which is the usual Docker Desktop socket group.

## Connect a Pipeline job

The project is published at `https://github.com/ukeshsai/TaskManager-AI-Docker-Jenkins` on the `master` branch. Jenkins needs to be able to check out that repository; if it is private, configure Git credentials in Jenkins as required by your Git provider.

1. In Jenkins, open **Manage Jenkins → Credentials → (global) → Add Credentials**.
2. Add a **Secret text** credential:
   - **Secret:** the same PostgreSQL password used by the existing application database
   - **ID:** `task-manager-postgres-password`
3. Add another **Secret text** credential for the Artemis broker password:
   - **Secret:** choose a strong broker password; on initial deployment, use the same value configured in the application's `.env`
   - **ID:** `task-manager-artemis-password`
4. Create a **Pipeline** job and select **Pipeline script from SCM**.
5. Choose **Git**, enter `https://github.com/ukeshsai/TaskManager-AI-Docker-Jenkins.git`, set the branch to `*/master`, and set **Script Path** to `Jenkinsfile`.
6. Save the job, then select **Build with Parameters** for the first run.

The Jenkinsfile uses `agent any`; no Windows Jenkins agent or extra agent container is required. The built-in Jenkins executor runs the pipeline steps in the Jenkins container.

## What the pipeline does

1. Checks Docker Engine and Compose availability inside Jenkins.
2. Validates the Compose configuration using Jenkins' PostgreSQL and Artemis password credentials.
3. Builds the Angular frontend and Spring Boot backend Docker images.
4. Deploys the application with Docker Compose.
5. Checks the task API from the frontend container, retrying for up to 10 minutes.

The pipeline accepts `POSTGRES_DB`, `POSTGRES_USER`, `OLLAMA_MODEL`, and `ARTEMIS_USER` parameters. The database name/user and Artemis user must match the values used when their persistent volumes were initialized. `OLLAMA_MODEL` defaults to `qwen2.5:1.5b`; selecting another tag causes Ollama to pull it into its persistent model volume.

The Compose project name for the application is fixed to `my-angular-app`, matching the existing local stack. This keeps the existing PostgreSQL, Ollama, and Artemis volumes in use. Do not run competing manual deployments while Jenkins is deploying.

The pipeline does not run Angular unit tests or backend JUnit tests yet. The current backend Dockerfile packages with `-DskipTests`; Angular production compilation is checked during the frontend image build.

## App and Jenkins lifecycle

The application remains available at [http://localhost:8080](http://localhost:8080); Jenkins is at [http://localhost:8081](http://localhost:8081). ActiveMQ Artemis runs inside Docker on the private Compose network and is not published to a host port.

Stop Jenkins without deleting its configuration or jobs:

```powershell
docker compose -f docker-compose.jenkins.yml down
```

Start it again:

```powershell
docker compose -f docker-compose.jenkins.yml up -d
```

Jenkins configuration and job data are stored in the `jenkins_home` Docker volume. The app database, Ollama models, and Artemis data are stored in separate volumes. Rebuilding or stopping these services does not normally delete their data. Avoid `down -v`; it removes the volumes belonging to the Compose project you are stopping.

## Security note

Mounting `/var/run/docker.sock` gives Jenkins jobs broad control of the Docker Desktop daemon and, effectively, the host. This is convenient for a trusted local learning environment, but do not expose this Jenkins instance publicly or run untrusted jobs on it. Production CI should use isolated agents and a deliberately secured Docker build/deployment service rather than granting the controller unrestricted Docker socket access.

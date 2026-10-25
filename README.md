# TaskManager-AI-Docker-Jenkins
Dockerized Angular and Spring Boot task manager with PostgreSQL and a local Ollama AI assistant, plus a Jenkins pipeline for local build and deployment.

# Project description
Local AI Task Manager is a full-stack task management application with an AI chat assistant. Users can manage tasks through the web interface or ask the assistant questions about their task list. The assistant runs locally using Ollama, can suggest task changes, and requires user confirmation before creating, updating, or deleting tasks. Conversations and tasks are stored in PostgreSQL.

The entire application runs with Docker Compose and includes a Jenkins-in-Docker pipeline for building, deploying, and verifying the app locally.

# Tech stack
  Frontend: Angular 18, TypeScript, HTML, CSS
  Backend: Java 21, Spring Boot, Maven, REST APIs, Spring Data JPA, Jakarta Validation
  Database: PostgreSQL
  Local AI: Ollama with a configurable language model, Qwen 2.5 1.5B by default
  Containers: Docker, Docker Compose, Nginx
  CI/CD: Jenkins Pipeline running in Docker

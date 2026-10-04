# LiveShield — Enterprise BioSecurity Operations Command

[![Deploy to Render](https://render.com/images/deploy-to-render-button.svg)](https://render.com/deploy?repo=https://github.com/Aakash-h-04/liveshield-v2)
[![LiveShield CI / CD](https://github.com/Aakash-h-04/liveshield-v2/actions/workflows/ci.yml/badge.svg)](https://github.com/Aakash-h-04/liveshield-v2/actions/workflows/ci.yml)

LiveShield is a modern, enterprise-grade biosecurity operations and surveillance platform for agricultural facilities, livestock management, and perimeter security. Built with **Spring Boot 3**, **Thymeleaf**, **PostgreSQL**, **Chart.js**, and automated **Email + WhatsApp Outbound Dispatch pipelines**.

---

## Key Features

- **Biosecurity Command Center**: Real-time high-density KPI telemetry cards, threat matrix gauge, compliance benchmarking vs. the 80% National Standard, livestock herd health surveillance, and visitor flow velocity.
- **Smart Gate & Perimeter Access Control**: Dual biometric verification featuring real-time camera face match and cryptographic QR code validation before granting facility entry.
- **Automated Outbound Alerts**: Multi-channel biosecurity alert dispatches via Gmail (OAuth2/SMTP) and Twilio WhatsApp with real-time audit logging.
- **Database Management Console**: Full table inspection, search, and record management for production facilities, livestock batches, and visitor clearance logs.
- **Biosecurity Compliance Passport**: Verifiable compliance passports and digital visitor e-passes with QR verification.
- **Adaptive Dark / Light Themes**: High-contrast, tailored enterprise palettes persisted seamlessly in browser storage.

---

## Tech Stack

- **Backend**: Spring Boot 3.5.5, Java 21 / 25, Spring Data JPA, Hibernate, Spring Security Crypto
- **Database**: PostgreSQL 17 (HikariCP connection pooling)
- **Frontend**: Thymeleaf, Vanilla CSS Enterprise Design System, Chart.js, HTML5 Canvas Biometrics
- **Notification Services**: Gmail API / JavaMailSender, Twilio WhatsApp API
- **Containerization**: Docker, Docker Compose

---

## Quick Start (Local Development)

### 1. Prerequisites
- Java 21+
- Maven 3.9+
- Docker & Docker Compose

### 2. Configure Environment
Copy the example environment file and set your credentials:
```bash
cp .env.example .env
```

### 3. Start Database
```bash
docker compose up postgres -d
```

### 4. Run Application
```bash
mvn spring-boot:run
```
Access the dashboard at `http://localhost:8081`.

---

## Full-Stack Docker Deployment

To launch the complete application with PostgreSQL in Docker containers:
```bash
docker compose up -d --build
```

---

## Cloud Deployment (Render / Railway / AWS / VPS)

1. **Environment Variables**:
   Configure the following environment variables in your deployment environment:
   - `DATABASE_URL` (e.g. `jdbc:postgresql://<host>:<port>/<dbname>`)
   - `DATABASE_USERNAME`
   - `DATABASE_PASSWORD`
   - `PORT` (e.g. `8081`)
   - `MAIL_USERNAME`
   - `MAIL_PASSWORD`
   - `ADMIN_EMAILS`
   - `TWILIO_ACCOUNT_SID`
   - `TWILIO_AUTH_TOKEN`
   - `TWILIO_WHATSAPP_FROM`
   - `LIVESHIELD_ADMIN_WHATSAPP_NUMBERS`

2. **Container Build**:
   The included multi-stage `Dockerfile` packages the application into an optimized Eclipse Temurin JRE runtime image.

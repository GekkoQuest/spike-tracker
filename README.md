# SpikeTracker

[![Java](https://img.shields.io/badge/Java-21+-2ea44f)](#) 
[![SpringBoot](https://img.shields.io/badge/Spring%20Boot-3.5.x-brightgreen)](#)
[![Docker](https://img.shields.io/badge/Docker-supported-blue?logo=docker)](#)
[![WebSocket](https://img.shields.io/badge/WebSocket-Real--time-orange)](#)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue?logo=postgresql)](#)
[![Prometheus](https://img.shields.io/badge/Monitoring-Prometheus-orange?logo=prometheus)](#)
[![CI/CD Pipeline](https://github.com/GekkoQuest/spike-tracker/actions/workflows/ci.yml/badge.svg)](https://github.com/GekkoQuest/spike-tracker/actions/workflows/ci.yml)

> **Real-Time Valorant Esports Match Tracker**

SpikeTracker is a production-ready application for monitoring Valorant esports matches with real-time WebSocket updates, persistent data storage, comprehensive monitoring, and a modern UI.

## ✨ Key Features

- 🔴 **Real-time updates** - WebSocket connections with polling fallback
- 🗄️ **PostgreSQL database** - Complete match history and statistics
- 🎮 **Gaming-inspired UI** - Dark theme, responsive design, smooth animations
- 🛡️ **Security & validation** - Rate limiting, input sanitization, CORS protection
- 🎥 **Stream integration** - Automatic detection of Twitch/YouTube links
- 📊 **Monitoring** - Health checks and Prometheus metrics
- 🌍 **International support** - Team countries and flag mapping

---

## 🏗️ Tech Stack

### Backend
- **Spring Boot 3.5.x** with Java 21
- **PostgreSQL 16** with connection pooling
- **WebSocket/STOMP** for real-time communication
- **Spring Security** for authentication and CORS
- **Flyway** for database migrations
- **Caffeine** for caching

### Frontend
- **Thymeleaf** templates with modern JavaScript
- **Responsive CSS** with mobile-first design
- **WebSocket client** with automatic reconnection

### Infrastructure
- **Docker** with multi-stage builds
- **Prometheus** metrics collection
- **GitHub Actions** CI/CD pipeline
- **VLR.gg API** integration with JSoup scraping

---

## 🚀 Quick Start with Docker

### Prerequisites
- Docker and Docker Compose
- 1.5GB+ available RAM
- PostgreSQL-compatible environment

### 1. Clone & Configure
```bash
git clone https://github.com/GekkoQuest/spike-tracker.git
cd spike-tracker
cp .env.example .env

# Edit .env with your database credentials
nano .env
```

### 2. Deploy with Docker Compose
```bash
# Production deployment with PostgreSQL
docker-compose up -d

# View logs
docker-compose logs -f spike-tracker

# Check health
curl http://localhost:8080/api/health
```

### 3. Access Application
- **Web Interface:** http://localhost:8080
- **API Health:** http://localhost:8080/api/health
- **Live Matches API:** http://localhost:8080/api/matches
- **Metrics:** http://localhost:8080/actuator/prometheus

---

## 🛠️ Development Setup

### Local Development
```bash
# Clone repository
git clone https://github.com/GekkoQuest/spike-tracker.git
cd spike-tracker

# Start PostgreSQL (Docker)
docker run --name spike-postgres -e POSTGRES_PASSWORD=spiketracker -e POSTGRES_DB=spiketracker_dev -p 5432:5432 -d postgres:16

# Run application
./mvnw spring-boot:run -Dspring.profiles.active=dev
```

### Development Features
- 🔄 **Hot reload** - Automatic restart on code changes
- 🐛 **Debug support** - Remote debugging capability
- 📊 **Dev tools** - Enhanced error pages and debugging information
- 🔍 **SQL logging** - Hibernate query debugging in development
- 🚀 **Relaxed security** - Reduced rate limiting for development

---

## 🌐 API Documentation

### Core Endpoints

#### Live Matches
```bash
GET /api/matches
# Returns current live matches with real-time data
```

#### Match History
```bash
GET /api/matches/history?limit=20
# Returns recent completed matches with statistics
```

#### Team Matches
```bash
GET /api/matches/team/{teamName}?limit=10
# Returns match history for specific team
```

#### Health & Monitoring
```bash
GET /api/health
# Application health status and metrics

GET /actuator/prometheus
# Prometheus metrics endpoint
```

#### Statistics
```bash
GET /api/stats
# Team statistics, event data, and analytics
```

### WebSocket Events
```javascript
// Connect to WebSocket
const socket = new SockJS('/ws');
const stompClient = Stomp.over(socket);

// Subscribe to live match updates
stompClient.subscribe('/topic/matches', (message) => {
    const matches = JSON.parse(message.body);
    // Handle real-time match updates
});
```

---

## 📊 Monitoring & Observability

### Prometheus Metrics
Set up monitoring with the provided `monitoring/prometheus.yml`:

```yaml
# monitoring/prometheus.yml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'spiketracker'
    static_configs:
      - targets: ['spike-tracker:8080']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 10s
```

### Key Metrics Tracked
- **API Performance**: Response times, error rates, circuit breaker status
- **WebSocket Connections**: Active connections, message throughput
- **Database**: Connection pool usage, query performance
- **Business Metrics**: Live match count, update frequency, user engagement
- **JVM Metrics**: Memory usage, garbage collection, thread pools

### Health Monitoring
```bash
# Application health
curl http://localhost:8080/api/health

# Detailed health with components
curl http://localhost:8080/actuator/health

# Readiness probe (Kubernetes)
curl http://localhost:8080/actuator/health/readiness

# Liveness probe (Kubernetes)  
curl http://localhost:8080/actuator/health/liveness
```

---

## 🗄️ Database Schema

### Core Tables

#### match_history
- Stores completed match records with full details
- Indexed on completion time, teams, and events
- Supports winner detection and duration tracking

#### match_tracking  
- Tracks live matches with real-time updates
- Manages match lifecycle from start to completion
- Enables match duration calculation

### Migration Management
```bash
# Check migration status
./mvnw flyway:info

# Apply migrations manually
./mvnw flyway:migrate

# Repair migration history (if needed)
./mvnw flyway:repair
```

---

## 🐳 Production Deployment

### Environment Configuration

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `DATABASE_URL` | ✅ | - | PostgreSQL connection string |
| `DATABASE_USERNAME` | ✅ | - | Database username |
| `DATABASE_PASSWORD` | ✅ | - | Database password |
| `SPRING_PROFILES_ACTIVE` | ❌ | `prod` | Application profile |
| `MAX_MEMORY` | ❌ | `512m` | JVM maximum heap size |
| `APP_SECURITY_ALLOWED_ORIGINS` | ❌ | localhost | CORS allowed origins |

### Production Optimizations
- **Multi-stage Docker builds** - Minimal runtime image (Alpine Linux)
- **Non-root execution** - Security hardening with dedicated user
- **Resource limits** - Configured memory and CPU constraints
- **Health checks** - Kubernetes-ready probe configuration
- **Graceful shutdown** - Clean resource cleanup on termination
- **Connection pooling** - Optimized database connection management

### Kubernetes Deployment
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: spike-tracker
spec:
  replicas: 2
  selector:
    matchLabels:
      app: spike-tracker
  template:
    metadata:
      labels:
        app: spike-tracker
    spec:
      containers:
      - name: spike-tracker
        image: spike-tracker:latest
        ports:
        - containerPort: 8080
        env:
        - name: DATABASE_URL
          valueFrom:
            secretKeyRef:
              name: spike-tracker-secrets
              key: database-url
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
```

---

## 🔧 Configuration Profiles

### Development Profile (`dev`)
- **Enhanced logging** - Debug level for application components
- **SQL logging** - Hibernate query debugging
- **Hot reload** - Template and static resource reloading
- **Relaxed security** - Higher rate limits, disabled HTTPS requirements
- **Local database** - H2 or local PostgreSQL configuration

### Production Profile (`prod`)
- **Optimized logging** - INFO level with structured output
- **Security hardening** - Strict CORS, HTTPS enforcement, rate limiting
- **Performance tuning** - Connection pooling, caching optimization
- **Monitoring** - Full metrics collection and health checks

### Test Profile (`test`)  
- **In-memory database** - H2 for fast test execution
- **Mock integrations** - Disabled external API calls
- **Simplified security** - Minimal security configuration for testing

---

## 🚨 Troubleshooting

### Common Issues

**Database Connection Errors**
```bash
# Check database connectivity
docker-compose logs postgres

# Verify connection string
psql $DATABASE_URL -c "SELECT version();"

# Reset database (development only)
docker-compose down -v && docker-compose up -d
```

**WebSocket Connection Issues**
- Check browser console for connection errors
- Verify firewall allows WebSocket connections
- Application automatically falls back to REST polling
- Check CORS configuration for production domains

**Performance Issues**
```bash
# Monitor JVM metrics
curl http://localhost:8080/actuator/metrics/jvm.memory.used

# Check database connection pool
curl http://localhost:8080/actuator/metrics/hikaricp.connections.active

# Review cache hit rates
curl http://localhost:8080/actuator/metrics/cache.gets
```

### Container Troubleshooting
```bash
# Check container logs
docker-compose logs -f spike-tracker

# Access container shell
docker-compose exec spike-tracker sh

# Check resource usage
docker stats spike-tracker-app

# Restart services
docker-compose restart spike-tracker
```

---

## 🔄 CI/CD Pipeline

The project includes automated GitHub Actions workflow:

### Automated Testing
- ✅ **Unit Tests** - Comprehensive test suite with coverage reporting
- ✅ **Integration Tests** - Database and API integration validation  
- ✅ **Security Scanning** - CodeQL static analysis for vulnerabilities
- ✅ **Docker Build** - Container image building and validation

### Pipeline Configuration
Located in `.github/workflows/ci-cd.yml` with PostgreSQL test database and Java 21 support.

---

## 📺 Live Demo

Experience SpikeTracker in production: **https://spike.gekko.quest**

---

## 🗺️ Roadmap

### Completed ✅
- [x] **Real-time WebSocket architecture** with automatic fallback
- [x] **PostgreSQL integration** with full match persistence
- [x] **Advanced monitoring** with Prometheus metrics
- [x] **Production deployment** with Docker and health checks
- [x] **Comprehensive error handling** and circuit breaker patterns
- [x] **Security hardening** with rate limiting and input validation
- [x] **CI/CD pipeline** with automated testing and security scanning

### In Progress 🔄
- [ ] **Enhanced analytics dashboard** with historical trend analysis
- [ ] **Team performance metrics** and head-to-head statistics
- [ ] **Tournament bracket visualization** for playoff matches
- [ ] **Advanced caching strategies** with Redis integration

### Planned 📋
- [ ] **GraphQL API** for flexible data querying
- [ ] **Real-time notifications** via webhooks and Discord integration
- [ ] **Machine learning predictions** for match outcomes
- [ ] **Mobile application** for iOS and Android
- [ ] **Multi-esports support** (CS2, League of Legends, Dota 2)

---

## 📄 License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for details.

---

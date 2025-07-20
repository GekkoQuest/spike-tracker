# SpikeTracker

[![Java](https://img.shields.io/badge/Java-21+-2ea44f)](#) 
[![SpringBoot](https://img.shields.io/badge/Spring%20Boot-3.4.x-brightgreen)](#)
[![Docker](https://img.shields.io/badge/Docker-supported-blue?logo=docker)](#)
[![WebSocket](https://img.shields.io/badge/WebSocket-Real--time-orange)](#)

> **Modern Real-Time Valorant Match Tracker with WebSocket Updates & Gaming UI**

SpikeTracker is a live-tracking application built to monitor Valorant esports matches using the **unofficial VLR.gg API**. Features real-time WebSocket updates, a modern gaming-inspired UI, and comprehensive match history tracking.

## ✨ Key Features

- 🔴 **Real-time WebSocket updates** - Instant match data without page refreshes
- 🎮 **Modern UI** - Dark theme with Valorant-inspired design and smooth animations  
- 📱 **Fully responsive** - Optimized for desktop, tablet, and mobile devices
- 📊 **Match history & statistics** - Track completed matches with duration and winners
- 🌍 **Country/region support** - Team countries displayed clearly for international tournaments
- ⚡ **High performance** - 90% fewer server requests with intelligent change detection
- 🐳 **Docker ready** - Production-ready containerization with health checks

---

## 🏗️ Architecture & Tech Stack

### Backend
- **Spring Boot 3.4.x** with Java 21
- **WebSocket/STOMP** for real-time updates
- **Spring Cache** for performance optimization
- **Async processing** for non-blocking operations
- **Comprehensive error handling** and retry logic

### Frontend  
- **Modern responsive design** with CSS Grid/Flexbox
- **Real-time JavaScript** with WebSocket client
- **Gaming-inspired UI** with smooth animations
- **Progressive enhancement** with polling fallback

### Infrastructure
- **Docker** with multi-stage builds and security hardening
- **Health monitoring** with automatic restart capabilities
- **Environment-based configuration** for dev/staging/prod
- **Resource optimization** and caching strategies

### Integrations
- **VLR.gg API** for live match data
- **Stream platform detection** (Twitch, YouTube, etc.)

---

## 🚀 Quick Start with Docker

### Prerequisites
- Docker and Docker Compose

### 1. Clone & Configure
```bash
git clone https://github.com/GekkoQuest/spike-tracker.git
cd spike-tracker
cp .env.example .env
```

### 2. Deploy with Docker Compose
```bash
# Production deployment
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

---

## 🛠️ Development Setup

### Local Development
```bash
# Clone repository
git clone https://github.com/GekkoQuest/spike-tracker.git
cd spike-tracker

# Run with Maven
./mvnw spring-boot:run
```

### Docker Development Environment
```bash
# Development with hot reload and debugging
docker-compose up spike-tracker-dev

# Debug port available on localhost:5005
```

**Development Features:**
- 🔄 Hot reload with volume mounting  
- 🐛 Remote debugging on port 5005
- 📦 Maven cache persistence
- 🔍 Enhanced logging for development

---

## 🌐 Web Interface Features

### Live Matches Dashboard
- **Real-time updates** via WebSocket (no page refresh needed)
- **Professional match cards** with team info, scores, and maps
- **Live status indicators** with pulsing animations
- **Stream integration** with direct links to Twitch/YouTube
- **Mobile-optimized** responsive design

### Match History
- **Completed match tracking** with timestamps
- **Winner identification** and match duration
- **Event and tournament information**
- **Historical performance data**

### Smart Features
- **Connection status monitoring** with automatic fallback
- **Intelligent change detection** prevents unnecessary updates
- **Informative tooltips** explain data availability
- **Accessibility optimized** with proper contrast and screen reader support

---

## 📊 Performance & Monitoring

### Health Monitoring
```bash
# Application health check
curl http://localhost:8080/api/health

# Docker container health
docker-compose ps

# View application metrics
curl http://localhost:8080/actuator/metrics
```

### Performance Metrics
- **90% fewer server requests** compared to polling-only systems
- **Sub-second update latency** for live match changes
- **Automatic reconnection** with exponential backoff
- **Resource-efficient caching** for static data

---

## 🐳 Production Deployment

### Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `SPRING_PROFILES_ACTIVE` | ❌ | `prod` | Application profile (dev/prod) |
| `JAVA_OPTS` | ❌ | `-Xmx512m -Xms256m` | JVM memory settings |

### Docker Configuration
```yaml
# docker-compose.yml
services:
  spike-tracker:
    build: .
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "wget", "--spider", "http://localhost:8080/api/health"]
      interval: 30s
      timeout: 10s
      retries: 3
```

### Production Optimizations
- **Multi-stage Docker builds** for minimal image size
- **Non-root user execution** for security
- **Health checks** for automatic recovery
- **Resource limits** and logging configuration
- **Graceful shutdown** handling

---

## 🔧 Configuration

### Application Profiles
- **Development (`dev`)**: Enhanced logging, hot reload, debug endpoints
- **Production (`prod`)**: Optimized caching, minimal logging, security headers

### Customization Options
- **Update intervals**: Configure WebSocket and polling frequencies
- **UI themes**: Modify CSS custom properties for color schemes  
- **Caching policies**: Adjust cache TTL for different data types

---

## 🚨 Troubleshooting

### Common Issues

**WebSocket connection issues:**
- Application automatically falls back to REST polling
- Check browser console for connection errors
- Verify network allows WebSocket connections

**Docker container issues:**
```bash
# Check container logs
docker-compose logs spike-tracker

# Restart services
docker-compose restart

# Rebuild with latest changes
docker-compose up --build
```

### Health Checks
```bash
# Application status
curl http://localhost:8080/api/health

# Match data availability  
curl http://localhost:8080/api/matches

# WebSocket endpoint test
# Open browser dev tools -> Network -> WS tab
```

---

## 📺 Live Demo

Experience SpikeTracker in action: **https://spike.gekko.quest**

---

## 🗺️ Roadmap

### Completed ✅
- [x] Real-time WebSocket implementation
- [x] Modern gaming UI with responsive design
- [x] Match history and statistics tracking
- [x] Docker deployment with health monitoring
- [x] Comprehensive error handling and fallbacks
- [x] Performance optimizations and caching

### Planned 🔄
- [ ] **Player statistics** and individual performance tracking
- [ ] **Tournament brackets** and playoff visualization  
- [ ] **Match predictions** and betting odds integration
- [ ] **Advanced filtering** by region, tournament, and teams
- [ ] **API rate limiting** and usage analytics
- [ ] **Database integration** for persistent match history
- [ ] **Multi-language support** for international users
- [ ] **Discord bot integration** (optional add-on)

### Future Considerations 💭
- [ ] **Mobile app** for iOS/Android
- [ ] **Integration with other esports** (CS2, League of Legends)

---

## 🤝 Contributing

We welcome contributions! Please see our contributing guidelines for:
- Code style and standards
- Pull request process  
- Issue reporting templates
- Development environment setup

---

## 📄 License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for details.

---

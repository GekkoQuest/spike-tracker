# SpikeTracker

[![Java](https://img.shields.io/badge/Java-21+-2ea44f)](#) 
[![SpringBoot](https://img.shields.io/badge/Spring%20Boot-3.2.x-brightgreen)](#)
[![Discord JDA](https://img.shields.io/badge/JDA-5.x-blueviolet?logo=discord)](#)
[![Docker](https://img.shields.io/badge/Docker-supported-blue?logo=docker)](#)

> **Real-Time Valorant Match Tracker, Discord Integration & Live Score Web Interface Powered by VLR.gg**

SpikeTracker is a modern live-tracking application built to monitor professional Valorant matches using data provided by the **unofficial VLR.gg API**. It sends **real-time updates** directly to your Discord server and displays current match details through an intuitive web UI.

- 🔥 **Live Valorant match data:** Real-time scores, teams, and live updates.
- 📱 **Discord integration:** Automatic embed updates delivered to any configured Discord channel.
- 🌐 **Responsive frontend UI:** Simple webpage showing ongoing match scores and updates (no Discord account required).
- 🐳 **Dockerized deployment:** Easy to deploy, scalable, and containerized setup.

---

## 📌 Features Overview

- ✅ **Real-time Integration:** Fetches live Valorant match data directly from the unofficial VLR.gg API.
- ✅ **Discord Bot Capability:** Continuous live match updates through embeds; easy Discord channel configuration and management via commands.
- ✅ **Frontend Web View:** User-friendly real-time match visualization.
- ✅ **Docker Support:** Optimized Dockerfile for easy containerization and production deployment.

---

## 📋 Tech Stack

- **Backend:** Spring Boot 3.2.x, Java 21, Lombok, Jackson, Jsoup, RestClient
- **Frontend:** Thymeleaf, JavaScript, HTML, CSS
- **Discord Integration:** Java Discord API (JDA)
- **Containerization:** Docker
- **Data:** Unofficial [VLR.gg API](https://vlrggapi.vercel.app/)

---

## ⚙️ Requirements

- ✅ Java 21 or higher
- ✅ Maven
- ✅ Discord Bot Token ([Guide to Create Bot](https://discord.com/developers/docs/getting-started))
- ✅ Docker (optional but highly recommended for easy deployment)

---

## 🚩 Installation & Setup

Clone the repository and enter the project directory:

```bash
git clone https://github.com/GekkoQuest/spike-tracker.git
cd spike-tracker
```

Set your Discord bot token as an environment variable:

**Linux/MacOS**:
```bash
export DISCORD_TOKEN=YOUR_DISCORD_BOT_TOKEN
```

Windows (cmd):
```bash
set DISCORD_TOKEN=YOUR_DISCORD_BOT_TOKEN
```

Windows PowerShell:
```bash
$env:DISCORD_TOKEN="YOUR_DISCORD_BOT_TOKEN"
```

Run the application locally:
```bash
./mvnw spring-boot:run
```

Open `http://localhost:8080` to view running matches.

## 🤖 Discord Usage

To receive Discord updates, you'll need to define a channel by using the following command:
```bash
#setchannel
```

Once the command is set, the bot will continuously provide live match updates.

## ⚠️ Important

The current implementation prioritizes Discord integration. If your Discord channel isn't set, then the website will not showcase Valorant matches. Future releases may improve the website to run independently.

## 🐳 Docker Deployment

Build the Docker image:
```bash
docker build --build-arg DISCORD_TOKEN=YOUR_DISCORD_BOT_TOKEN -t spike-tracker .
```

Run the Docker container:
```bash
docker run -d -p 8080:8080 -e DISCORD_TOKEN=YOUR_DISCORD_BOT_TOKEN spike-tracker
```

Visit the deployed app:
```
http://localhost:8080
```

## 📺 Live Demo
You can view a live demo of this application at: https://spike.gekko.quest.

## Roadmap/TODO
- [ ] Add player statistics and analytics at match completion.
- [ ] Fetch historical/archived match data.
- [ ] Configurable options for both the website and Discord.
- [ ] Discord commands and permission management for said commands.
- [ ] Ability to deploy independently of Discord configuration.

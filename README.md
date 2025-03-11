# SpikeTracker

SpikeTracker is a live match tracking system for professional Valorant matches. It utilizes the unofficial VLR.gg API to fetch real-time match data, sending updates to Discord channels and displaying ongoing match scores on a simple frontend website.

## Features

- Fetches live match data from the unofficial VLR.gg API.
- Discord Bot Integration: Sends match updates to a configured Discord channel.
- Simple web interface displaying live match data and scores for users without Discord.

## Requirements
- Java 21+
- Spring Boot
- Discord Bot Token (Note: Unless you decide to change it yourself, it must be passed as an environmental variable)
- Docker (Optional, but preferred for containerized deployment)

## Installation

- Clone the repository

  `https://github.com/GekkoQuest/spike-tracker.git`
  
  `cd spike-tracker`
  
- Setup your environmental variables
  
  `export DISCORD_TOKEN=YOUR_DISCORD_BOT_TOKEN`
  
  `$env:DISCORD_TOKEN="YOUR_DISCORD_BOT_TOKEN"`
  
- Build and run the application

  `./mvnw spring-boot:run`

## Usage
- Discord

To receive match updates on Discord, you must define a channel to send updates to by doing the `#setchannel` command in your server.

- Website

If you're hosting this yourself, you'll need to make sure your Discord channel is currently defined as mentioned earlier. At the moment, I do not intend on putting support otherwise as this is meant to be more of a Discord bot rather than a website.

## Docker Deployment (Optional, but recommended)
- Build the docker image:
  
  `docker build -t spike-tracker .`
  
- Run the docker container:
  
  `docker run -d -p 8080:8080 -e DISCORD_TOKEN=YOUR_DISCORD_BOT_TOKEN spike-tracker`
  

## TODO
- Add player stats to showcase at the end of match.
- Ability to fetch previous/older match data.
- Add configuration options for Discord messages.
- Make all Discord messages embeded rather than plain messages.
- Implement more Discord commands with permissions checking.

## Demo
You can view a live demo of this project at http://spike.gekko.quest, where you can see ongoing professional Valorant matches, team names, and scores.

## Screenshots
![Demo](https://i.imgur.com/nc5AzY3m.jpg)

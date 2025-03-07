
# SpikeTracker

SpikeTracker is a live match tracking system for professional Valorant that utilizes the unofficial VLR.gg api provided by https://vlrggapi.vercel.app/. It allows users to receive real-time notifications about ongoing matches.




## Features

- Fetches live match data from the unofficial VLR.gg api.
- Discord integration (WIP).

## Requirements
- Java 21+: This project is developed using Java 21 or higher.
- Spring Boot: For building and running the application.
- Discord Bot Token: Required for the Discord bot to function.
## Installation

- Clone the repository `https://github.com/GekkoQuest/spike-tracker.git`

- Add your Discord token to `application.properties`
- Run the application by doing `./mvnw spring-boot:run`
## Usage
The bot will not function unless you define a Discord channel to send updates to.

You can do so by doing the following command in the channel of your choice:
```bash
#setchannel
```
## TODO
- Add player stats to showcase at the end of match.
- Ability to fetch previous/older match data.
- Add configuration options for Discord messages.
- Implement more Discord commands with permissions checking.

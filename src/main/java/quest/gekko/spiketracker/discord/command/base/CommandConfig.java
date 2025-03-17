package quest.gekko.spiketracker.discord.command.base;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class CommandConfig {
    @Bean
    public CommandManager commandManager(final List<Command> commands) {
        final CommandManager commandManager = new CommandManager();

        commands.forEach(command -> {
            final String commandName = command.getCommandName();
            commandManager.registerCommand(commandName, command);
        });

        return commandManager;
    }
}
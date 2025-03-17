package quest.gekko.spiketracker.discord.command.base;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Component
public class CommandManager extends ListenerAdapter {
    private final Map<String, Command> commands = new HashMap<>();

    public void registerCommand(final String commandName, final Command command) {
        commands.put(commandName, command);
    }

    @Override
    public void onMessageReceived(final MessageReceivedEvent event) {
        if (event.getAuthor().isBot())
            return;

        final String[] splitMessage = event.getMessage().getContentRaw().split(" ");
        if (splitMessage.length == 0)
            return;

        final String commandName = splitMessage[0].toLowerCase();
        final String[] args = Arrays.copyOfRange(splitMessage, 1, splitMessage.length);

        final Command command = commands.get(commandName);

        if (command == null) {
            return;
        }

        if (command.hasPermission(event.getMember())) {
            command.execute(event, args);
        } else {
            event.getChannel().sendMessage("You don't have permission to use this command").queue();
        }
    }
}

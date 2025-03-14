package quest.gekko.spiketracker.discord.command.base;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public interface Command {

    String getCommandName();

    boolean hasPermission(final Member member);

    void execute(final MessageReceivedEvent event, final String[] args);

}

package quest.gekko.spiketracker.discord.command.impl;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.springframework.stereotype.Component;
import quest.gekko.spiketracker.discord.command.base.Command;
import quest.gekko.spiketracker.service.MatchNotifierService;

@Component
public class SetChannelCommand implements Command {
    private final MatchNotifierService notifierService;

    public SetChannelCommand(final MatchNotifierService notifierService) {
        this.notifierService = notifierService;
    }

    @Override
    public String getCommandName() {
        return "#setchannel";
    }

    @Override
    public boolean hasPermission(final Member member) {
        return member.hasPermission(Permission.ADMINISTRATOR);
    }

    @Override
    public void execute(final MessageReceivedEvent event, final String[] args) {
        if (event.getChannel() instanceof TextChannel channel) {
            notifierService.setChannel(channel);
            channel.sendMessage("Channel has been set for match updates!").queue();
        } else {
            event.getChannel().sendMessage("This command can only be executed in text channels!").queue();
        }
    }
}

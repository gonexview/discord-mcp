package dev.saseq.configs;

import dev.saseq.services.DiscordService;
import dev.saseq.services.MessageService;
import dev.saseq.services.UserService;
import dev.saseq.services.ChannelService;
import dev.saseq.services.CategoryService;
import dev.saseq.services.WebhookService;
import dev.saseq.services.ThreadService;
import dev.saseq.services.ModerationService;
import dev.saseq.services.RoleService;
import dev.saseq.services.VoiceChannelService;
import dev.saseq.services.ScheduledEventService;
import dev.saseq.services.InviteService;
import dev.saseq.services.ChannelPermissionService;
import dev.saseq.services.EmojiService;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
public class DiscordMcpConfig {

    private static final Logger log = LoggerFactory.getLogger(DiscordMcpConfig.class);

    @Value("${discord.mcp.tools.enabled:}")
    private String enabledToolsConfig;

    @Bean
    public ToolCallbackProvider discordTools(DiscordService discordService,
                                             MessageService messageService,
                                             UserService userService,
                                             ChannelService channelService,
                                             CategoryService categoryService,
                                             WebhookService webhookService,
                                             ThreadService threadService,
                                             RoleService roleService,
                                             ModerationService moderationService,
                                             VoiceChannelService voiceChannelService,
                                             ScheduledEventService scheduledEventService,
                                             InviteService inviteService,
                                             ChannelPermissionService channelPermissionService,
                                             EmojiService emojiService) {
        ToolCallbackProvider delegate = MethodToolCallbackProvider.builder().toolObjects(
                discordService,
                messageService,
                userService,
                channelService,
                categoryService,
                webhookService,
                threadService,
                roleService,
                moderationService,
                voiceChannelService,
                scheduledEventService,
                inviteService,
                channelPermissionService,
                emojiService
        ).build();

        if (enabledToolsConfig == null || enabledToolsConfig.isBlank()) {
            log.warn("discord.mcp.tools.enabled is not configured. ALL tools are exposed.");
            return delegate;
        }

        Set<String> allowedTools = Arrays.stream(enabledToolsConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());

        log.info("Tool allowlist active: {} tools enabled out of available set.", allowedTools.size());
        return new FilteredToolCallbackProvider(delegate, allowedTools);
    }

    @Bean
    public JDA jda(@Value("${DISCORD_TOKEN:}") String token) throws InterruptedException {
        if (token == null || token.isEmpty()) {
            System.err.println("ERROR: The environment variable DISCORD_TOKEN is not set. Please set it to run the application properly.");
            System.exit(1);
        }
        return JDABuilder.createDefault(token)
                .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_VOICE_STATES, GatewayIntent.SCHEDULED_EVENTS)
                .build()
                .awaitReady();
    }
}

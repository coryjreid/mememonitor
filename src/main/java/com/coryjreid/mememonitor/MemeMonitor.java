package com.coryjreid.mememonitor;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.login.LoginException;
import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class MemeMonitor implements EventListener {
    private static final Logger logger = LoggerFactory.getLogger(MemeMonitor.class);
    private static final String BOT_TOKEN_KEY = "botToken";
    private static final String CHANNEL_ID_KEY = "channelId";
    private static final String VALID_FILE_EXTENSIONS_KEY = "validFileExtensions";
    private static final String VALID_DOMAIN_NAMES_KEY = "validDomainNames";

    private static Config config;

    public static void main(final String[] args) throws LoginException, InterruptedException {
        if (args.length != 1) {
            logger.error("Missing argument! Please provide a path to a configuration file! Ex: /home/user/bot/config");
            return;
        }
        if (!Files.exists(Path.of(args[0]))) {
            logger.error("Config file does not exist!");
            return;
        }
        config = ConfigFactory.parseFile(new File(args[0]));

        final JDA jda = JDABuilder.createDefault(config.getString(BOT_TOKEN_KEY))
                .addEventListeners(new MemeMonitor())
                .build();

        jda.awaitReady();
    }

    @Override
    public void onEvent(final @NotNull GenericEvent event) {
        if (event instanceof MessageReceivedEvent) {
            final MessageReceivedEvent messageReceivedEvent = (MessageReceivedEvent) event;

            if (messageReceivedEvent.getChannel().getId().equals(config.getString(CHANNEL_ID_KEY))) {
                final Message message = messageReceivedEvent.getMessage();
                final String messageContent = message.getContentRaw();
                final List<Attachment> attachments = message.getAttachments();

                if (isValidMessage(message)) {
                    logger.info("Message permitted from " +
                            message.getAuthor() +
                            "\n\tcontent: \"" +
                            messageContent +
                            "\"\n\tattachments: " +
                            attachments.stream().map(Attachment::getFileName).collect(Collectors.joining(", ")));
                } else {
                    logger.info("Deleting message from " +
                            message.getAuthor() +
                            "\n\tcontent: \"" +
                            messageContent +
                            "\"\n\tattachments: " +
                            attachments.stream().map(Attachment::getFileName).collect(Collectors.joining(", ")));

                    message.delete().reason("Not a link or media file").queue();
                }
            }
        }
    }

    private static boolean isUrl(final String text) {
        try {
            new URL(text).toURI();
            return true;
        } catch (final Exception ignoredException) {
            return false;
        }
    }

    private static boolean isPermittedUrl(final String text) {
        for (final String validUrl : config.getStringList(VALID_DOMAIN_NAMES_KEY)) {
            if (isUrl(text) && text.contains(validUrl)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isValidMessage(final Message message) {
        final String messageContent = message.getContentRaw();
        final String[] messageParts = messageContent.split(" ");
        final List<Attachment> attachments = message.getAttachments();

        final int numberValidAttachments = attachments.stream().mapToInt(value -> config.getStringList(VALID_FILE_EXTENSIONS_KEY).contains(value.getFileExtension()) ? 1 : 0).sum();

        final int numberUrls = Arrays.stream(messageParts).mapToInt(value -> isUrl(value) ? 1 : 0).sum();
        final int numberValidUrls = Arrays.stream(messageParts).mapToInt(value -> isPermittedUrl(value) ? 1 : 0).sum();

        final boolean allUrlsValid = numberUrls == numberValidUrls;
        final boolean allAttachmentsValid = attachments.size() == numberValidAttachments;

        final boolean containsMentions = message.getMentions().size() > 0 || message.getMentionedMembers().size() > 0;

        return allUrlsValid && allAttachmentsValid && (containsMentions || messageContent.isEmpty() || numberValidUrls == messageParts.length);
    }
}

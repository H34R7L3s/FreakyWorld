package h34r7l3s.freakyworld;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import javax.annotation.Nonnull;

import org.bukkit.configuration.file.FileConfiguration;

import javax.security.auth.login.LoginException;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public class DiscordBot extends ListenerAdapter {
    private net.dv8tion.jda.api.JDA jda;
    private String serverId;
    private String token;
    private static int eventProbability; // Static variable to store the probability

    public DiscordBot(FileConfiguration secretsConfig) throws LoginException, InterruptedException {
        if (secretsConfig == null) {
            throw new IllegalStateException("Die Konfiguration darf nicht null sein.");
        }

        this.token = secretsConfig.getString("DiscordAPIKey");
        if (this.token == null || this.token.isEmpty()) {
            throw new IllegalStateException("Der DiscordAPIKey fehlt in der secrets.yml-Datei.");
        }

        this.serverId = secretsConfig.getString("DServerID");
        if (this.serverId == null || this.serverId.isEmpty()) {
            throw new IllegalStateException("Der DServerID fehlt in der secrets.yml-Datei.");
        }

        this.start(); // Start the bot
        this.calculateEventProbability(); // Calculate event probability
        this.announceProbabilityInChannel("1046919237081518220"); // Announce probability
    }

    private void start() throws LoginException, InterruptedException {

        //Boolean zwecks Wartungsmodus

        jda = JDABuilder.createDefault(token)
                .addEventListeners(this) // Add this class as an event listener
                .build();
        jda.awaitReady(); // Block until the bot is connected and ready
    }

    private void calculateEventProbability() {
        Random random = new Random();
        eventProbability = random.nextInt(61) + 40; // Generiert eine Zahl zwischen 40 und 100
    }


    private void announceProbabilityInChannel(String channelId) {
        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            System.err.println("Konnte den Kanal nicht finden!");
            return;
        }

        // Erstellen des ersten Nachrichtenblocks: Event-Wahrscheinlichkeit
        EmbedBuilder probabilityEmbed = new EmbedBuilder();
        probabilityEmbed.setTitle("Event-Wahrscheinlichkeit");
        probabilityEmbed.setDescription("Die Event-Wahrscheinlichkeit betraegt:");
        probabilityEmbed.addField("Wahrscheinlichkeit", eventProbability + "%", false);
        probabilityEmbed.setColor(0x3498DB); // Blaue Farbe für diesen Bereich

        // Erstellen des zweiten Nachrichtenblocks: Freaky Season
        EmbedBuilder seasonEmbed = new EmbedBuilder();
        seasonEmbed.setTitle("Freaky Season");
        seasonEmbed.setDescription("Fortschritt der aktuellen Season:");
        String progress = ":heart: :heart: :black_heart: :black_heart: :black_heart:";
        seasonEmbed.addField("Aktueller Fortschritt", progress, false);
        seasonEmbed.setColor(0x9B59B6); // Lila Farbe für diesen Bereich

        // Verwenden eines ScheduledExecutorService, um beide Nachrichten mit Verzögerung zu senden
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        // Planen der ersten Nachricht
        scheduler.schedule(() -> {
            channel.sendMessageEmbeds(probabilityEmbed.build()).queue();
        }, 5, TimeUnit.SECONDS); // Verzögerung der ersten Nachricht um 5 Sekunden

        // Planen der zweiten Nachricht direkt nach der ersten
        scheduler.schedule(() -> {
            channel.sendMessageEmbeds(seasonEmbed.build()).queue();
        }, 10, TimeUnit.SECONDS); // Verzögerung der zweiten Nachricht um 10 Sekunden

        // Shutdown des Schedulers, nachdem die Aufgaben abgeschlossen sind
        scheduler.shutdown();
    }

    // In DiscordBot Klasse
    public void sendMessageToDiscord(String message) {
        TextChannel channel = jda.getTextChannelById("1172297330221908108"); // Ersetzen Sie YOUR_CHANNEL_ID mit der tatsächlichen ID
        if (channel != null) {
            channel.sendMessage(message).queue();
        } else {
            System.err.println("Konnte den Discord-Kanal nicht finden.");
        }
    }
    public void sendMessageToDiscordStartInfo(String message) {
        TextChannel channel = jda.getTextChannelById("1046919237081518220"); // Ersetzen Sie YOUR_CHANNEL_ID mit der tatsächlichen ID
        if (channel != null) {
            channel.sendMessage(message).queue();
        } else {
            System.err.println("Konnte den Discord-Kanal nicht finden.");
        }
    }


    public static int getEventProbability() {
        return eventProbability; // Allows other classes to access the probability
    }

    public void shutdown() {
        if (jda != null) {
            jda.shutdownNow();
            // Perform any additional cleanup you need here
        }
    }

    public void sendEventMessageToChannel(String channelId, String initiatorName, int startInMinutes, int durationMinutes, String roleID) {
        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            System.err.println("Konnte den Discord-Kanal nicht finden.");
            return;
        }

        // Erstellen der Nachricht mit dem richtigen Rollen-Tag und Event-Informationen
        String message = "<@&1046913269966852207> " + initiatorName + " hat ein Event gestartet!\n\n" +
                "**Startzeit:** In " + startInMinutes + " Minuten\n" +
                "**Dauer:** " + durationMinutes + " Minuten\n" +
                "Seid dabei! Ab zum Spawn und meldet euch bei " + initiatorName + " um teilzunehmen!";

        // Senden der Nachricht
        channel.sendMessage(message).queue();
    }

    //HCFW DOCKER
    public void announceEventWithTimer(String initiatorName, String eventDescription, String timeText, int timeInSeconds) {
        String channelId = "1046919237081518220"; // ID des Info-Kanals
        TextChannel channel = jda.getTextChannelById(channelId);

        if (channel == null) {
            System.err.println("Konnte den Discord-Kanal nicht finden.");
            return;
        }

        // Formatierte Nachricht mit dem Event-Countdown
        EmbedBuilder eventEmbed = new EmbedBuilder();
        eventEmbed.setTitle("FreakyWorld Event!");
        eventEmbed.setDescription("Nutzt die Gelegenheit und schnappt euch die Beute!");
        eventEmbed.addField("", "", false);
        //eventEmbed.addField("Event-Initiator", initiatorName, false);
        eventEmbed.addField("Event-Beschreibung", eventDescription, false);
        eventEmbed.addField(timeText, "<t:" + (System.currentTimeMillis() / 1000 + timeInSeconds) + ":R>", false); // Discord-Timer auf die angegebene Zeit setzen
        eventEmbed.setColor(0x1ABC9C); // Auffällige Farbe für das Embed

        // Verwenden eines ScheduledExecutorService für die Verzögerung
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.schedule(() -> {
            channel.sendMessageEmbeds(eventEmbed.build()).queue();
        }, 3, TimeUnit.MINUTES); // Verzögerung um 3 Minuten

        // Scheduler nach der Ausführung herunterfahren
        scheduler.shutdown();
    }



}

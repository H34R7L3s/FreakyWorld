package h34r7l3s.freakyworld;


import net.dv8tion.jda.api.JDABuilder;
import org.bukkit.configuration.file.FileConfiguration;

import javax.security.auth.login.LoginException;

public class DiscordBot {
    private net.dv8tion.jda.api.JDA jda;
    private String serverId;
    private String token;

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
    }

    private void start() throws LoginException, InterruptedException {
        jda = JDABuilder.createDefault(token).build();
        jda.awaitReady(); // Block until the bot is connected and ready
    }

    public void shutdown() {
        if (jda != null) {
            jda.shutdownNow();
            // Perform any additional cleanup you need here
        }
    }

    // ... rest of the DiscordBot class ...
}

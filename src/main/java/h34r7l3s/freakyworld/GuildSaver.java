package h34r7l3s.freakyworld;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.bukkit.plugin.java.JavaPlugin;

public class GuildSaver {

    private JavaPlugin plugin;

    public GuildSaver(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void saveGuildData(Guild guild) {
        File dataFolder = new File(plugin.getDataFolder(), "guilds"); // The 'guilds' subdirectory
        if (!dataFolder.exists()) {
            dataFolder.mkdirs(); // Create the directory if it doesn't exist
        }

        File guildFile = new File(dataFolder, guild.getName() + ".yml"); // Each guild will have its own file
        YamlConfiguration config = YamlConfiguration.loadConfiguration(guildFile);

        // Saving the guild's basic data
        config.set("name", guild.getName());
        config.set("description", guild.getDescription());
        config.set("homeLocation", guild.getHomeLocation() != null ? guild.getHomeLocation().serialize() : null);
        config.set("members", new ArrayList<>(guild.getMembers()));

        // Saving the member ranks without using streams
        Map<String, String> ranksToSave = new HashMap<>();
        for (Map.Entry<String, Guild.GuildRank> rankEntry : guild.getMemberRanks().entrySet()) {
            ranksToSave.put(rankEntry.getKey(), rankEntry.getValue().name());
        }
        config.set("memberRanks", ranksToSave);

        // Saving the treasury
        Map<String, Integer> treasuryToSave = new HashMap<>();
        for (Map.Entry<Material, Integer> entry : guild.getTreasury().entrySet()) {
            treasuryToSave.put(entry.getKey().toString(), entry.getValue());
        }
        config.set("treasury", treasuryToSave);

        // Saving the guild messages
        config.set("guildMessages", guild.getMessages());

        // Save the file
        try {
            config.save(guildFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}


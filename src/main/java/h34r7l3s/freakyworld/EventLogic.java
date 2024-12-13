package h34r7l3s.freakyworld;

import java.util.Random;
import io.th0rgal.oraxen.api.OraxenItems;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import java.util.Random;



public class EventLogic {
    private final FreakyWorld plugin;
    private final CategoryManager categoryManager;
    private final Map<String, Map<UUID, Integer>> categoryScores;
    private final CustomDatabaseManager customDatabaseManager;

    private final Map<String, List<UUID>> topPlayersRewards = new HashMap<>();
    private final Map<String, UUID> topGuildRewards = new HashMap<>();
    private final GuildManager guildManager;



    public EventLogic(FreakyWorld plugin, CategoryManager categoryManager, CustomDatabaseManager customDatabaseManager, GuildManager guildManager) {
        this.plugin = plugin;
        this.categoryManager = categoryManager;
        this.customDatabaseManager = customDatabaseManager;
        this.categoryScores = new HashMap<>();
        this.guildManager = guildManager;

        for (String category : categoryManager.getCategories()) {
            categoryScores.put(category, new HashMap<>());
        }
    }

    public UUID getLeadingPlayerForCategory(String category) {
        UUID leadingPlayerUUID = null;
        int topScore = -1;

        // Lade die Scores direkt aus der Datenbank
        try (Connection conn = customDatabaseManager.getConnection()) {
            String query = "SELECT player_uuid, score FROM player_scores WHERE category = ? ORDER BY score DESC LIMIT 1";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, category);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        leadingPlayerUUID = UUID.fromString(rs.getString("player_uuid"));
                        topScore = rs.getInt("score");
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Fehler beim Laden der führenden Spieler aus der Datenbank: " + e.getMessage());
        }

        if (leadingPlayerUUID != null) {
            //plugin.getLogger().info("Führender Spieler in Kategorie " + category + ": " + leadingPlayerUUID + " mit Score " + topScore);
        } else {
            plugin.getLogger().info("Kein führender Spieler in Kategorie " + category + " gefunden.");
        }

        return leadingPlayerUUID;
    }




    public int getPlayerScoreForCategory(UUID playerUUID, String category) {
        return customDatabaseManager.getPlayerScore(playerUUID, category);
    }

    public void updatePlayerScore(UUID playerUUID, String category, int amount, boolean isTeamMode) {
        Map<UUID, Integer> scores = categoryScores.get(category);
        if (scores != null) {
            int newScore = scores.getOrDefault(playerUUID, 0) + amount;
            scores.put(playerUUID, newScore);
            categoryScores.put(category, scores);
            customDatabaseManager.updatePlayerScore(playerUUID, category, newScore);
        }
    }



    public void rewardTopPlayers() {
        for (String category : categoryManager.getCategories()) {
            UUID leadingPlayerUUID = getLeadingPlayerForCategory(category);
            if (leadingPlayerUUID != null) {
                Player player = Bukkit.getPlayer(leadingPlayerUUID);
                if (player != null) {
                    rewardPlayer(player, category);
                }
            }
        }
    }

    private void rewardPlayer(Player player, String category) {
        ItemStack reward = new ItemStack(Material.GOLD_INGOT, 1);
        player.getInventory().addItem(reward);
        player.sendMessage("§6Du hast die höchste Punktzahl in der Kategorie " + category + " und erhältst eine Belohnung!");
    }

    public List<String> getCategoryLore(String category) {
        List<String> lore = new ArrayList<>();
        lore.add("§eTop Spieler in Kategorie '" + category + "':");

        Map<UUID, Integer> categoryScoresMap = categoryScores.get(category);
        if (categoryScoresMap != null && !categoryScoresMap.isEmpty()) {
            List<Map.Entry<UUID, Integer>> sortedScores = categoryScoresMap.entrySet()
                    .stream()
                    .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                    .collect(Collectors.toList());

            int place = 1;
            for (Map.Entry<UUID, Integer> entry : sortedScores) {
                if (place <= 5) {
                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (player != null) {
                        lore.add("§7" + place + ". " + player.getName() + ": " + entry.getValue() + " Punkte");
                        place++;
                    }
                } else {
                    break;
                }
            }
        } else {
            lore.add("§cKeine Spieler in dieser Kategorie.");
        }

        return lore;
    }

    //Belohnung 1
    public void calculateAndStoreRewards() {
        for (String category : categoryManager.getCategories()) {
            List<UUID> topPlayers = getTopPlayersForCategory(category, 3);

            if (topPlayers.isEmpty()) {
                Bukkit.getLogger().info("Keine Spieler in der Kategorie " + category + " für Belohnungen.");
                continue;
            }
            Random random = new Random();
            int villagerMood = random.nextInt(3); // 0 = Low, 1 = Mid, 2 = High

            for (UUID playerUUID : topPlayers) {
                ItemStack reward = null;

                switch (villagerMood) {
                    case 0:
                        reward = generateLowTierReward(random);
                        break;
                    case 1:
                        reward = generateMidTierReward(random);
                        break;
                    case 2:
                        reward = generateHighTierReward(random);
                        break;
                }

                if (reward != null) {
                    plugin.getVillagerCategoryManager().storeRewardForPlayer(playerUUID, category, reward);

                }
            }
        }
    }

//Konfiguration Belohnungen --DUmm


    private ItemStack generateLowTierReward(Random random) {
        int quantity = random.nextInt(12) + 1; // 1-12
        Material material = getRandomMaterial(random, Material.DIAMOND, Material.GOLD_INGOT, Material.IRON_INGOT);
        return new ItemStack(material, quantity);
    }

    private ItemStack generateMidTierReward(Random random) {
        if (random.nextBoolean()) { // 50% Chance auf normales Material oder Oraxen-Item
            int quantity = random.nextInt(33) + 1; // 1-33
            Material material = getRandomMaterial(random, Material.DIAMOND, Material.GOLD_INGOT, Material.IRON_INGOT);
            return new ItemStack(material, quantity);
        } else {
            return getRandomOraxenItem(random, "silber", "gold");
        }
    }

    private ItemStack generateHighTierReward(Random random) {
        if (random.nextBoolean()) { // 50% Chance auf normales Material oder Oraxen-Item
            int quantity = random.nextInt(64) + 1; // 1-64
            Material material = getRandomMaterial(random, Material.DIAMOND, Material.GOLD_INGOT, Material.IRON_INGOT);
            return new ItemStack(material, quantity);
        } else {
            return getRandomOraxenItem(random, "silber", "gold");
        }
    }

    private Material getRandomMaterial(Random random, Material... materials) {
        return materials[random.nextInt(materials.length)];
    }

    private ItemStack getRandomOraxenItem(Random random, String... oraxenItemIds) {
        String selectedItem = oraxenItemIds[random.nextInt(oraxenItemIds.length)];
        if (OraxenItems.exists(selectedItem)) {
            return OraxenItems.getItemById(selectedItem).build();
        }
        return null; // Fallback, falls das Oraxen-Item nicht existiert
    }




    private List<UUID> getTopPlayersForCategory(String category, int topN) {
        List<UUID> topPlayers = new ArrayList<>();
        try (Connection conn = customDatabaseManager.getConnection()) {
            String query = "SELECT player_uuid FROM player_scores WHERE category = ? ORDER BY score DESC LIMIT ?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, category);
                stmt.setInt(2, topN);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        topPlayers.add(UUID.fromString(rs.getString("player_uuid")));
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Fehler beim Laden der Top-Spieler aus der Datenbank: " + e.getMessage());
        }
        return topPlayers;
    }

    private UUID getLeadingGuildForCategory(String category) {
        UUID guildLeaderUUID = null;
        try (Connection conn = customDatabaseManager.getConnection()) {
            String query = "SELECT guild_name FROM guild_scores WHERE category = ? ORDER BY score DESC LIMIT 1";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, category);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String guildName = rs.getString("guild_name");
                        guildLeaderUUID = guildManager.getGuildLeader(guildName);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Fehler beim Laden der führenden Gilde aus der Datenbank: " + e.getMessage());
        }
        return guildLeaderUUID;
    }

    public List<UUID> getTopPlayersRewards(String category) {
        return topPlayersRewards.getOrDefault(category, new ArrayList<>());
    }

    public UUID getTopGuildReward(String category) {
        return topGuildRewards.get(category);
    }

}

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
            plugin.getLogger().info("Führender Spieler in Kategorie " + category + ": " + leadingPlayerUUID + " mit Score " + topScore);
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


    public void calculateAndStoreRewards() {
        Random random = new Random();

        for (String category : categoryManager.getCategories()) {
            List<UUID> topPlayers = getTopPlayersForCategory(category, 3); // Hol die Top 3 Spieler

            // Überprüfe, ob es Spieler in der Kategorie gibt, andernfalls überspringen
            if (topPlayers.isEmpty()) {
                plugin.getLogger().info("Keine Spieler in der Kategorie " + category + " für Belohnungen.");
                continue;
            }

            // Generiere Belohnungen für die Top 3 Spieler
            for (int i = 0; i < topPlayers.size(); i++) {
                UUID playerUUID = topPlayers.get(i);

                // Basisbelohnungen mischen, dabei Menge > 0 sicherstellen
                List<ItemStack> rewards = new ArrayList<>();
                rewards.add(new ItemStack(Material.DIAMOND, Math.max(random.nextInt(3) + 1, 1))); // 1-3 Diamanten
                rewards.add(new ItemStack(Material.GOLD_INGOT, Math.max(random.nextInt(2) + 1, 1))); // 1-2 Goldbarren
                rewards.add(new ItemStack(Material.IRON_INGOT, Math.max(random.nextInt(2) + 1, 1))); // 1-2 Eisenbarren

                // Füge Oraxen-Items hinzu (z. B. Gold & Silber) falls vorhanden
                if (OraxenItems.exists("gold")) {
                    ItemStack oraxenGold = OraxenItems.getItemById("gold").build();
                    oraxenGold.setAmount(Math.max(random.nextInt(2) + (i == 0 ? 2 : 1), 1)); // 2-3 für den ersten, 1-2 für andere
                    rewards.add(oraxenGold);
                }
                if (OraxenItems.exists("silber")) {
                    ItemStack oraxenSilver = OraxenItems.getItemById("silber").build();
                    oraxenSilver.setAmount(Math.max(random.nextInt(2) + 1, 1)); // 1-2 Silber für alle
                    rewards.add(oraxenSilver);
                }

                // Zusätzliche seltene Belohnung für den Top-Spieler
                if (i == 0) {
                    rewards.add(new ItemStack(Material.NETHERITE_INGOT, 1)); // Netherite-Ingot für den Top-Spieler
                }

                // Mische die Belohnungen und wähle eine zufällige aus
                Collections.shuffle(rewards);
                ItemStack reward = rewards.get(0); // Eine zufällige Belohnung auswählen
                plugin.getVillagerCategoryManager().storeRewardForPlayer(playerUUID, category, reward);
            }

            // Belohnung für die führende Gilde, falls vorhanden
            UUID topGuildLeader = getLeadingGuildForCategory(category);
            if (topGuildLeader != null) {
                List<ItemStack> guildRewards = new ArrayList<>();
                guildRewards.add(new ItemStack(Material.EMERALD, Math.max(random.nextInt(3) + 2, 1))); // 2-4 Emeralds

                if (OraxenItems.exists("gold")) {
                    ItemStack guildGold = OraxenItems.getItemById("gold").build();
                    guildGold.setAmount(Math.max(random.nextInt(3) + 2, 1)); // 2-4 Gold für die Gilde
                    guildRewards.add(guildGold);
                }

                if (OraxenItems.exists("silber")) {
                    ItemStack guildSilver = OraxenItems.getItemById("silber").build();
                    guildSilver.setAmount(Math.max(random.nextInt(3) + 1, 1)); // 1-3 Silber
                    guildRewards.add(guildSilver);
                }

                // Zusätzliche Belohnung für die führende Gilde
                guildRewards.add(new ItemStack(Material.DIAMOND, Math.max(1 + random.nextInt(2), 1))); // 1-2 Diamanten für die führende Gilde
                Collections.shuffle(guildRewards);
                ItemStack guildReward = guildRewards.get(0); // Zufällige Auswahl einer Gildenbelohnung
                plugin.getVillagerCategoryManager().storeRewardForGuildLeader(topGuildLeader, category, guildReward);
            }
        }
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

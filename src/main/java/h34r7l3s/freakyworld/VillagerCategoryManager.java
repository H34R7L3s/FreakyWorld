package h34r7l3s.freakyworld;

import jdk.jfr.Category;
import org.bukkit.*;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class VillagerCategoryManager {

    private final FreakyWorld plugin;
    private Villager villager;
    private String currentCategory;
    private int difficultyMultiplier;
    private long lastRewardTime;
    private final long rewardInterval = 30 * 60; // Interval in seconds 30min)
    private final Map<String, List<ItemStack>> storedRewards = new HashMap<>();



    public VillagerCategoryManager(FreakyWorld plugin) {
        this.plugin = plugin;
        this.difficultyMultiplier = 1;
        this.lastRewardTime = System.currentTimeMillis() / 1000; // Setzt die Startzeit in Sekunden
        //chooseDailyCategory();
        spawnVillager();
        startDailyEvents();
    }


    public void spawnVillager() {
        removeVillager(); // Sicherstellen, dass kein alter Villager vorhanden ist

        Location spawnLocation = new Location(plugin.getServer().getWorld("world"), -59, 14, -16);
        this.villager = (Villager) spawnLocation.getWorld().spawnEntity(spawnLocation, EntityType.VILLAGER);

        this.villager.setCustomNameVisible(true);
        this.villager.setInvulnerable(true);
        this.villager.setAI(false);

        NamespacedKey key = new NamespacedKey(plugin, "quest_villager");
        PersistentDataContainer data = this.villager.getPersistentDataContainer();
        data.set(key, PersistentDataType.STRING, "bazar_villager");

        updateVillagerTaskDisplay();
        initializeVillagerLookTask();
    }

    public void removeVillager() {
        if (this.villager != null && !this.villager.isDead()) {
            this.villager.remove();
            this.villager = null;
        }
    }

    private void initializeVillagerLookTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (villager != null && !villager.isDead()) {
                    lookAtNearestPlayer();
                }
            }
        }.runTaskTimer(plugin, 0L, 40L); // alle 2 Sekunden (40 Ticks)
    }

    private void lookAtNearestPlayer() {
        Collection<Player> nearbyPlayers = villager.getWorld().getNearbyPlayers(villager.getLocation(), 10);
        Player nearestPlayer = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Player player : nearbyPlayers) {
            double distance = player.getLocation().distanceSquared(villager.getLocation());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestPlayer = player;
            }
        }

        if (nearestPlayer != null) {
            Location villagerLocation = villager.getLocation();
            Location playerLocation = nearestPlayer.getLocation();

            // Berechne die Richtung zum Spieler und setze die Rotation des Villagers
            double dx = playerLocation.getX() - villagerLocation.getX();
            double dz = playerLocation.getZ() - villagerLocation.getZ();
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            villagerLocation.setYaw(yaw);
            villager.teleport(villagerLocation); // Aktualisiert die Blickrichtung des Villagers
        }
    }

    public void chooseDailyCategory() {
        String[] categoriesArray = {"Bauernmarkt", "Minenarbeit", "Monsterjagd", "Angeln", "Schmiedekunst", "Baumfäller"};
        this.currentCategory = categoriesArray[new Random().nextInt(categoriesArray.length)];
        adjustTaskDifficulty();

        // Wähle 2 zufällige Items aus der gewählten Kategorie
        List<String> randomItems = chooseRandomItemsFromCategory(this.currentCategory, 2);
        plugin.getLogger().info("Heutige Kategorie: " + this.currentCategory + " - Zufällige Items: " + randomItems);
    }

    public List<String> chooseRandomItemsFromCategory(String category, int itemCount) {
        // Erstelle eine neue Liste, um sicherzustellen, dass das Original nicht geändert wird
        List<String> items = new ArrayList<>(CategoryManager.categories.getOrDefault(category, Collections.emptyList()));

        // Überprüfen, ob genug Elemente in der Liste sind
        if (items.size() < itemCount) {
            return items;  // Wenn es weniger als `itemCount` gibt, einfach die gesamte Liste zurückgeben
        }

        // Liste mischen und dann nur die benötigte Anzahl an Items auswählen
        Collections.shuffle(items);

        plugin.getLogger().info("Gefundene Items: " + items );
        return items.subList(0, itemCount);
    }





    public void adjustTaskDifficulty() {
        List<String> tasks = plugin.getCategoryManager().getTasksForCategory(currentCategory);
        if (!tasks.isEmpty()) {
            String task = tasks.get(0);
            int baseAmount = 1000;
            int adjustedAmount = baseAmount * difficultyMultiplier;
            plugin.getCategoryManager().addCategory(currentCategory, Collections.singletonList(task));
        }
    }

    public void increaseDifficulty() {
        this.difficultyMultiplier++;
        adjustTaskDifficulty();
    }

    public void updateVillagerTaskDisplay() {
        String taskMessage = ChatColor.YELLOW + "Aktuelle Aufgabe: " + ChatColor.LIGHT_PURPLE + getCurrentCategory();
        List<String> tasks = plugin.getCategoryManager().getTasksForCategory(getCurrentCategory());

        if (!tasks.isEmpty()) {
            taskMessage += ChatColor.YELLOW + " - Sammle: " + ChatColor.DARK_GREEN + tasks.get(0);
        }

        UUID leadingPlayer = plugin.getEventLogic().getLeadingPlayerForCategory(getCurrentCategory());
        if (leadingPlayer != null) {
            Player player = Bukkit.getPlayer(leadingPlayer);
            if (player != null) {
                taskMessage += ChatColor.YELLOW + " - Führend: " + ChatColor.GOLD + player.getName();
            }
        }

        if (villager != null) {
            villager.setCustomName(taskMessage);
        }
    }

    public void startDailyEvents() {
        new BukkitRunnable() {
            @Override
            public void run() {
                chooseDailyCategory();
                plugin.getVillagerCategoryManager().increaseDifficulty();
                updateVillagerTaskDisplay();
                plugin.getVillagerCategoryManager().resetPlayerQuestStates(); // Alle Quest-Zustände zurücksetzen
                plugin.getEventLogic().calculateAndStoreRewards();
                rewardAllPlayers();

                lastRewardTime = System.currentTimeMillis() / 1000; // Setzt die Belohnungszeit zurück
            }
        }.runTaskTimer(plugin, 0, 20 * 60 * 30);
        //}.runTaskTimer(plugin, 0, 20 * 60 * 30);
    }



    public void resetPlayerQuestStates() {
        // Setzt den Zustand für alle Spieler zurück
        for (Player player : Bukkit.getOnlinePlayers()) {
            // Lösche alle gesetzten Quest-Daten für diesen Spieler
            player.removeMetadata("quest1Accepted", plugin);
            player.removeMetadata("quest2Accepted", plugin);
        }
    }


    public long getRemainingTimeUntilReward() {
        long currentTime = System.currentTimeMillis() / 1000; // Aktuelle Zeit in Sekunden
        long elapsedTime = currentTime - lastRewardTime;
        long timeUntilNextReward = rewardInterval - elapsedTime;
        return timeUntilNextReward > 0 ? timeUntilNextReward : 0;
    }
    public String formatTimeUntilNextReward() {
        long remainingSeconds = getRemainingTimeUntilReward();
        long minutes = remainingSeconds / 60;
        long seconds = remainingSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    public void rewardAllPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerUUID = player.getUniqueId();
            int playerScore = plugin.getEventLogic().getPlayerScoreForCategory(playerUUID, currentCategory);

            if (playerScore > 0) {
                ItemStack reward = new ItemStack(Material.DIAMOND, playerScore / 100);
                plugin.getVillagerCategoryManager().storeRewardForPlayer(playerUUID, currentCategory, reward); // Hier wird die Belohnung gespeichert
                player.sendMessage("§6Du hast für deine Teilnahme an der Kategorie " + currentCategory + " eine Belohnung erhalten!");
            }
        }
    }


    public String getCurrentCategory() {
        return currentCategory;
    }



    public void storeRewardForPlayer(UUID playerUUID, String category, ItemStack reward) {
        storedRewards.computeIfAbsent(playerUUID.toString(), k -> new ArrayList<>()).add(reward);
    }

    public List<ItemStack> getStoredRewardsForPlayer(UUID playerUUID) {
        return storedRewards.getOrDefault(playerUUID.toString(), new ArrayList<>());
    }

    public void clearStoredRewardsForPlayer(UUID playerUUID) {
        storedRewards.remove(playerUUID.toString());
    }

    public void storeRewardForGuildLeader(UUID guildLeaderUUID, String category, ItemStack reward) {
        storedRewards.computeIfAbsent(guildLeaderUUID.toString(), k -> new ArrayList<>()).add(reward);
    }

}

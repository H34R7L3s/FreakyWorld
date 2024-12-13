package h34r7l3s.freakyworld;

import io.th0rgal.oraxen.api.OraxenItems;
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

        //updateVillagerTaskDisplay();
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

    private Map<String, Integer> categoryCount = new HashMap<>();

    public void chooseDailyCategory() {
        String[] categoriesArray = {"Bauernmarkt", "Minenarbeit", "Monsterjagd", "Angeln", "Schmiedekunst", "Baumfäller"};
        for (String category : categoriesArray) {
            categoryCount.putIfAbsent(category, 0);
        }

        String leastUsedCategory = Collections.min(categoryCount.entrySet(), Map.Entry.comparingByValue()).getKey();
        this.currentCategory = leastUsedCategory;

        categoryCount.put(leastUsedCategory, categoryCount.get(leastUsedCategory) + 1);

        adjustTaskDifficulty();

        List<String> randomItems = chooseRandomItemsFromCategory(this.currentCategory, 2);
        plugin.getLogger().info("Kategorie: " + this.currentCategory + " - Items: " + randomItems);

        String eventDescription = "Aktuelle Kategorie: **" + this.currentCategory + "**\n" +
                "Sammle: **" + String.join(", ", randomItems) + "**";

        plugin.getDiscordBot().announceEventWithTimer("Event Manager", eventDescription, "Zeit verbleibend: ", 1800);
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
        String taskMessage =

                ChatColor.GOLD + "Freaky Bazar ";
        taskMessage +=        ChatColor.YELLOW + "Aktuelle Aufgabe: " + ChatColor.RED + getCurrentCategory();
        List<String> tasks = plugin.getCategoryManager().getTasksForCategory(getCurrentCategory());

        if (!tasks.isEmpty()) {
            taskMessage += ChatColor.YELLOW + " - Sammle: " + ChatColor.DARK_GREEN + tasks.get(0);
        }

        UUID leadingPlayer = plugin.getEventLogic().getLeadingPlayerForCategory(getCurrentCategory());
        if (leadingPlayer != null) {
            Player player = Bukkit.getPlayer(leadingPlayer);
            if (player != null) {
                taskMessage += ChatColor.YELLOW + " - Bester Spieler: " + ChatColor.LIGHT_PURPLE + player.getName();
            }
        }

        if (villager != null) {
            villager.setCustomName(taskMessage);
            //villager.
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


                // Startet den UI-Timer für die verbleibende Zeit
                //startRewardTimerUpdater(); //ohne Spielerinteraktion

            }
        }.runTaskTimer(plugin, 0, 20 * 60 * 30);
        //}.runTaskTimer(plugin, 0, 20 * 60 * 30);
    }
    // Methode zum dynamischen Abrufen der verbleibenden Zeit und Aktualisieren der UI
    public void startRewardTimerUpdater() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long remainingTime = getRemainingTimeUntilReward();

                // Wenn die Zeit abgelaufen ist, beende diesen Timer
                if (remainingTime == 0) {
                    this.cancel();
                    return;
                }

                // Formatiere die verbleibende Zeit und aktualisiere die UI
                String formattedTime = formatTimeUntilNextReward();
                updateUITimer(formattedTime);
            }
        }.runTaskTimer(plugin, 0L, 20L); // Alle 20 Ticks (1 Sekunde)
    }
    // Methode zur Aktualisierung der UI mit der verbleibenden Zeit bis zur nächsten Belohnung
    public void updateUITimer(String formattedTime) {
        if (villager != null) {
            String taskMessageS =

                    ChatColor.GOLD + "Freaky Bazar \n" +
                            ChatColor.YELLOW + "Aktuelle Aufgabe: " + ChatColor.RED + getCurrentCategory() +
                            ChatColor.YELLOW + " - Zeit bis zur nächsten Belohnung: " + ChatColor.GREEN + formattedTime;

            List<String> tasks = plugin.getCategoryManager().getTasksForCategory(getCurrentCategory());
            if (!tasks.isEmpty()) {
                taskMessageS += ChatColor.YELLOW + " - Sammle: " + ChatColor.DARK_GREEN + tasks.get(0);
            }

            UUID leadingPlayer = plugin.getEventLogic().getLeadingPlayerForCategory(getCurrentCategory());
            if (leadingPlayer != null) {
                Player player = Bukkit.getPlayer(leadingPlayer);
                if (player != null) {
                    taskMessageS += ChatColor.YELLOW + " - Bester Spieler: " + ChatColor.LIGHT_PURPLE + player.getName();
                }
            }

            villager.setCustomName(taskMessageS);
        }
    }


    public void resetPlayerQuestStates() {
        // Setzt den Zustand für alle Spieler zurück
        for (Player player : Bukkit.getOnlinePlayers()) {
            // Lösche alle gesetzten Quest-Daten für diesen Spieler
            player.removeMetadata("quest1Accepted", plugin);
            player.removeMetadata("quest2Accepted", plugin);
        }
    }


    // Berechnet die verbleibende Zeit und setzt lastRewardTime zurück, wenn die Zeit abgelaufen ist.
    public long getRemainingTimeUntilReward() {
        long currentTime = System.currentTimeMillis() / 1000; // Aktuelle Zeit in Sekunden
        long elapsedTime = currentTime - lastRewardTime;
        long timeUntilNextReward = rewardInterval - elapsedTime;

        // Überprüfen, ob die Zeit abgelaufen ist
        if (timeUntilNextReward <= 0) {
            // Setzt lastRewardTime zurück, um den Countdown von vorne zu starten
            lastRewardTime = currentTime;
            return rewardInterval; // Startet den Countdown neu
        }

        return timeUntilNextReward;
    }



    public String formatTimeUntilNextReward() {
        long remainingSeconds = getRemainingTimeUntilReward();
        long minutes = remainingSeconds / 60;
        long seconds = remainingSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    //Konfiguration Belohnungen


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


    // Belohnung 2
    public void rewardAllPlayers() {
        // Spieler und ihre Punktestände sammeln
        List<OfflinePlayer> allPlayers = Arrays.asList(Bukkit.getOfflinePlayers());
        Map<UUID, Integer> playerScores = new HashMap<>();

        for (OfflinePlayer player : allPlayers) {
            UUID playerUUID = player.getUniqueId();
            int playerScore = plugin.getEventLogic().getPlayerScoreForCategory(playerUUID, currentCategory);

            // Belohnt nur Spieler mit Score > 1
            if (playerScore > 1) {
                playerScores.put(playerUUID, playerScore);
            }
        }

        // Belohnung an alle Spieler mit Score > 1 vergeben
        for (Map.Entry<UUID, Integer> entry : playerScores.entrySet()) {
            UUID playerUUID = entry.getKey();
            int playerScore = entry.getValue();

            // Villager-Mood festlegen: 0 = Low, 1 = Mid, 2 = High
            Random random = new Random();
            int villagerMood = random.nextInt(3);

            // Belohnung basierend auf Punkten und Villager-Mood
            ItemStack reward = null;

            if (villagerMood == 0) { // Low Tier
                reward = generateLowTierReward(random);
            } else if (villagerMood == 1) { // Mid Tier
                reward = generateMidTierReward(random);
            } else if (villagerMood == 2) { // High Tier
                reward = generateHighTierReward(random);
            }

            // Falls zusätzliche Punktzahl-basierte Logik benötigt wird, kann dies hier angepasst werden
            if (playerScore >= 100 && villagerMood == 2) {
                reward = OraxenItems.getItemById("gold").build();
            } else if (playerScore >= 50 && villagerMood >= 1) {
                reward = OraxenItems.getItemById("silber").build();
            } else if (playerScore >= 20) {
                reward = new ItemStack(Material.DIAMOND, Math.max(1, playerScore / 20));
            } else if (playerScore >= 10) {
                reward = new ItemStack(Material.EMERALD, Math.max(1, playerScore / 10));
            } else if (playerScore >= 5) {
                reward = new ItemStack(Material.IRON_INGOT, Math.max(1, playerScore / 5));
            }

            // Falls die Menge 0 ist, wird die Belohnung nicht ausgegeben, um Fehler zu vermeiden
            if (reward != null && reward.getAmount() > 0) {
                plugin.getVillagerCategoryManager().storeRewardForPlayer(playerUUID, currentCategory, reward);

                // Wenn der Spieler online ist, Nachricht senden
                Player onlinePlayer = Bukkit.getPlayer(playerUUID);
                if (onlinePlayer != null) {
                    onlinePlayer.sendMessage(ChatColor.GOLD + "Freaky, dass " + ChatColor.DARK_PURPLE + onlinePlayer.getName() +
                            ChatColor.GOLD +" schon einmal in der Kategorie "+ ChatColor.GREEN  + currentCategory +ChatColor.GOLD + " geholfen hat! \n " + ChatColor.GREEN + "Hast du hier noch weitere Ressourcen?");
                }
            }
        }

        if (playerScores.isEmpty()) {
            plugin.getLogger().info("Keine Belohnungen ausgestellt, da niemand mehr als 1 Punkt in der Kategorie " + currentCategory + " erreicht hat.");
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

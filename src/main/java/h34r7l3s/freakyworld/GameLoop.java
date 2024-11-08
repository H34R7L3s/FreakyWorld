package h34r7l3s.freakyworld;

import io.th0rgal.oraxen.api.OraxenItems;
import org.bukkit.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.EventPriority;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.sql.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class GameLoop implements Listener {
    private JavaPlugin plugin;

    private File dbFile;
    private final Object dbLock = new Object();
    private final Set<UUID> greetedPlayers = new HashSet<>();
    private final Map<UUID, Integer> playerIntroProgress = new HashMap<>();
    private final Set<UUID> currentlyPlayingIntro = new HashSet<>();
    private final Map<UUID, Location> lastGroundLocation = new HashMap<>();
    private GameLoopHCFW gameLoopHCFW;

    private File abilitiesDbFile;
    private Connection connection;
    private Connection abilitiesConnection;
    private Villager rankingVillager;
    private QuestVillager questVillager;
    private GuildManager guildManager;
    private FreakyBankRobber freakyBankRobber;
    private DragonEventManager dragonEventManager;

    public GameLoop(JavaPlugin plugin, DiscordBot bot, GuildManager guildManager, Connection dbConnection) {
        this.plugin = plugin;
        this.dbFile = new File(plugin.getDataFolder(), "player_progress.db");
        this.abilitiesDbFile = new File(plugin.getDataFolder(), "player_abilities.db");
        this.connection = dbConnection;
        this.questVillager = new QuestVillager((FreakyWorld) plugin, bot, this);
        this.guildManager = guildManager;
        this.gameLoopHCFW = new GameLoopHCFW(plugin, this); // Verbindungsobjekt übergeben
        gameLoopHCFW.initialize();
        this.freakyBankRobber = new FreakyBankRobber(plugin, this);



    }
    public void setDragonEventManager(DragonEventManager dragonEventManager) {
        this.dragonEventManager = dragonEventManager;
    }
    public QuestVillager getQuestVillager() {
        return questVillager;
    }

    public void initialize() {
        try {
            if (!dbFile.exists()) {
                dbFile.getParentFile().mkdirs();
                dbFile.createNewFile();
            }
            if (!abilitiesDbFile.exists()) {
                abilitiesDbFile.getParentFile().mkdirs();
                abilitiesDbFile.createNewFile();
            }

            openConnection();

            createTables();
            updateDatabaseStructure();
            spawnRankingVillager();
            startInterestScheduler();
            checkStuckPlayers();
            createDragonKillTable();
            //Bukkit.getPluginManager().registerEvents(this, plugin); // Register the events for inventory clicks
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public JavaPlugin getPlugin() {
        return plugin;
    }

    public void openConnection() {
        synchronized (dbLock) {
            try {
                if (connection == null || connection.isClosed()) {
                    connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getPath());
                    connection.setAutoCommit(true); // Enable autocommit
                    plugin.getLogger().info("Database connection opened.");
                }
                if (abilitiesConnection == null || abilitiesConnection.isClosed()) {
                    abilitiesConnection = DriverManager.getConnection("jdbc:sqlite:" + abilitiesDbFile.getPath());
                    abilitiesConnection.setAutoCommit(true); // Enable autocommit
                    plugin.getLogger().info("Abilities database connection opened.");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
    private void openConnectionStart() {
        try {
            if (connection == null || connection.isClosed()) {
                // Initialize the connection here
                // Example: connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getPath());
                plugin.getLogger().info("Database connection opened.");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error opening database connection: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void closeConnection() {
        synchronized (dbLock) {
            try {
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                    plugin.getLogger().info("Database connection closed.");
                }
                if (abilitiesConnection != null && !abilitiesConnection.isClosed()) {
                    abilitiesConnection.close();
                    plugin.getLogger().info("Abilities database connection closed.");
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Fehler beim Schließen der Datenbankverbindung: " + e.getMessage());
            }
        }
    }
    //private final Object dbLock = new Object();

    public void createTables() {
        try {
            openConnection(); // Ensure connection is open before creating tables

            // Create player_progress table
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS player_progress (" +
                        "uuid VARCHAR(36) PRIMARY KEY," +
                        "player_name VARCHAR(16)," +
                        "items_submitted INTEGER," +
                        "level INTEGER," +
                        "freaky_xp INTEGER DEFAULT 0," +
                        "xp_on_hand INTEGER DEFAULT 0," +
                        "xp_in_bank INTEGER DEFAULT 0," +
                        "version INTEGER DEFAULT 0" +
                        ")");
                plugin.getLogger().info("Table player_progress created or already exists.");
                closeConnection();
            }
            openConnection();
            // Create player_progress_backup table
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS player_progress_backup (" +
                        "uuid VARCHAR(36) PRIMARY KEY," +
                        "xp_on_hand INTEGER DEFAULT 0" +
                        ")");
                plugin.getLogger().info("Table player_progress_backup created or already exists.");
                closeConnection();
            }
            openConnection();
            // Create player_abilities table
            try (PreparedStatement statement = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS player_abilities (" +
                            "uuid VARCHAR(36) PRIMARY KEY," +
                            "abilities TEXT)")) {
                statement.executeUpdate();
                plugin.getLogger().info("Table player_abilities created or already exists.");
                closeConnection();
            }
            openConnection();
            // Create player_item_categories table
            try (PreparedStatement statement = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS player_item_categories (" +
                            "uuid VARCHAR(36)," +
                            "category VARCHAR(32)," +
                            "items_submitted INTEGER," +
                            "freaky_xp INTEGER," +
                            "player_name VARCHAR(16)," +
                            "PRIMARY KEY (uuid, category))")) {
                statement.executeUpdate();
                plugin.getLogger().info("Table player_item_categories created or already exists.");
                closeConnection();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error creating tables: " + e.getMessage());
            e.printStackTrace();
        } finally {
            closeConnection(); // Ensure the connection is closed after table creation
        }
    }

    private void spawnRankingVillager() {
        World world = Bukkit.getWorlds().get(0); // Get the first world
        if (world != null) {
            Location villagerLocation = new Location(world, 14, 99, 61);
            rankingVillager = world.spawn(villagerLocation, Villager.class);
            rankingVillager.setCustomName("FreakyWorld");
            rankingVillager.setCustomNameVisible(true);
            rankingVillager.setAI(false); // Disable AI to prevent movement
            rankingVillager.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 255, false, false)); // Prevent movement
            rankingVillager.setInvulnerable(true); // Make the villager unkillable
            startRankingVillagerLookTask();
        } else {
            plugin.getLogger().warning("Keine Welt gefunden, um den Ranglistenhändler zu spawnen.");
        }
    }


    private void startRankingVillagerLookTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (rankingVillager != null && !rankingVillager.isDead()) {
                    lookAtNearestPlayerRankingVillager();
                }
            }
        }.runTaskTimer(plugin, 0L, 40L); // alle 2 Sekunden (40 Ticks)
    }
    private void lookAtNearestPlayerRankingVillager() {
        Collection<Player> nearbyPlayers = rankingVillager.getWorld().getNearbyPlayers(rankingVillager.getLocation(), 10);
        Player nearestPlayer = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Player player : nearbyPlayers) {
            double distance = player.getLocation().distanceSquared(rankingVillager.getLocation());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestPlayer = player;
            }
        }

        if (nearestPlayer != null) {
            Location villagerLocation = rankingVillager.getLocation();
            Location playerLocation = nearestPlayer.getLocation();

            // Berechne die Richtung zum Spieler und setze die Rotation des Ranglistenhändlers
            double dx = playerLocation.getX() - villagerLocation.getX();
            double dz = playerLocation.getZ() - villagerLocation.getZ();
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            villagerLocation.setYaw(yaw);
            rankingVillager.teleport(villagerLocation); // Aktualisiert die Blickrichtung des Ranglistenhändlers
        }
    }

    public void removeRankingVillager() {
        if (rankingVillager != null && !rankingVillager.isDead()) {
            rankingVillager.remove();
            freakyBankRobber.removeBankRobberVillager();

            plugin.getLogger().info("Der Ranglistenhändler wurde entfernt.");
        } else {
            plugin.getLogger().warning("Kein Ranglistenhändler vorhanden oder er ist bereits tot.");
        }
    }

    public class NumberFormatter {
        public static String formatNumber(long value) {
            if (value < 1000) return Long.toString(value);
            int exp = (int) (Math.log(value) / Math.log(1000));
            return String.format("%.1f %c", value / Math.pow(1000, exp), "KMGTPE".charAt(exp - 1));
        }

        public static String formatNumberSpecial(long value) {
            if (value < 1000) return Long.toString(value);
            int exp = (int) (Math.log(value) / Math.log(1000));
            char unit = "KMGTPE".charAt(exp - 1);
            String formatted = String.format("%.1f %c", value / Math.pow(1000, exp), unit);

            switch (unit) {
                case 'K': return ChatColor.YELLOW + formatted; // Tausend
                case 'M': return ChatColor.GREEN + formatted; // Million
                case 'G': return ChatColor.AQUA + formatted; // Milliarde
                case 'T': return ChatColor.LIGHT_PURPLE + formatted; // Billion
                case 'P': return ChatColor.GOLD + formatted; // Billiarde
                case 'E': return ChatColor.RED + formatted; // Trillion
                default: return formatted;
            }
        }
    }


    private void updateUI(Player player) {
        Connection connection = getConnection(); // Ensure connection is open before updating UI

        UUID playerId = player.getUniqueId();
        int playerItems = questVillager.getPlayerContribution(player);
        double playerPercentage = questVillager.calculatePlayerContributionPercentage(player);
        int playerXP = getPlayerXP(playerId);
        int playerRank = getPlayerRank(playerId);
        int xpOnHand = getPlayerXPOnHand(playerId);
        int xpInBank = getPlayerXPInBank(playerId);

        plugin.getLogger().info("Updating UI for player " + player.getName() + ": Items=" + playerItems + ", XP=" + playerXP + ", Rank=" + playerRank + ", OnHand=" + xpOnHand + ", InBank=" + xpInBank);

        Inventory playerInventory = Bukkit.createInventory(null, 9, ChatColor.LIGHT_PURPLE + "FreakyWorld - Hauptmenü");

        // Default Item Stats View
        ItemStack itemStack = new ItemStack(Material.CHERRY_SAPLING);
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            String rankName = getPlayerRankName(playerRank);
            meta.setDisplayName(ChatColor.DARK_PURPLE + "Deine Freaky Statistik");
            meta.setLore(Arrays.asList(
                    ChatColor.GREEN + "",
                    ChatColor.GOLD + "Dein aktueller Rang: " + ChatColor.RED + "#" + playerRank + " " + ChatColor.AQUA + rankName,
                    ChatColor.GOLD + "Deine Gesamten Abgaben: " + ChatColor.DARK_AQUA + NumberFormatter.formatNumber(playerItems),
                    ChatColor.GOLD + "Freaky XP: " + ChatColor.DARK_AQUA + NumberFormatter.formatNumber(playerXP),
                    "",
                    ChatColor.DARK_RED +  "Inventar:",
                    ChatColor.GOLD + "Freaky XP auf Tasche: " + ChatColor.GREEN + NumberFormatter.formatNumber(xpOnHand),
                    "",
                    "",
                    ChatColor.GOLD + "Deine Freakyness: " + ChatColor.LIGHT_PURPLE + String.format("%.2f", playerPercentage) + "%"
            ));
            itemStack.setItemMeta(meta);
        }
        playerInventory.setItem(4, itemStack);

        // Conditional UI for more than 1000 items submitted
        if (playerItems >= 1000) {
            ItemStack netherriteHelmet = new ItemStack(Material.NETHERITE_HELMET);
            ItemMeta helmetMeta = netherriteHelmet.getItemMeta();
            if (helmetMeta != null) {
                helmetMeta.setDisplayName(ChatColor.DARK_PURPLE + "FreakyWorld - Schnellzugriff");
                helmetMeta.setLore(Arrays.asList(
                        ChatColor.GREEN + "",
                        ChatColor.GREEN + "Du hast mehr als 1000 Gegenstände abgegeben!",
                        ChatColor.YELLOW + "Klicke, um verfügbare Events und Aufgaben zu sehen."
                ));
                // Hide all attributes like armor and durability
                helmetMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                helmetMeta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
                helmetMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                netherriteHelmet.setItemMeta(helmetMeta);
            }
            playerInventory.setItem(0, netherriteHelmet);
            player.updateInventory();
        }

        // Conditional UI for more than 5000 items submitted
        if (playerItems >= 5000) {
            ItemStack hcfwSword = new ItemStack(Material.SKELETON_SKULL);
            ItemMeta hcfwSwordMeta = hcfwSword.getItemMeta();
            if (hcfwSwordMeta != null) {
                hcfwSwordMeta.setDisplayName(ChatColor.DARK_PURPLE + "HCFW Zugang");
                hcfwSwordMeta.setLore(Arrays.asList(
                        ChatColor.GREEN + "",
                        ChatColor.GREEN + "Du hast mehr als 5000 Gegenstände abgegeben!",
                        ChatColor.YELLOW + "Klicke, um das HCFW Menü zu öffnen."
                ));
                hcfwSword.setItemMeta(hcfwSwordMeta);
            }
            playerInventory.setItem(1, hcfwSword);
        }

        // Conditional UI for more than 10,000 items submitted
        if (playerItems >= 10000) {
            ItemStack specialAbilities = new ItemStack(Material.BOOK);
            ItemMeta abilitiesMeta = specialAbilities.getItemMeta();
            if (abilitiesMeta != null) {
                abilitiesMeta.setDisplayName(ChatColor.DARK_PURPLE + "Upgrades");
                abilitiesMeta.setLore(Arrays.asList(
                        ChatColor.GREEN + "",
                        ChatColor.GREEN + "Du hast mehr als 10.000 Gegenstände abgegeben!",
                        ChatColor.YELLOW + "Klicke, um permanente Fähigkeiten freizuschalten."
                ));
                specialAbilities.setItemMeta(abilitiesMeta);
            }
            playerInventory.setItem(8, specialAbilities);

            // Bank UI
            ItemStack bankItem = new ItemStack(Material.CHEST);
            ItemMeta bankMeta = bankItem.getItemMeta();
            if (bankMeta != null) {
                bankMeta.setDisplayName(ChatColor.DARK_PURPLE + "Freaky Bank");
                bankMeta.setLore(Arrays.asList(
                        ChatColor.GREEN + "",
                        ChatColor.GOLD + "Freaky XP in der Bank: " + ChatColor.GREEN + NumberFormatter.formatNumber(xpInBank),
                        ChatColor.YELLOW + "Klicke, um zu verwalten."
                ));
                bankItem.setItemMeta(bankMeta);
            }
            playerInventory.setItem(6, bankItem);
        }

        // Adding Ranking to the UI
        ItemStack rankingItem = new ItemStack(Material.NAME_TAG);
        ItemMeta rankingMeta = rankingItem.getItemMeta();
        if (rankingMeta != null) {
            rankingMeta.setDisplayName(ChatColor.DARK_PURPLE + "Top 10 Rangliste");

            List<String> rankingLore = new ArrayList<>();
            List<String> topPlayers = getTopPlayers();

            for (int i = 0; i < topPlayers.size(); i++) {
                String rankName = getPlayerRankName(i + 1);
                String playerInfo = topPlayers.get(i);
                rankingLore.add(ChatColor.GOLD.toString() + (i + 1) + ". "
                        + ChatColor.RESET + ChatColor.GREEN + playerInfo.split(" - ")[0]
                        + ChatColor.GOLD + " - " + ChatColor.AQUA + rankName + ChatColor.GOLD
                        +  " - " + ChatColor.GREEN + playerInfo.split(" - ")[1]);
            }
            rankingMeta.setLore(rankingLore);
            rankingItem.setItemMeta(rankingMeta);
        }
        playerInventory.setItem(2, rankingItem);

        player.openInventory(playerInventory);
        //closeConnection();
    }



    public enum FreakyRank {
        RANK_1("Freaky Herrscher"),
        RANK_2("Freaky Grossmeister"),
        RANK_3("Freaky Held"),
        RANK_4("Freaky Bezwinger"),
        RANK_5("Freaky Meister"),
        RANK_6("Freaky Gelehrter"),
        RANK_7("Freaky Entdecker"),
        RANK_8("Freaky Abenteurer"),
        RANK_9("Freaky Lehrling"),
        RANK_10("Freaky Neuling");

        private final String rankName;

        FreakyRank(String rankName) {
            this.rankName = rankName;
        }

        public String getRankName() {
            return rankName;
        }
    }


    public String getPlayerRankName(int rank) {
        if (rank >= 1 && rank <= 10) {
            return FreakyRank.values()[rank - 1].getRankName();
        }
        return "Unranked";
    }
    private final ReentrantLock lock = new ReentrantLock();
    public int getPlayerXPOnHand(UUID playerId) {
        Connection connection = getConnection();
        //openConnection();// Ensure connection is open before using it
        PreparedStatement stmt = null;
        ResultSet rs = null;
        int xpOnHand = 0;
        try {
            stmt = connection.prepareStatement("SELECT xp_on_hand FROM player_progress WHERE uuid = ?");
            stmt.setString(1, playerId.toString());
            rs = stmt.executeQuery();
            if (rs.next()) {
                xpOnHand = rs.getInt("xp_on_hand");
                plugin.getLogger().info("Fetched XP on hand for player " + playerId + ": " + xpOnHand + " at " + System.currentTimeMillis());
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeResources(null, stmt, rs);
        }

        return xpOnHand;
    }
    public int getPlayerXPInBank(UUID playerId) {
        Connection connection = getConnection(); // Ensure connection is open before using it
        PreparedStatement stmt = null;
        ResultSet rs = null;
        int xpInBank = 0;
        try {
            stmt = connection.prepareStatement("SELECT xp_in_bank FROM player_progress WHERE uuid = ?");
            stmt.setString(1, playerId.toString());
            rs = stmt.executeQuery();
            if (rs.next()) {
                xpInBank = rs.getInt("xp_in_bank");
                plugin.getLogger().info("Fetched XP in bank for player " + playerId + ": " + xpInBank);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeResources(null, stmt, rs);
        }
        return xpInBank;
    }




    public void addXPToPlayer(Player player, int xpEarned, int xpOnHand) {
        UUID playerId = player.getUniqueId();
        Connection connection = getConnection();

        plugin.getLogger().info("Adding XP to Player: player=" + player.getName() + ", xpEarned=" + xpEarned + ", xpOnHand=" + xpOnHand);

        try (PreparedStatement stmt = connection.prepareStatement(
                "UPDATE player_progress SET xp_on_hand = xp_on_hand + ?, freaky_xp = freaky_xp + ? WHERE uuid = ?")) {
            stmt.setInt(1, xpOnHand);
            stmt.setInt(2, xpEarned);
            stmt.setString(3, playerId.toString());
            stmt.executeUpdate();

            // Save to backup
            int newXPOnHand = getPlayerXPOnHand(playerId); // Removed connection parameter
            saveXPOnHandToBackup(playerId, newXPOnHand); // Removed connection parameter
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }









    private List<String> getTopPlayers() {
        Connection connection = getConnection(); // Ensure connection is open before using it
        List<String> topPlayers = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT player_name, freaky_xp FROM player_progress ORDER BY freaky_xp DESC LIMIT 10")) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String playerName = rs.getString("player_name");
                if (playerName == null || playerName.isEmpty()) {
                    playerName = "Unbekannt"; // Fallback for missing player names
                }
                topPlayers.add(playerName + " - Freaky XP: " + rs.getInt("freaky_xp"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return topPlayers;
    }







    public int getPlayerXP(UUID playerId) {
        Connection connection = getConnection(); // Ensure connection is open before using it
        int xp = 0;
        try (PreparedStatement stmt = connection.prepareStatement("SELECT freaky_xp FROM player_progress WHERE uuid = ?")) {
            stmt.setString(1, playerId.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                xp = rs.getInt("freaky_xp");
                // Debugging
                plugin.getLogger().info("Fetched Freaky XP for player " + playerId + ": " + xp);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return xp;
    }

    public int getPlayerRank(UUID playerId) {
        int rank = 1;
        Connection connection = getConnection(); // Ensure connection is open before using it
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT uuid FROM player_progress ORDER BY freaky_xp DESC")) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                if (rs.getString("uuid").equals(playerId.toString())) {
                    return rank;
                }
                rank++;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return rank;
    }

    private void showTop10Ranking(Player player) {
        try {
            plugin.getLogger().info("Preparing Top 10 Ranking inventory for player " + player.getName());

            List<String> topPlayers = getTopPlayers();
            Map<String, List<String>> topPlayersByCategory = new HashMap<>();

            for (String category : questVillager.getMaterialCategories().values()) {
                topPlayersByCategory.put(category, questVillager.getTopPlayersByCategory(category, 10));
            }
            topPlayersByCategory.put("HCFW", questVillager.getTopPlayersByCategory("HCFW", 10)); // HCFW-Kategorie hinzufügen

            Inventory top10Inventory = Bukkit.createInventory(null, 27, "Top 10 Rangliste");
            plugin.getLogger().info("Created inventory object for Top 10 Ranking");

            ItemStack overallItem = new ItemStack(Material.PAPER);
            ItemMeta overallMeta = overallItem.getItemMeta();
            if (overallMeta != null) {
                overallMeta.setDisplayName(ChatColor.DARK_PURPLE + "Top 10 Spieler");
                List<String> lore = new ArrayList<>();
                lore.add("Allgemeine Rangliste der Top 10 Spieler");
                for (String playerInfo : topPlayers) {
                    lore.add(playerInfo);
                }
                overallMeta.setLore(lore);
                overallItem.setItemMeta(overallMeta);
            }
            top10Inventory.setItem(13, overallItem);
            plugin.getLogger().info("Added overall top 10 players to the inventory");

            int slot = 0;
            for (Map.Entry<Material, String> entry : questVillager.getMaterialCategories().entrySet()) {
                String category = entry.getValue();
                Material material = entry.getKey();
                List<String> categoryTopPlayers = topPlayersByCategory.get(category);

                ItemStack categoryItem = new ItemStack(material);
                ItemMeta categoryMeta = categoryItem.getItemMeta();
                if (categoryMeta != null) {
                    categoryMeta.setDisplayName(category + " Top 10");
                    categoryMeta.setLore(categoryTopPlayers);
                    categoryItem.setItemMeta(categoryMeta);
                }
                top10Inventory.setItem(slot, categoryItem);
                slot++;
            }

            // HCFW-Kategorie zur Rangliste hinzufügen
            List<String> hcfwTopPlayers = topPlayersByCategory.get("HCFW");
            ItemStack hcfwItem = new ItemStack(Material.DIAMOND_SWORD); // Symbol für HCFW
            ItemMeta hcfwMeta = hcfwItem.getItemMeta();
            if (hcfwMeta != null) {
                hcfwMeta.setDisplayName("HCFW Top 10");
                hcfwMeta.setLore(hcfwTopPlayers);
                hcfwItem.setItemMeta(hcfwMeta);
            }
            top10Inventory.setItem(slot, hcfwItem);
            slot++;

            plugin.getLogger().info("Added category top players to the inventory");

            for (int i = slot; i < 27; i++) {
                ItemStack fillerItem = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                ItemMeta fillerMeta = fillerItem.getItemMeta();
                if (fillerMeta != null) {
                    fillerMeta.setDisplayName(" ");
                    fillerItem.setItemMeta(fillerMeta);
                }
                top10Inventory.setItem(i, fillerItem);
            }
            plugin.getLogger().info("Added filler items to the inventory");

            if (player.getInventory().contains(Material.BARRIER)) {
                ItemStack resetButton = new ItemStack(Material.BARRIER);
                ItemMeta resetMeta = resetButton.getItemMeta();
                if (resetMeta != null) {
                    resetMeta.setDisplayName("Rangliste zurücksetzen");
                    resetButton.setItemMeta(resetMeta);
                }
                top10Inventory.setItem(26, resetButton);
            }

            player.openInventory(top10Inventory);
            plugin.getLogger().info("Opened Top 10 Ranking inventory for player " + player.getName());
        } catch (Exception e) {
            plugin.getLogger().severe("Error while opening Top 10 Ranking inventory: " + e.getMessage());
            e.printStackTrace();
        }
    }





    private void openEventMenu(Player player) {
        Inventory eventInventory = Bukkit.createInventory(null, 9, "Events und Aufgaben");

        // Example event item
        ItemStack eventItem = new ItemStack(Material.ENDER_PEARL);
        ItemMeta eventMeta = eventItem.getItemMeta();
        if (eventMeta != null) {
            eventMeta.setDisplayName(ChatColor.AQUA + "Drachen Event");
            eventMeta.setLore(Arrays.asList("Starte das Drachen Event, um Belohnungen zu erhalten."));
            eventItem.setItemMeta(eventMeta);
        }
        eventInventory.setItem(0, eventItem);

        ItemStack comingSoonItem = OraxenItems.getItemById("runic_animated-shield1").build(); // Beispiel-Oraxen-ID
        ItemMeta meta = comingSoonItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "?????");
            meta.setLore(Arrays.asList("" +
                    "" +
                    "Bald wirst du mehr erfahren..."));

            comingSoonItem.setItemMeta(meta);
        }
        eventInventory.setItem(1, comingSoonItem);



        // Add more events similarly...
        player.openInventory(eventInventory);
    }

    private void openEventChoiceMenu(Player player) {
        Inventory eventChoiceInventory = Bukkit.createInventory(null, 9, "Event Art wählen");
        // Info Icon hinzufügen
        ItemStack infoItem = new ItemStack(Material.BOOK);  // Buch-Icon für Informationen
        ItemMeta infoMeta = infoItem.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName(ChatColor.GOLD + "Wähle deinen Pfad");

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.DARK_GRAY + "» In der uralten Welt von FreakyWorld, wo nur die");
            lore.add(ChatColor.DARK_GRAY + "stärksten und kühnsten Abenteurer bestehen, liegt");
            lore.add(ChatColor.DARK_GRAY + "eine Herausforderung jenseits aller Vorstellungskraft.");
            lore.add("");
            lore.add(ChatColor.GRAY + "» Tritt ein in die Arena des Drachenkampfes!");
            lore.add(ChatColor.GRAY + "Jeder Sieg stärkt deine Seele, schärft deinen Geist");
            lore.add(ChatColor.GRAY + "und bringt dich dem Ruhm näher. Doch sei gewarnt,");
            lore.add(ChatColor.GRAY + "dieser Kampf ist nicht für schwache Herzen.");
            lore.add("");
            lore.add(ChatColor.DARK_PURPLE + "» Wähle deine Seite:");
            lore.add(ChatColor.GRAY + "Willst du allein in den Kampf ziehen, die gesamte");
            lore.add(ChatColor.GRAY + "Last und den Ruhm für dich beanspruchen?");
            lore.add("");
            lore.add(ChatColor.DARK_AQUA + "» Oder stehst du Seite an Seite mit deiner Gilde,");
            lore.add(ChatColor.DARK_AQUA + "vereint im Streben nach Ruhm und Reichtum?");
            lore.add("");
            lore.add(ChatColor.GRAY + "» Deine Wahl beeinflusst den Weg, aber nicht die");
            lore.add(ChatColor.GRAY + "Härte des Kampfes oder den verdienten Lohn.");

            infoMeta.setLore(lore);
            infoItem.setItemMeta(infoMeta);
        }
        eventChoiceInventory.setItem(1, infoItem);
        // Info Icon

        ItemStack soloItem = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta soloMeta = soloItem.getItemMeta();
        if (soloMeta != null) {
            soloMeta.setDisplayName(ChatColor.DARK_PURPLE + "Solo");
            // Hide all attributes like armor and durability
            soloItem.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            soloItem.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            soloItem.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            soloItem.setItemMeta(soloMeta);
        }
        eventChoiceInventory.setItem(3, soloItem);


        ItemStack guildItem = new ItemStack(Material.GOLDEN_SWORD);
        ItemMeta guildMeta = guildItem.getItemMeta();
        if (guildMeta != null) {
            guildMeta.setDisplayName(ChatColor.DARK_AQUA + "Gilde");
            guildItem.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            guildItem.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            guildItem.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            guildItem.setItemMeta(guildMeta);
        }
        eventChoiceInventory.setItem(5, guildItem);
        player.updateInventory();
        player.openInventory(eventChoiceInventory);

    }


    private void openBoostingUI(Player player, boolean isGuild) {
        Inventory boostingUI = Bukkit.createInventory(null, 9, "Boosting UI / Start Event");

        ItemStack startButton = new ItemStack(Material.DRAGON_HEAD);
        ItemMeta startMeta = startButton.getItemMeta();
        if (startMeta != null) {
            startMeta.setDisplayName("Start Event");
            // Erstelle eine Beschreibung (Lore) für den Start-Button
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Dieses Event kostet Freaky XP.");
            lore.add(ChatColor.RED + "Preis: " + ChatColor.GOLD + "15.000 Freaky XP");

            // Setze die Lore in die Meta-Daten des Items
            startMeta.setLore(lore);
            startButton.setItemMeta(startMeta);
        }

        boostingUI.setItem(4, startButton);

        // Adjust the following line to open the difficulty UI
        player.setMetadata("isGuildEvent", new FixedMetadataValue(plugin, isGuild));
        player.openInventory(boostingUI);
    }

    private void openSelectAbilityMenu(Player player) {
        try {
            plugin.getLogger().info("Preparing to open Select Ability Menu for player " + player.getName());

            Inventory selectAbilityInventory = Bukkit.createInventory(null, 9, "Wähle eine Fähigkeit zum Verbessern");

            // Erklärung Icon hinzufügen
            ItemStack infoItem = new ItemStack(Material.BOOK);  // Buch-Icon für Skill-Tree-Erklärung
            ItemMeta infoMeta = infoItem.getItemMeta();
            if (infoMeta != null) {
                infoMeta.setDisplayName(ChatColor.GOLD + "Dein persönlicher Skill-Tree");

                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "» In deinen Händen liegt die Macht, dich selbst zu");
                lore.add(ChatColor.GRAY + "verbessern und Fähigkeiten zu entfesseln.");
                lore.add("");
                lore.add(ChatColor.DARK_PURPLE + "» Hier kannst du deine aktiven Fähigkeiten sehen:");
                lore.add(ChatColor.GRAY + "- Schnelligkeit");
                lore.add(ChatColor.GRAY + "- Stärke");
                lore.add(ChatColor.GRAY + "- Abbaugeschwindigkeit (Haste)");
                lore.add("");
                lore.add(ChatColor.DARK_AQUA + "» Alle Effekte sind permanent und können mit");
                lore.add(ChatColor.DARK_AQUA + "jedem Upgrade um eine Stufe ( +1) gesteigert werden.");
                infoMeta.setLore(lore);
                infoItem.setItemMeta(infoMeta);
            }
            selectAbilityInventory.setItem(1, infoItem);



            ItemStack speedAbility = new ItemStack(Material.SUGAR);
            ItemMeta speedMeta = speedAbility.getItemMeta();
            if (speedMeta != null) {
                speedMeta.setDisplayName("Schnelligkeit");
                speedAbility.setItemMeta(speedMeta);
            }

            ItemStack strengthAbility = new ItemStack(Material.BLAZE_POWDER);
            ItemMeta strengthMeta = strengthAbility.getItemMeta();
            if (strengthMeta != null) {
                strengthMeta.setDisplayName("Stärke");
                strengthAbility.setItemMeta(strengthMeta);
            }

            ItemStack anotherAbility = new ItemStack(Material.DIAMOND);
            ItemMeta anotherMeta = anotherAbility.getItemMeta();
            if (anotherMeta != null) {
                anotherMeta.setDisplayName("Haste");
                anotherAbility.setItemMeta(anotherMeta);
            }

            selectAbilityInventory.setItem(3, speedAbility);
            selectAbilityInventory.setItem(4, strengthAbility);
            selectAbilityInventory.setItem(5, anotherAbility);

            player.openInventory(selectAbilityInventory);
            plugin.getLogger().info("Opened Select Ability Menu for player " + player.getName());
        } catch (Exception e) {
            plugin.getLogger().severe("Error while opening Select Ability Menu: " + e.getMessage());
            e.printStackTrace();
        }
    }


    private void openAbilitiesMenu(Player player) {
        Inventory abilitiesInventory = Bukkit.createInventory(null, 54, "Fähigkeiten freischalten");

        ItemStack[] requiredItems = {
                OraxenItems.getItemById("kriegsmarke").build(),
                OraxenItems.getItemById("eisenherz").build(),
                OraxenItems.getItemById("auftragsbuch").build(),
                OraxenItems.getItemById("freaky_wissen").build(),
                OraxenItems.getItemById("freakyworlds_willen").build()
        };



        ItemStack separator = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta separatorMeta = separator.getItemMeta();
        if (separatorMeta != null) {
            separatorMeta.setDisplayName(" ");
            separator.setItemMeta(separatorMeta);
        }

        ItemStack glassPane = new ItemStack(Material.GLASS_PANE);
        ItemMeta glassPaneMeta = glassPane.getItemMeta();
        if (glassPaneMeta != null) {
            glassPaneMeta.setDisplayName(" ");
            glassPane.setItemMeta(glassPaneMeta);
        }
        for (int i = 0; i < abilitiesInventory.getSize(); i++) {
            abilitiesInventory.setItem(i, glassPane);
        }

        String selectedAbility = player.getMetadata("selectedAbility").get(0).asString();
        int currentLevel = getAbilityLevel(getPlayerAbilities(player.getUniqueId()), selectedAbility);
        int requiredItemsCount = (int) Math.pow(2, currentLevel);

        boolean allItemsPresent = true;

        for (int i = 0; i < requiredItems.length; i++) {
            ItemStack requiredItem = requiredItems[i];
            ItemMeta requiredMeta = requiredItem.getItemMeta();
            if (requiredMeta != null) {
                requiredMeta.setLore(Arrays.asList(
                        "Dieses Item wird benötigt, um die Fähigkeit freizuschalten.",
                        "Benötigte Anzahl: " + requiredItemsCount
                ));
                requiredItem.setItemMeta(requiredMeta);
            }
            abilitiesInventory.setItem(10 + i, requiredItem);

            boolean hasItems = hasPlayerRequiredItems(player, requiredItem, requiredItemsCount);
            allItemsPresent &= hasItems;
            ItemStack indicator = new ItemStack(hasItems ? Material.GREEN_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE);
            ItemMeta indicatorMeta = indicator.getItemMeta();
            if (indicatorMeta != null) {
                indicatorMeta.setDisplayName("Status: " + (hasItems ? "Vorhanden" : "Nicht Vorhanden"));
                indicator.setItemMeta(indicatorMeta);
            }
            abilitiesInventory.setItem(28 + i, indicator);
        }

        for (int i = 19; i <= 25; i++) {
            abilitiesInventory.setItem(i, separator);
        }

        if (allItemsPresent) {
            ItemStack confirmButton = new ItemStack(Material.LIME_CONCRETE);
            ItemMeta confirmMeta = confirmButton.getItemMeta();
            if (confirmMeta != null) {
                confirmMeta.setDisplayName("Bestätigen");
                confirmButton.setItemMeta(confirmMeta);
            }
            abilitiesInventory.setItem(40, confirmButton);
        }

        if (player.getInventory().contains(Material.BARRIER)) {
            ItemStack resetButton = new ItemStack(Material.BARRIER);
            ItemMeta resetMeta = resetButton.getItemMeta();
            if (resetMeta != null) {
                resetMeta.setDisplayName("Fähigkeiten zurücksetzen");
                resetButton.setItemMeta(resetMeta);
            }
            abilitiesInventory.setItem(41, resetButton);
        }

        //Info Text einfügen Slot 50
        ItemStack comingSoonItem = OraxenItems.getItemById("fairy_wing").build(); // Beispiel-Oraxen-ID
        ItemMeta meta = comingSoonItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "Dein Pfad zur Macht");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "In den Tiefen der FreakyWorld findest du hier",
                    ChatColor.GRAY + "Möglichkeiten, deine Kräfte dauerhaft zu entfesseln.",
                    ChatColor.GRAY + "Jedes Upgrade bringt dich näher an deine wahre Stärke.",
                    "",
                    ChatColor.DARK_PURPLE + "» Diese magische Tafel zeigt dir alle benötigten",
                    ChatColor.DARK_PURPLE + "Ressourcen und ihre Menge.",
                    ChatColor.GRAY + "- Wenn du alle Items beisammen hast, erscheint",
                    ChatColor.GRAY + " ein grüner Wolle-Block.",
                    ChatColor.GRAY + "- Ein Klick auf diesen Block entfesselt deine Macht.",
                    "",
                    ChatColor.DARK_AQUA + "» Doch sei gewarnt:",
                    ChatColor.DARK_AQUA + "Mit jeder Stufe wird der Preis für die nächste steigen.",
                    "",
                    ChatColor.GOLD + "Bereite dich vor, Held – deine wahre Macht erwartet dich."
            ));

            comingSoonItem.setItemMeta(meta);
        }
        abilitiesInventory.setItem(49, comingSoonItem);




        player.openInventory(abilitiesInventory);
    }


    private void resetPlayerAbilities(Player player) {
        UUID playerId = player.getUniqueId();
        Connection connection = getConnection(); // Ensure connection is open before using it

        try (PreparedStatement statement = abilitiesConnection.prepareStatement(
                "UPDATE player_abilities SET abilities = ? WHERE uuid = ?")) {
            statement.setString(1, "");
            statement.setString(2, playerId.toString());
            statement.executeUpdate();

            player.sendMessage("Alle Fähigkeiten wurden zurückgesetzt!");
            applyPermanentEffects(player, ""); // Remove all effects
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private String getPlayerAbilities(UUID playerId) {
        Connection connection = getConnection(); // Ensure connection is open before using it
        try (PreparedStatement fetchStatement = abilitiesConnection.prepareStatement(
                "SELECT abilities FROM player_abilities WHERE uuid = ?")) {
            fetchStatement.setString(1, playerId.toString());
            ResultSet resultSet = fetchStatement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getString("abilities");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "";
    }

    // Methode zum Vergleich der Oraxen-Items
    private boolean compareOraxenItems(ItemStack item1, ItemStack item2) {
        if (item1 == null || item2 == null) return false;

        if (item1.getType() != item2.getType()) return false;

        ItemMeta meta1 = item1.getItemMeta();
        ItemMeta meta2 = item2.getItemMeta();
        if (meta1 != null && meta2 != null && meta1.hasCustomModelData() && meta2.hasCustomModelData()) {
            return meta1.getCustomModelData() == meta2.getCustomModelData();
        }

        return false;
    }




    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClickEvent(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        if (event.getView().getTitle().equals("Event Art wählen")) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;

            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem.getType() == Material.DIAMOND_SWORD) {
                openBoostingUI(player, false);
            } else if (clickedItem.getType() == Material.GOLDEN_SWORD) {
                openBoostingUI(player, true);
            }
        } else if (event.getView().getTitle().equals("Boosting UI / Start Event")) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;

            if (event.getCurrentItem().getType() == Material.DRAGON_HEAD) {
                boolean isGuildEvent = player.getMetadata("isGuildEvent").get(0).asBoolean();
                dragonEventManager.openDragonDifficultyUI(player);
            }
        } else if (event.getView().getTitle().equals("Events und Aufgaben")) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;

            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem.getType() == Material.ENDER_PEARL) {
                openEventChoiceMenu(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClickRank(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        String title = ChatColor.stripColor(event.getView().getTitle());

        if (title.equals("FreakyWorld - Hauptmenü")) {
            event.setCancelled(true);
            handleAbgabestatusClick(event, player);
        } else if (title.equals("Wähle eine Fähigkeit zum Verbessern")) {
            event.setCancelled(true);
            // Überprüfung, ob das Buch angeklickt wurde (Slot 1 im GUI)
            if (event.getSlot() == 1 && event.getCurrentItem().getType() == Material.BOOK) {
                event.setCancelled(true); // Aktion abbrechen, um Klick zu verhindern
                player.sendMessage(ChatColor.GRAY + "Dieses Buch enthält nur Informationen und kann nicht ausgewählt werden.");
                return;
            }
            handleAbilitySelectClick(event, player);
        } else if (title.equals("Fähigkeiten freischalten")) {
            event.setCancelled(true);

            handleAbilityUnlockClick(event, player);
        } else if (title.equals("Freaky Bank")) {
            event.setCancelled(true);
            handleBankClick(event, player);
        } else if (title.equals("Top 10 Rangliste")) {
            event.setCancelled(true);
            handleTop10Click(event, player);
        } else if (title.equals("HCFW Events")) {
            event.setCancelled(true);
            handleHCFWEventClick(event, player);
        }
    }
    private void handleHCFWEventClick(InventoryClickEvent event, Player player) {
        gameLoopHCFW.onInventoryClick(event);
    }


    private void handleAbgabestatusClick(InventoryClickEvent event, Player player) {
        if (event.getCurrentItem() == null) {
            plugin.getLogger().info("Clicked item is null");
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        plugin.getLogger().info("Clicked item: " + clickedItem.getType());

        if (clickedItem.getType() == Material.NETHERITE_HELMET) {
            plugin.getLogger().info("NETHERITE_HELMET clicked, opening event menu");
            openEventMenu(player);
        } else if (clickedItem.getType() == Material.BOOK) {
            ItemMeta meta = clickedItem.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                String displayName = ChatColor.stripColor(meta.getDisplayName());
                plugin.getLogger().info("Book clicked with display name: " + displayName);
                if (displayName.equals("Upgrades")) {
                    plugin.getLogger().info("Opening select ability menu");
                    openSelectAbilityMenu(player);
                }
            }
        } else if (clickedItem.getType() == Material.NAME_TAG && ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName()).equals("Top 10 Rangliste")) {
            plugin.getLogger().info("PAPER clicked with Top 10 Rangliste, showing top 10 ranking");
            showTop10Ranking(player);
        } else if (clickedItem.getType() == Material.CHEST && ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName()).equals("Freaky Bank")) {
            plugin.getLogger().info("CHEST clicked with Freaky Bank, opening bank menu");
            openBankMenu(player);
        } else if (clickedItem.getType() == Material.SKELETON_SKULL) {
            plugin.getLogger().info("DIAMOND_SWORD clicked, opening HCFW menu");
            gameLoopHCFW.openHCFWMenuIfEligible(player);
        }
    }


    private void handleAbilitySelectClick(InventoryClickEvent event, Player player) {
        if (event.getCurrentItem() == null) {
            plugin.getLogger().info("Clicked item is null");
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        String selectedAbility = clickedItem.getItemMeta().getDisplayName();
        plugin.getLogger().info("Ability selected: " + selectedAbility);

        player.setMetadata("selectedAbility", new FixedMetadataValue(plugin, selectedAbility));
        openAbilitiesMenu(player);
    }

    private void handleAbilityUnlockClick(InventoryClickEvent event, Player player) {
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;

        String selectedAbility = player.getMetadata("selectedAbility").get(0).asString();
        plugin.getLogger().info("Selected ability for unlocking: " + selectedAbility);

        int currentLevel = getAbilityLevel(getPlayerAbilities(player.getUniqueId()), selectedAbility);
        int requiredItemsCount = (int) Math.pow(2, currentLevel);

        // Die benötigten Oraxen-Items
        ItemStack[] requiredItems = {
                OraxenItems.getItemById("kriegsmarke").build(),
                OraxenItems.getItemById("eisenherz").build(),
                OraxenItems.getItemById("auftragsbuch").build(),
                OraxenItems.getItemById("freaky_wissen").build(),
                OraxenItems.getItemById("freakyworlds_willen").build()
        };

        // Prüfung ob der Spieler auf den "Bestätigen"-Button klickt
        if (event.getCurrentItem().getType() == Material.LIME_CONCRETE &&
                ChatColor.stripColor(event.getCurrentItem().getItemMeta().getDisplayName()).equals("Bestätigen")) {

            boolean hasAllRequiredItems = true;

            // Prüfen, ob der Spieler alle benötigten Items im Inventar hat
            for (ItemStack requiredItem : requiredItems) {
                if (!hasPlayerRequiredItems(player, requiredItem, requiredItemsCount)) {
                    hasAllRequiredItems = false;
                    plugin.getLogger().info("Spieler hat nicht genügend " + requiredItem.getType() + " im Inventar.");
                    break;
                }
            }

            // Wenn alle benötigten Items vorhanden sind, entferne sie und schalte die Fähigkeit frei
            if (hasAllRequiredItems) {
                for (ItemStack requiredItem : requiredItems) {
                    removePlayerItems(player, requiredItem, requiredItemsCount);
                    plugin.getLogger().info("Entferne " + requiredItemsCount + " von " + requiredItem.getType() + " aus dem Inventar des Spielers.");
                }

                unlockAbilityForPlayer(player, selectedAbility);
                player.sendMessage(ChatColor.GREEN + "Fähigkeit erfolgreich freigeschaltet!");
                player.closeInventory();
            } else {
                player.sendMessage(ChatColor.RED + "Du hast nicht die richtigen Gegenstände platziert.");
            }
        } else if (event.getCurrentItem().getType() == Material.BARRIER) {
            resetPlayerAbilities(player);
            player.closeInventory();
        }
    }




    private synchronized void handleBankClick(InventoryClickEvent event, Player player) {
        if (event.getCurrentItem() == null) return;

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem.getType() == Material.EMERALD_BLOCK) {
            plugin.getLogger().info("EMERALD_BLOCK clicked, depositing all XP to bank");
            player.closeInventory();
            depositAllXPToBank(player);
        } else if (clickedItem.getType() == Material.DIAMOND_BLOCK) {
            plugin.getLogger().info("DIAMOND_BLOCK clicked, withdrawing XP from bank");
            player.closeInventory();
            player.sendMessage("Bitte gebe den Betrag ein, den du abheben möchtest:");
            player.setMetadata("bankAction", new FixedMetadataValue(plugin, "withdraw"));
        }
    }


    private void handleTop10Click(InventoryClickEvent event, Player player) {
        if (event.getCurrentItem() == null) return;

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem.getType() == Material.BARRIER) {
            resetTop10Rankings();
            player.sendMessage("Die Top 10 Rangliste wurde zurückgesetzt!");
            player.closeInventory();
        }
    }


    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof Villager) {
            Villager clickedVillager = (Villager) event.getRightClicked();
            if (clickedVillager.equals(rankingVillager)) {
                Player player = event.getPlayer();
                updateUI(player); // Update the UI with current data for the player who interacted
            }
        }
    }

    private boolean removePlayerItems(Player player, ItemStack itemToRemove, int itemCount) {
        int itemsToRemove = itemCount;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && compareOraxenItems(item, itemToRemove)) {
                int currentAmount = item.getAmount();
                if (currentAmount <= itemsToRemove) {
                    itemsToRemove -= currentAmount;
                    player.getInventory().remove(item);
                } else {
                    item.setAmount(currentAmount - itemsToRemove);
                    itemsToRemove = 0;
                }
                if (itemsToRemove == 0) break;
            }
        }
        return itemsToRemove == 0;
    }


    public List<Player> getParticipants(Player player, boolean isGuildEvent) {
        List<Player> participants = new ArrayList<>();
        if (isGuildEvent) {
            Guild guild = getGuildOfPlayer(player.getName());
            if (guild != null) {
                List<String> onlineMembers = getOnlineGuildMembers(guild);
                for (String memberName : onlineMembers) {
                    Player member = Bukkit.getPlayer(memberName);
                    if (member != null) {
                        participants.add(member);
                    }
                }
            } else {
                player.sendMessage("Du bist in keiner Gilde.");
            }
        } else {
            participants.add(player);
        }
        return participants;
    }

    private Guild getGuildOfPlayer(String playerName) {
        return guildManager.getPlayerGuild(playerName);
    }

    private List<String> getOnlineGuildMembers(Guild guild) {
        List<String> onlineMembers = new ArrayList<>();
        for (String member : guild.getMembers()) {
            if (Bukkit.getPlayer(member) != null) {
                onlineMembers.add(member);
            }
        }
        return onlineMembers;
    }

    private void unlockAbilityForPlayer(Player player, String selectedAbility) {
        UUID playerId = player.getUniqueId();
        Connection connection = getConnection(); // Ensure connection is open before using it

        try (PreparedStatement statement = abilitiesConnection.prepareStatement(
                "INSERT OR REPLACE INTO player_abilities (uuid, abilities) VALUES (?, ?)")) {
            // Fetch existing abilities from the database
            String existingAbilities = "";
            try (PreparedStatement fetchStatement = abilitiesConnection.prepareStatement(
                    "SELECT abilities FROM player_abilities WHERE uuid = ?")) {
                fetchStatement.setString(1, playerId.toString());
                ResultSet resultSet = fetchStatement.executeQuery();
                if (resultSet.next()) {
                    existingAbilities = resultSet.getString("abilities");
                }
            }

            // Get the current level of the selected ability
            int currentLevel = getAbilityLevel(existingAbilities, selectedAbility);
            int nextLevel = currentLevel + 1;

            // Upgrade the selected ability
            String newAbilities = upgradeAbility(existingAbilities, selectedAbility);
            statement.setString(1, playerId.toString());
            statement.setString(2, newAbilities);
            statement.executeUpdate();

            player.sendMessage("Fähigkeit '" + selectedAbility + "' auf Stufe " + nextLevel + " freigeschaltet!");
            applyPermanentEffects(player, newAbilities);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    private ItemStack[] getAllRequiredItems() {
        return new ItemStack[]{
                OraxenItems.getItemById("kriegsmarke").build(),
                OraxenItems.getItemById("eisenherz").build(),
                OraxenItems.getItemById("auftragsbuch").build(),
                OraxenItems.getItemById("freaky_wissen").build(),
                OraxenItems.getItemById("freakyworlds_willen").build()
        };
    }

    private int getAbilityLevel(String abilities, String selectedAbility) {
        for (String ability : abilities.split(",")) {
            if (ability.startsWith(selectedAbility)) {
                String[] parts = ability.split(":");
                return parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
            }
        }
        return 0; // Ability not found, so it starts from level 0
    }

    private ItemStack getRequiredItemForAbility(String ability) {
        switch (ability) {
            case "Schnelligkeit":
                return OraxenItems.getItemById("kriegsmarke").build();
            case "Stärke":
                return OraxenItems.getItemById("eisenherz").build();
            case "Haste":
                return OraxenItems.getItemById("auftragsbuch").build();
            // Add more cases for other abilities as needed
            default:
                return null;
        }
    }

    // Methode zur Prüfung, ob der Spieler die benötigten Items im Inventar hat
    private boolean hasPlayerRequiredItems(Player player, ItemStack requiredItem, int requiredCount) {
        int itemCount = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && compareOraxenItems(stack, requiredItem)) {
                itemCount += stack.getAmount();
            }
        }
        plugin.getLogger().info("Gesamtmenge von " + requiredItem.getType() + " im Inventar: " + itemCount);
        return itemCount >= requiredCount;
    }



    private String upgradeAbility(String existingAbilities, String selectedAbility) {
        // Split existing abilities into a list
        List<String> abilitiesList = new ArrayList<>(Arrays.asList(existingAbilities.split(",")));

        // Check if the ability is already in the list
        boolean found = false;
        for (int i = 0; i < abilitiesList.size(); i++) {
            String ability = abilitiesList.get(i);
            if (ability.startsWith(selectedAbility)) {
                // Upgrade the ability level
                String[] parts = ability.split(":");
                int level = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                level++;
                abilitiesList.set(i, selectedAbility + ":" + level);
                found = true;
                break;
            }
        }

        // If the ability is not found, add it to the list
        if (!found) {
            abilitiesList.add(selectedAbility + ":1");
        }

        // Join the list back into a string
        return String.join(",", abilitiesList);
    }

    private void applyPermanentEffects(Player player, String abilities) {
        // Split abilities into a list
        List<String> abilitiesList = Arrays.asList(abilities.split(","));

        // Clear existing effects
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));

        // Apply effects based on abilities
        for (String ability : abilitiesList) {
            String[] parts = ability.split(":");
            String abilityName = parts[0];
            int level = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;

            switch (abilityName) {
                case "Schnelligkeit":
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, level - 1, false, false));
                    break;
                case "Stärke":
                    player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, level - 1, false, false));
                    break;
                case "Haste":
                    player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, Integer.MAX_VALUE, level - 1, false, false));
                    break;
                // Add more abilities and their effects as needed
            }
        }
    }

    public Connection getConnection() {
        synchronized (dbLock) {
            try {
                if (connection == null || connection.isClosed() || !connection.isValid(5) )  {
                    openConnection();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return connection;
        }
    }
    public Connection getAbilitiesConnection() {
        synchronized (dbLock) {
            try {
                if (abilitiesConnection == null || abilitiesConnection.isClosed()) {
                    openConnection();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return abilitiesConnection;
        }
    }


    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        if (abilitiesConnection != null) {
            try {
                abilitiesConnection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public void updateDatabaseStructure() {
        try {
            openConnection(); // Ensure connection is open before updating the database

            // Create or update player_progress table
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS player_progress (" +
                        "uuid VARCHAR(36) PRIMARY KEY, " +
                        "player_name VARCHAR(16), " +
                        "items_submitted INTEGER, " +
                        "level INTEGER, " +
                        "freaky_xp INTEGER DEFAULT 0, " +
                        "xp_on_hand INTEGER DEFAULT 0, " +
                        "xp_in_bank INTEGER DEFAULT 0, " +
                        "version INTEGER DEFAULT 0)");

                // Ensure all necessary columns are present in player_progress
                ensureColumnExists("player_progress", "freaky_xp", "INTEGER DEFAULT 0");
                ensureColumnExists("player_progress", "player_name", "VARCHAR(16)");
                ensureColumnExists("player_progress", "xp_on_hand", "INTEGER DEFAULT 0");
                ensureColumnExists("player_progress", "xp_in_bank", "INTEGER DEFAULT 0");
                ensureColumnExists("player_progress", "version", "INTEGER DEFAULT 0");
            }

            // Create player_item_categories table if not exists
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS player_item_categories (" +
                        "uuid VARCHAR(36), " +
                        "category VARCHAR(32), " +
                        "items_submitted INTEGER, " +
                        "freaky_xp INTEGER, " +
                        "player_name VARCHAR(16), " +
                        "PRIMARY KEY (uuid, category))");

                // Ensure all necessary columns are present in player_item_categories
                ensureColumnExists("player_item_categories", "player_name", "VARCHAR(16)");
            }

            // Create player_abilities table if not exists
            try (Statement statement = abilitiesConnection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS player_abilities (" +
                        "uuid VARCHAR(36) PRIMARY KEY, " +
                        "abilities TEXT)");
            }

            // Create bank_robber_data table if not exists
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS bank_robber_data (" +
                        "uuid VARCHAR(36) PRIMARY KEY, " +
                        "heist_count INT DEFAULT 0, " +
                        "villager_account INT DEFAULT 0, " +
                        "bonus DOUBLE DEFAULT 0)");
                plugin.getLogger().info("Table bank_robber_data created or already exists.");

                // Check if the 'bonus' column exists and add it if missing
                plugin.getLogger().info("Checking for existence of 'bonus' column...");
                if (!doesColumnExist("bank_robber_data", "bonus")) {
                    plugin.getLogger().info("Column 'bonus' does not exist. Adding column...");
                    statement.executeUpdate("ALTER TABLE bank_robber_data ADD COLUMN bonus DOUBLE DEFAULT 0");
                    plugin.getLogger().info("Column 'bonus' added to table bank_robber_data.");
                } else {
                    plugin.getLogger().info("Column 'bonus' already exists.");
                }

                // Ensure villager account exists
                try (PreparedStatement stmt = connection.prepareStatement(
                        "INSERT OR IGNORE INTO bank_robber_data (uuid, villager_account, bonus) VALUES ('villager', 0, 0)")) {
                    int rowsAffected = stmt.executeUpdate();
                    if (rowsAffected > 0) {
                        plugin.getLogger().info("Villager account inserted into bank_robber_data table.");
                    } else {
                        plugin.getLogger().info("Villager account already exists in bank_robber_data table.");
                    }
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("Error updating database structure: " + e.getMessage());
            e.printStackTrace();
        } finally {
            closeConnection(); // Ensure the connection is closed after updating the database
        }
    }
    private boolean doesColumnExist(String tableName, String columnName) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "PRAGMA table_info(" + tableName + ")")) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String currentColumn = rs.getString("name");
                if (currentColumn.equalsIgnoreCase(columnName)) {
                    return true;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error checking for column existence: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    private void ensureColumnExists(String tableName, String columnName, String columnDefinition) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getColumns(null, null, tableName, columnName)) {
            if (!resultSet.next()) {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDefinition);
                }
            }
        }
    }


    @EventHandler(priority = EventPriority.HIGH)
    public void onTop10InventoryClick(InventoryClickEvent event) {
        // Überprüfen, ob das Inventar die Top-10-Rangliste ist
        if (event.getView().getTitle().equals("Top 10 Rangliste")) {
            // Verhindert, dass Items aus diesem Inventar genommen werden
            event.setCancelled(true);

            if (event.getCurrentItem() == null) return;
            ItemStack clickedItem = event.getCurrentItem();
            Player player = (Player) event.getWhoClicked();

            if (clickedItem.getType() == Material.BARRIER) {
                resetTop10Rankings();
                player.sendMessage("Die Top 10 Rangliste wurde zurückgesetzt!");
                player.closeInventory();
            }
        }
    }


    private void showCategoryRanking(Player player) {
        Inventory categoryRankingInventory = Bukkit.createInventory(null, 27, "Kategorie Rangliste");

        // Beispiel: Anzeige der Top 10 Spieler jeder Kategorie
        for (Map.Entry<Material, String> entry : questVillager.getMaterialCategories().entrySet()) {
            String category = entry.getValue();
            List<String> topPlayers = questVillager.getTopPlayersByCategory(category, 10);
            ItemStack itemStack = new ItemStack(entry.getKey());
            ItemMeta meta = itemStack.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(category + " Top 10");
                meta.setLore(topPlayers);
                itemStack.setItemMeta(meta);
            }
            categoryRankingInventory.addItem(itemStack);
        }

        player.openInventory(categoryRankingInventory);
    }

    private void resetTop10Rankings() {
        Connection connection = getConnection(); // Ensure connection is open before using it

        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("DELETE FROM player_progress");
            stmt.executeUpdate("DELETE FROM player_item_categories");
            plugin.getLogger().info("Top 10 Rangliste wurde zurückgesetzt.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }



//BANK METHODE FÜR FREAKY XP

    public void startInterestScheduler() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::applyBankInterest, 0L, 30000L * 25); // 25 minutes in ticks
    }


    private void openBankMenu(Player player) {
        try {
            plugin.getLogger().info("Preparing to open Bank Menu for player " + player.getName());

            Inventory bankInventory = Bukkit.createInventory(null, 9, "Freaky Bank");

            ItemStack depositItem = new ItemStack(Material.EMERALD_BLOCK);
            ItemMeta depositMeta = depositItem.getItemMeta();
            if (depositMeta != null) {
                depositMeta.setDisplayName(ChatColor.GREEN + "XP einzahlen");
                depositItem.setItemMeta(depositMeta);
            }
            bankInventory.setItem(3, depositItem);

            ItemStack withdrawItem = new ItemStack(Material.DIAMOND_BLOCK);
            ItemMeta withdrawMeta = withdrawItem.getItemMeta();
            if (withdrawMeta != null) {
                withdrawMeta.setDisplayName(ChatColor.RED +"XP abheben");
                withdrawItem.setItemMeta(withdrawMeta);
            }
            bankInventory.setItem(5, withdrawItem);

            player.openInventory(bankInventory);
            plugin.getLogger().info("Opened Bank Menu for player " + player.getName());
        } catch (Exception e) {
            plugin.getLogger().severe("Error while opening Bank Menu: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private final Set<UUID> recentlyDiedPlayers = Collections.synchronizedSet(new HashSet<>());

    public void reduceXPOnDeath(Player player) {
        UUID playerId = player.getUniqueId();
        Connection connection = getConnection(); // Ensure connection is open before using it

        if (recentlyDiedPlayers.contains(playerId)) {
            return; // Skip if already processed
        }

        recentlyDiedPlayers.add(playerId);

        // Überprüfen, ob der Spieler durch einen anderen Spieler getötet wurde
        if (player.getKiller() != null) {
            // Wenn der Spieler durch einen anderen Spieler getötet wurde, keine Reduktion der XP durchführen
            return;
        }

        try (PreparedStatement stmt = connection.prepareStatement("UPDATE player_progress SET xp_on_hand = xp_on_hand / 2 WHERE uuid = ?")) {
            stmt.setString(1, playerId.toString());
            stmt.executeUpdate();

            // Schauderhafte Nachricht
            String message = ChatColor.RED + "" + ChatColor.BOLD + "SCHRECKLICH!" + ChatColor.RESET + ChatColor.DARK_RED + " Du hast " + ChatColor.GOLD + "" + ChatColor.BOLD + "50%" + ChatColor.RESET + ChatColor.DARK_RED + " deiner wertvollen " + ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Freaky XP" + ChatColor.RESET + ChatColor.DARK_RED + " verloren!";
            player.sendMessage(message);

            // Sounds abspielen
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_DEATH, 1.0f, 0.5f); // Tiefer Ton
            player.playSound(player.getLocation(), Sound.ENTITY_GHAST_SCREAM, 1.0f, 0.5f); // Ghast Schrei
            player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.5f); // Donner

            // Partikeleffekte hinzufügen (optional)
            player.getWorld().spawnParticle(Particle.LARGE_SMOKE, player.getLocation(), 50, 1, 1, 1, 0.1);
            player.getWorld().spawnParticle(Particle.WITCH, player.getLocation(), 30, 1, 1, 1, 0.1);

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            // Remove the player from the set after a short delay to allow other event processing
            Bukkit.getScheduler().runTaskLater(plugin, () -> recentlyDiedPlayers.remove(playerId), 20L); // 1 second delay
        }
    }

    public void preventXPReductionOnDeath(Player player) {
        UUID playerId = player.getUniqueId();
        recentlyDiedPlayers.add(playerId);
        Bukkit.getScheduler().runTaskLater(plugin, () -> recentlyDiedPlayers.remove(playerId), 20L); // 1 second delay
    }

    public void applyBankInterest() {
        Connection connection = getConnection(); // Ensure connection is open before using it

        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("UPDATE player_progress SET xp_in_bank = xp_in_bank + (xp_in_bank / 100)");
            plugin.getLogger().info("1% Zinsen wurden auf alle Bank XP angewendet.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void withdrawXPFromBank(Player player, int amount) {
        UUID playerId = player.getUniqueId();
        Connection connection = getConnection(); // Ensure connection is open

        int xpInBank = getPlayerXPInBank(playerId);

        if (xpInBank >= amount) {
            try (PreparedStatement stmt = connection.prepareStatement(
                    "UPDATE player_progress SET xp_on_hand = xp_on_hand + ?, xp_in_bank = xp_in_bank - ? WHERE uuid = ?")) {
                stmt.setInt(1, amount);
                stmt.setInt(2, amount);
                stmt.setString(3, playerId.toString());
                stmt.executeUpdate();

                // Save to backup
                int newXPOnHand = getPlayerXPOnHand(playerId);
                saveXPOnHandToBackup(playerId, newXPOnHand);

                player.sendMessage(amount + " XP wurden von der Bank abgehoben.");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            player.sendMessage("Du hast nicht genug XP in der Bank, um " + amount + " XP abzuheben.");
        }
    }







    public void depositAllXPToBank(Player player) {
        UUID playerId = player.getUniqueId();
        Connection connection = getConnection();

        if (connection == null) {
            player.sendMessage("Ein Fehler ist aufgetreten. Die Verbindung zur Datenbank konnte nicht hergestellt werden.");
            plugin.getLogger().severe("Fehler bei der Verbindung zur Datenbank für Spieler " + player.getName() + ": Verbindung ist null.");
            return;
        }

        int currentXP = getPlayerXPOnHand(playerId);

        if (currentXP > 0) {
            try (PreparedStatement stmt = connection.prepareStatement(
                    "UPDATE player_progress SET xp_on_hand = 0, xp_in_bank = xp_in_bank + ? WHERE uuid = ?")) {
                stmt.setInt(1, currentXP);
                stmt.setString(2, playerId.toString());
                int rowsUpdated = stmt.executeUpdate();

                if (rowsUpdated == 0) {
                    player.sendMessage("Ein Fehler ist aufgetreten. Bitte versuche es erneut.");
                } else {
                    // Save to backup
                    int newXPOnHand = getPlayerXPOnHand(playerId);
                    plugin.getLogger().severe("Anzahl XP nach Einzahlung : " + newXPOnHand);
                    saveXPOnHandToBackup(playerId, newXPOnHand); // XP on hand is now 0 after deposit
                    player.sendMessage(currentXP + " XP wurden in die Bank eingezahlt.");
                }
            } catch (SQLException e) {
                player.sendMessage("Ein Fehler ist aufgetreten. Die Transaktion wurde abgebrochen.");
                plugin.getLogger().severe("Fehler beim Einzahlen von XP in die Bank für Spieler " + player.getName() + ": " + e.getMessage());
            } finally {
                closeResources(connection, null, null);
            }
        } else {
            player.sendMessage("Du hast keine XP auf Tasche, um einzuzahlen.");
            closeResources(connection, null, null);
        }
    }


    private int getCurrentVersion(UUID playerId) {
        int version = 0;
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT version FROM player_progress WHERE uuid = ?")) {
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    version = rs.getInt("version");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Fehler beim Abrufen der Versionsnummer für Spieler " + playerId + ": " + e.getMessage());
        }
        return version;
    }










    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeathBank(PlayerDeathEvent event) {
        Player player = event.getEntity();
        reduceXPOnDeath(player);
    }


    @EventHandler
    public void onPlayerChatBank(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (player.hasMetadata("bankAction")) {
            event.setCancelled(true);
            String action = player.getMetadata("bankAction").get(0).asString();
            player.removeMetadata("bankAction", plugin);

            try {
                int amount = Integer.parseInt(event.getMessage());
                if (amount <= 0) {
                    player.sendMessage("Bitte gebe eine positive Zahl ein.");
                    return;
                }

                // Ensure the connection is open
                Connection connection = getConnection();

                if (action.equals("withdraw")) {
                    if (getPlayerXPInBank(player.getUniqueId()) >= amount) {
                        withdrawXPFromBank(player, amount);
                    } else {
                        player.sendMessage("Du hast nicht genug XP in der Bank, um " + amount + " XP abzuheben.");
                    }
                }
            } catch (NumberFormatException e) {
                player.sendMessage("Bitte gebe eine gültige Zahl ein.");
            } finally {
                // Do not close the connection here, keep it open if needed elsewhere
            }
        }
    }






    //Freaky Welcome

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoinFreaky(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        plugin.getLogger().info("Player " + player.getName() + " is joining. Initializing connection.");

        // Retrieve XP on hand from the backup database
        int xpOnHand = getXPOnHandFromBackup(playerId);

        plugin.getLogger().info("Fetched XP on hand from backup for player " + player.getName() + ": " + xpOnHand);

        // Update player progress with the retrieved value
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(
                     "UPDATE player_progress SET xp_on_hand = ? WHERE uuid = ?")) {
            stmt.setInt(1, xpOnHand);
            stmt.setString(2, playerId.toString());
            stmt.executeUpdate();
            plugin.getLogger().info("Updated player_progress with XP on hand for player " + player.getName());
        } catch (SQLException e) {
            e.printStackTrace();
        }

        greetedPlayers.add(playerId);
        playerIntroProgress.put(playerId, 0);

        startIntro(player);
        //sendOraxenPackToPlayer(player);
    }
    private void sendOraxenPackToPlayer(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "oraxen pack send " + player.getName());
            plugin.getLogger().info("Oraxen pack sent to player " + player.getName());
        });
    }



    private void startIntro(Player player) {
        UUID playerId = player.getUniqueId();

        if (currentlyPlayingIntro.contains(playerId)) {
            return;
        }

        currentlyPlayingIntro.add(playerId);

        Connection connection = getConnection();

        int playerRank = getPlayerRank(playerId);
        int playerItems = questVillager.getPlayerContribution(player);
        int playerXP = getPlayerXP(playerId);

        String welcomeMessage1 = ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Willkommen auf FreakyWorld, " + ChatColor.GOLD + player.getName() + ChatColor.DARK_PURPLE + "!";
        String welcomeMessage2 = ChatColor.GOLD + "Dein aktueller Rang: " + ChatColor.RED + "#" + NumberFormatter.formatNumber(playerRank);
        String welcomeMessage3 = ChatColor.GOLD + "Deine Gesamten Abgaben: " + ChatColor.AQUA + NumberFormatter.formatNumberSpecial(playerItems);
        String welcomeMessage4 = ChatColor.GOLD + "Deine " + ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Freaky XP" + ChatColor.RESET + ChatColor.GOLD + ": " + ChatColor.BLUE + NumberFormatter.formatNumberSpecial(playerXP);

        // Start initial sound effect
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);

        // Apply effects to enhance the cinematic experience
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 400, 1, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 400, 1, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 400, 1, false, false));

        // Save the player's last ground location if not already present
        if (!lastGroundLocation.containsKey(playerId) && player.isOnGround()) {
            lastGroundLocation.put(playerId, player.getLocation());
        }

        Location lastLocation = lastGroundLocation.getOrDefault(playerId, player.getWorld().getSpawnLocation());
        Location startLocation = lastLocation.clone().add(0, 100, 0); // Start 100 blocks above the player's last ground location
        Location midLocation = lastLocation.clone().add(0, 50, 0); // Mid point 50 blocks above the player's last ground location
        Location endLocation = lastLocation;

        // Move the player to spectator mode and start camera movement
        setPlayerToSpectator(player);

        // Start the beacon beam effect
        startBeaconBeamEffect(player, startLocation, endLocation);

        // Camera movement from above to player's location
        moveCamera(player, startLocation, midLocation, () -> {
            moveCamera(player, midLocation, endLocation, () -> {
                // Set the player back to survival mode at the end location
                player.setGameMode(GameMode.SURVIVAL);
                player.setFlySpeed(0.1f); // Reset fly speed if needed
                player.setInvulnerable(false); // Remove invulnerability

                // Remove all effects once the camera movement ends
                player.removePotionEffect(PotionEffectType.BLINDNESS);
                player.removePotionEffect(PotionEffectType.NIGHT_VISION);
                player.removePotionEffect(PotionEffectType.SPEED);

                // Clear any remaining sounds or effects
                player.stopSound(Sound.MUSIC_DISC_PIGSTEP);

                // Trigger landing effects
                triggerLandingEffects(player);

                // Mark the intro as completed
                playerIntroProgress.put(player.getUniqueId(), 5);
                currentlyPlayingIntro.remove(playerId); // Remove the player from the currently playing set

                // Save the player's ground location after the intro
                savePlayerGroundLocation(player);
            });
        });

        // Schedule messages and effects during the camera movement
        scheduleIntroEffects(player, welcomeMessage1, welcomeMessage2, welcomeMessage3, welcomeMessage4);
    }

    private void setPlayerToSpectator(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                player.setGameMode(GameMode.SPECTATOR);
                player.setFlying(true);
                player.setFlySpeed(0.0f);
                player.setInvulnerable(true); // Make player invulnerable during the intro
            }
        }.runTaskLater(plugin, 1L); // Ensure this runs a tick later to overcome permission issues
    }

    private void scheduleIntroEffects(Player player, String welcomeMessage1, String welcomeMessage2, String welcomeMessage3, String welcomeMessage4) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || playerIntroProgress.get(player.getUniqueId()) >= 1) return;
                showTitle(player, welcomeMessage1, 20, 60, 20);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                player.getWorld().spawnParticle(Particle.HEART, player.getLocation(), 30, 1, 1, 1, 0.1);
                playerIntroProgress.put(player.getUniqueId(), 1);
            }
        }.runTaskLater(plugin, 120L); // Delayed to start after 6 seconds

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || playerIntroProgress.get(player.getUniqueId()) >= 2) return;
                showTitle(player, welcomeMessage2, 20, 60, 20);
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation(), 30, 1, 1, 1, 0.1);
                playerIntroProgress.put(player.getUniqueId(), 2);
            }
        }.runTaskLater(plugin, 180L);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || playerIntroProgress.get(player.getUniqueId()) >= 3) return;
                showTitle(player, welcomeMessage3, 20, 60, 20);
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
                player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation(), 30, 1, 1, 1, 0.1);
                playerIntroProgress.put(player.getUniqueId(), 3);
            }
        }.runTaskLater(plugin, 240L);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || playerIntroProgress.get(player.getUniqueId()) >= 4) return;
                showTitle(player, welcomeMessage4, 20, 60, 20);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                player.getWorld().spawnParticle(Particle.FIREWORK, player.getLocation(), 50, 1, 1, 1, 0.1);
                player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation(), 50, 1, 1, 1, 0.1);
                playerIntroProgress.put(player.getUniqueId(), 4);
            }
        }.runTaskLater(plugin, 300L);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;
                player.sendMessage(ChatColor.GREEN + "Dein Abenteuer kann weitergehen!");
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.getWorld().spawnParticle(Particle.DRAGON_BREATH, player.getLocation(), 100, 1, 1, 1, 0.1);
            }
        }.runTaskLater(plugin, 360L);
    }

    private void showTitle(Player player, String message, int fadeIn, int stay, int fadeOut) {
        player.sendTitle("", message, fadeIn, stay, fadeOut);
    }

    private void moveCamera(Player player, Location start, Location end, Runnable onComplete) {
        new BukkitRunnable() {
            double t = 0;
            double duration = 200; // Number of ticks the movement should take (10 seconds)
            @Override
            public void run() {
                t++;
                if (t > duration) {
                    player.teleport(end);
                    onComplete.run();
                    cancel();
                    return;
                }
                double progress = t / duration;
                double x = start.getX() + (end.getX() - start.getX()) * progress;
                double y = start.getY() + (end.getY() - start.getY()) * progress;
                double z = start.getZ() + (end.getZ() - start.getZ()) * progress;
                float yaw = start.getYaw() + (end.getYaw() - start.getYaw()) * (float)progress;
                float pitch = start.getPitch() + (end.getPitch() - start.getPitch()) * (float)progress;
                player.teleport(new Location(player.getWorld(), x, y, z, yaw, pitch));

                // Create beam effect during the camera movement
                player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation(), 10, 0.5, 2, 0.5, 0.1);
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 1.0f, 1.0f);

                // Gradually reduce blindness effect
                if (t % 40 == 0 && player.hasPotionEffect(PotionEffectType.BLINDNESS)) {
                    int newDuration = player.getPotionEffect(PotionEffectType.BLINDNESS).getDuration() - 40;
                    if (newDuration > 0) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, newDuration, 1, false, false));
                    } else {
                        player.removePotionEffect(PotionEffectType.BLINDNESS);
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 1); // Run every tick
    }

    private void startBeaconBeamEffect(Player player, Location start, Location end) {
        new BukkitRunnable() {
            int duration = 200;
            int step = 0;
            @Override
            public void run() {
                if (step > duration) {
                    cancel();
                    return;
                }
                step++;
                double progress = (double) step / duration;
                double x = start.getX() + (end.getX() - start.getX()) * progress;
                double y = start.getY() + (end.getY() - start.getY()) * progress;
                double z = start.getZ() + (end.getZ() - start.getZ()) * progress;
                Location loc = new Location(player.getWorld(), x, y, z);
                player.getWorld().spawnParticle(Particle.END_ROD, loc, 10, 0.5, 0.5, 0.5, 0.1);
                player.playSound(loc, Sound.BLOCK_BEACON_AMBIENT, 1.0f, 1.0f);
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    private void triggerLandingEffects(Player player) {
        player.getWorld().createExplosion(player.getLocation(), 0, false); // Sonic Boom effect
        player.getWorld().spawnParticle(Particle.SONIC_BOOM, player.getLocation(), 1);
        player.getWorld().spawnParticle(Particle.FIREWORK, player.getLocation(), 100, 1, 1, 1, 0.1);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 100, 1, 1, 1, 0.1);
        player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation(), 100, 1, 1, 1, 0.1);
        player.getWorld().spawnParticle(Particle.WITCH, player.getLocation(), 100, 1, 1, 1, 0.1);

        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.0f);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.0f);
    }

    // Check periodically for players stuck in spectator mode and restart their intro
    private void checkStuckPlayers() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID playerId = player.getUniqueId();
                    if (player.getGameMode() == GameMode.SPECTATOR && playerIntroProgress.getOrDefault(playerId, 0) < 5) {
                        // Ensure player is reset to their last known location
                        if (lastGroundLocation.containsKey(playerId)) {
                            player.teleport(lastGroundLocation.get(playerId));
                        }
                        startIntro(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // Check every second
    }

    private void savePlayerGroundLocation(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                UUID playerId = player.getUniqueId();
                if (player.isOnGround()) {
                    lastGroundLocation.put(playerId, player.getLocation());
                }
            }
        }.runTaskLater(plugin, 1L);
    }

    // Event to save player ground location when they logout
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        savePlayerGroundLocation(player);
        //closeConnection();
    }

    //BackupSystem

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuitBankScan(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Fetch XP on hand from the backup database
        int xpOnHand = getXPOnHandFromBackup(playerId);

        // Log the XP on hand
        plugin.getLogger().info("Fetched XP on hand from backup for player " + player.getName() + ": " + xpOnHand);
    }
    @EventHandler
    public void onPlayerJoinBank(PlayerJoinEvent event) {
        lock.lock();
        try {
            if (event == null || event.getPlayer() == null) {
                return; // Wenn das Event oder der Spieler null ist, beenden
            }

            Player player = event.getPlayer();
            UUID playerId = player.getUniqueId();

            if (playerId == null) {
                return; // Wenn die UUID des Spielers null ist, beenden
            }

            // Spieler-Daten initialisieren und synchronisieren
            initializePlayerData(playerId);
        } finally {
            lock.unlock();
        }
    }

    private void initializePlayerData(UUID playerId) {
        Connection connection = null;
        PreparedStatement stmt = null;
        try {
            connection = getConnection();
            int xpOnHand = getXPOnHandFromBackup(playerId);

            // Setze den Backup-Wert in der player_progress Tabelle
            stmt = connection.prepareStatement(
                    "INSERT OR REPLACE INTO player_progress (uuid, xp_on_hand) VALUES (?, ?)");
            stmt.setString(1, playerId.toString());
            stmt.setInt(2, xpOnHand);
            stmt.executeUpdate();

            // Logge die Aktualisierung
            plugin.getLogger().info("Updated player_progress for player " + playerId + " with xp_on_hand: " + xpOnHand);

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeResources(connection, stmt, null);
        }
    }


    public int getXPOnHandFromBackup(UUID playerId) {
        int xpOnHand = 0;
        Connection connection = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            connection = getConnection();
            stmt = connection.prepareStatement(
                    "SELECT xp_on_hand FROM player_progress_backup WHERE uuid = ?");
            stmt.setString(1, playerId.toString());
            rs = stmt.executeQuery();
            if (rs.next()) {
                xpOnHand = rs.getInt("xp_on_hand");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeResources(connection, stmt, rs);
        }
        return xpOnHand;
    }

    public void saveXPOnHandToBackup(UUID playerId, int xpOnHand) {
        Connection connection = null;
        PreparedStatement stmt = null;
        try {
            openConnection();
            connection = getConnection(); // Ensure connection is open before using it
            stmt = connection.prepareStatement(
                    "INSERT OR REPLACE INTO player_progress_backup (uuid, xp_on_hand) VALUES (?, ?)");
            stmt.setString(1, playerId.toString());
            stmt.setInt(2, xpOnHand);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeResources(connection, stmt, null);
        }
    }

    private void closeResources(Connection connection, PreparedStatement stmt, ResultSet rs) {
        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        if (stmt != null) {
            try {
                stmt.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }


    //END GAME ANHANG
    public void createDragonKillTable() {
        try (Connection connection = getConnection();
             Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS dragon_kills (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "kills INTEGER DEFAULT 0)");
            plugin.getLogger().info("Table dragon_kills created or already exists.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    private Map<UUID, Integer> dragonKills = new HashMap<>();

    // In GameLoop class
    public void saveDragonKills(UUID playerId, int kills) {
        // Save the updated kill count into the database
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(
                     "INSERT OR REPLACE INTO dragon_kills (uuid, kills) VALUES (?, ?)")) {
            stmt.setString(1, playerId.toString());
            stmt.setInt(2, kills);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }






    public int loadDragonKills(UUID playerId) {
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(
                     "SELECT kills FROM dragon_kills WHERE uuid = ?")) {
            stmt.setString(1, playerId.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("kills");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public boolean deductFreakyXP(Player player, int amount) {
        UUID playerId = player.getUniqueId();
        int xpOnHand = getXPOnHand(playerId); // Aktuellen XP-Bestand abrufen

        if (xpOnHand >= amount) {
            int newXPOnHand = xpOnHand - amount;
            setXPOnHand(playerId, xpOnHand - amount); // Speicherwert aktualisieren

            // Datenbankwert aktualisieren
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement("UPDATE player_progress SET xp_on_hand = ? WHERE uuid = ?")) {
                stmt.setInt(1, xpOnHand - amount);
                stmt.setString(2, playerId.toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
                player.sendMessage(ChatColor.RED + "Es gab einen Fehler beim Abziehen deiner Freaky XP.");
                return false;
            }
            // Backup des neuen Wertes
            saveXPOnHandToBackup(playerId, newXPOnHand);
            return true;
        } else {
            player.sendMessage(ChatColor.RED + "Du hast nicht genügend Freaky XP.");
            return false;
        }
    }

    public int getDragonKills(UUID playerId) {
        return loadDragonKills(playerId);
    }

    private int getXPOnHand(UUID playerId) {
        int xpOnHand = 0;
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement("SELECT xp_on_hand FROM player_progress WHERE uuid = ?")) {
            stmt.setString(1, playerId.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                xpOnHand = rs.getInt("xp_on_hand");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return xpOnHand;
    }

    private void setXPOnHand(UUID playerId, int amount) {
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement("UPDATE player_progress SET xp_on_hand = ? WHERE uuid = ?")) {
            stmt.setInt(1, amount);
            stmt.setString(2, playerId.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


}

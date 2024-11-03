package h34r7l3s.freakyworld;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.*;
import java.text.DecimalFormat;
import java.util.*;

public class FreakyBankRobber implements Listener {
    private final JavaPlugin plugin;
    private final GameLoop gameLoop;
    private Villager bankRobberVillager;
    private static final String VILLAGER_NAME = "Mysteriöser Freak";

    public FreakyBankRobber(JavaPlugin plugin, GameLoop gameLoop) {
        this.plugin = plugin;
        this.gameLoop = gameLoop;
        //updateDatabaseStructure();
        spawnBankRobberVillager();
        Bukkit.getPluginManager().registerEvents(this, plugin);

    }

    private void openConnection() {
        if (gameLoop.getConnection() == null) {
            gameLoop.getConnection();
        }
    }

    private void spawnBankRobberVillager() {
        World world = Bukkit.getWorlds().get(0); // Get the first world
        if (world != null) {
            Location villagerLocation = new Location(world, 13, 103, 61); // Change coordinates as needed
            bankRobberVillager = world.spawn(villagerLocation, Villager.class);
            bankRobberVillager.setCustomName(VILLAGER_NAME);
            bankRobberVillager.setCustomNameVisible(true);
            bankRobberVillager.setAI(false); // Disable AI to prevent movement
            bankRobberVillager.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 255, false, false)); // Prevent movement
            bankRobberVillager.setInvulnerable(true); // Make the villager unkillable

            // Kleidung und Effekte für den Villager
            bankRobberVillager.setProfession(Villager.Profession.NITWIT); // Set profession to make it look unique
            bankRobberVillager.getEquipment().setHelmet(new ItemStack(Material.LEATHER_HELMET));
            bankRobberVillager.getEquipment().setChestplate(new ItemStack(Material.CHAINMAIL_CHESTPLATE));
            bankRobberVillager.getEquipment().setLeggings(new ItemStack(Material.LEATHER_LEGGINGS));
            bankRobberVillager.getEquipment().setBoots(new ItemStack(Material.LEATHER_BOOTS));
        } else {
            plugin.getLogger().warning("Keine Welt gefunden, um den Bankräuber zu spawnen.");
        }
    }
    public void removeBankRobberVillager() {
        if (bankRobberVillager != null && !bankRobberVillager.isDead()) {
            bankRobberVillager.remove();
            plugin.getLogger().info("Der mysteriöse Bankräuber wurde entfernt.");
        } else {
            plugin.getLogger().warning("Kein mysteriöser Bankräuber vorhanden oder er ist bereits tot.");
        }
    }
    // Boolean, der steuert, ob ein Bankraub möglich ist
    private boolean isBankRobberyEnabled = false;
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof Villager) {
            Villager clickedVillager = (Villager) event.getRightClicked();

            // Überprüfen, ob es sich um den spezifischen Villager handelt
            if (clickedVillager.equals(bankRobberVillager)) {
                Player player = event.getPlayer();

                // Überprüfen, ob der Bankraub aktiviert ist
                if (isBankRobberyEnabled) {
                    // Bankraub ist möglich - Menü öffnen
                    openBankRobberMenu(player);
                } else {
                    // Bankraub ist deaktiviert - neutrale Nachricht senden
                    player.sendMessage(ChatColor.GRAY + "Der Villager mustert dich skeptisch und bleibt ruhig.");
                    player.sendMessage(ChatColor.GRAY + "Vielleicht gibt es hier eines Tages mehr zu entdecken...");
                }
            }
        }
    }

    private void openBankRobberMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27, ChatColor.DARK_RED + "Bankräuber Menü");

        // Top 10 Banken anzeigen
        ItemStack top10Item = createMenuItem(Material.PAPER, ChatColor.GOLD + "Top 10 Banken",
                ChatColor.YELLOW + "Die besten Spione haben herausgefunden,",
                ChatColor.YELLOW + "welche Spieler die reichsten Bankkonten haben.");
        inventory.setItem(10, top10Item);

        // Gesamtwert aller Banken und aktueller Bonus anzeigen
        ItemStack totalValueItem = createMenuItem(Material.GOLD_BLOCK, ChatColor.GOLD + "Gesamtwert + Bonus",
                ChatColor.YELLOW + "Die zusammengerechneten Reichtümer",
                ChatColor.YELLOW + "aller Spielerbanken und dein Bonus.");
        inventory.setItem(12, totalValueItem);

        // Konto des Villagers anzeigen
        ItemStack villagerAccountItem = createMenuItem(Material.CHEST, ChatColor.GOLD + "Villager Konto",
                ChatColor.YELLOW + "Hier siehst du die Ersparnisse",
                ChatColor.YELLOW + "des mysteriösen Bankräubers.");
        inventory.setItem(14, villagerAccountItem);

        // Option für den Bankraub hinzufügen
        ItemStack heistItem = createMenuItem(Material.DIAMOND_SWORD, ChatColor.RED + "Bankraub starten",
                ChatColor.YELLOW + "Plane und starte einen gewagten Bankraub.",
                ChatColor.RED + "Du benötigst mindestens 10.000 Freaky XP on hand.");
        inventory.setItem(16, heistItem);

        // Dekoration und Platzhalter hinzufügen
        ItemStack glassPane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta paneMeta = glassPane.getItemMeta();
        paneMeta.setDisplayName(" ");
        glassPane.setItemMeta(paneMeta);

        for (int i = 0; i < 27; i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, glassPane);
            }
        }

        player.openInventory(inventory);
    }

    private ItemStack createMenuItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        String title = ChatColor.stripColor(event.getView().getTitle());

        if (title.equals(ChatColor.stripColor(ChatColor.DARK_RED + "Bankräuber Menü"))) {
            event.setCancelled(true);
            handleBankRobberMenuClick(event, player);
        }
    }

    private void handleBankRobberMenuClick(InventoryClickEvent event, Player player) {
        if (event.getCurrentItem() == null) {
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        String displayName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());

        openConnection(); // Ensure connection is open before handling the click

        switch (displayName) {
            case "Top 10 Banken":
                showTop10Banks(player, event.getInventory());
                break;
            case "Gesamtwert + Bonus":
                showTotalValueAndBonus(player, event.getInventory());
                break;
            case "Villager Konto":
                showVillagerAccount(player, event.getInventory());
                break;
            case "Bankraub starten":
                if (hasEnoughXPOnHand(player)) {
                    deductXPOnHand(player, 10000); // Deduct 10,000 Freaky XP on hand
                    startHeistEvent(player);
                } else {
                    player.sendMessage(ChatColor.RED + "Du benötigst mindestens 10.000 Freaky XP on hand, um einen Bankraub zu starten!");
                }
                break;
        }
    }

    private boolean hasEnoughXPOnHand(Player player) {
        openConnection(); // Ensure connection is open before executing the query
        try (PreparedStatement stmt = gameLoop.getConnection().prepareStatement("SELECT xp_on_hand FROM player_progress WHERE uuid = ?")) {
            stmt.setString(1, player.getUniqueId().toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("xp_on_hand") >= 10000;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private void deductXPOnHand(Player player, int amount) {
        openConnection(); // Ensure connection is open before executing the query
        try (PreparedStatement stmt = gameLoop.getConnection().prepareStatement("UPDATE player_progress SET xp_on_hand = xp_on_hand - ? WHERE uuid = ?")) {
            stmt.setInt(1, amount);
            stmt.setString(2, player.getUniqueId().toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void showTop10Banks(Player player, Inventory inventory) {
        openConnection(); // Ensure connection is open before executing the query
        try (PreparedStatement stmt = gameLoop.getConnection().prepareStatement("SELECT player_name, xp_in_bank FROM player_progress ORDER BY xp_in_bank DESC LIMIT 10")) {
            ResultSet rs = stmt.executeQuery();
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.YELLOW + "Die reichsten Spieler:");

            while (rs.next()) {
                String playerName = rs.getString("player_name");
                int xpInBank = rs.getInt("xp_in_bank");
                lore.add(ChatColor.YELLOW + "Spieler: " + ChatColor.MAGIC + playerName + ChatColor.YELLOW + " - XP: " + formatNumber(xpInBank));
            }

            ItemStack top10Item = inventory.getItem(10);
            ItemMeta top10Meta = top10Item.getItemMeta();
            top10Meta.setLore(lore);
            top10Item.setItemMeta(top10Meta);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void showTotalValueAndBonus(Player player, Inventory inventory) {
        openConnection(); // Ensure connection is open before executing the query
        try (PreparedStatement stmt = gameLoop.getConnection().prepareStatement("SELECT SUM(xp_in_bank) AS total_value FROM player_progress")) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                int totalValue = rs.getInt("total_value");
                double bonus = getBonus(player); // Get the bonus from the database
                double totalWithBonus = totalValue * (1 + bonus / 100);
                List<String> lore = Arrays.asList(
                        ChatColor.YELLOW + "Gesamtwert aller Banken: " + formatNumber(totalValue),
                        ChatColor.YELLOW + "Aktueller Bonus: " + String.format("%.2f%%", bonus),
                        ChatColor.YELLOW + "Gesamt mit Bonus: " + formatNumber((int) totalWithBonus)
                );

                ItemStack totalValueItem = inventory.getItem(12);
                ItemMeta totalValueMeta = totalValueItem.getItemMeta();
                totalValueMeta.setLore(lore);
                totalValueItem.setItemMeta(totalValueMeta);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void showVillagerAccount(Player player, Inventory inventory) {
        openConnection(); // Ensure connection is open before executing the query
        try (PreparedStatement stmt = gameLoop.getConnection().prepareStatement("SELECT villager_account FROM bank_robber_data WHERE uuid = 'villager'")) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                int villagerAccount = rs.getInt("villager_account");
                List<String> lore = Collections.singletonList(ChatColor.YELLOW + "Villager Konto: " + formatNumber(villagerAccount) + " Freaky XP");

                ItemStack villagerAccountItem = inventory.getItem(14);
                ItemMeta villagerAccountMeta = villagerAccountItem.getItemMeta();
                villagerAccountMeta.setLore(lore);
                villagerAccountItem.setItemMeta(villagerAccountMeta);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private int getHeistCount(Player player) {
        openConnection(); // Ensure connection is open before executing the query
        try (PreparedStatement stmt = gameLoop.getConnection().prepareStatement("SELECT heist_count FROM bank_robber_data WHERE uuid = ?")) {
            stmt.setString(1, player.getUniqueId().toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("heist_count");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0; // Default to 0 if no record found
    }

    private double getBonus(Player player) {
        openConnection(); // Ensure connection is open before executing the query
        try (PreparedStatement stmt = gameLoop.getConnection().prepareStatement("SELECT bonus FROM bank_robber_data WHERE uuid = ?")) {
            stmt.setString(1, player.getUniqueId().toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getDouble("bonus");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0; // Default to 0 if no record found
    }

    //Hauptschleife
    //Phasenweise Durchfürhung, verschieden schwierig und aufwendig
    //Phase 1 Vorbereitung und Auskundschaften
    //Phase 2 Planung und weitere Vorbereitung
    //Phase 3 Durchführung + Flucht + Reward Cooldown
    //Phase 4 (complete)


    // 4 Stunden = 1 Server Neustart Interval
    // Zeitaufwand für BANKRAUB (Aktives Event)
    // ohne Vorbereitungszeit == 1H
    //
    //
    //
    // Kundschaften, Zubehör beschaffen, Informationen eintreiben, Gegenstände stehlen (Vergleich Autos / Laster / Boot),
    // Leute bestechen / quälen / töten,
    //
    // TNT, Minecart (mit TNT / mit Chest?), Boote (mit Chest?), Scaffholdings, Enderperlen, Spinennenetze, barrel, Dragon Breath, Paper / empty Maps / Books?,
    // Repeater, Redstone, Resdstonetorch, Skulk-Sensor, Blaze Rod, Magma Cream,
    //
    //
    // Tresor muss verteidigt werden (aufknacken)
    // Spielergebunden: Nur wenn Gewisse Anzhal weiterer Spieler online sind
    // Spieler gibt per Chat eine Kooridnate an (X Y Z)
    // --> Um 5 Radius =) Lager / Rückzugsort
    // wenn der Bankraub "abgeschlossen ist", nicht das Freaky XP erhält,
    // sondern X Zeit am Rückzugzucksort verweilen / verteigen muss#
    // Davon kommen

    private void startHeistEvent(Player player) {


        player.sendMessage(ChatColor.RED + "Du hast einen Bankraub gestartet!");

        new BukkitRunnable() {
            @Override
            public void run() {
                player.sendMessage(ChatColor.GREEN + "Phase 1: Sammle die benötigten Gegenstände!");
                // Code for collecting items
            }
        }.runTaskLater(plugin, 100L); // 5 seconds delay

        new BukkitRunnable() {
            @Override
            public void run() {
                player.sendMessage(ChatColor.GREEN + "Phase 2: Übergebe die Gegenstände!");
                // Code for submitting items
            }
        }.runTaskLater(plugin, 200L); // 10 seconds delay

        new BukkitRunnable() {
            @Override
            public void run() {
                player.sendMessage(ChatColor.GREEN + "Phase 3: Führe den Raub durch!");
                // Code for performing the heist
            }
        }.runTaskLater(plugin, 300L); // 15 seconds delay

        new BukkitRunnable() {
            @Override
            public void run() {
                completeHeist(player);
            }
        }.runTaskLater(plugin, 400L); // 20 seconds delay
    }



    private void completeHeist(Player player) {
        player.sendMessage(ChatColor.GREEN + "Bankraub erfolgreich!");

        openConnection(); // Ensure connection is open before executing the queries
        // Update the heist count and villager account
        try {
            Connection connection = gameLoop.getConnection();
            connection.setAutoCommit(false);

            // Ensure player has an entry in the bank_robber_data table
            ensurePlayerData(player);

            // Get total value with bonus
            int heistCount = getHeistCount(player);
            double bonus = getBonus(player);
            int totalValue = getTotalValue();
            int reward = (int) (totalValue * (1 + bonus / 100));
            int villagerShare = (int) (reward * 0.1);
            int playerShare = reward - villagerShare;

            // Update heist count and bonus for player
            double newBonus = calculateBonus(heistCount + 1);
            try (PreparedStatement stmt = connection.prepareStatement("UPDATE bank_robber_data SET heist_count = heist_count + 1, bonus = ? WHERE uuid = ?")) {
                stmt.setDouble(1, newBonus);
                stmt.setString(2, player.getUniqueId().toString());
                stmt.executeUpdate();
            }

            // Update villager account
            try (PreparedStatement stmt = connection.prepareStatement("UPDATE bank_robber_data SET villager_account = villager_account + ? WHERE uuid = 'villager'")) {
                stmt.setInt(1, villagerShare);
                stmt.executeUpdate();
            }

            // Update player's Freaky XP on hand
            try (PreparedStatement stmt = connection.prepareStatement("UPDATE player_progress SET xp_on_hand = xp_on_hand + ? WHERE uuid = ?")) {
                stmt.setInt(1, playerShare);
                stmt.setString(2, player.getUniqueId().toString());
                stmt.executeUpdate();
            }

            // Clear all players' xp_in_bank
            try (PreparedStatement stmt = connection.prepareStatement("UPDATE player_progress SET xp_in_bank = 0")) {
                stmt.executeUpdate();
            }

            connection.commit();
            connection.setAutoCommit(true);

            // Notify the player
            player.sendMessage(ChatColor.GREEN + "Du hast " + formatNumber(playerShare) + " Freaky XP erhalten!");
            player.sendMessage(ChatColor.GREEN + "Der Bankräuber hat " + formatNumber(villagerShare) + " Freaky XP als seinen Anteil genommen!");

            // Update the UI to reflect the new bonus
            openBankRobberMenu(player);

        } catch (SQLException e) {
            try {
                gameLoop.getConnection().rollback();
            } catch (SQLException rollbackException) {
                rollbackException.printStackTrace();
            }
            e.printStackTrace();
        }
    }

    private int getTotalValue() {
        openConnection(); // Ensure connection is open before executing the query
        try (PreparedStatement stmt = gameLoop.getConnection().prepareStatement("SELECT SUM(xp_in_bank) AS total_value FROM player_progress")) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("total_value");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    private double calculateBonus(int heistCount) {
        return 0.5 * Math.pow(2, heistCount); // Start at 0.5% and double with each heist
    }

    private String formatNumber(int number) {
        if (number >= 1_000_000) {
            return new DecimalFormat("#,###").format(number);
        }
        return String.valueOf(number);
    }

    private void ensurePlayerData(Player player) {
        try (PreparedStatement stmt = gameLoop.getConnection().prepareStatement("INSERT OR IGNORE INTO bank_robber_data (uuid, heist_count, villager_account, bonus) VALUES (?, 0, 0, 0)")) {
            stmt.setString(1, player.getUniqueId().toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void updateDatabaseStructure() {
        openConnection(); // Ensure connection is open before updating the database
        try (Connection connection = gameLoop.getConnection();
             Statement statement = connection.createStatement()) {

            // Log for debugging
            plugin.getLogger().info("Attempting to create or update the database structure...");

            // Create the table if it does not exist
            plugin.getLogger().info("Executing CREATE TABLE statement...");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS bank_robber_data (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "heist_count INT DEFAULT 0, " +
                    "villager_account INT DEFAULT 0, " +
                    "bonus DOUBLE DEFAULT 0)");
            plugin.getLogger().info("Table bank_robber_data created or already exists.");

            // Check if the 'bonus' column exists and add it if missing
            plugin.getLogger().info("Checking for existence of 'bonus' column...");
            try (ResultSet rs = statement.executeQuery("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
                    "WHERE TABLE_NAME = 'bank_robber_data' AND COLUMN_NAME = 'bonus'")) {
                boolean hasBonusColumn = rs.next();

                if (!hasBonusColumn) {
                    plugin.getLogger().info("Column 'bonus' does not exist. Adding column...");
                    statement.executeUpdate("ALTER TABLE bank_robber_data ADD COLUMN bonus DOUBLE DEFAULT 0");
                    plugin.getLogger().info("Column 'bonus' added to table bank_robber_data.");
                } else {
                    plugin.getLogger().info("Column 'bonus' already exists.");
                }
            }

            // Ensure villager account exists
            plugin.getLogger().info("Ensuring villager account exists...");
            try (PreparedStatement stmt = connection.prepareStatement(
                    "INSERT INTO bank_robber_data (uuid, villager_account, bonus) VALUES ('villager', 0, 0)")) {
                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected > 0) {
                    plugin.getLogger().info("Villager account inserted into bank_robber_data table.");
                } else {
                    plugin.getLogger().info("Villager account already exists in bank_robber_data table.");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error updating database structure: " + e.getMessage());
            e.printStackTrace();
        } finally {
            gameLoop.closeConnection();
        }
    }











}

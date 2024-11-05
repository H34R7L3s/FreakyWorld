package h34r7l3s.freakyworld;

import io.th0rgal.oraxen.api.OraxenItems;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.util.Vector;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class GameLoopHCFW implements Listener {
    private JavaPlugin plugin;
    private final GameLoop gameLoop;
    private final HashMap<UUID, Integer> playerXpInHand;
    private final Map<UUID, UUID> lastDamageBy;

    private static final int XP_RADIUS = 5;
    private static final int BASE_XP = 10;
    private int xpMultiplier = 1;

    private boolean eventInProgress = false;

    public GameLoopHCFW(JavaPlugin plugin, GameLoop gameLoop) {
        this.plugin = plugin;
        this.gameLoop = gameLoop;
        this.playerXpInHand = new HashMap<>();
        this.lastDamageBy = new HashMap<>();
        loadEventStatus();
    }

    public void initialize() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        startXpTrackingTask();
    }

    private void startXpTrackingTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getWorld().getName().equals("HCFW")) {
                        int xpToAdd = calculateNearbyEntityDeaths(player);
                        if (xpToAdd > 0) {
                            addXPToPlayer(player, xpToAdd);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 20);
    }

    private int calculateNearbyEntityDeaths(Player player) {
        int xpToAdd = 0;
        for (Entity entity : player.getNearbyEntities(XP_RADIUS, XP_RADIUS, XP_RADIUS)) {
            if (entity instanceof Player) continue;
            if (!entity.isDead()) continue;

            xpToAdd += BASE_XP * xpMultiplier;
        }
        return xpToAdd;
    }

    public void openHCFWMenu(Player player) {
        int playerItems = getPlayerItemsSubmitted(player.getUniqueId());

        Inventory hcfwInventory = Bukkit.createInventory(null, 9, "HCFW Events");

        ItemStack comingSoonItem = OraxenItems.getItemById("runicanimated_hat1").build(); // Beispiel-Oraxen-ID
        ItemMeta meta = comingSoonItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "Dein Pfad zur Macht");
            meta.setLore(Arrays.asList(
                    ChatColor.DARK_GRAY + "» Bist du bereit, die Hardcore-Welt zu meistern?",
                    ChatColor.DARK_GRAY + "  Hier regieren nur die Wagemutigsten.",
                    "",
                    ChatColor.GRAY + "» Erobere die gefährlichsten Gebiete, bezwinge",
                    ChatColor.GRAY + "  die stärksten Gegner und beherrsche die Arena.",
                    "",
                    ChatColor.DARK_PURPLE + "» Wer dieses Menü freigeschaltet hat, kennt",
                    ChatColor.DARK_PURPLE + "  den Weg zur wahren Macht.",
                    ChatColor.DARK_PURPLE + "  Und für die Elite: Hier steuerst du alles.",
                    "",
                    ChatColor.DARK_AQUA + "» Tritt ein... wenn du es wagst."
            ));
            comingSoonItem.setItemMeta(meta);
        }
        hcfwInventory.setItem(1, comingSoonItem);



        ItemStack hcfwEventItem = new ItemStack(Material.ENDER_PEARL);
        ItemMeta hcfwEventMeta = hcfwEventItem.getItemMeta();
        if (hcfwEventMeta != null) {
            hcfwEventMeta.setDisplayName(ChatColor.GREEN + "HCFW Event - "+ChatColor.RED +"Quick Warp");
            hcfwEventMeta.setLore(Arrays.asList(
                    ChatColor.AQUA + "Starte sofort dein HCFW Abenteuer und farme Freaky XP.",
                    //ChatColor.GREEN + "Ganz ohne Teleportkosten ;)",
                    ChatColor.YELLOW + "",
                    ChatColor.RED + "Bei klick wirst DU sofort in die HCFW teleportiert!!"
            ));
            hcfwEventItem.setItemMeta(hcfwEventMeta);
        }
        if (playerItems >= 1000) {
            hcfwInventory.setItem(4, hcfwEventItem);
        }



        ItemStack specialEventItem = new ItemStack(Material.END_CRYSTAL);
        ItemMeta specialEventMeta = specialEventItem.getItemMeta();
        if (specialEventMeta != null) {
            specialEventMeta.setDisplayName("Starte spezielle Events");
            specialEventMeta.setLore(Arrays.asList("Wähle eine Event-Version."));
            specialEventItem.setItemMeta(specialEventMeta);
        }

        if (playerItems >= 10000) {
            hcfwInventory.setItem(6, specialEventItem);
        }

        player.openInventory(hcfwInventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        if (event.getView().getTitle().equals("HCFW Events")) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;

            if (event.getCurrentItem().getType() == Material.ENDER_PEARL) {
                teleportToHCFW(player);
            } else if (event.getCurrentItem().getType() == Material.END_CRYSTAL) {
                openSpecialEventMenu(player);
            }
        } else if (event.getView().getTitle().equals("Wähle ein Event")) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;

            if (event.getCurrentItem().getType() == Material.PIGLIN_HEAD) {
                ItemMeta meta = event.getCurrentItem().getItemMeta();
                if (meta != null && meta.hasDisplayName()) {
                    switch (meta.getDisplayName()) {
                        case "Event Version 1":
                            startSpecialEvent(player, 25000, 1);
                            break;
                        case "Event Version 2":
                            startSpecialEvent(player, 40000, 2);
                            break;
                        case "Event Version 3":
                            startSpecialEvent(player, 80000, 3);
                            break;
                        case "Event Version 4":
                            startSpecialEvent(player, 120000, 4);
                            break;
                    }
                }
            }
        }
    }



    private void openSpecialEventMenu(Player player) {
        Inventory specialEventInventory = Bukkit.createInventory(null, 9, "Wähle ein Event");

        ItemStack version1 = new ItemStack(Material.PIGLIN_HEAD);
        ItemMeta version1Meta = version1.getItemMeta();
        if (version1Meta != null) {
            version1Meta.setDisplayName("Event Version 1");
            version1Meta.setLore(Arrays.asList("Kosten: 25,000 Freaky XP", "Belohnungen: Random"));
            version1.setItemMeta(version1Meta);
        }
        specialEventInventory.setItem(0, version1);

        ItemStack version2 = new ItemStack(Material.PIGLIN_HEAD);
        ItemMeta version2Meta = version2.getItemMeta();
        if (version2Meta != null) {
            version2Meta.setDisplayName("Event Version 2");
            version2Meta.setLore(Arrays.asList("Kosten: 40,000 Freaky XP", "Belohnungen: Mehr Chancen auf Silber"));
            version2.setItemMeta(version2Meta);
        }
        specialEventInventory.setItem(2, version2);

        ItemStack version3 = new ItemStack(Material.PIGLIN_HEAD);
        ItemMeta version3Meta = version3.getItemMeta();
        if (version3Meta != null) {
            version3Meta.setDisplayName("Event Version 3");
            version3Meta.setLore(Arrays.asList("Kosten: 80,000 Freaky XP", "Belohnungen: Mehr Chancen auf Silber und Gold"));
            version3.setItemMeta(version3Meta);
        }
        specialEventInventory.setItem(4, version3);

        ItemStack version4 = new ItemStack(Material.PIGLIN_HEAD);
        ItemMeta version4Meta = version4.getItemMeta();
        if (version4Meta != null) {
            version4Meta.setDisplayName("Event Version 4");
            version4Meta.setLore(Arrays.asList("Kosten: 120,000 Freaky XP", "Belohnungen: Mehr Chancen auf Gold und Eggmac"));
            version4.setItemMeta(version4Meta);
        }
        specialEventInventory.setItem(6, version4);

        player.openInventory(specialEventInventory);
    }

    private void teleportToHCFW(Player player) {
        World hcfwWorld = Bukkit.getWorld("HCFW");
        if (hcfwWorld == null) {
            player.sendMessage("Die HCFW-Welt ist nicht geladen.");
            return;
        }
        //Spawn Location für SaveSpawn
        Location hcfwLocation = hcfwWorld.getSpawnLocation();

        player.teleport(hcfwLocation);
        player.sendMessage("Du wurdest in die HCFW-Welt teleportiert! Töte Mobs für Freaky XP.");
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity().getWorld().getName().equals("HCFW") && event.getEntity() instanceof LivingEntity) {
            Entity damager = event.getDamager();
            if (damager instanceof Player) {
                Player player = (Player) damager;
                lastDamageBy.put(event.getEntity().getUniqueId(), player.getUniqueId());
            }
        }
    }

    @EventHandler
    public void onEntityDeathHCFWStealXP(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        UUID entityId = entity.getUniqueId();

        if (entity.getWorld().getName().equals("HCFW")) {
            if (entity instanceof LivingEntity) {
                LivingEntity livingEntity = (LivingEntity) entity;
                Player killer = livingEntity.getKiller();
                if (killer != null) {
                    // Füge dem Killer XP hinzu und sichere sie ins Backup
                    addXPToPlayer(killer, BASE_XP * xpMultiplier);
                    gameLoop.saveXPOnHandToBackup(killer.getUniqueId(), getPlayerXPOnHand(killer));

                    if (entity instanceof Player) {
                        Player victim = (Player) entity;
                        int stolenXp = getPlayerXPOnHandFromDatabase(victim);
                        if (stolenXp > 0) {
                            // Entferne die XP vom Opfer und sichere die aktualisierten Werte ins Backup
                            removeXPFromPlayer(victim, stolenXp);
                            gameLoop.saveXPOnHandToBackup(victim.getUniqueId(), getPlayerXPOnHand(victim));

                            // Füge die gestohlenen XP dem Killer hinzu und sichere sie ins Backup
                            addXPToPlayer(killer, stolenXp);
                            gameLoop.saveXPOnHandToBackup(killer.getUniqueId(), getPlayerXPOnHand(killer));

                            killer.sendMessage("Du hast " + stolenXp + " Freaky XP von " + victim.getName() + " gestohlen!");
                            gameLoop.preventXPReductionOnDeath(victim);
                        } else {
                            killer.sendMessage(victim.getName() + " hatte keine Freaky XP zum Stehlen.");
                        }
                    }
                } else if (lastDamageBy.containsKey(entityId)) {
                    UUID damagerId = lastDamageBy.get(entityId);
                    Player damager = Bukkit.getPlayer(damagerId);
                    if (damager != null && damager.getWorld().getName().equals("HCFW")) {
                        // Füge XP dem letzten Angreifer hinzu und sichere sie ins Backup
                        addXPToPlayer(damager, BASE_XP * xpMultiplier);
                        gameLoop.saveXPOnHandToBackup(damager.getUniqueId(), getPlayerXPOnHand(damager));

                        lastDamageBy.remove(entityId);
                    }
                } else {
                    for (Player nearbyPlayer : getNearbyPlayers(entity.getLocation(), XP_RADIUS)) {
                        // Füge den nahegelegenen Spielern XP hinzu und sichere sie ins Backup
                        addXPToPlayer(nearbyPlayer, BASE_XP * xpMultiplier);
                        gameLoop.saveXPOnHandToBackup(nearbyPlayer.getUniqueId(), getPlayerXPOnHand(nearbyPlayer));
                    }
                }
            }
        }
    }


    private List<Player> getNearbyPlayers(Location location, int radius) {
        List<Player> players = new ArrayList<>();
        for (Entity entity : location.getWorld().getNearbyEntities(location, radius, radius, radius)) {
            if (entity instanceof Player) {
                players.add((Player) entity);
            }
        }
        return players;
    }

    private void addXPToPlayer(Player player, int xp) {
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        player.sendMessage(xp + " Freaky XP erhalten!");

        try (Connection connection = gameLoop.getConnection()) {
            connection.setAutoCommit(false);

            try (PreparedStatement stmt = connection.prepareStatement(
                    "INSERT INTO player_progress (uuid, player_name, xp_on_hand, freaky_xp) VALUES (?, ?, ?, ?) " +
                            "ON CONFLICT(uuid) DO UPDATE SET xp_on_hand = xp_on_hand + ?, freaky_xp = freaky_xp + ?, player_name = ?")) {
                stmt.setString(1, playerId.toString());
                stmt.setString(2, playerName);
                stmt.setInt(3, xp);
                stmt.setInt(4, xp);
                stmt.setInt(5, xp);
                stmt.setInt(6, xp);
                stmt.setString(7, playerName);
                stmt.executeUpdate();
            }

            try (PreparedStatement stmt = connection.prepareStatement(
                    "INSERT INTO player_item_categories (uuid, category, player_name, items_submitted) VALUES (?, 'HCFW', ?, ?) " +
                            "ON CONFLICT(uuid, category) DO UPDATE SET items_submitted = items_submitted + ?, player_name = ?")) {
                stmt.setString(1, playerId.toString());
                stmt.setString(2, playerName);
                stmt.setInt(3, xp);
                stmt.setInt(4, xp);
                stmt.setString(5, playerName);
                stmt.executeUpdate();
            }

            connection.commit();

            int currentXp = playerXpInHand.getOrDefault(playerId, 0);
            playerXpInHand.put(playerId, currentXp + xp);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void removeXPFromBankEvent(UUID playerId, int xp) {
        try (Connection connection = gameLoop.getConnection()) {
            connection.setAutoCommit(false);

            try (PreparedStatement stmt = connection.prepareStatement(
                    "UPDATE player_progress SET xp_in_bank = xp_in_bank - ? WHERE uuid = ?")) {
                stmt.setInt(1, xp);
                stmt.setString(2, playerId.toString());
                stmt.executeUpdate();
            }

            connection.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private int getPlayerXPOnHand(Player player) {
        UUID playerId = player.getUniqueId();
        return getPlayerXPOnHandFromDatabase(player);
    }

    private int getPlayerXPOnHandFromDatabase(Player player) {
        UUID playerId = player.getUniqueId();
        int xp = 0;

        try (Connection connection = gameLoop.getConnection();
             PreparedStatement stmt = connection.prepareStatement(
                     "SELECT xp_on_hand FROM player_progress WHERE uuid = ?")) {
            stmt.setString(1, playerId.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                xp = rs.getInt("xp_on_hand");
                playerXpInHand.put(playerId, xp);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return xp;
    }

    private void removeXPFromPlayer(Player player, int xp) {
        UUID playerId = player.getUniqueId();
        try (Connection connection = gameLoop.getConnection()) {
            connection.setAutoCommit(false);

            int currentXp = playerXpInHand.getOrDefault(playerId, 0);
            int newXpOnHand = Math.max(0, currentXp - xp);
            playerXpInHand.put(playerId, Math.max(0, currentXp - xp));

            try (PreparedStatement stmt = connection.prepareStatement(
                    "UPDATE player_progress SET xp_on_hand = xp_on_hand - ? WHERE uuid = ?")) {
                stmt.setInt(1, xp);
                stmt.setString(2, playerId.toString());
                stmt.executeUpdate();
            }

            try (PreparedStatement stmt = connection.prepareStatement(
                    "INSERT INTO player_item_categories (uuid, category, items_submitted) VALUES (?, 'HCFW', ?) " +
                            "ON CONFLICT(uuid, category) DO UPDATE SET items_submitted = items_submitted - ?")) {
                stmt.setString(1, playerId.toString());
                stmt.setInt(2, -xp);
                stmt.setInt(3, xp);
                stmt.executeUpdate();
            }
            // Backup des neuen XP-Wertes
            gameLoop.saveXPOnHandToBackup(playerId, newXpOnHand);
            connection.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        playerXpInHand.put(playerId, getPlayerXPOnHandFromDatabase(player));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        updatePlayerXPInDatabase(playerId, playerXpInHand.getOrDefault(playerId, 0));
        playerXpInHand.remove(playerId);
    }

    private void updatePlayerXPInDatabase(UUID playerId, int xp) {
        try (Connection connection = gameLoop.getConnection();
             PreparedStatement stmt = connection.prepareStatement(
                     "UPDATE player_progress SET xp_on_hand = ? WHERE uuid = ?")) {
            stmt.setInt(1, xp);
            stmt.setString(2, playerId.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void openHCFWMenuIfEligible(Player player) {
        int playerItems = gameLoop.getQuestVillager().getPlayerContribution(player);
        if (playerItems >= 5000) {
            openHCFWMenu(player);
        } else {
            player.sendMessage("Du musst mindestens 5.000 Gegenstände abgegeben haben, um das HCFW Menü zu öffnen.");
        }
    }

    private void loadEventStatus() {
        File file = new File(plugin.getDataFolder(), "eventStatus.txt");
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String status = reader.readLine();
                eventInProgress = Boolean.parseBoolean(status);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void saveEventStatus() {
        File file = new File(plugin.getDataFolder(), "eventStatus.txt");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(Boolean.toString(eventInProgress));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startSpecialEvent(Player player, int xpRequired, int version) {
        if (eventInProgress) {
            player.sendMessage(ChatColor.RED + "Ein Event läuft bereits. Bitte warte, bis es abgeschlossen ist.");
            return;
        }

        UUID playerId = player.getUniqueId();
        int xpOnHand = getPlayerXPOnHand(player);
        int xpInBank = gameLoop.getPlayerXPInBank(playerId);

        if (xpOnHand + xpInBank < xpRequired) {
            player.sendMessage(ChatColor.RED + "Du hast nicht genügend Freaky XP, um dieses Event zu starten.");
            return;
        }

        int itemsSubmitted = getPlayerItemsSubmitted(playerId);
        if (itemsSubmitted < 50000) {
            player.sendMessage(ChatColor.RED + "Du musst mindestens 50.000 HCFW Punkte haben, um diese Events zu starten.");
            return;
        }

        if (xpOnHand >= xpRequired) {
            removeXPFromPlayer(player, xpRequired);
        } else {
            int remainingXP = xpRequired - xpOnHand;
            removeXPFromPlayer(player, xpOnHand);
            removeXPFromBankEvent(playerId, remainingXP);
        }
        // Nach dem Abziehen der XP, Backup aktualisieren
        int newXPOnHand = getPlayerXPOnHand(player);
        gameLoop.saveXPOnHandToBackup(playerId, newXPOnHand);
        player.sendMessage(ChatColor.GREEN + "Du hast das spezielle Event gestartet!");

        eventInProgress = true;
        saveEventStatus();
        startEvent(player, version);
    }

    private int getPlayerItemsSubmitted(UUID playerId) {
        int itemsSubmitted = 0;
        try (Connection connection = gameLoop.getConnection();
             PreparedStatement stmt = connection.prepareStatement(
                     "SELECT items_submitted FROM player_item_categories WHERE uuid = ? AND category = 'HCFW'")) {
            stmt.setString(1, playerId.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                itemsSubmitted = rs.getInt("items_submitted");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return itemsSubmitted;
    }


    private void startEvent(Player player, int version) {
        World hcfwWorld = Bukkit.getWorld("HCFW");
        if (hcfwWorld == null) {
            player.sendMessage(ChatColor.RED + "Die HCFW-Welt ist nicht geladen.");
            return;
        }

        // Find a random location and send coordinates to the initiating player
        Location loc = findRandomLandLocation(hcfwWorld);
        player.sendMessage(ChatColor.YELLOW + "Das Event wurde bei den Koordinaten X: " + loc.getBlockX() + " Y: " + loc.getBlockY() + " Z: " + loc.getBlockZ() + " gestartet.");

        // Notify the player about the upcoming broadcast to all players
        player.sendMessage(ChatColor.GREEN + "In 30 Sekunden werden alle Online-Spieler über die Event-Koordinaten informiert.");

        // Ggfs. Discord Send Message hinzufügen


        // Spawn particles at the event location
        spawnParticles(loc);

        // Schedule the flying villager to spawn after 60 ticks (3 seconds)
        new BukkitRunnable() {
            @Override
            public void run() {
                spawnFlyingVillager(loc, player, version);

            }
        }.runTaskLater(plugin, 60);



        // Schedule the broadcast to all online players after 30 seconds (600 ticks)
        new BukkitRunnable() {
            @Override
            public void run() {
                // Broadcast the coordinates to all online players
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    onlinePlayer.sendMessage(ChatColor.GOLD + "[Event-Info] Ein Event ist gestartet bei den Koordinaten:");
                    onlinePlayer.sendMessage(ChatColor.YELLOW + "X: " + loc.getBlockX() + " Y: " + loc.getBlockY() + " Z: " + loc.getBlockZ());
                    onlinePlayer.sendMessage(ChatColor.DARK_RED + "Jeder Kill wird entlohnt!");
                }
            }
        }.runTaskLater(plugin, 600); // 30 seconds delay (600 ticks)
    }

    // Spawn Ghasts with high health that target the Villager
    private void spawnGhastWave(Location loc, Villager targetVillager, Player player) {
        World world = loc.getWorld();
        // Wenn Spieler an Location Gefunden
        // Prüfung auf Spieler an Location gefunden
        // Aktuell nur initialisierender Spieler

        new BukkitRunnable() {
            int ghastCount = 5; // Anzahl der Ghasts
            int spawnHeightOffset = 20; // Spawn-Offset für Höhe über dem Villager

            @Override
            public void run() {
                for (int i = 0; i < ghastCount; i++) {
                    // Erstelle den Ghast oberhalb des Villagers
                    Location spawnLocation = loc.clone().add(0, spawnHeightOffset, 0);
                    Ghast ghast = (Ghast) world.spawnEntity(spawnLocation, EntityType.GHAST);
                    ghast.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(80.0); // Mehr Leben für Ghasts
                    ghast.setHealth(80.0);
                    ghast.setCustomName("Event Ghast");
                    //ggfs. AI False?
                    //ghast.setAI(false);
                    //ghast.getTargetEntity(200, false);
                    //ghast.setCharging(true);

                    // Setze das Ziel auf den Villager und verhindere Spieler-Angriff
                    ghast.setPersistent(true); // Verhindert Despawn
                    ghast.setTarget(targetVillager); // Villager als primäres Ziel

                    // Nutze einen speziellen AI-Task, der sicherstellt, dass der Ghast den Villager anvisiert
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (ghast.isDead() || targetVillager.isDead()) {
                                cancel();
                                return;
                            }

                            // Überprüfe, ob das Ziel nicht der Villager ist und setze es zurück
                            if (ghast.getTarget() != targetVillager) {
                                ghast.setTarget(targetVillager);
                            }
                            // &&& Spieler ist Ziel von Ghast
                            if (ghast.getTarget() == player ){
                                // Ziel Manuell auf villager setzen
                                ghast.setTarget(targetVillager);
                            }

                            // Bewege den Ghast kontinuierlich in Richtung des Villagers
                            Vector direction = targetVillager.getLocation().toVector().subtract(ghast.getLocation().toVector()).normalize();
                            ghast.setVelocity(direction.multiply(0.2)); // Geschwindigkeit Richtung Villager
                        }
                    }.runTaskTimer(plugin, 0, 20); // Alle Sekunde erneut ausführen
                }
            }
        }.runTaskLater(plugin, 1800); // Verzögerung von 1 Minute 30 Sekunden
    }


    private Location findRandomLandLocation(World world) {
        Random random = new Random();
        Location loc;
        do {
            int x = random.nextInt(1000) - 500;
            int z = random.nextInt(1000) - 500;
            int y = world.getHighestBlockYAt(x, z);
            loc = new Location(world, x, y, z);
        } while (loc.getBlock().getType().isAir() || loc.getBlock().isLiquid());
        return loc;
    }

    private void spawnParticles(Location loc) {
        loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 100, 1, 1, 1, 0.1);
        loc.getWorld().playSound(loc, Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.0f);
    }

    private void spawnFlyingVillager(Location loc, Player player, int version) {
        World world = loc.getWorld();
        Villager villager = world.spawn(loc, Villager.class);
        villager.setCustomName("Event Villager");
        villager.setCustomNameVisible(true);
        villager.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(200.0);
        villager.setHealth(200.0);
        villager.setGlowing(true);
        villager.setAI(false);

        EntityEquipment equipment = villager.getEquipment();
        equipment.setChestplate(new ItemStack(Material.ELYTRA));
        equipment.setHelmet(new ItemStack(Material.DIAMOND_HELMET));

        spawnGhastWave(loc, villager, player);
        new BukkitRunnable() {
            int timeLeft = 180;

            @Override
            public void run() {
                if (timeLeft <= 0 || villager.isDead()) {
                    villager.remove();
                    eventInProgress = false;
                    saveEventStatus();
                    cancel();
                    return;
                }

                double radius = 5.0;
                double angle = (180 - timeLeft) * Math.PI / 90;
                double x = loc.getX() + radius * Math.cos(angle);
                double z = loc.getZ() + radius * Math.sin(angle);
                double y = world.getHighestBlockYAt((int) x, (int) z) + 1.0;
                Location newLoc = new Location(world, x, y, z);
                villager.teleport(newLoc);

                if (timeLeft % 2 == 0) { // Adjust the interval to every 2 seconds
                    for (int i = 0; i < 10; i++) { // Drop 10 items each time
                        dropRandomItem(villager.getLocation(), version);
                    }
                }

                Bukkit.getLogger().info("Villager moved to: " + newLoc.toString());

                timeLeft--;
            }
        }.runTaskTimer(plugin, 0, 20);

        BossBar bossBar = Bukkit.createBossBar(ChatColor.RED + "Event Villager", BarColor.RED, BarStyle.SOLID);
        bossBar.setProgress(1.0);
        bossBar.addPlayer(player);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (villager.isDead() || !villager.isValid()) {
                    bossBar.removeAll();
                    cancel();
                    return;
                }
                double healthPercent = villager.getHealth() / villager.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                bossBar.setProgress(healthPercent);
            }
        }.runTaskTimer(plugin, 0, 10);
    }

    private void dropRandomItem(Location loc, int version) {
        World world = loc.getWorld();
        ItemStack item;

        switch (new Random().nextInt(10)) {
            case 0:
                item = new ItemStack(Material.DIAMOND);
                break;
            case 1:
                item = OraxenItems.getItemById("gold").build();
                break;
            case 2:
                item = OraxenItems.getItemById("silber").build();
                break;
            case 3:
                item = OraxenItems.getItemById("eggmac").build();
                break;
            default:
                item = new ItemStack(Material.IRON_INGOT);
                if (version >= 2 && new Random().nextInt(100) < 30) {
                    item = OraxenItems.getItemById("silber").build();
                }
                if (version >= 3 && new Random().nextInt(100) < 50) {
                    item = OraxenItems.getItemById("gold").build();
                }
                if (version >= 4 && new Random().nextInt(100) < 70) {
                    item = OraxenItems.getItemById("eggmac").build();
                }
                break;
        }

        Item droppedItem = world.dropItem(loc, item);
        Vector velocity = new Vector(
                (new Random().nextDouble() - 0.5) * 2,
                1.0,
                (new Random().nextDouble() - 0.5) * 2
        );
        droppedItem.setVelocity(velocity);
        world.playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        world.spawnParticle(Particle.CRIT, loc, 10, 0.5, 0.5, 0.5, 0.1);

        Bukkit.getLogger().info("Item dropped at: " + loc.toString() + " Item: " + item.getType().toString());
    }

}




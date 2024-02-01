package h34r7l3s.freakyworld;

import io.th0rgal.oraxen.api.OraxenItems;
import io.th0rgal.oraxen.shaded.triumphteam.gui.builder.item.ItemBuilder;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.Vector;


import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import java.util.HashMap;
import java.util.UUID;
import java.util.Random;
import io.th0rgal.oraxen.api.OraxenItems;
import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;


public class HCFW implements Listener {

    private final FreakyWorld plugin;
    private final Set<UUID> playersInHCFW;
    private final Map<UUID, Long> playerDeathTimes;
    private Connection connection;

    public HCFW(FreakyWorld plugin) {
        this.plugin = plugin;
        this.playersInHCFW = new HashSet<>();
        this.playerDeathTimes = new HashMap<>();
        setupDatabase();
        loadPlayerDeathTimes();
        initializeZombieConfigurations();
        startZombieSpawnTimer();
        //startBlazeSpawnTimer(); // Für Events zurückhalten

        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        new BukkitRunnable() {
            @Override
            public void run() {
                checkPlayerLocks();
                checkAndTeleportPlayers();
            }
        }.runTaskTimer(plugin, 0L, 20L);// Alle 20 Ticks überprüfen



        new BukkitRunnable() {
            @Override
            public void run() {
                applyDarknessEffectToPlayers(); // Füge die Methode hier hinzu
                checkPlayerLocks();
                checkAndTeleportPlayers();
            }
        }.runTaskTimer(plugin, 0L, 20L); // Alle 100 Ticks überprüfen (5 Sekunden)
        new BukkitRunnable() {
            @Override
            public void run() {
                checkForExperienceBottles();
            }
        }.runTaskTimer(plugin, 0L, 20L * 10); // Überprüfe alle 60 Sekunden



    }

    public void cleanupEvents() {
        // Beenden aller laufenden Event-Runnables
        // Entfernen von Custom Entities oder Items, die für das Event erstellt wurden
        // Zurücksetzen von Event-bezogenen Zuständen

        // Beispiel: Wenn Sie einen schwebenden Rahmen für das Event verwenden
        if (eventItemFrame != null) {
            eventItemFrame.remove();
        }

        // ... Weitere Bereinigungslogik ...
    }

    private void setupDatabase() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            File dbFile = new File(dataFolder, "player_deaths.db");

            String jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            connection = DriverManager.getConnection(jdbcUrl);

            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS player_deaths (uuid TEXT PRIMARY KEY, death_time BIGINT)");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        plugin.getLogger().info("Datenbank erfolgreich initialisiert.");
    }

    private void loadPlayerDeathTimes() {
        try {
            PreparedStatement statement = connection.prepareStatement("SELECT * FROM player_deaths");
            ResultSet resultSet = statement.executeQuery();

            while (resultSet.next()) {
                UUID playerId = UUID.fromString(resultSet.getString("uuid"));
                Long deathTime = resultSet.getLong("death_time");
                playerDeathTimes.put(playerId, deathTime);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        plugin.getLogger().info("Spieler-Todeszeiten geladen.");
    }
    private void checkPlayerLocks() {
        long currentTimeMillis = System.currentTimeMillis();
        Set<UUID> playersToRemove = new HashSet<>();

        for (UUID playerId : playersInHCFW) {
            Long deathTime = playerDeathTimes.get(playerId);
            if (deathTime != null) {
                long timeSinceDeath = currentTimeMillis - deathTime;
                long lockDurationMillis = TimeUnit.MINUTES.toMillis(30);

                if (timeSinceDeath >= lockDurationMillis) {
                    playersToRemove.add(playerId);
                }
            }
        }

        // Verschieben Sie die Logik zum Entfernen von Spielern nach der Schleife, um ConcurrentModificationExceptions zu vermeiden
        for (UUID playerId : playersToRemove) {
            playersInHCFW.remove(playerId);
            playerDeathTimes.remove(playerId); // Stellen Sie sicher, dass der Todeszeitpunkt ebenfalls entfernt wird
        }
    }
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (isPlayerLocked(player)) {
            if (isInHCFW(player)) {
                blockPlayerAccess(player);
            } else {
                informPlayerOfWaitTime(player);
            }
        } else {
            welcomePlayer(player);
        }
    }

    private void informPlayerOfWaitTime(Player player) {
        long timeRemaining = TimeUnit.MINUTES.toMillis(30) - (System.currentTimeMillis() - playerDeathTimes.get(player.getUniqueId()));
        if (timeRemaining > 0) {
            player.sendMessage(ChatColor.RED + "Du kannst noch nicht in die HCFW gelangen. Bitte warte noch " +
                    TimeUnit.MILLISECONDS.toMinutes(timeRemaining) + " Minuten.");
        }
    }

    @EventHandler
    public void onPlayerDeathInHCFW(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (isInHCFW(player)) {
            UUID playerId = player.getUniqueId();
            playerDeathTimes.put(playerId, System.currentTimeMillis());
            savePlayerDeathInfo(playerId);
            // Löschen Sie alle Items, die der Spieler fallen lässt
            event.getDrops().clear();

            player.sendMessage(ChatColor.RED + "Du kannst nicht erneut in die HCFW gelangen, nachdem du gestorben bist.");
        }
    }

    @EventHandler
    public void onPlayerRespawnInHCFW(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (isInHCFW(player)) {
            event.setRespawnLocation(plugin.getServer().getWorld("world").getSpawnLocation());
        }
    }

    private enum LockStatus {
        LOCKED, UNLOCKED, NOT_LOCKED
    }

    private LockStatus getPlayerLockStatus(Player player) {
        Long deathTime = playerDeathTimes.get(player.getUniqueId());
        if (deathTime == null) {
            return LockStatus.NOT_LOCKED;
        }
        long timeSinceDeath = System.currentTimeMillis() - deathTime;
        if (timeSinceDeath < TimeUnit.MINUTES.toMillis(30)) {
            return LockStatus.LOCKED;
        } else {
            return LockStatus.UNLOCKED;
        }
    }

    private void checkAndTeleportPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isInHCFW(player)) {
                LockStatus status = getPlayerLockStatus(player);
                if (status == LockStatus.LOCKED) {
                    blockPlayerAccess(player);
                } else if (status == LockStatus.UNLOCKED) {
                    playersInHCFW.add(player.getUniqueId()); // Spieler zur HCFW-Liste hinzufügen
                    welcomePlayer(player); // Willkommen heißen, wenn gerade entsperrt
                }
            }
        }
    }


    private boolean isPlayerLocked(Player player) {
        Long deathTime = playerDeathTimes.get(player.getUniqueId());
        if (deathTime == null) {
            return false;
        }
        long timeSinceDeath = System.currentTimeMillis() - deathTime;
        return timeSinceDeath < TimeUnit.MINUTES.toMillis(30);
    }

    private void blockPlayerAccess(Player player) {
        player.teleport(plugin.getServer().getWorld("world").getSpawnLocation());
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0F, 1.0F);
        long timeRemaining = TimeUnit.MINUTES.toMillis(30) - (System.currentTimeMillis() - playerDeathTimes.get(player.getUniqueId()));
        player.sendMessage(ChatColor.RED + "Du kannst noch nicht in die HCFW gelangen. Bitte warte noch " +
                TimeUnit.MILLISECONDS.toMinutes(timeRemaining) + " Minuten.");
    }

    private void welcomePlayer(Player player) {
        if (!isInHCFW(player)) return;
        int eventProbability = plugin.getDiscordBot().getEventProbability();
        if (eventProbability >= 1) {
            player.sendTitle(ChatColor.GREEN + "Ein besonderes Event tritt ein!",
                    ChatColor.WHITE + "Event-Wahrscheinlichkeit: " + eventProbability + "%", 10, 70, 20);
            player.playSound(player.getLocation(), Sound.ENTITY_GHAST_SCREAM, 1.0F, 1.0F);
        } else {
            player.sendTitle(ChatColor.GREEN + "Willkommen in der HCFW!", "", 10, 70, 20);
        }
    }

    private void savePlayerDeathInfo(UUID playerId) {
        long timestamp = System.currentTimeMillis();
        try {
            PreparedStatement checkStmt = connection.prepareStatement("SELECT COUNT(*) FROM player_deaths WHERE uuid = ?");
            checkStmt.setString(1, playerId.toString());
            ResultSet rs = checkStmt.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) {
                PreparedStatement updateStmt = connection.prepareStatement("UPDATE player_deaths SET death_time = ? WHERE uuid = ?");
                updateStmt.setLong(1, timestamp);
                updateStmt.setString(2, playerId.toString());
                updateStmt.executeUpdate();
            } else {
                PreparedStatement insertStmt = connection.prepareStatement("INSERT INTO player_deaths (uuid, death_time) VALUES (?, ?)");
                insertStmt.setString(1, playerId.toString());
                insertStmt.setLong(2, timestamp);
                insertStmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private boolean isInHCFW(Player player) {
        boolean inHCFW = player.getWorld().getName().equalsIgnoreCase("hcfw");
        if (!inHCFW) {
            //plugin.getLogger().info("Spieler ist nicht in der Welt 'hcfw'.");
        }
        return inHCFW;
    }

    //weitere Hardocre Elemente

    //skelette
    @EventHandler
    public void onSkeletonShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Skeleton)) return;

        Skeleton skeleton = (Skeleton) event.getEntity();
        if (!skeleton.getWorld().getName().equalsIgnoreCase("hcfw")) return;

        int difficultyLevel = plugin.getDiscordBot().getEventProbability();
        Random random = new Random();
        Arrow originalArrow = (Arrow) event.getProjectile();

        // Eine einzige Zufallszahl für die gesamte Logik
        int randomChance = random.nextInt(100);

        // Wahrscheinlichkeiten für verschiedene Pfeiltypen festlegen
        // Test
        int lightningArrowProbability = 40; // 40% Chance für Thors Pfeil

        // Zuerst prüfen, ob es ein Blitzpfeil sein soll
        if (randomChance < lightningArrowProbability) {
            originalArrow.setMetadata("LightningArrow", new FixedMetadataValue(plugin, true));
        }
        // Dann prüfen, ob es ein Trankpfeil sein soll, falls es kein Blitzpfeil ist
        else if (randomChance < difficultyLevel) {
            createTippedArrow(originalArrow, difficultyLevel);
        }
        // Wenn keine der Bedingungen erfüllt ist, bleibt es ein normaler Pfeil
    }
    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow)) return;
        Arrow arrow = (Arrow) event.getEntity();

        if (!arrow.getWorld().getName().equalsIgnoreCase("hcfw")) return;

        Location hitLocation = arrow.getLocation();
        Block block = hitLocation.getBlock();

        if (block.getType() != Material.AIR) {
            arrow.getWorld().strikeLightning(hitLocation);

            // Verzögertes Entfernen des Pfeils
            new BukkitRunnable() {
                @Override
                public void run() {
                    arrow.remove();
                }
            }.runTaskLater(plugin, 1L); // 1 Tick später
        }
    }





    private void createTippedArrow(Arrow originalArrow, int difficultyLevel) {
        Location arrowLocation = originalArrow.getLocation();
        Vector arrowVelocity = originalArrow.getVelocity();
        ProjectileSource shooter = originalArrow.getShooter();

        // Entfernen Sie den ursprünglichen Pfeil
        originalArrow.remove();

        // Erstellen eines neuen Trankpfeils
        Arrow tippedArrow = arrowLocation.getWorld().spawnArrow(arrowLocation, arrowVelocity, 1.0f, 12.0f, TippedArrow.class);

        // Setzen Sie den Shooter, wenn es eine Entität ist
        if (shooter instanceof ProjectileSource) {
            tippedArrow.setShooter((ProjectileSource)shooter);
        }

        // Setzen Sie Trankdaten basierend auf der Schwierigkeit
        if (tippedArrow instanceof TippedArrow) {
            TippedArrow ta = (TippedArrow) tippedArrow;
            if (difficultyLevel >= 80) {
                ta.setBasePotionData(new PotionData(PotionType.INSTANT_DAMAGE, true, true));
            } else {
                ta.setBasePotionData(new PotionData(PotionType.INSTANT_DAMAGE));
            }
        }
    }

///Blazes
// Methode zum Starten des Blaze-Spawn-Timers
public void startBlazeSpawnTimer() {
    new BukkitRunnable() {
        @Override
        public void run() {
            World world = plugin.getServer().getWorld("hcfw");
            if (world == null || world.getPlayers().isEmpty()) {
                removeAllBlazes(world); // Entferne alle Blazes, wenn keine Spieler da sind
                return;
            }
            spawnBlazesBasedOnProbability();
        }
    }.runTaskTimer(plugin, 0L, 440L); // 80 Ticks = 4 Sekunden
}

    // Methode zum Spawnen von Blazes basierend auf der Event-Wahrscheinlichkeit
    private Location ritualLocation;
    private void spawnBlazesBasedOnProbability() {
        World world = plugin.getServer().getWorld("hcfw");
        if (world == null || world.getPlayers().isEmpty()) {
            removeAllBlazes(world); // Entferne alle Blazes, wenn keine Spieler da sind
            return;
        }

        int eventProbability = plugin.getDiscordBot().getEventProbability();
        int maxBlazesToSpawn = calculateMaxBlazes(eventProbability, world.getPlayers().size());
        List<Player> playersInWorld = world.getPlayers();

        for (Player player : playersInWorld) {
            int blazesPerPlayer = maxBlazesToSpawn / playersInWorld.size();
            for (int i = 0; i < blazesPerPlayer; i++) {
                if (countCurrentBlazes(world) >= maxBlazesToSpawn) {
                    break;
                }

                Location playerLocation = player.getLocation();
                int x = playerLocation.getBlockX() + (new Random().nextInt(61) - 30); // Bereich von -30 bis +30 um den Spieler
                int z = playerLocation.getBlockZ() + (new Random().nextInt(61) - 30); // Bereich von -30 bis +30 um den Spieler
                int y = world.getHighestBlockYAt(x, z) + 10; // Höhere Y-Position für Blazes

                Location spawnLocation = new Location(world, x, y, z);
                Blaze blaze = (Blaze) world.spawnEntity(spawnLocation, EntityType.BLAZE);


                // Konfiguration der Blaze
                setupBasicBlazeAttributes(blaze, eventProbability);
                enhanceBlazeAttributes(blaze, eventProbability);
                giveBlazeSpecialAbilities(blaze, eventProbability);
                applyVisualEffects(blaze, eventProbability);
            }
        }
    }
    // Methode zur Berechnung der maximalen Anzahl an Blazes basierend auf der Event-Wahrscheinlichkeit
    private int calculateMaxBlazes(int eventProbability, int playerCount) {
        int baseMax = eventProbability * 2; // Basiswert für Maximalanzahl
        int maxPerPlayer = 10; // Maximale Anzahl von Blazes pro Spieler
        int totalMax = Math.min(baseMax, playerCount * maxPerPlayer);
        return Math.min(totalMax, 1000); // Maximalgrenze festlegen
    }

    private void removeAllBlazes(World world) {
        for (Entity entity : world.getEntitiesByClass(Blaze.class)) {
            entity.remove();
        }
    }

    private int countCurrentBlazes(World world) {
        int count = 0;
        for (Entity entity : world.getEntitiesByClass(Blaze.class)) {
            count++;
        }
        return count;
    }
    private void setupBasicBlazeAttributes(Blaze blaze, int eventProbability) {
        // Grundlegende Blaze-Attribute setzen, z.B. maximale Gesundheit
        blaze.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(20.0);
        blaze.setHealth(20.0);

        // Weitere Attribute und Eigenschaften festlegen, je nach Bedarf
    }

    private void enhanceBlazeAttributes(Blaze blaze, int eventProbability) {
        // Blaze-Attribute basierend auf der Event-Wahrscheinlichkeit verbessern, z.B. Schaden erhöhen
        double damageMultiplier = 1.0 + (eventProbability * 0.1); // Beispiel: Je höher die Wahrscheinlichkeit, desto mehr Schaden
        blaze.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(damageMultiplier);

        // Weitere Verbesserungen je nach Bedarf
    }

    private void giveBlazeSpecialAbilities(Blaze blaze, int eventProbability) {
        // Besondere Fähigkeiten oder Effekte für Blazes hinzufügen, abhängig von der Event-Wahrscheinlichkeit
        if (eventProbability >= 50) {
            blaze.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0));
        }

        // Weitere spezielle Fähigkeiten hinzufügen, je nach Bedarf
    }

    private void applyVisualEffects(Blaze blaze, int eventProbability) {
        // Visual Effects für Blazes basierend auf der Event-Wahrscheinlichkeit anwenden
        // Hier können Sie optische Effekte, Partikel oder andere visuelle Anpassungen hinzufügen.
    }



    //Zombie
    // Methode zum Starten des Zombie-Spawn-Timers
    public void startZombieSpawnTimer() {
        new BukkitRunnable() {
            @Override
            public void run() {
                World world = plugin.getServer().getWorld("hcfw");
                if (world == null || world.getPlayers().isEmpty()) {
                    removeAllZombies(world); // Entferne alle Zombies, wenn keine Spieler da sind
                    return;
                }

                modifyExistingZombies(world);
                spawnZombiesBasedOnProbability();
                spawnSpiderJockeysBasedOnProbability();
                spawnGuardiansBasedOnProbability();
            }
        }.runTaskTimer(plugin, 0L, 320L); // 320 Ticks = 16 Sekunden
    }
    private void modifyExistingZombies(World world) {
        // Überprüfen Sie, ob die Welt die richtige ist
        if (world.getName().equals("HCFW")) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Zombie) {
                    Zombie zombie = (Zombie) entity;
                    if (!isCustomZombie(zombie)) {
                        int eventProbability = plugin.getDiscordBot().getEventProbability();
                        setupBasicZombieAttributes(zombie, eventProbability);
                        enhanceZombieAttributes(zombie, eventProbability);
                        applyVisualEffects(zombie, eventProbability);
                    }
                }
            }
        }
    }

    private boolean isCustomZombie(Zombie zombie) {
        ItemStack leggings = zombie.getEquipment().getLeggings();

        // Überprüfe, ob die Leggings eines der spezifischen Materialien sind, die in setupBasicZombieAttributes verwendet werden
        if (leggings != null) {
            Material leggingsMaterial = leggings.getType();
            return leggingsMaterial == Material.LEATHER_LEGGINGS ||
                    leggingsMaterial == Material.IRON_LEGGINGS ||
                    leggingsMaterial == Material.DIAMOND_LEGGINGS ||
                    leggingsMaterial == Material.CHAINMAIL_LEGGINGS ||
                    leggingsMaterial == Material.GOLDEN_LEGGINGS ||
                    leggingsMaterial == Material.NETHERITE_LEGGINGS;
        }

        return false;
    }
    public void spawnSpiderJockeysBasedOnProbability() {
        World world = plugin.getServer().getWorld("HCFW");
        if (world == null || world.getPlayers().isEmpty()) {
            removeAllSpiderJockeys(world);
            return; // Nichts zu tun, wenn keine Spieler oder die Welt nicht vorhanden ist
        }

        int eventProbability = plugin.getDiscordBot().getEventProbability();
        int maxSpiderJockeysToSpawn = calculateMaxSpiderJockeys(eventProbability, world.getPlayers().size());

        for (int i = 0; i < maxSpiderJockeysToSpawn; i++) {
            Player randomPlayer = world.getPlayers().get(new Random().nextInt(world.getPlayers().size()));
            Location spawnLocation = calculateSpawnLocationNearPlayer(randomPlayer);

            if (spawnLocation != null) {
                Spider spider = (Spider) world.spawnEntity(spawnLocation, EntityType.SPIDER);
                Skeleton skeleton = (Skeleton) world.spawnEntity(spawnLocation, EntityType.SKELETON);

                // Setze die Kettenrüstung auf das Skelett
                ItemStack chainmailHelmet = new ItemStack(Material.CHAINMAIL_HELMET);
                ItemStack chainmailChestplate = new ItemStack(Material.CHAINMAIL_CHESTPLATE);
                ItemStack chainmailLeggings = new ItemStack(Material.CHAINMAIL_LEGGINGS);
                ItemStack chainmailBoots = new ItemStack(Material.CHAINMAIL_BOOTS);

                skeleton.getEquipment().setHelmet(chainmailHelmet);
                skeleton.getEquipment().setChestplate(chainmailChestplate);
                skeleton.getEquipment().setLeggings(chainmailLeggings);
                skeleton.getEquipment().setBoots(chainmailBoots);

                spider.setPassenger(skeleton);
            }
        }
    }


    private int calculateMaxSpiderJockeys(int eventProbability, int playerCount) {
        int baseMax = eventProbability * 3; // Beispiel: 3 Spinnenreiter pro Wahrscheinlichkeitseinheit
        int maxPerPlayer = 3;// Maximal 5 Spinnenreiter pro Spieler
        int totalMax = Math.min(baseMax, playerCount * maxPerPlayer);
        return Math.min(totalMax, 100); // Begrenzung auf maximal 100 Spinnenreiter insgesamt
    }

    private Location calculateSpawnLocationNearPlayer(Player player) {
        Location playerLocation = player.getLocation();
        Random random = new Random();
        int x = playerLocation.getBlockX() + (random.nextInt(101) - 50); // Bereich von -50 bis +50 um den Spieler
        int z = playerLocation.getBlockZ() + (random.nextInt(101) - 50); // Bereich von -50 bis +50 um den Spieler
        int y = playerLocation.getWorld().getHighestBlockYAt(x, z);
        return new Location(playerLocation.getWorld(), x, y, z);
    }

    private void removeAllSpiderJockeys(World world) {
        for (Entity entity : world.getEntities()) {
            if (entity instanceof Spider) {
                Spider spider = (Spider) entity;
                if (spider.getPassenger() instanceof Skeleton) {
                    Skeleton skeleton = (Skeleton) spider.getPassenger();
                    // Entferne den Spider Jockey (Spinnenreiter)
                    spider.remove();
                    skeleton.remove();
                }
            }
        }
    }
    public void spawnGuardiansBasedOnProbability() {
        World world = plugin.getServer().getWorld("HCFW");
        if (world == null || world.getPlayers().isEmpty()) {
            removeAllGuardians(world);
            return; // Nichts zu tun, wenn keine Spieler oder die Welt nicht vorhanden ist
        }

        int eventProbability = plugin.getDiscordBot().getEventProbability();
        int maxGuardiansToSpawn = calculateMaxGuardians(eventProbability, world.getPlayers().size());

        for (int i = 0; i < maxGuardiansToSpawn; i++) {
            Player randomPlayer = world.getPlayers().get(new Random().nextInt(world.getPlayers().size()));
            Location spawnLocation = calculateSpawnLocationInWater(randomPlayer);

            if (spawnLocation != null) {
                Guardian guardian = (Guardian) world.spawnEntity(spawnLocation, EntityType.GUARDIAN);

                // Hier kannst du weitere Einstellungen für die Guardians vornehmen, z.B. Ausrüstung setzen oder andere Eigenschaften anpassen.
            }
        }
    }

    private int calculateMaxGuardians(int eventProbability, int playerCount) {
        int baseMax = eventProbability * 3; // Beispiel: 3 Guardians pro Wahrscheinlichkeitseinheit
        int maxPerPlayer = 3; // Maximal 3 Guardians pro Spieler
        int totalMax = Math.min(baseMax, playerCount * maxPerPlayer);
        return Math.min(totalMax, 40); // Begrenzung auf maximal 100 Guardians insgesamt
    }

    private Location calculateSpawnLocationInWater(Player player) {
        Location playerLocation = player.getLocation();
        World world = player.getWorld();
        Random random = new Random();

        // Finde eine zufällige Position im Wasser in der Nähe des Spielers
        int maxAttempts = 100; // Maximale Versuche, eine gültige Position zu finden
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int x = playerLocation.getBlockX() + (random.nextInt(101) - 50); // Bereich von -50 bis +50 um den Spieler
            int z = playerLocation.getBlockZ() + (random.nextInt(101) - 50); // Bereich von -50 bis +50 um den Spieler
            int y = playerLocation.getWorld().getHighestBlockYAt(x, z);

            Location potentialSpawnLocation = new Location(world, x, y, z);
            if (potentialSpawnLocation.getBlock().getType() == Material.WATER) {
                return potentialSpawnLocation; // Gültige Position im Wasser gefunden
            }
        }

        return null; // Keine gültige Position im Wasser gefunden
    }

    private void removeAllGuardians(World world) {
        for (Entity entity : world.getEntities()) {
            if (entity instanceof Guardian) {
                entity.remove();
            }
        }
    }



    // Methode zum Spawnen von Zombies basierend auf der Event-Wahrscheinlichkeit
    private void spawnZombiesBasedOnProbability() {
        World world = plugin.getServer().getWorld("hcfw");
        if (world == null || world.getPlayers().isEmpty()) {
            removeAllZombies(world); // Entferne alle Zombies, wenn keine Spieler da sind
            return;
        }

        int eventProbability = plugin.getDiscordBot().getEventProbability();
        int maxZombiesToSpawn = calculateMaxZombies(eventProbability, world.getPlayers().size());
        List<Player> playersInWorld = world.getPlayers();

        for (Player player : playersInWorld) {
            int zombiesPerPlayer = maxZombiesToSpawn / playersInWorld.size();
            for (int i = 0; i < zombiesPerPlayer; i++) {
                if (countCurrentZombies(world) >= maxZombiesToSpawn) {
                    break;
                }

                Location playerLocation = player.getLocation();
                int x = playerLocation.getBlockX() + (new Random().nextInt(61) - 30); // Bereich von -30 bis +30 um den Spieler
                int z = playerLocation.getBlockZ() + (new Random().nextInt(61) - 30); // Bereich von -30 bis +30 um den Spieler
                int y = world.getHighestBlockYAt(x, z);
                Location spawnLocation = new Location(world, x, y, z);

                // Zombie an der berechneten Position spawnen
                Zombie zombie = (Zombie) world.spawnEntity(spawnLocation, EntityType.ZOMBIE);

                // Konfiguration des Zombies
                setupBasicZombieAttributes(zombie, eventProbability);
                enhanceZombieAttributes(zombie, eventProbability);
                applyVisualEffects(zombie, eventProbability);

                // Setze benutzerdefiniertes Metadatum, um die Rüstung zu speichern
                zombie.getPersistentDataContainer().set(new NamespacedKey(plugin, "keepArmor"), PersistentDataType.BYTE, (byte) 1);
                zombie.setCanPickupItems(false);

                // Sonderfall für Zombies im Wasser
                if (spawnLocation.getBlock().getType() == Material.WATER) {
                    zombie.getEquipment().setItemInMainHand(new ItemStack(Material.TRIDENT));
                }
            }
        }
    }
    // Beispielmethoden
    private int calculateMaxZombies(int eventProbability, int playerCount) {
        int baseMax = eventProbability * 5; // Reduziere die Anzahl basierend auf der Event-Wahrscheinlichkeit
        int maxPerPlayer = 55; // Maximale Anzahl von Zombies pro Spieler
        int totalMax = Math.min(baseMax, playerCount * maxPerPlayer);
        return Math.min(totalMax, 1220);
    }

    private void removeAllZombies(World world) {
        for (Entity entity : world.getEntities()) {
            if (entity instanceof Zombie) {
                entity.remove(); // Entferne den Zombie
            }
        }
    }

    private int countCurrentZombies(World world) {
        // Zähle die aktuellen lebenden Zombies in der Welt "HCFW"
        int count = 0;
        for (Entity entity : world.getEntities()) {
            if (entity instanceof Zombie && entity.getWorld().getName().equals("HCFW")) {
                boolean isAlive = checkIfZombieIsAlive(entity); // Implementiere diese Methode entsprechend deiner Logik

                if (isAlive) {
                    count++;
                }
            }
        }

        return count;
    }


    private boolean checkIfZombieIsAlive(Entity zombie) {
        if (zombie instanceof LivingEntity) {
            LivingEntity livingZombie = (LivingEntity) zombie;
            return !livingZombie.isDead();
        }
        return false;
    }

    private void setupBasicZombieAttributes(Zombie zombie, int difficultyLevel) {
        ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
        //ItemStack chestplate = new ItemStack(Material.LEATHER_CHESTPLATE);
        ItemStack leggings = new ItemStack(Material.LEATHER_LEGGINGS);
        ItemStack boots = new ItemStack(Material.LEATHER_BOOTS);
        //ItemStack sword = new ItemStack(Material.STONE_SWORD);

        // Je nach Schwierigkeitsgrad unterschiedliche Ausrüstung
        if (difficultyLevel >= 30 && difficultyLevel < 60) {
            helmet = new ItemStack(Material.IRON_HELMET);
            //chestplate = new ItemStack(Material.IRON_CHESTPLATE);
            leggings = new ItemStack(Material.IRON_LEGGINGS);
            boots = new ItemStack(Material.IRON_BOOTS);
            //sword = new ItemStack(Material.IRON_SWORD);
        } else if (difficultyLevel >= 60 && difficultyLevel < 70) {
            helmet = new ItemStack(Material.DIAMOND_HELMET);
            //chestplate = new ItemStack(Material.DIAMOND_CHESTPLATE);
            leggings = new ItemStack(Material.DIAMOND_LEGGINGS);
            boots = new ItemStack(Material.DIAMOND_BOOTS);
            //sword = new ItemStack(Material.DIAMOND_SWORD);
        } else if (difficultyLevel >= 70 && difficultyLevel < 80) {
            // Hier können Sie die Rüstung nach Ihren Wünschen hinzufügen (z. B. Kettenrüstung, Goldrüstung)
            // und die Ausrüstung entsprechend anpassen.
            helmet = new ItemStack(Material.CHAINMAIL_HELMET);
            //chestplate = new ItemStack(Material.CHAINMAIL_CHESTPLATE);
            leggings = new ItemStack(Material.CHAINMAIL_LEGGINGS);
            boots = new ItemStack(Material.CHAINMAIL_BOOTS);
            //sword = new ItemStack(Material.STONE_SWORD);
        } else if (difficultyLevel >= 80 && difficultyLevel < 97) {
            // Hier können Sie weitere Ausrüstungsvarianten für höhere Schwierigkeitsgrade hinzufügen.
            helmet = new ItemStack(Material.GOLDEN_HELMET);
            //chestplate = new ItemStack(Material.GOLDEN_CHESTPLATE);
            leggings = new ItemStack(Material.GOLDEN_LEGGINGS);
            boots = new ItemStack(Material.GOLDEN_BOOTS);
            //sword = new ItemStack(Material.GOLDEN_SWORD);
        } else if (difficultyLevel >= 97) {
            // Hier können Sie Netherite-Rüstung oder andere besondere Ausrüstung hinzufügen.
            helmet = new ItemStack(Material.NETHERITE_HELMET);
            //chestplate = new ItemStack(Material.NETHERITE_CHESTPLATE);
            leggings = new ItemStack(Material.NETHERITE_LEGGINGS);
            boots = new ItemStack(Material.NETHERITE_BOOTS);
            //sword = new ItemStack(Material.NETHERITE_SWORD);
        }


        zombie.getEquipment().setHelmet(helmet);
        //zombie.getEquipment().setChestplate(chestplate);
        zombie.getEquipment().setLeggings(leggings);
        zombie.getEquipment().setBoots(boots);
        //zombie.getEquipment().setItemInMainHand(sword);

        double maxHealth = 20.0;

        if (difficultyLevel >= 0 && difficultyLevel < 10) {
            maxHealth *= 1.1;
        } else if (difficultyLevel >= 10 && difficultyLevel < 20) {
            maxHealth *= 1.2;
        } else if (difficultyLevel >= 20 && difficultyLevel < 30) {
            maxHealth *= 1.3;
        } else if (difficultyLevel >= 30 && difficultyLevel < 40) {
            maxHealth *= 1.4;
        } else if (difficultyLevel >= 40 && difficultyLevel < 50) {
            maxHealth *= 1.5;
        } else if (difficultyLevel >= 50 && difficultyLevel < 60) {
            maxHealth *= 1.6;
        } else if (difficultyLevel >= 60 && difficultyLevel < 70) {
            maxHealth *= 1.7;
        } else if (difficultyLevel >= 70 && difficultyLevel < 80) {
            maxHealth *= 1.8;
        } else if (difficultyLevel >= 80 && difficultyLevel < 90) {
            maxHealth *= 1.9;
        } else if (difficultyLevel >= 90 && difficultyLevel < 94) {
            maxHealth *= 2.3;
        } else if (difficultyLevel >= 94 && difficultyLevel < 97) {
            maxHealth *= 2.5;
        } else if (difficultyLevel >= 97 && difficultyLevel < 99) {
            maxHealth *= 2.8;
        }



        zombie.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(maxHealth);
        zombie.setHealth(maxHealth);
    }

    private void enhanceZombieAttributes(Zombie zombie, int difficultyLevel) {
        int maxStrength = 4; // Maximal erlaubte Stärke
        int maxSpeed = 2; // Maximal erlaubte Geschwindigkeit
        int maxRegeneration = 1; // Maximal erlaubte Regeneration

        if (difficultyLevel >= 30 && difficultyLevel < 40) {
            zombie.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, Integer.MAX_VALUE, Math.min(maxStrength, 0)));
        } else if (difficultyLevel >= 40 && difficultyLevel < 60) {
            zombie.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, Integer.MAX_VALUE, Math.min(maxStrength, 1)));
        } else if (difficultyLevel >= 60 && difficultyLevel < 80) {
            zombie.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, Integer.MAX_VALUE, Math.min(maxStrength, 2)));
            zombie.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, Math.min(maxSpeed, 0)));
        } else if (difficultyLevel >= 80 && difficultyLevel < 90) {
            zombie.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, Integer.MAX_VALUE, Math.min(maxStrength, 3)));
            zombie.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, Math.min(maxSpeed, 1)));
            zombie.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, Math.min(maxRegeneration, 0)));
        } else if (difficultyLevel >= 90) {
            zombie.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, Integer.MAX_VALUE, Math.min(maxStrength, 4)));
            zombie.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, Math.min(maxSpeed, 2)));
            zombie.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, Math.min(maxRegeneration, 1)));
        }
    }

    private final Map<UUID, Long> zombieCooldowns = new HashMap<>();
    private int baseCooldownDuration = 10 * 1000; // 10 Sekunden in Millisekunden

    private void teleportZombiesToRandomPlayer(Zombie zombie) {
        World hcfwWorld = plugin.getServer().getWorld("hcfw");

        if (hcfwWorld == null || hcfwWorld.getPlayers().isEmpty()) {
            return; // Keine Spieler in 'hcfw' oder Welt existiert nicht.
        }

        int eventProbability = plugin.getDiscordBot().getEventProbability();
        int cooldownDuration;

        if (eventProbability >= 60) {
            cooldownDuration = 10 * 1000; // 10 Sekunden Cooldown bei 60% Wahrscheinlichkeit
        } else if (eventProbability >= 80) {
            cooldownDuration = 6 * 1000; // 6 Sekunden Cooldown bei 80% Wahrscheinlichkeit
        } else {
            cooldownDuration = baseCooldownDuration; // Standard-Cooldown-Dauer
        }

        UUID zombieUUID = zombie.getUniqueId();

        if (!zombieCooldowns.containsKey(zombieUUID)) {
            // Hier fügen wir eine Zufallsentscheidung ein
            boolean shouldTeleport = Math.random() < 0.3; // 30% Chance, dass der Zombie teleportiert wird

            if (shouldTeleport) {
                Player randomPlayer = getRandomPlayerInHCFW(hcfwWorld, zombie, 15.0); // Beispiel: Suchradius von 10 Blöcken


                if (randomPlayer != null) {
                    // Teleportiere den Zombie zum zufälligen Spieler
                    zombie.teleport(randomPlayer.getLocation());

                    // Füge Slowness-Effekt hinzu
                    zombie.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 20 * 30, 10));

                    // Setze den Cooldown für den aktuellen Zombie
                    zombieCooldowns.put(zombieUUID, System.currentTimeMillis() + cooldownDuration);
                }
            }
        }
    }

    private Player getRandomPlayerInHCFW(World hcfwWorld, Zombie zombie, double maxDistance) {
        List<Player> playersInHCFW = hcfwWorld.getPlayers();

        if (playersInHCFW.isEmpty()) {
            return null;
        }

        Location zombieLocation = zombie.getLocation();
        List<Player> playersInRange = new ArrayList<>();

        for (Player player : playersInHCFW) {
            if (player.getLocation().distance(zombieLocation) <= maxDistance) {
                playersInRange.add(player);
            }
        }

        if (playersInRange.isEmpty()) {
            return null;
        }

        Random random = new Random();
        int randomIndex = random.nextInt(playersInRange.size());
        return playersInRange.get(randomIndex);
    }

    private void giveZombieSpecialAbilities(Zombie zombie, int difficultyLevel) {
        int eventProbability = plugin.getDiscordBot().getEventProbability();

        if (eventProbability >= 60) {
            // Hier kannst du die speziellen Fähigkeiten für den Zombie hinzufügen
            // Zum Beispiel: zombie.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));

            // Rufe die Methode teleportZombiesToRandomPlayer auf, wenn die Wahrscheinlichkeit >= 60 ist
            teleportZombiesToRandomPlayer(zombie);
        } else if (eventProbability >= 70) {
            // Hier kannst du zusätzliche spezielle Fähigkeiten für den Zombie hinzufügen
            // Zum Beispiel: zombie.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, Integer.MAX_VALUE, 1));

            // Rufe die Methode teleportZombiesToRandomPlayer auf, wenn die Wahrscheinlichkeit >= 70 ist
            teleportZombiesToRandomPlayer(zombie);
        }
    }



    private void applyVisualEffects(Zombie zombie, int difficultyLevel) {
        // Visuelle Effekte basierend auf Schwierigkeitsgrad
        if (difficultyLevel >= 90) {
            zombie.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0));
        }
    }




    private Location eventLocation;
    public boolean isEventInitialized = false; // Dieser Wert zeigt an, ob das Event abgeschlossen wurde
    public boolean isEventCompleted = false;


    public boolean isEventActive = false;

    private ItemFrame eventItemFrame;

    public void initializeEvent() {
        World world = plugin.getServer().getWorld("hcfw");
        plugin.getLogger().info("Event initialized erreicht");
        int maxAttempts = 500; // Maximale Anzahl von Versuchen, einen geeigneten Ort zu finden
        int attempt = 0;

        while (attempt < maxAttempts) {
            // Zufällige Koordinaten in der Welt "hcfw" generieren
            int x = new Random().nextInt(world.getMaxHeight());
            int z = new Random().nextInt(world.getMaxHeight());
            int y = world.getHighestBlockYAt(x, z);

            // Überprüfen, ob die Position in einem ungünstigen Biome ist oder in der Nähe von Bäumen oder Wasser liegt
            Biome biome = world.getBiome(x, y, z);
            if (biome == Biome.JUNGLE || biome == Biome.RIVER || biome == Biome.OCEAN || biome == Biome.DEEP_OCEAN) {
                attempt++;
                continue;
            }

            boolean unsuitableLocation = false;
            int checkRadius = 5; // Radius, in dem nach Baumblöcken oder Wasser gesucht wird

            for (int checkX = x - checkRadius; checkX <= x + checkRadius; checkX++) {
                for (int checkZ = z - checkRadius; checkZ <= z + checkRadius; checkZ++) {
                    for (int checkY = y - 5; checkY <= y + 5; checkY++) {
                        Material blockType = world.getBlockAt(checkX, checkY, checkZ).getType();
                        if (isWood(blockType) || blockType == Material.WATER) {
                            unsuitableLocation = true;
                            break;
                        }
                    }
                    if (unsuitableLocation) {
                        break;
                    }
                }
                if (unsuitableLocation) {
                    break;
                }
            }

            if (unsuitableLocation) {
                attempt++;
                continue;
            }

            // Geeignete Position gefunden, Event initialisieren
            eventLocation = new Location(world, x, y + 2, z); // Etwas oberhalb des Bodens positionieren
            eventItemFrame = (ItemFrame) world.spawnEntity(eventLocation, EntityType.ITEM_FRAME);
            eventItemFrame.setVisible(true);
            eventItemFrame.setInvulnerable(true);
            eventItemFrame.setFixed(true);
            eventItemFrame.setItem(new ItemStack(Material.DIAMOND)); // Zeigt einen Diamanten als Hinweis
            isEventActive = false; // Das Event wird noch nicht aktiviert
            isEventInitialized = true;
            isEventCompleted = false;
            plugin.getLogger().info("Event initialized: isEventActive=" + isEventActive + ", isEventInitialized=" + isEventInitialized + ", isEventCompleted=" + isEventCompleted);

            // Partikeleffekt für inaktives Event (Rot)
            world.spawnParticle(Particle.REDSTONE, eventLocation.add(0.5, 2.0, 0.5), 10, 1.0, 1.0, 1.0, new Particle.DustOptions(Color.RED, 1));

            break; // Geeignete Position gefunden, Schleife beenden
        }
    }





//Freaky Raids
    // Neue Funktionen ab hier, die das Event Raid System betreffen

    private int probabilityBooster = 0;

    public void checkAndStartEventBasedOnProbability() {
        int baseEventProbability = plugin.getDiscordBot().getEventProbability();
        int cumulativeProbability = baseEventProbability + probabilityBooster;

        // Begrenzung der kumulativen Wahrscheinlichkeit auf maximal 100
        cumulativeProbability = Math.min(cumulativeProbability, 100);

        int randomValue = new Random().nextInt(100);

        plugin.getLogger().info("Checking event start: Probability=" + cumulativeProbability + ", Random=" + randomValue);

        if (randomValue < cumulativeProbability) {
            initializeEvent();
            probabilityBooster = 0; // Booster zurücksetzen
            plugin.getLogger().info("Event started");
        } else {
            probabilityBooster += 14; // Booster erhöhen
            randomValue = randomValue -16;
            plugin.getLogger().info("Booster +14 started");
            cumulativeProbability = baseEventProbability + probabilityBooster;
            plugin.getLogger().info("Probability=" + cumulativeProbability + ", Random=" + randomValue + "Try again");
            if (randomValue < cumulativeProbability) {
                initializeEvent();
                probabilityBooster = 0; // Booster zurücksetzen
                plugin.getLogger().info("Event started durch Tryagain");
            }
        }
    }


    private boolean isWood(Material material) {
        return material == Material.OAK_LOG || material == Material.BIRCH_LOG ||
                material == Material.SPRUCE_LOG || material == Material.JUNGLE_LOG ||
                material == Material.ACACIA_LOG || material == Material.DARK_OAK_LOG ||
                material == Material.OAK_LEAVES || material == Material.BIRCH_LEAVES ||
                material == Material.SPRUCE_LEAVES || material == Material.JUNGLE_LEAVES ||
                material == Material.ACACIA_LEAVES || material == Material.DARK_OAK_LEAVES;
    }



    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        Entity clickedEntity = event.getRightClicked();

        // Prüfen, ob das angeklickte Entity ein ItemFrame ist
        if (!(clickedEntity instanceof ItemFrame)) {
            return;
        }

        ItemFrame clickedFrame = (ItemFrame) clickedEntity;

        // Überprüfen, ob es sich um den spezifischen Rahmen handelt, der für das Event verwendet wird
        if (!clickedFrame.equals(eventItemFrame)) {
            return;
        }

        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        // Überprüfen, ob der Spieler das erforderliche Item (z. B. ein Diamant) in der Hand hat
        if (itemInHand == null || itemInHand.getType() != Material.DIAMOND) {
            player.sendMessage(ChatColor.RED + "Du benötigst einen Diamanten in deiner Hand, um das Event zu starten.");
            return;
        }

        // Überprüfen, ob das Event bereits aktiv ist
        if (isEventActive) {
            player.sendMessage(ChatColor.RED + "Das Event ist bereits aktiv!");
            return;
        }

        // Event starten
        startEvent(player);

        // Entferne einen Diamanten aus der Hand des Spielers
        itemInHand.setAmount(itemInHand.getAmount() - 1);
        player.sendMessage(ChatColor.GREEN + "Event gestartet!");

        // Hier können zusätzliche Aktionen oder visuelle Effekte hinzugefügt werden
    }



    private void playStartEventEffects(Location location) {
        World world = location.getWorld();

        // Partikeleffekte
        world.spawnParticle(Particle.SMOKE_LARGE, location, 50, 1.0, 1.0, 1.0, 0.1);
        world.spawnParticle(Particle.FIREWORKS_SPARK, location, 30, 1.0, 1.0, 1.0, 0.1);
        world.spawnParticle(Particle.ENCHANTMENT_TABLE, location, 100, 1.0, 1.0, 1.0, 1);

        // Soundeffekte
        world.playSound(location, Sound.BLOCK_BELL_USE, 1.0F, 1.0F);
        world.playSound(location, Sound.ENTITY_EVOKER_CAST_SPELL, 1.0F, 1.0F);

        // Zeitliche Abfolge der Effekte
        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (count > 5) {
                    this.cancel();
                    return;
                }

                world.spawnParticle(Particle.VILLAGER_HAPPY, location, 20, 0.5, 0.5, 0.5, 0);
                world.playSound(location, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.0F, 0.5F);
                count++;
            }
        }.runTaskTimer(plugin, 20L, 20L); // Wiederholt alle 1 Sekunden
    }



    // StartEvent-Methode mit Event-Fortschrittsinfo
    private void startEvent(Player player) {

        isEventActive = true;
        isEventInitialized = false;

        plugin.getLogger().info("Event started by player " + player.getName() + ": isEventActive=" + isEventActive + ", isEventInitialized=" + isEventInitialized);


        startEventVisuals(eventLocation);
        playStartEventEffects(eventLocation);

        int eventProbability = plugin.getDiscordBot().getEventProbability();
        int numberOfWaves = eventProbability / 10;
        player.sendMessage(ChatColor.GREEN + "Das Event hat " + numberOfWaves + " Wellen gestartet.");

        new BukkitRunnable() {
            int currentWave = 0;

            @Override
            public void run() {
                if (currentWave >= numberOfWaves) {
                    endEvent();
                    this.cancel();
                    return;
                }
                spawnWave(currentWave, eventLocation);
                broadcastToPlayersNearby(eventLocation, ChatColor.YELLOW + "Welle " + (currentWave + 1) + " von " + numberOfWaves + " gestartet.");
                currentWave++;
            }
        }.runTaskTimer(plugin, 0L, 20L * 60); // Jede Welle startet jede Minute
    }

    // Methode zum Senden von Nachrichten an Spieler in der Nähe
    private void broadcastToPlayersNearby(Location location, String message) {
        int radius = 50; // Radius, in dem Spieler die Nachricht erhalten
        for (Player player : location.getWorld().getPlayers()) {
            if (player.getLocation().distance(location) <= radius) {
                player.sendMessage(message);
            }
        }
    }


    private List<ZombieConfiguration> zombieConfigurations = new ArrayList<>();

    private class ZombieConfiguration {
        private ItemStack helmet;
        private List<PotionEffect> potionEffects;

        public ZombieConfiguration(ItemStack helmet, List<PotionEffect> potionEffects) {
            this.helmet = helmet;
            this.potionEffects = potionEffects;
        }

        public ItemStack getHelmet() {
            return helmet;
        }

        public List<PotionEffect> getPotionEffects() {
            return potionEffects;
        }
    }

    public void initializeZombieConfigurations() {
        // Leere die bestehenden Konfigurationen
        zombieConfigurations.clear();

        // Konfigurationen für verschiedene Schwierigkeitsgrade oder Event-Wahrscheinlichkeiten hinzufügen
        zombieConfigurations.add(new ZombieConfiguration(new ItemStack(Material.GOLDEN_HELMET, 1),
                Arrays.asList(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 600, 4),
                        new PotionEffect(PotionEffectType.SPEED, 600, 3))));

        zombieConfigurations.add(new ZombieConfiguration(new ItemStack(Material.DIAMOND_HELMET, 1),
                Arrays.asList(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 600, 6),
                        new PotionEffect(PotionEffectType.SPEED, 600, 3))));

        zombieConfigurations.add(new ZombieConfiguration(new ItemStack(Material.NETHERITE_HELMET, 1),
                Arrays.asList(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 600, 8),
                        new PotionEffect(PotionEffectType.SPEED, 600, 4))));

        zombieConfigurations.add(new ZombieConfiguration(new ItemStack(Material.CHAINMAIL_HELMET, 1),
                Arrays.asList(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 600, 3),
                        new PotionEffect(PotionEffectType.SPEED, 600, 2))));

        zombieConfigurations.add(new ZombieConfiguration(new ItemStack(Material.LEATHER_HELMET, 1),
                Arrays.asList(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 600, 2))));


    }


    private void spawnWave(int wave, Location location) {
        World world = location.getWorld();
        int difficultyLevel = plugin.getDiscordBot().getEventProbability();
        int numberOfEnemies = 5 + wave * 2; // Anzahl der Gegner pro Welle erhöhen

        for (int i = 0; i < numberOfEnemies; i++) {
            Zombie zombie = (Zombie) world.spawnEntity(location, EntityType.ZOMBIE);
            // Zufällige Konfiguration auswählen
            ZombieConfiguration randomConfiguration = getRandomZombieConfiguration();
            equipZombieWithConfiguration(zombie, randomConfiguration);
            giveZombieSpecialAbilities(zombie, difficultyLevel);
            spawnBlazesBasedOnProbability();
            zombie.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, Integer.MAX_VALUE, 6));
        }
    }

    private ZombieConfiguration getRandomZombieConfiguration() {
        // Zufällige Konfiguration aus der Liste auswählen
        Random random = new Random();
        int randomIndex = random.nextInt(zombieConfigurations.size());
        return zombieConfigurations.get(randomIndex);
    }

    private void equipZombieWithConfiguration(Zombie zombie, ZombieConfiguration configuration) {
        // Ausrüstung und Effekte entsprechend der Konfiguration anwenden
        zombie.getEquipment().setHelmet(configuration.getHelmet());
        for (PotionEffect effect : configuration.getPotionEffects()) {
            zombie.addPotionEffect(effect);
        }
    }


    private void endEvent() {
        isEventActive = false;
        isEventInitialized  = false;
        isEventCompleted = true;

        plugin.getLogger().info("Event ended: isEventActive=" + isEventActive + ", isEventInitialized=" + isEventInitialized + ", isEventCompleted=" + isEventCompleted);


        probabilityBooster = 0;

        endEventVisuals(eventLocation);

        // Entferne den ItemFrame
        eventItemFrame.remove();
        // Nachricht an alle Spieler in der Nähe senden
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(eventLocation.getWorld()) && player.getLocation().distance(eventLocation) < 50) {
                player.sendMessage(ChatColor.GREEN + "Es kommen keine weiteren Wellen! Bleibt in der nähe für Belohnungen.");


                //Visuelle Effekte (wie beginn) Zum Abschluss?
                playStartEventEffects(eventLocation);
            }
        }
        distributeRewards(eventLocation);
        //broadcastToPlayersNearby(eventLocation, ChatColor.GREEN + "Das Event wurde erfolgreich abgeschlossen!");
    }


    private void distributeRewards(Location location) {
        World world = location.getWorld();
        Collection<Entity> nearbyEntities = world.getNearbyEntities(location, 10, 10, 10);

        for (Entity entity : nearbyEntities) {
            if (entity instanceof Player) {
                Player player = (Player) entity;

                // Erstelle eine zufällige Menge an Silber zwischen 3 und 9
                int randomSilverAmount = new Random().nextInt(7) + 3; // Gibt eine zufällige Zahl zwischen 3 und 9
                ItemStack silverStack = createSilverStack(randomSilverAmount);

                // Überprüfe, ob der Spieler genug Platz im Inventar hat
                if (hasEnoughSpace(player.getInventory(), silverStack)) {
                    // Belohnungen an Spieler verteilen
                    player.getInventory().addItem(silverStack);
                    player.sendMessage(ChatColor.GREEN + "Du hast " + randomSilverAmount + " Silber als Belohnung erhalten!");
                } else {
                    player.sendMessage(ChatColor.RED + "Dein Inventar ist voll. Du hast keine Belohnung erhalten.");
                }
            }
        }
    }

    private boolean hasEnoughSpace(Inventory inventory, ItemStack itemStack) {
        int freeSlots = 0;

        for (ItemStack slot : inventory.getContents()) {
            if (slot == null || slot.getType() == Material.AIR) {
                freeSlots++;
            } else if (slot.isSimilar(itemStack) && (slot.getAmount() + itemStack.getAmount()) <= slot.getMaxStackSize()) {
                return true;
            }
        }

        return freeSlots >= itemStack.getAmount();
    }


    private ItemStack createSilverStack(int amount) {
        ItemStack silverStack = OraxenItems.getItemById("silber").build();
        silverStack.setAmount(amount);
        return silverStack;
    }


    @EventHandler
    public void onPlayerKill(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer != null && isInHCFW(killer) && isEventActive) {
            // Zusätzliche Belohnungen für den Mörder
            killer.getInventory().addItem(new ItemStack(Material.NETHER_STAR));
            killer.sendMessage(ChatColor.RED + "Du hast einen Mitspieler verraten und eine extra Belohnung erhalten!");
        }
    }

    private void startEventVisuals(Location location) {
        World world = location.getWorld();
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!isEventActive) {
                    this.cancel();
                    return;
                }
                // Kontinuierliche visuelle Effekte für aktives Event
                playStartEventEffects(eventLocation);
                //world.spawnParticle(Particle.REDSTONE, location.add(0.5, 2.0, 0.5), 10, 1.0, 1.0, 1.0, new Particle.DustOptions(Color.GREEN, 1));
            }
        }.runTaskTimer(plugin, 0L, 20L * 60); // Effekte alle 60 Sekunden wiederholen
    }


    private void endEventVisuals(Location location) {
        World world = location.getWorld();
        world.spawnParticle(Particle.FIREWORKS_SPARK, location, 200, 1, 1, 1);
        world.playSound(location, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0F, 1.0F);

        plugin.getLogger().info("Event-Visuals werden beendet!"); // Debugging-Nachricht

        // Partikeleffekt für inaktives Event (Rot)
        world.spawnParticle(Particle.REDSTONE, location.add(0.5, 2.0, 0.5), 10, 1.0, 1.0, 1.0, new Particle.DustOptions(Color.RED, 1));
    }

    // Abteilung Warden
    // ANTI STRIP MINING TOOL
    private final HashMap<UUID, Integer> playerResourceCount = new HashMap<>();
    private final int RESOURCE_THRESHOLD = 6; // Beispielwert
    private final Random random = new Random();


    //Block Break
    //Incl. Or Generation + Event Trigger


    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        World world = block.getWorld();

        // Überprüfen, ob sich der Spieler in der Welt "HCFW" befindet
        if (player.getWorld().getName().equalsIgnoreCase("hcfw")) {
            UUID playerId = player.getUniqueId();
            playerResourceCount.put(playerId, playerResourceCount.getOrDefault(playerId, 0) + 1);

            // Überprüfen, ob der Blocktyp eine Steinvariante ist
            if (isStoneVariant(block.getType())) {
                generateOresAroundBlock(block, world);
            }

            if (playerResourceCount.get(playerId) >= RESOURCE_THRESHOLD) {
                triggerRandomEvent(player);
                playerResourceCount.put(playerId, 0); // Reset the count
            }
        }
    }

    private boolean isStoneVariant(Material material) {
        // Liste der Steinvarianten
        Set<Material> stoneVariants = EnumSet.of(
                Material.STONE, Material.COBBLESTONE, Material.ANDESITE, Material.DIORITE,
                Material.GRANITE, Material.DEEPSLATE, Material.STONE_BRICKS, Material.MOSSY_COBBLESTONE,
                Material.MOSSY_STONE_BRICKS, Material.MUD, Material.DRIPSTONE_BLOCK
        );
        return stoneVariants.contains(material);
    }

    //BlockBreakOre-Core
    private void generateOresAroundBlock(Block block, World world) {
        int radius = 3; // Radius um den zerstörten Block
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.random() < 0.006) { //  Chance für jeden Block im Radius
                        Location newLocation = block.getLocation().add(x, y, z);
                        Block newBlock = world.getBlockAt(newLocation);
                        // Überprüfen, ob der Block eine Steinvariante ist
                        if (isStoneVariant(newBlock.getType())) {
                            newBlock.setType(getRandomOreType()); // Zufälliger Erztyp
                        }
                    }
                }
            }
        }
    }

    private Material getRandomOreType() {
        // Hier kannst du eine zufällige Auswahl von Erztypen implementieren
        Material[] ores = {Material.COAL_ORE, Material.IRON_ORE, Material.REDSTONE_ORE, Material.GOLD_ORE, Material.DIAMOND_ORE, Material.LAPIS_ORE};
        return ores[new Random().nextInt(ores.length)];
    }


    //BlockBreakOre-Core END

    //Event Trigger VarManager
    private void triggerRandomEvent(Player player) {
        int eventProbability = plugin.getDiscordBot().getEventProbability();
        List<Runnable> possibleEvents = new ArrayList<>();

        // Basisereignisse, die immer möglich sind
        possibleEvents.add(() -> triggerScareEvent(player, "klein"));

        // Ereignisse, die mit steigender Wahrscheinlichkeit hinzugefügt werden
        if (eventProbability >= 25) {
            possibleEvents.add(() -> triggerScareEvent(player, "mittel"));
            possibleEvents.add(() -> spawnHostileAnimal(player, "Schaf"));
        }
        if (eventProbability >= 50) {
            possibleEvents.add(() -> triggerScareEvent(player, "groß"));
            possibleEvents.add(() -> spawnSwarmOfEnemies(player));
        }
        if (eventProbability >= 55) {
            possibleEvents.add(() -> spawnHostileAnimal(player, "Schwein"));

        }
        if (eventProbability >= 65) {

            possibleEvents.add(() -> spawnWeakenedWarden(player));
        }


        // Wähle zufällig ein Ereignis aus der Liste
        int randomEventIndex = new Random().nextInt(possibleEvents.size());
        possibleEvents.get(randomEventIndex).run();
    }


    private void triggerScareEvent(Player player, String scareSize) {
        switch (scareSize) {
            case "klein":
                // Klein: Plötzliches Geräusch
                player.playSound(player.getLocation(), Sound.ENTITY_CAT_HISS, 1.0F, 1.0F);
                break;
            case "mittel":
                // Mittel: Harmlose Kreaturen erscheinen plötzlich
                spawnBatsAroundPlayer(player, 5); // 5 Fledermäuse erscheinen
                break;
            case "groß":
                // Groß: Illusionen von gefährlichen Kreaturen
                spawnIllusionaryMobs(player, EntityType.ZOMBIE, 2); // 2 illusionäre Zombies erscheinen
                break;
            default:
                // Extrem: Kombination aus visuellen und Sound-Effekten
                createLightningEffect(player);
                break;
        }
    }

    private void spawnBatsAroundPlayer(Player player, int count) {
        for (int i = 0; i < count; i++) {
            Bat bat = (Bat) player.getWorld().spawnEntity(player.getLocation(), EntityType.BAT);
            scheduleBatAttack(bat, player);
        }
    }
    private void scheduleBatAttack(Bat bat, Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (bat.isDead() || !player.isOnline()) {
                    this.cancel();
                    return;
                }

                // Fledermaus greift Spieler an
                if (bat.getLocation().distance(player.getLocation()) < 2) {
                    player.damage(1.0); // Geringer Schaden
                    bat.remove();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // Überprüft jede Sekunde
    }

    private void spawnIllusionaryMobs(Player player, EntityType type, int count) {
        World world = player.getWorld();
        Location playerLocation = player.getLocation();

        for (int i = 0; i < count; i++) {
            Location spawnLocation = playerLocation.clone().add(
                    random.nextInt(5) - 2,  // Zufällige X Koordinate
                    0,                      // Etwas über dem Boden
                    random.nextInt(5) - 2   // Zufällige Z Koordinate
            );

            LivingEntity mob = (LivingEntity) world.spawnEntity(spawnLocation, type);

            // Setzen Sie benutzerdefinierte Eigenschaften für den Mob
            mob.setSilent(true); // Keine Geräusche
            mob.setAI(false); // Keine künstliche Intelligenz
            mob.setInvulnerable(true); // Unverwundbar
            mob.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 30, 0, false, false)); // Kurzzeitig unsichtbar

            // Erstellen Sie einen Task, um den Mob nach einer gewissen Zeit zu entfernen
            new BukkitRunnable() {
                @Override
                public void run() {
                    mob.remove();
                }
            }.runTaskLater(plugin, 20 * 10); // 10 Sekunden später
        }
    }


    private void createLightningEffect(Player player) {
        Location location = player.getLocation();
        player.getWorld().strikeLightningEffect(location);
        player.playSound(location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0F, 0.5F);
        spawnIllusionaryMobs(player, EntityType.ZOMBIE, 2);
        spawnBatsAroundPlayer(player, 5);
    }




    private void spawnHostileAnimal(Player player, String animalType) {
        World world = player.getWorld();
        Location location = player.getLocation();
        Entity entity;

        switch (animalType) {
            case "Schaf":
                entity = world.spawnEntity(location, EntityType.SHEEP);
                break;
            case "Schwein":
                entity = world.spawnEntity(location, EntityType.PIG);
                break;
            default:
                return;
        }
        int eventProbability = plugin.getDiscordBot().getEventProbability();
        LivingEntity livingEntity = (LivingEntity) entity;
        livingEntity.setCustomName(ChatColor.RED + "Freaky " + animalType);
        livingEntity.setCustomNameVisible(true);

        // Neue Logik: Das Tier explodiert nach einer Weile
        scheduleExplosion(livingEntity, player, eventProbability);
        followPlayer(livingEntity, player);
    }

    private void followPlayer(LivingEntity entity, Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (entity.isDead() || !player.isOnline() || entity.getLocation().distance(player.getLocation()) > MAX_FOLLOW_DISTANCE) {
                    this.cancel();
                    return;
                }

                // Einfache Logik, um das Entity in Richtung des Spielers zu bewegen
                Location entityLocation = entity.getLocation();
                Location playerLocation = player.getLocation();
                Vector toPlayer = playerLocation.toVector().subtract(entityLocation.toVector()).normalize();

                // Erhöhe die Geschwindigkeit für aggressivere Verfolgung
                Vector velocity = toPlayer.multiply(0.3); // Geschwindigkeitswert anpassen
                if (!Double.isFinite(velocity.getX()) || !Double.isFinite(velocity.getY()) || !Double.isFinite(velocity.getZ())) {
                    return;
                }
                entity.setVelocity(velocity); // Setzt die Geschwindigkeit des Entity
                entityLocation.setDirection(toPlayer); // Richtet das Entity zum Spieler aus
                entity.teleport(entityLocation);
            }
        }.runTaskTimer(plugin, 0L, 20L); // Aktualisiert alle Sekunden
    }

    private static final double MAX_FOLLOW_DISTANCE = 50.0; // Maximale Distanz, aus der das Entity dem Spieler folgt

    private void scheduleExplosion(LivingEntity entity, Player player, int probability) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (entity.isDead() || !player.isOnline()) {
                    this.cancel();
                    return;
                }

                // Explosionseffekt erzeugen
                Location loc = entity.getLocation();
                entity.getWorld().createExplosion(loc, 2.0F, true, true); // Explosion mit Feuer und ohne Blockschaden
                entity.remove();
            }
        }.runTaskLater(plugin, 20 * 6); // 6 Sekunden später
    }

    private void spawnWeakenedWarden(Player player) {
        World world = player.getWorld();
        Location location = player.getLocation();

        // Erzeugen einer kontrollierten Explosion, um Platz zu schaffen
        createClearanceExplosion(location);

        // Verzögerung vor dem Spawnen des Wardens
        new BukkitRunnable() {
            @Override
            public void run() {
                Warden warden = (Warden) world.spawnEntity(location, EntityType.WARDEN);
                warden.setHealth(30); // Reduzierte Gesundheit
                warden.setCustomName(ChatColor.RED + "Freaky Warden");
                warden.setCustomNameVisible(true);
                warden.setTarget(player);
            }
        }.runTaskLater(plugin, 20L * 2); // 2 Sekunden später
    }

    private void createClearanceExplosion(Location location) {
        World world = location.getWorld();

        // Spieler in der Nähe der Explosion kurzzeitig unverwundbar machen
        makeNearbyPlayersInvulnerable(location, world, 15.0, 20L * 2); // 2 Sekunden Unverwundbarkeit

        // Erzeugen einer Explosion, die Blöcke zerstört, aber keinen Schaden an Spielern verursacht
        world.createExplosion(location, 4.0F, false, true); // Explosion ohne Feuer

    }

    private void makeNearbyPlayersInvulnerable(Location location, World world, double radius, long durationTicks) {
        List<Player> affectedPlayers = world.getNearbyEntities(location, radius, radius, radius)
                .stream()
                .filter(entity -> entity instanceof Player)
                .map(entity -> (Player) entity)
                .collect(Collectors.toList());

        for (Player player : affectedPlayers) {
            player.setInvulnerable(true);
        }

        // Rückgängigmachen der Unverwundbarkeit nach der festgelegten Dauer
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : affectedPlayers) {
                    player.setInvulnerable(false);
                }
            }
        }.runTaskLater(plugin, durationTicks);
    }




    private void spawnSwarmOfEnemies(Player player) {
        World world = player.getWorld();
        Location location = player.getLocation();

        for (int i = 0; i < 5; i++) {
            Zombie zombie = (Zombie) world.spawnEntity(location, EntityType.ZOMBIE);

            // Reduziere die Wahrscheinlichkeit für Baby-Zombies
            if (random.nextDouble() < 0.2) { // 20% Chance für einen Baby-Zombie
                zombie.setBaby(true);
            }

            // Variiere die Ausrüstung der Zombies
            ItemStack weapon = random.nextDouble() < 0.5 ? new ItemStack(Material.IRON_SWORD) : new ItemStack(Material.WOODEN_SWORD);
            zombie.getEquipment().setItemInMainHand(weapon);

            zombie.setTarget(player);
        }
    }



    //FreakyBed

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();

        // Überprüfen, ob der Spieler sich in der Welt "HCFW" befindet und ob die Aktion ein Rechtsklick ist
        if (player.getWorld().getName().equalsIgnoreCase("hcfw") && action == Action.RIGHT_CLICK_BLOCK) {
            // Überprüfen, ob der geklickte Block ein Bett ist
            Block clickedBlock = event.getClickedBlock();
            if (clickedBlock != null && isBed(clickedBlock.getType())) {
                // Verhindern, dass der Spieler mit dem Bett interagiert
                event.setCancelled(true);

                // Erzeugen einer Explosion am Standort des Bettes
                clickedBlock.getWorld().createExplosion(clickedBlock.getLocation(), 6.0F, true, true);
            }
        }
    }

    private boolean isBed(Material material) {
        // Überprüfen Sie alle Bettvarianten, da es in Minecraft verschiedene Betten gibt
        return material.name().endsWith("_BED");
    }

    //Zombie Event Message Info
    private Map<UUID, Integer> playerZombieKillCount = new HashMap<>();

    @EventHandler
    public void onZombieDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Zombie)) return;
        Zombie zombie = (Zombie) event.getEntity();
        if (!zombie.getWorld().getName().equalsIgnoreCase("hcfw")) return;

        Player killer = zombie.getKiller();
        if (killer != null) {
            UUID playerID = killer.getUniqueId();
            int kills = playerZombieKillCount.getOrDefault(playerID, 0);
            kills++;
            playerZombieKillCount.put(playerID, kills);

            //plugin.getLogger().info("Zombie killed: isEventActive=" + isEventActive + ", isEventInitialized=" + isEventInitialized + ", isEventCompleted=" + isEventCompleted);

            // Schwierigkeitsgrad holen (zwischen 40% und 99%)
            int difficultyLevel = Math.max(40, Math.min(plugin.getDiscordBot().getEventProbability(), 99));

            // Berechnung der erforderlichen Kills
            int baseKills = 90;
            int requiredKillsForEventInfo = (int) (baseKills * (100.0 / difficultyLevel));


            if (kills >= requiredKillsForEventInfo) {
                if (isEventCompleted) {
                    killer.sendMessage(ChatColor.GOLD + "Die Schatten flüstern von deinem Sieg, doch die Echos der Schlacht sind längst verklungen. Das große Ereignis ist vorüber, und du stehst allein mit deiner Tapferkeit.");
                } else if (isEventInitialized && !isEventActive) {
                    killer.sendMessage(ChatColor.GOLD + "Dein Schwert singt Lieder des Todes, und die Untoten weichen zurück. Eine neue Herausforderung ruft! Finde das Herz des Aufruhrs bei den Koordinaten: [X: " + eventLocation.getBlockX() + ", Y: " + eventLocation.getBlockY() + ", Z: " + eventLocation.getBlockZ() + "]. Dein Schicksal wartet.");
                } else {
                    killer.sendMessage(ChatColor.GOLD + "Deine Klinge durchschneidet die Stille der Untoten, doch keine Legende erwacht heute aus ihrem Schlaf. Die Welt von FreakyWorld bleibt vorerst ruhig, ohne das Rufen eines neuen Abenteuers.");
                }
                playerZombieKillCount.put(playerID, 0); // Zähler zurücksetzen
            }
        }
    }

    public void applyDarknessEffectToPlayers() {
        int difficultyLevel = plugin.getDiscordBot().getEventProbability();

        // Überprüfen, ob die Wahrscheinlichkeit über 80% liegt
        if (difficultyLevel > 80) {
            for (UUID playerId : playersInHCFW) {
                Player player = Bukkit.getPlayer(playerId);

                // Stelle sicher, dass der Spieler online ist
                if (player != null) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 0));
                }
            }
        }
    }

    //Boss Spawn Demon
    //Boss Spawn Demon
    private void checkForExperienceBottles() {
        World world = Bukkit.getWorld("hcfw");
        if (world == null) {
            System.out.println("Welt 'hcfw' nicht gefunden"); // Debugging-Nachricht
            return;
        }

        int requiredExperienceBottles = 3456;
        int foundExperienceBottles = 0;
        Player initiatingPlayer = null; // Initialisieren Sie den Initiierenden Spieler mit null
        Location ritualLocation = findRitualLocation(world, null); // Initialisieren Sie ritualLocation

        for (Entity entity : world.getEntities()) {
            if (entity.getType() == EntityType.DROPPED_ITEM) {
                Item item = (Item) entity;
                ItemStack itemStack = item.getItemStack();
                if (itemStack != null && itemStack.getType() == Material.EXPERIENCE_BOTTLE) {
                    foundExperienceBottles += itemStack.getAmount();

                    if (initiatingPlayer == null && foundExperienceBottles >= requiredExperienceBottles) {
                        // Wenn die erforderliche Anzahl von Erfahrungsflaschen erreicht wurde
                        // und der Initiierende Spieler noch nicht festgelegt wurde, setzen Sie ihn fest.
                        initiatingPlayer = getPlayerWhoDroppedItemNearLocation(ritualLocation, 10);

                        if (initiatingPlayer != null) {
                            System.out.println("Initiierender Spieler gefunden: " + initiatingPlayer.getName());
                        }
                    }
                }
            }
        }

        if (initiatingPlayer != null && foundExperienceBottles >= requiredExperienceBottles) {
            System.out.println("Erforderliche Anzahl an XP-Flaschen erreicht. Initiierender Spieler: " + initiatingPlayer.getName());
            // Rufen Sie die Methode zum Auslösen des Rituals auf und übergeben Sie den Initiierenden Spieler
            triggerRitual(world, initiatingPlayer);
            // Nachdem die XP-Flaschen entfernt wurden, rufen Sie die Methode zum Finden des Ritualorts erneut auf
            ritualLocation = findRitualLocation(world, ritualLocation);
            if (ritualLocation != null) {
                // Aktualisieren Sie die Ritualposition
                removeExperienceBottles(world); // Hier die Methode zum Entfernen der XP-Flaschen aufrufen
                System.out.println("XP-Flaschen entfernt und Ritual gestartet an Position: " + ritualLocation.toString());
                // Führen Sie hier weitere Schritte aus, die auf der gefundenen Ritualposition basieren
            }
        } else {
            System.out.println("Keine ausreichenden XP-Flaschen gefunden. Gefundene Flaschen: " + foundExperienceBottles);
        }
    }




    // Diese Methode ermittelt den Spieler, der ein Item gedroppt hat
    private Player getPlayerWhoDroppedItemNearLocation(Location ritualLocation, int radius) {
        if (ritualLocation == null) {
            System.out.println("getPlayerWhoDroppedItemNearLocation: Ungültige Ritual Location");
            return null;
        }

        World world = ritualLocation.getWorld();
        if (world == null) {
            System.out.println("getPlayerWhoDroppedItemNearLocation: Ungültige Welt");
            return null;
        }

        System.out.println("getPlayerWhoDroppedItemNearLocation: Radius=" + radius + " Ritual Location=" + ritualLocation.toString());

        List<Player> nearbyPlayers = new ArrayList<>();

        for (Player player : world.getPlayers()) {
            if (player.getWorld().getName().equals("hcfw")) {
                double distance = player.getLocation().distance(ritualLocation);
                System.out.println("Entfernung zwischen Spieler " + player.getName() + " und Ritual Location: " + distance);
                if (distance <= radius) {
                    nearbyPlayers.add(player);
                }
            }
        }

        if (!nearbyPlayers.isEmpty()) {
            // Wählen Sie einen Spieler aus der Liste der in der Nähe befindlichen Spieler aus (z. B. den ersten in der Liste)
            Player nearestPlayer = nearbyPlayers.get(0);
            System.out.println("Spieler in der Nähe mit XP-Flasche gefunden: " + nearestPlayer.getName());
            return nearestPlayer;
        }

        System.out.println("Kein passender Spieler in der Nähe gefunden");
        return null;
    }






    private void removeExperienceBottles(World world) {
        System.out.println("removeExperienceBottles wird ausgeführt"); // Debugging-Nachricht

        List<Entity> entitiesToRemove = new ArrayList<>();

        for (Entity entity : world.getEntitiesByClasses(Item.class)) {
            Item item = (Item) entity;
            ItemStack itemStack = item.getItemStack();

            if (itemStack != null && itemStack.getType() == Material.EXPERIENCE_BOTTLE) {
                entitiesToRemove.add(entity);
            }
        }

        // Entferne alle XP-Flaschen auf einmal
        for (Entity entity : entitiesToRemove) {
            entity.remove();
        }

        System.out.println("Alle XP-Flaschen entfernt: " + entitiesToRemove.size()); // Debugging-Nachricht
    }

    private void updateBossHealthBar(Wither witherBoss) {
        if (witherBoss == null || !witherBoss.isValid()) {
            return; // Sicherstellen, dass der Boss noch existiert und gültig ist
        }

        double currentHealth = witherBoss.getHealth();
        double progress = currentHealth / witherBoss.getMaxHealth();
        witherBoss.getBossBar().setProgress(progress);
    }

    private void triggerRitual(World world, Player initiatingPlayer) {
        System.out.println("triggerRitual wird ausgeführt"); // Debugging-Nachricht

        // Finde den Ort, an dem die XP-Flaschen gefunden wurden
        Location ritualLocation = findRitualLocation(world, null);

        if (ritualLocation == null) {
            System.out.println("Ritualort nicht gefunden"); // Debugging-Nachricht
            return; // Der Ort wurde nicht gefunden, das Ritual kann nicht gestartet werden
        }

        // Rufe die Methode zum Spawnen der Blazes auf
        spawnBlazesBasedOnProbability();

        // Erstelle den Wither ohne AI
        Location bossLocation = ritualLocation.clone().add(0, 2, 0); // Über dem Ritualort
        Wither witherBoss = (Wither) world.spawnEntity(bossLocation, EntityType.WITHER);
        witherBoss.setAI(false); // Deaktiviere die KI

        // Passe den Wither an (z.B., Rüstung, Waffen)
        witherBoss.getEquipment().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
        witherBoss.getEquipment().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
        witherBoss.getEquipment().setLeggings(new ItemStack(Material.DIAMOND_LEGGINGS));
        witherBoss.getEquipment().setBoots(new ItemStack(Material.DIAMOND_BOOTS));

        // Füge weitere Anpassungen hinzu, je nach Bedarf

        // Starte den Wither
        witherBoss.setHealth(witherBoss.getMaxHealth());
        witherBoss.setTarget(null); // Kein Ziel, da die KI deaktiviert ist

        // Optional: Spiele Effekte oder Nachrichten für das Ritual ab
        playSpawnSound(); // Spiele den Spawn Sound
        blazesSpawnedForCurrentPhase = false;
        currentPhase = 0;

        BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
        final int[] taskIdWrapper = new int[1]; // Wrapper für die Task-ID
        taskIdWrapper[0] = scheduler.scheduleSyncRepeatingTask(plugin, () -> {
            updateBossHealthBar(witherBoss);
            checkAndUpdatePhase(witherBoss);

            if (witherBoss.getHealth() <= 0) {
                handleRitualCompletion(witherBoss, initiatingPlayer); // Übergebe den Initiierenden Spieler
                scheduler.cancelTask(taskIdWrapper[0]); // Verwende die umschlossene Task-ID
            }
        }, 0L, 20L); // Aktualisiert jede Sekunde (20 Ticks)
    }

    private boolean blazesSpawnedForCurrentPhase = false; // Globale Variable

    private void checkAndUpdatePhase(Wither witherBoss) {
        double healthPercentage = witherBoss.getHealth() / witherBoss.getMaxHealth();

        // Bestimme die Phase basierend auf dem Gesundheitsprozentsatz
        int newPhase;
        if (healthPercentage > 0.75) {
            newPhase = 0;
        } else if (healthPercentage > 0.50) {
            newPhase = 1;
        } else if (healthPercentage > 0.25) {
            newPhase = 2;
        } else {
            newPhase = 3;
        }

        if (newPhase != currentPhase) {
            currentPhase = newPhase;
            blazesSpawnedForCurrentPhase = false; // Zurücksetzen, wenn die Phase wechselt
            handleBossMessagesAndPhases(witherBoss, currentPhase);
        }
    }

    private Set<Player> participants = new HashSet<>();

    private void trackParticipant(Player player) {
        participants.add(player);
    }

    private void handleRitualCompletion(Wither witherBoss, Player initiatingPlayer) {
        // Nachricht und Belohnung nur für den Spieler, der das Ritual gestartet hat
        if (initiatingPlayer != null) {
            initiatingPlayer.sendMessage("Das Ritual wurde erfolgreich abgeschlossen!");

            // Gib dem initiierenden Spieler 4 Stücke des benutzerdefinierten Oraxen-Items "Gold"
            ItemStack goldItem = OraxenItems.getItemById("gold").build();
            goldItem.setAmount(4); // Setze die Menge auf 4
            if (hasEnoughSpace(initiatingPlayer.getInventory(), goldItem)) {
                initiatingPlayer.getInventory().addItem(goldItem);
                initiatingPlayer.sendMessage(ChatColor.GREEN + "Du hast 4 Gold als Belohnung erhalten!");
            } else {
                initiatingPlayer.sendMessage(ChatColor.RED + "Dein Inventar ist voll. Du hast keine Belohnung erhalten.");
            }
        }

        // Leere die Teilnehmerliste für das nächste Ritual
        participants.clear();
    }


    private void playSpawnSound() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            // Spiele verschiedene schaurige Sounds ab
            player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, 1.0f, 0.5f);
            player.playSound(player.getLocation(), Sound.AMBIENT_UNDERWATER_ENTER, 1.0f, 0.5f);
            player.playSound(player.getLocation(), Sound.AMBIENT_WARPED_FOREST_MOOD, 1.0f, 0.5f);
            // Füge weitere schaurige Sounds hinzu, je nach Bedarf
        }
    }
    private void playMelodyDuringBossFight(Location ritualLocation) {
        BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
        int taskId = scheduler.scheduleSyncRepeatingTask(plugin, () -> {
            for (Player player : ritualLocation.getWorld().getPlayers()) {
                if (player.getLocation().distanceSquared(ritualLocation) < 100) { // Anpassen, wie nah Spieler sein müssen
                    player.playSound(player.getLocation(), Sound.MUSIC_DISC_CAT, 1.0f, 1.0f); // Beispiel für eine Melodie
                    // Du kannst hier verschiedene Melodien und Effekte abspielen
                }
            }
        }, 0L, 20L * 30); // Hier kannst du die Intervalle für die Melodieanzeige einstellen (z.B. alle 30 Sekunden)
    }
    private int bossHealth = 1000; // Boss-Gesundheit (kann angepasst werden)
    private int currentPhase = 0; // Aktuelle Kampfphase (0 - 3)

    private boolean isBossAIEnabled = false; // Variable zur Verwaltung des KI-Zustands

    private void handleBossMessagesAndPhases(Wither witherBoss, int phase) {
        // Boss-Nachrichten und Phasen verwalten
        witherBoss.setCustomName("Freaky Demon"); // Beispiel: Setze den Namen des Bosses

        // Aktualisiere die Boss-Lebensanzeige
        double progress = witherBoss.getHealth() / witherBoss.getMaxHealth();
        witherBoss.getBossBar().setProgress(progress);
        witherBoss.getBossBar().setTitle("Boss Health: " + (int)(witherBoss.getHealth()));

        // Nachrichten und Aktionen abhängig von der Phase
        switch (phase) {
            case 0:
                sendNearbyPlayersMessage(witherBoss.getWorld(), witherBoss.getLocation(), "Kampf begonnen!", 50);
                setBossAIState(witherBoss, true); // Boss wechselt in AI True
                blazesSpawnedForCurrentPhase = false; // Zurücksetzen der Flag
                break;
            case 1:
                sendNearbyPlayersMessage(witherBoss.getWorld(), witherBoss.getLocation(), "Ihr seid sehr stark!", 50);
                setBossAIState(witherBoss, false); // Boss wechselt in AI False
                blazesSpawnedForCurrentPhase = false; // Zurücksetzen der Flag
                if (!blazesSpawnedForCurrentPhase) {
                    spawnHealingBlazes(witherBoss.getWorld(), witherBoss.getLocation(), witherBoss, 2); // Spawn 2 Blazes
                    blazesSpawnedForCurrentPhase = true; // Setzen der Flag
                }
                // Setze die Bewegungsgeschwindigkeit des Withers in Phase 1
                witherBoss.setVelocity(new Vector(0, -0.2, 0)); // Bewegt sich nach unten
                break;
            case 2:
                sendNearbyPlayersMessage(witherBoss.getWorld(), witherBoss.getLocation(), "Boss wechselt die Taktik! Der Wither wird nun von Blazes geheilt!", 50);
                if (!blazesSpawnedForCurrentPhase) {
                    spawnHealingBlazes(witherBoss.getWorld(), witherBoss.getLocation(), witherBoss, 5); // Spawn 5 Blazes
                    blazesSpawnedForCurrentPhase = true; // Setzen der Flag
                }
                setBossAIState(witherBoss, true); // Boss wechselt in AI True
                // Setze die maximale Position des Withers in Phase 2
                Location currentLocation = witherBoss.getLocation();
                Location maxPosition = new Location(currentLocation.getWorld(), currentLocation.getX(), 50, currentLocation.getZ());
                witherBoss.teleport(maxPosition);
                break;
            case 3:
                sendNearbyPlayersMessage(witherBoss.getWorld(), witherBoss.getLocation(), "Die letzte Phase beginnt!", 50);
                setBossAIState(witherBoss, true); // Boss wechselt in AI True
                blazesSpawnedForCurrentPhase = false; // Zurücksetzen der Flag
                // Setze die Bewegungsgeschwindigkeit und Geschwindigkeit des Withers in Phase 3
                witherBoss.setVelocity(new Vector(0, 1.0, 0)); // Bewegt sich sehr schnell nach oben
                witherBoss.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(2.0); // Erhöhe die Bewegungsgeschwindigkeit
                break;
            default:
                break;
        }
    }




    private void spawnHealingBlazes(World world, Location witherLocation, Wither witherBoss, int numberOfBlazes) {
        // Überprüfen und ändern Sie das Wetter in der HCFW-Welt
        if (world.hasStorm() || world.isThundering()) {
            world.setStorm(false);
            world.setThundering(false);
        }

        double angleStep = Math.PI * 2 / numberOfBlazes;

        for (int i = 0; i < numberOfBlazes; i++) {
            double angle = angleStep * i;
            Location blazeLocation = witherLocation.clone().add(Math.cos(angle) * 5, 3, Math.sin(angle) * 5); // Kreisformation um den Wither

            Blaze blaze = (Blaze) world.spawnEntity(blazeLocation, EntityType.BLAZE);
            blaze.setAI(false); // Deaktiviere die KI der Blazes

            // Starte einen visuellen Effekt und heile den Wither regelmäßig
            startHealingEffect(blaze, witherBoss);
        }
    }


    private List<Blaze> healingBlazes = new ArrayList<>();

    private void startHealingEffect(Blaze blaze, Wither witherBoss) {
        healingBlazes.add(blaze);
        BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
        final int[] taskIdWrapper = new int[1];
        taskIdWrapper[0] = scheduler.scheduleSyncRepeatingTask(plugin, () -> {
            if (!blaze.isValid() || !witherBoss.isValid()) {
                healingBlazes.remove(blaze);
                if (healingBlazes.isEmpty()) {
                    scheduler.cancelTask(taskIdWrapper[0]);
                }
                return;
            }

            // Visueller Effekt: Partikelstrahl von Blaze zu Wither
            Location blazeLocation = blaze.getLocation();
            Location witherLocation = witherBoss.getLocation();
            blaze.getWorld().spawnParticle(Particle.END_ROD, blazeLocation, 0, witherLocation.getX() - blazeLocation.getX(), witherLocation.getY() - blazeLocation.getY(), witherLocation.getZ() - blazeLocation.getZ(), 0.1);
            blaze.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, blazeLocation, 5,witherLocation.getX() - blazeLocation.getX(), witherLocation.getY() - blazeLocation.getY(), witherLocation.getZ() - blazeLocation.getZ(), 0.1);
            blaze.getWorld().spawnParticle(Particle.SMOKE_LARGE, blazeLocation, 5, witherLocation.getX() - blazeLocation.getX(), witherLocation.getY() - blazeLocation.getY(), witherLocation.getZ() - blazeLocation.getZ(), 0.1);

            // Heile den Wither, wenn mindestens ein Blaze lebt
            if (!healingBlazes.isEmpty()) {
                double newHealth = Math.min(witherBoss.getHealth() + 1, witherBoss.getMaxHealth());
                witherBoss.setHealth(newHealth);
            }
        }, 0L, 20L);
    }


    private void sendNearbyPlayersMessage(World world, Location location, String message, double radius) {
        double radiusSquared = radius * radius;
        for (Player player : world.getPlayers()) {
            if (player.getWorld().getName().equals("hcfw") && player.getLocation().distanceSquared(location) <= radiusSquared) {
                player.sendMessage(message);
            }
        }
    }



// Verwende diese Methode in `handleBossMessagesAndPhases`, um Nachrichten an Spieler in der Nähe zu senden

    private void setBossAIState(Wither witherBoss, boolean enableAI) {
        if (isBossAIEnabled != enableAI) {
            witherBoss.setAI(enableAI);
            isBossAIEnabled = enableAI;
        }
    }


    private Location findRitualLocation(World world, Location previousLocation) {
        System.out.println("findRitualLocation wird ausgeführt"); // Debugging-Nachricht

        if (previousLocation != null) {
            System.out.println("Verwende vorherige Position: " + previousLocation.toString()); // Debugging-Nachricht
            return previousLocation;
        }

        for (Entity entity : world.getEntities()) {
            if (entity.getType() == EntityType.DROPPED_ITEM) {
                Item item = (Item) entity;
                ItemStack itemStack = item.getItemStack();
                if (itemStack != null && itemStack.getType() == Material.EXPERIENCE_BOTTLE) {
                    Location ritualLocation = item.getLocation();
                    System.out.println("Ritualort gefunden: " + ritualLocation.toString()); // Debugging-Nachricht
                    return ritualLocation;
                }
            }
        }

        System.out.println("Kein passender Ort gefunden"); // Debugging-Nachricht
        return null; // Kein passender Ort gefunden
    }



}


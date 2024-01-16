package h34r7l3s.freakyworld;

import io.th0rgal.oraxen.api.OraxenItems;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;


import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import java.util.HashMap;
import java.util.UUID;
import java.util.Random;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
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
        }.runTaskTimer(plugin, 0L, 20L);// Alle 20 Ticks überprüfen



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
                spawnZombiesBasedOnProbability();
            }
        }.runTaskTimer(plugin, 0L, 320L); // 120 Ticks = 16 Sekunden
    }

    // Methode zum Spawnen von Zombies basierend auf der Event-Wahrscheinlichkeit
    private void spawnZombiesBasedOnProbability() {
        World world = plugin.getServer().getWorld("hcfw");
        if (world == null || world.getPlayers().isEmpty()) {
            //System.out.println("Keine Spieler in 'hcfw' oder Welt existiert nicht.");
            return;
        }

        int difficultyLevel = plugin.getDiscordBot().getEventProbability();
        int maxZombiesToSpawn = calculateMaxZombies(difficultyLevel);

        for (int i = 0; i < maxZombiesToSpawn; i++) {
            // Überprüfung, ob die maximale Anzahl an Zombies erreicht ist
            if (countCurrentZombies(world) >= maxZombiesToSpawn) {
                break;
            }

            // Zufällige Spawn-Position
            int x = new Random().nextInt(world.getMaxHeight());
            int z = new Random().nextInt(world.getMaxHeight());
            int y = world.getHighestBlockYAt(x, z);
            Location spawnLocation = new Location(world, x, y, z);

            // Spawn einen Zombie
            Zombie zombie = (Zombie) world.spawnEntity(spawnLocation, EntityType.ZOMBIE);

            // Konfiguration des Zombies
            setupBasicZombieAttributes(zombie, difficultyLevel);
            enhanceZombieAttributes(zombie, difficultyLevel);
            giveZombieSpecialAbilities(zombie, difficultyLevel);
            applyVisualEffects(zombie, difficultyLevel);
            zombie.setCanPickupItems(false);

            // Sonderfall für Zombies im Wasser
            if (spawnLocation.getBlock().getType() == Material.WATER) {
                zombie.getEquipment().setItemInMainHand(new ItemStack(Material.TRIDENT));
            }
        }
    }
    // Beispielmethoden
    private int calculateMaxZombies(int difficultyLevel) {
        int baseMax = difficultyLevel * 10; // Erhöhe diesen Wert für mehr Zombies
        int absoluteMax = 1220;
        return Math.min(baseMax, absoluteMax);
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
        ItemStack sword = new ItemStack(Material.STONE_SWORD);

        // Je nach Schwierigkeitsgrad unterschiedliche Ausrüstung
        if (difficultyLevel >= 30) {
            helmet = new ItemStack(Material.IRON_HELMET);
            sword = new ItemStack(Material.IRON_SWORD);
        } else if (difficultyLevel >= 60) {
            helmet = new ItemStack(Material.DIAMOND_HELMET);
            sword = new ItemStack(Material.DIAMOND_SWORD);
        }

        zombie.getEquipment().setHelmet(helmet);
        zombie.getEquipment().setItemInMainHand(sword);

        double maxHealth = 20.0;
        // Je nach Schwierigkeitsgrad unterschiedliche Gesundheit
        if (difficultyLevel >= 30) {
            maxHealth *= 1.5;
        } else if (difficultyLevel >= 60) {
            maxHealth *= 2.0;
        }

        zombie.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(maxHealth);
        zombie.setHealth(maxHealth);
    }

    private void enhanceZombieAttributes(Zombie zombie, int difficultyLevel) {
        // Zusätzliche Verstärkungen basierend auf Schwierigkeitsgrad
        if (difficultyLevel >= 30) {
            zombie.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, Integer.MAX_VALUE, 1));
        }
        if (difficultyLevel >= 60) {
            zombie.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
        }
        if (difficultyLevel >= 90) {
            zombie.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, 1));
        }
    }

    private void giveZombieSpecialAbilities(Zombie zombie, int difficultyLevel) {
        // Spezialfähigkeiten basierend auf Schwierigkeitsgrad
        if (difficultyLevel >= 60) {
            zombie.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0));
        }
        if (difficultyLevel >= 70) {
            zombie.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, Integer.MAX_VALUE, 3));
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
        // Konfigurationen für verschiedene Schwierigkeitsgrade oder Event-Wahrscheinlichkeiten hinzufügen
        zombieConfigurations.add(new ZombieConfiguration(new ItemStack(Material.IRON_HELMET),
                Arrays.asList(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 600, 1))));
        zombieConfigurations.add(new ZombieConfiguration(new ItemStack(Material.DIAMOND_HELMET),
                Arrays.asList(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 600, 2),
                        new PotionEffect(PotionEffectType.SPEED, 600, 1))));
        // Fügen Sie weitere Konfigurationen für andere Schwierigkeitsgrade oder Event-Wahrscheinlichkeiten hinzu
    }

    private void spawnWave(int wave, Location location) {
        World world = location.getWorld();
        int numberOfEnemies = 5 + wave * 2; // Anzahl der Gegner pro Welle erhöhen

        for (int i = 0; i < numberOfEnemies; i++) {
            Zombie zombie = (Zombie) world.spawnEntity(location, EntityType.ZOMBIE);
            // Zufällige Konfiguration auswählen
            ZombieConfiguration randomConfiguration = getRandomZombieConfiguration();
            equipZombieWithConfiguration(zombie, randomConfiguration);
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

                // Belohnungen an Spieler verteilen
                player.getInventory().addItem(silverStack);
                player.sendMessage(ChatColor.GREEN + "Du hast " + randomSilverAmount + " Silber als Belohnung erhalten!");
            }
        }
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




}


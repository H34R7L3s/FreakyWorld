package h34r7l3s.freakyworld;

//import io.th0rgal.oraxen.api.OraxenItems;
import io.th0rgal.oraxen.api.OraxenItems;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class ArmorEnhancements implements Listener {

    private enum ArmorType {
        SKY, FIRE, WATER, STONE, NONE
    }

    private final JavaPlugin plugin;
    private final HashMap<UUID, BossBar> playerBossBars = new HashMap<>();
    private final HashMap<UUID, Float> bossBarProgress = new HashMap<>();
    private final Random random = new Random();

    public ArmorEnhancements(JavaPlugin plugin) {
        this.plugin = plugin;
        startArmorEffectLoop();

        // Periodically check players' armor
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    checkPlayerArmor(player);
                }
            }
        }.runTaskTimer(plugin, 0L, 100L); // Check every 5 seconds (100 ticks)

        // Periodically change BossBar appearance for shimmering effect
        new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID uuid : playerBossBars.keySet()) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player == null || !player.isOnline()) {
                        // Der Spieler ist offline, gehe zum nächsten Spieler in der Schleife
                        continue;
                    }

                    BossBar bossBar = playerBossBars.get(uuid);
                    if (bossBar.getColor() == BarColor.PURPLE) {
                        bossBar.setColor(BarColor.YELLOW);
                        bossBar.setStyle(BarStyle.SEGMENTED_10);
                        bossBar.setTitle("LEGENDARY ITEM");
                        bossBar.setProgress(0); // Start the progress at 0
                        bossBarProgress.put(uuid, 0f);
                    } else {
                        bossBar.setColor(BarColor.PURPLE);
                        bossBar.setStyle(BarStyle.SOLID);
                        ArmorType armorType = getFullArmorSetType(Bukkit.getPlayer(uuid).getInventory().getArmorContents());
                        bossBar.setTitle(getArmorTitle(armorType));
                        bossBar.setProgress(1.0); // Set the progress to full
                        bossBarProgress.remove(uuid); // Remove the player from progress tracking
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 120L);

        // Periodically update the progress for the LEGENDARY ITEM bossbar
        new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID uuid : bossBarProgress.keySet()) {
                    BossBar bossBar = playerBossBars.get(uuid);
                    if (bossBar == null) {
                        continue; // Skip this iteration if the BossBar is null
                    }
                    float progress = bossBarProgress.get(uuid);

                    if (progress < 0.5) {
                        progress += 0.05; // Increase the progress by 5% every tick until 50%
                    } else if (progress < 1) {
                        progress += 0.025; // Slow down the increase from 50% to 100%
                    } else {
                        progress = 1;
                    }

                    progress = Math.min(1.0f, Math.max(0.0f, progress));
                    bossBar.setProgress(progress);
                }
            }
        }.runTaskTimer(plugin, 0L, 2L); // Update every 2 ticks (1/10th of a second)
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            Bukkit.getScheduler().runTaskLater(plugin, () -> checkPlayerArmor(player), 1L);
        }
    }

    private final HashMap<UUID, Long> lastSneakTime = new HashMap<>();
    private final HashMap<UUID, Long> lastBoostTime = new HashMap<>();
    private final HashMap<UUID, Integer> availableBoosts = new HashMap<>();
    private final long DOUBLE_CLICK_INTERVAL = 50000; // 500ms or 0.5 seconds
    private final long BOOST_COOLDOWN = 4280; // 5000ms or 5 seconds
    private final int MAX_BOOSTS = 2;

    private Map<UUID, Long> lastBoostedPlayers = new HashMap<>();
    private static final long JUMP_BOOST_INTERVAL = 1750; //
    private Map<UUID, Boolean> hasPlayerPressedJump = new HashMap<>();
    private Map<UUID, Long> initialBoostTime = new HashMap<>();


    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        ArmorType armorType = getFullArmorSetType(player.getInventory().getArmorContents());

        long currentTime = System.currentTimeMillis();

        // Initialize available boosts for the player if not already set
        availableBoosts.putIfAbsent(player.getUniqueId(), MAX_BOOSTS);

        // Check if the player is wearing a full set of a specific type of armor
        if (armorType != ArmorType.NONE && event.isSneaking()) {
            // Check if the player has sneaked recently (double click detection)
            if (lastSneakTime.containsKey(player.getUniqueId()) && (currentTime - lastSneakTime.get(player.getUniqueId()) <= DOUBLE_CLICK_INTERVAL)) {
                // Check if player has available boosts
                if (availableBoosts.get(player.getUniqueId()) > 0) {
                    Vector direction = player.getLocation().getDirection().multiply(2); // Speed multiplier
                    player.setVelocity(direction);

                    // Nachdem der Boost-Effekt ausgelöst wurde:
                    lastBoostedPlayers.put(player.getUniqueId(), System.currentTimeMillis());
                    initialBoostTime.put(player.getUniqueId(), System.currentTimeMillis());

                    hasPlayerPressedJump.put(player.getUniqueId(), false);
                    lastSneakTime.put(player.getUniqueId(), currentTime);



                    // Decrease the available boosts by 1
                    availableBoosts.put(player.getUniqueId(), availableBoosts.get(player.getUniqueId()) - 1);

                    // If all boosts are used up, reset the cooldown
                    if (availableBoosts.get(player.getUniqueId()) == 0) {
                        lastBoostTime.put(player.getUniqueId(), currentTime);
                    }
                } else {
                    // Reset available boosts after cooldown
                    if (!lastBoostTime.containsKey(player.getUniqueId()) || (currentTime - lastBoostTime.get(player.getUniqueId()) >= BOOST_COOLDOWN)) {
                        availableBoosts.put(player.getUniqueId(), MAX_BOOSTS);
                    }
                }
            }
            lastSneakTime.put(player.getUniqueId(), currentTime);

        }
    }
    @EventHandler
    public void onPlayerJump(PlayerJumpEvent event) {
        Player player = event.getPlayer();
        long currentTime = System.currentTimeMillis();

        // Überprüfen Sie, ob der Spieler kürzlich einen Boost verwendet hat
        if (lastBoostedPlayers.containsKey(player.getUniqueId()) && (currentTime - lastBoostedPlayers.get(player.getUniqueId()) <= JUMP_BOOST_INTERVAL)) {
            // Überprüfen Sie, ob der Spieler Boosts verfügbar hat
            if (availableBoosts.get(player.getUniqueId()) > 0) {
                // Boost-Effekt nach oben anwenden
                Vector upBoost = new Vector(0, 1, 0).multiply(2); // Stärke des Boosts
                player.setVelocity(upBoost);

                // Verringern Sie die verfügbaren Boosts um 1
                availableBoosts.put(player.getUniqueId(), availableBoosts.get(player.getUniqueId()) - 1);

                // Apply Slow Falling effect
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 500, 0)); // Dauer in Ticks, 100 Ticks = 5 Sekunden, Stärke 0

            }

        }
    }
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        long currentTime = System.currentTimeMillis();

        // Überprüfen Sie, ob der Spieler in der Luft ist und die Leertaste gedrückt hat
        if (!player.isOnGround() && player.getVelocity().getY() > 0) {
            if (lastBoostedPlayers.containsKey(player.getUniqueId()) &&
                    (currentTime - lastBoostedPlayers.get(player.getUniqueId()) <= JUMP_BOOST_INTERVAL) &&
                    (currentTime - initialBoostTime.getOrDefault(player.getUniqueId(), 0L) >= 500) && // Warten Sie mindestens 500ms

                    !hasPlayerPressedJump.getOrDefault(player.getUniqueId(), false)) {
                // Der Spieler hat die Leertaste gedrückt, nachdem er den Boost aktiviert hat
                hasPlayerPressedJump.put(player.getUniqueId(), true);
                Bukkit.getPluginManager().callEvent(new PlayerJumpEvent(player));
            }
        }
    }




    private void checkPlayerArmor(Player player) {
        ItemStack[] armor = player.getInventory().getArmorContents();
        ArmorType armorType = getFullArmorSetType(armor);

        if (armorType != ArmorType.NONE) {
            displayArmorEffects(player, armorType);

            if (!playerBossBars.containsKey(player.getUniqueId())) {
                BossBar bossBar = Bukkit.createBossBar(getArmorTitle(armorType), BarColor.BLUE, BarStyle.SOLID);
                bossBar.addPlayer(player);
                playerBossBars.put(player.getUniqueId(), bossBar);
            }
        } else {
            if (playerBossBars.containsKey(player.getUniqueId())) {
                BossBar bossBar = playerBossBars.get(player.getUniqueId());
                bossBar.removePlayer(player);
                bossBar.setVisible(false);
                playerBossBars.remove(player.getUniqueId());
            }
        }
    }

    private ArmorType getFullArmorSetType(ItemStack[] armor) {
        ArmorType type = getArmorType(armor[0]);
        for (int i = 1; i < armor.length; i++) {
            if (getArmorType(armor[i]) != type) {
                return ArmorType.NONE;
            }
        }
        return type;
    }

    private ArmorType getArmorType(ItemStack item) {
        if (item == null) {
            return ArmorType.NONE;
        }

        String oraxenId = OraxenItems.getIdByItem(item);
        if (oraxenId == null) {
            return ArmorType.NONE;
        }

        switch (oraxenId) {
            case "sky_crown":
            case "sky_guard":
            case "sky_leggings":
            case "sky_boots":
                return ArmorType.SKY;
            case "fire_crown":
            case "fire_guard":
            case "fire_leggings":
            case "fire_boots":
                return ArmorType.FIRE;
            case "water_crown":
            case "water_guard":
            case "water_leggings":
            case "water_boots":
                return ArmorType.WATER;
            case "stone_crown":
            case "stone_guard":
            case "stone_leggings":
            case "stone_boots":
                return ArmorType.STONE;
            default:
                return ArmorType.NONE;
        }
    }



    private String getArmorTitle(ArmorType armorType) {
        switch (armorType) {
            case SKY:
                return "Rüstung des Himmels";
            case FIRE:
                return "Rüstung des Feuers";
            case WATER:
                return "Rüstung des Meeres";
            case STONE:
                return "Rüstung des Berges";
            default:
                return "Unbekannte Rüstung";
        }
    }


    private void startArmorEffectLoop() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    ArmorType armorType = getFullArmorSetType(player.getInventory().getArmorContents());
                    if (armorType != ArmorType.NONE) {
                        displayArmorEffects(player, armorType);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // Every second (20 ticks)
    }


    private void displayArmorEffects(Player player, ArmorType armorType) {
        Location loc = player.getLocation().add(0, 1, 0); // Centered on the player, slightly raised
        double offsetX = (Math.random() - 0.5) * 2; // Random value between -1 and 1
        double offsetY = (Math.random() - 0.5) * 2;
        double offsetZ = (Math.random() - 0.5) * 2;

        switch (armorType) {
            case SKY:
                loc.getWorld().spawnParticle(Particle.CLOUD, loc, 10, offsetX, offsetY, offsetZ, 0.05);

                activateTempestWrath(player);



                break;
            case FIRE:
                loc.getWorld().spawnParticle(Particle.FLAME, loc, 10, offsetX, offsetY, offsetZ, 0.05);
                applyHeatSourceHealing(player);
                applyFireBoost(player);
                emitHeatPulse(player);
                applyLavaSpeedBoost(player);
                displayFireAura(player);


                break;
            case WATER:
                loc.getWorld().spawnParticle(Particle.DRIPPING_WATER, loc, 10, offsetX, offsetY, offsetZ, 0.05);
                applyAquaRegeneration(player);
                applyWaterWalking(player);
                applyHydrationBoost(player);
                applyRainEmpowerment(player);
                applyWaterBreathing(player);

                break;
            case STONE:
                loc.getWorld().spawnParticle(Particle.DUST, loc, 10, offsetX, offsetY, offsetZ, 0.05, Material.STONE.createBlockData());


                break;
            default:
                break;
        }
    }

//Einzel Effekte der jew. Rüstungs-Sets:
    //////  FIRE ///////////
    ////////////////////////
    //Feuerresistenz
    // Wenn in Lava = Regeneration?
    // Wenn im Nether/LAVA/FEUER dann...
    // ÜBERLEGUNG: Passiver DMG EFFEKT? - verbrennen (Player, Hostile / Entitys(Mobs wie Pig, Sheep, usw))
    // --- Feuerschutzring
    // ENTITY_MOUNT Ghast

    //////  Water ///////////
    ////////////////////////
    // Delfine Effekt
    // Wenn in Wasser = Regeneration?
    // Wenn im Wasser dann...
    // -- Night Vision
    // ÜBERLEGUNG: Passiver DMG EFFEKT? - ertrinken
    // --- Wasserschutzring
    // Wasseratmung / canBreatheUnderwater()
    // ENTITY_MOUNT Delfin

    //////  Stone ///////////
    ////////////////////////
    // Night Vision? // Haste?
    // Wenn Block über Spieler gefunden = Regeneration?  <<<< Fraglich
    // Wenn am Abbauen dann...
    // ÜBERLEGUNG: Passiver DMG EFFEKT? - ersticken
    // --- Giftwolke
    // Sättigung dafür MINING_FATIGUE / INFESTED?
    // ENTITY_MOUNT Silberfisch

    //////  AIR ///////////
    ////////////////////////
    // Geschwindigkeit? // ELYTRA_GLIDE ?
    // Wenn kein Block über Spieler gefunden = Regeneration?  <<<< Fraglich
    // Wenn am Fliegen? / Wenn am Sprinten oder Springen dann....
    // ÜBERLEGUNG: Passiver DMG EFFEKT? - true damage HURT_BERRY_BUSH
    // --- Giftwolke
    // Jump Boost // hasGravity()
    // ENTITY_MOUNT Phantom


    //////
    //////



    /////////////
    // FIRE RÜSTUNG

    // 1. Erweiterte Feuerresistenz und Regeneration durch Hitzequellen
    private void applyHeatSourceHealing(Player player) {
        Block blockUnderPlayer = player.getLocation().getBlock();
        boolean isNearHeatSource = blockUnderPlayer.getType() == Material.LAVA ||
                blockUnderPlayer.getType() == Material.FIRE ||
                blockUnderPlayer.getType() == Material.CAMPFIRE ||
                blockUnderPlayer.getType() == Material.SOUL_CAMPFIRE;

        if (isNearHeatSource) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 0, true, false));
            spawnHealingParticles(player.getLocation());
        }
    }

    private void spawnHealingParticles(Location location) {
        location.getWorld().spawnParticle(Particle.FLAME, location, 20, 0.5, 1.0, 0.5, 0.1);
        location.getWorld().spawnParticle(Particle.LARGE_SMOKE, location, 10, 0.3, 0.6, 0.3, 0.1);
    }

    // 2. Angriffsschub durch Feuer
    private void applyFireBoost(Player player) {
        if (player.getFireTicks() > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, 1, true, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1, true, false));
            spawnFireBoostParticles(player.getLocation());
        }
    }

    private void spawnFireBoostParticles(Location location) {
        location.getWorld().spawnParticle(Particle.EXPLOSION, location, 5, 1.0, 1.0, 1.0, 0.1);
        location.getWorld().spawnParticle(Particle.LAVA, location, 15, 0.5, 1.0, 0.5, 0.1);
    }

    // 3. Hitzestoß (alle 5 Sekunden)
    private void emitHeatPulse(Player player) {
        Location location = player.getLocation();
        double radius = 5.0;

        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                ((LivingEntity) entity).setFireTicks(60); // 3 Sekunden brennen
                spawnHeatPulseParticles(entity.getLocation());
            }
        }
    }

    private void spawnHeatPulseParticles(Location location) {
        location.getWorld().spawnParticle(Particle.FLAME, location, 50, 1.0, 1.0, 1.0, 0.05);
        location.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, location, 25, 1.0, 1.0, 1.0, 0.02);
    }

    // 4. Lava-Boost für Geschwindigkeit und Sprungkraft
    private void applyLavaSpeedBoost(Player player) {
        if (player.getLocation().getBlock().getType() == Material.LAVA) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1, true, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 100, 1, true, false));
            spawnLavaSpeedBoostParticles(player.getLocation());
        }
    }

    private void spawnLavaSpeedBoostParticles(Location location) {
        location.getWorld().spawnParticle(Particle.FLAME, location, 20, 0.5, 0.5, 0.5, 0.05);
        location.getWorld().spawnParticle(Particle.LAVA, location, 20, 0.3, 0.5, 0.3, 0.05);
    }

    // 5. Flammenschild bei Nahkampfangriffen
    @EventHandler
    public void onPlayerAttacked(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();

            // Überprüfen, ob der Spieler ein vollständiges Fire-Rüstungsset trägt
            if (isWearingFullArmor(player, ArmorType.FIRE) && event.getDamager() instanceof LivingEntity) {
                LivingEntity attacker = (LivingEntity) event.getDamager();
                attacker.setFireTicks(40); // Gegner in Brand setzen
                spawnFlameShieldParticles(attacker.getLocation());
            }
        }
    }

    // Überprüft, ob ein Spieler ein vollständiges Set eines bestimmten ArmorType trägt
    private boolean isWearingFullArmor(Player player, ArmorType type) {
        ItemStack[] armor = player.getInventory().getArmorContents();
        return getFullArmorSetType(armor) == type;
    }

    private void spawnFlameShieldParticles(Location location) {
        location.getWorld().spawnParticle(Particle.FLAME, location, 15, 0.3, 0.3, 0.3, 0.02);
        location.getWorld().playSound(location, Sound.ITEM_FIRECHARGE_USE, 1.0f, 1.0f);
    }

    // 6. Feuer-Aura um den Spieler
    private void displayFireAura(Player player) {
        Location location = player.getLocation().add(0, 1, 0);
        player.getWorld().spawnParticle(Particle.FLAME, location, 10, 0.5, 0.5, 0.5, 0.05);
        player.getWorld().spawnParticle(Particle.SMOKE, location, 5, 0.3, 0.3, 0.3, 0.01);
    }

    //////
    // Water Rüstung
    //////
    private void applyAquaRegeneration(Player player) {
        if (player.getLocation().getBlock().isLiquid()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1, true, true, true));
        }
    }
    private void applyWaterWalking(Player player) {
        Location loc = player.getLocation();
        Block blockBelow = loc.clone().subtract(0, 1, 0).getBlock();

        // Bedingungen für Wasserlaufen: Nur aktivieren, wenn der Spieler sich direkt an der Oberfläche des Wassers befindet
        if (loc.getBlock().getType() == Material.WATER && blockBelow.getType() != Material.WATER) {
            player.setAllowFlight(true);
            player.setFlying(true);
            player.setGliding(true); // Aktiviert das Gleiten für eine weiche Bewegung auf Wasser

            // Wasserpartikel um den Spieler für visuelles Feedback
            spawnWaterParticles(player.getLocation());

        } else if (loc.getBlock().getType() != Material.WATER) {
            // Flugmodus deaktivieren, wenn der Spieler nicht auf der Wasseroberfläche ist
            player.setAllowFlight(false);
            player.setFlying(false);
            player.setGliding(false);
        } else if (player.isSneaking()) {
            // Spieler kann durch Sneaken unter die Wasseroberfläche tauchen und die Effekte deaktivieren
            player.setAllowFlight(false);
            player.setFlying(false);
            player.setGliding(false);
        }
    }

    private void spawnWaterParticles(Location location) {
        World world = location.getWorld();
        world.spawnParticle(Particle.BUBBLE, location, 20, 0.5, 1, 0.5, 0.05); // Blasen-Effekt
        world.spawnParticle(Particle.SPLASH, location, 15, 0.5, 0.5, 0.5, 0.1); // Spritz-Effekt
    }
    private void applyWaterBreathing(Player player) {
        if (player.getLocation().getBlock().isLiquid()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 200, 1, true, false, false));
        } else {
            player.removePotionEffect(PotionEffectType.WATER_BREATHING); // Wasseratmung entfernen, wenn nicht im Wasser
        }
    }
    @EventHandler
    public void onPlayerAttackedByMob(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player && event.getDamager() instanceof LivingEntity) {
            Player player = (Player) event.getEntity();
            if (isWearingFullArmor(player, ArmorType.WATER)) {
                LivingEntity attacker = (LivingEntity) event.getDamager();
                Vector pullDirection = player.getLocation().toVector().subtract(attacker.getLocation().toVector()).normalize().multiply(0.5);
                attacker.setVelocity(pullDirection); // Gegner wird zum Spieler hingezogen
                spawnWaterSplashParticles(attacker.getLocation());
            }
        }
    }

    private void spawnWaterSplashParticles(Location location) {
        location.getWorld().spawnParticle(Particle.SPLASH, location, 30, 1, 1, 1, 0.1);
    }
    private void applyHydrationBoost(Player player) {
        if (player.getLocation().getBlock().isLiquid()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 200, 0, true, true, true));
        }
    }

    private void applyRainEmpowerment(Player player) {
        if (player.getWorld().hasStorm()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, 0, true, true, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 0, true, true, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 0, true, true, true));
        }
    }
    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (isWearingFullArmor(player, ArmorType.WATER)) {
                spawnBubbleShieldParticles(player.getLocation());
                event.setDamage(event.getDamage() * 0.8); // 20% Schaden absorbieren
            }
        }
    }

    private void spawnBubbleShieldParticles(Location location) {
        location.getWorld().spawnParticle(Particle.BUBBLE, location, 40, 1, 1, 1, 0.1);
    }

    //////
    ////// Stone Rüstung

    @EventHandler
    public void onPlayerDamageStone(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (isWearingFullArmor(player, ArmorType.STONE)) {
                event.setDamage(event.getDamage() * 0.85); // 15% Schadensreduktion
                player.setVelocity(player.getVelocity().multiply(0.5)); // Reduziertes Zurückstoßen
                spawnStoneParticles(player.getLocation());
            }
        }
    }

    private void spawnStoneParticles(Location location) {
        location.getWorld().spawnParticle(Particle.DUST, location, 20, 0.5, 1, 0.5, Material.STONE.createBlockData());
    }
    @EventHandler
    public void onPlayerFall(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            Player player = (Player) event.getEntity();
            if (isWearingFullArmor(player, ArmorType.STONE) && event.getFinalDamage() > 5) { // Nur bei größeren Stürzen
                createEarthquake(player.getLocation());
                event.setDamage(event.getDamage() * 0.6); // Verringert den Fallschaden
            }
        }
    }

    private void createEarthquake(Location location) {
        World world = location.getWorld();
        world.playSound(location, Sound.ENTITY_WITHER_BREAK_BLOCK, 1.0f, 0.8f);
        world.spawnParticle(Particle.DUST, location, 30, 2, 0.5, 2, Material.COBBLESTONE.createBlockData());
        for (Entity entity : world.getNearbyEntities(location, 5, 5, 5)) {
            if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                ((LivingEntity) entity).addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2)); // Gegner werden verlangsamt
            }
        }
    }
    @EventHandler
    public void onPlayerAttackedStone(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (isWearingFullArmor(player, ArmorType.STONE)) {
                // Temporäre Rüstungsschicht hinzufügen
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 1, true, true, true));
                spawnRockArmorParticles(player.getLocation());
            }
        }
    }

    private void spawnRockArmorParticles(Location location) {
        location.getWorld().spawnParticle(Particle.DUST, location, 20, 1.0, 1.0, 1.0, Material.STONE.createBlockData());
    }


    @EventHandler
    public void onPlayerEffect(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (isWearingFullArmor(player, ArmorType.STONE)) {
                //player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 200, 0, true, true, true)); // Erhöhte Stabilität
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 1, true, true, true));
            }
        }
    }

    @EventHandler
    public void onPlayerAttackedByMobStone(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player && event.getDamager() instanceof LivingEntity) {
            Player player = (Player) event.getEntity();
            LivingEntity attacker = (LivingEntity) event.getDamager();
            if (isWearingFullArmor(player, ArmorType.STONE) && new Random().nextInt(4) == 0) { // 25% Chance
                attacker.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1)); // Gegner wird verlangsamt
                spawnPetrifyEffect(attacker.getLocation());
            }
        }
    }

    private void spawnPetrifyEffect(Location location) {
        location.getWorld().spawnParticle(Particle.LARGE_SMOKE, location, 20, 0.5, 1, 0.5, 0.1);
        location.getWorld().playSound(location, Sound.BLOCK_STONE_BREAK, 1.0f, 0.8f);
    }


    /////
    ////// Sky Rüstung
    // Sky Armor-specific boost and glide mechanics
    // Sky Armor-specific glide mechanics
    private final Map<UUID, Long> cloudWalkerCooldowns = new HashMap<>();
    private static final long CLOUD_WALKER_COOLDOWN = 5000; // 5 seconds cooldown
    private static final int GLIDE_DURATION = 800; // Glide duration in ticks
    private static final int DESCENT_HOLD_DURATION = 50; // 2.5 seconds in ticks

    // Track airborne status and shift hold for descent activation
    private final Map<UUID, Long> shiftHoldStartTime = new HashMap<>();

    @EventHandler
    public void onPlayerJumpSkyRivals(PlayerJumpEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Activate Sky Armor glide mechanics if wearing Sky Armor
        if (isWearingFullArmor(player, ArmorType.SKY)) {
            long currentTime = System.currentTimeMillis();

            // Respect cooldown before initiating another glide
            if (cloudWalkerCooldowns.containsKey(playerId) && currentTime - cloudWalkerCooldowns.get(playerId) < CLOUD_WALKER_COOLDOWN) {
                return;
            }

            // Start gliding with Riptide effect
            initiateSkyGlide(player);
            cloudWalkerCooldowns.put(playerId, currentTime); // Set cooldown
        }
    }

    private void initiateSkyGlide(Player player) {
        // Start Riptide visual effect and enter gliding mode
        player.setRiptiding(true); // Activate Riptide effect for animation
        player.setGliding(true); // Enter glide mode for smooth control
        spawnCloudTrailParticles(player.getLocation()); // Visual trail effect

        // Play sound to enhance feedback
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1.0f, 1.0f);

        // Slow Falling for smooth glide experience
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, GLIDE_DURATION, 1, true, false, false));

        // Listen for Shift hold to trigger descent after gliding
        new BukkitRunnable() {
            int ticksElapsed = 0;

            @Override
            public void run() {
                ticksElapsed++;

                // Stop if the player lands
                if (player.isOnGround()) {
                    player.setRiptiding(false); // Stop Riptide animation on landing
                    player.removePotionEffect(PotionEffectType.SLOW_FALLING);
                    this.cancel();
                    return;
                }

                // Check if Shift is held long enough for descent
                if (player.isSneaking() && ticksElapsed >= DESCENT_HOLD_DURATION) {
                    activateRiptideDescent(player);
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 5L); // Check every 5 ticks
    }

    private void activateRiptideDescent(Player player) {
        // End gliding mode and start Riptide descent animation
        player.setGliding(false);
        player.setRiptiding(true); // Visual Riptide descent effect
        player.removePotionEffect(PotionEffectType.SLOW_FALLING);

        // Sharp downward boost for Riptide descent
        player.setVelocity(new Vector(0, -2.5, 0));
        player.playEffect(EntityEffect.HURT); // Spin effect during descent
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_3, 1.0f, 1.0f);
        spawnFallTrailParticles(player.getLocation());

        // Reset cooldown for the next glide
        cloudWalkerCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
    }

    // Cloud particle trail for gliding
    private void spawnCloudTrailParticles(Location location) {
        for (int i = 0; i < 10; i++) {
            Location particleLoc = location.clone().add(
                    (Math.random() - 0.5) * 2,
                    Math.random() * 1.5,
                    (Math.random() - 0.5) * 2
            );
            location.getWorld().spawnParticle(Particle.CLOUD, particleLoc, 5, 0.2, 0.2, 0.2, 0.01);
        }
    }

    // Particles for Riptide descent trail
    private void spawnFallTrailParticles(Location location) {
        for (int i = 0; i < 15; i++) {
            Location particleLoc = location.clone().add(
                    (Math.random() - 0.5) * 1.5,
                    Math.random() * 1.0,
                    (Math.random() - 0.5) * 1.5
            );
            location.getWorld().spawnParticle(Particle.SWEEP_ATTACK, particleLoc, 3, 0.2, 0.2, 0.2, 0.01);
        }
    }




    @EventHandler
    public void onPlayerAttackedSky(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (isWearingFullArmor(player, ArmorType.SKY) && event.getDamager() instanceof LivingEntity) {
                LivingEntity attacker = (LivingEntity) event.getDamager();
                attacker.setVelocity(attacker.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(1.5));
                spawnWindGustParticles(player.getLocation());
                player.getWorld().playSound(player.getLocation(), Sound.ITEM_ELYTRA_FLYING, 1.0f, 1.0f);
            }
        }
    }

    private void spawnWindGustParticles(Location location) {
        location.getWorld().spawnParticle(Particle.SWEEP_ATTACK, location, 20, 1.0, 1.0, 1.0, 0.1);
    }

    @EventHandler
    public void onPlayerJumpSky(PlayerJumpEvent event) {
        Player player = event.getPlayer();
        if (isWearingFullArmor(player, ArmorType.SKY)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 100, 2, true, true));
            //player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 100, 1, true, true));
            spawnFeatherParticles(player.getLocation());
        }
    }

    private void spawnFeatherParticles(Location location) {
        location.getWorld().spawnParticle(Particle.END_ROD, location, 20, 0.5, 1.0, 0.5, 0.05);
    }

    @EventHandler
    public void onPlayerTakeDamageSky(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (isWearingFullArmor(player, ArmorType.SKY) && player.isGliding()) {
                event.setDamage(event.getDamage() * 0.7); // 30% weniger Schaden
                spawnSkyEmbraceParticles(player.getLocation());
                player.getWorld().playSound(player.getLocation(), Sound.ITEM_ELYTRA_FLYING, 1.0f, 1.2f);
            }
        }
    }

    private void spawnSkyEmbraceParticles(Location location) {
        location.getWorld().spawnParticle(Particle.ITEM_SNOWBALL, location, 15, 0.5, 0.5, 0.5, 0.1);
    }

    @EventHandler
    public void onPlayerMoveSky(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (isWearingFullArmor(player, ArmorType.SKY)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20, 1, true, false, false)); // Permanente Bewegungsgeschwindigkeit
            spawnAirTrailParticles(player.getLocation());
        }
    }

    private void spawnAirTrailParticles(Location location) {
        location.getWorld().spawnParticle(Particle.SPIT, location, 5, 0.2, 0.2, 0.2, 0.02);
    }

    public void activateTempestWrath(Player player) {
        //player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PHANTOM_SWOOP, 1.0f, 1.0f);
        player.getWorld().spawnParticle(Particle.EFFECT, player.getLocation(), 1);

        for (Entity entity : player.getNearbyEntities(5, 5, 5)) {
            if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                LivingEntity target = (LivingEntity) entity;
                target.setVelocity(target.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(1.8));
                target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 60, 1)); // Verwirrt Feinde für kurze Zeit
            }
        }
    }




}

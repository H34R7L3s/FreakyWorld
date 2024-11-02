package h34r7l3s.freakyworld;

import io.th0rgal.oraxen.api.OraxenItems;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.boss.BossBar;
import org.bukkit.boss.BarColor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.bukkit.boss.DragonBattle;
import org.bukkit.util.Vector;

import static org.bukkit.Bukkit.getServer;

public class FreakyDragon implements Listener {

    private final EnderDragon dragon;
    private final World world;
    private final Location spawnLocation;
    private final Random random = new Random();
    private final JavaPlugin plugin;
    private final String difficulty;

    // Drop-Items mit Wahrscheinlichkeiten
    private final Map<ItemStack, Double> oraxenDropItems = new HashMap<>();
    //private final DragonBattle enderDragonBattle;
    private final BossBar bossBar;

    public FreakyDragon(World world, Location spawnLocation, String difficulty, JavaPlugin plugin) {
        this.world = world;
        this.spawnLocation = spawnLocation;
        this.plugin = plugin;
        this.difficulty = difficulty;

        // Erstelle und konfiguriere die Bossleiste
        bossBar = Bukkit.createBossBar(
                ChatColor.DARK_RED + "Freaky-Drache", // Titel der Bossleiste
                BarColor.RED, // Farbe
                BarStyle.SEGMENTED_10, // Stil (10 Segmente)
                BarFlag.CREATE_FOG // Optional: Fügt Nebeleffekt hinzu
        );
        bossBar.setVisible(true); // Setze die Bossleiste sichtbar


// Füge alle Spieler zur Bossleiste hinzu
        for (Player player : world.getPlayers()) {
            bossBar.addPlayer(player);
        }
        // Respawn von Ender-Kristallen und Türmen


        respawnTowersAndCrystals();
        respawnTowersAndCrystalsRivals();


        startArtilleryAttack();
        startLaserBeamAttack();
        startVortexAttack();

        // Füge Oraxen-Items mit ihren Wahrscheinlichkeiten hinzu (Chance von 0.0 bis 1.0)
        oraxenDropItems.put(OraxenItems.getItemById("freaky_schutz").build(), 0.1);  // 10% Chance
        oraxenDropItems.put(OraxenItems.getItemById("freaky_scharf").build(), 0.2);  // 20% Chance
        oraxenDropItems.put(OraxenItems.getItemById("freaky_break").build(), 0.15);  // 15% Chance
        oraxenDropItems.put(OraxenItems.getItemById("freaky_effi").build(), 0.05);   // 5% Chance
        oraxenDropItems.put(OraxenItems.getItemById("freaky_lucky").build(), 0.3);   // 30% Chance
        oraxenDropItems.put(OraxenItems.getItemById("freaky_plunder").build(), 0.2); // 20% Chance

        // Spawn the dragon
        dragon = (EnderDragon) world.spawnEntity(spawnLocation, EntityType.ENDER_DRAGON);
        dragon.setCustomName(ChatColor.RED + "Freaky-Drache");
        dragon.setAI(true);
        dragon.setPhase(EnderDragon.Phase.CHARGE_PLAYER);
        dragon.setPhase(EnderDragon.Phase.SEARCH_FOR_BREATH_ATTACK_TARGET);
        // Respawn von Ender-Kristallen und Türmen

        //world.getEnderDragonBattle().resetCrystals(); // Setzt die Türme und End-Kristalle neu


        // Ender Türme + Kristalle





        // Set dragon attributes and effects based on difficulty
        switch (difficulty) {
            case "Einfach":
                configureEasyDragon();
                break;
            case "Schwer":
                configureHardDragon();
                break;
            default:
                throw new IllegalArgumentException("Unbekannter Schwierigkeitsgrad: " + difficulty);
        }
        // Update die Bossleiste basierend auf der Drachen-Gesundheit
        updateBossBar();
        // Apply visual effects
        applyVisualEffects();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

    }

    // Aktualisiere die Bossleiste, um die Gesundheit des Drachen anzuzeigen
    private void updateBossBar() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!dragon.isDead()) {
                    double healthPercent = dragon.getHealth() / dragon.getMaxHealth();
                    bossBar.setProgress(healthPercent); // Setze den Fortschritt der Bossleiste
                } else {
                    bossBar.removeAll(); // Entferne die Bossleiste, wenn der Drache stirbt
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // Aktualisiere alle 20 Ticks (1 Sekunde)
    }
    // Methode zum Zurücksetzen der Türme und Kristalle
    public void respawnTowersAndCrystals() {
        // Definiere die Positionen der Kristalle relativ zur Drachen-Spawn-Position
        List<Location> crystalPositions = new ArrayList<>();
        crystalPositions.add(new Location(world, -2.5, 65, 0.5));
        crystalPositions.add(new Location(world, 0.5, 65, -2.5));
        crystalPositions.add(new Location(world, 3.5, 65, 0.5));
        crystalPositions.add(new Location(world, 0.5, 65, 3.5));




        //world.strikeLightningEffect(dragon.getLocation()); // Optional: Effekt für visuelle Verstärkung
        //world.playSound(dragon.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);

        for (Player player : world.getPlayers()) {
            player.sendMessage(ChatColor.DARK_RED + "Hahaha, meine Verstärkung!!");
        }

        // Spawne die Kristalle und platziere einen Obsidian-Block darunter
        for (Location loc : crystalPositions) {
            Location obsidianLocation = loc.clone().add(0, -2, 0); // 1 Block unter der Kristall-Position
            obsidianLocation.getBlock().setType(Material.BEDROCK); // Setze Obsidian-Block

            EnderCrystal crystal = (EnderCrystal) world.spawnEntity(loc, EntityType.ENDER_CRYSTAL);
            crystal.setInvulnerable(false);


            //crystal.setInvulnerable(false); // Optional: Macht die Kristalle unzerstörbar
        }


    }



    public void respawnTowersAndCrystalsRivals() {
        // Hol die End-Welt, in der der Drache sein sollte
        World endWorld = Bukkit.getWorlds().stream()
                .filter(world -> world.getEnvironment() == World.Environment.THE_END)
                .findFirst()
                .orElse(null);

        if (endWorld == null) {
            Bukkit.getLogger().severe("Die Endwelt konnte nicht gefunden werden.");
            return;
        }

        // Hol das Drachen-Kampf-Objekt für die Endwelt
        DragonBattle dragonBattle = endWorld.getEnderDragonBattle();
        if (dragonBattle == null) {
            Bukkit.getLogger().severe("Kein aktiver Drachenkampf in der Endwelt.");
            return;
        }


        // Setze den Drachen-Kampf-Status auf die Phase zum Aufbau der Türme
        //dragonBattle.setRespawnPhase(DragonBattle.RespawnPhase.START);
        dragonBattle.initiateRespawn();


        // Kristalle und Türme nach und nach spawnen lassen

        dragonBattle.setRespawnPhase(DragonBattle.RespawnPhase.SUMMONING_PILLARS);



    }


    private void startArtilleryAttack() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (dragon.isDead()) {
                    this.cancel();
                    return;
                }

                for (Player player : world.getPlayers()) {
                    if (player.getLocation().distance(dragon.getLocation()) <= 5000) {
                        int attackCount = 2 + random.nextInt(6); // Zwischen 5 und 10 Feuerbälle

                        new BukkitRunnable() {
                            int currentAttack = 0;

                            @Override
                            public void run() {
                                if (currentAttack >= attackCount) {
                                    this.cancel();
                                    return;
                                }

                                Location targetLocation = player.getLocation().clone().add(
                                        random.nextInt(10) - 5,
                                        0,
                                        random.nextInt(10) - 5
                                );

                                world.spawnParticle(Particle.SMOKE_LARGE, targetLocation, 20, 1.0, 0.5, 1.0, 0.1);
                                world.playSound(targetLocation, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.5f);

                                new BukkitRunnable() {
                                    @Override
                                    public void run() {
                                        Fireball fireball = world.spawn(targetLocation.add(0, 15, 0), Fireball.class);
                                        fireball.setDirection(new Vector(0, -1, 0));
                                        fireball.setIsIncendiary(false); // Keine brennenden Flächen hinterlassen
                                        fireball.setYield(2.0f); // Schwächere Explosionsstärke

                                        if (random.nextInt(4) == 0) { // 25% Chance für Drachenatem
                                            AreaEffectCloud cloud = (AreaEffectCloud) world.spawnEntity(targetLocation, EntityType.AREA_EFFECT_CLOUD);
                                            cloud.setRadius(3.0f);
                                            cloud.setDuration(100); // 5 Sekunden
                                            cloud.setParticle(Particle.DRAGON_BREATH);
                                            cloud.setColor(Color.PURPLE);
                                        }
                                    }
                                }.runTaskLater(plugin, 10L); // Verzögerung um 0,5 Sekunden

                                currentAttack++;
                            }
                        }.runTaskTimer(plugin, 0L, 15L); // Intervall von 15 Ticks
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 350L); // Angriff Timer
    }

    private void startLaserBeamAttack() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (dragon.isDead()) {
                    this.cancel();
                    return;
                }

                // Finde Spieler in Reichweite des Drachen
                for (Player player : world.getPlayers()) {
                    if (player.getLocation().distance(dragon.getLocation()) <= 5000) {
                        int beamCount = 11 + random.nextInt(13); //  Strahlenanzahl

                        for (int i = 0; i < beamCount; i++) {
                            Location beamLocation = player.getLocation().clone().add(
                                    random.nextInt(10) - 5,
                                    0,
                                    random.nextInt(10) - 5
                            );

                            // Strahl wird vertikal über die gesamte Höhe der Welt erzeugt und bewegt sich im Kreis
                            new BukkitRunnable() {
                                int beamDuration = 100; // 5 Sekunden (100 Ticks)
                                double angle = 0;

                                @Override
                                public void run() {
                                    if (beamDuration <= 0) {
                                        this.cancel();
                                        return;
                                    }

                                    angle += Math.PI / 16; // Winkelinkrement für Kreisbewegung
                                    double radius = 1.5; // Radius der Partikelrotation

                                    // Erzeuge rote, rotierende Partikel an der Strahlposition
                                    for (int y = beamLocation.getBlockY(); y < world.getMaxHeight(); y++) {
                                        double offsetX = radius * Math.cos(angle);
                                        double offsetZ = radius * Math.sin(angle);

                                        Location particleLocation = new Location(
                                                world,
                                                beamLocation.getX() + offsetX,
                                                y,
                                                beamLocation.getZ() + offsetZ
                                        );

                                        world.spawnParticle(Particle.REDSTONE, particleLocation, 20,
                                                new Particle.DustOptions(Color.RED, 3.5f)); // Mehr Partikel für intensiveren Effekt
                                    }

                                    // Prüfe, ob ein Spieler den Strahl berührt
                                    for (Player p : world.getPlayers()) {
                                        if (Math.abs(p.getLocation().getX() - beamLocation.getX()) < radius &&
                                                Math.abs(p.getLocation().getZ() - beamLocation.getZ()) < radius) {
                                            p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 1)); // 5 Sekunden Vergiftung
                                        }
                                    }

                                    beamDuration -= 10; // Zeit reduzieren
                                }
                            }.runTaskTimer(plugin, 0L, 5L); // Erhöhte Frequenz für flüssigere Animation
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 270L); // Angriff Timer
    }

    private void startVortexAttack() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (dragon.isDead()) {
                    this.cancel();
                    return;
                }

                // Spieler im Umkreis von 30 Blöcken finden
                List<Player> nearbyPlayers = new ArrayList<>();
                for (Player player : dragon.getWorld().getPlayers()) {
                    if (player.getLocation().distance(dragon.getLocation()) <= 3000) {
                        nearbyPlayers.add(player);
                    }
                }

                // Erste Phase: Vortex-Warnung
                for (Player player : nearbyPlayers) {
                    Location vortexLocation = player.getLocation().clone(); // Die Position des Spielers wird gespeichert
                    createWarningParticles(vortexLocation); // Visuelle Warnung mit auffälligen Partikeln

                    // Verzögerung von 2 Sekunden vor dem eigentlichen Angriff
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            // Prüfe, ob der Spieler sich noch in der Nähe des Vortex befindet
                            if (player.getLocation().distance(vortexLocation) <= 3.0) {
                                startVortexEffect(player, vortexLocation); // Vortex-Effekte starten
                            }
                        }
                    }.runTaskLater(plugin, 40L); // 2 Sekunden Verzögerung (40 Ticks)
                }
            }
        }.runTaskTimer(plugin, 0L, 980L); // Angriff Timer
    }

    // Methode zur Erzeugung der Partikel für die Warnphase
    private void createWarningParticles(Location location) {
        World world = location.getWorld();

        new BukkitRunnable() {
            int angleStep = 0;
            int height = 3; // Partikelhöhe in Blockabständen

            @Override
            public void run() {
                if (angleStep >= 360) {
                    this.cancel();
                    return;
                }

                double radius = 3.5; // Größerer Radius für die Warnung
                double angle = Math.toRadians(angleStep);

                for (int yOffset = 0; yOffset <= height; yOffset++) {
                    double x = radius * Math.cos(angle);
                    double z = radius * Math.sin(angle);
                    Location particleLocation = location.clone().add(x, yOffset * 0.5, z);

                    // Auffällige Partikel (grün, gelb, violett) zur visuellen Warnung
                    world.spawnParticle(Particle.REDSTONE, particleLocation, 15,
                            new Particle.DustOptions(Color.GREEN, 2.8f));
                    world.spawnParticle(Particle.REDSTONE, particleLocation, 15,
                            new Particle.DustOptions(Color.PURPLE, 2.8f));
                    world.spawnParticle(Particle.REDSTONE, particleLocation, 15,
                            new Particle.DustOptions(Color.YELLOW, 2.8f));
                    world.spawnParticle(Particle.SPELL_WITCH, particleLocation, 10, 0.2, 0.2, 0.2, 0.1);
                }

                angleStep += 15;
            }
        }.runTaskTimer(plugin, 0L, 5L); // Schnellere Partikelaktualisierung
    }

    // Methode zur Erzeugung der eigentlichen Vortex-Effekte nach der Warnphase
    private void startVortexEffect(Player player, Location vortexLocation) {
        // Sofortschaden und Partikel bei Start des Effekts
        createVortexParticles(vortexLocation);
        player.damage(2.0); // Sofortschaden ohne Abwehrmöglichkeit

        // Schleuder- und Vergiftungseffekt
        new BukkitRunnable() {
            int ticks = 0;
            boolean isThrown = false;

            @Override
            public void run() {
                if (ticks >= 40) { // Angriffsdauer: 2 Sekunden
                    this.cancel();
                    return;
                }

                if (!isThrown && ticks == 10) { // Nach 0.5 Sekunden wird der Spieler weggeschleudert
                    Vector launchDirection = player.getLocation().toVector()
                            .subtract(vortexLocation.toVector()).normalize().multiply(1.2).setY(1);

                    if (!Double.isNaN(launchDirection.getX()) && !Double.isNaN(launchDirection.getY()) && !Double.isNaN(launchDirection.getZ())) {
                        player.setVelocity(launchDirection);
                        player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 1));
                        isThrown = true;
                    } else {
                        this.cancel();
                        plugin.getLogger().warning("Ungültiger Vektor für Schleuderwirkung. Effekt wird abgebrochen.");
                    }
                }

                if (ticks >= 20 && ticks % 10 == 0) { // Spieler wird jede Sekunde zum Vortex zurückgezogen
                    Vector pullDirection = vortexLocation.toVector().subtract(player.getLocation().toVector()).normalize();

                    if (!Double.isNaN(pullDirection.getX()) && !Double.isNaN(pullDirection.getY()) && !Double.isNaN(pullDirection.getZ())) {
                        player.setVelocity(pullDirection.multiply(0.8));
                    } else {
                        this.cancel();
                        plugin.getLogger().warning("Ungültiger Vektor für Zurückziehen. Effekt wird abgebrochen.");
                    }
                }

                ticks += 5;
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    // Methode zur Erzeugung der Vortex-Partikel während des Effekts
    private void createVortexParticles(Location location) {
        World world = location.getWorld();

        new BukkitRunnable() {
            int angleStep = 0;
            int height = 3;

            @Override
            public void run() {
                if (angleStep >= 360) {
                    this.cancel();
                    return;
                }

                double radius = 2.0;
                double angle = Math.toRadians(angleStep);

                for (int yOffset = 0; yOffset <= height; yOffset++) {
                    double x = radius * Math.cos(angle);
                    double z = radius * Math.sin(angle);
                    Location particleLocation = location.clone().add(x, yOffset * 0.5, z);

                    // Erzeuge grüne, violette und gelbe Partikel
                    world.spawnParticle(Particle.REDSTONE, particleLocation, 10,
                            new Particle.DustOptions(Color.GREEN, 2.5f));
                    world.spawnParticle(Particle.REDSTONE, particleLocation, 10,
                            new Particle.DustOptions(Color.PURPLE, 2.5f));
                    world.spawnParticle(Particle.REDSTONE, particleLocation, 10,
                            new Particle.DustOptions(Color.YELLOW, 2.5f));
                    world.spawnParticle(Particle.SPELL_WITCH, particleLocation, 15, 0.2, 0.2, 0.2, 0.1);
                }

                angleStep += 15;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }





    private void configureEasyDragon() {
        dragon.setMaxHealth(1000);  // Set health for easy mode
        dragon.setHealth(1000);

        // Basic abilities for easy mode
        dragon.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, Integer.MAX_VALUE, 1));

        // Spawn supporting mobs
        spawnSupportingMobs(1, EntityType.GHAST);
        spawnSupportingMobs(3, EntityType.BLAZE);
    }

    private void configureHardDragon() {
        dragon.setMaxHealth(2048);  // Set health for hard mode
        dragon.setHealth(2048);

        // Advanced abilities for hard mode
        dragon.getAttribute(Attribute.GENERIC_ARMOR).setBaseValue(40.0);
        dragon.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, Integer.MAX_VALUE, 3));
        dragon.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 2));

        // Spawn more challenging supporting mobs
        spawnSupportingMobs(2, EntityType.GHAST);
        spawnSupportingMobs(5, EntityType.BLAZE);
        spawnSupportingMobs(3, EntityType.WITHER_SKELETON);
    }

    private void spawnSupportingMobs(int count, EntityType mobType) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (dragon.isDead()) {
                    this.cancel();  // Stop spawning if the dragon is dead
                    return;
                }

                for (int i = 0; i < count; i++) {
                    Location mobLocation = spawnLocation.clone().add(random.nextInt(10) - 5, 0, random.nextInt(10) - 5);
                    Location groundLocation = findGroundLocation(mobLocation); // Finde die nächste Bodenposition

                    world.spawnEntity(groundLocation, mobType);
                }
            }
        }.runTaskTimer(plugin, 0L, 400L);  // Repeat every 20 seconds (400 ticks)
    }

    // Hilfsmethode, um die nächste Bodenposition unterhalb eines Standorts zu finden
    private Location findGroundLocation(Location startLocation) {
        World world = startLocation.getWorld();
        int y = startLocation.getBlockY();

        // Suche den ersten festen Block in Richtung nach unten
        while (y > 0 && world.getBlockAt(startLocation.getBlockX(), y, startLocation.getBlockZ()).isPassable()) {
            y--;
        }

        // Rückgabe der Position direkt über dem gefundenen Bodenblock
        return new Location(world, startLocation.getX(), y + 1, startLocation.getZ());
    }


    private void applyVisualEffects() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!dragon.isDead()) {
                    world.spawnParticle(Particle.FLAME, dragon.getLocation(), 50, 1.0, 1.0, 1.0, 0.05);
                    world.playSound(dragon.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
                } else {
                    this.cancel(); // Stop the effects when the dragon dies
                }
            }
        }.runTaskTimer(plugin, 0L, 40L);  // Run every 2 seconds
    }

    public LivingEntity getDragon() {
        return dragon;
    }

    public String getDifficulty() {
        return difficulty;
    }
    @EventHandler
    public void onDragonDeath(EntityDeathEvent event) {
        // Prüfe, ob das gestorbene Entity ein Ender-Drache ist
        if (event.getEntity() instanceof EnderDragon) {
            // Prüfe, ob es der von uns gespawnte Drache ist
            if (event.getEntity().getUniqueId().equals(getDragon().getUniqueId())) {
                // Rufe die Schwierigkeit vom FreakyDragon-Objekt ab
                String difficulty = getDifficulty();

                // Der Drache ist gestorben, handle den Tod
                handleDragonDeath(event, difficulty);
                bossBar.removeAll();
            }
        }
    }

    // Handles the dragon's death and drops items based on difficulty and random chances
    public void handleDragonDeath(EntityDeathEvent event, String difficulty) {
        event.getDrops().clear(); // Normalen Drop verhindern

        // Anzahl der Drops festlegen
        int dropCount = difficulty.equalsIgnoreCase("Schwer") ? 3 : 1;

        Location dragonLocation = dragon.getLocation(); // Verwende die Drachen-Location für Drops und Feuerwerk

        // Oraxen-Items droppen
        for (int i = 0; i < dropCount; i++) {
            ItemStack drop = getRandomOraxenDrop();
            if (drop != null) {
                world.dropItemNaturally(dragonLocation, drop);
            }
        }

        // Feuerwerk nach dem Drachen-Tod starten
        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (count >= 10) { // Feuerwerk für 10 Sekunden
                    this.cancel();
                    return;
                }

                // Feuerwerk an der Position des Drachens
                Firework firework = world.spawn(dragonLocation, Firework.class);
                FireworkMeta fireworkMeta = firework.getFireworkMeta();
                fireworkMeta.addEffect(FireworkEffect.builder()
                        .withColor(Color.RED, Color.YELLOW)
                        .withFade(Color.ORANGE)
                        .with(FireworkEffect.Type.BALL_LARGE) // Feuerwerkstyp setzen
                        .withFlicker()
                        .withTrail()
                        .build());
                fireworkMeta.setPower(1); // Setze die Reichweite des Feuerwerks
                firework.setFireworkMeta(fireworkMeta);

                count++;
            }
        }.runTaskTimer(plugin, 0L, 20L); // Feuerwerk alle 20 Ticks (1 Sekunde)

        // Ton abspielen
        world.playSound(dragonLocation, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
    }

    private ItemStack getRandomOraxenDrop() {
        double roll = random.nextDouble(); // Get a random value between 0 and 1
        double cumulativeProbability = 0.0;

        for (Map.Entry<ItemStack, Double> entry : oraxenDropItems.entrySet()) {
            cumulativeProbability += entry.getValue(); // Add up the chances
            if (roll <= cumulativeProbability) {
                return entry.getKey();
            }
        }

        return null; // If no item matches, return null (though this should never happen)
    }
}

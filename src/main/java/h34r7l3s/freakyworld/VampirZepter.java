package h34r7l3s.freakyworld;

import io.th0rgal.oraxen.api.OraxenItems;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.Particle.DustOptions;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.stream.Collectors;

import static io.th0rgal.oraxen.shaded.playeranimator.api.PlayerAnimatorPlugin.plugin;

public class VampirZepter implements Listener {

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        Entity victim = event.getEntity();

        if (damager instanceof Player) {
            Player player = (Player) damager;
            String itemId = OraxenItems.getIdByItem(player.getInventory().getItemInMainHand());
            if (itemId != null && itemId.equals("vampir")) {
                // Direktes Spawnen von Partikeln an der Position des Opfers
                DustOptions redstoneOptions = new DustOptions(Color.RED, 1); // Farbe Rot
                victim.getWorld().spawnParticle(Particle.REDSTONE, victim.getLocation(), 11, redstoneOptions);
                victim.getWorld().spawnParticle(Particle.FLAME, victim.getLocation(), 5);
                victim.getWorld().spawnParticle(Particle.LAVA, victim.getLocation(), 7);
                victim.getWorld().spawnParticle(Particle.DRAGON_BREATH, victim.getLocation(), 22);

                // Erzeugen der Partikel-Schlange von Opfer zu Spieler
                createParticleSnake(victim.getLocation(), player.getLocation(), player, 2.0); // Heilung um 2 Herzen
            }
        }
    }


    private void createParticleSnake(Location start, Location end, Player player, double healthToRegain) {
        int steps = 6; // Anzahl der Schritte für die Interpolation
        long delayBetweenSteps = 1L; // 2 Ticks Verzögerung zwischen jedem Schritt

        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double x = lerp(start.getX(), end.getX(), t);
            double y = lerp(start.getY(), end.getY(), t);
            double z = lerp(start.getZ(), end.getZ(), t);

            Location particleLocation = new Location(start.getWorld(), x, y, z);

            // Verzögertes Spawnen der Partikel entlang des Pfads
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                DustOptions redstoneOptions = new DustOptions(Color.RED, 1); // Farbe Rot und Größe 1
                particleLocation.getWorld().spawnParticle(Particle.REDSTONE, particleLocation, 12, redstoneOptions);

                start.getWorld().spawnParticle(Particle.FLAME, particleLocation, 4); // Erhöhte Anzahl
                start.getWorld().spawnParticle(Particle.LAVA, particleLocation, 2); // Erhöhte Anzahl
                start.getWorld().spawnParticle(Particle.DRAGON_BREATH, particleLocation, 12); // Zusätzlicher Partikeltyp
            }, i * delayBetweenSteps);
        }

        // Verzögerte Heilung, nachdem die Leuchtschlange den Spieler erreicht hat
        Bukkit.getScheduler().runTaskLater(plugin, () -> {

            double currentHealth = player.getHealth();
            double healingAmount;

            // Entscheiden, wie viel Gesundheit basierend auf den aktuellen Herzen des Spielers wiederhergestellt werden soll
            if (currentHealth >= 20) { // 10 Herzen
                healingAmount = Math.random() < 0.8 ? getRandomValue(1, 2) : getRandomValue(3, 4);
                // Boost evtl. zu hoch

            } else if (currentHealth >= 16) { // 8 Herzen
                healingAmount = Math.random() < 0.6 ? getRandomValue(2, 3) : getRandomValue(4, 5);
                // Boost evt. zu hooch

            } else if (currentHealth >= 12) { // 6 Herzen
                healingAmount = Math.random() < 0.4 ? getRandomValue(3, 4) : getRandomValue(5, 6);
                // Boost OK

            } else if (currentHealth >= 8) { // 4 Herzen
                healingAmount = Math.random() < 0.2 ? getRandomValue(4, 5) : getRandomValue(6, 7);
                // Boost == unsterblich? XD

            } else { // 2/1 Herzen
                healingAmount = getRandomValue(7, 8);
                // ????


            }

            player.setHealth(Math.min(player.getHealth() + healingAmount, player.getMaxHealth()));

            Vector direction = player.getLocation().getDirection().normalize().multiply(2); // 3 Blöcke in die Blickrichtung
            Location loc = player.getLocation().add(direction).add(0, 2, 0); // 2 Blöcke nach oben
            player.getWorld().spawnParticle(Particle.HEART, loc, 10);
        }, (steps + 1) * delayBetweenSteps);

    }


    // Hilfsmethode für lineare Interpolation
    private double lerp(double start, double end, double t) {
        return start + t * (end - start);
    }


    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        String itemId = OraxenItems.getIdByItem(player.getInventory().getItemInMainHand());

        // Check if itemId is not null before comparing it
        if (itemId != null && itemId.equals("vampir") && event.getAction() == Action.LEFT_CLICK_AIR) {
            player.getWorld().spawnParticle(Particle.EXPLOSION_NORMAL, player.getLocation().add(0, 1, 0), 10);
        }
    }

    public void startVampirZepterEffectLoop(JavaPlugin plugin) {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    String itemId = OraxenItems.getIdByItem(player.getInventory().getItemInMainHand());
                    if (itemId != null && itemId.equals("vampir")) {
                        player.getWorld().spawnParticle(Particle.SPELL_WITCH, player.getLocation().add(0, 1, 0), 10);
                        player.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE, player.getLocation().add(0, 2, 0), 10);
                        player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1, 0), 5);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }


    // Funktion, um einen zufälligen Wert zwischen min und max zu erhalten
    private double getRandomValue(double min, double max) {
        return min + (Math.random() * (max - min + 1));
    }

    //HCFW GOODIES

    private final Map<UUID, Long> cooldowns = new HashMap<>();

    private boolean isOnCooldown(Player player, String itemId, long cooldownTime) {
        UUID playerID = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        if (!cooldowns.containsKey(playerID)) {
            cooldowns.put(playerID, currentTime);
            return false;
        }

        long lastUsedTime = cooldowns.get(playerID);
        if (currentTime - lastUsedTime >= cooldownTime) {
            cooldowns.put(playerID, currentTime);
            return false;
        }

        return true;
    }

    private final Map<Player, Integer> rightClicks = new HashMap<>();
    private final Set<Player> inRitual = new HashSet<>();

    @EventHandler
    public void onPlayerInteractWisdom(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();
        String itemId = OraxenItems.getIdByItem(player.getInventory().getItemInMainHand());

        if (itemId != null && itemId.equals("staff_of_wisdom") && action == Action.RIGHT_CLICK_AIR) {
            if (isOnCooldown(player, "staff_of_wisdom", 10000L)) { // 10 Sekunden Cooldown
                player.sendMessage("Bist du verrueckt?! Das koennte dich umbringen?!");
                return;
            }
            if (!inRitual.contains(player)) {
                int currentLevel = player.getLevel();
                if (currentLevel > 0) {
                    player.setLevel(currentLevel - 1); // Verringert um ein Level
                    inRitual.add(player);

                    rightClicks.put(player, rightClicks.getOrDefault(player, 0) + 1);

                    // Schaden zufügen, wenn mehr als 3 Klicks
                    if (rightClicks.get(player) > 2) {
                        player.damage(2.0); // 1 Herz Schaden
                    }

                    // Start des Rituals
                    startRitual(player);

                    // Reset nach Abschluss des Rituals
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            inRitual.remove(player);
                            rightClicks.put(player, 0);
                        }
                    }.runTaskLater(plugin, 200L); // 10 Sekunden später
                }
            }
        }
    }

    private void startRitual(Player player) {
        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (count < 15) {
                    // Effekte und Sounds während des Rituals
                    orchestrateEffects(player);
                } else {
                    // Abschluss des Rituals und Erzeugen der Erfahrungsflaschen
                    generateExperienceBottles(player);
                    this.cancel();
                }
                count++;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void generateExperienceBottles(Player player) {
        int playerLevel = player.getLevel();
        int maxBottles = 20; // Maximalanzahl an Flaschen
        int minBottles = 1; // Mindestanzahl an Flaschen

        // Berechnen der Anzahl der zu generierenden Flaschen basierend auf dem Level des Spielers
        int bottlesToGenerate = Math.min(playerLevel, maxBottles);
        bottlesToGenerate = Math.max(bottlesToGenerate, minBottles); // Sicherstellen, dass mindestens eine Flasche generiert wird

        for (int i = 0; i < bottlesToGenerate; i++) {
            player.getWorld().dropItemNaturally(player.getLocation(), new ItemStack(Material.EXPERIENCE_BOTTLE));
        }

        // Leben abziehen in Relation zur Anzahl der generierten Flaschen
        double healthToLose = bottlesToGenerate * 0.5; // 0.5 Herzen pro Flasche
        player.damage(healthToLose);

        // Spieler-Level entsprechend verringern
        player.setLevel(Math.max(playerLevel - bottlesToGenerate, 0)); // Verhindern, dass das Level negativ wird
    }




    private void orchestrateEffects(Player player) {
        // Hier eine Auswahl an Effekten und Sounds, die für ein episches Ritual sorgen
        player.getWorld().spawnParticle(Particle.SPELL_WITCH, player.getLocation(), 30, 0.5, 1, 0.5, 0.05);
        player.getWorld().spawnParticle(Particle.FLAME, player.getLocation(), 20, 0.5, 1, 0.5, 0.05);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.PLAYERS, 1.0F, 0.5F);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.PLAYERS, 1.0F, 1.0F);


        // Weitere Partikel- und Soundeffekte, die das Ritual verstärken
        player.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE, player.getLocation().add(0, 1, 0), 50, 1, 1, 1, 0.1);
        player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0), 30, 1, 1, 1, 0.05);
        player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, player.getLocation(), 40, 1, 1, 1, 0.05);

        // Fügen Sie hier zusätzliche Partikel- und Soundeffekte ein, um das Orchester der Effekte zu vervollständigen
    }
    private final Set<UUID> playersWithLantern = new HashSet<>();

    @EventHandler
    public void onPlayerInteractAny(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack offHandItem = player.getInventory().getItemInOffHand();
        String offHandItemId = OraxenItems.getIdByItem(offHandItem);

        // Prüfen, ob die Laterne in der Offhand gehalten wird
        boolean isHoldingLanternInOffHand = (offHandItemId != null && offHandItemId.equals("blue_lantern_of_doom"));

        if (isHoldingLanternInOffHand) {
            Action action = event.getAction();

            // Erlauben von Aktionen, die nicht direkt mit der Laterne zusammenhängen
            if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                Material interactedBlockType = event.getClickedBlock() != null ? event.getClickedBlock().getType() : null;
                if (interactedBlockType != null && interactedBlockType.isInteractable() && !player.isSneaking()) {
                    // Erlaubt Interaktion mit interaktiven Blöcken, wenn der Spieler schleicht
                    return;
                }
                event.setCancelled(true); // Verhindert das Platzieren des Items
            }

            checkAndStartLanternTask(player);
        }
    }

    @EventHandler
    public void onPlayerSwitchItem(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        checkAndStartLanternTask(player);
    }



    private void checkAndStartLanternTask(Player player) {
        boolean isHoldingLantern = isHoldingLantern(player);

        if (isHoldingLantern && !playersWithLantern.contains(player.getUniqueId())) {
            playersWithLantern.add(player.getUniqueId());
            startLanternTasks(player);
        } else if (!isHoldingLantern && playersWithLantern.contains(player.getUniqueId())) {
            playersWithLantern.remove(player.getUniqueId());
        }
    }


    private void startLanternTasks(Player player) {
        // Task für kontinuierlichen Schaden an Monstern jede Sekunde
        new BukkitRunnable() {
            @Override
            public void run() {
                if (isHoldingLantern(player) && player.getLevel() > 0) {
                    boolean monstersNearby = areMonstersNearby(player);
                    if (monstersNearby) {
                        player.getNearbyEntities(10, 10, 10).stream()
                                .filter(entity -> entity instanceof Monster)
                                .forEach(entity -> ((Monster) entity).damage(1.5)); // Tick-Schaden
                        player.giveExp(-1); // Verringert XP pro 6 Sekunden
                    }
                } else if (player.getLevel() <= 0) {
                    player.sendMessage("Du bist zu erschoepft."); // Spieler benachrichtigen
                    this.cancel(); // Beendet den Task
                }
            }
        }.runTaskTimer(plugin, 0L, 40L); // Task alle 20 Ticks (2 Sekunde)
    }



    private boolean isHoldingLantern(Player player) {
        ItemStack mainHandItem = player.getInventory().getItemInMainHand();
        ItemStack offHandItem = player.getInventory().getItemInOffHand();
        String mainHandItemId = OraxenItems.getIdByItem(mainHandItem);
        String offHandItemId = OraxenItems.getIdByItem(offHandItem);
        return (mainHandItemId != null && mainHandItemId.equals("blue_lantern_of_doom")) ||
                (offHandItemId != null && offHandItemId.equals("blue_lantern_of_doom"));
    }

    private boolean areMonstersNearby(Player player) {
        return player.getNearbyEntities(10, 10, 10).stream()
                .anyMatch(entity -> entity instanceof Monster);
    }


    @EventHandler
    public void onPlayerInteractLantern(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();
        ItemStack heldItem = player.getInventory().getItemInMainHand();
        String itemId = OraxenItems.getIdByItem(heldItem);

        if (itemId != null && itemId.equals("blue_lantern_of_doom")) {
            // Überprüfen, ob der Spieler versucht, etwas zu platzieren
            if (action == Action.RIGHT_CLICK_BLOCK) {
                Material interactedBlockType = event.getClickedBlock().getType();
                if (interactedBlockType.isInteractable() && !player.isSneaking()) {
                    // Erlaubt Interaktion mit interaktiven Blöcken (z.B. Truhen), wenn der Spieler schleicht
                    return;
                }
                event.setCancelled(true); // Verhindert das Platzieren des Items
            }

            if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                if (isOnCooldown(player, "blue_lantern_of_doom", 10000L)) { // 10 Sekunden Cooldown
                    player.sendMessage("Bist du verrueckt?! Das koennte dich umbringen!.");
                    return;
                }
                final int[] playerLevel = {player.getLevel()}; // Erstellen eines ein-Element-Arrays

                new BukkitRunnable() {
                    int count = 0;

                    @Override
                    public void run() {
                        if (count < 10) {
                            player.getNearbyEntities(10, 10, 10).stream()
                                    .filter(entity -> entity instanceof Monster)
                                    .forEach(entity -> {
                                        if (playerLevel[0] > 0) {
                                            ((Monster) entity).damage(8.0); // Fügt den Kreaturen Schaden zu
                                            if (((Monster) entity).isDead()) {
                                                player.setLevel(playerLevel[0] - 1);
                                                playerLevel[0]--;
                                            }
                                        }
                                    });
                            // Fügt eine Vielzahl von Partikel- und Soundeffekten hinzu
                            playRitualEffects(player);
                        } else {
                            this.cancel();
                        }
                        count++;
                    }
                }.runTaskTimer(plugin, 0L, 10L); // Task jede halbe Sekunde

                // Cooldown von 10 Sekunden vor erneuter Nutzung
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        // Cooldown abgelaufen
                    }
                }.runTaskLater(plugin, 200L); // 10 Sekunden später
            }
        }
    }

    private void playRitualEffects(Player player) {
        // Eine Vielzahl von Partikel- und Soundeffekten
        player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, player.getLocation(), 50, 1, 1, 1, 0.05);
        player.getWorld().spawnParticle(Particle.DRAGON_BREATH, player.getLocation(), 30, 1, 1, 1, 0.05);
        player.getWorld().spawnParticle(Particle.GLOW_SQUID_INK, player.getLocation(), 20, 1, 1, 1, 0.05);
        player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, player.getLocation(), 40, 1, 1, 1, 0.05);
        // Fügen Sie hier weitere Partikeleffekte hinzu, um insgesamt 40 Effekte zu erreichen

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 1.0F, 0.5F);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.PLAYERS, 1.0F, 1.0F);
        // Fügen Sie hier weitere Soundeffekte hinzu
    }

    @EventHandler
    public void onPlayerInteractEye(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        String itemId = OraxenItems.getIdByItem(itemInHand);

        // Überprüfung, ob der Spieler das "Eagle Eye" Item hält
        if (itemId != null && itemId.equals("eagle_eye") && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            // Überprüfung, ob der Spieler Fackeln im Inventar hat
            if (player.getInventory().contains(Material.TORCH)) {
                // Ermittlung des Zielblocks in einer Reichweite von 50 Blöcken
                Block targetBlock = player.getTargetBlockExact(50);
                if (targetBlock != null && targetBlock.getType().isSolid()) {
                    // Positionieren einer Fackel auf dem Zielblock
                    Location blockAbove = targetBlock.getLocation().add(0, 1, 0);
                    if (blockAbove.getBlock().getType() == Material.AIR) {
                        blockAbove.getBlock().setType(Material.TORCH);
                        removeItemFromPlayer(player, Material.TORCH);
                    }
                }
            } else {
                player.sendMessage(ChatColor.RED + "Du benötigst Fackeln im Inventar, um diese Fähigkeit zu nutzen.");
            }
        }
    }
    private void removeItemFromPlayer(Player player, Material material) {
        Inventory inventory = player.getInventory();
        for (ItemStack item : inventory.getContents()) {
            if (item != null && item.getType() == material) {
                if (item.getAmount() == 1) {
                    inventory.remove(item);
                } else {
                    item.setAmount(item.getAmount() - 1);
                }
                break;
            }
        }
    }

    private final double experienceMultiplier = 3.0; // Multiplikator für Erfahrung

    @EventHandler
    public void onPlayerGainExp(PlayerExpChangeEvent event) {
        Player player = event.getPlayer();
        if (hasExperienceOrbInInventory(player)) {
            int originalExp = event.getAmount();
            int boostedExp = (int) (originalExp * experienceMultiplier);
            event.setAmount(boostedExp);
        }
    }

    @EventHandler
    public void onEntityDeathOrb(EntityDeathEvent event) {
        List<Player> nearbyPlayers = event.getEntity().getNearbyEntities(10, 10, 10).stream()
                .filter(e -> e instanceof Player)
                .map(e -> (Player) e)
                .collect(Collectors.toList());

        for (Player player : nearbyPlayers) {
            if (hasExperienceOrbInInventory(player)) {
                int newExp = (int) (event.getDroppedExp() * experienceMultiplier);
                event.setDroppedExp(newExp);
                // Optional: Anpassung der Droprate oder Hinzufügen spezieller Drops
            }
        }
    }


    private boolean hasExperienceOrbInInventory(Player player) {
        for (ItemStack item : player.getInventory()) {
            if (item != null && OraxenItems.getIdByItem(item).equals("experience_orb")) {
                return true;
            }
        }
        return false;
    }

}


/*
Ende


 */

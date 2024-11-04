package h34r7l3s.freakyworld;

import com.comphenix.protocol.wrappers.EnumWrappers;
import io.th0rgal.oraxen.api.OraxenItems;
// Imports required for the guild management and inventory interaction
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.annotation.Target;
import java.util.List;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.Particle.DustOptions;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;



//import static io.th0rgal.oraxen.shaded.playeranimator.api.PlayerAnimatorPlugin.plugin;

public class VampirZepter implements Listener {
    private final JavaPlugin plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private DatabaseManager dbManager;
    private final GameLoop gameLoop; // Eine Instanz von GameLoop, die beim Konstruktor übergeben wird
    private final QuestVillager questVillager;
    private final GuildManager guildManager;
    private final List<ItemStack> spawnEggItems = new ArrayList<>();

    public VampirZepter(JavaPlugin plugin, DatabaseManager dbManager, GameLoop gameLoop,QuestVillager questVillager, GuildManager guildManager) {
        this.plugin = plugin;
        this.dbManager = dbManager;
        startLegendaryEffectTask();
        this.gameLoop = gameLoop;
        this.questVillager = questVillager;
        this.guildManager = guildManager;
        initializeSpawnEggList();
    }

    @EventHandler
    public void onPlayerNinjaInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        int requiredHunger = 7; // Anpassen Sie die erforderlichen Hungerpunkte nach Bedarf

        if (player.isSneaking() && event.getAction() == Action.RIGHT_CLICK_AIR) {
            // Zuerst überprüfen, ob der Spieler das richtige Item hat und ob ein Ziel existiert
            String itemId = OraxenItems.getIdByItem(player.getInventory().getItemInMainHand());
            if (itemId != null && itemId.equals("vampir")) {
                Entity target = getTargetedEntity(player, 20); // Angenommener Sichtbereich von 20 Blöcken
                if (target != null) {
                    // Überprüfe, ob der Spieler genügend Hunger hat
                    if (player.getFoodLevel() >= requiredHunger) {
                        // Versuche, den Spieler hinter das Ziel zu teleportieren
                        if (teleportPlayerBehindTarget(player, target)) {
                            // Hungerpunkte nur abziehen, wenn die Teleportation erfolgreich war
                            player.setFoodLevel(player.getFoodLevel() - requiredHunger);
                        }
                    } else {
                        player.sendMessage("Du hast nicht genug Hunger, um diese Fähigkeit zu nutzen.");
                    }
                } else {
                    player.sendMessage("Kein Ziel in Reichweite gefunden.");
                }
            }
        }
    }

    private Entity getTargetedEntity(Player player, double range) {
        World world = player.getWorld();
        RayTraceResult rayTraceResult = world.rayTraceEntities(
                player.getEyeLocation(),
                player.getLocation().getDirection(),
                range,
                entity -> entity != player && entity instanceof LivingEntity
        );

        if (rayTraceResult != null && rayTraceResult.getHitEntity() != null) {
            return rayTraceResult.getHitEntity();
        }

        return null;
    }

    private boolean teleportPlayerBehindTarget(Player player, Entity target) {
        Location targetLocation = target.getLocation();
        Vector direction = targetLocation.getDirection().normalize();
        Location checkLocation;

        for (int i = -2; i <= 2; i++) {
            for (int j = 1; j >= -1; j--) {
                checkLocation = targetLocation.clone().add(direction.clone().multiply(-2)).add(0, j, 0);
                if (isSafeLocation(checkLocation)) {
                    Location teleportLocation = checkLocation.setDirection(player.getLocation().getDirection());
                    player.teleport(teleportLocation);
                    player.getWorld().spawnParticle(Particle.LARGE_SMOKE, teleportLocation, 30, 0.3, 0.3, 0.3, 0.02);
                    return true; // Rückgabe von true, wenn die Teleportation erfolgreich war
                }
            }
        }

        // Rückgabe von false, falls keine sichere Position zum Teleportieren gefunden wurde
        player.sendMessage("Kein sicherer Ort zum Teleportieren gefunden!");
        return false;
    }

    private boolean isSafeLocation(Location location) {
        Block feet = location.getBlock();
        Block head = feet.getRelative(BlockFace.UP);
        Block below = feet.getRelative(BlockFace.DOWN);
        return !feet.getType().isSolid() && !head.getType().isSolid() && below.getType().isSolid();
    }



    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        Entity victim = event.getEntity();

        if (damager instanceof Player) {
            Player player = (Player) damager;
            String itemId = OraxenItems.getIdByItem(player.getInventory().getItemInMainHand());
            if (itemId != null && itemId.equals("vampir")) {
                // Generiere eine Zufallszahl zwischen 0 und 1
                double randomValue = Math.random();

                // Festlege die Wahrscheinlichkeit, dass der Heilungseffekt ausgelöst wird (hier 60%)
                double healingProbability = 0.6;

                if (randomValue < healingProbability) {
                    // Direktes Spawnen von Partikeln an der Position des Opfers
                    DustOptions redstoneOptions = new DustOptions(Color.RED, 1); // Farbe Rot
                    victim.getWorld().spawnParticle(Particle.DUST, victim.getLocation(), 11, redstoneOptions);
                    victim.getWorld().spawnParticle(Particle.FLAME, victim.getLocation(), 5);
                    victim.getWorld().spawnParticle(Particle.LAVA, victim.getLocation(), 7);
                    victim.getWorld().spawnParticle(Particle.DRAGON_BREATH, victim.getLocation(), 22);

                    // Erzeugen der Partikel-Schlange von Opfer zu Spieler
                    createParticleSnake(victim.getLocation(), player.getLocation(), player, 2.0); // Heilung um 2 Herzen
                }
            }
        }
    }


    @EventHandler
    public void onArrowShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getProjectile() instanceof Arrow)) {
            return;
        }

        Player player = (Player) event.getEntity();
        Arrow arrow = (Arrow) event.getProjectile();
        ItemStack arrowItem = event.getBow(); // Verwenden Sie getBow() um das Bogen-Item zu bekommen.

        if (arrowItem == null || !arrowItem.hasItemMeta()) {
            return;
        }

        String itemId = OraxenItems.getIdByItem(arrowItem);
        if (itemId != null && itemId.equals("woorpy")) { // Ersetzen Sie "woorpy" durch die tatsächliche ID des Oraxen-Pfeils
            arrow.setMetadata("woorpy", new FixedMetadataValue(plugin, true));
        }
    }

    @EventHandler
    public void onArrowLand(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow)) {
            return;
        }

        Arrow arrow = (Arrow) event.getEntity();
        if (!arrow.hasMetadata("woorpy")) {
            return;
        }

        // Überprüfung, ob der Schütze des Pfeils ein Spieler ist
        if (!(arrow.getShooter() instanceof Player)) {
            return;
        }

        Player shooter = (Player) arrow.getShooter();

        // Verarbeitung nur, wenn das getroffene Entity auch ein Spieler ist
        if (event.getHitEntity() instanceof Player) {
            Player victim = (Player) event.getHitEntity();

            // Sicherstellen, dass der Code im Hauptthread ausgeführt wird
            Bukkit.getScheduler().runTask(plugin, () -> {
                Location shooterLocation = shooter.getLocation().clone();
                Location victimLocation = victim.getLocation().clone();

                // Tausche die Positionen von Schütze und Opfer
                shooter.teleport(victimLocation);
                victim.teleport(shooterLocation);

                // Zeige Partikeleffekte an beiden Standorten
                showParticlesWarp(shooterLocation, victimLocation);
            });
        }
    }



    private void showParticlesWarp(org.bukkit.Location location1, org.bukkit.Location location2) {
        // Erzeuge Partikel-Effekte hier, um den Platztausch visuell darzustellen
        new BukkitRunnable() {
            @Override
            public void run() {
                location1.getWorld().spawnParticle(Particle.DUST, location1, 11, new Particle.DustOptions(Color.RED, 1));
                location1.getWorld().spawnParticle(Particle.FLAME, location1, 5);
                location1.getWorld().spawnParticle(Particle.LAVA, location1, 7);
                location1.getWorld().spawnParticle(Particle.DRAGON_BREATH, location1, 22);

                location2.getWorld().spawnParticle(Particle.DUST, location2, 11, new Particle.DustOptions(Color.RED, 1));
                location2.getWorld().spawnParticle(Particle.FLAME, location2, 5);
                location2.getWorld().spawnParticle(Particle.LAVA, location2, 7);
                location2.getWorld().spawnParticle(Particle.DRAGON_BREATH, location2, 22);
            }
        }.runTaskLater(plugin, 1L);
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
                particleLocation.getWorld().spawnParticle(Particle.DUST, particleLocation, 12, redstoneOptions);

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
                healingAmount = Math.random() < 0.3 ? getRandomValue(1, 2) : getRandomValue(3, 4);
            } else if (currentHealth >= 16) { // 8 Herzen
                healingAmount = Math.random() < 0.4 ? getRandomValue(2, 3) : getRandomValue(3, 4);
            } else if (currentHealth >= 12) { // 6 Herzen
                healingAmount = Math.random() < 0.5 ? getRandomValue(2, 3) : getRandomValue(3, 4);
            } else if (currentHealth >= 8) { // 4 Herzen
                healingAmount = Math.random() < 0.6 ? getRandomValue(1, 2) : getRandomValue(2, 3);
            } else { // 2/1 Herzen
                healingAmount = Math.random() < 0.7 ? getRandomValue(1, 2) : getRandomValue(2, 3);
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
            player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation().add(0, 1, 0), 10);
        }
    }

    public void startVampirZepterEffectLoop(JavaPlugin plugin) {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    String itemId = OraxenItems.getIdByItem(player.getInventory().getItemInMainHand());
                    if (itemId != null && itemId.equals("vampir")) {
                        player.getWorld().spawnParticle(Particle.WITCH, player.getLocation().add(0, 1, 0), 10);
                        player.getWorld().spawnParticle(Particle.ENCHANT, player.getLocation().add(0, 2, 0), 10);
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
        int maxBottles = 40; // Maximalanzahl an Flaschen
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
        player.getWorld().spawnParticle(Particle.WITCH, player.getLocation(), 30, 0.5, 1, 0.5, 0.05);
        player.getWorld().spawnParticle(Particle.FLAME, player.getLocation(), 20, 0.5, 1, 0.5, 0.05);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.PLAYERS, 1.0F, 0.5F);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.PLAYERS, 1.0F, 1.0F);


        // Weitere Partikel- und Soundeffekte, die das Ritual verstärken
        player.getWorld().spawnParticle(Particle.ENCHANT, player.getLocation().add(0, 1, 0), 50, 1, 1, 1, 0.1);
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
                                .forEach(entity -> ((Monster) entity).damage(2.5)); // Tick-Schaden
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
                            player.getNearbyEntities(50, 50, 50).stream()
                                    .filter(entity -> entity instanceof Monster || entity instanceof Ghast || entity instanceof EnderDragon)
                                    .forEach(entity -> {
                                        if (playerLevel[0] > 0) {
                                            ((LivingEntity) entity).damage(9.0, player); // Schaden an alle gefilterten Entitäten zufügen
                                            if (entity.isDead()) {
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
                    // Positionieren einer Fackel auf dem Zielblock oder seitlich davon
                    Location blockLocation = targetBlock.getLocation();
                    Location blockAbove = blockLocation.add(0, 1, 0);

                    if (blockAbove.getBlock().getType() == Material.AIR) {
                        // Platzieren Sie die Fackel oben auf dem Block, wenn über ihm Luft ist
                        blockAbove.getBlock().setType(Material.TORCH);
                        removeItemFromPlayer(player, Material.TORCH);
                    } else {
                        // Versuchen Sie, die Fackel seitlich am Zielblock zu platzieren
                        for (BlockFace face : BlockFace.values()) {
                            Location adjacentLocation = blockLocation.add(face.getModX(), face.getModY(), face.getModZ());
                            if (adjacentLocation.getBlock().getType() == Material.AIR) {
                                adjacentLocation.getBlock().setType(Material.TORCH);
                                removeItemFromPlayer(player, Material.TORCH);
                                break;
                            }
                        }
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
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null) {
                String itemId = OraxenItems.getIdByItem(item);
                if (itemId != null && itemId.equals("experience_orb")) {
                    return true;
                }
            }
        }
        return false;
    }
    //Orb Aktive
    @EventHandler
    public void onPlayerUseExperienceOrb(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack heldItem = player.getInventory().getItemInMainHand();
        String itemId = OraxenItems.getIdByItem(heldItem);

        // Überprüfen, ob das Item ein "experience_orb" ist und ein Rechtsklick ausgeführt wird
        if (itemId != null && itemId.equals("experience_orb") && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true); // Verhindert das Werfen des Orbs
            openExperienceOrbMenu(player); // Öffnet das Menü
        }
    }

    // Open the Experience Orb Menu
    private void openExperienceOrbMenu(Player player) {
        Inventory experienceOrbMenu = Bukkit.createInventory(null, 9, ChatColor.LIGHT_PURPLE + "Experience Orb Menü");

        // Teleport-Icon for World Spawn
        ItemStack teleportItem = new ItemStack(Material.ENDER_PEARL);
        ItemMeta teleportMeta = teleportItem.getItemMeta();
        teleportMeta.setDisplayName(ChatColor.GREEN + "Teleportiere zum Welt-Spawn");
        teleportMeta.setLore(Collections.singletonList(ChatColor.GRAY + "Klicke, um dich zum Spawn zu teleportieren"));
        teleportItem.setItemMeta(teleportMeta);
        experienceOrbMenu.setItem(0, teleportItem);

        // Freaky Statistics Item
        ItemStack statsItem = createFreakyStatisticsItem(player);
        experienceOrbMenu.setItem(4, statsItem);

        // Guild Information Section
        ItemStack guildItem = createGuildInfoItem(player);
        experienceOrbMenu.setItem(8, guildItem);

        player.openInventory(experienceOrbMenu);
    }

    // Create the Freaky Statistics Item for the Experience Orb Menu
    private ItemStack createFreakyStatisticsItem(Player player) {
        UUID playerId = player.getUniqueId();
        int playerItems = questVillager.getPlayerContribution(player);
        double playerPercentage = questVillager.calculatePlayerContributionPercentage(player);
        int playerXP = gameLoop.getPlayerXP(playerId);
        int playerRank = gameLoop.getPlayerRank(playerId);
        int xpOnHand = gameLoop.getPlayerXPOnHand(playerId);
        int xpInBank = gameLoop.getPlayerXPInBank(playerId);

        // Create the statistics item with player data
        ItemStack statsItem = new ItemStack(Material.CHERRY_SAPLING);
        ItemMeta statsMeta = statsItem.getItemMeta();
        if (statsMeta != null) {
            String rankName = gameLoop.getPlayerRankName(playerRank);
            statsMeta.setDisplayName(ChatColor.DARK_PURPLE + "Deine Freaky Statistik");
            statsMeta.setLore(Arrays.asList(
                    ChatColor.GOLD + "Dein aktueller Rang: " + ChatColor.RED + "#" + playerRank + " " + ChatColor.AQUA + rankName,
                    ChatColor.GOLD + "Deine Gesamten Abgaben: " + ChatColor.DARK_AQUA + GameLoop.NumberFormatter.formatNumber(playerItems),
                    ChatColor.GOLD + "Freaky XP: " + ChatColor.DARK_AQUA + GameLoop.NumberFormatter.formatNumber(playerXP),
                    "",
                    ChatColor.DARK_RED + "Inventar:",
                    ChatColor.GOLD + "Freaky XP auf Tasche: " + ChatColor.GREEN + GameLoop.NumberFormatter.formatNumber(xpOnHand),
                    ChatColor.GOLD + "Freaky XP in Bank: " + ChatColor.GREEN + GameLoop.NumberFormatter.formatNumber(xpInBank),
                    "",
                    ChatColor.GOLD + "Deine Freakyness: " + ChatColor.LIGHT_PURPLE + String.format("%.2f", playerPercentage) + "%"
            ));
            statsItem.setItemMeta(statsMeta);
        }
        return statsItem;
    }
    // Update the method that retrieves the player's guild based on player name
    private Guild getPlayerGuild(Player player) {
        String playerName = player.getName();  // Convert UUID to player name for the method
        return guildManager.getPlayerGuild(playerName);
    }
    // Create the Guild Information Item
    private ItemStack createGuildInfoItem(Player player) {
        // Retrieve the player's guild using the player's name instead of UUID
        String playerName = player.getName();
        Guild playerGuild = guildManager.getPlayerGuild(playerName);
        ItemStack guildItem = new ItemStack(Material.BOOK);
        ItemMeta guildMeta = guildItem.getItemMeta();

        if (playerGuild != null) {
            guildMeta.setDisplayName(ChatColor.DARK_PURPLE + "Gilde: " + playerGuild.getName());
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GOLD + "Gildennachrichten und Aufgaben");

            if (!playerGuild.getMessages().isEmpty()) {
                lore.add(ChatColor.AQUA + "Nachrichten:");
                for (String message : playerGuild.getMessages()) {
                    lore.add(ChatColor.WHITE + "- " + message);
                }
            } else {
                lore.add(ChatColor.GRAY + "Keine Nachrichten.");
            }

            if (!playerGuild.getTasks().isEmpty()) {
                lore.add(ChatColor.LIGHT_PURPLE + "Aufgaben:");
                for (Guild.GuildTask task : playerGuild.getTasks()) {
                    lore.add(ChatColor.WHITE + "- " + task.getDescription() + " (" + task.getStatus() + ")");
                }
            } else {
                lore.add(ChatColor.GRAY + "Keine Aufgaben.");
            }
            guildMeta.setLore(lore);
        } else {
            guildMeta.setDisplayName(ChatColor.RED + "Keine Gilde");
            guildMeta.setLore(Collections.singletonList(ChatColor.GRAY + "Du bist in keiner Gilde."));
        }
        guildItem.setItemMeta(guildMeta);
        return guildItem;
    }
    // Handle inventory clicks within the Experience Orb Menu
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals(ChatColor.LIGHT_PURPLE + "Experience Orb Menü")) {
            event.setCancelled(true); // Prevent items from being taken from the menu
            Player player = (Player) event.getWhoClicked();
            ItemStack clickedItem = event.getCurrentItem();

            if (clickedItem == null || !clickedItem.hasItemMeta()) return;
            String displayName = clickedItem.getItemMeta().getDisplayName();

            if (displayName.equals(ChatColor.GREEN + "Teleportiere zum Welt-Spawn")) {
                World TargetWorld = Bukkit.getWorld("world");
                player.teleport(TargetWorld.getSpawnLocation());
                player.sendMessage(ChatColor.GREEN + "Du wurdest zum Welt-Spawn teleportiert!");
                player.closeInventory();
            } else if (displayName.startsWith(ChatColor.DARK_PURPLE + "Gilde: ")) {
                Guild playerGuild = guildManager.getPlayerGuild(player.getName());
                if (playerGuild != null) {
                    // Initialize GuildGUIListener with correct parameters
                    GuildGUIListener guildGUIListener = new GuildGUIListener(plugin, guildManager.getDataSource());
                    guildGUIListener.openGuildMessagesMenu(player, playerGuild); // Display guild messages and tasks
                } else {
                    player.sendMessage(ChatColor.RED + "Du bist in keiner Gilde.");
                }
            }
        }
    }




    //sturmwirbel
    private final Map<UUID, Set<UUID>> markedPlayers = new HashMap<>();
    private final Map<UUID, BukkitRunnable> healingTasks = new HashMap<>();
    private final Map<UUID, Integer> playerMarkDuration = new HashMap<>();

    @EventHandler
    public void onPlayerInteractAxe(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();
        String itemId = OraxenItems.getIdByItem(player.getInventory().getItemInMainHand());

        // Behandlung des Rechtsklicks für Sturmwind
        if (itemId != null && itemId.equals("sturmwind")) {
            handleRightClickSturmwind(action, player);
        }

        // Erweiterte Behandlung des Linksklicks für Markierung und Heilung
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            handleLeftClickHealing(player);
        }
    }

    private void handleRightClickSturmwind(Action action, Player player) {
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            if (!isOnCooldownWind(player, "sturmwind", 10000L)) { // 10 Sekunden Cooldown
                player.sendMessage(ChatColor.GREEN + "Sturmwind wurde benutzt!");
                createSoulWhirl(player, this.plugin);
                // Dezente Partikeleffekte hier implementieren
            } else {
                player.sendMessage(ChatColor.RED + "Sturmwind ist noch im Cooldown.");
            }
        }
    }

    private void handleLeftClickHealing(Player player) {
        String itemId = OraxenItems.getIdByItem(player.getInventory().getItemInMainHand());
        if (!"sturmwind".equals(itemId)) {
            return; // Nicht die richtige Ausrüstung, frühzeitige Rückkehr
        }

        UUID playerId = player.getUniqueId();
        UUID targetId = getTargetPlayerId(player);

        // Heilung beenden, wenn bereits ein Task läuft, aber nur, wenn der Spieler denselben Zielplayer hat
        BukkitRunnable task = healingTasks.get(playerId);
        if (task != null && !task.isCancelled() && targetId != null && markedPlayers.get(playerId).contains(targetId)) {
            task.cancel();
            healingTasks.remove(playerId);
            unmarkAllPlayers(player); // Entfernt die Markierung von allen Spielern, die von diesem Spieler markiert wurden
            player.sendMessage(ChatColor.RED + "Heilung beendet.");
            return;
        }

        // Versuche, einen Spieler vor dem Spieler zu finden und zu markieren
        markOrUnmarkTargetPlayer(player);
    }

    private UUID getTargetPlayerId(Player player) {
        Vector direction = player.getLocation().getDirection();
        RayTraceResult rayTraceResult = player.getWorld().rayTraceEntities(player.getEyeLocation(), direction, 100, entity -> entity instanceof Player && entity != player);

        if (rayTraceResult != null && rayTraceResult.getHitEntity() instanceof Player) {
            Player target = (Player) rayTraceResult.getHitEntity();
            return target.getUniqueId();
        }
        return null;
    }





    private void unmarkAllPlayers(Player marker) {
        UUID markerId = marker.getUniqueId();
        Set<UUID> targets = markedPlayers.getOrDefault(markerId, Collections.emptySet());
        targets.forEach(targetId -> {
            Player target = Bukkit.getPlayer(targetId);
            if (target != null) {
                target.removePotionEffect(PotionEffectType.REGENERATION);
                target.removePotionEffect(PotionEffectType.HEALTH_BOOST);
            }
        });
        targets.clear(); // Entferne alle Markierungen
        updateLines(); // Aktualisiere die Animationen
    }
    private void markOrUnmarkTargetPlayer(Player player) {
        UUID playerId = player.getUniqueId();
        Vector direction = player.getLocation().getDirection();
        RayTraceResult rayTraceResult = player.getWorld().rayTraceEntities(player.getEyeLocation(), direction, 100, entity -> entity instanceof Player && entity != player);

        if (rayTraceResult != null && rayTraceResult.getHitEntity() instanceof Player) {
            Player target = (Player) rayTraceResult.getHitEntity();
            UUID targetId = target.getUniqueId();

            // Überprüfen, ob der Spieler bereits markierte Ziele hat
            Set<UUID> markedTargets = markedPlayers.computeIfAbsent(playerId, k -> new HashSet<>());

            if (markedTargets.contains(targetId)) {
                // Ziel ist bereits markiert, also unmarkiere es
                unmarkPlayer(player, target);
                markedTargets.remove(targetId); // Entfernen aus der Liste markierter Spieler
            } else {
                // Ziel ist nicht markiert, also markiere es und starte Heilungsaufgabe
                markedTargets.add(targetId);
                startHealingTask(player);
                markPlayer(player, target); // Fügt Effekte hinzu und startet Animation
            }
        } else {
            player.sendMessage(ChatColor.RED + "Kein Spieler getroffen.");
        }
    }

    private void unmarkPlayer(Player marker, Player target) {
        UUID markerId = marker.getUniqueId();
        UUID targetId = target.getUniqueId();
        if (markedPlayers.getOrDefault(markerId, Collections.emptySet()).remove(targetId)) {
            target.removePotionEffect(PotionEffectType.REGENERATION);
            target.removePotionEffect(PotionEffectType.HEALTH_BOOST);
            marker.sendMessage(ChatColor.RED + "Spieler abgewaehlt."); // Korrektur hier
            updateLines();
        }
    }

    private void startHealingTask(Player player) {
        UUID playerId = player.getUniqueId();
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                Set<UUID> marked = markedPlayers.getOrDefault(playerId, Collections.emptySet());
                if (!marked.isEmpty()) {
                    double damage = 1.5; // Beispielwert für Schaden

                    // Absorptionsherzen behandeln
                    double absorption = player.getAbsorptionAmount();
                    if (absorption > 0) {
                        double newAbsorption = Math.max(0, absorption - damage);
                        player.setAbsorptionAmount(newAbsorption);
                        damage = Math.max(0, damage - (absorption - newAbsorption));
                    }

                    // Normale Gesundheit behandeln, ohne das Maximum zu überschreiten
                    if (damage > 0) {
                        double currentHealth = player.getHealth();
                        double maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                        double newHealth = Math.max(0, Math.min(maxHealth, currentHealth - damage));
                        player.setHealth(newHealth);
                    }

                    // Spiele den Schadensound
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 1.0f, 1.0f);

                    // Überprüfe, ob der Spieler gestorben ist
                    if (player.getHealth() <= 0) {
                        this.cancel(); // Beende diesen Task, wenn der Spieler gestorben ist
                        healingTasks.remove(playerId); // Entferne den Task aus der Map
                    }
                }
            }
        };
        task.runTaskTimer(this.plugin, 0L, 80L); // Ändere das Intervall auf 80 Ticks für die Schadensroutine
        healingTasks.put(playerId, task);




    // Überprüfung, ob ein Spieler markiert wurde und die Heilung beginnen soll
        Vector direction = player.getLocation().getDirection();
        RayTraceResult rayTraceResult = player.getWorld().rayTraceEntities(player.getEyeLocation(), direction, 100, entity -> entity instanceof Player && entity != player);
        if (rayTraceResult != null && rayTraceResult.getHitEntity() instanceof Player) {
            Player target = (Player) rayTraceResult.getHitEntity();
            markPlayer(player, target); // Markiere den Spieler und starte die Heilung
        } else {
            player.sendMessage(ChatColor.RED + "Kein Spieler getroffen.");
        }
    }



    private void markPlayer(Player marker, Player target) {
        UUID targetId = target.getUniqueId();
        // Initialisiere oder erhöhe die Markierungsdauer
        playerMarkDuration.putIfAbsent(targetId, 0); // Starte mit 0, falls noch nicht markiert

        BukkitRunnable effectTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!target.isOnline() || !markedPlayers.getOrDefault(marker.getUniqueId(), new HashSet<>()).contains(targetId)) {
                    this.cancel();
                    playerMarkDuration.remove(targetId); // Entferne den Spieler aus der Überwachung
                    return;
                }

                // Erhöhe die Markierungsdauer
                int duration = playerMarkDuration.getOrDefault(targetId, 0) + 100; // Erhöhe um 100 Sekunden
                playerMarkDuration.put(targetId, duration);

                // Berechne den neuen Level des Health Boost Effekts
                int healthBoostLevel = Math.min(duration / 100, 5) - 1; // Maximal 10 zusätzliche Herzen

                // Anwenden des Health-Boost-Effekts
                target.removePotionEffect(PotionEffectType.HEALTH_BOOST); // Entferne alten Effekt
                target.addPotionEffect(new PotionEffect(PotionEffectType.HEALTH_BOOST, 2400, healthBoostLevel, false, true), true);

                // Erneuere den Regenerationseffekt
                target.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 2400, 0, false, true), true);

                // Absorptionseffekt überprüfen und beibehalten
                double absorption = target.getAbsorptionAmount(); // Erhalte den aktuellen Absorptionseffekt-Wert
                if (absorption > 0) {
                    target.setAbsorptionAmount(absorption); // Stelle sicher, dass der Absorptionseffekt nicht beeinträchtigt wird
                }
            }
        };
        effectTask.runTaskTimer(this.plugin, 0L, 20L * 100); // Erneuere die Effekte alle 100 Sekunden
    }





    private void updateLines() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<UUID, Set<UUID>> entry : markedPlayers.entrySet()) {
                Player marker = Bukkit.getPlayer(entry.getKey());
                if (marker == null || !marker.isOnline()) {
                    continue;
                }
                entry.getValue().forEach(targetId -> {
                    Player target = Bukkit.getPlayer(targetId);
                    if (target != null && target.isOnline()) {
                        Particle.DustOptions primaryDustOptions = new Particle.DustOptions(Color.fromRGB(65, 105, 225), 0.5F);
                        Particle.DustOptions secondaryDustOptions = new Particle.DustOptions(Color.fromRGB(255, 215, 0), 0.5F);
                        drawDynamicLine(marker.getLocation().add(0, 1, 0), target.getLocation().add(0, 1, 0), primaryDustOptions, secondaryDustOptions, true);
                        target.getWorld().spawnParticle(Particle.HEART, target.getLocation().add(0, 2.0, 0), 1, 0.5, 0.5, 0.5, 0);
                    }
                });
            }
        }, 0L, 10L); // Dies könnte optimiert werden, um die Task-ID zu speichern und nur bei Bedarf neu zu starten
    }

    private void drawDynamicLine(Location start, Location end, Particle.DustOptions primaryDustOptions, Particle.DustOptions secondaryDustOptions, boolean pulseEffect) {
        World world = start.getWorld();
        if (world == null || !start.getWorld().equals(end.getWorld())) {
            return;
        }

        double distance = start.distance(end);
        Vector p1 = start.toVector();
        Vector p2 = end.toVector();
        Vector direction = p2.clone().subtract(p1).normalize();

        int points = (int) (distance / 0.5);
        double pulseDistance = 0; // Distanz für den pulsierenden Effekt

        for (int i = 0; i <= points; i++) {
            Vector currentPoint = direction.clone().multiply(i * 0.5).add(p1);
            Location particleLocation = currentPoint.toLocation(world);

            // Wellenbewegung
            double waveOffset = Math.sin(i + pulseDistance) * 0.5;

            // Hauptpartikellinie
            world.spawnParticle(Particle.DUST, particleLocation.add(0, waveOffset, 0), 1, primaryDustOptions);

            // Sekundäre Partikellinie für pulsierende Effekte
            if (pulseEffect && i % 5 == 0) {
                world.spawnParticle(Particle.DUST, particleLocation, 1, secondaryDustOptions);
            }

            // Endeffekte an beiden Enden
            if (i == 0 || i == points) {
                world.spawnParticle(Particle.EXPLOSION, particleLocation, 1);
            }
        }

        if (pulseEffect) {
            pulseDistance += 0.1; // Pulsierende Bewegung für die nächste Aktualisierung
        }
    }




    private boolean isOnCooldownWind(Player player, String itemId, long cooldownTime) {
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
        } else {
            return true;
        }
    }



    private void createSoulWhirl(Player player, JavaPlugin plugin) {
        new BukkitRunnable() {
            int duration = 200; // Verlängerte Dauer für einen beeindruckenderen Effekt
            double angle = 0;
            final double maxRadius = 5.0; // Maximale Ausdehnung des Schutzschildes
            final Particle.DustOptions dustOptions = new Particle.DustOptions(Color.fromRGB(65, 105, 225), 1.5F); // Royal Blue, größer für mehr Sichtbarkeit
            final Particle.DustOptions innerDustOptions = new Particle.DustOptions(Color.fromRGB(255, 215, 0), 1.0F); // Goldfarben für den inneren Kreis
            final Particle.DustOptions transitionDustOptions = new Particle.DustOptions(Color.fromRGB(85, 255, 255), 2.0F); // Cyan für den Übergangseffekt
            double currentRadius = 0; // Aktueller Radius beginnt bei 0 und breitet sich bis maxRadius aus
            double spreadSpeed = maxRadius / (200.0 / 5.0); // Geschwindigkeit der Ausbreitung
            // Entferne innerRadius und innerSpreadSpeed, da wir eine neue Logik für das Pulsieren verwenden

            // Pulsierende Effektvariablen
            double pulseDuration = 20; // Dauer eines vollständigen Pulses (in Ticks)
            double currentTime = 0; // Aktuelle Zeit im Pulszyklus

            @Override
            public void run() {
                if (duration <= 0) {
                    cancel();
                    return;
                }

                Location center = player.getLocation().add(0, 1, 0); // Zentrum etwas erhöht setzen
                currentRadius += spreadSpeed; // Radius gleichmäßig über die Dauer ausbreiten
                if (currentRadius > maxRadius) currentRadius = maxRadius; // Radius nicht über maxRadius hinausgehen lassen

                // Pulsierender innerer Radius basierend auf einer Sinuswelle
                double innerRadius = 2.0 + Math.sin(currentTime / pulseDuration * 2 * Math.PI) * 2.0; // Variiert zwischen 0 und 4
                currentTime += 1; // Inkrementiere die aktuelle Zeit

                // Dynamische Partikeleffekte für einen beeindruckenden visuellen Wirbel
                for (int i = 0; i < 360; i += 5) {
                    double radian = Math.toRadians(i + angle);
                    double x = center.getX() + currentRadius * Math.cos(radian);
                    double z = center.getZ() + currentRadius * Math.sin(radian);
                    double innerX = center.getX() + innerRadius * Math.cos(radian);
                    double innerZ = center.getZ() + innerRadius * Math.sin(radian);

                    Location particleLocation = new Location(center.getWorld(), x, center.getY(), z);
                    Location innerParticleLocation = new Location(center.getWorld(), innerX, center.getY(), innerZ);

                    Particle.DustOptions chosenDustOptions = i % 20 == 0 ? transitionDustOptions : dustOptions;
                    center.getWorld().spawnParticle(Particle.DUST, particleLocation, 1, chosenDustOptions);
                    if(i % 10 == 0) {
                        center.getWorld().spawnParticle(Particle.DUST, innerParticleLocation, 1, innerDustOptions);
                    }
                }

                // Schadens- und Abstoßeffekt alle 40 Ticks
                if (duration % 40 == 0) {
                    player.getWorld().getNearbyEntities(center, maxRadius, maxRadius, maxRadius).forEach(entity -> {
                        if (entity instanceof LivingEntity && entity != player) {
                            LivingEntity target = (LivingEntity) entity;
                            Vector direction = target.getLocation().toVector().subtract(center.toVector()).normalize().multiply(-2).setY(1);
                            target.setVelocity(direction); // Wirft Entitäten vom Zentrum weg
                            target.damage(4.0); // Fügt Schaden zu
                            target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 1)); // Stärkerer Vergiftungseffekt
                        }
                    });
                    center.getWorld().playSound(center, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0F, 0.5F); // Mächtiger Flügelschlag-Sound
                    center.getWorld().spawnParticle(Particle.EXPLOSION, center, 2); // Explosionspartikel
                }

                angle += 5; // Schnellere Drehbewegung für mehr Dynamik
                duration--;
            }
        }.runTaskTimer(plugin, 0L, 1L); // Task jede Tick ausführen
    }


    //Edgar
    @EventHandler
    public void onEatEdd(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        String itemId = OraxenItems.getIdByItem(item);
        if (item != null && itemId != null && itemId.equals("edgars_steak")) {
            // Prüfe, ob der Spieler schleichend rechtsklickt
            if (player.isSneaking() && event.getAction().equals(Action.RIGHT_CLICK_AIR)) {
                // Heile den Spieler vollständig
                player.setHealth(player.getMaxHealth());

                // Gib zusätzliches Leben wie beim OP-Apfel
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 24000, 4));

                // Schnelligkeit für kurze Zeit
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 6000, 1));

                // Sprungkraft für kurze Zeit
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 6000, 1));

                // Weitere gewünschte Effekte hier hinzufügen

                // Nachricht an den Spieler
                player.sendMessage(ChatColor.GREEN + "Du genießt den Biss von Edgars Steak und fühlst dich mächtiger als je zuvor!");

                // Entferne das Steak aus dem Inventar
                int amount = item.getAmount();
                if (amount > 1) {
                    item.setAmount(amount - 1);
                } else {
                    player.getInventory().remove(item);
                }

                // Soundeffekte oder visuelle Effekte hier hinzufügen
            }
        }
    }
    //Klinge der Dunkelheit
    @EventHandler
    public void onPlayerShadowInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        // Überprüfen, ob der Spieler mit der Schattenklinge interagiert hat
        String itemId = OraxenItems.getIdByItem(player.getInventory().getItemInMainHand());
        if (itemId != null && itemId.equals("schattenklinge")) {
            // Wenn der Spieler shift rechtsklickt, wird er unsichtbar
            if (player.isSneaking() && event.getAction() == Action.RIGHT_CLICK_AIR) {
                toggleInvisibility(player);
            }

            // Wenn der Spieler ins Leere schlägt, werden Dunkelheitseffekte ausgelöst
            if (event.getAction() == Action.LEFT_CLICK_AIR) {
                triggerDarknessEffects(player);
            }
        }
    }

    @EventHandler
    public void onEntityDamageByShadow(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player && event.getEntity() instanceof LivingEntity) {
            Player player = (Player) event.getDamager();
            LivingEntity victim = (LivingEntity) event.getEntity();
            String itemId = OraxenItems.getIdByItem(player.getInventory().getItemInMainHand());

            if (itemId != null && itemId.equals("schattenklinge")) {
                incrementHitCount(player); // Inkrementiere den Trefferzähler bei jedem Schlag
                applySpeedEffects(player, victim);
                applyShadowDamageEffects(player, victim);

                if (victim instanceof Monster) {
                    handleMonsterKill(player); // Extrahierte Logik für die Übersichtlichkeit
                }
            }

        }
    }
    private void handleMonsterKill(Player player) {
        UUID playerId = player.getUniqueId();
        int[] playerData = dbManager.getPlayerData(playerId);
        int currentKills = playerData[0] + 1;
        int swordLevel = playerData[1];

        dbManager.updatePlayerData(playerId, currentKills, swordLevel);
        adjustSwordStrength(player, currentKills);
    }


    private int calculateKillsRequiredForNextLevel(int currentLevel) {
        int baseKills = 10;
        // Exponentielles Wachstum: Zum Beispiel 10 Kills für das 1. Level, 20 für das 2., 40 für das 3., etc.
        return baseKills * (int)Math.pow(2, currentLevel);
    }


    private void adjustSwordStrength(Player player, int currentKills) {
        UUID playerUUID = player.getUniqueId();
        int[] playerData = dbManager.getPlayerData(playerUUID);
        int swordLevel = playerData[1]; // Aktuelles Schwertlevel aus der Datenbank
        int killsRequiredForNextLevel = calculateKillsRequiredForNextLevel(swordLevel);

        ItemStack sword = player.getInventory().getItemInMainHand();
        ItemMeta meta = sword.getItemMeta();

        if (meta != null && meta.hasEnchant(Enchantment.SHARPNESS)) {
            if (currentKills >= killsRequiredForNextLevel) {
                swordLevel++; // Erhöhe das Level um 1
                meta.addEnchant(Enchantment.SHARPNESS, Math.min(swordLevel, 100), true);
                sword.setItemMeta(meta);
                player.sendMessage(ChatColor.GREEN + "Deine Klinge der Dunkelheit ist jetzt Level " + swordLevel + "!");
                dbManager.updatePlayerData(playerUUID, currentKills, swordLevel);
            }
        } else {
            // Initial setze Schärfe I, wenn noch keine Verzauberung vorhanden ist
            meta.addEnchant(Enchantment.SHARPNESS, 1, true);
            sword.setItemMeta(meta);
            player.sendMessage(ChatColor.GREEN + "Deine Klinge der Dunkelheit hat jetzt Schärfe I!");
            dbManager.updatePlayerData(playerUUID, currentKills, 1);
        }
    }


    //Join zum Abruf der Daten




    private final Map<UUID, Boolean> invisibilityState = new HashMap<>();
    private static final long COOLDOWN_IN_MILLIS = 10000; // 10 Sekunden Cooldown

    private final Map<UUID, Integer> playerKillsShadow = new HashMap<>(); // Speichert die Anzahl der Kills je Spieler


    public void toggleInvisibility(Player player) {
        long currentTime = System.currentTimeMillis();
        UUID playerId = player.getUniqueId();

        // Überprüfe, ob der Spieler im Cooldown ist
        if (cooldowns.containsKey(playerId) && (currentTime - cooldowns.get(playerId)) < COOLDOWN_IN_MILLIS) {
            player.sendMessage(ChatColor.RED + "Du musst noch warten, bevor du dies wieder verwenden kannst.");
            return;
        }

        // Cooldown aktualisieren
        cooldowns.put(playerId, currentTime);

        // Zustand der Unsichtbarkeit umschalten
        boolean isVisible = invisibilityState.getOrDefault(playerId, Boolean.TRUE);
        invisibilityState.put(playerId, !isVisible);

        if (isVisible) {
            // Spieler wird unsichtbar
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
            player.sendMessage(ChatColor.GRAY + "Du bist nun unsichtbar.");
            updateEquipmentVisibility(player, false);
        } else {
            // Spieler wird sichtbar
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
            player.sendMessage(ChatColor.GRAY + "Du bist nun sichtbar.");
            updateEquipmentVisibility(player, true);
        }
    }




    public static void updateEquipmentVisibility(Player player, boolean visible) {
        ItemStack air = new ItemStack(Material.AIR);
        EnumWrappers.ItemSlot[] slots = {EnumWrappers.ItemSlot.MAINHAND, EnumWrappers.ItemSlot.OFFHAND, EnumWrappers.ItemSlot.HEAD, EnumWrappers.ItemSlot.CHEST, EnumWrappers.ItemSlot.LEGS, EnumWrappers.ItemSlot.FEET};

        for (EnumWrappers.ItemSlot slot : slots) {
            ItemStack itemStack = visible ? getItemInSlot(player, slot) : air;
            EquipmentVisibilityUtil.updateEquipmentVisibilityForAll(player, itemStack, slot);
        }
    }





    private static ItemStack getItemInSlot(Player player, EnumWrappers.ItemSlot slot) {
        switch (slot) {
            case MAINHAND:
                return player.getInventory().getItemInMainHand();
            case OFFHAND:
                return player.getInventory().getItemInOffHand();
            case HEAD:
                return player.getInventory().getHelmet();
            case CHEST:
                return player.getInventory().getChestplate();
            case LEGS:
                return player.getInventory().getLeggings();
            case FEET:
                return player.getInventory().getBoots();
            default:
                return new ItemStack(Material.AIR);
        }
    }


    private void triggerDarknessEffects(Player player) {
        World world = player.getWorld();
        Location playerLocation = player.getLocation();

        // Dunkle Partikel- und Soundeffekte auslösen
        world.spawnParticle(Particle.SMOKE, playerLocation, 50, 0.5, 0.5, 0.5, 0.1);
        world.spawnParticle(Particle.WITCH, playerLocation, 25, 0.5, 0.5, 0.5, 0.1);
        world.spawnParticle(Particle.PORTAL, playerLocation, 10, 0.5, 0.5, 0.5, 0.1);
        world.playSound(playerLocation, Sound.ENTITY_WITHER_AMBIENT, SoundCategory.PLAYERS, 0.5f, 1.0f);
    }

    private void applyShadowDamageEffects(Player player, Entity victim) {
        // Lebenskraft des Opfers absorbieren und dem Spieler zuführen
        //double absorptionAmount = calculateAbsorptionAmount(victim); // Berechnung der Absorptionsmenge
        //player.setHealth(Math.min(player.getHealth() + absorptionAmount, player.getMaxHealth()));

        // Dunkle Partikel und Soundeffekte auslösen
        World world = player.getWorld();
        Location victimLocation = victim.getLocation();
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, victimLocation, 30, 0.5, 0.5, 0.5, 0.1);
        world.spawnParticle(Particle.ENCHANT, victimLocation, 15, 0.5, 0.5, 0.5, 0.1);
        world.playSound(victimLocation, Sound.ENTITY_ENDER_DRAGON_FLAP, SoundCategory.HOSTILE, 1.0f, 1.0f);
    }

    // Globale Speicherung der Geschwindigkeitsstufen und Trefferhäufigkeit für jeden Spieler
    private final Map<UUID, Integer> playerSpeedLevels = new HashMap<>();
    private final Map<UUID, Integer> playerHitCount = new HashMap<>();

    private void applySpeedEffects(Player player, LivingEntity victim) {
        int baseDuration = 20; // Basisdauer in Ticks
        int speedLevel = getPlayerSpeedLevel(player);
        int slowDuration = baseDuration + getAdditionalDuration(player);

        PotionEffect slow = new PotionEffect(PotionEffectType.SLOWNESS, slowDuration, 0, false, true);
        victim.addPotionEffect(slow, true);

        PotionEffect speed = new PotionEffect(PotionEffectType.SPEED, slowDuration, speedLevel - 1, false, true);
        player.addPotionEffect(speed, true);

        player.sendMessage(ChatColor.GREEN + "Du hast Geschwindigkeit " + speedLevel + " erhalten und dein Gegner wurde verlangsamt!");

        // Trefferzähler zurücksetzen
        resetHitCount(player);
    }

    private int getPlayerSpeedLevel(Player player) {
        // Rückgabe der aktuellen Geschwindigkeitsstufe, Standardwert ist 1
        return playerSpeedLevels.getOrDefault(player.getUniqueId(), 1);
    }

    private int getAdditionalDuration(Player player) {
        // Berechnung der zusätzlichen Dauer basierend auf der Trefferhäufigkeit
        int additionalSeconds = playerHitCount.getOrDefault(player.getUniqueId(), 0) * 20; // Jeder Treffer fügt 1 Sekunde hinzu
        return additionalSeconds;
    }

    // Beispielmethode zum Aktualisieren der Geschwindigkeitsstufe (muss durch tatsächliche Logik ergänzt werden)
    public void updatePlayerSpeedLevel(Player player, int newSpeedLevel) {
        playerSpeedLevels.put(player.getUniqueId(), newSpeedLevel);
    }

    // Methode zum Zählen der Treffer
    public void incrementHitCount(Player player) {
        playerHitCount.put(player.getUniqueId(), playerHitCount.getOrDefault(player.getUniqueId(), 0) + 1);
    }

    // Methode zum Zurücksetzen der Trefferzählung
    private void resetHitCount(Player player) {
        playerHitCount.put(player.getUniqueId(), 0);
    }


    @EventHandler
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        ItemStack mainHandItem = event.getMainHandItem();
        ItemStack offHandItem = event.getOffHandItem();
        UUID playerId = player.getUniqueId();

        // Überprüfen, ob das Item in der Offhand die Schattenklinge ist
        String offHandItemId = OraxenItems.getIdByItem(offHandItem);
        if (offHandItemId != null && offHandItemId.equals("schattenklinge")) {
            // Verhindern, dass das Event standardmäßig abläuft
            event.setCancelled(true);

            // Umschalten des Geschwindigkeitslevels
            int currentSpeedLevel = getPlayerSpeedLevel(player);
            int newSpeedLevel = (currentSpeedLevel % 3) + 1; // Wechselt zwischen 1, 2 und 3
            updatePlayerSpeedLevel(player, newSpeedLevel);
            player.sendMessage(ChatColor.BLUE + "Geschwindigkeitslevel auf " + newSpeedLevel + " gesetzt.");

            // Items tauschen
            player.getInventory().setItemInMainHand(offHandItem);
            player.getInventory().setItemInOffHand(mainHandItem);
        }
    }


    // MoneyMaker
// MoneyMaker-Klinge: XPVamp

    // Variablen für XP-Speicherung und Cooldown
    private final Map<UUID, Integer> storedXP = new HashMap<>(); // Speichert aufgeladene XP pro Spieler
    private final long vampCooldown = 4200L; //Cooldown
    private final int xpThreshold = 10; // Mindestanzahl an XP-Leveln, um Angriff aufzuladen
    private final int maxXPToUse = 20; // Maximal 10 Level für die Umwandlung in Materialien

    @EventHandler
    public void onEntityDeathWithXPVamp(EntityDeathEvent event) {
        if (event.getEntity().getKiller() != null) {
            Player player = event.getEntity().getKiller();
            ItemStack heldItem = player.getInventory().getItemInMainHand();
            String itemId = OraxenItems.getIdByItem(heldItem);

            if (itemId != null && itemId.equals("xpvamp")) {
                // Sammle XP für den Spieler
                int xpGained = event.getDroppedExp();
                storedXP.put(player.getUniqueId(), storedXP.getOrDefault(player.getUniqueId(), 0) + xpGained);

                // Visuelle Effekte beim Sammeln von XP
                player.getWorld().spawnParticle(Particle.SOUL, player.getLocation(), 20, 0.5, 1, 0.5, 0.1);
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);

                // Erzverwandlung mit 10% Wahrscheinlichkeit
                if (Math.random() < 0.10) {
                    transformEntityToOre(event.getEntity().getLocation());
                    player.sendMessage(ChatColor.GOLD + "Du hast den Gegner in Schätze verwandelt!");
                }
            }
        }
    }
    // Transformiert die getötete Entität in Erzblöcke
    private final Random random = new Random();

    private void transformEntityToOre(Location location) {
        World world = location.getWorld();

        // Erstelle eine Liste aller möglichen Items mit den Wahrscheinlichkeiten
        Map<Material, Integer> lootTable = new HashMap<>();
        lootTable.put(Material.BREAD, 30);          // 30% Chance für Brot
        lootTable.put(Material.APPLE, 25);          // 25% Chance für Apfel
        lootTable.put(Material.CARROT, 20);         // 20% Chance für Karotte
        lootTable.put(Material.POTATO, 20);         // 20% Chance für Kartoffel
        lootTable.put(Material.LEATHER, 15);        // 15% Chance für Leder
        lootTable.put(Material.STRING, 20);         // 20% Chance für Seil
        lootTable.put(Material.BONE, 25);           // 25% Chance für Knochen
        lootTable.put(Material.ROTTEN_FLESH, 30);   // 30% Chance für verrottetes Fleisch
        lootTable.put(Material.COAL, 10);           // 10% Chance für Kohle
        lootTable.put(Material.IRON_NUGGET, 15);    // 15% Chance für Eisennuggets
        lootTable.put(Material.GOLD_NUGGET, 10);    // 10% Chance für Goldnuggets
        lootTable.put(Material.FEATHER, 15);        // 15% Chance für Feder
        lootTable.put(Material.COOKED_CHICKEN, 10); // 10% Chance für gebratenes Hähnchen
        lootTable.put(Material.MUSHROOM_STEW, 5);   // 5% Chance für Pilzsuppe
        lootTable.put(Material.MELON_SLICE, 15);    // 15% Chance für Melonenscheiben
        lootTable.put(Material.COOKED_PORKCHOP, 8); // 8% Chance für gebratenes Schweinefleisch
        lootTable.put(Material.COD, 12);            // 12% Chance für Kabeljau
        lootTable.put(Material.BEETROOT, 10);       // 10% Chance für rote Bete
        lootTable.put(Material.PUMPKIN_SEEDS, 5);   // 5% Chance für Kürbiskerne
        lootTable.put(Material.MELON_SEEDS, 5);     // 5% Chance für Melonenkerne
        lootTable.put(Material.WHEAT_SEEDS, 15);    // 15% Chance für Weizenkörner
        lootTable.put(Material.SUGAR_CANE, 10);     // 10% Chance für Zuckerrohr
        lootTable.put(Material.FLINT, 7);           // 7% Chance für Feuerstein
        lootTable.put(Material.CLAY_BALL, 5);       // 5% Chance für Tonkugeln
        lootTable.put(Material.GUNPOWDER, 12);      // 12% Chance für Schießpulver
        lootTable.put(Material.INK_SAC, 10);        // 10% Chance für Tintensack
        lootTable.put(Material.KELP, 12);           // 12% Chance für Seetang
        lootTable.put(Material.CACTUS, 6);          // 6% Chance für Kaktus
        lootTable.put(Material.LILY_PAD, 8);        // 8% Chance für Seerosenblatt
        lootTable.put(Material.HONEYCOMB, 4);       // 4% Chance für Honigwaben

        Random random = new Random();

        // Für jeden möglichen Gegenstand entscheiden, ob er gedroppt wird
        lootTable.forEach((material, chance) -> {
            if (random.nextInt(100) < chance) { // Wenn die zufällige Zahl kleiner als die Chance ist
                int amount = 1 + random.nextInt(2); // Menge zwischen 1 und 2
                world.dropItemNaturally(location, new ItemStack(material, amount));
            }
        });

        // Visuelle Effekte zur Markierung der Verwandlung
        world.spawnParticle(Particle.HEART, location, 15, 0.5, 0.5, 0.5, 0.1);
        world.spawnParticle(Particle.HAPPY_VILLAGER, location, 30, 1, 1, 1, 0.2);
        world.playSound(location, Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
    }



    @EventHandler
    public void onPlayerInteractXPVamp(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();
        ItemStack heldItem = player.getInventory().getItemInMainHand();
        String itemId = OraxenItems.getIdByItem(heldItem);

        if (itemId != null && itemId.equals("xpvamp")) {
            // Passive visuelle Effekte um die Klinge
            player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation(), 10, 0.5, 1.5, 0.5, 0.1);

            // Behandlung des Rechtsklicks zum Aufladen
            if ((action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {
                if (isOnCooldownVamp(player, "xpvamp", vampCooldown)) {
                    player.sendMessage(ChatColor.RED + "Die Klinge braucht Zeit, um sich wieder aufzuladen.");
                    return;
                }

                int currentXP = player.getLevel();
                if (currentXP < xpThreshold) {
                    player.sendMessage(ChatColor.RED + "Nicht genug XP-Level, um den Angriff aufzuladen.");
                    return;
                }

                // Verbrauche max. 10 Level und speichere als "aufgeladen"
                int usedXP = Math.min(currentXP, maxXPToUse);
                storedXP.put(player.getUniqueId(), usedXP);
                player.setLevel(currentXP - usedXP);

                // Visuelle Effekte und Sound beim Aufladen
                player.getWorld().spawnParticle(Particle.CRIT, player.getLocation(), 40, 1, 1, 1, 0.3);
                player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
                player.sendMessage(ChatColor.GOLD + "Deine Klinge ist aufgeladen mit " + usedXP + " XP-Punkten!");

                // Setze Cooldown und Aufladungsverfall
                startCooldown(player, "xpvamp");
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (storedXP.containsKey(player.getUniqueId())) {
                            storedXP.remove(player.getUniqueId());
                            player.sendMessage(ChatColor.DARK_RED + "Deine Klingenladung ist verfallen!");
                            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GHAST_SCREAM, 1.0f, 0.8f);
                        }
                    }
                }.runTaskLater(plugin, 100L); // 5 Sekunden Verfallzeit (100 Ticks)
            }
        }
    }

    // Behandlung des nächsten Linksklicks, um den aufgeladenen Angriff auszuführen
    @EventHandler
    public void onPlayerAttackWithXPVamp(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player && event.getEntity() instanceof LivingEntity) {
            Player player = (Player) event.getDamager();
            ItemStack heldItem = player.getInventory().getItemInMainHand();
            String itemId = OraxenItems.getIdByItem(heldItem);
            World world = player.getWorld();
            Location targetLocation = event.getEntity().getLocation();

            if (itemId != null && itemId.equals("xpvamp")) {
                if (storedXP.containsKey(player.getUniqueId())) {
                    int xpCharge = storedXP.get(player.getUniqueId());
                    storedXP.remove(player.getUniqueId()); // Angriff wird ausgeführt, Klinge wird entladen

                    // Berechne die Drop-Mengen basierend auf XP, reduziere dabei die Mengen und setze eine Mindestmenge
                    int goldAmount = Math.max(1, Math.min(xpCharge / 12, 3));    // Mindestens 1 Gold
                    int ironAmount = Math.max(1, Math.min(xpCharge / 15, 4));    // Mindestens 1 Eisen
                    int diamondAmount = Math.max(1, Math.min(xpCharge / 25, 1)); // Mindestens 1 Diamant, wenn verfügbar

                    // Droppe die Materialien in reduzierter Menge
                    if (goldAmount > 0) {
                        world.dropItemNaturally(targetLocation, new ItemStack(Material.GOLD_INGOT, goldAmount));
                    }
                    if (ironAmount > 0) {
                        world.dropItemNaturally(targetLocation, new ItemStack(Material.IRON_INGOT, ironAmount));
                    }
                    if (diamondAmount > 0) {
                        world.dropItemNaturally(targetLocation, new ItemStack(Material.DIAMOND, diamondAmount));
                    }

                    // Nachricht und Effekte beim besonderen Angriff
                    player.sendMessage(ChatColor.GREEN + "Dein Angriff hat Schätze aus dem Gegner herausgeholt!");
                    world.spawnParticle(Particle.CRIT, targetLocation, 30, 1, 1, 1, 0.2);
                    world.spawnParticle(Particle.TOTEM_OF_UNDYING, targetLocation, 10);
                    world.playSound(targetLocation, Sound.ENTITY_WITHER_DEATH, 1.0f, 1.0f);
                } else {
                    // Normale visuelle Effekte bei nicht aufgeladenem Angriff
                    world.spawnParticle(Particle.CRIT, targetLocation, 10, 0.5, 0.5, 0.5, 0.1);
                    world.playSound(targetLocation, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.0f, 1.0f);
                }
            }
        }
    }


    // Cooldown-Management für den Rechtsklick
    private final Map<UUID, Long> cooldownsVamp = new HashMap<>();

    private boolean isOnCooldownVamp(Player player, String itemId, long cooldownTime) {
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        long lastUseTime = cooldownsVamp.getOrDefault(playerId, 0L);
        if (currentTime - lastUseTime < cooldownTime) {
            return true;
        }
        cooldownsVamp.put(playerId, currentTime);
        return false;
    }

    private void startCooldown(Player player, String itemId) {
        cooldownsVamp.put(player.getUniqueId(), System.currentTimeMillis());
    }

    // Initialisiere den Effekt beim Serverstart für alle Spieler
    public void startLegendaryEffectTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (isHoldingXPVamp(player)) {
                        playLegendaryVisualEffect(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 10L); // Task läuft alle 10 Ticks (0,5 Sekunden)
    }

    // Überprüfe, ob der Spieler die "xpvamp"-Klinge hält
    private boolean isHoldingXPVamp(Player player) {
        String mainHandId = OraxenItems.getIdByItem(player.getInventory().getItemInMainHand());
        String offHandId = OraxenItems.getIdByItem(player.getInventory().getItemInOffHand());
        return (mainHandId != null && mainHandId.equals("xpvamp")) || (offHandId != null && offHandId.equals("xpvamp"));
    }

    // Methode für den legendären visuellen Effekt
    private void playLegendaryVisualEffect(Player player) {
        // Winkel für rotierende Partikel
        double angle = (System.currentTimeMillis() % 3600) / 3600.0 * 2 * Math.PI;
        double radius = 1.5;
        double x = radius * Math.cos(angle);
        double z = radius * Math.sin(angle);

        Location loc = player.getLocation().add(x, 1.5, z);

        // Enchantment- und End Rod-Partikel
        player.getWorld().spawnParticle(Particle.ENCHANT, loc, 10, 0.2, 0.2, 0.2, 0.1);
        player.getWorld().spawnParticle(Particle.END_ROD, loc, 5, 0.2, 0.2, 0.2, 0.05);

        // "Geld"-Partikel: Goldglitzer und grüne Wellen
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 5, 0.3, 0.3, 0.3, 0.05);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 5, 0.3, 0.3, 0.3, 0.1);

        // Schwebende goldene Partikel über dem Spieler
        Location topLoc = player.getLocation().add(0, 2.5, 0);
        player.getWorld().spawnParticle(Particle.FLAME, topLoc, 5, 0.3, 0.3, 0.3, 0.01);
        player.getWorld().spawnParticle(Particle.GLOW, topLoc, 5, 0.3, 0.3, 0.3, 0.02);

        // Gelegentlicher Soundeffekt
        if (Math.random() < 0.1) { // Spielt Sound gelegentlich ab (10% Chance)
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 0.5f, 1.2f);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.0f);
        }
    }


    //Eggmac Trading Funktion
    // Austausch 1x Eggmac - gegen das gewünschte Spawn Item.


    private final Map<UUID, Inventory> activeEggmacGUIs = new HashMap<>();
    private final Map<UUID, BukkitRunnable> guiTimers = new HashMap<>();
    private final Map<UUID, List<ItemStack>> playerEggOptions = new HashMap<>();



    private void initializeSpawnEggList() {
        // Hinzufügen der gewünschten Spawn-Eier zur Liste
        spawnEggItems.add(new ItemStack(Material.ZOMBIE_SPAWN_EGG));
        spawnEggItems.add(new ItemStack(Material.SKELETON_SPAWN_EGG));
        spawnEggItems.add(new ItemStack(Material.SPIDER_SPAWN_EGG));
        spawnEggItems.add(new ItemStack(Material.CREEPER_SPAWN_EGG));
        spawnEggItems.add(new ItemStack(Material.ENDERMAN_SPAWN_EGG));
        spawnEggItems.add(new ItemStack(Material.BLAZE_SPAWN_EGG));
        spawnEggItems.add(new ItemStack(Material.WITCH_SPAWN_EGG));
        spawnEggItems.add(new ItemStack(Material.SLIME_SPAWN_EGG));
        spawnEggItems.add(new ItemStack(Material.MAGMA_CUBE_SPAWN_EGG));
        spawnEggItems.add(new ItemStack(Material.GUARDIAN_SPAWN_EGG));
        spawnEggItems.add(new ItemStack(Material.PIGLIN_SPAWN_EGG));
        spawnEggItems.add(new ItemStack(Material.ZOMBIFIED_PIGLIN_SPAWN_EGG));
        spawnEggItems.add(new ItemStack(Material.HUSK_SPAWN_EGG));
        spawnEggItems.add(new ItemStack(Material.DROWNED_SPAWN_EGG));
        spawnEggItems.add(new ItemStack(Material.PHANTOM_SPAWN_EGG));
        spawnEggItems.add(new ItemStack(Material.VINDICATOR_SPAWN_EGG));
        spawnEggItems.add(new ItemStack(Material.EVOKER_SPAWN_EGG));
        spawnEggItems.add(new ItemStack(Material.SHULKER_SPAWN_EGG));
        spawnEggItems.add(new ItemStack(Material.COW_SPAWN_EGG));
        spawnEggItems.add(new ItemStack(Material.SHEEP_SPAWN_EGG));
        spawnEggItems.add(new ItemStack(Material.PIG_SPAWN_EGG));
        spawnEggItems.add(new ItemStack(Material.CHICKEN_SPAWN_EGG));
        spawnEggItems.add(new ItemStack(Material.RABBIT_SPAWN_EGG));
        spawnEggItems.add(new ItemStack(Material.HORSE_SPAWN_EGG));
        spawnEggItems.add(new ItemStack(Material.BEE_SPAWN_EGG));
        spawnEggItems.add(new ItemStack(Material.FOX_SPAWN_EGG));
        spawnEggItems.add(new ItemStack(Material.WOLF_SPAWN_EGG));
        spawnEggItems.add(new ItemStack(Material.CAT_SPAWN_EGG));
    }

    @EventHandler
    public void onRightClickEggmac(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        ItemStack heldItem = player.getInventory().getItemInMainHand();
        String itemId = OraxenItems.getIdByItem(heldItem);

        // Prüfen, ob der Spieler bereits eine aktive Session hat
        if (activeEggmacGUIs.containsKey(playerUUID)) {
            player.sendMessage(ChatColor.RED + "Du hast bereits ein aktives Ei!");
            return;
        }

        // Prüfen, ob das eggmac gehalten wird und mit Rechtsklick interagiert
        if (itemId != null && itemId.equals("eggmac") &&
                (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {

            event.setCancelled(true); // Verhindert Standardaktionen
            int nonDudCount = 6; // Anzahl der "Nicht-Nieten", anpassbar

            // Verbrauch des eggmac
            heldItem.setAmount(heldItem.getAmount() - 1);
            player.getInventory().setItemInMainHand(heldItem.getAmount() > 0 ? heldItem : null);

            // GUI mit zufälligen Eiern und Nieten erstellen
            Inventory eggmacGUI = createRandomEggmacGUI(nonDudCount, playerUUID);
            activeEggmacGUIs.put(playerUUID, eggmacGUI);

            // GUI dem Spieler öffnen
            player.openInventory(eggmacGUI);
            startGUIExpiryTimer(player, playerUUID);
        }
    }

    private Inventory createRandomEggmacGUI(int nonDudCount, UUID playerUUID) {
        Inventory gui = Bukkit.createInventory(null, 18, ChatColor.LIGHT_PURPLE + "Wähle ein Spawn-Ei");

        List<ItemStack> shuffledEggs = new ArrayList<>(spawnEggItems);
        Collections.shuffle(shuffledEggs);

        List<ItemStack> selectedEggs = shuffledEggs.subList(0, Math.min(nonDudCount, shuffledEggs.size()));

        List<ItemStack> itemsToDisplay = new ArrayList<>(selectedEggs);
        while (itemsToDisplay.size() < 18) {
            itemsToDisplay.add(new ItemStack(Material.COBWEB));
        }
        Collections.shuffle(itemsToDisplay);

        // Speichere die Auswahl für den Spieler
        playerEggOptions.put(playerUUID, itemsToDisplay);

        for (int i = 0; i < itemsToDisplay.size(); i++) {
            gui.setItem(i, itemsToDisplay.get(i));
        }

        return gui;
    }

    private void startGUIExpiryTimer(Player player, UUID playerUUID) {
        BukkitRunnable task = new BukkitRunnable() {
            int countdown = 20; // Ablaufzeit in Sekunden

            @Override
            public void run() {
                if (!activeEggmacGUIs.containsKey(playerUUID)) {
                    cancel();
                    return;
                }

                if (countdown <= 0) {
                    // Schließe das GUI und entferne das GUI von der aktiven Liste
                    player.closeInventory();
                    activeEggmacGUIs.remove(playerUUID);
                    guiTimers.remove(playerUUID);
                    playerEggOptions.remove(playerUUID);
                    player.sendMessage(ChatColor.RED + "Das Ei ist abgelaufen!");
                    player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1, 1);
                    player.playSound(player.getLocation(), Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.0f, 1.0f);
                    cancel();
                    return;
                }

                // Spiele Tick-Sound jede Sekunde
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.0f);
                player.playSound(player.getLocation(), Sound.ITEM_MACE_SMASH_AIR, 0.5f, 1.0f);
                countdown--;
            }
        };

        guiTimers.put(playerUUID, task);
        task.runTaskTimer(plugin, 0, 20); // Startet das Ticken (1 Sekunde Interval)
    }

    @EventHandler
    public void onInventoryClickEggmac(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        UUID playerUUID = player.getUniqueId();
        Inventory inventory = event.getInventory();

        // Prüfen, ob das geöffnete Inventar das Spawn-Ei-GUI ist
        if (inventory != null && activeEggmacGUIs.get(playerUUID) == inventory) {
            event.setCancelled(true);
            ItemStack clickedItem = event.getCurrentItem();

            // Prüfen, ob das geklickte Item ein Spawn-Ei ist und keine Niete (Spinnennetz)
            if (clickedItem != null && clickedItem.getType().toString().contains("SPAWN_EGG")) {
                player.getInventory().addItem(new ItemStack(clickedItem.getType()));
                player.getWorld().playEffect(player.getLocation(), Effect.SMOKE, 1);
                player.getWorld().playSound(player.getLocation(), Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1, 1);

                // Beende den Timer und entferne GUI-Daten
                if (guiTimers.containsKey(playerUUID)) {
                    guiTimers.get(playerUUID).cancel();
                    guiTimers.remove(playerUUID);
                }
                activeEggmacGUIs.remove(playerUUID);
                playerEggOptions.remove(playerUUID);

                player.closeInventory();
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        Inventory inventory = event.getInventory();

        if (activeEggmacGUIs.get(playerUUID) == inventory) {
            // GUI wurde geschlossen, aber Session bleibt aktiv
            // Spieler kann GUI erneut öffnen, aber erhält dieselben Optionen
            BukkitRunnable reopenTask = new BukkitRunnable() {
                @Override
                public void run() {
                    if (activeEggmacGUIs.containsKey(playerUUID)) {
                        player.openInventory(activeEggmacGUIs.get(playerUUID));
                    } else {
                        cancel();
                    }
                }
            };
            // Verzögerung, um eventuelle Konflikte zu vermeiden
            reopenTask.runTaskLater(plugin, 1L);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        // Beende alle laufenden Timer und entferne die Daten
        if (guiTimers.containsKey(playerUUID)) {
            guiTimers.get(playerUUID).cancel();
            guiTimers.remove(playerUUID);
        }
        activeEggmacGUIs.remove(playerUUID);
        playerEggOptions.remove(playerUUID);
    }
}


/*
Ende !


 */

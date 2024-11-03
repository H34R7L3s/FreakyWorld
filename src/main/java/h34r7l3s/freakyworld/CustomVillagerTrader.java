package h34r7l3s.freakyworld;
import java.util.stream.Collectors;

import io.th0rgal.oraxen.api.OraxenItems;
import io.th0rgal.oraxen.items.ItemBuilder;
import io.th0rgal.oraxen.*;
import org.apache.commons.lang3.text.WordUtils;
import org.bukkit.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static javax.management.remote.JMXConnectorFactory.connect;


public class CustomVillagerTrader implements Listener {

    public final Map<UUID, ItemStack> pendingItemConfirmations = new HashMap<>();

    private final JavaPlugin plugin;
    private Villager masterVillager;
    private BukkitTask soundTask;
    private static final List<Villager> spawnedVillagers = new ArrayList<>();
    private final DatabaseManagerTrader dbManager;

    private final Map<Player, BukkitTask> activeProcesses = new HashMap<>();
    private final Map<String, ItemStack> unlockableItems = new HashMap<>();
    private final Map<String, Integer> itemProductionTimes = new HashMap<>();
    private final Map<String, Map<String, Integer>> itemCosts = new HashMap<>();
    private final Set<Player> clickCooldown = Collections.newSetFromMap(new WeakHashMap<>()); // Doppelklick-Vermeidung
    private final Map<Player, AtomicBoolean> clickProcessing = new HashMap<>();
    private final Map<Player, Long> boostCooldowns = new HashMap<>();


    public CustomVillagerTrader(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dbManager = new DatabaseManagerTrader(new File(plugin.getDataFolder(), "trader.db"),plugin);

        setupMasterVillager();
        initializeUnlockableItems();
        refreshItemsHourly();

        // Lade laufende Produktionen aus der Datenbank und starte sie neu
        loadActiveProductions();

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void setupMasterVillager() {
        World world = plugin.getServer().getWorlds().get(0);
        Location loc = new Location(world, 9, 99, 61);
        masterVillager = spawnVillager(loc, "Meisterhändler");

        startMasterVillagerLookTask();
        //Bukkit.getScheduler().runTaskTimer(plugin, this::playSmithSounds, 0L, 20L); // Check every second
        startProductionCheckLoop();
    }

    private void startMasterVillagerLookTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (masterVillager != null && !masterVillager.isDead()) {
                    lookAtNearestPlayerMasterVillager();
                }
            }
        }.runTaskTimer(plugin, 0L, 40L); // alle 2 Sekunden (40 Ticks)
    }
    private void lookAtNearestPlayerMasterVillager() {
        Collection<Player> nearbyPlayers = masterVillager.getWorld().getNearbyPlayers(masterVillager.getLocation(), 10);
        Player nearestPlayer = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Player player : nearbyPlayers) {
            double distance = player.getLocation().distanceSquared(masterVillager.getLocation());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestPlayer = player;
            }
        }

        if (nearestPlayer != null) {
            Location villagerLocation = masterVillager.getLocation();
            Location playerLocation = nearestPlayer.getLocation();

            // Berechne die Richtung zum Spieler und setze die Rotation des Meisterhändlers
            double dx = playerLocation.getX() - villagerLocation.getX();
            double dz = playerLocation.getZ() - villagerLocation.getZ();
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            villagerLocation.setYaw(yaw);
            masterVillager.teleport(villagerLocation); // Aktualisiert die Blickrichtung des Meisterhändlers
        }
    }
    private void playSmithSounds() {
        Location smithLocation = masterVillager.getLocation();
        World world = masterVillager.getWorld();
        Random random = new Random();

        // Erweiterte Geräuschgruppen

// Geräusche direkt aus der Schmiede
        List<Sound> primarySounds = Arrays.asList(
                Sound.BLOCK_ANVIL_LAND, Sound.BLOCK_ANVIL_USE,
                Sound.BLOCK_ANVIL_BREAK, Sound.BLOCK_ANVIL_FALL,
                Sound.ENTITY_VILLAGER_WORK_TOOLSMITH, Sound.ENTITY_VILLAGER_WORK_WEAPONSMITH,
                Sound.BLOCK_SMITHING_TABLE_USE, Sound.BLOCK_BLASTFURNACE_FIRE_CRACKLE,
                Sound.ITEM_SHIELD_BLOCK, Sound.ITEM_FLINTANDSTEEL_USE,
                Sound.ENTITY_ITEM_BREAK, Sound.BLOCK_LAVA_POP,
                Sound.ITEM_ARMOR_EQUIP_IRON, Sound.ENTITY_IRON_GOLEM_REPAIR,
                Sound.BLOCK_FIRE_AMBIENT, Sound.BLOCK_IRON_DOOR_CLOSE,
                Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR  // Schmied schließt Türen oder es gibt Bedrohungen
        );

// Umgebungsgeräusche nahe der Schmiede (Dorfaktivitäten in der Nähe)
        List<Sound> ambientSounds = Arrays.asList(
                Sound.BLOCK_BELL_USE, Sound.BLOCK_STONE_BREAK,
                Sound.BLOCK_WOODEN_DOOR_OPEN, Sound.BLOCK_CHEST_OPEN,
                Sound.BLOCK_CHEST_CLOSE, Sound.BLOCK_CHAIN_PLACE,
                Sound.BLOCK_METAL_PRESSURE_PLATE_CLICK_ON, Sound.ENTITY_HORSE_AMBIENT,
                Sound.ENTITY_CAT_AMBIENT, Sound.ENTITY_VILLAGER_TRADE,
                Sound.ENTITY_IRON_GOLEM_STEP, Sound.BLOCK_NOTE_BLOCK_BASEDRUM,
                Sound.ENTITY_DONKEY_AMBIENT, Sound.BLOCK_FURNACE_FIRE_CRACKLE,
                Sound.BLOCK_GRAVEL_BREAK, Sound.ENTITY_PIG_AMBIENT,
                Sound.BLOCK_BEEHIVE_WORK  // Betriebsamkeit und Geräusche von dörflichem Leben nahe der Schmiede
        );

// Villager-Aktivitäten und Geräusche in der Nähe der Schmiede (passend zur Interaktion mit dem Schmied)
        List<Sound> villagerSounds = Arrays.asList(
                Sound.ENTITY_VILLAGER_AMBIENT, Sound.ENTITY_VILLAGER_YES,
                Sound.ENTITY_VILLAGER_NO, Sound.ENTITY_VILLAGER_TRADE,
                Sound.ENTITY_VILLAGER_WORK_ARMORER, Sound.ENTITY_VILLAGER_WORK_MASON,
                Sound.ENTITY_VILLAGER_CELEBRATE, Sound.ENTITY_VILLAGER_WORK_LEATHERWORKER,
                Sound.ENTITY_VILLAGER_HURT, Sound.ENTITY_VILLAGER_CELEBRATE,
                Sound.ENTITY_IRON_GOLEM_ATTACK, Sound.ENTITY_IRON_GOLEM_DAMAGE,
                Sound.ENTITY_WOLF_HOWL, Sound.ENTITY_WOLF_AMBIENT,
                Sound.ENTITY_VILLAGER_WORK_BUTCHER, Sound.ENTITY_VILLAGER_WORK_FLETCHER
        );

        // Anpassbare Lautstärken und Tonhöhen
        float[] primaryVolumes = {0.6f, 0.7f, 0.9f};
        float[] ambientVolumes = {0.2f, 0.4f, 0.6f};
        float[] villagerVolumes = {0.2f, 0.5f, 0.6f};

        float[] pitches = {0.5f, 0.6f, 0.8f};

        // Spielt die Sounds für jede Kategorie mit unterschiedlichen Rhythmen
        playSoundGroup(world, smithLocation, primarySounds, primaryVolumes, pitches, random.nextInt(3) + 3, 40, 80);
        playSoundGroup(world, smithLocation, ambientSounds, ambientVolumes, pitches, random.nextInt(2) + 1, 100, 200);
        playSoundGroup(world, smithLocation, villagerSounds, villagerVolumes, pitches, random.nextInt(2) + 1, 150, 300);

        // Zufällige Funken- und Partikeleffekte
        if (random.nextInt(100) < 60) { // 10% Chance für Funken
            world.spawnParticle(Particle.FLAME, smithLocation.add(0, 1, 0), 20, 0.3, 0.3, 0.3, 0.05);
        }
        if (random.nextInt(100) < 50) { // 5% Chance für Schmelzeffekt
            world.spawnParticle(Particle.LARGE_SMOKE, smithLocation.add(0, 1, 0), 10, 0.2, 0.2, 0.2, 0.02);
            world.playSound(smithLocation, Sound.BLOCK_FURNACE_FIRE_CRACKLE, SoundCategory.BLOCKS, 0.6f, 0.9f);
        }
    }

    private void playSoundGroup(World world, Location location, List<Sound> sounds, float[] volumes, float[] pitches, int numSounds, int minDelay, int maxDelay) {
        Random random = new Random();
        for (int i = 0; i < numSounds; i++) {
            Sound sound = sounds.get(random.nextInt(sounds.size()));
            float volume = volumes[random.nextInt(volumes.length)];
            float pitch = pitches[random.nextInt(pitches.length)];
            world.playSound(location, sound, SoundCategory.BLOCKS, volume, pitch);

            // Zufällige Verzögerung zwischen den Sounds in der Gruppe für zusätzliche Dynamik
            int groupDelay = random.nextInt(maxDelay - minDelay + 1) + minDelay;
            try {
                Thread.sleep(groupDelay);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


    // Aufruf in der `onProductionStateChange` Methode:
    private void startProductionCheckLoop() {
        new BukkitRunnable() {
            @Override
            public void run() {
                checkAndHandleProductionState();
            }
        }.runTaskTimer(plugin, 0L, 85L); // Alle 10 Sekunden (200 Ticks) überprüfen
    }

    private void checkAndHandleProductionState() {
        boolean hasActiveProductions = hasActiveProductionsInDatabase();

        if (hasActiveProductions && (soundTask == null || soundTask.isCancelled())) {
            startPlayingSounds(); // Sounds starten, wenn aktive Produktionen vorhanden sind und noch keine Sounds laufen
        } else if (!hasActiveProductions && soundTask != null && !soundTask.isCancelled()) {
            soundTask.cancel(); // Sounds stoppen, wenn keine aktiven Produktionen mehr vorhanden sind
            soundTask = null;
        }
    }

    private boolean hasActiveProductionsInDatabase() {
        List<UUID> playersWithActiveProductions = dbManager.getPlayersWithActiveProductions();
        return !playersWithActiveProductions.isEmpty(); // Wenn die Liste nicht leer ist, gibt es aktive Produktionen
    }


    // Methode zum Starten des Sound-Loops, wenn ein Herstellungsprozess beginnt
    private void startPlayingSounds() {
        playSmithSounds(); // Die Methode zum Abspielen der Sounds aufrufen

        // Sicherstellen, dass nur eine Instanz des Sound-Loops läuft
        soundTask = Bukkit.getScheduler().runTaskTimer(plugin, this::playSmithSounds, 0L, 200L);
    }

    // Methode, die aufgerufen wird, wenn ein Herstellungsprozess beginnt oder endet





    private Villager spawnVillager(Location loc, String name) {
        Villager villager = loc.getWorld().spawn(loc, Villager.class);
        villager.setCustomName(name);
        villager.setCanPickupItems(false);
        villager.setProfession(Villager.Profession.WEAPONSMITH);
        villager.setAI(false);
        villager.setInvulnerable(true);
        spawnedVillagers.add(villager);
        return villager;
    }

    public static void removeVillagers() {
        for (Villager villager : spawnedVillagers) {
            if (!villager.isDead()) {
                villager.remove();
            }
        }
        spawnedVillagers.clear();
    }

    private void initializeUnlockableItems() {
        // Waffen
        addUnlockableItem("legendary_sword", "weapons", 2419200, Map.of("freaky_coin", 7, "freaky_ingot", 85,"auftragsbuch", 3, "freaky_wissen", 5, "kriegsmarke", 1, "eisenherz", 1,  "freakyworlds_willen", 1));
        addUnlockableItem("vampir", "weapons", 2419200, Map.of("freaky_coin", 25, "freaky_ingot", 100,"auftragsbuch", 25, "freaky_wissen", 20, "kriegsmarke", 12, "eisenherz", 13,  "freakyworlds_willen", 25));
        addUnlockableItem("lightning_arrow", "weapons", 2419200, Map.of("freaky_coin", 5, "freaky_ingot", 3,"auftragsbuch", 5, "freaky_wissen", 5, "kriegsmarke", 7, "eisenherz", 3,  "freakyworlds_willen", 10));
        //addUnlockableItem("Woorpy", "weapons", 3600, Map.of("freaky_coin", 10, "freaky_ingot", 1,"auftragsbuch", 1, "freaky_wissen", 1, "kriegsmarke", 1, "eisenherz", 1,  "freakyworlds_willen", 1));
        addUnlockableItem("schattenklinge", "weapons", 2419200, Map.of("freaky_coin", 25, "freaky_ingot", 100,"auftragsbuch", 25, "freaky_wissen", 20, "kriegsmarke", 10, "eisenherz", 11,  "freakyworlds_willen", 25));
        addUnlockableItem("xpvamp", "weapons", 2419200, Map.of("freaky_coin", 25, "freaky_ingot", 100,"auftragsbuch", 25, "freaky_wissen", 20, "kriegsmarke", 10, "eisenherz", 11,  "freakyworlds_willen", 25));
        addUnlockableItem("sturmwind", "weapons", 2419200, Map.of("freaky_coin", 25, "freaky_ingot", 200,"auftragsbuch", 50, "freaky_wissen", 40, "kriegsmarke", 20, "eisenherz", 11,  "freakyworlds_willen", 25));




        // Rüstungen
        //Wind-Rüstung
        addUnlockableItem("sky_crown", "armor", 777600 , Map.of("freaky_coin", 10, "freaky_ingot", 30,"auftragsbuch", 10, "freaky_wissen", 10, "kriegsmarke", 25, "eisenherz", 28,  "freakyworlds_willen", 5));
        addUnlockableItem("sky_guard", "armor", 864000 , Map.of("freaky_coin", 10, "freaky_ingot", 100,"auftragsbuch", 10, "freaky_wissen", 10, "kriegsmarke", 25, "eisenherz", 28,  "freakyworlds_willen", 5));
        addUnlockableItem("sky_boots", "armor", 777600 , Map.of("freaky_coin", 10, "freaky_ingot", 25,"auftragsbuch", 10, "freaky_wissen", 10, "kriegsmarke", 25, "eisenherz", 28,  "freakyworlds_willen", 5));
        addUnlockableItem("sky_leggings", "armor", 846720 , Map.of("freaky_coin", 10, "freaky_ingot", 75,"auftragsbuch", 10, "freaky_wissen", 10, "kriegsmarke", 25, "eisenherz", 28,  "freakyworlds_willen", 5));

        //Fire-Rüstung
        addUnlockableItem("fire_crown", "armor", 777600 , Map.of("freaky_coin", 10, "freaky_ingot", 30,"auftragsbuch", 10, "freaky_wissen", 10, "kriegsmarke", 25, "eisenherz", 28,  "freakyworlds_willen", 5));
        addUnlockableItem("fire_guard", "armor", 864000 , Map.of("freaky_coin", 10, "freaky_ingot", 100,"auftragsbuch", 10, "freaky_wissen", 10, "kriegsmarke", 25, "eisenherz", 28,  "freakyworlds_willen", 5));
        addUnlockableItem("fire_boots", "armor", 777600 , Map.of("freaky_coin", 10, "freaky_ingot", 25,"auftragsbuch", 10, "freaky_wissen", 10, "kriegsmarke", 25, "eisenherz", 28,  "freakyworlds_willen", 5));
        addUnlockableItem("fire_leggings", "armor", 846720 , Map.of("freaky_coin", 10, "freaky_ingot", 75,"auftragsbuch", 10, "freaky_wissen", 10, "kriegsmarke", 25, "eisenherz", 28,  "freakyworlds_willen", 5));
        //Water-Rüstung
        addUnlockableItem("water_crown", "armor", 777600 , Map.of("freaky_coin", 10, "freaky_ingot", 30,"auftragsbuch", 10, "freaky_wissen", 10, "kriegsmarke", 25, "eisenherz", 28,  "freakyworlds_willen", 5));
        addUnlockableItem("water_guard", "armor", 864000 , Map.of("freaky_coin", 10, "freaky_ingot", 100,"auftragsbuch", 10, "freaky_wissen", 10, "kriegsmarke", 25, "eisenherz", 28,  "freakyworlds_willen", 5));
        addUnlockableItem("water_boots", "armor", 777600 , Map.of("freaky_coin", 10, "freaky_ingot", 25,"auftragsbuch", 10, "freaky_wissen", 10, "kriegsmarke", 25, "eisenherz", 28,  "freakyworlds_willen", 5));
        addUnlockableItem("water_leggings", "armor", 846720 , Map.of("freaky_coin", 10, "freaky_ingot", 75,"auftragsbuch", 10, "freaky_wissen", 10, "kriegsmarke", 25, "eisenherz", 28,  "freakyworlds_willen", 5));//Stone-Rüstung

        //Stone-Rüstung
        addUnlockableItem("stone_crown", "armor", 777600, Map.of("freaky_coin", 10, "freaky_ingot", 30,"auftragsbuch", 10, "freaky_wissen", 10, "kriegsmarke", 25, "eisenherz", 28,  "freakyworlds_willen", 5));
        addUnlockableItem("stone_guard", "armor", 846720 , Map.of("freaky_coin", 10, "freaky_ingot", 100,"auftragsbuch", 10, "freaky_wissen", 10, "kriegsmarke", 25, "eisenherz", 28,  "freakyworlds_willen", 5));
        addUnlockableItem("stone_boots", "armor", 777600 , Map.of("freaky_coin", 10, "freaky_ingot", 25,"auftragsbuch", 10, "freaky_wissen", 10, "kriegsmarke", 25, "eisenherz", 28,  "freakyworlds_willen", 5));
        addUnlockableItem("stone_leggings", "armor", 846720 , Map.of("freaky_coin", 10, "freaky_ingot", 75,"auftragsbuch", 10, "freaky_wissen", 10, "kriegsmarke", 25, "eisenherz", 28,  "freakyworlds_willen", 5));//Stone-Rüstung


        // Werkzeuge
        addUnlockableItem("silkspawn_pickaxe", "tools", 172800, Map.of("freaky_coin", 1, "freaky_ingot", 5,"auftragsbuch", 1, "freaky_wissen", 2 ));
        addUnlockableItem("legendary_hoe", "tools", 432000, Map.of("freaky_coin", 5, "freaky_ingot", 3,"auftragsbuch", 1, "freaky_wissen", 4,  "freakyworlds_willen", 1));
        addUnlockableItem("legendary_pickaxe1", "tools", 604800, Map.of("freaky_coin", 10, "freaky_ingot", 15,"auftragsbuch", 3, "freaky_wissen", 4, "freakyworlds_willen", 2));
        addUnlockableItem("timberaxt", "tools", 604800, Map.of("freaky_coin", 10, "freaky_ingot", 12,"auftragsbuch", 15, "freaky_wissen", 4, "freakyworlds_willen", 5));
        addUnlockableItem("legendary_pickaxe", "tools", 604800, Map.of("freaky_coin", 5, "freaky_ingot", 11,"auftragsbuch", 2, "freaky_wissen", 2, "freakyworlds_willen", 1));

        // Magische Artefakte
        addUnlockableItem("aura_of_bloom", "magical", 2419200, Map.of("freaky_coin", 14, "freaky_ingot", 3,"auftragsbuch", 10, "freaky_wissen", 10,"freakyworlds_willen", 20));
        addUnlockableItem("experience_orb", "magical", 2419200, Map.of("freaky_coin", 21, "freaky_ingot", 35,"auftragsbuch", 10, "freaky_wissen", 5, "kriegsmarke", 11, "eisenherz", 9,  "freakyworlds_willen", 14));
        addUnlockableItem("staff_of_wisdom", "magical", 432000, Map.of("freaky_coin", 12, "freaky_ingot", 8,"auftragsbuch", 10, "freaky_wissen", 16, "kriegsmarke", 2, "eisenherz", 4,  "freakyworlds_willen", 6));
        addUnlockableItem("blue_lantern_of_doom", "magical", 2419200, Map.of("freaky_coin", 25, "freaky_ingot", 35,"auftragsbuch", 10, "freaky_wissen", 15, "kriegsmarke", 13, "eisenherz", 9,  "freakyworlds_willen", 24));
        addUnlockableItem("eagle_eye", "magical", 432000, Map.of("freaky_coin", 1, "freaky_ingot", 5,"auftragsbuch", 1, "freaky_wissen", 2,  "eisenherz", 1 ));


        // Sonstige
        //addUnlockableItem("gold", "misc", 600, Map.of("freaky_coin", 10, "freaky_ingot", 1,"auftragsbuch", 1, "freaky_wissen", 1, "kriegsmarke", 1, "eisenherz", 1,  "freakyworlds_willen", 1));
        //addUnlockableItem("silber", "misc", 600, Map.of("freaky_coin", 10, "freaky_ingot", 1,"auftragsbuch", 1, "freaky_wissen", 1, "kriegsmarke", 1, "eisenherz", 1,  "freakyworlds_willen", 1));
        //addUnlockableItem("gildenhome", "misc", 600, Map.of("freaky_coin", 10, "freaky_ingot", 1,"auftragsbuch", 1, "freaky_wissen", 1, "kriegsmarke", 1, "eisenherz", 1,  "freakyworlds_willen", 1));
        addUnlockableItem("eggmac", "misc", 5100, Map.of("freaky_coin", 10, "freaky_ingot", 1,"auftragsbuch", 1, "freaky_wissen", 1, "kriegsmarke", 1, "eisenherz", 1,  "freakyworlds_willen", 1));
        addUnlockableItem("freaky_gold", "misc", 6300, Map.of("freaky_coin", 2, "freaky_ingot", 10));

        //Möbel Set 1
        addUnlockableItem("table", "misc", 72800, Map.of("freaky_coin", 5, "freaky_ingot", 2));
        addUnlockableItem("cart", "misc", 62400, Map.of("freaky_coin", 5, "freaky_ingot", 2));
        addUnlockableItem("chair", "misc", 52400, Map.of("freaky_coin", 5, "freaky_ingot", 2));
        addUnlockableItem("coach", "misc", 42700, Map.of("freaky_coin", 5, "freaky_ingot", 2));
        addUnlockableItem("shelf", "misc", 32800, Map.of("freaky_coin", 5, "freaky_ingot", 2));

        //Möbel Set 2
        addUnlockableItem("forest_area_rug", "misc", 72800, Map.of("freaky_coin", 5, "freaky_ingot", 2));
        addUnlockableItem("forest_armchair", "misc", 62400, Map.of("freaky_coin", 5, "freaky_ingot", 2));
        addUnlockableItem("forest_bed", "misc", 52400, Map.of("freaky_coin", 5, "freaky_ingot", 2));
        addUnlockableItem("forest_chandelier", "misc", 42700, Map.of("freaky_coin", 5, "freaky_ingot", 2));
        addUnlockableItem("forest_endtable", "misc", 32800, Map.of("freaky_coin", 5, "freaky_ingot", 2));
        addUnlockableItem("forest_screen", "misc", 52400, Map.of("freaky_coin", 5, "freaky_ingot", 2));
        addUnlockableItem("forest_sofa", "misc", 42700, Map.of("freaky_coin", 5, "freaky_ingot", 2));
        addUnlockableItem("forest_wall_lamp", "misc", 32800, Map.of("freaky_coin", 5, "freaky_ingot", 2));



    }

    private void addUnlockableItem(String itemId, String category, int productionTime, Map<String, Integer> cost) {
        ItemBuilder itemBuilder = OraxenItems.getItemById(itemId);
        if (itemBuilder == null) {
            plugin.getLogger().warning("Oraxen-Item mit ID " + itemId + " konnte nicht gefunden werden.");
            return; // Abbrechen, wenn das Item nicht gefunden wurde
        }

        ItemStack itemStack = itemBuilder.build();
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            // Setze den Display-Namen, falls keiner vorhanden ist
            if (!meta.hasDisplayName()) {
                meta.setDisplayName(ChatColor.YELLOW + itemId); // Fallback auf die Item-ID als Name
            }

            List<String> lore = meta.getLore() != null ? meta.getLore() : new ArrayList<>();

            // Kosten hinzufügen, wenn sie noch nicht vorhanden sind
            if (!lore.contains(ChatColor.GOLD + "Kosten:")) {
                lore.add(ChatColor.GOLD + "Kosten:");
                cost.forEach((costItem, amount) -> {
                    ItemBuilder costItemBuilder = OraxenItems.getItemById(costItem);
                    if (costItemBuilder == null) {
                        plugin.getLogger().warning("Kosten-Item mit ID " + costItem + " konnte nicht gefunden werden.");
                        return; // Überspringe diesen Kosten-Eintrag, wenn er nicht gefunden wird
                    }

                    ItemStack costItemStack = costItemBuilder.build();
                    String costName = (costItemStack.getItemMeta() != null && costItemStack.getItemMeta().hasDisplayName())
                            ? costItemStack.getItemMeta().getDisplayName()
                            : costItem; // Fallback auf die Item-ID, wenn kein DisplayName vorhanden ist

                    lore.add(ChatColor.YELLOW + "- " + amount + "x " + costName);

                });
            }
            // Nur einmal leere Zeile und Herstellungszeit hinzufügen
            if (!lore.contains(ChatColor.YELLOW + "Meisterlich gefertigte Unikate")) {
                lore.add(ChatColor.YELLOW + "");
                lore.add(ChatColor.YELLOW + "Meisterlich gefertigte Unikate");
            }

            // Herstellungszeit nur einmal hinzufügen
            String formattedProductionTime = ChatColor.RED + "Herstellungszeit: " + formatDuration(productionTime);
            if (!lore.contains(formattedProductionTime)) {
                lore.add(formattedProductionTime);
            }

            meta.setLore(lore);
            itemStack.setItemMeta(meta);
        }


        // Füge das Item zu den freischaltbaren Items hinzu
        unlockableItems.put(category + ":" + itemId, itemStack);
        itemProductionTimes.put(category + ":" + itemId, productionTime);
        itemCosts.put(category + ":" + itemId, cost);
    }






    private void refreshItemsHourly() {
        long hourInTicks = 20L * 60L * 60L;
        Bukkit.getScheduler().runTaskTimer(plugin, this::resetItems, 0L, hourInTicks);
    }

    private void resetItems() {
        initializeUnlockableItems();
        Bukkit.broadcastMessage(ChatColor.GREEN + "Die Handelswaren des Meisterhändlers wurden erneuert!");
    }

    private final List<String> smithTips = Arrays.asList(
            "Ein scharfes Schwert schneidet durch jedes Problem!",
            "Gute Rüstung ist der halbe Sieg!",
            "Ein Werkzeug ist nur so gut wie der Handwerker, der es führt!",
            "Der Amboss singt, wenn das Metall sich beugt.",
            "Ein Funke der Inspiration kann das heißeste Feuer entzünden.",
            "Ach, die Hitze des Schmiedefeuers... Man gewöhnt sich dran.",
            "Manchmal frage ich mich, wer mehr glüht – das Metall oder ich.",
            "Du willst das wirklich? Naja, deine Entscheidung...",
            "Noch ein Tag, noch ein Schwert... immer dasselbe.",
            "Ich wünschte, das Metall würde weniger widerspenstig sein... wie meine Kinder.",
            "Was glänzt, ist nicht immer Gold... aber meistens Eisen.",
            "Ein schwerer Hammer für schwere Zeiten.",
            "Ein Schmied muss auch mal Dampf ablassen, wenn’s brenzlig wird.",
            "Hast du jemals überlegt, was passiert, wenn ich mal 'ne Pause mache? Gar nichts!",
            "Das Leben eines Schmieds ist nicht leicht... aber irgendwer muss es ja tun.",
            "Ich könnte dich unterrichten... aber dann müsstest du das doppelte bezahlen.",
            "Noch so ein Tag, und ich werde selbst zu Eisen.",
            "Weißt du, manchmal wäre ein einfaches Leben ganz schön... aber dann wird's auch langweilig.",
            "Ein Schmied ist nur so gut wie sein Feuer. Und mein Feuer ist heiß.",
            "Die Kunst des Schmiedens ist wie die Liebe – heiß, intensiv und manchmal schmerzhaft.",
            "Jedes Werkzeug hat seine Zeit... wie dieser alte Hammer.",
            "Du willst ein Schwert? Aber kannst du es auch führen?",
            "Manchmal schlage ich einfach drauf los und hoffe, es wird was Gutes.",
            "Ein ruhiger Tag in der Schmiede? Das gibt es nicht!",
            "Ich habe den ganzen Tag Hämmer geschwungen... und jetzt kommst du?",
            "Ein guter Schmied vergießt mehr Schweiß als Blut.",
            "Ich mache nicht nur Waffen, ich mache auch Erinnerungen.",
            "Die Rüstung schützt dich... aber nur, wenn du dich nicht davor drückst.",
            "Manchmal möchte ich einfach den Hammer fallen lassen und davonlaufen... aber dann erinnere ich mich an die Miete.",
            "Eine kalte Nacht? Für mich ist es immer heiß.",
            "Noch ein Kunde... ich hoffe, du hast Geduld.",
            "Das Feuer brennt, der Hammer fällt, und die Arbeit geht weiter.",
            "Weißt du, was wirklich hart ist? Mein Amboss. Frag ihn mal.",
            "Jedes Mal, wenn ich ein Schwert schmiede, lasse ich ein kleines Stück von mir darin.",
            "Die Kunst des Schmiedens? Mehr Rauch, weniger Reden.",
            "Manchmal wünsche ich mir, ich könnte auch mal glänzen... wie das Metall.",
            "Ein weiteres Schwert, ein weiterer Tag... und das Feuer brennt weiter.",
            "Schmieden ist wie ein Tanz – heiß, intensiv und rhythmisch.",
            "Du hast Fragen? Ich habe Hämmer. Lass uns sehen, wer gewinnt.",
            "Ein heißes Feuer ist wie ein guter Freund – es brennt, wenn du es brauchst.",
            "Ich mache keine Fehler... ich mache Unikate.",
            "Warum immer nur Schwerter? Was ist mit etwas Kunst?",
            "Du denkst, das ist leicht? Versuche mal, 1000 Grad zu widerstehen!",
            "Ein Funke reicht aus, um ein Feuer zu entfachen... und eine Schmiede am Laufen zu halten.",
            "Noch ein Tag, noch ein Hammerschlag... und ich werde nicht müde.",
            "Manchmal frage ich mich, wer hier wirklich die Kontrolle hat – ich oder das Feuer.",
            "Ein Schmied ist mehr als ein Handwerker... er ist ein Künstler des Feuers.",
            "Wenn es nicht funkt, war es nicht heiß genug.",
            "Jedes Stück Metall hat eine Seele... und ich bringe sie zum Leuchten.",
            "Du bringst mir einen kaputten Schild? Das ist alles, was ich sehe – Herausforderungen!"
    );

    @EventHandler
    public void onPlayerInteractEntityTrade(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof Villager) {
            Villager villager = (Villager) event.getRightClicked();
            if (villager.equals(masterVillager)) {
                Player player = event.getPlayer();

                // Verhindere Doppelklicks
                if (clickCooldown.contains(player)) return;
                clickCooldown.add(player);

                // Starte die interaktive Begrüßung und das Menü schrittweise
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        // Interaktive Begrüßung mit Effekten und Sounds
                        interactWithPlayer(player);

                        // Verzögerung um den Spieler in die Begrüßung einzutauchen
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                // Öffne das Hauptmenü nach den Begrüßungseffekten
                                openMainMenu(player);
                            }
                        }.runTaskLater(plugin, 120L); // Öffne das Menü nach 3 Sekunden
                    }
                }.runTask(plugin);

                event.setCancelled(true);

                // Entferne den Spieler nach 0,5 Sekunden aus der Doppelklick-Liste
                Bukkit.getScheduler().runTaskLater(plugin, () -> clickCooldown.remove(player), 10L);
            }
        }
    }


    private void openMainMenu(Player player) {
        Inventory mainMenu = Bukkit.createInventory(null, 27, "Hauptmenü - Kategorien");

        mainMenu.setItem(11, createMenuItem(Material.DIAMOND_SWORD, ChatColor.RED + "Waffen"));
        mainMenu.setItem(12, createMenuItem(Material.IRON_CHESTPLATE, ChatColor.BLUE + "Rüstungen"));
        mainMenu.setItem(13, createMenuItem(Material.DIAMOND_PICKAXE, ChatColor.GOLD + "Werkzeuge"));
        mainMenu.setItem(14, createMenuItem(Material.BLAZE_ROD, ChatColor.LIGHT_PURPLE + "Magische Artefakte"));
        mainMenu.setItem(15, createMenuItem(Material.APPLE, ChatColor.GREEN + "Sonstiges"));

        // Slot für den aktuellen Herstellungsprozess
        int activeProcessSlot = 22;
        mainMenu.setItem(activeProcessSlot, createActiveProcessItem(player));

        // Boost-Icons
        mainMenu.setItem(20, createBoostItem(player, "freaky_ingot", ChatColor.GOLD + "Verkürze Zeit um 10 Minuten", ChatColor.GRAY + "Nutze einen Freaky Barren, um 10 Minuten zu verkürzen."));
        mainMenu.setItem(24, createBoostItem(player, "freaky_gold", ChatColor.YELLOW + "Verkürze Zeit um 30 Minuten", ChatColor.GRAY + "Nutze Freaky Gold, um 30 Minuten zu verkürzen."));

        // Truhe für fertige Items
        mainMenu.setItem(26, createCompletedItemsChest(player));

        // Herstellung von freaky_coin
        mainMenu.setItem(16, createMenuItem(Material.GOLD_NUGGET, ChatColor.GOLD + "Freaky Coin herstellen"));

        player.openInventory(mainMenu);
          // Interaktive Unterhaltung

        // Start a task to update the active process item every second
        BukkitRunnable updater = new BukkitRunnable() {
            @Override
            public void run() {
                if (player.getOpenInventory() != null && player.getOpenInventory().getTitle().equals("Hauptmenü - Kategorien")) {
                    mainMenu.setItem(activeProcessSlot, createActiveProcessItem(player));
                } else {
                    this.cancel(); // Stop the task if the player closes the inventory
                }
            }
        };
        updater.runTaskTimer(plugin, 0L, 20L); // Update every second
    }

    private ItemStack createMenuItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String formatDuration(long seconds) {
        long days = seconds / 86400;
        seconds %= 86400;

        long hours = seconds / 3600;
        seconds %= 3600;

        long minutes = seconds / 60;
        seconds %= 60;

        StringBuilder formattedTime = new StringBuilder();
        if (days > 0) {
            formattedTime.append(days).append(" Tag(e) ");
        }
        if (hours > 0) {
            formattedTime.append(hours).append(" Stunde(n) ");
        }
        if (minutes > 0) {
            formattedTime.append(minutes).append(" Minute(n) ");
        }
        if (seconds > 0 || formattedTime.length() == 0) {
            formattedTime.append(seconds).append(" Sekunde(n)");
        }

        return formattedTime.toString().trim();
    }



    private ItemStack createActiveProcessItem(Player player) {
        ItemStack activeItem = new ItemStack(Material.ANVIL);
        ItemMeta meta = activeItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GREEN + "Aktiver Herstellungsprozess");
            List<String> lore = new ArrayList<>();
            Long endTime = dbManager.getProductionEndTime(player.getUniqueId());
            if (endTime != null) {
                long timeLeft = Math.max(0, (endTime - System.currentTimeMillis()) / 1000);
                lore.add(ChatColor.YELLOW + "Verbleibende Zeit: " + formatDuration(timeLeft));
                ItemStack itemInProduction = dbManager.getCurrentProductionItem(player.getUniqueId());
                if (itemInProduction != null && itemInProduction.getItemMeta() != null) {
                    lore.add(ChatColor.GRAY + "Bring Freaky Barren oder Freaky Gold und es geht schneller!");
                } else {
                    lore.add(ChatColor.RED + "Kein gültiges Item in Produktion.");
                }
            } else {
                lore.add(ChatColor.RED + "Keine aktiven Produktionen.");
            }
            meta.setLore(lore);
            activeItem.setItemMeta(meta);
        }
        return activeItem;
    }


    private ItemStack createBoostItem(Player player, String boostItemId, String displayName, String description) {
        ItemStack boostItem = OraxenItems.getItemById(boostItemId).build();
        if (boostItem != null) {
            ItemMeta meta = boostItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(displayName);
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Klicke hier, um die Zeit zu verkürzen.");
                lore.add(description);  // Beschreibung hinzufügen
                meta.setLore(lore);
                boostItem.setItemMeta(meta);
            }
        }
        return boostItem;
    }

    private ItemStack createCompletedItemsChest(Player player) {
        ItemStack chestItem = new ItemStack(Material.CHEST);
        ItemMeta meta = chestItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GREEN + "Abholbare Items");
            List<String> lore = new ArrayList<>();
            List<ItemStack> items = dbManager.getCompletedItems(player.getUniqueId());
            if (items == null || items.isEmpty()) {
                lore.add(ChatColor.GRAY + "Keine fertigen Items.");
            } else {
                lore.add(ChatColor.YELLOW + "Klicke hier, um deine fertigen Items abzuholen.");
            }
            meta.setLore(lore);
            chestItem.setItemMeta(meta);
        }
        return chestItem;
    }

    private void openCategoryMenu(Player player, String category) {
        Inventory categoryMenu = Bukkit.createInventory(null, 54, "Kategorie: " + category);

        // Sortiere die Items alphabetisch nach ihrem Display-Namen
        List<Map.Entry<String, ItemStack>> sortedItems = unlockableItems.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(category + ":")) // Nur Items der aktuellen Kategorie
                .sorted(Comparator.comparing(entry -> ChatColor.stripColor(entry.getValue().getItemMeta().getDisplayName()))) // Alphabetisch nach dem Namen sortieren
                .collect(Collectors.toList());

        // Füge die sortierten Items in die GUI ein
        int slot = 0;
        for (Map.Entry<String, ItemStack> entry : sortedItems) {
            categoryMenu.setItem(slot++, entry.getValue());
        }
        // Slot für den aktuellen Herstellungsprozess
        //categoryMenu.setItem(49, createActiveProcessItem(player));
        // Slot für den aktuellen Herstellungsprozess
        categoryMenu.setItem(53, createBackButton());

        player.openInventory(categoryMenu);
    }


    private ItemStack createBackButton() {
        ItemStack backButton = new ItemStack(Material.ARROW); // Du kannst das Material des Buttons nach Belieben ändern
        ItemMeta meta = backButton.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "Zurück zum Hauptmenü");
            backButton.setItemMeta(meta);
        }
        return backButton;
    }

    private void openConfirmationMenu(Player player, ItemStack itemStack) {
        Inventory confirmationMenu = Bukkit.createInventory(null, 27, "Bestätigung");

        // Bestätigen-Button
        ItemStack confirmItem = new ItemStack(Material.GREEN_WOOL);
        ItemMeta confirmMeta = confirmItem.getItemMeta();
        if (confirmMeta != null) {
            confirmMeta.setDisplayName(ChatColor.GREEN + "Bestätigen");
            confirmItem.setItemMeta(confirmMeta);
        }

        // Abbrechen-Button
        ItemStack cancelItem = new ItemStack(Material.RED_WOOL);
        ItemMeta cancelMeta = cancelItem.getItemMeta();
        if (cancelMeta != null) {
            cancelMeta.setDisplayName(ChatColor.RED + "Abbrechen");
            cancelItem.setItemMeta(cancelMeta);
        }

        // Vorschau des zu produzierenden Items
        ItemStack itemPreview = itemStack.clone();
        ItemMeta previewMeta = itemPreview.getItemMeta();
        if (previewMeta != null) {
            List<String> previewLore = previewMeta.hasLore() ? previewMeta.getLore() : new ArrayList<>();
            previewLore.add("");
            previewLore.add(ChatColor.GRAY + "Möchtest du dieses Item herstellen?");
            previewMeta.setLore(previewLore);
            itemPreview.setItemMeta(previewMeta);
        }

        // Setze die Items ins Bestätigungsmenü
        confirmationMenu.setItem(11, confirmItem);
        confirmationMenu.setItem(15, cancelItem);
        confirmationMenu.setItem(13, itemPreview);

        // Öffne das Bestätigungsinventar für den Spieler
        player.openInventory(confirmationMenu);

        // Speichere das Item sowohl in die Datenbank als auch in die temporäre Map
        pendingItemConfirmations.put(player.getUniqueId(), itemStack);
        plugin.getLogger().info("Pending item for confirmation saved in temporary map for UUID: " + player.getUniqueId());

        // Speichere es weiterhin in die Datenbank
        savePendingConfirmation(player.getUniqueId(), itemPreview);
    }




    @EventHandler
    public void onInventoryClickTrader(InventoryClickEvent event) {
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;

        InventoryView view = event.getView();
        String title = view.getTitle();
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        // Überprüfe nur spezielle Inventare, wie das Händlermenü
        if (title != null && (title.startsWith("Hauptmenü") || title.startsWith("Kategorie:") || title.startsWith("Freaky Coin Herstellung") || title.startsWith("Bestätigung"))) {
            // Debugging: Überprüfe das clickedItem
            if (clickedItem.getItemMeta() != null) {
                String clickedItemName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
                plugin.getLogger().info("Player clicked on item: " + clickedItemName);
            } else {
                plugin.getLogger().warning("Clicked item has no meta!");
            }

            handleSingleClick(player, view, clickedItem);
            event.setCancelled(true);  // Verhindere weitere Klickaktionen, nachdem sie verarbeitet wurden
        }
    }




    private void handleSingleClick(Player player, InventoryView view, ItemStack clickedItem) {
        String title = view.getTitle();

        // Überprüfe, ob bereits ein Klick verarbeitet wird
        AtomicBoolean isProcessing = clickProcessing.getOrDefault(player, new AtomicBoolean(false));

        // Verarbeite den Klick nur, wenn kein anderer Klick verarbeitet wird
        if (isProcessing.compareAndSet(false, true)) {
            try {
                if (clickedItem == null || clickedItem.getItemMeta() == null) {
                    player.sendMessage(ChatColor.RED + "Es wurde kein gültiges Item ausgewählt.");
                    plugin.getLogger().warning("Clicked item or its meta is null for player " + player.getName());
                    return;
                }

                String displayName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
                plugin.getLogger().info("Player clicked item: " + displayName + " in menu: " + title);

                // Zurück zum Hauptmenü
                if (displayName.equals("Zurück zum Hauptmenü")) {
                    openMainMenu(player);
                    return;
                }

                // Hauptmenü - Kategorien
                if (title != null && title.equals("Hauptmenü - Kategorien")) {
                    switch (displayName) {
                        case "Waffen":
                            openCategoryMenu(player, "weapons");
                            break;
                        case "Rüstungen":
                            openCategoryMenu(player, "armor");
                            break;
                        case "Werkzeuge":
                            openCategoryMenu(player, "tools");
                            break;
                        case "Magische Artefakte":
                            openCategoryMenu(player, "magical");
                            break;
                        case "Sonstiges":
                            openCategoryMenu(player, "misc");
                            break;
                        case "Aktiver Herstellungsprozess":
                            player.sendMessage(ChatColor.YELLOW + "Du hast einen aktiven Herstellungsprozess.");
                            break;
                        case "Verkürze Zeit um 10 Minuten":
                            applyBoost(player, "freaky_ingot");
                            break;
                        case "Verkürze Zeit um 30 Minuten":
                            applyBoost(player, "freaky_gold");
                            break;
                        case "Abholbare Items":
                            collectCompletedItems(player);
                            openMainMenu(player);
                            break;
                        case "Freaky Coin herstellen":
                            openCoinProductionMenu(player);
                            break;
                        default:
                            plugin.getLogger().warning("Unbekannte Option ausgewählt: " + displayName);
                            player.sendMessage("Unbekannte Option ausgewählt: " + displayName);
                            break;
                    }
                }
                // Freaky Coin Herstellung Menü
                else if (title != null && title.equals("Freaky Coin Herstellung")) {
                    if (displayName.equals("Mit Silber (30 Minuten)")) {
                        startCoinProduction(player, "silber", 1800); // 1800 Sekunden = 30 Minuten
                        openMainMenu(player);
                    } else if (displayName.equals("Mit Gold (10 Minuten)")) {
                        startCoinProduction(player, "gold", 600); // 600 Sekunden = 10 Minuten
                        openMainMenu(player);
                    }
                }
                // Kategorie-Menü: Überprüfung der Item-Auswahl
                else if (title != null && title.startsWith("Kategorie: ")) {
                    String itemName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
                    plugin.getLogger().info("Item selected in category: " + itemName);

                    // Aktueller Herstellungsprozess wird angezeigt
                    if (itemName.equals(ChatColor.stripColor(createActiveProcessItem(player).getItemMeta().getDisplayName()))) {
                        player.sendMessage(ChatColor.YELLOW + "Aktueller Herstellungsprozess wird angezeigt.");
                    }
                    // Abholbare Items anzeigen
                    else if (itemName.equals(ChatColor.stripColor(createCompletedItemsChest(player).getItemMeta().getDisplayName()))) {
                        collectCompletedItems(player);
                        openMainMenu(player);
                    }
                    // Bestätigungsmenü für das Item öffnen
                    else {
                        ItemStack selectedItem = clickedItem.clone();
                        openConfirmationMenu(player, selectedItem);
                    }
                }
                // Bestätigungsmenü
                else if (title != null && title.equals("Bestätigung")) {
                    if (displayName.equals("Bestätigen")) {
                        // Erst versuchen, das Item aus der temporären Map zu holen
                        ItemStack itemToProduce = pendingItemConfirmations.get(player.getUniqueId());

                        if (itemToProduce == null) {
                            // Fallback auf die Datenbank, falls es in der Map nicht gefunden wurde
                            itemToProduce = dbManager.getPendingConfirmation(player.getUniqueId());
                            plugin.getLogger().info("Item aus der Datenbank abgerufen für UUID: " + player.getUniqueId());
                        } else {
                            plugin.getLogger().info("Item aus der temporären Map abgerufen für UUID: " + player.getUniqueId());
                        }

                        if (itemToProduce != null && itemToProduce.getItemMeta() != null) {
                            // Logge das abgerufene Item und starte die Produktion
                            String itemToProduceName = ChatColor.stripColor(itemToProduce.getItemMeta().getDisplayName());
                            plugin.getLogger().info("Confirmed item to produce: " + itemToProduceName);

                            // Starte die Produktion mit der UUID des Spielers
                            String itemToProduce2 = getItemIdByName(ChatColor.stripColor(itemToProduce.getItemMeta().getDisplayName()));

                            if (canProduceItem(player, itemToProduce2)) {
                                // Debug: Überprüfe die Ressourcen vor dem Abzug
                                plugin.getLogger().info("Player has enough resources for item: " + itemToProduce2);

                                deductResources(player, itemToProduce2);

                                // Debug: Starte die Produktion und prüfe das Item erneut
                                plugin.getLogger().info("Starting production for item: " + itemToProduce2);

                                startProduction(player.getUniqueId(), OraxenItems.getIdByItem(itemToProduce), itemToProduce);

                            } else {
                                player.sendMessage(ChatColor.RED + "Du hast nicht genug Ressourcen, um dieses Item herzustellen.");
                            }

                            // Lösche die Bestätigung aus der Map und Datenbank
                            pendingItemConfirmations.remove(player.getUniqueId());
                            //dbManager.removePendingConfirmation(player.getUniqueId());
                            dbManager.removePendingConfirmation(player.getUniqueId());
                            player.closeInventory();
                            openMainMenu(player);
                        } else {
                            // Wenn das Item nicht gefunden wird, logge eine Warnung
                            player.sendMessage(ChatColor.RED + "...");
                            plugin.getLogger().warning("Pending confirmation item is null or has no meta for player " + player.getName());
                        }
                    } else if (displayName.equals("Abbrechen")) {
                        player.closeInventory();
                        player.sendMessage(ChatColor.RED + "Herstellung abgebrochen.");
                        pendingItemConfirmations.remove(player.getUniqueId());
                        dbManager.removePendingConfirmation(player.getUniqueId());
                        openMainMenu(player);
                    }
                }


            } finally {
                // Setze die Verarbeitung zurück
                isProcessing.set(false);
                clickProcessing.put(player, isProcessing);
            }
        }
    }


    private void openCoinProductionMenu(Player player) {
        Inventory coinProductionMenu = Bukkit.createInventory(null, 27, "Freaky Coin Herstellung");

        // Oraxen-Item "silber" laden
        ItemBuilder silverItemBuilder = OraxenItems.getItemById("silber");
        ItemStack silverOption;
        if (silverItemBuilder != null) {
            silverOption = silverItemBuilder.build(); // Baue das Item
            ItemMeta silverMeta = silverOption.getItemMeta();
            if (silverMeta != null) {
                silverMeta.setDisplayName(ChatColor.GRAY + "Mit Silber (30 Minuten)"); // Setze den Display-Namen

                // Setze die Item-Lore (Beschreibung)
                List<String> silverLore = new ArrayList<>();
                silverLore.add(ChatColor.YELLOW + "Verwende Silber, um einen Coin in 30 Minuten herzustellen.");
                silverLore.add(ChatColor.GRAY + "Preis: 10x Silber");
                silverLore.add("");
                silverLore.add(ChatColor.AQUA + "Erhalte 1 Freaky Coin.");
                silverMeta.setLore(silverLore);

                silverOption.setItemMeta(silverMeta); // Setze die Meta-Daten
            }
        } else {
            silverOption = new ItemStack(Material.IRON_INGOT); // Fallback, falls das Oraxen-Item nicht geladen wird
            ItemMeta silverMeta = silverOption.getItemMeta();
            if (silverMeta != null) {
                silverMeta.setDisplayName(ChatColor.GRAY + "Mit Silber (30 Minuten)");

                // Fallback-Lore
                List<String> silverLore = new ArrayList<>();
                silverLore.add(ChatColor.YELLOW + "Verwende Silber, um einen Coin in 30 Minuten herzustellen.");
                silverLore.add(ChatColor.GRAY + "Preis: 10x Silber");
                silverLore.add("");
                silverLore.add(ChatColor.AQUA + "Erhalte 1 Freaky Coin.");
                silverMeta.setLore(silverLore);

                silverOption.setItemMeta(silverMeta);
            }
        }
        // Oraxen-Item "gold" laden
        ItemBuilder goldItemBuilder = OraxenItems.getItemById("gold");
        ItemStack goldOption;
        if (goldItemBuilder != null) {
            goldOption = goldItemBuilder.build(); // Baue das Item
            ItemMeta goldMeta = goldOption.getItemMeta();
            if (goldMeta != null) {
                goldMeta.setDisplayName(ChatColor.GOLD + "Mit Gold (10 Minuten)"); // Setze den Display-Namen

                // Setze die Item-Lore (Beschreibung)
                List<String> goldLore = new ArrayList<>();
                goldLore.add(ChatColor.YELLOW + "Verwende Gold, um einen Coin in 10 Minuten herzustellen.");
                goldLore.add(ChatColor.GRAY + "Preis: 1x Gold");
                goldLore.add("");
                goldLore.add(ChatColor.AQUA + "Erhalte 1 Freaky Coin.");
                goldMeta.setLore(goldLore);

                goldOption.setItemMeta(goldMeta); // Setze die Meta-Daten
            }
        } else {
            goldOption = new ItemStack(Material.GOLD_INGOT); // Fallback, falls das Oraxen-Item nicht geladen wird
            ItemMeta goldMeta = goldOption.getItemMeta();
            if (goldMeta != null) {
                goldMeta.setDisplayName(ChatColor.GOLD + "Mit Gold (10 Minuten)");

                // Fallback-Lore
                List<String> goldLore = new ArrayList<>();
                goldLore.add(ChatColor.YELLOW + "Verwende Gold, um einen Coin in 10 Minuten herzustellen.");
                goldLore.add(ChatColor.GRAY + "Preis: 1x Gold");
                goldLore.add("");
                goldLore.add(ChatColor.AQUA + "Erhalte 1 Freaky Coin.");
                goldMeta.setLore(goldLore);

                goldOption.setItemMeta(goldMeta);
            }
        }

        coinProductionMenu.setItem(11, silverOption);
        coinProductionMenu.setItem(15, goldOption);
        coinProductionMenu.setItem(26, createBackButton());

        player.openInventory(coinProductionMenu);
    }

    private void startCoinProduction(Player player, String material, int productionTime) {
        // Definiere die Materialanforderungen
        int materialCost = 1; // Standardkosten, falls keine spezifischen Kosten definiert sind

        // Überprüfe das Material und setze die Kosten entsprechend
        if (material.equals("silber")) {
            materialCost = 10; // 10 Silber für 1 Freaky Coin
        } else if (material.equals("gold")) {
            materialCost = 1; // 1 Gold für 1 Freaky Coin
        }

        // Überprüfe, ob der Spieler bereits eine aktive Produktion hat
        if (dbManager.hasActiveProduction(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Du hast eine aktive Produktion.");
            return;
        }

        // Überprüfe, ob der Spieler genügend Material für die Produktion hat
        if (getItemAmount(player.getInventory(), material) < materialCost) {
            player.sendMessage(ChatColor.RED + "Du hast nicht genug " + material + ", um Freaky Coins herzustellen.");
            return;
        }

        // Entferne das benötigte Material aus dem Inventar des Spielers
        removeItems(player.getInventory(), OraxenItems.getItemById(material).build(), materialCost);

        // Starte die Produktion des Freaky Coins
        ItemStack freakyCoin = OraxenItems.getItemById("freaky_coin").build();
        long endTime = System.currentTimeMillis() + productionTime * 1000L;
        dbManager.startProduction(player.getUniqueId(), "freaky_coin", endTime, material);

        // Startet die Überwachung der Produktion
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                checkProductionStatus(player);
            }
        }.runTaskTimer(plugin, 0L, 20L); // Überprüfung jede Sekunde

        activeProcesses.put(player, task);
        player.sendMessage(ChatColor.GREEN + "Herstellung von Freaky Coins gestartet! Benötigtes Material: " + materialCost + " " + material);
    }


    private void collectCompletedItems(Player player) {
        List<ItemStack> items = dbManager.getCompletedItems(player.getUniqueId());
        if (items == null || items.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Du hast keine weiteren fertigen Items zum Abholen.");
        } else {
            for (ItemStack item : items) {
                player.getInventory().addItem(item);
            }
            dbManager.clearCompletedItems(player.getUniqueId());
            player.sendMessage(ChatColor.GREEN + "Alle fertigen Items wurden abgeholt!");

            // Lösche den Eintrag aus der Pending-Confirmations-Tabelle nach Abholung
            dbManager.removePendingConfirmation(player.getUniqueId());
        }
    }



    private void handleItemSelection(Player player, String itemName) {
        if (clickCooldown.contains(player)) return; // Doppelklick verhindern
        clickCooldown.add(player);

        try {
            // Debug: Überprüfe den itemName
            plugin.getLogger().info("Item selected by player: " + itemName);

            String itemId = getItemIdByName(itemName);

            // Debug: Überprüfe die itemId nach dem Abruf
            plugin.getLogger().info("Item ID retrieved: " + itemId);

            if (itemId == null) {
                player.sendMessage(ChatColor.RED + "Dieses Item ist nicht verfügbar.");
                return;
            }

            if (dbManager.hasActiveProduction(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "Du hast eine aktive Produktion.");
                return;
            }

            if (canProduceItem(player, itemId)) {
                // Debug: Überprüfe die Ressourcen vor dem Abzug
                plugin.getLogger().info("Player has enough resources for item: " + itemId);

                deductResources(player, itemId);

                // Debug: Starte die Produktion und prüfe das Item erneut
                plugin.getLogger().info("Starting production for item: " + itemId);

                startProduction(player.getUniqueId(), itemId, unlockableItems.get(itemId));

            } else {
                player.sendMessage(ChatColor.RED + "Du hast nicht genug Ressourcen, um dieses Item herzustellen.");
            }
        } finally {
            Bukkit.getScheduler().runTaskLater(plugin, () -> clickCooldown.remove(player), 10L); // Entferne den Spieler nach 0,5 Sekunden aus der Doppelklick-Liste
        }
    }



    public String getItemIdByName(String itemName) {
        // Füge Debugging hinzu, um alle möglichen Übereinstimmungen zu prüfen
        for (Map.Entry<String, ItemStack> entry : unlockableItems.entrySet()) {
            ItemMeta meta = entry.getValue().getItemMeta();

            if (meta != null && meta.getDisplayName() != null) {
                String displayName = ChatColor.stripColor(meta.getDisplayName());
                plugin.getLogger().info("Checking Item: " + displayName + " against selected: " + itemName);

                // Überprüfe, ob der Name mit dem ausgewählten Item übereinstimmt
                if (itemName.equals(displayName)) {
                    plugin.getLogger().info("Matched itemId: " + entry.getKey());
                    return entry.getKey();
                }
            }
        }

        plugin.getLogger().warning("No match found for item: " + itemName);
        return null;
    }


    private boolean canProduceItem(Player player, String itemId) {
        Map<String, Integer> cost = itemCosts.get(itemId);
        if (cost == null) return false;

        PlayerInventory inventory = player.getInventory();

        // Durchlaufe alle Kosten und prüfe die tatsächlich im Inventar vorhandenen Ressourcen
        for (Map.Entry<String, Integer> entry : cost.entrySet()) {
            String costItemId = entry.getKey();
            int requiredAmount = entry.getValue();
            int foundAmount = getItemAmount(inventory, costItemId);

            // Wenn nicht genügend Ressourcen für dieses spezielle Item vorhanden sind, zurückkehren
            if (foundAmount < requiredAmount) {
                player.sendMessage(ChatColor.RED + "Du hast nicht genug " + costItemId + ". Benötigt: " + requiredAmount + ", Vorhanden: " + foundAmount);
                return false;
            }
        }

        return true; // Alle benötigten Ressourcen sind vorhanden
    }




    private int getItemAmount(PlayerInventory inventory, String itemId) {
        ItemBuilder itemBuilder = OraxenItems.getItemById(itemId);
        if (itemBuilder == null) {
            plugin.getLogger().warning("Oraxen-Item konnte nicht gefunden werden: " + itemId);
            return 0;
        }

        ItemStack itemStack = itemBuilder.build(); // Erstelle das Oraxen-Item
        int amount = 0;

        for (ItemStack stack : inventory.getContents()) {
            if (stack != null && compareOraxenItems(stack, itemStack)) {
                amount += stack.getAmount();
            }
        }

        plugin.getLogger().info("Gesamtmenge von " + itemId + " im Inventar: " + amount);
        return amount;
    }







    private boolean compareOraxenItems(ItemStack item1, ItemStack item2) {
        if (item1 == null || item2 == null) return false;

        // Vergleiche den Materialtyp
        if (item1.getType() != item2.getType()) return false;

        // Vergleiche die CustomModelData
        ItemMeta meta1 = item1.getItemMeta();
        ItemMeta meta2 = item2.getItemMeta();
        if (meta1 != null && meta2 != null) {
            if (meta1.hasCustomModelData() && meta2.hasCustomModelData()) {
                if (meta1.getCustomModelData() != meta2.getCustomModelData()) return false;
            }

            // Vergleiche die Namen
            if (meta1.hasDisplayName() && meta2.hasDisplayName()) {
                if (!meta1.getDisplayName().equals(meta2.getDisplayName())) return false;
            }

            return Objects.equals(meta1.getLore(), meta2.getLore());
        }

        return false;
    }









    private void deductResources(Player player, String itemId) {
        Map<String, Integer> cost = itemCosts.get(itemId);
        if (cost == null) {
            plugin.getLogger().warning("Keine Kosten für Item ID: " + itemId  + "gefunden.");
            return;
        }

        PlayerInventory inventory = player.getInventory();

        // Durchlaufe alle Ressourcen, die abgezogen werden müssen
        for (Map.Entry<String, Integer> entry : cost.entrySet()) {
            String costItemId = entry.getKey();
            int amountToDeduct = entry.getValue();

            // Entferne die erforderliche Menge des Items
            removeItems(inventory, OraxenItems.getItemById(costItemId).build(), amountToDeduct);
        }
    }


    private void removeItems(PlayerInventory inventory, ItemStack itemStack, int amount) {
        int remaining = amount;

        // Durchlaufe das Inventar und ziehe die Items ab
        for (ItemStack stack : inventory.getContents()) {
            if (stack != null && compareOraxenItems(stack, itemStack)) {
                int stackAmount = stack.getAmount();

                if (stackAmount <= remaining) {
                    remaining -= stackAmount;
                    inventory.removeItem(stack);
                } else {
                    stack.setAmount(stackAmount - remaining);
                    remaining = 0;
                    break;
                }
            }

            // Beende die Schleife, wenn alle benötigten Ressourcen entfernt wurden
            if (remaining <= 0) {
                break;
            }
        }

        if (remaining > 0) {
            plugin.getLogger().warning("Es konnte nicht die erforderliche Menge an " + OraxenItems.getIdByItem(itemStack) + " entfernt werden.");
        }
    }



    public void startProduction(UUID uuid, String itemId, ItemStack itemStack) {
        if (dbManager.hasActiveProduction(uuid)) {
            plugin.getLogger().warning("Player " + uuid + " already has an active production.");
            return;
        }

        // Produktionszeit abrufen, unter Berücksichtigung von Kategorie und Item-ID
        String category = getCategoryForItem(itemId); // Beispiel: eine Methode, um die Kategorie zu ermitteln
        String fullItemId = category + ":" + itemId; // Vollständiger Schlüssel für die Map

        int productionTime = itemProductionTimes.getOrDefault(fullItemId, 3600);  // Verwende den vollständigen Schlüssel
        plugin.getLogger().info("Produktionszeit für " + fullItemId + ": " + productionTime + " Sekunden.");

        long endTime = System.currentTimeMillis() + productionTime * 1000L;

        plugin.getLogger().info("Starting production for item: " + fullItemId + " with production time: " + productionTime);

        // Speichere die Produktionsdetails
        dbManager.startProduction(uuid, itemId, endTime, null);

        // Starte die Überwachung der Produktion
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                checkProductionStatus(Bukkit.getPlayer(uuid));
            }
        }.runTaskTimer(plugin, 0L, 20L); // Jede Sekunde prüfen

        // Füge den Produktionsprozess zur Liste hinzu
        activeProcesses.put(Bukkit.getPlayer(uuid), task);
        Bukkit.getPlayer(uuid).sendMessage(ChatColor.GREEN + "Herstellung des Items " + itemStack.getItemMeta().getDisplayName() + " gestartet!");
    }


    private String getCategoryForItem(String itemId) {
        for (String key : itemProductionTimes.keySet()) {
            if (key.endsWith(":" + itemId)) {
                return key.split(":")[0]; // Gibt die Kategorie zurück
            }
        }
        return "default"; // Standardkategorie, falls nichts gefunden wird
    }


    private void checkProductionStatus(Player player) {
        //Spieler ist Offline und Zeit läuft ab, wird das Item nicht korrekt fertiggestellt
        //Tritt nicht immer auf, durch manuellen Boost kann Prozess abgeschlossen werden.
        //if (player == null) {
        // dürfte Error verursachen, sobald ein Spieler Offline ist, da Prüfung auf alle Spieler alle X Sekunden angewendet wird.


        if (player == null || !player.isOnline()) {
            plugin.getLogger().warning("Spieler ist nicht mehr online. Beende Überprüfung.");
            return; // Spieler ist nicht online, beende die Überprüfung.
        }

        Long endTime = dbManager.getProductionEndTime(player.getUniqueId());
        if (endTime != null && System.currentTimeMillis() >= endTime) {
            completeProduction(player);
        }
    }


    private void completeProduction(Player player) {
        BukkitTask task = activeProcesses.remove(player);
        if (task != null) {
            task.cancel();
        }

        // Hole das derzeit produzierte Item aus der Datenbank
        ItemStack itemStack = dbManager.getCurrentProductionItem(player.getUniqueId());
        if (itemStack != null) {
            // Füge das produzierte Item in die fertige Items Tabelle ein
            dbManager.addCompletedItem(player.getUniqueId(), itemStack);

            // Sende eine Benachrichtigung an den Spieler
            player.sendMessage(ChatColor.GREEN + "Die Produktion des Items " + itemStack.getItemMeta().getDisplayName() + " ist abgeschlossen!");

            // Lösche den Eintrag aus der Produktions-Tabelle
            dbManager.clearProduction(player.getUniqueId());

            // Lösche den Eintrag aus der Pending-Confirmations-Tabelle
            dbManager.removePendingConfirmation(player.getUniqueId());

            // Optional: Aktualisiere die Anzeige im Menü (falls erforderlich)
            player.closeInventory(); // Schließt das Inventar und zwingt den Spieler, es erneut zu öffnen
            openMainMenu(player); // Öffnet das Hauptmenü erneut
        } else {
            player.sendMessage(ChatColor.RED + "Fehler beim Abschluss der Produktion. Das Item konnte nicht gefunden werden.");
        }
        //onProductionStateChange();

    }



    public void savePendingConfirmation(UUID uuid, ItemStack item) {
        // Save the pending item in the temporary map first
        pendingItemConfirmations.put(uuid, item);

        plugin.getLogger().info("Pending item for confirmation saved in temporary map for UUID: " + uuid.toString());

        // Continue saving to the database
        String sql = "INSERT OR REPLACE INTO pending_confirmations (uuid, item_id) VALUES (?, ?)";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            pstmt.setString(2, OraxenItems.getIdByItem(item));
            pstmt.executeUpdate();
            plugin.getLogger().info("Pending confirmation successfully saved for UUID: " + uuid.toString());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }



    private void resetAnvilToDefault(Player player) {
        dbManager.clearProduction(player.getUniqueId());
    }

    private void applyBoost(Player player, String boostItem) {
        // Überprüfen, ob der Spieler im Cooldown ist
        if (isBoostOnCooldown(player)) {
            player.sendMessage(ChatColor.RED + "Warte kurz, bevor du erneut einen Boost anwendest!");
            return;
        }

        // Überprüfen, ob der Spieler den Boost hat
        if (!hasBoost(player, boostItem)) {
            player.sendMessage(ChatColor.RED + "Du hast keinen " + boostItem + " für einen Boost.");
            return;
        }
        setBoostCooldown(player);
        try {
            // Bestimmen der Reduktionszeit basierend auf dem Boost-Item
            int reduction = boostItem.equals("freaky_ingot") ? 600 : 1800; // 10 Minuten oder 30 Minuten
            Long endTime = dbManager.getProductionEndTime(player.getUniqueId());

            if (endTime != null) {
                long newEndTime = endTime - reduction * 1000L;
                long currentTime = System.currentTimeMillis();

                // Aktualisieren der Produktionszeit in der Datenbank
                dbManager.updateProductionEndTime(player.getUniqueId(), Math.max(newEndTime, currentTime));

                // Entfernen des Boost-Items aus dem Inventar
                removeItems(player.getInventory(), OraxenItems.getItemById(boostItem).build(), 1);

                // Bestätigungsnachricht
                player.sendMessage(ChatColor.GREEN + "Produktionszeit wurde um " + (reduction / 60) + " Minuten reduziert!");

                // Überprüfen, ob die Produktionszeit 0 erreicht hat
                if (newEndTime <= currentTime) {
                    checkProductionStatus(player);
                }



            } else {
                player.sendMessage(ChatColor.RED + "Keine aktive Produktion gefunden, auf die der Boost angewendet werden kann.");
            }
        } finally {
            openMainMenu(player);
        }
    }

    private void setBoostCooldown(Player player) {
        boostCooldowns.put(player, System.currentTimeMillis());
        // Entferne den Spieler nach 10 Sekunden aus der Cooldown-Liste
        Bukkit.getScheduler().runTaskLater(plugin, () -> boostCooldowns.remove(player), 20L);
    }

    private boolean isBoostOnCooldown(Player player) {
        return boostCooldowns.containsKey(player);
    }




    private boolean hasBoost(Player player, String boostItem) {
        return getItemAmount(player.getInventory(), boostItem) > 0;
    }

    private void loadActiveProductions() {
        List<UUID> playersWithActiveProductions = dbManager.getPlayersWithActiveProductions();
        for (UUID playerId : playersWithActiveProductions) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                BukkitTask task = new BukkitRunnable() {
                    @Override
                    public void run() {
                        checkProductionStatus(player);
                    }
                }.runTaskTimer(plugin, 0L, 20L); // Check every second
                activeProcesses.put(player, task);
            }
        }
    }


    // Spielerische Lore-Interaktion mit dem Schmied hinzufügen
    private void interactWithPlayer(Player player) {
        // Zeige sofort Titel und Untertitel mit zufälligem Tipp an
        String title = ChatColor.GOLD + "Meisterhändler";
        String subtitle = ChatColor.YELLOW + getRandomSmithTip();
        player.sendTitle(title, subtitle, 18, 85, 43);

        // Sofortige Soundeffekte und Partikel, um die Aufmerksamkeit zu erregen
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_AMBIENT, 1.0f, 1.0f);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_WORK_TOOLSMITH, 1.0f, 1.0f);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_WORK_WEAPONSMITH, 1.0f, 1.0f);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
        player.getWorld().spawnParticle(Particle.FLAME, player.getLocation().add(0, 1.5, 0), 10, 0.2, 0.2, 0.2, 0.01);

        // Verzögerte zusätzliche Effekte für mehr Immersion
        new BukkitRunnable() {
            @Override
            public void run() {
                // Zusätzliche Villager-Sounds und Partikel für ein lebendigeres Erlebnis
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_TRADE, 1.0f, 1.0f);
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_CELEBRATE, 1.0f, 1.0f);
                player.playSound(player.getLocation(), Sound.ENTITY_STRIDER_HAPPY, 1.0f, 1.0f);
                player.getWorld().spawnParticle(Particle.LARGE_SMOKE, player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);

                // Optionale Chatnachricht, die weniger im Fokus steht
                player.sendMessage(ChatColor.GOLD + "Meisterhändler: " + ChatColor.WHITE + "Willkommen zurück, Abenteurer!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_WORK_TOOLSMITH, 1.0f, 1.0f);
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_WORK_WEAPONSMITH, 1.0f, 1.0f);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_WORK_TOOLSMITH, 1.0f, 1.0f);
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_WORK_WEAPONSMITH, 1.0f, 1.0f);
                        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
                    }
                }.runTaskLater(plugin, 20L); // Zusätzliche Effekte nach 1 Sekunde

            }
        }.runTaskLater(plugin, 10L); // Hauptaktionen direkt nach der Interaktion starten
    }

    // Hilfsfunktion, um einen zufälligen Tipp auszuwählen
    private String getRandomSmithTip() {
        return smithTips.get(new Random().nextInt(smithTips.size()));
    }

}

class DatabaseManagerTrader {
    private final File dbFile;
    private final JavaPlugin plugin;
    private Connection connection;

    public DatabaseManagerTrader(File dbFile, JavaPlugin plugin) {
        this.dbFile = dbFile;
        this.plugin = plugin;
        initializeDatabase();
    }

    private void initializeDatabase() {
        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {
            // Tabelle für laufende Produktionen
            String sql = "CREATE TABLE IF NOT EXISTS productions (" +
                    "uuid TEXT," +
                    "item_id TEXT," +
                    "start_time BIGINT," +
                    "end_time BIGINT," +
                    "material TEXT" +
                    ")";
            stmt.execute(sql);

            // Tabelle für fertige Items
            sql = "CREATE TABLE IF NOT EXISTS completed_items (" +
                    "uuid TEXT," +
                    "item_id TEXT" +
                    ")";
            stmt.execute(sql);

            // Tabelle für ausstehende Bestätigungen
            sql = "CREATE TABLE IF NOT EXISTS pending_confirmations (" +
                    "uuid TEXT," +
                    "item_id TEXT" +
                    ")";
            stmt.execute(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Connection connect() throws SQLException {
        if (this.connection == null || this.connection.isClosed()) {
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getPath());
        }
        return this.connection;
    }

    public Connection getConnection() throws SQLException {
        connect();  // Stelle sicher, dass eine Verbindung aufgebaut ist

        return connection;
    }
    public Long getProductionEndTime(UUID uuid) {
        String sql = "SELECT end_time FROM productions WHERE uuid = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getLong("end_time");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Fehler beim Abrufen der Produktionszeit für UUID " + uuid + ": " + e.getMessage());
        }
        return null;
    }


    public ItemStack getCurrentProductionItem(UUID uuid) {
        String sql = "SELECT item_id FROM productions WHERE uuid = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String itemId = rs.getString("item_id");
                if (itemId != null) {
                    // Entferne den Präfix
                    itemId = stripCategoryPrefix(itemId);

                    ItemBuilder itemBuilder = OraxenItems.getItemById(itemId);
                    if (itemBuilder != null) {
                        return itemBuilder.build();
                    } else {
                        plugin.getLogger().warning("Item with ID " + itemId + " not found in Oraxen.");
                        return new ItemStack(Material.STICK); // Platzhalter-Item zurückgeben
                    }
                } else {
                    plugin.getLogger().warning("No item_id found for UUID " + uuid.toString());
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
    private String stripCategoryPrefix(String itemId) {
        if (itemId.contains(":")) {
            return itemId.split(":")[1]; // Nimmt den zweiten Teil nach dem Doppelpunkt
        }
        return itemId;
    }


    public boolean hasActiveProduction(UUID uuid) {
        String sql = "SELECT COUNT(*) AS count FROM productions WHERE uuid = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                int count = rs.getInt("count");
                plugin.getLogger().info("Aktive Produktionen für UUID " + uuid.toString() + ": " + count);
                return count > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }


    public void startProduction(UUID uuid, String itemId, long endTime, String material) {
        // Entferne den Präfix, bevor du die itemId speicherst
        itemId = stripCategoryPrefix(itemId);
        plugin.getLogger().info("Speichere Produktion für Item: " + itemId + " mit Endzeit: " + endTime);

        String sql = "INSERT INTO productions (uuid, item_id, start_time, end_time, material) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            pstmt.setString(2, itemId);
            pstmt.setLong(3, System.currentTimeMillis());
            pstmt.setLong(4, endTime);
            pstmt.setString(5, material);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Fehler beim Speichern der Produktionsdetails: " + e.getMessage());
            e.printStackTrace();
        }
    }



    public void updateProductionEndTime(UUID uuid, long newEndTime) {
        String sql = "UPDATE productions SET end_time = ? WHERE uuid = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, newEndTime);
            pstmt.setString(2, uuid.toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void clearProduction(UUID uuid) {
        String sql = "DELETE FROM productions WHERE uuid = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                plugin.getLogger().info("Produktion für UUID " + uuid + " erfolgreich gelöscht.");
            } else {
                plugin.getLogger().warning("Keine Produktion für UUID " + uuid + " gefunden.");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Fehler beim Löschen der Produktion für UUID " + uuid + ": " + e.getMessage());
        }
    }



    public void addCompletedItem(UUID uuid, ItemStack item) {
        // Entferne den Präfix, bevor du den itemId an OraxenItems übergibst
        String itemId = stripCategoryPrefix(OraxenItems.getIdByItem(item));

        plugin.getLogger().info("Trying to add completed item for UUID " + uuid.toString() + ": " + itemId);

        if (itemId == null) {
            plugin.getLogger().warning("Oraxen item ID could not be determined for item: " + item.getType() + ". Item will not be added to completed items.");
            return;
        }

        String sql = "INSERT INTO completed_items (uuid, item_id) VALUES (?, ?)";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            pstmt.setString(2, itemId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }




    public List<ItemStack> getCompletedItems(UUID uuid) {
        List<ItemStack> items = new ArrayList<>();
        String sql = "SELECT item_id FROM completed_items WHERE uuid = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String itemId = rs.getString("item_id");
                ItemBuilder itemBuilder = OraxenItems.getItemById(itemId);
                if (itemBuilder != null) {
                    items.add(itemBuilder.build());
                } else {
                    plugin.getLogger().warning("Oraxen item with ID " + itemId + " could not be found.");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return items;
    }

    public void clearCompletedItems(UUID uuid) {
        String sql = "DELETE FROM completed_items WHERE uuid = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }







    public ItemStack getPendingConfirmation(UUID uuid) {
        String sql = "SELECT item_id FROM pending_confirmations WHERE uuid = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            ResultSet rs = pstmt.executeQuery();

            // Überprüfe, ob die Abfrage ein Ergebnis liefert
            if (rs.next()) {
                String itemId = rs.getString("item_id");
                plugin.getLogger().info("Abgerufene item_id aus der Datenbank: " + itemId);

                // Lade das Item mit der ID aus Oraxen
                ItemBuilder itemBuilder = OraxenItems.getItemById(itemId);
                if (itemBuilder != null) {
                    ItemStack item = itemBuilder.build();
                    if (item != null) {
                        plugin.getLogger().info("Oraxen-Item mit ID " + itemId + " erfolgreich abgerufen.");
                        return item;
                    } else {
                        plugin.getLogger().warning("Fehler: Das Oraxen-Item mit ID " + itemId + " konnte nicht erstellt werden.");
                    }
                } else {
                    plugin.getLogger().warning("Fehler: Kein Oraxen-Item mit ID " + itemId + " gefunden.");
                }
            } else {
                plugin.getLogger().warning("Fehler: Keine Einträge für UUID " + uuid + " in pending_confirmations gefunden.");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("SQL-Fehler beim Abrufen des Items: " + e.getMessage());
            e.printStackTrace();
        }
        return null; // Gib null zurück, wenn kein gültiges Item gefunden wurde
    }



    public void removePendingConfirmation(UUID uuid) {
        String sql = "DELETE FROM pending_confirmations WHERE uuid = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<UUID> getPlayersWithActiveProductions() {
        List<UUID> players = new ArrayList<>();
        String sql = "SELECT DISTINCT uuid FROM productions";
        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                players.add(UUID.fromString(rs.getString("uuid")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return players;
    }
}

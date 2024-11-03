package h34r7l3s.freakyworld;

import io.th0rgal.oraxen.api.OraxenItems;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class VampireVillager implements Listener {
    private final GameLoop gameLoop;
    private final Villager villager;
    private final HashMap<UUID, Integer> playerSteps = new HashMap<>();
    private final HashMap<UUID, Integer> playerInteractions = new HashMap<>();
    private final HashMap<UUID, Long> lastInteractionTime = new HashMap<>();

    // Textlisten basierend auf Laune und Tageszeit
    // Verkürzte und prägnantere Texte basierend auf Laune und Tageszeit
    private final List<String> morningTexts = Arrays.asList(
            ChatColor.GRAY + "Ein neuer Morgen,",
            ChatColor.GRAY + "doch die Gefahren bleiben.",

            ChatColor.GRAY + "Früh auf, Held.",
            ChatColor.GRAY + "Große Taten erwarten dich.",

            ChatColor.GRAY + "Morgens wurden einst",
            ChatColor.GRAY + "große Kriege entschieden."
    );

    private final List<String> afternoonTexts = Arrays.asList(
            ChatColor.GRAY + "Der Tag vergeht,",
            ChatColor.GRAY + "doch die Arbeit ruft.",

            ChatColor.GRAY + "Mittag, die Stunde der Helden.",
            ChatColor.GRAY + "Bist du bereit?",

            ChatColor.GRAY + "Die Sonne steht hoch,",
            ChatColor.GRAY + "die Dunkelheit naht..."
    );

    private final List<String> nightTexts = Arrays.asList(
            ChatColor.GRAY + "Die Nacht ist voller Gefahren...",
            ChatColor.GRAY + "Sei wachsam.",

            ChatColor.GRAY + "Die Dunkelheit wächst,",
            ChatColor.GRAY + "bleib stark.",

            ChatColor.GRAY + "Fürchte die Nacht,",
            ChatColor.GRAY + "sie verschlingt die Schwachen."
    );

    private final List<String> annoyedTexts = Arrays.asList(
            ChatColor.RED + "Sag schon, was du willst!",
            ChatColor.RED + "Komm zur Sache!",

            ChatColor.RED + "Beeil dich!",
            ChatColor.RED + "Ich habe keine Zeit für Spielchen!"
    );

    public VampireVillager(GameLoop gameLoop) {
        this.gameLoop = gameLoop;

        Location location = new Location(Bukkit.getWorld("world"), -14, 63, 40);
        this.villager = spawnVillager(location);
        this.villager.setAI(false);

        startVampireEffects(villager.getWorld(), villager);

        startVampireVillagerLookTask();
    }

    private Villager spawnVillager(Location location) {
        Villager villager = (Villager) location.getWorld().spawnEntity(location, EntityType.VILLAGER);
        villager.setCustomName(ChatColor.DARK_PURPLE + "Der uralte Wächter");
        villager.setCustomNameVisible(true);
        villager.setInvulnerable(true);
        villager.setProfession(Villager.Profession.NONE);


        return villager;
    }

    private void startVampireVillagerLookTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (villager != null && !villager.isDead()) {
                    lookAtNearestPlayerVampireVillager();
                }
            }
        }.runTaskTimer(gameLoop.getPlugin(), 0L, 40L); // alle 2 Sekunden (40 Ticks)
    }
    private void lookAtNearestPlayerVampireVillager() {
        Collection<Player> nearbyPlayers = villager.getWorld().getNearbyPlayers(villager.getLocation(), 10);
        Player nearestPlayer = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Player player : nearbyPlayers) {
            double distance = player.getLocation().distanceSquared(villager.getLocation());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestPlayer = player;
            }
        }

        if (nearestPlayer != null) {
            Location villagerLocation = villager.getLocation();
            Location playerLocation = nearestPlayer.getLocation();

            // Berechne die Richtung zum Spieler und setze die Rotation des uralten Wächters
            double dx = playerLocation.getX() - villagerLocation.getX();
            double dz = playerLocation.getZ() - villagerLocation.getZ();
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            villagerLocation.setYaw(yaw);
            villager.teleport(villagerLocation); // Aktualisiert die Blickrichtung des uralten Wächters
        }
    }

    public void removeVillager() {
        if (villager != null && !villager.isDead()) {
            villager.remove();
            Bukkit.getLogger().info("Der uralte Wächter wurde erfolgreich entfernt.");
        } else {
            Bukkit.getLogger().warning("Kein Villager gefunden oder der Villager ist bereits tot.");
        }
    }

    private void startVampireEffects(World world, Villager villager) {
        new BukkitRunnable() {
            @Override
            public void run() {
                world.spawnParticle(Particle.ENCHANT, villager.getLocation().add(0, 1, 0), 30, 0.5, 1.0, 0.5, 0);
                world.playSound(villager.getLocation(), Sound.AMBIENT_CAVE, 0.5f, 1.0f);
            }
        }.runTaskTimer(gameLoop.getPlugin(), 0L, 60L);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager)) return;

        Villager clickedVillager = (Villager) event.getRightClicked();
        if (!clickedVillager.equals(villager)) return;

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        int kills = gameLoop.getDragonKills(playerId);

        if (kills > 0) {
            if (!playerSteps.containsKey(playerId)) {
                playerSteps.put(playerId, 0);
            }

            // Aktualisiere die Interaktionszeiten und zähle Interaktionen
            long currentTime = System.currentTimeMillis();
            long lastTime = lastInteractionTime.getOrDefault(playerId, 0L);

            if (currentTime - lastTime < 5000) { // 5 Sekunden als Schwelle
                playerInteractions.put(playerId, playerInteractions.getOrDefault(playerId, 0) + 1);
            } else {
                playerInteractions.put(playerId, 1); // Zurücksetzen bei Zeitüberschreitung
            }
            lastInteractionTime.put(playerId, currentTime);

            startDialogSequence(player, playerId, kills);
        } else {
            player.sendMessage(ChatColor.GRAY + "Dieser Dorfbewohner scheint nichts Besonderes zu sein...");
        }
    }

    private void startDialogSequence(Player player, UUID playerId, int kills) {
        int step = playerSteps.get(playerId);

        // Automatischer Ablauf der Dialoge
        new BukkitRunnable() {
            int currentStep = step;

            @Override
            public void run() {
                if (currentStep >= 4) {
                    openRelicUI(player, kills);
                    playerSteps.put(playerId, 4);
                    playerInteractions.put(playerId, 0); // Zurücksetzen der Interaktionen nach Öffnen der UI
                    this.cancel();
                } else {
                    String message = getDialogMessage(currentStep, playerInteractions.getOrDefault(playerId, 1));
                    List<String> lines = splitMessageToLines(message, 30);
                    String title = lines.get(0);
                    String subtitle = lines.size() > 1 ? lines.get(1) : "";

                    player.sendTitle(title, subtitle, 10, 70, 20);
                    player.playSound(player.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, 1.0f, 0.5f);

                    currentStep++;
                    playerSteps.put(playerId, currentStep);
                }
            }
        }.runTaskTimer(gameLoop.getPlugin(), 0L, 100L); // 5 Sekunden zwischen Dialogschritten
    }

    private List<String> splitMessageToLines(String message, int maxLineLength) {
        List<String> lines = new ArrayList<>();
        while (message.length() > maxLineLength) {
            int lastSpaceIndex = message.substring(0, maxLineLength).lastIndexOf(" ");
            if (lastSpaceIndex == -1) {
                lastSpaceIndex = maxLineLength;
            }
            lines.add(message.substring(0, lastSpaceIndex));
            message = message.substring(lastSpaceIndex).trim();
        }
        lines.add(message); // Add the remaining part of the message
        return lines;
    }

    private String getDialogMessage(int step, int interactions) {
        String message;
        if (interactions > 3) {
            message = ChatColor.DARK_RED + getRandomText(annoyedTexts);
        } else {
            message = ChatColor.DARK_PURPLE + getTimeBasedText();
        }
        return message;
    }

    private String getTimeBasedText() {
        World world = Bukkit.getWorld("world");
        long time = world.getTime();
        if (time >= 0 && time < 12300) {
            return getRandomText(morningTexts);
        } else if (time >= 12300 && time < 23850) {
            return getRandomText(afternoonTexts);
        } else {
            return getRandomText(nightTexts);
        }
    }

    private String getRandomText(List<String> texts) {
        Random random = new Random();
        return texts.get(random.nextInt(texts.size()));
    }

    private void openRelicUI(Player player, int kills) {
        Inventory relicInventory = Bukkit.createInventory(null, 9, ChatColor.DARK_PURPLE + "Deine Drachen-Statistiken & Relikte");

        relicInventory.setItem(0, createStatsItem(kills));

        // Füge dekorative Icons und zusätzliche Informationen hinzu
        relicInventory.setItem(1, createDecorativeItem(Material.BOOK, ChatColor.AQUA + "Wächter der Relikte", Arrays.asList(
                ChatColor.DARK_PURPLE + "» Eine uralte Präsenz bewacht diese",
                ChatColor.DARK_PURPLE + "mächtigen Relikte seit Jahrhunderten.",
                ChatColor.GOLD + "» Der mystische Wächter verbirgt das",
                ChatColor.GOLD + "Geheimnis unendlicher Macht –",
                ChatColor.GOLD + "jedoch nur für jene, die sich",
                ChatColor.GOLD + "bewiesen haben.",
                "",
                ChatColor.DARK_GRAY + "» Um die verborgene Kraft der",
                ChatColor.DARK_GRAY + "Relikte freizulegen, musst du dich",
                ChatColor.DARK_GRAY + "im Kampf gegen die Drachen behaupten.",
                ChatColor.DARK_GRAY + "Je mehr Drachen du besiegst, desto",
                ChatColor.DARK_GRAY + "näher kommst du der Freischaltung.",
                "",
                ChatColor.RED + "» Töte genug Drachen, um Zugang zu",
                ChatColor.RED + "endlosen Verzauberungen zu erhalten.",
                ChatColor.RED + "Wähle eine Verzauberung, und der Wächter",
                ChatColor.RED + "wird die Kraft deines gewählten Gegenstandes",
                ChatColor.RED + "in deinem Inventar erhöhen!"
        )));

        relicInventory.setItem(5, createRelicItem(Material.DIAMOND_CHESTPLATE, "Schutz", ChatColor.GRAY + "Erhöhe deine Rüstung", kills >= 250));
        relicInventory.setItem(3, createRelicItem(Material.DIAMOND_SWORD, "Schärfe", ChatColor.GRAY + "Erhöhe deinen Schaden", kills >= 100));
        relicInventory.setItem(4, createRelicItem(Material.DIAMOND_HELMET, "Unzerbrechlich", ChatColor.GRAY + "Verstärke deine Ausrüstung", kills >= 150));
        relicInventory.setItem(2, createRelicItem(Material.DIAMOND_PICKAXE, "Effizienz", ChatColor.GRAY + "Schnellere Werkzeuge", kills >= 50));

        relicInventory.setItem(6, createRelicItem(Material.DIAMOND_SWORD, "Plünderung", ChatColor.GRAY + "Erhalte mehr Beute", kills >= 250));
        relicInventory.setItem(7, createRelicItem(Material.DIAMOND_PICKAXE, "Glück", ChatColor.GRAY + "Erhalte mehr Ressourcen", kills >= 250));
        player.openInventory(relicInventory);
    }

    private ItemStack createStatsItem(int kills) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Deine Drachen-Statistiken");
        // Liste mit dynamischen Fortschrittstexten
        String progressMessage = getProgressMessage(kills);
        meta.setLore(Arrays.asList(
                ChatColor.DARK_PURPLE + "» " + ChatColor.GRAY + "Eine Spur aus Feuer und Asche... ",
                ChatColor.DARK_PURPLE + "» " + ChatColor.GRAY + "Nur die wahren Helden legen sich",
                ChatColor.DARK_PURPLE + "   " + ChatColor.GRAY + "mit den Drachen dieser Welt an!",
                "",
                ChatColor.RED + "" + ChatColor.BOLD + "Drachen-Kills: " + ChatColor.YELLOW + kills,
                "",
                ChatColor.DARK_AQUA + progressMessage, // Dynamischer Fortschrittstext
                "",
                ChatColor.GOLD + "» " + ChatColor.GRAY + "Erhebe dich im Kampf, stärke deinen Ruf",
                ChatColor.GOLD + "   " + ChatColor.GRAY + "und fordere immer größere Mächte heraus.",
                ChatColor.GOLD + "» " + ChatColor.GRAY + "Mit jedem besiegten Drachen wächst",
                ChatColor.GOLD + "   " + ChatColor.GRAY + "dein Ansehen und deine Macht.",
                "",
                ChatColor.DARK_GREEN + "" + ChatColor.BOLD + "Dein Ziel ist klar:",
                ChatColor.DARK_GREEN + "   " + ChatColor.GRAY + "Steige zur Legende auf und entfessle",
                ChatColor.DARK_GREEN + "   " + ChatColor.GRAY + "die wahre Kraft der Relikte!"
        ));

        item.setItemMeta(meta);
        return item;
    }
    // Methode zur Rückgabe der dynamischen Fortschrittstexte basierend auf der Anzahl der Drachenkills
    private String getProgressMessage(int kills) {
        if (kills < 10) {
            return "Du hast gerade erst begonnen, den ersten Funken der Macht zu erahnen...";
        } else if (kills < 20) {
            return "Die Asche der gefallenen Drachen klebt bereits an deinen Stiefeln!";
        } else if (kills < 30) {
            return "Du fühlst die Flammen des Ruhms. Mehr Drachen warten!";
        } else if (kills < 40) {
            return "Deine Stärke wächst. Die Drachen beginnen, deinen Namen zu fürchten.";
        } else if (kills < 50) {
            return "Ein wahrer Krieger erhebt sich. Doch noch warten viele Kämpfe!";
        } else if (kills < 60) {
            return "Die Luft ist erfüllt von deiner Macht. Der nächste Drache zittert.";
        } else if (kills < 70) {
            return "Ein Funke der Legende ist in dir erwacht. Er wird weiter brennen!";
        } else if (kills < 80) {
            return "Du hast viele besiegt, doch dein Durst nach Macht wächst weiter.";
        } else if (kills < 90) {
            return "Die Drachengötter erkennen deine Stärke. Beweise es ihnen!";
        } else if (kills < 100) {
            return "Ein legendärer Kämpfer formt sich. Die letzte Stufe ruft.";
        } else if (kills < 120) {
            return "Du stehst kurz davor, das Geheimnis unendlicher Kraft zu lüften.";
        } else if (kills < 140) {
            return "Die Welt spricht von deinem Namen. Der nächste Drache erwartet dich!";
        } else if (kills < 160) {
            return "Du hast es fast geschafft. Doch wahre Legenden ruhen nicht.";
        } else if (kills < 180) {
            return "Die Relikte rufen nach dir, ihre Macht wird bald dir gehören!";
        } else if (kills < 200) {
            return "Der Gipfel des Ruhms ist nahe, doch der letzte Kampf wartet.";
        } else if (kills < 225) {
            return "Du hörst das Flüstern der Drachen. Die Macht ist zum Greifen nah.";
        } else if (kills < 250) {
            return "Ein wahrer Meister aller Kämpfe, der Sieg liegt in deiner Hand.";
        } else if (kills < 275) {
            return "Deine Macht ist unaussprechlich. Keine Kreatur wagt dir zu trotzen!";
        } else if (kills < 300) {
            return "Du stehst am Höhepunkt. Nur wenige Drachen trennen dich vom Erbe!";
        } else {
            return "DU BIST DIE LEGENDE! Die Relikte beugen sich deiner unendlichen Kraft!";
        }
    }

    private ItemStack createRelicItem(Material material, String name, String description, boolean unlocked) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + name);
        if (unlocked) {
            meta.setLore(Arrays.asList(description, ChatColor.GREEN + "Freigeschaltet", ChatColor.BLUE + "Glanz des Relikts"));
            //meta.addEnchant(Enchantment.LUCK, 1, true);
        } else {
            meta.setLore(Arrays.asList(description, ChatColor.RED + "Gesperrt - Mehr Drachenkills nötig"));
            //meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createDecorativeItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
    private final Map<String, String> relicItemMappings = new HashMap<String, String>() {{
        put("Schutz", "freaky_schutz");
        put("Schärfe", "freaky_scharf");
        put("Unzerbrechlich", "freaky_break");
        put("Effizienz", "freaky_effi");
        put("Glück", "freaky_lucky");
        put("Plünderung", "freaky_plunder");
    }};
    private final Map<String, Integer> relicKillRequirements = new HashMap<String, Integer>() {{
        put("Schutz", 250);        //  Kills für "Schutz"
        put("Schärfe", 100);       //  Kills für "Schärfe"
        put("Unzerbrechlich", 150); //  Kills für "Unzerbrechlich"
        put("Effizienz", 50);     //  Kills für "Effizienz"
        put("Glück", 250);         //  Kills für "Glück"
        put("Plünderung", 250);    //  Kills für "Plünderung"
    }};

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals(ChatColor.DARK_PURPLE + "Deine Drachen-Statistiken & Relikte")) {
            event.setCancelled(true);

            if (event.getCurrentItem() == null || !event.getCurrentItem().hasItemMeta()) return;

            Player player = (Player) event.getWhoClicked();
            String itemName = ChatColor.stripColor(event.getCurrentItem().getItemMeta().getDisplayName());

            // Prüfe, ob der Name des Items mit einem Relikt übereinstimmt
            String oraxenItemId = relicItemMappings.get(itemName);
            Integer requiredKills = relicKillRequirements.get(itemName); // Hol die benötigten Kills

            if (oraxenItemId != null && requiredKills != null) {
                int playerKills = gameLoop.getDragonKills(player.getUniqueId()); // Aktuelle Drachenkills des Spielers

                // Überprüfe, ob der Spieler genügend Kills hat
                if (playerKills >= requiredKills) {
                    ItemStack oraxenItem = OraxenItems.getItemById(oraxenItemId).build();
                    checkAndOpenRelicUI(player, oraxenItem);
                } else {
                    player.sendMessage(ChatColor.RED + "Du benötigst mindestens " + requiredKills + " Drachenkills, um dieses Relikt zu verwenden.");
                }
            } else {
                player.sendMessage(ChatColor.RED + "Unbekanntes Relikt.");
            }
        }
    }


    private void checkAndOpenRelicUI(Player player, ItemStack requiredItem) {
        boolean hasRequiredItem = false;

        // Überprüfe, ob der Spieler das benötigte Oraxen-Item im Inventar hat
        for (ItemStack item : player.getInventory()) {
            if (compareOraxenItems(item, requiredItem)) {
                hasRequiredItem = true;
                break;
            }
        }

        // Wenn das Item gefunden wurde, öffne die GUI
        if (hasRequiredItem) {
            // Get the Oraxen item ID and use it to determine the category
            String itemId = OraxenItems.getIdByItem(requiredItem);
            String relicCategory = getRelicCategoryFromItemId(itemId);

            if (relicCategory != null) {
                openRelicInteractionUI(player, relicCategory); // Pass player and category
            } else {
                player.sendMessage(ChatColor.RED + "Ungültiges Relikt.");
            }
        } else {
            player.sendMessage(ChatColor.RED + "Du besitzt nicht das notwendige Relikt, um fortzufahren.");
        }
    }

    private String getRelicCategoryFromItemId(String itemId) {
        switch (itemId) {
            case "freaky_schutz":
                return "Schutz";
            case "freaky_scharf":
                return "Schärfe";
            case "freaky_break":
                return "Unzerbrechlich";
            case "freaky_effi":
                return "Effizienz";
            case "freaky_lucky":
                return "Glück";
            case "freaky_plunder":
                return "Plünderung";
            default:
                return null;
        }
    }
    private void openRelicInteractionUI(Player player, String relicCategory) {
        Enchantment enchantment = detectEnchantmentFromCategory(relicCategory);

        if (enchantment == null) {
            player.sendMessage(ChatColor.RED + "Unbekannte Relikt-Kategorie.");
            return;
        }

        openUpgradeGUI(player, enchantment, relicCategory);
    }
    private Enchantment detectEnchantmentFromCategory(String category) {
        switch (category) {
            case "Schutz":
                return Enchantment.PROTECTION;
            case "Schärfe":
                return Enchantment.SHARPNESS;
            case "Unzerbrechlich":
                return Enchantment.UNBREAKING;
            case "Effizienz":
                return Enchantment.EFFICIENCY;
            case "Glück":
                return Enchantment.FORTUNE;
            case "Plünderung":
                return Enchantment.LOOTING;
            default:
                return null;
        }
    }

    private boolean compareOraxenItems(ItemStack item1, ItemStack item2) {
        if (item1 == null || item2 == null) return false;

        // Hol die Oraxen-ID für beide Items
        String itemId1 = OraxenItems.getIdByItem(item1);
        String itemId2 = OraxenItems.getIdByItem(item2);

        // Wenn eine der IDs null ist, sind die Items keine Oraxen-Items oder nicht vergleichbar
        if (itemId1 == null || itemId2 == null) return false;

        // Vergleiche die Oraxen-IDs
        return itemId1.equals(itemId2);
    }


    private List<ItemStack> getUpgradeableItems(Player player, Enchantment enchantment) {
        List<ItemStack> upgradeableItems = new ArrayList<>();
        Set<ItemStack> uniqueItems = new HashSet<>(); // Verhindere doppelte Einträge

        // Check inventory slots
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.containsEnchantment(enchantment)) {
                uniqueItems.add(item);
            }
        }

        // Check armor slots
        for (ItemStack armorItem : player.getEquipment().getArmorContents()) {
            if (armorItem != null && armorItem.containsEnchantment(enchantment)) {
                uniqueItems.add(armorItem);
            }
        }

        upgradeableItems.addAll(uniqueItems);
        return upgradeableItems;
    }

    private void openUpgradeGUI(Player player, Enchantment enchantment, String category) {
        Inventory upgradeInventory = Bukkit.createInventory(null, 9, ChatColor.GOLD + "Upgrade " + category + " Items");

        List<ItemStack> itemsToUpgrade = getUpgradeableItems(player, enchantment);
        if (itemsToUpgrade.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Du hast keine Gegenstände, die für dieses Relikt aufrüstbar sind.");
            return;
        }

        int slot = 0;
        for (ItemStack item : itemsToUpgrade) {
            ItemStack displayItem = new ItemStack(item);  // Kopiere das Item für die Anzeige
            ItemMeta meta = displayItem.getItemMeta();
            meta.setDisplayName(ChatColor.YELLOW + "Level " + item.getEnchantmentLevel(enchantment));
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Dieses Item kann aufgewertet werden.",
                    ChatColor.GRAY + "Lege es in dein Inventar und klicke, um es zu verbessern."
            ));
            displayItem.setItemMeta(meta);

            upgradeInventory.setItem(slot++, displayItem);
        }

        player.openInventory(upgradeInventory);
    }

    @EventHandler
    public void onInventoryClickUpdgradeee(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        // Wenn kein Item geklickt wurde oder das Item keine Metadaten hat, beenden
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        // Prüfen, ob der Klick in der GUI "Upgrade" stattfindet
        if (event.getView().getTitle().contains("Upgrade")) {
            // Unterscheiden, ob der Spieler im GUI oder in seinem eigenen Inventar klickt
            if (event.getClickedInventory() == event.getView().getTopInventory()) {
                // Klickt in der GUI, blockiere den Klick und informiere den Spieler
                event.setCancelled(true); // Klicks in der GUI sollen blockiert werden
                player.sendMessage(ChatColor.RED + "Lege das Item in dein Inventar und klicke es dort an, um es zu verbessern.");
                return;
            } else if (event.getClickedInventory() == player.getInventory()) {
                // Der Spieler klickt in seinem eigenen Inventar
                handleInventoryUpgradeClick(event, player, clickedItem);
                return;
            }
        }
    }

    private void handleInventoryUpgradeClick(InventoryClickEvent event, Player player, ItemStack clickedItem) {
        Enchantment enchantment = detectEnchantmentFromItem(clickedItem); // Bestimme das Enchantment des Items
        if (enchantment == null) return; // Falls kein Enchantment vorhanden ist, beenden

        int currentLevel = clickedItem.getEnchantmentLevel(enchantment);
        int nextLevel = currentLevel + 1;

        if (currentLevel >= 100) {
            player.sendMessage(ChatColor.RED + "Dieses Item hat das maximale Upgrade-Level erreicht.");
            return;
        }

        // Prüfe, ob der Spieler genug Freaky XP hat
        if (!gameLoop.deductFreakyXP(player, 5000)) { // Beispiel: 5000 Freaky XP pro Upgrade
            player.sendMessage(ChatColor.RED + "Dieses Item konnte nicht verbessert werden.");
            return;
        }

        // Reliktkosten basierend auf der neuen Stufe (nextLevel)
        int requiredRelics = nextLevel;  // Beispiel: für Level 8 sind 8 Relikte nötig

        // Prüfe, ob der Spieler genügend Relikte im Inventar hat
        String relicCategory = detectRelicCategoryFromEnchantment(enchantment); // Detektiere die Reliktkategorie
        int availableRelics = countRelicsInInventory(player, relicCategory); // Zähle die vorhandenen Relikte

        if (availableRelics < requiredRelics) {
            player.sendMessage(ChatColor.RED + "Du benötigst " + requiredRelics + " Relikte, um dieses Item zu verbessern. Du hast nur " + availableRelics + ".");
            return;
        }

        // Entferne die benötigte Anzahl Relikte aus dem Inventar
        removeRelicsFromInventory(player, relicCategory, requiredRelics);
        player.sendMessage(ChatColor.GREEN + "" + requiredRelics + " Relikte wurden für das Upgrade verwendet.");

        // Führe das Upgrade durch
        clickedItem.addUnsafeEnchantment(enchantment, nextLevel);
        player.sendMessage(ChatColor.GREEN + "Das Item wurde erfolgreich auf Level " + nextLevel + " verbessert.");
    }

    // Hilfsmethode, um das Enchantment eines Items zu erkennen
    private Enchantment detectEnchantmentFromItem(ItemStack item) {
        for (Enchantment ench : item.getEnchantments().keySet()) {
            return ench; // Nimm das erste gefundene Enchantment
        }
        return null; // Kein Enchantment vorhanden
    }

    // Zählt die Relikte einer bestimmten Kategorie im Inventar des Spielers
    private int countRelicsInInventory(Player player, String relicCategory) {
        String relicItemId = getRelicItemIdFromCategory(relicCategory);
        if (relicItemId == null) return 0;

        int count = 0;
        for (ItemStack item : player.getInventory()) {
            if (item != null) {
                String itemId = OraxenItems.getIdByItem(item);
                if (itemId != null && itemId.equals(relicItemId)) {
                    count += item.getAmount();  // Add the number of relics
                }
            }
        }
        return count;
    }


    // Entfernt eine bestimmte Anzahl Relikte aus dem Inventar des Spielers
    private void removeRelicsFromInventory(Player player, String relicCategory, int amount) {
        String relicItemId = getRelicItemIdFromCategory(relicCategory);
        if (relicItemId == null) return;

        for (ItemStack item : player.getInventory()) {
            if (item != null) {
                String itemId = OraxenItems.getIdByItem(item);
                if (itemId != null && itemId.equals(relicItemId)) {
                    int stackAmount = item.getAmount();

                    if (stackAmount > amount) {
                        item.setAmount(stackAmount - amount);
                        break;
                    } else {
                        player.getInventory().removeItem(item);
                        amount -= stackAmount;
                        if (amount <= 0) break;
                    }
                }
            }
        }
    }


    private Enchantment detectEnchantmentFromTitle(String title) {
        if (title.contains("Schutz")) return Enchantment.PROTECTION;
        if (title.contains("Schärfe")) return Enchantment.SHARPNESS;
        if (title.contains("Unzerbrechlich")) return Enchantment.UNBREAKING;
        if (title.contains("Effizienz")) return Enchantment.EFFICIENCY;
        if (title.contains("Glück")) return Enchantment.FORTUNE;
        if (title.contains("Plünderung")) return Enchantment.LOOTING;
        return null;
    }

    // Hilfsmethode zur Detektion der Reliktkategorie basierend auf dem Enchantment
    private String detectRelicCategoryFromEnchantment(Enchantment enchantment) {
        if (enchantment.equals(Enchantment.PROTECTION)) return "Schutz";
        if (enchantment.equals(Enchantment.SHARPNESS)) return "Schärfe";
        if (enchantment.equals(Enchantment.UNBREAKING)) return "Unzerbrechlich";
        if (enchantment.equals(Enchantment.EFFICIENCY)) return "Effizienz";
        if (enchantment.equals(Enchantment.FORTUNE)) return "Glück";
        if (enchantment.equals(Enchantment.LOOTING)) return "Plünderung";
        return null;
    }
    // Hilfsmethode, um das passende Relikt im Inventar des Spielers zu finden
    private ItemStack findRelicInInventory(Player player, String relicCategory) {
        String relicItemId = getRelicItemIdFromCategory(relicCategory); // Hol die Item-ID des Relikts

        if (relicItemId == null) return null;

        // Überprüfe das Inventar des Spielers nach dem entsprechenden Relikt
        for (ItemStack item : player.getInventory()) {
            if (item != null && OraxenItems.getIdByItem(item).equals(relicItemId)) {
                return item;
            }
        }
        return null;
    }
    // Hilfsmethode, um die Relikt-Item-ID anhand der Kategorie zu ermitteln
    private String getRelicItemIdFromCategory(String category) {
        switch (category) {
            case "Schutz": return "freaky_schutz";
            case "Schärfe": return "freaky_scharf";
            case "Unzerbrechlich": return "freaky_break";
            case "Effizienz": return "freaky_effi";
            case "Glück": return "freaky_lucky";
            case "Plünderung": return "freaky_plunder";
            default: return null;
        }
    }
}

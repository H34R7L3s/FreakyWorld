package h34r7l3s.freakyworld;

import net.dv8tion.jda.api.entities.channel.attribute.IGuildChannelContainer;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;

import org.bukkit.potion.PotionType;
import io.th0rgal.oraxen.api.OraxenItems;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class QuestVillager implements Listener {
    private FreakyWorld plugin;
    private Location villagerLocation = new Location(Bukkit.getWorld("World"), -40, 14, -50);
    private Villager questVillager;
    private DiscordBot discordBot;
    private Set<UUID> hasInteracted = new HashSet<>();
    private GameLoop gameLoop;

    public QuestVillager(FreakyWorld plugin, DiscordBot discordBot, GameLoop gameLoop) {
        this.plugin = plugin;
        this.discordBot = discordBot;
        this.gameLoop = gameLoop;
    }

    private final Set<Material> validMaterials = new HashSet<>(Arrays.asList(
            Material.IRON_INGOT,
            Material.SCAFFOLDING,
            Material.BOOKSHELF,
            Material.TNT,
            Material.ENDER_EYE,
            Material.DIAMOND,
            Material.SOUL_CAMPFIRE
    ));

    private final Map<Material, String> materialRewards = new HashMap<>() {{
        put(Material.IRON_INGOT, "freaky_ingot");
        put(Material.SCAFFOLDING, "auftragsbuch");
        put(Material.BOOKSHELF, "freaky_wissen");
        put(Material.TNT, "kriegsmarke");
        put(Material.ENDER_EYE, "eisenherz");
        put(Material.DIAMOND, "freaky_coin");
        put(Material.SOUL_CAMPFIRE, "freakyworlds_willen");
    }};

    private Map<UUID, Material> playerSelectedMaterial = new HashMap<>();
    private Map<UUID, Integer> playerSelectedAmount = new HashMap<>();

    public void spawnVillager() {
        questVillager = (Villager) Bukkit.getWorld("World").spawnEntity(villagerLocation, EntityType.VILLAGER);
        questVillager.setCustomName("Hugo Heissluft");
        questVillager.setAI(false);
        questVillager.setInvulnerable(true);

        // Setzen der Rotation des Villagers um 180 Grad
        float currentYaw = questVillager.getLocation().getYaw();
        float newYaw = currentYaw + 180;
        newYaw = newYaw % 360; // Stellt sicher, dass der Wert zwischen 0 und 360 liegt


        questVillager.setRotation(newYaw, questVillager.getLocation().getPitch());

        initializeHugoLookTask();
    }

    private void initializeHugoLookTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (questVillager != null && !questVillager.isDead()) {
                    lookAtNearestPlayerHugo();
                }
            }
        }.runTaskTimer(plugin, 0L, 40L); // alle 2 Sekunden (40 Ticks)
    }

    private void lookAtNearestPlayerHugo() {
        Collection<Player> nearbyPlayers = questVillager.getWorld().getNearbyPlayers(questVillager.getLocation(), 10);
        Player nearestPlayer = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Player player : nearbyPlayers) {
            double distance = player.getLocation().distanceSquared(questVillager.getLocation());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestPlayer = player;
            }
        }

        if (nearestPlayer != null) {
            Location villagerLocation = questVillager.getLocation();
            Location playerLocation = nearestPlayer.getLocation();

            // Berechne die Richtung zum Spieler und setze die Rotation von Hugo
            double dx = playerLocation.getX() - villagerLocation.getX();
            double dz = playerLocation.getZ() - villagerLocation.getZ();
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            villagerLocation.setYaw(yaw);
            questVillager.teleport(villagerLocation); // Aktualisiert die Blickrichtung von Hugo
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractAtEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (entity instanceof Villager) {
            String customName = entity.getCustomName();
            if (customName != null && customName.equals("Hugo Heissluft")) {
                openQuestGUI(event.getPlayer());
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        Inventory clickedInventory = event.getClickedInventory();
        ItemStack clickedItem = event.getCurrentItem();
        String inventoryTitle = event.getView().getTitle();

        if (clickedInventory == null || clickedItem == null) return;

        if (inventoryTitle.equals("Geben Sie Ihre Items ab")) {
            event.setCancelled(true);

            if (event.getRawSlot() == 8 && clickedItem.getType() == Material.GREEN_WOOL) {
                handleItemSubmission(player, true, -1);
            } else if (validMaterials.contains(clickedItem.getType())) {
                handleItemSubmission(player, false, event.getRawSlot());
            } else {
                player.sendMessage(ChatColor.RED + "Ungültiges Item angeklickt.");
            }
            player.updateInventory();
        } else if (inventoryTitle.equals("Quest Items")) {
            if (clickedItem.getType() == Material.WRITTEN_BOOK) {
                BookMeta clickedBookMeta = (BookMeta) clickedItem.getItemMeta();
                if (clickedBookMeta != null && clickedBookMeta.hasCustomModelData() && clickedBookMeta.getCustomModelData() == 123456) {
                    event.setCancelled(true);
                    openQuestBook(player);
                }
            } else {
                if (validMaterials.contains(clickedItem.getType())) {
                    playerSelectedMaterial.put(player.getUniqueId(), clickedItem.getType());
                    openTradingInterface(player);
                }
            }
        }
    }


    private void openTradingInterface(Player player) {
        Inventory tradeInventory = Bukkit.createInventory(null, 9, "Geben Sie Ihre Items ab");
        tradeInventory.setItem(8, createGreenWool());
        player.openInventory(tradeInventory);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTitle().equals("Geben Sie Ihre Items ab")) {
            for (ItemStack item : event.getNewItems().values()) {
                if (!validMaterials.contains(item.getType())) {
                    event.setCancelled(true);
                    break;
                }
            }
        }
    }

    private ItemStack createGreenWool() {
        ItemStack item = new ItemStack(Material.GREEN_WOOL, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GREEN + "Alles für mich? Her damit!!");
            item.setItemMeta(meta);
        }
        return item;
    }

    private void handleItemSubmission(Player player, boolean isWoolBlockClicked, int clickedSlot) {
        UUID playerId = player.getUniqueId();
        if (!playerSelectedMaterial.containsKey(playerId)) return;

        Material selectedMaterial = playerSelectedMaterial.get(playerId);
        Inventory playerInv = player.getInventory();
        int totalAmount = 0;

        if (isWoolBlockClicked) {
            totalAmount = countMaterial(playerInv, selectedMaterial);
        } else {
            ItemStack clickedItem = playerInv.getItem(clickedSlot);
            if (clickedItem != null) {
                totalAmount = clickedItem.getAmount();
            } else {
                player.sendMessage(ChatColor.RED + "Es ist kein Item in diesem Slot.");
                return;
            }
        }

        int amountRemoved = removeItemsFromPlayer(player, selectedMaterial, totalAmount, isWoolBlockClicked);

        if (amountRemoved > 0) {
            plugin.getLogger().info("Player " + player.getName() + " removed " + amountRemoved + " of " + selectedMaterial);
            updatePlayerProgress(player, amountRemoved, selectedMaterial);
            int rewardCount = amountRemoved / 1000;
            int xpEarned = calculateXPEarned(amountRemoved, selectedMaterial);

            // XP hinzufügen
            gameLoop.addXPToPlayer(player, xpEarned, xpEarned);

            if (rewardCount > 0) {
                String rewardItem = materialRewards.get(selectedMaterial);
                for (int i = 0; i < rewardCount; i++) {
                    giveReward(player, rewardItem);
                }
            } else {
                giveReward(player, "angsthase");
            }

            sendTradeConfirmationToDiscord(player, new ItemStack(selectedMaterial, amountRemoved), amountRemoved);
            player.sendMessage(ChatColor.GREEN + "Du hast " + amountRemoved + "x " + selectedMaterial.toString() + " abgegeben und " + rewardCount + " Belohnungen sowie " + xpEarned + " XP erhalten!");
        } else {
            plugin.getLogger().warning("Player " + player.getName() + " could not remove items. Amount removed: " + amountRemoved);
        }

        player.updateInventory();
        playerSelectedMaterial.remove(playerId);
    }




    private int calculateXPEarned(int amountRemoved, Material selectedMaterial) {
        String category = materialCategories.get(selectedMaterial);
        int baseXP = categoryXpValues.getOrDefault(category, 0);
        return amountRemoved * baseXP;
    }


    private int countMaterial(Inventory inventory, Material material) {
        int count = 0;
        for (ItemStack item : inventory.getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private int removeItemsFromPlayer(Player player, Material material, int amountToRemove, boolean isWoolBlockClicked) {
        Inventory playerInv = player.getInventory();
        int totalRemoved = 0;

        for (int i = 0; i < playerInv.getSize(); i++) {
            ItemStack item = playerInv.getItem(i);
            if (item != null && item.getType() == material) {
                int amountInStack = item.getAmount();

                if (isWoolBlockClicked) {
                    totalRemoved += amountInStack;
                    playerInv.clear(i);
                } else {
                    if (amountInStack >= amountToRemove - totalRemoved) {
                        item.setAmount(amountInStack - (amountToRemove - totalRemoved));
                        totalRemoved = amountToRemove;
                        break;
                    } else {
                        totalRemoved += amountInStack;
                        playerInv.clear(i);
                    }

                    if (totalRemoved >= amountToRemove) {
                        break;
                    }
                }
            }
        }

        return totalRemoved;
    }


    private void giveReward(Player player, String rewardItem) {
        // Use Oraxen API to get and give the item
        ItemStack oraxenItem = OraxenItems.getItemById(rewardItem).build();
        if (oraxenItem != null) {
            player.getInventory().addItem(oraxenItem);
        } else {
            player.sendMessage(ChatColor.RED + "Fehler: Belohnung konnte nicht gefunden werden.");
        }
    }

    public void sendTradeConfirmationToDiscord(Player player, ItemStack itemStack, int amount) {
        if (itemStack == null || player == null) {
            return;
        }

        String itemName = itemStack.getType().toString();
        String message = player.getName() + " hat " + amount + "x " + itemName+" abgegeben!";
        discordBot.sendMessageToDiscord(message);
    }

    private ItemStack createNamedItem(Material material, String name) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(name);

            // Verzauberungen für Schwert und Rüstung hinzufügen
            if (material == Material.DIAMOND_SWORD) {
                meta.addEnchant(Enchantment.SHARPNESS, 1, true); // Schärfe 1
            } else if (material == Material.IRON_HELMET || material == Material.IRON_CHESTPLATE ||
                    material == Material.IRON_LEGGINGS || material == Material.IRON_BOOTS) {
                // Beispiel für eine zufällige Verzauberung
                meta.addEnchant(Enchantment.PROTECTION, 1, true);
            }

            item.setItemMeta(meta);
        }

        // Zusätzliche Eigenschaften für Tränke
        if (material == Material.POTION) {
            PotionMeta potionMeta = (PotionMeta) item.getItemMeta();
            if (potionMeta != null) {
                // Beispiel: Erstellen eines Heiltranks
                potionMeta.setBasePotionType(PotionType.HEALING);
                item.setItemMeta(potionMeta);
            }
        }

        return item;
    }

    // Dieses Event tritt ein, wenn der Spieler das Handelsinventar benutzt
    @EventHandler
    public void onTrade(InventoryClickEvent event) {
        Inventory inv = event.getInventory();
        if (inv.getHolder() == null && "Geben Sie Ihre Items ab".equals(event.getView().getTitle())) {
            event.setCancelled(true);

            ItemStack clickedItem = event.getCurrentItem();
            Player player = (Player) event.getWhoClicked();

            // Überprüfen, ob der grüne Wollblock angeklickt wurde
            if (clickedItem != null && clickedItem.getType() == Material.GREEN_WOOL) {
                // Grüner Wollblock geklickt: Verarbeiten Sie die Abgabe aller Items des ausgewählten Typs
                handleItemSubmission(player, true, -1);
                player.updateInventory();
            } else if (clickedItem != null && validMaterials.contains(clickedItem.getType())) {
                // Ein spezifisches Item geklickt: Verarbeiten Sie die Abgabe des angeklickten Item-Stacks
                handleItemSubmission(player, false, event.getRawSlot());
                player.updateInventory();
            } else {
                // Nachricht an den Spieler, wenn auf einen ungültigen oder leeren Slot geklickt wurde
                player.sendMessage(ChatColor.RED + "Ungültiges Item angeklickt.");
                player.updateInventory();
            }
        }
    }

    private void openQuestGUI(Player player) {
        Inventory questInventory = Bukkit.createInventory(null, 9, "Quest Items");

        // Füge die benannten Items zum Inventar hinzu
        questInventory.setItem(0, createNamedItem(Material.IRON_INGOT, "Schmiedekunst"));
        questInventory.setItem(1, createNamedItem(Material.SCAFFOLDING, "Baukuenste"));
        questInventory.setItem(2, createNamedItem(Material.BOOKSHELF, "Wissenschaft"));
        questInventory.setItem(3, createNamedItem(Material.TNT, "Kriegsfuehrung"));
        questInventory.setItem(4, createNamedItem(Material.ENDER_EYE, "Selbstfindung"));
        questInventory.setItem(5, createNamedItem(Material.DIAMOND, "Reichtum"));
        questInventory.setItem(6, createNamedItem(Material.SOUL_CAMPFIRE, "Unbekannt"));
        questInventory.setItem(7, createNamedItem(Material.CAKE, "Lecker Kuchen - Mich gibts um sonst!")); // Dies wird später spezifiziert

        questInventory.setItem(8, createQuestBook());

        // Öffne das Inventar für den Spieler
        player.openInventory(questInventory);
    }

    // In QuestVillager Klasse
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inv = event.getInventory();
        if ("Geben Sie Ihre Items ab".equals(event.getView().getTitle())) {
            Map<Material, Integer> itemCounts = new HashMap<>();

            // Zähle jedes gültige Item im Inventar
            for (ItemStack item : inv.getContents()) {
                if (item != null && validMaterials.contains(item.getType())) {
                    int count = itemCounts.getOrDefault(item.getType(), 0);
                    itemCounts.put(item.getType(), count + item.getAmount());
                }
            }

            // Sende Bestätigungsnachrichten und Discord-Nachrichten für jedes abgegebene Item
            Player player = (Player) event.getPlayer();
            itemCounts.forEach((material, count) -> {
                player.sendMessage(ChatColor.GREEN + "Du hast " + count + "x " + material.toString() + " abgegeben!");

                // Correctly create an ItemStack from the material and count
                ItemStack itemStack = new ItemStack(material, count);

                // Now call the method with the correct ItemStack parameter
                sendTradeConfirmationToDiscord(player, itemStack, count);
            });
        }
    }

    private ItemStack createQuestBook() {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta bookMeta = (BookMeta) book.getItemMeta();

        if (bookMeta != null) {
            bookMeta.setTitle(ChatColor.MAGIC + "Erben der FreakyWorld" + ChatColor.RESET);
            bookMeta.setAuthor(ChatColor.DARK_PURPLE + "" + ChatColor.ITALIC + "Unbekannt");

            // Setzen des benutzerdefinierten Metadaten-Schlüssels
            bookMeta.setCustomModelData(123456); // Beispielwert, bitte durch einen eindeutigen Wert ersetzen

            // Erstellen der Kapitelbeschreibungen in der Lore
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.RESET + "" + ChatColor.LIGHT_PURPLE + "Freaky" + ChatColor.LIGHT_PURPLE + "World" + ChatColor.DARK_PURPLE + " Season" + ChatColor.GOLD + " 4");
            bookMeta.setLore(lore);

            // Erstellen der Seiteninhalte
            List<String> pages = new ArrayList<>();

            pages.add(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Season 4 - Willkommen Freaks!\n\n" + ChatColor.RESET +
                    ChatColor.DARK_GRAY + "Mein Name ist Hugo Heissluft.\n" +
                    "Einige von euch kennen mich bereits,\n" +
                    ChatColor.GOLD + "" + ChatColor.BOLD+"für alle neuen Herzlich Willkommen!\n");

            pages.add(ChatColor.GOLD + "" + ChatColor.BOLD + "Erneut wagen wir einen Neustart \n" + ChatColor.RESET +
                    ChatColor.DARK_GRAY+"und müssen in der HCFW unsere alte Heimat sichern und zurückerobern.\n" +
                    "Wie ihr sehen könnt,\n" +
                    "befinden wir uns bereits in einer neuen Welt.\n" +
                    "");
                    //Edit


            pages.add(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Der Spawn ist bereits errichtet.\n" + ChatColor.RESET +
                    ChatColor.DARK_GRAY+"Eure Aufgabe besteht darin, eine neue Stadt zu errichten.\n" +
                    "Die Umgebung muss erneut aufgebaut werden.\n" +
                    "Damit meine ich wirklich die DIREKTE Umgebung!\n" +
                    "Der Spawn, Nein, gleich die gesamte Insel muss umgebaut werden!\n" +
                    "Alle wesentlichen Bestandteile unserer beliebten Stadt fehlen aktuell wieder.\n" +
                    "\n" +
                    "Jedem steht es frei so zu bauen und spielen wie er möchte.\n");

            pages.add(ChatColor.GOLD + "" + ChatColor.BOLD + "Es gibt ein paar Besonderheiten bei uns.\n" + ChatColor.RESET +
                    "Angefangen bei MIR, Hugo.\n" +
                    "\n" +
                    "Bei mir könnt ihr Items abgeben. \n" +
                    "Diese sind nötig, nunja...\n"+
                    ChatColor.GREEN+"für wirklich viel.."
            );

            pages.add(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Je mehr Items, um so mehr Freaky-XP bekommst du!\n" + ChatColor.RESET +
                    "Wir haben auch andere Villager.\n" +
                    "Wir haben einen Meisterschmied, einen Gildenmeister, Dave, einen Bazar Villager, den Uralten Wächter und FrekayWorld.\n"+
                    "Und natürlich..."+ ChatColor.DARK_RED +" unsere ALTE WELT!"+
                    "\n"
            );

            pages.add(ChatColor.GOLD + "" + ChatColor.BOLD + "Tools und Rüstungen\n" + ChatColor.RESET +
                    "Der Meisterschmied kann für euch nützliche Gegenstände herstellen.\n" +
                    "Wenn ihr fleißig beim Wiederaufbau der Stadt mithelft,\n" +
                    ChatColor.GOLD + "werdet ihr dafür wirklich gut entlohnt!\n"+
                    ChatColor.RESET+"Eigentlich ist das auch nh ganz netter...");

            pages.add(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Stürzt euch in ein neues Abenteuer,\n" + ChatColor.RESET +
                    " ob gemeinsam, allein oder als Gilde.\n"+
                    ChatColor.GOLD+"Euer Hugo Heissluft");

            bookMeta.setPages(pages);
            book.setItemMeta(bookMeta);
        }

        return book;
    }


    private void openQuestBook(Player player) {
        ItemStack questBook = createQuestBook();
        player.openBook(questBook);
    }

    public void removeQuestVillager() {
        if (questVillager != null && !questVillager.isDead()) {
            questVillager.remove();
            questVillager = null;
            Bukkit.getLogger().info("Quest Villager wurde erfolgreich entfernt.");
        } else {
            Bukkit.getLogger().info("Quest Villager war bereits tot oder nicht gesetzt.");
        }
    }

    private void updatePlayerProgress(Player player, int amountSubmitted, Material material) {
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        String category = materialCategories.get(material);
        int xpEarned = amountSubmitted * categoryXpValues.get(category);

        try (Connection conn = gameLoop.getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO player_progress (uuid, player_name, items_submitted, level, freaky_xp, xp_on_hand) " +
                            "VALUES (?, ?, ?, ?, ?, ?) " +
                            "ON CONFLICT(uuid) DO UPDATE SET items_submitted = items_submitted + ?, " +
                            "level = level + ?, freaky_xp = freaky_xp + ?, xp_on_hand = xp_on_hand + ? WHERE uuid = ?");
            stmt.setString(1, playerId.toString());
            stmt.setString(2, playerName);
            stmt.setInt(3, amountSubmitted);
            stmt.setInt(4, calculateLevel(amountSubmitted));
            stmt.setInt(5, xpEarned);
            stmt.setInt(6, xpEarned); // Initial XP on hand should be the earned XP
            stmt.setInt(7, amountSubmitted);
            stmt.setInt(8, calculateLevel(amountSubmitted));
            stmt.setInt(9, xpEarned);
            stmt.setInt(10, xpEarned); // Add earned XP to xp_on_hand
            stmt.setString(11, playerId.toString());
            stmt.executeUpdate();

            // Update the item category table
            PreparedStatement categoryStmt = conn.prepareStatement(
                    "INSERT INTO player_item_categories (uuid, category, items_submitted, freaky_xp, player_name) " +
                            "VALUES (?, ?, ?, ?, ?) " +
                            "ON CONFLICT(uuid, category) DO UPDATE SET items_submitted = items_submitted + ?, " +
                            "freaky_xp = freaky_xp + ?, player_name = ? WHERE uuid = ? AND category = ?");
            categoryStmt.setString(1, playerId.toString());
            categoryStmt.setString(2, category);
            categoryStmt.setInt(3, amountSubmitted);
            categoryStmt.setInt(4, xpEarned);
            categoryStmt.setString(5, playerName);
            categoryStmt.setInt(6, amountSubmitted);
            categoryStmt.setInt(7, xpEarned);
            categoryStmt.setString(8, playerName);
            categoryStmt.setString(9, playerId.toString());
            categoryStmt.setString(10, category);
            categoryStmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    private int calculateLevel(int itemsSubmitted) {
        return itemsSubmitted / 1000; // Beispiel: 1000 Items = 1 Level
    }

    private int getTotalItemsSubmitted() {
        int total = 0;
        try (Connection conn = gameLoop.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT SUM(items_submitted) AS total FROM player_progress");
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                total = rs.getInt("total");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return total;
    }
    public int getPlayerContribution(Player player) {
        int playerItems = 0;
        UUID playerId = player.getUniqueId(); // Spieler-ID abrufen
        try (Connection conn = gameLoop.getConnection(); // Verbindung von gameLoop abrufen
             PreparedStatement stmt = conn.prepareStatement("SELECT items_submitted FROM player_progress WHERE uuid = ?")) {
            stmt.setString(1, playerId.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                playerItems = rs.getInt("items_submitted");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return playerItems;
    }

    public double calculatePlayerContributionPercentage(Player player) {
        int totalItems = getTotalItemsSubmitted();
        int playerItems = getPlayerContribution(player);

        if (totalItems == 0) return 0;

        return (double) playerItems / totalItems * 100;
    }
    private final Map<Material, String> materialCategories = new HashMap<>() {{
        put(Material.IRON_INGOT, "Schmiedekunst");
        put(Material.SCAFFOLDING, "Baukünste");
        put(Material.BOOKSHELF, "Wissenschaft");
        put(Material.TNT, "Kriegsführung");
        put(Material.ENDER_EYE, "Selbstfindung");
        put(Material.DIAMOND, "Reichtum");
        put(Material.SOUL_CAMPFIRE, "Unbekannt");
    }};

    private final Map<String, Integer> categoryXpValues = new HashMap<>() {{
        put("Schmiedekunst", 5);
        put("Baukünste", 5);
        put("Wissenschaft", 10);
        put("Kriegsführung", 10);
        put("Selbstfindung", 15);
        put("Reichtum", 20);
        put("Unbekannt", 20);
    }};

    public List<String> getTopPlayers() {
        List<String> topPlayers = new ArrayList<>();
        try (Connection conn = gameLoop.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT player_name, freaky_xp FROM player_progress ORDER BY freaky_xp DESC LIMIT 10");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String playerName = rs.getString("player_name");
                int xp = rs.getInt("freaky_xp");
                topPlayers.add(playerName + " - " + xp + " XP");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return topPlayers;
    }

    public List<String> getTopPlayersByCategory(String category, int limit) {
        List<String> topPlayers = new ArrayList<>();
        String sql = "SELECT pic.items_submitted, pp.player_name " +
                "FROM player_item_categories pic " +
                "JOIN player_progress pp ON pic.uuid = pp.uuid " +
                "WHERE pic.category = ? " +
                "ORDER BY pic.items_submitted DESC " +
                "LIMIT ?";

        try (Connection conn = gameLoop.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, category);
            pstmt.setInt(2, limit);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                String playerName = rs.getString("player_name");
                int score = rs.getInt("items_submitted");
                topPlayers.add(playerName + ": " + score);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return topPlayers;
    }



    public Map<Material, String> getMaterialCategories() {
        return materialCategories;
    }
}

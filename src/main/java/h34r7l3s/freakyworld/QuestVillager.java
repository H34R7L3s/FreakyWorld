
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
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;

import java.util.*;

public class QuestVillager implements Listener {
    private FreakyWorld plugin;
    private Location villagerLocation = new Location(Bukkit.getWorld("World"), -80, 82, 34);
    private Villager questVillager;
    private DiscordBot discordBot;
    private Set<UUID> hasInteracted = new HashSet<>();

    public QuestVillager(FreakyWorld plugin, DiscordBot discordBot) {
        this.plugin = plugin;
        this.discordBot = discordBot;
        // Additional setup...
    }
    private final Set<Material> validMaterials = new HashSet<>(Arrays.asList(
            Material.CRYING_OBSIDIAN,
            Material.DIAMOND_SWORD, // Schwert (muss verzaubert werden)
            Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS, // Eisen-Rüstungsset
            Material.ENCHANTED_GOLDEN_APPLE, Material.GOLDEN_APPLE,
            Material.POTION // Generischer Trank
    ));



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

    // Diese Methode überprüft, ob ein Item in das Abgabeinventar gelegt werden darf.
    private Map<UUID, Material> playerSelectedMaterial = new HashMap<>();
    private Map<UUID, Integer> playerSelectedAmount = new HashMap<>();

    private Map<UUID, Integer> playerSelectedItemSlot = new HashMap<>();

    private enum PlayerMode {
        QUEST_ITEM, ITEM_SUBMISSION
    }

    private Map<UUID, PlayerMode> playerModes = new HashMap<>();

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        Inventory clickedInventory = event.getClickedInventory();
        ItemStack clickedItem = event.getCurrentItem();
        String inventoryTitle = event.getView().getTitle();

        // Prüfen, ob das geklickte Inventar oder das Item null ist
        if (clickedInventory == null || clickedItem == null) return;

        // Behandeln von Klicks im "Geben Sie Ihre Items ab" Inventar
        if (inventoryTitle.equals("Geben Sie Ihre Items ab")) {
            event.setCancelled(true); // Verhindern, dass Items herausgenommen werden können

            // Prüfen, ob der grüne Wollblock geklickt wurde
            if (event.getRawSlot() == 8 && clickedItem.getType() == Material.GREEN_WOOL) {
                handleItemSubmission(player, true, -1); // Alle Items des ausgewählten Typs entfernen
            }
            // Prüfen, ob ein gültiges Material geklickt wurde
            else if (validMaterials.contains(clickedItem.getType())) {
                handleItemSubmission(player, false, event.getRawSlot()); // Nur den ausgewählten Item-Stack entfernen
            }
        }

        // Logik für das "Quest Items" Inventar und andere Fälle
        else if (inventoryTitle.equals("Quest Items")) {
            if (validMaterials.contains(clickedItem.getType())) {
                playerSelectedMaterial.put(player.getUniqueId(), clickedItem.getType());
                openTradingInterface(player);
            }
        }
    }



    private void openTradingInterface(Player player) {
        Inventory tradeInventory = Bukkit.createInventory(null, 9, "Geben Sie Ihre Items ab");
        // Füge einen grünen Wolle-Knopf hinzu, der als 'Abgeben' dient
        tradeInventory.setItem(8, createGreenWool());
        player.openInventory(tradeInventory);
    }
    // Verhindern, dass Items durch Ziehen aus dem Quest-Inventar gezogen werden
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTitle().equals("Geben Sie Ihre Items ab")) {
            // Erlaube das Ziehen von gültigen Materialien
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


    // Diese Methode handhabt die Abgabe von Items.
    // In QuestVillager Klasse
    private void handleItemSubmission(Player player, boolean isWoolBlockClicked, int clickedSlot) {
        UUID playerId = player.getUniqueId();
        if (!playerSelectedMaterial.containsKey(playerId)) return;

        Material selectedMaterial = playerSelectedMaterial.get(playerId);
        Inventory playerInv = player.getInventory();

        if (isWoolBlockClicked) {
            // Wenn der Wollblock geklickt wurde, entfernen Sie bis zu 64 Items des ausgewählten Typs
            int amountRemoved = removeItemsFromPlayer(player, selectedMaterial, 64, isWoolBlockClicked);

            sendTradeConfirmationToDiscord(player, new ItemStack(selectedMaterial, amountRemoved), amountRemoved);
            player.sendMessage(ChatColor.GREEN + "Du hast " + amountRemoved + "x " + selectedMaterial.toString() + " abgegeben!");
            player.updateInventory();
        } else {
            // Wenn ein einzelnes Item geklickt wurde, entfernen Sie nur diesen Item-Stack
            ItemStack itemInSlot = playerInv.getItem(clickedSlot);
            if (itemInSlot != null && itemInSlot.getType() == selectedMaterial) {
                int amountToRemove = itemInSlot.getAmount();
                itemInSlot.setAmount(0);
                sendTradeConfirmationToDiscord(player, new ItemStack(selectedMaterial, amountToRemove), amountToRemove);
                player.sendMessage(ChatColor.GREEN + "Du hast " + amountToRemove + "x " + selectedMaterial.toString() + " abgegeben!");
                player.updateInventory();
            }
        }
        // Aktualisieren oder Zurücksetzen von playerSelectedMaterial
        playerSelectedMaterial.remove(playerId);

        // Aktualisieren des Spielerinventars, falls notwendig
        player.updateInventory();
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
                    // Entfernen Sie alle Items des Typs, wenn der Wollblock angeklickt wurde
                    totalRemoved += amountInStack;
                    playerInv.clear(i);
                } else {
                    // Entfernen Sie Items bis zur festgelegten Grenze, wenn ein einzelnes Item geklickt wurde
                    if (amountInStack > amountToRemove - totalRemoved) {
                        item.setAmount(amountInStack - (amountToRemove - totalRemoved));
                        totalRemoved = amountToRemove;
                        break;
                    } else {
                        playerInv.clear(i);
                        totalRemoved += amountInStack;

                        if (totalRemoved >= amountToRemove) break;
                    }
                }
            }
        }

        return totalRemoved;
    }





    public void sendTradeConfirmationToDiscord(Player player, ItemStack itemStack, int amount) {
        if (itemStack == null || player == null) {
            return; // Sicherstellen, dass weder der Spieler noch der ItemStack null ist.
        }

        // Konvertiere das Material des ItemStacks in einen String für die Nachricht.
        String itemName = itemStack.getType().toString();

        // Erstelle die Nachricht, die an Discord gesendet werden soll.
        String message = player.getName() + " hat " + amount + "x " + itemName + " abgegeben!";

        // Hier würde Ihr DiscordBot die Nachricht an den entsprechenden Kanal senden.
        // Dies hängt von der Implementierung Ihres DiscordBots ab.
        discordBot.sendMessageToDiscord(message);
    }


    private ItemStack createNamedItem(Material material, String name) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(name);

            // Verzauberungen für Schwert und Rüstung hinzufügen
            if (material == Material.DIAMOND_SWORD) {
                meta.addEnchant(Enchantment.DAMAGE_ALL, 1, true); // Schärfe 1
            } else if (material == Material.IRON_HELMET || material == Material.IRON_CHESTPLATE ||
                    material == Material.IRON_LEGGINGS || material == Material.IRON_BOOTS) {
                // Beispiel für eine zufällige Verzauberung
                meta.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, 1, true);
            }

            item.setItemMeta(meta);
        }

        // Zusätzliche Eigenschaften für Tränke
        if (material == Material.POTION) {
            PotionMeta potionMeta = (PotionMeta) item.getItemMeta();
            if (potionMeta != null) {
                // Beispiel: Erstellen eines Heiltranks
                potionMeta.setBasePotionData(new PotionData(PotionType.INSTANT_HEAL));
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
        questInventory.setItem(0, createNamedItem(Material.CRYING_OBSIDIAN, "Geheimnisvoller Obsidian"));
        questInventory.setItem(1, createNamedItem(Material.DIAMOND_SWORD, "Legendäres Schwert (VZ)"));
        questInventory.setItem(2, createNamedItem(Material.IRON_HELMET, "Robuster Eisenhelm (VZ)"));
        questInventory.setItem(3, createNamedItem(Material.IRON_CHESTPLATE, "Starke Eisenbrustplatte (VZ)"));
        questInventory.setItem(4, createNamedItem(Material.IRON_LEGGINGS, "Feste Eisenbeinpanzer(VZ)"));
        questInventory.setItem(5, createNamedItem(Material.IRON_BOOTS, "Solide Eisenstiefel(VZ)"));
        questInventory.setItem(6, createNamedItem(Material.ENCHANTED_GOLDEN_APPLE, "Magischer Goldapfel"));
        questInventory.setItem(7, createNamedItem(Material.POTION, "Mystischer Trank - Das beste vom besten!")); // Dies wird später spezifiziert

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


            // Erstellen der Kapitelbeschreibungen in der Lore
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.RESET + "" + ChatColor.LIGHT_PURPLE + "Freaky" + ChatColor.LIGHT_PURPLE + "World" + ChatColor.DARK_PURPLE + " Season" + ChatColor.GOLD + " 3");
            lore.add(ChatColor.RESET + "" + ChatColor.GREEN + "Kapitel 1: " + ChatColor.GREEN + "" + ChatColor.UNDERLINE + "Portal des Schreckens");
            lore.add(ChatColor.RESET + "" + ChatColor.YELLOW + "" + "Kapitel 2: " + ChatColor.YELLOW + "Der Schatten der dunkelheit");
            lore.add(ChatColor.RESET + "" + ChatColor.DARK_GREEN + ""  + "Kapitel 3: " + ChatColor.GREEN  + "Flucht in eine neue Welt");
            lore.add(ChatColor.RESET + "" + ChatColor.DARK_GRAY + "" + ChatColor.STRIKETHROUGH + "Kapitel 4: " + ChatColor.DARK_GRAY + "" + ChatColor.STRIKETHROUGH + "Geheimnisse in FreakyWorld");
            lore.add(ChatColor.RESET + "" + ChatColor.DARK_GRAY + "" + ChatColor.STRIKETHROUGH + "Kapitel 5: " + ChatColor.DARK_GRAY + "" + ChatColor.STRIKETHROUGH + "????????");






            List<String> pages = new ArrayList<>();
            pages.add(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Season 3 - NEW ERA\n" + ChatColor.RESET +
                    ChatColor.DARK_GRAY + "Eine neue Welt? Das wurde ganz schoen hektisch da drueben...");
            pages.add(ChatColor.GOLD + "Was nun???\n" + ChatColor.DARK_GRAY +
                    ChatColor.RED + "- Sucht euch Unterschlupf!\n" + ChatColor.GOLD + "- Rettet unseren Spawn in der alten Welt!\n" + ChatColor.DARK_GREEN + "- Lasst uns erneut landen! \n");
            pages.add(ChatColor.RED + "Aktuelle Lage!\n\n" + ChatColor.DARK_PURPLE +
                    "Ihr solltet euch ein neues Eigenheim suchen! Wir hoffen, dass diese Welt sicherer ist...");
            pages.add(ChatColor.DARK_BLUE + "Die Alte Welt\n" + ChatColor.DARK_GREEN +
                    "Die alte Welt kann noch gerettet werden. Sichert unseren geliebten Spawn und macht ihn zu einem besonderen Ort!");
            pages.add(ChatColor.DARK_BLUE + "Wir Landen! Erneut...\n" + ChatColor.DARK_GRAY +
                    ChatColor.RED + "- Haltet euch am Schwarzen Brett auf dem Laufenden!\n" + ChatColor.GOLD + "- Wir brauchen weiterhin Ausruestung.\n" + ChatColor.DARK_GREEN + "- Bleibt wachsam. \n");
            pages.add(ChatColor.RED + "Wir versuchen unsere Alte Welt zurueckzuerlangen. ");
            pages.add(ChatColor.DARK_RED + "Aber nun ist jeder erneut auf sich alleingestellt.\n\n");
            bookMeta.setPages(pages);
            bookMeta.setLore(lore);
            book.setItemMeta(bookMeta);

        }

        return book;
    }




    private void openQuestBook(Player player) {
        ItemStack questBook = createQuestBook();
        player.openBook(questBook);
    }



    public void removeQuestVillager() {
        if (questVillager != null) {
            questVillager.remove();
            questVillager = null;
        }
    }

}



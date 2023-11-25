
package h34r7l3s.freakyworld;

import net.dv8tion.jda.api.entities.channel.attribute.IGuildChannelContainer;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.*;
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
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class QuestVillager implements Listener {
    private FreakyWorld plugin;
    private Location villagerLocation = new Location(Bukkit.getWorld("World"), 0, 213, -16);
    private Villager questVillager;
    private DiscordBot discordBot;
    private Set<UUID> hasInteracted = new HashSet<>();

    public QuestVillager(FreakyWorld plugin, DiscordBot discordBot) {
        this.plugin = plugin;
        this.discordBot = discordBot;
        // Additional setup...
    }
    private final Set<Material> validMaterials = new HashSet<>(Arrays.asList(
            Material.BREAD, Material.COBBLESTONE, Material.OBSIDIAN,
            Material.POTATO, Material.CARROT, Material.FLINT_AND_STEEL,
            Material.RAW_IRON, Material.IRON_INGOT
    ));



    public void spawnVillager() {
        questVillager = (Villager) Bukkit.getWorld("World").spawnEntity(villagerLocation, EntityType.VILLAGER);
        questVillager.setCustomName("Quest Master");
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
        if (entity instanceof Villager && entity.getCustomName().equals("Quest Master")) {
            openQuestGUI(event.getPlayer());
        }
    }

    // Diese Methode überprüft, ob ein Item in das Abgabeinventar gelegt werden darf.
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        Inventory clickedInventory = event.getClickedInventory();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null) return;

        String inventoryTitle = event.getView().getTitle();

        // Überprüfung für das "Quest Items" Menü
        if (inventoryTitle.equals("Quest Items")) {
            // Verhindern, dass Items aus dem Inventar genommen werden
            event.setCancelled(true);

            if (validMaterials.contains(clickedItem.getType())) {
                // Öffne das Abgabe Menü
                openTradingInterface(player);
            } else if (clickedItem.getType() == Material.WRITTEN_BOOK) {
                // Öffnen des Questbuchs
                openQuestBook(player);
            }
            return;
        }
        // Logik für das "Geben Sie Ihre Items ab" Menü
        if (inventoryTitle.equals("Geben Sie Ihre Items ab")) {
            if (event.getRawSlot() == 8 && clickedItem.getType() == Material.GREEN_WOOL) {
                // Abgabe bestätigen und Items verarbeiten

                handleItemSubmission(player);
                event.setCancelled(true);
            } else {
                // Erlaube das Verschieben von gültigen Materialien in das Abgabeinventar
                if (!validMaterials.contains(clickedItem.getType())) {
                    event.setCancelled(true);
                }
            }
        } else {
            // Verhindere die direkte Abgabe von Items aus dem Spielerinventar
            //event.setCancelled(true);
            //Ich denke hier ist die Vanilla Inventar Logik?
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
            meta.setDisplayName(ChatColor.GREEN + "Nimm dir alles was du finden kannst");
            item.setItemMeta(meta);
        }
        return item;
    }


    // Diese Methode handhabt die Abgabe von Items.
    // In QuestVillager Klasse
    private void handleItemSubmission(Player player) {
        System.out.println("handleItemSubmission aufgerufen für Spieler " + player.getName());

        Inventory playerInv = player.getInventory();
        Map<Material, Integer> itemCounts = new HashMap<>();

        for (ItemStack item : playerInv.getContents()) {
            if (item != null && validMaterials.contains(item.getType())) {
                // Ignoriere den grünen Wollblock bei der Abgabe
                if (item.getType() == Material.GREEN_WOOL) {
                    continue;
                }

                int count = itemCounts.getOrDefault(item.getType(), 0);
                itemCounts.put(item.getType(), count + item.getAmount());
                System.out.println("Spieler " + player.getName() + " hat " + item.getAmount() + "x " + item.getType() + " abgegeben");

                removeItemsFromPlayer(player, item.getType(), item.getAmount());
            }
        }

        // Sende Discord Nachrichten für jedes abgegebene Item
        itemCounts.forEach((material, count) -> {
            ItemStack itemStack = new ItemStack(material, count);
            sendTradeConfirmationToDiscord(player, itemStack, count);
        });
    }



    private void removeItemsFromPlayer(Player player, Material material, int amountToRemove) {
        Inventory playerInv = player.getInventory();
        for (ItemStack item : playerInv.getContents()) {
            if (item != null && item.getType() == material) {
                int amountInStack = item.getAmount();

                if (amountInStack > amountToRemove) {
                    item.setAmount(amountInStack - amountToRemove);
                    return; // Genügend Items entfernt
                } else {
                    playerInv.remove(item);
                    amountToRemove -= amountInStack;
                    if (amountToRemove == 0) return; // Alle benötigten Items entfernt
                }
            }
        }
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
            item.setItemMeta(meta);
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
                    handleItemSubmission(player);
                    player.closeInventory();
                } else {
                    // Wenn nicht der grüne Wollblock angeklickt wurde, führe die übliche Logik aus
                    if (clickedItem != null && validMaterials.contains(clickedItem.getType())) {
                        int amountToTrade = clickedItem.getAmount();
                        removeItemsFromPlayer(player, clickedItem.getType(), amountToTrade);
                        player.closeInventory();
                        player.sendMessage(ChatColor.GREEN + "Sie haben " + amountToTrade + "x " + clickedItem.getType().toString() + " abgegeben!");
                        sendTradeConfirmationToDiscord(player, clickedItem, amountToTrade);
                    } else {
                        // Nachricht an den Spieler, wenn auf einen ungültigen oder leeren Slot geklickt wurde
                        player.sendMessage(ChatColor.RED + "Ungültiges Item angeklickt.");
                    }
                }
            }
        }







    private void openQuestGUI(Player player) {
        Inventory questInventory = Bukkit.createInventory(null, 9, "Quest Items");

        // Füge die benannten Items zum Inventar hinzu
        questInventory.setItem(0, createNamedItem(Material.BREAD, "Wir alle sind hungrig, Essen sollten wir also genug da haben!"));
        questInventory.setItem(1, createNamedItem(Material.COBBLESTONE, "Steine! Wer weiss welche Bauwerke wir errichten muessen."));
        questInventory.setItem(2, createNamedItem(Material.OBSIDIAN, "Mystische Steine? Wir haben keine Ahnung, was diese Welt fuer uns bereit haelt.."));
        questInventory.setItem(3, createNamedItem(Material.POTATO, "Kartoffeln! Alle lieben Kartoffeln!"));
        questInventory.setItem(4, createNamedItem(Material.CARROT, "Karotten, wichtig! Ansonsten haben wir vielleicht ein Problem.."));
        questInventory.setItem(5, createNamedItem(Material.FLINT_AND_STEEL, "Feuer! Wir wollens warm!"));
        questInventory.setItem(6, createNamedItem(Material.RAW_IRON, "Roheisenerz! Der Schmied verlangt danach!"));
        questInventory.setItem(7, createNamedItem(Material.IRON_INGOT, "Eisen! Der Schmied verlangt danach!"));
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
            lore.add(ChatColor.RESET + "" + ChatColor.RED + "Kapitel 1: " + ChatColor.GREEN + "" + ChatColor.UNDERLINE + "Portal des Schreckens");
            lore.add(ChatColor.RESET + "" + ChatColor.DARK_GRAY + "" + ChatColor.STRIKETHROUGH + "Kapitel 2: " + ChatColor.DARK_GRAY + "" + ChatColor.STRIKETHROUGH + "Der Schatten der dunkelheit");
            lore.add(ChatColor.RESET + "" + ChatColor.DARK_GRAY + "" + ChatColor.STRIKETHROUGH + "Kapitel 3: " + ChatColor.DARK_GRAY + "" + ChatColor.STRIKETHROUGH + "Geheimnisse in FreakyWorld");






            List<String> pages = new ArrayList<>();
            pages.add(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Season 3 - NEW ERA\n\n" + ChatColor.RESET +
                    ChatColor.DARK_GRAY + "Deine Reise beginnt, als unser Luftschiff in dieser mysterioesen Welt ankommt. Hier erwartet dich eine wichtige Aufgabe, und die Zukunft dieser Welt liegt in deinen Haenden.");
            pages.add(ChatColor.BLUE + "Eure Aufgabe\n" + ChatColor.DARK_GRAY +
                    "\n\n" +
                    ChatColor.RED + "- ein Quartier unterhalb des Luftschiffs errichten\n" + ChatColor.GOLD + "- den ankommenden Dorfbewohnern Schutz bieten.\n" + ChatColor.DARK_PURPLE + "- schafft euch eine eigene sichere Unterkunft ausserhalb des Hauptquartiers!\n" + ChatColor.DARK_GRAY + "... und noch so viel mehr...");
            pages.add(ChatColor.LIGHT_PURPLE + "Doch Vorsicht!\n\n" + ChatColor.DARK_GRAY +
                    "In der Wildnis seid ihr auf euch allein gestellt. Ausserhalb des Hauptlagers herrscht Unsicherheit, und ihr muesst selbst entscheiden, wem ihr vertraut.");
            pages.add(ChatColor.DARK_BLUE + "Das Abenteuer wartet!\n\n" + ChatColor.DARK_GRAY +
                    "Sei mutig, Abenteurer. Viel Glueck auf deiner Reise ins Unbekannte. Wenn wir genug von den Items haben und das Hauptquartier steht, werden wir die naechste Welt aufsuchen. Weisst du hier vielleicht schon mehr und kannst uns helfen?");

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



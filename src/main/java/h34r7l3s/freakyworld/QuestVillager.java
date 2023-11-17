
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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class QuestVillager implements Listener {
    private FreakyWorld plugin;
    private Location villagerLocation = new Location(Bukkit.getWorld("World"), -257, 86, 1851);
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
            if (validMaterials.contains(clickedItem.getType())) {
                // Verhindern, dass die Items aus dem Quest-Menü gezogen werden
                event.setCancelled(true);
                // Öffne das Abgabe Menü
                openTradingInterface(player);
            }
            return;
        }

        // Logik für das "Geben Sie Ihre Items ab" Menü
        if (inventoryTitle.equals("Geben Sie Ihre Items ab")) {
            if (event.getRawSlot() == 8 && clickedItem.getType() == Material.GREEN_WOOL) {
                // Abgabe bestätigen und Items verarbeiten

                handleItemSubmission(player, event.getInventory());
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
        }
    }





    private void openTradingInterface(Player player) {
        Inventory tradeInventory = Bukkit.createInventory(null, 9, "Geben Sie Ihre Items ab");
        // Füge einen grünen Wolle-Knopf hinzu, der als 'Abgeben' dient
        tradeInventory.setItem(8, new ItemStack(Material.GREEN_WOOL, 1));
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


    // Diese Methode handhabt die Abgabe von Items.
    // In QuestVillager Klasse
    private void handleItemSubmission(Player player, Inventory inv) {
        System.out.println("handleItemSubmission aufgerufen für Spieler " + player.getName());

        Map<Material, Integer> itemCounts = new HashMap<>();

        for (int i = 0; i < inv.getSize() - 1; i++) {
            ItemStack itemStack = inv.getItem(i);
            System.out.println("Schleife: Index " + i + ", ItemStack: " + (itemStack != null ? itemStack.getType() : "null"));

            if (itemStack != null && validMaterials.contains(itemStack.getType())) {
                int count = itemCounts.getOrDefault(itemStack.getType(), 0);
                itemCounts.put(itemStack.getType(), count + itemStack.getAmount());
                System.out.println("Entferne " + itemStack.getAmount() + "x " + itemStack.getType() + " vom Spieler " + player.getName());
                // Entferne das Item aus dem Inventar
                inv.clear(i);

                // Entferne die entsprechende Menge des Items aus dem Spielerinventar
                // Innerhalb von handleItemSubmission, vor dem Aufruf von removeItemsFromPlayer
                System.out.println("Entferne " + itemStack.getAmount() + "x " + itemStack.getType() + " vom Spieler " + player.getName());
                removeItemsFromPlayer(player, itemStack.getType(), itemStack.getAmount());

            }
        }
        // Rest des Codes...
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
            event.setCancelled(true); // Verhindern Sie, dass der Spieler die Vorlage nimmt

            ItemStack clickedItem = event.getCurrentItem();
            Player player = (Player) event.getWhoClicked();

            if (clickedItem != null && clickedItem.getAmount() > 0) {
                // Der Spieler hat auf das Item zum Abgeben geklickt
                int amountToTrade = clickedItem.getAmount();

                // Entfernen Sie das Item aus dem Inventar des Spielers (hier müssen Sie die Logik implementieren)
                // ...

                // Bestätigen Sie den Handel und schließen Sie das Inventar
                player.closeInventory();
                player.sendMessage(ChatColor.GREEN + "Sie haben " + amountToTrade + "x " + clickedItem.getType().toString() + " abgegeben!");

                // Senden Sie eine Nachricht an Discord
                sendTradeConfirmationToDiscord(player, clickedItem, amountToTrade);
            }
        }
    }





    private void openQuestGUI(Player player) {
        Inventory questInventory = Bukkit.createInventory(null, 9, "Quest Items");

        // Füge die benannten Items zum Inventar hinzu
        questInventory.setItem(0, createNamedItem(Material.BREAD, "Magisches Brot"));
        questInventory.setItem(1, createNamedItem(Material.COBBLESTONE, "Kobbelstein der Weisheit"));
        questInventory.setItem(2, createNamedItem(Material.OBSIDIAN, "Obsidian der Macht"));
        questInventory.setItem(3, createNamedItem(Material.POTATO, "Mystische Kartoffel"));
        questInventory.setItem(4, createNamedItem(Material.CARROT, "Zauberhafte Karotte"));
        questInventory.setItem(5, createNamedItem(Material.FLINT_AND_STEEL, "Feuerstarter"));
        questInventory.setItem(6, createNamedItem(Material.RAW_IRON, "Roheisenerz"));
        questInventory.setItem(7, createNamedItem(Material.IRON_INGOT, "Eiseningot"));
        questInventory.setItem(8, createNamedItem(Material.WRITABLE_BOOK, "Schreibbuch der Quests"));

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






    public void removeQuestVillager() {
        if (questVillager != null) {
            questVillager.remove();
            questVillager = null;
        }
    }

}



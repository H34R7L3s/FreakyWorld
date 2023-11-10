
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
        Inventory inv = event.getClickedInventory();
        ItemStack clickedItem = event.getCurrentItem();
        Inventory actionInv = event.getInventory(); // Das Inventar, in dem die Aktion stattfindet

        if (clickedItem == null) return;

        if (event.getView().getTitle().equals("Geben Sie Ihre Items ab")) {
            if (event.getRawSlot() == 8 && clickedItem.getType() == Material.GREEN_WOOL) {
                // Abgabe bestätigen und Items verarbeiten
                event.setCancelled(true);
                handleItemSubmission(player, actionInv);
            } else if (!event.getInventory().equals(player.getInventory()) && !validMaterials.contains(clickedItem.getType())) {
                // Verhindern, dass ungültige Items in das Abgabeinventar gelegt werden
                event.setCancelled(true);
            }
            // Verhindern, dass der grüne Wolle-Knopf gezogen wird
            if (event.getSlot() == 8) {
                event.setCancelled(true);
            }
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
        if (event.getView().getTitle().equals("Quest Items")) {
            event.setCancelled(true);
        }
    }

    // Diese Methode handhabt die Abgabe von Items.
    // In QuestVillager Klasse
    private void handleItemSubmission(Player player, Inventory inv) {
        Map<Material, Integer> itemCounts = new HashMap<>();

        for (int i = 0; i < inv.getSize() - 1; i++) {
            ItemStack itemStack = inv.getItem(i);
            if (itemStack != null && validMaterials.contains(itemStack.getType())) {
                int count = itemCounts.getOrDefault(itemStack.getType(), 0);
                itemCounts.put(itemStack.getType(), count + itemStack.getAmount());
                // Entferne das Item aus dem Inventar
                inv.clear(i);
            }
        }

        int totalSubmitted = itemCounts.values().stream().mapToInt(Integer::intValue).sum();
        player.sendMessage(ChatColor.GREEN + "Du hast insgesamt " + totalSubmitted + " Items abgegeben!");

        // Senden Sie die Gesamtnachricht an Discord
        sendTotalTradeConfirmationToDiscord(player, itemCounts);

        // Schließe das Inventar nach der Verarbeitung
        player.closeInventory();
    }
    public void sendTotalTradeConfirmationToDiscord(Player player, Map<Material, Integer> itemCounts) {
        StringBuilder message = new StringBuilder(player.getName() + " hat folgende Items abgegeben:");
        itemCounts.forEach((material, count) -> {
            message.append("\n").append(count).append("x ").append(material.toString());
        });

        // Hier würde Ihr DiscordBot die Nachricht an den entsprechenden Kanal senden.
        discordBot.sendMessageToDiscord(message.toString());
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

        // Füge die Items zum Inventar hinzu
        questInventory.setItem(0, new ItemStack(Material.BREAD, 1));
        questInventory.setItem(1, new ItemStack(Material.COBBLESTONE, 1));
        questInventory.setItem(2, new ItemStack(Material.OBSIDIAN, 1));
        questInventory.setItem(3, new ItemStack(Material.POTATO, 1));
        questInventory.setItem(4, new ItemStack(Material.CARROT, 1));
        questInventory.setItem(5, new ItemStack(Material.FLINT_AND_STEEL, 1));
        questInventory.setItem(6, new ItemStack(Material.RAW_IRON, 1));
        questInventory.setItem(7, new ItemStack(Material.IRON_INGOT, 1));
        questInventory.setItem(8, new ItemStack(Material.WRITABLE_BOOK, 1));

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



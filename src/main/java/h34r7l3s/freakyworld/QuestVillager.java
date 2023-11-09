
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

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // ...

        if ("Geben Sie Ihre Items ab".equals(event.getView().getTitle())) {
            ItemStack clickedItem = event.getCurrentItem();
            Player player = (Player) event.getWhoClicked();

            if (clickedItem != null && clickedItem.getType() == Material.GREEN_WOOL) {
                // Der Spieler hat auf den 'Abgeben'-Knopf geklickt
                event.setCancelled(true); // Verhindern, dass der Wolle-Knopf genommen wird
                handleItemSubmission(player, event.getInventory());
            } else if (clickedItem != null && !validMaterials.contains(clickedItem.getType())) {
                // Wenn das Item nicht zur Abgabe vorgesehen ist, verhindere das Platzieren
                event.setCancelled(true);
            }
        }
    }

    private void handleItemSubmission(Player player, Inventory inv) {
        int totalAmount = 0;
        // Gehen Sie durch das Inventar und zählen Sie die gültigen Items
        for (int i = 0; i < inv.getSize() - 1; i++) { // Ignoriere den letzten Slot mit dem 'Abgeben'-Knopf
            ItemStack item = inv.getItem(i);
            if (item != null && validMaterials.contains(item.getType())) {
                totalAmount += item.getAmount();
                // Entferne das Item aus dem Inventar
                inv.setItem(i, null);
            }
        }

        if (totalAmount > 0) {
            player.sendMessage(ChatColor.GREEN + "Du hast " + totalAmount + " Items abgegeben!");
            // Hier sollten Sie die tatsächlichen Items und ihre Mengen speichern und an Discord senden
            sendTradeConfirmationToDiscord(player, totalAmount);
        }
        player.closeInventory();
    }

    public void sendTradeConfirmationToDiscord(Player player, int totalAmount) {
        String message = player.getName() + " hat insgesamt " + totalAmount + " Items abgegeben!";
        discordBot.sendMessageToDiscord(message);
    }


    private void openTradingInterface(Player player) {
        Inventory tradeInventory = Bukkit.createInventory(null, 9, "Geben Sie Ihre Items ab");
        // Füge einen grünen Wolle-Knopf hinzu, der als 'Abgeben' dient
        tradeInventory.setItem(8, new ItemStack(Material.GREEN_WOOL, 1));
        player.openInventory(tradeInventory);
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


    // Beispiel für das Senden einer Nachricht an Discord
    public void sendTradeConfirmationToDiscord(Player player, ItemStack item, int amount) {
        String message = player.getName() + " hat " + amount + "x " + item.getType().toString() + " abgegeben!";
        discordBot.sendMessageToDiscord(message);
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
            int totalAmount = 0;
            // Gehen Sie durch das Inventar und zählen Sie nur die vom Spieler platzierten Items
            for (ItemStack item : inv.getContents()) {
                if (item != null && item.hasItemMeta() && item.getItemMeta().hasLore()) {
                    // Beispiel, um zu überprüfen, ob es ein abgegebenes Item ist
                    if (item.getItemMeta().getLore().contains("Abgegeben")) {
                        totalAmount += item.getAmount();
                    }
                }
            }
            if (totalAmount > 0) {
                Player player = (Player) event.getPlayer();
                player.sendMessage(ChatColor.GREEN + "Du hast " + totalAmount + " Items abgegeben!");
                sendTradeConfirmationToDiscord(player, Material.COBBLESTONE, totalAmount); // Material anpassen
            }
        }
    }



    public void sendTradeConfirmationToDiscord(Player player, Material item, int amount) {
        String message = player.getName() + " hat " + amount + "x " + item.toString() + " abgegeben!";
        discordBot.sendMessageToDiscord(message);
    }


    public void removeQuestVillager() {
        if (questVillager != null) {
            questVillager.remove();
            questVillager = null;
        }
    }

}



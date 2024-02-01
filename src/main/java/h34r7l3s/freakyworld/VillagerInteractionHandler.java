package h34r7l3s.freakyworld;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class VillagerInteractionHandler implements Listener {

    private final FreakyWorld plugin;
    private final CategoryManager categoryManager;
    private final EventLogic eventLogic;
    private final CategoryTaskHandler categoryTaskHandler;

    public VillagerInteractionHandler(FreakyWorld plugin, CategoryManager categoryManager, EventLogic eventLogic) {
        this.plugin = plugin;
        this.categoryManager = categoryManager;
        this.eventLogic = eventLogic;
        this.categoryTaskHandler = plugin.getCategoryTaskHandler(); // Initialize categoryTaskHandler
    }

    @EventHandler
    public void onPlayerInteractWithVillager(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager)) {
            return;
        }

        Villager villager = (Villager) event.getRightClicked();
        if (villager.getCustomName() != null && villager.getCustomName().equals("Quest Villager")) {
            Player player = event.getPlayer();
            openVillagerGUI(player);
        }
    }


    private void openVillagerGUI(Player player) {
        Inventory gui = Bukkit.createInventory(player, 27, "Quest Villager");

        // Schöner Rahmen aus Glasscheiben
        ItemStack glassPane = new ItemStack(Material.GLASS_PANE);
        ItemMeta glassMeta = glassPane.getItemMeta();
        glassMeta.setDisplayName(" ");
        glassPane.setItemMeta(glassMeta);

        for (int i = 0; i < 9; i++) {
            gui.setItem(i, glassPane); // Obere Reihe
            gui.setItem(i + 18, glassPane); // Untere Reihe
        }

        for (int i = 1; i < 3; i++) {
            gui.setItem(i * 9, glassPane); // Linke Spalte
            gui.setItem(i * 9 + 8, glassPane); // Rechte Spalte
        }

        // Ermitteln Sie die aktuelle Kategorie aus dem CategoryManager
        String currentCategory = plugin.getVillagerCategoryManager().getCurrentCategory();

        // Kategoriebeschreibung aus dem CategoryManager
        String categoryDescription = getCategoryItemName(currentCategory); // Verwenden Sie getCategoryItemName

        ItemStack categoryItem = new ItemStack(Material.DIAMOND_PICKAXE); // Beispiel: Pickaxe für Steinsammeln
        ItemMeta categoryMeta = categoryItem.getItemMeta();
        categoryMeta.setDisplayName(categoryDescription);
        categoryItem.setItemMeta(categoryMeta);

        // Führenden Spielerkopf aus dem aktualisierten getPlayerHeadOfLeadingPlayer verwenden
        ItemStack leadingPlayerHead = getPlayerHeadOfLeadingPlayer(currentCategory);
        ItemMeta headMeta = leadingPlayerHead.getItemMeta();
        headMeta.setDisplayName("Führender Spieler");
        leadingPlayerHead.setItemMeta(headMeta);

        // Legen Sie die Elemente in die GUI fest
        gui.setItem(10, categoryItem); // Kategoriebeschreibung
        gui.setItem(13, leadingPlayerHead); // Führender Spielerkopf

        // Aufgaben-Items in die GUI einfügen
        int taskSlot = 19; // Beginnen Sie ab Slot 19 für Aufgaben-Items

        for (String task : categoryManager.getTasksForCategory(currentCategory)) {
            // Hier können Sie die Aufgaben-Items erstellen und anpassen
            ItemStack taskItem = createTaskItem(task, currentCategory);
            gui.setItem(taskSlot, taskItem);
            taskSlot++;
        }

        // Abgabe-Knopf hinzufügen
        ItemStack submitButton = new ItemStack(Material.GREEN_DYE); // Beispiel: Verwenden Sie grüne Farbe für den Abgabe-Knopf
        ItemMeta submitButtonMeta = submitButton.getItemMeta();
        submitButtonMeta.setDisplayName("Abgeben");
        submitButton.setItemMeta(submitButtonMeta);
        gui.setItem(26, submitButton); // Abgabe-Knopf

        // GUI anzeigen
        player.openInventory(gui);
    }





    private ItemStack createTaskItem(String taskDescription, String category) {
        ItemStack taskItem = new ItemStack(Material.BOOK); // Beispiel: Verwenden Sie ein Buch als Aufgaben-Item
        ItemMeta taskMeta = taskItem.getItemMeta();
        taskMeta.setDisplayName(taskDescription);

        // Lore für die Aufgabe hinzufügen
        List<String> lore = new ArrayList<>();
        lore.add("Sammle so viele " + getCategoryItemName(category) + " wie möglich,");
        lore.add("um Glorreichen Reichtum zu erlangen.");
        taskMeta.setLore(lore);

        taskItem.setItemMeta(taskMeta);
        return taskItem;
    }

    private String getCategoryItemName(String category) {
        List<String> items = categoryManager.getTasksForCategory(category);

        if (!items.isEmpty()) {
            return items.get(0); // Nehmen Sie das erste Item in der Kategorie als Namen
        }

        return "Unbekanntes Item"; // Fallback, falls keine Items in der Kategorie vorhanden sind
    }



    private void handleItemSubmission(Player player, ItemStack item, String category) {
        // Führen Sie die Abgabe-Logik hier durch
        // Dies könnte das Entfernen des abgegebenen Items und das Aktualisieren von Punkten usw. umfassen
        // Verwenden Sie Ihren vorhandenen Code für die Abgabe-Logik
        categoryTaskHandler.handleItemSubmission(player, item, category);
    }


    private ItemStack getPlayerHeadOfLeadingPlayer(String category) {
        // Erstellen Sie eine Map zur Speicherung der führenden Spieler für jede Kategorie
        Map<String, UUID> leadingPlayers = new HashMap<>();

        // Schleife durch alle Kategorien und ermitteln Sie die führenden Spieler
        for (String categoryName : categoryManager.getCategories()) {
            UUID leadingPlayerUUID = eventLogic.getLeadingPlayerForCategory(categoryName);

            if (leadingPlayerUUID != null) {
                leadingPlayers.put(categoryName, leadingPlayerUUID);
            }
        }

        // Überprüfen Sie, ob die angegebene Kategorie einen führenden Spieler hat
        if (leadingPlayers.containsKey(category)) {
            UUID leadingPlayerUUID = leadingPlayers.get(category);
            Player leadingPlayer = Bukkit.getPlayer(leadingPlayerUUID);

            if (leadingPlayer != null) {
                ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD, 1);
                SkullMeta meta = (SkullMeta) playerHead.getItemMeta();
                meta.setOwningPlayer(leadingPlayer);
                playerHead.setItemMeta(meta);
                return playerHead;
            }
        }

        // Wenn keine führenden Spieler für die Kategorie gefunden wurden, verwenden Sie ein Standard-Item
        ItemStack defaultHead = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta meta = (SkullMeta) defaultHead.getItemMeta();
        meta.setDisplayName("X");
        defaultHead.setItemMeta(meta);
        return defaultHead;
    }


    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) return;

        Inventory inventory = event.getClickedInventory();
        if (inventory.getHolder() instanceof Villager) {
            Villager villager = (Villager) inventory.getHolder();
            if ("Quest Villager".equals(villager.getCustomName())) {
                event.setCancelled(true);
                event.setResult(Event.Result.DENY);

                Player player = (Player) event.getWhoClicked();
                ItemStack clickedItem = event.getCurrentItem();
                if (clickedItem != null && event.getSlotType() != InventoryType.SlotType.OUTSIDE) {
                    String currentCategory = plugin.getVillagerCategoryManager().getCurrentCategory();

                    if (event.getSlot() == 26) {
                        // Der Spieler hat auf den Abgabe-Knopf geklickt
                        ItemStack submitButton = new ItemStack(Material.GREEN_DYE); // Das Abgabe-Item
                        ItemMeta submitButtonMeta = submitButton.getItemMeta();
                        submitButtonMeta.setDisplayName("Abgeben");
                        submitButton.setItemMeta(submitButtonMeta);

                        // Überprüfen, ob der Spieler das Abgabe-Item in der Hand hält
                        if (player.getInventory().getItemInMainHand().isSimilar(submitButton)) {
                            handleItemSubmission(player, null, currentCategory);
                        } else {
                            player.sendMessage("Du musst das Abgabe-Item in der Hand halten.");
                        }
                    } else {
                        // Der Spieler hat auf ein Aufgaben-Item geklickt
                        for (String task : categoryManager.getTasksForCategory(currentCategory)) {
                            ItemStack taskItem = createTaskItem(task, currentCategory);
                            if (taskItem.isSimilar(clickedItem)) {
                                handleItemSubmission(player, clickedItem, currentCategory);
                                break;
                            }
                        }
                    }
                }
            }
        }
    }


    private boolean shouldPlayerSubmitItem(Player player, ItemStack item, String category) {
        // Hier können Sie die Logik hinzufügen, um zu überprüfen, ob der Spieler das Item abgeben sollte.
        // Zum Beispiel könnten Sie überprüfen, ob das Item eine gültige Aufgabe in der aktuellen Kategorie ist.
        // Sie können dies mithilfe Ihrer CategoryManager-Klasse tun.

        List<String> tasksForCategory = categoryManager.getTasksForCategory(category);

        if (tasksForCategory.contains(item.getType().toString())) {
            return true; // Das Item ist für die Abgabe geeignet
        }

        return false; // Das Item ist nicht für die Abgabe geeignet
    }

}

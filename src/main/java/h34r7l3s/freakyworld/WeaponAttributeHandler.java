package h34r7l3s.freakyworld;
// Importe
import io.th0rgal.oraxen.api.OraxenItems;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class WeaponAttributeHandler implements Listener {

    private final FreakyWorld plugin;
    private final WeaponAttributeManager attributeManager;

    public WeaponAttributeHandler(FreakyWorld plugin, WeaponAttributeManager attributeManager) {
        this.plugin = plugin;
        this.attributeManager = attributeManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getClickedInventory() instanceof AnvilInventory)) return;

        Player player = (Player) event.getWhoClicked();
        AnvilInventory anvil = (AnvilInventory) event.getClickedInventory();

        // Debugging: Zeigen Sie Informationen über die Items im Anvil an
        ItemStack leftItem = anvil.getItem(0);
        ItemStack rightItem = anvil.getItem(1);
        ItemStack resultItem = anvil.getItem(2);

        Bukkit.getLogger().info("=== Anvil Debug ===");
        Bukkit.getLogger().info("Left Item: " + (leftItem != null ? leftItem.toString() : "null"));
        Bukkit.getLogger().info("Right Item: " + (rightItem != null ? rightItem.toString() : "null"));
        Bukkit.getLogger().info("Result Item: " + (resultItem != null ? resultItem.toString() : "null"));
        Bukkit.getLogger().info("===================");

        if (event.getSlotType() != InventoryType.SlotType.RESULT || event.getSlot() != 2) return;
        Bukkit.getLogger().info("Slot!");
        // Überprüfen, ob das linke und rechte Item das gewünschte Rezept ergeben
        if (!isCustomRecipe(leftItem, rightItem)) return;

        // Debugging: Zeigen Sie an, dass auf den Ergebnisslot geklickt wurde
        Bukkit.getLogger().info("Ergebnisslot wurde geklickt!");

        UUID uniqueId = UUID.randomUUID(); // Erzeugen Sie eine eindeutige UUID für das aktualisierte Item
        ItemStack updatedResultItem = createUpdatedResultItem(leftItem, rightItem, uniqueId);

        anvil.setItem(2, updatedResultItem);

        if (isAttributeItem(rightItem)) {
            String attributeType = getAttributeType(rightItem);
            double attributeValue = getAttributeValue(rightItem);

            // Hier überprüfen Sie, ob uniqueId eine gültige UUID ist
            if (isValidUUID(uniqueId.toString())) {
                // Do something with uniqueId if needed
            } else {
                // Wenn uniqueId keine gültige UUID ist, können Sie eine Fehlermeldung ausgeben oder andere Maßnahmen ergreifen.
                Bukkit.getLogger().warning("Ungültige UUID erstellt: " + uniqueId.toString());
            }
        }

        player.updateInventory();
        attributeManager.setOriginalItem(player, leftItem);

        // Öffnen Sie das Bestätigungs-Menü, wenn das aktualisierte Item angeklickt wird
        openConfirmationMenu(player, updatedResultItem);
    }



    // Methode zum Überprüfen, ob eine Zeichenfolge eine gültige UUID ist
    private boolean isValidUUID(String uuidString) {
        try {
            UUID.fromString(uuidString);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void openConfirmationMenu(Player player, ItemStack updatedItem) {
        Inventory confirmMenu = Bukkit.createInventory(null, 27, ChatColor.YELLOW + "Item Bestätigung");

        // Füge das aktualisierte Item dem Bestätigungs-Inventar hinzu
        confirmMenu.setItem(13, updatedItem);

        // Füge Bestätigungs- und Abbrechen-Optionen hinzu
        ItemStack confirmOption = createConfirmationOption();
        ItemStack cancelOption = createCancelOption();

        confirmMenu.setItem(21, confirmOption);
        confirmMenu.setItem(23, cancelOption);
        // Leere das Anvil-Inventar
        AnvilInventory anvilInventory = (AnvilInventory) player.getOpenInventory().getTopInventory();
        anvilInventory.clear();
// Setzen Sie das temporäre Item im Attributmanager
        attributeManager.setTemporaryItem(player, updatedItem);
        // Öffne das Bestätigungs-Menü für den Spieler
        player.openInventory(confirmMenu);

        // Speichere das aktualisierte Item im temporären Speicher, um es später zu verwenden
        // Hier können Sie eine Map<Player, ItemStack> verwenden, um die Zuordnung zwischen dem Spieler und dem Item zu speichern


    }
    @EventHandler
    public void onConfirmationMenuClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals(ChatColor.YELLOW + "Item Bestätigung")) {
            if (event.getSlotType() == InventoryType.SlotType.CONTAINER) {
                Player player = (Player) event.getWhoClicked();
                ItemStack updatedItem = attributeManager.getTemporaryItem(player);
                ItemStack originalItem = attributeManager.getOriginalItem(player);
                ItemStack attributeItem = attributeManager.getAttributeItem(player);

                if (event.getSlot() == 21) { // Bestätigen
                    applyConfirmation(player, originalItem, updatedItem);
                    player.closeInventory();
                } else if (event.getSlot() == 13) { // Item Slot
                    event.setCancelled(true); // Verhindern, dass das Item entnommen wird
                } else if (event.getSlot() == 23) { // Abbrechen
                    giveItemBackToPlayer(player, originalItem);
                    giveItemBackToPlayer(player, attributeItem);
                    player.closeInventory();
                }
            }
        }
    }



    private boolean playerHasItem(Player player, ItemStack itemToCheck) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.isSimilar(itemToCheck)) {
                return true;
            }
        }
        return false;
    }



    private ItemStack createConfirmationOption() {
        ItemStack confirmOption = new ItemStack(Material.GREEN_WOOL);
        ItemMeta meta = confirmOption.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "Bestätigen");
        confirmOption.setItemMeta(meta);
        return confirmOption;
    }

    // Erstellen Sie die Abbruch-Option (z.B. ein rotes Wolle-Item)
    private ItemStack createCancelOption() {
        ItemStack cancelOption = new ItemStack(Material.RED_WOOL);
        ItemMeta meta = cancelOption.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "Abbrechen");
        cancelOption.setItemMeta(meta);
        return cancelOption;
    }


    private String getAttributeType(ItemStack attributeItem) {
        String itemId = OraxenItems.getIdByItem(attributeItem);
        switch (itemId) {
            case "damage_hades":
                return "mehr_Schaden";
            case "statischer_blitz":
                return "statischer_Blitz";
            case "verlangsamen_einfrieren":
                return "verlangsamen";
            default:
                return "";
        }
    }

    private double getAttributeValue(ItemStack attributeItem) {
        String itemId = OraxenItems.getIdByItem(attributeItem);
        switch (itemId) {
            case "damage_hades":
                return 10.0; // Beispielwert für mehr Schaden
            case "statischer_blitz":
                return 0.2; // Beispielwert für Chance auf statischen Blitz
            case "verlangsamen_einfrieren":
                return 5.0; // Beispielwert für Verlangsamungsdauer
            default:
                return 0.0;
        }
    }


    private boolean isAttributeItem(ItemStack item) {
        if (item == null) {
            Bukkit.getLogger().info("[isAttributeItem] Das Item ist null.");
            return false;
        }

        String itemId = OraxenItems.getIdByItem(item);
        Bukkit.getLogger().info("[isAttributeItem] Item ID: " + itemId);

        return "damage_hades".equals(itemId) || "statischer_blitz".equals(itemId) || "verlangsamen_einfrieren".equals(itemId);
    }

    private boolean isCustomRecipe(ItemStack leftItem, ItemStack rightItem) {
        // Überprüfen, ob die Items null sind
        if (leftItem == null || rightItem == null) {
            return false;
        }
        // Überprüfen, ob das linke Item ein Schwert ist
        boolean isLeftItemSword = leftItem.getType().toString().endsWith("_SWORD") || "sword".equals(OraxenItems.getIdByItem(leftItem));

        // Überprüfen, ob das rechte Item eines der Attribute-Items ist
        boolean isRightItemAttribute = isAttributeItem(rightItem);

        Bukkit.getLogger().info("isCustomRecipe - Left Item: " + leftItem.getType() + " / " + OraxenItems.getIdByItem(leftItem));
        Bukkit.getLogger().info("isCustomRecipe - Right Item: " + rightItem.getType() + " / " + OraxenItems.getIdByItem(rightItem));
        Bukkit.getLogger().info("isCustomRecipe - Is Left Item Sword: " + isLeftItemSword);
        Bukkit.getLogger().info("isCustomRecipe - Is Right Item Attribute: " + isRightItemAttribute);

        return isLeftItemSword && isRightItemAttribute;
    }

    private ItemStack createUpdatedResultItem(ItemStack baseItem, ItemStack attributeItem, UUID uniqueId) {
        // Klonen des Basis-Items
        ItemStack resultItem = baseItem.clone();
        ItemMeta resultMeta = resultItem.getItemMeta();

        if (resultMeta == null) {
            Bukkit.getLogger().warning("ItemMeta des Ergebnis-Items ist null!");
            return null;
        }

        // Holen der aktuellen Attribute des Basis-Items
        Map<String, AttributeData> currentAttributes = attributeManager.getAttributes(OraxenItems.getIdByItem(baseItem));
        String attributeItemId = OraxenItems.getIdByItem(attributeItem);
        double newValue = getAttributeValue(attributeItem);

        // Aktualisieren oder Hinzufügen des Attributs
        currentAttributes.merge(attributeItemId, new AttributeData(newValue, 1),
                (oldValue, value) -> new AttributeData(oldValue.getValue() + value.getValue(), oldValue.getCount() + 1));

        // Aktualisieren der Lore
        updateResultItemLore(resultMeta, currentAttributes);

        // Speichern der UUID
        resultMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "uniqueId"), PersistentDataType.STRING, uniqueId.toString());
        resultItem.setItemMeta(resultMeta);

        return resultItem;
    }

    private void updateResultItemLore(ItemMeta meta, Map<String, AttributeData> attributes) {
        List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
        for (Map.Entry<String, AttributeData> entry : attributes.entrySet()) {
            String attributeName = entry.getKey();
            AttributeData attributeData = entry.getValue();
            String formattedAttributeName = ChatColor.stripColor(attributeName);
            String attributeText = ChatColor.GOLD + formattedAttributeName + ": " + attributeData.getValue() + " (x" + attributeData.getCount() + ")";
            int index = findAttributeIndexInLore(lore, formattedAttributeName);
            if (index != -1) {
                lore.set(index, attributeText);
            } else {
                lore.add(attributeText);
            }
        }
        meta.setLore(lore);
    }

    private int findAttributeIndexInLore(List<String> lore, String attributeName) {
        for (int i = 0; i < lore.size(); i++) {
            String strippedLoreLine = ChatColor.stripColor(lore.get(i));
            if (strippedLoreLine.startsWith(attributeName + ":")) {
                return i;
            }
        }
        return -1;
    }


    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getView().getTitle().equals(ChatColor.YELLOW + "Item Bestätigung")) {
            Player player = (Player) event.getPlayer();
            ItemStack temporaryItem = attributeManager.getTemporaryItem(player);
            ItemStack originalItem = attributeManager.getOriginalItem(player);
            ItemStack attributeItem = attributeManager.getAttributeItem(player);

            // Prüfen, ob der Spieler bereits das aktualisierte Item erhalten hat
            if (temporaryItem != null && !playerHasItem(player, temporaryItem)) {
                // Gibt dem Spieler das ursprüngliche Item und das Attribut-Item zurück, wenn er das aktualisierte Item noch nicht erhalten hat
                giveItemBackToPlayer(player, originalItem);
                giveItemBackToPlayer(player, attributeItem);
            }

            // Bereinigen der temporären Speicherungen
            attributeManager.clearTemporaryItem(player);
            attributeManager.clearOriginalItem(player);
            attributeManager.clearAttributeItem(player);
        }
    }

    private void giveItemBackToPlayer(Player player, ItemStack item) {
        if (item != null) {
            player.getInventory().addItem(item);
        }
    }

    private void applyConfirmation(Player player, ItemStack originalItem, ItemStack updatedItem) {
        Bukkit.getLogger().info("Original Item: " + (originalItem != null ? originalItem.toString() : "null"));
        Bukkit.getLogger().info("Updated Item: " + (updatedItem != null ? updatedItem.toString() : "null"));

        // Überprüfen, ob updatedItem null ist
        if (updatedItem == null) {
            //player.sendMessage(ChatColor.RED + "Freaky!");
            return;
        }

        // 1. Das ursprüngliche Item und das Attribut-Item aus dem Inventar des Spielers entfernen
        removeItemsFromInventory(player, originalItem, 1);
        ItemStack attributeItem = attributeManager.getOriginalItem(player);
        if (attributeItem != null) {
            removeItemsFromInventory(player, attributeItem, 1);
        }

        // 2. Überprüfen, ob das aktualisierte Item bereits im Inventar des Spielers ist
        if (!playerHasItem(player, updatedItem)) {
            // 3. Das aktualisierte Item dem Inventar des Spielers hinzufügen, wenn es nicht vorhanden ist
            addToInventory(player, updatedItem);
        }

        // 4. Die Statistiken (Attribute) der beiden Items addieren und auf das aktualisierte Item anwenden
        // Erhalte die einzigartige ID des aktualisierten Items
        String uniqueId = updatedItem.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, "uniqueId"), PersistentDataType.STRING);

        // Aktualisiere die Attribute in der Datenbank
        addAttributes(updatedItem, originalItem, uniqueId);

        player.updateInventory();

        // 5. Referenzen auf das Original- und Attribut-Item entfernen
        attributeManager.clearOriginalItem(player);
        attributeManager.clearAttributeItem(player);
    }




    private void removeItemsFromInventory(Player player, ItemStack item, int amount) {
        Inventory playerInventory = player.getInventory();
        int toRemove = amount;

        for (ItemStack inventoryItem : playerInventory.getContents()) {
            if (inventoryItem != null && inventoryItem.isSimilar(item)) {
                int count = inventoryItem.getAmount();
                if (count > toRemove) {
                    inventoryItem.setAmount(count - toRemove);
                    break;
                } else {
                    playerInventory.remove(inventoryItem);
                    toRemove -= count;
                }

                if (toRemove <= 0) {
                    break;
                }
            }
        }
    }


    private void addToInventory(Player player, ItemStack item) {
        if (item != null) {
            Inventory playerInventory = player.getInventory();

            // Überprüfen, ob der Spieler genug Platz im Inventar hat
            if (playerInventory.firstEmpty() >= 0) {
                playerInventory.addItem(item);
            } else {
                // Spieler hat keinen Platz im Inventar, hier können Sie eine andere Handhabung implementieren
                // z.B. das Item am Boden fallen lassen oder dem Spieler eine Nachricht anzeigen
                player.getWorld().dropItem(player.getLocation(), item);
                player.sendMessage(ChatColor.RED + "Dein Inventar ist voll. Das Item wurde auf den Boden geworfen.");
            }
        }
    }


    private void addAttributes(ItemStack updatedItem, ItemStack originalItem, String uniqueId) {
        // Holen Sie die Attribute aus dem aktualisierten Item
        Map<String, AttributeData> updatedAttributes = attributeManager.getAttributes(OraxenItems.getIdByItem(updatedItem));

        // Holen Sie die Attribute aus dem ursprünglichen Item
        Map<String, AttributeData> originalAttributes = attributeManager.getAttributes(OraxenItems.getIdByItem(originalItem));

        // Fügen Sie die Attribute hinzu oder aktualisieren Sie sie im aktualisierten Item
        for (Map.Entry<String, AttributeData> entry : originalAttributes.entrySet()) {
            String attributeType = entry.getKey();
            AttributeData originalData = entry.getValue();
            AttributeData updatedData = updatedAttributes.get(attributeType);

            if (updatedData != null) {
                double totalValue = originalData.getValue() + (updatedData.getValue() * updatedData.getCount());
                updatedAttributes.put(attributeType, new AttributeData(totalValue, updatedData.getCount()));
            }
        }

        // Aktualisieren Sie die Attribute im aktualisierten Item
        attributeManager.setAttributes(uniqueId, updatedAttributes);

    }




}

package h34r7l3s.freakyworld;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class CategoryTaskHandler {
    private final FreakyWorld plugin;
    private final CategoryManager categoryManager;
    private final CustomDatabaseManager customDatabaseManager;
    private final EventLogic eventLogic;
    private final GuildManager guildManager;

    public CategoryTaskHandler(FreakyWorld plugin, CategoryManager categoryManager, EventLogic eventLogic, CustomDatabaseManager customDatabaseManager, GuildManager guildManager) {
        this.plugin = plugin;
        this.categoryManager = categoryManager;
        this.eventLogic = eventLogic;
        this.customDatabaseManager = customDatabaseManager;
        this.guildManager = guildManager;
    }

    public void handleItemSubmission(Player player, ItemStack clickedItem, String category, boolean isGuildSubmission) {
        Material submittedMaterial = clickedItem.getType();
        String requiredItem = categoryManager.getTasksForCategory(category).get(0); // Erwartetes Item für die Kategorie

        int totalItemCount = getItemCount(player, submittedMaterial); // Gesamtanzahl der relevanten Items im Inventar

        if (submittedMaterial.name().equalsIgnoreCase(requiredItem) && totalItemCount > 0) {
            removeItems(player, submittedMaterial, totalItemCount); // Alle relevanten Items entfernen

            if (isGuildSubmission) {
                // Punkte zur Gilde hinzufügen
                customDatabaseManager.updateGuildScore(guildManager.getPlayerGuild(player.getName()).getName(), category, totalItemCount);
                player.sendMessage("§aDu hast " + totalItemCount + " " + submittedMaterial.name() + " für deine Gilde abgegeben.");
            } else {
                // Punkte zum Spieler hinzufügen
                customDatabaseManager.updatePlayerScore(player.getUniqueId(), category, totalItemCount);
                player.sendMessage("§aDu hast " + totalItemCount + " " + submittedMaterial.name() + " abgegeben und Punkte in der Kategorie " + category + " gesammelt.");
            }

        } else {
            player.sendMessage("§cDas abgegebene Item entspricht nicht den Anforderungen oder du hast nicht genug davon.");
        }
    }





    private void removeItems(Player player, Material material, int amountToRemove) {
        Inventory inventory = player.getInventory();
        int amountRemaining = amountToRemove;

        for (ItemStack item : inventory.getContents()) {
            if (item != null && item.getType() == material) {
                int itemAmount = item.getAmount();

                if (itemAmount > amountRemaining) {
                    item.setAmount(itemAmount - amountRemaining);
                    break;
                } else {
                    inventory.remove(item);
                    amountRemaining -= itemAmount;
                    if (amountRemaining <= 0) break;
                }
            }
        }
        player.updateInventory();
    }


    private int getItemCount(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count;
    }
}

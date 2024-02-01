package h34r7l3s.freakyworld;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CategoryTaskHandler {
    private final FreakyWorld plugin;
    private final CategoryManager categoryManager;
    private final EventLogic eventLogic;
    private final Map<Player, Map<String, Integer>> playerProgress; // Spielerfortschritt nach Kategorie

    public CategoryTaskHandler(FreakyWorld plugin, CategoryManager categoryManager, EventLogic eventLogic) {
        this.plugin = plugin;
        this.categoryManager = categoryManager;
        this.eventLogic = eventLogic;
        this.playerProgress = new HashMap<>();
    }

    // In CategoryTaskHandler

    // Überarbeitete Methode zur Item-Abgabe
    public void handleItemSubmission(Player player, ItemStack item, String category) {
        List<String> tasksForCategory = categoryManager.getTasksForCategory(category);

        if (!tasksForCategory.contains(item.getType().toString())) {
            player.sendMessage("Dieses Item gehört nicht zur aktuellen Kategorie.");
            return;
        }

        int currentProgress = playerProgress
                .computeIfAbsent(player, k -> new HashMap<>())
                .getOrDefault(category, 0);

        if (currentProgress >= tasksForCategory.size()) {
            player.sendMessage("Du hast bereits alle Aufgaben in dieser Kategorie erfüllt.");
            return;
        }

        String requiredItem = tasksForCategory.get(currentProgress);
        if (item.getType().toString().equals(requiredItem)) {
            int requiredAmount = Collections.frequency(tasksForCategory, requiredItem);

            // Count how many of the required items the player has
            int availableAmount = getItemCount(player, item);

            if (availableAmount >= requiredAmount) {
                // Remove the required items from the player's inventory
                removeItemsFromInventory(player, item.getType(), requiredAmount);

                currentProgress++;
                playerProgress.get(player).put(category, currentProgress);

                if (currentProgress == tasksForCategory.size()) {
                    eventLogic.handleItemSubmission(player, item, category);
                    player.sendMessage("Du hast alle Aufgaben in dieser Kategorie erfüllt.");
                } else {
                    player.sendMessage("Aufgabe erfüllt! Sammle das nächste Item.");
                }
            } else {
                player.sendMessage("Du hast nicht genug Items. Benötigte Menge: " + requiredAmount);
            }
        } else {
            player.sendMessage("Falsches Item abgegeben. Versuche es erneut.");
        }
    }

    private int getItemCount(Player player, ItemStack item) {
        int count = 0;

        for (ItemStack inventoryItem : player.getInventory().getContents()) {
            if (inventoryItem != null && inventoryItem.getType() == item.getType()) {
                count += inventoryItem.getAmount();
            }
        }

        return count;
    }

    private void removeItemsFromInventory(Player player, Material material, int amount) {
        ItemStack[] contents = player.getInventory().getContents();

        for (int i = 0; i < contents.length && amount > 0; i++) {
            ItemStack stack = contents[i];

            if (stack != null && stack.getType() == material) {
                int stackAmount = stack.getAmount();

                if (stackAmount <= amount) {
                    player.getInventory().setItem(i, new ItemStack(Material.AIR));
                    amount -= stackAmount;
                } else {
                    stack.setAmount(stackAmount - amount);
                    amount = 0;
                }
            }
        }

        player.updateInventory();
    }







    public void resetPlayerProgress(Player player) {
        playerProgress.remove(player);
    }
}

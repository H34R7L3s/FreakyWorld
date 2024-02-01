package h34r7l3s.freakyworld;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import java.util.*;

public class EventLogic {

    private final FreakyWorld plugin;
    private final CategoryManager categoryManager;
    private final Map<String, Map<UUID, Integer>> categoryScores;

    public EventLogic(FreakyWorld plugin, CategoryManager categoryManager) {
        this.plugin = plugin;
        this.categoryManager = categoryManager;
        this.categoryScores = new HashMap<>();

        for (String category : categoryManager.getCategories()) {
            categoryScores.put(category, new HashMap<>());
        }
    }
    public UUID getLeadingPlayerForCategory(String category) {
        UUID leadingPlayerUUID = null;
        int topScore = -1;

        Map<UUID, Integer> categoryScoresMap = categoryScores.get(category);

        if (categoryScoresMap != null) {
            for (Map.Entry<UUID, Integer> entry : categoryScoresMap.entrySet()) {
                if (entry.getValue() > topScore) {
                    leadingPlayerUUID = entry.getKey();
                    topScore = entry.getValue();
                }
            }
        }

        return leadingPlayerUUID;
    }


    public void handleItemSubmission(Player player, ItemStack item, String currentCategory) {
        String mode = categoryManager.getCategoryMode(currentCategory);

        switch (mode) {
            case "FreeForAll":
                handleFreeForAll(player, item, currentCategory);
                break;
            case "DailyTask":
                handleDailyTask(player, item, currentCategory);
                break;
            case "TeamMode":
                handleTeamMode(player, item, currentCategory);
                break;
            default:
                player.sendMessage("Unbekannter Modus für diese Kategorie.");
                break;
        }
    }

    public void handleFreeForAll(Player player, ItemStack item, String category) {
        if (!isValidCategory(category)) return;

        int currentScore = categoryScores.get(category).getOrDefault(player.getUniqueId(), 0);
        int itemAmount = getItemCount(player, item);

        if (itemAmount > currentScore) {
            categoryScores.get(category).put(player.getUniqueId(), itemAmount);
            updateScoreboard(category);
        }
    }

    private int getItemCount(Player player, ItemStack item) {
        int count = 0;

        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == item.getType()) {
                count += stack.getAmount();
            }
        }

        return count;
    }

    private void updateScoreboard(String category) {
        String topPlayerName = getTopPlayerName(category);

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            // Hier fehlt die Methode setScoreboard, die den Spielern ein Scoreboard zuweist.
            // Das muss entsprechend Ihrer Implementierung hinzugefügt werden.
        }
    }

    private String getTopPlayerName(String category) {
        UUID topPlayerUUID = null;
        int topScore = -1;

        for (Map.Entry<UUID, Integer> entry : categoryScores.get(category).entrySet()) {
            if (entry.getValue() > topScore) {
                topPlayerUUID = entry.getKey();
                topScore = entry.getValue();
            }
        }

        if (topPlayerUUID != null) {
            Player topPlayer = plugin.getServer().getPlayer(topPlayerUUID);

            if (topPlayer != null) {
                return topPlayer.getName();
            }
        }

        return "No Leader";
    }

    public void handleDailyTask(Player player, ItemStack item, String category) {
        if (!isValidCategory(category)) return;

        List<String> tasksForCategory = categoryManager.getTasksForCategory(category);

        if (tasksForCategory.contains(item.getType().toString())) {
            int requiredAmount = Collections.frequency(tasksForCategory, item.getType().toString());
            int providedAmount = getItemCount(player, item);

            if (providedAmount >= requiredAmount) {
                // Spieler hat die tägliche Aufgabe erfolgreich abgeschlossen
                rewardPlayerForDailyTask(player);
            }
        }
    }

    private void rewardPlayerForDailyTask(Player player) {
        // Belohnen Sie den Spieler für das Erfüllen der täglichen Aufgabe, z.B. Erfahrungspunkte hinzufügen
        player.giveExp(10); // Beispielbelohnung
    }

    private boolean isValidCategory(String category) {
        return categoryScores.containsKey(category);
    }

    private void handleTeamMode(Player player, ItemStack item, String category) {
        // Team-Modus Logik
        handleFreeForAll(player, item, category); // Team-Modus ist auch ein "Frei für Alle"
    }
}

package h34r7l3s.freakyworld;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FreeForAllEvent {
    private final FreakyWorld plugin;
    private final CategoryManager categoryManager;
    private final EventLogic eventLogic;
    private Map<UUID, Integer> playerScores = new HashMap<>();

    public FreeForAllEvent(FreakyWorld plugin, CategoryManager categoryManager, EventLogic eventLogic) {
        this.plugin = plugin;
        this.categoryManager = categoryManager;
        this.eventLogic = eventLogic;

        // Starten Sie das Event, wenn der Server gestartet wird
        startEvent();

        // Zeigen Sie den führenden Spieler im Live Feed an
        updateLiveFeed();
    }

    public void startEvent() {
        // Starten Sie hier den Timer für das einstündige Event
        new BukkitRunnable() {
            @Override
            public void run() {
                // Ermitteln Sie den Gewinner des Events basierend auf den Spieler-Scores
                UUID leadingPlayer = getLeadingPlayer();
                // Zeigen Sie den Gewinner im Live Feed an

                // Starten Sie das Event erneut, um eine wiederholte Ausführung sicherzustellen
                startEvent();
            }
        }.runTaskLater(plugin, 20 * 60 * 60); // Event dauert eine Stunde (20 Ticks * 60 Sekunden * 60 Minuten)
    }

    public void handleItemSubmission(Player player, ItemStack item) {
        // Verfolgen Sie die Anzahl der abgegebenen Items für jeden Spieler
        int score = playerScores.getOrDefault(player.getUniqueId(), 0);
        score += item.getAmount();
        playerScores.put(player.getUniqueId(), score);
    }

    public UUID getLeadingPlayer() {
        // Ermitteln Sie den führenden Spieler basierend auf den Scores
        UUID leadingPlayer = null;
        int topScore = -1;

        for (Map.Entry<UUID, Integer> entry : playerScores.entrySet()) {
            if (entry.getValue() > topScore) {
                leadingPlayer = entry.getKey();
                topScore = entry.getValue();
            }
        }

        return leadingPlayer;
    }

    public void updateLiveFeed() {
        // Zeigen Sie den Kopf des führenden Spielers im Live Feed an
        UUID leadingPlayer = getLeadingPlayer();

        if (leadingPlayer != null) {
            Player player = Bukkit.getPlayer(leadingPlayer);

            if (player != null) {
                // Zeigen Sie den Kopf des führenden Spielers an (z.B. über Hologramme oder Schilder)
            }
        }
    }
}

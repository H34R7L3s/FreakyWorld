package h34r7l3s.freakyworld;

import org.bukkit.Location;
import org.bukkit.entity.Villager;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Random;

public class VillagerCategoryManager {

    private final FreakyWorld plugin;
    private Villager villager;
    private double spawnProbability;
    private String currentCategory;

    public VillagerCategoryManager(FreakyWorld plugin) {
        this.plugin = plugin;
        this.spawnProbability = 0; // Startwahrscheinlichkeit
        updateSpawnProbability();  // Starte das regelmäßige Update der Spawn-Wahrscheinlichkeit
    }

    public void updateSpawnProbability() {
        new BukkitRunnable() {
            @Override
            public void run() {
                double eventProbability = plugin.getDiscordBot().getEventProbability(); // Erhalte die Wahrscheinlichkeit vom Discord Bot

                if (spawnProbability < 1.0) {
                    spawnProbability += 0.05 * eventProbability; // Anpassung der Erhöhung basierend auf der Event-Wahrscheinlichkeit
                    spawnProbability = Math.min(spawnProbability, 1.0);
                }

                if (new Random().nextDouble() < spawnProbability) {
                    spawnVillager(); // Versuche den Villager zu spawnen, basierend auf der aktuellen Wahrscheinlichkeit
                }
            }
        }.runTaskTimer(plugin, 0, 20 * 60 * 60); // Jede Stunde aktualisieren
    }


    public void spawnVillager() {
        if (new Random().nextDouble() > spawnProbability) {
            return; // Nicht spawnen, wenn die Wahrscheinlichkeit nicht erreicht ist
        }

        Location spawnLocation = new Location(plugin.getServer().getWorld("world"), 100, 65, 100);
        this.villager = (Villager) spawnLocation.getWorld().spawnEntity(spawnLocation, EntityType.VILLAGER);

        this.villager.setCustomName("Quest Villager");
        this.villager.setInvulnerable(true);
        this.villager.setAI(false);

        initializeVillagerLookTask();
        initializeDailyCategoryTask(); // Starte die tägliche Kategorieauswahl
        displayCurrentTask(this.villager);  // Zeige die aktuelle Aufgabe am Villager an, sobald er gespawnt wird

    }

    private void initializeVillagerLookTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (villager != null && !villager.isDead()) {
                    lookAtNearestPlayer();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // Aktualisiert alle Sekunde
    }

    private void lookAtNearestPlayer() {
        Collection<Player> nearbyPlayers = villager.getWorld().getNearbyPlayers(villager.getLocation(), 10);
        Player nearestPlayer = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Player player : nearbyPlayers) {
            double distance = player.getLocation().distanceSquared(villager.getLocation());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestPlayer = player;
            }
        }

        if (nearestPlayer != null) {
            villager.lookAt(nearestPlayer.getLocation());
        }
    }

    public void chooseDailyCategory() {
        String[] categories = {"Bauernmarkt", "Minenarbeit", "Monsterjagd", "Angeln", "Schmiedekunst", "Baumfäller"};
        this.currentCategory = categories[new Random().nextInt(categories.length)];
        plugin.getLogger().info("Heutige Kategorie: " + this.currentCategory);
    }

    public String getCurrentCategory() {
        return currentCategory;
    }

    private void initializeDailyCategoryTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                chooseDailyCategory();
                if (villager != null) {
                    displayCurrentTask(villager);  // Aktualisiere die Anzeige jedes Mal, wenn eine neue Kategorie gewählt wird
                }
            }
        }.runTaskTimer(plugin, 0, 20 * 60 * 60 * 24); // 20 Ticks * 60 Sekunden * 60 Minuten * 24 Stunden
    }

    // In VillagerCategoryManager

    // Neue Methode, um die aktuelle Aufgabe anzuzeigen
    public void displayCurrentTask(Villager villager) {
        String taskMessage = "Aktuelle Aufgabe: " + getCurrentCategory();
        List<String> tasks = plugin.getCategoryManager().getTasksForCategory(getCurrentCategory());

        if (!tasks.isEmpty()) {
            taskMessage += " - Sammle: " + String.join(", ", tasks);
        }

        // Hier Code zum Anzeigen der Nachricht (z.B. über Hologramm oder Schild)
        // Beispiel: villager.setCustomName(taskMessage);
    }
    public void updateAndDisplayTask(Villager villager) {
        updateSpawnProbability(); // Aktualisiere die Spawn-Wahrscheinlichkeit
        chooseDailyCategory();    // Wähle die tägliche Kategorie
        displayCurrentTask(villager); // Zeige die aktuelle Aufgabe am Villager an
    }


    public void removeVillager() {
        if (this.villager != null) {
            this.villager.remove();
        }
    }
}

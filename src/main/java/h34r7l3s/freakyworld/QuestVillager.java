
package h34r7l3s.freakyworld;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

public class QuestVillager implements Listener {
    private final HashMap<UUID, Set<Location>> playerNotesDiscovered = new HashMap<>();
    private final JavaPlugin plugin;
    private final NamespacedKey questVillagerKey;
    private final HashMap<UUID, Integer> playerNotesFound = new HashMap<>();
    private final Location[] noteLocations = {
            new Location(Bukkit.getWorld("world"), -250, 77, 1851),
            new Location(Bukkit.getWorld("world"), -260, 77, 1851),
            new Location(Bukkit.getWorld("world"), -242, 77, 1840),
            new Location(Bukkit.getWorld("world"), -243, 75, 1827)
    };

    public QuestVillager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.questVillagerKey = new NamespacedKey(plugin, "QuestVillagerName");
        spawnQuestVillager();
    }

    private void spawnQuestVillager() {
        Location villagerLocation = new Location(Bukkit.getWorld("world"), -249, 86, 1864);
        Villager questVillager = (Villager) villagerLocation.getWorld().spawnEntity(villagerLocation, EntityType.VILLAGER);
        questVillager.setCustomName("QuestVillager");
        questVillager.setInvulnerable(true);
        questVillager.setAI(false);
    }
    @EventHandler
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (event.getRightClicked().getType() == EntityType.VILLAGER) {
            Villager villager = (Villager) event.getRightClicked();
            Player player = event.getPlayer();

            if (villager.getCustomName() != null && villager.getCustomName().equals("QuestVillager")) {
                villager.setInvulnerable(true);
                villager.setAI(false);

                Set<Location> discoveredNotes = playerNotesDiscovered.getOrDefault(player.getUniqueId(), new HashSet<>());
                if (discoveredNotes.size() == noteLocations.length) {
                    player.sendMessage("QuestVillager: " + "Hervorragend! Du hast alle Noten gefunden und die Melodie gespielt. Hier ist dein BattlePass!");
                    // Hier können Sie weitere Aktionen hinzufügen, z. B. dem Spieler den BattlePass geben
                } else {
                    player.sendMessage("QuestVillager: " + "Hallo Abenteurer! Ich habe die Noten meiner berühmtesten Melodie verloren. Kannst du mir helfen, sie zu finden?");
                    player.sendMessage("QuestVillager: " + "Suche nach speziellen Notenblöcken in der Welt. Sie geben eine einzigartige Note ab, wenn du in ihre Nähe kommst.");
                }

                event.setCancelled(true);
            }
        }
    }


    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location location = player.getLocation();

        for (int i = 0; i < noteLocations.length; i++) {
            Location noteLocation = noteLocations[i];
            if (location.distance(noteLocation) <= 5) {
                Set<Location> discoveredNotes = playerNotesDiscovered.getOrDefault(player.getUniqueId(), new HashSet<>());
                if (!discoveredNotes.contains(noteLocation)) {
                    discoveredNotes.add(noteLocation);
                    playerNotesDiscovered.put(player.getUniqueId(), discoveredNotes);

                    // Play a unique sound for each note location
                    Sound sound = Sound.values()[Sound.BLOCK_NOTE_BLOCK_PLING.ordinal() + i];
                    player.playSound(location, sound, 1.0f, 1.0f);
                    player.spawnParticle(Particle.NOTE, noteLocation, 10);

                    int notesRemaining = 4 - discoveredNotes.size();
                    player.sendMessage("QuestVillager: " + "Gut gemacht! Du hast eine Note gefunden. Noch " + notesRemaining + " übrig.");

                    if (notesRemaining == 0) {
                        player.sendMessage("QuestVillager: " + "Hervorragend! Du hast alle Noten gefunden. Kehre zu mir zurück und spiele die Melodie.");
                    }
                }
            }
        }
    }

    public void removeQuestVillager() {
        Location villagerLocation = new Location(Bukkit.getWorld("world"), -249, 86, 1864);
        Villager questVillager = (Villager) villagerLocation.getWorld().spawnEntity(villagerLocation, EntityType.VILLAGER);
        if (questVillager != null && questVillager.getCustomName().equals("QuestVillager")) {
            questVillager.remove();
        }
    }


}


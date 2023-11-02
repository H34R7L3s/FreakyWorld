package h34r7l3s.freakyworld;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.metadata.Metadatable;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.logging.Logger;



import java.util.Arrays;
import java.util.List;

public class MyVillager implements Listener {

    private final JavaPlugin plugin;
    private final NamespacedKey villagerKey;

    public MyVillager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.villagerKey = new NamespacedKey(plugin, "Unbekannter");
    }

    @EventHandler
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (event.getRightClicked().getType() == EntityType.VILLAGER) {
            Villager villager = (Villager) event.getRightClicked();
            Player player = event.getPlayer();

            // Überprüfe, ob der Villager ein benutzerdefiniertes NameTag hat
            if (villager.getCustomName() != null) {
                // Überprüfe, ob der Villager den gewünschten Namen hat
                if (villager.getCustomName().equals("Unbekannter")) {
                    // Setze den Villager als unverwundbar und verhindere, dass er sich bewegt
                    villager.setInvulnerable(true);
                    villager.setAI(false);

                    // Definiere die Sätze, die der Villager sagen soll
                    List<String> sentences = Arrays.asList(
                            "FREAKS?! Es freut mich, euch zu sehen!",
                            "Ich habe etwas Besonderes vorbereitet. Viel Arbeit wartet auf euch!",
                            "Muss ich da viel erklären?",
                            "....",
                            "Schau es dir einfach mal an",
                            "Hier:"
                    );

                    // Starte eine verzögerte Aufgabe für jeden Satz
                    new BukkitRunnable() {
                        int index = 0;

                        @Override
                        public void run() {
                            if (index < sentences.size()) {
                                player.sendMessage(villager.getCustomName() + ": " + sentences.get(index));
                                index++;
                            } else {
                                // Öffne die GUI über den Befehl /battlepass
                                Bukkit.getScheduler().runTaskLater(plugin, () -> player.performCommand("battlepass"), 1L);
                                cancel();  // Beende die Aufgabe
                            }
                        }
                    }.runTaskTimer(plugin, 0L, 60L);  // Startet sofort und wiederholt alle 60 Ticks (3 Sekunden)

                    // Verhindere, dass der Villager normal handelt (z. B. Handeln oder Reden)
                    event.setCancelled(true);
                }
            }
        }
    }


    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity().getType() == EntityType.VILLAGER) {
            Villager villager = (Villager) event.getEntity();
            if (villager.getCustomName() != null && villager.getCustomName().equals("Unbekannter")) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onEntityTarget(EntityTargetEvent event) {
        if (event.getEntity().getType() == EntityType.VILLAGER) {
            Villager villager = (Villager) event.getEntity();
            if (villager.getCustomName() != null && villager.getCustomName().equals("Unbekannter")) {
                event.setCancelled(true);
            }
        }
    }

    private void setMetadata(Metadatable metadatable, String value) {
        metadatable.setMetadata(villagerKey.toString(), new FixedMetadataValue(plugin, value));
    }

    private String getMetadata(Metadatable metadatable) {
        for (MetadataValue metadata : metadatable.getMetadata(villagerKey.toString())) {
            if (metadata.getOwningPlugin() == plugin) {
                return metadata.asString();
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        var player = event.getPlayer();
        var message = event.getMessage();

        // Log every command for debugging
        Logger.getLogger("Minecraft").info(player.getName() + " executed command: " + message);

        if (message.equalsIgnoreCase("/bp") || message.startsWith("/bp ")) {
            event.setMessage("/nocommand");
            //Das hier ist mein erster Test
            Logger.getLogger("Minecraft").info("Cancelled /bp command for player: " + player.getName());
        }
    }


}


/*

 */
package h34r7l3s.freakyworld;

import io.th0rgal.oraxen.api.OraxenItems;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public class ElytraDoubleJumpEffect implements Listener {

    private JavaPlugin plugin;
    private final String itemID = "vampir"; // ID des speziellen Items

    public ElytraDoubleJumpEffect(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();

        // Überprüfe, ob der Spieler eine Elytra trägt und im Flugmodus ist
        if (player.isGliding()) {
            String itemId = OraxenItems.getIdByItem(player.getInventory().getItemInMainHand());

            // Überprüfe, ob der Spieler das richtige Item hält
            if (itemId != null && itemId.equals(itemID)) {
                event.setCancelled(true); // Verhindere den normalen Flug

                // Füge einen Schub hinzu, um den Doppel-Sprung zu simulieren
                Vector jump = player.getLocation().getDirection().multiply(1.5).setY(1);
                player.setVelocity(player.getVelocity().add(jump));

                // Partikeleffekte, um den Effekt visuell darzustellen
                player.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, player.getLocation(), 5);
                player.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, player.getLocation(), 10);

                // Weitere benutzerdefinierte Aktionen können hier hinzugefügt werden

                // Beispiel: Funktion zum Auslösen eines zusätzlichen Effekts oder einer Fähigkeit
                triggerSpecialAbility(player);
            }
        }
    }

    private void triggerSpecialAbility(Player player) {
        // Hier können zusätzliche Effekte oder Fähigkeiten implementiert werden
        // Beispiel: Heilung, Teleportation, Angriffseffekte, etc.

        // Beispiel: Partikeleffekt um den Spieler
        player.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE, player.getLocation(), 30, 1, 1, 1, 0);

        // Hinweis: Die genaue Implementierung hängt von den spezifischen Anforderungen deines Projekts ab
    }

    // Weitere Hilfsmethoden und Funktionen können hier hinzugefügt werden
}

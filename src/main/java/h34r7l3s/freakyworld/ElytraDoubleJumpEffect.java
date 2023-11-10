package h34r7l3s.freakyworld;

import io.th0rgal.oraxen.api.OraxenItems;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public class ElytraDoubleJumpEffect implements Listener {

    private JavaPlugin plugin;
    private final String itemID = "vampir"; // ID des speziellen Items

    public ElytraDoubleJumpEffect(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();

        // Überprüfen, ob es Nacht ist
        if (world.getTime() > 12300 && world.getTime() < 23850) {
            if (player.isGliding()) {
                String itemId = OraxenItems.getIdByItem(player.getInventory().getItemInMainHand());

                if (itemId != null && itemId.equals(itemID)) {
                    // Langsamere Bewegung
                    Vector direction = player.getLocation().getDirection();
                    player.setVelocity(direction.multiply(0.7)); // Reduzierter Multiplikator

                    // Partikeleffekte und Unsichtbarkeit
                    createDarkMysteriousParticles(player);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 200, 0, false, false));
                } else if (player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                    player.removePotionEffect(PotionEffectType.INVISIBILITY);
                }
            }
        }
    }
    private void createDarkMysteriousParticles(Player player) {
        // Angepasste Partikeleffekte
        player.getWorld().spawnParticle(Particle.SMOKE_NORMAL, player.getLocation(), 20, 0.5, 0.5, 0.5, 0.05);
        player.getWorld().spawnParticle(Particle.SPELL_MOB, player.getLocation(), 10, 0.5, 0.5, 0.5, 1.0);
    }

    // Weitere Hilfsmethoden und Funktionen können hier hinzugefügt werden
}

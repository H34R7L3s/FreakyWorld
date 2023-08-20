package h34r7l3s.freakyworld;

import io.th0rgal.oraxen.api.OraxenItems;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.Particle.DustOptions;
import org.bukkit.Color;
import org.bukkit.util.Vector;

import static io.th0rgal.oraxen.shaded.playeranimator.api.PlayerAnimatorPlugin.plugin;

public class VampirZepter implements Listener {

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        Entity victim = event.getEntity();

        if (damager instanceof Player) {
            Player player = (Player) damager;
            String itemId = OraxenItems.getIdByItem(player.getInventory().getItemInMainHand());
            if (itemId != null && itemId.equals("vampir")) {
                // Direktes Spawnen von Partikeln an der Position des Opfers
                DustOptions redstoneOptions = new DustOptions(Color.RED, 1); // Farbe Rot
                victim.getWorld().spawnParticle(Particle.REDSTONE, victim.getLocation(), 20, redstoneOptions);
                victim.getWorld().spawnParticle(Particle.FLAME, victim.getLocation(), 20);
                victim.getWorld().spawnParticle(Particle.LAVA, victim.getLocation(), 20);
                victim.getWorld().spawnParticle(Particle.DRAGON_BREATH, victim.getLocation(), 20);

                // Erzeugen der Partikel-Schlange von Opfer zu Spieler
                createParticleSnake(victim.getLocation(), player.getLocation(), player, 2.0); // Heilung um 2 Herzen
            }
        }
    }




    private void createParticleSnake(Location start, Location end, Player player, double healthToRegain) {
        int steps = 6; // Anzahl der Schritte für die Interpolation
        long delayBetweenSteps = 1L; // 2 Ticks Verzögerung zwischen jedem Schritt

        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double x = lerp(start.getX(), end.getX(), t);
            double y = lerp(start.getY(), end.getY(), t);
            double z = lerp(start.getZ(), end.getZ(), t);

            Location particleLocation = new Location(start.getWorld(), x, y, z);

            // Verzögertes Spawnen der Partikel entlang des Pfads
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                DustOptions redstoneOptions = new DustOptions(Color.RED, 1); // Farbe Rot und Größe 1
                particleLocation.getWorld().spawnParticle(Particle.REDSTONE, particleLocation, 20, redstoneOptions);

                start.getWorld().spawnParticle(Particle.FLAME, particleLocation, 4); // Erhöhte Anzahl
                start.getWorld().spawnParticle(Particle.LAVA, particleLocation, 2); // Erhöhte Anzahl
                start.getWorld().spawnParticle(Particle.DRAGON_BREATH, particleLocation, 20); // Zusätzlicher Partikeltyp
            }, i * delayBetweenSteps);
        }

        // Verzögerte Heilung, nachdem die Leuchtschlange den Spieler erreicht hat
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            player.setHealth(Math.min(player.getHealth() + healthToRegain, player.getMaxHealth()));
            Vector direction = player.getLocation().getDirection().normalize().multiply(2); // 3 Blöcke in die Blickrichtung
            Location loc = player.getLocation().add(direction).add(0, 2, 0); // 2 Blöcke nach oben
            player.getWorld().spawnParticle(Particle.HEART, loc, 10);
        }, (steps + 1) * delayBetweenSteps);
    }


    // Hilfsmethode für lineare Interpolation
    private double lerp(double start, double end, double t) {
        return start + t * (end - start);
    }





    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        String itemId = OraxenItems.getIdByItem(player.getInventory().getItemInMainHand());

        // Check if itemId is not null before comparing it
        if (itemId != null && itemId.equals("vampir") && event.getAction() == Action.LEFT_CLICK_AIR) {
            player.getWorld().spawnParticle(Particle.EXPLOSION_NORMAL, player.getLocation().add(0, 1, 0), 10);
        }
    }

    public void startVampirZepterEffectLoop(JavaPlugin plugin) {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    String itemId = OraxenItems.getIdByItem(player.getInventory().getItemInMainHand());
                    if (itemId != null && itemId.equals("vampir")) {
                        player.getWorld().spawnParticle(Particle.SPELL_WITCH, player.getLocation().add(0, 1, 0), 10);
                        player.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE, player.getLocation().add(0, 2, 0), 10);
                        player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1, 0), 5);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }
}

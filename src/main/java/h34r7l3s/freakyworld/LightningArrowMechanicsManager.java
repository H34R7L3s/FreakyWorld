package h34r7l3s.freakyworld;


import org.bukkit.entity.Player;
import org.bukkit.entity.Arrow;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;

public class LightningArrowMechanicsManager implements Listener {

    private final JavaPlugin plugin;
    private static Location lastShotLocation = null;
    private static final double MAX_SHOT_DISTANCE = 100.0;

    public LightningArrowMechanicsManager(JavaPlugin plugin, LightningArrowMechanicFactory factory) {
        this.plugin = plugin;
        System.out.println("[DEBUG] New instance of LightningArrowMechanicsManager created!");
    }

    @EventHandler
    public void onArrowShoot(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player && event.getProjectile() instanceof Arrow) {
            Player player = (Player) event.getEntity();
            Arrow arrow = (Arrow) event.getProjectile();
            ItemStack arrowItem = event.getArrowItem();

            //System.out.println("An arrow event has been detected.");

            if (arrowItem.hasItemMeta()) {
                //System.out.println("Arrow has item meta.");
                String displayName = arrowItem.getItemMeta().getDisplayName();
                //System.out.println("Arrow Display Name: " + displayName);

                if (displayName.contains("Thors Pfeil")) {
                    System.out.println("An arrow has been shot by " + player.getName() + " from location: " + arrow.getLocation().toString());
                    arrow.setMetadata("ThorArrow", new FixedMetadataValue(plugin, true));
                    //System.out.println("Metadata set for arrow.");
                    lastShotLocation = arrow.getLocation();
                    System.out.println("Last shot location set to: " + lastShotLocation.toString());
                } else {
                    //System.out.println("This is not the custom arrow. Ignoring...");
                }
            } else {
                //System.out.println("Arrow does not have item meta.");
            }
        }
    }

    @EventHandler
    public void onArrowLand(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Arrow) {
            Arrow arrow = (Arrow) event.getEntity();

            if (arrow.hasMetadata("ThorArrow")) {
                System.out.println("Arrow has ThorArrow metadata. This is our custom arrow!");
                processArrowLanding(arrow);
            } else {
                System.out.println("This arrow doesn't have the custom metadata. Ignoring...");
            }
        }
    }

    private void processArrowLanding(Arrow arrow) {
        Location landedLocation = arrow.getLocation();
        System.out.println("An arrow has landed at location: " + landedLocation.toString());

        if (lastShotLocation != null) {
            System.out.println("Last shot location exists: " + lastShotLocation.toString());

            if (lastShotLocation.getWorld().equals(landedLocation.getWorld())) {
                System.out.println("The arrow landed in the same world as the shot.");

                double distance = lastShotLocation.distance(landedLocation);
                System.out.println("Calculated distance between shot and landing: " + distance);

                if (distance <= MAX_SHOT_DISTANCE) {
                    System.out.println("Distance is within the allowed range. Striking lightning...");
                    World world = landedLocation.getWorld();
                    world.strikeLightning(landedLocation);
                } else {
                    System.out.println("Distance is too large. Not striking lightning.");
                }
            } else {
                System.out.println("The arrow landed in a different world. Not striking lightning.");
            }
        } else {
            System.out.println("Last shot location does not exist.");
        }
    }
}

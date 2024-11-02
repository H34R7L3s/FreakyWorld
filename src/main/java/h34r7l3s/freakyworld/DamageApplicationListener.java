package h34r7l3s.freakyworld;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;

//import static io.th0rgal.oraxen.shaded.playeranimator.api.PlayerAnimatorPlugin.plugin;

public class DamageApplicationListener implements Listener {

    private final WeaponAttributeManager attributeManager;
    private final JavaPlugin plugin; // Hinzufügen des Plugin-Referenzfeldes

    public DamageApplicationListener(JavaPlugin plugin, WeaponAttributeManager attributeManager) {
        this.plugin = plugin; // Initialisieren der Plugin-Referenz
        this.attributeManager = attributeManager;
    }


    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;

        Player player = (Player) event.getDamager();
        ItemStack weapon = player.getInventory().getItemInMainHand();

        if (!weapon.hasItemMeta()) return;

        String itemId = weapon.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, "item_id"), PersistentDataType.STRING);

        if (itemId == null) return;

        Map<String, AttributeData> attributes = attributeManager.getAttributes(itemId);

        // Mehr Schaden
        AttributeData damageData = attributes.get("mehr Schaden");
        if (damageData != null) {
            double additionalDamage = damageData.getValue() * damageData.getCount();
            event.setDamage(event.getDamage() + additionalDamage);
        }

        // Statischer Blitz
        AttributeData lightningData = attributes.get("statischer Blitz");
        if (lightningData != null && Math.random() < lightningData.getValue()) {
            applyStaticLightningEffect(player, event.getEntity());
        }

        // Verlangsamen/Einfrieren
        AttributeData slowingData = attributes.get("verlangsamen");
        if (slowingData != null && Math.random() < slowingData.getValue()) {
            applySlowingEffect(event.getEntity(), slowingData.getValue());
        }
    }


    private void applyStaticLightningEffect(Player player, Entity target) {
        // Logik für das Erzeugen eines Blitzes
        target.getWorld().strikeLightningEffect(target.getLocation()); // Visueller Blitz ohne Schaden
        if (target instanceof LivingEntity) {
            ((LivingEntity) target).damage(5, player); // Zusätzlicher Blitzschaden
        }
    }

    private void applySlowingEffect(Entity target, double effectDuration) {
        // Logik für das Verlangsamen/Einfrieren des Ziels
        if (target instanceof LivingEntity) {
            ((LivingEntity) target).addPotionEffect(new PotionEffect(PotionEffectType.SLOW, (int) (effectDuration * 20), 1)); // Verlangsamen
        }
    }
}

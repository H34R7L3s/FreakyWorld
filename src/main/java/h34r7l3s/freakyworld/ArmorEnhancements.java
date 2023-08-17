package h34r7l3s.freakyworld;

import io.th0rgal.oraxen.api.OraxenItems;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class ArmorEnhancements implements Listener {

    private enum ArmorType {
        SKY, FIRE, WATER, STONE, NONE
    }

    private final JavaPlugin plugin;
    private final HashMap<UUID, BossBar> playerBossBars = new HashMap<>();
    private final HashMap<UUID, Float> bossBarProgress = new HashMap<>();
    private final Random random = new Random();

    public ArmorEnhancements(JavaPlugin plugin) {
        this.plugin = plugin;

        // Periodically check players' armor
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    checkPlayerArmor(player);
                }
            }
        }.runTaskTimer(plugin, 0L, 100L); // Check every 5 seconds (100 ticks)

        // Periodically change BossBar appearance for shimmering effect
        new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID uuid : playerBossBars.keySet()) {
                    BossBar bossBar = playerBossBars.get(uuid);
                    if (bossBar.getColor() == BarColor.PURPLE) {
                        bossBar.setColor(BarColor.YELLOW);
                        bossBar.setStyle(BarStyle.SEGMENTED_10);
                        bossBar.setTitle("LEGENDARY ITEM");
                        bossBar.setProgress(0); // Start the progress at 0
                        bossBarProgress.put(uuid, 0f);
                    } else {
                        bossBar.setColor(BarColor.PURPLE);
                        bossBar.setStyle(BarStyle.SOLID);
                        ArmorType armorType = getFullArmorSetType(Bukkit.getPlayer(uuid).getInventory().getArmorContents());
                        bossBar.setTitle(getArmorTitle(armorType));
                        bossBar.setProgress(1.0); // Set the progress to full
                        bossBarProgress.remove(uuid); // Remove the player from progress tracking
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 120L);

        // Periodically update the progress for the LEGENDARY ITEM bossbar
        new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID uuid : bossBarProgress.keySet()) {
                    BossBar bossBar = playerBossBars.get(uuid);
                    if (bossBar == null) {
                        continue; // Überspringt diesen Durchlauf, wenn die BossBar null ist
                    }
                    float progress = bossBarProgress.get(uuid);

                    if (progress < 0.5) {
                        progress += 0.05; // Increase the progress by 5% every tick until 50%
                    } else if (progress < 1) {
                        progress += 0.025; // Slow down the increase from 50% to 100%
                    } else {
                        progress = 1;
                    }

                    progress = Math.min(1.0f, Math.max(0.0f, progress));
                    bossBar.setProgress(progress);
                }
            }
        }.runTaskTimer(plugin, 0L, 2L); // Update every 2 ticks (1/10th of a second)
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            Bukkit.getScheduler().runTaskLater(plugin, () -> checkPlayerArmor(player), 1L);
        }
    }





    private void checkPlayerArmor(Player player) {
        ItemStack[] armor = player.getInventory().getArmorContents();
        ArmorType armorType = getFullArmorSetType(armor);

        if (armorType != ArmorType.NONE) {
            displayArmorEffects(player, armorType);

            if (!playerBossBars.containsKey(player.getUniqueId())) {
                BossBar bossBar = Bukkit.createBossBar(getArmorTitle(armorType), BarColor.BLUE, BarStyle.SOLID);
                bossBar.addPlayer(player);
                playerBossBars.put(player.getUniqueId(), bossBar);
            }
        } else {
            if (playerBossBars.containsKey(player.getUniqueId())) {
                BossBar bossBar = playerBossBars.get(player.getUniqueId());
                bossBar.removePlayer(player);
                bossBar.setVisible(false);
                playerBossBars.remove(player.getUniqueId());
            }
        }
    }

    private ArmorType getFullArmorSetType(ItemStack[] armor) {
        ArmorType type = getArmorType(armor[0]);
        for (int i = 1; i < armor.length; i++) {
            if (getArmorType(armor[i]) != type) {
                return ArmorType.NONE;
            }
        }
        return type;
    }

    private ArmorType getArmorType(ItemStack item) {
        if (item == null) {
            return ArmorType.NONE;
        }

        String oraxenId = OraxenItems.getIdByItem(item);
        if (oraxenId == null) {
            return ArmorType.NONE;
        }

        switch (oraxenId) {
            case "sky_crown":
            case "sky_guard":
            case "sky_leggings":
            case "sky_boots":
                return ArmorType.SKY;
            case "fire_crown":
            case "fire_guard":
            case "fire_leggings":
            case "fire_boots":
                return ArmorType.FIRE;
            case "water_crown":
            case "water_guard":
            case "water_leggings":
            case "water_boots":
                return ArmorType.WATER;
            case "stone_crown":
            case "stone_guard":
            case "stone_leggings":
            case "stone_boots":
                return ArmorType.STONE;
            default:
                return ArmorType.NONE;
        }
    }


    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (event.isSneaking()) {
            Vector direction = player.getLocation().getDirection().multiply(2); // Geschwindigkeitsmultiplikator
            player.setVelocity(direction);
        }
    }
//Hier
    private String getArmorTitle(ArmorType armorType) {
        switch (armorType) {
            case SKY:
                return "Rüstung des Himmels";
            case FIRE:
                return "Rüstung des Feuers";
            case WATER:
                return "Rüstung des Meeres";
            case STONE:
                return "Rüstung des Berges";
            default:
                return "Unbekannte Rüstung";
        }
    }



    private void displayArmorEffects(Player player, ArmorType armorType) {
        Location loc = player.getLocation().add(0, 1, 0); // Zentriert auf den Spieler, leicht erhöht
        double offsetX = (Math.random() - 0.5) * 2; // Zufälliger Wert zwischen -1 und 1
        double offsetY = (Math.random() - 0.5) * 2;
        double offsetZ = (Math.random() - 0.5) * 2;

        switch (armorType) {
            case SKY:
                loc.getWorld().spawnParticle(Particle.CLOUD, loc, 10, offsetX, offsetY, offsetZ, 0.05);
                break;
            case FIRE:
                loc.getWorld().spawnParticle(Particle.FLAME, loc, 10, offsetX, offsetY, offsetZ, 0.05);
                break;
            case WATER:
                loc.getWorld().spawnParticle(Particle.WATER_DROP, loc, 10, offsetX, offsetY, offsetZ, 0.05);
                break;
            case STONE:
                loc.getWorld().spawnParticle(Particle.BLOCK_CRACK, loc, 10, offsetX, offsetY, offsetZ, 0.05, Material.STONE.createBlockData());
                break;
            default:
                break;
        }
    }
}
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class ArmorEnhancements implements Listener {

    private enum ArmorType {
        SKY, FIRE, WATER, STONE, NONE
    }

    private final HashMap<UUID, Boolean> playerFlightStatus = new HashMap<>();

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
                        bossBar.setTitle("Rüstung des vergessenen Helden");
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


    private final HashMap<UUID, Integer> playerJumpCount = new HashMap<>();

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (event.getFrom().getY() < event.getTo().getY() && player.isOnGround()) {
            //System.out.println(player.getName() + " hat gesprungen!"); // Debug-Ausgabe
            Bukkit.getServer().getPluginManager().callEvent(new PlayerJumpEvent(player));
        }
    }




    @EventHandler
    public void onPlayerJump(PlayerJumpEvent event) {
        Player player = event.getPlayer();
        ArmorType armorType = getFullArmorSetType(player.getInventory().getArmorContents());

        if (armorType == ArmorType.SKY) {
            int jumpCount = playerJumpCount.getOrDefault(player.getUniqueId(), 0);

            if (jumpCount == 0) {
                Vector boostUpwards = new Vector(0, 1.5, 0);
                player.setVelocity(player.getVelocity().add(boostUpwards));
                player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1f);
                playerJumpCount.put(player.getUniqueId(), 1);
            } else if (jumpCount == 1) {
                if (!player.isGliding() && player.getInventory().getChestplate() != null && player.getInventory().getChestplate().getType() == Material.ELYTRA) {
                    player.setGliding(true);
                    playerJumpCount.put(player.getUniqueId(), 2);
                }
            } else {
                playerJumpCount.put(player.getUniqueId(), 0);  // Zurücksetzen, wenn der Spieler bereits zweimal gesprungen hat
            }
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
        Location loc = player.getLocation();
        switch (armorType) {
            case SKY:
                loc.getWorld().spawnParticle(Particle.CLOUD, loc, 10, 0.5, 0.5, 0.5, 0.05);
                break;
            case FIRE:
                loc.getWorld().spawnParticle(Particle.FLAME, loc, 10, 0.5, 0.5, 0.5, 0.05);
                break;
            case WATER:
                loc.getWorld().spawnParticle(Particle.WATER_DROP, loc, 10, 0.5, 0.5, 0.5, 0.05);
                break;
            case STONE:
                loc.getWorld().spawnParticle(Particle.FLAME, loc, 10, 0.5, 0.5, 0.5, 0.05, Material.STONE.createBlockData());
                break;
            default:
                break;
        }
    }
}
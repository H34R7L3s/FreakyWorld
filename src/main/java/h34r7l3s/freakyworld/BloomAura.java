package h34r7l3s.freakyworld;


import org.bukkit.*;
import io.th0rgal.oraxen.api.OraxenItems;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class BloomAura implements Listener {

    private static final String CUSTOM_ARMORSTAND_TAG = "BLOOM_AURA_ARMORSTAND";

    private final JavaPlugin plugin;
    private final HashMap<UUID, List<ArmorStand>> auraArmorStands = new HashMap<>();
    private final int MAX_ARMORSTANDS_PER_PLAYER = 10;
    private final double BOOST_RADIUS = 5.0;

    public BloomAura(JavaPlugin plugin) {
        this.plugin = plugin;

        // Remove all existing invisible ArmorStands on plugin start
        for (ArmorStand armorStand : Bukkit.getWorlds().get(0).getEntitiesByClass(ArmorStand.class)) {
            if (armorStand.getScoreboardTags().contains(CUSTOM_ARMORSTAND_TAG)) {
                armorStand.remove();
            }
        }



        // Runnable for the aura effect
        new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID playerId : auraArmorStands.keySet()) {
                    List<ArmorStand> armorStands = auraArmorStands.get(playerId);
                    armorStands.removeIf(armorStand -> !armorStand.isValid());  // Remove invalid ArmorStands

                    for (ArmorStand armorStand : armorStands) {
                        Location loc = armorStand.getLocation();
                        boolean isNearWater = isNearWater(loc);

                        for (double x = -BOOST_RADIUS; x <= BOOST_RADIUS; x++) {
                            for (double y = -BOOST_RADIUS; y <= BOOST_RADIUS; y++) {
                                for (double z = -BOOST_RADIUS; z <= BOOST_RADIUS; z++) {
                                    if (isNearWater) {
                                        loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc.clone().add(x, y + 1, z), 1, 0.5, 0.5, 0.5);

                                    } else {
                                        Particle.DustOptions dustOptions = new Particle.DustOptions(Color.RED, 1);
                                        loc.getWorld().spawnParticle(Particle.DUST, loc.clone().add(x, y + 1, z), 2, 0.5, 0.5, 0.5, dustOptions);
                                    }
                                }
                            }
                        }

                        // Boost plant growth if near water
                        if (isNearWater) {
                            boostPlantGrowth(loc);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getItem() != null) {
            //plugin.getLogger().info("Right-clicking with an item.");

            if (isCustomItem(event.getItem())) {
                event.setUseItemInHand(PlayerInteractEvent.Result.DENY);

                UUID playerId = event.getPlayer().getUniqueId();
                Location auraLocation = event.getClickedBlock().getLocation().clone().add(0, 1, 0);
                ArmorStand armorStand = auraLocation.getWorld().spawn(auraLocation, ArmorStand.class);
                armorStand.addScoreboardTag(CUSTOM_ARMORSTAND_TAG);
                ItemStack pumpkinHead = new ItemStack(Material.CARVED_PUMPKIN);
                ItemStack leatherChestplate = new ItemStack(Material.LEATHER_CHESTPLATE);
                ItemStack leatherLeggings = new ItemStack(Material.LEATHER_LEGGINGS);
                ItemStack leatherBoots = new ItemStack(Material.LEATHER_BOOTS);

                // Rüstungsteile auf den ArmorStand setzen
                armorStand.setHelmet(pumpkinHead);
                armorStand.setChestplate(leatherChestplate);
                armorStand.setLeggings(leatherLeggings);
                armorStand.setBoots(leatherBoots);

                armorStand.setInvulnerable(false);
                armorStand.setVisible(true);
                armorStand.setGravity(false);
                armorStand.setCanPickupItems(false);
                armorStand.setBasePlate(false);
                armorStand.setRemoveWhenFarAway(false);

                auraArmorStands.putIfAbsent(playerId, new ArrayList<>());
                List<ArmorStand> playerArmorStands = auraArmorStands.get(playerId);

                // Wenn der Spieler bereits die maximale Anzahl an ArmorStands hat, entferne den ältesten
                if (playerArmorStands.size() >= MAX_ARMORSTANDS_PER_PLAYER) {
                    ArmorStand oldestArmorStand = playerArmorStands.remove(0);
                    oldestArmorStand.remove();
                }

                playerArmorStands.add(armorStand);

                // Remove the custom item from the player's inventory
                event.getPlayer().getInventory().removeItem(event.getItem());

                plugin.getLogger().info("Added aura ArmorStand for player: " + event.getPlayer().getName());
            } else {
                //plugin.getLogger().info("Item is not aura_of_bloom.");
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof ArmorStand) {
            for (List<ArmorStand> armorStands : auraArmorStands.values()) {
                if (armorStands.contains(event.getEntity())) {
                    armorStands.remove(event.getEntity());
                    plugin.getLogger().info("Removed aura ArmorStand after damage.");
                }
            }
        }
    }

    @EventHandler
    public void onArmorStandDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof ArmorStand && event.getEntity().getScoreboardTags().contains(CUSTOM_ARMORSTAND_TAG)) {
            event.getDrops().clear(); // Verhindert das Droppen von anderen Items
            ItemStack customItem = OraxenItems.getItemById("aura_of_bloom").build();
            event.getDrops().add(customItem);
            plugin.getLogger().info("Returned custom item on armor stand death.");
        }
    }


    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        if (auraArmorStands.containsKey(playerId)) {
            for (ArmorStand armorStand : auraArmorStands.get(playerId)) {
                armorStand.remove();
            }
            auraArmorStands.remove(playerId);
            plugin.getLogger().info("Removed aura ArmorStands on player quit.");
        }
    }

    private boolean isCustomItem(org.bukkit.inventory.ItemStack item) {
        String customItemId = OraxenItems.getIdByItem(item);
        return customItemId != null && customItemId.equalsIgnoreCase("aura_of_bloom");
    }

    private void boostPlantGrowth(Location location) {
        int boostedBlocks = 0; // Zählt die geboosteten Blöcke für Logging
        for (double x = -BOOST_RADIUS; x <= BOOST_RADIUS; x++) {
            for (double y = -BOOST_RADIUS; y <= BOOST_RADIUS; y++) {
                for (double z = -BOOST_RADIUS; z <= BOOST_RADIUS; z++) {
                    Block block = location.clone().add(x, y, z).getBlock();
                    Material blockType = block.getType();

                    // Beschleunigt Wachstumsstufen (z. B. Weizen, Karotten, Kartoffeln)
                    if (block.getBlockData() instanceof org.bukkit.block.data.Ageable) {
                        org.bukkit.block.data.Ageable ageable = (org.bukkit.block.data.Ageable) block.getBlockData();
                        if (ageable.getAge() < ageable.getMaximumAge()) {
                            ageable.setAge(ageable.getAge() + 1);
                            block.setBlockData(ageable);
                            boostedBlocks++;
                        }
                    }
                    // Setzt ausgewachsenen Baum nach kurzer Verzögerung bei Saplings
                    else if (isSapling(blockType)) {
                        growTreeWithDelay(block);
                        boostedBlocks++;
                    }

                    // Bambus und Zuckerrohr beschleunigen
                    else if (blockType == Material.BAMBOO || blockType == Material.BAMBOO_SAPLING) {
                        growBamboo(block);
                        boostedBlocks++;
                    }
                    else if (blockType == Material.CACTUS || blockType == Material.SUGAR_CANE) {
                        growCactusOrSugarCane(block);
                        boostedBlocks++;
                    }
                }
            }
        }
        plugin.getLogger().info("Boosted blocks count: " + boostedBlocks);
    }

    // Prüfen, ob Block ein Setzling ist
    private boolean isSapling(Material blockType) {
        return blockType == Material.OAK_SAPLING || blockType == Material.BIRCH_SAPLING ||
                blockType == Material.SPRUCE_SAPLING || blockType == Material.JUNGLE_SAPLING ||
                blockType == Material.ACACIA_SAPLING || blockType == Material.DARK_OAK_SAPLING;
    }

    // Wächst einen Baum aus einem Setzling mit einer kurzen Verzögerung
    private void growTreeWithDelay(Block saplingBlock) {
        new BukkitRunnable() {
            @Override
            public void run() {
                // Überprüfen, ob der Setzling noch da ist und dann Baum setzen
                if (saplingBlock.getType() != Material.AIR && isSapling(saplingBlock.getType())) {
                    saplingBlock.getWorld().generateTree(saplingBlock.getLocation(), getTreeType(saplingBlock.getType()));
                }
            }
        }.runTaskLater(plugin, 20L); // Wartezeit von 1 Sekunde (20 Ticks)
    }

    // Funktion zur Baumtyp-Bestimmung basierend auf dem Setzling-Typ
    private TreeType getTreeType(Material saplingType) {
        switch (saplingType) {
            case OAK_SAPLING: return TreeType.TREE;
            case BIRCH_SAPLING: return TreeType.BIRCH;
            case SPRUCE_SAPLING: return TreeType.REDWOOD;
            case JUNGLE_SAPLING: return TreeType.JUNGLE;
            case ACACIA_SAPLING: return TreeType.ACACIA;
            case DARK_OAK_SAPLING: return TreeType.DARK_OAK;
            default: return TreeType.TREE;
        }
    }

    // Beschleunigt Bambuswachstum, indem direkt ein weiterer Bambusblock hinzugefügt wird
    private void growBamboo(Block block) {
        Block above = block.getRelative(0, 1, 0);
        if (above.getType() == Material.AIR) {
            above.setType(Material.BAMBOO);
        }
    }

    // Cactus und Sugar Cane wachsen lassen, indem eine weitere Schicht hinzugefügt wird
    private void growCactusOrSugarCane(Block block) {
        Block above = block.getRelative(0, 1, 0);
        if (above.getType() == Material.AIR) {
            above.setType(block.getType());
        }
    }




    private boolean isNearWater(Location location) {
        for (double x = -BOOST_RADIUS; x <= BOOST_RADIUS; x++) {
            for (double y = -BOOST_RADIUS; y <= BOOST_RADIUS; y++) {
                for (double z = -BOOST_RADIUS; z <= BOOST_RADIUS; z++) {
                    Block block = location.clone().add(x, y, z).getBlock();
                    if (block.getType() == Material.WATER) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}

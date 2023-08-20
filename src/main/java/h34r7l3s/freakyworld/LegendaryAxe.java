package h34r7l3s.freakyworld;

import io.th0rgal.oraxen.api.OraxenItems;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class LegendaryAxe implements Listener {

    private final String ORAXEN_ID = "timberaxt";

    @EventHandler
    public void onTreeChop(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        String oraxenId = OraxenItems.getIdByItem(itemInHand);

        if (oraxenId != null && oraxenId.equals(ORAXEN_ID) && isLog(event.getBlock().getType())) {
            chopTree(event);
        }
    }

    private Player getNearestPlayer(Location location, double maxDistance) {
        double nearestDistanceSquared = maxDistance * maxDistance;
        Player nearestPlayer = null;

        for (Player player : location.getWorld().getPlayers()) {
            double distanceSquared = player.getLocation().distanceSquared(location);
            if (distanceSquared < nearestDistanceSquared) {
                nearestDistanceSquared = distanceSquared;
                nearestPlayer = player;
            }
        }

        return nearestPlayer;
    }

    private boolean isLog(Material material) {
        return material.name().endsWith("_LOG") || material.name().endsWith("_WOOD");
    }

    private void chopTree(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Set<Block> logsToBreak = new HashSet<>();
        collectLogs(event.getBlock(), logsToBreak, 0);

        for (Block log : logsToBreak) {
            log.breakNaturally();
        }

        // Pflanzen Sie nur einen Setzling an der ursprünglichen Position
        Material originalType = event.getBlock().getType();

        // Füge eine Verzögerung von 20 Ticks (1 Sekunde) hinzu, bevor du versuchst, den Setzling zu pflanzen
        Bukkit.getScheduler().runTaskLater(FreakyWorld.getPlugin(FreakyWorld.class), () -> {
            plantSapling(event.getBlock(), originalType, player);
        }, 20L);
    }





    private void collectLogs(Block block, Set<Block> logs, int depth) {
        if (depth > 10) return;  // Begrenzen Sie die Tiefe auf 10, um zu verhindern, dass zu viele Baumstämme gesammelt werden
        if (isLog(block.getType()) && !logs.contains(block)) {
            logs.add(block);
            for (BlockFace face : new BlockFace[]{BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}) {
                collectLogs(block.getRelative(face), logs, depth + 1);
            }
        }
    }



    @EventHandler
    public void onItemSpawn(ItemSpawnEvent event) {
        Player player = getNearestPlayer(event.getEntity().getLocation(), 10);

        if (player != null) {
            String oraxenId = OraxenItems.getIdByItem(player.getInventory().getItemInMainHand());
            if (oraxenId != null && oraxenId.equals(ORAXEN_ID) && player.getLocation().distance(event.getEntity().getLocation()) < 10) {  // 10 Blöcke Radius
                event.getEntity().setVelocity(player.getLocation().subtract(event.getEntity().getLocation()).toVector().normalize().multiply(0.5));
            }
        }
    }



    private void plantSapling(Block block, Material originalType, Player player) {
        Material saplingType = getSaplingForLog(originalType);
        if (saplingType != null) {
            int saplingsRequired = (saplingType == Material.DARK_OAK_SAPLING) ? 4 : 1;
            System.out.println("Saplings Required: " + saplingsRequired);
            if (player.getInventory().containsAtLeast(new ItemStack(saplingType), saplingsRequired)) {
                if (saplingsRequired == 4) {
                    Block block1 = block.getRelative(BlockFace.NORTH);
                    Block block2 = block.getRelative(BlockFace.EAST);
                    Block block3 = block1.getRelative(BlockFace.EAST);
                    if (isSuitableForPlanting(block) && isSuitableForPlanting(block1) && isSuitableForPlanting(block2) && isSuitableForPlanting(block3)) {
                        block.setType(saplingType);
                        block1.setType(saplingType);
                        block2.setType(saplingType);
                        block3.setType(saplingType);
                        player.getInventory().removeItem(new ItemStack(saplingType, 4));
                    }
                } else {
                    if (isSuitableForPlanting(block)) {
                        block.setType(saplingType);
                        player.getInventory().removeItem(new ItemStack(saplingType, 1));
                    }
                }
                tryInstantGrow(block);
            }
        }
    }




    private boolean isSuitableForPlanting(Block block) {
        Material below = block.getRelative(BlockFace.DOWN).getType();
        boolean suitable = (block.getType() == Material.AIR || block.getType() == Material.TALL_GRASS)
                && (below == Material.GRASS_BLOCK || below == Material.DIRT || below == Material.PODZOL || below == Material.COARSE_DIRT);
        System.out.println("Block is " + (suitable ? "suitable" : "not suitable") + " for planting.");
        return suitable;
    }


    private Material getSaplingForLog(Material logType) {
        switch (logType) {
            case OAK_LOG:
                return Material.OAK_SAPLING;
            case SPRUCE_LOG:
                return Material.SPRUCE_SAPLING;
            case BIRCH_LOG:
                return Material.BIRCH_SAPLING;
            case JUNGLE_LOG:
                return Material.JUNGLE_SAPLING;
            case ACACIA_LOG:
                return Material.ACACIA_SAPLING;
            case DARK_OAK_LOG:
                return Material.DARK_OAK_SAPLING;
            default:
                return null;
        }
    }

    private void tryInstantGrow(Block block) {
        Random random = new Random();
        if (random.nextDouble() < 1.0) {
            Material blockType = block.getType();
            if (blockType.name().endsWith("_SAPLING")) {
                TreeType treeType = getTreeTypeForSapling(blockType);
                if (treeType != null) {
                    block.getWorld().generateTree(block.getLocation(), treeType);
                }
            }
        }
    }

    private TreeType getTreeTypeForSapling(Material saplingType) {
        switch (saplingType) {
            case OAK_SAPLING:
                return TreeType.TREE;
            case SPRUCE_SAPLING:
                return TreeType.REDWOOD;
            case BIRCH_SAPLING:
                return TreeType.BIRCH;
            case JUNGLE_SAPLING:
                return TreeType.JUNGLE;
            case ACACIA_SAPLING:
                return TreeType.ACACIA;
            case DARK_OAK_SAPLING:
                return TreeType.DARK_OAK;
            default:
                return null;
        }
    }
}

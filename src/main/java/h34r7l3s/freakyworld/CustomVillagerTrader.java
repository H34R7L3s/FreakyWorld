package h34r7l3s.freakyworld;

import io.th0rgal.oraxen.api.OraxenItems;
import org.bukkit.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;

public class CustomVillagerTrader implements Listener {

    private final JavaPlugin plugin;
    private static Villager weaponsVillager;
    private static Villager combatVillager;
    private static Villager armorVillager;
    private static Villager specialVillager;

    public CustomVillagerTrader(JavaPlugin plugin) {
        this.plugin = plugin;
        setupWeaponsVillager();
        setupCombatVillager();
        setupArmorVillager();
        setupSpecialVillager();
    }

    public static void removeVillagers() {
        if (weaponsVillager != null) weaponsVillager.remove();
        if (combatVillager != null) combatVillager.remove();
        if (armorVillager != null) armorVillager.remove();
        if (specialVillager != null) specialVillager.remove();
    }


    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof Villager) {
            Villager villager = (Villager) event.getRightClicked();

            if (villager.equals(weaponsVillager) || villager.equals(combatVillager) || villager.equals(armorVillager) || villager.equals(specialVillager)) {
                // Überprüfen, ob der Spieler das erforderliche Item im Inventar hat
                if (!hasOraxenItem(event.getPlayer(), "silber") && !hasOraxenItem(event.getPlayer(), "gold") && !hasOraxenItem(event.getPlayer(), "eggmac")) {
                    event.getPlayer().sendMessage(ChatColor.RED + "Du bist uns unbekannt! Du " + ChatColor.DARK_PURPLE + "Freak" + ChatColor.RED + "! " + ChatColor.GOLD + "Nichts hast du an dir, was mich interessieren könnte!" + ChatColor.RED + " Also lasset uns ins Ruhe, " + ChatColor.DARK_PURPLE + "Freak" + ChatColor.RED + ".");
                    event.setCancelled(true);
                    return;
                }

                if (villager.equals(weaponsVillager)) {
                    setupWeaponsTrades(villager);
                } else if (villager.equals(combatVillager)) {
                    setupCombatTrades(villager);
                } else if (villager.equals(armorVillager)) {
                    setupArmorTrades(villager);
                } else if (villager.equals(specialVillager)) {
                    setupSpecialTrades(villager);
                }

                event.getPlayer().openMerchant(villager, true);
                event.setCancelled(true);
            }
        }
    }

    private boolean hasOraxenItem(Player player, String itemId) {
        ItemStack targetItem = OraxenItems.getItemById(itemId).build();
        if (targetItem.hasItemMeta() && targetItem.getItemMeta().hasDisplayName()) {
            String targetDisplayName = targetItem.getItemMeta().getDisplayName();
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName() && item.getItemMeta().getDisplayName().equals(targetDisplayName)) {
                    return true;
                }
            }
        }
        return false;
    }


    private void setupWeaponsVillager() {
        World world = plugin.getServer().getWorlds().get(0);
        Location loc = new Location(world, -88, 86, 34);
        weaponsVillager = spawnVillager(loc, "Tools Trader");
        setupWeaponsTrades(weaponsVillager);
    }

    private void setupCombatVillager() {
        World world = plugin.getServer().getWorlds().get(0);
        Location loc = new Location(world, -92, 86, 34);
        combatVillager = spawnVillager(loc, "Mystery Trader");
        setupCombatTrades(combatVillager);
        // Drehen des Combat Villagers um 180 Grad
        rotateVillagerLater(combatVillager);
    }

    private void setupArmorVillager() {
        World world = plugin.getServer().getWorlds().get(0);
        Location loc = new Location(world, -100, 86, 34);
        armorVillager = spawnVillager(loc, "Armor Trader");
        setupArmorTrades(armorVillager);
    }

    private void setupSpecialVillager() {
        World world = plugin.getServer().getWorlds().get(0);
        Location loc = new Location(world, -104, 86, 34);
        specialVillager = spawnVillager(loc, "Special Trader");
        setupSpecialTrades(specialVillager);
        // Drehen des Combat Villagers um 180 Grad
        rotateVillagerLater(specialVillager);
    }
    private void rotateVillagerLater(Villager villager) {
        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (villager != null && !villager.isDead()) {
                    Location loc = villager.getLocation();
                    float currentYaw = loc.getYaw();
                    float newYaw = currentYaw + 180;

                    if (newYaw > 180) newYaw -= 360;
                    else if (newYaw < -180) newYaw += 360;

                    loc.setYaw(newYaw);
                    villager.teleport(loc);
                }
            }
        }, 20L); // 20 Ticks entsprechen 1 Sekunde
    }



    private Villager spawnVillager(Location loc, String name) {
        Villager villager = (Villager) loc.getWorld().spawnEntity(loc, EntityType.VILLAGER);
        villager.setCustomName(name);
        villager.setCanPickupItems(false);
        villager.setProfession(Villager.Profession.WEAPONSMITH);
        villager.setAI(false);
        villager.setInvulnerable(true);

        // Den Villager um 90 Grad nach rechts drehen
        //float newYaw = (villager.getLocation().getYaw() + 90) % 360;
        //villager.getLocation().setYaw(newYaw);
        //villager.teleport(villager.getLocation());

        return villager;
    }
    private void setupWeaponsTrades(Villager villager) {
        ArrayList<MerchantRecipe> trades = new ArrayList<>();
        trades.add(createTrade(createSilverStack(1), OraxenItems.getItemById("silkspawn_pickaxe").build(), 1, 10));
        trades.add(createTrade(createSilverStack(15), OraxenItems.getItemById("legendary_sword").build(), 1, 5));
        trades.add(createTrade(createSilverStack(15), OraxenItems.getItemById("vampir").build(), 1, 5));
        trades.add(createTrade(createSilverStack(12), OraxenItems.getItemById("legendary_hoe").build(), 1, 5));
        trades.add(createTrade(createSilverStack(12), OraxenItems.getItemById("legendary_pickaxe1").build(), 1, 5));
        trades.add(createTrade(createSilverStack(20), OraxenItems.getItemById("timberaxt").build(), 1, 5));
        trades.add(createTrade(createSilverStack(15), OraxenItems.getItemById("legendary_pickaxe").build(), 1, 5));
        villager.setRecipes(trades);
    }

    private void setupCombatTrades(Villager villager) {
        ArrayList<MerchantRecipe> trades = new ArrayList<>();
        trades.add(createTrade(createGoldStack(2), OraxenItems.getItemById("lightning_arrow").build(), 1, 5));
        trades.add(createTrade(createGoldStack(1), createSilverStack(10), 1, 5)); // Gold in Silber tauschen
        trades.add(createTrade(createSilverStack(5), createEggMacStack(1), 1, 5)); // Silber in Eggmaccs tauschen

        villager.setRecipes(trades);
    }

    private void setupArmorTrades(Villager villager) {
        ArrayList<MerchantRecipe> trades = new ArrayList<>();
        trades.add(createTrade(createGoldStack(13), OraxenItems.getItemById("sky_crown").build(), 1, 5));
        trades.add(createTrade(createGoldStack(14), OraxenItems.getItemById("sky_guard").build(), 1, 5));
        trades.add(createTrade(createGoldStack(13), OraxenItems.getItemById("sky_boots").build(), 1, 5));
        trades.add(createTrade(createGoldStack(14), OraxenItems.getItemById("sky_leggings").build(), 1, 5));
        trades.add(createTrade(createGoldStack(13), OraxenItems.getItemById("fire_crown").build(), 1, 5));
        trades.add(createTrade(createGoldStack(14), OraxenItems.getItemById("fire_guard").build(), 1, 5));
        trades.add(createTrade(createGoldStack(13), OraxenItems.getItemById("fire_boots").build(), 1, 5));
        trades.add(createTrade(createGoldStack(14), OraxenItems.getItemById("fire_leggings").build(), 1, 5));
        trades.add(createTrade(createGoldStack(13), OraxenItems.getItemById("water_crown").build(), 1, 5));
        trades.add(createTrade(createGoldStack(14), OraxenItems.getItemById("water_guard").build(), 1, 5));
        trades.add(createTrade(createGoldStack(13), OraxenItems.getItemById("water_boots").build(), 1, 5));
        trades.add(createTrade(createGoldStack(14), OraxenItems.getItemById("water_leggings").build(), 1, 5));
        trades.add(createTrade(createGoldStack(13), OraxenItems.getItemById("stone_crown").build(), 1, 5));
        trades.add(createTrade(createGoldStack(14), OraxenItems.getItemById("stone_guard").build(), 1, 5));
        trades.add(createTrade(createGoldStack(13), OraxenItems.getItemById("stone_boots").build(), 1, 5));
        trades.add(createTrade(createGoldStack(14), OraxenItems.getItemById("stone_leggings").build(), 1, 5));
        villager.setRecipes(trades);
    }

    private void setupSpecialTrades(Villager villager) {
        ArrayList<MerchantRecipe> trades = new ArrayList<>();
        trades.add(createTrade(createGoldStack(5), OraxenItems.getItemById("aura_of_bloom").build(), 1, 5));
        trades.add(createTrade(createEggMacStack(1), new ItemStack(Material.COW_SPAWN_EGG), 1, 20)); // Annahme, dass 1 EggMac gegen 1 Cow Spawn Egg getauscht wird
        trades.add(createTrade(createEggMacStack(1), new ItemStack(Material.CHICKEN_SPAWN_EGG), 1, 20)); // Annahme, dass 1 EggMac gegen 1 Cow Spawn Egg getauscht wird
        trades.add(createTrade(createEggMacStack(1), new ItemStack(Material.PIG_SPAWN_EGG), 1, 20)); // Annahme, dass 1 EggMac gegen 1 Cow Spawn Egg getauscht wird
        trades.add(createTrade(createEggMacStack(1), new ItemStack(Material.RABBIT_SPAWN_EGG), 1, 20)); // Annahme, dass 1 EggMac gegen 1 Cow Spawn Egg getauscht wird
        trades.add(createTrade(createEggMacStack(1), new ItemStack(Material.GLOW_SQUID_SPAWN_EGG), 1, 20)); // Annahme, dass 1 EggMac gegen 1 Cow Spawn Egg getauscht wird
        trades.add(createTrade(createEggMacStack(1), new ItemStack(Material.HORSE_SPAWN_EGG), 1, 20)); // Annahme, dass 1 EggMac gegen 1 Cow Spawn Egg getauscht wird
        trades.add(createTrade(createEggMacStack(1), new ItemStack(Material.PANDA_SPAWN_EGG), 1, 20)); // Annahme, dass 1 EggMac gegen 1 Cow Spawn Egg getauscht wird
        trades.add(createTrade(createEggMacStack(1), new ItemStack(Material.PARROT_SPAWN_EGG), 1, 20)); // Annahme, dass 1 EggMac gegen 1 Cow Spawn Egg getauscht wird
        trades.add(createTrade(createEggMacStack(1), new ItemStack(Material.TURTLE_SPAWN_EGG), 1, 20)); // Annahme, dass 1 EggMac gegen 1 Cow Spawn Egg getauscht wird

        trades.add(createTrade(createEggMacStack(3), new ItemStack(Material.CREEPER_SPAWN_EGG), 1, 20)); // Annahme, dass 1 EggMac gegen 1 Cow Spawn Egg getauscht wird
        trades.add(createTrade(createEggMacStack(3), new ItemStack(Material.ZOMBIE_SPAWN_EGG), 1, 20));
        trades.add(createTrade(createEggMacStack(3), new ItemStack(Material.HUSK_SPAWN_EGG), 1, 20));
        trades.add(createTrade(createEggMacStack(3), new ItemStack(Material.WITCH_SPAWN_EGG), 1, 20));
        trades.add(createTrade(createEggMacStack(3), new ItemStack(Material.SPIDER_SPAWN_EGG), 1, 20));
        trades.add(createTrade(createEggMacStack(3), new ItemStack(Material.HOGLIN_SPAWN_EGG), 1, 20));
        trades.add(createTrade(createEggMacStack(3), new ItemStack(Material.PILLAGER_SPAWN_EGG), 1, 20));
        trades.add(createTrade(createEggMacStack(3), new ItemStack(Material.SLIME_SPAWN_EGG), 1, 20));
        trades.add(createTrade(createEggMacStack(3), new ItemStack(Material.VEX_SPAWN_EGG), 1, 20));
        trades.add(createTrade(createEggMacStack(3), new ItemStack(Material.ZOGLIN_SPAWN_EGG), 1, 20));

        trades.add(createTrade(createEggMacStack(7), new ItemStack(Material.MOOSHROOM_SPAWN_EGG), 1, 20));
        trades.add(createTrade(createEggMacStack(7), new ItemStack(Material.ENDERMAN_SPAWN_EGG), 1, 20));
        trades.add(createTrade(createEggMacStack(7), new ItemStack(Material.GHAST_SPAWN_EGG), 1, 20));
        trades.add(createTrade(createEggMacStack(7), new ItemStack(Material.WITHER_SKELETON_SPAWN_EGG), 1, 20));
        trades.add(createTrade(createEggMacStack(7), new ItemStack(Material.SHULKER_SPAWN_EGG), 1, 20));




        villager.setRecipes(trades);
    }

    private MerchantRecipe createTrade(ItemStack input, ItemStack output, int outputAmount, int maxTrades) {
        ItemStack outputCopy = output.clone();
        outputCopy.setAmount(outputAmount);
        MerchantRecipe recipe = new MerchantRecipe(outputCopy, maxTrades);
        recipe.addIngredient(input);
        return recipe;
    }

    private ItemStack createSilverStack(int amount) {
        ItemStack silverStack = OraxenItems.getItemById("silber").build();
        silverStack.setAmount(amount);
        return silverStack;
    }
    private ItemStack createEggMacStack(int amount) {
        ItemStack eggMacStack = OraxenItems.getItemById("eggmac").build(); // Annahme, dass "eggmac" die ID im Oraxen-Plugin ist
        eggMacStack.setAmount(amount);
        return eggMacStack;
    }
    private ItemStack createGoldStack(int amount) {
        ItemStack goldStack = OraxenItems.getItemById("gold").build();
        goldStack.setAmount(amount);
        return goldStack;
    }

}

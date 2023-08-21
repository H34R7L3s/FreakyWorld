package h34r7l3s.freakyworld;

import io.th0rgal.oraxen.api.OraxenItems;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
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

            if (villager.equals(weaponsVillager)) {
                setupWeaponsTrades(villager);
            } else if (villager.equals(combatVillager)) {
                setupCombatTrades(villager);
            } else if (villager.equals(armorVillager)) {
                setupArmorTrades(villager);
            } else if (villager.equals(specialVillager)) {
                setupSpecialTrades(villager);
            } else {
                return;
            }

            event.getPlayer().openMerchant(villager, true);
            event.setCancelled(true);
        }
    }

    private void setupWeaponsVillager() {
        World world = plugin.getServer().getWorlds().get(0);
        Location loc = new Location(world, -251, 86, 1850);
        weaponsVillager = spawnVillager(loc, "Tools Trader");
        setupWeaponsTrades(weaponsVillager);
    }

    private void setupCombatVillager() {
        World world = plugin.getServer().getWorlds().get(0);
        Location loc = new Location(world, -254, 86, 1849);
        combatVillager = spawnVillager(loc, "Mystery Trader");
        setupCombatTrades(combatVillager);
    }

    private void setupArmorVillager() {
        World world = plugin.getServer().getWorlds().get(0);
        Location loc = new Location(world, -257, 86, 1849);
        armorVillager = spawnVillager(loc, "Armor Trader");
        setupArmorTrades(armorVillager);
    }

    private void setupSpecialVillager() {
        World world = plugin.getServer().getWorlds().get(0);
        Location loc = new Location(world, -257, 86, 1852);
        specialVillager = spawnVillager(loc, "Special Trader");
        setupSpecialTrades(specialVillager);
    }

    private Villager spawnVillager(Location loc, String name) {
        Villager villager = (Villager) loc.getWorld().spawnEntity(loc, EntityType.VILLAGER);
        villager.setCustomName(name);
        villager.setCanPickupItems(false);
        villager.setProfession(Villager.Profession.WEAPONSMITH);
        villager.setAI(false);
        villager.setInvulnerable(true);
        return villager;
    }
    private void setupWeaponsTrades(Villager villager) {
        ArrayList<MerchantRecipe> trades = new ArrayList<>();
        trades.add(createTrade(createSilverStack(10), OraxenItems.getItemById("silkspawn_pickaxe").build(), 1, 5));
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
        villager.setRecipes(trades);
    }

    private void setupArmorTrades(Villager villager) {
        ArrayList<MerchantRecipe> trades = new ArrayList<>();
        trades.add(createTrade(createGoldStack(3), OraxenItems.getItemById("sky_crown").build(), 1, 5));
        trades.add(createTrade(createGoldStack(4), OraxenItems.getItemById("sky_guard").build(), 1, 5));
        trades.add(createTrade(createGoldStack(3), OraxenItems.getItemById("sky_boots").build(), 1, 5));
        trades.add(createTrade(createGoldStack(4), OraxenItems.getItemById("sky_leggings").build(), 1, 5));
        trades.add(createTrade(createGoldStack(3), OraxenItems.getItemById("fire_crown").build(), 1, 5));
        trades.add(createTrade(createGoldStack(4), OraxenItems.getItemById("fire_guard").build(), 1, 5));
        trades.add(createTrade(createGoldStack(3), OraxenItems.getItemById("fire_boots").build(), 1, 5));
        trades.add(createTrade(createGoldStack(4), OraxenItems.getItemById("fire_leggings").build(), 1, 5));
        trades.add(createTrade(createGoldStack(3), OraxenItems.getItemById("water_crown").build(), 1, 5));
        trades.add(createTrade(createGoldStack(4), OraxenItems.getItemById("water_guard").build(), 1, 5));
        trades.add(createTrade(createGoldStack(3), OraxenItems.getItemById("water_boots").build(), 1, 5));
        trades.add(createTrade(createGoldStack(4), OraxenItems.getItemById("water_leggings").build(), 1, 5));
        trades.add(createTrade(createGoldStack(3), OraxenItems.getItemById("stone_crown").build(), 1, 5));
        trades.add(createTrade(createGoldStack(4), OraxenItems.getItemById("stone_guard").build(), 1, 5));
        trades.add(createTrade(createGoldStack(3), OraxenItems.getItemById("stone_boots").build(), 1, 5));
        trades.add(createTrade(createGoldStack(4), OraxenItems.getItemById("stone_leggings").build(), 1, 5));
        villager.setRecipes(trades);
    }

    private void setupSpecialTrades(Villager villager) {
        ArrayList<MerchantRecipe> trades = new ArrayList<>();
        trades.add(createTrade(createGoldStack(5), OraxenItems.getItemById("aura_of_bloom").build(), 1, 5));
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

    private ItemStack createGoldStack(int amount) {
        ItemStack goldStack = OraxenItems.getItemById("gold").build();
        goldStack.setAmount(amount);
        return goldStack;
    }

}

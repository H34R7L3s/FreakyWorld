package h34r7l3s.freakyworld;


import io.th0rgal.oraxen.api.OraxenItems;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
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
    private static Villager customVillager;

    public CustomVillagerTrader(JavaPlugin plugin) {
        this.plugin = plugin;
        setupCustomVillager();
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof Villager) {
            Villager villager = (Villager) event.getRightClicked();
            Player player = event.getPlayer();

            if (villager.equals(customVillager)) {
                setupCustomTrades(villager);
                player.openMerchant(villager, true);
                event.setCancelled(true);
            }
        }
    }

    private void setupCustomVillager() {
        World world = plugin.getServer().getWorlds().get(0);  // Assuming it's the main world. Adjust if necessary.
        Location loc = new Location(world, -254, 84, 1859);
        customVillager = (Villager) world.spawnEntity(loc, EntityType.VILLAGER);
        customVillager.setCustomName("Fridolyn");
        customVillager.setCanPickupItems(false);
        customVillager.setProfession(Villager.Profession.CARTOGRAPHER);
        customVillager.setAI(false);  // This will make the villager not move.
        setupCustomTrades(customVillager);
    }
    private void setupCustomTrades(Villager villager) {
        // Create a new list for our custom trades
        ArrayList<MerchantRecipe> customTrades = new ArrayList<>();

        // Define Oraxen items
        ItemStack gold = OraxenItems.getItemById("gold").build();
        ItemStack silber = OraxenItems.getItemById("silber").build();
        ItemStack silkspawnPickaxe = OraxenItems.getItemById("silkspawn_pickaxe").build();
        ItemStack legendarySword = OraxenItems.getItemById("legendary_sword").build();
        ItemStack legendaryHoe = OraxenItems.getItemById("legendary_hoe").build();
        ItemStack legendaryPickaxe1 = OraxenItems.getItemById("legendary_pickaxe1").build();
        ItemStack legendaryPickaxe = OraxenItems.getItemById("legendary_pickaxe").build();
        ItemStack lightningArrow = OraxenItems.getItemById("lightning_arrow").build();
        ItemStack skyCrown = OraxenItems.getItemById("sky_crown").build();
        ItemStack skyGuard = OraxenItems.getItemById("sky_guard").build();
        ItemStack skyBoots = OraxenItems.getItemById("sky_boots").build();
        ItemStack skyLeggings = OraxenItems.getItemById("sky_leggings").build();
        ItemStack auraOfBloom = OraxenItems.getItemById("aura_of_bloom").build();

        // Add trades with gold and silber as currency
        customTrades.add(createTrade(gold, 5, silkspawnPickaxe, 1, 10));
        customTrades.add(createTrade(gold, 10, legendarySword, 1, 10));
        customTrades.add(createTrade(gold, 8, legendaryHoe, 1, 10));
        customTrades.add(createTrade(silber, 5, legendaryPickaxe1, 1, 10));
        customTrades.add(createTrade(silber, 7, legendaryPickaxe, 1, 10));
        customTrades.add(createTrade(silber, 3, lightningArrow, 10, 10));
        customTrades.add(createTrade(gold, 15, skyCrown, 1, 10));
        customTrades.add(createTrade(gold, 15, skyGuard, 1, 10));
        customTrades.add(createTrade(gold, 10, skyBoots, 1, 10));
        customTrades.add(createTrade(gold, 12, skyLeggings, 1, 10));
        customTrades.add(createTrade(gold, 20, auraOfBloom, 1, 10));

        villager.setRecipes(customTrades);
    }

    private MerchantRecipe createTrade(ItemStack input, int inputAmount, ItemStack output, int outputAmount, int maxTrades) {
        ItemStack inputCopy = input.clone();
        inputCopy.setAmount(inputAmount);
        ItemStack outputCopy = output.clone();
        outputCopy.setAmount(outputAmount);
        MerchantRecipe recipe = new MerchantRecipe(outputCopy, maxTrades);
        recipe.addIngredient(inputCopy);
        return recipe;
    }
    public static void removeCustomVillager() {
        if (null != customVillager) {
            customVillager.remove();
        }
    }
}




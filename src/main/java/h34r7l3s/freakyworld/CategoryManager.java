package h34r7l3s.freakyworld;

import java.util.*;

public class CategoryManager {
    public static Map<String, List<String>> categories;
    private Map<String, String> categoryModes; // Neues Feld für die Modi

    public CategoryManager() {

        categories = new HashMap<>();
        categoryModes = new HashMap<>();

        // Kategorien hinzufügen
        categories.put("Bauernmarkt", Arrays.asList("WHEAT", "CARROT", "BEETROOT", "POTATO", "MELON_SLICE", "PUMPKIN", "BAMBOO", "COCOA_BEANS", "SUGAR_CANE", "CACTUS", "KELP", "NETHER_WART", "GLOW_BERRIES", "CHORUS_FRUIT"));
        categories.put("Minenarbeit", Arrays.asList("IRON_INGOT", "GOLD_INGOT", "FLINT", "CLAY", "COAL", "EMERALD", "COPPER_INGOT", "BRICK", "NEHTERRITE_INGOT"));
        categories.put("Monsterjagd", Arrays.asList("ROTTEN_FLESH", "BONE", "BLAZE_ROD", "LEATHER", "GUNPOWDER", "SLIMEBALL", "GHAST_TEAR", "STRING", "SPIDER_EYE", "ENDER_PEARL", "MAGMA_CREAM"));
        categories.put("Angeln", Arrays.asList("COD", "SALMON", "TROPICAL_FISH", "PUFFERFISH", "INK_SAC", "POTION"));
        categories.put("Schmiedekunst", Arrays.asList("IRON_SWORD", "GOLDEN_HELMET", "BOW", "IRON_CHESTPLATE", "IRON_BOOTS", "IRON_LEGGINS", "IRON_HELMET", "CROSSBOW", "SHIELD", "IRON_SHOVEL", "IRON_AXE", "IRON_HOE", "BUCKET"));
        categories.put("Baumfäller", Arrays.asList("OAK_LOG", "SPRUCE_LOG", "BIRCH_LEAVES", "BIRCH_LOG","CHERRY_LOG","DARK_OAK_LOG", "JUNGLE_LOG", "MANGROVE_LOG","ACACIA_LEAVES","AZALEA_LEAVES", "BIRCH_LEAVES", "CHERRY_LEAVES", "DARK_OAK_LEAVES", "FLOWERING_AZALEA_LEAVES","JUNGLE_LEAVES", "MANGROVE_LEAVES", "OAK_LEAVES", "SPRUCE_LEAVES"   ));


        // Modi für jede Kategorie festlegen
        categoryModes.put("Bauernmarkt", "FreeForAll");
        categoryModes.put("Angeln", "FreeForAll");
        categoryModes.put("Baumfäller", "FreeForAll");
        categoryModes.put("Schmiedekunst", "FreeForAll");
        categoryModes.put("Monsterjagd", "FreeForAll");
        categoryModes.put("Minenarbeit", "FreeForAll");

        categoryModes.put("Bauernmarkt", "DailyTask");
        categoryModes.put("Angeln", "DailyTask");
        categoryModes.put("Baumfäller", "DailyTask");
        categoryModes.put("Schmiedekunst", "DailyTask");
        categoryModes.put("Monsterjagd", "DailyTask");
        categoryModes.put("Minenarbeit", "DailyTask");



        categoryModes.put("Bauernmarkt", "TeamMode");
        categoryModes.put("Angeln", "TeamMode");
        categoryModes.put("Baumfäller", "TeamMode");
        categoryModes.put("Schmiedekunst", "TeamMode");
        categoryModes.put("Monsterjagd", "TeamMode");
        categoryModes.put("Minenarbeit", "TeamMode");



        addCategory("Sammel 20.000 von einem Item", Collections.singletonList("DIAMOND")); // Beispiel mit Diamanten

        // Weitere Kategorien und Modi hinzufügen
    }

    public List<String> getTasksForCategory(String category) {
        List<String> items = categories.getOrDefault(category, Collections.emptyList());

        // Falls die Liste weniger als 2 Items hat, gebe die gesamte Liste zurück
        if (items.size() <= 2) {
            return new ArrayList<>(items);
        }

        // Zufällige Auswahl von zwei Items
        Collections.shuffle(items);
        return items.subList(0, 2);
    }

    public void addCategory(String categoryName, List<String> items) {
        categories.put(categoryName, items);
    }

    public void removeCategory(String categoryName) {
        categories.remove(categoryName);
    }

    public String getCategoryMode(String category) {
        return categoryModes.getOrDefault(category, "Unknown");
    }

    public List<String> getCategories() {
        return new ArrayList<>(categories.keySet());
    }

}

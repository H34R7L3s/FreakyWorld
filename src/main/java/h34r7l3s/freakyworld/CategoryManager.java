package h34r7l3s.freakyworld;

import java.util.*;

public class CategoryManager {
    private Map<String, List<String>> categories;
    private Map<String, String> categoryModes; // Neues Feld für die Modi

    public CategoryManager() {
        categories = new HashMap<>();
        categoryModes = new HashMap<>();

        // Kategorien hinzufügen
        categories.put("Bauernmarkt", Arrays.asList("WHEAT", "CARROT"));
        categories.put("Minenarbeit", Arrays.asList("IRON_INGOT", "GOLD_INGOT"));
        categories.put("Monsterjagd", Arrays.asList("ROTTEN_FLESH", "BONE"));
        categories.put("Angeln", Arrays.asList("COD", "SALMON"));
        categories.put("Schmiedekunst", Arrays.asList("IRON_SWORD", "GOLDEN_HELMET"));
        categories.put("Baumfäller", Arrays.asList("OAK_LOG", "SPRUCE_LOG"));

        // Modi für jede Kategorie festlegen
        categoryModes.put("Bauernmarkt", "FreeForAll");
        categoryModes.put("Minenarbeit", "DailyTask");
        categoryModes.put("Monsterjagd", "TeamMode");
        categoryModes.put("Angeln", "FreeForAll");
        categoryModes.put("Schmiedekunst", "DailyTask");
        categoryModes.put("Baumfäller", "TeamMode");
        addCategory("Sammel 20.000 von einem Item", Collections.singletonList("DIAMOND")); // Beispiel mit Diamanten

        // Weitere Kategorien und Modi hinzufügen
    }

    public List<String> getTasksForCategory(String category) {
        return categories.getOrDefault(category, Arrays.asList());
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

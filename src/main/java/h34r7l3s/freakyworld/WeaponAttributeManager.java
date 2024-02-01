package h34r7l3s.freakyworld;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class WeaponAttributeManager {

    private final JavaPlugin plugin;
    private final Connection dbConnection;

    public WeaponAttributeManager(JavaPlugin plugin, Connection dbConnection) {
        this.plugin = plugin;
        this.dbConnection = dbConnection;
        createWeaponAttributesTable();
    }

    private void createWeaponAttributesTable() {
        String sql = "CREATE TABLE IF NOT EXISTS weapon_attributes (" +
                "item_id VARCHAR(255)," +
                "attribute_type VARCHAR(255)," +
                "attribute_value DOUBLE," +
                "attribute_count INT," +
                "PRIMARY KEY (item_id, attribute_type)" +
                ")";

        try (Statement statement = dbConnection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            plugin.getLogger().severe("Error creating weapon attributes table: " + e.getMessage());
        }
    }

    public void addOrUpdateAttribute(UUID itemId, String attributeType, double attributeValue) {
        // Überprüfen Sie, ob itemId null ist
        if (itemId == null) {
            plugin.getLogger().severe("Cannot add/update attribute with null item_id.");
            return;
        }

        String sqlCheck = "SELECT attribute_value, attribute_count FROM weapon_attributes WHERE item_id = ? AND attribute_type = ?";
        String sqlInsertOrUpdate = "INSERT INTO weapon_attributes (item_id, attribute_type, attribute_value, attribute_count) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE attribute_value = VALUES(attribute_value), attribute_count = attribute_count + 1";

        try (PreparedStatement psCheck = dbConnection.prepareStatement(sqlCheck)) {
            psCheck.setString(1, itemId.toString());
            psCheck.setString(2, attributeType);
            ResultSet rs = psCheck.executeQuery();
            if (rs.next()) {
                // Attribut existiert bereits, update Anzahl
                try (PreparedStatement psUpdate = dbConnection.prepareStatement(sqlInsertOrUpdate)) {
                    psUpdate.setString(1, itemId.toString());
                    psUpdate.setString(2, attributeType);
                    psUpdate.setDouble(3, attributeValue);
                    psUpdate.setInt(4, rs.getInt("attribute_count") + 1);
                    psUpdate.executeUpdate();
                }
            } else {
                // Attribut existiert noch nicht, füge neu hinzu
                try (PreparedStatement psInsert = dbConnection.prepareStatement(sqlInsertOrUpdate)) {
                    psInsert.setString(1, itemId.toString());
                    psInsert.setString(2, attributeType);
                    psInsert.setDouble(3, attributeValue);
                    psInsert.setInt(4, 1);
                    psInsert.executeUpdate();
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error adding/updating attribute: " + e.getMessage());
        }
    }



    private String generateItemId() {
        String uniqueItemId;
        do {
            uniqueItemId = UUID.randomUUID().toString();
        } while (uniqueItemId == null);
        return uniqueItemId;
    }




    public Map<String, AttributeData> getAttributes(String itemId) {
        Map<String, AttributeData> attributes = new HashMap<>();
        String sql = "SELECT attribute_type, attribute_value, attribute_count FROM weapon_attributes WHERE item_id = ?";

        try (PreparedStatement ps = dbConnection.prepareStatement(sql)) {
            ps.setString(1, itemId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String type = rs.getString("attribute_type");
                double value = rs.getDouble("attribute_value");
                int count = rs.getInt("attribute_count");
                attributes.put(type, new AttributeData(value, count));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error retrieving attributes: " + e.getMessage());
        }

        return attributes;
    }

    public void setAttributes(String itemId, Map<String, AttributeData> attributes) {
        String sqlInsertOrUpdate = "INSERT INTO weapon_attributes (item_id, attribute_type, attribute_value, attribute_count) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE attribute_value = VALUES(attribute_value), attribute_count = ?";

        try (PreparedStatement psInsertOrUpdate = dbConnection.prepareStatement(sqlInsertOrUpdate)) {
            for (Map.Entry<String, AttributeData> entry : attributes.entrySet()) {
                String attributeType = entry.getKey();
                AttributeData attributeData = entry.getValue();

                psInsertOrUpdate.setString(1, itemId);
                psInsertOrUpdate.setString(2, attributeType);
                psInsertOrUpdate.setDouble(3, attributeData.getValue());
                psInsertOrUpdate.setInt(4, attributeData.getCount());
                psInsertOrUpdate.setInt(5, attributeData.getCount());

                psInsertOrUpdate.addBatch();
            }

            psInsertOrUpdate.executeBatch();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error setting attributes: " + e.getMessage());
        }
    }

    private final Map<UUID, ItemStack> temporaryItems = new HashMap<>();
    private final Map<UUID, ItemStack> originalItems = new HashMap<>();

    public void setTemporaryItem(Player player, ItemStack itemStack) {
        temporaryItems.put(player.getUniqueId(), itemStack);
    }

    public void setOriginalItem(Player player, ItemStack item) {
        originalItems.put(player.getUniqueId(), item);
    }

    public ItemStack getOriginalItem(Player player) {
        return originalItems.get(player.getUniqueId());
    }

    public void clearOriginalItem(Player player) {
        originalItems.remove(player.getUniqueId());
    }

    private final Map<UUID, ItemStack> attributeItems = new HashMap<>();

    public void clearAttributeItem(Player player) {
        attributeItems.remove(player.getUniqueId());
    }

    public void setAttributeItem(Player player, ItemStack attributeItem) {
        attributeItems.put(player.getUniqueId(), attributeItem);
    }

    public ItemStack getAttributeItem(Player player) {
        return attributeItems.get(player.getUniqueId());
    }

    public ItemStack getTemporaryItem(Player player) {
        return temporaryItems.get(player.getUniqueId());
    }

    // Methode, um das temporäre Item zu löschen
    public void clearTemporaryItem(Player player) {
        temporaryItems.remove(player.getUniqueId());
    }

}

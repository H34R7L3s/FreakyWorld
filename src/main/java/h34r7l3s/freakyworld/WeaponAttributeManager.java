package h34r7l3s.freakyworld;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

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
                "PRIMARY KEY (item_id, attribute_type)" +
                ")";

        try (Statement statement = dbConnection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            plugin.getLogger().severe("Error creating weapon attributes table: " +e.getMessage());
        }
    }
    public void addOrUpdateAttribute(String itemId, String attributeType, double attributeValue) {
        String sql = "INSERT INTO weapon_attributes (item_id, attribute_type, attribute_value) " +
                "VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE attribute_value = VALUES(attribute_value)";

        try (PreparedStatement ps = dbConnection.prepareStatement(sql)) {
            ps.setString(1, itemId);
            ps.setString(2, attributeType);
            ps.setDouble(3, attributeValue);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error adding/updating attribute: " + e.getMessage());
        }
    }

    public Map<String, Double> getAttributes(String itemId) {
        Map<String, Double> attributes = new HashMap<>();
        String sql = "SELECT attribute_type, attribute_value FROM weapon_attributes WHERE item_id = ?";

        try (PreparedStatement ps = dbConnection.prepareStatement(sql)) {
            ps.setString(1, itemId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String type = rs.getString("attribute_type");
                double value = rs.getDouble("attribute_value");
                attributes.put(type, value);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error retrieving attributes: " + e.getMessage());
        }

        return attributes;
    }
}


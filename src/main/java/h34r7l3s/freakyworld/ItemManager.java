package h34r7l3s.freakyworld;

import org.bukkit.inventory.ItemStack;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Base64;

public class ItemManager {
    private DataSource dataSource;

    public ItemManager(DataSource dataSource) {
        this.dataSource = dataSource;
        createItemsTable(); // Create items table in the database
    }

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    private void createItemsTable() {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS guild_items (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "guild_name VARCHAR(255)," +
                "item_data TEXT" +
                ")";

        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(createTableSQL);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void saveItem(ItemStack item, String guildName) {
        String insertSQL = "INSERT INTO guild_items (guild_name, item_data) VALUES (?, ?)";

        try (Connection connection = getConnection(); PreparedStatement preparedStatement = connection.prepareStatement(insertSQL, Statement.RETURN_GENERATED_KEYS)) {
            preparedStatement.setString(1, guildName);
            String itemData = serializeItemStack(item);
            preparedStatement.setString(2, itemData);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public ItemStack loadItem(int itemId) {
        String selectSQL = "SELECT item_data FROM guild_items WHERE id = ?";

        try (Connection connection = getConnection(); PreparedStatement preparedStatement = connection.prepareStatement(selectSQL)) {
            preparedStatement.setInt(1, itemId);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                String itemData = resultSet.getString("item_data");
                return deserializeItemStack(itemData);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    public String serializeItemStack(ItemStack itemStack) {
        if (itemStack == null) {
            return null;
        }

        try {
            byte[] serializedItemStack = ItemSerializer.serialize(itemStack);
            return Base64.getEncoder().encodeToString(serializedItemStack);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public ItemStack deserializeItemStack(String itemData) {
        if (itemData == null) {
            return null;
        }

        try {
            byte[] serializedItemStack = Base64.getDecoder().decode(itemData);
            return ItemSerializer.deserialize(serializedItemStack);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
}

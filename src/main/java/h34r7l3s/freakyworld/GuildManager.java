package h34r7l3s.freakyworld;


import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

//import static io.th0rgal.oraxen.shaded.playeranimator.api.PlayerAnimatorPlugin.plugin;

//import static io.th0rgal.oraxen.shaded.playeranimator.api.PlayerAnimatorPlugin.plugin;


import java.util.Collection;
import java.util.HashMap;
import java.util.Map;


public class GuildManager {
    private DataSource dataSource;
    private ItemManager itemManager;
    private final JavaPlugin plugin;

    public GuildManager(DataSource dataSource, JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataSource = dataSource;
        this.itemManager = new ItemManager(dataSource);
    }

    public Connection getConnection() throws SQLException {
        Connection dbConnection = dataSource.getConnection();
        if (dbConnection == null || dbConnection.isClosed()) {
            throw new SQLException("Database connection closed");
        }
        return dbConnection;
    }
    public DataSource getDataSource() {
        return this.dataSource;
    }

    // Alle Methoden anpassen, um getConnection() zu verwenden und die Verbindungen ordnungsgemäß zu schließen
    // Beispiel:
    public void saveItemToGuild(String guildName, ItemStack item) {
        String insertSQL = "INSERT INTO guild_items (guild_name, item_data) VALUES (?, ?)";
        try (Connection dbConnection = getConnection();
             PreparedStatement preparedStatement = dbConnection.prepareStatement(insertSQL, Statement.RETURN_GENERATED_KEYS)) {
            preparedStatement.setString(1, guildName);
            String itemData = itemManager.serializeItemStack(item);
            preparedStatement.setString(2, itemData);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void removeGuildItem(String guildName, ItemStack item) {
        String deleteSQL = "DELETE FROM guild_items WHERE guild_name = ? AND item_data = ?";
        try (Connection dbConnection = getConnection();
             PreparedStatement preparedStatement = dbConnection.prepareStatement(deleteSQL)) {
            String itemData = itemManager.serializeItemStack(item);
            preparedStatement.setString(1, guildName);
            preparedStatement.setString(2, itemData);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void removeAllGuildItems(String guildName) {
        String deleteSQL = "DELETE FROM guild_items WHERE guild_name = ?";
        try (Connection dbConnection = getConnection();
             PreparedStatement preparedStatement = dbConnection.prepareStatement(deleteSQL)) {
            preparedStatement.setString(1, guildName);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean createGuild(String guildName, String leaderName) {
        try (Connection dbConnection = getConnection()) {
            ItemManager itemManager = new ItemManager(dataSource);
            Guild guild = new Guild(guildName, leaderName, itemManager);
            guild.addMember(leaderName, Guild.GuildRank.LEADER);
            guild.setDescription("Beschreibung deiner Gilde hier...");
            GuildSaver guildSaver = new GuildSaver(plugin, dataSource);
            guildSaver.saveGuildData(guild);
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    public void deleteGuild(String guildName) {
        try (Connection dbConnection = getConnection()) {
            String deleteTasksSql = "DELETE FROM guild_tasks WHERE guild_name = ?";
            try (PreparedStatement deleteTasksStmt = dbConnection.prepareStatement(deleteTasksSql)) {
                deleteTasksStmt.setString(1, guildName);
                deleteTasksStmt.executeUpdate();
            }
            String deleteItemsSql = "DELETE FROM guild_items WHERE guild_name = ?";
            try (PreparedStatement deleteItemsStmt = dbConnection.prepareStatement(deleteItemsSql)) {
                deleteItemsStmt.setString(1, guildName);
                deleteItemsStmt.executeUpdate();
            }
            String deleteGuildSql = "DELETE FROM guilds WHERE name = ?";
            try (PreparedStatement deleteGuildStmt = dbConnection.prepareStatement(deleteGuildSql)) {
                deleteGuildStmt.setString(1, guildName);
                deleteGuildStmt.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Löschen der Gilde aus der Datenbank", e);
        }
    }

    public List<ItemStack> getGuildItems(String guildName) {
        List<ItemStack> items = new ArrayList<>();
        String selectSQL = "SELECT item_data FROM guild_items WHERE guild_name = ?";
        try (Connection dbConnection = getConnection();
             PreparedStatement preparedStatement = dbConnection.prepareStatement(selectSQL)) {
            preparedStatement.setString(1, guildName);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                String itemData = resultSet.getString("item_data");
                ItemStack item = itemManager.deserializeItemStack(itemData);
                if (item != null) {
                    items.add(item);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return items;
    }
    public UUID getGuildLeader(String guildName) {
        Guild guild = getGuild(guildName);
        if (guild != null) {
            String leaderName = guild.getLeader();
            Player leaderPlayer = Bukkit.getPlayerExact(leaderName);
            if (leaderPlayer != null) {
                return leaderPlayer.getUniqueId(); // Hole die UUID des Spielers
            } else {
                // Gildenleiter ist offline, UUID aus der Datenbank holen
                try (Connection dbConnection = getConnection()) {
                    String query = "SELECT uuid FROM players WHERE player_name = ?"; // Verwende hier die korrekte Spalte
                    try (PreparedStatement stmt = dbConnection.prepareStatement(query)) {
                        stmt.setString(1, leaderName);
                        try (ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) {
                                return UUID.fromString(rs.getString("uuid"));
                            }
                        }
                    }
                } catch (SQLException e) {
                    plugin.getLogger().severe("Fehler beim Abrufen der UUID des Gildenleiters: " + e.getMessage());
                }
            }
        }
        return null; // Falls der Gildenleiter nicht gefunden wird
    }


    public Guild getGuild(String guildName) {
        try (Connection dbConnection = getConnection()) {
            GuildSaver guildSaver = new GuildSaver(plugin, dataSource);
            return guildSaver.loadGuildData(guildName);
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }
    public boolean isPlayerInGuild(String playerName) {
        return getPlayerGuild(playerName) != null;
    }



    public Guild getPlayerGuild(String playerName) {
        String selectSQL = "SELECT name FROM guilds WHERE leader = ? OR members LIKE ?";
        try (Connection dbConnection = getConnection();
             PreparedStatement preparedStatement = dbConnection.prepareStatement(selectSQL)) {
            preparedStatement.setString(1, playerName);
            preparedStatement.setString(2, "%" + playerName + "%");
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                String guildName = resultSet.getString("name");
                return getGuild(guildName);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Abrufen der Gildeninformationen für Spieler", e);
        }
        return null;
    }


    public List<Guild> getAllGuilds() {
        List<Guild> guilds = new ArrayList<>();
        String selectSQL = "SELECT name FROM guilds";
        try (Connection dbConnection = getConnection();
             PreparedStatement preparedStatement = dbConnection.prepareStatement(selectSQL)) {
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                String guildName = resultSet.getString("name");
                guilds.add(getGuild(guildName));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Abrufen der Gildenliste", e);
        }
        return guilds;
    }

    public void addMemberToGuild(String guildName, String playerName) {
        try (Connection dbConnection = getConnection()) {
            Guild guild = getGuild(guildName);
            if (guild != null) {
                guild.addMember(playerName, Guild.GuildRank.MEMBER);
                GuildSaver guildSaver = new GuildSaver(plugin, dataSource);
                guildSaver.saveGuildData(guild);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void removeMemberFromGuild(String guildName, String playerName) {
        try (Connection dbConnection = getConnection()) {
            Guild guild = getGuild(guildName);
            if (guild != null) {
                guild.removeMember(playerName);
                GuildSaver guildSaver = new GuildSaver(plugin, dataSource);
                guildSaver.saveGuildData(guild);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void saveGuildData(Guild guild) {
        try (Connection dbConnection = getConnection()) {
            GuildSaver guildSaver = new GuildSaver(plugin, dataSource);
            guildSaver.saveGuildData(guild);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean createAlliance(String guildName1, String guildName2, String status) {
        String insertSQL = "INSERT INTO alliances (guild_name_1, guild_name_2, status) VALUES (?, ?, ?)";
        try (Connection dbConnection = getConnection();
             PreparedStatement preparedStatement = dbConnection.prepareStatement(insertSQL)) {
            preparedStatement.setString(1, guildName1);
            preparedStatement.setString(2, guildName2);
            preparedStatement.setString(3, status);
            preparedStatement.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean updateAllianceStatus(String guildName1, String guildName2, String newStatus) {
        String updateSQL = "UPDATE alliances SET status = ? WHERE (guild_name_1 = ? AND guild_name_2 = ?) OR (guild_name_1 = ? AND guild_name_2 = ?)";
        try (Connection dbConnection = getConnection();
             PreparedStatement preparedStatement = dbConnection.prepareStatement(updateSQL)) {
            preparedStatement.setString(1, newStatus);
            preparedStatement.setString(2, guildName1);
            preparedStatement.setString(3, guildName2);
            preparedStatement.setString(4, guildName2);
            preparedStatement.setString(5, guildName1);
            preparedStatement.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean deleteAlliance(String guildName1, String guildName2) {
        String deleteSQL = "DELETE FROM alliances WHERE (guild_name_1 = ? AND guild_name_2 = ?) OR (guild_name_1 = ? AND guild_name_2 = ?)";
        try (Connection dbConnection = getConnection();
             PreparedStatement preparedStatement = dbConnection.prepareStatement(deleteSQL)) {
            preparedStatement.setString(1, guildName1);
            preparedStatement.setString(2, guildName2);
            preparedStatement.setString(3, guildName2);
            preparedStatement.setString(4, guildName1);
            preparedStatement.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public List<String[]> getAlliancesOfGuild(String guildName) {
        List<String[]> alliances = new ArrayList<>();
        String selectSQL = "SELECT * FROM alliances WHERE guild_name_1 = ? OR guild_name_2 = ?";
        try (Connection dbConnection = getConnection();
             PreparedStatement preparedStatement = dbConnection.prepareStatement(selectSQL)) {
            preparedStatement.setString(1, guildName);
            preparedStatement.setString(2, guildName);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                String guild1 = resultSet.getString("guild_name_1");
                String guild2 = resultSet.getString("guild_name_2");
                String status = resultSet.getString("status");
                alliances.add(new String[]{guild1, guild2, status});
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return alliances;

    public void deleteGuild(String name) {
        guilds.remove(name);

    }

    public boolean isPlayerAllowedToManageAlliances(String playerName, String guildName) {
        try (Connection dbConnection = getConnection()) {
            Guild guild = getGuild(guildName);
            if (guild != null) {
                return guild.getLeader().equals(playerName) || guild.getMemberRank(playerName) == Guild.GuildRank.OFFICER;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public String getAllianceStatus(String playerGuildName, String otherGuildName) {
        String selectSQL = "SELECT status FROM alliances WHERE (guild_name_1 = ? AND guild_name_2 = ?) OR (guild_name_1 = ? AND guild_name_2 = ?)";
        try (Connection dbConnection = getConnection();
             PreparedStatement preparedStatement = dbConnection.prepareStatement(selectSQL)) {
            preparedStatement.setString(1, playerGuildName);
            preparedStatement.setString(2, otherGuildName);
            preparedStatement.setString(3, otherGuildName);
            preparedStatement.setString(4, playerGuildName);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getString("status");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "Neutral"; // Standardwert, falls keine Allianz gefunden wird
    }






}

package h34r7l3s.freakyworld;

import io.th0rgal.oraxen.api.OraxenItems;
import io.th0rgal.oraxen.items.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.conversations.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FreakyBuilder implements Listener {
    private final JavaPlugin plugin;
    private Connection connection;
    private final Object dbLock = new Object();
    private final String dbFilePath = "builder_projects.db"; // Pfad zur neuen Datenbank

    private final GameLoop gameLoop; // Referenz zur GameLoop-Klasse, um Freaky_XP und Freaky_Coins zu verwalten
    private final Map<UUID, Integer> openNegotiations = new HashMap<>(); // Verfolgung von offenen Verhandlungen
    private final Map<UUID, String> projectTitleMap = new HashMap<>(); // Speichert die Titel der Projekte zwischen


    public FreakyBuilder(JavaPlugin plugin, GameLoop gameLoop) {
        this.plugin = plugin;
        this.gameLoop = gameLoop;
        plugin.getServer().getPluginManager().registerEvents(this, plugin); // Registriere Events
        openConnection();
        createTables();
        spawnBuilderVillager();
    }

    // Methode zur Öffnung der Datenbankverbindung
    private void openConnection() {
        synchronized (dbLock) {
            try {
                if (connection == null || connection.isClosed()) {
                    connection = DriverManager.getConnection("jdbc:sqlite:" + plugin.getDataFolder().getPath() + "/" + dbFilePath);
                    plugin.getLogger().info("Datenbankverbindung für FreakyBuilder geöffnet.");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    // Methode zur Schließung der Datenbankverbindung bei Plugin-Deaktivierung
    public void closeConnection() {
        synchronized (dbLock) {
            try {
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                    plugin.getLogger().info("Datenbankverbindung für FreakyBuilder geschlossen.");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    // Methode zur Erstellung der notwendigen Tabellen in der neuen Datenbank
    private void createTables() {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS builder_projects (" +
                    "project_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "player_uuid VARCHAR(36)," +
                    "title VARCHAR(100)," +
                    "description TEXT," +
                    "player_status VARCHAR(20)," +
                    "project_status VARCHAR(20)," +
                    "reward_freaky_xp INTEGER," +
                    "reward_freaky_coins INTEGER)");
            plugin.getLogger().info("Tabelle 'builder_projects' erstellt oder existiert bereits.");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS builder_requests (" +
                    "request_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "project_id INTEGER," +
                    "player_uuid VARCHAR(36)," +
                    "proposed_xp INTEGER," +
                    "FOREIGN KEY(project_id) REFERENCES builder_projects(project_id))");
            plugin.getLogger().info("Tabelle 'builder_requests' erstellt oder existiert bereits.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Fehler beim Erstellen der Tabellen für FreakyBuilder: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Villager spawnen an den gewünschten Koordinaten
    private void spawnBuilderVillager() {
        Location location = new Location(Bukkit.getWorld("world"), 5, 122, -30);
        Villager builderVillager = (Villager) location.getWorld().spawnEntity(location, EntityType.VILLAGER);
        builderVillager.setCustomName(ChatColor.GOLD + "Baumeister");
        builderVillager.setCustomNameVisible(true);
        builderVillager.setProfession(Villager.Profession.MASON);
        builderVillager.setVillagerLevel(5);
        builderVillager.setAI(false);
        builderVillager.setInvulnerable(true);
        builderVillager.setSilent(true);  // Der Villager gibt keine Geräusche von sich
        builderVillager.setCollidable(false); // Verhindert Kollisionen
        plugin.getLogger().info("Baumeister-Villager wurde bei den Koordinaten -75 82 31 gespawnt.");
    }

    // Methode zum Entfernen des Villagers
    public void removeBuilderVillager() {
        Location location = new Location(Bukkit.getWorld("world"), 5, 122, -30);
        for (Villager villager : location.getWorld().getEntitiesByClass(Villager.class)) {
            if (villager.getLocation().equals(location) && "Baumeister".equals(villager.getCustomName())) {
                villager.remove();
                plugin.getLogger().info("Baumeister-Villager wurde entfernt.");
                break;
            }
        }
    }

    // Event-Handler für die Interaktion mit dem Baumeister-Villager
    // Boolean zum Aktivieren oder Deaktivieren des Villager-Interaktionsmenüs
    private boolean isCityBuilt = false;

    @EventHandler
    public void handleVillagerInteraction(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof Villager) {
            Villager villager = (Villager) event.getRightClicked();

            // Überprüfen, ob der Villager der Baumeister ist
            if (villager.getCustomName() != null && villager.getCustomName().equals(ChatColor.GOLD + "Baumeister")) {
                event.setCancelled(true);  // Verhindert das Öffnen der normalen Villager-Handels-UI
                Player player = event.getPlayer();

                if (isCityBuilt) {
                    // Stadt ist fertiggestellt, Villager öffnet das Hauptmenü
                    openMainUI(player);
                } else {
                    // Stadt ist noch nicht fertiggestellt, Spieler wird ermutigt, zu bauen
                    player.sendMessage(ChatColor.DARK_GREEN + "Der Baumeister blickt auf die leeren Ländereien und spricht:");
                    player.sendMessage(ChatColor.GRAY + "\"Eine prachtvolle Stadt soll hier entstehen, direkt am Herzen unserer Welt!\"");
                    player.sendMessage(ChatColor.DARK_GREEN + "Er lächelt weise:");
                    player.sendMessage(ChatColor.GRAY + "\"Erschaffe eine Stadt um diesen Ort, baue Monumente und Straßen, die unsere Gemeinschaft stärken.\"");
                    player.sendMessage(ChatColor.YELLOW + "Nur wenn die Stadt vollendet ist, wird der Baumeister seine Dienste anbieten!");
                }
            }
        }
    }


    // Haupt-UI: Bietet dem Spieler die Auswahl, ob er Aufträge sehen oder einen neuen erstellen möchte
    private void openMainUI(Player player) {
        Inventory mainUI = Bukkit.createInventory(null, 9, ChatColor.DARK_AQUA + "Baumeister Menü");

        ItemStack viewProjectsItem = new ItemStack(Material.BOOK);
        ItemMeta viewMeta = viewProjectsItem.getItemMeta();
        if (viewMeta != null) {
            viewMeta.setDisplayName(ChatColor.GREEN + "Offene Aufträge anzeigen");
            viewMeta.setLore(List.of(ChatColor.YELLOW + "Klicke hier, um alle offenen Aufträge zu sehen."));
            viewProjectsItem.setItemMeta(viewMeta);
        }

        ItemStack createProjectItem = new ItemStack(Material.PAPER);
        ItemMeta createMeta = createProjectItem.getItemMeta();
        if (createMeta != null) {
            createMeta.setDisplayName(ChatColor.GOLD + "Neuen Auftrag erstellen");
            createMeta.setLore(List.of(ChatColor.YELLOW + "Klicke hier, um einen neuen Auftrag zu erstellen."));
            createProjectItem.setItemMeta(createMeta);
        }

        mainUI.setItem(3, viewProjectsItem);
        mainUI.setItem(5, createProjectItem);

        player.openInventory(mainUI);
    }

    // Event-Handler für das Klicken in den Inventaren
    @EventHandler
    public void handleInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        Inventory inventory = event.getInventory();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        String inventoryTitle = event.getView().getTitle();

        // Verhindert das Entfernen des Papiers aus der Anfrage-UI
        if (inventoryTitle.equals(ChatColor.DARK_AQUA + "Erhaltene Anfragen") && clickedItem.getType() == Material.PAPER) {


            event.setCancelled(true);

            // Verhindert das Entfernen der Barriere
            if (clickedItem.getType() == Material.BARRIER) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Du kannst die Barriere nicht entfernen.");
                return;
            }

            int requestId = getRequestIdFromInventory(inventory, clickedItem);
            if (requestId != -1) {
                openRequestDecisionUI(player, requestId);
            } else {
                player.sendMessage(ChatColor.RED + "Anfrage konnte nicht identifiziert werden.");
            }
            return;
        }

        // Verhindert das Verändern des ersten Slots in der "Anfrage stellen"-UI
        if (inventoryTitle.equals(ChatColor.DARK_AQUA + "Anfrage stellen") && event.getSlot() == 0) {
            event.setCancelled(true);
            return;
        }

        // Restliche Logik für andere Inventare wie Baumeister Menü, Offene Projekte usw.
        if (inventoryTitle.equals(ChatColor.DARK_AQUA + "Baumeister Menü")) {
            event.setCancelled(true);

            if (clickedItem.getItemMeta() != null && clickedItem.getItemMeta().getDisplayName().equals(ChatColor.GREEN + "Offene Aufträge anzeigen")) {
                openProjectOverview(player);
                player.closeInventory();
            } else if (clickedItem.getItemMeta() != null && clickedItem.getItemMeta().getDisplayName().equals(ChatColor.GOLD + "Neuen Auftrag erstellen")) {
                if (hasAuftragsbuch(player)) {
                    initiateProjectCreationConversation(player);
                } else {
                    player.sendMessage(ChatColor.RED + "Du benötigst ein Auftragsbuch, um einen neuen Auftrag zu erstellen.");
                }
                player.closeInventory();
            }
        } else if (inventoryTitle.equals(ChatColor.DARK_AQUA + "Offene Projekte")) {
            event.setCancelled(true);
            int projectId = getProjectIdFromInventory(inventory, clickedItem);
            if (projectId != -1) {
                manageProject(player, projectId);
            } else {
                player.sendMessage(ChatColor.RED + "Projekt konnte nicht identifiziert werden.");
            }
        } else if (inventoryTitle.equals(ChatColor.DARK_AQUA + "Anfrage stellen")) {
            event.setCancelled(true);

            if (clickedItem.getItemMeta() != null && clickedItem.getItemMeta().getDisplayName().equals(ChatColor.GREEN + "Anfrage stellen")) {
                initiateRequestConversation(player, inventory, clickedItem);
                player.closeInventory();
            }
        } else if (inventoryTitle.equals(ChatColor.DARK_AQUA + "Anfrage bearbeiten")) {
            event.setCancelled(true);

            if (clickedItem.getItemMeta() != null && clickedItem.getItemMeta().getDisplayName().equals(ChatColor.GREEN + "Anfrage annehmen")) {
                int requestId = getRequestIdFromInventory(inventory, clickedItem);
                if (requestId != -1) {
                    handleRequestAcceptance(player, requestId);
                } else {
                    player.sendMessage(ChatColor.RED + "Anfrage konnte nicht identifiziert werden.");
                }
                player.closeInventory();
            } else if (clickedItem.getItemMeta() != null && clickedItem.getItemMeta().getDisplayName().equals(ChatColor.RED + "Anfrage ablehnen")) {
                int requestId = getRequestIdFromInventory(inventory, clickedItem);
                if (requestId != -1) {
                    rejectRequest(player, requestId);
                } else {
                    player.sendMessage(ChatColor.RED + "Anfrage konnte nicht identifiziert werden.");
                }
                player.closeInventory();
            }
        }
    }


    // Methode zum Akzeptieren einer Anfrage durch den Auftragsersteller
    private void handleRequestAcceptance(Player player, int requestId) {
        openConnection();
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT project_id, player_uuid, proposed_xp FROM builder_requests WHERE request_id = ?")) {
            stmt.setInt(1, requestId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                int projectId = rs.getInt("project_id");
                UUID requesterUUID = UUID.fromString(rs.getString("player_uuid"));
                int proposedXP = rs.getInt("proposed_xp");

                // Überprüfen, ob der Spieler das Auftragsbuch hat
                if (hasAuftragsbuch(player)) {
                    // Anfrage akzeptieren, Projektstatus aktualisieren und Anfrage löschen
                    updateProjectStatus(projectId, "angenommen", "in_bearbeitung");
                    removeRequest(requestId);

                    player.sendMessage(ChatColor.GREEN + "Du hast die Anfrage angenommen. Das Projekt befindet sich jetzt in Bearbeitung.");
                    // Verknüpfen von Spieler und Projekt
                    playerProjectMap.put(requesterUUID, projectId);
                    projectRequesterMap.put(projectId, requesterUUID);

                    // Hier könnte der XP-Abzug oder die Belohnung erfolgen
                } else {
                    player.sendMessage(ChatColor.RED + "Du benötigst ein Auftragsbuch, um die Anfrage anzunehmen.");
                }
            }
        } catch (SQLException e) {
            player.sendMessage(ChatColor.RED + "Fehler beim Annehmen der Anfrage. Bitte versuche es erneut.");
            e.printStackTrace();
        } finally {
            closeConnection();
        }
    }


    private void openRequestDecisionUI(Player player, int requestId) {
        synchronized (dbLock) {
            openConnection();
            Inventory decisionInventory = Bukkit.createInventory(null, 9, ChatColor.DARK_AQUA + "Anfrage bearbeiten");

            // Hole die Anfrageinformationen aus der Datenbank
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT project_id, proposed_xp FROM builder_requests WHERE request_id = ?")) {
                stmt.setInt(1, requestId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    int projectId = rs.getInt("project_id");
                    int proposedXP = rs.getInt("proposed_xp");

                    // Informationen zur Anfrage anzeigen
                    ItemStack infoItem = new ItemStack(Material.PAPER);
                    ItemMeta meta = infoItem.getItemMeta();
                    if (meta != null) {
                        meta.setDisplayName(ChatColor.GOLD + "Anfrage Information");
                        meta.setLore(List.of(
                                ChatColor.YELLOW + "Projekt ID: " + projectId,
                                ChatColor.GREEN + "Vorgeschlagene XP: " + proposedXP
                        ));
                        infoItem.setItemMeta(meta);
                    }
                    decisionInventory.setItem(4, infoItem);

                    // Hinzufügen der Annahme- und Ablehnungsbuttons
                    ItemStack acceptItem = new ItemStack(Material.GREEN_WOOL);
                    ItemMeta acceptMeta = acceptItem.getItemMeta();
                    if (acceptMeta != null) {
                        acceptMeta.setDisplayName(ChatColor.GREEN + "Anfrage annehmen");
                        acceptItem.setItemMeta(acceptMeta);
                    }
                    decisionInventory.setItem(6, acceptItem);

                    ItemStack rejectItem = new ItemStack(Material.RED_WOOL);
                    ItemMeta rejectMeta = rejectItem.getItemMeta();
                    if (rejectMeta != null) {
                        rejectMeta.setDisplayName(ChatColor.RED + "Anfrage ablehnen");
                        rejectItem.setItemMeta(rejectMeta);
                    }
                    decisionInventory.setItem(2, rejectItem);

                    player.openInventory(decisionInventory);
                }
            } catch (SQLException e) {
                player.sendMessage(ChatColor.RED + "Fehler beim Laden der Anfragen. Bitte versuche es erneut.");
                e.printStackTrace();
            } finally {
                closeConnection();
            }
        }
    }


    private void initiateProjectCreationConversation(Player player) {
        ConversationFactory factory = new ConversationFactory(plugin);
        Conversation conversation = factory
                .withFirstPrompt(new ProjectTitlePrompt())
                .withLocalEcho(true)
                .withTimeout(30)
                .thatExcludesNonPlayersWithMessage("Nur Spieler können Aufträge erstellen.")
                .buildConversation(player);
        conversation.begin();
    }

    private class ProjectTitlePrompt extends StringPrompt {
        @Override
        public String getPromptText(ConversationContext context) {
            return ChatColor.GOLD + "Bitte gib den Titel deines Bauprojekts ein:";
        }

        @Override
        public Prompt acceptInput(ConversationContext context, String input) {
            Player player = (Player) context.getForWhom();
            projectTitleMap.put(player.getUniqueId(), input);
            return new ProjectDescriptionPrompt();
        }
    }

    private class ProjectDescriptionPrompt extends StringPrompt {
        @Override
        public String getPromptText(ConversationContext context) {
            return ChatColor.GOLD + "Bitte gib eine Beschreibung für dein Bauprojekt ein:";
        }

        @Override
        public Prompt acceptInput(ConversationContext context, String input) {
            Player player = (Player) context.getForWhom();
            String title = projectTitleMap.get(player.getUniqueId());
            addProject(player, title, input);
            player.sendMessage(ChatColor.GREEN + "Dein Bauprojekt wurde erfolgreich erstellt!");
            return END_OF_CONVERSATION;
        }
    }

    // Überprüft, ob der Spieler das "auftragsbuch" im Inventar hat
    // Überprüft, ob der Spieler das "auftragsbuch" im Inventar hat
    private boolean hasAuftragsbuch(Player player) {
        String expectedOraxenId = "auftragsbuch"; // ID des Oraxen-Items

        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null) {
                String oraxenId = OraxenItems.getIdByItem(item);
                if (oraxenId != null && oraxenId.equals(expectedOraxenId)) {
                    player.sendMessage(ChatColor.GREEN + "Das Auftragsbuch wurde erkannt. Oraxen-ID: " + oraxenId); // Debug-Ausgabe
                    return true;
                } else {
                    //player.sendMessage(ChatColor.RED + "Item im Inventar erkannt, aber keine Übereinstimmung: " + (oraxenId != null ? oraxenId : "null")); // Debug-Ausgabe
                }
            }
        }

        player.sendMessage(ChatColor.RED + "Das Auftragsbuch wurde nicht gefunden."); // Debug-Ausgabe
        return false;
    }


    // UI für das Erstellen eines neuen Bauprojekts
    private void openProjectCreationUI(Player player) {
        Inventory projectCreationInventory = Bukkit.createInventory(null, 9, ChatColor.DARK_AQUA + "Neues Bauprojekt");

        // Stelle sicher, dass das UI korrekt aufgebaut ist
        ItemStack titleItem = new ItemStack(Material.PAPER);
        ItemMeta titleMeta = titleItem.getItemMeta();
        if (titleMeta != null) {
            titleMeta.setDisplayName(ChatColor.GOLD + "Titel des Projekts eingeben");
            titleItem.setItemMeta(titleMeta);
        }

        ItemStack descriptionItem = new ItemStack(Material.PAPER);
        ItemMeta descriptionMeta = descriptionItem.getItemMeta();
        if (descriptionMeta != null) {
            descriptionMeta.setDisplayName(ChatColor.GOLD + "Beschreibung des Projekts eingeben");
            descriptionItem.setItemMeta(descriptionMeta);
        }

        ItemStack createProjectItem = new ItemStack(Material.GREEN_WOOL);
        ItemMeta createProjectMeta = createProjectItem.getItemMeta();
        if (createProjectMeta != null) {
            createProjectMeta.setDisplayName(ChatColor.GREEN + "Projekt erstellen");
            createProjectItem.setItemMeta(createProjectMeta);
        }

        projectCreationInventory.setItem(3, titleItem);
        projectCreationInventory.setItem(5, descriptionItem);
        projectCreationInventory.setItem(8, createProjectItem);

        player.openInventory(projectCreationInventory);
    }

    // Methode zum Hinzufügen eines neuen Bauprojekts in die Datenbank
    public void addProject(Player player, String title, String description) {
        synchronized (dbLock) {
            openConnection();
            try (PreparedStatement stmt = connection.prepareStatement(
                    "INSERT INTO builder_projects (player_uuid, title, description, player_status, project_status) " +
                            "VALUES (?, ?, ?, 'angefragt', 'ausstehend')")) {
                stmt.setString(1, player.getUniqueId().toString());
                stmt.setString(2, title);
                stmt.setString(3, description);
                stmt.executeUpdate();
                player.sendMessage(ChatColor.GREEN + "Dein Bauprojekt wurde erfolgreich hinzugefügt!");
            } catch (SQLException e) {
                player.sendMessage(ChatColor.RED + "Fehler beim Hinzufügen deines Bauprojekts. Bitte versuche es erneut.");
                e.printStackTrace();
            }
        }
    }

    public void openProjectOverview(Player player) {
        openConnection();
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT project_id, title, project_status FROM builder_projects WHERE project_status = 'ausstehend' OR project_status = 'in_bearbeitung'")) {
            ResultSet rs = stmt.executeQuery();
            Inventory overviewInventory = Bukkit.createInventory(null, 27, ChatColor.DARK_AQUA + "Offene Projekte");

            int slot = 0;
            boolean hasProjects = false;
            while (rs.next()) {
                hasProjects = true;
                int projectId = rs.getInt("project_id");
                String title = rs.getString("title");
                String status = rs.getString("project_status");

                plugin.getLogger().info("Lade Projekt: ID=" + projectId + ", Titel=" + title); // Debugging

                // Verwenden eines normalen Buchs anstelle des Oraxen-Items
                ItemStack projectItem = new ItemStack(Material.BOOK);
                ItemMeta meta = projectItem.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ChatColor.GOLD + title);
                    meta.setLore(List.of(
                            ChatColor.YELLOW + "Projekt ID: " + projectId,
                            ChatColor.GREEN + "Status: " + status
                    ));
                    projectItem.setItemMeta(meta);
                } else {
                    plugin.getLogger().warning("ItemMeta für Projekt ID=" + projectId + " konnte nicht gesetzt werden."); // Debugging
                }

                overviewInventory.setItem(slot, projectItem);
                slot++;
            }

            if (!hasProjects) {
                ItemStack noProjectsItem = new ItemStack(Material.BARRIER);
                ItemMeta noProjectsMeta = noProjectsItem.getItemMeta();
                if (noProjectsMeta != null) {
                    noProjectsMeta.setDisplayName(ChatColor.RED + "Keine offenen Projekte verfügbar");
                    noProjectsItem.setItemMeta(noProjectsMeta);
                }
                overviewInventory.setItem(13, noProjectsItem);
                plugin.getLogger().info("Keine offenen Projekte verfügbar."); // Debugging
            }

            // Öffnen des Inventars ohne das Schließen zu verursachen
            Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(overviewInventory));
            plugin.getLogger().info("Projektübersicht-UI für Spieler " + player.getName() + " geöffnet."); // Debugging
        } catch (SQLException e) {
            player.sendMessage(ChatColor.RED + "Fehler beim Laden der offenen Projekte. Bitte versuche es erneut.");
            e.printStackTrace();
        } finally {
            closeConnection();
        }
    }

    // Erweiterte Methode zur Verwaltung von Bauprojekten
    public void manageProject(Player player, int projectId) {
        openConnection();
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT player_uuid, title, description, player_status, project_status FROM builder_projects WHERE project_id = ?")) {
            stmt.setInt(1, projectId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                UUID projectOwnerUUID = UUID.fromString(rs.getString("player_uuid"));
                String title = rs.getString("title");
                String description = rs.getString("description");
                String playerStatus = rs.getString("player_status");
                String projectStatus = rs.getString("project_status");

                if (player.getUniqueId().equals(projectOwnerUUID)) {
                    showOwnerRequests(player, projectId);
                } else {
                    openRequestUI(player, projectId, title, description, playerStatus, projectStatus);
                }
            } else {
                player.sendMessage(ChatColor.RED + "Projekt nicht gefunden. Bitte überprüfe die Projekt-ID.");
            }
        } catch (SQLException e) {
            player.sendMessage(ChatColor.RED + "Fehler beim Laden der Projektdaten. Bitte versuche es erneut.");
            e.printStackTrace();
        } finally {
            closeConnection();
        }
    }

    // Methode zur Anzeige der Anfragen an den Auftragsersteller
    private void showOwnerRequests(Player player, int projectId) {
        openConnection();
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT request_id, player_uuid, proposed_xp FROM builder_requests WHERE project_id = ?")) {
            stmt.setInt(1, projectId);
            ResultSet rs = stmt.executeQuery();

            Inventory requestInventory = Bukkit.createInventory(null, 27, ChatColor.DARK_AQUA + "Erhaltene Anfragen");

            int slot = 0;
            boolean hasRequests = false;
            while (rs.next()) {
                hasRequests = true;
                int requestId = rs.getInt("request_id");
                UUID requesterUUID = UUID.fromString(rs.getString("player_uuid"));
                int proposedXP = rs.getInt("proposed_xp");

                ItemStack requestItem = new ItemStack(Material.PAPER);
                ItemMeta meta = requestItem.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ChatColor.GOLD + "Anfrage von Spieler: " + Bukkit.getOfflinePlayer(requesterUUID).getName());
                    meta.setLore(List.of(
                            ChatColor.YELLOW + "Anfrage ID: " + requestId,
                            ChatColor.GREEN + "Vorgeschlagene XP: " + proposedXP
                    ));
                    requestItem.setItemMeta(meta);
                }

                requestInventory.setItem(slot, requestItem);
                slot++;
            }

            if (!hasRequests) {
                ItemStack noRequestsItem = new ItemStack(Material.BARRIER);
                ItemMeta noRequestsMeta = noRequestsItem.getItemMeta();
                if (noRequestsMeta != null) {
                    noRequestsMeta.setDisplayName(ChatColor.RED + "Keine offenen Anfragen verfügbar");
                    noRequestsItem.setItemMeta(noRequestsMeta);
                }
                requestInventory.setItem(13, noRequestsItem);
            }

            player.openInventory(requestInventory);
        } catch (SQLException e) {
            player.sendMessage(ChatColor.RED + "Fehler beim Laden der Anfragen. Bitte versuche es erneut.");
            e.printStackTrace();
        } finally {
            closeConnection();
        }
    }


    private void openRequestUI(Player player, int projectId, String title, String description, String playerStatus, String projectStatus) {
        Inventory requestInventory = Bukkit.createInventory(null, 9, ChatColor.DARK_AQUA + "Anfrage stellen");

        ItemStack infoItem = new ItemStack(Material.PAPER);
        ItemMeta meta = infoItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "Projektinformation");
            meta.setLore(List.of(
                    ChatColor.YELLOW + "Titel: " + ChatColor.WHITE + title,
                    ChatColor.YELLOW + "Beschreibung: " + ChatColor.WHITE + description,
                    ChatColor.GREEN + "Spielerstatus: " + playerStatus,
                    ChatColor.GREEN + "Projektstatus: " + projectStatus
            ));
            infoItem.setItemMeta(meta);
        }
        requestInventory.setItem(4, infoItem);

        ItemStack requestItem = new ItemStack(Material.GREEN_WOOL);
        ItemMeta requestMeta = requestItem.getItemMeta();
        if (requestMeta != null) {
            requestMeta.setDisplayName(ChatColor.GREEN + "Anfrage stellen");
            requestItem.setItemMeta(requestMeta);
        }
        requestInventory.setItem(8, requestItem);

        // Projekt-ID in einem versteckten Slot speichern
        ItemStack hiddenItem = new ItemStack(Material.BARRIER);
        ItemMeta hiddenMeta = hiddenItem.getItemMeta();
        if (hiddenMeta != null) {
            hiddenMeta.setDisplayName("Projekt ID");
            hiddenMeta.setLore(List.of("Projekt ID: " + projectId));
            hiddenItem.setItemMeta(hiddenMeta);
        } else {
            plugin.getLogger().severe("ItemMeta für das versteckte Item konnte nicht gesetzt werden.");
        }
        requestInventory.setItem(0, hiddenItem);

        // Ausgabe der Lore zur Überprüfung
        plugin.getLogger().info("Verstecktes Item Lore: " + hiddenItem.getItemMeta().getLore());

        player.openInventory(requestInventory);
    }

    // Methode zur Annahme einer Anfrage durch den Auftragsersteller
    private void acceptRequest(Player player, int requestId) {
        openConnection();
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT project_id, player_uuid, proposed_xp FROM builder_requests WHERE request_id = ?")) {
            stmt.setInt(1, requestId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                int projectId = rs.getInt("project_id");
                UUID requesterUUID = UUID.fromString(rs.getString("player_uuid"));
                int proposedXP = rs.getInt("proposed_xp");

                // Anfrage akzeptieren, Projektstatus aktualisieren und Anfrage löschen
                updateProjectStatus(projectId, "angenommen", "in_bearbeitung");
                removeRequest(requestId);

                player.sendMessage(ChatColor.GREEN + "Du hast die Anfrage angenommen. Das Projekt befindet sich jetzt in Bearbeitung.");
                // Verknüpfen von Spieler und Projekt
                playerProjectMap.put(requesterUUID, projectId);
                projectRequesterMap.put(projectId, requesterUUID);

                // XP Abzug oder Belohnung kann hier erfolgen
            }
        } catch (SQLException e) {
            player.sendMessage(ChatColor.RED + "Fehler beim Annehmen der Anfrage. Bitte versuche es erneut.");
            e.printStackTrace();
        } finally {
            closeConnection();
        }
    }

    // Methode zum Ablehnen einer Anfrage durch den Auftragsersteller
    // Methode zum Ablehnen einer Anfrage durch den Auftragsersteller
    private void rejectRequest(Player player, int requestId) {
        openConnection();
        try {
            removeRequest(requestId);
            player.sendMessage(ChatColor.RED + "Du hast die Anfrage abgelehnt.");
        } catch (SQLException e) {
            player.sendMessage(ChatColor.RED + "Fehler beim Ablehnen der Anfrage. Bitte versuche es erneut.");
            e.printStackTrace();
        } finally {
            closeConnection();
        }
    }


    // Methode zum Löschen einer Anfrage
    private void removeRequest(int requestId) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(
                "DELETE FROM builder_requests WHERE request_id = ?")) {
            stmt.setInt(1, requestId);
            stmt.executeUpdate();
        }
    }
    // Hilfsmethode zur Extraktion der Anfrage-ID aus einem Inventar
    private int getRequestIdFromInventory(Inventory inventory, ItemStack clickedItem) {
        if (clickedItem == null) {
            plugin.getLogger().severe("Clicked item is null");
            return -1;
        }

        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null) {
            plugin.getLogger().severe("ItemMeta is null for clicked item");
            return -1;
        }

        List<String> lore = meta.getLore();
        if (lore == null || lore.isEmpty()) {
            plugin.getLogger().severe("Lore is null or empty for clicked item");
            return -1;
        }

        for (String line : lore) {
            if (line.contains("Anfrage ID")) {
                try {
                    return Integer.parseInt(line.split(": ")[1].trim());
                } catch (NumberFormatException e) {
                    plugin.getLogger().severe("Fehler beim Parsen der Anfrage-ID: " + e.getMessage());
                }
            }
        }

        plugin.getLogger().severe("Anfrage ID konnte nicht gefunden werden in the clicked item lore");
        return -1;
    }


    // Hilfsmethode zur Extraktion der Projekt-ID aus einem Inventar
    private int getProjectIdFromInventory(Inventory inventory, ItemStack clickedItem) {
        if (clickedItem == null) {
            plugin.getLogger().severe("Clicked item is null");
            return -1;
        }

        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null) {
            plugin.getLogger().severe("ItemMeta is null for clicked item");
            return -1;
        }

        List<String> lore = meta.getLore();
        if (lore == null || lore.isEmpty()) {
            plugin.getLogger().severe("Lore is null or empty for clicked item");
            return -1;
        }

        for (String line : lore) {
            if (line.contains("Projekt ID")) {
                try {
                    return Integer.parseInt(line.split(": ")[1].trim());
                } catch (NumberFormatException e) {
                    plugin.getLogger().severe("Fehler beim Parsen der Projekt-ID: " + e.getMessage());
                }
            }
        }

        plugin.getLogger().severe("Projekt ID konnte nicht gefunden werden in the clicked item lore");
        return -1;
    }


    // Update Project Status in the database
    private void updateProjectStatus(int projectId, String playerStatus, String projectStatus) {
        synchronized (dbLock) {
            try (PreparedStatement stmt = connection.prepareStatement(
                    "UPDATE builder_projects SET player_status = ?, project_status = ? WHERE project_id = ?")) {
                stmt.setString(1, playerStatus);
                stmt.setString(2, projectStatus);
                stmt.setInt(3, projectId);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Fehler beim Aktualisieren des Projektstatus: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    // Methode zur Einleitung der Anfrage-Konversation
    private void initiateRequestConversation(Player player, Inventory inventory, ItemStack clickedItem) {
        // Versteckte Projekt-ID auslesen
        ItemStack hiddenItem = inventory.getItem(0);
        int projectId = getProjectIdFromInventory(inventory, hiddenItem);

        if (projectId != -1) {
            ConversationFactory factory = new ConversationFactory(plugin);
            Conversation conversation = factory
                    .withFirstPrompt(new RequestXPAmountPrompt(projectId))
                    .withLocalEcho(true)
                    .withTimeout(30)
                    .thatExcludesNonPlayersWithMessage("Nur Spieler können Anfragen stellen.")
                    .buildConversation(player);
            conversation.begin();
        } else {
            player.sendMessage(ChatColor.RED + "Projekt-ID konnte nicht gefunden werden.");
            plugin.getLogger().severe("Projekt-ID konnte nicht gefunden werden, obwohl der versteckte Slot verwendet wurde.");
        }
    }




    // Innerer Klassenprompt für das Vorschlagen von XP in einer Anfrage
    private class RequestXPAmountPrompt extends StringPrompt {
        private final int projectId;

        public RequestXPAmountPrompt(int projectId) {
            this.projectId = projectId;
        }

        @Override
        public String getPromptText(ConversationContext context) {
            return ChatColor.GOLD + "Bitte gib die Anzahl an Freaky XP ein, die du als Belohnung vorschlägst:";
        }

        @Override
        public Prompt acceptInput(ConversationContext context, String input) {
            Player player = (Player) context.getForWhom();
            try {
                int proposedXP = Integer.parseInt(input);
                addRequest(player, projectId, proposedXP);
                player.sendMessage(ChatColor.GREEN + "Deine Anfrage wurde erfolgreich gestellt!");
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Ungültige Zahl eingegeben. Anfrage abgebrochen.");
            }
            return END_OF_CONVERSATION;
        }
    }

    private void addRequest(Player player, int projectId, int proposedXP) {
        synchronized (dbLock) {
            openConnection();
            try (PreparedStatement stmt = connection.prepareStatement(
                    "INSERT INTO builder_requests (project_id, player_uuid, proposed_xp) " +
                            "VALUES (?, ?, ?)")) {
                stmt.setInt(1, projectId);
                stmt.setString(2, player.getUniqueId().toString());
                stmt.setInt(3, proposedXP);
                stmt.executeUpdate();

                // Speichern der requesterUUID und projectId
                playerProjectMap.put(player.getUniqueId(), projectId);
                projectRequesterMap.put(projectId, player.getUniqueId());

                player.sendMessage(ChatColor.GREEN + "Deine Anfrage wurde erfolgreich gestellt!");
            } catch (SQLException e) {
                player.sendMessage(ChatColor.RED + "Fehler beim Speichern der Anfrage. Bitte versuche es erneut.");
                e.printStackTrace();
            } finally {
                closeConnection();
            }
        }
    }


    private final Map<UUID, Integer> playerProjectMap = new HashMap<>(); // Map zur Speicherung von Project IDs basierend auf Spieler UUIDs
    private final Map<Integer, UUID> projectRequesterMap = new HashMap<>(); // Map zur Speicherung von Requester UUIDs basierend auf Project IDs

    private final Map<UUID, Boolean> playerPayments = new HashMap<>(); // Tracking player payments
    // Methode zur Überprüfung, ob der Spieler bezahlt hat
    private boolean playerHasPaid(UUID playerUUID) {
        return playerPayments.getOrDefault(playerUUID, false);
    }
    // Methode zum Markieren, dass ein Spieler bezahlt hat
    private void markPlayerAsPaid(UUID playerUUID) {
        playerPayments.put(playerUUID, true);
    }


    // Methode zur Abwicklung der Projektbezahlung
    private void handleProjectPayment(Player player, int projectId, UUID requesterUUID) {
        if (playerHasPaid(player.getUniqueId()) && playerHasPaid(requesterUUID)) {
            updateProjectStatus(projectId, "bezahlt", "nächste_phase");
            player.sendMessage(ChatColor.GREEN + "Beide Spieler haben bezahlt. Das Projekt wechselt in die nächste Phase.");
            playerPayments.remove(player.getUniqueId());
            playerPayments.remove(requesterUUID);
            // Weitere Logik, um das Projekt fortzusetzen...
        } else {
            player.sendMessage(ChatColor.YELLOW + "Warte auf die Bezahlung des anderen Spielers.");
        }
    }
    @EventHandler
    public void handleVillagerPayment(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof Villager) {
            Villager villager = (Villager) event.getRightClicked();
            if (villager.getCustomName() != null && villager.getCustomName().equals(ChatColor.GOLD + "Baumeister")) {
                event.setCancelled(true);
                Player player = event.getPlayer();
                Inventory inv = player.getInventory();
                ItemStack auftragsbuch = OraxenItems.getItemById("auftragsbuch").build();
                if (inv.containsAtLeast(auftragsbuch, 1)) {
                    inv.removeItem(auftragsbuch);
                    player.sendMessage(ChatColor.GREEN + "Du hast das Auftragsbuch abgegeben.");
                    markPlayerAsPaid(player.getUniqueId());

                    Integer projectId = playerProjectMap.get(player.getUniqueId());
                    UUID requesterUUID = projectRequesterMap.get(projectId);

                    if (projectId != null && requesterUUID != null) {
                        handleProjectPayment(player, projectId, requesterUUID);
                    } else {
                        player.sendMessage(ChatColor.RED + "Fehler: Projektinformationen konnten nicht gefunden werden.");
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "Du benötigst ein Auftragsbuch, um zu bezahlen.");
                }
            }
        }
    }

}

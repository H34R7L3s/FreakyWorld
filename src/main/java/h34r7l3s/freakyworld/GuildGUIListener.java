package h34r7l3s.freakyworld;

import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.util.*;

public class GuildGUIListener implements Listener {

    private final JavaPlugin plugin;
    private final GuildSaver guildSaver;
    private GuildManager guildManager;
    private Map<Player, PlayerState> playerStates = new HashMap<>();
    private Map<Player, Guild> viewedGuilds = new HashMap<>();

    public enum PlayerState {
        NONE,
        ADDING_TASK,
        CREATING_ALLIANCE,
        CREATING_GUILD,
        ADDING_MEMBER,
        REMOVING_MEMBER,
        VIEWING_GUILD,
        SETTING_DESCRIPTION,
        ADDING_MESSAGE
    }

    public GuildGUIListener(JavaPlugin plugin, Connection connection) {
        this.plugin = plugin;
        this.guildManager = new GuildManager(connection);
        this.guildSaver = new GuildSaver(plugin, connection);

    }
    // Füge diese Methoden zu GuildGUIListener hinzu


    @EventHandler
    public void onVillagerInteract(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof Villager) {
            Villager villager = (Villager) event.getRightClicked();
            if ("Gildenmeister".equals(villager.getCustomName())) {
                event.setCancelled(true);
                openGuildMenu(event.getPlayer());
            }
        }
    }

    public void removeGuildVillager() {
        World world = Bukkit.getWorld("world"); // Ändern Sie "Weltname" in den Namen Ihrer Welt
        Location villagerLocation = new Location(world, -45, 369, 14); // Ändern Sie die Koordinaten entsprechend der Position des Gildenmeister-Villagers

        for (Entity entity : world.getEntities()) {
            if (entity instanceof Villager) {
                Villager villager = (Villager) entity;
                if (villager.getCustomName() != null && villager.getCustomName().equals("Gildenmeister") &&
                        villager.getLocation().getBlockX() == villagerLocation.getBlockX() &&
                        villager.getLocation().getBlockY() == villagerLocation.getBlockY() &&
                        villager.getLocation().getBlockZ() == villagerLocation.getBlockZ()) {
                    villager.remove();
                    return; // Der Villager wurde gefunden und entfernt
                }
            }
        }
    }

    private void openSetDescriptionMenu(Player player) {
        Guild guild = viewedGuilds.get(player);
        if (guild != null && guild.getMemberRank(player.getName()) == Guild.GuildRank.LEADER) {
            playerStates.put(player, PlayerState.SETTING_DESCRIPTION);
            player.closeInventory();
            player.sendMessage("Bitte gebe die neue Beschreibung für deine Gilde im Chat ein.");
        } else {
            player.sendMessage("Nur der Anführer kann die Beschreibung ändern!");
        }
    }

    private void openGuildMenu(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Inventory guildMenu = Bukkit.createInventory(null, 27, "Gilden Menü");

            // Anzeigen aller Gilden
            int slot = 0;
            for (Guild guild : guildManager.getAllGuilds()) {
                ItemStack guildItem = new ItemStack(Material.PAPER);
                ItemMeta meta = guildItem.getItemMeta();
                meta.setDisplayName(guild.getName());

                // Setzen der Beschreibung als Lore
                List<String> lore = new ArrayList<>();
                lore.add(guild.getDescription());
                meta.setLore(lore);

                guildItem.setItemMeta(meta);
                guildMenu.setItem(slot, guildItem);
                slot++;
            }

            // Icon zum Verwalten von Allianzen
            ItemStack manageAlliancesItem = new ItemStack(Material.DIAMOND_SWORD);
            ItemMeta manageAlliancesMeta = manageAlliancesItem.getItemMeta();
            manageAlliancesMeta.setDisplayName("Allianzen verwalten");
            manageAlliancesItem.setItemMeta(manageAlliancesMeta);
            guildMenu.setItem(26, manageAlliancesItem); // Platzierung im Inventar anpassen

            // Icon zum Gründen einer neuen Gilde
            ItemStack createGuildItem = new ItemStack(Material.GREEN_WOOL);
            ItemMeta createGuildMeta = createGuildItem.getItemMeta();
            createGuildMeta.setDisplayName("Neue Gilde gründen");
            createGuildItem.setItemMeta(createGuildMeta);
            guildMenu.setItem(25, createGuildItem);

            player.openInventory(guildMenu);
        });
    }


    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        ItemStack currentItem = event.getCurrentItem();

        if (currentItem == null || currentItem.getItemMeta() == null) return;

        String inventoryTitle = event.getView().getTitle();
        Guild playerGuild = guildManager.getPlayerGuild(player.getName());

        if (inventoryTitle.equals("Allianzen Menü")) {
            event.setCancelled(true); // Verhindern, dass der Spieler Items nimmt oder setzt
            handleAllianceMenuInteraction(event, player, guildManager);
            return; // Früher Rückkehr, um weitere Verarbeitung zu vermeiden
        }
        if (event.getView().getTitle().equals("Allianzstatus ändern")) {
            event.setCancelled(true); // Verhindern, dass der Spieler das Item nimmt
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || !clickedItem.hasItemMeta()) return;

            //Player player = (Player) event.getWhoClicked();
            String targetGuildName = clickedItem.getItemMeta().getDisplayName();
            String playerGuildName = guildManager.getPlayerGuild(player.getName()).getName();

            // Hier die Logik, um den neuen Status zu bestimmen, basierend auf dem geklickten Item
            String newStatus = determineNewStatus(clickedItem);

            // Update des Allianzstatus
            boolean statusUpdated = guildManager.updateAllianceStatus(playerGuildName, targetGuildName, newStatus);

            if (statusUpdated) {
                player.sendMessage("Allianzstatus mit " + targetGuildName + " geändert zu " + newStatus + ".");
            } else {
                player.sendMessage("Fehler beim Ändern des Allianzstatus.");
            }

            player.closeInventory(); // Schließt das Inventar
        }
        if (inventoryTitle.equals("Gilden Menü")) {
            String itemName = currentItem.getItemMeta().getDisplayName();

            // Überprüfen, ob der Spieler auf den Namen seiner eigenen Gilde klickt
            if (playerGuild != null && itemName.equals(playerGuild.getName())) {
                event.setCancelled(true);
                viewedGuilds.put(player, playerGuild);
                showGuildOptions(player, playerGuild);
                return;
            }

            // Andere Logik für das "Gilden Menü"
            handleGuildMenu(event, player, currentItem, playerGuild);
        } else if (inventoryTitle.startsWith("Gildenoptionen für ")) {
            handleGuildOptions(event, player, currentItem, playerGuild);
        } else if (inventoryTitle.equals("Spieler einladen")) {
            handlePlayerInvitation(event, player, currentItem, playerGuild);
        } else if (inventoryTitle.equals("Aufgabe/Nachricht hinzufügen")) {
            handleTaskOrMessageAddition(event, player, currentItem, playerGuild);
        } else if (inventoryTitle.equals("Gilden-Nachrichten und Aufgaben")) {
            handleGuildMessagesAndTasks(event, player, currentItem, playerGuild);
        }
    }


    private void handleGuildMenu(InventoryClickEvent event, Player player, ItemStack currentItem, Guild playerGuild) {
        event.setCancelled(true);
        String itemName = currentItem.getItemMeta().getDisplayName();
        Guild clickedGuild = guildManager.getGuild(itemName);

        // Logik für "Neue Gilde gründen"
        if ("Neue Gilde gründen".equals(itemName)) {
            if (playerGuild == null) {
                playerStates.put(player, PlayerState.CREATING_GUILD);
                player.closeInventory();
                player.sendMessage("Bitte gebe den Namen der neuen Gilde im Chat ein.");
            } else {
                player.sendMessage("Du bist bereits in einer Gilde!");
            }
        } else if (clickedGuild != null && clickedGuild.equals(playerGuild)) {
            viewedGuilds.put(player, clickedGuild);
            showGuildOptions(player, clickedGuild);
        }
        if ("Allianzen verwalten".equals(itemName)) {
            // Call the method to show the Alliances menu
            showAllianceOptions(player);
        } else {
            // Handle other options in the Guilds menu
            player.sendMessage("Da ist was schief gelaufen!");
        }
    }

    private void handleGuildOptions(InventoryClickEvent event, Player player, ItemStack currentItem, Guild playerGuild) {
        event.setCancelled(true);
        if (playerGuild == null) return;

        String itemName = currentItem.getItemMeta().getDisplayName();

        switch (itemName) {
            case "Beschreibung setzen":
                openSetDescriptionMenu(player);
                break;
            case "Mitglied hinzufügen":
                playerStates.put(player, PlayerState.ADDING_MEMBER);
                openAddMemberMenu(player);
                break;
            case "Mitglied entfernen":
                playerStates.put(player, PlayerState.REMOVING_MEMBER);
                openRemoveMemberMenu(player, playerGuild);
                break;
            case "Gilde löschen":
                if (playerGuild.isMember(player.getName()) && playerGuild.getMemberRank(player.getName()) == Guild.GuildRank.LEADER) {
                    guildManager.deleteGuild(playerGuild.getName());
                    player.sendMessage("Die Gilde " + playerGuild.getName() + " wurde gelöscht.");
                    playerStates.put(player, PlayerState.NONE);
                    player.closeInventory();
                } else {
                    player.sendMessage("Du hast keine Berechtigung, diese Gilde zu löschen.");
                }
                break;
            case "Gilden-Nachrichten anzeigen":
                openGuildMessagesMenu(player, playerGuild);
                break;
            case "Rang ändern":
                openChangeRankMenu(player, playerGuild);
                break;
            case "Schatztruhe öffnen":
                if (playerGuild != null) {
                    openTreasuryMenu(player, playerGuild);
                } else {
                    player.sendMessage("Du bist in keiner Gilde!");
                }
                break;
            case "Aufgabe/Nachricht hinzufügen":
                openAddTaskOrMessageMenu(player);
                break;
        }
    }

    private void handlePlayerInvitation(InventoryClickEvent event, Player player, ItemStack currentItem, Guild playerGuild) {
        event.setCancelled(true);
        if (!(currentItem.getItemMeta() instanceof SkullMeta)) return;
        SkullMeta skullMeta = (SkullMeta) currentItem.getItemMeta();
        if (skullMeta.getOwningPlayer() == null) return;

        String invitedPlayerName = skullMeta.getOwningPlayer().getName();
        if (playerGuild != null) {
            Player invitedPlayer = Bukkit.getPlayer(invitedPlayerName);
            if (invitedPlayer != null && !playerGuild.isMember(invitedPlayerName)) {
                guildManager.addMemberToGuild(playerGuild.getName(), invitedPlayerName);
                player.sendMessage(invitedPlayerName + " wurde zu " + playerGuild.getName() + " hinzugefügt!");
                invitedPlayer.sendMessage("Du wurdest zu " + playerGuild.getName() + " hinzugefügt!");
            } else {
                player.sendMessage(invitedPlayerName + " ist bereits in einer Gilde oder existiert nicht.");
            }
        }
        playerStates.put(player, PlayerState.VIEWING_GUILD);
        showGuildOptions(player, playerGuild);
    }

    private void handleTaskOrMessageAddition(InventoryClickEvent event, Player player, ItemStack currentItem, Guild playerGuild) {
        event.setCancelled(true);
        ItemMeta meta = currentItem.getItemMeta();
        if (meta == null) return;

        if (meta.getDisplayName().equals("Aufgabe hinzufügen")) {
            playerStates.put(player, PlayerState.ADDING_TASK);
            player.closeInventory();
            player.sendMessage("Bitte gebe die Beschreibung für die neue Aufgabe im Chat ein.");
        } else if (meta.getDisplayName().equals("Nachricht hinzufügen")) {
            playerStates.put(player, PlayerState.ADDING_MESSAGE);
            player.closeInventory();
            player.sendMessage("Bitte gebe die Nachricht für die Gilde im Chat ein.");
        }
    }

    private void handleGuildMessagesAndTasks(InventoryClickEvent event, Player player, ItemStack currentItem, Guild playerGuild) {
        event.setCancelled(true);
        ItemMeta meta = currentItem.getItemMeta();
        if (meta == null || playerGuild == null) return;

        if (meta.getDisplayName().equals("Aufgabe")) {
            List<String> lore = meta.getLore();
            if (lore != null && !lore.isEmpty()) {
                String taskIdString = lore.get(0);
                int taskId = Integer.parseInt(taskIdString);
                Guild.GuildTask task = playerGuild.findTaskById(taskId);

                if (task != null && task.getStatus().equals("offen")) {
                    if (playerGuild.assignTaskToMember(taskId, player.getName())) {
                        player.sendMessage("Du hast die Aufgabe angenommen!");
                        guildSaver.saveGuildTask(task, playerGuild.getName());
                    } else {
                        player.sendMessage("Diese Aufgabe ist bereits angenommen.");
                    }
                } else if (task != null && task.getStatus().equals("zugewiesen") && task.getAssignedMember().equals(player.getName())) {
                    task.completeTask();
                    player.sendMessage("Aufgabe abgeschlossen!");
                    guildSaver.saveGuildTask(task, playerGuild.getName());
                } else {
                    player.sendMessage("Du kannst diese Aufgabe nicht annehmen oder abschließen.");
                }
            }
        }
    }


    private void openAddMemberMenu(Player player) {
        Inventory inviteMenu = Bukkit.createInventory(null, 54, "Spieler einladen");

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.getName().equals(player.getName())) continue;

            ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta skullMeta = (SkullMeta) playerHead.getItemMeta();
            skullMeta.setOwningPlayer(onlinePlayer);
            skullMeta.setDisplayName(onlinePlayer.getName());
            playerHead.setItemMeta(skullMeta);

            inviteMenu.addItem(playerHead);
        }

        player.openInventory(inviteMenu);
    }

    private void openRemoveMemberMenu(Player player, Guild guild) {
        Inventory removeMenu = Bukkit.createInventory(null, 54, "Mitglied entfernen");

        for (String member : guild.getMembers()) {
            if (guild.getMemberRank(member) != Guild.GuildRank.LEADER && !member.equals(player.getName())) {
                ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta skullMeta = (SkullMeta) playerHead.getItemMeta();
                skullMeta.setOwner(member);
                skullMeta.setDisplayName(member);
                playerHead.setItemMeta(skullMeta);

                removeMenu.addItem(playerHead);
            }
        }

        player.openInventory(removeMenu);

    }

    private void removeMemberFromGuild(Player player, String guildName, String playerName) {
        Guild guild = viewedGuilds.get(player);
        if (guild != null && guild.getMemberRank(player.getName()) == Guild.GuildRank.LEADER) {
            guildManager.removeMemberFromGuild(guildName, playerName);
            player.sendMessage("Spieler " + playerName + " wurde aus der Gilde entfernt.");
            playerStates.put(player, PlayerState.VIEWING_GUILD);
            showGuildOptions(player, guild);
        } else {
            player.sendMessage("Nur der Anführer kann Mitglieder entfernen!");
        }
    }

    @EventHandler
    public void onRemoveMemberMenuClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        PlayerState state = playerStates.getOrDefault(player, PlayerState.NONE);
        ItemStack currentItem = event.getCurrentItem();

        if (currentItem == null || currentItem.getItemMeta() == null) return;

        String inventoryTitle = event.getView().getTitle();

        if (inventoryTitle.equals("Mitglied entfernen")) {
            event.setCancelled(true);
            String itemName = currentItem.getItemMeta().getDisplayName();

            if (!itemName.equals("Mitglied entfernen")) {
                // Der Spieler hat auf den Kopf eines Mitglieds geklickt
                String playerName = ChatColor.stripColor(itemName); // Entfernt Farbcodes aus dem Namen

                // Hier den aktuellen Gildenstatus des Spielers abrufen
                Guild guild = viewedGuilds.get(player);

                if (guild != null) {
                    removeMemberFromGuild(player, guild.getName(), playerName);
                } else {
                    player.sendMessage("Du bist in keiner Gilde!");
                }
            }
        }
    }

    private void openGuildMessagesMenu(Player player, Guild guild) {
        Inventory messagesMenu = Bukkit.createInventory(null, 54, "Gilden-Nachrichten und Aufgaben");

        // Hinzufügen von Nachrichten
        for (String message : guild.getMessages()) {
            ItemStack messageItem = new ItemStack(Material.PAPER);
            ItemMeta meta = messageItem.getItemMeta();
            meta.setDisplayName("Nachricht");
            List<String> lore = new ArrayList<>();
            lore.add(message);
            meta.setLore(lore);
            messageItem.setItemMeta(meta);

            messagesMenu.addItem(messageItem);
        }

        // Hinzufügen von Aufgaben
        for (Guild.GuildTask task : guild.getTasks()) {
            ItemStack taskItem = new ItemStack(Material.WRITABLE_BOOK);
            ItemMeta meta = taskItem.getItemMeta();
            meta.setDisplayName(task.getDescription());
            List<String> lore = new ArrayList<>();
            lore.add("Status: " + task.getStatus());
            if (!task.getAssignedMember().isEmpty()) {
                lore.add("Zugewiesen an: " + task.getAssignedMember());
            }
            meta.setLore(lore);
            taskItem.setItemMeta(meta);

            messagesMenu.addItem(taskItem);
        }

        player.openInventory(messagesMenu);
    }


    private void openAddTaskOrMessageMenu(Player player) {
        Inventory addMenu = Bukkit.createInventory(null, 9, "Aufgabe/Nachricht hinzufügen");

        ItemStack addTaskItem = new ItemStack(Material.BOOK);
        ItemMeta addTaskMeta = addTaskItem.getItemMeta();
        addTaskMeta.setDisplayName("Aufgabe hinzufügen");
        addTaskItem.setItemMeta(addTaskMeta);
        addMenu.setItem(3, addTaskItem);

        ItemStack addMessageItem = new ItemStack(Material.PAPER);
        ItemMeta addMessageMeta = addMessageItem.getItemMeta();
        addMessageMeta.setDisplayName("Nachricht hinzufügen");
        addMessageItem.setItemMeta(addMessageMeta);
        addMenu.setItem(5, addMessageItem);

        player.openInventory(addMenu);
    }

    private void openChangeRankMenu(Player player, Guild guild) {
        Inventory rankMenu = Bukkit.createInventory(null, 27, "Rang ändern");

        for (String member : guild.getMembers()) {
            if (!member.equals(player.getName())) {
                ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta skullMeta = (SkullMeta) playerHead.getItemMeta();
                skullMeta.setOwner(member);
                skullMeta.setDisplayName(member);
                playerHead.setItemMeta(skullMeta);

                rankMenu.addItem(playerHead);
            }
        }

        player.openInventory(rankMenu);
    }

    private void openTreasuryMenu(Player player, Guild guild) {
        Inventory treasuryMenu = Bukkit.createInventory(null, 27, "Gilden Schatztruhe für " + guild.getName());

        // Holen Sie die gespeicherten Gegenstände aus der Datenbank
        List<ItemStack> storedItems = guildManager.getGuildItems(guild.getName());

        // Füllen Sie das treasuryMenu mit den gespeicherten Gegenständen
        for (int i = 0; i < storedItems.size() && i < treasuryMenu.getSize(); i++) {
            ItemStack item = storedItems.get(i);
            treasuryMenu.setItem(i, item);
        }

        // Setzen Sie leere Slots auf null
        for (int i = storedItems.size(); i < treasuryMenu.getSize(); i++) {
            treasuryMenu.setItem(i, null);
        }

        player.openInventory(treasuryMenu);
    }


    private void showGuildOptions(Player player, Guild guild) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Inventory guildOptions = Bukkit.createInventory(null, 27, "Gildenoptionen für " + guild.getName());

            ItemStack setDescriptionItem = new ItemStack(Material.PAPER);
            ItemMeta setDescriptionMeta = setDescriptionItem.getItemMeta();
            setDescriptionMeta.setDisplayName("Beschreibung setzen");
            setDescriptionItem.setItemMeta(setDescriptionMeta);

            ItemStack addMemberItem = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta addMemberMeta = addMemberItem.getItemMeta();
            addMemberMeta.setDisplayName("Mitglied hinzufügen");
            addMemberItem.setItemMeta(addMemberMeta);

            ItemStack removeMemberItem = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta removeMemberMeta = removeMemberItem.getItemMeta();
            removeMemberMeta.setDisplayName("Mitglied entfernen");
            removeMemberItem.setItemMeta(removeMemberMeta);

            ItemStack deleteGuildItem = new ItemStack(Material.RED_WOOL);
            ItemMeta deleteGuildMeta = deleteGuildItem.getItemMeta();
            deleteGuildMeta.setDisplayName("Gilde löschen");
            deleteGuildItem.setItemMeta(deleteGuildMeta);

            ItemStack viewMessagesItem = new ItemStack(Material.BOOK);
            ItemMeta viewMessagesMeta = viewMessagesItem.getItemMeta();
            viewMessagesMeta.setDisplayName("Gilden-Nachrichten anzeigen");
            viewMessagesItem.setItemMeta(viewMessagesMeta);

            ItemStack changeRankItem = new ItemStack(Material.GOLDEN_CARROT);
            ItemMeta changeRankMeta = changeRankItem.getItemMeta();
            changeRankMeta.setDisplayName("Rang ändern");
            changeRankItem.setItemMeta(changeRankMeta);

            ItemStack openTreasuryItem = new ItemStack(Material.CHEST);
            ItemMeta openTreasuryMeta = openTreasuryItem.getItemMeta();
            openTreasuryMeta.setDisplayName("Schatztruhe öffnen");
            openTreasuryItem.setItemMeta(openTreasuryMeta);

            // Menüoption zum Hinzufügen von Nachrichten und Aufgaben
            ItemStack addTaskOrMessageItem = new ItemStack(Material.EMERALD_BLOCK);
            ItemMeta addTaskOrMessageMeta = addTaskOrMessageItem.getItemMeta();
            addTaskOrMessageMeta.setDisplayName("Aufgabe/Nachricht hinzufügen");
            addTaskOrMessageItem.setItemMeta(addTaskOrMessageMeta);
            guildOptions.setItem(24, addTaskOrMessageItem); // Platzierung im Inventar anpassen


            guildOptions.setItem(10, setDescriptionItem);
            guildOptions.setItem(12, addMemberItem);
            guildOptions.setItem(14, removeMemberItem);
            guildOptions.setItem(16, deleteGuildItem);
            guildOptions.setItem(19, viewMessagesItem);
            guildOptions.setItem(21, changeRankItem);
            guildOptions.setItem(23, openTreasuryItem);

            player.openInventory(guildOptions);
        });
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        PlayerState state = playerStates.getOrDefault(player, PlayerState.NONE);

        if (state == PlayerState.SETTING_DESCRIPTION) {
            event.setCancelled(true);

            Guild guild = viewedGuilds.get(player);
            if (guild != null) {
                guild.setDescription(event.getMessage());
                player.sendMessage("Beschreibung erfolgreich geändert!");
                playerStates.put(player, PlayerState.VIEWING_GUILD);
                showGuildOptions(player, guild);
                guildManager.saveGuildData(guild);
            }
        } else if (state == PlayerState.CREATING_GUILD) {
            event.setCancelled(true);

            String guildName = event.getMessage();
            if (guildName != null && !guildName.isEmpty()) {
                if (guildManager.getPlayerGuild(player.getName()) == null) {
                    if (guildManager.createGuild(guildName, player.getName())) {
                        player.sendMessage("Die Gilde " + guildName + " wurde erfolgreich erstellt!");
                        playerStates.put(player, PlayerState.VIEWING_GUILD);
                        Guild guild = guildManager.getGuild(guildName);
                        viewedGuilds.put(player, guild);
                        showGuildOptions(player, guild);
                    } else {
                        player.sendMessage("Fehler beim Erstellen der Gilde.");
                    }
                } else {
                    player.sendMessage("Du bist bereits in einer Gilde!");
                }
            } else {
                player.sendMessage("Ungültiger Gildenname!");
            }
        } else if (state == PlayerState.ADDING_MESSAGE) {
            event.setCancelled(true);

            Guild guild = viewedGuilds.get(player);
            if (guild != null) {
                guild.addMessage(player.getName() + ": " + event.getMessage());
                player.sendMessage("Nachricht erfolgreich gesendet!");
                playerStates.put(player, PlayerState.VIEWING_GUILD);
                showGuildOptions(player, guild);
                guildManager.saveGuildData(guild);
            }
        } else if (state == PlayerState.ADDING_TASK) {
            event.setCancelled(true);
            Guild guild = viewedGuilds.get(player);
            if (guild != null) {
                Guild.GuildTask newTask = guild.new GuildTask(guild.getTasks().size() + 1, event.getMessage());
                guild.addTask(newTask);
                player.sendMessage("Aufgabe erfolgreich hinzugefügt!");
                playerStates.put(player, PlayerState.VIEWING_GUILD);
                showGuildOptions(player, guild);
                guildManager.saveGuildData(guild); // Speichern der Gildendaten
                guildSaver.saveGuildTask(newTask, guild.getName());
            }
        }

    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        // Überprüfen, ob der Spieler im VIEWING_GUILD Zustand ist
        if (playerStates.getOrDefault(player, PlayerState.NONE) == PlayerState.VIEWING_GUILD) {
            // Event abbrechen, wenn der Spieler gerade die Gilden-GUI betrachtet
            event.setCancelled(true);
        }
        // Andernfalls normalen Blockabbau zulassen
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        // Überprüfen, ob der Spieler im VIEWING_GUILD Zustand ist
        if (playerStates.getOrDefault(player, PlayerState.NONE) == PlayerState.VIEWING_GUILD) {
            // Event abbrechen, wenn der Spieler gerade die Gilden-GUI betrachtet
            event.setCancelled(true);
        }
        // Andernfalls normales Blockplatzieren zulassen
    }


    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        Inventory inventory = event.getInventory();
        String inventoryTitle = event.getView().getTitle();

        // Überprüfen, ob es die Gilden-Schatztruhe ist
        if (inventoryTitle.startsWith("Gilden Schatztruhe für ")) {
            String guildName = inventoryTitle.replace("Gilden Schatztruhe für ", "");
            Guild guild = guildManager.getGuild(guildName);

            if (guild != null) {
                // Löschen Sie alle aktuellen Items aus der Datenbank für diese Gilde
                guildManager.removeAllGuildItems(guildName);

                // Speichern Sie jedes Item im Inventar in der Datenbank
                for (int i = 0; i < inventory.getSize(); i++) {
                    ItemStack item = inventory.getItem(i);
                    if (item != null) {
                        guildManager.saveItemToGuild(guildName, item);
                    }
                }
            }
        }
    }


    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        playerStates.put(player, PlayerState.NONE);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        playerStates.remove(player);
        viewedGuilds.remove(player);
    }


    //Allianzen
    private void showAllianceOptions(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Inventory allianceMenu = Bukkit.createInventory(null, 27, "Allianzen Menü");
            Guild playerGuild = guildManager.getPlayerGuild(player.getName());

            if (playerGuild == null) {
                player.sendMessage("Du bist in keiner Gilde!");
                return;
            }

            // Example: Button to create a new alliance
            ItemStack createAllianceItem = new ItemStack(Material.EMERALD_BLOCK);
            ItemMeta createAllianceMeta = createAllianceItem.getItemMeta();
            createAllianceMeta.setDisplayName("Neue Allianz gründen");
            createAllianceItem.setItemMeta(createAllianceMeta);
            allianceMenu.setItem(0, createAllianceItem);

            // Option to display existing alliances
            ItemStack displayAlliancesItem = new ItemStack(Material.DIAMOND_BLOCK);
            ItemMeta displayAlliancesMeta = displayAlliancesItem.getItemMeta();
            displayAlliancesMeta.setDisplayName("Allianzen anzeigen");
            displayAlliancesItem.setItemMeta(displayAlliancesMeta);
            allianceMenu.setItem(1, displayAlliancesItem);

            // Option to change alliance status
            ItemStack changeStatusItem = new ItemStack(Material.GOLD_BLOCK);
            ItemMeta changeStatusMeta = changeStatusItem.getItemMeta();
            changeStatusMeta.setDisplayName("Status ändern");
            changeStatusItem.setItemMeta(changeStatusMeta);
            allianceMenu.setItem(2, changeStatusItem);

            // Option to delete an alliance
            ItemStack deleteAllianceItem = new ItemStack(Material.REDSTONE_BLOCK);
            ItemMeta deleteAllianceMeta = deleteAllianceItem.getItemMeta();
            deleteAllianceMeta.setDisplayName("Allianz löschen");
            deleteAllianceItem.setItemMeta(deleteAllianceMeta);
            allianceMenu.setItem(3, deleteAllianceItem);

            player.openInventory(allianceMenu);
        });
    }




    private void handleAllianceMenuInteraction(InventoryClickEvent event, Player player, GuildManager guildManager) {
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getItemMeta() == null) return;

        String itemName = clickedItem.getItemMeta().getDisplayName();
        Guild playerGuild = guildManager.getPlayerGuild(player.getName());

        // Sicherstellen, dass der Spieler in einer Gilde ist
        if (playerGuild == null) {
            player.sendMessage("Du bist in keiner Gilde.");
            return;
        }

        // Prüfen, ob der Spieler berechtigt ist, Allianzaktionen durchzuführen
        if (!guildManager.isPlayerAllowedToManageAlliances(player.getName(), playerGuild.getName())) {
            player.sendMessage("Du hast keine Berechtigung, Allianzen zu verwalten.");
            return;
        }

        switch (itemName) {
            case "Neue Allianz gründen":
                // Implementierung zum Erstellen einer neuen Allianz
                // Beispiel: guildManager.createAlliance(playerGuild.getName(), "AndereGilde", "Status");
                player.sendMessage("Neue Allianz gegründet.");
                break;

            case "Allianzen anzeigen":
                List<String[]> alliances = guildManager.getAlliancesOfGuild(playerGuild.getName());
                // Hier Code zum Anzeigen der Allianzen
                player.sendMessage("Beta");
                // Beispiel: alliances.forEach(a -> player.sendMessage(a[0] + " - " + a[1] + " : " + a[2]));
                break;

            case "Status ändern":
                //Guild playerGuild = guildManager.getPlayerGuild(player.getName());
                if (playerGuild == null || !playerGuild.getLeader().equals(player.getName())) {
                    player.sendMessage("Du bist nicht berechtigt, den Allianzstatus zu ändern.");
                    break;
                }

                List<Guild> allGuilds = guildManager.getAllGuilds();
                allGuilds.remove(playerGuild); // Entfernen der eigenen Gilde aus der Liste

                // GUI erstellen und anzeigen
                Inventory inv = Bukkit.createInventory(null, 9, "Allianzstatus ändern");
                // Vorhandene Instanz von playerGuild verwenden

                for (Guild guild : allGuilds) {
                    ItemStack item = createItemForGuild(guild, playerGuild.getName()); // Übergeben Sie playerGuild.getName() als zweiten Parameter
                    inv.addItem(item);
                }

                player.openInventory(inv);
                break;

            case "Allianz löschen":
                // Implementierung zum Löschen einer Allianz
                // Beispiel: guildManager.deleteAlliance(guildId1, guildId2);
                player.sendMessage("Allianz gelöscht. BETA");
                break;
        }

        event.setCancelled(true);
    }
    private ItemStack createItemForGuild(Guild guild, String playerGuildName) {
        // Annahme: Eine Methode getAllianceStatus existiert im GuildManager
        String allianceStatus = guildManager.getAllianceStatus(playerGuildName, guild.getName());

        Material iconMaterial;
        String displayName;
        switch (allianceStatus) {
            case "Krieg":
                iconMaterial = Material.DIAMOND_SWORD;
                displayName = "Krieg mit " + guild.getName();
                break;
            case "Frieden":
                iconMaterial = Material.EMERALD;
                displayName = "Frieden mit " + guild.getName();
                break;
            default:
                iconMaterial = Material.WHITE_BANNER;
                displayName = "Neutral zu " + guild.getName();
        }

        ItemStack item = new ItemStack(iconMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            // Hier können Sie zusätzliche Informationen in die Lore einfügen
            meta.setLore(Arrays.asList("Klicken, um den Status zu ändern"));
            item.setItemMeta(meta);
        }

        return item;
    }



    @EventHandler
    public void onPlayerChatAllianz(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        PlayerState state = playerStates.getOrDefault(player, PlayerState.NONE);
        String message = event.getMessage();

        if (state == PlayerState.CREATING_ALLIANCE) {
            event.setCancelled(true);
            Guild playerGuild = guildManager.getPlayerGuild(player.getName());
            Guild targetGuild = guildManager.getGuild(message);

            if (playerGuild != null && targetGuild != null && !playerGuild.equals(targetGuild)) {
                // Implementieren Sie die Logik zum Erstellen einer Allianz
                boolean success = guildManager.createAlliance(playerGuild.getName(), targetGuild.getName(), "verbündet");



                if (success) {
                    player.sendMessage("Allianz mit " + targetGuild.getName() + " erfolgreich gegründet.");
                } else {
                    player.sendMessage("Fehler beim Gründen der Allianz.");
                }
            } else {
                player.sendMessage("Ungültige Gildeninformationen.");
            }

            playerStates.put(player, PlayerState.NONE);
        }

        // Weitere Zustände und Logik...
    }
    private String determineNewStatus(ItemStack clickedItem) {
        ItemMeta itemMeta = clickedItem.getItemMeta();
        if (itemMeta == null) {
            return "Unbekannt";
        }

        // Angenommen, der Name des Items repräsentiert den aktuellen Status
        // Wir wechseln den Status basierend auf dem aktuellen
        String currentStatus = itemMeta.getDisplayName();
        switch (currentStatus) {
            case "Krieg":
                return "Frieden";
            case "Frieden":
                return "Neutral";
            case "Neutral":
                return "Krieg";
            default:
                return "Unbekannt";
        }
    }



}
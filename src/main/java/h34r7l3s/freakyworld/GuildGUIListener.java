package h34r7l3s.freakyworld;



import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.EntityType;


import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GuildGUIListener implements Listener {

    private final JavaPlugin plugin;
    private GuildManager guildManager;
    private Map<Player, PlayerState> playerStates = new HashMap<>();
    private Map<Player, Guild> viewedGuilds = new HashMap<>();

    public enum PlayerState {
        NONE,
        CREATING_GUILD,
        ADDING_MEMBER,
        REMOVING_MEMBER,  // Neu hinzugefügt
        VIEWING_GUILD,
        SETTING_DESCRIPTION
    }


    public GuildGUIListener(JavaPlugin plugin) {
        this.plugin = plugin;
        this.guildManager = new GuildManager();
    }

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

    private void openGuildMenu(Player player) {
        Inventory guildMenu = Bukkit.createInventory(null, 27, "Gilden Menü");

        // Anzeigen aller Gilden
        int slot = 0;
        for (Guild guild : GuildManager.getAllGuilds()) {
            ItemStack guildItem = new ItemStack(Material.PAPER);
            ItemMeta meta = guildItem.getItemMeta();
            meta.setDisplayName(guild.getName());
            guildItem.setItemMeta(meta);

            guildMenu.setItem(slot, guildItem);
            slot++;
        }

        // Icon zum Gründen einer neuen Gilde
        ItemStack createGuildItem = new ItemStack(Material.GREEN_WOOL);
        ItemMeta createGuildMeta = createGuildItem.getItemMeta();
        createGuildMeta.setDisplayName("Neue Gilde gründen");
        createGuildItem.setItemMeta(createGuildMeta);
        guildMenu.setItem(25, createGuildItem);

        player.openInventory(guildMenu);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        PlayerState state = playerStates.getOrDefault(player, PlayerState.NONE);
        ItemStack currentItem = event.getCurrentItem();

        if (currentItem == null || currentItem.getItemMeta() == null) return;

        String inventoryTitle = event.getView().getTitle();

        if (inventoryTitle.equals("Gilden Menü")) {
            event.setCancelled(true);
            String itemName = currentItem.getItemMeta().getDisplayName();
            Guild clickedGuild = guildManager.getGuild(itemName);

            if ("Neue Gilde gründen".equals(itemName)) {
                playerStates.put(player, PlayerState.CREATING_GUILD);
                player.closeInventory();
                player.sendMessage("Bitte gebe den Namen der neuen Gilde im Chat ein.");
            } else if (clickedGuild != null) {
                viewedGuilds.put(player, clickedGuild);
                showGuildOptions(player, clickedGuild);
            }
        } else if (inventoryTitle.startsWith("Gildenoptionen für ")) {
            event.setCancelled(true);
            String itemName = currentItem.getItemMeta().getDisplayName();
            Guild guild = viewedGuilds.get(player);
            if (guild == null) return;

            switch (itemName) {
                case "Mitglied hinzufügen":
                    playerStates.put(player, PlayerState.ADDING_MEMBER);
                    openAddMemberMenu(player);
                    break;
                case "Mitglied entfernen":
                    playerStates.put(player, PlayerState.REMOVING_MEMBER);
                    openRemoveMemberMenu(player, guild);
                    break;
                case "Gilde löschen":
                    if (guild.isMember(player.getName()) && guild.getMemberRank(player.getName()) == Guild.GuildRank.LEADER) {
                        guildManager.deleteGuild(guild.getName());
                        player.sendMessage("Die Gilde " + guild.getName() + " wurde gelöscht.");
                        player.closeInventory();
                    }
                    break;
                case "Gilden-Nachrichten anzeigen":
                    openGuildMessagesMenu(player, guild);
                    break;
                case "Rang ändern":
                    openChangeRankMenu(player, guild);
                    break;
                case "Schatztruhe öffnen":
                    openTreasuryMenu(player, guild);
                    break;
            }
        } else if (inventoryTitle.equals("Spieler einladen")) {
            event.setCancelled(true);
            if (!(currentItem.getItemMeta() instanceof SkullMeta)) return;
            SkullMeta skullMeta = (SkullMeta) currentItem.getItemMeta();
            if (skullMeta.getOwningPlayer() == null) return;

            String invitedPlayerName = skullMeta.getOwningPlayer().getName();
            Player invitedPlayer = Bukkit.getPlayer(invitedPlayerName);
            if (invitedPlayer == null) return;

            Guild guild = viewedGuilds.get(player);
            if (guild == null) return;

            guild.addMember(invitedPlayer.getName(), Guild.GuildRank.MEMBER);
            player.sendMessage(invitedPlayer.getName() + " wurde zu " + guild.getName() + " hinzugefügt!");
            invitedPlayer.sendMessage("Du wurdest zu " + guild.getName() + " hinzugefügt!");
        } else if (inventoryTitle.equals("Mitglied entfernen")) {
            event.setCancelled(true);
            Guild guild = viewedGuilds.get(player);
            if (guild == null) return;

            String memberName = currentItem.getItemMeta().getDisplayName();
            guild.removeMember(memberName);
            player.sendMessage(memberName + " wurde aus " + guild.getName() + " entfernt.");
        } else if (inventoryTitle.equals("Rang ändern")) {
            event.setCancelled(true);
            if (!(currentItem.getItemMeta() instanceof SkullMeta)) return;
            SkullMeta skullMeta = (SkullMeta) currentItem.getItemMeta();
            if (skullMeta.getOwningPlayer() == null) return;

            String memberName = skullMeta.getOwningPlayer().getName();
            Guild guild = viewedGuilds.get(player);
            if (guild == null) return;

            Guild.GuildRank currentRank = guild.getMemberRank(memberName);
            Guild.GuildRank newRank = (currentRank == Guild.GuildRank.LEADER)
                    ? Guild.GuildRank.OFFICER
                    : (currentRank == Guild.GuildRank.OFFICER)
                    ? Guild.GuildRank.MEMBER
                    : Guild.GuildRank.OFFICER;
            guild.setMemberRank(memberName, newRank);
            player.sendMessage(memberName + "'s Rang wurde zu " + newRank.getDisplayName() + " geändert.");
        } else if (inventoryTitle.equals("Gilden-Schatz")) {
            Guild guild = viewedGuilds.get(player);
            if (guild == null) return;

            // Bei Schließung des Inventars speichern Sie die Items in der Datenstruktur Ihrer Gilde
            // Hier nur ein einfacher Ansatz:
            guild.getTreasury().clear();
            for (ItemStack item : event.getInventory().getContents()) {
                if (item != null) {
                    guild.getTreasury().put(item.getType(), item.getAmount());
                }
            }
        }
    }


    private void openGuildMessagesMenu(Player player, Guild guild) {
        Inventory guildMessagesMenu = Bukkit.createInventory(null, 54, "Gilden-Nachrichten");
        for (String message : guild.getMessages()) {
            ItemStack messageItem = new ItemStack(Material.PAPER);
            ItemMeta messageMeta = messageItem.getItemMeta();
            messageMeta.setDisplayName(message);
            messageItem.setItemMeta(messageMeta);
            guildMessagesMenu.addItem(messageItem);
        }
        player.openInventory(guildMessagesMenu);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Guild guild = guildManager.getPlayerGuild(player.getName());
        if (guild != null) {
            for (String message : guild.getMessages()) {
                player.sendMessage(message);
            }
        }
    }


    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        PlayerState state = playerStates.getOrDefault(player, PlayerState.NONE);

        if (state == PlayerState.CREATING_GUILD) {
            event.setCancelled(true);  // Verhindert, dass die Nachricht im Chat erscheint

            String guildName = event.getMessage();  // Get the guild name from the chat message

            boolean success = guildManager.createGuild(guildName, player.getName());
            if (success) {
                player.sendMessage("Die Gilde " + guildName + " wurde erfolgreich erstellt!");
            } else {
                player.sendMessage("Es gab einen Fehler beim Erstellen der Gilde. Möglicherweise existiert bereits eine Gilde mit diesem Namen.");
            }
        }
    }
    private void openChangeRankMenu(Player player, Guild guild) {
        Inventory changeRankMenu = Bukkit.createInventory(null, 54, "Rang ändern");
        for (String member : guild.getMembers()) {
            if (!member.equals(player.getName())) { // Der Spieler kann seinen eigenen Rang nicht ändern
                ItemStack memberItem = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta skullMeta = (SkullMeta) memberItem.getItemMeta();
                skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(member));

                memberItem.setItemMeta(skullMeta);
                changeRankMenu.addItem(memberItem);
            }
        }
        player.openInventory(changeRankMenu);
    }




    private void showGuildOptions(Player player, Guild guild) {
        Inventory guildOptions = Bukkit.createInventory(null, 27, "Gildenoptionen für " + guild.getName());

        // Option, um Mitglieder hinzuzufügen
        if (guild.isMember(player.getName())) {
            ItemStack addMemberItem = new ItemStack(Material.BLUE_WOOL);
            ItemMeta addMemberMeta = addMemberItem.getItemMeta();
            addMemberMeta.setDisplayName("Mitglied hinzufügen");
            addMemberItem.setItemMeta(addMemberMeta);
            guildOptions.setItem(0, addMemberItem);

            // Option, um Mitglieder zu entfernen
            ItemStack removeMemberItem = new ItemStack(Material.RED_WOOL);
            ItemMeta removeMemberMeta = removeMemberItem.getItemMeta();
            removeMemberMeta.setDisplayName("Mitglied entfernen");
            removeMemberItem.setItemMeta(removeMemberMeta);
            guildOptions.setItem(1, removeMemberItem);

            // Option, um die Gilden-Nachrichten anzuzeigen
            ItemStack messagesItem = new ItemStack(Material.BOOK);
            ItemMeta messagesMeta = messagesItem.getItemMeta();
            messagesMeta.setDisplayName("Gilden-Nachrichten anzeigen");
            messagesItem.setItemMeta(messagesMeta);
            guildOptions.setItem(2, messagesItem);

            // Option, um den Rang zu ändern
            ItemStack changeRankItem = new ItemStack(Material.GOLDEN_SWORD);
            ItemMeta changeRankMeta = changeRankItem.getItemMeta();
            changeRankMeta.setDisplayName("Rang ändern");
            changeRankItem.setItemMeta(changeRankMeta);
            guildOptions.setItem(3, changeRankItem);

            // Option, um die Gilden-Schatztruhe zu öffnen
            ItemStack treasuryItem = new ItemStack(Material.CHEST);
            ItemMeta treasuryMeta = treasuryItem.getItemMeta();
            treasuryMeta.setDisplayName("Gilden-Schatz öffnen");
            treasuryItem.setItemMeta(treasuryMeta);
            guildOptions.setItem(4, treasuryItem);

            // Option, um zum Gildenheim zu teleportieren
            ItemStack homeItem = new ItemStack(Material.RED_BED);
            ItemMeta homeMeta = homeItem.getItemMeta();
            homeMeta.setDisplayName("Zum Gildenheim teleportieren");
            homeItem.setItemMeta(homeMeta);
            guildOptions.setItem(5, homeItem);
        }

        // Mitglieder und deren Ränge anzeigen
        int slot = 9;
        for (String member : guild.getMembers()) {
            ItemStack memberItem = new ItemStack(Material.PAPER);
            ItemMeta memberMeta = memberItem.getItemMeta();
            Guild.GuildRank rank = guild.getMemberRank(member);
            memberMeta.setDisplayName(member + " - " + rank.getDisplayName());
            memberItem.setItemMeta(memberMeta);
            guildOptions.setItem(slot, memberItem);
            slot++;
        }

        player.openInventory(guildOptions);
    }

    private void openTreasuryMenu(Player player, Guild guild) {
        Inventory treasuryMenu = Bukkit.createInventory(null, 54, "Gilden-Schatz");
        for (Map.Entry<Material, Integer> entry : guild.getTreasury().entrySet()) {
            ItemStack item = new ItemStack(entry.getKey(), entry.getValue());
            treasuryMenu.addItem(item);
        }
        player.openInventory(treasuryMenu);
    }

    private void openAddMemberMenu(Player player) {
        Inventory inviteMenu = Bukkit.createInventory(null, 54, "Spieler einladen");
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta skullMeta = (SkullMeta) playerHead.getItemMeta();
            skullMeta.setOwningPlayer(onlinePlayer);
            playerHead.setItemMeta(skullMeta);
            inviteMenu.addItem(playerHead);
        }
        player.openInventory(inviteMenu);
    }
    private void openRemoveMemberMenu(Player player, Guild guild) {
        Inventory removeMemberMenu = Bukkit.createInventory(null, 54, "Mitglied entfernen");
        for (String member : guild.getMembers()) {
            ItemStack memberItem = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta skullMeta = (SkullMeta) memberItem.getItemMeta();
            skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(UUID.fromString(member)));
            memberItem.setItemMeta(skullMeta);
            removeMemberMenu.addItem(memberItem);
        }
        player.openInventory(removeMemberMenu);
    }
    private Villager guildVillager;
    public void spawnGuildMasterVillager() {
        Location location = new Location(Bukkit.getWorld("world"), -251, 86, 1855);
        Villager villager = (Villager) Bukkit.getWorld("world").spawnEntity(location, EntityType.VILLAGER);
        villager.setCustomName("Gildenmeister");
        villager.setAI(false);
        guildVillager = villager;
    }
    public void removeGuildVillager() {
        if (guildVillager != null && !guildVillager.isDead()) {
            guildVillager.remove();
        }
    }
    // This could be a new command or another mechanism.
    public void setGuildHome(Player player) {
        Guild guild = viewedGuilds.get(player);
        if (guild != null && guild.getMemberRank(player.getName()) == Guild.GuildRank.LEADER) {
            guild.setHomeLocation(player.getLocation());
            player.sendMessage("Gildenheim gesetzt!");
        }
    }

    public void teleportToGuildHome(Player player) {
        Guild guild = viewedGuilds.get(player);
        if (guild != null) {
            Location homeLocation = guild.getHomeLocation();
            if (homeLocation != null && homeLocation.getWorld() != null && homeLocation.getWorld().isChunkLoaded(homeLocation.getBlockX() >> 4, homeLocation.getBlockZ() >> 4)) {
                player.teleport(homeLocation);
            } else {
                player.sendMessage("Der Gildenheim-Ort ist ungültig oder nicht geladen!");
            }
        }
    }



}

package h34r7l3s.freakyworld;



import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.EntityType;


import java.util.*;

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
        SETTING_DESCRIPTION,
        ADDING_MESSAGE   // Hier hinzugefügta
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
                if (guildManager.getPlayerGuild(player.getName()) == null) {
                    playerStates.put(player, PlayerState.CREATING_GUILD);
                    player.closeInventory();
                    player.sendMessage("Bitte gebe den Namen der neuen Gilde im Chat ein.");
                } else {
                    player.sendMessage("Du bist bereits in einer Gilde!");
                }
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
                // Inside the onInventoryClick method, under the Gildenoptionen für switch-case:
                case "Beschreibung setzen":
                    openSetDescriptionMenu(player);
                    break;

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
                default:
                    // Für zukünftige Funktionen, die hinzugefügt werden könnten
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

            if (guildManager.getPlayerGuild(invitedPlayerName) == null) {
                Guild guild = viewedGuilds.get(player);
                if (guild == null) return;

                guild.addMember(invitedPlayer.getName(), Guild.GuildRank.MEMBER);
                player.sendMessage(invitedPlayer.getName() + " wurde zu " + guild.getName() + " hinzugefügt!");
                invitedPlayer.sendMessage("Du wurdest zu " + guild.getName() + " hinzugefügt!");
            } else {
                player.sendMessage(invitedPlayerName + " ist bereits in einer Gilde!");
            }
        } else if (inventoryTitle.equals("Mitglied entfernen")) {
            event.setCancelled(true);
            Guild guild = viewedGuilds.get(player);
            if (guild == null) return;

            String memberName = currentItem.getItemMeta().getDisplayName().split(" - ")[0];  // Extrahiert den Namen des Mitglieds
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
        }if (currentItem.getType() == Material.BARRIER) {
            showGuildOptions(player, viewedGuilds.get(player));
            return;
        }if (inventoryTitle.equals("Gilden-Nachrichten")) {
            event.setCancelled(true);
            String itemName = currentItem.getItemMeta().getDisplayName();
            Guild guild = viewedGuilds.get(player);

            if ("Neue Nachricht hinzufügen".equals(itemName)) {
                playerStates.put(player, PlayerState.ADDING_MESSAGE);  // Neuer PlayerState für das Hinzufügen von Nachrichten
                player.closeInventory();
                player.sendMessage("Bitte gebe die neue Nachricht für deine Gilde im Chat ein.");
            } else {
                // Option zum Löschen einer Nachricht (können Sie später hinzufügen, wenn gewünscht)
            }
        }// Inside the onInventoryClick method:


    }

    private void giveSpecialBanner(Player player) {
        ItemStack banner = new ItemStack(Material.WHITE_BANNER);  // Oder eine andere Farbe
        ItemMeta meta = banner.getItemMeta();
        meta.setDisplayName("Gildenheim Banner");
        banner.setItemMeta(meta);
        player.getInventory().addItem(banner);
    }


    public void addGuildMessage(Player player, String message) {
        Guild guild = viewedGuilds.get(player);
        if (guild != null) {
            guild.addMessage(message);
            player.sendMessage("Nachricht zur Gilde hinzugefügt!");
        }
    }
    private void openGuildMessagesMenu(Player player, Guild guild) {
        Inventory guildMessagesMenu = Bukkit.createInventory(null, 54, "Gilden-Nachrichten");

        // Nachrichten anzeigen
        for (String message : guild.getMessages()) {
            ItemStack messageItem = new ItemStack(Material.PAPER);
            ItemMeta messageMeta = messageItem.getItemMeta();
            messageMeta.setDisplayName(message);
            messageItem.setItemMeta(messageMeta);
            guildMessagesMenu.addItem(messageItem);
        }

        // Rück-Knopf
        addBackButton(guildMessagesMenu);

        // Option zum Hinzufügen einer neuen Nachricht
        ItemStack addMessageItem = new ItemStack(Material.GREEN_WOOL);
        ItemMeta addMessageMeta = addMessageItem.getItemMeta();
        addMessageMeta.setDisplayName("Neue Nachricht hinzufügen");
        addMessageItem.setItemMeta(addMessageMeta);
        guildMessagesMenu.setItem(53, addMessageItem);  // Setzt es im letzten Slot

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
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        playerStates.remove(player);
        viewedGuilds.remove(player);
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        PlayerState state = playerStates.getOrDefault(player, PlayerState.NONE);

        if (state == PlayerState.CREATING_GUILD) {
            event.setCancelled(true);

            String guildName = event.getMessage();

            boolean success = guildManager.createGuild(guildName, player.getName());
            if (success) {
                player.sendMessage("Die Gilde " + guildName + " wurde erfolgreich erstellt!");
            } else {
                player.sendMessage("Es gab einen Fehler beim Erstellen der Gilde. Möglicherweise existiert bereits eine Gilde mit diesem Namen.");
            }
        } if (state == PlayerState.ADDING_MESSAGE) {
            event.setCancelled(true);
            addGuildMessage(player, event.getMessage());
        }else if (state == PlayerState.SETTING_DESCRIPTION) {
            event.setCancelled(true);

            Guild guild = viewedGuilds.get(player);
            if (guild != null) {
                String description = event.getMessage();
                guild.setDescription(description);
                player.sendMessage("Die Beschreibung deiner Gilde wurde erfolgreich aktualisiert!");
                playerStates.put(player, PlayerState.NONE);

            } else {
                player.sendMessage("Es gab einen Fehler beim Festlegen der Beschreibung. Du gehörst möglicherweise keiner Gilde an.");
            }
        }
    }
    @EventHandler
    public void onBannerPlace(BlockPlaceEvent event) {
        if (event.getBlock().getType() == Material.WHITE_BANNER) {
            Player player = event.getPlayer();
            Guild guild = guildManager.getPlayerGuild(player.getName());

            if (guild != null && guild.getMemberRank(player.getName()) == Guild.GuildRank.LEADER) {
                guild.setHomeLocation(event.getBlock().getLocation());
                player.sendMessage("Gildenheim am Banner gesetzt!");
            } else {
                player.sendMessage("Nur der Anführer kann den Gildenheim-Ort setzen!");
                event.setCancelled(true);
            }
        }
    }
    @EventHandler
    public void onBannerBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() == Material.WHITE_BANNER) {
            Player player = event.getPlayer();
            Guild guild = guildManager.getPlayerGuild(player.getName());

            if (guild != null && guild.getHomeLocation().equals(event.getBlock().getLocation())) {
                guild.setHomeLocation(null);
                player.sendMessage("Gildenheim am Banner entfernt!");
            }
        }
    }

    private void openChangeRankMenu(Player player, Guild guild) {
        Inventory changeRankMenu = Bukkit.createInventory(null, 54, "Rang ändern");
        for (String member : guild.getMembers()) {
            if (!member.equals(player.getName())) {
                ItemStack memberItem = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta skullMeta = (SkullMeta) memberItem.getItemMeta();
                skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(member));
                memberItem.setItemMeta(skullMeta);
                changeRankMenu.addItem(memberItem);
            }
        }
        addBackButton(changeRankMenu);  // Füge den Zurück-Button hinzu
        player.openInventory(changeRankMenu);
    }

    private void addBackButton(Inventory menu) {
        ItemStack backButton = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.setDisplayName("Zurück zum Gilden-Menü");
        backButton.setItemMeta(backMeta);
        menu.setItem(26, backButton);
    }


    private void showGuildOptions(Player player, Guild guild) {
        Inventory guildOptions = Bukkit.createInventory(null, 27, "Gildenoptionen für " + guild.getName());

        // Option, um Mitglieder hinzuzufügen
        if (guild.isMember(player.getName())) {
            ItemStack addMemberItem = new ItemStack(Material.BLUE_WOOL);
            ItemMeta addMemberMeta = addMemberItem.getItemMeta();
            ItemStack setDescriptionItem = new ItemStack(Material.WRITABLE_BOOK );
            ItemMeta setDescriptionMeta = setDescriptionItem.getItemMeta();
            setDescriptionMeta.setDisplayName("Beschreibung setzen");
            setDescriptionItem.setItemMeta(setDescriptionMeta);
            guildOptions.setItem(6, setDescriptionItem);
            addMemberMeta.setDisplayName("Mitglied hinzufügen");
            addMemberItem.setItemMeta(addMemberMeta);
            guildOptions.setItem(0, addMemberItem);

            // Option to set the guild home
            ItemStack setHomeItem = new ItemStack(Material.BEACON);
            ItemMeta setHomeMeta = setHomeItem.getItemMeta();
            setHomeMeta.setDisplayName("Gildenheim setzen");
            setHomeItem.setItemMeta(setHomeMeta);
            guildOptions.setItem(7, setHomeItem);


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
            skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(member));

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
        villager.setInvulnerable(true);
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

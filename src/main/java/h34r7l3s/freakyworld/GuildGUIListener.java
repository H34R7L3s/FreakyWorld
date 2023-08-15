package h34r7l3s.freakyworld;



import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

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
        VIEWING_GUILD
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

        if (event.getView().getTitle().equals("Gilden Menü")) {
            event.setCancelled(true);

            if (currentItem != null && currentItem.getItemMeta() != null) {
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
            }
        } else if (event.getView().getTitle().startsWith("Gildenoptionen für ")) {
            event.setCancelled(true);

            if (currentItem != null && currentItem.getItemMeta() != null) {
                String itemName = currentItem.getItemMeta().getDisplayName();
                if ("Mitglied hinzufügen".equals(itemName)) {
                    playerStates.put(player, PlayerState.ADDING_MEMBER);
                    openAddMemberMenu(player);
                }
            }
        } else if (event.getView().getTitle().equals("Spieler einladen")) {
            event.setCancelled(true);
            if (currentItem != null && currentItem.getItemMeta() instanceof SkullMeta) {
                SkullMeta skullMeta = (SkullMeta) currentItem.getItemMeta();
                if (skullMeta.getOwningPlayer() != null) {
                    String invitedPlayerName = skullMeta.getOwningPlayer().getName();
                    Player invitedPlayer = Bukkit.getPlayer(invitedPlayerName);
                    if (invitedPlayer != null) {
                        Guild guild = viewedGuilds.get(player);
                        if (guild != null) {
                            guild.addMember(invitedPlayer.getName(), Guild.GuildRank.MEMBER);
                            player.sendMessage(invitedPlayer.getName() + " wurde zu " + guild.getName() + " hinzugefügt!");
                            invitedPlayer.sendMessage("Du wurdest zu " + guild.getName() + " hinzugefügt!");
                        }
                    }
                }
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




    private void showGuildOptions(Player player, Guild guild) {
        Inventory guildOptions = Bukkit.createInventory(null, 27, "Gildenoptionen für " + guild.getName());

        // If the player is a member of the guild, provide the option to add members
        if (guild.isMember(player.getName())) {
            ItemStack addMemberItem = new ItemStack(Material.BLUE_WOOL);
            ItemMeta addMemberMeta = addMemberItem.getItemMeta();
            addMemberMeta.setDisplayName("Mitglied hinzufügen");
            addMemberItem.setItemMeta(addMemberMeta);
            guildOptions.setItem(0, addMemberItem);
        }

        // Display members and their ranks
        int slot = 9;  // Starting from the second row of the inventory for clarity
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
    private Villager guildVillager;

    public void removeGuildVillager() {
        if (guildVillager != null && !guildVillager.isDead()) {
            guildVillager.remove();
        }
    }


}

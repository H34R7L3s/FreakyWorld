package h34r7l3s.freakyworld;

import com.comphenix.protocol.utility.ChatExtensions;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.UUID;

public class VillagerInteractionHandler implements Listener {

    private final FreakyWorld plugin;
    private final CategoryManager categoryManager;
    private final EventLogic eventLogic;
    private final CategoryTaskHandler categoryTaskHandler;
    private final GuildManager guildManager;

    public VillagerInteractionHandler(FreakyWorld plugin, CategoryManager categoryManager, EventLogic eventLogic, GuildManager guildManager) {
        this.plugin = plugin;
        this.categoryManager = categoryManager;
        this.eventLogic = eventLogic;
        this.categoryTaskHandler = plugin.getCategoryTaskHandler();
        this.guildManager = guildManager;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerInteractWithVillager(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager)) return;

        Villager villager = (Villager) event.getRightClicked();
        PersistentDataContainer data = villager.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(plugin, "quest_villager");

        if (data.has(key, PersistentDataType.STRING) && "bazar_villager".equals(data.get(key, PersistentDataType.STRING))) {
            Player player = event.getPlayer();
            openVillagerGUI(player);
        }
    }

    private void openVillagerGUI(Player player) {
        Inventory gui = Bukkit.createInventory(player, 9, "Quest Auswahl");

        // Zeigt die verbleibende Zeit bis zur nächsten Belohnung an
        ItemStack timeItem = new ItemStack(Material.CLOCK);
        ItemMeta timeMeta = timeItem.getItemMeta();
        timeMeta.setDisplayName(ChatColor.GOLD + "Zeit bis zur nächsten Belohnung");
        timeMeta.setLore(List.of("","",ChatColor.YELLOW + "Nächste Belohnung in: ", ChatColor.DARK_AQUA + plugin.getVillagerCategoryManager().formatTimeUntilNextReward()));
        timeItem.setItemMeta(timeMeta);
        gui.setItem(0, timeItem);

        // Belohnungen für den Spieler anzeigen
        List<ItemStack> rewards = plugin.getVillagerCategoryManager().getStoredRewardsForPlayer(player.getUniqueId());
        if (!rewards.isEmpty()) {
            ItemStack rewardItem = new ItemStack(Material.CHEST);
            ItemMeta meta = rewardItem.getItemMeta();
            meta.setDisplayName("Belohnungen abholen");
            meta.setLore(List.of( ChatColor.LIGHT_PURPLE +  "Klicke hier, um deine gespeicherten Belohnungen abzuholen."));
            rewardItem.setItemMeta(meta);
            gui.setItem(7, rewardItem);
        } else {
            // Debug: Keine Belohnungen gefunden
            player.sendMessage("Es wurden keine Belohnungen für dich gefunden.");
        }

        String currentCategory = plugin.getVillagerCategoryManager().getCurrentCategory();
        Material requiredMaterial = Material.matchMaterial(categoryManager.getTasksForCategory(currentCategory).get(0));



        if (requiredMaterial != null) {
            if (player.hasMetadata("quest1Accepted")) {
                ItemStack soloSubmissionSlot = new ItemStack(requiredMaterial);
                ItemMeta soloMeta = soloSubmissionSlot.getItemMeta();
                soloMeta.setDisplayName("Abgabe für Solo-Quest");
                soloMeta.setLore(List.of(ChatColor.GOLD + "Gebe hier die gesammelten " + ChatColor.DARK_GREEN + requiredMaterial.name() + ChatColor.GOLD + " ab!", ChatColor.YELLOW + "Belohnungen werden " +ChatColor.GREEN + player.getName() + ChatColor.YELLOW+ " gutgeschrieben."));
                soloSubmissionSlot.setItemMeta(soloMeta);
                gui.setItem(4, soloSubmissionSlot);
            }

            if (player.hasMetadata("quest2Accepted")) {
                ItemStack guildSubmissionSlot = new ItemStack(requiredMaterial);
                ItemMeta guildMeta = guildSubmissionSlot.getItemMeta();
                guildMeta.setDisplayName("Abgabe für Gilden-Quest");

                guildMeta.setLore(List.of(ChatColor.GOLD + "Gebe hier die gesammelten " + ChatColor.DARK_GREEN + requiredMaterial.name() + ChatColor.GOLD + " ab!", ChatColor.YELLOW + "Belohnungen werden " +ChatColor.GREEN + "deiner Gilde" + ChatColor.YELLOW+ " gutgeschrieben."));
                guildSubmissionSlot.setItemMeta(guildMeta);
                gui.setItem(5, guildSubmissionSlot);
            }
        }else {
            player.sendMessage(ChatColor.RED+"Es konnte kein Item Material gefunden werden.");
        }
        ItemStack leaderBoard = createLeaderBoardItem(currentCategory);
        gui.setItem(8, leaderBoard);

        ItemStack quest1Item = new ItemStack(Material.PAPER);
        ItemMeta quest1Meta = quest1Item.getItemMeta();
        quest1Meta.setDisplayName("Grundversorgung");
        quest1Meta.setLore(List.of(ChatColor.GOLD + "Beschaffe: " + ChatColor.DARK_GREEN + categoryManager.getTasksForCategory(currentCategory).get(0), ChatColor.YELLOW + "Klicke "+ChatColor.RED + "hier"+ChatColor.YELLOW +", um jetzt teilzunehmen."));
        quest1Item.setItemMeta(quest1Meta);
        gui.setItem(1, quest1Item);

        ItemStack quest2Item = new ItemStack(Material.PAPER);
        ItemMeta quest2Meta = quest2Item.getItemMeta();
        quest2Meta.setDisplayName("Gilden Quest");
        quest2Meta.setLore(List.of(ChatColor.GOLD + "Gilden-Mitglieder sammeln gemeinsam Items für ihre Gilde.", ChatColor.YELLOW + "Klicke "+ChatColor.RED + "hier"+ChatColor.YELLOW +", um jetzt teilzunehmen."));
        quest2Item.setItemMeta(quest2Meta);
        gui.setItem(2, quest2Item);

        player.openInventory(gui);
        startLeaderboardUpdater(player, gui);

    }


    private void startLeaderboardUpdater(Player player, Inventory gui) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.getOpenInventory().getTopInventory().equals(gui)) {
                    // Aktualisiere das Item, das die verbleibende Wartezeit anzeigt
                    ItemStack timeItem = gui.getItem(0); // Das Zeit-Item ist an Position 0
                    ItemMeta timeMeta = timeItem.getItemMeta();
                    timeMeta.setLore(List.of("", ChatColor.YELLOW + "Nächste Belohnung in: ",
                            ChatColor.DARK_AQUA + plugin.getVillagerCategoryManager().formatTimeUntilNextReward()));

                    timeItem.setItemMeta(timeMeta);
                    player.updateInventory();
                } else {
                    this.cancel(); // Stoppt die Aufgabe, wenn der Spieler das Inventar schließt
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // Aktualisiert jede Sekunde
    }



    private ItemStack createLeaderBoardItem(String category) {
        ItemStack leaderBoard = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = leaderBoard.getItemMeta();

        UUID leadingPlayerUUID = eventLogic.getLeadingPlayerForCategory(category);
        if (leadingPlayerUUID != null) {
            OfflinePlayer leadingPlayer = Bukkit.getOfflinePlayer(leadingPlayerUUID); // Verwenden Sie OfflinePlayer, um sicherzustellen, dass auch abgemeldete Spieler unterstützt werden

            meta.setDisplayName(ChatColor.GOLD + "Top Spieler: " + category );
            List<String> lore = List.of(
                    ChatColor.YELLOW + "Statistik:",
                    "",
                    ChatColor.LIGHT_PURPLE + "" + leadingPlayer.getName(),
                    ChatColor.GOLD +"Punkte: " + ChatColor.DARK_GREEN + eventLogic.getPlayerScoreForCategory(leadingPlayerUUID, category)
            );
            meta.setLore(lore);

            if (meta instanceof org.bukkit.inventory.meta.SkullMeta) {
                ((org.bukkit.inventory.meta.SkullMeta) meta).setOwningPlayer(leadingPlayer);
            }
        } else {
            meta.setDisplayName("Kein führender Spieler");
            meta.setLore(List.of("Es gibt derzeit keinen führenden Spieler", "in der Kategorie " + category + "."));
        }

        leaderBoard.setItemMeta(meta);
        return leaderBoard;
    }


    private String formatTime(long seconds) {
        long minutes = seconds / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }




    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        InventoryView view = event.getView();
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        if ("Quest Auswahl".equals(view.getTitle())) {
            event.setCancelled(true);

            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

            String displayName = clickedItem.getItemMeta().getDisplayName();
            String category = plugin.getVillagerCategoryManager().getCurrentCategory();

            if ("Abgabe für Solo-Quest".equals(displayName)) {
                if (player.hasMetadata("quest1Accepted")) {
                    categoryTaskHandler.handleItemSubmission(player, clickedItem, category, false);
                } else {
                    player.sendMessage("Du musst die Quest 1 annehmen, bevor du Items abgeben kannst.");
                }
                player.closeInventory();
            } else if ("Abgabe für Gilden-Quest".equals(displayName)) {
                if (guildManager.isPlayerInGuild(player.getName())) {
                    if (player.hasMetadata("quest2Accepted")) {
                        categoryTaskHandler.handleItemSubmission(player, clickedItem, category, true);
                    } else {
                        player.sendMessage("Du musst die Gilden-Quest annehmen, bevor du Items abgeben kannst.");
                    }
                } else {
                    player.sendMessage("Du musst Mitglied einer Gilde sein, um diese Quest anzunehmen.");
                }
                player.closeInventory();
            } else if ("Belohnungen abholen".equals(displayName)) {
                boolean hasRewards = false;

                // Eigene Belohnungen abholen
                List<ItemStack> playerRewards = plugin.getVillagerCategoryManager().getStoredRewardsForPlayer(player.getUniqueId());
                if (!playerRewards.isEmpty()) {
                    for (ItemStack reward : playerRewards) {
                        player.getInventory().addItem(reward);
                    }
                    plugin.getVillagerCategoryManager().clearStoredRewardsForPlayer(player.getUniqueId());
                    player.sendMessage("Du hast alle gespeicherten Belohnungen abgeholt.");
                    hasRewards = true;
                } else {
                    player.sendMessage("Du hast keine gespeicherten Belohnungen.");
                }

                // Gildenbelohnungen abholen, falls der Spieler ein Gildenleiter ist
                Guild playerGuild = guildManager.getPlayerGuild(player.getName());
                if (playerGuild != null) {
                    UUID guildLeaderUUID = guildManager.getGuildLeader(playerGuild.getName());
                    if (guildLeaderUUID != null && guildLeaderUUID.equals(player.getUniqueId())) {
                        List<ItemStack> guildRewards = plugin.getVillagerCategoryManager().getStoredRewardsForPlayer(guildLeaderUUID);
                        if (!guildRewards.isEmpty()) {
                            for (ItemStack reward : guildRewards) {
                                player.getInventory().addItem(reward);
                            }
                            plugin.getVillagerCategoryManager().clearStoredRewardsForPlayer(guildLeaderUUID);
                            player.sendMessage("Du hast alle gespeicherten Belohnungen für deine Gilde abgeholt.");
                            hasRewards = true;
                        } else {
                            player.sendMessage("Deine Gilde wurde ebenfalls gut entlohnt.");
                        }
                    }
                }

                if (!hasRewards) {
                    player.sendMessage("Es gibt keine Belohnungen zum Abholen.");
                }

                player.closeInventory();
            }
            else {
                // Quest 1 oder Quest 2 annehmen
                if ("Grundversorgung".equals(displayName)) {
                    player.setMetadata("quest1Accepted", new FixedMetadataValue(plugin, true));
                    player.sendMessage("Du hast die Quest Grundversorgung angenommen.");
                } else if ("Gilden Quest".equals(displayName)) {
                    if (guildManager.isPlayerInGuild(player.getName())) {
                        player.setMetadata("quest2Accepted", new FixedMetadataValue(plugin, true));
                        player.sendMessage("Du hast die Gilden Quest angenommen.");
                    } else {
                        player.sendMessage("Du musst Mitglied einer Gilde sein, um diese Quest anzunehmen.");
                    }
                }
                player.closeInventory();
            }
        }
    }




}

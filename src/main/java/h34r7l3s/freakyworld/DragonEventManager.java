package h34r7l3s.freakyworld;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class DragonEventManager implements Listener, CommandExecutor {
    private final GameLoop gameLoop;
    private FreakyDragon currentDragon;
    private List<Player> currentParticipants;
    private final Set<UUID> playersWhoClickedAccept = new HashSet<>();
    private final Map<UUID, Integer> dragonKills = new HashMap<>();
    private final int EASY_MODE_THRESHOLD = 100;
    private final Map<String, String> pendingInvitations = new HashMap<>(); // Speichert Einladungen
    private JavaPlugin plugin;
    private GuildManager  guildManager ;




    public DragonEventManager(GameLoop gameLoop, GuildManager guildManager, JavaPlugin plugin) {
        this.plugin = plugin;
        this.gameLoop = gameLoop;
        this.guildManager = guildManager;

    }

    // Implementiere die onCommand-Methode
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return false;
        Player player = (Player) sender;

        if (command.getName().equalsIgnoreCase("dragon")) {
            if (args.length == 1) {
                if (args[0].equalsIgnoreCase("annehmen")) {
                    if (acceptDragonInvitation(player)) {
                        player.sendMessage(ChatColor.GREEN + "Du hast die Teilnahme am Drachen-Event erfolgreich angenommen.");
                    } else {
                        player.sendMessage(ChatColor.RED + "Es gibt keine offene Einladung.");
                    }
                } else if (args[0].equalsIgnoreCase("ablehnen")) {
                    if (declineDragonInvitation(player)) {
                        player.sendMessage(ChatColor.RED + "Du hast die Einladung zum Drachen-Event abgelehnt.");
                    } else {
                        player.sendMessage(ChatColor.RED + "Es gibt keine offene Einladung.");
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "Ungültiger Befehl. Benutze /dragon [annehmen|ablehnen]");
                }
            } else {
                player.sendMessage(ChatColor.RED + "Ungültige Anzahl an Argumenten.");
            }
            return true;
        }

        return false;
    }


    // Spieler nimmt die Einladung an
    private boolean acceptDragonInvitation(Player player) {
        if (pendingInvitations.containsKey(player.getName())) {
            playersWhoClickedAccept.add(player.getUniqueId());
            pendingInvitations.remove(player.getName());
            return true;
        }
        return false;
    }


    // Spieler lehnt die Einladung ab
    private boolean declineDragonInvitation(Player player) {
        if (pendingInvitations.containsKey(player.getName())) {
            pendingInvitations.remove(player.getName());
            return true;
        }
        return false;
    }
    // Einladung senden
    // Einladung senden
    public void sendDragonInvitation(Player player, String dragonEventName, Player invitedPlayer) {
        if (invitedPlayer != null) {
            // Einladung speichern
            pendingInvitations.put(invitedPlayer.getName(), dragonEventName);

            // Nachricht mit klickbaren Optionen senden
            TextComponent message = new TextComponent(ChatColor.GREEN + "Du wurdest zum Drachen-Event '" + dragonEventName + "' eingeladen. ");
            TextComponent accept = new TextComponent("[Annehmen]");
            accept.setColor(net.md_5.bungee.api.ChatColor.GREEN);
            accept.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/dragon annehmen"));
            accept.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Klicke hier, um die Einladung anzunehmen.").create()));

            TextComponent decline = new TextComponent("[Ablehnen]");
            decline.setColor(net.md_5.bungee.api.ChatColor.RED);
            decline.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/dragon ablehnen"));
            decline.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Klicke hier, um die Einladung abzulehnen.").create()));

            message.addExtra(accept);
            message.addExtra(" ");
            message.addExtra(decline);

            invitedPlayer.spigot().sendMessage(message);
        }
    }


    public void startDragonEventWithConfirmation(Player initiator, String difficulty, List<Player> participants) {
        // Kosten für das Event
        int cost = 15000;

        // Überprüfe und ziehe Freaky XP ab
        if (!gameLoop.deductFreakyXP(initiator, cost)) {
            return; // Abbruch, wenn nicht genügend XP vorhanden sind
        }

        // Leere die Liste der Spieler, die auf "Annehmen" geklickt haben
        playersWhoClickedAccept.clear();

        // Sende die Einladung an alle Teilnehmer
        for (Player player : participants) {
            sendDragonInvitation(initiator, difficulty, player); // Einladung an jeden Spieler
        }

        // Nach 10 Sekunden teleportiere die Spieler, die auf "Annehmen" geklickt haben
        new BukkitRunnable() {
            @Override
            public void run() {
                List<Player> acceptedPlayers = new ArrayList<>();
                for (Player participant : participants) {
                    if (playersWhoClickedAccept.contains(participant.getUniqueId())) {
                        acceptedPlayers.add(participant);
                    } else {
                        participant.sendMessage(ChatColor.RED + "Du hast die Chance verpasst, am Event teilzunehmen.");
                    }
                }

                if (!acceptedPlayers.isEmpty()) {
                    startDragonEvent(initiator, difficulty, acceptedPlayers);
                } else {
                    initiator.sendMessage(ChatColor.RED + "Es hat niemand am Drachen-Event teilgenommen. Kosten werden nicht erstattet.");
                }
            }
        }.runTaskLater(plugin, 200L); // 10 Sekunden Wartezeit (200 Ticks)
    }
    public void openDragonDifficultyUI(Player player) {
        Inventory dragonInventory = Bukkit.createInventory(null, 9, "Wähle die Schwierigkeit");

        // Einfacher Modus Icon erstellen
        ItemStack easyMode = new ItemStack(Material.GREEN_WOOL);
        ItemMeta easyMeta = easyMode.getItemMeta();
        if (easyMeta != null) {
            easyMeta.setDisplayName(ChatColor.GREEN + "Einfach");
            List<String> easyLore = new ArrayList<>();
            easyLore.add(ChatColor.GRAY + "» Der einfache Weg für jene, die die Grundlagen");
            easyLore.add(ChatColor.GRAY + "des Drachenkampfes meistern möchten.");
            easyLore.add("");
            easyLore.add(ChatColor.DARK_GREEN + "» Ideal für Anfänger und Kämpfer,");
            easyLore.add(ChatColor.DARK_GREEN + "die ihre Stärke noch aufbauen.");
            easyMeta.setLore(easyLore);
            easyMode.setItemMeta(easyMeta);
        }

        // Schwerer Modus Icon erstellen
        ItemStack hardMode = new ItemStack(Material.RED_WOOL);
        ItemMeta hardMeta = hardMode.getItemMeta();
        if (hardMeta != null) {
            hardMeta.setDisplayName(ChatColor.RED + "Schwer");
            List<String> hardLore = new ArrayList<>();
            hardLore.add(ChatColor.GRAY + "» Ein Modus für wahre Drachenjäger,");
            hardLore.add(ChatColor.GRAY + "die ihre Kräfte bis an ihre Grenzen testen wollen.");
            hardLore.add("");
            hardLore.add(ChatColor.DARK_RED + "» Nur die erfahrensten Krieger wagen");
            hardLore.add(ChatColor.DARK_RED + "sich in diese brutale Herausforderung.");
            hardMeta.setLore(hardLore);
            hardMode.setItemMeta(hardMeta);
        }

        // Items in das Inventar setzen
        dragonInventory.setItem(3, easyMode);
        dragonInventory.setItem(5, hardMode);

        // Prüfen, ob der Spieler den schweren Modus freigeschaltet hat
        if (dragonKills.getOrDefault(player.getUniqueId(), 0) < EASY_MODE_THRESHOLD) {
            hardMode.setType(Material.GRAY_WOOL);
            hardMeta.setDisplayName(ChatColor.RED + "Schwer (gesperrt)");
            List<String> lockedLore = new ArrayList<>();
            lockedLore.add(ChatColor.GRAY + "» Fordere 100 Drachen im einfachen Modus heraus,");
            lockedLore.add(ChatColor.GRAY + "um den Weg zum schwereren Kampf freizuschalten.");
            lockedLore.add("");
            lockedLore.add(ChatColor.DARK_RED + "» Werde ein wahrer Drachenjäger und");
            lockedLore.add(ChatColor.DARK_RED + "verdiene dir den Zugang zu dieser Herausforderung.");
            hardMeta.setLore(lockedLore);
            hardMode.setItemMeta(hardMeta);
        }

        player.openInventory(dragonInventory);
        player.sendMessage(ChatColor.YELLOW + "» Wähle die Schwierigkeit deines Kampfes. Die Einladung wird im Chat erscheinen.");
    }

    public void startDragonEvent(Player initiator, String difficulty, List<Player> participants) {
        World endWorld = Bukkit.getWorld("world_the_end");
        Location spawnLocation = new Location(endWorld, 0, 180, 0);

        // Teleportiere die Teilnehmer zum Event
        for (Player p : participants) {
            teleportToEvent(p);
        }

         // Drachen spawnen
        currentDragon = new FreakyDragon(endWorld, spawnLocation, difficulty, plugin);



        // Teilnehmer informieren
        for (Player p : participants) {
            p.sendMessage(ChatColor.GREEN + "Das " + difficulty + " Drachen-Event beginnt jetzt!");
        }

        currentParticipants = participants;
    }

    private void teleportToEvent(Player player) {
        World endWorld = Bukkit.getWorld("world_the_end");
        Location endLocation = new Location(endWorld, 80, 80, 0);
        player.teleport(endLocation);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClickDragonFight(InventoryClickEvent event) {
        if (event.getView().getTitle().equals("Wähle die Schwierigkeit")) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null || !event.getCurrentItem().hasItemMeta()) return;

            Player player = (Player) event.getWhoClicked();
            String difficulty = ChatColor.stripColor(event.getCurrentItem().getItemMeta().getDisplayName());

            handleDragonDifficultySelection(player, difficulty);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDragonDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof EnderDragon) {
            LivingEntity entity = event.getEntity();
            if (entity.equals(currentDragon.getDragon())) {
                boolean isGuildEvent = currentParticipants.stream()
                        .anyMatch(player -> player.getMetadata("isGuildEvent").stream()
                                .anyMatch(meta -> meta.asBoolean()));

                // Belohnung für alle Teilnehmer
                for (Player participant : currentParticipants) {
                    if (isGuildEvent) {
                        // Retrieve the guild members of the participant

                        Guild participantGuild = guildManager.getPlayerGuild(participant.getName());
                        if (participantGuild != null) {
                            for (String guildMemberName : participantGuild.getMembers()) {
                                Player guildMember = Bukkit.getPlayer(guildMemberName);
                                if (guildMember != null && guildMember.isOnline()) {
                                    trackDragonKill(guildMember.getUniqueId()); // Track kill for the guild member
                                    saveDragonKills(guildMember.getUniqueId()); // Save the updated kill count
                                    guildMember.sendMessage(ChatColor.GOLD + "Deine Gilde hat den Drachen besiegt! Dein Drachen-Kill-Score wurde erhöht.");
                                }
                            }
                        }
                    } else {
                        // Normal event, just the participant gets the kill
                        trackDragonKill(participant.getUniqueId());
                        saveDragonKills(participant.getUniqueId());
                        participant.sendMessage(ChatColor.GOLD + "Du hast den Drachen besiegt! Dein Drachen-Kill-Score wurde erhöht.");
                    }
                }

                currentDragon = null; // Reset the current dragon
            }
        }
    }





    public void trackDragonKill(UUID playerId) {
        // Check if the playerId exists in the map. If not, load from database.
        if (!dragonKills.containsKey(playerId)) {
            int killsFromDatabase = gameLoop.loadDragonKills(playerId); // Load kills from database
            dragonKills.put(playerId, killsFromDatabase); // Initialize the map with the database value
        }

        // Now add 1 to the value stored in memory
        int currentKills = dragonKills.get(playerId);
        dragonKills.put(playerId, currentKills + 1);
    }



    public void handleDragonDifficultySelection(Player player, String difficulty) {
        //player.sendMessage("handleDragonDifficultySelection aufgerufen mit Schwierigkeit: " + difficulty);

        boolean isGuildEvent = player.getMetadata("isGuildEvent").get(0).asBoolean();
        List<Player> participants = gameLoop.getParticipants(player, isGuildEvent);

        if (difficulty.equals("Schwer") && !canPlayHardMode(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Du hast noch nicht genug Drachen im einfachen Modus besiegt, um den schweren Modus freizuschalten.");
            return;
        }

        startDragonEventWithConfirmation(player, difficulty, participants);
    }
    public boolean canPlayHardMode(UUID playerId) {
        return gameLoop.loadDragonKills(playerId) >= EASY_MODE_THRESHOLD;
    }
    public void saveDragonKills(UUID playerId) {
        int kills = dragonKills.getOrDefault(playerId, 0);
        // Now it correctly passes both the playerId and kills to GameLoop's save method
        gameLoop.saveDragonKills(playerId, kills);
    }



}

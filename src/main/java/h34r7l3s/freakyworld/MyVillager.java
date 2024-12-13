package h34r7l3s.freakyworld;


import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.metadata.Metadatable;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.logging.Logger;

public class MyVillager implements Listener {

    private final JavaPlugin plugin;
    private final NamespacedKey villagerKey;
    private DiscordBot discordBot;
    public MyVillager(JavaPlugin plugin, DiscordBot discordBot) {

        this.plugin = plugin;
        this.villagerKey = new NamespacedKey(plugin, "Unbekannter");
        this.discordBot = discordBot;
    }

    @EventHandler
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (event.getRightClicked().getType() == EntityType.VILLAGER) {
            Villager villager = (Villager) event.getRightClicked();
            Player player = event.getPlayer();

            // Überprüfe, ob der Villager ein benutzerdefiniertes NameTag hat
            if (villager.getCustomName() != null) {
                // Überprüfe, ob der Villager den gewünschten Namen hat
                if (villager.getCustomName().equals("Unbekannter")) {
                    // Setze den Villager als unverwundbar und verhindere, dass er sich bewegt
                    villager.setInvulnerable(false);
                    villager.setAI(false);

                    // Definiere die Sätze, die der Villager sagen soll
                    List<String> sentences = Arrays.asList(
                            "Verrueckt was hier abgeht, oder?!"

                    );

                    // Starte eine verzögerte Aufgabe für jeden Satz
                    new BukkitRunnable() {
                        int index = 0;

                        @Override
                        public void run() {
                            if (index < sentences.size()) {
                                player.sendMessage(villager.getCustomName() + ": " + sentences.get(index));
                                index++;
                            } else {
                                // Öffne die GUI über den Befehl /battlepass
                                Bukkit.getScheduler().runTaskLater(plugin, () -> player.performCommand("battlepass"), 1L);
                                cancel();  // Beende die Aufgabe
                            }
                        }
                    }.runTaskTimer(plugin, 0L, 60L);  // Startet sofort und wiederholt alle 60 Ticks (3 Sekunden)

                    // Verhindere, dass der Villager normal handelt (z. B. Handeln oder Reden)
                    event.setCancelled(true);
                }
            }
        }
    }


    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity().getType() == EntityType.VILLAGER) {
            Villager villager = (Villager) event.getEntity();
            if (villager.getCustomName() != null && villager.getCustomName().equals("Unbekannter")) {
                if (event instanceof EntityDamageByEntityEvent) { // Prüfen, ob der Schaden von einer Entität verursacht wurde
                    EntityDamageByEntityEvent entityDamageByEntityEvent = (EntityDamageByEntityEvent) event;
                    if (entityDamageByEntityEvent.getDamager() instanceof Player) { // Prüfen, ob der Verursacher ein Spieler ist
                        Player player = (Player) entityDamageByEntityEvent.getDamager();
                        if (!player.getGameMode().equals(GameMode.CREATIVE)) {
                            event.setCancelled(true); // Schaden nur blockieren, wenn der Spieler nicht im Kreativmodus ist
                        }
                    } else {
                        event.setCancelled(true); // Schaden von Nicht-Spielern blockieren
                    }
                } else {
                    event.setCancelled(true); // Schaden von Nicht-Entitäten blockieren
                }
            }
        }
    }



    @EventHandler
    public void onEntityTarget(EntityTargetEvent event) {
        if (event.getEntity().getType() == EntityType.VILLAGER) {
            Villager villager = (Villager) event.getEntity();
            if (villager.getCustomName() != null && villager.getCustomName().equals("Unbekannter")) {
                event.setCancelled(true);
            }
        }
    }

    private void setMetadata(Metadatable metadatable, String value) {
        metadatable.setMetadata(villagerKey.toString(), new FixedMetadataValue(plugin, value));
    }

    private String getMetadata(Metadatable metadatable) {
        for (MetadataValue metadata : metadatable.getMetadata(villagerKey.toString())) {
            if (metadata.getOwningPlugin() == plugin) {
                return metadata.asString();
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        var player = event.getPlayer();
        var message = event.getMessage();

        // Log every command for debugging
        Logger.getLogger("Minecraft").info(player.getName() + " executed command: " + message);

        // Check if the command starts with /bp or a variant like bpass
        if (message.equalsIgnoreCase("/bp") || message.toLowerCase().startsWith("/bp ") ||
                message.toLowerCase().matches("^/bp.*")) {
            event.setMessage("/nocommand");
            Logger.getLogger("Minecraft").info("Cancelled /bp-related command for player: " + player.getName());
        }
    }


    //Event
    private final Map<UUID, EventInfo> playerEventInfoMap = new HashMap<>();
    private boolean isEventActive = false;

    private BossBar eventBossBar;
    public void createCustomVillager() {
        World world = Bukkit.getWorld("world");
        Location location = new Location(world, -59, 14, -54);
        Villager dave = (Villager) world.spawnEntity(location, EntityType.VILLAGER);
        dave.setCustomName("Dave");
        dave.setCustomNameVisible(true);
        dave.setAI(false); // Optional: Schaltet KI aus
        dave.setInvulnerable(true); // Optional: Macht unverwundbar
    }
    public void removeVillagerAtPosition(Location location) {
        World world = location.getWorld();
        List<Entity> entities = world.getEntities();
        for (Entity entity : entities) {
            if (entity.getType() == EntityType.VILLAGER) {
                Villager villager = (Villager) entity;
                Location villagerLocation = villager.getLocation();
                if (villagerLocation.getBlockX() == location.getBlockX() &&
                        villagerLocation.getBlockY() == location.getBlockY() &&
                        villagerLocation.getBlockZ() == location.getBlockZ()) {
                    villager.remove();
                    break; // Beende die Schleife, nachdem der Villager entfernt wurde
                }
            }
        }
    }


    @EventHandler
    public void onPlayerInteractEntity2(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof Villager) {
            Villager villager = (Villager) event.getRightClicked();
            Location daveLocation = new Location(villager.getWorld(), -59, 14, -54);
            Player player = event.getPlayer();

            // Überprüfe, ob der Villager "Dave" ist und an der erwarteten Position steht
            if ("Dave".equals(ChatColor.stripColor(villager.getCustomName())) && villager.getLocation().equals(daveLocation)) {
                event.setCancelled(true); // Verhindert weitere Interaktionen
                villager.setAI(false);
                villager.setInvulnerable(true);

                if (isEventActive) {
                    player.sendMessage(ChatColor.RED + "Es läuft bereits ein Event. Bitte warte, bis es vorbei ist.");
                    return;
                }

                if (playerEventInfoMap.containsKey(player.getUniqueId())) {
                    player.sendMessage(ChatColor.RED + "Du hast bereits Eventinformationen eingegeben. Keine weiteren Änderungen möglich.");
                    return;
                }

                playerEventInfoMap.put(player.getUniqueId(), new EventInfo(player.getUniqueId()));
                player.sendMessage(ChatColor.YELLOW + "Wann geht es los? (in Minuten):");
                player.sendMessage(ChatColor.YELLOW + "(Tippe Abbruch zum beenden)");
            }
        }
    }


    @EventHandler
    public void onPlayerChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        if (playerEventInfoMap.containsKey(playerUUID)) {
            EventInfo eventInfo = playerEventInfoMap.get(playerUUID);

            String message = event.getMessage();

            // Prüfen, ob der Spieler "Abbruch" eingegeben hat
            if (message.equalsIgnoreCase("Abbruch")) {
                playerEventInfoMap.remove(playerUUID);
                player.sendMessage(ChatColor.RED + "Eventerstellung abgebrochen.");
                event.setCancelled(true);
                return;
            }

            try {
                int inputMinutes = Integer.parseInt(event.getMessage());
                if (inputMinutes >= 0) {
                    if (eventInfo.getStartTimestamp() == 0) {
                        eventInfo.setStartTimestamp(inputMinutes * 60); // Umrechnung in Sekunden
                        player.sendMessage(ChatColor.YELLOW + "Wie lange soll dein Event gehen? (in Minuten):");
                    } else if (eventInfo.getDuration() == 0) {
                        eventInfo.setDuration(inputMinutes * 60); // Umrechnung in Sekunden
                        player.sendMessage(ChatColor.GREEN + "Du hast erfolgreich ein Event gestartet. Viel Spass!!");

                        String roleID = "Freak"; // Ersetzen Sie dies mit der tatsächlichen ID der "Freak" Rolle
                        discordBot.sendEventMessageToChannel("1046916252083433632", player.getName(), eventInfo.getStartTimestamp() / 60, eventInfo.getDuration() / 60, roleID);

                        playerEventInfoMap.remove(playerUUID);
                        startEvent(player, eventInfo);
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "Ungültige Eingabe. Bitte gib eine positive Zahl ein.");
                }
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Ungültige Eingabe. Bitte gib eine positive Zahl ein.");
            }

            event.setCancelled(true);
        }
    }


    private void startEvent(Player initiator, EventInfo eventInfo) {
        isEventActive = true; // Event wird gestartet

        // Erstellen einer BossBar für die Event-Benachrichtigung
        eventBossBar = Bukkit.createBossBar("Event startet in: ",
                BarColor.GREEN, BarStyle.SOLID);
        eventBossBar.setVisible(true);

        // Füge alle Spieler zur BossBar hinzu
        for (Player player : Bukkit.getOnlinePlayers()) {
            eventBossBar.addPlayer(player);
        }

        // Starte den ersten Timer für die Zeit bis zum Start des Events
        new BukkitRunnable() {
            int timeUntilStart = eventInfo.getStartTimestamp();

            @Override
            public void run() {
                if (timeUntilStart > 0) {
                    eventBossBar.setTitle("Event startet in: " + timeUntilStart / 60 + " Minuten");
                    eventBossBar.setProgress((double) timeUntilStart / eventInfo.getStartTimestamp());
                    timeUntilStart--;
                } else {
                    // Start des Events
                    eventBossBar.setTitle("Event gestartet von " + initiator.getName() +
                            " - Dauer: " + eventInfo.getDuration() / 60 + " Minuten");
                    startEventDurationTimer(eventInfo);
                    cancel(); // Beende den ersten Timer
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // Starte den Timer (alle 20 Ticks = 1 Sekunde)
    }

    private void startEventDurationTimer(EventInfo eventInfo) {
        new BukkitRunnable() {
            int timeLeft = eventInfo.getDuration();

            @Override
            public void run() {
                if (timeLeft > 0) {
                    eventBossBar.setProgress((double) timeLeft / eventInfo.getDuration());
                    timeLeft--;
                } else {
                    // Event ist abgelaufen
                    eventBossBar.removeAll(); // Entferne alle Spieler aus der BossBar
                    eventBossBar.setVisible(false);
                    isEventActive = false; // Setze isEventActive auf false, da das Event beendet ist
                    cancel(); // Beende den zweiten Timer
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // Starte den Timer (alle 20 Ticks = 1 Sekunde)
    }



}




/*

 */
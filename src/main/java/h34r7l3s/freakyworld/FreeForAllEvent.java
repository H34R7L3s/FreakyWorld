package h34r7l3s.freakyworld;

import org.bukkit.scheduler.BukkitRunnable;

public class FreeForAllEvent {
    private final FreakyWorld plugin;
    private final EventLogic eventLogic;

    public FreeForAllEvent(FreakyWorld plugin, EventLogic eventLogic) {
        this.plugin = plugin;
        this.eventLogic = eventLogic;

        startEvent();
    }

    public void startEvent() {
        new BukkitRunnable() {
            @Override
            public void run() {
                eventLogic.rewardTopPlayers();
            }
        }.runTaskTimer(plugin, 0, 20 * 60 * 60); // Jede Stunde
    }
}

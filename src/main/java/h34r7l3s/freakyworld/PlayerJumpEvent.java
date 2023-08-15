package h34r7l3s.freakyworld;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

public class PlayerJumpEvent extends PlayerEvent {
    private static final HandlerList handlers = new HandlerList();

    public PlayerJumpEvent(Player who) {
        super(who);
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}

package h34r7l3s.freakyworld;


import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class GuildManager {
    private static Map<String, Guild> guilds = new HashMap<>();

    // Diese Methode gibt jetzt eine Sammlung von allen Gilden zurück:
    protected static Collection<Guild> getAllGuilds() {
        return guilds.values();
    }


    public boolean createGuild(String name, String leader) {
        if (guilds.containsKey(name)) {
            // The guild already exists
            return false;
        }
        Guild newGuild = new Guild(name, leader);
        newGuild.addMember(leader, Guild.GuildRank.LEADER);
        guilds.put(name, newGuild);
        return true;
    }



    public Guild getGuild(String name) {
        return guilds.get(name);
    }

    public Guild getPlayerGuild(String playerName) {
        for (Guild guild : guilds.values()) {
            if (guild.isMember(playerName)) {
                return guild;
            }
        }
        return null;
    }

    public void deleteGuild(String name) {
        Guild guild = guilds.get(name);
        if (guild != null) {
            for (String member : guild.getMembers()) {
                Player player = Bukkit.getPlayer(member);
                if (player != null) {
                    player.sendMessage("Deine Gilde " + name + " wurde gelöscht.");
                }
            }
            guilds.remove(name);
        }
    }
}

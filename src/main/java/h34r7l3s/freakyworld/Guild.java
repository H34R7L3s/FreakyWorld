package h34r7l3s.freakyworld;


import org.bukkit.Location;
import org.bukkit.Material;

import java.util.*;

public class Guild {
    private String name;
    private Set<String> members;
    private Map<String, GuildRank> memberRanks;
    public Guild(String name, String leader) {
        // ... other initializations ...
        this.memberRanks = new HashMap<>();
        this.name = name;
        this.members = new HashSet<>();
        addMember(leader, GuildRank.LEADER);  // Adds the leader when the guild is created
    }
    private List<String> guildMessages = new ArrayList<>();
    public boolean isHomeSet() {
        return homeLocation != null;
    }

    public void addMessage(String message) {
        guildMessages.add(message);
    }
    private String description;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getMessages() {
        return guildMessages;
    }


    public enum GuildRank {
        LEADER("Anführer"),
        OFFICER("Offizier"),
        MEMBER("Mitglied");

        private final String displayName;

        GuildRank(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
    public String getName() {
        return name;
    }

    public void addMember(String member) {
        members.add(member);
    }
    private Location homeLocation;

    public Location getHomeLocation() {
        return homeLocation;
    }

    public void setHomeLocation(Location location) {
        this.homeLocation = location;
    }



    public boolean isMember(String member) {
        return members.contains(member);
    }

    public Set<String> getMembers() {
        return members;
    }


    public void addMember(String member, GuildRank rank) {
        members.add(member);
        memberRanks.put(member, rank);
    }
    public boolean hasPermission(String playerName, String permission) {
        // Implementieren Sie die Logik hier, um zu überprüfen, ob der Spieler die erforderliche Berechtigung hat.
        // Zum Beispiel:
        GuildRank rank = getMemberRank(playerName);
        if (permission.equals("remove_member")) {
            return rank == GuildRank.LEADER; // Nur der Anführer darf Mitglieder entfernen.
        }
        // Fügen Sie weitere Berechtigungsprüfungen hinzu, wie benötigt.
        return false;
    }
    public boolean removeMember(String memberName) {
        // Implementieren Sie die Logik hier, um das Mitglied zu entfernen.
        // Zum Beispiel:
        if (this.members.contains(memberName)) {
            this.members.remove(memberName);
            return true; // Entfernen war erfolgreich.
        }
        return false; // Mitglied war nicht in der Liste.
    }

    public GuildRank getMemberRank(String member) {
        return memberRanks.get(member);
    }

    public void setMemberRank(String member, GuildRank rank) {
        if (members.contains(member)) {
            memberRanks.put(member, rank);
        }
    }
    private Map<Material, Integer> treasury = new HashMap<>();

    public void deposit(Material material, int amount) {
        treasury.put(material, treasury.getOrDefault(material, 0) + amount);
    }

    public boolean withdraw(Material material, int amount) {
        if (treasury.getOrDefault(material, 0) >= amount) {
            treasury.put(material, treasury.get(material) - amount);
            return true;
        }
        return false;
    }

    public Map<Material, Integer> getTreasury() {
        return treasury;
    }


}
package h34r7l3s.freakyworld;


import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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

    public void removeMember(String member) {
        members.remove(member);
        memberRanks.remove(member);
    }

    public GuildRank getMemberRank(String member) {
        return memberRanks.get(member);
    }

    public void setMemberRank(String member, GuildRank rank) {
        if (members.contains(member)) {
            memberRanks.put(member, rank);
        }
    }
}
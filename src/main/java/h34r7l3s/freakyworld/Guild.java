package h34r7l3s.freakyworld;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class Guild {
    private String name;
    private ItemStack[] treasureItems = new ItemStack[27]; // Assuming you have 27 slots in your treasure chest
    public ItemStack getTreasureItemAt(int index) {
        if (index >= 0 && index < treasureItems.length) {
            return treasureItems[index];
        }
        return null; // Return null for invalid index
    }
    public void addItemToTreasure(Guild guild, ItemStack itemStack, int index) {
        guild.setTreasureItemAt(index, itemStack);
        itemManager.saveItem(itemStack, guild.getName()); // Annahme: guild ist die Instanz Ihrer Guild-Klasse

    }

    public void saveTreasureItemsToDatabase(Guild guild) {
        for (int i = 0; i < guild.treasureItems.length; i++) {
            ItemStack itemStack = guild.getTreasureItemAt(i);
            if (itemStack != null) {
                itemManager.saveItem(itemStack, guild.getName()); // Annahme: guild ist die Instanz Ihrer Guild-Klasse
            }
        }
    }





    public void setTreasureItemAt(int index, ItemStack itemStack) {
        if (index >= 0 && index < treasureItems.length) {
            treasureItems[index] = itemStack;
        }
    }
    private List<GuildTask> tasks = new ArrayList<>();
    private String description;
    private String leader;
    private Map<String, GuildRank> members;
    private List<String> messages;
    private Location homeLocation;
    private ItemManager itemManager;

    public GuildTask findTaskById(int taskId) {
        for (GuildTask task : tasks) {
            if (task.getId() == taskId) {
                return task;
            }
        }
        return null; // Keine Aufgabe mit der gegebenen ID gefunden
    }


    public enum GuildRank {
        LEADER("Leader"),
        OFFICER("Officer"),
        MEMBER("Member");

        private final String displayName;

        GuildRank(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
    public List<GuildTask> getTasks() {
        return tasks;
    }

    public Guild(String name, String leader, ItemManager itemManager) {
        this.name = name;
        this.description = "";
        this.leader = leader;
        this.members = new HashMap<>();
        this.messages = new ArrayList<>();
        this.homeLocation = null;
        addMember(leader, GuildRank.LEADER);
        this.itemManager = itemManager; // Initialize itemManager
    }

    public void addTask(GuildTask task) {
        tasks.add(task);
    }
    public boolean assignTaskToMember(int taskId, String memberName) {
        for (GuildTask task : tasks) {
            if (task.getId() == taskId && task.getStatus().equals("offen")) {
                task.setAssignedMember(memberName);
                task.setStatus("zugewiesen");
                return true;
            }
        }
        return false;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLeader() {
        return leader;
    }

    public boolean addMember(String playerName, GuildRank rank) {
        if (!members.containsKey(playerName)) {
            members.put(playerName, rank);
            return true;
        }
        return false;
    }

    public boolean removeMember(String playerName) {
        if (members.containsKey(playerName)) {
            members.remove(playerName);
            return true;
        }
        return false;
    }

    public GuildRank getMemberRank(String playerName) {
        return members.getOrDefault(playerName, GuildRank.MEMBER);
    }

    public boolean isMember(String playerName) {
        return members.containsKey(playerName);
    }

    public List<String> getMembers() {
        return new ArrayList<>(members.keySet());
    }

    public void addMessage(String message) {
        messages.add(message);
    }

    public List<String> getMessages() {
        return new ArrayList<>(messages);
    }

    public void clearMessages() {
        messages.clear();
    }

    public void setHomeLocation(Location location) {
        this.homeLocation = location;
    }

    public Location getHomeLocation() {
        return homeLocation;
    }
    public class GuildTask {
        private int id;
        private String description;
        private String assignedMember;
        private String status; // z.B. "offen", "zugewiesen", "erledigt"

        // Konstruktor
        public GuildTask(int id, String description) {
            this.id = id;
            this.description = description;
            this.assignedMember = "";
            this.status = "offen";
        }
        public void completeTask() {
            this.status = "erledigt";
        }



        public void setId(int id) {
            this.id = id;
        }
        // Getter und Setter
        public int getId() {
            return id;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getAssignedMember() {
            return assignedMember;
        }

        public void setAssignedMember(String assignedMember) {
            this.assignedMember = assignedMember;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;

    public boolean withdraw(Material material, int amount) {
        if (treasury.getOrDefault(material, 0) >= amount) {
            treasury.put(material, treasury.get(material) - amount);
            return true;

        }
    }


    public boolean removeTask(int taskId, String playerName) {
        if (leader.equals(playerName)) {
            GuildTask task = findTaskById(taskId);
            if (task != null) {
                tasks.remove(task);
                return true;
            }
        }
        return false;
    }

    public boolean removeMessage(int messageIndex, String playerName) {
        if (leader.equals(playerName) && messageIndex >= 0 && messageIndex < messages.size()) {
            messages.remove(messageIndex);
            return true;
        }
        return false;
    }

    public void updateMemberRank(String playerName, GuildRank newRank) {
        if (members.containsKey(playerName)) {
            members.put(playerName, newRank); // Aktualisiert den Rang in der members Map
        }

    public Map<Material, Integer> getTreasury() {
        return treasury;

    }


}

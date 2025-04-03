package h34r7l3s.freakyworld;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers.ItemSlot;
import com.comphenix.protocol.wrappers.Pair;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class EquipmentVisibilityUtil {

    public static void updateEquipmentVisibilityForAll(Player targetPlayer, ItemStack itemStack, ItemSlot slot) {
        ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();

        // Erstelle eine Liste von Pair-Objekten für Slot und Item
        List<Pair<ItemSlot, ItemStack>> equipmentList = new ArrayList<>();
        equipmentList.add(new Pair<>(slot, itemStack));

        Bukkit.getOnlinePlayers().forEach(player -> {
            if (player != targetPlayer) { // Senden Sie das Paket nicht an den Zielplayer
                PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.ENTITY_EQUIPMENT);
                packet.getIntegers().write(0, targetPlayer.getEntityId());
                packet.getSlotStackPairLists().write(0, equipmentList);

                try {
                    protocolManager.sendServerPacket(player, packet);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }
}

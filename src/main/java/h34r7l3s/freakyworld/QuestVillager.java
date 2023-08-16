package h34r7l3s.freakyworld;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;

public class QuestVillager implements Listener {

    private final JavaPlugin plugin;
    private final NamespacedKey questVillagerKey;

    public QuestVillager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.questVillagerKey = new NamespacedKey(plugin, "QuestVillagerName");
    }

    @EventHandler
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (event.getRightClicked().getType() == EntityType.VILLAGER) {
            Villager villager = (Villager) event.getRightClicked();
            Player player = event.getPlayer();

            if (villager.getCustomName() != null && villager.getCustomName().equals("QuestVillager")) {
                villager.setInvulnerable(true);
                villager.setAI(false);

                List<String> sentences = Arrays.asList(
                        "Hallo Abenteurer!",
                        "Ich habe eine besondere Herausforderung für dich.",
                        "Wenn du diese Quest meisterst, werde ich dir den Weg zu einem besonderen Dorfbewohner zeigen.",
                        "Bist du bereit für die Herausforderung?"
                );

                new BukkitRunnable() {
                    int index = 0;

                    @Override
                    public void run() {
                        if (index < sentences.size()) {
                            player.sendMessage(villager.getCustomName() + ": " + sentences.get(index));
                            index++;
                        } else {
                            // Hier können Sie den Code hinzufügen, um die Quest zu starten oder weitere Anweisungen zu geben.
                            // Zum Beispiel:
                            player.sendMessage(villager.getCustomName() + ": " + "Beginne deine Quest, indem du [Aktion] machst.");
                            cancel();
                        }
                    }
                }.runTaskTimer(plugin, 0L, 60L);

                event.setCancelled(true);
            }
        }
    }

    // Hier können Sie zusätzliche Methoden und Event-Handler hinzufügen, um die Quest-Logik zu implementieren.
    // Zum Beispiel, wenn der Spieler die Quest abschließt, können Sie den "MyVillager" freischalten oder dem Spieler Anweisungen geben, wie er ihn finden kann.
}

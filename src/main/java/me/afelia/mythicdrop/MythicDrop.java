package me.afelia.mythicdrop;

import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Random;

public class MythicDrop extends JavaPlugin implements Listener {
    private FileConfiguration config;
    private Random random = new Random();

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        this.config = this.getConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onMythicMobDeath(MythicMobDeathEvent event) {
        if (event.getKiller() instanceof Player) {
            Player player = (Player) event.getKiller();
            String mobName = event.getMob().getType().getInternalName(); // Getting MythicMob's internal name

            if (config.contains(mobName + ".drops")) {
                ConfigurationSection mobDrops = config.getConfigurationSection(mobName + ".drops");
                mobDrops.getKeys(false).forEach(dropKey -> {
                    double chance = mobDrops.getDouble(dropKey + ".chance");
                    if (random.nextDouble() <= chance) {
                        String command = mobDrops.getString(dropKey + ".command"); // Corrected accessing command from config
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", player.getName()));
                        String message = mobDrops.getString(dropKey + ".message"); // Getting custom message from config
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message)); // Sending custom message with color codes to player
                    }
                });
            }
        }
    }
}

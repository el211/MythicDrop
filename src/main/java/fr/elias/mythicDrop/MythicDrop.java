package fr.elias.mythicDrop;

import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
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
    private LuckPerms luckPerms;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        this.config = this.getConfig();
        Bukkit.getPluginManager().registerEvents(this, this);

        // Initialize LuckPerms API
        this.luckPerms = LuckPermsProvider.get();
    }

    @EventHandler
    public void onMythicMobDeath(MythicMobDeathEvent event) {
        if (event.getKiller() instanceof Player) {
            Player player = (Player) event.getKiller();
            String mobName = event.getMob().getType().getInternalName();

            // Check if the mob has drops configured
            if (config.contains(mobName + ".drops")) {
                ConfigurationSection mobDrops = config.getConfigurationSection(mobName + ".drops");

                // Get the player's primary group from LuckPerms
                String primaryGroup = getPrimaryGroup(player);

                // If the group is not found in the configuration, default to the "default" group
                final ConfigurationSection groupDrops;
                if (mobDrops.contains(primaryGroup)) {
                    groupDrops = mobDrops.getConfigurationSection(primaryGroup);
                } else {
                    groupDrops = mobDrops.getConfigurationSection("default");
                }

                // Process drops based on the group configuration
                if (groupDrops != null) {
                    groupDrops.getKeys(false).forEach(dropKey -> {
                        final double chance = groupDrops.getDouble(dropKey + ".chance");
                        if (random.nextDouble() <= chance) {
                            final String command = groupDrops.getString(dropKey + ".command");
                            if (command != null) {
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", player.getName()));
                            }

                            final String message = groupDrops.getString(dropKey + ".message");
                            if (message != null) {
                                player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
                            }
                        }
                    });
                }
            }
        }
    }

    /**
     * Get the primary group of the player using LuckPerms.
     *
     * @param player The player whose group to fetch.
     * @return The primary group name.
     */
    private String getPrimaryGroup(Player player) {
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user != null) {
            String primaryGroup = user.getPrimaryGroup(); // This returns a String, not Optional<String>
            return primaryGroup != null ? primaryGroup : "default";
        }
        return "default";
    }
}

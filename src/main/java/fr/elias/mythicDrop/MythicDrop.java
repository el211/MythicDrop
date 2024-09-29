package fr.elias.mythicdrop;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import io.lumine.mythic.core.mobs.ActiveMob;
import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.adapters.AbstractPlayer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import org.bukkit.command.TabCompleter;

public class MythicDrop extends JavaPlugin implements Listener {
    private FileConfiguration config;
    private FileConfiguration announcementConfig;
    private FileConfiguration debugConfig;
    private final Random random = new Random();
    private LuckPerms luckPerms;


    @Override
    public void onEnable() {
        // Save default config if it doesn't exist
        this.saveDefaultConfig();
        this.config = this.getConfig();

        // Load the custom announcement and debug config
        loadAnnouncementConfig();
        loadDebugConfig();

        // Register event listener
        Bukkit.getPluginManager().registerEvents(this, this);

        // Register the command and tab completer
        Objects.requireNonNull(this.getCommand("mythicdrop")).setExecutor(this);
        Objects.requireNonNull(this.getCommand("mythicdrop")).setTabCompleter(this);  // Register the TabCompleter here

        // Ensure MythicMobs is loaded before interacting with it
        if (Bukkit.getPluginManager().getPlugin("MythicMobs") != null) {
            logDebug("MythicMobs found, proceeding...");

            // Initialize LuckPerms API if necessary
            try {
                this.luckPerms = LuckPermsProvider.get();
                logDebug("LuckPerms API initialized successfully.");
            } catch (IllegalStateException e) {
                logDebug("LuckPerms API could not be initialized.");
            }

        } else {
            logDebug("MythicMobs is not installed. Disabling MythicDrop...");
            getServer().getPluginManager().disablePlugin(this);
        }
    }


    @Override
    public void onDisable() {
        // Clean up resources if needed
    }


// Reload Command
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, String label, @NotNull String[] args) {
        if (label.equalsIgnoreCase("mythicdrop") && args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            // Reload main configuration file
            this.reloadConfig();
            this.config = this.getConfig();

            // Reload custom configuration files
            loadAnnouncementConfig();
            loadDebugConfig();

            // Notify the sender that the configurations have been reloaded
            sender.sendMessage(ChatColor.GREEN + "MythicDrop configuration reloaded.");
            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, Command command, @NotNull String alias, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("mythicdrop")) {
            // If no arguments or one argument, suggest "reload"
            if (args.length == 1) {
                List<String> subcommands = new ArrayList<>();
                subcommands.add("reload");
                return subcommands.stream()
                        .filter(subcommand -> subcommand.toLowerCase().startsWith(args[0].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }
        return null;
    }
    // Handle mob death and reward the most damage dealer or last hitter based on config
    @EventHandler
    public void onMythicMobDeath(MythicMobDeathEvent event) {
        ActiveMob activeMob = MythicBukkit.inst().getMobManager().getActiveMob(event.getEntity().getUniqueId()).orElse(null);
        if (activeMob == null) return; // Ensure the entity is a MythicMob

        Player rewardPlayer = null;  // Initialize the rewardPlayer to null

        // If most-damage is enabled in the config, get the player who generated the most threat
        if (config.getBoolean("reward-processing.most-damage", false)) {
            if (!activeMob.hasThreatTable()) {
                // If no threat table exists, log a debug message
                logDebug("No Threat Table found for mob: " + activeMob.getType().getInternalName());
            } else {
                AbstractEntity topThreatHolder = activeMob.getThreatTable().getTopThreatHolder();

                // Check if the top threat holder is a player
                if (topThreatHolder != null) { // Check if topThreatHolder is not null
                    if (topThreatHolder.isPlayer()) {
                        AbstractPlayer abstractPlayer = topThreatHolder.asPlayer();
                        rewardPlayer = (Player) abstractPlayer.getBukkitEntity(); // Cast to Bukkit Player
                        logDebug("Most-damage player: " + rewardPlayer.getName());
                    } else {
                        logDebug("No player found with top threat.");
                    }
                } else {
                    logDebug("Top threat holder is null."); // Log if topThreatHolder is null
                }

                // Check if announcements are globally enabled or enabled for this specific mob
                String mobName = activeMob.getType().getInternalName();
                boolean globalAnnounce = announcementConfig.getBoolean("announce-on-death", true);
                boolean specificMobAnnounce = announcementConfig.getBoolean("announce-specific-mob." + mobName, globalAnnounce);

                if (specificMobAnnounce) {
                    announceDamageRanking(activeMob);
                } else {
                    logDebug("Announcements disabled for mob: " + mobName);
                }
            }
        } else {
            // Otherwise, reward the last player who killed the mob
            rewardPlayer = (event.getKiller() instanceof Player) ? (Player) event.getKiller() : null;
            if (rewardPlayer != null) {
                logDebug("Last-hitter player: " + rewardPlayer.getName());
            } else {
                logDebug("No player found as the killer.");
            }
        }

        if (rewardPlayer != null) {
            String mobName = event.getMob().getType().getInternalName();
            logDebug("Processing rewards for mob: " + mobName);

            // Check if the mob has drops configured
            if (config.contains(mobName + ".drops")) {
                ConfigurationSection mobDrops = config.getConfigurationSection(mobName + ".drops");

                // Get the player's primary group from LuckPerms
                String primaryGroup = getPrimaryGroup(rewardPlayer);
                logDebug("Primary group for player: " + rewardPlayer.getName() + " is " + primaryGroup);

                // If the group is not found in the configuration, default to the "default" group
                final ConfigurationSection groupDrops;
                if (Objects.requireNonNull(mobDrops).contains(primaryGroup)) {
                    groupDrops = mobDrops.getConfigurationSection(primaryGroup);
                    logDebug("Group-specific drops found for: " + primaryGroup);
                } else {
                    groupDrops = mobDrops.getConfigurationSection("default");
                    logDebug("Using default drops for player: " + rewardPlayer.getName());
                }

                // Process drops based on the group configuration
                if (groupDrops != null) {
                    Player finalRewardPlayer = rewardPlayer;
                    groupDrops.getKeys(false).forEach(dropKey -> {
                        final double chance = groupDrops.getDouble(dropKey + ".chance");
                        logDebug("Checking drop " + dropKey + " with chance: " + chance);

                        if (random.nextDouble() <= chance) {
                            final String command = groupDrops.getString(dropKey + ".command");
                            if (command != null) {
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", finalRewardPlayer.getName()));
                                logDebug("Executed command: " + command.replace("%player%", finalRewardPlayer.getName()));
                            }

                            final String message = groupDrops.getString(dropKey + ".message");
                            if (message != null) {
                                finalRewardPlayer.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
                                logDebug("Sent message to player: " + finalRewardPlayer.getName() + " - " + message);
                            }
                        } else {
                            logDebug("Drop " + dropKey + " did not pass the chance check.");
                        }
                    });
                } else {
                    logDebug("No valid drop configuration found for the group.");
                }
            } else {
                logDebug("No drop configuration found for mob: " + mobName);
            }
        }
    }



    /**
     * Announce the list of players who inflicted the most damage on the mob.
     */
    private void announceDamageRanking(ActiveMob activeMob) {
        // Use getAllThreatTargets to retrieve all entities in the threat table
        Set<AbstractEntity> damageRanking = activeMob.getThreatTable().getAllThreatTargets();

        // Get the boss name
        String bossName = activeMob.getType().getInternalName(); // Use the internal name of the mob as the boss name

        // Get the header and replace the %BOSSNAME% placeholder
        String header = ChatColor.translateAlternateColorCodes('&', announcementConfig.getString("messages.header", "&aLIST OF PLAYERS WHO HAVE INFLICTED THE MOST DAMAGE ON %BOSSNAME%:"))
                .replace("%BOSSNAME%", bossName);
        Bukkit.broadcastMessage(header);

        if (damageRanking.isEmpty()) {
            String noPlayers = ChatColor.translateAlternateColorCodes('&', announcementConfig.getString("messages.no-players", "&cNo players contributed damage to %BOSSNAME%."))
                    .replace("%BOSSNAME%", bossName);
            Bukkit.broadcastMessage(noPlayers);
            return;
        }

        // Format and broadcast the message
        List<AbstractEntity> sortedRanking = damageRanking.stream()
                .sorted(Comparator.comparingDouble(activeMob.getThreatTable()::getThreat).reversed()) // Sort by threat (damage)
                .collect(Collectors.toList());

        for (int i = 0; i < Math.min(5, sortedRanking.size()); i++) { // Display top 5 players
            AbstractEntity entity = sortedRanking.get(i);
            if (entity.isPlayer()) {
                AbstractPlayer abstractPlayer = entity.asPlayer();
                String playerName = abstractPlayer.getBukkitEntity().getName();
                double damage = activeMob.getThreatTable().getThreat(entity);

                String entry = announcementConfig.getString("messages.entry", "&e%position%. %player% - %damage% DAMAGE")
                        .replace("%position%", String.valueOf(i + 1))
                        .replace("%player%", playerName)
                        .replace("%damage%", String.valueOf((int) damage));
                Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', entry));
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
            String primaryGroup = user.getPrimaryGroup();
            logDebug("Fetched primary group for player: " + player.getName() + " - " + primaryGroup);
            return primaryGroup;
        }
        logDebug("No LuckPerms user found for player: " + player.getName() + ". Using default group.");
        return "default";
    }

    /**
     * Load or reload the custom announcement config (announcement.yml).
     */
    private void loadAnnouncementConfig() {
        File announcementFile = new File(getDataFolder(), "announcement.yml");
        if (!announcementFile.exists()) {
            announcementFile.getParentFile().mkdirs();
            saveResource("announcement.yml", false); // Create the file if it doesn't exist
        }

        announcementConfig = new YamlConfiguration();
        try {
            announcementConfig.load(announcementFile);
        } catch (IOException | InvalidConfigurationException e) {
            getLogger().severe("Could not load announcement.yml: " + e.getMessage());
        }
    }

    /**
     * Load or reload the custom debug config (debug.yml).
     */
    private void loadDebugConfig() {
        File debugFile = new File(getDataFolder(), "debug.yml");
        if (!debugFile.exists()) {
            debugFile.getParentFile().mkdirs();
            saveResource("debug.yml", false); // Create the file if it doesn't exist
        }

        debugConfig = new YamlConfiguration();
        try {
            debugConfig.load(debugFile);
        } catch (IOException | InvalidConfigurationException e) {
            getLogger().severe("Could not load debug.yml: " + e.getMessage());
        }
    }

    /**
     * Log debug messages if debug mode is enabled.
     * @param message The debug message to log.
     */
    private void logDebug(String message) {
        if (debugConfig.getBoolean("activate-debug", false)) {
            getLogger().info(message);
        }
    }
}

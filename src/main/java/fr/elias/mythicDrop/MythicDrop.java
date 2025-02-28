package fr.elias.mythicDrop;

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
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.bukkit.command.TabCompleter;

import static jdk.jfr.internal.management.ManagementSupport.logDebug;

public class MythicDrop extends JavaPlugin implements Listener {
    private FileConfiguration config;
    private FileConfiguration announcementConfig;
    private FileConfiguration debugConfig;
    private final Random random = new Random();
    private LuckPerms luckPerms;
    private FileConfiguration top3Config;
    private FileConfiguration top5Config;


    @Override
    public void onEnable() {
        try {
            // Initialize debug config first to ensure debug logs can be used
            loadDebugConfig();
            logDebug("Starting MythicDrop plugin initialization...");

            // Save default config if it doesn't exist
            this.saveDefaultConfig();
            this.config = this.getConfig();
            logDebug("Main configuration loaded.");

            // Load additional configurations
            loadTop3Config();
            loadTop5Config();
            loadAnnouncementConfig();

            // Register event listener
            Bukkit.getPluginManager().registerEvents(this, this);
            logDebug("Event listeners registered.");

            // Register the command and tab completer
            if (this.getCommand("mythicdrop") != null) {
                this.getCommand("mythicdrop").setExecutor(this);
                this.getCommand("mythicdrop").setTabCompleter(this);  // Register the TabCompleter here
                logDebug("Commands and tab completers registered.");
            } else {
                logDebug("Failed to register commands for 'mythicdrop'.");
            }

            // Ensure MythicMobs is loaded before interacting with it
            if (Bukkit.getPluginManager().getPlugin("MythicMobs") != null) {
                logDebug("MythicMobs found, proceeding...");

                // Initialize LuckPerms API if necessary
                try {
                    this.luckPerms = LuckPermsProvider.get();
                    logDebug("LuckPerms API initialized successfully.");
                } catch (IllegalStateException e) {
                    logDebug("LuckPerms API could not be initialized: " + e.getMessage());
                }

            } else {
                logDebug("MythicMobs is not installed. Disabling MythicDrop...");
                getServer().getPluginManager().disablePlugin(this);
            }

            logDebug("MythicDrop plugin enabled successfully.");
        } catch (Exception e) {
            getLogger().severe("An error occurred while enabling MythicDrop: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }



    @Override
    public void onDisable() {
        // Clean up resources if needed
    }

    private void loadTop3Config() {
        File top3File = new File(getDataFolder(), "top3damage.yml");
        if (!top3File.exists()) {
            top3File.getParentFile().mkdirs();
            saveResource("top3damage.yml", false);
            logDebug("top3damage.yml not found. Default file created.");
        }

        top3Config = new YamlConfiguration();
        try {
            top3Config.load(top3File);
            logDebug("top3damage.yml loaded successfully.");
            logDebug("Contents of top3damage.yml: " + top3Config.saveToString());
        } catch (IOException | InvalidConfigurationException e) {
            getLogger().severe("Could not load top3damage.yml: " + e.getMessage());
            logDebug("Error loading top3damage.yml: " + e.getMessage());
        }
    }



    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, String label, @NotNull String[] args) {
        if (label.equalsIgnoreCase("mythicdrop") && args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            // Reload main configuration file
            this.reloadConfig();
            this.config = this.getConfig();

            // Reload custom configuration files
            loadAnnouncementConfig();
            loadDebugConfig();

            // Reload top3damage.yml and top5damage.yml
            loadTop3Config();
            loadTop5Config();

            // Notify the sender that the configurations have been reloaded
            sender.sendMessage(ChatColor.GREEN + "MythicDrop configuration reloaded, including top3damage.yml and top5damage.yml.");
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
    @EventHandler
    public void onMythicMobDeath(MythicMobDeathEvent event) {
        logDebug("MythicMobDeathEvent triggered.");

        // Retrieve the ActiveMob instance
        ActiveMob activeMob = MythicBukkit.inst().getMobManager().getActiveMob(event.getEntity().getUniqueId()).orElse(null);
        if (activeMob == null) {
            logDebug("Mob death event triggered, but the entity is not a MythicMob.");
            return;
        }

        // Ensure ActiveMob has a valid type and internal name
        String mobName = activeMob.getType().getInternalName();
        if (mobName == null) {
            logDebug("ActiveMob has no type or internal name. Aborting.");
            return;
        }

        UUID mobId = activeMob.getUniqueId();
        logDebug("Mob death event triggered for mob: " + mobName);

        // Prevent duplicate processing
        if (processedMobEvents.contains(mobId)) {
            logDebug("Rewards for mob " + mobName + " have already been processed. Skipping.");
            return;
        }
        processedMobEvents.add(mobId);

        // Inspect the threat table
        if (activeMob.hasThreatTable()) {
            logDebug("Threat table size for mob " + mobName + ": " + activeMob.getThreatTable().size());

            for (AbstractEntity target : activeMob.getThreatTable().getAllThreatTargets()) {
                if (target == null) { // Prevent null entity crash
                    logDebug("Skipping null entity in threat table.");
                    continue;
                }

                double threat = activeMob.getThreatTable().getThreat(target);
                logDebug(" - Entity: " + target.getName() + ", Threat: " + threat);
            }
        } else {
            logDebug("No Threat Table found for mob: " + mobName);
        }

        // Prioritize config.yml for mob rewards
        if (config.contains(mobName + ".drops")) {
            logDebug("Processing rewards for mob: " + mobName + " using config.yml.");
            handleRewardProcessing(activeMob, event.getKiller() instanceof Player ? (Player) event.getKiller() : null, event);
            return;
        }

        // Check for Top 3 and Top 5 reward configuration
        List<String> rewardTop3Mobs = top3Config.getStringList("rewardtop3");
        List<String> rewardTop5Mobs = top5Config.getStringList("rewardtop5");

        boolean isTop3RewardMob = rewardTop3Mobs.contains(mobName);
        boolean isTop5RewardMob = rewardTop5Mobs.contains(mobName);

        if (isTop3RewardMob) {
            logDebug("Mob " + mobName + " is configured for Top 3 rewards. Processing Top 3 rewards...");
            handleTop3Rewards(activeMob);
            return;
        }

        if (isTop5RewardMob) {
            logDebug("Mob " + mobName + " is configured for Top 5 rewards. Processing Top 5 rewards...");
            handleTop5Rewards(activeMob);
            return;
        }

        // Fallback to standard reward logic
        logDebug("Mob " + mobName + " is not configured for Top 3 or Top 5 rewards. Falling back to standard reward processing.");

        // Determine reward logic based on config
        boolean mostDamage = config.getBoolean("reward-processing.most-damage", false);
        boolean rewardTop3 = config.getBoolean("reward-processing.reward-top3", false);

        if (mostDamage && rewardTop3) {
            logDebug("Cannot have both most-damage and reward-top3 enabled simultaneously.");
            return;
        }

        // Process rewards based on configuration
        if (mostDamage) {
            logDebug("Processing most-damage reward...");
            handleMostDamageReward(activeMob);
        } else if (rewardTop3) {
            logDebug("Processing Top 3 rewards...");
            handleTop3Rewards(activeMob);
        } else {
            logDebug("Processing last-hit reward...");
            handleLastHitReward(activeMob, event);
        }

        // Unified announcement logic
        boolean globalAnnounce = announcementConfig.getBoolean("announce-on-death", true);
        boolean specificMobAnnounce = announcementConfig.getBoolean("announce-specific-mob." + mobName, globalAnnounce);

        if (specificMobAnnounce) {
            announceDamageRanking(activeMob);
        } else {
            logDebug("Announcements disabled for mob: " + mobName);

            // Additional debug check if mob is in Top 3 or Top 5
            logDebug("Checking if mob is configured in top3damage.yml: " + top3Config.getStringList("rewardtop3").contains(mobName));
            logDebug("Checking if mob is configured in top5damage.yml: " + top5Config.getStringList("rewardtop5").contains(mobName));
        }

        // Ensure cleanup at the end
        processedMobEvents.remove(mobId);
    }





    private void handleMostDamageReward(ActiveMob activeMob) {
        if (!activeMob.hasThreatTable()) {
            logDebug("No Threat Table found for mob: " + activeMob.getType().getInternalName());
            return;
        }
        logDebug("Threat table size for mob " + activeMob.getType().getInternalName() + ": " + activeMob.getThreatTable().getAllThreatTargets().size());

        AbstractEntity topThreatHolder = activeMob.getThreatTable().getTopThreatHolder();
        if (topThreatHolder == null || !topThreatHolder.isPlayer()) {
            logDebug("No player found with top threat for mob: " + activeMob.getType().getInternalName());
            return;
        }

        AbstractPlayer abstractPlayer = topThreatHolder.asPlayer();
        Player rewardPlayer = (Player) abstractPlayer.getBukkitEntity();
        logDebug("Most-damage player: " + rewardPlayer.getName());

        // Process rewards for the player
        processRewardsForPlayer(activeMob, rewardPlayer, 1); // Rank 1
    }


    private void handleRewardProcessing(ActiveMob activeMob, Player lastHitter, MythicMobDeathEvent event) {
        String mobName = activeMob.getType().getInternalName();
        logDebug("Processing rewards for mob: " + mobName);

        boolean mostDamage = isMostDamageRewardEnabled();

        if (mostDamage && activeMob.hasThreatTable()) {
            logDebug("Most-damage reward is enabled. Processing most-damage logic...");
            handleMostDamageReward(activeMob); // Process most-damage rewards
        } else {
            logDebug("Last-hit reward is enabled. Processing last-hit logic...");
            handleLastHitReward(activeMob, event); // Process last-hit rewards
        }

        // Announce the rewards if announcements are enabled
        boolean globalAnnounce = announcementConfig.getBoolean("announce-on-death", true);
        if (globalAnnounce) {
            announceDamageRanking(activeMob);
        }
    }


    private final Set<UUID> processedMobEvents = new HashSet<>();
    private final Set<UUID> processedTop3Events = new HashSet<>();

    private void handleTop3Rewards(ActiveMob activeMob) {
        long startTime = System.currentTimeMillis(); // Start timing for performance monitoring
        String mobName = activeMob.getType().getInternalName();

        // Prevent duplicate reward processing for the same mob
        UUID mobId = activeMob.getUniqueId();
        if (processedTop3Events.contains(mobId)) {
            logDebug("Top 3 rewards for mob " + mobName + " have already been processed. Skipping.");
            return;
        }
        processedTop3Events.add(mobId); // Use a separate set for Top 3



        // Log mob name and start of the reward handling process
        logDebug("Executing handleTop3Rewards for mob: " + mobName);

        // Check if the mob is configured in the rewardtop3 section
        List<String> rewardTop3Mobs = top3Config.getStringList("rewardtop3");
        logDebug("Configured top-3 mobs: " + rewardTop3Mobs);

        if (rewardTop3Mobs == null || rewardTop3Mobs.isEmpty() || !rewardTop3Mobs.contains(mobName)) {
            logDebug("Mob " + mobName + " is not configured for top-3 rewards. Skipping.");
            return; // Skip top-3 reward processing
        }

        if (!activeMob.hasThreatTable()) {
            logDebug("No Threat Table found for mob: " + mobName);
            return;
        }


        // Log threat table size
        logDebug("Threat table size for mob " + mobName + ": " + activeMob.getThreatTable().getAllThreatTargets().size());

        Set<AbstractEntity> damageRanking = activeMob.getThreatTable().getAllThreatTargets();
        if (damageRanking == null || damageRanking.isEmpty()) {
            logDebug("No players contributed damage to the mob: " + mobName);
            return;
        }

        // Sort by threat in descending order
        List<AbstractEntity> sortedRanking = damageRanking.stream()
                .sorted(Comparator.comparingDouble(activeMob.getThreatTable()::getThreat).reversed())
                .collect(Collectors.toList());
        logDebug("Sorted ranking size: " + sortedRanking.size());

        // Check if the mob has specific top3damage rewards configured
        if (!top3Config.contains(mobName)) {
            logDebug("No specific top-3 rewards configured for mob: " + mobName);
            return;
        }

        // Determine if standard rewards should also be applied
        boolean useStandardRewards = top3Config.getBoolean(mobName + ".use-standard-rewards", false);
        logDebug("Use standard rewards for mob " + mobName + ": " + useStandardRewards);

        // Reward the top 3 players
        for (int i = 0; i < Math.min(3, sortedRanking.size()); i++) {
            AbstractEntity entity = sortedRanking.get(i);
            if (entity.isPlayer()) {
                Player player = (Player) entity.asPlayer().getBukkitEntity();
                String rank;
                switch (i) {
                    case 0:
                        rank = "first-place";
                        break;
                    case 1:
                        rank = "second-place";
                        break;
                    case 2:
                        rank = "third-place";
                        break;
                    default:
                        rank = "unknown";
                        break;
                }

                logDebug("Rewarding player " + player.getName() + " for " + rank);

                // Apply top-3 specific rewards
                processTop3RewardsForPlayer(mobName, rank, player);

                // Optionally apply standard rewards
                if (useStandardRewards) {
                    logDebug("Applying standard rewards for mob: " + mobName + " to " + player.getName());
                    processRewardsForPlayer(activeMob, player, i + 1); // Rank starts at 1
                }
            } else {
                logDebug("Entity " + entity.getName() + " is not a player, skipping.");
            }
        }

        // Reward everyone else who contributed
        double minDamage = top3Config.getDouble(mobName + ".everyone-else-who-contributed.min-damage", 0.0); // Default to 0.0
        logDebug("Minimum damage for 'everyone else' rewards: " + minDamage);

        for (int i = 3; i < sortedRanking.size(); i++) {
            AbstractEntity entity = sortedRanking.get(i);
            if (entity.isPlayer()) {
                Player player = (Player) entity.asPlayer().getBukkitEntity();
                double playerDamage = activeMob.getThreatTable().getThreat(entity);

                logDebug("Player " + player.getName() + " damage: " + playerDamage);

                if (playerDamage >= minDamage) {
                    logDebug("Rewarding player " + player.getName() + " for contributing with damage: " + playerDamage);
                    processEveryoneElseRewards(mobName, player, playerDamage);
                } else {
                    logDebug("Player " + player.getName() + " did not meet the min-damage threshold (" + minDamage + "). Skipping.");
                }
            } else {
                logDebug("Entity " + entity.getName() + " is not a player, skipping.");
            }
        }

        // Announce damage ranking after all rewards have been processed
        logDebug("Announcing damage ranking for mob: " + mobName);
        announceDamageRanking(activeMob);

        long duration = System.currentTimeMillis() - startTime; // Measure duration
        logDebug("handleTop3Rewards for mob " + mobName + " executed in " + duration + " ms.");
        processedTop3Events.remove(mobId); // Add this at the end of the method

    }


    private void loadTop5Config() {
        logDebug("Starting to load top5damage.yml configuration file.");

        File top5File = new File(getDataFolder(), "top5damage.yml");

        // Check if the file exists, and create it if necessary
        if (!top5File.exists()) {
            logDebug("top5damage.yml does not exist. Attempting to create a new file.");
            top5File.getParentFile().mkdirs();
            saveResource("top5damage.yml", false); // Save default file if it doesn't exist
            logDebug("top5damage.yml file created successfully.");
        }

        top5Config = new YamlConfiguration();
        try {
            // Load the file into the configuration object
            top5Config.load(top5File);
            logDebug("top5damage.yml loaded successfully.");
        } catch (IOException e) {
            getLogger().severe("I/O error occurred while loading top5damage.yml: " + e.getMessage());
            logDebug("Stack trace for I/O exception: " + e);
        } catch (InvalidConfigurationException e) {
            getLogger().severe("Invalid configuration in top5damage.yml: " + e.getMessage());
            logDebug("Stack trace for invalid configuration: " + e);
        }

        // Additional debugging to confirm if the file content is loaded properly
        if (top5Config.getKeys(false).isEmpty()) {
            logDebug("top5damage.yml loaded but appears to be empty or improperly formatted.");
        } else {
            logDebug("top5damage.yml loaded successfully with keys: " + top5Config.getKeys(false));
        }
    }


    private final Set<UUID> processedTop5Events = new HashSet<>();

    private void handleTop5Rewards(ActiveMob activeMob) {
        String mobName = activeMob.getType().getInternalName();

        // Prevent duplicate processing for the same mob
        UUID mobId = activeMob.getUniqueId();
        if (processedTop5Events.contains(mobId)) {
            logDebug("Top 5 rewards for mob " + mobName + " have already been processed. Skipping.");
            return;
        }
        processedTop5Events.add(mobId);

        // Check if the mob is configured in the rewardTop5 section
        List<String> rewardTop5Mobs = top5Config.getStringList("rewardtop5");
        if (!rewardTop5Mobs.contains(mobName)) {
            logDebug("Mob " + mobName + " is not configured for top-5 rewards. Skipping.");
            return;
        }

        // Check if reward configuration exists for the mob
        if (!top5Config.contains(mobName)) {
            logDebug("No reward configuration found for Top 5 for mob: " + mobName);
            return;
        }

        if (!top5Config.contains(mobName + ".first-place")) {
            logDebug("Missing Top 5 reward configuration for first-place for mob: " + mobName);
            return;
        }

        if (!activeMob.hasThreatTable()) {
            logDebug("No Threat Table found for mob: " + mobName);
            return;
        }

        logDebug("Processing top-5 rewards for mob: " + mobName);
        Set<AbstractEntity> damageRanking = activeMob.getThreatTable().getAllThreatTargets();
        if (damageRanking.isEmpty()) {
            logDebug("No players contributed damage to the mob: " + mobName);
            return;
        }

        // Sort by threat in descending order
        List<AbstractEntity> sortedRanking = damageRanking.stream()
                .sorted(Comparator.comparingDouble(activeMob.getThreatTable()::getThreat).reversed())
                .collect(Collectors.toList());
        logDebug("Sorted damage ranking for mob " + mobName + ". Total contributors: " + sortedRanking.size());

        // Check if the mob allows standard rewards
        boolean useStandardRewards = top5Config.getBoolean(mobName + ".use-standard-rewards", false);
        logDebug("Use standard rewards for mob " + mobName + ": " + useStandardRewards);

        // Reward the top 5 players
        for (int i = 0; i < Math.min(5, sortedRanking.size()); i++) {
            AbstractEntity entity = sortedRanking.get(i);
            if (entity.isPlayer()) {
                Player player = (Player) entity.asPlayer().getBukkitEntity();
                String rank;

                switch (i) {
                    case 0:
                        rank = "first-place";
                        break;
                    case 1:
                        rank = "second-place";
                        break;
                    case 2:
                        rank = "third-place";
                        break;
                    case 3:
                        rank = "fourth-place";
                        break;
                    case 4:
                        rank = "fifth-place";
                        break;
                    default:
                        rank = "unknown";
                }

                logDebug("Rewarding player " + player.getName() + " for " + rank);

                // Process Top 5 specific rewards
                processTop5RewardsForPlayer(mobName, rank, player);

                // Optionally apply standard rewards
                if (useStandardRewards) {
                    logDebug("Applying standard rewards for mob: " + mobName + " to " + player.getName());
                    processRewardsForPlayer(activeMob, player, i + 1); // Rank starts at 1
                }
            } else {
                logDebug("Entity " + entity.getName() + " is not a player. Skipping.");
            }
        }

        // Reward everyone else who contributed
        if (top5Config.contains(mobName + ".everyone-else-who-contributed")) {
            double minDamage = top5Config.getDouble(mobName + ".everyone-else-who-contributed.min-damage", 0.0);
            logDebug("Minimum damage for 'everyone else' rewards: " + minDamage);

            for (int i = 5; i < sortedRanking.size(); i++) {
                AbstractEntity entity = sortedRanking.get(i);
                if (entity.isPlayer()) {
                    Player player = (Player) entity.asPlayer().getBukkitEntity();
                    double playerDamage = activeMob.getThreatTable().getThreat(entity);

                    logDebug("Player " + player.getName() + " damage: " + playerDamage);

                    if (playerDamage >= minDamage) {
                        logDebug("Rewarding player " + player.getName() + " for contributing with damage: " + playerDamage);
                        processEveryoneElseRewards(mobName, player, playerDamage);
                    } else {
                        logDebug("Player " + player.getName() + " did not meet the min-damage threshold (" + minDamage + "). Skipping.");
                    }
                } else {
                    logDebug("Entity " + entity.getName() + " is not a player. Skipping.");
                }
            }
        } else {
            logDebug("No 'everyone else' reward configuration found for mob: " + mobName);
        }

        // Announce damage ranking
        logDebug("Announcing damage ranking for mob: " + mobName);
        announceDamageRanking(activeMob);

        // Remove the processed mob event to avoid memory leaks after processing
        processedTop5Events.remove(mobId);

    }



    private void processTop5RewardsForPlayer(String mobName, String rank, Player player) {
        long startTime = System.currentTimeMillis(); // Start timing for performance monitoring

        logDebug("Processing top-5 rewards for mob: " + mobName + ", rank: " + rank + ", player: " + player.getName());

        // Check if the rank is configured
        if (!top5Config.contains(mobName + "." + rank)) {
            logDebug("No reward configuration for rank: " + rank + " for mob: " + mobName);
            return;
        }

        // Fetch the rank-specific section
        ConfigurationSection rankSection = top5Config.getConfigurationSection(mobName + "." + rank);
        if (rankSection == null) {
            logDebug("No reward section found for " + mobName + " at rank: " + rank);
            return;
        }

        // Determine the player's primary group
        String primaryGroup = getPrimaryGroup(player);
        logDebug("Player " + player.getName() + " primary group: " + primaryGroup);

        // Fetch group-specific or default drops
        ConfigurationSection groupDrops = rankSection.contains(primaryGroup)
                ? rankSection.getConfigurationSection(primaryGroup)
                : rankSection.getConfigurationSection("default");
        if (groupDrops == null) {
            logDebug("No valid drop configuration found for player group: " + primaryGroup + " or default.");
            return;
        }

        // Log the keys in the group drops for better visibility
        logDebug("Reward keys available for player group " + primaryGroup + ": " + groupDrops.getKeys(false));

        // Get the guaranteed-rewards value, defaulting to 1 if not set
        int guaranteedRewards = rankSection.contains("guaranteed-rewards") ? rankSection.getInt("guaranteed-rewards") : 1;
        logDebug("DEBUG CHECK: guaranteed-rewards for " + mobName + " at rank " + rank + " = " + guaranteedRewards);
        logDebug("Guaranteed rewards for top-5 rank " + rank + ": " + guaranteedRewards);

        // Collect potential rewards
        List<String> rewardKeys = new ArrayList<>(groupDrops.getKeys(false));
        List<String> selectedRewards = new ArrayList<>();

        // Shuffle the rewards list to randomize the selection
        Collections.shuffle(rewardKeys);

        // Loop through the shuffled rewards and select up to guaranteedRewards
        for (String dropKey : rewardKeys) {
            if (selectedRewards.size() >= guaranteedRewards) break;

            double chance = groupDrops.getDouble(dropKey + ".chance", 0.0);
            String command = groupDrops.getString(dropKey + ".command");
            String message = groupDrops.getString(dropKey + ".message");

            logDebug("Processing reward " + dropKey + " | Chance: " + chance);

            if (command == null || command.isEmpty()) {
                logDebug("Invalid or missing command for reward: " + dropKey + ". Skipping.");
                continue;
            }

            // Roll the chance and apply the reward if successful
            double roll = ThreadLocalRandom.current().nextDouble();
            logDebug("Reward " + dropKey + ": Roll=" + roll + " | Threshold=" + chance);

            if (roll <= chance) {
                selectedRewards.add(dropKey);

                // Execute the command
                boolean commandSuccess = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", player.getName()));
                logDebug("Executed reward command for " + dropKey + ": " + command.replace("%player%", player.getName()) + ", Success: " + commandSuccess);

                // Send the reward message, if specified
                if (message != null && !message.isEmpty()) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
                    logDebug("Sent reward message to player: " + message);
                } else {
                    logDebug("No message specified for reward: " + dropKey + ".");
                }
            } else {
                logDebug("Reward " + dropKey + " did not trigger due to chance roll.");
            }
        }

        long duration = System.currentTimeMillis() - startTime; // Calculate duration
        logDebug("Finished processing top-5 rewards for mob: " + mobName + ", rank: " + rank + ", player: " + player.getName() + " in " + duration + " ms.");
    }


    private void processEveryoneElseRewards(String mobName, Player player, double playerDamage) {
        // Ensure the player and their name are valid
        if (player == null || player.getName() == null) {
            logDebug("Player or player name is null. Skipping reward processing.");
            return;
        }

        logDebug("Processing rewards for everyone else who contributed for mob: " + mobName + ", player: " + player.getName());

        // Determine the appropriate configuration section (top5Config or top3Config)
        ConfigurationSection everyoneElseConfig = top5Config.getConfigurationSection(mobName + ".everyone-else-who-contributed");
        if (everyoneElseConfig == null) {
            everyoneElseConfig = top3Config.getConfigurationSection(mobName + ".everyone-else-who-contributed");
        }

        if (everyoneElseConfig == null) {
            logDebug("No valid 'everyone-else-who-contributed' section found for mob: " + mobName);
            return;
        }

        // Check for the minimum damage threshold
        double minDamage = everyoneElseConfig.getDouble("min-damage", 0.0); // Default to 0.0
        if (playerDamage < minDamage) {
            logDebug("Player " + player.getName() + " did not meet the min-damage threshold (" + minDamage + "). Skipping rewards.");
            return;
        }

        // Determine the player's primary group using LuckPerms
        String primaryGroup = getPrimaryGroup(player);
        logDebug("Player " + player.getName() + " primary group: " + primaryGroup);

        // Fetch group-specific drops or fallback to the default section
        ConfigurationSection groupDrops = everyoneElseConfig.getConfigurationSection(primaryGroup);
        ConfigurationSection effectiveGroupDrops = (groupDrops != null) ? groupDrops : everyoneElseConfig.getConfigurationSection("default");

        // Check if there are valid drops configured
        if (effectiveGroupDrops == null) {
            logDebug("No valid drop configuration found for player group: " + primaryGroup + " or default.");
            return;
        }

        // Process each drop in the configuration
        effectiveGroupDrops.getKeys(false).forEach(dropKey -> {
            double chance = effectiveGroupDrops.getDouble(dropKey + ".chance", 0.0); // Default to 0.0 chance if not configured
            double roll = ThreadLocalRandom.current().nextDouble(); // Thread-safe random number generation
            logDebug("Processing reward " + dropKey + " for player: " + player.getName() + " | Roll: " + roll + " | Chance: " + chance);

            // Check if the reward should trigger
            if (roll <= chance) {
                String command = effectiveGroupDrops.getString(dropKey + ".command");
                if (command == null || command.trim().isEmpty()) {
                    logDebug("Invalid or missing command for reward " + dropKey + " in configuration. Skipping.");
                    return;
                }

                // Execute the reward command
                boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", player.getName()));
                logDebug("Executed reward command: " + command.replace("%player%", player.getName()) + " | Success: " + success);

                // Send a message to the player if configured
                String message = effectiveGroupDrops.getString(dropKey + ".message");
                if (message != null && !message.isEmpty()) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
                    logDebug("Sent message to player: " + message);
                }
            } else {
                logDebug("Reward " + dropKey + " did not trigger. Chance threshold not met.");
            }
        });
    }

    private void processTop3RewardsForPlayer(String mobName, String rank, Player player) {
        long startTime = System.currentTimeMillis(); // Start timing for performance monitoring

        logDebug("Starting processing top-3 rewards for mob: " + mobName + ", rank: " + rank + ", player: " + player.getName());

        // Check if the rank section exists in the configuration
        if (!top3Config.contains(mobName + "." + rank)) {
            logDebug("No rewards configured for " + rank + " of mob: " + mobName);
            return;
        }

        // Get the rank-specific section
        ConfigurationSection rankSection = top3Config.getConfigurationSection(mobName + "." + rank);
        if (rankSection == null) {
            logDebug("No reward section found for mob: " + mobName + " at rank: " + rank);
            return;
        }
        logDebug("Rank-specific rewards section found for mob: " + mobName + ", rank: " + rank);

        // Determine the player's primary group
        String primaryGroup = getPrimaryGroup(player);
        logDebug("Player " + player.getName() + " belongs to primary group: " + primaryGroup);

        // Get the group-specific or default rewards section
        ConfigurationSection groupDrops = rankSection.contains(primaryGroup)
                ? rankSection.getConfigurationSection(primaryGroup)
                : rankSection.getConfigurationSection("default");
        if (groupDrops == null) {
            logDebug("No valid drop configuration found for player group: " + primaryGroup + " or default.");
            return;
        }

        logDebug("Reward section found for group: " + primaryGroup + " (or default) for player: " + player.getName());
        logDebug("Available reward keys for group: " + groupDrops.getKeys(false));

        // Get the guaranteed-rewards value, defaulting to 1 if not set
        int guaranteedRewards = top3Config.getInt(mobName + ".guaranteed-rewards", 1);
        logDebug("Guaranteed rewards for top-3 rank " + rank + ": " + guaranteedRewards);

        // Collect potential rewards
        List<String> rewardKeys = new ArrayList<>(groupDrops.getKeys(false));
        List<String> selectedRewards = new ArrayList<>();

        // Shuffle the rewards list to randomize the selection
        Collections.shuffle(rewardKeys);

        // Loop through the shuffled rewards and select up to guaranteedRewards
        for (String dropKey : rewardKeys) {
            if (selectedRewards.size() >= guaranteedRewards) break;

            double chance = groupDrops.getDouble(dropKey + ".chance", 0.0);
            String command = groupDrops.getString(dropKey + ".command");
            String message = groupDrops.getString(dropKey + ".message");

            logDebug("Processing reward " + dropKey + " | Chance: " + chance);

            if (command == null || command.isEmpty()) {
                logDebug("Invalid or missing command for reward: " + dropKey + ". Skipping.");
                continue;
            }

            // Roll the chance and apply the reward if successful
            double roll = ThreadLocalRandom.current().nextDouble();
            logDebug("Reward " + dropKey + ": Roll=" + roll + " | Threshold=" + chance);

            if (roll <= chance) {
                selectedRewards.add(dropKey);

                // Execute the command
                boolean commandSuccess = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", player.getName()));
                logDebug("Executed reward command for " + dropKey + ": " + command.replace("%player%", player.getName()) + ", Success: " + commandSuccess);

                // Send the reward message, if specified
                if (message != null && !message.isEmpty()) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
                    logDebug("Sent reward message to player: " + message);
                } else {
                    logDebug("No message specified for reward: " + dropKey + ".");
                }
            } else {
                logDebug("Reward " + dropKey + " did not trigger due to chance roll.");
            }
        }

        long duration = System.currentTimeMillis() - startTime; // Calculate duration
        logDebug("Completed processing top-3 rewards for mob: " + mobName + ", rank: " + rank + ", player: " + player.getName() + " in " + duration + " ms.");
    }



    private void handleLastHitReward(ActiveMob activeMob, MythicMobDeathEvent event) {
        // Identify the player who dealt the last hit
        Player rewardPlayer = (event.getKiller() instanceof Player) ? (Player) event.getKiller() : null;

        if (rewardPlayer != null) {
            logDebug("Last-hitter player: " + rewardPlayer.getName());
            processRewardsForPlayer(activeMob, rewardPlayer, 1); // Rank 1 for last-hit rewards
        } else {
            logDebug("No player found as the killer.");
            return; // Exit if no killer is found
        }

        // Check if ThreatTable exists
        if (!activeMob.hasThreatTable() || activeMob.getThreatTable() == null) {
            logDebug("No ThreatTable found for mob: " + activeMob.getType().getInternalName());
            return; // Skip further processing if no ThreatTable is found
        }

        // Consolidated announcement logic
        String mobName = activeMob.getType().getInternalName();
        boolean globalAnnounce = announcementConfig.getBoolean("announce-on-death", true);
        boolean specificMobAnnounce = announcementConfig.getBoolean("announce-specific-mob." + mobName, globalAnnounce);

        if (specificMobAnnounce) {
            logDebug("Announcements enabled for mob: " + mobName);
            announceDamageRanking(activeMob); // Announce the damage ranking
        } else {
            logDebug("Announcements disabled for mob: " + mobName);
        }
    }




    private void processRewardsForPlayer(ActiveMob activeMob, Player player, int position) {
        long startTime = System.currentTimeMillis();

        String mobName = activeMob.getType().getInternalName();
        logDebug("Starting reward processing for mob: " + mobName + ", player: " + player.getName() + ", position: " + position);

        // Check if the mob has drop configuration in config.yml
        if (!config.contains(mobName + ".drops")) {
            logDebug("No drop configuration found for mob: " + mobName + " in config.yml.");
            return;
        }

        ConfigurationSection mobDrops = config.getConfigurationSection(mobName + ".drops");
        if (mobDrops == null) {
            logDebug("Failed to retrieve drops section for mob: " + mobName);
            return;
        }

        // Get the player's primary group for group-specific drops
        String primaryGroup = getPrimaryGroup(player);
        logDebug("Player " + player.getName() + " belongs to primary group: " + primaryGroup);

        ConfigurationSection groupDrops = mobDrops.contains(primaryGroup)
                ? mobDrops.getConfigurationSection(primaryGroup)
                : mobDrops.getConfigurationSection("default");

        if (groupDrops == null) {
            logDebug("No valid drop configuration found for player group: " + primaryGroup + " or default.");
            return;
        }

        logDebug("Processing rewards for player group: " + primaryGroup + " (or default). Available reward keys: " + groupDrops.getKeys(false));

        // Get the guaranteed-rewards value, defaulting to 1 if not set
        int guaranteedRewards = mobDrops.getInt("guaranteed-rewards", 1);

        // Collect all potential reward keys
        List<String> rewardKeys = new ArrayList<>(groupDrops.getKeys(false));

        // Ensure we don't exceed the number of available reward keys
        if (rewardKeys.size() < guaranteedRewards) {
            logDebug("Not enough reward keys available for guaranteed-rewards: " + guaranteedRewards);
            guaranteedRewards = rewardKeys.size();
        }

        // Randomly shuffle the list of reward keys
        Collections.shuffle(rewardKeys);

        // Guarantee rewards without chance rolls
        logDebug("Guaranteeing " + guaranteedRewards + " rewards for player: " + player.getName());
        for (int i = 0; i < guaranteedRewards; i++) {
            String dropKey = rewardKeys.get(i);
            String command = groupDrops.getString(dropKey + ".command");
            String message = groupDrops.getString(dropKey + ".message");

            if (command != null && !command.isEmpty()) {
                // Execute the guaranteed reward
                boolean commandSuccess = Bukkit.dispatchCommand(
                        Bukkit.getConsoleSender(),
                        command.replace("%player%", player.getName())
                );
                logDebug("Executed guaranteed reward command for " + dropKey + ": " + command + ", Success: " + commandSuccess);

                // Send reward message if specified
                if (message != null && !message.isEmpty()) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
                    logDebug("Sent reward message to player: " + message);
                }
            } else {
                logDebug("Invalid or missing command for guaranteed reward: " + dropKey + ". Skipping.");
            }
        }

        // Process remaining rewards based on chance
        logDebug("Processing chance-based rewards for player: " + player.getName());
        for (String dropKey : rewardKeys.subList(guaranteedRewards, rewardKeys.size())) {
            double chance = groupDrops.getDouble(dropKey + ".chance", 0.0); // Default chance is 0
            String command = groupDrops.getString(dropKey + ".command");
            String message = groupDrops.getString(dropKey + ".message");

            logDebug("Processing chance-based reward " + dropKey + " | Chance: " + chance);

            if (command == null || command.trim().isEmpty()) {
                logDebug("Invalid or missing command for reward: " + dropKey + ". Skipping.");
                continue;
            }

            // Roll the chance for this reward
            double roll = ThreadLocalRandom.current().nextDouble();
            logDebug("Reward " + dropKey + ": Roll=" + roll + " | Threshold=" + chance);

            if (roll <= chance) {
                // Execute the reward command
                boolean commandSuccess = Bukkit.dispatchCommand(
                        Bukkit.getConsoleSender(),
                        command.replace("%player%", player.getName())
                );
                logDebug("Executed chance-based reward command for " + dropKey + ": " + command.replace("%player%", player.getName()) + ", Success: " + commandSuccess);

                // Send reward message to the player if configured
                if (message != null && !message.isEmpty()) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
                    logDebug("Sent reward message to player: " + message);
                }
            } else {
                logDebug("Reward " + dropKey + " did not trigger due to chance roll.");
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        logDebug("Finished reward processing for mob: " + mobName + ", player: " + player.getName() + ", position: " + position + " in " + duration + " ms.");
    }


    /**
     * Announce the list of players who inflicted the most damage on the mob.
     */
    private void announceDamageRanking(ActiveMob activeMob) {
        if (announcementConfig == null) {
            logDebug("announcementConfig is null; announcements cannot proceed.");
            return;
        }
        String mobName = activeMob.getType().getInternalName();
        logDebug("Starting damage ranking announcement for mob: " + mobName);

        // Check global and specific mob announcement settings
        boolean globalAnnounce = announcementConfig.getBoolean("announce-on-death", true);
        ConfigurationSection mobSpecificConfig = announcementConfig.getConfigurationSection("announce-specific-mob." + mobName);
        boolean specificMobAnnounce = mobSpecificConfig != null && mobSpecificConfig.getBoolean("announce", globalAnnounce);

        logDebug("Global announce-on-death enabled: " + globalAnnounce);
        logDebug("Specific mob announcement enabled for " + mobName + ": " + specificMobAnnounce);

        // Prioritize specific mob settings; skip announcement if disabled
        if (!specificMobAnnounce) {
            logDebug("Announcements are disabled for mob: " + mobName);
            return; // Skip announcements for this mob
        }

        // Check if threat table exists and has players
        if (!activeMob.hasThreatTable() || activeMob.getThreatTable().getAllThreatTargets().isEmpty()) {
            String noPlayersMessage = ChatColor.translateAlternateColorCodes('&',
                    (mobSpecificConfig != null
                            ? mobSpecificConfig.getString("messages.no-players", "&cNo players contributed to %BOSSNAME%.")
                            : announcementConfig.getString("messages.no-players", "&cNo players contributed damage to %BOSSNAME%."))
                            .replace("%BOSSNAME%", mobName));
            Bukkit.broadcastMessage(noPlayersMessage);
            logDebug("No players contributed damage to the mob: " + mobName);
            return;
        }

        // Announce header message
        String header = ChatColor.translateAlternateColorCodes('&',
                (mobSpecificConfig != null
                        ? mobSpecificConfig.getString("messages.header", "&aLIST OF PLAYERS WHO HAVE INFLICTED THE MOST DAMAGE ON %BOSSNAME%:")
                        : announcementConfig.getString("messages.header", "&aLIST OF PLAYERS WHO HAVE INFLICTED THE MOST DAMAGE ON %BOSSNAME%:"))
                        .replace("%BOSSNAME%", mobName));
        Bukkit.broadcastMessage(header);
        logDebug("Broadcasted header message: " + header);

        // Sort threat table by damage
        List<AbstractEntity> sortedRanking = activeMob.getThreatTable().getAllThreatTargets().stream()
                .sorted(Comparator.comparingDouble(activeMob.getThreatTable()::getThreat).reversed())
                .collect(Collectors.toList());
        logDebug("Sorted damage ranking for mob: " + mobName + ". Total contributors: " + sortedRanking.size());

        // Check if the mob is configured for Top 3 or Top 5 rewards
        boolean isTop3RewardMob = top3Config.getStringList("rewardtop3").contains(mobName);
        boolean isTop5RewardMob = top5Config.getStringList("rewardtop5").contains(mobName);

        // Announce Top 3 or Top 5 contributors based on configuration
        int maxEntries = isTop3RewardMob ? 3 : isTop5RewardMob ? 5 : 0;
        if (maxEntries > 0) {
            logDebug("Mob " + mobName + " is configured for Top " + maxEntries + " rewards. Announcing players...");
            for (int i = 0; i < Math.min(maxEntries, sortedRanking.size()); i++) {
                AbstractEntity entity = sortedRanking.get(i);
                if (entity.isPlayer()) {
                    Player player = (Player) entity.asPlayer().getBukkitEntity();
                    double damage = activeMob.getThreatTable().getThreat(entity);

                    String entry = ChatColor.translateAlternateColorCodes('&',
                            (mobSpecificConfig != null
                                    ? mobSpecificConfig.getString("messages.entry", "&8#%position% &a%player% &f(%damage% DAMAGE)")
                                    : announcementConfig.getString("messages.entry", "&e%position%. %player% - %damage% DAMAGE"))
                                    .replace("%position%", String.valueOf(i + 1))
                                    .replace("%player%", player.getName())
                                    .replace("%damage%", String.valueOf((int) damage)));

                    Bukkit.broadcastMessage(entry);
                    logDebug("Broadcasted entry for position " + (i + 1) + ": " + entry);
                } else {
                    logDebug("Entity at position " + (i + 1) + " is not a player. Skipping.");
                }
            }
        } else {
            logDebug("Mob " + mobName + " is not configured for Top 3 or Top 5 rewards. Skipping Top rankings.");
        }

        // Reward everyone else who contributed based on configuration
        ConfigurationSection everyoneElseConfig = isTop5RewardMob
                ? top5Config.getConfigurationSection(mobName + ".everyone-else-who-contributed")
                : top3Config.getConfigurationSection(mobName + ".everyone-else-who-contributed");

        if (everyoneElseConfig != null) {
            double minDamage = everyoneElseConfig.getDouble("min-damage", 0.0); // Default to 0.0
            logDebug("Processing rewards for everyone who contributed with minimum damage: " + minDamage);

            for (int i = maxEntries; i < sortedRanking.size(); i++) {
                AbstractEntity entity = sortedRanking.get(i);
                if (entity.isPlayer()) {
                    Player player = (Player) entity.asPlayer().getBukkitEntity();
                    double playerDamage = activeMob.getThreatTable().getThreat(entity);

                    if (playerDamage >= minDamage) {
                        logDebug("Rewarding player " + player.getName() + " for contributing damage: " + playerDamage);
                        processEveryoneElseRewards(mobName, player, playerDamage);
                    } else {
                        logDebug("Player " + player.getName() + " did not meet the min-damage threshold (" + minDamage + "). Skipping.");
                    }
                } else {
                    logDebug("Entity in contributors list is not a player. Skipping.");
                }
            }
        } else {
            logDebug("No 'everyone-else-who-contributed' configuration found for mob: " + mobName);
        }

        logDebug("Finished processing announcements for mob: " + mobName);
    }

    private boolean isMostDamageRewardEnabled() {
        return config.getBoolean("reward-processing.most-damage", false);
    }




    /**
     * Get the primary group of the player using LuckPerms.
     *
     * @param player The player whose group to fetch.
     * @return The primary group name.
     */
    private String getPrimaryGroup(Player player) {

        // Check if LuckPerms API is available
        if (luckPerms == null) {
            logDebug("LuckPerms API is not initialized. Using default group for player: " + player.getName());
            return "default";
        }

        // Attempt to fetch the LuckPerms user
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user != null) {
            logDebug("LuckPerms user found for player: " + player.getName());

            // Attempt to fetch the primary group
            String primaryGroup = user.getPrimaryGroup();
            if (primaryGroup != null && !primaryGroup.isEmpty()) {
                logDebug("Fetched primary group for player: " + player.getName() + " - " + primaryGroup);
                return primaryGroup;
            } else {
                logDebug("Primary group for player: " + player.getName() + " is null or empty. Using default group.");
            }
        } else {
            logDebug("No LuckPerms user found for player: " + player.getName() + ". Using default group.");
        }

        // Default fallback
        logDebug("Returning default group for player: " + player.getName());
        return "default";
    }



    /**
     * Load or reload the custom announcement config (announcement.yml).
     */
    private void loadAnnouncementConfig() {
        logDebug("Starting to load announcement.yml configuration file.");

        File announcementFile = new File(getDataFolder(), "announcement.yml");

        // Check if the file exists, and create it if necessary
        if (!announcementFile.exists()) {
            logDebug("announcement.yml does not exist. Attempting to create a new file.");
            announcementFile.getParentFile().mkdirs();
            saveResource("announcement.yml", false); // Create the file if it doesn't exist
            logDebug("announcement.yml file created successfully.");
        }

        announcementConfig = new YamlConfiguration();
        try {
            // Load the file into the configuration object
            announcementConfig.load(announcementFile);
            logDebug("announcement.yml loaded successfully.");
        } catch (IOException e) {
            getLogger().severe("I/O error occurred while loading announcement.yml: " + e.getMessage());
            logDebug("Stack trace for I/O exception: " + e.getMessage());
            return; // Exit if an error occurs
        } catch (InvalidConfigurationException e) {
            getLogger().severe("Invalid configuration in announcement.yml: " + e.getMessage());
            logDebug("Stack trace for invalid configuration: " + e.getMessage());
            return; // Exit if an error occurs
        }

        // Additional debugging to confirm if the file content is loaded properly
        if (announcementConfig.getKeys(false).isEmpty()) {
            logDebug("announcement.yml loaded but appears to be empty or improperly formatted.");
        } else {
            logDebug("announcement.yml loaded successfully with keys: " + announcementConfig.getKeys(false));
        }

        // Confirm presence of critical keys and log their values
        logDebug("Checking for critical keys in announcement.yml:");

        // Global 'announce-on-death' setting
        if (announcementConfig.contains("announce-on-death")) {
            logDebug("Key 'announce-on-death' found with value: " + announcementConfig.getBoolean("announce-on-death", true));
        } else {
            logDebug("Key 'announce-on-death' not found. Using default: true");
        }

        // Default messages
        if (announcementConfig.contains("messages.header")) {
            logDebug("Key 'messages.header' found with value: " + announcementConfig.getString("messages.header"));
        } else {
            logDebug("Key 'messages.header' not found. Using default: 'Default header'.");
        }

        if (announcementConfig.contains("messages.entry")) {
            logDebug("Key 'messages.entry' found with value: " + announcementConfig.getString("messages.entry"));
        } else {
            logDebug("Key 'messages.entry' not found. Using default: '&e%position%. %player% - %damage% DAMAGE'.");
        }

        if (announcementConfig.contains("messages.no-players")) {
            logDebug("Key 'messages.no-players' found with value: " + announcementConfig.getString("messages.no-players"));
        } else {
            logDebug("Key 'messages.no-players' not found. Using default: '&cNo players contributed damage to %BOSSNAME%'.");
        }

        // Validate specific mob settings
        logDebug("Validating announce-specific-mob settings...");
        ConfigurationSection specificMobConfig = announcementConfig.getConfigurationSection("announce-specific-mob");
        if (specificMobConfig != null) {
            for (String mobName : specificMobConfig.getKeys(false)) {
                logDebug("Found specific configuration for mob: " + mobName);
                ConfigurationSection mobConfig = specificMobConfig.getConfigurationSection(mobName);
                if (mobConfig != null) {
                    logDebug("Validating custom settings for mob: " + mobName);
                    if (mobConfig.contains("announce")) {
                        logDebug("Key 'announce' for mob " + mobName + " found with value: " + mobConfig.getBoolean("announce"));
                    } else {
                        logDebug("Key 'announce' for mob " + mobName + " not found. Defaulting to false.");
                    }

                    if (mobConfig.contains("messages.header")) {
                        logDebug("Key 'messages.header' for mob " + mobName + " found with value: " + mobConfig.getString("messages.header"));
                    } else {
                        logDebug("Key 'messages.header' for mob " + mobName + " not found. Defaulting to general header.");
                    }

                    if (mobConfig.contains("messages.entry")) {
                        logDebug("Key 'messages.entry' for mob " + mobName + " found with value: " + mobConfig.getString("messages.entry"));
                    } else {
                        logDebug("Key 'messages.entry' for mob " + mobName + " not found. Defaulting to general entry format.");
                    }

                    if (mobConfig.contains("messages.no-players")) {
                        logDebug("Key 'messages.no-players' for mob " + mobName + " found with value: " + mobConfig.getString("messages.no-players"));
                    } else {
                        logDebug("Key 'messages.no-players' for mob " + mobName + " not found. Defaulting to general no-players message.");
                    }
                } else {
                    logDebug("Configuration section for mob " + mobName + " is null or improperly formatted.");
                }
            }
        } else {
            logDebug("No specific mob configurations found under 'announce-specific-mob'.");
        }

        logDebug("Finished loading and validating announcement.yml.");
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
        try {
            // Ensure debugConfig is initialized before checking its values
            if (debugConfig != null && debugConfig.getBoolean("activate-debug", false)) {
                getLogger().info("[DEBUG] " + message);
            }
        } catch (Exception e) {
            // Log any issues with debug logging itself
            getLogger().severe("An error occurred while attempting to log a debug message: " + e.getMessage());
            e.printStackTrace();
        }
    }

}

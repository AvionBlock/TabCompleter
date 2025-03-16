/*
 * This file is part of TabCompleter.
 *
 * TabCompleter is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * TabCompleter is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with TabCompleter.  If not, see <https://www.gnu.org/licenses/>.
 */

package lol.hyper.tabcompleter;

import lol.hyper.githubreleaseapi.GitHubRelease;
import lol.hyper.githubreleaseapi.GitHubReleaseAPI;
import lol.hyper.tabcompleter.commands.CommandReload;
import lol.hyper.tabcompleter.events.PlayerCommandPreprocess;
import lol.hyper.tabcompleter.events.PlayerCommandSend;
import lol.hyper.tabcompleter.events.PlayerLeave;
import lol.hyper.tabcompleter.utils.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.permission.Permission;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import com.tcoded.folialib.FoliaLib;

import java.io.File;
import java.io.IOException;
import java.util.*;

import lombok.Getter;

public final class TabCompleter extends JavaPlugin implements Listener {

    public final File configFile = new File(this.getDataFolder(), "config.yml");
    public FileConfiguration config;
    private static @Getter TabCompleter instance;
    private static @Getter FoliaLib foliaLib;

    // this stores which groups have which commands from the config
    public final HashMap<String, List<String>> groupCommands = new HashMap<>();

    public PlayerCommandPreprocess playerCommandPreprocess;
    public PlayerCommandSend playerCommandSend;
    public PlayerLeave playerLeave;

    public Permission permission = null;

    public final MiniMessage miniMessage = MiniMessage.miniMessage();

    @Override
    public void onEnable() {
        foliaLib = new FoliaLib(this);
        instance = this;
        loadConfig(configFile);

        playerCommandPreprocess = new PlayerCommandPreprocess(this);
        playerCommandSend = new PlayerCommandSend(this);
        playerLeave = new PlayerLeave();

        Bukkit.getServer().getPluginManager().registerEvents(playerCommandPreprocess, this);
        Bukkit.getServer().getPluginManager().registerEvents(playerCommandSend, this);
        Bukkit.getServer().getPluginManager().registerEvents(playerLeave, this);

        this.getCommand("tcreload").setExecutor(new CommandReload(this));

        new Metrics(this, 10305);

        foliaLib.getScheduler().runAsync(task -> {
            checkForUpdates();
        });

        RegisteredServiceProvider<Permission> rsp = getServer().getServicesManager().getRegistration(Permission.class);
        if (rsp == null) {
            Logger.error("Vault is not installed!");
            Bukkit.getPluginManager().disablePlugin(this);
        } else {
            permission = rsp.getProvider();
        }
    }

    public void loadConfig(File file) {
        if (!configFile.exists()) {
            this.saveResource("config.yml", true);
        }
        config = YamlConfiguration.loadConfiguration(file);

        groupCommands.clear();

        // Load all base commands from the config
        ConfigurationSection groups = config.getConfigurationSection("groups");
        if (groups == null) {
            Logger.error(
                    "The 'groups' section is missing in the config! Plugin cannot function and will be disabled.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // If no "default" group in the config, add it
        if (!groups.contains("default")) {
            Logger.warn("No 'default' group in the config, adding...");
            groups.set("default.commands", Arrays.asList("help"));
            try {
                config.save(file);
            } catch (IOException e) {
                Logger.error("Error when saving the config!");
            }
        }

        boolean addDefaultCommands = config.getBoolean("add-default-commands", false);
        List<String> defaultCommands = config.getStringList("groups.default.commands");
        groupCommands.put("default", defaultCommands);

        for (String configGroup : groups.getKeys(false)) {
            List<String> commands = config.getStringList("groups." + configGroup + ".commands");

            // Skip the 'default' group as it has already been added
            if ("default".equals(configGroup)) {
                continue;
            }

            if (addDefaultCommands) {
                commands.addAll(defaultCommands.stream().filter(command -> !commands.contains(command)).toList());
            }

            groupCommands.put(configGroup, commands);
        }

        if (groupCommands.isEmpty()) {
            Logger.warn(
                    "No groups listed in the 'groups' section of the config. Please add groups. The plugin will not function.");
        }

        if (config.getInt("config-version") != 2) {
            Logger.warn("Your config file is outdated! Please regenerate the config.");
        }
    }

    /**
     * Gets a message from config.yml.
     *
     * @param path The path to the message.
     * @return Component with formatting applied.
     */
    public Component getMessage(String path) {
        String message = config.getString(path);
        if (message == null) {
            Logger.warn(path + " is not a valid message!");
            return Component.text("Invalid path! " + path).color(NamedTextColor.RED);
        }
        return miniMessage.deserialize(message);
    }

    public void checkForUpdates() {
        GitHubReleaseAPI api;
        try {
            api = new GitHubReleaseAPI("TabCompleter", "AvionBlock");
        } catch (IOException e) {
            Logger.warn("Unable to check updates!");
            e.printStackTrace();
            return;
        }

        try {
            GitHubRelease current = api.getReleaseByTag(this.getPluginMeta().getVersion());
            GitHubRelease latest = api.getLatestVersion();
            if (current == null) {
                Logger.warn(
                        "You are running a version that does not exist on GitHub. If you are in a dev environment, you can ignore this. Otherwise, this is a bug!");
                return;
            }

            if (latest == null) {
                Logger.warn("Unable to retrieve the latest release information.");
                return;
            }

            int buildsBehind = api.getBuildsBehind(current);
            if (buildsBehind == 0) {
                Logger.info("You are running the latest version.");
            } else {
                Logger.info("A new version is available (" + latest.getTagVersion() + ")! You are running version "
                        + current.getTagVersion() + ". You are " + buildsBehind + " version(s) behind.");
            }
        } catch (Exception e) {
            Logger.warn("Error when checking updates!");
        }
    }

    /**
     * Get all commands that a player can do/see.
     *
     * @param player The player to check.
     * @return List of all commands the player can do/see.
     */
    public List<String> getCommandsForPlayer(Player player) {
        List<String> allAllowedCommands = new ArrayList<>();
        String[] playerGroups = permission.getPlayerGroups(player);

        for (String playerGroup : playerGroups) {
            List<String> commands = groupCommands.get(playerGroup);

            // If the group is not found or it has no commands, fall back to 'default' group
            if (commands == null || commands.isEmpty()) {
                if (!groupCommands.containsKey(playerGroup)) {
                    Logger.info("Player group '" + playerGroup
                            + "' is not defined in the config. Falling back to 'default' group.");
                }
                commands = groupCommands.get("default");
            }

            if (commands != null && !commands.isEmpty()) {
                allAllowedCommands.addAll(commands);
            } else
                continue;
        }

        return allAllowedCommands;
    }
}

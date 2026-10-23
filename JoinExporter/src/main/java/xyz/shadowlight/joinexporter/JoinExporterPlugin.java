package xyz.shadowlight.joinexporter;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JoinExporterPlugin extends JavaPlugin implements Listener, TabExecutor {

    private static final String NOT_AVAILABLE = "N/A";
    private static final String UPDATE_PERMISSION = "mcgapi.update-notify";
    private static final String LEGACY_UPDATE_PERMISSION = "joinexporter.update-notify";
    private static final String LATEST_RELEASE_API = "https://api.github.com/repos/xShadowLightx/MC-GS_PlayerSync/releases/latest";

    private final Gson gson = new Gson();

    private String webhookUrl;
    private String secret;
    private boolean updateOnJoin;
    private boolean logSuccess;
    private String lastDateSource;
    private boolean loadLuckPermsOfflineUsers;
    private boolean updateCheckerEnabled;
    private int timeoutMs;
    private boolean exportName;
    private boolean exportFirstJoin;
    private boolean exportLastJoin;
    private boolean exportRole;
    private boolean exportPlayHours;
    private boolean exportBalance;
    private boolean exportDeaths;
    private boolean exportPlayerKills;
    private boolean exportLogoutLocation;
    private Set<String> redactionBlacklist = Set.of();

    private LuckPerms luckPerms;
    private final Map<UUID, String> lastLogoutLocations = new ConcurrentHashMap<>();
    private File logoutLocationsFile;
    private YamlConfiguration logoutLocationsConfig;
    private boolean updateAvailable;
    private String latestVersion;
    private String updateDownloadUrl;

    @Override
    public void onEnable() {
        migrateLegacyConfig();
        saveDefaultConfig();
        loadSettings();
        loadLogoutLocations();

        hookLuckPerms();

        getServer().getPluginManager().registerEvents(this, this);

        if (getCommand("exportjoins") != null) {
            getCommand("exportjoins").setExecutor(this);
            getCommand("exportjoins").setTabCompleter(this);
        }

        if (getCommand("exportplayer") != null) {
            getCommand("exportplayer").setExecutor(this);
            getCommand("exportplayer").setTabCompleter(this);
        }

        if (getCommand("joinexporterreload") != null) {
            getCommand("joinexporterreload").setExecutor(this);
            getCommand("joinexporterreload").setTabCompleter(this);
        }

        logStartupBanner();
        if (updateCheckerEnabled) {
            checkForUpdatesAsync();
        }
    }

    private void logStartupBanner() {
        List<String> enabledFields = new ArrayList<>();
        if (exportName) enabledFields.add("Name");
        if (exportFirstJoin) enabledFields.add("First Join");
        if (exportLastJoin) enabledFields.add("Last Join");
        if (exportRole) enabledFields.add("Role");
        if (exportPlayHours) enabledFields.add("Play Hours");
        if (exportBalance) enabledFields.add("Balance");
        if (exportDeaths) enabledFields.add("Deaths");
        if (exportPlayerKills) enabledFields.add("Player Kills");
        if (exportLogoutLocation) enabledFields.add("Logout Location");

        var console = Bukkit.getConsoleSender();
        console.sendMessage("§8§m----------------------------------------");
        console.sendMessage("");
        String[] logo = {
                "           ░██             ░███     ░███   ░██████            ░██████     ░███    ░█████████  ░██████",
                "           ░██             ░████   ░████  ░██   ░██          ░██   ░██   ░██░██   ░██     ░██   ░██",
                " ░███████  ░██             ░██░██ ░██░██ ░██                ░██         ░██  ░██  ░██     ░██   ░██",
                "░██        ░██             ░██ ░████ ░██ ░██        ░██████ ░██  █████ ░█████████ ░█████████    ░██",
                " ░███████  ░██             ░██  ░██  ░██ ░██                ░██     ██ ░██    ░██ ░██           ░██",
                "       ░██ ░██             ░██       ░██  ░██   ░██          ░██  ░███ ░██    ░██ ░██           ░██",
                " ░███████  ░██ ░██████████ ░██       ░██   ░██████            ░█████░█ ░██    ░██ ░██         ░██████",
                "",
                "",
                "",
                "   ___               __                 __  ___       _",
                "  / _ \\___ _  _____ / /__  ___  ___ ___/ / / _ )__ __(_) ",
                " / // / -_) |/ / -_) / _ \\/ _ \\/ -_) _  / / _  / // /",
                "/____/\\__/|___/\\__/_/\\___/ .__\\__/\\_,_/ /____/\\_, (_) ",
                "           ______       /_/ __           __   /___/  __   __",
                "    __ __ / __/ /  ___ ____/ /__ _    __/ /  (_)__ _/ /  / /___ __",
                "    \\ \\ /_\\ \\/ _ \\/ _ `/ _  / _ \\ |/|/ / /__/ / _ `/ _ \\/ __/\\ \\ /",
                "   /_\\_\\/___/_//_/\\_,_/\\_,_/\\___/__,__/____/_/\\_, /_//_/\\__//_\\_\\",
                "                                             /___/"
        };
        for (String line : logo) {
            console.sendMessage(line);
        }
        console.sendMessage("");
        console.sendMessage("§8§m----------------------------------------");
        console.sendMessage("§b  ◆ MC-GAPI");
        console.sendMessage("§7  Minecraft -> Google API");
        console.sendMessage("§7  Version: §f" + getDescription().getVersion());
        console.sendMessage("§7  Author: §f" + String.join(", ", getDescription().getAuthors()));
        console.sendMessage("§8§m----------------------------------------");
        console.sendMessage("§b  ✔ §fPlugin enabled successfully!");
        console.sendMessage("§7  Enabled features: §f" + enabledFields.size());
        console.sendMessage("§7  Export fields: §f" + (enabledFields.isEmpty()
                ? "None (UUID is always exported)" : String.join(", ", enabledFields)));
        console.sendMessage("§8§m----------------------------------------");
    }

    private void migrateLegacyConfig() {
        Path configFile = getDataFolder().toPath().resolve("config.yml");
        Path legacyConfigFile = getDataFolder().toPath().getParent().resolve("JoinExporter").resolve("config.yml");
        if (Files.exists(configFile) || !Files.exists(legacyConfigFile)) {
            return;
        }

        try {
            Files.createDirectories(configFile.getParent());
            Files.copy(legacyConfigFile, configFile, StandardCopyOption.COPY_ATTRIBUTES);
            getLogger().info("Copied existing configuration from plugins/JoinExporter to plugins/MC-GAPI.");
        } catch (Exception e) {
            getLogger().warning("Could not copy the old JoinExporter config: " + e.getMessage());
        }
    }

    private void hookLuckPerms() {
        if (getServer().getPluginManager().getPlugin("LuckPerms") == null) {
            getLogger().info("LuckPerms not found. Role export disabled.");
            return;
        }

        try {
            luckPerms = LuckPermsProvider.get();
            getLogger().info("Hooked into LuckPerms.");
        } catch (IllegalStateException e) {
            getLogger().warning("LuckPerms found but API was not available.");
            luckPerms = null;
        }
    }

    private void loadSettings() {
        reloadConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();

        webhookUrl = getConfig().getString("google.webhookUrl", "").trim();
        secret = getConfig().getString("google.secret", "").trim();
        exportName = getConfig().getBoolean("fields.name", true);
        exportFirstJoin = getConfig().getBoolean("fields.first-join", true);
        exportLastJoin = getConfig().getBoolean("fields.last-join", true);
        exportRole = getConfig().getBoolean("fields.role", true);
        exportPlayHours = getConfig().getBoolean("fields.play-hours", true);
        exportBalance = getConfig().getBoolean("fields.balance", true);
        exportDeaths = getConfig().getBoolean("fields.deaths", true);
        exportPlayerKills = getConfig().getBoolean("fields.player-kills", true);
        exportLogoutLocation = getConfig().getBoolean("fields.logout-location", true);
        Set<String> blacklistEntries = new HashSet<>();
        for (String entry : getConfig().getStringList("redaction.blacklist")) {
            if (entry != null && !entry.isBlank()) {
                blacklistEntries.add(entry.trim().toLowerCase(Locale.ROOT));
            }
        }
        redactionBlacklist = Set.copyOf(blacklistEntries);
        updateOnJoin = getConfig().getBoolean("options.update-on-join", true);
        logSuccess = getConfig().getBoolean("options.log-success", true);
        lastDateSource = getConfig().getString("options.last-date-source", "lastLogin").trim();
        loadLuckPermsOfflineUsers = getConfig().getBoolean("options.load-luckperms-offline-users", true);
        updateCheckerEnabled = getConfig().getBoolean("options.update-checker.enabled", true);
        timeoutMs = getConfig().getInt("options.timeout-ms", 10000);

        if (!lastDateSource.equalsIgnoreCase("lastLogin") && !lastDateSource.equalsIgnoreCase("lastSeen")) {
            getLogger().warning("Invalid options.last-date-source value. Falling back to lastLogin.");
            lastDateSource = "lastLogin";
        }
    }

    private void checkForUpdatesAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) URI.create(LATEST_RELEASE_API).toURL().openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                conn.setRequestProperty("User-Agent", "MC-GAPI/" + getDescription().getVersion());
                conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");

                int responseCode = conn.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    getLogger().fine("Update check returned HTTP " + responseCode + ".");
                    return;
                }

                JsonObject release;
                try (InputStream in = conn.getInputStream();
                     InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    release = JsonParser.parseReader(reader).getAsJsonObject();
                }

                String releaseTag = release.get("tag_name").getAsString();
                String currentVersion = getDescription().getVersion();
                if (!isVersionNewer(currentVersion, releaseTag)) {
                    return;
                }

                String releaseVersion = releaseTag.replaceFirst("(?i)^v-?", "");
                String downloadUrl = findJarDownloadUrl(release);
                Bukkit.getScheduler().runTask(this, () -> {
                    latestVersion = releaseVersion;
                    updateDownloadUrl = downloadUrl;
                    updateAvailable = true;

                    getLogger().info("MC-GAPI " + latestVersion + " is available. Current version: "
                            + currentVersion + ". Download: " + updateDownloadUrl);

                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (hasPermission(player, UPDATE_PERMISSION, LEGACY_UPDATE_PERMISSION)) {
                            notifyPlayerOfUpdate(player);
                        }
                    }
                });
            } catch (Exception e) {
                getLogger().fine("Update check failed: " + e.getMessage());
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    private String findJarDownloadUrl(JsonObject release) {
        if (release.has("assets") && release.get("assets").isJsonArray()) {
            JsonArray assets = release.getAsJsonArray("assets");
            for (JsonElement element : assets) {
                if (!element.isJsonObject()) {
                    continue;
                }

                JsonObject asset = element.getAsJsonObject();
                if (asset.has("name") && asset.get("name").getAsString().toLowerCase(Locale.ROOT).endsWith(".jar")
                        && asset.has("browser_download_url")) {
                    return asset.get("browser_download_url").getAsString();
                }
            }
        }

        if (release.has("html_url")) {
            return release.get("html_url").getAsString();
        }
        return "https://github.com/xShadowLightx/MC-GS_PlayerSync/releases/latest";
    }

    private boolean isVersionNewer(String currentVersion, String releaseVersion) {
        List<Integer> currentParts = versionParts(currentVersion);
        List<Integer> releaseParts = versionParts(releaseVersion);
        if (currentParts.isEmpty() || releaseParts.isEmpty()) {
            return false;
        }

        int partCount = Math.max(currentParts.size(), releaseParts.size());
        for (int i = 0; i < partCount; i++) {
            int currentPart = i < currentParts.size() ? currentParts.get(i) : 0;
            int releasePart = i < releaseParts.size() ? releaseParts.get(i) : 0;
            if (releasePart != currentPart) {
                return releasePart > currentPart;
            }
        }
        return false;
    }

    private List<Integer> versionParts(String version) {
        Matcher matcher = Pattern.compile("\\d+").matcher(version);
        List<Integer> parts = new ArrayList<>();
        while (matcher.find()) {
            parts.add(Integer.parseInt(matcher.group()));
        }
        return parts;
    }

    private void notifyPlayerOfUpdate(Player player) {
        Component message = Component.text("[MC-GAPI] Version " + latestVersion + " is available. ")
                .append(Component.text("Download the update here.")
                        .clickEvent(ClickEvent.openUrl(updateDownloadUrl)));
        player.sendMessage(message);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (updateCheckerEnabled && updateAvailable
                && hasPermission(event.getPlayer(), UPDATE_PERMISSION, LEGACY_UPDATE_PERMISSION)) {
            notifyPlayerOfUpdate(event.getPlayer());
        }

        if (!updateOnJoin) {
            return;
        }

        exportPlayerAsync(event.getPlayer(), null);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (isRedacted(event.getPlayer())) {
            lastLogoutLocations.remove(uuid);
            logoutLocationsConfig.set(uuid.toString(), null);
            saveLogoutLocations(event.getPlayer().getName());
            return;
        }

        Location location = event.getPlayer().getLocation();
        String value = String.format(Locale.ROOT, "%.2f,%.2f,%.2f", location.getX(), location.getY(), location.getZ());
        lastLogoutLocations.put(uuid, value);
        logoutLocationsConfig.set(uuid.toString(), value);
        saveLogoutLocations(event.getPlayer().getName());
    }

    private void saveLogoutLocations(String playerName) {
        try {
            logoutLocationsConfig.save(logoutLocationsFile);
        } catch (IOException e) {
            getLogger().warning("Could not save logout location for " + playerName + ": " + e.getMessage());
        }
    }

    private boolean isRedacted(OfflinePlayer player) {
        String uuid = player.getUniqueId().toString().toLowerCase(Locale.ROOT);
        String name = player.getName();
        return redactionBlacklist.contains(uuid)
                || (name != null && redactionBlacklist.contains(name.toLowerCase(Locale.ROOT)));
    }

    private void loadLogoutLocations() {
        logoutLocationsFile = new File(getDataFolder(), "logout-locations.yml");
        logoutLocationsConfig = YamlConfiguration.loadConfiguration(logoutLocationsFile);
        for (String key : logoutLocationsConfig.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String location = logoutLocationsConfig.getString(key);
                if (location != null && !location.isBlank()) {
                    lastLogoutLocations.put(uuid, location);
                }
            } catch (IllegalArgumentException ignored) {
                getLogger().warning("Ignoring invalid UUID in logout-locations.yml: " + key);
            }
        }
    }

    private long resolveLastDate(OfflinePlayer player) {
        if (lastDateSource.equalsIgnoreCase("lastSeen")) {
            return player.getLastSeen();
        }
        return player.getLastLogin();
    }

    private boolean hasPermission(CommandSender sender, String currentNode, String legacyNode) {
        return sender.hasPermission(currentNode) || sender.hasPermission(legacyNode);
    }

    private double resolvePlayHours(OfflinePlayer player) {
        try {
            int ticks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
            return Math.round((ticks / 20.0 / 3600.0) * 100.0) / 100.0;
        } catch (Exception e) {
            String name = player.getName() == null ? player.getUniqueId().toString() : player.getName();
            getLogger().warning("Failed to read playtime for " + name + ": " + e.getMessage());
            return 0.0;
        }
    }

    private int resolvePlayerStatistic(OfflinePlayer player, Statistic statistic, String label) {
        try {
            return player.getStatistic(statistic);
        } catch (Exception e) {
            String name = player.getName() == null ? player.getUniqueId().toString() : player.getName();
            getLogger().warning("Failed to read " + label + " for " + name + ": " + e.getMessage());
            return 0;
        }
    }

    private CompletableFuture<String> resolveRoleAsync(OfflinePlayer player) {
        if (luckPerms == null) {
            return CompletableFuture.completedFuture("");
        }

        try {
            if (player.isOnline() && player.getPlayer() != null) {
                User user = luckPerms.getPlayerAdapter(Player.class).getUser(player.getPlayer());
                return CompletableFuture.completedFuture(user.getPrimaryGroup());
            }

            User loadedUser = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (loadedUser != null) {
                return CompletableFuture.completedFuture(loadedUser.getPrimaryGroup());
            }

            if (!loadLuckPermsOfflineUsers) {
                return CompletableFuture.completedFuture("");
            }

            return luckPerms.getUserManager()
                    .loadUser(player.getUniqueId(), player.getName())
                    .thenApply(User::getPrimaryGroup)
                    .exceptionally(ex -> {
                        String name = player.getName() == null ? player.getUniqueId().toString() : player.getName();
                        getLogger().warning("Failed to load LuckPerms data for " + name + ": " + ex.getMessage());
                        return "";
                    });

        } catch (Exception e) {
            String name = player.getName() == null ? player.getUniqueId().toString() : player.getName();
            getLogger().warning("Failed to resolve role for " + name + ": " + e.getMessage());
            return CompletableFuture.completedFuture("");
        }
    }

    private CompletableFuture<PlayerRow> toPlayerRowAsync(OfflinePlayer player) {
        String uuid = player.getUniqueId().toString();
        String playerName = player.getName() == null ? "" : player.getName();
        if (isRedacted(player)) {
            return CompletableFuture.completedFuture(PlayerRow.redacted(uuid, playerName));
        }

        String name = !exportName ? NOT_AVAILABLE : playerName;
        Object firstJoin = exportFirstJoin ? player.getFirstPlayed() : NOT_AVAILABLE;
        Object lastJoin = exportLastJoin ? resolveLastDate(player) : NOT_AVAILABLE;
        Object playHours = exportPlayHours ? resolvePlayHours(player) : NOT_AVAILABLE;
        Object deaths = exportDeaths ? resolvePlayerStatistic(player, Statistic.DEATHS, "deaths") : NOT_AVAILABLE;
        Object playerKills = exportPlayerKills
                ? resolvePlayerStatistic(player, Statistic.PLAYER_KILLS, "player kills") : NOT_AVAILABLE;
        String logoutLocation = exportLogoutLocation
                ? lastLogoutLocations.getOrDefault(player.getUniqueId(), NOT_AVAILABLE)
                : NOT_AVAILABLE;

        Object balance = null;
        if (!exportBalance) {
            balance = NOT_AVAILABLE;
        } else {
            try {
                balance = Bukkit.getScheduler().callSyncMethod(this, () -> resolveBalance(player)).get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                getLogger().warning("Interrupted while reading balance for " + name + ".");
            } catch (Exception e) {
                getLogger().warning("Failed to read balance for " + name + ": " + e.getMessage());
            }
        }

        Object playerBalance = balance;
        CompletableFuture<String> roleFuture = exportRole
                ? resolveRoleAsync(player)
                : CompletableFuture.completedFuture(NOT_AVAILABLE);
        return roleFuture.thenApply(role ->
                new PlayerRow(uuid, name, firstJoin, lastJoin, role, playHours, playerBalance, deaths, playerKills,
                        logoutLocation, false)
        );
    }

    private Double resolveBalance(OfflinePlayer player) {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return null;
        }

        RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (registration == null || !registration.getProvider().isEnabled()) {
            return null;
        }

        return registration.getProvider().getBalance(player);
    }

    private void exportPlayerAsync(OfflinePlayer player, CommandSender senderToNotify) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                PlayerRow row = toPlayerRowAsync(player).join();
                ExportResult result = sendRow(row);

                if (senderToNotify != null) {
                    Bukkit.getScheduler().runTask(this, () -> {
                        if (result.success) {
                            senderToNotify.sendMessage("[MC-GAPI] Exported " + row.name + " successfully.");
                        } else {
                            senderToNotify.sendMessage("[MC-GAPI] Failed to export " + row.name + ": " + result.message);
                        }
                    });
                }
            } catch (Exception e) {
                if (senderToNotify != null) {
                    Bukkit.getScheduler().runTask(this, () ->
                            senderToNotify.sendMessage("[MC-GAPI] Failed to export player: " + e.getMessage())
                    );
                }
            }
        });
    }

    private void showExportData(OfflinePlayer target, CommandSender viewer, boolean ownData, String targetName) {
        viewer.sendMessage(ownData
                ? "[MC-GAPI] Loading your export data..."
                : "[MC-GAPI] Loading export data for " + targetName + "...");
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                PlayerRow row = toPlayerRowAsync(target).join();
                Bukkit.getScheduler().runTask(this, () -> {
                    if (viewer instanceof Player viewerPlayer && !viewerPlayer.isOnline()) {
                        return;
                    }
                    DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
                            .withZone(ZoneId.systemDefault());
                    viewer.sendMessage(ownData
                            ? "[MC-GAPI] Your export data (private):"
                            : "[MC-GAPI] Export data for " + targetName + " (private):");
                    viewer.sendMessage("UUID: " + row.uuid);
                    viewer.sendMessage("Name: " + row.name);
                    viewer.sendMessage("First Join: " + displayDate(row.firstJoin, dateFormat));
                    viewer.sendMessage("Last Join: " + displayDate(row.lastJoin, dateFormat));
                    viewer.sendMessage("Role: " + displayValue(row.role));
                    viewer.sendMessage("Play Hours: " + displayValue(row.playHours));
                    viewer.sendMessage("Balance: " + displayValue(row.balance));
                    viewer.sendMessage("Deaths: " + displayValue(row.deaths));
                    viewer.sendMessage("Player Kills: " + displayValue(row.playerKills));
                    viewer.sendMessage("Logout Location: " + displayValue(row.logoutLocation));
                });
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(this, () -> {
                    if (!(viewer instanceof Player viewerPlayer) || viewerPlayer.isOnline()) {
                        viewer.sendMessage("[MC-GAPI] Could not load export data: " + e.getMessage());
                    }
                });
            }
        });
    }

    private String displayDate(Object value, DateTimeFormatter formatter) {
        if (value instanceof Number timestamp) {
            return formatter.format(Instant.ofEpochMilli(timestamp.longValue()));
        }
        return displayValue(value);
    }

    private String displayValue(Object value) {
        return value == null ? NOT_AVAILABLE : String.valueOf(value);
    }

    private void exportAllPlayersAsync(CommandSender sender) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            int exported = 0;
            int failed = 0;

            for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
                if (!player.hasPlayedBefore()) {
                    continue;
                }

                try {
                    PlayerRow row = toPlayerRowAsync(player).join();
                    ExportResult result = sendRow(row);

                    if (result.success) {
                        exported++;
                    } else {
                        failed++;
                        getLogger().warning("Failed to export " + row.name + ": " + result.message);
                    }
                } catch (Exception e) {
                    failed++;
                    String name = player.getName() == null ? player.getUniqueId().toString() : player.getName();
                    getLogger().warning("Failed to export " + name + ": " + e.getMessage());
                }
            }

            int finalExported = exported;
            int finalFailed = failed;

            Bukkit.getScheduler().runTask(this, () ->
                    sender.sendMessage("[MC-GAPI] Finished export. Success: " + finalExported + ", Failed: " + finalFailed)
            );
        });
    }

    private ExportResult sendRow(PlayerRow row) {
        if (webhookUrl.isBlank()) {
            return ExportResult.failure("webhook URL is not configured");
        }

        if (secret.isBlank()) {
            return ExportResult.failure("shared secret is not configured");
        }

        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(webhookUrl).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

            Payload payload = new Payload(secret, row);
            String json = gson.toJson(payload);
            byte[] requestBody = json.getBytes(StandardCharsets.UTF_8);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(requestBody);
            }

            int responseCode = conn.getResponseCode();
            String responseText = readResponse(conn, responseCode);

            conn.disconnect();

            if (responseCode >= 200 && responseCode < 300) {
                if (logSuccess) {
                    getLogger().info("Exported " + row.name + " successfully. Response: " + responseText);
                }
                return ExportResult.success(responseText);
            }

            return ExportResult.failure("HTTP " + responseCode + " - " + responseText);
        } catch (Exception e) {
            return ExportResult.failure(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void deletePlayerAsync(UUID uuid, String displayName, CommandSender sender) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            ExportResult result = sendDelete(uuid.toString());
            Bukkit.getScheduler().runTask(this, () -> {
                if (!result.success) {
                    sender.sendMessage("[MC-GAPI] Failed to delete " + displayName + ": " + result.message);
                    return;
                }

                lastLogoutLocations.remove(uuid);
                logoutLocationsConfig.set(uuid.toString(), null);
                saveLogoutLocations(displayName);

                boolean deleted = false;
                try {
                    deleted = JsonParser.parseString(result.message).getAsJsonObject()
                            .get("deleted").getAsBoolean();
                } catch (Exception ignored) {
                    // The delete request succeeded; this only affects the response message.
                }
                sender.sendMessage(deleted
                        ? "[MC-GAPI] Deleted the Google Sheets row and saved logout location for " + displayName + "."
                        : "[MC-GAPI] No Google Sheets row existed for " + displayName + "; cleared saved logout location.");
            });
        });
    }

    private ExportResult sendDelete(String uuid) {
        if (webhookUrl.isBlank()) {
            return ExportResult.failure("webhook URL is not configured");
        }
        if (secret.isBlank()) {
            return ExportResult.failure("shared secret is not configured");
        }

        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) URI.create(webhookUrl).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

            byte[] requestBody = gson.toJson(new DeletePayload(secret, uuid)).getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(requestBody);
            }

            int responseCode = conn.getResponseCode();
            String responseText = readResponse(conn, responseCode);
            if (responseCode >= 200 && responseCode < 300) {
                try {
                    JsonObject response = JsonParser.parseString(responseText).getAsJsonObject();
                    if (response.has("ok") && !response.get("ok").getAsBoolean()) {
                        return ExportResult.failure("Apps Script: "
                                + response.get("error").getAsString());
                    }
                } catch (Exception e) {
                    return ExportResult.failure("invalid Apps Script response: " + responseText);
                }
                return ExportResult.success(responseText);
            }
            return ExportResult.failure("HTTP " + responseCode + " - " + responseText);
        } catch (Exception e) {
            return ExportResult.failure(e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private String readResponse(HttpURLConnection conn, int responseCode) {
        try {
            InputStream stream = (responseCode >= 200 && responseCode < 300)
                    ? conn.getInputStream()
                    : conn.getErrorStream();

            if (stream == null) {
                return "";
            }

            try (InputStream in = stream) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);

        if (cmd.equals("mcgapi")) {
            if (args.length == 0) {
                sender.sendMessage("[MC-GAPI] Usage: /mcgapi <export|player <name>|mydata|ingame <name>|delete <player|uuid>|reload>");
                return true;
            }

            String subcommand = args[0].toLowerCase(Locale.ROOT);
            args = java.util.Arrays.copyOfRange(args, 1, args.length);
            cmd = switch (subcommand) {
                case "export", "exportjoins" -> "exportjoins";
                case "player" -> "exportplayer";
                case "mydata" -> "mydata";
                case "ingame" -> "ingame";
                case "exportplayer" -> "exportplayer";
                case "delete" -> "deleteplayer";
                case "reload" -> "joinexporterreload";
                default -> "";
            };

            if (cmd.isEmpty()) {
                sender.sendMessage("[MC-GAPI] Usage: /mcgapi <export|player <name>|mydata|ingame <name>|delete <player|uuid>|reload>");
                return true;
            }
        }

        switch (cmd) {
            case "mydata" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("[MC-GAPI] This command can only be used in game by a player.");
                    return true;
                }
                showExportData(player, player, true, player.getName());
                return true;
            }

            case "ingame" -> {
                if (!sender.hasPermission("mcgapi.view-others")) {
                    sender.sendMessage("[MC-GAPI] You do not have permission.");
                    return true;
                }
                if (args.length < 1) {
                    sender.sendMessage("[MC-GAPI] Usage: /mcgapi ingame <name>");
                    return true;
                }

                OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
                if ((target.getName() == null || target.getName().isBlank()) && !target.hasPlayedBefore()) {
                    sender.sendMessage("[MC-GAPI] Player not found: " + args[0]);
                    return true;
                }
                String targetName = target.getName() == null ? args[0] : target.getName();
                showExportData(target, sender, false, targetName);
                return true;
            }

            case "exportjoins" -> {
                if (!hasPermission(sender, "mcgapi.export", "joinexporter.export")) {
                    sender.sendMessage("[MC-GAPI] You do not have permission.");
                    return true;
                }

                sender.sendMessage("[MC-GAPI] Starting export of all known players...");
                exportAllPlayersAsync(sender);
                return true;
            }

            case "exportplayer" -> {
                if (!hasPermission(sender, "mcgapi.export", "joinexporter.export")) {
                    sender.sendMessage("[MC-GAPI] You do not have permission.");
                    return true;
                }

                if (args.length < 1) {
                    sender.sendMessage("[MC-GAPI] Usage: /mcgapi player <name> (or /exportplayer <name>)");
                    return true;
                }

                OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
                if ((target.getName() == null || target.getName().isBlank()) && !target.hasPlayedBefore()) {
                    sender.sendMessage("[MC-GAPI] Player not found: " + args[0]);
                    return true;
                }

                sender.sendMessage("[MC-GAPI] Exporting " + (target.getName() == null ? args[0] : target.getName()) + "...");
                exportPlayerAsync(target, sender);
                return true;
            }

            case "deleteplayer" -> {
                if (!sender.hasPermission("mcgapi.delete")) {
                    sender.sendMessage("[MC-GAPI] You do not have permission.");
                    return true;
                }

                if (args.length < 1) {
                    sender.sendMessage("[MC-GAPI] Usage: /mcgapi delete <player|uuid>");
                    return true;
                }

                UUID targetUuid;
                String displayName;
                try {
                    targetUuid = UUID.fromString(args[0]);
                    OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
                    displayName = target.getName() == null ? targetUuid.toString() : target.getName();
                } catch (IllegalArgumentException e) {
                    OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
                    if ((target.getName() == null || target.getName().isBlank()) && !target.hasPlayedBefore()) {
                        sender.sendMessage("[MC-GAPI] Player not found: " + args[0]);
                        return true;
                    }
                    targetUuid = target.getUniqueId();
                    displayName = target.getName() == null ? args[0] : target.getName();
                }

                sender.sendMessage("[MC-GAPI] Deleting the Google Sheets row for " + displayName + "...");
                deletePlayerAsync(targetUuid, displayName, sender);
                return true;
            }

            case "joinexporterreload" -> {
                if (!hasPermission(sender, "mcgapi.reload", "joinexporter.reload")) {
                    sender.sendMessage("[MC-GAPI] You do not have permission.");
                    return true;
                }

                loadSettings();
                hookLuckPerms();
                if (updateCheckerEnabled) {
                    checkForUpdatesAsync();
                }
                sender.sendMessage("[MC-GAPI] Config reloaded.");
                return true;
            }

            default -> {
                return false;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String commandName = command.getName().toLowerCase(Locale.ROOT);
        if (commandName.equals("mcgapi")) {
            if (args.length == 1) {
                String prefix = args[0].toLowerCase(Locale.ROOT);
                return List.of("export", "player", "mydata", "ingame", "delete", "reload").stream()
                        .filter(value -> value.startsWith(prefix))
                        .toList();
            }
            if (args.length == 2 && (args[0].equalsIgnoreCase("player")
                    || args[0].equalsIgnoreCase("ingame") || args[0].equalsIgnoreCase("delete"))) {
                return completePlayerNames(args[1]);
            }
            return List.of();
        }

        if (!commandName.equals("exportplayer") || args.length != 1) {
            return List.of();
        }

        return completePlayerNames(args[0]);
    }

    private List<String> completePlayerNames(String currentArgument) {
        String prefix = currentArgument.toLowerCase(Locale.ROOT);
        List<String> results = new ArrayList<>();

        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            String name = player.getName();
            if (name != null && name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                results.add(name);
            }
        }

        return results;
    }

    private static final class Payload {
        private final String secret;
        private final PlayerRow player;

        private Payload(String secret, PlayerRow player) {
            this.secret = secret;
            this.player = player;
        }
    }

    private static final class DeletePayload {
        private final String secret;
        private final String action = "delete";
        private final String uuid;

        private DeletePayload(String secret, String uuid) {
            this.secret = secret;
            this.uuid = uuid;
        }
    }

    private static final class PlayerRow {
        private final String uuid;
        private final String name;
        private final Object firstJoin;
        private final Object lastJoin;
        private final String role;
        private final Object playHours;
        private final Object balance;
        private final Object deaths;
        private final Object playerKills;
        private final String logoutLocation;
        private final boolean redacted;

        private PlayerRow(String uuid, String name, Object firstJoin, Object lastJoin, String role, Object playHours,
                          Object balance, Object deaths, Object playerKills, String logoutLocation, boolean redacted) {
            this.uuid = uuid;
            this.name = name;
            this.firstJoin = firstJoin;
            this.lastJoin = lastJoin;
            this.role = role;
            this.playHours = playHours;
            this.balance = balance;
            this.deaths = deaths;
            this.playerKills = playerKills;
            this.logoutLocation = logoutLocation;
            this.redacted = redacted;
        }

        private static PlayerRow redacted(String uuid, String name) {
            return new PlayerRow(uuid, name, "redacted", "redacted", "redacted", "redacted", "redacted",
                    "redacted", "redacted", "redacted", true);
        }
    }

    private static final class ExportResult {
        private final boolean success;
        private final String message;

        private ExportResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        private static ExportResult success(String message) {
            return new ExportResult(true, message);
        }

        private static ExportResult failure(String message) {
            return new ExportResult(false, message);
        }
    }
}

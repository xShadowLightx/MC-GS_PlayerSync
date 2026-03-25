package xyz.shadowlight.joinexporter;

import com.google.gson.Gson;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class JoinExporterPlugin extends JavaPlugin implements Listener, TabExecutor {

    private final Gson gson = new Gson();

    private String webhookUrl;
    private String secret;
    private boolean updateOnJoin;
    private boolean logSuccess;
    private String lastDateSource;
    private int timeoutMs;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();

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

        getLogger().info("JoinExporter enabled.");
    }

    private void loadSettings() {
        reloadConfig();

        webhookUrl = getConfig().getString("google.webhookUrl", "").trim();
        secret = getConfig().getString("google.secret", "").trim();
        updateOnJoin = getConfig().getBoolean("options.update-on-join", true);
        logSuccess = getConfig().getBoolean("options.log-success", true);
        lastDateSource = getConfig().getString("options.last-date-source", "lastLogin").trim();
        timeoutMs = getConfig().getInt("options.timeout-ms", 10000);

        if (!lastDateSource.equalsIgnoreCase("lastLogin") && !lastDateSource.equalsIgnoreCase("lastSeen")) {
            getLogger().warning("Invalid options.last-date-source value. Falling back to lastLogin.");
            lastDateSource = "lastLogin";
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!updateOnJoin) {
            return;
        }

        exportPlayerAsync(event.getPlayer(), null);
    }

    private long resolveLastDate(OfflinePlayer player) {
        if (lastDateSource.equalsIgnoreCase("lastSeen")) {
            return player.getLastSeen();
        }
        return player.getLastLogin();
    }

    private PlayerRow toPlayerRow(OfflinePlayer player) {
        return new PlayerRow(
                player.getUniqueId().toString(),
                player.getName() == null ? "" : player.getName(),
                player.getFirstPlayed(),
                resolveLastDate(player)
        );
    }

    private void exportPlayerAsync(OfflinePlayer player, CommandSender senderToNotify) {
        PlayerRow row = toPlayerRow(player);

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            ExportResult result = sendRow(row);

            if (senderToNotify != null) {
                Bukkit.getScheduler().runTask(this, () -> {
                    if (result.success) {
                        senderToNotify.sendMessage("[JoinExporter] Exported " + row.name + " successfully.");
                    } else {
                        senderToNotify.sendMessage("[JoinExporter] Failed to export " + row.name + ": " + result.message);
                    }
                });
            }
        });
    }

    private void exportAllPlayersAsync(CommandSender sender) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            int exported = 0;
            int failed = 0;

            for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
                if (!player.hasPlayedBefore()) {
                    continue;
                }

                PlayerRow row = toPlayerRow(player);
                ExportResult result = sendRow(row);

                if (result.success) {
                    exported++;
                } else {
                    failed++;
                    getLogger().warning("Failed to export " + row.name + ": " + result.message);
                }
            }

            int finalExported = exported;
            int finalFailed = failed;

            Bukkit.getScheduler().runTask(this, () ->
                    sender.sendMessage("[JoinExporter] Finished export. Success: " + finalExported + ", Failed: " + finalFailed)
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

        switch (cmd) {
            case "exportjoins" -> {
                if (!sender.hasPermission("joinexporter.export")) {
                    sender.sendMessage("[JoinExporter] You do not have permission.");
                    return true;
                }

                sender.sendMessage("[JoinExporter] Starting export of all known players...");
                exportAllPlayersAsync(sender);
                return true;
            }

            case "exportplayer" -> {
                if (!sender.hasPermission("joinexporter.export")) {
                    sender.sendMessage("[JoinExporter] You do not have permission.");
                    return true;
                }

                if (args.length < 1) {
                    sender.sendMessage("[JoinExporter] Usage: /exportplayer <name>");
                    return true;
                }

                OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
                if ((target.getName() == null || target.getName().isBlank()) && !target.hasPlayedBefore()) {
                    sender.sendMessage("[JoinExporter] Player not found: " + args[0]);
                    return true;
                }

                sender.sendMessage("[JoinExporter] Exporting " + (target.getName() == null ? args[0] : target.getName()) + "...");
                exportPlayerAsync(target, sender);
                return true;
            }

            case "joinexporterreload" -> {
                if (!sender.hasPermission("joinexporter.reload")) {
                    sender.sendMessage("[JoinExporter] You do not have permission.");
                    return true;
                }

                loadSettings();
                sender.sendMessage("[JoinExporter] Config reloaded.");
                return true;
            }

            default -> {
                return false;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("exportplayer")) {
            return List.of();
        }

        if (args.length != 1) {
            return List.of();
        }

        String prefix = args[0].toLowerCase(Locale.ROOT);
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

    private static final class PlayerRow {
        private final String uuid;
        private final String name;
        private final long firstJoin;
        private final long lastJoin;

        private PlayerRow(String uuid, String name, long firstJoin, long lastJoin) {
            this.uuid = uuid;
            this.name = name;
            this.firstJoin = firstJoin;
            this.lastJoin = lastJoin;
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

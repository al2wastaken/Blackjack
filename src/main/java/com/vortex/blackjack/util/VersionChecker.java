package com.vortex.blackjack.util;

import com.vortex.blackjack.BlackjackPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles version checking against GitHub releases
 */
public class VersionChecker {
    private final BlackjackPlugin plugin;
    private final String currentVersion;
    private final String gitHubRepo;
    private String latestVersion;
    private boolean isOutdated = false;
    private boolean checkFailed = false;
    
    public VersionChecker(BlackjackPlugin plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getDescription().getVersion();
        this.gitHubRepo = "al2wastaken/Blackjack";
    }
    
    /**
     * Asynchronously check for updates
     */
    public void checkForUpdates() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                URI uri = new URI("https://api.github.com/repos/" + gitHubRepo + "/releases/latest");
                HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "BlackjackPlugin/" + currentVersion);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                
                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    
                    // Parse JSON manually using regex to avoid external dependencies
                    Pattern pattern = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
                    Matcher matcher = pattern.matcher(response.toString());
                    
                    if (matcher.find()) {
                        latestVersion = matcher.group(1);
                        
                        // Remove 'v' prefix if present
                        if (latestVersion.startsWith("v")) {
                            latestVersion = latestVersion.substring(1);
                        }
                        
                        // Compare versions
                        isOutdated = compareVersions(currentVersion, latestVersion) < 0;
                        
                        if (isOutdated) {
                            plugin.getLogger().info("New version available: " + latestVersion + " (Current: " + currentVersion + ")");
                            plugin.getLogger().info("Download at: https://github.com/" + gitHubRepo + "/releases/latest");
                        }
                    } else {
                        plugin.getLogger().warning("Failed to parse version from GitHub API response");
                        checkFailed = true;
                    }
                } else {
                    plugin.getLogger().warning("Failed to check for updates: HTTP " + responseCode);
                    checkFailed = true;
                }
                
                connection.disconnect();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to check for updates", e);
                checkFailed = true;
            }
        });
    }
    
    /**
     * Send update notification to an admin player
     */
    public void notifyAdmin(Player admin) {
        if (checkFailed) {
            return; // Don't notify if check failed
        }
        
        if (isOutdated) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                String downloadUrl = "https://github.com/" + gitHubRepo + "/releases/latest";
                List<String> lines = plugin.getConfigManager().getAdminVersionNotification(currentVersion, latestVersion, downloadUrl);
                for (String line : lines) {
                    admin.sendMessage(line);
                }
            }, 40L); // Delay 2 seconds after join
        }
    }
    
    /**
     * Get version status message for command
     */
    public String getVersionStatus() {
        var cfg = plugin.getConfigManager();
        if (checkFailed) {
            return cfg.getMessage("version-info.check-failed");
        }
        
        if (latestVersion == null) {
            return cfg.getMessage("version-info.checking");
        }
        
        if (isOutdated) {
            String downloadUrl = "https://github.com/" + gitHubRepo + "/releases/latest";
            return cfg.formatMessage("version-info.status-outdated", "current", currentVersion, "latest", latestVersion, "url", downloadUrl);
        } else {
            return cfg.formatMessage("version-info.status-current", "current", currentVersion);
        }
    }
    
    /**
     * Compare two version strings
     * @return negative if v1 < v2, 0 if equal, positive if v1 > v2
     */
    private int compareVersions(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        
        int maxLength = Math.max(parts1.length, parts2.length);
        
        for (int i = 0; i < maxLength; i++) {
            int num1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            int num2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;
            
            if (num1 != num2) {
                return Integer.compare(num1, num2);
            }
        }
        
        return 0;
    }
    
    public boolean isOutdated() {
        return isOutdated;
    }
    
    public String getCurrentVersion() {
        return currentVersion;
    }
    
    public String getLatestVersion() {
        return latestVersion;
    }

    public String getGitHubRepo() {
        return gitHubRepo;
    }
}

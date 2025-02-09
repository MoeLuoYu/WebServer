package xyz.moeluoyu.webserver;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.util.Objects;

import static org.bukkit.Bukkit.getLogger;

public class ConfigManager {
    private static File file;
    private static FileConfiguration fileConfiguration;

    /**
     * 设置配置文件。
     */
    public static void setup() {
        // 获取插件实例
        Plugin plugin = Objects.requireNonNull(Bukkit.getServer().getPluginManager().getPlugin("WebServer"));
        // 获取插件的数据文件夹
        File dataFolder = plugin.getDataFolder();

        // 确保数据文件夹存在
        if (!dataFolder.exists()) {
            if (dataFolder.mkdirs()) {
                getLogger().info("插件数据文件夹已创建: " + dataFolder.getAbsolutePath());
            } else {
                getLogger().severe("无法创建插件数据文件夹: " + dataFolder.getAbsolutePath());
                return;
            }
        }

        file = new File(dataFolder, "config.yml");

        // 检查配置文件是否存在；如果不存在，则创建默认值
        if (!file.exists()) {
            getLogger().warning("config.yml 不存在。正在创建默认配置...");
            createDefaultConfig(plugin);
        }

        fileConfiguration = YamlConfiguration.loadConfiguration(file);
    }

    /**
     * 创建默认配置文件。
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void createDefaultConfig(Plugin plugin) {
        try {
            file.createNewFile();
            fileConfiguration = YamlConfiguration.loadConfiguration(file);
            fileConfiguration.addDefault("port", 8080);
            fileConfiguration.addDefault("WebSiteFolder", "wwwroot");
            fileConfiguration.addDefault("LogConnections", false);
            fileConfiguration.options().copyDefaults(true);
            save();

            // 获取 WebSiteFolder 路径
            String webSiteFolder = fileConfiguration.getString("WebSiteFolder", "wwwroot");
            File targetDir = new File(plugin.getDataFolder(), webSiteFolder);

            // 确保 WebSiteFolder 目录存在
            if (!targetDir.exists()) {
                if (targetDir.mkdirs()) {
                    getLogger().info("WebSiteFolder 目录已创建: " + targetDir.getAbsolutePath());
                } else {
                    getLogger().severe("无法创建 WebSiteFolder 目录: " + targetDir.getAbsolutePath());
                    return;
                }
            }

            // 释放默认 index.html 文件
            releaseDefaultIndexHtml(plugin, targetDir);

        } catch (IOException e) {
            getLogger().severe("创建 config.yml 时出错: " + e.getMessage());
        }
    }

    /**
     * 释放默认 index.html 文件到目标目录
     * @param plugin 插件实例
     * @param targetDir 目标目录
     */
    private static void releaseDefaultIndexHtml(Plugin plugin, File targetDir) {
        InputStream inputStream = plugin.getResource("default/index.html");
        if (inputStream == null) {
            getLogger().severe("无法找到默认 index.html 文件");
            return;
        }

        File targetFile = new File(targetDir, "index.html");
        try (BufferedInputStream bis = new BufferedInputStream(inputStream);
             FileOutputStream fos = new FileOutputStream(targetFile);
             BufferedOutputStream bos = new BufferedOutputStream(fos)) {

            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = bis.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
            }
            getLogger().info("默认 index.html 文件已释放到: " + targetFile.getAbsolutePath());
        } catch (IOException e) {
            getLogger().severe("释放默认 index.html 文件时出错: " + e.getMessage());
        }
    }

    /**
     * 保存配置文件。
     */
    public static void save() {
        try {
            fileConfiguration.save(file);
        } catch (IOException e) {
            getLogger().severe("保存 config.yml 时出错: " + e.getMessage());
        }
    }
}
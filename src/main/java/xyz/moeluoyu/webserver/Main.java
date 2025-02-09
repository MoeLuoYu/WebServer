package xyz.moeluoyu.webserver;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Main extends JavaPlugin {

    private ServerSocket serverSocket = null;

    @Override
    public void onEnable() {
        ConfigManager.setup();
        // Plugin startup logic
        getConfig().options().copyDefaults();
        saveDefaultConfig();

        if (!validateConfig()) {
            getLogger().severe("配置文件加载失败！");
            this.setEnabled(false);
            return;
        }

        createFolder(getConfig().getString("WebSiteFolder"));
        startWebServer(getConfig().getInt("port"));
    }

    private boolean validateConfig() {
        // 验证配置值。如果任何值无效，则返回 false。
        // 目前，只是一个基本检查。根据需要扩展。
        return getConfig().isInt("port") && getConfig().isString("WebSiteFolder") && getConfig().isBoolean("LogConnections");
    }

    private void startWebServer(int port) {
        try {
            serverSocket = new ServerSocket(port);
            getLogger().info("Web服务器已在端口 " + port + " 上启动");
            
            new BukkitRunnable() {
                @Override
                public void run() {
                    acceptConnection();
                }
            }.runTaskTimerAsynchronously(this, 20, 10);
        } catch (IOException e) {
            getLogger().severe("Web服务启动失败: " + e.getMessage());
        }
    }

    private void acceptConnection() {
        try {
            Socket connectionSocket = serverSocket.accept();
            Thread connectionThread = new Thread(new Connection(connectionSocket, getConfig()));
            connectionThread.start();

            if (getConfig().getBoolean("LogConnections", false)) {
                getLogger().info("新连接已传入");
            }
        } catch (IOException e) {
            if (getConfig().getBoolean("LogConnections", false)) {
                getLogger().severe("连接已关闭: " + e.getMessage());
            }
        }
    }

    private void createFolder(String name) {
        File folder = new File(this.getDataFolder(), name);
        if (!folder.exists()) {
            if (folder.mkdir()) {
                getLogger().info("已创建文件夹: " + name);
            } else {
                getLogger().warning("创建文件夹失败: " + name);
            }
        }
    }

    @Override
    public void onDisable() {
        // 在插件禁用时关闭连接
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                getLogger().warning("关闭服务器套接字出现问题: " + e.getMessage());
            }
        }
    }
}

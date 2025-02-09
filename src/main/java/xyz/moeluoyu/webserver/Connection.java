package xyz.moeluoyu.webserver;

import org.bukkit.configuration.file.FileConfiguration;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;

import static org.bukkit.Bukkit.getLogger;

public class Connection implements Runnable {
    private final FileConfiguration config;
    // 用于连接服务器的套接字
    private final Socket connectionSocket;
    // 用于存储客户端请求中的键/值对的HashMap
    private final HashMap<String, String> request;
    // 包含需要重定向到给定值的键的HashMap
    private final HashMap<String, String> redirect;

    /**
     * 创建一个Connection对象
     *
     * @param connectionSocket 客户端用于连接服务器的套接字
     */
    public Connection(Socket connectionSocket, FileConfiguration config) {
        this.config = config;

        this.connectionSocket = connectionSocket;

        this.request = new HashMap<>();
        this.redirect = new HashMap<>();

        // 向重定向HashMap中添加键/值对
        // 键表示用户使用的URL值，值是要重定向到的URL值
        //redirect.put("/", "/index.html");
    }

    /**
     * 解析客户端请求，并将所有请求字段插入到
     * 请求HashMap中。键是请求字段，值是字段的值。
     *
     * @throws IOException 如果connectionSocket不存在且其输入流不可访问
     *
     */
    private void parseRequest() throws IOException {

        // 将BufferedReader连接到客户端套接字的输入流并读取其请求数据
        BufferedReader connectionReader = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));

        // 读取客户端请求的顶部行。例如：GET /index.html HTTP/1.1
        String requestLine = connectionReader.readLine();

        // 如果请求行存在，我们开始解析请求，否则它不是一个正确的请求
        // 我们什么也不做
        if (requestLine != null) {

            // 客户端请求的顶部行的格式与请求的其余部分不同，因此
            // 我们首先获取客户端请求顶部行的值
            String[] requestLineParams = requestLine.split(" ");

            // 从请求的顶部行提取相关信息。方法、资源URL和协议
            String requestMethod = requestLineParams[0];
            String requestResource = requestLineParams[1];
            String requestProtocol = requestLineParams[2];

            // 将方法、资源和协议添加到请求HashMap中
            request.put("Method", requestMethod);
            request.put("Resource", requestResource);
            request.put("Protocol", requestProtocol);

            // 读取客户端请求头的下一行
            String headerLine = connectionReader.readLine();

            // 当请求头仍有行可读时，我们继续读取并
            // 将每个请求字段的值存储到请求HashMap中
            while (!headerLine.isEmpty()) {

                // 将请求字段拆分为键值对
                String[] requestParams = headerLine.split(":", 2);

                // 将请求字段的键和值放入请求HashMap中
                request.put(requestParams[0], requestParams[1].replaceFirst(" ", ""));

                // 读取请求的下一个头行
                headerLine = connectionReader.readLine();
            }
        }
    }

    /**
     * 根据客户端请求发送适当的响应。
     * 如果请求的URL在重定向HashMap中
     * 客户端将收到一个HTTP 301错误重定向响应，将客户端重定向到
     * 新的URL位置。如果请求的URL不在重定向HashMap中
     * 并且不存在，则发送HTTP 404错误响应。如果URL请求
     * 存在，则发送HTTP 200 OK响应。
     *
     * @throws IOException 如果outStream、fileStream或bufInputStream在使用时已关闭或不存在
     *
     */
    private void sendResponse() throws IOException {

        // 创建一个DataOutputStream，outStream，以便能够向客户端连接发送信息
        DataOutputStream outStream = new DataOutputStream(connectionSocket.getOutputStream());

        // 获取客户端连接请求的文件路径
        String resourcePath = request.get("Resource");
        File file;

        // 检查请求的资源是否为根路径
        if ("/".equals(resourcePath)) {
            // 尝试加载 index.html 文件
            file = new File("./plugins/WebServer/" + config.getString("WebSiteFolder") + "/index.html");
        } else {
            file = new File("./plugins/WebServer/" + config.getString("WebSiteFolder") + resourcePath);
        }

        // 如果客户端请求的文件在重定向HashMap中，则向客户端发送一个
        // HTTP 301响应并将客户端重定向到新的文件地址
        if (redirect.get(resourcePath) != null) {

            // 向客户端发送HTTP 301请求并重定向到新地址
            outStream.writeBytes("HTTP/1.1 301 Moved Permanently\n" +
                    "Location: " + redirect.get(resourcePath));
        }

        // 如果请求的文件不存在，则向客户端发送一个带有网页的HTTP 404响应
        // 告诉客户端文件未找到
        else if (!file.exists()) {

            // 创建带有基本网页的HTTP 404响应
            String http404Response = "HTTP/1.1 404 Not Found\r\n\r\n" + "<!DOCTYPE html>\n" +
                    "<html>\n" +
                    "<head><title>404 Not Found</title></head>\n" +
                    "<body>\n" +
                    "<center><h1>404 Not Found</h1></center>\n" +
                    "<hr><center>WebServer</center>\n" +
                    "</body>\n";

            // 使用UTF-8编码向客户端发送HTTP 404响应
            outStream.write(http404Response.getBytes(StandardCharsets.UTF_8));
        }

        // 如果文件不在重定向HashMap中且存在，则我们向客户端发送
        // 一个HTTP 200响应并提供文件
        else {

            // 打开文件输入流以从文件中读取数据
            FileInputStream fileStream = new FileInputStream(file);

            // 获取客户端请求的文件的MIME文件类型
            String contentType = Files.probeContentType(file.toPath());

            // 创建一个BufferedInputStream以从fileStream中读取数据
            BufferedInputStream bufInputStream = new BufferedInputStream(fileStream);

            // 创建一个与请求文件长度相同的字节数组来保存文件数据字节
            byte[] bytes = new byte[(int) file.length()];

            // 发送HTTP 200响应的头部
            outStream.writeBytes("HTTP/1.1 200 OK\r\nContent-Type: " + contentType + "\r\n\r\n");

            // 将请求文件中的数据读入字节数组
            bufInputStream.read(bytes);

            // 将字节数组中包含的数据发送到客户端连接并刷新输出流
            outStream.write(bytes);
            outStream.flush();

            // 关闭输入流
            bufInputStream.close();
        }
        // 关闭输出流
        outStream.close();
    }


    /**
     * 在一个线程中可运行的 Connection 对象执行的开始时运行
     */
    @Override
    public void run() {
        try {
            // 解析客户端请求，并将请求字段的键/值存储在请求 HashMap 中
            parseRequest();

            // 根据服务器接收到的请求向客户端发送适当的响应
            sendResponse();

            // 关闭客户端的连接
            this.connectionSocket.close();
        } catch (IOException ex) {
            // 如果捕获到 IOException，则打印导致错误的命令堆栈。
            // ex.printStackTrace();
            getLogger().severe("连接请求出现问题: "+ ex.getMessage());
        }
    }
}
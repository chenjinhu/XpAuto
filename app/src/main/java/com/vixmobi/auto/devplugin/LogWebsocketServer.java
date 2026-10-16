package com.vixmobi.auto.devplugin;


import static com.vixmobi.auto.Constant.LOGSERVER;

import android.util.Log;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;

public class LogWebsocketServer {
    private static final String TAG = "LogWebsocketServer";
    private static final int PORT = 8887;  // 默认端口
    private static WebSocketServer server;
    private static boolean isRunning = false;
    private static LogWebsocketServer instance;
    public LogWebsocketServer() {
    }

    public static LogWebsocketServer getInstance() {
        if (instance == null) {
            instance = new LogWebsocketServer();
        }
        return instance;
    }
    public static boolean start() {
        if (isRunning) {
            Log.d(TAG, "WebSocket server is already running");
            return false;
        }

        try {
            // 创建 WebSocket 服务器
            server = new WebSocketServer(new InetSocketAddress(PORT)) {
                @Override
                public void onOpen(WebSocket conn, ClientHandshake handshake) {
                    String clientId = conn.getRemoteSocketAddress().getAddress().getHostAddress();
                    Log.d(TAG, "New connection from: " + clientId);
                }

                @Override
                public void onClose(WebSocket conn, int code, String reason, boolean remote) {
                    String clientId = conn.getRemoteSocketAddress().getAddress().getHostAddress();
                    Log.d(TAG, "Connection closed: " + clientId + " with code: " + code + " reason: " + reason);
                }

                @Override
                public void onMessage(WebSocket conn, String message) {
                    String clientId = conn.getRemoteSocketAddress().getAddress().getHostAddress();
                    Log.d(TAG, "Received message from " + clientId + ": " + message);

                    // 处理接收到的消息
                    handleMessage(conn, message);
                }

                @Override
                public void onError(WebSocket conn, Exception ex) {
                    String clientId = conn != null ? conn.getRemoteSocketAddress().getAddress().getHostAddress() : "Unknown";
                    Log.e(TAG, "Error occurred on connection " + clientId, ex);
                }

                @Override
                public void onStart() {
                    Log.d(TAG, "WebSocket server started on port: " + PORT);
                    isRunning = true;
                }
            };

            // 配置服务器
            server.setReuseAddr(true);
            server.setConnectionLostTimeout(30);  // 30秒超时检测

            // 启动服务器
            server.start();


            return true;

        } catch (Exception e) {
            Log.e(TAG, "Failed to start WebSocket server", e);
            return false;
        }
    }


    public static void stop() {
        if (server != null && isRunning) {
            try {
                server.stop();
                isRunning = false;
                Log.d(TAG, "WebSocket server stopped");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping WebSocket server", e);
            }
        }
    }

    private static void handleMessage(WebSocket conn, String message) {
        try {
            // 这里处理接收到的消息
            // 例如：回显消息
            conn.send("Server received: " + message);

            // 广播消息给所有连接的客户端
            broadcast("Broadcast: " + message);
        } catch (Exception e) {
            Log.e(TAG, "Error handling message", e);
        }
    }

    public static void broadcast(String message) {
        if (server != null && isRunning) {
            server.broadcast(message);
        }
    }


    public static void send(String message) {
        broadcast(message);
    }
    public static void runServer() {
        if (isRunning) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                start();
            }
        }).start();
    }
}
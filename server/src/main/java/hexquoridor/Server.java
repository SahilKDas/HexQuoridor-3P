// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2026 HexQuoridor-3P contributors
package hexquoridor;

import com.sun.net.httpserver.HttpServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import java.net.*;
import java.nio.file.*;
import java.util.concurrent.Executors;

public final class Server {
    public static void main(String[] args) throws Exception {
        String host=System.getenv().getOrDefault("HOST","127.0.0.1");
        int httpPort=Integer.parseInt(System.getenv().getOrDefault("PORT","8080"));
        int wsPort=Integer.parseInt(System.getenv().getOrDefault("WS_PORT","8081"));
        GameSessionManager sessions=new GameSessionManager();
        WebSocketServer ws=new WebSocketServer(new InetSocketAddress(host,wsPort),2) {
            @Override public void onOpen(WebSocket socket,ClientHandshake handshake) {
                if(!handshake.getResourceDescriptor().equals("/ws")) socket.close(1008,"Use /ws");
            }
            @Override public void onMessage(WebSocket socket,String message) { sessions.message(socket,message); }
            @Override public void onClose(WebSocket socket,int code,String reason,boolean remote) { sessions.close(socket); }
            @Override public void onError(WebSocket socket,Exception error) { System.err.println("WebSocket: "+error.getMessage()); }
            @Override public void onStart() { System.out.println("WebSocket listening on "+host+":"+wsPort); }
        };
        ws.setConnectionLostTimeout(30); ws.setReuseAddr(true); ws.start();
        Path root=Path.of("client/dist").toAbsolutePath().normalize();
        HttpServer http=HttpServer.create(new InetSocketAddress(host,httpPort),0);
        http.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        http.createContext("/",exchange-> {
            try {
                if(!exchange.getRequestMethod().equals("GET") && !exchange.getRequestMethod().equals("HEAD")) {
                    exchange.sendResponseHeaders(405,-1); return;
                }
                String request=exchange.getRequestURI().getPath();
                if(request.equals("/health")) {
                    byte[] body="{\"status\":\"ok\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type","application/json");
                    exchange.sendResponseHeaders(200,body.length); exchange.getResponseBody().write(body); return;
                }
                Path file=root.resolve(request.substring(1)).normalize();
                if(!file.startsWith(root)) { exchange.sendResponseHeaders(403,-1); return; }
                if(Files.isDirectory(file)) file=file.resolve("index.html");
                if(!Files.isRegularFile(file)) {
                    byte[] body="Build the client with pnpm --dir client build, or use pnpm dev.".getBytes();
                    exchange.sendResponseHeaders(404,body.length); exchange.getResponseBody().write(body); return;
                }
                if(!file.toRealPath().startsWith(root.toRealPath())) { exchange.sendResponseHeaders(403,-1); return; }
                String name=file.getFileName().toString();
                String type=name.endsWith(".html")?"text/html; charset=utf-8":name.endsWith(".js")?"text/javascript":
                    name.endsWith(".css")?"text/css":name.endsWith(".svg")?"image/svg+xml":"application/octet-stream";
                exchange.getResponseHeaders().set("Content-Type",type);
                exchange.getResponseHeaders().set("X-Content-Type-Options","nosniff");
                if(exchange.getRequestMethod().equals("HEAD")) { exchange.sendResponseHeaders(200,-1); return; }
                exchange.sendResponseHeaders(200,Files.size(file));
                Files.copy(file,exchange.getResponseBody());
            } finally { exchange.close(); }
        });
        http.start();
        Runtime.getRuntime().addShutdownHook(new Thread(()-> {
            http.stop(0); try { ws.stop(1000); } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
        }));
        System.out.println("HexQuoridor-3P: http://"+host+":"+httpPort);
    }
}

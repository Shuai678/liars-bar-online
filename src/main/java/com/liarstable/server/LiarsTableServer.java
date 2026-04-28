package com.liarstable.server;

import com.liarstable.common.Protocol;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class LiarsTableServer {
    private final GameRoom room = new GameRoom();

    public static void main(String[] args) throws IOException {
        new LiarsTableServer().start();
    }

    public void start() throws IOException {
        int port = Protocol.port();
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server Liar's Table avviato sulla porta " + port);
            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(() -> handleClient(socket), "client-handler").start();
            }
        }
    }

    private void handleClient(Socket socket) {
        Player player = null;
        try {
            player = new Player(socket);
            player.send("WELCOME;Inserisci nickname con JOIN;nome");

            String line;
            while ((line = player.in.readLine()) != null) {
                String[] parts = Protocol.split(line);
                switch (parts[0]) {
                    case "JOIN" -> {
                        if (parts.length > 1 && !parts[1].isBlank()) player.nickname = parts[1].trim();
                        if (!room.addPlayer(player)) player.send("ERROR;Lobby piena o partita già iniziata");
                    }
                    case "READY" -> room.setReady(player);
                    case "PLAY_CARD" -> {
                        int index = parts.length > 1 ? Integer.parseInt(parts[1]) : -1;
                        room.playCard(player, index);
                    }
                    case "CALL_BLUFF" -> room.callBluff(player);
                    case "PASS" -> room.pass(player);
                    case "PULL_TRIGGER" -> room.pullTrigger(player);
                    default -> player.send("ERROR;Comando sconosciuto: " + parts[0]);
                }
            }
        } catch (Exception e) {
            System.out.println("Client disconnesso: " + e.getMessage());
        } finally {
            if (player != null) room.removePlayer(player);
            try { socket.close(); } catch (IOException ignored) {}
        }
    }
}

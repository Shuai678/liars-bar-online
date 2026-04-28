package com.liarstable.server;

import java.io.*;
import java.net.Socket;
import java.util.*;

public class Player {
    public final Socket socket;
    public final BufferedReader in;
    public final PrintWriter out;
    public String nickname = "Player";
    public int lives = 3;
    public int bulletsLoaded = 1;
    public int cylinderSlots = 6;
    public boolean ready = false;
    public final List<Card> hand = new ArrayList<>();

    public Player(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
    }

    public void send(String msg) {
        out.println(msg);
    }
}

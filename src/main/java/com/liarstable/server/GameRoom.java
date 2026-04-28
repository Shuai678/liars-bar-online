package com.liarstable.server;

import java.util.*;
import java.util.stream.Collectors;

public class GameRoom {
    private final List<Player> players = new ArrayList<>();
    private final Random random = new Random();
    private Card target = Card.KING;
    private int turnIndex = 0;
    private Card lastPlayedCard = null;
    private Player lastPlayer = null;
    private Player riskPlayer = null;
    private String riskReason = "";
    private int riskSlotsAtStart = 0;
    private boolean riskChamberActive = false;
    private boolean started = false;
    private int round = 0;

    public synchronized boolean addPlayer(Player p) {
        if (started || players.size() >= 4) return false;
        players.add(p);
        broadcast("LOG;" + p.nickname + " è entrato nella lobby");
        broadcastLobby();
        return true;
    }

    public synchronized void removePlayer(Player p) {
        players.remove(p);
        broadcast("LOG;" + p.nickname + " si è disconnesso");
        if (riskPlayer == p) {
            riskPlayer = null;
            riskReason = "";
            riskSlotsAtStart = 0;
            riskChamberActive = false;
        }
        if (!players.isEmpty()) broadcastLobby();
        if (started && alivePlayers().size() <= 1) {
            Player winner = alivePlayers().isEmpty() ? null : alivePlayers().get(0);
            broadcast("GAME_OVER;" + (winner == null ? "Nessuno" : winner.nickname));
            started = false;
            players.forEach(player -> player.ready = false);
            broadcastLobby();
        }
    }

    public synchronized void setReady(Player p) {
        p.ready = true;
        broadcast("LOG;" + p.nickname + " è pronto");
        broadcastLobby();
        if (players.size() >= 2 && players.stream().allMatch(x -> x.ready)) {
            startGame();
        }
    }

    private void startGame() {
        started = true;
        round = 0;
        riskPlayer = null;
        riskReason = "";
        riskSlotsAtStart = 0;
        riskChamberActive = false;
        for (Player p : players) {
            p.lives = 1;
            p.bulletsLoaded = 1;
            p.cylinderSlots = 6;
            p.hand.clear();
        }
        newRound();
    }

    private void newRound() {
        round++;
        target = random.nextBoolean() ? Card.KING : Card.QUEEN;
        lastPlayedCard = null;
        lastPlayer = null;
        turnIndex = nextAliveIndex(turnIndex);

        for (Player p : alivePlayers()) {
            p.hand.clear();
            for (int i = 0; i < 3; i++) p.hand.add(randomCard());
        }
        broadcast("LOG;Nuovo round. Carta dichiarata: " + target.label());
        sendStateToAll();
    }

    public synchronized void pass(Player p) {
        if (riskChamberActive) {
            p.send("ERROR;Risk Chamber in corso"); return;
        }
        if (!started || currentPlayer() != p) {
            p.send("ERROR;Non è il tuo turno"); return;
        }
        broadcast("LOG;" + p.nickname + " passa il turno");
        turnIndex = nextAliveIndex(turnIndex + 1);
        sendStateToAll();
    }

    private Card randomCard() {
        int n = random.nextInt(10);
        if (n < 4) return Card.KING;
        if (n < 8) return Card.QUEEN;
        return Card.JOKER;
    }

    public synchronized void playCard(Player p, int index) {
        if (riskChamberActive) {
            p.send("ERROR;Risk Chamber in corso"); return;
        }
        if (!started || currentPlayer() != p) {
            p.send("ERROR;Non è il tuo turno"); return;
        }
        if (index < 0 || index >= p.hand.size()) {
            p.send("ERROR;Carta non valida"); return;
        }
        lastPlayedCard = p.hand.remove(index);
        lastPlayer = p;
        broadcast("LOG;" + p.nickname + " ha giocato una carta coperta dichiarando " + target.label());
        turnIndex = nextAliveIndex(turnIndex + 1);
        sendStateToAll();
    }

    public synchronized void callBluff(Player accuser) {
        if (riskChamberActive) {
            accuser.send("ERROR;Risk Chamber in corso"); return;
        }
        if (!started) return;
        if (accuser.lives <= 0) {
            accuser.send("ERROR;Sei eliminato dalla partita"); return;
        }
        if (lastPlayedCard == null || lastPlayer == null) {
            accuser.send("ERROR;Non c'è nessuna carta da accusare"); return;
        }
        if (accuser == lastPlayer) {
            accuser.send("ERROR;Non puoi accusare te stesso"); return;
        }

        boolean truthful = lastPlayedCard == target || lastPlayedCard == Card.JOKER;
        if (!truthful) {
            broadcast("RESULT;Bluff caught: " + lastPlayer.nickname + " revealed " + lastPlayedCard.label());
            startRussianRoulette(lastPlayer, "Bluff caught");
            return;
        }

        broadcast("RESULT;Wrong accusation: " + accuser.nickname + " challenged " + lastPlayer.nickname + ", but the card was " + lastPlayedCard.label());
        startRussianRoulette(accuser, "Wrong accusation");
    }

    public synchronized void pullTrigger(Player p) {
        if (!riskChamberActive || riskPlayer == null) {
            p.send("ERROR;Nessuna Risk Chamber attiva"); return;
        }
        if (p != riskPlayer) {
            p.send("ERROR;Solo " + riskPlayer.nickname + " può premere PULL TRIGGER"); return;
        }

        int slotsBefore = Math.max(1, riskSlotsAtStart);
        int roll = random.nextInt(slotsBefore) + 1;
        boolean eliminated = roll <= riskPlayer.bulletsLoaded;
        int slotsAfter = eliminated ? 0 : Math.max(1, slotsBefore - 1);
        if (eliminated) {
            riskPlayer.lives = 0;
            riskPlayer.hand.clear();
        } else {
            riskPlayer.cylinderSlots = slotsAfter;
        }

        broadcast("RISK;RESULT;" + riskPlayer.nickname + ";" + riskReason + ";" + slotsBefore + ";" + slotsAfter + ";" + roll + ";" + eliminated);
        Player resolvedPlayer = riskPlayer;
        riskPlayer = null;
        riskReason = "";
        riskSlotsAtStart = 0;
        riskChamberActive = false;
        continueAfterPenalty(resolvedPlayer);
    }

    private void startRussianRoulette(Player player, String reason) {
        riskPlayer = player;
        riskReason = reason;
        riskSlotsAtStart = Math.max(1, player.cylinderSlots);
        riskChamberActive = true;
        sendRiskStart();
    }

    private void continueAfterPenalty(Player player) {
        List<Player> alive = alivePlayers();
        if (alive.size() <= 1) {
            Player winner = alive.isEmpty() ? null : alive.get(0);
            broadcast("GAME_OVER;" + (winner == null ? "Nessuno" : winner.nickname));
            started = false;
            players.forEach(p -> p.ready = false);
            broadcastLobby();
        } else {
            turnIndex = players.indexOf(player);
            if (turnIndex < 0) turnIndex = 0;
            newRound();
        }
    }

    private void sendRiskStart() {
        for (Player p : players) {
            p.send("RISK;START;" + riskPlayer.nickname + ";" + riskReason + ";" + riskSlotsAtStart + ";" + (p == riskPlayer));
        }
    }

    private Player currentPlayer() {
        if (players.isEmpty()) return null;
        turnIndex = nextAliveIndex(turnIndex);
        return players.get(turnIndex);
    }

    private int nextAliveIndex(int start) {
        if (players.isEmpty()) return 0;
        for (int i = 0; i < players.size(); i++) {
            int idx = Math.floorMod(start + i, players.size());
            if (players.get(idx).lives > 0) return idx;
        }
        return 0;
    }

    private List<Player> alivePlayers() {
        return players.stream().filter(p -> p.lives > 0).collect(Collectors.toList());
    }

    private void broadcastLobby() {
        String names = players.stream()
                .map(p -> p.nickname + (p.ready ? "(ready)" : ""))
                .collect(Collectors.joining(","));
        broadcast("LOBBY;" + names);
    }

    private void sendStateToAll() {
        for (Player p : players) {
            String hand = p.hand.stream().map(Card::label).collect(Collectors.joining(","));
            String lives = players.stream().map(x -> x.nickname + ":" + x.lives).collect(Collectors.joining(","));
            String roulette = players.stream().map(x -> x.nickname + ":" + x.cylinderSlots).collect(Collectors.joining(","));
            String current = currentPlayer() == null ? "" : currentPlayer().nickname;
            p.send("STATE;" + round + ";" + target.label() + ";" + current + ";" + lives + ";" + roulette + ";" + hand + ";" + (currentPlayer() == p));
        }
    }

    private void broadcast(String msg) {
        for (Player p : players) p.send(msg);
    }
}

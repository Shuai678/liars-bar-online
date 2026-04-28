package com.liarstable.client;

import com.liarstable.common.Protocol;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LiarsTableClientApp extends Application {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    private final TextField ipField = new TextField("127.0.0.1");
    private final TextField portField = new TextField(String.valueOf(Protocol.port()));
    private final TextField nicknameField = new TextField("Player");

    private final StackPane root = new StackPane();
    private final VBox logList = new VBox(8);
    private final ScrollPane logScroll = new ScrollPane(logList);
    private final HBox handBox = new HBox(18);
    private final VBox leftPlayers = new VBox(14);
    private final VBox rightPlayers = new VBox(14);
    private final AnchorPane lobbySeats = new AnchorPane();
    private final Label statusLabel = new Label("Not connected");
    private final Label mainMessageLabel = new Label("Find a seat at the table.");
    private final Label lobbyMessageLabel = new Label("Waiting for other players...");
    private final Label roundLabel = new Label("Round 0");
    private final Label targetLabel = new Label("TARGET");
    private final Label targetCardLabel = new Label("-");
    private final Label currentPlayerLabel = new Label("Waiting");
    private final Label playedCardLabel = new Label("CARD BACK");
    private final Button readyButton = casinoButton("Ready", "button-gold");
    private final Button playButton = casinoButton("Play Card", "button-green");
    private final Button passButton = casinoButton("Pass", "button-dark");
    private final Button callBluffButton = casinoButton("Call Bluff", "button-red");

    private final List<Button> cardButtons = new ArrayList<>();
    private StackPane riskOverlay;
    private String pendingWinner;
    private List<PlayerSeat> lobbyPlayers = List.of();
    private Map<String, Integer> livesByPlayer = new LinkedHashMap<>();
    private Map<String, Integer> cylinderSlotsByPlayer = new LinkedHashMap<>();
    private String currentPlayer = "-";
    private String target = "-";
    private String winner = "-";
    private String selfName = "Player";
    private boolean inGame = false;
    private boolean yourTurn = false;
    private String visibleScreen = "";
    private int selectedCardIndex = -1;
    private int round = 0;
    private int correctAccusations = 0;
    private int wrongAccusations = 0;
    private int riskPulls = 0;
    private int riskEliminations = 0;

    @Override
    public void start(Stage stage) {
        stage.setTitle("Liar's Table");
        stage.setMinWidth(1080);
        stage.setMinHeight(720);

        root.getStyleClass().add("app-root");
        root.getChildren().setAll(startScreen());

        Scene scene = new Scene(root, 1180, 760);
        scene.getStylesheets().add(getClass().getResource("/casino.css").toExternalForm());
        stage.setScene(scene);
        stage.show();
    }

    private Node startScreen() {
        VBox hero = new VBox(18);
        hero.getStyleClass().add("start-screen");
        hero.setAlignment(Pos.CENTER);
        hero.setPadding(new Insets(44));

        Label title = new Label("Liar's Table");
        title.getStyleClass().add("hero-title");

        Label subtitle = new Label("Bluff. Accuse. Survive.");
        subtitle.getStyleClass().add("hero-subtitle");

        VBox panel = new VBox(14);
        panel.getStyleClass().add("join-panel");
        panel.setMaxWidth(420);

        panel.getChildren().addAll(
                fieldBlock("IP server", ipField),
                fieldBlock("Port", portField),
                fieldBlock("Nickname", nicknameField)
        );

        Button joinButton = casinoButton("Join Table", "button-gold");
        joinButton.getStyleClass().add("join-button");
        joinButton.setMaxWidth(Double.MAX_VALUE);
        joinButton.setOnAction(e -> connect());
        panel.getChildren().add(joinButton);

        statusLabel.getStyleClass().add("status-label");
        panel.getChildren().add(statusLabel);

        hero.getChildren().addAll(title, subtitle, panel);
        return hero;
    }

    private Node lobbyScreen() {
        BorderPane screen = shell("Lobby");
        screen.getStyleClass().add("table-screen");

        VBox header = new VBox(5);
        header.setAlignment(Pos.CENTER);
        Label title = new Label("Seats Around the Table");
        title.getStyleClass().add("section-title");
        detach(lobbyMessageLabel);
        lobbyMessageLabel.getStyleClass().add("main-message");
        header.getChildren().addAll(title, lobbyMessageLabel);
        screen.setTop(header);

        StackPane tableWrap = new StackPane();
        tableWrap.getStyleClass().add("lobby-table-wrap");

        StackPane table = new StackPane(new Label("TABLE"));
        table.getStyleClass().add("lobby-table");
        detach(lobbySeats);
        tableWrap.getChildren().addAll(table, lobbySeats);
        renderLobbySeats();
        screen.setCenter(tableWrap);

        readyButton.setDisable(false);
        readyButton.setText("Ready");
        readyButton.setOnAction(e -> {
            send("READY");
            readyButton.setDisable(true);
            lobbyMessageLabel.setText("Waiting for other players...");
        });

        detach(readyButton);
        VBox bottom = new VBox(12, readyButton);
        bottom.setAlignment(Pos.CENTER);
        bottom.setPadding(new Insets(20, 0, 6, 0));
        screen.setBottom(bottom);
        screen.setRight(logPanel());
        return screen;
    }

    private BorderPane shell(String mode) {
        BorderPane shell = new BorderPane();
        shell.setPadding(new Insets(22));

        Label brand = new Label("Liar's Table");
        brand.getStyleClass().add("brand-small");
        Label modeLabel = new Label(mode);
        modeLabel.getStyleClass().add("mode-label");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        detach(statusLabel);
        HBox top = new HBox(14, brand, modeLabel, spacer, statusLabel);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(0, 0, 18, 0));
        shell.setTop(top);
        return shell;
    }

    private Node gameScreen() {
        BorderPane screen = shell("Game");
        screen.getStyleClass().add("table-screen");

        VBox gameTop = new VBox(6);
        gameTop.setAlignment(Pos.CENTER);
        detach(roundLabel);
        detach(mainMessageLabel);
        roundLabel.getStyleClass().add("round-label");
        mainMessageLabel.getStyleClass().add("main-message");
        gameTop.getChildren().addAll(roundLabel, mainMessageLabel);
        screen.setTop(gameTop);

        BorderPane tableZone = new BorderPane();
        tableZone.setPadding(new Insets(18, 0, 16, 0));

        leftPlayers.getStyleClass().add("player-column");
        rightPlayers.getStyleClass().add("player-column");
        detach(leftPlayers);
        detach(rightPlayers);
        tableZone.setLeft(leftPlayers);
        tableZone.setRight(rightPlayers);

        tableZone.setCenter(casinoTable());
        screen.setCenter(tableZone);
        screen.setBottom(handAndActions());
        screen.setRight(logPanel());

        renderPlayers();
        return screen;
    }

    private Node casinoTable() {
        StackPane table = new StackPane();
        table.getStyleClass().add("casino-table");
        table.setMaxWidth(560);
        table.setMinHeight(360);
        table.setEffect(new DropShadow(38, Color.rgb(0, 0, 0, 0.55)));

        VBox center = new VBox(18);
        center.setAlignment(Pos.CENTER);

        detach(targetLabel);
        detach(targetCardLabel);
        targetLabel.getStyleClass().add("target-caption");
        targetCardLabel.getStyleClass().add("target-card");

        HBox cards = new HBox(22);
        cards.setAlignment(Pos.CENTER);

        StackPane deck = cardBack("DECK");
        playedCardLabel.getStyleClass().add("played-card-label");
        StackPane played = cardBack("");
        detach(playedCardLabel);
        played.getChildren().add(playedCardLabel);

        cards.getChildren().addAll(deck, played);
        detach(currentPlayerLabel);
        currentPlayerLabel.getStyleClass().add("current-player-chip");

        center.getChildren().addAll(targetLabel, targetCardLabel, cards, currentPlayerLabel);
        table.getChildren().add(center);
        return table;
    }

    private Node handAndActions() {
        VBox bottom = new VBox(16);
        bottom.setAlignment(Pos.CENTER);

        detach(handBox);
        handBox.getStyleClass().add("hand-box");
        handBox.setAlignment(Pos.CENTER);

        playButton.setDisable(true);
        passButton.setDisable(true);
        callBluffButton.setDisable(true);

        playButton.setOnAction(e -> playSelectedCard());
        passButton.setOnAction(e -> send("PASS"));
        callBluffButton.setOnAction(e -> {
            send("CALL_BLUFF");
        });

        detach(playButton);
        detach(passButton);
        detach(callBluffButton);
        HBox actions = new HBox(14, playButton, callBluffButton, passButton);
        actions.setAlignment(Pos.CENTER);
        bottom.getChildren().addAll(handBox, actions);
        return bottom;
    }

    private Node logPanel() {
        VBox panel = new VBox(12);
        panel.getStyleClass().add("log-panel");
        panel.setPrefWidth(310);

        Label title = new Label("Event History");
        title.getStyleClass().add("log-title");

        logList.setPadding(new Insets(4));
        detach(logScroll);
        logScroll.setFitToWidth(true);
        logScroll.getStyleClass().add("log-scroll");
        VBox.setVgrow(logScroll, Priority.ALWAYS);
        panel.getChildren().setAll(title, logScroll);
        return panel;
    }

    private Node handCard(String name, int index, boolean enabled) {
        Button button = new Button();
        button.getStyleClass().add("card-button");
        button.setDisable(!enabled);
        button.setGraphic(cardFace(name));
        button.setOnAction(e -> selectCard(index));
        cardButtons.add(button);
        return button;
    }

    private VBox cardFace(String name) {
        Label top = new Label(name);
        Label symbol = new Label(symbolFor(name));
        Label bottom = new Label(name);
        top.getStyleClass().add("card-name-top");
        symbol.getStyleClass().add("card-symbol");
        bottom.getStyleClass().add("card-name-bottom");

        VBox face = new VBox(10, top, symbol, bottom);
        face.setAlignment(Pos.CENTER);
        face.getStyleClass().add("card-face");
        return face;
    }

    private StackPane cardBack(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("card-back-text");
        StackPane back = new StackPane(label);
        back.getStyleClass().add("card-back");
        return back;
    }

    private Node fieldBlock(String labelText, TextField field) {
        VBox box = new VBox(7);
        Label label = new Label(labelText);
        label.getStyleClass().add("field-label");
        field.getStyleClass().add("casino-field");
        box.getChildren().addAll(label, field);
        return box;
    }

    private static Button casinoButton(String text, String styleClass) {
        Button button = new Button(text);
        button.getStyleClass().addAll("casino-button", styleClass);
        return button;
    }

    private void connect() {
        try {
            String ip = ipField.getText().trim();
            int port = Integer.parseInt(portField.getText().trim());
            socket = new Socket(ip, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
            selfName = nicknameField.getText().trim();
            if (selfName.isBlank()) selfName = "Player";
            send("JOIN;" + selfName);
            statusLabel.setText("Connected to " + ip + ":" + port);
            appendLog("Joined the table as " + selfName, "log-win");
            show("lobby", lobbyScreen());
            new Thread(this::listenServer, "server-listener").start();
        } catch (Exception ex) {
            statusLabel.setText("Connection failed: " + ex.getMessage());
        }
    }

    private void listenServer() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                String msg = line;
                Platform.runLater(() -> handleMessage(msg));
            }
        } catch (IOException e) {
            Platform.runLater(() -> {
                statusLabel.setText("Disconnected from server");
                appendLog("Disconnected from server", "log-danger");
            });
        }
    }

    private void handleMessage(String msg) {
        String[] p = Protocol.split(msg);
        switch (p[0]) {
            case "WELCOME" -> appendLog(p.length > 1 ? p[1] : "Welcome", "log-normal");
            case "LOBBY" -> {
                lobbyPlayers = parseLobby(p.length > 1 ? p[1] : "");
                renderLobbySeats();
                if (!inGame && riskOverlay == null && pendingWinner == null
                        && !"lobby".equals(visibleScreen) && !"final".equals(visibleScreen)) {
                    show("lobby", lobbyScreen());
                }
            }
            case "LOG" -> handleLog(p.length > 1 ? p[1] : msg);
            case "ERROR" -> appendLog(p.length > 1 ? p[1] : msg, "log-danger");
            case "RESULT" -> handleResult(p);
            case "RISK" -> handleRisk(p);
            case "GAME_OVER" -> {
                winner = p.length > 1 ? p[1] : "-";
                inGame = false;
                appendLog("Winner: " + winner, "log-win");
                if (riskOverlay != null) {
                    pendingWinner = winner;
                } else {
                    show("final", finalScreen());
                }
            }
            case "STATE" -> updateState(p);
            default -> appendLog(msg, "log-normal");
        }
    }

    private void handleLog(String text) {
        if (text.startsWith("Nuovo round")) {
            appendLog(text, "log-win");
            return;
        }
        if (text.contains("accus") || text.contains("bluff")) {
            appendLog(text, "log-accuse");
            return;
        }
        if (text.contains("ha giocato")) {
            playedCardLabel.setText("PLAYED");
            slide(playedCardLabel);
        }
        appendLog(text, "log-normal");
    }

    private void handleResult(String[] p) {
        String result = joinFrom(p, 1);
        appendLog(result, "log-danger");
        shake(root);

        if (result.contains("Bluff caught")) {
            correctAccusations++;
        } else if (result.contains("Wrong accusation")) {
            wrongAccusations++;
        }
    }

    private void handleRisk(String[] p) {
        if (p.length < 2) return;
        if ("START".equals(p[1])) {
            String player = p.length > 2 ? p[2] : "-";
            String reason = p.length > 3 ? p[3] : "Bluff caught";
            int slotsBefore = parseInt(p.length > 4 ? p[4] : "6", 6);
            boolean canPull = p.length > 5 && Boolean.parseBoolean(p[5]);
            showRiskOverlay(player, reason, slotsBefore, slotsBefore, 0, canPull, false, false, false);
        } else if ("RESULT".equals(p[1])) {
            String player = p.length > 2 ? p[2] : "-";
            String reason = p.length > 3 ? p[3] : "Bluff caught";
            int slotsBefore = parseInt(p.length > 4 ? p[4] : "6", 6);
            int slotsAfter = parseInt(p.length > 5 ? p[5] : String.valueOf(Math.max(1, slotsBefore - 1)), Math.max(1, slotsBefore - 1));
            int pulledChamber = parseInt(p.length > 6 ? p[6] : "0", 0);
            boolean eliminated = p.length > 7 && Boolean.parseBoolean(p[7]);
            String message = eliminated ? "BANG! " + player + " eliminated" : "CLICK... " + player + " survived";
            riskPulls++;
            if (eliminated) riskEliminations++;
            showRiskResultSequence(player, reason, slotsBefore, slotsAfter, pulledChamber, eliminated);
            appendLog(message, eliminated ? "log-danger" : "log-win");
        }
    }

    private void updateState(String[] p) {
        // STATE;round;target;currentPlayer;lives;roulette;hand;isYourTurn
        inGame = true;
        round = parseInt(p.length > 1 ? p[1] : "0", round);
        target = p.length > 2 ? p[2] : "-";
        currentPlayer = p.length > 3 ? p[3] : "-";
        livesByPlayer = parseLives(p.length > 4 ? p[4] : "");
        cylinderSlotsByPlayer = parseLives(p.length > 5 ? p[5] : "");
        String hand = p.length > 6 ? p[6] : "";
        yourTurn = p.length > 7 && Boolean.parseBoolean(p[7]);
        boolean alive = isLocalPlayerAlive();

        roundLabel.setText("Round " + round);
        targetCardLabel.setText(target);
        currentPlayerLabel.setText("Turn: " + currentPlayer);
        mainMessageLabel.setText(!alive ? "Eliminated" : (yourTurn ? "Your turn" : currentPlayer + " is deciding..."));

        renderPlayers();
        setHand(hand.isBlank() ? List.of() : List.of(hand.split(",")), yourTurn && alive);
        callBluffButton.setDisable(!alive);
        passButton.setDisable(!yourTurn || !alive);
        if (!"game".equals(visibleScreen)) show("game", gameScreen());
    }

    private Node finalScreen() {
        VBox screen = new VBox(22);
        screen.getStyleClass().add("final-screen");
        screen.setAlignment(Pos.CENTER);
        screen.setPadding(new Insets(44));

        Label winnerLabel = new Label("WINNER: " + winner);
        winnerLabel.getStyleClass().add("winner-label");
        winnerLabel.setWrapText(true);
        winnerLabel.setMaxWidth(920);
        winnerLabel.setAlignment(Pos.CENTER);

        HBox stats = new HBox(14,
                statBox("Rounds", String.valueOf(round)),
                statBox("Correct Accusations", String.valueOf(correctAccusations)),
                statBox("Wrong Accusations", String.valueOf(wrongAccusations)),
                statBox("Risk Pulls", String.valueOf(riskPulls)),
                statBox("Eliminations", String.valueOf(riskEliminations))
        );
        stats.setAlignment(Pos.CENTER);

        Button playAgain = casinoButton("Play Again", "button-gold");
        Button backLobby = casinoButton("Back to Lobby", "button-dark");
        Button exit = casinoButton("Exit", "button-red");

        playAgain.setOnAction(e -> {
            send("READY");
            show("lobby", lobbyScreen());
        });
        backLobby.setOnAction(e -> show("lobby", lobbyScreen()));
        exit.setOnAction(e -> Platform.exit());

        HBox actions = new HBox(12, playAgain, backLobby, exit);
        actions.setAlignment(Pos.CENTER);
        screen.getChildren().addAll(winnerLabel, stats, actions, logPanel());
        return screen;
    }

    private Node statBox(String label, String value) {
        VBox box = new VBox(6);
        box.getStyleClass().add("stat-box");
        box.setAlignment(Pos.CENTER);
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("stat-value");
        Label labelNode = new Label(label);
        labelNode.getStyleClass().add("stat-label");
        box.getChildren().addAll(valueLabel, labelNode);
        return box;
    }

    private void renderLobbySeats() {
        if (lobbySeats == null) return;
        lobbySeats.getChildren().clear();
        for (int i = 0; i < 4; i++) {
            Node seat = seatNode(i < lobbyPlayers.size() ? lobbyPlayers.get(i) : null);
            AnchorPane.setTopAnchor(seat, switch (i) {
                case 0 -> 8.0;
                case 1, 2 -> 158.0;
                default -> 308.0;
            });
            AnchorPane.setLeftAnchor(seat, switch (i) {
                case 0, 3 -> 235.0;
                case 1 -> 20.0;
                default -> 450.0;
            });
            lobbySeats.getChildren().add(seat);
        }
    }

    private Node seatNode(PlayerSeat player) {
        VBox seat = new VBox(7);
        seat.getStyleClass().add(player == null ? "empty-seat" : "seat-card");
        seat.setAlignment(Pos.CENTER);
        seat.setPrefSize(170, 92);

        Label avatar = new Label(player == null ? "+" : initials(player.name()));
        avatar.getStyleClass().add("avatar");
        Label name = new Label(player == null ? "Empty seat" : player.name());
        name.getStyleClass().add("seat-name");
        Label state = new Label(player == null ? "Waiting" : (player.ready() ? "Ready" : "Waiting"));
        state.getStyleClass().add(player != null && player.ready() ? "ready-state" : "waiting-state");

        seat.getChildren().addAll(avatar, name, state);
        return seat;
    }

    private void renderPlayers() {
        leftPlayers.getChildren().clear();
        rightPlayers.getChildren().clear();

        List<Map.Entry<String, Integer>> entries = new ArrayList<>(livesByPlayer.entrySet());
        int displayIndex = 0;
        for (int i = 0; i < entries.size(); i++) {
            String name = entries.get(i).getKey();
            int lives = entries.get(i).getValue();
            boolean eliminated = lives <= 0;
            Node card = playerPanel(name, cylinderSlotsByPlayer.getOrDefault(name, eliminated ? 0 : 6), eliminated);
            if (displayIndex % 2 == 0) leftPlayers.getChildren().add(card);
            else rightPlayers.getChildren().add(card);
            displayIndex++;
        }
    }

    private Node playerPanel(String name, int cylinderSlots, boolean eliminated) {
        VBox box = new VBox(7);
        box.getStyleClass().add("player-panel");
        if (eliminated) box.getStyleClass().add("eliminated-player");
        if (!eliminated && name.equals(currentPlayer)) box.getStyleClass().add("active-player");
        box.setPrefWidth(190);

        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().add("player-name");
        if (eliminated) nameLabel.getStyleClass().add("eliminated-name");
        Label rouletteLabel = new Label(eliminated ? "Chambers 0 / 6" : "Chambers " + cylinderSlots + " / 6");
        rouletteLabel.getStyleClass().add(eliminated ? "roulette-spent" : (cylinderSlots <= 2 ? "roulette-danger" : "roulette-label"));
        Label state = new Label(eliminated ? "ELIMINATED" : (name.equals(currentPlayer) ? "Turn" : "Waiting"));
        state.getStyleClass().add(eliminated ? "eliminated-badge" : "player-state");

        box.getChildren().addAll(nameLabel, rouletteLabel, state);
        return box;
    }

    private void setHand(List<String> cards, boolean enabled) {
        handBox.getChildren().clear();
        cardButtons.clear();
        selectedCardIndex = -1;
        for (int i = 0; i < cards.size(); i++) {
            handBox.getChildren().add(handCard(cards.get(i), i, enabled));
        }
        playButton.setDisable(true);
        passButton.setDisable(!enabled);
    }

    private void selectCard(int index) {
        if (!yourTurn) return;
        selectedCardIndex = index;
        for (int i = 0; i < cardButtons.size(); i++) {
            cardButtons.get(i).getStyleClass().remove("selected-card");
            if (i == index) cardButtons.get(i).getStyleClass().add("selected-card");
        }
        playButton.setDisable(false);
    }

    private void playSelectedCard() {
        if (selectedCardIndex < 0) return;
        send("PLAY_CARD;" + selectedCardIndex);
        playButton.setDisable(true);
        passButton.setDisable(true);
    }

    private void appendLog(String text, String styleClass) {
        Label entry = new Label(text);
        entry.setWrapText(true);
        entry.getStyleClass().addAll("log-entry", styleClass);
        logList.getChildren().add(entry);
        Platform.runLater(() -> logScroll.setVvalue(1.0));
    }

    private void show(String name, Node node) {
        if (name.equals(visibleScreen) && !root.getChildren().isEmpty() && root.getChildren().get(0) == node) return;
        visibleScreen = name;
        riskOverlay = null;
        node.setOpacity(0);
        root.getChildren().setAll(node);
        FadeTransition fade = new FadeTransition(Duration.millis(260), node);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
    }

    private void showRiskOverlay(String player, String reason, int slotsBefore, int slotsAfter, int pulledChamber, boolean canPull, boolean result, boolean eliminated, boolean autoHide) {
        showRiskOverlay(player, reason, slotsBefore, slotsAfter, pulledChamber, canPull, result, eliminated, autoHide, false);
    }

    private void showRiskOverlay(String player, String reason, int slotsBefore, int slotsAfter, int pulledChamber, boolean canPull, boolean result, boolean eliminated, boolean autoHide, boolean tension) {
        if (riskOverlay != null) root.getChildren().remove(riskOverlay);

        StackPane veil = new StackPane();
        veil.getStyleClass().add("risk-veil");
        veil.setPickOnBounds(true);

        VBox chamber = new VBox(18);
        chamber.getStyleClass().add("risk-chamber");
        chamber.setAlignment(Pos.CENTER);
        chamber.setMaxWidth(600);

        Label title = new Label("RUSSIAN ROULETTE");
        title.getStyleClass().add("risk-title");

        VBox meta = new VBox(6);
        meta.getStyleClass().add("risk-meta");
        meta.setAlignment(Pos.CENTER);
        Label playerLabel = new Label("Player: " + player);
        playerLabel.getStyleClass().add("risk-player");
        Label reasonLabel = new Label("Reason: " + reason);
        reasonLabel.getStyleClass().add("risk-reason");
        meta.getChildren().addAll(playerLabel, reasonLabel);

        Node revolver = revolverGraphic(result && eliminated);
        Node chamberSlots = chamberIndicator(slotsBefore, pulledChamber, result, eliminated);
        Label count = new Label("Cylinder: " + slotsBefore + " chamber" + (slotsBefore == 1 ? "" : "s") + " remaining");
        count.getStyleClass().add("risk-count");
        Label probability = new Label("Risk: 1 / " + slotsBefore);
        probability.getStyleClass().add("risk-probability");

        Node resultMessage = riskResultMessage(player, result, eliminated, tension);
        String detail = result
                ? (eliminated ? "Player removed from the table" : "Cylinder reduced: " + slotsBefore + " -> " + slotsAfter)
                : (tension ? "The room goes quiet..." : "One loaded chamber. No buildup.");
        Label detailLabel = new Label(detail);
        detailLabel.getStyleClass().add("risk-detail");

        Button pull = casinoButton("PULL TRIGGER", result ? "button-dark" : "button-red");
        pull.getStyleClass().add("risk-button");
        pull.setDisable(!canPull || result || tension);
        pull.setOnAction(e -> {
            pull.setDisable(true);
            pull.setText("...");
            showRiskOverlay(player, reason, slotsBefore, slotsAfter, pulledChamber, false, false, false, false, true);
            send("PULL_TRIGGER");
        });

        chamber.getChildren().addAll(title, meta, revolver, chamberSlots, count, probability, resultMessage, detailLabel, pull);
        veil.getChildren().add(chamber);
        riskOverlay = veil;
        root.getChildren().add(veil);

        FadeTransition fade = new FadeTransition(Duration.millis(180), veil);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();

        if (result && eliminated) {
            shake(revolver);
            shake(chamber);
        }

        if (autoHide) {
            PauseTransition pause = new PauseTransition(Duration.seconds(1.8));
            pause.setOnFinished(e -> hideRiskOverlay());
            pause.play();
        }
    }

    private void showRiskResultSequence(String player, String reason, int slotsBefore, int slotsAfter, int pulledChamber, boolean eliminated) {
        showRiskOverlay(player, reason, slotsBefore, slotsAfter, pulledChamber, false, false, false, false, true);
        PauseTransition tensionPause = new PauseTransition(Duration.millis(430));
        tensionPause.setOnFinished(e -> showRiskOverlay(player, reason, slotsBefore, slotsAfter, pulledChamber, false, true, eliminated, true));
        tensionPause.play();
    }

    private static Node riskResultMessage(String player, boolean result, boolean eliminated, boolean tension) {
        if (result && eliminated) {
            VBox box = new VBox(2);
            box.getStyleClass().add("risk-elimination-message");
            box.setAlignment(Pos.CENTER);

            Label bang = new Label("BANG!");
            bang.getStyleClass().add("risk-bang");
            Label name = new Label(player);
            name.getStyleClass().add("risk-eliminated-name");
            Label line = new Label("is eliminated");
            line.getStyleClass().add("risk-eliminated-line");

            box.getChildren().addAll(bang, name, line);
            return box;
        }

        String message = result
                ? "CLICK... " + player + " survived"
                : (tension ? "..." : "Pull the trigger");
        Label messageLabel = new Label(message);
        messageLabel.getStyleClass().add(result ? "risk-result" : (tension ? "risk-tension-message" : "risk-message"));
        return messageLabel;
    }

    private static Node revolverGraphic(boolean fired) {
        Pane art = new Pane();
        art.getStyleClass().add("revolver-art");
        art.setMinSize(270, 108);
        art.setPrefSize(270, 108);
        art.setMaxSize(270, 108);

        Region barrel = new Region();
        barrel.getStyleClass().add("revolver-barrel");
        barrel.setLayoutX(118);
        barrel.setLayoutY(28);

        Region body = new Region();
        body.getStyleClass().add("revolver-body");
        body.setLayoutX(72);
        body.setLayoutY(30);

        Region cylinder = new Region();
        cylinder.getStyleClass().add("revolver-cylinder");
        cylinder.setLayoutX(88);
        cylinder.setLayoutY(20);

        Region grip = new Region();
        grip.getStyleClass().add("revolver-grip");
        grip.setLayoutX(58);
        grip.setLayoutY(58);

        Region trigger = new Region();
        trigger.getStyleClass().add("revolver-trigger");
        trigger.setLayoutX(95);
        trigger.setLayoutY(65);

        art.getChildren().addAll(barrel, body, cylinder, grip, trigger);
        if (fired) {
            Region flash = new Region();
            flash.getStyleClass().add("revolver-flash");
            flash.setLayoutX(220);
            flash.setLayoutY(15);
            flash.setRotate(45);
            art.getChildren().add(flash);
        }
        return art;
    }

    private void hideRiskOverlay() {
        if (riskOverlay != null) {
            root.getChildren().remove(riskOverlay);
            riskOverlay = null;
        }
        if (pendingWinner != null) {
            winner = pendingWinner;
            pendingWinner = null;
            show("final", finalScreen());
        }
    }

    private void detach(Node node) {
        if (node.getParent() instanceof Pane pane) {
            pane.getChildren().remove(node);
        }
    }

    private void slide(Node node) {
        TranslateTransition transition = new TranslateTransition(Duration.millis(240), node);
        transition.setFromX(-26);
        transition.setToX(0);
        transition.play();
    }

    private void shake(Node node) {
        TranslateTransition shake = new TranslateTransition(Duration.millis(55), node);
        shake.setFromX(-7);
        shake.setToX(7);
        shake.setCycleCount(6);
        shake.setAutoReverse(true);
        shake.play();
    }

    private void send(String msg) {
        if (out != null) out.println(msg);
    }

    private boolean isLocalPlayerAlive() {
        return livesByPlayer.getOrDefault(selfName, 1) > 0;
    }

    private static List<PlayerSeat> parseLobby(String names) {
        if (names.isBlank()) return List.of();
        List<PlayerSeat> players = new ArrayList<>();
        for (String raw : names.split(",")) {
            String name = raw.trim();
            boolean ready = name.endsWith("(ready)");
            if (ready) name = name.substring(0, name.length() - "(ready)".length());
            players.add(new PlayerSeat(name, ready));
        }
        return players;
    }

    private static Map<String, Integer> parseLives(String text) {
        Map<String, Integer> lives = new LinkedHashMap<>();
        if (text.isBlank()) return lives;
        for (String item : text.split(",")) {
            String[] parts = item.split(":", 2);
            if (parts.length == 2) lives.put(parts[0], parseInt(parts[1], 0));
        }
        return lives;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String joinFrom(String[] arr, int start) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < arr.length; i++) {
            if (i > start) sb.append("; ");
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    private static String initials(String name) {
        if (name == null || name.isBlank()) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, 1).toUpperCase();
        return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
    }

    private static String symbolFor(String name) {
        return switch (name.toUpperCase()) {
            case "KING" -> "♛";
            case "QUEEN" -> "♕";
            case "JOKER" -> "★";
            default -> "?";
        };
    }

    private static Node chamberIndicator(int cylinderSlots, int pulledChamber, boolean resolved, boolean eliminated) {
        HBox slots = new HBox(10);
        slots.getStyleClass().add("risk-slots");
        slots.setAlignment(Pos.CENTER);
        int visibleSlots = Math.max(1, Math.min(6, cylinderSlots));
        for (int i = 1; i <= visibleSlots; i++) {
            StackPane slot = new StackPane();
            slot.getStyleClass().add("risk-slot");
            if (i == 1) slot.getStyleClass().add("risk-slot-loaded");
            if (resolved && i == pulledChamber) slot.getStyleClass().add(eliminated ? "risk-slot-hit" : "risk-slot-safe");

            Label number = new Label(String.valueOf(i));
            number.getStyleClass().add("risk-slot-number");
            slot.getChildren().add(number);
            slots.getChildren().add(slot);
        }
        return slots;
    }

    private record PlayerSeat(String name, boolean ready) {}

    public static void main(String[] args) {
        launch(args);
    }
}

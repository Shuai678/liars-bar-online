package com.liarstable.server;

public enum Card {
    KING("KING"), QUEEN("QUEEN"), JOKER("JOKER");

    private final String label;
    Card(String label) { this.label = label; }
    public String label() { return label; }
}

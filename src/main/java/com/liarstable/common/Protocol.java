package com.liarstable.common;

public final class Protocol {
    private Protocol() {}

    public static final int DEFAULT_PORT = 5000;

    public static int port() {
        return Integer.getInteger("liarstable.port", DEFAULT_PORT);
    }

    public static String[] split(String message) {
        return message == null ? new String[0] : message.split(";", -1);
    }
}

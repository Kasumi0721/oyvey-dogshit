package me.alpha432.oyvey.event.impl.entity;


import me.alpha432.oyvey.event.Event;

public class ClientTickEvent extends Event {
    private static final ClientTickEvent instance = new ClientTickEvent();

    private ClientTickEvent() {
    }

    public static ClientTickEvent get(Event.Stage stage) {
        instance.stage = stage;
        return instance;
    }
}
package me.alpha432.oyvey.event;

public class Event {
    private boolean cancelled;
    public Event.Stage stage;
    public boolean isCancelled() {
        return cancelled;
    }

    public void cancel() {
        cancelled = true;
    }
    public static enum Stage {
        Pre,
        Post;
    }
    public boolean isPost() {
        return this.stage == Event.Stage.Post;
    }
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
    public boolean isPre() {
        return this.stage == Event.Stage.Pre;
    }
}

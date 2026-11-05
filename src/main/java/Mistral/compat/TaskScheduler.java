package Mistral.compat;

public interface TaskScheduler {
    void sync(Runnable action);
    Task later(Runnable action, long ticks);
    interface Task { void cancel(); }
}

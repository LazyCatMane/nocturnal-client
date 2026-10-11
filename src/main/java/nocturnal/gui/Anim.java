package nocturnal.gui;

/** A value that glides toward a target each frame, at the same speed whatever the frame rate. */
public final class Anim {
    private final float speed;
    private float value;
    private long lastNanos = System.nanoTime();

    /** speed: roughly how many times per second the remaining distance shrinks by e. 12 feels snappy. */
    public Anim(float speed, float start) {
        this.speed = speed;
        this.value = start;
    }

    public float update(float target) {
        long now = System.nanoTime();
        float seconds = Math.min((now - lastNanos) / 1e9f, 0.1f);
        lastNanos = now;
        value += (target - value) * (1 - (float) Math.exp(-speed * seconds));
        if (Math.abs(target - value) < 0.001f) value = target;
        return value;
    }

    public float get() {
        return value;
    }

    public void set(float v) {
        value = v;
        lastNanos = System.nanoTime();
    }
}

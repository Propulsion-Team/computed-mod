package net.minecraft.network.chat;

/** Minimal test signature used because the plain JUnit source set does not inherit ModDev's game classpath. */
public final class MutableComponent implements Component {
    private final String text;

    public MutableComponent(String text) {
        this.text = text;
    }

    @Override
    public String getString() {
        return text;
    }
}

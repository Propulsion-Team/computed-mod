package net.minecraft.network.chat;

/** Minimal test signature used because the plain JUnit source set does not inherit ModDev's game classpath. */
public interface Component {
    static MutableComponent literal(String text) {
        return new MutableComponent(text);
    }

    String getString();
}

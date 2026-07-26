package net.neoforged.api.distmarker;

public enum Dist {
    CLIENT,
    DEDICATED_SERVER;

    public boolean isDedicatedServer() {
        return this == DEDICATED_SERVER;
    }
}

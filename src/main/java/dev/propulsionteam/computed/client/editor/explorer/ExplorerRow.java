package dev.propulsionteam.computed.client.editor.explorer;

public record ExplorerRow(
        String stablePath,
        String label,
        int depth,
        boolean folder,
        boolean expanded,
        ExplorerNode node) {}

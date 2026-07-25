package dev.propulsionteam.computed.client.editor.explorer;

import java.util.List;

public record ExplorerNode(
        String id,
        String title,
        Ownership ownership,
        List<String> folderPath,
        boolean available,
        String unavailableReason) {

    public ExplorerNode {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Explorer node id is required");
        }
        title = title == null || title.isBlank() ? id : title;
        ownership = ownership == null ? Ownership.BUNDLED : ownership;
        folderPath = folderPath == null ? List.of() : List.copyOf(folderPath);
        unavailableReason = unavailableReason == null ? "" : unavailableReason;
    }

    public enum Ownership {
        BUNDLED,
        INTEGRATION,
        USER
    }
}

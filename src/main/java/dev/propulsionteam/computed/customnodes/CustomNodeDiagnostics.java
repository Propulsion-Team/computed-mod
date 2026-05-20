package dev.propulsionteam.computed.customnodes;

import java.util.ArrayList;
import java.util.List;

public final class CustomNodeDiagnostics {
    private final List<String> warnings = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();

    public void warn(String message) {
        warnings.add(message);
    }

    public void error(String message) {
        errors.add(message);
    }

    public List<String> warnings() {
        return List.copyOf(warnings);
    }

    public List<String> errors() {
        return List.copyOf(errors);
    }
}

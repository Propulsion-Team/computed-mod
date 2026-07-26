package dev.propulsionteam.computed.lua.endpoint;

import java.util.regex.Pattern;

final class EndpointIds {
    private static final Pattern MEMBER = Pattern.compile("[a-z][a-z0-9_.-]{0,63}");
    private static final Pattern NAMESPACED = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private EndpointIds() {}

    static String requireMember(String id, String kind) {
        if (id == null || !MEMBER.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid " + kind + " id: " + id);
        }
        return id;
    }

    static String requireNamespaced(String id, String kind) {
        if (id == null || id.length() > 128 || !NAMESPACED.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid " + kind + " id: " + id);
        }
        return id;
    }
}

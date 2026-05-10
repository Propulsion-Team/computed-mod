package dev.devce.websnodelib.api;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/**
 * Saved function definitions for a computer (name + inner graph body per id). Serialized as a list tag
 * under {@code ComputerFunctions} alongside the root {@code ComputerGraph}.
 */
public final class FunctionDefinitionStore {
    public record Definition(UUID id, String name, CompoundTag body) {}

    private final Map<UUID, Definition> definitions = new LinkedHashMap<>();

    public void clear() {
        definitions.clear();
    }

    public boolean isEmpty() {
        return definitions.isEmpty();
    }

    public int size() {
        return definitions.size();
    }

    public Definition get(UUID id) {
        return definitions.get(id);
    }

    public CompoundTag getBody(UUID id) {
        Definition d = definitions.get(id);
        return d != null ? d.body() : null;
    }

    public Collection<Definition> definitionsInOrder() {
        return definitions.values();
    }

    public void put(UUID id, String name, CompoundTag body) {
        definitions.put(id, new Definition(id, name, body.copy()));
    }

    /** Registers a new definition and returns its id. */
    public UUID addNew(String name, CompoundTag bodyTag) {
        UUID id = UUID.randomUUID();
        put(id, name, bodyTag);
        return id;
    }

    public void load(ListTag list) {
        definitions.clear();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            UUID id = c.getUUID("Id");
            String name = c.getString("Name");
            CompoundTag body = c.getCompound("Body");
            definitions.put(id, new Definition(id, name, body));
        }
    }

    public ListTag saveList() {
        ListTag list = new ListTag();
        for (Definition d : definitions.values()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("Id", d.id());
            c.putString("Name", d.name());
            c.put("Body", d.body().copy());
            list.add(c);
        }
        return list;
    }

    /**
     * Copies inner graphs from every {@link FunctionCardNode} on the root graph into this store (creates or
     * updates bodies; keeps existing names when possible).
     */
    public void syncBodiesFromGraph(WGraph root) {
        for (WNode n : root.getNodes()) {
            if (n instanceof FunctionCardNode card) {
                UUID id = card.getFunctionId();
                Definition existing = definitions.get(id);
                String name = existing != null ? existing.name() : "Function";
                put(id, name, card.getInnerGraph().save());
            }
        }
    }
}

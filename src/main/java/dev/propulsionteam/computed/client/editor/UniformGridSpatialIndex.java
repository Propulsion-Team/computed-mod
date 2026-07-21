package dev.propulsionteam.computed.client.editor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A graph-space uniform grid for inexpensive viewport culling and hit-test broad phases.
 *
 * <p>Entries may occupy multiple cells. Queries de-duplicate entries and then apply an exact bounds
 * check, so callers never receive a false positive caused solely by sharing a cell. The index is
 * intended for a single editor thread.
 *
 * @param <K> stable entry key
 * @param <V> entry value
 */
public final class UniformGridSpatialIndex<K, V> {
    public static final int DEFAULT_MAX_CELLS_PER_ENTRY = 65_536;

    private final double cellSize;
    private final int maxCellsPerEntry;
    private final Map<K, StoredEntry<V>> entries = new LinkedHashMap<>();
    private final Map<Cell, LinkedHashSet<K>> cells = new HashMap<>();

    public UniformGridSpatialIndex(double cellSize) {
        this(cellSize, DEFAULT_MAX_CELLS_PER_ENTRY);
    }

    public UniformGridSpatialIndex(double cellSize, int maxCellsPerEntry) {
        if (!Double.isFinite(cellSize) || cellSize <= 0.0) {
            throw new IllegalArgumentException("Cell size must be finite and greater than zero");
        }
        if (maxCellsPerEntry < 1) {
            throw new IllegalArgumentException("Maximum cells per entry must be at least one");
        }
        this.cellSize = cellSize;
        this.maxCellsPerEntry = maxCellsPerEntry;
    }

    public void insert(K key, V value, GraphRect bounds) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(bounds, "bounds");
        if (entries.containsKey(key)) {
            throw new IllegalArgumentException("Spatial entry already exists: " + key);
        }

        Set<Cell> occupiedCells = cellsForEntry(bounds);
        entries.put(key, new StoredEntry<>(value, bounds, occupiedCells));
        addToCells(key, occupiedCells);
    }

    /** Replaces an existing value and bounds, returning {@code false} when the key is absent. */
    public boolean update(K key, V value, GraphRect bounds) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(bounds, "bounds");
        StoredEntry<V> previous = entries.get(key);
        if (previous == null) {
            return false;
        }

        Set<Cell> occupiedCells = cellsForEntry(bounds);
        for (Cell cell : previous.cells()) {
            if (!occupiedCells.contains(cell)) {
                removeFromCell(key, cell);
            }
        }
        for (Cell cell : occupiedCells) {
            if (!previous.cells().contains(cell)) {
                cells.computeIfAbsent(cell, ignored -> new LinkedHashSet<>()).add(key);
            }
        }
        entries.put(key, new StoredEntry<>(value, bounds, occupiedCells));
        return true;
    }

    /** Updates only the bounds of an existing entry. */
    public boolean updateBounds(K key, GraphRect bounds) {
        Objects.requireNonNull(key, "key");
        StoredEntry<V> previous = entries.get(key);
        return previous != null && update(key, previous.value(), bounds);
    }

    public Optional<SpatialEntry<K, V>> remove(K key) {
        Objects.requireNonNull(key, "key");
        StoredEntry<V> removed = entries.remove(key);
        if (removed == null) {
            return Optional.empty();
        }
        for (Cell cell : removed.cells()) {
            removeFromCell(key, cell);
        }
        return Optional.of(new SpatialEntry<>(key, removed.value(), removed.bounds()));
    }

    /** Returns entries whose exact bounds contain the supplied point. */
    public List<SpatialEntry<K, V>> query(GraphPoint point) {
        Objects.requireNonNull(point, "point");
        Cell cell = new Cell(cellCoordinate(point.x()), cellCoordinate(point.y()));
        Collection<K> candidates = cells.getOrDefault(cell, new LinkedHashSet<>());
        return matchingEntries(candidates, bounds -> bounds.contains(point));
    }

    /** Returns entries whose exact bounds intersect the supplied rectangle. */
    public List<SpatialEntry<K, V>> query(GraphRect area) {
        Objects.requireNonNull(area, "area");
        CellRange range = cellRange(area);
        if (range.cellCount() > maxCellsPerEntry) {
            return matchingEntries(entries.keySet(), bounds -> bounds.intersects(area));
        }

        LinkedHashSet<K> candidates = new LinkedHashSet<>();
        for (int x = range.minX(); x <= range.maxX(); x++) {
            for (int y = range.minY(); y <= range.maxY(); y++) {
                Set<K> cellEntries = cells.get(new Cell(x, y));
                if (cellEntries != null) {
                    candidates.addAll(cellEntries);
                }
                if (y == Integer.MAX_VALUE) {
                    break;
                }
            }
            if (x == Integer.MAX_VALUE) {
                break;
            }
        }
        return matchingEntries(candidates, bounds -> bounds.intersects(area));
    }

    public Optional<SpatialEntry<K, V>> get(K key) {
        StoredEntry<V> entry = entries.get(key);
        return entry == null
                ? Optional.empty()
                : Optional.of(new SpatialEntry<>(key, entry.value(), entry.bounds()));
    }

    public List<SpatialEntry<K, V>> entries() {
        List<SpatialEntry<K, V>> result = new ArrayList<>(entries.size());
        entries.forEach((key, entry) -> result.add(new SpatialEntry<>(key, entry.value(), entry.bounds())));
        return List.copyOf(result);
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public double cellSize() {
        return cellSize;
    }

    public void clear() {
        entries.clear();
        cells.clear();
    }

    private List<SpatialEntry<K, V>> matchingEntries(Collection<K> candidates, BoundsPredicate predicate) {
        List<SpatialEntry<K, V>> result = new ArrayList<>();
        for (K key : candidates) {
            StoredEntry<V> entry = entries.get(key);
            if (entry != null && predicate.test(entry.bounds())) {
                result.add(new SpatialEntry<>(key, entry.value(), entry.bounds()));
            }
        }
        return List.copyOf(result);
    }

    private Set<Cell> cellsForEntry(GraphRect bounds) {
        CellRange range = cellRange(bounds);
        if (range.cellCount() > maxCellsPerEntry) {
            throw new IllegalArgumentException(
                    "Spatial entry covers " + range.cellCount() + " cells; maximum is " + maxCellsPerEntry);
        }
        LinkedHashSet<Cell> result = new LinkedHashSet<>((int) range.cellCount());
        for (int x = range.minX(); x <= range.maxX(); x++) {
            for (int y = range.minY(); y <= range.maxY(); y++) {
                result.add(new Cell(x, y));
                if (y == Integer.MAX_VALUE) {
                    break;
                }
            }
            if (x == Integer.MAX_VALUE) {
                break;
            }
        }
        return Collections.unmodifiableSet(result);
    }

    private CellRange cellRange(GraphRect bounds) {
        return new CellRange(
                cellCoordinate(bounds.minX()),
                cellCoordinate(bounds.minY()),
                cellCoordinate(bounds.maxX()),
                cellCoordinate(bounds.maxY()));
    }

    private int cellCoordinate(double coordinate) {
        double scaled = Math.floor(coordinate / cellSize);
        if (scaled < Integer.MIN_VALUE || scaled > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Graph coordinate is outside the spatial index range: " + coordinate);
        }
        return (int) scaled;
    }

    private void addToCells(K key, Set<Cell> occupiedCells) {
        for (Cell cell : occupiedCells) {
            cells.computeIfAbsent(cell, ignored -> new LinkedHashSet<>()).add(key);
        }
    }

    private void removeFromCell(K key, Cell cell) {
        LinkedHashSet<K> cellEntries = cells.get(cell);
        if (cellEntries == null) {
            return;
        }
        cellEntries.remove(key);
        if (cellEntries.isEmpty()) {
            cells.remove(cell);
        }
    }

    public record SpatialEntry<K, V>(K key, V value, GraphRect bounds) {
        public SpatialEntry {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(bounds, "bounds");
        }
    }

    private interface BoundsPredicate {
        boolean test(GraphRect bounds);
    }

    private record StoredEntry<V>(V value, GraphRect bounds, Set<Cell> cells) {}

    private record Cell(int x, int y) {}

    private record CellRange(int minX, int minY, int maxX, int maxY) {
        long cellCount() {
            long width = (long) maxX - minX + 1L;
            long height = (long) maxY - minY + 1L;
            if (width > Long.MAX_VALUE / height) {
                return Long.MAX_VALUE;
            }
            return width * height;
        }
    }
}

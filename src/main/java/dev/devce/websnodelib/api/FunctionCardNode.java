package dev.devce.websnodelib.api;

import dev.devce.websnodelib.api.elements.WIconStrip;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * A placeable function card: inner graph is edited in-place; {@link #getFunctionId()} keys
 * {@link FunctionDefinitionStore} for persistent copies on the computer.
 */
public class FunctionCardNode extends WNode {

    public static final ResourceLocation TYPE_FUNCTION_CARD =
            ResourceLocation.fromNamespaceAndPath("websnodelib", "function_card");

    private static final int MAX_EVAL_DEPTH = 48;
    private static final ThreadLocal<Integer> EVAL_DEPTH = ThreadLocal.withInitial(() -> 0);

    private static final ResourceLocation ICON_UI_CLICK =
            ResourceLocation.fromNamespaceAndPath("computed", "textures/ui/icons/click.png");

    private final WGraph innerGraph = new WGraph();
    private UUID functionId = UUID.randomUUID();

    public FunctionCardNode(int x, int y) {
        this(x, y, UUID.randomUUID());
    }

    public FunctionCardNode(int x, int y, UUID functionId) {
        super(TYPE_FUNCTION_CARD, "Function", x, y);
        this.functionId = functionId;
        addElement(new WIconStrip(
                List.of(UiKeyTextures.key("alt"), ICON_UI_CLICK), ": open", 0xFFCCCCCC, 12));
        innerGraph.updateTopology();
        setEvaluator(this::evaluateInner);
        syncPinsFromInner(null);
    }

    public static CompoundTag newInnerTemplateTag() {
        WGraph g = new WGraph();
        WNode s = NodeRegistry.createNode(FunctionStartNode.TYPE_FN_START, -160, 0);
        WNode e = NodeRegistry.createNode(FunctionEndNode.TYPE_FN_END, 160, 0);
        if (s != null) {
            g.addNode(s);
        }
        if (e != null) {
            g.addNode(e);
        }
        g.updateTopology();
        return g.save();
    }

    public static FunctionCardNode createPlaced(int x, int y, UUID functionId, FunctionDefinitionStore store) {
        FunctionCardNode card = new FunctionCardNode(x, y, functionId);
        CompoundTag body = store.getBody(functionId);
        if (body != null) {
            card.getInnerGraph().load(body.copy());
        } else {
            card.getInnerGraph().load(newInnerTemplateTag());
        }
        card.syncPinsFromInner(store);
        return card;
    }

    /**
     * After loading a root graph, refreshes each function card from the library store without clobbering
     * in-graph state: the authoritative body is the {@code inner} tag on each card inside {@code ComputerGraph}.
     * The store is only used to fill a missing/broken inner (old saves) or to refresh outer pins / titles.
     */
    public static void applyLibraryToInnerGraphs(WGraph root, FunctionDefinitionStore store) {
        if (store == null) {
            return;
        }
        for (WNode n : root.getNodes()) {
            if (n instanceof FunctionCardNode c) {
                if (!functionInnerLooksUsable(c.getInnerGraph())) {
                    CompoundTag body = store.getBody(c.getFunctionId());
                    if (body != null) {
                        c.getInnerGraph().load(body.copy());
                    }
                }
                c.syncPinsFromInner(store);
            }
        }
    }

    private static boolean functionInnerLooksUsable(WGraph g) {
        return findStart(g) != null && findEnd(g) != null;
    }

    public UUID getFunctionId() {
        return functionId;
    }

    public WGraph getInnerGraph() {
        return innerGraph;
    }

    /**
     * Updates outer pins from Start/End nodes. When {@code store} is non-null, the card title is set to the
     * library function name.
     */
    public void syncPinsFromInner(FunctionDefinitionStore store) {
        getInputs().clear();
        getOutputs().clear();
        FunctionStartNode start = findStart(innerGraph);
        if (start != null) {
            start.syncPinsFromUiFields();
            for (WPin p : start.getOutputs()) {
                addInput(p.getName(), p.getColor());
            }
        }
        FunctionEndNode end = findEnd(innerGraph);
        if (end != null) {
            end.syncPinsFromUiFields();
        }
        if (end == null) {
            addOutput("Out", 0xFFFFAA66);
            applyTitleFromStore(store);
            updateLayout();
            return;
        }
        if (end.isEmptyReturn()) {
            applyTitleFromStore(store);
            updateLayout();
            return;
        }
        for (WPin p : end.getInputs()) {
            addOutput(p.getName(), p.getColor());
        }
        applyTitleFromStore(store);
        updateLayout();
    }

    /** @see #syncPinsFromInner(FunctionDefinitionStore) */
    public void syncPinsFromInner() {
        syncPinsFromInner(null);
    }

    private void applyTitleFromStore(FunctionDefinitionStore store) {
        if (store == null) {
            return;
        }
        FunctionDefinitionStore.Definition def = store.get(functionId);
        if (def != null && def.name() != null && !def.name().isEmpty()) {
            setTitle(def.name());
        }
    }

    private static FunctionStartNode findStart(WGraph g) {
        for (WNode n : g.getNodes()) {
            if (n instanceof FunctionStartNode s) {
                return s;
            }
        }
        return null;
    }

    private static FunctionEndNode findEnd(WGraph g) {
        for (WNode n : g.getNodes()) {
            if (n instanceof FunctionEndNode e) {
                return e;
            }
        }
        return null;
    }

    private void evaluateInner(WNode self) {
        int d = EVAL_DEPTH.get();
        if (d >= MAX_EVAL_DEPTH) {
            return;
        }
        EVAL_DEPTH.set(d + 1);
        try {
            FunctionStartNode start = findStart(innerGraph);
            FunctionEndNode end = findEnd(innerGraph);
            if (start != null) {
                int nIn = Math.min(self.getInputs().size(), start.getOutputs().size());
                for (int i = 0; i < nIn; i++) {
                    double v = self.getInputs().get(i).getValue();
                    start.getOutputs().get(i).setValue(v);
                }
            }
            innerGraph.propagateAndEvaluate();
            end = findEnd(innerGraph);
            if (end != null && !end.isEmptyReturn()) {
                int n = Math.min(self.getOutputs().size(), end.getInputs().size());
                for (int i = 0; i < n; i++) {
                    double v = end.getInputs().get(i).getValue();
                    self.getOutputs().get(i).setValue(v);
                }
            }
        } finally {
            EVAL_DEPTH.set(d);
        }
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = super.save();
        tag.putUUID("functionId", functionId);
        tag.put("inner", innerGraph.save());
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.hasUUID("functionId")) {
            functionId = tag.getUUID("functionId");
        }
        if (tag.contains("inner")) {
            innerGraph.load(tag.getCompound("inner"));
        }
        syncPinsFromInner(null);
    }

    public static void register() {
        NodeRegistry.register(TYPE_FUNCTION_CARD, FunctionCardNode::new);
        NodeMenuRegistry.hideFromAddMenu(TYPE_FUNCTION_CARD);
    }
}

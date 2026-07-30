package dev.propulsionteam.computed.client.editor.lua;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.propulsionteam.computed.client.editor.preview.LuaLivePreview;
import org.junit.jupiter.api.Test;

class LuaEditorSessionTest {
    @Test
    void debouncesCompilationAndKeepsTheLastValidPreviewStaleOnErrors() {
        LuaEditorSession editor = new LuaEditorSession();
        String valid = """
                local node = computed.node(1, "example:preview", "Preview")
                node:output("value", "number")
                node:on_run(function(ctx)
                    ctx:output("value", 7)
                end)
                return node
                """;
        editor.sourceChanged(valid, 1000);

        assertFalse(editor.update(1249));
        assertTrue(editor.update(1250));
        assertNotNull(editor.snapshot().currentDefinition());
        assertFalse(editor.snapshot().stalePreview());

        LuaLivePreview preview = new LuaLivePreview(valid);
        assertTrue(preview.run().outputs().containsKey("value"));
        assertTrue(preview.layout().width() > 0);

        editor.sourceChanged("local =", 2000);
        assertTrue(editor.update(2250));
        assertNull(editor.snapshot().currentDefinition());
        assertNotNull(editor.snapshot().lastValidDefinition());
        assertTrue(editor.snapshot().stalePreview());
        assertFalse(editor.snapshot().diagnostics().isEmpty());

        editor.sourceChanged("return { value = 0.1dd }", 3000);
        assertTrue(editor.update(3250));
        assertNull(editor.snapshot().currentDefinition());
        assertNotNull(editor.snapshot().lastValidDefinition());
        assertFalse(editor.snapshot().diagnostics().isEmpty());
    }
}

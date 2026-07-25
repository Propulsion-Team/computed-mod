package dev.propulsionteam.computed.integration.computercraft;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ComputerCraftOptionalLoadingTest {
    @Test
    void bootstrapBytecodeDoesNotResolveComputerCraftApiTypes() throws Exception {
        String resource = '/' + ComputerCraftBootstrap.class.getName().replace('.', '/') + ".class";
        try (InputStream stream = ComputerCraftBootstrap.class.getResourceAsStream(resource)) {
            byte[] bytecode = stream.readAllBytes();
            String constants = new String(bytecode, StandardCharsets.ISO_8859_1);
            assertFalse(constants.contains("dan200/computercraft"));
        }
    }
}

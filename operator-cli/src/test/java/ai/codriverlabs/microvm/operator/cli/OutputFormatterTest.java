package ai.codriverlabs.microvm.operator.cli;

import ai.codriverlabs.microvm.operator.cli.output.TableFormatter;
import ai.codriverlabs.microvm.operator.core.enums.MicroVMState;
import ai.codriverlabs.microvm.operator.core.enums.Runtime;
import ai.codriverlabs.microvm.operator.core.model.MicroVMSpec;
import ai.codriverlabs.microvm.operator.core.model.MicroVMStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OutputFormatterTest {

    private final TableFormatter formatter = new TableFormatter();

    @Test
    void emptyListShowsHeadersOnly() {
        String output = formatter.formatMicroVMList(List.of());
        assertTrue(output.contains("NAME"));
        assertTrue(output.contains("STATE"));
        assertTrue(output.contains("VM-ID"));
        assertTrue(output.contains("RUNTIME"));
        assertTrue(output.contains("MEMORY"));
        assertTrue(output.contains("AGE"));
        // Should only have header line(s), no data rows
        String[] lines = output.strip().split("\n");
        assertTrue(lines.length <= 2, "Empty list should have at most 2 lines (header + separator)");
    }

    @Test
    void singleResourceFormatsCorrectly() {
        MicroVMSpec spec = new MicroVMSpec();
        spec.setRuntime(Runtime.JAVA21);
        spec.setMemoryMB(512);
        spec.setVcpus(2);

        MicroVMStatus status = new MicroVMStatus();
        status.setState(MicroVMState.RUNNING);
        status.setVmId("vm-abc123");

        String output = formatter.formatMicroVMRow("my-vm", spec, status, "5m");
        assertTrue(output.contains("my-vm"));
        assertTrue(output.contains("Running"));
        assertTrue(output.contains("vm-abc123"));
        assertTrue(output.contains("java21"));
        assertTrue(output.contains("512"));
    }

    @Test
    void nullStatusFieldsHandledGracefully() {
        MicroVMSpec spec = new MicroVMSpec();
        spec.setRuntime(Runtime.NODEJS20);
        spec.setMemoryMB(256);

        MicroVMStatus status = new MicroVMStatus();
        status.setState(MicroVMState.PENDING);
        // vmId and ipAddress are null

        String output = formatter.formatMicroVMRow("pending-vm", spec, status, "10s");
        assertTrue(output.contains("pending-vm"));
        assertTrue(output.contains("Pending"));
        assertTrue(output.contains("nodejs20"));
        // Should not throw on null vmId
        assertFalse(output.contains("null"));
    }
}

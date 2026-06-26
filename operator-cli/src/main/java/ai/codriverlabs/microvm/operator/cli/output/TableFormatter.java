package ai.codriverlabs.microvm.operator.cli.output;

import ai.codriverlabs.microvm.operator.core.model.MicroVMSpec;
import ai.codriverlabs.microvm.operator.core.model.MicroVMStatus;

import java.util.List;

public class TableFormatter {

    private static final String HEADER_FORMAT = "%-20s %-12s %-15s %-12s %-8s %-6s%n";
    private static final String ROW_FORMAT = "%-20s %-12s %-15s %-12s %-8s %-6s%n";

    public String formatMicroVMList(List<MicroVMRow> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(HEADER_FORMAT, "NAME", "STATE", "VM-ID", "RUNTIME", "MEMORY", "AGE"));
        for (MicroVMRow row : rows) {
            sb.append(String.format(ROW_FORMAT,
                truncate(row.name(), 20),
                truncate(row.state(), 12),
                truncate(row.vmId() != null ? row.vmId() : "<none>", 15),
                truncate(row.runtime(), 12),
                row.memory(),
                row.age()));
        }
        return sb.toString();
    }

    public String formatMicroVMRow(String name, MicroVMSpec spec, MicroVMStatus status, String age) {
        return String.format(ROW_FORMAT,
            truncate(name, 20),
            truncate(status.getState() != null ? status.getState().getValue() : "Unknown", 12),
            truncate(status.getVmId() != null ? status.getVmId() : "<none>", 15),
            truncate(spec.getRuntime() != null ? spec.getRuntime().getValue() : "unknown", 12),
            spec.getMemoryMB() != null ? String.valueOf(spec.getMemoryMB()) : "0",
            age);
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "<none>";
        return s.length() > maxLen ? s.substring(0, maxLen - 1) + "\u2026" : s;
    }

    public record MicroVMRow(String name, String state, String vmId, String runtime, String memory, String age) {}
}

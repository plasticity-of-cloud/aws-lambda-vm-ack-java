package com.amazonaws.lambda.operator.cli;

import com.amazonaws.lambda.operator.cli.output.TableFormatter;
import com.amazonaws.lambda.operator.core.enums.MicroVMState;
import com.amazonaws.lambda.operator.core.enums.Runtime;
import com.amazonaws.lambda.operator.core.model.MicroVMSpec;
import com.amazonaws.lambda.operator.core.model.MicroVMStatus;
import net.jqwik.api.*;

import java.util.List;

/**
 * Feature: lambda-vm-ack-operator, Property 9: CLI Output Format Consistency
 * Validates: Requirements 7.2
 */
class CliOutputPropertyTest {

    private final TableFormatter formatter = new TableFormatter();

    @Property(tries = 100)
    void listOutputContainsCorrectColumns(
            @ForAll("resourceNames") String name,
            @ForAll("runtimes") Runtime runtime,
            @ForAll("states") MicroVMState state,
            @ForAll("vmIds") String vmId,
            @ForAll("memories") int memoryMB) {

        MicroVMSpec spec = new MicroVMSpec();
        spec.setRuntime(runtime);
        spec.setMemoryMB(memoryMB);
        spec.setVcpus(2);

        MicroVMStatus status = new MicroVMStatus();
        status.setState(state);
        status.setVmId(vmId);

        String output = formatter.formatMicroVMRow(name, spec, status, "5m");

        // Output should contain all required data (or truncated if name is too long)
        assert output.contains(name) || name.length() > 19 :
            "Output should contain resource name '" + name + "'";
        assert output.contains(state.getValue()) || state.getValue().length() > 11 :
            "Output should contain state '" + state.getValue() + "'";
        assert output.contains(runtime.getValue()) || runtime.getValue().length() > 11 :
            "Output should contain runtime '" + runtime.getValue() + "'";
        assert output.contains(String.valueOf(memoryMB)) :
            "Output should contain memory '" + memoryMB + "'";
    }

    @Property(tries = 100)
    void emptyListAlwaysShowsHeaders(@ForAll @net.jqwik.api.constraints.IntRange(min = 0, max = 0) int ignored) {
        String output = formatter.formatMicroVMList(List.of());
        assert output.contains("NAME") : "Header should contain NAME";
        assert output.contains("STATE") : "Header should contain STATE";
        assert output.contains("VM-ID") : "Header should contain VM-ID";
        assert output.contains("RUNTIME") : "Header should contain RUNTIME";
        assert output.contains("MEMORY") : "Header should contain MEMORY";
        assert output.contains("AGE") : "Header should contain AGE";
    }

    @Property(tries = 50)
    void listOutputContainsAllRows(@ForAll("rowCounts") int rowCount) {
        List<TableFormatter.MicroVMRow> rows = new java.util.ArrayList<>();
        for (int i = 0; i < rowCount; i++) {
            rows.add(new TableFormatter.MicroVMRow(
                "vm-" + i, "Running", "vm-id-" + i, "java21", "512", "5m"
            ));
        }
        String output = formatter.formatMicroVMList(rows);
        // Header line + all data rows
        String[] lines = output.strip().split("\n");
        assert lines.length == rowCount + 1 :
            "Expected " + (rowCount + 1) + " lines (header + " + rowCount + " rows) but got " + lines.length;
    }

    @Provide
    Arbitrary<String> resourceNames() {
        return Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(30);
    }

    @Provide
    Arbitrary<Runtime> runtimes() {
        return Arbitraries.of(Runtime.values());
    }

    @Provide
    Arbitrary<MicroVMState> states() {
        return Arbitraries.of(MicroVMState.values());
    }

    @Provide
    Arbitrary<String> vmIds() {
        return Arbitraries.strings().withCharRange('a', 'z').ofMinLength(5).ofMaxLength(15)
                .map(s -> "vm-" + s);
    }

    @Provide
    Arbitrary<Integer> memories() {
        return Arbitraries.integers().between(2, 160).map(i -> i * 64);
    }

    @Provide
    Arbitrary<Integer> rowCounts() {
        return Arbitraries.integers().between(1, 10);
    }
}

package ai.codriverlabs.microvm.operator.cli.commands;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import picocli.CommandLine.Command;

/**
 * Root command for the kubectl-microvm CLI plugin.
 * Provides subcommands for managing MicroVM resources.
 */
@TopCommand
@Command(
    name = "kubectl-microvm",
    description = "CLI plugin for managing AWS Lambda MicroVMs on Kubernetes",
    mixinStandardHelpOptions = true,
    version = "1.0.0-SNAPSHOT",
    subcommands = {
        CreateCommand.class,
        ListCommand.class,
        DescribeCommand.class,
        DeleteCommand.class,
        PauseCommand.class,
        ResumeCommand.class,
        StopCommand.class,
        StartCommand.class,
        LogsCommand.class,
        ExecCommand.class,
        PoolCommand.class,
        ImageCommand.class,
        TokenCommand.class,
        NetworkCommand.class
    }
)
public class MicroVMCommand {
}

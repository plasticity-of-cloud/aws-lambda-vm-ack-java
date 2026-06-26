package ai.codriverlabs.microvm.operator.cli.commands;

import picocli.CommandLine.Command;

@Command(name = "image", description = "Manage MicroVM images",
    subcommands = {
        ImageCreateCommand.class,
        ImageListCommand.class,
        ImageDescribeCommand.class,
        ImageUpdateCommand.class,
        ImageDeleteCommand.class
    },
    mixinStandardHelpOptions = true)
public class ImageCommand {
}

package com.orchbiz.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "orchbiz",
        mixinStandardHelpOptions = true,
        version = "orchbiz-cli 0.1.0",
        description = "Deterministic checks for the OrchBiz delivery workspace.%n"
                + "Exit codes: 0 = passed, 1 = check failed / blocked, 2 = usage or environment error.",
        subcommands = {
                Commands.InitClient.class,
                Commands.ParseIntake.class,
                Commands.Validate.class,
                Commands.ValidateClient.class,
                Commands.CheckScope.class,
                Commands.CheckBudget.class,
                Commands.CheckGate.class,
                Commands.ScanSecrets.class,
                Commands.ValidateCatalogue.class
        })
public class OrchbizCli implements Callable<Integer> {

    public static final int OK = 0;
    public static final int FAILED = 1;
    public static final int ERROR = 2;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() {
        spec.commandLine().usage(spec.commandLine().getOut());
        return ERROR;
    }

    /** Shared {@code --root} option. */
    public static class RootOption {
        @Option(names = "--root", description = "Workspace root (default: auto-detected from the current directory).")
        Path root;

        Workspace workspace() {
            return root != null ? new Workspace(root) : Workspace.locate(Path.of(""));
        }
    }

    public static CommandLine commandLine(PrintWriter out, PrintWriter err) {
        CommandLine cl = new CommandLine(new OrchbizCli());
        cl.setOut(out);
        cl.setErr(err);
        cl.setExecutionExceptionHandler((ex, cmd, parseResult) -> {
            if (ex instanceof CliException) {
                cmd.getErr().println("ERROR: " + ex.getMessage());
            } else {
                cmd.getErr().println("ERROR: " + ex);
            }
            cmd.getErr().flush();
            return ERROR;
        });
        return cl;
    }

    public static void main(String[] args) {
        PrintWriter out = new PrintWriter(System.out, true);
        PrintWriter err = new PrintWriter(System.err, true);
        System.exit(commandLine(out, err).execute(args));
    }
}

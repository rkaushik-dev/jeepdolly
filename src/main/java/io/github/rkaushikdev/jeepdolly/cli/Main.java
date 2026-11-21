package io.github.rkaushikdev.jeepdolly.cli;

import io.github.rkaushikdev.jeepdolly.Jeepdolly;
import io.github.rkaushikdev.jeepdolly.Logs;
import io.github.rkaushikdev.jeepdolly.Refusal;
import io.github.rkaushikdev.jeepdolly.Settings;
import io.github.rkaushikdev.jeepdolly.persistence.TariffStore;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import org.slf4j.event.Level;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.UnmatchedArgumentException;

/**
 * The command line, and the three answers it can give.
 *
 * <p>Exit codes, because this is run from cron and from a Makefile and both of them only understand
 * numbers. {@link #DONE} is done. {@link #REFUSED} is a refusal, which is the office saying no: a
 * load that is over, a permit that is void, a carrier that is suspended. Its code and sentence go to
 * standard error as {@code over_limit: the tandem is 4,000 lb over}. {@link #MISUSED} is the caller
 * using the command wrongly: a command that does not exist, an option left off, a day that is not a
 * day, a file that is not there.
 *
 * <p>Nothing else is ever caught. An unexpected exception is a defect and it comes out of {@link
 * #run} as it was thrown, on purpose, because a cron job that swallows a defect and exits zero is how
 * a database goes unmigrated for a month.
 *
 * <p>Every command prints plain lines, one record to a line, with the fields separated by two
 * spaces. No tables, no colour, no box drawing. The output of these commands is read by people and
 * piped into grep and awk, and the first of those two is the one that gets forgotten.
 *
 * <p>No command but {@code serve} starts the http service. Each works against the database the
 * settings name, or the one its caller handed in, and leaves the office log alone when {@code
 * --quiet} is given.
 */
@Command(
        name = Jeepdolly.NAME,
        description = "Permits for the motor carrier office.",
        version = Jeepdolly.NAME + ", version " + Jeepdolly.VERSION,
        subcommands = {
            Main.Build.class,
            CitationCommands.Citations.class,
            ExportCommands.Export.class,
            Main.Health.class,
            NightlyCommands.Night.class,
            PermitCommands.Permits.class,
            PilotCommands.Pilots.class,
            RenewalCommands.Renewal.class,
            ReportCommands.Report.class,
            NetworkCommands.Roads.class,
            Serve.class,
            LineCommands.Wires.class
        })
public final class Main {

    /** It worked, or there was nothing to do, which on a quiet night is the same thing. */
    public static final int DONE = 0;

    /** The office said no. */
    public static final int REFUSED = 1;

    /** The command was used wrongly. */
    public static final int MISUSED = 2;

    private static final String HELP = "Show this message and exit.";

    private final Terminal terminal;
    private final Optional<Console> handed;
    private Optional<Console> made = Optional.empty();
    private Console console;

    @Option(names = "--database", description = "Database url, overriding the environment.")
    private String database;

    @Option(names = "--quiet", description = "Say nothing on the log.")
    private boolean quiet;

    @Option(names = "--version", versionHelp = true, description = "Show the version and exit.")
    private boolean version;

    @Option(names = "--help", usageHelp = true, description = HELP)
    private boolean help;

    private Main(Terminal terminal, Optional<Console> handed) {
        this.terminal = terminal;
        this.handed = handed;
    }

    /** The console script. */
    public static void main(String... args) {
        System.exit(run(Terminal.system(), args));
    }

    /**
     * Run one command line against the database the environment and {@code --database} name. The
     * database is opened when a command first asks for it and closed when the command is done.
     *
     * @return the exit code
     */
    public static int run(Terminal terminal, String... args) {
        return run(terminal, Optional.empty(), List.of(), args);
    }

    /**
     * Run one command line against a console the caller already has. Its database is the caller's:
     * {@code --database} does not replace it and the command does not close it.
     *
     * @return the exit code
     */
    public static int run(Terminal terminal, Console console, String... args) {
        return run(terminal, Optional.of(console), List.of(), args);
    }

    /** Run one command line with more commands beside the office's own. */
    static int run(Terminal terminal, Optional<Console> console, List<Object> extra, String... args) {
        Main main = new Main(terminal, console);
        try {
            return main.execute(main.commandLine(extra), args);
        } finally {
            main.made.ifPresent(Console::close);
            terminal.out().flush();
            terminal.err().flush();
        }
    }

    /** One record, one line, two spaces between the fields. A field that is absent prints blank. */
    static String record(Object... fields) {
        StringJoiner line = new StringJoiner("  ");
        for (Object field : fields) {
            line.add(field(field));
        }
        return line.toString();
    }

    private static String field(Object field) {
        if (field instanceof Optional<?> maybe) {
            return maybe.map(String::valueOf).orElse("");
        }
        return field == null ? "" : field.toString();
    }

    Terminal terminal() {
        return terminal;
    }

    /** The console the command runs against, known once the root options have been read. */
    Console console() {
        return console;
    }

    private CommandLine commandLine(List<Object> extra) {
        CommandLine line = new CommandLine(this);
        extra.forEach(line::addSubcommand);
        helpOnEvery(line);
        return line.setExpandAtFiles(false)
                .setOverwrittenOptionsAllowed(true)
                .setCaseInsensitiveEnumValuesAllowed(true)
                .registerConverter(LocalDate.class, new Days())
                .setColorScheme(Help.defaultColorScheme(Help.Ansi.OFF))
                .setOut(terminal.out())
                .setErr(terminal.err());
    }

    /** Every command answers {@code --help}, and none of them answers {@code -h}. */
    private static void helpOnEvery(CommandLine line) {
        for (CommandLine command : line.getSubcommands().values()) {
            CommandSpec spec = command.getCommandSpec();
            if (spec.findOption("--help") == null) {
                spec.addOption(OptionSpec.builder("--help")
                        .usageHelp(true)
                        .type(boolean.class)
                        .arity("0")
                        .description(HELP)
                        .build());
            }
            helpOnEvery(command);
        }
    }

    private int execute(CommandLine line, String... args) {
        ParseResult parsed;
        try {
            parsed = line.parseArgs(args);
        } catch (ParameterException wrong) {
            return misused(wrong);
        }
        if (CommandLine.printHelpIfRequested(parsed)) {
            return DONE;
        }
        ParseResult chosen = parsed;
        while (chosen.hasSubcommand()) {
            chosen = chosen.subcommand();
        }
        try {
            begin();
            if (chosen.commandSpec().userObject() instanceof Action action) {
                return action.call();
            }
            chosen.commandSpec().commandLine().usage(terminal.out());
            return DONE;
        } catch (Refusal refusal) {
            terminal.err().println(refusal);
            return REFUSED;
        } catch (Stopped stopped) {
            terminal.err().println(stopped.getMessage());
            return REFUSED;
        } catch (ParameterException wrong) {
            return misused(wrong);
        }
    }

    /** Read the settings, lay {@code --database} over them, and point the log at standard error. */
    private void begin() {
        Settings settings = Settings.from(terminal.environment());
        if (database != null && !database.isEmpty()) {
            settings = settings.withDatabase(database);
        }
        if (!quiet) {
            Logs.configure(Level.INFO, new WriterStream(terminal.err()));
        }
        if (handed.isPresent()) {
            console = handed.get();
        } else {
            console = new Console(settings);
            made = Optional.of(console);
        }
    }

    /** The usage line, where to find the help, and what was wrong, on standard error. */
    private int misused(ParameterException wrong) {
        CommandLine where = wrong.getCommandLine();
        Help usage = where.getHelp();
        PrintWriter err = terminal.err();
        err.print(usage.synopsisHeading() + usage.synopsis(usage.synopsisHeadingLength()));
        err.println("Try '" + where.getCommandSpec().qualifiedName() + " --help' for help.");
        err.println();
        err.println("Error: " + wrong.getMessage());
        UnmatchedArgumentException.printSuggestions(wrong, err);
        return MISUSED;
    }

    /** Say whether the database is reachable. */
    @Command(name = "health", description = "Say whether the database is reachable.")
    static final class Health extends Action {

        @Override
        public Integer call() {
            Console console = console();
            try (Connection connection = console.database().dataSource().getConnection();
                    Statement statement = connection.createStatement()) {
                statement.execute("select 1");
            } catch (SQLException unreachable) {
                throw new IllegalStateException(
                        "cannot reach " + console.settings().safeUrl(), unreachable);
            }
            say("database", "up", console.settings().safeUrl());
            return DONE;
        }
    }

    /** Build the schema and seed the published tables. */
    @Command(name = "build", description = "Build the schema and seed the published tables.")
    static final class Build extends Action {

        @Override
        public Integer call() {
            Console console = console();
            console.build();
            try (UnitOfWork work = console.work()) {
                if (!TariffStore.seeded(work.entityManager())) {
                    TariffStore.seed(work.entityManager());
                    work.commit();
                    say("tariff", "seeded");
                }
            }
            say("schema", "built", console.settings().safeUrl());
            return DONE;
        }
    }
}

package io.github.rkaushikdev.jeepdolly.cli;

import io.github.rkaushikdev.jeepdolly.Written;
import java.nio.file.Files;
import java.nio.file.Path;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;

/**
 * A file named on the command line, checked before the command touches the database.
 *
 * <p>A file that is not there, a directory where a file belongs, or a file nobody may read is the
 * caller using the command wrongly, so each is a usage mistake rather than a refusal.
 */
final class FileArgument {

    private FileArgument() {}

    /**
     * A file the command reads, which must already be there.
     *
     * @param label how the help names the argument, {@code PATH} or {@code --to}
     * @throws ParameterException when it is not there, is a directory, or cannot be read
     */
    static Path existing(CommandSpec spec, String label, String given) {
        Path path = Path.of(given);
        if (!Files.exists(path)) {
            throw wrong(spec, label, "File " + Written.quoted(given) + " does not exist.");
        }
        return checked(spec, label, given, path);
    }

    /**
     * A file the command writes, which may or may not be there yet.
     *
     * @throws ParameterException when it is a directory, or is there and cannot be read
     */
    static Path written(CommandSpec spec, String label, String given) {
        Path path = Path.of(given);
        if (!Files.exists(path)) {
            return path;
        }
        return checked(spec, label, given, path);
    }

    private static Path checked(CommandSpec spec, String label, String given, Path path) {
        if (Files.isDirectory(path)) {
            throw wrong(spec, label, "File " + Written.quoted(given) + " is a directory.");
        }
        if (!Files.isReadable(path)) {
            throw wrong(spec, label, "File " + Written.quoted(given) + " is not readable.");
        }
        return path;
    }

    private static ParameterException wrong(CommandSpec spec, String label, String trouble) {
        return new ParameterException(spec.commandLine(), "Invalid value for '" + label + "': " + trouble);
    }
}

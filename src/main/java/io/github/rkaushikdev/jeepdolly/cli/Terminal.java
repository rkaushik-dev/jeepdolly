package io.github.rkaushikdev.jeepdolly.cli;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.function.UnaryOperator;

/**
 * What a command talks to: the environment it was started in, the keyboard and the two streams.
 *
 * <p>Records go to {@code out}. {@code err} is for the refusal, the usage mistake and the count of
 * lines a load could not read: a cron job that writes to standard error on a good night sends a
 * mail every night, and the mail gets filtered within a fortnight.
 *
 * @param environment the environment variables, answering null for one that is not set
 * @param in the keyboard
 * @param out where the records go
 * @param err where the troubles go
 */
public record Terminal(UnaryOperator<String> environment, BufferedReader in, PrintWriter out, PrintWriter err) {

    /** The process's own environment, keyboard and streams. */
    public static Terminal system() {
        Charset charset = Charset.defaultCharset();
        return new Terminal(
                System.getenv()::get,
                new BufferedReader(new InputStreamReader(System.in, charset)),
                new PrintWriter(System.out, true, charset),
                new PrintWriter(System.err, true, charset));
    }
}

package io.github.rkaushikdev.jeepdolly;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logging as key and value, because the office greps its own logs.
 *
 * <p>Nobody reads this log with a viewer. When a hauler rings about permit 25-P-004471, somebody
 * greps for it, and what they need back is every line that mentions it with enough on each line to
 * say what happened. So every line is
 *
 * <pre>permit.issued number=25-P-004471 usdot=1204471 gross=94000 miles=44</pre>
 *
 * <p>and never a sentence with the number embedded in it. The event name comes first, unquoted, and
 * everything after it is {@code key=value} with a space between. Values with a space in them are
 * quoted, values with none are not, because quoting everything makes the common case harder to
 * read.
 *
 * <p>This produces a line and hands it to SLF4J, and the logging configuration decides where it
 * goes. One rule about what goes in a line: never a whole document, never a body, never a key. A
 * log line names things and says what happened to them.
 */
public final class Logs {

    /** The office logger's name. */
    public static final String LOGGER = "jeepdolly";

    /** Anything with one of these in its name is written as three asterisks. */
    public static final List<String> SECRET = List.of("key", "token", "password", "secret", "digest");

    private Logs() {}

    /** One log line with no fields: just the event. */
    public static String line(String event) {
        return line(event, Map.of());
    }

    /**
     * One log line: the event, then key and value in the order given.
     *
     * @throws IllegalArgumentException when the event is blank, which is a mistake in the caller
     */
    public static String line(String event, Map<String, ?> fields) {
        if (event.isBlank()) {
            throw new IllegalArgumentException("a log line names an event");
        }
        List<String> parts = new ArrayList<>();
        parts.add(event);
        fields.forEach((name, value) -> parts.add(name + "=" + (hidden(name) ? "***" : quoted(value))));
        return String.join(" ", parts);
    }

    private static String quoted(Object value) {
        String written = value == null ? "" : value.toString();
        if (written.isEmpty()) {
            return "\"\"";
        }
        if (written.contains(" ") || written.contains("=") || written.contains("\"")) {
            return '"' + written.replace('"', '\'') + '"';
        }
        return written;
    }

    private static boolean hidden(String name) {
        String lowered = name.toLowerCase(Locale.ROOT);
        return SECRET.stream().anyMatch(lowered::contains);
    }

    /** The office logger. */
    public static Logger logger() {
        return LoggerFactory.getLogger(LOGGER);
    }

    /** Write one line at info, which is where the office reads. */
    public static void event(String name) {
        event(name, Map.of());
    }

    /** Write one line at info, which is where the office reads. */
    public static void event(String name, Map<String, ?> fields) {
        logger().info(line(name, fields));
    }

    /** Write one line at warning, for something a person should see. */
    public static void trouble(String name, Map<String, ?> fields) {
        logger().warn(line(name, fields));
    }

    /** Write one line at error, for something that did not work at all. */
    public static void failure(String name, Map<String, ?> fields) {
        logger().error(line(name, fields));
    }

    /** Point the office logger at standard error, at info, once, at start up. */
    public static Logger configure() {
        return configure(org.slf4j.event.Level.INFO, null);
    }

    /**
     * Point the office logger at somewhere, once, at start up. Calling it again replaces the
     * handler rather than adding a second one, so no line is ever written twice.
     *
     * @param level the quietest level that is written
     * @param stream where the lines go; null means standard error
     */
    public static Logger configure(org.slf4j.event.Level level, OutputStream stream) {
        ch.qos.logback.classic.Logger found = officeLogger();
        LoggerContext context = found.getLoggerContext();
        found.setLevel(Level.convertAnSLF4JLevel(level));
        found.setAdditive(false);
        found.detachAndStopAllAppenders();

        OfficeLayout layout = new OfficeLayout();
        layout.setContext(context);
        layout.start();
        LayoutWrappingEncoder<ILoggingEvent> encoder = new LayoutWrappingEncoder<>();
        encoder.setContext(context);
        encoder.setLayout(layout);
        encoder.setCharset(StandardCharsets.UTF_8);
        encoder.start();

        OutputStreamAppender<ILoggingEvent> appender = new OutputStreamAppender<>();
        appender.setContext(context);
        appender.setName("office");
        appender.setEncoder(encoder);
        appender.setImmediateFlush(true);
        appender.setOutputStream(new KeptOpen(stream == null ? System.err : stream));
        appender.start();
        found.addAppender(appender);
        return found;
    }

    private static ch.qos.logback.classic.Logger officeLogger() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        return context.getLogger(LOGGER);
    }

    /**
     * A stream an appender may stop without closing what it was handed. Reconfiguring on standard
     * error would otherwise close standard error for the whole process.
     */
    private static final class KeptOpen extends FilterOutputStream {

        KeptOpen(OutputStream stream) {
            super(stream);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            out.write(bytes, offset, length);
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }
}

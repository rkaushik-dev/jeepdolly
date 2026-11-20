package io.github.rkaushikdev.jeepdolly;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.LayoutBase;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The office's line on the way out: when, how bad, and the line itself.
 *
 * <pre>2025-04-08 10:14:03,512 WARNING route.blocked segment=US2-1</pre>
 *
 * <p>The level is spelled the way the office has always read it, {@code WARNING} rather than
 * Logback's {@code WARN}, because there are saved searches for it.
 */
public final class OfficeLayout extends LayoutBase<ILoggingEvent> {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS", Locale.ROOT);

    @Override
    public String doLayout(ILoggingEvent event) {
        LocalDateTime when =
                LocalDateTime.ofInstant(Instant.ofEpochMilli(event.getTimeStamp()), ZoneId.systemDefault());
        return STAMP.format(when) + " " + levelName(event.getLevel()) + " " + event.getFormattedMessage() + "\n";
    }

    /** The level as the office's saved searches spell it. */
    static String levelName(Level level) {
        return switch (level.toInt()) {
            case Level.WARN_INT -> "WARNING";
            default -> level.toString();
        };
    }
}

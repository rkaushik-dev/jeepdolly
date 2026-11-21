package io.github.rkaushikdev.jeepdolly.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Logs;
import io.github.rkaushikdev.jeepdolly.persistence.Database;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.support.Consoles;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.ConnectException;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

/** The service started from the command line, the way the jar starts it. */
class ServeTest {

    /** What the serving thread says when it has finished, whether or not it printed anything. */
    private static final String FINISHED = "<finished>";

    private static final String SERVING = "serving jeepdolly on ";

    @AfterEach
    void theLogGoesBackToStandardError() {
        Logs.configure(Level.WARN, null);
    }

    @Test
    void theServiceAnswersUntilItIsInterruptedAndThenItIsClosed() throws Exception {
        Database database = Databases.seeded();
        Console console = Consoles.over(database);
        BlockingQueue<String> said = new LinkedBlockingQueue<>();
        StringWriter troubles = new StringWriter();
        Terminal terminal = new Terminal(
                Map.<String, String>of()::get,
                new BufferedReader(new StringReader("")),
                new PrintWriter(new Lines(said), true),
                new PrintWriter(troubles, true));
        AtomicInteger code = new AtomicInteger(-1);
        AtomicReference<Throwable> escaped = new AtomicReference<>();
        Thread serving = new Thread(
                () -> {
                    try {
                        code.set(Main.run(terminal, console, "serve", "--port", "0"));
                    } catch (RuntimeException | Error unexpected) {
                        escaped.set(unexpected);
                    } finally {
                        said.add(FINISHED);
                    }
                },
                "serve");
        serving.start();

        String line = said.poll(3, TimeUnit.MINUTES);
        assertThat(escaped.get()).isNull();
        assertThat(line).matches("serving jeepdolly on http://127\\.0\\.0\\.1:\\d+");
        URI base = URI.create(line.substring(SERVING.length()));
        try (HttpClient http = HttpClient.newHttpClient()) {
            HttpResponse<String> health = http.send(
                    HttpRequest.newBuilder(base.resolve("/health")).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(health.statusCode()).isEqualTo(200);
            assertThat(health.body()).contains("\"database\":\"up\"");
        }

        serving.interrupt();
        serving.join(TimeUnit.MINUTES.toMillis(1));
        assertThat(serving.isAlive()).isFalse();
        assertThat(escaped.get()).isNull();
        assertThat(code.get()).isZero();
        assertThatThrownBy(() -> new Socket(base.getHost(), base.getPort()).close())
                .isInstanceOf(ConnectException.class);
        // The service kept the office log where the command line pointed it.
        assertThat(troubles.toString()).containsPattern("request\\.(done|slow) method=GET path=/health status=200");
        // The database was handed in, so it is still open for whoever handed it over.
        try (UnitOfWork work = database.unitOfWork()) {
            assertThat(work.network().segments()).hasSize(12);
        }
    }

    /** A writer that hands each finished line to a queue, for a test on another thread to read. */
    private static final class Lines extends Writer {

        private final BlockingQueue<String> queue;
        private final StringBuilder line = new StringBuilder();

        Lines(BlockingQueue<String> queue) {
            this.queue = queue;
        }

        @Override
        public synchronized void write(char[] text, int offset, int length) {
            for (int index = offset; index < offset + length; index++) {
                if (text[index] == '\n') {
                    queue.add(line.toString());
                    line.setLength(0);
                } else {
                    line.append(text[index]);
                }
            }
        }

        @Override
        public void flush() {
            // Every finished line is on the queue already.
        }

        @Override
        public void close() {
            // The queue belongs to the test.
        }
    }
}

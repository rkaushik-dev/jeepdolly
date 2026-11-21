package io.github.rkaushikdev.jeepdolly.cli;

import io.github.rkaushikdev.jeepdolly.Jeepdolly;
import io.github.rkaushikdev.jeepdolly.api.JeepdollyApplication;
import java.util.concurrent.CountDownLatch;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Run the http service until it is stopped.
 *
 * <p>The one command that starts the service. It hands the service the console's settings and
 * database, says where it is listening, and waits until the service closes, on a signal or an
 * interrupt. The service is told to leave the logging alone, so the office log goes where the root
 * options pointed it, and nowhere at all under {@code --quiet}.
 */
@Command(name = "serve", description = "Run the http service until it is stopped.")
final class Serve extends Action {

    @Option(names = "--host", defaultValue = "127.0.0.1", description = "Address to listen on.")
    private String host;

    @Option(names = "--port", defaultValue = "8000", description = "Port to listen on; 0 picks a free one.")
    private int port;

    @Override
    public Integer call() {
        Console console = console();
        CountDownLatch closed = new CountDownLatch(1);
        try (ConfigurableApplicationContext service = start(console)) {
            service.addApplicationListener(event -> {
                if (event instanceof ContextClosedEvent) {
                    closed.countDown();
                }
            });
            int listening = ((ServletWebServerApplicationContext) service)
                    .getWebServer()
                    .getPort();
            echo("serving " + Jeepdolly.NAME + " on http://" + host + ":" + listening);
            main().terminal().out().flush();
            if (service.isActive()) {
                closed.await();
            }
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
        }
        return Main.DONE;
    }

    /**
     * Start the service with its own logging set up switched off, so it neither resets the office
     * log nor writes its own lines past it, and with the server's own log carried on the same log.
     */
    private ConfigurableApplicationContext start(Console console) {
        if (!SLF4JBridgeHandler.isInstalled()) {
            SLF4JBridgeHandler.removeHandlersForRootLogger();
            SLF4JBridgeHandler.install();
        }
        String before = System.getProperty(LoggingSystem.SYSTEM_PROPERTY);
        System.setProperty(LoggingSystem.SYSTEM_PROPERTY, LoggingSystem.NONE);
        try {
            return JeepdollyApplication.run(
                    console.settings(), console.database(), "--server.port=" + port, "--server.address=" + host);
        } finally {
            if (before == null) {
                System.clearProperty(LoggingSystem.SYSTEM_PROPERTY);
            } else {
                System.setProperty(LoggingSystem.SYSTEM_PROPERTY, before);
            }
        }
    }
}

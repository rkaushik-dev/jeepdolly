package io.github.rkaushikdev.jeepdolly.cli;

/**
 * The operator said no at a question the command asked, or walked away from it.
 *
 * <p>Not a refusal and not a defect: the command prints {@code stopped} and exits {@link
 * Main#REFUSED}, so a script that runs it knows nothing was done.
 */
final class Stopped extends RuntimeException {

    Stopped() {
        super("stopped");
    }
}

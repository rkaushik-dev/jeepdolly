package io.github.rkaushikdev.jeepdolly.cli;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * The terminal's error writer seen as a stream of UTF-8 bytes, which is what the office log writes.
 *
 * <p>Bytes are held until a whole line has arrived and then written as text, so a character whose
 * encoding is split across two writes is never cut in half. A newline byte never occurs inside a
 * UTF-8 sequence, so a line boundary is always a character boundary.
 */
final class WriterStream extends OutputStream {

    private final PrintWriter writer;
    private final ByteArrayOutputStream pending = new ByteArrayOutputStream();

    WriterStream(PrintWriter writer) {
        this.writer = writer;
    }

    @Override
    public synchronized void write(int one) {
        pending.write(one);
        if (one == '\n') {
            drain();
        }
    }

    @Override
    public synchronized void write(byte[] bytes, int offset, int length) {
        for (int index = offset; index < offset + length; index++) {
            write(bytes[index]);
        }
    }

    @Override
    public synchronized void flush() {
        writer.flush();
    }

    /** Write whatever is still held, even without a newline, and flush. */
    @Override
    public synchronized void close() {
        if (pending.size() > 0) {
            drain();
        }
        writer.flush();
    }

    private void drain() {
        writer.write(pending.toString(StandardCharsets.UTF_8));
        writer.flush();
        pending.reset();
    }
}

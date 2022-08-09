/*
 * Copyright (c) 2002-2021, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.reader;

import java.io.StringWriter;
import java.io.Writer;

/**
 * Redirects a {@link Writer} to a {@link LineReader}'s {@link LineReader#printAbove(String)} method,
 * which draws output above the current prompt / input line.
 *
 * <p>Example:</p>
 * <pre>
 *     LineReader reader = LineReaderBuilder.builder().terminal(terminal).parser(parser).build();
 *     PrintAboveWriter printAbove = new PrintAboveWriter(reader);
 *     printAbove.write(new char[] { 'h', 'i', '!', '\n'});
 * </pre>
 *
 */
public class PrintAboveWriter extends StringWriter {
    private final LineReader reader;

    public PrintAboveWriter(LineReader reader) {
        this.reader = reader;
    }

    @Override
    public void flush() {
        StringBuffer buffer = getBuffer();
        int lastNewline = buffer.lastIndexOf("\n");
        if (lastNewline >= 0) {
            reader.printAbove(buffer.substring(0, lastNewline + 1));
            buffer.delete(0, lastNewline + 1);
        }
    }
}

/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, a notice that is now available elsewhere in this distribution
 * accompanied the original version of this file, and, per its terms,
 * should not be removed.
 */

package jdk.internal.org.commonmark.internal.util;

import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;

/**
 * Reads lines from a reader like {@link java.io.BufferedReader} but also returns the line terminators.
 * <p>
 * Line terminators can be either a line feed {@code "\n"}, carriage return {@code "\r"}, or a carriage return followed
 * by a line feed {@code "\r\n"}. Call {@link #getLineTerminator()} after {@link #readLine()} to obtain the
 * corresponding line terminator. If a stream has a line at the end without a terminator, {@link #getLineTerminator()}
 * returns {@code null}.
 */
public class LineReader implements Closeable {

    // Same as java.io.BufferedReader
    static final int CHAR_BUFFER_SIZE = 8192;
    static final int EXPECTED_LINE_LENGTH = 80;

    private Reader reader;
    private char[] cbuf;

    private int position = 0;
    private int limit = 0;

    private String lineTerminator = null;

    public LineReader(Reader reader) {
        this.reader = reader;
        this.cbuf = new char[CHAR_BUFFER_SIZE];
    }

    /**
     * Read a line of text.
     *
     * @return the line, or {@code null} when the end of the stream has been reached and no more lines can be read
     */
    public String readLine() throws IOException {
        StringBuilder sb = null;
        boolean cr = false;

        while (true) {
            if (position >= limit) {
                fill();
            }

            if (cr) {
                // We saw a CR before, check if we have CR LF or just CR.
                if (position < limit && cbuf[position] == '\n') {
                    position++;
                    return line(sb.toString(), "\r\n");
                } else {
                    return line(sb.toString(), "\r");
                }
            }

            if (position >= limit) {
                // End of stream, return either the last line without terminator or null for end.
                return line(sb != null ? sb.toString() : null, null);
            }

            int start = position;
            int i = position;
            for (; i < limit; i++) {
                char c = cbuf[i];
                if (c == '\n') {
                    position = i + 1;
                    return line(finish(sb, start, i), "\n");
                } else if (c == '\r') {
                    if (i + 1 < limit) {
                        // We know what the next character is, so we can check now whether we have
                        // a CR LF or just a CR and return.
                        if (cbuf[i + 1] == '\n') {
                            position = i + 2;
                            return line(finish(sb, start, i), "\r\n");
                        } else {
                            position = i + 1;
                            return line(finish(sb, start, i), "\r");
                        }
                    } else {
                        // We don't know what the next character is yet, check on next iteration.
                        cr = true;
                        position = i + 1;
                        break;
                    }
                }
            }

            if (position < i) {
                position = i;
            }

            // Haven't found a finished line yet, copy the data from the buffer so that we can fill
            // the buffer again.
            if (sb == null) {
                sb = new StringBuilder(EXPECTED_LINE_LENGTH);
            }
            sb.append(cbuf, start, i - start);
        }
    }

    /**
     * Return the line terminator of the last read line from {@link #readLine()}.
     *
     * @return {@code "\n"}, {@code "\r"}, {@code "\r\n"}, or {@code null}
     */
    public String getLineTerminator() {
        return lineTerminator;
    }

    @Override
    public void close() throws IOException {
        if (reader == null) {
            return;
        }
        try {
            reader.close();
        } finally {
            reader = null;
            cbuf = null;
        }
    }

    private void fill() throws IOException {
        int read;
        do {
            read = reader.read(cbuf, 0, cbuf.length);
        } while (read == 0);
        if (read > 0) {
            limit = read;
            position = 0;
        }
    }

    private String line(String line, String lineTerminator) {
        this.lineTerminator = lineTerminator;
        return line;
    }

    private String finish(StringBuilder sb, int start, int end) {
        int len = end - start;
        if (sb == null) {
            return new String(cbuf, start, len);
        } else {
            return sb.append(cbuf, start, len).toString();
        }
    }
}

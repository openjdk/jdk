/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 *
 */

package com.sun.hotspot.tools.compiler;

import java.io.*;
import java.util.regex.*;

/**
 * This class is a filter class to deal with malformed XML that used
 * to be produced by the JVM when generating LogCompilation.  In 1.6
 * and later releases it shouldn't be required.
 * @author never
 */

class LogCleanupReader extends Reader {
    private Reader reader;

    private char[] buffer = new char[4096];

    private int bufferCount;

    private int bufferOffset;

    private char[] line = new char[1024];

    private int index;

    private int length;

    private char[] one = new char[1];

    LogCleanupReader(Reader r) {
        reader = r;
    }

    static final private Matcher pattern = Pattern.compile(".+ compile_id='[0-9]+'.*( compile_id='[0-9]+)").matcher("");
    static final private Matcher pattern2 = Pattern.compile("' (C[12]) compile_id=").matcher("");
    static final private Matcher pattern3 = Pattern.compile("'(destroy_vm)/").matcher("");

    private void fill() throws IOException {
        rawFill();
        if (length != -1) {
            boolean changed = false;
            String s = new String(line, 0, length);
            String orig = s;

            pattern2.reset(s);
            if (pattern2.find()) {
                s = s.substring(0, pattern2.start(1)) + s.substring(pattern2.end(1) + 1);
                changed = true;
            }

            pattern.reset(s);
            if (pattern.lookingAt()) {
                s = s.substring(0, pattern.start(1)) + s.substring(pattern.end(1) + 1);
                changed = true;
            }

            pattern3.reset(s);
            if (pattern3.find()) {
                s = s.substring(0, pattern3.start(1)) + s.substring(pattern3.end(1));
                changed = true;
            }

            if (changed) {
                s.getChars(0, s.length(), line, 0);
                length = s.length();
            }
        }
    }

    private void rawFill() throws IOException {
        if (bufferCount == -1) {
            length = -1;
            return;
        }

        int i = 0;
        boolean fillNonEOL = true;
        outer:
        while (true) {
            if (fillNonEOL) {
                int p;
                for (p = bufferOffset; p < bufferCount; p++) {
                    char c = buffer[p];
                    if (c == '\r' || c == '\n') {
                        bufferOffset = p;
                        fillNonEOL = false;
                        continue outer;
                    }
                    if (i >= line.length) {
                        // copy and enlarge the line array
                        char[] newLine = new char[line.length * 2];
                        System.arraycopy(line, 0, newLine, 0, line.length);
                        line = newLine;
                    }
                    line[i++] = c;
                }
                bufferOffset = p;
            } else {
                int p;
                for (p = bufferOffset; p < bufferCount; p++) {
                    char c = buffer[p];
                    if (c != '\r' && c != '\n') {
                        bufferOffset = p;
                        length = i;
                        index = 0;
                        return;
                    }
                    line[i++] = c;
                }
                bufferOffset = p;
            }
            if (bufferCount == -1) {
                if (i == 0) {
                    length = -1;
                } else {
                    length = i;
                }
                index = 0;
                return;
            }
            if (bufferOffset != bufferCount) {
                System.out.println(bufferOffset);
                System.out.println(bufferCount);
                throw new InternalError("how did we get here");
            }
            // load more data and try again.
            bufferCount = reader.read(buffer, 0, buffer.length);
            bufferOffset = 0;
        }
    }

    public int read() throws java.io.IOException {
        read(one, 0, 1);
        return one[0];
    }

    public int read(char[] buffer) throws java.io.IOException {
        return read(buffer, 0, buffer.length);
    }

    public int read(char[] b, int off, int len) throws java.io.IOException {
        if (length == -1) {
            return -1;
        }

        if (index == length) {
            fill();
            if (length == -1) {
                return -1;
            }
        }
        int n = Math.min(length - index, Math.min(b.length - off, len));
        // System.out.printf("%d %d %d %d %d\n", index, length, off, len, n);
        System.arraycopy(line, index, b, off, n);
        index += n;
        return n;
    }

    public long skip(long n) throws java.io.IOException {
        long result = n;
        while (n-- > 0) read();
        return result;
    }

    public boolean ready() throws java.io.IOException {
        return reader.ready() || (line != null && length > 0);
    }

    public boolean markSupported() {
        return false;
    }

    public void mark(int unused) throws java.io.IOException {
        throw new UnsupportedOperationException("mark not supported");
    }

    public void reset() throws java.io.IOException {
        reader.reset();
        line = null;
        index = 0;
    }

    public void close() throws java.io.IOException {
        reader.close();
        line = null;
        index = 0;
    }
}

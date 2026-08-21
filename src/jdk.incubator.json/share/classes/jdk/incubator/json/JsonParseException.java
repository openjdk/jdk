/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.incubator.json;

import java.io.Serial;

/**
 * Signals that an error has been detected while parsing the
 * JSON text. This exception is thrown if the value supplied
 * to either {@link Json#parse(String)} or {@link Json#parse(char[])}
 * is not valid JSON syntax, or contains a JSON object with duplicate
 * names.
 *
 * @since 28
 */
public final class JsonParseException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 7022545379651073390L;

    /**
     * Zero-based line number of the error
     * @serial
     */
    private final int line;

    /**
     * Zero-based position of the error within the line
     * @serial
     */
    private final int pos;

    /**
     * Constructs a JsonParseException with the specified detail message.
     * @param message the detail message
     * @param line the zero-based line number of the error, counted by
     *         {@code '\n'} (linefeed, {@code U+000A}) characters. Non-negative.
     * @param pos the zero-based position of the error within the line, counted
     *         in UTF-16 code units. Non-negative.
     * @throws IllegalArgumentException if either {@code line} or {@code pos} are
     *      negative
     */
    public JsonParseException(String message, int line, int pos) {
        super(message);
        if (line < 0 || pos < 0) {
            throw new IllegalArgumentException(
                    "\"line\" and \"pos\" should be non-negative");
        }
        this.line = line;
        this.pos = pos;
    }

    /**
     * {@return the zero-based line number of the error, counted by
     * {@code '\n'} (linefeed, {@code U+000A}) characters}
     */
    public int getErrorLine() {
        return line;
    }

    /**
     * {@return the zero-based position of the error within the line,
     * counted in UTF-16 code units}
     */
    public int getErrorPosition() {
        return pos;
    }
}

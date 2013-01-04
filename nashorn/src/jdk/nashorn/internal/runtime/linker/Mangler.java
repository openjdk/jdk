/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.runtime.linker;

import java.util.Arrays;

/**
 * Used to mangle java visible names. Currently used only to transform script file
 * name to be safely used as part of generated Script class name.
 */
public class Mangler {
    /** Beginning of escape sequence (mu as in municode.) */
    private static final char ESCAPE = '\u03BC';

    /** Hexadecimal base. */
    private static final char hexBase = 'A';

    /** Hexadecimal characters. */
    private static final char[] hexCharacters = new char[] {
        '0',
        '1',
        '2',
        '3',
        '4',
        '5',
        '6',
        '7',
        '8',
        '9',
        hexBase + 0,
        hexBase + 1,
        hexBase + 2,
        hexBase + 3,
        hexBase + 4,
        hexBase + 5
    };

    private Mangler() {
    }

    /**
     * Mangles a user supplied name so it is java identifier safe.
     * @param name Name to be mangled
     * @return Mangled name or null if not mangled.
     */
    public static String mangle(final String name) {
        final int length = name.length();
        final char[] buffer = new char[length * 5];
        boolean mangled = false;
        int pos = 0;

        for (int i = 0; i < length; i++) {
            final char ch = name.charAt(i);

            if (! Character.isJavaIdentifierPart(ch)) {
                buffer[pos++] = ESCAPE;
                buffer[pos++] = hexCharacters[(ch >>> 12) & 0xF];
                buffer[pos++] = hexCharacters[(ch >>>  8) & 0xF];
                buffer[pos++] = hexCharacters[(ch >>>  4) & 0xF];
                buffer[pos++] = hexCharacters[ ch         & 0xF];
                mangled = true;
            } else if (ch == ESCAPE) {
                buffer[pos++] = ESCAPE;
                buffer[pos++] = ESCAPE;
                mangled = true;
            } else {
                buffer[pos++] = ch;
            }
        }

        return mangled ? new String(Arrays.copyOf(buffer, pos)) : null;
    }

    /**
     * Convert a character to a hexadecimal digit. Assumes [0-9A-F].
     * @param ch Character to convert.
     * @return Hexadecimal digit.
     */
    private static int fromHex(final char ch) {
        return ch >= hexBase ? ch - hexBase + 10 : ch - '0';
    }

    /**
     * Unmangles a name. Assumes name mangled by this package.
     * @param name Mangled name.
     * @return Unmangled name.
     */
    public static String unmangle(final String name) {
        final int length = name.length();
        char[] buffer = new char[length];
        int pos = 0;

        for (int i = 0; i < length; ) {
            char ch = name.charAt(i++);

            if (ch == ESCAPE) {
                final char ch0 = name.charAt(i++);

                if (ch0 == ESCAPE) {
                    // pass thru
                } else {
                     final char ch1 = name.charAt(i++);
                     final char ch2 = name.charAt(i++);
                     final char ch3 = name.charAt(i++);

                     ch = (char)(fromHex(ch0) << 12 | fromHex(ch1) << 8 | fromHex(ch2) << 4 | fromHex(ch3));
                }
            }

            buffer[pos++] = ch;
        }

        buffer = Arrays.copyOf(buffer, pos);

        return new String(buffer);
    }
}

/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jshell;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Within a String, mask code comments and ignored modifiers (within context).
 *
 * @author Robert Field
 */
class MaskCommentsAndModifiers {

    private final static Set<String> IGNORED_MODIFERS =
            Stream.of( "public", "protected", "private", "static", "final" )
                    .collect( Collectors.toSet() );

    private final StringBuilder sbCleared = new StringBuilder();
    private final StringBuilder sbMask = new StringBuilder();
    private final String str;
    private final int length;
    private final boolean maskModifiers;
    private int next = 0;
    private boolean wasMasked = false;
    private boolean inside = false;

    @SuppressWarnings("empty-statement")
    public MaskCommentsAndModifiers(String s, boolean maskModifiers) {
        this.str = s;
        this.length = s.length();
        this.maskModifiers = maskModifiers;
        do { } while (next());
    }

    public String cleared() {
        return sbCleared.toString();
    }

    public String mask() {
        return sbMask.toString();
    }

    public boolean wasMasked() {
        return wasMasked;
    }

    /**
     * Read the next character
     */
    private int read() {
        if (next >= length) {
            return -1;
        }
        return str.charAt(next++);
    }

    private void write(StringBuilder sb, int ch) {
        sb.append((char)ch);
    }

    private void write(int ch) {
        write(sbCleared, ch);
        write(sbMask, Character.isWhitespace(ch) ? ch : ' ');
    }

    private void writeMask(int ch) {
        wasMasked = true;
        write(sbMask, ch);
        write(sbCleared, Character.isWhitespace(ch) ? ch : ' ');
    }

    private void write(CharSequence s) {
        for (int cp : s.chars().toArray()) {
            write(cp);
        }
    }

    private void writeMask(CharSequence s) {
        for (int cp : s.chars().toArray()) {
            writeMask(cp);
        }
    }

    private boolean next() {
        return next(read());
    }

    private boolean next(int c) {
        if (c < 0) {
            return false;
        }

        if (c == '\'' || c == '"') {
            inside = true;
            write(c);
            int match = c;
            c = read();
            while (c != match) {
                if (c < 0) {
                    return false;
                }
                if (c == '\n' || c == '\r') {
                    write(c);
                    return true;
                }
                if (c == '\\') {
                    write(c);
                    c = read();
                }
                write(c);
                c = read();
            }
            write(c);
            return true;
        }

        if (c == '/') {
            c = read();
            if (c == '*') {
                writeMask('/');
                writeMask(c);
                int prevc = 0;
                while ((c = read()) != '/' || prevc != '*') {
                    if (c < 0) {
                        return false;
                    }
                    writeMask(c);
                    prevc = c;
                }
                writeMask(c);
                return true;
            } else if (c == '/') {
                writeMask('/');
                writeMask(c);
                while ((c = read()) != '\n' && c != '\r') {
                    if (c < 0) {
                        return false;
                    }
                    writeMask(c);
                }
                writeMask(c);
                return true;
            } else {
                inside = true;
                write('/');
                // read character falls through
            }
        }

        if (Character.isJavaIdentifierStart(c)) {
            if (maskModifiers && !inside) {
                StringBuilder sb = new StringBuilder();
                do {
                    write(sb, c);
                    c = read();
                } while (Character.isJavaIdentifierPart(c));
                String id = sb.toString();
                if (IGNORED_MODIFERS.contains(id)) {
                    writeMask(sb);
                } else {
                    write(sb);
                    if (id.equals("import")) {
                        inside = true;
                    }
                }
                return next(c); // recurse to handle left-over character
            }
        } else if (!Character.isWhitespace(c)) {
            inside = true;
        }

        if (c < 0) {
            return false;
        }
        write(c);
        return true;
    }
}

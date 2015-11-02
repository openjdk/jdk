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

/**
 * Implements the name mangling and demangling as specified by John Rose's
 * <a href="https://blogs.oracle.com/jrose/entry/symbolic_freedom_in_the_vm"
 * target="_blank">"Symbolic Freedom in the VM"</a> article. Normally, you would
 * mangle the names in the call sites as you're generating bytecode, and then
 * demangle them when you receive them in bootstrap methods.
 */
public final class NameCodec {
    private static final char ESCAPE_CHAR = '\\';
    private static final char EMPTY_ESCAPE = '=';
    /**
     * Canonical encoding for the empty name.
     */
    public static final String EMPTY_NAME = new String(new char[] { ESCAPE_CHAR, EMPTY_ESCAPE });
    private static final char EMPTY_CHAR = 0xFEFF;

    private static final int MIN_ENCODING = '$';
    private static final int MAX_ENCODING = ']';
    private static final char[] ENCODING = new char[MAX_ENCODING - MIN_ENCODING + 1];
    private static final int MIN_DECODING = '!';
    private static final int MAX_DECODING = '}';
    private static final char[] DECODING = new char[MAX_DECODING - MIN_DECODING + 1];

    static {
        addEncoding('/', '|');
        addEncoding('.', ',');
        addEncoding(';', '?');
        addEncoding('$', '%');
        addEncoding('<', '^');
        addEncoding('>', '_');
        addEncoding('[', '{');
        addEncoding(']', '}');
        addEncoding(':', '!');
        addEncoding('\\', '-');
        DECODING[EMPTY_ESCAPE - MIN_DECODING] = EMPTY_CHAR;
    }

    private NameCodec() {
    }

    /**
     * Encodes ("mangles") an unencoded symbolic name.
     * @param name the symbolic name to mangle
     * @return the mangled form of the symbolic name.
     */
    public static String encode(final String name) {
        final int l = name.length();
        if(l == 0) {
            return EMPTY_NAME;
        }
        StringBuilder b = null;
        int lastEscape = -1;
        for(int i = 0; i < l; ++i) {
            final int encodeIndex = name.charAt(i) - MIN_ENCODING;
            if(encodeIndex >= 0 && encodeIndex < ENCODING.length) {
                final char e = ENCODING[encodeIndex];
                if(e != 0) {
                    if(b == null) {
                        b = new StringBuilder(name.length() + 3);
                        if(name.charAt(0) != ESCAPE_CHAR && i > 0) {
                            b.append(EMPTY_NAME);
                        }
                        b.append(name, 0, i);
                    } else {
                        b.append(name, lastEscape + 1, i);
                    }
                    b.append(ESCAPE_CHAR).append(e);
                    lastEscape = i;
                }
            }
        }
        if(b == null) {
            return name;
        }
        assert lastEscape != -1;
        b.append(name, lastEscape + 1, l);
        return b.toString();
    }

    /**
     * Decodes ("demangles") an encoded symbolic name.
     * @param name the symbolic name to demangle
     * @return the demangled form of the symbolic name.
     */
    public static String decode(final String name) {
        if(name.isEmpty() || name.charAt(0) != ESCAPE_CHAR) {
            return name;
        }
        final int l = name.length();
        if(l == 2 && name.charAt(1) == EMPTY_CHAR) {
            return "";
        }
        final StringBuilder b = new StringBuilder(name.length());
        int lastEscape = -2;
        int lastBackslash = -1;
        for(;;) {
            final int nextBackslash = name.indexOf(ESCAPE_CHAR, lastBackslash + 1);
            if(nextBackslash == -1 || nextBackslash == l - 1) {
                break;
            }
            final int decodeIndex = name.charAt(nextBackslash + 1) - MIN_DECODING;
            if(decodeIndex >= 0 && decodeIndex < DECODING.length) {
                final char d = DECODING[decodeIndex];
                if(d == EMPTY_CHAR) {
                    // "\=" is only valid at the beginning of a mangled string
                    if(nextBackslash == 0) {
                        lastEscape = 0;
                    }
                } else if(d != 0) {
                    b.append(name, lastEscape + 2, nextBackslash).append(d);
                    lastEscape = nextBackslash;
                }
            }
            lastBackslash = nextBackslash;
        }
        b.append(name, lastEscape + 2, l);
        return b.toString();
    }

    private static void addEncoding(final char from, final char to) {
        ENCODING[from - MIN_ENCODING] = to;
        DECODING[to - MIN_DECODING] = from;
    }
}

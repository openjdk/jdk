/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.util;

import java.util.HashMap;

/**
 * Straightforward implementation of {@link Name.Table} using {@link String}s.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class StringNameTable extends Name.Table {

    private final HashMap<String, Name> nameMap;
    private final boolean intern;

// Factory

    public static StringNameTable create(Names names, boolean intern) {
        return new StringNameTable(names, intern);
    }

// Constructors

    public StringNameTable(Names names, boolean intern) {
        this(names, 0x8000, intern);
    }

    public StringNameTable(Names names, int initialCapacity, boolean intern) {
        super(names);
        this.nameMap = new HashMap<>(initialCapacity);
        this.intern = intern;
    }

// Name.Table

    @Override
    public Name fromString(String string) {
        return this.nameMap.computeIfAbsent(string, s -> new NameImpl(this, intern ? s.intern() : s));
    }

    @Override
    public Name fromChars(char[] buf, int off, int len) {
        return this.fromString(new String(buf, off, len));
    }

    @Override
    public Name fromUtf(byte[] buf, int off, int len, Convert.Validation validation) throws InvalidUtfException {
        return this.fromString(Convert.utf2string(buf, off, len, validation));
    }

    @Override
    public void dispose() {
        this.nameMap.clear();
    }

// NameImpl

    private static final class NameImpl extends Name {

        private final String string;

    // Constructor

        NameImpl(StringNameTable table, String string) {
            super(table);
            this.string = string;
        }

        @Override
        public String toString() {
            return string;
        }

        @Override
        public boolean contentEquals(CharSequence cs) {
            return string.contentEquals(cs);
        }

        @Override
        protected boolean nameEquals(Name that) {
            return ((NameImpl)that).string.equals(string);
        }

        @Override
        public int hashCode() {
            return string.hashCode();
        }

        @Override
        public int getUtf8Length() {
            int slen = string.length();
            int extra = 0;
            for (int i = 0; i < slen; i++) {
                int ch = string.charAt(i);
                if (ch > 0x007f || ch == 0x0000) {
                    extra++;
                    if (ch > 0x07ff)
                        extra++;
                }
            }
            return slen + extra;
        }

        @Override
        public void getUtf8Bytes(byte buf[], int off) {
            Convert.chars2utf(string.toCharArray(), 0, buf, off, string.length());
        }
    }
}

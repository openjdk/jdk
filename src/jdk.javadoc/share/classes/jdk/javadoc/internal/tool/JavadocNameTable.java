/*
 * Copyright (c) 2001, 2021, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.tool;

import java.lang.ref.SoftReference;
import java.util.WeakHashMap;

import com.sun.tools.javac.util.Convert;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;

/**
 * A character-based name table, optimizing character access over modified UTF-8
 * byte access.
 *
 * The table is instantiated indirectly, by {@link JavadocNames#preRegister}.
 *
 * Note that {@link Name#subName(int,int)} cannot be made character-oriented
 * because the {@code start} and {@code end} parameters are byte offsets in the
 * modified UTF-8 byte array.  A more advanced solution would be to add more
 * flexible methods to {@code Name}, allowing {@code subName} to be side-lined.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class JavadocNameTable extends Name.Table {
    private WeakHashMap<String, Name> map;

    public static Name.Table create(Names names) {
        return new JavadocNameTable(names);
    }

    public JavadocNameTable(Names names) {
        this(names, 0x8000);
    }

    @Override
    public Name fromChars(char[] cs, int start, int len) {
        return fromString(new String(cs, start, len));
    }

    @Override
    public Name fromUtf(byte[] cs, int start, int len) {
        return fromString(Convert.utf2string(cs, start, len));
    }

    @Override
    public Name fromString(String s) {
        return map.computeIfAbsent(s, s_ -> new NameImpl(this, s_));
    }

    @Override
    public void dispose() {
        map = null;
    }

    JavadocNameTable(Names names, int hashSize) {
        super(names);
        map = new WeakHashMap<>(hashSize);
    }

    /**
     * A character-based impl of Name that caches a byte array as needed.
     */
    private static class NameImpl extends Name {
        private final String string;
        private SoftReference<byte[]> refBytes = null;

        NameImpl(JavadocNameTable table, String s) {
            super(table);
            this.string = s;
        }

        @Override // Name
        public Name append(Name n) {
            return table.fromString(string + n.toString());
        }

        @Override // Name
        public Name append(char c, Name n) {
            return table.fromString(string + c + n.toString());
        }

        @Override // Name
        public int compareTo(Name other) {
            if (other instanceof NameImpl ni) {
                return string.compareTo(ni.string);
            } else {
                throw new IllegalArgumentException(other.getClass() + ": " + other);
            }
        }

        @Override // Name
        public boolean contentEquals(CharSequence cs) {
            return string.contentEquals(cs);
        }

        @Override // Name
        public boolean isEmpty() {
            return string.isEmpty();
        }

        @Override // Name
        public boolean startsWith(Name prefix) {
            return string.startsWith(prefix.toString());
        }

        /**
         * This method is only used to support the default implementations of {@code equals}
         * and {@code compareTo}, which are both overridden here to delegate to the equivalent
         * methods on {@code String}.
         *
         * @return an arbitrary index: in this case, the hashCode
         */
        @Override // Name
        public int getIndex() {
            return string.hashCode();
        }

        @Override // Name
        public int getByteLength() {
            return getByteArray().length;
        }

        @Override // Name
        public byte getByteAt(int i) {
            return getByteArray()[i];
        }

        @Override // Name
        public byte[] getByteArray() {
            byte[] bytes = refBytes == null ? null : refBytes.get();
            if (bytes == null) {
                refBytes = new SoftReference<>(bytes = Convert.string2utf(string));
            }
            return bytes;
        }

        @Override // Name
        public int getByteOffset() {
            return 0;
        }

        @Override // Object
        public boolean equals(Object other) {
            return (other instanceof NameImpl ni) && string.equals(ni.string);
        }

        @Override // Object
        public int hashCode() {
            return string.hashCode();
        }

        @Override // Object
        public String toString() {
            return string;
        }
    }
}

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

/**
 * Support superclass for {@link Name.Table} implementations that store
 * names as Modified UTF-8 data in {@code byte[]} arrays.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public abstract class Utf8NameTable extends Name.Table {

    /** Constructor.
     *  @param names The main name table
     */
    protected Utf8NameTable(Names names) {
        super(names);
    }

// Utility methods

    /** Generate a hash value for a subarray.
     */
    protected static int hashValue(byte[] buf, int off, int len) {
        int hash = 0;
        while (len-- > 0)
            hash = (hash << 5) - hash + buf[off++];
        return hash;
    }

    /** Compare two subarrays.
     */
    protected static boolean equals(byte[] buf1, int off1, byte[] buf2, int off2, int len) {
        while (len-- > 0) {
            if (buf1[off1++] != buf2[off2++])
                return false;
        }
        return true;
    }

// NameImpl

    protected abstract static class NameImpl extends Name {

    // Constructor

        NameImpl(Utf8NameTable table) {
            super(table);
        }

    // Subclass hooks

        /**
         * Get the {@code byte[]} array in which the Modified UTF-8 data is stored.
         */
        protected abstract byte[] getByteData();

        /**
         * Get the Modified UTF-8 data offset into the byte array.
         */
        protected abstract int getByteOffset();

        /**
         * Get the Modified UTF-8 data length in the byte array.
         */
        protected abstract int getByteLength();

        /**
         * Get a unique index corresponding to this instance.
         */
        protected abstract int getNameIndex();

        @Override
        protected boolean nameEquals(Name that) {
            return ((NameImpl)that).getNameIndex() == getNameIndex();
        }

    // CharSequence

        @Override
        public int length() {
            return Convert.utfNumChars(getByteData(), getByteOffset(), getByteLength());
        }

        @Override
        public String toString() {
            try {
                return Convert.utf2string(getByteData(), getByteOffset(), getByteLength(), Convert.Validation.NONE);
            } catch (InvalidUtfException e) {
                throw new AssertionError("invalid UTF8 data", e);
            }
        }

    // javax.lang.model.element.Name

        @Override
        public int hashCode() {
            return getNameIndex();
        }

    // Comparable

        @Override
        public int compareTo(Name name0) {
            // While most operations on Name that take a Name as an argument expect the argument
            // to come from the same table, in many cases, including here, that is not strictly
            // required. Moreover, javac.util.Name implements javax.lang.model.element.Name,
            // which extends CharSequence, which provides
            //   static int compare(CharSequence cs1, CharSequence cs2)
            // which ends up calling to this method via the Comparable<Object> interface
            // and a bridge method when the two arguments have the same class.
            // Therefore, for this method, we relax "same table", and delegate to the more
            // general super method if necessary.
            if (!(name0 instanceof NameImpl name)) {
                return super.compareTo(name0);
            }
            byte[] buf1 = getByteData();
            byte[] buf2 = name.getByteData();
            int off1 = getByteOffset();
            int off2 = name.getByteOffset();
            int len1 = getByteLength();
            int len2 = name.getByteLength();
            while (len1 > 0 && len2 > 0) {
                int val1 = buf1[off1++] & 0xff;
                int val2 = buf2[off2++] & 0xff;
                if (val1 == 0xc0 && (buf1[off1] & 0x3f) == 0) {
                    val1 = 0;       // char 0x0000 encoded in two bytes
                    off1++;
                    len1--;
                }
                if (val2 == 0xc0 && (buf2[off2] & 0x3f) == 0) {
                    val2 = 0;       // char 0x0000 encoded in two bytes
                    off2++;
                    len2--;
                }
                int diff = val1 - val2;
                if (diff != 0)
                    return diff;
                len1--;
                len2--;
            }
            return len1 > 0 ? 1 : len2 > 0 ? -1 : 0;
        }

    // Name

        @Override
        public Name append(Name name0) {
            NameImpl name = (NameImpl)name0;
            Assert.check(name.table == table);
            byte[] buf1 = getByteData();
            byte[] buf2 = name.getByteData();
            int off1 = getByteOffset();
            int off2 = name.getByteOffset();
            int len1 = getByteLength();
            int len2 = name.getByteLength();
            byte[] result = new byte[len1 + len2];
            System.arraycopy(buf1, off1, result, 0, len1);
            System.arraycopy(buf2, off2, result, len1, len2);
            try {
                return table.fromUtf(result, 0, result.length, Convert.Validation.NONE);
            } catch (InvalidUtfException e) {
                throw new AssertionError("invalid UTF8 data", e);
            }
        }

        @Override
        public Name append(char ch, Name name0) {
            Assert.check((ch & ~0x7f) == 0);
            NameImpl name = (NameImpl)name0;
            Assert.check(name.table == table);
            byte[] buf1 = getByteData();
            byte[] buf2 = name.getByteData();
            int off1 = getByteOffset();
            int off2 = name.getByteOffset();
            int len1 = getByteLength();
            int len2 = name.getByteLength();
            byte[] result = new byte[len1 + 1 + len2];
            System.arraycopy(buf1, off1, result, 0, len1);
            result[len1] = (byte)ch;
            System.arraycopy(buf2, off2, result, len1 + 1, len2);
            try {
                return table.fromUtf(result, 0, result.length, Convert.Validation.NONE);
            } catch (InvalidUtfException e) {
                throw new AssertionError("invalid UTF8 data", e);
            }
        }

        @Override
        public int lastIndexOfAscii(char ch) {
            Assert.check((ch & ~0x7f) == 0);

            // Find the last *byte* index of 'ch'
            byte b = (byte)ch;
            byte[] buf = getByteData();
            int off = getByteOffset();
            int len = getByteLength();
            int pos = len - 1;
            while (pos >= 0 && buf[off + pos] != b)
                pos--;

            // Not found, or index is zero?
            if (pos <= 0)
                return pos;

            // Convert the byte index into a char index
            return Convert.utfNumChars(buf, off, pos);
        }

        @Override
        public boolean startsWith(Name prefix0) {
            NameImpl prefix = (NameImpl)prefix0;
            Assert.check(prefix.table == table);
            int thisLen = getByteLength();
            int prefLen = prefix.getByteLength();
            if (thisLen < prefLen)
                return false;
            byte[] thisData = getByteData();
            byte[] prefData = prefix.getByteData();
            int thisOff = getByteOffset() + prefLen;
            int prefOff = prefix.getByteOffset() + prefLen;
            while (prefLen-- > 0) {
                if (thisData[--thisOff] != prefData[--prefOff])
                    return false;
            }
            return true;
        }

        @Override
        public int getUtf8Length() {
            return getByteLength();
        }

        @Override
        public void getUtf8Bytes(byte[] buf, int off) {
            System.arraycopy(getByteData(), getByteOffset(), buf, off, getByteLength());
        }
    }
}

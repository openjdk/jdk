/*
 * Copyright (c) 1997, 2008, Oracle and/or its affiliates. All rights reserved.
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
 * The Original Code is HAT. The Initial Developer of the
 * Original Code is Bill Foote, with contributions from others
 * at JavaSoft/Sun.
 */

package com.sun.tools.hat.internal.model;

import java.io.IOException;
import com.sun.tools.hat.internal.parser.ReadBuffer;

/*
 * Base class for lazily read Java heap objects.
 */
public abstract class JavaLazyReadObject extends JavaHeapObject {

    // file offset from which this object data starts
    private final long offset;

    protected JavaLazyReadObject(long offset) {
        this.offset = offset;
    }

    public final int getSize() {
        return getValueLength() + getClazz().getMinimumObjectSize();
    }

    protected final long getOffset() {
        return offset;
    }

    // return the length of the data for this object
    protected final int getValueLength() {
        try {
            return readValueLength();
        } catch (IOException exp) {
            System.err.println("lazy read failed at offset " + offset);
            exp.printStackTrace();
            return 0;
        }
    }

    // get this object's content as byte array
    protected final byte[] getValue() {
        try {
            return readValue();
        } catch (IOException exp) {
            System.err.println("lazy read failed at offset " + offset);
            exp.printStackTrace();
            return Snapshot.EMPTY_BYTE_ARRAY;
        }
    }

    // get ID of this object
    public final long getId() {
        try {
            ReadBuffer buf = getClazz().getReadBuffer();
            int idSize = getClazz().getIdentifierSize();
            if (idSize == 4) {
                return ((long)buf.getInt(offset)) & Snapshot.SMALL_ID_MASK;
            } else {
                return buf.getLong(offset);
            }
        } catch (IOException exp) {
            System.err.println("lazy read failed at offset " + offset);
            exp.printStackTrace();
            return -1;
        }
    }

    protected abstract int readValueLength() throws IOException;
    protected abstract byte[] readValue() throws IOException;

    // make Integer or Long for given object ID
    protected static Number makeId(long id) {
        if ((id & ~Snapshot.SMALL_ID_MASK) == 0) {
            return new Integer((int)id);
        } else {
            return id;
        }
    }

    // get ID as long value from Number
    protected static long getIdValue(Number num) {
        long id = num.longValue();
        if (num instanceof Integer) {
            id &= Snapshot.SMALL_ID_MASK;
        }
        return id;
    }

    // read object ID from given index from given byte array
    protected final long objectIdAt(int index, byte[] data) {
        int idSize = getClazz().getIdentifierSize();
        if (idSize == 4) {
            return ((long)intAt(index, data)) & Snapshot.SMALL_ID_MASK;
        } else {
            return longAt(index, data);
        }
    }

    // utility methods to read primitive types from byte array
    protected static byte byteAt(int index, byte[] value) {
        return value[index];
    }

    protected static boolean booleanAt(int index, byte[] value) {
        return (value[index] & 0xff) == 0? false: true;
    }

    protected static char charAt(int index, byte[] value) {
        int b1 = ((int) value[index++] & 0xff);
        int b2 = ((int) value[index++] & 0xff);
        return (char) ((b1 << 8) + b2);
    }

    protected static short shortAt(int index, byte[] value) {
        int b1 = ((int) value[index++] & 0xff);
        int b2 = ((int) value[index++] & 0xff);
        return (short) ((b1 << 8) + b2);
    }

    protected static int intAt(int index, byte[] value) {
        int b1 = ((int) value[index++] & 0xff);
        int b2 = ((int) value[index++] & 0xff);
        int b3 = ((int) value[index++] & 0xff);
        int b4 = ((int) value[index++] & 0xff);
        return ((b1 << 24) + (b2 << 16) + (b3 << 8) + b4);
    }

    protected static long longAt(int index, byte[] value) {
        long val = 0;
        for (int j = 0; j < 8; j++) {
            val = val << 8;
            int b = ((int)value[index++]) & 0xff;
            val |= b;
        }
        return val;
    }

    protected static float floatAt(int index, byte[] value) {
        int val = intAt(index, value);
        return Float.intBitsToFloat(val);
    }

    protected static double doubleAt(int index, byte[] value) {
        long val = longAt(index, value);
        return Double.longBitsToDouble(val);
    }
}

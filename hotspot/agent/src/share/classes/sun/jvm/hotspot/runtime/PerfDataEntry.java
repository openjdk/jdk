/*
 * Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.runtime;

import java.util.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.oops.*;
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.utilities.*;

public class PerfDataEntry extends VMObject {
    private static JIntField  entryLengthField;
    private static JIntField  nameOffsetField;
    private static JIntField  vectorLengthField;
    private static JByteField dataTypeField;
    private static JByteField flagsField;
    private static JByteField dataUnitsField;
    private static JByteField dataVariabilityField;
    private static JIntField  dataOffsetField;

    static {
        VM.registerVMInitializedObserver(new Observer() {
                public void update(Observable o, Object data) {
                    initialize(VM.getVM().getTypeDataBase());
                }
            });
    }

    private static synchronized void initialize(TypeDataBase db) {
        Type type = db.lookupType("PerfDataEntry");
        entryLengthField = type.getJIntField("entry_length");
        nameOffsetField = type.getJIntField("name_offset");
        vectorLengthField = type.getJIntField("vector_length");
        dataTypeField = type.getJByteField("data_type");
        flagsField = type.getJByteField("flags");
        dataUnitsField = type.getJByteField("data_units");
        dataVariabilityField = type.getJByteField("data_variability");
        dataOffsetField = type.getJIntField("data_offset");
    }

    public PerfDataEntry(Address addr) {
        super(addr);
    }

    // Accessors

    public int entryLength() {
        return (int) entryLengthField.getValue(addr);
    }

    public int nameOffset() {
        return (int) nameOffsetField.getValue(addr);
    }

    public int vectorLength() {
        return (int) vectorLengthField.getValue(addr);
    }

    // returns one of the constants in BasicType class
    public int dataType() {
        char ch = (char) (byte) dataTypeField.getValue(addr);
        return BasicType.charToType(ch);
    }

    public byte flags() {
        return (byte) flagsField.getValue(addr);
    }

    public boolean supported() {
        return (flags() & 0x1) != 0;
    }

    // NOTE: Keep this in sync with PerfData::Units enum in VM code
    public interface PerfDataUnits {
        public static final int U_None   = 1;
        public static final int U_Bytes  = 2;
        public static final int U_Ticks  = 3;
        public static final int U_Events = 4;
        public static final int U_String = 5;
        public static final int U_Hertz  = 6;
    }

    // returns one of the constants in PerfDataUnits
    public int dataUnits() {
        return (int) dataUnitsField.getValue(addr);
    }

    // NOTE: Keep this in sync with PerfData::Variability enum in VM code
    public interface PerfDataVariability {
        public static final int V_Constant  = 1;
        public static final int V_Monotonic = 2;
        public static final int V_Variable  = 3;
    }

    // returns one of the constants in PerfDataVariability
    public int dataVariability() {
        return (int) dataVariabilityField.getValue(addr);
    }

    public int dataOffset() {
        return (int) dataOffsetField.getValue(addr);
    }

    public String name() {
        int off = nameOffset();
        return CStringUtilities.getString(addr.addOffsetTo(off));
    }

    public boolean booleanValue() {
        if (Assert.ASSERTS_ENABLED) {
            Assert.that(vectorLength() == 0 &&
                        dataType() == BasicType.tBoolean, "not a boolean");
        }
        return addr.getJBooleanAt(dataOffset());
    }

    public char charValue() {
        if (Assert.ASSERTS_ENABLED) {
            Assert.that(vectorLength() == 0 &&
                        dataType() == BasicType.tChar, "not a char");
        }
        return addr.getJCharAt(dataOffset());
    }

    public byte byteValue() {
        if (Assert.ASSERTS_ENABLED) {
            Assert.that(vectorLength() == 0 &&
                        dataType() == BasicType.tByte, "not a byte");
        }
        return addr.getJByteAt(dataOffset());

    }

    public short shortValue() {
        if (Assert.ASSERTS_ENABLED) {
            Assert.that(vectorLength() == 0 &&
                        dataType() == BasicType.tShort, "not a short");
        }
        return addr.getJShortAt(dataOffset());
    }

    public int intValue() {
        if (Assert.ASSERTS_ENABLED) {
            Assert.that(vectorLength() == 0 &&
                        dataType() == BasicType.tInt, "not an int");
        }
        return addr.getJIntAt(dataOffset());
    }

    public long longValue() {
        if (Assert.ASSERTS_ENABLED) {
            Assert.that(vectorLength() == 0 &&
                        dataType() == BasicType.tLong, "not a long");
        }
        return addr.getJLongAt(dataOffset());
    }

    public float floatValue() {
        if (Assert.ASSERTS_ENABLED) {
            Assert.that(vectorLength() == 0 &&
                        dataType() == BasicType.tFloat, "not a float");
        }
        return addr.getJFloatAt(dataOffset());
    }

    public double doubleValue() {
        if (Assert.ASSERTS_ENABLED) {
            Assert.that(vectorLength() == 0 &&
                        dataType() == BasicType.tDouble, "not a double");
        }
        return addr.getJDoubleAt(dataOffset());
    }

    public boolean[] booleanArrayValue() {
        int len = vectorLength();
        if (Assert.ASSERTS_ENABLED) {
            Assert.that(len > 0 &&
                        dataType() == BasicType.tBoolean, "not a boolean vector");
        }
        boolean[] res = new boolean[len];
        final int off = dataOffset();
        final long size =  getHeap().getBooleanSize();
        for (int i = 0; i < len; i++) {
            res[i] = addr.getJBooleanAt(off + i * size);
        }
        return res;
    }

    public char[] charArrayValue() {
        int len = vectorLength();
        if (Assert.ASSERTS_ENABLED) {
            Assert.that(len > 0 &&
                        dataType() == BasicType.tChar, "not a char vector");
        }
        char[] res = new char[len];
        final int off = dataOffset();
        final long size = getHeap().getCharSize();
        for (int i = 0; i < len; i++) {
            res[i] = addr.getJCharAt(off + i * size);
        }
        return res;
    }

    public byte[] byteArrayValue() {
        int len = vectorLength();
        if (Assert.ASSERTS_ENABLED) {
            Assert.that(len > 0 &&
                        dataType() == BasicType.tByte, "not a byte vector");
        }
        byte[] res = new byte[len];
        final int off = dataOffset();
        final long size = getHeap().getByteSize();
        for (int i = 0; i < len; i++) {
            res[i] = addr.getJByteAt(off + i * size);
        }
        return res;
    }

    public short[] shortArrayValue() {
        int len = vectorLength();
        if (Assert.ASSERTS_ENABLED) {
            Assert.that(len > 0 &&
                        dataType() == BasicType.tShort, "not a short vector");
        }
        short[] res = new short[len];
        final int off = dataOffset();
        final long size = getHeap().getShortSize();
        for (int i = 0; i < len; i++) {
            res[i] = addr.getJShortAt(off + i * size);
        }
        return res;
    }

    public int[] intArrayValue() {
        int len = vectorLength();
        if (Assert.ASSERTS_ENABLED) {
            Assert.that(len > 0 &&
                        dataType() == BasicType.tInt, "not an int vector");
        }
        int[] res = new int[len];
        final int off = dataOffset();
        final long size = getHeap().getIntSize();
        for (int i = 0; i < len; i++) {
            res[i] = addr.getJIntAt(off + i * size);
        }
        return res;
    }

    public long[] longArrayValue() {
        int len = vectorLength();
        if (Assert.ASSERTS_ENABLED) {
            Assert.that(len > 0 &&
                        dataType() == BasicType.tLong, "not a long vector");
        }
        long[] res = new long[len];
        final int off = dataOffset();
        final long size = getHeap().getLongSize();
        for (int i = 0; i < len; i++) {
            res[i] = addr.getJLongAt(off + i * size);
        }
        return res;
    }

    public float[] floatArrayValue() {
        int len = vectorLength();
        if (Assert.ASSERTS_ENABLED) {
            Assert.that(len > 0 &&
                        dataType() == BasicType.tFloat, "not a float vector");
        }
        float[] res = new float[len];
        final int off = dataOffset();
        final long size = getHeap().getFloatSize();
        for (int i = 0; i < len; i++) {
            res[i] = addr.getJFloatAt(off + i * size);
        }
        return res;
    }

    public double[] doubleArrayValue() {
        int len = vectorLength();
        if (Assert.ASSERTS_ENABLED) {
            Assert.that(len > 0 &&
                        dataType() == BasicType.tDouble, "not a double vector");
        }
        double[] res = new double[len];
        final int off = dataOffset();
        final long size = getHeap().getDoubleSize();
        for (int i = 0; i < len; i++) {
            res[i] = addr.getJDoubleAt(off + i * size);
        }
        return res;
    }

    // value as String
    public String valueAsString() {
        int dataType = dataType();
        int len = vectorLength();
        String str = null;
        if (len == 0) { // scalar
            switch (dataType) {
            case BasicType.tBoolean:
                str = Boolean.toString(booleanValue());
                break;
            case BasicType.tChar:
                str = "'" + Character.toString(charValue()) + "'";
                break;
            case BasicType.tByte:
                str = Byte.toString(byteValue());
                break;
            case BasicType.tShort:
                str = Short.toString(shortValue());
                break;
            case BasicType.tInt:
                str = Integer.toString(intValue());
                break;
            case BasicType.tLong:
                str = Long.toString(longValue());
                break;
            case BasicType.tFloat:
                str = Float.toString(floatValue());
                break;
            case BasicType.tDouble:
                str = Double.toString(doubleValue());
                break;
            default:
                str = "<unknown scalar value>";
                break;
            }
        } else { // vector
            switch (dataType) {
            case BasicType.tBoolean: {
                boolean[] res = booleanArrayValue();
                StringBuffer buf = new StringBuffer();
                buf.append('[');
                for (int i = 0; i < res.length; i++) {
                    buf.append(Boolean.toString(res[i]));
                    buf.append(", ");
                }
                buf.append(']');
                str = buf.toString();
                break;
            }

            case BasicType.tChar: {
                // char[] is returned as a String
                str = new String(charArrayValue());
                break;
            }

            case BasicType.tByte: {
                // byte[] is returned as a String
                try {
                    str = new String(byteArrayValue(), "US-ASCII");
                } catch (java.io.UnsupportedEncodingException e) {
                    str = "can't decode string : " + e.getMessage();
                }
                break;
            }

            case BasicType.tShort: {
                short[] res = shortArrayValue();
                StringBuffer buf = new StringBuffer();
                buf.append('[');
                for (int i = 0; i < res.length; i++) {
                    buf.append(Short.toString(res[i]));
                    buf.append(", ");
                }
                buf.append(']');
                str = buf.toString();
                break;
            }

            case BasicType.tInt: {
                int[] res = intArrayValue();
                StringBuffer buf = new StringBuffer();
                buf.append('[');
                for (int i = 0; i < res.length; i++) {
                    buf.append(Integer.toString(res[i]));
                    buf.append(", ");
                }
                buf.append(']');
                str = buf.toString();
                break;
            }

            case BasicType.tLong: {
                long[] res = longArrayValue();
                StringBuffer buf = new StringBuffer();
                buf.append('[');
                for (int i = 0; i < res.length; i++) {
                    buf.append(Long.toString(res[i]));
                    buf.append(", ");
                }
                buf.append(']');
                str = buf.toString();
                break;
            }

            case BasicType.tFloat: {
                float[] res = floatArrayValue();
                StringBuffer buf = new StringBuffer();
                buf.append('[');
                for (int i = 0; i < res.length; i++) {
                    buf.append(Float.toString(res[i]));
                    buf.append(", ");
                }
                buf.append(']');
                str = buf.toString();
                break;
            }

            case BasicType.tDouble: {
                double[] res = doubleArrayValue();
                StringBuffer buf = new StringBuffer();
                buf.append('[');
                for (int i = 0; i < res.length; i++) {
                    buf.append(Double.toString(res[i]));
                    buf.append(", ");
                }
                buf.append(']');
                str = buf.toString();
                break;
            }

            default:
                str = "<unknown vector value>";
                break;
            }
        }

        // add units
        switch (dataUnits()) {
        case PerfDataUnits.U_None:
            break;
        case PerfDataUnits.U_Bytes:
            str += " byte(s)";
            break;
        case PerfDataUnits.U_Ticks:
            str += " tick(s)";
            break;
        case PerfDataUnits.U_Events:
            str += " event(s)";
            break;
        case PerfDataUnits.U_String:
            break;
        case PerfDataUnits.U_Hertz:
            str += " Hz";
            break;
        }

        return str;
    }

    // -- Internals only below this point
    private ObjectHeap getHeap() {
        return VM.getVM().getObjectHeap();
    }
}

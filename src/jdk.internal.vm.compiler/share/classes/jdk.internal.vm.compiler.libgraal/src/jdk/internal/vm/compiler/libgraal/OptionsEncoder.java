/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
 */


package jdk.internal.vm.compiler.libgraal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Facilities for encoding/decoding a set of options to/from a byte array.
 */
public final class OptionsEncoder {

    private OptionsEncoder() {
    }

    /**
     * Determines if {@code value} is supported by {@link #encode(Map)}.
     */
    public static boolean isValueSupported(Object value) {
        if (value == null) {
            return false;
        }
        Class<?> valueClass = value.getClass();
        return valueClass == Boolean.class ||
                        valueClass == Byte.class ||
                        valueClass == Short.class ||
                        valueClass == Character.class ||
                        valueClass == Integer.class ||
                        valueClass == Long.class ||
                        valueClass == Float.class ||
                        valueClass == Double.class ||
                        valueClass == String.class ||
                        value.getClass().isEnum();
    }

    /**
     * Encodes {@code options} into a byte array.
     *
     * @throws IllegalArgumentException if any value in {@code options} is not
     *             {@linkplain #isValueSupported(Object) supported}
     */
    public static byte[] encode(final Map<String, Object> options) {
        try (ByteArrayOutputStream baout = new ByteArrayOutputStream()) {
            try (DataOutputStream out = new DataOutputStream(baout)) {
                out.writeInt(options.size());
                for (Map.Entry<String, Object> e : options.entrySet()) {
                    final String key = e.getKey();
                    out.writeUTF(key);
                    final Object value = e.getValue();
                    final Class<?> valueClz = value.getClass();
                    if (valueClz == Boolean.class) {
                        out.writeByte('Z');
                        out.writeBoolean((Boolean) value);
                    } else if (valueClz == Byte.class) {
                        out.writeByte('B');
                        out.writeByte((Byte) value);
                    } else if (valueClz == Short.class) {
                        out.writeByte('S');
                        out.writeShort((Short) value);
                    } else if (valueClz == Character.class) {
                        out.writeByte('C');
                        out.writeChar((Character) value);
                    } else if (valueClz == Integer.class) {
                        out.writeByte('I');
                        out.writeInt((Integer) value);
                    } else if (valueClz == Long.class) {
                        out.writeByte('J');
                        out.writeLong((Long) value);
                    } else if (valueClz == Float.class) {
                        out.writeByte('F');
                        out.writeFloat((Float) value);
                    } else if (valueClz == Double.class) {
                        out.writeByte('D');
                        out.writeDouble((Double) value);
                    } else if (valueClz == String.class) {
                        out.writeByte('U');
                        out.writeUTF((String) value);
                    } else if (valueClz.isEnum()) {
                        out.writeByte('U');
                        out.writeUTF(((Enum<?>) value).name());
                    } else {
                        throw new IllegalArgumentException(String.format("Key: %s, Value: %s, Value type: %s", key, value, valueClz));
                    }
                }
            }
            return baout.toByteArray();
        } catch (IOException ioe) {
            throw new IllegalArgumentException(ioe);
        }
    }

    /**
     * Decodes {@code input} into a name/value map.
     *
     * @throws IllegalArgumentException if {@code input} cannot be decoded
     */
    public static Map<String, Object> decode(byte[] input) {
        Map<String, Object> res = new LinkedHashMap<>();
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(input))) {
            final int size = in.readInt();
            for (int i = 0; i < size; i++) {
                final String key = in.readUTF();
                final Object value;
                final byte type = in.readByte();
                switch (type) {
                    case 'Z':
                        value = in.readBoolean();
                        break;
                    case 'B':
                        value = in.readByte();
                        break;
                    case 'S':
                        value = in.readShort();
                        break;
                    case 'C':
                        value = in.readChar();
                        break;
                    case 'I':
                        value = in.readInt();
                        break;
                    case 'J':
                        value = in.readLong();
                        break;
                    case 'F':
                        value = in.readFloat();
                        break;
                    case 'D':
                        value = in.readDouble();
                        break;
                    case 'U':
                        value = in.readUTF();
                        break;
                    default:
                        throw new IllegalArgumentException("Unsupported value type: " + Integer.toHexString(type));
                }
                res.put(key, value);
            }
            if (in.available() != 0) {
                throw new IllegalArgumentException(in.available() + " undecoded bytes");
            }
        } catch (IOException ioe) {
            throw new IllegalArgumentException(ioe);
        }
        return res;
    }
}

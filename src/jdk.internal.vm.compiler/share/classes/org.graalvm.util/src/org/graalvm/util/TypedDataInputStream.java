/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.util;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * A stream that can read (trivial) values using their in-band data type information, intended for
 * use with {@link TypedDataOutputStream}.
 */
public class TypedDataInputStream extends DataInputStream {
    public TypedDataInputStream(InputStream in) {
        super(in);
    }

    /**
     * Reads a single value, using the data type encoded in the stream.
     *
     * @return The read value, such as a boxed primitive or a {@link String}.
     * @exception IOException in case of an I/O error.
     */
    public Object readTypedValue() throws IOException {
        Object value;
        final byte type = readByte();
        switch (type) {
            case 'Z':
                value = readBoolean();
                break;
            case 'B':
                value = readByte();
                break;
            case 'S':
                value = readShort();
                break;
            case 'C':
                value = readChar();
                break;
            case 'I':
                value = readInt();
                break;
            case 'J':
                value = readLong();
                break;
            case 'F':
                value = readFloat();
                break;
            case 'D':
                value = readDouble();
                break;
            case 'U':
                int len = readInt();
                byte[] bytes = new byte[len];
                readFully(bytes);
                value = new String(bytes, StandardCharsets.UTF_8);
                break;
            default:
                throw new IOException("Unsupported type: " + Integer.toHexString(type));
        }
        return value;
    }
}

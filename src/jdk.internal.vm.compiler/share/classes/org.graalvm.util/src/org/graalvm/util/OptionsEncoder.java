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


package org.graalvm.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
        return TypedDataOutputStream.isValueSupported(value);
    }

    /**
     * Encodes {@code options} into a byte array.
     *
     * @throws IllegalArgumentException if any value in {@code options} is not
     *             {@linkplain #isValueSupported(Object) supported}
     */
    public static byte[] encode(final Map<String, Object> options) {
        try (ByteArrayOutputStream baout = new ByteArrayOutputStream()) {
            try (TypedDataOutputStream out = new TypedDataOutputStream(baout)) {
                out.writeInt(options.size());
                for (Map.Entry<String, Object> e : options.entrySet()) {
                    out.writeUTF(e.getKey());
                    try {
                        out.writeTypedValue(e.getValue());
                    } catch (IllegalArgumentException iae) {
                        throw new IllegalArgumentException(String.format("Key: %s, Value: %s, Value type: %s",
                                        e.getKey(), e.getValue(), e.getValue().getClass()), iae);
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
        try (TypedDataInputStream in = new TypedDataInputStream(new ByteArrayInputStream(input))) {
            final int size = in.readInt();
            for (int i = 0; i < size; i++) {
                final String key = in.readUTF();
                final Object value = in.readTypedValue();
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

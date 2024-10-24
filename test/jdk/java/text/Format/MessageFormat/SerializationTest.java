/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8331446
 * @summary Check correctness of deserialization
 * @run junit SerializationTest
 */

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SerializationTest {

    // Ensure basic correctness of serialization round trip
    @ParameterizedTest
    @MethodSource
    public void serializationRoundTrip(MessageFormat expectedMf)
            throws IOException, ClassNotFoundException {
        byte[] bytes = ser(expectedMf);
        MessageFormat actualMf = (MessageFormat) deSer(bytes);
        assertEquals(expectedMf, actualMf);
    }

    // Various valid MessageFormats
    private static Stream<MessageFormat> serializationRoundTrip() {
        return Stream.of(
                // basic pattern
                new MessageFormat("{0} foo"),
                // Multiple arguments
                new MessageFormat("{0} {1} foo"),
                // duplicate arguments
                new MessageFormat("{0} {0} {1} foo"),
                // Non-ascending arguments
                new MessageFormat("{1} {0} foo"),
                // With locale
                new MessageFormat("{1} {0} foo", Locale.UK),
                // With null locale. (NPE not thrown, if no format defined)
                new MessageFormat("{1} {0} foo", null),
                // With formats
                new MessageFormat("{0,number,short} {0} {1,date,long} foo")
        );
    }

    // Utility method to serialize
    private static byte[] ser(Object obj) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new
                ByteArrayOutputStream();
        ObjectOutputStream oos = new
                ObjectOutputStream(byteArrayOutputStream);
        oos.writeObject(obj);
        return byteArrayOutputStream.toByteArray();
    }

    // Utility method to deserialize
    private static Object deSer(byte[] bytes) throws
            IOException, ClassNotFoundException {
        ByteArrayInputStream byteArrayInputStream = new
                ByteArrayInputStream(bytes);
        ObjectInputStream ois = new
                ObjectInputStream(byteArrayInputStream);
        return ois.readObject();
    }
}

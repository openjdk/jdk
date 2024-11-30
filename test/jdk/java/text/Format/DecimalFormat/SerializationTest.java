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
 * @bug 8327640
 * @summary Check parseStrict correctness for DecimalFormat serialization
 * @run junit/othervm SerializationTest
 */

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.NumberFormat;
import java.text.ParseException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SerializationTest {

    private static final NumberFormat FORMAT = NumberFormat.getInstance();

    @BeforeAll
    public static void mutateFormat() {
        FORMAT.setStrict(true);
    }

    @Test
    public void testSerialization() throws IOException, ClassNotFoundException {
        // Serialize
        serialize("fmt.ser", FORMAT);
        // Deserialize
        deserialize("fmt.ser", FORMAT);
    }

    private void serialize(String fileName, NumberFormat... formats)
            throws IOException {
        try (ObjectOutputStream os = new ObjectOutputStream(
                new FileOutputStream(fileName))) {
            for (NumberFormat fmt : formats) {
                os.writeObject(fmt);
            }
        }
    }

    private static void deserialize(String fileName, NumberFormat... formats)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream os = new ObjectInputStream(
                new FileInputStream(fileName))) {
            for (NumberFormat fmt : formats) {
                NumberFormat obj = (NumberFormat) os.readObject();
                assertEquals(fmt, obj, "Serialized and deserialized"
                        + " objects do not match");

                String badNumber = "fooofooo23foo";
                assertThrows(ParseException.class, () -> fmt.parse(badNumber));
                assertThrows(ParseException.class, () -> obj.parse(badNumber));
            }
        }
    }
}

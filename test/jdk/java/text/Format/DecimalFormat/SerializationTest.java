/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4069754 4067878 4101150 4185761 8327640
 * @library /java/text/testlib
 * @build HexDumpReader
 * @summary Check de-serialization correctness for DecimalFormat. That is, ensure the
 *          behavior for each stream version is correct during de-serialization.
 * @run junit/othervm --add-opens java.base/java.text=ALL-UNNAMED SerializationTest
 */

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.math.RoundingMode;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SerializationTest {

    @Nested // Test that rely on hex dump files that were written from older JDK versions
    class HexDumpTests {

        @Test // See 4101150 and CDF which is the serialized hex dump
        void JDK1_1_4Test() {
            // Reconstruct a 1.1.4 serializable class which has a DF holder
            var cdf = (CheckDecimalFormat) assertDoesNotThrow(
                    () -> deSer("DecimalFormat.114.txt"));
            assertDoesNotThrow(cdf::Update); // Checks format call succeeds
        }

        @Test // See 4185761
        void minMaxDigitsTest() {
            // Reconstructing a DFS stream from an older JDK version
            // The min digits are smaller than the max digits and should fail
            // minint maxint minfrac maxfrac
            // 0x122  0x121   0x124   0x123
            assertEquals("Digit count range invalid",
                    assertThrows(InvalidObjectException.class,
                            () -> deSer("NumberFormat4185761a.ser.txt")).getMessage());
        }

        @Test // See 4185761
        void digitLimitTest() {
            // Reconstructing a DFS stream from an older JDK version
            // The digit values exceed the class invariant limits
            // minint maxint minfrac maxfrac
            // 0x311  0x312   0x313   0x314
            assertEquals("Digit count out of range",
                    assertThrows(InvalidObjectException.class,
                            () -> deSer("NumberFormat4185761b.ser.txt")).getMessage());
        }
    }

    @Nested
    class VersionTests {

        // Version 0 did not have exponential fields and defaulted the value to false
        @Test
        void version0Test() {
            var crafted = new DFBuilder()
                    .setVer(0)
                    .set("useExponentialNotation", true)
                    .build();
            var bytes = ser(crafted);
            var df = assertDoesNotThrow(() -> deSer(bytes));
            // Ensure we do not observe exponential notation form
            assertFalse(df.format(0).contains("E"));
        }

        // Version 1 did not support the affix pattern Strings. Ensure when they
        // are read in from the stream they are not defaulted and remain null.
        @Test
        void version1Test() {
            var crafted = new DFBuilder()
                    .setVer(1)
                    .set("posPrefixPattern", null)
                    .set("posSuffixPattern", null)
                    .set("negPrefixPattern", null)
                    .set("negSuffixPattern", null)
                    .build();
            var bytes = ser(crafted);
            var df = assertDoesNotThrow(() -> deSer(bytes));
            assertNull(readField(df, "posPrefixPattern"));
            assertNull(readField(df, "posSuffixPattern"));
            assertNull(readField(df, "negPrefixPattern"));
            assertNull(readField(df, "negSuffixPattern"));
        }

        // Version 2 did not support the min/max int and frac digits.
        // Ensure the proper defaults are set.
        @Test
        void version2Test() {
            var crafted = new DFBuilder()
                    .setVer(2)
                    .set("maximumIntegerDigits", -1)
                    .set("maximumFractionDigits", -1)
                    .set("minimumIntegerDigits", -1)
                    .set("minimumFractionDigits", -1)
                    .build();
            var bytes = ser(crafted);
            var df = assertDoesNotThrow(() -> deSer(bytes));
            assertEquals(1, df.getMinimumIntegerDigits());
            assertEquals(3, df.getMaximumFractionDigits());
            assertEquals(309, df.getMaximumIntegerDigits());
            assertEquals(0, df.getMinimumFractionDigits());
        }

        // Version 3 did not support rounding mode. Should default to HALF_EVEN
        @Test
        void version3Test() {
            var crafted = new DFBuilder()
                    .setVer(3)
                    .set("roundingMode", RoundingMode.UNNECESSARY)
                    .build();
            var bytes = ser(crafted);
            var df = assertDoesNotThrow(() -> deSer(bytes));
            assertEquals(RoundingMode.HALF_EVEN, df.getRoundingMode());
        }
    }

    // Some invariant checking in DF relies on checking NF fields.
    // Either via NF.readObject() or through super calls in DF.readObject
    @Nested // For all these nested tests, see 4185761
    class NumberFormatTests {

        // Ensure the max integer value invariant is not exceeded
        @Test
        void integerTest() {
            var crafted = new DFBuilder()
                    .setSuper("maximumIntegerDigits", 786)
                    .setSuper("minimumIntegerDigits", 785)
                    .build();
            var bytes = ser(crafted);
            assertEquals("Digit count out of range",
                    assertThrows(InvalidObjectException.class, () -> deSer(bytes)).getMessage());
        }

        // Ensure the max fraction value invariant is not exceeded
        @Test
        void fractionTest() {
            var crafted = new DFBuilder()
                    .setSuper("maximumFractionDigits", 788)
                    .setSuper("minimumFractionDigits", 787)
                    .build();
            var bytes = ser(crafted);
            assertEquals("Digit count out of range",
                    assertThrows(InvalidObjectException.class, () -> deSer(bytes)).getMessage());
        }

        // Ensure the minimum integer digits cannot be greater than the max
        @Test
        void maxMinIntegerTest() {
            var crafted = new DFBuilder()
                    .setSuper("maximumIntegerDigits", 5)
                    .setSuper("minimumIntegerDigits", 6)
                    .build();
            var bytes = ser(crafted);
            assertEquals("Digit count range invalid",
                    assertThrows(InvalidObjectException.class, () -> deSer(bytes)).getMessage());
        }

        // Ensure the minimum fraction digits cannot be greater than the max
        @Test
        void maxMinFractionTest() {
            var crafted = new DFBuilder()
                    .setSuper("maximumFractionDigits", 5)
                    .setSuper("minimumFractionDigits", 6)
                    .build();
            var bytes = ser(crafted);
            assertEquals("Digit count range invalid",
                    assertThrows(InvalidObjectException.class, () -> deSer(bytes)).getMessage());
        }
    }

    // Ensure the serial version is updated to the current after de-serialization.
    @Test
    void versionTest() {
        var bytes = ser(new DFBuilder().setVer(-25).build());
        var df = assertDoesNotThrow(() -> deSer(bytes));
        assertEquals(4, readField(df, "serialVersionOnStream"));
    }

    // Ensure strictness value is read properly when it is set.
    @Test
    void strictnessTest() {
        var crafted = new DecimalFormat();
        crafted.setStrict(true);
        var bytes = ser(crafted);
        var df = assertDoesNotThrow(() -> deSer(bytes));
        assertTrue(df.isStrict());
    }

    // Ensure invalid grouping sizes are corrected to the default invariant.
    @Test
    void groupingSizeTest() {
        var crafted = new DFBuilder()
                .set("groupingSize", (byte) -5)
                .build();
        var bytes = ser(crafted);
        var df = assertDoesNotThrow(() -> deSer(bytes));
        assertEquals(3, df.getGroupingSize());
    }

    // Ensure a de-serialized dFmt does not throw NPE from missing digitList
    // later when formatting. i.e. re-construct the transient digitList field
    @Test // See 4069754, 4067878
    void digitListTest() {
        var crafted = new DecimalFormat();
        var bytes = ser(crafted);
        var df = assertDoesNotThrow(() -> deSer(bytes));
        assertDoesNotThrow(() -> df.format(1));
        assertNotNull(readField(df, "digitList"));
    }

    // Similar to the previous test, but the original regression test
    // which was a failure in DateFormat due to DecimalFormat NPE
    @Test // See 4069754 and 4067878
    void digitListDateFormatTest() {
        var fmt = new FooFormat();
        fmt.now();
        var bytes = ser(fmt);
        var ff = (FooFormat) assertDoesNotThrow(() -> deSer0(bytes));
        assertDoesNotThrow(ff::now);
    }

    static class FooFormat implements Serializable {
        DateFormat dateFormat = DateFormat.getDateInstance();

        public String now() {
            GregorianCalendar calendar = new GregorianCalendar();
            Date t = calendar.getTime();
            return dateFormat.format(t);
        }
    }

// Utilities ----

    // Utility to serialize
    private static byte[] ser(Object obj) {
        return assertDoesNotThrow(() -> {
            try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                 ObjectOutputStream oos = new ObjectOutputStream(byteArrayOutputStream)) {
                oos.writeObject(obj);
                return byteArrayOutputStream.toByteArray();
            }
        }, "Unexpected error during serialization");
    }

    // Utility to deserialize
    private static Object deSer0(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
             ObjectInputStream ois = new ObjectInputStream(byteArrayInputStream)) {
            return ois.readObject();
        }
    }

    // Convenience cast to DF
    private static DecimalFormat deSer(byte[] bytes) throws IOException, ClassNotFoundException {
        return (DecimalFormat) deSer0(bytes);
    }

    // Utility to deserialize from file in hex format
    private static Object deSer(String file) throws IOException, ClassNotFoundException {
        try (InputStream stream = HexDumpReader.getStreamFromHexDump(file);
             ObjectInputStream ois = new ObjectInputStream(stream)) {
            return ois.readObject();
        }
    }

    // Utility to read a private field
    private static Object readField(DecimalFormat df, String name) {
        return assertDoesNotThrow(() -> {
            var field = DecimalFormat.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(df);
        }, "Unexpected error during field reading");
    }

    // Utility class to build instances of DF via reflection
    private static class DFBuilder {

        private final DecimalFormat df;

        private DFBuilder() {
            df = new DecimalFormat();
        }

        private DFBuilder setVer(Object value) {
            return set("serialVersionOnStream", value);
        }

        private DFBuilder setSuper(String field, Object value) {
            return set(df.getClass().getSuperclass(), field, value);
        }

        private DFBuilder set(String field, Object value) {
            return set(df.getClass(), field, value);
        }

        private DFBuilder set(Class<?> clzz, String field, Object value) {
            return assertDoesNotThrow(() -> {
                Field f = clzz.getDeclaredField(field);
                f.setAccessible(true);
                f.set(df, value);
                return this;
            }, "Unexpected error during reflection setting");
        }

        private DecimalFormat build() {
            return df;
        }
    }
}

// Not nested, so that it can be recognized and cast correctly for the 1.1.4 test
class CheckDecimalFormat implements Serializable {
    DecimalFormat _decFormat = (DecimalFormat) NumberFormat.getInstance();
    public String Update() {
        Random r = new Random();
        return _decFormat.format(r.nextDouble());
    }
}

/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8366401
 * @summary Check serialization of DecimalFormatSymbols. That is, ensure the
 *          behavior for each stream version is correct during de-serialization.
 * @run junit/othervm --add-opens java.base/java.text=ALL-UNNAMED DFSSerializationTest
 */

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.text.DecimalFormatSymbols;
import java.util.Currency;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DFSSerializationTest {

    @Nested
    class VersionTests {

        // Ensure correct monetarySeparator and exponential field defaults
        // Reads monetary from decimal, and sets exponential to 'E'
        @Test
        public void version0Test() {
            var crafted = new DFSBuilder()
                    .setVer(0)
                    .set("monetarySeparator", '~')
                    .set("exponential", 'Z')
                    .build();
            var bytes = ser(crafted);
            var dfs = assertDoesNotThrow(() -> deSer(bytes));
            // Check exponential is set to proper default 'E', not 'Z'
            assertEquals('E', readField(dfs, "exponential"));
            // Ensure that mSep is based on dSep, and is not '~'
            assertNotEquals('~', dfs.getMonetaryDecimalSeparator());
            assertEquals(dfs.getDecimalSeparator(), dfs.getMonetaryDecimalSeparator());
        }

        // Version 1 did not have a locale field, and it defaulted to Locale.ROOT.
        // Note that other versions did allow a locale field, which was nullable.
        // E.g. see nullableLocaleTest which does not set locale when it is `null`
        @Test
        public void version1Test() {
            var crafted = new DFSBuilder()
                    .setVer(1)
                    .set("locale", null)
                    .build();
            var bytes = ser(crafted);
            var dfs = assertDoesNotThrow(() -> deSer(bytes));
            assertEquals(Locale.ROOT, dfs.getLocale());
        }

        // Version 2 did not have an exponential separator, and created it via exponent
        // char field.
        @Test
        public void version2Test() {
            var crafted = new DFSBuilder()
                    .setVer(2)
                    .set("exponentialSeparator", null)
                    .set("exponential", '~')
                    .build();
            var bytes = ser(crafted);
            var dfs = assertDoesNotThrow(() -> deSer(bytes));
            assertEquals("~", dfs.getExponentSeparator());
        }

        // Version 3 didn't have perMillText, percentText, and minusSignText.
        // These were created from the corresponding char equivalents.
        @Test
        public void version3Test() {
            var crafted = new DFSBuilder()
                    .setVer(3)
                    .set("perMillText", null)
                    .set("percentText", null)
                    .set("minusSignText", null)
                    .set("perMill", '~')
                    .set("percent", '~')
                    .set("minusSign", '~')
                    .build();
            var bytes = ser(crafted);
            var dfs = assertDoesNotThrow(() -> deSer(bytes));
            // Need to check these String fields using reflection, since they
            // are not exposed via the public API
            assertEquals("~", readField(dfs, "perMillText"));
            assertEquals("~", readField(dfs, "percentText"));
            assertEquals("~", readField(dfs, "minusSignText"));
        }

        // Version 4 did not have monetaryGroupingSeparator. It should be based
        // off of groupingSeparator.
        @Test
        public void version4Test() {
            var crafted = new DFSBuilder()
                    .setVer(4)
                    .set("monetaryGroupingSeparator", 'Z')
                    .set("groupingSeparator", '~')
                    .build();
            var bytes = ser(crafted);
            var dfs = assertDoesNotThrow(() -> deSer(bytes));
            assertEquals(dfs.getGroupingSeparator(), dfs.getMonetaryGroupingSeparator());
        }
    }

    // Up-to-date DFS stream versions do not expect a null locale since the
    // standard DecimalFormatSymbols API forbids it. However, this was not always
    // the case and previous stream versions can contain a null locale. Thus,
    // ensure that a null locale does not cause number data loading to fail.
    @Test
    public void nullableLocaleTest() {
        var bytes = ser(new DFSBuilder()
                .set("locale", null)
                .set("minusSignText", "zFoo")
                .set("minusSign", 'z') // Set so that char/String forms agree
                .build());
        var dfs = assertDoesNotThrow(() -> deSer(bytes));
        assertNull(dfs.getLocale());
        // LMS should be based off of minusSignText when locale is null
        assertEquals("zFoo", readField(dfs, "lenientMinusSigns"));
    }

    // readObject fails when the {@code char} and {@code String} representations
    // of percent, per mille, and/or minus sign disagree.
    @Test
    public void disagreeingTextTest() {
        var expected = "'char' and 'String' representations of either percent, " +
                "per mille, and/or minus sign disagree.";
        assertEquals(expected, assertThrows(InvalidObjectException.class, () ->
                deSer(ser(new DFSBuilder()
                        .set("minusSignText", "Z")
                        .set("minusSign", 'X')
                        .build()))).getMessage());
        assertEquals(expected, assertThrows(InvalidObjectException.class, () ->
                deSer(ser(new DFSBuilder()
                        .set("perMillText", "Z")
                        .set("perMill", 'X')
                        .build()))).getMessage());
        assertEquals(expected, assertThrows(InvalidObjectException.class, () ->
                deSer(ser(new DFSBuilder()
                        .set("percentText", "Z")
                        .set("percent", 'X')
                        .build()))).getMessage());
    }

    // Ensure the serial version is updated to the current after de-serialization.
    @Test
    public void updatedVersionTest() {
        var bytes = ser(new DFSBuilder().setVer(-25).build());
        var dfs = assertDoesNotThrow(() -> deSer(bytes));
        assertEquals(5, readField(dfs, "serialVersionOnStream"));
    }

    // Should set currency from 4217 code when it is valid.
    @Test
    public void validIntlCurrencyTest() {
        var bytes = ser(new DFSBuilder().set("intlCurrencySymbol", "JPY").build());
        var dfs = assertDoesNotThrow(() -> deSer(bytes));
        assertEquals(Currency.getInstance("JPY"), dfs.getCurrency());
    }

    // Should not set currency when 4217 code is invalid, it remains null.
    @Test
    public void invalidIntlCurrencyTest() {
        var bytes = ser(new DFSBuilder()
                .set("intlCurrencySymbol", ">.,")
                .set("locale", Locale.JAPAN)
                .build());
        var dfs = assertDoesNotThrow(() -> deSer(bytes));
        // Can not init off invalid 4217 code, remains null
        assertNull(dfs.getCurrency());
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
    private static DecimalFormatSymbols deSer(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
             ObjectInputStream ois = new ObjectInputStream(byteArrayInputStream)) {
            return (DecimalFormatSymbols) ois.readObject();
        }
    }

    // Utility to read a private field
    private static Object readField(DecimalFormatSymbols dfs, String name) {
        return assertDoesNotThrow(() -> {
            var field = DecimalFormatSymbols.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(dfs);
        }, "Unexpected error during field reading");
    }

    // Utility class to build instances of DFS via reflection
    private static class DFSBuilder {

        private final DecimalFormatSymbols dfs;

        private DFSBuilder() {
            dfs = new DecimalFormatSymbols();
        }

        private DFSBuilder setVer(Object value) {
            return set("serialVersionOnStream", value);
        }

        private DFSBuilder set(String field, Object value) {
            return assertDoesNotThrow(() -> {
                Field f = dfs.getClass().getDeclaredField(field);
                f.setAccessible(true);
                f.set(dfs, value);
                return this;
            }, "Unexpected error during reflection setting");
        }

        private DecimalFormatSymbols build() {
            return dfs;
        }
    }
}

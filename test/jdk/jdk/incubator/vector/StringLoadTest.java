/*
 * Copyright (c) 2026, Google LLC. All rights reserved.
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
 * @modules jdk.incubator.vector
 * @run testng StringLoadTest
 */

import static org.testng.Assert.*;

import jdk.incubator.vector.*;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class StringLoadTest {

    private static final List<VectorSpecies<Byte>> BYTE_SPECIES =
            List.of(
                    ByteVector.SPECIES_64,
                    ByteVector.SPECIES_128,
                    ByteVector.SPECIES_256,
                    ByteVector.SPECIES_512,
                    ByteVector.SPECIES_MAX);
    private static final List<VectorSpecies<Short>> SHORT_SPECIES =
            List.of(
                    ShortVector.SPECIES_64,
                    ShortVector.SPECIES_128,
                    ShortVector.SPECIES_256,
                    ShortVector.SPECIES_512,
                    ShortVector.SPECIES_MAX);

    @Test(dataProvider = "strings")
    public void testShortVector(String string) {
        for (VectorSpecies<Short> species : SHORT_SPECIES) {
            assertTrue(ShortVector.compatibleWith(string, StandardCharsets.UTF_16));
            for (int i = 0; i <= string.length() - species.length(); i++) {
                ShortVector vec =
                        ShortVector.fromString(species, string, StandardCharsets.UTF_16, i);
                for (int lane = 0; lane < species.length(); lane++) {
                    assertEquals(
                            vec.lane(lane),
                            (short) string.charAt(i + lane),
                            String.format(
                                    "mismatch at offset %d for lane %d in %s", i, lane, string));
                }
            }
        }
    }

    @Test(dataProvider = "strings")
    public void testShortVectorMask(String string) {
        for (VectorSpecies<Short> species : SHORT_SPECIES) {
            assertTrue(ShortVector.compatibleWith(string, StandardCharsets.UTF_16));
            for (int i = 0; i <= string.length(); i++) {
                VectorMask<Short> mask = species.indexInRange(i, string.length());
                ShortVector vec =
                        ShortVector.fromString(species, string, StandardCharsets.UTF_16, i, mask);
                for (int lane = 0; lane < species.length(); lane++) {
                    short expected =
                            (i + lane < string.length()) ? (short) string.charAt(i + lane) : 0;
                    assertEquals(
                            vec.lane(lane),
                            expected,
                            String.format(
                                    "mismatch at offset %d for lane %d in %s", i, lane, string));
                }
            }
        }
    }

    @Test(dataProvider = "strings")
    public void testByteVector(String string) {
        boolean isLatin1 = string.chars().allMatch(c -> c <= 0xFF);
        if (!isLatin1) {
            assertFalse(ByteVector.compatibleWith(string, StandardCharsets.ISO_8859_1));
            return;
        }
        for (VectorSpecies<Byte> species : BYTE_SPECIES) {
            assertTrue(ByteVector.compatibleWith(string, StandardCharsets.ISO_8859_1));
            for (int i = 0; i <= string.length() - species.length(); i++) {
                ByteVector vec =
                        ByteVector.fromString(species, string, StandardCharsets.ISO_8859_1, i);
                for (int lane = 0; lane < species.length(); lane++) {
                    assertEquals(
                            vec.lane(lane),
                            (byte) string.charAt(i + lane),
                            String.format(
                                    "mismatch at offset %d for lane %d in %s", i, lane, string));
                }
            }
        }
    }

    @Test(dataProvider = "strings")
    public void testByteVectorMask(String string) {
        boolean isLatin1 = string.chars().allMatch(c -> c <= 0xFF);
        if (!isLatin1) {
            assertFalse(ByteVector.compatibleWith(string, StandardCharsets.ISO_8859_1));
            return;
        }
        for (VectorSpecies<Byte> species : BYTE_SPECIES) {
            assertTrue(ByteVector.compatibleWith(string, StandardCharsets.ISO_8859_1));
            for (int i = 0; i <= string.length(); i++) {
                VectorMask<Byte> mask = species.indexInRange(i, string.length());
                ByteVector vec =
                        ByteVector.fromString(
                                species, string, StandardCharsets.ISO_8859_1, i, mask);
                for (int lane = 0; lane < species.length(); lane++) {
                    byte expected =
                            (i + lane < string.length()) ? (byte) string.charAt(i + lane) : 0;
                    assertEquals(
                            vec.lane(lane),
                            expected,
                            String.format(
                                    "mismatch at offset %d for lane %d in %s", i, lane, string));
                }
            }
        }
    }

    @Test
    public void testShortExceptions() {
        VectorSpecies<Short> species = ShortVector.SPECIES_PREFERRED;
        String string = "a".repeat(species.length());
        VectorMask<Short> mask = species.indexInRange(0, string.length());

        assertTrue(ShortVector.compatibleWith(string, StandardCharsets.UTF_16));
        assertThrows(
                NullPointerException.class,
                () -> ShortVector.compatibleWith(null, StandardCharsets.UTF_16));
        assertThrows(NullPointerException.class, () -> ShortVector.compatibleWith(string, null));

        assertNotNull(ShortVector.fromString(species, string, StandardCharsets.UTF_16, 0));
        assertThrows(
                NullPointerException.class,
                () -> ShortVector.fromString(null, string, StandardCharsets.UTF_16, 0));
        assertThrows(
                NullPointerException.class,
                () -> ShortVector.fromString(species, null, StandardCharsets.UTF_16, 0));
        assertThrows(
                NullPointerException.class, () -> ShortVector.fromString(species, string, null, 0));

        assertNotNull(ShortVector.fromString(species, string, StandardCharsets.UTF_16, 0, mask));
        assertThrows(
                NullPointerException.class,
                () -> ShortVector.fromString(null, string, StandardCharsets.UTF_16, 0, mask));
        assertThrows(
                NullPointerException.class,
                () -> ShortVector.fromString(species, null, StandardCharsets.UTF_16, 0, mask));
        assertThrows(
                NullPointerException.class,
                () -> ShortVector.fromString(species, string, null, 0, mask));
        assertThrows(
                NullPointerException.class,
                () -> ShortVector.fromString(species, string, StandardCharsets.UTF_16, 0, null));

        assertThrows(
                IllegalArgumentException.class,
                () -> ShortVector.fromString(species, string, StandardCharsets.UTF_8, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> ShortVector.fromString(species, string, StandardCharsets.UTF_8, 0, mask));

        assertThrows(
                IndexOutOfBoundsException.class,
                () -> ShortVector.fromString(species, string, StandardCharsets.UTF_16, -1));
        assertThrows(
                IndexOutOfBoundsException.class,
                () ->
                        ShortVector.fromString(
                                species, string, StandardCharsets.UTF_16, string.length()));
        assertThrows(
                IndexOutOfBoundsException.class,
                () ->
                        ShortVector.fromString(
                                species,
                                string,
                                StandardCharsets.UTF_16,
                                string.length() - species.length() + 1));

        assertThrows(
                IndexOutOfBoundsException.class,
                () -> ShortVector.fromString(species, string, StandardCharsets.UTF_16, -1, mask));
        assertThrows(
                IndexOutOfBoundsException.class,
                () ->
                        ShortVector.fromString(
                                species, string, StandardCharsets.UTF_16, string.length(), mask));
        assertThrows(
                IndexOutOfBoundsException.class,
                () ->
                        ShortVector.fromString(
                                species,
                                string,
                                StandardCharsets.UTF_16,
                                string.length() - species.length() + 1,
                                mask));
    }

    @Test
    public void testByteExceptions() {
        VectorSpecies<Byte> species = ByteVector.SPECIES_PREFERRED;
        String string = "a".repeat(species.length());
        VectorMask<Byte> mask = species.indexInRange(0, string.length());

        assertTrue(ByteVector.compatibleWith(string, StandardCharsets.ISO_8859_1));
        assertThrows(
                NullPointerException.class,
                () -> ByteVector.compatibleWith(null, StandardCharsets.ISO_8859_1));
        assertThrows(NullPointerException.class, () -> ByteVector.compatibleWith(string, null));

        assertNotNull(ByteVector.fromString(species, string, StandardCharsets.ISO_8859_1, 0));
        assertThrows(
                NullPointerException.class,
                () -> ByteVector.fromString(null, string, StandardCharsets.ISO_8859_1, 0));
        assertThrows(
                NullPointerException.class,
                () -> ByteVector.fromString(species, null, StandardCharsets.ISO_8859_1, 0));
        assertThrows(
                NullPointerException.class, () -> ByteVector.fromString(species, string, null, 0));

        assertNotNull(ByteVector.fromString(species, string, StandardCharsets.ISO_8859_1, 0, mask));
        assertThrows(
                NullPointerException.class,
                () -> ByteVector.fromString(null, string, StandardCharsets.ISO_8859_1, 0, mask));
        assertThrows(
                NullPointerException.class,
                () -> ByteVector.fromString(species, null, StandardCharsets.ISO_8859_1, 0, mask));
        assertThrows(
                NullPointerException.class,
                () -> ByteVector.fromString(species, string, null, 0, mask));
        assertThrows(
                NullPointerException.class,
                () -> ByteVector.fromString(species, string, StandardCharsets.ISO_8859_1, 0, null));

        assertThrows(
                IllegalArgumentException.class,
                () -> ByteVector.fromString(species, string, StandardCharsets.UTF_8, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> ByteVector.fromString(species, string, StandardCharsets.UTF_8, 0, mask));

        assertThrows(
                IndexOutOfBoundsException.class,
                () -> ByteVector.fromString(species, string, StandardCharsets.ISO_8859_1, -1));
        assertThrows(
                IndexOutOfBoundsException.class,
                () ->
                        ByteVector.fromString(
                                species, string, StandardCharsets.ISO_8859_1, string.length()));
        assertThrows(
                IndexOutOfBoundsException.class,
                () ->
                        ByteVector.fromString(
                                species,
                                string,
                                StandardCharsets.ISO_8859_1,
                                string.length() - species.length() + 1));

        assertThrows(
                IndexOutOfBoundsException.class,
                () ->
                        ByteVector.fromString(
                                species, string, StandardCharsets.ISO_8859_1, -1, mask));
        assertThrows(
                IndexOutOfBoundsException.class,
                () ->
                        ByteVector.fromString(
                                species,
                                string,
                                StandardCharsets.ISO_8859_1,
                                string.length(),
                                mask));
        assertThrows(
                IndexOutOfBoundsException.class,
                () ->
                        ByteVector.fromString(
                                species,
                                string,
                                StandardCharsets.ISO_8859_1,
                                string.length() - species.length() + 1,
                                mask));
    }

    @Test(dataProvider = "strings")
    public void compatibleWith(String string) {
        for (Charset charset : Charset.availableCharsets().values()) {
            boolean isLatin1 = string.chars().allMatch(c -> c <= 0xFF);
            boolean byteCompatible = isLatin1 && charset == StandardCharsets.ISO_8859_1;
            assertEquals(ByteVector.compatibleWith(string, charset), byteCompatible);

            boolean shortCompatible = charset == StandardCharsets.UTF_16;
            assertEquals(ShortVector.compatibleWith(string, charset), shortCompatible);
        }
    }

    private static String repeat(String string) {
        return string.repeat((128 / string.length()) + 1);
    }

    @DataProvider
    public static Object[][] strings() {
        return new Object[][] {
            {""},
            {"hello world"},
            {"123"},
            {"section \u00A7"},
            {repeat("hello world")},
            {repeat("123")},
            {repeat("\u1234")},
            {repeat("section \u00A7")},
            {repeat("snowman \u26C4")},
            {repeat("rainbow \uD83C\uDF08")},
            {repeat("cartwheel \uD83E\uDD38")},
        };
    }
}

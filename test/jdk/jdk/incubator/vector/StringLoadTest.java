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
 * @bug 8390049
 * @modules jdk.incubator.vector
 * @run junit/othervm -XX:+CompactStrings StringLoadTest
 * @run junit/othervm -XX:-CompactStrings StringLoadTest
 */

import static org.junit.jupiter.api.Assertions.*;

import jdk.incubator.vector.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

public class StringLoadTest {

    private static final List<VectorSpecies<Short>> SHORT_SPECIES =
            List.of(
                    ShortVector.SPECIES_64,
                    ShortVector.SPECIES_128,
                    ShortVector.SPECIES_256,
                    ShortVector.SPECIES_512,
                    ShortVector.SPECIES_MAX);

    @ParameterizedTest
    @MethodSource("strings")
    public void testShortVector(String string) {
        for (VectorSpecies<Short> species : SHORT_SPECIES) {
            for (int i = 0; i <= string.length() - species.length(); i++) {
                ShortVector vec = ShortVector.fromString(species, string, i);
                for (int lane = 0; lane < species.length(); lane++) {
                    assertEquals(
                            (short) string.charAt(i + lane),
                            vec.lane(lane),
                            String.format(
                                    "mismatch at offset %d for lane %d in %s", i, lane, string));
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("strings")
    public void testShortVectorMask(String string) {
        for (VectorSpecies<Short> species : SHORT_SPECIES) {
            for (int i = 0; i <= string.length(); i++) {
                VectorMask<Short> mask = species.indexInRange(i, string.length());
                ShortVector vec = ShortVector.fromString(species, string, i, mask);
                for (int lane = 0; lane < species.length(); lane++) {
                    short expected =
                            (i + lane < string.length()) ? (short) string.charAt(i + lane) : 0;
                    assertEquals(
                            expected,
                            vec.lane(lane),
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

        assertNotNull(ShortVector.fromString(species, string, 0));
        assertThrows(NullPointerException.class, () -> ShortVector.fromString(null, string, 0));
        assertThrows(NullPointerException.class, () -> ShortVector.fromString(species, null, 0));

        assertNotNull(ShortVector.fromString(species, string, 0, mask));
        assertThrows(
                NullPointerException.class, () -> ShortVector.fromString(null, string, 0, mask));
        assertThrows(
                NullPointerException.class, () -> ShortVector.fromString(species, null, 0, mask));
        assertThrows(
                NullPointerException.class, () -> ShortVector.fromString(species, string, 0, null));

        assertThrows(
                IndexOutOfBoundsException.class, () -> ShortVector.fromString(species, string, -1));
        assertThrows(
                IndexOutOfBoundsException.class,
                () -> ShortVector.fromString(species, string, string.length()));
        assertThrows(
                IndexOutOfBoundsException.class,
                () ->
                        ShortVector.fromString(
                                species, string, string.length() - species.length() + 1));

        assertThrows(
                IndexOutOfBoundsException.class,
                () -> ShortVector.fromString(species, string, -1, mask));
        assertThrows(
                IndexOutOfBoundsException.class,
                () -> ShortVector.fromString(species, string, string.length(), mask));
        assertThrows(
                IndexOutOfBoundsException.class,
                () ->
                        ShortVector.fromString(
                                species, string, string.length() - species.length() + 1, mask));
    }

    private static String repeat(String string) {
        return string.repeat((512 / string.length()) + 1);
    }

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

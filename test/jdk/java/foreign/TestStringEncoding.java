/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.testng.annotations.*;
import static org.testng.Assert.*;

/*
 * @test
 * @run testng TestStringEncoding
 */

public class TestStringEncoding {

    @Test(dataProvider = "strings")
    public void testStrings(String testString) throws ReflectiveOperationException {
        for (Charset charset : Charset.availableCharsets().values()) {
            if (isStandard(charset)) {
                try (Arena arena = Arena.ofConfined()) {
                    MemorySegment text = arena.allocateFrom(testString, charset);

                    int terminatorSize = "\0".getBytes(charset).length;
                    if (charset == StandardCharsets.UTF_16) {
                        terminatorSize -= 2; // drop BOM
                    }
                    // Note that the JDK's UTF_32 encoder doesn't add a BOM.
                    // This is legal under the Unicode standard, and means the byte order is BE.
                    // See: https://unicode.org/faq/utf_bom.html#gen7

                    int expectedByteLength =
                            testString.getBytes(charset).length +
                            terminatorSize;

                    assertEquals(text.byteSize(), expectedByteLength);

                    String roundTrip = text.getString(0, charset);
                    if (charset.newEncoder().canEncode(testString)) {
                        assertEquals(roundTrip, testString);
                    }
                }
            } else {
                assertThrows(IllegalArgumentException.class, () -> Arena.global().allocateFrom(testString, charset));
            }
        }
    }

    @DataProvider
    public static Object[][] strings() {
        return new Object[][] {
            { "testing" },
            { "" },
            { "X" },
            { "12345" },
            { "yen \u00A5" },
            { "snowman \u26C4" },
            { "rainbow \uD83C\uDF08" }
        };
    }

    boolean isStandard(Charset charset) throws ReflectiveOperationException {
        for (Field standardCharset : StandardCharsets.class.getDeclaredFields()) {
            if (standardCharset.get(null) == charset) {
                return true;
            }
        }
        return false;
    }
}

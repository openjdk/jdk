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
 * @bug 8356152
 * @summary Check that huge concatenations throw OutOfMemoryError
 * @run junit/othervm -Xmx6G -XX:+CompactStrings -Dcompact=true HugeConcatTest
 * @run junit/othervm -Xmx6G -XX:-CompactStrings -Dcompact=false HugeConcatTest
 */

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertThrows;

public class HugeConcatTest {

    private static final int HUGE_LENGTH_UTF16 = Integer.MAX_VALUE / 2 - 2;
    private static int HUGE_LENGTH_LATIN1;

    @BeforeAll
    public static void setHugeLengthLatin1() {
        String compact = System.getProperty("compact", "true");
        HUGE_LENGTH_LATIN1 = Boolean.parseBoolean(compact)
                ? Integer.MAX_VALUE - 2
                : HUGE_LENGTH_UTF16;
    }

    private static String hugeLatin1() {
        return "a".repeat(HUGE_LENGTH_LATIN1);
    }

    private static String hugeUTF16() {
        return "\u20AC".repeat(HUGE_LENGTH_UTF16);
    }

    @Test
    public void testConcat_Latin1_Latin1() {
        String s1 = hugeLatin1();
        assertThrows(OutOfMemoryError.class, () -> s1.concat(s1));
    }

    @Test
    public void testConcat_Latin1_UTF16() {
        String s1 = hugeLatin1();
        String s2 = hugeUTF16();
        assertThrows(OutOfMemoryError.class, () -> s1.concat(s2));
    }

    @Test
    public void testConcat_UTF16_Latin1() {
        String s1 = hugeUTF16();
        String s2 = hugeLatin1();
        assertThrows(OutOfMemoryError.class, () -> s1.concat(s2));
    }

    @Test
    public void testConcat_UTF16_UTF16() {
        String s1 = hugeUTF16();
        assertThrows(OutOfMemoryError.class, () -> s1.concat(s1));
    }

}

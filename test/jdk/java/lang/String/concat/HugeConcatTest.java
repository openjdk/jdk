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
 * @requires os.maxMemory > 8G
 * @run junit/othervm -Xmx8G -XX:+CompactStrings -Dcompact=true HugeConcatTest
 * @run junit/othervm -Xmx8G -XX:-CompactStrings -Dcompact=false HugeConcatTest
 */

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertThrows;

public class HugeConcatTest {

    private static final int HUGE_LENGTH_UTF16 = Integer.MAX_VALUE / 2 - 2;

    private static String hugeLatin1;
    private static final String hugeUTF16 = "\u20AC".repeat(HUGE_LENGTH_UTF16);

    @BeforeAll
    public static void initHugeLatin1() {
        String compact = System.getProperty("compact", "true");
        int hugeLatin1Length = Boolean.parseBoolean(compact)
                ? Integer.MAX_VALUE - 2
                : HUGE_LENGTH_UTF16;
        hugeLatin1 = "a".repeat(hugeLatin1Length);
    }

    @Test
    public void testConcat_Latin1_Latin1() {
        assertThrows(OutOfMemoryError.class, () -> { var s = hugeLatin1 + hugeLatin1; });
        assertThrows(OutOfMemoryError.class, () -> hugeLatin1.concat(hugeLatin1));
    }

    @Test
    public void testConcat_Latin1_UTF16() {
        assertThrows(OutOfMemoryError.class, () -> { var s = hugeLatin1 + hugeUTF16; });
        assertThrows(OutOfMemoryError.class, () -> hugeLatin1.concat(hugeUTF16));
    }

    @Test
    public void testConcat_UTF16_Latin1() {
        assertThrows(OutOfMemoryError.class, () -> { var s = hugeUTF16 + hugeLatin1; });
        assertThrows(OutOfMemoryError.class, () -> hugeUTF16.concat(hugeLatin1));
    }

    @Test
    public void testConcat_UTF16_UTF16() {
        assertThrows(OutOfMemoryError.class, () -> { var s = hugeUTF16 + hugeUTF16; });
        assertThrows(OutOfMemoryError.class, () -> hugeUTF16.concat(hugeUTF16));
    }

}

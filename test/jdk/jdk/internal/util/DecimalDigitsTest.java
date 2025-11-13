/*
 * Copyright (c) 2024, Alibaba Group Holding Limited. All Rights Reserved.
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

package test.jdk.internal.util;

import org.testng.annotations.Test;
import static org.testng.Assert.*;

import jdk.internal.util.DecimalDigits;

/*
 * @test
 * @bug 8366224
 * @summary Test DecimalDigits.appendPair method with LATIN1 and UTF16 encoding
 * @modules java.base/jdk.internal.util
 * @run testng test.jdk.internal.util.DecimalDigitsTest
 */
public class DecimalDigitsTest {

    @Test
    public void testAppendPair() {
        // Test values from 0 to 99
        for (int i = 0; i <= 99; i++) {
            StringBuilder sb = new StringBuilder();
            DecimalDigits.appendPair(sb, i);
            String expected = String.format("%02d", i);
            assertEquals(sb.toString(), expected, "Failed for value: " + i);
        }
    }

    @Test
    public void testAppendPairWithUTF16Encoding() {
        // Test appendPair with UTF16 encoding
        StringBuilder sb = new StringBuilder();

        // Force the StringBuilder to use UTF16 encoding by adding a non-LATIN1 character first
        sb.append('€'); // Euro sign is not in LATIN1

        for (int i = 0; i <= 99; i++) {
            int currentLength = sb.length();
            DecimalDigits.appendPair(sb, i);
            String expected = String.format("%02d", i);

            // Check that the pair was appended correctly
            String appended = sb.substring(currentLength);
            assertEquals(appended, expected, "Failed for value: " + i + " with UTF16 encoding");
        }
    }
}
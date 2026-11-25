/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8394548
 * @summary Incomplete Unicode escape surrogate pair causes incorrect backslash handling
 * @compile UnicodeSurrogateBackslash.java
 * @run main UnicodeSurrogateBackslash
 */

import java.util.Arrays;

public class UnicodeSurrogateBackslash {

    record TestCase(String string, int value1, int value2) {
        void check() {
            final String expected = String.format("%c\\u%04x", (char)value1, value2);
            if (!string.equals(expected))
                throw new AssertionError(String.format("%s: expected \"%s\"", this, expected));
        }

        @Override
        public String toString() {
            return String.format("TestCase[string=\"%s\",value1=0x%04x,value2=0x%04x]", string, value1, value2);
        }
    }

    // All of these Strings should compile as: Escaped Unicode character, backslash character, then "uXXXX" (for some X's).
    // The value of the first escaped Unicode character should have no effect on how the rest of the string is parsed.
    public static final TestCase[] TEST_CASES = new TestCase[] {

        // These are the test cases from the original bug report
        new TestCase("\u1234\\u1234", 0x1234, 0x1234),
        new TestCase("\ud800\\u1234", 0xd800, 0x1234),

        // Some other test cases
        new TestCase("\u0000\\u0000", 0x0000, 0x0000),
        new TestCase("\ud7ff\\u0000", 0xd7ff, 0x0000),
        new TestCase("\uaaaa\\ud7ff", 0xaaaa, 0xd7ff),
        new TestCase("\ud8ff\\ud8ff", 0xd8ff, 0xd8ff),
        new TestCase("\udbff\\u1234", 0xdbff, 0x1234),
        new TestCase("\udc01\\u1234", 0xdc01, 0x1234),
        new TestCase("\ud888\\udccc", 0xd888, 0xdccc),
        new TestCase("\ue123\\u1234", 0xe123, 0x1234),
        new TestCase("\ueeee\\ueabc", 0xeeee, 0xeabc)
    };

    public static void main(String[] args) {
        Arrays.asList(TEST_CASES).forEach(TestCase::check);
    }
}

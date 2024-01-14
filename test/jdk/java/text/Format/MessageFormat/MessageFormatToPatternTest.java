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
 * @summary Check MessageFormat.toPattern() is equivalent to original pattern
 * @bug 8323699
 * @run junit MessageFormatToPatternTest
 */

import java.text.MessageFormat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MessageFormatToPatternTest {

    // Converting from MessageFormat to pattern string and back should give the same result.
    // For this to work the pattern string needs to quote any "extra" closing brace "}" characters.
    @Test
    public void toPatternTest() {

        String pattern1 = "{0,choice,0.0#option A: {1}|1.0#option B: {1}'}'}";
        MessageFormat format1 = new MessageFormat(pattern1);
        String result1 = format1.format(new Object[] { 0, 5 });

        String pattern2 = format1.toPattern();
        MessageFormat format2 = new MessageFormat(pattern2);
        String result2 = format2.format(new Object[] { 0, 5 });

        assertEquals(result1, result2);
    }
}

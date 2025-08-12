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
 * @bug 8327640
 * @summary Check parseStrict correctness for DecimalFormat.equals()
 * @run junit EqualityTest
 */

import org.junit.jupiter.api.Test;

import java.text.DecimalFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class EqualityTest {

    private static final DecimalFormat fmt1 = new DecimalFormat();
    private static final DecimalFormat fmt2 = new DecimalFormat();

    // Ensure that parseStrict is reflected correctly for DecimalFormat.equals()
    @Test
    public void checkStrictTest() {
        // parseStrict is false by default
        assertEquals(fmt1, fmt2);
        fmt1.setStrict(true);
        assertNotEquals(fmt1, fmt2);
        fmt2.setStrict(true);
        assertEquals(fmt1, fmt2);
    }
}

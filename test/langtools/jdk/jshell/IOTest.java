/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test input/output
 * @build KullaTesting TestingInputStream
 * @run junit IOTest
 */

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public class IOTest extends KullaTesting {

    String LINE_SEPARATOR = System.getProperty("line.separator");

    @Test
    public void testOutput() {
        assertEval("System.out.println(\"Test\");");
        assertEquals("Test" + LINE_SEPARATOR, getOutput());
    }

    @Test
    public void testErrorOutput() {
        assertEval("System.err.println(\"Oops\");");
        assertEquals("Oops" + LINE_SEPARATOR, getErrorOutput());
    }

    @Test
    public void testInput() {
        setInput("x");
        assertEval("(char)System.in.read();", "'x'");
    }
}

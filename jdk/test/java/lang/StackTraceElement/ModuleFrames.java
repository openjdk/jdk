/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Stack trace should have module information
 * @run testng ModuleFrames
 */

import java.util.Arrays;

import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class ModuleFrames {

    @Test
    public void testModuleName() {
        try {
            Integer.parseInt("a");
        } catch (NumberFormatException e) {

            StackTraceElement[] stack = e.getStackTrace();
            StackTraceElement topFrame = stack[0];
            StackTraceElement thisFrame = findFrame(this.getClass().getName(), stack);

            assertEquals(topFrame.getModuleName(), "java.base",
                    "Expected top frame to be in module java.base");

            assertTrue(topFrame.toString().contains("java.base"),
                    "Expected toString of top frame to include java.base");

            assertNull(thisFrame.getModuleName(),
                    "Expected frame for test not to have a module name");
        }
    }

    private static StackTraceElement findFrame(String cn, StackTraceElement[] stack) {
        return Arrays.stream(stack)
                .filter(s -> s.getClassName().equals(cn))
                .findFirst()
                .get();
    }
}

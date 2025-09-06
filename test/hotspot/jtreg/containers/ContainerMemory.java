/*
 * Copyright (c) 2025, Red Hat, Inc.
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

import jdk.test.whitebox.WhiteBox;

/*
 * Checks OSContainer::has_memory_limit() and related APIs when run with
 * a container limit.
 */
public class ContainerMemory {

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new RuntimeException("Illegal number of arguments. Expected at least one argument.");
        }
        switch (args[0]) {
            case "hasMemoryLimit": {
                testHasMemoryLimit(args[0], args[1]);
                break;
            }
            default: {
                throw new RuntimeException("Unknown test argument: " + args[0]);
            }
        }
    }

    private static void testHasMemoryLimit(String testCase, String strExpected) {
        WhiteBox wb = WhiteBox.getWhiteBox();
        boolean expected = Boolean.parseBoolean(strExpected);
        boolean actual = wb.hasMemoryLimit();
        if (expected != actual) {
            throw new RuntimeException("hasMemoryLimit test failed. Expected '" + expected + "' but got '" + actual + "'");
        }
        // PASS
        System.out.printf("%s=%s", testCase, strExpected);
    }
}

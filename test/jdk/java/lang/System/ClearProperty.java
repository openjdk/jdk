/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4463345
 * @summary Simple test of System.clearProperty
 * @run main/othervm ClearProperty
 */

public class ClearProperty {
    public static void main(String [] argv) throws Exception {
        clearTest();
        paramTest();
    }

    static void clearTest() throws Exception {
        System.setProperty("blah", "blech");
        if (!System.getProperty("blah").equals("blech"))
            throw new RuntimeException("Clear test failed 1");
        System.clearProperty("blah");
        if (System.getProperty("blah") != null)
            throw new RuntimeException("Clear test failed 2");
    }

    static void paramTest() throws Exception {
        try {
            System.clearProperty(null);
            throw new RuntimeException("Param test failed");
        } catch (NullPointerException npe) {
            // Correct result
        }
        try {
            System.clearProperty("");
            throw new RuntimeException("Param test failed");
        } catch (IllegalArgumentException iae) {
            // Correct result
        }
    }
}

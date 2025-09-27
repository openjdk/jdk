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
 * @summary Test the range defined in globals.hpp for BciProfileWidth
 * @bug 8358696
 * @requires vm.debug
 * @library /test/lib
 * @run main/othervm compiler.arguments.TestBciProfileWidth
 */

package compiler.arguments;

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.Asserts;

public class TestBciProfileWidth {

    public static void main(String args[]) throws Throwable {
        checkBciProfileWidth(-1, true);
        checkBciProfileWidth(10000, true);
        checkBciProfileWidth(0, false);
        checkBciProfileWidth(1000, false);
    }

    static void checkBciProfileWidth(int value, boolean fail) throws Throwable {
        OutputAnalyzer out = ProcessTools.executeTestJava("-XX:BciProfileWidth=" + value);
        String output = out.getOutput();
        if (fail) {
            String pattern = "int BciProfileWidth=" + value + " is outside the allowed range [ 0 ... 1000 ]";
            Asserts.assertTrue(output.contains(pattern));
        } else {
            System.out.println("Passed");
        }
    }
}

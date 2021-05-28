/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8267773
 * @summary test that fetching a static field produces the correct result
 * @library /test/lib /
 * @run main compiler.stringopts.TestFetchStaticField
 */

package compiler.stringopts;

import java.lang.StringIndexOutOfBoundsException;
import jdk.test.lib.Asserts;

public class TestFetchStaticField {

    public void runTest() {
        System.out.println ("Test: StringIndexOutOfBoundsException(Integer.MIN_VALUE)");
        for (int i = 0; i < 100_000; i++ ) {
            run1Test(i);
        }
        System.out.println("Test finished.");
    }

    public static void main(String[] argv)
    {
        System.out.println("Running on:");
        System.out.println("os.arch: " + System.getProperty("os.arch"));
        System.out.println("os.name: " + System.getProperty("os.name"));
        System.out.println("java.runtime.version: " + System.getProperty("java.runtime.version"));
        System.out.println("java.vm.name: " + System.getProperty("java.vm.name"));
        System.out.println("java.vm.version: " + System.getProperty("java.vm.version"));

        TestFetchStaticField test = new TestFetchStaticField();
        test.runTest();
    }

    public void run1Test(int i) {
        StringIndexOutOfBoundsException obj = new StringIndexOutOfBoundsException(Integer.MIN_VALUE);
        if (obj == null) {
            Asserts.fail("Failed: obj == null");
        } else {
            if (!obj.toString().equals("java.lang.StringIndexOutOfBoundsException: String index out of range: -2147483648")) {
                Asserts.fail("Failed on invocation " + i + ": obj.toString() = \"" + obj.toString() + "\", not \"java.lang.StringIndexOutOfBoundsException: String index out of range: -2147483648\"");
            }
        }
    }
}

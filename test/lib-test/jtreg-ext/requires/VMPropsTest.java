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
 * @test VMPropsTest
 * @bug 8320750
 * @library /test/lib
 * @library /test/jtreg-ext
 * @modules java.base/jdk.internal.misc
 *          java.base/jdk.internal.foreign
 *          java.management/sun.management
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI VMPropsTest
 * @summary testing parsing of -Xflags
 */

import static jdk.test.lib.Asserts.assertTrue;
import static jdk.test.lib.Asserts.assertEQ;
import requires.VMProps;

/**
 * This tests parsing of -Xflags.
 */
public class VMPropsTest {
    public static void main(String[] args) {
        assertEQ(VMProps.parseXFlag("-name").name(), "name");
        assertEQ(VMProps.parseXFlag("-name").value(), "true");
        assertEQ(VMProps.parseXFlag("-name:value").name(), "name");
        assertEQ(VMProps.parseXFlag("-name:value").value(), "value");
        assertEQ(VMProps.parseXFlag("-name4g").name(), "name");
        assertEQ(VMProps.parseXFlag("-name4g").value(), "4g");
        assertEQ(VMProps.parseXFlag("-name43g").name(), "name");
        assertEQ(VMProps.parseXFlag("-name43g").value(), "43g");

        // Make sure that we handle several flags that are equal
        System.setProperty("test.java.opts", "-Xlog:gc* -Xlog:gc*");
        VMProps.xFlags();
    }
}

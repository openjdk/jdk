/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @summary Test whether different object type can be pinned or not.
 * @requires vm.gc.G1
 * @requires vm.debug
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver gc.g1.pinnedobjs.TestPinnedObjectTypes
 */

package gc.g1.pinnedobjs;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.whitebox.WhiteBox;

public class TestPinnedObjectTypes {

    public static void main(String[] args) throws Exception {
        testPinning("Object", false);
        testPinning("TypeArray", true);
        testPinning("ObjArray", false);
    }

    private static void testPinning(String type, boolean shouldSucceed) throws Exception {
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder("-XX:+UseG1GC",
                                                                             "-XX:+UnlockDiagnosticVMOptions",
                                                                             "-XX:+WhiteBoxAPI",
                                                                             "-Xbootclasspath/a:.",
                                                                             "-XX:-CreateCoredumpOnCrash",
                                                                             "-Xmx32M",
                                                                             "-Xmn16M",
                                                                             "-Xlog:gc",
                                                                             TestObjectPin.class.getName(),
                                                                             type);

        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        System.out.println(output.getStdout());
        if (shouldSucceed) {
          output.shouldHaveExitValue(0);
        } else {
          output.shouldNotHaveExitValue(0);
        }
    }

}

class TestObjectPin {

    private static final WhiteBox wb = WhiteBox.getWhiteBox();

    public static void main(String[] args) {
        Object o = switch (args[0]) {
            case "Object" -> new Object();
            case "TypeArray" -> new int[100];
            case "ObjArray" -> new Object[100];
            default -> null;
        };
        wb.pinObject(o);
    }
}


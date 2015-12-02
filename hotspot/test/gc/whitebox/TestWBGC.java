/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @test TestWBGC
 * @bug 8055098
 * @summary Test verify that WB methods isObjectInOldGen and youngGC works correctly.
 * @library /testlibrary /test/lib
 * @modules java.base/sun.misc
 *          java.management
 * @build TestWBGC
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 * @run driver TestWBGC
 */
import jdk.test.lib.*;
import sun.hotspot.WhiteBox;

public class TestWBGC {

    public static void main(String args[]) throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                true,
                "-Xbootclasspath/a:.",
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:+WhiteBoxAPI",
                "-XX:MaxTenuringThreshold=1",
                "-XX:+PrintGC",
                GCYoungTest.class.getName());

        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        System.out.println(output.getStdout());
        output.shouldHaveExitValue(0);
        output.shouldContain("WhiteBox Initiated Young GC");
        output.shouldNotContain("Full");
        // To be sure that we don't provoke Full GC additionaly to young
    }

    public static class GCYoungTest {
        static WhiteBox wb = WhiteBox.getWhiteBox();
        public static Object obj;

        public static void main(String args[]) {
            obj = new Object();
            Asserts.assertFalse(wb.isObjectInOldGen(obj));
            wb.youngGC();
            wb.youngGC();
            // 2 young GC is needed to promote object into OldGen
            Asserts.assertTrue(wb.isObjectInOldGen(obj));
        }
    }
}

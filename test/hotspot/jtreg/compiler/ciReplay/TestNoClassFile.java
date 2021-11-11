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
 * @bug 8276227
 * @library / /test/lib
 * @summary Testing that ciReplay works if we do not find the class files to replay compile.
 * @requires vm.flightRecorder != true & vm.compMode != "Xint" & vm.compMode != "Xcomp" & vm.debug == true & vm.compiler2.enabled
 * @modules java.base/jdk.internal.misc
 * @build sun.hotspot.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *      compiler.ciReplay.TestNoClassFile
 */

package compiler.ciReplay;

public class TestNoClassFile extends DumpReplayBase {

    public static void main(String[] args) {
        new TestNoClassFile().runTest(TIERED_DISABLED_VM_OPTION);
    }

    @Override
    public void testAction() {
        // Override classpath such that we do not find any class files for replay compilation. Should exit gracefully.
        positiveTest("-cp foo", "-XX:+ReplayIgnoreInitErrors");
    }

    @Override
    public String getTestClass() {
        return NoClassFileMain.class.getName();
    }

}

class NoClassFileMain {
    public static void main(String[] args) {
        for (int i = 0; i < 10000; i++) {
            test();
        }
    }
    public static void test() {
        NoClassFileHelper.bar();
    }
}

class NoClassFileHelper {
    public static int bar() {
        return 3;
    }
}

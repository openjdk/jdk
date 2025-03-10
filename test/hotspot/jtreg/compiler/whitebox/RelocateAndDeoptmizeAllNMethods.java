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
 * @test id=Serial
 * @bug 8316694
 * @summary Relocates all nmethods and also deoptimizes to confirm no crashes
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc java.management
 *
 * @requires vm.opt.DeoptimizeALot != true
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+SegmentedCodeCache -XX:+UseSerialGC compiler.whitebox.RelocateAndDeoptmizeAllNMethods
 */

/*
 * @test id=Parallel
 * @bug 8316694
 * @summary Relocates all nmethods and also deoptimizes to confirm no crashes
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc java.management
 *
 * @requires vm.opt.DeoptimizeALot != true
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+SegmentedCodeCache -XX:+UseParallelGC compiler.whitebox.RelocateAndDeoptmizeAllNMethods
 */

/*
 * @test id=G1
 * @bug 8316694
 * @summary Relocates all nmethods and also deoptimizes to confirm no crashes
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc java.management
 *
 * @requires vm.opt.DeoptimizeALot != true
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+SegmentedCodeCache -XX:+UseG1GC compiler.whitebox.RelocateAndDeoptmizeAllNMethods
 */

/*
 * @test id=Shenandoah
 * @bug 8316694
 * @summary Relocates all nmethods and also deoptimizes to confirm no crashes
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc java.management
 *
 * @requires vm.opt.DeoptimizeALot != true
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+SegmentedCodeCache -XX:+UseShenandoahGC compiler.whitebox.RelocateAndDeoptmizeAllNMethods
 */

/*
 * @test id=ZGC
 * @bug 8316694
 * @summary Relocates all nmethods and also deoptimizes to confirm no crashes
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc java.management
 *
 * @requires vm.opt.DeoptimizeALot != true
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+SegmentedCodeCache -XX:+UseZGC compiler.whitebox.RelocateAndDeoptmizeAllNMethods
 */

package compiler.whitebox;

import jdk.test.whitebox.WhiteBox;

public class RelocateAndDeoptmizeAllNMethods {

    private static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();

    public static void main(String [] args) throws Exception {
        WHITE_BOX.relocateAllNMethods();

        WHITE_BOX.fullGC();

        WHITE_BOX.deoptimizeAll();

        WHITE_BOX.fullGC();
    }
}

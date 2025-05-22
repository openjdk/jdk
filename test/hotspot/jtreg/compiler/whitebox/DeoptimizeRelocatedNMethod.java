/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 *
 */

/*
 * @test id=Serial
 * @bug 8316694
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc java.management
 * @requires vm.opt.DeoptimizeALot != true
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbatch -XX:+SegmentedCodeCache -XX:+UseSerialGC compiler.whitebox.DeoptimizeRelocatedNMethod
 */

/*
 * @test id=Parallel
 * @bug 8316694
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc java.management
 * @requires vm.opt.DeoptimizeALot != true
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbatch -XX:+SegmentedCodeCache -XX:+UseParallelGC compiler.whitebox.DeoptimizeRelocatedNMethod
 */

/*
 * @test id=G1
 * @bug 8316694
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc java.management
 * @requires vm.opt.DeoptimizeALot != true
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbatch -XX:+SegmentedCodeCache -XX:+UseG1GC compiler.whitebox.DeoptimizeRelocatedNMethod
 */

/*
 * @test id=Shenandoah
 * @bug 8316694
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc java.management
 * @requires vm.opt.DeoptimizeALot != true
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbatch -XX:+SegmentedCodeCache -XX:+UseShenandoahGC compiler.whitebox.DeoptimizeRelocatedNMethod
 */

/*
 * @test id=ZGC
 * @bug 8316694
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc java.management
 * @requires vm.opt.DeoptimizeALot != true
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbatch -XX:+SegmentedCodeCache -XX:+UseZGC compiler.whitebox.DeoptimizeRelocatedNMethod
 */

package compiler.whitebox;

import compiler.whitebox.CompilerWhiteBoxTest;
import java.lang.reflect.Method;
import jdk.test.whitebox.WhiteBox;
import jdk.test.whitebox.code.BlobType;
import jdk.test.whitebox.code.NMethod;

public class DeoptimizeRelocatedNMethod {

    private static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();
    public static double FUNCTION_RESULT = 0;

    public static void main(String [] args) throws Exception {
        // Get method that will be relocated
        Method method = DeoptimizeRelocatedNMethod.class.getMethod("function");
        WHITE_BOX.testSetDontInlineMethod(method, true);

        // Verify not initially compiled
        CompilerWhiteBoxTest.checkNotCompiled(method, false);

        // Call function enough to compile
        callFunction();

        // Verify now compiled
        CompilerWhiteBoxTest.checkCompiled(method, false);

        // Get newly created nmethod
        NMethod origNmethod = NMethod.get(method, false);

        // Relocate nmethod and mark old for cleanup
        WHITE_BOX.relocateNMethodFromMethod(method, BlobType.MethodProfiled.id);

        // Trigger GC to clean up old nmethod
        WHITE_BOX.fullGC();

        // Verify function still compiled after old was cleaned up
        CompilerWhiteBoxTest.checkCompiled(method, false);

        // Get new nmethod and verify it's actually new
        NMethod newNmethod = NMethod.get(method, false);
        if (origNmethod.entry_point == newNmethod.entry_point) {
            throw new RuntimeException("Did not create new nmethod");
        }

        // Deoptimized method
        WHITE_BOX.deoptimizeMethod(method);

        CompilerWhiteBoxTest.checkNotCompiled(method, false);

        // Call to verify everything still works
        function();
    }

    // Call function multiple times to trigger compilation
    private static void callFunction() {
        for (int i = 0; i < CompilerWhiteBoxTest.THRESHOLD; i++) {
            function();
        }
    }

    public static void function() {
        FUNCTION_RESULT = Math.random();
    }
}

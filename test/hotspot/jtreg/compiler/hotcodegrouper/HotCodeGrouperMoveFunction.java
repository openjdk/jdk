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
 * @test
 * @library /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -Xbatch -XX:-TieredCompilation -XX:+UnlockExperimentalVMOptions -XX:+HotCodeHeap
 *                   -XX:+NMethodRelocation -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:HotCodeIntervalSeconds=0
 *                   -XX:HotCodeSampleSeconds=5 -XX:HotCodeSteadyThreshold=1 -XX:HotCodeSampleRatio=1
 *                   compiler.hotcodegrouper.HotCodeGrouperMoveFunction
 */

package compiler.hotcodegrouper;

import java.lang.reflect.Method;

import jdk.test.lib.Asserts;
import jdk.test.whitebox.WhiteBox;
import jdk.test.whitebox.code.BlobType;
import jdk.test.whitebox.code.NMethod;

public class HotCodeGrouperMoveFunction {

    private static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();
    private static final Method method;
    private static final int C2_LEVEL = 4;

    static {
        try {
            method = HotCodeGrouperMoveFunction.class.getMethod("func");
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws Exception {
        WHITE_BOX.testSetDontInlineMethod(method, true);

        compileFunc();

        // Function should be in the NonProfiled code heap right after compilation
        NMethod orig_nm = NMethod.get(method, false);
        Asserts.assertNotEquals(orig_nm, null);
        Asserts.assertEQ(orig_nm.code_blob_type, BlobType.MethodNonProfiled);

        // Call function so grouper samples and relocates
        func();

        // Function should now be in the Hot code heap after grouper has had time to relocate
        NMethod reloc_nm = NMethod.get(method, false);
        Asserts.assertNotEquals(reloc_nm, null);
        Asserts.assertEQ(reloc_nm.code_blob_type, BlobType.MethodHot);
    }

    public static void compileFunc() {
        WHITE_BOX.enqueueMethodForCompilation(method, C2_LEVEL);

        if (WHITE_BOX.getMethodCompilationLevel(method) != C2_LEVEL) {
            throw new IllegalStateException("Method " + method + " is not compiled by C2.");
        }
    }

    public static void func() {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 10_000) {}
    }

}

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
 * @bug 8390662
 * @summary Test that compilation is not stopped when there is no space left in
 *          HotCodeHeap. Relocation is not compilation, so a failed
 *          relocation must not disable the compilers.
 * @requires vm.compiler2.enabled & vm.opt.SegmentedCodeCache != false
 * @library /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -Xbatch -XX:-TieredCompilation -XX:+SegmentedCodeCache
 *                   -XX:+UnlockExperimentalVMOptions -XX:+HotCodeHeap -XX:+NMethodRelocation
 *                   -XX:HotCodeHeapSize=256K -XX:HotCodeStartupDelaySeconds=86400
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:CodeCacheMinBlockLength=1
 *                   -XX:-UseCodeCacheFlushing
 *                   compiler.hotcode.TestFullHotCodeHeap
 */

package compiler.hotcode;

import java.lang.reflect.Method;

import jdk.test.lib.Asserts;
import jdk.test.whitebox.WhiteBox;
import jdk.test.whitebox.code.BlobType;
import jdk.test.whitebox.code.NMethod;

public class TestFullHotCodeHeap {

    private static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();

    private static final int C2_LEVEL = 4;
    private static final int BLOB_SIZE = 128;

    public static void main(String[] args) throws Exception {
        Method method1 = getMethod("simple1");

        simple1(10);
        compileWithC2(method1);
        NMethod nmethod = NMethod.get(method1, false);
        Asserts.assertNotNull(nmethod, "`simple1` is not compiled");

        // Fill HotCodeHeap until allocation fails.
        while (WHITE_BOX.allocateCodeBlob(BLOB_SIZE, BlobType.MethodHot.id) != 0) {}

        // Not enough space is left in the hot code heap, so the relocation must fail
        // and leave the nmethod where it is.
        WHITE_BOX.relocateNMethodFromMethod(method1, BlobType.MethodHot.id);
        NMethod relocated = NMethod.get(method1, false);
        Asserts.assertNotNull(relocated, "`simple1` is not kept after the failed relocation");
        Asserts.assertEQ(nmethod.address, relocated.address,
                         "`simple1` must not be relocated to the full hot code heap");

        simple2(11);
        compileWithC2(getMethod("simple2"));
    }

    private static Method getMethod(String name) throws Exception {
        return TestFullHotCodeHeap.class.getMethod(name, int.class);
    }

    private static void compileWithC2(Method method) {
        WHITE_BOX.enqueueMethodForCompilation(method, C2_LEVEL);
        Asserts.assertEQ(C2_LEVEL, WHITE_BOX.getMethodCompilationLevel(method),
                         "`" + method.getName() + "` is not compiled by C2");
    }

    public static int simple1(int n) {
        return ((n % 2) == 0) ? 1 : n;
    }

    public static int simple2(int n) {
        return ((n % 2) == 0) ? 2 : n;
    }
}

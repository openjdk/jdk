/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8378719
 * @summary Reproduces a RuntimeStub::resolve_static_call_blob pd_patch_instruction_size guarantee
 *          - forces adapters to be allocated outside the NonNMethod heap
 *          - puts c2i adapter and compiled method at 128+ MB distance
 * @requires vm.flagless
 * @requires os.arch == "aarch64"
 * @requires vm.debug == false
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+UnlockExperimentalVMOptions
 *                   -XX:+WhiteBoxAPI
 *                   -XX:ReservedCodeCacheSize=240M
 *                   -XX:NonNMethodCodeHeapSize=8M
 *                   -XX:ProfiledCodeHeapSize=116M
 *                   -XX:NonProfiledCodeHeapSize=116M
 *                   -XX:CodeCacheMinBlockLength=1
 *                   -XX:CodeCacheSegmentSize=128
 *                   -XX:-UseCodeCacheFlushing
 *                   -XX:CompileCommand=dontinline,compiler.codecache.TestNonNMethodHeapOverflowTarget::a
 *                   -XX:CompileCommand=exclude,compiler.codecache.TestNonNMethodHeapOverflowTarget::a
 *                   -XX:CompileCommand=compileonly,compiler.codecache.TestNonNMethodHeapOverflowTarget::b
 *                   compiler.codecache.TestNonNMethodHeapOverflow
 */

package compiler.codecache;

import jdk.test.whitebox.WhiteBox;
import jdk.test.whitebox.code.BlobType;
import jdk.test.whitebox.code.CodeBlob;
import jdk.test.whitebox.code.NMethod;

import java.lang.reflect.Method;

public class TestNonNMethodHeapOverflow {
    private static final WhiteBox WB = WhiteBox.getWhiteBox();
    private static final int HEAP_BLOCK_HEADER_SIZE = 8;

    public static void main(String[] args) throws Exception {
        WB.lockCompilation();

        BlobType blobType;
        int blobSize = 1024;
        int allocSize = blobSize - HEAP_BLOCK_HEADER_SIZE;
        // fill the NonNMethod heap
        do {
            long addr = WB.allocateCodeBlob(allocSize, BlobType.NonNMethod.id);
            if (addr == 0) {
                throw new RuntimeException("Failed to allocate in BlobType.NonNMethod");
            }
            blobType = CodeBlob.getCodeBlob(addr).code_blob_type;
        } while (blobType == BlobType.NonNMethod);

        if (blobType != BlobType.MethodNonProfiled) {
            throw new RuntimeException("NonNMethod->NonProfiled fallback mechanism was changed? Need to update the test");
        }

        long heapSize = BlobType.MethodNonProfiled.getSize();
        int allocated = 0;
        // fill the first half of NonProfiled heap
        while (allocated < heapSize / 2) {
            long addr = WB.allocateCodeBlob(allocSize, BlobType.MethodNonProfiled.id);
            if (addr == 0) {
                throw new RuntimeException("Failed to allocate in MethodNonProfiled");
            }
            allocated += blobSize;
        }

        WB.unlockCompilation();

        // loading triggers i2c/c2i adapter generation; NonNMethod heap is full, adapters go into a middle of NonProfiled heap
        Class<?> c = Class.forName("compiler.codecache.TestNonNMethodHeapOverflowTarget");
        Method methodB = c.getDeclaredMethod("b");
        methodB.invoke(null);

        // compile b() at level 2 so the nmethod goes into the beginning of Profiled heap
        int compLevel = 2;
        WB.enqueueMethodForCompilation(methodB, compLevel);
        while (WB.isMethodQueuedForCompilation(methodB)) {
            Thread.sleep(100);
        }
        if (WB.getMethodCompilationLevel(methodB) != compLevel) {
            throw new IllegalStateException("b() is not compiled at the compilation level " + compLevel +
                                            ". Got: " + WB.getMethodCompilationLevel(methodB));
        }

        // The distance from the static call stub in nmethod to the c2i adapter exceeds 128MB (AArch64 near-branch range):
        //
        //  |        Profiled                | NonNMethod |       NonProfiled              |
        //   -------------------------------- ------------ --------------------------------
        //  |[nmethod]                       |############|################[c2i]           |

        NMethod nm = NMethod.get(methodB, false);
        System.out.println("b() at 0x" + Long.toHexString(nm.address) + " heap=" + nm.code_blob_type);
        if (nm.code_blob_type != BlobType.MethodProfiled) {
            throw new RuntimeException("b() is expected to be in MethodProfiled heap, got: " + nm.code_blob_type);
        }

        // invoke compiled b(): triggers resolve_static_call_blob to patch the static call stub
        // in nmethod to point to the c2i adapter for a()
        methodB.invoke(null);
    }
}

class TestNonNMethodHeapOverflowTarget {
    static float a(float f1, double d1, long l1, int i1, float f2, double d2) {
        return f1;
    }
    static float b() {
        return a(1.0f, 2.0, 3L, 4, 5.0f, 6.0);
    }
}

/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022 SAP SE. All rights reserved.ights reserved.
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
 * @test MHIntrinsicAllocFailureTest
 * @bug 8295724
 * @requires vm.compMode == "Xmixed"
 * @requires vm.opt.TieredCompilation == null | vm.opt.TieredCompilation == true
 * @requires vm.opt.TieredStopAtLevel == null | vm.opt.TieredStopAtLevel == 4
 * @summary test allocation failure of method handle intrinsic in profiled/non-profiled space
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -XX:CompileCommand=compileonly,null::*
 *                   -XX:ReservedCodeCacheSize=16m -XX:+SegmentedCodeCache
 *                   compiler.codecache.MHIntrinsicAllocFailureTest
 */

package compiler.codecache;

import jdk.test.lib.Asserts;
import jdk.test.whitebox.WhiteBox;
import jdk.test.whitebox.code.BlobType;

import java.lang.management.MemoryPoolMXBean;

public class MHIntrinsicAllocFailureTest {
    private static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();

    private interface TestInterface {
        int testMethod(int a, int b, Object c);
    }

    private static void fillCodeCacheSegment(BlobType type) {
        // Fill with large blobs.
        MemoryPoolMXBean bean = type.getMemoryPool();
        int size = (int) (bean.getUsage().getMax() >> 7);
        while (WHITE_BOX.allocateCodeBlob(size, type.id) != 0) {}
        // Fill rest with minimal blobs.
        while (WHITE_BOX.allocateCodeBlob(1, type.id) != 0) {}
    }

    public static void main(String[] args) {
        // Lock compilation to be able to better control code cache space
        WHITE_BOX.lockCompilation();
        fillCodeCacheSegment(BlobType.MethodNonProfiled);
        fillCodeCacheSegment(BlobType.MethodProfiled);
        // JIT compilers should be off, now.
        Asserts.assertNotEquals(WHITE_BOX.getCompilationActivityMode(), 1);
        System.out.println("Code cache segments for non-profiled and profiled nmethods are full.");
        // Generate and use a MH itrinsic. Should not trigger one of the following:
        // - VirtualMachineError: Out of space in CodeCache for method handle intrinsic
        // - InternalError: java.lang.NoSuchMethodException: no such method:
        //   java.lang.invoke.MethodHandle.linkToStatic(int,int,Object,MemberName)int/invokeStatic
        TestInterface add2ints = (a, b, c) -> a + b;
        System.out.println("Result of lambda expression: " + add2ints.testMethod(1, 2, null));
        // Let GC check the code cache.
        WHITE_BOX.unlockCompilation();
        WHITE_BOX.fullGC();
    }
}

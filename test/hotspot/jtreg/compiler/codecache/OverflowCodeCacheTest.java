/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @test OverflowCodeCacheTest
 * @bug 8059550 8279356
 * @summary testing of code cache segments overflow
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -XX:CompileCommand=compileonly,null::*
 *                   -XX:-SegmentedCodeCache -Xmixed
 *                   compiler.codecache.OverflowCodeCacheTest CompilationDisabled
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -XX:CompileCommand=compileonly,null::*
 *                   -XX:+SegmentedCodeCache -Xmixed
 *                   compiler.codecache.OverflowCodeCacheTest CompilationDisabled
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -XX:-SegmentedCodeCache -Xmixed
 *                   compiler.codecache.OverflowCodeCacheTest
 */

package compiler.codecache;

import jdk.test.lib.Asserts;
import jdk.test.whitebox.WhiteBox;
import jdk.test.whitebox.code.BlobType;
import jdk.test.whitebox.code.CodeBlob;

import java.lang.management.MemoryPoolMXBean;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumSet;

class Helper {
    // Uncommon signature to prevent sharing and force creation of a new adapter
    public void method(float a, float b, float c, Object o) { }
}

public class OverflowCodeCacheTest {
    private static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();
    private static boolean COMPILATION_DISABLED = false;

    public static void main(String[] args) {
        COMPILATION_DISABLED = args.length > 0;
        EnumSet<BlobType> blobTypes = BlobType.getAvailable();
        for (BlobType type : blobTypes) {
            new OverflowCodeCacheTest(type).test();
        }
    }

    private final BlobType type;
    private final MemoryPoolMXBean bean;
    private OverflowCodeCacheTest(BlobType type) {
        this.type = type;
        this.bean = type.getMemoryPool();
    }

    private void test() {
        System.out.printf("type %s%n", type);
        System.out.println("allocating till possible...");
        ArrayList<Long> blobs = new ArrayList<>();
        int compilationActivityMode = -1;
        // Lock compilation to be able to better control code cache space
        WHITE_BOX.lockCompilation();
        try {
            long addr;
            int size = (int) (getHeapSize() >> 7);
            while ((addr = WHITE_BOX.allocateCodeBlob(size, type.id)) != 0) {
                blobs.add(addr);

                BlobType actualType = CodeBlob.getCodeBlob(addr).code_blob_type;
                if (actualType != type) {
                    // check we got allowed overflow handling
                    Asserts.assertTrue(type.allowTypeWhenOverflow(actualType),
                            type + " doesn't allow using " + actualType + " when overflow");
                }
            }
            /* now, remember compilationActivityMode to check it later, after freeing, since we
               possibly have no free cache for further work */
            compilationActivityMode = WHITE_BOX.getCompilationActivityMode();

            // Use smallest allocation size to make sure all of the available space
            // is filled up. Don't free these below to put some pressure on the sweeper.
            while ((addr = WHITE_BOX.allocateCodeBlob(1, type.id)) != 0) { }
        } finally {
            try {
                // Trigger creation of a new adapter for Helper::method
                // which will fail because we are out of code cache space.
                Helper helper = new Helper();
            } catch (VirtualMachineError e) {
                // Expected
            }
            // Free code cache space
            for (Long blob : blobs) {
                WHITE_BOX.freeCodeBlob(blob);
            }

            // Convert some nmethods to zombie and then free them to re-enable compilation
            WHITE_BOX.unlockCompilation();
            WHITE_BOX.forceNMethodSweep();
            WHITE_BOX.forceNMethodSweep();

            // Trigger compilation of Helper::method which will hit an assert because
            // adapter creation failed above due to a lack of code cache space.
            Helper helper = new Helper();
            for (int i = 0; i < 100_000; i++) {
                helper.method(0, 0, 0, null);
            }
        }
        // Only check this if compilation is disabled, otherwise the sweeper might have
        // freed enough nmethods to allow for re-enabling compilation.
        if (COMPILATION_DISABLED) {
            Asserts.assertNotEquals(compilationActivityMode, 1 /* run_compilation*/,
                    "Compilation must be disabled when CodeCache(CodeHeap) overflows");
        }
    }

    private long getHeapSize() {
        return bean.getUsage().getMax();
    }

}

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
 *
 */

import java.lang.management.MemoryPoolMXBean;
import java.util.EnumSet;
import java.util.ArrayList;

import sun.hotspot.WhiteBox;
import sun.hotspot.code.BlobType;
import sun.hotspot.code.CodeBlob;
import jdk.test.lib.Asserts;

/*
 * @test OverflowCodeCacheTest
 * @bug 8059550
 * @library /testlibrary /test/lib
 * @modules java.base/jdk.internal.misc
 * @modules java.management
 * @build OverflowCodeCacheTest
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -XX:CompileCommand=compileonly,null::*
 *                   -XX:-SegmentedCodeCache OverflowCodeCacheTest
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -XX:CompileCommand=compileonly,null::*
 *                   -XX:+SegmentedCodeCache OverflowCodeCacheTest
 * @summary testing of code cache segments overflow
 */
public class OverflowCodeCacheTest {
    private static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();

    public static void main(String[] args) {
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
            Asserts.assertNotEquals(WHITE_BOX.getCompilationActivityMode(), 1 /* run_compilation*/,
                    "Compilation must be disabled when CodeCache(CodeHeap) overflows");
        } finally {
            for (Long blob : blobs) {
                WHITE_BOX.freeCodeBlob(blob);
            }
        }
    }

    private long getHeapSize() {
        return bean.getUsage().getMax();
    }

}

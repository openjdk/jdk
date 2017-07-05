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

import java.util.ArrayList;

import sun.hotspot.code.BlobType;

/*
 * @test RandomAllocationTest
 * @library /testlibrary /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build RandomAllocationTest
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:CompileCommand=dontinline,Helper$TestCase::method
 *                   -XX:+WhiteBoxAPI -XX:-SegmentedCodeCache RandomAllocationTest
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:CompileCommand=dontinline,Helper$TestCase::method
 *                   -XX:+WhiteBoxAPI -XX:+SegmentedCodeCache RandomAllocationTest
 * @summary stressing code cache by allocating randomly sized "dummy" code blobs
 */
public class RandomAllocationTest implements Runnable {
    private static final long CODE_CACHE_SIZE
            = Helper.WHITE_BOX.getUintxVMFlag("ReservedCodeCacheSize");
    private static final int MAX_BLOB_SIZE = (int) (CODE_CACHE_SIZE >> 7);
    private static final BlobType[] BLOB_TYPES
            = BlobType.getAvailable().toArray(new BlobType[0]);

    public static void main(String[] args) {
        new CodeCacheStressRunner(new RandomAllocationTest()).runTest();
    }

    private final ArrayList<Long> blobs = new ArrayList<>();
    @Override
    public void run() {
        boolean allocate = blobs.isEmpty() || Helper.RNG.nextBoolean();
        if (allocate) {
            int type = Helper.RNG.nextInt(BLOB_TYPES.length);
            long addr = Helper.WHITE_BOX.allocateCodeBlob(
                    Helper.RNG.nextInt(MAX_BLOB_SIZE), BLOB_TYPES[type].id);
            if (addr != 0) {
                blobs.add(addr);
            }
        } else {
            int index = Helper.RNG.nextInt(blobs.size());
            Helper.WHITE_BOX.freeCodeBlob(blobs.remove(index));
        }
    }

}

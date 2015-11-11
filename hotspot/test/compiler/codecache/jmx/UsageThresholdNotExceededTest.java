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
 */

import jdk.test.lib.Asserts;
import java.lang.management.MemoryPoolMXBean;
import sun.hotspot.code.BlobType;

/*
 * @test UsageThresholdNotExceededTest
 * @library /testlibrary /test/lib
 * @modules java.base/sun.misc
 *          java.management
 * @build UsageThresholdNotExceededTest
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *     sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -Xbootclasspath/a:. -XX:-UseCodeCacheFlushing
 *     -XX:-MethodFlushing -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *     -XX:+SegmentedCodeCache -XX:CompileCommand=compileonly,null::*
 *     UsageThresholdNotExceededTest
 * @summary verifying that usage threshold not exceeded while allocating less
 *     than usage threshold
 */
public class UsageThresholdNotExceededTest {

    private final BlobType btype;

    public UsageThresholdNotExceededTest(BlobType btype) {
        this.btype = btype;
    }

    public static void main(String[] args) {
        for (BlobType btype : BlobType.getAvailable()) {
            new UsageThresholdNotExceededTest(btype).runTest();
        }
    }

    protected void runTest() {
        MemoryPoolMXBean bean = btype.getMemoryPool();
        long initialThresholdCount = bean.getUsageThresholdCount();
        long initialUsage = bean.getUsage().getUsed();
        bean.setUsageThreshold(initialUsage + 1 + CodeCacheUtils.MIN_ALLOCATION);
        CodeCacheUtils.WB.allocateCodeBlob(CodeCacheUtils.MIN_ALLOCATION
                - CodeCacheUtils.getHeaderSize(btype), btype.id);
        // a gc cycle triggers usage threshold recalculation
        CodeCacheUtils.WB.fullGC();
        CodeCacheUtils.assertEQorGTE(btype, bean.getUsageThresholdCount(), initialThresholdCount,
                String.format("Usage threshold was hit: %d times for %s. "
                        + "Threshold value: %d with current usage: %d",
                        bean.getUsageThresholdCount(), bean.getName(),
                        bean.getUsageThreshold(), bean.getUsage().getUsed()));
        System.out.println("INFO: Case finished successfully for " + bean.getName());
    }
}

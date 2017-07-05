/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test TestSmallHeap
 * @bug 8067438
 * @requires vm.gc=="null"
 * @summary Verify that starting the VM with a small heap works
 * @library /testlibrary
 * @run main/othervm -Xmx4m -XX:+UseParallelGC TestSmallHeap
 * @run main/othervm -Xmx4m -XX:+UseSerialGC TestSmallHeap
 * @run main/othervm -Xmx4m -XX:+UseG1GC TestSmallHeap
 * @run main/othervm -Xmx4m -XX:+UseConcMarkSweepGC -XX:CMSMarkStackSizeMax=1032 TestSmallHeap
 *
 * Note: It would be nice to verify the minimal supported heap size here,
 * but that turns out to be quite tricky since we align the heap size based
 * on the card table size. And the card table size is aligned based on the
 * minimal pages size provided by the os. This means that on most platforms,
 * where the minimal page size is 4k, we get a minimal heap size of 2m but
 * on Solaris/Sparc we have a page size of 8k and get a minimal heap size
 * of 8m.
 * There is also no check in the VM for verifying that the maximum heap size
 * is larger than the supported minimal heap size. This means that specifying
 * -Xmx1m on the command line is fine but will give a heap of 2m (or 4m).
 * To work around these rather strange behaviors this test uses 4m for all
 * platforms.
 */

import sun.management.ManagementFactoryHelper;
import static com.oracle.java.testlibrary.Asserts.*;

public class TestSmallHeap {

    public static void main(String[] args) {
        String maxHeap = ManagementFactoryHelper.getDiagnosticMXBean().getVMOption("MaxHeapSize").getValue();
        String expectedMaxHeap = "4194304";
        assertEQ(maxHeap, expectedMaxHeap);
    }
}

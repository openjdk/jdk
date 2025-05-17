/*
 * Copyright (c) 2025, Google LLC. All rights reserved.
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

package gc.g1;

/*
 * @test id=normal
 * @bug 8236073 8352765
 * @requires vm.gc.G1
 * @library /test/lib
 * @run main/othervm -Xmx100m -XX:MinHeapSize=4m -XX:SoftMaxHeapSize=4m
        gc.g1.TestSoftMaxHeapSizeNoOOM
 * @summary Setting SoftMaxHeapSize to a small value won't trigger
 *          OutOfMemoryError for normal allocations.
 */

/*
 * @test id=humongous
 * @bug 8236073 8352765
 * @requires vm.gc.G1
 * @library /test/lib
 * @run main/othervm -Xmx100m -XX:MinHeapSize=4m -XX:SoftMaxHeapSize=4m
        -Dhumongous=true gc.g1.TestSoftMaxHeapSizeNoOOM
 * @summary Setting SoftMaxHeapSize to a small value won't trigger
 *          OutOfMemoryError for humongous allocations.
 */

import java.util.ArrayList;

public class TestSoftMaxHeapSizeNoOOM {

  private static final long ALLOCATED_BYTES = 20_000_000; // About 20M
  private static final int OBJECT_SIZE = 1000;
  private static final int ITERATIONS = 100000;
  private static final int HUMONGOUS_OBJECT_SIZE = 1_500_000; // About 1.5M
  private static final int HUMONGOUS_ITERATIONS = 1000;

  private static final ArrayList<byte[]> holder = new ArrayList<>();

  private static void work(int objSize, int iterations) {
    long count = ALLOCATED_BYTES / objSize;
    for (long i = 0; i < count; ++i) {
      holder.add(new byte[objSize]);
    }
    // Mutate old objects while allocating new objects.
    // This is effective to trigger concurrent collections for G1,
    // and is necessary to reproduce OutOfMemoryError in JDK-8352765.
    for (long i = 0; i < iterations; ++i) {
      holder.remove(0);
      holder.add(new byte[objSize]);
    }
  }

  public static void main(String[] args) throws Exception {
    if (Boolean.getBoolean("humongous")) {
      work(HUMONGOUS_OBJECT_SIZE, HUMONGOUS_ITERATIONS);
    } else {
      work(OBJECT_SIZE, ITERATIONS);
    }
  }
}

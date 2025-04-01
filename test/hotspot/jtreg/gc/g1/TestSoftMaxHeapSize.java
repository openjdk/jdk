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
 * @test
 * @bug 8236073
 * @requires vm.gc.G1
 * @requires vm.opt.ExplicitGCInvokesConcurrent != true
 * @library /test/lib
 * @run main/othervm -Xmx200m -XX:MinHeapSize=4m -XX:MinHeapFreeRatio=99
        -XX:MaxHeapFreeRatio=99 gc.g1.TestSoftMaxHeapSize
 * @summary SoftMaxHeapSize should limit G1's heap size when resizing.
 */

import java.lang.management.ManagementFactory;
import java.util.LinkedList;
import java.util.List;
import jdk.test.lib.dcmd.PidJcmdExecutor;

public class TestSoftMaxHeapSize {

  private static final int OBJECT_SIZE = 1000;
  private static final long ALLOCATED_BYTES = 20_000_000; // About 20M
  private static final long SOFT_MAX_HEAP =
      50 * 1024 * 1024; // 50MiB, leaving ~30MiB headroom above ALLOCATED_BYTES.

  private static final List<byte[]> holder = new LinkedList<>();

  private static long getCurrentHeapSize() {
    return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getCommitted();
  }

  public static void main(String[] args) throws Exception {

    long count = ALLOCATED_BYTES / OBJECT_SIZE;
    for (long i = 0; i < count; ++i) {
      holder.add(new byte[OBJECT_SIZE]);
    }

    System.gc();
    long heapSize = getCurrentHeapSize();
    if (heapSize != ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getMax()) {
      throw new RuntimeException(
          "Heap size did not fully expand to Xmx after full GC: heapSize = " + heapSize);
    }

    PidJcmdExecutor jcmd = new PidJcmdExecutor();
    jcmd.execute("VM.set_flag SoftMaxHeapSize " + SOFT_MAX_HEAP, true);

    System.gc();
    heapSize = getCurrentHeapSize();
    if (heapSize != SOFT_MAX_HEAP) {
      throw new RuntimeException(
          "Heap size did not shrink to SoftMaxHeapSize after full GC: heapSize = " + heapSize);
    }
  }
}

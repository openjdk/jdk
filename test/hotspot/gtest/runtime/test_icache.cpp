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

#include "runtime/icache.hpp"
#include "runtime/os.hpp"
#include "testutils.hpp"
#include "unittest.hpp"

TEST_VM(ICacheTest, flush_perf) {
  // Warmup infra a little.
  {
    address p = (address)os::malloc(1024, mtTest);
    ICache::invalidate_range(p, 1024);
    os::free(p);
  }

  const int ITERS = 1000;

  for (int size = 256; size <= 256*1024; size *= 2) {
    address p = (address)os::malloc(size, mtTest);
    EXPECT_NOT_NULL(p);

    jlong total_flush = 0;
    jlong total_readback = 0;
    for (int t = 0; t < ITERS; t++) {
      // Force writes to mark cache lines modified.
      memset(p, 1, size);

      jlong time1 = os::javaTimeNanos();

      // Flush the cache under the memory.
      ICache::invalidate_range(p, size);

      jlong time2 = os::javaTimeNanos();

      // Read memory back.
      EXPECT_NULL(memchr(p, 0, size));

      jlong time3 = os::javaTimeNanos();

      total_flush += time2 - time1;
      total_readback += time3 - time2;
    }

    tty->print_cr("%10d bytes flushed in " JLONG_FORMAT_W(10) " ns, read back in " JLONG_FORMAT_W(10) " ns",
                  size, total_flush / ITERS, total_readback / ITERS);

    os::free(p);
  }
}

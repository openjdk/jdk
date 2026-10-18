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
 *
 */

#include "runtime/atomicAccess.hpp"
#include "threadHelper.inline.hpp"
#include "unittest.hpp"

// Check values and return conventions for each supported memory order.
// These functional checks do not establish memory ordering or atomicity.
template<typename T>
static void test_atomic_bitops() {
  const T values[] = {
    static_cast<T>(0),
    static_cast<T>(1),
    static_cast<T>(0x7f),
    static_cast<T>(0x80),
    static_cast<T>(0x7fffffff),
    static_cast<T>(0x80000000u),
    static_cast<T>(UCONST64(0x8000000000000000)),
    static_cast<T>(UCONST64(0x55aa55aaaa55aa55)),
    static_cast<T>(~UCONST64(0))
  };
  const atomic_memory_order orders[] = {
    memory_order_relaxed,
    memory_order_acquire,
    memory_order_release,
    memory_order_acq_rel,
    memory_order_seq_cst,
    memory_order_conservative
  };

  for (atomic_memory_order order : orders) {
    SCOPED_TRACE(order);
    for (T initial : values) {
      SCOPED_TRACE(initial);
      for (T bits : values) {
        SCOPED_TRACE(bits);
        volatile T value = initial;
        const T and_result = initial & bits;
        const T or_result = initial | bits;
        const T xor_result = initial ^ bits;

        EXPECT_EQ(initial, AtomicAccess::fetch_then_and(&value, bits, order));
        EXPECT_EQ(and_result, AtomicAccess::load(&value));
        AtomicAccess::store(&value, initial);
        EXPECT_EQ(and_result, AtomicAccess::and_then_fetch(&value, bits, order));
        EXPECT_EQ(and_result, AtomicAccess::load(&value));

        AtomicAccess::store(&value, initial);
        EXPECT_EQ(initial, AtomicAccess::fetch_then_or(&value, bits, order));
        EXPECT_EQ(or_result, AtomicAccess::load(&value));
        AtomicAccess::store(&value, initial);
        EXPECT_EQ(or_result, AtomicAccess::or_then_fetch(&value, bits, order));
        EXPECT_EQ(or_result, AtomicAccess::load(&value));

        AtomicAccess::store(&value, initial);
        EXPECT_EQ(initial, AtomicAccess::fetch_then_xor(&value, bits, order));
        EXPECT_EQ(xor_result, AtomicAccess::load(&value));
        AtomicAccess::store(&value, initial);
        EXPECT_EQ(xor_result, AtomicAccess::xor_then_fetch(&value, bits, order));
        EXPECT_EQ(xor_result, AtomicAccess::load(&value));
      }
    }
  }
}

TEST_VM(AtomicAccessBitops, int8) {
  test_atomic_bitops<int8_t>();
}

TEST_VM(AtomicAccessBitops, uint8) {
  test_atomic_bitops<uint8_t>();
}

TEST_VM(AtomicAccessBitops, int32) {
  test_atomic_bitops<int32_t>();
}

TEST_VM(AtomicAccessBitops, uint32) {
  test_atomic_bitops<uint32_t>();
}

TEST_VM(AtomicAccessBitops, int64) {
  test_atomic_bitops<int64_t>();
}

TEST_VM(AtomicAccessBitops, uint64) {
  test_atomic_bitops<uint64_t>();
}

// Each worker modifies its own bit in shared words. A non-atomic update can
// lose another worker's bit, even though each bit has only one writer.
template<typename T>
static void test_concurrent_atomic_bitops() {
  constexpr int threads = 8;
  constexpr int length = 2048;
  const T high_bit = T(1) << (sizeof(T) * BitsPerByte - 1);
  const T mask = high_bit | T(0x7f);
  volatile T values[length];

  for (atomic_memory_order order : {memory_order_relaxed, memory_order_conservative}) {
    SCOPED_TRACE(order);
    for (int operation = 0; operation < 6; ++operation) {
      SCOPED_TRACE(operation);
      const bool is_and = operation < 2;
      for (int i = 0; i < length; ++i) {
        values[i] = is_and ? ~T(0) : T(0);
      }

      Semaphore start;
      auto worker = [&](Thread*, int id) {
        SCOPED_TRACE(order);
        SCOPED_TRACE(operation);
        SCOPED_TRACE(id);
        const T bit = id == threads - 1 ? high_bit : T(1) << id;
        const T clear_bit = ~bit;
        start.wait();
        for (int i = 0; i < length; ++i) {
          T result;
          switch (operation) {
          case 0: result = AtomicAccess::fetch_then_and(&values[i], clear_bit, order); break;
          case 1: result = AtomicAccess::and_then_fetch(&values[i], clear_bit, order); break;
          case 2: result = AtomicAccess::fetch_then_or(&values[i], bit, order); break;
          case 3: result = AtomicAccess::or_then_fetch(&values[i], bit, order); break;
          case 4: result = AtomicAccess::fetch_then_xor(&values[i], bit, order); break;
          default: result = AtomicAccess::xor_then_fetch(&values[i], bit, order); break;
          }
          const bool returns_old = (operation % 2) == 0;
          EXPECT_EQ((is_and == returns_old) ? bit : T(0), T(result & bit)) << "index: " << i;
        }
      };
      TestThreadGroup<decltype(worker)> workers(worker, threads);
      workers.doit();
      start.signal(threads);
      workers.join();

      const T expected = is_and ? ~mask : mask;
      for (int i = 0; i < length; ++i) {
        EXPECT_EQ(expected, AtomicAccess::load(&values[i])) << "index: " << i;
      }
    }
  }
}

TEST_VM(AtomicAccessBitops, concurrent_uint32) {
  test_concurrent_atomic_bitops<uint32_t>();
}

TEST_VM(AtomicAccessBitops, concurrent_uint64) {
  test_concurrent_atomic_bitops<uint64_t>();
}

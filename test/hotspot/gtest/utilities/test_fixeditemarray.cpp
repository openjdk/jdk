/*
 * Copyright (c) 2023, Red Hat, Inc. and/or its affiliates.
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "memory/allocation.hpp"
#include "runtime/os.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"
#include "utilities/fixedItemArray.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

#include "unittest.hpp"

//#define LOG_PLEASE
#include "testutils.hpp"

template <class T, unsigned size>
class Pointers {
  T* _v[size];
public:
  Pointers() {
    for (unsigned i = 0; i < size; i++) {
      _v[i] = nullptr;
    }
  }

  T** at(unsigned idx) {
    assert(idx < size, "Sanity");
    return _v + idx;
  }
};

template <class T, unsigned items_per_slab, unsigned max_slabs, class Allocator>
class FixedItemArrayTest {

  static const int max_items = items_per_slab * max_slabs;
  Pointers<T, max_items> _pointers;

  typedef FixedItemArray<T, items_per_slab, max_slabs, Allocator> FiaType;
  FiaType _fia;

#define SOMETIMES_VERIFY(n) DEBUG_ONLY({ static int c = 0; if (((++c) % n) == 0) { _fia.verify(); } })

  static uint8_t ptr2mark(void* p) {
    return (uint8_t)(p2i(p) & 0xff);
  }

  T* allocate_with_test() {
    unsigned free1 = _fia.num_free();
    unsigned allocated1 = _fia.num_allocated();
    T* p = _fia.allocate();
    EXPECT_NOT_NULL(p);
    EXPECT_EQ(allocated1 + 1, _fia.num_allocated());
    if (free1 > 0) {
      EXPECT_EQ(free1 - 1, _fia.num_free());
    }
    // Every allocation should be correctly aligned
    EXPECT_TRUE(is_aligned(p, alignof(T))) <<
                "expected alignment: " << alignof(T) << " but got " << p2i(p) << ".";
    DEBUG_ONLY(EXPECT_TRUE(_fia.contains(p));)
    GtestUtils::mark_range_with(p, sizeof(T), GtestUtils::ptr2mark(p));
    SOMETIMES_VERIFY(5);
    return p;
  }

  bool allocate_with_test_at_slot(unsigned idx) {
    T** pp = _pointers.at(idx);
    if (*pp == nullptr) {
      *pp = allocate_with_test();
      return true;
    }
    return false;
  }

  void allocate_all_slots() {
    for (unsigned i = 0; i < max_items; i ++) {
      allocate_with_test_at_slot(i);
    }
    check_fia_full();
    check_all_slots();
  }

  void deallocate_with_test(T* p) {
    check_item(p);
    unsigned free1 = _fia.num_free();
    _fia.deallocate(p);
    EXPECT_EQ(free1 + 1, _fia.num_free());
    SOMETIMES_VERIFY(5);
  }

  bool deallocate_with_test_at_slot(unsigned idx) {
    T** pp = _pointers.at(idx);
    if (*pp != nullptr) {
      deallocate_with_test(*pp);
      *pp = nullptr;
      return true;
    }
    return false;
  }

  void deallocate_all_slots() {
    for (unsigned i = 0; i < max_items; i ++) {
      deallocate_with_test_at_slot(i);
    }
  }

  void deallocate_every_nth_slot(unsigned n, unsigned startidx) {
    for (unsigned i = startidx; i < max_items; i += n) {
      deallocate_with_test_at_slot(i);
    }
  }

  void allocate_or_deallocate_at_slot(unsigned idx) {
    bool success = allocate_with_test_at_slot(idx) || deallocate_with_test_at_slot(idx);
    assert(success, "one of these should have worked");
  }

  static void check_item(T* p) {
    EXPECT_RANGE_IS_MARKED_WITH(p, sizeof(T), GtestUtils::ptr2mark(p));
  }

  void check_item_at_slot(unsigned idx) {
    T** pp = _pointers.at(idx);
    EXPECT_RANGE_IS_MARKED_WITH(*pp, sizeof(T), GtestUtils::ptr2mark(*pp));
    DEBUG_ONLY(EXPECT_TRUE(_fia.contains(*pp)));
  }

  void check_all_slots() {
    for (unsigned i = 0; i < max_items; i ++) {
      check_item_at_slot(i);
    }
  }

  void check_every_nth_slot(unsigned n, unsigned startidx) {
    for (unsigned i = startidx; i < max_items; i += n) {
      check_item_at_slot(i);
    }
  }

  void check_fia_usage(unsigned expected_allocated, unsigned expected_free, unsigned expected_num_slabs) const {
    EXPECT_EQ(_fia.num_allocated(), expected_allocated);
    EXPECT_EQ(_fia.num_free(), expected_free);
    EXPECT_EQ(_fia.num_slabs(), expected_num_slabs);
  }

  void check_fia_full() {
    check_fia_usage(max_items, 0, max_slabs);
    // We should not be able to alloc more
    EXPECT_NULL(_fia.allocate());
  }

  void print_fia(int run, int line) {
    LOG_HERE("run %d line %d - Allocated: %u Free: %u slabs: %u, footprint: " SIZE_FORMAT,
             run, line, _fia.num_allocated(), _fia.num_free(), _fia.num_slabs(), _fia.footprint());
  }

#define LOGFIA print_fia(run, __LINE__);

public:

  FixedItemArrayTest() {
    LOG_HERE("Data size: %u, alignment requirement: %u, malloc alignment: %d",
             (unsigned)sizeof(T), (unsigned)alignof(T), minimum_malloc_alignment);
  }

  void test_random() {
    for (int run = 0; run <= max_items * 5; run++) {
      const unsigned idx = (unsigned)os::random() % max_items;
      allocate_or_deallocate_at_slot(idx);
      if ((run % max_items) == 0) {
        LOGFIA;
      }
    }
  }

  void test_breathe_in_breathe_out() {

    for (int run = 0; run < 3; run ++) {

      if (run > 0) {
        check_fia_usage(0, max_items, max_slabs);
      } else {
        check_fia_usage(0, 0, 0);
      }

      // 1. allocate fully
      LOGFIA
      allocate_all_slots();
      DEBUG_ONLY(_fia.verify();)

      // 2. Deallocate half of items
      if (max_items > 1) {
        LOGFIA
        deallocate_every_nth_slot(2, 0);
        check_fia_usage(max_items / 2, max_items / 2, max_slabs);
        check_every_nth_slot(2, 1); // Check remaining elements
        DEBUG_ONLY(_fia.verify();)

        // 3. allocate fully
        LOGFIA
        allocate_all_slots();
        DEBUG_ONLY(_fia.verify();)
      }

      // 4. Deallocate all
      LOGFIA
      deallocate_all_slots();
      check_fia_usage(0, max_items, max_slabs);
      DEBUG_ONLY(_fia.verify();)
    }
  }
};

struct Data { void* dummy; void* dummy2; };

// Data whith unaligned size
struct CrookedSizedData { char bytes[13]; };

// Data with user defined ctors
struct NonPODData {
  volatile void* const p;
  volatile static int num_ctor_dtor_calls;
  NonPODData() : p(this) { num_ctor_dtor_calls++; }
  ~NonPODData() { num_ctor_dtor_calls++; }
};
volatile int NonPODData::num_ctor_dtor_calls = 0;

// Data with large (but still natural) alignment requirement
union LargeAlignmentData {
  long double d;
  uint32_t i32;
  uint64_t i64;
  void* p;
};

#define DEF_FIATEST(type, name, items_per_slab, max_slabs, allocator)                              \
TEST_VM(FixedItemArray, FIAtest_##name##_##type##_##items_per_slab##_##max_slabs##_##allocator) {  \
  FixedItemArrayTest<type, items_per_slab, max_slabs, allocator> test;                             \
  test.test_##name();                                                                              \
}

#define DEF_FIATEST_ALL(type, items_per_slab, max_slabs, allocator)                                \
    DEF_FIATEST(type, breathe_in_breathe_out, items_per_slab, max_slabs, allocator)                \
    DEF_FIATEST(type, random, items_per_slab, max_slabs, allocator)

#define DEF_FIATESTS_FOR_TYPE(type) \
    DEF_FIATEST_ALL(type, 1, 1, CHeapAllocator) \
    DEF_FIATEST_ALL(type, 256, 5, CHeapAllocator) \
    DEF_FIATEST_ALL(type, 256, 5, RawCHeapAllocator)

// type smaller than pointer sized
DEF_FIATESTS_FOR_TYPE(char)
// pointer sized
DEF_FIATESTS_FOR_TYPE(uintptr_t)
// pod
DEF_FIATESTS_FOR_TYPE(Data)
// crooked sized pod
DEF_FIATESTS_FOR_TYPE(CrookedSizedData)
// nonpod
DEF_FIATESTS_FOR_TYPE(NonPODData)
// large alignment
DEF_FIATESTS_FOR_TYPE(LargeAlignmentData)

TEST_VM(FixedItemArray, FIAtest_limitless) {
  // Test that max_slabs = 0 means infinite slabs.
  FixedItemArray<int, 10, 0> fia;
  for (int i = 0; i < 40; i++) {
    ASSERT_NOT_NULL(fia.allocate());
  }
}

TEST_VM(FixedItemArray, FIAtest_nonpod) {
  // Test that allocation does not call constructors
  {
    FixedItemArray<NonPODData, 10, 0> fia;
    for (int i = 0; i < 40; i++) {
      ASSERT_NOT_NULL(fia.allocate());
    }
    ASSERT_EQ(NonPODData::num_ctor_dtor_calls, 0);
  }
  ASSERT_EQ(NonPODData::num_ctor_dtor_calls, 0);
}

// Release tests
struct TestResetAllocator {
  static int num_outstanding_allocs;
  static int num_peak_outstanding_allocs;
  static void* allocate(size_t l) {
    // we should never see deallocations before an allocation
    EXPECT_EQ(num_outstanding_allocs, num_peak_outstanding_allocs);
    num_outstanding_allocs++;
    num_peak_outstanding_allocs++;
    EXPECT_LE(num_outstanding_allocs, 5); // 4 slabs and the pool itself.
    return (char*)os::malloc(l, mtTest);
  }
  static void deallocate(void* p) {
    EXPECT_GT(num_outstanding_allocs, 0);
    os::free(p);
    num_outstanding_allocs--;
  }
};
int TestResetAllocator::num_outstanding_allocs = 0;
int TestResetAllocator::num_peak_outstanding_allocs = 0;

TEST_VM(FixedItemArray, FIAtest_allocator) {
  // Test that we allocate from Allocator correctly, and that we release everything upon delete
  EXPECT_EQ(TestResetAllocator::num_outstanding_allocs, 0);
  EXPECT_EQ(TestResetAllocator::num_peak_outstanding_allocs, 0);
  {
    typedef FixedItemArray<uint64_t, 10, 4, TestResetAllocator> MyFiaType;
    MyFiaType* fia = new MyFiaType;
    // We should see one allocation, from the new itself
    EXPECT_EQ(TestResetAllocator::num_outstanding_allocs, 1);
    EXPECT_EQ(TestResetAllocator::num_peak_outstanding_allocs, 1);
    for (int i = 0; i < 40; i++) {
      EXPECT_NOT_NULL(fia->allocate());
    }
    // We should see 4 additional allocations for four slabs.
    EXPECT_EQ(TestResetAllocator::num_outstanding_allocs, 5);
    EXPECT_EQ(TestResetAllocator::num_peak_outstanding_allocs, 5);
    delete fia;
  }
  // All allocations should have been returned to the pool.
  EXPECT_EQ(TestResetAllocator::num_outstanding_allocs, 0);
  EXPECT_EQ(TestResetAllocator::num_peak_outstanding_allocs, 5);
}

#ifdef ASSERT
// check that we correctly assert if an allocator returns badly aligned memory
struct BrokenAllocator {
  static void* allocate(size_t l) {
    return (char*)os::malloc(l + 1, mtTest) + 1;
  }
  static void deallocate(void* p) {}
};
TEST_VM_ASSERT_MSG(FixedItemArray, broken_allocator_assert, ".*bad alignment.*") {
  FixedItemArray<LargeAlignmentData, 10, 0, BrokenAllocator> fia;
  fia.allocate();
}
#endif // ASSERT

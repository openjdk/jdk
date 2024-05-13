/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "nmt/nmtTreap.hpp"
#include "runtime/os.hpp"
#include "unittest.hpp"

class TreapTest : public testing::Test {
public:
  struct Cmp {
    static int cmp(int a, int b) {
      return a - b;
    }
  };

#ifdef ASSERT
  template<typename K, typename V, typename CMP, typename ALLOC>
  void verify_it(Treap<K, V, CMP, ALLOC>& t) {
    t.verify_self();
  }
#endif // ASSERT

public:
  void inserting_duplicates_results_in_one_value() {
    constexpr const int up_to = 10;
    GrowableArrayCHeap<int, mtTest> nums_seen(up_to, up_to, 0);
    TreapCHeap<int, int, Cmp> treap;

    for (int i = 0; i < up_to; i++) {
      treap.upsert(i, i);
      treap.upsert(i, i);
      treap.upsert(i, i);
      treap.upsert(i, i);
      treap.upsert(i, i);
    }

    treap.visit_in_order([&](TreapCHeap<int, int, Cmp>::TreapNode* node) {
      nums_seen.at(node->key())++;
    });
    for (int i = 0; i < up_to; i++) {
      EXPECT_EQ(1, nums_seen.at(i));
    }
  }

  void treap_ought_not_leak() {
    struct LeakCheckedAllocator {
      struct Check {
        void* ptr;
        bool released;

        Check(void* ptr)
          : ptr(ptr),
            released(false) {
        }

        Check()
          : ptr(nullptr),
            released(false) {}

        void release() {
          released = true;
        }
      };
      GrowableArrayCHeap<Check, mtTest> allocations;

      LeakCheckedAllocator()
        : allocations() {
      }

      void* allocate(size_t sz) {
        void* allocation = os::malloc(sz, mtTest);
        if (allocation == nullptr) {
          vm_exit_out_of_memory(sz, OOM_MALLOC_ERROR, "treap failed allocation");
        }
        allocations.push(Check(allocation));
        return allocation;
      }

      void free(void* ptr) {
        for (int i = 0; i < allocations.length(); i++) {
          Check& c = allocations.at(i);
          EXPECT_NE(nullptr, c.ptr);
          if (c.ptr == ptr) {
            c.release();
          }
        }
        os::free(ptr);
      }
    };

    constexpr const int up_to = 10;
    {
      Treap<int, int, Cmp, LeakCheckedAllocator> treap;
      for (int i = 0; i < 10; i++) {
        treap.upsert(i, i);
      }
      for (int i = 0; i < 10; i++) {
        treap.remove(i);
      }
      EXPECT_EQ(10, treap._allocator.allocations.length());
      for (int i = 0; i < 10; i++) {
        EXPECT_TRUE(treap._allocator.allocations.at(i).released);
      }
    }

    {
      Treap<int, int, Cmp, LeakCheckedAllocator> treap;
      for (int i = 0; i < 10; i++) {
        treap.upsert(i, i);
      }
      treap.remove_all();
      EXPECT_EQ(10, treap._allocator.allocations.length());
      for (int i = 0; i < 10; i++) {
        EXPECT_TRUE(treap._allocator.allocations.at(i).released);
      }
    }
  }
};

TEST_VM_F(TreapTest, InsertingDuplicatesResultsInOneValue) {
  this->inserting_duplicates_results_in_one_value();
}

TEST_VM_F(TreapTest, TreapOughtNotLeak) {
  this->treap_ought_not_leak();
}

TEST_VM_F(TreapTest, TestVisitInRange) {
  TreapCHeap<int, int, Cmp> treap;
  using Node = TreapCHeap<int, int, Cmp>::TreapNode;

  treap.visit_range_in_order(0, 100, [&](Node* x) {
    EXPECT_TRUE(false) << "Empty treap has no nodes to visit";
  });

  // Single-element set
  treap.upsert(1, 0);
  int count = 0;
  treap.visit_range_in_order(0, 100, [&](Node* x) {
    count++;
  });
  EXPECT_EQ(1, count);

  // Add an element outside of the range that should not be visited on the right side and
  // one on the left side.
  treap.upsert(101, 0);
  treap.upsert(-1, 0);
  count = 0;
  treap.visit_range_in_order(0, 100, [&](Node* x) {
    count++;
  });
  EXPECT_EQ(1, count);

  // Visiting empty range [0, 0) == {}
  treap.upsert(0, 0); // This node should not be visited.
  treap.visit_range_in_order(0, 0, [&](Node* x) {
    EXPECT_TRUE(false) << "Empty visiting range should not visit any node";
  });
}

#ifdef ASSERT

TEST_VM_F(TreapTest, VerifyItThroughStressTest) {
  TreapCHeap<int, int,Cmp> treap;
  // Really hammer a Treap
  int ten_thousand = 10000;
  for (int i = 0; i < ten_thousand; i++) {
    int r = os::random();
    if (r >= 0) {
      treap.upsert(i, i);
    } else {
      treap.remove(i);
    }
    verify_it(treap);
  }
  for (int i = 0; i < ten_thousand; i++) {
    int r = os::random();
    if (r >= 0) {
      treap.upsert(i, i);
    } else {
      treap.remove(i);
    }
    verify_it(treap);
  }
}

#endif // ASSERT

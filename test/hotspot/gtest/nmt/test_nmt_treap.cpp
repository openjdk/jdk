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
#include "memory/resourceArea.hpp"
#include "nmt/nmtTreap.hpp"
#include "runtime/os.hpp"
#include "unittest.hpp"

class NMTTreapTest : public testing::Test {
public:
  struct Cmp {
    static int cmp(int a, int b) {
      return a - b;
    }
  };

  struct CmpInverse {
    static int cmp(int a, int b) {
      return b - a;
    }
  };

  struct FCmp {
    static int cmp(float a, float b) {
      if (a < b) return -1;
      if (a == b) return 0;
      return 1;
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
      int allocations;

      LeakCheckedAllocator()
        : allocations(0) {
      }

      void* allocate(size_t sz) {
        void* allocation = os::malloc(sz, mtTest);
        if (allocation == nullptr) {
          vm_exit_out_of_memory(sz, OOM_MALLOC_ERROR, "treap failed allocation");
        }
        ++allocations;
        return allocation;
      }

      void free(void* ptr) {
        --allocations;
        os::free(ptr);
      }
    };

    constexpr const int up_to = 10;
    {
      Treap<int, int, Cmp, LeakCheckedAllocator> treap;
      for (int i = 0; i < up_to; i++) {
        treap.upsert(i, i);
      }
      EXPECT_EQ(up_to, treap._allocator.allocations);
      for (int i = 0; i < up_to; i++) {
        treap.remove(i);
      }
      EXPECT_EQ(0, treap._allocator.allocations);
      EXPECT_EQ(nullptr, treap._root);
    }

    {
      Treap<int, int, Cmp, LeakCheckedAllocator> treap;
      for (int i = 0; i < up_to; i++) {
        treap.upsert(i, i);
      }
      treap.remove_all();
      EXPECT_EQ(0, treap._allocator.allocations);
      EXPECT_EQ(nullptr, treap._root);
    }
  }

  void test_find() {
    struct Empty {};
    TreapCHeap<float, Empty, FCmp> treap;
    using Node = TreapCHeap<float, Empty, FCmp>::TreapNode;

    Node* n = nullptr;
    auto test = [&](float f) {
      EXPECT_EQ(nullptr, treap.find(treap._root, f));
      treap.upsert(f, Empty{});
      Node* n = treap.find(treap._root, f);
      EXPECT_NE(nullptr, n);
      EXPECT_EQ(f, n->key());
    };

    test(1.0f);
    test(5.0f);
    test(0.0f);
  }
};

TEST_VM_F(NMTTreapTest, InsertingDuplicatesResultsInOneValue) {
  this->inserting_duplicates_results_in_one_value();
}

TEST_VM_F(NMTTreapTest, TreapOughtNotLeak) {
  this->treap_ought_not_leak();
}

TEST_VM_F(NMTTreapTest, TestVisitors) {
  { // Tests with 'default' ordering (ascending)
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

    count = 0;
    treap.visit_in_order([&](Node* x) {
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

    count = 0;
    treap.visit_in_order([&](Node* x) {
      count++;
    });
    EXPECT_EQ(3, count);

    // Visiting empty range [0, 0) == {}
    treap.upsert(0, 0); // This node should not be visited.
    treap.visit_range_in_order(0, 0, [&](Node* x) {
      EXPECT_TRUE(false) << "Empty visiting range should not visit any node";
    });

    treap.remove_all();
    for (int i = 0; i < 11; i++) {
      treap.upsert(i, 0);
    }

    ResourceMark rm;
    GrowableArray<int> seen;
    treap.visit_range_in_order(0, 10, [&](Node* x) {
      seen.push(x->key());
    });
    EXPECT_EQ(10, seen.length());
    for (int i = 0; i < 10; i++) {
      EXPECT_EQ(i, seen.at(i));
    }

    seen.clear();
    treap.visit_in_order([&](Node* x) {
      seen.push(x->key());
    });
    EXPECT_EQ(11, seen.length());
    for (int i = 0; i < 10; i++) {
      EXPECT_EQ(i, seen.at(i));
    }

    seen.clear();
    treap.visit_range_in_order(10, 12, [&](Node* x) {
      seen.push(x->key());
    });
    EXPECT_EQ(1, seen.length());
    EXPECT_EQ(10, seen.at(0));
  }
  { // Test with descending ordering
    TreapCHeap<int, int, CmpInverse> treap;
    using Node = TreapCHeap<int, int, CmpInverse>::TreapNode;

    for (int i = 0; i < 10; i++) {
      treap.upsert(i, 0);
    }
    ResourceMark rm;
    GrowableArray<int> seen;
    treap.visit_range_in_order(9, -1, [&](Node* x) {
      seen.push(x->key());
    });
    EXPECT_EQ(10, seen.length());
    for (int i = 0; i < 10; i++) {
      EXPECT_EQ(10-i-1, seen.at(i));
    }
    seen.clear();

    treap.visit_in_order([&](Node* x) {
      seen.push(x->key());
    });
    EXPECT_EQ(10, seen.length());
    for (int i = 0; i < 10; i++) {
      EXPECT_EQ(10 - i - 1, seen.at(i));
    }
  }
}

TEST_VM_F(NMTTreapTest, TestFind) {
  test_find();
}

TEST_VM_F(NMTTreapTest, TestClosestLeq) {
  using Node = TreapCHeap<int, int, Cmp>::TreapNode;
  {
    TreapCHeap<int, int, Cmp> treap;
    Node* n = treap.closest_leq(0);
    EXPECT_EQ(nullptr, n);

    treap.upsert(0, 0);
    n = treap.closest_leq(0);
    EXPECT_EQ(0, n->key());

    treap.upsert(-1, -1);
    n = treap.closest_leq(0);
    EXPECT_EQ(0, n->key());

    treap.upsert(6, 0);
    n = treap.closest_leq(6);
    EXPECT_EQ(6, n->key());

    n = treap.closest_leq(-2);
    EXPECT_EQ(nullptr, n);
  }
}

#ifdef ASSERT

TEST_VM_F(NMTTreapTest, VerifyItThroughStressTest) {
  { // Repeatedly verify a treap of moderate size
    TreapCHeap<int, int, Cmp> treap;
    constexpr const int ten_thousand = 10000;
    for (int i = 0; i < ten_thousand; i++) {
      int r = os::random();
      if (r % 2 == 0) {
        treap.upsert(i, i);
      } else {
        treap.remove(i);
      }
      if (i % 100 == 0) {
        verify_it(treap);
      }
    }
    for (int i = 0; i < ten_thousand; i++) {
      int r = os::random();
      if (r % 2 == 0) {
        treap.upsert(i, i);
      } else {
        treap.remove(i);
      }
      if (i % 100 == 0) {
        verify_it(treap);
      }
    }
  }
  { // Make a very large treap and verify at the end
  struct Nothing {};
    TreapCHeap<int, Nothing, Cmp> treap;
    constexpr const int one_hundred_thousand = 100000;
    for (int i = 0; i < one_hundred_thousand; i++) {
      treap.upsert(i, Nothing());
    }
    verify_it(treap);
  }
}

#endif // ASSERT

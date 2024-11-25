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
#include "runtime/os.hpp"
#include "testutils.hpp"
#include "unittest.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/rbTree.hpp"

class RBTreeTest : public testing::Test {
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
  void verify_it(RBTree<K, V, CMP, ALLOC>& t) {
    t.verify_self();
  }
#endif // ASSERT

public:
  void inserting_duplicates_results_in_one_value() {
    constexpr const int up_to = 10;
    GrowableArrayCHeap<int, mtTest> nums_seen(up_to, up_to, 0);
    RBTreeCHeap<int, int, Cmp> rbtree;

    for (int i = 0; i < up_to; i++) {
      rbtree.upsert(i, i);
      rbtree.upsert(i, i);
      rbtree.upsert(i, i);
      rbtree.upsert(i, i);
      rbtree.upsert(i, i);
    }

    rbtree.visit_in_order([&](RBTreeCHeap<int, int, Cmp>::RBNode* node) {
      nums_seen.at(node->key())++;
    });
    for (int i = 0; i < up_to; i++) {
      EXPECT_EQ(1, nums_seen.at(i));
    }
  }

  void rbtree_ought_not_leak() {
    struct LeakCheckedAllocator {
      int allocations;

      LeakCheckedAllocator()
        : allocations(0) {
      }

      void* allocate(size_t sz) {
        void* allocation = os::malloc(sz, mtTest);
        if (allocation == nullptr) {
          vm_exit_out_of_memory(sz, OOM_MALLOC_ERROR, "rbtree failed allocation");
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
      RBTree<int, int, Cmp, LeakCheckedAllocator> rbtree;
      for (int i = 0; i < up_to; i++) {
        rbtree.upsert(i, i);
      }
      EXPECT_EQ(up_to, rbtree._allocator.allocations);
      for (int i = 0; i < up_to; i++) {
        rbtree.remove(i);
      }
      EXPECT_EQ(0, rbtree._allocator.allocations);
      EXPECT_EQ(nullptr, rbtree._root);
    }

    {
      RBTree<int, int, Cmp, LeakCheckedAllocator> rbtree;
      for (int i = 0; i < up_to; i++) {
        rbtree.upsert(i, i);
      }
      rbtree.remove_all();
      EXPECT_EQ(0, rbtree._allocator.allocations);
      EXPECT_EQ(nullptr, rbtree._root);
    }
  }

  void test_find() {
    struct Empty {};
    RBTreeCHeap<float, Empty, FCmp> rbtree;
    using Node = RBTreeCHeap<float, Empty, FCmp>::RBNode;

    Node* n = nullptr;
    auto test = [&](float f) {
      EXPECT_EQ(nullptr, rbtree.find(rbtree._root, f));
      rbtree.upsert(f, Empty{});
      Node* n = rbtree.find(rbtree._root, f);
      EXPECT_NE(nullptr, n);
      EXPECT_EQ(f, n->key());
    };

    test(1.0f);
    test(5.0f);
    test(0.0f);
  }

  void test_visitors() {
    { // Tests with 'default' ordering (ascending)
      RBTreeCHeap<int, int, Cmp> rbtree;
      using Node = RBTreeCHeap<int, int, Cmp>::RBNode;

      rbtree.visit_range_in_order(0, 100, [&](Node* x) {
        EXPECT_TRUE(false) << "Empty rbtree has no nodes to visit";
      });

      // Single-element set
      rbtree.upsert(1, 0);
      int count = 0;
      rbtree.visit_range_in_order(0, 100, [&](Node* x) {
        count++;
      });
      EXPECT_EQ(1, count);

      count = 0;
      rbtree.visit_in_order([&](Node* x) {
        count++;
      });
      EXPECT_EQ(1, count);

      // Add an element outside of the range that should not be visited on the right side and
      // one on the left side.
      rbtree.upsert(101, 0);
      rbtree.upsert(-1, 0);
      count = 0;
      rbtree.visit_range_in_order(0, 100, [&](Node* x) {
        count++;
      });
      EXPECT_EQ(1, count);

      count = 0;
      rbtree.visit_in_order([&](Node* x) {
        count++;
      });
      EXPECT_EQ(3, count);

      // Visiting empty range [0, 0) == {}
      rbtree.upsert(0, 0); // This node should not be visited.
      rbtree.visit_range_in_order(0, 0, [&](Node* x) {
        EXPECT_TRUE(false) << "Empty visiting range should not visit any node";
      });

      rbtree.remove_all();
      for (int i = 0; i < 11; i++) {
        rbtree.upsert(i, 0);
      }

      ResourceMark rm;
      GrowableArray<int> seen;
      rbtree.visit_range_in_order(0, 10, [&](Node* x) {
        seen.push(x->key());
      });
      EXPECT_EQ(10, seen.length());
      for (int i = 0; i < 10; i++) {
        EXPECT_EQ(i, seen.at(i));
      }

      seen.clear();
      rbtree.visit_in_order([&](Node* x) {
        seen.push(x->key());
      });
      EXPECT_EQ(11, seen.length());
      for (int i = 0; i < 10; i++) {
        EXPECT_EQ(i, seen.at(i));
      }

      seen.clear();
      rbtree.visit_range_in_order(10, 12, [&](Node* x) {
        seen.push(x->key());
      });
      EXPECT_EQ(1, seen.length());
      EXPECT_EQ(10, seen.at(0));
    }
    { // Test with descending ordering
      RBTreeCHeap<int, int, CmpInverse> rbtree;
      using Node = RBTreeCHeap<int, int, CmpInverse>::RBNode;

      for (int i = 0; i < 10; i++) {
        rbtree.upsert(i, 0);
      }
      ResourceMark rm;
      GrowableArray<int> seen;
      rbtree.visit_range_in_order(9, -1, [&](Node* x) {
        seen.push(x->key());
      });
      EXPECT_EQ(10, seen.length());
      for (int i = 0; i < 10; i++) {
        EXPECT_EQ(10-i-1, seen.at(i));
      }
      seen.clear();

      rbtree.visit_in_order([&](Node* x) {
        seen.push(x->key());
      });
      EXPECT_EQ(10, seen.length());
      for (int i = 0; i < 10; i++) {
        EXPECT_EQ(10 - i - 1, seen.at(i));
      }
    }
  }

  void test_closest_leq() {
    using Node = RBTreeCHeap<int, int, Cmp>::RBNode;
    {
      RBTreeCHeap<int, int, Cmp> rbtree;
      Node* n = rbtree.closest_leq(0);
      EXPECT_EQ(nullptr, n);

      rbtree.upsert(0, 0);
      n = rbtree.closest_leq(0);
      EXPECT_EQ(0, n->key());

      rbtree.upsert(-1, -1);
      n = rbtree.closest_leq(0);
      EXPECT_EQ(0, n->key());

      rbtree.upsert(6, 0);
      n = rbtree.closest_leq(6);
      EXPECT_EQ(6, n->key());

      n = rbtree.closest_leq(-2);
      EXPECT_EQ(nullptr, n);
    }
  }

  void test_iterator() {
    constexpr const int num_nodes = 100;
    RBTreeCHeap<int, int, Cmp> tree;
    for (int n = 0; n <= num_nodes; n++) {
      tree.upsert(n, n);
    }

    RBTreeCHeap<int, int, Cmp>::RBTreeIterator iterator(&tree);
    for (int n = 0; n <= num_nodes; n++) {
      EXPECT_TRUE(iterator.has_next());
      EXPECT_EQ(iterator.next()->val(), n);
    }

    RBTreeCHeap<int, int, Cmp>::RBTreeReverseIterator reverse_iterator(&tree);
    for (int n = num_nodes; n >= 0; n--) {
      EXPECT_TRUE(reverse_iterator.has_next());
      EXPECT_EQ(reverse_iterator.next()->val(), n);
    }
  }

#ifdef ASSERT
  void test_fill_verify() {
    RBTreeCHeap<int, int, Cmp> rbtree;
    using Node = RBTreeCHeap<int, int, Cmp>::RBNode;

    ResourceMark rm;
    GrowableArray<int> allocations;

    int size = 10000;
    // Create random values
    for (int i = 0; i < size; i++) {
      int r = os::random() % size;
      allocations.append(r % size);
    }

    // Insert ~half of the values
    for (int i = 0; i < size; i++) {
      int r = os::random();
      if (r % 2 == 0) {
        rbtree.upsert(allocations.at(i), allocations.at(i));
      }
      if (i % 100 == 0) {
        verify_it(rbtree);
      }
    }

    // Insert and remove randomly
    for (int i = 0; i < size; i++) {
      int r = os::random();
      if (r % 2 == 0) {
        rbtree.upsert(allocations.at(i), allocations.at(i));
      } else {
        rbtree.remove(allocations.at(i));
      }
      if (i % 100 == 0) {
        verify_it(rbtree);
      }
    }

    // Remove all elements
    for (int i = 0; i < size; i++) {
      rbtree.remove(allocations.at(i));
    }

    verify_it(rbtree);
    EXPECT_EQ(rbtree.size(), 0UL);
  }
#endif // ASSERT

};

TEST_VM_F(RBTreeTest, InsertingDuplicatesResultsInOneValue) {
  this->inserting_duplicates_results_in_one_value();
}

TEST_VM_F(RBTreeTest, RBTreeOughtNotLeak) {
  this->rbtree_ought_not_leak();
}

TEST_VM_F(RBTreeTest, TestFind) {
  this->test_find();
}

TEST_VM_F(RBTreeTest, TestVisitors) {
  this->test_visitors();
}

TEST_VM_F(RBTreeTest, TestClosestLeq) {
  this->test_closest_leq();
}

TEST_VM_F(RBTreeTest, IteratorTest) {
  this->test_iterator();
}

#ifdef ASSERT
TEST_VM_F(RBTreeTest, FillAndVerify) {
  this->test_fill_verify();
}

TEST_VM_F(RBTreeTest, InsertRemoveVerify) {
  constexpr const int num_nodes = 100;
  for (int n_t1 = 0; n_t1 < num_nodes; n_t1++) {
    for (int n_t2 = 0; n_t2 < n_t1; n_t2++) {
      RBTreeCHeap<int, int, Cmp> tree;
      for (int i = 0; i < n_t1; i++) {
        tree.upsert(i, i);
      }
      for (int i = 0; i < n_t2; i++) {
        tree.remove(i);
      }
      verify_it(tree);
    }
  }
}

TEST_VM_F(RBTreeTest, VerifyItThroughStressTest) {
  { // Repeatedly verify a tree of moderate size
    RBTreeCHeap<int, int, Cmp> rbtree;
    constexpr const int ten_thousand = 10000;
    for (int i = 0; i < ten_thousand; i++) {
      int r = os::random();
      if (r % 2 == 0) {
        rbtree.upsert(i, i);
      } else {
        rbtree.remove(i);
      }
      if (i % 100 == 0) {
        verify_it(rbtree);
      }
    }
    for (int i = 0; i < ten_thousand; i++) {
      int r = os::random();
      if (r % 2 == 0) {
        rbtree.upsert(i, i);
      } else {
        rbtree.remove(i);
      }
      if (i % 100 == 0) {
        verify_it(rbtree);
      }
    }
  }
  { // Make a very large tree and verify at the end
  struct Nothing {};
    RBTreeCHeap<int, Nothing, Cmp> rbtree;
    constexpr const int one_hundred_thousand = 100000;
    for (int i = 0; i < one_hundred_thousand; i++) {
      rbtree.upsert(i, Nothing());
    }
    verify_it(rbtree);
  }
}

#endif // ASSERT

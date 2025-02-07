/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "memory/resourceArea.hpp"
#include "runtime/os.hpp"
#include "testutils.hpp"
#include "unittest.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/rbTree.hpp"
#include "utilities/rbTree.inline.hpp"


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

// Bump-pointer style allocator that can't free
template <size_t AreaSize>
struct ArrayAllocator {
  uint8_t area[AreaSize];
  size_t offset = 0;

  void* allocate(size_t sz) {
    if (offset + sz > AreaSize) {
      vm_exit_out_of_memory(sz, OOM_MALLOC_ERROR,
                            "red-black tree failed allocation");
    }
    void* place = &area[offset];
    offset += sz;
    return place;
  }

  void free(void* ptr) { }
};

using RBTreeInt = RBTreeCHeap<int, int, Cmp, mtOther>;

public:
  void inserting_duplicates_results_in_one_value() {
    constexpr int up_to = 10;
    GrowableArrayCHeap<int, mtTest> nums_seen(up_to, up_to, 0);
    RBTreeInt rbtree;
    const RBTreeInt& rbtree_const = rbtree;

    for (int i = 0; i < up_to; i++) {
      rbtree.upsert(i, i);
      rbtree.upsert(i, i);
      rbtree.upsert(i, i);
      rbtree.upsert(i, i);
      rbtree.upsert(i, i);
    }

    rbtree_const.visit_in_order([&](const RBTreeInt::RBNode* node) {
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

    constexpr int up_to = 10;
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
    RBTreeCHeap<float, Empty, FCmp, mtOther> rbtree;
    using Node = RBTreeCHeap<float, Empty, FCmp, mtOther>::RBNode;

    Node* n = nullptr;
    auto test = [&](float f) {
      EXPECT_EQ(nullptr, rbtree.find(f));
      rbtree.upsert(f, Empty{});
      const Node* n = rbtree.find_node(f);
      EXPECT_NE(nullptr, n);
      EXPECT_EQ(f, n->key());
    };

    test(1.0f);
    test(5.0f);
    test(0.0f);
  }

  void test_visitors() {
    { // Tests with 'default' ordering (ascending)
      RBTreeInt rbtree;
      const RBTreeInt& rbtree_const = rbtree;
      using Node = RBTreeInt::RBNode;

      rbtree_const.visit_range_in_order(0, 100, [&](const Node* x) {
        EXPECT_TRUE(false) << "Empty rbtree has no nodes to visit";
      });

      // Single-element set
      rbtree.upsert(1, 0);
      int count = 0;
      rbtree_const.visit_range_in_order(0, 100, [&](const Node* x) {
        count++;
      });
      EXPECT_EQ(1, count);

      count = 0;
      rbtree_const.visit_in_order([&](const Node* x) {
        count++;
      });
      EXPECT_EQ(1, count);

      // Add an element outside of the range that should not be visited on the right side and
      // one on the left side.
      rbtree.upsert(101, 0);
      rbtree.upsert(-1, 0);
      count = 0;
      rbtree_const.visit_range_in_order(0, 100, [&](const Node* x) {
        count++;
      });
      EXPECT_EQ(1, count);

      count = 0;
      rbtree_const.visit_in_order([&](const Node* x) {
        count++;
      });
      EXPECT_EQ(3, count);

      // Visiting empty range [0, 0) == {}
      rbtree.upsert(0, 0); // This node should not be visited.
      rbtree_const.visit_range_in_order(0, 0, [&](const Node* x) {
        EXPECT_TRUE(false) << "Empty visiting range should not visit any node";
      });

      rbtree.remove_all();
      for (int i = 0; i < 11; i++) {
        rbtree.upsert(i, 0);
      }

      ResourceMark rm;
      GrowableArray<int> seen;
      rbtree_const.visit_range_in_order(0, 10, [&](const Node* x) {
        seen.push(x->key());
      });
      EXPECT_EQ(10, seen.length());
      for (int i = 0; i < 10; i++) {
        EXPECT_EQ(i, seen.at(i));
      }

      seen.clear();
      rbtree_const.visit_in_order([&](const Node* x) {
        seen.push(x->key());
      });
      EXPECT_EQ(11, seen.length());
      for (int i = 0; i < 10; i++) {
        EXPECT_EQ(i, seen.at(i));
      }

      seen.clear();
      rbtree_const.visit_range_in_order(10, 12, [&](const Node* x) {
        seen.push(x->key());
      });
      EXPECT_EQ(1, seen.length());
      EXPECT_EQ(10, seen.at(0));
    }
    { // Test with descending ordering
      RBTreeCHeap<int, int, CmpInverse, mtOther> rbtree;
      const RBTreeCHeap<int, int, CmpInverse, mtOther>& rbtree_const = rbtree;
      using Node = RBTreeCHeap<int, int, CmpInverse, mtOther>::RBNode;

      for (int i = 0; i < 10; i++) {
        rbtree.upsert(i, 0);
      }
      ResourceMark rm;
      GrowableArray<int> seen;
      rbtree_const.visit_range_in_order(9, -1, [&](const Node* x) {
        seen.push(x->key());
      });
      EXPECT_EQ(10, seen.length());
      for (int i = 0; i < 10; i++) {
        EXPECT_EQ(10-i-1, seen.at(i));
      }
      seen.clear();

      rbtree_const.visit_in_order([&](const Node* x) {
        seen.push(x->key());
      });
      EXPECT_EQ(10, seen.length());
      for (int i = 0; i < 10; i++) {
        EXPECT_EQ(10 - i - 1, seen.at(i));
      }
    }
  }

  void test_closest_leq() {
    using Node = RBTreeInt::RBNode;
    {
      RBTreeInt rbtree;
      const RBTreeInt& rbtree_const = rbtree;
      const Node* n = rbtree_const.closest_leq(0);
      EXPECT_EQ(nullptr, n);

      rbtree.upsert(0, 0);
      n = rbtree_const.closest_leq(0);
      EXPECT_EQ(0, n->key());

      rbtree.upsert(-1, -1);
      n = rbtree_const.closest_leq(0);
      EXPECT_EQ(0, n->key());

      rbtree.upsert(6, 0);
      n = rbtree_const.closest_leq(6);
      EXPECT_EQ(6, n->key());

      n = rbtree_const.closest_leq(-2);
      EXPECT_EQ(nullptr, n);
    }
  }

  void test_node_prev() {
    RBTreeInt rbtree;
    const RBTreeInt& rbtree_const = rbtree;
    using Node = RBTreeInt::RBNode;
    constexpr int num_nodes = 100;

    for (int i = num_nodes; i > 0; i--) {
      rbtree.upsert(i, i);
    }

    const Node* node = rbtree_const.find_node(num_nodes);
    int count = num_nodes;
    while (node != nullptr) {
      EXPECT_EQ(count, node->val());
      node = node->prev();
      count--;
    }

    EXPECT_EQ(count, 0);
  }

  void test_node_next() {
    RBTreeInt rbtree;
    const RBTreeInt& rbtree_const = rbtree;
    using Node = RBTreeInt::RBNode;
    constexpr int num_nodes = 100;

    for (int i = 0; i < num_nodes; i++) {
      rbtree.upsert(i, i);
    }

    const Node* node = rbtree_const.find_node(0);
    int count = 0;
    while (node != nullptr) {
      EXPECT_EQ(count, node->val());
      node = node->next();
      count++;
    }

    EXPECT_EQ(count, num_nodes);
  }

  void test_stable_nodes() {
    RBTreeInt rbtree;
    const RBTreeInt& rbtree_const = rbtree;
    using Node = RBTreeInt::RBNode;
    ResourceMark rm;
    GrowableArray<Node*> a(10000);
    for (int i = 0; i < 10000; i++) {
      rbtree.upsert(i, i);
      a.push(rbtree.find_node(i));
    }

    for (int i = 0; i < 2000; i++) {
      int r = os::random() % 10000;
      Node* to_delete = rbtree.find_node(r);
      if (to_delete != nullptr && to_delete->_left != nullptr &&
          to_delete->_right != nullptr) {
        rbtree.remove(to_delete);
      }
    }

    // After deleting, nodes should have been moved around but kept their values
    for (int i = 0; i < 10000; i++) {
      const Node* n = rbtree_const.find_node(i);
      if (n != nullptr) {
        EXPECT_EQ(a.at(i), n);
      }
    }
  }

  void test_stable_nodes_addresses() {
    using Tree = RBTreeCHeap<int, void*, Cmp, mtOther>;
    using Node = Tree::RBNode;
    Tree rbtree;
    for (int i = 0; i < 10000; i++) {
      rbtree.upsert(i, nullptr);
      Node* inserted_node = rbtree.find_node(i);
      inserted_node->val() = inserted_node;
    }

    for (int i = 0; i < 2000; i++) {
      int r = os::random() % 10000;
      Node* to_delete = rbtree.find_node(r);
      if (to_delete != nullptr && to_delete->_left != nullptr &&
          to_delete->_right != nullptr) {
        rbtree.remove(to_delete);
      }
    }

    // After deleting, values should have remained consistant
    rbtree.visit_in_order([&](const Node* node) {
      EXPECT_EQ(node, node->val());
    });
  }

  void test_leftmost_rightmost() {
    using Node = RBTreeInt::RBNode;
    for (int i = 0; i < 10; i++) {
      RBTreeInt rbtree;
      const RBTreeInt& rbtree_const = rbtree;
      int max = 0, min = INT_MAX;
      for (int j = 0; j < 10; j++) {
        if (j == 0) {
          ASSERT_EQ(rbtree_const.leftmost(), (const Node*)nullptr);
          ASSERT_EQ(rbtree_const.rightmost(), (const Node*)nullptr);
        } else {
          ASSERT_EQ(rbtree_const.rightmost()->key(), max);
          ASSERT_EQ(rbtree_const.rightmost()->val(), max);
          ASSERT_EQ(rbtree_const.leftmost()->key(), min);
          ASSERT_EQ(rbtree_const.leftmost()->val(), min);
          ASSERT_EQ(rbtree_const.rightmost(), rbtree.rightmost());
          ASSERT_EQ(rbtree_const.leftmost(), rbtree.leftmost());
        }
        const int r = os::random();
        rbtree.upsert(r, r);
        min = MIN2(min, r);
        max = MAX2(max, r);
      }
      // Explicitly test non-const variants
      Node* n = rbtree.rightmost();
      ASSERT_EQ(n->key(), max);
      n->set_val(1);
      n = rbtree.leftmost();
      ASSERT_EQ(n->key(), min);
      n->set_val(1);
    }
  }

#ifdef ASSERT
  void test_fill_verify() {
    RBTreeInt rbtree;
    const RBTreeInt& rbtree_const = rbtree;
    ResourceMark rm;
    GrowableArray<int> allocations;

    int size = 10000;
    // Create random values
    for (int i = 0; i < size; i++) {
      int r = os::random() % size;
      allocations.append(r);
    }

    // Insert ~half of the values
    for (int i = 0; i < size; i++) {
      int r = os::random();
      if (r % 2 == 0) {
        rbtree.upsert(allocations.at(i), allocations.at(i));
      }
      if (i % 100 == 0) {
        rbtree_const.verify_self();
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
        rbtree_const.verify_self();
      }
    }

    // Remove all elements
    for (int i = 0; i < size; i++) {
      rbtree.remove(allocations.at(i));
    }

    rbtree.verify_self();
    EXPECT_EQ(rbtree_const.size(), 0UL);
  }

  void test_nodes_visited_once() {
    constexpr size_t memory_size = 65536;
    using Tree = RBTree<int, int, Cmp, ArrayAllocator<memory_size>>;
    using Node = Tree::RBNode;

    Tree tree;

    int num_nodes = memory_size / sizeof(Node);
    for (int i = 0; i < num_nodes; i++) {
      tree.upsert(i, i);
    }

    Node* start = tree.find_node(0);

    Node* node = start;
    for (int i = 0; i < num_nodes; i++) {
      EXPECT_EQ(tree._expected_visited, node->_visited);
      node += 1;
    }

    tree.verify_self();

    node = start;
    for (int i = 0; i < num_nodes; i++) {
      EXPECT_EQ(tree._expected_visited, node->_visited);
      node += 1;
    }

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

TEST_VM_F(RBTreeTest, NodePrev) {
  this->test_node_prev();
}

TEST_VM_F(RBTreeTest, NodeNext) {
  this->test_node_next();
}

TEST_VM_F(RBTreeTest, NodeStableTest) {
  this->test_stable_nodes();
}

TEST_VM_F(RBTreeTest, NodeStableAddressTest) {
  this->test_stable_nodes_addresses();
}

TEST_VM_F(RBTreeTest, LeftMostRightMost) {
  this->test_leftmost_rightmost();
}

struct PtrCmp {
  static int cmp(const void* a, const void* b) {
    const uintptr_t ai = p2u(a);
    const uintptr_t bi = p2u(b);
    return ai == bi ? 0 : (ai > bi ? 1 : -1);
  }
};

TEST_VM(RBTreeTestNonFixture, TestPrintPointerTree) {
  typedef RBTreeCHeap<const void*, unsigned, PtrCmp, mtTest> TreeType;
  TreeType tree;
#ifdef _LP64
  const void* const p1 = (const void*) 0x800000000ULL;
  const char* const s1 = "[0x0000000800000000] = 1";
  const void* const p2 = (const void*) 0xDEADBEEF0ULL;
  const char* const s2 = "[0x0000000deadbeef0] = 2";
  const void* const p3 = (const void*) 0x7f223fba0ULL;
  const char* const s3 = "[0x00000007f223fba0] = 3";
#else
  const void* const p1 = (const void*) 0x80000000ULL;
  const char* const s1 = "[0x80000000] = 1";
  const void* const p2 = (const void*) 0xDEADBEEFLL;
  const char* const s2 = "[0xdeadbeef] = 2";
  const void* const p3 = (const void*) 0x7f223fbaULL;
  const char* const s3 = "[0x7f223fba] = 3";
#endif
  tree.upsert(p1, 1);
  tree.upsert(p2, 2);
  tree.upsert(p3, 3);
  stringStream ss;
  tree.print_on(&ss);
  const char* const N = nullptr;
  ASSERT_NE(strstr(ss.base(), s1), N);
  ASSERT_NE(strstr(ss.base(), s2), N);
  ASSERT_NE(strstr(ss.base(), s3), N);
}

struct IntCmp {
  static int cmp(int a, int b) { return a == b ? 0 : (a > b ? 1 : -1); }
};

TEST_VM(RBTreeTestNonFixture, TestPrintIntegerTree) {
  typedef RBTree<int, unsigned, IntCmp, RBTreeCHeapAllocator<mtTest> > TreeType;
    TreeType tree;
    const int i1 = 82924;
    const char* const s1 = "[82924] = 1";
    const int i2 = -13591;
    const char* const s2 = "[-13591] = 2";
    const int i3 = 0;
    const char* const s3 = "[0] = 3";
    tree.upsert(i1, 1);
    tree.upsert(i2, 2);
    tree.upsert(i3, 3);
    stringStream ss;
    tree.print_on(&ss);
    const char* const N = nullptr;
    ASSERT_NE(strstr(ss.base(), s1), N);
    ASSERT_NE(strstr(ss.base(), s2), N);
    ASSERT_NE(strstr(ss.base(), s3), N);
}

#ifdef ASSERT
TEST_VM_F(RBTreeTest, FillAndVerify) {
  this->test_fill_verify();
}

TEST_VM_F(RBTreeTest, NodesVisitedOnce) {
  this->test_nodes_visited_once();
}

TEST_VM_F(RBTreeTest, InsertRemoveVerify) {
  constexpr int num_nodes = 100;
  for (int n_t1 = 0; n_t1 < num_nodes; n_t1++) {
    for (int n_t2 = 0; n_t2 < n_t1; n_t2++) {
      RBTreeInt tree;
      for (int i = 0; i < n_t1; i++) {
        tree.upsert(i, i);
      }
      for (int i = 0; i < n_t2; i++) {
        tree.remove(i);
      }
      tree.verify_self();
    }
  }
}

TEST_VM_F(RBTreeTest, VerifyItThroughStressTest) {
  { // Repeatedly verify a tree of moderate size
    RBTreeInt rbtree;
    constexpr int ten_thousand = 10000;
    for (int i = 0; i < ten_thousand; i++) {
      int r = os::random();
      if (r % 2 == 0) {
        rbtree.upsert(i, i);
      } else {
        rbtree.remove(i);
      }
      if (i % 100 == 0) {
        rbtree.verify_self();
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
        rbtree.verify_self();
      }
    }
  }
  { // Make a very large tree and verify at the end
    struct Nothing {};
    RBTreeCHeap<int, Nothing, Cmp, mtOther> rbtree;
    constexpr int one_hundred_thousand = 100000;
    for (int i = 0; i < one_hundred_thousand; i++) {
      rbtree.upsert(i, Nothing());
    }
    rbtree.verify_self();
  }
}

#endif // ASSERT

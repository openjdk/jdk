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

#ifdef ASSERT
  template<typename K, typename V, typename CMP, typename ALLOC>
  void verify_it(RBTree<K, V, CMP, ALLOC>& t) {
    t.verify_self();
  }
#endif // ASSERT

using RBTreeInt = RBTreeCHeap<int, int, Cmp, mtOther>;

public:
  void inserting_duplicates_results_in_one_value() {
    constexpr int up_to = 10;
    GrowableArrayCHeap<int, mtTest> nums_seen(up_to, up_to, 0);
    RBTreeInt rbtree;

    for (int i = 0; i < up_to; i++) {
      rbtree.upsert(i, i);
      rbtree.upsert(i, i);
      rbtree.upsert(i, i);
      rbtree.upsert(i, i);
      rbtree.upsert(i, i);
    }

    rbtree.visit_in_order([&](RBTreeInt::RBNode* node) {
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
      using Node = RBTreeInt::RBNode;

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
      RBTreeCHeap<int, int, CmpInverse, mtOther> rbtree;
      using Node = RBTreeCHeap<int, int, CmpInverse, mtOther>::RBNode;

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
    using Node = RBTreeInt::RBNode;
    {
      RBTreeInt rbtree;
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

  void test_node_prev() {
    RBTreeInt _tree;
    using Node = RBTreeInt::RBNode;
    constexpr int num_nodes = 100;

    for (int i = num_nodes; i > 0; i--) {
      _tree.upsert(i, i);
    }

    Node* node = _tree.find_node(num_nodes);
    int count = num_nodes;
    while (node != nullptr) {
      EXPECT_EQ(count, node->val());
      node = node->prev();
      count--;
    }

    EXPECT_EQ(count, 0);
  }

    void test_node_next() {
    RBTreeInt _tree;
    using Node = RBTreeInt::RBNode;
    constexpr int num_nodes = 100;

    for (int i = 0; i < num_nodes; i++) {
      _tree.upsert(i, i);
    }

    Node* node = _tree.find_node(0);
    int count = 0;
    while (node != nullptr) {
      EXPECT_EQ(count, node->val());
      node = node->next();
      count++;
    }

    EXPECT_EQ(count, num_nodes);
  }

  void test_stable_nodes() {
    using Node = RBTreeInt::RBNode;
    RBTreeInt rbtree;
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
      const Node* n = rbtree.find_node(i);
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
    rbtree.visit_in_order([&](Node* node) {
      EXPECT_EQ(node, node->val());
    });
  }

#ifdef ASSERT
  void test_fill_verify() {
    RBTreeInt rbtree;

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

    verify_it(tree);

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
      verify_it(tree);
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
    RBTreeCHeap<int, Nothing, Cmp, mtOther> rbtree;
    constexpr int one_hundred_thousand = 100000;
    for (int i = 0; i < one_hundred_thousand; i++) {
      rbtree.upsert(i, Nothing());
    }
    verify_it(rbtree);
  }
}

#endif // ASSERT

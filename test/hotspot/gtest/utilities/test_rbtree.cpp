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
#include "testutils.hpp"
#include "unittest.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/rbTree.hpp"
#include <chrono>

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
  void insert_remove_test() {
    constexpr const int num_nodes = 100;
    for (int n_t1 = 0; n_t1 < num_nodes; n_t1++) {
      for (int n_t2 = 0; n_t2 < n_t1; n_t2++) {
        RBTreeCHeap<int, int, Cmp> tree;
        for (int i = 0; i < n_t1; i++) {
          tree.insert(i, i);
        }
        for (int i = 0; i < n_t2; i++) {
          tree.remove(i);
        }
        verify_it(tree);
      }
    }
  }

  void merge_test() {
    constexpr const int num_nodes = 100;
    for (int n_t1 = 0; n_t1 < num_nodes; n_t1++) {
      for (int n_t2 = 0; n_t2 < num_nodes; n_t2++) {
        RBTreeCHeap<int, int, Cmp> tree_left;
        RBTreeCHeap<int, int, Cmp> tree_right;

        for (int i = 0; i < n_t1; i++) {
          tree_left.insert(i, i);
        }
        for (int i = n_t1; i < n_t1 + n_t2; i++) {
          tree_right.insert(i, i);
        }

        RBTreeCHeap<int, int, Cmp>& tree = RBTreeCHeap<int, int, Cmp>::merge(tree_left, tree_right);
        verify_it(tree);
      }
    }


  }

  void split_test() {
    constexpr const int num_nodes = 100;
    for (int n_t = 0; n_t < num_nodes; n_t++) {
      for (int k = 0; k < n_t; k++) {
        RBTreeCHeap<int, int, Cmp> tree;
        for (int i = 0; i < n_t; i++) {
          tree.insert(i, i);
        }

        RBTreeCHeap<int, int, Cmp> tree_left;
        RBTreeCHeap<int, int, Cmp> tree_right;

        tree.split(tree_left, tree_right, k);
        for (int i = 0; i <= k; i++) {
          EXPECT_NOT_NULL(tree_left.find(i));
        }
        for (int i = k+1; i < n_t; i++) {
          EXPECT_NOT_NULL(tree_right.find(i));
        }
        verify_it(tree_left);
        verify_it(tree_right);
      }
    }

    for (int n_t = 0; n_t < num_nodes; n_t++) {
      for (int k = 1; k < n_t * 2; k += 2) {
        RBTreeCHeap<int, int, Cmp> tree;
        for (int i = 0; i < n_t * 2; i += 2) {
          tree.insert(i, i);
        }

        RBTreeCHeap<int, int, Cmp> tree_left;
        RBTreeCHeap<int, int, Cmp> tree_right;

        tree.split(tree_left, tree_right, k);
        for (int i = 0; i < k; i += 2) {
          EXPECT_NOT_NULL(tree_left.find(i));
        }
        for (int i = k + 1; i < n_t; i += 2) {
          EXPECT_NOT_NULL(tree_right.find(i));
        }
        verify_it(tree_left);
        verify_it(tree_right);
      }
    }
  }

  void split_merge_test() {
    constexpr const int num_nodes = 80;
    for (int n_t = 0; n_t < num_nodes; n_t++) {
      for (int k1 = 0; k1 < n_t; k1++) {
        for (int k2 = k1; k2 < n_t; k2++) {
          RBTreeCHeap<int, int, Cmp> tree;
          for (int i = 0; i < n_t; i++) {
            tree.insert(i, i);
          }

          RBTreeCHeap<int, int, Cmp> tree_left;
          RBTreeCHeap<int, int, Cmp> tree_right1;
          tree.split(tree_left, tree_right1, k1);

          RBTreeCHeap<int, int, Cmp> tree_middle;
          RBTreeCHeap<int, int, Cmp> tree_right2;
          tree_right1.split(tree_middle, tree_right2, k2, RBTreeCHeap<int, int, Cmp>::LT);

          RBTreeCHeap<int, int, Cmp>& tree_merged =
            RBTreeCHeap<int, int, Cmp>::merge(tree_left, tree_right2);

          for (int i = 0; i <= k1; i++) {
            EXPECT_NOT_NULL(tree_merged.find(i));
          }
          for (int i = k2; i < n_t; i++) {
            EXPECT_NOT_NULL(tree_merged.find(i));
          }
          verify_it(tree_merged);
        }
      }
    }

    for (int n_t = 0; n_t < num_nodes; n_t++) {
      for (int k1 = 1; k1 < n_t * 2; k1 += 2) {
        for (int k2 = k1; k2 < n_t * 2; k2 += 2) {
          RBTreeCHeap<int, int, Cmp> tree;
          for (int i = 0; i < n_t * 2; i += 2) {
            tree.insert(i, i);
          }

          RBTreeCHeap<int, int, Cmp> tree_left;
          RBTreeCHeap<int, int, Cmp> tree_right1;
          tree.split(tree_left, tree_right1, k1);

          RBTreeCHeap<int, int, Cmp> tree_middle;
          RBTreeCHeap<int, int, Cmp> tree_right2;
          tree_right1.split(tree_middle, tree_right2, k2, RBTreeCHeap<int, int, Cmp>::LT);

          RBTreeCHeap<int, int, Cmp>& tree_merged =
            RBTreeCHeap<int, int, Cmp>::merge(tree_left, tree_right2);

          for (int i = 0; i <= k1; i += 2) {
            EXPECT_NOT_NULL(tree_merged.find(i));
          }
          for (int i = k2 + 1; i < n_t; i += 2) {
            EXPECT_NOT_NULL(tree_merged.find(i));
          }
          verify_it(tree_merged);
        }
      }
    }
  }

  void compare_test() {
    RBTreeCHeap<int, int, Cmp> tree;
    RBTreeCHeap<int, int, Cmp> tree2;
    TreapCHeap<int, int, Cmp> treap;
    TreapCHeap<int, int, Cmp> treap2;
    ResourceMark rm;
    GrowableArray<int> allocations;

    constexpr const int size = 1000000;
    for (int i = 0; i < size; i++) {
      int r = os::random();
      allocations.append(r % size);
    }

    std::cout << "Size: " << size << std::endl;

    auto start_treap = std::chrono::high_resolution_clock::now();
    for (int i = 0; i < size; i++) {
      treap.upsert(i, i);
    }
    auto end_treap = std::chrono::high_resolution_clock::now();
    auto treap_time = std::chrono::duration_cast<std::chrono::milliseconds>(
        end_treap - start_treap);

    auto start_tree = std::chrono::high_resolution_clock::now();
    for (int i = 0; i < size; i++) {
      tree.upsert(i, i);
    }
    auto end_tree = std::chrono::high_resolution_clock::now();
    auto tree_time = std::chrono::duration_cast<std::chrono::milliseconds>(
        end_tree - start_tree);


    auto start_treap2 = std::chrono::high_resolution_clock::now();
    for (int i = 0; i < size; i++) {
      treap.remove(i);
    }
    auto end_treap2 = std::chrono::high_resolution_clock::now();
    auto treap_time2 = std::chrono::duration_cast<std::chrono::milliseconds>(
        end_treap2 - start_treap2);

    auto start_tree2 = std::chrono::high_resolution_clock::now();
    for (int i = 0; i < size; i++) {
      tree.remove(i);
    }
    auto end_tree2 = std::chrono::high_resolution_clock::now();
    auto tree_time2 = std::chrono::duration_cast<std::chrono::milliseconds>(
        end_tree2 - start_tree2);


    auto start_treap_r = std::chrono::high_resolution_clock::now();
    for (int i = 0; i < size; i++) {
      treap2.upsert(allocations.at(i), allocations.at(i));
    }
    auto end_treap_r = std::chrono::high_resolution_clock::now();
    auto treap_time_r = std::chrono::duration_cast<std::chrono::milliseconds>(
        end_treap_r - start_treap_r);

    auto start_tree_r = std::chrono::high_resolution_clock::now();
    for (int i = 0; i < size; i++) {
      tree2.upsert(allocations.at(i), allocations.at(i));
    }
    auto end_tree_r = std::chrono::high_resolution_clock::now();
    auto tree_time_r = std::chrono::duration_cast<std::chrono::milliseconds>(
        end_tree_r - start_tree_r);

    auto start_treap2_r = std::chrono::high_resolution_clock::now();
    for (int i = 0; i < size; i++) {
      treap2.remove(allocations.at(i));
    }
    auto end_treap2_r = std::chrono::high_resolution_clock::now();
    auto treap_time2_r = std::chrono::duration_cast<std::chrono::milliseconds>(
        end_treap2_r - start_treap2_r);

    auto start_tree2_r = std::chrono::high_resolution_clock::now();
    for (int i = 0; i < size; i++) {
      tree2.remove(allocations.at(i));
    }
    auto end_tree2_r = std::chrono::high_resolution_clock::now();
    auto tree_time2_r = std::chrono::duration_cast<std::chrono::milliseconds>(
        end_tree2_r - start_tree2_r);

    std::cout << "Treap seq insert: " << treap_time.count() << " ms" << std::endl;
    std::cout << "Treap ran insert: " << treap_time_r.count() << " ms" << std::endl;

    std::cout << "Tree seq insert: " << tree_time.count() << " ms" << std::endl;
    std::cout << "Tree ran insert: " << tree_time_r.count() << " ms" <<std::endl;

    std::cout << "Treap seq delete: " << treap_time2.count() << " ms" << std::endl;
    std::cout << "Treap ran delete: " << treap_time2_r.count() << " ms" << std::endl;

    std::cout << "Tree seq delete: " << tree_time2.count() << " ms" << std::endl;
    std::cout << "Tree ran delete: " << tree_time2_r.count() << " ms" << std::endl;

    EXPECT_NULL(nullptr);
  }

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
};

// TEST_VM_F(RBTreeTest, RBTreeTesting) {
//   this->rbtreetest();
// }

TEST_VM_F(RBTreeTest, RBTreeCompare) {
  this->compare_test();
}

TEST_VM_F(RBTreeTest, InsertRemove) {
  this->insert_remove_test();
}

TEST_VM_F(RBTreeTest, Merge) {
  this->merge_test();
}

TEST_VM_F(RBTreeTest, Split) {
  this->split_test();
}

TEST_VM_F(RBTreeTest, SplitMerge) {
  this->split_merge_test();
}

TEST_VM_F(RBTreeTest, InsertingDuplicatesResultsInOneValue) {
  this->inserting_duplicates_results_in_one_value();
}

TEST_VM_F(RBTreeTest, RBTreeOughtNotLeak) {
  this->rbtree_ought_not_leak();
}

TEST_VM_F(RBTreeTest, TestFind) {
  test_find();
}

TEST_VM_F(RBTreeTest, TestVisitors) {
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

TEST_VM_F(RBTreeTest, TestClosestLeq) {
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

#ifdef ASSERT

TEST_VM_F(RBTreeTest, VerifyItThroughStressTest) {
  { // Repeatedly verify a treap of moderate size
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
  { // Make a very large treap and verify at the end
  struct Nothing {};
    RBTreeCHeap<int, Nothing, Cmp> rbtree;
    constexpr const int one_hundred_thousand = 100000;
    for (int i = 0; i < one_hundred_thousand; i++) {
      rbtree.upsert(i, Nothing());
    }
    verify_it(rbtree);
  }
}

TEST_VM_F(RBTreeTest, FillAndVerify) {
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
        rbtree.insert(allocations.at(i), allocations.at(i));
      }
      if (i % 100 == 0) {
        verify_it(rbtree);
      }
    }

    // Ins
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
    EXPECT_EQ(rbtree.num_nodes(), 0UL);

}

#endif // ASSERT

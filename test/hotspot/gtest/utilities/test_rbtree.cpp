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

public:
  void rbtreetest() {
    RBTreeCHeap<int, int, Cmp> tree;
    RBTreeCHeap<int, int, Cmp> tree2;
    RBTreeCHeap<int, int, Cmp> tree3;
    using Node = RBTreeCHeap<int, int, Cmp>::RBNode;

    // tree.insert(50, 50);
    // tree.insert(60, 60);
    // tree.insert(40, 40);
    // tree.insert(70, 70);
    // tree.insert(30, 30);
    // tree.insert(80, 80);
    // tree.insert(20, 20);
    // tree.insert(90, 90);
    // tree.insert(10, 10);
    // tree.insert(100, 100);


    // bool a = tree.verify_tree();
    // tree.print_tree();

    // printf("root: %d %d\n", tree._root->key(), tree._root->color());

    // ResourceMark rm;
    // GrowableArray<int> seen;
    // tree.inorder_traversal([&](Node *x) { });
    // tree.inorder_traversal([&](Node *x) { tty->print("%d %d, ", x->key(), x->color()); });
    // printf("\n");

    // for (int i = 0; i <= 10; i++) {
    //   tree2.insert(i, i);
    // }
    // for (int i = 0; i <= 0; i++) {
    //   tree2.remove(i);
    // }
    // tree2.verify_tree();
    // tree2.print_tree();
    // printf("root: %d %d\n", tree2._root->key(), tree2._root->color());

    int size = 15000;
    for (int i = 0; i <= size; i++) {
      int r = os::random();
      if (r % 2 == 0) {
        tree3.insert(i, i);
      }
      if (r % 100 == 0) {
        EXPECT_TRUE(tree3.verify_tree());
      }
    }


    for (int i = 0; i <= size; i++) {
      int r = os::random();
      if (r % 2 == 0) {
        tree3.upsert(i, i);
      } else {
        tree3.remove(i);
      }
      if (r % 100 == 0) {
        EXPECT_TRUE(tree3.verify_tree());
      }
    }

    for (int i = 0; i <= size; i++) {
      int r = os::random();
      if (r % 2 != 0) {
        tree3.remove(i);
      }
      if (r % 100 == 0) {
        EXPECT_TRUE(tree3.verify_tree());
      }
    }
    // tree3.verify_tree();

    RBTreeCHeap<int, int, Cmp> tree4;
    ResourceMark rm;
    GrowableArray<int> allocations;

    // fill tree
    int fill_size = 10000;
    for (int i = 0; i < fill_size; i++) {
      int val = os::random() % 1000000;
      tree4.insert(val, val);
      allocations.append(val);
      if (i % 100 == 0) {
        EXPECT_TRUE(tree4.verify_tree());
      }
    }

    // Flip between allocations and deallocations
    int work_size = 10000;
    for (int i = 0; i < work_size; i++) {
      int val = os::random() % 1000000;
      if (val % 2 == 0) {
        tree4.insert(val, val);
        allocations.append(val);
      } else {
        int index = val % allocations.length();
        int to_remove = allocations.at(index);
        allocations.remove_at(index);
        tree4.remove(to_remove);
      }
      if (i % 100 == 0) {
        EXPECT_TRUE(tree4.verify_tree());
      }
    }

    RBTreeCHeap<int, int, Cmp> tree5;

    // fill tree
    for (int i = 0; i < fill_size; i++) {
      int val = os::random() % 1000000;
      tree5.insert(val, val);
      allocations.append(val);
      if (i % 100 == 0) {
        EXPECT_TRUE(tree5.verify_tree());
      }
    }

    EXPECT_NULL(nullptr);
  }

  void compare_test() {
    RBTreeCHeap<int, int, Cmp> tree;
    TreapCHeap<int, int, Cmp> treap;
    RBTreeCHeap<int, int, Cmp> tree2;
    TreapCHeap<int, int, Cmp> treap2;
    ResourceMark rm;
    GrowableArray<int> allocations;

    int size = 10000000;
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
};

TEST_VM_F(RBTreeTest, RBTreeTesting) {
  this->rbtreetest();
}

TEST_VM_F(RBTreeTest, RBTreeCompare) {
  this->compare_test();
}

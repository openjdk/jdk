/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
  using RBTreeIntNode = RBNode<int, int>;

  struct Cmp {
    static RBTreeOrdering cmp(int a, int b) {
      if (a < b) return RBTreeOrdering::LT;
      if (a > b) return RBTreeOrdering::GT;
      return RBTreeOrdering::EQ;
    }

    static bool less_than(const RBTreeIntNode* a, const RBTreeIntNode* b) {
      return a->key() < b->key();
    }
  };

  struct CmpInverse {
    static RBTreeOrdering cmp(int a, int b) {
      if (a < b) return RBTreeOrdering::GT;
      if (a > b) return RBTreeOrdering::LT;
      return RBTreeOrdering::EQ;
    }
  };

  struct FCmp {
    static RBTreeOrdering cmp(float a, float b) {
      if (a < b) return RBTreeOrdering::LT;
      if (a > b) return RBTreeOrdering::GT;
      return RBTreeOrdering::EQ;
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

  using RBTreeInt = RBTreeCHeap<int, int, Cmp, mtTest>;
  using IntrusiveTreeNode = IntrusiveRBNode;

  struct IntrusiveHolder {
    IntrusiveTreeNode node;
    int key;
    int data;

    IntrusiveTreeNode* get_node() { return &node; }

    IntrusiveHolder() {}
    IntrusiveHolder(int key, int data) : key(key), data(data) {}
    static IntrusiveHolder* cast_to_self(const IntrusiveTreeNode* node) { return (IntrusiveHolder*)node; }
  };

  struct IntrusiveCmp {
    static RBTreeOrdering cmp(int a, const IntrusiveTreeNode* b_node) {
      int b = IntrusiveHolder::cast_to_self(b_node)->key;
      if (a < b) return RBTreeOrdering::LT;
      if (a > b) return RBTreeOrdering::GT;
      return RBTreeOrdering::EQ;
    }

    // true if a < b
    static bool less_than(const IntrusiveTreeNode* a, const IntrusiveTreeNode* b) {
      return (IntrusiveHolder::cast_to_self(a)->key -
              IntrusiveHolder::cast_to_self(b)->key) < 0;
    }
  };

  using IntrusiveTreeInt = IntrusiveRBTree<int, IntrusiveCmp>;
  using IntrusiveCursor = IntrusiveTreeInt::Cursor;

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

    rbtree_const.visit_in_order([&](const RBTreeIntNode* node) {
      nums_seen.at(node->key())++;
      return true;
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
    using Node = RBNode<float, Empty>;

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
      using Node = RBTreeIntNode;

      rbtree_const.visit_range_in_order(0, 100, [&](const Node* x) {
        EXPECT_TRUE(false) << "Empty rbtree has no nodes to visit";
        return true;
      });

      // Single-element set
      rbtree.upsert(1, 0);
      int count = 0;
      rbtree_const.visit_range_in_order(0, 100, [&](const Node* x) {
        count++;
        return true;
      });
      EXPECT_EQ(1, count);

      count = 0;
      rbtree_const.visit_in_order([&](const Node* x) {
        count++;
        return true;
      });
      EXPECT_EQ(1, count);
      rbtree.visit_in_order([&](const Node* x) {
        count++;
        return true;
      });
      EXPECT_EQ(2, count);

      // Add an element outside of the range that should not be visited on the right side and
      // one on the left side.
      rbtree.upsert(101, 0);
      rbtree.upsert(-1, 0);
      count = 0;
      rbtree_const.visit_range_in_order(0, 100, [&](const Node* x) {
        count++;
        return true;
      });
      EXPECT_EQ(1, count);
      rbtree.visit_range_in_order(0, 100, [&](const Node* x) {
        count++;
        return true;
      });
      EXPECT_EQ(2, count);

      count = 0;
      rbtree_const.visit_in_order([&](const Node* x) {
        count++;
        return true;
      });
      EXPECT_EQ(3, count);
      rbtree.visit_in_order([&](const Node* x) {
        count++;
        return true;
      });
      EXPECT_EQ(6, count);

      count = 0;
      rbtree.upsert(0, 0);
      rbtree_const.visit_range_in_order(0, 0, [&](const Node* x) {
        count++;
        return true;
      });
      EXPECT_EQ(1, count);
      rbtree.visit_range_in_order(0, 0, [&](const Node* x) {
        count++;
        return true;
      });
      EXPECT_EQ(2, count);

      // Test exiting visit early
      rbtree.remove_all();
      for (int i = 0; i < 11; i++) {
        rbtree.upsert(i, 0);
      }

      count = 0;
      rbtree_const.visit_in_order([&](const Node* x) {
        if (x->key() >= 6) return false;
        count++;
        return true;
      });
      EXPECT_EQ(6, count);

      count = 0;
      rbtree_const.visit_range_in_order(6, 10, [&](const Node* x) {
        if (x->key() >= 6) return false;
        count++;
        return true;
      });

      EXPECT_EQ(0, count);

      rbtree.remove_all();
      for (int i = 0; i < 11; i++) {
        rbtree.upsert(i, 0);
      }

      ResourceMark rm;
      GrowableArray<int> seen;
      rbtree_const.visit_range_in_order(0, 9, [&](const Node* x) {
        seen.push(x->key());
        return true;
      });
      EXPECT_EQ(10, seen.length());
      for (int i = 0; i < 10; i++) {
        EXPECT_EQ(i, seen.at(i));
      }

      seen.clear();
      rbtree_const.visit_in_order([&](const Node* x) {
        seen.push(x->key());
        return true;
      });
      EXPECT_EQ(11, seen.length());
      for (int i = 0; i < 10; i++) {
        EXPECT_EQ(i, seen.at(i));
      }

      seen.clear();
      rbtree_const.visit_range_in_order(10, 12, [&](const Node* x) {
        seen.push(x->key());
        return true;
      });
      EXPECT_EQ(1, seen.length());
      EXPECT_EQ(10, seen.at(0));
    }
    { // Test with descending ordering
      RBTreeCHeap<int, int, CmpInverse, mtOther> rbtree;
      const RBTreeCHeap<int, int, CmpInverse, mtOther>& rbtree_const = rbtree;
      using Node = RBNode<int, int>;

      for (int i = 0; i < 10; i++) {
        rbtree.upsert(i, 0);
      }
      ResourceMark rm;
      GrowableArray<int> seen;
      rbtree_const.visit_range_in_order(9, -1, [&](const Node* x) {
        seen.push(x->key());
        return true;
      });
      EXPECT_EQ(10, seen.length());
      for (int i = 0; i < 10; i++) {
        EXPECT_EQ(10-i-1, seen.at(i));
      }
      seen.clear();

      rbtree_const.visit_in_order([&](const Node* x) {
        seen.push(x->key());
        return true;
      });
      EXPECT_EQ(10, seen.length());
      for (int i = 0; i < 10; i++) {
        EXPECT_EQ(10 - i - 1, seen.at(i));
      }
    }
  }

  void test_visit_outside_range() {
    RBTreeInt rbtree;
    using Node = RBTreeIntNode;

    rbtree.upsert(2, 0);
    rbtree.upsert(5, 0);

    constexpr int test_cases[9][2] = {{0, 0}, {0, 1}, {1, 1}, {3, 3}, {3, 4},
                                      {4, 4}, {6, 6}, {6, 7}, {7, 7}};

    for (const int (&test_case)[2] : test_cases) {
      bool visited = false;
      rbtree.visit_range_in_order(test_case[0], test_case[1], [&](const Node* x) -> bool {
        visited = true;
        return true;
      });
      EXPECT_FALSE(visited);
    }
  }

  void test_closest_leq() {
    using Node = RBTreeIntNode;
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

  void test_closest_gt() {
    using Node = RBTreeIntNode;
    {
      RBTreeInt rbtree;
      Node* n = rbtree.closest_gt(0);
      EXPECT_EQ(nullptr, n);

      rbtree.upsert(0, 0);
      n = rbtree.closest_gt(-1);
      EXPECT_EQ(0, n->key());

      rbtree.upsert(-5, -5);
      n = rbtree.closest_gt(-1);
      EXPECT_EQ(0, n->key());

      n = rbtree.closest_gt(-5);
      EXPECT_EQ(0, n->key());

      n = rbtree.closest_gt(-10);
      EXPECT_EQ(-5, n->key());

      rbtree.upsert(10, 10);
      n = rbtree.closest_gt(5);
      EXPECT_EQ(10, n->key());

      n = rbtree.closest_gt(10);
      EXPECT_EQ(nullptr, n);
    }
  }

  void test_leftmost() {
    using Node = RBTreeIntNode;

    RBTreeInt rbtree;
    Node* n = rbtree.leftmost();
    EXPECT_EQ(nullptr, n);

    rbtree.upsert(0, 0);
    n = rbtree.leftmost();
    EXPECT_EQ(0, n->key());

    rbtree.upsert(2, 2);
    n = rbtree.leftmost();
    EXPECT_EQ(0, n->key());

    rbtree.upsert(1, 1);
    n = rbtree.leftmost();
    EXPECT_EQ(0, n->key());

    rbtree.upsert(-1, -1);
    n = rbtree.leftmost();
    EXPECT_EQ(-1, n->key());

    rbtree.remove(-1);
    n = rbtree.leftmost();
    EXPECT_EQ(0, n->key());

    rbtree.remove(1);
    n = rbtree.leftmost();
    EXPECT_EQ(0, n->key());

    rbtree.remove(0);
    n = rbtree.leftmost();
    EXPECT_EQ(2, n->key());

    rbtree.remove(2);
    n = rbtree.leftmost();
    EXPECT_EQ(nullptr, n);

  }

  void test_node_prev() {
    RBTreeInt rbtree;
    const RBTreeInt& rbtree_const = rbtree;
    using Node = RBTreeIntNode;
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
    using Node = RBTreeIntNode;
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
    using Node = RBTreeIntNode;
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
    using Node = RBNode<int, void*>;
    Tree rbtree;
    for (int i = 0; i < 10000; i++) {
      rbtree.upsert(i, (void*)nullptr);
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
      return true;
    });
  }

  void test_node_hints() {
    constexpr int num_nodes = 100;
    RBTreeInt tree;
    RBTreeIntNode* nodes[num_nodes];

    RBTreeIntNode* prev_node = nullptr;
    for (int i = 0; i < num_nodes; i++) {
      RBTreeIntNode* node = tree.allocate_node(i, i);
      nodes[i] = node;
      tree.insert(i, node, prev_node);
      prev_node = node;
    }

    for (int i = 0; i < num_nodes; i++) {
      RBTreeIntNode* target_node = nodes[i];
      for (int j = 0; j < num_nodes; j++) {
        if (i == j) continue;
        RBTreeIntNode* hint_node = nodes[j];
        RBTreeIntNode* find_node = tree.find_node(i);
        RBTreeIntNode* hint_find_node = tree.find_node(i, hint_node);

        ASSERT_EQ(find_node, hint_find_node);
        ASSERT_EQ(target_node, hint_find_node);
      }
    }
  }

  void test_cursor() {
    constexpr int num_nodes = 10;
    RBTreeInt tree;

    for (int n = 0; n <= num_nodes; n++) {
      RBTreeInt::Cursor find_cursor = tree.cursor(n);
      EXPECT_FALSE(find_cursor.found());
    }

    for (int n = 0; n <= num_nodes; n++) {
      tree.upsert(n, n);
    }

    for (int n = 0; n <= num_nodes; n++) {
      RBTreeInt::Cursor find_cursor = tree.cursor(n);
      EXPECT_TRUE(find_cursor.found());
    }

    EXPECT_FALSE(tree.cursor(-1).found());
    EXPECT_FALSE(tree.cursor(101).found());
  }

  void test_get_cursor() {
    constexpr int num_nodes = 10;
    IntrusiveTreeInt tree;
    GrowableArrayCHeap<IntrusiveHolder*, mtTest> nodes(num_nodes);

    for (int n = 0; n <= num_nodes; n++) {
      IntrusiveHolder* place = (IntrusiveHolder*)os::malloc(sizeof(IntrusiveHolder), mtTest);
      new (place) IntrusiveHolder(n, n);

      tree.insert_at_cursor(place->get_node(), tree.cursor(n));
      nodes.push(place);
    }

    for (int n = 0; n <= num_nodes; n++) {
      IntrusiveTreeNode* node = nodes.at(n)->get_node();
      IntrusiveCursor cursor = tree.cursor(node);
      IntrusiveCursor find_cursor = tree.cursor(n);
      EXPECT_TRUE(cursor.found());
      EXPECT_TRUE(cursor.valid());
      EXPECT_TRUE(find_cursor.found());
      EXPECT_TRUE(find_cursor.valid());
      EXPECT_EQ(cursor.node(), find_cursor.node());
    }
  }

  void test_cursor_empty_tree() {
    RBTreeInt tree;
    RBTreeInt::Cursor cursor = tree.cursor(tree.leftmost());
    EXPECT_FALSE(cursor.valid());

    cursor = tree.cursor(0);
    EXPECT_TRUE(cursor.valid());
    EXPECT_FALSE(cursor.found());
    EXPECT_FALSE(tree.next(cursor).valid());
  }

  void test_cursor_iterate() {
    constexpr int num_nodes = 100;
    RBTreeInt tree;
    for (int n = 0; n <= num_nodes; n++) {
      tree.upsert(n, n);
    }

    RBTreeInt::Cursor cursor = tree.cursor(0);
    for (int n = 0; n <= num_nodes; n++) {
      EXPECT_TRUE(cursor.valid());
      EXPECT_EQ(cursor.node()->val(), n);
      cursor = tree.next(cursor);
    }
    EXPECT_FALSE(cursor.valid());

    cursor = tree.cursor(num_nodes);
    for (int n = num_nodes; n >= 0; n--) {
      EXPECT_TRUE(cursor.valid());
      EXPECT_EQ(cursor.node()->val(), n);
      cursor = tree.prev(cursor);
    }
    EXPECT_FALSE(cursor.valid());
  }

  void test_leftmost_rightmost() {
    using Node = RBTreeIntNode;
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

  void test_cursor_replace() {
    constexpr int num_nodes = 100;
    RBTreeInt tree;

    for (int i = 0; i < num_nodes * 10; i += 10) {
      tree.upsert(i, i);
    }

    for (int i = 0; i < num_nodes * 10; i += 10) {
      RBTreeInt::Cursor cursor = tree.cursor(tree.find_node(i));
      RBTreeIntNode* new_node = tree.allocate_node(i + 1, i + 1);
      tree.replace_at_cursor(new_node, cursor);
    }

    for (int i = 0; i < num_nodes * 10; i += 10) {
      RBTreeIntNode* node = tree.find_node(i);
      EXPECT_NULL(node);
      node = tree.find_node(i + 1);
      EXPECT_NOT_NULL(node);
    }

    tree.verify_self();
  }

  void test_intrusive() {
    IntrusiveTreeInt intrusive_tree;
    int num_iterations = 100;

    // Insert values
    for (int n = 0; n < num_iterations; n++) {
      IntrusiveCursor cursor = intrusive_tree.cursor(n);
      EXPECT_NULL(cursor.node());

      // Custom allocation here is just malloc
      IntrusiveHolder* place = (IntrusiveHolder*)os::malloc(sizeof(IntrusiveHolder), mtTest);
      new (place) IntrusiveHolder(n, n);

      intrusive_tree.insert_at_cursor(place->get_node(), cursor);
      IntrusiveCursor cursor2 = intrusive_tree.cursor(n);

      EXPECT_NOT_NULL(cursor2.node());

      intrusive_tree.verify_self();
    }

    // Check inserted values
    for (int n = 0; n < num_iterations; n++) {
      IntrusiveCursor cursor = intrusive_tree.cursor(n);
      EXPECT_NOT_NULL(cursor.node());
      EXPECT_EQ(n, IntrusiveHolder::cast_to_self(cursor.node())->data);
    }

    // Remove all values
    for (int n = 0; n < num_iterations; n++) {
      IntrusiveCursor cursor = intrusive_tree.cursor(n);
      EXPECT_NOT_NULL(cursor.node());

      intrusive_tree.remove_at_cursor(cursor);
      IntrusiveCursor cursor2 = intrusive_tree.cursor(n);

      EXPECT_NULL(cursor2.node());

      intrusive_tree.verify_self();
    }

    // Check removed values
    for (int n = 0; n < num_iterations; n++) {
      IntrusiveCursor cursor = intrusive_tree.cursor(n);
      EXPECT_NULL(cursor.node());
    }
  }

  static bool custom_validator(const IntrusiveRBNode* n) {
    IntrusiveHolder* holder = IntrusiveHolder::cast_to_self(n);
    assert(holder->key == holder->data, "must be");

    return true;
  }

  void test_custom_verify_intrusive() {
    IntrusiveTreeInt intrusive_tree;
    int num_nodes = 100;

    // Insert values
    for (int n = 0; n < num_nodes; n++) {
      IntrusiveCursor cursor = intrusive_tree.cursor(n);
      EXPECT_NULL(cursor.node());

      // Custom allocation here is just malloc
      IntrusiveHolder* place = (IntrusiveHolder*)os::malloc(sizeof(IntrusiveHolder), mtTest);
      new (place) IntrusiveHolder(n, n);

      intrusive_tree.insert_at_cursor(place->get_node(), cursor);
      IntrusiveCursor cursor2 = intrusive_tree.cursor(n);

      EXPECT_NOT_NULL(cursor2.node());
    }

    intrusive_tree.verify_self(RBTreeTest::custom_validator);

    int node_count = 0;
    intrusive_tree.verify_self([&](const IntrusiveRBNode* n) {
      node_count++;

      IntrusiveHolder* holder = IntrusiveHolder::cast_to_self(n);
      assert(holder->key >= 0, "must be");
      assert(holder->data >= 0, "must be");
      assert(holder->key < num_nodes, "must be");
      assert(holder->data < num_nodes, "must be");

      return true;
    });

    EXPECT_EQ(node_count, num_nodes);
  }

  #ifdef ASSERT
  void test_nodes_visited_once() {
    constexpr size_t memory_size = 65536;
    using Tree = RBTree<int, int, Cmp, ArrayAllocator<memory_size>>;
    using Node = RBNode<int, int>;

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

TEST_VM_F(RBTreeTest, TestVisitOutsideRange) {
  this->test_visit_outside_range();
}

TEST_VM_F(RBTreeTest, TestClosestLeq) {
  this->test_closest_leq();
}

TEST_VM_F(RBTreeTest, TestClosestGt) {
  this->test_closest_gt();
}

TEST_VM_F(RBTreeTest, TestFirst) {
  this->test_leftmost();
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


TEST_VM_F(RBTreeTest, NodeHints) {
  this->test_node_hints();
}

TEST_VM_F(RBTreeTest, CursorFind) {
  this->test_cursor();
}

TEST_VM_F(RBTreeTest, CursorGet) {
  this->test_cursor();
}

TEST_VM_F(RBTreeTest, CursorEmptyTreeTest) {
  this->test_cursor_empty_tree();
}

TEST_VM_F(RBTreeTest, CursorIterateTest) {
  this->test_cursor_iterate();
}

TEST_VM_F(RBTreeTest, LeftMostRightMost) {
  this->test_leftmost_rightmost();
}

struct PtrCmp {
  static RBTreeOrdering cmp(const void* a, const void* b) {
    const uintptr_t ai = p2u(a);
    const uintptr_t bi = p2u(b);

    if (ai < bi) return RBTreeOrdering::LT;
    if (ai > bi) return RBTreeOrdering::GT;
    return RBTreeOrdering::EQ;
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
  tree.upsert(p1, 1U);
  tree.upsert(p2, 2U);
  tree.upsert(p3, 3U);
  stringStream ss;
  tree.print_on(&ss);
  const char* const N = nullptr;
  ASSERT_NE(strstr(ss.base(), s1), N);
  ASSERT_NE(strstr(ss.base(), s2), N);
  ASSERT_NE(strstr(ss.base(), s3), N);
}

struct IntCmp {
  static RBTreeOrdering cmp(int a, int b) {
    if (a < b) return RBTreeOrdering::LT;
    if (a > b) return RBTreeOrdering::GT;
    return RBTreeOrdering::EQ;
  }
};

TEST_VM(RBTreeTestNonFixture, TestPrintIntegerTree) {
  using TreeType = RBTreeCHeap<int, unsigned, IntCmp, mtTest>;
  TreeType tree;
  const int i1 = 82924;
  const char* const s1 = "[82924] = 1";
  const int i2 = -13591;
  const char* const s2 = "[-13591] = 2";
  const int i3 = 0;
  const char* const s3 = "[0] = 3";
  tree.upsert(i1, 1U);
  tree.upsert(i2, 2U);
  tree.upsert(i3, 3U);
  stringStream ss;
  tree.print_on(&ss);
  const char* const N = nullptr;
  ASSERT_NE(strstr(ss.base(), s1), N);
  ASSERT_NE(strstr(ss.base(), s2), N);
  ASSERT_NE(strstr(ss.base(), s3), N);
}

TEST_VM(RBTreeTestNonFixture, TestPrintCustomPrinter) {
  typedef RBTreeCHeap<int, unsigned, IntCmp, mtTest> TreeType;
  typedef RBNode<int, unsigned> NodeType;

  TreeType tree;
  const int i1 = -13591;
  const int i2 = 0;
  const int i3 = 82924;
  tree.upsert(i1, 1U);
  tree.upsert(i2, 2U);
  tree.upsert(i3, 3U);

  stringStream ss;
  int print_count = 0;
  tree.print_on(&ss, [&](outputStream* st, const NodeType* n, int depth) {
    st->print_cr("[%d] (%d): %d", depth, n->val(), n->key());
    print_count++;
  });

const char* const expected =
    "[0] (2): 0\n"
    "[1] (1): -13591\n"
    "[1] (3): 82924\n";

  ASSERT_EQ(print_count, 3);
  ASSERT_STREQ(ss.base(), expected);
}

TEST_VM_F(RBTreeTest, IntrusiveTest) {
  this->test_intrusive();
}

TEST_VM_F(RBTreeTest, IntrusiveCustomVerifyTest) {
  this->test_custom_verify_intrusive();
}

TEST_VM_F(RBTreeTest, FillAndVerify) {
  this->test_fill_verify();
}

TEST_VM_F(RBTreeTest, CursorReplace) {
  this->test_cursor_replace();
}

#ifdef ASSERT
TEST_VM_F(RBTreeTest, NodesVisitedOnce) {
  this->test_nodes_visited_once();
}

TEST_VM_ASSERT_MSG(RBTreeTestNonFixture, CustomVerifyAssert, ".*failed on key = 7") {
  typedef RBTreeCHeap<int, int, IntCmp, mtTest> TreeType;
  typedef RBNode<int, int> NodeType;

  TreeType tree;
  for (int i = 0; i < 10; i++) {
    tree.upsert(i, i);
  }

  tree.verify_self([&](const NodeType* n) {
    assert(n->key() != 7, "failed on key = %d", n->key());
    return true;
  });
}

#endif // ASSERT

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

TEST_VM_F(RBTreeTest, CustomVerify) {
  constexpr int num_nodes = 1000;
  RBTreeInt tree;
  for (int i = 0; i < num_nodes; i++) {
    tree.upsert(i, i);
  }

  int node_count = 0;
  tree.verify_self([&](const RBTreeIntNode* n) {
    node_count++;

    assert(n->key() >= 0, "must be");
    assert(n->key() < num_nodes, "must be");
    return true;
  });

  EXPECT_EQ(node_count, num_nodes);
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
    RBTreeInt::Cursor cursor = rbtree.cursor(10);
    RBTreeInt::Cursor cursor2 = rbtree.next(cursor);
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
    RBTreeCHeap<int, int, Cmp, mtOther> rbtree;
    constexpr int one_hundred_thousand = 100000;
    for (int i = 0; i < one_hundred_thousand; i++) {
      rbtree.upsert(i, i);
    }
    EXPECT_EQ((size_t)one_hundred_thousand, rbtree.size());
    rbtree.verify_self();
  }
}

TEST_VM_F(RBTreeTest, TestCopyInto) {
  {
    RBTreeInt rbtree1;
    RBTreeInt rbtree2;

    rbtree1.copy_into(rbtree2);
    rbtree2.verify_self();
  }

  RBTreeInt rbtree1;
  RBTreeInt rbtree2;

  int size = 1000;
  for (int i = 0; i < size; i++) {
    rbtree1.upsert(i, i);
  }

  rbtree1.copy_into(rbtree2);
  rbtree2.verify_self();

  ResourceMark rm;
  GrowableArray<int> allocations(size);
  int size1 = 0;
  rbtree1.visit_in_order([&](RBTreeIntNode* node) {
    size1++;
    allocations.append(node->key());
    return true;
  });

  int size2 = 0;
  rbtree2.visit_in_order([&](RBTreeIntNode* node) {
    EXPECT_EQ(node->key(), allocations.at(size2++));
    return true;
  });

  EXPECT_EQ(size1, size2);
  EXPECT_EQ(rbtree1.size(), rbtree2.size());
  EXPECT_EQ(size2, static_cast<int>(rbtree2.size()));
}

struct OomAllocator {
  void* allocate(size_t sz) {
    return nullptr;
  }
  void free(void* ptr) {}
};
TEST_VM_F(RBTreeTest, AllocatorMayReturnNull) {
  RBTree<int, int, Cmp, OomAllocator> rbtree;
  bool success = rbtree.upsert(5, 5);
  EXPECT_EQ(false, success);
  // The test didn't exit the VM, so it was succesful.
}

TEST_VM_F(RBTreeTest, ArenaAllocator) {
  Arena arena(mtTest);
  RBTreeArena<int, int, Cmp> rbtree(&arena);
  bool success = rbtree.upsert(5, 5);
  ASSERT_EQ(true, success);
}

TEST_VM_F(RBTreeTest, ResourceAreaAllocator) {
  ResourceArea area(mtTest);
  RBTreeResourceArea<int, int, Cmp> rbtree(&area);
  bool success = rbtree.upsert(5, 5);
  ASSERT_EQ(true, success);
}

/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/z/zIntrusiveRBTree.inline.hpp"
#include "memory/allocation.hpp"
#include "memory/arena.hpp"
#include "nmt/memTag.hpp"
#include "unittest.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "zunittest.hpp"

#include <limits>

struct ZRBTestEntryCompare {
  int operator()(const ZIntrusiveRBTreeNode* a, const ZIntrusiveRBTreeNode* b);
  int operator()(int key, const ZIntrusiveRBTreeNode* entry);
};

class ZRBTestEntry : public ArenaObj {
  friend class ZIntrusiveRBTree<int, ZRBTestEntryCompare>;

public:
  using ZTree = ZIntrusiveRBTree<int, ZRBTestEntryCompare>;
private:
  const int   _id;
  ZIntrusiveRBTreeNode _node;

public:
  ZRBTestEntry(int id)
    : _id(id),
      _node() {}

  int id() const {
    return _id;
  }

  static ZIntrusiveRBTreeNode* cast_to_inner(ZRBTestEntry* element) {
    return &element->_node;
  }
  static const ZRBTestEntry* cast_to_outer(const ZIntrusiveRBTreeNode* node) {
    return (ZRBTestEntry*)((uintptr_t)node - offset_of(ZRBTestEntry, _node));
  }

};

int ZRBTestEntryCompare::operator()(const ZIntrusiveRBTreeNode* a, const ZIntrusiveRBTreeNode* b) {
  return ZRBTestEntry::cast_to_outer(a)->id() - ZRBTestEntry::cast_to_outer(b)->id();
}
int ZRBTestEntryCompare::operator()(int key, const ZIntrusiveRBTreeNode* entry) {
  return key - ZRBTestEntry::cast_to_outer(entry)->id();
}

class ZTreeTest : public ZTest {
public:
  void shuffle_array(ZRBTestEntry** beg, ZRBTestEntry** end);
  void reverse_array(ZRBTestEntry** beg, ZRBTestEntry** end);
};

class ResettableArena : public Arena {
public:
  using Arena::Arena;

  void reset_arena() {
    if (_chunk != _first) {
      set_size_in_bytes(_chunk->length());
      Chunk::next_chop(_first);
    }
    _chunk = _first;
    _hwm = _chunk->bottom();
    _max = _chunk->top();
  }
};

TEST_F(ZTreeTest, test_random) {
  constexpr size_t sizes[] = {1, 2, 4, 8, 16, 1024, 1024 * 1024};
  constexpr size_t num_sizes = ARRAY_SIZE(sizes);
  constexpr size_t iterations_multiplier = 4;
  constexpr size_t max_allocation_size = sizes[num_sizes - 1] * iterations_multiplier * sizeof(ZRBTestEntry);
  ResettableArena arena{MemTag::mtTest, Arena::Tag::tag_other, max_allocation_size};
  for (size_t s : sizes) {
    ZRBTestEntry::ZTree tree;
    const size_t num_iterations = s * iterations_multiplier;
    for (size_t i = 0; i < num_iterations; i++) {
      if (i % s == 0) {
        tree.verify_tree();
      }
      int id = random() % s;
      auto cursor = tree.find(id);
      if (cursor.found()) {
        // Replace or Remove
        if (i % 2 == 0) {
          // Replace
          if (i % 4 == 0) {
            // Replace with new
            tree.replace(ZRBTestEntry::cast_to_inner(new (&arena) ZRBTestEntry(id)), cursor);
          } else {
            // Replace with same
            tree.replace(cursor.node(), cursor);
          }
        } else {
          // Remove
          tree.remove(cursor);
        }
      } else {
        // Insert
        tree.insert(ZRBTestEntry::cast_to_inner(new (&arena) ZRBTestEntry(id)), cursor);
      }
    }
    tree.verify_tree();
    arena.reset_arena();
  }
}

void ZTreeTest::reverse_array(ZRBTestEntry** beg, ZRBTestEntry** end) {
  if (beg == end) {
    return;
  }

  ZRBTestEntry** first = beg;
  ZRBTestEntry** last = end - 1;
  while (first < last) {
    ::swap(*first, *last);
    first++;
    last--;
  }
}

void ZTreeTest::shuffle_array(ZRBTestEntry** beg, ZRBTestEntry** end) {
  if (beg == end) {
    return;
  }

  for (ZRBTestEntry** first = beg + 1; first != end; first++) {
    const ptrdiff_t distance = first - beg;
    ASSERT_GE(distance, 0);
    const ptrdiff_t random_index = random() % (distance + 1);
    ::swap(*first, *(beg + random_index));
  }
}

TEST_F(ZTreeTest, test_insert) {
  Arena arena(MemTag::mtTest);
  constexpr size_t num_entries = 1024;
  ZRBTestEntry* forward[num_entries]{};
  ZRBTestEntry* reverse[num_entries]{};
  ZRBTestEntry* shuffle[num_entries]{};
  for (size_t i = 0; i < num_entries; i++) {
    const int id = static_cast<int>(i);
    forward[i] = new (&arena) ZRBTestEntry(id);
    reverse[i] = new (&arena) ZRBTestEntry(id);
    shuffle[i] = new (&arena) ZRBTestEntry(id);
  }
  reverse_array(reverse, reverse + num_entries);
  shuffle_array(shuffle, shuffle + num_entries);

  ZRBTestEntry::ZTree forward_tree;
  auto cursor = forward_tree.root_cursor();
  for (size_t i = 0; i < num_entries; i++) {
    ASSERT_TRUE(cursor.is_valid());
    ASSERT_FALSE(cursor.found());
    ZIntrusiveRBTreeNode* const new_node = ZRBTestEntry::cast_to_inner(forward[i]);
    forward_tree.insert(new_node, cursor);
    cursor = forward_tree.next_cursor(new_node);
  }
  forward_tree.verify_tree();

  ZRBTestEntry::ZTree reverse_tree;
  cursor = reverse_tree.root_cursor();
  for (size_t i = 0; i < num_entries; i++) {
    ASSERT_TRUE(cursor.is_valid());
    ASSERT_FALSE(cursor.found());
    ZIntrusiveRBTreeNode* const new_node = ZRBTestEntry::cast_to_inner(reverse[i]);
    reverse_tree.insert(new_node, cursor);
    cursor = reverse_tree.prev_cursor(new_node);
  }
  reverse_tree.verify_tree();

  ZRBTestEntry::ZTree shuffle_tree;
  for (size_t i = 0; i < num_entries; i++) {
    cursor = shuffle_tree.find(reverse[i]->id());
    ASSERT_TRUE(cursor.is_valid());
    ASSERT_FALSE(cursor.found());
    ZIntrusiveRBTreeNode* const new_node = ZRBTestEntry::cast_to_inner(reverse[i]);
    shuffle_tree.insert(new_node, cursor);
  }
  shuffle_tree.verify_tree();

  ZRBTestEntryCompare compare_fn;
  const ZIntrusiveRBTreeNode* forward_node = forward_tree.first();
  const ZIntrusiveRBTreeNode* reverse_node = reverse_tree.first();
  const ZIntrusiveRBTreeNode* shuffle_node = shuffle_tree.first();
  size_t count = 0;
  while (true) {
    count++;
    ASSERT_EQ(compare_fn(forward_node, reverse_node), 0);
    ASSERT_EQ(compare_fn(forward_node, shuffle_node), 0);
    ASSERT_EQ(compare_fn(reverse_node, shuffle_node), 0);
    const ZIntrusiveRBTreeNode* forward_next_node = forward_node->next();
    const ZIntrusiveRBTreeNode* reverse_next_node = reverse_node->next();
    const ZIntrusiveRBTreeNode* shuffle_next_node = shuffle_node->next();
    if (forward_next_node == nullptr) {
      ASSERT_EQ(forward_next_node, reverse_next_node);
      ASSERT_EQ(forward_next_node, shuffle_next_node);
      ASSERT_EQ(forward_node, forward_tree.last());
      ASSERT_EQ(reverse_node, reverse_tree.last());
      ASSERT_EQ(shuffle_node, shuffle_tree.last());
      break;
    }
    ASSERT_LT(compare_fn(forward_node, forward_next_node), 0);
    ASSERT_LT(compare_fn(reverse_node, reverse_next_node), 0);
    ASSERT_LT(compare_fn(shuffle_node, shuffle_next_node), 0);
    forward_node = forward_next_node;
    reverse_node = reverse_next_node;
    shuffle_node = shuffle_next_node;
  }
  ASSERT_EQ(count, num_entries);
}

TEST_F(ZTreeTest, test_replace) {
  Arena arena(MemTag::mtTest);
  constexpr size_t num_entries = 1024;
  ZRBTestEntry::ZTree tree;
  auto cursor = tree.root_cursor();
  for (size_t i = 0; i < num_entries; i++) {
    ASSERT_TRUE(cursor.is_valid());
    ASSERT_FALSE(cursor.found());
    const int id = static_cast<int>(i) * 2 + 1;
    ZIntrusiveRBTreeNode* const new_node = ZRBTestEntry::cast_to_inner(new (&arena) ZRBTestEntry(id));
    tree.insert(new_node, cursor);
    cursor = tree.next_cursor(new_node);
  }
  tree.verify_tree();

  size_t i = 0;
  for (auto it = tree.begin(), end = tree.end(); it != end; ++it) {
    auto& node = *it;
    if (i % (num_entries / 4)) {
      tree.verify_tree();
    }
    switch (i++ % 4) {
      case 0: {
        // Decrement
        ZRBTestEntry* new_entry = new (&arena) ZRBTestEntry(ZRBTestEntry::cast_to_outer(&node)->id() - 1);
        it.replace(ZRBTestEntry::cast_to_inner(new_entry));
      } break;
      case 1: break;
      case 2: {
        // Increment
        ZRBTestEntry* new_entry = new (&arena) ZRBTestEntry(ZRBTestEntry::cast_to_outer(&node)->id() + 1);
        it.replace(ZRBTestEntry::cast_to_inner(new_entry));
      } break;
      case 3: break;
      default:
        ShouldNotReachHere();
    }
  }
  tree.verify_tree();

  int last_id = std::numeric_limits<int>::min();
  for (auto& node : tree) {
    int id = ZRBTestEntry::cast_to_outer(&node)->id();
    ASSERT_LT(last_id, id);
    last_id = id;
  }
  tree.verify_tree();

  last_id = std::numeric_limits<int>::min();
  for (auto it = tree.begin(), end = tree.end(); it != end; ++it) {
    int id = ZRBTestEntry::cast_to_outer(&*it)->id();
    ASSERT_LT(last_id, id);
    last_id = id;
  }
  tree.verify_tree();

  last_id = std::numeric_limits<int>::min();
  for (auto it = tree.cbegin(), end = tree.cend(); it != end; ++it) {
    int id = ZRBTestEntry::cast_to_outer(&*it)->id();
    ASSERT_LT(last_id, id);
    last_id = id;
  }
  tree.verify_tree();

  last_id = std::numeric_limits<int>::max();
  for (auto it = tree.rbegin(), end = tree.rend(); it != end; ++it) {
    int id = ZRBTestEntry::cast_to_outer(&*it)->id();
    ASSERT_GT(last_id, id);
    last_id = id;
  }
  tree.verify_tree();

  last_id = std::numeric_limits<int>::max();
  for (auto it = tree.crbegin(), end = tree.crend(); it != end; ++it) {
    int id = ZRBTestEntry::cast_to_outer(&*it)->id();
    ASSERT_GT(last_id, id);
    last_id = id;
  }
  tree.verify_tree();
}

TEST_F(ZTreeTest, test_remove) {
  Arena arena(MemTag::mtTest);
  constexpr int num_entries = 1024;
  ZRBTestEntry::ZTree tree;
  int id = 0;
  tree.insert(ZRBTestEntry::cast_to_inner(new (&arena) ZRBTestEntry(++id)), tree.root_cursor());
  for (auto& node : tree) {
    if (ZRBTestEntry::cast_to_outer(&node)->id() == num_entries) {
      break;
    }
    auto cursor = tree.next_cursor(&node);
    ZIntrusiveRBTreeNode* const new_node = ZRBTestEntry::cast_to_inner(new (&arena) ZRBTestEntry(++id));
    tree.insert(new_node, cursor);
  }
  tree.verify_tree();
  ASSERT_EQ(ZRBTestEntry::cast_to_outer(tree.last())->id(), num_entries);

  int i = 0;
  int removed = 0;
  for (auto it = tree.begin(), end = tree.end(); it != end; ++it) {
    if (i++ % 2 == 0) {
      it.remove();
      ++removed;
    }
  }
  tree.verify_tree();

  int count = 0;
  for (auto it = tree.cbegin(), end = tree.cend(); it != end; ++it) {
    ++count;
  }
  ASSERT_EQ(count, num_entries - removed);
  tree.verify_tree();

  for (auto it = tree.rbegin(), end = tree.rend(); it != end; ++it) {
    if (i++ % 2 == 0) {
      it.remove();
      ++removed;
    }
  }
  tree.verify_tree();

  count = 0;
  for (auto it = tree.cbegin(), end = tree.cend(); it != end; ++it) {
    ++count;
  }
  ASSERT_EQ(count, num_entries - removed);
  tree.verify_tree();

  for (auto it = tree.begin(), end = tree.end(); it != end; ++it) {
    it.remove();
    removed++;
  }
  tree.verify_tree();

  ASSERT_EQ(removed, num_entries);
  ASSERT_EQ(tree.last(), nullptr);
  ASSERT_EQ(tree.first(), nullptr);
}

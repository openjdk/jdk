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

#ifndef SHARE_GC_Z_ZINTRUSIVERBTREE_HPP
#define SHARE_GC_Z_ZINTRUSIVERBTREE_HPP

#include "metaprogramming/enableIf.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

enum class ZIntrusiveRBTreeDirection { LEFT, RIGHT };

class ZIntrusiveRBTreeNode {
  template <typename Key, typename Compare>
  friend class ZIntrusiveRBTree;

public:
  enum Color { RED = 0b0, BLACK = 0b1 };

private:
  class ColoredNodePtr {
  private:
    static constexpr uintptr_t COLOR_MASK = 0b1;
    static constexpr uintptr_t NODE_MASK = ~COLOR_MASK;

    uintptr_t _value;

  public:
    ColoredNodePtr(ZIntrusiveRBTreeNode* node = nullptr, Color color = RED);

    constexpr Color color() const;
    constexpr bool is_black() const;
    constexpr bool is_red() const;

    ZIntrusiveRBTreeNode* node() const;
    ZIntrusiveRBTreeNode* red_node() const;
    ZIntrusiveRBTreeNode* black_node() const;
  };

private:
  ColoredNodePtr _colored_parent;
  ZIntrusiveRBTreeNode* _left;
  ZIntrusiveRBTreeNode* _right;

  template <ZIntrusiveRBTreeDirection DIRECTION>
  const ZIntrusiveRBTreeNode* find_next_node() const;

  template <ZIntrusiveRBTreeDirection DIRECTION>
  const ZIntrusiveRBTreeNode* child() const;
  template <ZIntrusiveRBTreeDirection DIRECTION>
  ZIntrusiveRBTreeNode* child();

  template <ZIntrusiveRBTreeDirection DIRECTION>
  ZIntrusiveRBTreeNode* const* child_addr() const;

  template <ZIntrusiveRBTreeDirection DIRECTION>
  bool has_child() const;

  template <ZIntrusiveRBTreeDirection DIRECTION>
  void update_child(ZIntrusiveRBTreeNode* new_child);

  void link_node(ZIntrusiveRBTreeNode* parent, ZIntrusiveRBTreeNode** insert_location);

  void copy_parent_and_color(ZIntrusiveRBTreeNode* other);
  void update_parent_and_color(ZIntrusiveRBTreeNode* parent, Color color);

  void update_parent(ZIntrusiveRBTreeNode* parent);
  void update_color(Color color);

  void update_left_child(ZIntrusiveRBTreeNode* new_child);
  void update_right_child(ZIntrusiveRBTreeNode* new_child);

  const ZIntrusiveRBTreeNode* parent() const;
  ZIntrusiveRBTreeNode* parent();
  const ZIntrusiveRBTreeNode* red_parent() const;
  ZIntrusiveRBTreeNode* red_parent();
  const ZIntrusiveRBTreeNode* black_parent() const;
  ZIntrusiveRBTreeNode* black_parent();

  bool has_parent() const;

  Color color() const;
  bool is_black() const;
  bool is_red() const;
  static bool is_black(ZIntrusiveRBTreeNode* node);

  ZIntrusiveRBTreeNode* const* left_child_addr() const;
  ZIntrusiveRBTreeNode* const* right_child_addr() const;

  const ZIntrusiveRBTreeNode* left_child() const;
  ZIntrusiveRBTreeNode* left_child();
  const ZIntrusiveRBTreeNode* right_child() const;
  ZIntrusiveRBTreeNode* right_child();

  bool has_left_child() const;
  bool has_right_child() const;

public:
  ZIntrusiveRBTreeNode();

  const ZIntrusiveRBTreeNode* prev() const;
  ZIntrusiveRBTreeNode* prev();
  const ZIntrusiveRBTreeNode* next() const;
  ZIntrusiveRBTreeNode* next();
};

template <typename Key, typename Compare>
class ZIntrusiveRBTree {
public:
  class FindCursor {
    friend class ZIntrusiveRBTree<Key, Compare>;

  private:
    ZIntrusiveRBTreeNode** _insert_location;
    ZIntrusiveRBTreeNode* _parent;
    bool _left_most;
    bool _right_most;
    DEBUG_ONLY(uintptr_t _sequence_number;)

    FindCursor(ZIntrusiveRBTreeNode** insert_location, ZIntrusiveRBTreeNode* parent, bool left_most, bool right_most DEBUG_ONLY(COMMA uintptr_t sequence_number));
    FindCursor();

#ifdef ASSERT
    bool is_valid(uintptr_t sequence_number) const;
#endif

  public:
    FindCursor(const FindCursor&) = default;
    FindCursor& operator=(const FindCursor&) = default;

    bool is_valid() const;
    bool found() const;
    ZIntrusiveRBTreeNode* node() const;
    bool is_left_most() const;
    bool is_right_most() const;
    ZIntrusiveRBTreeNode* parent() const;
    ZIntrusiveRBTreeNode** insert_location() const;
  };

private:
  ZIntrusiveRBTreeNode* _root_node;
  ZIntrusiveRBTreeNode* _left_most;
  ZIntrusiveRBTreeNode* _right_most;
  DEBUG_ONLY(uintptr_t _sequence_number;)

  NONCOPYABLE(ZIntrusiveRBTree);

#ifdef ASSERT
  template <bool swap_left_right>
  bool verify_node(ZIntrusiveRBTreeNode* parent, ZIntrusiveRBTreeNode* left_child, ZIntrusiveRBTreeNode* right_child);
  template <bool swap_left_right>
  bool verify_node(ZIntrusiveRBTreeNode* parent);
  template <bool swap_left_right>
  bool verify_node(ZIntrusiveRBTreeNode* parent, ZIntrusiveRBTreeNode* left_child);
  struct any_t {};
  template <bool swap_left_right>
  bool verify_node(ZIntrusiveRBTreeNode* parent, any_t, ZIntrusiveRBTreeNode* right_child);
#endif // ASSERT

  ZIntrusiveRBTreeNode* const* root_node_addr() const;

  void update_child_or_root(ZIntrusiveRBTreeNode* old_node, ZIntrusiveRBTreeNode* new_node, ZIntrusiveRBTreeNode* parent);
  void rotate_and_update_child_or_root(ZIntrusiveRBTreeNode* old_node, ZIntrusiveRBTreeNode* new_node, ZIntrusiveRBTreeNode::Color color);

  template <ZIntrusiveRBTreeDirection PARENT_SIBLING_DIRECTION>
  void rebalance_insert_with_sibling(ZIntrusiveRBTreeNode* node, ZIntrusiveRBTreeNode* parent, ZIntrusiveRBTreeNode* grand_parent);
  template <ZIntrusiveRBTreeDirection PARENT_SIBLING_DIRECTION>
  bool rebalance_insert_with_parent_sibling(ZIntrusiveRBTreeNode** node_addr, ZIntrusiveRBTreeNode** parent_addr, ZIntrusiveRBTreeNode* grand_parent);
  void rebalance_insert(ZIntrusiveRBTreeNode* new_node);

  template <ZIntrusiveRBTreeDirection SIBLING_DIRECTION>
  bool rebalance_remove_with_sibling(ZIntrusiveRBTreeNode** node_addr, ZIntrusiveRBTreeNode** parent_addr);
  void rebalance_remove(ZIntrusiveRBTreeNode* rebalance_from);

  FindCursor make_cursor(ZIntrusiveRBTreeNode* const* insert_location, ZIntrusiveRBTreeNode* parent, bool left_most, bool right_most) const;
  template <ZIntrusiveRBTreeDirection DIRECTION>
  FindCursor find_next(const FindCursor& cursor) const;

public:
  ZIntrusiveRBTree();

  ZIntrusiveRBTreeNode* first() const;
  ZIntrusiveRBTreeNode* last() const;

  FindCursor root_cursor() const;
  FindCursor get_cursor(const ZIntrusiveRBTreeNode* node) const;
  FindCursor prev_cursor(const ZIntrusiveRBTreeNode* node) const;
  FindCursor next_cursor(const ZIntrusiveRBTreeNode* node) const;
  FindCursor prev(const FindCursor& cursor) const;
  FindCursor next(const FindCursor& cursor) const;
  FindCursor find(const Key& key) const;

  void insert(ZIntrusiveRBTreeNode* new_node, const FindCursor& find_cursor);
  void replace(ZIntrusiveRBTreeNode* new_node, const FindCursor& find_cursor);
  void remove(const FindCursor& find_cursor);

  void verify_tree();

public:
  template <bool IsConst, bool Reverse>
  class IteratorImplementation;

  using Iterator = IteratorImplementation<false, false>;
  using ConstIterator = IteratorImplementation<true, false>;
  using ReverseIterator = IteratorImplementation<false, true>;
  using ConstReverseIterator = IteratorImplementation<true, true>;

  // remove and replace invalidate the iterators
  // however the iterators provide a remove and replace
  // function which does not invalidate that iterator nor
  // any end iterator
  Iterator begin();
  Iterator end();
  ConstIterator begin() const;
  ConstIterator end() const;
  ConstIterator cbegin() const;
  ConstIterator cend() const;
  ReverseIterator rbegin();
  ReverseIterator rend();
  ConstReverseIterator rbegin() const;
  ConstReverseIterator rend() const;
  ConstReverseIterator crbegin() const;
  ConstReverseIterator crend() const;
};

template <typename Key, typename Compare>
template <bool IsConst, bool Reverse>
class ZIntrusiveRBTree<Key, Compare>::IteratorImplementation {
  friend IteratorImplementation<true, Reverse>;

public:
  using difference_type   = std::ptrdiff_t;
  using value_type        = const ZIntrusiveRBTreeNode;
  using pointer           = value_type*;
  using reference         = value_type&;

private:
  ZIntrusiveRBTree<Key, Compare>* _tree;
  const ZIntrusiveRBTreeNode* _node;
  bool _removed;

  bool at_end() const;

public:
  IteratorImplementation(ZIntrusiveRBTree<Key, Compare>& tree, pointer node);
  IteratorImplementation(const IteratorImplementation<IsConst, Reverse>&) = default;
  template <bool Enable = IsConst, ENABLE_IF(Enable)>
  IteratorImplementation(const IteratorImplementation<false, Reverse>& other);

  reference operator*() const;
  pointer operator->();
  IteratorImplementation& operator--();
  IteratorImplementation operator--(int);
  IteratorImplementation& operator++();
  IteratorImplementation operator++(int);

  template <bool Enable = !IsConst, ENABLE_IF(Enable)>
  void replace(ZIntrusiveRBTreeNode * new_node);
  template <bool Enable = !IsConst, ENABLE_IF(Enable)>
  void remove();

  // Note: friend operator overloads defined inside class declaration because of problems with ADL
  friend bool operator==(const IteratorImplementation& a, const IteratorImplementation& b) {
    precond(a._tree == b._tree);
    return a._node == b._node;
  }
  friend bool operator!=(const IteratorImplementation& a, const IteratorImplementation& b) {
    precond(a._tree == b._tree);
    return a._node != b._node;
  }
};

#endif // SHARE_GC_Z_ZINTRUSIVERBTREE_HPP

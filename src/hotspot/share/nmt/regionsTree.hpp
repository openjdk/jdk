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
#ifndef NMT_REGIONSTREE_HPP
#define NMT_REGIONSTREE_HPP

#include "logging/log.hpp"
#include "nmt/nmtCommon.hpp"
#include "nmt/vmatree.hpp"


class ReservedMemoryRegion;
class CommittedMemoryRegion;
// RegionsTree extends VMATree to add some more specific API and also defines a helper
// for processing the tree nodes in a shorter and more meaningful way.
class RegionsTree : public VMATree {
  NativeCallStackStorage _ncs_storage;
  bool _with_storage;

 public:
  RegionsTree(bool with_storage) : VMATree() , _ncs_storage(with_storage), _with_storage(with_storage) { }

  ReservedMemoryRegion find_reserved_region(address addr);

  SummaryDiff commit_region(address addr, size_t size, const NativeCallStack& stack);
  SummaryDiff uncommit_region(address addr, size_t size);

  using Node = VMATree::TreapNode;

  class NodeHelper {
      Node* _node;
      public:
      NodeHelper() : _node(nullptr) { }
      NodeHelper(Node* node) : _node(node) { }
      inline bool is_valid() const { return _node != nullptr; }
      inline void clear_node() { _node = nullptr; }
      inline VMATree::position position() const { return _node->key(); }
      inline bool is_committed_begin() const { return ((uint8_t)out_state() & (uint8_t)VMATree::StateType::Committed) >= 2; }
      inline bool is_released_begin() const { return out_state() == VMATree::StateType::Released; }
      inline bool is_reserved_begin() const { return ((uint8_t)out_state() & (uint8_t)VMATree::StateType::Reserved) == 1; }
      inline VMATree::StateType in_state() const { return _node->val().in.type(); }
      inline VMATree::StateType out_state() const { return _node->val().out.type(); }
      inline size_t distance_from(const NodeHelper& other) const {
        assert (position() > other.position(), "negative distance");
        return position() - other.position();
      }
      inline NativeCallStackStorage::StackIndex out_stack_index() const { return _node->val().out.reserved_stack(); }
      inline MemTag in_tag() const { return _node->val().in.mem_tag(); }
      inline MemTag out_tag() const { return _node->val().out.mem_tag(); }
      inline void set_in_tag(MemTag tag) { _node->val().in.set_tag(tag); }
      inline void set_out_tag(MemTag tag) { _node->val().out.set_tag(tag); }
      DEBUG_ONLY(void print_on(outputStream* st);)
    };

  DEBUG_ONLY(void print_on(outputStream* st);)

  template<typename F>
  void visit_committed_regions(const ReservedMemoryRegion& rgn, F func);

  template<typename F>
  void visit_reserved_regions(F func);

  inline RegionData make_region_data(const NativeCallStack& ncs, MemTag tag) {
    return RegionData(_ncs_storage.push(ncs), tag);
  }

  inline const NativeCallStack stack(NodeHelper& node) {
    if (!_with_storage) {
      return NativeCallStack::empty_stack();
    }
    NativeCallStackStorage::StackIndex si = node.out_stack_index();
    return _ncs_storage.get(si);
  }
};

#endif // NMT_REGIONSTREE_HPP
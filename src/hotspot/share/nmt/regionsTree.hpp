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
#ifndef NMT_REGIONSTREE_HPP
#define NMT_REGIONSTREE_HPP

#include "logging/log.hpp"
#include "nmt/nmtCommon.hpp"
#include "nmt/vmatree.hpp"
#include "nmt/vmtCommon.hpp"

// RegionsTree extends VMATree to add some more specific API and also defines a helper
// for processing the tree nodes in a shorter and more meaningful way.
class RegionsTree : public VMATree {
 private:
  NativeCallStackStorage _ncs_storage;
  bool _with_storage;

 public:
  RegionsTree(bool with_storage) : VMATree() , _ncs_storage(with_storage), _with_storage(with_storage) { }

  ReservedMemoryRegion find_reserved_region(address addr);

  SummaryDiff commit_region(address addr, size_t size, const NativeCallStack& stack);
  SummaryDiff uncommit_region(address addr, size_t size);
  SummaryDiff region_summary(address addr, size_t size);

  using Node = VMATree::TreapNode;

  class NodeHelper {
      Node* _node;
      public:
      NodeHelper() : _node(nullptr) { }
      NodeHelper(Node* node) : _node(node) { }
      inline bool is_valid() { return _node != nullptr; }
      inline void clear_node() { _node = nullptr; }
      inline VMATree::position position() { return _node->key(); }
      inline bool is_committed_begin() { return ((uint8_t)out_state() & (uint8_t)VMATree::StateType::Committed) >= 2; }
      inline bool is_released_begin() { return out_state() == VMATree::StateType::Released; }
      inline bool is_reserved_begin() { return ((uint8_t)out_state() & (uint8_t)VMATree::StateType::Reserved) == 1; }
      inline VMATree::StateType in_state() { return _node->val().in.type(); }
      inline VMATree::StateType out_state() { return _node->val().out.type(); }
      inline size_t distance_from(NodeHelper& other) { return position() - other.position(); }
      inline NativeCallStackStorage::StackIndex out_stack_index() { return _node->val().out.stack(); }
      inline MemTag in_tag() { return _node->val().in.mem_tag(); }
      inline MemTag out_tag() { return _node->val().out.mem_tag(); }
      inline void set_in_tag(MemTag tag) { _node->val().in.set_tag(tag); }
      inline void set_out_tag(MemTag tag) { _node->val().out.set_tag(tag); }
      inline void print_on(outputStream* st) {
        auto st_str = [&](int s){
          return s == (int)VMATree::StateType::Released ? "Rl" :
                 s ==  (int)VMATree::StateType::Reserved ? "Rv" : "Cm";
        };
        st->print_cr("pos: " INTPTR_FORMAT " "
                     "%s, %s <|> %s, %s",
                     p2i((address)position()),
                     st_str((int)in_state()),
                     NMTUtil::tag_to_name(in_tag()),
                     st_str((int)out_state()),
                     NMTUtil::tag_to_name(out_tag())
                     );
      }
    };

  void print_on(outputStream* st) {
    visit_in_order([&](Node* node) {
      NodeHelper curr(node);
      curr.print_on(st);
      return true;
    });
  }

  template<typename F>
  void visit_committed_regions(const ReservedMemoryRegion& rgn, F func) {
    position start = (position)rgn.base();
    size_t end = (size_t)rgn.end() + 1;
    size_t comm_size = 0;

    NodeHelper prev;
    visit_range_in_order(start, end, [&](Node* node) {
      NodeHelper curr(node);
      if (prev.is_valid() && prev.is_committed_begin()) {
        CommittedMemoryRegion cmr((address)prev.position(), curr.distance_from(prev), stack(curr));
        if (!func(cmr))
          return false;
      }
      prev = curr;
      return true;
    });
  }

  template<typename F>
  void visit_reserved_regions(F func) {
    NodeHelper begin_node, prev;
    size_t rgn_size = 0;

    visit_in_order([&](Node* node) {
      NodeHelper curr(node);
      if (prev.is_valid()) {
        rgn_size += curr.distance_from(prev);
      } else {
        begin_node = curr;
        rgn_size = 0;
      }
      prev = curr;
      if (curr.is_released_begin() || begin_node.out_tag() != curr.out_tag()) {
        auto st = stack(curr);
        if (rgn_size == 0) {
          prev.clear_node();
          return true;
        }
        ReservedMemoryRegion rmr((address)begin_node.position(), rgn_size, st, begin_node.out_tag());
        if (!func(rmr))
          return false;
        rgn_size = 0;
        if (!curr.is_released_begin())
          begin_node = curr;
        else {
          begin_node.clear_node();
          prev.clear_node();
        }
      }

      return true;
    });
  }

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
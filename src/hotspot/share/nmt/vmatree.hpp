/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Red Hat Inc. All rights reserved.
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

#ifndef SHARE_NMT_VMATREE_HPP
#define SHARE_NMT_VMATREE_HPP

#include "memory/resourceArea.hpp"
#include "nmt/nmtNativeCallStackStorage.hpp"
#include "nmt/nmtTreap.hpp"
#include "runtime/os.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/growableArray.hpp"

// A VMATree stores a sequence of points on the natural number line.
// Each of these points stores information about a state change.
// For example, the state may go from released memory to committed memory,
// or from committed memory of a certain MEMFLAGS to committed memory of a different MEMFLAGS.
// The set of points is stored in a balanced binary tree for efficient querying and updating.
class VMATree {
  static int addr_cmp(size_t a, size_t b) {
    if (a < b) return -1;
    if (a == b) return 0;
    if (a > b) return 1;
    ShouldNotReachHere();
  }

public:
  enum class StateType : uint8_t { Reserved, Committed, Released };

  // Each point has some stack and a flag associated with it.
  struct Metadata {
    NativeCallStackStorage::StackIndex stack_idx;
    MEMFLAGS flag;

    Metadata()
      : stack_idx(),
        flag(mtNone) {
    }
    Metadata(NativeCallStackStorage::StackIndex stack_idx, MEMFLAGS flag)
      : stack_idx(stack_idx),
        flag(flag) {
    }
    static bool equals(const Metadata& a, const Metadata& b) {
      return NativeCallStackStorage::StackIndex::equals(a.stack_idx, b.stack_idx) &&
             a.flag == b.flag;
    }
  };

  struct IntervalState {
    StateType type;
    Metadata data;

    void merge(const IntervalState& b) {
      if (this->type == StateType::Released) {
        this->data.flag = b.data.flag;
        this->data.stack_idx = b.data.stack_idx;
      }
    }

    Metadata metadata() const {
      return data;
    }
  };

  // An IntervalChange indicates a change in state between two intervals. The incoming state
  // is denoted by in, and the outgoing state is denoted by out.
  struct IntervalChange {
    IntervalState in;
    IntervalState out;

    bool is_noop() {
      return (in.type == StateType::Released && out.type == StateType::Released) ||
             (in.type == out.type && Metadata::equals(in.data, out.data));
    }
  };

  using VTreap = TreapNode<size_t, IntervalChange, addr_cmp>;
  TreapCHeap<size_t, IntervalChange, addr_cmp> tree;
  VMATree()
    : tree() {
  }

  struct SingleDiff {
    int64_t reserve;
    int64_t commit;
  };
  struct SummaryDiff {
    SingleDiff flag[mt_number_of_types];
    SummaryDiff() {
      for (int i = 0; i < mt_number_of_types; i++) {
        flag[i] = {0, 0};
      }
    }
  };

  SummaryDiff register_mapping(size_t A, size_t B, StateType state, Metadata& metadata);

  SummaryDiff reserve_mapping(size_t from, size_t sz, Metadata& metadata) {
    return register_mapping(from, from + sz, StateType::Reserved, metadata);
  }

  SummaryDiff commit_mapping(size_t from, size_t sz, Metadata& metadata) {
    return register_mapping(from, from + sz, StateType::Committed, metadata);
  }

  SummaryDiff release_mapping(size_t from, size_t sz) {
    Metadata empty;
    return register_mapping(from, from + sz, StateType::Released, empty);
  }

  // Visit all nodes between [from, to) and call f on them.
  template<typename F>
  void visit(size_t from, size_t to, F f) {
    ResourceArea area(mtNMT);
    ResourceMark rm(&area);
    GrowableArray<VTreap*> to_visit(&area, 16, 0, nullptr);
    to_visit.push(tree.tree);
    VTreap* head = nullptr;
    while (!to_visit.is_empty()) {
      head = to_visit.pop();
      if (head == nullptr) continue;

      int cmp_from = addr_cmp(head->key(), from);
      int cmp_to = addr_cmp(head->key(), to);
      if (cmp_from >= 0 && cmp_to < 0) {
        f(head);
      }
      if (cmp_to >= 0) {
        to_visit.push(head->left());
      } else if (cmp_from >= 0) {
        to_visit.push(head->left());
        to_visit.push(head->right());
      } else {
        to_visit.push(head->right());
      }
    }
  }

private:
  template<typename F>
  void in_order_traversal_doer(F f, VTreap* node) const {
    if (node == nullptr) return;
    in_order_traversal_doer(f, node->left());
    f(node);
    in_order_traversal_doer(f, node->right());
  }

public:
  template<typename F>
  void in_order_traversal(F f) const {
    in_order_traversal_doer(f, tree.tree);
  }
};

#endif

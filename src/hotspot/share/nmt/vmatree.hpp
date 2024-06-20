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

#include "nmt/nmtNativeCallStackStorage.hpp"
#include "nmt/nmtTreap.hpp"
#include "runtime/os.hpp"
#include "utilities/globalDefinitions.hpp"
#include <cstdint>

// A VMATree stores a sequence of points on the natural number line.
// Each of these points stores information about a state change.
// For example, the state may go from released memory to committed memory,
// or from committed memory of a certain MEMFLAGS to committed memory of a different MEMFLAGS.
// The set of points is stored in a balanced binary tree for efficient querying and updating.
class VMATree {
  friend class VMATreeTest;
  // A position in memory.
public:
  using position = size_t;

  class PositionComparator {
  public:
    static int cmp(position a, position b) {
      if (a < b) return -1;
      if (a == b) return 0;
      if (a > b) return 1;
      ShouldNotReachHere();
    }
  };

  enum class StateType : uint8_t { Reserved, Committed, Released, LAST };

private:
  static const char* statetype_strings[static_cast<uint8_t>(StateType::LAST)];

public:
  NONCOPYABLE(VMATree);

  static const char* statetype_to_string(StateType type) {
    assert(type != StateType::LAST, "must be");
    return statetype_strings[static_cast<uint8_t>(type)];
  }

  // Each point has some stack and a flag associated with it.
  struct RegionData {
    const NativeCallStackStorage::StackIndex stack_idx;
    const MEMFLAGS flag;

    RegionData() : stack_idx(), flag(mtNone) {}

    RegionData(NativeCallStackStorage::StackIndex stack_idx, MEMFLAGS flag)
    : stack_idx(stack_idx), flag(flag) {}

    static bool equals(const RegionData& a, const RegionData& b) {
      return a.flag == b.flag &&
             NativeCallStackStorage::StackIndex::equals(a.stack_idx, b.stack_idx);
    }
  };

  static const RegionData empty_regiondata;

private:
  struct IntervalState {
  private:
    // Store the type and flag as two bytes
    uint8_t type_flag[2];
    NativeCallStackStorage::StackIndex sidx;

  public:
    IntervalState() : type_flag{0,0}, sidx() {}
    IntervalState(const StateType type, const RegionData data) {
      assert(!(type == StateType::Released) || data.flag == mtNone, "Released type must have flag mtNone");
      type_flag[0] = static_cast<uint8_t>(type);
      type_flag[1] = static_cast<uint8_t>(data.flag);
      sidx = data.stack_idx;
    }

    StateType type() const {
      return static_cast<StateType>(type_flag[0]);
    }

    MEMFLAGS flag() const {
      return static_cast<MEMFLAGS>(type_flag[1]);
    }

    RegionData regiondata() const {
      return RegionData{sidx, flag()};
    }

    const NativeCallStackStorage::StackIndex stack() const {
     return sidx;
    }
  };

  // An IntervalChange indicates a change in state between two intervals. The incoming state
  // is denoted by in, and the outgoing state is denoted by out.
  struct IntervalChange {
    IntervalState in;
    IntervalState out;

    bool is_noop() {
      return in.type() == out.type() &&
             RegionData::equals(in.regiondata(), out.regiondata());
    }
  };

public:
  using VMATreap = TreapCHeap<position, IntervalChange, PositionComparator>;
  using TreapNode = VMATreap::TreapNode;

private:
  VMATreap _tree;

  // AddressState saves the necessary information for performing online summary accounting.
  struct AddressState {
    position address;
    IntervalChange state;

    const IntervalState& out() const {
      return state.out;
    }

    const IntervalState& in() const {
      return state.in;
    }
  };

public:
  VMATree() : _tree() {}

  struct SingleDiff {
    using delta = int64_t;
    delta reserve;
    delta commit;
  };
  struct SummaryDiff {
    SingleDiff flag[mt_number_of_types];
    SummaryDiff() {
      for (int i = 0; i < mt_number_of_types; i++) {
        flag[i] = SingleDiff{0, 0};
      }
    }
  };

  SummaryDiff register_mapping(position A, position B, StateType state, const RegionData& metadata);

  SummaryDiff reserve_mapping(position from, position sz, const RegionData& metadata) {
    return register_mapping(from, from + sz, StateType::Reserved, metadata);
  }

  SummaryDiff commit_mapping(position from, position sz, const RegionData& metadata) {
    return register_mapping(from, from + sz, StateType::Committed, metadata);
  }

  SummaryDiff release_mapping(position from, position sz) {
    return register_mapping(from, from + sz, StateType::Released, VMATree::empty_regiondata);
  }

public:
  template<typename F>
  void visit_in_order(F f) const {
    _tree.visit_in_order(f);
  }
};

#endif

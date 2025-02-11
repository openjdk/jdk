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

#include "nmt/memTag.hpp"
#include "nmt/nmtNativeCallStackStorage.hpp"
#include "nmt/nmtTreap.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"
#include <cstdint>

// A VMATree stores a sequence of points on the natural number line.
// Each of these points stores information about a state change.
// For example, the state may go from released memory to committed memory,
// or from committed memory of a certain MemTag to committed memory of a different MemTag.
// The set of points is stored in a balanced binary tree for efficient querying and updating.
class VMATree {
  friend class NMTVMATreeTest;
  // A position in memory.
public:
  using position = size_t;
  using size = size_t;

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

  // Each point has some stack and a tag associated with it.
  struct RegionData {
    const NativeCallStackStorage::StackIndex stack_idx;
    const MemTag mem_tag;

    RegionData() : stack_idx(), mem_tag(mtNone) {}

    RegionData(NativeCallStackStorage::StackIndex stack_idx, MemTag mem_tag)
    : stack_idx(stack_idx), mem_tag(mem_tag) {}

    static bool equals(const RegionData& a, const RegionData& b) {
      return a.mem_tag == b.mem_tag &&
             NativeCallStackStorage::equals(a.stack_idx, b.stack_idx);
    }
  };

  static const RegionData empty_regiondata;

private:
  struct IntervalState {
  private:
    // Store the type and mem_tag as two bytes
    uint8_t type_tag[2];
    NativeCallStackStorage::StackIndex sidx;

  public:
    IntervalState() : type_tag{0,0}, sidx() {}
    IntervalState(const StateType type, const RegionData data) {
      assert(!(type == StateType::Released) || data.mem_tag == mtNone, "Released type must have memory tag mtNone");
      type_tag[0] = static_cast<uint8_t>(type);
      type_tag[1] = static_cast<uint8_t>(data.mem_tag);
      sidx = data.stack_idx;
    }

    StateType type() const {
      return static_cast<StateType>(type_tag[0]);
    }

    MemTag mem_tag() const {
      return static_cast<MemTag>(type_tag[1]);
    }

    RegionData regiondata() const {
      return RegionData{sidx, mem_tag()};
    }

    void set_tag(MemTag tag) {
      type_tag[1] = static_cast<uint8_t>(tag);
    }

    NativeCallStackStorage::StackIndex stack() const {
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

  static IntervalState& in_state(TreapNode* node) {
    return node->val().in;
  }

  static IntervalState& out_state(TreapNode* node) {
    return node->val().out;
  }

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
    SingleDiff tag[mt_number_of_tags];
    SummaryDiff() {
      for (int i = 0; i < mt_number_of_tags; i++) {
        tag[i] = SingleDiff{0, 0};
      }
    }

    void add(SummaryDiff& other) {
      for (int i = 0; i < mt_number_of_tags; i++) {
        tag[i].reserve += other.tag[i].reserve;
        tag[i].commit += other.tag[i].commit;
      }
    }

#ifdef ASSERT
    void print_on(outputStream* out);
#endif
  };

 private:
  SummaryDiff register_mapping(position A, position B, StateType state, const RegionData& metadata, bool use_tag_inplace = false);

 public:
  SummaryDiff reserve_mapping(position from, size size, const RegionData& metadata) {
    return register_mapping(from, from + size, StateType::Reserved, metadata, false);
  }

  SummaryDiff commit_mapping(position from, size size, const RegionData& metadata, bool use_tag_inplace = false) {
    return register_mapping(from, from + size, StateType::Committed, metadata, use_tag_inplace);
  }

  // Given an interval and a tag, find all reserved and committed ranges at least
  // partially contained within that interval and set their tag to the one provided.
  // This may cause merging and splitting of ranges.
  // Released regions are ignored.
  SummaryDiff set_tag(position from, size size, MemTag tag);

  SummaryDiff uncommit_mapping(position from, size size, const RegionData& metadata) {
    return register_mapping(from, from + size, StateType::Reserved, metadata, true);
  }

  SummaryDiff release_mapping(position from, size size) {
    return register_mapping(from, from + size, StateType::Released, VMATree::empty_regiondata);
  }

  VMATreap& tree() {
    return _tree;
  }

public:
  template<typename F>
  void visit_in_order(F f) const {
    _tree.visit_in_order(f);
  }

#ifdef ASSERT
  void print_on(outputStream* out);
#endif

};

#endif

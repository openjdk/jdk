/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
  friend class VMTWithVMATreeTest;
  // A position in memory.
public:
  using position = size_t;
  using size = size_t;
  using SIndex = NativeCallStackStorage::StackIndex;

  class PositionComparator {
  public:
    static int cmp(position a, position b) {
      if (a < b) return -1;
      if (a == b) return 0;
      if (a > b) return 1;
      ShouldNotReachHere();
    }
  };

  // Bit fields view: bit 0 for Reserved, bit 1 for Committed.
  // Setting a region as Committed preserves the Reserved state.
  enum class StateType : uint8_t { Reserved = 1, Committed = 3, Released = 0, st_number_of_states = 4 };

private:
  static const char* statetype_strings[static_cast<uint8_t>(StateType::st_number_of_states)];

public:
  NONCOPYABLE(VMATree);

  static const char* statetype_to_string(StateType type) {
    assert(type < StateType::st_number_of_states, "must be");
    return statetype_strings[static_cast<uint8_t>(type)];
  }

  // Each point has some stack and a tag associated with it.
  struct RegionData {
    const SIndex stack_idx;
    const MemTag mem_tag;

    RegionData() : stack_idx(), mem_tag(mtNone) {}

    RegionData(SIndex stack_idx, MemTag mem_tag)
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
    NativeCallStackStorage::StackIndex _reserved_stack;
    NativeCallStackStorage::StackIndex _committed_stack;

  public:
    IntervalState() : type_tag{0,0}, _reserved_stack(NativeCallStackStorage::invalid), _committed_stack(NativeCallStackStorage::invalid) {}
    IntervalState(const StateType type,
                  const MemTag mt,
                  const NativeCallStackStorage::StackIndex res_stack,
                  const NativeCallStackStorage::StackIndex com_stack) {
      assert(!(type == StateType::Released) || mt == mtNone, "Released state-type must have memory tag mtNone");
      type_tag[0] = static_cast<uint8_t>(type);
      type_tag[1] = static_cast<uint8_t>(mt);
      _reserved_stack = res_stack;
      _committed_stack = com_stack;
    }
    IntervalState(const StateType type, const RegionData data) {
      assert(!(type == StateType::Released) || data.mem_tag == mtNone, "Released state-type must have memory tag mtNone");
      type_tag[0] = static_cast<uint8_t>(type);
      type_tag[1] = static_cast<uint8_t>(data.mem_tag);
      _reserved_stack = data.stack_idx;
      _committed_stack = NativeCallStackStorage::invalid;
    }

    StateType type() const {
      return static_cast<StateType>(type_tag[0]);
    }

    MemTag mem_tag() const {
      return static_cast<MemTag>(type_tag[1]);
    }

    RegionData reserved_regiondata() const {
      return RegionData{_reserved_stack, mem_tag()};
    }
    RegionData committed_regiondata() const {
      return RegionData{_committed_stack, mem_tag()};
    }

    void set_tag(MemTag tag) {
      type_tag[1] = static_cast<uint8_t>(tag);
    }

    NativeCallStackStorage::StackIndex reserved_stack() const {
      return _reserved_stack;
    }

    NativeCallStackStorage::StackIndex committed_stack() const {
      return _committed_stack;
    }

    void set_reserve_stack(NativeCallStackStorage::StackIndex idx) {
      _reserved_stack = idx;
    }

    void set_commit_stack(NativeCallStackStorage::StackIndex idx) {
      _committed_stack = idx;
    }

    bool has_reserved_stack() {
      return _reserved_stack != NativeCallStackStorage::invalid;
    }

    bool has_committed_stack() {
      return _committed_stack != NativeCallStackStorage::invalid;
    }

    void set_type(StateType t) {
      type_tag[0] = static_cast<uint8_t>(t);
    }

    bool equals(const IntervalState& other) const {
      return mem_tag()          == other.mem_tag()          &&
             type()             == other.type()             &&
             reserved_stack()   == other.reserved_stack()   &&
             committed_stack()  == other.committed_stack();
    }
  };

  // An IntervalChange indicates a change in state between two intervals. The incoming state
  // is denoted by in, and the outgoing state is denoted by out.
  struct IntervalChange {
    IntervalState in;
    IntervalState out;

    bool is_noop() {
      if (in.type() == StateType::Released &&
          in.type() == out.type() &&
          in.mem_tag() == out.mem_tag()) {
        return true;
      }
      return in.type() == out.type() &&
             RegionData::equals(in.reserved_regiondata(), out.reserved_regiondata()) &&
             RegionData::equals(in.committed_regiondata(), out.committed_regiondata());
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

  enum Operation {Release, Reserve, Commit, Uncommit};
  struct RequestInfo {
    position A, B;
    StateType _op;
    MemTag tag;
    SIndex callstack;
    bool use_tag_inplace;
    Operation op() const {
      return
            _op == StateType::Reserved && !use_tag_inplace  ? Operation::Reserve  :
            _op == StateType::Committed                     ? Operation::Commit   :
            _op == StateType::Reserved &&  use_tag_inplace  ? Operation::Uncommit :
             Operation::Release;
    }

    int op_to_index() const {
      return
            _op == StateType::Reserved && !use_tag_inplace  ? 1 :
            _op == StateType::Committed                     ? 2 :
            _op == StateType::Reserved &&  use_tag_inplace  ? 3 :
             0;
    }
  };

 private:
  SummaryDiff register_mapping(position A, position B, StateType state, const RegionData& metadata, bool use_tag_inplace = false);
  StateType get_new_state(const StateType existinting_state, const RequestInfo& req) const;
  MemTag get_new_tag(const MemTag existinting_tag, const RequestInfo& req) const;
  SIndex get_new_reserve_callstack(const SIndex existinting_stack, const StateType ex, const RequestInfo& req) const;
  SIndex get_new_commit_callstack(const SIndex existinting_stack, const StateType ex, const RequestInfo& req) const;
  void compute_summary_diff(const SingleDiff::delta region_size, const MemTag t1, const StateType& ex, const RequestInfo& req, const MemTag new_tag, SummaryDiff& diff) const;
  void update_region(TreapNode* n1, TreapNode* n2, const RequestInfo& req, SummaryDiff& diff);
  int state_to_index(const StateType st) const {
    return
      st == StateType::Released ? 0 :
      st == StateType::Reserved ? 1 :
      st == StateType::Committed ? 2 : -1;
  }

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

  SummaryDiff release_mapping(position from, position sz) {
    return register_mapping(from, from + sz, StateType::Released, VMATree::empty_regiondata);
  }

public:
  template<typename F>
  void visit_in_order(F f) const {
    _tree.visit_in_order(f);
  }

#ifdef ASSERT
  void print_on(outputStream* out);
#endif
  template<typename F>
  void visit_range_in_order(const position& from, const position& to, F f) {
    _tree.visit_range_in_order(from, to, f);
  }
  VMATreap& tree() { return _tree; }
};
#endif

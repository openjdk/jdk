/*
 * Copyright (c) 2024, Red Hat, Inc. and/or its affiliates.
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

#ifndef SHARE_COMPILER_COMPILATIONMEMSTATINTERNALS_HPP
#define SHARE_COMPILER_COMPILATIONMEMSTATINTERNALS_HPP

#include "compiler/compilationMemoryStatistic.hpp"
#include "compiler/compilerDefinitions.hpp"
#include "memory/allocation.hpp"
#include "memory/arena.hpp"
#ifdef COMPILER2
#include "opto/phase.hpp"
#endif
#include "utilities/globalDefinitions.hpp"

class outputStream;

// We prevent littering this code with enum casts and ifdef COMPILER2. We
// only (mostly) need the enums for bounds checking.
template <int max_val, int init_val>
class EnumWrapper {
  typedef EnumWrapper<max_val, init_val> Type;
  int _v;
public:
  static constexpr int max = max_val;
  EnumWrapper() : _v(init_val) {}
  EnumWrapper(int v) : _v(v) { assert(v >= 0 && v < max_val, "OOB (%d)", v); }
  EnumWrapper(const Type& e) : _v(e._v) {}
  int raw() const { return _v; }
  Type operator=(Type other) { _v = other._v; return *this; }
  bool operator==(const Type& other) const { return _v == other._v; }
  bool operator!=(const Type& other) const { return _v != other._v; }
};

#ifdef COMPILER2
typedef EnumWrapper<(int)Phase::PhaseTraceId::max_phase_timers, 0> PhaseTrcId;
#else
// In minimal builds, the ArenaCounterTable is just a single-dimension vector of arena tags (see below)
typedef EnumWrapper<1, 0> PhaseTraceId;
#endif

typedef EnumWrapper<(int)Arena::Tag::tag_count, 0> ArenaTag;

// A table containing counters per arena Tag and per compilation Phase
class ArenaCounterTable {
  size_t _v[PhaseTrcId::max][ArenaTag::max];
public:
  ArenaCounterTable();
  void copy_from(const ArenaCounterTable& other);
  inline size_t at(PhaseTrcId id, ArenaTag tag) const;
  inline void set(size_t size, PhaseTrcId id, ArenaTag tag);
  inline void add(size_t size, PhaseTrcId id, ArenaTag tag);
  inline void sub(size_t size, PhaseTrcId id, ArenaTag tag);
  void print_on(outputStream* ss) const;
  void summarize(size_t out[ArenaTag::max]) const;
};

// A stack keeping track of the current compilation phase. For simplicity,
// fixed-width, since the nesting depth of TracePhase is limited
class PhaseIdStack {
  static constexpr int max_depth = 32;
  int _depth;
  int _stack[max_depth];
public:
  PhaseIdStack();
  inline void push(PhaseTrcId id);
  inline void pop(PhaseTrcId id);
  inline PhaseTrcId top() const;
};

class FootprintTimeline {
  struct Entry {
    int phaseid;
    size_t cur, peak, start;
    bool did_footprint_change() const       { return cur != start; }
    bool has_significant_local_peak() const { return (peak - start) > M && (peak - cur) > M; }
  };
  static constexpr unsigned max_entries = 64; // we wrap, keeping the last n phase movements
  Entry _entries[max_entries];
  unsigned _pos;
  Entry& at(unsigned pos)             { return _entries[pos % max_entries]; }
  const Entry& at(unsigned pos) const { return _entries[pos % max_entries]; }
  void print_entry_on(outputStream* st, unsigned pos) const;
public:
  FootprintTimeline();
  void copy_from(const FootprintTimeline& other);
  inline void on_footprint_change(size_t cur_abs);
  void print_on(outputStream* st) const;
  void on_phase_start(PhaseTrcId phase, size_t cur_abs);
};

// ArenaState is the central data structure holding all statistics and temp data during
// a single compilation. It is created on demand (if memstat is active) and tied to the
// CompilerThread.
class ArenaState : public CHeapObj<mtCompiler> {

  // Bytes total now
  size_t _current;
  // Bytes total at last global peak
  size_t _peak;
  // Bytes per arena/phase, now
  ArenaCounterTable _counters_current;
  // Bytes per arena/phase when we last reached the global peak
  ArenaCounterTable _counters_at_global_peak;

  // Number of live nodes now (C2 only)
  unsigned _live_nodes_current;
  // Number of live nodes at global peak (C2 only)
  unsigned _live_nodes_at_global_peak;

  // MemLimit handling
  const size_t _limit;
  bool _hit_limit;
  bool _limit_in_process;

  // Keep track of current C2 phase
  PhaseIdStack _phase_id_stack;

  // Keep track of C2 phase allocations over time
  FootprintTimeline _timeline;

  const CompilerType _comp_type;
  const int _comp_id;

  int retrieve_live_node_count() const;

  DEBUG_ONLY(void verify() const;)

public:
  ArenaState(CompilerType comp_type, int comp_id, size_t limit);

  void on_phase_start(PhaseTrcId id);
  void on_phase_end(PhaseTrcId id);

  // Account an arena allocation. Returns true if new peak reached.
  bool on_arena_chunk_allocation(size_t size, int tag, uint64_t* stamp);

  // Account an arena deallocation.
  void on_arena_chunk_deallocation(size_t size, uint64_t stamp);

  void print_peak_state_on(outputStream* st) const;

  size_t limit() const              { return _limit; }
  bool   hit_limit() const          { return _hit_limit; }
  bool   limit_in_process() const     { return _limit_in_process; }
  void   set_limit_in_process(bool v) { _limit_in_process = v; }

  CompilerType comp_type() const { return _comp_type; }
  int comp_id() const { return _comp_id; }

  // Bytes total at last global peak
  size_t peak() const { return _peak; }
  // Bytes per arena/phase when we last reached the global peak
  const ArenaCounterTable& counters_at_global_peak() const { return _counters_at_global_peak; }
  const FootprintTimeline& timeline() const                { return _timeline; }
  // Number of live nodes at global peak (C2 only)
  unsigned live_nodes_at_global_peak() const { return _live_nodes_at_global_peak; }
};

#endif // SHARE_COMPILER_COMPILATIONMEMSTATINTERNALS_HPP

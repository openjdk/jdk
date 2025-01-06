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

#ifdef COMPILER2
constexpr int phase_trc_id_max     = (int)Phase::PhaseTraceId::max_phase_timers;
constexpr int phase_trc_id_default = (int)Phase::PhaseTraceId::_t_none;
#else
// In minimal builds, the ArenaCounterTable is just a single-dimension vector of arena tags (see below)
constexpr int phase_trc_id_max = 1;
constexpr int phase_trc_id_default = 0;
#endif
inline void check_phase_trace_id(int v) { assert(v >= 0 && v < phase_trc_id_max, "OOB (%d)", v); }

constexpr int arena_tag_max = (int)Arena::Tag::tag_count;
inline void check_arena_tag(int v) { assert(v >= 0 && v < arena_tag_max, "OOB (%d)", v); }

// A table containing counters per arena Tag and per compilation Phase
class ArenaCounterTable {
  size_t _v[phase_trc_id_max][arena_tag_max];
public:
  ArenaCounterTable();
  void copy_from(const ArenaCounterTable& other);
  inline size_t at(int phase_trc_id, int arena_tag) const;
  inline void add(size_t size, int phase_trc_id, int arena_tag);
  inline void sub(size_t size, int phase_trc_id, int arena_tag);
  void print_on(outputStream* ss) const;
  void summarize(size_t out[arena_tag_max]) const;
};

// A stack keeping track of the current compilation phase. For simplicity,
// fixed-width, since the nesting depth of TracePhase is limited
class PhaseIdStack {
  static constexpr int max_depth = 16; // we rarely go beyond 3 layers of nesting
  int _depth;
  int _stack[max_depth];
public:
  PhaseIdStack();
  inline bool empty() const { return _depth == 0; }
  inline void push(int phase_trc_id);
  inline void pop(int phase_trc_id);
  inline int top() const;
};

template <typename T, int max>
class SimpleFifo {
  STATIC_ASSERT((max * 2) < INT_MAX);
  T _v[max];
  int _pos;
  uint64_t _lost;

  // [first_pos, current_pos)
  int first_pos() const   { return MAX2(0, _pos - max); }
  int current_pos() const { return _pos; }
  static int pos_to_index(int pos) { return pos % max; }
  T* slot_at(int pos)             { return _v + pos_to_index(pos); }
  const T* slot_at(int pos) const { return _v + pos_to_index(pos); }

public:

  SimpleFifo() : _pos(0), _lost(0UL) {}

  const T* raw() const            { return _v; }
  T& at(int pos)                  { return *slot_at(pos); }
  const T& at(int pos) const      { return *slot_at(pos); }
  T& current()                    { return at(current_pos()); }
  const T& current() const        { return at(current_pos()); }

  bool empty() const { return _pos == 0; }
  bool wrapped() const { return _pos >= max; }
  int size() const { return MIN2(max, _pos); }
  uint64_t lost() const { return _lost; }

  void advance() {
    _pos ++;
    if (_pos == max * 2) {
      _pos -= max;
    }
    if (_pos >= max) {
      _lost ++;
    }
  }

  template<typename F>
  void iterate_all(F f) const {
    const int start = first_pos();
    const int end = current_pos();
    for (int pos = start; pos < end; pos++) {
      const int index = pos_to_index(pos);
      f(_v[index]);
    }
  }

  void copy_from(const SimpleFifo& other) {
    memcpy(_v, other._v, sizeof(_v));
    _pos = other._pos;
    _lost = other._lost;
  }
};

// Holds a table of n entries; each entry keeping start->end footprints when
// a phase started and ended; each entry also keeping the phase-local peak (if
// a phase caused a temporary spike in footprint that vanished before the phase
// ended).
// Handling nested phases: for this structure, there is always a phase active;
// if a phase ends, we "restart" the parent phase (which often is the
// "outside any phase" phase).
class FootprintTimeline {
public:
  static constexpr unsigned max_num_phases = 64; // beyond that we wrap, keeping the last n phases
private:
  template <typename T, typename dT>
  struct C {
    T start, peak, cur;
    void init(T v)        { start = cur = peak = v; }
    void update(T v)      { cur = v; if (v > peak) peak = v; }
    dT end_delta() const  { return (dT)cur - (dT)start; }
    size_t peak_size() const {
      return MIN2(peak - cur, peak - start);
    }
  };
  struct Entry {
    int phase_trc_id;
    C<size_t, ssize_t> _bytes;
    C<unsigned, ssize_t> _live_nodes;
  };
  SimpleFifo<Entry, max_num_phases> _fifo;
public:
  FootprintTimeline();
  void copy_from(const FootprintTimeline& other);
  inline void on_footprint_change(size_t cur_abs, unsigned cur_nodes);
  void print_on(outputStream* st) const;
  void on_phase_start(int phase_trc_id, size_t cur_abs, unsigned cur_nodes);
};

// ArenaState is the central data structure holding all statistics and temp data during
// a single compilation. It is created on demand (if memstat is active) and tied to the
// CompilerThread.
class ArenaStatCounter : public CHeapObj<mtCompiler> {

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
  ArenaStatCounter(CompilerType comp_type, int comp_id, size_t limit);

  void on_phase_start(int phase_trc_id);
  void on_phase_end(int phase_trc_id);

  // Account an arena allocation. Returns true if new peak reached.
  bool on_arena_chunk_allocation(size_t size, int arena_tag, uint64_t* stamp);

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
  size_t current() const { return _current; }
  // Bytes total at last global peak
  size_t peak() const { return _peak; }

  // Bytes per arena/phase when we last reached the global peak
  const ArenaCounterTable& counters_at_global_peak() const { return _counters_at_global_peak; }
  const FootprintTimeline& timeline() const                { return _timeline; }
  // Number of live nodes at global peak (C2 only)
  unsigned live_nodes_at_global_peak() const { return _live_nodes_at_global_peak; }
};

#endif // SHARE_COMPILER_COMPILATIONMEMSTATINTERNALS_HPP

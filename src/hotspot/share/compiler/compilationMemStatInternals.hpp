/*
 * Copyright (c) 2024, 2025, Red Hat, Inc. and/or its affiliates.
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
 *
 */

#ifndef SHARE_COMPILER_COMPILATIONMEMSTATINTERNALS_HPP
#define SHARE_COMPILER_COMPILATIONMEMSTATINTERNALS_HPP

#include "compiler/compilationMemoryStatistic.hpp"
#include "compiler/compilerDefinitions.hpp"
#include "memory/allocation.hpp"
#include "memory/arena.hpp"
#include "utilities/globalDefinitions.hpp"

#ifdef COMPILER2
#include "opto/phase.hpp"
#endif

class CompileTask;
class Method;
class Symbol;
class outputStream;

#ifdef COMPILER2
constexpr int phase_trc_id_max     = (int)Phase::PhaseTraceId::max_phase_timers;
constexpr int phase_trc_id_none    = (int)Phase::PhaseTraceId::_t_none;
#else
// In minimal builds, the ArenaCounterTable is just a single-dimension vector of arena tags (see below)
constexpr int phase_trc_id_max = 1;
constexpr int phase_trc_id_none = 0;
#endif
inline void check_phase_trace_id(int v) { assert(v >= 0 && v < phase_trc_id_max, "OOB (%d)", v); }

constexpr int arena_tag_max = (int)Arena::Tag::tag_count;
inline void check_arena_tag(int v) { assert(v >= 0 && v < arena_tag_max, "OOB (%d)", v); }

// A two-dimensional table, containing byte counters per arena type and
// per compilation phase.
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

struct PhaseInfo {
  int id, num;
  const char* text;
};

// A stack keeping track of the current compilation phase. Fixed-width for simplicity
// (we should never go beyond 5 or so in depth).
class PhaseInfoStack {
  static constexpr int max_depth = 16;
  int _depth;
  PhaseInfo _stack[max_depth];
public:
  inline PhaseInfoStack();
  inline bool empty() const { return _depth == 0; }
  inline void push(PhaseInfo info);
  inline void pop();
  inline const PhaseInfo& top() const;
  inline int depth() const  { return _depth; }
};

// A very simple fixed-width FIFO buffer, used for the phase timeline
template <typename T, int size>
class SimpleFifo {
  STATIC_ASSERT((size * 2) < INT_MAX);
  T _v[size];
  int _pos;
  int _oldest;
  uint64_t _lost;

  int current_pos() const           { return _pos; }
  static int pos_to_index(int pos)  { return pos % size; }
  T& at(int pos)                    { return *(_v + pos_to_index(pos)); }

public:
  SimpleFifo() : _pos(0), _oldest(0), _lost(0UL) {}
  T& current()                      { return at(current_pos()); }
  T& last()                         { assert(!empty(), "sanity"); return at(current_pos() - 1); }
  bool empty() const                { return _pos == _oldest; }
  uint64_t lost() const             { return _lost; }

  void advance() {
    _pos ++;
    if (_pos >= size) {
      _oldest ++;
      _lost ++;
    }
    if (_pos == INT_MAX) {
      _pos -= size;
      _oldest -= size;
    }
  }

  void revert() {
    assert(!empty(), "sanity");
    _pos--;
  }

  template<typename F>
  void iterate_all(F f) const {
    for (int i = _oldest; i < _pos; i++) {
      const int index = pos_to_index(i);
      f(_v[index]);
    }
  }

  void copy_from(const SimpleFifo& other) {
    memcpy(_v, other._v, sizeof(_v));
    _pos = other._pos;
    _lost = other._lost;
    _oldest = other._oldest;
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
  static constexpr unsigned max_num_phases = 256; // beyond that we wrap, keeping just the last n phases
private:
  template <typename T, typename dT>
  struct C {
    T start, peak, cur;
    void init(T v)        { start = cur = peak = v; }
    void update(T v)      { cur = v; if (v > peak) peak = v; }
    dT end_delta() const  { return (dT)cur - (dT)start; }
    // Returns the peak size during this phase: how high usage rose above either
    // start or end of phase. The background is that we want to know the max. memory
    // consumption during this phase, but that may not be reflected by the start or the
    // end counters if an Arena was created during the phase and only lived temporarily.
    size_t temporary_peak_size() const { return MIN2(peak - cur, peak - start); }
  };
  struct Entry {
    PhaseInfo info;
    int level;
    C<size_t, ssize_t> _bytes;
    C<unsigned, signed int> _live_nodes;
  };
  SimpleFifo<Entry, max_num_phases> _fifo;
  DEBUG_ONLY(bool _inbetween_phases;)
public:
  FootprintTimeline();
  void copy_from(const FootprintTimeline& other);
  inline void on_footprint_change(size_t cur_abs, unsigned cur_nodes);
  void on_phase_end(size_t cur_abs, unsigned cur_nodes);
  void on_phase_start(PhaseInfo info, size_t cur_abs, unsigned cur_nodes, int level);
  void print_on(outputStream* st) const;
};

// We keep the name of the involved symbols in Symbol (made permanent) instead of resolving them to string and
// storing those. That significantly reduces footprint for the result store and delays resolving until printing
// time, which may be never.
class FullMethodName {
  Symbol* _k;
  Symbol* _m;
  Symbol* _s;
public:
  FullMethodName();
  FullMethodName(const Method* m);
  FullMethodName(const FullMethodName& o);
  FullMethodName& operator=(const FullMethodName& o);
  void make_permanent();
  void print_on(outputStream* st) const;
  char* as_C_string(char* buf, size_t len) const;
  bool operator== (const FullMethodName& b) const;
  DEBUG_ONLY(bool is_test_class() const;)
};

// ArenaState is the central data structure holding all statistics and temp data during
// a single compilation. It is created on demand (if memstat is active) and tied to the
// CompilerThread.
class ArenaStatCounter : public CHeapObj<mtCompiler> {

  FullMethodName _fmn;

  // from directives
  const bool _should_print_memstat;
  const bool _should_crash_on_memlimit;

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
  int _phase_counter;
  PhaseInfoStack _phase_info_stack;

  // Keep track of C2 phase allocations over time
  FootprintTimeline _timeline;

  const CompilerType _comp_type;
  const int _comp_id;

  DEBUG_ONLY(bool _is_test_class;)

  int retrieve_live_node_count() const;

public:
  ArenaStatCounter(const CompileTask* task, size_t limit);

  void on_phase_start(PhaseInfo info);
  void on_phase_end();

  // Account an arena allocation. Returns true if new peak reached.
  bool on_arena_chunk_allocation(size_t size, int arena_tag, uint64_t* stamp);

  // Account an arena deallocation.
  void on_arena_chunk_deallocation(size_t size, uint64_t stamp);

  void print_peak_state_on(outputStream* st) const;
  void print_error_state_on(outputStream* st) const;

  size_t limit() const                  { return _limit; }
  bool   hit_limit() const              { return _hit_limit; }
  bool   limit_in_process() const       { return _limit_in_process; }
  void   set_limit_in_process(bool v)   { _limit_in_process = v; }

  const FullMethodName& fmn() const     { return _fmn; }
  bool should_print_memstat()           { return _should_print_memstat; };
  bool should_crash_on_memlimit() const { return _should_crash_on_memlimit; };

  CompilerType comp_type() const        { return _comp_type; }
  int comp_id() const                   { return _comp_id; }
  DEBUG_ONLY(bool is_test_class() const { return _is_test_class; })

  // Bytes total at last global peak
  size_t peak() const                   { return _peak; }

  // Bytes per arena/phase when we last reached the global peak
  const ArenaCounterTable& counters_at_global_peak() const { return _counters_at_global_peak; }
  const FootprintTimeline& timeline() const                { return _timeline; }
  // Number of live nodes at global peak (C2 only)
  unsigned live_nodes_at_global_peak() const { return _live_nodes_at_global_peak; }

  int advance_phase_counter() { return ++_phase_counter; }
};

#endif // SHARE_COMPILER_COMPILATIONMEMSTATINTERNALS_HPP

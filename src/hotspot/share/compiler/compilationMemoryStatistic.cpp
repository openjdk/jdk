/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2024, Red Hat, Inc. and/or its affiliates.
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

#include "logging/log.hpp"
#include "logging/logStream.hpp"
#ifdef COMPILER1
#include "c1/c1_Compilation.hpp"
#endif
#include "code/nmethod.hpp"
#include "compiler/abstractCompiler.hpp"
#include "compiler/compilationMemStatInternals.inline.hpp"
#include "compiler/compilerDefinitions.inline.hpp"
#include "compiler/compilerDirectives.hpp"
#include "compiler/compilerOracle.hpp"
#include "compiler/compileTask.hpp"
#include "compiler/compilerDefinitions.hpp"
#include "compiler/compilerThread.hpp"
#include "memory/arena.hpp"
#include "memory/resourceArea.hpp"
#include "nmt/nmtCommon.hpp"
#include "oops/symbol.hpp"
#ifdef COMPILER2
#include "opto/node.hpp" // compile.hpp is not self-contained
#include "opto/compile.hpp"
#endif
#include "runtime/mutexLocker.hpp"
#include "runtime/os.hpp"
#include "utilities/checkedCast.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"
#include "utilities/quickSort.hpp"
#include "utilities/resourceHash.hpp"


static const char* phase_trc_id_to_string(int phase_trc_id) {
  return COMPILER2_PRESENT(Phase::get_phase_trace_id_text((Phase::PhaseTraceId)phase_trc_id))
         NOT_COMPILER2("");
}

// Arena-chunk stamping
union chunkstamp_t {
  uint64_t raw;
  struct {
    uint32_t tracked;
    uint16_t arena_tag;
    uint16_t phase_id;
  };
};
STATIC_ASSERT(sizeof(chunkstamp_t) == sizeof(chunkstamp_t::raw));

ArenaCounterTable::ArenaCounterTable() {
  memset(_v, 0, sizeof(_v));
}

void ArenaCounterTable::copy_from(const ArenaCounterTable& other) {
  memcpy(_v, other._v, sizeof(_v));
}

void ArenaCounterTable::summarize(size_t out[arena_tag_max]) const {
  memset(out, 0, arena_tag_max * sizeof(size_t));
  for (int i = 0; i < phase_trc_id_max; i++) {
    for (int j = 0; j < arena_tag_max; j++) {
      out[j] += _v[i][j];
    }
  }
}

void ArenaCounterTable::print_on(outputStream* st) const {
  bool header_printed = false;
  for (int phase_trc_id = 0; phase_trc_id < phase_trc_id_max; phase_trc_id++) {
    size_t sum = 0;
    for (int arena_tag = 0; arena_tag < arena_tag_max; arena_tag++) {
      sum += at(phase_trc_id, arena_tag);
    }
    if (sum > 0) { // omit phases that did not contribute to allocation load
      if (!header_printed) {
        st->print("%24s %10s", "phase name", "total");
        for (int arena_tag = 0; arena_tag < arena_tag_max; arena_tag++) {
          st->print("%10s", Arena::tag_name[arena_tag]);
        }
        st->cr();
        header_printed = true;
      }
      st->print("%24s ", phase_trc_id_to_string(phase_trc_id));
      st->print(SIZE_FORMAT_W(10), sum);
      for (int arena_tag = 0; arena_tag < arena_tag_max; arena_tag++) {
        const size_t v = at(phase_trc_id, arena_tag);
        st->print(SIZE_FORMAT_W(10), v);
      }
      st->cr();
    }
  }
}

PhaseIdStack::PhaseIdStack() : _depth(0) {
  push(phase_trc_id_default); // "outside phase"
}

// When reporting phase footprint movements, if phase-local peak over start as well over end
// was larger than this threshold, we report it.
static constexpr size_t significant_peak_threshold = M;

FootprintTimeline::FootprintTimeline() {
  Entry& e = _fifo.at(0);
  e._bytes.init(0);
  e._live_nodes.init(0);
  e.phase_trc_id = phase_trc_id_default; // "outside phase"
}

void FootprintTimeline::copy_from(const FootprintTimeline& other) {
  _fifo.copy_from(other._fifo);
}

void FootprintTimeline::print_on(outputStream* st) const {
  if (!_fifo.empty()) {
               // .123456789.123456789.123456789.123456789.123456789.123456789.123456789.123456789.123456789.123456789
    st->print_cr("                                      bytes               #nodes");
    unsigned from = 0;
    if (_fifo.lost() > 0) {
      st->print_cr("         (" UINT64_FORMAT " older entries lost)", _fifo.lost());
    }
    auto printer = [&](const Entry& e) {
      check_phase_trace_id(e.phase_trc_id);
      st->print("%24s: ", phase_trc_id_to_string(e.phase_trc_id));
      st->fill_to(30);
      st->print("%9zu ", e._bytes.cur); // end
      st->fill_to(40);
      st->print("%+9zd ", e._bytes.end_delta()); // delta end
      st->fill_to(55);
      st->print("%6u ", e._live_nodes.cur); // end
      st->fill_to(62);
      st->print("%+6zd ", e._live_nodes.end_delta()); // end
      if (e._bytes.peak_size() > significant_peak_threshold) {
        st->fill_to(80);
        st->print(" significant temporary peak: %zu (%+zd)", e._bytes.peak, (ssize_t)e._bytes.peak - e._bytes.start); // peak
      }
      st->cr();
    };
    _fifo.iterate_all(printer);
  }
}

void FootprintTimeline::on_phase_start(int phase_trc_id, size_t cur_abs, unsigned cur_nodes) {
  // Since we see all allocations, cur_abs should correspond to our topmost cur. But the
  // the same does not hold for nodes, since we only see allocations that cause new arena chunks
  // to be born. One can allocate a node without causing the arena to expand. So, cur_nodes
  // may be a new number for us. Just act as if this is a footprint change.
  on_footprint_change(cur_abs, cur_nodes);

  // Close old, open new entry; but only if the last phase was "interesting".
  // An "interesting" phase is one that causes a footprint change (end != start),
  // and/or one that has a local peak that significantly raises above either starting
  // or ending footprint.
  const Entry& old = _fifo.current();
  if (old._bytes.end_delta() != 0 || old._bytes.peak_size() > significant_peak_threshold) {
    _fifo.advance();
  }
  Entry& e = _fifo.current();
  e._bytes.start = e._bytes.cur = e._bytes.peak = cur_abs;
  e._live_nodes.start = e._live_nodes.cur = e._live_nodes.peak = cur_nodes;
  e.phase_trc_id = phase_trc_id;
}

ArenaState::ArenaState(CompilerType comp_type, int comp_id, size_t limit) :
    _current(0), _peak(0), _live_nodes_current(0), _live_nodes_at_global_peak(0),
    _limit(limit), _hit_limit(false), _limit_in_process(false),
    _comp_type(comp_type), _comp_id(comp_id)
{}

void ArenaState::on_phase_start(int phase_trc_id) {
  _phase_id_stack.push(phase_trc_id);
  _live_nodes_current = retrieve_live_node_count();
  _timeline.on_phase_start(phase_trc_id, _current, _live_nodes_current);
}

void ArenaState::on_phase_end(int phase_trc_id) {
  _phase_id_stack.pop(phase_trc_id);
  _live_nodes_current = retrieve_live_node_count();
  _timeline.on_phase_start(_phase_id_stack.top(), _current, _live_nodes_current); // parent phase "restarts"
}

int ArenaState::retrieve_live_node_count() const {
  int result = 0;
#ifdef COMPILER2
  if (_comp_type == compiler_c2) {
    // Update C2 node count
    // Careful, Compile::current() may be NULL in a short time window when Compile itself
    // is still being constructed.
    if (Compile::current() != nullptr) {
      result = Compile::current()->live_nodes();
    }
  }
#endif // COMPILER2
  return result;
}

// Account an arena allocation. Returns true if new peak reached.
bool ArenaState::on_arena_chunk_allocation(size_t size, int arena_tag, uint64_t* stamp) {
  bool rc = false;

  const size_t old_current = _current;
  _current += size;
  assert(_current >= old_current, "Overflow");

  const int phase_trc_id = _phase_id_stack.top();
  _counters_current.add(size, phase_trc_id, arena_tag);
  _live_nodes_current = retrieve_live_node_count();

  _timeline.on_footprint_change(_current, _live_nodes_current);

  // Did we reach a global peak?
  if (_current > _peak) {
    _peak = _current;
    // snapshot all current counters
    _counters_at_global_peak.copy_from(_counters_current);
    // snapshot live nodes
    _live_nodes_at_global_peak = _live_nodes_current;
    // Did we hit the memory limit?
    if (!_hit_limit && _limit > 0 && _peak > _limit) {
      _hit_limit = true;
    }
    // report peak back
    rc = true;
  }

  // calculate arena chunk stamp
  chunkstamp_t cs;
  cs.tracked = 1;
  cs.arena_tag = checked_cast<uint16_t>(arena_tag);
  cs.phase_id = checked_cast<uint16_t>(_phase_id_stack.top());
  *stamp = cs.raw;

  return rc;
}

void ArenaState::on_arena_chunk_deallocation(size_t size, uint64_t stamp) {
  assert(_current >= size, "Underflow (%zu %zu)", size, _current);

  // Extract tag and phase id from stamp
  chunkstamp_t cs;
  cs.raw = stamp;
  assert(cs.tracked == 1, "Sanity");
  const int arena_tag = cs.arena_tag;
  assert(arena_tag >= 0 && arena_tag < arena_tag_max, "Arena Tag OOB (%d)", arena_tag_max);
  const int phase_trc_id(cs.phase_id);
  assert(phase_trc_id >= 0 && phase_trc_id < phase_trc_id_max, "Phase trace id OOB (%d)", phase_trc_id);

  _current -= size;
  _counters_current.sub(size, phase_trc_id, arena_tag);
  _live_nodes_current = retrieve_live_node_count();
  _timeline.on_footprint_change(_current, _live_nodes_current);
}

// Used for logging, not for the report table generated with jcmd Compiler.memory
void ArenaState::print_peak_state_on(outputStream* st) const {
  st->print("%zu ", _peak);
  if (_peak > 0) {
    st->print("[");
    size_t sums[arena_tag_max];
    _counters_at_global_peak.summarize(sums);
    bool print_comma = false;
    for (int i = 0; i < arena_tag_max; i++) {
      if (sums[i] > 0) {
        if (print_comma) {
          st->print_raw(", ");
        }
        st->print("%s %zu", Arena::tag_name[i], sums[i]);
        print_comma = true;
      }
    }
    st->print_cr("]");
#ifdef COMPILER2
    if (_comp_type == CompilerType::compiler_c2) {
      st->print_cr("----- Peak composition, accumulated by phase and arena type ------");
      _counters_at_global_peak.print_on(st);
      st->print_cr("------Allocation timelime by phase, last %u phases ---------------", FootprintTimeline::max_num_phases);
      _timeline.print_on(st);
      st->print_cr("------------------------------------------------------------------");
    }
#endif
  } else {
    st->cr();
  }
}

#ifdef ASSERT
void ArenaState::verify() const {
  assert(_current <= _peak, "Sanity");
#ifdef COMPILER2
  size_t sum = 0;
  for (int phaseid = 0; phaseid < phase_trc_id_max; phaseid++) {
    for (int arena_tag = 0; arena_tag < arena_tag_max; arena_tag ++) {
      sum += _counters_at_global_peak.at(phaseid, arena_tag);
    }
  }
  assert(sum == _peak, "per phase counter mismatch - %zu, expected %zu", sum, _peak);
#endif
}
#endif // ASSERT

class FullMethodName {
  Symbol* const _k;
  Symbol* const _m;
  Symbol* const _s;

public:

  FullMethodName(const Method* m) :
    _k(m->klass_name()), _m(m->name()), _s(m->signature()) {};
  FullMethodName(const FullMethodName& o) : _k(o._k), _m(o._m), _s(o._s) {}

  void make_permanent() {
    _k->make_permanent();
    _m->make_permanent();
    _s->make_permanent();
  }

  static unsigned compute_hash(const FullMethodName& n) {
    return Symbol::compute_hash(n._k) ^
        Symbol::compute_hash(n._m) ^
        Symbol::compute_hash(n._s);
  }

  void print_on(outputStream* st) const {
    ResourceMark rm;
    st->print_raw(_k->as_C_string());
    st->print_raw("::");
    st->print_raw(_m->as_C_string());
    st->put('(');
    st->print_raw(_s->as_C_string());
    st->put(')');
  }

  char* as_C_string(char* buf, size_t len) const {
    stringStream ss(buf, len);
    print_on(&ss);
    return buf;
  }
  bool operator== (const FullMethodName& b) const {
    return _k == b._k && _m == b._m && _s == b._s;
  }
};

class MemStatEntry : public CHeapObj<mtCompiler> {

  const FullMethodName _method;
  CompilerType _comp_type;
  int _comp_id;
  double _time;
  // How often this has been recompiled.
  int _num_recomp;
  // Compiling thread. Only for diagnostic purposes. Thread may not be alive anymore.
  const Thread* _thread;
  // active limit for this compilation, if any
  size_t _limit;

  const char* _result;
  size_t _code_size;

  // Bytes total at global peak
  size_t _peak;
  // Bytes per arena tag.
  size_t _peak_composition_per_arena_tag[arena_tag_max];
  // Number of live nodes at global peak (C2 only)
  unsigned _live_nodes_at_global_peak;

  struct Details {
    ArenaCounterTable counters_at_global_peak;
    FootprintTimeline timeline;
  };

  Details* _detail_stats;

  MemStatEntry(const MemStatEntry& e); // deny

public:

  MemStatEntry(FullMethodName method)
    : _method(method), _comp_type(compiler_none), _comp_id(-1),
      _time(0), _num_recomp(0), _thread(nullptr), _limit(0),
      _result(nullptr), _code_size(0), _peak(0), _live_nodes_at_global_peak(0),
      _detail_stats(nullptr) {
  }

  ~MemStatEntry() {
    clean_details();
  }

  void set_comp_id(int comp_id) { _comp_id = comp_id; }
  void set_comptype(CompilerType comptype) { _comp_type = comptype; }
  void set_current_time() { _time = os::elapsedTime(); }
  void set_current_thread() { _thread = Thread::current(); }
  void set_limit(size_t limit) { _limit = limit; }
  void inc_recompilation() { _num_recomp++; }

  void set_from_state(const ArenaState* state, bool store_details) {
    _comp_type = state->comp_type();
    _comp_id = state->comp_id();
    _limit = state->limit();
    _peak = state->peak();
    _live_nodes_at_global_peak = state->live_nodes_at_global_peak();
    state->counters_at_global_peak().summarize(_peak_composition_per_arena_tag);
#ifdef COMPILER2
    assert(_detail_stats == nullptr, "should have been cleaned");
    if (store_details) {
      _detail_stats = NEW_C_HEAP_OBJ(Details, mtCompiler);
      _detail_stats->counters_at_global_peak.copy_from(state->counters_at_global_peak());
      _detail_stats->timeline.copy_from(state->timeline());
    }
#endif // COMPILER2
  }

  void clean_details() {
    if (_detail_stats != nullptr) {
      FREE_C_HEAP_ARRAY(Details, _detail_stats);
      _detail_stats = nullptr;
    }
  }

  void reset() {
    clean_details();
    _comp_type = CompilerType::compiler_none;
    _comp_id = -1;
    _limit = _peak = 0;
    _live_nodes_at_global_peak = 0;
    memset(_peak_composition_per_arena_tag, 0, sizeof(_peak_composition_per_arena_tag));
  }

  void set_result(const char* s) { _result = s; }
  void set_code_size(size_t s)   { _code_size = s; }

  size_t peak() const { return _peak; }

  static void print_legend(outputStream* st) {
#define LEGEND_KEY_FMT "%11s"
    st->print_cr("Legend:");
    st->print_cr("  " LEGEND_KEY_FMT ": %s", "ctype", "compiler type");
    st->print_cr("  " LEGEND_KEY_FMT ": %s", "total", "peak memory allocated via arenas while compiling");
    for (int tag = 0; tag < arena_tag_max; tag++) {
      st->print_cr("  " LEGEND_KEY_FMT ": %s", Arena::tag_name[tag], Arena::tag_desc[tag]);
    }
    st->print_cr("  " LEGEND_KEY_FMT ": %s", "#nodes", "...how many nodes (c2 only)");
    st->print_cr("  " LEGEND_KEY_FMT ": %s", "codesz", "Compiled method code size");
    st->print_cr("  " LEGEND_KEY_FMT ": %s", "result", "Result: 'ok' finished successfully, 'oom' hit memory limit, 'err' compilation failed");
    st->print_cr("  " LEGEND_KEY_FMT ": %s", "limit", "memory limit, if set");
    st->print_cr("  " LEGEND_KEY_FMT ": %s", "time", "timestamp");
    st->print_cr("  " LEGEND_KEY_FMT ": %s", "id", "compile id");
    st->print_cr("  " LEGEND_KEY_FMT ": %s", "rec", "how often recompiled");
    st->print_cr("  " LEGEND_KEY_FMT ": %s", "thread", "compiler thread");
#undef LEGEND_KEY_FMT
  }

  static void print_header(outputStream* st) {
    st->print("%-6s", "ctyp");

#define SIZE_FMT "%-10s"
    st->print(SIZE_FMT, "total");
    for (int tag = 0; tag < arena_tag_max; tag++) {
      st->print(SIZE_FMT, Arena::tag_name[tag]);
    }
#define HDR_FMT1 "%-8s%-8s%-8s%-8s%-8s"
#define HDR_FMT2 "%-6s%-4s%-19s%s"

    st->print(HDR_FMT1, "#nodes", "codesz", "result", "limit", "time");
    st->print(HDR_FMT2, "id", "#rc", "thread", "method");
    st->print_cr("");
  }

  void print_brief_oneline(outputStream* st) const {
    int col = 0;

    // Type
    st->print("%2s ", compilertype2name(_comp_type));
    col += 6; st->fill_to(col);

    // Total
    size_t v = _peak;
    st->print("%zu ", v);
    col += 10; st->fill_to(col);

    for (int tag = 0; tag < arena_tag_max; tag++) {
      v = _peak_composition_per_arena_tag[tag];
      st->print("%zu ", v);
      col += 10; st->fill_to(col);
    }

    // Number of Nodes when memory peaked
    if (_live_nodes_at_global_peak > 0) {
      st->print("%u ", _live_nodes_at_global_peak);
    } else {
      st->print("-");
    }
    col += 8; st->fill_to(col);

    // code size
    st->print("%zu ", _code_size);
    col += 8; st->fill_to(col);

    // result?
    st->print("%s ", _result ? _result : "");
    col += 8; st->fill_to(col);

    // Limit
    if (_limit > 0) {
      st->print(PROPERFMT " ", PROPERFMTARGS(_limit));
    } else {
      st->print("-");
    }
    col += 8; st->fill_to(col);

    // TimeStamp
    st->print("%.3f ", _time);
    col += 8; st->fill_to(col);

    // Compile ID
    st->print("%d ", _comp_id);
    col += 6; st->fill_to(col);

    // Recomp
    st->print("%u ", _num_recomp);
    col += 4; st->fill_to(col);

    // Thread
    st->print(PTR_FORMAT " ", p2i(_thread));

    // MethodName
    char buf[1024];
    st->print("%s ", _method.as_C_string(buf, sizeof(buf)));

    st->cr();
  }

  void print_detailed(outputStream* st) const {
    int col = 0;

    constexpr int indent1 = 40;
    constexpr int indent2 = 50;

    char buf[1024];
    st->print_cr("%*s: %s", indent1, "Method", _method.as_C_string(buf, sizeof(buf)));
    st->print_cr("%*s: %2s", indent1, "Compiler Type", compilertype2name(_comp_type));
    st->print_cr("%*s: %d", indent1, "Compile ID", _comp_id);
    st->print_cr("%*s: %u", indent1, "Number of recompilations", _num_recomp);
    st->print_cr("%*s: %s", indent1, "Result", _result);
    st->print("%*s: ", indent1, "MemLimit");
    if (_limit > 0) {
      st->print_cr("%zu", _limit);
    } else {
      st->print_cr("-");
    }
    st->print_cr("%*s: %.3f", indent1, "Timestamp", _time);
    st->print_cr("%*s: " PTR_FORMAT, indent1, "Thread", p2i(_thread));
    st->print("%*s: ", indent1, "Nodes at global peak");
    if (_live_nodes_at_global_peak > 0) {
      st->print_cr("%u", _live_nodes_at_global_peak);
    } else {
      st->print_cr("-");
    }
    st->print_cr("%*s: %zu", indent1, "Code Size", _code_size);
    st->print_cr("           ------ Arena Usage by Arena Tag, across all phases, at peak -------------------");
    for (int tag = 0; tag < arena_tag_max; tag++) {
      const size_t v = _peak_composition_per_arena_tag[tag];
      st->print_cr("%*s: %zu ", indent2, Arena::tag_desc[tag], v);
    }
    if (_detail_stats != nullptr && _comp_type == CompilerType::compiler_c2) {
      st->print_cr("          ----- Arena Usage by Arena Tag and compilation phase, at peak ----------------");
      _detail_stats->counters_at_global_peak.print_on(st);
      st->print_cr("          ------Allocation timelime by phase, last %u compilation phases ---------------", FootprintTimeline::max_num_phases);
      _detail_stats->timeline.print_on(st);
    }
  }

  int compare_by_size(const MemStatEntry* b) const {
    const size_t x1 = b->_peak;
    const size_t x2 = _peak;
    return x1 < x2 ? -1 : x1 == x2 ? 0 : 1;
  }
};

static inline ssize_t diff_entries_by_size(const MemStatEntry* e1, const MemStatEntry* e2) {
  return e1->compare_by_size(e2);
}

class MemStatTable : public CHeapObj<mtCompiler> {

  // Total number of entries. Reaching this limit, we discard oldest first.
  static constexpr int max_entries = 512 * K;
  SimpleFifo<MemStatEntry*, max_entries> _entries;

  // (C2) Details structure is hefty. To conserve memory, we only store those for compilations
  // above a certain size (the vast majority of compilations only consumes a few hundred KB).
  static constexpr size_t detail_threshold = 2 * M;

  template<typename F>
  int iterate_sorted_filtered(F f, size_t minsize) const {
    assert_lock_strong(NMTCompilationCostHistory_lock);

    // Build up filtered array
    const int num_all = _entries.size();
    const MemStatEntry** filtered = NEW_C_HEAP_ARRAY(const MemStatEntry*, num_all, mtInternal);
    int num_filtered = 0;
    auto copy_to_flat_array = [&](const MemStatEntry* e) {
      if (e->peak() >= minsize) {
        filtered[num_filtered] = e;
        assert(num_filtered < num_all, "Sanity");
        num_filtered++;
      }
    };
    _entries.iterate_all(copy_to_flat_array);
    assert(num_filtered <= num_all, "Sanity");

    // Sort it by entry size
    if (num_filtered > 0) {
      QuickSort::sort(filtered, num_filtered, diff_entries_by_size);
    }

    // Run
    for (int i = 0; i < num_filtered; i ++) {
      f(filtered[i]);
    }

    // free temp array
    FREE_C_HEAP_ARRAY(MemStatEntry, filtered);

    return num_filtered;
  }

  void print_footer(outputStream* st, int printed, size_t minsize) const {
    const int num_all = _entries.size();
    if (printed > 0) {
      st->print_cr("Total: %d compilations ", printed);
    } else {
      st->print_cr("No entries.");
    }
    const int omitted = num_all - printed;
    if (omitted > 0) {
      st->print_cr(" (%d compilations smaller than %zu omitted)", omitted, minsize);
    }
    if (_entries.lost() > 0) {
      st->print_cr(" (%zu old compilations lost - use method filters on the memstat CompileCommand to "
                   "reduce the amount of information gathered)", _entries.lost());
    }
  }

public:

  void add(const FullMethodName& fmn, const ArenaState* state, size_t code_size, const char* result) {
    assert_lock_strong(NMTCompilationCostHistory_lock);
    MemStatEntry*& e = _entries.current();
    if (_entries.wrapped()) {
      e->reset(); // Reuse entry
    } else {
      e = new MemStatEntry(fmn);
    }
    e->set_current_time();
    e->set_current_thread();
    e->inc_recompilation();
    e->set_result(result);
    e->set_code_size(code_size);

    const bool save_details = state->peak() >= detail_threshold;
    e->set_from_state(state, save_details);

    // advance position
    _entries.advance();
  }

  void print_table(outputStream* st, size_t minsize) const {
    assert_lock_strong(NMTCompilationCostHistory_lock);

    MemStatEntry::print_legend(st);
    st->cr();

    MemStatEntry::print_header(st);
    st->cr();

    auto printer = [&](const MemStatEntry* e) {
      e->print_brief_oneline(st);
    };
    const int num_printed = iterate_sorted_filtered(printer, minsize);

    print_footer(st, num_printed, minsize);
  }

  void print_details(outputStream* st, size_t minsize) const {
    assert_lock_strong(NMTCompilationCostHistory_lock);

    auto printer = [&](const MemStatEntry* e) {
      e->print_detailed(st);
      st->print_cr("====================================================================================");
    };
    const int num_printed = iterate_sorted_filtered(printer, minsize);

    print_footer(st, num_printed, minsize);
  }

  int size() const { return _entries.size(); }
};

bool CompilationMemoryStatistic::_enabled = false;

static MemStatTable* _the_table = nullptr;

void CompilationMemoryStatistic::initialize() {
  assert(_enabled == false && _the_table == nullptr, "Only once");
  _the_table = new MemStatTable;
  _enabled = true;
  log_info(compilation, alloc)("Compilation memory statistic enabled");
}

void CompilationMemoryStatistic::on_start_compilation(const DirectiveSet* directive) {
  assert(enabled(), "Not enabled?");
  assert(directive->should_collect_memstat(), "Don't call if not needed");
  CompilerThread* const th = Thread::current()->as_Compiler_thread();
  CompileTask* const task = th->task();
  const CompilerType comp_type = task->compiler()->type();
  const int comp_id = task->compile_id();
  const size_t limit = directive->mem_limit();
  ArenaState* const arena_stat = new ArenaState(comp_type, comp_id, limit);
  th->set_arenastat(arena_stat);
}

void CompilationMemoryStatistic::on_end_compilation() {
  assert(enabled(), "Not enabled?");
  CompilerThread* const th = Thread::current()->as_Compiler_thread();
  ArenaState* const arena_stat = th->arena_stat();
  if (arena_stat == nullptr) { // not started
    return;
  }

  CompileTask* const task = th->task();
  assert(task->compile_id() == arena_stat->comp_id(), "Different compilation?");

  const Method* const m = th->task()->method();

  ResourceMark rm;
  FullMethodName fmn(m);
  fmn.make_permanent();

  const DirectiveSet* directive = th->task()->directive();
  assert(directive->should_collect_memstat(), "Should only be called if memstat is enabled for this method");
  const bool print = directive->should_print_memstat();

  // Store memory used in task, for later processing by JFR
  task->set_arena_bytes(arena_stat->peak());

  // Store result
  // For this to work, we must call on_end_compilation() at a point where
  // Compile|Compilation already handed over the failure string to ciEnv,
  // but ciEnv must still be alive.
  const char* result = "ok"; // ok
  const ciEnv* const env = th->env();
  if (env) {
    const char* const failure_reason = env->failure_reason();
    if (failure_reason != nullptr) {
      result = (strcmp(failure_reason, failure_reason_memlimit()) == 0) ? "oom" : "err";
    }
  }

  size_t code_size = 0;
  if (m->code() != nullptr) {
    code_size = m->code()->total_size();
  }

  {
    MutexLocker ml(NMTCompilationCostHistory_lock, Mutex::_no_safepoint_check_flag);
    assert(_the_table != nullptr, "not initialized");

    _the_table->add(fmn, arena_stat, code_size, result);
  }

  if (print) {
    // Pre-assemble string to prevent tearing
    stringStream ss;
    ss.print("%s (%d) Arena usage ", compilertype2name(arena_stat->comp_type()), arena_stat->comp_id());
    fmn.print_on(&ss);
    ss.print_raw(": ");
    arena_stat->print_peak_state_on(&ss);
    tty->print_raw(ss.base());
  }

  // Mark end of compilation by clearing out the arena state object
  th->set_arenastat(nullptr);
  delete arena_stat;
}

static void inform_compilation_about_oom(CompilerType ct) {
  // Inform C1 or C2 that an OOM happened. They will take delayed action
  // and abort the compilation in progress. Note that this is not instantaneous,
  // since the compiler has to actively bailout, which may take a while, during
  // which memory usage may rise further.
  //
  // The mechanism differs slightly between C1 and C2:
  // - With C1, we directly set the bailout string, which will cause C1 to
  //   bailout at the typical BAILOUT places.
  // - With C2, the corresponding mechanism would be the failure string; but
  //   bailout paths in C2 are not complete and therefore it is dangerous to
  //   set the failure string at - for C2 - seemingly random places. Instead,
  //   upon OOM C2 sets the failure string next time it checks the node limit.
  if (ciEnv::current() != nullptr) {
    void* compiler_data = ciEnv::current()->compiler_data();
#ifdef COMPILER1
    if (ct == compiler_c1) {
      Compilation* C = static_cast<Compilation*>(compiler_data);
      if (C != nullptr) {
        C->bailout(CompilationMemoryStatistic::failure_reason_memlimit());
        C->set_oom();
      }
    }
#endif
#ifdef COMPILER2
    if (ct == compiler_c2) {
      Compile* C = static_cast<Compile*>(compiler_data);
      if (C != nullptr) {
        C->set_oom();
      }
    }
#endif // COMPILER2
  }
}

void CompilationMemoryStatistic::on_arena_chunk_allocation_0(size_t size, int arena_tag, uint64_t* stamp) {
  assert(enabled(), "Not enabled?");
  assert(arena_tag >= 0 && arena_tag < arena_tag_max, "Arena Tag OOB (%d)", arena_tag_max);

  CompilerThread* const th = Thread::current()->as_Compiler_thread();
  ArenaState* const arena_stat = th->arena_stat();
  if (arena_stat == nullptr) { // not started
    return;
  }
  if (arena_stat->limit_in_process()) {
    return; // avoid recursion on limit hit
  }

  bool hit_limit_before = arena_stat->hit_limit();

  if (arena_stat->on_arena_chunk_allocation(size, arena_tag, stamp)) { // new peak?

    // Limit handling
    if (arena_stat->hit_limit()) {
      char name[1024] = "";
      bool print = false;
      bool crash = false;
      CompilerType ct = compiler_none;

      arena_stat->set_limit_in_process(true); // prevent recursive limit hits

      // get some more info
      const CompileTask* const task = th->task();
      if (task != nullptr) {
        ct = task->compiler()->type();
        const DirectiveSet* directive = task->directive();
        print = directive->should_print_memstat();
        crash = directive->should_crash_at_mem_limit();
        const Method* m = th->task()->method();
        if (m != nullptr) {
          FullMethodName(m).as_C_string(name, sizeof(name));
        }
      }

      stringStream short_msg;

      if (print || crash) {
        if (ct != compiler_none && name[0] != '\0') {
          short_msg.print("%s %s: ", compilertype2name(ct), name);
        }
        short_msg.print("Hit MemLimit %s - limit: %zu now: %zu (see previous output for details)",
                 (hit_limit_before ? "again " : ""),
                 arena_stat->limit(), arena_stat->peak());
        stringStream long_msg;
        arena_stat->print_peak_state_on(&long_msg);
        long_msg.print_raw(short_msg.base());
        tty->print_raw(long_msg.base());
        tty->cr();
      }

      // Crash out if needed
      if (crash) {
        report_fatal(OOM_HOTSPOT_ARENA, __FILE__, __LINE__, "%s", short_msg.base());
      } else {
        inform_compilation_about_oom(ct);
      }

      arena_stat->set_limit_in_process(false);
    } // end Limit handling
  }
}

void CompilationMemoryStatistic::on_arena_chunk_deallocation_0(size_t size, uint64_t stamp) {
  assert(enabled(), "Not enabled?");
  CompilerThread* const th = Thread::current()->as_Compiler_thread();
  ArenaState* const arena_stat = th->arena_stat();
  if (arena_stat == nullptr) { // not started
    return;
  }
  if (arena_stat->limit_in_process()) {
    return; // avoid recursion on limit hit
  }
  arena_stat->on_arena_chunk_deallocation(size, stamp);
}

void CompilationMemoryStatistic::on_phase_start_0(int phase_trc_id) {
  assert(enabled(), "Not enabled?");
  assert(phase_trc_id >= 0 && phase_trc_id < phase_trc_id_max, "Phase trace id OOB (%d)", phase_trc_id);
  CompilerThread* const th = Thread::current()->as_Compiler_thread();
  ArenaState* const arena_stat = th->arena_stat();
  if (arena_stat == nullptr) { // not started
    return;
  }
  arena_stat->on_phase_start(phase_trc_id);
}

void CompilationMemoryStatistic::on_phase_end_0(int phase_trc_id) {
  assert(enabled(), "Not enabled?");
  assert(phase_trc_id >= 0 && phase_trc_id < phase_trc_id_max, "Phase trace id OOB (%d)", phase_trc_id);
  CompilerThread* const th = Thread::current()->as_Compiler_thread();
  ArenaState* const arena_stat = th->arena_stat();
  if (arena_stat == nullptr) { // not started
    return;
  }
  arena_stat->on_phase_end(phase_trc_id);
}

void CompilationMemoryStatistic::print_all(outputStream* st, bool verbose, size_t minsize) {

  MutexLocker ml(NMTCompilationCostHistory_lock, Mutex::_no_safepoint_check_flag);

  if (!enabled()) {
    st->print_cr("Compilation memory statistics are unavailable. "
                 "Did you specifiy -XX:CompileCommand=memstat?");
    return;
  }

  st->cr();
  st->print_cr("Compilation memory statistics, by allocation size");
  st->cr();

  if (_the_table == nullptr) {
    st->print_cr("Compilation memory statistics not yet initialized. ");
    return;
  }

  if (verbose) {
    _the_table->print_details(st, minsize);
  } else {
    _the_table->print_table(st, minsize);
  }

  st->cr();
}

const char* CompilationMemoryStatistic::failure_reason_memlimit() {
  static const char* const s = "hit memory limit while compiling";
  return s;
}

#ifdef ASSERT
// JTReg-test code. See
static bool is_compiling_jtreg_test_method(CompilerType& ctyp) {
  bool result = false;
  ctyp = CompilerType::compiler_none;
  CompilerThread* const th = Thread::current()->as_Compiler_thread();
  CompileTask* const task = th == nullptr ? nullptr : th->task();
  if (task != nullptr) {
    const Method* const m = th->task()->method();
    if (m != nullptr) {
      FullMethodName fmn(m);
      char tmp[1024];
      fmn.as_C_string(tmp, sizeof(tmp));
      #define TEST_METHOD_PREFIX "compiler/print/CompileCommandPrintMemStat$TestMain::method"
      return strncmp(tmp, TEST_METHOD_PREFIX, sizeof(TEST_METHOD_PREFIX) - 1) == 0;
      #undef TEST_METHOD_PREFIX
    }
    ctyp = th->task()->compiler()->type();
    result = true;
  }
  return result;
}

void CompilationMemoryStatistic::do_test_allocations() {
  CompilerType ctyp;
  if (!is_compiling_jtreg_test_method(ctyp)) {
    return;
  }

  // Allocate large amounts - large enough to (comfortably) cause new arena chunks to be
  // allocated, as well as large enough to trigger a new peak that is the highest peak that
  // would happend during compilation of the very small test methods.
  const size_t large_size = MAX3(M * 3, (size_t)Chunk::max_default_size * 2, significant_peak_threshold);

  // 1) memory that "sticks"
  // Allocate ResouceArea and leak it to the end of the compilation. Since we do this
  // only for the one jtreg test class, this is fine. This allocation should turn up also in the
  // non-verbose printout.

  NEW_RESOURCE_ARRAY(char, large_size);

  if (ctyp == CompilerType::compiler_c2) {
    // For C2 only, cause temporary spikes in test phases with non-RA test arenas. These allocations
    // won't show up in the non-verbose overview mode, since the spikes will vanish at the end of the
    // phase. However, they should turn up in both the detailled peak allocation breakdown as well as
    // the phase allocation timeline.
    Compile::TracePhase tp(Phase::_t_testTimer1);
    Arena testArena1(MemTag::mtTest, Arena::Tag::tag_node);
    Arena testArena2(MemTag::mtTest, Arena::Tag::tag_regsplit);
    // The following allocations bring us to a new peak
    testArena1.Amalloc(large_size);
    testArena2.Amalloc(large_size);
    {
      Compile::TracePhase tp(Phase::_t_testTimer2);
      testArena1.Amalloc(large_size);
      testArena2.Amalloc(large_size);
    }
  }
}
#endif // ASSERT

CompilationMemoryStatisticMark::CompilationMemoryStatisticMark(const DirectiveSet* directive)
  : _active(directive->should_collect_memstat()) {
  if (_active) {
    CompilationMemoryStatistic::on_start_compilation(directive);
  }
}

CompilationMemoryStatisticMark::~CompilationMemoryStatisticMark() {
  if (_active) {
    CompilationMemoryStatistic::on_end_compilation();
  }
}

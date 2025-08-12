/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2025, Red Hat, Inc. and/or its affiliates.
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

#include "code/nmethod.hpp"
#include "compiler/abstractCompiler.hpp"
#include "compiler/compilationMemStatInternals.inline.hpp"
#include "compiler/compilerDefinitions.inline.hpp"
#include "compiler/compilerDirectives.hpp"
#include "compiler/compilerOracle.hpp"
#include "compiler/compilerThread.hpp"
#include "compiler/compileTask.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/arena.hpp"
#include "nmt/nmtCommon.hpp"
#include "oops/method.inline.hpp"
#include "oops/symbol.hpp"
#include "runtime/atomic.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.hpp"
#include "utilities/checkedCast.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"

#ifdef COMPILER1
#include "c1/c1_Compilation.hpp"
#endif

#ifdef COMPILER2
#include "opto/compile.hpp"
#include "opto/node.hpp" // compile.hpp is not self-contained
#endif

static const char* phase_trc_id_to_string(int phase_trc_id) {
  return COMPILER2_PRESENT(Phase::get_phase_trace_id_text((Phase::PhaseTraceId)phase_trc_id))
         NOT_COMPILER2("");
}

// If crash option on memlimit is enabled and an oom occurred, the stats for the
// first offending compilation.
static ArenaStatCounter* volatile _arenastat_oom_crash = nullptr;

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
        st->print("%-24s %10s", "Phase", "Total");
        for (int arena_tag = 0; arena_tag < arena_tag_max; arena_tag++) {
          st->print("%10s", Arena::tag_name[arena_tag]);
        }
        st->cr();
        header_printed = true;
      }
      st->print("%-24s ", phase_trc_id_to_string(phase_trc_id));
      st->print("%10zu", sum);
      for (int arena_tag = 0; arena_tag < arena_tag_max; arena_tag++) {
        const size_t v = at(phase_trc_id, arena_tag);
        st->print("%10zu", v);
      }
      st->cr();
    }
  }
}

// When reporting phase footprint movements, if phase-local peak over start as well over end
// was larger than this threshold, we report it.
static constexpr size_t significant_peak_threshold = M;

FootprintTimeline::FootprintTimeline() {
  DEBUG_ONLY(_inbetween_phases = true;)
}

void FootprintTimeline::copy_from(const FootprintTimeline& other) {
  _fifo.copy_from(other._fifo);
  DEBUG_ONLY(_inbetween_phases = other._inbetween_phases;)
}

void FootprintTimeline::print_on(outputStream* st) const {
  const int start_indent = st->indentation();
  if (!_fifo.empty()) {
               // .123456789.123456789.123456789.123456789.123456789.123456789.123456789.123456789.123456789.123456789
    st->print_cr("Phase seq. number                             Bytes                  Nodes");
    unsigned from = 0;
    if (_fifo.lost() > 0) {
      st->print_cr("         (" UINT64_FORMAT " older entries lost)", _fifo.lost());
    }
    int last_level = 0;
    int last_num = 0; // see if we are regressing
    auto printer = [&](const Entry& e) {
      int col = start_indent;
      check_phase_trace_id(e.info.id);
      st->print("%*s", e.level,
          ((e.level < last_level) ? "<" : ((e.level > last_level) ? ">" : " "))
      );
      last_level = e.level;
      st->print("%d ", e.info.num);
      if (e.info.num < last_num) {
        st->print("(cont.) ");
      }
      last_num = e.info.num;
      col += 15; st->fill_to(col);
      st->print("%24s", e.info.text);
      col += 25; st->fill_to(col);
      char tmp[64];
      os::snprintf(tmp, sizeof(tmp), "%9zu (%+zd)", e._bytes.cur, e._bytes.end_delta());
      st->print("%s ", tmp); // end
      col += 21; st->fill_to(col);
      os::snprintf(tmp, sizeof(tmp), "%6u (%+d)", e._live_nodes.cur, e._live_nodes.end_delta());
      st->print("%s ", tmp); // end
      if (e._bytes.temporary_peak_size() > significant_peak_threshold) {
        col += 20; st->fill_to(col);
        st->print(" significant temporary peak: %zu (%+zd)", e._bytes.peak, (ssize_t)e._bytes.peak - e._bytes.start); // peak
      }
      st->cr();
    };
    _fifo.iterate_all(printer);
  }
}

void FootprintTimeline::on_phase_end(size_t cur_abs, unsigned cur_nodes) {
  const Entry& old = _fifo.current();

  // One last counter update in old phase:
  // We see all allocations, so cur_abs given should correspond to our topmost cur.
  // But the same does not hold for nodes, since we only get updated when node allocation
  // would cause a new arena chunk to be born. Node allocations that don't cause arena
  // chunks (the vast majority) fly by us.
  assert(old._bytes.cur == cur_abs, "miscount");
  on_footprint_change(cur_abs, cur_nodes);

  // Close old, open new entry
  _fifo.advance();

  DEBUG_ONLY(_inbetween_phases = true;)
}

void FootprintTimeline::on_phase_start(PhaseInfo info, size_t cur_abs, unsigned cur_nodes, int level) {
  if (!_fifo.empty() && _fifo.last().info.id == info.id && _fifo.last().level == level) {
    // Two phases with the same id are collapsed if they were not interleaved by another phase
    _fifo.revert();
    // We now just continue bookkeeping into the last entry
  } else {
    // seed current entry
    Entry& e = _fifo.current();
    e._bytes.init(cur_abs);
    e._live_nodes.init(cur_nodes);
    e.info = info;
    e.level = level;
  }
  DEBUG_ONLY(_inbetween_phases = false;)
}

FullMethodName::FullMethodName() : _k(nullptr), _m(nullptr), _s(nullptr) {}

FullMethodName::FullMethodName(const Method* m) :
  _k(m->klass_name()), _m(m->name()), _s(m->signature()) {};

FullMethodName::FullMethodName(const FullMethodName& o) : _k(o._k), _m(o._m), _s(o._s) {}

FullMethodName& FullMethodName::operator=(const FullMethodName& o) {
  _k = o._k; _m = o._m; _s = o._s;
  return *this;
}

void FullMethodName::make_permanent() {
  _k->make_permanent();
  _m->make_permanent();
  _s->make_permanent();
}

void FullMethodName::print_on(outputStream* st) const {
  char tmp[1024];
  st->print_raw(_k->as_C_string(tmp, sizeof(tmp)));
  st->print_raw("::");
  st->print_raw(_m->as_C_string(tmp, sizeof(tmp)));
  st->put('(');
  st->print_raw(_s->as_C_string(tmp, sizeof(tmp)));
  st->put(')');
}

char* FullMethodName::as_C_string(char* buf, size_t len) const {
  stringStream ss(buf, len);
  print_on(&ss);
  return buf;
}

bool FullMethodName::operator== (const FullMethodName& b) const {
  return _k == b._k && _m == b._m && _s == b._s;
}

#ifdef ASSERT
bool FullMethodName::is_test_class() const {
  char tmp[1024];
  _k->as_C_string(tmp, sizeof(tmp));
  return strstr(tmp, "CompileCommandPrintMemStat") != nullptr ||
         strstr(tmp, "CompileCommandMemLimit") != nullptr;
}
#endif // ASSERT

ArenaStatCounter::ArenaStatCounter(const CompileTask* task, size_t limit) :
    _fmn(task->method()),
    _should_print_memstat(task->directive()->should_print_memstat()),
    _should_crash_on_memlimit(task->directive()->should_crash_at_mem_limit()),
    _current(0), _peak(0), _live_nodes_current(0), _live_nodes_at_global_peak(0),
    _limit(limit), _hit_limit(false), _limit_in_process(false),
    _phase_counter(0), _comp_type(task->compiler()->type()), _comp_id(task->compile_id())
    DEBUG_ONLY(COMMA _is_test_class(false))
{
  _fmn.make_permanent();
#ifdef ASSERT
  // If the class name matches the JTreg test class, we are in test mode and
  // will do some test allocations to test the statistic
  _is_test_class = _fmn.is_test_class();
#endif // ASSERT
}

void ArenaStatCounter::on_phase_start(PhaseInfo info) {
  // Update node counter
  _live_nodes_current = retrieve_live_node_count();

  // For the timeline, when nesting TracePhase happens, we maintain the illusion of a flat succession of
  // separate phases. Thus, { TracePhase p1; { TracePhase p2; }} will be seen as:
  // P1 starts -> P1 ends -> P2 starts -> P2 ends -> P1 starts -> P1 ends
  // In other words, when a child phase interrupts a parent phase, it "ends" the parent phase, which will
  // be "restarted" when the child phase ends.
  // This is the only way to get a per-phase timeline that makes any sort of sense.
  if (!_phase_info_stack.empty()) {
    _timeline.on_phase_end(_current, _live_nodes_current);
  }
  _phase_info_stack.push(info);
  _timeline.on_phase_start(info, _current, _live_nodes_current, _phase_info_stack.depth());
}

void ArenaStatCounter::on_phase_end() {
  PhaseInfo top = _phase_info_stack.top();
  _phase_info_stack.pop();
  _live_nodes_current = retrieve_live_node_count();
  _timeline.on_phase_end(_current, _live_nodes_current);
  if (!_phase_info_stack.empty()) {
    // "restart" parent phase in timeline
    _timeline.on_phase_start(_phase_info_stack.top(), _current, _live_nodes_current, _phase_info_stack.depth());
  }
}

int ArenaStatCounter::retrieve_live_node_count() const {
  int result = 0;
#ifdef COMPILER2
  if (_comp_type == compiler_c2) {
    // Update C2 node count
    // Careful, Compile::current() may be null in a short time window when Compile itself
    // is still being constructed.
    if (Compile::current() != nullptr) {
      result = Compile::current()->live_nodes();
    }
  }
#endif // COMPILER2
  return result;
}

// Account an arena allocation. Returns true if new peak reached.
bool ArenaStatCounter::on_arena_chunk_allocation(size_t size, int arena_tag, uint64_t* stamp) {
  bool rc = false;

  const size_t old_current = _current;
  _current += size;
  assert(_current >= old_current, "Overflow");

  const int phase_trc_id = _phase_info_stack.top().id;
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
  cs.phase_id = checked_cast<uint16_t>(_phase_info_stack.top().id);
  *stamp = cs.raw;

  return rc;
}

void ArenaStatCounter::on_arena_chunk_deallocation(size_t size, uint64_t stamp) {
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
void ArenaStatCounter::print_peak_state_on(outputStream* st) const {
  st->print("Total Usage: %zu ", _peak);
  if (_peak > 0) {
#ifdef COMPILER1
    // C1: print allocations broken down by arena types
    if (_comp_type == CompilerType::compiler_c1) {
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
    }
#endif // COMPILER1
#ifdef COMPILER2
    // C2: print counters and timeline on multiple lines, indented
    if (_comp_type == CompilerType::compiler_c2) {
      StreamIndentor si(st, 4);
      st->cr();
      st->print_cr("--- Arena Usage by Arena Type and compilation phase, at arena usage peak of %zu ---", _peak);
      {
        StreamIndentor si(st, 4);
       _counters_at_global_peak.print_on(st);
      }
      st->print_cr("--- Allocation timelime by phase ---");
      {
        StreamIndentor si(st, 4);
        _timeline.print_on(st);
      }
      st->print_cr("---");
    }
#endif
  } else {
    st->cr();
  }
}

class MemStatEntry : public CHeapObj<mtCompiler> {

  FullMethodName _fmn;
  CompilerType _comp_type;
  int _comp_id;
  double _time;
  // Compiling thread. Only for diagnostic purposes. Thread may not be alive anymore.
  const Thread* _thread;
  // active limit for this compilation, if any
  size_t _limit;
  // true if the compilation hit the limit
  bool _hit_limit;
  // result as reported by compiler
  const char* _result;

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

  MemStatEntry()
    : _comp_type(compiler_none), _comp_id(-1),
      _time(0), _thread(nullptr), _limit(0), _hit_limit(false),
      _result(nullptr), _peak(0), _live_nodes_at_global_peak(0),
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

  void set_from_state(const ArenaStatCounter* state, bool store_details) {
    _fmn = state->fmn();
    _comp_type = state->comp_type();
    _comp_id = state->comp_id();
    _limit = state->limit();
    _hit_limit = state->hit_limit();
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

  void set_result(const char* s)  { _result = s; }

  size_t peak() const { return _peak; }
  bool is_c1() const { return _comp_type == CompilerType::compiler_c1; }
  bool is_c2() const { return _comp_type == CompilerType::compiler_c2; }

  static void print_legend(outputStream* st) {
#define LEGEND_KEY_FMT "%11s"
    st->print_cr("Legend:");
    st->print_cr("  " LEGEND_KEY_FMT ": %s", "ctype", "compiler type");
    st->print_cr("  " LEGEND_KEY_FMT ": %s", "total", "peak memory allocated via arenas while compiling");
    for (int tag = 0; tag < arena_tag_max; tag++) {
      st->print_cr("  " LEGEND_KEY_FMT ": %s", Arena::tag_name[tag], Arena::tag_desc[tag]);
    }
    st->print_cr("  " LEGEND_KEY_FMT ": %s", "#nodes", "...how many nodes (c2 only)");
    st->print_cr("  " LEGEND_KEY_FMT ": %s", "result", "Result reported by compiler");
    st->print_cr("  " LEGEND_KEY_FMT ": %s", "limit", "memory limit; followed by \"*\" if the limit was hit");
    st->print_cr("  " LEGEND_KEY_FMT ": %s", "time", "timestamp");
    st->print_cr("  " LEGEND_KEY_FMT ": %s", "id", "compile id");
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
#define HDR_FMT1 "%-8s%-8s%-10s%-8s"
#define HDR_FMT2 "%-6s%-19s%s"

    st->print(HDR_FMT1, "#nodes", "result", "limit", "time");
    st->print(HDR_FMT2, "id", "thread", "method");
  }

  void print_brief_oneline(outputStream* st) const {
    int col = st->indentation();

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

    // result?
    st->print("%s ", _result ? _result : "");
    col += 8; st->fill_to(col);

    // Limit
    if (_limit > 0) {
      st->print("%zu%s ", _limit, _hit_limit ? "*" : "");
    } else {
      st->print("-");
    }
    col += 10; st->fill_to(col);

    // TimeStamp
    st->print("%.3f ", _time);
    col += 8; st->fill_to(col);

    // Compile ID
    st->print("%d ", _comp_id);
    col += 6; st->fill_to(col);

    // Thread
    st->print(PTR_FORMAT " ", p2i(_thread));

    // MethodName
    char buf[1024];
    st->print("%s ", _fmn.as_C_string(buf, sizeof(buf)));

    st->cr();
  }

  void print_detailed(outputStream* st) const {
    int col = 0;

    constexpr int indent1 = 40;
    constexpr int indent2 = 50;

    char buf[1024];
    st->print_cr("Method              : %s", _fmn.as_C_string(buf, sizeof(buf)));
    st->print_cr("Compiler            : %2s", compilertype2name(_comp_type));
    st->print(   "Arena Usage at peak : %zu", _peak);
    if (_peak > M) {
      st->print(" (%.2fM)", ((double)_peak/(double)M));
    }
    st->cr();
    if (_comp_type == CompilerType::compiler_c2) {
      st->print_cr("Nodes at peak       : %u", _live_nodes_at_global_peak);
    }
    st->print_cr("Compile ID          : %d", _comp_id);
    st->print(   "Result              : %s", _result);
    if (strcmp(_result, "oom") == 0) {
      st->print(" (memory limit was: %zu)", _limit);
    }
    st->cr();
    st->print_cr("Thread              : " PTR_FORMAT, p2i(_thread));
    st->print_cr("Timestamp           : %.3f", _time);

    if (_detail_stats != nullptr) {
      st->cr();
      st->print_cr("Arena Usage by Arena Type and compilation phase, at arena usage peak of %zu:", _peak);
      _detail_stats->counters_at_global_peak.print_on(st);
      st->cr();
      st->print_cr("Allocation timelime by phase:");
      _detail_stats->timeline.print_on(st);
    } else {
      st->cr();
      st->print_cr("Arena Usage by Arena Type, at arena usage peak of %zu:", _peak);
      for (int tag = 0; tag < arena_tag_max; tag++) {
        const size_t v = _peak_composition_per_arena_tag[tag];
        if (v > 0) {
          st->print_cr("%-36s: %zu ", Arena::tag_desc[tag], v);
        }
      }
    }
  }
};

class MemStatStore : public CHeapObj<mtCompiler> {

  // Total number of entries. Reaching this limit, we discard the least interesting (smallest allocation size) first.
  static constexpr int max_entries = 64;

  struct {
    size_t s; MemStatEntry* e;
  } _entries[max_entries];

  struct iteration_result { unsigned num, num_c1, num_c2, num_filtered_out; };
  template<typename F>
  void iterate_sorted_filtered(F f, size_t minsize, int max_num_printed, iteration_result& result) const {
    assert_lock_strong(NMTCompilationCostHistory_lock);
    const unsigned stop_after = max_num_printed == -1 ? UINT_MAX : (unsigned)max_num_printed;
    result.num = result.num_c1 = result.num_c2 = result.num_filtered_out = 0;
    for (int i = 0; i < max_entries && _entries[i].e != nullptr && result.num < stop_after; i++) {
      if (_entries[i].s >= minsize) {
        f(_entries[i].e);
        result.num++;
        result.num_c1 += _entries[i].e->is_c1() ? 1 : 0;
        result.num_c2 += _entries[i].e->is_c2() ? 1 : 0;
      } else {
        result.num_filtered_out++;
      }
    }
  }

  void print_footer(outputStream* st, size_t minsize, const iteration_result& result) const {
    if (result.num > 0) {
      st->print_cr("Total: %u (C1: %u, C2: %u)", result.num, result.num_c1, result.num_c2);
    } else {
      st->print_cr("No entries.");
    }
    if (result.num_filtered_out > 0) {
      st->print_cr(" (%d compilations smaller than %zu omitted)", result.num_filtered_out, minsize);
    }
  }

public:

  MemStatStore() {
    memset(_entries, 0, sizeof(_entries));
  }

  void add(const ArenaStatCounter* state, const char* result) {

    const size_t size = state->peak();

    // search insert point
    int i = 0;
    while (i < max_entries && _entries[i].s > size) {
      i++;
    }
    if (i == max_entries) {
      return;
    }
    MemStatEntry* e = _entries[max_entries - 1].e; // recycle last one
    if (e == nullptr) {
      e = new MemStatEntry();
    }
    memmove(_entries + i + 1, _entries + i, sizeof(_entries[0]) * (max_entries - i - 1));

    e->reset();
    e->set_current_time();
    e->set_current_thread();
    e->set_result(result);

    // Since we don't have phases in C1, for now we just avoid saving details for C1.
    const bool save_details = state->comp_type() == CompilerType::compiler_c2;
    e->set_from_state(state, save_details);

    _entries[i].s = e->peak();
    _entries[i].e = e;
  }

  void print_table(outputStream* st, bool legend, size_t minsize, int max_num_printed) const {
    assert_lock_strong(NMTCompilationCostHistory_lock);

    if (legend) {
      MemStatEntry::print_legend(st);
      st->cr();
    }

    MemStatEntry::print_header(st);
    st->cr();

    iteration_result itres;
    auto printer = [&](const MemStatEntry* e) {
      e->print_brief_oneline(st);
    };
    iterate_sorted_filtered(printer, minsize, max_num_printed, itres);
    print_footer(st, minsize, itres);
  }

  void print_details(outputStream* st, size_t minsize, int max_num_printed) const {
    assert_lock_strong(NMTCompilationCostHistory_lock);
    iteration_result itres;
    auto printer = [&](const MemStatEntry* e) {
      e->print_detailed(st);
      st->cr();
      st->print_cr("------------------------");
      st->cr();
    };
    iterate_sorted_filtered(printer, minsize, max_num_printed, itres);
  }
};

bool CompilationMemoryStatistic::_enabled = false;
static MemStatStore* _the_store = nullptr;

void CompilationMemoryStatistic::initialize() {
  assert(_enabled == false && _the_store == nullptr, "Only once");
  _the_store = new MemStatStore;
  _enabled = true;
  log_info(compilation, alloc)("Compilation memory statistic enabled");
}

void CompilationMemoryStatistic::on_start_compilation(const DirectiveSet* directive) {
  assert(enabled(), "Not enabled?");
  assert(directive->should_collect_memstat(), "Don't call if not needed");
  CompilerThread* const th = Thread::current()->as_Compiler_thread();
  CompileTask* const task = th->task();
  const size_t limit = directive->mem_limit();
  // Create new ArenaStat object and hook it into the thread
  assert(th->arena_stat() == nullptr, "Sanity");
  ArenaStatCounter* const arena_stat = new ArenaStatCounter(task, limit);
  th->set_arenastat(arena_stat);
  // Start a "root" phase
  PhaseInfo info;
  info.id = phase_trc_id_none;
  info.num = 0;
  info.text = "(outside)";
  arena_stat->on_phase_start(info);
}

void CompilationMemoryStatistic::on_end_compilation() {
  assert(enabled(), "Not enabled?");
  CompilerThread* const th = Thread::current()->as_Compiler_thread();
  ArenaStatCounter* const arena_stat = th->arena_stat();
  if (arena_stat == nullptr) { // not started
    return;
  }

  // Mark end of compilation by clearing out the arena state object in the CompilerThread.
  // Do this before the final "phase end".
  th->set_arenastat(nullptr);

  // End final outer phase.
  arena_stat->on_phase_end();

  CompileTask* const task = th->task();
  assert(task->compile_id() == arena_stat->comp_id(), "Different compilation?");

  const Method* const m = th->task()->method();

  const DirectiveSet* directive = th->task()->directive();
  assert(directive->should_collect_memstat(), "Should only be called if memstat is enabled for this method");
  const bool print = directive->should_print_memstat();

  // Store memory used in task, for later processing by JFR
  task->set_arena_bytes(arena_stat->peak());

  // Store result (ok, failed, oom...)
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

  {
    MutexLocker ml(NMTCompilationCostHistory_lock, Mutex::_no_safepoint_check_flag);
    assert(_the_store != nullptr, "not initialized");
    _the_store->add(arena_stat, result);
  }

  if (print) {
    // Pre-assemble string to prevent tearing
    stringStream ss;
    ss.print("%s (%d) (%s) Arena usage ", compilertype2name(arena_stat->comp_type()), arena_stat->comp_id(), result);
    arena_stat->fmn().print_on(&ss);
    ss.print_raw(": ");
    arena_stat->print_peak_state_on(&ss);
    tty->print_raw(ss.base());
  }

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

void CompilationMemoryStatistic::on_arena_chunk_allocation(size_t size, int arena_tag, uint64_t* stamp) {

  assert(enabled(), "Not enabled?");
  assert(arena_tag >= 0 && arena_tag < arena_tag_max, "Arena Tag OOB (%d)", arena_tag_max);

  CompilerThread* const th = Thread::current()->as_Compiler_thread();
  ArenaStatCounter* const arena_stat = th->arena_stat();
  if (arena_stat == nullptr ||           // not started
      arena_stat->limit_in_process()) {  // limit handling in process, prevent recursion
    return;
  }

  // Compiler can be slow to bailout, so we may hit memlimit more than once
  const bool hit_limit_before = arena_stat->hit_limit();

  if (arena_stat->on_arena_chunk_allocation(size, arena_tag, stamp)) { // new peak?

    // Limit handling
    if (arena_stat->hit_limit()) {
      char name[1024] = "";
      const CompilerType ct = arena_stat->comp_type();
      const bool print = arena_stat->should_print_memstat();
      const bool crash = arena_stat->should_crash_on_memlimit();
      arena_stat->set_limit_in_process(true); // prevent recursive limit hits
      arena_stat->fmn().as_C_string(name, sizeof(name));

      inform_compilation_about_oom(ct);

      if (crash) {
        // Store this ArenaStat. If other threads also run into OOMs, let them sleep.
        // We will never return, so the global store will not contain this info. We will
        // print the stored ArenaStat in hs-err (see print_error_report)
        if (Atomic::cmpxchg(&_arenastat_oom_crash, (ArenaStatCounter*) nullptr, arena_stat) != nullptr) {
          os::infinite_sleep();
        }
      }

      stringStream short_msg;

      // We print to tty if either print is enabled or if we are to crash on MemLimit hit.
      // If printing/crashing are not enabled, we just quietly abort the compilation. The
      // compilation is marked as "oom" in the compilation memory result store.
      if (print || crash) {
        short_msg.print("%s (%d) %s: ", compilertype2name(ct), arena_stat->comp_id(), name);
        short_msg.print("Hit MemLimit %s- limit: %zu now: %zu",
                 (hit_limit_before ? "again " : ""),
                 arena_stat->limit(), arena_stat->peak());
        tty->print_raw(short_msg.base());
        tty->cr();
      }

      if (crash) {
        // Before crashing, if C2, end current phase. That causes its info (which is the most important) to
        // be added to the phase timeline.
        if (arena_stat->comp_type() == CompilerType::compiler_c2) {
          arena_stat->on_phase_end();
        }
        // print extended message to tty (mirrors the one that should show up in the hs-err file, just for good measure)
        tty->print_cr("Details:");
        arena_stat->print_peak_state_on(tty);
        tty->cr();
        // abort VM
        report_fatal(OOM_HOTSPOT_ARENA, __FILE__, __LINE__, "%s", short_msg.base());
      }

      arena_stat->set_limit_in_process(false);
    } // end Limit handling
  }
}

void CompilationMemoryStatistic::on_arena_chunk_deallocation(size_t size, uint64_t stamp) {
  assert(enabled(), "Not enabled?");
  CompilerThread* const th = Thread::current()->as_Compiler_thread();
  ArenaStatCounter* const arena_stat = th->arena_stat();
  if (arena_stat == nullptr) { // not started
    return;
  }
  if (arena_stat->limit_in_process()) {
    return; // avoid recursion on limit hit
  }

  arena_stat->on_arena_chunk_deallocation(size, stamp);
}

void CompilationMemoryStatistic::on_phase_start(int phase_trc_id, const char* text) {
  assert(enabled(), "Not enabled?");
  assert(phase_trc_id >= 0 && phase_trc_id < phase_trc_id_max, "Phase trace id OOB (%d)", phase_trc_id);
  CompilerThread* const th = Thread::current()->as_Compiler_thread();
  ArenaStatCounter* const arena_stat = th->arena_stat();
  if (arena_stat == nullptr) { // not started
    return;
  }
  PhaseInfo info;
  info.id = phase_trc_id;
  info.num = arena_stat->advance_phase_counter();
  info.text = text;
  arena_stat->on_phase_start(info);
}

void CompilationMemoryStatistic::on_phase_end() {
  assert(enabled(), "Not enabled?");
  CompilerThread* const th = Thread::current()->as_Compiler_thread();
  ArenaStatCounter* const arena_stat = th->arena_stat();
  if (arena_stat == nullptr) { // not started
    return;
  }
  arena_stat->on_phase_end();
}

static bool check_before_reporting(outputStream* st) {
  if (!CompilationMemoryStatistic::enabled()) {
    st->print_cr("Compilation memory statistics disabled.");
    return false;
  }
  if (_the_store == nullptr) {
    st->print_cr("Compilation memory statistics not yet initialized. ");
    return false;
  }
  return true;
}

bool CompilationMemoryStatistic::in_oom_crash() {
  return Atomic::load(&_arenastat_oom_crash) != nullptr;
}

void CompilationMemoryStatistic::print_error_report(outputStream* st) {
  if (!check_before_reporting(st)) {
    return;
  }
  StreamIndentor si(tty, 4);
  const ArenaStatCounter* const oom_stats = Atomic::load(&_arenastat_oom_crash);
  if (oom_stats != nullptr) {
    // we crashed due to a compiler limit hit. Lead with a printout of the offending stats
    // in detail.
    st->print_cr("Compiler Memory Statistic, hit OOM limit; offending compilation:");
    oom_stats->fmn().print_on(st);
    st->cr();
    oom_stats->print_peak_state_on(st);
    st->cr();
  }
  st->print_cr("Compiler Memory Statistic, 10 most expensive compilations:");
  print_all_by_size(st, false, false, 0, 10);
}

void CompilationMemoryStatistic::print_final_report(outputStream* st) {
  if (!check_before_reporting(st)) {
    return;
  }
  st->print_cr("Compiler Memory Statistic, 10 most expensive compilations:");
  StreamIndentor si(st, 4);
  print_all_by_size(st, false, false, 0, 10);
}

void CompilationMemoryStatistic::print_jcmd_report(outputStream* st, bool verbose, bool legend, size_t minsize) {
  if (!check_before_reporting(st)) {
    return;
  }
  st->print_cr("Compiler Memory Statistic");
  StreamIndentor si(st, 4);
  print_all_by_size(st, verbose, legend, minsize, -1);
}

void CompilationMemoryStatistic::print_all_by_size(outputStream* st, bool verbose, bool legend, size_t minsize, int max_num_printed) {
  MutexLocker ml(NMTCompilationCostHistory_lock, Mutex::_no_safepoint_check_flag);
  if (verbose) {
    _the_store->print_details(st, minsize, max_num_printed);
  } else {
    _the_store->print_table(st, legend, minsize, max_num_printed);
  }
}

const char* CompilationMemoryStatistic::failure_reason_memlimit() {
  static const char* const s = "hit memory limit while compiling";
  return s;
}

#ifdef ASSERT
void CompilationMemoryStatistic::do_test_allocations() {

  CompilerThread* const th = Thread::current()->as_Compiler_thread();
  ArenaStatCounter* const arena_stat = th->arena_stat();
  if (arena_stat == nullptr || !arena_stat->is_test_class()) { // not started or not the JTREG test
    return;
  }
  const CompilerType ctyp = th->task()->compiler()->type();

  // Note: all allocations in here need to be tagged as mtCompiler to be recognized
  // by the compilation memstat. ResourceArea in a CompilerThread is already tagged
  // mtCompiler (special handling for compiler threads).

#ifdef COMPILER2
  if (ctyp == CompilerType::compiler_c2) {
    {
      Compile::TracePhase tp(Phase::_t_testPhase1);
      Arena ar(MemTag::mtCompiler, Arena::Tag::tag_reglive);
      ar.Amalloc(2 * M); // phase-local peak
    }
    {
      Compile::TracePhase tp(Phase::_t_testPhase2);
      NEW_RESOURCE_ARRAY(char, 32 * M); // leaked (until compilation end)
    }
  } // C2
#endif // COMPILER2

#ifdef COMPILER1
  if (ctyp == CompilerType::compiler_c1) {
    NEW_RESOURCE_ARRAY(char, 32 * M); // leaked (until compilation end)
  }
#endif // COMPILER2

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

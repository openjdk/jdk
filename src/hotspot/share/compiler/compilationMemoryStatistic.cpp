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
#include "compiler/abstractCompiler.hpp"
#include "compiler/compilationMemStatInternals.inline.hpp"
#include "compiler/compilerDefinitions.inline.hpp"
#include "compiler/compilerDirectives.hpp"
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
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"
#include "utilities/quickSort.hpp"
#include "utilities/resourceHash.hpp"

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

void ArenaCounterTable::summarize(size_t out[ArenaTag::max]) const {
  memset(out, 0, ArenaTag::max * sizeof(size_t));
  for (int i = 0; i < PhaseTrcId::max; i++) {
    for (int j = 0; j < ArenaTag::max; j++) {
      out[j] += _v[i][j];
    }
  }
}

void ArenaCounterTable::print_on(outputStream* st) const {
  bool header_printed = false;
  for (int phaseid = 0; phaseid < (int)Phase::PhaseTraceId::max_phase_timers; phaseid++) {
    size_t sum = 0;
    for (int arenatag = 0; arenatag < Arena::tag_count(); arenatag++) {
      sum += at(phaseid, arenatag);
    }
    if (sum > 0) { // omit phases that did not contribute to allocation load
      if (!header_printed) {
        st->print("%24s %10s", "phase name", "total");
        for (int arenatag = 0; arenatag < Arena::tag_count(); arenatag++) {
          st->print("%10s", Arena::tag_name[arenatag]);
        }
        st->cr();
        header_printed = true;
      }
      st->print("%24s ", Phase::get_phase_trace_id_text((Phase::PhaseTraceId)phaseid));
      st->print(SIZE_FORMAT_W(10), sum);
      for (int arenatag = 0; arenatag < Arena::tag_count(); arenatag++) {
        const size_t v = at(phaseid, arenatag);
        st->print(SIZE_FORMAT_W(10), v);
      }
      st->cr();
    }
  }
}

PhaseIdStack::PhaseIdStack() : _depth(0) {
  push((int)Phase::PhaseTraceId::_t_none); // "outside phase"
}

FootprintTimeline::FootprintTimeline() {
  _pos = 0;
  _entries[0].cur = _entries[0].start = _entries[0].peak = 0;
  _entries[0].phaseid = (int)Phase::PhaseTraceId::_t_none; // "outside phase"
}

void FootprintTimeline::copy_from(const FootprintTimeline& other) {
  memcpy(_entries, other._entries, sizeof(_entries));
  _pos = other._pos;
}

void FootprintTimeline::print_entry_on(outputStream* st, unsigned pos) const {
  const Entry& e = at(pos);
  st->print("%2u %24s:", pos, Phase::get_phase_trace_id_text((Phase::PhaseTraceId)e.phaseid));
  st->fill_to(30);
  st->print("%12zu", e.start); // start
  st->fill_to(40);
  st->print("%12zu", e.cur); // end
  st->fill_to(50);
  st->print("%+12zd", (ssize_t)e.cur - e.start); // delta end
  if (e.has_significant_local_peak()) {
    st->fill_to(60);
    st->print(" significant temporary peak: %zu (%+zd)", e.peak, (ssize_t)e.peak - e.start); // peak
  }
  st->cr();
}

void FootprintTimeline::print_on(outputStream* st) const {
  if (_pos > 0) {
    st->print_cr("%30s%12s%12s%12s", "", "start", "end", "end delta");
    unsigned from = 0;
    if (_pos > max_entries) {
      st->print_cr("         (%u older entries skipped)", _pos - max_entries);
      from = _pos - max_entries;
    }
    for (unsigned i = from; i < _pos; i++) {
      print_entry_on(st, i);
    }
  }
}

void FootprintTimeline::on_phase_start(PhaseTrcId id, size_t cur_abs) {
  size_t start_footprint = 0;
  const Entry& old = at(_pos);
  start_footprint = old.cur;
  // close old, open new entry; but only if the last phase is "interesting".
  // An "interesting" phase is one that causes a footprint change (end != start),
  // and/or one that has a local peak that significantly raises above either starting
  // or ending footprint.
  if (old.did_footprint_change() || old.has_significant_local_peak()) {
    _pos++;
  }
  Entry& e = at(_pos);
  e.start = e.cur = e.peak = start_footprint;
  e.phaseid = id.raw();
}

ArenaState::ArenaState(CompilerType comp_type, int comp_id, size_t limit) :
    _current(0), _peak(0), _live_nodes_current(0), _live_nodes_at_global_peak(0),
    _limit(limit), _hit_limit(false), _limit_in_process(false),
    _comp_type(comp_type), _comp_id(comp_id)
{}

void ArenaState::on_phase_start(PhaseTrcId id) {
  _phase_id_stack.push(id);
  _timeline.on_phase_start(id, _current);
}

void ArenaState::on_phase_end(PhaseTrcId id) {
  _phase_id_stack.pop(id);
  _timeline.on_phase_start(_phase_id_stack.top(), _current); // parent phase "restarts"
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
bool ArenaState::on_arena_chunk_allocation(size_t size, int arenatagid, uint64_t* stamp) {
  bool rc = false;

  const size_t old_current = _current;
  _current += size;
  assert(_current >= old_current, "Overflow");

  _timeline.on_footprint_change(_current);

  const PhaseTrcId phase_id = _phase_id_stack.top();
  const ArenaTag arena_tag(arenatagid);

  _counters_current.add(size, phase_id, arena_tag);
  _live_nodes_current = retrieve_live_node_count();

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
  cs.arena_tag = (uint16_t)arenatagid;
  cs.phase_id = (uint16_t)_phase_id_stack.top().raw();
  *stamp = cs.raw;

  return rc;
}

void ArenaState::on_arena_chunk_deallocation(size_t size, uint64_t stamp) {
  assert(_current >= size, "Underflow (%zu %zu)", size, _current);

  // Extract tag and phase id from stamp
  chunkstamp_t cs;
  cs.raw = stamp;
  assert(cs.tracked == 1, "Sanity");
  const PhaseTrcId phase_id(cs.phase_id);
  const ArenaTag arena_tag(cs.arena_tag);

  _current -= size;
  _counters_current.sub(size, phase_id, arena_tag);
  _live_nodes_current = retrieve_live_node_count();
  _timeline.on_footprint_change(_current);
}

// Used for logging, not for the report table generated with jcmd Compiler.memory
void ArenaState::print_peak_state_on(outputStream* st) const {
  st->print("%zu ", _peak);
  if (_peak > 0) {
    st->print("[");
    size_t sums[ArenaTag::max];
    _counters_at_global_peak.summarize(sums);
    bool print_comma = false;
    for (int i = 0; i < ArenaTag::max; i++) {
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
      st->print_cr("----- By phase and arena type, at arena usage peak (%zu) -----", _peak);
      _counters_at_global_peak.print_on(st);
      st->print_cr("------------- Arena allocation timelime by phase -----------------");
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
  for (int phaseid = 0; phaseid < PhaseTrcId::max; phaseid++) {
    for (int arenatag = 0; arenatag < ArenaTag::max; arenatag ++) {
      sum += _counters_at_global_peak.at(phaseid, arenatag);
    }
  }
  assert(sum == _peak, "per phase counter mismatch - %zu, expected %zu", sum, _peak);
#endif
}
#endif // ASSERT

//////////////////////////
// Backend

// The backend is a hashtable.
//
// Key is a combination of method name (see FullMethodName) and compiler type. So, for each
// compiler type, we keep information about the last compilation of a given method
// (and if you need to know footprint of previous compilations, use Memstat print option).
//
// Value is a collection of various information

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

// Note: not mtCompiler since we don't want to change what we measure
class MemStatEntry : public CHeapObj<mtInternal /* (sic) */> {
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

  // Bytes total at global peak
  size_t _peak;
  // Bytes per arena tag.
  size_t _peak_composition_per_arena_tag[ArenaTag::max];
  // Number of live nodes at global peak (C2 only)
  unsigned _live_nodes_at_global_peak;

  struct PerPhaseCounters {
    // Bytes at global peak broken down on per phase and per arena type
    ArenaCounterTable counters_at_global_peak;
    FootprintTimeline timeline;
  };
  PerPhaseCounters* _per_phase_counters; // we only carry these if needed

  MemStatEntry(const MemStatEntry& e); // deny

public:

  MemStatEntry(FullMethodName method)
    : _method(method), _comp_type(compiler_none), _comp_id(-1),
      _time(0), _num_recomp(0), _thread(nullptr), _limit(0),
      _result(nullptr), _peak(0), _live_nodes_at_global_peak(0),
      _per_phase_counters(nullptr) {
  }

  ~MemStatEntry() {
    if (_per_phase_counters != nullptr) {
      FREE_C_HEAP_ARRAY(PerPhaseCounters, _per_phase_counters);
    }
  }

  void set_comp_id(int comp_id) { _comp_id = comp_id; }
  void set_comptype(CompilerType comptype) { _comp_type = comptype; }
  void set_current_time() { _time = os::elapsedTime(); }
  void set_current_thread() { _thread = Thread::current(); }
  void set_limit(size_t limit) { _limit = limit; }
  void inc_recompilation() { _num_recomp++; }

  void set_from_state(const ArenaState* state) {
    _comp_type = state->comp_type();
    _comp_id = state->comp_id();
    _limit = state->limit();
    _peak = state->peak();
    _live_nodes_at_global_peak = state->live_nodes_at_global_peak();
    state->counters_at_global_peak().summarize(_peak_composition_per_arena_tag);
#ifdef COMPILER2
    if (_comp_type == CompilerType::compiler_c2) {
      // Only store per phase details for C2 to save memory. Should we ever introduce something like phases for
      // other compilers, this needs to be changed to a possibly more generic solution.
      if (_per_phase_counters == nullptr) {
        _per_phase_counters = NEW_C_HEAP_OBJ(PerPhaseCounters, mtTest);
      }
      _per_phase_counters->counters_at_global_peak.copy_from(state->counters_at_global_peak());
      _per_phase_counters->timeline.copy_from(state->timeline());
    }
#endif // COMPILER2
  }

  void set_result(const char* s) { _result = s; }

  size_t peak() const { return _peak; }

  static void print_legend(outputStream* st) {
#define LEGEND_KEY_FMT "%11s"
    st->print_cr("Legend:");
    st->print_cr("  " LEGEND_KEY_FMT ": %s", "total", "peak memory allocated via arenas while compiling");
    for (int tag = 0; tag < Arena::tag_count(); tag++) {
      st->print_cr("  " LEGEND_KEY_FMT ": %s", Arena::tag_name[tag], Arena::tag_desc[tag]);
    }
    st->print_cr("  " LEGEND_KEY_FMT ": %s", "result", "Result: 'ok' finished successfully, 'oom' hit memory limit, 'err' compilation failed");
    st->print_cr("  " LEGEND_KEY_FMT ": %s", "#nodes", "...how many nodes (c2 only)");
    st->print_cr("  " LEGEND_KEY_FMT ": %s", "limit", "memory limit, if set");
    st->print_cr("  " LEGEND_KEY_FMT ": %s", "time", "time taken for last compilation (sec)");
    st->print_cr("  " LEGEND_KEY_FMT ": %s", "type", "compiler type");
    st->print_cr("  " LEGEND_KEY_FMT ": %s", "id", "compile id");
    st->print_cr("  " LEGEND_KEY_FMT ": %s", "#rc", "how often recompiled");
    st->print_cr("  " LEGEND_KEY_FMT ": %s", "thread", "compiler thread");
#undef LEGEND_KEY_FMT
  }

  static void print_header(outputStream* st) {
#define SIZE_FMT "%-10s"
    st->print(SIZE_FMT, "total");
    for (int tag = 0; tag < Arena::tag_count(); tag++) {
      st->print(SIZE_FMT, Arena::tag_name[tag]);
    }
#define HDR_FMT1 "%-8s%-8s%-8s%-8s"
#define HDR_FMT2 "%-6s%-6s%-4s%-19s%s"

    st->print(HDR_FMT1, "result", "#nodes", "limit", "time");
    st->print(HDR_FMT2, "type", "id", "#rc", "thread", "method");
    st->print_cr("");
  }

  void print_on(outputStream* st, bool human_readable, bool by_phase) const {
    int col = 0;

    // Total
    size_t v = _peak;
    if (human_readable) {
      st->print(PROPERFMT " ", PROPERFMTARGS(v));
    } else {
      st->print("%zu ", v);
    }
    col += 10; st->fill_to(col);

    for (int tag = 0; tag < ArenaTag::max; tag++) {
      v = _peak_composition_per_arena_tag[tag];
      if (human_readable) {
        st->print(PROPERFMT " ", PROPERFMTARGS(v));
      } else {
        st->print("%zu ", v);
      }
      col += 10; st->fill_to(col);
    }

    // result?
    st->print("%s ", _result ? _result : "");
    col += 8; st->fill_to(col);

    // Number of Nodes when memory peaked
    if (_live_nodes_at_global_peak > 0) {
      st->print("%u ", _live_nodes_at_global_peak);
    } else {
      st->print("-");
    }
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

    // Type
    st->print("%s ", compilertype2name(_comp_type));
    col += 6; st->fill_to(col);

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

    // If we have detail information, print two additional tables in the next lines:
    // One containing the counter composition at global peak, one containing the phase-local
    // counters
    if (_per_phase_counters != nullptr && by_phase) {
      st->print_cr("------------- Arena allocation timelime by phase -----------------");
      st->print_cr("----- By phase and arena type, at arena usage peak (%zu) -----", _peak);
      _per_phase_counters->counters_at_global_peak.print_on(st);
      st->print_cr("------------- Arena allocation timelime by phase -----------------");
      _per_phase_counters->timeline.print_on(st);
      st->print_cr("------------------------------------------------------------------");
    }
  }

  int compare_by_size(const MemStatEntry* b) const {
    const size_t x1 = b->_peak;
    const size_t x2 = _peak;
    return x1 < x2 ? -1 : x1 == x2 ? 0 : 1;
  }
};

// The MemStatTable contains records of memory usage of all compilations. It is printed,
// as memory summary, either with jcmd Compiler.memory, or - if the "print" suboption has
// been given with the MemStat compile command - as summary printout at VM exit.
// For any given compiled method, we only keep the memory statistics of the most recent
// compilation, but on a per-compiler basis. If one needs statistics of prior compilations,
// one needs to look into the log produced by the "print" suboption.

class MemStatTableKey {
  const FullMethodName _fmn;
  const CompilerType _comptype;
public:
  MemStatTableKey(FullMethodName fmn, CompilerType comptype) :
    _fmn(fmn), _comptype(comptype) {}
  MemStatTableKey(const MemStatTableKey& o) :
    _fmn(o._fmn), _comptype(o._comptype) {}
  bool operator== (const MemStatTableKey& other) const {
    return _fmn == other._fmn && _comptype == other._comptype;
  }
  static unsigned compute_hash(const MemStatTableKey& n) {
    return FullMethodName::compute_hash(n._fmn) + (unsigned)n._comptype;
  }
};

class MemStatTable :
    public ResourceHashtable<MemStatTableKey, MemStatEntry*, 7919, AnyObj::C_HEAP,
                             mtInternal, MemStatTableKey::compute_hash>
{
public:

  void add(const FullMethodName& fmn, const ArenaState* state, const char* result) {
    assert_lock_strong(NMTCompilationCostHistory_lock);
    MemStatTableKey key(fmn, state->comp_type());
    MemStatEntry** pe = get(key);
    MemStatEntry* e = nullptr;
    if (pe == nullptr) {
      e = new MemStatEntry(fmn);
      put(key, e);
    } else {
      // Update existing entry
      e = *pe;
      assert(e != nullptr, "Sanity");
    }
    e->set_current_time();
    e->set_current_thread();
    e->inc_recompilation();
    e->set_result(result);

    e->set_from_state(state);
  }

  // Returns a C-heap-allocated SortMe array containing all entries from the table,
  // optionally filtered by entry size
  MemStatEntry** calc_flat_array(int& num, size_t min_size) {
    assert_lock_strong(NMTCompilationCostHistory_lock);

    const int num_all = number_of_entries();
    MemStatEntry** flat = NEW_C_HEAP_ARRAY(MemStatEntry*, num_all, mtInternal);
    int i = 0;
    auto do_f = [&] (const MemStatTableKey& ignored, MemStatEntry* e) {
      if (e->peak() >= min_size) {
        flat[i] = e;
        assert(i < num_all, "Sanity");
        i ++;
      }
    };
    iterate_all(do_f);
    if (min_size == 0) {
      assert(i == num_all, "Sanity");
    } else {
      assert(i <= num_all, "Sanity");
    }
    num = i;
    return flat;
  }
};

bool CompilationMemoryStatistic::_enabled = false;

static MemStatTable* _the_table = nullptr;

void CompilationMemoryStatistic::initialize() {
  assert(_enabled == false && _the_table == nullptr, "Only once");
  _the_table = new (mtCompiler) MemStatTable;
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

  {
    MutexLocker ml(NMTCompilationCostHistory_lock, Mutex::_no_safepoint_check_flag);
    assert(_the_table != nullptr, "not initialized");

    _the_table->add(fmn, arena_stat, result);
  }

  if (print) {
    // Pre-assemble string to prevent tearing
    stringStream ss;
    ss.print("%s (%d) Arena usage", compilertype2name(arena_stat->comp_type()), arena_stat->comp_id());
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

void CompilationMemoryStatistic::on_arena_chunk_allocation_0(size_t size, int tag, uint64_t* stamp) {
  assert(enabled(), "Not enabled?");

  CompilerThread* const th = Thread::current()->as_Compiler_thread();
  ArenaState* const arena_stat = th->arena_stat();
  if (arena_stat == nullptr) { // not started
    return;
  }
  if (arena_stat->limit_in_process()) {
    return; // avoid recursion on limit hit
  }

  bool hit_limit_before = arena_stat->hit_limit();

  if (arena_stat->on_arena_chunk_allocation(size, tag, stamp)) { // new peak?

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

      char message[1024] = "";

      // build up message if we need it later
      if (print || crash) {
        stringStream ss(message, sizeof(message));
        if (ct != compiler_none && name[0] != '\0') {
          ss.print("%s %s: ", compilertype2name(ct), name);
        }
        ss.print("Hit MemLimit %s(limit: %zu now: %zu)",
                 (hit_limit_before ? "again " : ""),
                 arena_stat->limit(), arena_stat->peak());
      }

      // log if needed
      if (print) {
        tty->print_raw(message);
        tty->cr();
      }

      // Crash out if needed
      if (crash) {
        report_fatal(OOM_HOTSPOT_ARENA, __FILE__, __LINE__, "%s", message);
      } else {
        inform_compilation_about_oom(ct);
      }

      arena_stat->set_limit_in_process(false);
    }
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

void CompilationMemoryStatistic::on_phase_start_0(int phasetraceid) {
  assert(enabled(), "Not enabled?");
  CompilerThread* const th = Thread::current()->as_Compiler_thread();
  ArenaState* const arena_stat = th->arena_stat();
  if (arena_stat == nullptr) { // not started
    return;
  }
  arena_stat->on_phase_start(phasetraceid);
}

void CompilationMemoryStatistic::on_phase_end_0(int phasetraceid) {
  assert(enabled(), "Not enabled?");
  CompilerThread* const th = Thread::current()->as_Compiler_thread();
  ArenaState* const arena_stat = th->arena_stat();
  if (arena_stat == nullptr) { // not started
    return;
  }
  arena_stat->on_phase_end(phasetraceid);
}

static inline ssize_t diff_entries_by_size(const MemStatEntry* e1, const MemStatEntry* e2) {
  return e1->compare_by_size(e2);
}

void CompilationMemoryStatistic::print_all_by_size(outputStream* st, bool human_readable, bool by_phase, size_t min_size) {

  MutexLocker ml(NMTCompilationCostHistory_lock, Mutex::_no_safepoint_check_flag);

  st->cr();
  st->print_cr("Compilation memory statistics v2");

  if (!enabled()) {
    st->print_cr("(unavailable)");
    return;
  }

  st->cr();

  MemStatEntry::print_legend(st);
  st->cr();

  if (min_size > 0) {
    st->print_cr(" (cutoff: %zu bytes)", min_size);
  }
  st->cr();

  MemStatEntry::print_header(st);

  MemStatEntry** filtered = nullptr;

  if (_the_table != nullptr) {
    // We sort with quicksort
    int num = 0;
    filtered = _the_table->calc_flat_array(num, min_size);
    if (min_size > 0) {
      st->print_cr("(%d/%d)", num, _the_table->number_of_entries());
    }
    if (num > 0) {
      QuickSort::sort(filtered, num, diff_entries_by_size);
      // Now print. Has to happen under lock protection too, since entries may be changed.
      for (int i = 0; i < num; i ++) {
        filtered[i]->print_on(st, human_readable, by_phase);
      }
    } else {
      st->print_cr("No entries.");
    }
  } else {
    st->print_cr("Not initialized.");
  }
  st->cr();

  FREE_C_HEAP_ARRAY(Entry, filtered);
}

const char* CompilationMemoryStatistic::failure_reason_memlimit() {
  static const char* const s = "hit memory limit while compiling";
  return s;
}

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

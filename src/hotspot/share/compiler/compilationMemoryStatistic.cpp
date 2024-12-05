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
#include "compiler/compilationMemoryStatistic.hpp"
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

union chunkstamp_t {
  uint64_t raw;
  struct {
    uint32_t tracked;
    uint16_t arena_tag;
    uint16_t phase_id;
  };
};
STATIC_ASSERT(sizeof(chunkstamp_t) == sizeof(chunkstamp_t::raw));

ArenaState::ArenaState() {
  reset();
}

void ArenaState::reset() {
  _current = 0;
  _peak = 0;
  _current_by_tag.clear();
  _peak_by_tag.clear();
  _limit = 0;
  _hit_limit = false;
  _limit_in_process = false;
  _live_nodes_at_peak = 0;
  _active = false;
}

void ArenaState::start(size_t limit) {
  reset();
  _active = true;
  _limit = limit;
}

void ArenaState::end() {
  _limit = 0;
  _hit_limit = false;
  _active = false;
}

void ArenaState::update_c2_node_count() {
  assert(_active, "compilaton has not yet started");
#ifdef COMPILER2
  CompilerThread* const th = Thread::current()->as_Compiler_thread();
  const CompileTask* const task = th->task();
  if (task != nullptr &&
      th->task()->compiler() != nullptr &&
      th->task()->compiler()->type() == compiler_c2) {
    const Compile* const comp = Compile::current();
    if (comp != nullptr) {
      _live_nodes_at_peak = comp->live_nodes();
    }
  }
#endif
}

#ifdef COMPILER2

CountersPerC2Phase::CountersPerC2Phase() {
  reset();
}

void CountersPerC2Phase::reset() {
  memset(_v, 0, sizeof(_v));
}

void CountersPerC2Phase::print_on(outputStream* st) {
  for (int phaseid = 0; phaseid < (int)Phase::PhaseTraceId::max_phase_timers; phaseid++) {
    // todo
  }
}

// Mark the start and end of a phase.
void ArenaState::on_c2_phase_start(Phase::PhaseTraceId id) {
  _phase_id_stack.push(id);
}

void ArenaState::on_c2_phase_end() {
  _phase_id_stack.pop();
}
#endif // COMPILER2

// Account an arena allocation. Returns true if new peak reached.
bool ArenaState::on_arena_chunk_allocation(size_t size, int tag, uint64_t* stamp) {
  assert(_active, "compilaton has not yet started");
  bool rc = false;
  // Update totals
  _current += size;
  _current_by_tag.add(tag, size);

  // Did we reach a peak?
  if (_current > _peak) {
    _peak = _current;
    update_c2_node_count();
    _peak_by_tag = _current_by_tag;
    rc = true;
    // Did we hit the memory limit?
    if (!_hit_limit && _limit > 0 && _peak > _limit) {
      _hit_limit = true;
    }
  }

  // calc stamp
  chunkstamp_t cs;
  cs.tracked = 1;
  cs.arena_tag = tag;
  cs.phase_id = (uint16_t)_phase_id_stack.top();
  *stamp = cs.raw;

  return rc;
}

// Account an arena deallocation.
void ArenaState::on_arena_chunk_deallocation(size_t size, uint64_t stamp) {
  assert(_active, "compilaton has not yet started");
  assert(_current >= size, "Negative overflow (d=%zd %zu %zu)", size, _current, _peak);
  _current -= size;
  chunkstamp_t cs;
  cs.raw = stamp;
  assert(cs.tracked == 1, "Sanity");
  _current_by_tag.sub(cs.arena_tag, size);
}

void ArenaState::print_on(outputStream* st) const {
  st->print("%zu [", _peak);
  bool printed = false;
  for (int tag = 0; tag < _peak_by_tag.element_count(); tag++) {
    if (_peak_by_tag.counter(tag) > 0) {
      st->print("%s%s %zu",
          (printed ? ", " : ""), _peak_by_tag.tag_name(tag), _peak_by_tag.counter(tag));
      printed = true;
    }
  }
  st->print("]");
#ifdef ASSERT
  st->print(" (%zu->%zu)", _peak, _current);
#endif
}

//////////////////////////
// Backend

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
class MemStatEntry : public CHeapObj<mtInternal> {
  const FullMethodName _method;
  CompilerType _comptype;
  int _comp_id;
  double _time;
  // How often this has been recompiled.
  int _num_recomp;
  // Compiling thread. Only for diagnostic purposes. Thread may not be alive anymore.
  const Thread* _thread;
  // active limit for this compilation, if any
  size_t _limit;

  // peak usage, bytes, over all arenas
  size_t _total;
  // usage per arena tag when total peaked
  ArenaCountersByTag _peak_by_tag;
  // number of nodes (c2 only) when total peaked
  unsigned _live_nodes_at_peak;
  const char* _result;

public:

  MemStatEntry(FullMethodName method)
    : _method(method), _comptype(compiler_c1), _comp_id(-1),
      _time(0), _num_recomp(0), _thread(nullptr), _limit(0),
      _total(0), _live_nodes_at_peak(0),
      _result(nullptr) {
    _peak_by_tag.clear();
  }

  void set_comp_id(int comp_id) { _comp_id = comp_id; }
  void set_comptype(CompilerType comptype) { _comptype = comptype; }
  void set_current_time() { _time = os::elapsedTime(); }
  void set_current_thread() { _thread = Thread::current(); }
  void set_limit(size_t limit) { _limit = limit; }
  void inc_recompilation() { _num_recomp++; }

  void set_total(size_t n) { _total = n; }
  void set_peak_by_tag(ArenaCountersByTag peak_by_tag) { _peak_by_tag = peak_by_tag; }
  void set_live_nodes_at_peak(unsigned n) { _live_nodes_at_peak = n; }

  void set_result(const char* s) { _result = s; }

  size_t total() const { return _total; }

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

  void print_on(outputStream* st, bool human_readable) const {
    int col = 0;

    // Total
    if (human_readable) {
      st->print(PROPERFMT " ", PROPERFMTARGS(_total));
    } else {
      st->print("%zu ", _total);
    }
    col += 10; st->fill_to(col);

    for (int tag = 0; tag < Arena::tag_count(); tag++) {
      if (human_readable) {
        st->print(PROPERFMT " ", PROPERFMTARGS(_peak_by_tag.counter(tag)));
      } else {
        st->print("%zu ", _peak_by_tag.counter(tag));
      }
      col += 10; st->fill_to(col);
    }

    // result?
    st->print("%s ", _result ? _result : "");
    col += 8; st->fill_to(col);

    // Number of Nodes when memory peaked
    if (_live_nodes_at_peak > 0) {
      st->print("%u ", _live_nodes_at_peak);
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
    st->print("%s ", compilertype2name(_comptype));
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
  }

  int compare_by_size(const MemStatEntry* b) const {
    const size_t x1 = b->_total;
    const size_t x2 = _total;
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

  void add(const FullMethodName& fmn, CompilerType comptype, int compid,
           size_t total, ArenaCountersByTag peak_by_tag,
           unsigned live_nodes_at_peak, size_t limit, const char* result) {
    assert_lock_strong(NMTCompilationCostHistory_lock);
    MemStatTableKey key(fmn, comptype);
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
    e->set_comp_id(compid);
    e->set_current_time();
    e->set_current_thread();
    e->set_comptype(comptype);
    e->inc_recompilation();
    e->set_total(total);
    e->set_peak_by_tag(peak_by_tag);
    e->set_live_nodes_at_peak(live_nodes_at_peak);
    e->set_limit(limit);
    e->set_result(result);
  }

  // Returns a C-heap-allocated SortMe array containing all entries from the table,
  // optionally filtered by entry size
  MemStatEntry** calc_flat_array(int& num, size_t min_size) {
    assert_lock_strong(NMTCompilationCostHistory_lock);

    const int num_all = number_of_entries();
    MemStatEntry** flat = NEW_C_HEAP_ARRAY(MemStatEntry*, num_all, mtInternal);
    int i = 0;
    auto do_f = [&] (const MemStatTableKey& ignored, MemStatEntry* e) {
      if (e->total() >= min_size) {
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
  const size_t limit = directive->mem_limit();
  Thread::current()->as_Compiler_thread()->arena_stat()->start(limit);
}

void CompilationMemoryStatistic::on_end_compilation() {
  assert(enabled(), "Not enabled?");
  ResourceMark rm;
  CompilerThread* const th = Thread::current()->as_Compiler_thread();
  ArenaState* const arena_stat = th->arena_stat();
  CompileTask* const task = th->task();
  const CompilerType ct = task->compiler()->type();
  const int comp_id = task->compile_id();

  const Method* const m = th->task()->method();
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

    _the_table->add(fmn, ct, comp_id,
                    arena_stat->peak(), // total
                    arena_stat->peak_by_tag(),
                    arena_stat->live_nodes_at_peak(),
                    arena_stat->limit(),
                    result);
  }

  if (print) {
    // Pre-assemble string to prevent tearing
    char buf[2048];
    stringStream ss(buf, sizeof(buf));
    ss.print("%s (%d) Arena usage", compilertype2name(ct), comp_id);
    fmn.print_on(&ss);
    ss.print_raw(": ");
    arena_stat->print_on(&ss);
    ss.cr();
    tty->print_raw(buf);
  }

  arena_stat->end(); // reset things
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

void CompilationMemoryStatistic::on_arena_chunk_allocation(size_t size, int tag, uint64_t* stamp) {
  assert(enabled(), "Not enabled?");
  CompilerThread* const th = Thread::current()->as_Compiler_thread();

  (*stamp) = 0; // defaults to "not tracked"

  ArenaState* const arena_stat = th->arena_stat();
  if (!arena_stat->is_active()) {
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

void CompilationMemoryStatistic::on_arena_chunk_deallocation(size_t size, uint64_t stamp) {
  assert(enabled(), "Not enabled?");
  CompilerThread* const th = Thread::current()->as_Compiler_thread();

  ArenaState* const arena_stat = th->arena_stat();
  if (!arena_stat->is_active()) {
    return;
  }
  if (arena_stat->limit_in_process()) {
    return; // avoid recursion on limit hit
  }

  arena_stat->on_arena_chunk_deallocation(size, stamp);
}

#ifdef COMPILER2
// C2 only: inform statistic about start and end of a compilation phase
void CompilationMemoryStatistic::on_c2_phase_start_0(Phase::PhaseTraceId id) {
  assert(enabled(), "Not enabled?");
  CompilerThread* const th = Thread::current()->as_Compiler_thread();
  ArenaState* const arena_stat = th->arena_stat();
  assert(arena_stat != nullptr, "A compilation should be in process?");
  arena_stat->on_c2_phase_start(id);
}

void CompilationMemoryStatistic::on_c2_phase_end_0() {
  assert(enabled(), "Not enabled?");
  CompilerThread* const th = Thread::current()->as_Compiler_thread();
  ArenaState* const arena_stat = th->arena_stat();
  assert(arena_stat != nullptr, "A compilation should be in process?");
  arena_stat->on_c2_phase_end();
}
#endif

static inline ssize_t diff_entries_by_size(const MemStatEntry* e1, const MemStatEntry* e2) {
  return e1->compare_by_size(e2);
}

void CompilationMemoryStatistic::print_all_by_size(outputStream* st, bool human_readable, size_t min_size) {

  MutexLocker ml(NMTCompilationCostHistory_lock, Mutex::_no_safepoint_check_flag);

  st->cr();
  st->print_cr("Compilation memory statistics");

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
        filtered[i]->print_on(st, human_readable);
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

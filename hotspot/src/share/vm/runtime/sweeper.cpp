/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "code/codeCache.hpp"
#include "code/compiledIC.hpp"
#include "code/icBuffer.hpp"
#include "code/nmethod.hpp"
#include "compiler/compileBroker.hpp"
#include "memory/resourceArea.hpp"
#include "oops/method.hpp"
#include "runtime/atomic.hpp"
#include "runtime/compilationPolicy.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.hpp"
#include "runtime/sweeper.hpp"
#include "runtime/vm_operations.hpp"
#include "trace/tracing.hpp"
#include "utilities/events.hpp"
#include "utilities/xmlstream.hpp"

#ifdef ASSERT

#define SWEEP(nm) record_sweep(nm, __LINE__)
// Sweeper logging code
class SweeperRecord {
 public:
  int traversal;
  int invocation;
  int compile_id;
  long traversal_mark;
  int state;
  const char* kind;
  address vep;
  address uep;
  int line;

  void print() {
      tty->print_cr("traversal = %d invocation = %d compile_id = %d %s uep = " PTR_FORMAT " vep = "
                    PTR_FORMAT " state = %d traversal_mark %d line = %d",
                    traversal,
                    invocation,
                    compile_id,
                    kind == NULL ? "" : kind,
                    uep,
                    vep,
                    state,
                    traversal_mark,
                    line);
  }
};

static int _sweep_index = 0;
static SweeperRecord* _records = NULL;

void NMethodSweeper::report_events(int id, address entry) {
  if (_records != NULL) {
    for (int i = _sweep_index; i < SweeperLogEntries; i++) {
      if (_records[i].uep == entry ||
          _records[i].vep == entry ||
          _records[i].compile_id == id) {
        _records[i].print();
      }
    }
    for (int i = 0; i < _sweep_index; i++) {
      if (_records[i].uep == entry ||
          _records[i].vep == entry ||
          _records[i].compile_id == id) {
        _records[i].print();
      }
    }
  }
}

void NMethodSweeper::report_events() {
  if (_records != NULL) {
    for (int i = _sweep_index; i < SweeperLogEntries; i++) {
      // skip empty records
      if (_records[i].vep == NULL) continue;
      _records[i].print();
    }
    for (int i = 0; i < _sweep_index; i++) {
      // skip empty records
      if (_records[i].vep == NULL) continue;
      _records[i].print();
    }
  }
}

void NMethodSweeper::record_sweep(nmethod* nm, int line) {
  if (_records != NULL) {
    _records[_sweep_index].traversal = _traversals;
    _records[_sweep_index].traversal_mark = nm->_stack_traversal_mark;
    _records[_sweep_index].invocation = _invocations;
    _records[_sweep_index].compile_id = nm->compile_id();
    _records[_sweep_index].kind = nm->compile_kind();
    _records[_sweep_index].state = nm->_state;
    _records[_sweep_index].vep = nm->verified_entry_point();
    _records[_sweep_index].uep = nm->entry_point();
    _records[_sweep_index].line = line;

    _sweep_index = (_sweep_index + 1) % SweeperLogEntries;
  }
}
#else
#define SWEEP(nm)
#endif


long      NMethodSweeper::_traversals = 0;   // No. of stack traversals performed
nmethod*  NMethodSweeper::_current = NULL;   // Current nmethod
int       NMethodSweeper::_seen = 0 ;        // No. of nmethods we have currently processed in current pass of CodeCache
int       NMethodSweeper::_flushed_count = 0;   // Nof. nmethods flushed in current sweep
int       NMethodSweeper::_zombified_count = 0; // Nof. nmethods made zombie in current sweep
int       NMethodSweeper::_marked_count = 0;    // Nof. nmethods marked for reclaim in current sweep

volatile int NMethodSweeper::_invocations = 0;   // No. of invocations left until we are completed with this pass
volatile int NMethodSweeper::_sweep_started = 0; // Whether a sweep is in progress.

jint      NMethodSweeper::_locked_seen = 0;
jint      NMethodSweeper::_not_entrant_seen_on_stack = 0;
bool      NMethodSweeper::_resweep = false;
jint      NMethodSweeper::_flush_token = 0;
jlong     NMethodSweeper::_last_full_flush_time = 0;
int       NMethodSweeper::_highest_marked = 0;
int       NMethodSweeper::_dead_compile_ids = 0;
long      NMethodSweeper::_last_flush_traversal_id = 0;

int       NMethodSweeper::_number_of_flushes = 0; // Total of full traversals caused by full cache
int       NMethodSweeper::_total_nof_methods_reclaimed = 0;
jlong     NMethodSweeper::_total_time_sweeping = 0;
jlong     NMethodSweeper::_total_time_this_sweep = 0;
jlong     NMethodSweeper::_peak_sweep_time = 0;
jlong     NMethodSweeper::_peak_sweep_fraction_time = 0;
jlong     NMethodSweeper::_total_disconnect_time = 0;
jlong     NMethodSweeper::_peak_disconnect_time = 0;

class MarkActivationClosure: public CodeBlobClosure {
public:
  virtual void do_code_blob(CodeBlob* cb) {
    // If we see an activation belonging to a non_entrant nmethod, we mark it.
    if (cb->is_nmethod() && ((nmethod*)cb)->is_not_entrant()) {
      ((nmethod*)cb)->mark_as_seen_on_stack();
    }
  }
};
static MarkActivationClosure mark_activation_closure;

bool NMethodSweeper::sweep_in_progress() {
  return (_current != NULL);
}

void NMethodSweeper::scan_stacks() {
  assert(SafepointSynchronize::is_at_safepoint(), "must be executed at a safepoint");
  if (!MethodFlushing) return;

  // No need to synchronize access, since this is always executed at a
  // safepoint.

  // Make sure CompiledIC_lock in unlocked, since we might update some
  // inline caches. If it is, we just bail-out and try later.
  if (CompiledIC_lock->is_locked() || Patching_lock->is_locked()) return;

  // Check for restart
  assert(CodeCache::find_blob_unsafe(_current) == _current, "Sweeper nmethod cached state invalid");
  if (!sweep_in_progress() && _resweep) {
    _seen        = 0;
    _invocations = NmethodSweepFraction;
    _current     = CodeCache::first_nmethod();
    _traversals  += 1;
    _total_time_this_sweep = 0;

    if (PrintMethodFlushing) {
      tty->print_cr("### Sweep: stack traversal %d", _traversals);
    }
    Threads::nmethods_do(&mark_activation_closure);

    // reset the flags since we started a scan from the beginning.
    _resweep = false;
    _locked_seen = 0;
    _not_entrant_seen_on_stack = 0;
  }

  if (UseCodeCacheFlushing) {
    // only allow new flushes after the interval is complete.
    jlong now           = os::javaTimeMillis();
    jlong max_interval  = (jlong)MinCodeCacheFlushingInterval * (jlong)1000;
    jlong curr_interval = now - _last_full_flush_time;
    if (curr_interval > max_interval) {
      _flush_token = 0;
    }

    if (!CodeCache::needs_flushing() && !CompileBroker::should_compile_new_jobs()) {
      CompileBroker::set_should_compile_new_jobs(CompileBroker::run_compilation);
      log_sweep("restart_compiler");
    }
  }
}

void NMethodSweeper::possibly_sweep() {
  assert(JavaThread::current()->thread_state() == _thread_in_vm, "must run in vm mode");
  if (!MethodFlushing || !sweep_in_progress()) return;

  if (_invocations > 0) {
    // Only one thread at a time will sweep
    jint old = Atomic::cmpxchg( 1, &_sweep_started, 0 );
    if (old != 0) {
      return;
    }
#ifdef ASSERT
    if (LogSweeper && _records == NULL) {
      // Create the ring buffer for the logging code
      _records = NEW_C_HEAP_ARRAY(SweeperRecord, SweeperLogEntries, mtGC);
      memset(_records, 0, sizeof(SweeperRecord) * SweeperLogEntries);
    }
#endif
    if (_invocations > 0) {
      sweep_code_cache();
      _invocations--;
    }
    _sweep_started = 0;
  }
}

void NMethodSweeper::sweep_code_cache() {

  jlong sweep_start_counter = os::elapsed_counter();

  _flushed_count   = 0;
  _zombified_count = 0;
  _marked_count    = 0;

  if (PrintMethodFlushing && Verbose) {
    tty->print_cr("### Sweep at %d out of %d. Invocations left: %d", _seen, CodeCache::nof_nmethods(), _invocations);
  }

  if (!CompileBroker::should_compile_new_jobs()) {
    // If we have turned off compilations we might as well do full sweeps
    // in order to reach the clean state faster. Otherwise the sleeping compiler
    // threads will slow down sweeping. After a few iterations the cache
    // will be clean and sweeping stops (_resweep will not be set)
    _invocations = 1;
  }

  // We want to visit all nmethods after NmethodSweepFraction
  // invocations so divide the remaining number of nmethods by the
  // remaining number of invocations.  This is only an estimate since
  // the number of nmethods changes during the sweep so the final
  // stage must iterate until it there are no more nmethods.
  int todo = (CodeCache::nof_nmethods() - _seen) / _invocations;

  assert(!SafepointSynchronize::is_at_safepoint(), "should not be in safepoint when we get here");
  assert(!CodeCache_lock->owned_by_self(), "just checking");

  {
    MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);

    // The last invocation iterates until there are no more nmethods
    for (int i = 0; (i < todo || _invocations == 1) && _current != NULL; i++) {
      if (SafepointSynchronize::is_synchronizing()) { // Safepoint request
        if (PrintMethodFlushing && Verbose) {
          tty->print_cr("### Sweep at %d out of %d, invocation: %d, yielding to safepoint", _seen, CodeCache::nof_nmethods(), _invocations);
        }
        MutexUnlockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);

        assert(Thread::current()->is_Java_thread(), "should be java thread");
        JavaThread* thread = (JavaThread*)Thread::current();
        ThreadBlockInVM tbivm(thread);
        thread->java_suspend_self();
      }
      // Since we will give up the CodeCache_lock, always skip ahead
      // to the next nmethod.  Other blobs can be deleted by other
      // threads but nmethods are only reclaimed by the sweeper.
      nmethod* next = CodeCache::next_nmethod(_current);

      // Now ready to process nmethod and give up CodeCache_lock
      {
        MutexUnlockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
        process_nmethod(_current);
      }
      _seen++;
      _current = next;
    }
  }

  assert(_invocations > 1 || _current == NULL, "must have scanned the whole cache");

  if (!sweep_in_progress() && !_resweep && (_locked_seen || _not_entrant_seen_on_stack)) {
    // we've completed a scan without making progress but there were
    // nmethods we were unable to process either because they were
    // locked or were still on stack.  We don't have to aggresively
    // clean them up so just stop scanning.  We could scan once more
    // but that complicates the control logic and it's unlikely to
    // matter much.
    if (PrintMethodFlushing) {
      tty->print_cr("### Couldn't make progress on some nmethods so stopping sweep");
    }
  }

  jlong sweep_end_counter = os::elapsed_counter();
  jlong sweep_time = sweep_end_counter - sweep_start_counter;
  _total_time_sweeping  += sweep_time;
  _total_time_this_sweep += sweep_time;
  _peak_sweep_fraction_time = MAX2(sweep_time, _peak_sweep_fraction_time);
  _total_nof_methods_reclaimed += _flushed_count;

  EventSweepCodeCache event(UNTIMED);
  if (event.should_commit()) {
    event.set_starttime(sweep_start_counter);
    event.set_endtime(sweep_end_counter);
    event.set_sweepIndex(_traversals);
    event.set_sweepFractionIndex(NmethodSweepFraction - _invocations + 1);
    event.set_sweptCount(todo);
    event.set_flushedCount(_flushed_count);
    event.set_markedCount(_marked_count);
    event.set_zombifiedCount(_zombified_count);
    event.commit();
  }

#ifdef ASSERT
  if(PrintMethodFlushing) {
    tty->print_cr("### sweeper:      sweep time(%d): " INT64_FORMAT, _invocations, (jlong)sweep_time);
  }
#endif

  if (_invocations == 1) {
    _peak_sweep_time = MAX2(_peak_sweep_time, _total_time_this_sweep);
    log_sweep("finished");
  }

  // Sweeper is the only case where memory is released,
  // check here if it is time to restart the compiler.
  if (UseCodeCacheFlushing && !CompileBroker::should_compile_new_jobs() && !CodeCache::needs_flushing()) {
    CompileBroker::set_should_compile_new_jobs(CompileBroker::run_compilation);
    log_sweep("restart_compiler");
  }
}

class NMethodMarker: public StackObj {
 private:
  CompilerThread* _thread;
 public:
  NMethodMarker(nmethod* nm) {
    _thread = CompilerThread::current();
    if (!nm->is_zombie() && !nm->is_unloaded()) {
      // Only expose live nmethods for scanning
    _thread->set_scanned_nmethod(nm);
  }
  }
  ~NMethodMarker() {
    _thread->set_scanned_nmethod(NULL);
  }
};

void NMethodSweeper::release_nmethod(nmethod *nm) {
  // Clean up any CompiledICHolders
  {
    ResourceMark rm;
    MutexLocker ml_patch(CompiledIC_lock);
    RelocIterator iter(nm);
    while (iter.next()) {
      if (iter.type() == relocInfo::virtual_call_type) {
        CompiledIC::cleanup_call_site(iter.virtual_call_reloc());
      }
    }
  }

  MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
  nm->flush();
}

void NMethodSweeper::process_nmethod(nmethod *nm) {
  assert(!CodeCache_lock->owned_by_self(), "just checking");

  // Make sure this nmethod doesn't get unloaded during the scan,
  // since the locks acquired below might safepoint.
  NMethodMarker nmm(nm);

  SWEEP(nm);

  // Skip methods that are currently referenced by the VM
  if (nm->is_locked_by_vm()) {
    // But still remember to clean-up inline caches for alive nmethods
    if (nm->is_alive()) {
      // Clean-up all inline caches that points to zombie/non-reentrant methods
      MutexLocker cl(CompiledIC_lock);
      nm->cleanup_inline_caches();
      SWEEP(nm);
    } else {
      _locked_seen++;
      SWEEP(nm);
    }
    return;
  }

  if (nm->is_zombie()) {
    // If it is first time, we see nmethod then we mark it. Otherwise,
    // we reclame it. When we have seen a zombie method twice, we know that
    // there are no inline caches that refer to it.
    if (nm->is_marked_for_reclamation()) {
      assert(!nm->is_locked_by_vm(), "must not flush locked nmethods");
      if (PrintMethodFlushing && Verbose) {
        tty->print_cr("### Nmethod %3d/" PTR_FORMAT " (marked for reclamation) being flushed", nm->compile_id(), nm);
      }
      release_nmethod(nm);
      _flushed_count++;
    } else {
      if (PrintMethodFlushing && Verbose) {
        tty->print_cr("### Nmethod %3d/" PTR_FORMAT " (zombie) being marked for reclamation", nm->compile_id(), nm);
      }
      nm->mark_for_reclamation();
      _resweep = true;
      _marked_count++;
      SWEEP(nm);
    }
  } else if (nm->is_not_entrant()) {
    // If there is no current activations of this method on the
    // stack we can safely convert it to a zombie method
    if (nm->can_not_entrant_be_converted()) {
      if (PrintMethodFlushing && Verbose) {
        tty->print_cr("### Nmethod %3d/" PTR_FORMAT " (not entrant) being made zombie", nm->compile_id(), nm);
      }
      nm->make_zombie();
      _resweep = true;
      _zombified_count++;
      SWEEP(nm);
    } else {
      // Still alive, clean up its inline caches
      MutexLocker cl(CompiledIC_lock);
      nm->cleanup_inline_caches();
      // we coudn't transition this nmethod so don't immediately
      // request a rescan.  If this method stays on the stack for a
      // long time we don't want to keep rescanning the code cache.
      _not_entrant_seen_on_stack++;
      SWEEP(nm);
    }
  } else if (nm->is_unloaded()) {
    // Unloaded code, just make it a zombie
    if (PrintMethodFlushing && Verbose)
      tty->print_cr("### Nmethod %3d/" PTR_FORMAT " (unloaded) being made zombie", nm->compile_id(), nm);

    if (nm->is_osr_method()) {
      SWEEP(nm);
      // No inline caches will ever point to osr methods, so we can just remove it
      release_nmethod(nm);
      _flushed_count++;
    } else {
      nm->make_zombie();
      _resweep = true;
      _zombified_count++;
      SWEEP(nm);
    }
  } else {
    assert(nm->is_alive(), "should be alive");

    if (UseCodeCacheFlushing) {
      if (nm->is_speculatively_disconnected() && !nm->is_locked_by_vm() && !nm->is_osr_method() &&
          (_traversals > _last_flush_traversal_id + 2) && (nm->compile_id() < _highest_marked)) {
        // This method has not been called since the forced cleanup happened
        nm->make_not_entrant();
      }
    }

    // Clean-up all inline caches that points to zombie/non-reentrant methods
    MutexLocker cl(CompiledIC_lock);
    nm->cleanup_inline_caches();
    SWEEP(nm);
  }
}

// Code cache unloading: when compilers notice the code cache is getting full,
// they will call a vm op that comes here. This code attempts to speculatively
// unload the oldest half of the nmethods (based on the compile job id) by
// saving the old code in a list in the CodeCache. Then
// execution resumes. If a method so marked is not called by the second sweeper
// stack traversal after the current one, the nmethod will be marked non-entrant and
// got rid of by normal sweeping. If the method is called, the Method*'s
// _code field is restored and the Method*/nmethod
// go back to their normal state.
void NMethodSweeper::handle_full_code_cache(bool is_full) {

  if (is_full) {
    // Since code cache is full, immediately stop new compiles
    if (CompileBroker::set_should_compile_new_jobs(CompileBroker::stop_compilation)) {
      log_sweep("disable_compiler");
    }
  }

  // Make sure only one thread can flush
  // The token is reset after CodeCacheMinimumFlushInterval in scan stacks,
  // no need to check the timeout here.
  jint old = Atomic::cmpxchg( 1, &_flush_token, 0 );
  if (old != 0) {
    return;
  }

  VM_HandleFullCodeCache op(is_full);
  VMThread::execute(&op);

  // resweep again as soon as possible
  _resweep = true;
}

void NMethodSweeper::speculative_disconnect_nmethods(bool is_full) {
  // If there was a race in detecting full code cache, only run
  // one vm op for it or keep the compiler shut off

  jlong disconnect_start_counter = os::elapsed_counter();

  // Traverse the code cache trying to dump the oldest nmethods
  int curr_max_comp_id = CompileBroker::get_compilation_id();
  int flush_target = ((curr_max_comp_id - _dead_compile_ids) / CodeCacheFlushingFraction) + _dead_compile_ids;

  log_sweep("start_cleaning");

  nmethod* nm = CodeCache::alive_nmethod(CodeCache::first());
  jint disconnected = 0;
  jint made_not_entrant  = 0;
  jint nmethod_count = 0;

  while ((nm != NULL)){
    int curr_comp_id = nm->compile_id();

    // OSR methods cannot be flushed like this. Also, don't flush native methods
    // since they are part of the JDK in most cases
    if (!nm->is_osr_method() && !nm->is_locked_by_vm() && !nm->is_native_method()) {

      // only count methods that can be speculatively disconnected
      nmethod_count++;

      if (nm->is_in_use() && (curr_comp_id < flush_target)) {
        if ((nm->method()->code() == nm)) {
          // This method has not been previously considered for
          // unloading or it was restored already
          CodeCache::speculatively_disconnect(nm);
          disconnected++;
        } else if (nm->is_speculatively_disconnected()) {
          // This method was previously considered for preemptive unloading and was not called since then
          CompilationPolicy::policy()->delay_compilation(nm->method());
          nm->make_not_entrant();
          made_not_entrant++;
        }

        if (curr_comp_id > _highest_marked) {
          _highest_marked = curr_comp_id;
        }
      }
    }
    nm = CodeCache::alive_nmethod(CodeCache::next(nm));
  }

  // remember how many compile_ids wheren't seen last flush.
  _dead_compile_ids = curr_max_comp_id - nmethod_count;

  log_sweep("stop_cleaning",
                       "disconnected='" UINT32_FORMAT "' made_not_entrant='" UINT32_FORMAT "'",
                       disconnected, made_not_entrant);

  // Shut off compiler. Sweeper will start over with a new stack scan and
  // traversal cycle and turn it back on if it clears enough space.
  if (is_full) {
    _last_full_flush_time = os::javaTimeMillis();
  }

  jlong disconnect_end_counter = os::elapsed_counter();
  jlong disconnect_time = disconnect_end_counter - disconnect_start_counter;
  _total_disconnect_time += disconnect_time;
  _peak_disconnect_time = MAX2(disconnect_time, _peak_disconnect_time);

  EventCleanCodeCache event(UNTIMED);
  if (event.should_commit()) {
    event.set_starttime(disconnect_start_counter);
    event.set_endtime(disconnect_end_counter);
    event.set_disconnectedCount(disconnected);
    event.set_madeNonEntrantCount(made_not_entrant);
    event.commit();
  }
  _number_of_flushes++;

  // After two more traversals the sweeper will get rid of unrestored nmethods
  _last_flush_traversal_id = _traversals;
  _resweep = true;
#ifdef ASSERT

  if(PrintMethodFlushing && Verbose) {
    tty->print_cr("### sweeper: unload time: " INT64_FORMAT, (jlong)disconnect_time);
  }
#endif
}


// Print out some state information about the current sweep and the
// state of the code cache if it's requested.
void NMethodSweeper::log_sweep(const char* msg, const char* format, ...) {
  if (PrintMethodFlushing) {
    stringStream s;
    // Dump code cache state into a buffer before locking the tty,
    // because log_state() will use locks causing lock conflicts.
    CodeCache::log_state(&s);

    ttyLocker ttyl;
    tty->print("### sweeper: %s ", msg);
    if (format != NULL) {
      va_list ap;
      va_start(ap, format);
      tty->vprint(format, ap);
      va_end(ap);
    }
    tty->print_cr(s.as_string());
  }

  if (LogCompilation && (xtty != NULL)) {
    stringStream s;
    // Dump code cache state into a buffer before locking the tty,
    // because log_state() will use locks causing lock conflicts.
    CodeCache::log_state(&s);

    ttyLocker ttyl;
    xtty->begin_elem("sweeper state='%s' traversals='" INTX_FORMAT "' ", msg, (intx)traversal_count());
    if (format != NULL) {
      va_list ap;
      va_start(ap, format);
      xtty->vprint(format, ap);
      va_end(ap);
    }
    xtty->print(s.as_string());
    xtty->stamp();
    xtty->end_elem();
  }
}

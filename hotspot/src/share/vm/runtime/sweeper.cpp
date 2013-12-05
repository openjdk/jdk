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
#include "utilities/ticks.inline.hpp"
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
    _records[_sweep_index].invocation = _sweep_fractions_left;
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

nmethod* NMethodSweeper::_current                      = NULL; // Current nmethod
long     NMethodSweeper::_traversals                   = 0;    // Stack scan count, also sweep ID.
long     NMethodSweeper::_time_counter                 = 0;    // Virtual time used to periodically invoke sweeper
long     NMethodSweeper::_last_sweep                   = 0;    // Value of _time_counter when the last sweep happened
int      NMethodSweeper::_seen                         = 0;    // Nof. nmethod we have currently processed in current pass of CodeCache
int      NMethodSweeper::_flushed_count                = 0;    // Nof. nmethods flushed in current sweep
int      NMethodSweeper::_zombified_count              = 0;    // Nof. nmethods made zombie in current sweep
int      NMethodSweeper::_marked_for_reclamation_count = 0;    // Nof. nmethods marked for reclaim in current sweep

volatile bool NMethodSweeper::_should_sweep            = true; // Indicates if we should invoke the sweeper
volatile int  NMethodSweeper::_sweep_fractions_left    = 0;    // Nof. invocations left until we are completed with this pass
volatile int  NMethodSweeper::_sweep_started           = 0;    // Flag to control conc sweeper
volatile int  NMethodSweeper::_bytes_changed           = 0;    // Counts the total nmethod size if the nmethod changed from:
                                                               //   1) alive       -> not_entrant
                                                               //   2) not_entrant -> zombie
                                                               //   3) zombie      -> marked_for_reclamation

int   NMethodSweeper::_total_nof_methods_reclaimed     = 0;    // Accumulated nof methods flushed
Tickspan NMethodSweeper::_total_time_sweeping;                 // Accumulated time sweeping
Tickspan NMethodSweeper::_total_time_this_sweep;               // Total time this sweep
Tickspan NMethodSweeper::_peak_sweep_time;                     // Peak time for a full sweep
Tickspan NMethodSweeper::_peak_sweep_fraction_time;            // Peak time sweeping one fraction
int   NMethodSweeper::_hotness_counter_reset_val       = 0;


class MarkActivationClosure: public CodeBlobClosure {
public:
  virtual void do_code_blob(CodeBlob* cb) {
    if (cb->is_nmethod()) {
      nmethod* nm = (nmethod*)cb;
      nm->set_hotness_counter(NMethodSweeper::hotness_counter_reset_val());
      // If we see an activation belonging to a non_entrant nmethod, we mark it.
      if (nm->is_not_entrant()) {
        nm->mark_as_seen_on_stack();
      }
    }
  }
};
static MarkActivationClosure mark_activation_closure;

class SetHotnessClosure: public CodeBlobClosure {
public:
  virtual void do_code_blob(CodeBlob* cb) {
    if (cb->is_nmethod()) {
      nmethod* nm = (nmethod*)cb;
      nm->set_hotness_counter(NMethodSweeper::hotness_counter_reset_val());
    }
  }
};
static SetHotnessClosure set_hotness_closure;


int NMethodSweeper::hotness_counter_reset_val() {
  if (_hotness_counter_reset_val == 0) {
    _hotness_counter_reset_val = (ReservedCodeCacheSize < M) ? 1 : (ReservedCodeCacheSize / M) * 2;
  }
  return _hotness_counter_reset_val;
}
bool NMethodSweeper::sweep_in_progress() {
  return (_current != NULL);
}

// Scans the stacks of all Java threads and marks activations of not-entrant methods.
// No need to synchronize access, since 'mark_active_nmethods' is always executed at a
// safepoint.
void NMethodSweeper::mark_active_nmethods() {
  assert(SafepointSynchronize::is_at_safepoint(), "must be executed at a safepoint");
  // If we do not want to reclaim not-entrant or zombie methods there is no need
  // to scan stacks
  if (!MethodFlushing) {
    return;
  }

  // Increase time so that we can estimate when to invoke the sweeper again.
  _time_counter++;

  // Check for restart
  assert(CodeCache::find_blob_unsafe(_current) == _current, "Sweeper nmethod cached state invalid");
  if (!sweep_in_progress()) {
    _seen = 0;
    _sweep_fractions_left = NmethodSweepFraction;
    _current = CodeCache::first_nmethod();
    _traversals += 1;
    _total_time_this_sweep = Tickspan();

    if (PrintMethodFlushing) {
      tty->print_cr("### Sweep: stack traversal %d", _traversals);
    }
    Threads::nmethods_do(&mark_activation_closure);

  } else {
    // Only set hotness counter
    Threads::nmethods_do(&set_hotness_closure);
  }

  OrderAccess::storestore();
}
/**
 * This function invokes the sweeper if at least one of the three conditions is met:
 *    (1) The code cache is getting full
 *    (2) There are sufficient state changes in/since the last sweep.
 *    (3) We have not been sweeping for 'some time'
 */
void NMethodSweeper::possibly_sweep() {
  assert(JavaThread::current()->thread_state() == _thread_in_vm, "must run in vm mode");
  // Only compiler threads are allowed to sweep
  if (!MethodFlushing || !sweep_in_progress() || !Thread::current()->is_Compiler_thread()) {
    return;
  }

  // If there was no state change while nmethod sweeping, 'should_sweep' will be false.
  // This is one of the two places where should_sweep can be set to true. The general
  // idea is as follows: If there is enough free space in the code cache, there is no
  // need to invoke the sweeper. The following formula (which determines whether to invoke
  // the sweeper or not) depends on the assumption that for larger ReservedCodeCacheSizes
  // we need less frequent sweeps than for smaller ReservedCodecCacheSizes. Furthermore,
  // the formula considers how much space in the code cache is currently used. Here are
  // some examples that will (hopefully) help in understanding.
  //
  // Small ReservedCodeCacheSizes:  (e.g., < 16M) We invoke the sweeper every time, since
  //                                              the result of the division is 0. This
  //                                              keeps the used code cache size small
  //                                              (important for embedded Java)
  // Large ReservedCodeCacheSize :  (e.g., 256M + code cache is 10% full). The formula
  //                                              computes: (256 / 16) - 1 = 15
  //                                              As a result, we invoke the sweeper after
  //                                              15 invocations of 'mark_active_nmethods.
  // Large ReservedCodeCacheSize:   (e.g., 256M + code Cache is 90% full). The formula
  //                                              computes: (256 / 16) - 10 = 6.
  if (!_should_sweep) {
    int time_since_last_sweep = _time_counter - _last_sweep;
    double wait_until_next_sweep = (ReservedCodeCacheSize / (16 * M)) - time_since_last_sweep -
                                CodeCache::reverse_free_ratio();

    if ((wait_until_next_sweep <= 0.0) || !CompileBroker::should_compile_new_jobs()) {
      _should_sweep = true;
    }
  }

  if (_should_sweep && _sweep_fractions_left > 0) {
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

    if (_sweep_fractions_left > 0) {
      sweep_code_cache();
      _sweep_fractions_left--;
    }

    // We are done with sweeping the code cache once.
    if (_sweep_fractions_left == 0) {
      _last_sweep = _time_counter;
      // Reset flag; temporarily disables sweeper
      _should_sweep = false;
      // If there was enough state change, 'possibly_enable_sweeper()'
      // sets '_should_sweep' to true
      possibly_enable_sweeper();
      // Reset _bytes_changed only if there was enough state change. _bytes_changed
      // can further increase by calls to 'report_state_change'.
      if (_should_sweep) {
        _bytes_changed = 0;
      }
    }
    _sweep_started = 0;
  }
}

void NMethodSweeper::sweep_code_cache() {
  Ticks sweep_start_counter = Ticks::now();

  _flushed_count                = 0;
  _zombified_count              = 0;
  _marked_for_reclamation_count = 0;

  if (PrintMethodFlushing && Verbose) {
    tty->print_cr("### Sweep at %d out of %d. Invocations left: %d", _seen, CodeCache::nof_nmethods(), _sweep_fractions_left);
  }

  if (!CompileBroker::should_compile_new_jobs()) {
    // If we have turned off compilations we might as well do full sweeps
    // in order to reach the clean state faster. Otherwise the sleeping compiler
    // threads will slow down sweeping.
    _sweep_fractions_left = 1;
  }

  // We want to visit all nmethods after NmethodSweepFraction
  // invocations so divide the remaining number of nmethods by the
  // remaining number of invocations.  This is only an estimate since
  // the number of nmethods changes during the sweep so the final
  // stage must iterate until it there are no more nmethods.
  int todo = (CodeCache::nof_nmethods() - _seen) / _sweep_fractions_left;
  int swept_count = 0;


  assert(!SafepointSynchronize::is_at_safepoint(), "should not be in safepoint when we get here");
  assert(!CodeCache_lock->owned_by_self(), "just checking");

  int freed_memory = 0;
  {
    MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);

    // The last invocation iterates until there are no more nmethods
    for (int i = 0; (i < todo || _sweep_fractions_left == 1) && _current != NULL; i++) {
      swept_count++;
      if (SafepointSynchronize::is_synchronizing()) { // Safepoint request
        if (PrintMethodFlushing && Verbose) {
          tty->print_cr("### Sweep at %d out of %d, invocation: %d, yielding to safepoint", _seen, CodeCache::nof_nmethods(), _sweep_fractions_left);
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
        freed_memory += process_nmethod(_current);
      }
      _seen++;
      _current = next;
    }
  }

  assert(_sweep_fractions_left > 1 || _current == NULL, "must have scanned the whole cache");

  const Ticks sweep_end_counter = Ticks::now();
  const Tickspan sweep_time = sweep_end_counter - sweep_start_counter;
  _total_time_sweeping  += sweep_time;
  _total_time_this_sweep += sweep_time;
  _peak_sweep_fraction_time = MAX2(sweep_time, _peak_sweep_fraction_time);
  _total_nof_methods_reclaimed += _flushed_count;

  EventSweepCodeCache event(UNTIMED);
  if (event.should_commit()) {
    event.set_starttime(sweep_start_counter);
    event.set_endtime(sweep_end_counter);
    event.set_sweepIndex(_traversals);
    event.set_sweepFractionIndex(NmethodSweepFraction - _sweep_fractions_left + 1);
    event.set_sweptCount(swept_count);
    event.set_flushedCount(_flushed_count);
    event.set_markedCount(_marked_for_reclamation_count);
    event.set_zombifiedCount(_zombified_count);
    event.commit();
  }

#ifdef ASSERT
  if(PrintMethodFlushing) {
    tty->print_cr("### sweeper:      sweep time(%d): "
      INT64_FORMAT, _sweep_fractions_left, (jlong)sweep_time.value());
  }
#endif

  if (_sweep_fractions_left == 1) {
    _peak_sweep_time = MAX2(_peak_sweep_time, _total_time_this_sweep);
    log_sweep("finished");
  }

  // Sweeper is the only case where memory is released, check here if it
  // is time to restart the compiler. Only checking if there is a certain
  // amount of free memory in the code cache might lead to re-enabling
  // compilation although no memory has been released. For example, there are
  // cases when compilation was disabled although there is 4MB (or more) free
  // memory in the code cache. The reason is code cache fragmentation. Therefore,
  // it only makes sense to re-enable compilation if we have actually freed memory.
  // Note that typically several kB are released for sweeping 16MB of the code
  // cache. As a result, 'freed_memory' > 0 to restart the compiler.
  if (!CompileBroker::should_compile_new_jobs() && (freed_memory > 0)) {
    CompileBroker::set_should_compile_new_jobs(CompileBroker::run_compilation);
    log_sweep("restart_compiler");
  }
}

/**
 * This function updates the sweeper statistics that keep track of nmethods
 * state changes. If there is 'enough' state change, the sweeper is invoked
 * as soon as possible. There can be data races on _bytes_changed. The data
 * races are benign, since it does not matter if we loose a couple of bytes.
 * In the worst case we call the sweeper a little later. Also, we are guaranteed
 * to invoke the sweeper if the code cache gets full.
 */
void NMethodSweeper::report_state_change(nmethod* nm) {
  _bytes_changed += nm->total_size();
  possibly_enable_sweeper();
}

/**
 * Function determines if there was 'enough' state change in the code cache to invoke
 * the sweeper again. Currently, we determine 'enough' as more than 1% state change in
 * the code cache since the last sweep.
 */
void NMethodSweeper::possibly_enable_sweeper() {
  double percent_changed = ((double)_bytes_changed / (double)ReservedCodeCacheSize) * 100;
  if (percent_changed > 1.0) {
    _should_sweep = true;
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

int NMethodSweeper::process_nmethod(nmethod *nm) {
  assert(!CodeCache_lock->owned_by_self(), "just checking");

  int freed_memory = 0;
  // Make sure this nmethod doesn't get unloaded during the scan,
  // since safepoints may happen during acquired below locks.
  NMethodMarker nmm(nm);
  SWEEP(nm);

  // Skip methods that are currently referenced by the VM
  if (nm->is_locked_by_vm()) {
    // But still remember to clean-up inline caches for alive nmethods
    if (nm->is_alive()) {
      // Clean inline caches that point to zombie/non-entrant methods
      MutexLocker cl(CompiledIC_lock);
      nm->cleanup_inline_caches();
      SWEEP(nm);
    }
    return freed_memory;
  }

  if (nm->is_zombie()) {
    // If it is the first time we see nmethod then we mark it. Otherwise,
    // we reclaim it. When we have seen a zombie method twice, we know that
    // there are no inline caches that refer to it.
    if (nm->is_marked_for_reclamation()) {
      assert(!nm->is_locked_by_vm(), "must not flush locked nmethods");
      if (PrintMethodFlushing && Verbose) {
        tty->print_cr("### Nmethod %3d/" PTR_FORMAT " (marked for reclamation) being flushed", nm->compile_id(), nm);
      }
      freed_memory = nm->total_size();
      release_nmethod(nm);
      _flushed_count++;
    } else {
      if (PrintMethodFlushing && Verbose) {
        tty->print_cr("### Nmethod %3d/" PTR_FORMAT " (zombie) being marked for reclamation", nm->compile_id(), nm);
      }
      nm->mark_for_reclamation();
      // Keep track of code cache state change
      _bytes_changed += nm->total_size();
      _marked_for_reclamation_count++;
      SWEEP(nm);
    }
  } else if (nm->is_not_entrant()) {
    // If there are no current activations of this method on the
    // stack we can safely convert it to a zombie method
    if (nm->can_not_entrant_be_converted()) {
      if (PrintMethodFlushing && Verbose) {
        tty->print_cr("### Nmethod %3d/" PTR_FORMAT " (not entrant) being made zombie", nm->compile_id(), nm);
      }
      // Code cache state change is tracked in make_zombie()
      nm->make_zombie();
      _zombified_count++;
      SWEEP(nm);
    } else {
      // Still alive, clean up its inline caches
      MutexLocker cl(CompiledIC_lock);
      nm->cleanup_inline_caches();
      SWEEP(nm);
    }
  } else if (nm->is_unloaded()) {
    // Unloaded code, just make it a zombie
    if (PrintMethodFlushing && Verbose) {
      tty->print_cr("### Nmethod %3d/" PTR_FORMAT " (unloaded) being made zombie", nm->compile_id(), nm);
    }
    if (nm->is_osr_method()) {
      SWEEP(nm);
      // No inline caches will ever point to osr methods, so we can just remove it
      freed_memory = nm->total_size();
      release_nmethod(nm);
      _flushed_count++;
    } else {
      // Code cache state change is tracked in make_zombie()
      nm->make_zombie();
      _zombified_count++;
      SWEEP(nm);
    }
  } else {
    if (UseCodeCacheFlushing) {
      if (!nm->is_locked_by_vm() && !nm->is_osr_method() && !nm->is_native_method()) {
        // Do not make native methods and OSR-methods not-entrant
        nm->dec_hotness_counter();
        // Get the initial value of the hotness counter. This value depends on the
        // ReservedCodeCacheSize
        int reset_val = hotness_counter_reset_val();
        int time_since_reset = reset_val - nm->hotness_counter();
        double threshold = -reset_val + (CodeCache::reverse_free_ratio() * NmethodSweepActivity);
        // The less free space in the code cache we have - the bigger reverse_free_ratio() is.
        // I.e., 'threshold' increases with lower available space in the code cache and a higher
        // NmethodSweepActivity. If the current hotness counter - which decreases from its initial
        // value until it is reset by stack walking - is smaller than the computed threshold, the
        // corresponding nmethod is considered for removal.
        if ((NmethodSweepActivity > 0) && (nm->hotness_counter() < threshold) && (time_since_reset > 10)) {
          // A method is marked as not-entrant if the method is
          // 1) 'old enough': nm->hotness_counter() < threshold
          // 2) The method was in_use for a minimum amount of time: (time_since_reset > 10)
          //    The second condition is necessary if we are dealing with very small code cache
          //    sizes (e.g., <10m) and the code cache size is too small to hold all hot methods.
          //    The second condition ensures that methods are not immediately made not-entrant
          //    after compilation.
          nm->make_not_entrant();
          // Code cache state change is tracked in make_not_entrant()
          if (PrintMethodFlushing && Verbose) {
            tty->print_cr("### Nmethod %d/" PTR_FORMAT "made not-entrant: hotness counter %d/%d threshold %f",
                          nm->compile_id(), nm, nm->hotness_counter(), reset_val, threshold);
          }
        }
      }
    }
    // Clean-up all inline caches that point to zombie/non-reentrant methods
    MutexLocker cl(CompiledIC_lock);
    nm->cleanup_inline_caches();
    SWEEP(nm);
  }
  return freed_memory;
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

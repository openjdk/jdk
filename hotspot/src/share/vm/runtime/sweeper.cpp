/*
 * Copyright (c) 1997, 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/atomic.inline.hpp"
#include "runtime/compilationPolicy.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/orderAccess.inline.hpp"
#include "runtime/os.hpp"
#include "runtime/sweeper.hpp"
#include "runtime/thread.inline.hpp"
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
  int compile_id;
  long traversal_mark;
  int state;
  const char* kind;
  address vep;
  address uep;
  int line;

  void print() {
      tty->print_cr("traversal = %d compile_id = %d %s uep = " PTR_FORMAT " vep = "
                    PTR_FORMAT " state = %d traversal_mark %ld line = %d",
                    traversal,
                    compile_id,
                    kind == NULL ? "" : kind,
                    p2i(uep),
                    p2i(vep),
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

void NMethodSweeper::record_sweep(CompiledMethod* nm, int line) {
  if (_records != NULL) {
    _records[_sweep_index].traversal = _traversals;
    _records[_sweep_index].traversal_mark = nm->is_nmethod() ? ((nmethod*)nm)->_stack_traversal_mark : 0;
    _records[_sweep_index].compile_id = nm->compile_id();
    _records[_sweep_index].kind = nm->compile_kind();
    _records[_sweep_index].state = nm->get_state();
    _records[_sweep_index].vep = nm->verified_entry_point();
    _records[_sweep_index].uep = nm->entry_point();
    _records[_sweep_index].line = line;
    _sweep_index = (_sweep_index + 1) % SweeperLogEntries;
  }
}

void NMethodSweeper::init_sweeper_log() {
 if (LogSweeper && _records == NULL) {
   // Create the ring buffer for the logging code
   _records = NEW_C_HEAP_ARRAY(SweeperRecord, SweeperLogEntries, mtGC);
   memset(_records, 0, sizeof(SweeperRecord) * SweeperLogEntries);
  }
}
#else
#define SWEEP(nm)
#endif

CompiledMethodIterator NMethodSweeper::_current;               // Current compiled method
long     NMethodSweeper::_traversals                   = 0;    // Stack scan count, also sweep ID.
long     NMethodSweeper::_total_nof_code_cache_sweeps  = 0;    // Total number of full sweeps of the code cache
long     NMethodSweeper::_time_counter                 = 0;    // Virtual time used to periodically invoke sweeper
long     NMethodSweeper::_last_sweep                   = 0;    // Value of _time_counter when the last sweep happened
int      NMethodSweeper::_seen                         = 0;    // Nof. nmethod we have currently processed in current pass of CodeCache

volatile bool NMethodSweeper::_should_sweep            = true; // Indicates if we should invoke the sweeper
volatile bool NMethodSweeper::_force_sweep             = false;// Indicates if we should force a sweep
volatile int  NMethodSweeper::_bytes_changed           = 0;    // Counts the total nmethod size if the nmethod changed from:
                                                               //   1) alive       -> not_entrant
                                                               //   2) not_entrant -> zombie
int    NMethodSweeper::_hotness_counter_reset_val       = 0;

long   NMethodSweeper::_total_nof_methods_reclaimed     = 0;   // Accumulated nof methods flushed
long   NMethodSweeper::_total_nof_c2_methods_reclaimed  = 0;   // Accumulated nof methods flushed
size_t NMethodSweeper::_total_flushed_size              = 0;   // Total number of bytes flushed from the code cache
Tickspan NMethodSweeper::_total_time_sweeping;                 // Accumulated time sweeping
Tickspan NMethodSweeper::_total_time_this_sweep;               // Total time this sweep
Tickspan NMethodSweeper::_peak_sweep_time;                     // Peak time for a full sweep
Tickspan NMethodSweeper::_peak_sweep_fraction_time;            // Peak time sweeping one fraction

Monitor* NMethodSweeper::_stat_lock = new Monitor(Mutex::special, "Sweeper::Statistics", true, Monitor::_safepoint_check_sometimes);

class MarkActivationClosure: public CodeBlobClosure {
public:
  virtual void do_code_blob(CodeBlob* cb) {
    assert(cb->is_nmethod(), "CodeBlob should be nmethod");
    nmethod* nm = (nmethod*)cb;
    nm->set_hotness_counter(NMethodSweeper::hotness_counter_reset_val());
    // If we see an activation belonging to a non_entrant nmethod, we mark it.
    if (nm->is_not_entrant()) {
      nm->mark_as_seen_on_stack();
    }
  }
};
static MarkActivationClosure mark_activation_closure;

class SetHotnessClosure: public CodeBlobClosure {
public:
  virtual void do_code_blob(CodeBlob* cb) {
    assert(cb->is_nmethod(), "CodeBlob should be nmethod");
    nmethod* nm = (nmethod*)cb;
    nm->set_hotness_counter(NMethodSweeper::hotness_counter_reset_val());
  }
};
static SetHotnessClosure set_hotness_closure;


int NMethodSweeper::hotness_counter_reset_val() {
  if (_hotness_counter_reset_val == 0) {
    _hotness_counter_reset_val = (ReservedCodeCacheSize < M) ? 1 : (ReservedCodeCacheSize / M) * 2;
  }
  return _hotness_counter_reset_val;
}
bool NMethodSweeper::wait_for_stack_scanning() {
  return _current.end();
}

/**
  * Scans the stacks of all Java threads and marks activations of not-entrant methods.
  * No need to synchronize access, since 'mark_active_nmethods' is always executed at a
  * safepoint.
  */
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
  if (_current.method() != NULL) {
    if (_current.method()->is_nmethod()) {
      assert(CodeCache::find_blob_unsafe(_current.method()) == _current.method(), "Sweeper nmethod cached state invalid");
    } else {
      ShouldNotReachHere();
    }
  }

  if (wait_for_stack_scanning()) {
    _seen = 0;
    _current = CompiledMethodIterator();
    // Initialize to first nmethod
    _current.next();
    _traversals += 1;
    _total_time_this_sweep = Tickspan();

    if (PrintMethodFlushing) {
      tty->print_cr("### Sweep: stack traversal %ld", _traversals);
    }
    Threads::nmethods_do(&mark_activation_closure);

  } else {
    // Only set hotness counter
    Threads::nmethods_do(&set_hotness_closure);
  }

  OrderAccess::storestore();
}

/**
  * This function triggers a VM operation that does stack scanning of active
  * methods. Stack scanning is mandatory for the sweeper to make progress.
  */
void NMethodSweeper::do_stack_scanning() {
  assert(!CodeCache_lock->owned_by_self(), "just checking");
  if (wait_for_stack_scanning()) {
    VM_MarkActiveNMethods op;
    VMThread::execute(&op);
    _should_sweep = true;
  }
}

void NMethodSweeper::sweeper_loop() {
  bool timeout;
  while (true) {
    {
      ThreadBlockInVM tbivm(JavaThread::current());
      MutexLockerEx waiter(CodeCache_lock, Mutex::_no_safepoint_check_flag);
      const long wait_time = 60*60*24 * 1000;
      timeout = CodeCache_lock->wait(Mutex::_no_safepoint_check_flag, wait_time);
    }
    if (!timeout) {
      possibly_sweep();
    }
  }
}

/**
  * Wakes up the sweeper thread to possibly sweep.
  */
void NMethodSweeper::notify(int code_blob_type) {
  // Makes sure that we do not invoke the sweeper too often during startup.
  double start_threshold = 100.0 / (double)StartAggressiveSweepingAt;
  double aggressive_sweep_threshold = MIN2(start_threshold, 1.1);
  if (CodeCache::reverse_free_ratio(code_blob_type) >= aggressive_sweep_threshold) {
    assert_locked_or_safepoint(CodeCache_lock);
    CodeCache_lock->notify();
  }
}

/**
  * Wakes up the sweeper thread and forces a sweep. Blocks until it finished.
  */
void NMethodSweeper::force_sweep() {
  ThreadBlockInVM tbivm(JavaThread::current());
  MutexLockerEx waiter(CodeCache_lock, Mutex::_no_safepoint_check_flag);
  // Request forced sweep
  _force_sweep = true;
  while (_force_sweep) {
    // Notify sweeper that we want to force a sweep and wait for completion.
    // In case a sweep currently takes place we timeout and try again because
    // we want to enforce a full sweep.
    CodeCache_lock->notify();
    CodeCache_lock->wait(Mutex::_no_safepoint_check_flag, 1000);
  }
}

/**
 * Handle a safepoint request
 */
void NMethodSweeper::handle_safepoint_request() {
  if (SafepointSynchronize::is_synchronizing()) {
    if (PrintMethodFlushing && Verbose) {
      tty->print_cr("### Sweep at %d out of %d, yielding to safepoint", _seen, CodeCache::nmethod_count());
    }
    MutexUnlockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);

    JavaThread* thread = JavaThread::current();
    ThreadBlockInVM tbivm(thread);
    thread->java_suspend_self();
  }
}

/**
 * This function invokes the sweeper if at least one of the three conditions is met:
 *    (1) The code cache is getting full
 *    (2) There are sufficient state changes in/since the last sweep.
 *    (3) We have not been sweeping for 'some time'
 */
void NMethodSweeper::possibly_sweep() {
  assert(JavaThread::current()->thread_state() == _thread_in_vm, "must run in vm mode");
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
    const int time_since_last_sweep = _time_counter - _last_sweep;
    // ReservedCodeCacheSize has an 'unsigned' type. We need a 'signed' type for max_wait_time,
    // since 'time_since_last_sweep' can be larger than 'max_wait_time'. If that happens using
    // an unsigned type would cause an underflow (wait_until_next_sweep becomes a large positive
    // value) that disables the intended periodic sweeps.
    const int max_wait_time = ReservedCodeCacheSize / (16 * M);
    double wait_until_next_sweep = max_wait_time - time_since_last_sweep -
        MAX2(CodeCache::reverse_free_ratio(CodeBlobType::MethodProfiled),
             CodeCache::reverse_free_ratio(CodeBlobType::MethodNonProfiled));
    assert(wait_until_next_sweep <= (double)max_wait_time, "Calculation of code cache sweeper interval is incorrect");

    if ((wait_until_next_sweep <= 0.0) || !CompileBroker::should_compile_new_jobs()) {
      _should_sweep = true;
    }
  }

  // Remember if this was a forced sweep
  bool forced = _force_sweep;

  // Force stack scanning if there is only 10% free space in the code cache.
  // We force stack scanning only if the non-profiled code heap gets full, since critical
  // allocations go to the non-profiled heap and we must be make sure that there is
  // enough space.
  double free_percent = 1 / CodeCache::reverse_free_ratio(CodeBlobType::MethodNonProfiled) * 100;
  if (free_percent <= StartAggressiveSweepingAt) {
    do_stack_scanning();
  }

  if (_should_sweep || forced) {
    init_sweeper_log();
    sweep_code_cache();
  }

  // We are done with sweeping the code cache once.
  _total_nof_code_cache_sweeps++;
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

  if (forced) {
    // Notify requester that forced sweep finished
    assert(_force_sweep, "Should be a forced sweep");
    MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    _force_sweep = false;
    CodeCache_lock->notify();
  }
}

void NMethodSweeper::sweep_code_cache() {
  ResourceMark rm;
  Ticks sweep_start_counter = Ticks::now();

  int flushed_count                = 0;
  int zombified_count              = 0;
  int flushed_c2_count     = 0;

  if (PrintMethodFlushing && Verbose) {
    tty->print_cr("### Sweep at %d out of %d", _seen, CodeCache::nmethod_count());
  }

  int swept_count = 0;
  assert(!SafepointSynchronize::is_at_safepoint(), "should not be in safepoint when we get here");
  assert(!CodeCache_lock->owned_by_self(), "just checking");

  int freed_memory = 0;
  {
    MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);

    while (!_current.end()) {
      swept_count++;
      // Since we will give up the CodeCache_lock, always skip ahead
      // to the next nmethod.  Other blobs can be deleted by other
      // threads but nmethods are only reclaimed by the sweeper.
      CompiledMethod* nm = _current.method();
      _current.next();

      // Now ready to process nmethod and give up CodeCache_lock
      {
        MutexUnlockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
        // Save information before potentially flushing the nmethod
        // Only flushing nmethods so size only matters for them.
        int size = nm->is_nmethod() ? ((nmethod*)nm)->total_size() : 0;
        bool is_c2_method = nm->is_compiled_by_c2();
        bool is_osr = nm->is_osr_method();
        int compile_id = nm->compile_id();
        intptr_t address = p2i(nm);
        const char* state_before = nm->state();
        const char* state_after = "";

        MethodStateChange type = process_compiled_method(nm);
        switch (type) {
          case Flushed:
            state_after = "flushed";
            freed_memory += size;
            ++flushed_count;
            if (is_c2_method) {
              ++flushed_c2_count;
            }
            break;
          case MadeZombie:
            state_after = "made zombie";
            ++zombified_count;
            break;
          case None:
            break;
          default:
           ShouldNotReachHere();
        }
        if (PrintMethodFlushing && Verbose && type != None) {
          tty->print_cr("### %s nmethod %3d/" PTR_FORMAT " (%s) %s", is_osr ? "osr" : "", compile_id, address, state_before, state_after);
        }
      }

      _seen++;
      handle_safepoint_request();
    }
  }

  assert(_current.end(), "must have scanned the whole cache");

  const Ticks sweep_end_counter = Ticks::now();
  const Tickspan sweep_time = sweep_end_counter - sweep_start_counter;
  {
    MutexLockerEx mu(_stat_lock, Mutex::_no_safepoint_check_flag);
    _total_time_sweeping  += sweep_time;
    _total_time_this_sweep += sweep_time;
    _peak_sweep_fraction_time = MAX2(sweep_time, _peak_sweep_fraction_time);
    _total_flushed_size += freed_memory;
    _total_nof_methods_reclaimed += flushed_count;
    _total_nof_c2_methods_reclaimed += flushed_c2_count;
    _peak_sweep_time = MAX2(_peak_sweep_time, _total_time_this_sweep);
  }
  EventSweepCodeCache event(UNTIMED);
  if (event.should_commit()) {
    event.set_starttime(sweep_start_counter);
    event.set_endtime(sweep_end_counter);
    event.set_sweepIndex(_traversals);
    event.set_sweptCount(swept_count);
    event.set_flushedCount(flushed_count);
    event.set_zombifiedCount(zombified_count);
    event.commit();
  }

#ifdef ASSERT
  if(PrintMethodFlushing) {
    tty->print_cr("### sweeper:      sweep time(" JLONG_FORMAT "): ", sweep_time.value());
  }
#endif

  log_sweep("finished");

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

class CompiledMethodMarker: public StackObj {
 private:
  CodeCacheSweeperThread* _thread;
 public:
  CompiledMethodMarker(CompiledMethod* cm) {
    JavaThread* current = JavaThread::current();
    assert (current->is_Code_cache_sweeper_thread(), "Must be");
    _thread = (CodeCacheSweeperThread*)current;
    if (!cm->is_zombie() && !cm->is_unloaded()) {
      // Only expose live nmethods for scanning
      _thread->set_scanned_compiled_method(cm);
    }
  }
  ~CompiledMethodMarker() {
    _thread->set_scanned_compiled_method(NULL);
  }
};

void NMethodSweeper::release_compiled_method(CompiledMethod* nm) {
  // Make sure the released nmethod is no longer referenced by the sweeper thread
  CodeCacheSweeperThread* thread = (CodeCacheSweeperThread*)JavaThread::current();
  thread->set_scanned_compiled_method(NULL);

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

NMethodSweeper::MethodStateChange NMethodSweeper::process_compiled_method(CompiledMethod* cm) {
  assert(cm != NULL, "sanity");
  assert(!CodeCache_lock->owned_by_self(), "just checking");

  MethodStateChange result = None;
  // Make sure this nmethod doesn't get unloaded during the scan,
  // since safepoints may happen during acquired below locks.
  CompiledMethodMarker nmm(cm);
  SWEEP(cm);

  // Skip methods that are currently referenced by the VM
  if (cm->is_locked_by_vm()) {
    // But still remember to clean-up inline caches for alive nmethods
    if (cm->is_alive()) {
      // Clean inline caches that point to zombie/non-entrant/unloaded nmethods
      MutexLocker cl(CompiledIC_lock);
      cm->cleanup_inline_caches();
      SWEEP(cm);
    }
    return result;
  }

  if (cm->is_zombie()) {
    // All inline caches that referred to this nmethod were cleaned in the
    // previous sweeper cycle. Now flush the nmethod from the code cache.
    assert(!cm->is_locked_by_vm(), "must not flush locked Compiled Methods");
    release_compiled_method(cm);
    assert(result == None, "sanity");
    result = Flushed;
  } else if (cm->is_not_entrant()) {
    // If there are no current activations of this method on the
    // stack we can safely convert it to a zombie method
    if (cm->can_convert_to_zombie()) {
      // Clear ICStubs to prevent back patching stubs of zombie or flushed
      // nmethods during the next safepoint (see ICStub::finalize).
      {
        MutexLocker cl(CompiledIC_lock);
        cm->clear_ic_stubs();
      }
      // Code cache state change is tracked in make_zombie()
      cm->make_zombie();
      SWEEP(cm);
      // The nmethod may have been locked by JVMTI after being made zombie (see
      // JvmtiDeferredEvent::compiled_method_unload_event()). If so, we cannot
      // flush the osr nmethod directly but have to wait for a later sweeper cycle.
      if (cm->is_osr_method() && !cm->is_locked_by_vm()) {
        // No inline caches will ever point to osr methods, so we can just remove it.
        // Make sure that we unregistered the nmethod with the heap and flushed all
        // dependencies before removing the nmethod (done in make_zombie()).
        assert(cm->is_zombie(), "nmethod must be unregistered");
        release_compiled_method(cm);
        assert(result == None, "sanity");
        result = Flushed;
      } else {
        assert(result == None, "sanity");
        result = MadeZombie;
        assert(cm->is_zombie(), "nmethod must be zombie");
      }
    } else {
      // Still alive, clean up its inline caches
      MutexLocker cl(CompiledIC_lock);
      cm->cleanup_inline_caches();
      SWEEP(cm);
    }
  } else if (cm->is_unloaded()) {
    // Code is unloaded, so there are no activations on the stack.
    // Convert the nmethod to zombie or flush it directly in the OSR case.
    {
      // Clean ICs of unloaded nmethods as well because they may reference other
      // unloaded nmethods that may be flushed earlier in the sweeper cycle.
      MutexLocker cl(CompiledIC_lock);
      cm->cleanup_inline_caches();
    }
    if (cm->is_osr_method()) {
      SWEEP(cm);
      // No inline caches will ever point to osr methods, so we can just remove it
      release_compiled_method(cm);
      assert(result == None, "sanity");
      result = Flushed;
    } else {
      // Code cache state change is tracked in make_zombie()
      cm->make_zombie();
      SWEEP(cm);
      assert(result == None, "sanity");
      result = MadeZombie;
    }
  } else {
    if (cm->is_nmethod()) {
      possibly_flush((nmethod*)cm);
    }
    // Clean inline caches that point to zombie/non-entrant/unloaded nmethods
    MutexLocker cl(CompiledIC_lock);
    cm->cleanup_inline_caches();
    SWEEP(cm);
  }
  return result;
}


void NMethodSweeper::possibly_flush(nmethod* nm) {
  if (UseCodeCacheFlushing) {
    if (!nm->is_locked_by_vm() && !nm->is_native_method()) {
      bool make_not_entrant = false;

      // Do not make native methods not-entrant
      nm->dec_hotness_counter();
      // Get the initial value of the hotness counter. This value depends on the
      // ReservedCodeCacheSize
      int reset_val = hotness_counter_reset_val();
      int time_since_reset = reset_val - nm->hotness_counter();
      int code_blob_type = CodeCache::get_code_blob_type(nm);
      double threshold = -reset_val + (CodeCache::reverse_free_ratio(code_blob_type) * NmethodSweepActivity);
      // The less free space in the code cache we have - the bigger reverse_free_ratio() is.
      // I.e., 'threshold' increases with lower available space in the code cache and a higher
      // NmethodSweepActivity. If the current hotness counter - which decreases from its initial
      // value until it is reset by stack walking - is smaller than the computed threshold, the
      // corresponding nmethod is considered for removal.
      if ((NmethodSweepActivity > 0) && (nm->hotness_counter() < threshold) && (time_since_reset > MinPassesBeforeFlush)) {
        // A method is marked as not-entrant if the method is
        // 1) 'old enough': nm->hotness_counter() < threshold
        // 2) The method was in_use for a minimum amount of time: (time_since_reset > MinPassesBeforeFlush)
        //    The second condition is necessary if we are dealing with very small code cache
        //    sizes (e.g., <10m) and the code cache size is too small to hold all hot methods.
        //    The second condition ensures that methods are not immediately made not-entrant
        //    after compilation.
        make_not_entrant = true;
      }

      // The stack-scanning low-cost detection may not see the method was used (which can happen for
      // flat profiles). Check the age counter for possible data.
      if (UseCodeAging && make_not_entrant && (nm->is_compiled_by_c2() || nm->is_compiled_by_c1())) {
        MethodCounters* mc = nm->method()->get_method_counters(Thread::current());
        if (mc != NULL) {
          // Snapshot the value as it's changed concurrently
          int age = mc->nmethod_age();
          if (MethodCounters::is_nmethod_hot(age)) {
            // The method has gone through flushing, and it became relatively hot that it deopted
            // before we could take a look at it. Give it more time to appear in the stack traces,
            // proportional to the number of deopts.
            MethodData* md = nm->method()->method_data();
            if (md != NULL && time_since_reset > (int)(MinPassesBeforeFlush * (md->tenure_traps() + 1))) {
              // It's been long enough, we still haven't seen it on stack.
              // Try to flush it, but enable counters the next time.
              mc->reset_nmethod_age();
            } else {
              make_not_entrant = false;
            }
          } else if (MethodCounters::is_nmethod_warm(age)) {
            // Method has counters enabled, and the method was used within
            // previous MinPassesBeforeFlush sweeps. Reset the counter. Stay in the existing
            // compiled state.
            mc->reset_nmethod_age();
            // delay the next check
            nm->set_hotness_counter(NMethodSweeper::hotness_counter_reset_val());
            make_not_entrant = false;
          } else if (MethodCounters::is_nmethod_age_unset(age)) {
            // No counters were used before. Set the counters to the detection
            // limit value. If the method is going to be used again it will be compiled
            // with counters that we're going to use for analysis the the next time.
            mc->reset_nmethod_age();
          } else {
            // Method was totally idle for 10 sweeps
            // The counter already has the initial value, flush it and may be recompile
            // later with counters
          }
        }
      }

      if (make_not_entrant) {
        nm->make_not_entrant();

        // Code cache state change is tracked in make_not_entrant()
        if (PrintMethodFlushing && Verbose) {
          tty->print_cr("### Nmethod %d/" PTR_FORMAT "made not-entrant: hotness counter %d/%d threshold %f",
              nm->compile_id(), p2i(nm), nm->hotness_counter(), reset_val, threshold);
        }
      }
    }
  }
}

// Print out some state information about the current sweep and the
// state of the code cache if it's requested.
void NMethodSweeper::log_sweep(const char* msg, const char* format, ...) {
  if (PrintMethodFlushing) {
    ResourceMark rm;
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
    tty->print_cr("%s", s.as_string());
  }

  if (LogCompilation && (xtty != NULL)) {
    ResourceMark rm;
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
    xtty->print("%s", s.as_string());
    xtty->stamp();
    xtty->end_elem();
  }
}

void NMethodSweeper::print() {
  ttyLocker ttyl;
  tty->print_cr("Code cache sweeper statistics:");
  tty->print_cr("  Total sweep time:                %1.0lfms", (double)_total_time_sweeping.value()/1000000);
  tty->print_cr("  Total number of full sweeps:     %ld", _total_nof_code_cache_sweeps);
  tty->print_cr("  Total number of flushed methods: %ld(%ld C2 methods)", _total_nof_methods_reclaimed,
                                                    _total_nof_c2_methods_reclaimed);
  tty->print_cr("  Total size of flushed methods:   " SIZE_FORMAT "kB", _total_flushed_size/K);
}

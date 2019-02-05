/*
 * Copyright (c) 1997, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_RUNTIME_SWEEPER_HPP
#define SHARE_VM_RUNTIME_SWEEPER_HPP

class WhiteBox;

#include "code/codeCache.hpp"
#include "utilities/ticks.hpp"

class CodeBlobClosure;

// An NmethodSweeper is an incremental cleaner for:
//    - cleanup inline caches
//    - reclamation of nmethods
// Removing nmethods from the code cache includes two operations
//  1) mark active nmethods
//     Is done in 'mark_active_nmethods()'. This function is called at a
//     safepoint and marks all nmethods that are active on a thread's stack.
//  2) sweep nmethods
//     Is done in sweep_code_cache(). This function is the only place in the
//     sweeper where memory is reclaimed. Note that sweep_code_cache() is not
//     called at a safepoint. However, sweep_code_cache() stops executing if
//     another thread requests a safepoint. Consequently, 'mark_active_nmethods()'
//     and sweep_code_cache() cannot execute at the same time.
//     To reclaim memory, nmethods are first marked as 'not-entrant'. Methods can
//     be made not-entrant by (i) the sweeper, (ii) deoptimization, (iii) dependency
//     invalidation, and (iv) being replaced by a different method version (tiered
//     compilation). Not-entrant nmethods cannot be called by Java threads, but they
//     can still be active on the stack. To ensure that active nmethods are not reclaimed,
//     we have to wait until the next marking phase has completed. If a not-entrant
//     nmethod was NOT marked as active, it can be converted to 'zombie' state. To safely
//     remove the nmethod, all inline caches (IC) that point to the nmethod must be
//     cleared. After that, the nmethod can be evicted from the code cache. Each nmethod's
//     state change happens during separate sweeps. It may take at least 3 sweeps before an
//     nmethod's space is freed.

class NMethodSweeper : public AllStatic {
 private:
  enum MethodStateChange {
    None,
    MadeZombie,
    Flushed
  };
  static long      _traversals;                   // Stack scan count, also sweep ID.
  static long      _total_nof_code_cache_sweeps;  // Total number of full sweeps of the code cache
  static long      _time_counter;                 // Virtual time used to periodically invoke sweeper
  static long      _last_sweep;                   // Value of _time_counter when the last sweep happened
  static CompiledMethodIterator _current;         // Current compiled method
  static int       _seen;                         // Nof. nmethod we have currently processed in current pass of CodeCache

  static volatile int  _sweep_started;            // Flag to control conc sweeper
  static volatile bool _should_sweep;             // Indicates if we should invoke the sweeper
  static volatile bool _force_sweep;              // Indicates if we should force a sweep
  static volatile int _bytes_changed;             // Counts the total nmethod size if the nmethod changed from:
                                                  //   1) alive       -> not_entrant
                                                  //   2) not_entrant -> zombie
  // Stat counters
  static long      _total_nof_methods_reclaimed;    // Accumulated nof methods flushed
  static long      _total_nof_c2_methods_reclaimed; // Accumulated nof C2-compiled methods flushed
  static size_t    _total_flushed_size;             // Total size of flushed methods
  static int       _hotness_counter_reset_val;

  static Tickspan  _total_time_sweeping;          // Accumulated time sweeping
  static Tickspan  _total_time_this_sweep;        // Total time this sweep
  static Tickspan  _peak_sweep_time;              // Peak time for a full sweep
  static Tickspan  _peak_sweep_fraction_time;     // Peak time sweeping one fraction

  static MethodStateChange process_compiled_method(CompiledMethod *nm);

  static void init_sweeper_log() NOT_DEBUG_RETURN;
  static bool wait_for_stack_scanning();
  static void sweep_code_cache();
  static void handle_safepoint_request();
  static void do_stack_scanning();
  static void possibly_sweep();
 public:
  static long traversal_count()              { return _traversals; }
  static int  total_nof_methods_reclaimed()  { return _total_nof_methods_reclaimed; }
  static const Tickspan total_time_sweeping()      { return _total_time_sweeping; }
  static const Tickspan peak_sweep_time()          { return _peak_sweep_time; }
  static const Tickspan peak_sweep_fraction_time() { return _peak_sweep_fraction_time; }
  static void log_sweep(const char* msg, const char* format = NULL, ...) ATTRIBUTE_PRINTF(2, 3);


#ifdef ASSERT
  // Keep track of sweeper activity in the ring buffer
  static void record_sweep(CompiledMethod* nm, int line);
  static void report_events(int id, address entry);
  static void report_events();
#endif

  static void mark_active_nmethods();      // Invoked at the end of each safepoint
  static CodeBlobClosure* prepare_mark_active_nmethods();
  static CodeBlobClosure* prepare_reset_hotness_counters();
  static void sweeper_loop();
  static void notify(int code_blob_type);  // Possibly start the sweeper thread.
  static void force_sweep();

  static int hotness_counter_reset_val();
  static void report_state_change(nmethod* nm);
  static void possibly_enable_sweeper();
  static void possibly_flush(nmethod* nm);
  static void print(outputStream* out);   // Printing/debugging
  static void print() { print(tty); }
};

#endif // SHARE_VM_RUNTIME_SWEEPER_HPP

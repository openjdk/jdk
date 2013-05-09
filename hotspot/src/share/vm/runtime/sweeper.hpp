/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

// An NmethodSweeper is an incremental cleaner for:
//    - cleanup inline caches
//    - reclamation of unreferences zombie nmethods
//

class NMethodSweeper : public AllStatic {
  static long      _traversals;   // Stack traversal count
  static nmethod*  _current;      // Current nmethod
  static int       _seen;         // Nof. nmethod we have currently processed in current pass of CodeCache

  static volatile int  _invocations;   // No. of invocations left until we are completed with this pass
  static volatile int  _sweep_started; // Flag to control conc sweeper

  //The following are reset in scan_stacks and synchronized by the safepoint
  static bool      _resweep;           // Indicates that a change has happend and we want another sweep,
                                       // always checked and reset at a safepoint so memory will be in sync.
  static int       _locked_seen;       // Number of locked nmethods encountered during the scan
  static int       _not_entrant_seen_on_stack; // Number of not entrant nmethod were are still on stack
  static jint      _flush_token;       // token that guards method flushing, making sure it is executed only once.

  // These are set during a flush, a VM-operation
  static long      _last_flush_traversal_id; // trav number at last flush unloading
  static jlong     _last_full_flush_time;    // timestamp of last emergency unloading

  // These are synchronized by the _sweep_started token
  static int       _highest_marked;   // highest compile id dumped at last emergency unloading
  static int       _dead_compile_ids; // number of compile ids that where not in the cache last flush

  static void process_nmethod(nmethod *nm);
  static void release_nmethod(nmethod* nm);

  static void log_sweep(const char* msg, const char* format = NULL, ...);
  static bool sweep_in_progress();

 public:
  static long traversal_count() { return _traversals; }

#ifdef ASSERT
  // Keep track of sweeper activity in the ring buffer
  static void record_sweep(nmethod* nm, int line);
  static void report_events(int id, address entry);
  static void report_events();
#endif

  static void scan_stacks();      // Invoked at the end of each safepoint
  static void sweep_code_cache(); // Concurrent part of sweep job
  static void possibly_sweep();   // Compiler threads call this to sweep

  static void notify(nmethod* nm) {
    // Request a new sweep of the code cache from the beginning. No
    // need to synchronize the setting of this flag since it only
    // changes to false at safepoint so we can never overwrite it with false.
     _resweep = true;
  }

  static void handle_full_code_cache(bool is_full); // Called by compilers who fail to allocate
  static void speculative_disconnect_nmethods(bool was_full);   // Called by vm op to deal with alloc failure
};

#endif // SHARE_VM_RUNTIME_SWEEPER_HPP

/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_G1ROOTPROCESSOR_HPP
#define SHARE_VM_GC_G1_G1ROOTPROCESSOR_HPP

#include "gc/shared/strongRootsScope.hpp"
#include "memory/allocation.hpp"
#include "runtime/mutex.hpp"

class CLDClosure;
class CodeBlobClosure;
class G1CollectedHeap;
class G1GCPhaseTimes;
class G1ParPushHeapRSClosure;
class Monitor;
class OopClosure;
class SubTasksDone;

// Scoped object to assist in applying oop, CLD and code blob closures to
// root locations. Handles claiming of different root scanning tasks
// and takes care of global state for root scanning via a StrongRootsScope.
// In the parallel case there is a shared G1RootProcessor object where all
// worker thread call the process_roots methods.
class G1RootProcessor : public StackObj {
  G1CollectedHeap* _g1h;
  SubTasksDone _process_strong_tasks;
  StrongRootsScope _srs;

  // Used to implement the Thread work barrier.
  Monitor _lock;
  volatile jint _n_workers_discovered_strong_classes;

  enum G1H_process_roots_tasks {
    G1RP_PS_Universe_oops_do,
    G1RP_PS_JNIHandles_oops_do,
    G1RP_PS_ObjectSynchronizer_oops_do,
    G1RP_PS_FlatProfiler_oops_do,
    G1RP_PS_Management_oops_do,
    G1RP_PS_SystemDictionary_oops_do,
    G1RP_PS_ClassLoaderDataGraph_oops_do,
    G1RP_PS_jvmti_oops_do,
    G1RP_PS_CodeCache_oops_do,
    G1RP_PS_filter_satb_buffers,
    G1RP_PS_refProcessor_oops_do,
    // Leave this one last.
    G1RP_PS_NumElements
  };

  void worker_has_discovered_all_strong_classes();
  void wait_until_all_strong_classes_discovered();

  void process_java_roots(OopClosure* scan_non_heap_roots,
                          CLDClosure* thread_stack_clds,
                          CLDClosure* scan_strong_clds,
                          CLDClosure* scan_weak_clds,
                          CodeBlobClosure* scan_strong_code,
                          G1GCPhaseTimes* phase_times,
                          uint worker_i);

  void process_vm_roots(OopClosure* scan_non_heap_roots,
                        OopClosure* scan_non_heap_weak_roots,
                        G1GCPhaseTimes* phase_times,
                        uint worker_i);

public:
  G1RootProcessor(G1CollectedHeap* g1h, uint n_workers);

  // Apply closures to the strongly and weakly reachable roots in the system
  // in a single pass.
  // Record and report timing measurements for sub phases using the worker_i
  void evacuate_roots(OopClosure* scan_non_heap_roots,
                      OopClosure* scan_non_heap_weak_roots,
                      CLDClosure* scan_strong_clds,
                      CLDClosure* scan_weak_clds,
                      bool trace_metadata,
                      uint worker_i);

  // Apply oops, clds and blobs to all strongly reachable roots in the system
  void process_strong_roots(OopClosure* oops,
                            CLDClosure* clds,
                            CodeBlobClosure* blobs);

  // Apply oops, clds and blobs to strongly and weakly reachable roots in the system
  void process_all_roots(OopClosure* oops,
                         CLDClosure* clds,
                         CodeBlobClosure* blobs);

  // Number of worker threads used by the root processor.
  uint n_workers() const;
};

#endif // SHARE_VM_GC_G1_G1ROOTPROCESSOR_HPP

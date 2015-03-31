/*
 * Copyright (c) 2000, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/stringTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "code/codeCache.hpp"
#include "gc_interface/collectedHeap.inline.hpp"
#include "memory/sharedHeap.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomic.inline.hpp"
#include "runtime/fprofiler.hpp"
#include "runtime/java.hpp"
#include "utilities/copy.hpp"
#include "utilities/workgroup.hpp"

SharedHeap* SharedHeap::_sh;

SharedHeap::SharedHeap(CollectorPolicy* policy_) :
  CollectedHeap(),
  _collector_policy(policy_),
  _workers(NULL)
{
  _sh = this;  // ch is static, should be set only once.
  if (UseConcMarkSweepGC || UseG1GC) {
    _workers = new FlexibleWorkGang("GC Thread", ParallelGCThreads,
                            /* are_GC_task_threads */true,
                            /* are_ConcurrentGC_threads */false);
    if (_workers == NULL) {
      vm_exit_during_initialization("Failed necessary allocation.");
    } else {
      _workers->initialize_workers();
    }
  }
}

bool SharedHeap::heap_lock_held_for_gc() {
  Thread* t = Thread::current();
  return    Heap_lock->owned_by_self()
         || (   (t->is_GC_task_thread() ||  t->is_VM_thread())
             && _thread_holds_heap_lock_for_gc);
}

void SharedHeap::set_par_threads(uint t) {
  assert(t == 0 || !UseSerialGC, "Cannot have parallel threads");
  _n_par_threads = t;
}

SharedHeap::StrongRootsScope::StrongRootsScope(SharedHeap* heap, bool activate)
  : MarkScope(activate), _sh(heap)
{
  if (_active) {
    Threads::change_thread_claim_parity();
    // Zero the claimed high water mark in the StringTable
    StringTable::clear_parallel_claimed_index();
  }
}

SharedHeap::StrongRootsScope::~StrongRootsScope() {
  Threads::assert_all_threads_claimed();
}

void SharedHeap::set_barrier_set(BarrierSet* bs) {
  _barrier_set = bs;
  // Cached barrier set for fast access in oops
  oopDesc::set_bs(bs);
}

void SharedHeap::post_initialize() {
  CollectedHeap::post_initialize();
  ref_processing_init();
}

void SharedHeap::ref_processing_init() {}

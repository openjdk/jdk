/*
 * Copyright (c) 2020, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHEVACOOMHANDLER_INLINE_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHEVACOOMHANDLER_INLINE_HPP

#include "gc/shenandoah/shenandoahEvacOOMHandler.hpp"

#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahThreadLocalData.hpp"
#include "runtime/atomic.hpp"

jint ShenandoahEvacOOMCounter::load_acquire() {
  return Atomic::load_acquire(&_bits);
}

jint ShenandoahEvacOOMCounter::unmasked_count() {
  return Atomic::load_acquire(&_bits) & ~OOM_MARKER_MASK;
}

// Announce the intent by thread thr to perform allocations for evacuation.  
//
// Upon return:
//
//  1. The count of nested allocate-for-evacuation scopes for this thread has been incremented.
//  2. Thread thr is authorized to allocate for evacuation and the count of allocating threads represents this thread, or
//  3. Thread thr is not authorized to allocate for evacuation and the count of allocating thread does not include this thread.
//
// Thread-local flag is_oom_during_evac(thr) is false iff thread thr is authorized to allocate for evacuation.
//
// Notes: If this thread subsequently encounters a "need" to allocate memory for evacuation but it is not authorized to 
//        allocate for evacuation, this thread will simply treat the relevant cset object as "frozen within from-space".
//        If this thread is forbidden to allocate, then all threads are forbidden to allocate.  As soon as a first thread
//        begins to execute within an "evacuation region" without authorization to allocate, the evac-OOM protocol requires
//        that no additional objects be evacuated.  Normally, this phase of executing without authorization to evacuate is
//        immediately followed by a Full GC which compacts all of heap memory in STW mode.

void ShenandoahEvacOOMHandler::enter_evacuation(Thread* thr) {
  uint8_t level = ShenandoahThreadLocalData::push_evac_oom_scope(thr);
 if (level == 0) {
   // Entering top level scope, register this thread.
   register_thread(thr);
 } else if (!ShenandoahThreadLocalData::is_oom_during_evac(thr)) {
   ShenandoahEvacOOMCounter* counter = counter_for_thread(thr);
   jint threads_in_evac = counter->load_acquire();
   // If OOM is in progress, handle it.
   if ((threads_in_evac & ShenandoahEvacOOMCounter::OOM_MARKER_MASK) != 0) {
     counter->decrement();
     wait_for_no_evac_threads();
   }
 }
}

// Announce intent to leave a control scope that performs allocation for evacuation.
//
// Upon return:
//
// 1. The thread-local count of nested allocation-for-evacuation scopes for this thread has been decremented.
// 2. If we have left the outer-most allocation-for-evacuation scope for this thread:
//    a. The count of threads that are allocating for evacuation does not represent this thread
//    b. This thread is authorized to allocate for evacuation.
//
// Notes: A thread that has already entered evacuation and not left may make a nested re-entry into evacuation.  Each nested
// invocation of enter_evacuation should be matched by invocation of leave_evacuation.

void ShenandoahEvacOOMHandler::leave_evacuation(Thread* thr) {
  uint8_t level = ShenandoahThreadLocalData::pop_evac_oom_scope(thr);
  // Not top level, just return
  if (level > 1) {
    return;
  }

  // Leaving top level scope, unregister this thread.
  unregister_thread(thr);
}

ShenandoahEvacOOMScope::ShenandoahEvacOOMScope() :
  _thread(Thread::current()) {
  ShenandoahHeap::heap()->enter_evacuation(_thread);
}

ShenandoahEvacOOMScope::ShenandoahEvacOOMScope(Thread* t) :
  _thread(t) {
  ShenandoahHeap::heap()->enter_evacuation(_thread);
}

ShenandoahEvacOOMScope::~ShenandoahEvacOOMScope() {
  ShenandoahHeap::heap()->leave_evacuation(_thread);
}

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHEVACOOMHANDLER_INLINE_HPP

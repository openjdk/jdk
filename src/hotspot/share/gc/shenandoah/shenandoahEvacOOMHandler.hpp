/*
 * Copyright (c) 2018, 2020, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHEVACOOMHANDLER_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHEVACOOMHANDLER_HPP

#include "gc/shenandoah/shenandoahPadding.hpp"
#include "memory/allocation.hpp"
#include "runtime/javaThread.hpp"
#include "utilities/globalDefinitions.hpp"

/**
 * Striped counter used to implement the OOM protocol described below.
 */
class ShenandoahEvacOOMCounter {
private:
  // Combination of a 31-bit counter and 1-bit OOM marker.
  volatile jint _bits;

  // This class must be at least a cache line in size to prevent false sharing.
  shenandoah_padding_minus_size(0, sizeof(jint));

public:
  static const jint OOM_MARKER_MASK;

  ShenandoahEvacOOMCounter();

  void decrement();
  bool try_increment();
  void clear();
  void set_oom_bit(bool decrement);

  inline jint unmasked_count();
  inline jint load_acquire();
};

/**
 * Provides safe handling of out-of-memory situations during evacuation.
 *
 * When a Java thread encounters out-of-memory while evacuating an object in a
 * load-reference-barrier (i.e. it cannot copy the object to to-space), a special
 * protocol is required to assure that all threads see the same version of every
 * object.
 *
 * This file and its accompanying .cpp and .inline.hpp files hold the implementation
 * of this protocol.  The general idea is as follows:
 *
 *  1. If we fail to evacuate the entirety of live memory from all cset regions,
 *     we will transition to STW full gc at the end of the evacuation cycle.  Full GC
 *     marks and compacts the entire heap.  If we fail to evacuate a single cset object,
 *     we will pay the price of a full GC.
 *
 *  2. If any thread A fails to evacuate object X, it will wait to see if some
 *     other mutator or GC worker thread can successfully evacuate object X.  At the
 *     thread A fails to allocate, it launches the OOM-during-evacuation protocol.  There
 *     is no going back (even though some other thread may successfully evacuate object X).
 *
 *  3. The protocol consists of:
 *
 *     a) Thread A sets internal state to indicate that OOM-during-evac has been
 *        encountered.
 *     b) Thread A now waits for all other threads to finish any ongoing allocations
 *        for evacuation that might be in process.
 *     c) Other threads that announce intent to allocate for evacuation are informed
 *        that the OOM-during-evac protocol has been initated.  As with thread A,
 *        these threads also wait for all other threads to finish any ongoing allocations
 *        for evacuation that might be in process.
 *     d) After all threads have finished whatever allocations for evacuation they
 *        were in the process of performing, the evacution state of the heap is considered
 *        to be "frozen".  At this point, some cset objects may have been successfully
 *        evacuated and some cset objects may have failed to evacuate.  There will be
 *        no more evaucations until we STW and perform Full GC.
 *     e) Now, all of the threads that were waiting for evacuating threads to finish
 *        allocations that were in progress are allowed to run, but they are not allowed
 *        to allocate for evacuation.  Additional threads that newly announce intent to
 *        allocate for evacuation are immediately allowed to continue running, but without
 *        authorization to allocate.
 *     f) Threads that desire to allocate for evacuation but are not authorized to do so
 *        simply consult the head of each cset object.  If the header denotes that the
 *        object has been evacuated by a different thread, then this thread will replace
 *        its pointer to the object with a pointer to the new location.  If the header
 *        denotes that this object has not yet been copied, this thread will continue to
 *        use the original cset location as the official version of the object.  Since
 *        no threads are allowed to allocate for evacuation in this phase, all threads
 *        accessing this same object will agree to refer to this object at its original
 *        location within the cset.
 *     g) Evacuation is cancelled and all threads will eventually reach a Full GC
 *        safepoint.  Marking by Full GC will finish updating references that might
 *        be inconsistent within the heap, and will then compact all live memory within
 *        the heap.
 *
 * Detailed state change:
 *
 * Maintain a count of how many threads are on an evac-path (which is allocating for evacuation)
 *
 * Upon entry of the evac-path, entering thread will attempt to increase the counter,
 * using a CAS. Depending on the result of the CAS:
 * - success: carry on with evac
 * - failure:
 *   - if offending value is a valid counter, then try again
 *   - if offending value is OOM-during-evac special value: loop until
 *     counter drops to 0, then continue without authorization to allocate.
 *     As such, the thread will treat unforwarded cset objects as residing
 *     permanently at their original location.
 *
 *
 * Upon exit, exiting thread will decrease the counter using atomic dec.
 *
 * Upon OOM-during-evac, any thread will attempt to CAS OOM-during-evac
 * special value into the counter. Depending on result:
 *   - success: busy-loop until counter drops to zero, Then continue
 *     to execute without authnorization to allocate.  Any unforwarded
 *     cset objects will be treated as residing permanently at their original
 *     location.
 *   - failure:
 *     - offender is valid counter update: try again
 *     - offender is OOM-during-evac: busy loop until counter drops to
 *       zero, then continue to execute without autnorization to allocate,
 *       as above.
 */

/*
 * For most service workloads, OOM-during-evac will be very rare.  Most services are provisioned
 * with enough memory and CPU cores to avoid experiencing OOM during evac.  The typical cause for
 * OOM during evac is a spike in client requests, possibly related to a DOS attack.  When OOM during
 * evac does occur, there are opportunities to make the protocol more efficient.  In some cases,
 * OOM during evac can also occur because the heap becomes fragmented.  For example, it may not be
 * possible to find contiguous memory to evacuate an object that is 50% of the heap region size, even
 * though there is an abundance of "fragmented" memory available to support evacuation of thousands of
 * smaller (more normal-sized) objects.
 *
 * TODO: make refinements to the OOM-during-evac protocol so that it is less disruptive and more efficient.
 *
 *  1. Allow a mutator or GC worker thread that fails to allocate for evacuation to mark a single
 *     cset object as frozen-in-from-space and then continue to evacuate other objects while other
 *     threads continue to evacuate other objects.  A draft solution is described here, along with discussion
 *     of prerequisites required for full implementation:  https://github.com/openjdk/jdk/pull/12881
 *     This allows all threads, including the one that failed to evacuate a single object, to fully utilize
 *     all of the memory available within their existing GCLABs.  This allows more of evacuation to be
 *     performed concurrently rather than requiring STW operation.
 *
 *  2. At the end of evacuation, if there were any failures to evacuate, fixup the cset before
 *     we go to update-refs.  This can be done concurrently.  Fixup consists of:
 *
 *     a. Take region out of cset if it contains objects that failed to evacuate.
 *
 *     b. For each such region, set top to be address following last object that failed to evacuate.
 *
 *     c. For each such region, make the garbage found below and between uncopied objects parseable:
 *        overwrite each run of garbage with array-of-integer object of appropriate size.  Generational
 *        Shenandoah calls this coalesce-and-fill.
 *
 *  3. Do not automatically upgrade to Full GC.  Continue with concurrent GC as long as possible.
 *     There is already a mechanism in place to escalate to Full GC if the mutator experiences out-of-memory
 *     and/or if concurrent GC is not "productive".  Transitions to Full GC are very costly because (i) this
 *     results in a very long STW pause during which mutator threads are unresponsive, and (ii) Full GC
 *     redundantly repeats work that was already successfully performed concurrently.  When OOM-during-evac
 *     transitions to Full GC, we throw away and repeat all of the previously completed work of marking and
 *     evacuating.
 */
class ShenandoahEvacOOMHandler {
private:
  const int _num_counters;

  shenandoah_padding(0);
  ShenandoahEvacOOMCounter* _threads_in_evac;

  ShenandoahEvacOOMCounter* counter_for_thread(Thread* t);

  void wait_for_no_evac_threads();
  void wait_for_no_evac_threads_on_counter(ShenandoahEvacOOMCounter* counter);

  static uint64_t hash_pointer(const void* p);
  static int calc_num_counters();
public:
  ShenandoahEvacOOMHandler();

  /**
   * Enter a protected evacuation path.
   *
   * Upon return: 
   *
   *  1. Thread t has authorization to allocate for evacuation and the count of evacuating threads includes thread t, or
   *
   *  2. Thread t has no authorization to allocate for evacuation and the count of evacuating threads does not include
   *     thread t.
   *
   * This function may pause while it waits for coordination with other allocating threads.
   *
   * Authority to allocate for evacuation is represented by thread-local flag is_oom_during_evac(t) equal to false.
   * If this thread is not authorized to allocate and it encounters an object residing within the cset, it uses
   * the most current location of the object, as represented by the object's header.  If the object was not previously
   * allocated, the evac-OOM protocol assures that the object will not be subsequently evacuated during the remainder
   * of the concurrent evacuation phase.
   */
  inline void enter_evacuation(Thread* t);

  /**
   * Leave a protected evacuation path.
   */
  inline void leave_evacuation(Thread* t);

  /**
   * Signal out-of-memory during evacuation. This will prevent any other threads
   * from entering the evacuation path, then wait until all threads have left the
   * evacuation path, and then return. Following this, it is safe to assume that
   * any object residing in the cset and not previously forwarded will remain in
   * the cset throughout the remainder of the concurrent evacuation phase.  It will
   * not be subsequently evacuated.
   */
  void handle_out_of_memory_during_evacuation();

  void clear();

private:
  // Register/Unregister thread to evacuation OOM protocol
  void register_thread(Thread* t);
  void unregister_thread(Thread* t);
};

class ShenandoahEvacOOMScope : public StackObj {
private:
  Thread* const _thread;

public:
  inline ShenandoahEvacOOMScope();
  inline ShenandoahEvacOOMScope(Thread* t);
  inline ~ShenandoahEvacOOMScope();
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHEVACOOMHANDLER_HPP

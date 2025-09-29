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
 * load-reference-barrier (i.e. it cannot copy the object to to-space), it does not
 * necessarily follow we can return immediately from the LRB (and store to from-space).
 *
 * In very basic case, on such failure we may wait until the evacuation is over,
 * and then resolve the forwarded copy, and to the store there. This is possible
 * because other threads might still have space in their GCLABs, and successfully
 * evacuate the object.
 *
 * But, there is a race due to non-atomic evac_in_progress transition. Consider
 * thread A is stuck waiting for the evacuation to be over -- it cannot leave with
 * from-space copy yet. Control thread drops evacuation_in_progress preparing for
 * next STW phase that has to recover from OOME. Thread B misses that update, and
 * successfully evacuates the object, does the write to to-copy. But, before
 * Thread B is able to install the fwdptr, thread A discovers evac_in_progress is
 * down, exits from here, reads the fwdptr, discovers old from-copy, and stores there.
 * Thread B then wakes up and installs to-copy. This breaks to-space invariant, and
 * silently corrupts the heap: we accepted two writes to separate copies of the object.
 *
 * The way it is solved here is to maintain a counter of threads inside the
 * 'evacuation path'. The 'evacuation path' is the part of evacuation that does the actual
 * allocation, copying and CASing of the copy object, and is protected by this
 * OOM-during-evac-handler. The handler allows multiple threads to enter and exit
 * evacuation path, but on OOME it requires all threads that experienced OOME to wait
 * for current threads to leave, and blocks other threads from entering. The counter state
 * is striped across multiple cache lines to reduce contention when many threads attempt
 * to enter or leave the protocol at the same time.
 *
 * Detailed state change:
 *
 * Upon entry of the evac-path, entering thread will attempt to increase the counter,
 * using a CAS. Depending on the result of the CAS:
 * - success: carry on with evac
 * - failure:
 *   - if offending value is a valid counter, then try again
 *   - if offending value is OOM-during-evac special value: loop until
 *     counter drops to 0, then exit with resolving the ptr
 *
 * Upon exit, exiting thread will decrease the counter using atomic dec.
 *
 * Upon OOM-during-evac, any thread will attempt to CAS OOM-during-evac
 * special value into the counter. Depending on result:
 *   - success: busy-loop until counter drops to zero, then exit with resolve
 *   - failure:
 *     - offender is valid counter update: try again
 *     - offender is OOM-during-evac: busy loop until counter drops to
 *       zero, then exit with resolve
 */
class ShenandoahEvacOOMHandler {
private:
  const int _num_counters;

  shenandoah_padding(0);
  ShenandoahEvacOOMCounter* _threads_in_evac;

  ShenandoahEvacOOMCounter* counter_for_thread(Thread* t);

  void wait_for_no_evac_threads();
  void wait_for_one_counter(ShenandoahEvacOOMCounter* ptr);

  static uint64_t hash_pointer(const void* p);
  static int calc_num_counters();
public:
  ShenandoahEvacOOMHandler();

  /**
   * Attempt to enter the protected evacuation path.
   *
   * When this returns true, it is safe to continue with normal evacuation.
   * When this method returns false, evacuation must not be entered, and caller
   * may safely continue with a simple resolve (if Java thread).
   */
  inline void enter_evacuation(Thread* t);

  /**
   * Leave evacuation path.
   */
  inline void leave_evacuation(Thread* t);

  /**
   * Signal out-of-memory during evacuation. It will prevent any other threads
   * from entering the evacuation path, then wait until all threads have left the
   * evacuation path, and then return. It is then safe to continue with a simple resolve.
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

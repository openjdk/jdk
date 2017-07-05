/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_SHARED_GCLOCKER_HPP
#define SHARE_VM_GC_SHARED_GCLOCKER_HPP

#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/genCollectedHeap.hpp"
#include "memory/universe.hpp"
#include "oops/oop.hpp"

// The direct lock/unlock calls do not force a collection if an unlock
// decrements the count to zero. Avoid calling these if at all possible.

class GCLocker: public AllStatic {
 private:
  // The _jni_lock_count keeps track of the number of threads that are
  // currently in a critical region.  It's only kept up to date when
  // _needs_gc is true.  The current value is computed during
  // safepointing and decremented during the slow path of GCLocker
  // unlocking.
  static volatile jint _jni_lock_count;  // number of jni active instances.
  static volatile bool _needs_gc;        // heap is filling, we need a GC
                                         // note: bool is typedef'd as jint
  static volatile bool _doing_gc;        // unlock_critical() is doing a GC

#ifdef ASSERT
  // This lock count is updated for all operations and is used to
  // validate the jni_lock_count that is computed during safepoints.
  static volatile jint _debug_jni_lock_count;
#endif

  // At a safepoint, visit all threads and count the number of active
  // critical sections.  This is used to ensure that all active
  // critical sections are exited before a new one is started.
  static void verify_critical_count() NOT_DEBUG_RETURN;

  static void jni_lock(JavaThread* thread);
  static void jni_unlock(JavaThread* thread);

  static bool is_active_internal() {
    verify_critical_count();
    return _jni_lock_count > 0;
  }

  static void log_debug_jni(const char* msg);
 public:
  // Accessors
  static bool is_active() {
    assert(SafepointSynchronize::is_at_safepoint(), "only read at safepoint");
    return is_active_internal();
  }
  static bool needs_gc()       { return _needs_gc;                        }

  // Shorthand
  static bool is_active_and_needs_gc() {
    // Use is_active_internal since _needs_gc can change from true to
    // false outside of a safepoint, triggering the assert in
    // is_active.
    return needs_gc() && is_active_internal();
  }

  // In debug mode track the locking state at all times
  static void increment_debug_jni_lock_count() NOT_DEBUG_RETURN;
  static void decrement_debug_jni_lock_count() NOT_DEBUG_RETURN;

  // Set the current lock count
  static void set_jni_lock_count(int count) {
    _jni_lock_count = count;
    verify_critical_count();
  }

  // Sets _needs_gc if is_active() is true. Returns is_active().
  static bool check_active_before_gc();

  // Stalls the caller (who should not be in a jni critical section)
  // until needs_gc() clears. Note however that needs_gc() may be
  // set at a subsequent safepoint and/or cleared under the
  // JNICritical_lock, so the caller may not safely assert upon
  // return from this method that "!needs_gc()" since that is
  // not a stable predicate.
  static void stall_until_clear();

  // The following two methods are used for JNI critical regions.
  // If we find that we failed to perform a GC because the GCLocker
  // was active, arrange for one as soon as possible by allowing
  // all threads in critical regions to complete, but not allowing
  // other critical regions to be entered. The reasons for that are:
  // 1) a GC request won't be starved by overlapping JNI critical
  //    region activities, which can cause unnecessary OutOfMemory errors.
  // 2) even if allocation requests can still be satisfied before GC locker
  //    becomes inactive, for example, in tenured generation possibly with
  //    heap expansion, those allocations can trigger lots of safepointing
  //    attempts (ineffective GC attempts) and require Heap_lock which
  //    slow down allocations tremendously.
  //
  // Note that critical regions can be nested in a single thread, so
  // we must allow threads already in critical regions to continue.
  //
  // JNI critical regions are the only participants in this scheme
  // because they are, by spec, well bounded while in a critical region.
  //
  // Each of the following two method is split into a fast path and a
  // slow path. JNICritical_lock is only grabbed in the slow path.
  // _needs_gc is initially false and every java thread will go
  // through the fast path, which simply increments or decrements the
  // current thread's critical count.  When GC happens at a safepoint,
  // GCLocker::is_active() is checked. Since there is no safepoint in
  // the fast path of lock_critical() and unlock_critical(), there is
  // no race condition between the fast path and GC. After _needs_gc
  // is set at a safepoint, every thread will go through the slow path
  // after the safepoint.  Since after a safepoint, each of the
  // following two methods is either entered from the method entry and
  // falls into the slow path, or is resumed from the safepoints in
  // the method, which only exist in the slow path. So when _needs_gc
  // is set, the slow path is always taken, till _needs_gc is cleared.
  static void lock_critical(JavaThread* thread);
  static void unlock_critical(JavaThread* thread);

  static address needs_gc_address() { return (address) &_needs_gc; }
};


// A NoGCVerifier object can be placed in methods where one assumes that
// no garbage collection will occur. The destructor will verify this property
// unless the constructor is called with argument false (not verifygc).
//
// The check will only be done in debug mode and if verifygc true.

class NoGCVerifier: public StackObj {
 friend class PauseNoGCVerifier;

 protected:
  bool _verifygc;
  unsigned int _old_invocations;

 public:
#ifdef ASSERT
  NoGCVerifier(bool verifygc = true);
  ~NoGCVerifier();
#else
  NoGCVerifier(bool verifygc = true) {}
  ~NoGCVerifier() {}
#endif
};

// A PauseNoGCVerifier is used to temporarily pause the behavior
// of a NoGCVerifier object. If we are not in debug mode or if the
// NoGCVerifier object has a _verifygc value of false, then there
// is nothing to do.

class PauseNoGCVerifier: public StackObj {
 private:
  NoGCVerifier * _ngcv;

 public:
#ifdef ASSERT
  PauseNoGCVerifier(NoGCVerifier * ngcv);
  ~PauseNoGCVerifier();
#else
  PauseNoGCVerifier(NoGCVerifier * ngcv) {}
  ~PauseNoGCVerifier() {}
#endif
};


// A NoSafepointVerifier object will throw an assertion failure if
// the current thread passes a possible safepoint while this object is
// instantiated. A safepoint, will either be: an oop allocation, blocking
// on a Mutex or JavaLock, or executing a VM operation.
//
// If StrictSafepointChecks is turned off, it degrades into a NoGCVerifier
//
class NoSafepointVerifier : public NoGCVerifier {
 friend class PauseNoSafepointVerifier;

 private:
  bool _activated;
  Thread *_thread;
 public:
#ifdef ASSERT
  NoSafepointVerifier(bool activated = true, bool verifygc = true ) :
    NoGCVerifier(verifygc),
    _activated(activated) {
    _thread = Thread::current();
    if (_activated) {
      _thread->_allow_allocation_count++;
      _thread->_allow_safepoint_count++;
    }
  }

  ~NoSafepointVerifier() {
    if (_activated) {
      _thread->_allow_allocation_count--;
      _thread->_allow_safepoint_count--;
    }
  }
#else
  NoSafepointVerifier(bool activated = true, bool verifygc = true) : NoGCVerifier(verifygc){}
  ~NoSafepointVerifier() {}
#endif
};

// A PauseNoSafepointVerifier is used to temporarily pause the
// behavior of a NoSafepointVerifier object. If we are not in debug
// mode then there is nothing to do. If the NoSafepointVerifier
// object has an _activated value of false, then there is nothing to
// do for safepoint and allocation checking, but there may still be
// something to do for the underlying NoGCVerifier object.

class PauseNoSafepointVerifier : public PauseNoGCVerifier {
 private:
  NoSafepointVerifier * _nsv;

 public:
#ifdef ASSERT
  PauseNoSafepointVerifier(NoSafepointVerifier * nsv)
    : PauseNoGCVerifier(nsv) {

    _nsv = nsv;
    if (_nsv->_activated) {
      _nsv->_thread->_allow_allocation_count--;
      _nsv->_thread->_allow_safepoint_count--;
    }
  }

  ~PauseNoSafepointVerifier() {
    if (_nsv->_activated) {
      _nsv->_thread->_allow_allocation_count++;
      _nsv->_thread->_allow_safepoint_count++;
    }
  }
#else
  PauseNoSafepointVerifier(NoSafepointVerifier * nsv)
    : PauseNoGCVerifier(nsv) {}
  ~PauseNoSafepointVerifier() {}
#endif
};

// A SkipGCALot object is used to elide the usual effect of gc-a-lot
// over a section of execution by a thread. Currently, it's used only to
// prevent re-entrant calls to GC.
class SkipGCALot : public StackObj {
  private:
   bool _saved;
   Thread* _t;

  public:
#ifdef ASSERT
    SkipGCALot(Thread* t) : _t(t) {
      _saved = _t->skip_gcalot();
      _t->set_skip_gcalot(true);
    }

    ~SkipGCALot() {
      assert(_t->skip_gcalot(), "Save-restore protocol invariant");
      _t->set_skip_gcalot(_saved);
    }
#else
    SkipGCALot(Thread* t) { }
    ~SkipGCALot() { }
#endif
};

// JRT_LEAF currently can be called from either _thread_in_Java or
// _thread_in_native mode. In _thread_in_native, it is ok
// for another thread to trigger GC. The rest of the JRT_LEAF
// rules apply.
class JRTLeafVerifier : public NoSafepointVerifier {
  static bool should_verify_GC();
 public:
#ifdef ASSERT
  JRTLeafVerifier();
  ~JRTLeafVerifier();
#else
  JRTLeafVerifier() {}
  ~JRTLeafVerifier() {}
#endif
};

// A NoAllocVerifier object can be placed in methods where one assumes that
// no allocation will occur. The destructor will verify this property
// unless the constructor is called with argument false (not activated).
//
// The check will only be done in debug mode and if activated.
// Note: this only makes sense at safepoints (otherwise, other threads may
// allocate concurrently.)

class NoAllocVerifier : public StackObj {
 private:
  bool  _activated;

 public:
#ifdef ASSERT
  NoAllocVerifier(bool activated = true) {
    _activated = activated;
    if (_activated) Thread::current()->_allow_allocation_count++;
  }

  ~NoAllocVerifier() {
    if (_activated) Thread::current()->_allow_allocation_count--;
  }
#else
  NoAllocVerifier(bool activated = true) {}
  ~NoAllocVerifier() {}
#endif
};

#endif // SHARE_VM_GC_SHARED_GCLOCKER_HPP

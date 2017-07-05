/*
 * Copyright (c) 1998, 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/vmSymbols.hpp"
#include "logging/log.hpp"
#include "memory/metaspaceShared.hpp"
#include "memory/padded.hpp"
#include "memory/resourceArea.hpp"
#include "oops/markOop.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomic.inline.hpp"
#include "runtime/biasedLocking.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/interfaceSupport.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/objectMonitor.hpp"
#include "runtime/objectMonitor.inline.hpp"
#include "runtime/osThread.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/synchronizer.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/vframe.hpp"
#include "trace/traceMacros.hpp"
#include "trace/tracing.hpp"
#include "utilities/dtrace.hpp"
#include "utilities/events.hpp"
#include "utilities/preserveException.hpp"

// The "core" versions of monitor enter and exit reside in this file.
// The interpreter and compilers contain specialized transliterated
// variants of the enter-exit fast-path operations.  See i486.ad fast_lock(),
// for instance.  If you make changes here, make sure to modify the
// interpreter, and both C1 and C2 fast-path inline locking code emission.
//
// -----------------------------------------------------------------------------

#ifdef DTRACE_ENABLED

// Only bother with this argument setup if dtrace is available
// TODO-FIXME: probes should not fire when caller is _blocked.  assert() accordingly.

#define DTRACE_MONITOR_PROBE_COMMON(obj, thread)                           \
  char* bytes = NULL;                                                      \
  int len = 0;                                                             \
  jlong jtid = SharedRuntime::get_java_tid(thread);                        \
  Symbol* klassname = ((oop)(obj))->klass()->name();                       \
  if (klassname != NULL) {                                                 \
    bytes = (char*)klassname->bytes();                                     \
    len = klassname->utf8_length();                                        \
  }

#define DTRACE_MONITOR_WAIT_PROBE(monitor, obj, thread, millis)            \
  {                                                                        \
    if (DTraceMonitorProbes) {                                             \
      DTRACE_MONITOR_PROBE_COMMON(obj, thread);                            \
      HOTSPOT_MONITOR_WAIT(jtid,                                           \
                           (uintptr_t)(monitor), bytes, len, (millis));    \
    }                                                                      \
  }

#define HOTSPOT_MONITOR_PROBE_notify HOTSPOT_MONITOR_NOTIFY
#define HOTSPOT_MONITOR_PROBE_notifyAll HOTSPOT_MONITOR_NOTIFYALL
#define HOTSPOT_MONITOR_PROBE_waited HOTSPOT_MONITOR_WAITED

#define DTRACE_MONITOR_PROBE(probe, monitor, obj, thread)                  \
  {                                                                        \
    if (DTraceMonitorProbes) {                                             \
      DTRACE_MONITOR_PROBE_COMMON(obj, thread);                            \
      HOTSPOT_MONITOR_PROBE_##probe(jtid, /* probe = waited */             \
                                    (uintptr_t)(monitor), bytes, len);     \
    }                                                                      \
  }

#else //  ndef DTRACE_ENABLED

#define DTRACE_MONITOR_WAIT_PROBE(obj, thread, millis, mon)    {;}
#define DTRACE_MONITOR_PROBE(probe, obj, thread, mon)          {;}

#endif // ndef DTRACE_ENABLED

// This exists only as a workaround of dtrace bug 6254741
int dtrace_waited_probe(ObjectMonitor* monitor, Handle obj, Thread* thr) {
  DTRACE_MONITOR_PROBE(waited, monitor, obj(), thr);
  return 0;
}

#define NINFLATIONLOCKS 256
static volatile intptr_t gInflationLocks[NINFLATIONLOCKS];

// global list of blocks of monitors
// gBlockList is really PaddedEnd<ObjectMonitor> *, but we don't
// want to expose the PaddedEnd template more than necessary.
ObjectMonitor * volatile ObjectSynchronizer::gBlockList = NULL;
// global monitor free list
ObjectMonitor * volatile ObjectSynchronizer::gFreeList  = NULL;
// global monitor in-use list, for moribund threads,
// monitors they inflated need to be scanned for deflation
ObjectMonitor * volatile ObjectSynchronizer::gOmInUseList  = NULL;
// count of entries in gOmInUseList
int ObjectSynchronizer::gOmInUseCount = 0;

static volatile intptr_t gListLock = 0;      // protects global monitor lists
static volatile int gMonitorFreeCount  = 0;  // # on gFreeList
static volatile int gMonitorPopulation = 0;  // # Extant -- in circulation

static void post_monitor_inflate_event(EventJavaMonitorInflate&,
                                       const oop,
                                       const ObjectSynchronizer::InflateCause);

#define CHAINMARKER (cast_to_oop<intptr_t>(-1))


// =====================> Quick functions

// The quick_* forms are special fast-path variants used to improve
// performance.  In the simplest case, a "quick_*" implementation could
// simply return false, in which case the caller will perform the necessary
// state transitions and call the slow-path form.
// The fast-path is designed to handle frequently arising cases in an efficient
// manner and is just a degenerate "optimistic" variant of the slow-path.
// returns true  -- to indicate the call was satisfied.
// returns false -- to indicate the call needs the services of the slow-path.
// A no-loitering ordinance is in effect for code in the quick_* family
// operators: safepoints or indefinite blocking (blocking that might span a
// safepoint) are forbidden. Generally the thread_state() is _in_Java upon
// entry.
//
// Consider: An interesting optimization is to have the JIT recognize the
// following common idiom:
//   synchronized (someobj) { .... ; notify(); }
// That is, we find a notify() or notifyAll() call that immediately precedes
// the monitorexit operation.  In that case the JIT could fuse the operations
// into a single notifyAndExit() runtime primitive.

bool ObjectSynchronizer::quick_notify(oopDesc * obj, Thread * self, bool all) {
  assert(!SafepointSynchronize::is_at_safepoint(), "invariant");
  assert(self->is_Java_thread(), "invariant");
  assert(((JavaThread *) self)->thread_state() == _thread_in_Java, "invariant");
  NoSafepointVerifier nsv;
  if (obj == NULL) return false;  // slow-path for invalid obj
  const markOop mark = obj->mark();

  if (mark->has_locker() && self->is_lock_owned((address)mark->locker())) {
    // Degenerate notify
    // stack-locked by caller so by definition the implied waitset is empty.
    return true;
  }

  if (mark->has_monitor()) {
    ObjectMonitor * const mon = mark->monitor();
    assert(mon->object() == obj, "invariant");
    if (mon->owner() != self) return false;  // slow-path for IMS exception

    if (mon->first_waiter() != NULL) {
      // We have one or more waiters. Since this is an inflated monitor
      // that we own, we can transfer one or more threads from the waitset
      // to the entrylist here and now, avoiding the slow-path.
      if (all) {
        DTRACE_MONITOR_PROBE(notifyAll, mon, obj, self);
      } else {
        DTRACE_MONITOR_PROBE(notify, mon, obj, self);
      }
      int tally = 0;
      do {
        mon->INotify(self);
        ++tally;
      } while (mon->first_waiter() != NULL && all);
      OM_PERFDATA_OP(Notifications, inc(tally));
    }
    return true;
  }

  // biased locking and any other IMS exception states take the slow-path
  return false;
}


// The LockNode emitted directly at the synchronization site would have
// been too big if it were to have included support for the cases of inflated
// recursive enter and exit, so they go here instead.
// Note that we can't safely call AsyncPrintJavaStack() from within
// quick_enter() as our thread state remains _in_Java.

bool ObjectSynchronizer::quick_enter(oop obj, Thread * Self,
                                     BasicLock * lock) {
  assert(!SafepointSynchronize::is_at_safepoint(), "invariant");
  assert(Self->is_Java_thread(), "invariant");
  assert(((JavaThread *) Self)->thread_state() == _thread_in_Java, "invariant");
  NoSafepointVerifier nsv;
  if (obj == NULL) return false;       // Need to throw NPE
  const markOop mark = obj->mark();

  if (mark->has_monitor()) {
    ObjectMonitor * const m = mark->monitor();
    assert(m->object() == obj, "invariant");
    Thread * const owner = (Thread *) m->_owner;

    // Lock contention and Transactional Lock Elision (TLE) diagnostics
    // and observability
    // Case: light contention possibly amenable to TLE
    // Case: TLE inimical operations such as nested/recursive synchronization

    if (owner == Self) {
      m->_recursions++;
      return true;
    }

    // This Java Monitor is inflated so obj's header will never be
    // displaced to this thread's BasicLock. Make the displaced header
    // non-NULL so this BasicLock is not seen as recursive nor as
    // being locked. We do this unconditionally so that this thread's
    // BasicLock cannot be mis-interpreted by any stack walkers. For
    // performance reasons, stack walkers generally first check for
    // Biased Locking in the object's header, the second check is for
    // stack-locking in the object's header, the third check is for
    // recursive stack-locking in the displaced header in the BasicLock,
    // and last are the inflated Java Monitor (ObjectMonitor) checks.
    lock->set_displaced_header(markOopDesc::unused_mark());

    if (owner == NULL &&
        Atomic::cmpxchg_ptr(Self, &(m->_owner), NULL) == NULL) {
      assert(m->_recursions == 0, "invariant");
      assert(m->_owner == Self, "invariant");
      return true;
    }
  }

  // Note that we could inflate in quick_enter.
  // This is likely a useful optimization
  // Critically, in quick_enter() we must not:
  // -- perform bias revocation, or
  // -- block indefinitely, or
  // -- reach a safepoint

  return false;        // revert to slow-path
}

// -----------------------------------------------------------------------------
//  Fast Monitor Enter/Exit
// This the fast monitor enter. The interpreter and compiler use
// some assembly copies of this code. Make sure update those code
// if the following function is changed. The implementation is
// extremely sensitive to race condition. Be careful.

void ObjectSynchronizer::fast_enter(Handle obj, BasicLock* lock,
                                    bool attempt_rebias, TRAPS) {
  if (UseBiasedLocking) {
    if (!SafepointSynchronize::is_at_safepoint()) {
      BiasedLocking::Condition cond = BiasedLocking::revoke_and_rebias(obj, attempt_rebias, THREAD);
      if (cond == BiasedLocking::BIAS_REVOKED_AND_REBIASED) {
        return;
      }
    } else {
      assert(!attempt_rebias, "can not rebias toward VM thread");
      BiasedLocking::revoke_at_safepoint(obj);
    }
    assert(!obj->mark()->has_bias_pattern(), "biases should be revoked by now");
  }

  slow_enter(obj, lock, THREAD);
}

void ObjectSynchronizer::fast_exit(oop object, BasicLock* lock, TRAPS) {
  markOop mark = object->mark();
  // We cannot check for Biased Locking if we are racing an inflation.
  assert(mark == markOopDesc::INFLATING() ||
         !mark->has_bias_pattern(), "should not see bias pattern here");

  markOop dhw = lock->displaced_header();
  if (dhw == NULL) {
    // If the displaced header is NULL, then this exit matches up with
    // a recursive enter. No real work to do here except for diagnostics.
#ifndef PRODUCT
    if (mark != markOopDesc::INFLATING()) {
      // Only do diagnostics if we are not racing an inflation. Simply
      // exiting a recursive enter of a Java Monitor that is being
      // inflated is safe; see the has_monitor() comment below.
      assert(!mark->is_neutral(), "invariant");
      assert(!mark->has_locker() ||
             THREAD->is_lock_owned((address)mark->locker()), "invariant");
      if (mark->has_monitor()) {
        // The BasicLock's displaced_header is marked as a recursive
        // enter and we have an inflated Java Monitor (ObjectMonitor).
        // This is a special case where the Java Monitor was inflated
        // after this thread entered the stack-lock recursively. When a
        // Java Monitor is inflated, we cannot safely walk the Java
        // Monitor owner's stack and update the BasicLocks because a
        // Java Monitor can be asynchronously inflated by a thread that
        // does not own the Java Monitor.
        ObjectMonitor * m = mark->monitor();
        assert(((oop)(m->object()))->mark() == mark, "invariant");
        assert(m->is_entered(THREAD), "invariant");
      }
    }
#endif
    return;
  }

  if (mark == (markOop) lock) {
    // If the object is stack-locked by the current thread, try to
    // swing the displaced header from the BasicLock back to the mark.
    assert(dhw->is_neutral(), "invariant");
    if ((markOop) Atomic::cmpxchg_ptr(dhw, object->mark_addr(), mark) == mark) {
      TEVENT(fast_exit: release stack-lock);
      return;
    }
  }

  // We have to take the slow-path of possible inflation and then exit.
  ObjectSynchronizer::inflate(THREAD,
                              object,
                              inflate_cause_vm_internal)->exit(true, THREAD);
}

// -----------------------------------------------------------------------------
// Interpreter/Compiler Slow Case
// This routine is used to handle interpreter/compiler slow case
// We don't need to use fast path here, because it must have been
// failed in the interpreter/compiler code.
void ObjectSynchronizer::slow_enter(Handle obj, BasicLock* lock, TRAPS) {
  markOop mark = obj->mark();
  assert(!mark->has_bias_pattern(), "should not see bias pattern here");

  if (mark->is_neutral()) {
    // Anticipate successful CAS -- the ST of the displaced mark must
    // be visible <= the ST performed by the CAS.
    lock->set_displaced_header(mark);
    if (mark == (markOop) Atomic::cmpxchg_ptr(lock, obj()->mark_addr(), mark)) {
      TEVENT(slow_enter: release stacklock);
      return;
    }
    // Fall through to inflate() ...
  } else if (mark->has_locker() &&
             THREAD->is_lock_owned((address)mark->locker())) {
    assert(lock != mark->locker(), "must not re-lock the same lock");
    assert(lock != (BasicLock*)obj->mark(), "don't relock with same BasicLock");
    lock->set_displaced_header(NULL);
    return;
  }

  // The object header will never be displaced to this lock,
  // so it does not matter what the value is, except that it
  // must be non-zero to avoid looking like a re-entrant lock,
  // and must not look locked either.
  lock->set_displaced_header(markOopDesc::unused_mark());
  ObjectSynchronizer::inflate(THREAD,
                              obj(),
                              inflate_cause_monitor_enter)->enter(THREAD);
}

// This routine is used to handle interpreter/compiler slow case
// We don't need to use fast path here, because it must have
// failed in the interpreter/compiler code. Simply use the heavy
// weight monitor should be ok, unless someone find otherwise.
void ObjectSynchronizer::slow_exit(oop object, BasicLock* lock, TRAPS) {
  fast_exit(object, lock, THREAD);
}

// -----------------------------------------------------------------------------
// Class Loader  support to workaround deadlocks on the class loader lock objects
// Also used by GC
// complete_exit()/reenter() are used to wait on a nested lock
// i.e. to give up an outer lock completely and then re-enter
// Used when holding nested locks - lock acquisition order: lock1 then lock2
//  1) complete_exit lock1 - saving recursion count
//  2) wait on lock2
//  3) when notified on lock2, unlock lock2
//  4) reenter lock1 with original recursion count
//  5) lock lock2
// NOTE: must use heavy weight monitor to handle complete_exit/reenter()
intptr_t ObjectSynchronizer::complete_exit(Handle obj, TRAPS) {
  TEVENT(complete_exit);
  if (UseBiasedLocking) {
    BiasedLocking::revoke_and_rebias(obj, false, THREAD);
    assert(!obj->mark()->has_bias_pattern(), "biases should be revoked by now");
  }

  ObjectMonitor* monitor = ObjectSynchronizer::inflate(THREAD,
                                                       obj(),
                                                       inflate_cause_vm_internal);

  return monitor->complete_exit(THREAD);
}

// NOTE: must use heavy weight monitor to handle complete_exit/reenter()
void ObjectSynchronizer::reenter(Handle obj, intptr_t recursion, TRAPS) {
  TEVENT(reenter);
  if (UseBiasedLocking) {
    BiasedLocking::revoke_and_rebias(obj, false, THREAD);
    assert(!obj->mark()->has_bias_pattern(), "biases should be revoked by now");
  }

  ObjectMonitor* monitor = ObjectSynchronizer::inflate(THREAD,
                                                       obj(),
                                                       inflate_cause_vm_internal);

  monitor->reenter(recursion, THREAD);
}
// -----------------------------------------------------------------------------
// JNI locks on java objects
// NOTE: must use heavy weight monitor to handle jni monitor enter
void ObjectSynchronizer::jni_enter(Handle obj, TRAPS) {
  // the current locking is from JNI instead of Java code
  TEVENT(jni_enter);
  if (UseBiasedLocking) {
    BiasedLocking::revoke_and_rebias(obj, false, THREAD);
    assert(!obj->mark()->has_bias_pattern(), "biases should be revoked by now");
  }
  THREAD->set_current_pending_monitor_is_from_java(false);
  ObjectSynchronizer::inflate(THREAD, obj(), inflate_cause_jni_enter)->enter(THREAD);
  THREAD->set_current_pending_monitor_is_from_java(true);
}

// NOTE: must use heavy weight monitor to handle jni monitor exit
void ObjectSynchronizer::jni_exit(oop obj, Thread* THREAD) {
  TEVENT(jni_exit);
  if (UseBiasedLocking) {
    Handle h_obj(THREAD, obj);
    BiasedLocking::revoke_and_rebias(h_obj, false, THREAD);
    obj = h_obj();
  }
  assert(!obj->mark()->has_bias_pattern(), "biases should be revoked by now");

  ObjectMonitor* monitor = ObjectSynchronizer::inflate(THREAD,
                                                       obj,
                                                       inflate_cause_jni_exit);
  // If this thread has locked the object, exit the monitor.  Note:  can't use
  // monitor->check(CHECK); must exit even if an exception is pending.
  if (monitor->check(THREAD)) {
    monitor->exit(true, THREAD);
  }
}

// -----------------------------------------------------------------------------
// Internal VM locks on java objects
// standard constructor, allows locking failures
ObjectLocker::ObjectLocker(Handle obj, Thread* thread, bool doLock) {
  _dolock = doLock;
  _thread = thread;
  debug_only(if (StrictSafepointChecks) _thread->check_for_valid_safepoint_state(false);)
  _obj = obj;

  if (_dolock) {
    TEVENT(ObjectLocker);

    ObjectSynchronizer::fast_enter(_obj, &_lock, false, _thread);
  }
}

ObjectLocker::~ObjectLocker() {
  if (_dolock) {
    ObjectSynchronizer::fast_exit(_obj(), &_lock, _thread);
  }
}


// -----------------------------------------------------------------------------
//  Wait/Notify/NotifyAll
// NOTE: must use heavy weight monitor to handle wait()
int ObjectSynchronizer::wait(Handle obj, jlong millis, TRAPS) {
  if (UseBiasedLocking) {
    BiasedLocking::revoke_and_rebias(obj, false, THREAD);
    assert(!obj->mark()->has_bias_pattern(), "biases should be revoked by now");
  }
  if (millis < 0) {
    TEVENT(wait - throw IAX);
    THROW_MSG_0(vmSymbols::java_lang_IllegalArgumentException(), "timeout value is negative");
  }
  ObjectMonitor* monitor = ObjectSynchronizer::inflate(THREAD,
                                                       obj(),
                                                       inflate_cause_wait);

  DTRACE_MONITOR_WAIT_PROBE(monitor, obj(), THREAD, millis);
  monitor->wait(millis, true, THREAD);

  // This dummy call is in place to get around dtrace bug 6254741.  Once
  // that's fixed we can uncomment the following line, remove the call
  // and change this function back into a "void" func.
  // DTRACE_MONITOR_PROBE(waited, monitor, obj(), THREAD);
  return dtrace_waited_probe(monitor, obj, THREAD);
}

void ObjectSynchronizer::waitUninterruptibly(Handle obj, jlong millis, TRAPS) {
  if (UseBiasedLocking) {
    BiasedLocking::revoke_and_rebias(obj, false, THREAD);
    assert(!obj->mark()->has_bias_pattern(), "biases should be revoked by now");
  }
  if (millis < 0) {
    TEVENT(wait - throw IAX);
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(), "timeout value is negative");
  }
  ObjectSynchronizer::inflate(THREAD,
                              obj(),
                              inflate_cause_wait)->wait(millis, false, THREAD);
}

void ObjectSynchronizer::notify(Handle obj, TRAPS) {
  if (UseBiasedLocking) {
    BiasedLocking::revoke_and_rebias(obj, false, THREAD);
    assert(!obj->mark()->has_bias_pattern(), "biases should be revoked by now");
  }

  markOop mark = obj->mark();
  if (mark->has_locker() && THREAD->is_lock_owned((address)mark->locker())) {
    return;
  }
  ObjectSynchronizer::inflate(THREAD,
                              obj(),
                              inflate_cause_notify)->notify(THREAD);
}

// NOTE: see comment of notify()
void ObjectSynchronizer::notifyall(Handle obj, TRAPS) {
  if (UseBiasedLocking) {
    BiasedLocking::revoke_and_rebias(obj, false, THREAD);
    assert(!obj->mark()->has_bias_pattern(), "biases should be revoked by now");
  }

  markOop mark = obj->mark();
  if (mark->has_locker() && THREAD->is_lock_owned((address)mark->locker())) {
    return;
  }
  ObjectSynchronizer::inflate(THREAD,
                              obj(),
                              inflate_cause_notify)->notifyAll(THREAD);
}

// -----------------------------------------------------------------------------
// Hash Code handling
//
// Performance concern:
// OrderAccess::storestore() calls release() which at one time stored 0
// into the global volatile OrderAccess::dummy variable. This store was
// unnecessary for correctness. Many threads storing into a common location
// causes considerable cache migration or "sloshing" on large SMP systems.
// As such, I avoided using OrderAccess::storestore(). In some cases
// OrderAccess::fence() -- which incurs local latency on the executing
// processor -- is a better choice as it scales on SMP systems.
//
// See http://blogs.oracle.com/dave/entry/biased_locking_in_hotspot for
// a discussion of coherency costs. Note that all our current reference
// platforms provide strong ST-ST order, so the issue is moot on IA32,
// x64, and SPARC.
//
// As a general policy we use "volatile" to control compiler-based reordering
// and explicit fences (barriers) to control for architectural reordering
// performed by the CPU(s) or platform.

struct SharedGlobals {
  char         _pad_prefix[DEFAULT_CACHE_LINE_SIZE];
  // These are highly shared mostly-read variables.
  // To avoid false-sharing they need to be the sole occupants of a cache line.
  volatile int stwRandom;
  volatile int stwCycle;
  DEFINE_PAD_MINUS_SIZE(1, DEFAULT_CACHE_LINE_SIZE, sizeof(volatile int) * 2);
  // Hot RW variable -- Sequester to avoid false-sharing
  volatile int hcSequence;
  DEFINE_PAD_MINUS_SIZE(2, DEFAULT_CACHE_LINE_SIZE, sizeof(volatile int));
};

static SharedGlobals GVars;
static int MonitorScavengeThreshold = 1000000;
static volatile int ForceMonitorScavenge = 0; // Scavenge required and pending

static markOop ReadStableMark(oop obj) {
  markOop mark = obj->mark();
  if (!mark->is_being_inflated()) {
    return mark;       // normal fast-path return
  }

  int its = 0;
  for (;;) {
    markOop mark = obj->mark();
    if (!mark->is_being_inflated()) {
      return mark;    // normal fast-path return
    }

    // The object is being inflated by some other thread.
    // The caller of ReadStableMark() must wait for inflation to complete.
    // Avoid live-lock
    // TODO: consider calling SafepointSynchronize::do_call_back() while
    // spinning to see if there's a safepoint pending.  If so, immediately
    // yielding or blocking would be appropriate.  Avoid spinning while
    // there is a safepoint pending.
    // TODO: add inflation contention performance counters.
    // TODO: restrict the aggregate number of spinners.

    ++its;
    if (its > 10000 || !os::is_MP()) {
      if (its & 1) {
        os::naked_yield();
        TEVENT(Inflate: INFLATING - yield);
      } else {
        // Note that the following code attenuates the livelock problem but is not
        // a complete remedy.  A more complete solution would require that the inflating
        // thread hold the associated inflation lock.  The following code simply restricts
        // the number of spinners to at most one.  We'll have N-2 threads blocked
        // on the inflationlock, 1 thread holding the inflation lock and using
        // a yield/park strategy, and 1 thread in the midst of inflation.
        // A more refined approach would be to change the encoding of INFLATING
        // to allow encapsulation of a native thread pointer.  Threads waiting for
        // inflation to complete would use CAS to push themselves onto a singly linked
        // list rooted at the markword.  Once enqueued, they'd loop, checking a per-thread flag
        // and calling park().  When inflation was complete the thread that accomplished inflation
        // would detach the list and set the markword to inflated with a single CAS and
        // then for each thread on the list, set the flag and unpark() the thread.
        // This is conceptually similar to muxAcquire-muxRelease, except that muxRelease
        // wakes at most one thread whereas we need to wake the entire list.
        int ix = (cast_from_oop<intptr_t>(obj) >> 5) & (NINFLATIONLOCKS-1);
        int YieldThenBlock = 0;
        assert(ix >= 0 && ix < NINFLATIONLOCKS, "invariant");
        assert((NINFLATIONLOCKS & (NINFLATIONLOCKS-1)) == 0, "invariant");
        Thread::muxAcquire(gInflationLocks + ix, "gInflationLock");
        while (obj->mark() == markOopDesc::INFLATING()) {
          // Beware: NakedYield() is advisory and has almost no effect on some platforms
          // so we periodically call Self->_ParkEvent->park(1).
          // We use a mixed spin/yield/block mechanism.
          if ((YieldThenBlock++) >= 16) {
            Thread::current()->_ParkEvent->park(1);
          } else {
            os::naked_yield();
          }
        }
        Thread::muxRelease(gInflationLocks + ix);
        TEVENT(Inflate: INFLATING - yield/park);
      }
    } else {
      SpinPause();       // SMP-polite spinning
    }
  }
}

// hashCode() generation :
//
// Possibilities:
// * MD5Digest of {obj,stwRandom}
// * CRC32 of {obj,stwRandom} or any linear-feedback shift register function.
// * A DES- or AES-style SBox[] mechanism
// * One of the Phi-based schemes, such as:
//   2654435761 = 2^32 * Phi (golden ratio)
//   HashCodeValue = ((uintptr_t(obj) >> 3) * 2654435761) ^ GVars.stwRandom ;
// * A variation of Marsaglia's shift-xor RNG scheme.
// * (obj ^ stwRandom) is appealing, but can result
//   in undesirable regularity in the hashCode values of adjacent objects
//   (objects allocated back-to-back, in particular).  This could potentially
//   result in hashtable collisions and reduced hashtable efficiency.
//   There are simple ways to "diffuse" the middle address bits over the
//   generated hashCode values:

static inline intptr_t get_next_hash(Thread * Self, oop obj) {
  intptr_t value = 0;
  if (hashCode == 0) {
    // This form uses an unguarded global Park-Miller RNG,
    // so it's possible for two threads to race and generate the same RNG.
    // On MP system we'll have lots of RW access to a global, so the
    // mechanism induces lots of coherency traffic.
    value = os::random();
  } else if (hashCode == 1) {
    // This variation has the property of being stable (idempotent)
    // between STW operations.  This can be useful in some of the 1-0
    // synchronization schemes.
    intptr_t addrBits = cast_from_oop<intptr_t>(obj) >> 3;
    value = addrBits ^ (addrBits >> 5) ^ GVars.stwRandom;
  } else if (hashCode == 2) {
    value = 1;            // for sensitivity testing
  } else if (hashCode == 3) {
    value = ++GVars.hcSequence;
  } else if (hashCode == 4) {
    value = cast_from_oop<intptr_t>(obj);
  } else {
    // Marsaglia's xor-shift scheme with thread-specific state
    // This is probably the best overall implementation -- we'll
    // likely make this the default in future releases.
    unsigned t = Self->_hashStateX;
    t ^= (t << 11);
    Self->_hashStateX = Self->_hashStateY;
    Self->_hashStateY = Self->_hashStateZ;
    Self->_hashStateZ = Self->_hashStateW;
    unsigned v = Self->_hashStateW;
    v = (v ^ (v >> 19)) ^ (t ^ (t >> 8));
    Self->_hashStateW = v;
    value = v;
  }

  value &= markOopDesc::hash_mask;
  if (value == 0) value = 0xBAD;
  assert(value != markOopDesc::no_hash, "invariant");
  TEVENT(hashCode: GENERATE);
  return value;
}

intptr_t ObjectSynchronizer::FastHashCode(Thread * Self, oop obj) {
  if (UseBiasedLocking) {
    // NOTE: many places throughout the JVM do not expect a safepoint
    // to be taken here, in particular most operations on perm gen
    // objects. However, we only ever bias Java instances and all of
    // the call sites of identity_hash that might revoke biases have
    // been checked to make sure they can handle a safepoint. The
    // added check of the bias pattern is to avoid useless calls to
    // thread-local storage.
    if (obj->mark()->has_bias_pattern()) {
      // Handle for oop obj in case of STW safepoint
      Handle hobj(Self, obj);
      // Relaxing assertion for bug 6320749.
      assert(Universe::verify_in_progress() ||
             !SafepointSynchronize::is_at_safepoint(),
             "biases should not be seen by VM thread here");
      BiasedLocking::revoke_and_rebias(hobj, false, JavaThread::current());
      obj = hobj();
      assert(!obj->mark()->has_bias_pattern(), "biases should be revoked by now");
    }
  }

  // hashCode() is a heap mutator ...
  // Relaxing assertion for bug 6320749.
  assert(Universe::verify_in_progress() || DumpSharedSpaces ||
         !SafepointSynchronize::is_at_safepoint(), "invariant");
  assert(Universe::verify_in_progress() || DumpSharedSpaces ||
         Self->is_Java_thread() , "invariant");
  assert(Universe::verify_in_progress() || DumpSharedSpaces ||
         ((JavaThread *)Self)->thread_state() != _thread_blocked, "invariant");

  ObjectMonitor* monitor = NULL;
  markOop temp, test;
  intptr_t hash;
  markOop mark = ReadStableMark(obj);

  // object should remain ineligible for biased locking
  assert(!mark->has_bias_pattern(), "invariant");

  if (mark->is_neutral()) {
    hash = mark->hash();              // this is a normal header
    if (hash) {                       // if it has hash, just return it
      return hash;
    }
    hash = get_next_hash(Self, obj);  // allocate a new hash code
    temp = mark->copy_set_hash(hash); // merge the hash code into header
    // use (machine word version) atomic operation to install the hash
    test = (markOop) Atomic::cmpxchg_ptr(temp, obj->mark_addr(), mark);
    if (test == mark) {
      return hash;
    }
    // If atomic operation failed, we must inflate the header
    // into heavy weight monitor. We could add more code here
    // for fast path, but it does not worth the complexity.
  } else if (mark->has_monitor()) {
    monitor = mark->monitor();
    temp = monitor->header();
    assert(temp->is_neutral(), "invariant");
    hash = temp->hash();
    if (hash) {
      return hash;
    }
    // Skip to the following code to reduce code size
  } else if (Self->is_lock_owned((address)mark->locker())) {
    temp = mark->displaced_mark_helper(); // this is a lightweight monitor owned
    assert(temp->is_neutral(), "invariant");
    hash = temp->hash();              // by current thread, check if the displaced
    if (hash) {                       // header contains hash code
      return hash;
    }
    // WARNING:
    //   The displaced header is strictly immutable.
    // It can NOT be changed in ANY cases. So we have
    // to inflate the header into heavyweight monitor
    // even the current thread owns the lock. The reason
    // is the BasicLock (stack slot) will be asynchronously
    // read by other threads during the inflate() function.
    // Any change to stack may not propagate to other threads
    // correctly.
  }

  // Inflate the monitor to set hash code
  monitor = ObjectSynchronizer::inflate(Self, obj, inflate_cause_hash_code);
  // Load displaced header and check it has hash code
  mark = monitor->header();
  assert(mark->is_neutral(), "invariant");
  hash = mark->hash();
  if (hash == 0) {
    hash = get_next_hash(Self, obj);
    temp = mark->copy_set_hash(hash); // merge hash code into header
    assert(temp->is_neutral(), "invariant");
    test = (markOop) Atomic::cmpxchg_ptr(temp, monitor, mark);
    if (test != mark) {
      // The only update to the header in the monitor (outside GC)
      // is install the hash code. If someone add new usage of
      // displaced header, please update this code
      hash = test->hash();
      assert(test->is_neutral(), "invariant");
      assert(hash != 0, "Trivial unexpected object/monitor header usage.");
    }
  }
  // We finally get the hash
  return hash;
}

// Deprecated -- use FastHashCode() instead.

intptr_t ObjectSynchronizer::identity_hash_value_for(Handle obj) {
  return FastHashCode(Thread::current(), obj());
}


bool ObjectSynchronizer::current_thread_holds_lock(JavaThread* thread,
                                                   Handle h_obj) {
  if (UseBiasedLocking) {
    BiasedLocking::revoke_and_rebias(h_obj, false, thread);
    assert(!h_obj->mark()->has_bias_pattern(), "biases should be revoked by now");
  }

  assert(thread == JavaThread::current(), "Can only be called on current thread");
  oop obj = h_obj();

  markOop mark = ReadStableMark(obj);

  // Uncontended case, header points to stack
  if (mark->has_locker()) {
    return thread->is_lock_owned((address)mark->locker());
  }
  // Contended case, header points to ObjectMonitor (tagged pointer)
  if (mark->has_monitor()) {
    ObjectMonitor* monitor = mark->monitor();
    return monitor->is_entered(thread) != 0;
  }
  // Unlocked case, header in place
  assert(mark->is_neutral(), "sanity check");
  return false;
}

// Be aware of this method could revoke bias of the lock object.
// This method queries the ownership of the lock handle specified by 'h_obj'.
// If the current thread owns the lock, it returns owner_self. If no
// thread owns the lock, it returns owner_none. Otherwise, it will return
// owner_other.
ObjectSynchronizer::LockOwnership ObjectSynchronizer::query_lock_ownership
(JavaThread *self, Handle h_obj) {
  // The caller must beware this method can revoke bias, and
  // revocation can result in a safepoint.
  assert(!SafepointSynchronize::is_at_safepoint(), "invariant");
  assert(self->thread_state() != _thread_blocked, "invariant");

  // Possible mark states: neutral, biased, stack-locked, inflated

  if (UseBiasedLocking && h_obj()->mark()->has_bias_pattern()) {
    // CASE: biased
    BiasedLocking::revoke_and_rebias(h_obj, false, self);
    assert(!h_obj->mark()->has_bias_pattern(),
           "biases should be revoked by now");
  }

  assert(self == JavaThread::current(), "Can only be called on current thread");
  oop obj = h_obj();
  markOop mark = ReadStableMark(obj);

  // CASE: stack-locked.  Mark points to a BasicLock on the owner's stack.
  if (mark->has_locker()) {
    return self->is_lock_owned((address)mark->locker()) ?
      owner_self : owner_other;
  }

  // CASE: inflated. Mark (tagged pointer) points to an objectMonitor.
  // The Object:ObjectMonitor relationship is stable as long as we're
  // not at a safepoint.
  if (mark->has_monitor()) {
    void * owner = mark->monitor()->_owner;
    if (owner == NULL) return owner_none;
    return (owner == self ||
            self->is_lock_owned((address)owner)) ? owner_self : owner_other;
  }

  // CASE: neutral
  assert(mark->is_neutral(), "sanity check");
  return owner_none;           // it's unlocked
}

// FIXME: jvmti should call this
JavaThread* ObjectSynchronizer::get_lock_owner(Handle h_obj, bool doLock) {
  if (UseBiasedLocking) {
    if (SafepointSynchronize::is_at_safepoint()) {
      BiasedLocking::revoke_at_safepoint(h_obj);
    } else {
      BiasedLocking::revoke_and_rebias(h_obj, false, JavaThread::current());
    }
    assert(!h_obj->mark()->has_bias_pattern(), "biases should be revoked by now");
  }

  oop obj = h_obj();
  address owner = NULL;

  markOop mark = ReadStableMark(obj);

  // Uncontended case, header points to stack
  if (mark->has_locker()) {
    owner = (address) mark->locker();
  }

  // Contended case, header points to ObjectMonitor (tagged pointer)
  if (mark->has_monitor()) {
    ObjectMonitor* monitor = mark->monitor();
    assert(monitor != NULL, "monitor should be non-null");
    owner = (address) monitor->owner();
  }

  if (owner != NULL) {
    // owning_thread_from_monitor_owner() may also return NULL here
    return Threads::owning_thread_from_monitor_owner(owner, doLock);
  }

  // Unlocked case, header in place
  // Cannot have assertion since this object may have been
  // locked by another thread when reaching here.
  // assert(mark->is_neutral(), "sanity check");

  return NULL;
}

// Visitors ...

void ObjectSynchronizer::monitors_iterate(MonitorClosure* closure) {
  PaddedEnd<ObjectMonitor> * block =
    (PaddedEnd<ObjectMonitor> *)OrderAccess::load_ptr_acquire(&gBlockList);
  while (block != NULL) {
    assert(block->object() == CHAINMARKER, "must be a block header");
    for (int i = _BLOCKSIZE - 1; i > 0; i--) {
      ObjectMonitor* mid = (ObjectMonitor *)(block + i);
      oop object = (oop)mid->object();
      if (object != NULL) {
        closure->do_monitor(mid);
      }
    }
    block = (PaddedEnd<ObjectMonitor> *)block->FreeNext;
  }
}

// Get the next block in the block list.
static inline ObjectMonitor* next(ObjectMonitor* block) {
  assert(block->object() == CHAINMARKER, "must be a block header");
  block = block->FreeNext;
  assert(block == NULL || block->object() == CHAINMARKER, "must be a block header");
  return block;
}


void ObjectSynchronizer::oops_do(OopClosure* f) {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at safepoint");
  PaddedEnd<ObjectMonitor> * block =
    (PaddedEnd<ObjectMonitor> *)OrderAccess::load_ptr_acquire(&gBlockList);
  for (; block != NULL; block = (PaddedEnd<ObjectMonitor> *)next(block)) {
    assert(block->object() == CHAINMARKER, "must be a block header");
    for (int i = 1; i < _BLOCKSIZE; i++) {
      ObjectMonitor* mid = (ObjectMonitor *)&block[i];
      if (mid->object() != NULL) {
        f->do_oop((oop*)mid->object_addr());
      }
    }
  }
}


// -----------------------------------------------------------------------------
// ObjectMonitor Lifecycle
// -----------------------
// Inflation unlinks monitors from the global gFreeList and
// associates them with objects.  Deflation -- which occurs at
// STW-time -- disassociates idle monitors from objects.  Such
// scavenged monitors are returned to the gFreeList.
//
// The global list is protected by gListLock.  All the critical sections
// are short and operate in constant-time.
//
// ObjectMonitors reside in type-stable memory (TSM) and are immortal.
//
// Lifecycle:
// --   unassigned and on the global free list
// --   unassigned and on a thread's private omFreeList
// --   assigned to an object.  The object is inflated and the mark refers
//      to the objectmonitor.


// Constraining monitor pool growth via MonitorBound ...
//
// The monitor pool is grow-only.  We scavenge at STW safepoint-time, but the
// the rate of scavenging is driven primarily by GC.  As such,  we can find
// an inordinate number of monitors in circulation.
// To avoid that scenario we can artificially induce a STW safepoint
// if the pool appears to be growing past some reasonable bound.
// Generally we favor time in space-time tradeoffs, but as there's no
// natural back-pressure on the # of extant monitors we need to impose some
// type of limit.  Beware that if MonitorBound is set to too low a value
// we could just loop. In addition, if MonitorBound is set to a low value
// we'll incur more safepoints, which are harmful to performance.
// See also: GuaranteedSafepointInterval
//
// The current implementation uses asynchronous VM operations.

static void InduceScavenge(Thread * Self, const char * Whence) {
  // Induce STW safepoint to trim monitors
  // Ultimately, this results in a call to deflate_idle_monitors() in the near future.
  // More precisely, trigger an asynchronous STW safepoint as the number
  // of active monitors passes the specified threshold.
  // TODO: assert thread state is reasonable

  if (ForceMonitorScavenge == 0 && Atomic::xchg (1, &ForceMonitorScavenge) == 0) {
    if (ObjectMonitor::Knob_Verbose) {
      tty->print_cr("INFO: Monitor scavenge - Induced STW @%s (%d)",
                    Whence, ForceMonitorScavenge) ;
      tty->flush();
    }
    // Induce a 'null' safepoint to scavenge monitors
    // Must VM_Operation instance be heap allocated as the op will be enqueue and posted
    // to the VMthread and have a lifespan longer than that of this activation record.
    // The VMThread will delete the op when completed.
    VMThread::execute(new VM_ForceAsyncSafepoint());

    if (ObjectMonitor::Knob_Verbose) {
      tty->print_cr("INFO: Monitor scavenge - STW posted @%s (%d)",
                    Whence, ForceMonitorScavenge) ;
      tty->flush();
    }
  }
}

void ObjectSynchronizer::verifyInUse(Thread *Self) {
  ObjectMonitor* mid;
  int in_use_tally = 0;
  for (mid = Self->omInUseList; mid != NULL; mid = mid->FreeNext) {
    in_use_tally++;
  }
  assert(in_use_tally == Self->omInUseCount, "in-use count off");

  int free_tally = 0;
  for (mid = Self->omFreeList; mid != NULL; mid = mid->FreeNext) {
    free_tally++;
  }
  assert(free_tally == Self->omFreeCount, "free count off");
}

ObjectMonitor* ObjectSynchronizer::omAlloc(Thread * Self) {
  // A large MAXPRIVATE value reduces both list lock contention
  // and list coherency traffic, but also tends to increase the
  // number of objectMonitors in circulation as well as the STW
  // scavenge costs.  As usual, we lean toward time in space-time
  // tradeoffs.
  const int MAXPRIVATE = 1024;
  for (;;) {
    ObjectMonitor * m;

    // 1: try to allocate from the thread's local omFreeList.
    // Threads will attempt to allocate first from their local list, then
    // from the global list, and only after those attempts fail will the thread
    // attempt to instantiate new monitors.   Thread-local free lists take
    // heat off the gListLock and improve allocation latency, as well as reducing
    // coherency traffic on the shared global list.
    m = Self->omFreeList;
    if (m != NULL) {
      Self->omFreeList = m->FreeNext;
      Self->omFreeCount--;
      // CONSIDER: set m->FreeNext = BAD -- diagnostic hygiene
      guarantee(m->object() == NULL, "invariant");
      if (MonitorInUseLists) {
        m->FreeNext = Self->omInUseList;
        Self->omInUseList = m;
        Self->omInUseCount++;
        if (ObjectMonitor::Knob_VerifyInUse) {
          verifyInUse(Self);
        }
      } else {
        m->FreeNext = NULL;
      }
      return m;
    }

    // 2: try to allocate from the global gFreeList
    // CONSIDER: use muxTry() instead of muxAcquire().
    // If the muxTry() fails then drop immediately into case 3.
    // If we're using thread-local free lists then try
    // to reprovision the caller's free list.
    if (gFreeList != NULL) {
      // Reprovision the thread's omFreeList.
      // Use bulk transfers to reduce the allocation rate and heat
      // on various locks.
      Thread::muxAcquire(&gListLock, "omAlloc");
      for (int i = Self->omFreeProvision; --i >= 0 && gFreeList != NULL;) {
        gMonitorFreeCount--;
        ObjectMonitor * take = gFreeList;
        gFreeList = take->FreeNext;
        guarantee(take->object() == NULL, "invariant");
        guarantee(!take->is_busy(), "invariant");
        take->Recycle();
        omRelease(Self, take, false);
      }
      Thread::muxRelease(&gListLock);
      Self->omFreeProvision += 1 + (Self->omFreeProvision/2);
      if (Self->omFreeProvision > MAXPRIVATE) Self->omFreeProvision = MAXPRIVATE;
      TEVENT(omFirst - reprovision);

      const int mx = MonitorBound;
      if (mx > 0 && (gMonitorPopulation-gMonitorFreeCount) > mx) {
        // We can't safely induce a STW safepoint from omAlloc() as our thread
        // state may not be appropriate for such activities and callers may hold
        // naked oops, so instead we defer the action.
        InduceScavenge(Self, "omAlloc");
      }
      continue;
    }

    // 3: allocate a block of new ObjectMonitors
    // Both the local and global free lists are empty -- resort to malloc().
    // In the current implementation objectMonitors are TSM - immortal.
    // Ideally, we'd write "new ObjectMonitor[_BLOCKSIZE], but we want
    // each ObjectMonitor to start at the beginning of a cache line,
    // so we use align_size_up().
    // A better solution would be to use C++ placement-new.
    // BEWARE: As it stands currently, we don't run the ctors!
    assert(_BLOCKSIZE > 1, "invariant");
    size_t neededsize = sizeof(PaddedEnd<ObjectMonitor>) * _BLOCKSIZE;
    PaddedEnd<ObjectMonitor> * temp;
    size_t aligned_size = neededsize + (DEFAULT_CACHE_LINE_SIZE - 1);
    void* real_malloc_addr = (void *)NEW_C_HEAP_ARRAY(char, aligned_size,
                                                      mtInternal);
    temp = (PaddedEnd<ObjectMonitor> *)
             align_size_up((intptr_t)real_malloc_addr,
                           DEFAULT_CACHE_LINE_SIZE);

    // NOTE: (almost) no way to recover if allocation failed.
    // We might be able to induce a STW safepoint and scavenge enough
    // objectMonitors to permit progress.
    if (temp == NULL) {
      vm_exit_out_of_memory(neededsize, OOM_MALLOC_ERROR,
                            "Allocate ObjectMonitors");
    }
    (void)memset((void *) temp, 0, neededsize);

    // Format the block.
    // initialize the linked list, each monitor points to its next
    // forming the single linked free list, the very first monitor
    // will points to next block, which forms the block list.
    // The trick of using the 1st element in the block as gBlockList
    // linkage should be reconsidered.  A better implementation would
    // look like: class Block { Block * next; int N; ObjectMonitor Body [N] ; }

    for (int i = 1; i < _BLOCKSIZE; i++) {
      temp[i].FreeNext = (ObjectMonitor *)&temp[i+1];
    }

    // terminate the last monitor as the end of list
    temp[_BLOCKSIZE - 1].FreeNext = NULL;

    // Element [0] is reserved for global list linkage
    temp[0].set_object(CHAINMARKER);

    // Consider carving out this thread's current request from the
    // block in hand.  This avoids some lock traffic and redundant
    // list activity.

    // Acquire the gListLock to manipulate gBlockList and gFreeList.
    // An Oyama-Taura-Yonezawa scheme might be more efficient.
    Thread::muxAcquire(&gListLock, "omAlloc [2]");
    gMonitorPopulation += _BLOCKSIZE-1;
    gMonitorFreeCount += _BLOCKSIZE-1;

    // Add the new block to the list of extant blocks (gBlockList).
    // The very first objectMonitor in a block is reserved and dedicated.
    // It serves as blocklist "next" linkage.
    temp[0].FreeNext = gBlockList;
    // There are lock-free uses of gBlockList so make sure that
    // the previous stores happen before we update gBlockList.
    OrderAccess::release_store_ptr(&gBlockList, temp);

    // Add the new string of objectMonitors to the global free list
    temp[_BLOCKSIZE - 1].FreeNext = gFreeList;
    gFreeList = temp + 1;
    Thread::muxRelease(&gListLock);
    TEVENT(Allocate block of monitors);
  }
}

// Place "m" on the caller's private per-thread omFreeList.
// In practice there's no need to clamp or limit the number of
// monitors on a thread's omFreeList as the only time we'll call
// omRelease is to return a monitor to the free list after a CAS
// attempt failed.  This doesn't allow unbounded #s of monitors to
// accumulate on a thread's free list.
//
// Key constraint: all ObjectMonitors on a thread's free list and the global
// free list must have their object field set to null. This prevents the
// scavenger -- deflate_idle_monitors -- from reclaiming them.

void ObjectSynchronizer::omRelease(Thread * Self, ObjectMonitor * m,
                                   bool fromPerThreadAlloc) {
  guarantee(m->object() == NULL, "invariant");
  guarantee(((m->is_busy()|m->_recursions) == 0), "freeing in-use monitor");
  // Remove from omInUseList
  if (MonitorInUseLists && fromPerThreadAlloc) {
    ObjectMonitor* cur_mid_in_use = NULL;
    bool extracted = false;
    for (ObjectMonitor* mid = Self->omInUseList; mid != NULL; cur_mid_in_use = mid, mid = mid->FreeNext) {
      if (m == mid) {
        // extract from per-thread in-use list
        if (mid == Self->omInUseList) {
          Self->omInUseList = mid->FreeNext;
        } else if (cur_mid_in_use != NULL) {
          cur_mid_in_use->FreeNext = mid->FreeNext; // maintain the current thread in-use list
        }
        extracted = true;
        Self->omInUseCount--;
        if (ObjectMonitor::Knob_VerifyInUse) {
          verifyInUse(Self);
        }
        break;
      }
    }
    assert(extracted, "Should have extracted from in-use list");
  }

  // FreeNext is used for both omInUseList and omFreeList, so clear old before setting new
  m->FreeNext = Self->omFreeList;
  Self->omFreeList = m;
  Self->omFreeCount++;
}

// Return the monitors of a moribund thread's local free list to
// the global free list.  Typically a thread calls omFlush() when
// it's dying.  We could also consider having the VM thread steal
// monitors from threads that have not run java code over a few
// consecutive STW safepoints.  Relatedly, we might decay
// omFreeProvision at STW safepoints.
//
// Also return the monitors of a moribund thread's omInUseList to
// a global gOmInUseList under the global list lock so these
// will continue to be scanned.
//
// We currently call omFlush() from the Thread:: dtor _after the thread
// has been excised from the thread list and is no longer a mutator.
// That means that omFlush() can run concurrently with a safepoint and
// the scavenge operator.  Calling omFlush() from JavaThread::exit() might
// be a better choice as we could safely reason that that the JVM is
// not at a safepoint at the time of the call, and thus there could
// be not inopportune interleavings between omFlush() and the scavenge
// operator.

void ObjectSynchronizer::omFlush(Thread * Self) {
  ObjectMonitor * list = Self->omFreeList;  // Null-terminated SLL
  Self->omFreeList = NULL;
  ObjectMonitor * tail = NULL;
  int tally = 0;
  if (list != NULL) {
    ObjectMonitor * s;
    // The thread is going away, the per-thread free monitors
    // are freed via set_owner(NULL)
    // Link them to tail, which will be linked into the global free list
    // gFreeList below, under the gListLock
    for (s = list; s != NULL; s = s->FreeNext) {
      tally++;
      tail = s;
      guarantee(s->object() == NULL, "invariant");
      guarantee(!s->is_busy(), "invariant");
      s->set_owner(NULL);   // redundant but good hygiene
      TEVENT(omFlush - Move one);
    }
    guarantee(tail != NULL && list != NULL, "invariant");
  }

  ObjectMonitor * inUseList = Self->omInUseList;
  ObjectMonitor * inUseTail = NULL;
  int inUseTally = 0;
  if (inUseList != NULL) {
    Self->omInUseList = NULL;
    ObjectMonitor *cur_om;
    // The thread is going away, however the omInUseList inflated
    // monitors may still be in-use by other threads.
    // Link them to inUseTail, which will be linked into the global in-use list
    // gOmInUseList below, under the gListLock
    for (cur_om = inUseList; cur_om != NULL; cur_om = cur_om->FreeNext) {
      inUseTail = cur_om;
      inUseTally++;
    }
    assert(Self->omInUseCount == inUseTally, "in-use count off");
    Self->omInUseCount = 0;
    guarantee(inUseTail != NULL && inUseList != NULL, "invariant");
  }

  Thread::muxAcquire(&gListLock, "omFlush");
  if (tail != NULL) {
    tail->FreeNext = gFreeList;
    gFreeList = list;
    gMonitorFreeCount += tally;
  }

  if (inUseTail != NULL) {
    inUseTail->FreeNext = gOmInUseList;
    gOmInUseList = inUseList;
    gOmInUseCount += inUseTally;
  }

  Thread::muxRelease(&gListLock);
  TEVENT(omFlush);
}

// Fast path code shared by multiple functions
ObjectMonitor* ObjectSynchronizer::inflate_helper(oop obj) {
  markOop mark = obj->mark();
  if (mark->has_monitor()) {
    assert(ObjectSynchronizer::verify_objmon_isinpool(mark->monitor()), "monitor is invalid");
    assert(mark->monitor()->header()->is_neutral(), "monitor must record a good object header");
    return mark->monitor();
  }
  return ObjectSynchronizer::inflate(Thread::current(),
                                     obj,
                                     inflate_cause_vm_internal);
}

ObjectMonitor* ObjectSynchronizer::inflate(Thread * Self,
                                                     oop object,
                                                     const InflateCause cause) {

  // Inflate mutates the heap ...
  // Relaxing assertion for bug 6320749.
  assert(Universe::verify_in_progress() ||
         !SafepointSynchronize::is_at_safepoint(), "invariant");

  EventJavaMonitorInflate event;

  for (;;) {
    const markOop mark = object->mark();
    assert(!mark->has_bias_pattern(), "invariant");

    // The mark can be in one of the following states:
    // *  Inflated     - just return
    // *  Stack-locked - coerce it to inflated
    // *  INFLATING    - busy wait for conversion to complete
    // *  Neutral      - aggressively inflate the object.
    // *  BIASED       - Illegal.  We should never see this

    // CASE: inflated
    if (mark->has_monitor()) {
      ObjectMonitor * inf = mark->monitor();
      assert(inf->header()->is_neutral(), "invariant");
      assert(inf->object() == object, "invariant");
      assert(ObjectSynchronizer::verify_objmon_isinpool(inf), "monitor is invalid");
      event.cancel(); // let's not post an inflation event, unless we did the deed ourselves
      return inf;
    }

    // CASE: inflation in progress - inflating over a stack-lock.
    // Some other thread is converting from stack-locked to inflated.
    // Only that thread can complete inflation -- other threads must wait.
    // The INFLATING value is transient.
    // Currently, we spin/yield/park and poll the markword, waiting for inflation to finish.
    // We could always eliminate polling by parking the thread on some auxiliary list.
    if (mark == markOopDesc::INFLATING()) {
      TEVENT(Inflate: spin while INFLATING);
      ReadStableMark(object);
      continue;
    }

    // CASE: stack-locked
    // Could be stack-locked either by this thread or by some other thread.
    //
    // Note that we allocate the objectmonitor speculatively, _before_ attempting
    // to install INFLATING into the mark word.  We originally installed INFLATING,
    // allocated the objectmonitor, and then finally STed the address of the
    // objectmonitor into the mark.  This was correct, but artificially lengthened
    // the interval in which INFLATED appeared in the mark, thus increasing
    // the odds of inflation contention.
    //
    // We now use per-thread private objectmonitor free lists.
    // These list are reprovisioned from the global free list outside the
    // critical INFLATING...ST interval.  A thread can transfer
    // multiple objectmonitors en-mass from the global free list to its local free list.
    // This reduces coherency traffic and lock contention on the global free list.
    // Using such local free lists, it doesn't matter if the omAlloc() call appears
    // before or after the CAS(INFLATING) operation.
    // See the comments in omAlloc().

    if (mark->has_locker()) {
      ObjectMonitor * m = omAlloc(Self);
      // Optimistically prepare the objectmonitor - anticipate successful CAS
      // We do this before the CAS in order to minimize the length of time
      // in which INFLATING appears in the mark.
      m->Recycle();
      m->_Responsible  = NULL;
      m->_recursions   = 0;
      m->_SpinDuration = ObjectMonitor::Knob_SpinLimit;   // Consider: maintain by type/class

      markOop cmp = (markOop) Atomic::cmpxchg_ptr(markOopDesc::INFLATING(), object->mark_addr(), mark);
      if (cmp != mark) {
        omRelease(Self, m, true);
        continue;       // Interference -- just retry
      }

      // We've successfully installed INFLATING (0) into the mark-word.
      // This is the only case where 0 will appear in a mark-word.
      // Only the singular thread that successfully swings the mark-word
      // to 0 can perform (or more precisely, complete) inflation.
      //
      // Why do we CAS a 0 into the mark-word instead of just CASing the
      // mark-word from the stack-locked value directly to the new inflated state?
      // Consider what happens when a thread unlocks a stack-locked object.
      // It attempts to use CAS to swing the displaced header value from the
      // on-stack basiclock back into the object header.  Recall also that the
      // header value (hashcode, etc) can reside in (a) the object header, or
      // (b) a displaced header associated with the stack-lock, or (c) a displaced
      // header in an objectMonitor.  The inflate() routine must copy the header
      // value from the basiclock on the owner's stack to the objectMonitor, all
      // the while preserving the hashCode stability invariants.  If the owner
      // decides to release the lock while the value is 0, the unlock will fail
      // and control will eventually pass from slow_exit() to inflate.  The owner
      // will then spin, waiting for the 0 value to disappear.   Put another way,
      // the 0 causes the owner to stall if the owner happens to try to
      // drop the lock (restoring the header from the basiclock to the object)
      // while inflation is in-progress.  This protocol avoids races that might
      // would otherwise permit hashCode values to change or "flicker" for an object.
      // Critically, while object->mark is 0 mark->displaced_mark_helper() is stable.
      // 0 serves as a "BUSY" inflate-in-progress indicator.


      // fetch the displaced mark from the owner's stack.
      // The owner can't die or unwind past the lock while our INFLATING
      // object is in the mark.  Furthermore the owner can't complete
      // an unlock on the object, either.
      markOop dmw = mark->displaced_mark_helper();
      assert(dmw->is_neutral(), "invariant");

      // Setup monitor fields to proper values -- prepare the monitor
      m->set_header(dmw);

      // Optimization: if the mark->locker stack address is associated
      // with this thread we could simply set m->_owner = Self.
      // Note that a thread can inflate an object
      // that it has stack-locked -- as might happen in wait() -- directly
      // with CAS.  That is, we can avoid the xchg-NULL .... ST idiom.
      m->set_owner(mark->locker());
      m->set_object(object);
      // TODO-FIXME: assert BasicLock->dhw != 0.

      // Must preserve store ordering. The monitor state must
      // be stable at the time of publishing the monitor address.
      guarantee(object->mark() == markOopDesc::INFLATING(), "invariant");
      object->release_set_mark(markOopDesc::encode(m));

      // Hopefully the performance counters are allocated on distinct cache lines
      // to avoid false sharing on MP systems ...
      OM_PERFDATA_OP(Inflations, inc());
      TEVENT(Inflate: overwrite stacklock);
      if (log_is_enabled(Debug, monitorinflation)) {
        if (object->is_instance()) {
          ResourceMark rm;
          log_debug(monitorinflation)("Inflating object " INTPTR_FORMAT " , mark " INTPTR_FORMAT " , type %s",
                                      p2i(object), p2i(object->mark()),
                                      object->klass()->external_name());
        }
      }
      if (event.should_commit()) {
        post_monitor_inflate_event(event, object, cause);
      }
      return m;
    }

    // CASE: neutral
    // TODO-FIXME: for entry we currently inflate and then try to CAS _owner.
    // If we know we're inflating for entry it's better to inflate by swinging a
    // pre-locked objectMonitor pointer into the object header.   A successful
    // CAS inflates the object *and* confers ownership to the inflating thread.
    // In the current implementation we use a 2-step mechanism where we CAS()
    // to inflate and then CAS() again to try to swing _owner from NULL to Self.
    // An inflateTry() method that we could call from fast_enter() and slow_enter()
    // would be useful.

    assert(mark->is_neutral(), "invariant");
    ObjectMonitor * m = omAlloc(Self);
    // prepare m for installation - set monitor to initial state
    m->Recycle();
    m->set_header(mark);
    m->set_owner(NULL);
    m->set_object(object);
    m->_recursions   = 0;
    m->_Responsible  = NULL;
    m->_SpinDuration = ObjectMonitor::Knob_SpinLimit;       // consider: keep metastats by type/class

    if (Atomic::cmpxchg_ptr (markOopDesc::encode(m), object->mark_addr(), mark) != mark) {
      m->set_object(NULL);
      m->set_owner(NULL);
      m->Recycle();
      omRelease(Self, m, true);
      m = NULL;
      continue;
      // interference - the markword changed - just retry.
      // The state-transitions are one-way, so there's no chance of
      // live-lock -- "Inflated" is an absorbing state.
    }

    // Hopefully the performance counters are allocated on distinct
    // cache lines to avoid false sharing on MP systems ...
    OM_PERFDATA_OP(Inflations, inc());
    TEVENT(Inflate: overwrite neutral);
    if (log_is_enabled(Debug, monitorinflation)) {
      if (object->is_instance()) {
        ResourceMark rm;
        log_debug(monitorinflation)("Inflating object " INTPTR_FORMAT " , mark " INTPTR_FORMAT " , type %s",
                                    p2i(object), p2i(object->mark()),
                                    object->klass()->external_name());
      }
    }
    if (event.should_commit()) {
      post_monitor_inflate_event(event, object, cause);
    }
    return m;
  }
}


// Deflate_idle_monitors() is called at all safepoints, immediately
// after all mutators are stopped, but before any objects have moved.
// It traverses the list of known monitors, deflating where possible.
// The scavenged monitor are returned to the monitor free list.
//
// Beware that we scavenge at *every* stop-the-world point.
// Having a large number of monitors in-circulation negatively
// impacts the performance of some applications (e.g., PointBase).
// Broadly, we want to minimize the # of monitors in circulation.
//
// We have added a flag, MonitorInUseLists, which creates a list
// of active monitors for each thread. deflate_idle_monitors()
// only scans the per-thread in-use lists. omAlloc() puts all
// assigned monitors on the per-thread list. deflate_idle_monitors()
// returns the non-busy monitors to the global free list.
// When a thread dies, omFlush() adds the list of active monitors for
// that thread to a global gOmInUseList acquiring the
// global list lock. deflate_idle_monitors() acquires the global
// list lock to scan for non-busy monitors to the global free list.
// An alternative could have used a single global in-use list. The
// downside would have been the additional cost of acquiring the global list lock
// for every omAlloc().
//
// Perversely, the heap size -- and thus the STW safepoint rate --
// typically drives the scavenge rate.  Large heaps can mean infrequent GC,
// which in turn can mean large(r) numbers of objectmonitors in circulation.
// This is an unfortunate aspect of this design.

enum ManifestConstants {
  ClearResponsibleAtSTW = 0
};

// Deflate a single monitor if not in-use
// Return true if deflated, false if in-use
bool ObjectSynchronizer::deflate_monitor(ObjectMonitor* mid, oop obj,
                                         ObjectMonitor** freeHeadp,
                                         ObjectMonitor** freeTailp) {
  bool deflated;
  // Normal case ... The monitor is associated with obj.
  guarantee(obj->mark() == markOopDesc::encode(mid), "invariant");
  guarantee(mid == obj->mark()->monitor(), "invariant");
  guarantee(mid->header()->is_neutral(), "invariant");

  if (mid->is_busy()) {
    if (ClearResponsibleAtSTW) mid->_Responsible = NULL;
    deflated = false;
  } else {
    // Deflate the monitor if it is no longer being used
    // It's idle - scavenge and return to the global free list
    // plain old deflation ...
    TEVENT(deflate_idle_monitors - scavenge1);
    if (log_is_enabled(Debug, monitorinflation)) {
      if (obj->is_instance()) {
        ResourceMark rm;
        log_debug(monitorinflation)("Deflating object " INTPTR_FORMAT " , "
                                    "mark " INTPTR_FORMAT " , type %s",
                                    p2i(obj), p2i(obj->mark()),
                                    obj->klass()->external_name());
      }
    }

    // Restore the header back to obj
    obj->release_set_mark(mid->header());
    mid->clear();

    assert(mid->object() == NULL, "invariant");

    // Move the object to the working free list defined by freeHeadp, freeTailp
    if (*freeHeadp == NULL) *freeHeadp = mid;
    if (*freeTailp != NULL) {
      ObjectMonitor * prevtail = *freeTailp;
      assert(prevtail->FreeNext == NULL, "cleaned up deflated?");
      prevtail->FreeNext = mid;
    }
    *freeTailp = mid;
    deflated = true;
  }
  return deflated;
}

// Walk a given monitor list, and deflate idle monitors
// The given list could be a per-thread list or a global list
// Caller acquires gListLock
int ObjectSynchronizer::deflate_monitor_list(ObjectMonitor** listHeadp,
                                             ObjectMonitor** freeHeadp,
                                             ObjectMonitor** freeTailp) {
  ObjectMonitor* mid;
  ObjectMonitor* next;
  ObjectMonitor* cur_mid_in_use = NULL;
  int deflated_count = 0;

  for (mid = *listHeadp; mid != NULL;) {
    oop obj = (oop) mid->object();
    if (obj != NULL && deflate_monitor(mid, obj, freeHeadp, freeTailp)) {
      // if deflate_monitor succeeded,
      // extract from per-thread in-use list
      if (mid == *listHeadp) {
        *listHeadp = mid->FreeNext;
      } else if (cur_mid_in_use != NULL) {
        cur_mid_in_use->FreeNext = mid->FreeNext; // maintain the current thread in-use list
      }
      next = mid->FreeNext;
      mid->FreeNext = NULL;  // This mid is current tail in the freeHeadp list
      mid = next;
      deflated_count++;
    } else {
      cur_mid_in_use = mid;
      mid = mid->FreeNext;
    }
  }
  return deflated_count;
}

void ObjectSynchronizer::deflate_idle_monitors() {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at safepoint");
  int nInuse = 0;              // currently associated with objects
  int nInCirculation = 0;      // extant
  int nScavenged = 0;          // reclaimed
  bool deflated = false;

  ObjectMonitor * freeHeadp = NULL;  // Local SLL of scavenged monitors
  ObjectMonitor * freeTailp = NULL;

  TEVENT(deflate_idle_monitors);
  // Prevent omFlush from changing mids in Thread dtor's during deflation
  // And in case the vm thread is acquiring a lock during a safepoint
  // See e.g. 6320749
  Thread::muxAcquire(&gListLock, "scavenge - return");

  if (MonitorInUseLists) {
    int inUse = 0;
    for (JavaThread* cur = Threads::first(); cur != NULL; cur = cur->next()) {
      nInCirculation+= cur->omInUseCount;
      int deflated_count = deflate_monitor_list(cur->omInUseList_addr(), &freeHeadp, &freeTailp);
      cur->omInUseCount-= deflated_count;
      if (ObjectMonitor::Knob_VerifyInUse) {
        verifyInUse(cur);
      }
      nScavenged += deflated_count;
      nInuse += cur->omInUseCount;
    }

    // For moribund threads, scan gOmInUseList
    if (gOmInUseList) {
      nInCirculation += gOmInUseCount;
      int deflated_count = deflate_monitor_list((ObjectMonitor **)&gOmInUseList, &freeHeadp, &freeTailp);
      gOmInUseCount-= deflated_count;
      nScavenged += deflated_count;
      nInuse += gOmInUseCount;
    }

  } else {
    PaddedEnd<ObjectMonitor> * block =
      (PaddedEnd<ObjectMonitor> *)OrderAccess::load_ptr_acquire(&gBlockList);
    for (; block != NULL; block = (PaddedEnd<ObjectMonitor> *)next(block)) {
      // Iterate over all extant monitors - Scavenge all idle monitors.
      assert(block->object() == CHAINMARKER, "must be a block header");
      nInCirculation += _BLOCKSIZE;
      for (int i = 1; i < _BLOCKSIZE; i++) {
        ObjectMonitor* mid = (ObjectMonitor*)&block[i];
        oop obj = (oop)mid->object();

        if (obj == NULL) {
          // The monitor is not associated with an object.
          // The monitor should either be a thread-specific private
          // free list or the global free list.
          // obj == NULL IMPLIES mid->is_busy() == 0
          guarantee(!mid->is_busy(), "invariant");
          continue;
        }
        deflated = deflate_monitor(mid, obj, &freeHeadp, &freeTailp);

        if (deflated) {
          mid->FreeNext = NULL;
          nScavenged++;
        } else {
          nInuse++;
        }
      }
    }
  }

  gMonitorFreeCount += nScavenged;

  // Consider: audit gFreeList to ensure that gMonitorFreeCount and list agree.

  if (ObjectMonitor::Knob_Verbose) {
    tty->print_cr("INFO: Deflate: InCirc=%d InUse=%d Scavenged=%d "
                  "ForceMonitorScavenge=%d : pop=%d free=%d",
                  nInCirculation, nInuse, nScavenged, ForceMonitorScavenge,
                  gMonitorPopulation, gMonitorFreeCount);
    tty->flush();
  }

  ForceMonitorScavenge = 0;    // Reset

  // Move the scavenged monitors back to the global free list.
  if (freeHeadp != NULL) {
    guarantee(freeTailp != NULL && nScavenged > 0, "invariant");
    assert(freeTailp->FreeNext == NULL, "invariant");
    // constant-time list splice - prepend scavenged segment to gFreeList
    freeTailp->FreeNext = gFreeList;
    gFreeList = freeHeadp;
  }
  Thread::muxRelease(&gListLock);

  OM_PERFDATA_OP(Deflations, inc(nScavenged));
  OM_PERFDATA_OP(MonExtant, set_value(nInCirculation));

  // TODO: Add objectMonitor leak detection.
  // Audit/inventory the objectMonitors -- make sure they're all accounted for.
  GVars.stwRandom = os::random();
  GVars.stwCycle++;
}

// Monitor cleanup on JavaThread::exit

// Iterate through monitor cache and attempt to release thread's monitors
// Gives up on a particular monitor if an exception occurs, but continues
// the overall iteration, swallowing the exception.
class ReleaseJavaMonitorsClosure: public MonitorClosure {
 private:
  TRAPS;

 public:
  ReleaseJavaMonitorsClosure(Thread* thread) : THREAD(thread) {}
  void do_monitor(ObjectMonitor* mid) {
    if (mid->owner() == THREAD) {
      if (ObjectMonitor::Knob_VerifyMatch != 0) {
        ResourceMark rm;
        Handle obj((oop) mid->object());
        tty->print("INFO: unexpected locked object:");
        javaVFrame::print_locked_object_class_name(tty, obj, "locked");
        fatal("exiting JavaThread=" INTPTR_FORMAT
              " unexpectedly owns ObjectMonitor=" INTPTR_FORMAT,
              p2i(THREAD), p2i(mid));
      }
      (void)mid->complete_exit(CHECK);
    }
  }
};

// Release all inflated monitors owned by THREAD.  Lightweight monitors are
// ignored.  This is meant to be called during JNI thread detach which assumes
// all remaining monitors are heavyweight.  All exceptions are swallowed.
// Scanning the extant monitor list can be time consuming.
// A simple optimization is to add a per-thread flag that indicates a thread
// called jni_monitorenter() during its lifetime.
//
// Instead of No_Savepoint_Verifier it might be cheaper to
// use an idiom of the form:
//   auto int tmp = SafepointSynchronize::_safepoint_counter ;
//   <code that must not run at safepoint>
//   guarantee (((tmp ^ _safepoint_counter) | (tmp & 1)) == 0) ;
// Since the tests are extremely cheap we could leave them enabled
// for normal product builds.

void ObjectSynchronizer::release_monitors_owned_by_thread(TRAPS) {
  assert(THREAD == JavaThread::current(), "must be current Java thread");
  NoSafepointVerifier nsv;
  ReleaseJavaMonitorsClosure rjmc(THREAD);
  Thread::muxAcquire(&gListLock, "release_monitors_owned_by_thread");
  ObjectSynchronizer::monitors_iterate(&rjmc);
  Thread::muxRelease(&gListLock);
  THREAD->clear_pending_exception();
}

const char* ObjectSynchronizer::inflate_cause_name(const InflateCause cause) {
  switch (cause) {
    case inflate_cause_vm_internal:    return "VM Internal";
    case inflate_cause_monitor_enter:  return "Monitor Enter";
    case inflate_cause_wait:           return "Monitor Wait";
    case inflate_cause_notify:         return "Monitor Notify";
    case inflate_cause_hash_code:      return "Monitor Hash Code";
    case inflate_cause_jni_enter:      return "JNI Monitor Enter";
    case inflate_cause_jni_exit:       return "JNI Monitor Exit";
    default:
      ShouldNotReachHere();
  }
  return "Unknown";
}

static void post_monitor_inflate_event(EventJavaMonitorInflate& event,
                                       const oop obj,
                                       const ObjectSynchronizer::InflateCause cause) {
#if INCLUDE_TRACE
  assert(event.should_commit(), "check outside");
  event.set_klass(obj->klass());
  event.set_address((TYPE_ADDRESS)(uintptr_t)(void*)obj);
  event.set_cause((u1)cause);
  event.commit();
#endif
}

//------------------------------------------------------------------------------
// Debugging code

void ObjectSynchronizer::sanity_checks(const bool verbose,
                                       const uint cache_line_size,
                                       int *error_cnt_ptr,
                                       int *warning_cnt_ptr) {
  u_char *addr_begin      = (u_char*)&GVars;
  u_char *addr_stwRandom  = (u_char*)&GVars.stwRandom;
  u_char *addr_hcSequence = (u_char*)&GVars.hcSequence;

  if (verbose) {
    tty->print_cr("INFO: sizeof(SharedGlobals)=" SIZE_FORMAT,
                  sizeof(SharedGlobals));
  }

  uint offset_stwRandom = (uint)(addr_stwRandom - addr_begin);
  if (verbose) tty->print_cr("INFO: offset(stwRandom)=%u", offset_stwRandom);

  uint offset_hcSequence = (uint)(addr_hcSequence - addr_begin);
  if (verbose) {
    tty->print_cr("INFO: offset(_hcSequence)=%u", offset_hcSequence);
  }

  if (cache_line_size != 0) {
    // We were able to determine the L1 data cache line size so
    // do some cache line specific sanity checks

    if (offset_stwRandom < cache_line_size) {
      tty->print_cr("WARNING: the SharedGlobals.stwRandom field is closer "
                    "to the struct beginning than a cache line which permits "
                    "false sharing.");
      (*warning_cnt_ptr)++;
    }

    if ((offset_hcSequence - offset_stwRandom) < cache_line_size) {
      tty->print_cr("WARNING: the SharedGlobals.stwRandom and "
                    "SharedGlobals.hcSequence fields are closer than a cache "
                    "line which permits false sharing.");
      (*warning_cnt_ptr)++;
    }

    if ((sizeof(SharedGlobals) - offset_hcSequence) < cache_line_size) {
      tty->print_cr("WARNING: the SharedGlobals.hcSequence field is closer "
                    "to the struct end than a cache line which permits false "
                    "sharing.");
      (*warning_cnt_ptr)++;
    }
  }
}

#ifndef PRODUCT

// Verify all monitors in the monitor cache, the verification is weak.
void ObjectSynchronizer::verify() {
  PaddedEnd<ObjectMonitor> * block =
    (PaddedEnd<ObjectMonitor> *)OrderAccess::load_ptr_acquire(&gBlockList);
  while (block != NULL) {
    assert(block->object() == CHAINMARKER, "must be a block header");
    for (int i = 1; i < _BLOCKSIZE; i++) {
      ObjectMonitor* mid = (ObjectMonitor *)(block + i);
      oop object = (oop)mid->object();
      if (object != NULL) {
        mid->verify();
      }
    }
    block = (PaddedEnd<ObjectMonitor> *)block->FreeNext;
  }
}

// Check if monitor belongs to the monitor cache
// The list is grow-only so it's *relatively* safe to traverse
// the list of extant blocks without taking a lock.

int ObjectSynchronizer::verify_objmon_isinpool(ObjectMonitor *monitor) {
  PaddedEnd<ObjectMonitor> * block =
    (PaddedEnd<ObjectMonitor> *)OrderAccess::load_ptr_acquire(&gBlockList);
  while (block != NULL) {
    assert(block->object() == CHAINMARKER, "must be a block header");
    if (monitor > (ObjectMonitor *)&block[0] &&
        monitor < (ObjectMonitor *)&block[_BLOCKSIZE]) {
      address mon = (address)monitor;
      address blk = (address)block;
      size_t diff = mon - blk;
      assert((diff % sizeof(PaddedEnd<ObjectMonitor>)) == 0, "must be aligned");
      return 1;
    }
    block = (PaddedEnd<ObjectMonitor> *)block->FreeNext;
  }
  return 0;
}

#endif

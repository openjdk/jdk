/*
 * Copyright (c) 1998, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "logging/logStream.hpp"
#include "jfr/jfrEvents.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/metaspaceShared.hpp"
#include "memory/padded.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/markWord.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/biasedLocking.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/objectMonitor.hpp"
#include "runtime/objectMonitor.inline.hpp"
#include "runtime/osThread.hpp"
#include "runtime/safepointVerifiers.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/synchronizer.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/timer.hpp"
#include "runtime/vframe.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/align.hpp"
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
PaddedObjectMonitor* volatile ObjectSynchronizer::g_block_list = NULL;
// Global ObjectMonitor free list. Newly allocated and deflated
// ObjectMonitors are prepended here.
ObjectMonitor* volatile ObjectSynchronizer::g_free_list = NULL;
// Global ObjectMonitor in-use list. When a JavaThread is exiting,
// ObjectMonitors on its per-thread in-use list are prepended here.
ObjectMonitor* volatile ObjectSynchronizer::g_om_in_use_list = NULL;
int ObjectSynchronizer::g_om_in_use_count = 0;  // # on g_om_in_use_list

static volatile intptr_t gListLock = 0;   // protects global monitor lists
static volatile int g_om_free_count = 0;  // # on g_free_list
static volatile int g_om_population = 0;  // # Extant -- in circulation

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

bool ObjectSynchronizer::quick_notify(oopDesc* obj, Thread* self, bool all) {
  assert(!SafepointSynchronize::is_at_safepoint(), "invariant");
  assert(self->is_Java_thread(), "invariant");
  assert(((JavaThread *) self)->thread_state() == _thread_in_Java, "invariant");
  NoSafepointVerifier nsv;
  if (obj == NULL) return false;  // slow-path for invalid obj
  const markWord mark = obj->mark();

  if (mark.has_locker() && self->is_lock_owned((address)mark.locker())) {
    // Degenerate notify
    // stack-locked by caller so by definition the implied waitset is empty.
    return true;
  }

  if (mark.has_monitor()) {
    ObjectMonitor* const mon = mark.monitor();
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
      int free_count = 0;
      do {
        mon->INotify(self);
        ++free_count;
      } while (mon->first_waiter() != NULL && all);
      OM_PERFDATA_OP(Notifications, inc(free_count));
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

bool ObjectSynchronizer::quick_enter(oop obj, Thread* self,
                                     BasicLock * lock) {
  assert(!SafepointSynchronize::is_at_safepoint(), "invariant");
  assert(self->is_Java_thread(), "invariant");
  assert(((JavaThread *) self)->thread_state() == _thread_in_Java, "invariant");
  NoSafepointVerifier nsv;
  if (obj == NULL) return false;       // Need to throw NPE
  const markWord mark = obj->mark();

  if (mark.has_monitor()) {
    ObjectMonitor* const m = mark.monitor();
    assert(m->object() == obj, "invariant");
    Thread* const owner = (Thread *) m->_owner;

    // Lock contention and Transactional Lock Elision (TLE) diagnostics
    // and observability
    // Case: light contention possibly amenable to TLE
    // Case: TLE inimical operations such as nested/recursive synchronization

    if (owner == self) {
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
    lock->set_displaced_header(markWord::unused_mark());

    if (owner == NULL && Atomic::replace_if_null(self, &(m->_owner))) {
      assert(m->_recursions == 0, "invariant");
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
// Monitor Enter/Exit
// The interpreter and compiler assembly code tries to lock using the fast path
// of this algorithm. Make sure to update that code if the following function is
// changed. The implementation is extremely sensitive to race condition. Be careful.

void ObjectSynchronizer::enter(Handle obj, BasicLock* lock, TRAPS) {
  if (UseBiasedLocking) {
    if (!SafepointSynchronize::is_at_safepoint()) {
      BiasedLocking::revoke(obj, THREAD);
    } else {
      BiasedLocking::revoke_at_safepoint(obj);
    }
  }

  markWord mark = obj->mark();
  assert(!mark.has_bias_pattern(), "should not see bias pattern here");

  if (mark.is_neutral()) {
    // Anticipate successful CAS -- the ST of the displaced mark must
    // be visible <= the ST performed by the CAS.
    lock->set_displaced_header(mark);
    if (mark == obj()->cas_set_mark(markWord::from_pointer(lock), mark)) {
      return;
    }
    // Fall through to inflate() ...
  } else if (mark.has_locker() &&
             THREAD->is_lock_owned((address)mark.locker())) {
    assert(lock != mark.locker(), "must not re-lock the same lock");
    assert(lock != (BasicLock*)obj->mark().value(), "don't relock with same BasicLock");
    lock->set_displaced_header(markWord::from_pointer(NULL));
    return;
  }

  // The object header will never be displaced to this lock,
  // so it does not matter what the value is, except that it
  // must be non-zero to avoid looking like a re-entrant lock,
  // and must not look locked either.
  lock->set_displaced_header(markWord::unused_mark());
  inflate(THREAD, obj(), inflate_cause_monitor_enter)->enter(THREAD);
}

void ObjectSynchronizer::exit(oop object, BasicLock* lock, TRAPS) {
  markWord mark = object->mark();
  // We cannot check for Biased Locking if we are racing an inflation.
  assert(mark == markWord::INFLATING() ||
         !mark.has_bias_pattern(), "should not see bias pattern here");

  markWord dhw = lock->displaced_header();
  if (dhw.value() == 0) {
    // If the displaced header is NULL, then this exit matches up with
    // a recursive enter. No real work to do here except for diagnostics.
#ifndef PRODUCT
    if (mark != markWord::INFLATING()) {
      // Only do diagnostics if we are not racing an inflation. Simply
      // exiting a recursive enter of a Java Monitor that is being
      // inflated is safe; see the has_monitor() comment below.
      assert(!mark.is_neutral(), "invariant");
      assert(!mark.has_locker() ||
             THREAD->is_lock_owned((address)mark.locker()), "invariant");
      if (mark.has_monitor()) {
        // The BasicLock's displaced_header is marked as a recursive
        // enter and we have an inflated Java Monitor (ObjectMonitor).
        // This is a special case where the Java Monitor was inflated
        // after this thread entered the stack-lock recursively. When a
        // Java Monitor is inflated, we cannot safely walk the Java
        // Monitor owner's stack and update the BasicLocks because a
        // Java Monitor can be asynchronously inflated by a thread that
        // does not own the Java Monitor.
        ObjectMonitor* m = mark.monitor();
        assert(((oop)(m->object()))->mark() == mark, "invariant");
        assert(m->is_entered(THREAD), "invariant");
      }
    }
#endif
    return;
  }

  if (mark == markWord::from_pointer(lock)) {
    // If the object is stack-locked by the current thread, try to
    // swing the displaced header from the BasicLock back to the mark.
    assert(dhw.is_neutral(), "invariant");
    if (object->cas_set_mark(dhw, mark) == mark) {
      return;
    }
  }

  // We have to take the slow-path of possible inflation and then exit.
  inflate(THREAD, object, inflate_cause_vm_internal)->exit(true, THREAD);
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
  if (UseBiasedLocking) {
    BiasedLocking::revoke(obj, THREAD);
    assert(!obj->mark().has_bias_pattern(), "biases should be revoked by now");
  }

  ObjectMonitor* monitor = inflate(THREAD, obj(), inflate_cause_vm_internal);

  return monitor->complete_exit(THREAD);
}

// NOTE: must use heavy weight monitor to handle complete_exit/reenter()
void ObjectSynchronizer::reenter(Handle obj, intptr_t recursion, TRAPS) {
  if (UseBiasedLocking) {
    BiasedLocking::revoke(obj, THREAD);
    assert(!obj->mark().has_bias_pattern(), "biases should be revoked by now");
  }

  ObjectMonitor* monitor = inflate(THREAD, obj(), inflate_cause_vm_internal);

  monitor->reenter(recursion, THREAD);
}
// -----------------------------------------------------------------------------
// JNI locks on java objects
// NOTE: must use heavy weight monitor to handle jni monitor enter
void ObjectSynchronizer::jni_enter(Handle obj, TRAPS) {
  // the current locking is from JNI instead of Java code
  if (UseBiasedLocking) {
    BiasedLocking::revoke(obj, THREAD);
    assert(!obj->mark().has_bias_pattern(), "biases should be revoked by now");
  }
  THREAD->set_current_pending_monitor_is_from_java(false);
  inflate(THREAD, obj(), inflate_cause_jni_enter)->enter(THREAD);
  THREAD->set_current_pending_monitor_is_from_java(true);
}

// NOTE: must use heavy weight monitor to handle jni monitor exit
void ObjectSynchronizer::jni_exit(oop obj, Thread* THREAD) {
  if (UseBiasedLocking) {
    Handle h_obj(THREAD, obj);
    BiasedLocking::revoke(h_obj, THREAD);
    obj = h_obj();
  }
  assert(!obj->mark().has_bias_pattern(), "biases should be revoked by now");

  ObjectMonitor* monitor = inflate(THREAD, obj, inflate_cause_jni_exit);
  // If this thread has locked the object, exit the monitor. We
  // intentionally do not use CHECK here because we must exit the
  // monitor even if an exception is pending.
  if (monitor->check_owner(THREAD)) {
    monitor->exit(true, THREAD);
  }
}

// -----------------------------------------------------------------------------
// Internal VM locks on java objects
// standard constructor, allows locking failures
ObjectLocker::ObjectLocker(Handle obj, Thread* thread, bool do_lock) {
  _dolock = do_lock;
  _thread = thread;
  _thread->check_for_valid_safepoint_state();
  _obj = obj;

  if (_dolock) {
    ObjectSynchronizer::enter(_obj, &_lock, _thread);
  }
}

ObjectLocker::~ObjectLocker() {
  if (_dolock) {
    ObjectSynchronizer::exit(_obj(), &_lock, _thread);
  }
}


// -----------------------------------------------------------------------------
//  Wait/Notify/NotifyAll
// NOTE: must use heavy weight monitor to handle wait()
int ObjectSynchronizer::wait(Handle obj, jlong millis, TRAPS) {
  if (UseBiasedLocking) {
    BiasedLocking::revoke(obj, THREAD);
    assert(!obj->mark().has_bias_pattern(), "biases should be revoked by now");
  }
  if (millis < 0) {
    THROW_MSG_0(vmSymbols::java_lang_IllegalArgumentException(), "timeout value is negative");
  }
  ObjectMonitor* monitor = inflate(THREAD, obj(), inflate_cause_wait);

  DTRACE_MONITOR_WAIT_PROBE(monitor, obj(), THREAD, millis);
  monitor->wait(millis, true, THREAD);

  // This dummy call is in place to get around dtrace bug 6254741.  Once
  // that's fixed we can uncomment the following line, remove the call
  // and change this function back into a "void" func.
  // DTRACE_MONITOR_PROBE(waited, monitor, obj(), THREAD);
  return dtrace_waited_probe(monitor, obj, THREAD);
}

void ObjectSynchronizer::wait_uninterruptibly(Handle obj, jlong millis, TRAPS) {
  if (UseBiasedLocking) {
    BiasedLocking::revoke(obj, THREAD);
    assert(!obj->mark().has_bias_pattern(), "biases should be revoked by now");
  }
  if (millis < 0) {
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(), "timeout value is negative");
  }
  inflate(THREAD, obj(), inflate_cause_wait)->wait(millis, false, THREAD);
}

void ObjectSynchronizer::notify(Handle obj, TRAPS) {
  if (UseBiasedLocking) {
    BiasedLocking::revoke(obj, THREAD);
    assert(!obj->mark().has_bias_pattern(), "biases should be revoked by now");
  }

  markWord mark = obj->mark();
  if (mark.has_locker() && THREAD->is_lock_owned((address)mark.locker())) {
    return;
  }
  inflate(THREAD, obj(), inflate_cause_notify)->notify(THREAD);
}

// NOTE: see comment of notify()
void ObjectSynchronizer::notifyall(Handle obj, TRAPS) {
  if (UseBiasedLocking) {
    BiasedLocking::revoke(obj, THREAD);
    assert(!obj->mark().has_bias_pattern(), "biases should be revoked by now");
  }

  markWord mark = obj->mark();
  if (mark.has_locker() && THREAD->is_lock_owned((address)mark.locker())) {
    return;
  }
  inflate(THREAD, obj(), inflate_cause_notify)->notifyAll(THREAD);
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
  volatile int stw_random;
  volatile int stw_cycle;
  DEFINE_PAD_MINUS_SIZE(1, DEFAULT_CACHE_LINE_SIZE, sizeof(volatile int) * 2);
  // Hot RW variable -- Sequester to avoid false-sharing
  volatile int hc_sequence;
  DEFINE_PAD_MINUS_SIZE(2, DEFAULT_CACHE_LINE_SIZE, sizeof(volatile int));
};

static SharedGlobals GVars;
static int MonitorScavengeThreshold = 1000000;
static volatile int ForceMonitorScavenge = 0; // Scavenge required and pending

static markWord read_stable_mark(oop obj) {
  markWord mark = obj->mark();
  if (!mark.is_being_inflated()) {
    return mark;       // normal fast-path return
  }

  int its = 0;
  for (;;) {
    markWord mark = obj->mark();
    if (!mark.is_being_inflated()) {
      return mark;    // normal fast-path return
    }

    // The object is being inflated by some other thread.
    // The caller of read_stable_mark() must wait for inflation to complete.
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
        while (obj->mark() == markWord::INFLATING()) {
          // Beware: NakedYield() is advisory and has almost no effect on some platforms
          // so we periodically call self->_ParkEvent->park(1).
          // We use a mixed spin/yield/block mechanism.
          if ((YieldThenBlock++) >= 16) {
            Thread::current()->_ParkEvent->park(1);
          } else {
            os::naked_yield();
          }
        }
        Thread::muxRelease(gInflationLocks + ix);
      }
    } else {
      SpinPause();       // SMP-polite spinning
    }
  }
}

// hashCode() generation :
//
// Possibilities:
// * MD5Digest of {obj,stw_random}
// * CRC32 of {obj,stw_random} or any linear-feedback shift register function.
// * A DES- or AES-style SBox[] mechanism
// * One of the Phi-based schemes, such as:
//   2654435761 = 2^32 * Phi (golden ratio)
//   HashCodeValue = ((uintptr_t(obj) >> 3) * 2654435761) ^ GVars.stw_random ;
// * A variation of Marsaglia's shift-xor RNG scheme.
// * (obj ^ stw_random) is appealing, but can result
//   in undesirable regularity in the hashCode values of adjacent objects
//   (objects allocated back-to-back, in particular).  This could potentially
//   result in hashtable collisions and reduced hashtable efficiency.
//   There are simple ways to "diffuse" the middle address bits over the
//   generated hashCode values:

static inline intptr_t get_next_hash(Thread* self, oop obj) {
  intptr_t value = 0;
  if (hashCode == 0) {
    // This form uses global Park-Miller RNG.
    // On MP system we'll have lots of RW access to a global, so the
    // mechanism induces lots of coherency traffic.
    value = os::random();
  } else if (hashCode == 1) {
    // This variation has the property of being stable (idempotent)
    // between STW operations.  This can be useful in some of the 1-0
    // synchronization schemes.
    intptr_t addr_bits = cast_from_oop<intptr_t>(obj) >> 3;
    value = addr_bits ^ (addr_bits >> 5) ^ GVars.stw_random;
  } else if (hashCode == 2) {
    value = 1;            // for sensitivity testing
  } else if (hashCode == 3) {
    value = ++GVars.hc_sequence;
  } else if (hashCode == 4) {
    value = cast_from_oop<intptr_t>(obj);
  } else {
    // Marsaglia's xor-shift scheme with thread-specific state
    // This is probably the best overall implementation -- we'll
    // likely make this the default in future releases.
    unsigned t = self->_hashStateX;
    t ^= (t << 11);
    self->_hashStateX = self->_hashStateY;
    self->_hashStateY = self->_hashStateZ;
    self->_hashStateZ = self->_hashStateW;
    unsigned v = self->_hashStateW;
    v = (v ^ (v >> 19)) ^ (t ^ (t >> 8));
    self->_hashStateW = v;
    value = v;
  }

  value &= markWord::hash_mask;
  if (value == 0) value = 0xBAD;
  assert(value != markWord::no_hash, "invariant");
  return value;
}

intptr_t ObjectSynchronizer::FastHashCode(Thread* self, oop obj) {
  if (UseBiasedLocking) {
    // NOTE: many places throughout the JVM do not expect a safepoint
    // to be taken here, in particular most operations on perm gen
    // objects. However, we only ever bias Java instances and all of
    // the call sites of identity_hash that might revoke biases have
    // been checked to make sure they can handle a safepoint. The
    // added check of the bias pattern is to avoid useless calls to
    // thread-local storage.
    if (obj->mark().has_bias_pattern()) {
      // Handle for oop obj in case of STW safepoint
      Handle hobj(self, obj);
      // Relaxing assertion for bug 6320749.
      assert(Universe::verify_in_progress() ||
             !SafepointSynchronize::is_at_safepoint(),
             "biases should not be seen by VM thread here");
      BiasedLocking::revoke(hobj, JavaThread::current());
      obj = hobj();
      assert(!obj->mark().has_bias_pattern(), "biases should be revoked by now");
    }
  }

  // hashCode() is a heap mutator ...
  // Relaxing assertion for bug 6320749.
  assert(Universe::verify_in_progress() || DumpSharedSpaces ||
         !SafepointSynchronize::is_at_safepoint(), "invariant");
  assert(Universe::verify_in_progress() || DumpSharedSpaces ||
         self->is_Java_thread() , "invariant");
  assert(Universe::verify_in_progress() || DumpSharedSpaces ||
         ((JavaThread *)self)->thread_state() != _thread_blocked, "invariant");

  ObjectMonitor* monitor = NULL;
  markWord temp, test;
  intptr_t hash;
  markWord mark = read_stable_mark(obj);

  // object should remain ineligible for biased locking
  assert(!mark.has_bias_pattern(), "invariant");

  if (mark.is_neutral()) {
    hash = mark.hash();               // this is a normal header
    if (hash != 0) {                  // if it has hash, just return it
      return hash;
    }
    hash = get_next_hash(self, obj);  // allocate a new hash code
    temp = mark.copy_set_hash(hash);  // merge the hash code into header
    // use (machine word version) atomic operation to install the hash
    test = obj->cas_set_mark(temp, mark);
    if (test == mark) {
      return hash;
    }
    // If atomic operation failed, we must inflate the header
    // into heavy weight monitor. We could add more code here
    // for fast path, but it does not worth the complexity.
  } else if (mark.has_monitor()) {
    monitor = mark.monitor();
    temp = monitor->header();
    assert(temp.is_neutral(), "invariant: header=" INTPTR_FORMAT, temp.value());
    hash = temp.hash();
    if (hash != 0) {
      return hash;
    }
    // Skip to the following code to reduce code size
  } else if (self->is_lock_owned((address)mark.locker())) {
    temp = mark.displaced_mark_helper(); // this is a lightweight monitor owned
    assert(temp.is_neutral(), "invariant: header=" INTPTR_FORMAT, temp.value());
    hash = temp.hash();                  // by current thread, check if the displaced
    if (hash != 0) {                     // header contains hash code
      return hash;
    }
    // WARNING:
    // The displaced header in the BasicLock on a thread's stack
    // is strictly immutable. It CANNOT be changed in ANY cases.
    // So we have to inflate the stack lock into an ObjectMonitor
    // even if the current thread owns the lock. The BasicLock on
    // a thread's stack can be asynchronously read by other threads
    // during an inflate() call so any change to that stack memory
    // may not propagate to other threads correctly.
  }

  // Inflate the monitor to set hash code
  monitor = inflate(self, obj, inflate_cause_hash_code);
  // Load displaced header and check it has hash code
  mark = monitor->header();
  assert(mark.is_neutral(), "invariant: header=" INTPTR_FORMAT, mark.value());
  hash = mark.hash();
  if (hash == 0) {
    hash = get_next_hash(self, obj);
    temp = mark.copy_set_hash(hash); // merge hash code into header
    assert(temp.is_neutral(), "invariant: header=" INTPTR_FORMAT, temp.value());
    uintptr_t v = Atomic::cmpxchg(temp.value(), (volatile uintptr_t*)monitor->header_addr(), mark.value());
    test = markWord(v);
    if (test != mark) {
      // The only non-deflation update to the ObjectMonitor's
      // header/dmw field is to merge in the hash code. If someone
      // adds a new usage of the header/dmw field, please update
      // this code.
      hash = test.hash();
      assert(test.is_neutral(), "invariant: header=" INTPTR_FORMAT, test.value());
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
    BiasedLocking::revoke(h_obj, thread);
    assert(!h_obj->mark().has_bias_pattern(), "biases should be revoked by now");
  }

  assert(thread == JavaThread::current(), "Can only be called on current thread");
  oop obj = h_obj();

  markWord mark = read_stable_mark(obj);

  // Uncontended case, header points to stack
  if (mark.has_locker()) {
    return thread->is_lock_owned((address)mark.locker());
  }
  // Contended case, header points to ObjectMonitor (tagged pointer)
  if (mark.has_monitor()) {
    ObjectMonitor* monitor = mark.monitor();
    return monitor->is_entered(thread) != 0;
  }
  // Unlocked case, header in place
  assert(mark.is_neutral(), "sanity check");
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

  if (UseBiasedLocking && h_obj()->mark().has_bias_pattern()) {
    // CASE: biased
    BiasedLocking::revoke(h_obj, self);
    assert(!h_obj->mark().has_bias_pattern(),
           "biases should be revoked by now");
  }

  assert(self == JavaThread::current(), "Can only be called on current thread");
  oop obj = h_obj();
  markWord mark = read_stable_mark(obj);

  // CASE: stack-locked.  Mark points to a BasicLock on the owner's stack.
  if (mark.has_locker()) {
    return self->is_lock_owned((address)mark.locker()) ?
      owner_self : owner_other;
  }

  // CASE: inflated. Mark (tagged pointer) points to an ObjectMonitor.
  // The Object:ObjectMonitor relationship is stable as long as we're
  // not at a safepoint.
  if (mark.has_monitor()) {
    void* owner = mark.monitor()->_owner;
    if (owner == NULL) return owner_none;
    return (owner == self ||
            self->is_lock_owned((address)owner)) ? owner_self : owner_other;
  }

  // CASE: neutral
  assert(mark.is_neutral(), "sanity check");
  return owner_none;           // it's unlocked
}

// FIXME: jvmti should call this
JavaThread* ObjectSynchronizer::get_lock_owner(ThreadsList * t_list, Handle h_obj) {
  if (UseBiasedLocking) {
    if (SafepointSynchronize::is_at_safepoint()) {
      BiasedLocking::revoke_at_safepoint(h_obj);
    } else {
      BiasedLocking::revoke(h_obj, JavaThread::current());
    }
    assert(!h_obj->mark().has_bias_pattern(), "biases should be revoked by now");
  }

  oop obj = h_obj();
  address owner = NULL;

  markWord mark = read_stable_mark(obj);

  // Uncontended case, header points to stack
  if (mark.has_locker()) {
    owner = (address) mark.locker();
  }

  // Contended case, header points to ObjectMonitor (tagged pointer)
  else if (mark.has_monitor()) {
    ObjectMonitor* monitor = mark.monitor();
    assert(monitor != NULL, "monitor should be non-null");
    owner = (address) monitor->owner();
  }

  if (owner != NULL) {
    // owning_thread_from_monitor_owner() may also return NULL here
    return Threads::owning_thread_from_monitor_owner(t_list, owner);
  }

  // Unlocked case, header in place
  // Cannot have assertion since this object may have been
  // locked by another thread when reaching here.
  // assert(mark.is_neutral(), "sanity check");

  return NULL;
}

// Visitors ...

void ObjectSynchronizer::monitors_iterate(MonitorClosure* closure) {
  PaddedObjectMonitor* block = OrderAccess::load_acquire(&g_block_list);
  while (block != NULL) {
    assert(block->object() == CHAINMARKER, "must be a block header");
    for (int i = _BLOCKSIZE - 1; i > 0; i--) {
      ObjectMonitor* mid = (ObjectMonitor *)(block + i);
      oop object = (oop)mid->object();
      if (object != NULL) {
        // Only process with closure if the object is set.
        closure->do_monitor(mid);
      }
    }
    block = (PaddedObjectMonitor*)block->_next_om;
  }
}

static bool monitors_used_above_threshold() {
  if (g_om_population == 0) {
    return false;
  }
  int monitors_used = g_om_population - g_om_free_count;
  int monitor_usage = (monitors_used * 100LL) / g_om_population;
  return monitor_usage > MonitorUsedDeflationThreshold;
}

bool ObjectSynchronizer::is_cleanup_needed() {
  if (MonitorUsedDeflationThreshold > 0) {
    return monitors_used_above_threshold();
  }
  return false;
}

void ObjectSynchronizer::oops_do(OopClosure* f) {
  // We only scan the global used list here (for moribund threads), and
  // the thread-local monitors in Thread::oops_do().
  global_used_oops_do(f);
}

void ObjectSynchronizer::global_used_oops_do(OopClosure* f) {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at safepoint");
  list_oops_do(g_om_in_use_list, f);
}

void ObjectSynchronizer::thread_local_used_oops_do(Thread* thread, OopClosure* f) {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at safepoint");
  list_oops_do(thread->om_in_use_list, f);
}

void ObjectSynchronizer::list_oops_do(ObjectMonitor* list, OopClosure* f) {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at safepoint");
  ObjectMonitor* mid;
  for (mid = list; mid != NULL; mid = mid->_next_om) {
    if (mid->object() != NULL) {
      f->do_oop((oop*)mid->object_addr());
    }
  }
}


// -----------------------------------------------------------------------------
// ObjectMonitor Lifecycle
// -----------------------
// Inflation unlinks monitors from the global g_free_list and
// associates them with objects.  Deflation -- which occurs at
// STW-time -- disassociates idle monitors from objects.  Such
// scavenged monitors are returned to the g_free_list.
//
// The global list is protected by gListLock.  All the critical sections
// are short and operate in constant-time.
//
// ObjectMonitors reside in type-stable memory (TSM) and are immortal.
//
// Lifecycle:
// --   unassigned and on the global free list
// --   unassigned and on a thread's private om_free_list
// --   assigned to an object.  The object is inflated and the mark refers
//      to the objectmonitor.


// Constraining monitor pool growth via MonitorBound ...
//
// If MonitorBound is not set (<= 0), MonitorBound checks are disabled.
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
//
// If MonitorBound is set, the boundry applies to
//     (g_om_population - g_om_free_count)
// i.e., if there are not enough ObjectMonitors on the global free list,
// then a safepoint deflation is induced. Picking a good MonitorBound value
// is non-trivial.

static void InduceScavenge(Thread* self, const char * Whence) {
  // Induce STW safepoint to trim monitors
  // Ultimately, this results in a call to deflate_idle_monitors() in the near future.
  // More precisely, trigger an asynchronous STW safepoint as the number
  // of active monitors passes the specified threshold.
  // TODO: assert thread state is reasonable

  if (ForceMonitorScavenge == 0 && Atomic::xchg (1, &ForceMonitorScavenge) == 0) {
    // Induce a 'null' safepoint to scavenge monitors
    // Must VM_Operation instance be heap allocated as the op will be enqueue and posted
    // to the VMthread and have a lifespan longer than that of this activation record.
    // The VMThread will delete the op when completed.
    VMThread::execute(new VM_ScavengeMonitors());
  }
}

ObjectMonitor* ObjectSynchronizer::om_alloc(Thread* self) {
  // A large MAXPRIVATE value reduces both list lock contention
  // and list coherency traffic, but also tends to increase the
  // number of ObjectMonitors in circulation as well as the STW
  // scavenge costs.  As usual, we lean toward time in space-time
  // tradeoffs.
  const int MAXPRIVATE = 1024;
  stringStream ss;
  for (;;) {
    ObjectMonitor* m;

    // 1: try to allocate from the thread's local om_free_list.
    // Threads will attempt to allocate first from their local list, then
    // from the global list, and only after those attempts fail will the thread
    // attempt to instantiate new monitors.   Thread-local free lists take
    // heat off the gListLock and improve allocation latency, as well as reducing
    // coherency traffic on the shared global list.
    m = self->om_free_list;
    if (m != NULL) {
      self->om_free_list = m->_next_om;
      self->om_free_count--;
      guarantee(m->object() == NULL, "invariant");
      m->_next_om = self->om_in_use_list;
      self->om_in_use_list = m;
      self->om_in_use_count++;
      return m;
    }

    // 2: try to allocate from the global g_free_list
    // CONSIDER: use muxTry() instead of muxAcquire().
    // If the muxTry() fails then drop immediately into case 3.
    // If we're using thread-local free lists then try
    // to reprovision the caller's free list.
    if (g_free_list != NULL) {
      // Reprovision the thread's om_free_list.
      // Use bulk transfers to reduce the allocation rate and heat
      // on various locks.
      Thread::muxAcquire(&gListLock, "om_alloc(1)");
      for (int i = self->om_free_provision; --i >= 0 && g_free_list != NULL;) {
        g_om_free_count--;
        ObjectMonitor* take = g_free_list;
        g_free_list = take->_next_om;
        guarantee(take->object() == NULL, "invariant");
        take->Recycle();
        om_release(self, take, false);
      }
      Thread::muxRelease(&gListLock);
      self->om_free_provision += 1 + (self->om_free_provision/2);
      if (self->om_free_provision > MAXPRIVATE) self->om_free_provision = MAXPRIVATE;

      const int mx = MonitorBound;
      if (mx > 0 && (g_om_population-g_om_free_count) > mx) {
        // Not enough ObjectMonitors on the global free list.
        // We can't safely induce a STW safepoint from om_alloc() as our thread
        // state may not be appropriate for such activities and callers may hold
        // naked oops, so instead we defer the action.
        InduceScavenge(self, "om_alloc");
      }
      continue;
    }

    // 3: allocate a block of new ObjectMonitors
    // Both the local and global free lists are empty -- resort to malloc().
    // In the current implementation ObjectMonitors are TSM - immortal.
    // Ideally, we'd write "new ObjectMonitor[_BLOCKSIZE], but we want
    // each ObjectMonitor to start at the beginning of a cache line,
    // so we use align_up().
    // A better solution would be to use C++ placement-new.
    // BEWARE: As it stands currently, we don't run the ctors!
    assert(_BLOCKSIZE > 1, "invariant");
    size_t neededsize = sizeof(PaddedObjectMonitor) * _BLOCKSIZE;
    PaddedObjectMonitor* temp;
    size_t aligned_size = neededsize + (DEFAULT_CACHE_LINE_SIZE - 1);
    void* real_malloc_addr = NEW_C_HEAP_ARRAY(char, aligned_size, mtInternal);
    temp = (PaddedObjectMonitor*)align_up(real_malloc_addr, DEFAULT_CACHE_LINE_SIZE);
    (void)memset((void *) temp, 0, neededsize);

    // Format the block.
    // initialize the linked list, each monitor points to its next
    // forming the single linked free list, the very first monitor
    // will points to next block, which forms the block list.
    // The trick of using the 1st element in the block as g_block_list
    // linkage should be reconsidered.  A better implementation would
    // look like: class Block { Block * next; int N; ObjectMonitor Body [N] ; }

    for (int i = 1; i < _BLOCKSIZE; i++) {
      temp[i]._next_om = (ObjectMonitor *)&temp[i+1];
    }

    // terminate the last monitor as the end of list
    temp[_BLOCKSIZE - 1]._next_om = NULL;

    // Element [0] is reserved for global list linkage
    temp[0].set_object(CHAINMARKER);

    // Consider carving out this thread's current request from the
    // block in hand.  This avoids some lock traffic and redundant
    // list activity.

    // Acquire the gListLock to manipulate g_block_list and g_free_list.
    // An Oyama-Taura-Yonezawa scheme might be more efficient.
    Thread::muxAcquire(&gListLock, "om_alloc(2)");
    g_om_population += _BLOCKSIZE-1;
    g_om_free_count += _BLOCKSIZE-1;

    // Add the new block to the list of extant blocks (g_block_list).
    // The very first ObjectMonitor in a block is reserved and dedicated.
    // It serves as blocklist "next" linkage.
    temp[0]._next_om = g_block_list;
    // There are lock-free uses of g_block_list so make sure that
    // the previous stores happen before we update g_block_list.
    OrderAccess::release_store(&g_block_list, temp);

    // Add the new string of ObjectMonitors to the global free list
    temp[_BLOCKSIZE - 1]._next_om = g_free_list;
    g_free_list = temp + 1;
    Thread::muxRelease(&gListLock);
  }
}

// Place "m" on the caller's private per-thread om_free_list.
// In practice there's no need to clamp or limit the number of
// monitors on a thread's om_free_list as the only non-allocation time
// we'll call om_release() is to return a monitor to the free list after
// a CAS attempt failed. This doesn't allow unbounded #s of monitors to
// accumulate on a thread's free list.
//
// Key constraint: all ObjectMonitors on a thread's free list and the global
// free list must have their object field set to null. This prevents the
// scavenger -- deflate_monitor_list() -- from reclaiming them while we
// are trying to release them.

void ObjectSynchronizer::om_release(Thread* self, ObjectMonitor* m,
                                    bool from_per_thread_alloc) {
  guarantee(m->header().value() == 0, "invariant");
  guarantee(m->object() == NULL, "invariant");
  stringStream ss;
  guarantee((m->is_busy() | m->_recursions) == 0, "freeing in-use monitor: "
            "%s, recursions=" INTPTR_FORMAT, m->is_busy_to_string(&ss),
            m->_recursions);
  // _next_om is used for both per-thread in-use and free lists so
  // we have to remove 'm' from the in-use list first (as needed).
  if (from_per_thread_alloc) {
    // Need to remove 'm' from om_in_use_list.
    ObjectMonitor* cur_mid_in_use = NULL;
    bool extracted = false;
    for (ObjectMonitor* mid = self->om_in_use_list; mid != NULL; cur_mid_in_use = mid, mid = mid->_next_om) {
      if (m == mid) {
        // extract from per-thread in-use list
        if (mid == self->om_in_use_list) {
          self->om_in_use_list = mid->_next_om;
        } else if (cur_mid_in_use != NULL) {
          cur_mid_in_use->_next_om = mid->_next_om; // maintain the current thread in-use list
        }
        extracted = true;
        self->om_in_use_count--;
        break;
      }
    }
    assert(extracted, "Should have extracted from in-use list");
  }

  m->_next_om = self->om_free_list;
  self->om_free_list = m;
  self->om_free_count++;
}

// Return ObjectMonitors on a moribund thread's free and in-use
// lists to the appropriate global lists. The ObjectMonitors on the
// per-thread in-use list may still be in use by other threads.
//
// We currently call om_flush() from Threads::remove() before the
// thread has been excised from the thread list and is no longer a
// mutator. This means that om_flush() cannot run concurrently with
// a safepoint and interleave with deflate_idle_monitors(). In
// particular, this ensures that the thread's in-use monitors are
// scanned by a GC safepoint, either via Thread::oops_do() (before
// om_flush() is called) or via ObjectSynchronizer::oops_do() (after
// om_flush() is called).

void ObjectSynchronizer::om_flush(Thread* self) {
  ObjectMonitor* free_list = self->om_free_list;
  ObjectMonitor* free_tail = NULL;
  int free_count = 0;
  if (free_list != NULL) {
    ObjectMonitor* s;
    // The thread is going away. Set 'free_tail' to the last per-thread free
    // monitor which will be linked to g_free_list below under the gListLock.
    stringStream ss;
    for (s = free_list; s != NULL; s = s->_next_om) {
      free_count++;
      free_tail = s;
      guarantee(s->object() == NULL, "invariant");
      guarantee(!s->is_busy(), "must be !is_busy: %s", s->is_busy_to_string(&ss));
    }
    guarantee(free_tail != NULL, "invariant");
    assert(self->om_free_count == free_count, "free-count off");
    self->om_free_list = NULL;
    self->om_free_count = 0;
  }

  ObjectMonitor* in_use_list = self->om_in_use_list;
  ObjectMonitor* in_use_tail = NULL;
  int in_use_count = 0;
  if (in_use_list != NULL) {
    // The thread is going away, however the ObjectMonitors on the
    // om_in_use_list may still be in-use by other threads. Link
    // them to in_use_tail, which will be linked into the global
    // in-use list g_om_in_use_list below, under the gListLock.
    ObjectMonitor *cur_om;
    for (cur_om = in_use_list; cur_om != NULL; cur_om = cur_om->_next_om) {
      in_use_tail = cur_om;
      in_use_count++;
    }
    guarantee(in_use_tail != NULL, "invariant");
    assert(self->om_in_use_count == in_use_count, "in-use count off");
    self->om_in_use_list = NULL;
    self->om_in_use_count = 0;
  }

  Thread::muxAcquire(&gListLock, "om_flush");
  if (free_tail != NULL) {
    free_tail->_next_om = g_free_list;
    g_free_list = free_list;
    g_om_free_count += free_count;
  }

  if (in_use_tail != NULL) {
    in_use_tail->_next_om = g_om_in_use_list;
    g_om_in_use_list = in_use_list;
    g_om_in_use_count += in_use_count;
  }

  Thread::muxRelease(&gListLock);

  LogStreamHandle(Debug, monitorinflation) lsh_debug;
  LogStreamHandle(Info, monitorinflation) lsh_info;
  LogStream* ls = NULL;
  if (log_is_enabled(Debug, monitorinflation)) {
    ls = &lsh_debug;
  } else if ((free_count != 0 || in_use_count != 0) &&
             log_is_enabled(Info, monitorinflation)) {
    ls = &lsh_info;
  }
  if (ls != NULL) {
    ls->print_cr("om_flush: jt=" INTPTR_FORMAT ", free_count=%d"
                 ", in_use_count=%d" ", om_free_provision=%d",
                 p2i(self), free_count, in_use_count, self->om_free_provision);
  }
}

static void post_monitor_inflate_event(EventJavaMonitorInflate* event,
                                       const oop obj,
                                       ObjectSynchronizer::InflateCause cause) {
  assert(event != NULL, "invariant");
  assert(event->should_commit(), "invariant");
  event->set_monitorClass(obj->klass());
  event->set_address((uintptr_t)(void*)obj);
  event->set_cause((u1)cause);
  event->commit();
}

// Fast path code shared by multiple functions
void ObjectSynchronizer::inflate_helper(oop obj) {
  markWord mark = obj->mark();
  if (mark.has_monitor()) {
    assert(ObjectSynchronizer::verify_objmon_isinpool(mark.monitor()), "monitor is invalid");
    assert(mark.monitor()->header().is_neutral(), "monitor must record a good object header");
    return;
  }
  inflate(Thread::current(), obj, inflate_cause_vm_internal);
}

ObjectMonitor* ObjectSynchronizer::inflate(Thread* self,
                                           oop object,
                                           const InflateCause cause) {
  // Inflate mutates the heap ...
  // Relaxing assertion for bug 6320749.
  assert(Universe::verify_in_progress() ||
         !SafepointSynchronize::is_at_safepoint(), "invariant");

  EventJavaMonitorInflate event;

  for (;;) {
    const markWord mark = object->mark();
    assert(!mark.has_bias_pattern(), "invariant");

    // The mark can be in one of the following states:
    // *  Inflated     - just return
    // *  Stack-locked - coerce it to inflated
    // *  INFLATING    - busy wait for conversion to complete
    // *  Neutral      - aggressively inflate the object.
    // *  BIASED       - Illegal.  We should never see this

    // CASE: inflated
    if (mark.has_monitor()) {
      ObjectMonitor* inf = mark.monitor();
      markWord dmw = inf->header();
      assert(dmw.is_neutral(), "invariant: header=" INTPTR_FORMAT, dmw.value());
      assert(inf->object() == object, "invariant");
      assert(ObjectSynchronizer::verify_objmon_isinpool(inf), "monitor is invalid");
      return inf;
    }

    // CASE: inflation in progress - inflating over a stack-lock.
    // Some other thread is converting from stack-locked to inflated.
    // Only that thread can complete inflation -- other threads must wait.
    // The INFLATING value is transient.
    // Currently, we spin/yield/park and poll the markword, waiting for inflation to finish.
    // We could always eliminate polling by parking the thread on some auxiliary list.
    if (mark == markWord::INFLATING()) {
      read_stable_mark(object);
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
    // Using such local free lists, it doesn't matter if the om_alloc() call appears
    // before or after the CAS(INFLATING) operation.
    // See the comments in om_alloc().

    LogStreamHandle(Trace, monitorinflation) lsh;

    if (mark.has_locker()) {
      ObjectMonitor* m = om_alloc(self);
      // Optimistically prepare the objectmonitor - anticipate successful CAS
      // We do this before the CAS in order to minimize the length of time
      // in which INFLATING appears in the mark.
      m->Recycle();
      m->_Responsible  = NULL;
      m->_SpinDuration = ObjectMonitor::Knob_SpinLimit;   // Consider: maintain by type/class

      markWord cmp = object->cas_set_mark(markWord::INFLATING(), mark);
      if (cmp != mark) {
        om_release(self, m, true);
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
      // on-stack BasicLock back into the object header.  Recall also that the
      // header value (hash code, etc) can reside in (a) the object header, or
      // (b) a displaced header associated with the stack-lock, or (c) a displaced
      // header in an ObjectMonitor.  The inflate() routine must copy the header
      // value from the BasicLock on the owner's stack to the ObjectMonitor, all
      // the while preserving the hashCode stability invariants.  If the owner
      // decides to release the lock while the value is 0, the unlock will fail
      // and control will eventually pass from slow_exit() to inflate.  The owner
      // will then spin, waiting for the 0 value to disappear.   Put another way,
      // the 0 causes the owner to stall if the owner happens to try to
      // drop the lock (restoring the header from the BasicLock to the object)
      // while inflation is in-progress.  This protocol avoids races that might
      // would otherwise permit hashCode values to change or "flicker" for an object.
      // Critically, while object->mark is 0 mark.displaced_mark_helper() is stable.
      // 0 serves as a "BUSY" inflate-in-progress indicator.


      // fetch the displaced mark from the owner's stack.
      // The owner can't die or unwind past the lock while our INFLATING
      // object is in the mark.  Furthermore the owner can't complete
      // an unlock on the object, either.
      markWord dmw = mark.displaced_mark_helper();
      // Catch if the object's header is not neutral (not locked and
      // not marked is what we care about here).
      assert(dmw.is_neutral(), "invariant: header=" INTPTR_FORMAT, dmw.value());

      // Setup monitor fields to proper values -- prepare the monitor
      m->set_header(dmw);

      // Optimization: if the mark.locker stack address is associated
      // with this thread we could simply set m->_owner = self.
      // Note that a thread can inflate an object
      // that it has stack-locked -- as might happen in wait() -- directly
      // with CAS.  That is, we can avoid the xchg-NULL .... ST idiom.
      m->set_owner(mark.locker());
      m->set_object(object);
      // TODO-FIXME: assert BasicLock->dhw != 0.

      // Must preserve store ordering. The monitor state must
      // be stable at the time of publishing the monitor address.
      guarantee(object->mark() == markWord::INFLATING(), "invariant");
      object->release_set_mark(markWord::encode(m));

      // Hopefully the performance counters are allocated on distinct cache lines
      // to avoid false sharing on MP systems ...
      OM_PERFDATA_OP(Inflations, inc());
      if (log_is_enabled(Trace, monitorinflation)) {
        ResourceMark rm(self);
        lsh.print_cr("inflate(has_locker): object=" INTPTR_FORMAT ", mark="
                     INTPTR_FORMAT ", type='%s'", p2i(object),
                     object->mark().value(), object->klass()->external_name());
      }
      if (event.should_commit()) {
        post_monitor_inflate_event(&event, object, cause);
      }
      return m;
    }

    // CASE: neutral
    // TODO-FIXME: for entry we currently inflate and then try to CAS _owner.
    // If we know we're inflating for entry it's better to inflate by swinging a
    // pre-locked ObjectMonitor pointer into the object header.   A successful
    // CAS inflates the object *and* confers ownership to the inflating thread.
    // In the current implementation we use a 2-step mechanism where we CAS()
    // to inflate and then CAS() again to try to swing _owner from NULL to self.
    // An inflateTry() method that we could call from enter() would be useful.

    // Catch if the object's header is not neutral (not locked and
    // not marked is what we care about here).
    assert(mark.is_neutral(), "invariant: header=" INTPTR_FORMAT, mark.value());
    ObjectMonitor* m = om_alloc(self);
    // prepare m for installation - set monitor to initial state
    m->Recycle();
    m->set_header(mark);
    m->set_object(object);
    m->_Responsible  = NULL;
    m->_SpinDuration = ObjectMonitor::Knob_SpinLimit;       // consider: keep metastats by type/class

    if (object->cas_set_mark(markWord::encode(m), mark) != mark) {
      m->set_header(markWord::zero());
      m->set_object(NULL);
      m->Recycle();
      om_release(self, m, true);
      m = NULL;
      continue;
      // interference - the markword changed - just retry.
      // The state-transitions are one-way, so there's no chance of
      // live-lock -- "Inflated" is an absorbing state.
    }

    // Hopefully the performance counters are allocated on distinct
    // cache lines to avoid false sharing on MP systems ...
    OM_PERFDATA_OP(Inflations, inc());
    if (log_is_enabled(Trace, monitorinflation)) {
      ResourceMark rm(self);
      lsh.print_cr("inflate(neutral): object=" INTPTR_FORMAT ", mark="
                   INTPTR_FORMAT ", type='%s'", p2i(object),
                   object->mark().value(), object->klass()->external_name());
    }
    if (event.should_commit()) {
      post_monitor_inflate_event(&event, object, cause);
    }
    return m;
  }
}


// We maintain a list of in-use monitors for each thread.
//
// deflate_thread_local_monitors() scans a single thread's in-use list, while
// deflate_idle_monitors() scans only a global list of in-use monitors which
// is populated only as a thread dies (see om_flush()).
//
// These operations are called at all safepoints, immediately after mutators
// are stopped, but before any objects have moved. Collectively they traverse
// the population of in-use monitors, deflating where possible. The scavenged
// monitors are returned to the global monitor free list.
//
// Beware that we scavenge at *every* stop-the-world point. Having a large
// number of monitors in-use could negatively impact performance. We also want
// to minimize the total # of monitors in circulation, as they incur a small
// footprint penalty.
//
// Perversely, the heap size -- and thus the STW safepoint rate --
// typically drives the scavenge rate.  Large heaps can mean infrequent GC,
// which in turn can mean large(r) numbers of ObjectMonitors in circulation.
// This is an unfortunate aspect of this design.

// Deflate a single monitor if not in-use
// Return true if deflated, false if in-use
bool ObjectSynchronizer::deflate_monitor(ObjectMonitor* mid, oop obj,
                                         ObjectMonitor** free_head_p,
                                         ObjectMonitor** free_tail_p) {
  bool deflated;
  // Normal case ... The monitor is associated with obj.
  const markWord mark = obj->mark();
  guarantee(mark == markWord::encode(mid), "should match: mark="
            INTPTR_FORMAT ", encoded mid=" INTPTR_FORMAT, mark.value(),
            markWord::encode(mid).value());
  // Make sure that mark.monitor() and markWord::encode() agree:
  guarantee(mark.monitor() == mid, "should match: monitor()=" INTPTR_FORMAT
            ", mid=" INTPTR_FORMAT, p2i(mark.monitor()), p2i(mid));
  const markWord dmw = mid->header();
  guarantee(dmw.is_neutral(), "invariant: header=" INTPTR_FORMAT, dmw.value());

  if (mid->is_busy()) {
    deflated = false;
  } else {
    // Deflate the monitor if it is no longer being used
    // It's idle - scavenge and return to the global free list
    // plain old deflation ...
    if (log_is_enabled(Trace, monitorinflation)) {
      ResourceMark rm;
      log_trace(monitorinflation)("deflate_monitor: "
                                  "object=" INTPTR_FORMAT ", mark="
                                  INTPTR_FORMAT ", type='%s'", p2i(obj),
                                  mark.value(), obj->klass()->external_name());
    }

    // Restore the header back to obj
    obj->release_set_mark(dmw);
    mid->clear();

    assert(mid->object() == NULL, "invariant: object=" INTPTR_FORMAT,
           p2i(mid->object()));

    // Move the deflated ObjectMonitor to the working free list
    // defined by free_head_p and free_tail_p.
    if (*free_head_p == NULL) *free_head_p = mid;
    if (*free_tail_p != NULL) {
      // We append to the list so the caller can use mid->_next_om
      // to fix the linkages in its context.
      ObjectMonitor* prevtail = *free_tail_p;
      // Should have been cleaned up by the caller:
      assert(prevtail->_next_om == NULL, "cleaned up deflated?");
      prevtail->_next_om = mid;
    }
    *free_tail_p = mid;
    // At this point, mid->_next_om still refers to its current
    // value and another ObjectMonitor's _next_om field still
    // refers to this ObjectMonitor. Those linkages have to be
    // cleaned up by the caller who has the complete context.
    deflated = true;
  }
  return deflated;
}

// Walk a given monitor list, and deflate idle monitors
// The given list could be a per-thread list or a global list
// Caller acquires gListLock as needed.
//
// In the case of parallel processing of thread local monitor lists,
// work is done by Threads::parallel_threads_do() which ensures that
// each Java thread is processed by exactly one worker thread, and
// thus avoid conflicts that would arise when worker threads would
// process the same monitor lists concurrently.
//
// See also ParallelSPCleanupTask and
// SafepointSynchronize::do_cleanup_tasks() in safepoint.cpp and
// Threads::parallel_java_threads_do() in thread.cpp.
int ObjectSynchronizer::deflate_monitor_list(ObjectMonitor** list_p,
                                             ObjectMonitor** free_head_p,
                                             ObjectMonitor** free_tail_p) {
  ObjectMonitor* mid;
  ObjectMonitor* next;
  ObjectMonitor* cur_mid_in_use = NULL;
  int deflated_count = 0;

  for (mid = *list_p; mid != NULL;) {
    oop obj = (oop) mid->object();
    if (obj != NULL && deflate_monitor(mid, obj, free_head_p, free_tail_p)) {
      // Deflation succeeded and already updated free_head_p and
      // free_tail_p as needed. Finish the move to the local free list
      // by unlinking mid from the global or per-thread in-use list.
      if (mid == *list_p) {
        *list_p = mid->_next_om;
      } else if (cur_mid_in_use != NULL) {
        cur_mid_in_use->_next_om = mid->_next_om; // maintain the current thread in-use list
      }
      next = mid->_next_om;
      mid->_next_om = NULL;  // This mid is current tail in the free_head_p list
      mid = next;
      deflated_count++;
    } else {
      cur_mid_in_use = mid;
      mid = mid->_next_om;
    }
  }
  return deflated_count;
}

void ObjectSynchronizer::prepare_deflate_idle_monitors(DeflateMonitorCounters* counters) {
  counters->n_in_use = 0;              // currently associated with objects
  counters->n_in_circulation = 0;      // extant
  counters->n_scavenged = 0;           // reclaimed (global and per-thread)
  counters->per_thread_scavenged = 0;  // per-thread scavenge total
  counters->per_thread_times = 0.0;    // per-thread scavenge times
}

void ObjectSynchronizer::deflate_idle_monitors(DeflateMonitorCounters* counters) {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at safepoint");
  bool deflated = false;

  ObjectMonitor* free_head_p = NULL;  // Local SLL of scavenged monitors
  ObjectMonitor* free_tail_p = NULL;
  elapsedTimer timer;

  if (log_is_enabled(Info, monitorinflation)) {
    timer.start();
  }

  // Prevent om_flush from changing mids in Thread dtor's during deflation
  // And in case the vm thread is acquiring a lock during a safepoint
  // See e.g. 6320749
  Thread::muxAcquire(&gListLock, "deflate_idle_monitors");

  // Note: the thread-local monitors lists get deflated in
  // a separate pass. See deflate_thread_local_monitors().

  // For moribund threads, scan g_om_in_use_list
  int deflated_count = 0;
  if (g_om_in_use_list) {
    counters->n_in_circulation += g_om_in_use_count;
    deflated_count = deflate_monitor_list((ObjectMonitor **)&g_om_in_use_list, &free_head_p, &free_tail_p);
    g_om_in_use_count -= deflated_count;
    counters->n_scavenged += deflated_count;
    counters->n_in_use += g_om_in_use_count;
  }

  if (free_head_p != NULL) {
    // Move the deflated ObjectMonitors back to the global free list.
    guarantee(free_tail_p != NULL && counters->n_scavenged > 0, "invariant");
    assert(free_tail_p->_next_om == NULL, "invariant");
    // constant-time list splice - prepend scavenged segment to g_free_list
    free_tail_p->_next_om = g_free_list;
    g_free_list = free_head_p;
  }
  Thread::muxRelease(&gListLock);
  timer.stop();

  LogStreamHandle(Debug, monitorinflation) lsh_debug;
  LogStreamHandle(Info, monitorinflation) lsh_info;
  LogStream* ls = NULL;
  if (log_is_enabled(Debug, monitorinflation)) {
    ls = &lsh_debug;
  } else if (deflated_count != 0 && log_is_enabled(Info, monitorinflation)) {
    ls = &lsh_info;
  }
  if (ls != NULL) {
    ls->print_cr("deflating global idle monitors, %3.7f secs, %d monitors", timer.seconds(), deflated_count);
  }
}

void ObjectSynchronizer::finish_deflate_idle_monitors(DeflateMonitorCounters* counters) {
  // Report the cumulative time for deflating each thread's idle
  // monitors. Note: if the work is split among more than one
  // worker thread, then the reported time will likely be more
  // than a beginning to end measurement of the phase.
  log_info(safepoint, cleanup)("deflating per-thread idle monitors, %3.7f secs, monitors=%d", counters->per_thread_times, counters->per_thread_scavenged);

  g_om_free_count += counters->n_scavenged;

  if (log_is_enabled(Debug, monitorinflation)) {
    // exit_globals()'s call to audit_and_print_stats() is done
    // at the Info level.
    ObjectSynchronizer::audit_and_print_stats(false /* on_exit */);
  } else if (log_is_enabled(Info, monitorinflation)) {
    Thread::muxAcquire(&gListLock, "finish_deflate_idle_monitors");
    log_info(monitorinflation)("g_om_population=%d, g_om_in_use_count=%d, "
                               "g_om_free_count=%d", g_om_population,
                               g_om_in_use_count, g_om_free_count);
    Thread::muxRelease(&gListLock);
  }

  ForceMonitorScavenge = 0;    // Reset

  OM_PERFDATA_OP(Deflations, inc(counters->n_scavenged));
  OM_PERFDATA_OP(MonExtant, set_value(counters->n_in_circulation));

  GVars.stw_random = os::random();
  GVars.stw_cycle++;
}

void ObjectSynchronizer::deflate_thread_local_monitors(Thread* thread, DeflateMonitorCounters* counters) {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at safepoint");

  ObjectMonitor* free_head_p = NULL;  // Local SLL of scavenged monitors
  ObjectMonitor* free_tail_p = NULL;
  elapsedTimer timer;

  if (log_is_enabled(Info, safepoint, cleanup) ||
      log_is_enabled(Info, monitorinflation)) {
    timer.start();
  }

  int deflated_count = deflate_monitor_list(thread->om_in_use_list_addr(), &free_head_p, &free_tail_p);

  Thread::muxAcquire(&gListLock, "deflate_thread_local_monitors");

  // Adjust counters
  counters->n_in_circulation += thread->om_in_use_count;
  thread->om_in_use_count -= deflated_count;
  counters->n_scavenged += deflated_count;
  counters->n_in_use += thread->om_in_use_count;
  counters->per_thread_scavenged += deflated_count;

  if (free_head_p != NULL) {
    // Move the deflated ObjectMonitors back to the global free list.
    guarantee(free_tail_p != NULL && deflated_count > 0, "invariant");
    assert(free_tail_p->_next_om == NULL, "invariant");

    // constant-time list splice - prepend scavenged segment to g_free_list
    free_tail_p->_next_om = g_free_list;
    g_free_list = free_head_p;
  }

  timer.stop();
  // Safepoint logging cares about cumulative per_thread_times and
  // we'll capture most of the cost, but not the muxRelease() which
  // should be cheap.
  counters->per_thread_times += timer.seconds();

  Thread::muxRelease(&gListLock);

  LogStreamHandle(Debug, monitorinflation) lsh_debug;
  LogStreamHandle(Info, monitorinflation) lsh_info;
  LogStream* ls = NULL;
  if (log_is_enabled(Debug, monitorinflation)) {
    ls = &lsh_debug;
  } else if (deflated_count != 0 && log_is_enabled(Info, monitorinflation)) {
    ls = &lsh_info;
  }
  if (ls != NULL) {
    ls->print_cr("jt=" INTPTR_FORMAT ": deflating per-thread idle monitors, %3.7f secs, %d monitors", p2i(thread), timer.seconds(), deflated_count);
  }
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

//------------------------------------------------------------------------------
// Debugging code

u_char* ObjectSynchronizer::get_gvars_addr() {
  return (u_char*)&GVars;
}

u_char* ObjectSynchronizer::get_gvars_hc_sequence_addr() {
  return (u_char*)&GVars.hc_sequence;
}

size_t ObjectSynchronizer::get_gvars_size() {
  return sizeof(SharedGlobals);
}

u_char* ObjectSynchronizer::get_gvars_stw_random_addr() {
  return (u_char*)&GVars.stw_random;
}

void ObjectSynchronizer::audit_and_print_stats(bool on_exit) {
  assert(on_exit || SafepointSynchronize::is_at_safepoint(), "invariant");

  LogStreamHandle(Debug, monitorinflation) lsh_debug;
  LogStreamHandle(Info, monitorinflation) lsh_info;
  LogStreamHandle(Trace, monitorinflation) lsh_trace;
  LogStream* ls = NULL;
  if (log_is_enabled(Trace, monitorinflation)) {
    ls = &lsh_trace;
  } else if (log_is_enabled(Debug, monitorinflation)) {
    ls = &lsh_debug;
  } else if (log_is_enabled(Info, monitorinflation)) {
    ls = &lsh_info;
  }
  assert(ls != NULL, "sanity check");

  if (!on_exit) {
    // Not at VM exit so grab the global list lock.
    Thread::muxAcquire(&gListLock, "audit_and_print_stats");
  }

  // Log counts for the global and per-thread monitor lists:
  int chk_om_population = log_monitor_list_counts(ls);
  int error_cnt = 0;

  ls->print_cr("Checking global lists:");

  // Check g_om_population:
  if (g_om_population == chk_om_population) {
    ls->print_cr("g_om_population=%d equals chk_om_population=%d",
                 g_om_population, chk_om_population);
  } else {
    ls->print_cr("ERROR: g_om_population=%d is not equal to "
                 "chk_om_population=%d", g_om_population,
                 chk_om_population);
    error_cnt++;
  }

  // Check g_om_in_use_list and g_om_in_use_count:
  chk_global_in_use_list_and_count(ls, &error_cnt);

  // Check g_free_list and g_om_free_count:
  chk_global_free_list_and_count(ls, &error_cnt);

  if (!on_exit) {
    Thread::muxRelease(&gListLock);
  }

  ls->print_cr("Checking per-thread lists:");

  for (JavaThreadIteratorWithHandle jtiwh; JavaThread *jt = jtiwh.next(); ) {
    // Check om_in_use_list and om_in_use_count:
    chk_per_thread_in_use_list_and_count(jt, ls, &error_cnt);

    // Check om_free_list and om_free_count:
    chk_per_thread_free_list_and_count(jt, ls, &error_cnt);
  }

  if (error_cnt == 0) {
    ls->print_cr("No errors found in monitor list checks.");
  } else {
    log_error(monitorinflation)("found monitor list errors: error_cnt=%d", error_cnt);
  }

  if ((on_exit && log_is_enabled(Info, monitorinflation)) ||
      (!on_exit && log_is_enabled(Trace, monitorinflation))) {
    // When exiting this log output is at the Info level. When called
    // at a safepoint, this log output is at the Trace level since
    // there can be a lot of it.
    log_in_use_monitor_details(ls, on_exit);
  }

  ls->flush();

  guarantee(error_cnt == 0, "ERROR: found monitor list errors: error_cnt=%d", error_cnt);
}

// Check a free monitor entry; log any errors.
void ObjectSynchronizer::chk_free_entry(JavaThread* jt, ObjectMonitor* n,
                                        outputStream * out, int *error_cnt_p) {
  stringStream ss;
  if (n->is_busy()) {
    if (jt != NULL) {
      out->print_cr("ERROR: jt=" INTPTR_FORMAT ", monitor=" INTPTR_FORMAT
                    ": free per-thread monitor must not be busy: %s", p2i(jt),
                    p2i(n), n->is_busy_to_string(&ss));
    } else {
      out->print_cr("ERROR: monitor=" INTPTR_FORMAT ": free global monitor "
                    "must not be busy: %s", p2i(n), n->is_busy_to_string(&ss));
    }
    *error_cnt_p = *error_cnt_p + 1;
  }
  if (n->header().value() != 0) {
    if (jt != NULL) {
      out->print_cr("ERROR: jt=" INTPTR_FORMAT ", monitor=" INTPTR_FORMAT
                    ": free per-thread monitor must have NULL _header "
                    "field: _header=" INTPTR_FORMAT, p2i(jt), p2i(n),
                    n->header().value());
    } else {
      out->print_cr("ERROR: monitor=" INTPTR_FORMAT ": free global monitor "
                    "must have NULL _header field: _header=" INTPTR_FORMAT,
                    p2i(n), n->header().value());
    }
    *error_cnt_p = *error_cnt_p + 1;
  }
  if (n->object() != NULL) {
    if (jt != NULL) {
      out->print_cr("ERROR: jt=" INTPTR_FORMAT ", monitor=" INTPTR_FORMAT
                    ": free per-thread monitor must have NULL _object "
                    "field: _object=" INTPTR_FORMAT, p2i(jt), p2i(n),
                    p2i(n->object()));
    } else {
      out->print_cr("ERROR: monitor=" INTPTR_FORMAT ": free global monitor "
                    "must have NULL _object field: _object=" INTPTR_FORMAT,
                    p2i(n), p2i(n->object()));
    }
    *error_cnt_p = *error_cnt_p + 1;
  }
}

// Check the global free list and count; log the results of the checks.
void ObjectSynchronizer::chk_global_free_list_and_count(outputStream * out,
                                                        int *error_cnt_p) {
  int chk_om_free_count = 0;
  for (ObjectMonitor* n = g_free_list; n != NULL; n = n->_next_om) {
    chk_free_entry(NULL /* jt */, n, out, error_cnt_p);
    chk_om_free_count++;
  }
  if (g_om_free_count == chk_om_free_count) {
    out->print_cr("g_om_free_count=%d equals chk_om_free_count=%d",
                  g_om_free_count, chk_om_free_count);
  } else {
    out->print_cr("ERROR: g_om_free_count=%d is not equal to "
                  "chk_om_free_count=%d", g_om_free_count,
                  chk_om_free_count);
    *error_cnt_p = *error_cnt_p + 1;
  }
}

// Check the global in-use list and count; log the results of the checks.
void ObjectSynchronizer::chk_global_in_use_list_and_count(outputStream * out,
                                                          int *error_cnt_p) {
  int chk_om_in_use_count = 0;
  for (ObjectMonitor* n = g_om_in_use_list; n != NULL; n = n->_next_om) {
    chk_in_use_entry(NULL /* jt */, n, out, error_cnt_p);
    chk_om_in_use_count++;
  }
  if (g_om_in_use_count == chk_om_in_use_count) {
    out->print_cr("g_om_in_use_count=%d equals chk_om_in_use_count=%d", g_om_in_use_count,
                  chk_om_in_use_count);
  } else {
    out->print_cr("ERROR: g_om_in_use_count=%d is not equal to chk_om_in_use_count=%d",
                  g_om_in_use_count, chk_om_in_use_count);
    *error_cnt_p = *error_cnt_p + 1;
  }
}

// Check an in-use monitor entry; log any errors.
void ObjectSynchronizer::chk_in_use_entry(JavaThread* jt, ObjectMonitor* n,
                                          outputStream * out, int *error_cnt_p) {
  if (n->header().value() == 0) {
    if (jt != NULL) {
      out->print_cr("ERROR: jt=" INTPTR_FORMAT ", monitor=" INTPTR_FORMAT
                    ": in-use per-thread monitor must have non-NULL _header "
                    "field.", p2i(jt), p2i(n));
    } else {
      out->print_cr("ERROR: monitor=" INTPTR_FORMAT ": in-use global monitor "
                    "must have non-NULL _header field.", p2i(n));
    }
    *error_cnt_p = *error_cnt_p + 1;
  }
  if (n->object() == NULL) {
    if (jt != NULL) {
      out->print_cr("ERROR: jt=" INTPTR_FORMAT ", monitor=" INTPTR_FORMAT
                    ": in-use per-thread monitor must have non-NULL _object "
                    "field.", p2i(jt), p2i(n));
    } else {
      out->print_cr("ERROR: monitor=" INTPTR_FORMAT ": in-use global monitor "
                    "must have non-NULL _object field.", p2i(n));
    }
    *error_cnt_p = *error_cnt_p + 1;
  }
  const oop obj = (oop)n->object();
  const markWord mark = obj->mark();
  if (!mark.has_monitor()) {
    if (jt != NULL) {
      out->print_cr("ERROR: jt=" INTPTR_FORMAT ", monitor=" INTPTR_FORMAT
                    ": in-use per-thread monitor's object does not think "
                    "it has a monitor: obj=" INTPTR_FORMAT ", mark="
                    INTPTR_FORMAT,  p2i(jt), p2i(n), p2i(obj), mark.value());
    } else {
      out->print_cr("ERROR: monitor=" INTPTR_FORMAT ": in-use global "
                    "monitor's object does not think it has a monitor: obj="
                    INTPTR_FORMAT ", mark=" INTPTR_FORMAT, p2i(n),
                    p2i(obj), mark.value());
    }
    *error_cnt_p = *error_cnt_p + 1;
  }
  ObjectMonitor* const obj_mon = mark.monitor();
  if (n != obj_mon) {
    if (jt != NULL) {
      out->print_cr("ERROR: jt=" INTPTR_FORMAT ", monitor=" INTPTR_FORMAT
                    ": in-use per-thread monitor's object does not refer "
                    "to the same monitor: obj=" INTPTR_FORMAT ", mark="
                    INTPTR_FORMAT ", obj_mon=" INTPTR_FORMAT, p2i(jt),
                    p2i(n), p2i(obj), mark.value(), p2i(obj_mon));
    } else {
      out->print_cr("ERROR: monitor=" INTPTR_FORMAT ": in-use global "
                    "monitor's object does not refer to the same monitor: obj="
                    INTPTR_FORMAT ", mark=" INTPTR_FORMAT ", obj_mon="
                    INTPTR_FORMAT, p2i(n), p2i(obj), mark.value(), p2i(obj_mon));
    }
    *error_cnt_p = *error_cnt_p + 1;
  }
}

// Check the thread's free list and count; log the results of the checks.
void ObjectSynchronizer::chk_per_thread_free_list_and_count(JavaThread *jt,
                                                            outputStream * out,
                                                            int *error_cnt_p) {
  int chk_om_free_count = 0;
  for (ObjectMonitor* n = jt->om_free_list; n != NULL; n = n->_next_om) {
    chk_free_entry(jt, n, out, error_cnt_p);
    chk_om_free_count++;
  }
  if (jt->om_free_count == chk_om_free_count) {
    out->print_cr("jt=" INTPTR_FORMAT ": om_free_count=%d equals "
                  "chk_om_free_count=%d", p2i(jt), jt->om_free_count, chk_om_free_count);
  } else {
    out->print_cr("ERROR: jt=" INTPTR_FORMAT ": om_free_count=%d is not "
                  "equal to chk_om_free_count=%d", p2i(jt), jt->om_free_count,
                  chk_om_free_count);
    *error_cnt_p = *error_cnt_p + 1;
  }
}

// Check the thread's in-use list and count; log the results of the checks.
void ObjectSynchronizer::chk_per_thread_in_use_list_and_count(JavaThread *jt,
                                                              outputStream * out,
                                                              int *error_cnt_p) {
  int chk_om_in_use_count = 0;
  for (ObjectMonitor* n = jt->om_in_use_list; n != NULL; n = n->_next_om) {
    chk_in_use_entry(jt, n, out, error_cnt_p);
    chk_om_in_use_count++;
  }
  if (jt->om_in_use_count == chk_om_in_use_count) {
    out->print_cr("jt=" INTPTR_FORMAT ": om_in_use_count=%d equals "
                  "chk_om_in_use_count=%d", p2i(jt), jt->om_in_use_count,
                  chk_om_in_use_count);
  } else {
    out->print_cr("ERROR: jt=" INTPTR_FORMAT ": om_in_use_count=%d is not "
                  "equal to chk_om_in_use_count=%d", p2i(jt), jt->om_in_use_count,
                  chk_om_in_use_count);
    *error_cnt_p = *error_cnt_p + 1;
  }
}

// Log details about ObjectMonitors on the in-use lists. The 'BHL'
// flags indicate why the entry is in-use, 'object' and 'object type'
// indicate the associated object and its type.
void ObjectSynchronizer::log_in_use_monitor_details(outputStream * out,
                                                    bool on_exit) {
  if (!on_exit) {
    // Not at VM exit so grab the global list lock.
    Thread::muxAcquire(&gListLock, "log_in_use_monitor_details");
  }

  stringStream ss;
  if (g_om_in_use_count > 0) {
    out->print_cr("In-use global monitor info:");
    out->print_cr("(B -> is_busy, H -> has hash code, L -> lock status)");
    out->print_cr("%18s  %s  %18s  %18s",
                  "monitor", "BHL", "object", "object type");
    out->print_cr("==================  ===  ==================  ==================");
    for (ObjectMonitor* n = g_om_in_use_list; n != NULL; n = n->_next_om) {
      const oop obj = (oop) n->object();
      const markWord mark = n->header();
      ResourceMark rm;
      out->print(INTPTR_FORMAT "  %d%d%d  " INTPTR_FORMAT "  %s", p2i(n),
                 n->is_busy() != 0, mark.hash() != 0, n->owner() != NULL,
                 p2i(obj), obj->klass()->external_name());
      if (n->is_busy() != 0) {
        out->print(" (%s)", n->is_busy_to_string(&ss));
        ss.reset();
      }
      out->cr();
    }
  }

  if (!on_exit) {
    Thread::muxRelease(&gListLock);
  }

  out->print_cr("In-use per-thread monitor info:");
  out->print_cr("(B -> is_busy, H -> has hash code, L -> lock status)");
  out->print_cr("%18s  %18s  %s  %18s  %18s",
                "jt", "monitor", "BHL", "object", "object type");
  out->print_cr("==================  ==================  ===  ==================  ==================");
  for (JavaThreadIteratorWithHandle jtiwh; JavaThread *jt = jtiwh.next(); ) {
    for (ObjectMonitor* n = jt->om_in_use_list; n != NULL; n = n->_next_om) {
      const oop obj = (oop) n->object();
      const markWord mark = n->header();
      ResourceMark rm;
      out->print(INTPTR_FORMAT "  " INTPTR_FORMAT "  %d%d%d  " INTPTR_FORMAT
                 "  %s", p2i(jt), p2i(n), n->is_busy() != 0,
                 mark.hash() != 0, n->owner() != NULL, p2i(obj),
                 obj->klass()->external_name());
      if (n->is_busy() != 0) {
        out->print(" (%s)", n->is_busy_to_string(&ss));
        ss.reset();
      }
      out->cr();
    }
  }

  out->flush();
}

// Log counts for the global and per-thread monitor lists and return
// the population count.
int ObjectSynchronizer::log_monitor_list_counts(outputStream * out) {
  int pop_count = 0;
  out->print_cr("%18s  %10s  %10s  %10s",
                "Global Lists:", "InUse", "Free", "Total");
  out->print_cr("==================  ==========  ==========  ==========");
  out->print_cr("%18s  %10d  %10d  %10d", "",
                g_om_in_use_count, g_om_free_count, g_om_population);
  pop_count += g_om_in_use_count + g_om_free_count;

  out->print_cr("%18s  %10s  %10s  %10s",
                "Per-Thread Lists:", "InUse", "Free", "Provision");
  out->print_cr("==================  ==========  ==========  ==========");

  for (JavaThreadIteratorWithHandle jtiwh; JavaThread *jt = jtiwh.next(); ) {
    out->print_cr(INTPTR_FORMAT "  %10d  %10d  %10d", p2i(jt),
                  jt->om_in_use_count, jt->om_free_count, jt->om_free_provision);
    pop_count += jt->om_in_use_count + jt->om_free_count;
  }
  return pop_count;
}

#ifndef PRODUCT

// Check if monitor belongs to the monitor cache
// The list is grow-only so it's *relatively* safe to traverse
// the list of extant blocks without taking a lock.

int ObjectSynchronizer::verify_objmon_isinpool(ObjectMonitor *monitor) {
  PaddedObjectMonitor* block = OrderAccess::load_acquire(&g_block_list);
  while (block != NULL) {
    assert(block->object() == CHAINMARKER, "must be a block header");
    if (monitor > &block[0] && monitor < &block[_BLOCKSIZE]) {
      address mon = (address)monitor;
      address blk = (address)block;
      size_t diff = mon - blk;
      assert((diff % sizeof(PaddedObjectMonitor)) == 0, "must be aligned");
      return 1;
    }
    block = (PaddedObjectMonitor*)block->_next_om;
  }
  return 0;
}

#endif

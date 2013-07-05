/*
 * Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/resourceArea.hpp"
#include "oops/markOop.hpp"
#include "oops/oop.inline.hpp"
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
#include "utilities/dtrace.hpp"
#include "utilities/events.hpp"
#include "utilities/preserveException.hpp"
#ifdef TARGET_OS_FAMILY_linux
# include "os_linux.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_solaris
# include "os_solaris.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_windows
# include "os_windows.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_bsd
# include "os_bsd.inline.hpp"
#endif

#if defined(__GNUC__) && !defined(PPC64)
  // Need to inhibit inlining for older versions of GCC to avoid build-time failures
  #define ATTR __attribute__((noinline))
#else
  #define ATTR
#endif

// The "core" versions of monitor enter and exit reside in this file.
// The interpreter and compilers contain specialized transliterated
// variants of the enter-exit fast-path operations.  See i486.ad fast_lock(),
// for instance.  If you make changes here, make sure to modify the
// interpreter, and both C1 and C2 fast-path inline locking code emission.
//
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

#ifndef USDT2
HS_DTRACE_PROBE_DECL5(hotspot, monitor__wait,
  jlong, uintptr_t, char*, int, long);
HS_DTRACE_PROBE_DECL4(hotspot, monitor__waited,
  jlong, uintptr_t, char*, int);

#define DTRACE_MONITOR_WAIT_PROBE(monitor, obj, thread, millis)            \
  {                                                                        \
    if (DTraceMonitorProbes) {                                            \
      DTRACE_MONITOR_PROBE_COMMON(obj, thread);                            \
      HS_DTRACE_PROBE5(hotspot, monitor__wait, jtid,                       \
                       (monitor), bytes, len, (millis));                   \
    }                                                                      \
  }

#define DTRACE_MONITOR_PROBE(probe, monitor, obj, thread)                  \
  {                                                                        \
    if (DTraceMonitorProbes) {                                            \
      DTRACE_MONITOR_PROBE_COMMON(obj, thread);                            \
      HS_DTRACE_PROBE4(hotspot, monitor__##probe, jtid,                    \
                       (uintptr_t)(monitor), bytes, len);                  \
    }                                                                      \
  }

#else /* USDT2 */

#define DTRACE_MONITOR_WAIT_PROBE(monitor, obj, thread, millis)            \
  {                                                                        \
    if (DTraceMonitorProbes) {                                            \
      DTRACE_MONITOR_PROBE_COMMON(obj, thread);                            \
      HOTSPOT_MONITOR_WAIT(jtid,                                           \
                           (uintptr_t)(monitor), bytes, len, (millis));  \
    }                                                                      \
  }

#define HOTSPOT_MONITOR_PROBE_waited HOTSPOT_MONITOR_PROBE_WAITED

#define DTRACE_MONITOR_PROBE(probe, monitor, obj, thread)                  \
  {                                                                        \
    if (DTraceMonitorProbes) {                                            \
      DTRACE_MONITOR_PROBE_COMMON(obj, thread);                            \
      HOTSPOT_MONITOR_PROBE_##probe(jtid, /* probe = waited */             \
                       (uintptr_t)(monitor), bytes, len);                  \
    }                                                                      \
  }

#endif /* USDT2 */
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
static volatile intptr_t InflationLocks [NINFLATIONLOCKS] ;

ObjectMonitor * ObjectSynchronizer::gBlockList = NULL ;
ObjectMonitor * volatile ObjectSynchronizer::gFreeList  = NULL ;
ObjectMonitor * volatile ObjectSynchronizer::gOmInUseList  = NULL ;
int ObjectSynchronizer::gOmInUseCount = 0;
static volatile intptr_t ListLock = 0 ;      // protects global monitor free-list cache
static volatile int MonitorFreeCount  = 0 ;      // # on gFreeList
static volatile int MonitorPopulation = 0 ;      // # Extant -- in circulation
#define CHAINMARKER ((oop)-1)

// -----------------------------------------------------------------------------
//  Fast Monitor Enter/Exit
// This the fast monitor enter. The interpreter and compiler use
// some assembly copies of this code. Make sure update those code
// if the following function is changed. The implementation is
// extremely sensitive to race condition. Be careful.

void ObjectSynchronizer::fast_enter(Handle obj, BasicLock* lock, bool attempt_rebias, TRAPS) {
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

 slow_enter (obj, lock, THREAD) ;
}

void ObjectSynchronizer::fast_exit(oop object, BasicLock* lock, TRAPS) {
  assert(!object->mark()->has_bias_pattern(), "should not see bias pattern here");
  // if displaced header is null, the previous enter is recursive enter, no-op
  markOop dhw = lock->displaced_header();
  markOop mark ;
  if (dhw == NULL) {
     // Recursive stack-lock.
     // Diagnostics -- Could be: stack-locked, inflating, inflated.
     mark = object->mark() ;
     assert (!mark->is_neutral(), "invariant") ;
     if (mark->has_locker() && mark != markOopDesc::INFLATING()) {
        assert(THREAD->is_lock_owned((address)mark->locker()), "invariant") ;
     }
     if (mark->has_monitor()) {
        ObjectMonitor * m = mark->monitor() ;
        assert(((oop)(m->object()))->mark() == mark, "invariant") ;
        assert(m->is_entered(THREAD), "invariant") ;
     }
     return ;
  }

  mark = object->mark() ;

  // If the object is stack-locked by the current thread, try to
  // swing the displaced header from the box back to the mark.
  if (mark == (markOop) lock) {
     assert (dhw->is_neutral(), "invariant") ;
     if ((markOop) Atomic::cmpxchg_ptr (dhw, object->mark_addr(), mark) == mark) {
        TEVENT (fast_exit: release stacklock) ;
        return;
     }
  }

  ObjectSynchronizer::inflate(THREAD, object)->exit (true, THREAD) ;
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
      TEVENT (slow_enter: release stacklock) ;
      return ;
    }
    // Fall through to inflate() ...
  } else
  if (mark->has_locker() && THREAD->is_lock_owned((address)mark->locker())) {
    assert(lock != mark->locker(), "must not re-lock the same lock");
    assert(lock != (BasicLock*)obj->mark(), "don't relock with same BasicLock");
    lock->set_displaced_header(NULL);
    return;
  }

#if 0
  // The following optimization isn't particularly useful.
  if (mark->has_monitor() && mark->monitor()->is_entered(THREAD)) {
    lock->set_displaced_header (NULL) ;
    return ;
  }
#endif

  // The object header will never be displaced to this lock,
  // so it does not matter what the value is, except that it
  // must be non-zero to avoid looking like a re-entrant lock,
  // and must not look locked either.
  lock->set_displaced_header(markOopDesc::unused_mark());
  ObjectSynchronizer::inflate(THREAD, obj())->enter(THREAD);
}

// This routine is used to handle interpreter/compiler slow case
// We don't need to use fast path here, because it must have
// failed in the interpreter/compiler code. Simply use the heavy
// weight monitor should be ok, unless someone find otherwise.
void ObjectSynchronizer::slow_exit(oop object, BasicLock* lock, TRAPS) {
  fast_exit (object, lock, THREAD) ;
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
  TEVENT (complete_exit) ;
  if (UseBiasedLocking) {
    BiasedLocking::revoke_and_rebias(obj, false, THREAD);
    assert(!obj->mark()->has_bias_pattern(), "biases should be revoked by now");
  }

  ObjectMonitor* monitor = ObjectSynchronizer::inflate(THREAD, obj());

  return monitor->complete_exit(THREAD);
}

// NOTE: must use heavy weight monitor to handle complete_exit/reenter()
void ObjectSynchronizer::reenter(Handle obj, intptr_t recursion, TRAPS) {
  TEVENT (reenter) ;
  if (UseBiasedLocking) {
    BiasedLocking::revoke_and_rebias(obj, false, THREAD);
    assert(!obj->mark()->has_bias_pattern(), "biases should be revoked by now");
  }

  ObjectMonitor* monitor = ObjectSynchronizer::inflate(THREAD, obj());

  monitor->reenter(recursion, THREAD);
}
// -----------------------------------------------------------------------------
// JNI locks on java objects
// NOTE: must use heavy weight monitor to handle jni monitor enter
void ObjectSynchronizer::jni_enter(Handle obj, TRAPS) { // possible entry from jni enter
  // the current locking is from JNI instead of Java code
  TEVENT (jni_enter) ;
  if (UseBiasedLocking) {
    BiasedLocking::revoke_and_rebias(obj, false, THREAD);
    assert(!obj->mark()->has_bias_pattern(), "biases should be revoked by now");
  }
  THREAD->set_current_pending_monitor_is_from_java(false);
  ObjectSynchronizer::inflate(THREAD, obj())->enter(THREAD);
  THREAD->set_current_pending_monitor_is_from_java(true);
}

// NOTE: must use heavy weight monitor to handle jni monitor enter
bool ObjectSynchronizer::jni_try_enter(Handle obj, Thread* THREAD) {
  if (UseBiasedLocking) {
    BiasedLocking::revoke_and_rebias(obj, false, THREAD);
    assert(!obj->mark()->has_bias_pattern(), "biases should be revoked by now");
  }

  ObjectMonitor* monitor = ObjectSynchronizer::inflate_helper(obj());
  return monitor->try_enter(THREAD);
}


// NOTE: must use heavy weight monitor to handle jni monitor exit
void ObjectSynchronizer::jni_exit(oop obj, Thread* THREAD) {
  TEVENT (jni_exit) ;
  if (UseBiasedLocking) {
    Handle h_obj(THREAD, obj);
    BiasedLocking::revoke_and_rebias(h_obj, false, THREAD);
    obj = h_obj();
  }
  assert(!obj->mark()->has_bias_pattern(), "biases should be revoked by now");

  ObjectMonitor* monitor = ObjectSynchronizer::inflate(THREAD, obj);
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
    TEVENT (ObjectLocker) ;

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
void ObjectSynchronizer::wait(Handle obj, jlong millis, TRAPS) {
  if (UseBiasedLocking) {
    BiasedLocking::revoke_and_rebias(obj, false, THREAD);
    assert(!obj->mark()->has_bias_pattern(), "biases should be revoked by now");
  }
  if (millis < 0) {
    TEVENT (wait - throw IAX) ;
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(), "timeout value is negative");
  }
  ObjectMonitor* monitor = ObjectSynchronizer::inflate(THREAD, obj());
  DTRACE_MONITOR_WAIT_PROBE(monitor, obj(), THREAD, millis);
  monitor->wait(millis, true, THREAD);

  /* This dummy call is in place to get around dtrace bug 6254741.  Once
     that's fixed we can uncomment the following line and remove the call */
  // DTRACE_MONITOR_PROBE(waited, monitor, obj(), THREAD);
  dtrace_waited_probe(monitor, obj, THREAD);
}

void ObjectSynchronizer::waitUninterruptibly (Handle obj, jlong millis, TRAPS) {
  if (UseBiasedLocking) {
    BiasedLocking::revoke_and_rebias(obj, false, THREAD);
    assert(!obj->mark()->has_bias_pattern(), "biases should be revoked by now");
  }
  if (millis < 0) {
    TEVENT (wait - throw IAX) ;
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(), "timeout value is negative");
  }
  ObjectSynchronizer::inflate(THREAD, obj()) -> wait(millis, false, THREAD) ;
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
  ObjectSynchronizer::inflate(THREAD, obj())->notify(THREAD);
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
  ObjectSynchronizer::inflate(THREAD, obj())->notifyAll(THREAD);
}

// -----------------------------------------------------------------------------
// Hash Code handling
//
// Performance concern:
// OrderAccess::storestore() calls release() which STs 0 into the global volatile
// OrderAccess::Dummy variable.  This store is unnecessary for correctness.
// Many threads STing into a common location causes considerable cache migration
// or "sloshing" on large SMP system.  As such, I avoid using OrderAccess::storestore()
// until it's repaired.  In some cases OrderAccess::fence() -- which incurs local
// latency on the executing processor -- is a better choice as it scales on SMP
// systems.  See http://blogs.sun.com/dave/entry/biased_locking_in_hotspot for a
// discussion of coherency costs.  Note that all our current reference platforms
// provide strong ST-ST order, so the issue is moot on IA32, x64, and SPARC.
//
// As a general policy we use "volatile" to control compiler-based reordering
// and explicit fences (barriers) to control for architectural reordering performed
// by the CPU(s) or platform.

struct SharedGlobals {
    // These are highly shared mostly-read variables.
    // To avoid false-sharing they need to be the sole occupants of a $ line.
    double padPrefix [8];
    volatile int stwRandom ;
    volatile int stwCycle ;

    // Hot RW variables -- Sequester to avoid false-sharing
    double padSuffix [16];
    volatile int hcSequence ;
    double padFinal [8] ;
} ;

static SharedGlobals GVars ;
static int MonitorScavengeThreshold = 1000000 ;
static volatile int ForceMonitorScavenge = 0 ; // Scavenge required and pending

static markOop ReadStableMark (oop obj) {
  markOop mark = obj->mark() ;
  if (!mark->is_being_inflated()) {
    return mark ;       // normal fast-path return
  }

  int its = 0 ;
  for (;;) {
    markOop mark = obj->mark() ;
    if (!mark->is_being_inflated()) {
      return mark ;    // normal fast-path return
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

    ++its ;
    if (its > 10000 || !os::is_MP()) {
       if (its & 1) {
         os::NakedYield() ;
         TEVENT (Inflate: INFLATING - yield) ;
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
         int ix = (intptr_t(obj) >> 5) & (NINFLATIONLOCKS-1) ;
         int YieldThenBlock = 0 ;
         assert (ix >= 0 && ix < NINFLATIONLOCKS, "invariant") ;
         assert ((NINFLATIONLOCKS & (NINFLATIONLOCKS-1)) == 0, "invariant") ;
         Thread::muxAcquire (InflationLocks + ix, "InflationLock") ;
         while (obj->mark() == markOopDesc::INFLATING()) {
           // Beware: NakedYield() is advisory and has almost no effect on some platforms
           // so we periodically call Self->_ParkEvent->park(1).
           // We use a mixed spin/yield/block mechanism.
           if ((YieldThenBlock++) >= 16) {
              Thread::current()->_ParkEvent->park(1) ;
           } else {
              os::NakedYield() ;
           }
         }
         Thread::muxRelease (InflationLocks + ix ) ;
         TEVENT (Inflate: INFLATING - yield/park) ;
       }
    } else {
       SpinPause() ;       // SMP-polite spinning
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
//

static inline intptr_t get_next_hash(Thread * Self, oop obj) {
  intptr_t value = 0 ;
  if (hashCode == 0) {
     // This form uses an unguarded global Park-Miller RNG,
     // so it's possible for two threads to race and generate the same RNG.
     // On MP system we'll have lots of RW access to a global, so the
     // mechanism induces lots of coherency traffic.
     value = os::random() ;
  } else
  if (hashCode == 1) {
     // This variation has the property of being stable (idempotent)
     // between STW operations.  This can be useful in some of the 1-0
     // synchronization schemes.
     intptr_t addrBits = intptr_t(obj) >> 3 ;
     value = addrBits ^ (addrBits >> 5) ^ GVars.stwRandom ;
  } else
  if (hashCode == 2) {
     value = 1 ;            // for sensitivity testing
  } else
  if (hashCode == 3) {
     value = ++GVars.hcSequence ;
  } else
  if (hashCode == 4) {
     value = intptr_t(obj) ;
  } else {
     // Marsaglia's xor-shift scheme with thread-specific state
     // This is probably the best overall implementation -- we'll
     // likely make this the default in future releases.
     unsigned t = Self->_hashStateX ;
     t ^= (t << 11) ;
     Self->_hashStateX = Self->_hashStateY ;
     Self->_hashStateY = Self->_hashStateZ ;
     Self->_hashStateZ = Self->_hashStateW ;
     unsigned v = Self->_hashStateW ;
     v = (v ^ (v >> 19)) ^ (t ^ (t >> 8)) ;
     Self->_hashStateW = v ;
     value = v ;
  }

  value &= markOopDesc::hash_mask;
  if (value == 0) value = 0xBAD ;
  assert (value != markOopDesc::no_hash, "invariant") ;
  TEVENT (hashCode: GENERATE) ;
  return value;
}
//
intptr_t ObjectSynchronizer::FastHashCode (Thread * Self, oop obj) {
  if (UseBiasedLocking) {
    // NOTE: many places throughout the JVM do not expect a safepoint
    // to be taken here, in particular most operations on perm gen
    // objects. However, we only ever bias Java instances and all of
    // the call sites of identity_hash that might revoke biases have
    // been checked to make sure they can handle a safepoint. The
    // added check of the bias pattern is to avoid useless calls to
    // thread-local storage.
    if (obj->mark()->has_bias_pattern()) {
      // Box and unbox the raw reference just in case we cause a STW safepoint.
      Handle hobj (Self, obj) ;
      // Relaxing assertion for bug 6320749.
      assert (Universe::verify_in_progress() ||
              !SafepointSynchronize::is_at_safepoint(),
             "biases should not be seen by VM thread here");
      BiasedLocking::revoke_and_rebias(hobj, false, JavaThread::current());
      obj = hobj() ;
      assert(!obj->mark()->has_bias_pattern(), "biases should be revoked by now");
    }
  }

  // hashCode() is a heap mutator ...
  // Relaxing assertion for bug 6320749.
  assert (Universe::verify_in_progress() ||
          !SafepointSynchronize::is_at_safepoint(), "invariant") ;
  assert (Universe::verify_in_progress() ||
          Self->is_Java_thread() , "invariant") ;
  assert (Universe::verify_in_progress() ||
         ((JavaThread *)Self)->thread_state() != _thread_blocked, "invariant") ;

  ObjectMonitor* monitor = NULL;
  markOop temp, test;
  intptr_t hash;
  markOop mark = ReadStableMark (obj);

  // object should remain ineligible for biased locking
  assert (!mark->has_bias_pattern(), "invariant") ;

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
    assert (temp->is_neutral(), "invariant") ;
    hash = temp->hash();
    if (hash) {
      return hash;
    }
    // Skip to the following code to reduce code size
  } else if (Self->is_lock_owned((address)mark->locker())) {
    temp = mark->displaced_mark_helper(); // this is a lightweight monitor owned
    assert (temp->is_neutral(), "invariant") ;
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
  monitor = ObjectSynchronizer::inflate(Self, obj);
  // Load displaced header and check it has hash code
  mark = monitor->header();
  assert (mark->is_neutral(), "invariant") ;
  hash = mark->hash();
  if (hash == 0) {
    hash = get_next_hash(Self, obj);
    temp = mark->copy_set_hash(hash); // merge hash code into header
    assert (temp->is_neutral(), "invariant") ;
    test = (markOop) Atomic::cmpxchg_ptr(temp, monitor, mark);
    if (test != mark) {
      // The only update to the header in the monitor (outside GC)
      // is install the hash code. If someone add new usage of
      // displaced header, please update this code
      hash = test->hash();
      assert (test->is_neutral(), "invariant") ;
      assert (hash != 0, "Trivial unexpected object/monitor header usage.");
    }
  }
  // We finally get the hash
  return hash;
}

// Deprecated -- use FastHashCode() instead.

intptr_t ObjectSynchronizer::identity_hash_value_for(Handle obj) {
  return FastHashCode (Thread::current(), obj()) ;
}


bool ObjectSynchronizer::current_thread_holds_lock(JavaThread* thread,
                                                   Handle h_obj) {
  if (UseBiasedLocking) {
    BiasedLocking::revoke_and_rebias(h_obj, false, thread);
    assert(!h_obj->mark()->has_bias_pattern(), "biases should be revoked by now");
  }

  assert(thread == JavaThread::current(), "Can only be called on current thread");
  oop obj = h_obj();

  markOop mark = ReadStableMark (obj) ;

  // Uncontended case, header points to stack
  if (mark->has_locker()) {
    return thread->is_lock_owned((address)mark->locker());
  }
  // Contended case, header points to ObjectMonitor (tagged pointer)
  if (mark->has_monitor()) {
    ObjectMonitor* monitor = mark->monitor();
    return monitor->is_entered(thread) != 0 ;
  }
  // Unlocked case, header in place
  assert(mark->is_neutral(), "sanity check");
  return false;
}

// Be aware of this method could revoke bias of the lock object.
// This method querys the ownership of the lock handle specified by 'h_obj'.
// If the current thread owns the lock, it returns owner_self. If no
// thread owns the lock, it returns owner_none. Otherwise, it will return
// ower_other.
ObjectSynchronizer::LockOwnership ObjectSynchronizer::query_lock_ownership
(JavaThread *self, Handle h_obj) {
  // The caller must beware this method can revoke bias, and
  // revocation can result in a safepoint.
  assert (!SafepointSynchronize::is_at_safepoint(), "invariant") ;
  assert (self->thread_state() != _thread_blocked , "invariant") ;

  // Possible mark states: neutral, biased, stack-locked, inflated

  if (UseBiasedLocking && h_obj()->mark()->has_bias_pattern()) {
    // CASE: biased
    BiasedLocking::revoke_and_rebias(h_obj, false, self);
    assert(!h_obj->mark()->has_bias_pattern(),
           "biases should be revoked by now");
  }

  assert(self == JavaThread::current(), "Can only be called on current thread");
  oop obj = h_obj();
  markOop mark = ReadStableMark (obj) ;

  // CASE: stack-locked.  Mark points to a BasicLock on the owner's stack.
  if (mark->has_locker()) {
    return self->is_lock_owned((address)mark->locker()) ?
      owner_self : owner_other;
  }

  // CASE: inflated. Mark (tagged pointer) points to an objectMonitor.
  // The Object:ObjectMonitor relationship is stable as long as we're
  // not at a safepoint.
  if (mark->has_monitor()) {
    void * owner = mark->monitor()->_owner ;
    if (owner == NULL) return owner_none ;
    return (owner == self ||
            self->is_lock_owned((address)owner)) ? owner_self : owner_other;
  }

  // CASE: neutral
  assert(mark->is_neutral(), "sanity check");
  return owner_none ;           // it's unlocked
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

  markOop mark = ReadStableMark (obj) ;

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
  ObjectMonitor* block = gBlockList;
  ObjectMonitor* mid;
  while (block) {
    assert(block->object() == CHAINMARKER, "must be a block header");
    for (int i = _BLOCKSIZE - 1; i > 0; i--) {
      mid = block + i;
      oop object = (oop) mid->object();
      if (object != NULL) {
        closure->do_monitor(mid);
      }
    }
    block = (ObjectMonitor*) block->FreeNext;
  }
}

// Get the next block in the block list.
static inline ObjectMonitor* next(ObjectMonitor* block) {
  assert(block->object() == CHAINMARKER, "must be a block header");
  block = block->FreeNext ;
  assert(block == NULL || block->object() == CHAINMARKER, "must be a block header");
  return block;
}


void ObjectSynchronizer::oops_do(OopClosure* f) {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at safepoint");
  for (ObjectMonitor* block = gBlockList; block != NULL; block = next(block)) {
    assert(block->object() == CHAINMARKER, "must be a block header");
    for (int i = 1; i < _BLOCKSIZE; i++) {
      ObjectMonitor* mid = &block[i];
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
// The global list is protected by ListLock.  All the critical sections
// are short and operate in constant-time.
//
// ObjectMonitors reside in type-stable memory (TSM) and are immortal.
//
// Lifecycle:
// --   unassigned and on the global free list
// --   unassigned and on a thread's private omFreeList
// --   assigned to an object.  The object is inflated and the mark refers
//      to the objectmonitor.
//


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
//

static void InduceScavenge (Thread * Self, const char * Whence) {
  // Induce STW safepoint to trim monitors
  // Ultimately, this results in a call to deflate_idle_monitors() in the near future.
  // More precisely, trigger an asynchronous STW safepoint as the number
  // of active monitors passes the specified threshold.
  // TODO: assert thread state is reasonable

  if (ForceMonitorScavenge == 0 && Atomic::xchg (1, &ForceMonitorScavenge) == 0) {
    if (ObjectMonitor::Knob_Verbose) {
      ::printf ("Monitor scavenge - Induced STW @%s (%d)\n", Whence, ForceMonitorScavenge) ;
      ::fflush(stdout) ;
    }
    // Induce a 'null' safepoint to scavenge monitors
    // Must VM_Operation instance be heap allocated as the op will be enqueue and posted
    // to the VMthread and have a lifespan longer than that of this activation record.
    // The VMThread will delete the op when completed.
    VMThread::execute (new VM_ForceAsyncSafepoint()) ;

    if (ObjectMonitor::Knob_Verbose) {
      ::printf ("Monitor scavenge - STW posted @%s (%d)\n", Whence, ForceMonitorScavenge) ;
      ::fflush(stdout) ;
    }
  }
}
/* Too slow for general assert or debug
void ObjectSynchronizer::verifyInUse (Thread *Self) {
   ObjectMonitor* mid;
   int inusetally = 0;
   for (mid = Self->omInUseList; mid != NULL; mid = mid->FreeNext) {
     inusetally ++;
   }
   assert(inusetally == Self->omInUseCount, "inuse count off");

   int freetally = 0;
   for (mid = Self->omFreeList; mid != NULL; mid = mid->FreeNext) {
     freetally ++;
   }
   assert(freetally == Self->omFreeCount, "free count off");
}
*/
ObjectMonitor * ATTR ObjectSynchronizer::omAlloc (Thread * Self) {
    // A large MAXPRIVATE value reduces both list lock contention
    // and list coherency traffic, but also tends to increase the
    // number of objectMonitors in circulation as well as the STW
    // scavenge costs.  As usual, we lean toward time in space-time
    // tradeoffs.
    const int MAXPRIVATE = 1024 ;
    for (;;) {
        ObjectMonitor * m ;

        // 1: try to allocate from the thread's local omFreeList.
        // Threads will attempt to allocate first from their local list, then
        // from the global list, and only after those attempts fail will the thread
        // attempt to instantiate new monitors.   Thread-local free lists take
        // heat off the ListLock and improve allocation latency, as well as reducing
        // coherency traffic on the shared global list.
        m = Self->omFreeList ;
        if (m != NULL) {
           Self->omFreeList = m->FreeNext ;
           Self->omFreeCount -- ;
           // CONSIDER: set m->FreeNext = BAD -- diagnostic hygiene
           guarantee (m->object() == NULL, "invariant") ;
           if (MonitorInUseLists) {
             m->FreeNext = Self->omInUseList;
             Self->omInUseList = m;
             Self->omInUseCount ++;
             // verifyInUse(Self);
           } else {
             m->FreeNext = NULL;
           }
           return m ;
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
            Thread::muxAcquire (&ListLock, "omAlloc") ;
            for (int i = Self->omFreeProvision; --i >= 0 && gFreeList != NULL; ) {
                MonitorFreeCount --;
                ObjectMonitor * take = gFreeList ;
                gFreeList = take->FreeNext ;
                guarantee (take->object() == NULL, "invariant") ;
                guarantee (!take->is_busy(), "invariant") ;
                take->Recycle() ;
                omRelease (Self, take, false) ;
            }
            Thread::muxRelease (&ListLock) ;
            Self->omFreeProvision += 1 + (Self->omFreeProvision/2) ;
            if (Self->omFreeProvision > MAXPRIVATE ) Self->omFreeProvision = MAXPRIVATE ;
            TEVENT (omFirst - reprovision) ;

            const int mx = MonitorBound ;
            if (mx > 0 && (MonitorPopulation-MonitorFreeCount) > mx) {
              // We can't safely induce a STW safepoint from omAlloc() as our thread
              // state may not be appropriate for such activities and callers may hold
              // naked oops, so instead we defer the action.
              InduceScavenge (Self, "omAlloc") ;
            }
            continue;
        }

        // 3: allocate a block of new ObjectMonitors
        // Both the local and global free lists are empty -- resort to malloc().
        // In the current implementation objectMonitors are TSM - immortal.
        assert (_BLOCKSIZE > 1, "invariant") ;
        ObjectMonitor * temp = new ObjectMonitor[_BLOCKSIZE];

        // NOTE: (almost) no way to recover if allocation failed.
        // We might be able to induce a STW safepoint and scavenge enough
        // objectMonitors to permit progress.
        if (temp == NULL) {
            vm_exit_out_of_memory (sizeof (ObjectMonitor[_BLOCKSIZE]), OOM_MALLOC_ERROR,
                                   "Allocate ObjectMonitors");
        }

        // Format the block.
        // initialize the linked list, each monitor points to its next
        // forming the single linked free list, the very first monitor
        // will points to next block, which forms the block list.
        // The trick of using the 1st element in the block as gBlockList
        // linkage should be reconsidered.  A better implementation would
        // look like: class Block { Block * next; int N; ObjectMonitor Body [N] ; }

        for (int i = 1; i < _BLOCKSIZE ; i++) {
           temp[i].FreeNext = &temp[i+1];
        }

        // terminate the last monitor as the end of list
        temp[_BLOCKSIZE - 1].FreeNext = NULL ;

        // Element [0] is reserved for global list linkage
        temp[0].set_object(CHAINMARKER);

        // Consider carving out this thread's current request from the
        // block in hand.  This avoids some lock traffic and redundant
        // list activity.

        // Acquire the ListLock to manipulate BlockList and FreeList.
        // An Oyama-Taura-Yonezawa scheme might be more efficient.
        Thread::muxAcquire (&ListLock, "omAlloc [2]") ;
        MonitorPopulation += _BLOCKSIZE-1;
        MonitorFreeCount += _BLOCKSIZE-1;

        // Add the new block to the list of extant blocks (gBlockList).
        // The very first objectMonitor in a block is reserved and dedicated.
        // It serves as blocklist "next" linkage.
        temp[0].FreeNext = gBlockList;
        gBlockList = temp;

        // Add the new string of objectMonitors to the global free list
        temp[_BLOCKSIZE - 1].FreeNext = gFreeList ;
        gFreeList = temp + 1;
        Thread::muxRelease (&ListLock) ;
        TEVENT (Allocate block of monitors) ;
    }
}

// Place "m" on the caller's private per-thread omFreeList.
// In practice there's no need to clamp or limit the number of
// monitors on a thread's omFreeList as the only time we'll call
// omRelease is to return a monitor to the free list after a CAS
// attempt failed.  This doesn't allow unbounded #s of monitors to
// accumulate on a thread's free list.
//

void ObjectSynchronizer::omRelease (Thread * Self, ObjectMonitor * m, bool fromPerThreadAlloc) {
    guarantee (m->object() == NULL, "invariant") ;

    // Remove from omInUseList
    if (MonitorInUseLists && fromPerThreadAlloc) {
      ObjectMonitor* curmidinuse = NULL;
      for (ObjectMonitor* mid = Self->omInUseList; mid != NULL; ) {
       if (m == mid) {
         // extract from per-thread in-use-list
         if (mid == Self->omInUseList) {
           Self->omInUseList = mid->FreeNext;
         } else if (curmidinuse != NULL) {
           curmidinuse->FreeNext = mid->FreeNext; // maintain the current thread inuselist
         }
         Self->omInUseCount --;
         // verifyInUse(Self);
         break;
       } else {
         curmidinuse = mid;
         mid = mid->FreeNext;
      }
    }
  }

  // FreeNext is used for both onInUseList and omFreeList, so clear old before setting new
  m->FreeNext = Self->omFreeList ;
  Self->omFreeList = m ;
  Self->omFreeCount ++ ;
}

// Return the monitors of a moribund thread's local free list to
// the global free list.  Typically a thread calls omFlush() when
// it's dying.  We could also consider having the VM thread steal
// monitors from threads that have not run java code over a few
// consecutive STW safepoints.  Relatedly, we might decay
// omFreeProvision at STW safepoints.
//
// Also return the monitors of a moribund thread"s omInUseList to
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

void ObjectSynchronizer::omFlush (Thread * Self) {
    ObjectMonitor * List = Self->omFreeList ;  // Null-terminated SLL
    Self->omFreeList = NULL ;
    ObjectMonitor * Tail = NULL ;
    int Tally = 0;
    if (List != NULL) {
      ObjectMonitor * s ;
      for (s = List ; s != NULL ; s = s->FreeNext) {
          Tally ++ ;
          Tail = s ;
          guarantee (s->object() == NULL, "invariant") ;
          guarantee (!s->is_busy(), "invariant") ;
          s->set_owner (NULL) ;   // redundant but good hygiene
          TEVENT (omFlush - Move one) ;
      }
      guarantee (Tail != NULL && List != NULL, "invariant") ;
    }

    ObjectMonitor * InUseList = Self->omInUseList;
    ObjectMonitor * InUseTail = NULL ;
    int InUseTally = 0;
    if (InUseList != NULL) {
      Self->omInUseList = NULL;
      ObjectMonitor *curom;
      for (curom = InUseList; curom != NULL; curom = curom->FreeNext) {
        InUseTail = curom;
        InUseTally++;
      }
// TODO debug
      assert(Self->omInUseCount == InUseTally, "inuse count off");
      Self->omInUseCount = 0;
      guarantee (InUseTail != NULL && InUseList != NULL, "invariant");
    }

    Thread::muxAcquire (&ListLock, "omFlush") ;
    if (Tail != NULL) {
      Tail->FreeNext = gFreeList ;
      gFreeList = List ;
      MonitorFreeCount += Tally;
    }

    if (InUseTail != NULL) {
      InUseTail->FreeNext = gOmInUseList;
      gOmInUseList = InUseList;
      gOmInUseCount += InUseTally;
    }

    Thread::muxRelease (&ListLock) ;
    TEVENT (omFlush) ;
}

// Fast path code shared by multiple functions
ObjectMonitor* ObjectSynchronizer::inflate_helper(oop obj) {
  markOop mark = obj->mark();
  if (mark->has_monitor()) {
    assert(ObjectSynchronizer::verify_objmon_isinpool(mark->monitor()), "monitor is invalid");
    assert(mark->monitor()->header()->is_neutral(), "monitor must record a good object header");
    return mark->monitor();
  }
  return ObjectSynchronizer::inflate(Thread::current(), obj);
}


// Note that we could encounter some performance loss through false-sharing as
// multiple locks occupy the same $ line.  Padding might be appropriate.


ObjectMonitor * ATTR ObjectSynchronizer::inflate (Thread * Self, oop object) {
  // Inflate mutates the heap ...
  // Relaxing assertion for bug 6320749.
  assert (Universe::verify_in_progress() ||
          !SafepointSynchronize::is_at_safepoint(), "invariant") ;

  for (;;) {
      const markOop mark = object->mark() ;
      assert (!mark->has_bias_pattern(), "invariant") ;

      // The mark can be in one of the following states:
      // *  Inflated     - just return
      // *  Stack-locked - coerce it to inflated
      // *  INFLATING    - busy wait for conversion to complete
      // *  Neutral      - aggressively inflate the object.
      // *  BIASED       - Illegal.  We should never see this

      // CASE: inflated
      if (mark->has_monitor()) {
          ObjectMonitor * inf = mark->monitor() ;
          assert (inf->header()->is_neutral(), "invariant");
          assert (inf->object() == object, "invariant") ;
          assert (ObjectSynchronizer::verify_objmon_isinpool(inf), "monitor is invalid");
          return inf ;
      }

      // CASE: inflation in progress - inflating over a stack-lock.
      // Some other thread is converting from stack-locked to inflated.
      // Only that thread can complete inflation -- other threads must wait.
      // The INFLATING value is transient.
      // Currently, we spin/yield/park and poll the markword, waiting for inflation to finish.
      // We could always eliminate polling by parking the thread on some auxiliary list.
      if (mark == markOopDesc::INFLATING()) {
         TEVENT (Inflate: spin while INFLATING) ;
         ReadStableMark(object) ;
         continue ;
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
          ObjectMonitor * m = omAlloc (Self) ;
          // Optimistically prepare the objectmonitor - anticipate successful CAS
          // We do this before the CAS in order to minimize the length of time
          // in which INFLATING appears in the mark.
          m->Recycle();
          m->_Responsible  = NULL ;
          m->OwnerIsThread = 0 ;
          m->_recursions   = 0 ;
          m->_SpinDuration = ObjectMonitor::Knob_SpinLimit ;   // Consider: maintain by type/class

          markOop cmp = (markOop) Atomic::cmpxchg_ptr (markOopDesc::INFLATING(), object->mark_addr(), mark) ;
          if (cmp != mark) {
             omRelease (Self, m, true) ;
             continue ;       // Interference -- just retry
          }

          // We've successfully installed INFLATING (0) into the mark-word.
          // This is the only case where 0 will appear in a mark-work.
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
          markOop dmw = mark->displaced_mark_helper() ;
          assert (dmw->is_neutral(), "invariant") ;

          // Setup monitor fields to proper values -- prepare the monitor
          m->set_header(dmw) ;

          // Optimization: if the mark->locker stack address is associated
          // with this thread we could simply set m->_owner = Self and
          // m->OwnerIsThread = 1. Note that a thread can inflate an object
          // that it has stack-locked -- as might happen in wait() -- directly
          // with CAS.  That is, we can avoid the xchg-NULL .... ST idiom.
          m->set_owner(mark->locker());
          m->set_object(object);
          // TODO-FIXME: assert BasicLock->dhw != 0.

          // Must preserve store ordering. The monitor state must
          // be stable at the time of publishing the monitor address.
          guarantee (object->mark() == markOopDesc::INFLATING(), "invariant") ;
          object->release_set_mark(markOopDesc::encode(m));

          // Hopefully the performance counters are allocated on distinct cache lines
          // to avoid false sharing on MP systems ...
          if (ObjectMonitor::_sync_Inflations != NULL) ObjectMonitor::_sync_Inflations->inc() ;
          TEVENT(Inflate: overwrite stacklock) ;
          if (TraceMonitorInflation) {
            if (object->is_instance()) {
              ResourceMark rm;
              tty->print_cr("Inflating object " INTPTR_FORMAT " , mark " INTPTR_FORMAT " , type %s",
                (intptr_t) object, (intptr_t) object->mark(),
                object->klass()->external_name());
            }
          }
          return m ;
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

      assert (mark->is_neutral(), "invariant");
      ObjectMonitor * m = omAlloc (Self) ;
      // prepare m for installation - set monitor to initial state
      m->Recycle();
      m->set_header(mark);
      m->set_owner(NULL);
      m->set_object(object);
      m->OwnerIsThread = 1 ;
      m->_recursions   = 0 ;
      m->_Responsible  = NULL ;
      m->_SpinDuration = ObjectMonitor::Knob_SpinLimit ;       // consider: keep metastats by type/class

      if (Atomic::cmpxchg_ptr (markOopDesc::encode(m), object->mark_addr(), mark) != mark) {
          m->set_object (NULL) ;
          m->set_owner  (NULL) ;
          m->OwnerIsThread = 0 ;
          m->Recycle() ;
          omRelease (Self, m, true) ;
          m = NULL ;
          continue ;
          // interference - the markword changed - just retry.
          // The state-transitions are one-way, so there's no chance of
          // live-lock -- "Inflated" is an absorbing state.
      }

      // Hopefully the performance counters are allocated on distinct
      // cache lines to avoid false sharing on MP systems ...
      if (ObjectMonitor::_sync_Inflations != NULL) ObjectMonitor::_sync_Inflations->inc() ;
      TEVENT(Inflate: overwrite neutral) ;
      if (TraceMonitorInflation) {
        if (object->is_instance()) {
          ResourceMark rm;
          tty->print_cr("Inflating object " INTPTR_FORMAT " , mark " INTPTR_FORMAT " , type %s",
            (intptr_t) object, (intptr_t) object->mark(),
            object->klass()->external_name());
        }
      }
      return m ;
  }
}

// Note that we could encounter some performance loss through false-sharing as
// multiple locks occupy the same $ line.  Padding might be appropriate.


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
// only scans the per-thread inuse lists. omAlloc() puts all
// assigned monitors on the per-thread list. deflate_idle_monitors()
// returns the non-busy monitors to the global free list.
// When a thread dies, omFlush() adds the list of active monitors for
// that thread to a global gOmInUseList acquiring the
// global list lock. deflate_idle_monitors() acquires the global
// list lock to scan for non-busy monitors to the global free list.
// An alternative could have used a single global inuse list. The
// downside would have been the additional cost of acquiring the global list lock
// for every omAlloc().
//
// Perversely, the heap size -- and thus the STW safepoint rate --
// typically drives the scavenge rate.  Large heaps can mean infrequent GC,
// which in turn can mean large(r) numbers of objectmonitors in circulation.
// This is an unfortunate aspect of this design.
//

enum ManifestConstants {
    ClearResponsibleAtSTW   = 0,
    MaximumRecheckInterval  = 1000
} ;

// Deflate a single monitor if not in use
// Return true if deflated, false if in use
bool ObjectSynchronizer::deflate_monitor(ObjectMonitor* mid, oop obj,
                                         ObjectMonitor** FreeHeadp, ObjectMonitor** FreeTailp) {
  bool deflated;
  // Normal case ... The monitor is associated with obj.
  guarantee (obj->mark() == markOopDesc::encode(mid), "invariant") ;
  guarantee (mid == obj->mark()->monitor(), "invariant");
  guarantee (mid->header()->is_neutral(), "invariant");

  if (mid->is_busy()) {
     if (ClearResponsibleAtSTW) mid->_Responsible = NULL ;
     deflated = false;
  } else {
     // Deflate the monitor if it is no longer being used
     // It's idle - scavenge and return to the global free list
     // plain old deflation ...
     TEVENT (deflate_idle_monitors - scavenge1) ;
     if (TraceMonitorInflation) {
       if (obj->is_instance()) {
         ResourceMark rm;
           tty->print_cr("Deflating object " INTPTR_FORMAT " , mark " INTPTR_FORMAT " , type %s",
                (intptr_t) obj, (intptr_t) obj->mark(), obj->klass()->external_name());
       }
     }

     // Restore the header back to obj
     obj->release_set_mark(mid->header());
     mid->clear();

     assert (mid->object() == NULL, "invariant") ;

     // Move the object to the working free list defined by FreeHead,FreeTail.
     if (*FreeHeadp == NULL) *FreeHeadp = mid;
     if (*FreeTailp != NULL) {
       ObjectMonitor * prevtail = *FreeTailp;
       assert(prevtail->FreeNext == NULL, "cleaned up deflated?"); // TODO KK
       prevtail->FreeNext = mid;
      }
     *FreeTailp = mid;
     deflated = true;
  }
  return deflated;
}

// Caller acquires ListLock
int ObjectSynchronizer::walk_monitor_list(ObjectMonitor** listheadp,
                                          ObjectMonitor** FreeHeadp, ObjectMonitor** FreeTailp) {
  ObjectMonitor* mid;
  ObjectMonitor* next;
  ObjectMonitor* curmidinuse = NULL;
  int deflatedcount = 0;

  for (mid = *listheadp; mid != NULL; ) {
     oop obj = (oop) mid->object();
     bool deflated = false;
     if (obj != NULL) {
       deflated = deflate_monitor(mid, obj, FreeHeadp, FreeTailp);
     }
     if (deflated) {
       // extract from per-thread in-use-list
       if (mid == *listheadp) {
         *listheadp = mid->FreeNext;
       } else if (curmidinuse != NULL) {
         curmidinuse->FreeNext = mid->FreeNext; // maintain the current thread inuselist
       }
       next = mid->FreeNext;
       mid->FreeNext = NULL;  // This mid is current tail in the FreeHead list
       mid = next;
       deflatedcount++;
     } else {
       curmidinuse = mid;
       mid = mid->FreeNext;
    }
  }
  return deflatedcount;
}

void ObjectSynchronizer::deflate_idle_monitors() {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at safepoint");
  int nInuse = 0 ;              // currently associated with objects
  int nInCirculation = 0 ;      // extant
  int nScavenged = 0 ;          // reclaimed
  bool deflated = false;

  ObjectMonitor * FreeHead = NULL ;  // Local SLL of scavenged monitors
  ObjectMonitor * FreeTail = NULL ;

  TEVENT (deflate_idle_monitors) ;
  // Prevent omFlush from changing mids in Thread dtor's during deflation
  // And in case the vm thread is acquiring a lock during a safepoint
  // See e.g. 6320749
  Thread::muxAcquire (&ListLock, "scavenge - return") ;

  if (MonitorInUseLists) {
    int inUse = 0;
    for (JavaThread* cur = Threads::first(); cur != NULL; cur = cur->next()) {
      nInCirculation+= cur->omInUseCount;
      int deflatedcount = walk_monitor_list(cur->omInUseList_addr(), &FreeHead, &FreeTail);
      cur->omInUseCount-= deflatedcount;
      // verifyInUse(cur);
      nScavenged += deflatedcount;
      nInuse += cur->omInUseCount;
     }

   // For moribund threads, scan gOmInUseList
   if (gOmInUseList) {
     nInCirculation += gOmInUseCount;
     int deflatedcount = walk_monitor_list((ObjectMonitor **)&gOmInUseList, &FreeHead, &FreeTail);
     gOmInUseCount-= deflatedcount;
     nScavenged += deflatedcount;
     nInuse += gOmInUseCount;
    }

  } else for (ObjectMonitor* block = gBlockList; block != NULL; block = next(block)) {
  // Iterate over all extant monitors - Scavenge all idle monitors.
    assert(block->object() == CHAINMARKER, "must be a block header");
    nInCirculation += _BLOCKSIZE ;
    for (int i = 1 ; i < _BLOCKSIZE; i++) {
      ObjectMonitor* mid = &block[i];
      oop obj = (oop) mid->object();

      if (obj == NULL) {
        // The monitor is not associated with an object.
        // The monitor should either be a thread-specific private
        // free list or the global free list.
        // obj == NULL IMPLIES mid->is_busy() == 0
        guarantee (!mid->is_busy(), "invariant") ;
        continue ;
      }
      deflated = deflate_monitor(mid, obj, &FreeHead, &FreeTail);

      if (deflated) {
        mid->FreeNext = NULL ;
        nScavenged ++ ;
      } else {
        nInuse ++;
      }
    }
  }

  MonitorFreeCount += nScavenged;

  // Consider: audit gFreeList to ensure that MonitorFreeCount and list agree.

  if (ObjectMonitor::Knob_Verbose) {
    ::printf ("Deflate: InCirc=%d InUse=%d Scavenged=%d ForceMonitorScavenge=%d : pop=%d free=%d\n",
        nInCirculation, nInuse, nScavenged, ForceMonitorScavenge,
        MonitorPopulation, MonitorFreeCount) ;
    ::fflush(stdout) ;
  }

  ForceMonitorScavenge = 0;    // Reset

  // Move the scavenged monitors back to the global free list.
  if (FreeHead != NULL) {
     guarantee (FreeTail != NULL && nScavenged > 0, "invariant") ;
     assert (FreeTail->FreeNext == NULL, "invariant") ;
     // constant-time list splice - prepend scavenged segment to gFreeList
     FreeTail->FreeNext = gFreeList ;
     gFreeList = FreeHead ;
  }
  Thread::muxRelease (&ListLock) ;

  if (ObjectMonitor::_sync_Deflations != NULL) ObjectMonitor::_sync_Deflations->inc(nScavenged) ;
  if (ObjectMonitor::_sync_MonExtant  != NULL) ObjectMonitor::_sync_MonExtant ->set_value(nInCirculation);

  // TODO: Add objectMonitor leak detection.
  // Audit/inventory the objectMonitors -- make sure they're all accounted for.
  GVars.stwRandom = os::random() ;
  GVars.stwCycle ++ ;
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
  No_Safepoint_Verifier nsv ;
  ReleaseJavaMonitorsClosure rjmc(THREAD);
  Thread::muxAcquire(&ListLock, "release_monitors_owned_by_thread");
  ObjectSynchronizer::monitors_iterate(&rjmc);
  Thread::muxRelease(&ListLock);
  THREAD->clear_pending_exception();
}

//------------------------------------------------------------------------------
// Non-product code

#ifndef PRODUCT

// Verify all monitors in the monitor cache, the verification is weak.
void ObjectSynchronizer::verify() {
  ObjectMonitor* block = gBlockList;
  ObjectMonitor* mid;
  while (block) {
    assert(block->object() == CHAINMARKER, "must be a block header");
    for (int i = 1; i < _BLOCKSIZE; i++) {
      mid = block + i;
      oop object = (oop) mid->object();
      if (object != NULL) {
        mid->verify();
      }
    }
    block = (ObjectMonitor*) block->FreeNext;
  }
}

// Check if monitor belongs to the monitor cache
// The list is grow-only so it's *relatively* safe to traverse
// the list of extant blocks without taking a lock.

int ObjectSynchronizer::verify_objmon_isinpool(ObjectMonitor *monitor) {
  ObjectMonitor* block = gBlockList;

  while (block) {
    assert(block->object() == CHAINMARKER, "must be a block header");
    if (monitor > &block[0] && monitor < &block[_BLOCKSIZE]) {
      address mon = (address) monitor;
      address blk = (address) block;
      size_t diff = mon - blk;
      assert((diff % sizeof(ObjectMonitor)) == 0, "check");
      return 1;
    }
    block = (ObjectMonitor*) block->FreeNext;
  }
  return 0;
}

#endif

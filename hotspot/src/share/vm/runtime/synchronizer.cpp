/*
 * Copyright 1998-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

# include "incls/_precompiled.incl"
# include "incls/_synchronizer.cpp.incl"

#if defined(__GNUC__) && !defined(IA64)
  // Need to inhibit inlining for older versions of GCC to avoid build-time failures
  #define ATTR __attribute__((noinline))
#else
  #define ATTR
#endif

// Native markword accessors for synchronization and hashCode().
//
// The "core" versions of monitor enter and exit reside in this file.
// The interpreter and compilers contain specialized transliterated
// variants of the enter-exit fast-path operations.  See i486.ad fast_lock(),
// for instance.  If you make changes here, make sure to modify the
// interpreter, and both C1 and C2 fast-path inline locking code emission.
//
// TODO: merge the objectMonitor and synchronizer classes.
//
// -----------------------------------------------------------------------------

#ifdef DTRACE_ENABLED

// Only bother with this argument setup if dtrace is available
// TODO-FIXME: probes should not fire when caller is _blocked.  assert() accordingly.

HS_DTRACE_PROBE_DECL5(hotspot, monitor__wait,
  jlong, uintptr_t, char*, int, long);
HS_DTRACE_PROBE_DECL4(hotspot, monitor__waited,
  jlong, uintptr_t, char*, int);
HS_DTRACE_PROBE_DECL4(hotspot, monitor__notify,
  jlong, uintptr_t, char*, int);
HS_DTRACE_PROBE_DECL4(hotspot, monitor__notifyAll,
  jlong, uintptr_t, char*, int);
HS_DTRACE_PROBE_DECL4(hotspot, monitor__contended__enter,
  jlong, uintptr_t, char*, int);
HS_DTRACE_PROBE_DECL4(hotspot, monitor__contended__entered,
  jlong, uintptr_t, char*, int);
HS_DTRACE_PROBE_DECL4(hotspot, monitor__contended__exit,
  jlong, uintptr_t, char*, int);

#define DTRACE_MONITOR_PROBE_COMMON(klassOop, thread)                      \
  char* bytes = NULL;                                                      \
  int len = 0;                                                             \
  jlong jtid = SharedRuntime::get_java_tid(thread);                        \
  symbolOop klassname = ((oop)(klassOop))->klass()->klass_part()->name();  \
  if (klassname != NULL) {                                                 \
    bytes = (char*)klassname->bytes();                                     \
    len = klassname->utf8_length();                                        \
  }

#define DTRACE_MONITOR_WAIT_PROBE(monitor, klassOop, thread, millis)       \
  {                                                                        \
    if (DTraceMonitorProbes) {                                            \
      DTRACE_MONITOR_PROBE_COMMON(klassOop, thread);                       \
      HS_DTRACE_PROBE5(hotspot, monitor__wait, jtid,                       \
                       (monitor), bytes, len, (millis));                   \
    }                                                                      \
  }

#define DTRACE_MONITOR_PROBE(probe, monitor, klassOop, thread)             \
  {                                                                        \
    if (DTraceMonitorProbes) {                                            \
      DTRACE_MONITOR_PROBE_COMMON(klassOop, thread);                       \
      HS_DTRACE_PROBE4(hotspot, monitor__##probe, jtid,                    \
                       (uintptr_t)(monitor), bytes, len);                  \
    }                                                                      \
  }

#else //  ndef DTRACE_ENABLED

#define DTRACE_MONITOR_WAIT_PROBE(klassOop, thread, millis, mon)    {;}
#define DTRACE_MONITOR_PROBE(probe, klassOop, thread, mon)          {;}

#endif // ndef DTRACE_ENABLED

// ObjectWaiter serves as a "proxy" or surrogate thread.
// TODO-FIXME: Eliminate ObjectWaiter and use the thread-specific
// ParkEvent instead.  Beware, however, that the JVMTI code
// knows about ObjectWaiters, so we'll have to reconcile that code.
// See next_waiter(), first_waiter(), etc.

class ObjectWaiter : public StackObj {
 public:
  enum TStates { TS_UNDEF, TS_READY, TS_RUN, TS_WAIT, TS_ENTER, TS_CXQ } ;
  enum Sorted  { PREPEND, APPEND, SORTED } ;
  ObjectWaiter * volatile _next;
  ObjectWaiter * volatile _prev;
  Thread*       _thread;
  ParkEvent *   _event;
  volatile int  _notified ;
  volatile TStates TState ;
  Sorted        _Sorted ;           // List placement disposition
  bool          _active ;           // Contention monitoring is enabled
 public:
  ObjectWaiter(Thread* thread) {
    _next     = NULL;
    _prev     = NULL;
    _notified = 0;
    TState    = TS_RUN ;
    _thread   = thread;
    _event    = thread->_ParkEvent ;
    _active   = false;
    assert (_event != NULL, "invariant") ;
  }

  void wait_reenter_begin(ObjectMonitor *mon) {
    JavaThread *jt = (JavaThread *)this->_thread;
    _active = JavaThreadBlockedOnMonitorEnterState::wait_reenter_begin(jt, mon);
  }

  void wait_reenter_end(ObjectMonitor *mon) {
    JavaThread *jt = (JavaThread *)this->_thread;
    JavaThreadBlockedOnMonitorEnterState::wait_reenter_end(jt, _active);
  }
};

enum ManifestConstants {
    ClearResponsibleAtSTW   = 0,
    MaximumRecheckInterval  = 1000
} ;


#undef TEVENT
#define TEVENT(nom) {if (SyncVerbose) FEVENT(nom); }

#define FEVENT(nom) { static volatile int ctr = 0 ; int v = ++ctr ; if ((v & (v-1)) == 0) { ::printf (#nom " : %d \n", v); ::fflush(stdout); }}

#undef  TEVENT
#define TEVENT(nom) {;}

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

static int  MBFence (int x) { OrderAccess::fence(); return x; }

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


// Tunables ...
// The knob* variables are effectively final.  Once set they should
// never be modified hence.  Consider using __read_mostly with GCC.

static int Knob_LogSpins           = 0 ;       // enable jvmstat tally for spins
static int Knob_HandOff            = 0 ;
static int Knob_Verbose            = 0 ;
static int Knob_ReportSettings     = 0 ;

static int Knob_SpinLimit          = 5000 ;    // derived by an external tool -
static int Knob_SpinBase           = 0 ;       // Floor AKA SpinMin
static int Knob_SpinBackOff        = 0 ;       // spin-loop backoff
static int Knob_CASPenalty         = -1 ;      // Penalty for failed CAS
static int Knob_OXPenalty          = -1 ;      // Penalty for observed _owner change
static int Knob_SpinSetSucc        = 1 ;       // spinners set the _succ field
static int Knob_SpinEarly          = 1 ;
static int Knob_SuccEnabled        = 1 ;       // futile wake throttling
static int Knob_SuccRestrict       = 0 ;       // Limit successors + spinners to at-most-one
static int Knob_MaxSpinners        = -1 ;      // Should be a function of # CPUs
static int Knob_Bonus              = 100 ;     // spin success bonus
static int Knob_BonusB             = 100 ;     // spin success bonus
static int Knob_Penalty            = 200 ;     // spin failure penalty
static int Knob_Poverty            = 1000 ;
static int Knob_SpinAfterFutile    = 1 ;       // Spin after returning from park()
static int Knob_FixedSpin          = 0 ;
static int Knob_OState             = 3 ;       // Spinner checks thread state of _owner
static int Knob_UsePause           = 1 ;
static int Knob_ExitPolicy         = 0 ;
static int Knob_PreSpin            = 10 ;      // 20-100 likely better
static int Knob_ResetEvent         = 0 ;
static int BackOffMask             = 0 ;

static int Knob_FastHSSEC          = 0 ;
static int Knob_MoveNotifyee       = 2 ;       // notify() - disposition of notifyee
static int Knob_QMode              = 0 ;       // EntryList-cxq policy - queue discipline
static volatile int InitDone       = 0 ;


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

void BasicLock::print_on(outputStream* st) const {
  st->print("monitor");
}

void BasicLock::move_to(oop obj, BasicLock* dest) {
  // Check to see if we need to inflate the lock. This is only needed
  // if an object is locked using "this" lightweight monitor. In that
  // case, the displaced_header() is unlocked, because the
  // displaced_header() contains the header for the originally unlocked
  // object. However the object could have already been inflated. But it
  // does not matter, the inflation will just a no-op. For other cases,
  // the displaced header will be either 0x0 or 0x3, which are location
  // independent, therefore the BasicLock is free to move.
  //
  // During OSR we may need to relocate a BasicLock (which contains a
  // displaced word) from a location in an interpreter frame to a
  // new location in a compiled frame.  "this" refers to the source
  // basiclock in the interpreter frame.  "dest" refers to the destination
  // basiclock in the new compiled frame.  We *always* inflate in move_to().
  // The always-Inflate policy works properly, but in 1.5.0 it can sometimes
  // cause performance problems in code that makes heavy use of a small # of
  // uncontended locks.   (We'd inflate during OSR, and then sync performance
  // would subsequently plummet because the thread would be forced thru the slow-path).
  // This problem has been made largely moot on IA32 by inlining the inflated fast-path
  // operations in Fast_Lock and Fast_Unlock in i486.ad.
  //
  // Note that there is a way to safely swing the object's markword from
  // one stack location to another.  This avoids inflation.  Obviously,
  // we need to ensure that both locations refer to the current thread's stack.
  // There are some subtle concurrency issues, however, and since the benefit is
  // is small (given the support for inflated fast-path locking in the fast_lock, etc)
  // we'll leave that optimization for another time.

  if (displaced_header()->is_neutral()) {
    ObjectSynchronizer::inflate_helper(obj);
    // WARNING: We can not put check here, because the inflation
    // will not update the displaced header. Once BasicLock is inflated,
    // no one should ever look at its content.
  } else {
    // Typically the displaced header will be 0 (recursive stack lock) or
    // unused_mark.  Naively we'd like to assert that the displaced mark
    // value is either 0, neutral, or 3.  But with the advent of the
    // store-before-CAS avoidance in fast_lock/compiler_lock_object
    // we can find any flavor mark in the displaced mark.
  }
// [RGV] The next line appears to do nothing!
  intptr_t dh = (intptr_t) displaced_header();
  dest->set_displaced_header(displaced_header());
}

// -----------------------------------------------------------------------------

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


PerfCounter * ObjectSynchronizer::_sync_Inflations                  = NULL ;
PerfCounter * ObjectSynchronizer::_sync_Deflations                  = NULL ;
PerfCounter * ObjectSynchronizer::_sync_ContendedLockAttempts       = NULL ;
PerfCounter * ObjectSynchronizer::_sync_FutileWakeups               = NULL ;
PerfCounter * ObjectSynchronizer::_sync_Parks                       = NULL ;
PerfCounter * ObjectSynchronizer::_sync_EmptyNotifications          = NULL ;
PerfCounter * ObjectSynchronizer::_sync_Notifications               = NULL ;
PerfCounter * ObjectSynchronizer::_sync_PrivateA                    = NULL ;
PerfCounter * ObjectSynchronizer::_sync_PrivateB                    = NULL ;
PerfCounter * ObjectSynchronizer::_sync_SlowExit                    = NULL ;
PerfCounter * ObjectSynchronizer::_sync_SlowEnter                   = NULL ;
PerfCounter * ObjectSynchronizer::_sync_SlowNotify                  = NULL ;
PerfCounter * ObjectSynchronizer::_sync_SlowNotifyAll               = NULL ;
PerfCounter * ObjectSynchronizer::_sync_FailedSpins                 = NULL ;
PerfCounter * ObjectSynchronizer::_sync_SuccessfulSpins             = NULL ;
PerfCounter * ObjectSynchronizer::_sync_MonInCirculation            = NULL ;
PerfCounter * ObjectSynchronizer::_sync_MonScavenged                = NULL ;
PerfLongVariable * ObjectSynchronizer::_sync_MonExtant              = NULL ;

// One-shot global initialization for the sync subsystem.
// We could also defer initialization and initialize on-demand
// the first time we call inflate().  Initialization would
// be protected - like so many things - by the MonitorCache_lock.

void ObjectSynchronizer::Initialize () {
  static int InitializationCompleted = 0 ;
  assert (InitializationCompleted == 0, "invariant") ;
  InitializationCompleted = 1 ;
  if (UsePerfData) {
      EXCEPTION_MARK ;
      #define NEWPERFCOUNTER(n)   {n = PerfDataManager::create_counter(SUN_RT, #n, PerfData::U_Events,CHECK); }
      #define NEWPERFVARIABLE(n)  {n = PerfDataManager::create_variable(SUN_RT, #n, PerfData::U_Events,CHECK); }
      NEWPERFCOUNTER(_sync_Inflations) ;
      NEWPERFCOUNTER(_sync_Deflations) ;
      NEWPERFCOUNTER(_sync_ContendedLockAttempts) ;
      NEWPERFCOUNTER(_sync_FutileWakeups) ;
      NEWPERFCOUNTER(_sync_Parks) ;
      NEWPERFCOUNTER(_sync_EmptyNotifications) ;
      NEWPERFCOUNTER(_sync_Notifications) ;
      NEWPERFCOUNTER(_sync_SlowEnter) ;
      NEWPERFCOUNTER(_sync_SlowExit) ;
      NEWPERFCOUNTER(_sync_SlowNotify) ;
      NEWPERFCOUNTER(_sync_SlowNotifyAll) ;
      NEWPERFCOUNTER(_sync_FailedSpins) ;
      NEWPERFCOUNTER(_sync_SuccessfulSpins) ;
      NEWPERFCOUNTER(_sync_PrivateA) ;
      NEWPERFCOUNTER(_sync_PrivateB) ;
      NEWPERFCOUNTER(_sync_MonInCirculation) ;
      NEWPERFCOUNTER(_sync_MonScavenged) ;
      NEWPERFVARIABLE(_sync_MonExtant) ;
      #undef NEWPERFCOUNTER
  }
}

// Compile-time asserts
// When possible, it's better to catch errors deterministically at
// compile-time than at runtime.  The down-side to using compile-time
// asserts is that error message -- often something about negative array
// indices -- is opaque.

#define CTASSERT(x) { int tag[1-(2*!(x))]; printf ("Tag @" INTPTR_FORMAT "\n", (intptr_t)tag); }

void ObjectMonitor::ctAsserts() {
  CTASSERT(offset_of (ObjectMonitor, _header) == 0);
}

static int Adjust (volatile int * adr, int dx) {
  int v ;
  for (v = *adr ; Atomic::cmpxchg (v + dx, adr, v) != v; v = *adr) ;
  return v ;
}

// Ad-hoc mutual exclusion primitives: SpinLock and Mux
//
// We employ SpinLocks _only for low-contention, fixed-length
// short-duration critical sections where we're concerned
// about native mutex_t or HotSpot Mutex:: latency.
// The mux construct provides a spin-then-block mutual exclusion
// mechanism.
//
// Testing has shown that contention on the ListLock guarding gFreeList
// is common.  If we implement ListLock as a simple SpinLock it's common
// for the JVM to devolve to yielding with little progress.  This is true
// despite the fact that the critical sections protected by ListLock are
// extremely short.
//
// TODO-FIXME: ListLock should be of type SpinLock.
// We should make this a 1st-class type, integrated into the lock
// hierarchy as leaf-locks.  Critically, the SpinLock structure
// should have sufficient padding to avoid false-sharing and excessive
// cache-coherency traffic.


typedef volatile int SpinLockT ;

void Thread::SpinAcquire (volatile int * adr, const char * LockName) {
  if (Atomic::cmpxchg (1, adr, 0) == 0) {
     return ;   // normal fast-path return
  }

  // Slow-path : We've encountered contention -- Spin/Yield/Block strategy.
  TEVENT (SpinAcquire - ctx) ;
  int ctr = 0 ;
  int Yields = 0 ;
  for (;;) {
     while (*adr != 0) {
        ++ctr ;
        if ((ctr & 0xFFF) == 0 || !os::is_MP()) {
           if (Yields > 5) {
             // Consider using a simple NakedSleep() instead.
             // Then SpinAcquire could be called by non-JVM threads
             Thread::current()->_ParkEvent->park(1) ;
           } else {
             os::NakedYield() ;
             ++Yields ;
           }
        } else {
           SpinPause() ;
        }
     }
     if (Atomic::cmpxchg (1, adr, 0) == 0) return ;
  }
}

void Thread::SpinRelease (volatile int * adr) {
  assert (*adr != 0, "invariant") ;
  OrderAccess::fence() ;      // guarantee at least release consistency.
  // Roach-motel semantics.
  // It's safe if subsequent LDs and STs float "up" into the critical section,
  // but prior LDs and STs within the critical section can't be allowed
  // to reorder or float past the ST that releases the lock.
  *adr = 0 ;
}

// muxAcquire and muxRelease:
//
// *  muxAcquire and muxRelease support a single-word lock-word construct.
//    The LSB of the word is set IFF the lock is held.
//    The remainder of the word points to the head of a singly-linked list
//    of threads blocked on the lock.
//
// *  The current implementation of muxAcquire-muxRelease uses its own
//    dedicated Thread._MuxEvent instance.  If we're interested in
//    minimizing the peak number of extant ParkEvent instances then
//    we could eliminate _MuxEvent and "borrow" _ParkEvent as long
//    as certain invariants were satisfied.  Specifically, care would need
//    to be taken with regards to consuming unpark() "permits".
//    A safe rule of thumb is that a thread would never call muxAcquire()
//    if it's enqueued (cxq, EntryList, WaitList, etc) and will subsequently
//    park().  Otherwise the _ParkEvent park() operation in muxAcquire() could
//    consume an unpark() permit intended for monitorenter, for instance.
//    One way around this would be to widen the restricted-range semaphore
//    implemented in park().  Another alternative would be to provide
//    multiple instances of the PlatformEvent() for each thread.  One
//    instance would be dedicated to muxAcquire-muxRelease, for instance.
//
// *  Usage:
//    -- Only as leaf locks
//    -- for short-term locking only as muxAcquire does not perform
//       thread state transitions.
//
// Alternatives:
// *  We could implement muxAcquire and muxRelease with MCS or CLH locks
//    but with parking or spin-then-park instead of pure spinning.
// *  Use Taura-Oyama-Yonenzawa locks.
// *  It's possible to construct a 1-0 lock if we encode the lockword as
//    (List,LockByte).  Acquire will CAS the full lockword while Release
//    will STB 0 into the LockByte.  The 1-0 scheme admits stranding, so
//    acquiring threads use timers (ParkTimed) to detect and recover from
//    the stranding window.  Thread/Node structures must be aligned on 256-byte
//    boundaries by using placement-new.
// *  Augment MCS with advisory back-link fields maintained with CAS().
//    Pictorially:  LockWord -> T1 <-> T2 <-> T3 <-> ... <-> Tn <-> Owner.
//    The validity of the backlinks must be ratified before we trust the value.
//    If the backlinks are invalid the exiting thread must back-track through the
//    the forward links, which are always trustworthy.
// *  Add a successor indication.  The LockWord is currently encoded as
//    (List, LOCKBIT:1).  We could also add a SUCCBIT or an explicit _succ variable
//    to provide the usual futile-wakeup optimization.
//    See RTStt for details.
// *  Consider schedctl.sc_nopreempt to cover the critical section.
//


typedef volatile intptr_t MutexT ;      // Mux Lock-word
enum MuxBits { LOCKBIT = 1 } ;

void Thread::muxAcquire (volatile intptr_t * Lock, const char * LockName) {
  intptr_t w = Atomic::cmpxchg_ptr (LOCKBIT, Lock, 0) ;
  if (w == 0) return ;
  if ((w & LOCKBIT) == 0 && Atomic::cmpxchg_ptr (w|LOCKBIT, Lock, w) == w) {
     return ;
  }

  TEVENT (muxAcquire - Contention) ;
  ParkEvent * const Self = Thread::current()->_MuxEvent ;
  assert ((intptr_t(Self) & LOCKBIT) == 0, "invariant") ;
  for (;;) {
     int its = (os::is_MP() ? 100 : 0) + 1 ;

     // Optional spin phase: spin-then-park strategy
     while (--its >= 0) {
       w = *Lock ;
       if ((w & LOCKBIT) == 0 && Atomic::cmpxchg_ptr (w|LOCKBIT, Lock, w) == w) {
          return ;
       }
     }

     Self->reset() ;
     Self->OnList = intptr_t(Lock) ;
     // The following fence() isn't _strictly necessary as the subsequent
     // CAS() both serializes execution and ratifies the fetched *Lock value.
     OrderAccess::fence();
     for (;;) {
        w = *Lock ;
        if ((w & LOCKBIT) == 0) {
            if (Atomic::cmpxchg_ptr (w|LOCKBIT, Lock, w) == w) {
                Self->OnList = 0 ;   // hygiene - allows stronger asserts
                return ;
            }
            continue ;      // Interference -- *Lock changed -- Just retry
        }
        assert (w & LOCKBIT, "invariant") ;
        Self->ListNext = (ParkEvent *) (w & ~LOCKBIT );
        if (Atomic::cmpxchg_ptr (intptr_t(Self)|LOCKBIT, Lock, w) == w) break ;
     }

     while (Self->OnList != 0) {
        Self->park() ;
     }
  }
}

void Thread::muxAcquireW (volatile intptr_t * Lock, ParkEvent * ev) {
  intptr_t w = Atomic::cmpxchg_ptr (LOCKBIT, Lock, 0) ;
  if (w == 0) return ;
  if ((w & LOCKBIT) == 0 && Atomic::cmpxchg_ptr (w|LOCKBIT, Lock, w) == w) {
    return ;
  }

  TEVENT (muxAcquire - Contention) ;
  ParkEvent * ReleaseAfter = NULL ;
  if (ev == NULL) {
    ev = ReleaseAfter = ParkEvent::Allocate (NULL) ;
  }
  assert ((intptr_t(ev) & LOCKBIT) == 0, "invariant") ;
  for (;;) {
    guarantee (ev->OnList == 0, "invariant") ;
    int its = (os::is_MP() ? 100 : 0) + 1 ;

    // Optional spin phase: spin-then-park strategy
    while (--its >= 0) {
      w = *Lock ;
      if ((w & LOCKBIT) == 0 && Atomic::cmpxchg_ptr (w|LOCKBIT, Lock, w) == w) {
        if (ReleaseAfter != NULL) {
          ParkEvent::Release (ReleaseAfter) ;
        }
        return ;
      }
    }

    ev->reset() ;
    ev->OnList = intptr_t(Lock) ;
    // The following fence() isn't _strictly necessary as the subsequent
    // CAS() both serializes execution and ratifies the fetched *Lock value.
    OrderAccess::fence();
    for (;;) {
      w = *Lock ;
      if ((w & LOCKBIT) == 0) {
        if (Atomic::cmpxchg_ptr (w|LOCKBIT, Lock, w) == w) {
          ev->OnList = 0 ;
          // We call ::Release while holding the outer lock, thus
          // artificially lengthening the critical section.
          // Consider deferring the ::Release() until the subsequent unlock(),
          // after we've dropped the outer lock.
          if (ReleaseAfter != NULL) {
            ParkEvent::Release (ReleaseAfter) ;
          }
          return ;
        }
        continue ;      // Interference -- *Lock changed -- Just retry
      }
      assert (w & LOCKBIT, "invariant") ;
      ev->ListNext = (ParkEvent *) (w & ~LOCKBIT );
      if (Atomic::cmpxchg_ptr (intptr_t(ev)|LOCKBIT, Lock, w) == w) break ;
    }

    while (ev->OnList != 0) {
      ev->park() ;
    }
  }
}

// Release() must extract a successor from the list and then wake that thread.
// It can "pop" the front of the list or use a detach-modify-reattach (DMR) scheme
// similar to that used by ParkEvent::Allocate() and ::Release().  DMR-based
// Release() would :
// (A) CAS() or swap() null to *Lock, releasing the lock and detaching the list.
// (B) Extract a successor from the private list "in-hand"
// (C) attempt to CAS() the residual back into *Lock over null.
//     If there were any newly arrived threads and the CAS() would fail.
//     In that case Release() would detach the RATs, re-merge the list in-hand
//     with the RATs and repeat as needed.  Alternately, Release() might
//     detach and extract a successor, but then pass the residual list to the wakee.
//     The wakee would be responsible for reattaching and remerging before it
//     competed for the lock.
//
// Both "pop" and DMR are immune from ABA corruption -- there can be
// multiple concurrent pushers, but only one popper or detacher.
// This implementation pops from the head of the list.  This is unfair,
// but tends to provide excellent throughput as hot threads remain hot.
// (We wake recently run threads first).

void Thread::muxRelease (volatile intptr_t * Lock)  {
  for (;;) {
    const intptr_t w = Atomic::cmpxchg_ptr (0, Lock, LOCKBIT) ;
    assert (w & LOCKBIT, "invariant") ;
    if (w == LOCKBIT) return ;
    ParkEvent * List = (ParkEvent *) (w & ~LOCKBIT) ;
    assert (List != NULL, "invariant") ;
    assert (List->OnList == intptr_t(Lock), "invariant") ;
    ParkEvent * nxt = List->ListNext ;

    // The following CAS() releases the lock and pops the head element.
    if (Atomic::cmpxchg_ptr (intptr_t(nxt), Lock, w) != w) {
      continue ;
    }
    List->OnList = 0 ;
    OrderAccess::fence() ;
    List->unpark () ;
    return ;
  }
}

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
// TODO-FIXME:
//
// *  We currently protect the gFreeList with a simple lock.
//    An alternate lock-free scheme would be to pop elements from the gFreeList
//    with CAS.  This would be safe from ABA corruption as long we only
//    recycled previously appearing elements onto the list in deflate_idle_monitors()
//    at STW-time.  Completely new elements could always be pushed onto the gFreeList
//    with CAS.  Elements that appeared previously on the list could only
//    be installed at STW-time.
//
// *  For efficiency and to help reduce the store-before-CAS penalty
//    the objectmonitors on gFreeList or local free lists should be ready to install
//    with the exception of _header and _object.  _object can be set after inflation.
//    In particular, keep all objectMonitors on a thread's private list in ready-to-install
//    state with m.Owner set properly.
//
// *  We could all diffuse contention by using multiple global (FreeList, Lock)
//    pairs -- threads could use trylock() and a cyclic-scan strategy to search for
//    an unlocked free list.
//
// *  Add lifecycle tags and assert()s.
//
// *  Be more consistent about when we clear an objectmonitor's fields:
//    A.  After extracting the objectmonitor from a free list.
//    B.  After adding an objectmonitor to a free list.
//

ObjectMonitor * ObjectSynchronizer::gBlockList = NULL ;
ObjectMonitor * volatile ObjectSynchronizer::gFreeList  = NULL ;
static volatile intptr_t ListLock = 0 ;      // protects global monitor free-list cache
#define CHAINMARKER ((oop)-1)

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
                ObjectMonitor * take = gFreeList ;
                gFreeList = take->FreeNext ;
                guarantee (take->object() == NULL, "invariant") ;
                guarantee (!take->is_busy(), "invariant") ;
                take->Recycle() ;
                omRelease (Self, take) ;
            }
            Thread::muxRelease (&ListLock) ;
            Self->omFreeProvision += 1 + (Self->omFreeProvision/2) ;
            if (Self->omFreeProvision > MAXPRIVATE ) Self->omFreeProvision = MAXPRIVATE ;
            TEVENT (omFirst - reprovision) ;
            continue ;
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
            vm_exit_out_of_memory (sizeof (ObjectMonitor[_BLOCKSIZE]), "Allocate ObjectMonitors") ;
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
// In the future the usage of omRelease() might change and monitors
// could migrate between free lists.  In that case to avoid excessive
// accumulation we could  limit omCount to (omProvision*2), otherwise return
// the objectMonitor to the global list.  We should drain (return) in reasonable chunks.
// That is, *not* one-at-a-time.


void ObjectSynchronizer::omRelease (Thread * Self, ObjectMonitor * m) {
    guarantee (m->object() == NULL, "invariant") ;
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
    if (List == NULL) return ;
    ObjectMonitor * Tail = NULL ;
    ObjectMonitor * s ;
    for (s = List ; s != NULL ; s = s->FreeNext) {
        Tail = s ;
        guarantee (s->object() == NULL, "invariant") ;
        guarantee (!s->is_busy(), "invariant") ;
        s->set_owner (NULL) ;   // redundant but good hygiene
        TEVENT (omFlush - Move one) ;
    }

    guarantee (Tail != NULL && List != NULL, "invariant") ;
    Thread::muxAcquire (&ListLock, "omFlush") ;
    Tail->FreeNext = gFreeList ;
    gFreeList = List ;
    Thread::muxRelease (&ListLock) ;
    TEVENT (omFlush) ;
}


// Get the next block in the block list.
static inline ObjectMonitor* next(ObjectMonitor* block) {
  assert(block->object() == CHAINMARKER, "must be a block header");
  block = block->FreeNext ;
  assert(block == NULL || block->object() == CHAINMARKER, "must be a block header");
  return block;
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

#define NINFLATIONLOCKS 256
static volatile intptr_t InflationLocks [NINFLATIONLOCKS] ;

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
          m->FreeNext      = NULL ;
          m->_Responsible  = NULL ;
          m->OwnerIsThread = 0 ;
          m->_recursions   = 0 ;
          m->_SpinDuration = Knob_SpinLimit ;   // Consider: maintain by type/class

          markOop cmp = (markOop) Atomic::cmpxchg_ptr (markOopDesc::INFLATING(), object->mark_addr(), mark) ;
          if (cmp != mark) {
             omRelease (Self, m) ;
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
          // m->OwnerIsThread = 1.  Note that a thread can inflate an object
          // that it has stack-locked -- as might happen in wait() -- directly
          // with CAS.  That is, we can avoid the xchg-NULL .... ST idiom.
          m->set_owner (mark->locker());
          m->set_object(object);
          // TODO-FIXME: assert BasicLock->dhw != 0.

          // Must preserve store ordering. The monitor state must
          // be stable at the time of publishing the monitor address.
          guarantee (object->mark() == markOopDesc::INFLATING(), "invariant") ;
          object->release_set_mark(markOopDesc::encode(m));

          // Hopefully the performance counters are allocated on distinct cache lines
          // to avoid false sharing on MP systems ...
          if (_sync_Inflations != NULL) _sync_Inflations->inc() ;
          TEVENT(Inflate: overwrite stacklock) ;
          if (TraceMonitorInflation) {
            if (object->is_instance()) {
              ResourceMark rm;
              tty->print_cr("Inflating object " INTPTR_FORMAT " , mark " INTPTR_FORMAT " , type %s",
                (intptr_t) object, (intptr_t) object->mark(),
                Klass::cast(object->klass())->external_name());
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
      m->FreeNext      = NULL ;
      m->_Responsible  = NULL ;
      m->_SpinDuration = Knob_SpinLimit ;       // consider: keep metastats by type/class

      if (Atomic::cmpxchg_ptr (markOopDesc::encode(m), object->mark_addr(), mark) != mark) {
          m->set_object (NULL) ;
          m->set_owner  (NULL) ;
          m->OwnerIsThread = 0 ;
          m->Recycle() ;
          omRelease (Self, m) ;
          m = NULL ;
          continue ;
          // interference - the markword changed - just retry.
          // The state-transitions are one-way, so there's no chance of
          // live-lock -- "Inflated" is an absorbing state.
      }

      // Hopefully the performance counters are allocated on distinct
      // cache lines to avoid false sharing on MP systems ...
      if (_sync_Inflations != NULL) _sync_Inflations->inc() ;
      TEVENT(Inflate: overwrite neutral) ;
      if (TraceMonitorInflation) {
        if (object->is_instance()) {
          ResourceMark rm;
          tty->print_cr("Inflating object " INTPTR_FORMAT " , mark " INTPTR_FORMAT " , type %s",
            (intptr_t) object, (intptr_t) object->mark(),
            Klass::cast(object->klass())->external_name());
        }
      }
      return m ;
  }
}


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

  THREAD->update_highest_lock((address)lock);
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

  ObjectSynchronizer::inflate(THREAD, object)->exit (THREAD) ;
}

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
    BiasedLocking::revoke_and_rebias(obj, false, THREAD);
  }
  assert(!obj->mark()->has_bias_pattern(), "biases should be revoked by now");

  ObjectMonitor* monitor = ObjectSynchronizer::inflate(THREAD, obj);
  // If this thread has locked the object, exit the monitor.  Note:  can't use
  // monitor->check(CHECK); must exit even if an exception is pending.
  if (monitor->check(THREAD)) {
     monitor->exit(THREAD);
  }
}

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

// This exists only as a workaround of dtrace bug 6254741
int dtrace_waited_probe(ObjectMonitor* monitor, Handle obj, Thread* thr) {
  DTRACE_MONITOR_PROBE(waited, monitor, obj(), thr);
  return 0;
}

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
    return Threads::owning_thread_from_monitor_owner(owner, doLock);
  }

  // Unlocked case, header in place
  // Cannot have assertion since this object may have been
  // locked by another thread when reaching here.
  // assert(mark->is_neutral(), "sanity check");

  return NULL;
}

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

// Deflate_idle_monitors() is called at all safepoints, immediately
// after all mutators are stopped, but before any objects have moved.
// It traverses the list of known monitors, deflating where possible.
// The scavenged monitor are returned to the monitor free list.
//
// Beware that we scavenge at *every* stop-the-world point.
// Having a large number of monitors in-circulation negatively
// impacts the performance of some applications (e.g., PointBase).
// Broadly, we want to minimize the # of monitors in circulation.
// Alternately, we could partition the active monitors into sub-lists
// of those that need scanning and those that do not.
// Specifically, we would add a new sub-list of objectmonitors
// that are in-circulation and potentially active.  deflate_idle_monitors()
// would scan only that list.  Other monitors could reside on a quiescent
// list.  Such sequestered monitors wouldn't need to be scanned by
// deflate_idle_monitors().  omAlloc() would first check the global free list,
// then the quiescent list, and, failing those, would allocate a new block.
// Deflate_idle_monitors() would scavenge and move monitors to the
// quiescent list.
//
// Perversely, the heap size -- and thus the STW safepoint rate --
// typically drives the scavenge rate.  Large heaps can mean infrequent GC,
// which in turn can mean large(r) numbers of objectmonitors in circulation.
// This is an unfortunate aspect of this design.
//
// Another refinement would be to refrain from calling deflate_idle_monitors()
// except at stop-the-world points associated with garbage collections.
//
// An even better solution would be to deflate on-the-fly, aggressively,
// at monitorexit-time as is done in EVM's metalock or Relaxed Locks.

void ObjectSynchronizer::deflate_idle_monitors() {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at safepoint");
  int nInuse = 0 ;              // currently associated with objects
  int nInCirculation = 0 ;      // extant
  int nScavenged = 0 ;          // reclaimed

  ObjectMonitor * FreeHead = NULL ;  // Local SLL of scavenged monitors
  ObjectMonitor * FreeTail = NULL ;

  // Iterate over all extant monitors - Scavenge all idle monitors.
  TEVENT (deflate_idle_monitors) ;
  for (ObjectMonitor* block = gBlockList; block != NULL; block = next(block)) {
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

      // Normal case ... The monitor is associated with obj.
      guarantee (obj->mark() == markOopDesc::encode(mid), "invariant") ;
      guarantee (mid == obj->mark()->monitor(), "invariant");
      guarantee (mid->header()->is_neutral(), "invariant");

      if (mid->is_busy()) {
         if (ClearResponsibleAtSTW) mid->_Responsible = NULL ;
         nInuse ++ ;
      } else {
         // Deflate the monitor if it is no longer being used
         // It's idle - scavenge and return to the global free list
         // plain old deflation ...
         TEVENT (deflate_idle_monitors - scavenge1) ;
         if (TraceMonitorInflation) {
           if (obj->is_instance()) {
             ResourceMark rm;
               tty->print_cr("Deflating object " INTPTR_FORMAT " , mark " INTPTR_FORMAT " , type %s",
                    (intptr_t) obj, (intptr_t) obj->mark(), Klass::cast(obj->klass())->external_name());
           }
         }

         // Restore the header back to obj
         obj->release_set_mark(mid->header());
         mid->clear();

         assert (mid->object() == NULL, "invariant") ;

         // Move the object to the working free list defined by FreeHead,FreeTail.
         mid->FreeNext = NULL ;
         if (FreeHead == NULL) FreeHead = mid ;
         if (FreeTail != NULL) FreeTail->FreeNext = mid ;
         FreeTail = mid ;
         nScavenged ++ ;
      }
    }
  }

  // Move the scavenged monitors back to the global free list.
  // In theory we don't need the freelist lock as we're at a STW safepoint.
  // omAlloc() and omFree() can only be called while a thread is _not in safepoint state.
  // But it's remotely possible that omFlush() or release_monitors_owned_by_thread()
  // might be called while not at a global STW safepoint.  In the interest of
  // safety we protect the following access with ListLock.
  // An even more conservative and prudent approach would be to guard
  // the main loop in scavenge_idle_monitors() with ListLock.
  if (FreeHead != NULL) {
     guarantee (FreeTail != NULL && nScavenged > 0, "invariant") ;
     assert (FreeTail->FreeNext == NULL, "invariant") ;
     // constant-time list splice - prepend scavenged segment to gFreeList
     Thread::muxAcquire (&ListLock, "scavenge - return") ;
     FreeTail->FreeNext = gFreeList ;
     gFreeList = FreeHead ;
     Thread::muxRelease (&ListLock) ;
  }

  if (_sync_Deflations != NULL) _sync_Deflations->inc(nScavenged) ;
  if (_sync_MonExtant  != NULL) _sync_MonExtant ->set_value(nInCirculation);

  // TODO: Add objectMonitor leak detection.
  // Audit/inventory the objectMonitors -- make sure they're all accounted for.
  GVars.stwRandom = os::random() ;
  GVars.stwCycle ++ ;
}

// A macro is used below because there may already be a pending
// exception which should not abort the execution of the routines
// which use this (which is why we don't put this into check_slow and
// call it with a CHECK argument).

#define CHECK_OWNER()                                                             \
  do {                                                                            \
    if (THREAD != _owner) {                                                       \
      if (THREAD->is_lock_owned((address) _owner)) {                              \
        _owner = THREAD ;  /* Convert from basiclock addr to Thread addr */       \
        _recursions = 0;                                                          \
        OwnerIsThread = 1 ;                                                       \
      } else {                                                                    \
        TEVENT (Throw IMSX) ;                                                     \
        THROW(vmSymbols::java_lang_IllegalMonitorStateException());               \
      }                                                                           \
    }                                                                             \
  } while (false)

// TODO-FIXME: eliminate ObjectWaiters.  Replace this visitor/enumerator
// interface with a simple FirstWaitingThread(), NextWaitingThread() interface.

ObjectWaiter* ObjectMonitor::first_waiter() {
  return _WaitSet;
}

ObjectWaiter* ObjectMonitor::next_waiter(ObjectWaiter* o) {
  return o->_next;
}

Thread* ObjectMonitor::thread_of_waiter(ObjectWaiter* o) {
  return o->_thread;
}

// initialize the monitor, exception the semaphore, all other fields
// are simple integers or pointers
ObjectMonitor::ObjectMonitor() {
  _header       = NULL;
  _count        = 0;
  _waiters      = 0,
  _recursions   = 0;
  _object       = NULL;
  _owner        = NULL;
  _WaitSet      = NULL;
  _WaitSetLock  = 0 ;
  _Responsible  = NULL ;
  _succ         = NULL ;
  _cxq          = NULL ;
  FreeNext      = NULL ;
  _EntryList    = NULL ;
  _SpinFreq     = 0 ;
  _SpinClock    = 0 ;
  OwnerIsThread = 0 ;
}

ObjectMonitor::~ObjectMonitor() {
   // TODO: Add asserts ...
   // _cxq == 0 _succ == NULL _owner == NULL _waiters == 0
   // _count == 0 _EntryList  == NULL etc
}

intptr_t ObjectMonitor::is_busy() const {
  // TODO-FIXME: merge _count and _waiters.
  // TODO-FIXME: assert _owner == null implies _recursions = 0
  // TODO-FIXME: assert _WaitSet != null implies _count > 0
  return _count|_waiters|intptr_t(_owner)|intptr_t(_cxq)|intptr_t(_EntryList ) ;
}

void ObjectMonitor::Recycle () {
  // TODO: add stronger asserts ...
  // _cxq == 0 _succ == NULL _owner == NULL _waiters == 0
  // _count == 0 EntryList  == NULL
  // _recursions == 0 _WaitSet == NULL
  // TODO: assert (is_busy()|_recursions) == 0
  _succ          = NULL ;
  _EntryList     = NULL ;
  _cxq           = NULL ;
  _WaitSet       = NULL ;
  _recursions    = 0 ;
  _SpinFreq      = 0 ;
  _SpinClock     = 0 ;
  OwnerIsThread  = 0 ;
}

// WaitSet management ...

inline void ObjectMonitor::AddWaiter(ObjectWaiter* node) {
  assert(node != NULL, "should not dequeue NULL node");
  assert(node->_prev == NULL, "node already in list");
  assert(node->_next == NULL, "node already in list");
  // put node at end of queue (circular doubly linked list)
  if (_WaitSet == NULL) {
    _WaitSet = node;
    node->_prev = node;
    node->_next = node;
  } else {
    ObjectWaiter* head = _WaitSet ;
    ObjectWaiter* tail = head->_prev;
    assert(tail->_next == head, "invariant check");
    tail->_next = node;
    head->_prev = node;
    node->_next = head;
    node->_prev = tail;
  }
}

inline ObjectWaiter* ObjectMonitor::DequeueWaiter() {
  // dequeue the very first waiter
  ObjectWaiter* waiter = _WaitSet;
  if (waiter) {
    DequeueSpecificWaiter(waiter);
  }
  return waiter;
}

inline void ObjectMonitor::DequeueSpecificWaiter(ObjectWaiter* node) {
  assert(node != NULL, "should not dequeue NULL node");
  assert(node->_prev != NULL, "node already removed from list");
  assert(node->_next != NULL, "node already removed from list");
  // when the waiter has woken up because of interrupt,
  // timeout or other spurious wake-up, dequeue the
  // waiter from waiting list
  ObjectWaiter* next = node->_next;
  if (next == node) {
    assert(node->_prev == node, "invariant check");
    _WaitSet = NULL;
  } else {
    ObjectWaiter* prev = node->_prev;
    assert(prev->_next == node, "invariant check");
    assert(next->_prev == node, "invariant check");
    next->_prev = prev;
    prev->_next = next;
    if (_WaitSet == node) {
      _WaitSet = next;
    }
  }
  node->_next = NULL;
  node->_prev = NULL;
}

static char * kvGet (char * kvList, const char * Key) {
    if (kvList == NULL) return NULL ;
    size_t n = strlen (Key) ;
    char * Search ;
    for (Search = kvList ; *Search ; Search += strlen(Search) + 1) {
        if (strncmp (Search, Key, n) == 0) {
            if (Search[n] == '=') return Search + n + 1 ;
            if (Search[n] == 0)   return (char *) "1" ;
        }
    }
    return NULL ;
}

static int kvGetInt (char * kvList, const char * Key, int Default) {
    char * v = kvGet (kvList, Key) ;
    int rslt = v ? ::strtol (v, NULL, 0) : Default ;
    if (Knob_ReportSettings && v != NULL) {
        ::printf ("  SyncKnob: %s %d(%d)\n", Key, rslt, Default) ;
        ::fflush (stdout) ;
    }
    return rslt ;
}

// By convention we unlink a contending thread from EntryList|cxq immediately
// after the thread acquires the lock in ::enter().  Equally, we could defer
// unlinking the thread until ::exit()-time.

void ObjectMonitor::UnlinkAfterAcquire (Thread * Self, ObjectWaiter * SelfNode)
{
    assert (_owner == Self, "invariant") ;
    assert (SelfNode->_thread == Self, "invariant") ;

    if (SelfNode->TState == ObjectWaiter::TS_ENTER) {
        // Normal case: remove Self from the DLL EntryList .
        // This is a constant-time operation.
        ObjectWaiter * nxt = SelfNode->_next ;
        ObjectWaiter * prv = SelfNode->_prev ;
        if (nxt != NULL) nxt->_prev = prv ;
        if (prv != NULL) prv->_next = nxt ;
        if (SelfNode == _EntryList ) _EntryList = nxt ;
        assert (nxt == NULL || nxt->TState == ObjectWaiter::TS_ENTER, "invariant") ;
        assert (prv == NULL || prv->TState == ObjectWaiter::TS_ENTER, "invariant") ;
        TEVENT (Unlink from EntryList) ;
    } else {
        guarantee (SelfNode->TState == ObjectWaiter::TS_CXQ, "invariant") ;
        // Inopportune interleaving -- Self is still on the cxq.
        // This usually means the enqueue of self raced an exiting thread.
        // Normally we'll find Self near the front of the cxq, so
        // dequeueing is typically fast.  If needbe we can accelerate
        // this with some MCS/CHL-like bidirectional list hints and advisory
        // back-links so dequeueing from the interior will normally operate
        // in constant-time.
        // Dequeue Self from either the head (with CAS) or from the interior
        // with a linear-time scan and normal non-atomic memory operations.
        // CONSIDER: if Self is on the cxq then simply drain cxq into EntryList
        // and then unlink Self from EntryList.  We have to drain eventually,
        // so it might as well be now.

        ObjectWaiter * v = _cxq ;
        assert (v != NULL, "invariant") ;
        if (v != SelfNode || Atomic::cmpxchg_ptr (SelfNode->_next, &_cxq, v) != v) {
            // The CAS above can fail from interference IFF a "RAT" arrived.
            // In that case Self must be in the interior and can no longer be
            // at the head of cxq.
            if (v == SelfNode) {
                assert (_cxq != v, "invariant") ;
                v = _cxq ;          // CAS above failed - start scan at head of list
            }
            ObjectWaiter * p ;
            ObjectWaiter * q = NULL ;
            for (p = v ; p != NULL && p != SelfNode; p = p->_next) {
                q = p ;
                assert (p->TState == ObjectWaiter::TS_CXQ, "invariant") ;
            }
            assert (v != SelfNode,  "invariant") ;
            assert (p == SelfNode,  "Node not found on cxq") ;
            assert (p != _cxq,      "invariant") ;
            assert (q != NULL,      "invariant") ;
            assert (q->_next == p,  "invariant") ;
            q->_next = p->_next ;
        }
        TEVENT (Unlink from cxq) ;
    }

    // Diagnostic hygiene ...
    SelfNode->_prev  = (ObjectWaiter *) 0xBAD ;
    SelfNode->_next  = (ObjectWaiter *) 0xBAD ;
    SelfNode->TState = ObjectWaiter::TS_RUN ;
}

// Caveat: TryLock() is not necessarily serializing if it returns failure.
// Callers must compensate as needed.

int ObjectMonitor::TryLock (Thread * Self) {
   for (;;) {
      void * own = _owner ;
      if (own != NULL) return 0 ;
      if (Atomic::cmpxchg_ptr (Self, &_owner, NULL) == NULL) {
         // Either guarantee _recursions == 0 or set _recursions = 0.
         assert (_recursions == 0, "invariant") ;
         assert (_owner == Self, "invariant") ;
         // CONSIDER: set or assert that OwnerIsThread == 1
         return 1 ;
      }
      // The lock had been free momentarily, but we lost the race to the lock.
      // Interference -- the CAS failed.
      // We can either return -1 or retry.
      // Retry doesn't make as much sense because the lock was just acquired.
      if (true) return -1 ;
   }
}

// NotRunnable() -- informed spinning
//
// Don't bother spinning if the owner is not eligible to drop the lock.
// Peek at the owner's schedctl.sc_state and Thread._thread_values and
// spin only if the owner thread is _thread_in_Java or _thread_in_vm.
// The thread must be runnable in order to drop the lock in timely fashion.
// If the _owner is not runnable then spinning will not likely be
// successful (profitable).
//
// Beware -- the thread referenced by _owner could have died
// so a simply fetch from _owner->_thread_state might trap.
// Instead, we use SafeFetchXX() to safely LD _owner->_thread_state.
// Because of the lifecycle issues the schedctl and _thread_state values
// observed by NotRunnable() might be garbage.  NotRunnable must
// tolerate this and consider the observed _thread_state value
// as advisory.
//
// Beware too, that _owner is sometimes a BasicLock address and sometimes
// a thread pointer.  We differentiate the two cases with OwnerIsThread.
// Alternately, we might tag the type (thread pointer vs basiclock pointer)
// with the LSB of _owner.  Another option would be to probablistically probe
// the putative _owner->TypeTag value.
//
// Checking _thread_state isn't perfect.  Even if the thread is
// in_java it might be blocked on a page-fault or have been preempted
// and sitting on a ready/dispatch queue.  _thread state in conjunction
// with schedctl.sc_state gives us a good picture of what the
// thread is doing, however.
//
// TODO: check schedctl.sc_state.
// We'll need to use SafeFetch32() to read from the schedctl block.
// See RFE #5004247 and http://sac.sfbay.sun.com/Archives/CaseLog/arc/PSARC/2005/351/
//
// The return value from NotRunnable() is *advisory* -- the
// result is based on sampling and is not necessarily coherent.
// The caller must tolerate false-negative and false-positive errors.
// Spinning, in general, is probabilistic anyway.


int ObjectMonitor::NotRunnable (Thread * Self, Thread * ox) {
    // Check either OwnerIsThread or ox->TypeTag == 2BAD.
    if (!OwnerIsThread) return 0 ;

    if (ox == NULL) return 0 ;

    // Avoid transitive spinning ...
    // Say T1 spins or blocks trying to acquire L.  T1._Stalled is set to L.
    // Immediately after T1 acquires L it's possible that T2, also
    // spinning on L, will see L.Owner=T1 and T1._Stalled=L.
    // This occurs transiently after T1 acquired L but before
    // T1 managed to clear T1.Stalled.  T2 does not need to abort
    // its spin in this circumstance.
    intptr_t BlockedOn = SafeFetchN ((intptr_t *) &ox->_Stalled, intptr_t(1)) ;

    if (BlockedOn == 1) return 1 ;
    if (BlockedOn != 0) {
      return BlockedOn != intptr_t(this) && _owner == ox ;
    }

    assert (sizeof(((JavaThread *)ox)->_thread_state == sizeof(int)), "invariant") ;
    int jst = SafeFetch32 ((int *) &((JavaThread *) ox)->_thread_state, -1) ; ;
    // consider also: jst != _thread_in_Java -- but that's overspecific.
    return jst == _thread_blocked || jst == _thread_in_native ;
}


// Adaptive spin-then-block - rational spinning
//
// Note that we spin "globally" on _owner with a classic SMP-polite TATAS
// algorithm.  On high order SMP systems it would be better to start with
// a brief global spin and then revert to spinning locally.  In the spirit of MCS/CLH,
// a contending thread could enqueue itself on the cxq and then spin locally
// on a thread-specific variable such as its ParkEvent._Event flag.
// That's left as an exercise for the reader.  Note that global spinning is
// not problematic on Niagara, as the L2$ serves the interconnect and has both
// low latency and massive bandwidth.
//
// Broadly, we can fix the spin frequency -- that is, the % of contended lock
// acquisition attempts where we opt to spin --  at 100% and vary the spin count
// (duration) or we can fix the count at approximately the duration of
// a context switch and vary the frequency.   Of course we could also
// vary both satisfying K == Frequency * Duration, where K is adaptive by monitor.
// See http://j2se.east/~dice/PERSIST/040824-AdaptiveSpinning.html.
//
// This implementation varies the duration "D", where D varies with
// the success rate of recent spin attempts. (D is capped at approximately
// length of a round-trip context switch).  The success rate for recent
// spin attempts is a good predictor of the success rate of future spin
// attempts.  The mechanism adapts automatically to varying critical
// section length (lock modality), system load and degree of parallelism.
// D is maintained per-monitor in _SpinDuration and is initialized
// optimistically.  Spin frequency is fixed at 100%.
//
// Note that _SpinDuration is volatile, but we update it without locks
// or atomics.  The code is designed so that _SpinDuration stays within
// a reasonable range even in the presence of races.  The arithmetic
// operations on _SpinDuration are closed over the domain of legal values,
// so at worst a race will install and older but still legal value.
// At the very worst this introduces some apparent non-determinism.
// We might spin when we shouldn't or vice-versa, but since the spin
// count are relatively short, even in the worst case, the effect is harmless.
//
// Care must be taken that a low "D" value does not become an
// an absorbing state.  Transient spinning failures -- when spinning
// is overall profitable -- should not cause the system to converge
// on low "D" values.  We want spinning to be stable and predictable
// and fairly responsive to change and at the same time we don't want
// it to oscillate, become metastable, be "too" non-deterministic,
// or converge on or enter undesirable stable absorbing states.
//
// We implement a feedback-based control system -- using past behavior
// to predict future behavior.  We face two issues: (a) if the
// input signal is random then the spin predictor won't provide optimal
// results, and (b) if the signal frequency is too high then the control
// system, which has some natural response lag, will "chase" the signal.
// (b) can arise from multimodal lock hold times.  Transient preemption
// can also result in apparent bimodal lock hold times.
// Although sub-optimal, neither condition is particularly harmful, as
// in the worst-case we'll spin when we shouldn't or vice-versa.
// The maximum spin duration is rather short so the failure modes aren't bad.
// To be conservative, I've tuned the gain in system to bias toward
// _not spinning.  Relatedly, the system can sometimes enter a mode where it
// "rings" or oscillates between spinning and not spinning.  This happens
// when spinning is just on the cusp of profitability, however, so the
// situation is not dire.  The state is benign -- there's no need to add
// hysteresis control to damp the transition rate between spinning and
// not spinning.
//
// - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
//
// Spin-then-block strategies ...
//
// Thoughts on ways to improve spinning :
//
// *  Periodically call {psr_}getloadavg() while spinning, and
//    permit unbounded spinning if the load average is <
//    the number of processors.  Beware, however, that getloadavg()
//    is exceptionally fast on solaris (about 1/10 the cost of a full
//    spin cycle, but quite expensive on linux.  Beware also, that
//    multiple JVMs could "ring" or oscillate in a feedback loop.
//    Sufficient damping would solve that problem.
//
// *  We currently use spin loops with iteration counters to approximate
//    spinning for some interval.  Given the availability of high-precision
//    time sources such as gethrtime(), %TICK, %STICK, RDTSC, etc., we should
//    someday reimplement the spin loops to duration-based instead of iteration-based.
//
// *  Don't spin if there are more than N = (CPUs/2) threads
//        currently spinning on the monitor (or globally).
//    That is, limit the number of concurrent spinners.
//    We might also limit the # of spinners in the JVM, globally.
//
// *  If a spinning thread observes _owner change hands it should
//    abort the spin (and park immediately) or at least debit
//    the spin counter by a large "penalty".
//
// *  Classically, the spin count is either K*(CPUs-1) or is a
//        simple constant that approximates the length of a context switch.
//    We currently use a value -- computed by a special utility -- that
//    approximates round-trip context switch times.
//
// *  Normally schedctl_start()/_stop() is used to advise the kernel
//    to avoid preempting threads that are running in short, bounded
//    critical sections.  We could use the schedctl hooks in an inverted
//    sense -- spinners would set the nopreempt flag, but poll the preempt
//    pending flag.  If a spinner observed a pending preemption it'd immediately
//    abort the spin and park.   As such, the schedctl service acts as
//    a preemption warning mechanism.
//
// *  In lieu of spinning, if the system is running below saturation
//    (that is, loadavg() << #cpus), we can instead suppress futile
//    wakeup throttling, or even wake more than one successor at exit-time.
//    The net effect is largely equivalent to spinning.  In both cases,
//    contending threads go ONPROC and opportunistically attempt to acquire
//    the lock, decreasing lock handover latency at the expense of wasted
//    cycles and context switching.
//
// *  We might to spin less after we've parked as the thread will
//    have less $ and TLB affinity with the processor.
//    Likewise, we might spin less if we come ONPROC on a different
//    processor or after a long period (>> rechose_interval).
//
// *  A table-driven state machine similar to Solaris' dispadmin scheduling
//    tables might be a better design.  Instead of encoding information in
//    _SpinDuration, _SpinFreq and _SpinClock we'd just use explicit,
//    discrete states.   Success or failure during a spin would drive
//    state transitions, and each state node would contain a spin count.
//
// *  If the processor is operating in a mode intended to conserve power
//    (such as Intel's SpeedStep) or to reduce thermal output (thermal
//    step-down mode) then the Java synchronization subsystem should
//    forgo spinning.
//
// *  The minimum spin duration should be approximately the worst-case
//    store propagation latency on the platform.  That is, the time
//    it takes a store on CPU A to become visible on CPU B, where A and
//    B are "distant".
//
// *  We might want to factor a thread's priority in the spin policy.
//    Threads with a higher priority might spin for slightly longer.
//    Similarly, if we use back-off in the TATAS loop, lower priority
//    threads might back-off longer.  We don't currently use a
//    thread's priority when placing it on the entry queue.  We may
//    want to consider doing so in future releases.
//
// *  We might transiently drop a thread's scheduling priority while it spins.
//    SCHED_BATCH on linux and FX scheduling class at priority=0 on Solaris
//    would suffice.  We could even consider letting the thread spin indefinitely at
//    a depressed or "idle" priority.  This brings up fairness issues, however --
//    in a saturated system a thread would with a reduced priority could languish
//    for extended periods on the ready queue.
//
// *  While spinning try to use the otherwise wasted time to help the VM make
//    progress:
//
//    -- YieldTo() the owner, if the owner is OFFPROC but ready
//       Done our remaining quantum directly to the ready thread.
//       This helps "push" the lock owner through the critical section.
//       It also tends to improve affinity/locality as the lock
//       "migrates" less frequently between CPUs.
//    -- Walk our own stack in anticipation of blocking.  Memoize the roots.
//    -- Perform strand checking for other thread.  Unpark potential strandees.
//    -- Help GC: trace or mark -- this would need to be a bounded unit of work.
//       Unfortunately this will pollute our $ and TLBs.  Recall that we
//       spin to avoid context switching -- context switching has an
//       immediate cost in latency, a disruptive cost to other strands on a CMT
//       processor, and an amortized cost because of the D$ and TLB cache
//       reload transient when the thread comes back ONPROC and repopulates
//       $s and TLBs.
//    -- call getloadavg() to see if the system is saturated.  It'd probably
//       make sense to call getloadavg() half way through the spin.
//       If the system isn't at full capacity the we'd simply reset
//       the spin counter to and extend the spin attempt.
//    -- Doug points out that we should use the same "helping" policy
//       in thread.yield().
//
// *  Try MONITOR-MWAIT on systems that support those instructions.
//
// *  The spin statistics that drive spin decisions & frequency are
//    maintained in the objectmonitor structure so if we deflate and reinflate
//    we lose spin state.  In practice this is not usually a concern
//    as the default spin state after inflation is aggressive (optimistic)
//    and tends toward spinning.  So in the worst case for a lock where
//    spinning is not profitable we may spin unnecessarily for a brief
//    period.  But then again, if a lock is contended it'll tend not to deflate
//    in the first place.


intptr_t ObjectMonitor::SpinCallbackArgument = 0 ;
int (*ObjectMonitor::SpinCallbackFunction)(intptr_t, int) = NULL ;

// Spinning: Fixed frequency (100%), vary duration

int ObjectMonitor::TrySpin_VaryDuration (Thread * Self) {

    // Dumb, brutal spin.  Good for comparative measurements against adaptive spinning.
    int ctr = Knob_FixedSpin ;
    if (ctr != 0) {
        while (--ctr >= 0) {
            if (TryLock (Self) > 0) return 1 ;
            SpinPause () ;
        }
        return 0 ;
    }

    for (ctr = Knob_PreSpin + 1; --ctr >= 0 ; ) {
      if (TryLock(Self) > 0) {
        // Increase _SpinDuration ...
        // Note that we don't clamp SpinDuration precisely at SpinLimit.
        // Raising _SpurDuration to the poverty line is key.
        int x = _SpinDuration ;
        if (x < Knob_SpinLimit) {
           if (x < Knob_Poverty) x = Knob_Poverty ;
           _SpinDuration = x + Knob_BonusB ;
        }
        return 1 ;
      }
      SpinPause () ;
    }

    // Admission control - verify preconditions for spinning
    //
    // We always spin a little bit, just to prevent _SpinDuration == 0 from
    // becoming an absorbing state.  Put another way, we spin briefly to
    // sample, just in case the system load, parallelism, contention, or lock
    // modality changed.
    //
    // Consider the following alternative:
    // Periodically set _SpinDuration = _SpinLimit and try a long/full
    // spin attempt.  "Periodically" might mean after a tally of
    // the # of failed spin attempts (or iterations) reaches some threshold.
    // This takes us into the realm of 1-out-of-N spinning, where we
    // hold the duration constant but vary the frequency.

    ctr = _SpinDuration  ;
    if (ctr < Knob_SpinBase) ctr = Knob_SpinBase ;
    if (ctr <= 0) return 0 ;

    if (Knob_SuccRestrict && _succ != NULL) return 0 ;
    if (Knob_OState && NotRunnable (Self, (Thread *) _owner)) {
       TEVENT (Spin abort - notrunnable [TOP]);
       return 0 ;
    }

    int MaxSpin = Knob_MaxSpinners ;
    if (MaxSpin >= 0) {
       if (_Spinner > MaxSpin) {
          TEVENT (Spin abort -- too many spinners) ;
          return 0 ;
       }
       // Slighty racy, but benign ...
       Adjust (&_Spinner, 1) ;
    }

    // We're good to spin ... spin ingress.
    // CONSIDER: use Prefetch::write() to avoid RTS->RTO upgrades
    // when preparing to LD...CAS _owner, etc and the CAS is likely
    // to succeed.
    int hits    = 0 ;
    int msk     = 0 ;
    int caspty  = Knob_CASPenalty ;
    int oxpty   = Knob_OXPenalty ;
    int sss     = Knob_SpinSetSucc ;
    if (sss && _succ == NULL ) _succ = Self ;
    Thread * prv = NULL ;

    // There are three ways to exit the following loop:
    // 1.  A successful spin where this thread has acquired the lock.
    // 2.  Spin failure with prejudice
    // 3.  Spin failure without prejudice

    while (--ctr >= 0) {

      // Periodic polling -- Check for pending GC
      // Threads may spin while they're unsafe.
      // We don't want spinning threads to delay the JVM from reaching
      // a stop-the-world safepoint or to steal cycles from GC.
      // If we detect a pending safepoint we abort in order that
      // (a) this thread, if unsafe, doesn't delay the safepoint, and (b)
      // this thread, if safe, doesn't steal cycles from GC.
      // This is in keeping with the "no loitering in runtime" rule.
      // We periodically check to see if there's a safepoint pending.
      if ((ctr & 0xFF) == 0) {
         if (SafepointSynchronize::do_call_back()) {
            TEVENT (Spin: safepoint) ;
            goto Abort ;           // abrupt spin egress
         }
         if (Knob_UsePause & 1) SpinPause () ;

         int (*scb)(intptr_t,int) = SpinCallbackFunction ;
         if (hits > 50 && scb != NULL) {
            int abend = (*scb)(SpinCallbackArgument, 0) ;
         }
      }

      if (Knob_UsePause & 2) SpinPause() ;

      // Exponential back-off ...  Stay off the bus to reduce coherency traffic.
      // This is useful on classic SMP systems, but is of less utility on
      // N1-style CMT platforms.
      //
      // Trade-off: lock acquisition latency vs coherency bandwidth.
      // Lock hold times are typically short.  A histogram
      // of successful spin attempts shows that we usually acquire
      // the lock early in the spin.  That suggests we want to
      // sample _owner frequently in the early phase of the spin,
      // but then back-off and sample less frequently as the spin
      // progresses.  The back-off makes a good citizen on SMP big
      // SMP systems.  Oversampling _owner can consume excessive
      // coherency bandwidth.  Relatedly, if we _oversample _owner we
      // can inadvertently interfere with the the ST m->owner=null.
      // executed by the lock owner.
      if (ctr & msk) continue ;
      ++hits ;
      if ((hits & 0xF) == 0) {
        // The 0xF, above, corresponds to the exponent.
        // Consider: (msk+1)|msk
        msk = ((msk << 2)|3) & BackOffMask ;
      }

      // Probe _owner with TATAS
      // If this thread observes the monitor transition or flicker
      // from locked to unlocked to locked, then the odds that this
      // thread will acquire the lock in this spin attempt go down
      // considerably.  The same argument applies if the CAS fails
      // or if we observe _owner change from one non-null value to
      // another non-null value.   In such cases we might abort
      // the spin without prejudice or apply a "penalty" to the
      // spin count-down variable "ctr", reducing it by 100, say.

      Thread * ox = (Thread *) _owner ;
      if (ox == NULL) {
         ox = (Thread *) Atomic::cmpxchg_ptr (Self, &_owner, NULL) ;
         if (ox == NULL) {
            // The CAS succeeded -- this thread acquired ownership
            // Take care of some bookkeeping to exit spin state.
            if (sss && _succ == Self) {
               _succ = NULL ;
            }
            if (MaxSpin > 0) Adjust (&_Spinner, -1) ;

            // Increase _SpinDuration :
            // The spin was successful (profitable) so we tend toward
            // longer spin attempts in the future.
            // CONSIDER: factor "ctr" into the _SpinDuration adjustment.
            // If we acquired the lock early in the spin cycle it
            // makes sense to increase _SpinDuration proportionally.
            // Note that we don't clamp SpinDuration precisely at SpinLimit.
            int x = _SpinDuration ;
            if (x < Knob_SpinLimit) {
                if (x < Knob_Poverty) x = Knob_Poverty ;
                _SpinDuration = x + Knob_Bonus ;
            }
            return 1 ;
         }

         // The CAS failed ... we can take any of the following actions:
         // * penalize: ctr -= Knob_CASPenalty
         // * exit spin with prejudice -- goto Abort;
         // * exit spin without prejudice.
         // * Since CAS is high-latency, retry again immediately.
         prv = ox ;
         TEVENT (Spin: cas failed) ;
         if (caspty == -2) break ;
         if (caspty == -1) goto Abort ;
         ctr -= caspty ;
         continue ;
      }

      // Did lock ownership change hands ?
      if (ox != prv && prv != NULL ) {
          TEVENT (spin: Owner changed)
          if (oxpty == -2) break ;
          if (oxpty == -1) goto Abort ;
          ctr -= oxpty ;
      }
      prv = ox ;

      // Abort the spin if the owner is not executing.
      // The owner must be executing in order to drop the lock.
      // Spinning while the owner is OFFPROC is idiocy.
      // Consider: ctr -= RunnablePenalty ;
      if (Knob_OState && NotRunnable (Self, ox)) {
         TEVENT (Spin abort - notrunnable);
         goto Abort ;
      }
      if (sss && _succ == NULL ) _succ = Self ;
   }

   // Spin failed with prejudice -- reduce _SpinDuration.
   // TODO: Use an AIMD-like policy to adjust _SpinDuration.
   // AIMD is globally stable.
   TEVENT (Spin failure) ;
   {
     int x = _SpinDuration ;
     if (x > 0) {
        // Consider an AIMD scheme like: x -= (x >> 3) + 100
        // This is globally sample and tends to damp the response.
        x -= Knob_Penalty ;
        if (x < 0) x = 0 ;
        _SpinDuration = x ;
     }
   }

 Abort:
   if (MaxSpin >= 0) Adjust (&_Spinner, -1) ;
   if (sss && _succ == Self) {
      _succ = NULL ;
      // Invariant: after setting succ=null a contending thread
      // must recheck-retry _owner before parking.  This usually happens
      // in the normal usage of TrySpin(), but it's safest
      // to make TrySpin() as foolproof as possible.
      OrderAccess::fence() ;
      if (TryLock(Self) > 0) return 1 ;
   }
   return 0 ;
}

#define TrySpin TrySpin_VaryDuration

static void DeferredInitialize () {
  if (InitDone > 0) return ;
  if (Atomic::cmpxchg (-1, &InitDone, 0) != 0) {
      while (InitDone != 1) ;
      return ;
  }

  // One-shot global initialization ...
  // The initialization is idempotent, so we don't need locks.
  // In the future consider doing this via os::init_2().
  // SyncKnobs consist of <Key>=<Value> pairs in the style
  // of environment variables.  Start by converting ':' to NUL.

  if (SyncKnobs == NULL) SyncKnobs = "" ;

  size_t sz = strlen (SyncKnobs) ;
  char * knobs = (char *) malloc (sz + 2) ;
  if (knobs == NULL) {
     vm_exit_out_of_memory (sz + 2, "Parse SyncKnobs") ;
     guarantee (0, "invariant") ;
  }
  strcpy (knobs, SyncKnobs) ;
  knobs[sz+1] = 0 ;
  for (char * p = knobs ; *p ; p++) {
     if (*p == ':') *p = 0 ;
  }

  #define SETKNOB(x) { Knob_##x = kvGetInt (knobs, #x, Knob_##x); }
  SETKNOB(ReportSettings) ;
  SETKNOB(Verbose) ;
  SETKNOB(FixedSpin) ;
  SETKNOB(SpinLimit) ;
  SETKNOB(SpinBase) ;
  SETKNOB(SpinBackOff);
  SETKNOB(CASPenalty) ;
  SETKNOB(OXPenalty) ;
  SETKNOB(LogSpins) ;
  SETKNOB(SpinSetSucc) ;
  SETKNOB(SuccEnabled) ;
  SETKNOB(SuccRestrict) ;
  SETKNOB(Penalty) ;
  SETKNOB(Bonus) ;
  SETKNOB(BonusB) ;
  SETKNOB(Poverty) ;
  SETKNOB(SpinAfterFutile) ;
  SETKNOB(UsePause) ;
  SETKNOB(SpinEarly) ;
  SETKNOB(OState) ;
  SETKNOB(MaxSpinners) ;
  SETKNOB(PreSpin) ;
  SETKNOB(ExitPolicy) ;
  SETKNOB(QMode);
  SETKNOB(ResetEvent) ;
  SETKNOB(MoveNotifyee) ;
  SETKNOB(FastHSSEC) ;
  #undef SETKNOB

  if (os::is_MP()) {
     BackOffMask = (1 << Knob_SpinBackOff) - 1 ;
     if (Knob_ReportSettings) ::printf ("BackOffMask=%X\n", BackOffMask) ;
     // CONSIDER: BackOffMask = ROUNDUP_NEXT_POWER2 (ncpus-1)
  } else {
     Knob_SpinLimit = 0 ;
     Knob_SpinBase  = 0 ;
     Knob_PreSpin   = 0 ;
     Knob_FixedSpin = -1 ;
  }

  if (Knob_LogSpins == 0) {
     ObjectSynchronizer::_sync_FailedSpins = NULL ;
  }

  free (knobs) ;
  OrderAccess::fence() ;
  InitDone = 1 ;
}

// Theory of operations -- Monitors lists, thread residency, etc:
//
// * A thread acquires ownership of a monitor by successfully
//   CAS()ing the _owner field from null to non-null.
//
// * Invariant: A thread appears on at most one monitor list --
//   cxq, EntryList or WaitSet -- at any one time.
//
// * Contending threads "push" themselves onto the cxq with CAS
//   and then spin/park.
//
// * After a contending thread eventually acquires the lock it must
//   dequeue itself from either the EntryList or the cxq.
//
// * The exiting thread identifies and unparks an "heir presumptive"
//   tentative successor thread on the EntryList.  Critically, the
//   exiting thread doesn't unlink the successor thread from the EntryList.
//   After having been unparked, the wakee will recontend for ownership of
//   the monitor.   The successor (wakee) will either acquire the lock or
//   re-park itself.
//
//   Succession is provided for by a policy of competitive handoff.
//   The exiting thread does _not_ grant or pass ownership to the
//   successor thread.  (This is also referred to as "handoff" succession").
//   Instead the exiting thread releases ownership and possibly wakes
//   a successor, so the successor can (re)compete for ownership of the lock.
//   If the EntryList is empty but the cxq is populated the exiting
//   thread will drain the cxq into the EntryList.  It does so by
//   by detaching the cxq (installing null with CAS) and folding
//   the threads from the cxq into the EntryList.  The EntryList is
//   doubly linked, while the cxq is singly linked because of the
//   CAS-based "push" used to enqueue recently arrived threads (RATs).
//
// * Concurrency invariants:
//
//   -- only the monitor owner may access or mutate the EntryList.
//      The mutex property of the monitor itself protects the EntryList
//      from concurrent interference.
//   -- Only the monitor owner may detach the cxq.
//
// * The monitor entry list operations avoid locks, but strictly speaking
//   they're not lock-free.  Enter is lock-free, exit is not.
//   See http://j2se.east/~dice/PERSIST/040825-LockFreeQueues.html
//
// * The cxq can have multiple concurrent "pushers" but only one concurrent
//   detaching thread.  This mechanism is immune from the ABA corruption.
//   More precisely, the CAS-based "push" onto cxq is ABA-oblivious.
//
// * Taken together, the cxq and the EntryList constitute or form a
//   single logical queue of threads stalled trying to acquire the lock.
//   We use two distinct lists to improve the odds of a constant-time
//   dequeue operation after acquisition (in the ::enter() epilog) and
//   to reduce heat on the list ends.  (c.f. Michael Scott's "2Q" algorithm).
//   A key desideratum is to minimize queue & monitor metadata manipulation
//   that occurs while holding the monitor lock -- that is, we want to
//   minimize monitor lock holds times.  Note that even a small amount of
//   fixed spinning will greatly reduce the # of enqueue-dequeue operations
//   on EntryList|cxq.  That is, spinning relieves contention on the "inner"
//   locks and monitor metadata.
//
//   Cxq points to the the set of Recently Arrived Threads attempting entry.
//   Because we push threads onto _cxq with CAS, the RATs must take the form of
//   a singly-linked LIFO.  We drain _cxq into EntryList  at unlock-time when
//   the unlocking thread notices that EntryList is null but _cxq is != null.
//
//   The EntryList is ordered by the prevailing queue discipline and
//   can be organized in any convenient fashion, such as a doubly-linked list or
//   a circular doubly-linked list.  Critically, we want insert and delete operations
//   to operate in constant-time.  If we need a priority queue then something akin
//   to Solaris' sleepq would work nicely.  Viz.,
//   http://agg.eng/ws/on10_nightly/source/usr/src/uts/common/os/sleepq.c.
//   Queue discipline is enforced at ::exit() time, when the unlocking thread
//   drains the cxq into the EntryList, and orders or reorders the threads on the
//   EntryList accordingly.
//
//   Barring "lock barging", this mechanism provides fair cyclic ordering,
//   somewhat similar to an elevator-scan.
//
// * The monitor synchronization subsystem avoids the use of native
//   synchronization primitives except for the narrow platform-specific
//   park-unpark abstraction.  See the comments in os_solaris.cpp regarding
//   the semantics of park-unpark.  Put another way, this monitor implementation
//   depends only on atomic operations and park-unpark.  The monitor subsystem
//   manages all RUNNING->BLOCKED and BLOCKED->READY transitions while the
//   underlying OS manages the READY<->RUN transitions.
//
// * Waiting threads reside on the WaitSet list -- wait() puts
//   the caller onto the WaitSet.
//
// * notify() or notifyAll() simply transfers threads from the WaitSet to
//   either the EntryList or cxq.  Subsequent exit() operations will
//   unpark the notifyee.  Unparking a notifee in notify() is inefficient -
//   it's likely the notifyee would simply impale itself on the lock held
//   by the notifier.
//
// * An interesting alternative is to encode cxq as (List,LockByte) where
//   the LockByte is 0 iff the monitor is owned.  _owner is simply an auxiliary
//   variable, like _recursions, in the scheme.  The threads or Events that form
//   the list would have to be aligned in 256-byte addresses.  A thread would
//   try to acquire the lock or enqueue itself with CAS, but exiting threads
//   could use a 1-0 protocol and simply STB to set the LockByte to 0.
//   Note that is is *not* word-tearing, but it does presume that full-word
//   CAS operations are coherent with intermix with STB operations.  That's true
//   on most common processors.
//
// * See also http://blogs.sun.com/dave


void ATTR ObjectMonitor::EnterI (TRAPS) {
    Thread * Self = THREAD ;
    assert (Self->is_Java_thread(), "invariant") ;
    assert (((JavaThread *) Self)->thread_state() == _thread_blocked   , "invariant") ;

    // Try the lock - TATAS
    if (TryLock (Self) > 0) {
        assert (_succ != Self              , "invariant") ;
        assert (_owner == Self             , "invariant") ;
        assert (_Responsible != Self       , "invariant") ;
        return ;
    }

    DeferredInitialize () ;

    // We try one round of spinning *before* enqueueing Self.
    //
    // If the _owner is ready but OFFPROC we could use a YieldTo()
    // operation to donate the remainder of this thread's quantum
    // to the owner.  This has subtle but beneficial affinity
    // effects.

    if (TrySpin (Self) > 0) {
        assert (_owner == Self        , "invariant") ;
        assert (_succ != Self         , "invariant") ;
        assert (_Responsible != Self  , "invariant") ;
        return ;
    }

    // The Spin failed -- Enqueue and park the thread ...
    assert (_succ  != Self            , "invariant") ;
    assert (_owner != Self            , "invariant") ;
    assert (_Responsible != Self      , "invariant") ;

    // Enqueue "Self" on ObjectMonitor's _cxq.
    //
    // Node acts as a proxy for Self.
    // As an aside, if were to ever rewrite the synchronization code mostly
    // in Java, WaitNodes, ObjectMonitors, and Events would become 1st-class
    // Java objects.  This would avoid awkward lifecycle and liveness issues,
    // as well as eliminate a subset of ABA issues.
    // TODO: eliminate ObjectWaiter and enqueue either Threads or Events.
    //

    ObjectWaiter node(Self) ;
    Self->_ParkEvent->reset() ;
    node._prev   = (ObjectWaiter *) 0xBAD ;
    node.TState  = ObjectWaiter::TS_CXQ ;

    // Push "Self" onto the front of the _cxq.
    // Once on cxq/EntryList, Self stays on-queue until it acquires the lock.
    // Note that spinning tends to reduce the rate at which threads
    // enqueue and dequeue on EntryList|cxq.
    ObjectWaiter * nxt ;
    for (;;) {
        node._next = nxt = _cxq ;
        if (Atomic::cmpxchg_ptr (&node, &_cxq, nxt) == nxt) break ;

        // Interference - the CAS failed because _cxq changed.  Just retry.
        // As an optional optimization we retry the lock.
        if (TryLock (Self) > 0) {
            assert (_succ != Self         , "invariant") ;
            assert (_owner == Self        , "invariant") ;
            assert (_Responsible != Self  , "invariant") ;
            return ;
        }
    }

    // Check for cxq|EntryList edge transition to non-null.  This indicates
    // the onset of contention.  While contention persists exiting threads
    // will use a ST:MEMBAR:LD 1-1 exit protocol.  When contention abates exit
    // operations revert to the faster 1-0 mode.  This enter operation may interleave
    // (race) a concurrent 1-0 exit operation, resulting in stranding, so we
    // arrange for one of the contending thread to use a timed park() operations
    // to detect and recover from the race.  (Stranding is form of progress failure
    // where the monitor is unlocked but all the contending threads remain parked).
    // That is, at least one of the contended threads will periodically poll _owner.
    // One of the contending threads will become the designated "Responsible" thread.
    // The Responsible thread uses a timed park instead of a normal indefinite park
    // operation -- it periodically wakes and checks for and recovers from potential
    // strandings admitted by 1-0 exit operations.   We need at most one Responsible
    // thread per-monitor at any given moment.  Only threads on cxq|EntryList may
    // be responsible for a monitor.
    //
    // Currently, one of the contended threads takes on the added role of "Responsible".
    // A viable alternative would be to use a dedicated "stranding checker" thread
    // that periodically iterated over all the threads (or active monitors) and unparked
    // successors where there was risk of stranding.  This would help eliminate the
    // timer scalability issues we see on some platforms as we'd only have one thread
    // -- the checker -- parked on a timer.

    if ((SyncFlags & 16) == 0 && nxt == NULL && _EntryList == NULL) {
        // Try to assume the role of responsible thread for the monitor.
        // CONSIDER:  ST vs CAS vs { if (Responsible==null) Responsible=Self }
        Atomic::cmpxchg_ptr (Self, &_Responsible, NULL) ;
    }

    // The lock have been released while this thread was occupied queueing
    // itself onto _cxq.  To close the race and avoid "stranding" and
    // progress-liveness failure we must resample-retry _owner before parking.
    // Note the Dekker/Lamport duality: ST cxq; MEMBAR; LD Owner.
    // In this case the ST-MEMBAR is accomplished with CAS().
    //
    // TODO: Defer all thread state transitions until park-time.
    // Since state transitions are heavy and inefficient we'd like
    // to defer the state transitions until absolutely necessary,
    // and in doing so avoid some transitions ...

    TEVENT (Inflated enter - Contention) ;
    int nWakeups = 0 ;
    int RecheckInterval = 1 ;

    for (;;) {

        if (TryLock (Self) > 0) break ;
        assert (_owner != Self, "invariant") ;

        if ((SyncFlags & 2) && _Responsible == NULL) {
           Atomic::cmpxchg_ptr (Self, &_Responsible, NULL) ;
        }

        // park self
        if (_Responsible == Self || (SyncFlags & 1)) {
            TEVENT (Inflated enter - park TIMED) ;
            Self->_ParkEvent->park ((jlong) RecheckInterval) ;
            // Increase the RecheckInterval, but clamp the value.
            RecheckInterval *= 8 ;
            if (RecheckInterval > 1000) RecheckInterval = 1000 ;
        } else {
            TEVENT (Inflated enter - park UNTIMED) ;
            Self->_ParkEvent->park() ;
        }

        if (TryLock(Self) > 0) break ;

        // The lock is still contested.
        // Keep a tally of the # of futile wakeups.
        // Note that the counter is not protected by a lock or updated by atomics.
        // That is by design - we trade "lossy" counters which are exposed to
        // races during updates for a lower probe effect.
        TEVENT (Inflated enter - Futile wakeup) ;
        if (ObjectSynchronizer::_sync_FutileWakeups != NULL) {
           ObjectSynchronizer::_sync_FutileWakeups->inc() ;
        }
        ++ nWakeups ;

        // Assuming this is not a spurious wakeup we'll normally find _succ == Self.
        // We can defer clearing _succ until after the spin completes
        // TrySpin() must tolerate being called with _succ == Self.
        // Try yet another round of adaptive spinning.
        if ((Knob_SpinAfterFutile & 1) && TrySpin (Self) > 0) break ;

        // We can find that we were unpark()ed and redesignated _succ while
        // we were spinning.  That's harmless.  If we iterate and call park(),
        // park() will consume the event and return immediately and we'll
        // just spin again.  This pattern can repeat, leaving _succ to simply
        // spin on a CPU.  Enable Knob_ResetEvent to clear pending unparks().
        // Alternately, we can sample fired() here, and if set, forgo spinning
        // in the next iteration.

        if ((Knob_ResetEvent & 1) && Self->_ParkEvent->fired()) {
           Self->_ParkEvent->reset() ;
           OrderAccess::fence() ;
        }
        if (_succ == Self) _succ = NULL ;

        // Invariant: after clearing _succ a thread *must* retry _owner before parking.
        OrderAccess::fence() ;
    }

    // Egress :
    // Self has acquired the lock -- Unlink Self from the cxq or EntryList.
    // Normally we'll find Self on the EntryList .
    // From the perspective of the lock owner (this thread), the
    // EntryList is stable and cxq is prepend-only.
    // The head of cxq is volatile but the interior is stable.
    // In addition, Self.TState is stable.

    assert (_owner == Self      , "invariant") ;
    assert (object() != NULL    , "invariant") ;
    // I'd like to write:
    //   guarantee (((oop)(object()))->mark() == markOopDesc::encode(this), "invariant") ;
    // but as we're at a safepoint that's not safe.

    UnlinkAfterAcquire (Self, &node) ;
    if (_succ == Self) _succ = NULL ;

    assert (_succ != Self, "invariant") ;
    if (_Responsible == Self) {
        _Responsible = NULL ;
        // Dekker pivot-point.
        // Consider OrderAccess::storeload() here

        // We may leave threads on cxq|EntryList without a designated
        // "Responsible" thread.  This is benign.  When this thread subsequently
        // exits the monitor it can "see" such preexisting "old" threads --
        // threads that arrived on the cxq|EntryList before the fence, above --
        // by LDing cxq|EntryList.  Newly arrived threads -- that is, threads
        // that arrive on cxq after the ST:MEMBAR, above -- will set Responsible
        // non-null and elect a new "Responsible" timer thread.
        //
        // This thread executes:
        //    ST Responsible=null; MEMBAR    (in enter epilog - here)
        //    LD cxq|EntryList               (in subsequent exit)
        //
        // Entering threads in the slow/contended path execute:
        //    ST cxq=nonnull; MEMBAR; LD Responsible (in enter prolog)
        //    The (ST cxq; MEMBAR) is accomplished with CAS().
        //
        // The MEMBAR, above, prevents the LD of cxq|EntryList in the subsequent
        // exit operation from floating above the ST Responsible=null.
        //
        // In *practice* however, EnterI() is always followed by some atomic
        // operation such as the decrement of _count in ::enter().  Those atomics
        // obviate the need for the explicit MEMBAR, above.
    }

    // We've acquired ownership with CAS().
    // CAS is serializing -- it has MEMBAR/FENCE-equivalent semantics.
    // But since the CAS() this thread may have also stored into _succ,
    // EntryList, cxq or Responsible.  These meta-data updates must be
    // visible __before this thread subsequently drops the lock.
    // Consider what could occur if we didn't enforce this constraint --
    // STs to monitor meta-data and user-data could reorder with (become
    // visible after) the ST in exit that drops ownership of the lock.
    // Some other thread could then acquire the lock, but observe inconsistent
    // or old monitor meta-data and heap data.  That violates the JMM.
    // To that end, the 1-0 exit() operation must have at least STST|LDST
    // "release" barrier semantics.  Specifically, there must be at least a
    // STST|LDST barrier in exit() before the ST of null into _owner that drops
    // the lock.   The barrier ensures that changes to monitor meta-data and data
    // protected by the lock will be visible before we release the lock, and
    // therefore before some other thread (CPU) has a chance to acquire the lock.
    // See also: http://gee.cs.oswego.edu/dl/jmm/cookbook.html.
    //
    // Critically, any prior STs to _succ or EntryList must be visible before
    // the ST of null into _owner in the *subsequent* (following) corresponding
    // monitorexit.  Recall too, that in 1-0 mode monitorexit does not necessarily
    // execute a serializing instruction.

    if (SyncFlags & 8) {
       OrderAccess::fence() ;
    }
    return ;
}

// ExitSuspendEquivalent:
// A faster alternate to handle_special_suspend_equivalent_condition()
//
// handle_special_suspend_equivalent_condition() unconditionally
// acquires the SR_lock.  On some platforms uncontended MutexLocker()
// operations have high latency.  Note that in ::enter() we call HSSEC
// while holding the monitor, so we effectively lengthen the critical sections.
//
// There are a number of possible solutions:
//
// A.  To ameliorate the problem we might also defer state transitions
//     to as late as possible -- just prior to parking.
//     Given that, we'd call HSSEC after having returned from park(),
//     but before attempting to acquire the monitor.  This is only a
//     partial solution.  It avoids calling HSSEC while holding the
//     monitor (good), but it still increases successor reacquisition latency --
//     the interval between unparking a successor and the time the successor
//     resumes and retries the lock.  See ReenterI(), which defers state transitions.
//     If we use this technique we can also avoid EnterI()-exit() loop
//     in ::enter() where we iteratively drop the lock and then attempt
//     to reacquire it after suspending.
//
// B.  In the future we might fold all the suspend bits into a
//     composite per-thread suspend flag and then update it with CAS().
//     Alternately, a Dekker-like mechanism with multiple variables
//     would suffice:
//       ST Self->_suspend_equivalent = false
//       MEMBAR
//       LD Self_>_suspend_flags
//


bool ObjectMonitor::ExitSuspendEquivalent (JavaThread * jSelf) {
   int Mode = Knob_FastHSSEC ;
   if (Mode && !jSelf->is_external_suspend()) {
      assert (jSelf->is_suspend_equivalent(), "invariant") ;
      jSelf->clear_suspend_equivalent() ;
      if (2 == Mode) OrderAccess::storeload() ;
      if (!jSelf->is_external_suspend()) return false ;
      // We raced a suspension -- fall thru into the slow path
      TEVENT (ExitSuspendEquivalent - raced) ;
      jSelf->set_suspend_equivalent() ;
   }
   return jSelf->handle_special_suspend_equivalent_condition() ;
}


// ReenterI() is a specialized inline form of the latter half of the
// contended slow-path from EnterI().  We use ReenterI() only for
// monitor reentry in wait().
//
// In the future we should reconcile EnterI() and ReenterI(), adding
// Knob_Reset and Knob_SpinAfterFutile support and restructuring the
// loop accordingly.

void ATTR ObjectMonitor::ReenterI (Thread * Self, ObjectWaiter * SelfNode) {
    assert (Self != NULL                , "invariant") ;
    assert (SelfNode != NULL            , "invariant") ;
    assert (SelfNode->_thread == Self   , "invariant") ;
    assert (_waiters > 0                , "invariant") ;
    assert (((oop)(object()))->mark() == markOopDesc::encode(this) , "invariant") ;
    assert (((JavaThread *)Self)->thread_state() != _thread_blocked, "invariant") ;
    JavaThread * jt = (JavaThread *) Self ;

    int nWakeups = 0 ;
    for (;;) {
        ObjectWaiter::TStates v = SelfNode->TState ;
        guarantee (v == ObjectWaiter::TS_ENTER || v == ObjectWaiter::TS_CXQ, "invariant") ;
        assert    (_owner != Self, "invariant") ;

        if (TryLock (Self) > 0) break ;
        if (TrySpin (Self) > 0) break ;

        TEVENT (Wait Reentry - parking) ;

        // State transition wrappers around park() ...
        // ReenterI() wisely defers state transitions until
        // it's clear we must park the thread.
        {
           OSThreadContendState osts(Self->osthread());
           ThreadBlockInVM tbivm(jt);

           // cleared by handle_special_suspend_equivalent_condition()
           // or java_suspend_self()
           jt->set_suspend_equivalent();
           if (SyncFlags & 1) {
              Self->_ParkEvent->park ((jlong)1000) ;
           } else {
              Self->_ParkEvent->park () ;
           }

           // were we externally suspended while we were waiting?
           for (;;) {
              if (!ExitSuspendEquivalent (jt)) break ;
              if (_succ == Self) { _succ = NULL; OrderAccess::fence(); }
              jt->java_suspend_self();
              jt->set_suspend_equivalent();
           }
        }

        // Try again, but just so we distinguish between futile wakeups and
        // successful wakeups.  The following test isn't algorithmically
        // necessary, but it helps us maintain sensible statistics.
        if (TryLock(Self) > 0) break ;

        // The lock is still contested.
        // Keep a tally of the # of futile wakeups.
        // Note that the counter is not protected by a lock or updated by atomics.
        // That is by design - we trade "lossy" counters which are exposed to
        // races during updates for a lower probe effect.
        TEVENT (Wait Reentry - futile wakeup) ;
        ++ nWakeups ;

        // Assuming this is not a spurious wakeup we'll normally
        // find that _succ == Self.
        if (_succ == Self) _succ = NULL ;

        // Invariant: after clearing _succ a contending thread
        // *must* retry  _owner before parking.
        OrderAccess::fence() ;

        if (ObjectSynchronizer::_sync_FutileWakeups != NULL) {
          ObjectSynchronizer::_sync_FutileWakeups->inc() ;
        }
    }

    // Self has acquired the lock -- Unlink Self from the cxq or EntryList .
    // Normally we'll find Self on the EntryList.
    // Unlinking from the EntryList is constant-time and atomic-free.
    // From the perspective of the lock owner (this thread), the
    // EntryList is stable and cxq is prepend-only.
    // The head of cxq is volatile but the interior is stable.
    // In addition, Self.TState is stable.

    assert (_owner == Self, "invariant") ;
    assert (((oop)(object()))->mark() == markOopDesc::encode(this), "invariant") ;
    UnlinkAfterAcquire (Self, SelfNode) ;
    if (_succ == Self) _succ = NULL ;
    assert (_succ != Self, "invariant") ;
    SelfNode->TState = ObjectWaiter::TS_RUN ;
    OrderAccess::fence() ;      // see comments at the end of EnterI()
}

bool ObjectMonitor::try_enter(Thread* THREAD) {
  if (THREAD != _owner) {
    if (THREAD->is_lock_owned ((address)_owner)) {
       assert(_recursions == 0, "internal state error");
       _owner = THREAD ;
       _recursions = 1 ;
       OwnerIsThread = 1 ;
       return true;
    }
    if (Atomic::cmpxchg_ptr (THREAD, &_owner, NULL) != NULL) {
      return false;
    }
    return true;
  } else {
    _recursions++;
    return true;
  }
}

void ATTR ObjectMonitor::enter(TRAPS) {
  // The following code is ordered to check the most common cases first
  // and to reduce RTS->RTO cache line upgrades on SPARC and IA32 processors.
  Thread * const Self = THREAD ;
  void * cur ;

  cur = Atomic::cmpxchg_ptr (Self, &_owner, NULL) ;
  if (cur == NULL) {
     // Either ASSERT _recursions == 0 or explicitly set _recursions = 0.
     assert (_recursions == 0   , "invariant") ;
     assert (_owner      == Self, "invariant") ;
     // CONSIDER: set or assert OwnerIsThread == 1
     return ;
  }

  if (cur == Self) {
     // TODO-FIXME: check for integer overflow!  BUGID 6557169.
     _recursions ++ ;
     return ;
  }

  if (Self->is_lock_owned ((address)cur)) {
    assert (_recursions == 0, "internal state error");
    _recursions = 1 ;
    // Commute owner from a thread-specific on-stack BasicLockObject address to
    // a full-fledged "Thread *".
    _owner = Self ;
    OwnerIsThread = 1 ;
    return ;
  }

  // We've encountered genuine contention.
  assert (Self->_Stalled == 0, "invariant") ;
  Self->_Stalled = intptr_t(this) ;

  // Try one round of spinning *before* enqueueing Self
  // and before going through the awkward and expensive state
  // transitions.  The following spin is strictly optional ...
  // Note that if we acquire the monitor from an initial spin
  // we forgo posting JVMTI events and firing DTRACE probes.
  if (Knob_SpinEarly && TrySpin (Self) > 0) {
     assert (_owner == Self      , "invariant") ;
     assert (_recursions == 0    , "invariant") ;
     assert (((oop)(object()))->mark() == markOopDesc::encode(this), "invariant") ;
     Self->_Stalled = 0 ;
     return ;
  }

  assert (_owner != Self          , "invariant") ;
  assert (_succ  != Self          , "invariant") ;
  assert (Self->is_Java_thread()  , "invariant") ;
  JavaThread * jt = (JavaThread *) Self ;
  assert (!SafepointSynchronize::is_at_safepoint(), "invariant") ;
  assert (jt->thread_state() != _thread_blocked   , "invariant") ;
  assert (this->object() != NULL  , "invariant") ;
  assert (_count >= 0, "invariant") ;

  // Prevent deflation at STW-time.  See deflate_idle_monitors() and is_busy().
  // Ensure the object-monitor relationship remains stable while there's contention.
  Atomic::inc_ptr(&_count);

  { // Change java thread status to indicate blocked on monitor enter.
    JavaThreadBlockedOnMonitorEnterState jtbmes(jt, this);

    DTRACE_MONITOR_PROBE(contended__enter, this, object(), jt);
    if (JvmtiExport::should_post_monitor_contended_enter()) {
      JvmtiExport::post_monitor_contended_enter(jt, this);
    }

    OSThreadContendState osts(Self->osthread());
    ThreadBlockInVM tbivm(jt);

    Self->set_current_pending_monitor(this);

    // TODO-FIXME: change the following for(;;) loop to straight-line code.
    for (;;) {
      jt->set_suspend_equivalent();
      // cleared by handle_special_suspend_equivalent_condition()
      // or java_suspend_self()

      EnterI (THREAD) ;

      if (!ExitSuspendEquivalent(jt)) break ;

      //
      // We have acquired the contended monitor, but while we were
      // waiting another thread suspended us. We don't want to enter
      // the monitor while suspended because that would surprise the
      // thread that suspended us.
      //
          _recursions = 0 ;
      _succ = NULL ;
      exit (Self) ;

      jt->java_suspend_self();
    }
    Self->set_current_pending_monitor(NULL);
  }

  Atomic::dec_ptr(&_count);
  assert (_count >= 0, "invariant") ;
  Self->_Stalled = 0 ;

  // Must either set _recursions = 0 or ASSERT _recursions == 0.
  assert (_recursions == 0     , "invariant") ;
  assert (_owner == Self       , "invariant") ;
  assert (_succ  != Self       , "invariant") ;
  assert (((oop)(object()))->mark() == markOopDesc::encode(this), "invariant") ;

  // The thread -- now the owner -- is back in vm mode.
  // Report the glorious news via TI,DTrace and jvmstat.
  // The probe effect is non-trivial.  All the reportage occurs
  // while we hold the monitor, increasing the length of the critical
  // section.  Amdahl's parallel speedup law comes vividly into play.
  //
  // Another option might be to aggregate the events (thread local or
  // per-monitor aggregation) and defer reporting until a more opportune
  // time -- such as next time some thread encounters contention but has
  // yet to acquire the lock.  While spinning that thread could
  // spinning we could increment JVMStat counters, etc.

  DTRACE_MONITOR_PROBE(contended__entered, this, object(), jt);
  if (JvmtiExport::should_post_monitor_contended_entered()) {
    JvmtiExport::post_monitor_contended_entered(jt, this);
  }
  if (ObjectSynchronizer::_sync_ContendedLockAttempts != NULL) {
     ObjectSynchronizer::_sync_ContendedLockAttempts->inc() ;
  }
}

void ObjectMonitor::ExitEpilog (Thread * Self, ObjectWaiter * Wakee) {
   assert (_owner == Self, "invariant") ;

   // Exit protocol:
   // 1. ST _succ = wakee
   // 2. membar #loadstore|#storestore;
   // 2. ST _owner = NULL
   // 3. unpark(wakee)

   _succ = Knob_SuccEnabled ? Wakee->_thread : NULL ;
   ParkEvent * Trigger = Wakee->_event ;

   // Hygiene -- once we've set _owner = NULL we can't safely dereference Wakee again.
   // The thread associated with Wakee may have grabbed the lock and "Wakee" may be
   // out-of-scope (non-extant).
   Wakee  = NULL ;

   // Drop the lock
   OrderAccess::release_store_ptr (&_owner, NULL) ;
   OrderAccess::fence() ;                               // ST _owner vs LD in unpark()

   // TODO-FIXME:
   // If there's a safepoint pending the best policy would be to
   // get _this thread to a safepoint and only wake the successor
   // after the safepoint completed.  monitorexit uses a "leaf"
   // state transition, however, so this thread can't become
   // safe at this point in time.  (Its stack isn't walkable).
   // The next best thing is to defer waking the successor by
   // adding to a list of thread to be unparked after at the
   // end of the forthcoming STW).
   if (SafepointSynchronize::do_call_back()) {
      TEVENT (unpark before SAFEPOINT) ;
   }

   // Possible optimizations ...
   //
   // * Consider: set Wakee->UnparkTime = timeNow()
   //   When the thread wakes up it'll compute (timeNow() - Self->UnparkTime()).
   //   By measuring recent ONPROC latency we can approximate the
   //   system load.  In turn, we can feed that information back
   //   into the spinning & succession policies.
   //   (ONPROC latency correlates strongly with load).
   //
   // * Pull affinity:
   //   If the wakee is cold then transiently setting it's affinity
   //   to the current CPU is a good idea.
   //   See http://j2se.east/~dice/PERSIST/050624-PullAffinity.txt
   DTRACE_MONITOR_PROBE(contended__exit, this, object(), Self);
   Trigger->unpark() ;

   // Maintain stats and report events to JVMTI
   if (ObjectSynchronizer::_sync_Parks != NULL) {
      ObjectSynchronizer::_sync_Parks->inc() ;
   }
}


// exit()
// ~~~~~~
// Note that the collector can't reclaim the objectMonitor or deflate
// the object out from underneath the thread calling ::exit() as the
// thread calling ::exit() never transitions to a stable state.
// This inhibits GC, which in turn inhibits asynchronous (and
// inopportune) reclamation of "this".
//
// We'd like to assert that: (THREAD->thread_state() != _thread_blocked) ;
// There's one exception to the claim above, however.  EnterI() can call
// exit() to drop a lock if the acquirer has been externally suspended.
// In that case exit() is called with _thread_state as _thread_blocked,
// but the monitor's _count field is > 0, which inhibits reclamation.
//
// 1-0 exit
// ~~~~~~~~
// ::exit() uses a canonical 1-1 idiom with a MEMBAR although some of
// the fast-path operators have been optimized so the common ::exit()
// operation is 1-0.  See i486.ad fast_unlock(), for instance.
// The code emitted by fast_unlock() elides the usual MEMBAR.  This
// greatly improves latency -- MEMBAR and CAS having considerable local
// latency on modern processors -- but at the cost of "stranding".  Absent the
// MEMBAR, a thread in fast_unlock() can race a thread in the slow
// ::enter() path, resulting in the entering thread being stranding
// and a progress-liveness failure.   Stranding is extremely rare.
// We use timers (timed park operations) & periodic polling to detect
// and recover from stranding.  Potentially stranded threads periodically
// wake up and poll the lock.  See the usage of the _Responsible variable.
//
// The CAS() in enter provides for safety and exclusion, while the CAS or
// MEMBAR in exit provides for progress and avoids stranding.  1-0 locking
// eliminates the CAS/MEMBAR from the exist path, but it admits stranding.
// We detect and recover from stranding with timers.
//
// If a thread transiently strands it'll park until (a) another
// thread acquires the lock and then drops the lock, at which time the
// exiting thread will notice and unpark the stranded thread, or, (b)
// the timer expires.  If the lock is high traffic then the stranding latency
// will be low due to (a).  If the lock is low traffic then the odds of
// stranding are lower, although the worst-case stranding latency
// is longer.  Critically, we don't want to put excessive load in the
// platform's timer subsystem.  We want to minimize both the timer injection
// rate (timers created/sec) as well as the number of timers active at
// any one time.  (more precisely, we want to minimize timer-seconds, which is
// the integral of the # of active timers at any instant over time).
// Both impinge on OS scalability.  Given that, at most one thread parked on
// a monitor will use a timer.

void ATTR ObjectMonitor::exit(TRAPS) {
   Thread * Self = THREAD ;
   if (THREAD != _owner) {
     if (THREAD->is_lock_owned((address) _owner)) {
       // Transmute _owner from a BasicLock pointer to a Thread address.
       // We don't need to hold _mutex for this transition.
       // Non-null to Non-null is safe as long as all readers can
       // tolerate either flavor.
       assert (_recursions == 0, "invariant") ;
       _owner = THREAD ;
       _recursions = 0 ;
       OwnerIsThread = 1 ;
     } else {
       // NOTE: we need to handle unbalanced monitor enter/exit
       // in native code by throwing an exception.
       // TODO: Throw an IllegalMonitorStateException ?
       TEVENT (Exit - Throw IMSX) ;
       assert(false, "Non-balanced monitor enter/exit!");
       if (false) {
          THROW(vmSymbols::java_lang_IllegalMonitorStateException());
       }
       return;
     }
   }

   if (_recursions != 0) {
     _recursions--;        // this is simple recursive enter
     TEVENT (Inflated exit - recursive) ;
     return ;
   }

   // Invariant: after setting Responsible=null an thread must execute
   // a MEMBAR or other serializing instruction before fetching EntryList|cxq.
   if ((SyncFlags & 4) == 0) {
      _Responsible = NULL ;
   }

   for (;;) {
      assert (THREAD == _owner, "invariant") ;

      // Fast-path monitor exit:
      //
      // Observe the Dekker/Lamport duality:
      // A thread in ::exit() executes:
      //   ST Owner=null; MEMBAR; LD EntryList|cxq.
      // A thread in the contended ::enter() path executes the complementary:
      //   ST EntryList|cxq = nonnull; MEMBAR; LD Owner.
      //
      // Note that there's a benign race in the exit path.  We can drop the
      // lock, another thread can reacquire the lock immediately, and we can
      // then wake a thread unnecessarily (yet another flavor of futile wakeup).
      // This is benign, and we've structured the code so the windows are short
      // and the frequency of such futile wakeups is low.
      //
      // We could eliminate the race by encoding both the "LOCKED" state and
      // the queue head in a single word.  Exit would then use either CAS to
      // clear the LOCKED bit/byte.  This precludes the desirable 1-0 optimization,
      // however.
      //
      // Possible fast-path ::exit() optimization:
      // The current fast-path exit implementation fetches both cxq and EntryList.
      // See also i486.ad fast_unlock().  Testing has shown that two LDs
      // isn't measurably slower than a single LD on any platforms.
      // Still, we could reduce the 2 LDs to one or zero by one of the following:
      //
      // - Use _count instead of cxq|EntryList
      //   We intend to eliminate _count, however, when we switch
      //   to on-the-fly deflation in ::exit() as is used in
      //   Metalocks and RelaxedLocks.
      //
      // - Establish the invariant that cxq == null implies EntryList == null.
      //   set cxq == EMPTY (1) to encode the state where cxq is empty
      //   by EntryList != null.  EMPTY is a distinguished value.
      //   The fast-path exit() would fetch cxq but not EntryList.
      //
      // - Encode succ as follows:
      //   succ = t :  Thread t is the successor -- t is ready or is spinning.
      //               Exiting thread does not need to wake a successor.
      //   succ = 0 :  No successor required -> (EntryList|cxq) == null
      //               Exiting thread does not need to wake a successor
      //   succ = 1 :  Successor required    -> (EntryList|cxq) != null and
      //               logically succ == null.
      //               Exiting thread must wake a successor.
      //
      //   The 1-1 fast-exit path would appear as :
      //     _owner = null ; membar ;
      //     if (_succ == 1 && CAS (&_owner, null, Self) == null) goto SlowPath
      //     goto FastPathDone ;
      //
      //   and the 1-0 fast-exit path would appear as:
      //      if (_succ == 1) goto SlowPath
      //      Owner = null ;
      //      goto FastPathDone
      //
      // - Encode the LSB of _owner as 1 to indicate that exit()
      //   must use the slow-path and make a successor ready.
      //   (_owner & 1) == 0 IFF succ != null || (EntryList|cxq) == null
      //   (_owner & 1) == 0 IFF succ == null && (EntryList|cxq) != null (obviously)
      //   The 1-0 fast exit path would read:
      //      if (_owner != Self) goto SlowPath
      //      _owner = null
      //      goto FastPathDone

      if (Knob_ExitPolicy == 0) {
         // release semantics: prior loads and stores from within the critical section
         // must not float (reorder) past the following store that drops the lock.
         // On SPARC that requires MEMBAR #loadstore|#storestore.
         // But of course in TSO #loadstore|#storestore is not required.
         // I'd like to write one of the following:
         // A.  OrderAccess::release() ; _owner = NULL
         // B.  OrderAccess::loadstore(); OrderAccess::storestore(); _owner = NULL;
         // Unfortunately OrderAccess::release() and OrderAccess::loadstore() both
         // store into a _dummy variable.  That store is not needed, but can result
         // in massive wasteful coherency traffic on classic SMP systems.
         // Instead, I use release_store(), which is implemented as just a simple
         // ST on x64, x86 and SPARC.
         OrderAccess::release_store_ptr (&_owner, NULL) ;   // drop the lock
         OrderAccess::storeload() ;                         // See if we need to wake a successor
         if ((intptr_t(_EntryList)|intptr_t(_cxq)) == 0 || _succ != NULL) {
            TEVENT (Inflated exit - simple egress) ;
            return ;
         }
         TEVENT (Inflated exit - complex egress) ;

         // Normally the exiting thread is responsible for ensuring succession,
         // but if other successors are ready or other entering threads are spinning
         // then this thread can simply store NULL into _owner and exit without
         // waking a successor.  The existence of spinners or ready successors
         // guarantees proper succession (liveness).  Responsibility passes to the
         // ready or running successors.  The exiting thread delegates the duty.
         // More precisely, if a successor already exists this thread is absolved
         // of the responsibility of waking (unparking) one.
         //
         // The _succ variable is critical to reducing futile wakeup frequency.
         // _succ identifies the "heir presumptive" thread that has been made
         // ready (unparked) but that has not yet run.  We need only one such
         // successor thread to guarantee progress.
         // See http://www.usenix.org/events/jvm01/full_papers/dice/dice.pdf
         // section 3.3 "Futile Wakeup Throttling" for details.
         //
         // Note that spinners in Enter() also set _succ non-null.
         // In the current implementation spinners opportunistically set
         // _succ so that exiting threads might avoid waking a successor.
         // Another less appealing alternative would be for the exiting thread
         // to drop the lock and then spin briefly to see if a spinner managed
         // to acquire the lock.  If so, the exiting thread could exit
         // immediately without waking a successor, otherwise the exiting
         // thread would need to dequeue and wake a successor.
         // (Note that we'd need to make the post-drop spin short, but no
         // shorter than the worst-case round-trip cache-line migration time.
         // The dropped lock needs to become visible to the spinner, and then
         // the acquisition of the lock by the spinner must become visible to
         // the exiting thread).
         //

         // It appears that an heir-presumptive (successor) must be made ready.
         // Only the current lock owner can manipulate the EntryList or
         // drain _cxq, so we need to reacquire the lock.  If we fail
         // to reacquire the lock the responsibility for ensuring succession
         // falls to the new owner.
         //
         if (Atomic::cmpxchg_ptr (THREAD, &_owner, NULL) != NULL) {
            return ;
         }
         TEVENT (Exit - Reacquired) ;
      } else {
         if ((intptr_t(_EntryList)|intptr_t(_cxq)) == 0 || _succ != NULL) {
            OrderAccess::release_store_ptr (&_owner, NULL) ;   // drop the lock
            OrderAccess::storeload() ;
            // Ratify the previously observed values.
            if (_cxq == NULL || _succ != NULL) {
                TEVENT (Inflated exit - simple egress) ;
                return ;
            }

            // inopportune interleaving -- the exiting thread (this thread)
            // in the fast-exit path raced an entering thread in the slow-enter
            // path.
            // We have two choices:
            // A.  Try to reacquire the lock.
            //     If the CAS() fails return immediately, otherwise
            //     we either restart/rerun the exit operation, or simply
            //     fall-through into the code below which wakes a successor.
            // B.  If the elements forming the EntryList|cxq are TSM
            //     we could simply unpark() the lead thread and return
            //     without having set _succ.
            if (Atomic::cmpxchg_ptr (THREAD, &_owner, NULL) != NULL) {
               TEVENT (Inflated exit - reacquired succeeded) ;
               return ;
            }
            TEVENT (Inflated exit - reacquired failed) ;
         } else {
            TEVENT (Inflated exit - complex egress) ;
         }
      }

      guarantee (_owner == THREAD, "invariant") ;

      // Select an appropriate successor ("heir presumptive") from the EntryList
      // and make it ready.  Generally we just wake the head of EntryList .
      // There's no algorithmic constraint that we use the head - it's just
      // a policy decision.   Note that the thread at head of the EntryList
      // remains at the head until it acquires the lock.  This means we'll
      // repeatedly wake the same thread until it manages to grab the lock.
      // This is generally a good policy - if we're seeing lots of futile wakeups
      // at least we're waking/rewaking a thread that's like to be hot or warm
      // (have residual D$ and TLB affinity).
      //
      // "Wakeup locality" optimization:
      // http://j2se.east/~dice/PERSIST/040825-WakeLocality.txt
      // In the future we'll try to bias the selection mechanism
      // to preferentially pick a thread that recently ran on
      // a processor element that shares cache with the CPU on which
      // the exiting thread is running.   We need access to Solaris'
      // schedctl.sc_cpu to make that work.
      //
      ObjectWaiter * w = NULL ;
      int QMode = Knob_QMode ;

      if (QMode == 2 && _cxq != NULL) {
          // QMode == 2 : cxq has precedence over EntryList.
          // Try to directly wake a successor from the cxq.
          // If successful, the successor will need to unlink itself from cxq.
          w = _cxq ;
          assert (w != NULL, "invariant") ;
          assert (w->TState == ObjectWaiter::TS_CXQ, "Invariant") ;
          ExitEpilog (Self, w) ;
          return ;
      }

      if (QMode == 3 && _cxq != NULL) {
          // Aggressively drain cxq into EntryList at the first opportunity.
          // This policy ensure that recently-run threads live at the head of EntryList.
          // Drain _cxq into EntryList - bulk transfer.
          // First, detach _cxq.
          // The following loop is tantamount to: w = swap (&cxq, NULL)
          w = _cxq ;
          for (;;) {
             assert (w != NULL, "Invariant") ;
             ObjectWaiter * u = (ObjectWaiter *) Atomic::cmpxchg_ptr (NULL, &_cxq, w) ;
             if (u == w) break ;
             w = u ;
          }
          assert (w != NULL              , "invariant") ;

          ObjectWaiter * q = NULL ;
          ObjectWaiter * p ;
          for (p = w ; p != NULL ; p = p->_next) {
              guarantee (p->TState == ObjectWaiter::TS_CXQ, "Invariant") ;
              p->TState = ObjectWaiter::TS_ENTER ;
              p->_prev = q ;
              q = p ;
          }

          // Append the RATs to the EntryList
          // TODO: organize EntryList as a CDLL so we can locate the tail in constant-time.
          ObjectWaiter * Tail ;
          for (Tail = _EntryList ; Tail != NULL && Tail->_next != NULL ; Tail = Tail->_next) ;
          if (Tail == NULL) {
              _EntryList = w ;
          } else {
              Tail->_next = w ;
              w->_prev = Tail ;
          }

          // Fall thru into code that tries to wake a successor from EntryList
      }

      if (QMode == 4 && _cxq != NULL) {
          // Aggressively drain cxq into EntryList at the first opportunity.
          // This policy ensure that recently-run threads live at the head of EntryList.

          // Drain _cxq into EntryList - bulk transfer.
          // First, detach _cxq.
          // The following loop is tantamount to: w = swap (&cxq, NULL)
          w = _cxq ;
          for (;;) {
             assert (w != NULL, "Invariant") ;
             ObjectWaiter * u = (ObjectWaiter *) Atomic::cmpxchg_ptr (NULL, &_cxq, w) ;
             if (u == w) break ;
             w = u ;
          }
          assert (w != NULL              , "invariant") ;

          ObjectWaiter * q = NULL ;
          ObjectWaiter * p ;
          for (p = w ; p != NULL ; p = p->_next) {
              guarantee (p->TState == ObjectWaiter::TS_CXQ, "Invariant") ;
              p->TState = ObjectWaiter::TS_ENTER ;
              p->_prev = q ;
              q = p ;
          }

          // Prepend the RATs to the EntryList
          if (_EntryList != NULL) {
              q->_next = _EntryList ;
              _EntryList->_prev = q ;
          }
          _EntryList = w ;

          // Fall thru into code that tries to wake a successor from EntryList
      }

      w = _EntryList  ;
      if (w != NULL) {
          // I'd like to write: guarantee (w->_thread != Self).
          // But in practice an exiting thread may find itself on the EntryList.
          // Lets say thread T1 calls O.wait().  Wait() enqueues T1 on O's waitset and
          // then calls exit().  Exit release the lock by setting O._owner to NULL.
          // Lets say T1 then stalls.  T2 acquires O and calls O.notify().  The
          // notify() operation moves T1 from O's waitset to O's EntryList. T2 then
          // release the lock "O".  T2 resumes immediately after the ST of null into
          // _owner, above.  T2 notices that the EntryList is populated, so it
          // reacquires the lock and then finds itself on the EntryList.
          // Given all that, we have to tolerate the circumstance where "w" is
          // associated with Self.
          assert (w->TState == ObjectWaiter::TS_ENTER, "invariant") ;
          ExitEpilog (Self, w) ;
          return ;
      }

      // If we find that both _cxq and EntryList are null then just
      // re-run the exit protocol from the top.
      w = _cxq ;
      if (w == NULL) continue ;

      // Drain _cxq into EntryList - bulk transfer.
      // First, detach _cxq.
      // The following loop is tantamount to: w = swap (&cxq, NULL)
      for (;;) {
          assert (w != NULL, "Invariant") ;
          ObjectWaiter * u = (ObjectWaiter *) Atomic::cmpxchg_ptr (NULL, &_cxq, w) ;
          if (u == w) break ;
          w = u ;
      }
      TEVENT (Inflated exit - drain cxq into EntryList) ;

      assert (w != NULL              , "invariant") ;
      assert (_EntryList  == NULL    , "invariant") ;

      // Convert the LIFO SLL anchored by _cxq into a DLL.
      // The list reorganization step operates in O(LENGTH(w)) time.
      // It's critical that this step operate quickly as
      // "Self" still holds the outer-lock, restricting parallelism
      // and effectively lengthening the critical section.
      // Invariant: s chases t chases u.
      // TODO-FIXME: consider changing EntryList from a DLL to a CDLL so
      // we have faster access to the tail.

      if (QMode == 1) {
         // QMode == 1 : drain cxq to EntryList, reversing order
         // We also reverse the order of the list.
         ObjectWaiter * s = NULL ;
         ObjectWaiter * t = w ;
         ObjectWaiter * u = NULL ;
         while (t != NULL) {
             guarantee (t->TState == ObjectWaiter::TS_CXQ, "invariant") ;
             t->TState = ObjectWaiter::TS_ENTER ;
             u = t->_next ;
             t->_prev = u ;
             t->_next = s ;
             s = t;
             t = u ;
         }
         _EntryList  = s ;
         assert (s != NULL, "invariant") ;
      } else {
         // QMode == 0 or QMode == 2
         _EntryList = w ;
         ObjectWaiter * q = NULL ;
         ObjectWaiter * p ;
         for (p = w ; p != NULL ; p = p->_next) {
             guarantee (p->TState == ObjectWaiter::TS_CXQ, "Invariant") ;
             p->TState = ObjectWaiter::TS_ENTER ;
             p->_prev = q ;
             q = p ;
         }
      }

      // In 1-0 mode we need: ST EntryList; MEMBAR #storestore; ST _owner = NULL
      // The MEMBAR is satisfied by the release_store() operation in ExitEpilog().

      // See if we can abdicate to a spinner instead of waking a thread.
      // A primary goal of the implementation is to reduce the
      // context-switch rate.
      if (_succ != NULL) continue;

      w = _EntryList  ;
      if (w != NULL) {
          guarantee (w->TState == ObjectWaiter::TS_ENTER, "invariant") ;
          ExitEpilog (Self, w) ;
          return ;
      }
   }
}
// complete_exit exits a lock returning recursion count
// complete_exit/reenter operate as a wait without waiting
// complete_exit requires an inflated monitor
// The _owner field is not always the Thread addr even with an
// inflated monitor, e.g. the monitor can be inflated by a non-owning
// thread due to contention.
intptr_t ObjectMonitor::complete_exit(TRAPS) {
   Thread * const Self = THREAD;
   assert(Self->is_Java_thread(), "Must be Java thread!");
   JavaThread *jt = (JavaThread *)THREAD;

   DeferredInitialize();

   if (THREAD != _owner) {
    if (THREAD->is_lock_owned ((address)_owner)) {
       assert(_recursions == 0, "internal state error");
       _owner = THREAD ;   /* Convert from basiclock addr to Thread addr */
       _recursions = 0 ;
       OwnerIsThread = 1 ;
    }
   }

   guarantee(Self == _owner, "complete_exit not owner");
   intptr_t save = _recursions; // record the old recursion count
   _recursions = 0;        // set the recursion level to be 0
   exit (Self) ;           // exit the monitor
   guarantee (_owner != Self, "invariant");
   return save;
}

// reenter() enters a lock and sets recursion count
// complete_exit/reenter operate as a wait without waiting
void ObjectMonitor::reenter(intptr_t recursions, TRAPS) {
   Thread * const Self = THREAD;
   assert(Self->is_Java_thread(), "Must be Java thread!");
   JavaThread *jt = (JavaThread *)THREAD;

   guarantee(_owner != Self, "reenter already owner");
   enter (THREAD);       // enter the monitor
   guarantee (_recursions == 0, "reenter recursion");
   _recursions = recursions;
   return;
}

// Note: a subset of changes to ObjectMonitor::wait()
// will need to be replicated in complete_exit above
void ObjectMonitor::wait(jlong millis, bool interruptible, TRAPS) {
   Thread * const Self = THREAD ;
   assert(Self->is_Java_thread(), "Must be Java thread!");
   JavaThread *jt = (JavaThread *)THREAD;

   DeferredInitialize () ;

   // Throw IMSX or IEX.
   CHECK_OWNER();

   // check for a pending interrupt
   if (interruptible && Thread::is_interrupted(Self, true) && !HAS_PENDING_EXCEPTION) {
     // post monitor waited event.  Note that this is past-tense, we are done waiting.
     if (JvmtiExport::should_post_monitor_waited()) {
        // Note: 'false' parameter is passed here because the
        // wait was not timed out due to thread interrupt.
        JvmtiExport::post_monitor_waited(jt, this, false);
     }
     TEVENT (Wait - Throw IEX) ;
     THROW(vmSymbols::java_lang_InterruptedException());
     return ;
   }
   TEVENT (Wait) ;

   assert (Self->_Stalled == 0, "invariant") ;
   Self->_Stalled = intptr_t(this) ;
   jt->set_current_waiting_monitor(this);

   // create a node to be put into the queue
   // Critically, after we reset() the event but prior to park(), we must check
   // for a pending interrupt.
   ObjectWaiter node(Self);
   node.TState = ObjectWaiter::TS_WAIT ;
   Self->_ParkEvent->reset() ;
   OrderAccess::fence();          // ST into Event; membar ; LD interrupted-flag

   // Enter the waiting queue, which is a circular doubly linked list in this case
   // but it could be a priority queue or any data structure.
   // _WaitSetLock protects the wait queue.  Normally the wait queue is accessed only
   // by the the owner of the monitor *except* in the case where park()
   // returns because of a timeout of interrupt.  Contention is exceptionally rare
   // so we use a simple spin-lock instead of a heavier-weight blocking lock.

   Thread::SpinAcquire (&_WaitSetLock, "WaitSet - add") ;
   AddWaiter (&node) ;
   Thread::SpinRelease (&_WaitSetLock) ;

   if ((SyncFlags & 4) == 0) {
      _Responsible = NULL ;
   }
   intptr_t save = _recursions; // record the old recursion count
   _waiters++;                  // increment the number of waiters
   _recursions = 0;             // set the recursion level to be 1
   exit (Self) ;                    // exit the monitor
   guarantee (_owner != Self, "invariant") ;

   // As soon as the ObjectMonitor's ownership is dropped in the exit()
   // call above, another thread can enter() the ObjectMonitor, do the
   // notify(), and exit() the ObjectMonitor. If the other thread's
   // exit() call chooses this thread as the successor and the unpark()
   // call happens to occur while this thread is posting a
   // MONITOR_CONTENDED_EXIT event, then we run the risk of the event
   // handler using RawMonitors and consuming the unpark().
   //
   // To avoid the problem, we re-post the event. This does no harm
   // even if the original unpark() was not consumed because we are the
   // chosen successor for this monitor.
   if (node._notified != 0 && _succ == Self) {
      node._event->unpark();
   }

   // The thread is on the WaitSet list - now park() it.
   // On MP systems it's conceivable that a brief spin before we park
   // could be profitable.
   //
   // TODO-FIXME: change the following logic to a loop of the form
   //   while (!timeout && !interrupted && _notified == 0) park()

   int ret = OS_OK ;
   int WasNotified = 0 ;
   { // State transition wrappers
     OSThread* osthread = Self->osthread();
     OSThreadWaitState osts(osthread, true);
     {
       ThreadBlockInVM tbivm(jt);
       // Thread is in thread_blocked state and oop access is unsafe.
       jt->set_suspend_equivalent();

       if (interruptible && (Thread::is_interrupted(THREAD, false) || HAS_PENDING_EXCEPTION)) {
           // Intentionally empty
       } else
       if (node._notified == 0) {
         if (millis <= 0) {
            Self->_ParkEvent->park () ;
         } else {
            ret = Self->_ParkEvent->park (millis) ;
         }
       }

       // were we externally suspended while we were waiting?
       if (ExitSuspendEquivalent (jt)) {
          // TODO-FIXME: add -- if succ == Self then succ = null.
          jt->java_suspend_self();
       }

     } // Exit thread safepoint: transition _thread_blocked -> _thread_in_vm


     // Node may be on the WaitSet, the EntryList (or cxq), or in transition
     // from the WaitSet to the EntryList.
     // See if we need to remove Node from the WaitSet.
     // We use double-checked locking to avoid grabbing _WaitSetLock
     // if the thread is not on the wait queue.
     //
     // Note that we don't need a fence before the fetch of TState.
     // In the worst case we'll fetch a old-stale value of TS_WAIT previously
     // written by the is thread. (perhaps the fetch might even be satisfied
     // by a look-aside into the processor's own store buffer, although given
     // the length of the code path between the prior ST and this load that's
     // highly unlikely).  If the following LD fetches a stale TS_WAIT value
     // then we'll acquire the lock and then re-fetch a fresh TState value.
     // That is, we fail toward safety.

     if (node.TState == ObjectWaiter::TS_WAIT) {
         Thread::SpinAcquire (&_WaitSetLock, "WaitSet - unlink") ;
         if (node.TState == ObjectWaiter::TS_WAIT) {
            DequeueSpecificWaiter (&node) ;       // unlink from WaitSet
            assert(node._notified == 0, "invariant");
            node.TState = ObjectWaiter::TS_RUN ;
         }
         Thread::SpinRelease (&_WaitSetLock) ;
     }

     // The thread is now either on off-list (TS_RUN),
     // on the EntryList (TS_ENTER), or on the cxq (TS_CXQ).
     // The Node's TState variable is stable from the perspective of this thread.
     // No other threads will asynchronously modify TState.
     guarantee (node.TState != ObjectWaiter::TS_WAIT, "invariant") ;
     OrderAccess::loadload() ;
     if (_succ == Self) _succ = NULL ;
     WasNotified = node._notified ;

     // Reentry phase -- reacquire the monitor.
     // re-enter contended monitor after object.wait().
     // retain OBJECT_WAIT state until re-enter successfully completes
     // Thread state is thread_in_vm and oop access is again safe,
     // although the raw address of the object may have changed.
     // (Don't cache naked oops over safepoints, of course).

     // post monitor waited event. Note that this is past-tense, we are done waiting.
     if (JvmtiExport::should_post_monitor_waited()) {
       JvmtiExport::post_monitor_waited(jt, this, ret == OS_TIMEOUT);
     }
     OrderAccess::fence() ;

     assert (Self->_Stalled != 0, "invariant") ;
     Self->_Stalled = 0 ;

     assert (_owner != Self, "invariant") ;
     ObjectWaiter::TStates v = node.TState ;
     if (v == ObjectWaiter::TS_RUN) {
         enter (Self) ;
     } else {
         guarantee (v == ObjectWaiter::TS_ENTER || v == ObjectWaiter::TS_CXQ, "invariant") ;
         ReenterI (Self, &node) ;
         node.wait_reenter_end(this);
     }

     // Self has reacquired the lock.
     // Lifecycle - the node representing Self must not appear on any queues.
     // Node is about to go out-of-scope, but even if it were immortal we wouldn't
     // want residual elements associated with this thread left on any lists.
     guarantee (node.TState == ObjectWaiter::TS_RUN, "invariant") ;
     assert    (_owner == Self, "invariant") ;
     assert    (_succ != Self , "invariant") ;
   } // OSThreadWaitState()

   jt->set_current_waiting_monitor(NULL);

   guarantee (_recursions == 0, "invariant") ;
   _recursions = save;     // restore the old recursion count
   _waiters--;             // decrement the number of waiters

   // Verify a few postconditions
   assert (_owner == Self       , "invariant") ;
   assert (_succ  != Self       , "invariant") ;
   assert (((oop)(object()))->mark() == markOopDesc::encode(this), "invariant") ;

   if (SyncFlags & 32) {
      OrderAccess::fence() ;
   }

   // check if the notification happened
   if (!WasNotified) {
     // no, it could be timeout or Thread.interrupt() or both
     // check for interrupt event, otherwise it is timeout
     if (interruptible && Thread::is_interrupted(Self, true) && !HAS_PENDING_EXCEPTION) {
       TEVENT (Wait - throw IEX from epilog) ;
       THROW(vmSymbols::java_lang_InterruptedException());
     }
   }

   // NOTE: Spurious wake up will be consider as timeout.
   // Monitor notify has precedence over thread interrupt.
}


// Consider:
// If the lock is cool (cxq == null && succ == null) and we're on an MP system
// then instead of transferring a thread from the WaitSet to the EntryList
// we might just dequeue a thread from the WaitSet and directly unpark() it.

void ObjectMonitor::notify(TRAPS) {
  CHECK_OWNER();
  if (_WaitSet == NULL) {
     TEVENT (Empty-Notify) ;
     return ;
  }
  DTRACE_MONITOR_PROBE(notify, this, object(), THREAD);

  int Policy = Knob_MoveNotifyee ;

  Thread::SpinAcquire (&_WaitSetLock, "WaitSet - notify") ;
  ObjectWaiter * iterator = DequeueWaiter() ;
  if (iterator != NULL) {
     TEVENT (Notify1 - Transfer) ;
     guarantee (iterator->TState == ObjectWaiter::TS_WAIT, "invariant") ;
     guarantee (iterator->_notified == 0, "invariant") ;
     // Disposition - what might we do with iterator ?
     // a.  add it directly to the EntryList - either tail or head.
     // b.  push it onto the front of the _cxq.
     // For now we use (a).
     if (Policy != 4) {
        iterator->TState = ObjectWaiter::TS_ENTER ;
     }
     iterator->_notified = 1 ;

     ObjectWaiter * List = _EntryList ;
     if (List != NULL) {
        assert (List->_prev == NULL, "invariant") ;
        assert (List->TState == ObjectWaiter::TS_ENTER, "invariant") ;
        assert (List != iterator, "invariant") ;
     }

     if (Policy == 0) {       // prepend to EntryList
         if (List == NULL) {
             iterator->_next = iterator->_prev = NULL ;
             _EntryList = iterator ;
         } else {
             List->_prev = iterator ;
             iterator->_next = List ;
             iterator->_prev = NULL ;
             _EntryList = iterator ;
        }
     } else
     if (Policy == 1) {      // append to EntryList
         if (List == NULL) {
             iterator->_next = iterator->_prev = NULL ;
             _EntryList = iterator ;
         } else {
            // CONSIDER:  finding the tail currently requires a linear-time walk of
            // the EntryList.  We can make tail access constant-time by converting to
            // a CDLL instead of using our current DLL.
            ObjectWaiter * Tail ;
            for (Tail = List ; Tail->_next != NULL ; Tail = Tail->_next) ;
            assert (Tail != NULL && Tail->_next == NULL, "invariant") ;
            Tail->_next = iterator ;
            iterator->_prev = Tail ;
            iterator->_next = NULL ;
        }
     } else
     if (Policy == 2) {      // prepend to cxq
         // prepend to cxq
         if (List == NULL) {
             iterator->_next = iterator->_prev = NULL ;
             _EntryList = iterator ;
         } else {
            iterator->TState = ObjectWaiter::TS_CXQ ;
            for (;;) {
                ObjectWaiter * Front = _cxq ;
                iterator->_next = Front ;
                if (Atomic::cmpxchg_ptr (iterator, &_cxq, Front) == Front) {
                    break ;
                }
            }
         }
     } else
     if (Policy == 3) {      // append to cxq
        iterator->TState = ObjectWaiter::TS_CXQ ;
        for (;;) {
            ObjectWaiter * Tail ;
            Tail = _cxq ;
            if (Tail == NULL) {
                iterator->_next = NULL ;
                if (Atomic::cmpxchg_ptr (iterator, &_cxq, NULL) == NULL) {
                   break ;
                }
            } else {
                while (Tail->_next != NULL) Tail = Tail->_next ;
                Tail->_next = iterator ;
                iterator->_prev = Tail ;
                iterator->_next = NULL ;
                break ;
            }
        }
     } else {
        ParkEvent * ev = iterator->_event ;
        iterator->TState = ObjectWaiter::TS_RUN ;
        OrderAccess::fence() ;
        ev->unpark() ;
     }

     if (Policy < 4) {
       iterator->wait_reenter_begin(this);
     }

     // _WaitSetLock protects the wait queue, not the EntryList.  We could
     // move the add-to-EntryList operation, above, outside the critical section
     // protected by _WaitSetLock.  In practice that's not useful.  With the
     // exception of  wait() timeouts and interrupts the monitor owner
     // is the only thread that grabs _WaitSetLock.  There's almost no contention
     // on _WaitSetLock so it's not profitable to reduce the length of the
     // critical section.
  }

  Thread::SpinRelease (&_WaitSetLock) ;

  if (iterator != NULL && ObjectSynchronizer::_sync_Notifications != NULL) {
     ObjectSynchronizer::_sync_Notifications->inc() ;
  }
}


void ObjectMonitor::notifyAll(TRAPS) {
  CHECK_OWNER();
  ObjectWaiter* iterator;
  if (_WaitSet == NULL) {
      TEVENT (Empty-NotifyAll) ;
      return ;
  }
  DTRACE_MONITOR_PROBE(notifyAll, this, object(), THREAD);

  int Policy = Knob_MoveNotifyee ;
  int Tally = 0 ;
  Thread::SpinAcquire (&_WaitSetLock, "WaitSet - notifyall") ;

  for (;;) {
     iterator = DequeueWaiter () ;
     if (iterator == NULL) break ;
     TEVENT (NotifyAll - Transfer1) ;
     ++Tally ;

     // Disposition - what might we do with iterator ?
     // a.  add it directly to the EntryList - either tail or head.
     // b.  push it onto the front of the _cxq.
     // For now we use (a).
     //
     // TODO-FIXME: currently notifyAll() transfers the waiters one-at-a-time from the waitset
     // to the EntryList.  This could be done more efficiently with a single bulk transfer,
     // but in practice it's not time-critical.  Beware too, that in prepend-mode we invert the
     // order of the waiters.  Lets say that the waitset is "ABCD" and the EntryList is "XYZ".
     // After a notifyAll() in prepend mode the waitset will be empty and the EntryList will
     // be "DCBAXYZ".

     guarantee (iterator->TState == ObjectWaiter::TS_WAIT, "invariant") ;
     guarantee (iterator->_notified == 0, "invariant") ;
     iterator->_notified = 1 ;
     if (Policy != 4) {
        iterator->TState = ObjectWaiter::TS_ENTER ;
     }

     ObjectWaiter * List = _EntryList ;
     if (List != NULL) {
        assert (List->_prev == NULL, "invariant") ;
        assert (List->TState == ObjectWaiter::TS_ENTER, "invariant") ;
        assert (List != iterator, "invariant") ;
     }

     if (Policy == 0) {       // prepend to EntryList
         if (List == NULL) {
             iterator->_next = iterator->_prev = NULL ;
             _EntryList = iterator ;
         } else {
             List->_prev = iterator ;
             iterator->_next = List ;
             iterator->_prev = NULL ;
             _EntryList = iterator ;
        }
     } else
     if (Policy == 1) {      // append to EntryList
         if (List == NULL) {
             iterator->_next = iterator->_prev = NULL ;
             _EntryList = iterator ;
         } else {
            // CONSIDER:  finding the tail currently requires a linear-time walk of
            // the EntryList.  We can make tail access constant-time by converting to
            // a CDLL instead of using our current DLL.
            ObjectWaiter * Tail ;
            for (Tail = List ; Tail->_next != NULL ; Tail = Tail->_next) ;
            assert (Tail != NULL && Tail->_next == NULL, "invariant") ;
            Tail->_next = iterator ;
            iterator->_prev = Tail ;
            iterator->_next = NULL ;
        }
     } else
     if (Policy == 2) {      // prepend to cxq
         // prepend to cxq
         iterator->TState = ObjectWaiter::TS_CXQ ;
         for (;;) {
             ObjectWaiter * Front = _cxq ;
             iterator->_next = Front ;
             if (Atomic::cmpxchg_ptr (iterator, &_cxq, Front) == Front) {
                 break ;
             }
         }
     } else
     if (Policy == 3) {      // append to cxq
        iterator->TState = ObjectWaiter::TS_CXQ ;
        for (;;) {
            ObjectWaiter * Tail ;
            Tail = _cxq ;
            if (Tail == NULL) {
                iterator->_next = NULL ;
                if (Atomic::cmpxchg_ptr (iterator, &_cxq, NULL) == NULL) {
                   break ;
                }
            } else {
                while (Tail->_next != NULL) Tail = Tail->_next ;
                Tail->_next = iterator ;
                iterator->_prev = Tail ;
                iterator->_next = NULL ;
                break ;
            }
        }
     } else {
        ParkEvent * ev = iterator->_event ;
        iterator->TState = ObjectWaiter::TS_RUN ;
        OrderAccess::fence() ;
        ev->unpark() ;
     }

     if (Policy < 4) {
       iterator->wait_reenter_begin(this);
     }

     // _WaitSetLock protects the wait queue, not the EntryList.  We could
     // move the add-to-EntryList operation, above, outside the critical section
     // protected by _WaitSetLock.  In practice that's not useful.  With the
     // exception of  wait() timeouts and interrupts the monitor owner
     // is the only thread that grabs _WaitSetLock.  There's almost no contention
     // on _WaitSetLock so it's not profitable to reduce the length of the
     // critical section.
  }

  Thread::SpinRelease (&_WaitSetLock) ;

  if (Tally != 0 && ObjectSynchronizer::_sync_Notifications != NULL) {
     ObjectSynchronizer::_sync_Notifications->inc(Tally) ;
  }
}

// check_slow() is a misnomer.  It's called to simply to throw an IMSX exception.
// TODO-FIXME: remove check_slow() -- it's likely dead.

void ObjectMonitor::check_slow(TRAPS) {
  TEVENT (check_slow - throw IMSX) ;
  assert(THREAD != _owner && !THREAD->is_lock_owned((address) _owner), "must not be owner");
  THROW_MSG(vmSymbols::java_lang_IllegalMonitorStateException(), "current thread not owner");
}


// -------------------------------------------------------------------------
// The raw monitor subsystem is entirely distinct from normal
// java-synchronization or jni-synchronization.  raw monitors are not
// associated with objects.  They can be implemented in any manner
// that makes sense.  The original implementors decided to piggy-back
// the raw-monitor implementation on the existing Java objectMonitor mechanism.
// This flaw needs to fixed.  We should reimplement raw monitors as sui-generis.
// Specifically, we should not implement raw monitors via java monitors.
// Time permitting, we should disentangle and deconvolve the two implementations
// and move the resulting raw monitor implementation over to the JVMTI directories.
// Ideally, the raw monitor implementation would be built on top of
// park-unpark and nothing else.
//
// raw monitors are used mainly by JVMTI
// The raw monitor implementation borrows the ObjectMonitor structure,
// but the operators are degenerate and extremely simple.
//
// Mixed use of a single objectMonitor instance -- as both a raw monitor
// and a normal java monitor -- is not permissible.
//
// Note that we use the single RawMonitor_lock to protect queue operations for
// _all_ raw monitors.  This is a scalability impediment, but since raw monitor usage
// is deprecated and rare, this is not of concern.  The RawMonitor_lock can not
// be held indefinitely.  The critical sections must be short and bounded.
//
// -------------------------------------------------------------------------

int ObjectMonitor::SimpleEnter (Thread * Self) {
  for (;;) {
    if (Atomic::cmpxchg_ptr (Self, &_owner, NULL) == NULL) {
       return OS_OK ;
    }

    ObjectWaiter Node (Self) ;
    Self->_ParkEvent->reset() ;     // strictly optional
    Node.TState = ObjectWaiter::TS_ENTER ;

    RawMonitor_lock->lock_without_safepoint_check() ;
    Node._next  = _EntryList ;
    _EntryList  = &Node ;
    OrderAccess::fence() ;
    if (_owner == NULL && Atomic::cmpxchg_ptr (Self, &_owner, NULL) == NULL) {
        _EntryList = Node._next ;
        RawMonitor_lock->unlock() ;
        return OS_OK ;
    }
    RawMonitor_lock->unlock() ;
    while (Node.TState == ObjectWaiter::TS_ENTER) {
       Self->_ParkEvent->park() ;
    }
  }
}

int ObjectMonitor::SimpleExit (Thread * Self) {
  guarantee (_owner == Self, "invariant") ;
  OrderAccess::release_store_ptr (&_owner, NULL) ;
  OrderAccess::fence() ;
  if (_EntryList == NULL) return OS_OK ;
  ObjectWaiter * w ;

  RawMonitor_lock->lock_without_safepoint_check() ;
  w = _EntryList ;
  if (w != NULL) {
      _EntryList = w->_next ;
  }
  RawMonitor_lock->unlock() ;
  if (w != NULL) {
      guarantee (w ->TState == ObjectWaiter::TS_ENTER, "invariant") ;
      ParkEvent * ev = w->_event ;
      w->TState = ObjectWaiter::TS_RUN ;
      OrderAccess::fence() ;
      ev->unpark() ;
  }
  return OS_OK ;
}

int ObjectMonitor::SimpleWait (Thread * Self, jlong millis) {
  guarantee (_owner == Self  , "invariant") ;
  guarantee (_recursions == 0, "invariant") ;

  ObjectWaiter Node (Self) ;
  Node._notified = 0 ;
  Node.TState    = ObjectWaiter::TS_WAIT ;

  RawMonitor_lock->lock_without_safepoint_check() ;
  Node._next     = _WaitSet ;
  _WaitSet       = &Node ;
  RawMonitor_lock->unlock() ;

  SimpleExit (Self) ;
  guarantee (_owner != Self, "invariant") ;

  int ret = OS_OK ;
  if (millis <= 0) {
    Self->_ParkEvent->park();
  } else {
    ret = Self->_ParkEvent->park(millis);
  }

  // If thread still resides on the waitset then unlink it.
  // Double-checked locking -- the usage is safe in this context
  // as we TState is volatile and the lock-unlock operators are
  // serializing (barrier-equivalent).

  if (Node.TState == ObjectWaiter::TS_WAIT) {
    RawMonitor_lock->lock_without_safepoint_check() ;
    if (Node.TState == ObjectWaiter::TS_WAIT) {
      // Simple O(n) unlink, but performance isn't critical here.
      ObjectWaiter * p ;
      ObjectWaiter * q = NULL ;
      for (p = _WaitSet ; p != &Node; p = p->_next) {
         q = p ;
      }
      guarantee (p == &Node, "invariant") ;
      if (q == NULL) {
        guarantee (p == _WaitSet, "invariant") ;
        _WaitSet = p->_next ;
      } else {
        guarantee (p == q->_next, "invariant") ;
        q->_next = p->_next ;
      }
      Node.TState = ObjectWaiter::TS_RUN ;
    }
    RawMonitor_lock->unlock() ;
  }

  guarantee (Node.TState == ObjectWaiter::TS_RUN, "invariant") ;
  SimpleEnter (Self) ;

  guarantee (_owner == Self, "invariant") ;
  guarantee (_recursions == 0, "invariant") ;
  return ret ;
}

int ObjectMonitor::SimpleNotify (Thread * Self, bool All) {
  guarantee (_owner == Self, "invariant") ;
  if (_WaitSet == NULL) return OS_OK ;

  // We have two options:
  // A. Transfer the threads from the WaitSet to the EntryList
  // B. Remove the thread from the WaitSet and unpark() it.
  //
  // We use (B), which is crude and results in lots of futile
  // context switching.  In particular (B) induces lots of contention.

  ParkEvent * ev = NULL ;       // consider using a small auto array ...
  RawMonitor_lock->lock_without_safepoint_check() ;
  for (;;) {
      ObjectWaiter * w = _WaitSet ;
      if (w == NULL) break ;
      _WaitSet = w->_next ;
      if (ev != NULL) { ev->unpark(); ev = NULL; }
      ev = w->_event ;
      OrderAccess::loadstore() ;
      w->TState = ObjectWaiter::TS_RUN ;
      OrderAccess::storeload();
      if (!All) break ;
  }
  RawMonitor_lock->unlock() ;
  if (ev != NULL) ev->unpark();
  return OS_OK ;
}

// Any JavaThread will enter here with state _thread_blocked
int ObjectMonitor::raw_enter(TRAPS) {
  TEVENT (raw_enter) ;
  void * Contended ;

  // don't enter raw monitor if thread is being externally suspended, it will
  // surprise the suspender if a "suspended" thread can still enter monitor
  JavaThread * jt = (JavaThread *)THREAD;
  if (THREAD->is_Java_thread()) {
    jt->SR_lock()->lock_without_safepoint_check();
    while (jt->is_external_suspend()) {
      jt->SR_lock()->unlock();
      jt->java_suspend_self();
      jt->SR_lock()->lock_without_safepoint_check();
    }
    // guarded by SR_lock to avoid racing with new external suspend requests.
    Contended = Atomic::cmpxchg_ptr (THREAD, &_owner, NULL) ;
    jt->SR_lock()->unlock();
  } else {
    Contended = Atomic::cmpxchg_ptr (THREAD, &_owner, NULL) ;
  }

  if (Contended == THREAD) {
     _recursions ++ ;
     return OM_OK ;
  }

  if (Contended == NULL) {
     guarantee (_owner == THREAD, "invariant") ;
     guarantee (_recursions == 0, "invariant") ;
     return OM_OK ;
  }

  THREAD->set_current_pending_monitor(this);

  if (!THREAD->is_Java_thread()) {
     // No other non-Java threads besides VM thread would acquire
     // a raw monitor.
     assert(THREAD->is_VM_thread(), "must be VM thread");
     SimpleEnter (THREAD) ;
   } else {
     guarantee (jt->thread_state() == _thread_blocked, "invariant") ;
     for (;;) {
       jt->set_suspend_equivalent();
       // cleared by handle_special_suspend_equivalent_condition() or
       // java_suspend_self()
       SimpleEnter (THREAD) ;

       // were we externally suspended while we were waiting?
       if (!jt->handle_special_suspend_equivalent_condition()) break ;

       // This thread was externally suspended
       //
       // This logic isn't needed for JVMTI raw monitors,
       // but doesn't hurt just in case the suspend rules change. This
           // logic is needed for the ObjectMonitor.wait() reentry phase.
           // We have reentered the contended monitor, but while we were
           // waiting another thread suspended us. We don't want to reenter
           // the monitor while suspended because that would surprise the
           // thread that suspended us.
           //
           // Drop the lock -
       SimpleExit (THREAD) ;

           jt->java_suspend_self();
         }

     assert(_owner == THREAD, "Fatal error with monitor owner!");
     assert(_recursions == 0, "Fatal error with monitor recursions!");
  }

  THREAD->set_current_pending_monitor(NULL);
  guarantee (_recursions == 0, "invariant") ;
  return OM_OK;
}

// Used mainly for JVMTI raw monitor implementation
// Also used for ObjectMonitor::wait().
int ObjectMonitor::raw_exit(TRAPS) {
  TEVENT (raw_exit) ;
  if (THREAD != _owner) {
    return OM_ILLEGAL_MONITOR_STATE;
  }
  if (_recursions > 0) {
    --_recursions ;
    return OM_OK ;
  }

  void * List = _EntryList ;
  SimpleExit (THREAD) ;

  return OM_OK;
}

// Used for JVMTI raw monitor implementation.
// All JavaThreads will enter here with state _thread_blocked

int ObjectMonitor::raw_wait(jlong millis, bool interruptible, TRAPS) {
  TEVENT (raw_wait) ;
  if (THREAD != _owner) {
    return OM_ILLEGAL_MONITOR_STATE;
  }

  // To avoid spurious wakeups we reset the parkevent -- This is strictly optional.
  // The caller must be able to tolerate spurious returns from raw_wait().
  THREAD->_ParkEvent->reset() ;
  OrderAccess::fence() ;

  // check interrupt event
  if (interruptible && Thread::is_interrupted(THREAD, true)) {
    return OM_INTERRUPTED;
  }

  intptr_t save = _recursions ;
  _recursions = 0 ;
  _waiters ++ ;
  if (THREAD->is_Java_thread()) {
    guarantee (((JavaThread *) THREAD)->thread_state() == _thread_blocked, "invariant") ;
    ((JavaThread *)THREAD)->set_suspend_equivalent();
  }
  int rv = SimpleWait (THREAD, millis) ;
  _recursions = save ;
  _waiters -- ;

  guarantee (THREAD == _owner, "invariant") ;
  if (THREAD->is_Java_thread()) {
     JavaThread * jSelf = (JavaThread *) THREAD ;
     for (;;) {
        if (!jSelf->handle_special_suspend_equivalent_condition()) break ;
        SimpleExit (THREAD) ;
        jSelf->java_suspend_self();
        SimpleEnter (THREAD) ;
        jSelf->set_suspend_equivalent() ;
     }
  }
  guarantee (THREAD == _owner, "invariant") ;

  if (interruptible && Thread::is_interrupted(THREAD, true)) {
    return OM_INTERRUPTED;
  }
  return OM_OK ;
}

int ObjectMonitor::raw_notify(TRAPS) {
  TEVENT (raw_notify) ;
  if (THREAD != _owner) {
    return OM_ILLEGAL_MONITOR_STATE;
  }
  SimpleNotify (THREAD, false) ;
  return OM_OK;
}

int ObjectMonitor::raw_notifyAll(TRAPS) {
  TEVENT (raw_notifyAll) ;
  if (THREAD != _owner) {
    return OM_ILLEGAL_MONITOR_STATE;
  }
  SimpleNotify (THREAD, true) ;
  return OM_OK;
}

#ifndef PRODUCT
void ObjectMonitor::verify() {
}

void ObjectMonitor::print() {
}
#endif

//------------------------------------------------------------------------------
// Non-product code

#ifndef PRODUCT

void ObjectSynchronizer::trace_locking(Handle locking_obj, bool is_compiled,
                                       bool is_method, bool is_locking) {
  // Don't know what to do here
}

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

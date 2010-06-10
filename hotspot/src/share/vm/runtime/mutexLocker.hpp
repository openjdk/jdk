/*
 * Copyright (c) 1997, 2009, Oracle and/or its affiliates. All rights reserved.
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

// Mutexes used in the VM.

extern Mutex*   Patching_lock;                   // a lock used to guard code patching of compiled code
extern Monitor* SystemDictionary_lock;           // a lock on the system dictonary
extern Mutex*   PackageTable_lock;               // a lock on the class loader package table
extern Mutex*   CompiledIC_lock;                 // a lock used to guard compiled IC patching and access
extern Mutex*   InlineCacheBuffer_lock;          // a lock used to guard the InlineCacheBuffer
extern Mutex*   VMStatistic_lock;                // a lock used to guard statistics count increment
extern Mutex*   JNIGlobalHandle_lock;            // a lock on creating JNI global handles
extern Mutex*   JNIHandleBlockFreeList_lock;     // a lock on the JNI handle block free list
extern Mutex*   JNICachedItableIndex_lock;       // a lock on caching an itable index during JNI invoke
extern Mutex*   JmethodIdCreation_lock;          // a lock on creating JNI method identifiers
extern Mutex*   JfieldIdCreation_lock;           // a lock on creating JNI static field identifiers
extern Monitor* JNICritical_lock;                // a lock used while entering and exiting JNI critical regions, allows GC to sometimes get in
extern Mutex*   JvmtiThreadState_lock;           // a lock on modification of JVMTI thread data
extern Monitor* JvmtiPendingEvent_lock;          // a lock on the JVMTI pending events list
extern Monitor* Heap_lock;                       // a lock on the heap
extern Mutex*   ExpandHeap_lock;                 // a lock on expanding the heap
extern Mutex*   AdapterHandlerLibrary_lock;      // a lock on the AdapterHandlerLibrary
extern Mutex*   SignatureHandlerLibrary_lock;    // a lock on the SignatureHandlerLibrary
extern Mutex*   VtableStubs_lock;                // a lock on the VtableStubs
extern Mutex*   SymbolTable_lock;                // a lock on the symbol table
extern Mutex*   StringTable_lock;                // a lock on the interned string table
extern Mutex*   CodeCache_lock;                  // a lock on the CodeCache, rank is special, use MutexLockerEx
extern Mutex*   MethodData_lock;                 // a lock on installation of method data
extern Mutex*   RetData_lock;                    // a lock on installation of RetData inside method data
extern Mutex*   DerivedPointerTableGC_lock;      // a lock to protect the derived pointer table
extern Monitor* VMOperationQueue_lock;           // a lock on queue of vm_operations waiting to execute
extern Monitor* VMOperationRequest_lock;         // a lock on Threads waiting for a vm_operation to terminate
extern Monitor* Safepoint_lock;                  // a lock used by the safepoint abstraction
extern Monitor* Threads_lock;                    // a lock on the Threads table of active Java threads
                                                 // (also used by Safepoints too to block threads creation/destruction)
extern Monitor* CGC_lock;                        // used for coordination between
                                                 // fore- & background GC threads.
extern Mutex*   STS_init_lock;                   // coordinate initialization of SuspendibleThreadSets.
extern Monitor* SLT_lock;                        // used in CMS GC for acquiring PLL
extern Monitor* iCMS_lock;                       // CMS incremental mode start/stop notification
extern Monitor* FullGCCount_lock;                // in support of "concurrent" full gc
extern Monitor* CMark_lock;                      // used for concurrent mark thread coordination
extern Monitor* ZF_mon;                          // used for G1 conc zero-fill.
extern Monitor* Cleanup_mon;                     // used for G1 conc cleanup.
extern Mutex*   CMRegionStack_lock;              // used for protecting accesses to the CM region stack
extern Mutex*   SATB_Q_FL_lock;                  // Protects SATB Q
                                                 // buffer free list.
extern Monitor* SATB_Q_CBL_mon;                  // Protects SATB Q
                                                 // completed buffer queue.
extern Mutex*   Shared_SATB_Q_lock;              // Lock protecting SATB
                                                 // queue shared by
                                                 // non-Java threads.

extern Mutex*   DirtyCardQ_FL_lock;              // Protects dirty card Q
                                                 // buffer free list.
extern Monitor* DirtyCardQ_CBL_mon;              // Protects dirty card Q
                                                 // completed buffer queue.
extern Mutex*   Shared_DirtyCardQ_lock;          // Lock protecting dirty card
                                                 // queue shared by
                                                 // non-Java threads.
                                                 // (see option ExplicitGCInvokesConcurrent)
extern Mutex*   ParGCRareEvent_lock;             // Synchronizes various (rare) parallel GC ops.
extern Mutex*   EvacFailureStack_lock;           // guards the evac failure scan stack
extern Mutex*   Compile_lock;                    // a lock held when Compilation is updating code (used to block CodeCache traversal, CHA updates, etc)
extern Monitor* MethodCompileQueue_lock;         // a lock held when method compilations are enqueued, dequeued
#ifdef TIERED
extern Monitor* C1_lock;                         // a lock to ensure on single c1 compile is ever active
#endif // TIERED
extern Monitor* CompileThread_lock;              // a lock held by compile threads during compilation system initialization
extern Mutex*   CompileTaskAlloc_lock;           // a lock held when CompileTasks are allocated
extern Mutex*   CompileStatistics_lock;          // a lock held when updating compilation statistics
extern Mutex*   MultiArray_lock;                 // a lock used to guard allocation of multi-dim arrays
extern Monitor* Terminator_lock;                 // a lock used to guard termination of the vm
extern Monitor* BeforeExit_lock;                 // a lock used to guard cleanups and shutdown hooks
extern Monitor* Notify_lock;                     // a lock used to synchronize the start-up of the vm
extern Monitor* Interrupt_lock;                  // a lock used for condition variable mediated interrupt processing
extern Monitor* ProfileVM_lock;                  // a lock used for profiling the VMThread
extern Mutex*   ProfilePrint_lock;               // a lock used to serialize the printing of profiles
extern Mutex*   ExceptionCache_lock;             // a lock used to synchronize exception cache updates
extern Mutex*   OsrList_lock;                    // a lock used to serialize access to OSR queues

#ifndef PRODUCT
extern Mutex*   FullGCALot_lock;                 // a lock to make FullGCALot MT safe
#endif
extern Mutex*   Debug1_lock;                     // A bunch of pre-allocated locks that can be used for tracing
extern Mutex*   Debug2_lock;                     // down synchronization related bugs!
extern Mutex*   Debug3_lock;

extern Mutex*   RawMonitor_lock;
extern Mutex*   PerfDataMemAlloc_lock;           // a lock on the allocator for PerfData memory for performance data
extern Mutex*   PerfDataManager_lock;            // a long on access to PerfDataManager resources
extern Mutex*   ParkerFreeList_lock;
extern Mutex*   OopMapCacheAlloc_lock;           // protects allocation of oop_map caches

extern Mutex*   MMUTracker_lock;                 // protects the MMU
                                                 // tracker data structures
extern Mutex*   HotCardCache_lock;               // protects the hot card cache

extern Mutex*   Management_lock;                 // a lock used to serialize JVM management
extern Monitor* LowMemory_lock;                  // a lock used for low memory detection

// A MutexLocker provides mutual exclusion with respect to a given mutex
// for the scope which contains the locker.  The lock is an OS lock, not
// an object lock, and the two do not interoperate.  Do not use Mutex-based
// locks to lock on Java objects, because they will not be respected if a
// that object is locked using the Java locking mechanism.
//
//                NOTE WELL!!
//
// See orderAccess.hpp.  We assume throughout the VM that MutexLocker's
// and friends constructors do a fence, a lock and an acquire *in that
// order*.  And that their destructors do a release and unlock, in *that*
// order.  If their implementations change such that these assumptions
// are violated, a whole lot of code will break.

// Print all mutexes/monitors that are currently owned by a thread; called
// by fatal error handler.
void print_owned_locks_on_error(outputStream* st);

char *lock_name(Mutex *mutex);

class MutexLocker: StackObj {
 private:
  Monitor * _mutex;
 public:
  MutexLocker(Monitor * mutex) {
    assert(mutex->rank() != Mutex::special,
      "Special ranked mutex should only use MutexLockerEx");
    _mutex = mutex;
    _mutex->lock();
  }

  // Overloaded constructor passing current thread
  MutexLocker(Monitor * mutex, Thread *thread) {
    assert(mutex->rank() != Mutex::special,
      "Special ranked mutex should only use MutexLockerEx");
    _mutex = mutex;
    _mutex->lock(thread);
  }

  ~MutexLocker() {
    _mutex->unlock();
  }

};

// for debugging: check that we're already owning this lock (or are at a safepoint)
#ifdef ASSERT
void assert_locked_or_safepoint(const Monitor * lock);
void assert_lock_strong(const Monitor * lock);
#else
#define assert_locked_or_safepoint(lock)
#define assert_lock_strong(lock)
#endif

// A MutexLockerEx behaves like a MutexLocker when its constructor is
// called with a Mutex.  Unlike a MutexLocker, its constructor can also be
// called with NULL, in which case the MutexLockerEx is a no-op.  There
// is also a corresponding MutexUnlockerEx.  We want to keep the
// basic MutexLocker as fast as possible.  MutexLockerEx can also lock
// without safepoint check.

class MutexLockerEx: public StackObj {
 private:
  Monitor * _mutex;
 public:
  MutexLockerEx(Monitor * mutex, bool no_safepoint_check = !Mutex::_no_safepoint_check_flag) {
    _mutex = mutex;
    if (_mutex != NULL) {
      assert(mutex->rank() > Mutex::special || no_safepoint_check,
        "Mutexes with rank special or lower should not do safepoint checks");
      if (no_safepoint_check)
        _mutex->lock_without_safepoint_check();
      else
        _mutex->lock();
    }
  }

  ~MutexLockerEx() {
    if (_mutex != NULL) {
      _mutex->unlock();
    }
  }
};

// A MonitorLockerEx is like a MutexLockerEx above, except it takes
// a possibly null Monitor, and allows wait/notify as well which are
// delegated to the underlying Monitor.

class MonitorLockerEx: public MutexLockerEx {
 private:
  Monitor * _monitor;
 public:
  MonitorLockerEx(Monitor* monitor,
                  bool no_safepoint_check = !Mutex::_no_safepoint_check_flag):
    MutexLockerEx(monitor, no_safepoint_check),
    _monitor(monitor) {
    // Superclass constructor did locking
  }

  ~MonitorLockerEx() {
    #ifdef ASSERT
      if (_monitor != NULL) {
        assert_lock_strong(_monitor);
      }
    #endif  // ASSERT
    // Superclass destructor will do unlocking
  }

  bool wait(bool no_safepoint_check = !Mutex::_no_safepoint_check_flag,
            long timeout = 0,
            bool as_suspend_equivalent = !Mutex::_as_suspend_equivalent_flag) {
    if (_monitor != NULL) {
      return _monitor->wait(no_safepoint_check, timeout, as_suspend_equivalent);
    }
    return false;
  }

  bool notify_all() {
    if (_monitor != NULL) {
      return _monitor->notify_all();
    }
    return true;
  }

  bool notify() {
    if (_monitor != NULL) {
      return _monitor->notify();
    }
    return true;
  }
};



// A GCMutexLocker is usually initialized with a mutex that is
// automatically acquired in order to do GC.  The function that
// synchronizes using a GCMutexLocker may be called both during and between
// GC's.  Thus, it must acquire the mutex if GC is not in progress, but not
// if GC is in progress (since the mutex is already held on its behalf.)

class GCMutexLocker: public StackObj {
private:
  Monitor * _mutex;
  bool _locked;
public:
  GCMutexLocker(Monitor * mutex);
  ~GCMutexLocker() { if (_locked) _mutex->unlock(); }
};



// A MutexUnlocker temporarily exits a previously
// entered mutex for the scope which contains the unlocker.

class MutexUnlocker: StackObj {
 private:
  Monitor * _mutex;

 public:
  MutexUnlocker(Monitor * mutex) {
    _mutex = mutex;
    _mutex->unlock();
  }

  ~MutexUnlocker() {
    _mutex->lock();
  }
};

// A MutexUnlockerEx temporarily exits a previously
// entered mutex for the scope which contains the unlocker.

class MutexUnlockerEx: StackObj {
 private:
  Monitor * _mutex;
  bool _no_safepoint_check;

 public:
  MutexUnlockerEx(Monitor * mutex, bool no_safepoint_check = !Mutex::_no_safepoint_check_flag) {
    _mutex = mutex;
    _no_safepoint_check = no_safepoint_check;
    _mutex->unlock();
  }

  ~MutexUnlockerEx() {
    if (_no_safepoint_check == Mutex::_no_safepoint_check_flag) {
      _mutex->lock_without_safepoint_check();
    } else {
      _mutex->lock();
    }
  }
};

#ifndef PRODUCT
//
// A special MutexLocker that allows:
//   - reentrant locking
//   - locking out of order
//
// Only too be used for verify code, where we can relaxe out dead-lock
// dection code a bit (unsafe, but probably ok). This code is NEVER to
// be included in a product version.
//
class VerifyMutexLocker: StackObj {
 private:
  Monitor * _mutex;
  bool   _reentrant;
 public:
  VerifyMutexLocker(Monitor * mutex) {
    _mutex     = mutex;
    _reentrant = mutex->owned_by_self();
    if (!_reentrant) {
      // We temp. diable strict safepoint checking, while we require the lock
      FlagSetting fs(StrictSafepointChecks, false);
      _mutex->lock();
    }
  }

  ~VerifyMutexLocker() {
    if (!_reentrant) {
      _mutex->unlock();
    }
  }
};

#endif

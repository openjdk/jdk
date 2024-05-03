/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_RUNTIME_MUTEXLOCKER_HPP
#define SHARE_RUNTIME_MUTEXLOCKER_HPP

#include "memory/allocation.hpp"
#include "runtime/flags/flagSetting.hpp"
#include "runtime/mutex.hpp"

// Mutexes used in the VM.

extern Mutex*   Patching_lock;                   // a lock used to guard code patching of compiled code
extern Mutex*   NMethodState_lock;               // a lock used to guard a compiled method state
extern Monitor* SystemDictionary_lock;           // a lock on the system dictionary
extern Mutex*   InvokeMethodTypeTable_lock;
extern Monitor* InvokeMethodIntrinsicTable_lock;
extern Mutex*   SharedDictionary_lock;           // a lock on the CDS shared dictionary
extern Monitor* ClassInitError_lock;             // a lock on the class initialization error table
extern Mutex*   Module_lock;                     // a lock on module and package related data structures
extern Mutex*   CompiledIC_lock;                 // a lock used to guard compiled IC patching and access
extern Mutex*   InlineCacheBuffer_lock;          // a lock used to guard the InlineCacheBuffer
extern Mutex*   VMStatistic_lock;                // a lock used to guard statistics count increment
extern Mutex*   JmethodIdCreation_lock;          // a lock on creating JNI method identifiers
extern Mutex*   JfieldIdCreation_lock;           // a lock on creating JNI static field identifiers
extern Monitor* JNICritical_lock;                // a lock used while entering and exiting JNI critical regions, allows GC to sometimes get in
extern Mutex*   JvmtiThreadState_lock;           // a lock on modification of JVMTI thread data
extern Monitor* EscapeBarrier_lock;              // a lock to sync reallocating and relocking objects because of JVMTI access
extern Monitor* JvmtiVTMSTransition_lock;        // a lock for Virtual Thread Mount State transition (VTMS transition) management
extern Monitor* Heap_lock;                       // a lock on the heap
#ifdef INCLUDE_PARALLELGC
extern Mutex*   PSOldGenExpand_lock;         // a lock on expanding the heap
#endif
extern Mutex*   AdapterHandlerLibrary_lock;      // a lock on the AdapterHandlerLibrary
extern Mutex*   SignatureHandlerLibrary_lock;    // a lock on the SignatureHandlerLibrary
extern Mutex*   VtableStubs_lock;                // a lock on the VtableStubs
extern Mutex*   SymbolArena_lock;                // a lock on the symbol table arena
extern Monitor* StringDedup_lock;                // a lock on the string deduplication facility
extern Mutex*   StringDedupIntern_lock;          // a lock on StringTable notification of StringDedup
extern Monitor* CodeCache_lock;                  // a lock on the CodeCache
extern Mutex*   TouchedMethodLog_lock;           // a lock on allocation of LogExecutedMethods info
extern Mutex*   RetData_lock;                    // a lock on installation of RetData inside method data
extern Monitor* VMOperation_lock;                // a lock on queue of vm_operations waiting to execute
extern Monitor* Threads_lock;                    // a lock on the Threads table of active Java threads
                                                 // (also used by Safepoints too to block threads creation/destruction)
extern Mutex*   NonJavaThreadsList_lock;         // a lock on the NonJavaThreads list
extern Mutex*   NonJavaThreadsListSync_lock;     // a lock for NonJavaThreads list synchronization
extern Monitor* CGC_lock;                        // used for coordination between
                                                 // fore- & background GC threads.
extern Monitor* STS_lock;                        // used for joining/leaving SuspendibleThreadSet.
extern Monitor* G1OldGCCount_lock;               // in support of "concurrent" full gc
extern Mutex*   G1RareEvent_lock;                // Synchronizes (rare) parallel GC operations.
extern Mutex*   G1DetachedRefinementStats_lock;  // Lock protecting detached refinement stats
extern Mutex*   MarkStackFreeList_lock;          // Protects access to the global mark stack free list.
extern Mutex*   MarkStackChunkList_lock;         // Protects access to the global mark stack chunk list.
extern Mutex*   MonitoringSupport_lock;          // Protects updates to the serviceability memory pools and allocated memory high water mark.
extern Monitor* ConcurrentGCBreakpoints_lock;    // Protects concurrent GC breakpoint management
extern Mutex*   Compile_lock;                    // a lock held when Compilation is updating code (used to block CodeCache traversal, CHA updates, etc)
extern Monitor* MethodCompileQueue_lock;         // a lock held when method compilations are enqueued, dequeued
extern Monitor* CompileThread_lock;              // a lock held by compile threads during compilation system initialization
extern Monitor* Compilation_lock;                // a lock used to pause compilation
extern Mutex*   CompileTaskAlloc_lock;           // a lock held when CompileTasks are allocated
extern Mutex*   CompileStatistics_lock;          // a lock held when updating compilation statistics
extern Mutex*   DirectivesStack_lock;            // a lock held when mutating the dirstack and ref counting directives
extern Monitor* Terminator_lock;                 // a lock used to guard termination of the vm
extern Monitor* InitCompleted_lock;              // a lock used to signal threads waiting on init completed
extern Monitor* BeforeExit_lock;                 // a lock used to guard cleanups and shutdown hooks
extern Monitor* Notify_lock;                     // a lock used to synchronize the start-up of the vm
extern Mutex*   ExceptionCache_lock;             // a lock used to synchronize exception cache updates

#ifndef PRODUCT
extern Mutex*   FullGCALot_lock;                 // a lock to make FullGCALot MT safe
#endif // PRODUCT

extern Mutex*   RawMonitor_lock;
extern Mutex*   PerfDataMemAlloc_lock;           // a lock on the allocator for PerfData memory for performance data
extern Mutex*   PerfDataManager_lock;            // a long on access to PerfDataManager resources
extern Mutex*   OopMapCacheAlloc_lock;           // protects allocation of oop_map caches

extern Mutex*   FreeList_lock;                   // protects the free region list during safepoints
extern Mutex*   OldSets_lock;                    // protects the old region sets
extern Mutex*   Uncommit_lock;                   // protects the uncommit list when not at safepoints
extern Monitor* RootRegionScan_lock;             // used to notify that the CM threads have finished scanning the IM snapshot regions

extern Mutex*   Management_lock;                 // a lock used to serialize JVM management
extern Monitor* MonitorDeflation_lock;           // a lock used for monitor deflation thread operation
extern Monitor* Service_lock;                    // a lock used for service thread operation
extern Monitor* Notification_lock;               // a lock used for notification thread operation
extern Monitor* PeriodicTask_lock;               // protects the periodic task structure
extern Monitor* RedefineClasses_lock;            // locks classes from parallel redefinition
extern Mutex*   Verify_lock;                     // synchronize initialization of verify library
extern Monitor* ThreadsSMRDelete_lock;           // Used by ThreadsSMRSupport to take pressure off the Threads_lock
extern Mutex*   ThreadIdTableCreate_lock;        // Used by ThreadIdTable to lazily create the thread id table
extern Mutex*   SharedDecoder_lock;              // serializes access to the decoder during normal (not error reporting) use
extern Mutex*   DCmdFactory_lock;                // serialize access to DCmdFactory information
extern Mutex*   NMTQuery_lock;                   // serialize NMT Dcmd queries
extern Mutex*   NMTCompilationCostHistory_lock;  // guards NMT compilation cost history
#if INCLUDE_CDS
#if INCLUDE_JVMTI
extern Mutex*   CDSClassFileStream_lock;         // FileMapInfo::open_stream_for_jvmti
#endif
extern Mutex*   DumpTimeTable_lock;              // SystemDictionaryShared::_dumptime_table
extern Mutex*   CDSLambda_lock;                  // SystemDictionaryShared::get_shared_lambda_proxy_class
extern Mutex*   DumpRegion_lock;                 // Symbol::operator new(size_t sz, int len)
extern Mutex*   ClassListFile_lock;              // ClassListWriter()
extern Mutex*   UnregisteredClassesTable_lock;   // UnregisteredClassesTableTable
extern Mutex*   LambdaFormInvokers_lock;         // Protecting LambdaFormInvokers::_lambdaform_lines
extern Mutex*   ScratchObjects_lock;             // Protecting _scratch_xxx_table in heapShared.cpp
#endif // INCLUDE_CDS
#if INCLUDE_JFR
extern Mutex*   JfrStacktrace_lock;              // used to guard access to the JFR stacktrace table
extern Monitor* JfrMsg_lock;                     // protects JFR messaging
extern Mutex*   JfrBuffer_lock;                  // protects JFR buffer operations
extern Monitor* JfrThreadSampler_lock;           // used to suspend/resume JFR thread sampler
#endif

extern Mutex*   Metaspace_lock;                  // protects Metaspace virtualspace and chunk expansions
extern Monitor* MetaspaceCritical_lock;          // synchronizes failed metaspace allocations that risk throwing metaspace OOM
extern Mutex*   ClassLoaderDataGraph_lock;       // protects CLDG list, needed for concurrent unloading


extern Mutex*   CodeHeapStateAnalytics_lock;     // lock print functions against concurrent analyze functions.
                                                 // Only used locally in PrintCodeCacheLayout processing.

extern Monitor* ContinuationRelativize_lock;

#if INCLUDE_JVMCI
extern Monitor* JVMCI_lock;                      // protects global JVMCI critical sections
extern Monitor* JVMCIRuntime_lock;               // protects critical sections for a specific JVMCIRuntime object
#endif

extern Mutex*   Bootclasspath_lock;

extern Mutex*   tty_lock;                          // lock to synchronize output.

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
void print_lock_ranks(outputStream* st);

// for debugging: check that we're already owning this lock (or are at a safepoint / handshake)
#ifdef ASSERT
void assert_locked_or_safepoint(const Mutex* lock);
void assert_lock_strong(const Mutex* lock);
#else
#define assert_locked_or_safepoint(lock)
#define assert_lock_strong(lock)
#endif

// Internal implementation. Skips on null Mutex.
// Subclasses enforce stronger invariants.
class MutexLockerImpl: public StackObj {
 protected:
  Mutex* _mutex;

  MutexLockerImpl(Mutex* mutex, Mutex::SafepointCheckFlag flag = Mutex::_safepoint_check_flag) :
    _mutex(mutex) {
    bool no_safepoint_check = flag == Mutex::_no_safepoint_check_flag;
    if (_mutex != nullptr) {
      if (no_safepoint_check) {
        _mutex->lock_without_safepoint_check();
      } else {
        _mutex->lock();
      }
    }
  }

  MutexLockerImpl(Thread* thread, Mutex* mutex, Mutex::SafepointCheckFlag flag = Mutex::_safepoint_check_flag) :
    _mutex(mutex) {
    bool no_safepoint_check = flag == Mutex::_no_safepoint_check_flag;
    if (_mutex != nullptr) {
      if (no_safepoint_check) {
        _mutex->lock_without_safepoint_check(thread);
      } else {
        _mutex->lock(thread);
      }
    }
  }

  ~MutexLockerImpl() {
    if (_mutex != nullptr) {
      assert_lock_strong(_mutex);
      _mutex->unlock();
    }
  }

 public:
  static void post_initialize();
};

// Simplest mutex locker.
// Does not allow null mutexes.
class MutexLocker: public MutexLockerImpl {
 public:
   MutexLocker(Mutex* mutex, Mutex::SafepointCheckFlag flag = Mutex::_safepoint_check_flag) :
     MutexLockerImpl(mutex, flag) {
     assert(mutex != nullptr, "null mutex not allowed");
   }

   MutexLocker(Thread* thread, Mutex* mutex, Mutex::SafepointCheckFlag flag = Mutex::_safepoint_check_flag) :
     MutexLockerImpl(thread, mutex, flag) {
     assert(mutex != nullptr, "null mutex not allowed");
   }
};

// Conditional mutex locker.
// Like MutexLocker above, but only locks when condition is true.
class ConditionalMutexLocker: public MutexLockerImpl {
 public:
   ConditionalMutexLocker(Mutex* mutex, bool condition, Mutex::SafepointCheckFlag flag = Mutex::_safepoint_check_flag) :
     MutexLockerImpl(condition ? mutex : nullptr, flag) {
     assert(!condition || mutex != nullptr, "null mutex not allowed when locking");
   }

   ConditionalMutexLocker(Thread* thread, Mutex* mutex, bool condition, Mutex::SafepointCheckFlag flag = Mutex::_safepoint_check_flag) :
     MutexLockerImpl(thread, condition ? mutex : nullptr, flag) {
     assert(!condition || mutex != nullptr, "null mutex not allowed when locking");
   }
};

// A MonitorLocker is like a MutexLocker above, except it allows
// wait/notify as well which are delegated to the underlying Monitor.
// It also disallows null.
class MonitorLocker: public MutexLockerImpl {
  Mutex::SafepointCheckFlag _flag;

 protected:
  Monitor* as_monitor() const {
    return static_cast<Monitor*>(_mutex);
  }

 public:
  MonitorLocker(Monitor* monitor, Mutex::SafepointCheckFlag flag = Mutex::_safepoint_check_flag) :
    MutexLockerImpl(monitor, flag), _flag(flag) {
    // Superclass constructor did locking
    assert(monitor != nullptr, "null monitor not allowed");
  }

  MonitorLocker(Thread* thread, Monitor* monitor, Mutex::SafepointCheckFlag flag = Mutex::_safepoint_check_flag) :
    MutexLockerImpl(thread, monitor, flag), _flag(flag) {
    // Superclass constructor did locking
    assert(monitor != nullptr, "null monitor not allowed");
  }

  bool wait(int64_t timeout = 0) {
    return _flag == Mutex::_safepoint_check_flag ?
      as_monitor()->wait(timeout) : as_monitor()->wait_without_safepoint_check(timeout);
  }

  void notify_all() {
    as_monitor()->notify_all();
  }

  void notify() {
    as_monitor()->notify();
  }
};


// A GCMutexLocker is usually initialized with a mutex that is
// automatically acquired in order to do GC.  The function that
// synchronizes using a GCMutexLocker may be called both during and between
// GC's.  Thus, it must acquire the mutex if GC is not in progress, but not
// if GC is in progress (since the mutex is already held on its behalf.)

class GCMutexLocker: public StackObj {
private:
  Mutex* _mutex;
  bool _locked;
public:
  GCMutexLocker(Mutex* mutex);
  ~GCMutexLocker() { if (_locked) _mutex->unlock(); }
};

// A MutexUnlocker temporarily exits a previously
// entered mutex for the scope which contains the unlocker.

class MutexUnlocker: StackObj {
 private:
  Mutex* _mutex;
  bool _no_safepoint_check;

 public:
  MutexUnlocker(Mutex* mutex, Mutex::SafepointCheckFlag flag = Mutex::_safepoint_check_flag) :
    _mutex(mutex),
    _no_safepoint_check(flag == Mutex::_no_safepoint_check_flag) {
    _mutex->unlock();
  }

  ~MutexUnlocker() {
    if (_no_safepoint_check) {
      _mutex->lock_without_safepoint_check();
    } else {
      _mutex->lock();
    }
  }
};

// Instance of a RecursiveLock that may be held through Java heap allocation, which may include calls to Java,
// and JNI event notification for resource exhaustion for metaspace or heap.
extern RecursiveMutex* MultiArray_lock;

// RAII locker for a RecursiveMutex.  See comments in mutex.hpp for more information.
class RecursiveLocker {
  RecursiveMutex* _lock;
  Thread*         _thread;
 public:
  RecursiveLocker(RecursiveMutex* lock, Thread* current) : _lock(lock), _thread(current) {
    _lock->lock(_thread);
  }
  ~RecursiveLocker() {
    _lock->unlock(_thread);
  }
};

#endif // SHARE_RUNTIME_MUTEXLOCKER_HPP

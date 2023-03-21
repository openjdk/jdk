/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

#include <type_traits>

void mutex_init();

template <typename T>
class StaticLock {
 private:
  friend void mutex_init();

  alignas(T) uint8_t _storage[sizeof(T)];
  DEBUG_ONLY(bool _inited = false;)

  // Called by mutex_init() to actually construct the underlying T.
  template <typename... Args>
  void init(Args&&... args) {
    assert(!_inited, "Mutex/Monitor initialized twice");
    ::new (static_cast<void*>(&_storage[0])) T(forward<Args>(args)...);
    DEBUG_ONLY(_inited = true;)
  }

  NONCOPYABLE(StaticLock);

 public:
  static_assert(std::is_base_of<Mutex, T>::value,
                "StaticLock is only meant to be used with Mutex or its derived classes");

  constexpr StaticLock() = default;

  T* get() {
    assert(_inited, "Mutex/Monitor not initialized");
    return reinterpret_cast<T*>(&_storage[0]);
  }

  const T* get() const {
    assert(_inited, "Mutex/Monitor not initialized");
    return reinterpret_cast<const T*>(&_storage[0]);
  }

  T* operator->() {
    return get();
  }

  const T* operator->() const {
    return get();
  }

  // For compatibility, without doing lots of refactoring, this is implicitly convertible to T*.
  // Eventually it would be nice to make these explicit.
  operator T*() {
    return get();
  }
  operator const T*() const {
    return get();
  }
};

// Mutexes used in the VM.

extern StaticLock<PaddedMutex>   Patching_lock;                   // a lock used to guard code patching of compiled code
extern StaticLock<PaddedMutex>   CompiledMethod_lock;             // a lock used to guard a compiled method and OSR queues
extern StaticLock<PaddedMonitor> SystemDictionary_lock;           // a lock on the system dictionary
extern StaticLock<PaddedMutex>   InvokeMethodTable_lock;
extern StaticLock<PaddedMutex>   SharedDictionary_lock;           // a lock on the CDS shared dictionary
extern StaticLock<PaddedMonitor> ClassInitError_lock;             // a lock on the class initialization error table
extern StaticLock<PaddedMutex>   Module_lock;                     // a lock on module and package related data structures
extern StaticLock<PaddedMutex>   CompiledIC_lock;                 // a lock used to guard compiled IC patching and access
extern StaticLock<PaddedMutex>   InlineCacheBuffer_lock;          // a lock used to guard the InlineCacheBuffer
extern StaticLock<PaddedMutex>   VMStatistic_lock;                // a lock used to guard statistics count increment
extern StaticLock<PaddedMutex>   JmethodIdCreation_lock;          // a lock on creating JNI method identifiers
extern StaticLock<PaddedMutex>   JfieldIdCreation_lock;           // a lock on creating JNI static field identifiers
extern StaticLock<PaddedMonitor> JNICritical_lock;                // a lock used while entering and exiting JNI critical regions, allows GC to sometimes get in
extern StaticLock<PaddedMutex>   JvmtiThreadState_lock;           // a lock on modification of JVMTI thread data
extern StaticLock<PaddedMonitor> EscapeBarrier_lock;              // a lock to sync reallocating and relocking objects because of JVMTI access
extern StaticLock<PaddedMonitor> JvmtiVTMSTransition_lock;        // a lock for Virtual Thread Mount State transition (VTMS transition) management
extern StaticLock<PaddedMonitor> Heap_lock;                       // a lock on the heap
#ifdef INCLUDE_PARALLELGC
extern StaticLock<PaddedMutex>   PSOldGenExpand_lock;         // a lock on expanding the heap
#endif
extern StaticLock<PaddedMutex>   AdapterHandlerLibrary_lock;      // a lock on the AdapterHandlerLibrary
extern StaticLock<PaddedMutex>   SignatureHandlerLibrary_lock;    // a lock on the SignatureHandlerLibrary
extern StaticLock<PaddedMutex>   VtableStubs_lock;                // a lock on the VtableStubs
extern StaticLock<PaddedMutex>   SymbolArena_lock;                // a lock on the symbol table arena
extern StaticLock<PaddedMonitor> StringDedup_lock;                // a lock on the string deduplication facility
extern StaticLock<PaddedMutex>   StringDedupIntern_lock;          // a lock on StringTable notification of StringDedup
extern StaticLock<PaddedMonitor> CodeCache_lock;                  // a lock on the CodeCache
extern StaticLock<PaddedMutex>   TouchedMethodLog_lock;           // a lock on allocation of LogExecutedMethods info
extern StaticLock<PaddedMutex>   RetData_lock;                    // a lock on installation of RetData inside method data
extern StaticLock<PaddedMonitor> VMOperation_lock;                // a lock on queue of vm_operations waiting to execute
extern StaticLock<PaddedMonitor> Threads_lock;                    // a lock on the Threads table of active Java threads
                                                 // (also used by Safepoints too to block threads creation/destruction)
extern StaticLock<PaddedMutex>   NonJavaThreadsList_lock;         // a lock on the NonJavaThreads list
extern StaticLock<PaddedMutex>   NonJavaThreadsListSync_lock;     // a lock for NonJavaThreads list synchronization
extern StaticLock<PaddedMonitor> CGC_lock;                        // used for coordination between
                                                 // fore- & background GC threads.
extern StaticLock<PaddedMonitor> STS_lock;                        // used for joining/leaving SuspendibleThreadSet.
extern StaticLock<PaddedMonitor> G1OldGCCount_lock;               // in support of "concurrent" full gc
extern StaticLock<PaddedMutex>   G1RareEvent_lock;                // Synchronizes (rare) parallel GC operations.
extern StaticLock<PaddedMutex>   G1DetachedRefinementStats_lock;  // Lock protecting detached refinement stats
extern StaticLock<PaddedMutex>   MarkStackFreeList_lock;          // Protects access to the global mark stack free list.
extern StaticLock<PaddedMutex>   MarkStackChunkList_lock;         // Protects access to the global mark stack chunk list.
extern StaticLock<PaddedMutex>   MonitoringSupport_lock;          // Protects updates to the serviceability memory pools.
extern StaticLock<PaddedMonitor> ConcurrentGCBreakpoints_lock;    // Protects concurrent GC breakpoint management
extern StaticLock<PaddedMutex>   Compile_lock;                    // a lock held when Compilation is updating code (used to block CodeCache traversal, CHA updates, etc)
extern StaticLock<PaddedMonitor> MethodCompileQueue_lock;         // a lock held when method compilations are enqueued, dequeued
extern StaticLock<PaddedMonitor> CompileThread_lock;              // a lock held by compile threads during compilation system initialization
extern StaticLock<PaddedMonitor> Compilation_lock;                // a lock used to pause compilation
extern StaticLock<PaddedMutex>   CompileTaskAlloc_lock;           // a lock held when CompileTasks are allocated
extern StaticLock<PaddedMutex>   CompileStatistics_lock;          // a lock held when updating compilation statistics
extern StaticLock<PaddedMutex>   DirectivesStack_lock;            // a lock held when mutating the dirstack and ref counting directives
extern StaticLock<PaddedMutex>   MultiArray_lock;                 // a lock used to guard allocation of multi-dim arrays
extern StaticLock<PaddedMonitor> Terminator_lock;                 // a lock used to guard termination of the vm
extern StaticLock<PaddedMonitor> InitCompleted_lock;              // a lock used to signal threads waiting on init completed
extern StaticLock<PaddedMonitor> BeforeExit_lock;                 // a lock used to guard cleanups and shutdown hooks
extern StaticLock<PaddedMonitor> Notify_lock;                     // a lock used to synchronize the start-up of the vm
extern StaticLock<PaddedMutex>   ExceptionCache_lock;             // a lock used to synchronize exception cache updates

#ifndef PRODUCT
extern StaticLock<PaddedMutex>   FullGCALot_lock;                 // a lock to make FullGCALot MT safe
#endif // PRODUCT

extern StaticLock<PaddedMutex>   RawMonitor_lock;
extern StaticLock<PaddedMutex>   PerfDataMemAlloc_lock;           // a lock on the allocator for PerfData memory for performance data
extern StaticLock<PaddedMutex>   PerfDataManager_lock;            // a long on access to PerfDataManager resources
extern StaticLock<PaddedMutex>   OopMapCacheAlloc_lock;           // protects allocation of oop_map caches

extern StaticLock<PaddedMutex>   FreeList_lock;                   // protects the free region list during safepoints
extern StaticLock<PaddedMutex>   OldSets_lock;                    // protects the old region sets
extern StaticLock<PaddedMutex>   Uncommit_lock;                   // protects the uncommit list when not at safepoints
extern StaticLock<PaddedMonitor> RootRegionScan_lock;             // used to notify that the CM threads have finished scanning the IM snapshot regions

extern StaticLock<PaddedMutex>   Management_lock;                 // a lock used to serialize JVM management
extern StaticLock<PaddedMonitor> MonitorDeflation_lock;           // a lock used for monitor deflation thread operation
extern StaticLock<PaddedMonitor> Service_lock;                    // a lock used for service thread operation
extern StaticLock<PaddedMonitor> Notification_lock;               // a lock used for notification thread operation
extern StaticLock<PaddedMonitor> PeriodicTask_lock;               // protects the periodic task structure
extern StaticLock<PaddedMonitor> RedefineClasses_lock;            // locks classes from parallel redefinition
extern StaticLock<PaddedMutex>   Verify_lock;                     // synchronize initialization of verify library
extern StaticLock<PaddedMonitor> Zip_lock;                        // synchronize initialization of zip library
extern StaticLock<PaddedMonitor> ThreadsSMRDelete_lock;           // Used by ThreadsSMRSupport to take pressure off the Threads_lock
extern StaticLock<PaddedMutex>   ThreadIdTableCreate_lock;        // Used by ThreadIdTable to lazily create the thread id table
extern StaticLock<PaddedMutex>   SharedDecoder_lock;              // serializes access to the decoder during normal (not error reporting) use
extern StaticLock<PaddedMutex>   DCmdFactory_lock;                // serialize access to DCmdFactory information
extern StaticLock<PaddedMutex>   NMTQuery_lock;                   // serialize NMT Dcmd queries
#if INCLUDE_CDS
#if INCLUDE_JVMTI
extern StaticLock<PaddedMutex>   CDSClassFileStream_lock;         // FileMapInfo::open_stream_for_jvmti
#endif
extern StaticLock<PaddedMutex>   DumpTimeTable_lock;              // SystemDictionaryShared::_dumptime_table
extern StaticLock<PaddedMutex>   CDSLambda_lock;                  // SystemDictionaryShared::get_shared_lambda_proxy_class
extern StaticLock<PaddedMutex>   DumpRegion_lock;                 // Symbol::operator new(size_t sz, int len)
extern StaticLock<PaddedMutex>   ClassListFile_lock;              // ClassListWriter()
extern StaticLock<PaddedMutex>   UnregisteredClassesTable_lock;   // UnregisteredClassesTableTable
extern StaticLock<PaddedMutex>   LambdaFormInvokers_lock;         // Protecting LambdaFormInvokers::_lambdaform_lines
extern StaticLock<PaddedMutex>   ScratchObjects_lock;             // Protecting _scratch_xxx_table in heapShared.cpp
#endif // INCLUDE_CDS
#if INCLUDE_JFR
extern StaticLock<PaddedMutex>   JfrStacktrace_lock;              // used to guard access to the JFR stacktrace table
extern StaticLock<PaddedMonitor> JfrMsg_lock;                     // protects JFR messaging
extern StaticLock<PaddedMutex>   JfrBuffer_lock;                  // protects JFR buffer operations
extern StaticLock<PaddedMonitor> JfrThreadSampler_lock;           // used to suspend/resume JFR thread sampler
#endif

#ifndef SUPPORTS_NATIVE_CX8
extern StaticLock<PaddedMutex>   UnsafeJlong_lock;                // provides Unsafe atomic updates to jlongs on platforms that don't support cx8
#endif

extern StaticLock<PaddedMutex>   Metaspace_lock;                  // protects Metaspace virtualspace and chunk expansions
extern StaticLock<PaddedMonitor> MetaspaceCritical_lock;          // synchronizes failed metaspace allocations that risk throwing metaspace OOM
extern StaticLock<PaddedMutex>   ClassLoaderDataGraph_lock;       // protects CLDG list, needed for concurrent unloading


extern StaticLock<PaddedMutex>   CodeHeapStateAnalytics_lock;     // lock print functions against concurrent analyze functions.
                                                 // Only used locally in PrintCodeCacheLayout processing.

extern StaticLock<PaddedMonitor> ContinuationRelativize_lock;

#if INCLUDE_JVMCI
extern StaticLock<PaddedMonitor> JVMCI_lock;                      // protects global JVMCI critical sections
extern StaticLock<PaddedMonitor> JVMCIRuntime_lock;               // protects critical sections for a specific JVMCIRuntime object
#endif

extern StaticLock<PaddedMutex>   Bootclasspath_lock;

extern StaticLock<PaddedMutex> tty_lock;                          // lock to synchronize output.

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
void assert_locked_or_safepoint_weak(const Mutex* lock);
void assert_lock_strong(const Mutex* lock);
#else
#define assert_locked_or_safepoint(lock)
#define assert_locked_or_safepoint_weak(lock)
#define assert_lock_strong(lock)
#endif

class MutexLocker: public StackObj {
 protected:
  Mutex* _mutex;
 public:
  MutexLocker(Mutex* mutex, Mutex::SafepointCheckFlag flag = Mutex::_safepoint_check_flag) :
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

  MutexLocker(Thread* thread, Mutex* mutex, Mutex::SafepointCheckFlag flag = Mutex::_safepoint_check_flag) :
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

  ~MutexLocker() {
    if (_mutex != nullptr) {
      assert_lock_strong(_mutex);
      _mutex->unlock();
    }
  }

  static void post_initialize();
};

// A MonitorLocker is like a MutexLocker above, except it allows
// wait/notify as well which are delegated to the underlying Monitor.
// It also disallows null.

class MonitorLocker: public MutexLocker {
  Mutex::SafepointCheckFlag _flag;

 protected:
  Monitor* as_monitor() const {
    return static_cast<Monitor*>(_mutex);
  }

 public:
  MonitorLocker(Monitor* monitor, Mutex::SafepointCheckFlag flag = Mutex::_safepoint_check_flag) :
    MutexLocker(monitor, flag), _flag(flag) {
    // Superclass constructor did locking
    assert(monitor != nullptr, "null monitor not allowed");
  }

  MonitorLocker(Thread* thread, Monitor* monitor, Mutex::SafepointCheckFlag flag = Mutex::_safepoint_check_flag) :
    MutexLocker(thread, monitor, flag), _flag(flag) {
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

#endif // SHARE_RUNTIME_MUTEXLOCKER_HPP

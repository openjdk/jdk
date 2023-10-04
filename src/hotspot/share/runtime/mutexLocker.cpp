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

#include "precompiled.hpp"
#include "gc/shared/gc_globals.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/vmError.hpp"

// Mutexes used in the VM (see comment in mutexLocker.hpp):

Mutex*   Patching_lock                = nullptr;
Mutex*   CompiledMethod_lock          = nullptr;
Monitor* SystemDictionary_lock        = nullptr;
Mutex*   InvokeMethodTypeTable_lock   = nullptr;
Monitor* InvokeMethodIntrinsicTable_lock = nullptr;
Mutex*   SharedDictionary_lock        = nullptr;
Monitor* ClassInitError_lock          = nullptr;
Mutex*   Module_lock                  = nullptr;
Mutex*   CompiledIC_lock              = nullptr;
Mutex*   InlineCacheBuffer_lock       = nullptr;
Mutex*   VMStatistic_lock             = nullptr;
Mutex*   JmethodIdCreation_lock       = nullptr;
Mutex*   JfieldIdCreation_lock        = nullptr;
Monitor* JNICritical_lock             = nullptr;
Mutex*   JvmtiThreadState_lock        = nullptr;
Monitor* EscapeBarrier_lock           = nullptr;
Monitor* JvmtiVTMSTransition_lock     = nullptr;
Monitor* Heap_lock                    = nullptr;
#ifdef INCLUDE_PARALLELGC
Mutex*   PSOldGenExpand_lock      = nullptr;
#endif
Mutex*   AdapterHandlerLibrary_lock   = nullptr;
Mutex*   SignatureHandlerLibrary_lock = nullptr;
Mutex*   VtableStubs_lock             = nullptr;
Mutex*   SymbolArena_lock             = nullptr;
Monitor* StringDedup_lock             = nullptr;
Mutex*   StringDedupIntern_lock       = nullptr;
Monitor* CodeCache_lock               = nullptr;
Mutex*   TouchedMethodLog_lock        = nullptr;
Mutex*   RetData_lock                 = nullptr;
Monitor* VMOperation_lock             = nullptr;
Monitor* Threads_lock                 = nullptr;
Mutex*   NonJavaThreadsList_lock      = nullptr;
Mutex*   NonJavaThreadsListSync_lock  = nullptr;
Monitor* CGC_lock                     = nullptr;
Monitor* STS_lock                     = nullptr;
Monitor* G1OldGCCount_lock            = nullptr;
Mutex*   G1RareEvent_lock             = nullptr;
Mutex*   G1DetachedRefinementStats_lock = nullptr;
Mutex*   MarkStackFreeList_lock       = nullptr;
Mutex*   MarkStackChunkList_lock      = nullptr;
Mutex*   MonitoringSupport_lock       = nullptr;
Monitor* ConcurrentGCBreakpoints_lock = nullptr;
Mutex*   Compile_lock                 = nullptr;
Monitor* MethodCompileQueue_lock      = nullptr;
Monitor* CompileThread_lock           = nullptr;
Monitor* Compilation_lock             = nullptr;
Mutex*   CompileTaskAlloc_lock        = nullptr;
Mutex*   CompileStatistics_lock       = nullptr;
Mutex*   DirectivesStack_lock         = nullptr;
Mutex*   MultiArray_lock              = nullptr;
Monitor* Terminator_lock              = nullptr;
Monitor* InitCompleted_lock           = nullptr;
Monitor* BeforeExit_lock              = nullptr;
Monitor* Notify_lock                  = nullptr;
Mutex*   ExceptionCache_lock          = nullptr;
#ifndef PRODUCT
Mutex*   FullGCALot_lock              = nullptr;
#endif

Mutex*   tty_lock                     = nullptr;

Mutex*   RawMonitor_lock              = nullptr;
Mutex*   PerfDataMemAlloc_lock        = nullptr;
Mutex*   PerfDataManager_lock         = nullptr;
Mutex*   OopMapCacheAlloc_lock        = nullptr;

Mutex*   FreeList_lock                = nullptr;
Mutex*   OldSets_lock                 = nullptr;
Mutex*   Uncommit_lock                = nullptr;
Monitor* RootRegionScan_lock          = nullptr;

Mutex*   Management_lock              = nullptr;
Monitor* MonitorDeflation_lock        = nullptr;
Monitor* Service_lock                 = nullptr;
Monitor* Notification_lock            = nullptr;
Monitor* PeriodicTask_lock            = nullptr;
Monitor* RedefineClasses_lock         = nullptr;
Mutex*   Verify_lock                  = nullptr;
Monitor* Zip_lock                     = nullptr;

#if INCLUDE_JFR
Mutex*   JfrStacktrace_lock           = nullptr;
Monitor* JfrMsg_lock                  = nullptr;
Mutex*   JfrBuffer_lock               = nullptr;
Monitor* JfrThreadSampler_lock        = nullptr;
#endif

#ifndef SUPPORTS_NATIVE_CX8
Mutex*   UnsafeJlong_lock             = nullptr;
#endif
Mutex*   CodeHeapStateAnalytics_lock  = nullptr;

Monitor* ContinuationRelativize_lock  = nullptr;

Mutex*   Metaspace_lock               = nullptr;
Monitor* MetaspaceCritical_lock       = nullptr;
Mutex*   ClassLoaderDataGraph_lock    = nullptr;
Monitor* ThreadsSMRDelete_lock        = nullptr;
Mutex*   ThreadIdTableCreate_lock     = nullptr;
Mutex*   SharedDecoder_lock           = nullptr;
Mutex*   DCmdFactory_lock             = nullptr;
Mutex*   NMTQuery_lock                = nullptr;

#if INCLUDE_CDS
#if INCLUDE_JVMTI
Mutex*   CDSClassFileStream_lock      = nullptr;
#endif
Mutex*   DumpTimeTable_lock           = nullptr;
Mutex*   CDSLambda_lock               = nullptr;
Mutex*   DumpRegion_lock              = nullptr;
Mutex*   ClassListFile_lock           = nullptr;
Mutex*   UnregisteredClassesTable_lock= nullptr;
Mutex*   LambdaFormInvokers_lock      = nullptr;
Mutex*   ScratchObjects_lock          = nullptr;
#endif // INCLUDE_CDS
Mutex*   Bootclasspath_lock           = nullptr;

#if INCLUDE_JVMCI
Monitor* JVMCI_lock                   = nullptr;
Monitor* JVMCIRuntime_lock            = nullptr;
#endif


#define MAX_NUM_MUTEX 128
static Mutex* _mutex_array[MAX_NUM_MUTEX];
static int _num_mutex;

#ifdef ASSERT
void assert_locked_or_safepoint(const Mutex* lock) {
  if (DebuggingContext::is_enabled() || VMError::is_error_reported()) return;
  // check if this thread owns the lock (common case)
  assert(lock != nullptr, "Need non-null lock");
  if (lock->owned_by_self()) return;
  if (SafepointSynchronize::is_at_safepoint()) return;
  if (!Universe::is_fully_initialized()) return;
  fatal("must own lock %s", lock->name());
}

// a stronger assertion than the above
void assert_lock_strong(const Mutex* lock) {
  if (DebuggingContext::is_enabled() || VMError::is_error_reported()) return;
  assert(lock != nullptr, "Need non-null lock");
  if (lock->owned_by_self()) return;
  fatal("must own lock %s", lock->name());
}
#endif

static void add_mutex(Mutex* var) {
  assert(_num_mutex < MAX_NUM_MUTEX, "increase MAX_NUM_MUTEX");
  _mutex_array[_num_mutex++] = var;
}

#define MUTEX_STORAGE_NAME(name) name##_storage
#define MUTEX_STORAGE(name, type) alignas(type) static uint8_t MUTEX_STORAGE_NAME(name)[sizeof(type)]
#define MUTEX_DEF(name, type, pri, ...) {                                                       \
  assert(name == nullptr, "Mutex/Monitor initialized twice");                                   \
  MUTEX_STORAGE(name, type);                                                                    \
  name = ::new(static_cast<void*>(MUTEX_STORAGE_NAME(name))) type((pri), #name, ##__VA_ARGS__); \
  add_mutex(name);                                                                              \
}
#define MUTEX_DEFN(name, type, pri, ...) MUTEX_DEF(name, type, Mutex::pri, ##__VA_ARGS__)

// Specify relative ranked lock
#ifdef ASSERT
#define MUTEX_DEFL(name, type, held_lock, ...) MUTEX_DEF(name, type, (held_lock)->rank() - 1, ##__VA_ARGS__)
#else
#define MUTEX_DEFL(name, type, held_lock, ...) MUTEX_DEFN(name, type, safepoint, ##__VA_ARGS__)
#endif

// Using Padded subclasses to prevent false sharing of these global monitors and mutexes.
void mutex_init() {
  MUTEX_DEFN(tty_lock                        , PaddedMutex  , tty);      // allow to lock in VM

  MUTEX_DEFN(STS_lock                        , PaddedMonitor, nosafepoint);

  if (UseG1GC) {
    MUTEX_DEFN(CGC_lock                      , PaddedMonitor, nosafepoint);

    MUTEX_DEFN(G1DetachedRefinementStats_lock, PaddedMutex  , nosafepoint-2);

    MUTEX_DEFN(FreeList_lock                 , PaddedMutex  , service-1);
    MUTEX_DEFN(OldSets_lock                  , PaddedMutex  , nosafepoint);
    MUTEX_DEFN(Uncommit_lock                 , PaddedMutex  , service-2);
    MUTEX_DEFN(RootRegionScan_lock           , PaddedMonitor, nosafepoint-1);

    MUTEX_DEFN(MarkStackFreeList_lock        , PaddedMutex  , nosafepoint);
    MUTEX_DEFN(MarkStackChunkList_lock       , PaddedMutex  , nosafepoint);
  }
  MUTEX_DEFN(MonitoringSupport_lock          , PaddedMutex  , service-1);        // used for serviceability monitoring support

  MUTEX_DEFN(StringDedup_lock                , PaddedMonitor, nosafepoint);
  MUTEX_DEFN(StringDedupIntern_lock          , PaddedMutex  , nosafepoint);
  MUTEX_DEFN(RawMonitor_lock                 , PaddedMutex  , nosafepoint-1);

  MUTEX_DEFN(Metaspace_lock                  , PaddedMutex  , nosafepoint-4);
  MUTEX_DEFN(MetaspaceCritical_lock          , PaddedMonitor, nosafepoint-1);

  MUTEX_DEFN(Patching_lock                   , PaddedMutex  , nosafepoint);      // used for safepointing and code patching.
  MUTEX_DEFN(MonitorDeflation_lock           , PaddedMonitor, nosafepoint);      // used for monitor deflation thread operations
  MUTEX_DEFN(Service_lock                    , PaddedMonitor, service);      // used for service thread operations

  if (UseNotificationThread) {
    MUTEX_DEFN(Notification_lock             , PaddedMonitor, service);  // used for notification thread operations
  } else {
    Notification_lock = Service_lock;
  }

  MUTEX_DEFN(JmethodIdCreation_lock          , PaddedMutex  , nosafepoint-2); // used for creating jmethodIDs.
  MUTEX_DEFN(InvokeMethodTypeTable_lock      , PaddedMutex  , safepoint);
  MUTEX_DEFN(InvokeMethodIntrinsicTable_lock , PaddedMonitor, safepoint);
  MUTEX_DEFN(AdapterHandlerLibrary_lock      , PaddedMutex  , safepoint);
  MUTEX_DEFN(SharedDictionary_lock           , PaddedMutex  , safepoint);
  MUTEX_DEFN(VMStatistic_lock                , PaddedMutex  , safepoint);
  MUTEX_DEFN(SignatureHandlerLibrary_lock    , PaddedMutex  , safepoint);
  MUTEX_DEFN(SymbolArena_lock                , PaddedMutex  , nosafepoint);
  MUTEX_DEFN(ExceptionCache_lock             , PaddedMutex  , safepoint);
#ifndef PRODUCT
  MUTEX_DEFN(FullGCALot_lock                 , PaddedMutex  , safepoint); // a lock to make FullGCALot MT safe
#endif
  MUTEX_DEFN(BeforeExit_lock                 , PaddedMonitor, safepoint);

  MUTEX_DEFN(NonJavaThreadsList_lock         , PaddedMutex  , nosafepoint-1);
  MUTEX_DEFN(NonJavaThreadsListSync_lock     , PaddedMutex  , nosafepoint);

  MUTEX_DEFN(RetData_lock                    , PaddedMutex  , safepoint);
  MUTEX_DEFN(Terminator_lock                 , PaddedMonitor, safepoint, true);
  MUTEX_DEFN(InitCompleted_lock              , PaddedMonitor, nosafepoint);
  MUTEX_DEFN(Notify_lock                     , PaddedMonitor, safepoint, true);

  MUTEX_DEFN(JfieldIdCreation_lock           , PaddedMutex  , safepoint);

  MUTEX_DEFN(CompiledIC_lock                 , PaddedMutex  , nosafepoint);  // locks VtableStubs_lock, InlineCacheBuffer_lock
  MUTEX_DEFN(MethodCompileQueue_lock         , PaddedMonitor, safepoint);
  MUTEX_DEFN(CompileStatistics_lock          , PaddedMutex  , safepoint);
  MUTEX_DEFN(DirectivesStack_lock            , PaddedMutex  , nosafepoint);
  MUTEX_DEFN(MultiArray_lock                 , PaddedMutex  , safepoint);

  MUTEX_DEFN(JvmtiThreadState_lock           , PaddedMutex  , safepoint);   // Used by JvmtiThreadState/JvmtiEventController
  MUTEX_DEFN(EscapeBarrier_lock              , PaddedMonitor, nosafepoint); // Used to synchronize object reallocation/relocking triggered by JVMTI
  MUTEX_DEFN(JvmtiVTMSTransition_lock        , PaddedMonitor, safepoint);   // used for Virtual Thread Mount State transition management
  MUTEX_DEFN(Management_lock                 , PaddedMutex  , safepoint);   // used for JVM management

  MUTEX_DEFN(ConcurrentGCBreakpoints_lock    , PaddedMonitor, safepoint, true);
  MUTEX_DEFN(TouchedMethodLog_lock           , PaddedMutex  , safepoint);

  MUTEX_DEFN(CompileThread_lock              , PaddedMonitor, safepoint);
  MUTEX_DEFN(PeriodicTask_lock               , PaddedMonitor, safepoint, true);
  MUTEX_DEFN(RedefineClasses_lock            , PaddedMonitor, safepoint);
  MUTEX_DEFN(Verify_lock                     , PaddedMutex  , safepoint);

  if (WhiteBoxAPI) {
    MUTEX_DEFN(Compilation_lock              , PaddedMonitor, nosafepoint);
  }

#if INCLUDE_JFR
  MUTEX_DEFN(JfrBuffer_lock                  , PaddedMutex  , event);
  MUTEX_DEFN(JfrMsg_lock                     , PaddedMonitor, event);
  MUTEX_DEFN(JfrStacktrace_lock              , PaddedMutex  , stackwatermark-1);
  MUTEX_DEFN(JfrThreadSampler_lock           , PaddedMonitor, nosafepoint);
#endif

#ifndef SUPPORTS_NATIVE_CX8
  MUTEX_DEFN(UnsafeJlong_lock                , PaddedMutex  , nosafepoint);
#endif

  MUTEX_DEFN(ContinuationRelativize_lock     , PaddedMonitor, nosafepoint-4);
  MUTEX_DEFN(CodeHeapStateAnalytics_lock     , PaddedMutex  , safepoint);
  MUTEX_DEFN(ThreadsSMRDelete_lock           , PaddedMonitor, nosafepoint-4); // Holds ConcurrentHashTableResize_lock
  MUTEX_DEFN(ThreadIdTableCreate_lock        , PaddedMutex  , safepoint);
  MUTEX_DEFN(SharedDecoder_lock              , PaddedMutex  , tty-1);
  MUTEX_DEFN(DCmdFactory_lock                , PaddedMutex  , nosafepoint);
  MUTEX_DEFN(NMTQuery_lock                   , PaddedMutex  , safepoint);
#if INCLUDE_CDS
#if INCLUDE_JVMTI
  MUTEX_DEFN(CDSClassFileStream_lock         , PaddedMutex  , safepoint);
#endif
  MUTEX_DEFN(DumpTimeTable_lock              , PaddedMutex  , nosafepoint);
  MUTEX_DEFN(CDSLambda_lock                  , PaddedMutex  , nosafepoint);
  MUTEX_DEFN(DumpRegion_lock                 , PaddedMutex  , nosafepoint);
  MUTEX_DEFN(ClassListFile_lock              , PaddedMutex  , nosafepoint);
  MUTEX_DEFN(UnregisteredClassesTable_lock   , PaddedMutex  , nosafepoint-1);
  MUTEX_DEFN(LambdaFormInvokers_lock         , PaddedMutex  , safepoint);
  MUTEX_DEFN(ScratchObjects_lock             , PaddedMutex  , nosafepoint-1); // Holds DumpTimeTable_lock
#endif // INCLUDE_CDS
  MUTEX_DEFN(Bootclasspath_lock              , PaddedMutex  , nosafepoint);
  MUTEX_DEFN(Zip_lock                        , PaddedMonitor, nosafepoint-1); // Holds DumpTimeTable_lock

#if INCLUDE_JVMCI
  // JVMCIRuntime::_lock must be acquired before JVMCI_lock to avoid deadlock
  MUTEX_DEFN(JVMCIRuntime_lock               , PaddedMonitor, safepoint, true);
#endif

  // These locks have relative rankings, and inherit safepoint checking attributes from that rank.
  MUTEX_DEFL(InlineCacheBuffer_lock         , PaddedMutex  , CompiledIC_lock);
  MUTEX_DEFL(VtableStubs_lock               , PaddedMutex  , CompiledIC_lock);  // Also holds DumpTimeTable_lock
  MUTEX_DEFL(CodeCache_lock                 , PaddedMonitor, VtableStubs_lock);
  MUTEX_DEFL(CompiledMethod_lock            , PaddedMutex  , CodeCache_lock);

  MUTEX_DEFL(Threads_lock                   , PaddedMonitor, CompileThread_lock, true);
  MUTEX_DEFL(Compile_lock                   , PaddedMutex  , MethodCompileQueue_lock);
  MUTEX_DEFL(Heap_lock                      , PaddedMonitor, AdapterHandlerLibrary_lock);

  MUTEX_DEFL(PerfDataMemAlloc_lock          , PaddedMutex  , Heap_lock);
  MUTEX_DEFL(PerfDataManager_lock           , PaddedMutex  , Heap_lock);
  MUTEX_DEFL(ClassLoaderDataGraph_lock      , PaddedMutex  , MultiArray_lock);
  MUTEX_DEFL(VMOperation_lock               , PaddedMonitor, Heap_lock, true);
  MUTEX_DEFL(ClassInitError_lock            , PaddedMonitor, Threads_lock);

  if (UseG1GC) {
    MUTEX_DEFL(G1OldGCCount_lock            , PaddedMonitor, Threads_lock, true);
    MUTEX_DEFL(G1RareEvent_lock             , PaddedMutex  , Threads_lock, true);
  }

  MUTEX_DEFL(CompileTaskAlloc_lock          , PaddedMutex  ,  MethodCompileQueue_lock);
#ifdef INCLUDE_PARALLELGC
  if (UseParallelGC) {
    MUTEX_DEFL(PSOldGenExpand_lock          , PaddedMutex  , Heap_lock, true);
  }
#endif
  MUTEX_DEFL(OopMapCacheAlloc_lock          , PaddedMutex  ,  Threads_lock, true);
  MUTEX_DEFL(Module_lock                    , PaddedMutex  ,  ClassLoaderDataGraph_lock);
  MUTEX_DEFL(SystemDictionary_lock          , PaddedMonitor, Module_lock);
  MUTEX_DEFL(JNICritical_lock               , PaddedMonitor, AdapterHandlerLibrary_lock); // used for JNI critical regions
#if INCLUDE_JVMCI
  // JVMCIRuntime_lock must be acquired before JVMCI_lock to avoid deadlock
  MUTEX_DEFL(JVMCI_lock                     , PaddedMonitor, JVMCIRuntime_lock);
#endif
}

#undef MUTEX_DEFL
#undef MUTEX_DEFN
#undef MUTEX_DEF
#undef MUTEX_STORAGE
#undef MUTEX_STORAGE_NAME

void MutexLockerImpl::post_initialize() {
  // Print mutex ranks if requested.
  LogTarget(Info, vmmutex) lt;
  if (lt.is_enabled()) {
    ResourceMark rm;
    LogStream ls(lt);
    print_lock_ranks(&ls);
  }
}

GCMutexLocker::GCMutexLocker(Mutex* mutex) {
  if (SafepointSynchronize::is_at_safepoint()) {
    _locked = false;
  } else {
    _mutex = mutex;
    _locked = true;
    _mutex->lock();
  }
}

// Print all mutexes/monitors that are currently owned by a thread; called
// by fatal error handler.
void print_owned_locks_on_error(outputStream* st) {
  st->print("VM Mutex/Monitor currently owned by a thread: ");
  bool none = true;
  for (int i = 0; i < _num_mutex; i++) {
     // see if it has an owner
     if (_mutex_array[i]->owner() != nullptr) {
       if (none) {
          // print format used by Mutex::print_on_error()
          st->print_cr(" ([mutex/lock_event])");
          none = false;
       }
       _mutex_array[i]->print_on_error(st);
       st->cr();
     }
  }
  if (none) st->print_cr("None");
}

void print_lock_ranks(outputStream* st) {
  st->print_cr("VM Mutex/Monitor ranks: ");

#ifdef ASSERT
  // Be extra defensive and figure out the bounds on
  // ranks right here. This also saves a bit of time
  // in the #ranks*#mutexes loop below.
  int min_rank = INT_MAX;
  int max_rank = INT_MIN;
  for (int i = 0; i < _num_mutex; i++) {
    Mutex* m = _mutex_array[i];
    int r = (int) m->rank();
    if (min_rank > r) min_rank = r;
    if (max_rank < r) max_rank = r;
  }

  // Print the listings rank by rank
  for (int r = min_rank; r <= max_rank; r++) {
    bool first = true;
    for (int i = 0; i < _num_mutex; i++) {
      Mutex* m = _mutex_array[i];
      if (r != (int) m->rank()) continue;

      if (first) {
        st->cr();
        st->print_cr("Rank \"%s\":", m->rank_name());
        first = false;
      }
      st->print_cr("  %s", m->name());
    }
  }
#else
  st->print_cr("  Only known in debug builds.");
#endif // ASSERT
}

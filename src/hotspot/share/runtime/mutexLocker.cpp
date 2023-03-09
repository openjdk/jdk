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
#include "runtime/os.inline.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/vmThread.hpp"

// Mutexes used in the VM (see comment in mutexLocker.hpp):
//
// Note that the following pointers are effectively final -- after having been
// set at JVM startup-time, they should never be subsequently mutated.
// Instead of using pointers to malloc()ed monitors and mutexes we should consider
// eliminating the indirection and using instances instead.
// Consider using GCC's __read_mostly.

Mutex*   Patching_lock                = nullptr;
Mutex*   CompiledMethod_lock          = nullptr;
Monitor* SystemDictionary_lock        = nullptr;
Mutex*   InvokeMethodTable_lock       = nullptr;
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
  if (DebuggingContext::is_enabled()) return;
  // check if this thread owns the lock (common case)
  assert(lock != nullptr, "Need non-null lock");
  if (lock->owned_by_self()) return;
  if (SafepointSynchronize::is_at_safepoint()) return;
  if (!Universe::is_fully_initialized()) return;
  fatal("must own lock %s", lock->name());
}

// a weaker assertion than the above
void assert_locked_or_safepoint_weak(const Mutex* lock) {
  if (DebuggingContext::is_enabled()) return;
  assert(lock != nullptr, "Need non-null lock");
  if (lock->is_locked()) return;
  if (SafepointSynchronize::is_at_safepoint()) return;
  if (!Universe::is_fully_initialized()) return;
  fatal("must own lock %s", lock->name());
}

// a stronger assertion than the above
void assert_lock_strong(const Mutex* lock) {
  if (DebuggingContext::is_enabled()) return;
  assert(lock != nullptr, "Need non-null lock");
  if (lock->owned_by_self()) return;
  fatal("must own lock %s", lock->name());
}
#endif

static void add_mutex(Mutex* var) {
  assert(_num_mutex < MAX_NUM_MUTEX, "increase MAX_NUM_MUTEX");
  _mutex_array[_num_mutex++] = var;
}

#define def(var, type, pri, ...) {            \
  var = new type(Mutex::pri, #var, ##__VA_ARGS__); \
  add_mutex(var);                             \
}

// Specify relative ranked lock
#ifdef ASSERT
#define defl(var, type, held_lock, ...) {         \
  var = new type(held_lock->rank()-1, #var, ##__VA_ARGS__); \
  add_mutex(var);                                      \
}
#else
#define defl(var, type, held_lock, ...) {         \
  var = new type(Mutex::safepoint, #var, ##__VA_ARGS__); \
  add_mutex(var);                                      \
}
#endif

// Using Padded subclasses to prevent false sharing of these global monitors and mutexes.
void mutex_init() {
  def(tty_lock                     , PaddedMutex  , tty);      // allow to lock in VM

  def(STS_lock                     , PaddedMonitor, nosafepoint);

  if (UseG1GC) {
    def(CGC_lock                   , PaddedMonitor, nosafepoint);

    def(G1DetachedRefinementStats_lock, PaddedMutex, nosafepoint-2);

    def(FreeList_lock              , PaddedMutex  , service-1);
    def(OldSets_lock               , PaddedMutex  , nosafepoint);
    def(Uncommit_lock              , PaddedMutex  , service-2);
    def(RootRegionScan_lock        , PaddedMonitor, nosafepoint-1);

    def(MarkStackFreeList_lock     , PaddedMutex  , nosafepoint);
    def(MarkStackChunkList_lock    , PaddedMutex  , nosafepoint);

    def(MonitoringSupport_lock     , PaddedMutex  , service-1);      // used for serviceability monitoring support
  }
  def(StringDedup_lock             , PaddedMonitor, nosafepoint);
  def(StringDedupIntern_lock       , PaddedMutex  , nosafepoint);
  def(RawMonitor_lock              , PaddedMutex  , nosafepoint-1);

  def(Metaspace_lock               , PaddedMutex  , nosafepoint-3);
  def(MetaspaceCritical_lock       , PaddedMonitor, nosafepoint-1);

  def(Patching_lock                , PaddedMutex  , nosafepoint);      // used for safepointing and code patching.
  def(MonitorDeflation_lock        , PaddedMonitor, nosafepoint);      // used for monitor deflation thread operations
  def(Service_lock                 , PaddedMonitor, service);      // used for service thread operations

  if (UseNotificationThread) {
    def(Notification_lock          , PaddedMonitor, service);  // used for notification thread operations
  } else {
    Notification_lock = Service_lock;
  }

  def(JmethodIdCreation_lock       , PaddedMutex  , nosafepoint-2); // used for creating jmethodIDs.
  def(InvokeMethodTable_lock       , PaddedMutex  , safepoint);
  def(SharedDictionary_lock        , PaddedMutex  , safepoint);
  def(VMStatistic_lock             , PaddedMutex  , safepoint);
  def(SignatureHandlerLibrary_lock , PaddedMutex  , safepoint);
  def(SymbolArena_lock             , PaddedMutex  , nosafepoint);
  def(ExceptionCache_lock          , PaddedMutex  , safepoint);
#ifndef PRODUCT
  def(FullGCALot_lock              , PaddedMutex  , safepoint); // a lock to make FullGCALot MT safe
#endif
  def(BeforeExit_lock              , PaddedMonitor, safepoint);

  def(NonJavaThreadsList_lock      , PaddedMutex,   nosafepoint-1);
  def(NonJavaThreadsListSync_lock  , PaddedMutex,   nosafepoint);

  def(RetData_lock                 , PaddedMutex  , safepoint);
  def(Terminator_lock              , PaddedMonitor, safepoint, true);
  def(InitCompleted_lock           , PaddedMonitor, nosafepoint);
  def(Notify_lock                  , PaddedMonitor, safepoint, true);

  def(Heap_lock                    , PaddedMonitor, safepoint); // Doesn't safepoint check during termination.
  def(JfieldIdCreation_lock        , PaddedMutex  , safepoint);

  def(CompiledIC_lock              , PaddedMutex  , nosafepoint);  // locks VtableStubs_lock, InlineCacheBuffer_lock
  def(MethodCompileQueue_lock      , PaddedMonitor, safepoint);
  def(CompileStatistics_lock       , PaddedMutex  , safepoint);
  def(DirectivesStack_lock         , PaddedMutex  , nosafepoint);
  def(MultiArray_lock              , PaddedMutex  , safepoint);

  def(JvmtiThreadState_lock        , PaddedMutex  , safepoint);   // Used by JvmtiThreadState/JvmtiEventController
  def(EscapeBarrier_lock           , PaddedMonitor, nosafepoint); // Used to synchronize object reallocation/relocking triggered by JVMTI
  def(JvmtiVTMSTransition_lock     , PaddedMonitor, safepoint);   // used for Virtual Thread Mount State transition management
  def(Management_lock              , PaddedMutex  , safepoint);   // used for JVM management

  def(ConcurrentGCBreakpoints_lock , PaddedMonitor, safepoint, true);
  def(TouchedMethodLog_lock        , PaddedMutex  , safepoint);

  def(CompileThread_lock           , PaddedMonitor, safepoint);
  def(PeriodicTask_lock            , PaddedMonitor, safepoint, true);
  def(RedefineClasses_lock         , PaddedMonitor, safepoint);
  def(Verify_lock                  , PaddedMutex,   safepoint);

  if (WhiteBoxAPI) {
    def(Compilation_lock           , PaddedMonitor, nosafepoint);
  }

#if INCLUDE_JFR
  def(JfrBuffer_lock               , PaddedMutex  , nosafepoint);
  def(JfrMsg_lock                  , PaddedMonitor, nosafepoint-3);
  def(JfrStacktrace_lock           , PaddedMutex  , stackwatermark-1);
  def(JfrThreadSampler_lock        , PaddedMonitor, nosafepoint);
#endif

#ifndef SUPPORTS_NATIVE_CX8
  def(UnsafeJlong_lock             , PaddedMutex  , nosafepoint);
#endif

  def(ContinuationRelativize_lock  , PaddedMonitor, nosafepoint-3);
  def(CodeHeapStateAnalytics_lock  , PaddedMutex  , safepoint);
  def(ThreadsSMRDelete_lock        , PaddedMonitor, nosafepoint-3); // Holds ConcurrentHashTableResize_lock
  def(ThreadIdTableCreate_lock     , PaddedMutex  , safepoint);
  def(SharedDecoder_lock           , PaddedMutex  , tty-1);
  def(DCmdFactory_lock             , PaddedMutex  , nosafepoint);
  def(NMTQuery_lock                , PaddedMutex  , safepoint);
#if INCLUDE_CDS
#if INCLUDE_JVMTI
  def(CDSClassFileStream_lock      , PaddedMutex  , safepoint);
#endif
  def(DumpTimeTable_lock           , PaddedMutex  , nosafepoint);
  def(CDSLambda_lock               , PaddedMutex  , nosafepoint);
  def(DumpRegion_lock              , PaddedMutex  , nosafepoint);
  def(ClassListFile_lock           , PaddedMutex  , nosafepoint);
  def(LambdaFormInvokers_lock      , PaddedMutex  , safepoint);
  def(ScratchObjects_lock          , PaddedMutex  , nosafepoint-1); // Holds DumpTimeTable_lock
#endif // INCLUDE_CDS
  def(Bootclasspath_lock           , PaddedMutex  , nosafepoint);
  def(Zip_lock                     , PaddedMonitor, nosafepoint-1); // Holds DumpTimeTable_lock

#if INCLUDE_JVMCI
  // JVMCIRuntime::_lock must be acquired before JVMCI_lock to avoid deadlock
  def(JVMCIRuntime_lock            , PaddedMonitor, safepoint, true);
#endif

  // These locks have relative rankings, and inherit safepoint checking attributes from that rank.
  defl(InlineCacheBuffer_lock      , PaddedMutex  , CompiledIC_lock);
  defl(VtableStubs_lock            , PaddedMutex  , CompiledIC_lock);  // Also holds DumpTimeTable_lock
  defl(CodeCache_lock              , PaddedMonitor, VtableStubs_lock);
  defl(CompiledMethod_lock         , PaddedMutex  , CodeCache_lock);

  defl(Threads_lock                , PaddedMonitor, CompileThread_lock, true);
  defl(Compile_lock                , PaddedMutex  , MethodCompileQueue_lock);
  defl(AdapterHandlerLibrary_lock  , PaddedMutex  , InvokeMethodTable_lock);
  defl(Heap_lock                   , PaddedMonitor, AdapterHandlerLibrary_lock);

  defl(PerfDataMemAlloc_lock       , PaddedMutex  , Heap_lock);
  defl(PerfDataManager_lock        , PaddedMutex  , Heap_lock);
  defl(ClassLoaderDataGraph_lock   , PaddedMutex  , MultiArray_lock);
  defl(VMOperation_lock            , PaddedMonitor, Heap_lock, true);
  defl(ClassInitError_lock         , PaddedMonitor, Threads_lock);

  if (UseG1GC) {
    defl(G1OldGCCount_lock         , PaddedMonitor, Threads_lock, true);
    defl(G1RareEvent_lock          , PaddedMutex  , Threads_lock, true);
  }

  defl(CompileTaskAlloc_lock       , PaddedMutex ,  MethodCompileQueue_lock);
#ifdef INCLUDE_PARALLELGC
  if (UseParallelGC) {
    defl(PSOldGenExpand_lock   , PaddedMutex , Heap_lock, true);
  }
#endif
  defl(OopMapCacheAlloc_lock       , PaddedMutex ,  Threads_lock, true);
  defl(Module_lock                 , PaddedMutex ,  ClassLoaderDataGraph_lock);
  defl(SystemDictionary_lock       , PaddedMonitor, Module_lock);
  defl(JNICritical_lock            , PaddedMonitor, AdapterHandlerLibrary_lock); // used for JNI critical regions
#if INCLUDE_JVMCI
  // JVMCIRuntime_lock must be acquired before JVMCI_lock to avoid deadlock
  defl(JVMCI_lock                  , PaddedMonitor, JVMCIRuntime_lock);
#endif
}

void MutexLocker::post_initialize() {
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

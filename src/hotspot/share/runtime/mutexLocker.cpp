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

StaticLock<PaddedMutex>   Patching_lock;
StaticLock<PaddedMutex>   CompiledMethod_lock;
StaticLock<PaddedMonitor> SystemDictionary_lock;
StaticLock<PaddedMutex>   InvokeMethodTable_lock;
StaticLock<PaddedMutex>   SharedDictionary_lock;
StaticLock<PaddedMonitor> ClassInitError_lock;
StaticLock<PaddedMutex>   Module_lock;
StaticLock<PaddedMutex>   CompiledIC_lock;
StaticLock<PaddedMutex>   InlineCacheBuffer_lock;
StaticLock<PaddedMutex>   VMStatistic_lock;
StaticLock<PaddedMutex>   JmethodIdCreation_lock;
StaticLock<PaddedMutex>   JfieldIdCreation_lock;
StaticLock<PaddedMonitor> JNICritical_lock;
StaticLock<PaddedMutex>   JvmtiThreadState_lock;
StaticLock<PaddedMonitor> EscapeBarrier_lock;
StaticLock<PaddedMonitor> JvmtiVTMSTransition_lock;
StaticLock<PaddedMonitor> Heap_lock;
#ifdef INCLUDE_PARALLELGC
StaticLock<PaddedMutex>   PSOldGenExpand_lock;
#endif
StaticLock<PaddedMutex>   AdapterHandlerLibrary_lock;
StaticLock<PaddedMutex>   SignatureHandlerLibrary_lock;
StaticLock<PaddedMutex>   VtableStubs_lock;
StaticLock<PaddedMutex>   SymbolArena_lock;
StaticLock<PaddedMonitor> StringDedup_lock;
StaticLock<PaddedMutex>   StringDedupIntern_lock;
StaticLock<PaddedMonitor> CodeCache_lock;
StaticLock<PaddedMutex>   TouchedMethodLog_lock;
StaticLock<PaddedMutex>   RetData_lock;
StaticLock<PaddedMonitor> VMOperation_lock;
StaticLock<PaddedMonitor> Threads_lock;
StaticLock<PaddedMutex>   NonJavaThreadsList_lock;
StaticLock<PaddedMutex>   NonJavaThreadsListSync_lock;
StaticLock<PaddedMonitor> CGC_lock;
StaticLock<PaddedMonitor> STS_lock;
StaticLock<PaddedMonitor> G1OldGCCount_lock;
StaticLock<PaddedMutex>   G1RareEvent_lock;
StaticLock<PaddedMutex>   G1DetachedRefinementStats_lock;
StaticLock<PaddedMutex>   MarkStackFreeList_lock;
StaticLock<PaddedMutex>   MarkStackChunkList_lock;
StaticLock<PaddedMutex>   MonitoringSupport_lock;
StaticLock<PaddedMonitor> ConcurrentGCBreakpoints_lock;
StaticLock<PaddedMutex>   Compile_lock;
StaticLock<PaddedMonitor> MethodCompileQueue_lock;
StaticLock<PaddedMonitor> CompileThread_lock;
StaticLock<PaddedMonitor> Compilation_lock;
StaticLock<PaddedMutex>   CompileTaskAlloc_lock;
StaticLock<PaddedMutex>   CompileStatistics_lock;
StaticLock<PaddedMutex>   DirectivesStack_lock;
StaticLock<PaddedMutex>   MultiArray_lock;
StaticLock<PaddedMonitor> Terminator_lock;
StaticLock<PaddedMonitor> InitCompleted_lock;
StaticLock<PaddedMonitor> BeforeExit_lock;
StaticLock<PaddedMonitor> Notify_lock;
StaticLock<PaddedMutex>   ExceptionCache_lock;
#ifndef PRODUCT
StaticLock<PaddedMutex>   FullGCALot_lock;
#endif

StaticLock<PaddedMutex>   tty_lock;

StaticLock<PaddedMutex>   RawMonitor_lock;
StaticLock<PaddedMutex>   PerfDataMemAlloc_lock;
StaticLock<PaddedMutex>   PerfDataManager_lock;
StaticLock<PaddedMutex>   OopMapCacheAlloc_lock;

StaticLock<PaddedMutex>   FreeList_lock;
StaticLock<PaddedMutex>   OldSets_lock;
StaticLock<PaddedMutex>   Uncommit_lock;
StaticLock<PaddedMonitor> RootRegionScan_lock;

StaticLock<PaddedMutex>   Management_lock;
StaticLock<PaddedMonitor> MonitorDeflation_lock;
StaticLock<PaddedMonitor> Service_lock;
StaticLock<PaddedMonitor> Notification_lock;
StaticLock<PaddedMonitor> PeriodicTask_lock;
StaticLock<PaddedMonitor> RedefineClasses_lock;
StaticLock<PaddedMutex>   Verify_lock;
StaticLock<PaddedMonitor> Zip_lock;

#if INCLUDE_JFR
StaticLock<PaddedMutex>   JfrStacktrace_lock;
StaticLock<PaddedMonitor> JfrMsg_lock;
StaticLock<PaddedMutex>   JfrBuffer_lock;
StaticLock<PaddedMonitor> JfrThreadSampler_lock;
#endif

#ifndef SUPPORTS_NATIVE_CX8
StaticLock<PaddedMutex>   UnsafeJlong_lock;
#endif
StaticLock<PaddedMutex>   CodeHeapStateAnalytics_lock;

StaticLock<PaddedMonitor> ContinuationRelativize_lock;

StaticLock<PaddedMutex>   Metaspace_lock;
StaticLock<PaddedMonitor> MetaspaceCritical_lock;
StaticLock<PaddedMutex>   ClassLoaderDataGraph_lock;
StaticLock<PaddedMonitor> ThreadsSMRDelete_lock;
StaticLock<PaddedMutex>   ThreadIdTableCreate_lock;
StaticLock<PaddedMutex>   SharedDecoder_lock;
StaticLock<PaddedMutex>   DCmdFactory_lock;
StaticLock<PaddedMutex>   NMTQuery_lock;

#if INCLUDE_CDS
#if INCLUDE_JVMTI
StaticLock<PaddedMutex>   CDSClassFileStream_lock;
#endif
StaticLock<PaddedMutex>   DumpTimeTable_lock;
StaticLock<PaddedMutex>   CDSLambda_lock;
StaticLock<PaddedMutex>   DumpRegion_lock;
StaticLock<PaddedMutex>   ClassListFile_lock;
StaticLock<PaddedMutex>   UnregisteredClassesTable_lock;
StaticLock<PaddedMutex>   LambdaFormInvokers_lock;
StaticLock<PaddedMutex>   ScratchObjects_lock;
#endif // INCLUDE_CDS
StaticLock<PaddedMutex>   Bootclasspath_lock;

#if INCLUDE_JVMCI
StaticLock<PaddedMonitor> JVMCI_lock;
StaticLock<PaddedMonitor> JVMCIRuntime_lock;
#endif


#define MAX_NUM_MUTEX 128
static Mutex* _mutex_array[MAX_NUM_MUTEX];
static int _num_mutex;

#ifdef ASSERT
void assert_locked_or_safepoint(const Mutex* lock) {
  // check if this thread owns the lock (common case)
  assert(lock != nullptr, "Need non-null lock");
  if (lock->owned_by_self()) return;
  if (SafepointSynchronize::is_at_safepoint()) return;
  if (!Universe::is_fully_initialized()) return;
  fatal("must own lock %s", lock->name());
}

// a weaker assertion than the above
void assert_locked_or_safepoint_weak(const Mutex* lock) {
  assert(lock != nullptr, "Need non-null lock");
  if (lock->is_locked()) return;
  if (SafepointSynchronize::is_at_safepoint()) return;
  if (!Universe::is_fully_initialized()) return;
  fatal("must own lock %s", lock->name());
}

// a stronger assertion than the above
void assert_lock_strong(const Mutex* lock) {
  assert(lock != nullptr, "Need non-null lock");
  if (lock->owned_by_self()) return;
  fatal("must own lock %s", lock->name());
}
#endif

static void add_mutex(Mutex* var) {
  assert(_num_mutex < MAX_NUM_MUTEX, "increase MAX_NUM_MUTEX");
  _mutex_array[_num_mutex++] = var;
}

#define def(var, pri, ...) {                 \
  var.init(Mutex::pri, #var, ##__VA_ARGS__); \
  add_mutex(var.get());                      \
}

// Specify relative ranked lock
#ifdef ASSERT
#define defl(var, held_lock, ...) {                   \
  var.init(held_lock->rank()-1, #var, ##__VA_ARGS__); \
  add_mutex(var.get());                               \
}
#else
#define defl(var, held_lock, ...) {                \
  var.init(Mutex::safepoint, #var, ##__VA_ARGS__); \
  add_mutex(var.get());                            \
}
#endif

// Using Padded subclasses to prevent false sharing of these global monitors and mutexes.
void mutex_init() {
  def(tty_lock                     , tty);      // allow to lock in VM

  def(STS_lock                     , nosafepoint);

  if (UseG1GC) {
    def(CGC_lock                   , nosafepoint);

    def(G1DetachedRefinementStats_lock, nosafepoint-2);

    def(FreeList_lock              , service-1);
    def(OldSets_lock               , nosafepoint);
    def(Uncommit_lock              , service-2);
    def(RootRegionScan_lock        , nosafepoint-1);

    def(MarkStackFreeList_lock     , nosafepoint);
    def(MarkStackChunkList_lock    , nosafepoint);

    def(MonitoringSupport_lock     , service-1);      // used for serviceability monitoring support
  }
  def(StringDedup_lock             , nosafepoint);
  def(StringDedupIntern_lock       , nosafepoint);
  def(RawMonitor_lock              , nosafepoint-1);

  def(Metaspace_lock               , nosafepoint-3);
  def(MetaspaceCritical_lock       , nosafepoint-1);

  def(Patching_lock                , nosafepoint);      // used for safepointing and code patching.
  def(MonitorDeflation_lock        , nosafepoint);      // used for monitor deflation thread operations
  def(Service_lock                 , service);      // used for service thread operations

  def(Notification_lock            , service);  // used for notification thread operations

  def(JmethodIdCreation_lock       , nosafepoint-2); // used for creating jmethodIDs.
  def(InvokeMethodTable_lock       , safepoint);
  def(SharedDictionary_lock        , safepoint);
  def(VMStatistic_lock             , safepoint);
  def(SignatureHandlerLibrary_lock , safepoint);
  def(SymbolArena_lock             , nosafepoint);
  def(ExceptionCache_lock          , safepoint);
#ifndef PRODUCT
  def(FullGCALot_lock              , safepoint); // a lock to make FullGCALot MT safe
#endif
  def(BeforeExit_lock              , safepoint);

  def(NonJavaThreadsList_lock      ,   nosafepoint-1);
  def(NonJavaThreadsListSync_lock  ,   nosafepoint);

  def(RetData_lock                 , safepoint);
  def(Terminator_lock              , safepoint, true);
  def(InitCompleted_lock           , nosafepoint);
  def(Notify_lock                  , safepoint, true);

  def(Heap_lock                    , safepoint); // Doesn't safepoint check during termination.
  def(JfieldIdCreation_lock        , safepoint);

  def(CompiledIC_lock              , nosafepoint);  // locks VtableStubs_lock, InlineCacheBuffer_lock
  def(MethodCompileQueue_lock      , safepoint);
  def(CompileStatistics_lock       , safepoint);
  def(DirectivesStack_lock         , nosafepoint);
  def(MultiArray_lock              , safepoint);

  def(JvmtiThreadState_lock        , safepoint);   // Used by JvmtiThreadState/JvmtiEventController
  def(EscapeBarrier_lock           , nosafepoint); // Used to synchronize object reallocation/relocking triggered by JVMTI
  def(JvmtiVTMSTransition_lock     , safepoint);   // used for Virtual Thread Mount State transition management
  def(Management_lock              , safepoint);   // used for JVM management

  def(ConcurrentGCBreakpoints_lock , safepoint, true);
  def(TouchedMethodLog_lock        , safepoint);

  def(CompileThread_lock           , safepoint);
  def(PeriodicTask_lock            , safepoint, true);
  def(RedefineClasses_lock         , safepoint);
  def(Verify_lock                  ,   safepoint);

  if (WhiteBoxAPI) {
    def(Compilation_lock           , nosafepoint);
  }

#if INCLUDE_JFR
  def(JfrBuffer_lock               , nosafepoint);
  def(JfrMsg_lock                  , nosafepoint-3);
  def(JfrStacktrace_lock           , stackwatermark-1);
  def(JfrThreadSampler_lock        , nosafepoint);
#endif

#ifndef SUPPORTS_NATIVE_CX8
  def(UnsafeJlong_lock             , nosafepoint);
#endif

  def(ContinuationRelativize_lock  , nosafepoint-3);
  def(CodeHeapStateAnalytics_lock  , safepoint);
  def(ThreadsSMRDelete_lock        , nosafepoint-3); // Holds ConcurrentHashTableResize_lock
  def(ThreadIdTableCreate_lock     , safepoint);
  def(SharedDecoder_lock           , tty-1);
  def(DCmdFactory_lock             , nosafepoint);
  def(NMTQuery_lock                , safepoint);
#if INCLUDE_CDS
#if INCLUDE_JVMTI
  def(CDSClassFileStream_lock      , safepoint);
#endif
  def(DumpTimeTable_lock           , nosafepoint);
  def(CDSLambda_lock               , nosafepoint);
  def(DumpRegion_lock              , nosafepoint);
  def(ClassListFile_lock           , nosafepoint);
  def(LambdaFormInvokers_lock      , safepoint);
  def(ScratchObjects_lock          , nosafepoint-1); // Holds DumpTimeTable_lock
#endif // INCLUDE_CDS
  def(Bootclasspath_lock           , nosafepoint);
  def(Zip_lock                     , nosafepoint-1); // Holds DumpTimeTable_lock

#if INCLUDE_JVMCI
  // JVMCIRuntime::_lock must be acquired before JVMCI_lock to avoid deadlock
  def(JVMCIRuntime_lock            , safepoint, true);
#endif

  // These locks have relative rankings, and inherit safepoint checking attributes from that rank.
  defl(InlineCacheBuffer_lock      , CompiledIC_lock);
  defl(VtableStubs_lock            , CompiledIC_lock);  // Also holds DumpTimeTable_lock
  defl(CodeCache_lock              , VtableStubs_lock);
  defl(CompiledMethod_lock         , CodeCache_lock);

  defl(Threads_lock                , CompileThread_lock, true);
  defl(Compile_lock                , MethodCompileQueue_lock);
  defl(AdapterHandlerLibrary_lock  , InvokeMethodTable_lock);
  defl(Heap_lock                   , AdapterHandlerLibrary_lock);

  defl(PerfDataMemAlloc_lock       , Heap_lock);
  defl(PerfDataManager_lock        , Heap_lock);
  defl(ClassLoaderDataGraph_lock   , MultiArray_lock);
  defl(VMOperation_lock            , Heap_lock, true);
  defl(ClassInitError_lock         , Threads_lock);

  if (UseG1GC) {
    defl(G1OldGCCount_lock         , Threads_lock, true);
    defl(G1RareEvent_lock          , Threads_lock, true);
  }

  defl(CompileTaskAlloc_lock       ,  MethodCompileQueue_lock);
#ifdef INCLUDE_PARALLELGC
  if (UseParallelGC) {
    defl(PSOldGenExpand_lock   , Heap_lock, true);
  }
#endif
  defl(OopMapCacheAlloc_lock       ,  Threads_lock, true);
  defl(Module_lock                 ,  ClassLoaderDataGraph_lock);
  defl(SystemDictionary_lock       , Module_lock);
  defl(JNICritical_lock            , AdapterHandlerLibrary_lock); // used for JNI critical regions
#if INCLUDE_JVMCI
  // JVMCIRuntime_lock must be acquired before JVMCI_lock to avoid deadlock
  defl(JVMCI_lock                  , JVMCIRuntime_lock);
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

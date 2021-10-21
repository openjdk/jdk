/*
 * Copyright (c) 1997, 2021, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/universe.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.inline.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/vmThread.hpp"

// Mutexes used in the VM (see comment in mutexLocker.hpp):
//
// Note that the following pointers are effectively final -- after having been
// set at JVM startup-time, they should never be subsequently mutated.
// Instead of using pointers to malloc()ed monitors and mutexes we should consider
// eliminating the indirection and using instances instead.
// Consider using GCC's __read_mostly.

Mutex*   Patching_lock                = NULL;
Mutex*   CompiledMethod_lock          = NULL;
Monitor* SystemDictionary_lock        = NULL;
Mutex*   SharedDictionary_lock        = NULL;
Monitor* ClassInitError_lock          = NULL;
Mutex*   Module_lock                  = NULL;
Mutex*   CompiledIC_lock              = NULL;
Mutex*   InlineCacheBuffer_lock       = NULL;
Mutex*   VMStatistic_lock             = NULL;
Mutex*   JNIHandleBlockFreeList_lock  = NULL;
Mutex*   JmethodIdCreation_lock       = NULL;
Mutex*   JfieldIdCreation_lock        = NULL;
Monitor* JNICritical_lock             = NULL;
Mutex*   JvmtiThreadState_lock        = NULL;
Monitor* EscapeBarrier_lock           = NULL;
Monitor* Heap_lock                    = NULL;
Mutex*   ExpandHeap_lock              = NULL;
Mutex*   AdapterHandlerLibrary_lock   = NULL;
Mutex*   SignatureHandlerLibrary_lock = NULL;
Mutex*   VtableStubs_lock             = NULL;
Mutex*   SymbolArena_lock             = NULL;
Monitor* StringDedup_lock             = NULL;
Mutex*   StringDedupIntern_lock       = NULL;
Monitor* CodeCache_lock               = NULL;
Monitor* CodeSweeper_lock             = NULL;
Mutex*   MethodData_lock              = NULL;
Mutex*   TouchedMethodLog_lock        = NULL;
Mutex*   RetData_lock                 = NULL;
Monitor* VMOperation_lock             = NULL;
Monitor* Threads_lock                 = NULL;
Mutex*   NonJavaThreadsList_lock      = NULL;
Mutex*   NonJavaThreadsListSync_lock  = NULL;
Monitor* CGC_lock                     = NULL;
Monitor* STS_lock                     = NULL;
Monitor* G1OldGCCount_lock            = NULL;
Mutex*   G1DetachedRefinementStats_lock = NULL;
Mutex*   MarkStackFreeList_lock       = NULL;
Mutex*   MarkStackChunkList_lock      = NULL;
Mutex*   MonitoringSupport_lock       = NULL;
Mutex*   ParGCRareEvent_lock          = NULL;
Monitor* ConcurrentGCBreakpoints_lock = NULL;
Mutex*   Compile_lock                 = NULL;
Monitor* MethodCompileQueue_lock      = NULL;
Monitor* CompileThread_lock           = NULL;
Monitor* Compilation_lock             = NULL;
Mutex*   CompileTaskAlloc_lock        = NULL;
Mutex*   CompileStatistics_lock       = NULL;
Mutex*   DirectivesStack_lock         = NULL;
Mutex*   MultiArray_lock              = NULL;
Monitor* Terminator_lock              = NULL;
Monitor* InitCompleted_lock           = NULL;
Monitor* BeforeExit_lock              = NULL;
Monitor* Notify_lock                  = NULL;
Mutex*   ExceptionCache_lock          = NULL;
Mutex*   NMethodSweeperStats_lock     = NULL;
#ifndef PRODUCT
Mutex*   FullGCALot_lock              = NULL;
#endif

Mutex*   tty_lock                     = NULL;

Mutex*   RawMonitor_lock              = NULL;
Mutex*   PerfDataMemAlloc_lock        = NULL;
Mutex*   PerfDataManager_lock         = NULL;
Mutex*   OopMapCacheAlloc_lock        = NULL;

Mutex*   FreeList_lock                = NULL;
Mutex*   OldSets_lock                 = NULL;
Mutex*   Uncommit_lock                = NULL;
Monitor* RootRegionScan_lock          = NULL;

Mutex*   Management_lock              = NULL;
Monitor* MonitorDeflation_lock        = NULL;
Monitor* Service_lock                 = NULL;
Monitor* Notification_lock            = NULL;
Monitor* PeriodicTask_lock            = NULL;
Monitor* RedefineClasses_lock         = NULL;
Mutex*   Verify_lock                  = NULL;
Monitor* Zip_lock                     = NULL;

#if INCLUDE_JFR
Mutex*   JfrStacktrace_lock           = NULL;
Monitor* JfrMsg_lock                  = NULL;
Mutex*   JfrBuffer_lock               = NULL;
Monitor* JfrThreadSampler_lock        = NULL;
#endif

#ifndef SUPPORTS_NATIVE_CX8
Mutex*   UnsafeJlong_lock             = NULL;
#endif
Mutex*   CodeHeapStateAnalytics_lock  = NULL;

Mutex*   Metaspace_lock               = NULL;
Mutex*   ClassLoaderDataGraph_lock    = NULL;
Monitor* ThreadsSMRDelete_lock        = NULL;
Mutex*   ThreadIdTableCreate_lock     = NULL;
Mutex*   SharedDecoder_lock           = NULL;
Mutex*   DCmdFactory_lock             = NULL;
#if INCLUDE_NMT
Mutex*   NMTQuery_lock                = NULL;
#endif
#if INCLUDE_CDS
#if INCLUDE_JVMTI
Mutex*   CDSClassFileStream_lock      = NULL;
#endif
Mutex*   DumpTimeTable_lock           = NULL;
Mutex*   CDSLambda_lock               = NULL;
Mutex*   DumpRegion_lock              = NULL;
Mutex*   ClassListFile_lock           = NULL;
Mutex*   UnregisteredClassesTable_lock= NULL;
Mutex*   LambdaFormInvokers_lock      = NULL;
#endif // INCLUDE_CDS
Mutex*   Bootclasspath_lock           = NULL;

#if INCLUDE_JVMCI
Monitor* JVMCI_lock                   = NULL;
#endif

#ifdef ASSERT
void assert_locked_or_safepoint(const Mutex* lock) {
  // check if this thread owns the lock (common case)
  assert(lock != NULL, "Need non-NULL lock");
  if (lock->owned_by_self()) return;
  if (SafepointSynchronize::is_at_safepoint()) return;
  if (!Universe::is_fully_initialized()) return;
  fatal("must own lock %s", lock->name());
}

// a weaker assertion than the above
void assert_locked_or_safepoint_weak(const Mutex* lock) {
  assert(lock != NULL, "Need non-NULL lock");
  if (lock->is_locked()) return;
  if (SafepointSynchronize::is_at_safepoint()) return;
  if (!Universe::is_fully_initialized()) return;
  fatal("must own lock %s", lock->name());
}

// a stronger assertion than the above
void assert_lock_strong(const Mutex* lock) {
  assert(lock != NULL, "Need non-NULL lock");
  if (lock->owned_by_self()) return;
  fatal("must own lock %s", lock->name());
}

void assert_locked_or_safepoint_or_handshake(const Mutex* lock, const JavaThread* thread) {
  if (thread->is_handshake_safe_for(Thread::current())) return;
  assert_locked_or_safepoint(lock);
}
#endif

#define def(var, type, pri, vm_block) {       \
  var = new type(Mutex::pri, #var, vm_block); \
}

// Specify relative ranked lock
#ifdef ASSERT
#define defl(var, type, held_lock, vm_block) {         \
  var = new type(held_lock->rank()-1, #var, vm_block); \
}
#else
#define defl(var, type, held_lock, vm_block) {         \
  var = new type(Mutex::safepoint, #var, vm_block);    \
}
#endif

// Using Padded subclasses to prevent false sharing of these global monitors and mutexes.
void mutex_init() {
  def(tty_lock                     , PaddedMutex  , tty,            true);      // allow to lock in VM

  def(STS_lock                     , PaddedMonitor, nosafepoint,    true);

  if (UseG1GC) {
    def(CGC_lock                   , PaddedMonitor, nosafepoint,    true);

    def(G1DetachedRefinementStats_lock, PaddedMutex, nosafepoint-2, true);

    def(FreeList_lock              , PaddedMutex  , service-1,      true);
    def(OldSets_lock               , PaddedMutex  , nosafepoint,    true);
    def(Uncommit_lock              , PaddedMutex  , service-2,      true);
    def(RootRegionScan_lock        , PaddedMonitor, nosafepoint-1,  true);

    def(MarkStackFreeList_lock     , PaddedMutex  , nosafepoint,    true);
    def(MarkStackChunkList_lock    , PaddedMutex  , nosafepoint,    true);

    def(MonitoringSupport_lock     , PaddedMutex  , service-1,      true);      // used for serviceability monitoring support
  }
  def(StringDedup_lock             , PaddedMonitor, nosafepoint,    true);
  def(StringDedupIntern_lock       , PaddedMutex  , nosafepoint,    true);
  def(ParGCRareEvent_lock          , PaddedMutex  , safepoint,      true);
  def(RawMonitor_lock              , PaddedMutex  , nosafepoint-1,  true);

  def(Metaspace_lock               , PaddedMutex  , nosafepoint-3,  true);

  def(Patching_lock                , PaddedMutex  , nosafepoint,    true);      // used for safepointing and code patching.
  def(MonitorDeflation_lock        , PaddedMonitor, nosafepoint,    true);      // used for monitor deflation thread operations
  def(Service_lock                 , PaddedMonitor, service,        true);      // used for service thread operations

  if (UseNotificationThread) {
    def(Notification_lock          , PaddedMonitor, service,        true);  // used for notification thread operations
  } else {
    Notification_lock = Service_lock;
  }

  def(JmethodIdCreation_lock       , PaddedMutex  , nosafepoint-2,  true); // used for creating jmethodIDs.

  def(SharedDictionary_lock        , PaddedMutex  , safepoint,      true);
  def(VMStatistic_lock             , PaddedMutex  , safepoint,      false);
  def(JNIHandleBlockFreeList_lock  , PaddedMutex  , nosafepoint-1,  true);      // handles are used by VM thread
  def(SignatureHandlerLibrary_lock , PaddedMutex  , safepoint,      false);
  def(SymbolArena_lock             , PaddedMutex  , nosafepoint,    true);
  def(ExceptionCache_lock          , PaddedMutex  , safepoint,      false);
#ifndef PRODUCT
  def(FullGCALot_lock              , PaddedMutex  , safepoint,      false); // a lock to make FullGCALot MT safe
#endif
  def(BeforeExit_lock              , PaddedMonitor, safepoint,      true);

  def(NonJavaThreadsList_lock      , PaddedMutex,   nosafepoint-1,  true);
  def(NonJavaThreadsListSync_lock  , PaddedMutex,   nosafepoint,    true);

  def(RetData_lock                 , PaddedMutex  , safepoint,      false);
  def(Terminator_lock              , PaddedMonitor, safepoint,      true);
  def(InitCompleted_lock           , PaddedMonitor, nosafepoint,    true);
  def(Notify_lock                  , PaddedMonitor, safepoint,      true);
  def(AdapterHandlerLibrary_lock   , PaddedMutex  , safepoint,      true);

  def(Heap_lock                    , PaddedMonitor, safepoint,      false); // Doesn't safepoint check during termination.
  def(JfieldIdCreation_lock        , PaddedMutex  , safepoint,      true);  // jfieldID, Used in VM_Operation

  def(CompiledIC_lock              , PaddedMutex  , nosafepoint,    true);  // locks VtableStubs_lock, InlineCacheBuffer_lock
  def(MethodCompileQueue_lock      , PaddedMonitor, safepoint,      false);
  def(CompileStatistics_lock       , PaddedMutex  , safepoint,      false);
  def(DirectivesStack_lock         , PaddedMutex  , nosafepoint,    true);
  def(MultiArray_lock              , PaddedMutex  , safepoint,      false);

  def(JvmtiThreadState_lock        , PaddedMutex  , safepoint,      false); // Used by JvmtiThreadState/JvmtiEventController
  def(EscapeBarrier_lock           , PaddedMonitor, nosafepoint,    true);  // Used to synchronize object reallocation/relocking triggered by JVMTI
  def(Management_lock              , PaddedMutex  , safepoint,      false); // used for JVM management

  def(ConcurrentGCBreakpoints_lock , PaddedMonitor, safepoint,      true);
  def(MethodData_lock              , PaddedMutex  , safepoint,      false);
  def(TouchedMethodLog_lock        , PaddedMutex  , safepoint,      false);

  def(CompileThread_lock           , PaddedMonitor, safepoint,      false);
  def(PeriodicTask_lock            , PaddedMonitor, safepoint,      true);
  def(RedefineClasses_lock         , PaddedMonitor, safepoint,      true);
  def(Verify_lock                  , PaddedMutex,   safepoint,      true);

  if (WhiteBoxAPI) {
    def(Compilation_lock           , PaddedMonitor, nosafepoint,    true);
  }

#if INCLUDE_JFR
  def(JfrBuffer_lock               , PaddedMutex  , nosafepoint,       true);
  def(JfrMsg_lock                  , PaddedMonitor, nosafepoint-3,     true);
  def(JfrStacktrace_lock           , PaddedMutex  , stackwatermark-1,  true);
  def(JfrThreadSampler_lock        , PaddedMonitor, nosafepoint,       true);
#endif

#ifndef SUPPORTS_NATIVE_CX8
  def(UnsafeJlong_lock             , PaddedMutex  , nosafepoint,    true);
#endif

  def(CodeHeapStateAnalytics_lock  , PaddedMutex  , safepoint,      false);
  def(NMethodSweeperStats_lock     , PaddedMutex  , nosafepoint,    true);
  def(ThreadsSMRDelete_lock        , PaddedMonitor, nosafepoint-3,  true); // Holds ConcurrentHashTableResize_lock
  def(ThreadIdTableCreate_lock     , PaddedMutex  , safepoint,      false);
  def(SharedDecoder_lock           , PaddedMutex  , tty-1,          true);
  def(DCmdFactory_lock             , PaddedMutex  , nosafepoint,    true);
#if INCLUDE_NMT
  def(NMTQuery_lock                , PaddedMutex  , safepoint,      false);
#endif
#if INCLUDE_CDS
#if INCLUDE_JVMTI
  def(CDSClassFileStream_lock      , PaddedMutex  , safepoint,      false);
#endif
  def(DumpTimeTable_lock           , PaddedMutex  , nosafepoint,    true);
  def(CDSLambda_lock               , PaddedMutex  , nosafepoint,    true);
  def(DumpRegion_lock              , PaddedMutex  , nosafepoint,    true);
  def(ClassListFile_lock           , PaddedMutex  , nosafepoint,    true);
  def(LambdaFormInvokers_lock      , PaddedMutex  , safepoint,      false);
#endif // INCLUDE_CDS
  def(Bootclasspath_lock           , PaddedMutex  , nosafepoint,    true);
  def(Zip_lock                     , PaddedMonitor, nosafepoint-1,  true); // Holds DumpTimeTable_lock

#if INCLUDE_JVMCI
  def(JVMCI_lock                   , PaddedMonitor, safepoint,      true);
#endif

  // These locks have relative rankings, and inherit safepoint checking attributes from that rank.
  defl(InlineCacheBuffer_lock      , PaddedMutex  , CompiledIC_lock,  true);
  defl(VtableStubs_lock            , PaddedMutex  , CompiledIC_lock,  true);  // Also holds DumpTimeTable_lock
  defl(CodeCache_lock              , PaddedMonitor, VtableStubs_lock, true);
  defl(CompiledMethod_lock         , PaddedMutex  , CodeCache_lock,   true);
  defl(CodeSweeper_lock            , PaddedMonitor, CompiledMethod_lock, true);

  defl(Threads_lock                , PaddedMonitor, CompileThread_lock, true);
  defl(Heap_lock                   , PaddedMonitor, MultiArray_lock,    false);
  defl(Compile_lock                , PaddedMutex ,  MethodCompileQueue_lock, false);

  defl(PerfDataMemAlloc_lock       , PaddedMutex  , Heap_lock,         true);
  defl(PerfDataManager_lock        , PaddedMutex  , Heap_lock,         true);
  defl(ClassLoaderDataGraph_lock   , PaddedMutex  , MultiArray_lock,   false);
  defl(VMOperation_lock            , PaddedMonitor, Compile_lock,      true);
  defl(ClassInitError_lock         , PaddedMonitor, Threads_lock,      true);

  if (UseG1GC) {
    defl(G1OldGCCount_lock         , PaddedMonitor, Threads_lock,      true);
  }
  defl(CompileTaskAlloc_lock       , PaddedMutex ,  MethodCompileQueue_lock,   true);
  defl(ExpandHeap_lock             , PaddedMutex ,  Heap_lock,                 true);
  defl(OopMapCacheAlloc_lock       , PaddedMutex ,  Threads_lock,              true);
  defl(Module_lock                 , PaddedMutex ,  ClassLoaderDataGraph_lock, false);
  defl(SystemDictionary_lock       , PaddedMonitor, Module_lock,               true);
  defl(JNICritical_lock            , PaddedMonitor, MultiArray_lock,           true); // used for JNI critical regions
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

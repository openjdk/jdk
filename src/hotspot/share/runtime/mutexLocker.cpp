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

// Using Padded subclasses to prevent false sharing of these global monitors and mutexes.
void mutex_init() {
  tty_lock                     = new PaddedMutex(Mutex::tty, "tty_lock");      // allow to lock in VM

  STS_lock                     = new PaddedMonitor(Mutex::nosafepoint, "STS_lock");

  if (UseG1GC) {
    CGC_lock                   = new PaddedMonitor(Mutex::nosafepoint, "CGC_lock");

    G1DetachedRefinementStats_lock = new PaddedMutex(Mutex::nosafepoint-2, "G1DetachedRefinementStats_lock");

    FreeList_lock              = new PaddedMutex(Mutex::service-1, "FreeList_lock");
    OldSets_lock               = new PaddedMutex(Mutex::nosafepoint, "OldSets_lock");
    Uncommit_lock              = new PaddedMutex(Mutex::service-2, "Uncommit_lock");
    RootRegionScan_lock        = new PaddedMonitor(Mutex::nosafepoint-1, "RootRegionScan_lock");

    MarkStackFreeList_lock     = new PaddedMutex(Mutex::nosafepoint, "MarkStackFreeList_lock");
    MarkStackChunkList_lock    = new PaddedMutex(Mutex::nosafepoint, "MarkStackChunkList_lock");

    MonitoringSupport_lock     = new PaddedMutex(Mutex::service-1, "MonitoringSupport_lock"); // used for serviceability monitoring support
  }
  StringDedup_lock             = new PaddedMonitor(Mutex::nosafepoint, "StringDedup_lock");
  StringDedupIntern_lock       = new PaddedMutex(Mutex::nosafepoint, "StringDedupIntern_lock");
  ParGCRareEvent_lock          = new PaddedMutex(Mutex::safepoint, "ParGCRareEvent_lock", true);
  RawMonitor_lock              = new PaddedMutex(Mutex::nosafepoint-1, "RawMonitor_lock");

  Metaspace_lock               = new PaddedMutex(Mutex::nosafepoint-3, "Metaspace_lock");

  Patching_lock                = new PaddedMutex(Mutex::nosafepoint, "Patching_lock"); // used for safepointing and code patching.
  MonitorDeflation_lock        = new PaddedMonitor(Mutex::nosafepoint, "MonitorDeflation_lock"); // used for monitor deflation thread operations
  Service_lock                 = new PaddedMonitor(Mutex::service, "Service_lock"); // used for service thread operations

  if (UseNotificationThread) {
    Notification_lock          = new PaddedMonitor(Mutex::service, "Notification_lock");  // used for notification thread operations
  } else {
    Notification_lock = Service_lock;
  }

  JmethodIdCreation_lock       = new PaddedMutex(Mutex::nosafepoint-2, "JmethodIdCreation_lock"); // used for creating jmethodIDs.

  SharedDictionary_lock        = new PaddedMutex(Mutex::safepoint, "SharedDictionary_lock", true);
  VMStatistic_lock             = new PaddedMutex(Mutex::safepoint, "VMStatistic_lock");
  JNIHandleBlockFreeList_lock  = new PaddedMutex(Mutex::nosafepoint-1, "JNIHandleBlockFreeList_lock ");      // handles are used by VM thread
  SignatureHandlerLibrary_lock = new PaddedMutex(Mutex::safepoint, "SignatureHandlerLibrary_lock");
  SymbolArena_lock             = new PaddedMutex(Mutex::nosafepoint, "SymbolArena_lock");
  ExceptionCache_lock          = new PaddedMutex(Mutex::safepoint, "ExceptionCache_lock");
#ifndef PRODUCT
  FullGCALot_lock              = new PaddedMutex(Mutex::safepoint, "FullGCALot_lock"); // a lock to make FullGCALot MT safe
#endif
  BeforeExit_lock              = new PaddedMonitor(Mutex::safepoint, "BeforeExit_lock", true);

  NonJavaThreadsList_lock      = new PaddedMutex(Mutex::nosafepoint-1, "NonJavaThreadsList_lock");
  NonJavaThreadsListSync_lock  = new PaddedMutex(Mutex::nosafepoint, "NonJavaThreadsListSync_lock");

  RetData_lock                 = new PaddedMutex(Mutex::safepoint, "RetData_lock");
  Terminator_lock              = new PaddedMonitor(Mutex::safepoint, "Terminator_lock", true);
  InitCompleted_lock           = new PaddedMonitor(Mutex::nosafepoint, "InitCompleted_lock");
  Notify_lock                  = new PaddedMonitor(Mutex::safepoint, "Notify_lock", true);
  AdapterHandlerLibrary_lock   = new PaddedMutex(Mutex::safepoint, "AdapterHandlerLibrary_lock", true);

  Heap_lock                    = new PaddedMonitor(Mutex::safepoint, "Heap_lock"); // Doesn't safepoint check during termination.
  JfieldIdCreation_lock        = new PaddedMutex(Mutex::safepoint, "JfieldIdCreation_lock", true);  // jfieldID, Used in VM_Operation

  CompiledIC_lock              = new PaddedMutex(Mutex::nosafepoint, "CompiledIC_lock");  // locks VtableStubs_lock, InlineCacheBuffer_lock
  MethodCompileQueue_lock      = new PaddedMonitor(Mutex::safepoint, "MethodCompileQueue_lock");
  CompileStatistics_lock       = new PaddedMutex(Mutex::safepoint, "CompileStatistics_lock");
  DirectivesStack_lock         = new PaddedMutex(Mutex::nosafepoint, "DirectivesStack_lock");
  MultiArray_lock              = new PaddedMutex(Mutex::safepoint, "MultiArray_lock");

  JvmtiThreadState_lock        = new PaddedMutex(Mutex::safepoint, "JvmtiThreadState_lock"); // Used by JvmtiThreadState/JvmtiEventController
  EscapeBarrier_lock           = new PaddedMonitor(Mutex::nosafepoint, "EscapeBarrier_lock");  // Used to synchronize object reallocation/relocking triggered by JVMTI
  Management_lock              = new PaddedMutex(Mutex::safepoint, "Management_lock"); // used for JVM management

  ConcurrentGCBreakpoints_lock = new PaddedMonitor(Mutex::safepoint, "ConcurrentGCBreakpoints_lock", true);
  MethodData_lock              = new PaddedMutex(Mutex::safepoint, "MethodData_lock");
  TouchedMethodLog_lock        = new PaddedMutex(Mutex::safepoint, "TouchedMethodLog_lock");

  CompileThread_lock           = new PaddedMonitor(Mutex::safepoint, "CompileThread_lock");
  PeriodicTask_lock            = new PaddedMonitor(Mutex::safepoint, "PeriodicTask_lock", true);
  RedefineClasses_lock         = new PaddedMonitor(Mutex::safepoint, "RedefineClasses_lock", true);
  Verify_lock                  = new PaddedMutex(Mutex::safepoint, "Verify_lock", true);

  if (WhiteBoxAPI) {
    Compilation_lock           = new PaddedMonitor(Mutex::nosafepoint, "Compilation_lock");
  }

#if INCLUDE_JFR
  JfrBuffer_lock               = new PaddedMutex(Mutex::nosafepoint, "JfrBuffer_lock");
  JfrMsg_lock                  = new PaddedMonitor(Mutex::nosafepoint-3, "JfrMsg_lock");
  JfrStacktrace_lock           = new PaddedMutex(Mutex::stackwatermark-1, "JfrStacktrace_lock");
  JfrThreadSampler_lock        = new PaddedMonitor(Mutex::nosafepoint, "JfrThreadSampler_lock");
#endif

#ifndef SUPPORTS_NATIVE_CX8
  UnsafeJlong_lock             = new PaddedMutex(Mutex::nosafepoint, "UnsafeJlong_lock");
#endif

  CodeHeapStateAnalytics_lock  = new PaddedMutex(Mutex::safepoint, "CodeHeapStateAnalytics_lock");
  NMethodSweeperStats_lock     = new PaddedMutex(Mutex::nosafepoint, "NMethodSweeperStats_lock");
  ThreadsSMRDelete_lock        = new PaddedMonitor(Mutex::nosafepoint-3, "ThreadsSMRDelete_lock"); // Holds ConcurrentHashTableResize_lock
  ThreadIdTableCreate_lock     = new PaddedMutex(Mutex::safepoint, "ThreadIdTableCreate_lock");
  SharedDecoder_lock           = new PaddedMutex(Mutex::tty-1, "SharedDecoder_lock");
  DCmdFactory_lock             = new PaddedMutex(Mutex::nosafepoint, "DCmdFactory_lock");
#if INCLUDE_NMT
  NMTQuery_lock                = new PaddedMutex(Mutex::safepoint, "NMTQuery_lock");
#endif
#if INCLUDE_CDS
#if INCLUDE_JVMTI
  CDSClassFileStream_lock      = new PaddedMutex(Mutex::safepoint, "CDSClassFileStream_lock");
#endif
  DumpTimeTable_lock           = new PaddedMutex(Mutex::nosafepoint, "DumpTimeTable_lock");
  CDSLambda_lock               = new PaddedMutex(Mutex::nosafepoint, "CDSLambda_lock");
  DumpRegion_lock              = new PaddedMutex(Mutex::nosafepoint, "DumpRegion_lock");
  ClassListFile_lock           = new PaddedMutex(Mutex::nosafepoint, "ClassListFile_lock");
  LambdaFormInvokers_lock      = new PaddedMutex(Mutex::safepoint, "LambdaFormInvokers_lock");
#endif // INCLUDE_CDS
  Bootclasspath_lock           = new PaddedMutex(Mutex::nosafepoint, "Bootclasspath_lock");
  Zip_lock                     = new PaddedMonitor(Mutex::nosafepoint-1, "Zip_lock"); // Holds DumpTimeTable_lock

#if INCLUDE_JVMCI
  JVMCI_lock                   = new PaddedMonitor(Mutex::safepoint, "JVMCI_lock", true);
#endif

  // These locks have relative rankings, and inherit safepoint checking attributes from that rank.
  // Pass allow_vm_block explicitly here.
  InlineCacheBuffer_lock      = new PaddedMutex(CompiledIC_lock->rank()-1, "InlineCacheBuffer_lock");
  VtableStubs_lock            = new PaddedMutex(CompiledIC_lock->rank()-1, "VtableStubs_lock");  // Also holds DumpTimeTable_lock
  CodeCache_lock              = new PaddedMonitor(VtableStubs_lock->rank()-1, "CodeCache_lock");
  CompiledMethod_lock         = new PaddedMutex(CodeCache_lock->rank()-1, "CompiledMethod_lock");
  CodeSweeper_lock            = new PaddedMonitor(CompiledMethod_lock->rank()-1, "CodeSweeper_lock");

  Threads_lock                = new PaddedMonitor(CompileThread_lock->rank()-1, "Threads_lock", true);
  Heap_lock                   = new PaddedMonitor(MultiArray_lock->rank()-1, "Heap_lock");
  Compile_lock                = new PaddedMutex( MethodCompileQueue_lock->rank()-1, "Compile_lock");

  PerfDataMemAlloc_lock       = new PaddedMutex(Heap_lock->rank()-1, "PerfDataMemAlloc_lock", true);
  PerfDataManager_lock        = new PaddedMutex(Heap_lock->rank()-1, "PerfDataManager_lock", true);
  ClassLoaderDataGraph_lock   = new PaddedMutex(MultiArray_lock->rank()-1, "ClassLoaderDataGraph_lock");
  VMOperation_lock            = new PaddedMonitor(Compile_lock->rank()-1, "VMOperation_lock", true);
  ClassInitError_lock         = new PaddedMonitor(Threads_lock->rank()-1, "ClassInitError_lock", true);

  if (UseG1GC) {
    G1OldGCCount_lock         = new PaddedMonitor(Threads_lock->rank()-1, "G1OldGCCount_lock", true);
  }
  CompileTaskAlloc_lock       = new PaddedMutex(MethodCompileQueue_lock->rank()-1, "CompileTaskAlloc_lock", true);
  ExpandHeap_lock             = new PaddedMutex(Heap_lock->rank()-1, "ExpandHeap_lock", true);
  OopMapCacheAlloc_lock       = new PaddedMutex(Threads_lock->rank()-1, "OopMapCacheAlloc_lock", true);
  Module_lock                 = new PaddedMutex(ClassLoaderDataGraph_lock->rank()-1, "Module_lock");
  SystemDictionary_lock       = new PaddedMonitor(Module_lock->rank()-1, "SystemDictionary_lock", true);
  JNICritical_lock            = new PaddedMonitor(MultiArray_lock->rank()-1, "JNICritical_lock", true); // used for JNI critical regions
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

/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/mutexLocker.hpp"
#include "runtime/os.inline.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/threadLocalStorage.hpp"
#include "runtime/vmThread.hpp"

// Mutexes used in the VM (see comment in mutexLocker.hpp):
//
// Note that the following pointers are effectively final -- after having been
// set at JVM startup-time, they should never be subsequently mutated.
// Instead of using pointers to malloc()ed monitors and mutexes we should consider
// eliminating the indirection and using instances instead.
// Consider using GCC's __read_mostly.

Mutex*   Patching_lock                = NULL;
Monitor* SystemDictionary_lock        = NULL;
Mutex*   PackageTable_lock            = NULL;
Mutex*   CompiledIC_lock              = NULL;
Mutex*   InlineCacheBuffer_lock       = NULL;
Mutex*   VMStatistic_lock             = NULL;
Mutex*   JNIGlobalHandle_lock         = NULL;
Mutex*   JNIHandleBlockFreeList_lock  = NULL;
Mutex*   MemberNameTable_lock         = NULL;
Mutex*   JmethodIdCreation_lock       = NULL;
Mutex*   JfieldIdCreation_lock        = NULL;
Monitor* JNICritical_lock             = NULL;
Mutex*   JvmtiThreadState_lock        = NULL;
Monitor* JvmtiPendingEvent_lock       = NULL;
Monitor* Heap_lock                    = NULL;
Mutex*   ExpandHeap_lock              = NULL;
Mutex*   AdapterHandlerLibrary_lock   = NULL;
Mutex*   SignatureHandlerLibrary_lock = NULL;
Mutex*   VtableStubs_lock             = NULL;
Mutex*   SymbolTable_lock             = NULL;
Mutex*   StringTable_lock             = NULL;
Monitor* StringDedupQueue_lock        = NULL;
Mutex*   StringDedupTable_lock        = NULL;
Monitor* CodeCache_lock               = NULL;
Mutex*   MethodData_lock              = NULL;
Mutex*   TouchedMethodLog_lock        = NULL;
Mutex*   RetData_lock                 = NULL;
Monitor* VMOperationQueue_lock        = NULL;
Monitor* VMOperationRequest_lock      = NULL;
Monitor* Safepoint_lock               = NULL;
Monitor* SerializePage_lock           = NULL;
Monitor* Threads_lock                 = NULL;
Monitor* CGC_lock                     = NULL;
Monitor* STS_lock                     = NULL;
Monitor* SLT_lock                     = NULL;
Monitor* FullGCCount_lock             = NULL;
Monitor* CMark_lock                   = NULL;
Mutex*   CMRegionStack_lock           = NULL;
Mutex*   SATB_Q_FL_lock               = NULL;
Monitor* SATB_Q_CBL_mon               = NULL;
Mutex*   Shared_SATB_Q_lock           = NULL;
Mutex*   DirtyCardQ_FL_lock           = NULL;
Monitor* DirtyCardQ_CBL_mon           = NULL;
Mutex*   Shared_DirtyCardQ_lock       = NULL;
Mutex*   ParGCRareEvent_lock          = NULL;
Mutex*   DerivedPointerTableGC_lock   = NULL;
Mutex*   Compile_lock                 = NULL;
Monitor* MethodCompileQueue_lock      = NULL;
Monitor* CompileThread_lock           = NULL;
Monitor* Compilation_lock             = NULL;
Mutex*   CompileTaskAlloc_lock        = NULL;
Mutex*   CompileStatistics_lock       = NULL;
Mutex*   MultiArray_lock              = NULL;
Monitor* Terminator_lock              = NULL;
Monitor* BeforeExit_lock              = NULL;
Monitor* Notify_lock                  = NULL;
Monitor* Interrupt_lock               = NULL;
Monitor* ProfileVM_lock               = NULL;
Mutex*   ProfilePrint_lock            = NULL;
Mutex*   ExceptionCache_lock          = NULL;
Monitor* ObjAllocPost_lock            = NULL;
Mutex*   OsrList_lock                 = NULL;

#ifndef PRODUCT
Mutex*   FullGCALot_lock              = NULL;
#endif

Mutex*   Debug1_lock                  = NULL;
Mutex*   Debug2_lock                  = NULL;
Mutex*   Debug3_lock                  = NULL;

Mutex*   tty_lock                     = NULL;

Mutex*   RawMonitor_lock              = NULL;
Mutex*   PerfDataMemAlloc_lock        = NULL;
Mutex*   PerfDataManager_lock         = NULL;
Mutex*   OopMapCacheAlloc_lock        = NULL;

Mutex*   FreeList_lock                = NULL;
Monitor* SecondaryFreeList_lock       = NULL;
Mutex*   OldSets_lock                 = NULL;
Monitor* RootRegionScan_lock          = NULL;
Mutex*   MMUTracker_lock              = NULL;

Monitor* GCTaskManager_lock           = NULL;

Mutex*   Management_lock              = NULL;
Monitor* Service_lock                 = NULL;
Monitor* PeriodicTask_lock            = NULL;
Mutex*   LogConfiguration_lock        = NULL;

#ifdef INCLUDE_TRACE
Mutex*   JfrStacktrace_lock           = NULL;
Monitor* JfrMsg_lock                  = NULL;
Mutex*   JfrBuffer_lock               = NULL;
Mutex*   JfrStream_lock               = NULL;
Mutex*   JfrThreadGroups_lock         = NULL;
#endif

#ifndef SUPPORTS_NATIVE_CX8
Mutex*   UnsafeJlong_lock             = NULL;
#endif

#define MAX_NUM_MUTEX 128
static Monitor * _mutex_array[MAX_NUM_MUTEX];
static int _num_mutex;

#ifdef ASSERT
void assert_locked_or_safepoint(const Monitor * lock) {
  // check if this thread owns the lock (common case)
  if (IgnoreLockingAssertions) return;
  assert(lock != NULL, "Need non-NULL lock");
  if (lock->owned_by_self()) return;
  if (SafepointSynchronize::is_at_safepoint()) return;
  if (!Universe::is_fully_initialized()) return;
  // see if invoker of VM operation owns it
  VM_Operation* op = VMThread::vm_operation();
  if (op != NULL && op->calling_thread() == lock->owner()) return;
  fatal(err_msg("must own lock %s", lock->name()));
}

// a stronger assertion than the above
void assert_lock_strong(const Monitor * lock) {
  if (IgnoreLockingAssertions) return;
  assert(lock != NULL, "Need non-NULL lock");
  if (lock->owned_by_self()) return;
  fatal(err_msg("must own lock %s", lock->name()));
}
#endif

#define def(var, type, pri, vm_block, safepoint_check_allowed ) {      \
  var = new type(Mutex::pri, #var, vm_block, safepoint_check_allowed); \
  assert(_num_mutex < MAX_NUM_MUTEX, "increase MAX_NUM_MUTEX");        \
  _mutex_array[_num_mutex++] = var;                                      \
}

void mutex_init() {
  def(tty_lock                     , Mutex  , event,       true,  Monitor::_safepoint_check_never);      // allow to lock in VM

  def(CGC_lock                     , Monitor, special,     true,  Monitor::_safepoint_check_never);      // coordinate between fore- and background GC
  def(STS_lock                     , Monitor, leaf,        true,  Monitor::_safepoint_check_never);

  if (UseConcMarkSweepGC || UseG1GC) {
    def(FullGCCount_lock           , Monitor, leaf,        true,  Monitor::_safepoint_check_never);      // in support of ExplicitGCInvokesConcurrent
  }
  if (UseG1GC) {

    def(CMark_lock                 , Monitor, nonleaf,     true,  Monitor::_safepoint_check_never);      // coordinate concurrent mark thread
    def(CMRegionStack_lock         , Mutex,   leaf,        true,  Monitor::_safepoint_check_never);
    def(SATB_Q_FL_lock             , Mutex  , special,     true,  Monitor::_safepoint_check_never);
    def(SATB_Q_CBL_mon             , Monitor, nonleaf,     true,  Monitor::_safepoint_check_never);
    def(Shared_SATB_Q_lock         , Mutex,   nonleaf,     true,  Monitor::_safepoint_check_never);

    def(DirtyCardQ_FL_lock         , Mutex  , special,     true,  Monitor::_safepoint_check_never);
    def(DirtyCardQ_CBL_mon         , Monitor, nonleaf,     true,  Monitor::_safepoint_check_never);
    def(Shared_DirtyCardQ_lock     , Mutex,   nonleaf,     true,  Monitor::_safepoint_check_never);

    def(FreeList_lock              , Mutex,   leaf     ,   true,  Monitor::_safepoint_check_never);
    def(SecondaryFreeList_lock     , Monitor, leaf     ,   true,  Monitor::_safepoint_check_never);
    def(OldSets_lock               , Mutex  , leaf     ,   true,  Monitor::_safepoint_check_never);
    def(RootRegionScan_lock        , Monitor, leaf     ,   true,  Monitor::_safepoint_check_never);
    def(MMUTracker_lock            , Mutex  , leaf     ,   true,  Monitor::_safepoint_check_never);

    def(StringDedupQueue_lock      , Monitor, leaf,        true,  Monitor::_safepoint_check_never);
    def(StringDedupTable_lock      , Mutex  , leaf,        true,  Monitor::_safepoint_check_never);
  }
  def(ParGCRareEvent_lock          , Mutex  , leaf     ,   true,  Monitor::_safepoint_check_sometimes);
  def(DerivedPointerTableGC_lock   , Mutex,   leaf,        true,  Monitor::_safepoint_check_never);
  def(CodeCache_lock               , Mutex  , special,     true,  Monitor::_safepoint_check_never);
  def(Interrupt_lock               , Monitor, special,     true,  Monitor::_safepoint_check_never);      // used for interrupt processing
  def(RawMonitor_lock              , Mutex,   special,     true,  Monitor::_safepoint_check_never);
  def(OopMapCacheAlloc_lock        , Mutex,   leaf,        true,  Monitor::_safepoint_check_always);     // used for oop_map_cache allocation.

  def(Patching_lock                , Mutex  , special,     true,  Monitor::_safepoint_check_never);      // used for safepointing and code patching.
  def(ObjAllocPost_lock            , Monitor, special,     false, Monitor::_safepoint_check_never);
  def(Service_lock                 , Monitor, special,     true,  Monitor::_safepoint_check_never);      // used for service thread operations
  def(JmethodIdCreation_lock       , Mutex  , leaf,        true,  Monitor::_safepoint_check_always);     // used for creating jmethodIDs.

  def(SystemDictionary_lock        , Monitor, leaf,        true,  Monitor::_safepoint_check_always);     // lookups done by VM thread
  def(PackageTable_lock            , Mutex  , leaf,        false, Monitor::_safepoint_check_always);
  def(InlineCacheBuffer_lock       , Mutex  , leaf,        true,  Monitor::_safepoint_check_always);
  def(VMStatistic_lock             , Mutex  , leaf,        false, Monitor::_safepoint_check_always);
  def(ExpandHeap_lock              , Mutex  , leaf,        true,  Monitor::_safepoint_check_always);     // Used during compilation by VM thread
  def(JNIHandleBlockFreeList_lock  , Mutex  , leaf,        true,  Monitor::_safepoint_check_never);      // handles are used by VM thread
  def(SignatureHandlerLibrary_lock , Mutex  , leaf,        false, Monitor::_safepoint_check_always);
  def(SymbolTable_lock             , Mutex  , leaf+2,      true,  Monitor::_safepoint_check_always);
  def(StringTable_lock             , Mutex  , leaf,        true,  Monitor::_safepoint_check_always);
  def(ProfilePrint_lock            , Mutex  , leaf,        false, Monitor::_safepoint_check_always);     // serial profile printing
  def(ExceptionCache_lock          , Mutex  , leaf,        false, Monitor::_safepoint_check_always);     // serial profile printing
  def(OsrList_lock                 , Mutex  , leaf,        true,  Monitor::_safepoint_check_never);
  def(Debug1_lock                  , Mutex  , leaf,        true,  Monitor::_safepoint_check_never);
#ifndef PRODUCT
  def(FullGCALot_lock              , Mutex  , leaf,        false, Monitor::_safepoint_check_always);     // a lock to make FullGCALot MT safe
#endif
  def(BeforeExit_lock              , Monitor, leaf,        true,  Monitor::_safepoint_check_always);
  def(PerfDataMemAlloc_lock        , Mutex  , leaf,        true,  Monitor::_safepoint_check_always);     // used for allocating PerfData memory for performance data
  def(PerfDataManager_lock         , Mutex  , leaf,        true,  Monitor::_safepoint_check_always);     // used for synchronized access to PerfDataManager resources

  // CMS_modUnionTable_lock                   leaf
  // CMS_bitMap_lock                          leaf 1
  // CMS_freeList_lock                        leaf 2

  def(Safepoint_lock               , Monitor, safepoint,   true,  Monitor::_safepoint_check_sometimes);  // locks SnippetCache_lock/Threads_lock

  def(Threads_lock                 , Monitor, barrier,     true,  Monitor::_safepoint_check_sometimes);

  def(VMOperationQueue_lock        , Monitor, nonleaf,     true,  Monitor::_safepoint_check_sometimes);  // VM_thread allowed to block on these
  def(VMOperationRequest_lock      , Monitor, nonleaf,     true,  Monitor::_safepoint_check_sometimes);
  def(RetData_lock                 , Mutex  , nonleaf,     false, Monitor::_safepoint_check_always);
  def(Terminator_lock              , Monitor, nonleaf,     true,  Monitor::_safepoint_check_sometimes);
  def(VtableStubs_lock             , Mutex  , nonleaf,     true,  Monitor::_safepoint_check_always);
  def(Notify_lock                  , Monitor, nonleaf,     true,  Monitor::_safepoint_check_always);
  def(JNIGlobalHandle_lock         , Mutex  , nonleaf,     true,  Monitor::_safepoint_check_always);     // locks JNIHandleBlockFreeList_lock
  def(JNICritical_lock             , Monitor, nonleaf,     true,  Monitor::_safepoint_check_always);     // used for JNI critical regions
  def(AdapterHandlerLibrary_lock   , Mutex  , nonleaf,     true,  Monitor::_safepoint_check_always);
  if (UseConcMarkSweepGC) {
    def(SLT_lock                   , Monitor, nonleaf,     false, Monitor::_safepoint_check_never);      // used in CMS GC for locking PLL lock
  }

  def(Heap_lock                    , Monitor, nonleaf+1,   false, Monitor::_safepoint_check_sometimes);
  def(JfieldIdCreation_lock        , Mutex  , nonleaf+1,   true,  Monitor::_safepoint_check_always);     // jfieldID, Used in VM_Operation
  def(MemberNameTable_lock         , Mutex  , nonleaf+1,   false, Monitor::_safepoint_check_always);     // Used to protect MemberNameTable

  def(CompiledIC_lock              , Mutex  , nonleaf+2,   false, Monitor::_safepoint_check_always);     // locks VtableStubs_lock, InlineCacheBuffer_lock
  def(CompileTaskAlloc_lock        , Mutex  , nonleaf+2,   true,  Monitor::_safepoint_check_always);
  def(CompileStatistics_lock       , Mutex  , nonleaf+2,   false, Monitor::_safepoint_check_always);
  def(MultiArray_lock              , Mutex  , nonleaf+2,   false, Monitor::_safepoint_check_always);     // locks SymbolTable_lock

  def(JvmtiThreadState_lock        , Mutex  , nonleaf+2,   false, Monitor::_safepoint_check_always);     // Used by JvmtiThreadState/JvmtiEventController
  def(JvmtiPendingEvent_lock       , Monitor, nonleaf,     false, Monitor::_safepoint_check_never);      // Used by JvmtiCodeBlobEvents
  def(Management_lock              , Mutex  , nonleaf+2,   false, Monitor::_safepoint_check_always);     // used for JVM management

  def(Compile_lock                 , Mutex  , nonleaf+3,   true,  Monitor::_safepoint_check_sometimes);
  def(MethodData_lock              , Mutex  , nonleaf+3,   false, Monitor::_safepoint_check_always);
  def(TouchedMethodLog_lock        , Mutex  , nonleaf+3,   false, Monitor::_safepoint_check_always);

  def(MethodCompileQueue_lock      , Monitor, nonleaf+4,   true,  Monitor::_safepoint_check_always);
  def(Debug2_lock                  , Mutex  , nonleaf+4,   true,  Monitor::_safepoint_check_never);
  def(Debug3_lock                  , Mutex  , nonleaf+4,   true,  Monitor::_safepoint_check_never);
  def(ProfileVM_lock               , Monitor, special,     false, Monitor::_safepoint_check_never);      // used for profiling of the VMThread
  def(CompileThread_lock           , Monitor, nonleaf+5,   false, Monitor::_safepoint_check_always);
  def(PeriodicTask_lock            , Monitor, nonleaf+5,   true,  Monitor::_safepoint_check_sometimes);
  if (WhiteBoxAPI) {
    def(Compilation_lock           , Monitor, leaf,        false, Monitor::_safepoint_check_never);
  }
  def(LogConfiguration_lock        , Mutex,   nonleaf,     false, Monitor::_safepoint_check_always);

#ifdef INCLUDE_TRACE
  def(JfrMsg_lock                  , Monitor, leaf,        true,  Monitor::_safepoint_check_always);
  def(JfrBuffer_lock               , Mutex,   leaf,        true,  Monitor::_safepoint_check_never);
  def(JfrThreadGroups_lock         , Mutex,   leaf,        true,  Monitor::_safepoint_check_always);
  def(JfrStream_lock               , Mutex,   nonleaf,     true,  Monitor::_safepoint_check_never);
  def(JfrStacktrace_lock           , Mutex,   special,     true,  Monitor::_safepoint_check_sometimes);
#endif

#ifndef SUPPORTS_NATIVE_CX8
  def(UnsafeJlong_lock             , Mutex,   special,     false, Monitor::_safepoint_check_never);
#endif
}

GCMutexLocker::GCMutexLocker(Monitor * mutex) {
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
     if (_mutex_array[i]->owner() != NULL) {
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

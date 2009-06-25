/*
 * Copyright 1997-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

#include "incls/_precompiled.incl"
#include "incls/_mutexLocker.cpp.incl"

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
Mutex*   JNICachedItableIndex_lock    = NULL;
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
Mutex*   CodeCache_lock               = NULL;
Mutex*   MethodData_lock              = NULL;
Mutex*   RetData_lock                 = NULL;
Monitor* VMOperationQueue_lock        = NULL;
Monitor* VMOperationRequest_lock      = NULL;
Monitor* Safepoint_lock               = NULL;
Monitor* SerializePage_lock           = NULL;
Monitor* Threads_lock                 = NULL;
Monitor* CGC_lock                     = NULL;
Mutex*   STS_init_lock                = NULL;
Monitor* SLT_lock                     = NULL;
Monitor* iCMS_lock                    = NULL;
Monitor* FullGCCount_lock             = NULL;
Monitor* CMark_lock                   = NULL;
Monitor* ZF_mon                       = NULL;
Monitor* Cleanup_mon                  = NULL;
Mutex*   SATB_Q_FL_lock               = NULL;
Monitor* SATB_Q_CBL_mon               = NULL;
Mutex*   Shared_SATB_Q_lock           = NULL;
Mutex*   DirtyCardQ_FL_lock           = NULL;
Monitor* DirtyCardQ_CBL_mon           = NULL;
Mutex*   Shared_DirtyCardQ_lock       = NULL;
Mutex*   ParGCRareEvent_lock          = NULL;
Mutex*   EvacFailureStack_lock        = NULL;
Mutex*   DerivedPointerTableGC_lock   = NULL;
Mutex*   Compile_lock                 = NULL;
Monitor* MethodCompileQueue_lock      = NULL;
#ifdef TIERED
Monitor* C1_lock                      = NULL;
#endif // TIERED
Monitor* CompileThread_lock           = NULL;
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

Mutex*   MMUTracker_lock              = NULL;
Mutex*   HotCardCache_lock            = NULL;

Monitor* GCTaskManager_lock           = NULL;

Mutex*   Management_lock              = NULL;
Monitor* LowMemory_lock               = NULL;

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
  fatal1("must own lock %s", lock->name());
}

// a stronger assertion than the above
void assert_lock_strong(const Monitor * lock) {
  if (IgnoreLockingAssertions) return;
  assert(lock != NULL, "Need non-NULL lock");
  if (lock->owned_by_self()) return;
  fatal1("must own lock %s", lock->name());
}
#endif

#define def(var, type, pri, vm_block) {                           \
  var = new type(Mutex::pri, #var, vm_block);                     \
  assert(_num_mutex < MAX_NUM_MUTEX,                              \
                    "increase MAX_NUM_MUTEX");                    \
  _mutex_array[_num_mutex++] = var;                               \
}

void mutex_init() {
  def(tty_lock                     , Mutex  , event,       true ); // allow to lock in VM

  def(CGC_lock                   , Monitor, special,     true ); // coordinate between fore- and background GC
  def(STS_init_lock              , Mutex,   leaf,        true );
  if (UseConcMarkSweepGC) {
    def(iCMS_lock                  , Monitor, special,     true ); // CMS incremental mode start/stop notification
    def(FullGCCount_lock           , Monitor, leaf,        true ); // in support of ExplicitGCInvokesConcurrent
  }
  if (UseG1GC) {
    def(CMark_lock                 , Monitor, nonleaf,     true ); // coordinate concurrent mark thread
    def(ZF_mon                     , Monitor, leaf,        true );
    def(Cleanup_mon                , Monitor, nonleaf,     true );
    def(SATB_Q_FL_lock             , Mutex  , special,     true );
    def(SATB_Q_CBL_mon             , Monitor, nonleaf,     true );
    def(Shared_SATB_Q_lock         , Mutex,   nonleaf,     true );

    def(DirtyCardQ_FL_lock         , Mutex  , special,     true );
    def(DirtyCardQ_CBL_mon         , Monitor, nonleaf,     true );
    def(Shared_DirtyCardQ_lock     , Mutex,   nonleaf,     true );

    def(MMUTracker_lock            , Mutex  , leaf     ,   true );
    def(HotCardCache_lock          , Mutex  , special  ,   true );
    def(EvacFailureStack_lock      , Mutex  , nonleaf  ,   true );
  }
  def(ParGCRareEvent_lock          , Mutex  , leaf     ,   true );
  def(DerivedPointerTableGC_lock   , Mutex,   leaf,        true );
  def(CodeCache_lock               , Mutex  , special,     true );
  def(Interrupt_lock               , Monitor, special,     true ); // used for interrupt processing
  def(RawMonitor_lock              , Mutex,   special,     true );
  def(OopMapCacheAlloc_lock        , Mutex,   leaf,        true ); // used for oop_map_cache allocation.

  def(Patching_lock                , Mutex  , special,     true ); // used for safepointing and code patching.
  def(ObjAllocPost_lock            , Monitor, special,     false);
  def(LowMemory_lock               , Monitor, special,     true ); // used for low memory detection
  def(JmethodIdCreation_lock       , Mutex  , leaf,        true ); // used for creating jmethodIDs.

  def(SystemDictionary_lock        , Monitor, leaf,        true ); // lookups done by VM thread
  def(PackageTable_lock            , Mutex  , leaf,        false);
  def(InlineCacheBuffer_lock       , Mutex  , leaf,        true );
  def(VMStatistic_lock             , Mutex  , leaf,        false);
  def(ExpandHeap_lock              , Mutex  , leaf,        true ); // Used during compilation by VM thread
  def(JNIHandleBlockFreeList_lock  , Mutex  , leaf,        true ); // handles are used by VM thread
  def(SignatureHandlerLibrary_lock , Mutex  , leaf,        false);
  def(SymbolTable_lock             , Mutex  , leaf,        true );
  def(StringTable_lock             , Mutex  , leaf,        true );
  def(ProfilePrint_lock            , Mutex  , leaf,        false); // serial profile printing
  def(ExceptionCache_lock          , Mutex  , leaf,        false); // serial profile printing
  def(OsrList_lock                 , Mutex  , leaf,        true );
  def(Debug1_lock                  , Mutex  , leaf,        true );
#ifndef PRODUCT
  def(FullGCALot_lock              , Mutex  , leaf,        false); // a lock to make FullGCALot MT safe
#endif
  def(BeforeExit_lock              , Monitor, leaf,        true );
  def(PerfDataMemAlloc_lock        , Mutex  , leaf,        true ); // used for allocating PerfData memory for performance data
  def(PerfDataManager_lock         , Mutex  , leaf,        true ); // used for synchronized access to PerfDataManager resources

  // CMS_modUnionTable_lock                   leaf
  // CMS_bitMap_lock                          leaf + 1
  // CMS_freeList_lock                        leaf + 2

  def(Safepoint_lock               , Monitor, safepoint,   true ); // locks SnippetCache_lock/Threads_lock

  def(Threads_lock                 , Monitor, barrier,     true );

  def(VMOperationQueue_lock        , Monitor, nonleaf,     true ); // VM_thread allowed to block on these
  def(VMOperationRequest_lock      , Monitor, nonleaf,     true );
  def(RetData_lock                 , Mutex  , nonleaf,     false);
  def(Terminator_lock              , Monitor, nonleaf,     true );
  def(VtableStubs_lock             , Mutex  , nonleaf,     true );
  def(Notify_lock                  , Monitor, nonleaf,     true );
  def(JNIGlobalHandle_lock         , Mutex  , nonleaf,     true ); // locks JNIHandleBlockFreeList_lock
  def(JNICritical_lock             , Monitor, nonleaf,     true ); // used for JNI critical regions
  def(AdapterHandlerLibrary_lock   , Mutex  , nonleaf,     true);
  if (UseConcMarkSweepGC) {
    def(SLT_lock                   , Monitor, nonleaf,     false );
                    // used in CMS GC for locking PLL lock
  }
  def(Heap_lock                    , Monitor, nonleaf+1,   false);
  def(JfieldIdCreation_lock        , Mutex  , nonleaf+1,   true ); // jfieldID, Used in VM_Operation
  def(JNICachedItableIndex_lock    , Mutex  , nonleaf+1,   false); // Used to cache an itable index during JNI invoke

  def(CompiledIC_lock              , Mutex  , nonleaf+2,   false); // locks VtableStubs_lock, InlineCacheBuffer_lock
  def(CompileTaskAlloc_lock        , Mutex  , nonleaf+2,   true );
  def(CompileStatistics_lock       , Mutex  , nonleaf+2,   false);
  def(MultiArray_lock              , Mutex  , nonleaf+2,   false); // locks SymbolTable_lock

  def(JvmtiThreadState_lock        , Mutex  , nonleaf+2,   false); // Used by JvmtiThreadState/JvmtiEventController
  def(JvmtiPendingEvent_lock       , Monitor, nonleaf,     false); // Used by JvmtiCodeBlobEvents
  def(Management_lock              , Mutex  , nonleaf+2,   false); // used for JVM management

  def(Compile_lock                 , Mutex  , nonleaf+3,   true );
  def(MethodData_lock              , Mutex  , nonleaf+3,   false);

  def(MethodCompileQueue_lock      , Monitor, nonleaf+4,   true );
  def(Debug2_lock                  , Mutex  , nonleaf+4,   true );
  def(Debug3_lock                  , Mutex  , nonleaf+4,   true );
  def(ProfileVM_lock               , Monitor, nonleaf+4,   false); // used for profiling of the VMThread
  def(CompileThread_lock           , Monitor, nonleaf+5,   false );
#ifdef TIERED
  def(C1_lock                      , Monitor, nonleaf+5,   false );
#endif // TIERED


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

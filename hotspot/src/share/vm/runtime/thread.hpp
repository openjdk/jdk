/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_RUNTIME_THREAD_HPP
#define SHARE_VM_RUNTIME_THREAD_HPP

#include "memory/allocation.hpp"
#include "memory/threadLocalAllocBuffer.hpp"
#include "oops/oop.hpp"
#include "prims/jni.h"
#include "prims/jvmtiExport.hpp"
#include "runtime/frame.hpp"
#include "runtime/javaFrameAnchor.hpp"
#include "runtime/jniHandles.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.hpp"
#include "runtime/osThread.hpp"
#include "runtime/park.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/threadLocalStorage.hpp"
#include "runtime/unhandledOops.hpp"
#include "utilities/macros.hpp"

#if INCLUDE_NMT
#include "services/memRecorder.hpp"
#endif // INCLUDE_NMT

#include "trace/traceBackend.hpp"
#include "trace/traceMacros.hpp"
#include "utilities/exceptions.hpp"
#include "utilities/top.hpp"
#if INCLUDE_ALL_GCS
#include "gc_implementation/g1/dirtyCardQueue.hpp"
#include "gc_implementation/g1/satbQueue.hpp"
#endif // INCLUDE_ALL_GCS
#ifdef ZERO
#ifdef TARGET_ARCH_zero
# include "stack_zero.hpp"
#endif
#endif

class ThreadSafepointState;
class ThreadProfiler;

class JvmtiThreadState;
class JvmtiGetLoadedClassesClosure;
class ThreadStatistics;
class ConcurrentLocksDump;
class ParkEvent;
class Parker;

class ciEnv;
class CompileThread;
class CompileLog;
class CompileTask;
class CompileQueue;
class CompilerCounters;
class vframeArray;

class DeoptResourceMark;
class jvmtiDeferredLocalVariableSet;

class GCTaskQueue;
class ThreadClosure;
class IdealGraphPrinter;

DEBUG_ONLY(class ResourceMark;)

class WorkerThread;

// Class hierarchy
// - Thread
//   - NamedThread
//     - VMThread
//     - ConcurrentGCThread
//     - WorkerThread
//       - GangWorker
//       - GCTaskThread
//   - JavaThread
//   - WatcherThread

class Thread: public ThreadShadow {
  friend class VMStructs;
 private:
  // Exception handling
  // (Note: _pending_exception and friends are in ThreadShadow)
  //oop       _pending_exception;                // pending exception for current thread
  // const char* _exception_file;                   // file information for exception (debugging only)
  // int         _exception_line;                   // line information for exception (debugging only)
 protected:
  // Support for forcing alignment of thread objects for biased locking
  void*       _real_malloc_address;
 public:
  void* operator new(size_t size) throw() { return allocate(size, true); }
  void* operator new(size_t size, const std::nothrow_t& nothrow_constant) throw() {
    return allocate(size, false); }
  void  operator delete(void* p);

 protected:
   static void* allocate(size_t size, bool throw_excpt, MEMFLAGS flags = mtThread);
 private:

  // ***************************************************************
  // Suspend and resume support
  // ***************************************************************
  //
  // VM suspend/resume no longer exists - it was once used for various
  // things including safepoints but was deprecated and finally removed
  // in Java 7. Because VM suspension was considered "internal" Java-level
  // suspension was considered "external", and this legacy naming scheme
  // remains.
  //
  // External suspend/resume requests come from JVM_SuspendThread,
  // JVM_ResumeThread, JVMTI SuspendThread, and finally JVMTI
  // ResumeThread. External
  // suspend requests cause _external_suspend to be set and external
  // resume requests cause _external_suspend to be cleared.
  // External suspend requests do not nest on top of other external
  // suspend requests. The higher level APIs reject suspend requests
  // for already suspended threads.
  //
  // The external_suspend
  // flag is checked by has_special_runtime_exit_condition() and java thread
  // will self-suspend when handle_special_runtime_exit_condition() is
  // called. Most uses of the _thread_blocked state in JavaThreads are
  // considered the same as being externally suspended; if the blocking
  // condition lifts, the JavaThread will self-suspend. Other places
  // where VM checks for external_suspend include:
  //   + mutex granting (do not enter monitors when thread is suspended)
  //   + state transitions from _thread_in_native
  //
  // In general, java_suspend() does not wait for an external suspend
  // request to complete. When it returns, the only guarantee is that
  // the _external_suspend field is true.
  //
  // wait_for_ext_suspend_completion() is used to wait for an external
  // suspend request to complete. External suspend requests are usually
  // followed by some other interface call that requires the thread to
  // be quiescent, e.g., GetCallTrace(). By moving the "wait time" into
  // the interface that requires quiescence, we give the JavaThread a
  // chance to self-suspend before we need it to be quiescent. This
  // improves overall suspend/query performance.
  //
  // _suspend_flags controls the behavior of java_ suspend/resume.
  // It must be set under the protection of SR_lock. Read from the flag is
  // OK without SR_lock as long as the value is only used as a hint.
  // (e.g., check _external_suspend first without lock and then recheck
  // inside SR_lock and finish the suspension)
  //
  // _suspend_flags is also overloaded for other "special conditions" so
  // that a single check indicates whether any special action is needed
  // eg. for async exceptions.
  // -------------------------------------------------------------------
  // Notes:
  // 1. The suspend/resume logic no longer uses ThreadState in OSThread
  // but we still update its value to keep other part of the system (mainly
  // JVMTI) happy. ThreadState is legacy code (see notes in
  // osThread.hpp).
  //
  // 2. It would be more natural if set_external_suspend() is private and
  // part of java_suspend(), but that probably would affect the suspend/query
  // performance. Need more investigation on this.
  //

  // suspend/resume lock: used for self-suspend
  Monitor* _SR_lock;

 protected:
  enum SuspendFlags {
    // NOTE: avoid using the sign-bit as cc generates different test code
    //       when the sign-bit is used, and sometimes incorrectly - see CR 6398077

    _external_suspend       = 0x20000000U, // thread is asked to self suspend
    _ext_suspended          = 0x40000000U, // thread has self-suspended
    _deopt_suspend          = 0x10000000U, // thread needs to self suspend for deopt

    _has_async_exception    = 0x00000001U, // there is a pending async exception
    _critical_native_unlock = 0x00000002U  // Must call back to unlock JNI critical lock
  };

  // various suspension related flags - atomically updated
  // overloaded for async exception checking in check_special_condition_for_native_trans.
  volatile uint32_t _suspend_flags;

 private:
  int _num_nested_signal;

 public:
  void enter_signal_handler() { _num_nested_signal++; }
  void leave_signal_handler() { _num_nested_signal--; }
  bool is_inside_signal_handler() const { return _num_nested_signal > 0; }

 private:
  // Debug tracing
  static void trace(const char* msg, const Thread* const thread) PRODUCT_RETURN;

  // Active_handles points to a block of handles
  JNIHandleBlock* _active_handles;

  // One-element thread local free list
  JNIHandleBlock* _free_handle_block;

  // Point to the last handle mark
  HandleMark* _last_handle_mark;

  // The parity of the last strong_roots iteration in which this thread was
  // claimed as a task.
  jint _oops_do_parity;

  public:
   void set_last_handle_mark(HandleMark* mark)   { _last_handle_mark = mark; }
   HandleMark* last_handle_mark() const          { return _last_handle_mark; }
  private:

  // debug support for checking if code does allow safepoints or not
  // GC points in the VM can happen because of allocation, invoking a VM operation, or blocking on
  // mutex, or blocking on an object synchronizer (Java locking).
  // If !allow_safepoint(), then an assertion failure will happen in any of the above cases
  // If !allow_allocation(), then an assertion failure will happen during allocation
  // (Hence, !allow_safepoint() => !allow_allocation()).
  //
  // The two classes No_Safepoint_Verifier and No_Allocation_Verifier are used to set these counters.
  //
  NOT_PRODUCT(int _allow_safepoint_count;)      // If 0, thread allow a safepoint to happen
  debug_only (int _allow_allocation_count;)     // If 0, the thread is allowed to allocate oops.

  // Used by SkipGCALot class.
  NOT_PRODUCT(bool _skip_gcalot;)               // Should we elide gc-a-lot?

  // Record when GC is locked out via the GC_locker mechanism
  CHECK_UNHANDLED_OOPS_ONLY(int _gc_locked_out_count;)

  friend class No_Alloc_Verifier;
  friend class No_Safepoint_Verifier;
  friend class Pause_No_Safepoint_Verifier;
  friend class ThreadLocalStorage;
  friend class GC_locker;

  ThreadLocalAllocBuffer _tlab;                 // Thread-local eden
  jlong _allocated_bytes;                       // Cumulative number of bytes allocated on
                                                // the Java heap

  TRACE_DATA _trace_data;                       // Thread-local data for tracing

  int   _vm_operation_started_count;            // VM_Operation support
  int   _vm_operation_completed_count;          // VM_Operation support

  ObjectMonitor* _current_pending_monitor;      // ObjectMonitor this thread
                                                // is waiting to lock
  bool _current_pending_monitor_is_from_java;   // locking is from Java code

  // ObjectMonitor on which this thread called Object.wait()
  ObjectMonitor* _current_waiting_monitor;

  // Private thread-local objectmonitor list - a simple cache organized as a SLL.
 public:
  ObjectMonitor* omFreeList;
  int omFreeCount;                              // length of omFreeList
  int omFreeProvision;                          // reload chunk size
  ObjectMonitor* omInUseList;                   // SLL to track monitors in circulation
  int omInUseCount;                             // length of omInUseList

#ifdef ASSERT
 private:
  bool _visited_for_critical_count;

 public:
  void set_visited_for_critical_count(bool z) { _visited_for_critical_count = z; }
  bool was_visited_for_critical_count() const   { return _visited_for_critical_count; }
#endif

 public:
  enum {
    is_definitely_current_thread = true
  };

  // Constructor
  Thread();
  virtual ~Thread();

  // initializtion
  void initialize_thread_local_storage();

  // thread entry point
  virtual void run();

  // Testers
  virtual bool is_VM_thread()       const            { return false; }
  virtual bool is_Java_thread()     const            { return false; }
  virtual bool is_Compiler_thread() const            { return false; }
  virtual bool is_hidden_from_external_view() const  { return false; }
  virtual bool is_jvmti_agent_thread() const         { return false; }
  // True iff the thread can perform GC operations at a safepoint.
  // Generally will be true only of VM thread and parallel GC WorkGang
  // threads.
  virtual bool is_GC_task_thread() const             { return false; }
  virtual bool is_Watcher_thread() const             { return false; }
  virtual bool is_ConcurrentGC_thread() const        { return false; }
  virtual bool is_Named_thread() const               { return false; }
  virtual bool is_Worker_thread() const              { return false; }

  // Casts
  virtual WorkerThread* as_Worker_thread() const     { return NULL; }

  virtual char* name() const { return (char*)"Unknown thread"; }

  // Returns the current thread
  static inline Thread* current();

  // Common thread operations
  static void set_priority(Thread* thread, ThreadPriority priority);
  static ThreadPriority get_priority(const Thread* const thread);
  static void start(Thread* thread);
  static void interrupt(Thread* thr);
  static bool is_interrupted(Thread* thr, bool clear_interrupted);

  void set_native_thread_name(const char *name) {
    assert(Thread::current() == this, "set_native_thread_name can only be called on the current thread");
    os::set_native_thread_name(name);
  }

  ObjectMonitor** omInUseList_addr()             { return (ObjectMonitor **)&omInUseList; }
  Monitor* SR_lock() const                       { return _SR_lock; }

  bool has_async_exception() const { return (_suspend_flags & _has_async_exception) != 0; }

  void set_suspend_flag(SuspendFlags f) {
    assert(sizeof(jint) == sizeof(_suspend_flags), "size mismatch");
    uint32_t flags;
    do {
      flags = _suspend_flags;
    }
    while (Atomic::cmpxchg((jint)(flags | f),
                           (volatile jint*)&_suspend_flags,
                           (jint)flags) != (jint)flags);
  }
  void clear_suspend_flag(SuspendFlags f) {
    assert(sizeof(jint) == sizeof(_suspend_flags), "size mismatch");
    uint32_t flags;
    do {
      flags = _suspend_flags;
    }
    while (Atomic::cmpxchg((jint)(flags & ~f),
                           (volatile jint*)&_suspend_flags,
                           (jint)flags) != (jint)flags);
  }

  void set_has_async_exception() {
    set_suspend_flag(_has_async_exception);
  }
  void clear_has_async_exception() {
    clear_suspend_flag(_has_async_exception);
  }

  bool do_critical_native_unlock() const { return (_suspend_flags & _critical_native_unlock) != 0; }

  void set_critical_native_unlock() {
    set_suspend_flag(_critical_native_unlock);
  }
  void clear_critical_native_unlock() {
    clear_suspend_flag(_critical_native_unlock);
  }

  // Support for Unhandled Oop detection
#ifdef CHECK_UNHANDLED_OOPS
 private:
  UnhandledOops* _unhandled_oops;
 public:
  UnhandledOops* unhandled_oops() { return _unhandled_oops; }
  // Mark oop safe for gc.  It may be stack allocated but won't move.
  void allow_unhandled_oop(oop *op) {
    if (CheckUnhandledOops) unhandled_oops()->allow_unhandled_oop(op);
  }
  // Clear oops at safepoint so crashes point to unhandled oop violator
  void clear_unhandled_oops() {
    if (CheckUnhandledOops) unhandled_oops()->clear_unhandled_oops();
  }
  bool is_gc_locked_out() { return _gc_locked_out_count > 0; }
#endif // CHECK_UNHANDLED_OOPS

#ifndef PRODUCT
  bool skip_gcalot()           { return _skip_gcalot; }
  void set_skip_gcalot(bool v) { _skip_gcalot = v;    }
#endif

 public:
  // Installs a pending exception to be inserted later
  static void send_async_exception(oop thread_oop, oop java_throwable);

  // Resource area
  ResourceArea* resource_area() const            { return _resource_area; }
  void set_resource_area(ResourceArea* area)     { _resource_area = area; }

  OSThread* osthread() const                     { return _osthread;   }
  void set_osthread(OSThread* thread)            { _osthread = thread; }

  // JNI handle support
  JNIHandleBlock* active_handles() const         { return _active_handles; }
  void set_active_handles(JNIHandleBlock* block) { _active_handles = block; }
  JNIHandleBlock* free_handle_block() const      { return _free_handle_block; }
  void set_free_handle_block(JNIHandleBlock* block) { _free_handle_block = block; }

  // Internal handle support
  HandleArea* handle_area() const                { return _handle_area; }
  void set_handle_area(HandleArea* area)         { _handle_area = area; }

  GrowableArray<Metadata*>* metadata_handles() const          { return _metadata_handles; }
  void set_metadata_handles(GrowableArray<Metadata*>* handles){ _metadata_handles = handles; }

  // Thread-Local Allocation Buffer (TLAB) support
  ThreadLocalAllocBuffer& tlab()                 { return _tlab; }
  void initialize_tlab() {
    if (UseTLAB) {
      tlab().initialize();
    }
  }

  jlong allocated_bytes()               { return _allocated_bytes; }
  void set_allocated_bytes(jlong value) { _allocated_bytes = value; }
  void incr_allocated_bytes(jlong size) { _allocated_bytes += size; }
  jlong cooked_allocated_bytes() {
    jlong allocated_bytes = OrderAccess::load_acquire(&_allocated_bytes);
    if (UseTLAB) {
      size_t used_bytes = tlab().used_bytes();
      if ((ssize_t)used_bytes > 0) {
        // More-or-less valid tlab.  The load_acquire above should ensure
        // that the result of the add is <= the instantaneous value
        return allocated_bytes + used_bytes;
      }
    }
    return allocated_bytes;
  }

  TRACE_DATA* trace_data()              { return &_trace_data; }

  // VM operation support
  int vm_operation_ticket()                      { return ++_vm_operation_started_count; }
  int vm_operation_completed_count()             { return _vm_operation_completed_count; }
  void increment_vm_operation_completed_count()  { _vm_operation_completed_count++; }

  // For tracking the heavyweight monitor the thread is pending on.
  ObjectMonitor* current_pending_monitor() {
    return _current_pending_monitor;
  }
  void set_current_pending_monitor(ObjectMonitor* monitor) {
    _current_pending_monitor = monitor;
  }
  void set_current_pending_monitor_is_from_java(bool from_java) {
    _current_pending_monitor_is_from_java = from_java;
  }
  bool current_pending_monitor_is_from_java() {
    return _current_pending_monitor_is_from_java;
  }

  // For tracking the ObjectMonitor on which this thread called Object.wait()
  ObjectMonitor* current_waiting_monitor() {
    return _current_waiting_monitor;
  }
  void set_current_waiting_monitor(ObjectMonitor* monitor) {
    _current_waiting_monitor = monitor;
  }

  // GC support
  // Apply "f->do_oop" to all root oops in "this".
  // Apply "cld_f->do_cld" to CLDs that are otherwise not kept alive.
  //   Used by JavaThread::oops_do.
  // Apply "cf->do_code_blob" (if !NULL) to all code blobs active in frames
  virtual void oops_do(OopClosure* f, CLDToOopClosure* cld_f, CodeBlobClosure* cf);

  // Handles the parallel case for the method below.
private:
  bool claim_oops_do_par_case(int collection_parity);
public:
  // Requires that "collection_parity" is that of the current strong roots
  // iteration.  If "is_par" is false, sets the parity of "this" to
  // "collection_parity", and returns "true".  If "is_par" is true,
  // uses an atomic instruction to set the current threads parity to
  // "collection_parity", if it is not already.  Returns "true" iff the
  // calling thread does the update, this indicates that the calling thread
  // has claimed the thread's stack as a root groop in the current
  // collection.
  bool claim_oops_do(bool is_par, int collection_parity) {
    if (!is_par) {
      _oops_do_parity = collection_parity;
      return true;
    } else {
      return claim_oops_do_par_case(collection_parity);
    }
  }

  // Sweeper support
  void nmethods_do(CodeBlobClosure* cf);

  // jvmtiRedefineClasses support
  void metadata_do(void f(Metadata*));

  // Used by fast lock support
  virtual bool is_lock_owned(address adr) const;

  // Check if address is in the stack of the thread (not just for locks).
  // Warning: the method can only be used on the running thread
  bool is_in_stack(address adr) const;
  // Check if address is in the usable part of the stack (excludes protected
  // guard pages)
  bool is_in_usable_stack(address adr) const;

  // Sets this thread as starting thread. Returns failure if thread
  // creation fails due to lack of memory, too many threads etc.
  bool set_as_starting_thread();

 protected:
  // OS data associated with the thread
  OSThread* _osthread;  // Platform-specific thread information

  // Thread local resource area for temporary allocation within the VM
  ResourceArea* _resource_area;

  DEBUG_ONLY(ResourceMark* _current_resource_mark;)

  // Thread local handle area for allocation of handles within the VM
  HandleArea* _handle_area;
  GrowableArray<Metadata*>* _metadata_handles;

  // Support for stack overflow handling, get_thread, etc.
  address          _stack_base;
  size_t           _stack_size;
  uintptr_t        _self_raw_id;      // used by get_thread (mutable)
  int              _lgrp_id;

 public:
  // Stack overflow support
  address stack_base() const           { assert(_stack_base != NULL,"Sanity check"); return _stack_base; }

  void    set_stack_base(address base) { _stack_base = base; }
  size_t  stack_size() const           { return _stack_size; }
  void    set_stack_size(size_t size)  { _stack_size = size; }
  void    record_stack_base_and_size();

  bool    on_local_stack(address adr) const {
    /* QQQ this has knowledge of direction, ought to be a stack method */
    return (_stack_base >= adr && adr >= (_stack_base - _stack_size));
  }

  uintptr_t self_raw_id()                    { return _self_raw_id; }
  void      set_self_raw_id(uintptr_t value) { _self_raw_id = value; }

  int     lgrp_id() const        { return _lgrp_id; }
  void    set_lgrp_id(int value) { _lgrp_id = value; }

  // Printing
  void print_on(outputStream* st) const;
  void print() const { print_on(tty); }
  virtual void print_on_error(outputStream* st, char* buf, int buflen) const;

  // Debug-only code
#ifdef ASSERT
 private:
  // Deadlock detection support for Mutex locks. List of locks own by thread.
  Monitor* _owned_locks;
  // Mutex::set_owner_implementation is the only place where _owned_locks is modified,
  // thus the friendship
  friend class Mutex;
  friend class Monitor;

 public:
  void print_owned_locks_on(outputStream* st) const;
  void print_owned_locks() const                 { print_owned_locks_on(tty);    }
  Monitor* owned_locks() const                   { return _owned_locks;          }
  bool owns_locks() const                        { return owned_locks() != NULL; }
  bool owns_locks_but_compiled_lock() const;

  // Deadlock detection
  bool allow_allocation()                        { return _allow_allocation_count == 0; }
  ResourceMark* current_resource_mark()          { return _current_resource_mark; }
  void set_current_resource_mark(ResourceMark* rm) { _current_resource_mark = rm; }
#endif

  void check_for_valid_safepoint_state(bool potential_vm_operation) PRODUCT_RETURN;

 private:
  volatile int _jvmti_env_iteration_count;

 public:
  void entering_jvmti_env_iteration()            { ++_jvmti_env_iteration_count; }
  void leaving_jvmti_env_iteration()             { --_jvmti_env_iteration_count; }
  bool is_inside_jvmti_env_iteration()           { return _jvmti_env_iteration_count > 0; }

  // Code generation
  static ByteSize exception_file_offset()        { return byte_offset_of(Thread, _exception_file   ); }
  static ByteSize exception_line_offset()        { return byte_offset_of(Thread, _exception_line   ); }
  static ByteSize active_handles_offset()        { return byte_offset_of(Thread, _active_handles   ); }

  static ByteSize stack_base_offset()            { return byte_offset_of(Thread, _stack_base ); }
  static ByteSize stack_size_offset()            { return byte_offset_of(Thread, _stack_size ); }

#define TLAB_FIELD_OFFSET(name) \
  static ByteSize tlab_##name##_offset()         { return byte_offset_of(Thread, _tlab) + ThreadLocalAllocBuffer::name##_offset(); }

  TLAB_FIELD_OFFSET(start)
  TLAB_FIELD_OFFSET(end)
  TLAB_FIELD_OFFSET(top)
  TLAB_FIELD_OFFSET(pf_top)
  TLAB_FIELD_OFFSET(size)                   // desired_size
  TLAB_FIELD_OFFSET(refill_waste_limit)
  TLAB_FIELD_OFFSET(number_of_refills)
  TLAB_FIELD_OFFSET(fast_refill_waste)
  TLAB_FIELD_OFFSET(slow_allocations)

#undef TLAB_FIELD_OFFSET

  static ByteSize allocated_bytes_offset()       { return byte_offset_of(Thread, _allocated_bytes ); }

 public:
  volatile intptr_t _Stalled ;
  volatile int _TypeTag ;
  ParkEvent * _ParkEvent ;                     // for synchronized()
  ParkEvent * _SleepEvent ;                    // for Thread.sleep
  ParkEvent * _MutexEvent ;                    // for native internal Mutex/Monitor
  ParkEvent * _MuxEvent ;                      // for low-level muxAcquire-muxRelease
  int NativeSyncRecursion ;                    // diagnostic

  volatile int _OnTrap ;                       // Resume-at IP delta
  jint _hashStateW ;                           // Marsaglia Shift-XOR thread-local RNG
  jint _hashStateX ;                           // thread-specific hashCode generator state
  jint _hashStateY ;
  jint _hashStateZ ;
  void * _schedctl ;


  volatile jint rng [4] ;                      // RNG for spin loop

  // Low-level leaf-lock primitives used to implement synchronization
  // and native monitor-mutex infrastructure.
  // Not for general synchronization use.
  static void SpinAcquire (volatile int * Lock, const char * Name) ;
  static void SpinRelease (volatile int * Lock) ;
  static void muxAcquire  (volatile intptr_t * Lock, const char * Name) ;
  static void muxAcquireW (volatile intptr_t * Lock, ParkEvent * ev) ;
  static void muxRelease  (volatile intptr_t * Lock) ;
};

// Inline implementation of Thread::current()
// Thread::current is "hot" it's called > 128K times in the 1st 500 msecs of
// startup.
// ThreadLocalStorage::thread is warm -- it's called > 16K times in the same
// period.   This is inlined in thread_<os_family>.inline.hpp.

inline Thread* Thread::current() {
#ifdef ASSERT
// This function is very high traffic. Define PARANOID to enable expensive
// asserts.
#ifdef PARANOID
  // Signal handler should call ThreadLocalStorage::get_thread_slow()
  Thread* t = ThreadLocalStorage::get_thread_slow();
  assert(t != NULL && !t->is_inside_signal_handler(),
         "Don't use Thread::current() inside signal handler");
#endif
#endif
  Thread* thread = ThreadLocalStorage::thread();
  assert(thread != NULL, "just checking");
  return thread;
}

// Name support for threads.  non-JavaThread subclasses with multiple
// uniquely named instances should derive from this.
class NamedThread: public Thread {
  friend class VMStructs;
  enum {
    max_name_len = 64
  };
 private:
  char* _name;
  // log JavaThread being processed by oops_do
  JavaThread* _processed_thread;

 public:
  NamedThread();
  ~NamedThread();
  // May only be called once per thread.
  void set_name(const char* format, ...);
  virtual bool is_Named_thread() const { return true; }
  virtual char* name() const { return _name == NULL ? (char*)"Unknown Thread" : _name; }
  JavaThread *processed_thread() { return _processed_thread; }
  void set_processed_thread(JavaThread *thread) { _processed_thread = thread; }
};

// Worker threads are named and have an id of an assigned work.
class WorkerThread: public NamedThread {
private:
  uint _id;
public:
  WorkerThread() : _id(0)               { }
  virtual bool is_Worker_thread() const { return true; }

  virtual WorkerThread* as_Worker_thread() const {
    assert(is_Worker_thread(), "Dubious cast to WorkerThread*?");
    return (WorkerThread*) this;
  }

  void set_id(uint work_id)             { _id = work_id; }
  uint id() const                       { return _id; }
};

// A single WatcherThread is used for simulating timer interrupts.
class WatcherThread: public Thread {
  friend class VMStructs;
 public:
  virtual void run();

 private:
  static WatcherThread* _watcher_thread;

  static bool _startable;
  volatile static bool _should_terminate; // updated without holding lock

  os::WatcherThreadCrashProtection* _crash_protection;
 public:
  enum SomeConstants {
    delay_interval = 10                          // interrupt delay in milliseconds
  };

  // Constructor
  WatcherThread();

  // Tester
  bool is_Watcher_thread() const                 { return true; }

  // Printing
  char* name() const { return (char*)"VM Periodic Task Thread"; }
  void print_on(outputStream* st) const;
  void print() const { print_on(tty); }
  void unpark();

  // Returns the single instance of WatcherThread
  static WatcherThread* watcher_thread()         { return _watcher_thread; }

  // Create and start the single instance of WatcherThread, or stop it on shutdown
  static void start();
  static void stop();
  // Only allow start once the VM is sufficiently initialized
  // Otherwise the first task to enroll will trigger the start
  static void make_startable();

  void set_crash_protection(os::WatcherThreadCrashProtection* crash_protection) {
    assert(Thread::current()->is_Watcher_thread(), "Can only be set by WatcherThread");
    _crash_protection = crash_protection;
  }

  bool has_crash_protection() const { return _crash_protection != NULL; }
  os::WatcherThreadCrashProtection* crash_protection() const { return _crash_protection; }

 private:
  int sleep() const;
};


class CompilerThread;

typedef void (*ThreadFunction)(JavaThread*, TRAPS);

class JavaThread: public Thread {
  friend class VMStructs;
 private:
  JavaThread*    _next;                          // The next thread in the Threads list
  oop            _threadObj;                     // The Java level thread object

#ifdef ASSERT
 private:
  int _java_call_counter;

 public:
  int  java_call_counter()                       { return _java_call_counter; }
  void inc_java_call_counter()                   { _java_call_counter++; }
  void dec_java_call_counter() {
    assert(_java_call_counter > 0, "Invalid nesting of JavaCallWrapper");
    _java_call_counter--;
  }
 private:  // restore original namespace restriction
#endif  // ifdef ASSERT

#ifndef PRODUCT
 public:
  enum {
    jump_ring_buffer_size = 16
  };
 private:  // restore original namespace restriction
#endif

  JavaFrameAnchor _anchor;                       // Encapsulation of current java frame and it state

  ThreadFunction _entry_point;

  JNIEnv        _jni_environment;

  // Deopt support
  DeoptResourceMark*  _deopt_mark;               // Holds special ResourceMark for deoptimization

  intptr_t*      _must_deopt_id;                 // id of frame that needs to be deopted once we
                                                 // transition out of native
  nmethod*       _deopt_nmethod;                 // nmethod that is currently being deoptimized
  vframeArray*  _vframe_array_head;              // Holds the heap of the active vframeArrays
  vframeArray*  _vframe_array_last;              // Holds last vFrameArray we popped
  // Because deoptimization is lazy we must save jvmti requests to set locals
  // in compiled frames until we deoptimize and we have an interpreter frame.
  // This holds the pointer to array (yeah like there might be more than one) of
  // description of compiled vframes that have locals that need to be updated.
  GrowableArray<jvmtiDeferredLocalVariableSet*>* _deferred_locals_updates;

  // Handshake value for fixing 6243940. We need a place for the i2c
  // adapter to store the callee Method*. This value is NEVER live
  // across a gc point so it does NOT have to be gc'd
  // The handshake is open ended since we can't be certain that it will
  // be NULLed. This is because we rarely ever see the race and end up
  // in handle_wrong_method which is the backend of the handshake. See
  // code in i2c adapters and handle_wrong_method.

  Method*       _callee_target;

  // Used to pass back results to the interpreter or generated code running Java code.
  oop           _vm_result;    // oop result is GC-preserved
  Metadata*     _vm_result_2;  // non-oop result

  // See ReduceInitialCardMarks: this holds the precise space interval of
  // the most recent slow path allocation for which compiled code has
  // elided card-marks for performance along the fast-path.
  MemRegion     _deferred_card_mark;

  MonitorChunk* _monitor_chunks;                 // Contains the off stack monitors
                                                 // allocated during deoptimization
                                                 // and by JNI_MonitorEnter/Exit

  // Async. requests support
  enum AsyncRequests {
    _no_async_condition = 0,
    _async_exception,
    _async_unsafe_access_error
  };
  AsyncRequests _special_runtime_exit_condition; // Enum indicating pending async. request
  oop           _pending_async_exception;

  // Safepoint support
 public:                                         // Expose _thread_state for SafeFetchInt()
  volatile JavaThreadState _thread_state;
 private:
  ThreadSafepointState *_safepoint_state;        // Holds information about a thread during a safepoint
  address               _saved_exception_pc;     // Saved pc of instruction where last implicit exception happened

  // JavaThread termination support
  enum TerminatedTypes {
    _not_terminated = 0xDEAD - 2,
    _thread_exiting,                             // JavaThread::exit() has been called for this thread
    _thread_terminated,                          // JavaThread is removed from thread list
    _vm_exited                                   // JavaThread is still executing native code, but VM is terminated
                                                 // only VM_Exit can set _vm_exited
  };

  // In general a JavaThread's _terminated field transitions as follows:
  //
  //   _not_terminated => _thread_exiting => _thread_terminated
  //
  // _vm_exited is a special value to cover the case of a JavaThread
  // executing native code after the VM itself is terminated.
  volatile TerminatedTypes _terminated;
  // suspend/resume support
  volatile bool         _suspend_equivalent;     // Suspend equivalent condition
  jint                  _in_deopt_handler;       // count of deoptimization
                                                 // handlers thread is in
  volatile bool         _doing_unsafe_access;    // Thread may fault due to unsafe access
  bool                  _do_not_unlock_if_synchronized; // Do not unlock the receiver of a synchronized method (since it was
                                                 // never locked) when throwing an exception. Used by interpreter only.

  // JNI attach states:
  enum JNIAttachStates {
    _not_attaching_via_jni = 1,  // thread is not attaching via JNI
    _attaching_via_jni,          // thread is attaching via JNI
    _attached_via_jni            // thread has attached via JNI
  };

  // A regular JavaThread's _jni_attach_state is _not_attaching_via_jni.
  // A native thread that is attaching via JNI starts with a value
  // of _attaching_via_jni and transitions to _attached_via_jni.
  volatile JNIAttachStates _jni_attach_state;

 public:
  // State of the stack guard pages for this thread.
  enum StackGuardState {
    stack_guard_unused,         // not needed
    stack_guard_yellow_disabled,// disabled (temporarily) after stack overflow
    stack_guard_enabled         // enabled
  };

 private:

  StackGuardState        _stack_guard_state;

  // Compiler exception handling (NOTE: The _exception_oop is *NOT* the same as _pending_exception. It is
  // used to temp. parsing values into and out of the runtime system during exception handling for compiled
  // code)
  volatile oop     _exception_oop;               // Exception thrown in compiled code
  volatile address _exception_pc;                // PC where exception happened
  volatile address _exception_handler_pc;        // PC for handler of exception
  volatile int     _is_method_handle_return;     // true (== 1) if the current exception PC is a MethodHandle call site.

  // support for compilation
  bool    _is_compiling;                         // is true if a compilation is active inthis thread (one compilation per thread possible)

  // support for JNI critical regions
  jint    _jni_active_critical;                  // count of entries into JNI critical region

  // For deadlock detection.
  int _depth_first_number;

  // JVMTI PopFrame support
  // This is set to popframe_pending to signal that top Java frame should be popped immediately
  int _popframe_condition;

#ifndef PRODUCT
  int _jmp_ring_index;
  struct {
      // We use intptr_t instead of address so debugger doesn't try and display strings
      intptr_t _target;
      intptr_t _instruction;
      const char*  _file;
      int _line;
  }   _jmp_ring[ jump_ring_buffer_size ];
#endif /* PRODUCT */

#if INCLUDE_ALL_GCS
  // Support for G1 barriers

  ObjPtrQueue _satb_mark_queue;          // Thread-local log for SATB barrier.
  // Set of all such queues.
  static SATBMarkQueueSet _satb_mark_queue_set;

  DirtyCardQueue _dirty_card_queue;      // Thread-local log for dirty cards.
  // Set of all such queues.
  static DirtyCardQueueSet _dirty_card_queue_set;

  void flush_barrier_queues();
#endif // INCLUDE_ALL_GCS

  friend class VMThread;
  friend class ThreadWaitTransition;
  friend class VM_Exit;

  void initialize();                             // Initialized the instance variables

 public:
  // Constructor
  JavaThread(bool is_attaching_via_jni = false); // for main thread and JNI attached threads
  JavaThread(ThreadFunction entry_point, size_t stack_size = 0);
  ~JavaThread();

#ifdef ASSERT
  // verify this JavaThread hasn't be published in the Threads::list yet
  void verify_not_published();
#endif

  //JNI functiontable getter/setter for JVMTI jni function table interception API.
  void set_jni_functions(struct JNINativeInterface_* functionTable) {
    _jni_environment.functions = functionTable;
  }
  struct JNINativeInterface_* get_jni_functions() {
    return (struct JNINativeInterface_ *)_jni_environment.functions;
  }

  // This function is called at thread creation to allow
  // platform specific thread variables to be initialized.
  void cache_global_variables();

  // Executes Shutdown.shutdown()
  void invoke_shutdown_hooks();

  // Cleanup on thread exit
  enum ExitType {
    normal_exit,
    jni_detach
  };
  void exit(bool destroy_vm, ExitType exit_type = normal_exit);

  void cleanup_failed_attach_current_thread();

  // Testers
  virtual bool is_Java_thread() const            { return true;  }

  // compilation
  void set_is_compiling(bool f)                  { _is_compiling = f; }
  bool is_compiling() const                      { return _is_compiling; }

  // Thread chain operations
  JavaThread* next() const                       { return _next; }
  void set_next(JavaThread* p)                   { _next = p; }

  // Thread oop. threadObj() can be NULL for initial JavaThread
  // (or for threads attached via JNI)
  oop threadObj() const                          { return _threadObj; }
  void set_threadObj(oop p)                      { _threadObj = p; }

  ThreadPriority java_priority() const;          // Read from threadObj()

  // Prepare thread and add to priority queue.  If a priority is
  // not specified, use the priority of the thread object. Threads_lock
  // must be held while this function is called.
  void prepare(jobject jni_thread, ThreadPriority prio=NoPriority);

  void set_saved_exception_pc(address pc)        { _saved_exception_pc = pc; }
  address saved_exception_pc()                   { return _saved_exception_pc; }


  ThreadFunction entry_point() const             { return _entry_point; }

  // Allocates a new Java level thread object for this thread. thread_name may be NULL.
  void allocate_threadObj(Handle thread_group, char* thread_name, bool daemon, TRAPS);

  // Last frame anchor routines

  JavaFrameAnchor* frame_anchor(void)                { return &_anchor; }

  // last_Java_sp
  bool has_last_Java_frame() const                   { return _anchor.has_last_Java_frame(); }
  intptr_t* last_Java_sp() const                     { return _anchor.last_Java_sp(); }

  // last_Java_pc

  address last_Java_pc(void)                         { return _anchor.last_Java_pc(); }

  // Safepoint support
  JavaThreadState thread_state() const           { return _thread_state; }
  void set_thread_state(JavaThreadState s)       { _thread_state=s;      }
  ThreadSafepointState *safepoint_state() const  { return _safepoint_state;  }
  void set_safepoint_state(ThreadSafepointState *state) { _safepoint_state = state; }
  bool is_at_poll_safepoint()                    { return _safepoint_state->is_at_poll_safepoint(); }

  // thread has called JavaThread::exit() or is terminated
  bool is_exiting()                              { return _terminated == _thread_exiting || is_terminated(); }
  // thread is terminated (no longer on the threads list); we compare
  // against the two non-terminated values so that a freed JavaThread
  // will also be considered terminated.
  bool is_terminated()                           { return _terminated != _not_terminated && _terminated != _thread_exiting; }
  void set_terminated(TerminatedTypes t)         { _terminated = t; }
  // special for Threads::remove() which is static:
  void set_terminated_value()                    { _terminated = _thread_terminated; }
  void block_if_vm_exited();

  bool doing_unsafe_access()                     { return _doing_unsafe_access; }
  void set_doing_unsafe_access(bool val)         { _doing_unsafe_access = val; }

  bool do_not_unlock_if_synchronized()             { return _do_not_unlock_if_synchronized; }
  void set_do_not_unlock_if_synchronized(bool val) { _do_not_unlock_if_synchronized = val; }

#if INCLUDE_NMT
  // native memory tracking
  inline MemRecorder* get_recorder() const          { return (MemRecorder*)_recorder; }
  inline void         set_recorder(MemRecorder* rc) { _recorder = rc; }

 private:
  // per-thread memory recorder
  MemRecorder* volatile _recorder;
#endif // INCLUDE_NMT

  // Suspend/resume support for JavaThread
 private:
  void set_ext_suspended()       { set_suspend_flag (_ext_suspended);  }
  void clear_ext_suspended()     { clear_suspend_flag(_ext_suspended); }

 public:
  void java_suspend();
  void java_resume();
  int  java_suspend_self();

  void check_and_wait_while_suspended() {
    assert(JavaThread::current() == this, "sanity check");

    bool do_self_suspend;
    do {
      // were we externally suspended while we were waiting?
      do_self_suspend = handle_special_suspend_equivalent_condition();
      if (do_self_suspend) {
        // don't surprise the thread that suspended us by returning
        java_suspend_self();
        set_suspend_equivalent();
      }
    } while (do_self_suspend);
  }
  static void check_safepoint_and_suspend_for_native_trans(JavaThread *thread);
  // Check for async exception in addition to safepoint and suspend request.
  static void check_special_condition_for_native_trans(JavaThread *thread);

  // Same as check_special_condition_for_native_trans but finishes the
  // transition into thread_in_Java mode so that it can potentially
  // block.
  static void check_special_condition_for_native_trans_and_transition(JavaThread *thread);

  bool is_ext_suspend_completed(bool called_by_wait, int delay, uint32_t *bits);
  bool is_ext_suspend_completed_with_lock(uint32_t *bits) {
    MutexLockerEx ml(SR_lock(), Mutex::_no_safepoint_check_flag);
    // Warning: is_ext_suspend_completed() may temporarily drop the
    // SR_lock to allow the thread to reach a stable thread state if
    // it is currently in a transient thread state.
    return is_ext_suspend_completed(false /*!called_by_wait */,
                                    SuspendRetryDelay, bits);
  }

  // We cannot allow wait_for_ext_suspend_completion() to run forever or
  // we could hang. SuspendRetryCount and SuspendRetryDelay are normally
  // passed as the count and delay parameters. Experiments with specific
  // calls to wait_for_ext_suspend_completion() can be done by passing
  // other values in the code. Experiments with all calls can be done
  // via the appropriate -XX options.
  bool wait_for_ext_suspend_completion(int count, int delay, uint32_t *bits);

  void set_external_suspend()     { set_suspend_flag  (_external_suspend); }
  void clear_external_suspend()   { clear_suspend_flag(_external_suspend); }

  void set_deopt_suspend()        { set_suspend_flag  (_deopt_suspend); }
  void clear_deopt_suspend()      { clear_suspend_flag(_deopt_suspend); }
  bool is_deopt_suspend()         { return (_suspend_flags & _deopt_suspend) != 0; }

  bool is_external_suspend() const {
    return (_suspend_flags & _external_suspend) != 0;
  }
  // Whenever a thread transitions from native to vm/java it must suspend
  // if external|deopt suspend is present.
  bool is_suspend_after_native() const {
    return (_suspend_flags & (_external_suspend | _deopt_suspend) ) != 0;
  }

  // external suspend request is completed
  bool is_ext_suspended() const {
    return (_suspend_flags & _ext_suspended) != 0;
  }

  bool is_external_suspend_with_lock() const {
    MutexLockerEx ml(SR_lock(), Mutex::_no_safepoint_check_flag);
    return is_external_suspend();
  }

  // Special method to handle a pending external suspend request
  // when a suspend equivalent condition lifts.
  bool handle_special_suspend_equivalent_condition() {
    assert(is_suspend_equivalent(),
      "should only be called in a suspend equivalence condition");
    MutexLockerEx ml(SR_lock(), Mutex::_no_safepoint_check_flag);
    bool ret = is_external_suspend();
    if (!ret) {
      // not about to self-suspend so clear suspend equivalence
      clear_suspend_equivalent();
    }
    // implied else:
    // We have a pending external suspend request so we leave the
    // suspend_equivalent flag set until java_suspend_self() sets
    // the ext_suspended flag and clears the suspend_equivalent
    // flag. This insures that wait_for_ext_suspend_completion()
    // will return consistent values.
    return ret;
  }

  // utility methods to see if we are doing some kind of suspension
  bool is_being_ext_suspended() const            {
    MutexLockerEx ml(SR_lock(), Mutex::_no_safepoint_check_flag);
    return is_ext_suspended() || is_external_suspend();
  }

  bool is_suspend_equivalent() const             { return _suspend_equivalent; }

  void set_suspend_equivalent()                  { _suspend_equivalent = true; };
  void clear_suspend_equivalent()                { _suspend_equivalent = false; };

  // Thread.stop support
  void send_thread_stop(oop throwable);
  AsyncRequests clear_special_runtime_exit_condition() {
    AsyncRequests x = _special_runtime_exit_condition;
    _special_runtime_exit_condition = _no_async_condition;
    return x;
  }

  // Are any async conditions present?
  bool has_async_condition() { return (_special_runtime_exit_condition != _no_async_condition); }

  void check_and_handle_async_exceptions(bool check_unsafe_error = true);

  // these next two are also used for self-suspension and async exception support
  void handle_special_runtime_exit_condition(bool check_asyncs = true);

  // Return true if JavaThread has an asynchronous condition or
  // if external suspension is requested.
  bool has_special_runtime_exit_condition() {
    // We call is_external_suspend() last since external suspend should
    // be less common. Because we don't use is_external_suspend_with_lock
    // it is possible that we won't see an asynchronous external suspend
    // request that has just gotten started, i.e., SR_lock grabbed but
    // _external_suspend field change either not made yet or not visible
    // yet. However, this is okay because the request is asynchronous and
    // we will see the new flag value the next time through. It's also
    // possible that the external suspend request is dropped after
    // we have checked is_external_suspend(), we will recheck its value
    // under SR_lock in java_suspend_self().
    return (_special_runtime_exit_condition != _no_async_condition) ||
            is_external_suspend() || is_deopt_suspend();
  }

  void set_pending_unsafe_access_error()          { _special_runtime_exit_condition = _async_unsafe_access_error; }

  void set_pending_async_exception(oop e) {
    _pending_async_exception = e;
    _special_runtime_exit_condition = _async_exception;
    set_has_async_exception();
  }

  // Fast-locking support
  bool is_lock_owned(address adr) const;

  // Accessors for vframe array top
  // The linked list of vframe arrays are sorted on sp. This means when we
  // unpack the head must contain the vframe array to unpack.
  void set_vframe_array_head(vframeArray* value) { _vframe_array_head = value; }
  vframeArray* vframe_array_head() const         { return _vframe_array_head;  }

  // Side structure for defering update of java frame locals until deopt occurs
  GrowableArray<jvmtiDeferredLocalVariableSet*>* deferred_locals() const { return _deferred_locals_updates; }
  void set_deferred_locals(GrowableArray<jvmtiDeferredLocalVariableSet *>* vf) { _deferred_locals_updates = vf; }

  // These only really exist to make debugging deopt problems simpler

  void set_vframe_array_last(vframeArray* value) { _vframe_array_last = value; }
  vframeArray* vframe_array_last() const         { return _vframe_array_last;  }

  // The special resourceMark used during deoptimization

  void set_deopt_mark(DeoptResourceMark* value)  { _deopt_mark = value; }
  DeoptResourceMark* deopt_mark(void)            { return _deopt_mark; }

  intptr_t* must_deopt_id()                      { return _must_deopt_id; }
  void     set_must_deopt_id(intptr_t* id)       { _must_deopt_id = id; }
  void     clear_must_deopt_id()                 { _must_deopt_id = NULL; }

  void set_deopt_nmethod(nmethod* nm)            { _deopt_nmethod = nm;   }
  nmethod* deopt_nmethod()                       { return _deopt_nmethod; }

  Method*    callee_target() const               { return _callee_target; }
  void set_callee_target  (Method* x)          { _callee_target   = x; }

  // Oop results of vm runtime calls
  oop  vm_result() const                         { return _vm_result; }
  void set_vm_result  (oop x)                    { _vm_result   = x; }

  Metadata*    vm_result_2() const               { return _vm_result_2; }
  void set_vm_result_2  (Metadata* x)          { _vm_result_2   = x; }

  MemRegion deferred_card_mark() const           { return _deferred_card_mark; }
  void set_deferred_card_mark(MemRegion mr)      { _deferred_card_mark = mr;   }

  // Exception handling for compiled methods
  oop      exception_oop() const                 { return _exception_oop; }
  address  exception_pc() const                  { return _exception_pc; }
  address  exception_handler_pc() const          { return _exception_handler_pc; }
  bool     is_method_handle_return() const       { return _is_method_handle_return == 1; }

  void set_exception_oop(oop o)                  { _exception_oop = o; }
  void set_exception_pc(address a)               { _exception_pc = a; }
  void set_exception_handler_pc(address a)       { _exception_handler_pc = a; }
  void set_is_method_handle_return(bool value)   { _is_method_handle_return = value ? 1 : 0; }

  // Stack overflow support
  inline size_t stack_available(address cur_sp);
  address stack_yellow_zone_base()
    { return (address)(stack_base() - (stack_size() - (stack_red_zone_size() + stack_yellow_zone_size()))); }
  size_t  stack_yellow_zone_size()
    { return StackYellowPages * os::vm_page_size(); }
  address stack_red_zone_base()
    { return (address)(stack_base() - (stack_size() - stack_red_zone_size())); }
  size_t stack_red_zone_size()
    { return StackRedPages * os::vm_page_size(); }
  bool in_stack_yellow_zone(address a)
    { return (a <= stack_yellow_zone_base()) && (a >= stack_red_zone_base()); }
  bool in_stack_red_zone(address a)
    { return (a <= stack_red_zone_base()) && (a >= (address)((intptr_t)stack_base() - stack_size())); }

  void create_stack_guard_pages();
  void remove_stack_guard_pages();

  void enable_stack_yellow_zone();
  void disable_stack_yellow_zone();
  void enable_stack_red_zone();
  void disable_stack_red_zone();

  inline bool stack_guard_zone_unused();
  inline bool stack_yellow_zone_disabled();
  inline bool stack_yellow_zone_enabled();

  // Attempt to reguard the stack after a stack overflow may have occurred.
  // Returns true if (a) guard pages are not needed on this thread, (b) the
  // pages are already guarded, or (c) the pages were successfully reguarded.
  // Returns false if there is not enough stack space to reguard the pages, in
  // which case the caller should unwind a frame and try again.  The argument
  // should be the caller's (approximate) sp.
  bool reguard_stack(address cur_sp);
  // Similar to above but see if current stackpoint is out of the guard area
  // and reguard if possible.
  bool reguard_stack(void);

  // Misc. accessors/mutators
  void set_do_not_unlock(void)                   { _do_not_unlock_if_synchronized = true; }
  void clr_do_not_unlock(void)                   { _do_not_unlock_if_synchronized = false; }
  bool do_not_unlock(void)                       { return _do_not_unlock_if_synchronized; }

#ifndef PRODUCT
  void record_jump(address target, address instr, const char* file, int line);
#endif /* PRODUCT */

  // For assembly stub generation
  static ByteSize threadObj_offset()             { return byte_offset_of(JavaThread, _threadObj           ); }
#ifndef PRODUCT
  static ByteSize jmp_ring_index_offset()        { return byte_offset_of(JavaThread, _jmp_ring_index      ); }
  static ByteSize jmp_ring_offset()              { return byte_offset_of(JavaThread, _jmp_ring            ); }
#endif /* PRODUCT */
  static ByteSize jni_environment_offset()       { return byte_offset_of(JavaThread, _jni_environment     ); }
  static ByteSize last_Java_sp_offset()          {
    return byte_offset_of(JavaThread, _anchor) + JavaFrameAnchor::last_Java_sp_offset();
  }
  static ByteSize last_Java_pc_offset()          {
    return byte_offset_of(JavaThread, _anchor) + JavaFrameAnchor::last_Java_pc_offset();
  }
  static ByteSize frame_anchor_offset()          {
    return byte_offset_of(JavaThread, _anchor);
  }
  static ByteSize callee_target_offset()         { return byte_offset_of(JavaThread, _callee_target       ); }
  static ByteSize vm_result_offset()             { return byte_offset_of(JavaThread, _vm_result           ); }
  static ByteSize vm_result_2_offset()           { return byte_offset_of(JavaThread, _vm_result_2         ); }
  static ByteSize thread_state_offset()          { return byte_offset_of(JavaThread, _thread_state        ); }
  static ByteSize saved_exception_pc_offset()    { return byte_offset_of(JavaThread, _saved_exception_pc  ); }
  static ByteSize osthread_offset()              { return byte_offset_of(JavaThread, _osthread            ); }
  static ByteSize exception_oop_offset()         { return byte_offset_of(JavaThread, _exception_oop       ); }
  static ByteSize exception_pc_offset()          { return byte_offset_of(JavaThread, _exception_pc        ); }
  static ByteSize exception_handler_pc_offset()  { return byte_offset_of(JavaThread, _exception_handler_pc); }
  static ByteSize is_method_handle_return_offset() { return byte_offset_of(JavaThread, _is_method_handle_return); }
  static ByteSize stack_guard_state_offset()     { return byte_offset_of(JavaThread, _stack_guard_state   ); }
  static ByteSize suspend_flags_offset()         { return byte_offset_of(JavaThread, _suspend_flags       ); }

  static ByteSize do_not_unlock_if_synchronized_offset() { return byte_offset_of(JavaThread, _do_not_unlock_if_synchronized); }
  static ByteSize should_post_on_exceptions_flag_offset() {
    return byte_offset_of(JavaThread, _should_post_on_exceptions_flag);
  }

#if INCLUDE_ALL_GCS
  static ByteSize satb_mark_queue_offset()       { return byte_offset_of(JavaThread, _satb_mark_queue); }
  static ByteSize dirty_card_queue_offset()      { return byte_offset_of(JavaThread, _dirty_card_queue); }
#endif // INCLUDE_ALL_GCS

  // Returns the jni environment for this thread
  JNIEnv* jni_environment()                      { return &_jni_environment; }

  static JavaThread* thread_from_jni_environment(JNIEnv* env) {
    JavaThread *thread_from_jni_env = (JavaThread*)((intptr_t)env - in_bytes(jni_environment_offset()));
    // Only return NULL if thread is off the thread list; starting to
    // exit should not return NULL.
    if (thread_from_jni_env->is_terminated()) {
       thread_from_jni_env->block_if_vm_exited();
       return NULL;
    } else {
       return thread_from_jni_env;
    }
  }

  // JNI critical regions. These can nest.
  bool in_critical()    { return _jni_active_critical > 0; }
  bool in_last_critical()  { return _jni_active_critical == 1; }
  void enter_critical() { assert(Thread::current() == this ||
                                 Thread::current()->is_VM_thread() && SafepointSynchronize::is_synchronizing(),
                                 "this must be current thread or synchronizing");
                          _jni_active_critical++; }
  void exit_critical()  { assert(Thread::current() == this,
                                 "this must be current thread");
                          _jni_active_critical--;
                          assert(_jni_active_critical >= 0,
                                 "JNI critical nesting problem?"); }

  // For deadlock detection
  int depth_first_number() { return _depth_first_number; }
  void set_depth_first_number(int dfn) { _depth_first_number = dfn; }

 private:
  void set_monitor_chunks(MonitorChunk* monitor_chunks) { _monitor_chunks = monitor_chunks; }

 public:
  MonitorChunk* monitor_chunks() const           { return _monitor_chunks; }
  void add_monitor_chunk(MonitorChunk* chunk);
  void remove_monitor_chunk(MonitorChunk* chunk);
  bool in_deopt_handler() const                  { return _in_deopt_handler > 0; }
  void inc_in_deopt_handler()                    { _in_deopt_handler++; }
  void dec_in_deopt_handler()                    {
    assert(_in_deopt_handler > 0, "mismatched deopt nesting");
    if (_in_deopt_handler > 0) { // robustness
      _in_deopt_handler--;
    }
  }

 private:
  void set_entry_point(ThreadFunction entry_point) { _entry_point = entry_point; }

 public:

  // Frame iteration; calls the function f for all frames on the stack
  void frames_do(void f(frame*, const RegisterMap*));

  // Memory operations
  void oops_do(OopClosure* f, CLDToOopClosure* cld_f, CodeBlobClosure* cf);

  // Sweeper operations
  void nmethods_do(CodeBlobClosure* cf);

  // RedefineClasses Support
  void metadata_do(void f(Metadata*));

  // Memory management operations
  void gc_epilogue();
  void gc_prologue();

  // Misc. operations
  char* name() const { return (char*)get_thread_name(); }
  void print_on(outputStream* st) const;
  void print() const { print_on(tty); }
  void print_value();
  void print_thread_state_on(outputStream* ) const      PRODUCT_RETURN;
  void print_thread_state() const                       PRODUCT_RETURN;
  void print_on_error(outputStream* st, char* buf, int buflen) const;
  void verify();
  const char* get_thread_name() const;
private:
  // factor out low-level mechanics for use in both normal and error cases
  const char* get_thread_name_string(char* buf = NULL, int buflen = 0) const;
public:
  const char* get_threadgroup_name() const;
  const char* get_parent_name() const;

  // Accessing frames
  frame last_frame() {
    _anchor.make_walkable(this);
    return pd_last_frame();
  }
  javaVFrame* last_java_vframe(RegisterMap* reg_map);

  // Returns method at 'depth' java or native frames down the stack
  // Used for security checks
  Klass* security_get_caller_class(int depth);

  // Print stack trace in external format
  void print_stack_on(outputStream* st);
  void print_stack() { print_stack_on(tty); }

  // Print stack traces in various internal formats
  void trace_stack()                             PRODUCT_RETURN;
  void trace_stack_from(vframe* start_vf)        PRODUCT_RETURN;
  void trace_frames()                            PRODUCT_RETURN;
  void trace_oops()                              PRODUCT_RETURN;

  // Print an annotated view of the stack frames
  void print_frame_layout(int depth = 0, bool validate_only = false) NOT_DEBUG_RETURN;
  void validate_frame_layout() {
    print_frame_layout(0, true);
  }

  // Returns the number of stack frames on the stack
  int depth() const;

  // Function for testing deoptimization
  void deoptimize();
  void make_zombies();

  void deoptimized_wrt_marked_nmethods();

  // Profiling operation (see fprofile.cpp)
 public:
   bool profile_last_Java_frame(frame* fr);

 private:
   ThreadProfiler* _thread_profiler;
 private:
   friend class FlatProfiler;                    // uses both [gs]et_thread_profiler.
   friend class FlatProfilerTask;                // uses get_thread_profiler.
   friend class ThreadProfilerMark;              // uses get_thread_profiler.
   ThreadProfiler* get_thread_profiler()         { return _thread_profiler; }
   ThreadProfiler* set_thread_profiler(ThreadProfiler* tp) {
     ThreadProfiler* result = _thread_profiler;
     _thread_profiler = tp;
     return result;
   }

 // NMT (Native memory tracking) support.
 // This flag helps NMT to determine if this JavaThread will be blocked
 // at safepoint. If not, ThreadCritical is needed for writing memory records.
 // JavaThread is only safepoint visible when it is in Threads' thread list,
 // it is not visible until it is added to the list and becomes invisible
 // once it is removed from the list.
 public:
  bool is_safepoint_visible() const { return _safepoint_visible; }
  void set_safepoint_visible(bool visible) { _safepoint_visible = visible; }
 private:
  bool _safepoint_visible;

  // Static operations
 public:
  // Returns the running thread as a JavaThread
  static inline JavaThread* current();

  // Returns the active Java thread.  Do not use this if you know you are calling
  // from a JavaThread, as it's slower than JavaThread::current.  If called from
  // the VMThread, it also returns the JavaThread that instigated the VMThread's
  // operation.  You may not want that either.
  static JavaThread* active();

  inline CompilerThread* as_CompilerThread();

 public:
  virtual void run();
  void thread_main_inner();

 private:
  // PRIVILEGED STACK
  PrivilegedElement*  _privileged_stack_top;
  GrowableArray<oop>* _array_for_gc;
 public:

  // Returns the privileged_stack information.
  PrivilegedElement* privileged_stack_top() const       { return _privileged_stack_top; }
  void set_privileged_stack_top(PrivilegedElement *e)   { _privileged_stack_top = e; }
  void register_array_for_gc(GrowableArray<oop>* array) { _array_for_gc = array; }

 public:
  // Thread local information maintained by JVMTI.
  void set_jvmti_thread_state(JvmtiThreadState *value)                           { _jvmti_thread_state = value; }
  // A JvmtiThreadState is lazily allocated. This jvmti_thread_state()
  // getter is used to get this JavaThread's JvmtiThreadState if it has
  // one which means NULL can be returned. JvmtiThreadState::state_for()
  // is used to get the specified JavaThread's JvmtiThreadState if it has
  // one or it allocates a new JvmtiThreadState for the JavaThread and
  // returns it. JvmtiThreadState::state_for() will return NULL only if
  // the specified JavaThread is exiting.
  JvmtiThreadState *jvmti_thread_state() const                                   { return _jvmti_thread_state; }
  static ByteSize jvmti_thread_state_offset()                                    { return byte_offset_of(JavaThread, _jvmti_thread_state); }
  void set_jvmti_get_loaded_classes_closure(JvmtiGetLoadedClassesClosure* value) { _jvmti_get_loaded_classes_closure = value; }
  JvmtiGetLoadedClassesClosure* get_jvmti_get_loaded_classes_closure() const     { return _jvmti_get_loaded_classes_closure; }

  // JVMTI PopFrame support
  // Setting and clearing popframe_condition
  // All of these enumerated values are bits. popframe_pending
  // indicates that a PopFrame() has been requested and not yet been
  // completed. popframe_processing indicates that that PopFrame() is in
  // the process of being completed. popframe_force_deopt_reexecution_bit
  // indicates that special handling is required when returning to a
  // deoptimized caller.
  enum PopCondition {
    popframe_inactive                      = 0x00,
    popframe_pending_bit                   = 0x01,
    popframe_processing_bit                = 0x02,
    popframe_force_deopt_reexecution_bit   = 0x04
  };
  PopCondition popframe_condition()                   { return (PopCondition) _popframe_condition; }
  void set_popframe_condition(PopCondition c)         { _popframe_condition = c; }
  void set_popframe_condition_bit(PopCondition c)     { _popframe_condition |= c; }
  void clear_popframe_condition()                     { _popframe_condition = popframe_inactive; }
  static ByteSize popframe_condition_offset()         { return byte_offset_of(JavaThread, _popframe_condition); }
  bool has_pending_popframe()                         { return (popframe_condition() & popframe_pending_bit) != 0; }
  bool popframe_forcing_deopt_reexecution()           { return (popframe_condition() & popframe_force_deopt_reexecution_bit) != 0; }
  void clear_popframe_forcing_deopt_reexecution()     { _popframe_condition &= ~popframe_force_deopt_reexecution_bit; }
#ifdef CC_INTERP
  bool pop_frame_pending(void)                        { return ((_popframe_condition & popframe_pending_bit) != 0); }
  void clr_pop_frame_pending(void)                    { _popframe_condition = popframe_inactive; }
  bool pop_frame_in_process(void)                     { return ((_popframe_condition & popframe_processing_bit) != 0); }
  void set_pop_frame_in_process(void)                 { _popframe_condition |= popframe_processing_bit; }
  void clr_pop_frame_in_process(void)                 { _popframe_condition &= ~popframe_processing_bit; }
#endif

 private:
  // Saved incoming arguments to popped frame.
  // Used only when popped interpreted frame returns to deoptimized frame.
  void*    _popframe_preserved_args;
  int      _popframe_preserved_args_size;

 public:
  void  popframe_preserve_args(ByteSize size_in_bytes, void* start);
  void* popframe_preserved_args();
  ByteSize popframe_preserved_args_size();
  WordSize popframe_preserved_args_size_in_words();
  void  popframe_free_preserved_args();


 private:
  JvmtiThreadState *_jvmti_thread_state;
  JvmtiGetLoadedClassesClosure* _jvmti_get_loaded_classes_closure;

  // Used by the interpreter in fullspeed mode for frame pop, method
  // entry, method exit and single stepping support. This field is
  // only set to non-zero by the VM_EnterInterpOnlyMode VM operation.
  // It can be set to zero asynchronously (i.e., without a VM operation
  // or a lock) so we have to be very careful.
  int               _interp_only_mode;

 public:
  // used by the interpreter for fullspeed debugging support (see above)
  static ByteSize interp_only_mode_offset() { return byte_offset_of(JavaThread, _interp_only_mode); }
  bool is_interp_only_mode()                { return (_interp_only_mode != 0); }
  int get_interp_only_mode()                { return _interp_only_mode; }
  void increment_interp_only_mode()         { ++_interp_only_mode; }
  void decrement_interp_only_mode()         { --_interp_only_mode; }

  // support for cached flag that indicates whether exceptions need to be posted for this thread
  // if this is false, we can avoid deoptimizing when events are thrown
  // this gets set to reflect whether jvmtiExport::post_exception_throw would actually do anything
 private:
  int    _should_post_on_exceptions_flag;

 public:
  int   should_post_on_exceptions_flag()  { return _should_post_on_exceptions_flag; }
  void  set_should_post_on_exceptions_flag(int val)  { _should_post_on_exceptions_flag = val; }

 private:
  ThreadStatistics *_thread_stat;

 public:
  ThreadStatistics* get_thread_stat() const    { return _thread_stat; }

  // Return a blocker object for which this thread is blocked parking.
  oop current_park_blocker();

 private:
  static size_t _stack_size_at_create;

 public:
  static inline size_t stack_size_at_create(void) {
    return _stack_size_at_create;
  }
  static inline void set_stack_size_at_create(size_t value) {
    _stack_size_at_create = value;
  }

#if INCLUDE_ALL_GCS
  // SATB marking queue support
  ObjPtrQueue& satb_mark_queue() { return _satb_mark_queue; }
  static SATBMarkQueueSet& satb_mark_queue_set() {
    return _satb_mark_queue_set;
  }

  // Dirty card queue support
  DirtyCardQueue& dirty_card_queue() { return _dirty_card_queue; }
  static DirtyCardQueueSet& dirty_card_queue_set() {
    return _dirty_card_queue_set;
  }
#endif // INCLUDE_ALL_GCS

  // This method initializes the SATB and dirty card queues before a
  // JavaThread is added to the Java thread list. Right now, we don't
  // have to do anything to the dirty card queue (it should have been
  // activated when the thread was created), but we have to activate
  // the SATB queue if the thread is created while a marking cycle is
  // in progress. The activation / de-activation of the SATB queues at
  // the beginning / end of a marking cycle is done during safepoints
  // so we have to make sure this method is called outside one to be
  // able to safely read the active field of the SATB queue set. Right
  // now, it is called just before the thread is added to the Java
  // thread list in the Threads::add() method. That method is holding
  // the Threads_lock which ensures we are outside a safepoint. We
  // cannot do the obvious and set the active field of the SATB queue
  // when the thread is created given that, in some cases, safepoints
  // might happen between the JavaThread constructor being called and the
  // thread being added to the Java thread list (an example of this is
  // when the structure for the DestroyJavaVM thread is created).
#if INCLUDE_ALL_GCS
  void initialize_queues();
#else  // INCLUDE_ALL_GCS
  void initialize_queues() { }
#endif // INCLUDE_ALL_GCS

  // Machine dependent stuff
#ifdef TARGET_OS_ARCH_linux_x86
# include "thread_linux_x86.hpp"
#endif
#ifdef TARGET_OS_ARCH_linux_sparc
# include "thread_linux_sparc.hpp"
#endif
#ifdef TARGET_OS_ARCH_linux_zero
# include "thread_linux_zero.hpp"
#endif
#ifdef TARGET_OS_ARCH_solaris_x86
# include "thread_solaris_x86.hpp"
#endif
#ifdef TARGET_OS_ARCH_solaris_sparc
# include "thread_solaris_sparc.hpp"
#endif
#ifdef TARGET_OS_ARCH_windows_x86
# include "thread_windows_x86.hpp"
#endif
#ifdef TARGET_OS_ARCH_linux_arm
# include "thread_linux_arm.hpp"
#endif
#ifdef TARGET_OS_ARCH_linux_ppc
# include "thread_linux_ppc.hpp"
#endif
#ifdef TARGET_OS_ARCH_bsd_x86
# include "thread_bsd_x86.hpp"
#endif
#ifdef TARGET_OS_ARCH_bsd_zero
# include "thread_bsd_zero.hpp"
#endif


 public:
  void set_blocked_on_compilation(bool value) {
    _blocked_on_compilation = value;
  }

  bool blocked_on_compilation() {
    return _blocked_on_compilation;
  }
 protected:
  bool         _blocked_on_compilation;


  // JSR166 per-thread parker
private:
  Parker*    _parker;
public:
  Parker*     parker() { return _parker; }

  // Biased locking support
private:
  GrowableArray<MonitorInfo*>* _cached_monitor_info;
public:
  GrowableArray<MonitorInfo*>* cached_monitor_info() { return _cached_monitor_info; }
  void set_cached_monitor_info(GrowableArray<MonitorInfo*>* info) { _cached_monitor_info = info; }

  // clearing/querying jni attach status
  bool is_attaching_via_jni() const { return _jni_attach_state == _attaching_via_jni; }
  bool has_attached_via_jni() const { return is_attaching_via_jni() || _jni_attach_state == _attached_via_jni; }
  void set_done_attaching_via_jni() { _jni_attach_state = _attached_via_jni; OrderAccess::fence(); }
private:
  // This field is used to determine if a thread has claimed
  // a par_id: it is -1 if the thread has not claimed a par_id;
  // otherwise its value is the par_id that has been claimed.
  int _claimed_par_id;
public:
  int get_claimed_par_id() { return _claimed_par_id; }
  void set_claimed_par_id(int id) { _claimed_par_id = id;}
};

// Inline implementation of JavaThread::current
inline JavaThread* JavaThread::current() {
  Thread* thread = ThreadLocalStorage::thread();
  assert(thread != NULL && thread->is_Java_thread(), "just checking");
  return (JavaThread*)thread;
}

inline CompilerThread* JavaThread::as_CompilerThread() {
  assert(is_Compiler_thread(), "just checking");
  return (CompilerThread*)this;
}

inline bool JavaThread::stack_guard_zone_unused() {
  return _stack_guard_state == stack_guard_unused;
}

inline bool JavaThread::stack_yellow_zone_disabled() {
  return _stack_guard_state == stack_guard_yellow_disabled;
}

inline bool JavaThread::stack_yellow_zone_enabled() {
#ifdef ASSERT
  if (os::uses_stack_guard_pages()) {
    assert(_stack_guard_state != stack_guard_unused, "guard pages must be in use");
  }
#endif
    return _stack_guard_state == stack_guard_enabled;
}

inline size_t JavaThread::stack_available(address cur_sp) {
  // This code assumes java stacks grow down
  address low_addr; // Limit on the address for deepest stack depth
  if ( _stack_guard_state == stack_guard_unused) {
    low_addr =  stack_base() - stack_size();
  } else {
    low_addr = stack_yellow_zone_base();
  }
  return cur_sp > low_addr ? cur_sp - low_addr : 0;
}

// A thread used for Compilation.
class CompilerThread : public JavaThread {
  friend class VMStructs;
 private:
  CompilerCounters* _counters;

  ciEnv*        _env;
  CompileLog*   _log;
  CompileTask*  _task;
  CompileQueue* _queue;
  BufferBlob*   _buffer_blob;

  nmethod*      _scanned_nmethod;  // nmethod being scanned by the sweeper

 public:

  static CompilerThread* current();

  CompilerThread(CompileQueue* queue, CompilerCounters* counters);

  bool is_Compiler_thread() const                { return true; }
  // Hide this compiler thread from external view.
  bool is_hidden_from_external_view() const      { return true; }

  CompileQueue* queue()                          { return _queue; }
  CompilerCounters* counters()                   { return _counters; }

  // Get/set the thread's compilation environment.
  ciEnv*        env()                            { return _env; }
  void          set_env(ciEnv* env)              { _env = env; }

  BufferBlob*   get_buffer_blob()                { return _buffer_blob; }
  void          set_buffer_blob(BufferBlob* b)   { _buffer_blob = b; };

  // Get/set the thread's logging information
  CompileLog*   log()                            { return _log; }
  void          init_log(CompileLog* log) {
    // Set once, for good.
    assert(_log == NULL, "set only once");
    _log = log;
  }

  // GC support
  // Apply "f->do_oop" to all root oops in "this".
  // Apply "cf->do_code_blob" (if !NULL) to all code blobs active in frames
  void oops_do(OopClosure* f, CLDToOopClosure* cld_f, CodeBlobClosure* cf);

#ifndef PRODUCT
private:
  IdealGraphPrinter *_ideal_graph_printer;
public:
  IdealGraphPrinter *ideal_graph_printer()                       { return _ideal_graph_printer; }
  void set_ideal_graph_printer(IdealGraphPrinter *n)             { _ideal_graph_printer = n; }
#endif

  // Get/set the thread's current task
  CompileTask*  task()                           { return _task; }
  void          set_task(CompileTask* task)      { _task = task; }

  // Track the nmethod currently being scanned by the sweeper
  void          set_scanned_nmethod(nmethod* nm) {
    assert(_scanned_nmethod == NULL || nm == NULL, "should reset to NULL before writing a new value");
    _scanned_nmethod = nm;
  }
};

inline CompilerThread* CompilerThread::current() {
  return JavaThread::current()->as_CompilerThread();
}


// The active thread queue. It also keeps track of the current used
// thread priorities.
class Threads: AllStatic {
  friend class VMStructs;
 private:
  static JavaThread* _thread_list;
  static int         _number_of_threads;
  static int         _number_of_non_daemon_threads;
  static int         _return_code;
#ifdef ASSERT
  static bool        _vm_complete;
#endif

 public:
  // Thread management
  // force_daemon is a concession to JNI, where we may need to add a
  // thread to the thread list before allocating its thread object
  static void add(JavaThread* p, bool force_daemon = false);
  static void remove(JavaThread* p);
  static bool includes(JavaThread* p);
  static JavaThread* first()                     { return _thread_list; }
  static void threads_do(ThreadClosure* tc);

  // Initializes the vm and creates the vm thread
  static jint create_vm(JavaVMInitArgs* args, bool* canTryAgain);
  static void convert_vm_init_libraries_to_agents();
  static void create_vm_init_libraries();
  static void create_vm_init_agents();
  static void shutdown_vm_agents();
  static bool destroy_vm();
  // Supported VM versions via JNI
  // Includes JNI_VERSION_1_1
  static jboolean is_supported_jni_version_including_1_1(jint version);
  // Does not include JNI_VERSION_1_1
  static jboolean is_supported_jni_version(jint version);

  // Garbage collection
  static void follow_other_roots(void f(oop*));

  // Apply "f->do_oop" to all root oops in all threads.
  // This version may only be called by sequential code.
  static void oops_do(OopClosure* f, CLDToOopClosure* cld_f, CodeBlobClosure* cf);
  // This version may be called by sequential or parallel code.
  static void possibly_parallel_oops_do(OopClosure* f, CLDToOopClosure* cld_f, CodeBlobClosure* cf);
  // This creates a list of GCTasks, one per thread.
  static void create_thread_roots_tasks(GCTaskQueue* q);
  // This creates a list of GCTasks, one per thread, for marking objects.
  static void create_thread_roots_marking_tasks(GCTaskQueue* q);

  // Apply "f->do_oop" to roots in all threads that
  // are part of compiled frames
  static void compiled_frame_oops_do(OopClosure* f, CodeBlobClosure* cf);

  static void convert_hcode_pointers();
  static void restore_hcode_pointers();

  // Sweeper
  static void nmethods_do(CodeBlobClosure* cf);

  // RedefineClasses support
  static void metadata_do(void f(Metadata*));

  static void gc_epilogue();
  static void gc_prologue();
#ifdef ASSERT
  static bool is_vm_complete() { return _vm_complete; }
#endif

  // Verification
  static void verify();
  static void print_on(outputStream* st, bool print_stacks, bool internal_format, bool print_concurrent_locks);
  static void print(bool print_stacks, bool internal_format) {
    // this function is only used by debug.cpp
    print_on(tty, print_stacks, internal_format, false /* no concurrent lock printed */);
  }
  static void print_on_error(outputStream* st, Thread* current, char* buf, int buflen);

  // Get Java threads that are waiting to enter a monitor. If doLock
  // is true, then Threads_lock is grabbed as needed. Otherwise, the
  // VM needs to be at a safepoint.
  static GrowableArray<JavaThread*>* get_pending_threads(int count,
    address monitor, bool doLock);

  // Get owning Java thread from the monitor's owner field. If doLock
  // is true, then Threads_lock is grabbed as needed. Otherwise, the
  // VM needs to be at a safepoint.
  static JavaThread *owning_thread_from_monitor_owner(address owner,
    bool doLock);

  // Number of threads on the active threads list
  static int number_of_threads()                 { return _number_of_threads; }
  // Number of non-daemon threads on the active threads list
  static int number_of_non_daemon_threads()      { return _number_of_non_daemon_threads; }

  // Deoptimizes all frames tied to marked nmethods
  static void deoptimized_wrt_marked_nmethods();

};


// Thread iterator
class ThreadClosure: public StackObj {
 public:
  virtual void do_thread(Thread* thread) = 0;
};

class SignalHandlerMark: public StackObj {
private:
  Thread* _thread;
public:
  SignalHandlerMark(Thread* t) {
    _thread = t;
    if (_thread) _thread->enter_signal_handler();
  }
  ~SignalHandlerMark() {
    if (_thread) _thread->leave_signal_handler();
    _thread = NULL;
  }
};


#endif // SHARE_VM_RUNTIME_THREAD_HPP

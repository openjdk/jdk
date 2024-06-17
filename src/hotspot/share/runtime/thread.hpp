/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, Azul Systems, Inc. All rights reserved.
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

#ifndef SHARE_RUNTIME_THREAD_HPP
#define SHARE_RUNTIME_THREAD_HPP

#include "jni.h"
#include "gc/shared/gcThreadLocalData.hpp"
#include "gc/shared/threadLocalAllocBuffer.hpp"
#include "memory/allocation.hpp"
#include "runtime/atomic.hpp"
#include "runtime/globals.hpp"
#include "runtime/os.hpp"
#include "runtime/threadHeapSampler.hpp"
#include "runtime/threadLocalStorage.hpp"
#include "runtime/threadStatisticalInfo.hpp"
#include "runtime/unhandledOops.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#if INCLUDE_JFR
#include "jfr/support/jfrThreadExtension.hpp"
#endif

class CompilerThread;
class HandleArea;
class HandleMark;
class ICRefillVerifier;
class JvmtiRawMonitor;
class NMethodClosure;
class Metadata;
class OopClosure;
class OSThread;
class ParkEvent;
class ResourceArea;
class SafeThreadsListPtr;
class ThreadClosure;
class ThreadsList;
class ThreadsSMRSupport;
class VMErrorCallback;


DEBUG_ONLY(class ResourceMark;)

class WorkerThread;

class JavaThread;

// Class hierarchy
// - Thread
//   - JavaThread
//     - various subclasses eg CompilerThread, ServiceThread
//   - NonJavaThread
//     - NamedThread
//       - VMThread
//       - ConcurrentGCThread
//       - WorkerThread
//     - WatcherThread
//     - JfrThreadSampler
//     - LogAsyncWriter
//
// All Thread subclasses must be either JavaThread or NonJavaThread.
// This means !t->is_Java_thread() iff t is a NonJavaThread, or t is
// a partially constructed/destroyed Thread.

// Thread execution sequence and actions:
// All threads:
//  - thread_native_entry  // per-OS native entry point
//    - stack initialization
//    - other OS-level initialization (signal masks etc)
//    - handshake with creating thread (if not started suspended)
//    - this->call_run()  // common shared entry point
//      - shared common initialization
//      - this->pre_run()  // virtual per-thread-type initialization
//      - this->run()      // virtual per-thread-type "main" logic
//      - shared common tear-down
//      - this->post_run()  // virtual per-thread-type tear-down
//      - // 'this' no longer referenceable
//    - OS-level tear-down (minimal)
//    - final logging
//
// For JavaThread:
//   - this->run()  // virtual but not normally overridden
//     - this->thread_main_inner()  // extra call level to ensure correct stack calculations
//       - this->entry_point()  // set differently for each kind of JavaThread

class Thread: public ThreadShadow {
  friend class VMError;
  friend class VMErrorCallbackMark;
  friend class VMStructs;
  friend class JVMCIVMStructs;
 private:

#ifndef USE_LIBRARY_BASED_TLS_ONLY
  // Current thread is maintained as a thread-local variable
  static THREAD_LOCAL Thread* _thr_current;
#endif

  // On AArch64, the high order 32 bits are used by a "patching epoch" number
  // which reflects if this thread has executed the required fences, after
  // an nmethod gets disarmed. The low order 32 bits denote the disarmed value.
  uint64_t _nmethod_disarmed_guard_value;

 public:
  void set_nmethod_disarmed_guard_value(int value) {
    _nmethod_disarmed_guard_value = (uint64_t)(uint32_t)value;
  }

  static ByteSize nmethod_disarmed_guard_value_offset() {
    ByteSize offset = byte_offset_of(Thread, _nmethod_disarmed_guard_value);
    // At least on x86_64, nmethod entry barrier encodes disarmed value offset
    // in instruction as disp8 immed
    assert(in_bytes(offset) < 128, "Offset >= 128");
    return offset;
  }

 private:
  // Thread local data area available to the GC. The internal
  // structure and contents of this data area is GC-specific.
  // Only GC and GC barrier code should access this data area.
  GCThreadLocalData _gc_data;

 public:
  static ByteSize gc_data_offset() {
    return byte_offset_of(Thread, _gc_data);
  }

  template <typename T> T* gc_data() {
    STATIC_ASSERT(sizeof(T) <= sizeof(_gc_data));
    return reinterpret_cast<T*>(&_gc_data);
  }

  // Exception handling
  // (Note: _pending_exception and friends are in ThreadShadow)
  //oop       _pending_exception;                // pending exception for current thread
  // const char* _exception_file;                   // file information for exception (debugging only)
  // int         _exception_line;                   // line information for exception (debugging only)
 protected:

  DEBUG_ONLY(static Thread* _starting_thread;)

  // JavaThread lifecycle support:
  friend class SafeThreadsListPtr;  // for _threads_list_ptr, cmpxchg_threads_hazard_ptr(), {dec_,inc_,}nested_threads_hazard_ptr_cnt(), {g,s}et_threads_hazard_ptr(), inc_nested_handle_cnt(), tag_hazard_ptr() access
  friend class ScanHazardPtrGatherProtectedThreadsClosure;  // for cmpxchg_threads_hazard_ptr(), get_threads_hazard_ptr(), is_hazard_ptr_tagged() access
  friend class ScanHazardPtrGatherThreadsListClosure;  // for get_threads_hazard_ptr(), untag_hazard_ptr() access
  friend class ScanHazardPtrPrintMatchingThreadsClosure;  // for get_threads_hazard_ptr(), is_hazard_ptr_tagged() access
  friend class ThreadsSMRSupport;  // for _nested_threads_hazard_ptr_cnt, _threads_hazard_ptr, _threads_list_ptr access
  friend class ThreadsListHandleTest;  // for _nested_threads_hazard_ptr_cnt, _threads_hazard_ptr, _threads_list_ptr access
  friend class ValidateHazardPtrsClosure;  // for get_threads_hazard_ptr(), untag_hazard_ptr() access

  ThreadsList* volatile _threads_hazard_ptr;
  SafeThreadsListPtr*   _threads_list_ptr;
  ThreadsList*          cmpxchg_threads_hazard_ptr(ThreadsList* exchange_value, ThreadsList* compare_value);
  ThreadsList*          get_threads_hazard_ptr() const;
  void                  set_threads_hazard_ptr(ThreadsList* new_list);
  static bool           is_hazard_ptr_tagged(ThreadsList* list) {
    return (intptr_t(list) & intptr_t(1)) == intptr_t(1);
  }
  static ThreadsList*   tag_hazard_ptr(ThreadsList* list) {
    return (ThreadsList*)(intptr_t(list) | intptr_t(1));
  }
  static ThreadsList*   untag_hazard_ptr(ThreadsList* list) {
    return (ThreadsList*)(intptr_t(list) & ~intptr_t(1));
  }
  // This field is enabled via -XX:+EnableThreadSMRStatistics:
  uint _nested_threads_hazard_ptr_cnt;
  void dec_nested_threads_hazard_ptr_cnt() {
    assert(_nested_threads_hazard_ptr_cnt != 0, "mismatched {dec,inc}_nested_threads_hazard_ptr_cnt()");
    _nested_threads_hazard_ptr_cnt--;
  }
  void inc_nested_threads_hazard_ptr_cnt() {
    _nested_threads_hazard_ptr_cnt++;
  }
  uint nested_threads_hazard_ptr_cnt() {
    return _nested_threads_hazard_ptr_cnt;
  }

 public:
  // Is the target JavaThread protected by the calling Thread or by some other
  // mechanism?
  static bool is_JavaThread_protected(const JavaThread* target);
  // Is the target JavaThread protected by a ThreadsListHandle (TLH) associated
  // with the calling Thread?
  static bool is_JavaThread_protected_by_TLH(const JavaThread* target);

 private:
  DEBUG_ONLY(bool _suspendible_thread;)
  DEBUG_ONLY(bool _indirectly_suspendible_thread;)
  DEBUG_ONLY(bool _indirectly_safepoint_thread;)

 public:
#ifdef ASSERT
  void set_suspendible_thread()   { _suspendible_thread = true; }
  void clear_suspendible_thread() { _suspendible_thread = false; }
  bool is_suspendible_thread()    { return _suspendible_thread; }

  void set_indirectly_suspendible_thread()   { _indirectly_suspendible_thread = true; }
  void clear_indirectly_suspendible_thread() { _indirectly_suspendible_thread = false; }
  bool is_indirectly_suspendible_thread()    { return _indirectly_suspendible_thread; }

  void set_indirectly_safepoint_thread()   { _indirectly_safepoint_thread = true; }
  void clear_indirectly_safepoint_thread() { _indirectly_safepoint_thread = false; }
  bool is_indirectly_safepoint_thread()    { return _indirectly_safepoint_thread; }
#endif

 private:
  // Point to the last handle mark
  HandleMark* _last_handle_mark;

  // Claim value for parallel iteration over threads.
  uintx _threads_do_token;

  // Support for GlobalCounter
 private:
  volatile uintx _rcu_counter;
 public:
  volatile uintx* get_rcu_counter() {
    return &_rcu_counter;
  }

 public:
  void set_last_handle_mark(HandleMark* mark)   { _last_handle_mark = mark; }
  HandleMark* last_handle_mark() const          { return _last_handle_mark; }
 private:

#ifdef ASSERT
  ICRefillVerifier* _missed_ic_stub_refill_verifier;

 public:
  ICRefillVerifier* missed_ic_stub_refill_verifier() {
    return _missed_ic_stub_refill_verifier;
  }

  void set_missed_ic_stub_refill_verifier(ICRefillVerifier* verifier) {
    _missed_ic_stub_refill_verifier = verifier;
  }
#endif // ASSERT

 private:
  // Used by SkipGCALot class.
  NOT_PRODUCT(bool _skip_gcalot;)               // Should we elide gc-a-lot?

  friend class GCLocker;

 private:
  ThreadLocalAllocBuffer _tlab;                 // Thread-local eden
  jlong _allocated_bytes;                       // Cumulative number of bytes allocated on
                                                // the Java heap
  ThreadHeapSampler _heap_sampler;              // For use when sampling the memory.

  ThreadStatisticalInfo _statistical_info;      // Statistics about the thread

  JFR_ONLY(DEFINE_THREAD_LOCAL_FIELD_JFR;)      // Thread-local data for jfr

  JvmtiRawMonitor* _current_pending_raw_monitor; // JvmtiRawMonitor this thread
                                                 // is waiting to lock
 public:
  // Constructor
  Thread();
  virtual ~Thread() = 0;        // Thread is abstract.

  // Manage Thread::current()
  void initialize_thread_current();
  static void clear_thread_current(); // TLS cleanup needed before threads terminate

 protected:
  // To be implemented by children.
  virtual void run() = 0;
  virtual void pre_run() = 0;
  virtual void post_run() = 0;  // Note: Thread must not be deleted prior to calling this!

#ifdef ASSERT
  enum RunState {
    PRE_CALL_RUN,
    CALL_RUN,
    PRE_RUN,
    RUN,
    POST_RUN
    // POST_CALL_RUN - can't define this one as 'this' may be deleted when we want to set it
  };
  RunState _run_state;  // for lifecycle checks
#endif


 public:
  // invokes <ChildThreadClass>::run(), with common preparations and cleanups.
  void call_run();

  // Testers
  virtual bool is_VM_thread()       const            { return false; }
  virtual bool is_Java_thread()     const            { return false; }
  virtual bool is_Compiler_thread() const            { return false; }
  virtual bool is_service_thread() const             { return false; }
  virtual bool is_hidden_from_external_view() const  { return false; }
  virtual bool is_jvmti_agent_thread() const         { return false; }
  virtual bool is_Watcher_thread() const             { return false; }
  virtual bool is_ConcurrentGC_thread() const        { return false; }
  virtual bool is_Named_thread() const               { return false; }
  virtual bool is_Worker_thread() const              { return false; }
  virtual bool is_JfrSampler_thread() const          { return false; }
  virtual bool is_AttachListener_thread() const      { return false; }
  virtual bool is_monitor_deflation_thread() const   { return false; }

  // Convenience cast functions
  CompilerThread* as_Compiler_thread() const {
    assert(is_Compiler_thread(), "Must be compiler thread");
    return (CompilerThread*)this;
  }

  // Can this thread make Java upcalls
  virtual bool can_call_java() const                 { return false; }

  // Is this a JavaThread that is on the VM's current ThreadsList?
  // If so it must participate in the safepoint protocol.
  virtual bool is_active_Java_thread() const         { return false; }

  // All threads are given names. For singleton subclasses we can
  // just hard-wire the known name of the instance. JavaThreads and
  // NamedThreads support multiple named instances, and dynamic
  // changing of the name of an instance.
  virtual const char* name() const { return "Unknown thread"; }

  // A thread's type name is also made available for debugging
  // and logging.
  virtual const char* type_name() const { return "Thread"; }

  // Returns the current thread (ASSERTS if null)
  static inline Thread* current();
  // Returns the current thread, or null if not attached
  static inline Thread* current_or_null();
  // Returns the current thread, or null if not attached, and is
  // safe for use from signal-handlers
  static inline Thread* current_or_null_safe();

  // Common thread operations
#ifdef ASSERT
  static void check_for_dangling_thread_pointer(Thread *thread);
#endif
  static void set_priority(Thread* thread, ThreadPriority priority);
  static void start(Thread* thread);

  void set_native_thread_name(const char *name) {
    assert(Thread::current() == this, "set_native_thread_name can only be called on the current thread");
    os::set_native_thread_name(name);
  }

  // Support for Unhandled Oop detection
  // Add the field for both, fastdebug and debug, builds to keep
  // Thread's fields layout the same.
  // Note: CHECK_UNHANDLED_OOPS is defined only for fastdebug build.
#ifdef CHECK_UNHANDLED_OOPS
 private:
  UnhandledOops* _unhandled_oops;
#elif defined(ASSERT)
 private:
  void* _unhandled_oops;
#endif
#ifdef CHECK_UNHANDLED_OOPS
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
#endif // CHECK_UNHANDLED_OOPS

 public:
#ifndef PRODUCT
  bool skip_gcalot()           { return _skip_gcalot; }
  void set_skip_gcalot(bool v) { _skip_gcalot = v;    }
#endif

  // Resource area
  ResourceArea* resource_area() const            { return _resource_area; }
  void set_resource_area(ResourceArea* area)     { _resource_area = area; }

  OSThread* osthread() const                     { return _osthread;   }
  void set_osthread(OSThread* thread)            { _osthread = thread; }

  // Internal handle support
  HandleArea* handle_area() const                { return _handle_area; }
  void set_handle_area(HandleArea* area)         { _handle_area = area; }

  GrowableArray<Metadata*>* metadata_handles() const          { return _metadata_handles; }
  void set_metadata_handles(GrowableArray<Metadata*>* handles){ _metadata_handles = handles; }

  // Thread-Local Allocation Buffer (TLAB) support
  ThreadLocalAllocBuffer& tlab()                 { return _tlab; }
  void initialize_tlab();

  jlong allocated_bytes()               { return _allocated_bytes; }
  void set_allocated_bytes(jlong value) { _allocated_bytes = value; }
  void incr_allocated_bytes(jlong size) { _allocated_bytes += size; }
  inline jlong cooked_allocated_bytes();

  ThreadHeapSampler& heap_sampler()     { return _heap_sampler; }

  ThreadStatisticalInfo& statistical_info() { return _statistical_info; }

  JFR_ONLY(DEFINE_THREAD_LOCAL_ACCESSOR_JFR;)

  // For tracking the Jvmti raw monitor the thread is pending on.
  JvmtiRawMonitor* current_pending_raw_monitor() {
    return _current_pending_raw_monitor;
  }
  void set_current_pending_raw_monitor(JvmtiRawMonitor* monitor) {
    _current_pending_raw_monitor = monitor;
  }

  // GC support
  // Apply "f->do_oop" to all root oops in "this".
  //   Used by JavaThread::oops_do.
  // Apply "cf->do_nmethod" (if !nullptr) to all nmethods active in frames
  virtual void oops_do_no_frames(OopClosure* f, NMethodClosure* cf);
  virtual void oops_do_frames(OopClosure* f, NMethodClosure* cf) {}
  void oops_do(OopClosure* f, NMethodClosure* cf);

  // Handles the parallel case for claim_threads_do.
 private:
  bool claim_par_threads_do(uintx claim_token);
 public:
  // Requires that "claim_token" is that of the current iteration.
  // If "is_par" is false, sets the token of "this" to
  // "claim_token", and returns "true".  If "is_par" is true,
  // uses an atomic instruction to set the current thread's token to
  // "claim_token", if it is not already.  Returns "true" iff the
  // calling thread does the update, this indicates that the calling thread
  // has claimed the thread in the current iteration.
  bool claim_threads_do(bool is_par, uintx claim_token) {
    if (!is_par) {
      _threads_do_token = claim_token;
      return true;
    } else {
      return claim_par_threads_do(claim_token);
    }
  }

  uintx threads_do_token() const { return _threads_do_token; }

  // jvmtiRedefineClasses support
  void metadata_handles_do(void f(Metadata*));

 private:
  // Check if address is within the given range of this thread's
  // stack:  stack_base() > adr >/>= limit
  // The check is inclusive of limit if passed true, else exclusive.
  bool is_in_stack_range(address adr, address limit, bool inclusive) const {
    assert(stack_base() > limit && limit >= stack_end(), "limit is outside of stack");
    return stack_base() > adr && (inclusive ? adr >= limit : adr > limit);
  }

 public:
  // Check if address is within the given range of this thread's
  // stack:  stack_base() > adr >= limit
  bool is_in_stack_range_incl(address adr, address limit) const {
    return is_in_stack_range(adr, limit, true);
  }

  // Check if address is within the given range of this thread's
  // stack:  stack_base() > adr > limit
  bool is_in_stack_range_excl(address adr, address limit) const {
    return is_in_stack_range(adr, limit, false);
  }

  // Check if address is in the stack mapped to this thread. Used mainly in
  // error reporting (so has to include guard zone) and frame printing.
  // Expects _stack_base to be initialized - checked with assert.
  bool is_in_full_stack_checked(address adr) const {
    return is_in_stack_range_incl(adr, stack_end());
  }

  // Like is_in_full_stack_checked but without the assertions as this
  // may be called in a thread before _stack_base is initialized.
  bool is_in_full_stack(address adr) const {
    address stack_end = _stack_base - _stack_size;
    return _stack_base > adr && adr >= stack_end;
  }

  // Check if address is in the live stack of this thread (not just for locks).
  // Warning: can only be called by the current thread on itself.
  bool is_in_live_stack(address adr) const {
    assert(Thread::current() == this, "is_in_live_stack can only be called from current thread");
    return is_in_stack_range_incl(adr, os::current_stack_pointer());
  }

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
  int              _lgrp_id;

 public:
  // Stack overflow support
  address stack_base() const           { assert(_stack_base != nullptr,"Sanity check"); return _stack_base; }
  void    set_stack_base(address base) { _stack_base = base; }
  size_t  stack_size() const           { return _stack_size; }
  void    set_stack_size(size_t size)  { _stack_size = size; }
  address stack_end()  const           { return stack_base() - stack_size(); }
  void    record_stack_base_and_size();
  void    register_thread_stack_with_NMT();
  void    unregister_thread_stack_with_NMT();

  int     lgrp_id() const        { return _lgrp_id; }
  void    set_lgrp_id(int value) { _lgrp_id = value; }

  // Printing
  void print_on(outputStream* st, bool print_extended_info) const;
  virtual void print_on(outputStream* st) const { print_on(st, false); }
  void print() const;
  virtual void print_on_error(outputStream* st, char* buf, int buflen) const;
  // Basic, non-virtual, printing support that is simple and always safe.
  void print_value_on(outputStream* st) const;

  // Debug-only code
#ifdef ASSERT
 private:
  // Deadlock detection support for Mutex locks. List of locks own by thread.
  Mutex* _owned_locks;
  // Mutex::set_owner_implementation is the only place where _owned_locks is modified,
  // thus the friendship
  friend class Mutex;
  friend class Monitor;

 public:
  void print_owned_locks_on(outputStream* st) const;
  void print_owned_locks() const                 { print_owned_locks_on(tty);    }
  Mutex* owned_locks() const                     { return _owned_locks;          }
  bool owns_locks() const                        { return owned_locks() != nullptr; }

  // Deadlock detection
  ResourceMark* current_resource_mark()          { return _current_resource_mark; }
  void set_current_resource_mark(ResourceMark* rm) { _current_resource_mark = rm; }
#endif // ASSERT

 private:
  volatile int _jvmti_env_iteration_count;

 public:
  void entering_jvmti_env_iteration()            { ++_jvmti_env_iteration_count; }
  void leaving_jvmti_env_iteration()             { --_jvmti_env_iteration_count; }
  bool is_inside_jvmti_env_iteration()           { return _jvmti_env_iteration_count > 0; }

  // Code generation
  static ByteSize exception_file_offset()        { return byte_offset_of(Thread, _exception_file); }
  static ByteSize exception_line_offset()        { return byte_offset_of(Thread, _exception_line); }

  static ByteSize stack_base_offset()            { return byte_offset_of(Thread, _stack_base); }
  static ByteSize stack_size_offset()            { return byte_offset_of(Thread, _stack_size); }

  static ByteSize tlab_start_offset()            { return byte_offset_of(Thread, _tlab) + ThreadLocalAllocBuffer::start_offset(); }
  static ByteSize tlab_end_offset()              { return byte_offset_of(Thread, _tlab) + ThreadLocalAllocBuffer::end_offset(); }
  static ByteSize tlab_top_offset()              { return byte_offset_of(Thread, _tlab) + ThreadLocalAllocBuffer::top_offset(); }
  static ByteSize tlab_pf_top_offset()           { return byte_offset_of(Thread, _tlab) + ThreadLocalAllocBuffer::pf_top_offset(); }

  JFR_ONLY(DEFINE_THREAD_LOCAL_OFFSET_JFR;)

 public:
  ParkEvent * volatile _ParkEvent;            // for Object monitors, JVMTI raw monitors,
                                              // and ObjectSynchronizer::read_stable_mark

  // Termination indicator used by the signal handler.
  // _ParkEvent is just a convenient field we can null out after setting the JavaThread termination state
  // (which can't itself be read from the signal handler if a signal hits during the Thread destructor).
  bool has_terminated()                       { return Atomic::load(&_ParkEvent) == nullptr; };

  jint _hashStateW;                           // Marsaglia Shift-XOR thread-local RNG
  jint _hashStateX;                           // thread-specific hashCode generator state
  jint _hashStateY;
  jint _hashStateZ;

  // Low-level leaf-lock primitives used to implement synchronization.
  // Not for general synchronization use.
  static void SpinAcquire(volatile int * Lock, const char * Name);
  static void SpinRelease(volatile int * Lock);

#if defined(__APPLE__) && defined(AARCH64)
 private:
  DEBUG_ONLY(bool _wx_init);
  WXMode _wx_state;
 public:
  void init_wx();
  WXMode enable_wx(WXMode new_state);

  void assert_wx_state(WXMode expected) {
    assert(_wx_state == expected, "wrong state");
  }
#endif // __APPLE__ && AARCH64

 private:
  bool _in_asgct = false;
 public:
  bool in_asgct() const { return _in_asgct; }
  void set_in_asgct(bool value) { _in_asgct = value; }
  static bool current_in_asgct() {
    Thread *cur = Thread::current_or_null_safe();
    return cur != nullptr && cur->in_asgct();
  }

 private:
  VMErrorCallback* _vm_error_callbacks;
};

class ThreadInAsgct {
 private:
  Thread* _thread;
  bool _saved_in_asgct;
 public:
  ThreadInAsgct(Thread* thread) : _thread(thread) {
    assert(thread != nullptr, "invariant");
    // Allow AsyncGetCallTrace to be reentrant - save the previous state.
    _saved_in_asgct = thread->in_asgct();
    thread->set_in_asgct(true);
  }
  ~ThreadInAsgct() {
    assert(_thread->in_asgct(), "invariant");
    _thread->set_in_asgct(_saved_in_asgct);
  }
};

// Inline implementation of Thread::current()
inline Thread* Thread::current() {
  Thread* current = current_or_null();
  assert(current != nullptr, "Thread::current() called on detached thread");
  return current;
}

inline Thread* Thread::current_or_null() {
#ifndef USE_LIBRARY_BASED_TLS_ONLY
  return _thr_current;
#else
  if (ThreadLocalStorage::is_initialized()) {
    return ThreadLocalStorage::thread();
  }
  return nullptr;
#endif
}

inline Thread* Thread::current_or_null_safe() {
  if (ThreadLocalStorage::is_initialized()) {
    return ThreadLocalStorage::thread();
  }
  return nullptr;
}

#endif // SHARE_RUNTIME_THREAD_HPP

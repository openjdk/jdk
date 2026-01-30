/*
 * Copyright (c) 2003, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/javaClasses.inline.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmClasses.hpp"
#include "classfile/vmSymbols.hpp"
#include "gc/shared/oopStorageSet.hpp"
#include "memory/heapInspection.hpp"
#include "memory/oopFactory.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "nmt/memTag.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/klass.inline.hpp"
#include "oops/method.inline.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/oopHandle.inline.hpp"
#include "prims/jvmtiRawMonitor.hpp"
#include "prims/jvmtiThreadState.hpp"
#include "runtime/atomicAccess.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/init.hpp"
#include "runtime/javaThread.inline.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "runtime/objectMonitor.inline.hpp"
#include "runtime/synchronizer.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/threads.hpp"
#include "runtime/threadSMR.inline.hpp"
#include "runtime/vframe.inline.hpp"
#include "runtime/vmOperations.hpp"
#include "runtime/vmThread.hpp"
#include "services/threadService.hpp"

// TODO: we need to define a naming convention for perf counters
// to distinguish counters for:
//   - standard JSR174 use
//   - Hotspot extension (public and committed)
//   - Hotspot extension (private/internal and uncommitted)

// Default is disabled.
bool ThreadService::_thread_monitoring_contention_enabled = false;
bool ThreadService::_thread_cpu_time_enabled = false;
bool ThreadService::_thread_allocated_memory_enabled = false;

PerfCounter*  ThreadService::_total_threads_count = nullptr;
PerfVariable* ThreadService::_live_threads_count = nullptr;
PerfVariable* ThreadService::_peak_threads_count = nullptr;
PerfVariable* ThreadService::_daemon_threads_count = nullptr;
volatile int ThreadService::_atomic_threads_count = 0;
volatile int ThreadService::_atomic_daemon_threads_count = 0;

volatile jlong ThreadService::_exited_allocated_bytes = 0;

ThreadDumpResult* ThreadService::_threaddump_list = nullptr;

static const int INITIAL_ARRAY_SIZE = 10;

// OopStorage for thread stack sampling
static OopStorage* _thread_service_storage = nullptr;

void ThreadService::init() {
  EXCEPTION_MARK;

  // These counters are for java.lang.management API support.
  // They are created even if -XX:-UsePerfData is set and in
  // that case, they will be allocated on C heap.

  _total_threads_count =
                PerfDataManager::create_counter(JAVA_THREADS, "started",
                                                PerfData::U_Events, CHECK);

  _live_threads_count =
                PerfDataManager::create_variable(JAVA_THREADS, "live",
                                                 PerfData::U_None, CHECK);

  _peak_threads_count =
                PerfDataManager::create_variable(JAVA_THREADS, "livePeak",
                                                 PerfData::U_None, CHECK);

  _daemon_threads_count =
                PerfDataManager::create_variable(JAVA_THREADS, "daemon",
                                                 PerfData::U_None, CHECK);

  if (os::is_thread_cpu_time_supported()) {
    _thread_cpu_time_enabled = true;
  }

  _thread_allocated_memory_enabled = true; // Always on, so enable it

  // Initialize OopStorage for thread stack sampling walking
  _thread_service_storage = OopStorageSet::create_strong("ThreadService OopStorage",
                                                         mtServiceability);
}

void ThreadService::reset_peak_thread_count() {
  // Acquire the lock to update the peak thread count
  // to synchronize with thread addition and removal.
  MutexLocker mu(Threads_lock);
  _peak_threads_count->set_value(get_live_thread_count());
}

static bool is_hidden_thread(JavaThread *thread) {
  // hide VM internal or JVMTI agent threads
  return thread->is_hidden_from_external_view() || thread->is_jvmti_agent_thread();
}

void ThreadService::add_thread(JavaThread* thread, bool daemon) {
  assert(Threads_lock->owned_by_self(), "must have threads lock");

  // Do not count hidden threads
  if (is_hidden_thread(thread)) {
    return;
  }

  _total_threads_count->inc();
  _live_threads_count->inc();
  AtomicAccess::inc(&_atomic_threads_count);
  int count = _atomic_threads_count;

  if (count > _peak_threads_count->get_value()) {
    _peak_threads_count->set_value(count);
  }

  if (daemon) {
    _daemon_threads_count->inc();
    AtomicAccess::inc(&_atomic_daemon_threads_count);
  }
}

void ThreadService::decrement_thread_counts(JavaThread* jt, bool daemon) {
  AtomicAccess::dec(&_atomic_threads_count);

  if (daemon) {
    AtomicAccess::dec(&_atomic_daemon_threads_count);
  }
}

void ThreadService::remove_thread(JavaThread* thread, bool daemon) {
  assert(Threads_lock->owned_by_self(), "must have threads lock");

  // Include hidden thread allcations in exited_allocated_bytes
  ThreadService::incr_exited_allocated_bytes(thread->cooked_allocated_bytes());

  // Do not count hidden threads
  if (is_hidden_thread(thread)) {
    return;
  }

  assert(!thread->is_terminated(), "must not be terminated");
  if (!thread->is_exiting()) {
    // We did not get here via JavaThread::exit() so current_thread_exiting()
    // was not called, e.g., JavaThread::cleanup_failed_attach_current_thread().
    decrement_thread_counts(thread, daemon);
  }

  int daemon_count = _atomic_daemon_threads_count;
  int count = _atomic_threads_count;

  // Counts are incremented at the same time, but atomic counts are
  // decremented earlier than perf counts.
  assert(_live_threads_count->get_value() > count,
    "thread count mismatch %d : %d",
    (int)_live_threads_count->get_value(), count);

  _live_threads_count->dec(1);
  if (daemon) {
    assert(_daemon_threads_count->get_value() > daemon_count,
      "thread count mismatch %d : %d",
      (int)_daemon_threads_count->get_value(), daemon_count);

    _daemon_threads_count->dec(1);
  }

  // Counts are incremented at the same time, but atomic counts are
  // decremented earlier than perf counts.
  assert(_daemon_threads_count->get_value() >= daemon_count,
    "thread count mismatch %d : %d",
    (int)_daemon_threads_count->get_value(), daemon_count);
  assert(_live_threads_count->get_value() >= count,
    "thread count mismatch %d : %d",
    (int)_live_threads_count->get_value(), count);
  assert(_live_threads_count->get_value() > 0 ||
    (_live_threads_count->get_value() == 0 && count == 0 &&
    _daemon_threads_count->get_value() == 0 && daemon_count == 0),
    "thread counts should reach 0 at the same time, live %d,%d daemon %d,%d",
    (int)_live_threads_count->get_value(), count,
    (int)_daemon_threads_count->get_value(), daemon_count);
  assert(_daemon_threads_count->get_value() > 0 ||
    (_daemon_threads_count->get_value() == 0 && daemon_count == 0),
    "thread counts should reach 0 at the same time, daemon %d,%d",
    (int)_daemon_threads_count->get_value(), daemon_count);
}

void ThreadService::current_thread_exiting(JavaThread* jt, bool daemon) {
  // Do not count hidden threads
  if (is_hidden_thread(jt)) {
    return;
  }

  assert(jt == JavaThread::current(), "Called by current thread");
  assert(!jt->is_terminated() && jt->is_exiting(), "must be exiting");

  decrement_thread_counts(jt, daemon);
}

// FIXME: JVMTI should call this function
Handle ThreadService::get_current_contended_monitor(JavaThread* thread) {
  assert(thread != nullptr, "should be non-null");
  DEBUG_ONLY(Thread::check_for_dangling_thread_pointer(thread);)

  // This function can be called on a target JavaThread that is not
  // the caller and we are not at a safepoint. So it is possible for
  // the waiting or pending condition to be over/stale and for the
  // first stage of async deflation to clear the object field in
  // the ObjectMonitor. It is also possible for the object to be
  // inflated again and to be associated with a completely different
  // ObjectMonitor by the time this object reference is processed
  // by the caller.
  ObjectMonitor *wait_obj = thread->current_waiting_monitor();

  oop obj = nullptr;
  if (wait_obj != nullptr) {
    // thread is doing an Object.wait() call
    obj = wait_obj->object();
  } else {
    ObjectMonitor *enter_obj = thread->current_pending_monitor();
    if (enter_obj != nullptr) {
      // thread is trying to enter() an ObjectMonitor.
      obj = enter_obj->object();
    }
  }

  Handle h(Thread::current(), obj);
  return h;
}

bool ThreadService::set_thread_monitoring_contention(bool flag) {
  MutexLocker m(Management_lock);

  bool prev = _thread_monitoring_contention_enabled;
  _thread_monitoring_contention_enabled = flag;

  return prev;
}

bool ThreadService::set_thread_cpu_time_enabled(bool flag) {
  MutexLocker m(Management_lock);

  bool prev = _thread_cpu_time_enabled;
  _thread_cpu_time_enabled = flag;

  return prev;
}

bool ThreadService::set_thread_allocated_memory_enabled(bool flag) {
  MutexLocker m(Management_lock);

  bool prev = _thread_allocated_memory_enabled;
  _thread_allocated_memory_enabled = flag;

  return prev;
}

void ThreadService::metadata_do(void f(Metadata*)) {
  for (ThreadDumpResult* dump = _threaddump_list; dump != nullptr; dump = dump->next()) {
    dump->metadata_do(f);
  }
}

void ThreadService::add_thread_dump(ThreadDumpResult* dump) {
  MutexLocker ml(Management_lock);
  if (_threaddump_list == nullptr) {
    _threaddump_list = dump;
  } else {
    dump->set_next(_threaddump_list);
    _threaddump_list = dump;
  }
}

void ThreadService::remove_thread_dump(ThreadDumpResult* dump) {
  MutexLocker ml(Management_lock);

  ThreadDumpResult* prev = nullptr;
  bool found = false;
  for (ThreadDumpResult* d = _threaddump_list; d != nullptr; prev = d, d = d->next()) {
    if (d == dump) {
      if (prev == nullptr) {
        _threaddump_list = dump->next();
      } else {
        prev->set_next(dump->next());
      }
      found = true;
      break;
    }
  }
  assert(found, "The threaddump result to be removed must exist.");
}

// Dump stack trace of threads specified in the given threads array.
// Returns StackTraceElement[][] each element is the stack trace of a thread in
// the corresponding entry in the given threads array
Handle ThreadService::dump_stack_traces(GrowableArray<instanceHandle>* threads,
                                        int num_threads,
                                        TRAPS) {
  assert(num_threads > 0, "just checking");

  ThreadDumpResult dump_result;
  VM_ThreadDump op(&dump_result,
                   threads,
                   num_threads,
                   -1,    /* entire stack */
                   false, /* with locked monitors */
                   false  /* with locked synchronizers */);
  VMThread::execute(&op);

  // Allocate the resulting StackTraceElement[][] object

  ResourceMark rm(THREAD);
  Klass* k = SystemDictionary::resolve_or_fail(vmSymbols::java_lang_StackTraceElement_array(), true, CHECK_NH);
  ObjArrayKlass* ik = ObjArrayKlass::cast(k);
  objArrayOop r = oopFactory::new_objArray(ik, num_threads, CHECK_NH);
  objArrayHandle result_obj(THREAD, r);

  int num_snapshots = dump_result.num_snapshots();
  assert(num_snapshots == num_threads, "Must have num_threads thread snapshots");
  assert(num_snapshots == 0 || dump_result.t_list_has_been_set(), "ThreadsList must have been set if we have a snapshot");
  int i = 0;
  for (ThreadSnapshot* ts = dump_result.snapshots(); ts != nullptr; i++, ts = ts->next()) {
    ThreadStackTrace* stacktrace = ts->get_stack_trace();
    if (stacktrace == nullptr) {
      // No stack trace
      result_obj->obj_at_put(i, nullptr);
    } else {
      // Construct an array of java/lang/StackTraceElement object
      Handle backtrace_h = stacktrace->allocate_fill_stack_trace_element_array(CHECK_NH);
      result_obj->obj_at_put(i, backtrace_h());
    }
  }

  return result_obj;
}

void ThreadService::reset_contention_count_stat(JavaThread* thread) {
  ThreadStatistics* stat = thread->get_thread_stat();
  if (stat != nullptr) {
    stat->reset_count_stat();
  }
}

void ThreadService::reset_contention_time_stat(JavaThread* thread) {
  ThreadStatistics* stat = thread->get_thread_stat();
  if (stat != nullptr) {
    stat->reset_time_stat();
  }
}

bool ThreadService::is_virtual_or_carrier_thread(JavaThread* jt) {
  oop threadObj = jt->threadObj();
  if (threadObj != nullptr && threadObj->is_a(vmClasses::BaseVirtualThread_klass())) {
    // a virtual thread backed by JavaThread
    return true;
  }
  if (jt->is_vthread_mounted()) {
    // carrier thread
    return true;
  }
  return false;
}

// Find deadlocks involving raw monitors, object monitors and concurrent locks
// if concurrent_locks is true.
// We skip virtual thread carriers under the assumption that the current scheduler, ForkJoinPool,
// doesn't hold any locks while mounting a virtual thread, so any owned monitor (or j.u.c., lock for that matter)
// on that JavaThread must be owned by the virtual thread, and we don't support deadlock detection for virtual threads.
DeadlockCycle* ThreadService::find_deadlocks_at_safepoint(ThreadsList * t_list, bool concurrent_locks) {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at safepoint");

  // This code was modified from the original Threads::find_deadlocks code.
  int globalDfn = 0, thisDfn;
  ObjectMonitor* waitingToLockMonitor = nullptr;
  JvmtiRawMonitor* waitingToLockRawMonitor = nullptr;
  oop waitingToLockBlocker = nullptr;
  bool blocked_on_monitor = false;
  JavaThread *currentThread, *previousThread;
  int num_deadlocks = 0;

  // Initialize the depth-first-number for each JavaThread.
  JavaThreadIterator jti(t_list);
  for (JavaThread* jt = jti.first(); jt != nullptr; jt = jti.next()) {
    if (!is_virtual_or_carrier_thread(jt)) {
      jt->set_depth_first_number(-1);
    }
  }

  DeadlockCycle* deadlocks = nullptr;
  DeadlockCycle* last = nullptr;
  DeadlockCycle* cycle = new DeadlockCycle();
  for (JavaThread* jt = jti.first(); jt != nullptr; jt = jti.next()) {
    if (is_virtual_or_carrier_thread(jt)) {
      // skip virtual and carrier threads
      continue;
    }
    if (jt->depth_first_number() >= 0) {
      // this thread was already visited
      continue;
    }

    thisDfn = globalDfn;
    jt->set_depth_first_number(globalDfn++);
    previousThread = jt;
    currentThread = jt;

    cycle->reset();

    // The ObjectMonitor* can't be async deflated since we are at a safepoint.
    // When there is a deadlock, all the monitors involved in the dependency
    // cycle must be contended and heavyweight. So we only care about the
    // heavyweight monitor a thread is waiting to lock.
    waitingToLockMonitor = jt->current_pending_monitor();
    // JVM TI raw monitors can also be involved in deadlocks, and we can be
    // waiting to lock both a raw monitor and ObjectMonitor at the same time.
    // It isn't clear how to make deadlock detection work correctly if that
    // happens.
    waitingToLockRawMonitor = jt->current_pending_raw_monitor();

    if (concurrent_locks) {
      waitingToLockBlocker = jt->current_park_blocker();
    }

    while (waitingToLockMonitor != nullptr ||
           waitingToLockRawMonitor != nullptr ||
           waitingToLockBlocker != nullptr) {
      cycle->add_thread(currentThread);
      // Give preference to the raw monitor
      if (waitingToLockRawMonitor != nullptr) {
        Thread* owner = waitingToLockRawMonitor->owner();
        if (owner != nullptr && // the raw monitor could be released at any time
            owner->is_Java_thread()) {
          currentThread = JavaThread::cast(owner);
        }
      } else if (waitingToLockMonitor != nullptr) {
        if (waitingToLockMonitor->has_owner()) {
          currentThread = Threads::owning_thread_from_monitor(t_list, waitingToLockMonitor);
          // If currentThread is null we would like to know if the owner
          // is an unmounted vthread (no JavaThread*), because if it's not,
          // it would mean the previous currentThread is blocked permanently
          // and we should record this as a deadlock. Since there is currently
          // no fast way to determine if the owner is indeed an unmounted
          // vthread we never record this as a deadlock. Note: unless there
          // is a bug in the VM, or a thread exits without releasing monitors
          // acquired through JNI, null should imply an unmounted vthread owner.
        }
      } else {
        if (concurrent_locks) {
          if (waitingToLockBlocker->is_a(vmClasses::java_util_concurrent_locks_AbstractOwnableSynchronizer_klass())) {
            oop threadObj = java_util_concurrent_locks_AbstractOwnableSynchronizer::get_owner_threadObj(waitingToLockBlocker);
            // This JavaThread (if there is one) is protected by the
            // ThreadsListSetter in VM_FindDeadlocks::doit().
            currentThread = threadObj != nullptr ? java_lang_Thread::thread(threadObj) : nullptr;
          } else {
            currentThread = nullptr;
          }
        }
      }

      if (currentThread == nullptr || is_virtual_or_carrier_thread(currentThread)) {
        // No dependency on another thread
        break;
      }
      if (currentThread->depth_first_number() < 0) {
        // First visit to this thread
        currentThread->set_depth_first_number(globalDfn++);
      } else if (currentThread->depth_first_number() < thisDfn) {
        // Thread already visited, and not on a (new) cycle
        break;
      } else if (currentThread == previousThread) {
        // Self-loop, ignore
        break;
      } else {
        // We have a (new) cycle
        num_deadlocks++;

        // add this cycle to the deadlocks list
        if (deadlocks == nullptr) {
          deadlocks = cycle;
        } else {
          last->set_next(cycle);
        }
        last = cycle;
        cycle = new DeadlockCycle();
        break;
      }
      previousThread = currentThread;
      waitingToLockMonitor = (ObjectMonitor*)currentThread->current_pending_monitor();
      if (concurrent_locks) {
        waitingToLockBlocker = currentThread->current_park_blocker();
      }
    }

  }
  delete cycle;
  return deadlocks;
}

ThreadDumpResult::ThreadDumpResult() : _num_threads(0), _num_snapshots(0), _snapshots(nullptr), _last(nullptr), _next(nullptr), _setter() {

  // Create a new ThreadDumpResult object and append to the list.
  // If GC happens before this function returns, Method*
  // in the stack trace will be visited.
  ThreadService::add_thread_dump(this);
}

ThreadDumpResult::ThreadDumpResult(int num_threads) : _num_threads(num_threads), _num_snapshots(0), _snapshots(nullptr), _last(nullptr), _next(nullptr), _setter() {
  // Create a new ThreadDumpResult object and append to the list.
  // If GC happens before this function returns, oops
  // will be visited.
  ThreadService::add_thread_dump(this);
}

ThreadDumpResult::~ThreadDumpResult() {
  ThreadService::remove_thread_dump(this);

  // free all the ThreadSnapshot objects created during
  // the VM_ThreadDump operation
  ThreadSnapshot* ts = _snapshots;
  while (ts != nullptr) {
    ThreadSnapshot* p = ts;
    ts = ts->next();
    delete p;
  }
}

ThreadSnapshot* ThreadDumpResult::add_thread_snapshot() {
  ThreadSnapshot* ts = new ThreadSnapshot();
  link_thread_snapshot(ts);
  return ts;
}

ThreadSnapshot* ThreadDumpResult::add_thread_snapshot(JavaThread* thread) {
  ThreadSnapshot* ts = new ThreadSnapshot();
  link_thread_snapshot(ts);
  ts->initialize(t_list(), thread);
  return ts;
}

void ThreadDumpResult::link_thread_snapshot(ThreadSnapshot* ts) {
  assert(_num_threads == 0 || _num_snapshots < _num_threads,
         "_num_snapshots must be less than _num_threads");
  _num_snapshots++;
  if (_snapshots == nullptr) {
    _snapshots = ts;
  } else {
    _last->set_next(ts);
  }
  _last = ts;
}

void ThreadDumpResult::metadata_do(void f(Metadata*)) {
  for (ThreadSnapshot* ts = _snapshots; ts != nullptr; ts = ts->next()) {
    ts->metadata_do(f);
  }
}

ThreadsList* ThreadDumpResult::t_list() {
  return _setter.list();
}

StackFrameInfo::StackFrameInfo(javaVFrame* jvf, bool with_lock_info) {
  _method = jvf->method();
  _bci = jvf->bci();
  _class_holder = OopHandle(_thread_service_storage, _method->method_holder()->klass_holder());
  _locked_monitors = nullptr;
  if (with_lock_info) {
    Thread* current_thread = Thread::current();
    ResourceMark rm(current_thread);
    HandleMark hm(current_thread);
    GrowableArray<MonitorInfo*>* list = jvf->locked_monitors();
    int length = list->length();
    if (length > 0) {
      _locked_monitors = new (mtServiceability) GrowableArray<OopHandle>(length, mtServiceability);
      for (int i = 0; i < length; i++) {
        MonitorInfo* monitor = list->at(i);
        assert(monitor->owner() != nullptr, "This monitor must have an owning object");
        _locked_monitors->append(OopHandle(_thread_service_storage, monitor->owner()));
      }
    }
  }
}

StackFrameInfo::~StackFrameInfo() {
  if (_locked_monitors != nullptr) {
    for (int i = 0; i < _locked_monitors->length(); i++) {
      _locked_monitors->at(i).release(_thread_service_storage);
    }
    delete _locked_monitors;
  }
  _class_holder.release(_thread_service_storage);
}

void StackFrameInfo::metadata_do(void f(Metadata*)) {
  f(_method);
}

void StackFrameInfo::print_on(outputStream* st) const {
  ResourceMark rm;
  java_lang_Throwable::print_stack_element(st, method(), bci());
  int len = (_locked_monitors != nullptr ? _locked_monitors->length() : 0);
  for (int i = 0; i < len; i++) {
    oop o = _locked_monitors->at(i).resolve();
    st->print_cr("\t- locked <" INTPTR_FORMAT "> (a %s)", p2i(o), o->klass()->external_name());
  }
}

// Iterate through monitor cache to find JNI locked monitors
class InflatedMonitorsClosure: public MonitorClosure {
private:
  ThreadStackTrace* _stack_trace;
public:
  InflatedMonitorsClosure(ThreadStackTrace* st) {
    _stack_trace = st;
  }
  void do_monitor(ObjectMonitor* mid) {
    oop object = mid->object();
    if (!_stack_trace->is_owned_monitor_on_stack(object)) {
      _stack_trace->add_jni_locked_monitor(object);
    }
  }
};

ThreadStackTrace::ThreadStackTrace(JavaThread* t, bool with_locked_monitors) {
  _thread = t;
  _frames = new (mtServiceability) GrowableArray<StackFrameInfo*>(INITIAL_ARRAY_SIZE, mtServiceability);
  _depth = 0;
  _with_locked_monitors = with_locked_monitors;
  if (_with_locked_monitors) {
    _jni_locked_monitors = new (mtServiceability) GrowableArray<OopHandle>(INITIAL_ARRAY_SIZE, mtServiceability);
  } else {
    _jni_locked_monitors = nullptr;
  }
}

void ThreadStackTrace::add_jni_locked_monitor(oop object) {
  _jni_locked_monitors->append(OopHandle(_thread_service_storage, object));
}

ThreadStackTrace::~ThreadStackTrace() {
  for (int i = 0; i < _frames->length(); i++) {
    delete _frames->at(i);
  }
  delete _frames;
  if (_jni_locked_monitors != nullptr) {
    for (int i = 0; i < _jni_locked_monitors->length(); i++) {
      _jni_locked_monitors->at(i).release(_thread_service_storage);
    }
    delete _jni_locked_monitors;
  }
}

void ThreadStackTrace::dump_stack_at_safepoint(int maxDepth, ObjectMonitorsView* monitors, bool full) {
  assert(SafepointSynchronize::is_at_safepoint(), "all threads are stopped");

  if (_thread->has_last_Java_frame()) {
    RegisterMap reg_map(_thread,
                        RegisterMap::UpdateMap::include,
                        RegisterMap::ProcessFrames::include,
                        RegisterMap::WalkContinuation::skip);
    ResourceMark rm(VMThread::vm_thread());
    // If full, we want to print both vthread and carrier frames
    vframe* start_vf = !full && _thread->is_vthread_mounted()
      ? _thread->carrier_last_java_vframe(&reg_map)
      : _thread->last_java_vframe(&reg_map);
    int count = 0;
    for (vframe* f = start_vf; f; f = f->sender() ) {
      if (maxDepth >= 0 && count == maxDepth) {
        // Skip frames if more than maxDepth
        break;
      }
      if (!full && f->is_vthread_entry()) {
        break;
      }
      if (f->is_java_frame()) {
        javaVFrame* jvf = javaVFrame::cast(f);
        add_stack_frame(jvf);
        count++;
      } else {
        // Ignore non-Java frames
      }
    }
  }

  if (_with_locked_monitors) {
    // Iterate inflated monitors and find monitors locked by this thread
    // that are not found in the stack, e.g. JNI locked monitors:
    InflatedMonitorsClosure imc(this);
    monitors->visit(&imc, _thread);
  }
}


bool ThreadStackTrace::is_owned_monitor_on_stack(oop object) {
  assert(SafepointSynchronize::is_at_safepoint(), "all threads are stopped");

  bool found = false;
  int num_frames = get_stack_depth();
  for (int depth = 0; depth < num_frames; depth++) {
    StackFrameInfo* frame = stack_frame_at(depth);
    int len = frame->num_locked_monitors();
    GrowableArray<OopHandle>* locked_monitors = frame->locked_monitors();
    for (int j = 0; j < len; j++) {
      oop monitor = locked_monitors->at(j).resolve();
      assert(monitor != nullptr, "must be a Java object");
      if (monitor == object) {
        found = true;
        break;
      }
    }
  }
  return found;
}

Handle ThreadStackTrace::allocate_fill_stack_trace_element_array(TRAPS) {
  InstanceKlass* ik = vmClasses::StackTraceElement_klass();
  assert(ik != nullptr, "must be loaded in 1.4+");

  // Allocate an array of java/lang/StackTraceElement object
  objArrayOop ste = oopFactory::new_objArray(ik, _depth, CHECK_NH);
  objArrayHandle backtrace(THREAD, ste);
  for (int j = 0; j < _depth; j++) {
    StackFrameInfo* frame = _frames->at(j);
    methodHandle mh(THREAD, frame->method());
    oop element = java_lang_StackTraceElement::create(mh, frame->bci(), CHECK_NH);
    backtrace->obj_at_put(j, element);
  }
  return backtrace;
}

void ThreadStackTrace::add_stack_frame(javaVFrame* jvf) {
  StackFrameInfo* frame = new StackFrameInfo(jvf, _with_locked_monitors);
  _frames->append(frame);
  _depth++;
}

void ThreadStackTrace::metadata_do(void f(Metadata*)) {
  int length = _frames->length();
  for (int i = 0; i < length; i++) {
    _frames->at(i)->metadata_do(f);
  }
}


ConcurrentLocksDump::~ConcurrentLocksDump() {
  if (_retain_map_on_free) {
    return;
  }

  for (ThreadConcurrentLocks* t = _map; t != nullptr;)  {
    ThreadConcurrentLocks* tcl = t;
    t = t->next();
    delete tcl;
  }
}

void ConcurrentLocksDump::dump_at_safepoint() {
  // dump all locked concurrent locks
  assert(SafepointSynchronize::is_at_safepoint(), "all threads are stopped");

  GrowableArray<oop>* aos_objects = new (mtServiceability) GrowableArray<oop>(INITIAL_ARRAY_SIZE, mtServiceability);

  // Find all instances of AbstractOwnableSynchronizer
  HeapInspection::find_instances_at_safepoint(vmClasses::java_util_concurrent_locks_AbstractOwnableSynchronizer_klass(),
                                              aos_objects);
  // Build a map of thread to its owned AQS locks
  build_map(aos_objects);

  delete aos_objects;
}


// build a map of JavaThread to all its owned AbstractOwnableSynchronizer
void ConcurrentLocksDump::build_map(GrowableArray<oop>* aos_objects) {
  int length = aos_objects->length();
  for (int i = 0; i < length; i++) {
    oop o = aos_objects->at(i);
    oop owner_thread_obj = java_util_concurrent_locks_AbstractOwnableSynchronizer::get_owner_threadObj(o);
    if (owner_thread_obj != nullptr) {
      // See comments in ThreadConcurrentLocks to see how this
      // JavaThread* is protected.
      JavaThread* thread = java_lang_Thread::thread(owner_thread_obj);
      assert(o->is_instance(), "Must be an instanceOop");
      add_lock(thread, (instanceOop) o);
    }
  }
}

void ConcurrentLocksDump::add_lock(JavaThread* thread, instanceOop o) {
  ThreadConcurrentLocks* tcl = thread_concurrent_locks(thread);
  if (tcl != nullptr) {
    tcl->add_lock(o);
    return;
  }

  // First owned lock found for this thread
  tcl = new ThreadConcurrentLocks(thread);
  tcl->add_lock(o);
  if (_map == nullptr) {
    _map = tcl;
  } else {
    _last->set_next(tcl);
  }
  _last = tcl;
}

ThreadConcurrentLocks* ConcurrentLocksDump::thread_concurrent_locks(JavaThread* thread) {
  for (ThreadConcurrentLocks* tcl = _map; tcl != nullptr; tcl = tcl->next()) {
    if (tcl->java_thread() == thread) {
      return tcl;
    }
  }
  return nullptr;
}

void ConcurrentLocksDump::print_locks_on(JavaThread* t, outputStream* st) {
  st->print_cr("   Locked ownable synchronizers:");
  ThreadConcurrentLocks* tcl = thread_concurrent_locks(t);
  GrowableArray<OopHandle>* locks = (tcl != nullptr ? tcl->owned_locks() : nullptr);
  if (locks == nullptr || locks->is_empty()) {
    st->print_cr("\t- None");
    st->cr();
    return;
  }

  for (int i = 0; i < locks->length(); i++) {
    oop obj = locks->at(i).resolve();
    st->print_cr("\t- <" INTPTR_FORMAT "> (a %s)", p2i(obj), obj->klass()->external_name());
  }
  st->cr();
}

ThreadConcurrentLocks::ThreadConcurrentLocks(JavaThread* thread) {
  _thread = thread;
  _owned_locks = new (mtServiceability) GrowableArray<OopHandle>(INITIAL_ARRAY_SIZE, mtServiceability);
  _next = nullptr;
}

ThreadConcurrentLocks::~ThreadConcurrentLocks() {
  for (int i = 0; i < _owned_locks->length(); i++) {
    _owned_locks->at(i).release(_thread_service_storage);
  }
  delete _owned_locks;
}

void ThreadConcurrentLocks::add_lock(instanceOop o) {
  _owned_locks->append(OopHandle(_thread_service_storage, o));
}

ThreadStatistics::ThreadStatistics() {
  _contended_enter_count = 0;
  _monitor_wait_count = 0;
  _sleep_count = 0;
  _count_pending_reset = false;
  _timer_pending_reset = false;
  memset((void*) _perf_recursion_counts, 0, sizeof(_perf_recursion_counts));
}

oop ThreadSnapshot::threadObj() const { return _threadObj.resolve(); }

void ThreadSnapshot::initialize(ThreadsList * t_list, JavaThread* thread) {
  _thread = thread;
  oop threadObj = thread->threadObj();
  _threadObj = OopHandle(_thread_service_storage, threadObj);

  ThreadStatistics* stat = thread->get_thread_stat();
  _contended_enter_ticks = stat->contended_enter_ticks();
  _contended_enter_count = stat->contended_enter_count();
  _monitor_wait_ticks = stat->monitor_wait_ticks();
  _monitor_wait_count = stat->monitor_wait_count();
  _sleep_ticks = stat->sleep_ticks();
  _sleep_count = stat->sleep_count();

  // If thread is still attaching then threadObj will be null.
  _thread_status = threadObj == nullptr ? JavaThreadStatus::NEW
                                     : java_lang_Thread::get_thread_status(threadObj);

  _is_suspended = thread->is_suspended();
  _is_in_native = (thread->thread_state() == _thread_in_native);

  Handle obj = ThreadService::get_current_contended_monitor(thread);

  oop blocker_object = nullptr;
  oop blocker_object_owner = nullptr;

  if (thread->is_vthread_mounted() && thread->vthread() != threadObj) { // ThreadSnapshot only captures platform threads
    _thread_status = JavaThreadStatus::IN_OBJECT_WAIT;
    oop vthread = thread->vthread();
    assert(vthread != nullptr, "");
    blocker_object = vthread;
    blocker_object_owner = vthread;
  } else if (_thread_status == JavaThreadStatus::BLOCKED_ON_MONITOR_ENTER ||
      _thread_status == JavaThreadStatus::IN_OBJECT_WAIT ||
      _thread_status == JavaThreadStatus::IN_OBJECT_WAIT_TIMED) {

    if (obj() == nullptr) {
      // monitor no longer exists; thread is not blocked
      _thread_status = JavaThreadStatus::RUNNABLE;
    } else {
      blocker_object = obj();
      JavaThread* owner = ObjectSynchronizer::get_lock_owner(t_list, obj);
      if ((owner == nullptr && _thread_status == JavaThreadStatus::BLOCKED_ON_MONITOR_ENTER)
          || (owner != nullptr && owner->is_attaching_via_jni())) {
        // ownership information of the monitor is not available
        // (may no longer be owned or releasing to some other thread)
        // make this thread in RUNNABLE state.
        // And when the owner thread is in attaching state, the java thread
        // is not completely initialized. For example thread name and id
        // and may not be set, so hide the attaching thread.
        _thread_status = JavaThreadStatus::RUNNABLE;
        blocker_object = nullptr;
      } else if (owner != nullptr) {
        blocker_object_owner = owner->threadObj();
      }
    }
  } else if (_thread_status == JavaThreadStatus::PARKED || _thread_status == JavaThreadStatus::PARKED_TIMED) {
    blocker_object = thread->current_park_blocker();
    if (blocker_object != nullptr && blocker_object->is_a(vmClasses::java_util_concurrent_locks_AbstractOwnableSynchronizer_klass())) {
      blocker_object_owner = java_util_concurrent_locks_AbstractOwnableSynchronizer::get_owner_threadObj(blocker_object);
    }
  }

  if (blocker_object != nullptr) {
    _blocker_object = OopHandle(_thread_service_storage, blocker_object);
  }
  if (blocker_object_owner != nullptr) {
    _blocker_object_owner = OopHandle(_thread_service_storage, blocker_object_owner);
  }
}

oop ThreadSnapshot::blocker_object() const           { return _blocker_object.resolve(); }
oop ThreadSnapshot::blocker_object_owner() const     { return _blocker_object_owner.resolve(); }

ThreadSnapshot::~ThreadSnapshot() {
  _blocker_object.release(_thread_service_storage);
  _blocker_object_owner.release(_thread_service_storage);
  _threadObj.release(_thread_service_storage);

  delete _stack_trace;
  delete _concurrent_locks;
}

void ThreadSnapshot::dump_stack_at_safepoint(int max_depth, bool with_locked_monitors,
                                             ObjectMonitorsView* monitors, bool full) {
  _stack_trace = new ThreadStackTrace(_thread, with_locked_monitors);
  _stack_trace->dump_stack_at_safepoint(max_depth, monitors, full);
}


void ThreadSnapshot::metadata_do(void f(Metadata*)) {
  if (_stack_trace != nullptr) {
    _stack_trace->metadata_do(f);
  }
}


DeadlockCycle::DeadlockCycle() {
  _threads = new (mtServiceability) GrowableArray<JavaThread*>(INITIAL_ARRAY_SIZE, mtServiceability);
  _next = nullptr;
}

DeadlockCycle::~DeadlockCycle() {
  delete _threads;
}

void DeadlockCycle::print_on_with(ThreadsList * t_list, outputStream* st) const {
  st->cr();
  st->print_cr("Found one Java-level deadlock:");
  st->print("=============================");

  JavaThread* currentThread;
  JvmtiRawMonitor* waitingToLockRawMonitor;
  oop waitingToLockBlocker;
  int len = _threads->length();
  for (int i = 0; i < len; i++) {
    currentThread = _threads->at(i);
    // The ObjectMonitor* can't be async deflated since we are at a safepoint.
    ObjectMonitor* waitingToLockMonitor = currentThread->current_pending_monitor();
    waitingToLockRawMonitor = currentThread->current_pending_raw_monitor();
    waitingToLockBlocker = currentThread->current_park_blocker();
    st->cr();
    st->print_cr("\"%s\":", currentThread->name());
    const char* owner_desc = ",\n  which is held by";

    // Note: As the JVM TI "monitor contended enter" event callback is executed after ObjectMonitor
    // sets the current pending monitor, it is possible to then see a pending raw monitor as well.
    if (waitingToLockRawMonitor != nullptr) {
      st->print("  waiting to lock JVM TI raw monitor " INTPTR_FORMAT, p2i(waitingToLockRawMonitor));
      Thread* owner = waitingToLockRawMonitor->owner();
      // Could be null as the raw monitor could be released at any time if held by non-JavaThread
      if (owner != nullptr) {
        if (owner->is_Java_thread()) {
          currentThread = JavaThread::cast(owner);
          st->print_cr("%s \"%s\"", owner_desc, currentThread->name());
        } else {
          st->print_cr(",\n  which has now been released");
        }
      } else {
        st->print_cr("%s non-Java thread=" PTR_FORMAT, owner_desc, p2i(owner));
      }
    }

    if (waitingToLockMonitor != nullptr) {
      st->print("  waiting to lock monitor " INTPTR_FORMAT, p2i(waitingToLockMonitor));
      oop obj = waitingToLockMonitor->object();
      st->print(" (object " INTPTR_FORMAT ", a %s)", p2i(obj),
                 obj->klass()->external_name());

      if (!currentThread->current_pending_monitor_is_from_java()) {
        owner_desc = "\n  in JNI, which is held by";
      }
      currentThread = Threads::owning_thread_from_monitor(t_list, waitingToLockMonitor);
      if (currentThread == nullptr) {
        // The deadlock was detected at a safepoint so the JavaThread
        // that owns waitingToLockMonitor should be findable, but
        // if it is not findable, then the previous currentThread is
        // blocked permanently.
        st->print_cr("%s UNKNOWN_owner_addr=" INT64_FORMAT, owner_desc,
                     waitingToLockMonitor->owner());
        continue;
      }
    } else {
      st->print("  waiting for ownable synchronizer " INTPTR_FORMAT ", (a %s)",
                p2i(waitingToLockBlocker),
                waitingToLockBlocker->klass()->external_name());
      assert(waitingToLockBlocker->is_a(vmClasses::java_util_concurrent_locks_AbstractOwnableSynchronizer_klass()),
             "Must be an AbstractOwnableSynchronizer");
      oop ownerObj = java_util_concurrent_locks_AbstractOwnableSynchronizer::get_owner_threadObj(waitingToLockBlocker);
      currentThread = java_lang_Thread::thread(ownerObj);
      assert(currentThread != nullptr, "AbstractOwnableSynchronizer owning thread is unexpectedly null");
    }
    st->print_cr("%s \"%s\"", owner_desc, currentThread->name());
  }

  st->cr();

  // Print stack traces
  bool oldJavaMonitorsInStackTrace = JavaMonitorsInStackTrace;
  JavaMonitorsInStackTrace = true;
  st->print_cr("Java stack information for the threads listed above:");
  st->print_cr("===================================================");
  for (int j = 0; j < len; j++) {
    currentThread = _threads->at(j);
    st->print_cr("\"%s\":", currentThread->name());
    currentThread->print_stack_on(st);
  }
  JavaMonitorsInStackTrace = oldJavaMonitorsInStackTrace;
}

ThreadsListEnumerator::ThreadsListEnumerator(Thread* cur_thread,
                                             bool include_jvmti_agent_threads,
                                             bool include_jni_attaching_threads,
                                             bool include_bound_virtual_threads) {
  assert(cur_thread == Thread::current(), "Check current thread");

  int init_size = ThreadService::get_live_thread_count();
  _threads_array = new GrowableArray<instanceHandle>(init_size);

  for (JavaThreadIteratorWithHandle jtiwh; JavaThread *jt = jtiwh.next(); ) {
    // skips JavaThreads in the process of exiting
    // and also skips VM internal JavaThreads
    // Threads in _thread_new or _thread_new_trans state are included.
    // i.e. threads have been started but not yet running.
    if (jt->threadObj() == nullptr   ||
        jt->is_exiting() ||
        !java_lang_Thread::is_alive(jt->threadObj())   ||
        jt->is_hidden_from_external_view()) {
      continue;
    }

    // skip agent threads
    if (!include_jvmti_agent_threads && jt->is_jvmti_agent_thread()) {
      continue;
    }

    // skip jni threads in the process of attaching
    if (!include_jni_attaching_threads && jt->is_attaching_via_jni()) {
      continue;
    }

    // skip instances of BoundVirtualThread
    if (!include_bound_virtual_threads && jt->threadObj()->is_a(vmClasses::BoundVirtualThread_klass())) {
      continue;
    }

    instanceHandle h(cur_thread, (instanceOop) jt->threadObj());
    _threads_array->append(h);
  }
}


// jdk.internal.vm.ThreadSnapshot support
class GetThreadSnapshotHandshakeClosure: public HandshakeClosure {
private:
  static OopStorage* oop_storage() {
    assert(_thread_service_storage != nullptr, "sanity");
    return _thread_service_storage;
  }

public:
  struct OwnedLock {
    // should be synced with ordinals of jdk.internal.vm.ThreadSnapshot.OwnedLockType enum
    enum Type {
      NOTHING = -1,
      LOCKED = 0,
      ELIMINATED = 1,
    };

    int _frame_depth;
    Type _type;
    // synchronization object (when type == LOCKED) or its klass (type == ELIMINATED)
    OopHandle _obj;

    OwnedLock(int depth, Type type, OopHandle obj): _frame_depth(depth), _type(type), _obj(obj) {}
    OwnedLock(): _frame_depth(0), _type(NOTHING), _obj(nullptr) {}
  };

  struct Blocker {
    // should be synced with ordinals of jdk.internal.vm.ThreadSnapshot.BlockerLockType enum
    enum Type {
      NOTHING = -1,
      PARK_BLOCKER = 0,
      WAITING_TO_LOCK = 1,
      WAITING_ON = 2,
    };

    Type _type;
    // park blocker or an object the thread waiting on/trying to lock
    OopHandle _obj;
    // thread that owns park blocker object when park blocker is AbstractOwnableSynchronizer
    OopHandle _owner;

    Blocker(Type type, OopHandle obj): _type(type), _obj(obj), _owner() {}
    Blocker(): _type(NOTHING), _obj(), _owner() {}

    bool is_empty() const {
      return _type == NOTHING;
    }
  };

  Handle _thread_h;
  JavaThread* _java_thread;
  int _frame_count; // length of _methods and _bcis arrays
  GrowableArray<Method*>* _methods;
  GrowableArray<int>* _bcis;
  JavaThreadStatus _thread_status;
  OopHandle _thread_name;
  OopHandle _carrier_thread;
  GrowableArray<OwnedLock>* _locks;
  Blocker _blocker;

  GetThreadSnapshotHandshakeClosure(Handle thread_h):
    HandshakeClosure("GetThreadSnapshotHandshakeClosure"),
    _thread_h(thread_h), _java_thread(nullptr),
    _frame_count(0), _methods(nullptr), _bcis(nullptr),
    _thread_status(), _thread_name(nullptr),
    _locks(nullptr), _blocker() {
  }
  virtual ~GetThreadSnapshotHandshakeClosure() {
    delete _methods;
    delete _bcis;
    _thread_name.release(oop_storage());
    if (_locks != nullptr) {
      for (int i = 0; i < _locks->length(); i++) {
        _locks->at(i)._obj.release(oop_storage());
      }
      delete _locks;
    }
    _blocker._obj.release(oop_storage());
    _blocker._owner.release(oop_storage());
  }

private:
  void detect_locks(javaVFrame* jvf, int depth) {
    Thread* current = Thread::current();

    if (depth == 0 && _blocker.is_empty()) {
      // If this is the first frame and it is java.lang.Object.wait(...)
      // then print out the receiver.
      if (jvf->method()->name() == vmSymbols::wait_name() &&
        jvf->method()->method_holder()->name() == vmSymbols::java_lang_Object()) {
        OopHandle lock_object;
        StackValueCollection* locs = jvf->locals();
        if (!locs->is_empty()) {
          StackValue* sv = locs->at(0);
          if (sv->type() == T_OBJECT) {
            Handle o = locs->at(0)->get_obj();
            lock_object = OopHandle(oop_storage(), o());
          }
        }
        _blocker = Blocker(Blocker::WAITING_ON, lock_object);
      }
    }

    GrowableArray<MonitorInfo*>* mons = jvf->monitors();
    if (!mons->is_empty()) {
      for (int index = (mons->length() - 1); index >= 0; index--) {
        MonitorInfo* monitor = mons->at(index);
        if (monitor->eliminated() && jvf->is_compiled_frame()) { // Eliminated in compiled code
          if (monitor->owner_is_scalar_replaced()) {
            Klass* k = java_lang_Class::as_Klass(monitor->owner_klass());
            _locks->push(OwnedLock(depth, OwnedLock::ELIMINATED, OopHandle(oop_storage(), k->klass_holder())));
          } else {
            Handle owner(current, monitor->owner());
            if (owner.not_null()) {
              Klass* k = owner->klass();
              _locks->push(OwnedLock(depth, OwnedLock::ELIMINATED, OopHandle(oop_storage(), k->klass_holder())));
            }
          }
          continue;
        }
        if (monitor->owner() != nullptr) {
          // the monitor is associated with an object, i.e., it is locked

          if (depth == 0 && _blocker.is_empty()) {
            ObjectMonitor* pending_moninor = java_lang_VirtualThread::is_instance(_thread_h())
              ? java_lang_VirtualThread::current_pending_monitor(_thread_h())
              : jvf->thread()->current_pending_monitor();

            markWord mark = monitor->owner()->mark();
            // The first stage of async deflation does not affect any field
            // used by this comparison so the ObjectMonitor* is usable here.
            if (mark.has_monitor()) {
              ObjectMonitor* mon = ObjectSynchronizer::read_monitor(current, monitor->owner(), mark);
              if (// if the monitor is null we must be in the process of locking
                  mon == nullptr ||
                  // we have marked ourself as pending on this monitor
                  mon == pending_moninor ||
                  // we are not the owner of this monitor
                  (_java_thread != nullptr && !mon->is_entered(_java_thread))) {
                _blocker = Blocker(Blocker::WAITING_TO_LOCK, OopHandle(oop_storage(), monitor->owner()));
                continue; // go to next monitor
              }
            }
          }
          _locks->push(OwnedLock(depth, OwnedLock::LOCKED, OopHandle(oop_storage(), monitor->owner())));
        }
      }
    }
  }

public:
  void do_thread(Thread* th) override {
    Thread* current = Thread::current();
    _java_thread = th != nullptr ? JavaThread::cast(th) : nullptr;

    bool is_virtual = java_lang_VirtualThread::is_instance(_thread_h());
    if (_java_thread != nullptr) {
      if (is_virtual) {
        // mounted vthread, use carrier thread state
        _carrier_thread = OopHandle(oop_storage(), java_lang_VirtualThread::carrier_thread(_thread_h()));
        assert(_carrier_thread.resolve() == _java_thread->threadObj(), "");
        _thread_status = java_lang_Thread::get_thread_status(_carrier_thread.resolve());
      } else {
        _thread_status = java_lang_Thread::get_thread_status(_thread_h());
      }
    } else {
      // unmounted vthread
      int vt_state = java_lang_VirtualThread::state(_thread_h());
      _thread_status = java_lang_VirtualThread::map_state_to_thread_status(vt_state);
    }
    _thread_name = OopHandle(oop_storage(), java_lang_Thread::name(_thread_h()));

    if (_java_thread != nullptr && !_java_thread->has_last_Java_frame()) {
      // stack trace is empty
      return;
    }

    bool vthread_carrier = !is_virtual && (_java_thread->vthread_continuation() != nullptr);

    oop park_blocker = java_lang_Thread::park_blocker(_thread_h());
    if (park_blocker != nullptr) {
      _blocker = Blocker(Blocker::PARK_BLOCKER, OopHandle(oop_storage(), park_blocker));
      if (park_blocker->is_a(vmClasses::java_util_concurrent_locks_AbstractOwnableSynchronizer_klass())) {
        // could be stale (unlikely in practice), but it's good enough to see deadlocks
        oop ownerObj = java_util_concurrent_locks_AbstractOwnableSynchronizer::get_owner_threadObj(park_blocker);
        if (ownerObj != nullptr) {
          _blocker._owner = OopHandle(oop_storage(), ownerObj);
        }
      }
    }

    ResourceMark rm(current);
    HandleMark hm(current);

    const int max_depth = MaxJavaStackTraceDepth;
    const bool skip_hidden = !ShowHiddenFrames;

    // Pick minimum length that will cover most cases
    int init_length = 64;
    _methods = new (mtInternal) GrowableArray<Method*>(init_length, mtInternal);
    _bcis = new (mtInternal) GrowableArray<int>(init_length, mtInternal);
    _locks = new (mtInternal) GrowableArray<OwnedLock>(init_length, mtInternal);
    int total_count = 0;

    vframeStream vfst(_java_thread != nullptr
      ? vframeStream(_java_thread, false, true, vthread_carrier)
      : vframeStream(java_lang_VirtualThread::continuation(_thread_h())));

    for (;
      !vfst.at_end() && (max_depth == 0 || max_depth != total_count);
      vfst.next()) {

      detect_locks(vfst.asJavaVFrame(), total_count);

      if (skip_hidden && (vfst.method()->is_hidden() ||
        vfst.method()->is_continuation_enter_intrinsic())) {
        continue;
      }
      _methods->push(vfst.method());
      _bcis->push(vfst.bci());
      total_count++;
    }

    _frame_count = total_count;
  }
};

class jdk_internal_vm_ThreadLock: AllStatic {
  static bool _inited;
  static int _depth_offset;
  static int _typeOrdinal_offset;
  static int _obj_offset;

  static void compute_offsets(InstanceKlass* klass, TRAPS) {
    JavaClasses::compute_offset(_depth_offset, klass, "depth", vmSymbols::int_signature(), false);
    JavaClasses::compute_offset(_typeOrdinal_offset, klass, "typeOrdinal", vmSymbols::int_signature(), false);
    JavaClasses::compute_offset(_obj_offset, klass, "obj", vmSymbols::object_signature(), false);
  }
public:
  static void init(InstanceKlass* klass, TRAPS) {
    if (!_inited) {
      compute_offsets(klass, CHECK);
      _inited = true;
    }
  }

  static Handle create(InstanceKlass* klass, int depth, int type_ordinal, OopHandle obj, TRAPS) {
    init(klass, CHECK_NH);
    Handle result = klass->allocate_instance_handle(CHECK_NH);
    result->int_field_put(_depth_offset, depth);
    result->int_field_put(_typeOrdinal_offset, type_ordinal);
    result->obj_field_put(_obj_offset, obj.resolve());
    return result;
  }
};

bool jdk_internal_vm_ThreadLock::_inited = false;
int jdk_internal_vm_ThreadLock::_depth_offset;
int jdk_internal_vm_ThreadLock::_typeOrdinal_offset;
int jdk_internal_vm_ThreadLock::_obj_offset;

class jdk_internal_vm_ThreadSnapshot: AllStatic {
  static bool _inited;
  static int _name_offset;
  static int _threadStatus_offset;
  static int _carrierThread_offset;
  static int _stackTrace_offset;
  static int _locks_offset;
  static int _blockerTypeOrdinal_offset;
  static int _blockerObject_offset;
  static int _parkBlockerOwner_offset;

  static void compute_offsets(InstanceKlass* klass, TRAPS) {
    JavaClasses::compute_offset(_name_offset, klass, "name", vmSymbols::string_signature(), false);
    JavaClasses::compute_offset(_threadStatus_offset, klass, "threadStatus", vmSymbols::int_signature(), false);
    JavaClasses::compute_offset(_carrierThread_offset, klass, "carrierThread", vmSymbols::thread_signature(), false);
    JavaClasses::compute_offset(_stackTrace_offset, klass, "stackTrace", vmSymbols::java_lang_StackTraceElement_array(), false);
    JavaClasses::compute_offset(_locks_offset, klass, "locks", vmSymbols::jdk_internal_vm_ThreadLock_array(), false);
    JavaClasses::compute_offset(_blockerTypeOrdinal_offset, klass, "blockerTypeOrdinal", vmSymbols::int_signature(), false);
    JavaClasses::compute_offset(_blockerObject_offset, klass, "blockerObject", vmSymbols::object_signature(), false);
    JavaClasses::compute_offset(_parkBlockerOwner_offset, klass, "parkBlockerOwner", vmSymbols::thread_signature(), false);
  }
public:
  static void init(InstanceKlass* klass, TRAPS) {
    if (!_inited) {
      compute_offsets(klass, CHECK);
      _inited = true;
    }
  }

  static Handle allocate(InstanceKlass* klass, TRAPS) {
    init(klass, CHECK_NH);
    Handle h_k = klass->allocate_instance_handle(CHECK_NH);
    return h_k;
  }

  static void set_name(oop snapshot, oop name) {
    snapshot->obj_field_put(_name_offset, name);
  }
  static void set_thread_status(oop snapshot, int status) {
    snapshot->int_field_put(_threadStatus_offset, status);
  }
  static void set_carrier_thread(oop snapshot, oop carrier_thread) {
    snapshot->obj_field_put(_carrierThread_offset, carrier_thread);
  }
  static void set_stack_trace(oop snapshot, oop trace) {
    snapshot->obj_field_put(_stackTrace_offset, trace);
  }
  static void set_locks(oop snapshot, oop locks) {
    snapshot->obj_field_put(_locks_offset, locks);
  }
  static void set_blocker(oop snapshot, int type_ordinal, oop lock, oop owner) {
    snapshot->int_field_put(_blockerTypeOrdinal_offset, type_ordinal);
    snapshot->obj_field_put(_blockerObject_offset, lock);
    snapshot->obj_field_put(_parkBlockerOwner_offset, owner);
  }
};

bool jdk_internal_vm_ThreadSnapshot::_inited = false;
int jdk_internal_vm_ThreadSnapshot::_name_offset;
int jdk_internal_vm_ThreadSnapshot::_threadStatus_offset;
int jdk_internal_vm_ThreadSnapshot::_carrierThread_offset;
int jdk_internal_vm_ThreadSnapshot::_stackTrace_offset;
int jdk_internal_vm_ThreadSnapshot::_locks_offset;
int jdk_internal_vm_ThreadSnapshot::_blockerTypeOrdinal_offset;
int jdk_internal_vm_ThreadSnapshot::_blockerObject_offset;
int jdk_internal_vm_ThreadSnapshot::_parkBlockerOwner_offset;

oop ThreadSnapshotFactory::get_thread_snapshot(jobject jthread, TRAPS) {
  ThreadsListHandle tlh(THREAD);

  ResourceMark rm(THREAD);
  HandleMark   hm(THREAD);

  JavaThread* java_thread = nullptr;
  oop thread_oop = nullptr;
  bool has_javathread = tlh.cv_internal_thread_to_JavaThread(jthread, &java_thread, &thread_oop);
  assert(thread_oop != nullptr, "Missing Thread oop");
  bool is_virtual = java_lang_VirtualThread::is_instance(thread_oop);  // Deals with null

  if (!has_javathread && !is_virtual) {
    return nullptr; // platform thread terminated
  }

  // Handshake with target
  Handle thread_h(THREAD, thread_oop);
  GetThreadSnapshotHandshakeClosure cl(thread_h);
  if (java_lang_VirtualThread::is_instance(thread_oop)) {
    Handshake::execute(&cl, thread_oop);
  } else {
    Handshake::execute(&cl, &tlh, java_thread);
  }

  assert(cl._thread_status != JavaThreadStatus::NEW, "unstarted Thread");
  if (is_virtual && (cl._thread_status == JavaThreadStatus::TERMINATED)) {
    return nullptr; // virtual thread terminated
  }

  // StackTrace
  InstanceKlass* ste_klass = vmClasses::StackTraceElement_klass();
  assert(ste_klass != nullptr, "must be loaded");

  objArrayHandle trace = oopFactory::new_objArray_handle(ste_klass, cl._frame_count, CHECK_NULL);

  for (int i = 0; i < cl._frame_count; i++) {
    methodHandle method(THREAD, cl._methods->at(i));
    oop element = java_lang_StackTraceElement::create(method, cl._bcis->at(i), CHECK_NULL);
    trace->obj_at_put(i, element);
  }

  // Locks
  Symbol* lock_sym = vmSymbols::jdk_internal_vm_ThreadLock();
  Klass* lock_k = SystemDictionary::resolve_or_fail(lock_sym, true, CHECK_NULL);
  InstanceKlass* lock_klass = InstanceKlass::cast(lock_k);

  objArrayHandle locks;
  if (cl._locks != nullptr && cl._locks->length() > 0) {
    locks = oopFactory::new_objArray_handle(lock_klass, cl._locks->length(), CHECK_NULL);
    for (int n = 0; n < cl._locks->length(); n++) {
      GetThreadSnapshotHandshakeClosure::OwnedLock* lock_info = cl._locks->adr_at(n);

      Handle lock = jdk_internal_vm_ThreadLock::create(lock_klass,
        lock_info->_frame_depth, lock_info->_type, lock_info->_obj, CHECK_NULL);
      locks->obj_at_put(n, lock());
    }
  }

  Symbol* snapshot_klass_name = vmSymbols::jdk_internal_vm_ThreadSnapshot();
  Klass* snapshot_klass = SystemDictionary::resolve_or_fail(snapshot_klass_name, true, CHECK_NULL);
  if (snapshot_klass->should_be_initialized()) {
    snapshot_klass->initialize(CHECK_NULL);
  }

  Handle snapshot = jdk_internal_vm_ThreadSnapshot::allocate(InstanceKlass::cast(snapshot_klass), CHECK_NULL);
  jdk_internal_vm_ThreadSnapshot::set_name(snapshot(), cl._thread_name.resolve());
  jdk_internal_vm_ThreadSnapshot::set_thread_status(snapshot(), (int)cl._thread_status);
  jdk_internal_vm_ThreadSnapshot::set_carrier_thread(snapshot(), cl._carrier_thread.resolve());
  jdk_internal_vm_ThreadSnapshot::set_stack_trace(snapshot(), trace());
  jdk_internal_vm_ThreadSnapshot::set_locks(snapshot(), locks());
  if (!cl._blocker.is_empty()) {
    jdk_internal_vm_ThreadSnapshot::set_blocker(snapshot(),
        cl._blocker._type, cl._blocker._obj.resolve(), cl._blocker._owner.resolve());
  }
  return snapshot();
}

/*
 * Copyright 2003-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

# include "incls/_precompiled.incl"
# include "incls/_threadService.cpp.incl"

// TODO: we need to define a naming convention for perf counters
// to distinguish counters for:
//   - standard JSR174 use
//   - Hotspot extension (public and committed)
//   - Hotspot extension (private/internal and uncommitted)

// Default is disabled.
bool ThreadService::_thread_monitoring_contention_enabled = false;
bool ThreadService::_thread_cpu_time_enabled = false;

PerfCounter*  ThreadService::_total_threads_count = NULL;
PerfVariable* ThreadService::_live_threads_count = NULL;
PerfVariable* ThreadService::_peak_threads_count = NULL;
PerfVariable* ThreadService::_daemon_threads_count = NULL;
volatile int ThreadService::_exiting_threads_count = 0;
volatile int ThreadService::_exiting_daemon_threads_count = 0;

ThreadDumpResult* ThreadService::_threaddump_list = NULL;

static const int INITIAL_ARRAY_SIZE = 10;

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
}

void ThreadService::reset_peak_thread_count() {
  // Acquire the lock to update the peak thread count
  // to synchronize with thread addition and removal.
  MutexLockerEx mu(Threads_lock);
  _peak_threads_count->set_value(get_live_thread_count());
}

void ThreadService::add_thread(JavaThread* thread, bool daemon) {
  // Do not count VM internal or JVMTI agent threads
  if (thread->is_hidden_from_external_view() ||
      thread->is_jvmti_agent_thread()) {
    return;
  }

  _total_threads_count->inc();
  _live_threads_count->inc();

  if (_live_threads_count->get_value() > _peak_threads_count->get_value()) {
    _peak_threads_count->set_value(_live_threads_count->get_value());
  }

  if (daemon) {
    _daemon_threads_count->inc();
  }
}

void ThreadService::remove_thread(JavaThread* thread, bool daemon) {
  Atomic::dec((jint*) &_exiting_threads_count);

  if (thread->is_hidden_from_external_view() ||
      thread->is_jvmti_agent_thread()) {
    return;
  }

  _live_threads_count->set_value(_live_threads_count->get_value() - 1);

  if (daemon) {
    _daemon_threads_count->set_value(_daemon_threads_count->get_value() - 1);
    Atomic::dec((jint*) &_exiting_daemon_threads_count);
  }
}

void ThreadService::current_thread_exiting(JavaThread* jt) {
  assert(jt == JavaThread::current(), "Called by current thread");
  Atomic::inc((jint*) &_exiting_threads_count);

  oop threadObj = jt->threadObj();
  if (threadObj != NULL && java_lang_Thread::is_daemon(threadObj)) {
    Atomic::inc((jint*) &_exiting_daemon_threads_count);
  }
}

// FIXME: JVMTI should call this function
Handle ThreadService::get_current_contended_monitor(JavaThread* thread) {
  assert(thread != NULL, "should be non-NULL");
  assert(Threads_lock->owned_by_self(), "must grab Threads_lock or be at safepoint");

  ObjectMonitor *wait_obj = thread->current_waiting_monitor();

  oop obj = NULL;
  if (wait_obj != NULL) {
    // thread is doing an Object.wait() call
    obj = (oop) wait_obj->object();
    assert(obj != NULL, "Object.wait() should have an object");
  } else {
    ObjectMonitor *enter_obj = thread->current_pending_monitor();
    if (enter_obj != NULL) {
      // thread is trying to enter() or raw_enter() an ObjectMonitor.
      obj = (oop) enter_obj->object();
    }
    // If obj == NULL, then ObjectMonitor is raw which doesn't count.
  }

  Handle h(obj);
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

// GC support
void ThreadService::oops_do(OopClosure* f) {
  for (ThreadDumpResult* dump = _threaddump_list; dump != NULL; dump = dump->next()) {
    dump->oops_do(f);
  }
}

void ThreadService::add_thread_dump(ThreadDumpResult* dump) {
  MutexLocker ml(Management_lock);
  if (_threaddump_list == NULL) {
    _threaddump_list = dump;
  } else {
    dump->set_next(_threaddump_list);
    _threaddump_list = dump;
  }
}

void ThreadService::remove_thread_dump(ThreadDumpResult* dump) {
  MutexLocker ml(Management_lock);

  ThreadDumpResult* prev = NULL;
  bool found = false;
  for (ThreadDumpResult* d = _threaddump_list; d != NULL; prev = d, d = d->next()) {
    if (d == dump) {
      if (prev == NULL) {
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
  klassOop k = SystemDictionary::resolve_or_fail(vmSymbolHandles::java_lang_StackTraceElement_array(), true, CHECK_NH);
  objArrayKlassHandle ik (THREAD, k);
  objArrayOop r = oopFactory::new_objArray(ik(), num_threads, CHECK_NH);
  objArrayHandle result_obj(THREAD, r);

  int num_snapshots = dump_result.num_snapshots();
  assert(num_snapshots == num_threads, "Must have num_threads thread snapshots");
  int i = 0;
  for (ThreadSnapshot* ts = dump_result.snapshots(); ts != NULL; i++, ts = ts->next()) {
    ThreadStackTrace* stacktrace = ts->get_stack_trace();
    if (stacktrace == NULL) {
      // No stack trace
      result_obj->obj_at_put(i, NULL);
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
  if (stat != NULL) {
    stat->reset_count_stat();
  }
}

void ThreadService::reset_contention_time_stat(JavaThread* thread) {
  ThreadStatistics* stat = thread->get_thread_stat();
  if (stat != NULL) {
    stat->reset_time_stat();
  }
}

// Find deadlocks involving object monitors and concurrent locks if concurrent_locks is true
DeadlockCycle* ThreadService::find_deadlocks_at_safepoint(bool concurrent_locks) {
  // This code was modified from the original Threads::find_deadlocks code.
  int globalDfn = 0, thisDfn;
  ObjectMonitor* waitingToLockMonitor = NULL;
  oop waitingToLockBlocker = NULL;
  bool blocked_on_monitor = false;
  JavaThread *currentThread, *previousThread;
  int num_deadlocks = 0;

  for (JavaThread* p = Threads::first(); p != NULL; p = p->next()) {
    // Initialize the depth-first-number
    p->set_depth_first_number(-1);
  }

  DeadlockCycle* deadlocks = NULL;
  DeadlockCycle* last = NULL;
  DeadlockCycle* cycle = new DeadlockCycle();
  for (JavaThread* jt = Threads::first(); jt != NULL; jt = jt->next()) {
    if (jt->depth_first_number() >= 0) {
      // this thread was already visited
      continue;
    }

    thisDfn = globalDfn;
    jt->set_depth_first_number(globalDfn++);
    previousThread = jt;
    currentThread = jt;

    cycle->reset();

    // When there is a deadlock, all the monitors involved in the dependency
    // cycle must be contended and heavyweight. So we only care about the
    // heavyweight monitor a thread is waiting to lock.
    waitingToLockMonitor = (ObjectMonitor*)jt->current_pending_monitor();
    if (concurrent_locks) {
      waitingToLockBlocker = jt->current_park_blocker();
    }
    while (waitingToLockMonitor != NULL || waitingToLockBlocker != NULL) {
      cycle->add_thread(currentThread);
      if (waitingToLockMonitor != NULL) {
        currentThread = Threads::owning_thread_from_monitor_owner((address)waitingToLockMonitor->owner(),
                                                                  false /* no locking needed */);
      } else {
        if (concurrent_locks) {
          if (waitingToLockBlocker->is_a(SystemDictionary::abstract_ownable_synchronizer_klass())) {
            oop threadObj = java_util_concurrent_locks_AbstractOwnableSynchronizer::get_owner_threadObj(waitingToLockBlocker);
            currentThread = threadObj != NULL ? java_lang_Thread::thread(threadObj) : NULL;
          } else {
            currentThread = NULL;
          }
        }
      }

      if (currentThread == NULL) {
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

        cycle->set_deadlock(true);

        // add this cycle to the deadlocks list
        if (deadlocks == NULL) {
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

  return deadlocks;
}

ThreadDumpResult::ThreadDumpResult() : _num_threads(0), _num_snapshots(0), _snapshots(NULL), _next(NULL), _last(NULL) {

  // Create a new ThreadDumpResult object and append to the list.
  // If GC happens before this function returns, methodOop
  // in the stack trace will be visited.
  ThreadService::add_thread_dump(this);
}

ThreadDumpResult::ThreadDumpResult(int num_threads) : _num_threads(num_threads), _num_snapshots(0), _snapshots(NULL), _next(NULL), _last(NULL) {
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
  while (ts != NULL) {
    ThreadSnapshot* p = ts;
    ts = ts->next();
    delete p;
  }
}


void ThreadDumpResult::add_thread_snapshot(ThreadSnapshot* ts) {
  assert(_num_threads == 0 || _num_snapshots < _num_threads,
         "_num_snapshots must be less than _num_threads");
  _num_snapshots++;
  if (_snapshots == NULL) {
    _snapshots = ts;
  } else {
    _last->set_next(ts);
  }
  _last = ts;
}

void ThreadDumpResult::oops_do(OopClosure* f) {
  for (ThreadSnapshot* ts = _snapshots; ts != NULL; ts = ts->next()) {
    ts->oops_do(f);
  }
}

StackFrameInfo::StackFrameInfo(javaVFrame* jvf, bool with_lock_info) {
  _method = jvf->method();
  _bci = jvf->bci();
  _locked_monitors = NULL;
  if (with_lock_info) {
    ResourceMark rm;
    GrowableArray<MonitorInfo*>* list = jvf->locked_monitors();
    int length = list->length();
    if (length > 0) {
      _locked_monitors = new (ResourceObj::C_HEAP) GrowableArray<oop>(length, true);
      for (int i = 0; i < length; i++) {
        MonitorInfo* monitor = list->at(i);
        assert(monitor->owner(), "This monitor must have an owning object");
        _locked_monitors->append(monitor->owner());
      }
    }
  }
}

void StackFrameInfo::oops_do(OopClosure* f) {
  f->do_oop((oop*) &_method);
  if (_locked_monitors != NULL) {
    int length = _locked_monitors->length();
    for (int i = 0; i < length; i++) {
      f->do_oop((oop*) _locked_monitors->adr_at(i));
    }
  }
}

void StackFrameInfo::print_on(outputStream* st) const {
  ResourceMark rm;
  java_lang_Throwable::print_stack_element(st, method(), bci());
  int len = (_locked_monitors != NULL ? _locked_monitors->length() : 0);
  for (int i = 0; i < len; i++) {
    oop o = _locked_monitors->at(i);
    instanceKlass* ik = instanceKlass::cast(o->klass());
    st->print_cr("\t- locked <" INTPTR_FORMAT "> (a %s)", (address)o, ik->external_name());
  }

}

// Iterate through monitor cache to find JNI locked monitors
class InflatedMonitorsClosure: public MonitorClosure {
private:
  ThreadStackTrace* _stack_trace;
  Thread* _thread;
public:
  InflatedMonitorsClosure(Thread* t, ThreadStackTrace* st) {
    _thread = t;
    _stack_trace = st;
  }
  void do_monitor(ObjectMonitor* mid) {
    if (mid->owner() == _thread) {
      oop object = (oop) mid->object();
      if (!_stack_trace->is_owned_monitor_on_stack(object)) {
        _stack_trace->add_jni_locked_monitor(object);
      }
    }
  }
};

ThreadStackTrace::ThreadStackTrace(JavaThread* t, bool with_locked_monitors) {
  _thread = t;
  _frames = new (ResourceObj::C_HEAP) GrowableArray<StackFrameInfo*>(INITIAL_ARRAY_SIZE, true);
  _depth = 0;
  _with_locked_monitors = with_locked_monitors;
  if (_with_locked_monitors) {
    _jni_locked_monitors = new (ResourceObj::C_HEAP) GrowableArray<oop>(INITIAL_ARRAY_SIZE, true);
  } else {
    _jni_locked_monitors = NULL;
  }
}

ThreadStackTrace::~ThreadStackTrace() {
  for (int i = 0; i < _frames->length(); i++) {
    delete _frames->at(i);
  }
  delete _frames;
  if (_jni_locked_monitors != NULL) {
    delete _jni_locked_monitors;
  }
}

void ThreadStackTrace::dump_stack_at_safepoint(int maxDepth) {
  assert(SafepointSynchronize::is_at_safepoint(), "all threads are stopped");

  if (_thread->has_last_Java_frame()) {
    RegisterMap reg_map(_thread);
    vframe* start_vf = _thread->last_java_vframe(&reg_map);
    int count = 0;
    for (vframe* f = start_vf; f; f = f->sender() ) {
      if (f->is_java_frame()) {
        javaVFrame* jvf = javaVFrame::cast(f);
        add_stack_frame(jvf);
        count++;
      } else {
        // Ignore non-Java frames
      }
      if (maxDepth > 0 && count == maxDepth) {
        // Skip frames if more than maxDepth
        break;
      }
    }
  }

  if (_with_locked_monitors) {
    // Iterate inflated monitors and find monitors locked by this thread
    // not found in the stack
    InflatedMonitorsClosure imc(_thread, this);
    ObjectSynchronizer::monitors_iterate(&imc);
  }
}


bool ThreadStackTrace::is_owned_monitor_on_stack(oop object) {
  assert(SafepointSynchronize::is_at_safepoint(), "all threads are stopped");

  bool found = false;
  int num_frames = get_stack_depth();
  for (int depth = 0; depth < num_frames; depth++) {
    StackFrameInfo* frame = stack_frame_at(depth);
    int len = frame->num_locked_monitors();
    GrowableArray<oop>* locked_monitors = frame->locked_monitors();
    for (int j = 0; j < len; j++) {
      oop monitor = locked_monitors->at(j);
      assert(monitor != NULL && monitor->is_instance(), "must be a Java object");
      if (monitor == object) {
        found = true;
        break;
      }
    }
  }
  return found;
}

Handle ThreadStackTrace::allocate_fill_stack_trace_element_array(TRAPS) {
  klassOop k = SystemDictionary::StackTraceElement_klass();
  assert(k != NULL, "must be loaded in 1.4+");
  instanceKlassHandle ik(THREAD, k);

  // Allocate an array of java/lang/StackTraceElement object
  objArrayOop ste = oopFactory::new_objArray(ik(), _depth, CHECK_NH);
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

void ThreadStackTrace::oops_do(OopClosure* f) {
  int length = _frames->length();
  for (int i = 0; i < length; i++) {
    _frames->at(i)->oops_do(f);
  }

  length = (_jni_locked_monitors != NULL ? _jni_locked_monitors->length() : 0);
  for (int j = 0; j < length; j++) {
    f->do_oop((oop*) _jni_locked_monitors->adr_at(j));
  }
}

ConcurrentLocksDump::~ConcurrentLocksDump() {
  if (_retain_map_on_free) {
    return;
  }

  for (ThreadConcurrentLocks* t = _map; t != NULL;)  {
    ThreadConcurrentLocks* tcl = t;
    t = t->next();
    delete tcl;
  }
}

void ConcurrentLocksDump::dump_at_safepoint() {
  // dump all locked concurrent locks
  assert(SafepointSynchronize::is_at_safepoint(), "all threads are stopped");

  if (JDK_Version::is_gte_jdk16x_version()) {
    ResourceMark rm;

    GrowableArray<oop>* aos_objects = new GrowableArray<oop>(INITIAL_ARRAY_SIZE);

    // Find all instances of AbstractOwnableSynchronizer
    HeapInspection::find_instances_at_safepoint(SystemDictionary::abstract_ownable_synchronizer_klass(),
                                                aos_objects);
    // Build a map of thread to its owned AQS locks
    build_map(aos_objects);
  }
}


// build a map of JavaThread to all its owned AbstractOwnableSynchronizer
void ConcurrentLocksDump::build_map(GrowableArray<oop>* aos_objects) {
  int length = aos_objects->length();
  for (int i = 0; i < length; i++) {
    oop o = aos_objects->at(i);
    oop owner_thread_obj = java_util_concurrent_locks_AbstractOwnableSynchronizer::get_owner_threadObj(o);
    if (owner_thread_obj != NULL) {
      JavaThread* thread = java_lang_Thread::thread(owner_thread_obj);
      assert(o->is_instance(), "Must be an instanceOop");
      add_lock(thread, (instanceOop) o);
    }
  }
}

void ConcurrentLocksDump::add_lock(JavaThread* thread, instanceOop o) {
  ThreadConcurrentLocks* tcl = thread_concurrent_locks(thread);
  if (tcl != NULL) {
    tcl->add_lock(o);
    return;
  }

  // First owned lock found for this thread
  tcl = new ThreadConcurrentLocks(thread);
  tcl->add_lock(o);
  if (_map == NULL) {
    _map = tcl;
  } else {
    _last->set_next(tcl);
  }
  _last = tcl;
}

ThreadConcurrentLocks* ConcurrentLocksDump::thread_concurrent_locks(JavaThread* thread) {
  for (ThreadConcurrentLocks* tcl = _map; tcl != NULL; tcl = tcl->next()) {
    if (tcl->java_thread() == thread) {
      return tcl;
    }
  }
  return NULL;
}

void ConcurrentLocksDump::print_locks_on(JavaThread* t, outputStream* st) {
  st->print_cr("   Locked ownable synchronizers:");
  ThreadConcurrentLocks* tcl = thread_concurrent_locks(t);
  GrowableArray<instanceOop>* locks = (tcl != NULL ? tcl->owned_locks() : NULL);
  if (locks == NULL || locks->is_empty()) {
    st->print_cr("\t- None");
    st->cr();
    return;
  }

  for (int i = 0; i < locks->length(); i++) {
    instanceOop obj = locks->at(i);
    instanceKlass* ik = instanceKlass::cast(obj->klass());
    st->print_cr("\t- <" INTPTR_FORMAT "> (a %s)", (address)obj, ik->external_name());
  }
  st->cr();
}

ThreadConcurrentLocks::ThreadConcurrentLocks(JavaThread* thread) {
  _thread = thread;
  _owned_locks = new (ResourceObj::C_HEAP) GrowableArray<instanceOop>(INITIAL_ARRAY_SIZE, true);
  _next = NULL;
}

ThreadConcurrentLocks::~ThreadConcurrentLocks() {
  delete _owned_locks;
}

void ThreadConcurrentLocks::add_lock(instanceOop o) {
  _owned_locks->append(o);
}

void ThreadConcurrentLocks::oops_do(OopClosure* f) {
  int length = _owned_locks->length();
  for (int i = 0; i < length; i++) {
    f->do_oop((oop*) _owned_locks->adr_at(i));
  }
}

ThreadStatistics::ThreadStatistics() {
  _contended_enter_count = 0;
  _monitor_wait_count = 0;
  _sleep_count = 0;
  _count_pending_reset = false;
  _timer_pending_reset = false;
  memset((void*) _perf_recursion_counts, 0, sizeof(_perf_recursion_counts));
}

ThreadSnapshot::ThreadSnapshot(JavaThread* thread) {
  _thread = thread;
  _threadObj = thread->threadObj();
  _stack_trace = NULL;
  _concurrent_locks = NULL;
  _next = NULL;

  ThreadStatistics* stat = thread->get_thread_stat();
  _contended_enter_ticks = stat->contended_enter_ticks();
  _contended_enter_count = stat->contended_enter_count();
  _monitor_wait_ticks = stat->monitor_wait_ticks();
  _monitor_wait_count = stat->monitor_wait_count();
  _sleep_ticks = stat->sleep_ticks();
  _sleep_count = stat->sleep_count();

  _blocker_object = NULL;
  _blocker_object_owner = NULL;

  _thread_status = java_lang_Thread::get_thread_status(_threadObj);
  _is_ext_suspended = thread->is_being_ext_suspended();
  _is_in_native = (thread->thread_state() == _thread_in_native);

  if (_thread_status == java_lang_Thread::BLOCKED_ON_MONITOR_ENTER ||
      _thread_status == java_lang_Thread::IN_OBJECT_WAIT ||
      _thread_status == java_lang_Thread::IN_OBJECT_WAIT_TIMED) {

    Handle obj = ThreadService::get_current_contended_monitor(thread);
    if (obj() == NULL) {
      // monitor no longer exists; thread is not blocked
      _thread_status = java_lang_Thread::RUNNABLE;
    } else {
      _blocker_object = obj();
      JavaThread* owner = ObjectSynchronizer::get_lock_owner(obj, false);
      if ((owner == NULL && _thread_status == java_lang_Thread::BLOCKED_ON_MONITOR_ENTER)
          || (owner != NULL && owner->is_attaching())) {
        // ownership information of the monitor is not available
        // (may no longer be owned or releasing to some other thread)
        // make this thread in RUNNABLE state.
        // And when the owner thread is in attaching state, the java thread
        // is not completely initialized. For example thread name and id
        // and may not be set, so hide the attaching thread.
        _thread_status = java_lang_Thread::RUNNABLE;
        _blocker_object = NULL;
      } else if (owner != NULL) {
        _blocker_object_owner = owner->threadObj();
      }
    }
  }

  // Support for JSR-166 locks
  if (JDK_Version::current().supports_thread_park_blocker() &&
        (_thread_status == java_lang_Thread::PARKED ||
         _thread_status == java_lang_Thread::PARKED_TIMED)) {

    _blocker_object = thread->current_park_blocker();
    if (_blocker_object != NULL && _blocker_object->is_a(SystemDictionary::abstract_ownable_synchronizer_klass())) {
      _blocker_object_owner = java_util_concurrent_locks_AbstractOwnableSynchronizer::get_owner_threadObj(_blocker_object);
    }
  }
}

ThreadSnapshot::~ThreadSnapshot() {
  delete _stack_trace;
  delete _concurrent_locks;
}

void ThreadSnapshot::dump_stack_at_safepoint(int max_depth, bool with_locked_monitors) {
  _stack_trace = new ThreadStackTrace(_thread, with_locked_monitors);
  _stack_trace->dump_stack_at_safepoint(max_depth);
}


void ThreadSnapshot::oops_do(OopClosure* f) {
  f->do_oop(&_threadObj);
  f->do_oop(&_blocker_object);
  f->do_oop(&_blocker_object_owner);
  if (_stack_trace != NULL) {
    _stack_trace->oops_do(f);
  }
  if (_concurrent_locks != NULL) {
    _concurrent_locks->oops_do(f);
  }
}

DeadlockCycle::DeadlockCycle() {
  _is_deadlock = false;
  _threads = new (ResourceObj::C_HEAP) GrowableArray<JavaThread*>(INITIAL_ARRAY_SIZE, true);
  _next = NULL;
}

DeadlockCycle::~DeadlockCycle() {
  delete _threads;
}

void DeadlockCycle::print_on(outputStream* st) const {
  st->cr();
  st->print_cr("Found one Java-level deadlock:");
  st->print("=============================");

  JavaThread* currentThread;
  ObjectMonitor* waitingToLockMonitor;
  oop waitingToLockBlocker;
  int len = _threads->length();
  for (int i = 0; i < len; i++) {
    currentThread = _threads->at(i);
    waitingToLockMonitor = (ObjectMonitor*)currentThread->current_pending_monitor();
    waitingToLockBlocker = currentThread->current_park_blocker();
    st->cr();
    st->print_cr("\"%s\":", currentThread->get_thread_name());
    const char* owner_desc = ",\n  which is held by";
    if (waitingToLockMonitor != NULL) {
      st->print("  waiting to lock monitor " INTPTR_FORMAT, waitingToLockMonitor);
      oop obj = (oop)waitingToLockMonitor->object();
      if (obj != NULL) {
        st->print(" (object "INTPTR_FORMAT ", a %s)", (address)obj,
                   (instanceKlass::cast(obj->klass()))->external_name());

        if (!currentThread->current_pending_monitor_is_from_java()) {
          owner_desc = "\n  in JNI, which is held by";
        }
      } else {
        // No Java object associated - a JVMTI raw monitor
        owner_desc = " (JVMTI raw monitor),\n  which is held by";
      }
      currentThread = Threads::owning_thread_from_monitor_owner(
        (address)waitingToLockMonitor->owner(), false /* no locking needed */);
    } else {
      st->print("  waiting for ownable synchronizer " INTPTR_FORMAT ", (a %s)",
                (address)waitingToLockBlocker,
                (instanceKlass::cast(waitingToLockBlocker->klass()))->external_name());
      assert(waitingToLockBlocker->is_a(SystemDictionary::abstract_ownable_synchronizer_klass()),
             "Must be an AbstractOwnableSynchronizer");
      oop ownerObj = java_util_concurrent_locks_AbstractOwnableSynchronizer::get_owner_threadObj(waitingToLockBlocker);
      currentThread = java_lang_Thread::thread(ownerObj);
    }
    st->print("%s \"%s\"", owner_desc, currentThread->get_thread_name());
  }

  st->cr();
  st->cr();

  // Print stack traces
  bool oldJavaMonitorsInStackTrace = JavaMonitorsInStackTrace;
  JavaMonitorsInStackTrace = true;
  st->print_cr("Java stack information for the threads listed above:");
  st->print_cr("===================================================");
  for (int j = 0; j < len; j++) {
    currentThread = _threads->at(j);
    st->print_cr("\"%s\":", currentThread->get_thread_name());
    currentThread->print_stack_on(st);
  }
  JavaMonitorsInStackTrace = oldJavaMonitorsInStackTrace;
}

ThreadsListEnumerator::ThreadsListEnumerator(Thread* cur_thread,
                                             bool include_jvmti_agent_threads,
                                             bool include_jni_attaching_threads) {
  assert(cur_thread == Thread::current(), "Check current thread");

  int init_size = ThreadService::get_live_thread_count();
  _threads_array = new GrowableArray<instanceHandle>(init_size);

  MutexLockerEx ml(Threads_lock);

  for (JavaThread* jt = Threads::first(); jt != NULL; jt = jt->next()) {
    // skips JavaThreads in the process of exiting
    // and also skips VM internal JavaThreads
    // Threads in _thread_new or _thread_new_trans state are included.
    // i.e. threads have been started but not yet running.
    if (jt->threadObj() == NULL   ||
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
    if (!include_jni_attaching_threads && jt->is_attaching()) {
      continue;
    }

    instanceHandle h(cur_thread, (instanceOop) jt->threadObj());
    _threads_array->append(h);
  }
}

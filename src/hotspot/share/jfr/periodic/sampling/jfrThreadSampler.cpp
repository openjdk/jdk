/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "jfr/jfrEvents.hpp"
#include "jfr/recorder/jfrRecorder.hpp"
#include "jfr/periodic/sampling/jfrCallTrace.hpp"
#include "jfr/periodic/sampling/jfrThreadSampler.hpp"
#include "jfr/recorder/service/jfrOptionSet.hpp"
#include "jfr/recorder/stacktrace/jfrStackTraceRepository.hpp"
#include "jfr/support/jfrThreadId.hpp"
#include "jfr/support/jfrThreadLocal.hpp"
#include "jfr/utilities/jfrTime.hpp"
#include "jfrfiles/jfrEventClasses.hpp"
#include "logging/log.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/os.hpp"
#include "runtime/semaphore.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/threadSMR.hpp"

enum JfrSampleType {
  NO_SAMPLE = 0,
  JAVA_SAMPLE = 1,
  NATIVE_SAMPLE = 2
};

static bool thread_state_in_java(JavaThread* thread) {
  assert(thread != NULL, "invariant");
  switch(thread->thread_state()) {
    case _thread_new:
    case _thread_uninitialized:
    case _thread_new_trans:
    case _thread_in_vm_trans:
    case _thread_blocked_trans:
    case _thread_in_native_trans:
    case _thread_blocked:
    case _thread_in_vm:
    case _thread_in_native:
    case _thread_in_Java_trans:
      break;
    case _thread_in_Java:
      return true;
    default:
      ShouldNotReachHere();
      break;
  }
  return false;
}

static bool thread_state_in_native(JavaThread* thread) {
  assert(thread != NULL, "invariant");
  switch(thread->thread_state()) {
    case _thread_new:
    case _thread_uninitialized:
    case _thread_new_trans:
    case _thread_blocked_trans:
    case _thread_blocked:
    case _thread_in_vm:
    case _thread_in_vm_trans:
    case _thread_in_Java_trans:
    case _thread_in_Java:
    case _thread_in_native_trans:
      break;
    case _thread_in_native:
      return true;
    default:
      ShouldNotReachHere();
      break;
  }
  return false;
}

class JfrThreadSampleClosure {
 public:
  JfrThreadSampleClosure(EventExecutionSample* events, EventNativeMethodSample* events_native);
  ~JfrThreadSampleClosure() {}
  EventExecutionSample* next_event() { return &_events[_added_java++]; }
  EventNativeMethodSample* next_event_native() { return &_events_native[_added_native++]; }
  void commit_events(JfrSampleType type);
  bool do_sample_thread(JavaThread* thread, JfrStackFrame* frames, u4 max_frames, JfrSampleType type);
  uint java_entries() { return _added_java; }
  uint native_entries() { return _added_native; }

 private:
  bool sample_thread_in_java(JavaThread* thread, JfrStackFrame* frames, u4 max_frames);
  bool sample_thread_in_native(JavaThread* thread, JfrStackFrame* frames, u4 max_frames);
  EventExecutionSample* _events;
  EventNativeMethodSample* _events_native;
  Thread* _self;
  uint _added_java;
  uint _added_native;
};

class OSThreadSampler : public os::SuspendedThreadTask {
 public:
  OSThreadSampler(JavaThread* thread,
                  JfrThreadSampleClosure& closure,
                  JfrStackFrame *frames,
                  u4 max_frames) : os::SuspendedThreadTask((Thread*)thread),
    _success(false),
    _thread_oop(thread->threadObj()),
    _stacktrace(frames, max_frames),
    _closure(closure),
    _suspend_time() {}

  void take_sample();
  void do_task(const os::SuspendedThreadTaskContext& context);
  void protected_task(const os::SuspendedThreadTaskContext& context);
  bool success() const { return _success; }
  const JfrStackTrace& stacktrace() const { return _stacktrace; }

 private:
  bool _success;
  oop _thread_oop;
  JfrStackTrace _stacktrace;
  JfrThreadSampleClosure& _closure;
  JfrTicks _suspend_time;
};

class OSThreadSamplerCallback : public os::CrashProtectionCallback {
 public:
  OSThreadSamplerCallback(OSThreadSampler& sampler, const os::SuspendedThreadTaskContext &context) :
    _sampler(sampler), _context(context) {
  }
  virtual void call() {
    _sampler.protected_task(_context);
  }
 private:
  OSThreadSampler& _sampler;
  const os::SuspendedThreadTaskContext& _context;
};

void OSThreadSampler::do_task(const os::SuspendedThreadTaskContext& context) {
#ifndef ASSERT
  guarantee(JfrOptionSet::sample_protection(), "Sample Protection should be on in product builds");
#endif
  assert(_suspend_time.value() == 0, "already timestamped!");
  _suspend_time = JfrTicks::now();

  if (JfrOptionSet::sample_protection()) {
    OSThreadSamplerCallback cb(*this, context);
    os::ThreadCrashProtection crash_protection;
    if (!crash_protection.call(cb)) {
      log_error(jfr)("Thread method sampler crashed");
    }
  } else {
    protected_task(context);
  }
}

/*
* From this method and down the call tree we attempt to protect against crashes
* using a signal handler / __try block. Don't take locks, rely on destructors or
* leave memory (in case of signal / exception) in an inconsistent state. */
void OSThreadSampler::protected_task(const os::SuspendedThreadTaskContext& context) {
  JavaThread* jth = context.thread()->as_Java_thread();
  // Skip sample if we signaled a thread that moved to other state
  if (!thread_state_in_java(jth)) {
    return;
  }
  JfrGetCallTrace trace(true, jth);
  frame topframe;
  if (trace.get_topframe(context.ucontext(), topframe)) {
    if (_stacktrace.record_thread(*jth, topframe)) {
      /* If we managed to get a topframe and a stacktrace, create an event
      * and put it into our array. We can't call Jfr::_stacktraces.add()
      * here since it would allocate memory using malloc. Doing so while
      * the stopped thread is inside malloc would deadlock. */
      _success = true;
      EventExecutionSample *ev = _closure.next_event();
      ev->set_starttime(_suspend_time);
      ev->set_endtime(_suspend_time); // fake to not take an end time
      ev->set_sampledThread(JFR_THREAD_ID(jth));
      ev->set_state(static_cast<u8>(java_lang_Thread::get_thread_status(_thread_oop)));
    }
  }
}

void OSThreadSampler::take_sample() {
  run();
}

class JfrNativeSamplerCallback : public os::CrashProtectionCallback {
 public:
  JfrNativeSamplerCallback(JfrThreadSampleClosure& closure, JavaThread* jt, JfrStackFrame* frames, u4 max_frames) :
    _closure(closure), _jt(jt), _thread_oop(jt->threadObj()), _stacktrace(frames, max_frames), _success(false) {
  }
  virtual void call();
  bool success() { return _success; }
  JfrStackTrace& stacktrace() { return _stacktrace; }

 private:
  JfrThreadSampleClosure& _closure;
  JavaThread* _jt;
  oop _thread_oop;
  JfrStackTrace _stacktrace;
  bool _success;
};

static void write_native_event(JfrThreadSampleClosure& closure, JavaThread* jt, oop thread_oop) {
  EventNativeMethodSample *ev = closure.next_event_native();
  ev->set_starttime(JfrTicks::now());
  ev->set_sampledThread(JFR_THREAD_ID(jt));
  ev->set_state(static_cast<u8>(java_lang_Thread::get_thread_status(thread_oop)));
}

void JfrNativeSamplerCallback::call() {
  // When a thread is only attach it will be native without a last java frame
  if (!_jt->has_last_Java_frame()) {
    return;
  }

  frame topframe = _jt->last_frame();
  frame first_java_frame;
  Method* method = NULL;
  JfrGetCallTrace gct(false, _jt);
  if (!gct.find_top_frame(topframe, &method, first_java_frame)) {
    return;
  }
  if (method == NULL) {
    return;
  }
  topframe = first_java_frame;
  _success = _stacktrace.record_thread(*_jt, topframe);
  if (_success) {
    write_native_event(_closure, _jt, _thread_oop);
  }
}

bool JfrThreadSampleClosure::sample_thread_in_java(JavaThread* thread, JfrStackFrame* frames, u4 max_frames) {
  OSThreadSampler sampler(thread, *this, frames, max_frames);
  sampler.take_sample();
  /* We don't want to allocate any memory using malloc/etc while the thread
  * is stopped, so everything is stored in stack allocated memory until this
  * point where the thread has been resumed again, if the sampling was a success
  * we need to store the stacktrace in the stacktrace repository and update
  * the event with the id that was returned. */
  if (!sampler.success()) {
    return false;
  }
  EventExecutionSample *event = &_events[_added_java - 1];
  traceid id = JfrStackTraceRepository::add(sampler.stacktrace());
  assert(id != 0, "Stacktrace id should not be 0");
  event->set_stackTrace(id);
  return true;
}

bool JfrThreadSampleClosure::sample_thread_in_native(JavaThread* thread, JfrStackFrame* frames, u4 max_frames) {
  JfrNativeSamplerCallback cb(*this, thread, frames, max_frames);
  if (JfrOptionSet::sample_protection()) {
    os::ThreadCrashProtection crash_protection;
    if (!crash_protection.call(cb)) {
      log_error(jfr)("Thread method sampler crashed for native");
    }
  } else {
    cb.call();
  }
  if (!cb.success()) {
    return false;
  }
  EventNativeMethodSample *event = &_events_native[_added_native - 1];
  traceid id = JfrStackTraceRepository::add(cb.stacktrace());
  assert(id != 0, "Stacktrace id should not be 0");
  event->set_stackTrace(id);
  return true;
}

static const uint MAX_NR_OF_JAVA_SAMPLES = 5;
static const uint MAX_NR_OF_NATIVE_SAMPLES = 1;

void JfrThreadSampleClosure::commit_events(JfrSampleType type) {
  if (JAVA_SAMPLE == type) {
    assert(_added_java > 0 && _added_java <= MAX_NR_OF_JAVA_SAMPLES, "invariant");
    if (EventExecutionSample::is_enabled()) {
      for (uint i = 0; i < _added_java; ++i) {
        _events[i].commit();
      }
    }
  } else {
    assert(NATIVE_SAMPLE == type, "invariant");
    assert(_added_native > 0 && _added_native <= MAX_NR_OF_NATIVE_SAMPLES, "invariant");
    if (EventNativeMethodSample::is_enabled()) {
      for (uint i = 0; i < _added_native; ++i) {
        _events_native[i].commit();
      }
    }
  }
}

JfrThreadSampleClosure::JfrThreadSampleClosure(EventExecutionSample* events, EventNativeMethodSample* events_native) :
  _events(events),
  _events_native(events_native),
  _self(Thread::current()),
  _added_java(0),
  _added_native(0) {
}

class JfrThreadSampler : public NonJavaThread {
  friend class JfrThreadSampling;
 private:
  Semaphore _sample;
  Thread* _sampler_thread;
  JfrStackFrame* const _frames;
  JavaThread* _last_thread_java;
  JavaThread* _last_thread_native;
  int64_t _java_period_millis;
  int64_t _native_period_millis;
  int _cur_index;
  const u4 _max_frames;
  volatile bool _disenrolled;

  JavaThread* next_thread(ThreadsList* t_list, JavaThread* first_sampled, JavaThread* current);
  void task_stacktrace(JfrSampleType type, JavaThread** last_thread);
  JfrThreadSampler(int64_t java_period_millis, int64_t native_period_millis, u4 max_frames);
  ~JfrThreadSampler();

  void start_thread();

  void enroll();
  void disenroll();
  void set_java_period(int64_t period_millis);
  void set_native_period(int64_t period_millis);
 protected:
  virtual void post_run();
 public:
  virtual char* name() const { return (char*)"JFR Thread Sampler"; }
  bool is_JfrSampler_thread() const { return true; }
  void run();
  static Monitor* transition_block() { return JfrThreadSampler_lock; }
  static void on_javathread_suspend(JavaThread* thread);
  int64_t get_java_period() const { return _java_period_millis; };
  int64_t get_native_period() const { return _native_period_millis; };
};

static void clear_transition_block(JavaThread* jt) {
  assert(Threads_lock->owned_by_self(), "Holding the thread table lock.");
  jt->clear_trace_flag();
  JfrThreadLocal* const tl = jt->jfr_thread_local();
  MutexLocker ml(JfrThreadSampler::transition_block(), Mutex::_no_safepoint_check_flag);
  if (tl->is_trace_block()) {
    JfrThreadSampler::transition_block()->notify();
  }
}

static bool is_excluded(JavaThread* thread) {
  assert(thread != NULL, "invariant");
  return thread->is_hidden_from_external_view() || thread->in_deopt_handler() || thread->jfr_thread_local()->is_excluded();
}

bool JfrThreadSampleClosure::do_sample_thread(JavaThread* thread, JfrStackFrame* frames, u4 max_frames, JfrSampleType type) {
  assert(Threads_lock->owned_by_self(), "Holding the thread table lock.");
  if (is_excluded(thread)) {
    return false;
  }

  bool ret = false;
  thread->set_trace_flag();  // Provides StoreLoad, needed to keep read of thread state from floating up.
  if (JAVA_SAMPLE == type) {
    if (thread_state_in_java(thread)) {
      ret = sample_thread_in_java(thread, frames, max_frames);
    }
  } else {
    assert(NATIVE_SAMPLE == type, "invariant");
    if (thread_state_in_native(thread)) {
      ret = sample_thread_in_native(thread, frames, max_frames);
    }
  }
  clear_transition_block(thread);
  return ret;
}

JfrThreadSampler::JfrThreadSampler(int64_t java_period_millis, int64_t native_period_millis, u4 max_frames) :
  _sample(),
  _sampler_thread(NULL),
  _frames(JfrCHeapObj::new_array<JfrStackFrame>(max_frames)),
  _last_thread_java(NULL),
  _last_thread_native(NULL),
  _java_period_millis(java_period_millis),
  _native_period_millis(native_period_millis),
  _cur_index(-1),
  _max_frames(max_frames),
  _disenrolled(true) {
  assert(_java_period_millis >= 0, "invariant");
  assert(_native_period_millis >= 0, "invariant");
}

JfrThreadSampler::~JfrThreadSampler() {
  JfrCHeapObj::free(_frames, sizeof(JfrStackFrame) * _max_frames);
}

void JfrThreadSampler::set_java_period(int64_t period_millis) {
  assert(period_millis >= 0, "invariant");
  _java_period_millis = period_millis;
}

void JfrThreadSampler::set_native_period(int64_t period_millis) {
  assert(period_millis >= 0, "invariant");
  _native_period_millis = period_millis;
}

static inline bool is_released(JavaThread* jt) {
  return !jt->is_trace_suspend();
}

void JfrThreadSampler::on_javathread_suspend(JavaThread* thread) {
  if (is_released(thread)) {
    return;
  }
  JfrThreadLocal* const tl = thread->jfr_thread_local();
  MonitorLocker ml(transition_block(), Mutex::_no_safepoint_check_flag);
  tl->set_trace_block();
  while (!is_released(thread)) {
    ml.wait();
  }
  tl->clear_trace_block();
}

JavaThread* JfrThreadSampler::next_thread(ThreadsList* t_list, JavaThread* first_sampled, JavaThread* current) {
  assert(t_list != NULL, "invariant");
  assert(Threads_lock->owned_by_self(), "Holding the thread table lock.");
  assert(_cur_index >= -1 && (uint)_cur_index + 1 <= t_list->length(), "invariant");
  assert((current == NULL && -1 == _cur_index) || (t_list->find_index_of_JavaThread(current) == _cur_index), "invariant");
  if ((uint)_cur_index + 1 == t_list->length()) {
    // wrap
    _cur_index = 0;
  } else {
    _cur_index++;
  }
  assert(_cur_index >= 0 && (uint)_cur_index < t_list->length(), "invariant");
  JavaThread* const next = t_list->thread_at(_cur_index);
  return next != first_sampled ? next : NULL;
}

void JfrThreadSampler::start_thread() {
  if (os::create_thread(this, os::os_thread)) {
    os::start_thread(this);
  } else {
    log_error(jfr)("Failed to create thread for thread sampling");
  }
}

void JfrThreadSampler::enroll() {
  if (_disenrolled) {
    log_trace(jfr)("Enrolling thread sampler");
    _sample.signal();
    _disenrolled = false;
  }
}

void JfrThreadSampler::disenroll() {
  if (!_disenrolled) {
    _sample.wait();
    _disenrolled = true;
    log_trace(jfr)("Disenrolling thread sampler");
  }
}

static int64_t get_monotonic_ms() {
  return os::javaTimeNanos() / 1000000;
}

void JfrThreadSampler::run() {
  assert(_sampler_thread == NULL, "invariant");

  _sampler_thread = this;

  int64_t last_java_ms = get_monotonic_ms();
  int64_t last_native_ms = last_java_ms;
  while (true) {
    if (!_sample.trywait()) {
      // disenrolled
      _sample.wait();
      last_java_ms = get_monotonic_ms();
      last_native_ms = last_java_ms;
    }
    _sample.signal();
    const int64_t java_period_millis = _java_period_millis == 0 ? max_jlong : MAX2<int64_t>(_java_period_millis, 1);
    const int64_t native_period_millis = _native_period_millis == 0 ? max_jlong : MAX2<int64_t>(_native_period_millis, 1);

    const int64_t now_ms = get_monotonic_ms();

    /*
     * Let I be java_period or native_period.
     * Let L be last_java_ms or last_native_ms.
     * Let N be now_ms.
     *
     * Interval, I, might be max_jlong so the addition
     * could potentially overflow without parenthesis (UB). Also note that
     * L - N < 0. Avoid UB, by adding parenthesis.
     */
    const int64_t next_j = java_period_millis + (last_java_ms - now_ms);
    const int64_t next_n = native_period_millis + (last_native_ms - now_ms);

    const int64_t sleep_to_next = MIN2<int64_t>(next_j, next_n);

    if (sleep_to_next > 0) {
      os::naked_short_sleep(sleep_to_next);
    }

    if ((next_j - sleep_to_next) <= 0) {
      task_stacktrace(JAVA_SAMPLE, &_last_thread_java);
      last_java_ms = get_monotonic_ms();
    }
    if ((next_n - sleep_to_next) <= 0) {
      task_stacktrace(NATIVE_SAMPLE, &_last_thread_native);
      last_native_ms = get_monotonic_ms();
    }
  }
}

void JfrThreadSampler::post_run() {
  this->NonJavaThread::post_run();
  delete this;
}


void JfrThreadSampler::task_stacktrace(JfrSampleType type, JavaThread** last_thread) {
  ResourceMark rm;
  EventExecutionSample samples[MAX_NR_OF_JAVA_SAMPLES];
  EventNativeMethodSample samples_native[MAX_NR_OF_NATIVE_SAMPLES];
  JfrThreadSampleClosure sample_task(samples, samples_native);

  const uint sample_limit = JAVA_SAMPLE == type ? MAX_NR_OF_JAVA_SAMPLES : MAX_NR_OF_NATIVE_SAMPLES;
  uint num_samples = 0;
  JavaThread* start = NULL;

  {
    elapsedTimer sample_time;
    sample_time.start();
    {
      MutexLocker tlock(Threads_lock);
      ThreadsListHandle tlh;
      // Resolve a sample session relative start position index into the thread list array.
      // In cases where the last sampled thread is NULL or not-NULL but stale, find_index() returns -1.
      _cur_index = tlh.list()->find_index_of_JavaThread(*last_thread);
      JavaThread* current = _cur_index != -1 ? *last_thread : NULL;

      while (num_samples < sample_limit) {
        current = next_thread(tlh.list(), start, current);
        if (current == NULL) {
          break;
        }
        if (start == NULL) {
          start = current;  // remember the thread where we started to attempt sampling
        }
        if (current->is_Compiler_thread()) {
          continue;
        }
        if (sample_task.do_sample_thread(current, _frames, _max_frames, type)) {
          num_samples++;
        }
      }
      *last_thread = current;  // remember the thread we last attempted to sample
    }
    sample_time.stop();
    log_trace(jfr)("JFR thread sampling done in %3.7f secs with %d java %d native samples",
                   sample_time.seconds(), sample_task.java_entries(), sample_task.native_entries());
  }
  if (num_samples > 0) {
    sample_task.commit_events(type);
  }
}

static JfrThreadSampling* _instance = NULL;

JfrThreadSampling& JfrThreadSampling::instance() {
  return *_instance;
}

JfrThreadSampling* JfrThreadSampling::create() {
  assert(_instance == NULL, "invariant");
  _instance = new JfrThreadSampling();
  return _instance;
}

void JfrThreadSampling::destroy() {
  if (_instance != NULL) {
    delete _instance;
    _instance = NULL;
  }
}

JfrThreadSampling::JfrThreadSampling() : _sampler(NULL) {}

JfrThreadSampling::~JfrThreadSampling() {
  if (_sampler != NULL) {
    _sampler->disenroll();
  }
}

#ifdef ASSERT
void assert_periods(const JfrThreadSampler* sampler, int64_t java_period_millis, int64_t native_period_millis) {
  assert(sampler != nullptr, "invariant");
  assert(sampler->get_java_period() == java_period_millis, "invariant");
  assert(sampler->get_native_period() == native_period_millis, "invariant");
}
#endif

static void log(int64_t java_period_millis, int64_t native_period_millis) {
  log_trace(jfr)("Updated thread sampler for java: " INT64_FORMAT "  ms, native " INT64_FORMAT " ms", java_period_millis, native_period_millis);
}

void JfrThreadSampling::create_sampler(int64_t java_period_millis, int64_t native_period_millis) {
  assert(_sampler == nullptr, "invariant");
  log_trace(jfr)("Creating thread sampler for java:" INT64_FORMAT " ms, native " INT64_FORMAT " ms", java_period_millis, native_period_millis);
  _sampler = new JfrThreadSampler(java_period_millis, native_period_millis, JfrOptionSet::stackdepth());
  _sampler->start_thread();
  _sampler->enroll();
}

void JfrThreadSampling::update_run_state(int64_t java_period_millis, int64_t native_period_millis) {
  if (java_period_millis > 0 || native_period_millis > 0) {
    if (_sampler == nullptr) {
      create_sampler(java_period_millis, native_period_millis);
    } else {
      _sampler->enroll();
    }
    DEBUG_ONLY(assert_periods(_sampler, java_period_millis, native_period_millis);)
    log(java_period_millis, native_period_millis);
    return;
  }
  if (_sampler != nullptr) {
    DEBUG_ONLY(assert_periods(_sampler, java_period_millis, native_period_millis);)
    _sampler->disenroll();
  }
}

void JfrThreadSampling::set_sampling_period(bool is_java_period, int64_t period_millis) {
  int64_t java_period_millis = 0;
  int64_t native_period_millis = 0;
  if (is_java_period) {
    java_period_millis = period_millis;
    if (_sampler != nullptr) {
      _sampler->set_java_period(java_period_millis);
      native_period_millis = _sampler->get_native_period();
    }
  } else {
    native_period_millis = period_millis;
    if (_sampler != nullptr) {
      _sampler->set_native_period(native_period_millis);
      java_period_millis = _sampler->get_java_period();
    }
  }
  update_run_state(java_period_millis, native_period_millis);
}

void JfrThreadSampling::set_java_sample_period(int64_t period_millis) {
  assert(period_millis >= 0, "invariant");
  if (_instance == NULL && 0 == period_millis) {
    return;
  }
  instance().set_sampling_period(true, period_millis);
}

void JfrThreadSampling::set_native_sample_period(int64_t period_millis) {
  assert(period_millis >= 0, "invariant");
  if (_instance == NULL && 0 == period_millis) {
    return;
  }
  instance().set_sampling_period(false, period_millis);
}

void JfrThreadSampling::on_javathread_suspend(JavaThread* thread) {
  JfrThreadSampler::on_javathread_suspend(thread);
}

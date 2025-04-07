/*
 * Copyright (c) 2024, SAP SE. All rights reserved.
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

#include "gc/shared/barrierSet.hpp"
#include "jfr/periodic/sampling/jfrSampleRequest.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/atomic.hpp"
#include "runtime/orderAccess.hpp"
#include "utilities/ticks.hpp"
#include "utilities/utf8.hpp"
#include "jfr/periodic/sampling/jfrCPUTimeThreadSampler.hpp"

#if defined(LINUX)

#include "jfr/recorder/service/jfrOptionSet.hpp"
#include "jfr/recorder/service/jfrEvent.hpp"
#include "jfr/utilities/jfrThreadIterator.hpp"
#include "jfr/utilities/jfrTypes.hpp"
#include "jfr/recorder/jfrRecorder.hpp"
#include "jfr/recorder/storage/jfrBuffer.hpp"
#include "jfr/recorder/service/jfrRecorderService.hpp"
#include "jfr/utilities/jfrTime.hpp"
#include "jfrfiles/jfrEventClasses.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/safepointMechanism.hpp"
#include "runtime/safepointMechanism.inline.hpp"
#include "runtime/threadSMR.hpp"
#include "runtime/threadCrashProtection.hpp"
#include "runtime/osThread.hpp"
#include "runtime/vmOperation.hpp"
#include "runtime/vmThread.hpp"

#include "signals_posix.hpp"

static const int64_t AUTOADAPT_INTERVAL_MS = 100;

static bool thread_state_in_java(JavaThread* thread) {
  assert(thread != nullptr, "invariant");
  JavaThreadState state = thread->thread_state();
  return state == _thread_in_Java || state == _thread_in_Java_trans;
}

static bool thread_state_in_non_java(JavaThread* thread) {
  assert(thread != nullptr, "invariant");
  switch(thread->thread_state()) {
    case _thread_new:
    case _thread_uninitialized:
    case _thread_new_trans:
    case _thread_in_Java_trans:
    case _thread_in_Java:
          break;
    case _thread_in_vm:
    case _thread_blocked_trans:
    case _thread_in_vm_trans:
    case _thread_in_native_trans:
    case _thread_blocked:
    case _thread_in_native:
      return true;
    default:
      ShouldNotReachHere();
      break;
  }
  return false;
}

static bool is_excluded(JavaThread* thread) {
  return thread->is_hidden_from_external_view() || thread->jfr_thread_local()->is_excluded();
}

static JavaThread* get_java_thread_if_valid() {
  Thread* raw_thread = Thread::current_or_null_safe();
  JavaThread* jt;

  if (raw_thread == nullptr || !raw_thread->is_Java_thread() ||
      (jt = JavaThread::cast(raw_thread))->is_exiting()) {
    return nullptr;
  }

  if (is_excluded(jt)) {
    return nullptr;
  }
  return jt;
}

 JfrCPUTimeTraceQueue::JfrCPUTimeTraceQueue(u4 capacity) : _capacity(capacity), _head(0) {
  _data = JfrCHeapObj::new_array<JfrSampleRequest>(capacity);
}

JfrCPUTimeTraceQueue::~JfrCPUTimeTraceQueue() {
  JfrCHeapObj::free(_data, _capacity * sizeof(JfrSampleRequest));
}

bool JfrCPUTimeTraceQueue::enqueue(JfrSampleRequest& request) {
  assert(JavaThread::current()->is_cpu_time_jfr_enqueue_locked(), "invariant");
  u4 elementIndex;
  do {
    elementIndex = Atomic::load(&_head);
    if (elementIndex >= _capacity) {
      return false;
    }
  } while (Atomic::cmpxchg(&_head, elementIndex, elementIndex + 1) != elementIndex);
  _data[elementIndex] = request;
  return true;
}

JfrSampleRequest* JfrCPUTimeTraceQueue::dequeue() {
  assert(Thread::current()->is_Java_thread(), "invariant");
  assert(JavaThread::current()->is_cpu_time_jfr_dequeue_locked(), "invariant");
  if (_head > 0) {
    _head--;
    return &_data[_head];
  }
  return nullptr;
}

volatile u4 _lost_samples_sum = 0;

u4 JfrCPUTimeTraceQueue::size() const {
  return Atomic::load(&_head);
}

u4 JfrCPUTimeTraceQueue::capacity() const {
  return _capacity;
}

void JfrCPUTimeTraceQueue::set_capacity(u4 capacity) {
  _head = 0;
  JfrSampleRequest* new_data = JfrCHeapObj::new_array<JfrSampleRequest>(capacity);
  JfrCHeapObj::free(_data, _capacity * sizeof(JfrSampleRequest));
  _data = new_data;
  _capacity = capacity;
}

u4 JfrCPUTimeTraceQueue::is_full() const {
  return Atomic::load(&_head) >= _capacity;
}

u4 JfrCPUTimeTraceQueue::is_empty() const {
  return Atomic::load(&_head) == 0;
}

u4 JfrCPUTimeTraceQueue::lost_samples() const {
  return Atomic::load(&_lost_samples);
}

void JfrCPUTimeTraceQueue::increment_lost_samples() {
  if (Atomic::load(&_lost_samples) == 0) {
    _lost_samples_start = JfrTicks::now();
    OrderAccess::storestore();
  }
  Atomic::inc(&_lost_samples_sum);
  Atomic::inc(&_lost_samples);
}

void JfrCPUTimeTraceQueue::reset_lost_samples() {
  Atomic::store(&_lost_samples, (u4)0);
  _lost_samples_start = JfrTicks::now();
}

JfrTicks JfrCPUTimeTraceQueue::lost_samples_start() const {
  return _lost_samples_start;
}

static int64_t compute_sampling_period(double rate);

class JfrCPUTimeThreadSampler : public NonJavaThread {
  friend class JfrCPUTimeThreadSampling;
 private:
  Semaphore _sample;
  NonJavaThread* _sampler_thread;
  double _rate;
  bool _autoadapt;
  volatile int64_t _current_sampling_period_ns = -1;
  volatile bool _disenrolled;
  volatile bool _stop_signals = false;
  volatile int _active_signal_handlers;

  void reniew_enqueue_buffer_if_full();

  JfrCPUTimeThreadSampler(double rate, bool autoadapt);
  ~JfrCPUTimeThreadSampler();

  void start_thread();

  void enroll();
  void disenroll();
  void update_all_thread_timers();

  void autoadapt_period_if_needed();

  void set_rate(double rate, bool autoadapt);
  int64_t get_sampling_period() const { return Atomic::load(&_current_sampling_period_ns); };

  void sample_thread(JfrSampleRequest& request, void* ucontext, JavaThread* jt);

protected:
  virtual void post_run();
public:
  virtual const char* name() const { return "JFR CPU Time Thread Sampler"; }
  virtual const char* type_name() const { return "JfrCPUTimeThreadSampler"; }
  void run();
  void on_javathread_create(JavaThread* thread);
  bool create_timer_for_thread(JavaThread* thread, timer_t &timerid);
  void on_javathread_terminate(JavaThread* thread);

  void handle_timer_signal(siginfo_t* info, void* context);
  void init_timers();
  void stop_timer();

};


JfrCPUTimeThreadSampler::JfrCPUTimeThreadSampler(double rate, bool autoadapt) :
  _sample(),
  _sampler_thread(nullptr),
  _rate(rate),
  _autoadapt(autoadapt),
  _current_sampling_period_ns(compute_sampling_period(rate)),
  _disenrolled(true) {
  assert(rate >= 0, "invariant");
}

JfrCPUTimeThreadSampler::~JfrCPUTimeThreadSampler() {
}

void JfrCPUTimeThreadSampler::on_javathread_create(JavaThread* thread) {
  if (thread->is_Compiler_thread()) {
    return;
  }
  thread->enable_cpu_time_jfr_queue();
  if (thread->jfr_thread_local() != nullptr) {
    timer_t timerid;
    if (create_timer_for_thread(thread, timerid)) {
      thread->jfr_thread_local()->set_timerid(timerid);
    }
  }
}

void JfrCPUTimeThreadSampler::on_javathread_terminate(JavaThread* thread) {
  if (thread->jfr_thread_local() != nullptr && thread->jfr_thread_local()->has_timerid()) {
    timer_delete(thread->jfr_thread_local()->timerid());
    thread->jfr_thread_local()->unset_timerid();
    thread->disable_cpu_time_jfr_queue();
  }
}

void JfrCPUTimeThreadSampler::start_thread() {
  if (os::create_thread(this, os::os_thread)) {
    os::start_thread(this);
  } else {
    log_error(jfr)("Failed to create thread for thread sampling");
  }
}

void JfrCPUTimeThreadSampler::enroll() {
  if (Atomic::cmpxchg(&_disenrolled, true, false)) {
    log_info(jfr)("Enrolling CPU thread sampler");
    _sample.signal();
    init_timers();
    log_trace(jfr)("Enrolled CPU thread sampler");
  }
}

void JfrCPUTimeThreadSampler::disenroll() {
  if (!Atomic::cmpxchg(&_disenrolled, false, true)) {
    log_info(jfr)("Disenrolling CPU thread sampler");
    stop_timer();
    Atomic::store(&_stop_signals, true);
    while (_active_signal_handlers > 0) {
      // wait for all signal handlers to finish
      os::naked_short_nanosleep(1000);
    }
    _sample.wait();
    Atomic::store(&_stop_signals, false);
    log_trace(jfr)("Disenrolled CPU thread sampler");
  }
}

void JfrCPUTimeThreadSampler::run() {
  assert(_sampler_thread == nullptr, "invariant");
  _sampler_thread = this;
  int64_t last_autoadapt_check = os::javaTimeNanos();
  while (true) {
    if (!_sample.trywait()) {
      // disenrolled
      _sample.wait();
    }
    _sample.signal();

    if (os::javaTimeNanos() - last_autoadapt_check > AUTOADAPT_INTERVAL_MS * 1000000) {
      autoadapt_period_if_needed();
      last_autoadapt_check = os::javaTimeNanos();
    }

    os::naked_sleep(10);
  }
}

static volatile size_t count = 0;

void JfrCPUTimeThreadSampling::send_empty_event(const JfrTicks &start_time, const JfrTicks &end_time, traceid tid, Tickspan cpu_time_period) {
  EventCPUTimeSample event(UNTIMED);
  event.set_failed(true);
  event.set_starttime(start_time);
  event.set_endtime(end_time);
  event.set_eventThread(tid);
  event.set_stackTrace(0);
  event.set_samplingPeriod(cpu_time_period);
  event.commit();
}

void JfrCPUTimeThreadSampling::send_event(const JfrTicks &start_time, const JfrTicks &end_time, traceid sid, traceid tid, Tickspan cpu_time_period) {
  EventCPUTimeSample event(UNTIMED);
  event.set_failed(false);
  event.set_starttime(start_time);
  event.set_endtime(end_time);
  event.set_eventThread(tid);
  event.set_stackTrace(sid);
  event.set_samplingPeriod(cpu_time_period);
  event.commit();
  Atomic::inc(&count);
  if (Atomic::load(&count) % 1000 == 0) {
    log_info(jfr)("CPU thread sampler sent %ld events, lost %d\n", Atomic::load(&count), Atomic::load(&_lost_samples_sum));
  }
}

void JfrCPUTimeThreadSampling::send_lost_event(const JfrTicks &start_time, const JfrTicks &end_time, traceid tid, u4 lost_samples) {
  EventCPUTimeSampleLoss event(UNTIMED);
  event.set_starttime(start_time);
  event.set_endtime(end_time);
  event.set_lostSamples(lost_samples);
  event.set_eventThread(tid);
  event.commit();
}

void JfrCPUTimeThreadSampler::post_run() {
  this->NonJavaThread::post_run();
  delete this;
}

void JfrCPUTimeThreadSampler::reniew_enqueue_buffer_if_full() {
  const JfrBuffer* buffer = JfrTraceIdLoadBarrier::get_sampler_enqueue_buffer(_sampler_thread);
  if (buffer->free_size() < 100) {
    JfrTraceIdLoadBarrier::renew_sampler_enqueue_buffer(_sampler_thread);
  }
}

static JfrCPUTimeThreadSampling* _instance = nullptr;

JfrCPUTimeThreadSampling& JfrCPUTimeThreadSampling::instance() {
  return *_instance;
}

JfrCPUTimeThreadSampling* JfrCPUTimeThreadSampling::create() {
  assert(_instance == nullptr, "invariant");
  _instance = new JfrCPUTimeThreadSampling();
  return _instance;
}

void JfrCPUTimeThreadSampling::destroy() {
  if (_instance != nullptr) {
    delete _instance;
    _instance = nullptr;
  }
}

JfrCPUTimeThreadSampling::JfrCPUTimeThreadSampling() : _sampler(nullptr) {}

JfrCPUTimeThreadSampling::~JfrCPUTimeThreadSampling() {
  if (_sampler != nullptr) {
    _sampler->disenroll();
  }
}

void JfrCPUTimeThreadSampling::create_sampler(double rate, bool autoadapt) {
  assert(_sampler == nullptr, "invariant");
  _sampler = new JfrCPUTimeThreadSampler(rate, autoadapt);
  _sampler->start_thread();
  _sampler->enroll();
}

void JfrCPUTimeThreadSampling::update_run_state(double rate, bool autoadapt) {
  if (rate != 0) {
    if (_sampler == nullptr) {
      create_sampler(rate, autoadapt);
    } else {
      _sampler->set_rate(rate, autoadapt);
      _sampler->enroll();
    }
    return;
  }
  if (_sampler != nullptr) {
    _sampler->set_rate(rate /* 0 */, autoadapt);
    _sampler->disenroll();
  }
}

void JfrCPUTimeThreadSampling::set_rate(double rate, bool autoadapt) {
  assert(rate >= 0, "invariant");
  if (_instance == nullptr) {
    return;
  }
  instance().set_rate_value(rate, autoadapt);
}

void JfrCPUTimeThreadSampling::set_rate_value(double rate, bool autoadapt) {
  if (_sampler != nullptr) {
    _sampler->set_rate(rate, autoadapt);
  }
  update_run_state(rate, autoadapt);
}

void JfrCPUTimeThreadSampling::on_javathread_create(JavaThread *thread) {
  if (_instance != nullptr && _instance->_sampler != nullptr) {
    _instance->_sampler->on_javathread_create(thread);
  }
}

void JfrCPUTimeThreadSampling::on_javathread_terminate(JavaThread *thread) {
  if (_instance != nullptr && _instance->_sampler != nullptr) {
    _instance->_sampler->on_javathread_terminate(thread);
  }
}

void handle_timer_signal(int signo, siginfo_t* info, void* context) {
  assert(_instance != nullptr, "invariant");
  _instance->handle_timer_signal(info, context);
}


void JfrCPUTimeThreadSampling::handle_timer_signal(siginfo_t* info, void* context) {
  assert(_sampler != nullptr, "invariant");
  if (Atomic::load(&_sampler->_stop_signals)) {
    return;
  }
  Atomic::inc(&_sampler->_active_signal_handlers);
  _sampler->handle_timer_signal(info, context);
  Atomic::dec(&_sampler->_active_signal_handlers);
}

void JfrCPUTimeThreadSampler::sample_thread(JfrSampleRequest& request, void* ucontext, JavaThread* jt) {
  JfrSampleRequestBuilder::build_cpu_time_sample_request(request, ucontext, jt);
}


void JfrCPUTimeThreadSampler::handle_timer_signal(siginfo_t* info, void* context) {
  JavaThread* jt = get_java_thread_if_valid();
  if (jt == nullptr) {
    return;
  }
  if (!jt->acquire_cpu_time_jfr_enqueue_lock()) {
    jt->cpu_time_jfr_queue().increment_lost_samples();
    return;
  }
  NoResourceMark rm;
  if (jt->cpu_time_jfr_queue().is_full()) {
    jt->cpu_time_jfr_queue().increment_lost_samples();
  }
  JfrSampleRequest request;
  // the sampling period might be too low for the current Linux configuration
  // so samples might be skipped and we have to compute the actual period
  int64_t period = get_sampling_period() * (info->si_overrun + 1);
  request._cpu_time_period = Ticks(period / 1000000000.0 * JfrTime::frequency()) - Ticks(0);

  bool success = false;

  if (thread_state_in_java(jt) || thread_state_in_non_java(jt)) {
    sample_thread(request, context, jt);
    success = true;
  }

  if (jt->cpu_time_jfr_queue().enqueue(request)) {
    if (success) {
      jt->set_has_cpu_time_jfr_requests(true);
      SafepointMechanism::arm_local_poll_release(jt);
    }
  } else {
    jt->cpu_time_jfr_queue().increment_lost_samples();
  }
  jt->release_cpu_time_jfr_queue_lock();
}

static const int SIG = SIGPROF;

static void set_timer_time(timer_t timerid, int64_t period_nanos) {
  struct itimerspec its;
  if (period_nanos == 0) {
    its.it_interval.tv_sec = 0;
    its.it_interval.tv_nsec = 0;
  } else {
    its.it_interval.tv_sec = period_nanos / NANOSECS_PER_SEC;
    its.it_interval.tv_nsec = period_nanos % NANOSECS_PER_SEC;
  }
  its.it_value = its.it_interval;
  if (timer_settime(timerid, 0, &its, NULL) == -1) {
    warning("Failed to set timer for thread sampling: %s", os::strerror(os::get_last_error()));
  }
}

bool JfrCPUTimeThreadSampler::create_timer_for_thread(JavaThread* thread, timer_t& timerid) {
  if (thread->osthread() == nullptr || thread->osthread()->thread_id() == 0){
    return false;
  }
  timer_t t;
  struct sigevent sev;
  sev.sigev_notify = SIGEV_THREAD_ID;
  sev.sigev_signo = SIG;
  sev.sigev_value.sival_ptr = &t;
  ((int*)&sev.sigev_notify)[1] = thread->osthread()->thread_id();
  clockid_t clock;
  int err = pthread_getcpuclockid(thread->osthread()->pthread_id(), &clock);
  if (err != 0) {
    log_error(jfr)("Failed to get clock for thread sampling: %s", os::strerror(err));
    return false;
  }
  if (timer_create(clock, &sev, &t) < 0) {
    return false;
  }
  int64_t period = get_sampling_period();
  if (period != 0) {
    set_timer_time(t, period);
  }
  timerid = t;
  return true;
}

class VM_CPUTimeSamplerThreadInitializer : public VM_Operation {
 private:
  JfrCPUTimeThreadSampler *_sampler;
 public:

  VM_CPUTimeSamplerThreadInitializer(JfrCPUTimeThreadSampler* sampler) : _sampler(sampler) {
  }

  VMOp_Type type() const { return VMOp_CPUTimeSamplerThreadInitializer; }
  void doit() {
    JfrJavaThreadIterator iter;
    while (iter.has_next()) {
      _sampler->on_javathread_create(iter.next());
    }
  };
};

void JfrCPUTimeThreadSampler::init_timers() {
  // install sig handler for sig
  PosixSignals::install_generic_signal_handler(SIG, (void*)::handle_timer_signal);

  VM_CPUTimeSamplerThreadInitializer op(this);
  VMThread::execute(&op);
}

class VM_CPUTimeSamplerThreadTerminator : public VM_Operation {
 private:
  JfrCPUTimeThreadSampler *_sampler;
 public:

  VM_CPUTimeSamplerThreadTerminator(JfrCPUTimeThreadSampler* sampler) : _sampler(sampler) {
  }

  VMOp_Type type() const { return VMOp_CPUTimeSamplerThreadTerminator; }
  void doit() {
    JfrJavaThreadIterator iter;
    while (iter.has_next()) {
      JavaThread *thread = iter.next();
      JfrThreadLocal* jfr_thread_local = thread->jfr_thread_local();
      if (jfr_thread_local != nullptr && jfr_thread_local->has_timerid()) {
        timer_delete(jfr_thread_local->timerid());
        thread->disable_cpu_time_jfr_queue();
        thread->jfr_thread_local()->unset_timerid();
      }
    }
  };
};

void JfrCPUTimeThreadSampler::stop_timer() {
  VM_CPUTimeSamplerThreadTerminator op(this);
  VMThread::execute(&op);
}

int64_t compute_sampling_period(double rate) {
  if (rate == 0) {
    return 0;
  }
  return os::active_processor_count() * 1000000000.0 / rate;
}

void JfrCPUTimeThreadSampler::autoadapt_period_if_needed() {
  int64_t current_period = get_sampling_period();
  if (_autoadapt || current_period == -1) {
    int64_t period = compute_sampling_period(_rate);
    if (period != current_period) {
      Atomic::store(&_current_sampling_period_ns, period);
      update_all_thread_timers();
    }
  }
}

void JfrCPUTimeThreadSampler::set_rate(double rate, bool autoadapt) {
  _rate = rate;
  _autoadapt = autoadapt;
  if (_rate > 0 && Atomic::load(&_disenrolled) == false) {
    autoadapt_period_if_needed();
  } else {
    Atomic::store(&_current_sampling_period_ns, compute_sampling_period(rate));
  }
}

void JfrCPUTimeThreadSampler::update_all_thread_timers() {
  int64_t period_millis = get_sampling_period();
  MutexLocker tlock(Threads_lock);
  ThreadsListHandle tlh;
  for (size_t i = 0; i < tlh.length(); i++) {
    JavaThread* thread = tlh.thread_at(i);
    JfrThreadLocal* jfr_thread_local = thread->jfr_thread_local();
    if (jfr_thread_local != nullptr && jfr_thread_local->has_timerid()) {
      set_timer_time(jfr_thread_local->timerid(), period_millis);
    }
  }
}

#else

static bool _showed_warning = false;

static void warn() {
  if (!_showed_warning) {
    warning("CPU time method sampling not supported in JFR on your platform");
    _showed_warning = true;
  }
}

static JfrCPUTimeThreadSampling* _instance = nullptr;

JfrCPUTimeThreadSampling& JfrCPUTimeThreadSampling::instance() {
  return *_instance;
}

JfrCPUTimeThreadSampling* JfrCPUTimeThreadSampling::create() {
  _instance = new JfrCPUTimeThreadSampling();
  return _instance;
}

void JfrCPUTimeThreadSampling::destroy() {
  delete _instance;
  _instance = nullptr;
}

void JfrCPUTimeThreadSampling::set_rate(double rate, bool autoadapt) {
  if (rate != 0) {
    warn();
  }
}

void JfrCPUTimeThreadSampling::on_javathread_create(JavaThread* thread) {
}

void JfrCPUTimeThreadSampling::on_javathread_terminate(JavaThread* thread) {
}

void JfrCPUTimeThreadSampling::send_empty_event(const JfrTicks& start_time, const JfrTicks& end_time, traceid tid, Tickspan cpu_time_period) {
}

void JfrCPUTimeThreadSampling::send_event(const JfrTicks& start_time, const JfrTicks& end_time, traceid sid, traceid tid, Tickspan cpu_time_period) {
}

#endif // defined(LINUX) && defined(INCLUDE_JFR)

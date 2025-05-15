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
#include "jfr/support/jfrThreadLocal.hpp"
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

static bool is_excluded(JavaThread* thread) {
  return thread->is_hidden_from_external_view() || thread->jfr_thread_local()->is_excluded();
}

static JavaThread* get_java_thread_if_valid() {
  Thread* raw_thread = Thread::current_or_null_safe();
  JavaThread* jt;
  assert(raw_thread != nullptr, "invariant");
  assert(raw_thread->is_Java_thread(), "invariant");
  if ((jt = JavaThread::cast(raw_thread))->is_exiting()) {
    return nullptr;
  }

  if (is_excluded(jt)) {
    return nullptr;
  }
  return jt;
}

JfrCPUTimeTraceQueue::JfrCPUTimeTraceQueue(u4 capacity) : _capacity(capacity), _head(0), _lost_samples(0) {
  _data = JfrCHeapObj::new_array<JfrCPUTimeSampleRequest>(capacity);
}

JfrCPUTimeTraceQueue::~JfrCPUTimeTraceQueue() {
  JfrCHeapObj::free(_data, _capacity * sizeof(JfrCPUTimeSampleRequest));
}

bool JfrCPUTimeTraceQueue::enqueue(JfrCPUTimeSampleRequest& request) {
  assert(JavaThread::current()->jfr_thread_local()->is_cpu_time_jfr_enqueue_locked(), "invariant");
  u4 elementIndex;
  do {
    elementIndex = Atomic::load_acquire(&_head);
    if (elementIndex >= _capacity) {
      return false;
    }
  } while (Atomic::cmpxchg(&_head, elementIndex, elementIndex + 1) != elementIndex);
  _data[elementIndex] = request;
  return true;
}

JfrCPUTimeSampleRequest& JfrCPUTimeTraceQueue::at(u4 index) {
  assert(index < _head, "invariant");
  return _data[index];
}

volatile u4 _lost_samples_sum = 0;

u4 JfrCPUTimeTraceQueue::size() const {
  return Atomic::load(&_head);
}

void JfrCPUTimeTraceQueue::set_size(u4 size) {
  Atomic::release_store(&_head, size);
}

u4 JfrCPUTimeTraceQueue::capacity() const {
  return _capacity;
}

void JfrCPUTimeTraceQueue::set_capacity(u4 capacity) {
  _head = 0;
  JfrCPUTimeSampleRequest* new_data = JfrCHeapObj::new_array<JfrCPUTimeSampleRequest>(capacity);
  JfrCHeapObj::free(_data, _capacity * sizeof(JfrCPUTimeSampleRequest));
  _data = new_data;
  _capacity = capacity;
}

bool JfrCPUTimeTraceQueue::is_full() const {
  return Atomic::load_acquire(&_head) >= _capacity;
}

bool JfrCPUTimeTraceQueue::is_empty() const {
  return Atomic::load_acquire(&_head) == 0;
}

s4 JfrCPUTimeTraceQueue::lost_samples() const {
  return Atomic::load_acquire(&_lost_samples);
}

void JfrCPUTimeTraceQueue::increment_lost_samples() {
  Atomic::inc(&_lost_samples_sum);
  Atomic::inc(&_lost_samples);
}

u4 JfrCPUTimeTraceQueue::get_and_reset_lost_samples() {
  s4 lost_samples = Atomic::load_acquire(&_lost_samples);
  while (Atomic::cmpxchg(&_lost_samples, lost_samples, 0) != lost_samples) {
    lost_samples = Atomic::load_acquire(&_lost_samples);
  }
  return lost_samples;
}

void JfrCPUTimeTraceQueue::ensure_capacity(u4 capacity) {
  if (capacity != _capacity) {
    set_capacity(capacity);
  }
}

void JfrCPUTimeTraceQueue::ensure_capacity_for_period(u4 period_millis) {
  u4 capacity = CPU_TIME_QUEUE_CAPACITY;
  if (period_millis > 0 && period_millis < 10) {
    capacity = (u4) ((double) capacity * 10 / period_millis);
  }
  ensure_capacity(capacity);
}

void JfrCPUTimeTraceQueue::clear() {
  Atomic::release_store(&_head, (u4)0);
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
  volatile int _active_signal_handlers = 0;
  volatile bool _is_out_of_safepoint_sampling_triggered = false;

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

  // sample all marked threads out of safepoint
  void sample_out_of_safepoint();

  void sample_out_of_safepoint(JavaThread* thread);

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

  void trigger_out_of_safepoint_sampling();
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

void JfrCPUTimeThreadSampler::trigger_out_of_safepoint_sampling() {
  Atomic::release_store(&_is_out_of_safepoint_sampling_triggered, true);
}

void JfrCPUTimeThreadSampler::on_javathread_create(JavaThread* thread) {
  if (thread->is_Compiler_thread()) {
    return;
  }
  JfrThreadLocal* tl = thread->jfr_thread_local();
  assert(tl != nullptr, "invariant");
  tl->cpu_time_jfr_queue().ensure_capacity_for_period(_current_sampling_period_ns / 1000000);
  timer_t timerid;
  if (create_timer_for_thread(thread, timerid)) {
    tl->set_cpu_timer(timerid);
  }
}

void JfrCPUTimeThreadSampler::on_javathread_terminate(JavaThread* thread) {
  JfrThreadLocal* tl = thread->jfr_thread_local();
  assert(tl != nullptr, "invariant");
  if (tl->has_cpu_timer()) {
    timer_delete(tl->cpu_timer());
    tl->unset_cpu_timer();
    tl->disable_cpu_time_jfr_queue();
    s4 lost_samples = tl->cpu_time_jfr_queue().lost_samples();
    if (lost_samples > 0) {
      JfrCPUTimeThreadSampling::send_lost_event(JfrTicks::now(), JfrThreadLocal::thread_id(thread), lost_samples);
    }
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

    if (Atomic::load_acquire(&_is_out_of_safepoint_sampling_triggered)) {
      Atomic::release_store(&_is_out_of_safepoint_sampling_triggered, false);
      sample_out_of_safepoint();
    }
    os::naked_sleep(100);
  }
}

void JfrCPUTimeThreadSampler::sample_out_of_safepoint() {
  ResourceMark rm;
  MutexLocker tlock(Threads_lock);
  ThreadsListHandle tlh;
  for (size_t i = 0; i < tlh.list()->length(); i++) {
    JavaThread* jt = tlh.list()->thread_at(i);
    JfrThreadLocal* tl = jt->jfr_thread_local();
    if (tl != nullptr && tl->wants_out_of_safepoint_sampling()) {
      if (!tl->acquire_cpu_time_jfr_native_lock()) {
        continue;
      }
      tl->set_wants_out_of_safepoint_sampling(false);
      sample_out_of_safepoint(jt);
      tl->release_cpu_time_jfr_queue_lock();
    }
  }
}

// equals operator for JfrSampleRequest
inline bool operator==(const JfrSampleRequest& lhs, const JfrSampleRequest& rhs) {
  return lhs._sample_sp == rhs._sample_sp &&
         lhs._sample_pc == rhs._sample_pc &&
         lhs._sample_bcp == rhs._sample_bcp;
}

void JfrCPUTimeThreadSampler::sample_out_of_safepoint(JavaThread* thread) {
  assert(thread->jfr_thread_local() != nullptr, "invariant");
  JfrCPUTimeTraceQueue& queue = thread->jfr_thread_local()->cpu_time_jfr_queue();
  assert(!queue.is_empty(), "invariant");
  if (queue.is_empty()) {
    return;
  }
  JfrCPUTimeSampleRequest& request = queue.at(queue.size() - 1);
  // find the first that is equal
  // idea: this ensures that the frames happened in native
  // maybe we can wrap this in #ifdef ASSERT if first_index is always 0
  s4 first_index = queue.size() - 1;
  for (s4 i = queue.size() - 1; i >= 0; i--) {
    if (queue.at(i)._request == request._request) {
      first_index = i;
    } else {
      break;
    }
  }
  assert(first_index == 0, "invariant");

  // now obtain the single stack trace

  const frame top_frame = thread->last_frame();
  for (u4 i = first_index; i < queue.size(); i++) {
    const JfrTicks now = JfrTicks::now();
    JfrCPUTimeSampleRequest& request = queue.at(i);
    JfrStackTrace stacktrace;
    traceid tid = JfrThreadLocal::thread_id(thread);
    if (!stacktrace.record_inner(thread, top_frame, 0)) {
      log_info(jfr)("Unable to record native stacktrace for thread %s in CPU time sampler", thread->name());
      JfrCPUTimeThreadSampling::send_empty_event(request._request._sample_ticks, now, tid, request._cpu_time_period);
    } else {
      traceid sid = JfrStackTraceRepository::add(stacktrace);
      JfrCPUTimeThreadSampling::send_event(request._request._sample_ticks, now, sid, tid, request._cpu_time_period, false);
    }
  }
  if (queue.lost_samples() > 0) {
    const JfrTicks now = JfrTicks::now();
    JfrCPUTimeThreadSampling::send_lost_event(now, JfrThreadLocal::thread_id(thread), queue.get_and_reset_lost_samples());
  }
  queue.set_size(first_index);
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
  event.set_biased(false);
  event.commit();
}


static volatile size_t biased_count = 0;

void JfrCPUTimeThreadSampling::send_event(const JfrTicks &start_time, const JfrTicks &end_time, traceid sid, traceid tid, Tickspan cpu_time_period, bool biased) {
  EventCPUTimeSample event(UNTIMED);
  event.set_failed(false);
  event.set_starttime(start_time);
  event.set_endtime(end_time);
  event.set_eventThread(tid);
  event.set_stackTrace(sid);
  event.set_samplingPeriod(cpu_time_period);
  event.set_biased(biased);
  event.commit();
  Atomic::inc(&count);
  if (biased) {
    Atomic::inc(&biased_count);
  }
  if (Atomic::load(&count) % 1000 == 0) {
    log_info(jfr)("CPU thread sampler sent %ld events, lost %d, biased %ld\n", Atomic::load(&count), Atomic::load(&_lost_samples_sum), Atomic::load(&biased_count));
  }
}

void JfrCPUTimeThreadSampling::send_lost_event(const JfrTicks &time, traceid tid, s4 lost_samples) {
  if (!EventCPUTimeSampleLoss::is_enabled()) {
    return;
  }
  EventCPUTimeSampleLoss event(UNTIMED);
  event.set_starttime(time);
  event.set_lostSamples(lost_samples);
  event.set_eventThread(tid);
  event.commit();
}

void JfrCPUTimeThreadSampler::post_run() {
  this->NonJavaThread::post_run();
  delete this;
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

void JfrCPUTimeThreadSampling::trigger_out_of_safepoint_sampling() {
  if (_instance != nullptr && _instance->_sampler != nullptr) {
    _instance->_sampler->trigger_out_of_safepoint_sampling();
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

volatile size_t count__ = 0;

static bool check_state(JavaThread* thread) {
  switch (thread->thread_state()) {
    case _thread_in_Java:
    case _thread_in_native:
      return true;
    case _thread_in_vm:
    default:
      return false;
  }
}

void JfrCPUTimeThreadSampler::handle_timer_signal(siginfo_t* info, void* context) {
  JavaThread* jt = get_java_thread_if_valid();
  if (jt == nullptr) {
    return;
  }
  JfrThreadLocal* tl = jt->jfr_thread_local();
  if (!check_state(jt) ||
      jt->is_at_poll_safepoint() ||
      jt->is_JfrRecorder_thread()) {
      tl->cpu_time_jfr_queue().increment_lost_samples();
      tl->set_wants_out_of_safepoint_sampling(false);
    return;
  }
  if (!tl->acquire_cpu_time_jfr_enqueue_lock()) {
    tl->cpu_time_jfr_queue().increment_lost_samples();
    return;
  }

  NoResourceMark rm;
  JfrCPUTimeSampleRequest request;
  // the sampling period might be too low for the current Linux configuration
  // so samples might be skipped and we have to compute the actual period
  int64_t period = get_sampling_period() * (info->si_overrun + 1);
  request._cpu_time_period = Ticks(period / 1000000000.0 * JfrTime::frequency()) - Ticks(0);
  sample_thread(request._request, context, jt);

  if (tl->cpu_time_jfr_queue().enqueue(request)) {
    tl->set_has_cpu_time_jfr_requests(true);
    SafepointMechanism::arm_local_poll_release(jt);
  } else {
    tl->cpu_time_jfr_queue().increment_lost_samples();
  }

  if (jt->thread_state() == _thread_in_native &&
    tl->cpu_time_jfr_queue().size() > tl->cpu_time_jfr_queue().capacity() * 2 / 3) {
    // we are in native code and the queue is getting full
    tl->set_wants_out_of_safepoint_sampling(true);
    JfrCPUTimeThreadSampling::trigger_out_of_safepoint_sampling();
  } else {
    tl->set_wants_out_of_safepoint_sampling(false);
  }

  tl->release_cpu_time_jfr_queue_lock();
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
  if (timer_settime(timerid, 0, &its, nullptr) == -1) {
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
      JfrThreadLocal* tl = thread->jfr_thread_local();
      if (tl != nullptr && tl->has_cpu_timer()) {
        timer_delete(tl->cpu_timer());
        tl->disable_cpu_time_jfr_queue();
        tl->unset_cpu_timer();
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
    if (jfr_thread_local != nullptr && jfr_thread_local->has_cpu_timer()) {
      set_timer_time(jfr_thread_local->cpu_timer(), period_millis);
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

void JfrCPUTimeThreadSampling::send_event(const JfrTicks& start_time, const JfrTicks& end_time, traceid sid, traceid tid, Tickspan cpu_time_period, bool biased) {
}

#endif // defined(LINUX) && defined(INCLUDE_JFR)

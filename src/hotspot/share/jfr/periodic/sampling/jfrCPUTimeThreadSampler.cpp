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

 JfrTraceQueue::JfrTraceQueue(u4 capacity) : _capacity(capacity), _head(0), _tail(0) {
  _data = JfrCHeapObj::new_array<Element>(capacity);
  memset(_data, 0, _capacity * sizeof(Element));
}

JfrTraceQueue::~JfrTraceQueue() {
  JfrCHeapObj::free(_data, _capacity * sizeof(Element));
}

bool JfrTraceQueue::enqueue(JfrSampleRequest* request) {
  int count = 10000;
  while (count -- > 0) {
    u4 tail = Atomic::load_acquire(&_tail);
    Element* e = element(tail);
    u4 state = Atomic::load_acquire(&e->_state);
    if (state == state_empty(tail)) {
      if (Atomic::cmpxchg(&_tail, tail, tail + 1, memory_order_seq_cst) == tail) {
          e->_sample_request = request;
          // Mark element as full in current generation
          Atomic::release_store(&e->_state, state_full(tail));
          return true;
      }
    } else if (state == state_full(tail)) {
      // Another thread has just filled the same position; retry operation
    } else {
      return false;
    }
  }
  return false;
}

JfrSampleRequest* JfrTraceQueue::dequeue() {
  int count = 1000;
  while (count-- > 0) {
    u4 head = Atomic::load_acquire(&_head);
    Element* e = element(head);
    u4 state = Atomic::load_acquire(&e->_state);
    if (state == state_full(head)) {
      if (Atomic::cmpxchg(&_head, head, head + 1, memory_order_seq_cst) == head) {
          JfrSampleRequest* request = e->_sample_request;
          // After taking an element, mark it as empty in the next generation,
          // so we can reuse it again after completing the full circle
          Atomic::release_store(&e->_state, state_empty(head + _capacity));
          return request;
      }
    } else if (state == state_empty(head)) {
      return nullptr; // Queue is empty
    } else {
      // Producer has not yet completed transaction
      // mark it as empty to prevent stalling
      if (count < 500) {
        Atomic::release_store(&e->_state, state_empty(head));
      }
    }
  }
  return nullptr; // prevent hanging
}

// Two queues for sampling, fresh and filled
// at the start, all traces are in the fresh queue
class JfrTraceQueues {
  JfrSampleRequest* _sample_requests;
  JfrTraceQueue _fresh;
  u4 _max_traces;
  u4 _max_frames_per_trace;

public:
  JfrTraceQueues(u4 max_traces, u4 max_frames_per_trace):
    _sample_requests(JfrCHeapObj::new_array<JfrSampleRequest>(max_traces)), _fresh(max_traces),
    _max_traces(max_traces), _max_frames_per_trace(max_frames_per_trace) {
    // create traces
    for (u4 i = 0; i < max_traces; i++) {
      _sample_requests[i] = JfrSampleRequest();
    }
    // initialize fresh queue
    for (u4 i = 0; i < max_traces; i++) {
      _fresh.enqueue(&_sample_requests[i]);
    }
  }

  ~JfrTraceQueues() {
    JfrCHeapObj::free(_sample_requests, sizeof(JfrSampleRequest) * _max_traces);
  }

  JfrTraceQueue& fresh() { return _fresh; }

  u4 max_traces() const { return _max_traces; }
};

static int64_t compute_sampling_period(double rate);

class JfrCPUTimeThreadSampler : public NonJavaThread {
  friend class JfrCPUTimeThreadSampling;
 private:
  Semaphore _sample;
  NonJavaThread* _sampler_thread;
  JfrTraceQueues _queues;
  double _rate;
  bool _autoadapt;
  volatile int64_t _current_sampling_period_ns = -1;
  const size_t _max_frames_per_trace;
  volatile bool _disenrolled;
  volatile bool _stop_signals = false;
  volatile int _active_signal_handlers;
  JfrStackFrame *_jfrFrames;
  volatile int _lossed_samples = 0;
  volatile int _lossed_samples_sum = 0;

  const JfrBuffer* get_enqueue_buffer(JavaThread* thread);
  const JfrBuffer* renew_if_full(const JfrBuffer* enqueue_buffer, JavaThread* thread);


  JfrCPUTimeThreadSampler(double rate, bool autoadapt, u4 max_traces, u4 max_frames_per_trace);
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


JfrCPUTimeThreadSampler::JfrCPUTimeThreadSampler(double rate, bool autoadapt, u4 max_traces, u4 max_frames_per_trace) :
  _sample(),
  _sampler_thread(nullptr),
  _queues(max_traces, max_frames_per_trace),
  _rate(rate),
  _autoadapt(autoadapt),
  _current_sampling_period_ns(compute_sampling_period(rate)),
  _max_frames_per_trace(max_frames_per_trace),
  _disenrolled(true),
  _jfrFrames(JfrCHeapObj::new_array<JfrStackFrame>(_max_frames_per_trace)) {
  assert(rate >= 0, "invariant");
}

JfrCPUTimeThreadSampler::~JfrCPUTimeThreadSampler() {
  JfrCHeapObj::free(_jfrFrames, sizeof(JfrStackFrame) * _max_frames_per_trace);
}

void JfrCPUTimeThreadSampler::on_javathread_create(JavaThread* thread) {
  if (thread->is_Compiler_thread()) {
    return;
  }
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

    int64_t period_nanos = get_sampling_period();
    period_nanos = period_nanos == 0 ? max_jlong : period_nanos;
    // If both periods are max_jlong, it implies the sampler is in the process of
    // disenrolling. Loop back for graceful disenroll by means of the semaphore.
    if (period_nanos == max_jlong) {
      continue;
    }
    int ignored = Atomic::xchg(&_lossed_samples, 0);
    if (ignored != 0) {
      log_trace(jfr)("CPU thread sampler ignored %d elements because of full queue or locks (sum %d)\n", ignored, _lossed_samples_sum);
      if (EventCPUTimeSampleLoss::is_enabled()) {
        EventCPUTimeSampleLoss event;
        event.set_starttime(JfrTicks::now());
        event.set_lostSamples(ignored);
        event.commit();
      }
    }

    os::naked_sleep(20);
  }
}

static size_t count = 0;

void JfrCPUTimeThreadSampling::send_empty_event(const JfrTicks &start_time, const JfrTicks &end_time, traceid tid, Tickspan cpu_time_period) {
  EventCPUTimeSample event;
  event.set_failed(true);
  event.set_starttime(start_time);
  event.set_endtime(end_time);
  event.set_eventThread(tid);
  event.set_stackTrace(0);
  event.set_samplingPeriod(cpu_time_period);
  event.commit();
}

void JfrCPUTimeThreadSampling::send_event(const JfrTicks &start_time, const JfrTicks &end_time, traceid sid, traceid tid, Tickspan cpu_time_period) {
  EventCPUTimeSample event;
  event.set_failed(false);
  event.set_starttime(start_time);
  event.set_endtime(end_time);
  event.set_eventThread(tid);
  event.set_stackTrace(sid);
  event.set_samplingPeriod(cpu_time_period);
  event.commit();
  count++;
  // debug print event
  printf("send_event(sid %ld, tid %ld)\n", sid, tid);
}

void JfrCPUTimeThreadSampler::post_run() {
  this->NonJavaThread::post_run();
  delete this;
}

const JfrBuffer* JfrCPUTimeThreadSampler::get_enqueue_buffer(JavaThread* thread) {
  const JfrBuffer* buffer = JfrTraceIdLoadBarrier::get_sampler_enqueue_buffer(thread);
  return buffer != nullptr ? renew_if_full(buffer, thread) : JfrTraceIdLoadBarrier::renew_sampler_enqueue_buffer(thread);
}

const JfrBuffer* JfrCPUTimeThreadSampler::renew_if_full(const JfrBuffer* enqueue_buffer, JavaThread* thread) {
  assert(enqueue_buffer != nullptr, "invariant");
  return enqueue_buffer->free_size() < _max_frames_per_trace * 2 * wordSize ? JfrTraceIdLoadBarrier::renew_sampler_enqueue_buffer(thread) : enqueue_buffer;
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
  // factor of 20 seems to be a sweet spot between memory consumption
  // and lost samples for 1ms interval, we additionally keep in a
  // predetermined range to avoid adverse effects with too many
  // or too little elements in the queue, as we only have
  // one thread that processes the queue
  int64_t period_millis = compute_sampling_period(rate) / 1000000;
  int queue_size = 20 * os::processor_count() / (period_millis > 9 ? 2 : 1);
  // the queue should not be larger a factor of 4 of the max chunk size
  // so that it usually can be processed in one go without
  // allocating a new chunk
  long max_chunk_size = JfrOptionSet::max_chunk_size() == 0 ? 12 * 1024 * 1024 : JfrOptionSet::max_chunk_size() / 2;
  int max_size = max_chunk_size / 2 / wordSize / JfrOptionSet::stackdepth();
  if (queue_size < 20 * 4) {
    queue_size = 20 * 4;
  } else if (queue_size > max_size) {
    queue_size = max_size;
  }
  _sampler = new JfrCPUTimeThreadSampler(rate, autoadapt, queue_size, JfrOptionSet::stackdepth());
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
  if (!jt->acquire_cpu_time_jfr_queue_lock()) {
    Atomic::inc(&_lossed_samples);
    Atomic::inc(&_lossed_samples_sum);
    return;
  }
  NoResourceMark rm;
  JfrSampleRequest* request = this->_queues.fresh().dequeue();
  if (request != nullptr) {
    // the sampling period might be too low for the current Linux configuration
    // so samples might be skipped and we have to compute the actual period
    int64_t period = get_sampling_period() * (info->si_overrun + 1);
    request->_cpu_time_period = Ticks(period / 1000000000.0 * JfrTime::frequency()) - Ticks(0);

    bool success = false;

    if (thread_state_in_java(jt) || thread_state_in_non_java(jt)) {
      sample_thread(*request, context, jt);
      success = true;
    }

    if (jt->cpu_time_jfr_queue().enqueue(request)) {
      if (success) {
        jt->set_has_cpu_time_jfr_requests(true);
        SafepointMechanism::arm_local_poll_release(jt);
      }
    } else {
      Atomic::inc(&_lossed_samples);
      Atomic::inc(&_lossed_samples_sum);
    }
  } else {
    Atomic::inc(&_lossed_samples);
    Atomic::inc(&_lossed_samples_sum);
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

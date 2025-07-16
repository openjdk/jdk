/*
 * Copyright (c) 2025 SAP SE. All rights reserved.
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

#include "jfr/periodic/sampling/jfrCPUTimeThreadSampler.hpp"
#include "logging/log.hpp"


#if defined(LINUX)
#include "jfr/periodic/sampling/jfrThreadSampling.hpp"
#include "jfr/support/jfrThreadLocal.hpp"
#include "jfr/utilities/jfrTime.hpp"
#include "jfr/utilities/jfrThreadIterator.hpp"
#include "jfr/utilities/jfrTypes.hpp"
#include "jfrfiles/jfrEventClasses.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/atomic.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/osThread.hpp"
#include "runtime/safepointMechanism.inline.hpp"
#include "runtime/threadSMR.hpp"
#include "runtime/vmOperation.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/ticks.hpp"

#include "signals_posix.hpp"

static const int64_t RECOMPUTE_INTERVAL_MS = 100;

static bool is_excluded(JavaThread* jt) {
  return jt->is_hidden_from_external_view() ||
         jt->jfr_thread_local()->is_excluded() ||
         jt->is_JfrRecorder_thread();
}

static JavaThread* get_java_thread_if_valid() {
  Thread* raw_thread = Thread::current_or_null_safe();
  if (raw_thread == nullptr) {
    // probably while shutting down
    return nullptr;
  }
  assert(raw_thread->is_Java_thread(), "invariant");
  JavaThread* jt = JavaThread::cast(raw_thread);
  if (is_excluded(jt) || jt->is_exiting()) {
    return nullptr;
  }
  return jt;
}

JfrCPUTimeTraceQueue::JfrCPUTimeTraceQueue(u4 capacity) :
   _data(nullptr), _capacity(capacity), _head(0), _lost_samples(0) {
  if (capacity != 0) {
    _data = JfrCHeapObj::new_array<JfrCPUTimeSampleRequest>(capacity);
  }
}

JfrCPUTimeTraceQueue::~JfrCPUTimeTraceQueue() {
  if (_data != nullptr) {
    assert(_capacity != 0, "invariant");
    JfrCHeapObj::free(_data, _capacity * sizeof(JfrCPUTimeSampleRequest));
  }
}

bool JfrCPUTimeTraceQueue::enqueue(JfrCPUTimeSampleRequest& request) {
  assert(JavaThread::current()->jfr_thread_local()->is_cpu_time_jfr_enqueue_locked(), "invariant");
  assert(&JavaThread::current()->jfr_thread_local()->cpu_time_jfr_queue() == this, "invariant");
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

static volatile u4 _lost_samples_sum = 0;

u4 JfrCPUTimeTraceQueue::size() const {
  return Atomic::load_acquire(&_head);
}

void JfrCPUTimeTraceQueue::set_size(u4 size) {
  Atomic::release_store(&_head, size);
}

u4 JfrCPUTimeTraceQueue::capacity() const {
  return _capacity;
}

void JfrCPUTimeTraceQueue::set_capacity(u4 capacity) {
  _head = 0;
  if (_data != nullptr) {
    assert(_capacity != 0, "invariant");
    JfrCHeapObj::free(_data, _capacity * sizeof(JfrCPUTimeSampleRequest));
  }
  if (capacity != 0) {
    _data = JfrCHeapObj::new_array<JfrCPUTimeSampleRequest>(capacity);
  } else {
    _data = nullptr;
  }
  _capacity = capacity;
}

bool JfrCPUTimeTraceQueue::is_empty() const {
  return Atomic::load_acquire(&_head) == 0;
}

u4 JfrCPUTimeTraceQueue::lost_samples() const {
  return Atomic::load(&_lost_samples);
}

void JfrCPUTimeTraceQueue::increment_lost_samples() {
  Atomic::inc(&_lost_samples_sum);
  Atomic::inc(&_lost_samples);
}

u4 JfrCPUTimeTraceQueue::get_and_reset_lost_samples() {
  return Atomic::xchg(&_lost_samples, (u4)0);
}

void JfrCPUTimeTraceQueue::resize(u4 capacity) {
  if (capacity != _capacity) {
    set_capacity(capacity);
  }
}

void JfrCPUTimeTraceQueue::resize_for_period(u4 period_millis) {
  u4 capacity = CPU_TIME_QUEUE_CAPACITY;
  if (period_millis > 0 && period_millis < 10) {
    capacity = (u4) ((double) capacity * 10 / period_millis);
  }
  resize(capacity);
}

void JfrCPUTimeTraceQueue::clear() {
  Atomic::release_store(&_head, (u4)0);
}

// A throttle is either a rate or a fixed period
class JfrCPUSamplerThrottle {

  union {
    double _rate;
    u8 _period_nanos;
  };
  bool _is_rate;

public:

  JfrCPUSamplerThrottle(double rate) : _rate(rate), _is_rate(true) {
    assert(rate >= 0, "invariant");
  }

  JfrCPUSamplerThrottle(u8 period_nanos) : _period_nanos(period_nanos), _is_rate(false) {}

  bool enabled() const { return _is_rate ? _rate > 0 : _period_nanos > 0; }

  int64_t compute_sampling_period() const {
    if (_is_rate) {
      if (_rate == 0) {
        return 0;
      }
      return os::active_processor_count() * 1000000000.0 / _rate;
    }
    return _period_nanos;
  }
};

class JfrCPUSamplerThread : public NonJavaThread {
  friend class JfrCPUTimeThreadSampling;
 private:
  Semaphore _sample;
  NonJavaThread* _sampler_thread;
  JfrCPUSamplerThrottle _throttle;
  volatile int64_t _current_sampling_period_ns;
  volatile bool _disenrolled;
  // top bit is used to indicate that no signal handler should proceed
  volatile u4 _active_signal_handlers;
  volatile bool _is_async_processing_of_cpu_time_jfr_requests_triggered;
  volatile bool _warned_about_timer_creation_failure;
  volatile bool _signal_handler_installed;

  static const u4 STOP_SIGNAL_BIT = 0x80000000;

  JfrCPUSamplerThread(JfrCPUSamplerThrottle& throttle);

  void start_thread();

  void enroll();
  void disenroll();
  void update_all_thread_timers();

  void recompute_period_if_needed();

  void set_throttle(JfrCPUSamplerThrottle& throttle);
  int64_t get_sampling_period() const { return Atomic::load(&_current_sampling_period_ns); };

  void sample_thread(JfrSampleRequest& request, void* ucontext, JavaThread* jt, JfrThreadLocal* tl, JfrTicks& now);

  // process the queues for all threads that are in native state (and requested to be processed)
  void stackwalk_threads_in_native();
  bool create_timer_for_thread(JavaThread* thread, timer_t &timerid);

  void stop_signal_handlers();

  // returns false if the stop signal bit was set, true otherwise
  bool increment_signal_handler_count();

  void decrement_signal_handler_count();

  void initialize_active_signal_handler_counter();

protected:
  virtual void post_run();
public:
  virtual const char* name() const { return "JFR CPU Sampler Thread"; }
  virtual const char* type_name() const { return "JfrCPUTimeSampler"; }
  void run();
  void on_javathread_create(JavaThread* thread);
  void on_javathread_terminate(JavaThread* thread);

  void handle_timer_signal(siginfo_t* info, void* context);
  bool init_timers();
  void stop_timer();

  void trigger_async_processing_of_cpu_time_jfr_requests();
};

JfrCPUSamplerThread::JfrCPUSamplerThread(JfrCPUSamplerThrottle& throttle) :
  _sample(),
  _sampler_thread(nullptr),
  _throttle(throttle),
  _current_sampling_period_ns(throttle.compute_sampling_period()),
  _disenrolled(true),
  _active_signal_handlers(STOP_SIGNAL_BIT),
  _is_async_processing_of_cpu_time_jfr_requests_triggered(false),
  _warned_about_timer_creation_failure(false),
  _signal_handler_installed(false) {
}

void JfrCPUSamplerThread::trigger_async_processing_of_cpu_time_jfr_requests() {
  Atomic::release_store(&_is_async_processing_of_cpu_time_jfr_requests_triggered, true);
}

void JfrCPUSamplerThread::on_javathread_create(JavaThread* thread) {
  if (thread->is_hidden_from_external_view() || thread->is_JfrRecorder_thread() ||
      !Atomic::load_acquire(&_signal_handler_installed)) {
    return;
  }
  JfrThreadLocal* tl = thread->jfr_thread_local();
  assert(tl != nullptr, "invariant");
  tl->cpu_time_jfr_queue().resize_for_period(_current_sampling_period_ns / 1000000);
  timer_t timerid;
  if (create_timer_for_thread(thread, timerid)) {
    tl->set_cpu_timer(&timerid);
  } else {
    if (!Atomic::or_then_fetch(&_warned_about_timer_creation_failure, true)) {
      log_warning(jfr)("Failed to create timer for a thread");
    }
    tl->deallocate_cpu_time_jfr_queue();
  }
}

void JfrCPUSamplerThread::on_javathread_terminate(JavaThread* thread) {
  JfrThreadLocal* tl = thread->jfr_thread_local();
  assert(tl != nullptr, "invariant");
  timer_t* timer = tl->cpu_timer();
  if (timer == nullptr) {
    return; // no timer was created for this thread
  }
  tl->unset_cpu_timer();
  tl->deallocate_cpu_time_jfr_queue();
  s4 lost_samples = tl->cpu_time_jfr_queue().lost_samples();
  if (lost_samples > 0) {
    JfrCPUTimeThreadSampling::send_lost_event(JfrTicks::now(), JfrThreadLocal::thread_id(thread), lost_samples);
  }
}

void JfrCPUSamplerThread::start_thread() {
  if (os::create_thread(this, os::os_thread)) {
    os::start_thread(this);
  } else {
    log_error(jfr)("Failed to create thread for thread sampling");
  }
}

void JfrCPUSamplerThread::enroll() {
  if (Atomic::cmpxchg(&_disenrolled, true, false)) {
    Atomic::store(&_warned_about_timer_creation_failure, false);
    initialize_active_signal_handler_counter();
    log_trace(jfr)("Enrolling CPU thread sampler");
    _sample.signal();
    if (!init_timers()) {
      log_error(jfr)("Failed to initialize timers for CPU thread sampler");
      disenroll();
      return;
    }
    log_trace(jfr)("Enrolled CPU thread sampler");
  }
}

void JfrCPUSamplerThread::disenroll() {
  if (!Atomic::cmpxchg(&_disenrolled, false, true)) {
    log_trace(jfr)("Disenrolling CPU thread sampler");
    if (Atomic::load_acquire(&_signal_handler_installed)) {
      stop_timer();
      stop_signal_handlers();
    }
    _sample.wait();
    log_trace(jfr)("Disenrolled CPU thread sampler");
  }
}

void JfrCPUSamplerThread::run() {
  assert(_sampler_thread == nullptr, "invariant");
  _sampler_thread = this;
  int64_t last_recompute_check = os::javaTimeNanos();
  while (true) {
    if (!_sample.trywait()) {
      // disenrolled
      _sample.wait();
    }
    _sample.signal();

    if (os::javaTimeNanos() - last_recompute_check > RECOMPUTE_INTERVAL_MS * 1000000) {
      recompute_period_if_needed();
      last_recompute_check = os::javaTimeNanos();
    }

    if (Atomic::cmpxchg(&_is_async_processing_of_cpu_time_jfr_requests_triggered, true, false)) {
      stackwalk_threads_in_native();
    }
    os::naked_sleep(100);
  }
}

void JfrCPUSamplerThread::stackwalk_threads_in_native() {
  ResourceMark rm;
  // Required to prevent JFR from sampling through an ongoing safepoint
  MutexLocker tlock(Threads_lock);
  ThreadsListHandle tlh;
  Thread* current = Thread::current();
  for (size_t i = 0; i < tlh.list()->length(); i++) {
    JavaThread* jt = tlh.list()->thread_at(i);
    JfrThreadLocal* tl = jt->jfr_thread_local();
    if (tl->wants_async_processing_of_cpu_time_jfr_requests()) {
      if (jt->thread_state() != _thread_in_native || !tl->try_acquire_cpu_time_jfr_dequeue_lock()) {
        tl->set_do_async_processing_of_cpu_time_jfr_requests(false);
        continue;
      }
      if (jt->has_last_Java_frame()) {
        JfrThreadSampling::process_cpu_time_request(jt, tl, current, false);
      } else {
        tl->set_do_async_processing_of_cpu_time_jfr_requests(false);
      }
      tl->release_cpu_time_jfr_queue_lock();
    }
  }
}

static volatile size_t count = 0;

void JfrCPUTimeThreadSampling::send_empty_event(const JfrTicks &start_time, traceid tid, Tickspan cpu_time_period) {
  EventCPUTimeSample event(UNTIMED);
  event.set_failed(true);
  event.set_starttime(start_time);
  event.set_eventThread(tid);
  event.set_stackTrace(0);
  event.set_samplingPeriod(cpu_time_period);
  event.set_biased(false);
  event.commit();
}


static volatile size_t biased_count = 0;

void JfrCPUTimeThreadSampling::send_event(const JfrTicks &start_time, traceid sid, traceid tid, Tickspan cpu_time_period, bool biased) {
  EventCPUTimeSample event(UNTIMED);
  event.set_failed(false);
  event.set_starttime(start_time);
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
    log_debug(jfr)("CPU thread sampler sent %zu events, lost %d, biased %zu\n", Atomic::load(&count), Atomic::load(&_lost_samples_sum), Atomic::load(&biased_count));
  }
}

void JfrCPUTimeThreadSampling::send_lost_event(const JfrTicks &time, traceid tid, s4 lost_samples) {
  if (!EventCPUTimeSamplesLost::is_enabled()) {
    return;
  }
  EventCPUTimeSamplesLost event(UNTIMED);
  event.set_starttime(time);
  event.set_lostSamples(lost_samples);
  event.set_eventThread(tid);
  event.commit();
}

void JfrCPUSamplerThread::post_run() {
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

void JfrCPUTimeThreadSampling::create_sampler(JfrCPUSamplerThrottle& throttle) {
  assert(_sampler == nullptr, "invariant");
  _sampler = new JfrCPUSamplerThread(throttle);
  _sampler->start_thread();
  _sampler->enroll();
}

void JfrCPUTimeThreadSampling::update_run_state(JfrCPUSamplerThrottle& throttle) {
  if (throttle.enabled()) {
    if (_sampler == nullptr) {
      create_sampler(throttle);
    } else {
      _sampler->set_throttle(throttle);
      _sampler->enroll();
    }
    return;
  }
  if (_sampler != nullptr) {
    _sampler->set_throttle(throttle);
    _sampler->disenroll();
  }
}

void JfrCPUTimeThreadSampling::set_rate(double rate) {
  if (_instance == nullptr) {
    return;
  }
  JfrCPUSamplerThrottle throttle(rate);
  instance().set_throttle_value(throttle);
}

void JfrCPUTimeThreadSampling::set_period(u8 nanos) {
  if (_instance == nullptr) {
    return;
  }
  JfrCPUSamplerThrottle throttle(nanos);
  instance().set_throttle_value(throttle);
}

void JfrCPUTimeThreadSampling::set_throttle_value(JfrCPUSamplerThrottle& throttle) {
  if (_sampler != nullptr) {
    _sampler->set_throttle(throttle);
  }
  update_run_state(throttle);
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

void JfrCPUTimeThreadSampling::trigger_async_processing_of_cpu_time_jfr_requests() {
  if (_instance != nullptr && _instance->_sampler != nullptr) {
    _instance->_sampler->trigger_async_processing_of_cpu_time_jfr_requests();
  }
}

void handle_timer_signal(int signo, siginfo_t* info, void* context) {
  assert(_instance != nullptr, "invariant");
  _instance->handle_timer_signal(info, context);
}


void JfrCPUTimeThreadSampling::handle_timer_signal(siginfo_t* info, void* context) {
  if (info->si_code != SI_TIMER) {
    // not the signal we are interested in
    return;
  }
  assert(_sampler != nullptr, "invariant");

  if (!_sampler->increment_signal_handler_count()) {
    return;
  }
  _sampler->handle_timer_signal(info, context);
  _sampler->decrement_signal_handler_count();
}

void JfrCPUSamplerThread::sample_thread(JfrSampleRequest& request, void* ucontext, JavaThread* jt, JfrThreadLocal* tl, JfrTicks& now) {
  JfrSampleRequestBuilder::build_cpu_time_sample_request(request, ucontext, jt, jt->jfr_thread_local(), now);
}

static bool check_state(JavaThread* thread) {
  switch (thread->thread_state()) {
    case _thread_in_Java:
    case _thread_in_native:
      return true;
    default:
      return false;
  }
}

void JfrCPUSamplerThread::handle_timer_signal(siginfo_t* info, void* context) {
  JfrTicks now = JfrTicks::now();
  JavaThread* jt = get_java_thread_if_valid();
  if (jt == nullptr) {
    return;
  }
  JfrThreadLocal* tl = jt->jfr_thread_local();
  JfrCPUTimeTraceQueue& queue = tl->cpu_time_jfr_queue();
  if (!check_state(jt)) {
    queue.increment_lost_samples();
    return;
  }
  if (!tl->try_acquire_cpu_time_jfr_enqueue_lock()) {
    queue.increment_lost_samples();
    return;
  }

  JfrCPUTimeSampleRequest request;
  // the sampling period might be too low for the current Linux configuration
  // so samples might be skipped and we have to compute the actual period
  int64_t period = get_sampling_period() * (info->si_overrun + 1);
  request._cpu_time_period = Ticks(period / 1000000000.0 * JfrTime::frequency()) - Ticks(0);
  sample_thread(request._request, context, jt, tl, now);

  if (queue.enqueue(request)) {
    if (queue.size() == 1) {
      tl->set_has_cpu_time_jfr_requests(true);
      SafepointMechanism::arm_local_poll_release(jt);
    }
  } else {
    queue.increment_lost_samples();
  }

  if (jt->thread_state() == _thread_in_native) {
      if (!tl->wants_async_processing_of_cpu_time_jfr_requests()) {
        tl->set_do_async_processing_of_cpu_time_jfr_requests(true);
        JfrCPUTimeThreadSampling::trigger_async_processing_of_cpu_time_jfr_requests();
      }
  } else {
    tl->set_do_async_processing_of_cpu_time_jfr_requests(false);
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

bool JfrCPUSamplerThread::create_timer_for_thread(JavaThread* thread, timer_t& timerid) {
  struct sigevent sev;
  sev.sigev_notify = SIGEV_THREAD_ID;
  sev.sigev_signo = SIG;
  sev.sigev_value.sival_ptr = nullptr;
  ((int*)&sev.sigev_notify)[1] = thread->osthread()->thread_id();
  clockid_t clock;
  int err = pthread_getcpuclockid(thread->osthread()->pthread_id(), &clock);
  if (err != 0) {
    log_error(jfr)("Failed to get clock for thread sampling: %s", os::strerror(err));
    return false;
  }
  if (timer_create(clock, &sev, &timerid) < 0) {
    return false;
  }
  int64_t period = get_sampling_period();
  if (period != 0) {
    set_timer_time(timerid, period);
  }
  return true;
}


void JfrCPUSamplerThread::stop_signal_handlers() {
  // set the stop signal bit
  Atomic::or_then_fetch(&_active_signal_handlers, STOP_SIGNAL_BIT, memory_order_acq_rel);
  while (Atomic::load_acquire(&_active_signal_handlers) > STOP_SIGNAL_BIT) {
    // wait for all signal handlers to finish
    os::naked_short_nanosleep(1000);
  }
}

// returns false if the stop signal bit was set, true otherwise
bool JfrCPUSamplerThread::increment_signal_handler_count() {
  // increment the count of active signal handlers
  u4 old_value = Atomic::fetch_then_add(&_active_signal_handlers, (u4)1, memory_order_acq_rel);
  if ((old_value & STOP_SIGNAL_BIT) != 0) {
    // if the stop signal bit was set, we are not allowed to increment
    Atomic::dec(&_active_signal_handlers, memory_order_acq_rel);
    return false;
  }
  return true;
}

void JfrCPUSamplerThread::decrement_signal_handler_count() {
  Atomic::dec(&_active_signal_handlers, memory_order_acq_rel);
}

void JfrCPUSamplerThread::initialize_active_signal_handler_counter() {
  Atomic::release_store(&_active_signal_handlers, (u4)0);
}

class VM_JFRInitializeCPUTimeSampler : public VM_Operation {
 private:
  JfrCPUSamplerThread* const _sampler;

 public:
  VM_JFRInitializeCPUTimeSampler(JfrCPUSamplerThread* sampler) : _sampler(sampler) {}

  VMOp_Type type() const { return VMOp_JFRInitializeCPUTimeSampler; }
  void doit() {
    JfrJavaThreadIterator iter;
    while (iter.has_next()) {
      _sampler->on_javathread_create(iter.next());
    }
  };
};

bool JfrCPUSamplerThread::init_timers() {
  // install sig handler for sig
  void* prev_handler = PosixSignals::get_signal_handler_for_signal(SIG);
  if ((prev_handler != SIG_DFL && prev_handler != SIG_IGN && prev_handler != (void*)::handle_timer_signal) ||
      PosixSignals::install_generic_signal_handler(SIG, (void*)::handle_timer_signal) == (void*)-1) {
    log_error(jfr)("Conflicting SIGPROF handler found: %p. CPUTimeSample events will not be recorded", prev_handler);
    return false;
  }
  Atomic::release_store(&_signal_handler_installed, true);
  VM_JFRInitializeCPUTimeSampler op(this);
  VMThread::execute(&op);
  return true;
}

class VM_JFRTerminateCPUTimeSampler : public VM_Operation {
 private:
  JfrCPUSamplerThread* const _sampler;

 public:
  VM_JFRTerminateCPUTimeSampler(JfrCPUSamplerThread* sampler) : _sampler(sampler) {}

  VMOp_Type type() const { return VMOp_JFRTerminateCPUTimeSampler; }
  void doit() {
    JfrJavaThreadIterator iter;
    while (iter.has_next()) {
      JavaThread *thread = iter.next();
      JfrThreadLocal* tl = thread->jfr_thread_local();
      timer_t* timer = tl->cpu_timer();
      if (timer == nullptr) {
        continue;
      }
      tl->deallocate_cpu_time_jfr_queue();
      tl->unset_cpu_timer();
    }
  };
};

void JfrCPUSamplerThread::stop_timer() {
  VM_JFRTerminateCPUTimeSampler op(this);
  VMThread::execute(&op);
}

void JfrCPUSamplerThread::recompute_period_if_needed() {
  int64_t current_period = get_sampling_period();
  int64_t period = _throttle.compute_sampling_period();
  if (period != current_period) {
    Atomic::store(&_current_sampling_period_ns, period);
    update_all_thread_timers();
  }
}

void JfrCPUSamplerThread::set_throttle(JfrCPUSamplerThrottle& throttle) {
  _throttle = throttle;
  if (_throttle.enabled() && Atomic::load_acquire(&_disenrolled) == false) {
    recompute_period_if_needed();
  } else {
    Atomic::store(&_current_sampling_period_ns, _throttle.compute_sampling_period());
  }
}

void JfrCPUSamplerThread::update_all_thread_timers() {
  int64_t period_millis = get_sampling_period();
  ThreadsListHandle tlh;
  for (size_t i = 0; i < tlh.length(); i++) {
    JavaThread* thread = tlh.thread_at(i);
    JfrThreadLocal* tl = thread->jfr_thread_local();
    assert(tl != nullptr, "invariant");
    timer_t* timer = tl->cpu_timer();
    if (timer != nullptr) {
      set_timer_time(*timer, period_millis);
    }
  }
}

#else

static void warn() {
  static bool displayed_warning = false;
  if (!displayed_warning) {
    warning("CPU time method sampling not supported in JFR on your platform");
    displayed_warning = true;
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

void JfrCPUTimeThreadSampling::set_rate(double rate) {
  if (rate != 0) {
    warn();
  }
}

void JfrCPUTimeThreadSampling::set_period(u8 period_nanos) {
  if (period_nanos != 0) {
    warn();
  }
}

void JfrCPUTimeThreadSampling::on_javathread_create(JavaThread* thread) {
}

void JfrCPUTimeThreadSampling::on_javathread_terminate(JavaThread* thread) {
}

#endif // defined(LINUX) && defined(INCLUDE_JFR)

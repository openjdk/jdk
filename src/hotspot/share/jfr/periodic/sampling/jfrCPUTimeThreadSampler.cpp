/*
 * Copyright (c) 2025 SAP SE. All rights reserved.
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
#include "jfr/utilities/jfrThreadIterator.hpp"
#include "jfr/utilities/jfrTime.hpp"
#include "jfr/utilities/jfrTypes.hpp"
#include "jfrfiles/jfrEventClasses.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/atomicAccess.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/osThread.hpp"
#include "runtime/safepointMechanism.inline.hpp"
#include "runtime/threadSMR.hpp"
#include "runtime/vmOperation.hpp"
#include "runtime/vmThread.hpp"
#include "signals_posix.hpp"
#include "utilities/spinYield.hpp"
#include "utilities/ticks.hpp"

static const int64_t RECOMPUTE_INTERVAL_MS = 100;

static const int CPUTIME_SIGNAL = SIGPROF;

static bool create_timer_for_thread(JavaThread* thread, timer_t& timerid) {
  struct sigevent sev;
  sev.sigev_notify = SIGEV_THREAD_ID;
  sev.sigev_signo = CPUTIME_SIGNAL;
  sev.sigev_value.sival_ptr = nullptr;
  ((int*)&sev.sigev_notify)[1] = thread->osthread()->thread_id();

  clockid_t clock;
  int err = pthread_getcpuclockid(thread->osthread()->pthread_id(), &clock);
  if (err != 0) {
    log_error(jfr)("Failed to get clock for thread sampling: %s", os::strerror(err));
    return false;
  }
  return timer_create(clock, &sev, &timerid) == 0;
}

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
  if (timer_settime(timerid, 0, &its, nullptr) != 0) {
    log_warning(jfr)("Failed to set timer for thread sampling: %s", os::strerror(os::get_last_error()));
  }
}

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

JfrCPUSamplerState::JfrCPUSamplerState() :
    _lock(UNLOCKED), _has_cpu_timer(false), _queue(nullptr), _capacity(0), _head(0),
    _lost_samples(0), _lost_samples_due_to_queue_full(0) {
}

JfrCPUSamplerState::~JfrCPUSamplerState() {
  if (_queue != nullptr) {
    assert(_capacity != 0, "invariant");
    JfrCHeapObj::free(_queue, _capacity * sizeof(JfrCPUTimeSampleRequest));
  }
  if (_has_cpu_timer) {
    timer_delete(_cpu_timer);
  }
}

JfrCPUSamplerState::LockState JfrCPUSamplerState::lock_state() const {
  return AtomicAccess::load_acquire(&_lock);
}

bool JfrCPUSamplerState::try_acquire_enqueue_lock() {
  return AtomicAccess::cmpxchg(&_lock, UNLOCKED, ENQUEUE, memory_order_seq_cst) == UNLOCKED;
}

bool JfrCPUSamplerState::try_acquire_dequeue_lock() {
  SpinYield s;
  while (true) {
    LockState state = lock_state();
    if (state == UNLOCKED &&
        AtomicAccess::cmpxchg(&_lock, UNLOCKED, DEQUEUE, memory_order_seq_cst) == UNLOCKED) {
      return true; // successfully locked for dequeuing
    }
    if (state == DEQUEUE) {
      return false; // already locked for dequeuing
    }
    s.wait(); // else wait for the lock to be released by a signal handler
  }
}

void JfrCPUSamplerState::acquire_dequeue_lock() {
  SpinYield s;
  while (lock_state() != UNLOCKED ||
         AtomicAccess::cmpxchg(&_lock, UNLOCKED, DEQUEUE, memory_order_seq_cst) != UNLOCKED) {
    s.wait();
  }
}

void JfrCPUSamplerState::release_lock() {
  assert(lock_state() != UNLOCKED, "invariant");
  AtomicAccess::release_store(&_lock, UNLOCKED);
}

bool JfrCPUSamplerState::enqueue(JfrCPUTimeSampleRequest& request) {
  assert(lock_state() == ENQUEUE, "invariant");
  assert(&JavaThread::current()->jfr_thread_local()->cpu_sampler_state() == this, "invariant");
  if (_head >= _capacity) {
    return false;
  }
  _queue[_head++] = request;
  return true;
}

JfrCPUTimeSampleRequest& JfrCPUSamplerState::request_at(u4 index) {
  assert(lock_state() == DEQUEUE, "invariant");
  assert(index < _head, "invariant");
  return _queue[index];
}

u4 JfrCPUSamplerState::request_count() const {
  assert(lock_state() != UNLOCKED, "invariant");
  return _head;
}

static volatile u4 _lost_samples_sum = 0;

void JfrCPUSamplerState::set_capacity(u4 capacity) {
  assert(lock_state() == DEQUEUE, "invariant");
  if (capacity == _capacity) {
    return;
  }
  _head = 0;
  if (_queue != nullptr) {
    assert(_capacity != 0, "invariant");
    JfrCHeapObj::free(_queue, _capacity * sizeof(JfrCPUTimeSampleRequest));
  }
  if (capacity != 0) {
    _queue = JfrCHeapObj::new_array<JfrCPUTimeSampleRequest>(capacity);
  } else {
    _queue = nullptr;
  }
  _capacity = capacity;
}

u4 JfrCPUSamplerState::lost_samples() const {
  return AtomicAccess::load(&_lost_samples);
}

void JfrCPUSamplerState::increment_lost_samples() {
  AtomicAccess::inc(&_lost_samples_sum);
  AtomicAccess::inc(&_lost_samples);
}

void JfrCPUSamplerState::increment_lost_samples_due_to_queue_full() {
  AtomicAccess::inc(&_lost_samples_due_to_queue_full);
}

u4 JfrCPUSamplerState::get_and_reset_lost_samples() {
  return AtomicAccess::xchg(&_lost_samples, (u4)0);
}

u4 JfrCPUSamplerState::get_and_reset_lost_samples_due_to_queue_full() {
  return AtomicAccess::xchg(&_lost_samples_due_to_queue_full, (u4)0);
}

bool JfrCPUSamplerState::has_cpu_timer() const {
  assert(lock_state() == DEQUEUE, "invariant");
  return _has_cpu_timer;
}

void JfrCPUSamplerState::init(timer_t timer, int64_t period_nanos) {
  assert(lock_state() == DEQUEUE, "invariant");
  assert(!_has_cpu_timer, "timer leak");
  set_capacity(CPU_TIME_QUEUE_INITIAL_CAPACITY);
  _cpu_timer = timer;
  _has_cpu_timer = true;

  // Reset counters left from the previous profiling session
  AtomicAccess::store(&_lost_samples, (u4)0);
  AtomicAccess::store(&_lost_samples_due_to_queue_full, (u4)0);

  if (period_nanos != 0) {
    set_timer_time(timer, period_nanos);
  }
}

void JfrCPUSamplerState::deallocate() {
  assert(lock_state() == DEQUEUE, "invariant");
  set_capacity(0);
  if (_has_cpu_timer) {
    timer_delete(_cpu_timer);
    _has_cpu_timer = false;
  }
}

void JfrCPUSamplerState::update_timer_period(int64_t period_nanos) {
  assert(lock_state() == DEQUEUE, "invariant");
  if (_has_cpu_timer) {
    set_timer_time(_cpu_timer, period_nanos);
  }
}

void JfrCPUSamplerState::clear() {
  assert(lock_state() == DEQUEUE, "invariant");
  _head = 0;
}

void JfrCPUSamplerState::resize_if_needed() {
  u4 lost_samples_due_to_queue_full = get_and_reset_lost_samples_due_to_queue_full();
  if (lost_samples_due_to_queue_full == 0) {
    return;
  }
  u4 capacity = _capacity;
  if (capacity < CPU_TIME_QUEUE_MAX_CAPACITY) {
    float ratio = (float)lost_samples_due_to_queue_full / (float)capacity;
    int factor = 1;
    if (ratio > 8) { // idea is to quickly scale the queue in the worst case
      factor = ratio;
    } else if (ratio > 2) {
      factor = 8;
    } else if (ratio > 0.5) {
      factor = 4;
    } else if (ratio > 0.01) {
      factor = 2;
    }
    if (factor > 1) {
      u4 new_capacity = MIN2(CPU_TIME_QUEUE_MAX_CAPACITY, capacity * factor);
      set_capacity(new_capacity);
    }
  }
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
  // top bit is used to indicate that no signal handler should proceed
  volatile u4 _active_signal_handlers;
  volatile bool _enrolled;
  volatile bool _is_async_processing_of_cpu_time_jfr_requests_triggered;
  volatile bool _warned_about_timer_creation_failure;
  DEBUG_ONLY(volatile bool _out_of_stack_walking_enabled = true;)

  static const u4 STOP_SIGNAL_BIT = 0x80000000;

  JfrCPUSamplerThread(JfrCPUSamplerThrottle& throttle);

  void start_thread();

  void enroll();
  void disenroll();
  bool is_enrolled() const { return AtomicAccess::load_acquire(&_enrolled); }

  void recompute_period_if_needed();

  void set_throttle(JfrCPUSamplerThrottle& throttle);
  int64_t get_sampling_period() const { return AtomicAccess::load(&_current_sampling_period_ns); };

  void sample_thread(JfrSampleRequest& request, void* ucontext, JavaThread* jt, JfrThreadLocal* tl, JfrTicks& now);

  // process the queues for all threads that are in native state (and requested to be processed)
  void stackwalk_threads_in_native();

  bool install_timer_signal_handler();
  void init_timers();
  void stop_timers();
  void update_all_thread_timers();

  void allow_signal_handlers();
  void stop_signal_handlers();
  void assert_no_active_signal_handlers();

  // returns false if the stop signal bit was set, true otherwise
  bool increment_signal_handler_count();
  void decrement_signal_handler_count();

protected:
  virtual void post_run();
public:
  virtual const char* name() const { return "JFR CPU Sampler Thread"; }
  virtual const char* type_name() const { return "JfrCPUTimeSampler"; }
  void run();
  void on_javathread_create(JavaThread* thread);
  void on_javathread_terminate(JavaThread* thread);

  void handle_timer_signal(siginfo_t* info, void* context);
  virtual void print_on(outputStream* st) const;

  void trigger_async_processing_of_cpu_time_jfr_requests();

  #ifdef ASSERT
  void set_out_of_stack_walking_enabled(bool runnable) {
    AtomicAccess::release_store(&_out_of_stack_walking_enabled, runnable);
  }
  #endif
};

JfrCPUSamplerThread::JfrCPUSamplerThread(JfrCPUSamplerThrottle& throttle) :
  _sample(),
  _sampler_thread(nullptr),
  _throttle(throttle),
  _current_sampling_period_ns(throttle.compute_sampling_period()),
  _active_signal_handlers(STOP_SIGNAL_BIT),
  _enrolled(false),
  _is_async_processing_of_cpu_time_jfr_requests_triggered(false),
  _warned_about_timer_creation_failure(false) {
}

void JfrCPUSamplerThread::trigger_async_processing_of_cpu_time_jfr_requests() {
  AtomicAccess::release_store(&_is_async_processing_of_cpu_time_jfr_requests_triggered, true);
}

void JfrCPUSamplerThread::on_javathread_create(JavaThread* thread) {
  if (thread->is_hidden_from_external_view() || thread->is_JfrRecorder_thread() || !is_enrolled()) {
    return;
  }

  JfrCPUSamplerState& css = thread->jfr_thread_local()->cpu_sampler_state();
  css.acquire_dequeue_lock();
  // A thread started during enrollment may have already initialized the timer
  if (!css.has_cpu_timer()) {
    timer_t timerid;
    if (create_timer_for_thread(thread, timerid)) {
      css.init(timerid, get_sampling_period());
    } else if (!AtomicAccess::fetch_then_or(&_warned_about_timer_creation_failure, true)) {
      log_warning(jfr)("Failed to create timer for a thread");
    }
  }
  css.release_lock();
}

void JfrCPUSamplerThread::on_javathread_terminate(JavaThread* thread) {
  JfrCPUSamplerState& css = thread->jfr_thread_local()->cpu_sampler_state();
  css.acquire_dequeue_lock();
  css.deallocate();
  css.release_lock();

  u4 lost_samples = css.lost_samples();
  if (lost_samples > 0 && is_enrolled()) {
    // Report lost samples only if CPU-time profiler is still running
    JfrCPUTimeThreadSampling::send_lost_event(JfrTicks::now(), lost_samples);
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
  if (!is_enrolled() && !install_timer_signal_handler()) {
    return; // error should have been already logged
  }

  if (!AtomicAccess::cmpxchg(&_enrolled, false, true, memory_order_seq_cst)) {
    AtomicAccess::store(&_warned_about_timer_creation_failure, false);
    log_trace(jfr)("Enrolling CPU thread sampler");
    _sample.signal();
    init_timers();
    allow_signal_handlers();
    log_trace(jfr)("Enrolled CPU thread sampler");
  }
}

void JfrCPUSamplerThread::disenroll() {
  if (AtomicAccess::cmpxchg(&_enrolled, true, false, memory_order_seq_cst)) {
    log_trace(jfr)("Disenrolling CPU thread sampler");
    // Ensure no signal handlers are running before deleting timers and queues
    stop_signal_handlers();
    stop_timers();
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
    DEBUG_ONLY(if (AtomicAccess::load_acquire(&_out_of_stack_walking_enabled)) {)
      if (AtomicAccess::cmpxchg(&_is_async_processing_of_cpu_time_jfr_requests_triggered, true, false)) {
        stackwalk_threads_in_native();
      }
    DEBUG_ONLY(})
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
      if (jt->thread_state() != _thread_in_native || !tl->cpu_sampler_state().try_acquire_dequeue_lock()) {
        tl->set_do_async_processing_of_cpu_time_jfr_requests(false);
        continue;
      }
      if (jt->has_last_Java_frame()) {
        JfrThreadSampling::process_cpu_time_request(jt, tl, current, false);
      } else {
        tl->set_do_async_processing_of_cpu_time_jfr_requests(false);
      }
      tl->cpu_sampler_state().release_lock();
    }
  }
}

static volatile size_t count = 0;

void JfrCPUTimeThreadSampling::send_empty_event(const JfrTicks &start_time, Tickspan cpu_time_period) {
  EventCPUTimeSample event(UNTIMED);
  event.set_failed(true);
  event.set_starttime(start_time);
  event.set_stackTrace(0);
  event.set_samplingPeriod(cpu_time_period);
  event.set_biased(false);
  event.commit();
}


static volatile size_t biased_count = 0;

void JfrCPUTimeThreadSampling::send_event(const JfrTicks &start_time, traceid sid, Tickspan cpu_time_period, bool biased) {
  EventCPUTimeSample event(UNTIMED);
  event.set_failed(false);
  event.set_starttime(start_time);
  event.set_stackTrace(sid);
  event.set_samplingPeriod(cpu_time_period);
  event.set_biased(biased);
  event.commit();
  AtomicAccess::inc(&count);
  if (biased) {
    AtomicAccess::inc(&biased_count);
  }
  if (AtomicAccess::load(&count) % 1000 == 0) {
    log_debug(jfr)("CPU thread sampler sent %zu events, lost %d, biased %zu\n", AtomicAccess::load(&count), AtomicAccess::load(&_lost_samples_sum), AtomicAccess::load(&biased_count));
  }
}

void JfrCPUTimeThreadSampling::send_lost_event(const JfrTicks &time, s4 lost_samples) {
  if (!EventCPUTimeSamplesLost::is_enabled()) {
    return;
  }
  EventCPUTimeSamplesLost event(UNTIMED);
  event.set_starttime(time);
  event.set_lostSamples(lost_samples);
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

#ifdef ASSERT
bool JfrCPUTimeThreadSampling::set_out_of_stack_walking_enabled(bool runnable) {
  if (_instance != nullptr && _instance->_sampler != nullptr) {
    _instance->_sampler->set_out_of_stack_walking_enabled(runnable);
    return true;
  } else {
    return false;
  }
}
#endif

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
  JfrCPUSamplerState& css = tl->cpu_sampler_state();
  if (!check_state(jt)) {
    css.increment_lost_samples();
    return;
  }
  if (!css.try_acquire_enqueue_lock()) {
    css.increment_lost_samples();
    return;
  }

  JfrCPUTimeSampleRequest request;
  // the sampling period might be too low for the current Linux configuration
  // so samples might be skipped and we have to compute the actual period
  int64_t period = get_sampling_period() * (info->si_overrun + 1);
  request._cpu_time_period = Ticks(period / 1000000000.0 * JfrTime::frequency()) - Ticks(0);
  sample_thread(request._request, context, jt, tl, now);

  if (css.enqueue(request)) {
    if (css.request_count() == 1) {
      tl->set_has_cpu_time_jfr_requests(true);
      SafepointMechanism::arm_local_poll_release(jt);
    }
  } else {
    css.increment_lost_samples();
    css.increment_lost_samples_due_to_queue_full();
  }

  if (jt->thread_state() == _thread_in_native) {
      if (!tl->wants_async_processing_of_cpu_time_jfr_requests()) {
        tl->set_do_async_processing_of_cpu_time_jfr_requests(true);
        JfrCPUTimeThreadSampling::trigger_async_processing_of_cpu_time_jfr_requests();
      }
  } else {
    tl->set_do_async_processing_of_cpu_time_jfr_requests(false);
  }

  css.release_lock();
}

void JfrCPUSamplerThread::allow_signal_handlers() {
  assert_no_active_signal_handlers();
  // reset STOP_SIGNAL_BIT to allow SIGPROF handlers to run
  AtomicAccess::release_store(&_active_signal_handlers, (u4)0);
}

void JfrCPUSamplerThread::stop_signal_handlers() {
  // set the stop signal bit
  AtomicAccess::or_then_fetch(&_active_signal_handlers, STOP_SIGNAL_BIT, memory_order_acq_rel);
  while (AtomicAccess::load_acquire(&_active_signal_handlers) > STOP_SIGNAL_BIT) {
    // wait for all signal handlers to finish
    os::naked_short_nanosleep(1000);
  }
}

void JfrCPUSamplerThread::assert_no_active_signal_handlers() {
  assert(AtomicAccess::load(&_active_signal_handlers) == STOP_SIGNAL_BIT, "invariant");
}

// returns false if the stop signal bit was set, true otherwise
bool JfrCPUSamplerThread::increment_signal_handler_count() {
  u4 count = AtomicAccess::load_acquire(&_active_signal_handlers);
  while (true) {
    if ((count & STOP_SIGNAL_BIT) != 0) {
      // if the stop signal bit was set, we are not allowed to increment
      return false;
    }
    // atomically increment the count of active signal handlers
    u4 expected = count;
    count = AtomicAccess::cmpxchg(&_active_signal_handlers, expected, expected + 1, memory_order_acq_rel);
    if (count == expected) {
      return true;
    }
  }
}

void JfrCPUSamplerThread::decrement_signal_handler_count() {
  AtomicAccess::dec(&_active_signal_handlers, memory_order_acq_rel);
}

bool JfrCPUSamplerThread::install_timer_signal_handler() {
  void* prev_handler = PosixSignals::get_signal_handler_for_signal(CPUTIME_SIGNAL);
  if (prev_handler == (void*)::handle_timer_signal) {
    return true; // our handler is already in place
  }
  if (prev_handler != SIG_DFL && prev_handler != SIG_IGN) {
    log_error(jfr)("Conflicting SIGPROF handler found: %p. CPUTimeSample events will not be recorded", prev_handler);
    return false;
  }
  if (PosixSignals::install_generic_signal_handler(CPUTIME_SIGNAL, (void*)::handle_timer_signal) == (void*)-1) {
    log_error(jfr)("Failed to install SIGPROF handler. CPUTimeSample events will not be recorded");
    return false;
  }
  return true;
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

void JfrCPUSamplerThread::init_timers() {
  assert_no_active_signal_handlers();
  VM_JFRInitializeCPUTimeSampler op(this);
  VMThread::execute(&op);
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
      _sampler->on_javathread_terminate(iter.next());
    }
  };
};

void JfrCPUSamplerThread::stop_timers() {
  assert_no_active_signal_handlers();
  VM_JFRTerminateCPUTimeSampler op(this);
  VMThread::execute(&op);
}

void JfrCPUSamplerThread::print_on(outputStream* st) const {
  st->print("\"%s\" ", name());
  Thread::print_on(st);
  st->cr();
}

void JfrCPUSamplerThread::recompute_period_if_needed() {
  int64_t current_period = get_sampling_period();
  int64_t period = _throttle.compute_sampling_period();
  if (period != current_period) {
    AtomicAccess::store(&_current_sampling_period_ns, period);
    update_all_thread_timers();
  }
}

void JfrCPUSamplerThread::set_throttle(JfrCPUSamplerThrottle& throttle) {
  _throttle = throttle;
  if (_throttle.enabled() && is_enrolled()) {
    recompute_period_if_needed();
  } else {
    AtomicAccess::store(&_current_sampling_period_ns, _throttle.compute_sampling_period());
  }
}

void JfrCPUSamplerThread::update_all_thread_timers() {
  int64_t period_nanos = get_sampling_period();
  ThreadsListHandle tlh;
  for (size_t i = 0; i < tlh.length(); i++) {
    JavaThread* thread = tlh.thread_at(i);
    JfrCPUSamplerState& css = thread->jfr_thread_local()->cpu_sampler_state();
    css.acquire_dequeue_lock();
    css.update_timer_period(period_nanos);
    css.release_lock();
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

#ifdef ASSERT
bool JfrCPUTimeThreadSampling::set_out_of_stack_walking_enabled(bool runnable) {
  warn();
  return false;
}
#endif

#endif // defined(LINUX) && defined(INCLUDE_JFR)

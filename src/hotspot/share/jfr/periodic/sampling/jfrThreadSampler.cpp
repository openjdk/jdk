/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "jfr/metadata/jfrSerializer.hpp"
#include "jfr/recorder/service/jfrOptionSet.hpp"
#include "jfr/periodic/sampling/jfrSampleMonitor.hpp"
#include "jfr/periodic/sampling/jfrSampleRequest.hpp"
#include "jfr/periodic/sampling/jfrThreadSampling.hpp"
#include "jfr/periodic/sampling/jfrThreadSampler.hpp"
#include "jfr/utilities/jfrTime.hpp"
#include "jfr/utilities/jfrTryLock.hpp"
#include "jfr/utilities/jfrTypes.hpp"
#include "logging/log.hpp"
#include "runtime/atomic.hpp"
#include "runtime/globals.hpp"
#include "runtime/javaThread.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/os.hpp"
#include "runtime/safepointMechanism.inline.hpp"
#include "runtime/semaphore.hpp"
#include "runtime/suspendedThreadTask.hpp"
#include "runtime/threadSMR.inline.hpp"
#include "utilities/systemMemoryBarrier.hpp"

// The JfrSamplerThread suspends, if necessary, JavaThreads for sampling.
// It creates a sample description of the top Java frame, called a Jfr Sample Request.
// The request is installed into a thread-local queue associated with the sampled thread.
// Before resuming the sampled thread, its thread-local poll page is armed.
// This mechanism lets the sampled thread discover and process the installed
// sample request at its next safepoint poll instruction.
class JfrSamplerThread : public NonJavaThread {
  friend class JfrThreadSampler;
 private:
  Semaphore _sample;
  JavaThread* _last_thread_java;
  JavaThread* _last_thread_native;
  int64_t _java_period_millis;
  int64_t _native_period_millis;
  int _cur_index;
  const u4 _max_frames;
  volatile bool _disenrolled;

  JavaThread* next_thread(ThreadsList* t_list, JavaThread* first_sampled, JavaThread* current);
  void task_stacktrace(JfrSampleRequestType type, JavaThread** last_thread);
  JfrSamplerThread(int64_t java_period_millis, int64_t native_period_millis, u4 max_frames);

  void start_thread();

  void enroll();
  void disenroll();
  void set_java_period(int64_t period_millis);
  void set_native_period(int64_t period_millis);
  bool sample_java_thread(JavaThread* jt);
  bool sample_native_thread(JavaThread* jt);

 protected:
  void run();
  virtual void post_run();

 public:
  virtual const char* name() const { return "JFR Sampler Thread"; }
  virtual const char* type_name() const { return "JfrSamplerThread"; }
  bool is_JfrSampler_thread() const { return true; }
  int64_t java_period() const { return Atomic::load(&_java_period_millis); };
  int64_t native_period() const { return Atomic::load(&_native_period_millis); };
};

JfrSamplerThread::JfrSamplerThread(int64_t java_period_millis, int64_t native_period_millis, u4 max_frames) :
  _sample(),
  _last_thread_java(nullptr),
  _last_thread_native(nullptr),
  _java_period_millis(java_period_millis),
  _native_period_millis(native_period_millis),
  _cur_index(-1),
  _max_frames(max_frames),
  _disenrolled(true) {
  assert(_java_period_millis >= 0, "invariant");
  assert(_native_period_millis >= 0, "invariant");
}

void JfrSamplerThread::post_run() {
  this->NonJavaThread::post_run();
  delete this;
}

void JfrSamplerThread::start_thread() {
  if (os::create_thread(this, os::os_thread)) {
    os::start_thread(this);
  } else {
    log_error(jfr)("Failed to create thread for thread sampling");
  }
}

void JfrSamplerThread::enroll() {
  if (_disenrolled) {
    log_trace(jfr)("Enrolling thread sampler");
    _sample.signal();
    _disenrolled = false;
  }
}

void JfrSamplerThread::disenroll() {
  if (!_disenrolled) {
    _sample.wait();
    _disenrolled = true;
    log_trace(jfr)("Disenrolling thread sampler");
  }
}

// Currently we only need to serialize a single thread state
// _thread_in_Java for the SafepointLatency event.
class VMThreadStateSerializer : public JfrSerializer {
 public:
  void serialize(JfrCheckpointWriter& writer) {
    writer.write_count(1);
    writer.write_key(_thread_in_Java);
    writer.write("_thread_in_Java");
  }
};

static inline int64_t get_monotonic_ms() {
  return os::javaTimeNanos() / 1000000;
}

void JfrSamplerThread::run() {
  JfrSerializer::register_serializer(TYPE_VMTHREADSTATE, true, new VMThreadStateSerializer());

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

    int64_t java_period_millis = java_period();
    java_period_millis = java_period_millis == 0 ? max_jlong : MAX2<int64_t>(java_period_millis, 1);
    int64_t native_period_millis = native_period();
    native_period_millis = native_period_millis == 0 ? max_jlong : MAX2<int64_t>(native_period_millis, 1);

    // If both periods are max_jlong, it implies the sampler is in the process of
    // disenrolling. Loop back for graceful disenroll by means of the semaphore.
    if (java_period_millis == max_jlong && native_period_millis == max_jlong) {
      continue;
    }

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
      os::naked_sleep(sleep_to_next);
    }

    // Note, this code used to check (next_j - sleep_to_next) <= 0,
    // but that can overflow (UB) and cause a spurious sample.
    if (next_j <= sleep_to_next) {
      task_stacktrace(JAVA_SAMPLE, &_last_thread_java);
      last_java_ms = get_monotonic_ms();
    }
    if (next_n <= sleep_to_next) {
      task_stacktrace(NATIVE_SAMPLE, &_last_thread_native);
      last_native_ms = get_monotonic_ms();
    }
  }
}

JavaThread* JfrSamplerThread::next_thread(ThreadsList* t_list, JavaThread* first_sampled, JavaThread* current) {
  assert(t_list != nullptr, "invariant");
  assert(_cur_index >= -1 && (uint)_cur_index + 1 <= t_list->length(), "invariant");
  assert((current == nullptr && -1 == _cur_index) || (t_list->find_index_of_JavaThread(current) == _cur_index), "invariant");
  if ((uint)_cur_index + 1 == t_list->length()) {
    // wrap
    _cur_index = 0;
  } else {
    _cur_index++;
  }
  assert(_cur_index >= 0 && (uint)_cur_index < t_list->length(), "invariant");
  JavaThread* const next = t_list->thread_at(_cur_index);
  return next != first_sampled ? next : nullptr;
}

static inline bool is_excluded(JavaThread* jt) {
  assert(jt != nullptr, "invariant");
  return jt->is_Compiler_thread() || jt->is_hidden_from_external_view() || jt->is_JfrRecorder_thread() || jt->jfr_thread_local()->is_excluded();
}

static const uint MAX_NR_OF_JAVA_SAMPLES = 5;
static const uint MAX_NR_OF_NATIVE_SAMPLES = 1;

void JfrSamplerThread::task_stacktrace(JfrSampleRequestType type, JavaThread** last_thread) {
  const uint sample_limit = JAVA_SAMPLE == type ? MAX_NR_OF_JAVA_SAMPLES : MAX_NR_OF_NATIVE_SAMPLES;
  uint num_samples = 0;
  JavaThread* start = nullptr;
  elapsedTimer sample_time;
  sample_time.start();
  ThreadsListHandle tlh;
  // Resolve a sample session relative start position index into the thread list array.
  // In cases where the last sampled thread is null or not-null but stale, find_index() returns -1.
  _cur_index = tlh.list()->find_index_of_JavaThread(*last_thread);
  JavaThread* current = _cur_index != -1 ? *last_thread : nullptr;

  while (num_samples < sample_limit) {
    current = next_thread(tlh.list(), start, current);
    if (current == nullptr) {
      break;
    }
    if (is_excluded(current)) {
      continue;
    }
    if (start == nullptr) {
      start = current; // remember the thread where we started to attempt sampling
    }
    bool success;
    if (JAVA_SAMPLE == type) {
      success = sample_java_thread(current);
    } else {
      assert(type == NATIVE_SAMPLE, "invariant");
      success = sample_native_thread(current);
    }
    if (success) {
      num_samples++;
    }
    if (SafepointSynchronize::is_at_safepoint()) {
      // For _thread_in_native, we cannot get the Threads_lock.
      // For _thread_in_Java, well, there are none.
      break;
    }
  }

  *last_thread = current; // remember the thread we last attempted to sample
  sample_time.stop();
  log_trace(jfr)("JFR thread sampling done in %3.7f secs with %d java %d native samples",
    sample_time.seconds(), type == JAVA_SAMPLE ? num_samples : 0, type == NATIVE_SAMPLE ? num_samples : 0);
}

// Platform-specific thread suspension and CPU context retrieval.
class OSThreadSampler : public SuspendedThreadTask {
 private:
  JfrSampleResult _result;
 public:
  OSThreadSampler(JavaThread* jt) : SuspendedThreadTask(jt),
                                    _result(THREAD_SUSPENSION_ERROR) {}
  void request_sample() { run(); }
  JfrSampleResult result() const { return _result; }

  void do_task(const SuspendedThreadTaskContext& context) {
    JavaThread* const jt = JavaThread::cast(context.thread());
    assert(jt != nullptr, "invariant");
    if (jt->thread_state() == _thread_in_Java) {
      JfrThreadLocal* const tl = jt->jfr_thread_local();
      if (tl->sample_state() == NO_SAMPLE) {
        _result = JfrSampleRequestBuilder::build_java_sample_request(context.ucontext(), tl, jt);
      }
    }
  }
};

// Sampling a thread in state _thread_in_Java
// involves a platform-specific thread suspend and CPU context retrieval.
bool JfrSamplerThread::sample_java_thread(JavaThread* jt) {
  if (jt->thread_state() != _thread_in_Java) {
    return false;
  }

  OSThreadSampler sampler(jt);
  sampler.request_sample();

  if (sampler.result() != SAMPLE_JAVA) {
    // Wrong thread state or suspension error.
    return false;
  }

  // If we get to do it before the sampled thread, we install
  // the new Jfr Sample Request into the thread-local queue
  // associated with the sampled thread. This makes the just
  // sampled thread eligible for yet another sample.
  JfrThreadLocal* const tl = jt->jfr_thread_local();
  JfrMutexTryLock lock(tl->sample_monitor());
  if (lock.acquired() && tl->sample_state() == JAVA_SAMPLE) {
    tl->enqueue_request();
    assert(tl->sample_state() == NO_SAMPLE, "invariant");
  }
  return true;
}

static JfrSamplerThread* _sampler_thread = nullptr;

// We can sample a JavaThread running in state _thread_in_native
// without thread suspension and CPU context retrieval,
// if we carefully order the loads of the thread state.
bool JfrSamplerThread::sample_native_thread(JavaThread* jt) {
  if (jt->thread_state() != _thread_in_native) {
    return false;
  }

  JfrThreadLocal* const tl = jt->jfr_thread_local();
  assert(tl != nullptr, "invariant");

  if (tl->sample_state() != NO_SAMPLE) {
    return false;
  }

  tl->set_sample_state(NATIVE_SAMPLE);

  SafepointMechanism::arm_local_poll_release(jt);

  // Take the Threads_lock for two purposes:
  // 1) Avoid sampling through a safepoint which could result
  //    in touching oops in case of virtual threads.
  // 2) Prevent JFR from issuing an epoch rotation while the sampler thread
  //    is actively processing a thread in native, as both threads are now
  //    outside the safepoint protocol.

  // OrderAccess::fence() as part of acquiring the lock prevents loads from floating up.
  JfrMutexTryLock threads_lock(Threads_lock);

  if (!threads_lock.acquired() || !jt->has_last_Java_frame()) {
    // Remove the native sample request and release the potentially waiting thread.
    JfrSampleMonitor jsm(tl);
    return false;
  }

  if (jt->thread_state() != _thread_in_native) {
    assert_lock_strong(Threads_lock);
    JfrSampleMonitor jsm(tl);
    if (jsm.is_waiting()) {
      // The thread has already returned from native,
      // now in _thread_in_vm and is waiting to be sampled.
      // Convert the native sample request into a java sample request
      // and let the thread process the ljf on its own.
      jsm.install_java_sample_request();
    }
    return false;
  }

  return JfrThreadSampling::process_native_sample_request(tl, jt, _sampler_thread);
}

void JfrSamplerThread::set_java_period(int64_t period_millis) {
  assert(period_millis >= 0, "invariant");
  Atomic::store(&_java_period_millis, period_millis);
}

void JfrSamplerThread::set_native_period(int64_t period_millis) {
  assert(period_millis >= 0, "invariant");
  Atomic::store(&_native_period_millis, period_millis);
}

// JfrThreadSampler;
static JfrThreadSampler* _instance = nullptr;

JfrThreadSampler& JfrThreadSampler::instance() {
  return *_instance;
}

JfrThreadSampler::JfrThreadSampler() {}

JfrThreadSampler::~JfrThreadSampler() {
  if (_sampler_thread != nullptr) {
    _sampler_thread->disenroll();
  }
}

JfrThreadSampler* JfrThreadSampler::create() {
  assert(_instance == nullptr, "invariant");
  _instance = new JfrThreadSampler();
  return _instance;
}

void JfrThreadSampler::destroy() {
  if (_instance != nullptr) {
    delete _instance;
    _instance = nullptr;
  }
}

#ifdef ASSERT
static void assert_periods(const JfrSamplerThread* sampler_thread, int64_t java_period_millis, int64_t native_period_millis) {
  assert(sampler_thread != nullptr, "invariant");
  assert(sampler_thread->java_period() == java_period_millis, "invariant");
  assert(sampler_thread->native_period() == native_period_millis, "invariant");
}
#endif

static void log(int64_t java_period_millis, int64_t native_period_millis) {
  log_trace(jfr)("Updated thread sampler for java: " INT64_FORMAT "  ms, native " INT64_FORMAT " ms", java_period_millis, native_period_millis);
}

void JfrThreadSampler::create_sampler(int64_t java_period_millis, int64_t native_period_millis) {
  assert(_sampler_thread == nullptr, "invariant");
  log_trace(jfr)("Creating thread sampler for java:" INT64_FORMAT " ms, native " INT64_FORMAT " ms", java_period_millis, native_period_millis);
  _sampler_thread = new JfrSamplerThread(java_period_millis, native_period_millis, JfrOptionSet::stackdepth());
  _sampler_thread->start_thread();
  _sampler_thread->enroll();
}

void JfrThreadSampler::update_run_state(int64_t java_period_millis, int64_t native_period_millis) {
  if (java_period_millis > 0 || native_period_millis > 0) {
    if (_sampler_thread == nullptr) {
      create_sampler(java_period_millis, native_period_millis);
    } else {
      _sampler_thread->enroll();
    }
    DEBUG_ONLY(assert_periods(_sampler_thread, java_period_millis, native_period_millis);)
    log(java_period_millis, native_period_millis);
    return;
  }
  if (_sampler_thread != nullptr) {
    DEBUG_ONLY(assert_periods(_sampler_thread, java_period_millis, native_period_millis);)
    _sampler_thread->disenroll();
  }
}

void JfrThreadSampler::set_period(bool is_java_period, int64_t period_millis) {
  int64_t java_period_millis = 0;
  int64_t native_period_millis = 0;
  if (is_java_period) {
    java_period_millis = period_millis;
    if (_sampler_thread != nullptr) {
      _sampler_thread->set_java_period(java_period_millis);
      native_period_millis = _sampler_thread->native_period();
    }
  } else {
    native_period_millis = period_millis;
    if (_sampler_thread != nullptr) {
      _sampler_thread->set_native_period(native_period_millis);
      java_period_millis = _sampler_thread->java_period();
    }
  }
  update_run_state(java_period_millis, native_period_millis);
}

void JfrThreadSampler::set_java_sample_period(int64_t period_millis) {
  assert(period_millis >= 0, "invariant");
  if (_instance == nullptr && 0 == period_millis) {
    return;
  }
  instance().set_period(true, period_millis);
}

void JfrThreadSampler::set_native_sample_period(int64_t period_millis) {
  assert(period_millis >= 0, "invariant");
  if (_instance == nullptr && 0 == period_millis) {
    return;
  }
  instance().set_period(false, period_millis);
}

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
#include "memory/resourceArea.hpp"
#include "runtime/atomic.hpp"
#include "runtime/orderAccess.hpp"
#include "jfr/periodic/sampling/jfrCPUTimeThreadSampler.hpp"

#if defined(LINUX)

#include "jfr/recorder/service/jfrOptionSet.hpp"
#include "jfr/recorder/service/jfrEvent.hpp"
#include "jfr/recorder/stacktrace/jfrAsyncStackTrace.hpp"
#include "jfr/utilities/jfrThreadIterator.hpp"
#include "jfr/utilities/jfrTypes.hpp"
#include "jfr/recorder/jfrRecorder.hpp"
#include "jfr/periodic/sampling/jfrCallTrace.hpp"
#include "jfr/recorder/storage/jfrBuffer.hpp"
#include "jfr/recorder/service/jfrRecorderService.hpp"
#include "jfr/utilities/jfrTime.hpp"
#include "jfrfiles/jfrEventClasses.hpp"
#include "memory/iterator.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/threadSMR.hpp"
#include "runtime/threadCrashProtection.hpp"
#include "runtime/osThread.hpp"
#include "runtime/vmOperation.hpp"
#include "runtime/vmThread.hpp"

#include "signals_posix.hpp"

static const int64_t AUTOADAPT_INTERVAL_MS = 100;

enum JfrSampleType {
  // no sample, because thread not in walkable state
  NO_SAMPLE = 0,
  // sample from thread while in Java
  JAVA_SAMPLE = 1,
  // sample from thread while in native
  NATIVE_SAMPLE = 2
};

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
    case _thread_blocked_trans:
    case _thread_in_vm_trans:
    case _thread_in_native_trans:
    case _thread_blocked:
    case _thread_in_native:
    case _thread_in_vm:
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


volatile uint32_t active_recordings = 0;

// A trace of stack frames, contains all information
// collected in the signal handler, required to create
// a JFR event with a stack trace
class JfrCPUTimeTrace {
  friend class JfrTraceQueue;
  u4 _index;
  JfrAsyncStackFrame* _frames;
  JfrAsyncStackTrace _stacktrace;
  u4 _max_frames;
  // error code for the trace, 0 if no error
  u4 _error;
  int64_t _sampling_period;

  JfrSampleType _type;
  JfrTicks _start_time;
  JfrTicks _end_time;
  JavaThread* _sampled_thread;

public:
  JfrCPUTimeTrace(u4 index, JfrAsyncStackFrame* frames, u4 max_frames):
    _index(index), _frames(frames), _stacktrace(_frames, max_frames),
    _max_frames(max_frames) {

    }

  JfrAsyncStackFrame* frames() { return _frames; }
  u4 max_frames() const { return _max_frames; }

  bool successful() const { return _error == NO_ERROR; }

  JfrSampleType type() const { return _type; }

  JfrTicks start_time() const { return _start_time; }
  void set_end_time(JfrTicks end_time) { _end_time = end_time; }
  JfrTicks end_time() const { return _end_time; }
  void set_sampled_thread(JavaThread* thread) { _sampled_thread = thread; }
  JavaThread* sampled_thread() const { return _sampled_thread; }

  JfrAsyncStackTrace& stacktrace() { return _stacktrace; }

  int64_t sampling_period() const { return _sampling_period; }

  enum SampleError {
    NO_ERROR = 0,
    ERROR_NO_TRACE = 1,
    ERROR_NO_TOPFRAME = 2,
    ERROR_JAVA_WALK_FAILED = 3,
    ERROR_NATIVE_WALK_FAILED = 4,
    ERROR_NO_TOP_METHOD = 5,
    ERROR_NO_LAST_JAVA_FRAME = 6
  };

  // Record a trace of the current thread
  void record_trace(JavaThread* jt, void* ucontext, int64_t sampling_period) {
    _stacktrace = JfrAsyncStackTrace(_frames, _max_frames);
    set_sampled_thread(jt);
    _type = NO_SAMPLE;
    _error = ERROR_NO_TRACE;
    _start_time = _end_time = JfrTicks::now();
    _sampling_period = sampling_period;
    if (!jt->in_deopt_handler() && !Universe::heap()->is_stw_gc_active())  {
      ThreadInAsgct tia(jt);
      Atomic::inc(&active_recordings);
      if (thread_state_in_java(jt)) {
        record_java_trace(jt, ucontext);
      } else if (thread_state_in_non_java(jt)) {
        record_native_trace(jt, ucontext);
      }
      Atomic::dec(&active_recordings);
    }
    _end_time = JfrTicks::now();
  }

  void classes_do(KlassClosure* cl) const {
    if (_error != NO_ERROR) {
      return;
    }
    _stacktrace.classes_do(cl);
  }

private:

  void record_java_trace(JavaThread* jt, void* ucontext) {
    _type = JAVA_SAMPLE;
    JfrGetCallTrace trace(true, jt);
    frame topframe;
    if (trace.get_topframe(ucontext, topframe)) {
      _error = _stacktrace.record_async(jt, topframe) ? NO_ERROR : ERROR_JAVA_WALK_FAILED;
    }
  }

  void record_native_trace(JavaThread* jt, void* ucontext) {
    _type = NATIVE_SAMPLE;
    _error = ERROR_NO_TRACE;
    if (!jt->has_last_Java_frame()) {
      _error = ERROR_NO_LAST_JAVA_FRAME;
      return;
    }
    frame topframe = jt->last_frame();
    frame first_java_frame;
    Method* method = nullptr;
    JfrGetCallTrace gct(false, jt);
    if (!gct.find_top_frame(topframe, &method, first_java_frame)) {
      _error = ERROR_NO_TOPFRAME;
      return;
    }
    if (method == nullptr) {
      _error = ERROR_NO_TOP_METHOD;
      return;
    }
    topframe = first_java_frame;
    _error = _stacktrace.record_async(jt, topframe) ? NO_ERROR: ERROR_NATIVE_WALK_FAILED;
  }
};

// Fixed size async-signal-safe MPMC queue backed by an array.
// Serves for passing traces from a thread being sampled (producer)
// to a thread emitting JFR events (consumer).
// Does not own any frames.
//
// _head and _tail of the queue are virtual (always increasing) positions modulo 2^32.
// Actual index into the backing array is computed as (position % _capacity).
// Generation G of an element at position P is the number of full wraps around the array:
//   G = P / _capacity
// Generation allows to disambiguate situations when _head and _tail point to the same element.
//
// Each element of the array is assigned a state, which encodes full/empty flag in bit 31
// and the generation G of the element in bits 0..30:
//   state (0,G): the element is empty and avaialble for enqueue() in generation G,
//   state (1,G): the element is full and available for dequeue() in generation G.
// Possible transitions are:
//   (0,G) --enqueue--> (1,G) --dequeue--> (0,G+1)
class JfrTraceQueue {

  struct Element {
    // Encodes full/empty flag along with generation of the element.
    // Also, establishes happens-before relationship between producer and consumer.
    // Update of this field "commits" enqueue/dequeue transaction.
    u4 _state;
    JfrCPUTimeTrace* _trace;
  };

  Element* _data;
  u4 _capacity;

  // Pad _head and _tail to avoid false sharing
  DEFINE_PAD_MINUS_SIZE(0, DEFAULT_PADDING_SIZE, sizeof(Element*) + sizeof(u4));

  volatile u4 _head;
  DEFINE_PAD_MINUS_SIZE(1, DEFAULT_PADDING_SIZE, sizeof(u4));

  volatile u4 _tail;
  DEFINE_PAD_MINUS_SIZE(2, DEFAULT_PADDING_SIZE, sizeof(u4));

  inline Element* element(u4 position) {
    return &_data[position % _capacity];
  }

  inline u4 state_empty(u4 position) {
    return (position / _capacity) & 0x7fffffff;
  }

  inline u4 state_full(u4 position) {
    return (position / _capacity) | 0x80000000;
  }

public:
  JfrTraceQueue(u4 capacity) : _capacity(capacity), _head(0), _tail(0) {
    _data = JfrCHeapObj::new_array<Element>(capacity);
    memset(_data, 0, _capacity * sizeof(Element));
  }

  ~JfrTraceQueue() {
    JfrCHeapObj::free(_data, _capacity * sizeof(Element));
  }

  bool enqueue(JfrCPUTimeTrace* trace) {
    int count = 10000;
    while (count -- > 0) {
      u4 tail = Atomic::load_acquire(&_tail);
      Element* e = element(tail);
      u4 state = Atomic::load_acquire(&e->_state);
      if (state == state_empty(tail)) {
        if (Atomic::cmpxchg(&_tail, tail, tail + 1, memory_order_seq_cst) == tail) {
            e->_trace = trace;
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

  u4 _mark_count = 0;

  JfrCPUTimeTrace* dequeue() {
    int count = 1000;
    while (count-- > 0) {
      u4 head = Atomic::load_acquire(&_head);
      Element* e = element(head);
      u4 state = Atomic::load_acquire(&e->_state);
      if (state == state_full(head)) {
        if (Atomic::cmpxchg(&_head, head, head + 1, memory_order_seq_cst) == head) {
            JfrCPUTimeTrace* trace = e->_trace;
            // After taking an element, mark it as empty in the next generation,
            // so we can reuse it again after completing the full circle
            Atomic::release_store(&e->_state, state_empty(head + _capacity));
            return trace;
        }
      } else if (state == state_empty(head)) {
        return nullptr; // Queue is empty
      } else {
        // Producer has not yet completed transaction
        // mark it as empty to prevent stalling
        if (count < 500) {
          Atomic::inc(&_mark_count);
          printf("mark count: %d\n", Atomic::load(&_mark_count));
          Atomic::release_store(&e->_state, state_empty(head));
        }
      }
    }
    return nullptr; // prevent hanging
  }

  void reset() {
    memset(_data, 0, _capacity * sizeof(Element));
    _head = 0;
    _tail = 0;
    OrderAccess::release();
  }

  void classes_do(KlassClosure* cl) {
    if (Atomic::load(&_head) == Atomic::load(&_tail)) {
      return;
    }
    // iterate over all elements between head and tail
    uint32_t head = Atomic::load(&_head);
    uint32_t tail = Atomic::load(&_tail);
    if (tail > head) {
      for (u4 i = head; i < tail; i++) {
        Element* e = element(i);
        if (e->_state == state_full(i)) {
          e->_trace->classes_do(cl);
        }
      }
    } else {
      for (u4 i = _head; i < _capacity; i++) {
        Element* e = element(i);
        if (e->_state == state_full(i)) {
          e->_trace->classes_do(cl);
        }
      }
      for (u4 i = 0; i < _tail; i++) {
        Element* e = element(i);
        if (e->_state == state_full(i)) {
          e->_trace->classes_do(cl);
        }
      }
    }
  }

};


// Two queues for sampling, fresh and filled
// at the start, all traces are in the fresh queue
class JfrTraceQueues {
  JfrAsyncStackFrame* _frames;
  JfrCPUTimeTrace* _traces;
  JfrTraceQueue _fresh;
  JfrTraceQueue _filled;
  u4 _max_traces;
  u4 _max_frames_per_trace;

public:
  JfrTraceQueues(u4 max_traces, u4 max_frames_per_trace):
    _frames(JfrCHeapObj::new_array<JfrAsyncStackFrame>(max_traces * max_frames_per_trace)),
    _traces(JfrCHeapObj::new_array<JfrCPUTimeTrace>(max_traces)), _fresh(max_traces), _filled(max_traces),
    _max_traces(max_traces), _max_frames_per_trace(max_frames_per_trace) {
    // create traces
    for (u4 i = 0; i < max_traces; i++) {
      _traces[i] = JfrCPUTimeTrace(i, &_frames[i * max_frames_per_trace], max_frames_per_trace);
    }
    // initialize fresh queue
    for (u4 i = 0; i < max_traces; i++) {
      _fresh.enqueue(&_traces[i]);
    }
  }

  ~JfrTraceQueues() {
    JfrCHeapObj::free(_frames, sizeof(JfrAsyncStackFrame) * _max_traces * _max_frames_per_trace);
    JfrCHeapObj::free(_traces, sizeof(JfrCPUTimeTrace) * _max_traces);
  }

  JfrTraceQueue& fresh() { return _fresh; }
  JfrTraceQueue& filled() { return _filled; }

  u4 max_traces() const { return _max_traces; }

  void reset() {
    _fresh.reset();
    for (u4 i = 0; i < _max_traces; i++) {
      _fresh.enqueue(&_traces[i]);
    }
    _filled.reset();
  }

  void classes_do(KlassClosure* cl) {
    _filled.classes_do(cl);
  }
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
  volatile bool _enqueue_loop_active = false;
  volatile bool _should_pause = false;
  JfrStackFrame *_jfrFrames;
  volatile int _ignore_because_queue_full = 0;
  volatile int _ignore_because_queue_full_sum = 0;

#ifdef ASSERT
  volatile bool _process_queue = true;
#endif

  const JfrBuffer* get_enqueue_buffer();
  const JfrBuffer* renew_if_full(const JfrBuffer* enqueue_buffer);


  void task_stacktrace(JfrSampleType type, JavaThread** last_thread);
  JfrCPUTimeThreadSampler(double rate, bool autoadapt, u4 max_traces, u4 max_frames_per_trace);
  ~JfrCPUTimeThreadSampler();

  void start_thread();

  void enroll();
  void disenroll();
  void update_all_thread_timers();

  void autoadapt_period_if_needed();

  bool should_process_trace_queue();
  // returns whether it returned normally and not because of lock contention
  bool process_trace_queue();

  void set_rate(double rate, bool autoadapt);
  int64_t get_sampling_period() const { return Atomic::load(&_current_sampling_period_ns); };

  void classes_do(KlassClosure* cl);

protected:
  virtual void post_run();
public:
  virtual const char* name() const { return "JFR CPU Time Thread Sampler"; }
  virtual const char* type_name() const { return "JfrCPUTimeThreadSampler"; }
  bool is_JfrSampler_thread() const { return true; }
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
    _queues.reset();
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
    int ignored = Atomic::xchg(&_ignore_because_queue_full, 0);
    if (ignored != 0) {
      log_trace(jfr)("CPU thread sampler ignored %d elements because of full queue (sum %d)\n", ignored, _ignore_because_queue_full_sum);
      if (EventCPUTimeSampleLoss::is_enabled()) {
        EventCPUTimeSampleLoss event;
        event.set_starttime(JfrTicks::now());
        event.set_lostSamples(ignored);
        event.commit();
      }
    }

    // process all filled traces
    if (process_trace_queue()) {
      int64_t sleep_to_next = period_nanos / os::processor_count() / 2;
      if (sleep_to_next >= NANOSECS_PER_SEC) {
        sleep_to_next = NANOSECS_PER_SEC - 1;
      }
      os::naked_short_nanosleep(sleep_to_next);
    } else {
      // lock contention, so make sure to sleep to allow other
      // threads to finish
      os::naked_sleep(10);
    }
  }
}

// crash protection for JfrThreadLocal::thread_id(trace->sampled_thread())
// because the thread could be deallocated between the time of recording
// and the time of processing
class JFRRecordSampledThreadCallback : public CrashProtectionCallback {
  friend class JfrCPUTimeThreadSampler;
 public:
  JFRRecordSampledThreadCallback(JavaThread* thread) :
    _thread(thread) {
  }
  virtual void call() {
    if (_thread->is_exiting()) {
      return;
    }
    _thread_id = JfrThreadLocal::thread_id(_thread);
    _valid = true;
  }
 private:
  JavaThread* _thread;
  traceid _thread_id;
  bool _valid = false;
};


static size_t count = 0;
static size_t lock_contention_loss_count = 0;


bool JfrCPUTimeThreadSampler::should_process_trace_queue() {
#ifdef ASSERT
  bool could_potentially_run = Atomic::load_acquire(&_process_queue);
#else
  bool could_potentially_run = true;
#endif
  return could_potentially_run && !Atomic::load_acquire(&_should_pause);
}

bool JfrCPUTimeThreadSampler::process_trace_queue() {
  JfrCPUTimeTrace* trace;
  const JfrBuffer* enqueue_buffer = get_enqueue_buffer();
  assert(enqueue_buffer != nullptr, "invariant");
  enqueue_buffer = renew_if_full(enqueue_buffer);

  if (!should_process_trace_queue()) {
    return true;
  }

  Atomic::release_store(&_enqueue_loop_active, true);

  bool stop = false;

  while (!stop && should_process_trace_queue() && (trace = _queues.filled().dequeue()) != nullptr) {
    {
      JfrRotationLock lock(false, 1); // don't lock automatically
      if (lock.lock(1)) {
        // we aquired the lock, all good

        // make sure we have enough space in the JFR enqueue buffer
        // create event, convert frames (resolve method ids)
        // we can't do the conversion in the signal handler,
        // as this causes segmentation faults related to the
        // enqueue buffers
        EventCPUTimeSample event;
        event.set_failed(true);
        if (trace->successful() && trace->stacktrace().nr_of_frames() > 0) {
          JfrStackTrace jfrTrace(_jfrFrames, _max_frames_per_trace);
          if (trace->stacktrace().store(&jfrTrace) && jfrTrace.nr_of_frames() > 0) {
            traceid id = JfrStackTraceRepository::add(jfrTrace);
            event.set_stackTrace(id);
            event.set_failed(false);
          } else {
            event.set_stackTrace(0);
          }
        } else {
          event.set_stackTrace(0);
        }
        event.set_starttime(trace->start_time());
        event.set_endtime(trace->end_time());
        event.set_samplingPeriod(Ticks(trace->sampling_period() / 1000000000.0 * JfrTime::frequency()) - Ticks(0));

        if (EventCPUTimeSample::is_enabled()) {
          JFRRecordSampledThreadCallback cb(trace->sampled_thread());
          ThreadCrashProtection crash_protection;
          if (crash_protection.call(cb) && cb._valid) {
            event.set_eventThread(cb._thread_id);
            event.commit();
            count++;
            if (count % 10000 == 0) {
              log_info(jfr)("CPU thread sampler count %d; lock contention loss %d; queue loss (includes lock contention) %d\n", (int) count, (int) lock_contention_loss_count, (int) Atomic::load(&_ignore_because_queue_full_sum));
            }
          } else {
            log_trace(jfr)("Couldn't obtain thread id\n");
          }
        }
      } else {
        // we didn't get the lock
        // note this down as a loss and break out of the loop
        // this doesn't happen often, but it can happen
        //
        // without this weak locking, we can cause a deadlock
        // this might happen because running this loop prevents the GC from progressing in the classes_do method.
        // when the GC runs at the safepoint induced by the JFR recorder (which obtains the lock we're waiting for)
        Atomic::inc(&_ignore_because_queue_full);
        stop = true;
        lock_contention_loss_count++;
      }
    }
    if (!stop) {
      // only renew the buffer if we did enqueue or commit anything
      enqueue_buffer = renew_if_full(enqueue_buffer);
    }
    _queues.fresh().enqueue(trace);
  }
  Atomic::release_store(&_enqueue_loop_active, false);
  return !stop;
}

void JfrCPUTimeThreadSampler::post_run() {
  this->NonJavaThread::post_run();
  delete this;
}

const JfrBuffer* JfrCPUTimeThreadSampler::get_enqueue_buffer() {
  const JfrBuffer* buffer = JfrTraceIdLoadBarrier::get_sampler_enqueue_buffer(this);
  return buffer != nullptr ? renew_if_full(buffer) : JfrTraceIdLoadBarrier::renew_sampler_enqueue_buffer(this);
}

const JfrBuffer* JfrCPUTimeThreadSampler::renew_if_full(const JfrBuffer* enqueue_buffer) {
  assert(enqueue_buffer != nullptr, "invariant");
  return enqueue_buffer->free_size() < _max_frames_per_trace * 2 * wordSize ? JfrTraceIdLoadBarrier::renew_sampler_enqueue_buffer(this) : enqueue_buffer;
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

void JfrCPUTimeThreadSampler::handle_timer_signal(siginfo_t* info, void* context) {
  JavaThread* jt = get_java_thread_if_valid();
  if (jt == nullptr) {
    return;
  }
  if (Atomic::load_acquire(&_should_pause)) {
    return;
  }
  NoResourceMark rm;
  JfrCPUTimeTrace* trace = this->_queues.fresh().dequeue();
  if (trace != nullptr) {
    // the sampling period might be too low for the current Linux configuration
    // so samples might be skipped and we have to compute the actual period
    int64_t period = get_sampling_period() * (info->si_overrun + 1);
    trace->record_trace(jt, context, period);
    this->_queues.filled().enqueue(trace);
  } else {
    Atomic::inc(&_ignore_because_queue_full);
    Atomic::inc(&_ignore_because_queue_full_sum);
  }
}

void JfrCPUTimeThreadSampling::classes_do(KlassClosure* cl) {
  if (_sampler != nullptr) {
    _sampler->classes_do(cl);
  }
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

void JfrCPUTimeThreadSampler::classes_do(KlassClosure* cl) {
  Atomic::release_store(&_should_pause, true);
  while (Atomic::load_acquire(&active_recordings) > 0) {
  }
  while (Atomic::load_acquire(&_enqueue_loop_active)) {
  }
  OrderAccess::loadload();
  _queues.classes_do(cl);
  Atomic::release_store(&_should_pause, false);
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

#ifdef ASSERT
void JfrCPUTimeThreadSampling::set_process_queue(bool process_queue) {
  if (_instance != nullptr && _instance->_sampler != nullptr) {
    Atomic::store(&_instance->_sampler->_process_queue, process_queue);
  }
}
#endif

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

void JfrCPUTimeThreadSampling::classes_do(KlassClosure* cl) {
}

#ifdef ASSERT
void JfrCPUTimeThreadSampling::set_process_queue(bool process_queue) {}
#endif

#endif // defined(LINUX) && defined(INCLUDE_JFR)

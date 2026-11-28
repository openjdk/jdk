/*
 * Copyright (c) 2025 SAP SE. All rights reserved.
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

#ifndef SHARE_JFR_PERIODIC_SAMPLING_JFRCPUTIMETHREADSAMPLER_HPP
#define SHARE_JFR_PERIODIC_SAMPLING_JFRCPUTIMETHREADSAMPLER_HPP

#include "jfr/utilities/jfrAllocation.hpp"

class JavaThread;

#if defined(LINUX)

#include "jfr/periodic/sampling/jfrSampleRequest.hpp"
#include "jfr/utilities/jfrTypes.hpp"

struct JfrCPUTimeSampleRequest {
  JfrSampleRequest _request;
  Tickspan _cpu_time_period;

  JfrCPUTimeSampleRequest() {}
};

// Per-thread state of the CPU-Time sampler. Includes a CPU timer
// and an async-signal-safe SPSC request queue backed by an array.
// All accesses to the state, with the exception of the atomic
// lost-samples counters, are guarded by a tri-state lock:
// - ENQUEUE: locked for enqueuing by a signal handler
// - DEQUEUE: locked for dequeuing, queue reallocation, or timer configuration
// - UNLOCKED: no lock held
class JfrCPUSamplerState {
 private:
  enum LockState {
    UNLOCKED,
    ENQUEUE,
    DEQUEUE
  };

  volatile LockState _lock;

  bool _has_cpu_timer;
  timer_t _cpu_timer;

  JfrCPUTimeSampleRequest* _queue;
  u4 _capacity;
  // next unfilled index
  u4 _head;

  volatile u4 _lost_samples;
  volatile u4 _lost_samples_due_to_queue_full;

  static const u4 CPU_TIME_QUEUE_INITIAL_CAPACITY = 20;
  static const u4 CPU_TIME_QUEUE_MAX_CAPACITY     = 2000;

  LockState lock_state() const;

 public:
  JfrCPUSamplerState();

  ~JfrCPUSamplerState();

  // signal safe, but can't be interleaved with dequeue
  bool enqueue(JfrCPUTimeSampleRequest& request);

  JfrCPUTimeSampleRequest& request_at(u4 index);

  u4 request_count() const;

  // deletes all samples in the queue
  void set_capacity(u4 capacity);

  u4 lost_samples() const;

  void increment_lost_samples();

  void increment_lost_samples_due_to_queue_full();

  // returns the previous lost samples count
  u4 get_and_reset_lost_samples();

  u4 get_and_reset_lost_samples_due_to_queue_full();

  void resize_if_needed();

  bool has_cpu_timer() const;

  // arms the timer and allocates the request queue
  void init(timer_t timer, int64_t period_nanos);

  void deallocate();

  void update_timer_period(int64_t period_nanos);

  void clear();

  // signal safe; fails if the lock is already held
  bool try_acquire_enqueue_lock();
  // waits while the lock is held for enqueue; fails if it is held for dequeue
  bool try_acquire_dequeue_lock();
  void acquire_dequeue_lock();
  void release_lock();
};


class JfrCPUSamplerThread;

class JfrCPUSamplerThrottle;

class JfrCPUTimeThreadSampling : public JfrCHeapObj {
  friend class JfrRecorder;
 private:

  JfrCPUSamplerThread* _sampler;

  void create_sampler(JfrCPUSamplerThrottle& throttle);
  void set_throttle_value(JfrCPUSamplerThrottle& throttle);

  JfrCPUTimeThreadSampling();
  ~JfrCPUTimeThreadSampling();

  static JfrCPUTimeThreadSampling& instance();
  static JfrCPUTimeThreadSampling* create();
  static void destroy();

  void update_run_state(JfrCPUSamplerThrottle& throttle);

 public:
  static void set_rate(double rate);
  static void set_period(u8 nanos);

  static void on_javathread_create(JavaThread* thread);
  static void on_javathread_terminate(JavaThread* thread);
  void handle_timer_signal(siginfo_t* info, void* context);

  static void send_empty_event(const JfrTicks& start_time, Tickspan cpu_time_period);
  static void send_event(const JfrTicks& start_time, traceid sid, Tickspan cpu_time_period, bool biased);
  static void send_lost_event(const JfrTicks& time, s4 lost_samples);

  static void trigger_async_processing_of_cpu_time_jfr_requests();

  DEBUG_ONLY(static bool set_out_of_stack_walking_enabled(bool runnable);)
};

#else

// a basic implementation on other platforms that
// emits warnings

class JfrCPUTimeThreadSampling : public JfrCHeapObj {
  friend class JfrRecorder;
private:
  static JfrCPUTimeThreadSampling& instance();
  static JfrCPUTimeThreadSampling* create();
  static void destroy();

 public:
  static void set_rate(double rate);
  static void set_period(u8 nanos);

  static void on_javathread_create(JavaThread* thread);
  static void on_javathread_terminate(JavaThread* thread);
  DEBUG_ONLY(static bool set_out_of_stack_walking_enabled(bool runnable));
};

#endif // defined(LINUX)


#endif // SHARE_JFR_PERIODIC_SAMPLING_JFRCPUTIMETHREADSAMPLER_HPP

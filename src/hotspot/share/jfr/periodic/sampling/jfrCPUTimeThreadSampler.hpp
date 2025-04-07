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

#ifndef SHARE_JFR_PERIODIC_SAMPLING_JFRCPUTIMETHREADSAMPLER_HPP
#define SHARE_JFR_PERIODIC_SAMPLING_JFRCPUTIMETHREADSAMPLER_HPP

#include "jfr/utilities/jfrAllocation.hpp"
#include "jfr/utilities/jfrTypes.hpp"

class JavaThread;
class NonJavaThread;

#if defined(LINUX)

#include "memory/padded.hpp"
#include "jfr/periodic/sampling/jfrSampleRequest.hpp"

// Fixed size async-signal-safe SPSC linear queue backed by an array.
// Designed to be only used under lock and read linearly
class JfrCPUTimeTraceQueue {

  // the default queue capacity, scaled if the sampling period is smaller than 10ms
  // when the thread is started
  static const u4 CPU_TIME_QUEUE_CAPACITY = 1000;

  JfrSampleRequest* _data;
  u4 _capacity;
  // next unfilled index
  volatile u4 _head;

  volatile s4 _lost_samples;

public:
  JfrCPUTimeTraceQueue(u4 capacity);

  ~JfrCPUTimeTraceQueue();

  // signal safe, but can't be interleaved with dequeue
  bool enqueue(JfrSampleRequest& trace);

  // only usable if dequeue lock aquired
  JfrSampleRequest* dequeue();

  u4 size() const;

  u4 capacity() const;

  // deletes all samples in the queue
  void set_capacity(u4 capacity);

  bool is_full() const;

  bool is_empty() const;

  s4 lost_samples() const;

  void increment_lost_samples();

  void reset_lost_samples();

  void ensure_capacity(u4 capacity);

  void ensure_capacity_for_period(u4 period_millis);

  void clear();
};


class JfrCPUTimeThreadSampler;

class JfrCPUTimeThreadSampling : public JfrCHeapObj {
  friend class JfrRecorder;
 private:

  JfrCPUTimeThreadSampler* _sampler;

  void create_sampler(double rate, bool autoadapt);
  void set_rate_value(double rate, bool autoadapt);

  JfrCPUTimeThreadSampling();
  ~JfrCPUTimeThreadSampling();

  static JfrCPUTimeThreadSampling& instance();
  static JfrCPUTimeThreadSampling* create();
  static void destroy();

  void update_run_state(double rate, bool autoadapt);

 public:
  static void set_rate(double rate, bool autoadapt);

  static void on_javathread_create(JavaThread* thread);
  static void on_javathread_terminate(JavaThread* thread);
  void handle_timer_signal(siginfo_t* info, void* context);

  static void send_empty_event(const JfrTicks& start_time, const JfrTicks& end_time, traceid tid, Tickspan cpu_time_period);
  static void send_event(const JfrTicks& start_time, const JfrTicks& end_time, traceid sid, traceid tid, Tickspan cpu_time_period, bool biased);
  static void send_lost_event(const JfrTicks& time, traceid tid, s4 lost_samples);
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
  static void set_rate(double rate, bool autoadapt);

  static void on_javathread_create(JavaThread* thread);
  static void on_javathread_terminate(JavaThread* thread);

  static void send_empty_event(const JfrTicks& start_time, const JfrTicks& end_time, traceid tid, Tickspan cpu_time_period);
  static void send_event(const JfrTicks& start_time, const JfrTicks& end_time, traceid sid, traceid tid, Tickspan cpu_time_period, bool biased);
};

#endif // defined(LINUX)


#endif // SHARE_JFR_PERIODIC_SAMPLING_JFRCPUTIMETHREADSAMPLER_HPP

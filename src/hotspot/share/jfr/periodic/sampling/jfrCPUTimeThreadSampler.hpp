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

class JavaThread;
class NonJavaThread;

#if defined(LINUX)

#include "memory/padded.hpp"

class JfrCPUTimeTrace;

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
  JfrTraceQueue(u4 capacity);

  ~JfrTraceQueue();

  bool enqueue(JfrCPUTimeTrace* trace);

  u4 _mark_count = 0;

  JfrCPUTimeTrace* dequeue();

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

  void on_safepoint(JavaThread* thread);
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

  void on_safepoint(JavaThread* thread);
};

#endif // defined(LINUX)


#endif // SHARE_JFR_PERIODIC_SAMPLING_JFRCPUTIMETHREADSAMPLER_HPP

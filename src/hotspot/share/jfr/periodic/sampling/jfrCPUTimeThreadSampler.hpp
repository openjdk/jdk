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

// Fixed size async-signal-safe SPSC linear queue backed by an array.
// Designed to be only used under lock and read linearly
template <class T>
class JfrCPUTimeStack {

  T* _data;
  u4 _capacity;
  // next unfilled index
  volatile u4 _head;

public:
  JfrCPUTimeStack(u4 capacity) : _capacity(capacity), _head(0) {
    _data = JfrCHeapObj::new_array<T>(capacity);
  }

  ~JfrCPUTimeStack() {
    JfrCHeapObj::free(_data, sizeof(T) * _capacity);
  }

  bool enqueue(T element) {
    u4 elementIndex;
    do {
      elementIndex = Atomic::load_acquire(&_head);
      if (elementIndex >= _capacity) {
        return false;
      }
    } while (Atomic::cmpxchg(&_head, elementIndex, elementIndex + 1) != elementIndex);
    _data[elementIndex] = element;
    return true;
  }

  T dequeue() {
    u4 elementIndex;
    do {
      elementIndex = Atomic::load_acquire(&_head);
      if (elementIndex == 0) {
        return nullptr;
      }
    } while (Atomic::cmpxchg(&_head, elementIndex, elementIndex - 1) != elementIndex);
    return _data[--elementIndex];
  }

  T at(u4 index) {
    assert(index < _head, "invariant");
    return _data[index];
  }

  u4 size() const {
    return Atomic::load(&_head);
  }

  void set_size(u4 size) {
    Atomic::store(&_head, size);
  }

  u4 capacity() const {
    return _capacity;
  }

  // deletes all samples in the queue
  void set_capacity(u4 capacity) {
    _head = 0;
    T* new_data = JfrCHeapObj::new_array<T>(capacity);
    JfrCHeapObj::free(_data, _capacity * sizeof(T));
    _data = new_data;
    _capacity = capacity;
  }

  bool is_full() const {
    return size() >= _capacity;
  }

  bool is_empty() const {
    return size() == 0;
  }

  void ensure_capacity(u4 capacity) {
    if (capacity != _capacity) {
      set_capacity(capacity);
    }
  }

  void clear() {
    Atomic::release_store(&_head, (u4)0);
  }
};

// Used as a per thread sampling stack
typedef JfrCPUTimeStack<JfrCPUTimeTrace*> JfrCPUTimeTraceStack;

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

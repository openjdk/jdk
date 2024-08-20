/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018, Google and/or its affiliates. All rights reserved.
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

#ifndef SHARE_RUNTIME_THREADHEAPSAMPLER_HPP
#define SHARE_RUNTIME_THREADHEAPSAMPLER_HPP

#include "gc/shared/threadLocalAllocBuffer.hpp"
#include "memory/allocation.hpp"

class ThreadHeapSamplers;

class ThreadHeapSampler {
 friend ThreadHeapSamplers;

 private:
  size_t _bytes_until_sample;
  size_t _bytes_since_last_sample_point;

  // Cheap random number generator
  static uint64_t _rnd;

  volatile int* _sampling_interval_ref;

  void pick_next_geometric_sample();
  void pick_next_sample(size_t overflowed_bytes = 0);

  static double fast_log2(const double& d);
  uint64_t next_random(uint64_t rnd);

  int get_interval();

  // TLAB associated with the thread owning this sampler
  // The lifetime of the TLAB is bound to the lifetime of the thread as is the lifetime of this sampler,
  // so we don't need to worry about it going away.
  ThreadLocalAllocBuffer* _tlab;

  volatile bool _active_flag;
 public:
  ThreadHeapSampler(ThreadLocalAllocBuffer* tlab, volatile int* sampling_interval_ref) : _bytes_until_sample(0), _bytes_since_last_sample_point(0), _sampling_interval_ref(sampling_interval_ref), _tlab(tlab), _active_flag(true) {
    _rnd = static_cast<uint32_t>(reinterpret_cast<uintptr_t>(this));
    if (_rnd == 0) {
      _rnd = 1;
    }

    // Call this after _rnd is initialized to initialize _bytes_until_sample.
    pick_next_sample();
  }

  size_t bytes_until_sample() {
    return _bytes_until_sample;
  }

  inline size_t bytes_since_last_sample_point() const {
    return _bytes_since_last_sample_point;
  }

  void update_bytes(size_t bytes, bool reset);

  bool check_for_sampling(size_t* bytes_since_allocation, size_t size_in_bytes, bool in_tlab);

  // TODO: For compatibility purposes only
  static void set_sampling_interval(int sampling_interval);
  static int get_sampling_interval();

  inline bool is_active() const {
    return _active_flag;
  }
};

class ThreadHeapSamplers {
 private:
  static volatile int _jvmti_sampling_interval;
  static volatile int _jfr_sampling_interval;

  ThreadHeapSampler _jvmti;
  ThreadHeapSampler _jfr;



 public:
  ThreadHeapSamplers(ThreadLocalAllocBuffer* tlab) : _jvmti(tlab, &_jvmti_sampling_interval), _jfr(tlab, &_jfr_sampling_interval) {
  }

  ThreadHeapSampler& jvmti() {
    return _jvmti;
  }

  ThreadHeapSampler& jfr() {
    return _jfr;
  }

  static void set_jvmti_sampling_interval(int interval);
  static int get_jvmti_sampling_interval();
  static void set_jfr_sampling_interval(int interval);
  static int get_jfr_sampling_interval();
};

#endif // SHARE_RUNTIME_THREADHEAPSAMPLER_HPP

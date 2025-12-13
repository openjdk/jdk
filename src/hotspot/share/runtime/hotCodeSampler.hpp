/*
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

#ifdef COMPILER2
#ifndef SHARE_RUNTIME_HOTCODESAMPLER_HPP
#define SHARE_RUNTIME_HOTCODESAMPLER_HPP

#include "runtime/javaThread.hpp"
#include "runtime/suspendedThreadTask.hpp"
#include "runtime/threadSMR.hpp"
#include "utilities/resizableHashTable.hpp"

// Minumum amount of time between samples
static inline int64_t min_sampling_period_ms() {
  return 5;
}

// Maximum amount of time between samples
static inline int64_t max_sampling_period_ms() {
  return 15;
}

// Generate a random sampling period between min and max
static inline int64_t rand_sampling_period_ms() {
  return os::random() % (max_sampling_period_ms() - min_sampling_period_ms() + 1) + min_sampling_period_ms();
}

class GetPCTask : public SuspendedThreadTask {
 private:
  address _pc;

  void do_task(const SuspendedThreadTaskContext& context) override{
    JavaThread* jt = JavaThread::cast(context.thread());
    if (jt->thread_state() != _thread_in_native && jt->thread_state() != _thread_in_Java) {
      return;
    }
    _pc = os::fetch_frame_from_context(context.ucontext(), nullptr, nullptr);
  }

 public:
  GetPCTask(JavaThread* thread) : SuspendedThreadTask(thread), _pc(nullptr) {}

  address pc() const {
    return _pc;
  }
};

class ThreadSampler : public StackObj {
 public:
  static const int INITIAL_TABLE_SIZE = 109;

  static ThreadSampler* _current_sampler;

  // Table of nmethods found during profiling with sample count
  ResizeableHashTable<nmethod*, int, AnyObj::C_HEAP, mtCompiler> _samples;

  int _hot_sample_count;
  int _non_profiled_sample_count;

  // List of nmethods from profiling that are candidates for grouping
  GrowableArray<nmethod*> _sorted_candidate_list;

  // Find candidate nmethods for grouping
  void generate_sorted_candidate_list();

  static ThreadSampler* get_current_sampler() {
    return _current_sampler;
  }

 public:
  ThreadSampler() : _samples(INITIAL_TABLE_SIZE, HotCodeSampleSeconds * 1000 / max_sampling_period_ms()), _hot_sample_count(0), _non_profiled_sample_count(0) {}

  // Sample and generate the candidate nmethods for grouping
  void do_sampling();

  // Get number of samples for nmethod. Returns zero if not found
  int get_sample_count(nmethod* nm) {
    int* num = _samples.get(nm);
    return num == nullptr ? 0 : *num;
  }

  // Get ratio of C2 samples from hot code heap
  double get_hot_sample_ratio() {
    return (double) _hot_sample_count / (_hot_sample_count + _non_profiled_sample_count);
  }

  // Update the number of samples from hot code heap after relocating nmethod
  void update_sample_count(nmethod* nm) {
    int samples = get_sample_count(nm);
    _hot_sample_count += samples;
    _non_profiled_sample_count -= samples;
  }

  // Returns true if we still have candidates for grouping
  bool has_candidates() {
    return !_sorted_candidate_list.is_empty();
  }

  // Get next candidate nmethod for grouping
  nmethod* get_candidate() {
    assert(has_candidates(), "must not be empty");
    return _sorted_candidate_list.pop();
  }
};

#endif // SHARE_RUNTIME_HOTCODESAMPLER_HPP
#endif // COMPILER2

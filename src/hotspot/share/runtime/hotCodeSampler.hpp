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
#include "utilities/pair.hpp"
#include "utilities/resizableHashTable.hpp"

// Generate a random sampling period between min and max
static inline uint rand_sampling_period_ms() {
  assert(HotCodeMaxSamplingMs >= HotCodeMinSamplingMs, "max cannot be smaller than min");
  julong range = (julong)HotCodeMaxSamplingMs - (julong)HotCodeMinSamplingMs + 1;
  return (uint)(os::random() % range) + HotCodeMinSamplingMs;
}

class ThreadSampler;

class Candidates : public StackObj {
 private:
  GrowableArray<Pair<nmethod*, int>> _candidates;
  int _hot_sample_count;
  int _non_profiled_sample_count;

 public:
  Candidates(ThreadSampler& sampler);

  void add_candidate(nmethod* nm, int count);
  void add_hot_sample_count(int count);
  void add_non_profiled_sample_count(int count);
  void sort();

  bool has_candidates();
  nmethod* get_candidate();
  double get_hot_sample_percent();
};

class GetPCTask : public SuspendedThreadTask {
 private:
  address _pc;

  void do_task(const SuspendedThreadTaskContext& context) override {
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
 private:
  static const int INITIAL_TABLE_SIZE = 109;

  // Table of nmethods found during profiling with sample count
  ResizeableHashTable<nmethod*, int, AnyObj::C_HEAP, mtInternal> _samples;

 public:
  ThreadSampler() : _samples(INITIAL_TABLE_SIZE, HotCodeSampleSeconds * 1000 / HotCodeMaxSamplingMs) {}

  // Iterate over and sample all Java threads
  void sample_all_java_threads();

  // Iterate over all samples with a callback function
  template<typename Function>
  void iterate_samples(Function func) {
    _samples.iterate_all(func);
  }
};

#endif // SHARE_RUNTIME_HOTCODESAMPLER_HPP
#endif // COMPILER2

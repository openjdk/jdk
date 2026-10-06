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
class nmethod;

class Candidate : public StackObj {
 private:
  nmethod* _nm;
  int _compile_id;
  int _sample_count;

 public:
  Candidate() : _nm(nullptr), _compile_id(0), _sample_count(0) {}
  Candidate(nmethod* nm, int compile_id, int samples_count) : _nm(nm),
      _compile_id(compile_id), _sample_count(samples_count) {}

  nmethod* nmethod() const {
    return _nm;
  }

  int compile_id() const {
    return _compile_id;
  }

  int sample_count() const {
    return _sample_count;
  }
};

class Candidates : public StackObj {
 private:
  GrowableArray<Candidate> _candidates;
  int _hot_sample_count;
  int _non_profiled_sample_count;

 public:
  Candidates(ThreadSampler& sampler);

  void move_samples_to_hot(int count);
  void sort();

  bool has_candidates();
  Candidate get_candidate();
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
  // hash table: key = compile_id, value = Pair<nmethod*, sample_count>
  ResizeableHashTable<int, Pair<nmethod*, int>, AnyObj::C_HEAP, mtInternal> _samples;

  int _hot_sample_count;
  int _non_profiled_sample_count;

 public:
  ThreadSampler() : _samples(INITIAL_TABLE_SIZE, HotCodeSampleSeconds * 1000 / HotCodeMaxSamplingMs), _hot_sample_count(0),
      _non_profiled_sample_count(0) {}

  // Iterate over and sample all Java threads. Return false if sampling was interrupted by JFR sampling.
  bool sample_all_java_threads();

  // Iterate over all samples with a callback function
  template<typename Function>
  void iterate_samples(Function func) {
    _samples.iterate_all(func);
  }

  int hot_sample_count() const {
    return _hot_sample_count;
  }

  int non_profiled_sample_count() const {
    return _non_profiled_sample_count;
  }
};

#endif // SHARE_RUNTIME_HOTCODESAMPLER_HPP
#endif // COMPILER2

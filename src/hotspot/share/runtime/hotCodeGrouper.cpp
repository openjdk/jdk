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
#include "code/codeCache.inline.hpp"
#include "code/compiledIC.hpp"
#include "compiler/compilerDefinitions.inline.hpp"
#include "logging/log.hpp"
#include "runtime/atomicAccess.hpp"
#include "runtime/hotCodeGrouper.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/os.hpp"
#include "runtime/suspendedThreadTask.hpp"
#include "runtime/threads.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/hashTable.hpp"

#include <math.h>

enum class State {
  NotStarted,
  Profiling,
  Grouping,
  Waiting
};

enum class ProfilingResult {
  Failure,
  Success
};

class HotCodeGrouperThread : public NonJavaThread {
 public:
  void run() override {
    HotCodeGrouper::group_nmethods_loop();
  }
  const char* name()      const override { return "Hot Code Grouper Thread"; }
  const char* type_name() const override { return "HotCodeGrouperThread"; }
};

NonJavaThread* HotCodeGrouper::_nmethod_grouper_thread = nullptr;
NMethodList    HotCodeGrouper::_unregistered_nmethods;
bool           HotCodeGrouper::_is_initialized = false;

static volatile int _state = static_cast<int>(State::NotStarted);

static size_t _new_c2_nmethods_count = 0;
static size_t _total_c2_nmethods_count = 0;

static State get_state() {
  return static_cast<State>(AtomicAccess::load_acquire(&_state));
}

static void set_state(State new_state) {
  AtomicAccess::release_store(&_state, static_cast<int>(new_state));
}

void HotCodeGrouper::initialize() {
  if (!CompilerConfig::is_c2_enabled()) {
    return; // No C2 support, no need for nmethod grouping
  }

  if (!NMethodRelocation) {
    vm_exit_during_initialization("NMethodRelocation must be enabled to use HotCodeGrouper");
  }

  if (HotCodeHeapSize == 0) {
    vm_exit_during_initialization("HotCodeHeapSize must be non-zero to use HotCodeGrouper");
  }

  _nmethod_grouper_thread = new HotCodeGrouperThread();
  if (os::create_thread(_nmethod_grouper_thread, os::os_thread)) {
    os::start_thread(_nmethod_grouper_thread);
  } else {
    vm_exit_during_initialization("Failed to create C2 nmethod grouper thread");
  }
  _is_initialized = true;
}

static void wait_for_c2_code_size_exceeding_threshold() {
  log_info(hotcodegrouper)("Waiting for C2 code size to exceed threshold for profiling...");
  MonitorLocker ml(HotCodeGrouper_lock, Mutex::_no_safepoint_check_flag);
  ml.wait();  // Wait without timeout until notified
  log_info(hotcodegrouper)("C2 code size threshold exceeded, starting profiling...");
}

static void wait_for_new_c2_nmethods() {
  set_state(State::Waiting);
  log_info(hotcodegrouper)("Waiting for new C2 nmethods for profiling...");
  MonitorLocker ml(HotCodeGrouper_lock, Mutex::_no_safepoint_check_flag);
  ml.wait();  // Wait without timeout until notified
  log_info(hotcodegrouper)("New C2 nmethods detected, starting profiling...");
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

static inline int64_t get_monotonic_ms() {
  return os::javaTimeNanos() / 1000000;
}

static inline int64_t max_sampling_period_ms() {
  // Use a maximum sampling period of 15 milliseconds.
  return 15;
}

static inline int64_t rand_sampling_period_ms() {
  // Use a random sampling period between 5 and 15 milliseconds.
  return os::random() % 11 + 5;
}

static double margin_of_error() {
  return 0.0005; // 0.05%
}

static double z_score() {
  return 1.96; // 95% confidence
}

static int min_samples() {
  // We identify hot nmethods by calculating their frequency in the collected samples.
  // We want to be confident that the identified hot nmethods are indeed hot.
  // To do that, we need to collect enough samples so that the margin of error
  // in the calculated frequencies is reasonably low.
  //
  // For example, to achieve a margin of error of 0.05% at a confidence level of 95%,
  // to detect a frequency of 0.1%, we need about 15,000 samples.
  // The formula is: n = (Z^2 * p * (1 - p)) / E^2
  // where Z is the Z-score (1.96 for 95% confidence), p is the estimated frequency (0.001),
  // and E is the margin of error (0.0005).

  return (z_score() * z_score() * HotCodeMinMethodFrequency * (1 - HotCodeMinMethodFrequency)) / (margin_of_error() * margin_of_error());
}

static inline int64_t time_ms_for_samples(int samples, int64_t sampling_period_ms) {
  return samples * sampling_period_ms;
}

using NMethodSamples = ResizeableHashTable<nmethod*, int, AnyObj::C_HEAP, mtCompiler>;

static inline bool steady_nmethod_count(int new_nmethods_count, int total_nmethods_count) {
  if (total_nmethods_count == 0) {
    return false;
  }
  constexpr double steady_threshold = 0.05; // 5%
  const double fraction_new = (double)new_nmethods_count / total_nmethods_count;
  return fraction_new < steady_threshold;
}

class ThreadSampler : public StackObj {
 private:
  static const int INITIAL_TABLE_SIZE = 109;

  NMethodSamples _samples;

  int _total_samples;
  int _total_nmethods_samples;
  int _unregistered_nmethods_samples;

 public:
  ThreadSampler() : _samples(INITIAL_TABLE_SIZE, min_samples()), _total_samples(0), _total_nmethods_samples(0), _unregistered_nmethods_samples(0) {}

  void run() {
    MutexLocker ml(Threads_lock);

    for (JavaThreadIteratorWithHandle jtiwh; JavaThread *jt = jtiwh.next(); ) {
      if (jt->is_hidden_from_external_view() ||
          jt->in_deopt_handler() ||
          (jt->thread_state() != _thread_in_native && jt->thread_state() != _thread_in_Java)) {
        continue;
      }

      GetPCTask task(jt);
      task.run();
      address pc = task.pc();
      if (pc == nullptr) {
        continue;
      }

      _total_samples++;

      if (!Interpreter::contains(pc) && CodeCache::contains(pc)) {
        nmethod* nm = CodeCache::find_blob_fast(pc)->as_nmethod_or_null();
        if (nm != nullptr) {
          bool created = false;
          int *count = _samples.put_if_absent(nm, 0, &created);
          (*count)++;
          _total_nmethods_samples++;
          if (created) {
            _samples.maybe_grow();
          }
        }
      }
    }
  }

  ProfilingResult collect_samples() {
    set_state(State::Profiling);

    log_info(hotcodegrouper)("Profiling nmethods");

    const int64_t max_needed_time_ms = 2 * time_ms_for_samples(min_samples(), max_sampling_period_ms());
    const int64_t start_time = get_monotonic_ms();

    while (true) {
      const int nmethod_count_start = _samples.number_of_entries();
      run();
      const int nmethod_count_end = _samples.number_of_entries();
      const int new_nmethods_this_iteration = nmethod_count_end - nmethod_count_start;
      const bool have_enough_samples = _total_samples >= min_samples();
      const bool is_nmethod_count_steady =
          steady_nmethod_count(new_nmethods_this_iteration, nmethod_count_end);

      if (have_enough_samples && is_nmethod_count_steady) {
        log_info(hotcodegrouper)(
            "Profiling complete: collected %d samples with %d nmethods (added "
            "%d new nmethods)",
            _total_samples, nmethod_count_end, new_nmethods_this_iteration);
        break;
      }

      // Check timeout
      if (get_monotonic_ms() - start_time >= max_needed_time_ms) {
        // Enough samples but nmethod count not stable:
        // Proceed with grouping (we have statistical confidence, even if
        // nmethods still being added)
        if (have_enough_samples && !is_nmethod_count_steady) {
          log_info(hotcodegrouper)(
              "Timeout: collected %d samples but nmethod count not steady (%d "
              "new/%d total = %.2f%%). Proceeding with grouping.",
              _total_samples, new_nmethods_this_iteration, nmethod_count_end,
              100.0 * new_nmethods_this_iteration / nmethod_count_end);
          break;
        }

        log_info(hotcodegrouper)(
            "Timeout: insufficient samples (%d/%d, nmethod count %s). "
            "Profiling failed.",
            _total_samples, min_samples(),
            is_nmethod_count_steady ? "steady" : "unstable");
        return ProfilingResult::Failure;
      }

      os::naked_sleep(rand_sampling_period_ms());
    }

    return ProfilingResult::Success;
  }

  const NMethodSamples& samples() const {
    return _samples;
  }

  int total_samples() const {
    return _total_samples;
  }

  void exclude_unregistered_nmethods(const NMethodList& unregistered) {
    NMethodListIterator it(unregistered.head());
    while (!it.is_empty()) {
      nmethod* nm = *it.next();
      int* count = _samples.get(nm);
      if (count != nullptr) {
        _unregistered_nmethods_samples += *count;
        *count = 0;
      }
    }
  }
};

void HotCodeGrouper::group_nmethods_loop() {
  wait_for_c2_code_size_exceeding_threshold();

  while (true) {
    ResourceMark rm;
    ThreadSampler sampler;

    if (sampler.collect_samples() != ProfilingResult::Failure) {
      group_nmethods(sampler);
    }
    wait_for_new_c2_nmethods();
  }
}

class HotCodeHeapCandidates : public StackObj {
 private:
  NMethodList _hot_candidates;

 public:
  void find_hot(const NMethodSamples& samples, int total_samples) {
    auto func = [&](nmethod* nm, uint64_t count) {
      double frequency = (double) count / total_samples;
      if (frequency < HotCodeMinMethodFrequency) {
        return;
      }

      if (CodeCache::get_code_blob_type(nm) != CodeBlobType::MethodHot) {
        log_trace(hotcodegrouper)("\tFound candidate nm: <%p> method: <%s> count: <" UINT64_FORMAT "> frequency: <%f>", nm, nm->method()->external_name(), count, frequency);
        _hot_candidates.add(nm);
      }
    };
    samples.iterate_all(func);
  }

  void relocate_hot() {
    NMethodListIterator it(_hot_candidates.head());
    while (!it.is_empty()) {
      nmethod* nm = *it.next();

      log_trace(hotcodegrouper)("\tRelocating nm: <%p> method: <%s>", nm, nm->method()->external_name());

      CompiledICLocker ic_locker(nm);
      nm->relocate(CodeBlobType::MethodHot);
    }
  }
};

void HotCodeGrouper::group_nmethods(ThreadSampler& sampler) {
  ResourceMark rm;
  MutexLocker ml_Compile_lock(Compile_lock);
  MutexLocker ml_CompiledIC_lock(CompiledIC_lock,
                                 Mutex::_no_safepoint_check_flag);
  MutexLocker ml_CodeCache_lock(CodeCache_lock,
                                Mutex::_no_safepoint_check_flag);
  set_state(State::Grouping);
  int total_samples = sampler.total_samples();
  log_info(hotcodegrouper)("Profiling results: %d samples, %d nmethods, %d unregistered nmethods",
      total_samples, sampler.samples().number_of_entries(), (int)_unregistered_nmethods.size());

  sampler.exclude_unregistered_nmethods(_unregistered_nmethods);
  _unregistered_nmethods.clear();

  // TODO: We might want to update nmethods GC status to prevent them from
  // getting cold.

  HotCodeHeapCandidates candidates;
  candidates.find_hot(sampler.samples(), sampler.total_samples());
  candidates.relocate_hot();
}

static size_t get_c2_code_size() {
  for (CodeHeap *ch : *CodeCache::nmethod_heaps()) {
    switch (ch->code_blob_type()) {
      case CodeBlobType::All:
      case CodeBlobType::MethodNonProfiled:
        return ch->allocated_capacity();
      default:
        break;
    }
  }
  ShouldNotReachHere(); // We always have at least one CodeHeap for C2 nmethods.
  return 0;
}

static size_t min_c2_code_size() {
  // We start profiling when we have at least 16MB of C2 nmethods.
  return 16 * M; // TODO: Make this configurable.
}

static bool percent_exceeds(size_t value, size_t total, size_t percent) {
  return (value * 100) > percent * total;
}

void HotCodeGrouper::unregister_nmethod(nmethod* nm) {
  assert_locked_or_safepoint(CodeCache_lock);
  if (!_is_initialized) {
    return;
  }

  if (get_state() == State::Profiling) {
    _unregistered_nmethods.add(nm);
  }

  if (!nm->is_compiled_by_c2()) {
    return;
  }

  // CodeCache_lock is held, so we can safely decrement the count.
  _total_c2_nmethods_count--;
}

void HotCodeGrouper::register_nmethod(nmethod* nm) {
  assert_locked_or_safepoint(CodeCache_lock);

  if (!nm->is_compiled_by_c2()) {
    return; // Only C2 nmethods are relocated to HotCodeHeap.
  }

  // CodeCache_lock is held, so we can safely increment the count.
  _total_c2_nmethods_count++;

  if (!_is_initialized) {
    return;
  }

  if (get_state() == State::NotStarted &&
      get_c2_code_size() >= min_c2_code_size()) {
    MonitorLocker ml(HotCodeGrouper_lock, Mutex::_no_safepoint_check_flag);
    ml.notify();
    return;
  }

  // CodeCache_lock is held, so we can safely increment the count.
  _new_c2_nmethods_count++;

  if (get_state() == State::Waiting &&
      percent_exceeds(_new_c2_nmethods_count, _total_c2_nmethods_count, 5)) {
    log_info(hotcodegrouper)("New C2 nmethods count exceeded threshold: %zu. Total C2 nmethods count: %zu %d",
                  _new_c2_nmethods_count, _total_c2_nmethods_count, CodeCache::nmethod_count());
    _new_c2_nmethods_count = 0;
    MonitorLocker ml(HotCodeGrouper_lock, Mutex::_no_safepoint_check_flag);
    ml.notify();
  }
}
#endif // COMPILER2

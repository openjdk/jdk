/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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
 */

#include "gc/shared/gc_globals.hpp"
#include "gc/shared/gcLogPrecious.hpp"
#include "gc/z/zAdaptiveHeap.hpp"
#include "gc/z/zAdaptiveHeap.inline.hpp"
#include "gc/z/zDriver.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zHeap.inline.hpp"
#include "gc/z/zLock.inline.hpp"
#include "gc/z/zStat.hpp"
#include "logging/log.hpp"
#include "runtime/atomicAccess.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/os.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

#include <cmath>
#include <limits>

bool ZAdaptiveHeap::_explicit_max_capacity;
bool ZAdaptiveHeap::_initialized;
bool ZAdaptiveHeap::_initialized_generation_data;
ZAdaptiveHeap::ZIntensitySmoother ZAdaptiveHeap::_gc_intensities;

Atomic<double> ZAdaptiveHeap::_young_to_old_gc_time(0.0);
Atomic<double> ZAdaptiveHeap::_accumulated_young_gc_time(0.0);
Atomic<double> ZAdaptiveHeap::_accumulated_mem_worker_time(0.0);
Atomic<double> ZAdaptiveHeap::_avg_mem_worker_cpu_overhead(0.0);
ZAdaptiveHeap::ZGenerationOverhead ZAdaptiveHeap::_young_data;
ZAdaptiveHeap::ZGenerationOverhead ZAdaptiveHeap::_old_data;
Atomic<uint> ZAdaptiveHeap::_initial_young_worker_cap;

// Calculate progression from 0 to 1 going down from a less critical availability to a more critical availability
static double calculate_progression(double availability, double from, double to) {
  return 1.0 - (availability - to) / (from - to);
}

void ZAdaptiveHeap::ZIntensitySmoother::initialize() {
  _lock = new ZLock();
}

double ZAdaptiveHeap::ZIntensitySmoother::record_and_smooth_gc_intensity(double scaled_gc_intensity) {
  precond(_lock != nullptr);
  ZLocker<ZLock> locker(_lock);
  _gc_intensities.add(scaled_gc_intensity);
  return MAX2(_gc_intensities.avg(), scaled_gc_intensity);
}

void ZAdaptiveHeap::initialize(bool explicit_max_capacity) {
  precond(!_initialized);
  _explicit_max_capacity = explicit_max_capacity;
  if (ZAdaptiveHeapSizing) {
    _gc_intensities.initialize();
  }
  _initialized = true;
}

void ZAdaptiveHeap::initialize_generation_data() {
  precond(_initialized);
  precond(!_initialized_generation_data);

  { // Setup initial machine_elapsed_system_cpu_time
    ZSystemCpuTime machine_system_time_now;
    const bool has_machine_system_time_now = machine_elapsed_system_cpu_time(machine_system_time_now);
    if (has_machine_system_time_now) {
      _young_data._last_machine_system_time = machine_system_time_now._elapsed_time;
      _old_data._last_machine_system_time = machine_system_time_now._elapsed_time;
      _young_data._machine_processor_count = machine_system_time_now._processor_count;
      _old_data._machine_processor_count = machine_system_time_now._processor_count;
    }
    _young_data._has_last_machine_system_time = has_machine_system_time_now;
    _old_data._has_last_machine_system_time = has_machine_system_time_now;
  }

  { // Setup initial container_elapsed_system_cpu_time
    ZSystemCpuTime container_system_time_now;
    const bool has_container_system_time = container_elapsed_system_cpu_time(container_system_time_now);
    if (has_container_system_time) {
      _young_data._last_container_system_time = container_system_time_now._elapsed_time;
      _old_data._last_container_system_time = container_system_time_now._elapsed_time;
      _young_data._container_processor_count = container_system_time_now._processor_count;
      _old_data._container_processor_count = container_system_time_now._processor_count;
    }
    _young_data._has_last_container_system_time = has_container_system_time;
    _old_data._has_last_container_system_time = has_container_system_time;
  }

  { // Setup initial os::elapsed_process_cpu_time
    double process_time_now;
    const bool has_process_time = os::elapsed_process_cpu_time(process_time_now);
    if (has_process_time) {
      _young_data._last_process_time = process_time_now;
      _old_data._last_process_time = process_time_now;
    }
    _young_data._has_last_process_time = has_process_time;
    _old_data._has_last_process_time = has_process_time;
  }

  { // Setup initial os::elapsedTime
    const double time_now = os::elapsedTime();
    _young_data._last_time = time_now;
    _old_data._last_time = time_now;
  }

  _initialized_generation_data = true;
}

ZMachineMemoryInfo ZAdaptiveHeap::machine_memory_info() {
  ZMachineMemoryInfo info = {};

  // Used to "latch" on to whatever validity we initially observe and make sure
  // that the same validity is continued to be observed moving forward. Observing
  // different validity during execution can lead to really bad heuristics.
  struct ZBooleanStabilityRAII {
    ZMachineMemoryInfo& _info;
    ZBooleanStabilityRAII(ZMachineMemoryInfo& info) : _info(info) {}
    ~ZBooleanStabilityRAII() {
      static bool is_valid = _info._is_valid;
      if (_info._is_valid != is_valid) {
        log_warning(gc)("The data source for machine_memory_info is unstable (expected _is_valid to be %s, got %s). "
                        "This behavior is unsupported for Adaptive Heap Sizing. Please run without Adaptive Heap Sizing using -XX:-ZAdaptiveHeapSizing/-XX:ZGCIntensity=0",
                        BOOL_TO_STR(is_valid), BOOL_TO_STR(_info._is_valid));
        os::exit(1);
      }
    }
  } is_valid_stability(info);

  pd_machine_memory_info(info);
  return info;
}

physical_memory_size_type ZAdaptiveHeap::machine_physical_memory() {
  return machine_memory_info()._physical_memory;
}

double ZAdaptiveHeap::young_to_old_gc_time() {
  return _young_to_old_gc_time.load_relaxed();
}

uint ZAdaptiveHeap::initial_young_worker_cap() {
  uint capacity = _initial_young_worker_cap.load_relaxed();
  if (capacity == 0) {
    // Not yet set; use one - there are barely any objects early on anyway
    return 1;
  }

  return capacity;
}

// For large machines we estimate their wait probability as if they have 128
// processors available. This means that for machines with more than 128
// available processors we may overestimate the impact of loads above 75%.
static constexpr int ErlangCMaxProcessors = 128;

static double* generate_factorials() {
  static double result[ErlangCMaxProcessors + 1];
  result[0] = 1.0;
  for (uint64_t i = 1; i <= ErlangCMaxProcessors; ++i) {
    result[i] = result[i - 1] * (double)i;
  }
  return result;
}

// Probability to have to wait in a queue given c servers and rho queue utilization
static double erlang_c(int c, double rho) {
  // Cap c at ErlangCMaxProcessors to avoid problems with double precision.
  c = MIN2(c, ErlangCMaxProcessors);

  static double* const factorials = generate_factorials();

  const double unutilized_reciprocal = 1.0 / (1.0 - rho);
  const double offered_load = rho * c;
  const double nominator = unutilized_reciprocal * pow(offered_load, double(c)) / factorials[c];

  double sum = 0.0;
  for (int k = 0; k < c; ++k) {
    sum += pow(offered_load, double(k)) / factorials[k];
  }

  const double denominator = nominator + sum;

  return nominator / denominator;
}

static double cpu_latency_factor(int c, double rho, double unluckyness) {
  const double prob_join_queue = erlang_c(c, rho);

  // P99 is approximately 100x more likely to join the queue
  const double p99_prob_join_queue = MIN2(prob_join_queue * unluckyness, 1.0);
  const double p99_latency_factor = p99_prob_join_queue / (1.0 - rho);

  return 1.0 + p99_latency_factor;
}

ZMemoryPressureMetrics ZAdaptiveHeap::memory_pressure_metrics() {
  precond(ZAdaptiveHeapSizing);
  const double unscaled_gc_intensity = clamp(AtomicAccess::load(&ZGCIntensity), MinZGCIntensity, MaxZGCIntensity);

  const ZMachineMemoryInfo machine_memory_info = ZAdaptiveHeap::machine_memory_info();

  const physical_memory_size_type machine_max_memory = machine_memory_info._physical_memory;

  const physical_memory_size_type machine_used_memory = machine_memory_info._is_valid
    ? machine_memory_info._physical_memory - machine_memory_info._available_memory
    : os::rss(); // Approximation for faulty os

  // The concerning threshold is after which memory utilization we start trying
  // harder to keep the memory down. There are multiple reasons for letting the GC
  // run hotter:
  // 1) We want to maintain some headroom on the machine so that we can deal with
  //    spikes without getting allocation stalls.
  // 2) It's good to let the OS keep some file system cache memory around
  // 3) On systems that compress used memory, using compressed memory is not a
  //    free lunch as it leads to page faults that compress and decompress memory.
  //    This is extra painful for a tracing GC to traverse.
  const double machine_compression_rate = machine_memory_compression_ratio(machine_used_memory, machine_max_memory);

  // When the machine memory usage is fluctuating and isn't stable, it's good to
  // increase the headroom to the memory limits, otherwise there is a higher risk
  // of an allocation stall.
  const double machine_memory_instability = ZStatSystemMemoryUsage::machine_memory_stability();

  const double memory_uncertainty = MAX2(machine_memory_instability, machine_compression_rate);
  const double machine_concerning_threshold = MIN2(ZMemoryConcerningThreshold + memory_uncertainty, 1.0);

  const double machine_concerning_vs_high_diff = ZMemoryConcerningThreshold - ZMemoryHighThreshold;
  const double machine_high_threshold = machine_concerning_threshold - machine_concerning_vs_high_diff;

  const double machine_critical_threshold = ZMemoryCriticalThreshold;

  const double far_avoid = 1.0 - ZMemoryConcerningThreshold;
  const double medium_avoid = 1.0 - ZMemoryHighThreshold;
  const double near_avoid = 1.0 - ZMemoryCriticalThreshold;

  physical_memory_size_type container_max_memory;
  physical_memory_size_type container_used_memory;
  double container_concerning_threshold;
  double container_high_threshold;
  double container_critical_threshold;

  const bool can_read_container_memory = os::is_containerized() && os::Container::used_memory(container_used_memory);
  bool has_container_limit = false;
  if (can_read_container_memory) {
    physical_memory_size_type container_critical_memory;

    // Allocation stalls at critical levels
    if (os::Container::memory_limit(container_max_memory)) {
      has_container_limit = true;
    } else {
      container_max_memory = machine_max_memory;
    }

    // Exponential increase in pressure up to critical
    physical_memory_size_type container_high_memory;
    if (os::Container::memory_throttle_limit(container_high_memory)) {
      container_max_memory = MIN2(container_max_memory, container_high_memory);
      container_critical_memory = container_max_memory;
      container_high_memory = physical_memory_size_type(MIN2(container_high_memory, container_critical_memory) * (medium_avoid / near_avoid));
      has_container_limit = true;
    } else {
      container_critical_memory = physical_memory_size_type(container_max_memory * near_avoid);
      container_high_memory = physical_memory_size_type(container_critical_memory * (medium_avoid / near_avoid));
    }

    // Linear increase in pressure up to intensity squared
    physical_memory_size_type container_min_memory;
    if (os::Container::memory_soft_limit(container_min_memory)) {
      container_min_memory = MIN2(container_min_memory, physical_memory_size_type(container_high_memory * (far_avoid / medium_avoid)));
      has_container_limit = true;
    } else {
      container_min_memory = physical_memory_size_type(container_high_memory * (far_avoid / medium_avoid));
    }

    const auto memory_threshold = [](physical_memory_size_type used_memory, physical_memory_size_type max_memory) {
      return max_memory == 0 ? 1.0 : 1.0 - (double)used_memory / (double)max_memory;
    };

    container_critical_threshold = memory_threshold(container_critical_memory, container_max_memory);
    container_high_threshold = memory_threshold(container_high_memory, container_max_memory);
    container_concerning_threshold = memory_threshold(container_min_memory, container_max_memory);

    // Adjust the threshold by how unstable the memory usage in the system is
    const double container_memory_instability = ZStatSystemMemoryUsage::container_memory_stability();
    const double instability_adjustment = MIN2(container_memory_instability, 1.0 - container_concerning_threshold);
    container_concerning_threshold += instability_adjustment;
    container_high_threshold += instability_adjustment;
  }

  const bool is_containerized = can_read_container_memory && has_container_limit;

  if (!is_containerized) {
    container_max_memory = machine_max_memory;
    container_used_memory = machine_used_memory;
    container_concerning_threshold = machine_concerning_threshold;
    container_high_threshold = machine_high_threshold;
    container_critical_threshold = machine_critical_threshold;
  }

  return {
    unscaled_gc_intensity,
    is_containerized,
    {
      machine_used_memory,
      machine_max_memory,
      machine_concerning_threshold,
      machine_high_threshold,
      machine_critical_threshold
    },
    {
      container_used_memory,
      container_max_memory,
      container_concerning_threshold,
      container_high_threshold,
      container_critical_threshold
    }
  };
}

static double system_memory_pressure(const ZSystemMemoryPressureMetrics& metrics, double unscaled_gc_intensity) {
  const physical_memory_size_type available_memory = metrics.available_memory();

  // The remaining memory reserve of the machine
  const double availability = metrics.available_fraction();

  // A number indicating how much the memory pressure should grow as the
  // memory unavailability grows
  const double pressure_rate = MAX2(unscaled_gc_intensity, 2.0);

  const double concerning = metrics._concerning_threshold;
  const double high = metrics._high_threshold;
  const double critical = metrics._critical_threshold;

  if (availability < high) {
    // When memory pressure is "high", we exponentially scale up memory pressure,
    // from the already "high" pressure induced by "concerning" memory pressure.
    const double progression = calculate_progression(availability, high, critical);
    const double exponent = 1.0 + progression;

    // Returns a value between pressure_rate and pressure_rate^3 (the exact exponent
    // depends on the values of high and critical, but will not go above 3)
    return pow(pressure_rate, exponent);
  }

  if (availability < concerning) {
    // When memory pressure is "concerning", we quadratically scale up memory
    // pressure to the "high" pressure (i.e., unscaled GC intensity).
    const double progression = calculate_progression(availability, concerning, high);
    const double progression_squared = progression * progression;
    const double pressure_factor = (pressure_rate - 1.0) * progression_squared;

    // Returns a value between 1 and pressure_rate
    return 1.0 + pressure_factor;
  }

  return 1.0;
}

double ZAdaptiveHeap::compute_memory_pressure(const ZMemoryPressureMetrics& metrics) {
  const double machine_mem_pressure = system_memory_pressure(metrics._machine, metrics._unscaled_gc_intensity);

  if (!metrics._is_containerized) {
    return machine_mem_pressure;
  }

  const double container_mem_pressure = system_memory_pressure(metrics._container, metrics._unscaled_gc_intensity);
  return MAX2(machine_mem_pressure, container_mem_pressure);
}

static bool is_system_memory_pressure_concerning(const ZSystemMemoryPressureMetrics& metrics) {
  const double availability = metrics.available_fraction();

  return availability < metrics._concerning_threshold;
}

bool ZAdaptiveHeap::is_memory_pressure_concerning(const ZMemoryPressureMetrics& metrics) {
  if (is_system_memory_pressure_concerning(metrics._machine)) {
    return true;
  }

  if (metrics._is_containerized) {
    return is_system_memory_pressure_concerning(metrics._container);
  }

  return false;
}

static bool is_system_memory_pressure_high(const ZSystemMemoryPressureMetrics& metrics) {
  const double availability = metrics.available_fraction();

  return availability < metrics._high_threshold;
}

bool ZAdaptiveHeap::is_memory_pressure_high(const ZMemoryPressureMetrics& metrics) {
  if (is_system_memory_pressure_high(metrics._machine)) {
    return true;
  }

  if (metrics._is_containerized) {
    return is_system_memory_pressure_high(metrics._container);
  }

  return false;
}

static bool is_system_memory_pressure_critical(const ZSystemMemoryPressureMetrics& metrics) {
  const double availability = metrics.available_fraction();

  return availability < metrics._critical_threshold;
}

bool ZAdaptiveHeap::is_memory_pressure_critical(const ZMemoryPressureMetrics& metrics) {
  if (is_system_memory_pressure_critical(metrics._machine)) {
    return true;
  }

  if (metrics._is_containerized) {
    return is_system_memory_pressure_critical(metrics._container);
  }

  return false;
}

void ZAdaptiveHeap::sample_generation_data(ZGenerationOverhead& generation_data) {

  { // Sample machine_elapsed_system_cpu_time
    ZSystemCpuTime machine_system_time_now;
    const bool has_machine_system_time = machine_elapsed_system_cpu_time(machine_system_time_now);
    if (has_machine_system_time) {
      if (generation_data._has_last_machine_system_time) {
        const double machine_system_time_last = generation_data._last_machine_system_time;
        const double machine_system_time = machine_system_time_now._elapsed_time - machine_system_time_last;
        generation_data._machine_system_times.add(machine_system_time);
      }

      generation_data._last_machine_system_time = machine_system_time_now._elapsed_time;
      generation_data._machine_processor_count = machine_system_time_now._processor_count;
    }
    generation_data._has_last_machine_system_time = has_machine_system_time;
  }

  { // Sample container_elapsed_system_cpu_time
    ZSystemCpuTime container_system_time_now;
    const bool has_container_system_time = container_elapsed_system_cpu_time(container_system_time_now);
    if (has_container_system_time) {
      if (generation_data._has_last_container_system_time) {
        const double container_system_time_last = generation_data._last_container_system_time;
        const double container_system_time = container_system_time_now._elapsed_time - container_system_time_last;
        generation_data._container_system_times.add(container_system_time);
      }

      generation_data._last_container_system_time = container_system_time_now._elapsed_time;
      generation_data._container_processor_count = container_system_time_now._processor_count;
    }
    generation_data._has_last_container_system_time = has_container_system_time;
  }

  { // Sample os::elapsed_process_cpu_time
    double process_time_now;
    const bool has_process_time = os::elapsed_process_cpu_time(process_time_now);
    if (has_process_time) {
      if (generation_data._has_last_process_time) {
        const double process_time_last = generation_data._last_process_time;
        const double process_time = process_time_now - process_time_last;
        generation_data._process_times.add(process_time);
      }

      generation_data._last_process_time = process_time_now;
    }
    generation_data._has_last_process_time = has_process_time;
  }

  { // Sample os::elapsedTime
    const double time_now = os::elapsedTime();
    const double time_last = generation_data._last_time;
    const double time_since_last = time_now - time_last;
    generation_data._last_time = time_now;
    generation_data._gc_times_since_last.add(time_since_last);
  }
}

ZCpuPressureMetrics ZAdaptiveHeap::cpu_pressure_metrics(ZGenerationId generation) {
  precond(_initialized_generation_data);
  const bool is_young = generation == ZGenerationId::young;
  ZGenerationOverhead& generation_data = is_young ? _young_data : _old_data;

  // Time metrics
  // Note that the system time might have poor accuracy early on; it typically
  // has 100 ms granularity. So take it with a large grain of salt early on...
  sample_generation_data(generation_data);

  // Processor metrics
  const double machine_ncpus = generation_data._has_last_machine_system_time
      ? generation_data._machine_processor_count
      : double(os::processor_count());
  const bool has_container_cpu_metrics = generation_data._has_last_container_system_time;
  const double container_ncpus = generation_data._container_processor_count;
  const bool has_container_cpu_capacity_limit =
      has_container_cpu_metrics &&
      container_ncpus < double(machine_ncpus);

  // Processors used by this process
  const double ncpus = has_container_cpu_metrics ? container_ncpus : double(machine_ncpus);

  // Cycle metrics
  ZStatCycleStats cycle_stats = ZGeneration::generation(generation)->stat_cycle()->stats();
  const double gc_time = cycle_stats._last_total_vtime + (is_young ? 0.0 : _accumulated_young_gc_time.load_relaxed());
  generation_data._gc_times.add(gc_time);
  const double mem_worker_time = is_young ? ZStatMemoryWorkers::get_and_reset_vtime() : _accumulated_mem_worker_time.load_relaxed();
  generation_data._mem_worker_times.add(mem_worker_time);

  const double avg_gc_time = generation_data._gc_times.avg();
  const double avg_time_since_last = generation_data._gc_times_since_last.avg();
  const double avg_machine_system_time = generation_data._machine_system_times.avg();
  const double avg_container_system_time = generation_data._container_system_times.avg();

  // Process times
  const double process_time = generation_data._process_times.last();
  const double avg_process_time = generation_data._process_times.avg();
  const double avg_generation_gc_cpu_overhead = percent_of(avg_gc_time, avg_process_time) / 100.0;
  const double generation_gc_cpu_overhead = percent_of(gc_time, process_time) / 100.0;

  const double avg_machine_process_cpu_load = clamp((avg_process_time / avg_time_since_last) / machine_ncpus, 0.0, 1.0);
  const double avg_machine_system_cpu_load = clamp((avg_machine_system_time / avg_time_since_last) / machine_ncpus, 0.0, 1.0);
  const double avg_container_process_cpu_load = has_container_cpu_metrics
    ? clamp((avg_process_time / avg_time_since_last) / ncpus, 0.0, 1.0)
    : avg_machine_process_cpu_load;
  const double avg_container_system_cpu_load = has_container_cpu_metrics
    ? clamp((avg_container_system_time / avg_time_since_last) / ncpus, 0.0, 1.0)
    : avg_machine_system_cpu_load;

  // Account for the overhead of old generation collections when evaluating
  // the heap efficiency for young generation collections.
  const double avg_total_gc_cpu_overhead = MIN2(avg_generation_gc_cpu_overhead / (is_young ? _young_to_old_gc_time.load_relaxed() : 1.0), 1.0);

  const double avg_mem_worker_time = generation_data._mem_worker_times.avg();
  const double avg_mem_worker_cpu_overhead = percent_of(avg_mem_worker_time, avg_gc_time);

  return {
    has_container_cpu_metrics,
    has_container_cpu_capacity_limit,
    generation_gc_cpu_overhead,
    avg_generation_gc_cpu_overhead,
    avg_total_gc_cpu_overhead,
    avg_time_since_last,
    avg_process_time,
    gc_time,
    mem_worker_time,
    avg_mem_worker_cpu_overhead,
    {
      machine_ncpus,
      avg_machine_process_cpu_load,
      avg_machine_system_cpu_load
    },
    {
      ncpus,
      avg_container_process_cpu_load,
      avg_container_system_cpu_load
    }
  };
}

static double mem_urgency_scaled_cpu_pressure(const ZSystemMemoryPressureMetrics& mem_metrics, double cpu_pressure) {
  if (cpu_pressure >= 1.0) {
    return cpu_pressure;
  }

  // As memory pressure gets high, heap must be pressed down
  if (is_system_memory_pressure_high(mem_metrics)) {
    return 1.0;
  }

  // As memory pressure gets higher, heap must be pressed down
  if (!is_system_memory_pressure_concerning(mem_metrics)) {
    return cpu_pressure;
  }

  // Calculate concerning progression towards high
  const double availability = mem_metrics.available_fraction();
  const double progression = calculate_progression(availability, mem_metrics._concerning_threshold, mem_metrics._high_threshold);

  // Scale back to 1 as we approach high mem pressure
  return cpu_pressure + (1.0 - cpu_pressure) * progression;
}

static double compute_cpu_vs_memory_pressure(const ZSystemMemoryPressureMetrics& mem_metrics, const ZSystemCpuPressureMetrics& cpu_metrics, physical_memory_size_type process_used_memory) {
  const double process_memory_usage_ratio = clamp(double(process_used_memory) / double(mem_metrics._used_memory), 0.0, 1.0);

  const double process_cpu_usage_ratio = cpu_metrics._avg_process_load / cpu_metrics._avg_system_load;

  // The GC intensity is scaled by the relationship of how many of the system's
  // used bytes belong to this process compared to how many of the used system
  // CPU ticks belong to this process. For a single application deployment this
  // has effectively no effect, while for a multi process deployment, processes
  // that are unproportionately memory bloated compared to other processes will
  // rebalance themselves better to provide more memory for other processes.
  const double process_cpu_pressure = process_memory_usage_ratio - process_cpu_usage_ratio;

  // The GC intensity is scaled by what portion of system CPU resources are being
  // used. As CPU utilization of the machine gets higher, there will be more
  // fighting between mutator threads for CPU time, affecting latencies.
  // This concern is ignored here - this heuristic is purely reasoning about
  // the CPU vs memory resource availabilities. A separate cpu_vs_latency
  // calculation estimates the latency impact of too high CPU, and adjusts.
  const double system_cpu_usage = cpu_metrics._avg_system_load;
  const double system_memory_usage = mem_metrics.used_fraction();
  const double system_cpu_pressure = system_memory_usage - system_cpu_usage;

  // Balance the forces of resource share imbalance across processes with the
  // forces of system level resource usage imbalance.
  const double cpu_vs_memory_pressure = 1.0 + clamp(process_cpu_pressure + system_cpu_pressure, -0.2, 2.0);

  // Make sure that as memory availability drops, memory pressure starts to
  // dominate the overall GC intensity; without memory the JVM dies.
  return mem_urgency_scaled_cpu_pressure(mem_metrics, cpu_vs_memory_pressure);
}

static double compute_cpu_vs_memory_pressure(const ZMemoryPressureMetrics& mem_metrics, const ZCpuPressureMetrics& cpu_metrics, physical_memory_size_type process_used_memory) {
  const double machine_cpu_pressure = compute_cpu_vs_memory_pressure(mem_metrics._machine, cpu_metrics._machine, process_used_memory);

  if (!mem_metrics._is_containerized && !cpu_metrics._has_container_cpu_capacity_limit) {
    return machine_cpu_pressure;
  }

  const double container_cpu_pressure = compute_cpu_vs_memory_pressure(mem_metrics._container, cpu_metrics._container, process_used_memory);
  return ::sqrt(container_cpu_pressure * machine_cpu_pressure);
}

static double compute_cpu_vs_latency_pressure(const ZSystemCpuPressureMetrics& machine_cpu_metrics) {
  // Approximate latency risks on the machine level using an M/M/c queue system.
  // This allows us to calculate by what factor tail latency will be affected by
  // the CPU pressure. Rather than looking at the current CPU pressure, we look
  // ahead a bit so we deal with latency problems proactively - before they arise,
  // instead of post mortem.
  // This type of M/M/c calculations make sense on a system level, but for the
  // container level, things work differently, and CPU vs memory is considered
  // purely as a resource balancing exercise, which still helps latency as well.
  double rho = MIN2(machine_cpu_metrics._avg_system_load + 0.05, 0.99);
  const int processor_count = MAX2(1, int(machine_cpu_metrics._processor_count));
  const double p99_response_time_scaling = cpu_latency_factor(processor_count, rho, 100.0);
  return 1.0 / MIN2(p99_response_time_scaling, 5.0);
}

static double compute_cpu_vs_latency_pressure(const ZMemoryPressureMetrics& mem_metrics, const ZCpuPressureMetrics& cpu_metrics) {
  const double machine_cpu_vs_latency_pressure = compute_cpu_vs_latency_pressure(cpu_metrics._machine);

  // Make sure that as machine memory availability drops, memory pressure starts to
  // dominate the overall GC intensity; without memory the JVM dies.
  const double machine_scaled_pressure = mem_urgency_scaled_cpu_pressure(mem_metrics._machine, machine_cpu_vs_latency_pressure);

  if (!mem_metrics._is_containerized && !cpu_metrics._has_container_cpu_capacity_limit) {
    return machine_scaled_pressure;
  }

  // Make sure that as container availability drops, memory pressure starts to
  // dominate the overall GC intensity; without memory the JVM dies.
  // Note that using the machine CPU vs latency pressure here is intentional;
  // the probability of getting placed in a run queue is on machine level,
  // not on the container level.
  const double container_scaled_pressure = mem_urgency_scaled_cpu_pressure(mem_metrics._container, machine_cpu_vs_latency_pressure);

  return sqrt(machine_scaled_pressure * container_scaled_pressure);
}

ZResourcePressure ZAdaptiveHeap::compute_pressures(const ZMemoryPressureMetrics& mem_metrics, const ZCpuPressureMetrics& cpu_metrics, size_t projected_process_used_memory) {
  precond(ZAdaptiveHeapSizing);

  const double mem_pressure = compute_memory_pressure(mem_metrics);
  const double cpu_vs_memory_pressure = compute_cpu_vs_memory_pressure(mem_metrics, cpu_metrics, projected_process_used_memory);
  const double cpu_vs_latency_pressure = compute_cpu_vs_latency_pressure(mem_metrics, cpu_metrics);

  // Use sqrt to compute geometric mean
  const double cpu_pressure = ::sqrt(cpu_vs_memory_pressure * cpu_vs_latency_pressure);

  // The combined forces of memory vs CPU. The one force... TO RULE THEM ALL!!
  const double resource_pressure = mem_pressure * cpu_pressure;

  const double scaled_gc_intensity = mem_metrics._unscaled_gc_intensity * resource_pressure;

  return {
    scaled_gc_intensity,
    cpu_pressure,
    mem_pressure,
    cpu_vs_memory_pressure,
    cpu_vs_latency_pressure
  };
}

double ZAdaptiveHeap::smoothed_gc_intensity(double scaled_gc_intensity) {
  precond(_initialized);
  return _gc_intensities.record_and_smooth_gc_intensity(scaled_gc_intensity);
}

bool ZAdaptiveHeap::machine_elapsed_system_cpu_time(ZSystemCpuTime& value) {
  return pd_machine_elapsed_system_cpu_time(value);
}

bool ZAdaptiveHeap::container_elapsed_system_cpu_time(ZSystemCpuTime& value) {
  if (os::is_containerized()) {
    return pd_container_elapsed_system_cpu_time(value);
  }

  return false;
}

double ZAdaptiveHeap::machine_memory_compression_ratio(physical_memory_size_type machine_used_memory,
                                                       physical_memory_size_type machine_max_memory) {
  physical_memory_size_type machine_compressed_memory;
  if (pd_machine_compressed_memory(machine_compressed_memory)) {
    machine_compressed_memory = MIN2(machine_compressed_memory, machine_used_memory);
  } else {
    machine_compressed_memory = 0;
  }

  return double(machine_compressed_memory) / double(machine_max_memory);
}

static double compute_target_gc_interval(const ZSystemMemoryPressureMetrics& mem_metrics,
                                         double gc_intensity,
                                         size_t process_used_memory) {
  // As memory pressure gets high, heap must be pressed down
  if (is_system_memory_pressure_critical(mem_metrics)) {
    return 0.0;
  }

  // High GC frequencies lead to extra overheads such as barrier storms and
  // frequent cache invalidation of the heap.
  // Therefore, we add a factor that ensures there is at least some social
  // distancing between GCs, even when the GC overhead is small. The size of
  // the factor scales with the level of load induced on the machine, as well
  // as the scaled and unscaled GC intensities.
  const double gc_intensity_factor = 5.0 / gc_intensity;

  // As the process starts dominating the available system memory, linearly
  // tone down the memory bloating.
  const double other_processes_memory = clamp(1.0 - mem_metrics.fraction_of_max(process_used_memory), 0.0, 1.0);
  const double process_progression = calculate_progression(other_processes_memory,
                                                           1.0,
                                                           mem_metrics._critical_threshold);

  const double gc_interval = gc_intensity_factor * (1.0 - process_progression);

  if (is_system_memory_pressure_concerning(mem_metrics)) {
    // As memory availability drops low in the system, we want to further prevent
    // memory bloating due to the interval target with another linear factor. If
    // the only memory culprit is the current process, then the progression will
    // now turn quadratic, which should help reduce the memory bloating further.

    const double system_availability = mem_metrics.available_fraction();
    const double system_progression = calculate_progression(system_availability,
                                                            mem_metrics._concerning_threshold,
                                                            mem_metrics._critical_threshold);

    return gc_interval * (1.0 - system_progression);
  }

  return gc_interval;
}

static double compute_target_gc_interval(const ZMemoryPressureMetrics& mem_metrics,
                                         double gc_intensity,
                                         size_t process_used_memory) {
  const double machine_target_gc_interval = compute_target_gc_interval(mem_metrics._machine,
                                                                       gc_intensity,
                                                                       process_used_memory);

  if (!mem_metrics._is_containerized) {
    return machine_target_gc_interval;
  }

  const double container_target_gc_interval = compute_target_gc_interval(mem_metrics._container,
                                                                         gc_intensity,
                                                                         process_used_memory);
  return MIN2(machine_target_gc_interval, container_target_gc_interval);
}

// Logistic function, produces values in the range 0 - 1 in an S shape
static double sigmoid_function(double value) {
  return 1.0 / (1.0 + pow(M_E, -value));
}

// This function smoothens out measured error signals to make the incremental heap
// sizing converge better. During an initial warmup period, a more aggressive function
// is used, which doesn't try to reduce the error signals. This reduces the number of
// early GCs before the system has had any chance to converge to a stable heap size.
static double smoothing_function(double value, double warmness) {
  const double sigmoid = sigmoid_function(value);
  const double aggressive = MIN2(MAX2(sigmoid, 0.5 + value), 2.0);

  return sigmoid * warmness + aggressive * (1.0 - warmness);
}

size_t ZAdaptiveHeap::compute_heap_size(const ZHeapResizeMetrics& heap_metrics, ZGenerationId generation) {
  precond(ZAdaptiveHeapSizing);

  const bool is_major = Thread::current() == ZDriver::major();
  const GCCause::Cause cause = is_major ? ZDriver::major()->gc_cause() : ZDriver::minor()->gc_cause();
  const bool is_heap_anti_pressure_gc = cause == GCCause::_z_proactive;
  const bool is_heap_pressure_gc = cause == GCCause::_z_allocation_rate ||
    cause == GCCause::_z_high_usage ||
    cause == GCCause::_z_warmup;

  if (!is_heap_anti_pressure_gc && !is_heap_pressure_gc) {
    // If this GC isn't heuristically triggered, don't resize or learn anything
    return heap_metrics._heuristic_max_capacity;
  }

  // System memory load
  const ZMemoryPressureMetrics mem_metrics = memory_pressure_metrics();
  ZStatSystemMemoryUsage::record(mem_metrics);

  // System CPU load
  const ZCpuPressureMetrics cpu_metrics = cpu_pressure_metrics(generation);
  if (generation == ZGenerationId::young) {
    _avg_mem_worker_cpu_overhead.store_relaxed(cpu_metrics._avg_mem_worker_cpu_overhead * ZAdaptiveHeap::young_to_old_gc_time());
  } else {
    _avg_mem_worker_cpu_overhead.store_relaxed(cpu_metrics._avg_mem_worker_cpu_overhead);
  }

  // Heap size metrics
  const size_t soft_max_capacity = heap_metrics._soft_max_capacity;
  const size_t current_max_capacity = heap_metrics._current_max_capacity;
  const size_t heuristic_max_capacity = heap_metrics._heuristic_max_capacity;
  const size_t capacity = heap_metrics._capacity;
  const size_t static_min_capacity = heap_metrics._static_min_capacity;
  const size_t used = heap_metrics._used;

  if (is_heap_anti_pressure_gc) {
    // The GC is bored. The impact of shrinking should not cost a considerable amount of
    // CPU, or we would not get here.
    const size_t selected_capacity = MAX2(size_t(double(heuristic_max_capacity) * 0.95), used);
    return clamp(align_down(selected_capacity, ZGranuleSize), static_min_capacity, current_max_capacity);
  }

  const ZStatCycleStats cycle_stats = ZGeneration::generation(generation)->stat_cycle()->stats();

  const double warmup_time_seconds = 3.0;
  const double warmness = MIN2(os::elapsedTime(), warmup_time_seconds) / warmup_time_seconds;
  const double warmness_squared = warmness * warmness;

  const size_t process_used_memory = os::rss();
  const size_t process_non_heap_memory = process_used_memory > capacity ? process_used_memory - capacity : 0;
  const size_t projected_process_used_memory = heuristic_max_capacity + process_non_heap_memory;

  // Calculate the GC pressure that scales the rest of the heuristics
  ZResourcePressure pressures = compute_pressures(mem_metrics, cpu_metrics, projected_process_used_memory);

  const double mem_pressure = pressures._mem_pressure;
  const double avg_time_since_last = cpu_metrics._avg_gc_interval;
  const double gc_intensity = smoothed_gc_intensity(pressures._scaled_gc_intensity);

  // Calculate the heuristic lower bound for the heuristic heap
  const double alloc_rate = heap_metrics._alloc_rate;
  // Since a GC cycle is obviously round, we can estimate the minimum bytes due to
  // a particular allocation rate and GC intensity by calculating GC intensity * pi.
  const double alloc_rate_window = warmness_squared / (gc_intensity * M_PI);
  const size_t alloc_rate_window_bytes = size_t(alloc_rate * alloc_rate_window);
  const size_t heuristic_low = align_down(size_t(double(MAX2(size_t(double(used) * 1.1), alloc_rate_window_bytes)) / mem_pressure), ZGranuleSize);

  const size_t upper_bound = MIN2(soft_max_capacity, current_max_capacity);
  const size_t lower_bound = clamp(heuristic_low, static_min_capacity, upper_bound);

  // We want strictly more GC activity on systems with proactive memory
  // compression (Windows and MacOS). Otherwise, memory bloating leads to
  // memory compression and decompression, which wastes more CPU than just
  // doing more GC instead.
  const double compression_gc_penalty = 1.0 MACOS_ONLY(* 2.0) WINDOWS_ONLY(* 2.0);

  // When GC intensity is 10, the implication is that we want ZConcurrentWorkersCPUShare
  // of the process CPU to be spent on doing GC when the process uses 100% of the
  // available CPU cores. The ConcGCThreads sizing by default goes up to a maximum
  // of ZConcurrentWorkersCPUShare of the available cores. So all ConcGCThreads
  // would be running back to back then.
  const double normalised_gc_intensity = gc_intensity / 10;
  const double target_cpu_overhead = ZConcurrentWorkersCPUShare * normalised_gc_intensity * compression_gc_penalty;

  // Save some breadcrumbs to the director to not use more conc GC threads
  // than we need to run back to back GC at the target GC CPU overhead limit.
  // It is better to let concurrent heap expansion run.
  const double avg_process_time = cpu_metrics._avg_process_time;
  const double avg_process_cpus = avg_process_time / avg_time_since_last;
  const double high_target_workers = avg_process_cpus * target_cpu_overhead;
  const uint initial_young_worker_cap = (uint)clamp(ceil(high_target_workers * 1.5), 1.0, (double)ZYoungGCThreads);
  _initial_young_worker_cap.store_relaxed(initial_young_worker_cap);

  const double upper_cpu_overhead = MAX2(cpu_metrics._avg_total_gc_cpu_overhead, cpu_metrics._generation_gc_cpu_overhead);
  const double upper_cpu_overhead_error = upper_cpu_overhead - target_cpu_overhead;

  const double lower_cpu_overhead = MIN2(cpu_metrics._avg_total_gc_cpu_overhead, cpu_metrics._generation_gc_cpu_overhead);
  const double lower_cpu_overhead_error = lower_cpu_overhead - target_cpu_overhead;

  const double target_gc_interval = compute_target_gc_interval(mem_metrics, gc_intensity, process_used_memory) / compression_gc_penalty;
  const double gc_interval_error = MAX2(target_gc_interval - avg_time_since_last, target_gc_interval - cpu_metrics._avg_gc_interval);

  const double upper_error_signal = MAX2(upper_cpu_overhead_error, gc_interval_error);
  const double lower_error_signal = MAX2(lower_cpu_overhead_error, gc_interval_error);

  const bool is_young = generation == ZGenerationId::young;

  if (is_young) {
    const double accumulated_young_gc_time = _accumulated_young_gc_time.load_relaxed();
    const double new_accumulated_young_gc_time = accumulated_young_gc_time + cpu_metrics._gc_time;
    // The only race is with the old collection end's exchange bellow. So if the
    // first CAS fails, the second must invariantly succeed.
    const bool result = _accumulated_young_gc_time.compare_set(accumulated_young_gc_time, new_accumulated_young_gc_time, memory_order_relaxed) ||
                        _accumulated_young_gc_time.compare_set(0.0, cpu_metrics._gc_time, memory_order_relaxed);
    assert(result, "Should have succeded, unexpected _accumulated_young_gc_time mutation, expected: %f or 0.0, current: %f",
           accumulated_young_gc_time, _accumulated_young_gc_time.load_relaxed());

    const double accumulated_mem_worker_time = _accumulated_mem_worker_time.load_relaxed();
    const double new_accumulated_mem_worker_time = accumulated_mem_worker_time + cpu_metrics._mem_worker_time;
    const bool result2 = _accumulated_mem_worker_time.compare_set(accumulated_mem_worker_time, new_accumulated_mem_worker_time, memory_order_relaxed) ||
                         _accumulated_mem_worker_time.compare_set(0.0, cpu_metrics._mem_worker_time, memory_order_relaxed);
    assert(result2, "Should have succeded, unexpected _accumulated_mem_worker_time mutation, expected: %f or 0.0, current: %f",
           accumulated_mem_worker_time, _accumulated_mem_worker_time.load_relaxed());
  } else {
    const double accumulated_young_gc_time = _accumulated_young_gc_time.exchange(0.0, memory_order_relaxed);
    const double young_to_old_gc_time = accumulated_young_gc_time / (accumulated_young_gc_time + cycle_stats._last_total_vtime);
    _young_to_old_gc_time.store_relaxed(young_to_old_gc_time);

    _accumulated_mem_worker_time.exchange(0.0, memory_order_relaxed);
  }

  const double upper_smoothened_error = smoothing_function(upper_error_signal, warmness);
  const double upper_correction_factor = upper_smoothened_error + 0.5;

  const double lower_smoothened_error = smoothing_function(lower_error_signal, warmness);
  const double lower_correction_factor = lower_smoothened_error + 0.5;

  const size_t upper_scaled_capacity = align_up(size_t(double(heuristic_max_capacity) * upper_correction_factor), ZGranuleSize);
  const size_t lower_scaled_capacity = align_up(size_t(double(heuristic_max_capacity) * lower_correction_factor), ZGranuleSize);

  // Instead of increasing the heap by an aggressive amount, reduce the memory
  // availability by said amount as we get closer to the current max capacity.
  // This ensures the heap increase slows down as we approach the max capacity,
  // similar to when a space ship is docking at a station.
  const size_t remaining_capacity = capacity >= current_max_capacity ? 0 : current_max_capacity - capacity;
  const size_t upper_scaled_max_capacity = upper_correction_factor <= 1.0
    ? upper_scaled_capacity
    : align_up(heuristic_max_capacity + size_t(double(remaining_capacity) * (upper_correction_factor - 1.0)), ZGranuleSize);
  const size_t lower_scaled_max_capacity = lower_correction_factor <= 1.0
    ? lower_scaled_capacity
    : align_up(heuristic_max_capacity + size_t(double(remaining_capacity) * (lower_correction_factor - 1.0)), ZGranuleSize);

  const size_t upper_suggested_capacity = MIN2(upper_scaled_capacity, upper_scaled_max_capacity);
  const size_t lower_suggested_capacity = MIN2(lower_scaled_capacity, lower_scaled_max_capacity);

  const size_t upper_bounded_capacity = clamp(upper_suggested_capacity, lower_bound, upper_bound);
  const size_t lower_bounded_capacity = clamp(lower_suggested_capacity, lower_bound, upper_bound);

  // Grow if we experience short term *and* long term pressure on the heap
  const bool should_grow = lower_bounded_capacity > heuristic_max_capacity && upper_bounded_capacity > heuristic_max_capacity;
  // Shrink if we experience short term *and* long term reverse pressure on the heap
  const bool should_shrink = lower_bounded_capacity < heuristic_max_capacity && upper_bounded_capacity < heuristic_max_capacity;

  const double cpu_pressure = pressures._cpu_pressure;
  const double cpu_vs_memory_pressure = pressures._cpu_vs_memory_pressure;
  const double cpu_vs_latency_pressure = pressures._cpu_vs_latency_pressure;

  if (mem_metrics._is_containerized) {
    log_info(gc, load)("Container: System Memory Load: %.1f%%, Process Memory Load: %.1f%%, Heap Memory Load: %.1f%%",
                       double(mem_metrics._container._used_memory) / double(mem_metrics._container._max_memory) * 100.0,
                       double(projected_process_used_memory) / double(mem_metrics._container._max_memory) * 100.0,
                       double(heuristic_max_capacity) / double(mem_metrics._container._max_memory) * 100.0);
  }

  if (cpu_metrics._has_container_cpu_metrics) {
    log_info(gc, load)("Container: System CPU Load: %.1f%%, Process CPU Load: %.1f%%, GC CPU Load: %.1f%%",
                       cpu_metrics._container._avg_system_load * 100.0, cpu_metrics._container._avg_process_load * 100.0,
                       cpu_metrics._avg_total_gc_cpu_overhead * cpu_metrics._container._avg_process_load * 100.0);
  }

  log_info(gc, load)("Machine: System Memory Load: %.1f%%, Process Memory Load: %.1f%%, Heap Memory Load: %.1f%%",
                     double(mem_metrics._machine._used_memory) / double(mem_metrics._machine._max_memory) * 100.0,
                     double(projected_process_used_memory) / double(mem_metrics._machine._max_memory) * 100.0,
                     double(heuristic_max_capacity) / double(mem_metrics._machine._max_memory) * 100.0);

  log_info(gc, load)("Machine: System CPU Load: %.1f%%, Process CPU Load: %.1f%%, GC CPU Load: %.1f%%",
                     cpu_metrics._machine._avg_system_load * 100.0, cpu_metrics._machine._avg_process_load * 100.0,
                     cpu_metrics._avg_total_gc_cpu_overhead * cpu_metrics._machine._avg_process_load * 100.0);

  if (ZAdaptiveHeapSizing) {
    log_info(gc, heap)("Process GC CPU Overhead: %.1f%%, Target Process GC CPU Overhead: %.1f%%",
                       cpu_metrics._avg_total_gc_cpu_overhead * 100.0, target_cpu_overhead * 100.0);
    log_debug(gc, heap)("Memory Worker CPU Overhead: %.1f%% of GC CPU Time",
                        cpu_metrics._avg_mem_worker_cpu_overhead);

    log_debug(gc, heap)("System CPU Pressure: %.1f, System Memory Pressure: %.1f",
                        cpu_pressure, mem_pressure);
    log_debug(gc, heap)("System CPU vs Memory Pressure: %.1f, System CPU vs Latency Pressure: %.1f",
                        cpu_vs_memory_pressure, cpu_vs_latency_pressure);
    log_info(gc, heap)("GC Intensity: %.1f, Resource Pressure Scaling: %.1f",
                       gc_intensity, gc_intensity / mem_metrics._unscaled_gc_intensity);

    log_debug(gc, heap)("GC Interval: %.3fs, Target Minimum: %.3fs",
                        avg_time_since_last, target_gc_interval);
    log_debug(gc, heap)("Target heap lower bound: %zuM, upper bound: %zuM",
                        lower_bound / M, upper_bound / M);
    log_debug(gc, heap)("Suggested capacity range: %zuM - %zuM, heuristic capacity: %zuM",
                        lower_suggested_capacity / M, upper_suggested_capacity / M, heuristic_max_capacity / M);
  }

  if (should_grow) {
    if (capacity < heuristic_max_capacity) {
      // We have not yet finished growing initiated in a previous GC, because the throughput of GCs
      // is higher than the throughput of growing. The control system needs to feel the impact of the
      // previous growth request before growing further. Therefore, we decide not to change the
      // heuristic max capacity in this case.
      return heuristic_max_capacity;
    }

    const size_t selected_capacity = MIN2(upper_bounded_capacity, lower_bounded_capacity);
    const size_t capacity_resize = selected_capacity - heuristic_max_capacity;

    log_debug(gc, heap)("Updated heuristic max capacity: %zuM (%.3f%%), current capacity: %zuM",
                        selected_capacity / M, double(selected_capacity) / double(heuristic_max_capacity) * 100.0 - 100.0, capacity / M);

    log_info(gc, heap)("Heap Increase %zuM (%.1f%%)", capacity_resize / M, double(capacity_resize) / double(heuristic_max_capacity) * 100.0);

    return selected_capacity;
  } else if (should_shrink) {
    // We want to shrink slower than we grow; by splitting the proposed shrinking into a fraction,
    // we get a slower tail of shrinking, which avoids unnecessary fluctuations up and down.
    const size_t shrinking_fraction = 5;

    const size_t proposed_selected_capacity = MAX2(upper_bounded_capacity, lower_bounded_capacity);
    const size_t capacity_resize = align_up((heuristic_max_capacity - proposed_selected_capacity) / shrinking_fraction, ZGranuleSize);

    const size_t selected_capacity = heuristic_max_capacity - capacity_resize;

    log_debug(gc, heap)("Updated heuristic max capacity: %zuM (%.3f%%), current capacity: %zuM",
                        selected_capacity / M, double(selected_capacity) / double(heuristic_max_capacity) * 100.0 - 100.0, capacity / M);

    log_info(gc, heap)("Heap Decrease %zuM (%.1f%%)", capacity_resize / M, double(capacity_resize) / double(heuristic_max_capacity) * 100.0);

    return selected_capacity;
  }

  return heuristic_max_capacity;
}

uint64_t ZAdaptiveHeap::urgent_uncommit_rate() {
  return 64; // MB per second
}

uint64_t ZAdaptiveHeap::no_uncommit_delay() {
  return std::numeric_limits<uint64_t>::max();
}

uint64_t ZAdaptiveHeap::critical_uncommit_delay() {
  return 0;
}

static uint64_t system_uncommit_delay(const ZSystemMemoryPressureMetrics& metrics, size_t capacity, size_t uncommit_granule, double avg_mem_worker_cpu_overhead) {
  const double capacity_scaling = clamp(double(capacity) / double(metrics._used_memory), 0.05, 1.0);
  const double worker_scaling = clamp(1.0 - avg_mem_worker_cpu_overhead, 0.05, 1.0);
  const double rate_scaling = capacity_scaling * worker_scaling;

  // The remaining memory reserve of the system
  const double available_fraction = metrics.available_fraction();

  // If we are critically low on memory, aggressively free up memory
  if (available_fraction <= metrics._critical_threshold) {
    return ZAdaptiveHeap::critical_uncommit_delay();
  }

  // If we aren't low on memory, disable timer based uncommit; let
  // the GC heuristics guide the heap down instead, as part of the
  // natural control system.
  if (available_fraction > metrics._concerning_threshold) {
    return ZAdaptiveHeap::no_uncommit_delay();
  }

  const double scaled_urgent_rate =  ZAdaptiveHeap::urgent_uncommit_rate() * rate_scaling;
  const uint64_t urgent_delay = uncommit_granule * MILLIUNITS / scaled_urgent_rate;

  // If we aren't using a high amount memory, uncommit memory rather slowly
  // and let the GC heuristics do most of the heavy lifting
  if (available_fraction > metrics._high_threshold) {
    // The memory pressure is concerning but not high; gradually siphon the
    // heap to potential other JVMs that may be under more pressure, allowing
    // them to grow.
    // Progression until critical uncommitting starts
    const double progression = calculate_progression(available_fraction, metrics._concerning_threshold, metrics._high_threshold);

    if (ZUncommitDelay == 0) {
      // With no ZUncommitDelay, simply use the urgent delay
      return urgent_delay;
    }

    // Scale the uncommit interval by memory urgency, so the pace of uncommitting
    // ramps up as the machine resources gets exhausted.
    const double highest_rate = double(ZAdaptiveHeap::urgent_uncommit_rate());
    const double lowest_rate = highest_rate / ZUncommitDelay;
    const double interpolated_rate = lowest_rate * (1.0 - progression) + highest_rate * progression;
    const double scaled_rate = interpolated_rate * rate_scaling;
    return uncommit_granule * MILLIUNITS / scaled_rate;
  }

  // We use a policy where the uncommit delay drops off fairly quickly
  // as the memory pressure gets "high" to let uncommitting react before
  // the next GC, but still without being brutal.
  // When the memory availability becomes critical, more brutal uncommitting
  // will commence.

  // Progression until critical uncommitting starts
  const double progression = calculate_progression(available_fraction, metrics._high_threshold, metrics._critical_threshold);

  // Scale the uncommit interval by memory urgency, so the pace of uncommitting
  // ramps up as the machine resources gets exhausted.
  return uint64_t(urgent_delay * (1.0 - progression));
}

// How long to wait until it is time to uncommit memory. This goes towards
// infinity when there is no concerning memory pressure, then from 64 MB per
// ZUncommitDelay 64 MB per second when high, and eventually 0 when critically low.
uint64_t ZAdaptiveHeap::uncommit_delay(size_t uncommit_granule) {
  precond(ZAdaptiveHeapSizing);

  const ZMemoryPressureMetrics mem_metrics = memory_pressure_metrics();
  ZStatSystemMemoryUsage::record(mem_metrics);

  const size_t capacity = ZHeap::heap()->capacity();
  const double avg_mem_worker_cpu_overhead = _avg_mem_worker_cpu_overhead.load_relaxed() / 100.0;

  const uint64_t machine_uncommit_delay =  system_uncommit_delay(mem_metrics._machine, capacity, uncommit_granule, avg_mem_worker_cpu_overhead);

  if (!mem_metrics._is_containerized) {
    return machine_uncommit_delay;
  }

  const uint64_t container_uncommit_delay = system_uncommit_delay(mem_metrics._container, capacity, uncommit_granule, avg_mem_worker_cpu_overhead);
  return MIN2(machine_uncommit_delay, container_uncommit_delay);
}

uint64_t ZAdaptiveHeap::soft_ref_delay() {
  ZStatHeap* const stats = ZGeneration::old()->stat_heap();
  // Young generation should have mostly transient state;
  // consider it as basically free.
  const size_t old_used_reloc_end = stats->used_generation_at_relocate_end();
  const size_t target_capacity = MAX2(ZHeap::heap()->heuristic_max_capacity(), old_used_reloc_end);
  const size_t free_heap = target_capacity - old_used_reloc_end;

  const uint64_t explicit_delay = free_heap / M * uint64_t(SoftRefLRUPolicyMSPerMB);

  if (!ZAdaptiveHeapSizing) {
    // Use the good old policy we all know and love so much when adaptive heap
    // sizing is not in use.
    return explicit_delay;
  }

  // With adaptive heap sizing, there is a risk for a feedback loop when the amount
  // of free memory decides how long soft references survive. More soft references
  // will lead to the heap growing, hence creating more free memory and suddenly
  // letting soft references live for longer. In order to cut this feedback loop,
  // a more involved policy is used.
  //
  // The more involved strategy scales the delay with the time it would take for the
  // heap to get filled up by old generation allocations multiplied by a scaled
  // variation of SoftRefLRUPolicyMSPerMB. The scaling is more aggressive than linear
  // by computing the nth root of SoftRefLRUPolicyMSPerMB, where n is some memory
  // pressure.

  // Scale the delay by the old generation allocation rate; the faster it fills up,
  // the more rapidly we need to prune soft references
  const double avg_time_since_last = _old_data._gc_times_since_last.avg();
  const size_t old_live = stats->live_at_mark_end();
  const size_t old_used = ZHeap::heap()->used_old();
  const double old_allocated = old_used - old_live;
  const double old_alloc_rate = MAX2(percent_of(old_allocated / M, avg_time_since_last) / 100.0, 1.0);

  const double time_to_old_oom = double(free_heap) / M / old_alloc_rate;

  const double free_ratio = double(target_capacity) / double(free_heap);

  const double mem_pressure = free_ratio;

  // Scale the SoftRefLRUPolicyMSPerMB as an nth root where n is the memory pressure.
  // The reason for using the nth root is that it might not necessarily be that
  // linearly decreasing the interval with memory pressure yields linearly more
  // soft references being cleared. It rather depends on the access frequency.
  // If they get accessed very frequently, then it's likely that no soft reference
  // get cleared at all, until the interval is made *very* small. Therefore, the
  // more aggressive nth root is used.
  const uint64_t scaled_interval = uint64_t(pow(SoftRefLRUPolicyMSPerMB, 1.0 / mem_pressure));

  // Compute the potentially more aggressive delay that cuts the feedback loop
  const uint64_t implicit_delay = uint64_t(time_to_old_oom * double(scaled_interval));

  // If the new policy yields earlier cut off points, then use that. Otherwise,
  // we still use the more relaxed policy to cut off soft references when they
  // have not been used for unreasonably long. While we could keep them around
  // forever, it might also be a bit pointless.
  const uint64_t delay = MIN2(implicit_delay, explicit_delay);

  log_info(gc, ref)("Soft ref timeout: %.3fs", double(delay) / MILLIUNITS);

  LogTarget(Debug, gc, ref) lt;
  if (lt.is_enabled()) {
    LogStream ls(lt);

    ls.print_cr("Soft ref time to old generation OOM: %.3fs", time_to_old_oom);
    ls.print_cr("Soft ref explicit timeout: %.3fs", double(explicit_delay) / MILLIUNITS);
    ls.print_cr("Soft ref implicit timeout: %.3fs", double(implicit_delay) / MILLIUNITS);
    ls.print_cr("Soft ref memory pressure: %.3f", mem_pressure);
  }

  return delay;
}

void ZAdaptiveHeap::print() {
  const char* status;
  if (!ZAdaptiveHeapSizing) {
    status = "Fixed";
  } else {
    if (explicit_max_capacity() || MinHeapSize != DefaultMinHeapSize) {
      status = "Adaptive (Explicit Boundaries)";
    } else {
      status = "Adaptive (Implicit Boundaries)";
    }
  }
  log_info_p(gc, init)("Heap Sizing: %s", status);
}

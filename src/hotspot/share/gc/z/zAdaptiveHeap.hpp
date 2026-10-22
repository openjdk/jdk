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

#ifndef SHARE_GC_Z_ZADAPTIVEHEAP_HPP
#define SHARE_GC_Z_ZADAPTIVEHEAP_HPP

#include "gc/z/zGenerationId.hpp"
#include "gc/z/zStat.hpp"
#include "memory/allocation.hpp"
#include "utilities/numberSeq.hpp"

struct ZHeapResizeMetrics {
  const size_t _soft_max_capacity;
  const size_t _current_max_capacity;
  const size_t _heuristic_max_capacity;
  const size_t _static_min_capacity;
  const size_t _capacity;
  const size_t _used;
  const double _alloc_rate;
};

struct ZSystemMemoryPressureMetrics {
  const physical_memory_size_type _used_memory;
  const physical_memory_size_type _max_memory;

  const double _concerning_threshold;
  const double _high_threshold;
  const double _critical_threshold;

  double fraction_of_max(physical_memory_size_type memory) const;
  physical_memory_size_type available_memory() const;
  double used_fraction() const;
  double available_fraction() const;
};

struct ZMemoryPressureMetrics {
  const double _unscaled_gc_intensity;
  const bool _is_containerized;
  const ZSystemMemoryPressureMetrics _machine;
  const ZSystemMemoryPressureMetrics _container;
};

struct ZSystemCpuPressureMetrics {
  const double _processor_count;
  const double _avg_process_load;
  const double _avg_system_load;
};

struct ZCpuPressureMetrics {
  const bool _has_container_cpu_metrics;
  const bool _has_container_cpu_capacity_limit;
  const double _generation_gc_cpu_overhead;
  const double _avg_generation_gc_cpu_overhead;
  const double _avg_total_gc_cpu_overhead;
  const double _avg_gc_interval;
  const double _avg_process_time;
  const double _gc_time;
  const double _mem_worker_time;
  const double _avg_mem_worker_cpu_overhead;
  const ZSystemCpuPressureMetrics _machine;
  const ZSystemCpuPressureMetrics _container;
};

struct ZResourcePressure {
  const double _scaled_gc_intensity;
  const double _cpu_pressure;
  const double _mem_pressure;
  const double _cpu_vs_memory_pressure;
  const double _cpu_vs_latency_pressure;
};

struct ZMachineMemoryInfo {
  physical_memory_size_type _physical_memory;
  physical_memory_size_type _available_memory;
  bool _is_valid;
};

struct ZSystemCpuTime {
  double _elapsed_time;
  double _processor_count;
};

class ZAdaptiveHeap : public AllStatic {
  friend class ZAdaptiveHeapTest;
private:
  // We clamp ZGCIntensity between thes values to avoid precision errors
  static constexpr double MinZGCIntensity = 1e-12;
  static constexpr double MaxZGCIntensity = 1e12;

  static bool _explicit_max_capacity;
  static bool _initialized;
  static bool _initialized_generation_data;

  struct ZIntensitySmoother {
  private:
    ZLock* _lock;
    TruncatedSeq _gc_intensities;

  public:
    ZIntensitySmoother()
      : _lock(nullptr),
        _gc_intensities() {}

    void initialize();
    double record_and_smooth_gc_intensity(double scaled_gc_intensity);
  };

  struct ZGenerationOverhead {
    double       _last_machine_system_time;
    double       _last_container_system_time;
    double       _machine_processor_count;
    double       _container_processor_count;
    double       _last_process_time;
    bool         _has_last_machine_system_time;
    bool         _has_last_container_system_time;
    bool         _has_last_process_time;
    double       _last_time;
    TruncatedSeq _process_times;
    TruncatedSeq _machine_system_times;
    TruncatedSeq _container_system_times;
    TruncatedSeq _gc_times;
    TruncatedSeq _gc_times_since_last;
    TruncatedSeq _mem_worker_times;

    ZGenerationOverhead()
      : _last_machine_system_time(),
        _last_container_system_time(),
        _machine_processor_count(),
        _container_processor_count(),
        _last_process_time(),
        _has_last_machine_system_time(),
        _has_last_container_system_time(),
        _has_last_process_time(),
        _process_times(),
        _machine_system_times(),
        _container_system_times(),
        _gc_times(),
        _gc_times_since_last(),
        _mem_worker_times() {}
  };

  static Atomic<double> _young_to_old_gc_time;
  static Atomic<double> _accumulated_young_gc_time;
  static Atomic<double> _accumulated_mem_worker_time;
  static Atomic<double> _avg_mem_worker_cpu_overhead;
  static ZGenerationOverhead _young_data;
  static ZGenerationOverhead _old_data;
  static Atomic<uint> _initial_young_worker_cap;
  static ZIntensitySmoother _gc_intensities;

  static void sample_generation_data(ZGenerationOverhead& generation_data);
  static ZCpuPressureMetrics cpu_pressure_metrics(ZGenerationId generation);

  static ZResourcePressure compute_pressures(const ZMemoryPressureMetrics& mem_metrics,
                                             const ZCpuPressureMetrics& cpu_metrics,
                                             size_t projected_process_used_memory);
  static double compute_memory_pressure(const ZMemoryPressureMetrics& metrics);
  static double smoothed_gc_intensity(double scaled_gc_intensity);

  [[nodiscard]] static bool pd_machine_elapsed_system_cpu_time(ZSystemCpuTime& value);
  [[nodiscard]] static bool pd_container_elapsed_system_cpu_time(ZSystemCpuTime& value);
  [[nodiscard]] static bool machine_elapsed_system_cpu_time(ZSystemCpuTime& value);
  [[nodiscard]] static bool container_elapsed_system_cpu_time(ZSystemCpuTime& value);

  static void pd_machine_memory_info(ZMachineMemoryInfo& info);
  static bool pd_machine_compressed_memory(physical_memory_size_type& value);
  static double machine_memory_compression_ratio(physical_memory_size_type machine_used_memory,
                                                 physical_memory_size_type machine_max_memory);

public:
  static void initialize(bool explicit_max_heap_size);
  static void initialize_generation_data();

  static ZMachineMemoryInfo machine_memory_info();
  static physical_memory_size_type machine_physical_memory();

  static size_t compute_heap_size(const ZHeapResizeMetrics& metrics, ZGenerationId generation);
  static double young_to_old_gc_time();
  static uint initial_young_worker_cap();

  // Uncommit support
  static uint64_t no_uncommit_delay();
  static uint64_t critical_uncommit_delay();
  static uint64_t urgent_uncommit_rate();
  static uint64_t uncommit_delay(size_t uncommit_granule);

  static uint64_t soft_ref_delay();

  // Memory pressure
  static ZMemoryPressureMetrics memory_pressure_metrics();
  static bool is_memory_pressure_concerning(const ZMemoryPressureMetrics& metrics);
  static bool is_memory_pressure_high(const ZMemoryPressureMetrics& metrics);
  static bool is_memory_pressure_critical(const ZMemoryPressureMetrics& metrics);

  // How adaptive are we?
  static bool explicit_max_capacity();

  static constexpr double DefaultGCIntensity = 5.0;
  static constexpr double DefaultMaxRAMPercentage = 100.;
  static constexpr size_t DefaultMinHeapSize = 2 * M;

  static void print();
};

#endif // SHARE_GC_Z_ZADAPTIVEHEAP_HPP

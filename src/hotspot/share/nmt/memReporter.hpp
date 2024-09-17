/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_NMT_MEMREPORTER_HPP
#define SHARE_NMT_MEMREPORTER_HPP

#include "memory/metaspace.hpp"
#include "nmt/mallocTracker.hpp"
#include "nmt/memBaseline.hpp"
#include "nmt/nativeCallStackPrinter.hpp"
#include "nmt/nmtCommon.hpp"
#include "nmt/virtualMemoryTracker.hpp"
#include "utilities/nativeCallStack.hpp"

/*
 * Base class that provides helpers
*/
class MemReporterBase : public StackObj {
 private:
  const size_t  _scale;         // report in this scale
  outputStream* const _output;  // destination
  StreamAutoIndentor _auto_indentor;

 public:

  // Default scale to use if no scale given.
  static const size_t default_scale = K;

  MemReporterBase(outputStream* out, size_t scale = default_scale);

  // Helper functions
  // Calculate total reserved and committed amount
  static size_t reserved_total(const MallocMemory* malloc, const VirtualMemory* vm);
  static size_t committed_total(const MallocMemory* malloc, const VirtualMemory* vm);

 protected:
  inline outputStream* output() const {
    return _output;
  }
  // Current reporting scale
  size_t scale() const {
    return _scale;
  }
  inline const char* current_scale() const {
    return NMTUtil::scale_name(_scale);
  }
  // Convert memory amount in bytes to current reporting scale
  inline size_t amount_in_current_scale(size_t amount) const {
    return NMTUtil::amount_in_scale(amount, _scale);
  }

  // Convert diff amount in bytes to current reporting scale
  // We use int64_t instead of ssize_t because on 32-bit it allows us to express deltas larger than 2 gb.
  // On 64-bit we never expect memory sizes larger than INT64_MAX.
  int64_t diff_in_current_scale(size_t s1, size_t s2) const {
    assert(_scale != 0, "wrong scale");

#ifdef _LP64
    assert(s1 < INT64_MAX, "exceeded possible memory limits");
    assert(s2 < INT64_MAX, "exceeded possible memory limits");
#endif

    bool is_negative = false;
    if (s1 < s2) {
      is_negative = true;
      swap(s1, s2);
    }

    size_t amount = s1 - s2;
    // We can split amount into p + q, where
    //     q = amount % _scale
    // and p = amount - q   (which is also (amount / _scale) * _scale).
    // Then use
    //   size_t scaled = (p + q + _scale/2) / _scale;
    // =>
    //   size_t scaled = (p / _scale) + ((q + _scale/2) / _scale);
    // The lefthand side of the addition is exact.
    // The righthand side is 0 if q <= (_scale - 1)/2, else 1. (The -1 is to account for odd _scale values.)
    size_t scaled = (amount / _scale);
    if ((amount % _scale) > (_scale - 1)/2) {
      scaled += 1;
    }

    int64_t result = static_cast<int64_t>(scaled);
    return is_negative ? -result : result;
  }

  // Print summary total, malloc and virtual memory
  void print_total(size_t reserved, size_t committed, size_t peak = 0) const;
  void print_malloc(const MemoryCounter* c, MEMFLAGS flag = mtNone) const;
  void print_virtual_memory(size_t reserved, size_t committed, size_t peak) const;
  void print_arena(const MemoryCounter* c) const;

  void print_virtual_memory_region(const char* type, address base, size_t size) const;
};

/*
 * The class is for generating summary tracking report.
 */
class MemSummaryReporter : public MemReporterBase {
 private:
  MallocMemorySnapshot*   _malloc_snapshot;
  VirtualMemorySnapshot*  _vm_snapshot;
  size_t                  _instance_class_count;
  size_t                  _array_class_count;

 public:
  // This constructor is for normal reporting from a recent baseline.
  MemSummaryReporter(MemBaseline& baseline, outputStream* output,
    size_t scale = default_scale) : MemReporterBase(output, scale),
    _malloc_snapshot(baseline.malloc_memory_snapshot()),
    _vm_snapshot(baseline.virtual_memory_snapshot()),
    _instance_class_count(baseline.instance_class_count()),
    _array_class_count(baseline.array_class_count()) { }


  // Generate summary report
  virtual void report();
 private:
  // Report summary for each memory type
  void report_summary_of_type(MEMFLAGS type, MallocMemory* malloc_memory,
    VirtualMemory* virtual_memory);

  void report_metadata(Metaspace::MetadataType type) const;
};

/*
 * The class is for generating detail tracking report.
 */
class MemDetailReporter : public MemSummaryReporter {
 private:
  MemBaseline&   _baseline;
  NativeCallStackPrinter _stackprinter;
 public:
  MemDetailReporter(MemBaseline& baseline, outputStream* output, size_t scale = default_scale) :
    MemSummaryReporter(baseline, output, scale),
     _baseline(baseline), _stackprinter(output) { }

  // Generate detail report.
  // The report contains summary and detail sections.
  virtual void report() {
    MemSummaryReporter::report();
    report_virtual_memory_map();
    report_memory_file_allocations();
    report_detail();
  }

 private:
  // Report detail tracking data.
  void report_detail();
  // Report virtual memory map
  void report_virtual_memory_map();
  // Report all physical devices
  void report_memory_file_allocations();
  // Report malloc allocation sites; returns number of omitted sites
  int report_malloc_sites();
  // Report virtual memory reservation sites; returns number of omitted sites
  int report_virtual_memory_allocation_sites();

  // Report a virtual memory region
  void report_virtual_memory_region(const ReservedMemoryRegion* rgn);
};

/*
 * The class is for generating summary comparison report.
 * It compares current memory baseline against an early baseline.
 */
class MemSummaryDiffReporter : public MemReporterBase {
 protected:
  MemBaseline&      _early_baseline;
  MemBaseline&      _current_baseline;

 public:
  MemSummaryDiffReporter(MemBaseline& early_baseline, MemBaseline& current_baseline,
    outputStream* output, size_t scale = default_scale) : MemReporterBase(output, scale),
    _early_baseline(early_baseline), _current_baseline(current_baseline) {
    assert(early_baseline.baseline_type()   != MemBaseline::Not_baselined, "Not baselined");
    assert(current_baseline.baseline_type() != MemBaseline::Not_baselined, "Not baselined");
  }

  // Generate summary comparison report
  virtual void report_diff();

 private:
  // report the comparison of each memory type
  void diff_summary_of_type(MEMFLAGS type,
    const MallocMemory* early_malloc, const VirtualMemory* early_vm,
    const MetaspaceCombinedStats& early_ms,
    const MallocMemory* current_malloc, const VirtualMemory* current_vm,
    const MetaspaceCombinedStats& current_ms) const;

 protected:
  void print_malloc_diff(size_t current_amount, size_t current_count,
    size_t early_amount, size_t early_count, MEMFLAGS flags) const;
  void print_virtual_memory_diff(size_t current_reserved, size_t current_committed,
    size_t early_reserved, size_t early_committed) const;
  void print_arena_diff(size_t current_amount, size_t current_count,
    size_t early_amount, size_t early_count) const;

  void print_metaspace_diff(const MetaspaceCombinedStats& current_ms,
                            const MetaspaceCombinedStats& early_ms) const;
  void print_metaspace_diff(const char* header,
                            const MetaspaceStats& current_ms,
                            const MetaspaceStats& early_ms) const;
};

/*
 * The class is for generating detail comparison report.
 * It compares current memory baseline against an early baseline,
 * both baselines have to be detail baseline.
 */
class MemDetailDiffReporter : public MemSummaryDiffReporter {
  NativeCallStackPrinter _stackprinter;
 public:
  MemDetailDiffReporter(MemBaseline& early_baseline, MemBaseline& current_baseline,
    outputStream* output, size_t scale = default_scale) :
    MemSummaryDiffReporter(early_baseline, current_baseline, output, scale),
    _stackprinter(output) { }

  // Generate detail comparison report
  virtual void report_diff();

  // Malloc allocation site comparison
  void diff_malloc_sites() const;
  // Virtual memory reservation site comparison
  void diff_virtual_memory_sites() const;

  // New malloc allocation site in recent baseline
  void new_malloc_site (const MallocSite* site) const;
  // The malloc allocation site is not in recent baseline
  void old_malloc_site (const MallocSite* site) const;
  // Compare malloc allocation site, it is in both baselines
  void diff_malloc_site(const MallocSite* early, const MallocSite* current)  const;

  // New virtual memory allocation site in recent baseline
  void new_virtual_memory_site (const VirtualMemoryAllocationSite* callsite) const;
  // The virtual memory allocation site is not in recent baseline
  void old_virtual_memory_site (const VirtualMemoryAllocationSite* callsite) const;
  // Compare virtual memory allocation site, it is in both baseline
  void diff_virtual_memory_site(const VirtualMemoryAllocationSite* early,
                                const VirtualMemoryAllocationSite* current)  const;

  void diff_malloc_site(const NativeCallStack* stack, size_t current_size,
    size_t currrent_count, size_t early_size, size_t early_count, MEMFLAGS flags) const;
  void diff_virtual_memory_site(const NativeCallStack* stack, size_t current_reserved,
    size_t current_committed, size_t early_reserved, size_t early_committed, MEMFLAGS flag) const;
};

#endif // SHARE_NMT_MEMREPORTER_HPP

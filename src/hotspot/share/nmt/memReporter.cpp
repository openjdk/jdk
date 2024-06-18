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
#include "precompiled.hpp"
#include "cds/filemap.hpp"
#include "memory/metaspace.hpp"
#include "memory/metaspaceUtils.hpp"
#include "nmt/mallocTracker.hpp"
#include "nmt/memflags.hpp"
#include "nmt/memReporter.hpp"
#include "nmt/memoryFileTracker.hpp"
#include "nmt/threadStackTracker.hpp"
#include "nmt/virtualMemoryTracker.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"

#define INDENT_BY(num_chars, CODE) { \
  streamIndentor si(out, num_chars); \
  { CODE }                           \
}

// Diff two counters, express them as signed, with range checks
static ssize_t counter_diff(size_t c1, size_t c2) {
  assert(c1 <= SSIZE_MAX, "counter out of range: " SIZE_FORMAT ".", c1);
  assert(c2 <= SSIZE_MAX, "counter out of range: " SIZE_FORMAT ".", c2);
  if (c1 > SSIZE_MAX || c2 > SSIZE_MAX) {
    return 0;
  }
  return c1 - c2;
}

MemReporterBase::MemReporterBase(outputStream* out, size_t scale) :
  _scale(scale), _output(out), _auto_indentor(out) {}

size_t MemReporterBase::reserved_total(const MallocMemory* malloc, const VirtualMemory* vm) {
  return malloc->malloc_size() + malloc->arena_size() + vm->reserved();
}

size_t MemReporterBase::committed_total(const MallocMemory* malloc, const VirtualMemory* vm) {
  return malloc->malloc_size() + malloc->arena_size() + vm->committed();
}

void MemReporterBase::print_total(size_t reserved, size_t committed, size_t peak) const {
  const char* scale = current_scale();
  output()->print("reserved=" SIZE_FORMAT "%s, committed=" SIZE_FORMAT "%s",
    amount_in_current_scale(reserved), scale, amount_in_current_scale(committed), scale);
  if (peak != 0) {
    output()->print(", peak=" SIZE_FORMAT "%s", amount_in_current_scale(peak), scale);
  }
}

void MemReporterBase::print_malloc(const MemoryCounter* c, MEMFLAGS flag) const {
  const char* scale = current_scale();
  outputStream* out = output();
  const char* alloc_type = (flag == mtThreadStack) ? "" : "malloc=";

  const size_t amount = c->size();
  const size_t count = c->count();

  if (flag != mtNone) {
    out->print("(%s" SIZE_FORMAT "%s type=%s", alloc_type,
      amount_in_current_scale(amount), scale, NMTUtil::flag_to_name(flag));
  } else {
    out->print("(%s" SIZE_FORMAT "%s", alloc_type,
      amount_in_current_scale(amount), scale);
  }

  // blends out mtChunk count number
  if (count > 0) {
    out->print(" #" SIZE_FORMAT "", count);
  }

  out->print(")");

  size_t pk_amount = c->peak_size();
  if (pk_amount == amount) {
    out->print_raw(" (at peak)");
  } else if (pk_amount > amount) {
    size_t pk_count = c->peak_count();
    out->print(" (peak=" SIZE_FORMAT "%s #" SIZE_FORMAT ")",
        amount_in_current_scale(pk_amount), scale, pk_count);
  }
}

void MemReporterBase::print_virtual_memory(size_t reserved, size_t committed, size_t peak) const {
  outputStream* out = output();
  const char* scale = current_scale();
  out->print("(mmap: reserved=" SIZE_FORMAT "%s, committed=" SIZE_FORMAT "%s, ",
    amount_in_current_scale(reserved), scale, amount_in_current_scale(committed), scale);
  if (peak == committed) {
    out->print_raw("at peak)");
  } else {
    out->print("peak=" SIZE_FORMAT "%s)", amount_in_current_scale(peak), scale);
  }
}

void MemReporterBase::print_arena(const MemoryCounter* c) const {
  const char* scale = current_scale();
  outputStream* out = output();

  const size_t amount = c->size();
  const size_t count = c->count();

  out->print("(arena=" SIZE_FORMAT "%s #" SIZE_FORMAT ")",
             amount_in_current_scale(amount), scale, count);

  size_t pk_amount = c->peak_size();
  if (pk_amount == amount) {
    out->print_raw(" (at peak)");
  } else if (pk_amount > amount) {
    size_t pk_count = c->peak_count();
    out->print(" (peak=" SIZE_FORMAT "%s #" SIZE_FORMAT ")",
        amount_in_current_scale(pk_amount), scale, pk_count);
  }
}

void MemReporterBase::print_virtual_memory_region(const char* type, address base, size_t size) const {
  const char* scale = current_scale();
  output()->print("[" PTR_FORMAT " - " PTR_FORMAT "] %s " SIZE_FORMAT "%s",
    p2i(base), p2i(base + size), type, amount_in_current_scale(size), scale);
}


void MemSummaryReporter::report() {
  outputStream* out = output();
  const size_t total_malloced_bytes = _malloc_snapshot->total();
  const size_t total_mmap_reserved_bytes = _vm_snapshot->total_reserved();
  const size_t total_mmap_committed_bytes = _vm_snapshot->total_committed();

  size_t total_reserved_amount = total_malloced_bytes + total_mmap_reserved_bytes;
  size_t total_committed_amount = total_malloced_bytes + total_mmap_committed_bytes;

  // Overall total
  out->cr();
  out->print_cr("Native Memory Tracking:");
  out->cr();

  if (scale() > 1) {
    out->print_cr("(Omitting categories weighting less than 1%s)", current_scale());
    out->cr();
  }

  out->print("Total: ");
  print_total(total_reserved_amount, total_committed_amount);
  out->cr();
  INDENT_BY(7,
    out->print_cr("malloc: " SIZE_FORMAT "%s #" SIZE_FORMAT ", peak=" SIZE_FORMAT "%s #" SIZE_FORMAT,
                  amount_in_current_scale(total_malloced_bytes), current_scale(),
                  _malloc_snapshot->total_count(),
                  amount_in_current_scale(_malloc_snapshot->total_peak()),
                  current_scale(), _malloc_snapshot->total_peak_count());
    out->print("mmap:   ");
    print_total(total_mmap_reserved_bytes, total_mmap_committed_bytes);
  )
  out->cr();
  out->cr();

  // Summary by memory type
  for (int index = 0; index < mt_number_of_types; index ++) {
    MEMFLAGS flag = NMTUtil::index_to_flag(index);
    // thread stack is reported as part of thread category
    if (flag == mtThreadStack) continue;
    MallocMemory* malloc_memory = _malloc_snapshot->by_type(flag);
    VirtualMemory* virtual_memory = _vm_snapshot->by_type(flag);

    report_summary_of_type(flag, malloc_memory, virtual_memory);
  }
}

void MemSummaryReporter::report_summary_of_type(MEMFLAGS flag,
  MallocMemory*  malloc_memory, VirtualMemory* virtual_memory) {

  size_t reserved_amount  = reserved_total (malloc_memory, virtual_memory);
  size_t committed_amount = committed_total(malloc_memory, virtual_memory);

  // Count thread's native stack in "Thread" category
  if (flag == mtThread) {
    const VirtualMemory* thread_stack_usage =
      (const VirtualMemory*)_vm_snapshot->by_type(mtThreadStack);
    reserved_amount  += thread_stack_usage->reserved();
    committed_amount += thread_stack_usage->committed();
  } else if (flag == mtNMT) {
    // Count malloc headers in "NMT" category
    reserved_amount  += _malloc_snapshot->malloc_overhead();
    committed_amount += _malloc_snapshot->malloc_overhead();
  }

  // Omit printing if the current reserved value as well as all historical peaks (malloc, mmap committed, arena)
  // fall below scale threshold
  const size_t pk_vm = virtual_memory->peak_size();
  const size_t pk_malloc = malloc_memory->malloc_peak_size();
  const size_t pk_arena = malloc_memory->arena_peak_size();

  if (amount_in_current_scale(MAX4(reserved_amount, pk_vm, pk_malloc, pk_arena)) == 0) {
    return;
  }

  outputStream* out   = output();
  const char*   scale = current_scale();
  constexpr int indent = 28;
  out->print("-%*s (", indent - 2, NMTUtil::flag_to_name(flag));
  print_total(reserved_amount, committed_amount);
#if INCLUDE_CDS
  if (flag == mtClassShared) {
      size_t read_only_bytes = FileMapInfo::readonly_total();
    output()->print(", readonly=" SIZE_FORMAT "%s",
                    amount_in_current_scale(read_only_bytes), scale);
  }
#endif
  out->print_cr(")");

  streamIndentor si(out, indent);

  if (flag == mtClass) {
    // report class count
    out->print_cr("(classes #" SIZE_FORMAT ")", (_instance_class_count + _array_class_count));
    out->print_cr("(  instance classes #" SIZE_FORMAT ", array classes #" SIZE_FORMAT ")",
                  _instance_class_count, _array_class_count);
  } else if (flag == mtThread) {
    const VirtualMemory* thread_stack_usage =
     _vm_snapshot->by_type(mtThreadStack);
    // report thread count
    out->print_cr("(threads #" SIZE_FORMAT ")", ThreadStackTracker::thread_count());
    out->print("(stack: ");
    print_total(thread_stack_usage->reserved(), thread_stack_usage->committed(), thread_stack_usage->peak_size());
    out->print_cr(")");
  }

   // report malloc'd memory
  if (amount_in_current_scale(MAX2(malloc_memory->malloc_size(), pk_malloc)) > 0) {
    print_malloc(malloc_memory->malloc_counter());
    out->cr();
  }

  if (amount_in_current_scale(MAX2(virtual_memory->reserved(), pk_vm)) > 0) {
    print_virtual_memory(virtual_memory->reserved(), virtual_memory->committed(), virtual_memory->peak_size());
    out->cr();
  }

  if (amount_in_current_scale(MAX2(malloc_memory->arena_size(), pk_arena)) > 0) {
    print_arena(malloc_memory->arena_counter());
    out->cr();
  }

  if (flag == mtNMT &&
    amount_in_current_scale(_malloc_snapshot->malloc_overhead()) > 0) {
    out->print_cr("(tracking overhead=" SIZE_FORMAT "%s)",
                   amount_in_current_scale(_malloc_snapshot->malloc_overhead()), scale);
  } else if (flag == mtClass) {
    // Metadata information
    report_metadata(Metaspace::NonClassType);
    if (Metaspace::using_class_space()) {
      report_metadata(Metaspace::ClassType);
    }
  }
  out->cr();
}

void MemSummaryReporter::report_metadata(Metaspace::MetadataType type) const {

  // NMT reports may be triggered (as part of error handling) very early. Make sure
  // Metaspace is already initialized.
  if (!Metaspace::initialized()) {
    return;
  }

  assert(type == Metaspace::NonClassType || type == Metaspace::ClassType,
    "Invalid metadata type");
  const char* name = (type == Metaspace::NonClassType) ?
    "Metadata:   " : "Class space:";

  outputStream* out = output();
  const char* scale = current_scale();
  const MetaspaceStats stats = MetaspaceUtils::get_statistics(type);

  size_t waste = stats.committed() - stats.used();
  float waste_percentage = stats.committed() > 0 ? (((float)waste * 100)/(float)stats.committed()) : 0.0f;

  out->print_cr("(  %s)", name);
  out->print("(    ");
  print_total(stats.reserved(), stats.committed());
  out->print_cr(")");
  out->print_cr("(    used=" SIZE_FORMAT "%s)", amount_in_current_scale(stats.used()), scale);
  out->print_cr("(    waste=" SIZE_FORMAT "%s =%2.2f%%)", amount_in_current_scale(waste),
                scale, waste_percentage);
}

void MemDetailReporter::report_detail() {
  // Start detail report
  outputStream* out = output();
  out->print_cr("Details:\n");

  int num_omitted =
      report_malloc_sites() +
      report_virtual_memory_allocation_sites();
  if (num_omitted > 0) {
    assert(scale() > 1, "sanity");
    out->print_cr("(%d call sites weighting less than 1%s each omitted.)",
                   num_omitted, current_scale());
    out->cr();
  }
}

int MemDetailReporter::report_malloc_sites() {
  MallocSiteIterator         malloc_itr = _baseline.malloc_sites(MemBaseline::by_size);
  if (malloc_itr.is_empty()) return 0;

  outputStream* out = output();

  const MallocSite* malloc_site;
  int num_omitted = 0;
  while ((malloc_site = malloc_itr.next()) != nullptr) {
    // Omit printing if the current value and the historic peak value both fall below the reporting scale threshold
    if (amount_in_current_scale(MAX2(malloc_site->size(), malloc_site->peak_size())) == 0) {
      num_omitted ++;
      continue;
    }
    const NativeCallStack* stack = malloc_site->call_stack();
    stack->print_on(out);
    MEMFLAGS flag = malloc_site->flag();
    assert(NMTUtil::flag_is_valid(flag) && flag != mtNone,
      "Must have a valid memory type");
    INDENT_BY(29,
      out->print("(");
      print_malloc(malloc_site->counter(), flag);
      out->print_cr(")");
    )
    out->cr();
  }
  return num_omitted;
}

int MemDetailReporter::report_virtual_memory_allocation_sites()  {
  VirtualMemorySiteIterator  virtual_memory_itr =
    _baseline.virtual_memory_sites(MemBaseline::by_size);

  if (virtual_memory_itr.is_empty()) return 0;

  outputStream* out = output();

  const VirtualMemoryAllocationSite*  virtual_memory_site;
  int num_omitted = 0;
  while ((virtual_memory_site = virtual_memory_itr.next()) != nullptr) {
    // Don't report free sites; does not count toward omitted count.
    if (virtual_memory_site->reserved() == 0) {
      continue;
    }
    // Omit printing if the current value and the historic peak value both fall below the
    // reporting scale threshold
    if (amount_in_current_scale(MAX2(virtual_memory_site->reserved(),
                                     virtual_memory_site->peak_size())) == 0) {
      num_omitted++;
      continue;
    }
    const NativeCallStack* stack = virtual_memory_site->call_stack();
    stack->print_on(out);
    INDENT_BY(29,
      out->print("(");
      print_total(virtual_memory_site->reserved(), virtual_memory_site->committed());
      const MEMFLAGS flag = virtual_memory_site->flag();
      if (flag != mtNone) {
        out->print(" Type=%s", NMTUtil::flag_to_name(flag));
      }
      out->print_cr(")");
    )
    out->cr();
  }
  return num_omitted;
}


void MemDetailReporter::report_virtual_memory_map() {
  // Virtual memory map always in base address order
  VirtualMemoryAllocationIterator itr = _baseline.virtual_memory_allocations();
  const ReservedMemoryRegion* rgn;

  output()->print_cr("Virtual memory map:");
  while ((rgn = itr.next()) != nullptr) {
    report_virtual_memory_region(rgn);
  }
}

void MemDetailReporter::report_virtual_memory_region(const ReservedMemoryRegion* reserved_rgn) {
  assert(reserved_rgn != nullptr, "null pointer");

  // We don't bother about reporting peaks here.
  // That is because peaks - in the context of virtual memory, peak of committed areas - make little sense
  // when we report *by region*, which are identified by their location in memory. There is a philosophical
  // question about identity here: e.g. a committed region that has been split into three regions by
  // uncommitting a middle section of it, should that still count as "having peaked" before the split? If
  // yes, which of the three new regions would be the spiritual successor? Rather than introducing more
  // complexity, we avoid printing peaks altogether. Note that peaks should still be printed when reporting
  // usage *by callsite*.

  // Don't report if size is too small.
  if (amount_in_current_scale(reserved_rgn->size()) == 0) return;

  outputStream* out = output();
  const char* scale = current_scale();
  const NativeCallStack*  stack = reserved_rgn->call_stack();
  bool all_committed = reserved_rgn->size() == reserved_rgn->committed_size();
  const char* region_type = (all_committed ? "reserved and committed" : "reserved");
  out->cr();
  print_virtual_memory_region(region_type, reserved_rgn->base(), reserved_rgn->size());
  out->print(" for %s", NMTUtil::flag_to_name(reserved_rgn->flag()));
  if (stack->is_empty()) {
    out->cr();
  } else {
    out->print_cr(" from");
    INDENT_BY(4, stack->print_on(out);)
  }

  if (all_committed) {
    CommittedRegionIterator itr = reserved_rgn->iterate_committed_regions();
    const CommittedMemoryRegion* committed_rgn = itr.next();
    if (committed_rgn->size() == reserved_rgn->size() && committed_rgn->call_stack()->equals(*stack)) {
      // One region spanning the entire reserved region, with the same stack trace.
      // Don't print this regions because the "reserved and committed" line above
      // already indicates that the region is committed.
      assert(itr.next() == nullptr, "Unexpectedly more than one regions");
      return;
    }
  }

  CommittedRegionIterator itr = reserved_rgn->iterate_committed_regions();
  const CommittedMemoryRegion* committed_rgn;
  while ((committed_rgn = itr.next()) != nullptr) {
    // Don't report if size is too small
    if (amount_in_current_scale(committed_rgn->size()) == 0) continue;
    stack = committed_rgn->call_stack();
    out->cr();
    INDENT_BY(8,
      print_virtual_memory_region("committed", committed_rgn->base(), committed_rgn->size());
      if (stack->is_empty()) {
        out->cr();
      } else {
        out->print_cr(" from");
        INDENT_BY(4, stack->print_on(out);)
      }
    )
  }
}

void MemDetailReporter::report_memory_file_allocations() {
  stringStream st;
  {
    MemoryFileTracker::Instance::Locker lock;
    MemoryFileTracker::Instance::print_all_reports_on(&st, scale());
  }
  output()->print_raw(st.freeze());
}

void MemSummaryDiffReporter::report_diff() {
  outputStream* out = output();
  out->cr();
  out->print_cr("Native Memory Tracking:");
  out->cr();

  if (scale() > 1) {
    out->print_cr("(Omitting categories weighting less than 1%s)", current_scale());
    out->cr();
  }

  // Overall diff
  out->print("Total: ");
  print_virtual_memory_diff(_current_baseline.total_reserved_memory(),
    _current_baseline.total_committed_memory(), _early_baseline.total_reserved_memory(),
    _early_baseline.total_committed_memory());

  out->cr();
  out->cr();

  // Summary diff by memory type
  for (int index = 0; index < mt_number_of_types; index ++) {
    MEMFLAGS flag = NMTUtil::index_to_flag(index);
    // thread stack is reported as part of thread category
    if (flag == mtThreadStack) continue;
    diff_summary_of_type(flag,
      _early_baseline.malloc_memory(flag),
      _early_baseline.virtual_memory(flag),
      _early_baseline.metaspace_stats(),
      _current_baseline.malloc_memory(flag),
      _current_baseline.virtual_memory(flag),
      _current_baseline.metaspace_stats());
  }
}

void MemSummaryDiffReporter::print_malloc_diff(size_t current_amount, size_t current_count,
    size_t early_amount, size_t early_count, MEMFLAGS flags) const {
  const char* scale = current_scale();
  outputStream* out = output();
  const char* alloc_type = (flags == mtThread) ? "" : "malloc=";

  out->print("%s" SIZE_FORMAT "%s", alloc_type, amount_in_current_scale(current_amount), scale);
  // Report type only if it is valid and not under "thread" category
  if (flags != mtNone && flags != mtThread) {
    out->print(" type=%s", NMTUtil::flag_to_name(flags));
  }

  int64_t amount_diff = diff_in_current_scale(current_amount, early_amount);
  if (amount_diff != 0) {
    out->print(" " INT64_PLUS_FORMAT "%s", amount_diff, scale);
  }
  if (current_count > 0) {
    out->print(" #" SIZE_FORMAT "", current_count);
    const ssize_t delta_count = counter_diff(current_count, early_count);
    if (delta_count != 0) {
      out->print(" " SSIZE_PLUS_FORMAT, delta_count);
    }
  }
}

void MemSummaryDiffReporter::print_arena_diff(size_t current_amount, size_t current_count,
  size_t early_amount, size_t early_count) const {
  const char* scale = current_scale();
  outputStream* out = output();
  out->print("arena=" SIZE_FORMAT "%s", amount_in_current_scale(current_amount), scale);
  int64_t amount_diff = diff_in_current_scale(current_amount, early_amount);
  if (amount_diff != 0) {
    out->print(" " INT64_PLUS_FORMAT "%s", amount_diff, scale);
  }

  out->print(" #" SIZE_FORMAT "", current_count);
  const ssize_t delta_count = counter_diff(current_count, early_count);
  if (delta_count != 0) {
    out->print(" " SSIZE_PLUS_FORMAT, delta_count);
  }
}

void MemSummaryDiffReporter::print_virtual_memory_diff(size_t current_reserved, size_t current_committed,
    size_t early_reserved, size_t early_committed) const {
  const char* scale = current_scale();
  outputStream* out = output();
  out->print("reserved=" SIZE_FORMAT "%s", amount_in_current_scale(current_reserved), scale);
  int64_t reserved_diff = diff_in_current_scale(current_reserved, early_reserved);
  if (reserved_diff != 0) {
    out->print(" " INT64_PLUS_FORMAT "%s", reserved_diff, scale);
  }

  out->print(", committed=" SIZE_FORMAT "%s", amount_in_current_scale(current_committed), scale);
  int64_t committed_diff = diff_in_current_scale(current_committed, early_committed);
  if (committed_diff != 0) {
    out->print(" " INT64_PLUS_FORMAT "%s", committed_diff, scale);
  }
}


void MemSummaryDiffReporter::diff_summary_of_type(MEMFLAGS flag,
  const MallocMemory* early_malloc, const VirtualMemory* early_vm,
  const MetaspaceCombinedStats& early_ms,
  const MallocMemory* current_malloc, const VirtualMemory* current_vm,
  const MetaspaceCombinedStats& current_ms) const {

  outputStream* out = output();
  const char* scale = current_scale();
  constexpr int indent = 28;

  // Total reserved and committed memory in current baseline
  size_t current_reserved_amount  = reserved_total (current_malloc, current_vm);
  size_t current_committed_amount = committed_total(current_malloc, current_vm);

  // Total reserved and committed memory in early baseline
  size_t early_reserved_amount  = reserved_total(early_malloc, early_vm);
  size_t early_committed_amount = committed_total(early_malloc, early_vm);

  // Adjust virtual memory total
  if (flag == mtThread) {
    const VirtualMemory* early_thread_stack_usage =
      _early_baseline.virtual_memory(mtThreadStack);
    const VirtualMemory* current_thread_stack_usage =
      _current_baseline.virtual_memory(mtThreadStack);

    early_reserved_amount  += early_thread_stack_usage->reserved();
    early_committed_amount += early_thread_stack_usage->committed();

    current_reserved_amount  += current_thread_stack_usage->reserved();
    current_committed_amount += current_thread_stack_usage->committed();
  } else if (flag == mtNMT) {
    early_reserved_amount  += _early_baseline.malloc_tracking_overhead();
    early_committed_amount += _early_baseline.malloc_tracking_overhead();

    current_reserved_amount  += _current_baseline.malloc_tracking_overhead();
    current_committed_amount += _current_baseline.malloc_tracking_overhead();
  }

  if (amount_in_current_scale(current_reserved_amount) > 0 ||
      diff_in_current_scale(current_reserved_amount, early_reserved_amount) != 0) {

    // print summary line
    out->print("-%*s (", indent - 2, NMTUtil::flag_to_name(flag));
    print_virtual_memory_diff(current_reserved_amount, current_committed_amount,
      early_reserved_amount, early_committed_amount);
    out->print_cr(")");

    streamIndentor si(out, indent);

    // detail lines
    if (flag == mtClass) {
      // report class count
      out->print("(classes #" SIZE_FORMAT, _current_baseline.class_count());
      const ssize_t class_count_diff =
          counter_diff(_current_baseline.class_count(), _early_baseline.class_count());
      if (class_count_diff != 0) {
        out->print(" " SSIZE_PLUS_FORMAT, class_count_diff);
      }
      out->print_cr(")");

      out->print("(  instance classes #" SIZE_FORMAT, _current_baseline.instance_class_count());
      const ssize_t instance_class_count_diff =
          counter_diff(_current_baseline.instance_class_count(), _early_baseline.instance_class_count());
      if (instance_class_count_diff != 0) {
        out->print(" " SSIZE_PLUS_FORMAT, instance_class_count_diff);
      }
      out->print(", array classes #" SIZE_FORMAT, _current_baseline.array_class_count());
      const ssize_t array_class_count_diff =
          counter_diff(_current_baseline.array_class_count(), _early_baseline.array_class_count());
      if (array_class_count_diff != 0) {
        out->print(" " SSIZE_PLUS_FORMAT, array_class_count_diff);
      }
      out->print_cr(")");

    } else if (flag == mtThread) {
      // report thread count
      out->print("(threads #" SIZE_FORMAT, _current_baseline.thread_count());
      const ssize_t thread_count_diff = counter_diff(_current_baseline.thread_count(), _early_baseline.thread_count());
      if (thread_count_diff != 0) {
        out->print(" " SSIZE_PLUS_FORMAT, thread_count_diff);
      }
      out->print_cr(")");

      out->print("(stack: ");
      // report thread stack
      const VirtualMemory* current_thread_stack =
        _current_baseline.virtual_memory(mtThreadStack);
      const VirtualMemory* early_thread_stack =
        _early_baseline.virtual_memory(mtThreadStack);

      print_virtual_memory_diff(current_thread_stack->reserved(), current_thread_stack->committed(),
        early_thread_stack->reserved(), early_thread_stack->committed());

      out->print_cr(")");
    }

    // Report malloc'd memory
    size_t current_malloc_amount = current_malloc->malloc_size();
    size_t early_malloc_amount   = early_malloc->malloc_size();
    if (amount_in_current_scale(current_malloc_amount) > 0 ||
        diff_in_current_scale(current_malloc_amount, early_malloc_amount) != 0) {
      out->print("(");
      print_malloc_diff(current_malloc_amount, (flag == mtChunk) ? 0 : current_malloc->malloc_count(),
        early_malloc_amount, early_malloc->malloc_count(), mtNone);
      out->print_cr(")");
    }

    // Report virtual memory
    if (amount_in_current_scale(current_vm->reserved()) > 0 ||
        diff_in_current_scale(current_vm->reserved(), early_vm->reserved()) != 0) {
      out->print("(mmap: ");
      print_virtual_memory_diff(current_vm->reserved(), current_vm->committed(),
        early_vm->reserved(), early_vm->committed());
      out->print_cr(")");
    }

    // Report arena memory
    if (amount_in_current_scale(current_malloc->arena_size()) > 0 ||
        diff_in_current_scale(current_malloc->arena_size(), early_malloc->arena_size()) != 0) {
      out->print("(");
      print_arena_diff(current_malloc->arena_size(), current_malloc->arena_count(),
        early_malloc->arena_size(), early_malloc->arena_count());
      out->print_cr(")");
    }

    // Report native memory tracking overhead
    if (flag == mtNMT) {
      size_t current_tracking_overhead = amount_in_current_scale(_current_baseline.malloc_tracking_overhead());
      size_t early_tracking_overhead   = amount_in_current_scale(_early_baseline.malloc_tracking_overhead());

      out->print("(tracking overhead=" SIZE_FORMAT "%s",
                 amount_in_current_scale(_current_baseline.malloc_tracking_overhead()), scale);

      int64_t overhead_diff = diff_in_current_scale(_current_baseline.malloc_tracking_overhead(),
                                                    _early_baseline.malloc_tracking_overhead());
      if (overhead_diff != 0) {
        out->print(" " INT64_PLUS_FORMAT "%s", overhead_diff, scale);
      }
      out->print_cr(")");
    } else if (flag == mtClass) {
      print_metaspace_diff(current_ms, early_ms);
    }
    out->cr();
  }
}

void MemSummaryDiffReporter::print_metaspace_diff(const MetaspaceCombinedStats& current_ms,
                                                  const MetaspaceCombinedStats& early_ms) const {
  print_metaspace_diff("Metadata", current_ms.non_class_space_stats(), early_ms.non_class_space_stats());
  if (Metaspace::using_class_space()) {
    print_metaspace_diff("Class space", current_ms.class_space_stats(), early_ms.class_space_stats());
  }
}

void MemSummaryDiffReporter::print_metaspace_diff(const char* header,
                                                  const MetaspaceStats& current_stats,
                                                  const MetaspaceStats& early_stats) const {
  outputStream* out = output();
  const char* scale = current_scale();

  out->print_cr("(  %s)", header);
  out->print("(    ");
  print_virtual_memory_diff(current_stats.reserved(),
                            current_stats.committed(),
                            early_stats.reserved(),
                            early_stats.committed());
  out->print_cr(")");

  int64_t diff_used = diff_in_current_scale(current_stats.used(),
                                            early_stats.used());

  size_t current_waste = current_stats.committed() - current_stats.used();
  size_t early_waste = early_stats.committed() - early_stats.used();
  int64_t diff_waste = diff_in_current_scale(current_waste, early_waste);

  // Diff used
  out->print("(    used=" SIZE_FORMAT "%s",
             amount_in_current_scale(current_stats.used()), scale);
  if (diff_used != 0) {
    out->print(" " INT64_PLUS_FORMAT "%s", diff_used, scale);
  }
  out->print_cr(")");

  // Diff waste
  const float waste_percentage = current_stats.committed() == 0 ? 0.0f :
                                 ((float)current_waste * 100.0f) / (float)current_stats.committed();
  out->print("(    waste=" SIZE_FORMAT "%s =%2.2f%%",
             amount_in_current_scale(current_waste), scale, waste_percentage);
  if (diff_waste != 0) {
    out->print(" " INT64_PLUS_FORMAT "%s", diff_waste, scale);
  }
  out->print_cr(")");
}

void MemDetailDiffReporter::report_diff() {
  MemSummaryDiffReporter::report_diff();
  diff_malloc_sites();
  diff_virtual_memory_sites();
}

void MemDetailDiffReporter::diff_malloc_sites() const {
  MallocSiteIterator early_itr = _early_baseline.malloc_sites(MemBaseline::by_site_and_type);
  MallocSiteIterator current_itr = _current_baseline.malloc_sites(MemBaseline::by_site_and_type);

  const MallocSite* early_site   = early_itr.next();
  const MallocSite* current_site = current_itr.next();

  while (early_site != nullptr || current_site != nullptr) {
    if (early_site == nullptr) {
      new_malloc_site(current_site);
      current_site = current_itr.next();
    } else if (current_site == nullptr) {
      old_malloc_site(early_site);
      early_site = early_itr.next();
    } else {
      int compVal = current_site->call_stack()->compare(*early_site->call_stack());
      if (compVal < 0) {
        new_malloc_site(current_site);
        current_site = current_itr.next();
      } else if (compVal > 0) {
        old_malloc_site(early_site);
        early_site = early_itr.next();
      } else {
        diff_malloc_site(early_site, current_site);
        early_site   = early_itr.next();
        current_site = current_itr.next();
      }
    }
  }
}

void MemDetailDiffReporter::diff_virtual_memory_sites() const {
  VirtualMemorySiteIterator early_itr = _early_baseline.virtual_memory_sites(MemBaseline::by_site);
  VirtualMemorySiteIterator current_itr = _current_baseline.virtual_memory_sites(MemBaseline::by_site);

  const VirtualMemoryAllocationSite* early_site   = early_itr.next();
  const VirtualMemoryAllocationSite* current_site = current_itr.next();

  while (early_site != nullptr || current_site != nullptr) {
    if (early_site == nullptr) {
      new_virtual_memory_site(current_site);
      current_site = current_itr.next();
    } else if (current_site == nullptr) {
      old_virtual_memory_site(early_site);
      early_site = early_itr.next();
    } else {
      int compVal = current_site->call_stack()->compare(*early_site->call_stack());
      if (compVal < 0) {
        new_virtual_memory_site(current_site);
        current_site = current_itr.next();
      } else if (compVal > 0) {
        old_virtual_memory_site(early_site);
        early_site = early_itr.next();
      } else if (early_site->flag() != current_site->flag()) {
        // This site was originally allocated with one flag, then released,
        // then re-allocated at the same site (as far as we can tell) with a different flag.
        old_virtual_memory_site(early_site);
        early_site = early_itr.next();
        new_virtual_memory_site(current_site);
        current_site = current_itr.next();
      } else {
        diff_virtual_memory_site(early_site, current_site);
        early_site   = early_itr.next();
        current_site = current_itr.next();
      }
    }
  }
}


void MemDetailDiffReporter::new_malloc_site(const MallocSite* malloc_site) const {
  diff_malloc_site(malloc_site->call_stack(), malloc_site->size(), malloc_site->count(),
    0, 0, malloc_site->flag());
}

void MemDetailDiffReporter::old_malloc_site(const MallocSite* malloc_site) const {
  diff_malloc_site(malloc_site->call_stack(), 0, 0, malloc_site->size(),
    malloc_site->count(), malloc_site->flag());
}

void MemDetailDiffReporter::diff_malloc_site(const MallocSite* early,
  const MallocSite* current)  const {
  if (early->flag() != current->flag()) {
    // If malloc site type changed, treat it as deallocation of old type and
    // allocation of new type.
    old_malloc_site(early);
    new_malloc_site(current);
  } else {
    diff_malloc_site(current->call_stack(), current->size(), current->count(),
      early->size(), early->count(), early->flag());
  }
}

void MemDetailDiffReporter::diff_malloc_site(const NativeCallStack* stack, size_t current_size,
  size_t current_count, size_t early_size, size_t early_count, MEMFLAGS flags) const {
  outputStream* out = output();

  assert(stack != nullptr, "null stack");

  if (diff_in_current_scale(current_size, early_size) == 0) {
      return;
  }

  stack->print_on(out);
  INDENT_BY(28,
    out->print("(");
    print_malloc_diff(current_size, current_count, early_size, early_count, flags);
    out->print_cr(")");
  )
  out->cr();

}


void MemDetailDiffReporter::new_virtual_memory_site(const VirtualMemoryAllocationSite* site) const {
  diff_virtual_memory_site(site->call_stack(), site->reserved(), site->committed(), 0, 0, site->flag());
}

void MemDetailDiffReporter::old_virtual_memory_site(const VirtualMemoryAllocationSite* site) const {
  diff_virtual_memory_site(site->call_stack(), 0, 0, site->reserved(), site->committed(), site->flag());
}

void MemDetailDiffReporter::diff_virtual_memory_site(const VirtualMemoryAllocationSite* early,
  const VirtualMemoryAllocationSite* current) const {
  diff_virtual_memory_site(current->call_stack(), current->reserved(), current->committed(),
    early->reserved(), early->committed(), current->flag());
}

void MemDetailDiffReporter::diff_virtual_memory_site(const NativeCallStack* stack, size_t current_reserved,
  size_t current_committed, size_t early_reserved, size_t early_committed, MEMFLAGS flag) const  {
  outputStream* out = output();

  // no change
  if (diff_in_current_scale(current_reserved, early_reserved) == 0 &&
      diff_in_current_scale(current_committed, early_committed) == 0) {
    return;
  }

  stack->print_on(out);
  INDENT_BY(28,
    out->print("(mmap: ");
    print_virtual_memory_diff(current_reserved, current_committed, early_reserved, early_committed);
    if (flag != mtNone) {
      out->print(" Type=%s", NMTUtil::flag_to_name(flag));
    }
    out->print_cr(")");
  )
  out->cr();
}


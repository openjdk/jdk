/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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
#include "cds/filemap.hpp"
#include "logging/log.hpp"
#include "memory/metaspace.hpp"
#include "memory/metaspaceUtils.hpp"
#include "nmt/mallocTracker.hpp"
#include "nmt/memoryFileTracker.hpp"
#include "nmt/memReporter.hpp"
#include "nmt/memTag.hpp"
#include "nmt/memTracker.hpp"
#include "nmt/regionsTree.inline.hpp"
#include "nmt/threadStackTracker.hpp"
#include "nmt/virtualMemoryTracker.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"
#include "utilities/xmlstream.hpp"

#define INDENT_BY(num_chars, CODE) { \
  StreamIndentor si(out, num_chars); \
  { CODE }                           \
}

// Diff two counters, express them as signed, with range checks
static ssize_t counter_diff(size_t c1, size_t c2) {
  assert(c1 <= SSIZE_MAX, "counter out of range: %zu.", c1);
  assert(c2 <= SSIZE_MAX, "counter out of range: %zu.", c2);
  if (c1 > SSIZE_MAX || c2 > SSIZE_MAX) {
    return 0;
  }
  return c1 - c2;
}

MemReporterBase::MemReporterBase(outputStream* out, size_t scale) :
  _scale(scale), _output(out) {}

size_t MemReporterBase::reserved_total(const MallocMemory* malloc, const VirtualMemory* vm) {
  return malloc->malloc_size() + malloc->arena_size() + vm->reserved();
}

size_t MemReporterBase::committed_total(const MallocMemory* malloc, const VirtualMemory* vm) {
  return malloc->malloc_size() + malloc->arena_size() + vm->committed();
}

void MemReporterBase::print_total(size_t reserved, size_t committed, size_t peak) const {
  const char* scale = current_scale();
  output()->print("reserved=%zu%s, committed=%zu%s",
    amount_in_current_scale(reserved), scale, amount_in_current_scale(committed), scale);
  if (peak != 0) {
    output()->print(", peak=%zu%s", amount_in_current_scale(peak), scale);
  }
}

void MemReporterBase::print_malloc(const MemoryCounter* c, MemTag mem_tag) const {
  const char* scale = current_scale();
  outputStream* out = output();
  const char* alloc_type = (mem_tag == mtThreadStack) ? "" : "malloc=";

  const size_t amount = c->size();
  const size_t count = c->count();

  if (mem_tag != mtNone) {
    out->print("(%s%zu%s tag=%s", alloc_type,
      amount_in_current_scale(amount), scale, NMTUtil::tag_to_name(mem_tag));
  } else {
    out->print("(%s%zu%s", alloc_type,
      amount_in_current_scale(amount), scale);
  }

  // blends out mtChunk count number
  if (count > 0) {
    out->print(" #%zu", count);
  }

  out->print(")");

  size_t pk_amount = c->peak_size();
  if (pk_amount == amount) {
    out->print_raw(" (at peak)");
  } else if (pk_amount > amount) {
    size_t pk_count = c->peak_count();
    out->print(" (peak=%zu%s #%zu)",
        amount_in_current_scale(pk_amount), scale, pk_count);
  }
}

void MemReporterBase::print_virtual_memory(size_t reserved, size_t committed, size_t peak) const {
  outputStream* out = output();
  const char* scale = current_scale();
  out->print("(mmap: reserved=%zu%s, committed=%zu%s, ",
    amount_in_current_scale(reserved), scale, amount_in_current_scale(committed), scale);
  if (peak == committed) {
    out->print_raw("at peak)");
  } else {
    out->print("peak=%zu%s)", amount_in_current_scale(peak), scale);
  }
}

void MemReporterBase::print_arena(const MemoryCounter* c) const {
  const char* scale = current_scale();
  outputStream* out = output();

  const size_t amount = c->size();
  const size_t count = c->count();

  out->print("(arena=%zu%s #%zu)",
             amount_in_current_scale(amount), scale, count);

  size_t pk_amount = c->peak_size();
  if (pk_amount == amount) {
    out->print_raw(" (at peak)");
  } else if (pk_amount > amount) {
    size_t pk_count = c->peak_count();
    out->print(" (peak=%zu%s #%zu)",
        amount_in_current_scale(pk_amount), scale, pk_count);
  }
}

void MemReporterBase::print_virtual_memory_region(const char* type, address base, size_t size) const {
  const char* scale = current_scale();
  output()->print("[" PTR_FORMAT " - " PTR_FORMAT "] %s %zu%s",
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
    out->print_cr("malloc: %zu%s #%zu, peak=%zu%s #%zu",
                  amount_in_current_scale(total_malloced_bytes), current_scale(),
                  _malloc_snapshot->total_count(),
                  amount_in_current_scale(_malloc_snapshot->total_peak()),
                  current_scale(), _malloc_snapshot->total_peak_count());
    out->print("mmap:   ");
    print_total(total_mmap_reserved_bytes, total_mmap_committed_bytes);
  )
  out->cr();
  out->cr();

  // Summary by memory tag
  for (int index = 0; index < mt_number_of_tags; index ++) {
    MemTag mem_tag = NMTUtil::index_to_tag(index);
    // thread stack is reported as part of thread category
    if (mem_tag == mtThreadStack) continue;
    MallocMemory* malloc_memory = _malloc_snapshot->by_tag(mem_tag);
    VirtualMemory* virtual_memory = _vm_snapshot->by_tag(mem_tag);

    report_summary_of_tag(mem_tag, malloc_memory, virtual_memory);
  }
}
void MemSummaryReporter::report_summary_of_tag(MemTag mem_tag,
  MallocMemory*  malloc_memory, VirtualMemory* virtual_memory) {

  size_t reserved_amount  = reserved_total (malloc_memory, virtual_memory);
  size_t committed_amount = committed_total(malloc_memory, virtual_memory);

  // Count thread's native stack in "Thread" category
  if (mem_tag == mtThread) {
    const VirtualMemory* thread_stack_usage =
      (const VirtualMemory*)_vm_snapshot->by_tag(mtThreadStack);
    reserved_amount  += thread_stack_usage->reserved();
    committed_amount += thread_stack_usage->committed();
  } else if (mem_tag == mtNMT) {
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
  out->print("-%*s (", indent - 2, NMTUtil::tag_to_name(mem_tag));
  print_total(reserved_amount, committed_amount);
#if INCLUDE_CDS
  if (mem_tag == mtClassShared) {
      size_t read_only_bytes = FileMapInfo::readonly_total();
    output()->print(", readonly=%zu%s",
                    amount_in_current_scale(read_only_bytes), scale);
  }
#endif
  out->print_cr(")");

  StreamIndentor si(out, indent);

  if (mem_tag == mtClass) {
    // report class count
    out->print_cr("(classes #%zu)", (_instance_class_count + _array_class_count));
    out->print_cr("(  instance classes #%zu, array classes #%zu)",
                  _instance_class_count, _array_class_count);
  } else if (mem_tag == mtThread) {
    const VirtualMemory* thread_stack_usage =
     _vm_snapshot->by_tag(mtThreadStack);
    // report thread count
    out->print_cr("(threads #%zu)", ThreadStackTracker::thread_count());
    out->print("(stack: ");
    print_total(thread_stack_usage->reserved(), thread_stack_usage->committed(), thread_stack_usage->peak_size());
    out->print_cr(")");
  }

   // report malloc'd memory
  if (amount_in_current_scale(MAX2(malloc_memory->malloc_size(), pk_malloc)) > 0) {
    print_malloc(malloc_memory->malloc_counter(), mem_tag);
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

  if (mem_tag == mtNMT &&
    amount_in_current_scale(_malloc_snapshot->malloc_overhead()) > 0) {
    out->print_cr("(tracking overhead=%zu%s)",
                   amount_in_current_scale(_malloc_snapshot->malloc_overhead()), scale);
  } else if (mem_tag == mtClass) {
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
  out->print_cr("(    used=%zu%s)", amount_in_current_scale(stats.used()), scale);
  out->print_cr("(    waste=%zu%s =%2.2f%%)", amount_in_current_scale(waste),
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
    _stackprinter.print_stack(stack);
    MemTag mem_tag = malloc_site->mem_tag();
    assert(NMTUtil::tag_is_valid(mem_tag) && mem_tag != mtNone,
      "Must have a valid memory tag");
    INDENT_BY(29,
      out->print("(");
      print_malloc(malloc_site->counter(), mem_tag);
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
    _stackprinter.print_stack(stack);
    INDENT_BY(29,
      out->print("(");
      print_total(virtual_memory_site->reserved(), virtual_memory_site->committed());
      const MemTag mem_tag = virtual_memory_site->mem_tag();
      if (mem_tag != mtNone) {
        out->print(" Tag=%s", NMTUtil::tag_to_name(mem_tag));
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
  bool all_committed = reserved_rgn->size() == VirtualMemoryTracker::Instance::committed_size(reserved_rgn);
  const char* region_type = (all_committed ? "reserved and committed" : "reserved");
  out->cr();
  print_virtual_memory_region(region_type, reserved_rgn->base(), reserved_rgn->size());
  out->print(" for %s", NMTUtil::tag_to_name(reserved_rgn->mem_tag()));
  if (stack->is_empty()) {
    out->cr();
  } else {
    out->print_cr(" from");
    INDENT_BY(4, _stackprinter.print_stack(stack);)
  }

  if (all_committed) {
    bool reserved_and_committed = false;
    VirtualMemoryTracker::Instance::tree()->visit_committed_regions(*reserved_rgn,
                                                                  [&](CommittedMemoryRegion& committed_rgn) {
      if (committed_rgn.equals(*reserved_rgn)) {
        // One region spanning the entire reserved region, with the same stack trace.
        // Don't print this regions because the "reserved and committed" line above
        // already indicates that the region is committed.
        reserved_and_committed = true;
        return false;
      }
      return true;
    });

    if (reserved_and_committed) {
      return;
    }
  }

  auto print_committed_rgn = [&](const CommittedMemoryRegion& crgn) {
    // Don't report if size is too small
    if (amount_in_current_scale(crgn.size()) == 0) return;
    stack = crgn.call_stack();
    out->cr();
    INDENT_BY(8,
      print_virtual_memory_region("committed", crgn.base(), crgn.size());
      if (stack->is_empty()) {
        out->cr();
      } else {
        out->print_cr(" from");
        INDENT_BY(4, _stackprinter.print_stack(stack);)
      }
    )
  };

  VirtualMemoryTracker::Instance::tree()->visit_committed_regions(*reserved_rgn,
                                                                  [&](CommittedMemoryRegion& crgn) {
    print_committed_rgn(crgn);
    return true;
  });
}

void MemDetailReporter::report_memory_file_allocations() {
  stringStream st;
  {
    MemTracker::NmtVirtualMemoryLocker nvml;
    MemoryFileTracker::Instance::print_all_reports_on(&st, scale());
  }
  output()->print_raw(st.freeze());
}

void MemSummaryDiffReporter::report_diff() const {
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

  // malloc diff
  const size_t early_malloced_bytes =
    _early_baseline.malloc_memory_snapshot()->total();
  const size_t early_count =
    _early_baseline.malloc_memory_snapshot()->total_count();
  const size_t current_malloced_bytes =
    _current_baseline.malloc_memory_snapshot()->total();
  const size_t current_count =
    _current_baseline.malloc_memory_snapshot()->total_count();
  print_malloc_diff(current_malloced_bytes, current_count, early_malloced_bytes,
                    early_count, mtNone);
  out->cr();
  out->cr();

  // mmap diff
  out->print("mmap: ");
  const size_t early_reserved =
    _early_baseline.virtual_memory_snapshot()->total_reserved();
  const size_t early_committed =
    _early_baseline.virtual_memory_snapshot()->total_committed();
  const size_t current_reserved =
    _current_baseline.virtual_memory_snapshot()->total_reserved();
  const size_t current_committed =
    _current_baseline.virtual_memory_snapshot()->total_committed();
  print_virtual_memory_diff(current_reserved, current_committed, early_reserved,
                            early_committed);
  out->cr();
  out->cr();

  // Summary diff by memory tag
  for (int index = 0; index < mt_number_of_tags; index ++) {
    MemTag mem_tag = NMTUtil::index_to_tag(index);
    // thread stack is reported as part of thread category
    if (mem_tag == mtThreadStack) continue;
    diff_summary_of_tag(mem_tag,
      _early_baseline.malloc_memory(mem_tag),
      _early_baseline.virtual_memory(mem_tag),
      _early_baseline.metaspace_stats(),
      _current_baseline.malloc_memory(mem_tag),
      _current_baseline.virtual_memory(mem_tag),
      _current_baseline.metaspace_stats());
  }
}

void MemSummaryDiffReporter::print_malloc_diff(size_t current_amount, size_t current_count,
    size_t early_amount, size_t early_count, MemTag mem_tag) const {
  const char* scale = current_scale();
  outputStream* out = output();
  const char* alloc_tag = (mem_tag == mtThread) ? "" : "malloc=";

  out->print("%s%zu%s", alloc_tag, amount_in_current_scale(current_amount), scale);
  // Report type only if it is valid and not under "thread" category
  if (mem_tag != mtNone && mem_tag != mtThread) {
    out->print(" type=%s", NMTUtil::tag_to_name(mem_tag));
  }

  int64_t amount_diff = diff_in_current_scale(current_amount, early_amount);
  if (amount_diff != 0) {
    out->print(" " INT64_PLUS_FORMAT "%s", amount_diff, scale);
  }
  if (current_count > 0) {
    out->print(" #%zu", current_count);
    const ssize_t delta_count = counter_diff(current_count, early_count);
    if (delta_count != 0) {
      out->print(" %+zd", delta_count);
    }
  }
}

void MemSummaryDiffReporter::print_arena_diff(size_t current_amount, size_t current_count,
  size_t early_amount, size_t early_count) const {
  const char* scale = current_scale();
  outputStream* out = output();
  out->print("arena=%zu%s", amount_in_current_scale(current_amount), scale);
  int64_t amount_diff = diff_in_current_scale(current_amount, early_amount);
  if (amount_diff != 0) {
    out->print(" " INT64_PLUS_FORMAT "%s", amount_diff, scale);
  }

  out->print(" #%zu", current_count);
  const ssize_t delta_count = counter_diff(current_count, early_count);
  if (delta_count != 0) {
    out->print(" %+zd", delta_count);
  }
}

void MemSummaryDiffReporter::print_virtual_memory_diff(size_t current_reserved, size_t current_committed,
    size_t early_reserved, size_t early_committed) const {
  const char* scale = current_scale();
  outputStream* out = output();
  out->print("reserved=%zu%s", amount_in_current_scale(current_reserved), scale);
  int64_t reserved_diff = diff_in_current_scale(current_reserved, early_reserved);
  if (reserved_diff != 0) {
    out->print(" " INT64_PLUS_FORMAT "%s", reserved_diff, scale);
  }

  out->print(", committed=%zu%s", amount_in_current_scale(current_committed), scale);
  int64_t committed_diff = diff_in_current_scale(current_committed, early_committed);
  if (committed_diff != 0) {
    out->print(" " INT64_PLUS_FORMAT "%s", committed_diff, scale);
  }
}


void MemSummaryDiffReporter::diff_summary_of_tag(MemTag mem_tag,
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
  if (mem_tag == mtThread) {
    const VirtualMemory* early_thread_stack_usage =
      _early_baseline.virtual_memory(mtThreadStack);
    const VirtualMemory* current_thread_stack_usage =
      _current_baseline.virtual_memory(mtThreadStack);

    early_reserved_amount  += early_thread_stack_usage->reserved();
    early_committed_amount += early_thread_stack_usage->committed();

    current_reserved_amount  += current_thread_stack_usage->reserved();
    current_committed_amount += current_thread_stack_usage->committed();
  } else if (mem_tag == mtNMT) {
    early_reserved_amount  += _early_baseline.malloc_tracking_overhead();
    early_committed_amount += _early_baseline.malloc_tracking_overhead();

    current_reserved_amount  += _current_baseline.malloc_tracking_overhead();
    current_committed_amount += _current_baseline.malloc_tracking_overhead();
  }

  if (amount_in_current_scale(current_reserved_amount) > 0 ||
      diff_in_current_scale(current_reserved_amount, early_reserved_amount) != 0) {

    // print summary line
    out->print("-%*s (", indent - 2, NMTUtil::tag_to_name(mem_tag));
    print_virtual_memory_diff(current_reserved_amount, current_committed_amount,
      early_reserved_amount, early_committed_amount);
    out->print_cr(")");

    StreamIndentor si(out, indent);

    // detail lines
    if (mem_tag == mtClass) {
      // report class count
      out->print("(classes #%zu", _current_baseline.class_count());
      const ssize_t class_count_diff =
          counter_diff(_current_baseline.class_count(), _early_baseline.class_count());
      if (class_count_diff != 0) {
        out->print(" %+zd", class_count_diff);
      }
      out->print_cr(")");

      out->print("(  instance classes #%zu", _current_baseline.instance_class_count());
      const ssize_t instance_class_count_diff =
          counter_diff(_current_baseline.instance_class_count(), _early_baseline.instance_class_count());
      if (instance_class_count_diff != 0) {
        out->print(" %+zd", instance_class_count_diff);
      }
      out->print(", array classes #%zu", _current_baseline.array_class_count());
      const ssize_t array_class_count_diff =
          counter_diff(_current_baseline.array_class_count(), _early_baseline.array_class_count());
      if (array_class_count_diff != 0) {
        out->print(" %+zd", array_class_count_diff);
      }
      out->print_cr(")");

    } else if (mem_tag == mtThread) {
      // report thread count
      out->print("(threads #%zu", _current_baseline.thread_count());
      const ssize_t thread_count_diff = counter_diff(_current_baseline.thread_count(), _early_baseline.thread_count());
      if (thread_count_diff != 0) {
        out->print(" %+zd", thread_count_diff);
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
      print_malloc_diff(current_malloc_amount, (mem_tag == mtChunk) ? 0 : current_malloc->malloc_count(),
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
    if (mem_tag == mtNMT) {
      size_t current_tracking_overhead = amount_in_current_scale(_current_baseline.malloc_tracking_overhead());
      size_t early_tracking_overhead   = amount_in_current_scale(_early_baseline.malloc_tracking_overhead());

      out->print("(tracking overhead=%zu%s",
                 amount_in_current_scale(_current_baseline.malloc_tracking_overhead()), scale);

      int64_t overhead_diff = diff_in_current_scale(_current_baseline.malloc_tracking_overhead(),
                                                    _early_baseline.malloc_tracking_overhead());
      if (overhead_diff != 0) {
        out->print(" " INT64_PLUS_FORMAT "%s", overhead_diff, scale);
      }
      out->print_cr(")");
    } else if (mem_tag == mtClass) {
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
  out->print("(    used=%zu%s",
             amount_in_current_scale(current_stats.used()), scale);
  if (diff_used != 0) {
    out->print(" " INT64_PLUS_FORMAT "%s", diff_used, scale);
  }
  out->print_cr(")");

  // Diff waste
  const float waste_percentage = current_stats.committed() == 0 ? 0.0f :
                                 ((float)current_waste * 100.0f) / (float)current_stats.committed();
  out->print("(    waste=%zu%s =%2.2f%%",
             amount_in_current_scale(current_waste), scale, waste_percentage);
  if (diff_waste != 0) {
    out->print(" " INT64_PLUS_FORMAT "%s", diff_waste, scale);
  }
  out->print_cr(")");
}

void MemDetailDiffReporter::report_diff() const{
  MemSummaryDiffReporter::report_diff();
  diff_malloc_sites();
  diff_virtual_memory_sites();
}

void MemDetailDiffReporter::diff_malloc_sites() const {
  MallocSiteIterator early_itr = _early_baseline.malloc_sites(MemBaseline::by_site_and_tag);
  MallocSiteIterator current_itr = _current_baseline.malloc_sites(MemBaseline::by_site_and_tag);

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
      } else if (early_site->mem_tag() != current_site->mem_tag()) {
        // This site was originally allocated with one memory tag, then released,
        // then re-allocated at the same site (as far as we can tell) with a different memory tag.
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
    0, 0, malloc_site->mem_tag());
}

void MemDetailDiffReporter::old_malloc_site(const MallocSite* malloc_site) const {
  diff_malloc_site(malloc_site->call_stack(), 0, 0, malloc_site->size(),
    malloc_site->count(), malloc_site->mem_tag());
}

void MemDetailDiffReporter::diff_malloc_site(const MallocSite* early,
  const MallocSite* current)  const {
  if (early->mem_tag() != current->mem_tag()) {
    // If malloc site type changed, treat it as deallocation of old type and
    // allocation of new type.
    old_malloc_site(early);
    new_malloc_site(current);
  } else {
    diff_malloc_site(current->call_stack(), current->size(), current->count(),
      early->size(), early->count(), early->mem_tag());
  }
}

void MemDetailDiffReporter::diff_malloc_site(const NativeCallStack* stack, size_t current_size,
  size_t current_count, size_t early_size, size_t early_count, MemTag mem_tag) const {
  outputStream* out = output();

  assert(stack != nullptr, "null stack");

  if (diff_in_current_scale(current_size, early_size) == 0) {
      return;
  }

  _stackprinter.print_stack(stack);
  INDENT_BY(28,
    out->print("(");
    print_malloc_diff(current_size, current_count, early_size, early_count, mem_tag);
    out->print_cr(")");
  )
  out->cr();

}


void MemDetailDiffReporter::new_virtual_memory_site(const VirtualMemoryAllocationSite* site) const {
  diff_virtual_memory_site(site->call_stack(), site->reserved(), site->committed(), 0, 0, site->mem_tag());
}

void MemDetailDiffReporter::old_virtual_memory_site(const VirtualMemoryAllocationSite* site) const {
  diff_virtual_memory_site(site->call_stack(), 0, 0, site->reserved(), site->committed(), site->mem_tag());
}

void MemDetailDiffReporter::diff_virtual_memory_site(const VirtualMemoryAllocationSite* early,
  const VirtualMemoryAllocationSite* current) const {
  diff_virtual_memory_site(current->call_stack(), current->reserved(), current->committed(),
    early->reserved(), early->committed(), current->mem_tag());
}

void MemDetailDiffReporter::diff_virtual_memory_site(const NativeCallStack* stack, size_t current_reserved,
  size_t current_committed, size_t early_reserved, size_t early_committed, MemTag mem_tag) const  {
  outputStream* out = output();

  // no change
  if (diff_in_current_scale(current_reserved, early_reserved) == 0 &&
      diff_in_current_scale(current_committed, early_committed) == 0) {
    return;
  }

  _stackprinter.print_stack(stack);
  INDENT_BY(28,
    out->print("(mmap: ");
    print_virtual_memory_diff(current_reserved, current_committed, early_reserved, early_committed);
    if (mem_tag != mtNone) {
      out->print(" Type=%s", NMTUtil::tag_to_name(mem_tag));
    }
    out->print_cr(")");
  )
  out->cr();
}
class XmlElemHelper {
 private:
  const char* _node;

 protected:
  xmlStream* xs;

 public:
  XmlElemHelper(xmlStream* st, const char* node): _node(node), xs(st) {
    xs->head("%s", _node);
  }
  ~XmlElemHelper() {
    xs->tail(_node);
  }
};

class XmlElemStack : public XmlElemHelper {
 public:
  XmlElemStack(xmlStream* st, const char* text) : XmlElemHelper(st, text) {
    st->print_raw("<![CDATA[");
  }
  ~XmlElemStack() {
    xs->print_raw("]]>");
  }
};

#define XmlParentElement(txt) XmlElemHelper _not_used(xml_output(), txt)
#define XmlStackElement XmlElemStack __not_used(xml_output(), "stack")

// Put all the <elem>text</elem> in one line
#define XmlElementWithText(ename, txt, ...)   \
    xs->write("<", 1);                        \
    xs->text()->print("%s", ename);           \
    xs->write(">", 1);                        \
    xs->text()->print(txt, ##__VA_ARGS__);    \
    xs->write("</", 2);                       \
    xs->text()->print("%s", ename);           \
    xs->write(">\n", 2);



XmlMemSummaryReporter::XmlMemSummaryReporter(MemBaseline& baseline, fileStream* output, size_t scale) :
 _malloc_snapshot(baseline.malloc_memory_snapshot()),
 _vm_snapshot(baseline.virtual_memory_snapshot()),
 _instance_class_count(baseline.instance_class_count()),
 _array_class_count(baseline.array_class_count()) ,
 _scale(scale) {
  _xml_output = new (mtNMT) xmlStream(output);
}

XmlMemSummaryReporter::~XmlMemSummaryReporter() {
  if(_xml_output != nullptr) {
    _xml_output->flush();
  }
  delete _xml_output;
}

void XmlMemSummaryReporter::print_malloc(const MemoryCounter* c, MemTag mem_tag) const {
  xmlStream* xs = xml_output();

  const size_t amount = amount_in_current_scale(c->size());
  const size_t count = c->count();
  const size_t pk_amount = amount_in_current_scale(c->peak_size());
  XmlParentElement("malloc");
  XmlElementWithText("memoryTag", "%s", NMTUtil::tag_to_name(mem_tag));
  XmlElementWithText(mem_tag == mtThreadStack ? "threadStack" : "malloc", "%zu", amount);
  XmlElementWithText("count", "%zu", count);
  XmlElementWithText("atPeak", "%d", pk_amount == amount);
  XmlElementWithText("amountPeak", "%zu", pk_amount);
  XmlElementWithText("countPeak", "%zu", c->peak_count());
}

void XmlMemSummaryReporter::print_virtual_memory(size_t reserved, size_t committed, size_t peak) const {
  xmlStream* xs = xml_output();
  XmlParentElement("mmap");
  XmlElementWithText("reserved", "%zu", amount_in_current_scale(reserved));
  XmlElementWithText("committed", "%zu", amount_in_current_scale(committed));
  XmlElementWithText("atPeak", "%d", peak == committed);
  XmlElementWithText("peak", "%zu", amount_in_current_scale(peak));
}

void XmlMemSummaryReporter::print_arena(const MemoryCounter* c) const {
  xmlStream* xs = xml_output();

  const size_t amount = c->size();
  const size_t count = c->count();
  const size_t pk_amount = c->peak_size();
  const size_t pk_count = c->peak_count();

  XmlParentElement("arena");
  XmlElementWithText("amount", "%zu", amount_in_current_scale(amount));
  XmlElementWithText("count", "%zu", amount_in_current_scale(count));
  XmlElementWithText("atPeak", "%d", pk_amount == amount);
  XmlElementWithText("countPeak", "%zu", amount_in_current_scale(pk_count));
}

void XmlMemSummaryReporter::print_virtual_memory_region(const char* type, address base, size_t size) const {
  xmlStream* xs = xml_output();
  XmlParentElement("region");
  XmlElementWithText("base", PTR_FORMAT, p2i(base));
  XmlElementWithText("end", PTR_FORMAT, p2i(base+size));
  XmlElementWithText("size", "%zu", amount_in_current_scale(size));
  XmlElementWithText("state", "%s", type);
}

void XmlMemSummaryReporter::report(bool summary_only) const {

  xmlStream* xs = xml_output();
  assert(xs != nullptr, "sanity");
  const size_t total_malloced_bytes       = _malloc_snapshot->total();
  const size_t total_mmap_reserved_bytes  = _vm_snapshot->total_reserved();
  const size_t total_mmap_committed_bytes = _vm_snapshot->total_committed();
  const size_t total_reserved_amount      = total_malloced_bytes + total_mmap_reserved_bytes;
  const size_t total_committed_amount     = total_malloced_bytes + total_mmap_committed_bytes;

  xs->head("nativeMemoryTracking scale=\"%s\"", current_scale());
  XmlElementWithText("report", summary_only ? "Summary" : "Detail");
  if (scale() > 1) {
    XmlElementWithText("warning", "(Omitting categories weighting less than 1%s)", current_scale());
  }
  {
    XmlParentElement("total");
    XmlElementWithText("reserved", "%zu", amount_in_current_scale(total_reserved_amount));
    XmlElementWithText("committed", "%zu", amount_in_current_scale(total_committed_amount));
  }
  {
    XmlParentElement("malloc");
    XmlElementWithText("size", "%zu", amount_in_current_scale(total_malloced_bytes));
    XmlElementWithText("count", "%zu", _malloc_snapshot->total_count());
    XmlElementWithText("sizePeak", "%zu", amount_in_current_scale(_malloc_snapshot->total_peak()));
    XmlElementWithText("countPeak", "%zu", _malloc_snapshot->total_peak_count());
  }
  {
    XmlParentElement("mmap");
    XmlElementWithText("reserved", "%zu", amount_in_current_scale(total_mmap_reserved_bytes));
    XmlElementWithText("committed", "%zu", amount_in_current_scale(total_mmap_committed_bytes));
  }
  {
    XmlParentElement("memoryTags");
    for (int index = 0; index < mt_number_of_tags; index ++) {
      MemTag mem_tag = NMTUtil::index_to_tag(index);
      if (mem_tag == mtThreadStack) continue;

      MallocMemory* malloc_memory = _malloc_snapshot->by_tag(mem_tag);
      VirtualMemory* virtual_memory = _vm_snapshot->by_tag(mem_tag);

      report_summary_of_tag(mem_tag, malloc_memory, virtual_memory);
    }
  }
  if (summary_only) {
    xs->tail("nativeMemoryTracking");
  }
}

void XmlMemSummaryReporter::report_summary_of_tag(MemTag mem_tag,
  MallocMemory* malloc_memory, VirtualMemory* virtual_memory) const {

  size_t reserved_amount  = MemReporterBase::reserved_total (malloc_memory, virtual_memory);
  size_t committed_amount = MemReporterBase::committed_total(malloc_memory, virtual_memory);

  // Count thread's native stack in "Thread" category
  if (mem_tag == mtThread) {
    const VirtualMemory* thread_stack_usage =
      (const VirtualMemory*)_vm_snapshot->by_tag(mtThreadStack);
    reserved_amount  += thread_stack_usage->reserved();
    committed_amount += thread_stack_usage->committed();
  } else if (mem_tag == mtNMT) {
    // Count malloc headers in "NMT" category
    reserved_amount  += _malloc_snapshot->malloc_overhead();
    committed_amount += _malloc_snapshot->malloc_overhead();
  }

  // Omit printing if the current reserved value as well as all historical peaks (malloc, mmap committed, arena)
  // fall below scale threshold
  const size_t pk_vm     = virtual_memory->peak_size();
  const size_t pk_malloc = malloc_memory->malloc_peak_size();
  const size_t pk_arena  = malloc_memory->arena_peak_size();

  if (amount_in_current_scale(MAX4(reserved_amount, pk_vm, pk_malloc, pk_arena)) == 0) {
    return;
  }

  xmlStream* xs = xml_output();
  XmlParentElement("memoryTag");
  XmlElementWithText("name", "%s", NMTUtil::tag_to_name(mem_tag));
  {
    XmlParentElement("total");
    XmlElementWithText("reserved", "%zu", amount_in_current_scale(reserved_amount));
    XmlElementWithText("committed", "%zu", amount_in_current_scale(committed_amount));
  }
#if INCLUDE_CDS
  if (mem_tag == mtClassShared) {
      size_t read_only_bytes = FileMapInfo::readonly_total();
      XmlElementWithText("readonly", "%zu", amount_in_current_scale(read_only_bytes));
  }
#endif



  if (mem_tag == mtClass) {
    // report class count
    XmlElementWithText("classes", "%zu", _instance_class_count + _array_class_count);
    XmlElementWithText("instanceClasses", "%zu", _instance_class_count);
    XmlElementWithText("arrayClasses", "%zu", _array_class_count);
  } else if (mem_tag == mtThread) {
    const VirtualMemory* thread_stack_usage =_vm_snapshot->by_tag(mtThreadStack);
    // report thread count
    XmlElementWithText("threads", "%zu", ThreadStackTracker::thread_count());
    {
      XmlParentElement("threadStack");
      XmlElementWithText("reserved", "%zu", thread_stack_usage->reserved());
      XmlElementWithText("committed", "%zu", thread_stack_usage->committed());
      XmlElementWithText("peak", "%zu", thread_stack_usage->peak_size());
    }
  }

   // report malloc'd memory
  if (amount_in_current_scale(MAX2(malloc_memory->malloc_size(), pk_malloc)) > 0) {
    print_malloc(malloc_memory->malloc_counter(), mem_tag);
  }

  if (amount_in_current_scale(MAX2(virtual_memory->reserved(), pk_vm)) > 0) {
    print_virtual_memory(virtual_memory->reserved(), virtual_memory->committed(), virtual_memory->peak_size());
  }

  if (amount_in_current_scale(MAX2(malloc_memory->arena_size(), pk_arena)) > 0) {
    print_arena(malloc_memory->arena_counter());
  }

  if (mem_tag == mtNMT &&
    amount_in_current_scale(_malloc_snapshot->malloc_overhead()) > 0) {
    XmlElementWithText("trackingOverhead", "%zu", amount_in_current_scale(_malloc_snapshot->malloc_overhead()));
  } else if (mem_tag == mtClass) {
    // Metadata information
    report_metadata(Metaspace::NonClassType);
    if (Metaspace::using_class_space()) {
      report_metadata(Metaspace::ClassType);
    }
  }


}

void XmlMemSummaryReporter::report_metadata(Metaspace::MetadataType type) const {

  // NMT reports may be triggered (as part of error handling) very early. Make sure
  // Metaspace is already initialized.
  if (!Metaspace::initialized()) {
    return;
  }

  assert(type == Metaspace::NonClassType || type == Metaspace::ClassType, "Invalid metadata type");
  const char* name = (type == Metaspace::NonClassType) ? "metadata" : "classSpace";

  xmlStream* xs = xml_output();
  const MetaspaceStats stats = MetaspaceUtils::get_statistics(type);

  size_t waste = stats.committed() - stats.used();
  float waste_percentage = stats.committed() > 0 ? (((float)waste * 100)/(float)stats.committed()) : 0.0f;

  XmlParentElement(name);
  {
    XmlParentElement("total");
    XmlElementWithText("reserved", "%zu", amount_in_current_scale(stats.reserved()));
    XmlElementWithText("committed", "%zu", amount_in_current_scale(stats.committed()));
    XmlElementWithText("used", "%zu", amount_in_current_scale(stats.used()));
    XmlElementWithText("waste", "%zu", amount_in_current_scale(waste));
    XmlElementWithText("wastePercentage", "%2.2f", waste_percentage);
  }
}

void XmlMemDetailReporter::report() const {
  XmlMemSummaryReporter::report(/*summary only*/false);
  report_virtual_memory_map();
  report_memory_file_allocations();
  report_detail();
  xml_output()->tail("nativeMemoryTracking");
}

void XmlMemDetailReporter::report_detail() const {
  xmlStream* xs = xml_output();

  XmlParentElement("details");

  int num_omitted = report_malloc_sites() + report_virtual_memory_allocation_sites();
  {
    XmlParentElement("ommitted");
    XmlElementWithText("count", "%d", num_omitted);
    XmlElementWithText("scale", "%s", current_scale());
  }
}

int XmlMemDetailReporter::report_malloc_sites() const {
  MallocSiteIterator         malloc_itr = _baseline.malloc_sites(MemBaseline::by_size);
  if (malloc_itr.is_empty()) return 0;

  xmlStream* xs = xml_output();

  const MallocSite* malloc_site;
  int num_omitted = 0;
  XmlParentElement("mallocSites");
  while ((malloc_site = malloc_itr.next()) != nullptr) {
    // Omit printing if the current value and the historic peak value both fall below the reporting scale threshold
    if (amount_in_current_scale(MAX2(malloc_site->size(), malloc_site->peak_size())) == 0) {
      num_omitted ++;
      continue;
    }
    XmlParentElement("mallocSite");
    {
      XmlStackElement;
      _stackprinter.print_stack(malloc_site->call_stack());

    }
    MemTag mem_tag = malloc_site->mem_tag();
    assert(NMTUtil::tag_is_valid(mem_tag) && mem_tag != mtNone, "Must have a valid memory tag");
    {
      XmlParentElement("malloc");
      print_malloc(malloc_site->counter(), mem_tag);
    }
  }
  return num_omitted;
}

int XmlMemDetailReporter::report_virtual_memory_allocation_sites() const {
  VirtualMemorySiteIterator  virtual_memory_itr =
    _baseline.virtual_memory_sites(MemBaseline::by_size);

  if (virtual_memory_itr.is_empty()) return 0;


  xmlStream* xs = xml_output();

  const VirtualMemoryAllocationSite*  virtual_memory_site;
  int num_omitted = 0;
  XmlParentElement("virtualMemoryAllocationSites");
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
    {
      XmlParentElement("AllocSite");
      {
        XmlStackElement;
        _stackprinter.print_stack(virtual_memory_site->call_stack());
      }
      {
        XmlParentElement("total");
        XmlElementWithText("reserved", "%zu", virtual_memory_site->reserved());
        XmlElementWithText("committed", "%zu", virtual_memory_site->committed());
        XmlElementWithText("memoryTag", "%s", NMTUtil::tag_to_name(virtual_memory_site->mem_tag()));
      }
    }
  }
  return num_omitted;
}

void XmlMemDetailReporter::report_virtual_memory_map() const{
  // Virtual memory map always in base address order
  VirtualMemoryAllocationIterator itr = _baseline.virtual_memory_allocations();
  const ReservedMemoryRegion* rgn;
  xmlStream* xs = xml_output();
  XmlParentElement("virtualMemoryMap");
  while ((rgn = itr.next()) != nullptr) {
    XmlParentElement("region");
    report_virtual_memory_region(rgn);
  }
}

void XmlMemDetailReporter::report_virtual_memory_region(const ReservedMemoryRegion* reserved_rgn) const {
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
  xmlStream* xs = xml_output();
  const NativeCallStack*  stack = reserved_rgn->call_stack();
  bool all_committed = reserved_rgn->size() == VirtualMemoryTracker::Instance::committed_size(reserved_rgn);
  const char* region_type = (all_committed ? "reservedAndCommitted" : "reserved");

  print_virtual_memory_region(region_type, reserved_rgn->base(), reserved_rgn->size());

  XmlElementWithText("memoryTag", "%s", NMTUtil::tag_to_name(reserved_rgn->mem_tag()));

  {
    XmlStackElement;
    _stackprinter.print_stack(stack);
  }

  if (all_committed) {
    bool reserved_and_committed = false;
    VirtualMemoryTracker::Instance::tree()->visit_committed_regions(*reserved_rgn,
                                                                  [&](CommittedMemoryRegion& committed_rgn) {
      if (committed_rgn.equals(*reserved_rgn)) {
        // One region spanning the entire reserved region, with the same stack trace.
        // Don't print this regions because the "reserved and committed" line above
        // already indicates that the region is committed.
        reserved_and_committed = true;
        return false;
      }
      return true;
    });

    if (reserved_and_committed) {
      return;
    }
  }
  auto print_committed_rgn = [&](const CommittedMemoryRegion& crgn) {
    XmlParentElement("committedRegion");

    print_virtual_memory_region("committed", crgn.base(), crgn.size());
    {
      XmlStackElement;
      _stackprinter.print_stack(crgn.call_stack());
    }
  };

  VirtualMemoryTracker::Instance::tree()->visit_committed_regions(*reserved_rgn,
                                                                 [&](CommittedMemoryRegion& crgn) {
    print_committed_rgn(crgn);
    return true;
  });
}

void XmlMemDetailReporter::report_memory_file_allocations() const {
  MemTracker::NmtVirtualMemoryLocker nvml;
  MemoryFileTracker::Instance::print_all_reports_xml_on(xml_output(), scale());
}

void XmlMemSummaryDiffReporter::report_diff(bool summary_only) const {
  xmlStream* xs = xml_output();
  xs->head("nativeMemoryTracking scale=\"%s\"", current_scale());
  XmlElementWithText("report", summary_only ? "Summary Diff" : "Detail Diff");


  if (scale() > 1) {
    XmlElementWithText("warning", "(Omitting categories weighting less than 1%s)", current_scale());
  }

  // Overall diff
  {
    XmlParentElement("total");
    print_virtual_memory_diff(_current_baseline.total_reserved_memory(),
                              _current_baseline.total_committed_memory(),
                              _early_baseline.total_reserved_memory(),
                              _early_baseline.total_committed_memory());
  }

  // malloc diff
  const size_t early_malloced_bytes   =   _early_baseline.malloc_memory_snapshot()->total();
  const size_t early_count            =   _early_baseline.malloc_memory_snapshot()->total_count();
  const size_t current_malloced_bytes = _current_baseline.malloc_memory_snapshot()->total();
  const size_t current_count          = _current_baseline.malloc_memory_snapshot()->total_count();
  {
    XmlParentElement("malloc");
    print_malloc_diff(current_malloced_bytes,
                      current_count,
                      early_malloced_bytes,
                      early_count, mtNone);
  }
  // mmap diff
  {
    XmlParentElement("virtualMemoryDiff");
    print_virtual_memory_diff(_current_baseline.virtual_memory_snapshot()->total_reserved(),
                              _current_baseline.virtual_memory_snapshot()->total_committed(),
                              _early_baseline.virtual_memory_snapshot()->total_reserved(),
                              _early_baseline.virtual_memory_snapshot()->total_committed());
  }

  // Summary diff by memory tag
  for (int index = 0; index < mt_number_of_tags; index ++) {
    MemTag mem_tag = NMTUtil::index_to_tag(index);
    // thread stack is reported as part of thread category
    if (mem_tag == mtThreadStack) continue;

    {
      XmlParentElement("memoryTag");
      diff_summary_of_tag(mem_tag,
        _early_baseline.malloc_memory(mem_tag),
        _early_baseline.virtual_memory(mem_tag),
        _early_baseline.metaspace_stats(),
        _current_baseline.malloc_memory(mem_tag),
        _current_baseline.virtual_memory(mem_tag),
        _current_baseline.metaspace_stats());
    }
  }
  if (summary_only) {
    xs->tail("nativeMemoryTracking");
  }
}

void XmlMemSummaryDiffReporter::print_malloc_diff(size_t current_amount, size_t current_count,
    size_t early_amount, size_t early_count, MemTag mem_tag) const {
  xmlStream* xs = xml_output();

  XmlParentElement("mallocDiff");
  XmlElementWithText("amount", "%zu", amount_in_current_scale(current_amount));
  // Report type only if it is valid and not under "thread" category
  if (mem_tag != mtNone && mem_tag != mtThread) {
    XmlElementWithText("memoryTag", "%s", NMTUtil::tag_to_name(mem_tag));
  }

  XmlElementWithText("amountDiff", INT64_PLUS_FORMAT, diff_in_current_scale(current_amount, early_amount));
  XmlElementWithText("count", "%zu", current_count);
  XmlElementWithText("countDiff", " %+zd", counter_diff(current_count, early_count));
}

void XmlMemSummaryDiffReporter::print_arena_diff(size_t current_amount, size_t current_count,
  size_t early_amount, size_t early_count) const {
  xmlStream* xs = xml_output();
  XmlParentElement("arenaDiff");
  XmlElementWithText("amount", "%zu", amount_in_current_scale(current_amount));
  XmlElementWithText("amountDiff", INT64_PLUS_FORMAT, diff_in_current_scale(current_amount, early_amount));
  XmlElementWithText("count", "%zu", current_count);
  XmlElementWithText("countDiff", "%+zd", counter_diff(current_count, early_count));
}

void XmlMemSummaryDiffReporter::print_virtual_memory_diff(size_t current_reserved, size_t current_committed,
    size_t early_reserved, size_t early_committed) const {

  xmlStream* xs = xml_output();

  XmlParentElement("vmDiff");
  XmlElementWithText("reservedCurrent", "%zu", amount_in_current_scale(current_reserved));
  XmlElementWithText("reservedDiff", INT64_PLUS_FORMAT, diff_in_current_scale(current_reserved, early_reserved));
  XmlElementWithText("committedCurrent", "%zu", amount_in_current_scale(current_committed));
  XmlElementWithText("committedDiff", INT64_PLUS_FORMAT, diff_in_current_scale(current_committed, early_committed));
}


void XmlMemSummaryDiffReporter::diff_summary_of_tag(MemTag mem_tag,
  const MallocMemory* early_malloc, const VirtualMemory* early_vm,
  const MetaspaceCombinedStats& early_ms,
  const MallocMemory* current_malloc, const VirtualMemory* current_vm,
  const MetaspaceCombinedStats& current_ms) const {

  xmlStream* xs = xml_output();
  const char* scale = current_scale();
  constexpr int indent = 28;

  // Total reserved and committed memory in current baseline
  size_t current_reserved_amount  = reserved_total (current_malloc, current_vm);
  size_t current_committed_amount = committed_total(current_malloc, current_vm);

  // Total reserved and committed memory in early baseline
  size_t early_reserved_amount  = reserved_total(early_malloc, early_vm);
  size_t early_committed_amount = committed_total(early_malloc, early_vm);

  // Adjust virtual memory total
  if (mem_tag == mtThread) {
    const VirtualMemory* early_thread_stack_usage =
      _early_baseline.virtual_memory(mtThreadStack);
    const VirtualMemory* current_thread_stack_usage =
      _current_baseline.virtual_memory(mtThreadStack);

    early_reserved_amount  += early_thread_stack_usage->reserved();
    early_committed_amount += early_thread_stack_usage->committed();

    current_reserved_amount  += current_thread_stack_usage->reserved();
    current_committed_amount += current_thread_stack_usage->committed();
  } else if (mem_tag == mtNMT) {
    early_reserved_amount  += _early_baseline.malloc_tracking_overhead();
    early_committed_amount += _early_baseline.malloc_tracking_overhead();

    current_reserved_amount  += _current_baseline.malloc_tracking_overhead();
    current_committed_amount += _current_baseline.malloc_tracking_overhead();
  }

  if (amount_in_current_scale(current_reserved_amount) > 0 ||
      diff_in_current_scale(current_reserved_amount, early_reserved_amount) != 0) {

    // print summary line
    XmlElementWithText("name", "%s", NMTUtil::tag_to_name(mem_tag));
    print_virtual_memory_diff(current_reserved_amount, current_committed_amount,
      early_reserved_amount, early_committed_amount);

    // detail lines
    if (mem_tag == mtClass) {
      // report class count
      {
        XmlParentElement("classes");
        XmlElementWithText("count", "%zu", _current_baseline.class_count());
        XmlElementWithText("countDiff", "%+zd", counter_diff(_current_baseline.class_count(), _early_baseline.class_count()));
      }
      {
        XmlParentElement("instanceClasses");
        XmlElementWithText("count", "%zu", _current_baseline.instance_class_count());
        XmlElementWithText("countDiff", "%+zd", counter_diff(_current_baseline.instance_class_count(),
                                                  _early_baseline.instance_class_count()));
      }
      {
        XmlParentElement("arrayClasses");
        XmlElementWithText("count", "%zu", _current_baseline.array_class_count());
        XmlElementWithText("countDiff", "%+zd", counter_diff(_current_baseline.array_class_count(),
                                                    _early_baseline.array_class_count()));
      }

    } else if (mem_tag == mtThread) {
      {
        XmlParentElement("thread");
        XmlElementWithText("count", "%zu", _current_baseline.thread_count());
        XmlElementWithText("countDiff", "%+zd", counter_diff(_current_baseline.thread_count(),
                                                   _early_baseline.thread_count()));
      }
      {
        XmlParentElement("stackVirtualMemory");
        const VirtualMemory* current_thread_stack = _current_baseline.virtual_memory(mtThreadStack);
        const VirtualMemory* early_thread_stack   = _early_baseline.virtual_memory(mtThreadStack);

        print_virtual_memory_diff(current_thread_stack->reserved(),
                                  current_thread_stack->committed(),
                                  early_thread_stack->reserved(),
                                  early_thread_stack->committed());

      }
    }

    // Report malloc'd memory
    size_t current_malloc_amount = current_malloc->malloc_size();
    size_t early_malloc_amount   = early_malloc->malloc_size();
    if (amount_in_current_scale(current_malloc_amount) > 0 ||
        diff_in_current_scale(current_malloc_amount, early_malloc_amount) != 0) {
      XmlParentElement("mallocDiff");
      print_malloc_diff(current_malloc_amount, (mem_tag == mtChunk) ? 0 : current_malloc->malloc_count(),
        early_malloc_amount, early_malloc->malloc_count(), mtNone);
    }

    // Report virtual memory
    if (amount_in_current_scale(current_vm->reserved()) > 0 ||
        diff_in_current_scale(current_vm->reserved(), early_vm->reserved()) != 0) {
      XmlParentElement("mmapDiff");
      print_virtual_memory_diff(current_vm->reserved(), current_vm->committed(),
        early_vm->reserved(), early_vm->committed());
    }

    // Report arena memory
    if (amount_in_current_scale(current_malloc->arena_size()) > 0 ||
        diff_in_current_scale(current_malloc->arena_size(), early_malloc->arena_size()) != 0) {
      XmlParentElement("arenaDiff");
      print_arena_diff(current_malloc->arena_size(), current_malloc->arena_count(),
        early_malloc->arena_size(), early_malloc->arena_count());
    }

    // Report native memory tracking overhead
    if (mem_tag == mtNMT) {
      size_t current_tracking_overhead = amount_in_current_scale(_current_baseline.malloc_tracking_overhead());
      size_t early_tracking_overhead   = amount_in_current_scale(_early_baseline.malloc_tracking_overhead());
      int64_t overhead_diff = diff_in_current_scale(_current_baseline.malloc_tracking_overhead(),
                                                    _early_baseline.malloc_tracking_overhead());
      XmlParentElement("trackingOverhead");
      XmlElementWithText("amount", "%zu", amount_in_current_scale(_current_baseline.malloc_tracking_overhead()));
      XmlElementWithText("amountDiff", INT64_PLUS_FORMAT, overhead_diff);

    } else if (mem_tag == mtClass) {
      XmlParentElement("metaspaceDiff");
      print_metaspace_diff(current_ms, early_ms);
    }
  }
}

void XmlMemSummaryDiffReporter::print_metaspace_diff(const MetaspaceCombinedStats& current_ms,
                                                  const MetaspaceCombinedStats& early_ms) const {
  print_metaspace_diff("metadata", current_ms.non_class_space_stats(), early_ms.non_class_space_stats());
  if (Metaspace::using_class_space()) {
    print_metaspace_diff("classSpace", current_ms.class_space_stats(), early_ms.class_space_stats());
  }
}

void XmlMemSummaryDiffReporter::print_metaspace_diff(const char* header,
                                                  const MetaspaceStats& current_stats,
                                                  const MetaspaceStats& early_stats) const {

  xmlStream* xs = xml_output();
  const char* scale = current_scale();

  XmlParentElement(header);
  print_virtual_memory_diff(current_stats.reserved(),
                            current_stats.committed(),
                            early_stats.reserved(),
                            early_stats.committed());

  int64_t diff_used = diff_in_current_scale(current_stats.used(),
                                            early_stats.used());

  size_t current_waste = current_stats.committed() - current_stats.used();
  size_t early_waste = early_stats.committed() - early_stats.used();
  int64_t diff_waste = diff_in_current_scale(current_waste, early_waste);

  // Diff used
  XmlElementWithText("used", "%zu", amount_in_current_scale(current_stats.used()));
  XmlElementWithText("usedDiff", INT64_PLUS_FORMAT, diff_used);

  // Diff waste
  const float waste_percentage = current_stats.committed() == 0 ? 0.0f :
                                   ((float)current_waste * 100.0f) / (float)current_stats.committed();
  XmlElementWithText("waste", "%zu", amount_in_current_scale(current_waste));
  XmlElementWithText("wastePercentage", "%2.2f", waste_percentage);
  XmlElementWithText("wasteDiff", INT64_PLUS_FORMAT, diff_waste);
}

size_t XmlMemSummaryDiffReporter::reserved_total(const MallocMemory* malloc, const VirtualMemory* vm) {
  return malloc->malloc_size() + malloc->arena_size() + vm->reserved();
}

size_t XmlMemSummaryDiffReporter::committed_total(const MallocMemory* malloc, const VirtualMemory* vm) {
  return malloc->malloc_size() + malloc->arena_size() + vm->committed();
}

void XmlMemDetailDiffReporter::report_diff() const {
  XmlMemSummaryDiffReporter::report_diff(/*summary only*/false);
  diff_malloc_sites();
  diff_virtual_memory_sites();
  xml_output()->tail("nativeMemoryTracking");
}

void XmlMemDetailDiffReporter::diff_malloc_sites() const {
  MallocSiteIterator early_itr = _early_baseline.malloc_sites(MemBaseline::by_site_and_tag);
  MallocSiteIterator current_itr = _current_baseline.malloc_sites(MemBaseline::by_site_and_tag);

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

void XmlMemDetailDiffReporter::diff_virtual_memory_sites() const {
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
      } else if (early_site->mem_tag() != current_site->mem_tag()) {
        // This site was originally allocated with one memory tag, then released,
        // then re-allocated at the same site (as far as we can tell) with a different memory tag.
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


void XmlMemDetailDiffReporter::new_malloc_site(const MallocSite* malloc_site) const {
  diff_malloc_site(malloc_site->call_stack(), malloc_site->size(), malloc_site->count(),
    0, 0, malloc_site->mem_tag());
}

void XmlMemDetailDiffReporter::old_malloc_site(const MallocSite* malloc_site) const {
  diff_malloc_site(malloc_site->call_stack(), 0, 0, malloc_site->size(),
    malloc_site->count(), malloc_site->mem_tag());
}

void XmlMemDetailDiffReporter::diff_malloc_site(const MallocSite* early,
  const MallocSite* current)  const {
  if (early->mem_tag() != current->mem_tag()) {
    // If malloc site type changed, treat it as deallocation of old type and
    // allocation of new type.
    old_malloc_site(early);
    new_malloc_site(current);
  } else {
    diff_malloc_site(current->call_stack(), current->size(), current->count(),
      early->size(), early->count(), early->mem_tag());
  }
}

void XmlMemDetailDiffReporter::diff_malloc_site(const NativeCallStack* stack, size_t current_size,
  size_t current_count, size_t early_size, size_t early_count, MemTag mem_tag) const {
  outputStream* out = xml_output();

  assert(stack != nullptr, "null stack");

  if (diff_in_current_scale(current_size, early_size) == 0) {
      return;
  }
  XmlParentElement("mallocSiteDiff");
  {
    XmlStackElement;
    _stackprinter.print_stack(stack);
  }
  {
    XmlParentElement("mallocDiff");
    print_malloc_diff(current_size, current_count, early_size, early_count, mem_tag);
  }
}


void XmlMemDetailDiffReporter::new_virtual_memory_site(const VirtualMemoryAllocationSite* site) const {
  diff_virtual_memory_site(site->call_stack(), site->reserved(), site->committed(), 0, 0, site->mem_tag());
}

void XmlMemDetailDiffReporter::old_virtual_memory_site(const VirtualMemoryAllocationSite* site) const {
  diff_virtual_memory_site(site->call_stack(), 0, 0, site->reserved(), site->committed(), site->mem_tag());
}

void XmlMemDetailDiffReporter::diff_virtual_memory_site(const VirtualMemoryAllocationSite* early,
  const VirtualMemoryAllocationSite* current) const {
  diff_virtual_memory_site(current->call_stack(), current->reserved(), current->committed(),
    early->reserved(), early->committed(), current->mem_tag());
}

void XmlMemDetailDiffReporter::diff_virtual_memory_site(const NativeCallStack* stack, size_t current_reserved,
  size_t current_committed, size_t early_reserved, size_t early_committed, MemTag mem_tag) const  {
  xmlStream* xs = xml_output();

  // no change
  if (diff_in_current_scale(current_reserved, early_reserved) == 0 &&
      diff_in_current_scale(current_committed, early_committed) == 0) {
    return;
  }
  XmlParentElement("virtualMemorySiteDiff");
  {
    XmlStackElement;
    _stackprinter.print_stack(stack);
  }
  {
    XmlParentElement("mmapDiff");
    print_virtual_memory_diff(current_reserved, current_committed, early_reserved, early_committed);
    if (mem_tag != mtNone) {
      XmlElementWithText("memoryTag", "%s", NMTUtil::tag_to_name(mem_tag));
    }
  }
}

/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, Red Hat, Inc. and/or its affiliates.
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

#include "logging/logAsyncWriter.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "memory/allocation.hpp"
#include "memory/universe.hpp"
#include "runtime/nonJavaThread.hpp"
#include "runtime/osThread.hpp"
#include "runtime/thread.hpp"
#include "runtime/threadSMR.hpp"
#include "runtime/vmThread.hpp"
#include "services/memMapPrinter.hpp"
#include "nmt/memTracker.hpp"
#include "nmt/virtualMemoryTracker.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"

// Note: throughout this code we will use the term "VMA" for OS system level memory mapping

/// NMT mechanics

// Short, clear, descriptive names for all possible markers. Note that we only expect to see
// those that have been used with mmap. Others I leave at nullptr.
#define NMTFLAGS_DO(f) \
  /* flag, short, description */ \
  f(mtGCCardSet,      "CARDTBL", "GC Card table") \
  f(mtClassShared,    "CDS", "CDS archives") \
  f(mtClass,          "CLASS", "Class Space") \
  f(mtCode,           "CODE", "Code Heap") \
  f(mtGC,             "GC", "GC support data (e.g. bitmaps)") \
  f(mtInternal,       "INTERN", "Internal") \
  f(mtJavaHeap,       "JAVAHEAP", "Java Heap") \
  f(mtOther,          "JDK", "allocated by JDK libraries other than VM") \
  f(mtMetaspace,      "META", "Metaspace nodes (non-class)") \
  f(mtSafepoint,      "POLL", "Polling pages") \
  f(mtThreadStack,    "STACK", "(known) Thread Stack")
  //end

static const char* get_shortname_for_nmt_flag(MEMFLAGS f) {
#define DO(flag, shortname, text) if (flag == f) return shortname;
  NMTFLAGS_DO(DO)
#undef DO
  return NMTUtil::flag_to_enum_name(f);
}

class MemFlagBitmap {
  uint32_t _v;
  STATIC_ASSERT(sizeof(_v) * BitsPerByte >= mt_number_of_types);

public:
  MemFlagBitmap(uint32_t v = 0) : _v(v) {}
  MemFlagBitmap(const MemFlagBitmap& o) : _v(o._v) {}

  uint32_t raw_value() const { return _v; }

  void set_flag(MEMFLAGS f) {
    const int bitno = (int)f;
    _v |= nth_bit(bitno);
  }

  bool has_flag(MEMFLAGS f) const {
    const int bitno = (int)f;
    return _v & nth_bit(bitno);
  }

  bool has_any() const { return _v > 0; }

};

/// NMT virtual memory

struct NMTRegionSearchWalker : public VirtualMemoryWalker {

  const void* const _from;
  const void* const _to;
  // Number of round region associations by type
  MemFlagBitmap _found;
  enum class MatchType {
    exact,        // VMA is the same size as NMT region
    vma_superset, // VMA is a superset of the region
    nmt_superset, // NMT region is a superset of VMA
    unclear       // unclear match
  };
  MatchType _match_type;

  NMTRegionSearchWalker(const void* from, const void* to) :
    _from(from), _to(to), _found(), _match_type(MatchType::unclear) {
  }

  bool do_allocation_site(const ReservedMemoryRegion* rgn) {

    // Count if we have an intersection.
    // Note:
    // A) A NMT virtual memory region may contain committed and uncommitted regions, therefore
    //    it may span multiple VMAs on system level. That happens frequently for memory regions
    //    that are committed on demand (e.g. Metaspace, heap etc).
    // B) A system-level VMA may be the result of a folding operation by the kernel. The kernel
    //    folds adjacent memory mappings that share the same attributes into one.
    // Therefore there is no 1:1 relationship between VMA and NMT region. It is m:n. We deal
    // with (B) by returning a *set* of NMT flags associated with a single VMA. There is no need
    // to deal with (A) explicitly; it just means that adjacent VMAs show the same NMT flag. For
    // instance, one will always see multiple adjacent VMAs showing up as java heap or class space.
    address intersection_from = MAX2(rgn->base(), (address)_from);
    address intersection_to = MIN2(rgn->end(), (address)_to);
    if (intersection_from < intersection_to) {
      // we intersect
      const MEMFLAGS flag = rgn->flag();
      _found.set_flag(flag);
      if (_match_type == MatchType::unclear) {
        if (rgn->base() == (address)_from && rgn->end() == (address)_to) {
          _match_type = MatchType::exact;
        } else if (rgn->base() <= (address)_from && rgn->end() >= (address)_to) {
          // this will most often happen, since JVM regions are typically committed on demand,
          // leaving us with multiple matching VMAs at the system that differ by protectedness.
          _match_type = MatchType::nmt_superset;
        } else if ((address)_from <= rgn->base() && (address)_to >= rgn->end()) {
          // This can happen if mappings from different JVM subsystems are mapped adjacent
          // of each other and share the same properties; the kernel will fold them into
          // one OS-side VMA.
          _match_type = MatchType::vma_superset;
        }
      }
    }
    return true;
  }
};

// Given a VMA [from, to) and a thread, check if vma intersects with thread stack
static bool vma_touches_thread_stack(const void* from, const void* to, const Thread* t) {
  // Java thread stacks (and sometimes also other threads) have guard pages. Therefore they typically occupy
  // at least two distinct neighboring VMAs. Therefore we typically have a 1:n relationshipt between thread
  // stack and vma.
  // Very rarely however is a VMA backing a thread stack folded together with another adjacent VMA by the
  // kernel. That can happen, e.g., for non-java threads that don't have guard pages.
  // Therefore we go for the simplest way here and check for intersection between VMA and thread stack.
  const address min = MAX2((address)from, t->stack_end());
  const address max = MIN2((address)to, t->stack_base());
  return min < max;
}

struct GCThreadClosure : public ThreadClosure {
  Thread* _t;
  const void* const _from;
  const void* const _to;
public:
  GCThreadClosure(const void* from, const void* to) : _t(nullptr), _from(from), _to(to) {}
  void do_thread(Thread* thread) override {
    if (_t == nullptr && thread != nullptr && vma_touches_thread_stack(_from, _to, thread)) {
      _t = thread;
    }
  }
};

static uintx safely_get_thread_id(const Thread* t) {
  const OSThread* osth = t->osthread();
  uintx tid = 0;
  if (osth != nullptr) {
    return (uintx)(osth->thread_id());
  }
  return 0;
}

// Given a region [from, to) that is supposed to represent a thread stack,
static void print_thread_details_for_supposed_stack_address(const void* from, const void* to, outputStream* st) {

  for (JavaThreadIteratorWithHandle jtiwh; JavaThread* t = jtiwh.next(); ) {
    const size_t len = pointer_delta(to, from, 1);
    if (vma_touches_thread_stack(from, to, t)) {
      st->print("(" UINTX_FORMAT " \"%s\")", safely_get_thread_id(t), t->name());
      return;
    }
  }

#define HANDLE_THREAD(T)                                                        \
  if (T != nullptr && vma_touches_thread_stack(from, to, T)) {                                   \
    st->print("(" UINTX_FORMAT " \"%s\")", safely_get_thread_id(T), ((const Thread*)T)->name()); \
    return;                                                                     \
  }

  HANDLE_THREAD(VMThread::vm_thread());
  HANDLE_THREAD(WatcherThread::watcher_thread());
  HANDLE_THREAD(AsyncLogWriter::instance());

  if (Universe::heap() != nullptr) {
    GCThreadClosure cl(from, to);
    Universe::heap()->gc_threads_do(&cl);
    HANDLE_THREAD(cl._t);
  }
#undef HANDLE_THREAD
}

static bool ask_nmt_about(const void* from, const void* to, outputStream* st) {
  if (!MemTracker::enabled()) {
    return false;
  }
  NMTRegionSearchWalker walker(from, to);
  VirtualMemoryTracker::walk_virtual_memory(&walker);
  if (walker._found.has_any()) {
    // The address range we may be asked about may be the result of VMA folding:
    // Two adjacent memory mappings that happen to have the same property will be folded
    // by the kernel into a single VMA.
    // Since that can seriously confuse readers of this mapping output, we try to find
    // out if the mapping is used for multiple purposes. We mark those VMAs with "(*)"
    // and print all NMT region markings.
    for (int i = 0; i < mt_number_of_types; i++) {
      const MEMFLAGS flag = (MEMFLAGS)i;
      if (walker._found.has_flag(flag)) {
        st->print("%s", get_shortname_for_nmt_flag(flag));
        if (flag == mtThreadStack) {
          print_thread_details_for_supposed_stack_address(from, to, st);
        }
        st->print(" ");
      }
    }
    if (walker._match_type == NMTRegionSearchWalker::MatchType::vma_superset) {
      st->print(" (*)");
    }
    return true;
  }

  return false;
}

static void print_legend(outputStream* st) {
#define DO(flag, shortname, text) st->print_cr("%10s    %s", shortname, text);
  NMTFLAGS_DO(DO)
#undef DO
}

MappingPrintClosure::MappingPrintClosure(outputStream* st, bool human_readable, jlong timeout_at) :
    _out(st), _humam_readable(human_readable), _timeout_at(timeout_at), _total_count(0), _total_vsize(0) {}

bool MappingPrintClosure::do_it(const MappingPrintInformation* info) {
  _total_count++;
  _out->print(PTR_FORMAT " - " PTR_FORMAT " ", p2i(info->from()), p2i(info->to()));
  const size_t size = pointer_delta(info->to(), info->from(), 1);
  _total_vsize += size;
  if (_humam_readable) {
    _out->print(PROPERFMT " ", PROPERFMTARGS(size));
  } else {
    _out->print("%11zu", size);
  }
  assert(info->from() <= info->to(), "Invalid VMA");
  _out->fill_to(53);
  info->print_OS_specific_details_heading(_out);
  _out->fill_to(70);
  ask_nmt_about(info->from(), info->to(), _out);
  _out->fill_to(100);
  info->print_OS_specific_details_trailing(_out);
  _out->cr();

  return _timeout_at > os::javaTimeNanos(); // false if timeout
}

void MemMapPrinter::print_all_mappings(outputStream* st, bool human_readable) {

  st->print_cr("Memory mappings:");
  if (!MemTracker::enabled()) {
    st->print_cr(" (For full functionality, please enable Native Memory Tracking)");
  }
  st->cr();
  print_legend(st);
  st->print_cr("(*) - Mapping contains data from multiple regions");
  st->cr();
  pd_print_header(st);
  // Under rare circumstances the process memory map may be insanely large and/or fragmented. We cap
  // the absolute runtime of printing to blocking other VM operations too long.
  const jlong timeout_at = os::javaTimeNanos() +
                           ((jlong)(SafepointTimeoutDelay * NANOSECS_PER_MILLISEC) / 2);
  MappingPrintClosure closure(st, human_readable, timeout_at);
  bool ok = pd_iterate_all_mappings(closure);
  if (!ok) {
    st->print_cr("Aborted after printing " UINTX_FORMAT " mappings, took too long.", closure.total_count());
  } else {
    st->print_cr("Total: " UINTX_FORMAT " mappings with a total vsize of %zu (" PROPERFMT ")",
                 closure.total_count(), closure.total_vsize(), PROPERFMTARGS(closure.total_vsize()));
  }
}

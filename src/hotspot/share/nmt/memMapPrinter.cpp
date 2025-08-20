/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2024, Red Hat, Inc. and/or its affiliates.
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

#if defined(LINUX) || defined(_WIN64) || defined(__APPLE__)

#include "gc/shared/collectedHeap.hpp"
#include "logging/logAsyncWriter.hpp"
#include "memory/allocation.hpp"
#include "memory/universe.hpp"
#include "memory/resourceArea.hpp"
#include "nmt/memTag.hpp"
#include "nmt/memTagBitmap.hpp"
#include "nmt/memMapPrinter.hpp"
#include "nmt/memTracker.hpp"
#include "nmt/virtualMemoryTracker.hpp"
#include "runtime/nonJavaThread.hpp"
#include "runtime/osThread.hpp"
#include "runtime/thread.hpp"
#include "runtime/threadSMR.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"
#include "utilities/permitForbiddenFunctions.hpp"

// Note: throughout this code we will use the term "VMA" for OS system level memory mapping

/// NMT mechanics

// Short, clear, descriptive names for all possible markers. Note that we only expect to see
// those that have been used with mmap. Flags left out are printed with their nmt tags name.
#define NMT_FLAGS_DO(f) \
  /* mem_tag, short, description */ \
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
  f(mtThreadStack,    "STACK", "(known) Thread Stack") \
  f(mtTest,           "TEST", "JVM internal test mappings")
  //end

static const char* get_shortname_for_mem_tag(MemTag mem_tag) {
#define DO(t, shortname, text) if (t == mem_tag) return shortname;
  NMT_FLAGS_DO(DO)
#undef DO
  return NMTUtil::tag_to_enum_name(mem_tag);
}

/// NMT virtual memory

static bool range_intersects(const void* from1, const void* to1, const void* from2, const void* to2) {
  return MAX2(from1, from2) < MIN2(to1, to2);
}

// A Cache that correlates range with MemTag, optimized to be iterated quickly
// (cache friendly).
class CachedNMTInformation : public VirtualMemoryWalker {
  struct Range { const void* from; const void* to; };
  // We keep ranges apart from flags since that prevents the padding a combined
  // structure would have, and it allows for faster iteration of ranges since more
  // of them fit into a cache line.
  Range* _ranges;
  MemTag* _mem_tags;
  size_t _count, _capacity;
  mutable size_t _last;

public:
  CachedNMTInformation() : _ranges(nullptr), _mem_tags(nullptr),
                           _count(0), _capacity(0), _last(0) {}

  ~CachedNMTInformation() {
    permit_forbidden_function::free(_ranges);
    permit_forbidden_function::free(_mem_tags);
  }

  bool add(const void* from, const void* to, MemTag mem_tag) {
    // We rely on NMT regions being sorted by base
    assert(_count == 0 || (from >= _ranges[_count - 1].to), "NMT regions unordered?");
    // we can just fold two regions if they are adjacent and have the same mem_tag.
    if (_count > 0 && from == _ranges[_count - 1].to && mem_tag == _mem_tags[_count - 1]) {
      _ranges[_count - 1].to = to;
      return true;
    }
    if (_count == _capacity) {
      // Enlarge if needed
      const size_t new_capacity = MAX2((size_t)4096, 2 * _capacity);
      // Unfortunately, we need to allocate manually, raw, since we must prevent NMT deadlocks.
      _ranges = (Range*)permit_forbidden_function::realloc(_ranges, new_capacity * sizeof(Range));
      _mem_tags = (MemTag*)permit_forbidden_function::realloc(_mem_tags, new_capacity * sizeof(MemTag));
      if (_ranges == nullptr || _mem_tags == nullptr) {
        // In case of OOM lets make no fuss. Just return.
        return false;
      }
      _capacity = new_capacity;
    }
    assert(_capacity > _count, "Sanity");
    _ranges[_count] = Range { from, to };
    _mem_tags[_count] = mem_tag;
    _count++;
    return true;
  }

  // Given a vma [from, to), find all regions that intersect with this vma and
  // return their collective flags.
  MemTagBitmap lookup(const void* from, const void* to) const {
    assert(from <= to, "Sanity");
    // We optimize for sequential lookups. Since this class is used when a list
    // of OS mappings is scanned (VirtualQuery, /proc/pid/maps), and these lists
    // are usually sorted in order of addresses, ascending.
    if (to <= _ranges[_last].from) {
      // the range is to the right of the given section, we need to re-start the search
      _last = 0;
    }
    MemTagBitmap bm;
    for(uintx i = _last; i < _count; i++) {
      if (range_intersects(from, to, _ranges[i].from, _ranges[i].to)) {
        bm.set_tag(_mem_tags[i]);
      } else if (to <= _ranges[i].from) {
        _last = i;
        break;
      }
    }
    return bm;
  }

  bool do_allocation_site(const ReservedMemoryRegion* rgn) override {
    // Cancel iteration if we run out of memory (add returns false);
    return add(rgn->base(), rgn->end(), rgn->mem_tag());
  }

  // Iterate all NMT virtual memory regions and fill this cache.
  bool fill_from_nmt() {
    return VirtualMemoryTracker::Instance::walk_virtual_memory(this);
  }
};

/////// Thread information //////////////////////////

// Given a VMA [from, to) and a thread, check if vma intersects with thread stack
static bool vma_touches_thread_stack(const void* from, const void* to, const Thread* t) {
  // Java thread stacks (and sometimes also other threads) have guard pages. Therefore they typically occupy
  // at least two distinct neighboring VMAs. Therefore we typically have a 1:n relationshipt between thread
  // stack and vma.
  // Very rarely however is a VMA backing a thread stack folded together with another adjacent VMA by the
  // kernel. That can happen, e.g., for non-java threads that don't have guard pages.
  // Therefore we go for the simplest way here and check for intersection between VMA and thread stack.
  // Note it is possible to encounter a brand new thread that has not yet initialized its stack fields.
  return t->stack_base_or_null() != nullptr && range_intersects(from, to, (const void*)t->stack_end(), (const void*)t->stack_base());
}

struct GCThreadClosure : public ThreadClosure {
  bool _found;
  uintx _tid;
  const void* const _from;
  const void* const _to;
  GCThreadClosure(const void* from, const void* to) : _found(false), _tid(0), _from(from), _to(to) {}
  void do_thread(Thread* t) override {
    if (_tid == 0 && t != nullptr && vma_touches_thread_stack(_from, _to, t)) {
      _found = true;
      _tid = t->osthread()->thread_id();
      // lemme stooop! No way to signal stop :(
    }
  }
};

static void print_thread_details(uintx thread_id, const char* name, outputStream* st) {
  // avoid commas and spaces in output to ease post-processing via awk
  char tmp[64];
  stringStream ss(tmp, sizeof(tmp));
  ss.print(":%zu-%s", (uintx)thread_id, name);
  for (int i = 0; tmp[i] != '\0'; i++) {
    if (!isalnum(tmp[i])) {
      tmp[i] = '-';
    }
  }
  st->print_raw(tmp);
}

// Given a region [from, to), if it intersects a known thread stack, print detail infos about that thread.
static void print_thread_details_for_supposed_stack_address(const void* from, const void* to, outputStream* st) {

  ResourceMark rm;

#define HANDLE_THREAD(T)                                                        \
  if (T != nullptr && vma_touches_thread_stack(from, to, T)) {                  \
    print_thread_details((uintx)(T->osthread()->thread_id()), T->name(), st);   \
    return;                                                                     \
  }
  for (JavaThreadIteratorWithHandle jtiwh; JavaThread* t = jtiwh.next(); ) {
    HANDLE_THREAD(t);
  }
  HANDLE_THREAD(VMThread::vm_thread());
  HANDLE_THREAD(WatcherThread::watcher_thread());
  HANDLE_THREAD(AsyncLogWriter::instance());
#undef HANDLE_THREAD

  if (Universe::heap() != nullptr) {
    GCThreadClosure cl(from, to);
    Universe::heap()->gc_threads_do(&cl);
    if (cl._found) {
      print_thread_details(cl._tid, "GC Thread", st);
    }
  }
}

///////////////

MappingPrintSession::MappingPrintSession(outputStream* st, const CachedNMTInformation& nmt_info) :
    _out(st), _nmt_info(nmt_info)
{}

void MappingPrintSession::print_nmt_flag_legend() const {
#define DO(flag, shortname, text) _out->print_cr("%10s: %s", shortname, text);
  NMT_FLAGS_DO(DO)
#undef DO
}

bool MappingPrintSession::print_nmt_info_for_region(const void* vma_from, const void* vma_to) const {
  int num_printed = 0;
  // print NMT information, if available
  if (MemTracker::enabled()) {
    // Correlate vma region (from, to) with NMT region(s) we collected previously.
    const MemTagBitmap flags = _nmt_info.lookup(vma_from, vma_to);
    if (flags.has_any()) {
      for (int i = 0; i < mt_number_of_tags; i++) {
        const MemTag mem_tag = (MemTag)i;
        if (flags.has_tag(mem_tag)) {
          if (num_printed > 0) {
            _out->put(',');
          }
          _out->print("%s", get_shortname_for_mem_tag(mem_tag));
          if (mem_tag == mtThreadStack) {
            print_thread_details_for_supposed_stack_address(vma_from, vma_to, _out);
          }
          num_printed++;
        }
      }
    }
  }
  return num_printed > 0;
}

void MemMapPrinter::print_all_mappings(outputStream* st) {
  CachedNMTInformation nmt_info;
  st->print_cr("Memory mappings:");
  // Prepare NMT info cache. But only do so if we print individual mappings,
  // otherwise, we won't need it and can save that work.
  if (MemTracker::enabled()) {
    nmt_info.fill_from_nmt();
  } else {
    st->print_cr("NMT is disabled. VM info not available.");
  }
  MappingPrintSession session(st, nmt_info);
  pd_print_all_mappings(session);
}

#endif // LINUX

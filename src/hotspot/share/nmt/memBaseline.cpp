/*
 * Copyright (c) 2012, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/classLoaderDataGraph.inline.hpp"
#include "memory/allocation.hpp"
#include "memory/metaspaceUtils.hpp"
#include "nmt/memBaseline.hpp"
#include "nmt/memTracker.hpp"
#include "nmt/regionsTree.inline.hpp"
#include "nmt/virtualMemoryTracker.hpp"
#include "utilities/quickSort.hpp"

/*
 * Sizes are sorted in descenting order for reporting
 */
int compare_malloc_size(const MallocSite& s1, const MallocSite& s2) {
  if (s1.size() == s2.size()) {
    return 0;
  } else if (s1.size() > s2.size()) {
    return -1;
  } else {
    return 1;
  }
}

int compare_virtual_memory_size(const VirtualMemoryAllocationSite& s1,
  const VirtualMemoryAllocationSite& s2) {
  if (s1.reserved() == s2.reserved()) {
    return 0;
  } else if (s1.reserved() > s2.reserved()) {
    return -1;
  } else {
    return 1;
  }
}

// Sort into allocation site addresses order for baseline comparison
int compare_malloc_site(const MallocSite& s1, const MallocSite& s2) {
  return s1.call_stack()->compare(*s2.call_stack());
}

// Sort into allocation site addresses and memory tag order for baseline comparison
int compare_malloc_site_and_tag(const MallocSite& s1, const MallocSite& s2) {
  int res = compare_malloc_site(s1, s2);
  if (res == 0) {
    res = (int)(NMTUtil::tag_to_index(s1.mem_tag()) - NMTUtil::tag_to_index(s2.mem_tag()));
  }

  return res;
}

int compare_virtual_memory_site(const VirtualMemoryAllocationSite& s1,
  const VirtualMemoryAllocationSite& s2) {
  return s1.call_stack()->compare(*s2.call_stack());
}


class MallocAllocationSiteWalker : public MallocSiteWalker {
private:
  MallocSite* _malloc_sites;
  int _length;
  int _index;
  // Entries in MallocSiteTable with size = 0 and count = 0,
  // when the malloc site is not longer there.
public:
  MallocAllocationSiteWalker()
  : MallocSiteWalker(), _malloc_sites(NEW_C_HEAP_ARRAY_RETURN_NULL(MallocSite, 16, mtNMT)),
    _length(_malloc_sites == nullptr ? 0 : 16), _index(0) {}

  MallocSite* malloc_sites(int* len) {
    *len = _index;
    return _malloc_sites;
  }

  bool do_malloc_site(const MallocSite* site) override {
    if (site->size() > 0) {
      if (_index >= _length) {
        _malloc_sites = REALLOC_C_HEAP_ARRAY(_malloc_sites, _length*2, mtNMT);
        if (_malloc_sites == nullptr) {
          return false;  // OOM
        }
        _length = _length * 2;
      }
      new (&_malloc_sites[_index]) MallocSite(*site);
      _index++;
      return true;
    } else {
      // Ignore empty sites.
      return true;
    }
  }
};

void MemBaseline::baseline_summary() {
  MallocMemorySummary::snapshot(&_malloc_memory_snapshot);
  {
    MemTracker::NmtVirtualMemoryLocker nvml;
    VirtualMemorySummary::snapshot(&_virtual_memory_snapshot);
    MemoryFileTracker::Instance::summary_snapshot(&_virtual_memory_snapshot);
  }

  _metaspace_stats = MetaspaceUtils::get_combined_statistics();
}

bool MemBaseline::baseline_allocation_sites() {
  // Malloc allocation sites
  MallocAllocationSiteWalker malloc_walker;
  if (!MallocSiteTable::walk_malloc_site(&malloc_walker)) {
    return false;
  }
  _malloc_sites = malloc_walker.malloc_sites(&_malloc_sites_length);

  // The malloc sites are collected in size order
  _malloc_sites_order = by_size;

  assert(_vma_allocations == nullptr, "must");
  {
    MemTracker::NmtVirtualMemoryLocker locker;
    _vma_allocations = new (mtNMT, std::nothrow) RegionsTree(*VirtualMemoryTracker::Instance::tree());
    if (_vma_allocations == nullptr)  {
      return false;
    }
  }

  if (!aggregate_virtual_memory_allocation_sites()) {
    return false;
  }
  // Virtual memory allocation sites are aggregrated in call stack order
  _virtual_memory_sites_order = by_address;

  return true;
}

void MemBaseline::baseline(bool summaryOnly) {
  reset();

  _instance_class_count = ClassLoaderDataGraph::num_instance_classes();
  _array_class_count = ClassLoaderDataGraph::num_array_classes();
  _thread_count = ThreadStackTracker::thread_count();
  baseline_summary();

  _baseline_type = Summary_baselined;

  // baseline details
  if (!summaryOnly &&
      MemTracker::tracking_level() == NMT_detail) {
    baseline_allocation_sites();
    _baseline_type = Detail_baselined;
  }
}

int compare_allocation_site(const VirtualMemoryAllocationSite& s1,
  const VirtualMemoryAllocationSite& s2) {
  return s1.call_stack()->compare(*s2.call_stack());
}

bool MemBaseline::aggregate_virtual_memory_allocation_sites() {
  auto stack = [](const VirtualMemoryAllocationSite& vmas) -> const NativeCallStack& { return *vmas.call_stack(); };
  auto hash = [](const NativeCallStack& ncs) { return ncs.calculate_hash(); };
  auto equals = [](const NativeCallStack& a, const NativeCallStack& b) { return a.equals(b); };
  using VirtualMemorySiteTable = OpenAddressedHashTable<VirtualMemoryAllocationSite,
                                                        decltype(stack),
                                                        decltype(hash),
                                                        decltype(equals),
                                                        mtNMT, AllocFailStrategy::RETURN_NULL,
                                                        75>;
  VirtualMemorySiteTable vht(stack, hash, equals);
  VirtualMemoryAllocationSite* site;
  bool failed_oom = false;
  _vma_allocations->visit_reserved_regions([&](VirtualMemoryRegion& rgn) {
    VirtualMemoryAllocationSite tmp(*rgn.reserved_call_stack(), rgn.mem_tag());
    bool found;
    site = vht.put_if_absent(tmp, &found);
    if (site == nullptr) {
      failed_oom = true;
      return false;
    }
    site->reserve_memory(rgn.size());
    site->commit_memory(_vma_allocations->committed_size(rgn));
    return true;
  });

  if (failed_oom) {
    return false;
  }
  _virtual_memory_sites = vht.detach(&_virtual_memory_sites_length);
  return true;
}

template<typename T, auto Cmp>
static void qsort_helper(T* array, int length) {
  ::qsort(array, length, sizeof(T),
          [](const void* a, const void* b) -> int {
            return Cmp(*static_cast<const T*>(a),
                       *static_cast<const T*>(b));
          });
}
template<typename T>
using Qsorter = void (*)(T*, int);


void MemBaseline::sort_malloc_sites(SortingOrder order) {
  assert(_malloc_sites_length != 0, "Not detail baseline");
  Qsorter<MallocSite> sorter_by_sortingorder[SortingOrder::_count] = {
    nullptr,
    &qsort_helper<MallocSite, &compare_malloc_size>,
    &qsort_helper<MallocSite, &compare_malloc_site>,
    &qsort_helper<MallocSite, &compare_malloc_site_and_tag>
  };

  auto sort = sorter_by_sortingorder[order];
  guarantee(sort != nullptr, "An invalid sorting method was selected");
  if (_malloc_sites_order != order) {
    sort(_malloc_sites, _malloc_sites_length);
  }
}

void MemBaseline::sort_virtual_memory_sites(SortingOrder order) {
  assert(_virtual_memory_sites_length != 0, "Not detail baseline");
  Qsorter<VirtualMemoryAllocationSite> sorter_by_sortingorder[SortingOrder::_count] = {
    nullptr,
    &qsort_helper<VirtualMemoryAllocationSite, &compare_virtual_memory_size>,
    &qsort_helper<VirtualMemoryAllocationSite, &compare_virtual_memory_site>,
    nullptr
  };

  auto sort = sorter_by_sortingorder[order];
  guarantee(sort != nullptr, "An invalid sorting method was selected");
  if (_virtual_memory_sites_order != order) {
    sort(_virtual_memory_sites, _virtual_memory_sites_length);
  }
}

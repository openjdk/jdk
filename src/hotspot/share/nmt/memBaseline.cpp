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

#include "classfile/classLoaderDataGraph.inline.hpp"
#include "memory/allocation.hpp"
#include "memory/metaspaceUtils.hpp"
#include "nmt/memBaseline.hpp"
#include "nmt/memTracker.hpp"
#include "nmt/regionsTree.inline.hpp"

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

/*
 * Walker to walk malloc allocation site table
 */
class MallocAllocationSiteWalker : public MallocSiteWalker {
 private:
  SortedLinkedList<MallocSite, compare_malloc_size> _malloc_sites;

  // Entries in MallocSiteTable with size = 0 and count = 0,
  // when the malloc site is not longer there.
 public:

  LinkedList<MallocSite>* malloc_sites() {
    return &_malloc_sites;
  }

  bool do_malloc_site(const MallocSite* site) {
    if (site->size() > 0) {
      if (_malloc_sites.add(*site) != nullptr) {
        return true;
      } else {
        return false;  // OOM
      }
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

  _malloc_sites.move(malloc_walker.malloc_sites());
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
  SortedLinkedList<VirtualMemoryAllocationSite, compare_allocation_site> allocation_sites;

  VirtualMemoryAllocationSite* site;
  bool failed_oom = false;
  _vma_allocations->visit_reserved_regions([&](ReservedMemoryRegion& rgn) {
    VirtualMemoryAllocationSite tmp(*rgn.call_stack(), rgn.mem_tag());
    site = allocation_sites.find(tmp);
    if (site == nullptr) {
      LinkedListNode<VirtualMemoryAllocationSite>* node =
        allocation_sites.add(tmp);
      if (node == nullptr) {
        failed_oom = true;
        return false;
      }
      site = node->data();
    }
    site->reserve_memory(rgn.size());

    site->commit_memory(_vma_allocations->committed_size(rgn));
    return true;
  });

  if (failed_oom) {
    return false;
  }

  _virtual_memory_sites.move(&allocation_sites);
  return true;
}

MallocSiteIterator MemBaseline::malloc_sites(SortingOrder order) {
  assert(!_malloc_sites.is_empty(), "Not detail baseline");
  switch(order) {
    case by_size:
      malloc_sites_to_size_order();
      break;
    case by_site:
      malloc_sites_to_allocation_site_order();
      break;
    case by_site_and_tag:
      malloc_sites_to_allocation_site_and_tag_order();
      break;
    case by_address:
    default:
      ShouldNotReachHere();
  }
  return MallocSiteIterator(_malloc_sites.head());
}

VirtualMemorySiteIterator MemBaseline::virtual_memory_sites(SortingOrder order) {
  assert(!_virtual_memory_sites.is_empty(), "Not detail baseline");
  switch(order) {
    case by_size:
      virtual_memory_sites_to_size_order();
      break;
    case by_site:
      virtual_memory_sites_to_reservation_site_order();
      break;
    case by_address:
    default:
      ShouldNotReachHere();
  }
  return VirtualMemorySiteIterator(_virtual_memory_sites.head());
}


// Sorting allocations sites in different orders
void MemBaseline::malloc_sites_to_size_order() {
  if (_malloc_sites_order != by_size) {
    SortedLinkedList<MallocSite, compare_malloc_size> tmp;

    // Add malloc sites to sorted linked list to sort into size order
    tmp.move(&_malloc_sites);
    _malloc_sites.set_head(tmp.head());
    tmp.set_head(nullptr);
    _malloc_sites_order = by_size;
  }
}

void MemBaseline::malloc_sites_to_allocation_site_order() {
  if (_malloc_sites_order != by_site && _malloc_sites_order != by_site_and_tag) {
    SortedLinkedList<MallocSite, compare_malloc_site> tmp;
    // Add malloc sites to sorted linked list to sort into site (address) order
    tmp.move(&_malloc_sites);
    _malloc_sites.set_head(tmp.head());
    tmp.set_head(nullptr);
    _malloc_sites_order = by_site;
  }
}

void MemBaseline::malloc_sites_to_allocation_site_and_tag_order() {
  if (_malloc_sites_order != by_site_and_tag) {
    SortedLinkedList<MallocSite, compare_malloc_site_and_tag> tmp;
    // Add malloc sites to sorted linked list to sort into site (address) order
    tmp.move(&_malloc_sites);
    _malloc_sites.set_head(tmp.head());
    tmp.set_head(nullptr);
    _malloc_sites_order = by_site_and_tag;
  }
}

void MemBaseline::virtual_memory_sites_to_size_order() {
  if (_virtual_memory_sites_order != by_size) {
    SortedLinkedList<VirtualMemoryAllocationSite, compare_virtual_memory_size> tmp;

    tmp.move(&_virtual_memory_sites);

    _virtual_memory_sites.set_head(tmp.head());
    tmp.set_head(nullptr);
    _virtual_memory_sites_order = by_size;
  }
}

void MemBaseline::virtual_memory_sites_to_reservation_site_order() {
  if (_virtual_memory_sites_order != by_size) {
    SortedLinkedList<VirtualMemoryAllocationSite, compare_virtual_memory_site> tmp;

    tmp.move(&_virtual_memory_sites);

    _virtual_memory_sites.set_head(tmp.head());
    tmp.set_head(nullptr);

    _virtual_memory_sites_order = by_size;
  }
}


/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "gc/z/zArray.inline.hpp"
#include "gc/z/zCollectedHeap.hpp"
#include "gc/z/zForwarding.inline.hpp"
#include "gc/z/zForwardingAllocator.inline.hpp"
#include "gc/z/zGeneration.inline.hpp"
#include "gc/z/zPage.inline.hpp"
#include "gc/z/zPageAllocator.hpp"
#include "gc/z/zRelocationSet.inline.hpp"
#include "gc/z/zRelocationSetSelector.inline.hpp"
#include "gc/z/zStat.hpp"
#include "gc/z/zTask.hpp"
#include "gc/z/zWorkers.hpp"
#include "runtime/atomic.hpp"
#include "utilities/debug.hpp"

class ZRelocationSetInstallTask : public ZTask {
private:
  ZForwardingAllocator* const    _allocator;
  ZForwarding**                  _forwardings;
  const size_t                   _nforwardings;
  const ZArray<ZPage*>*          _small;
  const ZArray<ZPage*>*          _medium;
  ZArrayParallelIterator<ZPage*> _small_iter;
  ZArrayParallelIterator<ZPage*> _medium_iter;

  void install(ZForwarding* forwarding, size_t index) {
    assert(index < _nforwardings, "Invalid index");

    ZPage* const page = forwarding->page();

    page->log_msg(" (relocation selected)");

    _forwardings[index] = forwarding;

    if (forwarding->is_promotion()) {
      // Before promoting an object (and before relocate start), we must ensure that all
      // contained zpointers are store good. The marking code ensures that for non-null
      // pointers, but null pointers are ignored. This code ensures that even null pointers
      // are made store good, for the promoted objects.
      page->object_iterate([&](oop obj) {
        ZIterator::basic_oop_iterate_safe(obj, ZBarrier::promote_barrier_on_young_oop_field);
      });
    }
  }

  void install_small(ZForwarding* forwarding, size_t index) {
    install(forwarding, index);
  }

  void install_medium(ZForwarding* forwarding, size_t index) {
    install(forwarding, index);
  }

  ZPageAge to_age(ZPage* page) {
    return ZRelocate::compute_to_age(page->age());
  }

public:
  ZRelocationSetInstallTask(ZForwardingAllocator* allocator, const ZRelocationSetSelector* selector)
    : ZTask("ZRelocationSetInstallTask"),
      _allocator(allocator),
      _forwardings(nullptr),
      _nforwardings(selector->selected_small()->length() + selector->selected_medium()->length()),
      _small(selector->selected_small()),
      _medium(selector->selected_medium()),
      _small_iter(selector->selected_small()),
      _medium_iter(selector->selected_medium()) {

    // Reset the allocator to have room for the relocation
    // set, all forwardings, and all forwarding entries.
    const size_t relocation_set_size = _nforwardings * sizeof(ZForwarding*);
    const size_t forwardings_size = _nforwardings * sizeof(ZForwarding);
    const size_t forwarding_entries_size = selector->forwarding_entries() * sizeof(ZForwardingEntry);
    _allocator->reset(relocation_set_size + forwardings_size + forwarding_entries_size);

    // Allocate relocation set
    _forwardings = new (_allocator->alloc(relocation_set_size)) ZForwarding*[_nforwardings];
  }

  ~ZRelocationSetInstallTask() {
    assert(_allocator->is_full(), "Should be full");
  }

  virtual void work() {
    // Join the STS to block out VMThreads while running promote_barrier_on_young_oop_field
    SuspendibleThreadSetJoiner sts_joiner;

    // Allocate and install forwardings for small pages
    for (size_t page_index; _small_iter.next_index(&page_index);) {
      ZPage* page = _small->at(int(page_index));
      ZForwarding* const forwarding = ZForwarding::alloc(_allocator, page, to_age(page));
      install_small(forwarding, _medium->length() + page_index);

      SuspendibleThreadSet::yield();
    }

    // Allocate and install forwardings for medium pages
    for (size_t page_index; _medium_iter.next_index(&page_index);) {
      ZPage* page = _medium->at(int(page_index));
      ZForwarding* const forwarding = ZForwarding::alloc(_allocator, page, to_age(page));
      install_medium(forwarding, page_index);

      SuspendibleThreadSet::yield();
    }
  }

  ZForwarding** forwardings() const {
    return _forwardings;
  }

  size_t nforwardings() const {
    return _nforwardings;
  }
};

ZRelocationSet::ZRelocationSet(ZGeneration* generation)
  : _generation(generation),
    _allocator(),
    _forwardings(nullptr),
    _nforwardings(0),
    _promotion_lock(),
    _flip_promoted_pages(),
    _in_place_relocate_promoted_pages() {}

ZWorkers* ZRelocationSet::workers() const {
  return _generation->workers();
}

ZGeneration* ZRelocationSet::generation() const {
  return _generation;
}

ZArray<ZPage*>* ZRelocationSet::flip_promoted_pages() {
  return &_flip_promoted_pages;
}

void ZRelocationSet::install(const ZRelocationSetSelector* selector) {
  // Install relocation set
  ZRelocationSetInstallTask task(&_allocator, selector);
  workers()->run(&task);

  _forwardings = task.forwardings();
  _nforwardings = task.nforwardings();

  // Update statistics
  _generation->stat_relocation()->at_install_relocation_set(_allocator.size());
}

static void destroy_and_clear(ZPageAllocator* page_allocator, ZArray<ZPage*>* array) {
  for (int i = 0; i < array->length(); i++) {
    // Delete non-relocating promoted pages from last cycle
    ZPage* const page = array->at(i);
    page_allocator->safe_destroy_page(page);
  }

  array->clear();
}
void ZRelocationSet::reset(ZPageAllocator* page_allocator) {
  // Destroy forwardings
  ZRelocationSetIterator iter(this);
  for (ZForwarding* forwarding; iter.next(&forwarding);) {
    forwarding->~ZForwarding();
  }

  _nforwardings = 0;

  destroy_and_clear(page_allocator, &_in_place_relocate_promoted_pages);
  destroy_and_clear(page_allocator, &_flip_promoted_pages);
}

void ZRelocationSet::register_flip_promoted(const ZArray<ZPage*>& pages) {
  ZLocker<ZLock> locker(&_promotion_lock);
  for (ZPage* const page : pages) {
    assert(!_flip_promoted_pages.contains(page), "no duplicates allowed");
    _flip_promoted_pages.append(page);
  }
}

void ZRelocationSet::register_in_place_relocate_promoted(ZPage* page) {
  ZLocker<ZLock> locker(&_promotion_lock);
  assert(!_in_place_relocate_promoted_pages.contains(page), "no duplicates allowed");
  _in_place_relocate_promoted_pages.append(page);
}

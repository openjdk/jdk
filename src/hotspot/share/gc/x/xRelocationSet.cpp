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
#include "gc/x/xArray.inline.hpp"
#include "gc/x/xForwarding.inline.hpp"
#include "gc/x/xForwardingAllocator.inline.hpp"
#include "gc/x/xRelocationSet.inline.hpp"
#include "gc/x/xRelocationSetSelector.inline.hpp"
#include "gc/x/xStat.hpp"
#include "gc/x/xTask.hpp"
#include "gc/x/xWorkers.hpp"
#include "runtime/atomic.hpp"
#include "utilities/debug.hpp"

class XRelocationSetInstallTask : public XTask {
private:
  XForwardingAllocator* const    _allocator;
  XForwarding**                  _forwardings;
  const size_t                   _nforwardings;
  XArrayParallelIterator<XPage*> _small_iter;
  XArrayParallelIterator<XPage*> _medium_iter;
  volatile size_t                _small_next;
  volatile size_t                _medium_next;

  void install(XForwarding* forwarding, volatile size_t* next) {
    const size_t index = Atomic::fetch_then_add(next, 1u);
    assert(index < _nforwardings, "Invalid index");
    _forwardings[index] = forwarding;
  }

  void install_small(XForwarding* forwarding) {
    install(forwarding, &_small_next);
  }

  void install_medium(XForwarding* forwarding) {
    install(forwarding, &_medium_next);
  }

public:
  XRelocationSetInstallTask(XForwardingAllocator* allocator, const XRelocationSetSelector* selector) :
      XTask("XRelocationSetInstallTask"),
      _allocator(allocator),
      _forwardings(nullptr),
      _nforwardings(selector->small()->length() + selector->medium()->length()),
      _small_iter(selector->small()),
      _medium_iter(selector->medium()),
      _small_next(selector->medium()->length()),
      _medium_next(0) {

    // Reset the allocator to have room for the relocation
    // set, all forwardings, and all forwarding entries.
    const size_t relocation_set_size = _nforwardings * sizeof(XForwarding*);
    const size_t forwardings_size = _nforwardings * sizeof(XForwarding);
    const size_t forwarding_entries_size = selector->forwarding_entries() * sizeof(XForwardingEntry);
    _allocator->reset(relocation_set_size + forwardings_size + forwarding_entries_size);

    // Allocate relocation set
    _forwardings = new (_allocator->alloc(relocation_set_size)) XForwarding*[_nforwardings];
  }

  ~XRelocationSetInstallTask() {
    assert(_allocator->is_full(), "Should be full");
  }

  virtual void work() {
    // Allocate and install forwardings for small pages
    for (XPage* page; _small_iter.next(&page);) {
      XForwarding* const forwarding = XForwarding::alloc(_allocator, page);
      install_small(forwarding);
    }

    // Allocate and install forwardings for medium pages
    for (XPage* page; _medium_iter.next(&page);) {
      XForwarding* const forwarding = XForwarding::alloc(_allocator, page);
      install_medium(forwarding);
    }
  }

  XForwarding** forwardings() const {
    return _forwardings;
  }

  size_t nforwardings() const {
    return _nforwardings;
  }
};

XRelocationSet::XRelocationSet(XWorkers* workers) :
    _workers(workers),
    _allocator(),
    _forwardings(nullptr),
    _nforwardings(0) {}

void XRelocationSet::install(const XRelocationSetSelector* selector) {
  // Install relocation set
  XRelocationSetInstallTask task(&_allocator, selector);
  _workers->run(&task);

  _forwardings = task.forwardings();
  _nforwardings = task.nforwardings();

  // Update statistics
  XStatRelocation::set_at_install_relocation_set(_allocator.size());
}

void XRelocationSet::reset() {
  // Destroy forwardings
  XRelocationSetIterator iter(this);
  for (XForwarding* forwarding; iter.next(&forwarding);) {
    forwarding->~XForwarding();
  }

  _nforwardings = 0;
}

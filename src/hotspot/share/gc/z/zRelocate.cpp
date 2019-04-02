/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zBarrier.inline.hpp"
#include "gc/z/zForwarding.inline.hpp"
#include "gc/z/zHeap.hpp"
#include "gc/z/zOopClosures.inline.hpp"
#include "gc/z/zPage.hpp"
#include "gc/z/zRelocate.hpp"
#include "gc/z/zRelocationSet.inline.hpp"
#include "gc/z/zRootsIterator.hpp"
#include "gc/z/zStat.hpp"
#include "gc/z/zTask.hpp"
#include "gc/z/zThreadLocalAllocBuffer.hpp"
#include "gc/z/zWorkers.hpp"
#include "logging/log.hpp"

static const ZStatCounter ZCounterRelocationContention("Contention", "Relocation Contention", ZStatUnitOpsPerSecond);

ZRelocate::ZRelocate(ZWorkers* workers) :
    _workers(workers) {}

class ZRelocateRootsIteratorClosure : public ZRootsIteratorClosure {
public:
  virtual void do_thread(Thread* thread) {
    // Update thread local address bad mask
    ZThreadLocalData::set_address_bad_mask(thread, ZAddressBadMask);

    // Remap TLAB
    ZThreadLocalAllocBuffer::remap(thread);
  }

  virtual void do_oop(oop* p) {
    ZBarrier::relocate_barrier_on_root_oop_field(p);
  }

  virtual void do_oop(narrowOop* p) {
    ShouldNotReachHere();
  }
};

class ZRelocateRootsTask : public ZTask {
private:
  ZRootsIterator                _roots;
  ZRelocateRootsIteratorClosure _cl;

public:
  ZRelocateRootsTask() :
      ZTask("ZRelocateRootsTask"),
      _roots() {}

  virtual void work() {
    // During relocation we need to visit the JVMTI
    // export weak roots to rehash the JVMTI tag map
    _roots.oops_do(&_cl, true /* visit_jvmti_weak_export */);
  }
};

void ZRelocate::start() {
  ZRelocateRootsTask task;
  _workers->run_parallel(&task);
}

uintptr_t ZRelocate::relocate_object_inner(ZForwarding* forwarding, uintptr_t from_index, uintptr_t from_offset) const {
  ZForwardingCursor cursor;

  // Lookup forwarding entry
  const ZForwardingEntry entry = forwarding->find(from_index, &cursor);
  if (entry.populated() && entry.from_index() == from_index) {
    // Already relocated, return new address
    return entry.to_offset();
  }

  assert(ZHeap::heap()->is_object_live(ZAddress::good(from_offset)), "Should be live");

  if (forwarding->is_pinned()) {
    // In-place forward
    return forwarding->insert(from_index, from_offset, &cursor);
  }

  // Allocate object
  const uintptr_t from_good = ZAddress::good(from_offset);
  const size_t size = ZUtils::object_size(from_good);
  const uintptr_t to_good = ZHeap::heap()->alloc_object_for_relocation(size);
  if (to_good == 0) {
    // Failed, in-place forward
    return forwarding->insert(from_index, from_offset, &cursor);
  }

  // Copy object
  ZUtils::object_copy(from_good, to_good, size);

  // Insert forwarding entry
  const uintptr_t to_offset = ZAddress::offset(to_good);
  const uintptr_t to_offset_final = forwarding->insert(from_index, to_offset, &cursor);
  if (to_offset_final == to_offset) {
    // Relocation succeeded
    return to_offset;
  }

  // Relocation contention
  ZStatInc(ZCounterRelocationContention);
  log_trace(gc)("Relocation contention, thread: " PTR_FORMAT " (%s), forwarding: " PTR_FORMAT
                ", entry: " UINT32_FORMAT ", oop: " PTR_FORMAT ", size: " SIZE_FORMAT,
                ZThread::id(), ZThread::name(), p2i(forwarding), cursor, from_good, size);

  // Try undo allocation
  ZHeap::heap()->undo_alloc_object_for_relocation(to_good, size);

  return to_offset_final;
}

uintptr_t ZRelocate::relocate_object(ZForwarding* forwarding, uintptr_t from_addr) const {
  const uintptr_t from_offset = ZAddress::offset(from_addr);
  const uintptr_t from_index = (from_offset - forwarding->start()) >> forwarding->object_alignment_shift();
  const uintptr_t to_offset = relocate_object_inner(forwarding, from_index, from_offset);

  if (from_offset == to_offset) {
    // In-place forwarding, pin page
    forwarding->set_pinned();
  }

  return ZAddress::good(to_offset);
}

uintptr_t ZRelocate::forward_object(ZForwarding* forwarding, uintptr_t from_addr) const {
  const uintptr_t from_offset = ZAddress::offset(from_addr);
  const uintptr_t from_index = (from_offset - forwarding->start()) >> forwarding->object_alignment_shift();
  const ZForwardingEntry entry = forwarding->find(from_index);

  assert(entry.populated(), "Should be forwarded");
  assert(entry.from_index() == from_index, "Should be forwarded");

  return ZAddress::good(entry.to_offset());
}

class ZRelocateObjectClosure : public ObjectClosure {
private:
  ZRelocate* const   _relocate;
  ZForwarding* const _forwarding;

public:
  ZRelocateObjectClosure(ZRelocate* relocate, ZForwarding* forwarding) :
      _relocate(relocate),
      _forwarding(forwarding) {}

  virtual void do_object(oop o) {
    _relocate->relocate_object(_forwarding, ZOop::to_address(o));
  }
};

bool ZRelocate::work(ZRelocationSetParallelIterator* iter) {
  bool success = true;

  // Relocate pages in the relocation set
  for (ZForwarding* forwarding; iter->next(&forwarding);) {
    // Relocate objects in page
    ZRelocateObjectClosure cl(this, forwarding);
    forwarding->page()->object_iterate(&cl);

    if (ZVerifyForwarding) {
      forwarding->verify();
    }

    if (forwarding->is_pinned()) {
      // Relocation failed, page is now pinned
      success = false;
    } else {
      // Relocation succeeded, release page
      forwarding->release_page();
    }
  }

  return success;
}

class ZRelocateTask : public ZTask {
private:
  ZRelocate* const               _relocate;
  ZRelocationSetParallelIterator _iter;
  bool                           _failed;

public:
  ZRelocateTask(ZRelocate* relocate, ZRelocationSet* relocation_set) :
      ZTask("ZRelocateTask"),
      _relocate(relocate),
      _iter(relocation_set),
      _failed(false) {}

  virtual void work() {
    if (!_relocate->work(&_iter)) {
      _failed = true;
    }
  }

  bool failed() const {
    return _failed;
  }
};

bool ZRelocate::relocate(ZRelocationSet* relocation_set) {
  ZRelocateTask task(this, relocation_set);
  _workers->run_concurrent(&task);
  return !task.failed();
}

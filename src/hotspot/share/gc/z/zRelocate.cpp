/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/z/zHeap.hpp"
#include "gc/z/zOopClosures.inline.hpp"
#include "gc/z/zPage.hpp"
#include "gc/z/zRelocate.hpp"
#include "gc/z/zRelocationSet.inline.hpp"
#include "gc/z/zRootsIterator.hpp"
#include "gc/z/zTask.hpp"
#include "gc/z/zWorkers.hpp"

ZRelocate::ZRelocate(ZWorkers* workers) :
    _workers(workers) {}

class ZRelocateRootsTask : public ZTask {
private:
  ZRootsIterator _roots;

public:
  ZRelocateRootsTask() :
      ZTask("ZRelocateRootsTask"),
      _roots() {}

  virtual void work() {
    // During relocation we need to visit the JVMTI
    // export weak roots to rehash the JVMTI tag map
    ZRelocateRootOopClosure cl;
    _roots.oops_do(&cl, true /* visit_jvmti_weak_export */);
  }
};

void ZRelocate::start() {
  ZRelocateRootsTask task;
  _workers->run_parallel(&task);
}

class ZRelocateObjectClosure : public ObjectClosure {
private:
  ZPage* const _page;

public:
  ZRelocateObjectClosure(ZPage* page) :
      _page(page) {}

  virtual void do_object(oop o) {
    _page->relocate_object(ZOop::to_address(o));
  }
};

bool ZRelocate::work(ZRelocationSetParallelIterator* iter) {
  bool success = true;

  // Relocate pages in the relocation set
  for (ZPage* page; iter->next(&page);) {
    // Relocate objects in page
    ZRelocateObjectClosure cl(page);
    page->object_iterate(&cl);

    if (ZVerifyForwarding) {
      page->verify_forwarding();
    }

    if (page->is_pinned()) {
      // Relocation failed, page is now pinned
      success = false;
    } else {
      // Relocation succeeded, release page
      ZHeap::heap()->release_page(page, true /* reclaimed */);
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

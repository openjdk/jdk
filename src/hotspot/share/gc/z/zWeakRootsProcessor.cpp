/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shared/suspendibleThreadSet.hpp"
#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zBarrier.inline.hpp"
#include "gc/z/zHeap.inline.hpp"
#include "gc/z/zRootsIterator.hpp"
#include "gc/z/zTask.hpp"
#include "gc/z/zWeakRootsProcessor.hpp"
#include "gc/z/zWorkers.hpp"
#include "memory/iterator.hpp"
#include "runtime/atomic.hpp"
#include "utilities/debug.hpp"

class ZPhantomCleanOopClosure : public OopClosure {
public:
  virtual void do_oop(oop* p) {
    ZBarrier::clean_barrier_on_phantom_oop_field((zpointer*)p);
    SuspendibleThreadSet::yield();
  }

  virtual void do_oop(narrowOop* p) {
    ShouldNotReachHere();
  }
};

ZWeakRootsProcessor::ZWeakRootsProcessor(ZWorkers* workers)
  : _workers(workers) {}

class ZProcessWeakRootsTask : public ZTask {
private:
  ZRootsIteratorWeakColored _roots_weak_colored;

public:
  ZProcessWeakRootsTask()
    : ZTask("ZProcessWeakRootsTask"),
      _roots_weak_colored(ZGenerationIdOptional::old) {}

  ~ZProcessWeakRootsTask() {
    _roots_weak_colored.report_num_dead();
  }

  virtual void work() {
    SuspendibleThreadSetJoiner sts_joiner;
    ZPhantomCleanOopClosure cl;
    _roots_weak_colored.apply(&cl);
  }
};

void ZWeakRootsProcessor::process_weak_roots() {
  ZProcessWeakRootsTask task;
  _workers->run(&task);
}

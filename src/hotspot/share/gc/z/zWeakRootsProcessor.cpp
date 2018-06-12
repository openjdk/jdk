/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/z/zHeap.inline.hpp"
#include "gc/z/zOopClosures.inline.hpp"
#include "gc/z/zStat.hpp"
#include "gc/z/zTask.hpp"
#include "gc/z/zThread.hpp"
#include "runtime/jniHandles.hpp"

ZWeakRootsProcessor::ZWeakRootsProcessor(ZWorkers* workers) :
    _workers(workers) {}

class ZProcessWeakRootsTask : public ZTask {
private:
  ZWeakRootsIterator _weak_roots;

public:
  ZProcessWeakRootsTask() :
      ZTask("ZProcessWeakRootsTask"),
      _weak_roots() {}

  virtual void work() {
    ZPhantomIsAliveObjectClosure is_alive;
    ZPhantomKeepAliveOopClosure keep_alive;
    _weak_roots.weak_oops_do(&is_alive, &keep_alive);
  }
};

void ZWeakRootsProcessor::process_weak_roots() {
  ZProcessWeakRootsTask task;
  _workers->run_parallel(&task);
}

class ZProcessConcurrentWeakRootsTask : public ZTask {
private:
  ZConcurrentWeakRootsIterator _concurrent_weak_roots;

public:
  ZProcessConcurrentWeakRootsTask() :
      ZTask("ZProcessConccurentWeakRootsTask"),
      _concurrent_weak_roots() {}

  virtual void work() {
    ZPhantomCleanOopClosure cl;
    _concurrent_weak_roots.oops_do(&cl);
  }
};

void ZWeakRootsProcessor::process_concurrent_weak_roots() {
  ZProcessConcurrentWeakRootsTask task;
  _workers->run_concurrent(&task);
}

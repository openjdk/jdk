/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/z/zStatTLAB.hpp"

ZPerWorker<ThreadLocalAllocStats>* ZStatTLAB::_stats = NULL;

void ZStatTLAB::initialize() {
  if (UseTLAB) {
    assert(_stats == NULL, "Already initialized");
    _stats = new ZPerWorker<ThreadLocalAllocStats>();
    reset();
  }
}

void ZStatTLAB::reset() {
  if (UseTLAB) {
    ZPerWorkerIterator<ThreadLocalAllocStats> iter(_stats);
    for (ThreadLocalAllocStats* stats; iter.next(&stats);) {
      stats->reset();
    }
  }
}

ThreadLocalAllocStats* ZStatTLAB::get() {
  if (UseTLAB) {
    return _stats->addr();
  }

  return NULL;
}

void ZStatTLAB::publish() {
  if (UseTLAB) {
    ThreadLocalAllocStats total;

    ZPerWorkerIterator<ThreadLocalAllocStats> iter(_stats);
    for (ThreadLocalAllocStats* stats; iter.next(&stats);) {
      total.update(*stats);
    }

    total.publish();
  }
}

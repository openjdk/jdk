/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/z/zMarkingSMR.hpp"
#include "gc/z/zMarkStack.inline.hpp"
#include "gc/z/zValue.inline.hpp"
#include "runtime/atomic.hpp"

ZMarkingSMR::ZMarkingSMR()
  : _worker_states(),
    _expanded_recently() {
}

void ZMarkingSMR::free_node(ZMarkStackListNode* node) {
  // We use hazard pointers as an safe memory reclamation (SMR) technique,
  // for marking stacks. Each stripe has a lock-free stack of mark stacks.
  // When a GC thread (1) pops a mark stack from this lock-free stack,
  // there is a small window of time when the head has been read and we
  // are about to read its next pointer. It is then of great importance
  // that the node is not concurrently freed by another concurrent GC
  // thread (2), popping the same entry. In such an event, the memory
  // of the freed node could, for example become part of a separate
  // node, and potentially pushed onto a separate stripe, with a
  // different next pointer referring to a node of the other stripe.
  // When GC thread (1) then reads the next pointer of what it believed
  // to be the current head node of the first stripe, it actually read
  // a next pointer of a logically different node, pointing into the
  // other stripe. GC thread (2) could then pop the node from the second
  // mark stripe and re-insert it as the head of the first stripe.
  // Disaster eventually hits when GC thread (1) succeeds with its
  // CAS (ABA problem), switching the loaded head to the loaded next
  // pointer of the head. Due to the next pointer belonging to a logically
  // different node than the logical head, we can accidentally corrupt the
  // stack integrity. Using hazard pointers involves publishing what head
  // was observed by GC thread (1), so that GC thread (2) knows not to
  // free the node when popping it in this race. This prevents the racy
  // interactions from causing any such use-after-free problems.

  assert(Thread::current()->is_Worker_thread(), "must be a worker");

  ZWorkerState* const local_state = _worker_states.addr();
  ZArray<ZMarkStackListNode*>* const freeing = &local_state->_freeing;
  freeing->append(node);

  if (freeing->length() < (int)ZPerWorkerStorage::count() * 8) {
    return;
  }

  ZPerWorkerIterator<ZWorkerState> iter(&_worker_states);
  ZArray<ZMarkStackListNode*>* const scanned_hazards = &local_state->_scanned_hazards;

  for (ZWorkerState* remote_state; iter.next(&remote_state);) {
    ZMarkStackListNode* const hazard = Atomic::load(&remote_state->_hazard_ptr);

    if (hazard != nullptr) {
      scanned_hazards->append(hazard);
    }
  }

  int kept = 0;
  for (int i = 0; i < freeing->length(); ++i) {
    ZMarkStackListNode* node = freeing->at(i);
    freeing->at_put(i, nullptr);

    if (scanned_hazards->contains(node)) {
      // Keep
      freeing->at_put(kept++, node);
    } else {
      // Delete
      delete node;
    }
  }

  scanned_hazards->clear();
  freeing->trunc_to(kept);
}

void ZMarkingSMR::free() {
  // Here it is free by definition to free mark stacks.
  ZPerWorkerIterator<ZWorkerState> iter(&_worker_states);
  for (ZWorkerState* worker_state; iter.next(&worker_state);) {
    ZArray<ZMarkStackListNode*>* const freeing = &worker_state->_freeing;
    for (ZMarkStackListNode* node: *freeing) {
      delete node;
    }
    freeing->clear();
  }
}

ZMarkStackListNode* volatile* ZMarkingSMR::hazard_ptr() {
  return &_worker_states.addr()->_hazard_ptr;
}

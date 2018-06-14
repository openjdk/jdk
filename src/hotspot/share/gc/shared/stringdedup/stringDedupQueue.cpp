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
 *
 */

#include "precompiled.hpp"

#include "gc/shared/stringdedup/stringDedup.hpp"
#include "gc/shared/stringdedup/stringDedupQueue.hpp"
#include "runtime/atomic.hpp"

StringDedupQueue* StringDedupQueue::_queue = NULL;
volatile size_t   StringDedupQueue::_claimed_index = 0;

size_t StringDedupQueue::claim() {
  return Atomic::add(size_t(1), &_claimed_index) - 1;
}

void StringDedupQueue::unlink_or_oops_do(StringDedupUnlinkOrOopsDoClosure* cl) {
  size_t claimed_queue = claim();
  while (claimed_queue < queue()->num_queues()) {
    queue()->unlink_or_oops_do_impl(cl, claimed_queue);
    claimed_queue = claim();
  }
}

void StringDedupQueue::print_statistics() {
  queue()->print_statistics_impl();
}

void StringDedupQueue::verify() {
  queue()->verify_impl();
}

StringDedupQueue* const StringDedupQueue::queue() {
  assert(_queue != NULL, "Not yet initialized");
  return _queue;
}


void StringDedupQueue::gc_prologue() {
  _claimed_index = 0;
}

void StringDedupQueue::gc_epilogue() {
  assert(_claimed_index >= queue()->num_queues() || _claimed_index == 0, "All or nothing");
}

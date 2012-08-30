/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
#include "services/memPtr.hpp"
#include "services/memTracker.hpp"

volatile jint SequenceGenerator::_seq_number = 1;
NOT_PRODUCT(jint SequenceGenerator::_max_seq_number = 1;)
DEBUG_ONLY(volatile unsigned long SequenceGenerator::_generation = 0;)

jint SequenceGenerator::next() {
  jint seq = Atomic::add(1, &_seq_number);
  if (seq < 0) {
    MemTracker::shutdown(MemTracker::NMT_sequence_overflow);
  }
  assert(seq > 0, "counter overflow");
  NOT_PRODUCT(_max_seq_number = (seq > _max_seq_number) ? seq : _max_seq_number;)
  return seq;
}



bool VMMemRegion::contains(const VMMemRegion* mr) const {
  assert(base() != 0, "no base address");
  assert(size() != 0 || committed_size() != 0,
    "no range");
  address base_addr = base();
  address end_addr = base_addr +
    (is_reserve_record()? reserved_size(): committed_size());
  if (mr->is_reserve_record()) {
    if (mr->base() == base_addr && mr->size() == size()) {
      // the same range
      return true;
    }
    return false;
  } else if (mr->is_commit_record() || mr->is_uncommit_record()) {
    assert(mr->base() != 0 && mr->committed_size() > 0,
      "bad record");
    return (mr->base() >= base_addr &&
      (mr->base() + mr->committed_size()) <= end_addr);
  } else if (mr->is_type_tagging_record()) {
    assert(mr->base() != 0, "no base");
    return mr->base() == base_addr;
  } else if (mr->is_release_record()) {
    assert(mr->base() != 0 && mr->size() > 0,
      "bad record");
    return (mr->base() == base_addr && mr->size() == size());
  } else {
    assert(false, "what happened?");
    return false;
  }
}

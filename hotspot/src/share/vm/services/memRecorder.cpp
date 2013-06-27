/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

#include "runtime/atomic.hpp"
#include "services/memBaseline.hpp"
#include "services/memRecorder.hpp"
#include "services/memPtr.hpp"
#include "services/memTracker.hpp"

MemPointer* SequencedRecordIterator::next_record() {
  MemPointerRecord* itr_cur = (MemPointerRecord*)_itr.current();
  if (itr_cur == NULL)  {
    return itr_cur;
  }

  MemPointerRecord* itr_next = (MemPointerRecord*)_itr.next();

  // don't collapse virtual memory records
  while (itr_next != NULL && !itr_cur->is_vm_pointer() &&
    !itr_next->is_vm_pointer() &&
    same_kind(itr_cur, itr_next)) {
    itr_cur = itr_next;
    itr_next = (MemPointerRecord*)_itr.next();
  }

  return itr_cur;
}


volatile jint MemRecorder::_instance_count = 0;

MemRecorder::MemRecorder() {
  assert(MemTracker::is_on(), "Native memory tracking is off");
  Atomic::inc(&_instance_count);
  set_generation();

  if (MemTracker::track_callsite()) {
    _pointer_records = new (std::nothrow)FixedSizeMemPointerArray<SeqMemPointerRecordEx,
        DEFAULT_RECORDER_PTR_ARRAY_SIZE>();
  } else {
    _pointer_records = new (std::nothrow)FixedSizeMemPointerArray<SeqMemPointerRecord,
        DEFAULT_RECORDER_PTR_ARRAY_SIZE>();
  }
  _next = NULL;


  if (_pointer_records != NULL) {
    // recode itself
    address pc = CURRENT_PC;
    record((address)this, (MemPointerRecord::malloc_tag()|mtNMT|otNMTRecorder),
        sizeof(MemRecorder), SequenceGenerator::next(), pc);
    record((address)_pointer_records, (MemPointerRecord::malloc_tag()|mtNMT|otNMTRecorder),
        _pointer_records->instance_size(), SequenceGenerator::next(), pc);
  }
}

MemRecorder::~MemRecorder() {
  if (_pointer_records != NULL) {
    if (MemTracker::is_on()) {
      MemTracker::record_free((address)_pointer_records, mtNMT);
      MemTracker::record_free((address)this, mtNMT);
    }
    delete _pointer_records;
  }
  // delete all linked recorders
  while (_next != NULL) {
    MemRecorder* tmp = _next;
    _next = _next->next();
    tmp->set_next(NULL);
    delete tmp;
  }
  Atomic::dec(&_instance_count);
}

// Sorting order:
//   1. memory block address
//   2. mem pointer record tags
//   3. sequence number
int MemRecorder::sort_record_fn(const void* e1, const void* e2) {
  const MemPointerRecord* p1 = (const MemPointerRecord*)e1;
  const MemPointerRecord* p2 = (const MemPointerRecord*)e2;
  int delta = UNSIGNED_COMPARE(p1->addr(), p2->addr());
  if (delta == 0) {
    int df = UNSIGNED_COMPARE((p1->flags() & MemPointerRecord::tag_masks),
                              (p2->flags() & MemPointerRecord::tag_masks));
    if (df == 0) {
      assert(p1->seq() != p2->seq(), "dup seq");
      return p1->seq() - p2->seq();
    } else {
      return df;
    }
  } else {
    return delta;
  }
}

bool MemRecorder::record(address p, MEMFLAGS flags, size_t size, jint seq, address pc) {
  assert(seq > 0, "No sequence number");
#ifdef ASSERT
  if (MemPointerRecord::is_virtual_memory_record(flags)) {
    assert((flags & MemPointerRecord::tag_masks) != 0, "bad virtual memory record");
  } else {
    assert((flags & MemPointerRecord::tag_masks) == MemPointerRecord::malloc_tag() ||
           (flags & MemPointerRecord::tag_masks) == MemPointerRecord::free_tag() ||
           IS_ARENA_OBJ(flags),
           "bad malloc record");
  }
  // a recorder should only hold records within the same generation
  unsigned long cur_generation = SequenceGenerator::current_generation();
  assert(cur_generation == _generation,
         "this thread did not enter sync point");
#endif

  if (MemTracker::track_callsite()) {
    SeqMemPointerRecordEx ap(p, flags, size, seq, pc);
    debug_only(check_dup_seq(ap.seq());)
    return _pointer_records->append(&ap);
  } else {
    SeqMemPointerRecord ap(p, flags, size, seq);
    debug_only(check_dup_seq(ap.seq());)
    return _pointer_records->append(&ap);
  }
}

  // iterator for alloc pointers
SequencedRecordIterator MemRecorder::pointer_itr() {
  assert(_pointer_records != NULL, "just check");
  _pointer_records->sort((FN_SORT)sort_record_fn);
  return SequencedRecordIterator(_pointer_records);
}


void MemRecorder::set_generation() {
  _generation = SequenceGenerator::current_generation();
}

#ifdef ASSERT

void MemRecorder::check_dup_seq(jint seq) const {
  MemPointerArrayIteratorImpl itr(_pointer_records);
  MemPointerRecord* rc = (MemPointerRecord*)itr.current();
  while (rc != NULL) {
    assert(rc->seq() != seq, "dup seq");
    rc = (MemPointerRecord*)itr.next();
  }
}

#endif

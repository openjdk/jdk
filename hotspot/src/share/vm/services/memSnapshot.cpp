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
#include "runtime/mutexLocker.hpp"
#include "utilities/decoder.hpp"
#include "services/memBaseline.hpp"
#include "services/memPtr.hpp"
#include "services/memPtrArray.hpp"
#include "services/memSnapshot.hpp"
#include "services/memTracker.hpp"

static int sort_in_seq_order(const void* p1, const void* p2) {
  assert(p1 != NULL && p2 != NULL, "Sanity check");
  const MemPointerRecord* mp1 = (MemPointerRecord*)p1;
  const MemPointerRecord* mp2 = (MemPointerRecord*)p2;
  return (mp1->seq() - mp2->seq());
}

bool StagingArea::init() {
  if (MemTracker::track_callsite()) {
    _malloc_data = new (std::nothrow)MemPointerArrayImpl<SeqMemPointerRecordEx>();
    _vm_data = new (std::nothrow)MemPointerArrayImpl<SeqMemPointerRecordEx>();
  } else {
    _malloc_data = new (std::nothrow)MemPointerArrayImpl<SeqMemPointerRecord>();
    _vm_data = new (std::nothrow)MemPointerArrayImpl<SeqMemPointerRecord>();
  }

  if (_malloc_data != NULL && _vm_data != NULL &&
      !_malloc_data->out_of_memory() &&
      !_vm_data->out_of_memory()) {
    return true;
  } else {
    if (_malloc_data != NULL) delete _malloc_data;
    if (_vm_data != NULL) delete _vm_data;
    _malloc_data = NULL;
    _vm_data = NULL;
    return false;
  }
}


MemPointerArrayIteratorImpl StagingArea::virtual_memory_record_walker() {
  MemPointerArray* arr = vm_data();
  // sort into seq number order
  arr->sort((FN_SORT)sort_in_seq_order);
  return MemPointerArrayIteratorImpl(arr);
}


MemSnapshot::MemSnapshot() {
  if (MemTracker::track_callsite()) {
    _alloc_ptrs = new (std::nothrow) MemPointerArrayImpl<MemPointerRecordEx>();
    _vm_ptrs = new (std::nothrow)MemPointerArrayImpl<VMMemRegionEx>(64, true);
  } else {
    _alloc_ptrs = new (std::nothrow) MemPointerArrayImpl<MemPointerRecord>();
    _vm_ptrs = new (std::nothrow)MemPointerArrayImpl<VMMemRegion>(64, true);
  }

  _staging_area.init();
  _lock = new (std::nothrow) Mutex(Monitor::max_nonleaf - 1, "memSnapshotLock");
  NOT_PRODUCT(_untracked_count = 0;)
}

MemSnapshot::~MemSnapshot() {
  assert(MemTracker::shutdown_in_progress(), "native memory tracking still on");
  {
    MutexLockerEx locker(_lock);
    if (_alloc_ptrs != NULL) {
      delete _alloc_ptrs;
      _alloc_ptrs = NULL;
    }

    if (_vm_ptrs != NULL) {
      delete _vm_ptrs;
      _vm_ptrs = NULL;
    }
  }

  if (_lock != NULL) {
    delete _lock;
    _lock = NULL;
  }
}

void MemSnapshot::copy_pointer(MemPointerRecord* dest, const MemPointerRecord* src) {
  assert(dest != NULL && src != NULL, "Just check");
  assert(dest->addr() == src->addr(), "Just check");

  MEMFLAGS flags = dest->flags();

  if (MemTracker::track_callsite()) {
    *(MemPointerRecordEx*)dest = *(MemPointerRecordEx*)src;
  } else {
    *dest = *src;
  }
}


// merge a per-thread memory recorder to the staging area
bool MemSnapshot::merge(MemRecorder* rec) {
  assert(rec != NULL && !rec->out_of_memory(), "Just check");

  SequencedRecordIterator itr(rec->pointer_itr());

  MutexLockerEx lock(_lock, true);
  MemPointerIterator malloc_staging_itr(_staging_area.malloc_data());
  MemPointerRecord *p1, *p2;
  p1 = (MemPointerRecord*) itr.current();
  while (p1 != NULL) {
    if (p1->is_vm_pointer()) {
      // we don't do anything with virtual memory records during merge
      if (!_staging_area.vm_data()->append(p1)) {
        return false;
      }
    } else {
      p2 = (MemPointerRecord*)malloc_staging_itr.locate(p1->addr());
      // we have not seen this memory block, so just add to staging area
      if (p2 == NULL) {
        if (!malloc_staging_itr.insert(p1)) {
          return false;
        }
      } else if (p1->addr() == p2->addr()) {
        MemPointerRecord* staging_next = (MemPointerRecord*)malloc_staging_itr.peek_next();
        // a memory block can have many tagging records, find right one to replace or
        // right position to insert
        while (staging_next != NULL && staging_next->addr() == p1->addr()) {
          if ((staging_next->flags() & MemPointerRecord::tag_masks) <=
            (p1->flags() & MemPointerRecord::tag_masks)) {
            p2 = (MemPointerRecord*)malloc_staging_itr.next();
            staging_next = (MemPointerRecord*)malloc_staging_itr.peek_next();
          } else {
            break;
          }
        }
        int df = (p1->flags() & MemPointerRecord::tag_masks) -
          (p2->flags() & MemPointerRecord::tag_masks);
        if (df == 0) {
          assert(p1->seq() > 0, "not sequenced");
          assert(p2->seq() > 0, "not sequenced");
          if (p1->seq() > p2->seq()) {
            copy_pointer(p2, p1);
          }
        } else if (df < 0) {
          if (!malloc_staging_itr.insert(p1)) {
            return false;
          }
        } else {
          if (!malloc_staging_itr.insert_after(p1)) {
            return false;
          }
        }
      } else if (p1->addr() < p2->addr()) {
        if (!malloc_staging_itr.insert(p1)) {
          return false;
        }
      } else {
        if (!malloc_staging_itr.insert_after(p1)) {
          return false;
        }
      }
    }
    p1 = (MemPointerRecord*)itr.next();
  }
  NOT_PRODUCT(void check_staging_data();)
  return true;
}



// promote data to next generation
bool MemSnapshot::promote() {
  assert(_alloc_ptrs != NULL && _vm_ptrs != NULL, "Just check");
  assert(_staging_area.malloc_data() != NULL && _staging_area.vm_data() != NULL,
         "Just check");
  MutexLockerEx lock(_lock, true);

  MallocRecordIterator  malloc_itr = _staging_area.malloc_record_walker();
  bool promoted = false;
  if (promote_malloc_records(&malloc_itr)) {
    MemPointerArrayIteratorImpl vm_itr = _staging_area.virtual_memory_record_walker();
    if (promote_virtual_memory_records(&vm_itr)) {
      promoted = true;
    }
  }

  NOT_PRODUCT(check_malloc_pointers();)
  _staging_area.clear();
  return promoted;
}

bool MemSnapshot::promote_malloc_records(MemPointerArrayIterator* itr) {
  MemPointerIterator malloc_snapshot_itr(_alloc_ptrs);
  MemPointerRecord* new_rec = (MemPointerRecord*)itr->current();
  MemPointerRecord* matched_rec;
  while (new_rec != NULL) {
    matched_rec = (MemPointerRecord*)malloc_snapshot_itr.locate(new_rec->addr());
    // found matched memory block
    if (matched_rec != NULL && new_rec->addr() == matched_rec->addr()) {
      // snapshot already contains 'lived' records
      assert(matched_rec->is_allocation_record() || matched_rec->is_arena_size_record(),
             "Sanity check");
      // update block states
      if (new_rec->is_allocation_record() || new_rec->is_arena_size_record()) {
        copy_pointer(matched_rec, new_rec);
      } else {
        // a deallocation record
        assert(new_rec->is_deallocation_record(), "Sanity check");
        // an arena record can be followed by a size record, we need to remove both
        if (matched_rec->is_arena_record()) {
          MemPointerRecord* next = (MemPointerRecord*)malloc_snapshot_itr.peek_next();
          if (next->is_arena_size_record()) {
            // it has to match the arena record
            assert(next->is_size_record_of_arena(matched_rec), "Sanity check");
            malloc_snapshot_itr.remove();
          }
        }
        // the memory is deallocated, remove related record(s)
        malloc_snapshot_itr.remove();
      }
    } else {
      // it is a new record, insert into snapshot
      if (new_rec->is_arena_size_record()) {
        MemPointerRecord* prev = (MemPointerRecord*)malloc_snapshot_itr.peek_prev();
        if (prev == NULL || !prev->is_arena_record() || !new_rec->is_size_record_of_arena(prev)) {
          // no matched arena record, ignore the size record
          new_rec = NULL;
        }
      }
      // only 'live' record can go into snapshot
      if (new_rec != NULL) {
        if  (new_rec->is_allocation_record() || new_rec->is_arena_size_record()) {
          if (matched_rec != NULL && new_rec->addr() > matched_rec->addr()) {
            if (!malloc_snapshot_itr.insert_after(new_rec)) {
              return false;
            }
          } else {
            if (!malloc_snapshot_itr.insert(new_rec)) {
              return false;
            }
          }
        }
#ifndef PRODUCT
        else if (!has_allocation_record(new_rec->addr())) {
          // NMT can not track some startup memory, which is allocated before NMT is on
          _untracked_count ++;
        }
#endif
      }
    }
    new_rec = (MemPointerRecord*)itr->next();
  }
  return true;
}

bool MemSnapshot::promote_virtual_memory_records(MemPointerArrayIterator* itr) {
  VMMemPointerIterator vm_snapshot_itr(_vm_ptrs);
  MemPointerRecord* new_rec = (MemPointerRecord*)itr->current();
  VMMemRegionEx new_vm_rec;
  VMMemRegion*  matched_rec;
  while (new_rec != NULL) {
    assert(new_rec->is_vm_pointer(), "Sanity check");
    if (MemTracker::track_callsite()) {
      new_vm_rec.init((MemPointerRecordEx*)new_rec);
    } else {
      new_vm_rec.init(new_rec);
    }
    matched_rec = (VMMemRegion*)vm_snapshot_itr.locate(new_rec->addr());
    if (matched_rec != NULL &&
        (matched_rec->contains(&new_vm_rec) || matched_rec->base() == new_vm_rec.base())) {
      // snapshot can only have 'live' records
      assert(matched_rec->is_reserve_record(), "Sanity check");
      if (new_vm_rec.is_reserve_record() && matched_rec->base() == new_vm_rec.base()) {
        // resize reserved virtual memory range
        // resize has to cover committed area
        assert(new_vm_rec.size() >= matched_rec->committed_size(), "Sanity check");
        matched_rec->set_reserved_size(new_vm_rec.size());
      } else if (new_vm_rec.is_commit_record()) {
        // commit memory inside reserved memory range
        assert(new_vm_rec.committed_size() <= matched_rec->reserved_size(), "Sanity check");
        // thread stacks are marked committed, so we ignore 'commit' record for creating
        // stack guard pages
        if (FLAGS_TO_MEMORY_TYPE(matched_rec->flags()) != mtThreadStack) {
          matched_rec->commit(new_vm_rec.committed_size());
        }
      } else if (new_vm_rec.is_uncommit_record()) {
        if (FLAGS_TO_MEMORY_TYPE(matched_rec->flags()) == mtThreadStack) {
          // ignore 'uncommit' record from removing stack guard pages, uncommit
          // thread stack as whole
          if (matched_rec->committed_size() == new_vm_rec.committed_size()) {
            matched_rec->uncommit(new_vm_rec.committed_size());
          }
        } else {
          // uncommit memory inside reserved memory range
          assert(new_vm_rec.committed_size() <= matched_rec->committed_size(),
                "Sanity check");
          matched_rec->uncommit(new_vm_rec.committed_size());
        }
      } else if (new_vm_rec.is_type_tagging_record()) {
        // tag this virtual memory range to a memory type
        // can not re-tag a memory range to different type
        assert(FLAGS_TO_MEMORY_TYPE(matched_rec->flags()) == mtNone ||
               FLAGS_TO_MEMORY_TYPE(matched_rec->flags()) == FLAGS_TO_MEMORY_TYPE(new_vm_rec.flags()),
               "Sanity check");
        matched_rec->tag(new_vm_rec.flags());
      } else if (new_vm_rec.is_release_record()) {
        // release part or whole memory range
        if (new_vm_rec.base() == matched_rec->base() &&
            new_vm_rec.size() == matched_rec->size()) {
          // release whole virtual memory range
          assert(matched_rec->committed_size() == 0, "Sanity check");
          vm_snapshot_itr.remove();
        } else {
          // partial release
          matched_rec->partial_release(new_vm_rec.base(), new_vm_rec.size());
        }
      } else {
        // multiple reserve/commit on the same virtual memory range
        assert((new_vm_rec.is_reserve_record() || new_vm_rec.is_commit_record()) &&
          (new_vm_rec.base() == matched_rec->base() && new_vm_rec.size() == matched_rec->size()),
          "Sanity check");
        matched_rec->tag(new_vm_rec.flags());
      }
    } else {
      // no matched record
      if (new_vm_rec.is_reserve_record()) {
        if (matched_rec == NULL || matched_rec->base() > new_vm_rec.base()) {
          if (!vm_snapshot_itr.insert(&new_vm_rec)) {
            return false;
          }
        } else {
          if (!vm_snapshot_itr.insert_after(&new_vm_rec)) {
            return false;
          }
        }
      } else {
        // throw out obsolete records, which are the commit/uncommit/release/tag records
        // on memory regions that are already released.
      }
  }
    new_rec = (MemPointerRecord*)itr->next();
  }
  return true;
}

#ifndef PRODUCT
void MemSnapshot::print_snapshot_stats(outputStream* st) {
  st->print_cr("Snapshot:");
  st->print_cr("\tMalloced: %d/%d [%5.2f%%]  %dKB", _alloc_ptrs->length(), _alloc_ptrs->capacity(),
    (100.0 * (float)_alloc_ptrs->length()) / (float)_alloc_ptrs->capacity(), _alloc_ptrs->instance_size()/K);

  st->print_cr("\tVM: %d/%d [%5.2f%%] %dKB", _vm_ptrs->length(), _vm_ptrs->capacity(),
    (100.0 * (float)_vm_ptrs->length()) / (float)_vm_ptrs->capacity(), _vm_ptrs->instance_size()/K);

  st->print_cr("\tMalloc staging Area:     %d/%d [%5.2f%%] %dKB", _staging_area.malloc_data()->length(),
    _staging_area.malloc_data()->capacity(),
    (100.0 * (float)_staging_area.malloc_data()->length()) / (float)_staging_area.malloc_data()->capacity(),
    _staging_area.malloc_data()->instance_size()/K);

  st->print_cr("\tVirtual memory staging Area:     %d/%d [%5.2f%%] %dKB", _staging_area.vm_data()->length(),
    _staging_area.vm_data()->capacity(),
    (100.0 * (float)_staging_area.vm_data()->length()) / (float)_staging_area.vm_data()->capacity(),
    _staging_area.vm_data()->instance_size()/K);

  st->print_cr("\tUntracked allocation: %d", _untracked_count);
}

void MemSnapshot::check_malloc_pointers() {
  MemPointerArrayIteratorImpl mItr(_alloc_ptrs);
  MemPointerRecord* p = (MemPointerRecord*)mItr.current();
  MemPointerRecord* prev = NULL;
  while (p != NULL) {
    if (prev != NULL) {
      assert(p->addr() >= prev->addr(), "sorting order");
    }
    prev = p;
    p = (MemPointerRecord*)mItr.next();
  }
}

bool MemSnapshot::has_allocation_record(address addr) {
  MemPointerArrayIteratorImpl itr(_staging_area.malloc_data());
  MemPointerRecord* cur = (MemPointerRecord*)itr.current();
  while (cur != NULL) {
    if (cur->addr() == addr && cur->is_allocation_record()) {
      return true;
    }
    cur = (MemPointerRecord*)itr.next();
  }
  return false;
}
#endif // PRODUCT

#ifdef ASSERT
void MemSnapshot::check_staging_data() {
  MemPointerArrayIteratorImpl itr(_staging_area.malloc_data());
  MemPointerRecord* cur = (MemPointerRecord*)itr.current();
  MemPointerRecord* next = (MemPointerRecord*)itr.next();
  while (next != NULL) {
    assert((next->addr() > cur->addr()) ||
      ((next->flags() & MemPointerRecord::tag_masks) >
       (cur->flags() & MemPointerRecord::tag_masks)),
       "sorting order");
    cur = next;
    next = (MemPointerRecord*)itr.next();
  }

  MemPointerArrayIteratorImpl vm_itr(_staging_area.vm_data());
  cur = (MemPointerRecord*)vm_itr.current();
  while (cur != NULL) {
    assert(cur->is_vm_pointer(), "virtual memory pointer only");
    cur = (MemPointerRecord*)vm_itr.next();
  }
}
#endif // ASSERT


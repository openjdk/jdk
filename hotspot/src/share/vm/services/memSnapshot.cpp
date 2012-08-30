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


// stagging data groups the data of a VM memory range, so we can consolidate
// them into one record during the walk
bool StagingWalker::consolidate_vm_records(VMMemRegionEx* vm_rec) {
  MemPointerRecord* cur = (MemPointerRecord*)_itr.current();
  assert(cur != NULL && cur->is_vm_pointer(), "not a virtual memory pointer");

  jint cur_seq;
  jint next_seq;

  bool trackCallsite = MemTracker::track_callsite();

  if (trackCallsite) {
    vm_rec->init((MemPointerRecordEx*)cur);
    cur_seq = ((SeqMemPointerRecordEx*)cur)->seq();
  } else {
    vm_rec->init((MemPointerRecord*)cur);
    cur_seq = ((SeqMemPointerRecord*)cur)->seq();
  }

  // only can consolidate when we have allocation record,
  // which contains virtual memory range
  if (!cur->is_allocation_record()) {
    _itr.next();
    return true;
  }

  // allocation range
  address base = cur->addr();
  address end = base + cur->size();

  MemPointerRecord* next = (MemPointerRecord*)_itr.peek_next();
  // if the memory range is alive
  bool live_vm_rec = true;
  while (next != NULL && next->is_vm_pointer()) {
    if (next->is_allocation_record()) {
      assert(next->addr() >= base, "sorting order or overlapping");
      break;
    }

    if (trackCallsite) {
      next_seq = ((SeqMemPointerRecordEx*)next)->seq();
    } else {
      next_seq = ((SeqMemPointerRecord*)next)->seq();
    }

    if (next_seq < cur_seq) {
      _itr.next();
      next = (MemPointerRecord*)_itr.peek_next();
      continue;
    }

    if (next->is_deallocation_record()) {
      if (next->addr() == base && next->size() == cur->size()) {
        // the virtual memory range has been released
        _itr.next();
        live_vm_rec = false;
        break;
      } else if (next->addr() < end) { // partial release
        vm_rec->partial_release(next->addr(), next->size());
        _itr.next();
      } else {
        break;
      }
    } else if (next->is_commit_record()) {
      if (next->addr() >= base && next->addr() + next->size() <= end) {
        vm_rec->commit(next->size());
        _itr.next();
      } else {
        assert(next->addr() >= base, "sorting order or overlapping");
        break;
      }
    } else if (next->is_uncommit_record()) {
      if (next->addr() >= base && next->addr() + next->size() <= end) {
        vm_rec->uncommit(next->size());
        _itr.next();
      } else {
        assert(next->addr() >= end, "sorting order or overlapping");
        break;
      }
    } else if (next->is_type_tagging_record()) {
      if (next->addr() >= base && next->addr() < end ) {
        vm_rec->tag(next->flags());
        _itr.next();
      } else {
          break;
      }
    } else {
      assert(false, "unknown record type");
    }
    next = (MemPointerRecord*)_itr.peek_next();
  }
  _itr.next();
  return live_vm_rec;
}

MemPointer* StagingWalker::next() {
  MemPointerRecord* cur_p = (MemPointerRecord*)_itr.current();
  if (cur_p == NULL) {
    _end_of_array = true;
    return NULL;
  }

  MemPointerRecord* next_p;
  if (cur_p->is_vm_pointer()) {
    _is_vm_record = true;
    if (!consolidate_vm_records(&_vm_record)) {
      return next();
    }
  } else { // malloc-ed pointer
    _is_vm_record = false;
    next_p = (MemPointerRecord*)_itr.peek_next();
    if (next_p != NULL && next_p->addr() == cur_p->addr()) {
      assert(cur_p->is_allocation_record(), "sorting order");
      assert(!next_p->is_allocation_record(), "sorting order");
      _itr.next();
      if (cur_p->seq() < next_p->seq()) {
        cur_p = next_p;
      }
    }
    if (MemTracker::track_callsite()) {
      _malloc_record.init((MemPointerRecordEx*)cur_p);
    } else {
      _malloc_record.init((MemPointerRecord*)cur_p);
    }

    _itr.next();
  }
  return current();
}

MemSnapshot::MemSnapshot() {
  if (MemTracker::track_callsite()) {
    _alloc_ptrs = new (std::nothrow) MemPointerArrayImpl<MemPointerRecordEx>();
    _vm_ptrs = new (std::nothrow)MemPointerArrayImpl<VMMemRegionEx>(64, true);
    _staging_area = new (std::nothrow)MemPointerArrayImpl<SeqMemPointerRecordEx>();
  } else {
    _alloc_ptrs = new (std::nothrow) MemPointerArrayImpl<MemPointerRecord>();
    _vm_ptrs = new (std::nothrow)MemPointerArrayImpl<VMMemRegion>(64, true);
    _staging_area = new (std::nothrow)MemPointerArrayImpl<SeqMemPointerRecord>();
  }

  _lock = new (std::nothrow) Mutex(Monitor::max_nonleaf - 1, "memSnapshotLock");
  NOT_PRODUCT(_untracked_count = 0;)
}

MemSnapshot::~MemSnapshot() {
  assert(MemTracker::shutdown_in_progress(), "native memory tracking still on");
  {
    MutexLockerEx locker(_lock);
    if (_staging_area != NULL) {
      delete _staging_area;
      _staging_area = NULL;
    }

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

  // out of memory
  if (_staging_area == NULL || _staging_area->out_of_memory()) {
    return false;
  }

  SequencedRecordIterator itr(rec->pointer_itr());

  MutexLockerEx lock(_lock, true);
  MemPointerIterator staging_itr(_staging_area);
  MemPointerRecord *p1, *p2;
  p1 = (MemPointerRecord*) itr.current();
  while (p1 != NULL) {
    p2 = (MemPointerRecord*)staging_itr.locate(p1->addr());
    // we have not seen this memory block, so just add to staging area
    if (p2 == NULL) {
      if (!staging_itr.insert(p1)) {
        return false;
      }
    } else if (p1->addr() == p2->addr()) {
      MemPointerRecord* staging_next = (MemPointerRecord*)staging_itr.peek_next();
      // a memory block can have many tagging records, find right one to replace or
      // right position to insert
      while (staging_next != NULL && staging_next->addr() == p1->addr()) {
        if ((staging_next->flags() & MemPointerRecord::tag_masks) <=
          (p1->flags() & MemPointerRecord::tag_masks)) {
          p2 = (MemPointerRecord*)staging_itr.next();
          staging_next = (MemPointerRecord*)staging_itr.peek_next();
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
        if (!staging_itr.insert(p1)) {
          return false;
        }
      } else {
        if (!staging_itr.insert_after(p1)) {
          return false;
        }
      }
    } else if (p1->addr() < p2->addr()) {
      if (!staging_itr.insert(p1)) {
        return false;
      }
    } else {
      if (!staging_itr.insert_after(p1)) {
        return false;
      }
    }
    p1 = (MemPointerRecord*)itr.next();
  }
  NOT_PRODUCT(void check_staging_data();)
  return true;
}



// promote data to next generation
void MemSnapshot::promote() {
  assert(_alloc_ptrs != NULL && _staging_area != NULL && _vm_ptrs != NULL,
    "Just check");
  MutexLockerEx lock(_lock, true);
  StagingWalker walker(_staging_area);
  MemPointerIterator malloc_itr(_alloc_ptrs);
  VMMemPointerIterator vm_itr(_vm_ptrs);
  MemPointer* cur = walker.current();
  while (cur != NULL) {
    if (walker.is_vm_record()) {
      VMMemRegion* cur_vm = (VMMemRegion*)cur;
      VMMemRegion* p = (VMMemRegion*)vm_itr.locate(cur_vm->addr());
      cur_vm = (VMMemRegion*)cur;
      if (p != NULL && (p->contains(cur_vm) || p->base() == cur_vm->base())) {
        assert(p->is_reserve_record() ||
          p->is_commit_record(), "wrong vm record type");
        // resize existing reserved range
        if (cur_vm->is_reserve_record() && p->base() == cur_vm->base()) {
          assert(cur_vm->size() >= p->committed_size(), "incorrect resizing");
          p->set_reserved_size(cur_vm->size());
        } else if (cur_vm->is_commit_record()) {
          p->commit(cur_vm->committed_size());
        } else if (cur_vm->is_uncommit_record()) {
          p->uncommit(cur_vm->committed_size());
          if (!p->is_reserve_record() && p->committed_size() == 0) {
            vm_itr.remove();
          }
        } else if (cur_vm->is_type_tagging_record()) {
          p->tag(cur_vm->flags());
        } else if (cur_vm->is_release_record()) {
          if (cur_vm->base() == p->base() && cur_vm->size() == p->size()) {
            // release the whole range
            vm_itr.remove();
          } else {
            // partial release
            p->partial_release(cur_vm->base(), cur_vm->size());
          }
        } else {
          // we do see multiple reserver on the same vm range
          assert((cur_vm->is_commit_record() || cur_vm->is_reserve_record()) &&
             cur_vm->base() == p->base() && cur_vm->size() == p->size(), "bad record");
          p->tag(cur_vm->flags());
        }
      } else {
        if(cur_vm->is_reserve_record()) {
          if (p == NULL || p->base() > cur_vm->base()) {
            vm_itr.insert(cur_vm);
          } else {
            vm_itr.insert_after(cur_vm);
          }
        } else {
          // In theory, we should assert without conditions. However, in case of native
          // thread stack, NMT explicitly releases the thread stack in Thread's destructor,
          // due to platform dependent behaviors. On some platforms, we see uncommit/release
          // native thread stack, but some, we don't.
          assert(cur_vm->is_uncommit_record() || cur_vm->is_deallocation_record(),
            err_msg("Should not reach here, pointer addr = [" INTPTR_FORMAT "], flags = [%x]",
               cur_vm->addr(), cur_vm->flags()));
        }
      }
    } else {
      MemPointerRecord* cur_p = (MemPointerRecord*)cur;
      MemPointerRecord* p = (MemPointerRecord*)malloc_itr.locate(cur->addr());
      if (p != NULL && cur_p->addr() == p->addr()) {
        assert(p->is_allocation_record() || p->is_arena_size_record(), "untracked");
        if (cur_p->is_allocation_record() || cur_p->is_arena_size_record()) {
          copy_pointer(p, cur_p);
        } else {   // deallocation record
          assert(cur_p->is_deallocation_record(), "wrong record type");

          // we are removing an arena record, we also need to remove its 'size'
          // record behind it
          if (p->is_arena_record()) {
            MemPointerRecord* next_p = (MemPointerRecord*)malloc_itr.peek_next();
            if (next_p->is_arena_size_record()) {
              assert(next_p->is_size_record_of_arena(p), "arena records dont match");
              malloc_itr.remove();
            }
          }
          malloc_itr.remove();
        }
      } else {
        if (cur_p->is_arena_size_record()) {
          MemPointerRecord* prev_p = (MemPointerRecord*)malloc_itr.peek_prev();
          if (prev_p != NULL &&
             (!prev_p->is_arena_record() || !cur_p->is_size_record_of_arena(prev_p))) {
            // arena already deallocated
            cur_p = NULL;
          }
        }
        if (cur_p != NULL) {
          if (cur_p->is_allocation_record() || cur_p->is_arena_size_record()) {
            if (p != NULL && cur_p->addr() > p->addr()) {
              malloc_itr.insert_after(cur);
            } else {
              malloc_itr.insert(cur);
            }
          }
#ifndef PRODUCT
          else if (!has_allocation_record(cur_p->addr())){
            // NMT can not track some startup memory, which allocated before NMT
            // is enabled
            _untracked_count ++;
          }
#endif
        }
      }
    }

    cur = walker.next();
  }
  NOT_PRODUCT(check_malloc_pointers();)
  _staging_area->shrink();
  _staging_area->clear();
}


#ifndef PRODUCT
void MemSnapshot::print_snapshot_stats(outputStream* st) {
  st->print_cr("Snapshot:");
  st->print_cr("\tMalloced: %d/%d [%5.2f%%]  %dKB", _alloc_ptrs->length(), _alloc_ptrs->capacity(),
    (100.0 * (float)_alloc_ptrs->length()) / (float)_alloc_ptrs->capacity(), _alloc_ptrs->instance_size()/K);

  st->print_cr("\tVM: %d/%d [%5.2f%%] %dKB", _vm_ptrs->length(), _vm_ptrs->capacity(),
    (100.0 * (float)_vm_ptrs->length()) / (float)_vm_ptrs->capacity(), _vm_ptrs->instance_size()/K);

  st->print_cr("\tStaging:     %d/%d [%5.2f%%] %dKB", _staging_area->length(), _staging_area->capacity(),
    (100.0 * (float)_staging_area->length()) / (float)_staging_area->capacity(), _staging_area->instance_size()/K);

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
  MemPointerArrayIteratorImpl itr(_staging_area);
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
  MemPointerArrayIteratorImpl itr(_staging_area);
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
}
#endif // ASSERT


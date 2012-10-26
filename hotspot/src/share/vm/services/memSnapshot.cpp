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


bool VMMemPointerIterator::insert_record(MemPointerRecord* rec) {
  VMMemRegionEx new_rec;
  assert(rec->is_allocation_record() || rec->is_commit_record(),
    "Sanity check");
  if (MemTracker::track_callsite()) {
    new_rec.init((MemPointerRecordEx*)rec);
  } else {
    new_rec.init(rec);
  }
  return insert(&new_rec);
}

bool VMMemPointerIterator::insert_record_after(MemPointerRecord* rec) {
  VMMemRegionEx new_rec;
  assert(rec->is_allocation_record() || rec->is_commit_record(),
    "Sanity check");
  if (MemTracker::track_callsite()) {
    new_rec.init((MemPointerRecordEx*)rec);
  } else {
    new_rec.init(rec);
  }
  return insert_after(&new_rec);
}

// we don't consolidate reserved regions, since they may be categorized
// in different types.
bool VMMemPointerIterator::add_reserved_region(MemPointerRecord* rec) {
  assert(rec->is_allocation_record(), "Sanity check");
  VMMemRegion* cur = (VMMemRegion*)current();

  // we don't have anything yet
  if (cur == NULL) {
    return insert_record(rec);
  }

  assert(cur->is_reserved_region(), "Sanity check");
  // duplicated records
  if (cur->is_same_region(rec)) {
    return true;
  }
  assert(cur->base() > rec->addr(), "Just check: locate()");
  assert(rec->addr() + rec->size() <= cur->base(), "Can not overlap");
  return insert_record(rec);
}

// we do consolidate committed regions
bool VMMemPointerIterator::add_committed_region(MemPointerRecord* rec) {
  assert(rec->is_commit_record(), "Sanity check");
  VMMemRegion* cur;
  cur = (VMMemRegion*)current();
  assert(cur->is_reserved_region() && cur->contains_region(rec),
    "Sanity check");

  // thread's native stack is always marked as "committed", ignore
  // the "commit" operation for creating stack guard pages
  if (FLAGS_TO_MEMORY_TYPE(cur->flags()) == mtThreadStack &&
      FLAGS_TO_MEMORY_TYPE(rec->flags()) != mtThreadStack) {
    return true;
  }

  cur = (VMMemRegion*)next();
  while (cur != NULL && cur->is_committed_region()) {
    // duplicated commit records
    if(cur->contains_region(rec)) {
      return true;
    }
    if (cur->base() > rec->addr()) {
      // committed regions can not overlap
      assert(rec->addr() + rec->size() <= cur->base(), "Can not overlap");
      if (rec->addr() + rec->size() == cur->base()) {
        cur->expand_region(rec->addr(), rec->size());
        return true;
      } else {
        return insert_record(rec);
      }
    } else if (cur->base() + cur->size() == rec->addr()) {
      cur->expand_region(rec->addr(), rec->size());
      VMMemRegion* next_reg = (VMMemRegion*)next();
      // see if we can consolidate next committed region
      if (next_reg != NULL && next_reg->is_committed_region() &&
        next_reg->base() == cur->base() + cur->size()) {
          cur->expand_region(next_reg->base(), next_reg->size());
          remove();
      }
      return true;
    }
    cur = (VMMemRegion*)next();
  }
  return insert_record(rec);
}

bool VMMemPointerIterator::remove_uncommitted_region(MemPointerRecord* rec) {
  assert(rec->is_uncommit_record(), "sanity check");
  VMMemRegion* cur;
  cur = (VMMemRegion*)current();
  assert(cur->is_reserved_region() && cur->contains_region(rec),
    "Sanity check");
  // thread's native stack is always marked as "committed", ignore
  // the "commit" operation for creating stack guard pages
  if (FLAGS_TO_MEMORY_TYPE(cur->flags()) == mtThreadStack &&
      FLAGS_TO_MEMORY_TYPE(rec->flags()) != mtThreadStack) {
    return true;
  }

  cur = (VMMemRegion*)next();
  while (cur != NULL && cur->is_committed_region()) {
    // region already uncommitted, must be due to duplicated record
    if (cur->addr() >= rec->addr() + rec->size()) {
      break;
    } else if (cur->contains_region(rec)) {
      // uncommit whole region
      if (cur->is_same_region(rec)) {
        remove();
        break;
      } else if (rec->addr() == cur->addr() ||
        rec->addr() + rec->size() == cur->addr() + cur->size()) {
        // uncommitted from either end of current memory region.
        cur->exclude_region(rec->addr(), rec->size());
        break;
      } else { // split the committed region and release the middle
        address high_addr = cur->addr() + cur->size();
        size_t sz = high_addr - rec->addr();
        cur->exclude_region(rec->addr(), sz);
        sz = high_addr - (rec->addr() + rec->size());
        if (MemTracker::track_callsite()) {
          MemPointerRecordEx tmp(rec->addr() + rec->size(), cur->flags(), sz,
             ((VMMemRegionEx*)cur)->pc());
          return insert_record_after(&tmp);
        } else {
          MemPointerRecord tmp(rec->addr() + rec->size(), cur->flags(), sz);
          return insert_record_after(&tmp);
        }
      }
    }
    cur = (VMMemRegion*)next();
  }

  // we may not find committed record due to duplicated records
  return true;
}

bool VMMemPointerIterator::remove_released_region(MemPointerRecord* rec) {
  assert(rec->is_deallocation_record(), "Sanity check");
  VMMemRegion* cur = (VMMemRegion*)current();
  assert(cur->is_reserved_region() && cur->contains_region(rec),
    "Sanity check");
#ifdef ASSERT
  VMMemRegion* next_reg = (VMMemRegion*)peek_next();
  // should not have any committed memory in this reserved region
  assert(next_reg == NULL || !next_reg->is_committed_region(), "Sanity check");
#endif
  if (rec->is_same_region(cur)) {
    remove();
  } else if (rec->addr() == cur->addr() ||
    rec->addr() + rec->size() == cur->addr() + cur->size()) {
    // released region is at either end of this region
    cur->exclude_region(rec->addr(), rec->size());
  } else { // split the reserved region and release the middle
    address high_addr = cur->addr() + cur->size();
    size_t sz = high_addr - rec->addr();
    cur->exclude_region(rec->addr(), sz);
    sz = high_addr - rec->addr() - rec->size();
    if (MemTracker::track_callsite()) {
      MemPointerRecordEx tmp(rec->addr() + rec->size(), cur->flags(), sz,
        ((VMMemRegionEx*)cur)->pc());
      return insert_reserved_region(&tmp);
    } else {
      MemPointerRecord tmp(rec->addr() + rec->size(), cur->flags(), sz);
      return insert_reserved_region(&tmp);
    }
  }
  return true;
}

bool VMMemPointerIterator::insert_reserved_region(MemPointerRecord* rec) {
  // skip all 'commit' records associated with previous reserved region
  VMMemRegion* p = (VMMemRegion*)next();
  while (p != NULL && p->is_committed_region() &&
         p->base() + p->size() < rec->addr()) {
    p = (VMMemRegion*)next();
  }
  return insert_record(rec);
}

bool VMMemPointerIterator::split_reserved_region(VMMemRegion* rgn, address new_rgn_addr, size_t new_rgn_size) {
  assert(rgn->contains_region(new_rgn_addr, new_rgn_size), "Not fully contained");
  address pc = (MemTracker::track_callsite() ? ((VMMemRegionEx*)rgn)->pc() : NULL);
  if (rgn->base() == new_rgn_addr) { // new region is at the beginning of the region
    size_t sz = rgn->size() - new_rgn_size;
    // the original region becomes 'new' region
    rgn->exclude_region(new_rgn_addr + new_rgn_size, sz);
     // remaining becomes next region
    MemPointerRecordEx next_rgn(new_rgn_addr + new_rgn_size, rgn->flags(), sz, pc);
    return insert_reserved_region(&next_rgn);
  } else if (rgn->base() + rgn->size() == new_rgn_addr + new_rgn_size) {
    rgn->exclude_region(new_rgn_addr, new_rgn_size);
    MemPointerRecordEx next_rgn(new_rgn_addr, rgn->flags(), new_rgn_size, pc);
    return insert_reserved_region(&next_rgn);
  } else {
    // the orginal region will be split into three
    address rgn_high_addr = rgn->base() + rgn->size();
    // first region
    rgn->exclude_region(new_rgn_addr, (rgn_high_addr - new_rgn_addr));
    // the second region is the new region
    MemPointerRecordEx new_rgn(new_rgn_addr, rgn->flags(), new_rgn_size, pc);
    if (!insert_reserved_region(&new_rgn)) return false;
    // the remaining region
    MemPointerRecordEx rem_rgn(new_rgn_addr + new_rgn_size, rgn->flags(),
      rgn_high_addr - (new_rgn_addr + new_rgn_size), pc);
    return insert_reserved_region(&rem_rgn);
  }
}

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


VMRecordIterator StagingArea::virtual_memory_record_walker() {
  MemPointerArray* arr = vm_data();
  // sort into seq number order
  arr->sort((FN_SORT)sort_in_seq_order);
  return VMRecordIterator(arr);
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
      // locate matched record and/or also position the iterator to proper
      // location for this incoming record.
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
    VMRecordIterator vm_itr = _staging_area.virtual_memory_record_walker();
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
      // snapshot already contains 'live' records
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
  VMMemRegion*  reserved_rec;
  while (new_rec != NULL) {
    assert(new_rec->is_vm_pointer(), "Sanity check");

    // locate a reserved region that contains the specified address, or
    // the nearest reserved region has base address just above the specified
    // address
    reserved_rec = (VMMemRegion*)vm_snapshot_itr.locate(new_rec->addr());
    if (reserved_rec != NULL && reserved_rec->contains_region(new_rec)) {
      // snapshot can only have 'live' records
      assert(reserved_rec->is_reserved_region(), "Sanity check");
      if (new_rec->is_allocation_record()) {
        if (!reserved_rec->is_same_region(new_rec)) {
          // only deal with split a bigger reserved region into smaller regions.
          // So far, CDS is the only use case.
          if (!vm_snapshot_itr.split_reserved_region(reserved_rec, new_rec->addr(), new_rec->size())) {
            return false;
          }
        }
      } else if (new_rec->is_uncommit_record()) {
        if (!vm_snapshot_itr.remove_uncommitted_region(new_rec)) {
          return false;
        }
      } else if (new_rec->is_commit_record()) {
        // insert or expand existing committed region to cover this
        // newly committed region
        if (!vm_snapshot_itr.add_committed_region(new_rec)) {
          return false;
        }
      } else if (new_rec->is_deallocation_record()) {
        // release part or all memory region
        if (!vm_snapshot_itr.remove_released_region(new_rec)) {
          return false;
        }
      } else if (new_rec->is_type_tagging_record()) {
        // tag this reserved virtual memory range to a memory type. Can not re-tag a memory range
        // to different type.
        assert(FLAGS_TO_MEMORY_TYPE(reserved_rec->flags()) == mtNone ||
               FLAGS_TO_MEMORY_TYPE(reserved_rec->flags()) == FLAGS_TO_MEMORY_TYPE(new_rec->flags()),
               "Sanity check");
        reserved_rec->tag(new_rec->flags());
    } else {
        ShouldNotReachHere();
          }
        } else {
      /*
       * The assertion failure indicates mis-matched virtual memory records. The likely
       * scenario is, that some virtual memory operations are not going through os::xxxx_memory()
       * api, which have to be tracked manually. (perfMemory is an example).
      */
      assert(new_rec->is_allocation_record(), "Sanity check");
      if (!vm_snapshot_itr.add_reserved_region(new_rec)) {
            return false;
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

void MemSnapshot::dump_all_vm_pointers() {
  MemPointerArrayIteratorImpl itr(_vm_ptrs);
  VMMemRegion* ptr = (VMMemRegion*)itr.current();
  tty->print_cr("dump virtual memory pointers:");
  while (ptr != NULL) {
    if (ptr->is_committed_region()) {
      tty->print("\t");
    }
    tty->print("[" PTR_FORMAT " - " PTR_FORMAT "] [%x]", ptr->addr(),
      (ptr->addr() + ptr->size()), ptr->flags());

    if (MemTracker::track_callsite()) {
      VMMemRegionEx* ex = (VMMemRegionEx*)ptr;
      if (ex->pc() != NULL) {
        char buf[1024];
        if (os::dll_address_to_function_name(ex->pc(), buf, sizeof(buf), NULL)) {
          tty->print_cr("\t%s", buf);
        } else {
          tty->print_cr("");
        }
      }
    }

    ptr = (VMMemRegion*)itr.next();
  }
  tty->flush();
}
#endif // ASSERT


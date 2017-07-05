/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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

PRAGMA_FORMAT_MUTE_WARNINGS_FOR_GCC

#ifdef ASSERT

void decode_pointer_record(MemPointerRecord* rec) {
  tty->print("Pointer: [" PTR_FORMAT " - " PTR_FORMAT  "] size = %d bytes", rec->addr(),
    rec->addr() + rec->size(), (int)rec->size());
  tty->print(" type = %s", MemBaseline::type2name(FLAGS_TO_MEMORY_TYPE(rec->flags())));
  if (rec->is_vm_pointer()) {
    if (rec->is_allocation_record()) {
      tty->print_cr(" (reserve)");
    } else if (rec->is_commit_record()) {
      tty->print_cr(" (commit)");
    } else if (rec->is_uncommit_record()) {
      tty->print_cr(" (uncommit)");
    } else if (rec->is_deallocation_record()) {
      tty->print_cr(" (release)");
    } else {
      tty->print_cr(" (tag)");
    }
  } else {
    if (rec->is_arena_memory_record()) {
      tty->print_cr(" (arena size)");
    } else if (rec->is_allocation_record()) {
      tty->print_cr(" (malloc)");
    } else {
      tty->print_cr(" (free)");
    }
  }
  if (MemTracker::track_callsite()) {
    char buf[1024];
    address pc = ((MemPointerRecordEx*)rec)->pc();
    if (pc != NULL && os::dll_address_to_function_name(pc, buf, sizeof(buf), NULL)) {
      tty->print_cr("\tfrom %s", buf);
    } else {
      tty->print_cr("\tcould not decode pc = " PTR_FORMAT "", pc);
    }
  }
}

void decode_vm_region_record(VMMemRegion* rec) {
  tty->print("VM Region [" PTR_FORMAT " - " PTR_FORMAT "]", rec->addr(),
    rec->addr() + rec->size());
  tty->print(" type = %s", MemBaseline::type2name(FLAGS_TO_MEMORY_TYPE(rec->flags())));
  if (rec->is_allocation_record()) {
    tty->print_cr(" (reserved)");
  } else if (rec->is_commit_record()) {
    tty->print_cr(" (committed)");
  } else {
    ShouldNotReachHere();
  }
  if (MemTracker::track_callsite()) {
    char buf[1024];
    address pc = ((VMMemRegionEx*)rec)->pc();
    if (pc != NULL && os::dll_address_to_function_name(pc, buf, sizeof(buf), NULL)) {
      tty->print_cr("\tfrom %s", buf);
    } else {
      tty->print_cr("\tcould not decode pc = " PTR_FORMAT "", pc);
    }

  }
}

#endif


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
  VMMemRegion* reserved_region = (VMMemRegion*)current();

  // we don't have anything yet
  if (reserved_region == NULL) {
    return insert_record(rec);
  }

  assert(reserved_region->is_reserved_region(), "Sanity check");
  // duplicated records
  if (reserved_region->is_same_region(rec)) {
    return true;
  }
  // Overlapping stack regions indicate that a JNI thread failed to
  // detach from the VM before exiting. This leaks the JavaThread object.
  if (CheckJNICalls)  {
      guarantee(FLAGS_TO_MEMORY_TYPE(reserved_region->flags()) != mtThreadStack ||
         !reserved_region->overlaps_region(rec),
         "Attached JNI thread exited without being detached");
  }
  // otherwise, we should not have overlapping reserved regions
  assert(FLAGS_TO_MEMORY_TYPE(reserved_region->flags()) == mtThreadStack ||
    reserved_region->base() > rec->addr(), "Just check: locate()");
  assert(FLAGS_TO_MEMORY_TYPE(reserved_region->flags()) == mtThreadStack ||
    !reserved_region->overlaps_region(rec), "overlapping reserved regions");

  return insert_record(rec);
}

// we do consolidate committed regions
bool VMMemPointerIterator::add_committed_region(MemPointerRecord* rec) {
  assert(rec->is_commit_record(), "Sanity check");
  VMMemRegion* reserved_rgn = (VMMemRegion*)current();
  assert(reserved_rgn->is_reserved_region() && reserved_rgn->contains_region(rec),
    "Sanity check");

  // thread's native stack is always marked as "committed", ignore
  // the "commit" operation for creating stack guard pages
  if (FLAGS_TO_MEMORY_TYPE(reserved_rgn->flags()) == mtThreadStack &&
      FLAGS_TO_MEMORY_TYPE(rec->flags()) != mtThreadStack) {
    return true;
  }

  // if the reserved region has any committed regions
  VMMemRegion* committed_rgn  = (VMMemRegion*)next();
  while (committed_rgn != NULL && committed_rgn->is_committed_region()) {
    // duplicated commit records
    if(committed_rgn->contains_region(rec)) {
      return true;
    } else if (committed_rgn->overlaps_region(rec)) {
      // overlaps front part
      if (rec->addr() < committed_rgn->addr()) {
        committed_rgn->expand_region(rec->addr(),
          committed_rgn->addr() - rec->addr());
      } else {
        // overlaps tail part
        address committed_rgn_end = committed_rgn->addr() +
              committed_rgn->size();
        assert(committed_rgn_end < rec->addr() + rec->size(),
             "overlap tail part");
        committed_rgn->expand_region(committed_rgn_end,
          (rec->addr() + rec->size()) - committed_rgn_end);
      }
    } else if (committed_rgn->base() + committed_rgn->size() == rec->addr()) {
      // adjunct each other
      committed_rgn->expand_region(rec->addr(), rec->size());
      VMMemRegion* next_reg = (VMMemRegion*)next();
      // see if we can consolidate next committed region
      if (next_reg != NULL && next_reg->is_committed_region() &&
        next_reg->base() == committed_rgn->base() + committed_rgn->size()) {
          committed_rgn->expand_region(next_reg->base(), next_reg->size());
          // delete merged region
          remove();
      }
      return true;
    } else if (committed_rgn->base() > rec->addr()) {
      // found the location, insert this committed region
      return insert_record(rec);
    }
    committed_rgn = (VMMemRegion*)next();
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
  if (rec->is_same_region(cur)) {

    // In snapshot, the virtual memory records are sorted in following orders:
    // 1. virtual memory's base address
    // 2. virtual memory reservation record, followed by commit records within this reservation.
    //    The commit records are also in base address order.
    // When a reserved region is released, we want to remove the reservation record and all
    // commit records following it.
#ifdef ASSERT
    address low_addr = cur->addr();
    address high_addr = low_addr + cur->size();
#endif
    // remove virtual memory reservation record
    remove();
    // remove committed regions within above reservation
    VMMemRegion* next_region = (VMMemRegion*)current();
    while (next_region != NULL && next_region->is_committed_region()) {
      assert(next_region->addr() >= low_addr &&
             next_region->addr() + next_region->size() <= high_addr,
            "Range check");
      remove();
      next_region = (VMMemRegion*)current();
    }
  } else if (rec->addr() == cur->addr() ||
    rec->addr() + rec->size() == cur->addr() + cur->size()) {
    // released region is at either end of this region
    cur->exclude_region(rec->addr(), rec->size());
    assert(check_reserved_region(), "Integrity check");
  } else { // split the reserved region and release the middle
    address high_addr = cur->addr() + cur->size();
    size_t sz = high_addr - rec->addr();
    cur->exclude_region(rec->addr(), sz);
    sz = high_addr - rec->addr() - rec->size();
    if (MemTracker::track_callsite()) {
      MemPointerRecordEx tmp(rec->addr() + rec->size(), cur->flags(), sz,
        ((VMMemRegionEx*)cur)->pc());
      bool ret = insert_reserved_region(&tmp);
      assert(!ret || check_reserved_region(), "Integrity check");
      return ret;
    } else {
      MemPointerRecord tmp(rec->addr() + rec->size(), cur->flags(), sz);
      bool ret = insert_reserved_region(&tmp);
      assert(!ret || check_reserved_region(), "Integrity check");
      return ret;
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
  _number_of_classes = 0;
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


void MemSnapshot::copy_seq_pointer(MemPointerRecord* dest, const MemPointerRecord* src) {
  assert(dest != NULL && src != NULL, "Just check");
  assert(dest->addr() == src->addr(), "Just check");
  assert(dest->seq() > 0 && src->seq() > 0, "not sequenced");

  if (MemTracker::track_callsite()) {
    *(SeqMemPointerRecordEx*)dest = *(SeqMemPointerRecordEx*)src;
  } else {
    *(SeqMemPointerRecord*)dest = *(SeqMemPointerRecord*)src;
  }
}

void MemSnapshot::assign_pointer(MemPointerRecord*dest, const MemPointerRecord* src) {
  assert(src != NULL && dest != NULL, "Just check");
  assert(dest->seq() == 0 && src->seq() >0, "cast away sequence");

  if (MemTracker::track_callsite()) {
    *(MemPointerRecordEx*)dest = *(MemPointerRecordEx*)src;
  } else {
    *(MemPointerRecord*)dest = *(MemPointerRecord*)src;
  }
}

// merge a recorder to the staging area
bool MemSnapshot::merge(MemRecorder* rec) {
  assert(rec != NULL && !rec->out_of_memory(), "Just check");

  SequencedRecordIterator itr(rec->pointer_itr());

  MutexLockerEx lock(_lock, true);
  MemPointerIterator malloc_staging_itr(_staging_area.malloc_data());
  MemPointerRecord* incoming_rec = (MemPointerRecord*) itr.current();
  MemPointerRecord* matched_rec;

  while (incoming_rec != NULL) {
    if (incoming_rec->is_vm_pointer()) {
      // we don't do anything with virtual memory records during merge
      if (!_staging_area.vm_data()->append(incoming_rec)) {
        return false;
      }
    } else {
      // locate matched record and/or also position the iterator to proper
      // location for this incoming record.
      matched_rec = (MemPointerRecord*)malloc_staging_itr.locate(incoming_rec->addr());
      // we have not seen this memory block in this generation,
      // so just add to staging area
      if (matched_rec == NULL) {
        if (!malloc_staging_itr.insert(incoming_rec)) {
          return false;
        }
      } else if (incoming_rec->addr() == matched_rec->addr()) {
        // whoever has higher sequence number wins
        if (incoming_rec->seq() > matched_rec->seq()) {
          copy_seq_pointer(matched_rec, incoming_rec);
        }
      } else if (incoming_rec->addr() < matched_rec->addr()) {
        if (!malloc_staging_itr.insert(incoming_rec)) {
          return false;
        }
      } else {
        ShouldNotReachHere();
      }
    }
    incoming_rec = (MemPointerRecord*)itr.next();
  }
  NOT_PRODUCT(void check_staging_data();)
  return true;
}


// promote data to next generation
bool MemSnapshot::promote(int number_of_classes) {
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
  _number_of_classes = number_of_classes;
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
      assert(matched_rec->is_allocation_record() || matched_rec->is_arena_memory_record(),
             "Sanity check");
      // update block states
      if (new_rec->is_allocation_record()) {
        assign_pointer(matched_rec, new_rec);
      } else if (new_rec->is_arena_memory_record()) {
        if (new_rec->size() == 0) {
          // remove size record once size drops to 0
          malloc_snapshot_itr.remove();
        } else {
          assign_pointer(matched_rec, new_rec);
        }
      } else {
        // a deallocation record
        assert(new_rec->is_deallocation_record(), "Sanity check");
        // an arena record can be followed by a size record, we need to remove both
        if (matched_rec->is_arena_record()) {
          MemPointerRecord* next = (MemPointerRecord*)malloc_snapshot_itr.peek_next();
          if (next != NULL && next->is_arena_memory_record() &&
              next->is_memory_record_of_arena(matched_rec)) {
            malloc_snapshot_itr.remove();
          }
        }
        // the memory is deallocated, remove related record(s)
        malloc_snapshot_itr.remove();
      }
    } else {
      // don't insert size 0 record
      if (new_rec->is_arena_memory_record() && new_rec->size() == 0) {
        new_rec = NULL;
      }

      if (new_rec != NULL) {
        if  (new_rec->is_allocation_record() || new_rec->is_arena_memory_record()) {
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
          tty->cr();
        }
      }
    }

    ptr = (VMMemRegion*)itr.next();
  }
  tty->flush();
}
#endif // ASSERT


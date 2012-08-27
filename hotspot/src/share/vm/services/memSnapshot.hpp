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

#ifndef SHARE_VM_SERVICES_MEM_SNAPSHOT_HPP
#define SHARE_VM_SERVICES_MEM_SNAPSHOT_HPP

#include "memory/allocation.hpp"
#include "runtime/mutex.hpp"
#include "runtime/mutexLocker.hpp"
#include "services/memBaseline.hpp"
#include "services/memPtrArray.hpp"


// Snapshot pointer array iterator

// The pointer array contains malloc-ed pointers
class MemPointerIterator : public MemPointerArrayIteratorImpl {
 public:
  MemPointerIterator(MemPointerArray* arr):
    MemPointerArrayIteratorImpl(arr) {
    assert(arr != NULL, "null array");
  }

#ifdef ASSERT
  virtual bool is_dup_pointer(const MemPointer* ptr1,
    const MemPointer* ptr2) const {
    MemPointerRecord* p1 = (MemPointerRecord*)ptr1;
    MemPointerRecord* p2 = (MemPointerRecord*)ptr2;

    if (p1->addr() != p2->addr()) return false;
    if ((p1->flags() & MemPointerRecord::tag_masks) !=
        (p2->flags() & MemPointerRecord::tag_masks)) {
      return false;
    }
    // we do see multiple commit/uncommit on the same memory, it is ok
    return (p1->flags() & MemPointerRecord::tag_masks) == MemPointerRecord::tag_alloc ||
           (p1->flags() & MemPointerRecord::tag_masks) == MemPointerRecord::tag_release;
  }

  virtual bool insert(MemPointer* ptr) {
    if (_pos > 0) {
      MemPointer* p1 = (MemPointer*)ptr;
      MemPointer* p2 = (MemPointer*)_array->at(_pos - 1);
      assert(!is_dup_pointer(p1, p2),
        err_msg("duplicated pointer, flag = [%x]", (unsigned int)((MemPointerRecord*)p1)->flags()));
    }
     if (_pos < _array->length() -1) {
      MemPointer* p1 = (MemPointer*)ptr;
      MemPointer* p2 = (MemPointer*)_array->at(_pos + 1);
      assert(!is_dup_pointer(p1, p2),
        err_msg("duplicated pointer, flag = [%x]", (unsigned int)((MemPointerRecord*)p1)->flags()));
     }
    return _array->insert_at(ptr, _pos);
  }

  virtual bool insert_after(MemPointer* ptr) {
    if (_pos > 0) {
      MemPointer* p1 = (MemPointer*)ptr;
      MemPointer* p2 = (MemPointer*)_array->at(_pos - 1);
      assert(!is_dup_pointer(p1, p2),
        err_msg("duplicated pointer, flag = [%x]", (unsigned int)((MemPointerRecord*)p1)->flags()));
    }
    if (_pos < _array->length() - 1) {
      MemPointer* p1 = (MemPointer*)ptr;
      MemPointer* p2 = (MemPointer*)_array->at(_pos + 1);

      assert(!is_dup_pointer(p1, p2),
        err_msg("duplicated pointer, flag = [%x]", (unsigned int)((MemPointerRecord*)p1)->flags()));
     }
    if (_array->insert_at(ptr, _pos + 1)) {
      _pos ++;
      return true;
    }
    return false;
  }
#endif

  virtual MemPointer* locate(address addr) {
    MemPointer* cur = current();
    while (cur != NULL && cur->addr() < addr) {
      cur = next();
    }
    return cur;
  }
};

class VMMemPointerIterator : public MemPointerIterator {
 public:
  VMMemPointerIterator(MemPointerArray* arr):
      MemPointerIterator(arr) {
  }

  // locate an exiting record that contains specified address, or
  // the record, where the record with specified address, should
  // be inserted
  virtual MemPointer* locate(address addr) {
    VMMemRegion* cur = (VMMemRegion*)current();
    VMMemRegion* next_p;

    while (cur != NULL) {
      if (cur->base() > addr) {
        return cur;
      } else {
        // find nearest existing range that has base address <= addr
        next_p = (VMMemRegion*)peek_next();
        if (next_p != NULL && next_p->base() <= addr) {
          cur = (VMMemRegion*)next();
          continue;
        }
      }

      if (cur->is_reserve_record() &&
        cur->base() <= addr &&
        (cur->base() + cur->size() > addr)) {
          return cur;
      } else if (cur->is_commit_record() &&
        cur->base() <= addr &&
        (cur->base() + cur->committed_size() > addr)) {
          return cur;
      }
      cur = (VMMemRegion*)next();
    }
    return NULL;
  }

#ifdef ASSERT
  virtual bool is_dup_pointer(const MemPointer* ptr1,
    const MemPointer* ptr2) const {
    VMMemRegion* p1 = (VMMemRegion*)ptr1;
    VMMemRegion* p2 = (VMMemRegion*)ptr2;

    if (p1->addr() != p2->addr()) return false;
    if ((p1->flags() & MemPointerRecord::tag_masks) !=
        (p2->flags() & MemPointerRecord::tag_masks)) {
      return false;
    }
    // we do see multiple commit/uncommit on the same memory, it is ok
    return (p1->flags() & MemPointerRecord::tag_masks) == MemPointerRecord::tag_alloc ||
           (p1->flags() & MemPointerRecord::tag_masks) == MemPointerRecord::tag_release;
  }
#endif
};

class StagingWalker : public MemPointerArrayIterator {
 private:
  MemPointerArrayIteratorImpl  _itr;
  bool                         _is_vm_record;
  bool                         _end_of_array;
  VMMemRegionEx                _vm_record;
  MemPointerRecordEx           _malloc_record;

 public:
  StagingWalker(MemPointerArray* arr): _itr(arr) {
    _end_of_array = false;
    next();
  }

  // return the pointer at current position
  MemPointer* current() const {
    if (_end_of_array) {
      return NULL;
    }
    if (is_vm_record()) {
      return (MemPointer*)&_vm_record;
    } else {
      return (MemPointer*)&_malloc_record;
    }
  }

  // return the next pointer and advance current position
  MemPointer* next();

  // type of 'current' record
  bool is_vm_record() const {
    return _is_vm_record;
  }

  // return the next poinger without advancing current position
  MemPointer* peek_next() const {
    assert(false, "not supported");
    return NULL;
  }

  MemPointer* peek_prev() const {
    assert(false, "not supported");
    return NULL;
  }
  // remove the pointer at current position
  void remove() {
    assert(false, "not supported");
  }

  // insert the pointer at current position
  bool insert(MemPointer* ptr) {
    assert(false, "not supported");
    return false;
  }

  bool insert_after(MemPointer* ptr) {
    assert(false, "not supported");
    return false;
  }

 private:
  // consolidate all records referring to this vm region
  bool consolidate_vm_records(VMMemRegionEx* vm_rec);
};

class MemBaseline;

class MemSnapshot : public CHeapObj<mtNMT> {
 private:
  // the following two arrays contain records of all known lived memory blocks
  // live malloc-ed memory pointers
  MemPointerArray*      _alloc_ptrs;
  // live virtual memory pointers
  MemPointerArray*      _vm_ptrs;

  // stagging a generation's data, before
  // it can be prompted to snapshot
  MemPointerArray*      _staging_area;

  // the lock to protect this snapshot
  Monitor*              _lock;

  NOT_PRODUCT(size_t    _untracked_count;)
  friend class MemBaseline;

 public:
  MemSnapshot();
  virtual ~MemSnapshot();

  // if we are running out of native memory
  bool out_of_memory() const {
    return (_alloc_ptrs == NULL || _staging_area == NULL ||
      _vm_ptrs == NULL || _lock == NULL ||
      _alloc_ptrs->out_of_memory() ||
      _staging_area->out_of_memory() ||
      _vm_ptrs->out_of_memory());
  }

  // merge a per-thread memory recorder into staging area
  bool merge(MemRecorder* rec);
  // promote staged data to snapshot
  void promote();


  void wait(long timeout) {
    assert(_lock != NULL, "Just check");
    MonitorLockerEx locker(_lock);
    locker.wait(true, timeout);
  }

  NOT_PRODUCT(void print_snapshot_stats(outputStream* st);)
  NOT_PRODUCT(void check_staging_data();)
  NOT_PRODUCT(void check_malloc_pointers();)
  NOT_PRODUCT(bool has_allocation_record(address addr);)

 private:
   // copy pointer data from src to dest
   void copy_pointer(MemPointerRecord* dest, const MemPointerRecord* src);
};


#endif // SHARE_VM_SERVICES_MEM_SNAPSHOT_HPP

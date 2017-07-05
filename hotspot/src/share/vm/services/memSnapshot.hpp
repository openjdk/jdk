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

  // locate an existing record that contains specified address, or
  // the record, where the record with specified address, should
  // be inserted.
  // virtual memory record array is sorted in address order, so
  // binary search is performed
  virtual MemPointer* locate(address addr) {
    int index_low = 0;
    int index_high = _array->length();
    int index_mid = (index_high + index_low) / 2;
    int r = 1;
    while (index_low < index_high && (r = compare(index_mid, addr)) != 0) {
      if (r > 0) {
        index_high = index_mid;
      } else {
        index_low = index_mid;
      }
      index_mid = (index_high + index_low) / 2;
    }
    if (r == 0) {
      // update current location
      _pos = index_mid;
      return _array->at(index_mid);
    } else {
      return NULL;
    }
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
  // compare if an address falls into a memory region,
  // return 0, if the address falls into a memory region at specified index
  // return 1, if memory region pointed by specified index is higher than the address
  // return -1, if memory region pointed by specified index is lower than the address
  int compare(int index, address addr) const {
    VMMemRegion* r = (VMMemRegion*)_array->at(index);
    assert(r->is_reserve_record(), "Sanity check");
    if (r->addr() > addr) {
      return 1;
    } else if (r->addr() + r->reserved_size() <= addr) {
      return -1;
    } else {
      return 0;
    }
  }
};

class MallocRecordIterator : public MemPointerArrayIterator {
 private:
  MemPointerArrayIteratorImpl  _itr;

 public:
  MallocRecordIterator(MemPointerArray* arr) : _itr(arr) {
  }

  MemPointer* current() const {
    MemPointerRecord* cur = (MemPointerRecord*)_itr.current();
    assert(cur == NULL || !cur->is_vm_pointer(), "seek error");
    MemPointerRecord* next = (MemPointerRecord*)_itr.peek_next();
    if (next == NULL || next->addr() != cur->addr()) {
      return cur;
    } else {
      assert(!cur->is_vm_pointer(), "Sanity check");
      assert(cur->is_allocation_record() && next->is_deallocation_record(),
             "sorting order");
      assert(cur->seq() != next->seq(), "Sanity check");
      return cur->seq() >  next->seq() ? cur : next;
    }
  }

  MemPointer* next() {
    MemPointerRecord* cur = (MemPointerRecord*)_itr.current();
    assert(cur == NULL || !cur->is_vm_pointer(), "Sanity check");
    MemPointerRecord* next = (MemPointerRecord*)_itr.next();
    if (next == NULL) {
      return NULL;
    }
    if (cur->addr() == next->addr()) {
      next = (MemPointerRecord*)_itr.next();
    }
    return current();
  }

  MemPointer* peek_next() const      { ShouldNotReachHere(); return NULL; }
  MemPointer* peek_prev() const      { ShouldNotReachHere(); return NULL; }
  void remove()                      { ShouldNotReachHere(); }
  bool insert(MemPointer* ptr)       { ShouldNotReachHere(); return false; }
  bool insert_after(MemPointer* ptr) { ShouldNotReachHere(); return false; }
};

class StagingArea : public _ValueObj {
 private:
  MemPointerArray*   _malloc_data;
  MemPointerArray*   _vm_data;

 public:
  StagingArea() : _malloc_data(NULL), _vm_data(NULL) {
    init();
  }

  ~StagingArea() {
    if (_malloc_data != NULL) delete _malloc_data;
    if (_vm_data != NULL) delete _vm_data;
  }

  MallocRecordIterator malloc_record_walker() {
    return MallocRecordIterator(malloc_data());
  }

  MemPointerArrayIteratorImpl virtual_memory_record_walker();
  bool init();
  void clear() {
    assert(_malloc_data != NULL && _vm_data != NULL, "Just check");
    _malloc_data->shrink();
    _malloc_data->clear();
    _vm_data->clear();
  }

  inline MemPointerArray* malloc_data() { return _malloc_data; }
  inline MemPointerArray* vm_data()     { return _vm_data; }
};

class MemBaseline;
class MemSnapshot : public CHeapObj<mtNMT> {
 private:
  // the following two arrays contain records of all known lived memory blocks
  // live malloc-ed memory pointers
  MemPointerArray*      _alloc_ptrs;
  // live virtual memory pointers
  MemPointerArray*      _vm_ptrs;

  StagingArea           _staging_area;

  // the lock to protect this snapshot
  Monitor*              _lock;

  NOT_PRODUCT(size_t    _untracked_count;)
  friend class MemBaseline;

 public:
  MemSnapshot();
  virtual ~MemSnapshot();

  // if we are running out of native memory
  bool out_of_memory() {
    return (_alloc_ptrs == NULL ||
      _staging_area.malloc_data() == NULL ||
      _staging_area.vm_data() == NULL ||
      _vm_ptrs == NULL || _lock == NULL ||
      _alloc_ptrs->out_of_memory() ||
      _vm_ptrs->out_of_memory());
  }

  // merge a per-thread memory recorder into staging area
  bool merge(MemRecorder* rec);
  // promote staged data to snapshot
  bool promote();


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

   bool promote_malloc_records(MemPointerArrayIterator* itr);
   bool promote_virtual_memory_records(MemPointerArrayIterator* itr);
};


#endif // SHARE_VM_SERVICES_MEM_SNAPSHOT_HPP

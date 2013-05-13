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

  // locate an existing reserved memory region that contains specified address,
  // or the reserved region just above this address, where the incoming
  // reserved region should be inserted.
  virtual MemPointer* locate(address addr) {
    reset();
    VMMemRegion* reg = (VMMemRegion*)current();
    while (reg != NULL) {
      if (reg->is_reserved_region()) {
        if (reg->contains_address(addr) || addr < reg->base()) {
          return reg;
      }
    }
      reg = (VMMemRegion*)next();
    }
      return NULL;
    }

  // following methods update virtual memory in the context
  // of 'current' position, which is properly positioned by
  // callers via locate method.
  bool add_reserved_region(MemPointerRecord* rec);
  bool add_committed_region(MemPointerRecord* rec);
  bool remove_uncommitted_region(MemPointerRecord* rec);
  bool remove_released_region(MemPointerRecord* rec);

  // split a reserved region to create a new memory region with specified base and size
  bool split_reserved_region(VMMemRegion* rgn, address new_rgn_addr, size_t new_rgn_size);
 private:
  bool insert_record(MemPointerRecord* rec);
  bool insert_record_after(MemPointerRecord* rec);

  bool insert_reserved_region(MemPointerRecord* rec);

  // reset current position
  inline void reset() { _pos = 0; }
#ifdef ASSERT
  // check integrity of records on current reserved memory region.
  bool check_reserved_region() {
    VMMemRegion* reserved_region = (VMMemRegion*)current();
    assert(reserved_region != NULL && reserved_region->is_reserved_region(),
          "Sanity check");
    // all committed regions that follow current reserved region, should all
    // belong to the reserved region.
    VMMemRegion* next_region = (VMMemRegion*)next();
    for (; next_region != NULL && next_region->is_committed_region();
         next_region = (VMMemRegion*)next() ) {
      if(!reserved_region->contains_region(next_region)) {
        return false;
      }
    }
    return true;
  }

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

class MallocRecordIterator : public MemPointerArrayIterator {
 private:
  MemPointerArrayIteratorImpl  _itr;



 public:
  MallocRecordIterator(MemPointerArray* arr) : _itr(arr) {
  }

  virtual MemPointer* current() const {
#ifdef ASSERT
    MemPointer* cur_rec = _itr.current();
    if (cur_rec != NULL) {
      MemPointer* prev_rec = _itr.peek_prev();
      MemPointer* next_rec = _itr.peek_next();
      assert(prev_rec == NULL || prev_rec->addr() < cur_rec->addr(), "Sorting order");
      assert(next_rec == NULL || next_rec->addr() > cur_rec->addr(), "Sorting order");
    }
#endif
    return _itr.current();
  }
  virtual MemPointer* next() {
    MemPointerRecord* next_rec = (MemPointerRecord*)_itr.next();
    // arena memory record is a special case, which we have to compare
    // sequence number against its associated arena record.
    if (next_rec != NULL && next_rec->is_arena_memory_record()) {
      MemPointerRecord* prev_rec = (MemPointerRecord*)_itr.peek_prev();
      // if there is an associated arena record, it has to be previous
      // record because of sorting order (by address) - NMT generates a pseudo address
      // for arena's size record by offsetting arena's address, that guarantees
      // the order of arena record and it's size record.
      if (prev_rec != NULL && prev_rec->is_arena_record() &&
        next_rec->is_memory_record_of_arena(prev_rec)) {
        if (prev_rec->seq() > next_rec->seq()) {
          // Skip this arena memory record
          // Two scenarios:
          //   - if the arena record is an allocation record, this early
          //     size record must be leftover by previous arena,
          //     and the last size record should have size = 0.
          //   - if the arena record is a deallocation record, this
          //     size record should be its cleanup record, which should
          //     also have size = 0. In other world, arena alway reset
          //     its size before gone (see Arena's destructor)
          assert(next_rec->size() == 0, "size not reset");
          return _itr.next();
        } else {
          assert(prev_rec->is_allocation_record(),
            "Arena size record ahead of allocation record");
        }
      }
    }
    return next_rec;
  }

  MemPointer* peek_next() const      { ShouldNotReachHere(); return NULL; }
  MemPointer* peek_prev() const      { ShouldNotReachHere(); return NULL; }
  void remove()                      { ShouldNotReachHere(); }
  bool insert(MemPointer* ptr)       { ShouldNotReachHere(); return false; }
  bool insert_after(MemPointer* ptr) { ShouldNotReachHere(); return false; }
};

// collapse duplicated records. Eliminating duplicated records here, is much
// cheaper than during promotion phase. However, it does have limitation - it
// can only eliminate duplicated records within the generation, there are
// still chances seeing duplicated records during promotion.
// We want to use the record with higher sequence number, because it has
// more accurate callsite pc.
class VMRecordIterator : public MemPointerArrayIterator {
 private:
  MemPointerArrayIteratorImpl  _itr;

 public:
  VMRecordIterator(MemPointerArray* arr) : _itr(arr) {
    MemPointerRecord* cur = (MemPointerRecord*)_itr.current();
    MemPointerRecord* next = (MemPointerRecord*)_itr.peek_next();
    while (next != NULL) {
      assert(cur != NULL, "Sanity check");
      assert(((SeqMemPointerRecord*)next)->seq() > ((SeqMemPointerRecord*)cur)->seq(),
        "pre-sort order");

      if (is_duplicated_record(cur, next)) {
        _itr.next();
        next = (MemPointerRecord*)_itr.peek_next();
      } else {
        break;
      }
    }
  }

  virtual MemPointer* current() const {
    return _itr.current();
  }

  // get next record, but skip the duplicated records
  virtual MemPointer* next() {
    MemPointerRecord* cur = (MemPointerRecord*)_itr.next();
    MemPointerRecord* next = (MemPointerRecord*)_itr.peek_next();
    while (next != NULL) {
      assert(cur != NULL, "Sanity check");
      assert(((SeqMemPointerRecord*)next)->seq() > ((SeqMemPointerRecord*)cur)->seq(),
        "pre-sort order");

      if (is_duplicated_record(cur, next)) {
        _itr.next();
        cur = next;
        next = (MemPointerRecord*)_itr.peek_next();
      } else {
        break;
      }
    }
    return cur;
  }

  MemPointer* peek_next() const      { ShouldNotReachHere(); return NULL; }
  MemPointer* peek_prev() const      { ShouldNotReachHere(); return NULL; }
  void remove()                      { ShouldNotReachHere(); }
  bool insert(MemPointer* ptr)       { ShouldNotReachHere(); return false; }
  bool insert_after(MemPointer* ptr) { ShouldNotReachHere(); return false; }

 private:
  bool is_duplicated_record(MemPointerRecord* p1, MemPointerRecord* p2) const {
    bool ret = (p1->addr() == p2->addr() && p1->size() == p2->size() && p1->flags() == p2->flags());
    assert(!(ret && FLAGS_TO_MEMORY_TYPE(p1->flags()) == mtThreadStack), "dup on stack record");
    return ret;
  }
};

class StagingArea VALUE_OBJ_CLASS_SPEC {
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

  VMRecordIterator virtual_memory_record_walker();

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

  // the number of instance classes
  int                   _number_of_classes;

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
  bool promote(int number_of_classes);

  int  number_of_classes() const { return _number_of_classes; }

  void wait(long timeout) {
    assert(_lock != NULL, "Just check");
    MonitorLockerEx locker(_lock);
    locker.wait(true, timeout);
  }

  NOT_PRODUCT(void print_snapshot_stats(outputStream* st);)
  NOT_PRODUCT(void check_staging_data();)
  NOT_PRODUCT(void check_malloc_pointers();)
  NOT_PRODUCT(bool has_allocation_record(address addr);)
  // dump all virtual memory pointers in snapshot
  DEBUG_ONLY( void dump_all_vm_pointers();)

 private:
   // copy sequenced pointer from src to dest
   void copy_seq_pointer(MemPointerRecord* dest, const MemPointerRecord* src);
   // assign a sequenced pointer to non-sequenced pointer
   void assign_pointer(MemPointerRecord*dest, const MemPointerRecord* src);

   bool promote_malloc_records(MemPointerArrayIterator* itr);
   bool promote_virtual_memory_records(MemPointerArrayIterator* itr);
};

#endif // SHARE_VM_SERVICES_MEM_SNAPSHOT_HPP

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

#ifndef SHARE_VM_SERVICES_MEM_RECORDER_HPP
#define SHARE_VM_SERVICES_MEM_RECORDER_HPP

#include "memory/allocation.hpp"
#include "runtime/os.hpp"
#include "services/memPtrArray.hpp"

class MemSnapshot;
class MemTracker;
class MemTrackWorker;

// Fixed size memory pointer array implementation
template <class E, int SIZE> class FixedSizeMemPointerArray :
  public MemPointerArray {
  // This implementation is for memory recorder only
  friend class MemRecorder;

 private:
  E      _data[SIZE];
  int    _size;

 protected:
  FixedSizeMemPointerArray(bool init_elements = false):
   _size(0){
    if (init_elements) {
      for (int index = 0; index < SIZE; index ++) {
        ::new ((void*)&_data[index]) E();
      }
    }
  }

  void* operator new(size_t size, const std::nothrow_t& nothrow_constant) {
    // the instance is part of memRecorder, needs to be tagged with 'otNMTRecorder'
    // to avoid recursion
    return os::malloc(size, (mtNMT | otNMTRecorder));
  }

  void* operator new(size_t size) {
    assert(false, "use nothrow version");
    return NULL;
  }

  void operator delete(void* p) {
    os::free(p, (mtNMT | otNMTRecorder));
  }

  // instance size
  inline size_t instance_size() const {
    return sizeof(FixedSizeMemPointerArray<E, SIZE>);
  }

  NOT_PRODUCT(int capacity() const { return SIZE; })

 public:
  // implementation of public interface
  bool out_of_memory() const { return false; }
  bool is_empty()      const { return _size == 0; }
  bool is_full()             { return length() >= SIZE; }
  int  length()        const { return _size; }

  void clear() {
    _size = 0;
  }

  bool append(MemPointer* ptr) {
    if (is_full()) return false;
    _data[_size ++] = *(E*)ptr;
    return true;
  }

  virtual bool insert_at(MemPointer* p, int pos) {
    assert(false, "append only");
    return false;
  }

  virtual bool remove_at(int pos) {
    assert(false, "not supported");
    return false;
  }

  MemPointer* at(int index) const {
    assert(index >= 0 && index < length(),
      "parameter check");
    return ((E*)&_data[index]);
  }

  void sort(FN_SORT fn) {
    qsort((void*)_data, _size, sizeof(E), fn);
  }

  bool shrink() {
    return false;
  }
};


// This iterator requires pre-sorted MemPointerArray, which is sorted by:
//  1. address
//  2. allocation type
//  3. sequence number
// During the array walking, iterator collapses pointers with the same
// address and allocation type, and only returns the one with highest
// sequence number.
//
// This is read-only iterator, update methods are asserted.
class SequencedRecordIterator : public MemPointerArrayIterator {
 private:
   MemPointerArrayIteratorImpl _itr;
   MemPointer*                 _cur;

 public:
  SequencedRecordIterator(const MemPointerArray* arr):
    _itr(const_cast<MemPointerArray*>(arr)) {
    _cur = next_record();
  }

  SequencedRecordIterator(const SequencedRecordIterator& itr):
    _itr(itr._itr) {
    _cur = next_record();
  }

  // return the pointer at current position
  virtual MemPointer* current() const {
    return _cur;
  };

  // return the next pointer and advance current position
  virtual MemPointer* next() {
    _cur = next_record();
    return _cur;
  }

  // return the next pointer without advancing current position
  virtual MemPointer* peek_next() const {
    assert(false, "not implemented");
    return NULL;

  }
  // return the previous pointer without changing current position
  virtual MemPointer* peek_prev() const {
    assert(false, "not implemented");
    return NULL;
  }

  // remove the pointer at current position
  virtual void remove() {
    assert(false, "read-only iterator");
  };
  // insert the pointer at current position
  virtual bool insert(MemPointer* ptr) {
    assert(false, "read-only iterator");
    return false;
  }

  virtual bool insert_after(MemPointer* ptr) {
    assert(false, "read-only iterator");
    return false;
  }
 private:
  // collapse the 'same kind' of records, and return this 'kind' of
  // record with highest sequence number
  MemPointer* next_record();

  // Test if the two records are the same kind: the same memory block and allocation
  // type.
  inline bool same_kind(const MemPointerRecord* p1, const MemPointerRecord* p2) const {
    assert(!p1->is_vm_pointer() && !p2->is_vm_pointer(), "malloc pointer only");
    return (p1->addr() == p2->addr() &&
      (p1->flags() &MemPointerRecord::tag_masks) ==
      (p2->flags() & MemPointerRecord::tag_masks));
  }
};



#define DEFAULT_RECORDER_PTR_ARRAY_SIZE 512

class MemRecorder : public CHeapObj<mtNMT|otNMTRecorder> {
  friend class MemSnapshot;
  friend class MemTracker;
  friend class MemTrackWorker;
  friend class GenerationData;

 protected:
  // the array that holds memory records
  MemPointerArray*         _pointer_records;

 private:
  // used for linked list
  MemRecorder*             _next;
  // active recorder can only record a certain generation data
  unsigned long            _generation;

 protected:
  _NOINLINE_ MemRecorder();
  ~MemRecorder();

  // record a memory operation
  bool record(address addr, MEMFLAGS flags, size_t size, jint seq, address caller_pc = 0);

  // linked list support
  inline void set_next(MemRecorder* rec) {
    _next = rec;
  }

  inline MemRecorder* next() const {
    return _next;
  }

  // if the recorder is full
  inline bool is_full() const {
    assert(_pointer_records != NULL, "just check");
    return _pointer_records->is_full();
  }

  // if running out of memory when initializing recorder's internal
  // data
  inline bool out_of_memory() const {
    return (_pointer_records == NULL ||
      _pointer_records->out_of_memory());
  }

  inline void clear() {
    assert(_pointer_records != NULL, "Just check");
    _pointer_records->clear();
  }

  SequencedRecordIterator pointer_itr();

  // return the generation of this recorder which it belongs to
  unsigned long get_generation() const { return _generation; }
 protected:
  // number of MemRecorder instance
  static volatile jint _instance_count;

 private:
  // sorting function, sort records into following order
  // 1. memory address
  // 2. allocation type
  // 3. sequence number
  static int sort_record_fn(const void* e1, const void* e2);

  debug_only(void check_dup_seq(jint seq) const;)
  void set_generation();
};

#endif // SHARE_VM_SERVICES_MEM_RECORDER_HPP

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
#ifndef SHARE_VM_UTILITIES_MEM_PTR_ARRAY_HPP
#define SHARE_VM_UTILITIES_MEM_PTR_ARRAY_HPP

#include "memory/allocation.hpp"
#include "services/memPtr.hpp"

class MemPtr;
class MemRecorder;
class ArenaInfo;
class MemSnapshot;

extern "C" {
  typedef int (*FN_SORT)(const void *, const void *);
}


// Memory pointer array interface. This array is used by NMT to hold
// various memory block information.
// The memory pointer arrays are usually walked with their iterators.

class MemPointerArray : public CHeapObj<mtNMT> {
 public:
  virtual ~MemPointerArray() { }

  // return true if it can not allocate storage for the data
  virtual bool out_of_memory() const = 0;
  virtual bool is_empty() const = 0;
  virtual bool is_full() = 0;
  virtual int  length() const = 0;
  virtual void clear() = 0;
  virtual bool append(MemPointer* ptr) = 0;
  virtual bool insert_at(MemPointer* ptr, int pos) = 0;
  virtual bool remove_at(int pos) = 0;
  virtual MemPointer* at(int index) const = 0;
  virtual void sort(FN_SORT fn) = 0;
  virtual size_t instance_size() const = 0;
  virtual bool shrink() = 0;

  NOT_PRODUCT(virtual int capacity() const = 0;)
};

// Iterator interface
class MemPointerArrayIterator VALUE_OBJ_CLASS_SPEC {
 public:
  // return the pointer at current position
  virtual MemPointer* current() const = 0;
  // return the next pointer and advance current position
  virtual MemPointer* next() = 0;
  // return next pointer without advancing current position
  virtual MemPointer* peek_next() const = 0;
  // return previous pointer without changing current position
  virtual MemPointer* peek_prev() const = 0;
  // remove the pointer at current position
  virtual void        remove() = 0;
  // insert the pointer at current position
  virtual bool        insert(MemPointer* ptr) = 0;
  // insert specified element after current position and
  // move current position to newly inserted position
  virtual bool        insert_after(MemPointer* ptr) = 0;
};

// implementation class
class MemPointerArrayIteratorImpl : public MemPointerArrayIterator {
 protected:
  MemPointerArray*  _array;
  int               _pos;

 public:
  MemPointerArrayIteratorImpl(MemPointerArray* arr) {
    assert(arr != NULL, "Parameter check");
    _array = arr;
    _pos = 0;
  }

  virtual MemPointer* current() const {
    if (_pos < _array->length()) {
      return _array->at(_pos);
    }
    return NULL;
  }

  virtual MemPointer* next() {
    if (_pos + 1 < _array->length()) {
      return _array->at(++_pos);
    }
    _pos = _array->length();
    return NULL;
  }

  virtual MemPointer* peek_next() const {
    if (_pos + 1 < _array->length()) {
      return _array->at(_pos + 1);
    }
    return NULL;
  }

  virtual MemPointer* peek_prev() const {
    if (_pos > 0) {
      return _array->at(_pos - 1);
    }
    return NULL;
  }

  virtual void remove() {
    if (_pos < _array->length()) {
      _array->remove_at(_pos);
    }
  }

  virtual bool insert(MemPointer* ptr) {
    return _array->insert_at(ptr, _pos);
  }

  virtual bool insert_after(MemPointer* ptr) {
    if (_array->insert_at(ptr, _pos + 1)) {
      _pos ++;
      return true;
    }
    return false;
  }
};



// Memory pointer array implementation.
// This implementation implements expandable array
#define DEFAULT_PTR_ARRAY_SIZE 1024

template <class E> class MemPointerArrayImpl : public MemPointerArray {
 private:
  int                   _max_size;
  int                   _size;
  bool                  _init_elements;
  E*                    _data;

 public:
  MemPointerArrayImpl(int initial_size = DEFAULT_PTR_ARRAY_SIZE, bool init_elements = true):
   _max_size(initial_size), _size(0), _init_elements(init_elements) {
    _data = (E*)raw_allocate(sizeof(E), initial_size);
    if (_init_elements) {
      for (int index = 0; index < _max_size; index ++) {
        ::new ((void*)&_data[index]) E();
      }
    }
  }

  virtual ~MemPointerArrayImpl() {
    if (_data != NULL) {
      raw_free(_data);
    }
  }

 public:
  bool out_of_memory() const {
    return (_data == NULL);
  }

  size_t instance_size() const {
    return sizeof(MemPointerArrayImpl<E>) + _max_size * sizeof(E);
  }

  bool is_empty() const {
    assert(_data != NULL, "Just check");
    return _size == 0;
  }

  bool is_full() {
    assert(_data != NULL, "Just check");
    if (_size < _max_size) {
      return false;
    } else {
      return !expand_array();
    }
  }

  int length() const {
    assert(_data != NULL, "Just check");
    return _size;
  }

  NOT_PRODUCT(int capacity() const { return _max_size; })

  void clear() {
    assert(_data != NULL, "Just check");
    _size = 0;
  }

  bool append(MemPointer* ptr) {
    assert(_data != NULL, "Just check");
    if (is_full()) {
      return false;
    }
    _data[_size ++] = *(E*)ptr;
    return true;
  }

  bool insert_at(MemPointer* ptr, int pos) {
    assert(_data != NULL, "Just check");
    if (is_full()) {
      return false;
    }
    for (int index = _size; index > pos; index --) {
      _data[index] = _data[index - 1];
    }
    _data[pos] = *(E*)ptr;
    _size ++;
    return true;
  }

  bool remove_at(int pos) {
    assert(_data != NULL, "Just check");
    if (_size <= pos && pos >= 0) {
      return false;
    }
    -- _size;

    for (int index = pos; index < _size; index ++) {
      _data[index] = _data[index + 1];
    }
    return true;
  }

  MemPointer* at(int index) const {
    assert(_data != NULL, "Just check");
    assert(index >= 0 && index < _size, "illegal index");
    return &_data[index];
  }

  bool shrink() {
    float used = ((float)_size) / ((float)_max_size);
    if (used < 0.40) {
      E* old_ptr = _data;
      int new_size = ((_max_size) / (2 * DEFAULT_PTR_ARRAY_SIZE) + 1) * DEFAULT_PTR_ARRAY_SIZE;
      _data = (E*)raw_reallocate(_data, sizeof(E), new_size);
      if (_data == NULL) {
        _data = old_ptr;
        return false;
      } else {
        _max_size = new_size;
        return true;
      }
    }
    return false;
  }

  void sort(FN_SORT fn) {
    assert(_data != NULL, "Just check");
    qsort((void*)_data, _size, sizeof(E), fn);
  }

 private:
  bool  expand_array() {
    assert(_data != NULL, "Not yet allocated");
    E* old_ptr = _data;
    if ((_data = (E*)raw_reallocate((void*)_data, sizeof(E),
      _max_size + DEFAULT_PTR_ARRAY_SIZE)) == NULL) {
      _data = old_ptr;
      return false;
    } else {
      _max_size += DEFAULT_PTR_ARRAY_SIZE;
      if (_init_elements) {
        for (int index = _size; index < _max_size; index ++) {
          ::new ((void*)&_data[index]) E();
        }
      }
      return true;
    }
  }

  void* raw_allocate(size_t elementSize, int items) {
    return os::malloc(elementSize * items, mtNMT);
  }

  void* raw_reallocate(void* ptr, size_t elementSize, int items) {
    return os::realloc(ptr, elementSize * items, mtNMT);
  }

  void  raw_free(void* ptr) {
    os::free(ptr, mtNMT);
  }
};

#endif // SHARE_VM_UTILITIES_MEM_PTR_ARRAY_HPP

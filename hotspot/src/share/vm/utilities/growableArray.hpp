/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_UTILITIES_GROWABLEARRAY_HPP
#define SHARE_VM_UTILITIES_GROWABLEARRAY_HPP

#include "memory/allocation.hpp"
#include "memory/allocation.inline.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/top.hpp"

// A growable array.

/*************************************************************************/
/*                                                                       */
/*     WARNING WARNING WARNING WARNING WARNING WARNING WARNING WARNING   */
/*                                                                       */
/* Should you use GrowableArrays to contain handles you must be certain  */
/* the the GrowableArray does not outlive the HandleMark that contains   */
/* the handles. Since GrowableArrays are typically resource allocated    */
/* the following is an example of INCORRECT CODE,                        */
/*                                                                       */
/* ResourceMark rm;                                                      */
/* GrowableArray<Handle>* arr = new GrowableArray<Handle>(size);         */
/* if (blah) {                                                           */
/*    while (...) {                                                      */
/*      HandleMark hm;                                                   */
/*      ...                                                              */
/*      Handle h(THREAD, some_oop);                                      */
/*      arr->append(h);                                                  */
/*    }                                                                  */
/* }                                                                     */
/* if (arr->length() != 0 ) {                                            */
/*    oop bad_oop = arr->at(0)(); // Handle is BAD HERE.                 */
/*    ...                                                                */
/* }                                                                     */
/*                                                                       */
/* If the GrowableArrays you are creating is C_Heap allocated then it    */
/* hould not old handles since the handles could trivially try and       */
/* outlive their HandleMark. In some situations you might need to do     */
/* this and it would be legal but be very careful and see if you can do  */
/* the code in some other manner.                                        */
/*                                                                       */
/*************************************************************************/

// To call default constructor the placement operator new() is used.
// It should be empty (it only returns the passed void* pointer).
// The definition of placement operator new(size_t, void*) in the <new>.

#include <new>

// Need the correct linkage to call qsort without warnings
extern "C" {
  typedef int (*_sort_Fn)(const void *, const void *);
}

class GenericGrowableArray : public ResourceObj {
  friend class VMStructs;

 protected:
  int    _len;          // current length
  int    _max;          // maximum length
  Arena* _arena;        // Indicates where allocation occurs:
                        //   0 means default ResourceArea
                        //   1 means on C heap
                        //   otherwise, allocate in _arena

  MEMFLAGS   _memflags;   // memory type if allocation in C heap

#ifdef ASSERT
  int    _nesting;      // resource area nesting at creation
  void   set_nesting();
  void   check_nesting();
#else
#define  set_nesting();
#define  check_nesting();
#endif

  // Where are we going to allocate memory?
  bool on_C_heap() { return _arena == (Arena*)1; }
  bool on_stack () { return _arena == NULL;      }
  bool on_arena () { return _arena >  (Arena*)1;  }

  // This GA will use the resource stack for storage if c_heap==false,
  // Else it will use the C heap.  Use clear_and_deallocate to avoid leaks.
  GenericGrowableArray(int initial_size, int initial_len, bool c_heap, MEMFLAGS flags = mtNone) {
    _len = initial_len;
    _max = initial_size;
    _memflags = flags;

    // memory type has to be specified for C heap allocation
    assert(!(c_heap && flags == mtNone), "memory type not specified for C heap object");

    assert(_len >= 0 && _len <= _max, "initial_len too big");
    _arena = (c_heap ? (Arena*)1 : NULL);
    set_nesting();
    assert(!on_C_heap() || allocated_on_C_heap(), "growable array must be on C heap if elements are");
    assert(!on_stack() ||
           (allocated_on_res_area() || allocated_on_stack()),
           "growable array must be on stack if elements are not on arena and not on C heap");
  }

  // This GA will use the given arena for storage.
  // Consider using new(arena) GrowableArray<T> to allocate the header.
  GenericGrowableArray(Arena* arena, int initial_size, int initial_len) {
    _len = initial_len;
    _max = initial_size;
    assert(_len >= 0 && _len <= _max, "initial_len too big");
    _arena = arena;
    _memflags = mtNone;

    assert(on_arena(), "arena has taken on reserved value 0 or 1");
    // Relax next assert to allow object allocation on resource area,
    // on stack or embedded into an other object.
    assert(allocated_on_arena() || allocated_on_stack(),
           "growable array must be on arena or on stack if elements are on arena");
  }

  void* raw_allocate(int elementSize);

  // some uses pass the Thread explicitly for speed (4990299 tuning)
  void* raw_allocate(Thread* thread, int elementSize) {
    assert(on_stack(), "fast ResourceObj path only");
    return (void*)resource_allocate_bytes(thread, elementSize * _max);
  }
};

template<class E> class GrowableArray : public GenericGrowableArray {
  friend class VMStructs;

 private:
  E*     _data;         // data array

  void grow(int j);
  void raw_at_put_grow(int i, const E& p, const E& fill);
  void  clear_and_deallocate();
 public:
  GrowableArray(Thread* thread, int initial_size) : GenericGrowableArray(initial_size, 0, false) {
    _data = (E*)raw_allocate(thread, sizeof(E));
    for (int i = 0; i < _max; i++) ::new ((void*)&_data[i]) E();
  }

  GrowableArray(int initial_size, bool C_heap = false, MEMFLAGS F = mtInternal)
    : GenericGrowableArray(initial_size, 0, C_heap, F) {
    _data = (E*)raw_allocate(sizeof(E));
    for (int i = 0; i < _max; i++) ::new ((void*)&_data[i]) E();
  }

  GrowableArray(int initial_size, int initial_len, const E& filler, bool C_heap = false, MEMFLAGS memflags = mtInternal)
    : GenericGrowableArray(initial_size, initial_len, C_heap, memflags) {
    _data = (E*)raw_allocate(sizeof(E));
    int i = 0;
    for (; i < _len; i++) ::new ((void*)&_data[i]) E(filler);
    for (; i < _max; i++) ::new ((void*)&_data[i]) E();
  }

  GrowableArray(Arena* arena, int initial_size, int initial_len, const E& filler) : GenericGrowableArray(arena, initial_size, initial_len) {
    _data = (E*)raw_allocate(sizeof(E));
    int i = 0;
    for (; i < _len; i++) ::new ((void*)&_data[i]) E(filler);
    for (; i < _max; i++) ::new ((void*)&_data[i]) E();
  }

  GrowableArray() : GenericGrowableArray(2, 0, false) {
    _data = (E*)raw_allocate(sizeof(E));
    ::new ((void*)&_data[0]) E();
    ::new ((void*)&_data[1]) E();
  }

                                // Does nothing for resource and arena objects
  ~GrowableArray()              { if (on_C_heap()) clear_and_deallocate(); }

  void  clear()                 { _len = 0; }
  int   length() const          { return _len; }
  int   max_length() const      { return _max; }
  void  trunc_to(int l)         { assert(l <= _len,"cannot increase length"); _len = l; }
  bool  is_empty() const        { return _len == 0; }
  bool  is_nonempty() const     { return _len != 0; }
  bool  is_full() const         { return _len == _max; }
  DEBUG_ONLY(E* data_addr() const      { return _data; })

  void print();

  int append(const E& elem) {
    check_nesting();
    if (_len == _max) grow(_len);
    int idx = _len++;
    _data[idx] = elem;
    return idx;
  }

  bool append_if_missing(const E& elem) {
    // Returns TRUE if elem is added.
    bool missed = !contains(elem);
    if (missed) append(elem);
    return missed;
  }

  E& at(int i) {
    assert(0 <= i && i < _len, "illegal index");
    return _data[i];
  }

  E const& at(int i) const {
    assert(0 <= i && i < _len, "illegal index");
    return _data[i];
  }

  E* adr_at(int i) const {
    assert(0 <= i && i < _len, "illegal index");
    return &_data[i];
  }

  E first() const {
    assert(_len > 0, "empty list");
    return _data[0];
  }

  E top() const {
    assert(_len > 0, "empty list");
    return _data[_len-1];
  }

  void push(const E& elem) { append(elem); }

  E pop() {
    assert(_len > 0, "empty list");
    return _data[--_len];
  }

  void at_put(int i, const E& elem) {
    assert(0 <= i && i < _len, "illegal index");
    _data[i] = elem;
  }

  E at_grow(int i, const E& fill = E()) {
    assert(0 <= i, "negative index");
    check_nesting();
    if (i >= _len) {
      if (i >= _max) grow(i);
      for (int j = _len; j <= i; j++)
        _data[j] = fill;
      _len = i+1;
    }
    return _data[i];
  }

  void at_put_grow(int i, const E& elem, const E& fill = E()) {
    assert(0 <= i, "negative index");
    check_nesting();
    raw_at_put_grow(i, elem, fill);
  }

  bool contains(const E& elem) const {
    for (int i = 0; i < _len; i++) {
      if (_data[i] == elem) return true;
    }
    return false;
  }

  int  find(const E& elem) const {
    for (int i = 0; i < _len; i++) {
      if (_data[i] == elem) return i;
    }
    return -1;
  }

  int  find_from_end(const E& elem) const {
    for (int i = _len-1; i >= 0; i--) {
      if (_data[i] == elem) return i;
    }
    return -1;
  }

  int  find(void* token, bool f(void*, E)) const {
    for (int i = 0; i < _len; i++) {
      if (f(token, _data[i])) return i;
    }
    return -1;
  }

  int  find_from_end(void* token, bool f(void*, E)) const {
    // start at the end of the array
    for (int i = _len-1; i >= 0; i--) {
      if (f(token, _data[i])) return i;
    }
    return -1;
  }

  void remove(const E& elem) {
    for (int i = 0; i < _len; i++) {
      if (_data[i] == elem) {
        for (int j = i + 1; j < _len; j++) _data[j-1] = _data[j];
        _len--;
        return;
      }
    }
    ShouldNotReachHere();
  }

  // The order is preserved.
  void remove_at(int index) {
    assert(0 <= index && index < _len, "illegal index");
    for (int j = index + 1; j < _len; j++) _data[j-1] = _data[j];
    _len--;
  }

  // The order is changed.
  void delete_at(int index) {
    assert(0 <= index && index < _len, "illegal index");
    if (index < --_len) {
      // Replace removed element with last one.
      _data[index] = _data[_len];
    }
  }

  // inserts the given element before the element at index i
  void insert_before(const int idx, const E& elem) {
    check_nesting();
    if (_len == _max) grow(_len);
    for (int j = _len - 1; j >= idx; j--) {
      _data[j + 1] = _data[j];
    }
    _len++;
    _data[idx] = elem;
  }

  void appendAll(const GrowableArray<E>* l) {
    for (int i = 0; i < l->_len; i++) {
      raw_at_put_grow(_len, l->_data[i], 0);
    }
  }

  void sort(int f(E*,E*)) {
    qsort(_data, length(), sizeof(E), (_sort_Fn)f);
  }
  // sort by fixed-stride sub arrays:
  void sort(int f(E*,E*), int stride) {
    qsort(_data, length() / stride, sizeof(E) * stride, (_sort_Fn)f);
  }
};

// Global GrowableArray methods (one instance in the library per each 'E' type).

template<class E> void GrowableArray<E>::grow(int j) {
    // grow the array by doubling its size (amortized growth)
    int old_max = _max;
    if (_max == 0) _max = 1; // prevent endless loop
    while (j >= _max) _max = _max*2;
    // j < _max
    E* newData = (E*)raw_allocate(sizeof(E));
    int i = 0;
    for (     ; i < _len; i++) ::new ((void*)&newData[i]) E(_data[i]);
    for (     ; i < _max; i++) ::new ((void*)&newData[i]) E();
    for (i = 0; i < old_max; i++) _data[i].~E();
    if (on_C_heap() && _data != NULL) {
      FreeHeap(_data);
    }
    _data = newData;
}

template<class E> void GrowableArray<E>::raw_at_put_grow(int i, const E& p, const E& fill) {
    if (i >= _len) {
      if (i >= _max) grow(i);
      for (int j = _len; j < i; j++)
        _data[j] = fill;
      _len = i+1;
    }
    _data[i] = p;
}

// This function clears and deallocate the data in the growable array that
// has been allocated on the C heap.  It's not public - called by the
// destructor.
template<class E> void GrowableArray<E>::clear_and_deallocate() {
    assert(on_C_heap(),
           "clear_and_deallocate should only be called when on C heap");
    clear();
    if (_data != NULL) {
      for (int i = 0; i < _max; i++) _data[i].~E();
      FreeHeap(_data);
      _data = NULL;
    }
}

template<class E> void GrowableArray<E>::print() {
    tty->print("Growable Array " INTPTR_FORMAT, this);
    tty->print(": length %ld (_max %ld) { ", _len, _max);
    for (int i = 0; i < _len; i++) tty->print(INTPTR_FORMAT " ", *(intptr_t*)&(_data[i]));
    tty->print("}\n");
}

#endif // SHARE_VM_UTILITIES_GROWABLEARRAY_HPP

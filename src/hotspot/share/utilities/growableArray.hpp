/*
 * Copyright (c) 1997, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_GROWABLEARRAY_HPP
#define SHARE_UTILITIES_GROWABLEARRAY_HPP

#include "memory/allocation.hpp"
#include "memory/iterator.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"
#include "utilities/powerOfTwo.hpp"

// A growable array.

/*************************************************************************/
/*                                                                       */
/*     WARNING WARNING WARNING WARNING WARNING WARNING WARNING WARNING   */
/*                                                                       */
/* Should you use GrowableArrays to contain handles you must be certain  */
/* that the GrowableArray does not outlive the HandleMark that contains  */
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
/* should not hold handles since the handles could trivially try and     */
/* outlive their HandleMark. In some situations you might need to do     */
/* this and it would be legal but be very careful and see if you can do  */
/* the code in some other manner.                                        */
/*                                                                       */
/*************************************************************************/

// Non-template base class responsible for handling the length and max.


class GrowableArrayBase : public AnyObj {
  friend class VMStructs;

protected:
  // Current number of accessible elements
  int _len;
  // Current number of allocated elements
  int _capacity;

  GrowableArrayBase(int capacity, int initial_len) :
      _len(initial_len),
      _capacity(capacity) {
    assert(_len >= 0 && _len <= _capacity, "initial_len too big");
  }

  ~GrowableArrayBase() {}

public:
  int   length() const          { return _len; }
  int   capacity() const        { return _capacity; }

  bool  is_empty() const        { return _len == 0; }
  bool  is_nonempty() const     { return _len != 0; }
  bool  is_full() const         { return _len == _capacity; }
};

template <typename E> class GrowableArrayIterator;

// Extends GrowableArrayBase with a typed data array.
//
// E: Element type
//
// The "view" adds function that don't grow or deallocate
// the _data array, so there's no need for an allocator.
//
// The "view" can be used to type erase the allocator classes
// of GrowableArrayWithAllocator.
template <typename E>
class GrowableArrayView : public GrowableArrayBase {
protected:
  E* _data; // data array

  GrowableArrayView(E* data, int capacity, int initial_len) :
      GrowableArrayBase(capacity, initial_len), _data(data) {}

  ~GrowableArrayView() {}

protected:
  // Used by AOTGrowableArray for MetaspaceClosure support.
  E** data_addr() {
    return &_data;
  }

public:
  bool operator==(const GrowableArrayView& rhs) const {
    if (_len != rhs._len)
      return false;
    for (int i = 0; i < _len; i++) {
      if (at(i) != rhs.at(i)) {
        return false;
      }
    }
    return true;
  }

  bool operator!=(const GrowableArrayView& rhs) const {
    return !(*this == rhs);
  }

  E& at(int i) {
    assert(0 <= i && i < _len, "illegal index %d for length %d", i, _len);
    return _data[i];
  }

  E const& at(int i) const {
    assert(0 <= i && i < _len, "illegal index %d for length %d", i, _len);
    return _data[i];
  }

  E* adr_at(int i) const {
    assert(0 <= i && i < _len, "illegal index %d for length %d", i, _len);
    return &_data[i];
  }

  E& first() {
    assert(_len > 0, "empty");
    return _data[0];
  }

  E const& first() const {
    assert(_len > 0, "empty");
    return _data[0];
  }

  E& top() {
    assert(_len > 0, "empty");
    return _data[_len - 1];
  }

  E const& top() const {
    assert(_len > 0, "empty");
    return _data[_len - 1];
  }

  E& last() {
    return top();
  }

  E const& last() const {
    return top();
  }

  GrowableArrayIterator<E> begin() const {
    return GrowableArrayIterator<E>(this, 0);
  }

  GrowableArrayIterator<E> end() const {
    return GrowableArrayIterator<E>(this, length());
  }

  void at_put(int i, const E& elem) {
    assert(0 <= i && i < _len, "illegal index %d for length %d", i, _len);
    _data[i] = elem;
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

  // Find first element that matches the given predicate.
  //
  // Predicate: bool predicate(const E& elem)
  //
  // Returns the index of the element or -1 if no element matches the predicate
  template<typename Predicate>
  int find_if(Predicate predicate) const {
    for (int i = 0; i < _len; i++) {
      if (predicate(_data[i])) return i;
    }
    return -1;
  }

  // Find last element that matches the given predicate.
  //
  // Predicate: bool predicate(const E& elem)
  //
  // Returns the index of the element or -1 if no element matches the predicate
  template<typename Predicate>
  int find_from_end_if(Predicate predicate) const {
    // start at the end of the array
    for (int i = _len-1; i >= 0; i--) {
      if (predicate(_data[i])) return i;
    }
    return -1;
  }

  void sort(int f(E*, E*)) {
    if (_data == nullptr) return;
    qsort(_data, length(), sizeof(E), (_sort_Fn)f);
  }
  // sort by fixed-stride sub arrays:
  void sort(int f(E*, E*), int stride) {
    if (_data == nullptr) return;
    qsort(_data, length() / stride, sizeof(E) * stride, (_sort_Fn)f);
  }

  template <typename K, int compare(const K&, const E&)> int find_sorted(const K& key, bool& found) const {
    found = false;
    int min = 0;
    int max = length() - 1;

    while (max >= min) {
      int mid = (int)(((uint)max + min) / 2);
      E value = at(mid);
      int diff = compare(key, value);
      if (diff > 0) {
        min = mid + 1;
      } else if (diff < 0) {
        max = mid - 1;
      } else {
        found = true;
        return mid;
      }
    }
    return min;
  }

  template <typename K>
  int find_sorted(CompareClosure<E>* cc, const K& key, bool& found) {
    found = false;
    int min = 0;
    int max = length() - 1;

    while (max >= min) {
      int mid = (int)(((uint)max + min) / 2);
      E value = at(mid);
      int diff = cc->do_compare(key, value);
      if (diff > 0) {
        min = mid + 1;
      } else if (diff < 0) {
        max = mid - 1;
      } else {
        found = true;
        return mid;
      }
    }
    return min;
  }

  void print() const {
    tty->print("Growable Array " PTR_FORMAT, p2i(this));
    tty->print(": length %d (capacity %d) { ", _len, _capacity);
    for (int i = 0; i < _len; i++) {
      tty->print(INTPTR_FORMAT " ", *(intptr_t*)&(_data[i]));
    }
    tty->print("}\n");
  }
};

template <typename E>
class GrowableArrayFromArray : public GrowableArrayView<E> {
public:

  GrowableArrayFromArray(E* data, int len) :
    GrowableArrayView<E>(data, len, len) {}
};

// GrowableArrayWithAllocator extends the "view" with
// the capability to grow and deallocate the data array.
//
// The allocator responsibility is delegated to the sub-class.
//
// Derived: The sub-class responsible for allocation / deallocation
//  - E* Derived::allocate()       - member function responsible for allocation
//  - void Derived::deallocate(E*) - member function responsible for deallocation
template <typename E, typename Derived>
class GrowableArrayWithAllocator : public GrowableArrayView<E> {
  void expand_to(int j);
  void grow(int j);

protected:
  GrowableArrayWithAllocator(E* data, int capacity) :
      GrowableArrayView<E>(data, capacity, 0) {
    for (int i = 0; i < capacity; i++) {
      ::new ((void*)&data[i]) E();
    }
  }

  GrowableArrayWithAllocator(E* data, int capacity, int initial_len, const E& filler) :
      GrowableArrayView<E>(data, capacity, initial_len) {
    int i = 0;
    for (; i < initial_len; i++) {
      ::new ((void*)&data[i]) E(filler);
    }
    for (; i < capacity; i++) {
      ::new ((void*)&data[i]) E();
    }
  }

  GrowableArrayWithAllocator(E* data, int capacity, int initial_len) :
    GrowableArrayView<E>(data, capacity, initial_len) {}

  ~GrowableArrayWithAllocator() {}

public:
  int append(const E& elem) {
    if (this->_len == this->_capacity) grow(this->_len);
    int idx = this->_len++;
    this->_data[idx] = elem;
    return idx;
  }

  bool append_if_missing(const E& elem) {
    // Returns TRUE if elem is added.
    bool missed = !this->contains(elem);
    if (missed) append(elem);
    return missed;
  }

  void push(const E& elem) { append(elem); }

  E pop() {
    assert(this->_len > 0, "empty list");
    return this->_data[--this->_len];
  }

  E& at_grow(int i, const E& fill = E()) {
    assert(0 <= i, "negative index %d", i);
    if (i >= this->_len) {
      if (i >= this->_capacity) grow(i);
      for (int j = this->_len; j <= i; j++)
        this->_data[j] = fill;
      this->_len = i+1;
    }
    return this->_data[i];
  }

  void at_put_grow(int i, const E& elem, const E& fill = E()) {
    assert(0 <= i, "negative index %d", i);
    if (i >= this->_len) {
      if (i >= this->_capacity) grow(i);
      for (int j = this->_len; j < i; j++)
        this->_data[j] = fill;
      this->_len = i+1;
    }
    this->_data[i] = elem;
  }

  // inserts the given element before the element at index i
  void insert_before(const int idx, const E& elem) {
    assert(0 <= idx && idx <= this->_len, "illegal index %d for length %d", idx, this->_len);
    if (this->_len == this->_capacity) grow(this->_len);
    for (int j = this->_len - 1; j >= idx; j--) {
      this->_data[j + 1] = this->_data[j];
    }
    this->_len++;
    this->_data[idx] = elem;
  }

  void insert_before(const int idx, const GrowableArrayView<E>* array) {
    assert(0 <= idx && idx <= this->_len, "illegal index %d for length %d", idx, this->_len);
    int array_len = array->length();
    int new_len = this->_len + array_len;
    if (new_len >= this->_capacity) grow(new_len);

    for (int j = this->_len - 1; j >= idx; j--) {
      this->_data[j + array_len] = this->_data[j];
    }

    for (int j = 0; j < array_len; j++) {
      this->_data[idx + j] = array->at(j);
    }

    this->_len += array_len;
  }

  void appendAll(const GrowableArrayView<E>* l) {
    for (int i = 0; i < l->length(); i++) {
      this->at_put_grow(this->_len, l->at(i), E());
    }
  }

  // Binary search and insertion utility.  Search array for element
  // matching key according to the static compare function.  Insert
  // that element if not already in the list.  Assumes the list is
  // already sorted according to compare function.
  template <int compare(const E&, const E&)> E insert_sorted(const E& key) {
    bool found;
    int location = GrowableArrayView<E>::template find_sorted<E, compare>(key, found);
    if (!found) {
      insert_before(location, key);
    }
    return this->at(location);
  }

  E insert_sorted(CompareClosure<E>* cc, const E& key) {
    bool found;
    int location = find_sorted(cc, key, found);
    if (!found) {
      insert_before(location, key);
    }
    return this->at(location);
  }

  void swap(GrowableArrayWithAllocator* other) {
    ::swap(this->_data, other->_data);
    ::swap(this->_len, other->_len);
    ::swap(this->_capacity, other->_capacity);
  }

  // Ensure capacity is at least new_capacity.
  void reserve(int new_capacity);

  void trunc_to(int length) {
    assert(length <= this->_len,"cannot increase length");
    this->_len = length;
  }

  // Order preserving remove operations.

  void remove_at(int index) {
    assert(0 <= index && index < this->_len,
           "illegal index %d for length %d", index, this->_len);
    for (int j = index + 1; j < this->_len; j++) {
      this->_data[j-1] = this->_data[j];
    }
    this->_len--;
  }

  void remove(const E& elem) {
    // Assuming that element does exist.
    bool removed = this->remove_if_existing(elem);
    if (removed) return;
    ShouldNotReachHere();
  }

  bool remove_if_existing(const E& elem) {
    // Returns TRUE if elem is removed.
    for (int i = 0; i < this->_len; i++) {
      if (this->_data[i] == elem) {
        this->remove_at(i);
        return true;
      }
    }
    return false;
  }

  // Remove all elements in the range [0; end). The order is preserved.
  void remove_till(int end) {
    remove_range(0, end);
  }

  // Remove all elements in the range [start; end). The order is preserved.
  void remove_range(int start, int end) {
    assert(0 <= start, "illegal start index %d", start);
    assert(start <= end && end <= this->_len,
           "erase called with invalid range [%d, %d) for length %d",
           start, end, this->_len);

    for (int i = start, j = end; j < this->length(); i++, j++) {
      this->at_put(i, this->at(j));
    }
    this->_len -= (end - start);
  }

  // Replaces the designated element with the last element and shrinks by 1.
  void delete_at(int index) {
    assert(0 <= index && index < this->_len, "illegal index %d for length %d", index, this->_len);
    if (index < --this->_len) {
      // Replace removed element with last one.
      this->_data[index] = this->_data[this->_len];
    }
  }

  // Reduce capacity to length.
  void shrink_to_fit();

  void clear() { this->_len = 0; }
  void clear_and_deallocate();
};

template <typename E, typename Derived>
void GrowableArrayWithAllocator<E, Derived>::expand_to(int new_capacity) {
  int old_capacity = this->_capacity;
  assert(new_capacity > old_capacity,
         "expected growth but %d <= %d", new_capacity, old_capacity);
  this->_capacity = new_capacity;
  E* newData = static_cast<Derived*>(this)->allocate();
  int i = 0;
  for (     ; i < this->_len; i++) ::new ((void*)&newData[i]) E(this->_data[i]);
  for (     ; i < this->_capacity; i++) ::new ((void*)&newData[i]) E();
  for (i = 0; i < old_capacity; i++) this->_data[i].~E();
  if (this->_data != nullptr) {
    static_cast<Derived*>(this)->deallocate(this->_data);
  }
  this->_data = newData;
}

template <typename E, typename Derived>
void GrowableArrayWithAllocator<E, Derived>::grow(int j) {
  // grow the array by increasing _capacity to the first power of two larger than the size we need
  expand_to(next_power_of_2(j));
}

template <typename E, typename Derived>
void GrowableArrayWithAllocator<E, Derived>::reserve(int new_capacity) {
  if (new_capacity > this->_capacity) {
    expand_to(new_capacity);
  }
}

template <typename E, typename Derived>
void GrowableArrayWithAllocator<E, Derived>::shrink_to_fit() {
  int old_capacity = this->_capacity;
  int len = this->_len;
  assert(len <= old_capacity, "invariant");

  // If already at full capacity, nothing to do.
  if (len == old_capacity) {
    return;
  }

  // If not empty, allocate new, smaller, data, and copy old data to it.
  E* old_data = this->_data;
  E* new_data = nullptr;
  this->_capacity = len;        // Must preceed allocate().
  if (len > 0) {
    new_data = static_cast<Derived*>(this)->allocate();
    for (int i = 0; i < len; ++i) ::new (&new_data[i]) E(old_data[i]);
  }
  // Destroy contents of old data, and deallocate it.
  for (int i = 0; i < old_capacity; ++i) old_data[i].~E();
  if (old_data != nullptr) {
    static_cast<Derived*>(this)->deallocate(old_data);
  }
  // Install new data, which might be nullptr.
  this->_data = new_data;
}

template <typename E, typename Derived>
void GrowableArrayWithAllocator<E, Derived>::clear_and_deallocate() {
  this->clear();
  this->shrink_to_fit();
}

class GrowableArrayResourceAllocator {
public:
  static void* allocate(int max, int element_size);
};

// Arena allocator
class GrowableArrayArenaAllocator {
public:
  static void* allocate(int max, int element_size, Arena* arena);
};

// CHeap allocator
class GrowableArrayCHeapAllocator {
public:
  static void* allocate(int max, int element_size, MemTag mem_tag);
  static void deallocate(void* mem);
};

#ifdef ASSERT

// Checks resource allocation nesting
class GrowableArrayNestingCheck {
  // resource area nesting at creation
  int _nesting;

public:
  GrowableArrayNestingCheck(bool on_resource_area);
  GrowableArrayNestingCheck(Arena* arena);

  void on_resource_area_alloc() const;
  void on_arena_alloc(Arena* arena) const;
};

#endif // ASSERT

// Encodes where the backing array is allocated
// and performs necessary checks.
class GrowableArrayMetadata {
  uintptr_t _bits;

  // resource area nesting at creation
  DEBUG_ONLY(GrowableArrayNestingCheck _nesting_check;)

  // Resource allocation
  static uintptr_t bits() {
    return 0;
  }

  // CHeap allocation
  static uintptr_t bits(MemTag mem_tag) {
    assert(mem_tag != mtNone, "Must provide a proper MemTag");
    return (uintptr_t(mem_tag) << 1) | 1;
  }

  // Arena allocation
  static uintptr_t bits(Arena* arena) {
    assert((uintptr_t(arena) & 1) == 0, "Required for on_C_heap() to work");
    return uintptr_t(arena);
  }

public:
  // Resource allocation
  GrowableArrayMetadata() :
      _bits(bits())
      DEBUG_ONLY(COMMA _nesting_check(true)) {
  }

  // Arena allocation
  GrowableArrayMetadata(Arena* arena) :
      _bits(bits(arena))
      DEBUG_ONLY(COMMA _nesting_check(arena)) {
  }

  // CHeap allocation
  GrowableArrayMetadata(MemTag mem_tag) :
      _bits(bits(mem_tag))
      DEBUG_ONLY(COMMA _nesting_check(false)) {
  }

#ifdef ASSERT
  GrowableArrayMetadata(const GrowableArrayMetadata& other) :
      _bits(other._bits),
      _nesting_check(other._nesting_check) {
    assert(!on_C_heap(), "Copying of CHeap arrays not supported");
    assert(!other.on_C_heap(), "Copying of CHeap arrays not supported");
  }

  GrowableArrayMetadata& operator=(const GrowableArrayMetadata& other) {
    _bits = other._bits;
    _nesting_check = other._nesting_check;
    assert(!on_C_heap(), "Assignment of CHeap arrays not supported");
    assert(!other.on_C_heap(), "Assignment of CHeap arrays not supported");
    return *this;
  }

  void init_checks(const GrowableArrayBase* array) const;
  void on_resource_area_alloc_check() const;
  void on_arena_alloc_check() const;
#endif // ASSERT

  bool on_C_heap() const        { return (_bits & 1) == 1; }
  bool on_resource_area() const { return _bits == 0; }
  bool on_arena() const         { return (_bits & 1) == 0 && _bits != 0; }

  Arena* arena() const      { return (Arena*)_bits; }
  MemTag mem_tag() const { return MemTag(_bits >> 1); }
};

// THE GrowableArray.
//
// Supports multiple allocation strategies:
//  - Resource stack allocation: if no extra argument is provided
//  - CHeap allocation: if mem_tag is provided
//  - Arena allocation: if an arena is provided
//
// There are some drawbacks of using GrowableArray, that are removed in some
// of the other implementations of GrowableArrayWithAllocator sub-classes:
//
// Memory overhead: The multiple allocation strategies uses extra metadata
//  embedded in the instance.
//
// Strict allocation locations: There are rules about where the GrowableArray
//  instance is allocated, that depends on where the data array is allocated.
//  See: init_checks.

template <typename E>
class GrowableArray : public GrowableArrayWithAllocator<E, GrowableArray<E>> {
  friend class VMStructs;
  friend class GrowableArrayWithAllocator<E, GrowableArray>;
  friend class GrowableArrayTest;

  static E* allocate(int max) {
    return (E*)GrowableArrayResourceAllocator::allocate(max, sizeof(E));
  }

  static E* allocate(int max, MemTag mem_tag) {
    return (E*)GrowableArrayCHeapAllocator::allocate(max, sizeof(E), mem_tag);
  }

  static E* allocate(int max, Arena* arena) {
    return (E*)GrowableArrayArenaAllocator::allocate(max, sizeof(E), arena);
  }

  GrowableArrayMetadata _metadata;

  void init_checks() const { DEBUG_ONLY(_metadata.init_checks(this);) }

  // Where are we going to allocate memory?
  bool on_C_heap() const        { return _metadata.on_C_heap(); }
  bool on_resource_area() const { return _metadata.on_resource_area(); }
  bool on_arena() const         { return _metadata.on_arena(); }

  E* allocate() {
    if (on_resource_area()) {
      DEBUG_ONLY(_metadata.on_resource_area_alloc_check());
      return allocate(this->_capacity);
    }

    if (on_C_heap()) {
      return allocate(this->_capacity, _metadata.mem_tag());
    }

    assert(on_arena(), "Sanity");
    DEBUG_ONLY(_metadata.on_arena_alloc_check());
    return allocate(this->_capacity, _metadata.arena());
  }

  void deallocate(E* mem) {
    if (on_C_heap()) {
      GrowableArrayCHeapAllocator::deallocate(mem);
    }
  }

public:
  GrowableArray() : GrowableArray(2 /* initial_capacity */) {}

  explicit GrowableArray(int initial_capacity) :
      GrowableArrayWithAllocator<E, GrowableArray>(
          allocate(initial_capacity),
          initial_capacity),
      _metadata() {
    init_checks();
  }

  GrowableArray(int initial_capacity, MemTag mem_tag) :
      GrowableArrayWithAllocator<E, GrowableArray>(
          allocate(initial_capacity, mem_tag),
          initial_capacity),
      _metadata(mem_tag) {
    init_checks();
  }

  GrowableArray(int initial_capacity, int initial_len, const E& filler) :
      GrowableArrayWithAllocator<E, GrowableArray>(
          allocate(initial_capacity),
          initial_capacity, initial_len, filler),
      _metadata() {
    init_checks();
  }

  // This constructor performs no default initialization, so be careful.
  GrowableArray(int initial_capacity, int initial_len, MemTag mem_tag) :
    GrowableArrayWithAllocator<E, GrowableArray>(
      allocate(initial_capacity, mem_tag),
      initial_capacity, initial_len),
    _metadata(mem_tag) {
    init_checks();
  }

  GrowableArray(int initial_capacity, int initial_len, const E& filler, MemTag mem_tag) :
      GrowableArrayWithAllocator<E, GrowableArray>(
          allocate(initial_capacity, mem_tag),
          initial_capacity, initial_len, filler),
      _metadata(mem_tag) {
    init_checks();
  }

  GrowableArray(Arena* arena, int initial_capacity, int initial_len, const E& filler) :
      GrowableArrayWithAllocator<E, GrowableArray>(
          allocate(initial_capacity, arena),
          initial_capacity, initial_len, filler),
      _metadata(arena) {
    init_checks();
  }

  ~GrowableArray() {
    if (on_C_heap()) {
      this->clear_and_deallocate();
    }
  }
};

// Leaner GrowableArray for CHeap backed data arrays, with compile-time decided MemTag.
template <typename E, MemTag MT>
class GrowableArrayCHeap : public GrowableArrayWithAllocator<E, GrowableArrayCHeap<E, MT> > {
  friend class GrowableArrayWithAllocator<E, GrowableArrayCHeap<E, MT> >;

  STATIC_ASSERT(MT != mtNone);

  static E* allocate(int max, MemTag mem_tag) {
    return (E*)GrowableArrayCHeapAllocator::allocate(max, sizeof(E), mem_tag);
  }

  NONCOPYABLE(GrowableArrayCHeap);

  E* allocate() {
    return allocate(this->_capacity, MT);
  }

  void deallocate(E* mem) {
    GrowableArrayCHeapAllocator::deallocate(mem);
  }

public:
  GrowableArrayCHeap(int initial_capacity = 0) :
      GrowableArrayWithAllocator<E, GrowableArrayCHeap<E, MT> >(
          allocate(initial_capacity, MT),
          initial_capacity) {}

  GrowableArrayCHeap(int initial_capacity, int initial_len, const E& filler) :
      GrowableArrayWithAllocator<E, GrowableArrayCHeap<E, MT> >(
          allocate(initial_capacity, MT),
          initial_capacity, initial_len, filler) {}

  ~GrowableArrayCHeap() {
    this->clear_and_deallocate();
  }

  void* operator new(size_t size) {
    return AnyObj::operator new(size, MT);
  }

  void* operator new(size_t size, const std::nothrow_t&  nothrow_constant) throw() {
    return AnyObj::operator new(size, nothrow_constant, MT);
  }
  void operator delete(void *p) {
    AnyObj::operator delete(p);
  }
};

// Custom STL-style iterator to iterate over GrowableArrays
// It is constructed by invoking GrowableArray::begin() and GrowableArray::end()
template <typename E>
class GrowableArrayIterator : public StackObj {
  friend class GrowableArrayView<E>;

 private:
  const GrowableArrayView<E>* _array; // GrowableArray we iterate over
  int _position;                      // The current position in the GrowableArray

  // Private constructor used in GrowableArray::begin() and GrowableArray::end()
  GrowableArrayIterator(const GrowableArrayView<E>* array, int position) : _array(array), _position(position) {
    assert(0 <= position && position <= _array->length(), "illegal position");
  }

 public:
  GrowableArrayIterator() : _array(nullptr), _position(0) { }
  GrowableArrayIterator& operator++() { ++_position; return *this; }
  E operator*()                       { return _array->at(_position); }

  bool operator==(const GrowableArrayIterator& rhs)  {
    assert(_array == rhs._array, "iterator belongs to different array");
    return _position == rhs._position;
  }

  bool operator!=(const GrowableArrayIterator& rhs)  {
    assert(_array == rhs._array, "iterator belongs to different array");
    return _position != rhs._position;
  }
};

// Arrays for basic types
typedef GrowableArray<int> intArray;
typedef GrowableArray<int> intStack;
typedef GrowableArray<bool> boolArray;

#endif // SHARE_UTILITIES_GROWABLEARRAY_HPP

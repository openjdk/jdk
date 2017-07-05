/*
 * Copyright (c) 2000, 2014, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_UTILITIES_ARRAY_HPP
#define SHARE_VM_UTILITIES_ARRAY_HPP

#include "memory/allocation.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/metaspace.hpp"
#include "runtime/orderAccess.hpp"

// correct linkage required to compile w/o warnings
// (must be on file level - cannot be local)
extern "C" { typedef int (*ftype)(const void*, const void*); }


class ResourceArray: public ResourceObj {
 protected:
  int   _length;                                 // the number of array elements
  void* _data;                                   // the array memory
#ifdef ASSERT
  int   _nesting;                                // the resource area nesting level
#endif

  // creation
  ResourceArray() {
    _length  = 0;
    _data    = NULL;
    DEBUG_ONLY(init_nesting();)
    // client may call initialize, at most once
  }


  ResourceArray(size_t esize, int length) {
    DEBUG_ONLY(_data = NULL);
    initialize(esize, length);
  }

  void initialize(size_t esize, int length) {
    assert(length >= 0, "illegal length");
    assert(StressRewriter || _data == NULL, "must be new object");
    _length  = length;
    _data    = resource_allocate_bytes(esize * length);
    DEBUG_ONLY(init_nesting();)
  }

#ifdef ASSERT
  void init_nesting();
#endif

  // helper functions
  void sort     (size_t esize, ftype f);         // sort the array
  void expand   (size_t esize, int i, int& size);// expand the array to include slot i
  void remove_at(size_t esize, int i);           // remove the element in slot i

 public:
  // standard operations
  int  length() const                            { return _length; }
  bool is_empty() const                          { return length() == 0; }
};


template <MEMFLAGS F>class CHeapArray: public CHeapObj<F> {
 protected:
  int   _length;                                 // the number of array elements
  void* _data;                                   // the array memory

  // creation
  CHeapArray() {
    _length  = 0;
    _data    = NULL;
  }


  CHeapArray(size_t esize, int length) {
    assert(length >= 0, "illegal length");
    _length  = length;
    _data    = (void*) NEW_C_HEAP_ARRAY(char *, esize * length, F);
  }

  void initialize(size_t esize, int length) {
    // In debug set array to 0?
  }

#ifdef ASSERT
  void init_nesting();
#endif

  // helper functions
  void sort     (size_t esize, ftype f);         // sort the array
  void expand   (size_t esize, int i, int& size);// expand the array to include slot i
  void remove_at(size_t esize, int i);           // remove the element in slot i

 public:
  // standard operations
  int  length() const                            { return _length; }
  bool is_empty() const                          { return length() == 0; }
};

#define define_generic_array(array_name,element_type, base_class)                        \
  class array_name: public base_class {                                                  \
   protected:                                                                            \
    typedef element_type etype;                                                          \
    enum { esize = sizeof(etype) };                                                      \
                                                                                         \
    void base_remove_at(size_t size, int i) { base_class::remove_at(size, i); }          \
                                                                                         \
   public:                                                                               \
    /* creation */                                                                       \
    array_name() : base_class()                       {}                                 \
    explicit array_name(const int length) : base_class(esize, length) {}                          \
    array_name(const int length, const etype fx)      { initialize(length, fx); }        \
    void initialize(const int length)     { base_class::initialize(esize, length); }     \
    void initialize(const int length, const etype fx) {                                  \
      initialize(length);                                                                \
      for (int i = 0; i < length; i++) ((etype*)_data)[i] = fx;                          \
    }                                                                                    \
                                                                                         \
    /* standard operations */                                                            \
    etype& operator [] (const int i) const {                                             \
      assert(0 <= i && i < length(), "index out of bounds");                             \
      return ((etype*)_data)[i];                                                         \
    }                                                                                    \
                                                                                         \
    int index_of(const etype x) const {                                                  \
      int i = length();                                                                  \
      while (i-- > 0 && ((etype*)_data)[i] != x) ;                                       \
      /* i < 0 || ((etype*)_data)_data[i] == x */                                        \
      return i;                                                                          \
    }                                                                                    \
                                                                                         \
    void sort(int f(etype*, etype*))             { base_class::sort(esize, (ftype)f); }  \
    bool contains(const etype x) const           { return index_of(x) >= 0; }            \
                                                                                         \
    /* deprecated operations - for compatibility with GrowableArray only */              \
    etype  at(const int i) const                 { return (*this)[i]; }                  \
    void   at_put(const int i, const etype x)    { (*this)[i] = x; }                     \
    etype* adr_at(const int i)                   { return &(*this)[i]; }                 \
    int    find(const etype x)                   { return index_of(x); }                 \
  };                                                                                     \


#define define_array(array_name,element_type)                                            \
  define_generic_array(array_name, element_type, ResourceArray)


#define define_stack(stack_name,array_name)                                              \
  class stack_name: public array_name {                                                  \
   protected:                                                                            \
    int _size;                                                                           \
                                                                                         \
    void grow(const int i, const etype fx) {                                             \
      assert(i >= length(), "index too small");                                          \
      if (i >= size()) expand(esize, i, _size);                                          \
      for (int j = length(); j <= i; j++) ((etype*)_data)[j] = fx;                       \
      _length = i+1;                                                                     \
    }                                                                                    \
                                                                                         \
   public:                                                                               \
    /* creation */                                                                       \
    stack_name() : array_name()                     { _size = 0; }                       \
    stack_name(const int size)                      { initialize(size); }                \
    stack_name(const int size, const etype fx)      { initialize(size, fx); }            \
    void initialize(const int size, const etype fx) {                                    \
      _size = size;                                                                      \
      array_name::initialize(size, fx);                                                  \
      /* _length == size, allocation and size are the same */                            \
    }                                                                                    \
    void initialize(const int size) {                                                    \
      _size = size;                                                                      \
      array_name::initialize(size);                                                      \
      _length = 0;          /* reset length to zero; _size records the allocation */     \
    }                                                                                    \
                                                                                         \
    /* standard operations */                                                            \
    int size() const                             { return _size; }                       \
                                                                                         \
    int push(const etype x) {                                                            \
      int len = length();                                                                \
      if (len >= size()) expand(esize, len, _size);                                      \
      ((etype*)_data)[len] = x;                                                          \
      _length = len+1;                                                                   \
      return len;                                                                        \
    }                                                                                    \
                                                                                         \
    etype pop() {                                                                        \
      assert(!is_empty(), "stack is empty");                                             \
      return ((etype*)_data)[--_length];                                                 \
    }                                                                                    \
                                                                                         \
    etype top() const {                                                                  \
      assert(!is_empty(), "stack is empty");                                             \
      return ((etype*)_data)[length() - 1];                                              \
    }                                                                                    \
                                                                                         \
    void push_all(const stack_name* stack) {                                             \
      const int l = stack->length();                                                     \
      for (int i = 0; i < l; i++) push(((etype*)(stack->_data))[i]);                     \
    }                                                                                    \
                                                                                         \
    etype at_grow(const int i, const etype fx) {                                         \
      if (i >= length()) grow(i, fx);                                                    \
      return ((etype*)_data)[i];                                                         \
    }                                                                                    \
                                                                                         \
    void at_put_grow(const int i, const etype x, const etype fx) {                       \
      if (i >= length()) grow(i, fx);                                                    \
      ((etype*)_data)[i] = x;                                                            \
    }                                                                                    \
                                                                                         \
    void truncate(const int length) {                                                    \
      assert(0 <= length && length <= this->length(), "illegal length");                 \
      _length = length;                                                                  \
    }                                                                                    \
                                                                                         \
    void remove_at(int i)                        { base_remove_at(esize, i); }           \
    void remove(etype x)                         { remove_at(index_of(x)); }             \
                                                                                         \
    /* inserts the given element before the element at index i */                        \
    void insert_before(const int i, const etype el)  {                                   \
      int len = length();                                                                \
      int new_length = len + 1;                                                          \
      if (new_length >= size()) expand(esize, new_length, _size);                        \
      for (int j = len - 1; j >= i; j--) {                                               \
        ((etype*)_data)[j + 1] = ((etype*)_data)[j];                                     \
      }                                                                                  \
      _length = new_length;                                                              \
      at_put(i, el);                                                                     \
    }                                                                                    \
                                                                                         \
    /* inserts contents of the given stack before the element at index i */              \
    void insert_before(const int i, const stack_name *st) {                              \
      if (st->length() == 0) return;                                                     \
      int len = length();                                                                \
      int st_len = st->length();                                                         \
      int new_length = len + st_len;                                                     \
      if (new_length >= size()) expand(esize, new_length, _size);                        \
      int j;                                                                             \
      for (j = len - 1; j >= i; j--) {                                                   \
        ((etype*)_data)[j + st_len] = ((etype*)_data)[j];                                \
      }                                                                                  \
      for (j = 0; j < st_len; j++) {                                                     \
        ((etype*)_data)[i + j] = ((etype*)st->_data)[j];                                 \
      }                                                                                  \
      _length = new_length;                                                              \
    }                                                                                    \
                                                                                         \
    /* deprecated operations - for compatibility with GrowableArray only */              \
    int  capacity() const                        { return size(); }                      \
    void clear()                                 { truncate(0); }                        \
    void trunc_to(const int length)              { truncate(length); }                   \
    int  append(const etype x)                   { return push(x); }                     \
    void appendAll(const stack_name* stack)      { push_all(stack); }                    \
    etype last() const                           { return top(); }                       \
  };                                                                                     \


#define define_resource_list(element_type)                                               \
  define_generic_array(element_type##Array, element_type, ResourceArray)                 \
  define_stack(element_type##List, element_type##Array)

#define define_resource_pointer_list(element_type)                                       \
  define_generic_array(element_type##Array, element_type *, ResourceArray)               \
  define_stack(element_type##List, element_type##Array)

#define define_c_heap_list(element_type)                                                 \
  define_generic_array(element_type##Array, element_type, CHeapArray)                    \
  define_stack(element_type##List, element_type##Array)

#define define_c_heap_pointer_list(element_type)                                         \
  define_generic_array(element_type##Array, element_type *, CHeapArray)                  \
  define_stack(element_type##List, element_type##Array)


// Arrays for basic types

define_array(boolArray, bool)          define_stack(boolStack, boolArray)
define_array(intArray , int )          define_stack(intStack , intArray )

// Array for metadata allocation

template <typename T>
class Array: public MetaspaceObj {
  friend class MetadataFactory;
  friend class VMStructs;
  friend class MethodHandleCompiler;           // special case
  friend class WhiteBox;
protected:
  int _length;                                 // the number of array elements
  T   _data[1];                                // the array memory

  void initialize(int length) {
    _length = length;
  }

 private:
  // Turn off copy constructor and assignment operator.
  Array(const Array<T>&);
  void operator=(const Array<T>&);

  void* operator new(size_t size, ClassLoaderData* loader_data, int length, bool read_only, TRAPS) throw() {
    size_t word_size = Array::size(length);
    return (void*) Metaspace::allocate(loader_data, word_size, read_only,
                                       MetaspaceObj::array_type(sizeof(T)), THREAD);
  }

  static size_t byte_sizeof(int length) { return sizeof(Array<T>) + MAX2(length - 1, 0) * sizeof(T); }

  // WhiteBox API helper.
  // Can't distinguish between array of length 0 and length 1,
  // will always return 0 in those cases.
  static int bytes_to_length(size_t bytes)       {
    assert(is_size_aligned(bytes, BytesPerWord), "Must be, for now");

    if (sizeof(Array<T>) >= bytes) {
      return 0;
    }

    size_t left = bytes - sizeof(Array<T>);
    assert(is_size_aligned(left, sizeof(T)), "Must be");

    size_t elements = left / sizeof(T);
    assert(elements <= (size_t)INT_MAX, "number of elements " SIZE_FORMAT "doesn't fit into an int.", elements);

    int length = (int)elements;

    assert((size_t)size(length) * BytesPerWord == bytes,
           "Expected: " SIZE_FORMAT " got: " SIZE_FORMAT,
           bytes, (size_t)size(length) * BytesPerWord);

    return length;
  }

  explicit Array(int length) : _length(length) {
    assert(length >= 0, "illegal length");
  }

  Array(int length, T init) : _length(length) {
    assert(length >= 0, "illegal length");
    for (int i = 0; i < length; i++) {
      _data[i] = init;
    }
  }

 public:

  // standard operations
  int  length() const                 { return _length; }
  T* data()                           { return _data; }
  bool is_empty() const               { return length() == 0; }

  int index_of(const T& x) const {
    int i = length();
    while (i-- > 0 && _data[i] != x) ;

    return i;
  }

  // sort the array.
  bool contains(const T& x) const      { return index_of(x) >= 0; }

  T    at(int i) const                 { assert(i >= 0 && i< _length, "oob: 0 <= %d < %d", i, _length); return _data[i]; }
  void at_put(const int i, const T& x) { assert(i >= 0 && i< _length, "oob: 0 <= %d < %d", i, _length); _data[i] = x; }
  T*   adr_at(const int i)             { assert(i >= 0 && i< _length, "oob: 0 <= %d < %d", i, _length); return &_data[i]; }
  int  find(const T& x)                { return index_of(x); }

  T at_acquire(const int which)              { return OrderAccess::load_acquire(adr_at(which)); }
  void release_at_put(int which, T contents) { OrderAccess::release_store(adr_at(which), contents); }

  static int size(int length) {
    return align_size_up(byte_sizeof(length), BytesPerWord) / BytesPerWord;
  }

  int size() {
    return size(_length);
  }

  static int length_offset_in_bytes() { return (int) (offset_of(Array<T>, _length)); }
  // Note, this offset don't have to be wordSize aligned.
  static int base_offset_in_bytes() { return (int) (offset_of(Array<T>, _data)); };

  // FIXME: How to handle this?
  void print_value_on(outputStream* st) const {
    st->print("Array<T>(" INTPTR_FORMAT ")", p2i(this));
  }

#ifndef PRODUCT
  void print(outputStream* st) {
     for (int i = 0; i< _length; i++) {
       st->print_cr("%d: " INTPTR_FORMAT, i, (intptr_t)at(i));
     }
  }
  void print() { print(tty); }
#endif // PRODUCT
};


#endif // SHARE_VM_UTILITIES_ARRAY_HPP

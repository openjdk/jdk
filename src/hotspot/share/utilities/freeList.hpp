/*
 * Copyright (c) 2022 SAP SE. All rights reserved.
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_FREELIST_HPP
#define SHARE_UTILITIES_FREELIST_HPP

#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

class outputStream;

// Simple classic double-headed, self-counting (optional), freelist of dead elements

template <class T>
static T* Tptr_at(T* p)                   { return *((T**)p); }

template <class T>
static void set_Tptr_at(T* p, T* newval)  { *((T**)p) = newval; }

template <class T>
static void set_Tptr_at_null(T* p)        { set_Tptr_at(p, (T*) NULL); }

template <class T>
class FreeList {

  static const bool _counting = true;//DEBUG_ONLY(true) NOT_DEBUG(false);

  T* _head;
  T* _tail;
  uintx _count;
  uintx _peak_count;

#ifdef ASSERT
  void quick_verify() const {
    assert((_head == NULL) == (_tail == NULL), "malformed list");
    if (_counting) {
      assert( (_count == 0 && _head == NULL && _tail == NULL) ||
              (_count == 1 && _head == _tail) ||
              (_count > 1 && _head != _tail), "malformed list");
    }
  }
#endif

public:

  FreeList() :
    _head(NULL), _tail(NULL),
    _count(0), _peak_count(0)
  {}

  FreeList(T* head, T* tail, uintx count) :
    _head(head), _tail(tail),
    _count(count), _peak_count(count)
  {}

  T* head() const { return _head; }
  T* tail() const { return _tail; }

  // Remove the topmost element from the freelist; NULL if empty
  T* take_top() {
    T* p = _head;
    if (p != NULL) {
      _head = Tptr_at(_head);
      if (_head == NULL) {
        _tail = NULL;
      }
      if (_counting) {
        assert(_count > 0, "sanity");
        _count --;
      }
      DEBUG_ONLY(set_Tptr_at_null(p);)
      DEBUG_ONLY(quick_verify();)
    }
    return p;
  }

  void prepend(T* elem) {
    if (_head == NULL) {
      assert(!_counting || 0 == _count, "invalid freelist count");
      _head = _tail = elem;
      set_Tptr_at_null(_head);
    } else {
      assert(!_counting || 0 < _count, "invalid freelist count");
      set_Tptr_at(elem, _head);
      _head = elem;
    }
    if (_counting) {
      _count ++;
      _peak_count = MAX2(_peak_count, _count);
    }
    DEBUG_ONLY(quick_verify();)
  }

  // Take over other list, reset other list
  void take_elements(FreeList& other) {
    assert(empty(), "must be empty");
    if (!other.empty()) {
      _head = other.head();
      _tail = other.tail();
      if (_counting) {
        _count = other.count();
        _peak_count = other.peak_count();
      }
      other.reset();
      DEBUG_ONLY(verify();)
    }
  }

  // Prepends list items to this list and resets the other list.
  void prepend_list(FreeList& other) {
    DEBUG_ONLY(other.quick_verify();)
    if (!other.empty()) {
      if (empty()) {
        take_elements(other);
      } else {
        set_Tptr_at(other.tail(), _head);
        _head = other.head();
        if (_counting) {
          _count += other.count();
          _peak_count = MAX2(_peak_count, _count);
        }
        DEBUG_ONLY(verify();)
        other.reset();
      }
    }
  }

  void prepend_list(T* head, T* tail, uintx count) {
    FreeList tmp(head, tail, count);
    add_list_to_front(tmp);
  }

  // Reset also resets the peak count, so the history is lost.
  void reset() {
    _head = _tail = NULL;
    if (_counting) {
      _count = _peak_count = 0;
    }
  }

  bool empty() const { return _head == NULL; }

  // True if list counts itself
  bool counting() const       { return _counting; }
  uintx count() const         { return _count; }      // Note: only if counting
  uintx peak_count() const    { return _peak_count; } // Note: only if counting

  struct Closure {
    // return false to stop iterating
    virtual bool do_it(const T* element) = 0;
  };
  // Call Closure.doit(). If that returns false, iteration is cancelled at that point.
  uintx iterate(Closure& closure) const;

#ifdef ASSERT
  // paranoid = true: dup check
  void verify(bool paranoid = false) const;
#endif

  void print_on(outputStream* st, bool print_elems = false) const;

}; // Freelist

#endif // SHARE_UTILITIES_FREELIST_HPP

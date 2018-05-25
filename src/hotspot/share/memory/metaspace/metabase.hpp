/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_MEMORY_METASPACE_METABASE_HPP
#define SHARE_MEMORY_METASPACE_METABASE_HPP

#include "utilities/globalDefinitions.hpp"

namespace metaspace {

// Super class of Metablock and Metachunk to allow them to
// be put on the FreeList and in the BinaryTreeDictionary.
template <class T>
class Metabase {
  size_t _word_size;
  T*     _next;
  T*     _prev;

 protected:
  Metabase(size_t word_size) : _word_size(word_size), _next(NULL), _prev(NULL) {}

 public:
  T* next() const         { return _next; }
  T* prev() const         { return _prev; }
  void set_next(T* v)     { _next = v; assert(v != this, "Boom");}
  void set_prev(T* v)     { _prev = v; assert(v != this, "Boom");}
  void clear_next()       { set_next(NULL); }
  void clear_prev()       { set_prev(NULL); }

  size_t size() const volatile { return _word_size; }
  void set_size(size_t v) { _word_size = v; }

  void link_next(T* ptr)  { set_next(ptr); }
  void link_prev(T* ptr)  { set_prev(ptr); }
  void link_after(T* ptr) {
    link_next(ptr);
    if (ptr != NULL) ptr->link_prev((T*)this);
  }

  uintptr_t* end() const        { return ((uintptr_t*) this) + size(); }

  bool cantCoalesce() const     { return false; }

  // Debug support
#ifdef ASSERT
  void* prev_addr() const { return (void*)&_prev; }
  void* next_addr() const { return (void*)&_next; }
  void* size_addr() const { return (void*)&_word_size; }
#endif
  bool verify_chunk_in_free_list(T* tc) const { return true; }
  bool verify_par_locked() { return true; }

  void assert_is_mangled() const {/* Don't check "\*/}

  bool is_free()                 { return true; }
};

} // namespace metaspace

#endif /* SHARE_MEMORY_METASPACE_METABASE_HPP */


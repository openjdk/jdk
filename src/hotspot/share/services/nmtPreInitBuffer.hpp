/*
 * Copyright (c) 2021 SAP SE. All rights reserved.
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_SERVICES_NMT_PREINIT_BUFFER_HPP
#define SHARE_SERVICES_NMT_PREINIT_BUFFER_HPP



#include "memory/allocation.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

#if INCLUDE_NMT

class outputStream;


// VM initialization wrt NMT:
//
//---------------------------------------------------------------
//-> launcher dlopen's libjvm                           ^
//   -> dynamic C++ initialization                      |
//           of libjvm                                  |
//                                                      |
//-> launcher starts new thread (maybe)          NMT pre-init phase
//                                                      |
//-> launcher invokes CreateJavaVM                      |
//   -> VM initialization before arg parsing            |
//   -> VM argument parsing                             v
//   -> NMT initialization  -------------------------------------
//                                                      ^
//   ...                                                |
//   -> VM life...                               NMT post-init phase
//   ...                                                |
//                                                      v
//----------------------------------------------------------------


// NMT is initialized after argument parsing, long after the first C-heap allocations happen
//  in the VM. Therefore it misses the first n allocations, and when those allocations are freed,
//  it needs to treat those special.
// To separate pre-init allocations from post-init allocations, pre-init allocations are not
//  taken from C-heap at all but silently redirected from os::malloc() to an NMT internal static
//  preallocated buffer.
//
// This class implements this NMT pre-init-buffer. It consists of two parts:
// - a very small one (128k), allocated upfront right at VM start. It is in 99% of all cases
//   sufficient to bring the VM up to post-init phase.
// - Only if there is a lot of memory allocated during preinit-phase this buffer will not be
//   enough. That can happen e.g. with outlandishly long command lines. In that case, a second,
//   much larger overflow buffer will be dynamically allocated and used.

class NMTPreInitBuffer : public AllStatic {

  class Slab {
    const size_t _capacity;
    size_t _used;
    uint8_t _buffer[1];
  public:
    Slab(size_t capacity) : _capacity(capacity), _used(0) {}
    uint8_t* allocate(size_t s);
    bool contains(const uint8_t* p) const {
      return p >= _buffer && p < _buffer + _capacity;
    }
    void print_on(outputStream* st) const;
  };

  static Slab* create_slab(size_t capacity);

  // A small primary buffer, large enough to cover the pre-init allocations of
  //  99% of all normal VM runs.
  static Slab* _primary_buffer;
  static const size_t primary_buffer_size = 128 * K;

  // A large secondary buffer, gets only allocated if the primary buffer gets
  //  exhausted.
  static Slab* _overflow_buffer;
  static const size_t overflow_buffer_size = 2 * M;

public:

  // Allocate s bytes from the preinit buffer. Can only be called before NMT initialization.
  // On buffer exhaustion, NMT is switched off and C-heap is returned instead (release);
  // in debug builds we assert.
  static uint8_t* allocate_block(size_t s, MEMFLAGS flag);

  // Reallocate an allocation originally allocated from the preinit buffer within the preinit
  //  buffer. Can only be called before NMT initialization.
  static uint8_t* reallocate_block(uint8_t* old, size_t s, MEMFLAGS flag);

  // Move an allocation originally allocated from the preinit buffers into the regular
  //  C-heap. Can only be called *after* NMT initialization.
  static uint8_t* evacuate_block_to_c_heap(uint8_t* old, size_t s, MEMFLAGS flag);

  // Attempts to free a block originally allocated from the preinit buffer (only rolls
  // back top allocation).
  static void free_block(uint8_t* old);

  // This needs to be fast
  inline static bool contains_block(const uint8_t* p) {
    return _primary_buffer->contains(p) ||
           (_overflow_buffer != NULL && _overflow_buffer->contains(p));
  }

  // print a string describing the current buffer state
  static void print_state(outputStream* st);

};

#endif // INCLUDE_NMT

#endif // SHARE_SERVICES_NMT_PREINIT_BUFFER_HPP

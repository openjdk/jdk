/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CDS_SERIALIZECLOSURE_HPP
#define SHARE_CDS_SERIALIZECLOSURE_HPP

#include "memory/allocation.hpp"
#include "oops/oopsHierarchy.hpp"

// A handy way to read/write auxiliary information in the CDS archive file
// (without the burden of adding new fields into FileMapHeader).

class SerializeClosure : public StackObj {
public:
  // Return bool indicating whether closure implements read or write.
  virtual bool reading() const = 0;

  // Read/write the void pointer pointed to by p.
  virtual void do_ptr(void** p) = 0;

  // Read/write the 32-bit unsigned integer pointed to by p.
  virtual void do_u4(u4* p) = 0;

  // Read/write the int pointed to by p.
  virtual void do_int(int* p) = 0;

  // Read/write the bool pointed to by p.
  virtual void do_bool(bool* p) = 0;

  // Iterate on the pointers from p[0] through p[num_pointers-1]
  void do_ptrs(void** p, size_t size) {
    assert((intptr_t)p % sizeof(intptr_t) == 0, "bad alignment");
    assert(size % sizeof(intptr_t) == 0, "bad size");
    do_tag((int)size);
    while (size > 0) {
      do_ptr(p);
      p++;
      size -= sizeof(intptr_t);
    }
  }

  // Address of the first element being written (write only)
  virtual char* region_top() = 0;

  // Check/write the tag.  If reading, then compare the tag against
  // the passed in value and fail is they don't match.  This allows
  // for verification that sections of the serialized data are of the
  // correct length.
  virtual void do_tag(int tag) = 0;

  bool writing() {
    return !reading();
  }

  // Useful alias
  template <typename T> void do_ptr(T** p) { do_ptr((void**)p); }
};

#endif // SHARE_CDS_SERIALIZECLOSURE_HPP

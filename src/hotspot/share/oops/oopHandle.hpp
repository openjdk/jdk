/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_OOPS_OOPHANDLE_HPP
#define SHARE_VM_OOPS_OOPHANDLE_HPP

#include "oops/oop.hpp"

// Simple class for encapsulating oop pointers stored in metadata.
// These are different from Handle.  The Handle class stores pointers
// to oops on the stack, and manages the allocation from a thread local
// area in the constructor.
// This assumes that the caller will allocate the handle in the appropriate
// area.  The reason for the encapsulation is to help with naming and to allow
// future uses for read barriers.

class OopHandle {
private:
  oop* _obj;

public:
  OopHandle() : _obj(NULL) {}
  OopHandle(oop* w) : _obj(w) {}

  inline oop resolve() const;
  inline oop peek() const;

  // Used only for removing handle.
  oop* ptr_raw() const { return _obj; }
};

#endif // SHARE_VM_OOPS_OOPHANDLE_HPP

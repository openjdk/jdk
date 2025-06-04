/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_COMPILER_CHEAPSTRINGHOLDER_HPP
#define SHARE_COMPILER_CHEAPSTRINGHOLDER_HPP

#include "memory/allocation.hpp"

// Holder for a C-Heap allocated String
// The user must ensure that the destructor is called, or at least clear.
class CHeapStringHolder : public StackObj {
private:
  char* _string;

public:
  CHeapStringHolder() : _string(nullptr) {}
  ~CHeapStringHolder() { clear(); };
  NONCOPYABLE(CHeapStringHolder);

  // Allocate memory to hold a copy of string
  void set(const char* string);

  // Release allocated memory
  void clear();

  const char* get() const { return _string; };
};

#endif // SHARE_COMPILER_CHEAPSTRINGHOLDER_HPP

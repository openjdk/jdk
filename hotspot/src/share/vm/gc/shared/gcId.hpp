/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_SHARED_GCID_HPP
#define SHARE_VM_GC_SHARED_GCID_HPP

#include "memory/allocation.hpp"

class GCId : public AllStatic {
  friend class GCIdMark;
  friend class GCIdMarkAndRestore;
  static uint _next_id;
  static const uint UNDEFINED = (uint)-1;
  static const uint create();

 public:
  // Returns the currently active GC id. Asserts that there is an active GC id.
  static const uint current();
  // Same as current() but can return undefined() if no GC id is currently active
  static const uint current_raw();
  static const uint undefined() { return UNDEFINED; }
};

class GCIdMark : public StackObj {
  uint _gc_id;
 public:
  GCIdMark();
  GCIdMark(uint gc_id);
  ~GCIdMark();
};

class GCIdMarkAndRestore : public StackObj {
  uint _gc_id;
  uint _previous_gc_id;
 public:
  GCIdMarkAndRestore();
  ~GCIdMarkAndRestore();
};

#endif // SHARE_VM_GC_SHARED_GCID_HPP

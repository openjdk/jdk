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

#ifndef SHARE_VM_JFR_LEAKPROFILER_UTILITIES_UNIFIEDOOP_HPP
#define SHARE_VM_JFR_LEAKPROFILER_UTILITIES_UNIFIEDOOP_HPP

#include "oops/oop.inline.hpp"

class UnifiedOop : public AllStatic {
 public:
  static const bool is_narrow(const oop* ref) {
    assert(ref != NULL, "invariant");
    return 1 == (((u8)ref) & 1);
  }

  static const oop* decode(const oop* ref) {
    assert(ref != NULL, "invariant");
    return is_narrow(ref) ? (const oop*)(((u8)ref) & ~1) : ref;
  }

  static const oop* encode(narrowOop* ref) {
    assert(ref != NULL, "invariant");
    return (const oop*)((u8)ref | 1);
  }

  static oop dereference(const oop* ref) {
    assert(ref != NULL, "invariant");
    return is_narrow(ref) ?
      (oop)RawAccess<>::oop_load((narrowOop*)decode(ref)) :
      (oop)RawAccess<>::oop_load(const_cast<oop*>(ref));

  }
};

#endif // SHARE_VM_JFR_LEAKPROFILER_UTILITIES_UNIFIEDOOP_HPP

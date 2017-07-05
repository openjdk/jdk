/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_X86_VM_INTERPRETER_X86_HPP
#define CPU_X86_VM_INTERPRETER_X86_HPP

 public:
  static Address::ScaleFactor stackElementScale() {
    return NOT_LP64(Address::times_4) LP64_ONLY(Address::times_8);
  }

  // Offset from rsp (which points to the last stack element)
  static int expr_offset_in_bytes(int i) { return stackElementSize * i; }

  // Stack index relative to tos (which points at value)
  static int expr_index_at(int i)        { return stackElementWords * i; }

  // Already negated by c++ interpreter
  static int local_index_at(int i) {
    assert(i <= 0, "local direction already negated");
    return stackElementWords * i;
  }

#endif // CPU_X86_VM_INTERPRETER_X86_HPP

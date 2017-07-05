/*
 * Copyright (c) 2003, 2010, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2007, 2008 Red Hat, Inc.
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

#ifndef CPU_ZERO_VM_INTERPRETER_ZERO_HPP
#define CPU_ZERO_VM_INTERPRETER_ZERO_HPP

 public:
  static void invoke_method(methodOop method, address entry_point, TRAPS) {
    ((ZeroEntry *) entry_point)->invoke(method, THREAD);
  }
  static void invoke_osr(methodOop method,
                         address   entry_point,
                         address   osr_buf,
                         TRAPS) {
    ((ZeroEntry *) entry_point)->invoke_osr(method, osr_buf, THREAD);
  }

 public:
  static int expr_index_at(int i) {
    return stackElementWords * i;
  }

  static int expr_offset_in_bytes(int i) {
    return stackElementSize * i;
  }

  static int local_index_at(int i) {
    assert(i <= 0, "local direction already negated");
    return stackElementWords * i;
  }

#endif // CPU_ZERO_VM_INTERPRETER_ZERO_HPP

/*
 * Copyright (c) 2000, 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_PRIMS_METHODCOMPARATOR_HPP
#define SHARE_VM_PRIMS_METHODCOMPARATOR_HPP

#include "interpreter/bytecodeStream.hpp"
#include "oops/constantPool.hpp"
#include "oops/method.hpp"

// methodComparator provides an interface for determining if methods of
// different versions of classes are equivalent or switchable

class MethodComparator {
 private:
  static BytecodeStream *_s_old, *_s_new;
  static ConstantPool* _old_cp;
  static ConstantPool* _new_cp;

  static bool args_same(Bytecodes::Code c_old, Bytecodes::Code c_new);
  static bool pool_constants_same(int cpi_old, int cpi_new);
  static int check_stack_and_locals_size(Method* old_method, Method* new_method);

 public:
  // Check if the new method is equivalent to the old one modulo constant pool (EMCP).
  // Intuitive definition: two versions of the same method are EMCP, if they don't differ
  // on the source code level. Practically, we check whether the only difference between
  // method versions is some constantpool indices embedded into the bytecodes, and whether
  // these indices eventually point to the same constants for both method versions.
  static bool methods_EMCP(Method* old_method, Method* new_method);

};

#endif // SHARE_VM_PRIMS_METHODCOMPARATOR_HPP

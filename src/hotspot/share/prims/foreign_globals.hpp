/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 */

#ifndef SHARE_PRIMS_FOREIGN_GLOBALS
#define SHARE_PRIMS_FOREIGN_GLOBALS

#include "oops/oopsHierarchy.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/macros.hpp"

#include CPU_HEADER(foreign_globals)

class ForeignGlobals {
private:
  struct {
    int inputStorage_offset;
    int outputStorage_offset;
    int volatileStorage_offset;
    int stackAlignment_offset;
    int shadowSpace_offset;
  } ABI;

  struct {
    int index_offset;
  } VMS;

  struct {
    int size_offset;
    int arguments_next_pc_offset;
    int stack_args_bytes_offset;
    int stack_args_offset;
    int input_type_offsets_offset;
    int output_type_offsets_offset;
  } BL;

  ForeignGlobals();

  static const ForeignGlobals& instance();

  template<typename R>
  static R cast(oop theOop);

  template<typename T, typename Func>
  void loadArray(objArrayOop jarray, int type_index, GrowableArray<T>& array, Func converter) const;

  const ABIDescriptor parse_abi_descriptor_impl(jobject jabi) const;
  const BufferLayout parse_buffer_layout_impl(jobject jlayout) const;
public:
  static const ABIDescriptor parse_abi_descriptor(jobject jabi);
  static const BufferLayout parse_buffer_layout(jobject jlayout);
};

#endif // SHARE_PRIMS_FOREIGN_GLOBALS

/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, Red Hat Inc. All rights reserved.
 * Copyright (c) 2020, 2023, Huawei Technologies Co., Ltd. All rights reserved.
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

#ifndef CPU_RISCV_STUBROUTINES_RISCV_HPP
#define CPU_RISCV_STUBROUTINES_RISCV_HPP

// This file holds the platform specific parts of the StubRoutines
// definition. See stubRoutines.hpp for a description on how to
// extend it.

static bool returns_to_call_stub(address return_pc) {
  return return_pc == _call_stub_return_address;
}

// emit enum used to size per-blob code buffers

#define DEFINE_BLOB_SIZE(blob_name, size) \
  _ ## blob_name ## _code_size = size,

enum platform_dependent_constants {
  STUBGEN_ARCH_BLOBS_DO(DEFINE_BLOB_SIZE)
};

#undef DEFINE_BLOB_SIZE

class riscv {
 friend class StubGenerator;
 friend class StubRoutines;
#if INCLUDE_JVMCI
  friend class JVMCIVMStructs;
#endif

  // declare fields for arch-specific entries

#define DECLARE_ARCH_ENTRY(arch, blob_name, stub_name, field_name, getter_name) \
  static address STUB_FIELD_NAME(field_name) ;

#define DECLARE_ARCH_ENTRY_INIT(arch, blob_name, stub_name, field_name, getter_name, init_function) \
  DECLARE_ARCH_ENTRY(arch, blob_name, stub_name, field_name, getter_name)

private:
  STUBGEN_ARCH_ENTRIES_DO(DECLARE_ARCH_ENTRY, DECLARE_ARCH_ENTRY_INIT)

#undef DECLARE_ARCH_ENTRY_INIT
#undef DECLARE_ARCH_ENTRY

  static bool _completed;

 public:

  // declare getters for arch-specific entries

#define DEFINE_ARCH_ENTRY_GETTER(arch, blob_name, stub_name, field_name, getter_name) \
  static address getter_name() { return STUB_FIELD_NAME(field_name) ; }

#define DEFINE_ARCH_ENTRY_GETTER_INIT(arch, blob_name, stub_name, field_name, getter_name, init_function) \
  DEFINE_ARCH_ENTRY_GETTER(arch, blob_name, stub_name, field_name, getter_name)

  STUBGEN_ARCH_ENTRIES_DO(DEFINE_ARCH_ENTRY_GETTER, DEFINE_ARCH_ENTRY_GETTER_INIT)

#undef DEFINE_ARCH_ENTRY_GETTER_INIT
#undef DEFINE_ARCH_ENTRY_GETTER

  static bool complete() {
    return _completed;
  }

  static void set_completed() {
    _completed = true;
  }

private:
  static juint    _crc_table[];
};

#endif // CPU_RISCV_STUBROUTINES_RISCV_HPP

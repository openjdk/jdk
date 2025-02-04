/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2016, 2017 SAP SE. All rights reserved.
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

#ifndef CPU_S390_STUBROUTINES_S390_HPP
#define CPU_S390_STUBROUTINES_S390_HPP

// This file holds the platform specific parts of the StubRoutines
// definition. See stubRoutines.hpp for a description on how to extend it.

static bool returns_to_call_stub(address return_pc) { return return_pc == _call_stub_return_address; }

// emit enum used to size per-blob code buffers

#define DEFINE_BLOB_SIZE(blob_name, size) \
  _ ## blob_name ## _code_size = size,

enum platform_dependent_constants {
  STUBGEN_ARCH_BLOBS_DO(DEFINE_BLOB_SIZE)
};

#undef DEFINE_BLOB_SIZE

// MethodHandles adapters
enum method_handles_platform_dependent_constants {
  method_handles_adapters_code_size = 5000
};

#define CRC32_COLUMN_SIZE 256
#define CRC32_BYFOUR
#ifdef CRC32_BYFOUR
  #define CRC32_TABLES 8
#else
  #define CRC32_TABLES 1
#endif

// Comapct string intrinsics: Translate table for string inflate intrinsic. Used by trot instruction.
#define TROT_ALIGNMENT   8  // Required by instruction,
                            // guaranteed by jlong table element type.
#define TROT_COLUMN_SIZE (256*sizeof(jchar)/sizeof(jlong))

class zarch {
 friend class StubGenerator;

 public:
  enum { nof_instance_allocators = 10 };

  // allocator lock values
  enum {
    unlocked = 0,
    locked   = 1
  };

  // declare fields for arch-specific entries

#define DECLARE_ARCH_ENTRY(arch, blob_name, stub_name, field_name, getter_name) \
  static address STUB_FIELD_NAME(field_name) ;

#define DECLARE_ARCH_ENTRY_INIT(arch, blob_name, stub_name, field_name, getter_name, init_function) \
  DECLARE_ARCH_ENTRY(arch, blob_name, stub_name, field_name, getter_name)

private:
  STUBGEN_ARCH_ENTRIES_DO(DECLARE_ARCH_ENTRY, DECLARE_ARCH_ENTRY_INIT)

#undef DECLARE_ARCH_ENTRY_INIT
#undef DECLARE_ARCH_ENTRY

 private:

  static int _atomic_memory_operation_lock;

  static juint   _crc_table[CRC32_TABLES][CRC32_COLUMN_SIZE];
  static juint   _crc32c_table[CRC32_TABLES][CRC32_COLUMN_SIZE];

  // Comapct string intrinsics: Translate table for string inflate intrinsic. Used by trot instruction.
  static address _trot_table_addr;
  static jlong   _trot_table[TROT_COLUMN_SIZE];

 public:

  // declare getters for arch-specific entries

#define DEFINE_ARCH_ENTRY_GETTER(arch, blob_name, stub_name, field_name, getter_name) \
  static address getter_name() { return STUB_FIELD_NAME(field_name) ; }

#define DEFINE_ARCH_ENTRY_GETTER_INIT(arch, blob_name, stub_name, field_name, getter_name, init_function) \
  DEFINE_ARCH_ENTRY_GETTER(arch, blob_name, stub_name, field_name, getter_name)

  STUBGEN_ARCH_ENTRIES_DO(DEFINE_ARCH_ENTRY_GETTER, DEFINE_ARCH_ENTRY_GETTER_INIT)

#undef DEFINE_ARCH_ENTRY_GETTER_INIT
#undef DEFINE_ARCH_ENTRY_GETTER

  // Global lock for everyone who needs to use atomic_compare_and_exchange
  // or atomic_increment -- should probably use more locks for more
  // scalability -- for instance one for each eden space or group of.

  // Address of the lock for atomic_compare_and_exchange.
  static int* atomic_memory_operation_lock_addr() { return &_atomic_memory_operation_lock; }

  // Accessor and mutator for _atomic_memory_operation_lock.
  static int atomic_memory_operation_lock() { return _atomic_memory_operation_lock; }
  static void set_atomic_memory_operation_lock(int value) { _atomic_memory_operation_lock = value; }

  static void generate_load_absolute_address(MacroAssembler* masm, Register table, address table_addr, uint64_t table_contents);
  static void generate_load_crc_table_addr(MacroAssembler* masm, Register table);
  static void generate_load_crc32c_table_addr(MacroAssembler* masm, Register table);

  // Comapct string intrinsics: Translate table for string inflate intrinsic. Used by trot instruction.
  static void generate_load_trot_table_addr(MacroAssembler* masm, Register table);
};

#endif // CPU_S390_STUBROUTINES_S390_HPP

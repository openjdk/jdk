/*
 * Copyright 1997-2010 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

// This file holds the platform specific parts of the StubRoutines
// definition. See stubRoutines.hpp for a description on how to
// extend it.


// So unfortunately c2 will call with a pc from a frame object
// (already adjusted) and a raw pc (unadjusted), so we need to check both.
// It didn't use to be like this before adapter removal.
static bool returns_to_call_stub(address return_pc)   {
  return ((return_pc + frame::pc_return_offset) == _call_stub_return_address) ||
          (return_pc == _call_stub_return_address );
}

enum /* platform_dependent_constants */ {
  // %%%%%%%% May be able to shrink this a lot
  code_size1 = 20000,           // simply increase if too small (assembler will crash if too small)
  code_size2 = 20000            // simply increase if too small (assembler will crash if too small)
};

// MethodHandles adapters
enum method_handles_platform_dependent_constants {
  method_handles_adapters_code_size = 5000
};

class Sparc {
 friend class StubGenerator;

 public:
  enum { nof_instance_allocators = 10 };

  // allocator lock values
  enum {
    unlocked = 0,
    locked   = 1
  };

  enum {
    v8_oop_lock_ignore_bits = 2,
    v8_oop_lock_bits = 4,
    nof_v8_oop_lock_cache_entries = 1 << (v8_oop_lock_bits+v8_oop_lock_ignore_bits),
    v8_oop_lock_mask = right_n_bits(v8_oop_lock_bits),
    v8_oop_lock_mask_in_place = v8_oop_lock_mask << v8_oop_lock_ignore_bits
  };

  static int _v8_oop_lock_cache[nof_v8_oop_lock_cache_entries];

 private:
  static address _test_stop_entry;
  static address _stop_subroutine_entry;
  static address _flush_callers_register_windows_entry;

  static int _atomic_memory_operation_lock;

  static address _partial_subtype_check;

 public:
  // %%% global lock for everyone who needs to use atomic_compare_and_exchange
  // %%% or atomic_increment -- should probably use more locks for more
  // %%% scalability-- for instance one for each eden space or group of

  // address of the lock for atomic_compare_and_exchange
  static int* atomic_memory_operation_lock_addr() { return &_atomic_memory_operation_lock; }

  // accessor and mutator for _atomic_memory_operation_lock
  static int atomic_memory_operation_lock() { return _atomic_memory_operation_lock; }
  static void set_atomic_memory_operation_lock(int value) { _atomic_memory_operation_lock = value; }

  // test assembler stop routine by setting registers
  static void (*test_stop_entry()) ()                     { return CAST_TO_FN_PTR(void (*)(void), _test_stop_entry); }

  // a subroutine for debugging assembler code
  static address stop_subroutine_entry_address()          { return (address)&_stop_subroutine_entry; }

  // flushes (all but current) register window
  static intptr_t* (*flush_callers_register_windows_func())() { return CAST_TO_FN_PTR(intptr_t* (*)(void), _flush_callers_register_windows_entry); }

  static address partial_subtype_check()                  { return _partial_subtype_check; }
};

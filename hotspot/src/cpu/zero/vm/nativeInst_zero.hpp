/*
 * Copyright (c) 2003, 2010, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2007 Red Hat, Inc.
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

#ifndef CPU_ZERO_VM_NATIVEINST_ZERO_HPP
#define CPU_ZERO_VM_NATIVEINST_ZERO_HPP

#include "asm/assembler.hpp"
#include "memory/allocation.hpp"
#include "runtime/icache.hpp"
#include "runtime/os.hpp"
#include "utilities/top.hpp"

// We have interfaces for the following instructions:
// - NativeInstruction
// - - NativeCall
// - - NativeMovConstReg
// - - NativeMovConstRegPatching
// - - NativeJump
// - - NativeIllegalOpCode
// - - NativeReturn
// - - NativeReturnX (return with argument)
// - - NativePushConst
// - - NativeTstRegMem

// The base class for different kinds of native instruction abstractions.
// Provides the primitive operations to manipulate code relative to this.

class NativeInstruction VALUE_OBJ_CLASS_SPEC {
 public:
  bool is_jump() {
    ShouldNotCallThis();
  }

  bool is_safepoint_poll() {
    ShouldNotCallThis();
  }
};

inline NativeInstruction* nativeInstruction_at(address address) {
  ShouldNotCallThis();
}

class NativeCall : public NativeInstruction {
 public:
  enum zero_specific_constants {
    instruction_size = 0 // not used within the interpreter
  };

  address instruction_address() const {
    ShouldNotCallThis();
  }

  address next_instruction_address() const {
    ShouldNotCallThis();
  }

  address return_address() const {
    ShouldNotCallThis();
  }

  address destination() const {
    ShouldNotCallThis();
  }

  void set_destination_mt_safe(address dest) {
    ShouldNotCallThis();
  }

  void verify_alignment() {
    ShouldNotCallThis();
  }

  void verify() {
    ShouldNotCallThis();
  }

  static bool is_call_before(address return_address) {
    ShouldNotCallThis();
  }
};

inline NativeCall* nativeCall_before(address return_address) {
  ShouldNotCallThis();
}

inline NativeCall* nativeCall_at(address address) {
  ShouldNotCallThis();
}

class NativeMovConstReg : public NativeInstruction {
 public:
  address next_instruction_address() const {
    ShouldNotCallThis();
  }

  intptr_t data() const {
    ShouldNotCallThis();
  }

  void set_data(intptr_t x) {
    ShouldNotCallThis();
  }
};

inline NativeMovConstReg* nativeMovConstReg_at(address address) {
  ShouldNotCallThis();
}

class NativeMovRegMem : public NativeInstruction {
 public:
  int offset() const {
    ShouldNotCallThis();
  }

  void set_offset(intptr_t x) {
    ShouldNotCallThis();
  }

  void add_offset_in_bytes(int add_offset) {
    ShouldNotCallThis();
  }
};

inline NativeMovRegMem* nativeMovRegMem_at(address address) {
  ShouldNotCallThis();
}

class NativeJump : public NativeInstruction {
 public:
  enum zero_specific_constants {
    instruction_size = 0 // not used within the interpreter
  };

  address jump_destination() const {
    ShouldNotCallThis();
  }

  void set_jump_destination(address dest) {
    ShouldNotCallThis();
  }

  static void check_verified_entry_alignment(address entry,
                                             address verified_entry) {
  }

  static void patch_verified_entry(address entry,
                                   address verified_entry,
                                   address dest);
};

inline NativeJump* nativeJump_at(address address) {
  ShouldNotCallThis();
}

class NativeGeneralJump : public NativeInstruction {
 public:
  address jump_destination() const {
    ShouldNotCallThis();
  }

  static void insert_unconditional(address code_pos, address entry) {
    ShouldNotCallThis();
  }

  static void replace_mt_safe(address instr_addr, address code_buffer) {
    ShouldNotCallThis();
  }
};

inline NativeGeneralJump* nativeGeneralJump_at(address address) {
  ShouldNotCallThis();
}

#endif // CPU_ZERO_VM_NATIVEINST_ZERO_HPP

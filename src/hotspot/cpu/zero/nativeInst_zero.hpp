/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_ZERO_NATIVEINST_ZERO_HPP
#define CPU_ZERO_NATIVEINST_ZERO_HPP

#include "asm/assembler.hpp"
#include "runtime/icache.hpp"
#include "runtime/os.hpp"

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
// - - NativeDeoptInstruction

// The base class for different kinds of native instruction abstractions.
// Provides the primitive operations to manipulate code relative to this.

class NativeInstruction {
 public:
  bool is_jump() {
    ShouldNotCallThis();
    return false;
  }

  bool is_safepoint_poll() {
    ShouldNotCallThis();
    return false;
  }
};

inline NativeInstruction* nativeInstruction_at(address address) {
  ShouldNotCallThis();
  return nullptr;
}

class NativeCall : public NativeInstruction {
 public:
  enum zero_specific_constants {
    instruction_size = 0 // not used within the interpreter
  };

  address instruction_address() const {
    ShouldNotCallThis();
    return nullptr;
  }

  address next_instruction_address() const {
    ShouldNotCallThis();
    return nullptr;
  }

  address return_address() const {
    ShouldNotCallThis();
    return nullptr;
  }

  address destination() const {
    ShouldNotCallThis();
    return nullptr;
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
    return false;
  }
};

inline NativeCall* nativeCall_before(address return_address) {
  ShouldNotCallThis();
  return nullptr;
}

inline NativeCall* nativeCall_at(address address) {
  ShouldNotCallThis();
  return nullptr;
}

class NativeMovConstReg : public NativeInstruction {
 public:
  address next_instruction_address() const {
    ShouldNotCallThis();
    return nullptr;
  }

  intptr_t data() const {
    ShouldNotCallThis();
    return 0;
  }

  void set_data(intptr_t x) {
    ShouldNotCallThis();
  }
};

inline NativeMovConstReg* nativeMovConstReg_at(address address) {
  ShouldNotCallThis();
  return nullptr;
}

class NativeMovRegMem : public NativeInstruction {
 public:
  int offset() const {
    ShouldNotCallThis();
    return 0;
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
  return nullptr;
}

class NativeJump : public NativeInstruction {
 public:
  enum zero_specific_constants {
    instruction_size = 0 // not used within the interpreter
  };

  address jump_destination() const {
    ShouldNotCallThis();
    return nullptr;
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
  return nullptr;
}

class NativeGeneralJump : public NativeInstruction {
 public:
  address jump_destination() const {
    ShouldNotCallThis();
    return nullptr;
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
  return nullptr;
}

class NativePostCallNop: public NativeInstruction {
public:
  bool check() const { Unimplemented(); return false; }
  bool decode(int32_t& oopmap_slot, int32_t& cb_offset) const { Unimplemented(); return false; }
  bool patch(int32_t oopmap_slot, int32_t cb_offset) { Unimplemented(); return false; }
  void make_deopt() { Unimplemented(); }
};

inline NativePostCallNop* nativePostCallNop_at(address address) {
  Unimplemented();
  return nullptr;
}

class NativeDeoptInstruction: public NativeInstruction {
public:
  address instruction_address() const       { Unimplemented(); return nullptr; }
  address next_instruction_address() const  { Unimplemented(); return nullptr; }

  void  verify() { Unimplemented(); }

  static bool is_deopt_at(address instr) {
    Unimplemented();
    return false;
  }

  // MT-safe patching
  static void insert(address code_pos) {
    Unimplemented();
  }
};

#endif // CPU_ZERO_NATIVEINST_ZERO_HPP

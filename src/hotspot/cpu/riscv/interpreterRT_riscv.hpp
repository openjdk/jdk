/*
 * Copyright (c) 1998, 2019, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, Red Hat Inc. All rights reserved.
 * Copyright (c) 2020, 2021, Huawei Technologies Co., Ltd. All rights reserved.
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

#ifndef CPU_RISCV_INTERPRETERRT_RISCV_HPP
#define CPU_RISCV_INTERPRETERRT_RISCV_HPP

// This is included in the middle of class Interpreter.
// Do not include files here.

// native method calls

class SignatureHandlerGenerator: public NativeSignatureIterator {
 private:
  MacroAssembler* _masm;
  unsigned int _num_reg_fp_args;
  unsigned int _num_reg_int_args;
  int _stack_offset;

  void pass_int();
  void pass_long();
  void pass_float();
  void pass_double();
  void pass_object();

  Register next_gpr();
  FloatRegister next_fpr();
  int next_stack_offset();

 public:
  // Creation
  SignatureHandlerGenerator(const methodHandle& method, CodeBuffer* buffer);
  virtual ~SignatureHandlerGenerator() {
    _masm = NULL;
  }

  // Code generation
  void generate(uint64_t fingerprint);

  // Code generation support
  static Register from();
  static Register to();
  static Register temp();
};

#endif // CPU_RISCV_INTERPRETERRT_RISCV_HPP

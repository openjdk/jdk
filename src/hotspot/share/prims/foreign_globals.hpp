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

#include "code/vmreg.hpp"
#include "oops/oopsHierarchy.hpp"
#include "runtime/sharedRuntime.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/macros.hpp"

#include CPU_HEADER(foreign_globals)

class CallConvClosure {
public:
  virtual int calling_convention(BasicType* sig_bt, VMRegPair* regs, int num_args) const = 0;
};

struct CallRegs {
  VMReg* _arg_regs;
  int _args_length;

  VMReg* _ret_regs;
  int _rets_length;
};

class ForeignGlobals {
private:
  struct {
    int inputStorage_offset;
    int outputStorage_offset;
    int volatileStorage_offset;
    int stackAlignment_offset;
    int shadowSpace_offset;
    int targetAddrStorage_offset;
    int retBufAddrStorage_offset;
  } ABI;

  struct {
    int index_offset;
    int type_offset;
  } VMS;

  struct {
    int arg_regs_offset;
    int ret_regs_offset;
  } CallConvOffsets;

  ForeignGlobals();

  static const ForeignGlobals& instance();

  template<typename T, typename Func>
  void loadArray(objArrayOop jarray, int type_index, GrowableArray<T>& array, Func converter) const;

  const ABIDescriptor parse_abi_descriptor_impl(jobject jabi) const;
  const CallRegs parse_call_regs_impl(jobject jconv) const;
  VMReg parse_vmstorage_impl(oop storage) const;
public:
  static const ABIDescriptor parse_abi_descriptor(jobject jabi);
  static const CallRegs parse_call_regs(jobject jconv);
  static VMReg vmstorage_to_vmreg(int type, int index);
  static VMReg parse_vmstorage(oop storage);
};



class JavaCallConv : public CallConvClosure {
public:
  int calling_convention(BasicType* sig_bt, VMRegPair* regs, int num_args) const override {
    return SharedRuntime::java_calling_convention(sig_bt, regs, num_args);
  }
};

class NativeCallConv : public CallConvClosure {
  const VMReg* _input_regs;
  int _input_regs_length;
public:
  NativeCallConv(const VMReg* input_regs, int input_regs_length) :
    _input_regs(input_regs),
    _input_regs_length(input_regs_length) {
  }
  NativeCallConv(const GrowableArray<VMReg>& input_regs)
   : NativeCallConv(input_regs.data(), input_regs.length()) {}

  int calling_convention(BasicType* sig_bt, VMRegPair* out_regs, int num_args) const override;
};

class RegSpiller {
  const VMReg* _regs;
  int _num_regs;
  int _spill_size_bytes;
public:
  RegSpiller(const VMReg* regs, int num_regs) :
    _regs(regs), _num_regs(num_regs),
    _spill_size_bytes(compute_spill_area(regs, num_regs)) {
  }
  RegSpiller(const GrowableArray<VMReg>& regs) : RegSpiller(regs.data(), regs.length()) {
  }

  int spill_size_bytes() const { return _spill_size_bytes; }
  void generate_spill(MacroAssembler* masm, int rsp_offset) const { return generate(masm, rsp_offset, true); }
  void generate_fill(MacroAssembler* masm, int rsp_offset) const { return generate(masm, rsp_offset, false); }

private:
  static int compute_spill_area(const VMReg* regs, int num_regs);
  void generate(MacroAssembler* masm, int rsp_offset, bool is_spill) const;

  static int pd_reg_size(VMReg reg);
  static void pd_store_reg(MacroAssembler* masm, int offset, VMReg reg);
  static void pd_load_reg(MacroAssembler* masm, int offset, VMReg reg);
};

struct Move {
  BasicType bt;
  VMRegPair from;
  VMRegPair to;
};

class ArgumentShuffle {
private:
  GrowableArray<Move> _moves;
  int _out_arg_stack_slots;
public:
  ArgumentShuffle(
    BasicType* in_sig_bt, int num_in_args,
    BasicType* out_sig_bt, int num_out_args,
    const CallConvClosure* input_conv, const CallConvClosure* output_conv,
    VMReg shuffle_temp);

  int out_arg_stack_slots() const { return _out_arg_stack_slots; }
  void generate(MacroAssembler* masm, VMReg tmp, int in_stk_bias, int out_stk_bias) const {
    pd_generate(masm, tmp, in_stk_bias, out_stk_bias);
  }

  void print_on(outputStream* os) const;
private:
  void pd_generate(MacroAssembler* masm, VMReg tmp, int in_stk_bias, int out_stk_bias) const;
};

#endif // SHARE_PRIMS_FOREIGN_GLOBALS

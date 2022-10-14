/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "prims/vmstorage.inline.hpp"
#include "runtime/sharedRuntime.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/macros.hpp"

#include CPU_HEADER(foreignGlobals)

// needs to match StubLocations in Java code.
// placeholder locations to be filled in by
// the code gen code
class StubLocations {
public:
  enum Location : uint32_t {
    TARGET_ADDRESS,
    RETURN_BUFFER,
    MAX
  };
private:
  VMStorage _locs[MAX];
public:
  StubLocations();

  void set(uint32_t loc, VMStorage storage);
  void set_frame_data(uint32_t loc, int offset);
  VMStorage get(uint32_t loc) const;
  VMStorage get(VMStorage placeholder) const;
  int data_offset(uint32_t loc) const;
};

class CallingConventionClosure {
public:
  virtual int calling_convention(const BasicType* sig_bt, VMStorage* regs, int num_args) const = 0;
};

struct CallRegs {
  GrowableArray<VMStorage> _arg_regs;
  GrowableArray<VMStorage> _ret_regs;

  CallRegs(int num_args, int num_rets)
    : _arg_regs(num_args), _ret_regs(num_rets) {}
};


class ForeignGlobals {
private:
  template<typename T>
  static void parse_register_array(objArrayOop jarray, int type_index, GrowableArray<T>& array, T (*converter)(int));

public:
  static const ABIDescriptor parse_abi_descriptor(jobject jabi);
  static const CallRegs parse_call_regs(jobject jconv);
  static VMStorage parse_vmstorage(oop storage);
};

class JavaCallingConvention : public CallingConventionClosure {
public:
  int calling_convention(const BasicType* sig_bt, VMStorage* regs, int num_args) const override;
};

class NativeCallingConvention : public CallingConventionClosure {
  GrowableArray<VMStorage> _input_regs;
public:
  NativeCallingConvention(const GrowableArray<VMStorage>& input_regs)
   : _input_regs(input_regs) {}

  int calling_convention(const BasicType* sig_bt, VMStorage* out_regs, int num_args) const override;
};

class RegSpiller {
  GrowableArray<VMStorage> _regs;
  int _spill_size_bytes;
public:
  RegSpiller(const GrowableArray<VMStorage>& regs) : _regs(regs), _spill_size_bytes(compute_spill_area(regs)) {
  }

  int spill_size_bytes() const { return _spill_size_bytes; }
  void generate_spill(MacroAssembler* masm, int rsp_offset) const { return generate(masm, rsp_offset, true); }
  void generate_fill(MacroAssembler* masm, int rsp_offset) const { return generate(masm, rsp_offset, false); }

private:
  static int compute_spill_area(const GrowableArray<VMStorage>& regs);
  void generate(MacroAssembler* masm, int rsp_offset, bool is_spill) const;

  static int pd_reg_size(VMStorage reg);
  static void pd_store_reg(MacroAssembler* masm, int offset, VMStorage reg);
  static void pd_load_reg(MacroAssembler* masm, int offset, VMStorage reg);
};

struct Move {
  BasicType bt;
  VMStorage from;
  VMStorage to;
};

class ArgumentShuffle {
private:
  GrowableArray<Move> _moves;
  int _out_arg_bytes;
public:
  ArgumentShuffle(
    BasicType* in_sig_bt, int num_in_args,
    BasicType* out_sig_bt, int num_out_args,
    const CallingConventionClosure* input_conv, const CallingConventionClosure* output_conv,
    VMStorage shuffle_temp);

  int out_arg_bytes() const { return _out_arg_bytes; }
  void generate(MacroAssembler* masm, VMStorage tmp, int in_stk_bias, int out_stk_bias, const StubLocations& locs) const {
    pd_generate(masm, tmp, in_stk_bias, out_stk_bias, locs);
  }

  void print_on(outputStream* os) const;
private:
  void pd_generate(MacroAssembler* masm, VMStorage tmp, int in_stk_bias, int out_stk_bias, const StubLocations& locs) const;
};

#endif // SHARE_PRIMS_FOREIGN_GLOBALS

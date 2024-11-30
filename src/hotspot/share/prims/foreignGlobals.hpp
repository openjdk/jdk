/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "prims/vmstorage.hpp"
#include "runtime/sharedRuntime.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/macros.hpp"

#include CPU_HEADER(foreignGlobals)

// Needs to match jdk.internal.foreign.abi.StubLocations in Java code.
// Placeholder locations to be filled in by the code gen code.
class StubLocations {
public:
  enum Location : uint32_t {
    TARGET_ADDRESS,
    RETURN_BUFFER,
    CAPTURED_STATE_BUFFER,
    LOCATION_LIMIT
  };
private:
  VMStorage _locs[LOCATION_LIMIT];
public:
  StubLocations();

  void set(uint32_t loc, VMStorage storage);
  void set_frame_data(uint32_t loc, int offset);
  VMStorage get(uint32_t loc) const;
  VMStorage get(VMStorage placeholder) const;
  int data_offset(uint32_t loc) const;
};

// C++ 'mirror' of jdk.internal.foreign.abi.UpcallLinker.CallRegs
struct CallRegs {
  GrowableArray<VMStorage> _arg_regs;
  GrowableArray<VMStorage> _ret_regs;

  CallRegs(int num_args, int num_rets)
    : _arg_regs(num_args), _ret_regs(num_rets) {}
};


class ForeignGlobals {
private:
  template<typename T>
  static void parse_register_array(objArrayOop jarray, StorageType type_index, GrowableArray<T>& array, T (*converter)(int));

public:
  static bool is_foreign_linker_supported();

  // Helpers for translating from the Java to C++ representation
  static const ABIDescriptor parse_abi_descriptor(jobject jabi);
  static const CallRegs parse_call_regs(jobject jconv);
  static VMStorage parse_vmstorage(oop storage);

  // Adapter from SharedRuntime::java_calling_convention to a 'single VMStorage per value' form.
  // Doesn't assign (invalid) storage for T_VOID entries in the signature, which are instead ignored.
  static int java_calling_convention(const BasicType* signature, int num_args, GrowableArray<VMStorage>& out_regs);

  // Computes the space (in bytes) that is taken up by stack arguments
  static int compute_out_arg_bytes(const GrowableArray<VMStorage>& out_regs);

  // Replace placeholders (see class StubLocations above) with actual locations in a stub frame
  static GrowableArray<VMStorage> replace_place_holders(const GrowableArray<VMStorage>& regs, const StubLocations& locs);

  // The receiver method handle for upcalls is injected manually into the argument list by the upcall stub. We need a
  // filtered list to generate an argument shuffle for the rest of the arguments.
  static GrowableArray<VMStorage> upcall_filter_receiver_reg(const GrowableArray<VMStorage>& unfiltered_regs);

  // Oop offsets are not passed on to native code.
  // Filter out the registers of oop offsets to create a list that we can pass to ArgumentShuffle.
  static GrowableArray<VMStorage> downcall_filter_offset_regs(const GrowableArray<VMStorage>& regs, BasicType* signature,
                                                              int num_args, bool& has_objects);
};

// Helper class useful for generating spills and fills of a set of registers.
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

// Class used to compute and generate a shuffle between 2 lists of VMStorages.
// The lists must have the same size.
// Each VMStorage in the source list (in_regs) is shuffled into the
// the VMStorage at the same index in the destination list (out_regs).
// This class helps to automatically compute an order of moves that makes
// sure not to destroy values accidentally by interfering moves, in case the
// source and destination registers overlap.
class ArgumentShuffle {
private:
  class ComputeMoveOrder;
  struct Move {
    VMStorage from;
    VMStorage to;
  };

  GrowableArray<Move> _moves;
public:
  ArgumentShuffle(
    const GrowableArray<VMStorage>& in_regs,
    const GrowableArray<VMStorage>& out_regs,
    VMStorage shuffle_temp);

  void generate(MacroAssembler* masm, VMStorage tmp, int in_stk_bias, int out_stk_bias) const {
    pd_generate(masm, tmp, in_stk_bias, out_stk_bias);
  }

  void print_on(outputStream* os) const;
private:
  void pd_generate(MacroAssembler* masm, VMStorage tmp, int in_stk_bias, int out_stk_bias) const;
};

#endif // SHARE_PRIMS_FOREIGN_GLOBALS

/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_AARCH64_GC_Z_ZBARRIERSETASSEMBLER_AARCH64_HPP
#define CPU_AARCH64_GC_Z_ZBARRIERSETASSEMBLER_AARCH64_HPP

#include "code/vmreg.hpp"
#include "oops/accessDecorators.hpp"
#ifdef COMPILER2
#include "gc/z/c2/zBarrierSetC2.hpp"
#include "opto/optoreg.hpp"
#endif // COMPILER2

#ifdef COMPILER1
class LIR_Address;
class LIR_Assembler;
class LIR_Opr;
class StubAssembler;
class ZLoadBarrierStubC1;
class ZStoreBarrierStubC1;
#endif // COMPILER1

#ifdef COMPILER2
class MachNode;
class Node;
#endif // COMPILER2

// ZBarrierRelocationFormatLoadGoodBeforeTbX is used for both tbnz and tbz
// They are patched in the same way, their immediate value has the same
// structure
const int ZBarrierRelocationFormatLoadGoodBeforeTbX  = 0;
const int ZBarrierRelocationFormatMarkBadBeforeMov   = 1;
const int ZBarrierRelocationFormatStoreGoodBeforeMov = 2;
const int ZBarrierRelocationFormatStoreBadBeforeMov  = 3;

class ZBarrierSetAssembler : public ZBarrierSetAssemblerBase {
public:
  virtual void load_at(MacroAssembler* masm,
                       DecoratorSet decorators,
                       BasicType type,
                       Register dst,
                       Address src,
                       Register tmp1,
                       Register tmp2);

  void store_barrier_fast(MacroAssembler* masm,
                          Address ref_addr,
                          Register rnew_zaddress,
                          Register rnew_zpointer,
                          Register rtmp,
                          bool in_nmethod,
                          bool is_atomic,
                          Label& medium_path,
                          Label& medium_path_continuation) const;

  void store_barrier_medium(MacroAssembler* masm,
                            Address ref_addr,
                            Register rtmp1,
                            Register rtmp2,
                            Register rtmp3,
                            bool is_native,
                            bool is_atomic,
                            Label& medium_path_continuation,
                            Label& slow_path,
                            Label& slow_path_continuation) const;

  virtual void store_at(MacroAssembler* masm,
                        DecoratorSet decorators,
                        BasicType type,
                        Address dst,
                        Register val,
                        Register tmp1,
                        Register tmp2,
                        Register tmp3);

  virtual void arraycopy_prologue(MacroAssembler* masm,
                                  DecoratorSet decorators,
                                  bool is_oop,
                                  Register src,
                                  Register dst,
                                  Register count,
                                  RegSet saved_regs);

  virtual void copy_load_at(MacroAssembler* masm,
                            DecoratorSet decorators,
                            BasicType type,
                            size_t bytes,
                            Register dst1,
                            Register dst2,
                            Address src,
                            Register tmp);

  virtual void copy_store_at(MacroAssembler* masm,
                             DecoratorSet decorators,
                             BasicType type,
                             size_t bytes,
                             Address dst,
                             Register src1,
                             Register src2,
                             Register tmp1,
                             Register tmp2,
                             Register tmp3);

  virtual void copy_load_at(MacroAssembler* masm,
                            DecoratorSet decorators,
                            BasicType type,
                            size_t bytes,
                            FloatRegister dst1,
                            FloatRegister dst2,
                            Address src,
                            Register tmp1,
                            Register tmp2,
                            FloatRegister vec_tmp);

  virtual void copy_store_at(MacroAssembler* masm,
                             DecoratorSet decorators,
                             BasicType type,
                             size_t bytes,
                             Address dst,
                             FloatRegister src1,
                             FloatRegister src2,
                             Register tmp1,
                             Register tmp2,
                             Register tmp3,
                             FloatRegister vec_tmp1,
                             FloatRegister vec_tmp2,
                             FloatRegister vec_tmp3);

  virtual void try_resolve_jobject_in_native(MacroAssembler* masm,
                                             Register jni_env,
                                             Register robj,
                                             Register tmp,
                                             Label& slowpath);

  virtual NMethodPatchingType nmethod_patching_type() { return NMethodPatchingType::conc_instruction_and_data_patch; }

  void patch_barrier_relocation(address addr, int format);

  void patch_barriers() {}

#ifdef COMPILER1
  void generate_c1_color(LIR_Assembler* ce, LIR_Opr ref) const;
  void generate_c1_uncolor(LIR_Assembler* ce, LIR_Opr ref) const;

  void generate_c1_load_barrier(LIR_Assembler* ce,
                                LIR_Opr ref,
                                ZLoadBarrierStubC1* stub,
                                bool on_non_strong) const;

  void generate_c1_load_barrier_stub(LIR_Assembler* ce,
                                     ZLoadBarrierStubC1* stub) const;

  void generate_c1_load_barrier_runtime_stub(StubAssembler* sasm,
                                             DecoratorSet decorators) const;

  void generate_c1_store_barrier(LIR_Assembler* ce,
                                 LIR_Address* addr,
                                 LIR_Opr new_zaddress,
                                 LIR_Opr new_zpointer,
                                 ZStoreBarrierStubC1* stub) const;

  void generate_c1_store_barrier_stub(LIR_Assembler* ce,
                                      ZStoreBarrierStubC1* stub) const;

  void generate_c1_store_barrier_runtime_stub(StubAssembler* sasm,
                                              bool self_healing) const;
#endif // COMPILER1

#ifdef COMPILER2
  OptoReg::Name refine_register(const Node* node,
                                OptoReg::Name opto_reg);

  void generate_c2_load_barrier_stub(MacroAssembler* masm,
                                     ZLoadBarrierStubC2* stub) const;
  void generate_c2_store_barrier_stub(MacroAssembler* masm,
                                      ZStoreBarrierStubC2* stub) const;
#endif // COMPILER2

  void check_oop(MacroAssembler* masm, Register obj, Register tmp1, Register tmp2, Label& error);
};

#ifdef COMPILER2

// Load barriers on aarch64 are implemented with a test-and-branch immediate instruction.
// This immediate has a max delta of 32K. Because of this the branch is implemented with
// a small jump, as follows:
//      __ tbz(ref, barrier_Relocation::unpatched, good);
//      __ b(*stub->entry());
//      __ bind(good);
//
// If we can guarantee that the *stub->entry() label is within 32K we can replace the above
// code with:
//      __ tbnz(ref, barrier_Relocation::unpatched, *stub->entry());
//
// From the branch shortening part of PhaseOutput we get a pessimistic code size that the code
// will not grow beyond.
//
// The stubs objects are created and registered when the load barriers are emitted. The decision
// between emitting the long branch or the test and branch is done at this point and uses the
// pessimistic code size from branch shortening.
//
// After the code has been emitted the barrier set will emit all the stubs. When the stubs are
// emitted we know the real code size. Because of this the trampoline jump can be skipped in
// favour of emitting the stub directly if it does not interfere with the next trampoline stub.
// (With respect to test and branch distance)
//
// The algorithm for emitting the load barrier branches and stubs now have three versions
// depending on the distance between the barrier and the stub.
// Version 1: Not Reachable with a test-and-branch immediate
// Version 2: Reachable with a test-and-branch immediate via trampoline
// Version 3: Reachable with a test-and-branch immediate without trampoline
//
//     +--------------------- Code ----------------------+
//     |                      ***                        |
//     | b(stub1)                                        | (Version 1)
//     |                      ***                        |
//     | tbnz(ref, barrier_Relocation::unpatched, tramp) | (Version 2)
//     |                      ***                        |
//     | tbnz(ref, barrier_Relocation::unpatched, stub3) | (Version 3)
//     |                      ***                        |
//     +--------------------- Stub ----------------------+
//     | tramp: b(stub2)                                 | (Trampoline slot)
//     | stub3:                                          |
//     |                  * Stub Code*                   |
//     | stub1:                                          |
//     |                  * Stub Code*                   |
//     | stub2:                                          |
//     |                  * Stub Code*                   |
//     +-------------------------------------------------+
//
//  Version 1: Is emitted if the pessimistic distance between the branch instruction and the current
//             trampoline slot cannot fit in a test and branch immediate.
//
//  Version 2: Is emitted if the distance between the branch instruction and the current trampoline
//             slot can fit in a test and branch immediate. But emitting the stub directly would
//             interfere with the next trampoline.
//
//  Version 3: Same as version two but emitting the stub directly (skipping the trampoline) does not
//             interfere with the next trampoline.
//
class ZLoadBarrierStubC2Aarch64 : public ZLoadBarrierStubC2 {
private:
  Label _test_and_branch_reachable_entry;
  const int _offset;
  bool _deferred_emit;
  bool _test_and_branch_reachable;

  ZLoadBarrierStubC2Aarch64(const MachNode* node, Address ref_addr, Register ref);
  ZLoadBarrierStubC2Aarch64(const MachNode* node, Address ref_addr, Register ref, int offset);

  int get_stub_size();
public:
  static ZLoadBarrierStubC2Aarch64* create(const MachNode* node, Address ref_addr, Register ref);
  static ZLoadBarrierStubC2Aarch64* create(const MachNode* node, Address ref_addr, Register ref, int offset);

  virtual void emit_code(MacroAssembler& masm);
  bool is_test_and_branch_reachable();
  Label* entry();
};


class ZStoreBarrierStubC2Aarch64 : public ZStoreBarrierStubC2 {
private:
  bool _deferred_emit;

  ZStoreBarrierStubC2Aarch64(const MachNode* node, Address ref_addr, Register new_zaddress, Register new_zpointer, bool is_native, bool is_atomic);

public:
  static ZStoreBarrierStubC2Aarch64* create(const MachNode* node, Address ref_addr, Register new_zaddress, Register new_zpointer, bool is_native, bool is_atomic);

  virtual void emit_code(MacroAssembler& masm);
};

#endif // COMPILER2

#endif // CPU_AARCH64_GC_Z_ZBARRIERSETASSEMBLER_AARCH64_HPP

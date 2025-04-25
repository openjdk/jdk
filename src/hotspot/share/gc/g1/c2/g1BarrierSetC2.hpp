/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_C2_G1BARRIERSETC2_HPP
#define SHARE_GC_G1_C2_G1BARRIERSETC2_HPP

#include "gc/shared/c2/cardTableBarrierSetC2.hpp"

class PhaseTransform;
class Type;
class TypeFunc;

const int G1C2BarrierPre         = 1;
const int G1C2BarrierPost        = 2;
const int G1C2BarrierPostNotNull = 4;

class G1BarrierStubC2 : public BarrierStubC2 {
public:
  G1BarrierStubC2(const MachNode* node);
  virtual void emit_code(MacroAssembler& masm) = 0;
};

class G1PreBarrierStubC2 : public G1BarrierStubC2 {
private:
  Register _obj;
  Register _pre_val;
  Register _thread;
  Register _tmp1;
  Register _tmp2;

protected:
  G1PreBarrierStubC2(const MachNode* node);

public:
  static bool needs_barrier(const MachNode* node);
  static G1PreBarrierStubC2* create(const MachNode* node);
  void initialize_registers(Register obj, Register pre_val, Register thread, Register tmp1 = noreg, Register tmp2 = noreg);
  Register obj() const;
  Register pre_val() const;
  Register thread() const;
  Register tmp1() const;
  Register tmp2() const;
  virtual void emit_code(MacroAssembler& masm);
};

class G1PostBarrierStubC2 : public G1BarrierStubC2 {
private:
  Register _thread;
  Register _tmp1;
  Register _tmp2;
  Register _tmp3;

protected:
  G1PostBarrierStubC2(const MachNode* node);

public:
  static bool needs_barrier(const MachNode* node);
  static G1PostBarrierStubC2* create(const MachNode* node);
  void initialize_registers(Register thread, Register tmp1 = noreg, Register tmp2 = noreg, Register tmp3 = noreg);
  Register thread() const;
  Register tmp1() const;
  Register tmp2() const;
  Register tmp3() const;
  virtual void emit_code(MacroAssembler& masm);
};

class G1BarrierSetC2: public CardTableBarrierSetC2 {
private:
  void analyze_dominating_barriers() const;

protected:
  bool g1_can_remove_pre_barrier(GraphKit* kit,
                                 PhaseValues* phase,
                                 Node* adr,
                                 BasicType bt,
                                 uint adr_idx) const;

  bool g1_can_remove_post_barrier(GraphKit* kit,
                                  PhaseValues* phase, Node* store,
                                  Node* adr) const;

  int get_store_barrier(C2Access& access) const;

  virtual Node* load_at_resolved(C2Access& access, const Type* val_type) const;
  virtual Node* store_at_resolved(C2Access& access, C2AccessValue& val) const;
  virtual Node* atomic_cmpxchg_val_at_resolved(C2AtomicParseAccess& access, Node* expected_val,
                                               Node* new_val, const Type* value_type) const;
  virtual Node* atomic_cmpxchg_bool_at_resolved(C2AtomicParseAccess& access, Node* expected_val,
                                                Node* new_val, const Type* value_type) const;
  virtual Node* atomic_xchg_at_resolved(C2AtomicParseAccess& access, Node* new_val, const Type* value_type) const;

public:
  virtual void eliminate_gc_barrier(PhaseMacroExpand* macro, Node* node) const;
  virtual void eliminate_gc_barrier_data(Node* node) const;
  virtual bool expand_barriers(Compile* C, PhaseIterGVN& igvn) const;
  virtual uint estimated_barrier_size(const Node* node) const;
  virtual bool can_initialize_object(const StoreNode* store) const;
  virtual void clone_at_expansion(PhaseMacroExpand* phase,
                                  ArrayCopyNode* ac) const;
  virtual void* create_barrier_state(Arena* comp_arena) const;
  virtual void emit_stubs(CodeBuffer& cb) const;
  virtual void elide_dominated_barrier(MachNode* mach) const;
  virtual void late_barrier_analysis() const;

#ifndef PRODUCT
  virtual void dump_barrier_data(const MachNode* mach, outputStream* st) const;
#endif
};

#endif // SHARE_GC_G1_C2_G1BARRIERSETC2_HPP

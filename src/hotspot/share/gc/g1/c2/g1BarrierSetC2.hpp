/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
  void initialize_registers(Register obj, Register pre_val, Register thread, Register tmp1, Register tmp2);
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

protected:
  G1PostBarrierStubC2(const MachNode* node);

public:
  static bool needs_barrier(const MachNode* node);
  static G1PostBarrierStubC2* create(const MachNode* node);
  void initialize_registers(Register thread, Register tmp1, Register tmp2);
  Register thread() const;
  Register tmp1() const;
  Register tmp2() const;
  virtual void emit_code(MacroAssembler& masm);
};

class G1BarrierSetC2: public CardTableBarrierSetC2 {
protected:
  bool g1_can_remove_pre_barrier(GraphKit* kit,
                                 PhaseValues* phase,
                                 Node* adr,
                                 BasicType bt,
                                 uint adr_idx) const;

  bool g1_can_remove_post_barrier(GraphKit* kit,
                                  PhaseValues* phase, Node* store,
                                  Node* adr) const;

  int get_store_barrier(C2Access& access, C2AccessValue& val) const;

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
  virtual void clone_at_expansion(PhaseMacroExpand* phase,
                                  ArrayCopyNode* ac) const;
  virtual void* create_barrier_state(Arena* comp_arena) const;
  virtual void emit_stubs(CodeBuffer& cb) const;
  virtual void late_barrier_analysis() const;

#ifndef PRODUCT
  virtual void dump_barrier_data(const MachNode* mach, outputStream* st) const;
#endif
};

#if G1_LATE_BARRIER_MIGRATION_SUPPORT
class G1BarrierSetC2Early : public CardTableBarrierSetC2 {
protected:
  virtual void pre_barrier(GraphKit* kit,
                           bool do_load,
                           Node* ctl,
                           Node* obj,
                           Node* adr,
                           uint adr_idx,
                           Node* val,
                           const TypeOopPtr* val_type,
                           Node* pre_val,
                           BasicType bt) const;

  virtual void post_barrier(GraphKit* kit,
                            Node* ctl,
                            Node* store,
                            Node* obj,
                            Node* adr,
                            uint adr_idx,
                            Node* val,
                            BasicType bt,
                            bool use_precise) const;

  bool g1_can_remove_pre_barrier(GraphKit* kit,
                                 PhaseValues* phase,
                                 Node* adr,
                                 BasicType bt,
                                 uint adr_idx) const;

  bool g1_can_remove_post_barrier(GraphKit* kit,
                                  PhaseValues* phase, Node* store,
                                  Node* adr) const;

  void g1_mark_card(GraphKit* kit,
                    IdealKit& ideal,
                    Node* card_adr,
                    Node* oop_store,
                    uint oop_alias_idx,
                    Node* index,
                    Node* index_adr,
                    Node* buffer,
                    const TypeFunc* tf) const;

  // Helper for unsafe accesses, that may or may not be on the referent field.
  // Generates the guards that check whether the result of
  // Unsafe.getReference should be recorded in an SATB log buffer.
  void insert_pre_barrier(GraphKit* kit, Node* base_oop, Node* offset, Node* pre_val, bool need_mem_bar) const;

  static const TypeFunc* write_ref_field_pre_entry_Type();
  static const TypeFunc* write_ref_field_post_entry_Type();

  virtual Node* load_at_resolved(C2Access& access, const Type* val_type) const;

#ifdef ASSERT
  bool has_cas_in_use_chain(Node* x) const;
  void verify_pre_load(Node* marking_check_if, Unique_Node_List& loads /*output*/) const;
  void verify_no_safepoints(Compile* compile, Node* marking_load, const Unique_Node_List& loads) const;
#endif

  static bool is_g1_pre_val_load(Node* n);
public:
  virtual void clone(GraphKit* kit, Node* src, Node* dst, Node* size, bool is_array) const;
  virtual bool is_gc_pre_barrier_node(Node* node) const;
  virtual bool is_gc_barrier_node(Node* node) const;
  virtual void eliminate_gc_barrier(PhaseMacroExpand* macro, Node* node) const;
  virtual Node* step_over_gc_barrier(Node* c) const;

#ifdef ASSERT
  virtual void verify_gc_barriers(Compile* compile, CompilePhase phase) const;
#endif

  virtual bool escape_add_to_con_graph(ConnectionGraph* conn_graph, PhaseGVN* gvn, Unique_Node_List* delayed_worklist, Node* n, uint opcode) const;
};
#endif // G1_LATE_BARRIER_MIGRATION_SUPPORT

#endif // SHARE_GC_G1_C2_G1BARRIERSETC2_HPP

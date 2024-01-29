/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_C2_ZBARRIERSETC2_HPP
#define SHARE_GC_Z_C2_ZBARRIERSETC2_HPP

#include "gc/shared/c2/barrierSetC2.hpp"
#include "memory/allocation.hpp"
#include "opto/node.hpp"
#include "utilities/growableArray.hpp"

const uint8_t ZBarrierStrong      =  1;
const uint8_t ZBarrierWeak        =  2;
const uint8_t ZBarrierPhantom     =  4;
const uint8_t ZBarrierNoKeepalive =  8;
const uint8_t ZBarrierNative      = 16;
const uint8_t ZBarrierElided      = 32;

class Block;
class MachNode;

class MacroAssembler;

class ZBarrierStubC2 : public ArenaObj {
protected:
  const MachNode* _node;
  Label           _entry;
  Label           _continuation;

static void register_stub(ZBarrierStubC2* stub);
static void inc_trampoline_stubs_count();
static int trampoline_stubs_count();
static int stubs_start_offset();

  ZBarrierStubC2(const MachNode* node);

public:
  RegMask& live() const;
  Label* entry();
  Label* continuation();

  virtual Register result() const = 0;
  virtual void emit_code(MacroAssembler& masm) = 0;
};

class ZLoadBarrierStubC2 : public ZBarrierStubC2 {
private:
  const Address  _ref_addr;
  const Register _ref;

protected:
  ZLoadBarrierStubC2(const MachNode* node, Address ref_addr, Register ref);

public:
  static ZLoadBarrierStubC2* create(const MachNode* node, Address ref_addr, Register ref);

  Address ref_addr() const;
  Register ref() const;
  address slow_path() const;

  virtual Register result() const;
  virtual void emit_code(MacroAssembler& masm);
};

class ZStoreBarrierStubC2 : public ZBarrierStubC2 {
private:
  const Address  _ref_addr;
  const Register _new_zaddress;
  const Register _new_zpointer;
  const bool     _is_native;
  const bool     _is_atomic;

protected:
  ZStoreBarrierStubC2(const MachNode* node, Address ref_addr, Register new_zaddress, Register new_zpointer, bool is_native, bool is_atomic);

public:
  static ZStoreBarrierStubC2* create(const MachNode* node, Address ref_addr, Register new_zaddress, Register new_zpointer, bool is_native, bool is_atomic);

  Address ref_addr() const;
  Register new_zaddress() const;
  Register new_zpointer() const;
  bool is_native() const;
  bool is_atomic() const;

  virtual Register result() const;
  virtual void emit_code(MacroAssembler& masm);
};

class ZBarrierSetC2 : public BarrierSetC2 {
private:
  void compute_liveness_at_stubs() const;
  void analyze_dominating_barriers_impl(Node_List& accesses, Node_List& access_dominators) const;
  void analyze_dominating_barriers() const;

protected:
  virtual Node* store_at_resolved(C2Access& access, C2AccessValue& val) const;
  virtual Node* load_at_resolved(C2Access& access, const Type* val_type) const;
  virtual Node* atomic_cmpxchg_val_at_resolved(C2AtomicParseAccess& access,
                                               Node* expected_val,
                                               Node* new_val,
                                               const Type* val_type) const;
  virtual Node* atomic_cmpxchg_bool_at_resolved(C2AtomicParseAccess& access,
                                                Node* expected_val,
                                                Node* new_val,
                                                const Type* value_type) const;
  virtual Node* atomic_xchg_at_resolved(C2AtomicParseAccess& access,
                                        Node* new_val,
                                        const Type* val_type) const;

public:
  virtual uint estimated_barrier_size(const Node* node) const;
  virtual void* create_barrier_state(Arena* comp_arena) const;
  virtual bool array_copy_requires_gc_barriers(bool tightly_coupled_alloc,
                                               BasicType type,
                                               bool is_clone,
                                               bool is_clone_instance,
                                               ArrayCopyPhase phase) const;
  virtual void clone_at_expansion(PhaseMacroExpand* phase,
                                  ArrayCopyNode* ac) const;

  virtual void late_barrier_analysis() const;
  virtual int estimate_stub_size() const;
  virtual void emit_stubs(CodeBuffer& cb) const;
  virtual void eliminate_gc_barrier(PhaseMacroExpand* macro, Node* node) const;
  virtual void eliminate_gc_barrier_data(Node* node) const;

#ifndef PRODUCT
  virtual void dump_barrier_data(const MachNode* mach, outputStream* st) const;
#endif
};

#endif // SHARE_GC_Z_C2_ZBARRIERSETC2_HPP

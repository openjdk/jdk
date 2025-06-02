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

#include "code/vmreg.inline.hpp"
#include "gc/shared/barrierSet.hpp"
#include "gc/shared/c2/barrierSetC2.hpp"
#include "gc/shared/tlab_globals.hpp"
#include "opto/arraycopynode.hpp"
#include "opto/block.hpp"
#include "opto/convertnode.hpp"
#include "opto/graphKit.hpp"
#include "opto/idealKit.hpp"
#include "opto/macro.hpp"
#include "opto/narrowptrnode.hpp"
#include "opto/output.hpp"
#include "opto/regalloc.hpp"
#include "opto/runtime.hpp"
#include "utilities/macros.hpp"
#include CPU_HEADER(gc/shared/barrierSetAssembler)

// By default this is a no-op.
void BarrierSetC2::resolve_address(C2Access& access) const { }

void* C2ParseAccess::barrier_set_state() const {
  return _kit->barrier_set_state();
}

PhaseGVN& C2ParseAccess::gvn() const { return _kit->gvn(); }

bool C2Access::needs_cpu_membar() const {
  bool mismatched   = (_decorators & C2_MISMATCHED) != 0;
  bool is_unordered = (_decorators & MO_UNORDERED) != 0;

  bool anonymous = (_decorators & C2_UNSAFE_ACCESS) != 0;
  bool in_heap   = (_decorators & IN_HEAP) != 0;
  bool in_native = (_decorators & IN_NATIVE) != 0;
  bool is_mixed  = !in_heap && !in_native;

  bool is_write  = (_decorators & C2_WRITE_ACCESS) != 0;
  bool is_read   = (_decorators & C2_READ_ACCESS) != 0;
  bool is_atomic = is_read && is_write;

  if (is_atomic) {
    // Atomics always need to be wrapped in CPU membars
    return true;
  }

  if (anonymous) {
    // We will need memory barriers unless we can determine a unique
    // alias category for this reference.  (Note:  If for some reason
    // the barriers get omitted and the unsafe reference begins to "pollute"
    // the alias analysis of the rest of the graph, either Compile::can_alias
    // or Compile::must_alias will throw a diagnostic assert.)
    if (is_mixed || !is_unordered || (mismatched && !_addr.type()->isa_aryptr())) {
      return true;
    }
  } else {
    assert(!is_mixed, "not unsafe");
  }

  return false;
}

static BarrierSetC2State* barrier_set_state() {
  return reinterpret_cast<BarrierSetC2State*>(Compile::current()->barrier_set_state());
}

RegMask& BarrierStubC2::live() const {
  return *barrier_set_state()->live(_node);
}

BarrierStubC2::BarrierStubC2(const MachNode* node)
  : _node(node),
    _entry(),
    _continuation(),
    _preserve(live()) {}

Label* BarrierStubC2::entry() {
  // The _entry will never be bound when in_scratch_emit_size() is true.
  // However, we still need to return a label that is not bound now, but
  // will eventually be bound. Any eventually bound label will do, as it
  // will only act as a placeholder, so we return the _continuation label.
  return Compile::current()->output()->in_scratch_emit_size() ? &_continuation : &_entry;
}

Label* BarrierStubC2::continuation() {
  return &_continuation;
}

uint8_t BarrierStubC2::barrier_data() const {
  return _node->barrier_data();
}

void BarrierStubC2::preserve(Register r) {
  const VMReg vm_reg = r->as_VMReg();
  assert(vm_reg->is_Register(), "r must be a general-purpose register");
  _preserve.Insert(OptoReg::as_OptoReg(vm_reg));
}

void BarrierStubC2::dont_preserve(Register r) {
  VMReg vm_reg = r->as_VMReg();
  assert(vm_reg->is_Register(), "r must be a general-purpose register");
  // Subtract the given register and all its sub-registers (e.g. {R11, R11_H}
  // for r11 in aarch64).
  do {
    _preserve.Remove(OptoReg::as_OptoReg(vm_reg));
    vm_reg = vm_reg->next();
  } while (vm_reg->is_Register() && !vm_reg->is_concrete());
}

const RegMask& BarrierStubC2::preserve_set() const {
  return _preserve;
}

Node* BarrierSetC2::store_at_resolved(C2Access& access, C2AccessValue& val) const {
  DecoratorSet decorators = access.decorators();

  bool mismatched = (decorators & C2_MISMATCHED) != 0;
  bool unaligned = (decorators & C2_UNALIGNED) != 0;
  bool unsafe = (decorators & C2_UNSAFE_ACCESS) != 0;
  bool requires_atomic_access = (decorators & MO_UNORDERED) == 0;

  MemNode::MemOrd mo = access.mem_node_mo();

  Node* store;
  BasicType bt = access.type();
  if (access.is_parse_access()) {
    C2ParseAccess& parse_access = static_cast<C2ParseAccess&>(access);

    GraphKit* kit = parse_access.kit();
    store = kit->store_to_memory(kit->control(), access.addr().node(), val.node(), bt,
                                 mo, requires_atomic_access, unaligned, mismatched,
                                 unsafe, access.barrier_data());
  } else {
    assert(access.is_opt_access(), "either parse or opt access");
    C2OptAccess& opt_access = static_cast<C2OptAccess&>(access);
    Node* ctl = opt_access.ctl();
    MergeMemNode* mm = opt_access.mem();
    PhaseGVN& gvn = opt_access.gvn();
    const TypePtr* adr_type = access.addr().type();
    int alias = gvn.C->get_alias_index(adr_type);
    Node* mem = mm->memory_at(alias);

    StoreNode* st = StoreNode::make(gvn, ctl, mem, access.addr().node(), adr_type, val.node(), bt, mo, requires_atomic_access);
    if (unaligned) {
      st->set_unaligned_access();
    }
    if (mismatched) {
      st->set_mismatched_access();
    }
    st->set_barrier_data(access.barrier_data());
    store = gvn.transform(st);
    if (store == st) {
      mm->set_memory_at(alias, st);
    }
  }
  access.set_raw_access(store);

  return store;
}

Node* BarrierSetC2::load_at_resolved(C2Access& access, const Type* val_type) const {
  DecoratorSet decorators = access.decorators();

  Node* adr = access.addr().node();
  const TypePtr* adr_type = access.addr().type();

  bool mismatched = (decorators & C2_MISMATCHED) != 0;
  bool requires_atomic_access = (decorators & MO_UNORDERED) == 0;
  bool unaligned = (decorators & C2_UNALIGNED) != 0;
  bool control_dependent = (decorators & C2_CONTROL_DEPENDENT_LOAD) != 0;
  bool unknown_control = (decorators & C2_UNKNOWN_CONTROL_LOAD) != 0;
  bool unsafe = (decorators & C2_UNSAFE_ACCESS) != 0;
  bool immutable = (decorators & C2_IMMUTABLE_MEMORY) != 0;

  MemNode::MemOrd mo = access.mem_node_mo();
  LoadNode::ControlDependency dep = unknown_control ? LoadNode::UnknownControl : LoadNode::DependsOnlyOnTest;

  Node* load;
  if (access.is_parse_access()) {
    C2ParseAccess& parse_access = static_cast<C2ParseAccess&>(access);
    GraphKit* kit = parse_access.kit();
    Node* control = control_dependent ? kit->control() : nullptr;

    if (immutable) {
      Compile* C = Compile::current();
      Node* mem = kit->immutable_memory();
      load = LoadNode::make(kit->gvn(), control, mem, adr,
                            adr_type, val_type, access.type(), mo, dep, requires_atomic_access,
                            unaligned, mismatched, unsafe, access.barrier_data());
      load = kit->gvn().transform(load);
    } else {
      load = kit->make_load(control, adr, val_type, access.type(), mo,
                            dep, requires_atomic_access, unaligned, mismatched, unsafe,
                            access.barrier_data());
    }
  } else {
    assert(access.is_opt_access(), "either parse or opt access");
    C2OptAccess& opt_access = static_cast<C2OptAccess&>(access);
    Node* control = control_dependent ? opt_access.ctl() : nullptr;
    MergeMemNode* mm = opt_access.mem();
    PhaseGVN& gvn = opt_access.gvn();
    Node* mem = mm->memory_at(gvn.C->get_alias_index(adr_type));
    load = LoadNode::make(gvn, control, mem, adr, adr_type, val_type, access.type(), mo, dep,
                          requires_atomic_access, unaligned, mismatched, unsafe, access.barrier_data());
    load = gvn.transform(load);
  }
  access.set_raw_access(load);

  return load;
}

class C2AccessFence: public StackObj {
  C2Access& _access;
  Node* _leading_membar;

public:
  C2AccessFence(C2Access& access) :
    _access(access), _leading_membar(nullptr) {
    GraphKit* kit = nullptr;
    if (access.is_parse_access()) {
      C2ParseAccess& parse_access = static_cast<C2ParseAccess&>(access);
      kit = parse_access.kit();
    }
    DecoratorSet decorators = access.decorators();

    bool is_write = (decorators & C2_WRITE_ACCESS) != 0;
    bool is_read = (decorators & C2_READ_ACCESS) != 0;
    bool is_atomic = is_read && is_write;

    bool is_volatile = (decorators & MO_SEQ_CST) != 0;
    bool is_release = (decorators & MO_RELEASE) != 0;

    if (is_atomic) {
      assert(kit != nullptr, "unsupported at optimization time");
      // Memory-model-wise, a LoadStore acts like a little synchronized
      // block, so needs barriers on each side.  These don't translate
      // into actual barriers on most machines, but we still need rest of
      // compiler to respect ordering.
      if (is_release) {
        _leading_membar = kit->insert_mem_bar(Op_MemBarRelease);
      } else if (is_volatile) {
        if (support_IRIW_for_not_multiple_copy_atomic_cpu) {
          _leading_membar = kit->insert_mem_bar(Op_MemBarVolatile);
        } else {
          _leading_membar = kit->insert_mem_bar(Op_MemBarRelease);
        }
      }
    } else if (is_write) {
      // If reference is volatile, prevent following memory ops from
      // floating down past the volatile write.  Also prevents commoning
      // another volatile read.
      if (is_volatile || is_release) {
        assert(kit != nullptr, "unsupported at optimization time");
        _leading_membar = kit->insert_mem_bar(Op_MemBarRelease);
      }
    } else {
      // Memory barrier to prevent normal and 'unsafe' accesses from
      // bypassing each other.  Happens after null checks, so the
      // exception paths do not take memory state from the memory barrier,
      // so there's no problems making a strong assert about mixing users
      // of safe & unsafe memory.
      if (is_volatile && support_IRIW_for_not_multiple_copy_atomic_cpu) {
        assert(kit != nullptr, "unsupported at optimization time");
        _leading_membar = kit->insert_mem_bar(Op_MemBarVolatile);
      }
    }

    if (access.needs_cpu_membar()) {
      assert(kit != nullptr, "unsupported at optimization time");
      kit->insert_mem_bar(Op_MemBarCPUOrder);
    }

    if (is_atomic) {
      // 4984716: MemBars must be inserted before this
      //          memory node in order to avoid a false
      //          dependency which will confuse the scheduler.
      access.set_memory();
    }
  }

  ~C2AccessFence() {
    GraphKit* kit = nullptr;
    if (_access.is_parse_access()) {
      C2ParseAccess& parse_access = static_cast<C2ParseAccess&>(_access);
      kit = parse_access.kit();
    }
    DecoratorSet decorators = _access.decorators();

    bool is_write = (decorators & C2_WRITE_ACCESS) != 0;
    bool is_read = (decorators & C2_READ_ACCESS) != 0;
    bool is_atomic = is_read && is_write;

    bool is_volatile = (decorators & MO_SEQ_CST) != 0;
    bool is_acquire = (decorators & MO_ACQUIRE) != 0;

    // If reference is volatile, prevent following volatiles ops from
    // floating up before the volatile access.
    if (_access.needs_cpu_membar()) {
      kit->insert_mem_bar(Op_MemBarCPUOrder);
    }

    if (is_atomic) {
      assert(kit != nullptr, "unsupported at optimization time");
      if (is_acquire || is_volatile) {
        Node* n = _access.raw_access();
        Node* mb = kit->insert_mem_bar(Op_MemBarAcquire, n);
        if (_leading_membar != nullptr) {
          MemBarNode::set_load_store_pair(_leading_membar->as_MemBar(), mb->as_MemBar());
        }
      }
    } else if (is_write) {
      // If not multiple copy atomic, we do the MemBarVolatile before the load.
      if (is_volatile && !support_IRIW_for_not_multiple_copy_atomic_cpu) {
        assert(kit != nullptr, "unsupported at optimization time");
        Node* n = _access.raw_access();
        Node* mb = kit->insert_mem_bar(Op_MemBarVolatile, n); // Use fat membar
        if (_leading_membar != nullptr) {
          MemBarNode::set_store_pair(_leading_membar->as_MemBar(), mb->as_MemBar());
        }
      }
    } else {
      if (is_volatile || is_acquire) {
        assert(kit != nullptr, "unsupported at optimization time");
        Node* n = _access.raw_access();
        assert(_leading_membar == nullptr || support_IRIW_for_not_multiple_copy_atomic_cpu, "no leading membar expected");
        Node* mb = kit->insert_mem_bar(Op_MemBarAcquire, n);
        mb->as_MemBar()->set_trailing_load();
      }
    }
  }
};

Node* BarrierSetC2::store_at(C2Access& access, C2AccessValue& val) const {
  C2AccessFence fence(access);
  resolve_address(access);
  return store_at_resolved(access, val);
}

Node* BarrierSetC2::load_at(C2Access& access, const Type* val_type) const {
  C2AccessFence fence(access);
  resolve_address(access);
  return load_at_resolved(access, val_type);
}

MemNode::MemOrd C2Access::mem_node_mo() const {
  bool is_write = (_decorators & C2_WRITE_ACCESS) != 0;
  bool is_read = (_decorators & C2_READ_ACCESS) != 0;
  if ((_decorators & MO_SEQ_CST) != 0) {
    if (is_write && is_read) {
      // For atomic operations
      return MemNode::seqcst;
    } else if (is_write) {
      return MemNode::release;
    } else {
      assert(is_read, "what else?");
      return MemNode::acquire;
    }
  } else if ((_decorators & MO_RELEASE) != 0) {
    return MemNode::release;
  } else if ((_decorators & MO_ACQUIRE) != 0) {
    return MemNode::acquire;
  } else if (is_write) {
    // Volatile fields need releasing stores.
    // Non-volatile fields also need releasing stores if they hold an
    // object reference, because the object reference might point to
    // a freshly created object.
    // Conservatively release stores of object references.
    return StoreNode::release_if_reference(_type);
  } else {
    return MemNode::unordered;
  }
}

void C2Access::fixup_decorators() {
  bool default_mo = (_decorators & MO_DECORATOR_MASK) == 0;
  bool is_unordered = (_decorators & MO_UNORDERED) != 0 || default_mo;
  bool anonymous = (_decorators & C2_UNSAFE_ACCESS) != 0;

  bool is_read = (_decorators & C2_READ_ACCESS) != 0;
  bool is_write = (_decorators & C2_WRITE_ACCESS) != 0;

  if (AlwaysAtomicAccesses && is_unordered) {
    _decorators &= ~MO_DECORATOR_MASK; // clear the MO bits
    _decorators |= MO_RELAXED; // Force the MO_RELAXED decorator with AlwaysAtomicAccess
  }

  _decorators = AccessInternal::decorator_fixup(_decorators, _type);

  if (is_read && !is_write && anonymous) {
    // To be valid, unsafe loads may depend on other conditions than
    // the one that guards them: pin the Load node
    _decorators |= C2_CONTROL_DEPENDENT_LOAD;
    _decorators |= C2_UNKNOWN_CONTROL_LOAD;
    const TypePtr* adr_type = _addr.type();
    Node* adr = _addr.node();
    if (!needs_cpu_membar() && adr_type->isa_instptr()) {
      assert(adr_type->meet(TypePtr::NULL_PTR) != adr_type->remove_speculative(), "should be not null");
      intptr_t offset = Type::OffsetBot;
      AddPNode::Ideal_base_and_offset(adr, &gvn(), offset);
      if (offset >= 0) {
        int s = Klass::layout_helper_size_in_bytes(adr_type->isa_instptr()->instance_klass()->layout_helper());
        if (offset < s) {
          // Guaranteed to be a valid access, no need to pin it
          _decorators ^= C2_CONTROL_DEPENDENT_LOAD;
          _decorators ^= C2_UNKNOWN_CONTROL_LOAD;
        }
      }
    }
  }
}

//--------------------------- atomic operations---------------------------------

void BarrierSetC2::pin_atomic_op(C2AtomicParseAccess& access) const {
  // SCMemProjNodes represent the memory state of a LoadStore. Their
  // main role is to prevent LoadStore nodes from being optimized away
  // when their results aren't used.
  assert(access.is_parse_access(), "entry not supported at optimization time");
  C2ParseAccess& parse_access = static_cast<C2ParseAccess&>(access);
  GraphKit* kit = parse_access.kit();
  Node* load_store = access.raw_access();
  assert(load_store != nullptr, "must pin atomic op");
  Node* proj = kit->gvn().transform(new SCMemProjNode(load_store));
  kit->set_memory(proj, access.alias_idx());
}

void C2AtomicParseAccess::set_memory() {
  Node *mem = _kit->memory(_alias_idx);
  _memory = mem;
}

Node* BarrierSetC2::atomic_cmpxchg_val_at_resolved(C2AtomicParseAccess& access, Node* expected_val,
                                                   Node* new_val, const Type* value_type) const {
  GraphKit* kit = access.kit();
  MemNode::MemOrd mo = access.mem_node_mo();
  Node* mem = access.memory();

  Node* adr = access.addr().node();
  const TypePtr* adr_type = access.addr().type();

  Node* load_store = nullptr;

  if (access.is_oop()) {
#ifdef _LP64
    if (adr->bottom_type()->is_ptr_to_narrowoop()) {
      Node *newval_enc = kit->gvn().transform(new EncodePNode(new_val, new_val->bottom_type()->make_narrowoop()));
      Node *oldval_enc = kit->gvn().transform(new EncodePNode(expected_val, expected_val->bottom_type()->make_narrowoop()));
      load_store = new CompareAndExchangeNNode(kit->control(), mem, adr, newval_enc, oldval_enc, adr_type, value_type->make_narrowoop(), mo);
    } else
#endif
    {
      load_store = new CompareAndExchangePNode(kit->control(), mem, adr, new_val, expected_val, adr_type, value_type->is_oopptr(), mo);
    }
  } else {
    switch (access.type()) {
      case T_BYTE: {
        load_store = new CompareAndExchangeBNode(kit->control(), mem, adr, new_val, expected_val, adr_type, mo);
        break;
      }
      case T_SHORT: {
        load_store = new CompareAndExchangeSNode(kit->control(), mem, adr, new_val, expected_val, adr_type, mo);
        break;
      }
      case T_INT: {
        load_store = new CompareAndExchangeINode(kit->control(), mem, adr, new_val, expected_val, adr_type, mo);
        break;
      }
      case T_LONG: {
        load_store = new CompareAndExchangeLNode(kit->control(), mem, adr, new_val, expected_val, adr_type, mo);
        break;
      }
      default:
        ShouldNotReachHere();
    }
  }

  load_store->as_LoadStore()->set_barrier_data(access.barrier_data());
  load_store = kit->gvn().transform(load_store);

  access.set_raw_access(load_store);
  pin_atomic_op(access);

#ifdef _LP64
  if (access.is_oop() && adr->bottom_type()->is_ptr_to_narrowoop()) {
    return kit->gvn().transform(new DecodeNNode(load_store, load_store->get_ptr_type()));
  }
#endif

  return load_store;
}

Node* BarrierSetC2::atomic_cmpxchg_bool_at_resolved(C2AtomicParseAccess& access, Node* expected_val,
                                                    Node* new_val, const Type* value_type) const {
  GraphKit* kit = access.kit();
  DecoratorSet decorators = access.decorators();
  MemNode::MemOrd mo = access.mem_node_mo();
  Node* mem = access.memory();
  bool is_weak_cas = (decorators & C2_WEAK_CMPXCHG) != 0;
  Node* load_store = nullptr;
  Node* adr = access.addr().node();

  if (access.is_oop()) {
#ifdef _LP64
    if (adr->bottom_type()->is_ptr_to_narrowoop()) {
      Node *newval_enc = kit->gvn().transform(new EncodePNode(new_val, new_val->bottom_type()->make_narrowoop()));
      Node *oldval_enc = kit->gvn().transform(new EncodePNode(expected_val, expected_val->bottom_type()->make_narrowoop()));
      if (is_weak_cas) {
        load_store = new WeakCompareAndSwapNNode(kit->control(), mem, adr, newval_enc, oldval_enc, mo);
      } else {
        load_store = new CompareAndSwapNNode(kit->control(), mem, adr, newval_enc, oldval_enc, mo);
      }
    } else
#endif
    {
      if (is_weak_cas) {
        load_store = new WeakCompareAndSwapPNode(kit->control(), mem, adr, new_val, expected_val, mo);
      } else {
        load_store = new CompareAndSwapPNode(kit->control(), mem, adr, new_val, expected_val, mo);
      }
    }
  } else {
    switch(access.type()) {
      case T_BYTE: {
        if (is_weak_cas) {
          load_store = new WeakCompareAndSwapBNode(kit->control(), mem, adr, new_val, expected_val, mo);
        } else {
          load_store = new CompareAndSwapBNode(kit->control(), mem, adr, new_val, expected_val, mo);
        }
        break;
      }
      case T_SHORT: {
        if (is_weak_cas) {
          load_store = new WeakCompareAndSwapSNode(kit->control(), mem, adr, new_val, expected_val, mo);
        } else {
          load_store = new CompareAndSwapSNode(kit->control(), mem, adr, new_val, expected_val, mo);
        }
        break;
      }
      case T_INT: {
        if (is_weak_cas) {
          load_store = new WeakCompareAndSwapINode(kit->control(), mem, adr, new_val, expected_val, mo);
        } else {
          load_store = new CompareAndSwapINode(kit->control(), mem, adr, new_val, expected_val, mo);
        }
        break;
      }
      case T_LONG: {
        if (is_weak_cas) {
          load_store = new WeakCompareAndSwapLNode(kit->control(), mem, adr, new_val, expected_val, mo);
        } else {
          load_store = new CompareAndSwapLNode(kit->control(), mem, adr, new_val, expected_val, mo);
        }
        break;
      }
      default:
        ShouldNotReachHere();
    }
  }

  load_store->as_LoadStore()->set_barrier_data(access.barrier_data());
  load_store = kit->gvn().transform(load_store);

  access.set_raw_access(load_store);
  pin_atomic_op(access);

  return load_store;
}

Node* BarrierSetC2::atomic_xchg_at_resolved(C2AtomicParseAccess& access, Node* new_val, const Type* value_type) const {
  GraphKit* kit = access.kit();
  Node* mem = access.memory();
  Node* adr = access.addr().node();
  const TypePtr* adr_type = access.addr().type();
  Node* load_store = nullptr;

  if (access.is_oop()) {
#ifdef _LP64
    if (adr->bottom_type()->is_ptr_to_narrowoop()) {
      Node *newval_enc = kit->gvn().transform(new EncodePNode(new_val, new_val->bottom_type()->make_narrowoop()));
      load_store = kit->gvn().transform(new GetAndSetNNode(kit->control(), mem, adr, newval_enc, adr_type, value_type->make_narrowoop()));
    } else
#endif
    {
      load_store = new GetAndSetPNode(kit->control(), mem, adr, new_val, adr_type, value_type->is_oopptr());
    }
  } else  {
    switch (access.type()) {
      case T_BYTE:
        load_store = new GetAndSetBNode(kit->control(), mem, adr, new_val, adr_type);
        break;
      case T_SHORT:
        load_store = new GetAndSetSNode(kit->control(), mem, adr, new_val, adr_type);
        break;
      case T_INT:
        load_store = new GetAndSetINode(kit->control(), mem, adr, new_val, adr_type);
        break;
      case T_LONG:
        load_store = new GetAndSetLNode(kit->control(), mem, adr, new_val, adr_type);
        break;
      default:
        ShouldNotReachHere();
    }
  }

  load_store->as_LoadStore()->set_barrier_data(access.barrier_data());
  load_store = kit->gvn().transform(load_store);

  access.set_raw_access(load_store);
  pin_atomic_op(access);

#ifdef _LP64
  if (access.is_oop() && adr->bottom_type()->is_ptr_to_narrowoop()) {
    return kit->gvn().transform(new DecodeNNode(load_store, load_store->get_ptr_type()));
  }
#endif

  return load_store;
}

Node* BarrierSetC2::atomic_add_at_resolved(C2AtomicParseAccess& access, Node* new_val, const Type* value_type) const {
  Node* load_store = nullptr;
  GraphKit* kit = access.kit();
  Node* adr = access.addr().node();
  const TypePtr* adr_type = access.addr().type();
  Node* mem = access.memory();

  switch(access.type()) {
    case T_BYTE:
      load_store = new GetAndAddBNode(kit->control(), mem, adr, new_val, adr_type);
      break;
    case T_SHORT:
      load_store = new GetAndAddSNode(kit->control(), mem, adr, new_val, adr_type);
      break;
    case T_INT:
      load_store = new GetAndAddINode(kit->control(), mem, adr, new_val, adr_type);
      break;
    case T_LONG:
      load_store = new GetAndAddLNode(kit->control(), mem, adr, new_val, adr_type);
      break;
    default:
      ShouldNotReachHere();
  }

  load_store->as_LoadStore()->set_barrier_data(access.barrier_data());
  load_store = kit->gvn().transform(load_store);

  access.set_raw_access(load_store);
  pin_atomic_op(access);

  return load_store;
}

Node* BarrierSetC2::atomic_cmpxchg_val_at(C2AtomicParseAccess& access, Node* expected_val,
                                          Node* new_val, const Type* value_type) const {
  C2AccessFence fence(access);
  resolve_address(access);
  return atomic_cmpxchg_val_at_resolved(access, expected_val, new_val, value_type);
}

Node* BarrierSetC2::atomic_cmpxchg_bool_at(C2AtomicParseAccess& access, Node* expected_val,
                                           Node* new_val, const Type* value_type) const {
  C2AccessFence fence(access);
  resolve_address(access);
  return atomic_cmpxchg_bool_at_resolved(access, expected_val, new_val, value_type);
}

Node* BarrierSetC2::atomic_xchg_at(C2AtomicParseAccess& access, Node* new_val, const Type* value_type) const {
  C2AccessFence fence(access);
  resolve_address(access);
  return atomic_xchg_at_resolved(access, new_val, value_type);
}

Node* BarrierSetC2::atomic_add_at(C2AtomicParseAccess& access, Node* new_val, const Type* value_type) const {
  C2AccessFence fence(access);
  resolve_address(access);
  return atomic_add_at_resolved(access, new_val, value_type);
}

int BarrierSetC2::arraycopy_payload_base_offset(bool is_array) {
  // Exclude the header but include array length to copy by 8 bytes words.
  // Can't use base_offset_in_bytes(bt) since basic type is unknown.
  int base_off = is_array ? arrayOopDesc::length_offset_in_bytes() :
                            instanceOopDesc::base_offset_in_bytes();
  // base_off:
  // 8  - 32-bit VM or 64-bit VM, compact headers
  // 12 - 64-bit VM, compressed klass
  // 16 - 64-bit VM, normal klass
  if (base_off % BytesPerLong != 0) {
    assert(UseCompressedClassPointers, "");
    assert(!UseCompactObjectHeaders, "");
    if (is_array) {
      // Exclude length to copy by 8 bytes words.
      base_off += sizeof(int);
    } else {
      // Include klass to copy by 8 bytes words.
      base_off = instanceOopDesc::klass_offset_in_bytes();
    }
    assert(base_off % BytesPerLong == 0, "expect 8 bytes alignment");
  }
  return base_off;
}

void BarrierSetC2::clone(GraphKit* kit, Node* src_base, Node* dst_base, Node* size, bool is_array) const {
  int base_off = arraycopy_payload_base_offset(is_array);
  Node* payload_size = size;
  Node* offset = kit->MakeConX(base_off);
  payload_size = kit->gvn().transform(new SubXNode(payload_size, offset));
  if (is_array) {
    // Ensure the array payload size is rounded up to the next BytesPerLong
    // multiple when converting to double-words. This is necessary because array
    // size does not include object alignment padding, so it might not be a
    // multiple of BytesPerLong for sub-long element types.
    payload_size = kit->gvn().transform(new AddXNode(payload_size, kit->MakeConX(BytesPerLong - 1)));
  }
  payload_size = kit->gvn().transform(new URShiftXNode(payload_size, kit->intcon(LogBytesPerLong)));
  ArrayCopyNode* ac = ArrayCopyNode::make(kit, false, src_base, offset, dst_base, offset, payload_size, true, false);
  if (is_array) {
    ac->set_clone_array();
  } else {
    ac->set_clone_inst();
  }
  Node* n = kit->gvn().transform(ac);
  if (n == ac) {
    const TypePtr* raw_adr_type = TypeRawPtr::BOTTOM;
    ac->set_adr_type(TypeRawPtr::BOTTOM);
    kit->set_predefined_output_for_runtime_call(ac, ac->in(TypeFunc::Memory), raw_adr_type);
  } else {
    kit->set_all_memory(n);
  }
}

Node* BarrierSetC2::obj_allocate(PhaseMacroExpand* macro, Node* mem, Node* toobig_false, Node* size_in_bytes,
                                 Node*& i_o, Node*& needgc_ctrl,
                                 Node*& fast_oop_ctrl, Node*& fast_oop_rawmem,
                                 intx prefetch_lines) const {
  assert(UseTLAB, "Only for TLAB enabled allocations");

  Node* thread = macro->transform_later(new ThreadLocalNode());
  Node* tlab_top_adr = macro->basic_plus_adr(macro->top()/*not oop*/, thread, in_bytes(JavaThread::tlab_top_offset()));
  Node* tlab_end_adr = macro->basic_plus_adr(macro->top()/*not oop*/, thread, in_bytes(JavaThread::tlab_end_offset()));

  // Load TLAB end.
  //
  // Note: We set the control input on "tlab_end" and "old_tlab_top" to work around
  //       a bug where these values were being moved across
  //       a safepoint.  These are not oops, so they cannot be include in the oop
  //       map, but they can be changed by a GC.   The proper way to fix this would
  //       be to set the raw memory state when generating a  SafepointNode.  However
  //       this will require extensive changes to the loop optimization in order to
  //       prevent a degradation of the optimization.
  //       See comment in memnode.hpp, around line 227 in class LoadPNode.
  Node* tlab_end = macro->make_load(toobig_false, mem, tlab_end_adr, 0, TypeRawPtr::BOTTOM, T_ADDRESS);

  // Load the TLAB top.
  Node* old_tlab_top = new LoadPNode(toobig_false, mem, tlab_top_adr, TypeRawPtr::BOTTOM, TypeRawPtr::BOTTOM, MemNode::unordered);
  macro->transform_later(old_tlab_top);

  // Add to heap top to get a new TLAB top
  Node* new_tlab_top = new AddPNode(macro->top(), old_tlab_top, size_in_bytes);
  macro->transform_later(new_tlab_top);

  // Check against TLAB end
  Node* tlab_full = new CmpPNode(new_tlab_top, tlab_end);
  macro->transform_later(tlab_full);

  Node* needgc_bol = new BoolNode(tlab_full, BoolTest::ge);
  macro->transform_later(needgc_bol);
  IfNode* needgc_iff = new IfNode(toobig_false, needgc_bol, PROB_UNLIKELY_MAG(4), COUNT_UNKNOWN);
  macro->transform_later(needgc_iff);

  // Plug the failing-heap-space-need-gc test into the slow-path region
  Node* needgc_true = new IfTrueNode(needgc_iff);
  macro->transform_later(needgc_true);
  needgc_ctrl = needgc_true;

  // No need for a GC.
  Node* needgc_false = new IfFalseNode(needgc_iff);
  macro->transform_later(needgc_false);

  // Fast path:
  i_o = macro->prefetch_allocation(i_o, needgc_false, mem,
                                   old_tlab_top, new_tlab_top, prefetch_lines);

  // Store the modified TLAB top back down.
  Node* store_tlab_top = new StorePNode(needgc_false, mem, tlab_top_adr,
                   TypeRawPtr::BOTTOM, new_tlab_top, MemNode::unordered);
  macro->transform_later(store_tlab_top);

  fast_oop_ctrl = needgc_false;
  fast_oop_rawmem = store_tlab_top;
  return old_tlab_top;
}

static const TypeFunc* clone_type() {
  // Create input type (domain)
  int argcnt = NOT_LP64(3) LP64_ONLY(4);
  const Type** const domain_fields = TypeTuple::fields(argcnt);
  int argp = TypeFunc::Parms;
  domain_fields[argp++] = TypeInstPtr::NOTNULL;  // src
  domain_fields[argp++] = TypeInstPtr::NOTNULL;  // dst
  domain_fields[argp++] = TypeX_X;               // size lower
  LP64_ONLY(domain_fields[argp++] = Type::HALF); // size upper
  assert(argp == TypeFunc::Parms+argcnt, "correct decoding");
  const TypeTuple* const domain = TypeTuple::make(TypeFunc::Parms + argcnt, domain_fields);

  // Create result type (range)
  const Type** const range_fields = TypeTuple::fields(0);
  const TypeTuple* const range = TypeTuple::make(TypeFunc::Parms + 0, range_fields);

  return TypeFunc::make(domain, range);
}

#define XTOP LP64_ONLY(COMMA phase->top())

void BarrierSetC2::clone_in_runtime(PhaseMacroExpand* phase, ArrayCopyNode* ac,
                                    address clone_addr, const char* clone_name) const {
  Node* const ctrl = ac->in(TypeFunc::Control);
  Node* const mem  = ac->in(TypeFunc::Memory);
  Node* const src  = ac->in(ArrayCopyNode::Src);
  Node* const dst  = ac->in(ArrayCopyNode::Dest);
  Node* const size = ac->in(ArrayCopyNode::Length);

  assert(size->bottom_type()->base() == Type_X,
         "Should be of object size type (int for 32 bits, long for 64 bits)");

  // The native clone we are calling here expects the object size in words.
  // Add header/offset size to payload size to get object size.
  Node* const base_offset = phase->MakeConX(arraycopy_payload_base_offset(ac->is_clone_array()) >> LogBytesPerLong);
  Node* const full_size = phase->transform_later(new AddXNode(size, base_offset));
  // HeapAccess<>::clone expects size in heap words.
  // For 64-bits platforms, this is a no-operation.
  // For 32-bits platforms, we need to multiply full_size by HeapWordsPerLong (2).
  Node* const full_size_in_heap_words = phase->transform_later(new LShiftXNode(full_size, phase->intcon(LogHeapWordsPerLong)));

  Node* const call = phase->make_leaf_call(ctrl,
                                           mem,
                                           clone_type(),
                                           clone_addr,
                                           clone_name,
                                           TypeRawPtr::BOTTOM,
                                           src, dst, full_size_in_heap_words XTOP);
  phase->transform_later(call);
  phase->igvn().replace_node(ac, call);
}

void BarrierSetC2::clone_at_expansion(PhaseMacroExpand* phase, ArrayCopyNode* ac) const {
  Node* ctrl = ac->in(TypeFunc::Control);
  Node* mem = ac->in(TypeFunc::Memory);
  Node* src = ac->in(ArrayCopyNode::Src);
  Node* src_offset = ac->in(ArrayCopyNode::SrcPos);
  Node* dest = ac->in(ArrayCopyNode::Dest);
  Node* dest_offset = ac->in(ArrayCopyNode::DestPos);
  Node* length = ac->in(ArrayCopyNode::Length);

  Node* payload_src = phase->basic_plus_adr(src, src_offset);
  Node* payload_dst = phase->basic_plus_adr(dest, dest_offset);

  const char* copyfunc_name = "arraycopy";
  address     copyfunc_addr = phase->basictype2arraycopy(T_LONG, nullptr, nullptr, true, copyfunc_name, true);

  const TypePtr* raw_adr_type = TypeRawPtr::BOTTOM;
  const TypeFunc* call_type = OptoRuntime::fast_arraycopy_Type();

  Node* call = phase->make_leaf_call(ctrl, mem, call_type, copyfunc_addr, copyfunc_name, raw_adr_type, payload_src, payload_dst, length XTOP);
  phase->transform_later(call);

  phase->igvn().replace_node(ac, call);
}

#undef XTOP

static bool block_has_safepoint(const Block* block, uint from, uint to) {
  for (uint i = from; i < to; i++) {
    if (block->get_node(i)->is_MachSafePoint()) {
      // Safepoint found
      return true;
    }
  }

  // Safepoint not found
  return false;
}

static bool block_has_safepoint(const Block* block) {
  return block_has_safepoint(block, 0, block->number_of_nodes());
}

static uint block_index(const Block* block, const Node* node) {
  for (uint j = 0; j < block->number_of_nodes(); ++j) {
    if (block->get_node(j) == node) {
      return j;
    }
  }
  ShouldNotReachHere();
  return 0;
}

// Look through various node aliases
static const Node* look_through_node(const Node* node) {
  while (node != nullptr) {
    const Node* new_node = node;
    if (node->is_Mach()) {
      const MachNode* const node_mach = node->as_Mach();
      if (node_mach->ideal_Opcode() == Op_CheckCastPP) {
        new_node = node->in(1);
      }
      if (node_mach->is_SpillCopy()) {
        new_node = node->in(1);
      }
    }
    if (new_node == node || new_node == nullptr) {
      break;
    } else {
      node = new_node;
    }
  }

  return node;
}

// Whether the given offset is undefined.
static bool is_undefined(intptr_t offset) {
  return offset == Type::OffsetTop;
}

// Whether the given offset is unknown.
static bool is_unknown(intptr_t offset) {
  return offset == Type::OffsetBot;
}

// Whether the given offset is concrete (defined and compile-time known).
static bool is_concrete(intptr_t offset) {
  return !is_undefined(offset) && !is_unknown(offset);
}

// Compute base + offset components of the memory address accessed by mach.
// Return a node representing the base address, or null if the base cannot be
// found or the offset is undefined or a concrete negative value. If a non-null
// base is returned, the offset is a concrete, nonnegative value or unknown.
static const Node* get_base_and_offset(const MachNode* mach, intptr_t& offset) {
  const TypePtr* adr_type = nullptr;
  offset = 0;
  const Node* base = mach->get_base_and_disp(offset, adr_type);

  if (base == nullptr || base == NodeSentinel) {
    return nullptr;
  }

  if (offset == 0 && base->is_Mach() && base->as_Mach()->ideal_Opcode() == Op_AddP) {
    // The memory address is computed by 'base' and fed to 'mach' via an
    // indirect memory operand (indicated by offset == 0). The ultimate base and
    // offset can be fetched directly from the inputs and Ideal type of 'base'.
    const TypeOopPtr* oopptr = base->bottom_type()->isa_oopptr();
    if (oopptr == nullptr) return nullptr;
    offset = oopptr->offset();
    // Even if 'base' is not an Ideal AddP node anymore, Matcher::ReduceInst()
    // guarantees that the base address is still available at the same slot.
    base = base->in(AddPNode::Base);
    assert(base != nullptr, "");
  }

  if (is_undefined(offset) || (is_concrete(offset) && offset < 0)) {
    return nullptr;
  }

  return look_through_node(base);
}

// Whether a phi node corresponds to an array allocation.
// This test is incomplete: in some edge cases, it might return false even
// though the node does correspond to an array allocation.
static bool is_array_allocation(const Node* phi) {
  precond(phi->is_Phi());
  // Check whether phi has a successor cast (CheckCastPP) to Java array pointer,
  // possibly below spill copies and other cast nodes. Limit the exploration to
  // a single path from the phi node consisting of these node types.
  const Node* current = phi;
  while (true) {
    const Node* next = nullptr;
    for (DUIterator_Fast imax, i = current->fast_outs(imax); i < imax; i++) {
      if (!current->fast_out(i)->isa_Mach()) {
        continue;
      }
      const MachNode* succ = current->fast_out(i)->as_Mach();
      if (succ->ideal_Opcode() == Op_CheckCastPP) {
        if (succ->get_ptr_type()->isa_aryptr()) {
          // Cast to Java array pointer: phi corresponds to an array allocation.
          return true;
        }
        // Other cast: record as candidate for further exploration.
        next = succ;
      } else if (succ->is_SpillCopy() && next == nullptr) {
        // Spill copy, and no better candidate found: record as candidate.
        next = succ;
      }
    }
    if (next == nullptr) {
      // No evidence found that phi corresponds to an array allocation, and no
      // candidates available to continue exploring.
      return false;
    }
    // Continue exploring from the best candidate found.
    current = next;
  }
  ShouldNotReachHere();
}

bool BarrierSetC2::is_allocation(const Node* node) {
  assert(node->is_Phi(), "expected phi node");
  if (node->req() != 3) {
    return false;
  }
  const Node* const fast_node = node->in(2);
  if (!fast_node->is_Mach()) {
    return false;
  }
  const MachNode* const fast_mach = fast_node->as_Mach();
  if (fast_mach->ideal_Opcode() != Op_LoadP) {
    return false;
  }
  intptr_t offset;
  const Node* const base = get_base_and_offset(fast_mach, offset);
  if (base == nullptr || !base->is_Mach() || !is_concrete(offset)) {
    return false;
  }
  const MachNode* const base_mach = base->as_Mach();
  if (base_mach->ideal_Opcode() != Op_ThreadLocal) {
    return false;
  }
  return offset == in_bytes(Thread::tlab_top_offset());
}

void BarrierSetC2::elide_dominated_barriers(Node_List& accesses, Node_List& access_dominators) const {
  Compile* const C = Compile::current();
  PhaseCFG* const cfg = C->cfg();

  for (uint i = 0; i < accesses.size(); i++) {
    MachNode* const access = accesses.at(i)->as_Mach();
    intptr_t access_offset;
    const Node* const access_obj = get_base_and_offset(access, access_offset);
    Block* const access_block = cfg->get_block_for_node(access);
    const uint access_index = block_index(access_block, access);

    if (access_obj == nullptr) {
      // No information available
      continue;
    }

    for (uint j = 0; j < access_dominators.size(); j++) {
     const  Node* const mem = access_dominators.at(j);
      if (mem->is_Phi()) {
        assert(is_allocation(mem), "expected allocation phi node");
        if (mem != access_obj) {
          continue;
        }
        if (is_unknown(access_offset) && !is_array_allocation(mem)) {
          // The accessed address has an unknown offset, but the allocated
          // object cannot be determined to be an array. Avoid eliding in this
          // case, to be on the safe side.
          continue;
        }
        assert((is_concrete(access_offset) && access_offset >= 0) || (is_unknown(access_offset) && is_array_allocation(mem)),
               "candidate allocation-dominated access offsets must be either concrete and nonnegative, or unknown (for array allocations only)");
      } else {
        // Access node
        const MachNode* const mem_mach = mem->as_Mach();
        intptr_t mem_offset;
        const Node* const mem_obj = get_base_and_offset(mem_mach, mem_offset);

        if (mem_obj == nullptr ||
            !is_concrete(access_offset) ||
            !is_concrete(mem_offset)) {
          // No information available
          continue;
        }

        if (mem_obj != access_obj || mem_offset != access_offset) {
          // Not the same addresses, not a candidate
          continue;
        }
        assert(is_concrete(access_offset) && access_offset >= 0,
               "candidate non-allocation-dominated access offsets must be concrete and nonnegative");
      }

      Block* mem_block = cfg->get_block_for_node(mem);
      const uint mem_index = block_index(mem_block, mem);

      if (access_block == mem_block) {
        // Earlier accesses in the same block
        if (mem_index < access_index && !block_has_safepoint(mem_block, mem_index + 1, access_index)) {
          elide_dominated_barrier(access);
        }
      } else if (mem_block->dominates(access_block)) {
        // Dominating block? Look around for safepoints
        ResourceMark rm;
        Block_List stack;
        VectorSet visited;
        stack.push(access_block);
        bool safepoint_found = block_has_safepoint(access_block);
        while (!safepoint_found && stack.size() > 0) {
          const Block* const block = stack.pop();
          if (visited.test_set(block->_pre_order)) {
            continue;
          }
          if (block_has_safepoint(block)) {
            safepoint_found = true;
            break;
          }
          if (block == mem_block) {
            continue;
          }

          // Push predecessor blocks
          for (uint p = 1; p < block->num_preds(); ++p) {
            Block* const pred = cfg->get_block_for_node(block->pred(p));
            stack.push(pred);
          }
        }

        if (!safepoint_found) {
          elide_dominated_barrier(access);
        }
      }
    }
  }
}

void BarrierSetC2::compute_liveness_at_stubs() const {
  ResourceMark rm;
  Compile* const C = Compile::current();
  Arena* const A = Thread::current()->resource_area();
  PhaseCFG* const cfg = C->cfg();
  PhaseRegAlloc* const regalloc = C->regalloc();
  RegMask* const live = NEW_ARENA_ARRAY(A, RegMask, cfg->number_of_blocks() * sizeof(RegMask));
  BarrierSetAssembler* const bs = BarrierSet::barrier_set()->barrier_set_assembler();
  BarrierSetC2State* bs_state = barrier_set_state();
  Block_List worklist;

  for (uint i = 0; i < cfg->number_of_blocks(); ++i) {
    new ((void*)(live + i)) RegMask();
    worklist.push(cfg->get_block(i));
  }

  while (worklist.size() > 0) {
    const Block* const block = worklist.pop();
    RegMask& old_live = live[block->_pre_order];
    RegMask new_live;

    // Initialize to union of successors
    for (uint i = 0; i < block->_num_succs; i++) {
      const uint succ_id = block->_succs[i]->_pre_order;
      new_live.OR(live[succ_id]);
    }

    // Walk block backwards, computing liveness
    for (int i = block->number_of_nodes() - 1; i >= 0; --i) {
      const Node* const node = block->get_node(i);

      // If this node tracks out-liveness, update it
      if (!bs_state->needs_livein_data()) {
        RegMask* const regs = bs_state->live(node);
        if (regs != nullptr) {
          regs->OR(new_live);
        }
      }

      // Remove def bits
      const OptoReg::Name first = bs->refine_register(node, regalloc->get_reg_first(node));
      const OptoReg::Name second = bs->refine_register(node, regalloc->get_reg_second(node));
      if (first != OptoReg::Bad) {
        new_live.Remove(first);
      }
      if (second != OptoReg::Bad) {
        new_live.Remove(second);
      }

      // Add use bits
      for (uint j = 1; j < node->req(); ++j) {
        const Node* const use = node->in(j);
        const OptoReg::Name first = bs->refine_register(use, regalloc->get_reg_first(use));
        const OptoReg::Name second = bs->refine_register(use, regalloc->get_reg_second(use));
        if (first != OptoReg::Bad) {
          new_live.Insert(first);
        }
        if (second != OptoReg::Bad) {
          new_live.Insert(second);
        }
      }

      // If this node tracks in-liveness, update it
      if (bs_state->needs_livein_data()) {
        RegMask* const regs = bs_state->live(node);
        if (regs != nullptr) {
          regs->OR(new_live);
        }
      }
    }

    // Now at block top, see if we have any changes
    new_live.SUBTRACT(old_live);
    if (new_live.is_NotEmpty()) {
      // Liveness has refined, update and propagate to prior blocks
      old_live.OR(new_live);
      for (uint i = 1; i < block->num_preds(); ++i) {
        Block* const pred = cfg->get_block_for_node(block->pred(i));
        worklist.push(pred);
      }
    }
  }
}

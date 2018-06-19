/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "gc/shared/c2/barrierSetC2.hpp"
#include "opto/arraycopynode.hpp"
#include "opto/graphKit.hpp"
#include "opto/idealKit.hpp"
#include "opto/narrowptrnode.hpp"
#include "utilities/macros.hpp"

// By default this is a no-op.
void BarrierSetC2::resolve_address(C2Access& access) const { }

void* C2Access::barrier_set_state() const {
  return _kit->barrier_set_state();
}

bool C2Access::needs_cpu_membar() const {
  bool mismatched = (_decorators & C2_MISMATCHED) != 0;
  bool is_unordered = (_decorators & MO_UNORDERED) != 0;
  bool anonymous = (_decorators & C2_UNSAFE_ACCESS) != 0;
  bool in_heap = (_decorators & IN_HEAP) != 0;

  bool is_write = (_decorators & C2_WRITE_ACCESS) != 0;
  bool is_read = (_decorators & C2_READ_ACCESS) != 0;
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
    if (!in_heap || !is_unordered || (mismatched && !_addr.type()->isa_aryptr())) {
      return true;
    }
  }

  return false;
}

Node* BarrierSetC2::store_at_resolved(C2Access& access, C2AccessValue& val) const {
  DecoratorSet decorators = access.decorators();
  GraphKit* kit = access.kit();

  bool mismatched = (decorators & C2_MISMATCHED) != 0;
  bool unaligned = (decorators & C2_UNALIGNED) != 0;
  bool requires_atomic_access = (decorators & MO_UNORDERED) == 0;

  bool in_native = (decorators & IN_NATIVE) != 0;
  assert(!in_native, "not supported yet");

  if (access.type() == T_DOUBLE) {
    Node* new_val = kit->dstore_rounding(val.node());
    val.set_node(new_val);
  }

  MemNode::MemOrd mo = access.mem_node_mo();

  Node* store = kit->store_to_memory(kit->control(), access.addr().node(), val.node(), access.type(),
                                     access.addr().type(), mo, requires_atomic_access, unaligned, mismatched);
  access.set_raw_access(store);
  return store;
}

Node* BarrierSetC2::load_at_resolved(C2Access& access, const Type* val_type) const {
  DecoratorSet decorators = access.decorators();
  GraphKit* kit = access.kit();

  Node* adr = access.addr().node();
  const TypePtr* adr_type = access.addr().type();

  bool mismatched = (decorators & C2_MISMATCHED) != 0;
  bool requires_atomic_access = (decorators & MO_UNORDERED) == 0;
  bool unaligned = (decorators & C2_UNALIGNED) != 0;
  bool control_dependent = (decorators & C2_CONTROL_DEPENDENT_LOAD) != 0;
  bool pinned = (decorators & C2_PINNED_LOAD) != 0;

  bool in_native = (decorators & IN_NATIVE) != 0;
  assert(!in_native, "not supported yet");

  MemNode::MemOrd mo = access.mem_node_mo();
  LoadNode::ControlDependency dep = pinned ? LoadNode::Pinned : LoadNode::DependsOnlyOnTest;
  Node* control = control_dependent ? kit->control() : NULL;

  Node* load = kit->make_load(control, adr, val_type, access.type(), adr_type, mo,
                              dep, requires_atomic_access, unaligned, mismatched);
  access.set_raw_access(load);

  return load;
}

class C2AccessFence: public StackObj {
  C2Access& _access;

public:
  C2AccessFence(C2Access& access) :
    _access(access) {
    GraphKit* kit = access.kit();
    DecoratorSet decorators = access.decorators();

    bool is_write = (decorators & C2_WRITE_ACCESS) != 0;
    bool is_read = (decorators & C2_READ_ACCESS) != 0;
    bool is_atomic = is_read && is_write;

    bool is_volatile = (decorators & MO_SEQ_CST) != 0;
    bool is_release = (decorators & MO_RELEASE) != 0;

    if (is_atomic) {
      // Memory-model-wise, a LoadStore acts like a little synchronized
      // block, so needs barriers on each side.  These don't translate
      // into actual barriers on most machines, but we still need rest of
      // compiler to respect ordering.
      if (is_release) {
        kit->insert_mem_bar(Op_MemBarRelease);
      } else if (is_volatile) {
        if (support_IRIW_for_not_multiple_copy_atomic_cpu) {
          kit->insert_mem_bar(Op_MemBarVolatile);
        } else {
          kit->insert_mem_bar(Op_MemBarRelease);
        }
      }
    } else if (is_write) {
      // If reference is volatile, prevent following memory ops from
      // floating down past the volatile write.  Also prevents commoning
      // another volatile read.
      if (is_volatile || is_release) {
        kit->insert_mem_bar(Op_MemBarRelease);
      }
    } else {
      // Memory barrier to prevent normal and 'unsafe' accesses from
      // bypassing each other.  Happens after null checks, so the
      // exception paths do not take memory state from the memory barrier,
      // so there's no problems making a strong assert about mixing users
      // of safe & unsafe memory.
      if (is_volatile && support_IRIW_for_not_multiple_copy_atomic_cpu) {
        kit->insert_mem_bar(Op_MemBarVolatile);
      }
    }

    if (access.needs_cpu_membar()) {
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
    GraphKit* kit = _access.kit();
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
      if (is_acquire || is_volatile) {
        kit->insert_mem_bar(Op_MemBarAcquire);
      }
    } else if (is_write) {
      // If not multiple copy atomic, we do the MemBarVolatile before the load.
      if (is_volatile && !support_IRIW_for_not_multiple_copy_atomic_cpu) {
        kit->insert_mem_bar(Op_MemBarVolatile); // Use fat membar
      }
    } else {
      if (is_volatile || is_acquire) {
        kit->insert_mem_bar(Op_MemBarAcquire, _access.raw_access());
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

  _decorators = AccessInternal::decorator_fixup(_decorators);

  if (is_read && !is_write && anonymous) {
    // To be valid, unsafe loads may depend on other conditions than
    // the one that guards them: pin the Load node
    _decorators |= C2_CONTROL_DEPENDENT_LOAD;
    _decorators |= C2_PINNED_LOAD;
    const TypePtr* adr_type = _addr.type();
    Node* adr = _addr.node();
    if (!needs_cpu_membar() && adr_type->isa_instptr()) {
      assert(adr_type->meet(TypePtr::NULL_PTR) != adr_type->remove_speculative(), "should be not null");
      intptr_t offset = Type::OffsetBot;
      AddPNode::Ideal_base_and_offset(adr, &_kit->gvn(), offset);
      if (offset >= 0) {
        int s = Klass::layout_helper_size_in_bytes(adr_type->isa_instptr()->klass()->layout_helper());
        if (offset < s) {
          // Guaranteed to be a valid access, no need to pin it
          _decorators ^= C2_CONTROL_DEPENDENT_LOAD;
          _decorators ^= C2_PINNED_LOAD;
        }
      }
    }
  }
}

//--------------------------- atomic operations---------------------------------

static void pin_atomic_op(C2AtomicAccess& access) {
  if (!access.needs_pinning()) {
    return;
  }
  // SCMemProjNodes represent the memory state of a LoadStore. Their
  // main role is to prevent LoadStore nodes from being optimized away
  // when their results aren't used.
  GraphKit* kit = access.kit();
  Node* load_store = access.raw_access();
  assert(load_store != NULL, "must pin atomic op");
  Node* proj = kit->gvn().transform(new SCMemProjNode(load_store));
  kit->set_memory(proj, access.alias_idx());
}

void C2AtomicAccess::set_memory() {
  Node *mem = _kit->memory(_alias_idx);
  _memory = mem;
}

Node* BarrierSetC2::atomic_cmpxchg_val_at_resolved(C2AtomicAccess& access, Node* expected_val,
                                                   Node* new_val, const Type* value_type) const {
  GraphKit* kit = access.kit();
  MemNode::MemOrd mo = access.mem_node_mo();
  Node* mem = access.memory();

  Node* adr = access.addr().node();
  const TypePtr* adr_type = access.addr().type();

  Node* load_store = NULL;

  if (access.is_oop()) {
#ifdef _LP64
    if (adr->bottom_type()->is_ptr_to_narrowoop()) {
      Node *newval_enc = kit->gvn().transform(new EncodePNode(new_val, new_val->bottom_type()->make_narrowoop()));
      Node *oldval_enc = kit->gvn().transform(new EncodePNode(expected_val, expected_val->bottom_type()->make_narrowoop()));
      load_store = kit->gvn().transform(new CompareAndExchangeNNode(kit->control(), mem, adr, newval_enc, oldval_enc, adr_type, value_type->make_narrowoop(), mo));
    } else
#endif
    {
      load_store = kit->gvn().transform(new CompareAndExchangePNode(kit->control(), mem, adr, new_val, expected_val, adr_type, value_type->is_oopptr(), mo));
    }
  } else {
    switch (access.type()) {
      case T_BYTE: {
        load_store = kit->gvn().transform(new CompareAndExchangeBNode(kit->control(), mem, adr, new_val, expected_val, adr_type, mo));
        break;
      }
      case T_SHORT: {
        load_store = kit->gvn().transform(new CompareAndExchangeSNode(kit->control(), mem, adr, new_val, expected_val, adr_type, mo));
        break;
      }
      case T_INT: {
        load_store = kit->gvn().transform(new CompareAndExchangeINode(kit->control(), mem, adr, new_val, expected_val, adr_type, mo));
        break;
      }
      case T_LONG: {
        load_store = kit->gvn().transform(new CompareAndExchangeLNode(kit->control(), mem, adr, new_val, expected_val, adr_type, mo));
        break;
      }
      default:
        ShouldNotReachHere();
    }
  }

  access.set_raw_access(load_store);
  pin_atomic_op(access);

#ifdef _LP64
  if (access.is_oop() && adr->bottom_type()->is_ptr_to_narrowoop()) {
    return kit->gvn().transform(new DecodeNNode(load_store, load_store->get_ptr_type()));
  }
#endif

  return load_store;
}

Node* BarrierSetC2::atomic_cmpxchg_bool_at_resolved(C2AtomicAccess& access, Node* expected_val,
                                                    Node* new_val, const Type* value_type) const {
  GraphKit* kit = access.kit();
  DecoratorSet decorators = access.decorators();
  MemNode::MemOrd mo = access.mem_node_mo();
  Node* mem = access.memory();
  bool is_weak_cas = (decorators & C2_WEAK_CMPXCHG) != 0;
  Node* load_store = NULL;
  Node* adr = access.addr().node();

  if (access.is_oop()) {
#ifdef _LP64
    if (adr->bottom_type()->is_ptr_to_narrowoop()) {
      Node *newval_enc = kit->gvn().transform(new EncodePNode(new_val, new_val->bottom_type()->make_narrowoop()));
      Node *oldval_enc = kit->gvn().transform(new EncodePNode(expected_val, expected_val->bottom_type()->make_narrowoop()));
      if (is_weak_cas) {
        load_store = kit->gvn().transform(new WeakCompareAndSwapNNode(kit->control(), mem, adr, newval_enc, oldval_enc, mo));
      } else {
        load_store = kit->gvn().transform(new CompareAndSwapNNode(kit->control(), mem, adr, newval_enc, oldval_enc, mo));
      }
    } else
#endif
    {
      if (is_weak_cas) {
        load_store = kit->gvn().transform(new WeakCompareAndSwapPNode(kit->control(), mem, adr, new_val, expected_val, mo));
      } else {
        load_store = kit->gvn().transform(new CompareAndSwapPNode(kit->control(), mem, adr, new_val, expected_val, mo));
      }
    }
  } else {
    switch(access.type()) {
      case T_BYTE: {
        if (is_weak_cas) {
          load_store = kit->gvn().transform(new WeakCompareAndSwapBNode(kit->control(), mem, adr, new_val, expected_val, mo));
        } else {
          load_store = kit->gvn().transform(new CompareAndSwapBNode(kit->control(), mem, adr, new_val, expected_val, mo));
        }
        break;
      }
      case T_SHORT: {
        if (is_weak_cas) {
          load_store = kit->gvn().transform(new WeakCompareAndSwapSNode(kit->control(), mem, adr, new_val, expected_val, mo));
        } else {
          load_store = kit->gvn().transform(new CompareAndSwapSNode(kit->control(), mem, adr, new_val, expected_val, mo));
        }
        break;
      }
      case T_INT: {
        if (is_weak_cas) {
          load_store = kit->gvn().transform(new WeakCompareAndSwapINode(kit->control(), mem, adr, new_val, expected_val, mo));
        } else {
          load_store = kit->gvn().transform(new CompareAndSwapINode(kit->control(), mem, adr, new_val, expected_val, mo));
        }
        break;
      }
      case T_LONG: {
        if (is_weak_cas) {
          load_store = kit->gvn().transform(new WeakCompareAndSwapLNode(kit->control(), mem, adr, new_val, expected_val, mo));
        } else {
          load_store = kit->gvn().transform(new CompareAndSwapLNode(kit->control(), mem, adr, new_val, expected_val, mo));
        }
        break;
      }
      default:
        ShouldNotReachHere();
    }
  }

  access.set_raw_access(load_store);
  pin_atomic_op(access);

  return load_store;
}

Node* BarrierSetC2::atomic_xchg_at_resolved(C2AtomicAccess& access, Node* new_val, const Type* value_type) const {
  GraphKit* kit = access.kit();
  Node* mem = access.memory();
  Node* adr = access.addr().node();
  const TypePtr* adr_type = access.addr().type();
  Node* load_store = NULL;

  if (access.is_oop()) {
#ifdef _LP64
    if (adr->bottom_type()->is_ptr_to_narrowoop()) {
      Node *newval_enc = kit->gvn().transform(new EncodePNode(new_val, new_val->bottom_type()->make_narrowoop()));
      load_store = kit->gvn().transform(new GetAndSetNNode(kit->control(), mem, adr, newval_enc, adr_type, value_type->make_narrowoop()));
    } else
#endif
    {
      load_store = kit->gvn().transform(new GetAndSetPNode(kit->control(), mem, adr, new_val, adr_type, value_type->is_oopptr()));
    }
  } else  {
    switch (access.type()) {
      case T_BYTE:
        load_store = kit->gvn().transform(new GetAndSetBNode(kit->control(), mem, adr, new_val, adr_type));
        break;
      case T_SHORT:
        load_store = kit->gvn().transform(new GetAndSetSNode(kit->control(), mem, adr, new_val, adr_type));
        break;
      case T_INT:
        load_store = kit->gvn().transform(new GetAndSetINode(kit->control(), mem, adr, new_val, adr_type));
        break;
      case T_LONG:
        load_store = kit->gvn().transform(new GetAndSetLNode(kit->control(), mem, adr, new_val, adr_type));
        break;
      default:
        ShouldNotReachHere();
    }
  }

  access.set_raw_access(load_store);
  pin_atomic_op(access);

#ifdef _LP64
  if (access.is_oop() && adr->bottom_type()->is_ptr_to_narrowoop()) {
    return kit->gvn().transform(new DecodeNNode(load_store, load_store->get_ptr_type()));
  }
#endif

  return load_store;
}

Node* BarrierSetC2::atomic_add_at_resolved(C2AtomicAccess& access, Node* new_val, const Type* value_type) const {
  Node* load_store = NULL;
  GraphKit* kit = access.kit();
  Node* adr = access.addr().node();
  const TypePtr* adr_type = access.addr().type();
  Node* mem = access.memory();

  switch(access.type()) {
    case T_BYTE:
      load_store = kit->gvn().transform(new GetAndAddBNode(kit->control(), mem, adr, new_val, adr_type));
      break;
    case T_SHORT:
      load_store = kit->gvn().transform(new GetAndAddSNode(kit->control(), mem, adr, new_val, adr_type));
      break;
    case T_INT:
      load_store = kit->gvn().transform(new GetAndAddINode(kit->control(), mem, adr, new_val, adr_type));
      break;
    case T_LONG:
      load_store = kit->gvn().transform(new GetAndAddLNode(kit->control(), mem, adr, new_val, adr_type));
      break;
    default:
      ShouldNotReachHere();
  }

  access.set_raw_access(load_store);
  pin_atomic_op(access);

  return load_store;
}

Node* BarrierSetC2::atomic_cmpxchg_val_at(C2AtomicAccess& access, Node* expected_val,
                                          Node* new_val, const Type* value_type) const {
  C2AccessFence fence(access);
  resolve_address(access);
  return atomic_cmpxchg_val_at_resolved(access, expected_val, new_val, value_type);
}

Node* BarrierSetC2::atomic_cmpxchg_bool_at(C2AtomicAccess& access, Node* expected_val,
                                           Node* new_val, const Type* value_type) const {
  C2AccessFence fence(access);
  resolve_address(access);
  return atomic_cmpxchg_bool_at_resolved(access, expected_val, new_val, value_type);
}

Node* BarrierSetC2::atomic_xchg_at(C2AtomicAccess& access, Node* new_val, const Type* value_type) const {
  C2AccessFence fence(access);
  resolve_address(access);
  return atomic_xchg_at_resolved(access, new_val, value_type);
}

Node* BarrierSetC2::atomic_add_at(C2AtomicAccess& access, Node* new_val, const Type* value_type) const {
  C2AccessFence fence(access);
  resolve_address(access);
  return atomic_add_at_resolved(access, new_val, value_type);
}

void BarrierSetC2::clone(GraphKit* kit, Node* src, Node* dst, Node* size, bool is_array) const {
  // Exclude the header but include array length to copy by 8 bytes words.
  // Can't use base_offset_in_bytes(bt) since basic type is unknown.
  int base_off = is_array ? arrayOopDesc::length_offset_in_bytes() :
                            instanceOopDesc::base_offset_in_bytes();
  // base_off:
  // 8  - 32-bit VM
  // 12 - 64-bit VM, compressed klass
  // 16 - 64-bit VM, normal klass
  if (base_off % BytesPerLong != 0) {
    assert(UseCompressedClassPointers, "");
    if (is_array) {
      // Exclude length to copy by 8 bytes words.
      base_off += sizeof(int);
    } else {
      // Include klass to copy by 8 bytes words.
      base_off = instanceOopDesc::klass_offset_in_bytes();
    }
    assert(base_off % BytesPerLong == 0, "expect 8 bytes alignment");
  }
  Node* src_base  = kit->basic_plus_adr(src,  base_off);
  Node* dst_base = kit->basic_plus_adr(dst, base_off);

  // Compute the length also, if needed:
  Node* countx = size;
  countx = kit->gvn().transform(new SubXNode(countx, kit->MakeConX(base_off)));
  countx = kit->gvn().transform(new URShiftXNode(countx, kit->intcon(LogBytesPerLong) ));

  const TypePtr* raw_adr_type = TypeRawPtr::BOTTOM;

  ArrayCopyNode* ac = ArrayCopyNode::make(kit, false, src_base, NULL, dst_base, NULL, countx, false, false);
  ac->set_clonebasic();
  Node* n = kit->gvn().transform(ac);
  if (n == ac) {
    kit->set_predefined_output_for_runtime_call(ac, ac->in(TypeFunc::Memory), raw_adr_type);
  } else {
    kit->set_all_memory(n);
  }
}

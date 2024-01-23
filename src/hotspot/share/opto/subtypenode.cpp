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
 *
 */

#include "precompiled.hpp"
#include "opto/addnode.hpp"
#include "opto/callnode.hpp"
#include "opto/connode.hpp"
#include "opto/convertnode.hpp"
#include "opto/phaseX.hpp"
#include "opto/rootnode.hpp"
#include "opto/subnode.hpp"
#include "opto/subtypenode.hpp"

const Type* SubTypeCheckNode::sub(const Type* sub_t, const Type* super_t) const {
  const TypeKlassPtr* superk = super_t->isa_klassptr();
  assert(sub_t != Type::TOP && !TypePtr::NULL_PTR->higher_equal(sub_t), "should be not null");
  const TypeKlassPtr* subk = sub_t->isa_klassptr() ? sub_t->is_klassptr() : sub_t->is_oopptr()->as_klass_type();

  // Oop can't be a subtype of abstract type that has no subclass.
  if (sub_t->isa_oopptr() && superk->isa_instklassptr() && superk->klass_is_exact()) {
    ciKlass* superklass = superk->exact_klass();
    if (!superklass->is_interface() && superklass->is_abstract() &&
        !superklass->as_instance_klass()->has_subklass()) {
      Compile::current()->dependencies()->assert_leaf_type(superklass);
      if (subk->is_same_java_type_as(superk) && !sub_t->maybe_null()) {
        // The super_t has no subclasses, and sub_t has the same type and is not null,
        // hence the check should always evaluate to EQ. However, this is an impossible
        // situation since super_t is also abstract, and hence sub_t cannot have the
        // same type and be non-null.
        // Still, if the non-static method of an abstract class without subclasses is
        // force-compiled, the Param0 has the self/this pointer with NotNull. This
        // method would now never be called, because of the leaf-type dependency. Hence,
        // just for consistency with verification, we return EQ.
        return TypeInt::CC_EQ;
      }
      // subk is either a supertype of superk, or null. In either case, superk is a subtype.
      return TypeInt::CC_GT;
    }
  }

  if (subk != nullptr) {
    switch (Compile::current()->static_subtype_check(superk, subk, false)) {
      case Compile::SSC_always_false:
        return TypeInt::CC_GT;
      case Compile::SSC_always_true:
        return TypeInt::CC_EQ;
      case Compile::SSC_easy_test:
      case Compile::SSC_full_test:
        break;
      default:
        ShouldNotReachHere();
    }
  }

  return bottom_type();
}

Node *SubTypeCheckNode::Ideal(PhaseGVN* phase, bool can_reshape) {
  Node* obj_or_subklass = in(ObjOrSubKlass);
  Node* superklass = in(SuperKlass);

  if (obj_or_subklass == nullptr ||
      superklass == nullptr) {
    return nullptr;
  }

  const Type* sub_t = phase->type(obj_or_subklass);
  const Type* super_t = phase->type(superklass);

  if (!super_t->isa_klassptr() ||
      (!sub_t->isa_klassptr() && !sub_t->isa_oopptr())) {
    return nullptr;
  }

  Node* addr = nullptr;
  if (obj_or_subklass->is_DecodeNKlass()) {
    if (obj_or_subklass->in(1) != nullptr &&
        obj_or_subklass->in(1)->Opcode() == Op_LoadNKlass) {
      addr = obj_or_subklass->in(1)->in(MemNode::Address);
    }
  } else if (obj_or_subklass->Opcode() == Op_LoadKlass) {
    addr = obj_or_subklass->in(MemNode::Address);
  }

  if (addr != nullptr) {
    intptr_t con = 0;
    Node* obj = AddPNode::Ideal_base_and_offset(addr, phase, con);
    if (con == oopDesc::klass_offset_in_bytes() && obj != nullptr) {
      assert(is_oop(phase, obj), "only for oop input");
      set_req_X(ObjOrSubKlass, obj, phase);
      return this;
    }
  }

  // AllocateNode might have more accurate klass input
  Node* allocated_klass = AllocateNode::Ideal_klass(obj_or_subklass, phase);
  if (allocated_klass != nullptr) {
    assert(is_oop(phase, obj_or_subklass), "only for oop input");
    set_req_X(ObjOrSubKlass, allocated_klass, phase);
    return this;
  }

  // Verify that optimizing the subtype check to a simple code pattern
  // when possible would not constant fold better
  assert(verify(phase), "missing Value() optimization");

  return nullptr;
}

#ifdef ASSERT
bool SubTypeCheckNode::is_oop(PhaseGVN* phase, Node* n) {
    const Type* t = phase->type(n);
    if (!t->isa_oopptr() && t != Type::TOP) {
      n->dump();
      t->dump(); tty->cr();
      return false;
    }
    return true;
}

static Node* record_for_cleanup(Node* n, PhaseGVN* phase) {
  if (phase->is_IterGVN()) {
    phase->is_IterGVN()->_worklist.push(n); // record for cleanup
  }
  return n;
}
bool SubTypeCheckNode::verify_helper(PhaseGVN* phase, Node* subklass, const Type* cached_t) {
  Node* cmp = phase->transform(new CmpPNode(subklass, in(SuperKlass)));
  record_for_cleanup(cmp, phase);

  const Type* cmp_t = phase->type(cmp);
  const Type* t = Value(phase);

  if (t == cmp_t ||
      t != cached_t || // previous observations don't hold anymore
      (cmp_t != TypeInt::CC_GT && cmp_t != TypeInt::CC_EQ)) {
    return true;
  } else {
    t->dump(); tty->cr();
    this->dump(2); tty->cr();
    cmp_t->dump(); tty->cr();
    subklass->dump(2); tty->cr();
    tty->print_cr("==============================");
    phase->C->root()->dump(9999);
    return false;
  }
}

// Verify that optimizing the subtype check to a simple code pattern when possible would not constant fold better.
bool SubTypeCheckNode::verify(PhaseGVN* phase) {
  Compile* C = phase->C;
  Node* obj_or_subklass = in(ObjOrSubKlass);
  Node* superklass = in(SuperKlass);

  const Type* sub_t = phase->type(obj_or_subklass);
  const Type* super_t = phase->type(superklass);

  const TypeKlassPtr* superk = super_t->isa_klassptr();
  const TypeKlassPtr* subk = sub_t->isa_klassptr() ? sub_t->is_klassptr() : sub_t->is_oopptr()->as_klass_type();

  if (super_t->singleton() && subk != nullptr) {
    if (obj_or_subklass->bottom_type() == Type::TOP) {
      // The bottom type of obj_or_subklass is TOP, despite its recorded type
      // being an OOP or a klass pointer. This can happen for example in
      // transient scenarios where obj_or_subklass is a projection of the TOP
      // node. In such cases, skip verification to avoid violating the contract
      // of LoadKlassNode::make(). This does not weaken the effect of verify(),
      // as SubTypeCheck nodes with TOP obj_or_subklass inputs are dead anyway.
      return true;
    }
    const Type* cached_t = Value(phase); // cache the type to validate consistency
    switch (C->static_subtype_check(superk, subk)) {
      case Compile::SSC_easy_test: {
        return verify_helper(phase, load_klass(phase), cached_t);
      }
      case Compile::SSC_full_test: {
        Node* p1 = phase->transform(new AddPNode(superklass, superklass, phase->MakeConX(in_bytes(Klass::super_check_offset_offset()))));
        Node* chk_off = phase->transform(new LoadINode(nullptr, C->immutable_memory(), p1, phase->type(p1)->is_ptr(), TypeInt::INT, MemNode::unordered));
        record_for_cleanup(chk_off, phase);

        int cacheoff_con = in_bytes(Klass::secondary_super_cache_offset());
        bool might_be_cache = (phase->find_int_con(chk_off, cacheoff_con) == cacheoff_con);
        if (!might_be_cache) {
          Node* subklass = load_klass(phase);
          Node* chk_off_X = chk_off;
#ifdef _LP64
          chk_off_X = phase->transform(new ConvI2LNode(chk_off_X));
#endif
          Node* p2 = phase->transform(new AddPNode(subklass, subklass, chk_off_X));
          Node* nkls = phase->transform(LoadKlassNode::make(*phase, nullptr, C->immutable_memory(), p2, phase->type(p2)->is_ptr(), TypeInstKlassPtr::OBJECT_OR_NULL));

          return verify_helper(phase, nkls, cached_t);
        }
        break;
      }
      case Compile::SSC_always_false:
      case Compile::SSC_always_true:
      default: {
        break; // nothing to do
      }
    }
  }

  return true;
}

Node* SubTypeCheckNode::load_klass(PhaseGVN* phase) const {
  Node* obj_or_subklass = in(ObjOrSubKlass);
  const Type* sub_t = phase->type(obj_or_subklass);
  Node* subklass = nullptr;
  if (sub_t->isa_oopptr()) {
    Node* adr = phase->transform(new AddPNode(obj_or_subklass, obj_or_subklass, phase->MakeConX(oopDesc::klass_offset_in_bytes())));
    subklass  = phase->transform(LoadKlassNode::make(*phase, nullptr, phase->C->immutable_memory(), adr, TypeInstPtr::KLASS));
    record_for_cleanup(subklass, phase);
  } else {
    subklass = obj_or_subklass;
  }
  return subklass;
}
#endif

uint SubTypeCheckNode::size_of() const {
  return sizeof(*this);
}

uint SubTypeCheckNode::hash() const {
  return NO_HASH;
}

#ifndef PRODUCT
void SubTypeCheckNode::dump_spec(outputStream* st) const {
  if (_method != nullptr) {
    st->print(" profiled at: ");
    _method->print_short_name(st);
    st->print(":%d", _bci);
  }
}
#endif
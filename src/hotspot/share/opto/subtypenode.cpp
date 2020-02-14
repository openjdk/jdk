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
 *
 */

#include "precompiled.hpp"
#include "opto/addnode.hpp"
#include "opto/connode.hpp"
#include "opto/convertnode.hpp"
#include "opto/phaseX.hpp"
#include "opto/subnode.hpp"
#include "opto/subtypenode.hpp"

const Type* SubTypeCheckNode::sub(const Type* sub_t, const Type* super_t) const {
  ciKlass* superk = super_t->is_klassptr()->klass();
  ciKlass* subk   = sub_t->isa_klassptr() ? sub_t->is_klassptr()->klass() : sub_t->is_oopptr()->klass();

  bool xsuperk = super_t->is_klassptr()->klass_is_exact();
  bool xsubk = sub_t->isa_klassptr() ? sub_t->is_klassptr()->klass_is_exact() : sub_t->is_oopptr()->klass_is_exact();

  // Similar to logic in CmpPNode::sub()
  if (superk && subk &&
      superk->is_loaded() && !superk->is_interface() &&
      subk->is_loaded() && !subk->is_interface() &&
      (!superk->is_obj_array_klass() ||
       !superk->as_obj_array_klass()->base_element_klass()->is_interface()) &&
      (!subk->is_obj_array_klass() ||
       !subk->as_obj_array_klass()->base_element_klass()->is_interface() ||
       xsubk)) {
    bool unrelated_classes = false;
    if (superk->equals(subk)) {
      // skip
    } else if (superk->is_subtype_of(subk)) {
      unrelated_classes = xsubk;
    } else if (subk->is_subtype_of(superk)) {
      // skip
    } else {
      unrelated_classes = true;
    }
    if (unrelated_classes) {
      TypePtr::PTR jp = sub_t->is_ptr()->join_ptr(super_t->is_ptr()->_ptr);
      if (jp != TypePtr::Null && jp != TypePtr::BotPTR) {
        return TypeInt::CC_GT;
      }
    }
  }

  if (super_t->singleton()) {
    if (subk != NULL) {
      switch (Compile::current()->static_subtype_check(superk, subk)) {
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
  }

  return bottom_type();
}

Node *SubTypeCheckNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  // Verify that optimizing the subtype check to a simple code pattern
  // when possible would not constant fold better
#ifdef ASSERT
  Node* obj_or_subklass = in(ObjOrSubKlass);
  Node* superklass = in(SuperKlass);

  if (obj_or_subklass == NULL ||
      superklass == NULL) {
    return NULL;
  }

  const Type* sub_t = phase->type(obj_or_subklass);
  const Type* super_t = phase->type(superklass);

  if (!super_t->isa_klassptr() ||
      (!sub_t->isa_klassptr() && !sub_t->isa_oopptr())) {
    return NULL;
  }


  ciKlass* superk = super_t->is_klassptr()->klass();
  ciKlass* subk   = sub_t->isa_klassptr() ? sub_t->is_klassptr()->klass() : sub_t->is_oopptr()->klass();

  if (super_t->singleton() && subk != NULL && phase->C->static_subtype_check(superk, subk) == Compile::SSC_easy_test) {
    Node* subklass = NULL;
    if (sub_t->isa_oopptr()) {
      Node* adr = phase->transform(new AddPNode(obj_or_subklass, obj_or_subklass, phase->MakeConX(oopDesc::klass_offset_in_bytes())));
      subklass = phase->transform(LoadKlassNode::make(*phase, NULL, phase->C->immutable_memory(), adr, TypeInstPtr::KLASS));
    } else {
      subklass = obj_or_subklass;
    }
    Node* res = new CmpPNode(subklass, superklass);
    const Type* t = phase->type(phase->transform(res));
    assert((Value(phase) == t) || (t != TypeInt::CC_GT && t != TypeInt::CC_EQ), "missing Value() optimization");
    if (phase->is_IterGVN()) {
      phase->is_IterGVN()->_worklist.push(res);
    }
    return NULL;
  }

  if (super_t->singleton() && subk != NULL && phase->C->static_subtype_check(superk, subk) == Compile::SSC_full_test) {
    Node* subklass = NULL;
    if (sub_t->isa_oopptr()) {
      Node* adr = phase->transform(new AddPNode(obj_or_subklass, obj_or_subklass, phase->MakeConX(oopDesc::klass_offset_in_bytes())));
      subklass = phase->transform(LoadKlassNode::make(*phase, NULL, phase->C->immutable_memory(), adr, TypeInstPtr::KLASS));
    } else {
      subklass = obj_or_subklass;
    }

    Node *p1 = phase->transform(new AddPNode(superklass, superklass, phase->MakeConX(in_bytes(Klass::super_check_offset_offset()))));
    Node* m = phase->C->immutable_memory();
    Node *chk_off = phase->transform(new LoadINode(NULL, m, p1, phase->type(p1)->is_ptr(), TypeInt::INT, MemNode::unordered));
    int cacheoff_con = in_bytes(Klass::secondary_super_cache_offset());
    bool might_be_cache = (phase->find_int_con(chk_off, cacheoff_con) == cacheoff_con);

    if (might_be_cache) {
      return NULL;
    }

    Node *chk_off_X = chk_off;
#ifdef _LP64
    chk_off_X = phase->transform(new ConvI2LNode(chk_off_X));
#endif
    Node *p2 = phase->transform(new AddPNode(subklass,subklass,chk_off_X));
    Node *kmem = phase->C->immutable_memory();
    Node *nkls = phase->transform(LoadKlassNode::make(*phase, NULL, kmem, p2, phase->type(p2)->is_ptr(), TypeKlassPtr::OBJECT_OR_NULL));

    Node* res = new CmpPNode(superklass, nkls);
    const Type* t = phase->type(phase->transform(res));
    assert((Value(phase) == t) || (t != TypeInt::CC_GT && t != TypeInt::CC_EQ), "missing Value() optimization");
    if (phase->is_IterGVN()) {
      phase->is_IterGVN()->_worklist.push(res);
    }
    return NULL;
  }
#endif

  return NULL;
}

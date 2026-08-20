/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OPTO_TYPEJAVAPTR_HPP
#define SHARE_OPTO_TYPEJAVAPTR_HPP

#include "cppstdlib/type_traits.hpp"
#include "opto/type.hpp"

class TypeJavaPtrMeetHelper {
private:
  friend class TypeJavaPtrJoinHelper;

public:
  template <class PtrType>
  static const PtrType* javaptr_type_xmeet(const PtrType* t1, const PtrType* t2) {
    if (t1 == t2) {
      return t1;
    }

    TypePtr::PTR ptr1 = t1->ptr();
    TypePtr::PTR ptr2 = t2->ptr();
    assert(ptr1 == TypePtr::Constant || ptr1 == TypePtr::NotNull || ptr1 == TypePtr::BotPTR, "unexpected ptr: %d", int(ptr1));
    assert(ptr2 == TypePtr::Constant || ptr2 == TypePtr::NotNull || ptr2 == TypePtr::BotPTR, "unexpected ptr: %d", int(ptr2));

    if constexpr (PtrType::is_oopptr_type) {
      return oopptr_type_xmeet(t1, t2);
    } else {
      return klassptr_type_xmeet(t1, t2);
    }
  }

private:
  template <class OopType>
  static const OopType* oopptr_type_xmeet(const OopType* t1, const OopType* t2) {
    assert(t1 != t2, "must have been handled");
    Type::TYPES base1 = t1->base();
    Type::TYPES base2 = t2->base();
    assert(base1 == Type::InstPtr || base1 == Type::AryPtr, "must be an oopptr: %d", int(base1));
    assert(base2 == Type::InstPtr || base2 == Type::AryPtr, "must be an oopptr: %d", int(base2));

    Type::Offset offset = meet_offset(t1, t2);
    auto interfaces = meet_interfaces(t1, t2);
    auto flat_in_array = meet_flat_in_array(t1, t2);
    int instance_id = meet_instance_id(t1, t2);
    auto speculative = meet_speculative(t1, t2);
    int inline_depth = meet_inline_depth(t1, t2);

    if (base1 != base2) {
      TypePtr::PTR ptr = t1->ptr() == TypePtr::BotPTR || t2->ptr() == TypePtr::BotPTR ? TypePtr::BotPTR : TypePtr::NotNull;
      return OopType::InstType::make(ptr, OopType::ciEnv::current()->Object_klass(), interfaces, false, nullptr, offset,
                                     flat_in_array, instance_id, speculative, inline_depth);
    } else if (base1 == Type::InstPtr) {
      return instptr_type_xmeet(t1->is_instptr(), t2->is_instptr(), offset, interfaces, flat_in_array, instance_id, speculative, inline_depth);
    } else {
      return aryptr_type_xmeet(t1->is_aryptr(), t2->is_aryptr(), offset, instance_id, speculative, inline_depth);
    }
  }

  template <class InstOopType, class PtrType, class InterfacesType>
  static const InstOopType* instptr_type_xmeet(const InstOopType* t1, const InstOopType* t2, Type::Offset offset, InterfacesType interfaces,
                                               TypePtr::FlatInArray flat_in_array, int instance_id, const PtrType* speculative, int inline_depth) {
    using ConstOopType = decltype(t1->const_oop());

    auto k1 = t1->instance_klass();
    auto k2 = t2->instance_klass();
    TypePtr::PTR ptr;
    ConstOopType const_oop = nullptr;
    meet_ptr_and_const_oop(ptr, const_oop, t1, t2);
    bool xk = t1->klass_is_exact() && t2->klass_is_exact() && k1 == k2;

    // Consider an unloaded class to be a direct child of j.l.O and not have any subclass
    decltype(k1) k;
    if (k1 == k2) {
      k = k1;
    } else if (k1->is_java_lang_Object() || k2->is_java_lang_Object() || !k1->is_loaded() || !k2->is_loaded()) {
      k = InstOopType::ciEnv::current()->Object_klass();
    } else {
      k = k1->least_common_ancestor(k2)->as_instance_klass();
    }

    return InstOopType::make(ptr, k, interfaces, xk, const_oop, offset, flat_in_array, instance_id, speculative, inline_depth);
  }

  template <class AryOopType, class PtrType>
  static const AryOopType* aryptr_type_xmeet(const AryOopType* t1, const AryOopType* t2, Type::Offset offset, int instance_id,
                                             const PtrType* speculative, int inline_depth) {
    using AryType = std::remove_pointer_t<decltype(t1->ary())>;
    using ConstOopType = decltype(t1->const_oop());
    using ElemType = decltype(t1->elem());
    using KlassType = decltype(t1->klass());

    TypePtr::PTR ptr;
    ConstOopType const_oop = nullptr;
    meet_ptr_and_const_oop(ptr, const_oop, t1, t2);

    ElemType elem;
    KlassType klass = nullptr;
    meet_ary_elem(elem, klass, t1, t2);

    auto size = t1->size()->meet(t2->size())->is_int();
    bool stable = t1->is_stable() && t2->is_stable();
    bool flat = t1->is_flat() && t2->is_flat();
    bool not_flat = t1->is_not_flat() && t2->is_not_flat();
    bool null_free = t1->is_null_free() && t2->is_null_free();
    bool not_null_free = t1->is_not_null_free() && t2->is_not_null_free();
    bool atomic = t1->is_atomic() && t2->is_atomic();
    auto ary = AryType::make(elem, size, stable, flat, not_flat, null_free, not_null_free, atomic);
    bool xk = t1->klass_is_exact() && t2->klass_is_exact() && !aryptr_klass_disjoint(t1, t2);
    auto field_offset = t1->field_offset().meet(t2->field_offset());
    bool autobox_cache = t1->is_autobox_cache() && t2->is_autobox_cache();
    return AryOopType::make(ptr, const_oop, ary, klass, xk, offset, field_offset, instance_id, speculative, inline_depth, autobox_cache);
  }

  template <class KlassType>
  static const KlassType* klassptr_type_xmeet(const KlassType* t1, const KlassType* t2) {
    assert(t1 != t2, "must have been handled");
    Type::TYPES base1 = t1->base();
    Type::TYPES base2 = t2->base();
    assert(base1 == Type::InstKlassPtr || base1 == Type::AryKlassPtr, "must be a klassptr: %d", int(base1));
    assert(base2 == Type::InstKlassPtr || base2 == Type::AryKlassPtr, "must be a klassptr: %d", int(base2));

    Type::Offset offset = meet_offset(t1, t2);
    auto interfaces = meet_interfaces(t1, t2);
    auto flat_in_array = meet_flat_in_array(t1, t2);

    if (base1 != base2) {
      TypePtr::PTR ptr = t1->ptr() == TypePtr::BotPTR || t2->ptr() == TypePtr::BotPTR ? TypePtr::BotPTR : TypePtr::NotNull;
      return KlassType::InstType::make(ptr, KlassType::ciEnv::current()->Object_klass(), interfaces, offset, flat_in_array);
    } else if (base1 == Type::InstKlassPtr) {
      return instklassptr_type_xmeet(t1->is_instklassptr(), t2->is_instklassptr(), offset, interfaces, flat_in_array);
    } else {
      return aryklassptr_type_xmeet(t1->is_aryklassptr(), t2->is_aryklassptr(), offset);
    }
  }

  template <class InstKlassType, class InterfacesType>
  static const InstKlassType* instklassptr_type_xmeet(const InstKlassType* t1, const InstKlassType* t2, Type::Offset offset, InterfacesType interfaces,
                                                      TypePtr::FlatInArray flat_in_array) {
    TypePtr::PTR ptr = meet_inst_klass_ptr(t1, t2);
    auto klass = t1->instance_klass()->least_common_ancestor(t2->instance_klass())->as_instance_klass();
    return InstKlassType::make(ptr, klass, interfaces, offset, flat_in_array);
  }

  template <class AryKlassType>
  static const AryKlassType* aryklassptr_type_xmeet(const AryKlassType* t1, const AryKlassType* t2, Type::Offset offset) {
    using ElemType = decltype(t1->elem());
    using KlassType = decltype(t1->klass());

    TypePtr::PTR ptr = meet_ary_klass_ptr(t1, t2);
    ElemType elem;
    KlassType klass = nullptr;
    meet_ary_elem(elem, klass, t1, t2);
    bool not_flat = t1->is_not_flat() && t2->is_not_flat();
    bool not_null_free = t1->is_not_null_free() && t2->is_not_null_free();
    bool flat = t1->is_flat() && t2->is_flat();
    bool null_free = t1->is_null_free() && t2->is_null_free();
    bool atomic = t1->is_atomic() && t2->is_atomic();
    bool refined = t1->is_refined_type() && t2->is_refined_type();
    return AryKlassType::make(ptr, elem, klass, offset, not_flat, not_null_free, flat, null_free, atomic, refined);
  }

  template <class PtrType>
  static Type::Offset meet_offset(const PtrType* t1, const PtrType* t2) {
    return Type::Offset(t1->offset()).meet(Type::Offset(t2->offset()));
  }

  template <class PtrType, class InterfacesType = std::invoke_result_t<decltype(&PtrType::interfaces), PtrType>>
  static InterfacesType meet_interfaces(const PtrType* t1, const PtrType* t2) {
    return t1->interfaces()->intersection_with(t2->interfaces());
  }

  template <class OopType>
  static int meet_instance_id(const OopType* t1, const OopType* t2) {
    int id1 = t1->instance_id();
    int id2 = t2->instance_id();
    assert(id1 != TypeOopPtr::InstanceTop && id2 != TypeOopPtr::InstanceTop, "InstanceTop must be normalized to TypePtr::TopPTR");
    return id1 == id2 ? id1 : TypeOopPtr::InstanceBot;
  }

  template <class OopType>
  static auto meet_speculative(const OopType* t1, const OopType* t2) {
    auto s1 = t1->speculative();
    auto s2 = t2->speculative();
    if (s1 == nullptr && s2 == nullptr) {
      return s1;
    }

    if (s1 == nullptr) {
      s1 = t1;
    } else if (s2 == nullptr) {
      s2 = t2;
    }
    return s1->meet(s2)->is_ptr();
  }

  template <class OopType>
  static int meet_inline_depth(const OopType* t1, const OopType* t2) {
    return MAX2(t1->inline_depth(), t2->inline_depth());
  }

  template <class OopType, class ConstOopType>
  static void meet_ptr_and_const_oop(TypePtr::PTR& ptr, ConstOopType& const_oop, const OopType* t1, const OopType* t2) {
    if (t1->ptr() == TypePtr::Constant && t2->ptr() == TypePtr::Constant && t1->const_oop() == t2->const_oop()) {
      ptr = TypePtr::Constant;
      const_oop = t1->const_oop();
    } else if (t1->ptr() != TypePtr::BotPTR && t2->ptr() != TypePtr::BotPTR) {
      ptr = TypePtr::NotNull;
    } else {
      ptr = TypePtr::BotPTR;
    }
  }

  template <class InstKlassType>
  static TypePtr::PTR meet_inst_klass_ptr(const InstKlassType* t1, const InstKlassType* t2) {
    if (t1->ptr() == TypePtr::Constant && t2->ptr() == TypePtr::Constant &&
        t1->instance_klass() == t2->instance_klass() && t1->interfaces() == t2->interfaces()) {
      return TypePtr::Constant;
    } else if (t1->ptr() != TypePtr::BotPTR && t2->ptr() != TypePtr::BotPTR) {
      return TypePtr::NotNull;
    } else {
      return TypePtr::BotPTR;
    }
  }

  template <class AryKlassType>
  static TypePtr::PTR meet_ary_klass_ptr(const AryKlassType* t1, const AryKlassType* t2) {
    if (t1->ptr() == TypePtr::Constant && t2->ptr() == TypePtr::Constant &&
        t1->elem() == t2->elem() && t1->klass() == t2->klass() &&
        t1->is_not_flat() == t2->is_not_flat() && t1->is_not_null_free() == t2->is_not_null_free() &&
        t1->is_flat() == t2->is_flat() && t1->is_null_free() == t2->is_null_free() &&
        t1->is_atomic() == t2->is_atomic() && t1->is_refined_type() == t2->is_refined_type()) {
      return TypePtr::Constant;
    } else if (t1->ptr() != TypePtr::BotPTR && t2->ptr() != TypePtr::BotPTR) {
      return TypePtr::NotNull;
    } else {
      return TypePtr::BotPTR;
    }
  }

  template <class PtrType>
  static TypePtr::FlatInArray meet_flat_in_array(const PtrType* t1, const PtrType* t2) {
    auto v1 = t1->flat_in_array();
    auto v2 = t2->flat_in_array();
    assert(v1 != TypePtr::TopFlat && v2 != TypePtr::TopFlat, "TopFlat must be normalized to TypePtr::TopPTR");
    return v1 == v2 ? v1 : TypePtr::MaybeFlat;
  }

  template <class AryType, class ElemType, class CIKlassType>
  static void meet_ary_elem(const ElemType*& elem, CIKlassType& klass, const AryType* t1, const AryType* t2) {
    const ElemType* elem1 = t1->elem();
    const ElemType* elem2 = t2->elem();
    assert(!elem1->empty() && !elem2->empty(), "cannot be top");
    if (elem1->base() == elem2->base()) {
      if (elem1->base() == Type::Int) {
        // boolean[], byte[], short[], char[], int[] all use some kinds of TypeInt as their element
        // types, klass is used to distinguish between them. As a result, different kinds of array
        // should result in bot[].
        CIKlassType klass1 = t1->klass();
        CIKlassType klass2 = t2->klass();
        assert(klass1 != nullptr && klass2 != nullptr, "ambiguous array");
        if (klass1 == klass2) {
          elem = elem1->meet_speculative(elem2);
          klass = klass1;
        } else {
          elem = ElemType::BOTTOM;
        }
      } else {
        elem = elem1->meet_speculative(elem2);
      }
    } else {
      if (elem1->make_ptr() != nullptr && elem2->make_ptr() != nullptr) {
        elem = elem1->meet_speculative(elem2);
      } else {
        elem = ElemType::BOTTOM;
      }
    }
  }

  // TypeAryPtr is tricky, an exact Number[][] and an exact Integer[][] should be disjoint.
  // However, their elements are non-exact Number[] and exact Integer[], respectively, which are
  // not disjoint. This function specifically handle those cases.
  template <class AryOopType>
  static bool aryptr_klass_disjoint(const AryOopType* t1, const AryOopType* t2) {
    if (!t1->klass_is_exact() && !t2->klass_is_exact()) {
      // If t1 and t2 are both non-exact and disjoint, their elems should be disjoint, too. As a
      // result, we do not need to handle that case here.
      return false;
    }

    decltype(t1->is_oopptr()) exact_type;
    decltype(t1->is_oopptr()) other_type;
    bool both_are_exact;
    if (t1->klass_is_exact()) {
      exact_type = t1;
      other_type = t2;
      both_are_exact = t2->klass_is_exact();
    } else {
      exact_type = t2;
      other_type = t1;
      both_are_exact = t1->klass_is_exact();
    }

    // At each iteration, walk down from the array klasses to their element types. Keep
    // both_are_exact because the element type of an exact Number[][] is a non-exact Number[], but
    // we need to remember that the original type is an exact array.
    while (true) {
      if (exact_type->base() == Type::InstPtr) {
        if (other_type->base() == Type::AryPtr) {
          return true;
        }

        auto exact_klass = exact_type->is_instptr()->instance_klass();
        auto other_klass = other_type->is_instptr()->instance_klass();
        if (both_are_exact) {
          return exact_klass != other_klass || exact_type->interfaces() != other_type->interfaces();
        } else {
          return !exact_klass->is_subtype_of(other_klass) || !exact_type->interfaces()->contains(other_type->interfaces());
        }
      }

      if (other_type->base() == Type::InstPtr) {
        auto other_inst_type = other_type->is_instptr();
        return both_are_exact || !other_inst_type->instance_klass()->is_java_lang_Object() ||
               !AryOopType::_array_interfaces->contains(other_inst_type->interfaces());
      }

      auto exact_ary_type = exact_type->is_aryptr();
      auto other_ary_type = other_type->is_aryptr();
      if (both_are_exact) {
        if (exact_ary_type->is_flat() != other_ary_type->is_flat() || exact_ary_type->is_not_flat() != other_ary_type->is_not_flat() ||
            exact_ary_type->is_not_null_free() != other_ary_type->is_not_null_free() || exact_ary_type->is_atomic() != other_ary_type->is_atomic()) {
          return true;
        }
      } else {
        if (!exact_ary_type->is_atomic() && other_ary_type->is_atomic()) {
          return true;
        }
      }

      auto exact_elem = exact_ary_type->elem();
      auto other_elem = other_ary_type->elem();
      assert(exact_elem->base() != Type::Bottom, "cannot have an exact bot[]");
      assert(!both_are_exact || other_elem->base() != Type::Bottom, "cannot have an exact bot[]");
      if (other_elem->base() == Type::Bottom) {
        return false;
      }

      if (exact_elem->make_ptr() != nullptr && other_elem->make_ptr() != nullptr) {
        exact_type = exact_elem->make_ptr()->is_oopptr();
        other_type = other_elem->make_ptr()->is_oopptr();
        continue;
      }

      if (exact_elem->base() != other_elem->base()) {
        return true;
      } else if (exact_elem->base() == Type::Int) {
        return exact_ary_type->klass() != other_ary_type->klass();
      } else {
        return false;
      }
    }
  }
};

class TypeJavaPtrJoinHelper {
public:
  template <class PtrType>
  static const typename PtrType::PtrType* javaptr_type_xjoin(const PtrType* t1, const PtrType* t2) {
    if (t1 == t2) {
      return t1;
    }

    TypePtr::PTR ptr1 = t1->ptr();
    TypePtr::PTR ptr2 = t2->ptr();
    assert(ptr1 == TypePtr::Constant || ptr1 == TypePtr::NotNull || ptr1 == TypePtr::BotPTR, "unexpected ptr: %d", int(ptr1));
    assert(ptr2 == TypePtr::Constant || ptr2 == TypePtr::NotNull || ptr2 == TypePtr::BotPTR, "unexpected ptr: %d", int(ptr2));

    if constexpr (PtrType::is_oopptr_type) {
      return oopptr_type_xjoin(t1, t2);
    } else {
      return klassptr_type_xjoin(t1, t2);
    }
  }

private:
  template <class OopType>
  static const typename OopType::PtrType* oopptr_type_xjoin(const OopType* t1, const OopType* t2) {
    assert(t1 != t2, "must have been handled");
    Type::TYPES base1 = t1->base();
    Type::TYPES base2 = t2->base();
    assert(base1 == Type::InstPtr || base1 == Type::AryPtr, "must be an oopptr: %d", int(base1));
    assert(base2 == Type::InstPtr || base2 == Type::AryPtr, "must be an oopptr: %d", int(base2));

    Type::Offset offset = join_offset(t1, t2);
    auto interfaces = join_interfaces(t1, t2);
    auto flat_in_array = join_flat_in_array(t1, t2);
    int instance_id = join_instance_id(t1, t2);
    auto speculative = join_speculative(t1, t2);
    int inline_depth = join_inline_depth(t1, t2);
    if (offset == Type::Offset::top) {
      return OopType::PtrType::make(Type::AnyPtr, TypePtr::TopPTR, offset, speculative, inline_depth);
    } else if ((t1->ptr() == TypePtr::Constant || t2->ptr() == TypePtr::Constant) && instance_id != TypeOopPtr::InstanceBot) {
      // A constant oop cannot be produced by an allocation in the current compilation
      return OopType::PtrType::make(Type::AnyPtr, TypePtr::TopPTR, offset, speculative, inline_depth);
    } else if (flat_in_array == TypePtr::TopFlat || instance_id == TypeOopPtr::InstanceTop) {
      TypePtr::PTR ptr = join_ptr_with_null(join_ptr(t1, t2));
      return OopType::PtrType::make(Type::AnyPtr, ptr, offset, speculative, inline_depth);
    }

    if (base1 != base2) {
      const typename OopType::InstType* inst_type;
      const typename OopType::AryType* ary_type;
      if (base1 == Type::InstPtr) {
        inst_type = t1->is_instptr();
        ary_type = t2->is_aryptr();
      } else {
        inst_type = t2->is_instptr();
        ary_type = t1->is_aryptr();
      }

      TypePtr::PTR ptr = join_ptr(t1, t2);
      bool inst_type_can_contain_arrays = inst_type->instance_klass()->is_java_lang_Object() && !inst_type->klass_is_exact() &&
                                          OopType::AryType::_array_interfaces->contains(inst_type->interfaces());
      if (inst_type_can_contain_arrays) {
        return OopType::AryType::make(ptr, ary_type->const_oop(), ary_type->ary(), ary_type->klass(), ary_type->klass_is_exact(), offset, ary_type->field_offset(),
                                      instance_id, speculative, inline_depth, ary_type->is_autobox_cache());
      } else {
        return OopType::PtrType::make(Type::AnyPtr, join_ptr_with_null(ptr), offset, speculative, inline_depth);
      }
    } else if (base1 == Type::InstPtr) {
      return instptr_type_xjoin(t1->is_instptr(), t2->is_instptr(), offset, interfaces, flat_in_array, instance_id, speculative, inline_depth);
    } else {
      return aryptr_type_xjoin(t1->is_aryptr(), t2->is_aryptr(), offset, instance_id, speculative, inline_depth);
    }
  }

  template <class InstOopType, class PtrType, class InterfacesType>
  static const typename InstOopType::PtrType* instptr_type_xjoin(const InstOopType* t1, const InstOopType* t2, Type::Offset offset, InterfacesType interfaces,
                                                                 TypePtr::FlatInArray flat_in_array, int instance_id, const PtrType* speculative, int inline_depth) {
    // Join 2 constants
    if (t1->const_oop() != nullptr && t2->const_oop() != nullptr) {
      if (t1->const_oop() != t2->const_oop()) {
        return InstOopType::PtrType::make(Type::AnyPtr, TypePtr::TopPTR, offset, speculative, inline_depth);
      } else {
        return InstOopType::make(TypePtr::Constant, t1->instance_klass(), interfaces, true, t1->const_oop(), offset, flat_in_array, instance_id, speculative, inline_depth);
      }
    }

    // From here, at least one of the operand is not constant
    TypePtr::PTR ptr = join_ptr(t1, t2);
    auto const_oop = t1->const_oop() != nullptr ? t1->const_oop() : t2->const_oop();
    auto klass1 = t1->instance_klass();
    auto klass2 = t2->instance_klass();
    bool xk1 = t1->klass_is_exact();
    bool xk2 = t2->klass_is_exact();

    if (xk1 && xk2) {
      if (t1->instance_klass() == t2->instance_klass()) {
        return InstOopType::make(ptr, klass1, interfaces, true, const_oop, offset, flat_in_array, instance_id, speculative, inline_depth);
      } else {
        return InstOopType::PtrType::make(Type::AnyPtr, join_ptr_with_null(ptr), offset, speculative, inline_depth);
      }
    } else if (xk1) {
      assert(klass1->is_loaded(), "pointer to an oop of an exact type must be loaded");
      if (non_exact_type_contains_exact_type(t2, t1, interfaces)) {
        return InstOopType::make(ptr, klass1, interfaces, true, const_oop, offset, flat_in_array, instance_id, speculative, inline_depth);
      } else {
        return InstOopType::PtrType::make(Type::AnyPtr, join_ptr_with_null(ptr), offset, speculative, inline_depth);
      }
    } else if (xk2) {
      assert(klass2->is_loaded(), "pointer to an oop of an exact type must be loaded");
      if (non_exact_type_contains_exact_type(t1, t2, interfaces)) {
        return InstOopType::make(ptr, klass2, interfaces, true, const_oop, offset, flat_in_array, instance_id, speculative, inline_depth);
      } else {
        return InstOopType::PtrType::make(Type::AnyPtr, join_ptr_with_null(ptr), offset, speculative, inline_depth);
      }
    }

    assert(const_oop == nullptr && ptr != TypePtr::Constant, "const oop should have exact klass");
    if (!klass1->is_loaded() || !klass2->is_loaded()) {
      // Consider an unloaded class to be a direct child of j.l.O and not have any subclass
      if (klass1->is_java_lang_Object()) {
        return InstOopType::make(ptr, klass2, interfaces, false, nullptr, offset, flat_in_array, instance_id, speculative, inline_depth);
      } else if (klass2->is_java_lang_Object()) {
        return InstOopType::make(ptr, klass1, interfaces, false, nullptr, offset, flat_in_array, instance_id, speculative, inline_depth);
      } else if (klass1 == klass2) {
        return InstOopType::make(ptr, klass1, interfaces, false, nullptr, offset, flat_in_array, instance_id, speculative, inline_depth);
      } else {
        return InstOopType::PtrType::make(Type::AnyPtr, join_ptr_with_null(ptr), offset, speculative, inline_depth);
      }
    }

    // There is no opposite of LCA, there exists a non-null object o subtyping both A and B iff A
    // is a subtype of B or B is a subtype of A
    if (klass1->is_subtype_of(klass2)) {
      return InstOopType::make(ptr, klass1, interfaces, false, nullptr, offset, flat_in_array, instance_id, speculative, inline_depth);
    } else if (klass2->is_subtype_of(klass1)) {
      return InstOopType::make(ptr, klass2, interfaces, false, nullptr, offset, flat_in_array, instance_id, speculative, inline_depth);
    } else {
      return InstOopType::PtrType::make(Type::AnyPtr, join_ptr_with_null(ptr), offset, speculative, inline_depth);
    }
  }

  template <class AryOopType, class PtrType>
  static const typename AryOopType::PtrType* aryptr_type_xjoin(const AryOopType* t1, const AryOopType* t2, Type::Offset offset, int instance_id,
                                                               const PtrType* speculative, int inline_depth) {
    using AryType = std::remove_pointer_t<decltype(t1->ary())>;
    using ElemType = decltype(t1->elem());
    using KlassType = decltype(t1->klass());

    // Join of 2 different constants
    if (t1->const_oop() != nullptr && t2->const_oop() != nullptr && t1->const_oop() != t2->const_oop()) {
      return AryOopType::PtrType::make(Type::AnyPtr, TypePtr::TopPTR, offset, speculative, inline_depth);
    }

    TypePtr::PTR ptr = join_ptr(t1, t2);
    ElemType elem;
    KlassType klass = nullptr;
    join_ary_elem(elem, klass, t1, t2);
    if (elem->empty()) {
      return AryOopType::PtrType::make(Type::AnyPtr, join_ptr_with_null(ptr), offset, speculative, inline_depth);
    }

    if (TypeJavaPtrMeetHelper::aryptr_klass_disjoint(t1, t2)) {
      return AryOopType::PtrType::make(Type::AnyPtr, join_ptr_with_null(ptr), offset, speculative, inline_depth);
    }

    auto size = t1->size()->join(t2->size())->isa_int();
    if (size == nullptr) {
      return AryOopType::PtrType::make(Type::AnyPtr, join_ptr_with_null(ptr), offset, speculative, inline_depth);
    }

    auto const_oop = t1->const_oop() != nullptr ? t1->const_oop() : t2->const_oop();
    bool stable = t1->is_stable() || t2->is_stable();
    bool flat = t1->is_flat() || t2->is_flat();
    bool not_flat = t1->is_not_flat() || t2->is_not_flat();
    bool null_free = t1->is_null_free() || t2->is_null_free();
    bool not_null_free = t1->is_not_null_free() || t2->is_not_null_free();
    bool atomic = t1->is_atomic() || t2->is_atomic();
    auto field_offset = t1->field_offset().join(t2->field_offset());
    if ((flat && not_flat) || (null_free && not_null_free) || field_offset == Type::Offset::top) {
      return AryOopType::PtrType::make(Type::AnyPtr, join_ptr_with_null(ptr), offset, speculative, inline_depth);
    }

    auto ary = AryType::make(elem, size, stable, flat, not_flat, null_free, not_null_free, atomic);
    bool xk = t1->klass_is_exact() || t2->klass_is_exact();
    bool autobox_cache = t1->is_autobox_cache() || t2->is_autobox_cache();
    return AryOopType::make(ptr, const_oop, ary, klass, xk, offset, field_offset, instance_id, speculative, inline_depth, autobox_cache);
  }

  template <class KlassType>
  static const typename KlassType::PtrType* klassptr_type_xjoin(const KlassType* t1, const KlassType* t2) {
    assert(t1 != t2, "must have been handled");
    Type::TYPES base1 = t1->base();
    Type::TYPES base2 = t2->base();
    assert(base1 == Type::InstKlassPtr || base1 == Type::AryKlassPtr, "must be a klassptr: %d", int(base1));
    assert(base2 == Type::InstKlassPtr || base2 == Type::AryKlassPtr, "must be a klassptr: %d", int(base2));

    Type::Offset offset = join_offset(t1, t2);
    auto interfaces = join_interfaces(t1, t2);
    auto flat_in_array = join_flat_in_array(t1, t2);
    if (offset == Type::Offset::top) {
      return KlassType::PtrType::make(Type::AnyPtr, TypePtr::TopPTR, offset);
    } else if (flat_in_array == TypePtr::TopFlat) {
      TypePtr::PTR ptr = join_ptr_with_null(join_ptr(t1, t2));
      return KlassType::PtrType::make(Type::AnyPtr, ptr, offset);
    }

    if (base1 != base2) {
      const typename KlassType::InstType* inst_type;
      const typename KlassType::AryType* ary_type;
      if (base1 == Type::InstKlassPtr) {
        inst_type = t1->is_instklassptr();
        ary_type = t2->is_aryklassptr();
      } else {
        inst_type = t2->is_instklassptr();
        ary_type = t1->is_aryklassptr();
      }

      TypePtr::PTR ptr = join_ptr(t1, t2);
      bool inst_type_can_contain_arrays = inst_type->instance_klass()->is_java_lang_Object() && !inst_type->klass_is_exact() &&
                                          KlassType::AryType::_array_interfaces->contains(inst_type->interfaces());
      if (inst_type_can_contain_arrays) {
        return KlassType::AryType::make(ptr, ary_type->elem(), ary_type->klass(), offset, ary_type->is_not_flat(), ary_type->is_not_null_free(),
                                 ary_type->is_flat(), ary_type->is_null_free(), ary_type->is_atomic(), ary_type->is_refined_type());
      } else {
        return KlassType::PtrType::make(Type::AnyPtr, join_ptr_with_null(ptr), offset);
      }
    } else if (base1 == Type::InstKlassPtr) {
      return instklassptr_type_xjoin(t1->is_instklassptr(), t2->is_instklassptr(), offset, interfaces, flat_in_array);
    } else {
      return aryklassptr_type_xjoin(t1->is_aryklassptr(), t2->is_aryklassptr(), offset);
    }
  }

  template <class InstKlassType, class InterfacesType>
  static const typename InstKlassType::PtrType* instklassptr_type_xjoin(const InstKlassType* t1, const InstKlassType* t2, Type::Offset offset,
                                                                        InterfacesType interfaces, TypePtr::FlatInArray flat_in_array) {
    auto klass1 = t1->instance_klass();
    auto klass2 = t2->instance_klass();
    // Beware, when the exact klass pointer is an interface, instance_klass() is j.l.O and
    // interfaces() is not empty
    if (t1->ptr() == TypePtr::Constant && t2->ptr() == TypePtr::Constant) {
      if (klass1 == klass2 && t1->interfaces() == t2->interfaces()) {
        return InstKlassType::make(TypePtr::Constant, klass1, interfaces, offset, flat_in_array);
      } else {
        return InstKlassType::PtrType::make(Type::AnyPtr, TypePtr::TopPTR, offset);
      }
    } else if (t1->ptr() == TypePtr::Constant) {
      if (non_exact_type_contains_exact_type(t2, t1, interfaces)) {
        return InstKlassType::make(TypePtr::Constant, klass1, interfaces, offset, flat_in_array);
      } else {
        return InstKlassType::PtrType::make(Type::AnyPtr, TypePtr::TopPTR, offset);
      }
    } else if (t2->ptr() == TypePtr::Constant) {
      if (non_exact_type_contains_exact_type(t1, t2, interfaces)) {
        return InstKlassType::make(TypePtr::Constant, klass2, interfaces, offset, flat_in_array);
      } else {
        return InstKlassType::PtrType::make(Type::AnyPtr, TypePtr::TopPTR, offset);
      }
    } else {
      TypePtr::PTR ptr = join_ptr(t1, t2);
      if (klass1->is_subtype_of(klass2)) {
        return InstKlassType::make(ptr, klass1, interfaces, offset, flat_in_array);
      } else if (klass2->is_subtype_of(klass1)) {
        return InstKlassType::make(ptr, klass2, interfaces, offset, flat_in_array);
      } else {
        return InstKlassType::PtrType::make(Type::AnyPtr, join_ptr_with_null(ptr), offset);
      }
    }
  }

  template <class AryKlassType>
  static const typename AryKlassType::PtrType* aryklassptr_type_xjoin(const AryKlassType* t1, const AryKlassType* t2, Type::Offset offset) {
    using ElemType = decltype(t1->elem());
    using KlassType = decltype(t1->klass());

    TypePtr::PTR ptr = join_ptr(t1, t2);
    ElemType elem;
    KlassType klass = nullptr;
    join_ary_elem(elem, klass, t1, t2);
    bool not_flat = t1->is_not_flat() || t2->is_not_flat();
    bool not_null_free = t1->is_not_null_free() || t2->is_not_null_free();
    bool flat = t1->is_flat() || t2->is_flat();
    bool null_free = t1->is_null_free() || t2->is_null_free();
    if (elem->empty() || (flat && not_flat) || (null_free && not_null_free)) {
      return AryKlassType::PtrType::make(Type::AnyPtr, join_ptr_with_null(ptr), offset);
    }

    bool atomic = t1->is_atomic() || t2->is_atomic();
    bool refined = t1->is_refined_type() || t2->is_refined_type();
    if (t1->klass_is_exact() && (not_flat != t1->is_not_flat() || not_null_free != t1->is_not_null_free() || flat != t1->is_flat() ||
                                 null_free != t1->is_null_free() || atomic != t1->is_atomic() || refined != t1->is_refined_type())) {
      return AryKlassType::PtrType::make(Type::AnyPtr, join_ptr_with_null(ptr), offset);
    }
    if (t2->klass_is_exact() && (not_flat != t2->is_not_flat() || not_null_free != t2->is_not_null_free() || flat != t2->is_flat() ||
                                 null_free != t2->is_null_free() || atomic != t2->is_atomic() || refined != t2->is_refined_type())) {
      return AryKlassType::PtrType::make(Type::AnyPtr, join_ptr_with_null(ptr), offset);
    }

    return AryKlassType::make(ptr, elem, klass, offset, not_flat, not_null_free, flat, null_free, atomic, refined);
  }

  template <class PtrType>
  static TypePtr::PTR join_ptr(const PtrType* t1, const PtrType* t2) {
    TypePtr::PTR ptr1 = t1->ptr();
    TypePtr::PTR ptr2 = t2->ptr();
    if (ptr1 == TypePtr::Constant || ptr2 == TypePtr::Constant) {
      return TypePtr::Constant;
    } else if (ptr1 == TypePtr::NotNull || ptr2 == TypePtr::NotNull) {
      return TypePtr::NotNull;
    } else {
      return TypePtr::BotPTR;
    }
  }

  // When 2 TypePtrs seem unrelated, they may still join at null if both of them are
  // TypePtr::BotPTR.
  static TypePtr::PTR join_ptr_with_null(TypePtr::PTR ptr) {
    return ptr == TypePtr::BotPTR ? TypePtr::Null : TypePtr::TopPTR;
  }

  template <class PtrType>
  static Type::Offset join_offset(const PtrType* t1, const PtrType* t2) {
    return Type::Offset(t1->offset()).join(Type::Offset(t2->offset()));
  }

  template <class PtrType, class InterfacesType = std::invoke_result_t<decltype(&PtrType::interfaces), PtrType>>
  static InterfacesType join_interfaces(const PtrType* t1, const PtrType* t2) {
    return t1->interfaces()->union_with(t2->interfaces());
  }

  template <class OopType>
  static int join_instance_id(const OopType* t1, const OopType* t2) {
    int id1 = t1->instance_id();
    int id2 = t2->instance_id();
    assert(id1 != TypeOopPtr::InstanceTop && id2 != TypeOopPtr::InstanceTop, "InstanceTop must be normalized to TypePtr::TopPTR");
    if (id1 == TypeOopPtr::InstanceBot) {
      return id2;
    } else if (id2 == TypeOopPtr::InstanceBot) {
      return id1;
    } else if (id1 == id2) {
      return id1;
    } else {
      return TypeOopPtr::InstanceTop;
    }
  }

  template <class OopType>
  static auto join_speculative(const OopType* t1, const OopType* t2) {
    auto s1 = t1->speculative();
    auto s2 = t2->speculative();
    if (s1 == nullptr && s2 == nullptr) {
      return s1;
    }

    if (s1 == nullptr) {
      s1 = t1;
    } else if (s2 == nullptr) {
      s2 = t2;
    }
    return s1->join(s2)->is_ptr();
  }

  template <class OopType>
  static int join_inline_depth(const OopType* t1, const OopType* t2) {
    return MIN2(t1->inline_depth(), t2->inline_depth());
  }

  template <class PtrType>
  static TypePtr::FlatInArray join_flat_in_array(const PtrType* t1, const PtrType* t2) {
    auto v1 = t1->flat_in_array();
    auto v2 = t2->flat_in_array();
    assert(v1 != TypePtr::TopFlat && v2 != TypePtr::TopFlat, "TopFlat must be normalized to TopPTR");
    if (v1 == TypePtr::MaybeFlat) {
      return v2;
    } else if (v2 == TypePtr::MaybeFlat) {
      return v1;
    } else if (v1 == v2) {
      return v1;
    } else {
      return TypePtr::TopFlat;
    }
  }

  template <class AryType, class ElemType, class CIKlassType>
  static void join_ary_elem(const ElemType*& elem, CIKlassType& klass, const AryType* t1, const AryType* t2) {
    const ElemType* elem1 = t1->elem();
    const ElemType* elem2 = t2->elem();
    if (elem1 == ElemType::BOTTOM) {
      elem = elem2;
      klass = t2->klass();
      return;
    } else if (elem2 == ElemType::BOTTOM) {
      elem = elem1;
      klass = t1->klass();
      return;
    }

    if (elem1->make_ptr() != nullptr && elem2->make_ptr() != nullptr) {
      elem = elem1->join_speculative(elem2);
      if (!elem->empty() && elem->make_ptr()->ptr() == TypePtr::Null) {
        elem = ElemType::TOP;
      }
      return;
    }

    if (elem1->base() != elem2->base()) {
      elem = ElemType::TOP;
    } else if (elem1->base() == Type::Int) {
      // boolean[], byte[], short[], char[], int[] all use some kinds of TypeInt as their element
      // type, klass is used to distinguish between them. As a result, different kinds of array
      // should result in top.
      if (t1->klass() != t2->klass()) {
        elem = ElemType::TOP;
      } else {
        elem = elem1->join(elem2);
        klass = t1->klass();
      }
    } else {
      elem = elem1->join(elem2);
    }
  }

  template <class InstType, class InterfacesType>
  static bool non_exact_type_contains_exact_type(const InstType* non_exact_type, const InstType* exact_type, InterfacesType interfaces) {
    assert(!non_exact_type->klass_is_exact() && exact_type->klass_is_exact(), "invalid arguments");
    auto non_exact_klass = non_exact_type->instance_klass();
    auto exact_klass = exact_type->instance_klass();
    // Supertypes of a loaded class should also be loaded
    return (exact_klass->is_java_lang_Object() && non_exact_klass->is_java_lang_Object() && exact_type->interfaces() == interfaces) ||
           (non_exact_klass->is_loaded() && exact_klass->is_subtype_of(non_exact_klass) && interfaces->eq(exact_klass));
  }
};

#endif // SHARE_OPTO_TYPEJAVAPTR_HPP

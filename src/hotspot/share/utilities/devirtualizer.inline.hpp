/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_DEVIRTUALIZER_INLINE_HPP
#define SHARE_UTILITIES_DEVIRTUALIZER_INLINE_HPP

#include "utilities/devirtualizer.hpp"

#include "classfile/classLoaderData.hpp"
#include "oops/compressedKlass.inline.hpp"
#include "oops/access.inline.hpp"
#include "utilities/debug.hpp"

#include <type_traits>

// Implementation of the non-virtual do_oop dispatch.
//
// The same implementation is used for do_metadata, do_klass, and do_cld.
//
// Preconditions:
//  - Base has a pure virtual do_oop
//  - Only one of the classes in the inheritance chain from OopClosureType to
//    Base implements do_oop.
//
// Given the preconditions:
//  - If &OopClosureType::do_oop is resolved to &Base::do_oop, then there is no
//    implementation of do_oop between Base and OopClosureType. However, there
//    must be one implementation in one of the subclasses of OopClosureType.
//    In this case we take the virtual call.
//
//  - Conversely, if &OopClosureType::do_oop is not resolved to &Base::do_oop,
//    then we've found the one and only concrete implementation. In this case we
//    take a non-virtual call.
//
// Because of this it's clear when we should call the virtual call and
//   when the non-virtual call should be made.
//
// The way we find if &OopClosureType::do_oop is resolved to &Base::do_oop is to
//   check if the resulting type of the class of a member-function pointer to
//   &OopClosureType::do_oop is equal to the type of the class of a
//   &Base::do_oop member-function pointer. Template parameter deduction is used
//   to find these types, and then the IsSame trait is used to check if they are
//   equal. Finally, SFINAE is used to select the appropriate implementation.
//
// Template parameters:
//   T              - narrowOop or oop
//   Receiver       - the resolved type of the class of the
//                    &OopClosureType::do_oop member-function pointer. That is,
//                    the klass with the do_oop member function.
//   Base           - klass with the pure virtual do_oop member function.
//   OopClosureType - The dynamic closure type
//
// Parameters:
//   closure - The closure to call
//   p       - The oop (or narrowOop) field to pass to the closure

template <typename T, typename Receiver, typename Base, typename OopClosureType>
static typename EnableIf<std::is_same<Receiver, Base>::value, void>::type
call_do_oop(void (Receiver::*)(T*), void (Base::*)(T*), OopClosureType* closure, T* p) {
  closure->do_oop(p);
}

template <typename T, typename Receiver, typename Base, typename OopClosureType>
static typename EnableIf<!std::is_same<Receiver, Base>::value, void>::type
call_do_oop(void (Receiver::*)(T*), void (Base::*)(T*), OopClosureType* closure, T* p) {
  // Sanity check
  STATIC_ASSERT((!std::is_same<OopClosureType, OopIterateClosure>::value));
  closure->OopClosureType::do_oop(p);
}

template <typename OopClosureType, typename T>
inline void Devirtualizer::do_oop(OopClosureType* closure, T* p) {
  call_do_oop<T>(&OopClosureType::do_oop, &OopClosure::do_oop, closure, p);
}

// Implementation of the non-virtual do_metadata dispatch.

template <typename Receiver, typename Base, typename OopClosureType>
static typename EnableIf<std::is_same<Receiver, Base>::value, bool>::type
call_do_metadata(bool (Receiver::*)(), bool (Base::*)(), OopClosureType* closure) {
  return closure->do_metadata();
}

template <typename Receiver, typename Base, typename OopClosureType>
static typename EnableIf<!std::is_same<Receiver, Base>::value, bool>::type
call_do_metadata(bool (Receiver::*)(), bool (Base::*)(), OopClosureType* closure) {
  return closure->OopClosureType::do_metadata();
}

template <typename OopClosureType>
inline bool Devirtualizer::do_metadata(OopClosureType* closure) {
  return call_do_metadata(&OopClosureType::do_metadata, &OopIterateClosure::do_metadata, closure);
}

// Implementation of the non-virtual do_klass dispatch.

template <typename Receiver, typename Base, typename OopClosureType>
static typename EnableIf<std::is_same<Receiver, Base>::value, void>::type
call_do_klass(void (Receiver::*)(Klass*), void (Base::*)(Klass*), OopClosureType* closure, Klass* k) {
  closure->do_klass(k);
}

template <typename Receiver, typename Base, typename OopClosureType>
static typename EnableIf<!std::is_same<Receiver, Base>::value, void>::type
call_do_klass(void (Receiver::*)(Klass*), void (Base::*)(Klass*), OopClosureType* closure, Klass* k) {
  closure->OopClosureType::do_klass(k);
}

template <typename OopClosureType>
inline void Devirtualizer::do_klass(OopClosureType* closure, Klass* k) {
  call_do_klass(&OopClosureType::do_klass, &OopIterateClosure::do_klass, closure, k);
}

// Implementation of the non-virtual do_cld dispatch.

template <typename Receiver, typename Base, typename OopClosureType>
static typename EnableIf<std::is_same<Receiver, Base>::value, void>::type
call_do_cld(void (Receiver::*)(ClassLoaderData*), void (Base::*)(ClassLoaderData*), OopClosureType* closure, ClassLoaderData* cld) {
  closure->do_cld(cld);
}

template <typename Receiver, typename Base, typename OopClosureType>
static typename EnableIf<!std::is_same<Receiver, Base>::value, void>::type
call_do_cld(void (Receiver::*)(ClassLoaderData*), void (Base::*)(ClassLoaderData*), OopClosureType* closure, ClassLoaderData* cld) {
  closure->OopClosureType::do_cld(cld);
}

template <typename OopClosureType>
void Devirtualizer::do_cld(OopClosureType* closure, ClassLoaderData* cld) {
  call_do_cld(&OopClosureType::do_cld, &OopIterateClosure::do_cld, closure, cld);
}

// Implementation of the non-virtual do_derived_oop dispatch.

template <typename Receiver, typename Base, typename DerivedOopClosureType>
static typename EnableIf<std::is_same<Receiver, Base>::value, void>::type
call_do_derived_oop(void (Receiver::*)(derived_base*, derived_pointer*), void (Base::*)(derived_base*, derived_pointer*), DerivedOopClosureType* closure, derived_base* base, derived_pointer* derived) {
  closure->do_derived_oop(base, derived);
}

template <typename Receiver, typename Base, typename DerivedOopClosureType>
static typename EnableIf<!std::is_same<Receiver, Base>::value, void>::type
call_do_derived_oop(void (Receiver::*)(derived_base*, derived_pointer*), void (Base::*)(derived_base*, derived_pointer*), DerivedOopClosureType* closure, derived_base* base, derived_pointer* derived) {
  closure->DerivedOopClosureType::do_derived_oop(base, derived);
}

template <typename DerivedOopClosureType>
inline void Devirtualizer::do_derived_oop(DerivedOopClosureType* closure, derived_base* base, derived_pointer* derived) {
  call_do_derived_oop(&DerivedOopClosureType::do_derived_oop, &DerivedOopClosure::do_derived_oop, closure, base, derived);
}

#endif // SHARE_UTILITIES_DEVIRTUALIZER_INLINE_HPP


/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OOPS_OOPSHIERARCHY_HPP
#define SHARE_OOPS_OOPSHIERARCHY_HPP

#include "cppstdlib/type_traits.hpp"
#include "metaprogramming/primitiveConversions.hpp"
#include "utilities/globalDefinitions.hpp"

// OBJECT hierarchy
// This hierarchy is a representation hierarchy, i.e. if A is a superclass
// of B, A's representation is a prefix of B's representation.

// Global offset instead of address for an oop within a java object.
enum class narrowOop : uint32_t { null = 0 };

typedef void* OopOrNarrowOopStar;

#ifndef CHECK_UNHANDLED_OOPS

typedef class oopDesc*                    oop;
typedef class   instanceOopDesc*            instanceOop;
typedef class     inlineOopDesc*              inlineOop;
typedef class     stackChunkOopDesc*          stackChunkOop;
typedef class   arrayOopDesc*               arrayOop;
typedef class     objArrayOopDesc*            objArrayOop;
typedef class       flatArrayOopDesc*           flatArrayOop;
typedef class       refArrayOopDesc*            refArrayOop;
typedef class     typeArrayOopDesc*           typeArrayOop;

#else

// When CHECK_UNHANDLED_OOPS is defined, an "oop" is a class with a
// carefully chosen set of constructors and conversion operators to go
// to and from the underlying oopDesc pointer type.
//
// Because oop and its subclasses <type>Oop are class types, arbitrary
// conversions are not accepted by the compiler.  Applying a cast to
// an oop will cause the best matched conversion operator to be
// invoked returning the underlying oopDesc* type if appropriate.
// No copy constructors, explicit user conversions or operators of
// numerical type should be defined within the oop class. Most C++
// compilers will issue a compile time error concerning the overloading
// ambiguity between operators of numerical and pointer types. If
// a conversion to or from an oop to a numerical type is needed,
// use the inline template methods, cast_*_oop, defined below.
//
// Converting null to oop to Handle implicit is no longer accepted by the
// compiler because there are too many steps in the conversion.  Use Handle()
// instead, which generates less code anyway.

class Thread;
class oopDesc;

extern "C" bool CheckUnhandledOops;

// Extra verification when creating and using oops.
// Used to catch broken oops as soon as possible.
using CheckOopFunctionPointer = void(*)(oopDesc*);
extern CheckOopFunctionPointer check_oop_function;

class oop {
public:
  using DescType = oopDesc;

private:
  oopDesc* _o;

  void register_oop();
  void unregister_oop();

  // Extra verification of the oop
  void check_oop() const { if (check_oop_function != nullptr && _o != nullptr) check_oop_function(_o); }

  void on_usage() const  { check_oop(); }
  void on_construction() { check_oop(); if (CheckUnhandledOops)   register_oop(); }
  void on_destruction()  {              if (CheckUnhandledOops) unregister_oop(); }

public:
  oop()             : _o(nullptr) { on_construction(); }
  oop(const oop& o) : _o(o._o)    { on_construction(); }
  oop(oopDesc* o)   : _o(o)       { on_construction(); }
  ~oop() {
    on_destruction();
  }

  oopDesc* obj() const                  { on_usage(); return _o; }

  oopDesc* operator->() const           { return obj(); }
  operator oopDesc* () const            { return obj(); }

  bool operator==(const oop& o) const   { return obj() == o.obj(); }
  bool operator!=(const oop& o) const   { return obj() != o.obj(); }

  bool operator==(std::nullptr_t) const { return obj() == nullptr; }
  bool operator!=(std::nullptr_t) const { return obj() != nullptr; }

  oop& operator=(const oop& o)          { _o = o.obj(); return *this; }
};

template<>
struct PrimitiveConversions::Translate<oop> : public std::true_type {
  typedef oop Value;
  typedef oopDesc* Decayed;

  static Decayed decay(Value x) { return x.obj(); }
  static Value recover(Decayed x) { return oop(x); }
};

#define DEF_OOP_IMPL(OopType, OopDescType, BaseOopType)                        \
  class OopDescType;                                                           \
  class OopType : public BaseOopType {                                         \
  public:                                                                      \
    using DescType = OopDescType;                                              \
    OopType() : BaseOopType() {}                                               \
    OopType(std::nullptr_t) : BaseOopType() {}                                 \
    OopType(const OopType& o) : BaseOopType(o) {}                              \
    explicit OopType(const oop& o) : BaseOopType(o) {}                         \
    OopType(DescType* o) : BaseOopType((BaseOopType::DescType*)o) {}           \
    operator DescType*() const { return (DescType*)obj(); }                    \
    DescType* operator->() const { return (DescType*)obj(); }                  \
    OopType& operator=(std::nullptr_t) {                                       \
      BaseOopType::operator=(nullptr);                                         \
      return *this;                                                            \
    }                                                                          \
    OopType& operator=(const OopType& o) {                                     \
      BaseOopType::operator=(o);                                               \
      return *this;                                                            \
    }                                                                          \
    OopType& operator=(const oop& o) = delete;                                 \
  };                                                                           \
                                                                               \
  template <>                                                                  \
  struct PrimitiveConversions::Translate<OopType> : public std::true_type {    \
    typedef OopType Value;                                                     \
    typedef OopType::DescType* Decayed;                                        \
                                                                               \
    static Decayed decay(Value x) { return (OopType::DescType*)x.obj(); }      \
    static Value recover(Decayed x) { return OopType(x); }                     \
  };

#define DEF_OOP_BASE(type, base)                                               \
  DEF_OOP_IMPL(type##Oop, type##OopDesc, base##Oop)
#define DEF_OOP(type) DEF_OOP_IMPL(type##Oop, type##OopDesc, oop)

DEF_OOP(instance);
DEF_OOP_BASE(inline, instance);
DEF_OOP_BASE(stackChunk, instance);
DEF_OOP(array);
DEF_OOP_BASE(objArray, array);
DEF_OOP_BASE(typeArray, array);
DEF_OOP_BASE(flatArray, objArray);
DEF_OOP_BASE(refArray, objArray);

#undef DEF_OOP_IMPL
#undef DEF_OOP_BASE
#undef DEF_OOP

#endif // CHECK_UNHANDLED_OOPS

// Cast functions to convert to and from oops.
template <typename T> inline oop cast_to_oop(T value) {
  return (oopDesc*)value;
}
template <typename T> inline T cast_from_oop(oop o) {
  return (T)(CHECK_UNHANDLED_OOPS_ONLY((oopDesc*))o);
}

inline intptr_t p2i(narrowOop o) {
  return static_cast<intptr_t>(o);
}

// The metadata hierarchy is separate from the oop hierarchy

//      class MetaspaceObj
class   ConstMethod;
class   ConstantPoolCache;
class   MethodData;
//      class Metadata
class   Method;
class   ConstantPool;

// The klass hierarchy is separate from the oop hierarchy.

class Klass;
class   InstanceKlass;
class     InlineKlass;
class     InstanceMirrorKlass;
class     InstanceClassLoaderKlass;
class     InstanceRefKlass;
class     InstanceStackChunkKlass;
class   ArrayKlass;
class     ObjArrayKlass;
class       FlatArrayKlass;
class       RefArrayKlass;
class     TypeArrayKlass;

#endif // SHARE_OOPS_OOPSHIERARCHY_HPP

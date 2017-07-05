/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_OOPS_OOPSHIERARCHY_HPP
#define SHARE_VM_OOPS_OOPSHIERARCHY_HPP

#include "runtime/globals.hpp"
#include "utilities/globalDefinitions.hpp"

// OBJECT hierarchy
// This hierarchy is a representation hierarchy, i.e. if A is a superclass
// of B, A's representation is a prefix of B's representation.

typedef juint narrowOop; // Offset instead of address for an oop within a java object

// If compressed klass pointers then use narrowKlass.
typedef juint  narrowKlass;

typedef void* OopOrNarrowOopStar;
typedef class   markOopDesc*                markOop;

#ifndef CHECK_UNHANDLED_OOPS

typedef class oopDesc*                            oop;
typedef class   instanceOopDesc*            instanceOop;
typedef class   arrayOopDesc*                    arrayOop;
typedef class     objArrayOopDesc*            objArrayOop;
typedef class     typeArrayOopDesc*            typeArrayOop;

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
// Converting NULL to oop to Handle implicit is no longer accepted by the
// compiler because there are too many steps in the conversion.  Use Handle()
// instead, which generates less code anyway.

class Thread;
class PromotedObject;


class oop {
  oopDesc* _o;

  void register_oop();
  void unregister_oop();

  // friend class markOop;
public:
  void set_obj(const void* p)         {
    raw_set_obj(p);
    if (CheckUnhandledOops) register_oop();
  }
  void raw_set_obj(const void* p)     { _o = (oopDesc*)p; }

  oop()                               { set_obj(NULL); }
  oop(const oop& o)                   { set_obj(o.obj()); }
  oop(const volatile oop& o)          { set_obj(o.obj()); }
  oop(const void* p)                  { set_obj(p); }
  ~oop()                              {
    if (CheckUnhandledOops) unregister_oop();
  }

  oopDesc* obj()  const volatile      { return _o; }

  // General access
  oopDesc*  operator->() const        { return obj(); }
  bool operator==(const oop o) const  { return obj() == o.obj(); }
  bool operator==(void *p) const      { return obj() == p; }
  bool operator!=(const volatile oop o) const  { return obj() != o.obj(); }
  bool operator!=(void *p) const      { return obj() != p; }

  bool operator<(oop o) const         { return obj() < o.obj(); }
  bool operator>(oop o) const         { return obj() > o.obj(); }
  bool operator<=(oop o) const        { return obj() <= o.obj(); }
  bool operator>=(oop o) const        { return obj() >= o.obj(); }
  bool operator!() const              { return !obj(); }

  // Assignment
  oop& operator=(const oop& o)                            { _o = o.obj(); return *this; }
  volatile oop& operator=(const oop& o) volatile          { _o = o.obj(); return *this; }
  volatile oop& operator=(const volatile oop& o) volatile { _o = o.obj(); return *this; }

  // Explict user conversions
  operator void* () const             { return (void *)obj(); }
#ifndef SOLARIS
  operator void* () const volatile    { return (void *)obj(); }
#endif
  operator HeapWord* () const         { return (HeapWord*)obj(); }
  operator oopDesc* () const volatile { return obj(); }
  operator intptr_t* () const         { return (intptr_t*)obj(); }
  operator PromotedObject* () const   { return (PromotedObject*)obj(); }
  operator markOop () const           { return markOop(obj()); }
  operator address   () const         { return (address)obj(); }

  // from javaCalls.cpp
  operator jobject () const           { return (jobject)obj(); }
  // from javaClasses.cpp
  operator JavaThread* () const       { return (JavaThread*)obj(); }

#ifndef _LP64
  // from jvm.cpp
  operator jlong* () const            { return (jlong*)obj(); }
#endif

  // from parNewGeneration and other things that want to get to the end of
  // an oop for stuff (like ObjArrayKlass.cpp)
  operator oop* () const              { return (oop *)obj(); }
};

#define DEF_OOP(type)                                                      \
   class type##OopDesc;                                                    \
   class type##Oop : public oop {                                          \
     public:                                                               \
       type##Oop() : oop() {}                                              \
       type##Oop(const oop& o) : oop(o) {}                                 \
       type##Oop(const volatile oop& o) : oop(o) {}                        \
       type##Oop(const void* p) : oop(p) {}                                \
       operator type##OopDesc* () const { return (type##OopDesc*)obj(); }  \
       type##OopDesc* operator->() const {                                 \
            return (type##OopDesc*)obj();                                  \
       }                                                                   \
       type##Oop& operator=(const type##Oop& o) {                          \
            oop::operator=(o);                                             \
            return *this;                                                  \
       }                                                                   \
       volatile type##Oop& operator=(const type##Oop& o) volatile {        \
            (void)const_cast<oop&>(oop::operator=(o));                     \
            return *this;                                                  \
       }                                                                   \
       volatile type##Oop& operator=(const volatile type##Oop& o) volatile {\
            (void)const_cast<oop&>(oop::operator=(o));                     \
            return *this;                                                  \
       }                                                                   \
   };

DEF_OOP(instance);
DEF_OOP(array);
DEF_OOP(objArray);
DEF_OOP(typeArray);

#endif // CHECK_UNHANDLED_OOPS

// For CHECK_UNHANDLED_OOPS, it is ambiguous C++ behavior to have the oop
// structure contain explicit user defined conversions of both numerical
// and pointer type. Define inline methods to provide the numerical conversions.
template <class T> inline oop cast_to_oop(T value) {
  return (oop)(CHECK_UNHANDLED_OOPS_ONLY((void *))(value));
}
template <class T> inline T cast_from_oop(oop o) {
  return (T)(CHECK_UNHANDLED_OOPS_ONLY((void*))o);
}

// The metadata hierarchy is separate from the oop hierarchy

//      class MetaspaceObj
class   ConstMethod;
class   ConstantPoolCache;
class   MethodData;
//      class Metadata
class   Method;
class   ConstantPool;
//      class CHeapObj
class   CompiledICHolder;


// The klass hierarchy is separate from the oop hierarchy.

class Klass;
class   InstanceKlass;
class     InstanceMirrorKlass;
class     InstanceClassLoaderKlass;
class     InstanceRefKlass;
class   ArrayKlass;
class     ObjArrayKlass;
class     TypeArrayKlass;

#endif // SHARE_VM_OOPS_OOPSHIERARCHY_HPP

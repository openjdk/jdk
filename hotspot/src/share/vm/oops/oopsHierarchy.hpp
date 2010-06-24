/*
 * Copyright (c) 1997, 2009, Oracle and/or its affiliates. All rights reserved.
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

// OBJECT hierarchy
// This hierarchy is a representation hierarchy, i.e. if A is a superclass
// of B, A's representation is a prefix of B's representation.

typedef juint narrowOop; // Offset instead of address for an oop within a java object
typedef class klassOopDesc* wideKlassOop; // to keep SA happy and unhandled oop
                                          // detector happy.
typedef void* OopOrNarrowOopStar;

#ifndef CHECK_UNHANDLED_OOPS

typedef class oopDesc*                            oop;
typedef class   instanceOopDesc*            instanceOop;
typedef class   methodOopDesc*                    methodOop;
typedef class   constMethodOopDesc*            constMethodOop;
typedef class   methodDataOopDesc*            methodDataOop;
typedef class   arrayOopDesc*                    arrayOop;
typedef class     objArrayOopDesc*            objArrayOop;
typedef class     typeArrayOopDesc*            typeArrayOop;
typedef class   constantPoolOopDesc*            constantPoolOop;
typedef class   constantPoolCacheOopDesc*   constantPoolCacheOop;
typedef class   symbolOopDesc*                    symbolOop;
typedef class   klassOopDesc*                    klassOop;
typedef class   markOopDesc*                    markOop;
typedef class   compiledICHolderOopDesc*    compiledICHolderOop;

#else


// When CHECK_UNHANDLED_OOPS is defined, an "oop" is a class with a
// carefully chosen set of constructors and conversion operators to go
// to and from the underlying oopDesc pointer type.
//
// Because oop and its subclasses <type>Oop are class types, arbitrary
// conversions are not accepted by the compiler, and you may get a message
// about overloading ambiguity (between long and int is common when converting
// from a constant in 64 bit mode), or unable to convert from type to 'oop'.
// Applying a cast to one of these conversion operators first will get to the
// underlying oopDesc* type if appropriate.
// Converting NULL to oop to Handle implicit is no longer accepted by the
// compiler because there are too many steps in the conversion.  Use Handle()
// instead, which generates less code anyway.

class Thread;
typedef class   markOopDesc*                markOop;
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
  oop(const volatile oop& o)          { set_obj(o.obj()); }
  oop(const void* p)                  { set_obj(p); }
  oop(intptr_t i)                     { set_obj((void *)i); }
#ifdef _LP64
  oop(int i)                          { set_obj((void *)i); }
#endif
  ~oop()                              {
    if (CheckUnhandledOops) unregister_oop();
  }

  oopDesc* obj()  const volatile      { return _o; }

  // General access
  oopDesc*  operator->() const        { return obj(); }
  bool operator==(const oop o) const  { return obj() == o.obj(); }
  bool operator==(void *p) const      { return obj() == p; }
  bool operator!=(const oop o) const  { return obj() != o.obj(); }
  bool operator!=(void *p) const      { return obj() != p; }
  bool operator==(intptr_t p) const   { return obj() == (oopDesc*)p; }
  bool operator!=(intptr_t p) const   { return obj() != (oopDesc*)p; }

  bool operator<(oop o) const         { return obj() < o.obj(); }
  bool operator>(oop o) const         { return obj() > o.obj(); }
  bool operator<=(oop o) const        { return obj() <= o.obj(); }
  bool operator>=(oop o) const        { return obj() >= o.obj(); }
  bool operator!() const              { return !obj(); }

  // Cast
  operator void* () const             { return (void *)obj(); }
  operator HeapWord* () const         { return (HeapWord*)obj(); }
  operator oopDesc* () const          { return obj(); }
  operator intptr_t* () const         { return (intptr_t*)obj(); }
  operator PromotedObject* () const   { return (PromotedObject*)obj(); }
  operator markOop () const           { return markOop(obj()); }

  operator address   () const         { return (address)obj(); }
  operator intptr_t () const          { return (intptr_t)obj(); }

  // from javaCalls.cpp
  operator jobject () const           { return (jobject)obj(); }
  // from javaClasses.cpp
  operator JavaThread* () const       { return (JavaThread*)obj(); }

#ifndef _LP64
  // from jvm.cpp
  operator jlong* () const            { return (jlong*)obj(); }
#endif

  // from parNewGeneration and other things that want to get to the end of
  // an oop for stuff (like constMethodKlass.cpp, objArrayKlass.cpp)
  operator oop* () const              { return (oop *)obj(); }
};

#define DEF_OOP(type)                                                      \
   class type##OopDesc;                                                    \
   class type##Oop : public oop {                                          \
     public:                                                               \
       type##Oop() : oop() {}                                              \
       type##Oop(const volatile oop& o) : oop(o) {}                        \
       type##Oop(const void* p) : oop(p) {}                                \
       operator type##OopDesc* () const { return (type##OopDesc*)obj(); }  \
       type##OopDesc* operator->() const {                                 \
            return (type##OopDesc*)obj();                                  \
       }                                                                   \
   };                                                                      \

DEF_OOP(instance);
DEF_OOP(method);
DEF_OOP(methodData);
DEF_OOP(array);
DEF_OOP(constMethod);
DEF_OOP(constantPool);
DEF_OOP(constantPoolCache);
DEF_OOP(objArray);
DEF_OOP(typeArray);
DEF_OOP(symbol);
DEF_OOP(klass);
DEF_OOP(compiledICHolder);

#endif // CHECK_UNHANDLED_OOPS

// The klass hierarchy is separate from the oop hierarchy.

class Klass;
class   instanceKlass;
class     instanceRefKlass;
class   methodKlass;
class   constMethodKlass;
class   methodDataKlass;
class   klassKlass;
class     instanceKlassKlass;
class     arrayKlassKlass;
class       objArrayKlassKlass;
class       typeArrayKlassKlass;
class   arrayKlass;
class     objArrayKlass;
class     typeArrayKlass;
class   constantPoolKlass;
class   constantPoolCacheKlass;
class   symbolKlass;
class   compiledICHolderKlass;

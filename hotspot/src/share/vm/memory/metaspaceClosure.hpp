/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_MEMORY_METASPACE_ITERATOR_HPP
#define SHARE_VM_MEMORY_METASPACE_ITERATOR_HPP

#include "logging/log.hpp"
#include "memory/allocation.hpp"
#include "oops/array.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/resourceHash.hpp"

// The metadata hierarchy is separate from the oop hierarchy
  class MetaspaceObj;        // no C++ vtable
//class   Array;             // no C++ vtable
  class   Annotations;       // no C++ vtable
  class   ConstantPoolCache; // no C++ vtable
  class   ConstMethod;       // no C++ vtable
  class   MethodCounters;    // no C++ vtable
  class   Symbol;            // no C++ vtable
  class   Metadata;          // has C++ vtable (so do all subclasses)
  class     ConstantPool;
  class     MethodData;
  class     Method;
  class     Klass;
  class       InstanceKlass;
  class         InstanceMirrorKlass;
  class         InstanceClassLoaderKlass;
  class         InstanceRefKlass;
  class       ArrayKlass;
  class         ObjArrayKlass;
  class         TypeArrayKlass;

// class MetaspaceClosure --
//
// This class is used for iterating the objects in the HotSpot Metaspaces. It
// provides an API to walk all the reachable objects starting from a set of
// root references (such as all Klass'es in the SystemDictionary).
//
// Currently it is used for compacting the CDS archive by eliminate temporary
// objects allocated during archive creation time. See ArchiveCompactor in
// metaspaceShared.cpp for an example.
//
// To support MetaspaceClosure, each subclass of MetaspaceObj must provide
// a method of the type void metaspace_pointers_do(MetaspaceClosure*). This method
// should call MetaspaceClosure::push() on every pointer fields of this
// class that points to a MetaspaceObj. See Annotations::metaspace_pointers_do()
// for an example.
class MetaspaceClosure {
public:
  enum Writability {
    _writable,
    _not_writable,
    _default
  };

  // class MetaspaceClosure::Ref --
  //
  // MetaspaceClosure can be viewed as a very simple type of copying garbage
  // collector. For it to function properly, it requires each subclass of
  // MetaspaceObj to provide two methods:
  //
  //  size_t size();                                 -- to determine how much data to copy
  //  void metaspace_pointers_do(MetaspaceClosure*); -- to locate all the embedded pointers
  //
  // Calling these methods would be trivial if these two were virtual methods.
  // However, to save space, MetaspaceObj has NO vtable. The vtable is introduced
  // only in the Metadata class.
  //
  // To work around the lack of a vtable, we use Ref class with templates
  // (see ObjectRef, PrimitiveArrayRef and PointerArrayRef)
  // so that we can statically discover the type of a object. The use of Ref
  // depends on the fact that:
  //
  // [1] We don't use polymorphic pointers for MetaspaceObj's that are not subclasses
  //     of Metadata. I.e., we don't do this:
  //     class Klass {
  //         MetaspaceObj *_obj;
  //         Array<int>* foo() { return (Array<int>*)_obj; }
  //         Symbol*     bar() { return (Symbol*)    _obj; }
  //
  // [2] All Array<T> dimensions are statically declared.
  class Ref {
  protected:
    virtual void** mpp() const = 0;
  public:
    virtual bool not_null() const = 0;
    virtual int size() const = 0;
    virtual void metaspace_pointers_do(MetaspaceClosure *it) const = 0;
    virtual void metaspace_pointers_do_at(MetaspaceClosure *it, address new_loc) const = 0;
    virtual MetaspaceObj::Type msotype() const = 0;
    virtual bool is_read_only_by_default() const = 0;

    address obj() const {
      // In some rare cases (see CPSlot in constantPool.hpp) we store some flags in the lowest
      // 2 bits of a MetaspaceObj pointer. Unmask these when manipulating the pointer.
      uintx p = (uintx)*mpp();
      return (address)(p & (~FLAG_MASK));
    }

    void update(address new_loc) const;

  private:
    static const uintx FLAG_MASK = 0x03;

    int flag_bits() const {
      uintx p = (uintx)*mpp();
      return (int)(p & FLAG_MASK);
    }
  };

private:
  // -------------------------------------------------- ObjectRef
  template <class T> class ObjectRef : public Ref {
    T** _mpp;
    T* dereference() const {
      return *_mpp;
    }
  protected:
    virtual void** mpp() const {
      return (void**)_mpp;
    }

  public:
    ObjectRef(T** mpp) : _mpp(mpp) {}

    virtual bool is_read_only_by_default() const { return T::is_read_only_by_default(); }
    virtual bool not_null()                const { return dereference() != NULL; }
    virtual int size()                     const { return dereference()->size(); }
    virtual MetaspaceObj::Type msotype()   const { return dereference()->type(); }

    virtual void metaspace_pointers_do(MetaspaceClosure *it) const {
      dereference()->metaspace_pointers_do(it);
    }
    virtual void metaspace_pointers_do_at(MetaspaceClosure *it, address new_loc) const {
      ((T*)new_loc)->metaspace_pointers_do(it);
    }
  };

  // -------------------------------------------------- PrimitiveArrayRef
  template <class T> class PrimitiveArrayRef : public Ref {
    Array<T>** _mpp;
    Array<T>* dereference() const {
      return *_mpp;
    }
  protected:
    virtual void** mpp() const {
      return (void**)_mpp;
    }

  public:
    PrimitiveArrayRef(Array<T>** mpp) : _mpp(mpp) {}

    // all Arrays are read-only by default
    virtual bool is_read_only_by_default() const { return true; }
    virtual bool not_null()                const { return dereference() != NULL;  }
    virtual int size()                     const { return dereference()->size(); }
    virtual MetaspaceObj::Type msotype()   const { return MetaspaceObj::array_type(sizeof(T)); }

    virtual void metaspace_pointers_do(MetaspaceClosure *it) const {
      Array<T>* array = dereference();
      log_trace(cds)("Iter(PrimitiveArray): %p [%d]", array, array->length());
    }
    virtual void metaspace_pointers_do_at(MetaspaceClosure *it, address new_loc) const {
      Array<T>* array = (Array<T>*)new_loc;
      log_trace(cds)("Iter(PrimitiveArray): %p [%d]", array, array->length());
    }
  };

  // -------------------------------------------------- PointerArrayRef
  template <class T> class PointerArrayRef : public Ref {
    Array<T*>** _mpp;
    Array<T*>* dereference() const {
      return *_mpp;
    }
  protected:
    virtual void** mpp() const {
      return (void**)_mpp;
    }

  public:
    PointerArrayRef(Array<T*>** mpp) : _mpp(mpp) {}

    // all Arrays are read-only by default
    virtual bool is_read_only_by_default() const { return true; }
    virtual bool not_null()                const { return dereference() != NULL; }
    virtual int size()                     const { return dereference()->size(); }
    virtual MetaspaceObj::Type msotype()   const { return MetaspaceObj::array_type(sizeof(T*)); }

    virtual void metaspace_pointers_do(MetaspaceClosure *it) const {
      metaspace_pointers_do_at_impl(it, dereference());
    }
    virtual void metaspace_pointers_do_at(MetaspaceClosure *it, address new_loc) const {
      metaspace_pointers_do_at_impl(it, (Array<T*>*)new_loc);
    }
  private:
    void metaspace_pointers_do_at_impl(MetaspaceClosure *it, Array<T*>* array) const {
      log_trace(cds)("Iter(ObjectArray): %p [%d]", array, array->length());
      for (int i = 0; i < array->length(); i++) {
        T** mpp = array->adr_at(i);
        it->push(mpp);
      }
    }
  };

  void push_impl(Ref* ref, Writability w);

public:
  // returns true if we want to keep iterating the pointers embedded inside <ref>
  virtual bool do_ref(Ref* ref, bool read_only) = 0;

  // When you do:
  //     void MyType::metaspace_pointers_do(MetaspaceClosure* it) {
  //       it->push(_my_field)
  //
  // C++ will try to match the "most specific" template function. This one will
  // will be matched if possible (if mpp is an Array<> of any pointer type).
  template <typename T> void push(Array<T*>** mpp, Writability w = _default) {
    PointerArrayRef<T> ref(mpp);
    push_impl(&ref, w);
  }

  // If the above function doesn't match (mpp is an Array<>, but T is not a pointer type), then
  // this is the second choice.
  template <typename T> void push(Array<T>** mpp, Writability w = _default) {
    PrimitiveArrayRef<T> ref(mpp);
    push_impl(&ref, w);
  }

  // If the above function doesn't match (mpp is not an Array<> type), then
  // this will be matched by default.
  template <class T> void push(T** mpp, Writability w = _default) {
    ObjectRef<T> ref(mpp);
    push_impl(&ref, w);
  }
};

// This is a special MetaspaceClosure that visits each unique MetaspaceObj once.
class UniqueMetaspaceClosure : public MetaspaceClosure {
  // Do not override. Returns true if we are discovering ref->obj() for the first time.
  virtual bool do_ref(Ref* ref, bool read_only);

public:
  // Gets called the first time we discover an object.
  virtual void do_unique_ref(Ref* ref, bool read_only) = 0;
private:
  static unsigned my_hash(const address& a) {
    return primitive_hash<address>(a);
  }
  static bool my_equals(const address& a0, const address& a1) {
    return primitive_equals<address>(a0, a1);
  }
  ResourceHashtable<
      address, bool,
      UniqueMetaspaceClosure::my_hash,   // solaris compiler doesn't like: primitive_hash<address>
      UniqueMetaspaceClosure::my_equals, // solaris compiler doesn't like: primitive_equals<address>
      15889,                             // prime number
      ResourceObj::C_HEAP> _has_been_visited;
};

#endif // SHARE_VM_MEMORY_METASPACE_ITERATOR_HPP

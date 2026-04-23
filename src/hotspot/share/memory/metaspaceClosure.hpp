/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_MEMORY_METASPACECLOSURE_HPP
#define SHARE_MEMORY_METASPACECLOSURE_HPP

#include "cppstdlib/type_traits.hpp"
#include "logging/log.hpp"
#include "memory/allocation.hpp"
#include "memory/metaspaceClosureType.hpp"
#include "metaprogramming/enableIf.hpp"
#include "oops/array.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/macros.hpp"
#include "utilities/resizableHashTable.hpp"

// class MetaspaceClosure --
//
// This class is used for iterating the class metadata objects. It
// provides an API to walk all the reachable objects starting from a set of
// root references (such as all Klass'es in the SystemDictionary).
//
// Currently it is used to copy class metadata into the AOT cache.
// See ArchiveBuilder for an example.
class MetaspaceClosure {
public:
  enum Writability {
    _writable,
    _not_writable,
    _default
  };

#define METASPACE_CLOSURE_TYPE_NAME_CASE(name) case MetaspaceClosureType::name ## Type: return #name;

  static const char* type_name(MetaspaceClosureType type) {
    switch(type) {
    METASPACE_CLOSURE_TYPES_DO(METASPACE_CLOSURE_TYPE_NAME_CASE)
    default:
      ShouldNotReachHere();
      return nullptr;
    }
  }

  // class MetaspaceClosure::Ref --
  //
  // For type X to be iterable by MetaspaceClosure, X (or one of X's supertypes) must have
  // the following public functions:
  //         void metaspace_pointers_do(MetaspaceClosure* it);
  //         static bool is_read_only_by_default() { return true; }
  //
  // In addition, if X is not a subtype of MetaspaceObj, it must have the following function:
  //         MetaspaceClosureType type() const;
  //         int size_in_heapwords() const;
  //
  // Currently, the iterable types include all subtypes of MetsapceObj, as well
  // as GrowableArray (C-heap allocated only), ModuleEntry, and PackageEntry.
  //
  // (Note that GrowableArray is supported specially and does not require the above functions.)
  //
  // Calling these functions would be trivial if these were virtual functions.
  // However, to save space, MetaspaceObj has NO vtable. The vtable is introduced
  // only in the Metadata class.
  //
  // To work around the lack of a vtable, we use the Ref class with templates
  // so that we can statically discover the type of a object. The use of Ref
  // depends on the fact that:
  //
  // [1] We don't use polymorphic pointers to MetaspaceObj's that are not subclasses
  //     of Metadata. I.e., we don't do this:
  //     class Klass {
  //         MetaspaceObj *_obj;
  //         Array<int>* foo() { return (Array<int>*)_obj; }
  //         Symbol*     bar() { return (Symbol*)    _obj; }
  //
  // [2] All Array<T> dimensions are statically declared.
  //
  // Pointer Tagging
  //
  // All metaspace pointers are at least 4 byte aligned. Therefore, it's possible for
  // certain pointers to contain "tags" in their lowest 2 bits.
  //
  // Ref::obj() clears the tag bits in the return values. As a result, most
  // callers who just want walk a closure of metaspace objects do not need to worry
  // about the tag bits.
  //
  // If you need to use the tags, you can access the tagged pointer with Ref::addr()
  // and manipulate its parts with strip_tags(), decode_tags() and add_tags()
  class Ref : public CHeapObj<mtMetaspace> {
    Writability _writability;
    address _enclosing_obj;
    Ref* _next;
    NONCOPYABLE(Ref);

  protected:
    virtual void** mpp() const = 0;
    Ref(Writability w) : _writability(w), _enclosing_obj(nullptr), _next(nullptr) {}
  public:
    virtual bool not_null() const = 0;
    virtual int size() const = 0;
    virtual void metaspace_pointers_do(MetaspaceClosure *it) const = 0;
    virtual MetaspaceClosureType type() const = 0;
    virtual bool is_read_only_by_default() const = 0;
    virtual ~Ref() {}

    address obj() const {
      return strip_tags(*addr());
    }

    address* addr() const {
      return (address*)mpp();
    }

    // See comments in ArchiveBuilder::remember_embedded_pointer_in_enclosing_obj()
    address enclosing_obj() const {
      return _enclosing_obj;
    }
    void set_enclosing_obj(address obj) {
      _enclosing_obj = obj;
    }

    Writability writability() const { return _writability; };
    void set_next(Ref* n)           { _next = n; }
    Ref* next() const               { return _next; }
  };

  // Pointer tagging support
  constexpr static uintx TAG_MASK = 0x03;

  template <typename T>
  static T strip_tags(T ptr_with_tags) {
    uintx n = (uintx)ptr_with_tags;
    return (T)(n & ~TAG_MASK);
  }

  template <typename T>
  static uintx decode_tags(T ptr_with_tags) {
    uintx n = (uintx)ptr_with_tags;
    return (n & TAG_MASK);
  }

  template <typename T>
  static T add_tags(T ptr, uintx tags) {
    uintx n = (uintx)ptr;
    assert((n & TAG_MASK) == 0, "sanity");
    assert(tags <= TAG_MASK, "sanity");
    return (T)(n | tags);
  }

private:
  template <typename T, ENABLE_IF(std::is_base_of<MetaspaceObj, T>::value)>
  static int get_size(T* obj) {
    return obj->size();
  }

  template <typename T, ENABLE_IF(!std::is_base_of<MetaspaceObj, T>::value)>
  static int get_size(T* obj) {
    return obj->size_in_heapwords();
  }

  // MSORef -- iterate an instance of T, where T::metaspace_pointers_do() exists.
  template <class T> class MSORef : public Ref {
    T** _mpp;
    T* dereference() const {
      return strip_tags(*_mpp);
    }
  protected:
    virtual void** mpp() const override {
      return (void**)_mpp;
    }

  public:
    MSORef(T** mpp, Writability w) : Ref(w), _mpp(mpp) {}

    virtual bool is_read_only_by_default() const override { return T::is_read_only_by_default(); }
    virtual bool not_null()                const override { return dereference() != nullptr; }
    virtual int size()                     const override { return get_size(dereference()); }
    virtual MetaspaceClosureType type()    const override { return as_type(dereference()->type()); }
    virtual void metaspace_pointers_do(MetaspaceClosure *it) const override {
      dereference()->metaspace_pointers_do(it);
    }
  };

  //--------------------------------
  // Support for GrowableArray<T>
  //--------------------------------

  // GrowableArrayRef -- iterate an instance of GrowableArray<T>.
  template <class T> class GrowableArrayRef : public Ref {
    GrowableArray<T>** _mpp;
    GrowableArray<T>* dereference() const {
      return *_mpp;
    }

  protected:
    virtual void** mpp() const override {
      return (void**)_mpp;
    }

  public:
    GrowableArrayRef(GrowableArray<T>** mpp, Writability w) : Ref(w), _mpp(mpp) {}

    virtual bool is_read_only_by_default() const override { return false; }
    virtual bool not_null()                const override { return dereference() != nullptr;  }
    virtual int size()                     const override { return (int)heap_word_size(sizeof(*dereference())); }
    virtual MetaspaceClosureType type()    const override { return MetaspaceClosureType::GrowableArrayType; }
    virtual void metaspace_pointers_do(MetaspaceClosure *it) const override {
      GrowableArray<T>* array = dereference();
      log_trace(aot)("Iter(GrowableArray): %p [%d]", array, array->length());
      array->assert_on_C_heap();
      it->push_c_array(array->data_addr(), array->capacity());
    }
  };

  // For iterating the buffer held by GrowableArray<T>.
  template <class T> class CArrayRef : public Ref {
    T** _mpp;
    int _num_elems; // Number of elements

    int byte_size() const {
      return _num_elems * sizeof(T);
    }

    // Gives you back the GrowableArray::_data
    T* dereference() const {
      return *_mpp;
    }

    int num_elems() const {
      return _num_elems;
    }

  protected:
    virtual void** mpp() const override {
      return (void**)_mpp;
    }

  public:
    CArrayRef(T** mpp, int num_elems, Writability w)
      : Ref(w), _mpp(mpp), _num_elems(num_elems) {
      assert(is_aligned(byte_size(), BytesPerWord), "must be");
    }

    virtual bool is_read_only_by_default() const override { return false; }
    virtual bool not_null()                const override { precond(dereference() != nullptr); return true; }
    virtual int size()                     const override { return (int)heap_word_size(byte_size()); }
    virtual MetaspaceClosureType type()    const override { return MetaspaceClosureType::CArrayType; }
    virtual void metaspace_pointers_do(MetaspaceClosure *it) const override {
      metaspace_pointers_do_impl<T>(it, dereference());
    }

  private:
    // E.g., GrowableArray<int>
    template <typename U, ENABLE_IF(!std::is_pointer<U>::value && !HAS_METASPACE_POINTERS_DO(U))>
    void metaspace_pointers_do_impl(MetaspaceClosure* it, T* array) const {
      log_trace(aot)("Iter(OtherCArray): %p [%d]", array, num_elems());
    }

    // E.g., GrowableArray<Annotation>
    template <typename U, ENABLE_IF(!std::is_pointer<U>::value && HAS_METASPACE_POINTERS_DO(U))>
    void metaspace_pointers_do_impl(MetaspaceClosure* it, T* array) const {
      log_trace(aot)("Iter(MSOCArray): %p [%d]", array, num_elems());
      for (int i = 0; i < num_elems(); i++) {
        T* elm = array + i;
        elm->metaspace_pointers_do(it);
      }
    }

    // E.g., GrowableArray<Klass*>
    template <typename U, ENABLE_IF(std::is_pointer<U>::value && HAS_METASPACE_POINTERS_DO(typename std::remove_pointer<U>::type))>
    void metaspace_pointers_do_impl(MetaspaceClosure* it, T* array) const {
      log_trace(aot)("Iter(MSOPointerCArray): %p [%d]", array, num_elems());
      for (int i = 0; i < num_elems(); i++) {
        T* mpp = array + i;
        it->push(mpp);
      }
    }
  };

  // Normally, chains of references like a->b->c->d are iterated recursively. However,
  // if recursion is too deep, we save the Refs in _pending_refs, and push them later in
  // MetaspaceClosure::finish(). This avoids overflowing the C stack.
  //
  // When we are visting d, the _enclosing_ref is c,
  // When we are visting c, the _enclosing_ref is b, ... and so on.
  static const int MAX_NEST_LEVEL = 5;
  Ref* _pending_refs;
  int _nest_level;
  Ref* _enclosing_ref;

  void push_impl(Ref* ref);
  void do_push(Ref* ref);

public:
  MetaspaceClosure(): _pending_refs(nullptr), _nest_level(0), _enclosing_ref(nullptr) {}
  ~MetaspaceClosure();

  void finish();

  // returns true if we want to keep iterating the pointers embedded inside <ref>
  virtual bool do_ref(Ref* ref, bool read_only) = 0;

private:
  template <class REF_TYPE, typename T>
  void push_with_ref(T** mpp, Writability w) {
    // We cannot make stack allocation because the Ref may need to be saved in
    // _pending_refs to avoid overflowing the C call stack
    push_impl(new REF_TYPE(mpp, w));
  }

public:
  // When MetaspaceClosure::push(...) is called, pick the correct Ref subtype to handle it:
  //
  // MetaspaceClosure*      it = ...;
  // Klass*                 o  = ...;  it->push(&o);     => MSORef
  //
  // GrowableArrays have a separate "C array" buffer, so they are scanned in two steps:
  //
  // GrowableArray<jlong>*      ga1 = ...;  it->push(&ga1);  => GrowableArrayRef => CArrayRef<jlong>
  // GrowableArray<Annotation>* ga2 = ...;  it->push(&ga2);  => GrowableArrayRef => CArrayRef<Annotation>
  // GrowableArray<Klass*>*     ga3 = ...;  it->push(&ga3);  => GrowableArrayRef => CArrayRef<Klass*>
  //
  // Note that the following will fail to compile:
  //
  // MemoryPool*            p  = ...;  it->push(&p);     => MemoryPool doesn't have metaspace_pointers_do
  // Array<MemoryPool*>*    a6 = ...;  it->push(&a6);    => MemoryPool doesn't have metaspace_pointers_do
  // Array<int*>*           a7 = ...;  it->push(&a7);    => int doesn't have metaspace_pointers_do

  // --- Regular iterable objects
  template <typename T>
  void push(T** mpp, Writability w = _default) {
    static_assert(HAS_METASPACE_POINTERS_DO(T), "Do not push pointers of arbitrary types");
    push_with_ref<MSORef<T>>(mpp, w);
  }

  template <typename T>
  void push(GrowableArray<T>** mpp, Writability w = _default) {
    push_with_ref<GrowableArrayRef<T>>(mpp, w);
  }

  // --- The buffer of GrowableArray<T>
  template <typename T>
  void push_c_array(T** mpp, int num_elems, Writability w = _default) {
    push_impl(new CArrayRef<T>(mpp, num_elems, w));
  }
};

// This is a special MetaspaceClosure that visits each unique object once.
class UniqueMetaspaceClosure : public MetaspaceClosure {
  static const int INITIAL_TABLE_SIZE = 15889;
  static const int MAX_TABLE_SIZE     = 1000000;

  // Do not override. Returns true if we are discovering ref->obj() for the first time.
  virtual bool do_ref(Ref* ref, bool read_only);

public:
  // Gets called the first time we discover an object.
  virtual bool do_unique_ref(Ref* ref, bool read_only) = 0;
  UniqueMetaspaceClosure() : _has_been_visited(INITIAL_TABLE_SIZE, MAX_TABLE_SIZE) {}

private:
  ResizeableHashTable<address, bool, AnyObj::C_HEAP,
                              mtClassShared> _has_been_visited;
};

#endif // SHARE_MEMORY_METASPACECLOSURE_HPP

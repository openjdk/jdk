/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_RUNTIME_HANDLES_HPP
#define SHARE_VM_RUNTIME_HANDLES_HPP

#include "oops/klass.hpp"
#include "oops/klassOop.hpp"
#include "utilities/top.hpp"

//------------------------------------------------------------------------------------------------------------------------
// In order to preserve oops during garbage collection, they should be
// allocated and passed around via Handles within the VM. A handle is
// simply an extra indirection allocated in a thread local handle area.
//
// A handle is a ValueObj, so it can be passed around as a value, can
// be used as a parameter w/o using &-passing, and can be returned as a
// return value.
//
// oop parameters and return types should be Handles whenever feasible.
//
// Handles are declared in a straight-forward manner, e.g.
//
//   oop obj = ...;
//   Handle h1(obj);              // allocate new handle
//   Handle h2(thread, obj);      // faster allocation when current thread is known
//   Handle h3;                   // declare handle only, no allocation occurs
//   ...
//   h3 = h1;                     // make h3 refer to same indirection as h1
//   oop obj2 = h2();             // get handle value
//   h1->print();                 // invoking operation on oop
//
// Handles are specialized for different oop types to provide extra type
// information and avoid unnecessary casting. For each oop type xxxOop
// there is a corresponding handle called xxxHandle, e.g.
//
//   oop           Handle
//   methodOop     methodHandle
//   instanceOop   instanceHandle
//
// For klassOops, it is often useful to model the Klass hierarchy in order
// to get access to the klass_part without casting. For each xxxKlass there
// is a corresponding handle called xxxKlassHandle, e.g.
//
//   klassOop      Klass           KlassHandle
//   klassOop      methodKlass     methodKlassHandle
//   klassOop      instanceKlass   instanceKlassHandle
//

//------------------------------------------------------------------------------------------------------------------------
// Base class for all handles. Provides overloading of frequently
// used operators for ease of use.

class Handle VALUE_OBJ_CLASS_SPEC {
 private:
  oop* _handle;

 protected:
  oop     obj() const                            { return _handle == NULL ? (oop)NULL : *_handle; }
  oop     non_null_obj() const                   { assert(_handle != NULL, "resolving NULL handle"); return *_handle; }

 public:
  // Constructors
  Handle()                                       { _handle = NULL; }
  Handle(oop obj);
#ifndef ASSERT
  Handle(Thread* thread, oop obj);
#else
  // Don't inline body with assert for current thread
  Handle(Thread* thread, oop obj);
#endif // ASSERT

  // General access
  oop     operator () () const                   { return obj(); }
  oop     operator -> () const                   { return non_null_obj(); }
  bool    operator == (oop o) const              { return obj() == o; }
  bool    operator == (const Handle& h) const          { return obj() == h.obj(); }

  // Null checks
  bool    is_null() const                        { return _handle == NULL; }
  bool    not_null() const                       { return _handle != NULL; }

  // Debugging
  void    print()                                { obj()->print(); }

  // Direct interface, use very sparingly.
  // Used by JavaCalls to quickly convert handles and to create handles static data structures.
  // Constructor takes a dummy argument to prevent unintentional type conversion in C++.
  Handle(oop *handle, bool dummy)                { _handle = handle; }

  // Raw handle access. Allows easy duplication of Handles. This can be very unsafe
  // since duplicates is only valid as long as original handle is alive.
  oop* raw_value()                               { return _handle; }
  static oop raw_resolve(oop *handle)            { return handle == NULL ? (oop)NULL : *handle; }
};


//------------------------------------------------------------------------------------------------------------------------
// Base class for Handles containing klassOops. Provides overloading of frequently
// used operators for ease of use and typed access to the Klass part.
class KlassHandle: public Handle {
 protected:
  klassOop    obj() const                        { return (klassOop)Handle::obj(); }
  klassOop    non_null_obj() const               { return (klassOop)Handle::non_null_obj(); }
  Klass*      as_klass() const                   { return non_null_obj()->klass_part(); }

 public:
  // Constructors
  KlassHandle ()                                 : Handle()            {}
  KlassHandle (oop obj) : Handle(obj) {
    assert(SharedSkipVerify || is_null() || obj->is_klass(), "not a klassOop");
  }
  KlassHandle (Klass* kl) : Handle(kl ? kl->as_klassOop() : (klassOop)NULL) {
    assert(SharedSkipVerify || is_null() || obj()->is_klass(), "not a klassOop");
  }

  // Faster versions passing Thread
  KlassHandle (Thread* thread, oop obj) : Handle(thread, obj) {
    assert(SharedSkipVerify || is_null() || obj->is_klass(), "not a klassOop");
  }
  KlassHandle (Thread *thread, Klass* kl)
    : Handle(thread, kl ? kl->as_klassOop() : (klassOop)NULL) {
    assert(is_null() || obj()->is_klass(), "not a klassOop");
  }

  // Direct interface, use very sparingly.
  // Used by SystemDictionaryHandles to create handles on existing WKKs.
  // The obj of such a klass handle may be null, because the handle is formed
  // during system bootstrapping.
  KlassHandle(klassOop *handle, bool dummy) : Handle((oop*)handle, dummy) {
    assert(SharedSkipVerify || is_null() || obj() == NULL || obj()->is_klass(), "not a klassOop");
  }

  // General access
  klassOop    operator () () const               { return obj(); }
  Klass*      operator -> () const               { return as_klass(); }
};


//------------------------------------------------------------------------------------------------------------------------
// Specific Handles for different oop types
#define DEF_HANDLE(type, is_a)                   \
  class type##Handle;                            \
  class type##Handle: public Handle {            \
   protected:                                    \
    type##Oop    obj() const                     { return (type##Oop)Handle::obj(); } \
    type##Oop    non_null_obj() const            { return (type##Oop)Handle::non_null_obj(); } \
                                                 \
   public:                                       \
    /* Constructors */                           \
    type##Handle ()                              : Handle()                 {} \
    type##Handle (type##Oop obj) : Handle((oop)obj) {                         \
      assert(SharedSkipVerify || is_null() || ((oop)obj)->is_a(),             \
             "illegal type");                                                 \
    }                                                                         \
    type##Handle (Thread* thread, type##Oop obj) : Handle(thread, (oop)obj) { \
      assert(SharedSkipVerify || is_null() || ((oop)obj)->is_a(), "illegal type");  \
    }                                                                         \
    \
    /* Special constructor, use sparingly */ \
    type##Handle (type##Oop *handle, bool dummy) : Handle((oop*)handle, dummy) {} \
                                                 \
    /* Operators for ease of use */              \
    type##Oop    operator () () const            { return obj(); } \
    type##Oop    operator -> () const            { return non_null_obj(); } \
  };


DEF_HANDLE(instance         , is_instance         )
DEF_HANDLE(method           , is_method           )
DEF_HANDLE(constMethod      , is_constMethod      )
DEF_HANDLE(methodData       , is_methodData       )
DEF_HANDLE(array            , is_array            )
DEF_HANDLE(constantPool     , is_constantPool     )
DEF_HANDLE(constantPoolCache, is_constantPoolCache)
DEF_HANDLE(objArray         , is_objArray         )
DEF_HANDLE(typeArray        , is_typeArray        )
DEF_HANDLE(symbol           , is_symbol           )

//------------------------------------------------------------------------------------------------------------------------
// Specific KlassHandles for different Klass types

#define DEF_KLASS_HANDLE(type, is_a)             \
  class type##Handle : public KlassHandle {      \
   public:                                       \
    /* Constructors */                           \
    type##Handle ()                              : KlassHandle()           {} \
    type##Handle (klassOop obj) : KlassHandle(obj) {                          \
      assert(SharedSkipVerify || is_null() || obj->klass_part()->is_a(),      \
             "illegal type");                                                 \
    }                                                                         \
    type##Handle (Thread* thread, klassOop obj) : KlassHandle(thread, obj) {  \
      assert(SharedSkipVerify || is_null() || obj->klass_part()->is_a(),      \
             "illegal type");                                                 \
    }                                                                         \
                                                 \
    /* Access to klass part */                   \
    type*        operator -> () const            { return (type*)obj()->klass_part(); } \
                                                 \
    static type##Handle cast(KlassHandle h)      { return type##Handle(h()); } \
                                                 \
  };


DEF_KLASS_HANDLE(instanceKlass         , oop_is_instance_slow )
DEF_KLASS_HANDLE(methodKlass           , oop_is_method        )
DEF_KLASS_HANDLE(constMethodKlass      , oop_is_constMethod   )
DEF_KLASS_HANDLE(klassKlass            , oop_is_klass         )
DEF_KLASS_HANDLE(arrayKlassKlass       , oop_is_arrayKlass    )
DEF_KLASS_HANDLE(objArrayKlassKlass    , oop_is_objArrayKlass )
DEF_KLASS_HANDLE(typeArrayKlassKlass   , oop_is_typeArrayKlass)
DEF_KLASS_HANDLE(arrayKlass            , oop_is_array         )
DEF_KLASS_HANDLE(typeArrayKlass        , oop_is_typeArray_slow)
DEF_KLASS_HANDLE(objArrayKlass         , oop_is_objArray_slow )
DEF_KLASS_HANDLE(symbolKlass           , oop_is_symbol        )
DEF_KLASS_HANDLE(constantPoolKlass     , oop_is_constantPool  )
DEF_KLASS_HANDLE(constantPoolCacheKlass, oop_is_constantPool  )


//------------------------------------------------------------------------------------------------------------------------
// Thread local handle area

class HandleArea: public Arena {
  friend class HandleMark;
  friend class NoHandleMark;
  friend class ResetNoHandleMark;
#ifdef ASSERT
  int _handle_mark_nesting;
  int _no_handle_mark_nesting;
#endif
  HandleArea* _prev;          // link to outer (older) area
 public:
  // Constructor
  HandleArea(HandleArea* prev) {
    debug_only(_handle_mark_nesting    = 0);
    debug_only(_no_handle_mark_nesting = 0);
    _prev = prev;
  }

  // Handle allocation
 private:
  oop* real_allocate_handle(oop obj) {
#ifdef ASSERT
    oop* handle = (oop*) (UseMallocOnly ? internal_malloc_4(oopSize) : Amalloc_4(oopSize));
#else
    oop* handle = (oop*) Amalloc_4(oopSize);
#endif
    *handle = obj;
    return handle;
  }
 public:
#ifdef ASSERT
  oop* allocate_handle(oop obj);
#else
  oop* allocate_handle(oop obj) { return real_allocate_handle(obj); }
#endif

  // Garbage collection support
  void oops_do(OopClosure* f);

  // Number of handles in use
  size_t used() const     { return Arena::used() / oopSize; }

  debug_only(bool no_handle_mark_active() { return _no_handle_mark_nesting > 0; })
};


//------------------------------------------------------------------------------------------------------------------------
// Handles are allocated in a (growable) thread local handle area. Deallocation
// is managed using a HandleMark. It should normally not be necessary to use
// HandleMarks manually.
//
// A HandleMark constructor will record the current handle area top, and the
// desctructor will reset the top, destroying all handles allocated in between.
// The following code will therefore NOT work:
//
//   Handle h;
//   {
//     HandleMark hm;
//     h = Handle(obj);
//   }
//   h()->print();       // WRONG, h destroyed by HandleMark destructor.
//
// If h has to be preserved, it can be converted to an oop or a local JNI handle
// across the HandleMark boundary.

// The base class of HandleMark should have been StackObj but we also heap allocate
// a HandleMark when a thread is created.

class HandleMark {
 private:
  Thread *_thread;              // thread that owns this mark
  HandleArea *_area;            // saved handle area
  Chunk *_chunk;                // saved arena chunk
  char *_hwm, *_max;            // saved arena info
  NOT_PRODUCT(size_t _size_in_bytes;) // size of handle area
  // Link to previous active HandleMark in thread
  HandleMark* _previous_handle_mark;

  void initialize(Thread* thread);                // common code for constructors
  void set_previous_handle_mark(HandleMark* mark) { _previous_handle_mark = mark; }
  HandleMark* previous_handle_mark() const        { return _previous_handle_mark; }

 public:
  HandleMark();                            // see handles_inline.hpp
  HandleMark(Thread* thread)                      { initialize(thread); }
  ~HandleMark();

  // Functions used by HandleMarkCleaner
  // called in the constructor of HandleMarkCleaner
  void push();
  // called in the destructor of HandleMarkCleaner
  void pop_and_restore();
};

//------------------------------------------------------------------------------------------------------------------------
// A NoHandleMark stack object will verify that no handles are allocated
// in its scope. Enabled in debug mode only.

class NoHandleMark: public StackObj {
 public:
#ifdef ASSERT
  NoHandleMark();
  ~NoHandleMark();
#else
  NoHandleMark()  {}
  ~NoHandleMark() {}
#endif
};


class ResetNoHandleMark: public StackObj {
  int _no_handle_mark_nesting;
 public:
#ifdef ASSERT
  ResetNoHandleMark();
  ~ResetNoHandleMark();
#else
  ResetNoHandleMark()  {}
  ~ResetNoHandleMark() {}
#endif
};

#endif // SHARE_VM_RUNTIME_HANDLES_HPP

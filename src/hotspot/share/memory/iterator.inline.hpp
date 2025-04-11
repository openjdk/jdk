/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_MEMORY_ITERATOR_INLINE_HPP
#define SHARE_MEMORY_ITERATOR_INLINE_HPP

#include "memory/iterator.hpp"

#include "cds/aotLinkedClassBulkLoader.hpp"
#include "classfile/classLoaderData.hpp"
#include "code/nmethod.hpp"
#include "oops/access.inline.hpp"
#include "oops/compressedOops.inline.hpp"
#include "oops/klass.hpp"
#include "oops/instanceKlass.inline.hpp"
#include "oops/instanceMirrorKlass.inline.hpp"
#include "oops/instanceClassLoaderKlass.inline.hpp"
#include "oops/instanceRefKlass.inline.hpp"
#include "oops/instanceStackChunkKlass.inline.hpp"
#include "oops/klassInfoLUTEntry.inline.hpp"
#include "oops/objArrayKlass.inline.hpp"
#include "oops/typeArrayKlass.inline.hpp"
#include "utilities/debug.hpp"

// Defaults to strong claiming.
inline MetadataVisitingOopIterateClosure::MetadataVisitingOopIterateClosure(ReferenceDiscoverer* rd) :
    ClaimMetadataVisitingOopIterateClosure(ClassLoaderData::_claim_strong, rd) {}

inline void ClaimMetadataVisitingOopIterateClosure::do_cld(ClassLoaderData* cld) {
  cld->oops_do(this, _claim);
}

inline void ClaimMetadataVisitingOopIterateClosure::do_klass(Klass* k) {
  ClassLoaderData* cld = k->class_loader_data();
  if (cld != nullptr) {
    ClaimMetadataVisitingOopIterateClosure::do_cld(cld);
  } else {
    assert(AOTLinkedClassBulkLoader::is_pending_aot_linked_class(k), "sanity");
  }
}

inline void ClaimMetadataVisitingOopIterateClosure::do_nmethod(nmethod* nm) {
  nm->follow_nmethod(this);
}

inline void ClaimMetadataVisitingOopIterateClosure::do_method(Method* m) {
  // Mark interpreted frames for class redefinition
  m->record_gc_epoch();
}

// TODO update comment
// Dispatch table implementation for *Klass::oop_oop_iterate
//
// It allows for a single call to do a multi-dispatch to an optimized version
//   of oop_oop_iterate that statically know all these types:
//   - OopClosureType    : static type give at call site
//   - Klass*            : dynamic to static type through Klass::kind() -> table index
//   - UseCompressedOops : dynamic to static value determined once
//
// when users call obj->oop_iterate(&cl).
//
// oopDesc::oop_iterate() calls OopOopIterateDispatch::function(klass)(cl, obj, klass),
//   which dispatches to an optimized version of
//   [Instance, ObjArry, etc]Klass::oop_oop_iterate(oop, OopClosureType)
//
// OopClosureType :
//   If OopClosureType has an implementation of do_oop (and do_metadata et.al.),
//   then the static type of OopClosureType will be used to allow inlining of
//   do_oop (even though do_oop is virtual). Otherwise, a virtual call will be
//   used when calling do_oop.
//
// Klass* :
//   A table mapping from *Klass::Kind to function is setup. This happens once
//   when the program starts, when the static _table instance is initialized for
//   the OopOopIterateDispatch specialized with the OopClosureType.
//
// UseCompressedOops :
//   Initially the table is populated with an init function, and not the actual
//   oop_oop_iterate function. This is done, so that the first time we dispatch
//   through the init function we check what the value of UseCompressedOops
//   became, and use that to determine if we should install an optimized
//   narrowOop version or optimized oop version of oop_oop_iterate. The appropriate
//   oop_oop_iterate function replaces the init function in the table, and
//   succeeding calls will jump directly to oop_oop_iterate.


class DispatchBase {
protected:

  // Return the size of an object; uses as much hard-coded information as possible
  template <class KlassType, class OopType, bool compact_headers>
  static inline size_t calculate_size_for_object_fast(KlassLUTEntry klute, oop obj) {
    size_t s;
    constexpr bool is_instance = KlassType::Kind < Klass::TypeArrayKlassKind;
    if (is_instance) {
      if (klute.ik_carries_infos()) {
        s = klute.ik_wordsize();
      } else {
        // Rare path: size not statically computable (e.g. MirrorKlass); calculate using Klass
        s = obj->size();
      }
    } else {
      constexpr bool is_objarray = KlassType::Kind == Klass::ObjArrayKlassKind;
      s = klute.ak_calculate_wordsize_given_oop_fast<is_objarray, OopType, compact_headers>(obj);
    }
    assert(s == obj->size(), "Unexpected size (klute %X, %zu vs %zu)",
           klute.value(), s, obj->size());
    return s;
  }

  static inline bool should_use_slowpath_getsize() {
    return !UseCompressedClassPointers || ObjectAlignmentInBytes != BytesPerWord;
  }
};

// Dispatch for normal iteration, unbounded, does not return size
// void XXXKlass::oop_oop_iterate(oop, closure, klute)
template <typename OopClosureType>
class OopOopIterateDispatch : public DispatchBase {
  typedef void (*FunctionType) (oop obj, OopClosureType* cl, KlassLUTEntry klute);

  struct Table {

    FunctionType _function [Klass::KLASS_KIND_COUNT];

    template <typename KlassType, typename T>
    static void invoke(oop obj, OopClosureType* cl, KlassLUTEntry klute) {
      KlassType::template oop_oop_iterate<T>(obj, cl, klute);
    }

    template <typename KlassType>
    static void init_and_execute(oop obj, OopClosureType* cl, KlassLUTEntry klute) {
      OopOopIterateDispatch<OopClosureType>::_table.resolve_and_execute<KlassType> (obj, cl, klute);
    }

    template <typename KlassType>
    void resolve_and_execute(oop obj, OopClosureType* cl, KlassLUTEntry klute) {
      resolve<KlassType>();
      _function[KlassType::Kind] (obj, cl, klute);
    }

    template <typename KlassType>
    void set_init_function() {
      _function[KlassType::Kind] = &init_and_execute<KlassType>;
    }

    template <typename KlassType>
    void resolve() {
      _function[KlassType::Kind] = UseCompressedOops ?
          &invoke<KlassType, narrowOop> :
          &invoke<KlassType, oop>;
    }

    Table() {
      set_init_function<InstanceKlass>();
      set_init_function<InstanceRefKlass>();
      set_init_function<InstanceMirrorKlass>();
      set_init_function<InstanceClassLoaderKlass>();
      set_init_function<InstanceStackChunkKlass>();
      set_init_function<ObjArrayKlass>();
      set_init_function<TypeArrayKlass>();
    }

  };

  static Table _table;

public:

  static void invoke(oop obj, OopClosureType* cl, KlassLUTEntry klute) {
    const int slot = klute.kind();
    _table._function[slot](obj, cl, klute);
  }

};

template <typename OopClosureType>
typename OopOopIterateDispatch<OopClosureType>::Table OopOopIterateDispatch<OopClosureType>::_table;

template <typename OopClosureType>
void OopIteratorClosureDispatch::oop_oop_iterate  (oop obj, OopClosureType* cl, KlassLUTEntry klute) {
  OopOopIterateDispatch<OopClosureType>::invoke(obj, cl, klute);
}

// Dispatch for reverse iteration, unbounded, does not return size
// void XXXKlass::oop_oop_iterate_reverse(oop, closure, klute)
template <typename OopClosureType>
class OopOopIterateDispatchReverse {
  typedef void (*FunctionType) (oop obj, OopClosureType* cl, KlassLUTEntry klute);

  struct Table {

    FunctionType _function [Klass::KLASS_KIND_COUNT];

    template <typename KlassType, typename T>
    static void invoke(oop obj, OopClosureType* cl, KlassLUTEntry klute) {
      KlassType::template oop_oop_iterate_reverse<T> (obj, cl, klute);
    }

    template <typename KlassType>
    static void init_and_execute (oop obj, OopClosureType* cl, KlassLUTEntry klute) {
      OopOopIterateDispatchReverse<OopClosureType>::_table.resolve_and_execute<KlassType>(obj, cl, klute);
    }

    template <typename KlassType>
    void resolve_and_execute(oop obj, OopClosureType* cl, KlassLUTEntry klute) {
      resolve<KlassType>();
      _function[KlassType::Kind](obj, cl, klute);
    }

    template <typename KlassType>
    void set_init_function() {
      _function[KlassType::Kind] = &init_and_execute<KlassType>;
    }

    template <typename KlassType>
    void resolve() {
      _function[KlassType::Kind] = UseCompressedOops ?
          &invoke<KlassType, narrowOop> :
          &invoke<KlassType, oop>;
    }

    Table() {
      set_init_function<InstanceKlass>();
      set_init_function<InstanceRefKlass>();
      set_init_function<InstanceMirrorKlass>();
      set_init_function<InstanceClassLoaderKlass>();
      set_init_function<InstanceStackChunkKlass>();
      set_init_function<ObjArrayKlass>();
      set_init_function<TypeArrayKlass>();
    }

  };

  static Table _table;

public:

  static void invoke(oop obj, OopClosureType* cl, KlassLUTEntry klute) {
    const int slot = klute.kind();
    _table._function[slot] (obj, cl, klute);
  }

};

template <typename OopClosureType>
typename OopOopIterateDispatchReverse<OopClosureType>::Table OopOopIterateDispatchReverse<OopClosureType>::_table;

template <typename OopClosureType>
void OopIteratorClosureDispatch::oop_oop_iterate_reverse(oop obj, OopClosureType* cl, KlassLUTEntry klute) {
  OopOopIterateDispatchReverse<OopClosureType>::invoke (obj, cl, klute);
}

template <typename OopClosureType>
class OopOopIterateDispatchBounded {
  typedef void (*FunctionType) (oop obj, OopClosureType* cl, MemRegion mr, KlassLUTEntry klute);

  struct Table {

    FunctionType _function [Klass::KLASS_KIND_COUNT];

    template <typename KlassType, typename T>
    static void invoke(oop obj, OopClosureType* cl, MemRegion mr, KlassLUTEntry klute) {
      KlassType::template oop_oop_iterate_bounded<T> (obj, cl, mr, klute);
    }

    template <typename KlassType>
    static void init_and_execute(oop obj, OopClosureType* cl, MemRegion mr, KlassLUTEntry klute) {
      OopOopIterateDispatchBounded<OopClosureType>::_table.resolve_and_execute<KlassType> (obj, cl, mr, klute);
    }

    template <typename KlassType>
    void resolve_and_execute(oop obj, OopClosureType* cl, MemRegion mr, KlassLUTEntry klute) {
      resolve<KlassType>();
      _function[KlassType::Kind] (obj, cl, mr, klute);
    }

    template <typename KlassType>
    void set_init_function() {
      _function[KlassType::Kind] = &init_and_execute<KlassType>;
    }

    template <typename KlassType>
    void resolve() {
      _function[KlassType::Kind] = UseCompressedOops ?
          &invoke<KlassType, narrowOop> :
          &invoke<KlassType, oop>;
    }

    Table(){
      set_init_function<InstanceKlass>();
      set_init_function<InstanceRefKlass>();
      set_init_function<InstanceMirrorKlass>();
      set_init_function<InstanceClassLoaderKlass>();
      set_init_function<InstanceStackChunkKlass>();
      set_init_function<ObjArrayKlass>();
      set_init_function<TypeArrayKlass>();
    }

  };

  static Table _table;

public:

  static void invoke(oop obj, OopClosureType* cl, MemRegion mr, KlassLUTEntry klute) {
    const int slot = klute.kind();
    _table._function[slot] (obj, cl, mr, klute);
  }

};

template <typename OopClosureType>
typename OopOopIterateDispatchBounded<OopClosureType>::Table OopOopIterateDispatchBounded<OopClosureType>::_table;

template <typename OopClosureType>
void OopIteratorClosureDispatch::oop_oop_iterate_bounded(oop obj, OopClosureType* cl, MemRegion mr, KlassLUTEntry klute) {
  OopOopIterateDispatchBounded<OopClosureType>::invoke(obj, cl, mr, klute);
}

// Dispatch for normal iteration, unbounded, returns size
// size_t XXXKlass::oop_oop_iterate(oop, closure, klute)
template <typename OopClosureType>
class OopOopIterateDispatchReturnSize : public DispatchBase {

  typedef size_t (*FunctionType) (oop obj, OopClosureType* cl, KlassLUTEntry klute);

  struct Table {

    FunctionType _function [Klass::KLASS_KIND_COUNT];

    template <typename KlassType, typename OopType, bool compact_headers>
    static size_t invoke(oop obj, OopClosureType* cl, KlassLUTEntry klute) {
      KlassType::template oop_oop_iterate<OopType> (obj, cl, klute);
      return calculate_size_for_object_fast<KlassType, OopType, compact_headers>(klute, obj);
    }

    template <class KlassType, class OopType>
    static size_t invoke(oop obj, OopClosureType* cl, KlassLUTEntry klute) {
      KlassType::template oop_oop_iterate<OopType> (obj, cl, klute);
      return obj->size();
    }

    template <typename KlassType>
    static size_t init_and_execute (oop obj, OopClosureType* cl, KlassLUTEntry klute) {
      return OopOopIterateDispatchReturnSize<OopClosureType>::_table.resolve_and_execute<KlassType> (obj, cl, klute);
    }

    template <typename KlassType>
        void set_init_function() {
          _function[KlassType::Kind] = &init_and_execute<KlassType>;
    }

    template <typename KlassType>
    size_t resolve_and_execute(oop obj, OopClosureType* cl, KlassLUTEntry klute) {
      resolve<KlassType>();
      return _function[KlassType::Kind] (obj, cl, klute);
    }

    template <typename KlassType>
    void resolve() {
      if (should_use_slowpath_getsize()) {
        if (UseCompressedOops) {
          _function[KlassType::Kind] = &invoke<KlassType, narrowOop>;
        } else {
          _function[KlassType::Kind] = &invoke<KlassType, oop>;
        }
      } else {
        if (UseCompressedOops) {
          if (UseCompactObjectHeaders) {
            _function[KlassType::Kind] = &invoke<KlassType, narrowOop, true>;
          } else {
            _function[KlassType::Kind] = &invoke<KlassType, narrowOop, false>;
          }
        } else {
          if (UseCompactObjectHeaders) {
            _function[KlassType::Kind] = &invoke<KlassType, oop, true>;
          } else {
            _function[KlassType::Kind] = &invoke<KlassType, oop, false>;
          }
        }
      }
    }

    Table() {
      set_init_function<InstanceKlass>();
      set_init_function<InstanceRefKlass>();
      set_init_function<InstanceMirrorKlass>();
      set_init_function<InstanceClassLoaderKlass>();
      set_init_function<InstanceStackChunkKlass>();
      set_init_function<ObjArrayKlass>();
      set_init_function<TypeArrayKlass>();
    }

  };

  static Table _table;

public:

  static size_t invoke(oop obj, OopClosureType* cl, KlassLUTEntry klute) {
    const int slot = klute.kind();
    return _table._function[slot](obj, cl, klute);
  }

};

template <typename OopClosureType>
typename OopOopIterateDispatchReturnSize<OopClosureType>::Table OopOopIterateDispatchReturnSize<OopClosureType>::_table;

template <typename OopClosureType>
size_t OopIteratorClosureDispatch::oop_oop_iterate_size(oop obj, OopClosureType* cl, KlassLUTEntry klute) {

  klute.verify_against_klass(obj->klass());

  return OopOopIterateDispatchReturnSize<OopClosureType>::invoke(obj, cl, klute);
}


// Dispatch for bounded iteration, returns size
// size_t XXXKlass::oop_oop_iterate_bounded(oop, closure, memregion, klute)
template <typename OopClosureType>
class OopOopIterateDispatchBoundedReturnSize : public DispatchBase {
  typedef size_t (*FunctionType) (oop obj, OopClosureType* cl, MemRegion mr, KlassLUTEntry klute);

  struct Table {

    FunctionType _function [Klass::KLASS_KIND_COUNT];

    template <typename KlassType, typename OopType, bool compact_headers>
    static size_t invoke(oop obj, OopClosureType* cl, MemRegion mr, KlassLUTEntry klute) {
      KlassType::template oop_oop_iterate_bounded<OopType> (obj, cl, mr, klute);
      return calculate_size_for_object_fast<KlassType, OopType, compact_headers>(klute, obj);
    }

    template <class KlassType, class OopType>
    static size_t invoke(oop obj, OopClosureType* cl, MemRegion mr, KlassLUTEntry klute) {
      KlassType::template oop_oop_iterate_bounded<OopType> (obj, cl, mr, klute);
      return obj->size();
    }

    template <typename KlassType>
    static size_t init_and_execute (oop obj, OopClosureType* cl, MemRegion mr, KlassLUTEntry klute) {
      return OopOopIterateDispatchBoundedReturnSize<OopClosureType>::_table.resolve_and_execute<KlassType> (obj, cl, mr, klute);
    }

    template <typename KlassType>
    void set_init_function() {
      _function[KlassType::Kind] = &init_and_execute<KlassType>;
    }

    template <typename KlassType>
    size_t resolve_and_execute (oop obj, OopClosureType* cl, MemRegion mr, KlassLUTEntry klute) {
      resolve<KlassType>();
      return _function[KlassType::Kind] (obj, cl, mr, klute);
    }

    template <typename KlassType>
    void resolve() {
      if (should_use_slowpath_getsize()) {
        if (UseCompressedOops) {
          _function[KlassType::Kind] = &invoke<KlassType, narrowOop>;
        } else {
          _function[KlassType::Kind] = &invoke<KlassType, oop>;
        }
      } else {
        if (UseCompressedOops) {
          if (UseCompactObjectHeaders) {
            _function[KlassType::Kind] = &invoke<KlassType, narrowOop, true>;
          } else {
            _function[KlassType::Kind] = &invoke<KlassType, narrowOop, false>;
          }
        } else {
          if (UseCompactObjectHeaders) {
            _function[KlassType::Kind] = &invoke<KlassType, oop, true>;
          } else {
            _function[KlassType::Kind] = &invoke<KlassType, oop, false>;
          }
        }
      }
    }

    Table() {
      set_init_function<InstanceKlass>();
      set_init_function<InstanceRefKlass>();
      set_init_function<InstanceMirrorKlass>();
      set_init_function<InstanceClassLoaderKlass>();
      set_init_function<InstanceStackChunkKlass>();
      set_init_function<ObjArrayKlass>();
      set_init_function<TypeArrayKlass>();
    }

  };

  static Table _table;

public:

  static size_t invoke(oop obj, OopClosureType* cl, MemRegion mr, KlassLUTEntry klute) {
    const int slot = klute.kind();
    return _table._function[slot](obj, cl, mr, klute);
  }

};

template <typename OopClosureType>
typename OopOopIterateDispatchBoundedReturnSize<OopClosureType>::Table
OopOopIterateDispatchBoundedReturnSize<OopClosureType>::_table;

template <typename OopClosureType>
size_t OopIteratorClosureDispatch::oop_oop_iterate_bounded_size  (oop obj, OopClosureType* cl, MemRegion mr, KlassLUTEntry klute) {
  return OopOopIterateDispatchBoundedReturnSize<OopClosureType>::invoke(obj, cl, mr, klute);
}

#endif // SHARE_MEMORY_ITERATOR_INLINE_HPP

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


template <typename OopClosureType>
class OopOopIterateDispatch : public AllStatic {
private:
  typedef void (*FunctionType)(OopClosureType*, oop, Klass*);

  class Table {
  private:
    template <typename KlassType, typename T>
    static void oop_oop_iterate(OopClosureType* cl, oop obj, Klass* k) {
      ((KlassType*)k)->KlassType::template oop_oop_iterate<T>(obj, cl);
    }

    template <typename KlassType>
    static void init(OopClosureType* cl, oop obj, Klass* k) {
      OopOopIterateDispatch<OopClosureType>::_table.set_resolve_function_and_execute<KlassType>(cl, obj, k);
    }

    template <typename KlassType>
    void set_init_function() {
      _function[KlassType::Kind] = &init<KlassType>;
    }

    template <typename KlassType>
    void set_resolve_function() {
      // Size requirement to prevent word tearing
      // when functions pointers are updated.
      STATIC_ASSERT(sizeof(_function[0]) == sizeof(void*));
      if (UseCompressedOops) {
        _function[KlassType::Kind] = &oop_oop_iterate<KlassType, narrowOop>;
      } else {
        _function[KlassType::Kind] = &oop_oop_iterate<KlassType, oop>;
      }
    }

    template <typename KlassType>
    void set_resolve_function_and_execute(OopClosureType* cl, oop obj, Klass* k) {
      set_resolve_function<KlassType>();
      _function[KlassType::Kind](cl, obj, k);
    }

  public:
    FunctionType _function[Klass::KLASS_KIND_COUNT];

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

  static FunctionType function(Klass* klass) {
    return _table._function[klass->kind()];
  }
};

template <typename OopClosureType>
typename OopOopIterateDispatch<OopClosureType>::Table OopOopIterateDispatch<OopClosureType>::_table;


template <typename OopClosureType>
class OopOopIterateBoundedDispatch {
private:
  typedef void (*FunctionType)(OopClosureType*, oop, Klass*, MemRegion);

  class Table {
  private:
    template <typename KlassType, typename T>
    static void oop_oop_iterate_bounded(OopClosureType* cl, oop obj, Klass* k, MemRegion mr) {
      ((KlassType*)k)->KlassType::template oop_oop_iterate_bounded<T>(obj, cl, mr);
    }

    template <typename KlassType>
    static void init(OopClosureType* cl, oop obj, Klass* k, MemRegion mr) {
      OopOopIterateBoundedDispatch<OopClosureType>::_table.set_resolve_function_and_execute<KlassType>(cl, obj, k, mr);
    }

    template <typename KlassType>
    void set_init_function() {
      _function[KlassType::Kind] = &init<KlassType>;
    }

    template <typename KlassType>
    void set_resolve_function() {
      if (UseCompressedOops) {
        _function[KlassType::Kind] = &oop_oop_iterate_bounded<KlassType, narrowOop>;
      } else {
        _function[KlassType::Kind] = &oop_oop_iterate_bounded<KlassType, oop>;
      }
    }

    template <typename KlassType>
    void set_resolve_function_and_execute(OopClosureType* cl, oop obj, Klass* k, MemRegion mr) {
      set_resolve_function<KlassType>();
      _function[KlassType::Kind](cl, obj, k, mr);
    }

  public:
    FunctionType _function[Klass::KLASS_KIND_COUNT];

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

  static FunctionType function(Klass* klass) {
    return _table._function[klass->kind()];
  }
};

template <typename OopClosureType>
typename OopOopIterateBoundedDispatch<OopClosureType>::Table OopOopIterateBoundedDispatch<OopClosureType>::_table;


template <typename OopClosureType>
class OopOopIterateBackwardsDispatch {
private:
  typedef void (*FunctionType)(OopClosureType*, oop, Klass*);

  class Table {
  private:
    template <typename KlassType, typename T>
    static void oop_oop_iterate_backwards(OopClosureType* cl, oop obj, Klass* k) {
      ((KlassType*)k)->KlassType::template oop_oop_iterate_reverse<T>(obj, cl);
    }

    template <typename KlassType>
    static void init(OopClosureType* cl, oop obj, Klass* k) {
      OopOopIterateBackwardsDispatch<OopClosureType>::_table.set_resolve_function_and_execute<KlassType>(cl, obj, k);
    }

    template <typename KlassType>
    void set_init_function() {
      _function[KlassType::Kind] = &init<KlassType>;
    }

    template <typename KlassType>
    void set_resolve_function() {
      if (UseCompressedOops) {
        _function[KlassType::Kind] = &oop_oop_iterate_backwards<KlassType, narrowOop>;
      } else {
        _function[KlassType::Kind] = &oop_oop_iterate_backwards<KlassType, oop>;
      }
    }

    template <typename KlassType>
    void set_resolve_function_and_execute(OopClosureType* cl, oop obj, Klass* k) {
      set_resolve_function<KlassType>();
      _function[KlassType::Kind](cl, obj, k);
    }

  public:
    FunctionType _function[Klass::KLASS_KIND_COUNT];

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

  static FunctionType function(Klass* klass) {
    return _table._function[klass->kind()];
  }
};

template <typename OopClosureType>
typename OopOopIterateBackwardsDispatch<OopClosureType>::Table OopOopIterateBackwardsDispatch<OopClosureType>::_table;


template <typename OopClosureType>
void OopIteratorClosureDispatch::oop_oop_iterate(OopClosureType* cl, oop obj, Klass* klass) {
  OopOopIterateDispatch<OopClosureType>::function(klass)(cl, obj, klass);
}

template <typename OopClosureType>
void OopIteratorClosureDispatch::oop_oop_iterate(OopClosureType* cl, oop obj, Klass* klass, MemRegion mr) {
  OopOopIterateBoundedDispatch<OopClosureType>::function(klass)(cl, obj, klass, mr);
}

template <typename OopClosureType>
void OopIteratorClosureDispatch::oop_oop_iterate_backwards(OopClosureType* cl, oop obj, Klass* klass) {
  OopOopIterateBackwardsDispatch<OopClosureType>::function(klass)(cl, obj, klass);
}

#endif // SHARE_MEMORY_ITERATOR_INLINE_HPP

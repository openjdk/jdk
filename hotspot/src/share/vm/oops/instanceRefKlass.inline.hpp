/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_OOPS_INSTANCEREFKLASS_INLINE_HPP
#define SHARE_VM_OOPS_INSTANCEREFKLASS_INLINE_HPP

#include "classfile/javaClasses.inline.hpp"
#include "gc/shared/referenceProcessor.hpp"
#include "logging/log.hpp"
#include "oops/instanceKlass.inline.hpp"
#include "oops/instanceRefKlass.hpp"
#include "oops/oop.inline.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

template <bool nv, typename T, class OopClosureType, class Contains>
void InstanceRefKlass::do_referent(oop obj, OopClosureType* closure, Contains& contains) {
  T* referent_addr = (T*)java_lang_ref_Reference::referent_addr(obj);
  if (contains(referent_addr)) {
    Devirtualizer<nv>::do_oop(closure, referent_addr);
  }
}

template <bool nv, typename T, class OopClosureType, class Contains>
void InstanceRefKlass::do_next(oop obj, OopClosureType* closure, Contains& contains) {
  T* next_addr = (T*)java_lang_ref_Reference::next_addr(obj);
  if (contains(next_addr)) {
    Devirtualizer<nv>::do_oop(closure, next_addr);
  }
}

template <bool nv, typename T, class OopClosureType, class Contains>
void InstanceRefKlass::do_discovered(oop obj, OopClosureType* closure, Contains& contains) {
  T* discovered_addr = (T*)java_lang_ref_Reference::discovered_addr(obj);
  if (contains(discovered_addr)) {
    Devirtualizer<nv>::do_oop(closure, discovered_addr);
  }
}

template <typename T, class OopClosureType>
bool InstanceRefKlass::try_discover(oop obj, ReferenceType type, OopClosureType* closure) {
  ReferenceProcessor* rp = closure->ref_processor();
  if (rp != NULL) {
    T referent_oop = oopDesc::load_heap_oop((T*)java_lang_ref_Reference::referent_addr(obj));
    if (!oopDesc::is_null(referent_oop)) {
      oop referent = oopDesc::decode_heap_oop_not_null(referent_oop);
      if (!referent->is_gc_marked()) {
        // Only try to discover if not yet marked.
        return rp->discover_reference(obj, type);
      }
    }
  }
  return false;
}

template <bool nv, typename T, class OopClosureType, class Contains>
void InstanceRefKlass::oop_oop_iterate_discovery(oop obj, ReferenceType type, OopClosureType* closure, Contains& contains) {
  log_develop_trace(gc, ref)("Process reference with discovery " PTR_FORMAT, p2i(obj));

  // Special case for some closures.
  if (closure->apply_to_weak_ref_discovered_field()) {
    do_discovered<nv, T>(obj, closure, contains);
  }

  // Try to discover reference and return if it succeeds.
  if (try_discover<T>(obj, type, closure)) {
    return;
  }

  // Treat referent as normal oop.
  do_referent<nv, T>(obj, closure, contains);

  // Treat discovered as normal oop, if ref is not "active" (next non-NULL).
  T next_oop  = oopDesc::load_heap_oop((T*)java_lang_ref_Reference::next_addr(obj));
  if (!oopDesc::is_null(next_oop)) {
    do_discovered<nv, T>(obj, closure, contains);
  }

  // Treat next as normal oop.
  do_next<nv, T>(obj, closure, contains);
}

template <bool nv, typename T, class OopClosureType, class Contains>
void InstanceRefKlass::oop_oop_iterate_fields(oop obj, OopClosureType* closure, Contains& contains) {
  do_referent<nv, T>(obj, closure, contains);
  do_discovered<nv, T>(obj, closure, contains);
  do_next<nv, T>(obj, closure, contains);

  trace_reference_gc("InstanceRefKlass::oop_oop_iterate_fields()",
                     obj,
                     (T*)java_lang_ref_Reference::referent_addr(obj),
                     (T*)java_lang_ref_Reference::next_addr(obj),
                     (T*)java_lang_ref_Reference::discovered_addr(obj));
}

template <bool nv, typename T, class OopClosureType, class Contains>
void InstanceRefKlass::oop_oop_iterate_ref_processing_specialized(oop obj, OopClosureType* closure, Contains& contains) {
  switch (closure->reference_iteration_mode()) {
    case ExtendedOopClosure::DO_DISCOVERY:
      oop_oop_iterate_discovery<nv, T>(obj, reference_type(), closure, contains);
      break;
    case ExtendedOopClosure::DO_FIELDS:
      oop_oop_iterate_fields<nv, T>(obj, closure, contains);
      break;
    default:
      ShouldNotReachHere();
  }
}

class AlwaysContains {
 public:
  template <typename T> bool operator()(T* p) const { return true; }
};

template <bool nv, class OopClosureType>
void InstanceRefKlass::oop_oop_iterate_ref_processing(oop obj, OopClosureType* closure) {
  AlwaysContains always_contains;
  if (UseCompressedOops) {
    oop_oop_iterate_ref_processing_specialized<nv, narrowOop>(obj, closure, always_contains);
  } else {
    oop_oop_iterate_ref_processing_specialized<nv, oop>(obj, closure, always_contains);
  }
}

class MrContains {
  const MemRegion _mr;
 public:
  MrContains(MemRegion mr) : _mr(mr) {}
  template <typename T> bool operator()(T* p) const { return _mr.contains(p); }
};

template <bool nv, class OopClosureType>
void InstanceRefKlass::oop_oop_iterate_ref_processing_bounded(oop obj, OopClosureType* closure, MemRegion mr) {
  const MrContains contains(mr);
  if (UseCompressedOops) {
    oop_oop_iterate_ref_processing_specialized<nv, narrowOop>(obj, closure, contains);
  } else {
    oop_oop_iterate_ref_processing_specialized<nv, oop>(obj, closure, contains);
  }
}

template <bool nv, class OopClosureType>
void InstanceRefKlass::oop_oop_iterate(oop obj, OopClosureType* closure) {
  InstanceKlass::oop_oop_iterate<nv>(obj, closure);

  oop_oop_iterate_ref_processing<nv>(obj, closure);
}

#if INCLUDE_ALL_GCS
template <bool nv, class OopClosureType>
void InstanceRefKlass::oop_oop_iterate_reverse(oop obj, OopClosureType* closure) {
  InstanceKlass::oop_oop_iterate_reverse<nv>(obj, closure);

  oop_oop_iterate_ref_processing<nv>(obj, closure);
}
#endif // INCLUDE_ALL_GCS


template <bool nv, class OopClosureType>
void InstanceRefKlass::oop_oop_iterate_bounded(oop obj, OopClosureType* closure, MemRegion mr) {
  InstanceKlass::oop_oop_iterate_bounded<nv>(obj, closure, mr);

  oop_oop_iterate_ref_processing_bounded<nv>(obj, closure, mr);
}

#ifdef ASSERT
template <typename T>
void InstanceRefKlass::trace_reference_gc(const char *s, oop obj, T* referent_addr, T* next_addr, T* discovered_addr) {
  log_develop_trace(gc, ref)("%s obj " PTR_FORMAT, s, p2i(obj));
  log_develop_trace(gc, ref)("     referent_addr/* " PTR_FORMAT " / " PTR_FORMAT,
      p2i(referent_addr), p2i(referent_addr ? (address)oopDesc::load_decode_heap_oop(referent_addr) : NULL));
  log_develop_trace(gc, ref)("     next_addr/* " PTR_FORMAT " / " PTR_FORMAT,
      p2i(next_addr), p2i(next_addr ? (address)oopDesc::load_decode_heap_oop(next_addr) : NULL));
  log_develop_trace(gc, ref)("     discovered_addr/* " PTR_FORMAT " / " PTR_FORMAT,
      p2i(discovered_addr), p2i(discovered_addr ? (address)oopDesc::load_decode_heap_oop(discovered_addr) : NULL));
}
#endif

// Macro to define InstanceRefKlass::oop_oop_iterate for virtual/nonvirtual for
// all closures.  Macros calling macros above for each oop size.
#define ALL_INSTANCE_REF_KLASS_OOP_OOP_ITERATE_DEFN(OopClosureType, nv_suffix)  \
  OOP_OOP_ITERATE_DEFN(          InstanceRefKlass, OopClosureType, nv_suffix)   \
  OOP_OOP_ITERATE_DEFN_BOUNDED(  InstanceRefKlass, OopClosureType, nv_suffix)   \
  OOP_OOP_ITERATE_DEFN_BACKWARDS(InstanceRefKlass, OopClosureType, nv_suffix)

#endif // SHARE_VM_OOPS_INSTANCEREFKLASS_INLINE_HPP

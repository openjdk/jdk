/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_OOPS_INSTANCEKLASS_INLINE_HPP
#define SHARE_VM_OOPS_INSTANCEKLASS_INLINE_HPP

#include "memory/iterator.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/klass.hpp"
#include "oops/oop.inline.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

// The iteration over the oops in objects is a hot path in the GC code.
// By force inlining the following functions, we get similar GC performance
// as the previous macro based implementation.
#ifdef TARGET_COMPILER_visCPP
#define INLINE __forceinline
#elif defined(TARGET_COMPILER_sparcWorks)
#define INLINE __attribute__((always_inline))
#else
#define INLINE inline
#endif

template <bool nv, typename T, class OopClosureType>
INLINE void InstanceKlass::oop_oop_iterate_oop_map(OopMapBlock* map, oop obj, OopClosureType* closure) {
  T* p         = (T*)obj->obj_field_addr<T>(map->offset());
  T* const end = p + map->count();

  for (; p < end; ++p) {
    Devirtualizer<nv>::do_oop(closure, p);
  }
}

#if INCLUDE_ALL_GCS
template <bool nv, typename T, class OopClosureType>
INLINE void InstanceKlass::oop_oop_iterate_oop_map_reverse(OopMapBlock* map, oop obj, OopClosureType* closure) {
  T* const start = (T*)obj->obj_field_addr<T>(map->offset());
  T*       p     = start + map->count();

  while (start < p) {
    --p;
    Devirtualizer<nv>::do_oop(closure, p);
  }
}
#endif

template <bool nv, typename T, class OopClosureType>
INLINE void InstanceKlass::oop_oop_iterate_oop_map_bounded(OopMapBlock* map, oop obj, OopClosureType* closure, MemRegion mr) {
  T* p   = (T*)obj->obj_field_addr<T>(map->offset());
  T* end = p + map->count();

  T* const l   = (T*)mr.start();
  T* const h   = (T*)mr.end();
  assert(mask_bits((intptr_t)l, sizeof(T)-1) == 0 &&
         mask_bits((intptr_t)h, sizeof(T)-1) == 0,
         "bounded region must be properly aligned");

  if (p < l) {
    p = l;
  }
  if (end > h) {
    end = h;
  }

  for (;p < end; ++p) {
    Devirtualizer<nv>::do_oop(closure, p);
  }
}

template <bool nv, typename T, class OopClosureType>
INLINE void InstanceKlass::oop_oop_iterate_oop_maps_specialized(oop obj, OopClosureType* closure) {
  OopMapBlock* map           = start_of_nonstatic_oop_maps();
  OopMapBlock* const end_map = map + nonstatic_oop_map_count();

  for (; map < end_map; ++map) {
    oop_oop_iterate_oop_map<nv, T>(map, obj, closure);
  }
}

#if INCLUDE_ALL_GCS
template <bool nv, typename T, class OopClosureType>
INLINE void InstanceKlass::oop_oop_iterate_oop_maps_specialized_reverse(oop obj, OopClosureType* closure) {
  OopMapBlock* const start_map = start_of_nonstatic_oop_maps();
  OopMapBlock* map             = start_map + nonstatic_oop_map_count();

  while (start_map < map) {
    --map;
    oop_oop_iterate_oop_map_reverse<nv, T>(map, obj, closure);
  }
}
#endif

template <bool nv, typename T, class OopClosureType>
INLINE void InstanceKlass::oop_oop_iterate_oop_maps_specialized_bounded(oop obj, OopClosureType* closure, MemRegion mr) {
  OopMapBlock* map           = start_of_nonstatic_oop_maps();
  OopMapBlock* const end_map = map + nonstatic_oop_map_count();

  for (;map < end_map; ++map) {
    oop_oop_iterate_oop_map_bounded<nv, T>(map, obj, closure, mr);
  }
}

template <bool nv, class OopClosureType>
INLINE void InstanceKlass::oop_oop_iterate_oop_maps(oop obj, OopClosureType* closure) {
  if (UseCompressedOops) {
    oop_oop_iterate_oop_maps_specialized<nv, narrowOop>(obj, closure);
  } else {
    oop_oop_iterate_oop_maps_specialized<nv, oop>(obj, closure);
  }
}

#if INCLUDE_ALL_GCS
template <bool nv, class OopClosureType>
INLINE void InstanceKlass::oop_oop_iterate_oop_maps_reverse(oop obj, OopClosureType* closure) {
  if (UseCompressedOops) {
    oop_oop_iterate_oop_maps_specialized_reverse<nv, narrowOop>(obj, closure);
  } else {
    oop_oop_iterate_oop_maps_specialized_reverse<nv, oop>(obj, closure);
  }
}
#endif

template <bool nv, class OopClosureType>
INLINE void InstanceKlass::oop_oop_iterate_oop_maps_bounded(oop obj, OopClosureType* closure, MemRegion mr) {
  if (UseCompressedOops) {
    oop_oop_iterate_oop_maps_specialized_bounded<nv, narrowOop>(obj, closure, mr);
  } else {
    oop_oop_iterate_oop_maps_specialized_bounded<nv, oop>(obj, closure, mr);
  }
}

template <bool nv, class OopClosureType>
INLINE int InstanceKlass::oop_oop_iterate(oop obj, OopClosureType* closure) {
  if (Devirtualizer<nv>::do_metadata(closure)) {
    Devirtualizer<nv>::do_klass(closure, this);
  }

  oop_oop_iterate_oop_maps<nv>(obj, closure);

  return size_helper();
}

#if INCLUDE_ALL_GCS
template <bool nv, class OopClosureType>
INLINE int InstanceKlass::oop_oop_iterate_reverse(oop obj, OopClosureType* closure) {
  assert(!Devirtualizer<nv>::do_metadata(closure),
      "Code to handle metadata is not implemented");

  oop_oop_iterate_oop_maps_reverse<nv>(obj, closure);

  return size_helper();
}
#endif

template <bool nv, class OopClosureType>
INLINE int InstanceKlass::oop_oop_iterate_bounded(oop obj, OopClosureType* closure, MemRegion mr) {
  if (Devirtualizer<nv>::do_metadata(closure)) {
    if (mr.contains(obj)) {
      Devirtualizer<nv>::do_klass(closure, this);
    }
  }

  oop_oop_iterate_oop_maps_bounded<nv>(obj, closure, mr);

  return size_helper();
}

#undef INLINE

#define ALL_INSTANCE_KLASS_OOP_OOP_ITERATE_DEFN(OopClosureType, nv_suffix)  \
  OOP_OOP_ITERATE_DEFN(          InstanceKlass, OopClosureType, nv_suffix)  \
  OOP_OOP_ITERATE_DEFN_BOUNDED(  InstanceKlass, OopClosureType, nv_suffix)  \
  OOP_OOP_ITERATE_DEFN_BACKWARDS(InstanceKlass, OopClosureType, nv_suffix)

#endif // SHARE_VM_OOPS_INSTANCEKLASS_INLINE_HPP

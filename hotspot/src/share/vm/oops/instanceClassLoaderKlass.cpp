/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "classfile/javaClasses.hpp"
#include "classfile/systemDictionary.hpp"
#include "gc_implementation/shared/markSweep.inline.hpp"
#include "gc_interface/collectedHeap.inline.hpp"
#include "memory/genOopClosures.inline.hpp"
#include "memory/iterator.inline.hpp"
#include "memory/oopFactory.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/instanceClassLoaderKlass.hpp"
#include "oops/instanceMirrorKlass.hpp"
#include "oops/instanceOop.hpp"
#include "oops/oop.inline.hpp"
#include "oops/symbol.hpp"
#include "runtime/handles.inline.hpp"
#include "utilities/macros.hpp"
#if INCLUDE_ALL_GCS
#include "gc_implementation/parNew/parOopClosures.inline.hpp"
#include "gc_implementation/parallelScavenge/psPromotionManager.inline.hpp"
#include "gc_implementation/parallelScavenge/psScavenge.inline.hpp"
#include "oops/oop.pcgc.inline.hpp"
#endif // INCLUDE_ALL_GCS

// Macro to define InstanceClassLoaderKlass::oop_oop_iterate for virtual/nonvirtual for
// all closures.  Macros calling macros above for each oop size.
// Since ClassLoader objects have only a pointer to the loader_data, they are not
// compressed nor does the pointer move.

#define InstanceClassLoaderKlass_OOP_OOP_ITERATE_DEFN(OopClosureType, nv_suffix)\
                                                                                \
int InstanceClassLoaderKlass::                                                  \
oop_oop_iterate##nv_suffix(oop obj, OopClosureType* closure) {                  \
  /* Get size before changing pointers */                                       \
  SpecializationStats::record_iterate_call##nv_suffix(SpecializationStats::irk);\
  int size = InstanceKlass::oop_oop_iterate##nv_suffix(obj, closure);           \
                                                                                \
  if_do_metadata_checked(closure, nv_suffix) {                                  \
    ClassLoaderData* cld = java_lang_ClassLoader::loader_data(obj);             \
    /* cld can be null if we have a non-registered class loader. */             \
    if (cld != NULL) {                                                          \
      closure->do_class_loader_data(cld);                                       \
    }                                                                           \
  }                                                                             \
                                                                                \
  return size;                                                                  \
}

#if INCLUDE_ALL_GCS
#define InstanceClassLoaderKlass_OOP_OOP_ITERATE_BACKWARDS_DEFN(OopClosureType, nv_suffix) \
                                                                                \
int InstanceClassLoaderKlass::                                                  \
oop_oop_iterate_backwards##nv_suffix(oop obj, OopClosureType* closure) {        \
  /* Get size before changing pointers */                                       \
  SpecializationStats::record_iterate_call##nv_suffix(SpecializationStats::irk);\
  int size = InstanceKlass::oop_oop_iterate_backwards##nv_suffix(obj, closure); \
  return size;                                                                  \
}
#endif // INCLUDE_ALL_GCS


#define InstanceClassLoaderKlass_OOP_OOP_ITERATE_DEFN_m(OopClosureType, nv_suffix)      \
                                                                                \
int InstanceClassLoaderKlass::                                                  \
oop_oop_iterate##nv_suffix##_m(oop obj,                                         \
                               OopClosureType* closure,                         \
                               MemRegion mr) {                                  \
  SpecializationStats::record_iterate_call##nv_suffix(SpecializationStats::irk);\
                                                                                \
  int size = InstanceKlass::oop_oop_iterate##nv_suffix##_m(obj, closure, mr);   \
                                                                                \
  if_do_metadata_checked(closure, nv_suffix) {                                  \
    if (mr.contains(obj)) {                                                     \
      ClassLoaderData* cld = java_lang_ClassLoader::loader_data(obj);           \
      /* cld can be null if we have a non-registered class loader. */           \
      if (cld != NULL) {                                                        \
        closure->do_class_loader_data(cld);                                     \
      }                                                                         \
    }                                                                           \
  }                                                                             \
                                                                                \
  return size;                                                                  \
}

ALL_OOP_OOP_ITERATE_CLOSURES_1(InstanceClassLoaderKlass_OOP_OOP_ITERATE_DEFN)
ALL_OOP_OOP_ITERATE_CLOSURES_2(InstanceClassLoaderKlass_OOP_OOP_ITERATE_DEFN)
#if INCLUDE_ALL_GCS
ALL_OOP_OOP_ITERATE_CLOSURES_1(InstanceClassLoaderKlass_OOP_OOP_ITERATE_BACKWARDS_DEFN)
ALL_OOP_OOP_ITERATE_CLOSURES_2(InstanceClassLoaderKlass_OOP_OOP_ITERATE_BACKWARDS_DEFN)
#endif // INCLUDE_ALL_GCS
ALL_OOP_OOP_ITERATE_CLOSURES_1(InstanceClassLoaderKlass_OOP_OOP_ITERATE_DEFN_m)
ALL_OOP_OOP_ITERATE_CLOSURES_2(InstanceClassLoaderKlass_OOP_OOP_ITERATE_DEFN_m)

void InstanceClassLoaderKlass::oop_follow_contents(oop obj) {
  InstanceKlass::oop_follow_contents(obj);
  ClassLoaderData * const loader_data = java_lang_ClassLoader::loader_data(obj);

  // We must NULL check here, since the class loader
  // can be found before the loader data has been set up.
  if(loader_data != NULL) {
    MarkSweep::follow_class_loader(loader_data);
  }
}

#if INCLUDE_ALL_GCS
void InstanceClassLoaderKlass::oop_follow_contents(ParCompactionManager* cm,
        oop obj) {
  InstanceKlass::oop_follow_contents(cm, obj);
  ClassLoaderData * const loader_data = java_lang_ClassLoader::loader_data(obj);
  if (loader_data != NULL) {
    PSParallelCompact::follow_class_loader(cm, loader_data);
  }
}

void InstanceClassLoaderKlass::oop_push_contents(PSPromotionManager* pm, oop obj) {
  InstanceKlass::oop_push_contents(pm, obj);

  // This is called by the young collector. It will already have taken care of
  // all class loader data. So, we don't have to follow the class loader ->
  // class loader data link.
}

int InstanceClassLoaderKlass::oop_update_pointers(ParCompactionManager* cm, oop obj) {
  InstanceKlass::oop_update_pointers(cm, obj);
  return size_helper();
}
#endif // INCLUDE_ALL_GCS


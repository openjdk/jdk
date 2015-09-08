/* Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_OOPS_INSTANCEMIRRORKLASS_INLINE_HPP
#define SHARE_VM_OOPS_INSTANCEMIRRORKLASS_INLINE_HPP

#include "classfile/javaClasses.hpp"
#include "oops/instanceKlass.inline.hpp"
#include "oops/instanceMirrorKlass.hpp"
#include "oops/klass.hpp"
#include "oops/oop.inline.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

template <bool nv, typename T, class OopClosureType>
void InstanceMirrorKlass::oop_oop_iterate_statics_specialized(oop obj, OopClosureType* closure) {
  T* p         = (T*)start_of_static_fields(obj);
  T* const end = p + java_lang_Class::static_oop_field_count(obj);

  for (; p < end; ++p) {
    Devirtualizer<nv>::do_oop(closure, p);
  }
}

template <bool nv, class OopClosureType>
void InstanceMirrorKlass::oop_oop_iterate_statics(oop obj, OopClosureType* closure) {
  if (UseCompressedOops) {
    oop_oop_iterate_statics_specialized<nv, narrowOop>(obj, closure);
  } else {
    oop_oop_iterate_statics_specialized<nv, oop>(obj, closure);
  }
}

template <bool nv, class OopClosureType>
void InstanceMirrorKlass::oop_oop_iterate(oop obj, OopClosureType* closure) {
  InstanceKlass::oop_oop_iterate<nv>(obj, closure);

  if (Devirtualizer<nv>::do_metadata(closure)) {
    Klass* klass = java_lang_Class::as_Klass(obj);
    // We'll get NULL for primitive mirrors.
    if (klass != NULL) {
      if (klass->oop_is_instance() && InstanceKlass::cast(klass)->is_anonymous()) {
        // An anonymous class doesn't have its own class loader, so when handling
        // the java mirror for an anonymous class we need to make sure its class
        // loader data is claimed, this is done by calling do_cld explicitly.
        // For non-anonymous classes the call to do_cld is made when the class
        // loader itself is handled.
        Devirtualizer<nv>::do_cld(closure, klass->class_loader_data());
      } else {
        Devirtualizer<nv>::do_klass(closure, klass);
      }
    } else {
      // If klass is NULL then this a mirror for a primitive type.
      // We don't have to follow them, since they are handled as strong
      // roots in Universe::oops_do.
      assert(java_lang_Class::is_primitive(obj), "Sanity check");
    }
  }

  oop_oop_iterate_statics<nv>(obj, closure);
}

#if INCLUDE_ALL_GCS
template <bool nv, class OopClosureType>
void InstanceMirrorKlass::oop_oop_iterate_reverse(oop obj, OopClosureType* closure) {
  InstanceKlass::oop_oop_iterate_reverse<nv>(obj, closure);

  InstanceMirrorKlass::oop_oop_iterate_statics<nv>(obj, closure);
}
#endif

template <bool nv, typename T, class OopClosureType>
void InstanceMirrorKlass::oop_oop_iterate_statics_specialized_bounded(oop obj,
                                                                     OopClosureType* closure,
                                                                     MemRegion mr) {
  T* p   = (T*)start_of_static_fields(obj);
  T* end = p + java_lang_Class::static_oop_field_count(obj);

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

template <bool nv, class OopClosureType>
void InstanceMirrorKlass::oop_oop_iterate_statics_bounded(oop obj, OopClosureType* closure, MemRegion mr) {
  if (UseCompressedOops) {
    oop_oop_iterate_statics_specialized_bounded<nv, narrowOop>(obj, closure, mr);
  } else {
    oop_oop_iterate_statics_specialized_bounded<nv, oop>(obj, closure, mr);
  }
}

template <bool nv, class OopClosureType>
void InstanceMirrorKlass::oop_oop_iterate_bounded(oop obj, OopClosureType* closure, MemRegion mr) {
  InstanceKlass::oop_oop_iterate_bounded<nv>(obj, closure, mr);

  if (Devirtualizer<nv>::do_metadata(closure)) {
    if (mr.contains(obj)) {
      Klass* klass = java_lang_Class::as_Klass(obj);
      // We'll get NULL for primitive mirrors.
      if (klass != NULL) {
        Devirtualizer<nv>::do_klass(closure, klass);
      }
    }
  }

  oop_oop_iterate_statics_bounded<nv>(obj, closure, mr);
}

#define ALL_INSTANCE_MIRROR_KLASS_OOP_OOP_ITERATE_DEFN(OopClosureType, nv_suffix)  \
  OOP_OOP_ITERATE_DEFN(          InstanceMirrorKlass, OopClosureType, nv_suffix)   \
  OOP_OOP_ITERATE_DEFN_BOUNDED(  InstanceMirrorKlass, OopClosureType, nv_suffix)   \
  OOP_OOP_ITERATE_DEFN_BACKWARDS(InstanceMirrorKlass, OopClosureType, nv_suffix)

#endif // SHARE_VM_OOPS_INSTANCEMIRRORKLASS_INLINE_HPP

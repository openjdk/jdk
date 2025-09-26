/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CDS_HEAPSHARED_INLINE_HPP
#define SHARE_CDS_HEAPSHARED_INLINE_HPP

#include "cds/heapShared.hpp"

#include "cds/aotReferenceObjSupport.hpp"
#include "cds/regeneratedClasses.hpp"
#include "classfile/javaClasses.hpp"
#include "utilities/macros.hpp"

#if INCLUDE_CDS_JAVA_HEAP

// Keep the knowledge about which objects have what metadata in one single place
template <typename T>
void HeapShared::do_metadata_offsets(oop src_obj, T callback) {
  if (java_lang_Class::is_instance(src_obj)) {
    callback(java_lang_Class::klass_offset());
    callback(java_lang_Class::array_klass_offset());
  } else if (java_lang_invoke_ResolvedMethodName::is_instance(src_obj)) {
    callback(java_lang_invoke_ResolvedMethodName::vmtarget_offset());
  }
}

// When loading class mirrors, the klass field is already set up correctly since
// the allocation. Therefore, it should not be fixed up
inline void HeapShared::remap_loaded_metadata(oop src_obj, bool skip_mirror_klass) {
  do_metadata_offsets(src_obj, [&](int offset) {
    if (skip_mirror_klass &&
        java_lang_Class::is_instance(src_obj) &&
        offset == java_lang_Class::klass_offset()) {
      // The klass field of the class mirror is already set up since the allocation,
      // hence "fixing" it here will only mess it up. Doing so is okay before GC can
      // run as long as it is fixed up after, but when GC can run, it is not a good
      // idea to patch this field and then fix it back again.
      return;
    }
    Metadata* metadata = src_obj->metadata_field(offset);
    if (metadata != nullptr) {
      metadata = (Metadata*)(address(metadata) + AOTMetaspace::relocation_delta());
      src_obj->metadata_field_put(offset, metadata);
    }
  });
}

inline oop HeapShared::maybe_remap_referent(bool is_java_lang_ref, size_t field_offset, oop referent) {
  if (referent == nullptr) {
    return nullptr;
  }

  if (is_java_lang_ref && AOTReferenceObjSupport::skip_field((int)field_offset)) {
    return nullptr;
  }

  if (java_lang_Class::is_instance(referent)) {
    Klass* k = java_lang_Class::as_Klass(referent);
    if (RegeneratedClasses::has_been_regenerated(k)) {
      referent = RegeneratedClasses::get_regenerated_object(k)->java_mirror();
    }
    // When the source object points to a "real" mirror, the buffered object should point
    // to the "scratch" mirror, which has all unarchivable fields scrubbed (to be reinstated
    // at run time).
    referent = HeapShared::scratch_java_mirror(referent);
    assert(referent != nullptr, "must be");
  }

  return referent;
}

#endif // INCLUDE_CDS_JAVA_HEAP

#endif // SHARE_CDS_HEAPSHARED_INLINE_HPP

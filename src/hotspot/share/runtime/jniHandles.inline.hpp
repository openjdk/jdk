/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_RUNTIME_JNIHANDLES_INLINE_HPP
#define SHARE_RUNTIME_JNIHANDLES_INLINE_HPP

#include "runtime/jniHandles.hpp"

#include "oops/access.inline.hpp"
#include "oops/oop.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

inline bool JNIHandles::is_tagged_with(jobject handle, TypeTag tag) {
  return (reinterpret_cast<uintptr_t>(handle) & tag_mask) == tag;
}

inline bool JNIHandles::is_local_tagged(jobject handle) {
  return is_tagged_with(handle, TypeTag::local);
}

inline bool JNIHandles::is_weak_global_tagged(jobject handle) {
  return is_tagged_with(handle, TypeTag::weak_global);
}

inline bool JNIHandles::is_global_tagged(jobject handle) {
  return is_tagged_with(handle, TypeTag::global);
}

inline oop* JNIHandles::local_ptr(jobject handle) {
  assert(is_local_tagged(handle), "precondition");
  STATIC_ASSERT(TypeTag::local == 0);
  return reinterpret_cast<oop*>(handle);
}

inline oop* JNIHandles::global_ptr(jobject handle) {
  assert(is_global_tagged(handle), "precondition");
  char* ptr = reinterpret_cast<char*>(handle) - TypeTag::global;
  return reinterpret_cast<oop*>(ptr);
}

inline oop* JNIHandles::weak_global_ptr(jweak handle) {
  assert(is_weak_global_tagged(handle), "precondition");
  char* ptr = reinterpret_cast<char*>(handle) - TypeTag::weak_global;
  return reinterpret_cast<oop*>(ptr);
}

// external_guard is true if called from resolve_external_guard.
template <DecoratorSet decorators, bool external_guard>
inline oop JNIHandles::resolve_impl(jobject handle) {
  assert(handle != NULL, "precondition");
  assert(!current_thread_in_native(), "must not be in native");
  oop result;
  if (is_weak_global_tagged(handle)) {       // Unlikely
    result = NativeAccess<ON_PHANTOM_OOP_REF|decorators>::oop_load(weak_global_ptr(handle));
  } else if (is_global_tagged(handle)) {
    result = NativeAccess<decorators>::oop_load(global_ptr(handle));
    // Construction of jobjects canonicalize a null value into a null
    // jobject, so for non-jweak the pointee should never be null.
    assert(external_guard || result != NULL, "Invalid JNI handle");
  } else {
    result = *local_ptr(handle);
    // Construction of jobjects canonicalize a null value into a null
    // jobject, so for non-jweak the pointee should never be null.
    assert(external_guard || result != NULL, "Invalid JNI handle");
  }
  return result;
}

inline oop JNIHandles::resolve(jobject handle) {
  oop result = NULL;
  if (handle != NULL) {
    result = resolve_impl<DECORATORS_NONE, false /* external_guard */>(handle);
  }
  return result;
}

inline oop JNIHandles::resolve_no_keepalive(jobject handle) {
  oop result = NULL;
  if (handle != NULL) {
    result = resolve_impl<AS_NO_KEEPALIVE, false /* external_guard */>(handle);
  }
  return result;
}

inline bool JNIHandles::is_same_object(jobject handle1, jobject handle2) {
  oop obj1 = resolve_no_keepalive(handle1);
  oop obj2 = resolve_no_keepalive(handle2);
  return obj1 == obj2;
}

inline oop JNIHandles::resolve_non_null(jobject handle) {
  assert(handle != NULL, "JNI handle should not be null");
  oop result = resolve_impl<DECORATORS_NONE, false /* external_guard */>(handle);
  assert(result != NULL, "NULL read from jni handle");
  return result;
}

inline void JNIHandles::destroy_local(jobject handle) {
  if (handle != NULL) {
    *local_ptr(handle) = NULL;
  }
}

#endif // SHARE_RUNTIME_JNIHANDLES_INLINE_HPP

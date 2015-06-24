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

#ifndef SHARE_VM_CLASSFILE_JAVACLASSES_INLINE_HPP
#define SHARE_VM_CLASSFILE_JAVACLASSES_INLINE_HPP

#include "classfile/javaClasses.hpp"
#include "oops/oop.inline.hpp"
#include "oops/oopsHierarchy.hpp"

inline void java_lang_invoke_CallSite::set_target_volatile(oop site, oop target) {
  site->obj_field_put_volatile(_target_offset, target);
}

inline oop  java_lang_invoke_CallSite::target(oop site) {
  return site->obj_field(_target_offset);
}

inline void java_lang_invoke_CallSite::set_target(oop site, oop target) {
  site->obj_field_put(_target_offset, target);
}

inline bool java_lang_String::is_instance_inlined(oop obj) {
  return obj != NULL && obj->klass() == SystemDictionary::String_klass();
}

inline bool java_lang_invoke_CallSite::is_instance(oop obj) {
  return obj != NULL && is_subclass(obj->klass());
}

inline bool java_lang_invoke_MethodHandleNatives_CallSiteContext::is_instance(oop obj) {
  return obj != NULL && is_subclass(obj->klass());
}

inline bool java_lang_invoke_MemberName::is_instance(oop obj) {
  return obj != NULL && is_subclass(obj->klass());
}

inline bool java_lang_invoke_MethodType::is_instance(oop obj) {
  return obj != NULL && obj->klass() == SystemDictionary::MethodType_klass();
}

inline bool java_lang_invoke_MethodHandle::is_instance(oop obj) {
  return obj != NULL && is_subclass(obj->klass());
}

inline bool java_lang_Class::is_instance(oop obj) {
  return obj != NULL && obj->klass() == SystemDictionary::Class_klass();
}

inline bool java_lang_invoke_DirectMethodHandle::is_instance(oop obj) {
  return obj != NULL && is_subclass(obj->klass());
}

#endif // SHARE_VM_CLASSFILE_JAVACLASSES_INLINE_HPP

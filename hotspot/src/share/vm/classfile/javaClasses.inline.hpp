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

inline int Backtrace::merge_bci_and_version(int bci, int version) {
  // only store u2 for version, checking for overflow.
  if (version > USHRT_MAX || version < 0) version = USHRT_MAX;
  assert((jushort)bci == bci, "bci should be short");
  return build_int_from_shorts(version, bci);
}

inline int Backtrace::merge_mid_and_cpref(int mid, int cpref) {
  // only store u2 for mid and cpref, checking for overflow.
  assert((jushort)mid == mid, "mid should be short");
  assert((jushort)cpref == cpref, "cpref should be short");
  return build_int_from_shorts(cpref, mid);
}

inline int Backtrace::bci_at(unsigned int merged) {
  return extract_high_short_from_int(merged);
}

inline int Backtrace::version_at(unsigned int merged) {
  return extract_low_short_from_int(merged);
}

inline int Backtrace::mid_at(unsigned int merged) {
  return extract_high_short_from_int(merged);
}

inline int Backtrace::cpref_at(unsigned int merged) {
  return extract_low_short_from_int(merged);
}

inline int Backtrace::get_line_number(const methodHandle& method, int bci) {
  int line_number = 0;
  if (method->is_native()) {
    // Negative value different from -1 below, enabling Java code in
    // class java.lang.StackTraceElement to distinguish "native" from
    // "no LineNumberTable".  JDK tests for -2.
    line_number = -2;
  } else {
    // Returns -1 if no LineNumberTable, and otherwise actual line number
    line_number = method->line_number_from_bci(bci);
    if (line_number == -1 && ShowHiddenFrames) {
      line_number = bci + 1000000;
    }
  }
  return line_number;
}

/*
 * Returns the source file name of a given InstanceKlass and version
 */
inline Symbol* Backtrace::get_source_file_name(InstanceKlass* holder, int version) {
  // Find the specific ik version that contains this source_file_name_index
  // via the previous versions list, but use the current version's
  // constant pool to look it up.  The previous version's index has been
  // merged for the current constant pool.
  InstanceKlass* ik = holder->get_klass_version(version);
  // This version has been cleaned up.
  if (ik == NULL) return NULL;
  int source_file_name_index = ik->source_file_name_index();
  return (source_file_name_index == 0) ?
      (Symbol*)NULL : holder->constants()->symbol_at(source_file_name_index);
}

#endif // SHARE_VM_CLASSFILE_JAVACLASSES_INLINE_HPP

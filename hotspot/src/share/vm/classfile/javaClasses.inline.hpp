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

#ifndef SHARE_VM_CLASSFILE_JAVACLASSES_INLINE_HPP
#define SHARE_VM_CLASSFILE_JAVACLASSES_INLINE_HPP

#include "classfile/javaClasses.hpp"
#include "oops/oop.inline.hpp"
#include "oops/oopsHierarchy.hpp"

void java_lang_String::set_coder(oop string, jbyte coder) {
  assert(initialized && (coder_offset > 0), "Must be initialized");
  string->byte_field_put(coder_offset, coder);
}

void java_lang_String::set_value_raw(oop string, typeArrayOop buffer) {
  assert(initialized, "Must be initialized");
  string->obj_field_put_raw(value_offset, buffer);
}
void java_lang_String::set_value(oop string, typeArrayOop buffer) {
  assert(initialized && (value_offset > 0), "Must be initialized");
  string->obj_field_put(value_offset, (oop)buffer);
}
void java_lang_String::set_hash(oop string, unsigned int hash) {
  assert(initialized && (hash_offset > 0), "Must be initialized");
  string->int_field_put(hash_offset, hash);
}

// Accessors
typeArrayOop java_lang_String::value(oop java_string) {
  assert(initialized && (value_offset > 0), "Must be initialized");
  assert(is_instance(java_string), "must be java_string");
  return (typeArrayOop) java_string->obj_field(value_offset);
}
unsigned int java_lang_String::hash(oop java_string) {
  assert(initialized && (hash_offset > 0), "Must be initialized");
  assert(is_instance(java_string), "must be java_string");
  return java_string->int_field(hash_offset);
}
bool java_lang_String::is_latin1(oop java_string) {
  assert(initialized && (coder_offset > 0), "Must be initialized");
  assert(is_instance(java_string), "must be java_string");
  jbyte coder = java_string->byte_field(coder_offset);
  assert(CompactStrings || coder == CODER_UTF16, "Must be UTF16 without CompactStrings");
  return coder == CODER_LATIN1;
}
int java_lang_String::length(oop java_string) {
  assert(initialized, "Must be initialized");
  assert(is_instance(java_string), "must be java_string");
  typeArrayOop value_array = ((typeArrayOop)java_string->obj_field(value_offset));
  if (value_array == NULL) {
    return 0;
  }
  int arr_length = value_array->length();
  if (!is_latin1(java_string)) {
    assert((arr_length & 1) == 0, "should be even for UTF16 string");
    arr_length >>= 1; // convert number of bytes to number of elements
  }
  return arr_length;
}

bool java_lang_String::is_instance_inlined(oop obj) {
  return obj != NULL && obj->klass() == SystemDictionary::String_klass();
}

// Accessors
oop java_lang_ref_Reference::referent(oop ref) {
  return ref->obj_field(referent_offset);
}
void java_lang_ref_Reference::set_referent(oop ref, oop value) {
  ref->obj_field_put(referent_offset, value);
}
void java_lang_ref_Reference::set_referent_raw(oop ref, oop value) {
  ref->obj_field_put_raw(referent_offset, value);
}
HeapWord* java_lang_ref_Reference::referent_addr(oop ref) {
  return ref->obj_field_addr<HeapWord>(referent_offset);
}
oop java_lang_ref_Reference::next(oop ref) {
  return ref->obj_field(next_offset);
}
void java_lang_ref_Reference::set_next(oop ref, oop value) {
  ref->obj_field_put(next_offset, value);
}
void java_lang_ref_Reference::set_next_raw(oop ref, oop value) {
  ref->obj_field_put_raw(next_offset, value);
}
HeapWord* java_lang_ref_Reference::next_addr(oop ref) {
  return ref->obj_field_addr<HeapWord>(next_offset);
}
oop java_lang_ref_Reference::discovered(oop ref) {
  return ref->obj_field(discovered_offset);
}
void java_lang_ref_Reference::set_discovered(oop ref, oop value) {
  ref->obj_field_put(discovered_offset, value);
}
void java_lang_ref_Reference::set_discovered_raw(oop ref, oop value) {
  ref->obj_field_put_raw(discovered_offset, value);
}
HeapWord* java_lang_ref_Reference::discovered_addr(oop ref) {
  return ref->obj_field_addr<HeapWord>(discovered_offset);
}

inline void java_lang_invoke_CallSite::set_target_volatile(oop site, oop target) {
  site->obj_field_put_volatile(_target_offset, target);
}

inline oop  java_lang_invoke_CallSite::target(oop site) {
  return site->obj_field(_target_offset);
}

inline void java_lang_invoke_CallSite::set_target(oop site, oop target) {
  site->obj_field_put(_target_offset, target);
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

inline bool java_lang_Module::is_instance(oop obj) {
  return obj != NULL && obj->klass() == SystemDictionary::Module_klass();
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

inline Symbol* Backtrace::get_source_file_name(InstanceKlass* holder, int version) {
  // RedefineClasses() currently permits redefine operations to
  // happen in parallel using a "last one wins" philosophy. That
  // spec laxness allows the constant pool entry associated with
  // the source_file_name_index for any older constant pool version
  // to be unstable so we shouldn't try to use it.
  if (holder->constants()->version() != version) {
    return NULL;
  } else {
    return holder->source_file_name();
  }
}

#endif // SHARE_VM_CLASSFILE_JAVACLASSES_INLINE_HPP

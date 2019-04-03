/*
 * Copyright (c) 1997, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/altHashing.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "memory/heapShared.inline.hpp"
#include "memory/resourceArea.hpp"
#include "oops/access.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/verifyOopClosure.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/thread.inline.hpp"
#include "utilities/copy.hpp"

bool always_do_update_barrier = false;

void oopDesc::print_on(outputStream* st) const {
  klass()->oop_print_on(oop(this), st);
}

void oopDesc::print_address_on(outputStream* st) const {
  st->print("{" INTPTR_FORMAT "}", p2i(this));

}

void oopDesc::print()         { print_on(tty);         }

void oopDesc::print_address() { print_address_on(tty); }

char* oopDesc::print_string() {
  stringStream st;
  print_on(&st);
  return st.as_string();
}

void oopDesc::print_value() {
  print_value_on(tty);
}

char* oopDesc::print_value_string() {
  char buf[100];
  stringStream st(buf, sizeof(buf));
  print_value_on(&st);
  return st.as_string();
}

void oopDesc::print_value_on(outputStream* st) const {
  oop obj = oop(this);
  if (java_lang_String::is_instance(obj)) {
    java_lang_String::print(obj, st);
    print_address_on(st);
  } else {
    klass()->oop_print_value_on(obj, st);
  }
}


void oopDesc::verify_on(outputStream* st, oopDesc* oop_desc) {
  if (oop_desc != NULL) {
    oop_desc->klass()->oop_verify_on(oop_desc, st);
  }
}


void oopDesc::verify(oopDesc* oop_desc) {
  verify_on(tty, oop_desc);
}

intptr_t oopDesc::slow_identity_hash() {
  // slow case; we have to acquire the micro lock in order to locate the header
  Thread* THREAD = Thread::current();
  ResetNoHandleMark rnm; // Might be called from LEAF/QUICK ENTRY
  HandleMark hm(THREAD);
  Handle object(THREAD, this);
  return ObjectSynchronizer::identity_hash_value_for(object);
}

// used only for asserts and guarantees
bool oopDesc::is_oop(oop obj, bool ignore_mark_word) {
  if (!Universe::heap()->is_oop(obj)) {
    return false;
  }

  // Header verification: the mark is typically non-NULL. If we're
  // at a safepoint, it must not be null.
  // Outside of a safepoint, the header could be changing (for example,
  // another thread could be inflating a lock on this object).
  if (ignore_mark_word) {
    return true;
  }
  if (obj->mark_raw() != NULL) {
    return true;
  }
  return !SafepointSynchronize::is_at_safepoint();
}

// used only for asserts and guarantees
bool oopDesc::is_oop_or_null(oop obj, bool ignore_mark_word) {
  return obj == NULL ? true : is_oop(obj, ignore_mark_word);
}

#ifndef PRODUCT
#if INCLUDE_CDS_JAVA_HEAP
bool oopDesc::is_archived_object(oop p) {
  return HeapShared::is_archived_object(p);
}
#endif
#endif // PRODUCT

VerifyOopClosure VerifyOopClosure::verify_oop;

template <class T> void VerifyOopClosure::do_oop_work(T* p) {
  oop obj = RawAccess<>::oop_load(p);
  guarantee(oopDesc::is_oop_or_null(obj), "invalid oop: " INTPTR_FORMAT, p2i((oopDesc*) obj));
}

void VerifyOopClosure::do_oop(oop* p)       { VerifyOopClosure::do_oop_work(p); }
void VerifyOopClosure::do_oop(narrowOop* p) { VerifyOopClosure::do_oop_work(p); }

// type test operations that doesn't require inclusion of oop.inline.hpp.
bool oopDesc::is_instance_noinline()          const { return is_instance();            }
bool oopDesc::is_array_noinline()             const { return is_array();               }
bool oopDesc::is_objArray_noinline()          const { return is_objArray();            }
bool oopDesc::is_typeArray_noinline()         const { return is_typeArray();           }

bool oopDesc::has_klass_gap() {
  // Only has a klass gap when compressed class pointers are used.
  return UseCompressedClassPointers;
}

oop oopDesc::decode_oop_raw(narrowOop narrow_oop) {
  return (oop)(void*)( (uintptr_t)Universe::narrow_oop_base() +
                      ((uintptr_t)narrow_oop << Universe::narrow_oop_shift()));
}

void* oopDesc::load_klass_raw(oop obj) {
  if (UseCompressedClassPointers) {
    narrowKlass narrow_klass = *(obj->compressed_klass_addr());
    if (narrow_klass == 0) return NULL;
    return (void*)Klass::decode_klass_raw(narrow_klass);
  } else {
    return *(void**)(obj->klass_addr());
  }
}

void* oopDesc::load_oop_raw(oop obj, int offset) {
  uintptr_t addr = (uintptr_t)(void*)obj + (uint)offset;
  if (UseCompressedOops) {
    narrowOop narrow_oop = *(narrowOop*)addr;
    if (narrow_oop == 0) return NULL;
    return (void*)decode_oop_raw(narrow_oop);
  } else {
    return *(void**)addr;
  }
}

bool oopDesc::is_valid(oop obj) {
  if (!is_object_aligned(obj)) return false;
  if ((size_t)(oopDesc*)obj < os::min_page_size()) return false;

  // We need at least the mark and the klass word in the committed region.
  if (!os::is_readable_range(obj, (oopDesc*)obj + 1)) return false;
  if (!Universe::heap()->is_in(obj)) return false;

  Klass* k = (Klass*)load_klass_raw(obj);

  if (!os::is_readable_range(k, k + 1)) return false;
  return MetaspaceUtils::is_range_in_committed(k, k + 1);
}

oop oopDesc::oop_or_null(address addr) {
  if (is_valid(oop(addr))) {
    // We were just given an oop directly.
    return oop(addr);
  }

  // Try to find addr using block_start.
  HeapWord* p = Universe::heap()->block_start(addr);
  if (p != NULL && Universe::heap()->block_is_obj(p)) {
    if (!is_valid(oop(p))) return NULL;
    return oop(p);
  }

  // If we can't find it it just may mean that heap wasn't parsable.
  return NULL;
}

oop oopDesc::obj_field_acquire(int offset) const                      { return HeapAccess<MO_ACQUIRE>::oop_load_at(as_oop(), offset); }

void oopDesc::obj_field_put_raw(int offset, oop value)                { RawAccess<>::oop_store_at(as_oop(), offset, value); }
void oopDesc::release_obj_field_put(int offset, oop value)            { HeapAccess<MO_RELEASE>::oop_store_at(as_oop(), offset, value); }
void oopDesc::obj_field_put_volatile(int offset, oop value)           { HeapAccess<MO_SEQ_CST>::oop_store_at(as_oop(), offset, value); }

address oopDesc::address_field(int offset) const                      { return HeapAccess<>::load_at(as_oop(), offset); }
address oopDesc::address_field_acquire(int offset) const              { return HeapAccess<MO_ACQUIRE>::load_at(as_oop(), offset); }

void oopDesc::address_field_put(int offset, address value)            { HeapAccess<>::store_at(as_oop(), offset, value); }
void oopDesc::release_address_field_put(int offset, address value)    { HeapAccess<MO_RELEASE>::store_at(as_oop(), offset, value); }

Metadata* oopDesc::metadata_field(int offset) const                   { return HeapAccess<>::load_at(as_oop(), offset); }
Metadata* oopDesc::metadata_field_raw(int offset) const               { return RawAccess<>::load_at(as_oop(), offset); }
void oopDesc::metadata_field_put(int offset, Metadata* value)         { HeapAccess<>::store_at(as_oop(), offset, value); }

Metadata* oopDesc::metadata_field_acquire(int offset) const           { return HeapAccess<MO_ACQUIRE>::load_at(as_oop(), offset); }
void oopDesc::release_metadata_field_put(int offset, Metadata* value) { HeapAccess<MO_RELEASE>::store_at(as_oop(), offset, value); }

jbyte oopDesc::byte_field_acquire(int offset) const                   { return HeapAccess<MO_ACQUIRE>::load_at(as_oop(), offset); }
void oopDesc::release_byte_field_put(int offset, jbyte value)         { HeapAccess<MO_RELEASE>::store_at(as_oop(), offset, value); }

jchar oopDesc::char_field_acquire(int offset) const                   { return HeapAccess<MO_ACQUIRE>::load_at(as_oop(), offset); }
void oopDesc::release_char_field_put(int offset, jchar value)         { HeapAccess<MO_RELEASE>::store_at(as_oop(), offset, value); }

jboolean oopDesc::bool_field_acquire(int offset) const                { return HeapAccess<MO_ACQUIRE>::load_at(as_oop(), offset); }
void oopDesc::release_bool_field_put(int offset, jboolean value)      { HeapAccess<MO_RELEASE>::store_at(as_oop(), offset, jboolean(value & 1)); }

jint oopDesc::int_field_acquire(int offset) const                     { return HeapAccess<MO_ACQUIRE>::load_at(as_oop(), offset); }
void oopDesc::release_int_field_put(int offset, jint value)           { HeapAccess<MO_RELEASE>::store_at(as_oop(), offset, value); }

jshort oopDesc::short_field_acquire(int offset) const                 { return HeapAccess<MO_ACQUIRE>::load_at(as_oop(), offset); }
void oopDesc::release_short_field_put(int offset, jshort value)       { HeapAccess<MO_RELEASE>::store_at(as_oop(), offset, value); }

jlong oopDesc::long_field_acquire(int offset) const                   { return HeapAccess<MO_ACQUIRE>::load_at(as_oop(), offset); }
void oopDesc::release_long_field_put(int offset, jlong value)         { HeapAccess<MO_RELEASE>::store_at(as_oop(), offset, value); }

jfloat oopDesc::float_field_acquire(int offset) const                 { return HeapAccess<MO_ACQUIRE>::load_at(as_oop(), offset); }
void oopDesc::release_float_field_put(int offset, jfloat value)       { HeapAccess<MO_RELEASE>::store_at(as_oop(), offset, value); }

jdouble oopDesc::double_field_acquire(int offset) const               { return HeapAccess<MO_ACQUIRE>::load_at(as_oop(), offset); }
void oopDesc::release_double_field_put(int offset, jdouble value)     { HeapAccess<MO_RELEASE>::store_at(as_oop(), offset, value); }

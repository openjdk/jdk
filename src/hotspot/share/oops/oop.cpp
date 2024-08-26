/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "cds/cdsConfig.hpp"
#include "classfile/altHashing.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "gc/shared/collectedHeap.inline.hpp"
#include "gc/shared/gc_globals.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/access.inline.hpp"
#include "oops/compressedKlass.inline.hpp"
#include "oops/compressedOops.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/verifyOopClosure.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/synchronizer.hpp"
#include "utilities/macros.hpp"

void oopDesc::print_on(outputStream* st) const {
  if (*((juint*)this) == badHeapWordVal) {
    st->print_cr("BAD WORD");
  } else {
    klass()->oop_print_on(cast_to_oop(this), st);
  }
}

void oopDesc::print_address_on(outputStream* st) const {
  st->print("{" PTR_FORMAT "}", p2i(this));

}

void oopDesc::print_name_on(outputStream* st) const {
  if (*((juint*)this) == badHeapWordVal) {
    st->print_cr("BAD WORD");
  } else {
    st->print_cr("%s", klass()->external_name());
  }
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
  oop obj = const_cast<oopDesc*>(this);
  if (java_lang_String::is_instance(obj)) {
    java_lang_String::print(obj, st);
    print_address_on(st);
  } else {
    klass()->oop_print_value_on(obj, st);
  }
}


void oopDesc::verify_on(outputStream* st, oopDesc* oop_desc) {
  if (oop_desc != nullptr) {
    oop_desc->klass()->oop_verify_on(oop_desc, st);
  }
}


void oopDesc::verify(oopDesc* oop_desc) {
  verify_on(tty, oop_desc);
}

intptr_t oopDesc::slow_identity_hash() {
  // slow case; we have to acquire the micro lock in order to locate the header
  Thread* current = Thread::current();
  return ObjectSynchronizer::FastHashCode(current, this);
}

// used only for asserts and guarantees
bool oopDesc::is_oop(oop obj, bool ignore_mark_word) {
  if (!Universe::heap()->is_oop(obj)) {
    return false;
  }

  // Header verification: the mark is typically non-zero. If we're
  // at a safepoint, it must not be zero, except when using the new lightweight locking.
  // Outside of a safepoint, the header could be changing (for example,
  // another thread could be inflating a lock on this object).
  if (ignore_mark_word) {
    return true;
  }
  if (obj->mark().value() != 0) {
    return true;
  }
  return LockingMode == LM_LIGHTWEIGHT || !SafepointSynchronize::is_at_safepoint();
}

// used only for asserts and guarantees
bool oopDesc::is_oop_or_null(oop obj, bool ignore_mark_word) {
  return obj == nullptr ? true : is_oop(obj, ignore_mark_word);
}

VerifyOopClosure VerifyOopClosure::verify_oop;

template <class T> void VerifyOopClosure::do_oop_work(T* p) {
  oop obj = RawAccess<>::oop_load(p);
  guarantee(oopDesc::is_oop_or_null(obj), "invalid oop: " PTR_FORMAT, p2i(obj));
}

void VerifyOopClosure::do_oop(oop* p)       { VerifyOopClosure::do_oop_work(p); }
void VerifyOopClosure::do_oop(narrowOop* p) { VerifyOopClosure::do_oop_work(p); }

// type test operations that doesn't require inclusion of oop.inline.hpp.
bool oopDesc::is_instance_noinline()    const { return is_instance();    }
bool oopDesc::is_instanceRef_noinline() const { return is_instanceRef(); }
bool oopDesc::is_stackChunk_noinline()  const { return is_stackChunk();  }
bool oopDesc::is_array_noinline()       const { return is_array();       }
bool oopDesc::is_objArray_noinline()    const { return is_objArray();    }
bool oopDesc::is_typeArray_noinline()   const { return is_typeArray();   }

bool oopDesc::has_klass_gap() {
  // Only has a klass gap when compressed class pointers are used.
  return UseCompressedClassPointers;
}

#if INCLUDE_CDS_JAVA_HEAP
void oopDesc::set_narrow_klass(narrowKlass nk) {
  assert(CDSConfig::is_dumping_heap(), "Used by CDS only. Do not abuse!");
  assert(UseCompressedClassPointers, "must be");
  _metadata._compressed_klass = nk;
}
#endif

void* oopDesc::load_oop_raw(oop obj, int offset) {
  uintptr_t addr = (uintptr_t)(void*)obj + (uint)offset;
  if (UseCompressedOops) {
    narrowOop narrow_oop = *(narrowOop*)addr;
    if (CompressedOops::is_null(narrow_oop)) return nullptr;
    return (void*)CompressedOops::decode_raw(narrow_oop);
  } else {
    return *(void**)addr;
  }
}

oop oopDesc::obj_field_acquire(int offset) const                      { return HeapAccess<MO_ACQUIRE>::oop_load_at(as_oop(), offset); }

void oopDesc::obj_field_put_raw(int offset, oop value)                { assert(!(UseZGC && ZGenerational), "Generational ZGC must use store barriers");
                                                                        RawAccess<>::oop_store_at(as_oop(), offset, value); }
void oopDesc::release_obj_field_put(int offset, oop value)            { HeapAccess<MO_RELEASE>::oop_store_at(as_oop(), offset, value); }
void oopDesc::obj_field_put_volatile(int offset, oop value)           { HeapAccess<MO_SEQ_CST>::oop_store_at(as_oop(), offset, value); }

address oopDesc::address_field(int offset) const                      { return *field_addr<address>(offset); }
address oopDesc::address_field_acquire(int offset) const              { return Atomic::load_acquire(field_addr<address>(offset)); }

void oopDesc::address_field_put(int offset, address value)            { *field_addr<address>(offset) = value; }
void oopDesc::release_address_field_put(int offset, address value)    { Atomic::release_store(field_addr<address>(offset), value); }

Metadata* oopDesc::metadata_field(int offset) const                   { return *field_addr<Metadata*>(offset); }
void oopDesc::metadata_field_put(int offset, Metadata* value)         { *field_addr<Metadata*>(offset) = value; }

Metadata* oopDesc::metadata_field_acquire(int offset) const           { return Atomic::load_acquire(field_addr<Metadata*>(offset)); }
void oopDesc::release_metadata_field_put(int offset, Metadata* value) { Atomic::release_store(field_addr<Metadata*>(offset), value); }

jbyte oopDesc::byte_field_acquire(int offset) const                   { return Atomic::load_acquire(field_addr<jbyte>(offset)); }
void oopDesc::release_byte_field_put(int offset, jbyte value)         { Atomic::release_store(field_addr<jbyte>(offset), value); }

jchar oopDesc::char_field_acquire(int offset) const                   { return Atomic::load_acquire(field_addr<jchar>(offset)); }
void oopDesc::release_char_field_put(int offset, jchar value)         { Atomic::release_store(field_addr<jchar>(offset), value); }

jboolean oopDesc::bool_field_acquire(int offset) const                { return Atomic::load_acquire(field_addr<jboolean>(offset)); }
void oopDesc::release_bool_field_put(int offset, jboolean value)      { Atomic::release_store(field_addr<jboolean>(offset), jboolean(value & 1)); }

jint oopDesc::int_field_acquire(int offset) const                     { return Atomic::load_acquire(field_addr<jint>(offset)); }
void oopDesc::release_int_field_put(int offset, jint value)           { Atomic::release_store(field_addr<jint>(offset), value); }

jshort oopDesc::short_field_acquire(int offset) const                 { return Atomic::load_acquire(field_addr<jshort>(offset)); }
void oopDesc::release_short_field_put(int offset, jshort value)       { Atomic::release_store(field_addr<jshort>(offset), value); }

jlong oopDesc::long_field_acquire(int offset) const                   { return Atomic::load_acquire(field_addr<jlong>(offset)); }
void oopDesc::release_long_field_put(int offset, jlong value)         { Atomic::release_store(field_addr<jlong>(offset), value); }

jfloat oopDesc::float_field_acquire(int offset) const                 { return Atomic::load_acquire(field_addr<jfloat>(offset)); }
void oopDesc::release_float_field_put(int offset, jfloat value)       { Atomic::release_store(field_addr<jfloat>(offset), value); }

jdouble oopDesc::double_field_acquire(int offset) const               { return Atomic::load_acquire(field_addr<jdouble>(offset)); }
void oopDesc::release_double_field_put(int offset, jdouble value)     { Atomic::release_store(field_addr<jdouble>(offset), value); }

#ifdef ASSERT
bool oopDesc::size_might_change() {
  // UseParallelGC and UseG1GC can change the length field
  // of an "old copy" of an object array in the young gen so it indicates
  // the grey portion of an already copied array. This will cause the first
  // disjunct below to fail if the two comparands are computed across such
  // a concurrent change.
  return Universe::heap()->is_stw_gc_active() && is_objArray() && is_forwarded() && (UseParallelGC || UseG1GC);
}
#endif

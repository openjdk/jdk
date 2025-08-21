/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OOPS_OOP_INLINE_HPP
#define SHARE_OOPS_OOP_INLINE_HPP

#include "oops/oop.hpp"

#include "memory/iterator.inline.hpp"
#include "memory/universe.hpp"
#include "oops/access.inline.hpp"
#include "oops/arrayKlass.hpp"
#include "oops/arrayOop.hpp"
#include "oops/compressedKlass.inline.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/markWord.inline.hpp"
#include "oops/objLayout.inline.hpp"
#include "oops/oopsHierarchy.hpp"
#include "runtime/atomic.hpp"
#include "runtime/globals.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

// Implementation of all inlined member functions defined in oop.hpp
// We need a separate file to avoid circular references

markWord oopDesc::mark() const {
  return Atomic::load(&_mark);
}

markWord oopDesc::mark_acquire() const {
  return Atomic::load_acquire(&_mark);
}

markWord* oopDesc::mark_addr() const {
  return (markWord*) &_mark;
}

void oopDesc::set_mark(markWord m) {
  Atomic::store(&_mark, m);
}

void oopDesc::set_mark(HeapWord* mem, markWord m) {
  *(markWord*)(((char*)mem) + mark_offset_in_bytes()) = m;
}

void oopDesc::release_set_mark(HeapWord* mem, markWord m) {
  Atomic::release_store((markWord*)(((char*)mem) + mark_offset_in_bytes()), m);
}

void oopDesc::release_set_mark(markWord m) {
  Atomic::release_store(&_mark, m);
}

markWord oopDesc::cas_set_mark(markWord new_mark, markWord old_mark) {
  return Atomic::cmpxchg(&_mark, old_mark, new_mark);
}

markWord oopDesc::cas_set_mark(markWord new_mark, markWord old_mark, atomic_memory_order order) {
  return Atomic::cmpxchg(&_mark, old_mark, new_mark, order);
}

markWord oopDesc::prototype_mark() const {
  if (UseCompactObjectHeaders) {
    return klass()->prototype_header();
  } else {
    return markWord::prototype();
  }
}

void oopDesc::init_mark() {
  set_mark(prototype_mark());
}

Klass* oopDesc::klass() const {
  switch (ObjLayout::klass_mode()) {
    case ObjLayout::Compact:
      return mark().klass();
    case ObjLayout::Compressed:
      return CompressedKlassPointers::decode_not_null(_metadata._compressed_klass);
    default:
      return _metadata._klass;
  }
}

Klass* oopDesc::klass_or_null() const {
  switch (ObjLayout::klass_mode()) {
    case ObjLayout::Compact:
      return mark().klass_or_null();
    case ObjLayout::Compressed:
      return CompressedKlassPointers::decode(_metadata._compressed_klass);
    default:
      return _metadata._klass;
  }
}

Klass* oopDesc::klass_or_null_acquire() const {
  switch (ObjLayout::klass_mode()) {
    case ObjLayout::Compact:
      return mark_acquire().klass();
    case ObjLayout::Compressed: {
      narrowKlass narrow_klass = Atomic::load_acquire(&_metadata._compressed_klass);
      return CompressedKlassPointers::decode(narrow_klass);
    }
    default:
      return Atomic::load_acquire(&_metadata._klass);
  }
}

Klass* oopDesc::klass_without_asserts() const {
  switch (ObjLayout::klass_mode()) {
    case ObjLayout::Compact:
      return mark().klass_without_asserts();
    case ObjLayout::Compressed:
      return CompressedKlassPointers::decode_without_asserts(_metadata._compressed_klass);
    default:
      return _metadata._klass;
  }
}

narrowKlass oopDesc::narrow_klass() const {
  switch (ObjLayout::klass_mode()) {
    case ObjLayout::Compact:
      return mark().narrow_klass();
    case ObjLayout::Compressed:
      return _metadata._compressed_klass;
    default:
      ShouldNotReachHere();
  }
}

void oopDesc::set_klass(Klass* k) {
  assert(Universe::is_bootstrapping() || (k != nullptr && k->is_klass()), "incorrect Klass");
  assert(!UseCompactObjectHeaders, "don't set Klass* with compact headers");
  if (UseCompressedClassPointers) {
    _metadata._compressed_klass = CompressedKlassPointers::encode_not_null(k);
  } else {
    _metadata._klass = k;
  }
}

void oopDesc::release_set_klass(HeapWord* mem, Klass* k) {
  assert(Universe::is_bootstrapping() || (k != nullptr && k->is_klass()), "incorrect Klass");
  assert(!UseCompactObjectHeaders, "don't set Klass* with compact headers");
  char* raw_mem = ((char*)mem + klass_offset_in_bytes());
  if (UseCompressedClassPointers) {
    Atomic::release_store((narrowKlass*)raw_mem,
                          CompressedKlassPointers::encode_not_null(k));
  } else {
    Atomic::release_store((Klass**)raw_mem, k);
  }
}

void oopDesc::set_klass_gap(HeapWord* mem, int v) {
  assert(has_klass_gap(), "precondition");
  *(int*)(((char*)mem) + klass_gap_offset_in_bytes()) = v;
}

bool oopDesc::is_a(Klass* k) const {
  return klass()->is_subtype_of(k);
}

size_t oopDesc::size()  {
  return size_given_klass(klass());
}

size_t oopDesc::size_given_klass(Klass* klass)  {
  int lh = klass->layout_helper();
  size_t s;

  // lh is now a value computed at class initialization that may hint
  // at the size.  For instances, this is positive and equal to the
  // size.  For arrays, this is negative and provides log2 of the
  // array element size.  For other oops, it is zero and thus requires
  // a virtual call.
  //
  // We go to all this trouble because the size computation is at the
  // heart of phase 2 of mark-compaction, and called for every object,
  // alive or dead.  So the speed here is equal in importance to the
  // speed of allocation.

  if (lh > Klass::_lh_neutral_value) {
    if (!Klass::layout_helper_needs_slow_path(lh)) {
      s = lh >> LogHeapWordSize;  // deliver size scaled by wordSize
    } else {
      s = klass->oop_size(this);
    }
  } else if (lh <= Klass::_lh_neutral_value) {
    // The most common case is instances; fall through if so.
    if (lh < Klass::_lh_neutral_value) {
      // Second most common case is arrays.  We have to fetch the
      // length of the array, shift (multiply) it appropriately,
      // up to wordSize, add the header, and align to object size.
      size_t size_in_bytes;
      size_t array_length = (size_t) ((arrayOop)this)->length();
      size_in_bytes = array_length << Klass::layout_helper_log2_element_size(lh);
      size_in_bytes += Klass::layout_helper_header_size(lh);

      // This code could be simplified, but by keeping array_header_in_bytes
      // in units of bytes and doing it this way we can round up just once,
      // skipping the intermediate round to HeapWordSize.
      s = align_up(size_in_bytes, MinObjAlignmentInBytes) / HeapWordSize;

      assert(s == klass->oop_size(this), "wrong array object size");
    } else {
      // Must be zero, so bite the bullet and take the virtual call.
      s = klass->oop_size(this);
    }
  }

  assert(s > 0, "Oop size must be greater than zero, not %zu", s);
  assert(is_object_aligned(s), "Oop size is not properly aligned: %zu", s);
  return s;
}

bool oopDesc::is_instance()    const { return klass()->is_instance_klass();             }
bool oopDesc::is_instanceRef() const { return klass()->is_reference_instance_klass();   }
bool oopDesc::is_stackChunk()  const { return klass()->is_stack_chunk_instance_klass(); }
bool oopDesc::is_array()       const { return klass()->is_array_klass();                }
bool oopDesc::is_objArray()    const { return klass()->is_objArray_klass();             }
bool oopDesc::is_typeArray()   const { return klass()->is_typeArray_klass();            }

template<typename T>
T*       oopDesc::field_addr(int offset)     const { return reinterpret_cast<T*>(cast_from_oop<intptr_t>(as_oop()) + offset); }

template <typename T>
size_t   oopDesc::field_offset(T* p) const { return pointer_delta((void*)p, (void*)this, 1); }

template <DecoratorSet decorators>
inline oop  oopDesc::obj_field_access(int offset) const             { return HeapAccess<decorators>::oop_load_at(as_oop(), offset); }
inline oop  oopDesc::obj_field(int offset) const                    { return HeapAccess<>::oop_load_at(as_oop(), offset);  }

inline void oopDesc::obj_field_put(int offset, oop value)           { HeapAccess<>::oop_store_at(as_oop(), offset, value); }
template <DecoratorSet decorators>
inline void oopDesc::obj_field_put_access(int offset, oop value)    { HeapAccess<decorators>::oop_store_at(as_oop(), offset, value); }

inline jbyte oopDesc::byte_field(int offset) const                  { return *field_addr<jbyte>(offset);  }
inline void  oopDesc::byte_field_put(int offset, jbyte value)       { *field_addr<jbyte>(offset) = value; }

inline jchar oopDesc::char_field(int offset) const                  { return *field_addr<jchar>(offset);  }
inline void  oopDesc::char_field_put(int offset, jchar value)       { *field_addr<jchar>(offset) = value; }

inline jboolean oopDesc::bool_field(int offset) const               { return *field_addr<jboolean>(offset); }
inline void     oopDesc::bool_field_put(int offset, jboolean value) { *field_addr<jboolean>(offset) = jboolean(value & 1); }
inline jboolean oopDesc::bool_field_volatile(int offset) const      { return RawAccess<MO_SEQ_CST>::load(field_addr<jboolean>(offset)); }
inline void     oopDesc::bool_field_put_volatile(int offset, jboolean value) { RawAccess<MO_SEQ_CST>::store(field_addr<jboolean>(offset), jboolean(value & 1)); }
inline jshort oopDesc::short_field(int offset) const                { return *field_addr<jshort>(offset);   }
inline void   oopDesc::short_field_put(int offset, jshort value)    { *field_addr<jshort>(offset) = value;  }

inline jint oopDesc::int_field(int offset) const                    { return *field_addr<jint>(offset);     }
inline void oopDesc::int_field_put(int offset, jint value)          { *field_addr<jint>(offset) = value;    }
inline jint oopDesc::int_field_relaxed(int offset) const            { return Atomic::load(field_addr<jint>(offset)); }
inline void oopDesc::int_field_put_relaxed(int offset, jint value)  { Atomic::store(field_addr<jint>(offset), value); }

inline jlong oopDesc::long_field(int offset) const                  { return *field_addr<jlong>(offset);    }
inline void  oopDesc::long_field_put(int offset, jlong value)       { *field_addr<jlong>(offset) = value;   }

inline jfloat oopDesc::float_field(int offset) const                { return *field_addr<jfloat>(offset);   }
inline void   oopDesc::float_field_put(int offset, jfloat value)    { *field_addr<jfloat>(offset) = value;  }

inline jdouble oopDesc::double_field(int offset) const              { return *field_addr<jdouble>(offset);  }
inline void    oopDesc::double_field_put(int offset, jdouble value) { *field_addr<jdouble>(offset) = value; }

bool oopDesc::is_locked() const {
  return mark().is_locked();
}

bool oopDesc::is_unlocked() const {
  return mark().is_unlocked();
}

bool oopDesc::is_gc_marked() const {
  return mark().is_marked();
}

// Used by scavengers
bool oopDesc::is_forwarded() const {
  return mark().is_forwarded();
}

bool oopDesc::is_self_forwarded() const {
  return mark().is_self_forwarded();
}

// Used by scavengers
void oopDesc::forward_to(oop p) {
  assert(cast_from_oop<oopDesc*>(p) != this,
         "must not be used for self-forwarding, use forward_to_self() instead");
  markWord m = markWord::encode_pointer_as_mark(p);
  assert(m.decode_pointer() == p, "encoding must be reversible");
  set_mark(m);
}

void oopDesc::forward_to_self() {
  set_mark(mark().set_self_forwarded());
}

oop oopDesc::cas_set_forwardee(markWord new_mark, markWord compare, atomic_memory_order order) {
  markWord old_mark = cas_set_mark(new_mark, compare, order);
  if (old_mark == compare) {
    return nullptr;
  } else {
    assert(old_mark.is_forwarded(), "must be forwarded here");
    return forwardee(old_mark);
  }
}

oop oopDesc::forward_to_atomic(oop p, markWord compare, atomic_memory_order order) {
  assert(cast_from_oop<oopDesc*>(p) != this,
         "must not be used for self-forwarding, use forward_to_self_atomic() instead");
  markWord m = markWord::encode_pointer_as_mark(p);
  assert(forwardee(m) == p, "encoding must be reversible");
  return cas_set_forwardee(m, compare, order);
}

oop oopDesc::forward_to_self_atomic(markWord old_mark, atomic_memory_order order) {
  markWord new_mark = old_mark.set_self_forwarded();
  assert(forwardee(new_mark) == cast_to_oop(this), "encoding must be reversible");
  return cas_set_forwardee(new_mark, old_mark, order);
}

oop oopDesc::forwardee(markWord mark) const {
  assert(mark.is_forwarded(), "only decode when actually forwarded");
  if (mark.is_self_forwarded()) {
    return cast_to_oop(this);
  } else {
    return mark.forwardee();
  }
}

// Note that the forwardee is not the same thing as the displaced_mark.
// The forwardee is used when copying during scavenge and mark-sweep.
// It does need to clear the low two locking- and GC-related bits.
oop oopDesc::forwardee() const {
  return forwardee(mark());
}

void oopDesc::unset_self_forwarded() {
  set_mark(mark().unset_self_forwarded());
}

// The following method needs to be MT safe.
uint oopDesc::age() const {
  markWord m = mark();
  assert(!m.is_marked(), "Attempt to read age from forwarded mark");
  if (m.has_displaced_mark_helper()) {
    return m.displaced_mark_helper().age();
  } else {
    return m.age();
  }
}

void oopDesc::incr_age() {
  markWord m = mark();
  assert(!m.is_marked(), "Attempt to increment age of forwarded mark");
  if (m.has_displaced_mark_helper()) {
    m.set_displaced_mark_helper(m.displaced_mark_helper().incr_age());
  } else {
    set_mark(m.incr_age());
  }
}

template <typename OopClosureType>
void oopDesc::oop_iterate(OopClosureType* cl) {
  OopIteratorClosureDispatch::oop_oop_iterate(cl, this, klass());
}

template <typename OopClosureType>
void oopDesc::oop_iterate(OopClosureType* cl, MemRegion mr) {
  OopIteratorClosureDispatch::oop_oop_iterate(cl, this, klass(), mr);
}

template <typename OopClosureType>
size_t oopDesc::oop_iterate_size(OopClosureType* cl) {
  Klass* k = klass();
  size_t size = size_given_klass(k);
  OopIteratorClosureDispatch::oop_oop_iterate(cl, this, k);
  return size;
}

template <typename OopClosureType>
size_t oopDesc::oop_iterate_size(OopClosureType* cl, MemRegion mr) {
  Klass* k = klass();
  size_t size = size_given_klass(k);
  OopIteratorClosureDispatch::oop_oop_iterate(cl, this, k, mr);
  return size;
}

template <typename OopClosureType>
void oopDesc::oop_iterate_backwards(OopClosureType* cl) {
  oop_iterate_backwards(cl, klass());
}

template <typename OopClosureType>
void oopDesc::oop_iterate_backwards(OopClosureType* cl, Klass* k) {
  // In this assert, we cannot safely access the Klass* with compact headers.
  assert(k == klass(), "wrong klass");
  OopIteratorClosureDispatch::oop_oop_iterate_backwards(cl, this, k);
}

bool oopDesc::is_instanceof_or_null(oop obj, Klass* klass) {
  return obj == nullptr || obj->klass()->is_subtype_of(klass);
}

intptr_t oopDesc::identity_hash() {
  // Fast case; if the object is unlocked and the hash value is set, no locking is needed
  // Note: The mark must be read into local variable to avoid concurrent updates.
  markWord mrk = mark();
  if (mrk.is_unlocked() && !mrk.has_no_hash()) {
    return mrk.hash();
  } else if (mrk.is_marked()) {
    return mrk.hash();
  } else {
    return slow_identity_hash();
  }
}

// This checks fast simple case of whether the oop has_no_hash,
// to optimize JVMTI table lookup.
bool oopDesc::fast_no_hash_check() {
  markWord mrk = mark_acquire();
  assert(!mrk.is_marked(), "should never be marked");
  return mrk.is_unlocked() && mrk.has_no_hash();
}

bool oopDesc::has_displaced_mark() const {
  return mark().has_displaced_mark_helper();
}

markWord oopDesc::displaced_mark() const {
  return mark().displaced_mark_helper();
}

void oopDesc::set_displaced_mark(markWord m) {
  mark().set_displaced_mark_helper(m);
}

bool oopDesc::mark_must_be_preserved() const {
  return mark_must_be_preserved(mark());
}

bool oopDesc::mark_must_be_preserved(markWord m) const {
  return m.must_be_preserved();
}

#endif // SHARE_OOPS_OOP_INLINE_HPP

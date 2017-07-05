/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_OOPS_OOP_INLINE_HPP
#define SHARE_VM_OOPS_OOP_INLINE_HPP

#include "gc_implementation/shared/ageTable.hpp"
#include "gc_implementation/shared/markSweep.inline.hpp"
#include "gc_interface/collectedHeap.inline.hpp"
#include "memory/barrierSet.inline.hpp"
#include "memory/cardTableModRefBS.hpp"
#include "memory/genCollectedHeap.hpp"
#include "memory/generation.hpp"
#include "memory/specialized_oop_closures.hpp"
#include "oops/arrayKlass.hpp"
#include "oops/arrayOop.hpp"
#include "oops/klass.inline.hpp"
#include "oops/markOop.inline.hpp"
#include "oops/oop.hpp"
#include "runtime/atomic.hpp"
#include "runtime/os.hpp"
#include "utilities/macros.hpp"
#ifdef TARGET_ARCH_x86
# include "bytes_x86.hpp"
#endif
#ifdef TARGET_ARCH_sparc
# include "bytes_sparc.hpp"
#endif
#ifdef TARGET_ARCH_zero
# include "bytes_zero.hpp"
#endif
#ifdef TARGET_ARCH_arm
# include "bytes_arm.hpp"
#endif
#ifdef TARGET_ARCH_ppc
# include "bytes_ppc.hpp"
#endif

// Implementation of all inlined member functions defined in oop.hpp
// We need a separate file to avoid circular references

inline void oopDesc::release_set_mark(markOop m) {
  OrderAccess::release_store_ptr(&_mark, m);
}

inline markOop oopDesc::cas_set_mark(markOop new_mark, markOop old_mark) {
  return (markOop) Atomic::cmpxchg_ptr(new_mark, &_mark, old_mark);
}

inline Klass* oopDesc::klass() const {
  if (UseCompressedKlassPointers) {
    return Klass::decode_klass_not_null(_metadata._compressed_klass);
  } else {
    return _metadata._klass;
  }
}

inline Klass* oopDesc::klass_or_null() const volatile {
  // can be NULL in CMS
  if (UseCompressedKlassPointers) {
    return Klass::decode_klass(_metadata._compressed_klass);
  } else {
    return _metadata._klass;
  }
}

inline int oopDesc::klass_gap_offset_in_bytes() {
  assert(UseCompressedKlassPointers, "only applicable to compressed klass pointers");
  return oopDesc::klass_offset_in_bytes() + sizeof(narrowKlass);
}

inline Klass** oopDesc::klass_addr() {
  // Only used internally and with CMS and will not work with
  // UseCompressedOops
  assert(!UseCompressedKlassPointers, "only supported with uncompressed klass pointers");
  return (Klass**) &_metadata._klass;
}

inline narrowKlass* oopDesc::compressed_klass_addr() {
  assert(UseCompressedKlassPointers, "only called by compressed klass pointers");
  return &_metadata._compressed_klass;
}

inline void oopDesc::set_klass(Klass* k) {
  // since klasses are promoted no store check is needed
  assert(Universe::is_bootstrapping() || k != NULL, "must be a real Klass*");
  assert(Universe::is_bootstrapping() || k->is_klass(), "not a Klass*");
  if (UseCompressedKlassPointers) {
    *compressed_klass_addr() = Klass::encode_klass_not_null(k);
  } else {
    *klass_addr() = k;
  }
}

inline int oopDesc::klass_gap() const {
  return *(int*)(((intptr_t)this) + klass_gap_offset_in_bytes());
}

inline void oopDesc::set_klass_gap(int v) {
  if (UseCompressedKlassPointers) {
    *(int*)(((intptr_t)this) + klass_gap_offset_in_bytes()) = v;
  }
}

inline void oopDesc::set_klass_to_list_ptr(oop k) {
  // This is only to be used during GC, for from-space objects, so no
  // barrier is needed.
  if (UseCompressedKlassPointers) {
    _metadata._compressed_klass = (narrowKlass)encode_heap_oop(k);  // may be null (parnew overflow handling)
  } else {
    _metadata._klass = (Klass*)(address)k;
  }
}

inline oop oopDesc::list_ptr_from_klass() {
  // This is only to be used during GC, for from-space objects.
  if (UseCompressedKlassPointers) {
    return decode_heap_oop((narrowOop)_metadata._compressed_klass);
  } else {
    // Special case for GC
    return (oop)(address)_metadata._klass;
  }
}

inline void   oopDesc::init_mark()                 { set_mark(markOopDesc::prototype_for_object(this)); }

inline bool oopDesc::is_a(Klass* k)        const { return klass()->is_subtype_of(k); }

inline bool oopDesc::is_instance()           const { return klass()->oop_is_instance(); }
inline bool oopDesc::is_instanceMirror()     const { return klass()->oop_is_instanceMirror(); }
inline bool oopDesc::is_instanceRef()        const { return klass()->oop_is_instanceRef(); }
inline bool oopDesc::is_array()              const { return klass()->oop_is_array(); }
inline bool oopDesc::is_objArray()           const { return klass()->oop_is_objArray(); }
inline bool oopDesc::is_typeArray()          const { return klass()->oop_is_typeArray(); }

inline void*     oopDesc::field_base(int offset)        const { return (void*)&((char*)this)[offset]; }

template <class T> inline T* oopDesc::obj_field_addr(int offset) const { return (T*)field_base(offset); }
inline Metadata** oopDesc::metadata_field_addr(int offset) const { return (Metadata**)field_base(offset); }
inline jbyte*    oopDesc::byte_field_addr(int offset)   const { return (jbyte*)   field_base(offset); }
inline jchar*    oopDesc::char_field_addr(int offset)   const { return (jchar*)   field_base(offset); }
inline jboolean* oopDesc::bool_field_addr(int offset)   const { return (jboolean*)field_base(offset); }
inline jint*     oopDesc::int_field_addr(int offset)    const { return (jint*)    field_base(offset); }
inline jshort*   oopDesc::short_field_addr(int offset)  const { return (jshort*)  field_base(offset); }
inline jlong*    oopDesc::long_field_addr(int offset)   const { return (jlong*)   field_base(offset); }
inline jfloat*   oopDesc::float_field_addr(int offset)  const { return (jfloat*)  field_base(offset); }
inline jdouble*  oopDesc::double_field_addr(int offset) const { return (jdouble*) field_base(offset); }
inline address*  oopDesc::address_field_addr(int offset) const { return (address*) field_base(offset); }


// Functions for getting and setting oops within instance objects.
// If the oops are compressed, the type passed to these overloaded functions
// is narrowOop.  All functions are overloaded so they can be called by
// template functions without conditionals (the compiler instantiates via
// the right type and inlines the appopriate code).

inline bool oopDesc::is_null(oop obj)       { return obj == NULL; }
inline bool oopDesc::is_null(narrowOop obj) { return obj == 0; }

// Algorithm for encoding and decoding oops from 64 bit pointers to 32 bit
// offset from the heap base.  Saving the check for null can save instructions
// in inner GC loops so these are separated.

inline bool check_obj_alignment(oop obj) {
  return (intptr_t)obj % MinObjAlignmentInBytes == 0;
}

inline narrowOop oopDesc::encode_heap_oop_not_null(oop v) {
  assert(!is_null(v), "oop value can never be zero");
  assert(check_obj_alignment(v), "Address not aligned");
  assert(Universe::heap()->is_in_reserved(v), "Address not in heap");
  address base = Universe::narrow_oop_base();
  int    shift = Universe::narrow_oop_shift();
  uint64_t  pd = (uint64_t)(pointer_delta((void*)v, (void*)base, 1));
  assert(OopEncodingHeapMax > pd, "change encoding max if new encoding");
  uint64_t result = pd >> shift;
  assert((result & CONST64(0xffffffff00000000)) == 0, "narrow oop overflow");
  assert(decode_heap_oop(result) == v, "reversibility");
  return (narrowOop)result;
}

inline narrowOop oopDesc::encode_heap_oop(oop v) {
  return (is_null(v)) ? (narrowOop)0 : encode_heap_oop_not_null(v);
}

inline oop oopDesc::decode_heap_oop_not_null(narrowOop v) {
  assert(!is_null(v), "narrow oop value can never be zero");
  address base = Universe::narrow_oop_base();
  int    shift = Universe::narrow_oop_shift();
  oop result = (oop)(void*)((uintptr_t)base + ((uintptr_t)v << shift));
  assert(check_obj_alignment(result), err_msg("address not aligned: " PTR_FORMAT, (void*) result));
  return result;
}

inline oop oopDesc::decode_heap_oop(narrowOop v) {
  return is_null(v) ? (oop)NULL : decode_heap_oop_not_null(v);
}

inline oop oopDesc::decode_heap_oop_not_null(oop v) { return v; }
inline oop oopDesc::decode_heap_oop(oop v)  { return v; }

// Load an oop out of the Java heap as is without decoding.
// Called by GC to check for null before decoding.
inline oop       oopDesc::load_heap_oop(oop* p)          { return *p; }
inline narrowOop oopDesc::load_heap_oop(narrowOop* p)    { return *p; }

// Load and decode an oop out of the Java heap into a wide oop.
inline oop oopDesc::load_decode_heap_oop_not_null(oop* p)       { return *p; }
inline oop oopDesc::load_decode_heap_oop_not_null(narrowOop* p) {
  return decode_heap_oop_not_null(*p);
}

// Load and decode an oop out of the heap accepting null
inline oop oopDesc::load_decode_heap_oop(oop* p) { return *p; }
inline oop oopDesc::load_decode_heap_oop(narrowOop* p) {
  return decode_heap_oop(*p);
}

// Store already encoded heap oop into the heap.
inline void oopDesc::store_heap_oop(oop* p, oop v)                 { *p = v; }
inline void oopDesc::store_heap_oop(narrowOop* p, narrowOop v)     { *p = v; }

// Encode and store a heap oop.
inline void oopDesc::encode_store_heap_oop_not_null(narrowOop* p, oop v) {
  *p = encode_heap_oop_not_null(v);
}
inline void oopDesc::encode_store_heap_oop_not_null(oop* p, oop v) { *p = v; }

// Encode and store a heap oop allowing for null.
inline void oopDesc::encode_store_heap_oop(narrowOop* p, oop v) {
  *p = encode_heap_oop(v);
}
inline void oopDesc::encode_store_heap_oop(oop* p, oop v) { *p = v; }

// Store heap oop as is for volatile fields.
inline void oopDesc::release_store_heap_oop(volatile oop* p, oop v) {
  OrderAccess::release_store_ptr(p, v);
}
inline void oopDesc::release_store_heap_oop(volatile narrowOop* p,
                                            narrowOop v) {
  OrderAccess::release_store(p, v);
}

inline void oopDesc::release_encode_store_heap_oop_not_null(
                                                volatile narrowOop* p, oop v) {
  // heap oop is not pointer sized.
  OrderAccess::release_store(p, encode_heap_oop_not_null(v));
}

inline void oopDesc::release_encode_store_heap_oop_not_null(
                                                      volatile oop* p, oop v) {
  OrderAccess::release_store_ptr(p, v);
}

inline void oopDesc::release_encode_store_heap_oop(volatile oop* p,
                                                           oop v) {
  OrderAccess::release_store_ptr(p, v);
}
inline void oopDesc::release_encode_store_heap_oop(
                                                volatile narrowOop* p, oop v) {
  OrderAccess::release_store(p, encode_heap_oop(v));
}


// These functions are only used to exchange oop fields in instances,
// not headers.
inline oop oopDesc::atomic_exchange_oop(oop exchange_value, volatile HeapWord *dest) {
  if (UseCompressedOops) {
    // encode exchange value from oop to T
    narrowOop val = encode_heap_oop(exchange_value);
    narrowOop old = (narrowOop)Atomic::xchg(val, (narrowOop*)dest);
    // decode old from T to oop
    return decode_heap_oop(old);
  } else {
    return (oop)Atomic::xchg_ptr(exchange_value, (oop*)dest);
  }
}

// In order to put or get a field out of an instance, must first check
// if the field has been compressed and uncompress it.
inline oop oopDesc::obj_field(int offset) const {
  return UseCompressedOops ?
    load_decode_heap_oop(obj_field_addr<narrowOop>(offset)) :
    load_decode_heap_oop(obj_field_addr<oop>(offset));
}
inline volatile oop oopDesc::obj_field_volatile(int offset) const {
  volatile oop value = obj_field(offset);
  OrderAccess::acquire();
  return value;
}
inline void oopDesc::obj_field_put(int offset, oop value) {
  UseCompressedOops ? oop_store(obj_field_addr<narrowOop>(offset), value) :
                      oop_store(obj_field_addr<oop>(offset),       value);
}

inline Metadata* oopDesc::metadata_field(int offset) const {
  return *metadata_field_addr(offset);
}

inline void oopDesc::metadata_field_put(int offset, Metadata* value) {
  *metadata_field_addr(offset) = value;
}

inline void oopDesc::obj_field_put_raw(int offset, oop value) {
  UseCompressedOops ?
    encode_store_heap_oop(obj_field_addr<narrowOop>(offset), value) :
    encode_store_heap_oop(obj_field_addr<oop>(offset),       value);
}
inline void oopDesc::obj_field_put_volatile(int offset, oop value) {
  OrderAccess::release();
  obj_field_put(offset, value);
  OrderAccess::fence();
}

inline jbyte oopDesc::byte_field(int offset) const                  { return (jbyte) *byte_field_addr(offset);    }
inline void oopDesc::byte_field_put(int offset, jbyte contents)     { *byte_field_addr(offset) = (jint) contents; }

inline jboolean oopDesc::bool_field(int offset) const               { return (jboolean) *bool_field_addr(offset); }
inline void oopDesc::bool_field_put(int offset, jboolean contents)  { *bool_field_addr(offset) = (jint) contents; }

inline jchar oopDesc::char_field(int offset) const                  { return (jchar) *char_field_addr(offset);    }
inline void oopDesc::char_field_put(int offset, jchar contents)     { *char_field_addr(offset) = (jint) contents; }

inline jint oopDesc::int_field(int offset) const                    { return *int_field_addr(offset);        }
inline void oopDesc::int_field_put(int offset, jint contents)       { *int_field_addr(offset) = contents;    }

inline jshort oopDesc::short_field(int offset) const                { return (jshort) *short_field_addr(offset);  }
inline void oopDesc::short_field_put(int offset, jshort contents)   { *short_field_addr(offset) = (jint) contents;}

inline jlong oopDesc::long_field(int offset) const                  { return *long_field_addr(offset);       }
inline void oopDesc::long_field_put(int offset, jlong contents)     { *long_field_addr(offset) = contents;   }

inline jfloat oopDesc::float_field(int offset) const                { return *float_field_addr(offset);      }
inline void oopDesc::float_field_put(int offset, jfloat contents)   { *float_field_addr(offset) = contents;  }

inline jdouble oopDesc::double_field(int offset) const              { return *double_field_addr(offset);     }
inline void oopDesc::double_field_put(int offset, jdouble contents) { *double_field_addr(offset) = contents; }

inline address oopDesc::address_field(int offset) const              { return *address_field_addr(offset);     }
inline void oopDesc::address_field_put(int offset, address contents) { *address_field_addr(offset) = contents; }

inline oop oopDesc::obj_field_acquire(int offset) const {
  return UseCompressedOops ?
             decode_heap_oop((narrowOop)
               OrderAccess::load_acquire(obj_field_addr<narrowOop>(offset)))
           : decode_heap_oop((oop)
               OrderAccess::load_ptr_acquire(obj_field_addr<oop>(offset)));
}
inline void oopDesc::release_obj_field_put(int offset, oop value) {
  UseCompressedOops ?
    oop_store((volatile narrowOop*)obj_field_addr<narrowOop>(offset), value) :
    oop_store((volatile oop*)      obj_field_addr<oop>(offset),       value);
}

inline jbyte oopDesc::byte_field_acquire(int offset) const                  { return OrderAccess::load_acquire(byte_field_addr(offset));     }
inline void oopDesc::release_byte_field_put(int offset, jbyte contents)     { OrderAccess::release_store(byte_field_addr(offset), contents); }

inline jboolean oopDesc::bool_field_acquire(int offset) const               { return OrderAccess::load_acquire(bool_field_addr(offset));     }
inline void oopDesc::release_bool_field_put(int offset, jboolean contents)  { OrderAccess::release_store(bool_field_addr(offset), contents); }

inline jchar oopDesc::char_field_acquire(int offset) const                  { return OrderAccess::load_acquire(char_field_addr(offset));     }
inline void oopDesc::release_char_field_put(int offset, jchar contents)     { OrderAccess::release_store(char_field_addr(offset), contents); }

inline jint oopDesc::int_field_acquire(int offset) const                    { return OrderAccess::load_acquire(int_field_addr(offset));      }
inline void oopDesc::release_int_field_put(int offset, jint contents)       { OrderAccess::release_store(int_field_addr(offset), contents);  }

inline jshort oopDesc::short_field_acquire(int offset) const                { return (jshort)OrderAccess::load_acquire(short_field_addr(offset)); }
inline void oopDesc::release_short_field_put(int offset, jshort contents)   { OrderAccess::release_store(short_field_addr(offset), contents);     }

inline jlong oopDesc::long_field_acquire(int offset) const                  { return OrderAccess::load_acquire(long_field_addr(offset));       }
inline void oopDesc::release_long_field_put(int offset, jlong contents)     { OrderAccess::release_store(long_field_addr(offset), contents);   }

inline jfloat oopDesc::float_field_acquire(int offset) const                { return OrderAccess::load_acquire(float_field_addr(offset));      }
inline void oopDesc::release_float_field_put(int offset, jfloat contents)   { OrderAccess::release_store(float_field_addr(offset), contents);  }

inline jdouble oopDesc::double_field_acquire(int offset) const              { return OrderAccess::load_acquire(double_field_addr(offset));     }
inline void oopDesc::release_double_field_put(int offset, jdouble contents) { OrderAccess::release_store(double_field_addr(offset), contents); }

inline address oopDesc::address_field_acquire(int offset) const             { return (address) OrderAccess::load_ptr_acquire(address_field_addr(offset)); }
inline void oopDesc::release_address_field_put(int offset, address contents) { OrderAccess::release_store_ptr(address_field_addr(offset), contents); }

inline int oopDesc::size_given_klass(Klass* klass)  {
  int lh = klass->layout_helper();
  int s;

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
#ifdef _M_IA64
      // The Windows Itanium Aug 2002 SDK hoists this load above
      // the check for s < 0.  An oop at the end of the heap will
      // cause an access violation if this load is performed on a non
      // array oop.  Making the reference volatile prohibits this.
      // (%%% please explain by what magic the length is actually fetched!)
      volatile int *array_length;
      array_length = (volatile int *)( (intptr_t)this +
                          arrayOopDesc::length_offset_in_bytes() );
      assert(array_length > 0, "Integer arithmetic problem somewhere");
      // Put into size_t to avoid overflow.
      size_in_bytes = (size_t) array_length;
      size_in_bytes = size_in_bytes << Klass::layout_helper_log2_element_size(lh);
#else
      size_t array_length = (size_t) ((arrayOop)this)->length();
      size_in_bytes = array_length << Klass::layout_helper_log2_element_size(lh);
#endif
      size_in_bytes += Klass::layout_helper_header_size(lh);

      // This code could be simplified, but by keeping array_header_in_bytes
      // in units of bytes and doing it this way we can round up just once,
      // skipping the intermediate round to HeapWordSize.  Cast the result
      // of round_to to size_t to guarantee unsigned division == right shift.
      s = (int)((size_t)round_to(size_in_bytes, MinObjAlignmentInBytes) /
        HeapWordSize);

      // UseParNewGC, UseParallelGC and UseG1GC can change the length field
      // of an "old copy" of an object array in the young gen so it indicates
      // the grey portion of an already copied array. This will cause the first
      // disjunct below to fail if the two comparands are computed across such
      // a concurrent change.
      // UseParNewGC also runs with promotion labs (which look like int
      // filler arrays) which are subject to changing their declared size
      // when finally retiring a PLAB; this also can cause the first disjunct
      // to fail for another worker thread that is concurrently walking the block
      // offset table. Both these invariant failures are benign for their
      // current uses; we relax the assertion checking to cover these two cases below:
      //     is_objArray() && is_forwarded()   // covers first scenario above
      //  || is_typeArray()                    // covers second scenario above
      // If and when UseParallelGC uses the same obj array oop stealing/chunking
      // technique, we will need to suitably modify the assertion.
      assert((s == klass->oop_size(this)) ||
             (Universe::heap()->is_gc_active() &&
              ((is_typeArray() && UseParNewGC) ||
               (is_objArray()  && is_forwarded() && (UseParNewGC || UseParallelGC || UseG1GC)))),
             "wrong array object size");
    } else {
      // Must be zero, so bite the bullet and take the virtual call.
      s = klass->oop_size(this);
    }
  }

  assert(s % MinObjAlignment == 0, "alignment check");
  assert(s > 0, "Bad size calculated");
  return s;
}


inline int oopDesc::size()  {
  return size_given_klass(klass());
}

inline void update_barrier_set(void* p, oop v) {
  assert(oopDesc::bs() != NULL, "Uninitialized bs in oop!");
  oopDesc::bs()->write_ref_field(p, v);
}

template <class T> inline void update_barrier_set_pre(T* p, oop v) {
  oopDesc::bs()->write_ref_field_pre(p, v);
}

template <class T> inline void oop_store(T* p, oop v) {
  if (always_do_update_barrier) {
    oop_store((volatile T*)p, v);
  } else {
    update_barrier_set_pre(p, v);
    oopDesc::encode_store_heap_oop(p, v);
    update_barrier_set((void*)p, v);  // cast away type
  }
}

template <class T> inline void oop_store(volatile T* p, oop v) {
  update_barrier_set_pre((T*)p, v);   // cast away volatile
  // Used by release_obj_field_put, so use release_store_ptr.
  oopDesc::release_encode_store_heap_oop(p, v);
  update_barrier_set((void*)p, v);    // cast away type
}

// Should replace *addr = oop assignments where addr type depends on UseCompressedOops
// (without having to remember the function name this calls).
inline void oop_store_raw(HeapWord* addr, oop value) {
  if (UseCompressedOops) {
    oopDesc::encode_store_heap_oop((narrowOop*)addr, value);
  } else {
    oopDesc::encode_store_heap_oop((oop*)addr, value);
  }
}

inline oop oopDesc::atomic_compare_exchange_oop(oop exchange_value,
                                                volatile HeapWord *dest,
                                                oop compare_value,
                                                bool prebarrier) {
  if (UseCompressedOops) {
    if (prebarrier) {
      update_barrier_set_pre((narrowOop*)dest, exchange_value);
    }
    // encode exchange and compare value from oop to T
    narrowOop val = encode_heap_oop(exchange_value);
    narrowOop cmp = encode_heap_oop(compare_value);

    narrowOop old = (narrowOop) Atomic::cmpxchg(val, (narrowOop*)dest, cmp);
    // decode old from T to oop
    return decode_heap_oop(old);
  } else {
    if (prebarrier) {
      update_barrier_set_pre((oop*)dest, exchange_value);
    }
    return (oop)Atomic::cmpxchg_ptr(exchange_value, (oop*)dest, compare_value);
  }
}

// Used only for markSweep, scavenging
inline bool oopDesc::is_gc_marked() const {
  return mark()->is_marked();
}

inline bool oopDesc::is_locked() const {
  return mark()->is_locked();
}

inline bool oopDesc::is_unlocked() const {
  return mark()->is_unlocked();
}

inline bool oopDesc::has_bias_pattern() const {
  return mark()->has_bias_pattern();
}


// used only for asserts
inline bool oopDesc::is_oop(bool ignore_mark_word) const {
  oop obj = (oop) this;
  if (!check_obj_alignment(obj)) return false;
  if (!Universe::heap()->is_in_reserved(obj)) return false;
  // obj is aligned and accessible in heap
  if (Universe::heap()->is_in_reserved(obj->klass_or_null())) return false;

  // Header verification: the mark is typically non-NULL. If we're
  // at a safepoint, it must not be null.
  // Outside of a safepoint, the header could be changing (for example,
  // another thread could be inflating a lock on this object).
  if (ignore_mark_word) {
    return true;
  }
  if (mark() != NULL) {
    return true;
  }
  return !SafepointSynchronize::is_at_safepoint();
}


// used only for asserts
inline bool oopDesc::is_oop_or_null(bool ignore_mark_word) const {
  return this == NULL ? true : is_oop(ignore_mark_word);
}

#ifndef PRODUCT
// used only for asserts
inline bool oopDesc::is_unlocked_oop() const {
  if (!Universe::heap()->is_in_reserved(this)) return false;
  return mark()->is_unlocked();
}
#endif // PRODUCT

inline void oopDesc::follow_contents(void) {
  assert (is_gc_marked(), "should be marked");
  klass()->oop_follow_contents(this);
}

// Used by scavengers

inline bool oopDesc::is_forwarded() const {
  // The extra heap check is needed since the obj might be locked, in which case the
  // mark would point to a stack location and have the sentinel bit cleared
  return mark()->is_marked();
}

// Used by scavengers
inline void oopDesc::forward_to(oop p) {
  assert(check_obj_alignment(p),
         "forwarding to something not aligned");
  assert(Universe::heap()->is_in_reserved(p),
         "forwarding to something not in heap");
  markOop m = markOopDesc::encode_pointer_as_mark(p);
  assert(m->decode_pointer() == p, "encoding must be reversable");
  set_mark(m);
}

// Used by parallel scavengers
inline bool oopDesc::cas_forward_to(oop p, markOop compare) {
  assert(check_obj_alignment(p),
         "forwarding to something not aligned");
  assert(Universe::heap()->is_in_reserved(p),
         "forwarding to something not in heap");
  markOop m = markOopDesc::encode_pointer_as_mark(p);
  assert(m->decode_pointer() == p, "encoding must be reversable");
  return cas_set_mark(m, compare) == compare;
}

// Note that the forwardee is not the same thing as the displaced_mark.
// The forwardee is used when copying during scavenge and mark-sweep.
// It does need to clear the low two locking- and GC-related bits.
inline oop oopDesc::forwardee() const {
  return (oop) mark()->decode_pointer();
}

inline bool oopDesc::has_displaced_mark() const {
  return mark()->has_displaced_mark_helper();
}

inline markOop oopDesc::displaced_mark() const {
  return mark()->displaced_mark_helper();
}

inline void oopDesc::set_displaced_mark(markOop m) {
  mark()->set_displaced_mark_helper(m);
}

// The following method needs to be MT safe.
inline uint oopDesc::age() const {
  assert(!is_forwarded(), "Attempt to read age from forwarded mark");
  if (has_displaced_mark()) {
    return displaced_mark()->age();
  } else {
    return mark()->age();
  }
}

inline void oopDesc::incr_age() {
  assert(!is_forwarded(), "Attempt to increment age of forwarded mark");
  if (has_displaced_mark()) {
    set_displaced_mark(displaced_mark()->incr_age());
  } else {
    set_mark(mark()->incr_age());
  }
}


inline intptr_t oopDesc::identity_hash() {
  // Fast case; if the object is unlocked and the hash value is set, no locking is needed
  // Note: The mark must be read into local variable to avoid concurrent updates.
  markOop mrk = mark();
  if (mrk->is_unlocked() && !mrk->has_no_hash()) {
    return mrk->hash();
  } else if (mrk->is_marked()) {
    return mrk->hash();
  } else {
    return slow_identity_hash();
  }
}

inline int oopDesc::adjust_pointers() {
  debug_only(int check_size = size());
  int s = klass()->oop_adjust_pointers(this);
  assert(s == check_size, "should be the same");
  return s;
}

#define OOP_ITERATE_DEFN(OopClosureType, nv_suffix)                        \
                                                                           \
inline int oopDesc::oop_iterate(OopClosureType* blk) {                     \
  SpecializationStats::record_call();                                      \
  return klass()->oop_oop_iterate##nv_suffix(this, blk);               \
}                                                                          \
                                                                           \
inline int oopDesc::oop_iterate(OopClosureType* blk, MemRegion mr) {       \
  SpecializationStats::record_call();                                      \
  return klass()->oop_oop_iterate##nv_suffix##_m(this, blk, mr);       \
}


inline int oopDesc::oop_iterate_no_header(OopClosure* blk) {
  // The NoHeaderExtendedOopClosure wraps the OopClosure and proxies all
  // the do_oop calls, but turns off all other features in ExtendedOopClosure.
  NoHeaderExtendedOopClosure cl(blk);
  return oop_iterate(&cl);
}

inline int oopDesc::oop_iterate_no_header(OopClosure* blk, MemRegion mr) {
  NoHeaderExtendedOopClosure cl(blk);
  return oop_iterate(&cl, mr);
}

ALL_OOP_OOP_ITERATE_CLOSURES_1(OOP_ITERATE_DEFN)
ALL_OOP_OOP_ITERATE_CLOSURES_2(OOP_ITERATE_DEFN)

#if INCLUDE_ALL_GCS
#define OOP_ITERATE_BACKWARDS_DEFN(OopClosureType, nv_suffix)              \
                                                                           \
inline int oopDesc::oop_iterate_backwards(OopClosureType* blk) {           \
  SpecializationStats::record_call();                                      \
  return klass()->oop_oop_iterate_backwards##nv_suffix(this, blk);     \
}

ALL_OOP_OOP_ITERATE_CLOSURES_1(OOP_ITERATE_BACKWARDS_DEFN)
ALL_OOP_OOP_ITERATE_CLOSURES_2(OOP_ITERATE_BACKWARDS_DEFN)
#endif // INCLUDE_ALL_GCS

#endif // SHARE_VM_OOPS_OOP_INLINE_HPP

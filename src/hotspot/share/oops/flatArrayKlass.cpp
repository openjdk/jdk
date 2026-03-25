/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/moduleEntry.hpp"
#include "classfile/packageEntry.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "gc/shared/collectedHeap.inline.hpp"
#include "memory/iterator.inline.hpp"
#include "memory/metadataFactory.hpp"
#include "memory/metaspaceClosure.hpp"
#include "memory/oopFactory.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/access.hpp"
#include "oops/arrayKlass.inline.hpp"
#include "oops/arrayOop.hpp"
#include "oops/flatArrayKlass.hpp"
#include "oops/flatArrayOop.hpp"
#include "oops/flatArrayOop.inline.hpp"
#include "oops/inlineKlass.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/klass.inline.hpp"
#include "oops/layoutKind.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/oopCast.inline.hpp"
#include "oops/valuePayload.inline.hpp"
#include "oops/verifyOopClosure.hpp"
#include "runtime/arguments.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/copy.hpp"
#include "utilities/macros.hpp"

// Allocation...

FlatArrayKlass::FlatArrayKlass(Klass* element_klass, Symbol* name, ArrayProperties props, LayoutKind lk)
    : ObjArrayKlass(1, element_klass, name, Kind, props),
      _layout_kind(lk) {
  assert(element_klass->is_inline_klass(), "Expected Inline");
  assert(lk != LayoutKind::NULLABLE_NON_ATOMIC_FLAT, "Layout not supported by arrays yet (needs frozen arrays)");
  assert(LayoutKindHelper::is_flat(lk), "Must be a flat layout");

  assert(_class_loader_data == element_klass->class_loader_data(), "Sanity check");

  set_layout_helper(array_layout_helper(InlineKlass::cast(element_klass), lk));
  assert(is_array_klass(), "sanity");
  assert(is_flatArray_klass(), "sanity");

#ifdef ASSERT
  assert(layout_helper_is_array(layout_helper()), "Must be");
  assert(layout_helper_is_flatArray(layout_helper()), "Must be");
  assert(layout_helper_element_type(layout_helper()) == T_FLAT_ELEMENT, "Must be");
  assert(prototype_header().is_flat_array(), "Must be");
  switch(lk) {
    case LayoutKind::NULL_FREE_NON_ATOMIC_FLAT:
    case LayoutKind::NULL_FREE_ATOMIC_FLAT:
      assert(layout_helper_is_null_free(layout_helper()), "Must be");
      assert(prototype_header().is_null_free_array(), "Must be");
    break;
    case LayoutKind::NULLABLE_ATOMIC_FLAT:
      assert(!layout_helper_is_null_free(layout_helper()), "Must be");
      assert(!prototype_header().is_null_free_array(), "Must be");
    break;
    case LayoutKind::NULLABLE_NON_ATOMIC_FLAT:
      ShouldNotReachHere();
    default:
      ShouldNotReachHere();
    break;
  }
#endif // ASSERT

  if (PrintFlatArrayLayout) {
    print();
  }
}

FlatArrayKlass* FlatArrayKlass::allocate_klass(Klass* eklass, ArrayProperties props, LayoutKind lk, TRAPS) {
  guarantee((!Universe::is_bootstrapping() || vmClasses::Object_klass_is_loaded()), "Really ?!");
  assert(UseArrayFlattening, "Flatten array required");
  assert(MultiArray_lock->holds_lock(THREAD), "must hold lock after bootstrapping");

  InlineKlass* element_klass = InlineKlass::cast(eklass);
  assert(element_klass->must_be_atomic() || (!AlwaysAtomicAccesses), "Atomic by-default");

  // Eagerly allocate the direct array supertype.
  Klass* super_klass = nullptr;
  Klass* element_super = element_klass->super();
  if (element_super != nullptr) {
    // The element type has a direct super.  E.g., String[] has direct super of Object[].
    super_klass = element_klass->array_klass(CHECK_NULL);
  }

  Symbol* name = create_element_klass_array_name(THREAD, element_klass);
  ClassLoaderData* loader_data = element_klass->class_loader_data();
  int size = ArrayKlass::static_size(FlatArrayKlass::header_size());
  FlatArrayKlass* vak = new (loader_data, size, THREAD) FlatArrayKlass(element_klass, name, props, lk);

  ModuleEntry* module = vak->module();
  assert(module != nullptr, "No module entry for array");
  complete_create_array_klass(vak, super_klass, module, CHECK_NULL);

  loader_data->add_class(vak);

  return vak;
}

void FlatArrayKlass::initialize(TRAPS) {
  element_klass()->initialize(THREAD);
}

void FlatArrayKlass::metaspace_pointers_do(MetaspaceClosure* it) {
  ObjArrayKlass::metaspace_pointers_do(it);
}

// Oops allocation...
flatArrayOop FlatArrayKlass::allocate_instance(int length, TRAPS) {
  assert(UseArrayFlattening, "Must be enabled");
  check_array_allocation_length(length, max_elements(), CHECK_NULL);
  int size = flatArrayOopDesc::object_size(layout_helper(), length);
  oop array = Universe::heap()->array_allocate(this, size, length, true, CHECK_NULL);
  return oop_cast<flatArrayOop>(array);
}

oop FlatArrayKlass::multi_allocate(int rank, jint* last_size, TRAPS) {
  // FlatArrays only have one dimension
  ShouldNotReachHere();
}

jint FlatArrayKlass::array_layout_helper(InlineKlass* vk, LayoutKind lk) {
  BasicType etype = T_FLAT_ELEMENT;
  int esize = log2i_exact(round_up_power_of_2(vk->layout_size_in_bytes(lk)));
  int hsize = arrayOopDesc::base_offset_in_bytes(etype);
  bool null_free = !LayoutKindHelper::is_nullable_flat(lk);
  int lh = Klass::array_layout_helper(_lh_array_tag_flat_value, null_free, hsize, etype, esize);

  assert(lh < (int)_lh_neutral_value, "must look like an array layout");
  assert(layout_helper_is_array(lh), "correct kind");
  assert(layout_helper_is_flatArray(lh), "correct kind");
  assert(!layout_helper_is_typeArray(lh), "correct kind");
  assert(layout_helper_is_null_free(lh) == null_free, "correct kind");
  assert(layout_helper_header_size(lh) == hsize, "correct decode");
  assert(layout_helper_element_type(lh) == etype, "correct decode");
  assert(layout_helper_log2_element_size(lh) == esize, "correct decode");
  assert((1 << esize) < BytesPerLong || is_aligned(hsize, HeapWordsPerLong), "unaligned base");

  return lh;
}

size_t FlatArrayKlass::oop_size(oop obj) const {
  // In this assert, we cannot safely access the Klass* with compact headers,
  // because size_given_klass() calls oop_size() on objects that might be
  // concurrently forwarded, which would overwrite the Klass*.
  // Also, why we need to pass this layout_helper() to flatArrayOop::object_size.
  assert(UseCompactObjectHeaders || obj->is_flatArray(),"must be an flat array");
  flatArrayOop array = flatArrayOop(obj);
  return array->object_size(layout_helper());
}

// For now return the maximum number of array elements that will not exceed:
// nof bytes = "max_jint * HeapWord" since the "oopDesc::oop_iterate_size"
// returns "int" HeapWords, need fix for JDK-4718400 and JDK-8233189
jint FlatArrayKlass::max_elements() const {
  // Check the max number of heap words limit first (because of int32_t in oopDesc_oop_size() etc)
  size_t max_size = max_jint;
  max_size -= (arrayOopDesc::base_offset_in_bytes(T_FLAT_ELEMENT) >> LogHeapWordSize);
  max_size = align_down(max_size, MinObjAlignment);
  max_size <<= LogHeapWordSize;                                  // convert to max payload size in bytes
  max_size >>= layout_helper_log2_element_size(_layout_helper);  // divide by element size (in bytes) = max elements
  // Within int32_t heap words, still can't exceed Java array element limit
  if (max_size > max_jint) {
    max_size = max_jint;
  }
  assert((max_size >> LogHeapWordSize) <= max_jint, "Overflow");
  return (jint) max_size;
}

oop FlatArrayKlass::protection_domain() const {
  return element_klass()->protection_domain();
}

// Temp hack having this here: need to move towards Access API
static bool needs_backwards_copy(arrayOop s, int src_pos,
                                 arrayOop d, int dst_pos, int length) {
  return (s == d) && (dst_pos > src_pos) && (dst_pos - src_pos) < length;
}

void FlatArrayKlass::copy_array(arrayOop s, int src_pos,
                                arrayOop d, int dst_pos, int length, TRAPS) {

  assert(s->is_refined_objArray(), "must be ref or flat array");

  // Check destination
  if (!d->is_refined_objArray()) {
    THROW(vmSymbols::java_lang_ArrayStoreException());
  }

  // Check if all offsets and lengths are non negative
  if (src_pos < 0 || dst_pos < 0 || length < 0) {
    THROW(vmSymbols::java_lang_ArrayIndexOutOfBoundsException());
  }
  // Check if the ranges are valid
  if  ( (((unsigned int) length + (unsigned int) src_pos) > (unsigned int) s->length())
      || (((unsigned int) length + (unsigned int) dst_pos) > (unsigned int) d->length()) ) {
    THROW(vmSymbols::java_lang_ArrayIndexOutOfBoundsException());
  }
  // Check zero copy
  if (length == 0)
    return;

  ObjArrayKlass* sk = ObjArrayKlass::cast(s->klass());
  ObjArrayKlass* dk = ObjArrayKlass::cast(d->klass());
  Klass* d_elem_klass = dk->element_klass();
  Klass* s_elem_klass = sk->element_klass();
  /**** CMH: compare and contrast impl, re-factor once we find edge cases... ****/

  if (sk->is_flatArray_klass()) {
    assert(sk == this, "Unexpected call to copy_array");
    FlatArrayKlass* fsk = FlatArrayKlass::cast(sk);
    // Check subtype, all src homogeneous, so just once
    if (!s_elem_klass->is_subtype_of(d_elem_klass)) {
      THROW(vmSymbols::java_lang_ArrayStoreException());
    }

    flatArrayOop sa = flatArrayOop(s);

    // flatArray-to-flatArray
    if (dk->is_flatArray_klass()) {
      // element types MUST be exact, subtype check would be dangerous
      if (d_elem_klass != this->element_klass()) {
        THROW(vmSymbols::java_lang_ArrayStoreException());
      }

      FlatArrayKlass* fdk = FlatArrayKlass::cast(dk);
      InlineKlass* vk = InlineKlass::cast(s_elem_klass);
      flatArrayOop da = flatArrayOop(d);

      // We have already checked that src_pos and dst_pos are valid indices.
      FlatArrayPayload src_payload(sa, src_pos, fsk);
      FlatArrayPayload dst_payload(da, dst_pos, fdk);

      if (fsk->layout_kind() == fdk->layout_kind()) {
        // Because source and destination have the same layout, we do not have
        // to worry about null checks and atomicity problems and can call the
        // Access API directly.
        int index_delta;
        if (needs_backwards_copy(sa, src_pos, da, dst_pos, length)) {
          index_delta = -1;
          src_payload.advance_index(length - 1);
          dst_payload.advance_index(length - 1);
        } else {
          index_delta = 1;
        }

        for (int i = 0; i < length; i++) {
          HeapAccess<>::value_copy(src_payload, dst_payload);
          src_payload.advance_index(index_delta);
          dst_payload.advance_index(index_delta);
        }
      } else {
        // We need to allocate a buffer object to facilitate the copy between
        // the different layouts. Keep the payload in a handle so we can reload
        // the oops.
        FlatArrayPayload::Handle src_payload_handle = src_payload.make_handle(THREAD);
        FlatArrayPayload::Handle dst_payload_handle = dst_payload.make_handle(THREAD);

        inlineOop buffer = vk->allocate_instance(CHECK);
        BufferedValuePayload buf_payload(buffer);

        // Reload the oops from the payload handles.
        src_payload = src_payload_handle();
        dst_payload = dst_payload_handle();

        const bool dst_is_null_restricted = !LayoutKindHelper::is_nullable_flat(dst_payload.layout_kind());

        // fsk->layout_kind() != fdk->layout_kind() implies that s != d, which
        // means that the copy is disjoint and we do not need to worry about
        // needs_backwards_copy.
        for (int i = 0; i < length; i++) {
          // Copy via buffer
          if (src_payload.is_payload_null() || !src_payload.copy_to(buf_payload)) {
            // The source payload is null. Nothing to copy.
            if (dst_is_null_restricted) {
              // The destination does not support null.
              THROW(vmSymbols::java_lang_NullPointerException());
            }
          } else {
            dst_payload.copy_from(buf_payload);
          }

          // Advance to next element
          src_payload.next_element();
          dst_payload.next_element();
        }
      }
    } else {
      // flatArray-to-refArray
      assert(dk->is_refArray_klass(), "Expected objArray here");

      // Need to allocate each new src elem payload -> dst oop
      refArrayHandle dh(THREAD, (refArrayOop)d);
      flatArrayHandle sh(THREAD, sa);
      for (int i = 0; i < length; i++) {
        oop o = sh->obj_at(src_pos + i, CHECK);
        dh->obj_at_put(dst_pos + i, o);
      }
    }
  } else {
    // refArray-to-flatArray
    assert(s->is_refArray(), "Expected refArray");
    assert(d->is_flatArray(), "Expected flatArray");
    refArrayOop sa = refArrayOop(s);
    flatArrayOop da = flatArrayOop(d);

    for (int i = 0; i < length; i++) {
      da->obj_at_put( dst_pos + i, sa->obj_at(src_pos + i), CHECK);
    }
  }
}

bool FlatArrayKlass::can_be_primary_super_slow() const {
    return true;
}

u2 FlatArrayKlass::compute_modifier_flags() const {
  // The modifier for an flatArray is the same as its element
  // With the addition of ACC_IDENTITY
  u2 element_flags = element_klass()->compute_modifier_flags();

  u2 identity_flag = (Arguments::is_valhalla_enabled()) ? JVM_ACC_IDENTITY : 0;

  return (element_flags & (JVM_ACC_PUBLIC | JVM_ACC_PRIVATE | JVM_ACC_PROTECTED))
                        | (identity_flag | JVM_ACC_ABSTRACT | JVM_ACC_FINAL);
}

void FlatArrayKlass::print_on(outputStream* st) const {
  assert(!is_refArray_klass(), "Unimplemented");
  ResourceMark rm;

  st->print("Flat Type Array: ");
  Klass::print_on(st);

  st->print(" - element klass: ");
  element_klass()->print_value_on(st);
  st->cr();

  st->print(" - layout kind: %s", LayoutKindHelper::layout_kind_as_string(layout_kind()));
  st->cr();

  st->print(" - array properties: %s", properties().as_string());
  st->cr();

  int elem_size = element_byte_size();
  st->print(" - element size %i ", elem_size);
  st->print("aligned layout size %i", 1 << layout_helper_log2_element_size(layout_helper()));
  st->cr();
}

void FlatArrayKlass::print_value_on(outputStream* st) const {
  assert(is_klass(), "must be klass");

  element_klass()->print_value_on(st);
  st->print("[]");
}

#ifndef PRODUCT
void FlatArrayKlass::oop_print_on(oop obj, outputStream* st) {
  ArrayKlass::oop_print_on(obj, st);
  flatArrayOop va = flatArrayOop(obj);
  oop_print_elements_on(va, st);
}
#endif //PRODUCT

void FlatArrayKlass::oop_print_value_on(oop obj, outputStream* st) {
  assert(obj->is_flatArray(), "must be flatArray");
  st->print("a ");
  element_klass()->print_value_on(st);
  int len = flatArrayOop(obj)->length();
  st->print("[%d] ", len);
  obj->print_address_on(st);
  if (PrintMiscellaneous && (WizardMode || Verbose)) {
    int lh = layout_helper();
    st->print("{");
    for (int i = 0; i < len; i++) {
      if (i > 4) {
        st->print("..."); break;
      }
      st->print(" " INTPTR_FORMAT, (intptr_t)(void*)flatArrayOop(obj)->value_at_addr(i , lh));
    }
    st->print(" }");
  }
}

void FlatArrayKlass::oop_print_elements_on(flatArrayOop fa, outputStream* st) {
  InlineKlass* vk = element_klass();
  int print_len = MIN2(fa->length(), MaxElementPrintSize);
  for(int index = 0; index < print_len; index++) {
    int off = (address) fa->value_at_addr(index, layout_helper()) - cast_from_oop<address>(fa);
    st->print_cr(" - Index %3d offset %3d: ", index, off);
    oop obj = cast_to_oop((address)fa->value_at_addr(index, layout_helper()) - vk->payload_offset());
    FieldPrinter print_field(st, obj);
    vk->do_nonstatic_fields(&print_field);
    st->cr();
  }
  int remaining = fa->length() - print_len;
  if (remaining > 0) {
    st->print_cr(" - <%d more elements, increase MaxElementPrintSize to print>", remaining);
  }
}

// Verification
class VerifyElementClosure: public BasicOopIterateClosure {
 public:
  virtual void do_oop(oop* p)       { VerifyOopClosure::verify_oop.do_oop(p); }
  virtual void do_oop(narrowOop* p) { VerifyOopClosure::verify_oop.do_oop(p); }
};

void FlatArrayKlass::oop_verify_on(oop obj, outputStream* st) {
  ObjArrayKlass::oop_verify_on(obj, st);
  guarantee(obj->is_flatArray(), "must be flatArray");

  if (contains_oops()) {
    flatArrayOop va = flatArrayOop(obj);
    VerifyElementClosure ec;
    va->oop_iterate(&ec);
  }
}

void FlatArrayKlass::verify_on(outputStream* st) {
  ArrayKlass::verify_on(st);
  guarantee(element_klass()->is_inline_klass(), "should be inline type klass");
}

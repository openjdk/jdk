/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/vmClasses.hpp"
#include "classfile/vmSymbols.hpp"
#include "gc/shared/collectedHeap.inline.hpp"
#include "memory/iterator.inline.hpp"
#include "memory/metadataFactory.hpp"
#include "memory/metaspaceClosure.hpp"
#include "memory/oopFactory.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/arrayKlass.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/klass.inline.hpp"
#include "oops/markWord.hpp"
#include "oops/oop.inline.hpp"
#include "oops/refArrayKlass.inline.hpp"
#include "oops/refArrayOop.inline.hpp"
#include "oops/symbol.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/macros.hpp"

RefArrayKlass* RefArrayKlass::allocate_klass(ClassLoaderData* loader_data, int n,
                                       Klass* k, Symbol* name,
                                       TRAPS) {
  assert(RefArrayKlass::header_size() <= InstanceKlass::header_size(),
         "array klasses must be same size as InstanceKlass");

  int size = ArrayKlass::static_size(RefArrayKlass::header_size());

  return new (loader_data, size, THREAD) RefArrayKlass(n, k, name);
}

RefArrayKlass* RefArrayKlass::allocate_refArray_klass(ClassLoaderData* loader_data, int n,
                                                      Klass* element_klass,
                                                      TRAPS) {

  // Eagerly allocate the direct array supertype.
  Klass* super_klass = nullptr;
  if (!Universe::is_bootstrapping() || vmClasses::Object_klass_is_loaded()) {
    assert(MultiArray_lock->holds_lock(THREAD),
           "must hold lock after bootstrapping");
    Klass* element_super = element_klass->super();
    super_klass = element_klass->array_klass(CHECK_NULL);
  }

  // Create type name for klass.
  Symbol* name = create_element_klass_array_name(THREAD, element_klass);

  // Initialize instance variables
  RefArrayKlass* oak = RefArrayKlass::allocate_klass(loader_data, n, element_klass,
                                                     name, CHECK_NULL);

  ModuleEntry* module = oak->module();
  assert(module != nullptr, "No module entry for array");

  // Call complete_create_array_klass after all instance variables has been
  // initialized.
  ArrayKlass::complete_create_array_klass(oak, super_klass, module, CHECK_NULL);

  // Add all classes to our internal class loader list here,
  // including classes in the bootstrap (null) class loader.
  // Do this step after creating the mirror so that if the
  // mirror creation fails, loaded_classes_do() doesn't find
  // an array class without a mirror.
  loader_data->add_class(oak);

  return oak;
}

RefArrayKlass::RefArrayKlass(int n, Klass* element_klass, Symbol* name)
    : ObjArrayKlass(n, element_klass, name, Kind) {
  set_dimension(n);
  set_element_klass(element_klass);

  Klass* bk;
  if (element_klass->is_objArray_klass()) {
    bk = ObjArrayKlass::cast(element_klass)->bottom_klass();
  } else {
    bk = element_klass;
  }
  assert(bk != nullptr && (bk->is_instance_klass() || bk->is_typeArray_klass()),
         "invalid bottom klass");
  set_bottom_klass(bk);
  set_class_loader_data(bk->class_loader_data());

  if (element_klass->is_array_klass()) {
    set_lower_dimension(ArrayKlass::cast(element_klass));
  }

  int lh = array_layout_helper(T_OBJECT);
  set_layout_helper(lh);
  assert(is_array_klass(), "sanity");
  assert(is_refArray_klass(), "sanity");
}

size_t RefArrayKlass::oop_size(oop obj) const {
  // In this assert, we cannot safely access the Klass* with compact headers,
  // because size_given_klass() calls oop_size() on objects that might be
  // concurrently forwarded, which would overwrite the Klass*.
  assert(UseCompactObjectHeaders || obj->is_refArray(), "must be a reference array");
  return refArrayOop(obj)->object_size();
}

objArrayOop RefArrayKlass::allocate_instance(int length, TRAPS) {
  check_array_allocation_length(
      length, arrayOopDesc::max_array_length(T_OBJECT), CHECK_NULL);
  size_t size = refArrayOopDesc::object_size(length);
  objArrayOop array = (objArrayOop)Universe::heap()->array_allocate(
      this, size, length,
      /* do_zero */ true, CHECK_NULL);
  assert(array->is_refArray(), "Must be");
  return array;
}

static void throw_array_store_exception(arrayOop src, arrayOop dst, TRAPS) {
  ResourceMark rm(THREAD);
  Klass* bound = ObjArrayKlass::cast(dst->klass())->element_klass();
  Klass* stype = ObjArrayKlass::cast(src->klass())->element_klass();
  stringStream ss;
  if (!bound->is_subtype_of(stype)) {
    ss.print("arraycopy: type mismatch: can not copy %s[] into %s[]",
             stype->external_name(), bound->external_name());
  } else {
    // oop_arraycopy should return the index in the source array that
    // contains the problematic oop.
    ss.print("arraycopy: element type mismatch: can not cast one of the elements"
             " of %s[] to the type of the destination array, %s",
             stype->external_name(), bound->external_name());
  }
  THROW_MSG(vmSymbols::java_lang_ArrayStoreException(), ss.as_string());
}

// Either oop or narrowOop depending on UseCompressedOops.
void RefArrayKlass::do_copy(arrayOop s, size_t src_offset,
                            arrayOop d, size_t dst_offset, int length, TRAPS) {
  if (s == d) {
    // since source and destination are equal we do not need conversion checks.
    assert(length > 0, "sanity check");
    OopCopyResult result = ArrayAccess<>::oop_arraycopy(s, src_offset, d, dst_offset, length);
    assert(result == OopCopyResult::ok, "Should never fail");
  } else {
    // We have to make sure all elements conform to the destination array
    Klass* bound = RefArrayKlass::cast(d->klass())->element_klass();
    Klass* stype = RefArrayKlass::cast(s->klass())->element_klass();
    bool type_check = stype != bound && !stype->is_subtype_of(bound);

    auto arraycopy = [&] {
      if (type_check) {
        return ArrayAccess<ARRAYCOPY_DISJOINT | ARRAYCOPY_CHECKCAST>::
            oop_arraycopy(s, src_offset, d, dst_offset, length);
      } else {
        return ArrayAccess<ARRAYCOPY_DISJOINT>::
            oop_arraycopy(s, src_offset, d, dst_offset, length);
      }
    };

    OopCopyResult result = arraycopy();

    switch (result) {
    case OopCopyResult::ok:
      // Done
      break;
    case OopCopyResult::failed_check_class_cast:
      throw_array_store_exception(s, d, JavaThread::current());
      break;
    default:
      ShouldNotReachHere();
    }
  }
}

void RefArrayKlass::copy_array(arrayOop s, int src_pos, arrayOop d,
                               int dst_pos, int length, TRAPS) {
  assert(s->is_refArray(), "must be a reference array");

  if (!d->is_refArray()) {
    ResourceMark rm(THREAD);
    stringStream ss;
    if (d->is_typeArray()) {
      ss.print("arraycopy: type mismatch: can not copy object array[] into %s[]",
               type2name_tab[ArrayKlass::cast(d->klass())->element_type()]);
    } else {
      ss.print("arraycopy: destination type %s is not an array", d->klass()->external_name());
    }
    THROW_MSG(vmSymbols::java_lang_ArrayStoreException(), ss.as_string());
  }

  // Check is all offsets and lengths are non negative
  if (src_pos < 0 || dst_pos < 0 || length < 0) {
    // Pass specific exception reason.
    ResourceMark rm(THREAD);
    stringStream ss;
    if (src_pos < 0) {
      ss.print("arraycopy: source index %d out of bounds for object array[%d]",
               src_pos, s->length());
    } else if (dst_pos < 0) {
      ss.print("arraycopy: destination index %d out of bounds for object array[%d]",
               dst_pos, d->length());
    } else {
      ss.print("arraycopy: length %d is negative", length);
    }
    THROW_MSG(vmSymbols::java_lang_ArrayIndexOutOfBoundsException(), ss.as_string());
  }
  // Check if the ranges are valid
  if ((((unsigned int) length + (unsigned int) src_pos) > (unsigned int) s->length()) ||
      (((unsigned int) length + (unsigned int) dst_pos) > (unsigned int) d->length())) {
    // Pass specific exception reason.
    ResourceMark rm(THREAD);
    stringStream ss;
    if (((unsigned int) length + (unsigned int) src_pos) > (unsigned int) s->length()) {
      ss.print("arraycopy: last source index %u out of bounds for object array[%d]",
               (unsigned int) length + (unsigned int) src_pos, s->length());
    } else {
      ss.print("arraycopy: last destination index %u out of bounds for object array[%d]",
               (unsigned int) length + (unsigned int) dst_pos, d->length());
    }
    THROW_MSG(vmSymbols::java_lang_ArrayIndexOutOfBoundsException(), ss.as_string());
  }

  // Special case. Boundary cases must be checked first
  // This allows the following call: copy_array(s, s.length(), d.length(), 0).
  // This is correct, since the position is supposed to be an 'in between
  // point', i.e., s.length(), points to the right of the last element.
  if (length == 0) {
    return;
  }
  if (UseCompressedOops) {
    size_t src_offset = (size_t) refArrayOopDesc::obj_at_offset<narrowOop>(src_pos);
    size_t dst_offset = (size_t) refArrayOopDesc::obj_at_offset<narrowOop>(dst_pos);
    assert(arrayOopDesc::obj_offset_to_raw<narrowOop>(s, src_offset, nullptr) ==
           refArrayOop(s)->obj_at_addr<narrowOop>(src_pos), "sanity");
    assert(arrayOopDesc::obj_offset_to_raw<narrowOop>(d, dst_offset, nullptr) ==
           refArrayOop(d)->obj_at_addr<narrowOop>(dst_pos), "sanity");
    do_copy(s, src_offset, d, dst_offset, length, CHECK);
  } else {
    size_t src_offset = (size_t) refArrayOopDesc::obj_at_offset<oop>(src_pos);
    size_t dst_offset = (size_t) refArrayOopDesc::obj_at_offset<oop>(dst_pos);
    assert(arrayOopDesc::obj_offset_to_raw<oop>(s, src_offset, nullptr) ==
           refArrayOop(s)->obj_at_addr<oop>(src_pos), "sanity");
    assert(arrayOopDesc::obj_offset_to_raw<oop>(d, dst_offset, nullptr) ==
           refArrayOop(d)->obj_at_addr<oop>(dst_pos), "sanity");
    do_copy(s, src_offset, d, dst_offset, length, CHECK);
  }
}

void RefArrayKlass::initialize(TRAPS) {
  bottom_klass()->initialize(THREAD); // dispatches to either InstanceKlass or TypeArrayKlass
}

void RefArrayKlass::metaspace_pointers_do(MetaspaceClosure *it) {
  ObjArrayKlass::metaspace_pointers_do(it);
}

// Printing

void RefArrayKlass::print_on(outputStream* st) const {
#ifndef PRODUCT
  Klass::print_on(st);
  st->print(" - element klass: ");
  element_klass()->print_value_on(st);
  st->cr();
#endif // PRODUCT
}

void RefArrayKlass::print_value_on(outputStream* st) const {
  assert(is_klass(), "must be klass");

  element_klass()->print_value_on(st);
  st->print("[]");
}

#ifndef PRODUCT

void RefArrayKlass::oop_print_on(oop obj, outputStream* st) {
  ArrayKlass::oop_print_on(obj, st);
  assert(obj->is_refArray(), "must be refArray");
  refArrayOop oa = refArrayOop(obj);
  int print_len = MIN2(oa->length(), MaxElementPrintSize);
  for (int index = 0; index < print_len; index++) {
    st->print(" - %3d : ", index);
    if (oa->obj_at(index) != nullptr) {
      oa->obj_at(index)->print_value_on(st);
      st->cr();
    } else {
      st->print_cr("null");
    }
  }
  int remaining = oa->length() - print_len;
  if (remaining > 0) {
    st->print_cr(" - <%d more elements, increase MaxElementPrintSize to print>",
                 remaining);
  }
}

#endif // PRODUCT

void RefArrayKlass::oop_print_value_on(oop obj, outputStream* st) {
  assert(obj->is_refArray(), "must be refArray");
  st->print("a ");
  element_klass()->print_value_on(st);
  int len = refArrayOop(obj)->length();
  st->print("[%d] ", len);
  if (obj != nullptr) {
    obj->print_address_on(st);
  } else {
    st->print_cr("null");
  }
}

// Verification

void RefArrayKlass::verify_on(outputStream* st) {
  ArrayKlass::verify_on(st);
  guarantee(element_klass()->is_klass(), "should be klass");
  guarantee(bottom_klass()->is_klass(), "should be klass");
  Klass *bk = bottom_klass();
  guarantee(bk->is_instance_klass() || bk->is_typeArray_klass(),
            "invalid bottom klass");
}

void RefArrayKlass::oop_verify_on(oop obj, outputStream* st) {
  ArrayKlass::oop_verify_on(obj, st);
  guarantee(obj->is_refArray(), "must be refArray");
  refArrayOop oa = refArrayOop(obj);
  for (int index = 0; index < oa->length(); index++) {
    guarantee(oopDesc::is_oop_or_null(oa->obj_at(index)), "should be oop");
  }
}

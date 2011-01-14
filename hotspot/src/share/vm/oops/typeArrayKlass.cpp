/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "gc_interface/collectedHeap.hpp"
#include "gc_interface/collectedHeap.inline.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "memory/universe.inline.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/klassOop.hpp"
#include "oops/objArrayKlassKlass.hpp"
#include "oops/oop.inline.hpp"
#include "oops/typeArrayKlass.hpp"
#include "oops/typeArrayOop.hpp"
#include "runtime/handles.inline.hpp"

bool typeArrayKlass::compute_is_subtype_of(klassOop k) {
  if (!k->klass_part()->oop_is_typeArray()) {
    return arrayKlass::compute_is_subtype_of(k);
  }

  typeArrayKlass* tak = typeArrayKlass::cast(k);
  if (dimension() != tak->dimension()) return false;

  return element_type() == tak->element_type();
}

klassOop typeArrayKlass::create_klass(BasicType type, int scale,
                                      const char* name_str, TRAPS) {
  typeArrayKlass o;

  symbolHandle sym(symbolOop(NULL));
  // bootstrapping: don't create sym if symbolKlass not created yet
  if (Universe::symbolKlassObj() != NULL && name_str != NULL) {
    sym = oopFactory::new_symbol_handle(name_str, CHECK_NULL);
  }
  KlassHandle klassklass (THREAD, Universe::typeArrayKlassKlassObj());

  arrayKlassHandle k = base_create_array_klass(o.vtbl_value(), header_size(), klassklass, CHECK_NULL);
  typeArrayKlass* ak = typeArrayKlass::cast(k());
  ak->set_name(sym());
  ak->set_layout_helper(array_layout_helper(type));
  assert(scale == (1 << ak->log2_element_size()), "scale must check out");
  assert(ak->oop_is_javaArray(), "sanity");
  assert(ak->oop_is_typeArray(), "sanity");
  ak->set_max_length(arrayOopDesc::max_array_length(type));
  assert(k()->size() > header_size(), "bad size");

  // Call complete_create_array_klass after all instance variables have been initialized.
  KlassHandle super (THREAD, k->super());
  complete_create_array_klass(k, super, CHECK_NULL);

  return k();
}

typeArrayOop typeArrayKlass::allocate(int length, TRAPS) {
  assert(log2_element_size() >= 0, "bad scale");
  if (length >= 0) {
    if (length <= max_length()) {
      size_t size = typeArrayOopDesc::object_size(layout_helper(), length);
      KlassHandle h_k(THREAD, as_klassOop());
      typeArrayOop t;
      CollectedHeap* ch = Universe::heap();
      if (size < ch->large_typearray_limit()) {
        t = (typeArrayOop)CollectedHeap::array_allocate(h_k, (int)size, length, CHECK_NULL);
      } else {
        t = (typeArrayOop)CollectedHeap::large_typearray_allocate(h_k, (int)size, length, CHECK_NULL);
      }
      assert(t->is_parsable(), "Don't publish unless parsable");
      return t;
    } else {
      report_java_out_of_memory("Requested array size exceeds VM limit");
      THROW_OOP_0(Universe::out_of_memory_error_array_size());
    }
  } else {
    THROW_0(vmSymbols::java_lang_NegativeArraySizeException());
  }
}

typeArrayOop typeArrayKlass::allocate_permanent(int length, TRAPS) {
  if (length < 0) THROW_0(vmSymbols::java_lang_NegativeArraySizeException());
  int size = typeArrayOopDesc::object_size(layout_helper(), length);
  KlassHandle h_k(THREAD, as_klassOop());
  typeArrayOop t = (typeArrayOop)
    CollectedHeap::permanent_array_allocate(h_k, size, length, CHECK_NULL);
  assert(t->is_parsable(), "Can't publish until parsable");
  return t;
}

oop typeArrayKlass::multi_allocate(int rank, jint* last_size, TRAPS) {
  // For typeArrays this is only called for the last dimension
  assert(rank == 1, "just checking");
  int length = *last_size;
  return allocate(length, THREAD);
}


void typeArrayKlass::copy_array(arrayOop s, int src_pos, arrayOop d, int dst_pos, int length, TRAPS) {
  assert(s->is_typeArray(), "must be type array");

  // Check destination
  if (!d->is_typeArray() || element_type() != typeArrayKlass::cast(d->klass())->element_type()) {
    THROW(vmSymbols::java_lang_ArrayStoreException());
  }

  // Check is all offsets and lengths are non negative
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

  // This is an attempt to make the copy_array fast.
  int l2es = log2_element_size();
  int ihs = array_header_in_bytes() / wordSize;
  char* src = (char*) ((oop*)s + ihs) + ((size_t)src_pos << l2es);
  char* dst = (char*) ((oop*)d + ihs) + ((size_t)dst_pos << l2es);
  Copy::conjoint_memory_atomic(src, dst, (size_t)length << l2es);
}


// create a klass of array holding typeArrays
klassOop typeArrayKlass::array_klass_impl(bool or_null, int n, TRAPS) {
  typeArrayKlassHandle h_this(THREAD, as_klassOop());
  return array_klass_impl(h_this, or_null, n, THREAD);
}

klassOop typeArrayKlass::array_klass_impl(typeArrayKlassHandle h_this, bool or_null, int n, TRAPS) {
  int dimension = h_this->dimension();
  assert(dimension <= n, "check order of chain");
    if (dimension == n)
      return h_this();

  objArrayKlassHandle  h_ak(THREAD, h_this->higher_dimension());
  if (h_ak.is_null()) {
    if (or_null)  return NULL;

    ResourceMark rm;
    JavaThread *jt = (JavaThread *)THREAD;
    {
      MutexLocker mc(Compile_lock, THREAD);   // for vtables
      // Atomic create higher dimension and link into list
      MutexLocker mu(MultiArray_lock, THREAD);

      h_ak = objArrayKlassHandle(THREAD, h_this->higher_dimension());
      if (h_ak.is_null()) {
        klassOop oak = objArrayKlassKlass::cast(
          Universe::objArrayKlassKlassObj())->allocate_objArray_klass(
          dimension + 1, h_this, CHECK_NULL);
        h_ak = objArrayKlassHandle(THREAD, oak);
        h_ak->set_lower_dimension(h_this());
        OrderAccess::storestore();
        h_this->set_higher_dimension(h_ak());
        assert(h_ak->oop_is_objArray(), "incorrect initialization of objArrayKlass");
      }
    }
  } else {
    CHECK_UNHANDLED_OOPS_ONLY(Thread::current()->clear_unhandled_oops());
  }
  if (or_null) {
    return h_ak->array_klass_or_null(n);
  }
  return h_ak->array_klass(n, CHECK_NULL);
}

klassOop typeArrayKlass::array_klass_impl(bool or_null, TRAPS) {
  return array_klass_impl(or_null, dimension() +  1, THREAD);
}

int typeArrayKlass::oop_size(oop obj) const {
  assert(obj->is_typeArray(),"must be a type array");
  typeArrayOop t = typeArrayOop(obj);
  return t->object_size();
}

void typeArrayKlass::oop_follow_contents(oop obj) {
  assert(obj->is_typeArray(),"must be a type array");
  // Performance tweak: We skip iterating over the klass pointer since we
  // know that Universe::typeArrayKlass never moves.
}

#ifndef SERIALGC
void typeArrayKlass::oop_follow_contents(ParCompactionManager* cm, oop obj) {
  assert(obj->is_typeArray(),"must be a type array");
  // Performance tweak: We skip iterating over the klass pointer since we
  // know that Universe::typeArrayKlass never moves.
}
#endif // SERIALGC

int typeArrayKlass::oop_adjust_pointers(oop obj) {
  assert(obj->is_typeArray(),"must be a type array");
  typeArrayOop t = typeArrayOop(obj);
  // Performance tweak: We skip iterating over the klass pointer since we
  // know that Universe::typeArrayKlass never moves.
  return t->object_size();
}

int typeArrayKlass::oop_oop_iterate(oop obj, OopClosure* blk) {
  assert(obj->is_typeArray(),"must be a type array");
  typeArrayOop t = typeArrayOop(obj);
  // Performance tweak: We skip iterating over the klass pointer since we
  // know that Universe::typeArrayKlass never moves.
  return t->object_size();
}

int typeArrayKlass::oop_oop_iterate_m(oop obj, OopClosure* blk, MemRegion mr) {
  assert(obj->is_typeArray(),"must be a type array");
  typeArrayOop t = typeArrayOop(obj);
  // Performance tweak: We skip iterating over the klass pointer since we
  // know that Universe::typeArrayKlass never moves.
  return t->object_size();
}

#ifndef SERIALGC
void typeArrayKlass::oop_push_contents(PSPromotionManager* pm, oop obj) {
  assert(obj->is_typeArray(),"must be a type array");
}

int
typeArrayKlass::oop_update_pointers(ParCompactionManager* cm, oop obj) {
  assert(obj->is_typeArray(),"must be a type array");
  return typeArrayOop(obj)->object_size();
}

int
typeArrayKlass::oop_update_pointers(ParCompactionManager* cm, oop obj,
                                    HeapWord* beg_addr, HeapWord* end_addr) {
  assert(obj->is_typeArray(),"must be a type array");
  return typeArrayOop(obj)->object_size();
}
#endif // SERIALGC

void typeArrayKlass::initialize(TRAPS) {
  // Nothing to do. Having this function is handy since objArrayKlasses can be
  // initialized by calling initialize on their bottom_klass, see objArrayKlass::initialize
}

const char* typeArrayKlass::external_name(BasicType type) {
  switch (type) {
    case T_BOOLEAN: return "[Z";
    case T_CHAR:    return "[C";
    case T_FLOAT:   return "[F";
    case T_DOUBLE:  return "[D";
    case T_BYTE:    return "[B";
    case T_SHORT:   return "[S";
    case T_INT:     return "[I";
    case T_LONG:    return "[J";
    default: ShouldNotReachHere();
  }
  return NULL;
}

#ifndef PRODUCT
// Printing

static void print_boolean_array(typeArrayOop ta, int print_len, outputStream* st) {
  for (int index = 0; index < print_len; index++) {
    st->print_cr(" - %3d: %s", index, (ta->bool_at(index) == 0) ? "false" : "true");
  }
}


static void print_char_array(typeArrayOop ta, int print_len, outputStream* st) {
  for (int index = 0; index < print_len; index++) {
    jchar c = ta->char_at(index);
    st->print_cr(" - %3d: %x %c", index, c, isprint(c) ? c : ' ');
  }
}


static void print_float_array(typeArrayOop ta, int print_len, outputStream* st) {
  for (int index = 0; index < print_len; index++) {
    st->print_cr(" - %3d: %g", index, ta->float_at(index));
  }
}


static void print_double_array(typeArrayOop ta, int print_len, outputStream* st) {
  for (int index = 0; index < print_len; index++) {
    st->print_cr(" - %3d: %g", index, ta->double_at(index));
  }
}


static void print_byte_array(typeArrayOop ta, int print_len, outputStream* st) {
  for (int index = 0; index < print_len; index++) {
    jbyte c = ta->byte_at(index);
    st->print_cr(" - %3d: %x %c", index, c, isprint(c) ? c : ' ');
  }
}


static void print_short_array(typeArrayOop ta, int print_len, outputStream* st) {
  for (int index = 0; index < print_len; index++) {
    int v = ta->ushort_at(index);
    st->print_cr(" - %3d: 0x%x\t %d", index, v, v);
  }
}


static void print_int_array(typeArrayOop ta, int print_len, outputStream* st) {
  for (int index = 0; index < print_len; index++) {
    jint v = ta->int_at(index);
    st->print_cr(" - %3d: 0x%x %d", index, v, v);
  }
}


static void print_long_array(typeArrayOop ta, int print_len, outputStream* st) {
  for (int index = 0; index < print_len; index++) {
    jlong v = ta->long_at(index);
    st->print_cr(" - %3d: 0x%x 0x%x", index, high(v), low(v));
  }
}


void typeArrayKlass::oop_print_on(oop obj, outputStream* st) {
  arrayKlass::oop_print_on(obj, st);
  typeArrayOop ta = typeArrayOop(obj);
  int print_len = MIN2((intx) ta->length(), MaxElementPrintSize);
  switch (element_type()) {
    case T_BOOLEAN: print_boolean_array(ta, print_len, st); break;
    case T_CHAR:    print_char_array(ta, print_len, st);    break;
    case T_FLOAT:   print_float_array(ta, print_len, st);   break;
    case T_DOUBLE:  print_double_array(ta, print_len, st);  break;
    case T_BYTE:    print_byte_array(ta, print_len, st);    break;
    case T_SHORT:   print_short_array(ta, print_len, st);   break;
    case T_INT:     print_int_array(ta, print_len, st);     break;
    case T_LONG:    print_long_array(ta, print_len, st);    break;
    default: ShouldNotReachHere();
  }
  int remaining = ta->length() - print_len;
  if (remaining > 0) {
    tty->print_cr(" - <%d more elements, increase MaxElementPrintSize to print>", remaining);
  }
}

#endif // PRODUCT

const char* typeArrayKlass::internal_name() const {
  return Klass::external_name();
}

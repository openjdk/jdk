/*
 * Copyright (c) 2004, 2017, Oracle and/or its affiliates. All rights reserved.
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
#include "asm/macroAssembler.inline.hpp"
#include "memory/resourceArea.hpp"
#include "prims/jniFastGetField.hpp"
#include "prims/jvm_misc.hpp"
#include "runtime/safepoint.hpp"

// TSO ensures that loads are blocking and ordered with respect to
// to earlier loads, so we don't need LoadLoad membars.

#define __ masm->

#define BUFFER_SIZE 30*sizeof(jint)

// Common register usage:
// O0: env
// O1: obj
// O2: jfieldID
// O4: offset (O2 >> 2)
// G4: old safepoint counter

address JNI_FastGetField::generate_fast_get_int_field0(BasicType type) {
  const char *name;
  switch (type) {
    case T_BOOLEAN: name = "jni_fast_GetBooleanField"; break;
    case T_BYTE:    name = "jni_fast_GetByteField";    break;
    case T_CHAR:    name = "jni_fast_GetCharField";    break;
    case T_SHORT:   name = "jni_fast_GetShortField";   break;
    case T_INT:     name = "jni_fast_GetIntField";     break;
    default:        ShouldNotReachHere();
  }
  ResourceMark rm;
  BufferBlob* blob = BufferBlob::create(name, BUFFER_SIZE*wordSize);
  CodeBuffer cbuf(blob);
  MacroAssembler* masm = new MacroAssembler(&cbuf);
  address fast_entry = __ pc();

  Label label1, label2;

  AddressLiteral cnt_addrlit(SafepointSynchronize::safepoint_counter_addr());
  __ sethi (cnt_addrlit, O3);
  Address cnt_addr(O3, cnt_addrlit.low10());
  __ ld (cnt_addr, G4);
  __ andcc (G4, 1, G0);
  __ br (Assembler::notZero, false, Assembler::pn, label1);
  __ delayed()->srl (O2, 2, O4);
  __ andn (O1, JNIHandles::weak_tag_mask, O1);
  __ ld_ptr (O1, 0, O5);

  assert(count < LIST_CAPACITY, "LIST_CAPACITY too small");
  speculative_load_pclist[count] = __ pc();
  switch (type) {
    case T_BOOLEAN: __ ldub (O5, O4, G3);  break;
    case T_BYTE:    __ ldsb (O5, O4, G3);  break;
    case T_CHAR:    __ lduh (O5, O4, G3);  break;
    case T_SHORT:   __ ldsh (O5, O4, G3);  break;
    case T_INT:     __ ld (O5, O4, G3);    break;
    default:        ShouldNotReachHere();
  }

  __ ld (cnt_addr, O5);
  __ cmp (O5, G4);
  __ br (Assembler::notEqual, false, Assembler::pn, label2);
  __ delayed()->mov (O7, G1);
  __ retl ();
  __ delayed()->mov (G3, O0);

  slowcase_entry_pclist[count++] = __ pc();
  __ bind (label1);
  __ mov (O7, G1);

  address slow_case_addr;
  switch (type) {
    case T_BOOLEAN: slow_case_addr = jni_GetBooleanField_addr(); break;
    case T_BYTE:    slow_case_addr = jni_GetByteField_addr();    break;
    case T_CHAR:    slow_case_addr = jni_GetCharField_addr();    break;
    case T_SHORT:   slow_case_addr = jni_GetShortField_addr();   break;
    case T_INT:     slow_case_addr = jni_GetIntField_addr();     break;
    default:        ShouldNotReachHere();
  }
  __ bind (label2);
  __ call (slow_case_addr, relocInfo::none);
  __ delayed()->mov (G1, O7);

  __ flush ();

  return fast_entry;
}

address JNI_FastGetField::generate_fast_get_boolean_field() {
  return generate_fast_get_int_field0(T_BOOLEAN);
}

address JNI_FastGetField::generate_fast_get_byte_field() {
  return generate_fast_get_int_field0(T_BYTE);
}

address JNI_FastGetField::generate_fast_get_char_field() {
  return generate_fast_get_int_field0(T_CHAR);
}

address JNI_FastGetField::generate_fast_get_short_field() {
  return generate_fast_get_int_field0(T_SHORT);
}

address JNI_FastGetField::generate_fast_get_int_field() {
  return generate_fast_get_int_field0(T_INT);
}

address JNI_FastGetField::generate_fast_get_long_field() {
  const char *name = "jni_fast_GetLongField";
  ResourceMark rm;
  BufferBlob* blob = BufferBlob::create(name, BUFFER_SIZE*wordSize);
  CodeBuffer cbuf(blob);
  MacroAssembler* masm = new MacroAssembler(&cbuf);
  address fast_entry = __ pc();

  Label label1, label2;

  AddressLiteral cnt_addrlit(SafepointSynchronize::safepoint_counter_addr());
  __ sethi (cnt_addrlit, G3);
  Address cnt_addr(G3, cnt_addrlit.low10());
  __ ld (cnt_addr, G4);
  __ andcc (G4, 1, G0);
  __ br (Assembler::notZero, false, Assembler::pn, label1);
  __ delayed()->srl (O2, 2, O4);
  __ andn (O1, JNIHandles::weak_tag_mask, O1);
  __ ld_ptr (O1, 0, O5);
  __ add (O5, O4, O5);

#ifndef _LP64
  assert(count < LIST_CAPACITY-1, "LIST_CAPACITY too small");
  speculative_load_pclist[count++] = __ pc();
  __ ld (O5, 0, G2);

  speculative_load_pclist[count] = __ pc();
  __ ld (O5, 4, O3);
#else
  assert(count < LIST_CAPACITY, "LIST_CAPACITY too small");
  speculative_load_pclist[count] = __ pc();
  __ ldx (O5, 0, O3);
#endif

  __ ld (cnt_addr, G1);
  __ cmp (G1, G4);
  __ br (Assembler::notEqual, false, Assembler::pn, label2);
  __ delayed()->mov (O7, G1);

#ifndef _LP64
  __ mov (G2, O0);
  __ retl ();
  __ delayed()->mov (O3, O1);
#else
  __ retl ();
  __ delayed()->mov (O3, O0);
#endif

#ifndef _LP64
  slowcase_entry_pclist[count-1] = __ pc();
  slowcase_entry_pclist[count++] = __ pc() ;
#else
  slowcase_entry_pclist[count++] = __ pc();
#endif

  __ bind (label1);
  __ mov (O7, G1);

  address slow_case_addr = jni_GetLongField_addr();
  __ bind (label2);
  __ call (slow_case_addr, relocInfo::none);
  __ delayed()->mov (G1, O7);

  __ flush ();

  return fast_entry;
}

address JNI_FastGetField::generate_fast_get_float_field0(BasicType type) {
  const char *name;
  switch (type) {
    case T_FLOAT:  name = "jni_fast_GetFloatField";  break;
    case T_DOUBLE: name = "jni_fast_GetDoubleField"; break;
    default:       ShouldNotReachHere();
  }
  ResourceMark rm;
  BufferBlob* blob = BufferBlob::create(name, BUFFER_SIZE*wordSize);
  CodeBuffer cbuf(blob);
  MacroAssembler* masm = new MacroAssembler(&cbuf);
  address fast_entry = __ pc();

  Label label1, label2;

  AddressLiteral cnt_addrlit(SafepointSynchronize::safepoint_counter_addr());
  __ sethi (cnt_addrlit, O3);
  Address cnt_addr(O3, cnt_addrlit.low10());
  __ ld (cnt_addr, G4);
  __ andcc (G4, 1, G0);
  __ br (Assembler::notZero, false, Assembler::pn, label1);
  __ delayed()->srl (O2, 2, O4);
  __ andn (O1, JNIHandles::weak_tag_mask, O1);
  __ ld_ptr (O1, 0, O5);

  assert(count < LIST_CAPACITY, "LIST_CAPACITY too small");
  speculative_load_pclist[count] = __ pc();
  switch (type) {
    case T_FLOAT:  __ ldf (FloatRegisterImpl::S, O5, O4, F0); break;
    case T_DOUBLE: __ ldf (FloatRegisterImpl::D, O5, O4, F0); break;
    default:       ShouldNotReachHere();
  }

  __ ld (cnt_addr, O5);
  __ cmp (O5, G4);
  __ br (Assembler::notEqual, false, Assembler::pn, label2);
  __ delayed()->mov (O7, G1);

  __ retl ();
  __ delayed()-> nop ();

  slowcase_entry_pclist[count++] = __ pc();
  __ bind (label1);
  __ mov (O7, G1);

  address slow_case_addr;
  switch (type) {
    case T_FLOAT:  slow_case_addr = jni_GetFloatField_addr();  break;
    case T_DOUBLE: slow_case_addr = jni_GetDoubleField_addr(); break;
    default:       ShouldNotReachHere();
  }
  __ bind (label2);
  __ call (slow_case_addr, relocInfo::none);
  __ delayed()->mov (G1, O7);

  __ flush ();

  return fast_entry;
}

address JNI_FastGetField::generate_fast_get_float_field() {
  return generate_fast_get_float_field0(T_FLOAT);
}

address JNI_FastGetField::generate_fast_get_double_field() {
  return generate_fast_get_float_field0(T_DOUBLE);
}

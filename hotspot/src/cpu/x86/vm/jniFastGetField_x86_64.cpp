/*
 * Copyright (c) 2004, 2010, Oracle and/or its affiliates. All rights reserved.
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

# include "incls/_precompiled.incl"
# include "incls/_jniFastGetField_x86_64.cpp.incl"

#define __ masm->

#define BUFFER_SIZE 30*wordSize

// Instead of issuing lfence for LoadLoad barrier, we create data dependency
// between loads, which is more efficient than lfence.

// Common register usage:
// rax/xmm0: result
// c_rarg0:    jni env
// c_rarg1:    obj
// c_rarg2:    jfield id

static const Register robj          = r9;
static const Register rcounter      = r10;
static const Register roffset       = r11;
static const Register rcounter_addr = r11;

// Warning: do not use rip relative addressing after the first counter load
// since that may scratch r10!

address JNI_FastGetField::generate_fast_get_int_field0(BasicType type) {
  const char *name;
  switch (type) {
    case T_BOOLEAN: name = "jni_fast_GetBooleanField"; break;
    case T_BYTE:    name = "jni_fast_GetByteField";    break;
    case T_CHAR:    name = "jni_fast_GetCharField";    break;
    case T_SHORT:   name = "jni_fast_GetShortField";   break;
    case T_INT:     name = "jni_fast_GetIntField";     break;
    case T_LONG:    name = "jni_fast_GetLongField";    break;
    default:        ShouldNotReachHere();
  }
  ResourceMark rm;
  BufferBlob* blob = BufferBlob::create(name, BUFFER_SIZE);
  CodeBuffer cbuf(blob);
  MacroAssembler* masm = new MacroAssembler(&cbuf);
  address fast_entry = __ pc();

  Label slow;

  ExternalAddress counter(SafepointSynchronize::safepoint_counter_addr());
  __ mov32 (rcounter, counter);
  __ mov   (robj, c_rarg1);
  __ testb (rcounter, 1);
  __ jcc (Assembler::notZero, slow);
  if (os::is_MP()) {
    __ xorptr(robj, rcounter);
    __ xorptr(robj, rcounter);                   // obj, since
                                                // robj ^ rcounter ^ rcounter == robj
                                                // robj is data dependent on rcounter.
  }
  __ movptr(robj, Address(robj, 0));             // *obj
  __ mov   (roffset, c_rarg2);
  __ shrptr(roffset, 2);                         // offset

  assert(count < LIST_CAPACITY, "LIST_CAPACITY too small");
  speculative_load_pclist[count] = __ pc();
  switch (type) {
    case T_BOOLEAN: __ movzbl (rax, Address(robj, roffset, Address::times_1)); break;
    case T_BYTE:    __ movsbl (rax, Address(robj, roffset, Address::times_1)); break;
    case T_CHAR:    __ movzwl (rax, Address(robj, roffset, Address::times_1)); break;
    case T_SHORT:   __ movswl (rax, Address(robj, roffset, Address::times_1)); break;
    case T_INT:     __ movl   (rax, Address(robj, roffset, Address::times_1)); break;
    case T_LONG:    __ movq   (rax, Address(robj, roffset, Address::times_1)); break;
    default:        ShouldNotReachHere();
  }

  if (os::is_MP()) {
    __ lea(rcounter_addr, counter);
    // ca is data dependent on rax.
    __ xorptr(rcounter_addr, rax);
    __ xorptr(rcounter_addr, rax);
    __ cmpl (rcounter, Address(rcounter_addr, 0));
  } else {
    __ cmp32 (rcounter, counter);
  }
  __ jcc (Assembler::notEqual, slow);

  __ ret (0);

  slowcase_entry_pclist[count++] = __ pc();
  __ bind (slow);
  address slow_case_addr;
  switch (type) {
    case T_BOOLEAN: slow_case_addr = jni_GetBooleanField_addr(); break;
    case T_BYTE:    slow_case_addr = jni_GetByteField_addr();    break;
    case T_CHAR:    slow_case_addr = jni_GetCharField_addr();    break;
    case T_SHORT:   slow_case_addr = jni_GetShortField_addr();   break;
    case T_INT:     slow_case_addr = jni_GetIntField_addr();     break;
    case T_LONG:    slow_case_addr = jni_GetLongField_addr();
  }
  // tail call
  __ jump (ExternalAddress(slow_case_addr));

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
  return generate_fast_get_int_field0(T_LONG);
}

address JNI_FastGetField::generate_fast_get_float_field0(BasicType type) {
  const char *name;
  switch (type) {
    case T_FLOAT:     name = "jni_fast_GetFloatField";     break;
    case T_DOUBLE:    name = "jni_fast_GetDoubleField";    break;
    default:          ShouldNotReachHere();
  }
  ResourceMark rm;
  BufferBlob* blob = BufferBlob::create(name, BUFFER_SIZE);
  CodeBuffer cbuf(blob);
  MacroAssembler* masm = new MacroAssembler(&cbuf);
  address fast_entry = __ pc();

  Label slow;

  ExternalAddress counter(SafepointSynchronize::safepoint_counter_addr());
  __ mov32 (rcounter, counter);
  __ mov   (robj, c_rarg1);
  __ testb (rcounter, 1);
  __ jcc (Assembler::notZero, slow);
  if (os::is_MP()) {
    __ xorptr(robj, rcounter);
    __ xorptr(robj, rcounter);                   // obj, since
                                                // robj ^ rcounter ^ rcounter == robj
                                                // robj is data dependent on rcounter.
  }
  __ movptr(robj, Address(robj, 0));             // *obj
  __ mov   (roffset, c_rarg2);
  __ shrptr(roffset, 2);                         // offset

  assert(count < LIST_CAPACITY, "LIST_CAPACITY too small");
  speculative_load_pclist[count] = __ pc();
  switch (type) {
    case T_FLOAT:  __ movflt (xmm0, Address(robj, roffset, Address::times_1)); break;
    case T_DOUBLE: __ movdbl (xmm0, Address(robj, roffset, Address::times_1)); break;
    default:        ShouldNotReachHere();
  }

  if (os::is_MP()) {
    __ lea(rcounter_addr, counter);
    __ movdq (rax, xmm0);
    // counter address is data dependent on xmm0.
    __ xorptr(rcounter_addr, rax);
    __ xorptr(rcounter_addr, rax);
    __ cmpl (rcounter, Address(rcounter_addr, 0));
  } else {
    __ cmp32 (rcounter, counter);
  }
  __ jcc (Assembler::notEqual, slow);

  __ ret (0);

  slowcase_entry_pclist[count++] = __ pc();
  __ bind (slow);
  address slow_case_addr;
  switch (type) {
    case T_FLOAT:     slow_case_addr = jni_GetFloatField_addr();  break;
    case T_DOUBLE:    slow_case_addr = jni_GetDoubleField_addr();
  }
  // tail call
  __ jump (ExternalAddress(slow_case_addr));

  __ flush ();

  return fast_entry;
}

address JNI_FastGetField::generate_fast_get_float_field() {
  return generate_fast_get_float_field0(T_FLOAT);
}

address JNI_FastGetField::generate_fast_get_double_field() {
  return generate_fast_get_float_field0(T_DOUBLE);
}

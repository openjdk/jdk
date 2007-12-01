/*
 * Copyright 2004-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

# include "incls/_precompiled.incl"
# include "incls/_jniFastGetField_x86_32.cpp.incl"

#define __ masm->

#define BUFFER_SIZE 30

#ifdef _WINDOWS
GetBooleanField_t JNI_FastGetField::jni_fast_GetBooleanField_fp;
GetByteField_t    JNI_FastGetField::jni_fast_GetByteField_fp;
GetCharField_t    JNI_FastGetField::jni_fast_GetCharField_fp;
GetShortField_t   JNI_FastGetField::jni_fast_GetShortField_fp;
GetIntField_t     JNI_FastGetField::jni_fast_GetIntField_fp;
GetLongField_t    JNI_FastGetField::jni_fast_GetLongField_fp;
GetFloatField_t   JNI_FastGetField::jni_fast_GetFloatField_fp;
GetDoubleField_t  JNI_FastGetField::jni_fast_GetDoubleField_fp;
#endif

// Instead of issuing lfence for LoadLoad barrier, we create data dependency
// between loads, which is much more efficient than lfence.

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
  BufferBlob* b = BufferBlob::create(name, BUFFER_SIZE*wordSize);
  address fast_entry = b->instructions_begin();
  CodeBuffer cbuf(fast_entry, b->instructions_size());
  MacroAssembler* masm = new MacroAssembler(&cbuf);

  Label slow;

  // stack layout:    offset from rsp (in words):
  //  return pc        0
  //  jni env          1
  //  obj              2
  //  jfieldID         3

  ExternalAddress counter(SafepointSynchronize::safepoint_counter_addr());
  __ mov32 (rcx, counter);
  __ testb (rcx, 1);
  __ jcc (Assembler::notZero, slow);
  if (os::is_MP()) {
    __ movl (rax, rcx);
    __ andl (rax, 1);                         // rax, must end up 0
    __ movl (rdx, Address(rsp, rax, Address::times_1, 2*wordSize));
                                              // obj, notice rax, is 0.
                                              // rdx is data dependent on rcx.
  } else {
    __ movl (rdx, Address(rsp, 2*wordSize));  // obj
  }
  __ movl (rax, Address(rsp, 3*wordSize));  // jfieldID
  __ movl (rdx, Address(rdx, 0));           // *obj
  __ shrl (rax, 2);                         // offset

  assert(count < LIST_CAPACITY, "LIST_CAPACITY too small");
  speculative_load_pclist[count] = __ pc();
  switch (type) {
    case T_BOOLEAN: __ movzxb (rax, Address(rdx, rax, Address::times_1)); break;
    case T_BYTE:    __ movsxb (rax, Address(rdx, rax, Address::times_1)); break;
    case T_CHAR:    __ movzxw (rax, Address(rdx, rax, Address::times_1)); break;
    case T_SHORT:   __ movsxw (rax, Address(rdx, rax, Address::times_1)); break;
    case T_INT:     __ movl   (rax, Address(rdx, rax, Address::times_1)); break;
    default:        ShouldNotReachHere();
  }

  Address ca1;
  if (os::is_MP()) {
    __ lea(rdx, counter);
    __ xorl(rdx, rax);
    __ xorl(rdx, rax);
    __ cmp32(rcx, Address(rdx, 0));
    // ca1 is the same as ca because
    // rax, ^ counter_addr ^ rax, = address
    // ca1 is data dependent on rax,.
  } else {
    __ cmp32(rcx, counter);
  }
  __ jcc (Assembler::notEqual, slow);

#ifndef _WINDOWS
  __ ret (0);
#else
  // __stdcall calling convention
  __ ret (3*wordSize);
#endif

  slowcase_entry_pclist[count++] = __ pc();
  __ bind (slow);
  address slow_case_addr;
  switch (type) {
    case T_BOOLEAN: slow_case_addr = jni_GetBooleanField_addr(); break;
    case T_BYTE:    slow_case_addr = jni_GetByteField_addr();    break;
    case T_CHAR:    slow_case_addr = jni_GetCharField_addr();    break;
    case T_SHORT:   slow_case_addr = jni_GetShortField_addr();   break;
    case T_INT:     slow_case_addr = jni_GetIntField_addr();
  }
  // tail call
  __ jump (ExternalAddress(slow_case_addr));

  __ flush ();

#ifndef _WINDOWS
  return fast_entry;
#else
  switch (type) {
    case T_BOOLEAN: jni_fast_GetBooleanField_fp = (GetBooleanField_t)fast_entry; break;
    case T_BYTE:    jni_fast_GetByteField_fp = (GetByteField_t)fast_entry; break;
    case T_CHAR:    jni_fast_GetCharField_fp = (GetCharField_t)fast_entry; break;
    case T_SHORT:   jni_fast_GetShortField_fp = (GetShortField_t)fast_entry; break;
    case T_INT:     jni_fast_GetIntField_fp = (GetIntField_t)fast_entry;
  }
  return os::win32::fast_jni_accessor_wrapper(type);
#endif
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
  BufferBlob* b = BufferBlob::create(name, BUFFER_SIZE*wordSize);
  address fast_entry = b->instructions_begin();
  CodeBuffer cbuf(fast_entry, b->instructions_size());
  MacroAssembler* masm = new MacroAssembler(&cbuf);

  Label slow;

  // stack layout:    offset from rsp (in words):
  //  old rsi          0
  //  return pc        1
  //  jni env          2
  //  obj              3
  //  jfieldID         4

  ExternalAddress counter(SafepointSynchronize::safepoint_counter_addr());

  __ pushl (rsi);
  __ mov32 (rcx, counter);
  __ testb (rcx, 1);
  __ jcc (Assembler::notZero, slow);
  if (os::is_MP()) {
    __ movl (rax, rcx);
    __ andl (rax, 1);                         // rax, must end up 0
    __ movl (rdx, Address(rsp, rax, Address::times_1, 3*wordSize));
                                              // obj, notice rax, is 0.
                                              // rdx is data dependent on rcx.
  } else {
    __ movl (rdx, Address(rsp, 3*wordSize));  // obj
  }
  __ movl (rsi, Address(rsp, 4*wordSize));  // jfieldID
  __ movl (rdx, Address(rdx, 0));           // *obj
  __ shrl (rsi, 2);                         // offset

  assert(count < LIST_CAPACITY-1, "LIST_CAPACITY too small");
  speculative_load_pclist[count++] = __ pc();
  __ movl (rax, Address(rdx, rsi, Address::times_1));
  speculative_load_pclist[count] = __ pc();
  __ movl (rdx, Address(rdx, rsi, Address::times_1, 4));

  if (os::is_MP()) {
    __ lea  (rsi, counter);
    __ xorl (rsi, rdx);
    __ xorl (rsi, rax);
    __ xorl (rsi, rdx);
    __ xorl (rsi, rax);
    __ cmp32(rcx, Address(rsi, 0));
    // ca1 is the same as ca because
    // rax, ^ rdx ^ counter_addr ^ rax, ^ rdx = address
    // ca1 is data dependent on both rax, and rdx.
  } else {
    __ cmp32(rcx, counter);
  }
  __ jcc (Assembler::notEqual, slow);

  __ popl (rsi);

#ifndef _WINDOWS
  __ ret (0);
#else
  // __stdcall calling convention
  __ ret (3*wordSize);
#endif

  slowcase_entry_pclist[count-1] = __ pc();
  slowcase_entry_pclist[count++] = __ pc();
  __ bind (slow);
  __ popl (rsi);
  address slow_case_addr = jni_GetLongField_addr();;
  // tail call
  __ jump (ExternalAddress(slow_case_addr));

  __ flush ();

#ifndef _WINDOWS
  return fast_entry;
#else
  jni_fast_GetLongField_fp = (GetLongField_t)fast_entry;
  return os::win32::fast_jni_accessor_wrapper(T_LONG);
#endif
}

address JNI_FastGetField::generate_fast_get_float_field0(BasicType type) {
  const char *name;
  switch (type) {
    case T_FLOAT:  name = "jni_fast_GetFloatField";  break;
    case T_DOUBLE: name = "jni_fast_GetDoubleField"; break;
    default:       ShouldNotReachHere();
  }
  ResourceMark rm;
  BufferBlob* b = BufferBlob::create(name, BUFFER_SIZE*wordSize);
  address fast_entry = b->instructions_begin();
  CodeBuffer cbuf(fast_entry, b->instructions_size());
  MacroAssembler* masm = new MacroAssembler(&cbuf);

  Label slow_with_pop, slow;

  // stack layout:    offset from rsp (in words):
  //  return pc        0
  //  jni env          1
  //  obj              2
  //  jfieldID         3

  ExternalAddress counter(SafepointSynchronize::safepoint_counter_addr());

  __ mov32 (rcx, counter);
  __ testb (rcx, 1);
  __ jcc (Assembler::notZero, slow);
  if (os::is_MP()) {
    __ movl (rax, rcx);
    __ andl (rax, 1);                         // rax, must end up 0
    __ movl (rdx, Address(rsp, rax, Address::times_1, 2*wordSize));
                                              // obj, notice rax, is 0.
                                              // rdx is data dependent on rcx.
  } else {
    __ movl (rdx, Address(rsp, 2*wordSize)); // obj
  }
  __ movl (rax, Address(rsp, 3*wordSize));  // jfieldID
  __ movl (rdx, Address(rdx, 0));           // *obj
  __ shrl (rax, 2);                         // offset

  assert(count < LIST_CAPACITY, "LIST_CAPACITY too small");
  speculative_load_pclist[count] = __ pc();
  switch (type) {
    case T_FLOAT:  __ fld_s (Address(rdx, rax, Address::times_1)); break;
    case T_DOUBLE: __ fld_d (Address(rdx, rax, Address::times_1)); break;
    default:       ShouldNotReachHere();
  }

  Address ca1;
  if (os::is_MP()) {
    __ fst_s (Address(rsp, -4));
    __ lea(rdx, counter);
    __ movl (rax, Address(rsp, -4));
    __ xorl(rdx, rax);
    __ xorl(rdx, rax);
    __ cmp32(rcx, Address(rdx, 0));
                                          // rax, ^ counter_addr ^ rax, = address
                                          // ca1 is data dependent on the field
                                          // access.
  } else {
    __ cmp32(rcx, counter);
  }
  __ jcc (Assembler::notEqual, slow_with_pop);

#ifndef _WINDOWS
  __ ret (0);
#else
  // __stdcall calling convention
  __ ret (3*wordSize);
#endif

  __ bind (slow_with_pop);
  // invalid load. pop FPU stack.
  __ fstp_d (0);

  slowcase_entry_pclist[count++] = __ pc();
  __ bind (slow);
  address slow_case_addr;
  switch (type) {
    case T_FLOAT:  slow_case_addr = jni_GetFloatField_addr();  break;
    case T_DOUBLE: slow_case_addr = jni_GetDoubleField_addr(); break;
    default:       ShouldNotReachHere();
  }
  // tail call
  __ jump (ExternalAddress(slow_case_addr));

  __ flush ();

#ifndef _WINDOWS
  return fast_entry;
#else
  switch (type) {
    case T_FLOAT:  jni_fast_GetFloatField_fp = (GetFloatField_t)fast_entry; break;
    case T_DOUBLE: jni_fast_GetDoubleField_fp = (GetDoubleField_t)fast_entry;
  }
  return os::win32::fast_jni_accessor_wrapper(type);
#endif
}

address JNI_FastGetField::generate_fast_get_float_field() {
  return generate_fast_get_float_field0(T_FLOAT);
}

address JNI_FastGetField::generate_fast_get_double_field() {
  return generate_fast_get_float_field0(T_DOUBLE);
}

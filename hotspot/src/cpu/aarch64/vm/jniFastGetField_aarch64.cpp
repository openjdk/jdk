/*
 * Copyright (c) 2004, 2010, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, Red Hat Inc. All rights reserved.
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
#include "asm/macroAssembler.hpp"
#include "memory/resourceArea.hpp"
#include "prims/jniFastGetField.hpp"
#include "prims/jvm_misc.hpp"
#include "runtime/safepoint.hpp"

#define __ masm->

#define BUFFER_SIZE 30*wordSize

// Instead of issuing a LoadLoad barrier we create an address
// dependency between loads; this might be more efficient.

// Common register usage:
// r0/v0:      result
// c_rarg0:    jni env
// c_rarg1:    obj
// c_rarg2:    jfield id

static const Register robj          = r3;
static const Register rcounter      = r4;
static const Register roffset       = r5;
static const Register rcounter_addr = r6;
static const Register result        = r7;

address JNI_FastGetField::generate_fast_get_int_field0(BasicType type) {
  const char *name;
  switch (type) {
    case T_BOOLEAN: name = "jni_fast_GetBooleanField"; break;
    case T_BYTE:    name = "jni_fast_GetByteField";    break;
    case T_CHAR:    name = "jni_fast_GetCharField";    break;
    case T_SHORT:   name = "jni_fast_GetShortField";   break;
    case T_INT:     name = "jni_fast_GetIntField";     break;
    case T_LONG:    name = "jni_fast_GetLongField";    break;
    case T_FLOAT:   name = "jni_fast_GetFloatField";   break;
    case T_DOUBLE:  name = "jni_fast_GetDoubleField";  break;
    default:        ShouldNotReachHere();
      name = NULL;  // unreachable
  }
  ResourceMark rm;
  BufferBlob* blob = BufferBlob::create(name, BUFFER_SIZE);
  CodeBuffer cbuf(blob);
  MacroAssembler* masm = new MacroAssembler(&cbuf);
  address fast_entry = __ pc();

  Label slow;

  unsigned long offset;
  __ adrp(rcounter_addr,
          SafepointSynchronize::safepoint_counter_addr(), offset);
  Address safepoint_counter_addr(rcounter_addr, offset);
  __ ldrw(rcounter, safepoint_counter_addr);
  __ andw(rscratch1, rcounter, 1);
  __ cbnzw(rscratch1, slow);
  __ eor(robj, c_rarg1, rcounter);
  __ eor(robj, robj, rcounter);               // obj, since
                                              // robj ^ rcounter ^ rcounter == robj
                                              // robj is address dependent on rcounter.
  __ ldr(robj, Address(robj, 0));             // *obj
  __ lsr(roffset, c_rarg2, 2);                // offset

  assert(count < LIST_CAPACITY, "LIST_CAPACITY too small");
  speculative_load_pclist[count] = __ pc();   // Used by the segfault handler
  switch (type) {
    case T_BOOLEAN: __ ldrb    (result, Address(robj, roffset)); break;
    case T_BYTE:    __ ldrsb   (result, Address(robj, roffset)); break;
    case T_CHAR:    __ ldrh    (result, Address(robj, roffset)); break;
    case T_SHORT:   __ ldrsh   (result, Address(robj, roffset)); break;
    case T_FLOAT:   __ ldrw    (result, Address(robj, roffset)); break;
    case T_INT:     __ ldrsw   (result, Address(robj, roffset)); break;
    case T_DOUBLE:
    case T_LONG:    __ ldr     (result, Address(robj, roffset)); break;
    default:        ShouldNotReachHere();
  }

  // counter_addr is address dependent on result.
  __ eor(rcounter_addr, rcounter_addr, result);
  __ eor(rcounter_addr, rcounter_addr, result);
  __ ldrw(rscratch1, safepoint_counter_addr);
  __ cmpw(rcounter, rscratch1);
  __ br (Assembler::NE, slow);

  switch (type) {
    case T_FLOAT:   __ fmovs(v0, result); break;
    case T_DOUBLE:  __ fmovd(v0, result); break;
    default:        __ mov(r0, result);   break;
  }
  __ ret(lr);

  slowcase_entry_pclist[count++] = __ pc();
  __ bind(slow);
  address slow_case_addr;
  switch (type) {
    case T_BOOLEAN: slow_case_addr = jni_GetBooleanField_addr(); break;
    case T_BYTE:    slow_case_addr = jni_GetByteField_addr();    break;
    case T_CHAR:    slow_case_addr = jni_GetCharField_addr();    break;
    case T_SHORT:   slow_case_addr = jni_GetShortField_addr();   break;
    case T_INT:     slow_case_addr = jni_GetIntField_addr();     break;
    case T_LONG:    slow_case_addr = jni_GetLongField_addr();    break;
    case T_FLOAT:   slow_case_addr = jni_GetFloatField_addr();   break;
    case T_DOUBLE:  slow_case_addr = jni_GetDoubleField_addr();  break;
    default:        ShouldNotReachHere();
      slow_case_addr = NULL;  // unreachable
  }

  {
    __ enter();
    __ lea(rscratch1, ExternalAddress(slow_case_addr));
    __ blr(rscratch1);
    __ maybe_isb();
    __ leave();
    __ ret(lr);
  }
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

address JNI_FastGetField::generate_fast_get_float_field() {
  return generate_fast_get_int_field0(T_FLOAT);
}

address JNI_FastGetField::generate_fast_get_double_field() {
  return generate_fast_get_int_field0(T_DOUBLE);
}


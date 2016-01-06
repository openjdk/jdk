/*
 * Copyright (c) 2003, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "interpreter/interp_masm.hpp"
#include "interpreter/interpreter.hpp"
#include "interpreter/templateInterpreterGenerator.hpp"
#include "runtime/arguments.hpp"

#define __ _masm->

/**
 * Method entry for static native methods:
 *   int java.util.zip.CRC32.update(int crc, int b)
 */
address TemplateInterpreterGenerator::generate_CRC32_update_entry() {
  if (UseCRC32Intrinsics) {
    address entry = __ pc();

    // rbx,: Method*
    // r13: senderSP must preserved for slow path, set SP to it on fast path
    // c_rarg0: scratch (rdi on non-Win64, rcx on Win64)
    // c_rarg1: scratch (rsi on non-Win64, rdx on Win64)

    Label slow_path;
    // If we need a safepoint check, generate full interpreter entry.
    ExternalAddress state(SafepointSynchronize::address_of_state());
    __ cmp32(ExternalAddress(SafepointSynchronize::address_of_state()),
             SafepointSynchronize::_not_synchronized);
    __ jcc(Assembler::notEqual, slow_path);

    // We don't generate local frame and don't align stack because
    // we call stub code and there is no safepoint on this path.

    // Load parameters
    const Register crc = rax;  // crc
    const Register val = c_rarg0;  // source java byte value
    const Register tbl = c_rarg1;  // scratch

    // Arguments are reversed on java expression stack
    __ movl(val, Address(rsp,   wordSize)); // byte value
    __ movl(crc, Address(rsp, 2*wordSize)); // Initial CRC

    __ lea(tbl, ExternalAddress(StubRoutines::crc_table_addr()));
    __ notl(crc); // ~crc
    __ update_byte_crc32(crc, val, tbl);
    __ notl(crc); // ~crc
    // result in rax

    // _areturn
    __ pop(rdi);                // get return address
    __ mov(rsp, r13);           // set sp to sender sp
    __ jmp(rdi);

    // generate a vanilla native entry as the slow path
    __ bind(slow_path);
    __ jump_to_entry(Interpreter::entry_for_kind(Interpreter::native));
    return entry;
  }
  return NULL;
}

/**
 * Method entry for static native methods:
 *   int java.util.zip.CRC32.updateBytes(int crc, byte[] b, int off, int len)
 *   int java.util.zip.CRC32.updateByteBuffer(int crc, long buf, int off, int len)
 */
address TemplateInterpreterGenerator::generate_CRC32_updateBytes_entry(AbstractInterpreter::MethodKind kind) {
  if (UseCRC32Intrinsics) {
    address entry = __ pc();

    // rbx,: Method*
    // r13: senderSP must preserved for slow path, set SP to it on fast path

    Label slow_path;
    // If we need a safepoint check, generate full interpreter entry.
    ExternalAddress state(SafepointSynchronize::address_of_state());
    __ cmp32(ExternalAddress(SafepointSynchronize::address_of_state()),
             SafepointSynchronize::_not_synchronized);
    __ jcc(Assembler::notEqual, slow_path);

    // We don't generate local frame and don't align stack because
    // we call stub code and there is no safepoint on this path.

    // Load parameters
    const Register crc = c_rarg0;  // crc
    const Register buf = c_rarg1;  // source java byte array address
    const Register len = c_rarg2;  // length
    const Register off = len;      // offset (never overlaps with 'len')

    // Arguments are reversed on java expression stack
    // Calculate address of start element
    if (kind == Interpreter::java_util_zip_CRC32_updateByteBuffer) {
      __ movptr(buf, Address(rsp, 3*wordSize)); // long buf
      __ movl2ptr(off, Address(rsp, 2*wordSize)); // offset
      __ addq(buf, off); // + offset
      __ movl(crc,   Address(rsp, 5*wordSize)); // Initial CRC
    } else {
      __ movptr(buf, Address(rsp, 3*wordSize)); // byte[] array
      __ addptr(buf, arrayOopDesc::base_offset_in_bytes(T_BYTE)); // + header size
      __ movl2ptr(off, Address(rsp, 2*wordSize)); // offset
      __ addq(buf, off); // + offset
      __ movl(crc,   Address(rsp, 4*wordSize)); // Initial CRC
    }
    // Can now load 'len' since we're finished with 'off'
    __ movl(len, Address(rsp, wordSize)); // Length

    __ super_call_VM_leaf(CAST_FROM_FN_PTR(address, StubRoutines::updateBytesCRC32()), crc, buf, len);
    // result in rax

    // _areturn
    __ pop(rdi);                // get return address
    __ mov(rsp, r13);           // set sp to sender sp
    __ jmp(rdi);

    // generate a vanilla native entry as the slow path
    __ bind(slow_path);
    __ jump_to_entry(Interpreter::entry_for_kind(Interpreter::native));
    return entry;
  }
  return NULL;
}

/**
* Method entry for static native methods:
*   int java.util.zip.CRC32C.updateBytes(int crc, byte[] b, int off, int end)
*   int java.util.zip.CRC32C.updateByteBuffer(int crc, long address, int off, int end)
*/
address TemplateInterpreterGenerator::generate_CRC32C_updateBytes_entry(AbstractInterpreter::MethodKind kind) {
  if (UseCRC32CIntrinsics) {
    address entry = __ pc();
    // Load parameters
    const Register crc = c_rarg0;  // crc
    const Register buf = c_rarg1;  // source java byte array address
    const Register len = c_rarg2;
    const Register off = c_rarg3;  // offset
    const Register end = len;

    // Arguments are reversed on java expression stack
    // Calculate address of start element
    if (kind == Interpreter::java_util_zip_CRC32C_updateDirectByteBuffer) {
      __ movptr(buf, Address(rsp, 3 * wordSize)); // long buf
      __ movl2ptr(off, Address(rsp, 2 * wordSize)); // offset
      __ addq(buf, off); // + offset
      __ movl(crc, Address(rsp, 5 * wordSize)); // Initial CRC
      // Note on 5 * wordSize vs. 4 * wordSize:
      // *   int java.util.zip.CRC32C.updateByteBuffer(int crc, long address, int off, int end)
      //                                                   4         2,3          1        0
      // end starts at SP + 8
      // The Java(R) Virtual Machine Specification Java SE 7 Edition
      // 4.10.2.3. Values of Types long and double
      //    "When calculating operand stack length, values of type long and double have length two."
    } else {
      __ movptr(buf, Address(rsp, 3 * wordSize)); // byte[] array
      __ addptr(buf, arrayOopDesc::base_offset_in_bytes(T_BYTE)); // + header size
      __ movl2ptr(off, Address(rsp, 2 * wordSize)); // offset
      __ addq(buf, off); // + offset
      __ movl(crc, Address(rsp, 4 * wordSize)); // Initial CRC
    }
    __ movl(end, Address(rsp, wordSize)); // end
    __ subl(end, off); // end - off
    __ super_call_VM_leaf(CAST_FROM_FN_PTR(address, StubRoutines::updateBytesCRC32C()), crc, buf, len);
    // result in rax
    // _areturn
    __ pop(rdi);                // get return address
    __ mov(rsp, r13);           // set sp to sender sp
    __ jmp(rdi);

    return entry;
  }

  return NULL;
}

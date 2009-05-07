/*
 * Copyright 1997-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

#include "incls/_precompiled.incl"
#include "incls/_icBuffer_sparc.cpp.incl"

int InlineCacheBuffer::ic_stub_code_size() {
#ifdef _LP64
  if (TraceJumps) return 600 * wordSize;
  return (NativeMovConstReg::instruction_size +  // sethi;add
          NativeJump::instruction_size +          // sethi; jmp; delay slot
          (1*BytesPerInstWord) + 1);            // flush + 1 extra byte
#else
  if (TraceJumps) return 300 * wordSize;
  return (2+2+ 1) * wordSize + 1; // set/jump_to/nop + 1 byte so that code_end can be set in CodeBuffer
#endif
}

void InlineCacheBuffer::assemble_ic_buffer_code(address code_begin, oop cached_oop, address entry_point) {
  ResourceMark rm;
  CodeBuffer     code(code_begin, ic_stub_code_size());
  MacroAssembler* masm            = new MacroAssembler(&code);
  // note: even though the code contains an embedded oop, we do not need reloc info
  // because
  // (1) the oop is old (i.e., doesn't matter for scavenges)
  // (2) these ICStubs are removed *before* a GC happens, so the roots disappear
  assert(cached_oop == NULL || cached_oop->is_perm(), "must be old oop");
  AddressLiteral cached_oop_addrlit(cached_oop, relocInfo::none);
  // Force the set to generate the fixed sequence so next_instruction_address works
  masm->patchable_set(cached_oop_addrlit, G5_inline_cache_reg);
  assert(G3_scratch != G5_method, "Do not clobber the method oop in the transition stub");
  assert(G3_scratch != G5_inline_cache_reg, "Do not clobber the inline cache register in the transition stub");
  AddressLiteral entry(entry_point);
  masm->JUMP(entry, G3_scratch, 0);
  masm->delayed()->nop();
  masm->flush();
}


address InlineCacheBuffer::ic_buffer_entry_point(address code_begin) {
  NativeMovConstReg* move = nativeMovConstReg_at(code_begin);   // creation also verifies the object
  NativeJump*        jump = nativeJump_at(move->next_instruction_address());
  return jump->jump_destination();
}


oop InlineCacheBuffer::ic_buffer_cached_oop(address code_begin) {
  NativeMovConstReg* move = nativeMovConstReg_at(code_begin);   // creation also verifies the object
  NativeJump*        jump = nativeJump_at(move->next_instruction_address());
  return (oop)move->data();
}
